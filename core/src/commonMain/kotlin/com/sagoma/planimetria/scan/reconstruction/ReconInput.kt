package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.ArCameraProjection
import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.CaptureIndex
import com.sagoma.planimetria.scan.recording.DepthKeyframe
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.IntrinsicsSource
import com.sagoma.planimetria.scan.recording.PoseMatch
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.StreamStatus
import com.sagoma.planimetria.scan.recording.rotate
import kotlin.math.abs
import kotlin.math.sqrt

/** I file della registrazione (depth, raw, confidenza, JPEG) per percorso relativo; null se manca. */
fun interface DatasetBlobs {
    fun read(path: String): ByteArray?
}

/** Parametri della ricostruzione R1/R2. Valori iniziali, da tarare: ognuno ha il suo motivo nel commento. */
data class ReconParams(
    /** Sotto 30 cm la depth di ARCore non è affidabile; oltre 4 m il rumore cresce col quadrato della distanza. */
    val minRangeM: Double = 0.3,
    val maxRangeM: Double = 4.0,
    /** Confidenza minima della raw (0..255): sotto, il pixel si scarta. Il peso è la confidenza/255. */
    val minRawConfidence: Int = 32,
    /** Pixel "volanti" ai bordi degli oggetti: salto di profondità con un vicino oltre questa frazione della distanza. */
    val edgeJumpRatio: Double = 0.08,
    /** Peso della depth filtrata quando si usa come ripiego (evidenza secondaria). */
    val fallbackWeight: Double = 0.5,
    /** Voxel di indicizzazione (i fit usano i punti originali). */
    val voxelM: Double = 0.04,
    /** Griglia di pianta. */
    val planCellM: Double = 0.05,
    /** Griglia 3D dello spazio libero. */
    val freeCellM: Double = 0.10,
    /** Un raggio di spazio libero ogni [freeRayStride] pixel per lato. */
    val freeRayStride: Int = 2,
    /** Gruppi di vista: un gruppo nuovo dopo 0,5 s, 10 cm o 5° di movimento della camera. */
    val viewGroupMs: Long = 500,
    val viewGroupMoveM: Double = 0.10,
    val viewGroupTurnDeg: Double = 5.0,
    /** Solo i depth keyframe con seq pari (0), dispari (1) o tutti (null): per il test di stabilità. */
    val frameParity: Int? = null,
    /** SOLO DIAGNOSI: ignora la raw e usa la depth filtrata ovunque (per confrontare le due fonti). Mai nel percorso normale. */
    val diagnosticFilteredOnly: Boolean = false,
    /** SOLO DIAGNOSI: usa la raw senza il filtro di confidenza (per vedere cosa toglie il filtro). */
    val diagnosticIgnoreConfidence: Boolean = false,
)

/** Da dove viene una depth usata nella mappa. */
enum class DepthUse { RAW, FILTERED_FALLBACK }

/** Esito di un depth keyframe: usato (raw o ripiego) o saltato, con il motivo. */
data class FrameUse(
    val depthSeq: Int,
    val use: DepthUse?,
    val skipReason: String?,
    val poseFrameSeq: Int? = null,
    val poseMatch: String? = null,
    val timestampNs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val pixels: Int = 0,
    val accepted: Int = 0,
    val rejectedZero: Int = 0,
    val rejectedRange: Int = 0,
    val rejectedConfidence: Int = 0,
    val rejectedEdge: Int = 0,
)

/** Una depth pronta per l'integrazione: valori (mm), confidenza (0..255 o null), posa e intrinseche della depth. */
class DepthFrame(
    val depthSeq: Int,
    val use: DepthUse,
    val timestampNs: Long,
    val pose: RecPose,
    val k: CameraIntrinsics,
    val mm: IntArray,
    val confidence: ByteArray?,
    val poseFrameSeq: Int?,
    val poseMatch: String?,
)

/**
 * Sceglie, per ogni depth keyframe, cosa usare. Fonte primaria: depth RAW + confidenza, con la posa del frame della raw
 * ([DepthKeyframe.rawPoseFrameSeq], registrata dal telefono, o ricavata dal suo timestamp con [CaptureIndex.rawPoseFor]) e le
 * intrinseche della texture. Ripiego esplicito: la depth filtrata con la sua posa, solo se la raw non c'è o non ha una posa,
 * con peso ridotto. Le raw ripetute non si riusano (sono la stessa osservazione); nessuna posa si inventa.
 */
