package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.recording.ArCameraProjection
import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.CaptureIndex
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.IntrinsicsSource
import com.sagoma.planimetria.scan.recording.PoseMatch
import com.sagoma.planimetria.scan.recording.PoseSample
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.StreamStatus
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * STUDIO DIAGNOSTICO DEI BORDI (non cambia la ricostruzione): la posa giusta della depth raw si vede meglio sui bordi di profondità
 * netti (stipite, spigolo), dove uno spostamento della camera sposta il bordo nell'immagine di molti pixel.
 *
 * Riferimento NON circolare: dalle raw RECENTI (età < soglia, A ≈ C) si raccolgono i punti 3D del LATO VICINO dei salti di profondità
 * (lo spigolo fisico, che non dipende dal punto di vista). Per ogni altra raw: pixel di bordo osservati; il bordo di riferimento
 * proiettato con le pose A, B, C, D; scarto orizzontale (px) osservato − previsto; λ = (osservato − A) / (C − A) (0 = A, 1 = C).
 * In più: residuo 3D del lato vicino rispetto al bordo di riferimento e del lato lontano rispetto alle superfici, e lo scarto medio
 * 3D proiettato sulla direzione del movimento A → C (errore sistematico di traslazione).
 */
data class RawEdgeParams(
    /** Salto di profondità tra due pixel vicini in orizzontale: almeno questo e almeno [jumpRatio] della distanza. */
    val minJumpM: Double = 0.25,
    val jumpRatio: Double = 0.15,
    val referenceAgeMs: Double = 40.0,
    /** Se le raw di riferimento sono meno di questo numero, la soglia d'età sale a [fallbackAgeMs]. */
    val minReferenceRaws: Int = 6,
    val fallbackAgeMs: Double = 70.0,
    val oldAgeMs: Double = 150.0,
    /** Ricerca del bordo previsto nella stessa riga (±1) entro questa distanza (px). */
    val searchPx: Double = 25.0,
    /** Casi "con maggiore movimento": spostamento previsto A → C sul bordo almeno di questi pixel. */
    val strongShiftPx: Double = 6.0,
    /** Per entrare nelle prove: spostamento previsto A → C sul bordo almeno di questi pixel (sotto, A e C non si distinguono). */
    val minShiftPx: Double = 2.0,
)

/** Una raw valutata sui bordi. Scarti in pixel (orizzontali, con segno): osservato − previsto con ciascuna posa. */
data class EdgeCase(
    val depthSeq: Int,
    val ageMs: Double,
    val aSeq: Int,
    val cSeq: Int,
    val edgePixels: Int,
    /** Colonna mediana del bordo osservato (px) e scarti mediani osservato − previsto per A, B, C, D (px). */
    val observedU: Double?,
    val offsetPx: Map<RawAssociation, Double?>,
    val absOffsetPx: Map<RawAssociation, Double?>,
    /** Spostamento previsto del bordo tra A e C (px, mediana di uA − uC sugli stessi punti di riferimento). */
    val predictedShiftPx: Double?,
    /** λ = (osservato − previsto A) / (previsto C − previsto A): 0 = A, 1 = C. */
    val lambda: Double?,
    /** Residuo 3D mediano del lato vicino rispetto al bordo di riferimento (m) e del lato lontano rispetto alle superfici (m). */
    val nearResidualM: Map<RawAssociation, Double?>,
    val farResidualM: Map<RawAssociation, Double?>,
    /** Scarto 3D medio (osservato − riferimento) proiettato sulla direzione del movimento A → C (m), e lunghezza del movimento (m). */
    val alongMotionM: Map<RawAssociation, Double?>,
    val motionM: Double,
)

class RawEdgeResult(
    val referenceRaws: Int,
    val referenceAgeMs: Double,
    val referenceEdgePoints: Int,
    val ageDistributionMs: List<Double>,
    val cases: List<EdgeCase>,
    val verdict: String,
    val confidence: String,
    val evidence: List<String>,
)

object RawEdgeStudy {
    private class Raw(
        val seq: Int, val rawTs: Long, val ageNs: Long, val aSeq: Int, val cSeq: Int, val mm: IntArray, val conf: ByteArray?, val k: CameraIntrinsics,
    )

