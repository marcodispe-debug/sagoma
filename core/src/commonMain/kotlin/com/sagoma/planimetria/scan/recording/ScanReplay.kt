package com.sagoma.planimetria.scan.recording

import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.WallObservation
import kotlin.math.abs
import kotlin.math.sqrt

/** Cosa è cambiato tra un frame e il precedente: gli eventi che un algoritmo di scansione "vedrebbe" su un telefono vero. */
sealed interface ReplayEvent {
    data class TrackingChanged(val from: String?, val to: String, val failureReason: String?) : ReplayEvent
    data class PlaneAppeared(val key: Int, val kind: String) : ReplayEvent

    /** Il piano è cambiato: poligono e/o posa diversi dal frame precedente. */
    data class PlaneChanged(val key: Int, val polygonChanged: Boolean, val poseChanged: Boolean) : ReplayEvent
    data class PlaneSubsumed(val key: Int, val by: Int) : ReplayEvent
    data class PlaneDisappeared(val key: Int) : ReplayEvent
    data class PointCloudUpdated(val count: Int) : ReplayEvent
}

data class ReplayPlane(
    val key: Int,
    val kind: String,
    val tracking: String,
    val subsumedBy: Int?,
    val pose: RecPose,
    /** Normale nel mondo (come registrata). */
    val normal: ArPoint?,
    val extentX: Double,
    val extentZ: Double,
    /** Poligono locale del piano: coppie (x, z). */
    val polygonLocal: List<Pair<Double, Double>>,
    /** Gli stessi vertici nel mondo ARCore (posa del piano applicata al poligono locale). */
    val polygonWorld: List<ArPoint>,
) {
    val isVertical: Boolean get() = kind == KIND_VERTICAL
    val isHorizontalUp: Boolean get() = kind == KIND_HORIZONTAL_UP

    companion object {
        const val KIND_VERTICAL = "VERTICAL"
        const val KIND_HORIZONTAL_UP = "HORIZONTAL_UPWARD_FACING"
    }
}

data class ReplayPoint(val id: Int, val x: Double, val y: Double, val z: Double, val confidence: Double)

/** Un frame normalizzato: tutto nel mondo ARCore (metri), indipendente da Android. */
data class ReplayFrame(
    val index: Int,
    val timestampNs: Long,
    val elapsedMs: Long,
    val tracking: String,
    val failureReason: String?,
    val camera: RecPose?,
    val cameraPosition: ArPoint?,
    /** Velocità della camera (m/s) rispetto al frame precedente con posa; null al primo o senza posa. */
    val speedMps: Double?,
    val floorY: Double?,
    val depthInUse: Boolean,
    val planes: List<ReplayPlane>,
    /** Nuvola di punti, solo nei frame in cui è cambiata. */
    val points: List<ReplayPoint>?,
    val events: List<ReplayEvent>,
)

/** Matematica delle pose (quaternione locale→mondo): serve a portare nel mondo i dati locali dei piani. */
fun RecPose.rotate(vx: Double, vy: Double, vz: Double): ArPoint {
    // v' = v + 2w (q × v) + 2 q × (q × v)
    val cx = qy * vz - qz * vy
    val cy = qz * vx - qx * vz
    val cz = qx * vy - qy * vx
    val dx = qy * cz - qz * cy
    val dy = qz * cx - qx * cz
    val dz = qx * cy - qy * cx
    return ArPoint(vx + 2 * (qw * cx + dx), vy + 2 * (qw * cy + dy), vz + 2 * (qw * cz + dz))
}

/** Punto locale (x, y, z) → mondo. */
fun RecPose.transformPoint(lx: Double, ly: Double, lz: Double): ArPoint {
    val r = rotate(lx, ly, lz)
    return ArPoint(r.x + x, r.y + y, r.z + z)
}

/** Dalla registrazione ai frame normalizzati e ai loro eventi: la base per far girare un algoritmo di scansione senza telefono. */
object ScanReplay {
    private const val EPS = 1e-6

    fun frames(recording: ScanRecording): List<ReplayFrame> {
        val out = ArrayList<ReplayFrame>(recording.frames.size)
        var prev: ReplayFrame? = null
        val prevPlanes = HashMap<Int, ReplayPlane>()
        for (f in recording.frames) {
            val planes = f.planes.map(::plane)
            val events = ArrayList<ReplayEvent>()
            if (prev == null || prev.tracking != f.tracking) events.add(ReplayEvent.TrackingChanged(prev?.tracking, f.tracking, f.failureReason))
            val current = planes.associateBy { it.key }
            for (p in planes) {
                val before = prevPlanes[p.key]
                if (before == null) {
                    events.add(ReplayEvent.PlaneAppeared(p.key, p.kind))
                } else {
                    val polygonChanged = before.polygonLocal != p.polygonLocal
                    val poseChanged = poseDiffers(before.pose, p.pose)
                    if (polygonChanged || poseChanged) events.add(ReplayEvent.PlaneChanged(p.key, polygonChanged, poseChanged))
                    if (before.subsumedBy == null && p.subsumedBy != null) events.add(ReplayEvent.PlaneSubsumed(p.key, p.subsumedBy))
                }
            }
            for (k in prevPlanes.keys.sorted()) if (k !in current) events.add(ReplayEvent.PlaneDisappeared(k))
            val points = f.points?.let { pc -> points(pc) }
            if (points != null) events.add(ReplayEvent.PointCloudUpdated(points.size))
            val position = f.camera?.let { ArPoint(it.x, it.y, it.z) }
            val frame = ReplayFrame(
                index = f.index, timestampNs = f.timestampNs, elapsedMs = f.elapsedMs, tracking = f.tracking,
                failureReason = f.failureReason, camera = f.camera, cameraPosition = position,
                speedMps = speed(prev, position, f.timestampNs), floorY = f.floorY, depthInUse = f.depthInUse,
                planes = planes, points = points, events = events,
            )
            out.add(frame)
            prev = frame
            prevPlanes.clear()
            prevPlanes.putAll(current)
        }
        return out
    }

