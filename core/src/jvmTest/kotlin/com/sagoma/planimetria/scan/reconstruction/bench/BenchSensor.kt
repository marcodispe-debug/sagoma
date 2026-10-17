package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.CameraModelInfo
import com.sagoma.planimetria.scan.recording.CaptureDataset
import com.sagoma.planimetria.scan.recording.CaptureMode
import com.sagoma.planimetria.scan.recording.CaptureSettings
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
import java.util.Random
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * BENCHMARK (solo test) — SIMULATORE DEL SENSORE. Dal ground truth a osservazioni nel formato M0.2 (depth raw + filtrata +
 * confidenza + pose), che R1 legge senza sapere che sono sintetiche. Per ogni pixel conserva l'etichetta della superficie VERA
 * colpita dal raggio: è il collegamento esatto tra ogni punto di R1 e il ground truth. Volutamente imperfetto e parametrizzabile.
 */

/** Etichette delle superfici vere. */
object Label {
    const val NONE = 0; const val FLOOR = 1; const val CEILING = 2; const val OUTSIDE = 3
    fun wall(id: Int) = 100 + id
    fun box(id: Int) = 200 + id
    fun panel(id: Int) = 300 + id
    fun isWall(l: Int) = l in 100..199
    fun isBox(l: Int) = l in 200..299
    fun isPanel(l: Int) = l in 300..399
    fun wallId(l: Int) = l - 100
    fun cls(l: Int) = when { isWall(l) -> "WALL"; isBox(l) -> "OBJECT"; isPanel(l) -> "PANEL"; l == FLOOR -> "FLOOR"; l == CEILING -> "CEILING"; l == OUTSIDE -> "OUTSIDE"; else -> "NONE" }
}

/**
 * Parametri del sensore. σ della depth: σ(z) = [depthSigmaM] · z² (z in metri: [depthSigmaM] è la σ a 1 m). [dropout]: frazione di
 * pixel senza ritorno. [width] × [height]: densità (campo visivo fisso [hfovDeg]). Pose: rumore per frame e deriva lineare per frame
 * lungo x. [grazingDeg]: oltre questa incidenza la confidenza scende sotto la soglia di R1. [framesPerYaw]: frame per ogni imbardata.
 */
data class SensorParams(
    val seed: Long = 1,
    val depthSigmaM: Double = 0.0,
    val dropout: Double = 0.0,
    val width: Int = 64,
    val height: Int = 36,
    val hfovDeg: Double = 69.6,
    val poseNoiseM: Double = 0.0,
    val poseNoiseDeg: Double = 0.0,
    val driftMPerFrame: Double = 0.0,
    val grazingDeg: Double = 80.0,
    val maxRangeM: Double = 6.0,
    val frameIntervalMs: Int = 500,
    val framesPerYaw: Int = 1,
    /** Modello di rumore ALTERNATIVO sperimentale: σ(z) interpolata linearmente da (z, σ) (metri); null = modello attuale σ₁·z². */
    val noiseTable: List<Pair<Double, Double>>? = null,
)

/** Un frame simulato: posa vera (per il rendering), posa registrata (rumore/deriva: quella che R1 usa), depth, confidenza, etichette. */
/** [hitLabels]: superficie vera colpita (anche senza ritorno); [hitXYZ]: punto vero colpito (x, y, z per pixel, NaN se nessuno). */
class SimFrame(val truePose: RecPose, val recordedPose: RecPose, val mm: IntArray, val confidence: ByteArray, val labels: IntArray, val hitLabels: IntArray, val hitXYZ: FloatArray)

class SimResult(val room: GtRoom, val params: SensorParams, val k: CameraIntrinsics, val frames: List<SimFrame>, val recording: ScanRecording, val blobs: Map<String, ByteArray>)

