package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.recording.ArCoreInfo
import com.sagoma.planimetria.scan.recording.DeviceInfo
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.RecordedFrame
import com.sagoma.planimetria.scan.recording.RecordedPlane
import com.sagoma.planimetria.scan.recording.RecordedPointCloud
import com.sagoma.planimetria.scan.recording.RecordingEnd
import com.sagoma.planimetria.scan.recording.RecordingHeader
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.SessionInfo
import com.sagoma.planimetria.scan.recording.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Scene sintetiche per i test della scansione assistita: pareti verticali (piani ARCore), una camera che guarda un punto, rumore
 * deterministico. Tutto nel mondo ARCore (metri, y verso l'alto, pianta = x, z).
 */
internal object AssistedScene {
    const val DT = 50L

    /** Rumore deterministico in [-1, 1]. */
    fun noise(t: Long, seed: Int): Double = sin(t * 0.0371 + seed * 1.7) * cos(t * 0.0113 + seed * 0.9)

    /** Ondeggiare lento della mano (periodo di circa 1,5 s): in [-1, 1]. */
    fun sway(t: Long, seed: Int): Double = sin(t * 0.0043 + seed * 1.3) * cos(t * 0.0021 + seed * 0.7)

    private fun quatFromColumns(c0: DoubleArray, c1: DoubleArray, c2: DoubleArray): DoubleArray {
        val m00 = c0[0]; val m10 = c0[1]; val m20 = c0[2]
        val m01 = c1[0]; val m11 = c1[1]; val m21 = c1[2]
        val m02 = c2[0]; val m12 = c2[1]; val m22 = c2[2]
        val tr = m00 + m11 + m22
        val q = DoubleArray(4)
        if (tr > 0) {
            val s = sqrt(tr + 1.0) * 2
            q[3] = 0.25 * s; q[0] = (m21 - m12) / s; q[1] = (m02 - m20) / s; q[2] = (m10 - m01) / s
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1.0 + m00 - m11 - m22) * 2
            q[3] = (m21 - m12) / s; q[0] = 0.25 * s; q[1] = (m01 + m10) / s; q[2] = (m02 + m20) / s
        } else if (m11 > m22) {
            val s = sqrt(1.0 + m11 - m00 - m22) * 2
            q[3] = (m02 - m20) / s; q[0] = (m01 + m10) / s; q[1] = 0.25 * s; q[2] = (m12 + m21) / s
        } else {
            val s = sqrt(1.0 + m22 - m00 - m11) * 2
            q[3] = (m10 - m01) / s; q[0] = (m02 + m20) / s; q[1] = (m12 + m21) / s; q[2] = 0.25 * s
        }
        return q
    }

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun unit(a: DoubleArray): DoubleArray { val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }

    /**
     * Piano verticale lungo il segmento (ax, az) → (bx, bz), tra le quote y0 e y1. `side` = +1 / −1: da che lato punta la normale
     * (rispetto a (−dz, dx)). `tiltDeg` inclina la normale verso l'alto. `jitter` sposta il piano lungo la normale.
     */
    fun plane(
        key: Int, ax: Double, az: Double, bx: Double, bz: Double, y0: Double = 0.0, y1: Double = 2.5, side: Int = 1, tiltDeg: Double = 0.0,
        tracking: String = "TRACKING", subsumedBy: Int? = null, shift: Double = 0.0, extraHeading: Double = 0.0,
    ): RecordedPlane {
        val dx = bx - ax; val dz = bz - az; val len = sqrt(dx * dx + dz * dz)
        val h = (if (extraHeading != 0.0) { // ruota la direzione attorno al centro
            val a = extraHeading * PI / 180; doubleArrayOf(dx * cos(a) - dz * sin(a), 0.0, dx * sin(a) + dz * cos(a))
        } else doubleArrayOf(dx, 0.0, dz)).let { unit(it) }
        val nh = doubleArrayOf(-h[2] * side, 0.0, h[0] * side)
        val phi = tiltDeg * PI / 180
        val n = doubleArrayOf(nh[0] * cos(phi), sin(phi), nh[2] * cos(phi))
        val t = unit(cross(doubleArrayOf(0.0, 1.0, 0.0), n))
        val zl = cross(t, n)
        val q = quatFromColumns(t, n, zl)
        val cx = (ax + bx) / 2 + nh[0] * shift; val cz = (az + bz) / 2 + nh[2] * shift
        val pose = RecPose(cx, (y0 + y1) / 2, cz, q[0], q[1], q[2], q[3])
        val hz = (y1 - y0) / cos(phi)
        val nw = pose.rotate(0.0, 1.0, 0.0)
        return RecordedPlane(
            key, "VERTICAL", tracking, subsumedBy, pose, listOf(nw.x, nw.y, nw.z), len, hz,
            listOf(-len / 2, -hz / 2, len / 2, -hz / 2, len / 2, hz / 2, -len / 2, hz / 2),
        )
    }

    /** Camera in (px, py, pz) che guarda (tx, ty, tz). */
    fun camera(px: Double, py: Double, pz: Double, tx: Double, ty: Double, tz: Double): RecPose {
        val f = unit(doubleArrayOf(tx - px, ty - py, tz - pz))
        val r = unit(cross(f, doubleArrayOf(0.0, 1.0, 0.0)))
        val u = cross(r, f)
        val q = quatFromColumns(r, u, doubleArrayOf(-f[0], -f[1], -f[2]))
        return RecPose(px, py, pz, q[0], q[1], q[2], q[3])
    }

    /** Muro lungo x a z = 0 (normale +z), larghezza 4 m, alto 2,5 m, visto da una camera in (2, 1,4, 2) che guarda (2, 1,25, 0). */
    fun wallA(key: Int = 1) = plane(key, 0.0, 0.0, 4.0, 0.0)

    val standardCam = camera(2.0, 1.4, 2.0, 2.0, 1.25, 0.0)

    class Scene(val durationMs: Long) {
        var cam: (Long) -> RecPose = { standardCam }
        var planes: (Long) -> List<RecordedPlane> = { emptyList() }
        var tracking: (Long) -> Boolean = { true }
        var points: (Long, Int) -> RecordedPointCloud? = { _, _ -> null }
        var floorY: Double? = 0.0
    }

    fun scene(durationMs: Long, build: Scene.() -> Unit): List<RecordedFrame> {
        val s = Scene(durationMs).apply(build)
        return (0..durationMs / DT).map { i ->
            val t = i * DT
            val c = s.cam(t)
            RecordedFrame(
                index = i.toInt(), timestampNs = 1_000_000_000L + t * 1_000_000L, elapsedMs = t, tracking = if (s.tracking(t)) "TRACKING" else "PAUSED",
                camera = c, cameraDisplay = c, floorY = s.floorY, planes = s.planes(t), points = s.points(t, i.toInt()),
            )
        }
    }

    fun recording(frames: List<RecordedFrame>) = ScanRecording(
        RecordingHeader(createdAtMillis = 1L, recordIntervalMs = DT, device = DeviceInfo("Test", "Fantasma 1", 34), arcore = ArCoreInfo("1.44.0", "x"), session = SessionInfo(depthMode = "ENABLED", depthSupported = true)),
        frames, RecordingEnd(frames = frames.size, durationMs = frames.lastOrNull()?.elapsedMs ?: 0),
    )

    /** Punti sul muro lungo x (z = bias + rumore), in un solo frame. */
    fun wallPoints(bias: Double, spread: Double, n: Int = 60): RecordedPointCloud {
        val ids = (0 until n).toList()
        val xyz = ids.flatMap { listOf(0.2 + 3.6 * it / n, 0.2 + 2.1 * ((it * 7) % n) / n, bias + spread * noise(it * 50L, 3)) }
        return RecordedPointCloud(5L, xyz, ids.map { 0.9 }, ids)
    }
}