    private fun poseDiffers(a: RecPose, b: RecPose) =
        abs(a.x - b.x) > EPS || abs(a.y - b.y) > EPS || abs(a.z - b.z) > EPS ||
            abs(a.qx - b.qx) > EPS || abs(a.qy - b.qy) > EPS || abs(a.qz - b.qz) > EPS || abs(a.qw - b.qw) > EPS

    private fun speed(prev: ReplayFrame?, position: ArPoint?, tNs: Long): Double? {
        if (prev == null) return null
        val p0 = prev.cameraPosition ?: return null
        val p1 = position ?: return null
        val dt = (tNs - prev.timestampNs) / 1e9
        if (dt <= 0) return null
        val dx = p1.x - p0.x; val dy = p1.y - p0.y; val dz = p1.z - p0.z
        return sqrt(dx * dx + dy * dy + dz * dz) / dt
    }

    private fun plane(p: RecordedPlane): ReplayPlane {
        val local = ArrayList<Pair<Double, Double>>(p.polygon.size / 2)
        for (i in 0 until p.polygon.size / 2) local.add(p.polygon[2 * i] to p.polygon[2 * i + 1])
        return ReplayPlane(
            key = p.key, kind = p.kind, tracking = p.tracking, subsumedBy = p.subsumedBy, pose = p.pose,
            normal = if (p.normal.size == 3) ArPoint(p.normal[0], p.normal[1], p.normal[2]) else null,
            extentX = p.extentX, extentZ = p.extentZ, polygonLocal = local,
            polygonWorld = local.map { (x, z) -> p.pose.transformPoint(x, 0.0, z) },
        )
    }

    private fun points(pc: RecordedPointCloud): List<ReplayPoint> {
        if (!pc.isConsistent) return emptyList()
        return List(pc.count) { ReplayPoint(pc.ids[it], pc.xyz[3 * it], pc.xyz[3 * it + 1], pc.xyz[3 * it + 2], pc.confidence[it]) }
    }

    /**
     * I piani verticali di un frame come li vede l'algoritmo ATTUALE (stessa geometria di `ArScanView.wallObservation`:
     * vertici nel mondo, normale orizzontale, proiezione sulla direzione della parete; stessi filtri): permette di rieseguire
     * `WallScan` su una registrazione. Non lo migliora, lo riproduce.
     */
    fun currentWallObservations(frame: ReplayFrame): List<WallObservation> {
        if (frame.tracking != "TRACKING") return emptyList()
        return frame.planes.filter { it.isVertical && it.tracking == "TRACKING" && it.subsumedBy == null }.mapNotNull { wallObservation(it) }
    }

    private fun wallObservation(plane: ReplayPlane): WallObservation? {
        val n = plane.polygonWorld.size
        if (n < 3) return null
        val normal = plane.pose.rotate(0.0, 1.0, 0.0)
        val hl = sqrt(normal.x * normal.x + normal.z * normal.z)
        if (hl < MIN_HORIZONTAL_NORMAL) return null
        val nx = normal.x / hl
        val nz = normal.z / hl
        val tx = -nz
        val tz = nx
        var smin = Double.MAX_VALUE; var smax = -Double.MAX_VALUE
        var offset = 0.0
        var ymin = Double.MAX_VALUE; var ymax = -Double.MAX_VALUE
        for (v in plane.polygonWorld) {
            val s = v.x * tx + v.z * tz
            smin = minOf(smin, s); smax = maxOf(smax, s)
            offset += v.x * nx + v.z * nz
            ymin = minOf(ymin, v.y); ymax = maxOf(ymax, v.y)
        }
        offset /= n
        if (smax - smin < MIN_PLANE_LENGTH_M || ymax - ymin < MIN_PLANE_HEIGHT_M) return null
        return WallObservation(
            plane.key,
            ArXZ(tx * smin + nx * offset, tz * smin + nz * offset),
            ArXZ(tx * smax + nx * offset, tz * smax + nz * offset),
            ymin, ymax,
        )
    }

    // Stesse soglie di ArScanView (vedi lì): si copiano, non si condividono, per non toccare il codice Android in questa tappa.
    private const val MIN_HORIZONTAL_NORMAL = 0.5
    private const val MIN_PLANE_LENGTH_M = 0.3
    private const val MIN_PLANE_HEIGHT_M = 0.2
}
