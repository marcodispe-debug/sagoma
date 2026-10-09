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
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalyzer
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * STUDIO DIAGNOSTICO (non cambia la ricostruzione): quale posa descrive davvero il contenuto della depth raw di ARCore?
 *
 *   A = frame del rawTimestampNs (regola M0.2: primo frame con timestamp ≥ quello della raw)
 *   B = il frame successivo ad A (A+1)
 *   C = frame corrente, quello della depth filtrata dello stesso keyframe
 *   D = posa interpolata (slerp) tra i due frame attorno al rawTimestampNs
 *
 * Riferimento NON circolare: superfici stabili ricostruite (R1/R2) solo dalle raw RECENTI (età < [RawPoseStudyParams.referenceAgeMs],
 * dove A, B, C e D differiscono al più di un frame) con seq pari; si valutano tutte le altre raw. Si misurano anche: rumore intrinseco
 * (spessore rispetto al proprio piano, indipendente dalla posa), spostamento per frame (posa ARCore + errore della depth), scansione
 * del ritardo (pose da A−3 a C+3) e confronto pixel per pixel con la depth filtrata (contenuto contro timestamp dichiarato).
 */

enum class RawAssociation(val label: String) {
    A("A · frame del rawTimestampNs (M0.2)"),
    B("B · frame successivo ad A"),
    C("C · frame corrente (depth filtrata)"),
    D("D · interpolata al rawTimestampNs"),
}

data class RawPoseStudyParams(
    /** Raw di riferimento: età (depth filtrata − raw) sotto questa soglia, seq pari. */
    val referenceAgeMs: Double = 40.0,
    /** Soglia tra raw "recenti" e "vecchie" nel report. */
    val oldAgeMs: Double = 150.0,
    /** Un pixel ogni [stride] per lato (velocità). */
    val stride: Int = 2,
    /** Distanza massima dalla superficie per contare un residuo (oltre: è un'altra cosa). */
    val maxResidualM: Double = 0.30,
    /** Margine attorno all'estensione della superficie. */
    val extentMarginM: Double = 0.15,
)

data class ResStat(val n: Int, val medianM: Double, val p90M: Double, val rmsM: Double, val coverage: Double) {
    companion object {
        fun of(values: DoubleArray, total: Int): ResStat {
            if (values.isEmpty()) return ResStat(0, 0.0, 0.0, 0.0, 0.0)
            val s = values.sortedArray()
            return ResStat(s.size, Geo.percentile(s, 0.5), Geo.percentile(s, 0.9), sqrt(s.sumOf { it * it } / s.size), if (total == 0) 0.0 else s.size.toDouble() / total)
        }
    }
}

/** Una raw valutata: età, frame A e C, gruppo di vista, residuo mediano per associazione, scansione del ritardo, confronto di contenuto. */
data class RawCase(
    val depthSeq: Int,
    val ageMs: Double,
    val aSeq: Int,
    val cSeq: Int,
    val viewGroup: Int,
    val medians: Map<RawAssociation, Double?>,
    /** Ritardo k (frame dopo A) con il residuo mediano minimo, e (C − A). */
    val bestLag: Int?,
    val span: Int,
    /** Confronto con la depth filtrata (mm): così com'è, e riproiettata dalla posa A alla posa C; spostamento previsto (px). */
    val identityMm: Double?,
    val warpedMm: Double?,
    val predictedShiftPx: Double?,
    /** Prova 4: residuo mediano (m) della depth FILTRATA dello stesso keyframe con la posa C e con la posa A. */
    val filteredWithC: Double? = null,
    val filteredWithA: Double? = null,
)

class RawPoseStudyResult(
    val referenceRaws: Int,
    val referenceSurfaces: Int,
    val referenceArCore: Int,
    val evaluated: Int,
    val cases: List<RawCase>,
    /** (associazione, sottoinsieme) → statistica contro le superfici di riferimento. */
    val surface: Map<Pair<RawAssociation, String>, ResStat>,
    val arcore: Map<Pair<RawAssociation, String>, ResStat>,
    /** (associazione, fascia di distanza) → statistica (raw vecchie). */
    val byRange: Map<Pair<RawAssociation, String>, ResStat>,
    val intrinsicNoiseM: Double?,
    val frameOffsetRecentM: Map<RawAssociation, Double?>,
    val frameOffsetOldM: Map<RawAssociation, Double?>,
    val verdict: String,
    val confidence: String,
    val evidence: List<String>,
)

