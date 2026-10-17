package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.ArCameraProjection
import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.ScanRecording
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Diagnosi di R1/R2: servono a capire, non a promuovere o bocciare da sole. I punti ARCore e i pixel della depth NON sono la stessa
 * osservazione fisica (i punti sono angoli e tessiture tracciati, la depth è stimata dal movimento): il loro confronto dice se
 * convenzioni, intrinseche e pose sono coerenti, non quanto è precisa la mappa.
 */

/** Scarto depth misurata − profondità del punto ARCore proiettato, su tutti i frame (metri). */
data class ResidualStats(val label: String, val frames: Int, val samples: Int, val medianAbsM: Double, val p90AbsM: Double, val within5cm: Double, val medianSignedM: Double)

data class FloorCheck(
    val surfaceId: Int?, val tiltDeg: Double?, val rmsM: Double?, val floorY: Double?, val arcoreFloorY: Double?,
    val earlyY: Double?, val lateY: Double?,
) {
    val deltaVsArcore: Double? get() = if (floorY != null && arcoreFloorY != null) floorY - arcoreFloorY else null
    val drift: Double? get() = if (earlyY != null && lateY != null) lateY - earlyY else null
}

/** Dispersione tra gruppi di vista: scarto medio dal piano di ogni gruppo con abbastanza punti. */
data class ViewSpread(val surfaceId: Int, val groups: Int, val stdM: Double, val rangeM: Double)

data class StabilityRow(val surfaceId: Int, val kind: SurfaceKind, val otherId: Int?, val otherKind: SurfaceKind?, val angleDeg: Double?, val offsetM: Double?)

object ReconDiagnostics {
    /** I punti ARCore (posizione più recente di ogni id) con confidenza ≥ [minConfidence]. */
    private fun arcorePoints(r: ScanRecording, minConfidence: Double): List<DoubleArray> {
        val last = HashMap<Int, DoubleArray>()
        for (f in r.frames) {
            val pc = f.points ?: continue
            for (i in pc.ids.indices) {
                if (pc.confidence[i] < minConfidence) continue
                last[pc.ids[i]] = doubleArrayOf(pc.xyz[3 * i], pc.xyz[3 * i + 1], pc.xyz[3 * i + 2])
            }
        }
        return last.keys.sorted().map { last.getValue(it) }
    }

    /**
     * Test 1 (diagnostico): i punti ARCore proiettati in ogni depth raw (posa del frame della raw, intrinseche della texture) e
     * confrontati con la depth in quel pixel. Due controlli volutamente sbagliati: intrinseche dell'immagine CPU (il vecchio errore)
     * e posa della depth filtrata invece di quella della raw. Se le convenzioni sono giuste, i controlli devono peggiorare.
     */
    fun arcoreVsDepth(r: ScanRecording, blobs: DatasetBlobs, p: ReconParams = ReconParams()): List<ResidualStats> {
        val pts = arcorePoints(r, 0.3)
        val (frames, _) = DepthFrames.select(r, blobs, p)
        val raw = frames.filter { it.use == DepthUse.RAW }
        val cpu = r.header.camera?.imageIntrinsics
        val bySeq = r.depth.associateBy { it.seq }
        val out = mutableListOf(residuals("corretto: raw + posa del suo frame + intrinseche texture", raw, pts) { f -> f.pose to f.k })
        if (cpu != null) out.add(residuals("controllo: intrinseche dell'immagine CPU", raw, pts) { f -> f.pose to cpu.scaledTo(f.k.width, f.k.height) })
        out.add(residuals("controllo: posa della depth filtrata", raw, pts) { f -> (bySeq[f.depthSeq]?.camera ?: f.pose) to f.k })
        // Quale posa descrive meglio la raw? Per le raw "vecchie" (timestamp della raw molto prima della depth filtrata) le due pose
        // differiscono davvero: se vince la posa della depth filtrata, il timestamp della raw non è l'istante della sua immagine.
        val old = raw.filter { f -> bySeq[f.depthSeq]?.let { it.timestampNs - f.timestampNs > 150_000_000L } == true }
        if (old.isNotEmpty()) {
            out.add(residuals("raw più vecchie di 150 ms: posa del timestamp della raw", old, pts) { f -> f.pose to f.k })
            out.add(residuals("raw più vecchie di 150 ms: posa della depth filtrata", old, pts) { f -> (bySeq[f.depthSeq]?.camera ?: f.pose) to f.k })
        }
        return out
    }

    private fun residuals(label: String, frames: List<DepthFrame>, pts: List<DoubleArray>, model: (DepthFrame) -> Pair<RecPose, CameraIntrinsics>): ResidualStats {
        val all = mutableListOf<Double>()
        var used = 0
        for (f in frames) {
            val (pose, k) = model(f)
            var any = false
            for (q in pts) {
                val hit = ArCameraProjection.project(pose, k, q[0], q[1], q[2]) ?: continue
                if (hit.depthM < 0.3 || hit.depthM > 4.0) continue
                val u = hit.u.roundToInt(); val v = hit.v.roundToInt()
                if (u < 0 || v < 0 || u >= f.k.width || v >= f.k.height) continue
                val mm = f.mm[v * f.k.width + u]
                if (mm <= 0) continue
                all.add(mm / 1000.0 - hit.depthM)
                any = true
            }
            if (any) used++
        }
        if (all.isEmpty()) return ResidualStats(label, used, 0, 0.0, 0.0, 0.0, 0.0)
        val abs = all.map { abs(it) }.sorted().toDoubleArray()
        return ResidualStats(label, used, all.size, Geo.percentile(abs, 0.5), Geo.percentile(abs, 0.9), abs.count { it <= 0.05 }.toDouble() / abs.size, Geo.median(all.toDoubleArray()))
    }