object SensorSim {
    fun pose(x: Double, y: Double, z: Double, yawDeg: Double, pitchDeg: Double): RecPose {
        val a = yawDeg * Math.PI / 360; val b = pitchDeg * Math.PI / 360
        val yw = cos(a); val yy = sin(a); val pw = cos(b); val px = sin(b)
        return RecPose(x, y, z, qx = yw * px, qy = yy * pw, qz = -yy * px, qw = yw * pw)
    }

    private class Hit(val t: Double, val label: Int, val nx: Double, val ny: Double, val nz: Double, val noReturn: Boolean)

    /** Raggio dall'origine [o] con direzione [d] (non normalizzata: t = profondità lungo l'asse ottico). Prima superficie colpita. */
    private fun cast(room: GtRoom, o: DoubleArray, d: DoubleArray): Hit? {
        var best: Hit? = null
        fun take(h: Hit) { if (h.t > 1e-6 && (best == null || h.t < best!!.t)) best = h }
        val hH = room.heightM
        for (w in room.walls) {
            val den = d[0] * w.nx + d[2] * w.nz
            if (abs(den) < 1e-12) continue
            val t = -w.dist(o[0], o[2]) / den
            if (t <= 1e-6) continue
            val px = o[0] + d[0] * t; val py = o[1] + d[1] * t; val pz = o[2] + d[2] * t
            val u = w.along(px, pz)
            if (u < 0 || u > w.length || py < 0 || py > hH) continue
            if (w.openings.any { u in it.fromM..it.toM && py in it.bottomM..it.topM }) continue
            take(Hit(t, Label.wall(w.id), w.nx, 0.0, w.nz, w.noReturn.any { u in it.fromM..it.toM && py in it.bottomM..it.topM }))
        }
        for (p in room.panels) {
            val lx = p.bx - p.ax; val lz = p.bz - p.az; val len = sqrt(lx * lx + lz * lz)
            val nx = lz / len; val nz = -lx / len
            val den = d[0] * nx + d[2] * nz
            if (abs(den) < 1e-12) continue
            val t = -((o[0] - p.ax) * nx + (o[2] - p.az) * nz) / den
            val px = o[0] + d[0] * t; val py = o[1] + d[1] * t; val pz = o[2] + d[2] * t
            val u = ((px - p.ax) * lx + (pz - p.az) * lz) / len
            if (u < 0 || u > len || py < p.y0 || py > p.y1) continue
            take(Hit(t, Label.panel(p.id), nx, 0.0, nz, false))
        }
        // Pavimento e soffitto (fuori dal poligono: superfici esterne viste attraverso le aperture).
        for ((yv, lab) in listOf(0.0 to Label.FLOOR, hH to Label.CEILING)) {
            if (abs(d[1]) < 1e-12) continue
            val t = (yv - o[1]) / d[1]
            if (t <= 1e-6) continue
            val px = o[0] + d[0] * t; val pz = o[2] + d[2] * t
            val ins = room.inside(px, pz)
            take(Hit(t, if (ins) lab else Label.OUTSIDE, 0.0, if (lab == Label.FLOOR) 1.0 else -1.0, 0.0, !ins && !room.outsideReturns))
        }
        // Fondale esterno (oltre porte e finestre).
        val ext = doubleArrayOf(room.minX - room.outsideM, room.maxX + room.outsideM, room.minZ - room.outsideM, room.maxZ + room.outsideM)
        for ((axis, v) in listOf(0 to ext[0], 0 to ext[1], 2 to ext[2], 2 to ext[3])) {
            if (abs(d[axis]) < 1e-12) continue
            val t = (v - o[axis]) / d[axis]
            if (t <= 1e-6) continue
            take(Hit(t, Label.OUTSIDE, if (axis == 0) 1.0 else 0.0, 0.0, if (axis == 2) 1.0 else 0.0, !room.outsideReturns))
        }
        for (b in room.boxes) {
            var t0 = 0.0; var t1 = Double.MAX_VALUE; var face = -1
            val lo = doubleArrayOf(b.x0, b.y0, b.z0); val hi = doubleArrayOf(b.x1, b.y1, b.z1)
            var ok = true
            for (a in 0 until 3) {
                if (abs(d[a]) < 1e-12) { if (o[a] < lo[a] || o[a] > hi[a]) ok = false; continue }
                var ta = (lo[a] - o[a]) / d[a]; var tb = (hi[a] - o[a]) / d[a]
                if (ta > tb) { val s = ta; ta = tb; tb = s }
                if (ta > t0) { t0 = ta; face = a }
                t1 = min(t1, tb)
            }
            if (ok && t0 <= t1 && t0 > 1e-6 && face >= 0) take(Hit(t0, Label.box(b.id), if (face == 0) 1.0 else 0.0, if (face == 1) 1.0 else 0.0, if (face == 2) 1.0 else 0.0, false))
        }
        return best
    }

