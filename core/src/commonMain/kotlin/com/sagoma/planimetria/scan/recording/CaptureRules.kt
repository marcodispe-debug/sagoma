package com.sagoma.planimetria.scan.recording

import kotlin.math.abs

/**
 * Valori non finiti (NaN, ±∞) nei frame di ARCore. Il JSON non li può rappresentare: prima la serializzazione falliva e il frame
 * intero spariva (con la depth attiva la nuvola di punti di ARCore li contiene). Regola deterministica e conservativa:
 * - un punto con una coordinata o la confidenza non finita si scarta; gli altri restano, nell'ordine, con il loro id;
 * - un piano con un valore non finito (posa, normale, estensioni, poligono) si scarta;
 * - una posa della camera non finita si toglie ([RecordedFrame.invalidCamera]); una quota del pavimento non finita diventa null.
 * Quanti punti e piani sono stati scartati resta nel frame ([RecordedFrame.invalidPoints], [RecordedFrame.invalidPlanes]).
 */
object RecordingSanitizer {
    fun frame(f: RecordedFrame): RecordedFrame {
        val planes = f.planes.filter { validPlane(it) }
        val cloud = f.points?.let { cloud(it) }
        val camera = f.camera?.takeIf { valid(it) }
        val display = f.cameraDisplay?.takeIf { valid(it) }
        val invalidCamera = (f.camera != null && camera == null) || (f.cameraDisplay != null && display == null)
        val invalidPoints = cloud?.second ?: 0
        val invalidPlanes = f.planes.size - planes.size
        if (invalidPoints == 0 && invalidPlanes == 0 && !invalidCamera && f.floorY?.isFinite() != false) return f
        return f.copy(
            camera = camera, cameraDisplay = display, floorY = f.floorY?.takeIf { it.isFinite() }, planes = planes, points = cloud?.first,
            invalidPoints = f.invalidPoints + invalidPoints, invalidPlanes = f.invalidPlanes + invalidPlanes, invalidCamera = f.invalidCamera || invalidCamera,
        )
    }

    /** La nuvola senza i punti non finiti, e quanti ne sono stati tolti. */
    fun cloud(c: RecordedPointCloud): Pair<RecordedPointCloud, Int> {
        val n = c.ids.size
        val keep = (0 until n).filter { i ->
            c.xyz[3 * i].isFinite() && c.xyz[3 * i + 1].isFinite() && c.xyz[3 * i + 2].isFinite() && c.confidence[i].isFinite()
        }
        if (keep.size == n) return c to 0
        return RecordedPointCloud(
            c.timestampNs,
            keep.flatMap { listOf(c.xyz[3 * it], c.xyz[3 * it + 1], c.xyz[3 * it + 2]) },
            keep.map { c.confidence[it] },
            keep.map { c.ids[it] },
        ) to n - keep.size
    }

    fun valid(p: RecPose) = p.x.isFinite() && p.y.isFinite() && p.z.isFinite() && p.qx.isFinite() && p.qy.isFinite() && p.qz.isFinite() && p.qw.isFinite()

    fun validPlane(p: RecordedPlane) =
        valid(p.pose) && p.normal.all { it.isFinite() } && p.extentX.isFinite() && p.extentZ.isFinite() && p.polygon.all { it.isFinite() }
}

/** Intrinseche della depth di ARCore. */
object DepthIntrinsics {
    /** Tolleranza sul rapporto d'aspetto tra texture e depth. */
    const val ASPECT_TOLERANCE = 0.01

    /**
     * Le intrinseche della texture GPU (`Camera.getTextureIntrinsics`) riportate alla risoluzione della depth: la depth ha il campo
     * visivo della texture. Se i rapporti d'aspetto differiscono più di [ASPECT_TOLERANCE], la fonte lo dice
     * ([IntrinsicsSource.TEXTURE_SCALED_ASPECT_MISMATCH]).
     */
    fun fromTexture(texture: CameraIntrinsics, depthWidth: Int, depthHeight: Int): Pair<CameraIntrinsics, String> {
        val textureAspect = texture.width.toDouble() / texture.height
        val depthAspect = depthWidth.toDouble() / depthHeight
        val source = if (abs(textureAspect / depthAspect - 1) <= ASPECT_TOLERANCE) IntrinsicsSource.TEXTURE_SCALED else IntrinsicsSource.TEXTURE_SCALED_ASPECT_MISMATCH
        return texture.scaledTo(depthWidth, depthHeight) to source
    }
}