/** Esegue il motore su una scena, come farebbe il telefono, con un gancio per i gesti dell'utente. */
internal class Run(val frames: List<AimFrame>, val p: AssistedParams = AssistedParams(), gesture: (AimFrame, AssistedState) -> Gesture? = { _, _ -> null }) {
    enum class Gesture { CONFIRM, UNDO }

    val states = ArrayList<AssistedState>()
    val events = ArrayList<AssistedEvent>()
    var last: AssistedState = AssistedScan.initial()

    init {
        for (f in frames) {
            var r = AssistedScan.step(last, f, p)
            events += r.events
            when (gesture(f, r.state)) {
                Gesture.CONFIRM -> { val c = AssistedScan.confirm(r.state, f.timeMs, p); events += c.events; r = StepResult(c.state, emptyList()) }
                Gesture.UNDO -> { val c = AssistedScan.undo(r.state, f.timeMs); events += c.events; r = StepResult(c.state, emptyList()) }
                null -> {}
            }
            last = r.state
            states += last
        }
    }

    fun stateAt(timeMs: Long): AssistedState = states[frames.indexOfFirst { it.timeMs >= timeMs }.let { if (it < 0) frames.lastIndex else it }]
    inline fun <reified T : AssistedEvent> eventsOf(): List<T> = events.filterIsInstance<T>()
    fun firstProposedMs(): Long? = eventsOf<AssistedEvent.Proposed>().firstOrNull()?.timeMs
}

internal fun aimFrames(recorded: List<RecordedFrame>) = AimFrames.from(recorded)