object DepthFrames {
    fun select(r: ScanRecording, blobs: DatasetBlobs, p: ReconParams): Pair<List<DepthFrame>, List<FrameUse>> {
        val index = CaptureIndex(r)
        val frames = mutableListOf<DepthFrame>()
        val uses = mutableListOf<FrameUse>()
        val texture = r.header.camera?.textureIntrinsics
        for (d in r.depth.sortedBy { it.seq }) {
            if (p.frameParity != null && d.seq % 2 != p.frameParity) continue
            fun skip(reason: String) = uses.add(FrameUse(d.seq, null, reason, timestampNs = d.timestampNs))
            if (d.rawStatus == StreamStatus.DUPLICATE) { skip("raw ripetuta: nessuna osservazione nuova"); continue }
            // Intrinseche della depth: quelle registrate se vengono dalla texture; per i file vecchi, ricalcolate dalla texture.
            val k = depthIntrinsics(d, texture) ?: run { skip("intrinseche della depth non disponibili"); null } ?: continue
            val raw = d.rawPath?.let { path -> blobs.read(path) }
            val rawPose = if (raw != null) index.rawPoseFor(d) else null
            if (!p.diagnosticFilteredOnly && raw != null && rawPose?.pose?.camera != null && rawPose.kind != PoseMatch.UNAVAILABLE) {
                val w = d.rawWidth ?: d.width
                val h = d.rawHeight ?: d.height
                if (raw.size != w * h * 2) { skip("raw di ${raw.size} byte, attesi ${w * h * 2}"); continue }
                val conf = if (p.diagnosticIgnoreConfidence) null else d.confidencePath?.let { blobs.read(it) }?.takeIf { it.size == w * h }
                val kr = if (w == k.width && h == k.height) k else k.scaledTo(w, h)
                frames.add(DepthFrame(d.seq, DepthUse.RAW, d.rawTimestampNs ?: d.timestampNs, rawPose.pose.camera, kr, DepthRaw.decode(raw, w, h), conf, rawPose.pose.seq, rawPose.kind))
                continue
            }
            // Ripiego: depth filtrata con la sua posa (per frame), solo se esatta.
            val pose = index.poseFor(d)
            val filtered = blobs.read(d.path)
            val why = when {
                d.rawPath == null -> "raw ${d.rawStatus ?: "assente"}"
                raw == null -> "file raw mancante"
                else -> "raw senza posa (${rawPose?.kind ?: "?"}${rawPose?.detail?.let { ": $it" } ?: ""})"
            }
            if (filtered == null || filtered.size != d.width * d.height * 2) { skip("$why; depth filtrata non leggibile"); continue }
            val cam = pose?.item?.camera
            if (pose == null || !pose.exact || cam == null) { skip("$why; depth filtrata senza posa esatta"); continue }
            frames.add(DepthFrame(d.seq, DepthUse.FILTERED_FALLBACK, d.timestampNs, cam, k, DepthRaw.decode(filtered, d.width, d.height), null, pose.item.seq, PoseMatch.FRAME))
        }
        return frames to uses
    }

    private fun depthIntrinsics(d: DepthKeyframe, texture: CameraIntrinsics?): CameraIntrinsics? = when {
        d.intrinsics != null && d.intrinsicsSource == IntrinsicsSource.TEXTURE_SCALED -> d.intrinsics
        texture != null -> texture.scaledTo(d.width, d.height)
        else -> null
    }
}

/**
 * Dai pixel della depth ai punti nel mondo: p_camera = ((u − cx)/fx·z, (cy − v)/fy·z, −z) e poi la posa (vedi
 * [ArCameraProjection]). `z` è la profondità lungo l'asse ottico. Pixel scartati e perché: depth 0 (nessun dato), fuori dal
 * range, confidenza bassa, salto di profondità con un vicino (pixel "volante" al bordo di un oggetto). Nessun valore inventato.
 */
object DepthToWorld {
    /** Un punto accettato: posizione nel mondo e peso. [u], [v] servono al raggio dello spazio libero. */
    class Point(val u: Int, val v: Int, val x: Double, val y: Double, val z: Double, val weight: Double, val rangeM: Double)

    fun convert(f: DepthFrame, p: ReconParams, sink: (Point) -> Unit): FrameUse {
        val w = f.k.width
        val h = f.k.height
        var zero = 0; var range = 0; var conf = 0; var edge = 0; var ok = 0
        val baseWeight = if (f.use == DepthUse.RAW) 1.0 else p.fallbackWeight
        for (v in 0 until h) for (u in 0 until w) {
            val mm = f.mm[v * w + u]
            if (mm <= 0) { zero++; continue }
            val z = mm / 1000.0
            if (z < p.minRangeM || z > p.maxRangeM) { range++; continue }
            var weight = baseWeight
            val c = f.confidence
            if (c != null) {
                val cv = c[v * w + u].toInt() and 0xFF
                if (cv < p.minRawConfidence) { conf++; continue }
                weight *= cv / 255.0
            }
            if (isEdge(f.mm, w, h, u, v, mm, p.edgeJumpRatio)) { edge++; continue }
            val world = ArCameraProjection.unproject(f.pose, f.k, u.toDouble(), v.toDouble(), z)
            val dx = world[0] - f.pose.x; val dy = world[1] - f.pose.y; val dz = world[2] - f.pose.z
            sink(Point(u, v, world[0], world[1], world[2], weight, sqrt(dx * dx + dy * dy + dz * dz)))
            ok++
        }
        return FrameUse(
            f.depthSeq, f.use, null, f.poseFrameSeq, f.poseMatch, f.timestampNs, w, h, w * h, ok, zero, range, conf, edge,
        )
    }

    private fun isEdge(mm: IntArray, w: Int, h: Int, u: Int, v: Int, d: Int, ratio: Double): Boolean {
        val limit = d * ratio
        fun jump(uu: Int, vv: Int): Boolean {
            if (uu < 0 || vv < 0 || uu >= w || vv >= h) return false
            val n = mm[vv * w + uu]
            return n > 0 && abs(n - d) > limit
        }
        return jump(u - 1, v) || jump(u + 1, v) || jump(u, v - 1) || jump(u, v + 1)
    }

    /** Direzione di vista (unitaria) della camera della posa: −Z della camera nel mondo. */
    fun viewDirection(pose: RecPose): DoubleArray {
        val f = pose.rotate(0.0, 0.0, -1.0)
        return doubleArrayOf(f.x, f.y, f.z)
    }
}
