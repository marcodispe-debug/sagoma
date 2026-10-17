package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.DatasetBlobs
import com.sagoma.planimetria.scan.reconstruction.DepthFrames
import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.PerimeterResult
import com.sagoma.planimetria.scan.reconstruction.PerimeterSolver
import com.sagoma.planimetria.scan.reconstruction.R2CornerDiagnostic
import com.sagoma.planimetria.scan.reconstruction.ReconParams
import com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor
import com.sagoma.planimetria.scan.reconstruction.SurfaceParams
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimationResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimator
import com.sagoma.planimetria.scan.recording.ArCameraProjection
import kotlin.math.abs

/*
 * BENCHMARK (solo test) — esecuzione della pipeline di PRODUZIONE R1 → R2 → R3/R3.1 → R4.1 sulle osservazioni simulate.
 * Le varianti di R2 usano SOLO parametri pubblici già esistenti (voxel e raggio del vicinato): nessuna modifica di produzione.
 */

/**
 * Configurazione di R2 per il confronto. Vicinato della normale = (2·[normalRadius] + 1) · [voxelM]. [runnable] = false: variante
 * prevista ma non eseguibile senza una modifica di R2 (es. adattiva → R2.1).
 */
data class R2Variant(val id: String, val voxelM: Double = 0.04, val normalRadius: Int = 2, val runnable: Boolean = true, val note: String = "") {
    val neighborhoodM: Double get() = (2 * normalRadius + 1) * voxelM
}

object R2Variants {
    val BASELINE = R2Variant("baseline-20cm", 0.04, 2, note = "R2 di produzione: voxel 4 cm, vicinato 5×5×5 = 20 cm")
    val N12 = R2Variant("exp-12cm", 0.04, 1, note = "voxel 4 cm, vicinato 3×3×3 = 12 cm")
    val N8 = R2Variant("exp-8cm", 0.027, 1, note = "voxel 2,7 cm, vicinato 3×3×3 ≈ 8 cm: CONFONDE dimensione del voxel e vicinato")
    val ADAPTIVE = R2Variant("exp-adaptive", runnable = false, note = "non esiste in R2 di produzione: richiede R2.1 (aggancio previsto, non eseguibile)")
    val all = listOf(BASELINE, N12, N8, ADAPTIVE)
}

/** Parametri del benchmark modificabili senza riscrivere gli scenari. [occlusion]: scala dei mobili (0 = nessun mobile, 1 = come progettati). */
data class BenchParams(
    val seed: Long? = null,
    val depthNoiseSigma: Double? = null,
    val dropoutRate: Double? = null,
    val variant: R2Variant = R2Variants.BASELINE,
    val numberOfViews: Int? = null,
    val occlusion: Double = 1.0,
)

/** Conteggi di R1 per i pixel: (classe di verità) → numero. */
class R1Accounting(val gtHits: Map<String, Int>, val generated: Map<String, Int>, val accepted: Map<String, Int>, val rejected: Map<String, Map<String, Int>>)

class BenchRun(
    val scenario: Scenario, val params: BenchParams, val sim: SimResult, val map: GlobalMap, val surfaces: SurfaceResult,
    val walls: WallEstimationResult, val r4: PerimeterResult, val trace: R2CornerDiagnostic.Trace,
    /** Etichetta di verità di ogni punto della mappa R1 (stesso ordine di [GlobalMap.points]). */
    val pointLabels: IntArray,
    val r1: R1Accounting,
)

object BenchPipeline {
    /** Lo scenario con i parametri sovrascritti (seme, rumore, dropout, mobili). */
    fun apply(sc: Scenario, bp: BenchParams): Scenario {
        val sp = sc.sensor.copy(
            seed = bp.seed ?: sc.sensor.seed, depthSigmaM = bp.depthNoiseSigma ?: sc.sensor.depthSigmaM, dropout = bp.dropoutRate ?: sc.sensor.dropout,
        )
        val o = bp.occlusion
        val room = if (o == 1.0) sc.room else GtRoom(
            sc.room.name, sc.room.walls, sc.room.heightM,
            sc.room.boxes.filter { o > 0 }.map { b -> b.copy(y1 = b.y0 + (b.y1 - b.y0) * o) }, sc.room.panels.filter { o > 0 }.map { p -> p.copy(y1 = p.y0 + (p.y1 - p.y0) * o) }, sc.room.outsideM,
        )
        return sc.copy(room = room, sensor = sp)
    }