    /** Un pixel di bordo: colonna e riga del lato vicino, profondità (m) del lato vicino e di quello lontano, colonna del lato lontano. */
    private class EdgePx(val u: Int, val v: Int, val nearZ: Double, val farU: Int, val farZ: Double)

    fun run(r: ScanRecording, blobs: DatasetBlobs, rp: ReconParams = ReconParams(), p: RawEdgeParams = RawEdgeParams()): RawEdgeResult {
        val index = CaptureIndex(r)
        val poses = r.poses.associateBy { it.seq }
        val texture = r.header.camera?.textureIntrinsics
        val raws = mutableListOf<Raw>()
        for (d in r.depth.sortedBy { it.seq }) {
            if (d.rawStatus == StreamStatus.DUPLICATE || d.rawPath == null) continue
            val bytes = blobs.read(d.rawPath) ?: continue
            val a = index.rawPoseFor(d) ?: continue
            if (a.kind == PoseMatch.UNAVAILABLE || a.pose == null) continue
            val c = d.poseFrameSeq ?: index.poseFor(d)?.item?.seq ?: continue
            val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
            if (bytes.size != w * h * 2) continue
            val fk = d.intrinsics?.takeIf { d.intrinsicsSource == IntrinsicsSource.TEXTURE_SCALED } ?: texture?.scaledTo(d.width, d.height) ?: continue
            val k = if (w == fk.width && h == fk.height) fk else fk.scaledTo(w, h)
            val conf = d.confidencePath?.let { blobs.read(it) }?.takeIf { it.size == w * h }
            val rawTs = d.rawTimestampNs ?: continue
            raws.add(Raw(d.seq, rawTs, d.timestampNs - rawTs, a.pose.seq, c, DepthRaw.decode(bytes, w, h), conf, k))
        }
        val ages = raws.map { it.ageNs / 1e6 }.sorted()

        // 1. Riferimento: lato vicino dei bordi nelle raw recenti (A ≈ C), seq pari; soglia d'età più larga se sono troppo poche.
        var refAge = p.referenceAgeMs
        var refRaws = raws.filter { it.ageNs < refAge * 1e6 && it.seq % 2 == 0 }
        if (refRaws.size < p.minReferenceRaws) { refAge = p.fallbackAgeMs; refRaws = raws.filter { it.ageNs < refAge * 1e6 && it.seq % 2 == 0 } }
        val refSeqs = refRaws.map { it.seq }.toSet()
        val refEdge = mutableListOf<DoubleArray>()
        for (raw in refRaws) {
            val pose = poses[raw.aSeq]?.camera ?: continue
            for (e in edges(raw, rp, p)) refEdge.add(ArCameraProjection.unproject(pose, raw.k, e.u.toDouble(), e.v.toDouble(), e.nearZ))
        }
        val refGrid = PointGrid(refEdge, 0.05)
        // Superfici di riferimento (per il lato lontano), con le stesse raw: R1/R2 come nello studio della posa.
        val refMap = GlobalMap.build(r.copy(depth = r.depth.filter { it.seq in refSeqs }), blobs, rp)
        val refSurf = SurfaceExtractor.extract(refMap, r).surfaces.filter { it.kind != SurfaceKind.UNKNOWN && it.areaM2 >= 0.2 }

        // 2. Ogni altra raw: bordo osservato contro bordo previsto con ciascuna posa.
        val cases = mutableListOf<EdgeCase>()
        for (raw in raws.filter { it.seq !in refSeqs }) {
            val obs = edges(raw, rp, p)
            if (obs.size < 5) continue
            val poseFor = RawAssociation.entries.associateWith { a -> poseOf(a, raw, poses) }
            // Bordo di riferimento proiettato con ogni posa: righe → colonne previste.
            val predicted = poseFor.mapValues { (_, pose) -> pose?.let { projectRows(refEdge, it, raw.k) } }
            val offsets = HashMap<RawAssociation, Double?>(); val absOff = HashMap<RawAssociation, Double?>()
            val perPoint = HashMap<RawAssociation, DoubleArray?>()
            for (a in RawAssociation.entries) {
                val rows = predicted[a] ?: run { offsets[a] = null; absOff[a] = null; perPoint[a] = null; null } ?: continue
                val d = DoubleArray(obs.size) { Double.NaN }
                for ((i, e) in obs.withIndex()) nearestInRows(rows, e.u.toDouble(), e.v, p.searchPx)?.let { d[i] = e.u - it }
                val valid = d.filter { !it.isNaN() }
                perPoint[a] = d
                offsets[a] = if (valid.size >= 5) Geo.median(valid.toDoubleArray()) else null
                absOff[a] = if (valid.size >= 5) Geo.median(valid.map { abs(it) }.toDoubleArray()) else null
            }
            // Spostamento previsto A → C e λ, sugli stessi punti osservati che hanno un bordo previsto con entrambe le pose.
            val dA = perPoint[RawAssociation.A]; val dC = perPoint[RawAssociation.C]
            var shift: Double? = null; var lambda: Double? = null
            if (dA != null && dC != null) {
                val pairs = obs.indices.filter { !dA[it].isNaN() && !dC[it].isNaN() }
                if (pairs.size >= 5) {
                    // uA − uC = (obs − dA) − (obs − dC) = dC − dA
                    val s = Geo.median(pairs.map { dC[it] - dA[it] }.toDoubleArray())
                    shift = s
                    if (abs(s) >= 0.5) lambda = Geo.median(pairs.map { dA[it] / (dA[it] - dC[it]).let { den -> if (abs(den) < 1e-9) Double.NaN else den } }.filter { !it.isNaN() }.toDoubleArray())
                }
            }
            // Residui 3D: lato vicino contro il bordo di riferimento, lato lontano contro le superfici; scarto lungo il movimento.
            val near = HashMap<RawAssociation, Double?>(); val far = HashMap<RawAssociation, Double?>(); val along = HashMap<RawAssociation, Double?>()
            val pa = poses[raw.aSeq]?.camera; val pc = poses[raw.cSeq]?.camera
            val motion = if (pa != null && pc != null) doubleArrayOf(pc.x - pa.x, pc.y - pa.y, pc.z - pa.z) else doubleArrayOf(0.0, 0.0, 0.0)
            val motionLen = sqrt(motion[0] * motion[0] + motion[1] * motion[1] + motion[2] * motion[2])
            for (a in RawAssociation.entries) {
                val pose = poseFor[a] ?: run { near[a] = null; far[a] = null; along[a] = null; null } ?: continue
                val nr = mutableListOf<Double>(); val fr = mutableListOf<Double>(); val al = mutableListOf<Double>()
                for (e in obs) {
                    val w = ArCameraProjection.unproject(pose, raw.k, e.u.toDouble(), e.v.toDouble(), e.nearZ)
                    refGrid.nearest(w, 0.25)?.let { (q, dist) ->
                        nr.add(dist)
                        if (motionLen > 0.01) al.add(((w[0] - q[0]) * motion[0] + (w[1] - q[1]) * motion[1] + (w[2] - q[2]) * motion[2]) / motionLen)
                    }
                    val wf = ArCameraProjection.unproject(pose, raw.k, e.farU.toDouble(), e.v.toDouble(), e.farZ)
                    refSurf.mapNotNull { s -> abs(s.plane.distance(wf[0], wf[1], wf[2])).takeIf { it <= 0.3 } }.minOrNull()?.let { fr.add(it) }
                }
                near[a] = if (nr.size >= 5) Geo.median(nr.toDoubleArray()) else null
                far[a] = if (fr.size >= 5) Geo.median(fr.toDoubleArray()) else null
                along[a] = if (al.size >= 5) Geo.median(al.toDoubleArray()) else null
            }
            cases.add(EdgeCase(raw.seq, raw.ageNs / 1e6, raw.aSeq, raw.cSeq, obs.size, Geo.median(obs.map { it.u.toDouble() }.toDoubleArray()), offsets, absOff, shift, lambda, near, far, along, motionLen))
        }
        val (verdict, confidence, ev) = verdict(cases, p)
        return RawEdgeResult(refRaws.size, refAge, refEdge.size, ages, cases, verdict, confidence, ev)
    }