object RawPoseStudy {
    private class Case(
        val depthSeq: Int, val rawTs: Long, val ageNs: Long, val aSeq: Int, val cSeq: Int,
        val mm: IntArray, val conf: ByteArray?, val k: CameraIntrinsics, val filtered: IntArray?, val fk: CameraIntrinsics,
    )

    private class Ref(val plane: PlaneFit, val u: DoubleArray, val v: DoubleArray, val uMin: Double, val uMax: Double, val vMin: Double, val vMax: Double)
    private class Line(val ax: Double, val az: Double, val ux: Double, val uz: Double, val len: Double, val y0: Double, val y1: Double)

    fun run(r: ScanRecording, blobs: DatasetBlobs, rp: ReconParams = ReconParams(), p: RawPoseStudyParams = RawPoseStudyParams()): RawPoseStudyResult {
        val index = CaptureIndex(r)
        val poses = r.poses.associateBy { it.seq }
        val texture = r.header.camera?.textureIntrinsics
        // 1. Le raw utilizzabili (non ripetute, con posa A e con il frame C).
        val cases = mutableListOf<Case>()
        for (d in r.depth.sortedBy { it.seq }) {
            if (d.rawStatus == StreamStatus.DUPLICATE || d.rawPath == null) continue
            val raw = blobs.read(d.rawPath) ?: continue
            val a = index.rawPoseFor(d) ?: continue
            if (a.kind == PoseMatch.UNAVAILABLE || a.pose == null) continue
            val c = d.poseFrameSeq ?: index.poseFor(d)?.item?.seq ?: continue
            val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
            if (raw.size != w * h * 2) continue
            val fk = d.intrinsics?.takeIf { d.intrinsicsSource == IntrinsicsSource.TEXTURE_SCALED } ?: texture?.scaledTo(d.width, d.height) ?: continue
            val k = if (w == fk.width && h == fk.height) fk else fk.scaledTo(w, h)
            val filtered = blobs.read(d.path)?.takeIf { it.size == d.width * d.height * 2 }?.let { DepthRaw.decode(it, d.width, d.height) }
            val conf = d.confidencePath?.let { blobs.read(it) }?.takeIf { it.size == w * h }
            cases.add(Case(d.seq, d.rawTimestampNs ?: continue, d.timestampNs - (d.rawTimestampNs ?: d.timestampNs), a.pose.seq, c, DepthRaw.decode(raw, w, h), conf, k, filtered, fk))
        }
        // Gruppi di vista: finestre di 0,5 s sul timestamp della depth filtrata.
        val t0 = cases.minOfOrNull { it.rawTs + it.ageNs } ?: 0L
        fun group(c: Case) = ((c.rawTs + c.ageNs - t0) / 500_000_000L).toInt()

        // 2. Riferimento: superfici stabili dalle sole raw recenti con seq pari (A ≈ C), via R1/R2.
        val refSeqs = cases.filter { it.ageNs < p.referenceAgeMs * 1e6 && it.depthSeq % 2 == 0 }.map { it.depthSeq }.toSet()
        val refRecording = r.copy(depth = r.depth.filter { it.seq in refSeqs })
        val refMap = GlobalMap.build(refRecording, blobs, rp)
        val refSurf = SurfaceExtractor.extract(refMap, r)
        val refs = refSurf.surfaces.filter { it.kind != SurfaceKind.UNKNOWN && it.areaM2 >= 0.4 && it.effectiveFrames >= 4 }
            .map { Ref(it.plane, it.u, it.v, it.uMin, it.uMax, it.vMin, it.vMax) }
        val tracks = RecordingAnalyzer.analyze(r).verticalPlanes.filter { t -> t.last != null && refSurf.arcore.any { it.key == t.key && it.surface != null } }
        val lines = tracks.map { t ->
            val g = t.last!!
            val dx = g.b.x - g.a.x; val dz = g.b.z - g.a.z; val len = sqrt(dx * dx + dz * dz)
            Line(g.a.x, g.a.z, dx / len, dz / len, len, g.minY, g.maxY)
        }

        // 3. Valutazione di ogni raw non di riferimento, per ogni associazione.
        val evalCases = cases.filter { it.depthSeq !in refSeqs }
        val surf = HashMap<Pair<RawAssociation, String>, MutableList<Double>>()
        val surfTotal = HashMap<Pair<RawAssociation, String>, Int>()
        val arc = HashMap<Pair<RawAssociation, String>, MutableList<Double>>()
        val arcTotal = HashMap<Pair<RawAssociation, String>, Int>()
        val rng = HashMap<Pair<RawAssociation, String>, MutableList<Double>>()
        val rngTotal = HashMap<Pair<RawAssociation, String>, Int>()
        val ownRms = mutableListOf<Double>()
        val offsRecent = HashMap<RawAssociation, MutableList<Double>>()
        val offsOld = HashMap<RawAssociation, MutableList<Double>>()
        val out = mutableListOf<RawCase>()
        for (c in evalCases) {
            val old = c.ageNs > p.oldAgeMs * 1e6
            val subset = if (old) "vecchie (> ${p.oldAgeMs.roundToInt()} ms)" else "recenti (< ${p.oldAgeMs.roundToInt()} ms)"
            val medians = HashMap<RawAssociation, Double?>()
            for (assoc in RawAssociation.entries) {
                val pose = poseOf(assoc, c, poses) ?: run { medians[assoc] = null; null } ?: continue
                val res = mutableListOf<Double>(); var total = 0
                val perSurface = HashMap<Int, Moments>()
                points(c, pose, rp, p.stride) { x, y, z, range ->
                    total++
                    val (d, which) = nearestSurface(refs, x, y, z, p)
                    if (d != null) {
                        res.add(d)
                        if (assoc == RawAssociation.A || assoc == RawAssociation.C) perSurface.getOrPut(which) { Moments() }.add(x, y, z, 1.0)
                        val bin = rangeBin(range)
                        if (old) { rng.getOrPut(assoc to bin) { mutableListOf() }.add(d) }
                    }
                    if (old) { val bin = rangeBin(range); rngTotal[assoc to bin] = (rngTotal[assoc to bin] ?: 0) + 1 }
                    lineResidual(lines, x, y, z, p)?.let { arc.getOrPut(assoc to subset) { mutableListOf() }.add(it); arc.getOrPut(assoc to "tutte") { mutableListOf() }.add(it) }
                }
                for (key in listOf(assoc to subset, assoc to "tutte")) {
                    surf.getOrPut(key) { mutableListOf() }.addAll(res)
                    surfTotal[key] = (surfTotal[key] ?: 0) + total
                    arcTotal[key] = (arcTotal[key] ?: 0) + total
                }
                medians[assoc] = if (res.size >= 200) Geo.median(res.toDoubleArray()) else null
                // Rumore intrinseco (spessore rispetto al PROPRIO piano: non dipende dalla posa) e spostamento del frame dal riferimento.
                if (assoc == RawAssociation.A || assoc == RawAssociation.C) for ((si, m) in perSurface) {
                    if (m.n < 300) continue
                    val own = m.plane() ?: continue
                    if (assoc == RawAssociation.A) ownRms.add(own.rms)
                    val ref = refs[si].plane
                    val off = abs(ref.distance(own.centroid[0], own.centroid[1], own.centroid[2]))
                    (if (old) offsOld else offsRecent).getOrPut(assoc) { mutableListOf() }.add(off)
                }
            }
            // Scansione del ritardo: pose dei frame da A−3 a C+3.
            var bestLag: Int? = null; var best = Double.MAX_VALUE
            if (old) for (kLag in -3..(c.cSeq - c.aSeq + 3)) {
                val pose = poses[c.aSeq + kLag]?.camera ?: continue
                val res = mutableListOf<Double>()
                points(c, pose, rp, p.stride * 2) { x, y, z, _ -> nearestSurface(refs, x, y, z, p).first?.let { res.add(it) } }
                if (res.size < 100) continue
                val m = Geo.median(res.toDoubleArray())
                if (m < best) { best = m; bestLag = kLag }
            }
            val content = contentTest(c, poses)
            // Prova 4: la depth FILTRATA dello stesso keyframe contro il riferimento, con la posa C (il suo frame) e con la posa A.
            // Serve a sapere a quale istante appartiene la geometria che raw e filtrata condividono (la prova 3 da sola non lo dice).
            var filteredC: Double? = null; var filteredA: Double? = null
            if (old && c.filtered != null) {
                for ((assoc, pose) in listOf(RawAssociation.C to poses[c.cSeq]?.camera, RawAssociation.A to poses[c.aSeq]?.camera)) {
                    if (pose == null) continue
                    val res = mutableListOf<Double>()
                    val f = DepthFrame(c.depthSeq, DepthUse.FILTERED_FALLBACK, 0, pose, c.fk, c.filtered, null, null, null)
                    DepthToWorld.convert(f, rp) { pt -> if (pt.u % p.stride == 0 && pt.v % p.stride == 0) nearestSurface(refs, pt.x, pt.y, pt.z, p).first?.let { res.add(it) } }
                    val m = if (res.size >= 200) Geo.median(res.toDoubleArray()) else null
                    if (assoc == RawAssociation.C) filteredC = m else filteredA = m
                }
            }
            out.add(RawCase(c.depthSeq, c.ageNs / 1e6, c.aSeq, c.cSeq, group(c), medians, bestLag, c.cSeq - c.aSeq, content?.first, content?.second, content?.third, filteredC, filteredA))
        }
        val surfaceStats = surf.mapValues { (k, v) -> ResStat.of(v.toDoubleArray(), surfTotal[k] ?: 0) }
        val arcoreStats = arc.mapValues { (k, v) -> ResStat.of(v.toDoubleArray(), arcTotal[k] ?: 0) }
        val rangeStats = rng.mapValues { (k, v) -> ResStat.of(v.toDoubleArray(), rngTotal[k] ?: 0) }
        val (verdict, confidence, evidence) = verdict(out)
        return RawPoseStudyResult(
            refSeqs.size, refs.size, lines.size, evalCases.size, out, surfaceStats, arcoreStats, rangeStats,
            if (ownRms.isEmpty()) null else Geo.median(ownRms.toDoubleArray()),
            RawAssociation.entries.associateWith { a -> offsRecent[a]?.let { Geo.median(it.toDoubleArray()) } },
            RawAssociation.entries.associateWith { a -> offsOld[a]?.let { Geo.median(it.toDoubleArray()) } },
            verdict, confidence, evidence,
        )
    }