/** A quale frame ARCore appartiene un'immagine (RGB o depth), dato il suo timestamp. */
object FrameAssignment {
    /** Distanza massima tra il timestamp dell'immagine e quello del suo frame (l'offset normale è di pochi ms). */
    const val MAX_IMAGE_TO_FRAME_NS = 50_000_000L

    /**
     * L'indice del frame a cui appartiene l'immagine con timestamp [imageTs]: il primo frame con timestamp ≥ [imageTs], purché il
     * precedente sia < [imageTs] (l'immagine è arrivata tra i due) e la distanza non superi [MAX_IMAGE_TO_FRAME_NS]. [frameTimes]
     * in ordine crescente. Null se non si può dire.
     */
    fun frameFor(imageTs: Long, frameTimes: LongArray): Int? {
        if (frameTimes.isEmpty()) return null
        var lo = 0
        var hi = frameTimes.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (frameTimes[mid] < imageTs) lo = mid + 1 else hi = mid
        }
        if (lo == frameTimes.size) return null
        if (frameTimes[lo] - imageTs > MAX_IMAGE_TO_FRAME_NS) return null
        if (lo > 0 && frameTimes[lo - 1] >= imageTs) return null
        return lo
    }

    /** Esito di [match]: indice del frame (null se nessuno), tipo ([PoseMatch]) e motivo se non trovato. */
    data class Match(val index: Int?, val kind: String, val detail: String? = null)

    /**
     * Il frame di un'immagine: [PoseMatch.FRAME] con la regola di [frameFor]; altrimenti la posa più vicina se dista al massimo
     * [MAX_IMAGE_TO_FRAME_NS] ([PoseMatch.TIMESTAMP]); altrimenti [PoseMatch.UNAVAILABLE] con il motivo. Mai una posa più lontana.
     */
    fun match(imageTs: Long, frameTimes: LongArray): Match {
        frameFor(imageTs, frameTimes)?.let { return Match(it, PoseMatch.FRAME) }
        if (frameTimes.isEmpty()) return Match(null, PoseMatch.UNAVAILABLE, "nessuna posa disponibile")
        var best = 0
        for (i in frameTimes.indices) if (abs(frameTimes[i] - imageTs) < abs(frameTimes[best] - imageTs)) best = i
        val delta = abs(frameTimes[best] - imageTs)
        if (delta <= MAX_IMAGE_TO_FRAME_NS) return Match(best, PoseMatch.TIMESTAMP)
        val where = when {
            imageTs < frameTimes.first() -> "più vecchia della prima posa disponibile (${(frameTimes.first() - imageTs) / 1_000_000} ms)"
            imageTs > frameTimes.last() -> "più recente dell'ultima posa disponibile (${(imageTs - frameTimes.last()) / 1_000_000} ms)"
            else -> "posa più vicina a ${delta / 1_000_000} ms"
        }
        return Match(null, PoseMatch.UNAVAILABLE, "nessuna posa entro ${MAX_IMAGE_TO_FRAME_NS / 1_000_000} ms: $where")
    }
}

/** Depth raw ripetute: ARCore può ridare la stessa raw in più keyframe depth. */
object RawDepthDedup {
    /** Stessa raw = stesso timestamp e stesse dimensioni. */
    fun sameSample(ts: Long, width: Int, height: Int, prevTs: Long?, prevWidth: Int?, prevHeight: Int?) =
        prevTs != null && ts == prevTs && width == prevWidth && height == prevHeight
}

/** Una riga da scrivere, oppure il campione perso se non si è potuta serializzare. */
data class LineResult(val line: String?, val missing: MissingSample?)

/** Serializzazione protetta: un frame che non si può scrivere diventa un `missing` esplicito con la causa, mai un buco silenzioso. */
object CaptureLines {
    /** Pulisce il frame ([RecordingSanitizer]) e lo serializza; se fallisce lo stesso, `missing` "write-failed" con la causa. */
    fun frame(f: RecordedFrame, monotonicNs: Long = 0): LineResult = try {
        LineResult(ScanRecordingJson.frameLine(RecordingSanitizer.frame(f)), null)
    } catch (e: Exception) {
        LineResult(null, writeFailed(CaptureStream.FRAME, f.timestampNs, monotonicNs, e))
    }

    fun writeFailed(kind: String, timestampNs: Long?, monotonicNs: Long, e: Throwable) = MissingSample(
        kind = kind, reason = "write-failed", timestampNs = timestampNs, monotonicNs = monotonicNs,
        detail = (e::class.simpleName ?: "Exception") + (e.message?.let { ": " + it.take(200) } ?: ""),
    )
}