    private fun poseOf(a: RawAssociation, raw: Raw, poses: Map<Int, PoseSample>): RecPose? = when (a) {
        RawAssociation.A -> poses[raw.aSeq]?.camera
        RawAssociation.B -> poses[raw.aSeq + 1]?.camera
        RawAssociation.C -> poses[raw.cSeq]?.camera
        RawAssociation.D -> {
            val before = poses[raw.aSeq - 1]; val after = poses[raw.aSeq]
            if (before?.camera == null || after?.camera == null || after.timestampNs <= before.timestampNs) null
            else RawPoseStudy.interpolate(before.camera, after.camera, ((raw.rawTs - before.timestampNs).toDouble() / (after.timestampNs - before.timestampNs)).coerceIn(0.0, 1.0))
        }
    }

    /** Bordi: coppie di pixel vicini in orizzontale con un salto di profondità netto; il lato vicino è il pixel di bordo. */
    private fun edges(raw: Raw, rp: ReconParams, p: RawEdgeParams): List<EdgePx> {
        val w = raw.k.width; val h = raw.k.height
        fun ok(u: Int, v: Int): Double? {
            val mm = raw.mm[v * w + u]
            if (mm <= 0) return null
            val z = mm / 1000.0
            if (z < rp.minRangeM || z > rp.maxRangeM) return null
            raw.conf?.let { if ((it[v * w + u].toInt() and 0xFF) < rp.minRawConfidence) return null }
            return z
        }
        val out = mutableListOf<EdgePx>()
        for (v in 0 until h) for (u in 0 until w - 1) {
            val a = ok(u, v) ?: continue
            val b = ok(u + 1, v) ?: continue
            val jump = abs(a - b)
            if (jump < p.minJumpM || jump < p.jumpRatio * minOf(a, b)) continue
            if (a < b) out.add(EdgePx(u, v, a, u + 1, b)) else out.add(EdgePx(u + 1, v, b, u, a))
        }
        return out
    }