    private fun poseOf(a: RawAssociation, c: Case, poses: Map<Int, PoseSample>): RecPose? = when (a) {
        RawAssociation.A -> poses[c.aSeq]?.camera
        RawAssociation.B -> poses[c.aSeq + 1]?.camera
        RawAssociation.C -> poses[c.cSeq]?.camera
        RawAssociation.D -> {
            val before = poses[c.aSeq - 1]; val after = poses[c.aSeq]
            if (before?.camera == null || after?.camera == null || after.timestampNs <= before.timestampNs) null
            else interpolate(before.camera, after.camera, ((c.rawTs - before.timestampNs).toDouble() / (after.timestampNs - before.timestampNs)).coerceIn(0.0, 1.0))
        }
    }

    /** Posa interpolata: traslazione lineare, rotazione slerp. */
    fun interpolate(a: RecPose, b: RecPose, t: Double): RecPose {
        var bx = b.qx; var by = b.qy; var bz = b.qz; var bw = b.qw
        var dot = a.qx * bx + a.qy * by + a.qz * bz + a.qw * bw
        if (dot < 0) { bx = -bx; by = -by; bz = -bz; bw = -bw; dot = -dot }
        val (wa, wb) = if (dot > 0.9995) (1 - t) to t else {
            val th = acos(dot.coerceIn(-1.0, 1.0)); val s = sin(th)
            sin((1 - t) * th) / s to sin(t * th) / s
        }
        var qx = wa * a.qx + wb * bx; var qy = wa * a.qy + wb * by; var qz = wa * a.qz + wb * bz; var qw = wa * a.qw + wb * bw
        val n = sqrt(qx * qx + qy * qy + qz * qz + qw * qw); qx /= n; qy /= n; qz /= n; qw /= n
        return RecPose(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, qx, qy, qz, qw)
    }