    fun run(sc0: Scenario, bp: BenchParams = BenchParams()): BenchRun {
        require(bp.variant.runnable) { "variante ${bp.variant.id} non eseguibile: ${bp.variant.note}" }
        val sc = apply(sc0, bp)
        val sim = SensorSim.simulate(sc.room, sc.stations, sc.sensor, bp.numberOfViews)
        val rp = ReconParams(voxelM = bp.variant.voxelM)
        val sp = SurfaceParams(normalRadius = bp.variant.normalRadius)
        val blobs = DatasetBlobs { sim.blobs[it] }
        val map = GlobalMap.build(sim.recording, blobs, rp)
        val s = SurfaceExtractor.extract(map, sim.recording, sp)
        val w = WallEstimator.estimate(WallEstimator.inputFrom(map, s, sim.recording))
        val r4 = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, w))
        val (labels, acc) = labelR1(sim, blobs, rp, map)
        val trace = R2CornerDiagnostic.trace(map, s, BooleanArray(map.voxels.size) { true }, sp)
        return BenchRun(sc, bp, sim, map, s, w, r4, trace, labels, acc)
    }

    /**
     * Etichette di verità dei punti di R1: rifà l'accettazione dei pixel di R1 (distanza, confidenza, bordi) nello stesso ordine e
     * controlla che posizioni e numero coincidano con la mappa di produzione (altrimenti il benchmark si ferma: niente metriche false).
     */
    private fun labelR1(sim: SimResult, blobs: DatasetBlobs, p: ReconParams, map: GlobalMap): Pair<IntArray, R1Accounting> {
        val (frames, _) = DepthFrames.select(sim.recording, blobs, p)
        val out = ArrayList<Int>(map.points.size)
        val gt = HashMap<String, Int>(); val gen = HashMap<String, Int>(); val acc = HashMap<String, Int>(); val rej = HashMap<String, HashMap<String, Int>>()
        for (fr in sim.frames) for (i in fr.hitLabels.indices) {
            if (fr.hitLabels[i] != 0) gt.merge(Label.cls(fr.hitLabels[i]), 1, Int::plus)
            if (fr.mm[i] > 0) gen.merge(Label.cls(fr.labels[i]), 1, Int::plus)
        }
        var k = 0
        for (f in frames) {
            val sf = sim.frames[f.depthSeq]
            val w = f.k.width; val h = f.k.height
            for (v in 0 until h) for (u in 0 until w) {
                val mm = f.mm[v * w + u]
                if (mm <= 0) continue
                val z = mm / 1000.0
                val lab = sf.labels[v * w + u]
                val reason = when {
                    z < p.minRangeM || z > p.maxRangeM -> "range"
                    f.confidence != null && (f.confidence[v * w + u].toInt() and 0xFF) < p.minRawConfidence -> "confidence"
                    isEdge(f.mm, w, h, u, v, mm, p.edgeJumpRatio) -> "edge"
                    else -> null
                }
                if (reason != null) { rej.getOrPut(Label.cls(lab)) { HashMap() }.merge(reason, 1, Int::plus); continue }
                val q = ArCameraProjection.unproject(f.pose, f.k, u.toDouble(), v.toDouble(), z)
                check(k < map.points.size && abs(q[0] - map.points.x[k]) < 1e-3 && abs(q[2] - map.points.z[k]) < 1e-3) { "etichettatura di R1 non allineata al punto $k" }
                out.add(lab); acc.merge(Label.cls(lab), 1, Int::plus); k++
            }
        }
        check(k == map.points.size) { "etichettatura di R1: $k punti contro ${map.points.size} della mappa" }
        return out.toIntArray() to R1Accounting(gt, gen, acc, rej)
    }

    private fun isEdge(mm: IntArray, w: Int, h: Int, u: Int, v: Int, d: Int, ratio: Double): Boolean {
        val limit = d * ratio
        fun jump(uu: Int, vv: Int): Boolean { if (uu < 0 || vv < 0 || uu >= w || vv >= h) return false; val n = mm[vv * w + uu]; return n > 0 && abs(n - d) > limit }
        return jump(u - 1, v) || jump(u + 1, v) || jump(u, v - 1) || jump(u, v + 1)
    }
}