    fun simulate(room: GtRoom, stations: List<Station>, p: SensorParams, viewsPerStation: Int? = null): SimResult {
        val f = p.width / 2.0 / tan(Math.toRadians(p.hfovDeg / 2))
        val k = CameraIntrinsics(fx = f, fy = f, cx = p.width / 2.0, cy = p.height / 2.0, width = p.width, height = p.height)
        val rnd = Random(p.seed)
        val frames = mutableListOf<SimFrame>()
        var n = 0
        for (st in stations) {
            val yaws = if (viewsPerStation != null) {
                val span = st.yawToDeg - st.yawFromDeg
                (0 until viewsPerStation).map { if (viewsPerStation == 1) st.yawFromDeg else st.yawFromDeg + span * it / (viewsPerStation - 1) }
            } else generateSequence(st.yawFromDeg) { it + st.yawStepDeg }.takeWhile { it <= st.yawToDeg + 1e-9 }.toList()
            for (yaw in yaws) repeat(p.framesPerYaw) {
                val truth = pose(st.x, st.y, st.z, yaw, st.pitchDeg)
                // Posa registrata: rumore per frame e deriva lineare (lungo x) accumulata.
                val nyaw = yaw + rnd.nextGaussian() * p.poseNoiseDeg; val npitch = st.pitchDeg + rnd.nextGaussian() * p.poseNoiseDeg
                val rec = pose(st.x + rnd.nextGaussian() * p.poseNoiseM + p.driftMPerFrame * n, st.y + rnd.nextGaussian() * p.poseNoiseM, st.z + rnd.nextGaussian() * p.poseNoiseM, nyaw, npitch)
                val mm = IntArray(p.width * p.height); val conf = ByteArray(p.width * p.height); val labels = IntArray(p.width * p.height); val hitLabels = IntArray(p.width * p.height)
                val hitXYZ = FloatArray(3 * p.width * p.height) { Float.NaN }
                for (v in 0 until p.height) for (u in 0 until p.width) {
                    val i = v * p.width + u
                    val dc = doubleArrayOf((u - k.cx) / k.fx, (k.cy - v) / k.fy, -1.0)
                    val w = truth.rotate(dc[0], dc[1], dc[2])
                    val d = doubleArrayOf(w.x, w.y, w.z)
                    val h = cast(room, doubleArrayOf(truth.x, truth.y, truth.z), d)
                    val drop = rnd.nextDouble() < p.dropout
                    val noise = rnd.nextGaussian()
                    if (h == null) continue
                    hitLabels[i] = h.label
                    hitXYZ[3 * i] = (truth.x + d[0] * h.t).toFloat(); hitXYZ[3 * i + 1] = (truth.y + d[1] * h.t).toFloat(); hitXYZ[3 * i + 2] = (truth.z + d[2] * h.t).toFloat()
                    val z = h.t
                    if (h.noReturn || drop || z > p.maxRangeM) continue
                    val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
                    val inc = Math.toDegrees(acos(min(1.0, abs((d[0] * h.nx + d[1] * h.ny + d[2] * h.nz) / len))))
                    // Modello di default: stessa espressione di prima (stessi arrotondamenti, benchmark byte-identico).
                    val zn = if (p.noiseTable == null) z + noise * p.depthSigmaM * z * z else z + noise * sigmaAt(p, z)
                    mm[i] = (zn * 1000).toInt().coerceAtLeast(1)
                    conf[i] = (if (inc > p.grazingDeg) 16 else 255).toByte()
                    labels[i] = h.label
                }
                frames.add(SimFrame(truth, rec, mm, conf, labels, hitLabels, hitXYZ))
                n++
            }
        }
        val (rec, blobs) = recording(frames, k, p)
        return SimResult(room, p, k, frames, rec, blobs)
    }

