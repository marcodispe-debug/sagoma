package com.sagoma.planimetria.scanner

import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.RecordedFrame
import com.sagoma.planimetria.scan.recording.RecordedPlane
import com.sagoma.planimetria.scan.recording.RecordedPointCloud
import com.sagoma.planimetria.scan.recording.RecordingSanitizer
import kotlin.math.round

/**
 * Scansione guidata (M2.0, prova solo Pro): il frame di ARCore ridotto agli STESSI dati che la registrazione salva per ogni
 * frame (posa, piani, nuvola di punti; stessi arrotondamenti), così il motore riceve dal vivo quello che riceve rigiocando una
 * registrazione (`AimFrameBuilder`). Non registra nulla e non tocca [ScanRecorder]. Da usare sul thread di disegno.
 */
internal class LiveAimFrames {
    private val planeKeys = HashMap<Plane, Int>()
    private var index = 0
    private var startNs = 0L
    private var lastFrameTs = Long.MIN_VALUE
    private var lastCloudTs = Long.MIN_VALUE

    /** Il frame, al massimo uno ogni [INTERVAL_NS] di tempo di ARCore (come la registrazione); null se è troppo presto. */
    fun next(session: Session, frame: Frame, camera: Camera, floorY: Float?): RecordedFrame? {
        val ts = frame.timestamp
        if (ts == 0L) return null
        if (lastFrameTs != Long.MIN_VALUE && ts - lastFrameTs < INTERVAL_NS) return null
        lastFrameTs = ts
        if (startNs == 0L) startNs = ts
        val planes = session.getAllTrackables(Plane::class.java).map { p ->
            val poly = p.polygon
            val start = poly.position()
            val n = poly.remaining() / 2 * 2
            val normal = p.centerPose.yAxis
            RecordedPlane(
                key = keyOf(p), kind = p.type.name, tracking = p.trackingState.name, subsumedBy = p.subsumedBy?.let { keyOf(it) },
                pose = recPose(p.centerPose), normal = listOf(r5(normal[0]), r5(normal[1]), r5(normal[2])),
                extentX = r5(p.extentX), extentZ = r5(p.extentZ), polygon = List(n) { r5(poly.get(start + it)) },
            )
        }
        var cloud: RecordedPointCloud? = null
        try {
            frame.acquirePointCloud().use { pc ->
                if (pc.timestamp != lastCloudTs) {
                    lastCloudTs = pc.timestamp
                    val points = pc.points
                    val ids = pc.ids
                    val pStart = points.position()
                    val iStart = ids.position()
                    val n = minOf(points.remaining() / 4, ids.remaining())
                    val xyz = ArrayList<Double>(n * 3)
                    val conf = ArrayList<Double>(n)
                    val idList = ArrayList<Int>(n)
                    for (i in 0 until n) {
                        xyz.add(r5(points.get(pStart + 4 * i))); xyz.add(r5(points.get(pStart + 4 * i + 1))); xyz.add(r5(points.get(pStart + 4 * i + 2)))
                        conf.add(r5(points.get(pStart + 4 * i + 3)))
                        idList.add(ids.get(iStart + i))
                    }
                    cloud = RecordedPointCloud(pc.timestamp, xyz, conf, idList)
                }
            }
        } catch (_: Exception) {
            // Nuvola non disponibile in questo frame: il motore usa i punti già visti (sono solo controllo qualità).
        }
        val f = RecordedFrame(
            index = index++, timestampNs = ts, elapsedMs = (ts - startNs) / 1_000_000, tracking = camera.trackingState.name,
            failureReason = camera.trackingFailureReason.takeIf { it != TrackingFailureReason.NONE }?.name,
            camera = recPose(camera.pose), cameraDisplay = recPose(camera.displayOrientedPose), floorY = floorY?.let { r5(it) },
            planes = planes, points = cloud,
        )
        return RecordingSanitizer.frame(f)
    }

    private fun keyOf(p: Plane): Int = planeKeys.getOrPut(p) { planeKeys.size }

    private companion object {
        /** Come la registrazione (`recordIntervalMs` 100): circa 10 frame al secondo. */
        const val INTERVAL_NS = 100_000_000L

        fun r5(v: Float): Double = round(v.toDouble() * 100_000.0) / 100_000.0

        fun recPose(p: Pose) = RecPose(r5(p.tx()), r5(p.ty()), r5(p.tz()), r5(p.qx()), r5(p.qy()), r5(p.qz()), r5(p.qw()))
    }
}
