package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.GapReason
import com.sagoma.planimetria.scan.reconstruction.LinkDecision
import com.sagoma.planimetria.scan.reconstruction.LinkKind
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.R2CornerDiagnostic.Loss
import com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.VoxelIndex
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * BENCHMARK (solo test) — METRICHE. Ogni punto di R1 ha l'etichetta della superficie vera (dal simulatore): le metriche di R2
 * sono esatte, senza tolleranze di attribuzione. Le tolleranze in [EvalParams] servono SOLO a valutare la geometria (R3/R4) contro
 * il ground truth: sono parametri del benchmark, non soglie degli algoritmi.
 */

data class EvalParams(
    /** Un angolo R4 corrisponde a un angolo vero se dista meno di così. */
    val cornerTolM: Double = 0.15,
    /** Zona attorno ad angoli e spalle delle aperture (raggio in pianta). */
    val zoneM: Double = 0.15,
    /** Il sensore "ha visto" un angolo se ha punti di entrambe le pareti entro questa distanza dall'angolo. */
    val sensorCornerM: Double = 0.04,
    /** Celle della copertura delle pareti (lato). */
    val cellM: Double = 0.10,
    /** Un'apertura è "segnalata" da un'estremità FREE_BEYOND entro questa distanza dalla sua spalla. */
    val openingEdgeM: Double = 0.15,
)

// ------------------------------------------------------------------------------------------------------------------- risultati

data class R1Metrics(
    val gtHits: Map<String, Int>, val generated: Map<String, Int>, val accepted: Map<String, Int>, val rejected: Map<String, Map<String, Int>>,
    val wallRecall: Double, val wallErrorMeanM: Double, val wallErrorRmsM: Double, val wallErrorP95M: Double, val wallErrorMaxM: Double,
    /** Per parete vera: copertura (frazione di celle 10 cm) vista dal sensore e accettata da R1. */
    val wallCoverage: List<Triple<Int, Double, Double>>,
)

data class R2Metrics(
    val wallPointsAccepted: Int,
    /** Esiti dei punti delle pareti vere: TP, WRONG_WALL, IN_FALSE_WALL, OBJECT, VERTICAL_OBJECT, UNKNOWN, FLOOR/CEILING e le perdite (Loss). */
    val wallOutcomes: Map<String, Int>,
    /** Punti NON di parete finiti in superfici VERTICAL_STRUCTURAL, per classe vera. */
    val contamination: Map<String, Int>,
    val structuralPoints: Int,
    val recall: Double, val endToEndRecall: Double, val precision: Double, val contaminationRate: Double,
    /** Superfici VERTICAL_STRUCTURAL la cui maggioranza di punti NON è una parete (muri falsi di R2). */
    val falseStructuralSurfaces: Int,
    val replicaMismatches: Int,
    /** Per parete vera: esiti R2 dei suoi punti e superfici R2 la cui maggioranza di punti è quella parete (classe, punti, motivi). */
    val perWall: Map<Int, Map<String, Int>> = emptyMap(),
    val wallSurfaces: Map<Int, List<String>> = emptyMap(),
)

data class CornerMetrics(
    val cornerId: Int, val x: Double, val z: Double, val wall: Int, val side: String,
    val sensorLastM: Double?, val r1LastM: Double?, val r2LastM: Double?, val r3EndM: Double?, val r3EndState: String, val r3EndSigmaM: Double?,
    val zonePointsR1: Int, val sensorLostR1: Int, val lostNormal: Int, val lostGrowing: Int, val wrongSurface: Int, val objectOrUnknown: Int, val assigned: Int,
)

data class OpeningMetrics(
    val id: String, val wall: Int, val kind: OpeningKind, val zonePoints: Int, val lost: Int, val lostByCause: Map<String, Int>,
    val outsideInStructural: Int, val r3Signal: String,
)

data class WallMetrics(
    val wall: Int, val trueLengthM: Double, val detected: Boolean, val fragments: Int, val observedLengthM: Double, val extentLengthM: Double,
    val lengthErrorM: Double, val lengthErrorRel: Double, val orientationErrorDeg: Double?, val positionErrorM: Double?,
    val startErrorM: Double?, val startState: String, val startSigmaM: Double?, val endErrorM: Double?, val endState: String, val endSigmaM: Double?,
)