    private fun points(c: Case, pose: RecPose, rp: ReconParams, stride: Int, sink: (Double, Double, Double, Double) -> Unit) {
        val f = DepthFrame(c.depthSeq, DepthUse.RAW, c.rawTs, pose, c.k, c.mm, c.conf, null, null)
        DepthToWorld.convert(f, rp) { pt -> if (pt.u % stride == 0 && pt.v % stride == 0) sink(pt.x, pt.y, pt.z, pt.rangeM) }
    }

    private fun nearestSurface(refs: List<Ref>, x: Double, y: Double, z: Double, p: RawPoseStudyParams): Pair<Double?, Int> {
        var best: Double? = null; var which = -1
        for ((i, s) in refs.withIndex()) {
            val d = abs(s.plane.distance(x, y, z))
            if (d > p.maxResidualM || (best != null && d >= best)) continue
            val c = s.plane.centroid
            val dx = x - c[0]; val dy = y - c[1]; val dz = z - c[2]
            val u = dx * s.u[0] + dy * s.u[1] + dz * s.u[2]; val v = dx * s.v[0] + dy * s.v[1] + dz * s.v[2]
            if (u < s.uMin - p.extentMarginM || u > s.uMax + p.extentMarginM || v < s.vMin - p.extentMarginM || v > s.vMax + p.extentMarginM) continue
            best = d; which = i
        }
        return best to which
    }