    /** Pavimento: inclinazione, RMS, quota contro `floorY` di ARCore, quota stimata dal primo e dall'ultimo terzo del tempo (deriva). */
    fun floor(map: GlobalMap, s: SurfaceResult, r: ScanRecording?): FloorCheck {
        val arFloor = r?.frames?.mapNotNull { it.floorY }?.sorted()?.let { if (it.isEmpty()) null else it[it.size / 2] }
        val f = s.surfaces.filter { it.kind == SurfaceKind.FLOOR }.maxByOrNull { it.areaM2 }
            ?: return FloorCheck(null, null, null, s.floor?.y, arFloor, null, null)
        val idx = SurfaceExtractor.pointsOf(map, f.memberVoxels)
        val times = map.frames.map { it.timestampNs }.sorted()
        val t1 = times[times.size / 3]; val t2 = times[2 * times.size / 3]
        fun heightOf(pred: (Long) -> Boolean): Double? {
            val m = Moments(map.origin[0], map.origin[1], map.origin[2])
            for (i in idx) if (pred(map.frames[map.points.frame[i]].timestampNs)) m.add(map.points.x[i].toDouble(), map.points.y[i].toDouble(), map.points.z[i].toDouble(), map.points.weight[i].toDouble())
            val pl = m.plane() ?: return null
            if (m.n < 200) return null
            // Quota del piano al centroide del pavimento intero.
            val c = f.plane.centroid
            return if (abs(pl.ny) < 1e-6) null else (pl.d - pl.nx * c[0] - pl.nz * c[2]) / pl.ny
        }
        return FloorCheck(f.id, f.tiltDeg, f.rmsM, f.plane.centroid[1], arFloor, heightOf { it <= t1 }, heightOf { it >= t2 })
    }

    /** Coerenza tra viste delle superfici grandi: scarto medio dal piano di ogni gruppo di vista con almeno 150 punti. */
    fun viewCoherence(map: GlobalMap, s: SurfaceResult, minAreaM2: Double = 0.5): List<ViewSpread> = s.surfaces.filter { it.areaM2 >= minAreaM2 }.mapNotNull { sf ->
        val byGroup = HashMap<Int, MutableList<Double>>()
        for (i in SurfaceExtractor.pointsOf(map, sf.memberVoxels)) {
            val g = map.frames[map.points.frame[i]].viewGroup
            byGroup.getOrPut(g) { mutableListOf() }.add(sf.plane.distance(map.points.x[i].toDouble(), map.points.y[i].toDouble(), map.points.z[i].toDouble()))
        }
        val means = byGroup.keys.sorted().map { byGroup.getValue(it) }.filter { it.size >= 150 }.map { it.average() }
        if (means.size < 2) return@mapNotNull null
        val mean = means.average()
        ViewSpread(sf.id, means.size, sqrt(means.sumOf { (it - mean) * (it - mean) } / (means.size - 1)), means.max() - means.min())
    }

    /** Stabilità: le superfici di una ricostruzione ritrovate in un'altra (stessa orientazione, angolo ≤ 10°, distanza ≤ 15 cm). */
    fun stability(a: SurfaceResult, b: SurfaceResult, minAreaM2: Double = 0.3): List<StabilityRow> =
        a.surfaces.filter { it.areaM2 >= minAreaM2 && it.kind != SurfaceKind.UNKNOWN }.map { s ->
            val match = b.surfaces.filter { it.orientation == s.orientation && it.plane.angleTo(s.plane) <= 10 }
                .map { it to max(abs(s.plane.distance(it.plane.centroid[0], it.plane.centroid[1], it.plane.centroid[2])), abs(it.plane.distance(s.plane.centroid[0], s.plane.centroid[1], s.plane.centroid[2]))) }
                .filter { it.second <= 0.15 }.minByOrNull { it.second }
            StabilityRow(s.id, s.kind, match?.first?.id, match?.first?.kind, match?.first?.plane?.angleTo(s.plane), match?.let { abs(s.plane.distance(it.first.plane.centroid[0], it.first.plane.centroid[1], it.first.plane.centroid[2])) })
        }

    /** Firma deterministica del risultato (stessi dati → stessa firma). */
    fun signature(map: GlobalMap, s: SurfaceResult): String {
        var h = 1125899906842597L
        fun mix(v: Long) { h = 31 * h + v }
        mix(map.points.size.toLong()); mix(map.voxels.size.toLong())
        for (sf in s.surfaces) {
            mix(sf.kind.ordinal.toLong()); mix(sf.samples.toLong()); mix((sf.plane.d * 1e5).roundToLong()); mix((sf.plane.nx * 1e5).roundToLong())
            mix((sf.plane.ny * 1e5).roundToLong()); mix((sf.plane.nz * 1e5).roundToLong()); mix((sf.areaM2 * 1e4).roundToLong())
        }
        return h.toULong().toString(16)
    }

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}