data class R3Metrics(val walls: List<WallMetrics>, val falseWalls: List<String>, val r3Walls: Int, val impureWalls: Int)

data class R4Metrics(
    val gtCorners: Int, val correct: Int, val correctAmbiguous: Int, val wrong: Int, val missed: Int,
    val correctObservedSupport: Int, val correctInferredSupport: Int,
    val sameWallCorrect: Int, val sameWallWrong: Int,
    val sensorClosable: Boolean, val mainState: String, val closureVerdict: String,
    val links: List<String>,
)

data class GlobalMetrics(
    val geometryRecall: Double, val geometryPrecision: Double, val wallRecall: Double, val wallPrecision: Double,
    val cornerRecall: Double, val cornerPrecision: Double, val openingRecall: Double?, val openingFalsePositives: Int,
    val occlusion: Map<String, Int>, val falseClosure: Boolean, val meanGeomErrorM: Double, val maxGeomErrorM: Double,
)

class BenchMetrics(
    val scenario: String, val variant: String, val r1: R1Metrics, val r2: R2Metrics, val corners: List<CornerMetrics>, val openings: List<OpeningMetrics>,
    val r3: R3Metrics, val r4: R4Metrics, val global: GlobalMetrics,
)

// ----------------------------------------------------------------------------------------------------------------- calcolo

object BenchEval {
    private fun wallOf(run: BenchRun, id: Int) = run.scenario.room.walls.first { it.id == id }

    private fun voxelOf(run: BenchRun, i: Int): Int {
        val sz = run.map.voxels.sizeM; val p = run.map.points
        return run.map.voxels.find(VoxelIndex.key(floor(p.x[i] / sz).toInt(), floor(p.y[i] / sz).toInt(), floor(p.z[i] / sz).toInt()))
    }