    private fun lineResidual(lines: List<Line>, x: Double, y: Double, z: Double, p: RawPoseStudyParams): Double? {
        var best: Double? = null
        for (l in lines) {
            if (y < l.y0 - 0.1 || y > l.y1 + 0.1) continue
            val px = x - l.ax; val pz = z - l.az
            val along = px * l.ux + pz * l.uz
            if (along < -0.1 || along > l.len + 0.1) continue
            val d = abs(px * -l.uz + pz * l.ux)
            if (d <= p.maxResidualM && (best == null || d < best)) best = d
        }
        return best
    }

    private fun rangeBin(r: Double) = when {
        r < 1.0 -> "0,3–1 m"
        r < 1.5 -> "1–1,5 m"
        r < 2.0 -> "1,5–2 m"
        r < 3.0 -> "2–3 m"
        else -> "3–4 m"
    }

    /**
     * Contenuto contro timestamp: la raw confrontata pixel per pixel con la depth filtrata dello stesso keyframe (frame C). Così
     * com'è (stessi pixel) e riproiettata dalla posa A alla posa C. Se la raw è davvero dell'istante A, la riproiezione deve
     * migliorare l'accordo; se è già allineata a C, deve peggiorarlo. Con uno spostamento previsto sotto ~1 px il test non distingue.
     */
    private fun contentTest(c: Case, poses: Map<Int, PoseSample>): Triple<Double, Double, Double>? {
        val filtered = c.filtered ?: return null
        val pa = poses[c.aSeq]?.camera ?: return null
        val pc = poses[c.cSeq]?.camera ?: return null
        val w = c.k.width; val h = c.k.height
        if (w != c.fk.width || h != c.fk.height) return null
        val ident = mutableListOf<Double>(); val warped = mutableListOf<Double>(); val shift = mutableListOf<Double>()
        for (v in 0 until h step 2) for (u in 0 until w step 2) {
            val mm = c.mm[v * w + u]
            if (mm <= 0) continue
            val f0 = filtered[v * w + u]
            if (f0 > 0) ident.add(abs(mm - f0).toDouble())
            val world = ArCameraProjection.unproject(pa, c.k, u.toDouble(), v.toDouble(), mm / 1000.0)
            val hit = ArCameraProjection.project(pc, c.fk, world[0], world[1], world[2]) ?: continue
            val iu = hit.u.roundToInt(); val iv = hit.v.roundToInt()
            shift.add(sqrt((hit.u - u) * (hit.u - u) + (hit.v - v) * (hit.v - v)))
            if (iu !in 0 until w || iv !in 0 until h) continue
            val f1 = filtered[iv * w + iu]
            if (f1 > 0) warped.add(abs(hit.depthM * 1000 - f1))
        }
        if (ident.size < 100 || warped.size < 100) return null
        return Triple(Geo.median(ident.toDoubleArray()), Geo.median(warped.toDoubleArray()), Geo.median(shift.toDoubleArray()))
    }

    /** Test dei segni bilaterale esatto: probabilità di almeno [wins] successi su [n] con p = 0,5 (×2). */
    fun signTestP(wins: Int, n: Int): Double {
        if (n == 0) return 1.0
        val k = max(wins, n - wins)
        var tail = 0.0
        for (i in k..n) tail += exp(lnChoose(n, i) - n * ln(2.0))
        return min(1.0, 2 * tail)
    }

    private fun lnChoose(n: Int, k: Int): Double { var s = 0.0; for (i in 1..k) s += ln((n - k + i).toDouble() / i); return s }