    /** Registrazione M0.2 in memoria (stessa struttura usata dalla stanza sintetica dei test): raw = filtrata = la depth simulata. */
    private fun recording(frames: List<SimFrame>, k: CameraIntrinsics, p: SensorParams): Pair<ScanRecording, Map<String, ByteArray>> {
        val blobs = HashMap<String, ByteArray>()
        val poses = mutableListOf<PoseSample>(); val depth = mutableListOf<DepthKeyframe>()
        for ((i, fr) in frames.withIndex()) {
            val ts = 1_000_000_000L + i * p.frameIntervalMs * 1_000_000L
            val cam = fr.recordedPose
            poses.add(PoseSample(seq = i, timestampNs = ts, tracking = "TRACKING", camera = cam))
            blobs[CaptureDataset.depthPath(i)] = DepthRaw.encode(fr.mm)
            blobs[CaptureDataset.rawDepthPath(i)] = DepthRaw.encode(fr.mm)
            blobs[CaptureDataset.confidencePath(i)] = fr.confidence
            depth.add(
                DepthKeyframe(
                    seq = i, frameSeq = i, timestampNs = ts, frameTimestampNs = ts, camera = cam, poseTimestampNs = ts, poseExact = true,
                    width = p.width, height = p.height, path = CaptureDataset.depthPath(i), rawPath = CaptureDataset.rawDepthPath(i),
                    confidencePath = CaptureDataset.confidencePath(i), intrinsics = k, intrinsicsSource = IntrinsicsSource.TEXTURE_SCALED,
                    poseFrameSeq = i, poseMatch = PoseMatch.FRAME, rawTimestampNs = ts, rawWidth = p.width, rawHeight = p.height,
                    rawStatus = StreamStatus.SAVED, confidenceStatus = StreamStatus.SAVED, rawPoseFrameSeq = i, rawPoseMatch = PoseMatch.FRAME,
                    rawPoseDeltaNs = 0, rawCamera = cam,
                ),
            )
        }
        val header = RecordingHeader(
            session = SessionInfo(depthMode = "AUTOMATIC", depthSupported = true),
            capture = CaptureSettings(CaptureMode.ENVIRONMENT, 100, 200, 500, 85, maxQueueBytes = 1 shl 20),
            camera = CameraModelInfo(90, null, CameraIntrinsics(k.fx * 12, k.fy * 12, k.cx * 12, k.cy * 12, p.width * 12, p.height * 12)),
        )
        return ScanRecording(header, emptyList(), RecordingEnd(), poses = poses, depth = depth) to blobs
    }
}

/** σ della depth alla profondità [z]: modello attuale σ₁·z², oppure la tabella sperimentale (interpolazione lineare, estremi costanti). */
fun sigmaAt(p: SensorParams, z: Double): Double {
    val t = p.noiseTable ?: return p.depthSigmaM * z * z
    if (z <= t.first().first) return t.first().second
    if (z >= t.last().first) return t.last().second
    val k = t.indexOfFirst { it.first > z }
    val (z0, s0) = t[k - 1]; val (z1, s1) = t[k]
    return s0 + (s1 - s0) * (z - z0) / (z1 - z0)
}