    /** Etichetta di maggioranza (e purezza) dei punti di una superficie R2. */
    private fun majority(run: BenchRun, surfaceId: Int): Pair<Int, Double> {
        val idx = SurfaceExtractor.pointsOf(run.map, run.surfaces.surfaces[surfaceId].memberVoxels)
        if (idx.isEmpty()) return Label.NONE to 0.0
        val h = HashMap<Int, Int>(); for (i in idx) h.merge(run.pointLabels[i], 1, Int::plus)
        val best = h.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).first()
        return best.key to best.value.toDouble() / idx.size
    }

    fun evaluate(run: BenchRun, ev: EvalParams = EvalParams()): BenchMetrics {
        val room = run.scenario.room
        val pts = run.map.points
        val n = pts.size
        val vox = IntArray(n) { voxelOf(run, it) }
        val surf = run.surfaces.surfaces
        val surfLabel = surf.associate { it.id to majority(run, it.id).first }
        fun surfaceOfPoint(i: Int) = run.surfaces.voxelSurface[vox[i]]

        // ---------------------------------------------------------------- R1
        val wallErr = mutableListOf<Double>()
        for (i in 0 until n) { val l = run.pointLabels[i]; if (Label.isWall(l)) wallErr.add(abs(wallOf(run, Label.wallId(l)).dist(pts.x[i].toDouble(), pts.z[i].toDouble()))) }
        wallErr.sort()
        val coverage = room.walls.map { w ->
            val cu = max(1, (w.length / ev.cellM).toInt()); val cv = max(1, (room.heightM / ev.cellM).toInt())
            val open = BooleanArray(cu * cv) { c -> val u = (c % cu + 0.5) * ev.cellM; val y = (c / cu + 0.5) * ev.cellM; w.openings.any { u in it.fromM..it.toM && y in it.bottomM..it.topM } }
            val sens = BooleanArray(cu * cv); val acc = BooleanArray(cu * cv)
            fun cell(x: Double, y: Double, z: Double): Int { val u = w.along(x, z); if (u < 0 || u >= w.length || y < 0 || y >= room.heightM) return -1; return min(cv - 1, (y / ev.cellM).toInt()) * cu + min(cu - 1, (u / ev.cellM).toInt()) }
            for (f in run.sim.frames) for (i in f.mm.indices) if (f.mm[i] > 0 && f.labels[i] == Label.wall(w.id)) { val c = cell(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 1].toDouble(), f.hitXYZ[3 * i + 2].toDouble()); if (c >= 0) sens[c] = true }
            for (i in 0 until n) if (run.pointLabels[i] == Label.wall(w.id)) { val c = cell(pts.x[i].toDouble(), pts.y[i].toDouble(), pts.z[i].toDouble()); if (c >= 0) acc[c] = true }
            val tot = open.count { !it }.coerceAtLeast(1)
            Triple(w.id, (0 until cu * cv).count { !open[it] && sens[it] }.toDouble() / tot, (0 until cu * cv).count { !open[it] && acc[it] }.toDouble() / tot)
        }
        val r1 = R1Metrics(
            run.r1.gtHits, run.r1.generated, run.r1.accepted, run.r1.rejected,
            (run.r1.accepted["WALL"] ?: 0).toDouble() / max(1, run.r1.gtHits["WALL"] ?: 0),
            if (wallErr.isEmpty()) 0.0 else wallErr.average(), if (wallErr.isEmpty()) 0.0 else sqrt(wallErr.sumOf { it * it } / wallErr.size),
            if (wallErr.isEmpty()) 0.0 else wallErr[(0.95 * (wallErr.size - 1)).toInt()], wallErr.lastOrNull() ?: 0.0, coverage,
        )

        // ---------------------------------------------------------------- R2 (punto per punto, etichette esatte)
        val mixed = HashMap<Int, MutableSet<Int>>()
        for (i in 0 until n) mixed.getOrPut(vox[i]) { HashSet() }.add(run.pointLabels[i])
        val outcome = arrayOfNulls<String>(n)
        val outcomes = HashMap<String, Int>(); val contamination = HashMap<String, Int>(); val perWall = HashMap<Int, HashMap<String, Int>>()
        var tp = 0; var inStructural = 0; var wallAcc = 0
        for (i in 0 until n) {
            val l = run.pointLabels[i]; val sid = surfaceOfPoint(i)
            val structural = sid >= 0 && surf[sid].kind == SurfaceKind.VERTICAL_STRUCTURAL
            if (structural) inStructural++
            if (!Label.isWall(l)) { if (structural) contamination.merge(Label.cls(l), 1, Int::plus); continue }
            wallAcc++
            val o = wallOutcome(run, i, l, sid, surfLabel, mixed)
            outcome[i] = o; outcomes.merge(o, 1, Int::plus); perWall.getOrPut(Label.wallId(l)) { HashMap() }.merge(o, 1, Int::plus)
            if (o == "TP") tp++
        }
        val falseStructural = surf.count { it.kind == SurfaceKind.VERTICAL_STRUCTURAL && !Label.isWall(surfLabel.getValue(it.id)) }
        val r2 = R2Metrics(
            wallAcc, outcomes.toSortedMap(), contamination.toSortedMap(), inStructural,
            tp.toDouble() / max(1, wallAcc), tp.toDouble() / max(1, run.r1.gtHits["WALL"] ?: 0), tp.toDouble() / max(1, inStructural),
            contamination.values.sum().toDouble() / max(1, inStructural), falseStructural, run.trace.mismatches,
            perWall.keys.sorted().associateWith { perWall.getValue(it).toSortedMap() },
            room.walls.associate { g -> g.id to surf.filter { surfLabel[it.id] == Label.wall(g.id) }.sortedByDescending { it.samples }.map { s -> "S${s.id} ${s.kind} ${s.samples} punti, ${f(s.areaM2)} m²: " + s.reasons.take(4).joinToString(" · ") } },
        )

        // ---------------------------------------------------------------- R3 (associazione esatta via etichette dei punti sorgente)
        val r3ToGt = HashMap<Int, Int>(); var impure = 0
        val falseWalls = mutableListOf<String>()
        for (w in run.walls.walls) {
            val h = HashMap<Int, Int>()
            for (sid in w.sourceSurfaceIds) for (i in SurfaceExtractor.pointsOf(run.map, surf[sid].memberVoxels)) h.merge(run.pointLabels[i], 1, Int::plus)
            val tot = h.values.sum()
            val best = h.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).firstOrNull()
            if (best != null && best.value < 0.8 * tot) impure++
            if (best != null && Label.isWall(best.key)) r3ToGt[w.id] = Label.wallId(best.key)
            else falseWalls.add("W${w.id}: maggioranza ${best?.let { Label.cls(it.key) } ?: "-"} (${f(100.0 * (best?.value ?: 0) / max(1, tot), 0)}%)")
        }
        val wallMetrics = room.walls.map { g -> wallMetric(run, g, run.walls.walls.filter { r3ToGt[it.id] == g.id }) }
        val r3 = R3Metrics(wallMetrics, falseWalls, run.walls.walls.size, impure)

        // ---------------------------------------------------------------- angoli
        val corners = room.corners.flatMap { c -> cornerMetrics(run, c, outcome, vox, wallMetrics, r3ToGt, ev) }

        // ---------------------------------------------------------------- aperture
        val openings = room.walls.flatMap { w -> w.openings.map { o -> openingMetrics(run, w, o, outcome, r3ToGt, ev) } }

        // ---------------------------------------------------------------- R4
        val r4 = r4Metrics(run, r3ToGt, ev)

        // ---------------------------------------------------------------- globali
        val covered = room.walls.sumOf { g -> union(run.walls.walls.filter { r3ToGt[it.id] == g.id }.flatMap { w -> w.geometry.observedSpans.map { sp -> proj(g, w, sp.fromU, sp.toU) } }, g.length) }
        val r3Obs = run.walls.walls.sumOf { it.geometry.observedLengthM }
        val r3ObsOk = run.walls.walls.filter { it.id in r3ToGt }.sumOf { it.geometry.observedLengthM }
        val errs = wallMetrics.flatMap { listOfNotNull(it.startErrorM?.let(::abs), it.endErrorM?.let(::abs), it.positionErrorM?.let(::abs)) }
        val openingsSignalled = openings.count { it.r3Signal.startsWith("SEGNALATA") }
        val global = GlobalMetrics(
            covered / room.perimeterM, if (r3Obs > 0) r3ObsOk / r3Obs else 0.0,
            wallMetrics.count { it.detected }.toDouble() / room.walls.size, if (run.walls.walls.isEmpty()) 0.0 else r3ToGt.size.toDouble() / run.walls.walls.size,
            r4.correct.toDouble() / max(1, r4.gtCorners), if (r4.correct + r4.wrong == 0) 0.0 else r4.correct.toDouble() / (r4.correct + r4.wrong),
            if (openings.isEmpty()) null else openingsSignalled.toDouble() / openings.size, openingFalsePositives(run, r3ToGt, ev),
            occlusion(run, r3ToGt), r4.closureVerdict.startsWith("CLOSED falsamente"),
            if (errs.isEmpty()) 0.0 else errs.average(), errs.maxOrNull() ?: 0.0,
        )
        return BenchMetrics(run.scenario.id, run.params.variant.id, r1, r2, corners, openings, r3, r4, global)
    }

    // ----------------------------------------------------------------------------------- R2: esito di un punto di parete

    private fun wallOutcome(run: BenchRun, i: Int, label: Int, sid: Int, surfLabel: Map<Int, Int>, mixed: Map<Int, Set<Int>>): String {
        val surf = run.surfaces.surfaces
        val v = voxelOf(run, i)
        if (sid >= 0) {
            val k = surf[sid].kind
            val ml = surfLabel.getValue(sid)
            return when {
                k == SurfaceKind.VERTICAL_STRUCTURAL && ml == label -> "TP"
                k == SurfaceKind.VERTICAL_STRUCTURAL && Label.isWall(ml) -> if (mixed[v].orEmpty().contains(ml)) Loss.VOXELIZATION.name else "WRONG_WALL"
                k == SurfaceKind.VERTICAL_STRUCTURAL -> "IN_FALSE_WALL"
                k == SurfaceKind.OBJECT -> "OBJECT"
                k == SurfaceKind.VERTICAL_OBJECT -> "VERTICAL_OBJECT"
                k == SurfaceKind.UNKNOWN -> "UNKNOWN"
                else -> if (mixed[v].orEmpty().contains(ml)) Loss.VOXELIZATION.name else "IN_${k.name}"
            }
        }
        val t = run.trace
        if (t.normals[v] == null) return Loss.INSUFFICIENT_EVIDENCE.name
        val mine = run.surfaces.surfaces.filter { surfLabel[it.id] == label }.map { it.id }.toSet()
        val att = t.attempts[v].orEmpty().filter { a -> a.region >= 0 && t.regionSurface[a.region] in mine }
        // In una regione poi scartata (piccola): ordine di assegnazione se la parete ha trovato il voxel già preso, altrimenti crescita.
        if (t.region[v] >= 0) return if (att.any { it.reason.startsWith("taken") }) Loss.ASSIGNMENT_ORDER.name else Loss.REGION_GROWING.name
        val first = att.firstOrNull() ?: return if (mine.isEmpty()) "NO_SURFACE" else Loss.REGION_GROWING.name
        return when (first.reason) {
            "variation", "angle" -> Loss.NORMAL_THRESHOLD.name
            "distance" -> Loss.DISTANCE_THRESHOLD.name
            "no-normal" -> Loss.INSUFFICIENT_EVIDENCE.name
            else -> Loss.SURFACE_CONFLICT.name
        }
    }

    // ----------------------------------------------------------------------------------------- R3

    /** Proiezione di un tratto [u0, u1] della parete R3 [w] sulla parete vera [g] (coordinate lungo g). */
    private fun proj(g: GtWall, w: EstimatedWall, u0: Double, u1: Double): Pair<Double, Double> {
        val a = w.geometry.pointAt(u0); val b = w.geometry.pointAt(u1)
        val p = g.along(a[0], a[1]); val q = g.along(b[0], b[1])
        return min(p, q) to max(p, q)
    }

    /** Lunghezza dell'unione di intervalli, ritagliata a [0, len]. */
    private fun union(iv: List<Pair<Double, Double>>, len: Double): Double {
        var tot = 0.0; var cur = Double.NEGATIVE_INFINITY
        for ((a0, b0) in iv.map { max(0.0, it.first) to min(len, it.second) }.filter { it.second > it.first }.sortedBy { it.first }) {
            val a = max(a0, cur); if (b0 > a) { tot += b0 - a; cur = b0 }
        }
        return tot
    }

    private fun wallMetric(run: BenchRun, g: GtWall, frags: List<EstimatedWall>): WallMetrics {
        if (frags.isEmpty()) return WallMetrics(g.id, g.length, false, 0, 0.0, 0.0, g.length, 1.0, null, null, null, "-", null, null, "-", null)
        val ext = frags.map { proj(g, it, it.geometry.startU, it.geometry.endU) }
        val extent = union(ext, g.length)
        val obs = frags.sumOf { it.geometry.observedLengthM }
        val main = frags.maxBy { it.geometry.observedLengthM }
        val cos = abs(main.geometry.ux * g.ux + main.geometry.uz * g.uz)
        val orient = Math.toDegrees(acos(min(1.0, cos)))
        val pos = abs(g.dist(main.geometry.cx, main.geometry.cz))
        // Estremità vere: u = 0 (inizio) e u = L (fine). L'estremità R3 più vicina, con il suo stato e la sua σ.
        data class E(val u: Double, val state: EndState, val reason: EndReason, val sigma: Double)
        val ends = frags.flatMap { w -> listOf(w.start, w.end).map { e -> val q = w.geometry.pointAt(e.u); E(g.along(q[0], q[1]), e.state, e.reason, e.sigmaM) } }
        val s = ends.minBy { it.u }; val e = ends.maxBy { it.u }
        return WallMetrics(
            g.id, g.length, true, frags.size, obs, extent, g.length - extent, (g.length - extent) / g.length, orient, pos,
            s.u, "${s.state}/${s.reason}", s.sigma, g.length - e.u, "${e.state}/${e.reason}", e.sigma,
        )
    }

    // -------------------------------------------------------------------------------------- angoli

    private fun cornerMetrics(run: BenchRun, c: GtCorner, outcome: Array<String?>, vox: IntArray, wm: List<WallMetrics>, r3ToGt: Map<Int, Int>, ev: EvalParams): List<CornerMetrics> {
        val room = run.scenario.room
        val pts = run.map.points
        return listOf(c.wallIn to "fine", c.wallOut to "inizio").map { (wid, side) ->
            val g = wallOf(run, wid)
            // Distanza dall'angolo lungo la parete (0 all'angolo).
            fun dc(x: Double, z: Double): Double { val u = g.along(x, z); return if (side == "fine") g.length - u else u }
            var sensor: Double? = null
            for (f in run.sim.frames) for (i in f.mm.indices) if (f.mm[i] > 0 && f.labels[i] == Label.wall(wid)) {
                val y = f.hitXYZ[3 * i + 1]; if (y < 0.2 || y > room.heightM - 0.2) continue
                val d = dc(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()); if (sensor == null || d < sensor) sensor = d
            }
            var r1: Double? = null; var r2: Double? = null
            var zone = 0; var lostNormal = 0; var lostGrow = 0; var wrong = 0; var objUnk = 0; var assigned = 0
            for (i in 0 until pts.size) {
                if (run.pointLabels[i] != Label.wall(wid)) continue
                val y = pts.y[i]; if (y < 0.2 || y > room.heightM - 0.2) continue
                val d = dc(pts.x[i].toDouble(), pts.z[i].toDouble())
                if (r1 == null || d < r1) r1 = d
                val o = outcome[i]
                if (o == "TP" && (r2 == null || d < r2)) r2 = d
                if (d > ev.zoneM) continue
                zone++
                when (o) {
                    "TP" -> assigned++
                    Loss.NORMAL_THRESHOLD.name, Loss.INSUFFICIENT_EVIDENCE.name -> lostNormal++
                    Loss.REGION_GROWING.name, Loss.ASSIGNMENT_ORDER.name, Loss.DISTANCE_THRESHOLD.name, "NO_SURFACE" -> lostGrow++
                    Loss.VOXELIZATION.name, Loss.SURFACE_CONFLICT.name, "WRONG_WALL", "IN_FALSE_WALL", "IN_FLOOR", "IN_CEILING" -> wrong++
                    else -> objUnk++
                }
            }
            // Pixel del sensore vicino all'angolo che R1 ha scartato.
            var sensorZone = 0
            for (f in run.sim.frames) for (i in f.mm.indices) if (f.mm[i] > 0 && f.labels[i] == Label.wall(wid)) {
                val y = f.hitXYZ[3 * i + 1]; if (y < 0.2 || y > room.heightM - 0.2) continue
                if (dc(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()) <= ev.zoneM) sensorZone++
            }
            // Estremità R3 dal lato dell'angolo.
            val frags = run.walls.walls.filter { r3ToGt[it.id] == wid }
            var r3: Double? = null; var st = "-"; var sg: Double? = null
            for (w in frags) for (e in listOf(w.start, w.end)) {
                val q = w.geometry.pointAt(e.u); val d = dc(q[0], q[1])
                if (r3 == null || d < r3) { r3 = d; st = "${e.state}/${e.reason}"; sg = e.sigmaM }
            }
            CornerMetrics(c.id, c.x, c.z, wid, side, sensor, r1, r2, r3, st, sg, zone, max(0, sensorZone - zone), lostNormal, lostGrow, wrong, objUnk, assigned)
        }
    }

    // ------------------------------------------------------------------------------------- aperture

    private fun openingMetrics(run: BenchRun, g: GtWall, o: GtOpening, outcome: Array<String?>, r3ToGt: Map<Int, Int>, ev: EvalParams): OpeningMetrics {
        val pts = run.map.points
        var zone = 0; var lost = 0; val by = HashMap<String, Int>(); var outsideIn = 0
        for (i in 0 until pts.size) {
            val x = pts.x[i].toDouble(); val z = pts.z[i].toDouble(); val y = pts.y[i].toDouble()
            val u = g.along(x, z)
            val nearEdge = (abs(u - o.fromM) <= ev.zoneM || abs(u - o.toM) <= ev.zoneM) && y in (o.bottomM - ev.zoneM)..(o.topM + ev.zoneM) && abs(g.dist(x, z)) <= 0.3
            if (!nearEdge) continue
            val l = run.pointLabels[i]
            val sid = run.surfaces.voxelSurface[voxelOf(run, i)]
            if (l == Label.OUTSIDE && sid >= 0 && run.surfaces.surfaces[sid].kind == SurfaceKind.VERTICAL_STRUCTURAL) outsideIn++
            if (l != Label.wall(g.id)) continue
            zone++
            val oc = outcome[i] ?: continue
            if (oc != "TP") { lost++; by.merge(oc, 1, Int::plus) }
        }
        // Segnale R3: gap SEEN_THROUGH che copre l'apertura, o estremità FREE_BEYOND vicino a una spalla.
        var signal = "NON segnalata"
        for (w in run.walls.walls.filter { r3ToGt[it.id] == g.id }) {
            for (gp in w.gaps) { val (a, b) = proj(g, w, gp.fromU, gp.toU); if (min(b, o.toM) - max(a, o.fromM) > 0) signal = if (gp.reason == GapReason.SEEN_THROUGH) "SEGNALATA: gap visto attraverso" else if (!signal.startsWith("SEGNALATA")) "gap ${gp.reason} (non come apertura)" else signal }
            for (e in listOf(w.start, w.end)) {
                val q = w.geometry.pointAt(e.u); val u = g.along(q[0], q[1])
                if (e.reason == EndReason.FREE_BEYOND && (abs(u - o.fromM) <= ev.openingEdgeM || abs(u - o.toM) <= ev.openingEdgeM) && !signal.startsWith("SEGNALATA")) signal = "SEGNALATA: estremità FREE_BEYOND sulla spalla"
            }
        }
        return OpeningMetrics(o.id, g.id, o.kind, zone, lost, by.toSortedMap(), outsideIn, signal)
    }

    /** Segnali di apertura di R3 che NON corrispondono ad aperture vere (falsi positivi). */
    private fun openingFalsePositives(run: BenchRun, r3ToGt: Map<Int, Int>, ev: EvalParams): Int {
        var fp = 0
        for (w in run.walls.walls) {
            val g = r3ToGt[w.id]?.let { wallOf(run, it) } ?: continue
            fun nearOpening(u0: Double, u1: Double) = g.openings.any { min(u1, it.toM + ev.openingEdgeM) - max(u0, it.fromM - ev.openingEdgeM) > 0 }
            for (gp in w.gaps.filter { it.reason == GapReason.SEEN_THROUGH }) { val (a, b) = proj(g, w, gp.fromU, gp.toU); if (!nearOpening(a, b)) fp++ }
            for (e in listOf(w.start, w.end).filter { it.reason == EndReason.FREE_BEYOND }) { val q = w.geometry.pointAt(e.u); val u = g.along(q[0], q[1]); if (!nearOpening(u, u)) fp++ }
        }
        return fp
    }

    /** Tratti di parete vera senza nessun punto del sensore ma con un mobile/pannello davanti: come li rappresenta R3. */
    private fun occlusion(run: BenchRun, r3ToGt: Map<Int, Int>): Map<String, Int> {
        val room = run.scenario.room
        val out = HashMap<String, Int>()
        for (g in room.walls) {
            val bins = max(1, (g.length / 0.10).toInt())
            val seen = BooleanArray(bins)
            for (f in run.sim.frames) for (i in f.mm.indices) if (f.mm[i] > 0 && f.labels[i] == Label.wall(g.id)) {
                val u = g.along(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()); val b = (u / 0.10).toInt(); if (b in 0 until bins) seen[b] = true
            }
            val frags = run.walls.walls.filter { r3ToGt[it.id] == g.id }
            for (b in 0 until bins) {
                if (seen[b]) continue
                val u = (b + 0.5) * 0.10
                if (g.openings.any { u in it.fromM..it.toM && it.bottomM <= 0.1 }) continue
                val q = g.pointAt(u)
                val occluded = room.boxes.any { bx -> listOf(bx.x0 to bx.z0, bx.x1 to bx.z1, bx.x0 to bx.z1, bx.x1 to bx.z0).let { cs -> cs.minOf { g.along(it.first, it.second) } <= u + 0.05 && cs.maxOf { g.along(it.first, it.second) } >= u - 0.05 && cs.any { g.dist(it.first, it.second) in 0.0..1.5 } } } ||
                    room.panels.any { p -> min(g.along(p.ax, p.az), g.along(p.bx, p.bz)) <= u && max(g.along(p.ax, p.az), g.along(p.bx, p.bz)) >= u && g.dist(p.ax, p.az) in 0.0..1.5 }
                if (!occluded) continue
                // Rappresentazione in R3 del tratto occluso.
                var rep = "nessuna parete R3 (tratto perso)"
                for (w in frags) {
                    val (a, bb) = proj(g, w, w.geometry.startU, w.geometry.endU)
                    for (gp in w.gaps) { val (ga, gb) = proj(g, w, gp.fromU, gp.toU); if (u in ga..gb) rep = "gap ${gp.reason}" }
                    if (rep.startsWith("nessuna") && u in a..bb) rep = "dentro un tratto osservato"
                }
                if (rep.startsWith("nessuna") && frags.isNotEmpty()) {
                    val e = frags.flatMap { w -> listOf(w.start, w.end).map { en -> val p = w.geometry.pointAt(en.u); abs(g.along(p[0], p[1]) - u) to en.reason } }.minBy { it.first }
                    rep = "oltre un'estremità ${e.second}"
                }
                out.merge(rep, 1, Int::plus)
            }
        }
        return out.toSortedMap()
    }

    // ------------------------------------------------------------------------------------------- R4

    private fun r4Metrics(run: BenchRun, r3ToGt: Map<Int, Int>, ev: EvalParams): R4Metrics {
        val room = run.scenario.room
        val next = room.corners.associate { it.wallIn to it }
        var correct = 0; var correctAmb = 0; var wrong = 0; var obs = 0; var inf = 0; var swOk = 0; var swBad = 0
        val found = HashSet<Int>()
        val lines = mutableListOf<String>()
        for (l in run.r4.candidates.filter { it.decision != LinkDecision.REJECTED }.sortedWith(compareBy({ it.fromWall }, { it.toWall }))) {
            val ga = r3ToGt[l.fromWall]; val gb = r3ToGt[l.toWall]
            if (l.kind == LinkKind.SAME_WALL) {
                if (ga != null && ga == gb) swOk++ else swBad++
                lines.add("W${l.fromWall}→W${l.toWall} SAME_WALL ${l.decision}: " + if (ga != null && ga == gb) "corretto (stessa parete vera $ga)" else "SBAGLIATO (pareti vere $ga e $gb)")
                continue
            }
            val gc = ga?.let { next[it] }
            val ok = ga != null && gb != null && gc != null && gc.wallOut == gb && l.cornerX != null &&
                sqrt((l.cornerX!! - gc.x) * (l.cornerX!! - gc.x) + (l.cornerZ!! - gc.z) * (l.cornerZ!! - gc.z)) <= ev.cornerTolM
            val err = if (gc != null && l.cornerX != null) sqrt((l.cornerX!! - gc.x) * (l.cornerX!! - gc.x) + (l.cornerZ!! - gc.z) * (l.cornerZ!! - gc.z)) else null
            if (ok) {
                if (l.decision == LinkDecision.ACCEPTED) { correct++; found.add(gc!!.id); if (l.support.name == "OBSERVED") obs++ else inf++ } else correctAmb++
            } else if (l.decision == LinkDecision.ACCEPTED) wrong++
            lines.add("W${l.fromWall}→W${l.toWall} ${l.decision} ${l.support}: pareti vere $ga→$gb · " + (if (ok) "CORRETTO" else "SBAGLIATO") + (err?.let { " · errore angolo ${f(it * 100, 1)} cm" } ?: ""))
        }
        // Il sensore ha visto ogni angolo (punti di entrambe le pareti vicino all'angolo)?
        val closable = room.corners.all { c ->
            listOf(c.wallIn, c.wallOut).all { wid ->
                val g = wallOf(run, wid)
                run.sim.frames.any { f -> f.mm.indices.any { i -> f.mm[i] > 0 && f.labels[i] == Label.wall(wid) && sqrt((f.hitXYZ[3 * i] - c.x).let { it * it } + (f.hitXYZ[3 * i + 2] - c.z).let { it * it }) <= ev.sensorCornerM + 1e-9 && g.length > 0 } }
            }
        }
        val main = run.r4.main
        val state = main?.state?.name ?: "OPEN"
        val polygonOk = main?.closure != null && main.corners.size == room.corners.size &&
            main.corners.all { pc -> room.corners.any { c -> sqrt((pc.x - c.x) * (pc.x - c.x) + (pc.z - c.z) * (pc.z - c.z)) <= ev.cornerTolM } }
        val verdict = when {
            main?.state == PerimeterState.CLOSED && closable && polygonOk -> "CLOSED correttamente"
            main?.state == PerimeterState.CLOSED -> "CLOSED falsamente" + (if (!closable) " (il sensore non ha visto tutti gli angoli)" else " (poligono diverso dal vero)")
            closable -> "$state falsamente (chiusura mancata: il sensore ha visto tutti gli angoli)"
            else -> "$state correttamente (il sensore non ha visto tutti gli angoli)"
        }
        return R4Metrics(room.corners.size, correct, correctAmb, wrong, room.corners.size - found.size, obs, inf, swOk, swBad, closable, state, verdict, lines)
    }

    internal fun f(v: Double, d: Int = 2) = "%.${d}f".format(java.util.Locale.ROOT, v)
}