    /**
     * Tre prove indipendenti sulle raw vecchie: (1) confronto appaiato A contro C frame per frame (test dei segni); (2) ritardo con il
     * residuo minimo, come frazione tra A (0) e C (1); (3) contenuto: la riproiezione A → C migliora o peggiora l'accordo con la depth
     * filtrata (solo dove lo spostamento previsto è almeno 1,5 px). Conclusione solo se le prove concordano.
     */
    private fun verdict(cases: List<RawCase>): Triple<String, String, List<String>> {
        val ev = mutableListOf<String>()
        val old = cases.filter { it.span > 0 && it.ageMs > 150 }
        val paired = old.mapNotNull { c -> val a = c.medians[RawAssociation.A]; val cc = c.medians[RawAssociation.C]; if (a != null && cc != null) a to cc else null }
        val cWins = paired.count { it.second < it.first }
        val pSign = signTestP(cWins, paired.size)
        ev.add("Prova 1 (appaiata, raw vecchie): C meglio di A in $cWins/${paired.size} raw · p (test dei segni) = ${f(pSign, 4)}")
        val lags = old.mapNotNull { c -> c.bestLag?.let { it.toDouble() / c.span } }
        val lagMed = if (lags.isEmpty()) null else Geo.median(lags.toDoubleArray())
        ev.add("Prova 2 (scansione del ritardo): posizione del minimo tra A (0) e C (1), mediana ${lagMed?.let { f(it, 2) } ?: "—"} su ${lags.size} raw")
        val powered = old.filter { (it.predictedShiftPx ?: 0.0) >= 1.5 && it.identityMm != null && it.warpedMm != null }
        val identityBetter = powered.count { it.identityMm!! < it.warpedMm!! }
        ev.add("Prova 3 (contenuto contro depth filtrata, spostamento previsto ≥ 1,5 px): raw già allineata al frame corrente in $identityBetter/${powered.size} raw" +
            if (powered.isNotEmpty()) " · spostamento previsto mediano ${f(Geo.median(powered.map { it.predictedShiftPx!! }.toDoubleArray()), 1)} px" else "")
        val filt = old.filter { it.filteredWithC != null && it.filteredWithA != null }
        val filtCWins = filt.count { it.filteredWithC!! < it.filteredWithA!! }
        val pFilt = signTestP(filtCWins, filt.size)
        ev.add("Prova 4 (depth filtrata degli stessi keyframe contro il riferimento): la posa C (suo frame) è migliore della posa A in $filtCWins/${filt.size} · p = ${f(pFilt, 4)}" +
            " · serve a datare la geometria che raw e filtrata condividono (prova 3)")
        // La prova 3 indica C solo se la geometria condivisa è quella del frame corrente (prova 4); altrimenti non conta.
        val filteredIsCurrent = filt.size >= 8 && filtCWins >= 0.7 * filt.size && pFilt < 0.01
        val filteredIsOld = filt.size >= 8 && filtCWins <= 0.3 * filt.size && pFilt < 0.01
        ev.add("Lettura combinata 3+4: " + when {
            filteredIsCurrent && identityBetter >= 0.7 * powered.size -> "la filtrata è del frame corrente e la raw ha la sua stessa geometria → la raw è del frame corrente"
            filteredIsOld && identityBetter >= 0.7 * powered.size -> "raw e filtrata hanno la stessa geometria, ma quella di un istante precedente → entrambe più vecchie del loro timestamp"
            else -> "non conclusiva (la prova 4 non data la geometria in modo significativo)"
        })
        val forC = listOf(
            paired.size >= 8 && cWins >= 0.7 * paired.size && pSign < 0.01,
            lagMed != null && lags.size >= 8 && lagMed >= 0.6,
            powered.size >= 5 && identityBetter >= 0.7 * powered.size && filteredIsCurrent,
        )
        val forA = listOf(
            paired.size >= 8 && cWins <= 0.3 * paired.size && pSign < 0.01,
            lagMed != null && lags.size >= 8 && lagMed <= 0.4,
            powered.size >= 5 && identityBetter <= 0.3 * powered.size,
        )
        val c = forC.count { it }; val a = forA.count { it }
        return when {
            c == 3 -> Triple("I dati supportano C: il contenuto della raw è allineato al frame CORRENTE, non al suo rawTimestampNs.", "alta (3 prove indipendenti su 3)", ev)
            a == 3 -> Triple("I dati supportano A: la regola M0.2 (posa del rawTimestampNs) è corretta.", "alta (3 prove indipendenti su 3)", ev)
            c == 2 && a == 0 -> Triple("I dati indicano C, ma non tutte le prove concordano.", "media (2 prove su 3, nessuna contraria)", ev)
            a == 2 && c == 0 -> Triple("I dati indicano A, ma non tutte le prove concordano.", "media (2 prove su 3, nessuna contraria)", ev)
            else -> Triple("NON CONCLUSIVO: i dati non distinguono in modo affidabile tra le associazioni.", "bassa", ev)
        }
    }

    private fun f(v: Double, d: Int) = formatDecimal(v, d, '.')