    /** Bordo di riferimento proiettato: per ogni riga dell'immagine, le colonne previste (ordinate). */
    private fun projectRows(points: List<DoubleArray>, pose: RecPose, k: CameraIntrinsics): Array<DoubleArray> {
        val rows = Array(k.height) { mutableListOf<Double>() }
        for (q in points) {
            val hit = ArCameraProjection.project(pose, k, q[0], q[1], q[2]) ?: continue
            val v = hit.v.roundToInt()
            if (v !in 0 until k.height || hit.u < -5 || hit.u > k.width + 5) continue
            rows[v].add(hit.u)
        }
        return Array(k.height) { rows[it].sorted().toDoubleArray() }
    }

    private fun nearestInRows(rows: Array<DoubleArray>, u: Double, v: Int, max: Double): Double? {
        var best: Double? = null
        for (vv in v - 1..v + 1) {
            if (vv !in rows.indices) continue
            val r = rows[vv]
            if (r.isEmpty()) continue
            var lo = 0; var hi = r.size - 1
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (r[mid] < u) lo = mid + 1 else hi = mid }
            for (i in maxOf(0, lo - 1)..minOf(r.size - 1, lo)) if (abs(r[i] - u) <= max && (best == null || abs(r[i] - u) < abs(best - u))) best = r[i]
        }
        return best
    }

    /**
     * Prove sui bordi, solo con le raw che hanno uno spostamento previsto A → C sul bordo di almeno [RawEdgeParams.minShiftPx]:
     * (1) scarto assoluto del bordo: C contro A raw per raw (test dei segni); (2) λ mediano (0 = A, 1 = C) e quante raw stanno dalla
     * parte di C (λ > 0,5); (3) residuo 3D del lato vicino: C contro A (test dei segni). Conclusione solo se tutte e tre concordano.
     */
    private fun verdict(cases: List<EdgeCase>, p: RawEdgeParams): Triple<String, String, List<String>> {
        val ev = mutableListOf<String>()
        val usable = cases.filter { (it.predictedShiftPx?.let { s -> abs(s) } ?: 0.0) >= p.minShiftPx }
        ev.add("Raw con spostamento previsto del bordo ≥ ${f(p.minShiftPx, 1)} px (le sole che possono distinguere A da C): ${usable.size} su ${cases.size}")
        val off = usable.mapNotNull { c -> val a = c.absOffsetPx[RawAssociation.A]; val cc = c.absOffsetPx[RawAssociation.C]; if (a != null && cc != null) a to cc else null }
        val cWins = off.count { it.second < it.first }
        val p1 = RawPoseStudy.signTestP(cWins, off.size)
        ev.add("Prova bordi 1 (scarto |osservato − previsto|): C minore di A in $cWins/${off.size} · p = ${f(p1, 4)}")
        val lambdas = usable.mapNotNull { it.lambda }
        val lMed = if (lambdas.isEmpty()) null else Geo.median(lambdas.toDoubleArray())
        val towardC = lambdas.count { it > 0.5 }
        val p2 = RawPoseStudy.signTestP(towardC, lambdas.size)
        ev.add("Prova bordi 2 (λ: 0 = A, 1 = C): mediana ${lMed?.let { f(it, 2) } ?: "—"} · dalla parte di C $towardC/${lambdas.size} · p = ${f(p2, 4)}")
        val near = usable.mapNotNull { c -> val a = c.nearResidualM[RawAssociation.A]; val cc = c.nearResidualM[RawAssociation.C]; if (a != null && cc != null) a to cc else null }
        val nWins = near.count { it.second < it.first }
        val p3 = RawPoseStudy.signTestP(nWins, near.size)
        ev.add("Prova bordi 3 (residuo 3D del lato vicino rispetto allo spigolo di riferimento): C minore di A in $nWins/${near.size} · p = ${f(p3, 4)}")
        val forC = listOf(off.size >= 8 && cWins >= 0.75 * off.size && p1 < 0.01, lambdas.size >= 8 && lMed != null && lMed >= 0.7 && p2 < 0.01, near.size >= 8 && nWins >= 0.75 * near.size && p3 < 0.01)
        val forA = listOf(off.size >= 8 && cWins <= 0.25 * off.size && p1 < 0.01, lambdas.size >= 8 && lMed != null && lMed <= 0.3 && p2 < 0.01, near.size >= 8 && nWins <= 0.25 * near.size && p3 < 0.01)
        val c = forC.count { it }; val a = forA.count { it }
        return when {
            c == 3 -> Triple("C supportata: sui bordi il contenuto della raw corrisponde alla posa del frame CORRENTE.", "alta (3 prove su 3, p < 0,01)", ev)
            a == 3 -> Triple("A supportata: sui bordi il contenuto della raw corrisponde alla posa del rawTimestampNs (regola M0.2).", "alta (3 prove su 3, p < 0,01)", ev)
            c == 2 && a == 0 -> Triple("Indicazione verso C, non tutte le prove sono significative.", "media", ev)
            a == 2 && c == 0 -> Triple("Indicazione verso A, non tutte le prove sono significative.", "media", ev)
            else -> Triple("NON CONCLUSIVO: anche sui bordi i dati non distinguono A da C.", "bassa", ev)
        }
    }

    private fun f(v: Double, d: Int) = formatDecimal(v, d, '.')

    fun report(res: RawEdgeResult, title: String): String = buildString {
        fun line(s: String = "") = append(s).append('\n')
        fun px(v: Double?) = v?.let { f(it, 1) } ?: "—"
        fun cm(v: Double?) = v?.let { f(it * 100, 1) } ?: "—"
        line("=== Studio dei BORDI della depth raw — $title ===")
        line("Riferimento: ${res.referenceRaws} raw recenti (< ${f(res.referenceAgeMs, 0)} ms, seq pari) · ${res.referenceEdgePoints} punti 3D del lato vicino dei bordi")
        val ages = res.ageDistributionMs.toDoubleArray()
        line("Età delle raw (depth filtrata − raw, ms): n ${ages.size} · min ${f(ages.firstOrNull() ?: 0.0, 0)} · p10 ${f(Geo.percentile(ages, 0.1), 0)} · mediana ${f(Geo.percentile(ages, 0.5), 0)} · p90 ${f(Geo.percentile(ages, 0.9), 0)} · max ${f(ages.lastOrNull() ?: 0.0, 0)}")
        val bins = listOf(0.0 to 40.0, 40.0 to 100.0, 100.0 to 150.0, 150.0 to 300.0, 300.0 to 1e9)
        line("Distribuzione: " + bins.joinToString(" · ") { (a, b) -> "${f(a, 0)}–${if (b > 1e8) "∞" else f(b, 0)} ms ${ages.count { it >= a && it < b }}" })
        line()
        for ((label, list) in listOf("recenti (< 150 ms)" to res.cases.filter { it.ageMs < 150 }, "vecchie (≥ 150 ms)" to res.cases.filter { it.ageMs >= 150 })) {
            line("-- Raw $label: ${list.size}")
            for (a in RawAssociation.entries) {
                val absOff = list.mapNotNull { it.absOffsetPx[a] }; val sOff = list.mapNotNull { it.offsetPx[a] }
                val near = list.mapNotNull { it.nearResidualM[a] }; val far = list.mapNotNull { it.farResidualM[a] }; val along = list.mapNotNull { it.alongMotionM[a] }
                line(
                    "  ${a.label.padEnd(42)} |scarto bordo| ${px(absOff.medianOrNull())} px (con segno ${px(sOff.medianOrNull())}) · lato vicino ${cm(near.medianOrNull())} cm · " +
                        "lato lontano ${cm(far.medianOrNull())} cm · scarto lungo il movimento ${cm(along.medianOrNull())} cm",
                )
            }
            line("  spostamento previsto A → C sul bordo: mediana ${px(list.mapNotNull { it.predictedShiftPx?.let { s -> abs(s) } }.medianOrNull())} px · λ mediano ${list.mapNotNull { it.lambda }.medianOrNull()?.let { f(it, 2) } ?: "—"}")
            line()
        }
        line("-- Raw una per una (px; λ: 0 = A, 1 = C; residui in cm)")
        line("seq  età ms  A→C    bordo px  col. oss.  |off| A  |off| B  |off| C  |off| D  shift A→C    λ    vicino A  vicino C  lontano A  lontano C  movimento cm")
        for (c in res.cases.sortedByDescending { abs(it.predictedShiftPx ?: 0.0) }) line(
            c.depthSeq.toString().padEnd(5) + f(c.ageMs, 0).padStart(6) + "${c.aSeq}→${c.cSeq}".padStart(9) + c.edgePixels.toString().padStart(9) + px(c.observedU).padStart(10) +
                RawAssociation.entries.joinToString("") { px(c.absOffsetPx[it]).padStart(9) } + px(c.predictedShiftPx).padStart(11) + (c.lambda?.let { f(it, 2) } ?: "—").padStart(7) +
                cm(c.nearResidualM[RawAssociation.A]).padStart(10) + cm(c.nearResidualM[RawAssociation.C]).padStart(10) + cm(c.farResidualM[RawAssociation.A]).padStart(11) +
                cm(c.farResidualM[RawAssociation.C]).padStart(11) + f(c.motionM * 100, 1).padStart(14),
        )
        line()
        line("-- Conclusione (bordi)")
        for (e in res.evidence) line(e)
        line("ESITO: ${res.verdict}")
        line("Confidenza: ${res.confidence}")
    }

    private fun List<Double>.medianOrNull(): Double? = if (isEmpty()) null else Geo.median(toDoubleArray())
}

