package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.CaptureDataset
import com.sagoma.planimetria.scan.recording.CaptureMode
import com.sagoma.planimetria.scan.recording.CaptureSettings
import com.sagoma.planimetria.scan.recording.CameraModelInfo
import com.sagoma.planimetria.scan.recording.DepthKeyframe
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.IntrinsicsSource
import com.sagoma.planimetria.scan.recording.PoseMatch
import com.sagoma.planimetria.scan.recording.PoseSample
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.RecordingEnd
import com.sagoma.planimetria.scan.recording.RecordingHeader
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.SessionInfo
import com.sagoma.planimetria.scan.recording.StreamStatus
import com.sagoma.planimetria.scan.recording.rotate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Scatola allineata agli assi (metri). */
data class Box(val x0: Double, val y0: Double, val z0: Double, val x1: Double, val y1: Double, val z1: Double)

/**
 * Stanza sintetica per i test di R1/R2: pareti x ∈ [0, 4], z ∈ [0, 3], pavimento y = 0, soffitto y = 2,6, più scatole (mobili) e
 * buchi nelle pareti (finestre). Depth calcolata con i raggi in modo esatto, con rumore deterministico facoltativo, pose note.
 */
class SyntheticRoom(
    val boxes: List<Box> = listOf(Box(1.4, 0.0, 0.8, 2.4, 0.8, 1.1)),
    /** Buchi nella parete z = 0 (x0, y0, x1, y1): oltre la parete c'è un fondo a z = −2. */
    val windows: List<DoubleArray> = emptyList(),
    val noiseM: Double = 0.0,
    val width: Int = 64,
    val height: Int = 36,
) {
    val k = CameraIntrinsics(fx = 46.0, fy = 46.0, cx = 32.0, cy = 18.0, width = width, height = height)

    /** Posa della camera: posizione e rotazione (imbardata attorno a y, poi beccheggio attorno a x). */
    fun pose(x: Double, y: Double, z: Double, yawDeg: Double, pitchDeg: Double): RecPose {
        val a = yawDeg * Math.PI / 360; val b = pitchDeg * Math.PI / 360
        // q = q_yaw · q_pitch
        val yw = cos(a); val yy = sin(a)
        val pw = cos(b); val px = sin(b)
        return RecPose(x, y, z, qx = yw * px, qy = yy * pw, qz = -yy * px, qw = yw * pw)
    }

    /** Distanza lungo il raggio (parametro t con direzione non normalizzata) alla prima superficie. */
    fun cast(o: DoubleArray, d: DoubleArray): Double {
        var best = Double.MAX_VALUE
        // Interno della stanza: il raggio esce da uno dei sei piani.
        fun plane(axis: Int, value: Double) {
            if (abs(d[axis]) < 1e-12) return
            val t = (value - o[axis]) / d[axis]
            if (t <= 1e-9 || t >= best) return
            val p = DoubleArray(3) { o[it] + d[it] * t }
            if (axis == 2 && value == 0.0 && windows.any { w -> p[0] in w[0]..w[2] && p[1] in w[1]..w[3] }) {
                // Attraverso la finestra fino al fondo z = −2.
                val t2 = (-2.0 - o[2]) / d[2]
                if (t2 > 0 && t2 < best) best = t2
                return
            }
            best = t
        }
        plane(0, 0.0); plane(0, 4.0); plane(1, 0.0); plane(1, 2.6); plane(2, 0.0); plane(2, 3.0)
        for (bx in boxes) {
            var t0 = 0.0; var t1 = Double.MAX_VALUE
            val lo = doubleArrayOf(bx.x0, bx.y0, bx.z0); val hi = doubleArrayOf(bx.x1, bx.y1, bx.z1)
            var ok = true
            for (a in 0 until 3) {
                if (abs(d[a]) < 1e-12) { if (o[a] < lo[a] || o[a] > hi[a]) ok = false; continue }
                var ta = (lo[a] - o[a]) / d[a]; var tb = (hi[a] - o[a]) / d[a]
                if (ta > tb) { val s = ta; ta = tb; tb = s }
                t0 = maxOf(t0, ta); t1 = minOf(t1, tb)
            }
            if (ok && t0 <= t1 && t0 > 1e-9 && t0 < best) best = t0
        }
        return best
    }

    /** Depth (mm) di una camera: profondità lungo l'asse ottico per ogni pixel, 0 se nessun dato. */
    fun render(pose: RecPose, seed: Int = 0, invalid: (Int, Int) -> Boolean = { _, _ -> false }): IntArray {
        val out = IntArray(width * height)
        var rnd = (seed * 2654435761L + 12345L) and 0x7FFFFFFF
        for (v in 0 until height) for (u in 0 until width) {
            val dc = doubleArrayOf((u - k.cx) / k.fx, (k.cy - v) / k.fy, -1.0)
            val w = pose.rotate(dc[0], dc[1], dc[2])
            val t = cast(doubleArrayOf(pose.x, pose.y, pose.z), doubleArrayOf(w.x, w.y, w.z))
            rnd = (rnd * 1103515245L + 12345L) and 0x7FFFFFFF
            val noise = if (noiseM > 0) ((rnd % 2001) / 1000.0 - 1.0) * noiseM else 0.0
            out[v * width + u] = if (invalid(u, v) || t == Double.MAX_VALUE) 0 else ((t + noise) * 1000).toInt().coerceAtLeast(0)
        }
        return out
    }

    /** Camere: due giri di 24 viste (imbardata ogni 15°), beccheggio −20°, una vista ogni 0,5 s (gruppi di vista distinti). */
    fun cameras(): List<RecPose> = buildList {
        for (c in listOf(doubleArrayOf(2.0, 1.4, 2.0), doubleArrayOf(2.6, 1.5, 2.2))) for (i in 0 until 24) add(pose(c[0], c[1], c[2], i * 15.0, -20.0))
    }

    /**
     * Registrazione M0.2 sintetica: una posa per frame (frame ogni 0,5 s), depth raw (con confidenza piena) e filtrata uguali, posa
     * della raw = quella del suo frame. [rawPoseOverride] cambia la posa del frame da cui viene la raw (per il test della posa propria).
     */
    fun recording(
        poses: List<RecPose> = cameras(),
        confidence: (Int, Int, Int) -> Int = { _, _, _ -> 255 },
        invalid: (Int, Int, Int) -> Boolean = { _, _, _ -> false },
    ): Pair<ScanRecording, Map<String, ByteArray>> {
        val blobs = HashMap<String, ByteArray>()
        val poseLines = mutableListOf<PoseSample>()
        val depth = mutableListOf<DepthKeyframe>()
        for ((i, p) in poses.withIndex()) {
            val ts = 1_000_000_000L + i * 500_000_000L
            poseLines.add(PoseSample(seq = i, timestampNs = ts, tracking = "TRACKING", camera = p))
            val mm = render(p, i) { u, v -> invalid(i, u, v) }
            blobs[CaptureDataset.depthPath(i)] = DepthRaw.encode(mm)
            blobs[CaptureDataset.rawDepthPath(i)] = DepthRaw.encode(mm)
            blobs[CaptureDataset.confidencePath(i)] = ByteArray(width * height) { idx -> confidence(i, idx % width, idx / width).toByte() }
            depth.add(
                DepthKeyframe(
                    seq = i, frameSeq = i, timestampNs = ts, frameTimestampNs = ts, camera = p, poseTimestampNs = ts, poseExact = true,
                    width = width, height = height, path = CaptureDataset.depthPath(i), rawPath = CaptureDataset.rawDepthPath(i),
                    confidencePath = CaptureDataset.confidencePath(i), intrinsics = k, intrinsicsSource = IntrinsicsSource.TEXTURE_SCALED,
                    poseFrameSeq = i, poseMatch = PoseMatch.FRAME, rawTimestampNs = ts, rawWidth = width, rawHeight = height,
                    rawStatus = StreamStatus.SAVED, confidenceStatus = StreamStatus.SAVED, rawPoseFrameSeq = i, rawPoseMatch = PoseMatch.FRAME,
                    rawPoseDeltaNs = 0, rawCamera = p,
                ),
            )
        }
        val header = RecordingHeader(
            session = SessionInfo(depthMode = "AUTOMATIC", depthSupported = true),
            capture = CaptureSettings(CaptureMode.ENVIRONMENT, 100, 200, 500, 85, maxQueueBytes = 1 shl 20),
            camera = CameraModelInfo(90, null, CameraIntrinsics(k.fx * 12, k.fy * 12, k.cx * 12, k.cy * 12, width * 12, height * 12)),
        )
        return ScanRecording(header, emptyList(), RecordingEnd(), poses = poseLines, depth = depth) to blobs
    }
}