    /**
     * Controllo QUALITATIVO con l'RGB: i punti della raw [depthSeq], messi nel mondo con la posa dell'associazione [assoc], proiettati
     * sull'immagine RGB più vicina al frame corrente (posa e intrinseche dell'immagine CPU). RGB e depth non osservano gli stessi pixel
     * fisici (risoluzione, campo visivo e istante diversi): serve solo a guardare se i bordi coincidono, non è una misura.
     */
    fun rgbOverlay(r: ScanRecording, blobs: DatasetBlobs, depthSeq: Int, assoc: RawAssociation, rp: ReconParams = ReconParams()): String? {
        val d = r.depth.firstOrNull { it.seq == depthSeq } ?: return null
        val index = CaptureIndex(r)
        val poses = r.poses.associateBy { it.seq }
        val raw = d.rawPath?.let { blobs.read(it) } ?: return null
        val aSeq = index.rawPoseFor(d)?.pose?.seq ?: return null
        val cSeq = d.poseFrameSeq ?: return null
        val pose = when (assoc) {
            RawAssociation.A -> poses[aSeq]?.camera
            RawAssociation.B -> poses[aSeq + 1]?.camera
            RawAssociation.C -> poses[cSeq]?.camera
            RawAssociation.D -> poses[aSeq - 1]?.camera?.let { b -> poses[aSeq]?.camera?.let { a2 -> interpolate(b, a2, 0.9) } }
        } ?: return null
        val rgb = r.rgb.minByOrNull { abs(it.frameSeq - cSeq) } ?: return null
        val jpeg = blobs.read(rgb.path) ?: return null
        val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
        val k = (d.intrinsics ?: return null).let { if (it.width == w && it.height == h) it else it.scaledTo(w, h) }
        val mm = DepthRaw.decode(raw, w, h)
        val sb = StringBuilder()
        val rot = (((rgb.sensorOrientationDeg ?: 0) - rgb.displayRotation * 90) % 360 + 360) % 360
        val (ow, oh) = if (rot == 90 || rot == 270) rgb.height to rgb.width else rgb.width to rgb.height
        val transform = when (rot) { 90 -> "translate(${rgb.height},0) rotate(90)"; 180 -> "translate(${rgb.width},${rgb.height}) rotate(180)"; 270 -> "translate(0,${rgb.width}) rotate(270)"; else -> "" }
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$ow" height="${oh + 50}" font-family="sans-serif" font-size="11"><rect width="100%" height="100%" fill="#FFF"/><g transform="$transform">""")
        sb.append("""<image href="data:image/jpeg;base64,${kotlin.io.encoding.Base64.encode(jpeg)}" x="0" y="0" width="${rgb.width}" height="${rgb.height}"/>""")
        val f = DepthFrame(depthSeq, DepthUse.RAW, 0, pose, k, mm, null, null, null)
        DepthToWorld.convert(f, rp.copy(edgeJumpRatio = 10.0)) { pt ->
            if (pt.u % 2 != 0 || pt.v % 2 != 0) return@convert
            val hit = ArCameraProjection.project(rgb.camera, rgb.intrinsics, pt.x, pt.y, pt.z) ?: return@convert
            if (hit.u < 0 || hit.v < 0 || hit.u >= rgb.width || hit.v >= rgb.height) return@convert
            // Colore per distanza: vicino = rosso, lontano = blu.
            val t = ((hit.depthM - 0.5) / 3.0).coerceIn(0.0, 1.0)
            sb.append("""<circle cx="${f(hit.u, 1)}" cy="${f(hit.v, 1)}" r="1.4" fill="rgb(${(255 * (1 - t)).roundToInt()},60,${(255 * t).roundToInt()})" fill-opacity="0.7"/>""")
        }
        sb.append("</g>")
        sb.append("""<text x="4" y="${oh + 16}">Raw #$depthSeq (età ${f((d.timestampNs - (d.rawTimestampNs ?: d.timestampNs)) / 1e6, 0)} ms, frame A $aSeq, frame C $cSeq) posta con ${assoc.label}</text>""")
        sb.append("""<text x="4" y="${oh + 32}">sull'RGB #${rgb.seq} (frame ${rgb.frameSeq}) · solo controllo visivo: RGB e depth non sono gli stessi pixel fisici · rosso vicino, blu lontano</text>""")
        sb.append("</svg>")
        return sb.toString()
    }