/** Ricerca del punto più vicino in una nuvola, con una griglia di celle. */
internal class PointGrid(private val points: List<DoubleArray>, private val cell: Double) {
    private val map = HashMap<Long, MutableList<Int>>()

    init {
        for ((i, p) in points.withIndex()) map.getOrPut(key(c(p[0]), c(p[1]), c(p[2]))) { mutableListOf() }.add(i)
    }

    private fun c(v: Double) = floor(v / cell).toInt()
    private fun key(x: Int, y: Int, z: Int) = VoxelIndex.key(x, y, z)

    /** Il punto più vicino entro [max] metri e la sua distanza; null se nessuno. */
    fun nearest(q: DoubleArray, max: Double): Pair<DoubleArray, Double>? {
        val r = kotlin.math.ceil(max / cell).toInt()
        val cx = c(q[0]); val cy = c(q[1]); val cz = c(q[2])
        var best: DoubleArray? = null; var bd = max
        for (dx in -r..r) for (dy in -r..r) for (dz in -r..r) {
            val list = map[key(cx + dx, cy + dy, cz + dz)] ?: continue
            for (i in list) {
                val p = points[i]
                val d = sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]))
                if (d <= bd) { bd = d; best = p }
            }
        }
        return best?.let { it to bd }
    }
}