    fun report(res: RawPoseStudyResult, title: String): String = buildString {
        fun line(s: String = "") = append(s).append('\n')
        fun cm(v: Double?) = v?.let { f(it * 100, 1) } ?: "—"
        line("=== Studio della posa della depth raw — $title ===")
        line("Riferimento: superfici stabili da ${res.referenceRaws} raw recenti (< 40 ms, seq pari): ${res.referenceSurfaces} superfici, ${res.referenceArCore} piani ARCore compatibili · raw valutate: ${res.evaluated}")
        line()
        line("-- Residuo contro le superfici di riferimento (cm)")
        line("associazione                               sottoinsieme              n        mediana   p90     RMS     copertura")
        for (a in RawAssociation.entries) for (sub in res.surface.keys.filter { it.first == a }.map { it.second }.sorted()) {
            val s = res.surface.getValue(a to sub)
            line(a.label.padEnd(43) + sub.padEnd(26) + s.n.toString().padStart(8) + cm(s.medianM).padStart(9) + cm(s.p90M).padStart(8) + cm(s.rmsM).padStart(8) + (f(s.coverage * 100, 0) + "%").padStart(11))
        }
        line()
        line("-- Residuo contro i piani verticali ARCore compatibili (cm; i piani ARCore NON sono verità)")
        for (a in RawAssociation.entries) for (sub in res.arcore.keys.filter { it.first == a }.map { it.second }.sorted()) {
            val s = res.arcore.getValue(a to sub)
            line(a.label.padEnd(43) + sub.padEnd(26) + s.n.toString().padStart(8) + cm(s.medianM).padStart(9) + cm(s.p90M).padStart(8) + cm(s.rmsM).padStart(8))
        }
        line()
        line("-- Raw vecchie: residuo per distanza dalla camera (cm, mediana / p90)")
        val bins = res.byRange.keys.map { it.second }.distinct().sorted()
        line("fascia     " + RawAssociation.entries.joinToString("") { it.name.padStart(16) })
        for (b in bins) line(b.padEnd(11) + RawAssociation.entries.joinToString("") { a -> res.byRange[a to b]?.let { "${cm(it.medianM)} / ${cm(it.p90M)}" }?.padStart(16) ?: "—".padStart(16) })
        line()
        line("-- Scomposizione dell'errore")
        line("Rumore intrinseco della raw (spessore rispetto al proprio piano, stesso frame, indipendente dalla posa): ${cm(res.intrinsicNoiseM)} cm (mediana)")
        line("Spostamento di ogni frame dal riferimento (posa ARCore + errore della depth), raw recenti: A ${cm(res.frameOffsetRecentM[RawAssociation.A])} · C ${cm(res.frameOffsetRecentM[RawAssociation.C])} cm")
        line("Spostamento di ogni frame dal riferimento, raw vecchie: A ${cm(res.frameOffsetOldM[RawAssociation.A])} · C ${cm(res.frameOffsetOldM[RawAssociation.C])} cm")
        line()
        line("-- Raw vecchie una per una (residuo mediano in cm; ritardo migliore in frame dopo A; contenuto in mm)")
        line("seq   età ms  A→C frame  gruppo     A       B       C       D    ritardo migliore   raw=filtrata  riproiettata  spost. px   filtrata C   filtrata A")
        for (c in res.cases.filter { it.ageMs > 150 }.sortedBy { it.depthSeq }) line(
            c.depthSeq.toString().padEnd(6) + f(c.ageMs, 0).padStart(6) + "${c.aSeq}→${c.cSeq}".padStart(11) + c.viewGroup.toString().padStart(8) +
                RawAssociation.entries.joinToString("") { cm(c.medians[it]).padStart(8) } + (c.bestLag?.let { "$it/${c.span}" } ?: "—").padStart(19) +
                (c.identityMm?.let { f(it, 0) } ?: "—").padStart(14) + (c.warpedMm?.let { f(it, 0) } ?: "—").padStart(14) + (c.predictedShiftPx?.let { f(it, 1) } ?: "—").padStart(11) +
                cm(c.filteredWithC).padStart(13) + cm(c.filteredWithA).padStart(13),
        )
        line()
        line("-- Per gruppo di vista (raw vecchie; residuo mediano in cm)")
        val byGroup = res.cases.filter { it.ageMs > 150 }.groupBy { it.viewGroup }
        for (g in byGroup.keys.sorted()) { val list = byGroup.getValue(g); line(
            "gruppo $g: ${list.size} raw · " + RawAssociation.entries.joinToString(" · ") { a -> "${a.name} ${cm(list.mapNotNull { it.medians[a] }.let { if (it.isEmpty()) null else Geo.median(it.toDoubleArray()) })}" },
        ) }
        line()
        line("-- Conclusione")
        for (e in res.evidence) line(e)
        line("ESITO: ${res.verdict}")
        line("Confidenza: ${res.confidence}")
    }
}
