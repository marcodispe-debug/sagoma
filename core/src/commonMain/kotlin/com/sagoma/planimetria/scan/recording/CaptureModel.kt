package com.sagoma.planimetria.scan.recording

import kotlinx.serialization.Serializable

/**
 * M0.2 — registrazione multimodale: oltre ai frame di M0 (pose, piani, punti) si registrano la traiettoria di OGNI frame ARCore
 * ([PoseSample]), keyframe RGB ([RgbKeyframe], JPEG in `rgb/`) e depth grezza ([DepthKeyframe], in `depth/`), più i campioni
 * persi ([MissingSample]) e lo stato della pipeline ([CaptureStats]). Solo dati: nessun tipo di Android o di ARCore.
 *
 * Sincronizzazione: tutti i `timestampNs` sono timestamp ARCore (`Frame.getTimestamp`, nanosecondi, stesso orologio delle
 * immagini). `monotonicNs` è l'orologio monotono del telefono (`SystemClock.elapsedRealtimeNanos`) al momento dell'acquisizione:
 * serve a misurare i ritardi della pipeline, non a sincronizzare i dati.
 */
object CaptureMode {
    /** Ambiente (pareti, stanza): frequenza più alta, risoluzione della fotocamera quella scelta da ARCore. */
    const val ENVIRONMENT = "ENVIRONMENT"

    /** Oggetto (fotogrammetria di un mobile): immagine CPU alla risoluzione più alta offerta da ARCore, frequenza più bassa. */
    const val OBJECT = "OBJECT"
}

/** Come si acquisisce: intervalli minimi tra un campione e l'altro (frequenze obiettivo) e qualità delle immagini. */
@Serializable
data class CaptureSettings(
    val mode: String = CaptureMode.ENVIRONMENT,
    /** Righe `frame` (piani e punti di ARCore). */
    val frameIntervalMs: Long = 100,
    /** Keyframe RGB. */
    val rgbIntervalMs: Long = 200,
    /** Keyframe depth (opportunistici: se la depth non è pronta si salta, senza aspettare). */
    val depthIntervalMs: Long = 500,
    val jpegQuality: Int = 85,
    /** Le immagini RGB si salvano alla risoluzione dell'immagine CPU, senza ridimensionarle. */
    val rgbResized: Boolean = false,
    /** Memoria massima dei dati in coda di scrittura: oltre, si scartano keyframe (registrati come `missing`, motivo `backlog`). */
    val maxQueueBytes: Long = 0,
)

/** Una configurazione della fotocamera di ARCore (`CameraConfig`). Le dimensioni sono in pixel, nell'orientamento del sensore. */
@Serializable
data class CameraConfigInfo(
    val cameraId: String? = null,
    val facing: String? = null,
    /** Immagine CPU (`Frame.acquireCameraImage`): quella che si salva come RGB. */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    /** Texture GPU (quella mostrata a schermo). */
    val textureWidth: Int = 0,
    val textureHeight: Int = 0,
    val fpsMin: Int? = null,
    val fpsMax: Int? = null,
    val depthSensorUsage: String? = null,
    val stereoCameraUsage: String? = null,
) {
    val imagePixels: Long get() = imageWidth.toLong() * imageHeight
}

/**
 * Intrinseche pinhole di un'immagine, in pixel: fuoco (fx, fy) e punto principale (cx, cy) per un'immagine `width` × `height`.
 * Per la proiezione vedi [ArCameraProjection].
 */
@Serializable
data class CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val width: Int,
    val height: Int,
) {
    /** Le stesse intrinseche per un'immagine con lo stesso campo visivo ma un'altra risoluzione (per esempio la depth). */
    fun scaledTo(w: Int, h: Int): CameraIntrinsics {
        val sx = w.toDouble() / width
        val sy = h.toDouble() / height
        return CameraIntrinsics(fx * sx, fy * sy, cx * sx, cy * sy, w, h)
    }
}

/** Modello della fotocamera nell'intestazione: orientamento del sensore, intrinseche di partenza e convenzioni. */
@Serializable
data class CameraModelInfo(
    /** Rotazione del sensore rispetto al telefono in verticale (`SENSOR_ORIENTATION`, gradi), se nota. */
    val sensorOrientationDeg: Int? = null,
    /** Intrinseche dell'immagine CPU (RGB) all'avvio, alla sua risoluzione (ogni keyframe RGB ha comunque le sue). */
    val imageIntrinsics: CameraIntrinsics? = null,
    /** Intrinseche della texture GPU all'avvio, alla sua risoluzione: da queste, scalate, vengono quelle della depth. */
    val textureIntrinsics: CameraIntrinsics? = null,
    val convention: String = ArCameraProjection.CONVENTION,
)

/**
 * Posa di un frame ARCore. Se ne scrive una per OGNI frame nuovo (timestamp diverso dal precedente), così si può avere la posa
 * di qualsiasi immagine, anche della depth che arriva con un timestamp più vecchio. `seq` conta i frame ARCore dall'inizio della
 * registrazione (0, 1, 2…): un salto di `seq` non c'è mai, un salto di `timestampNs` grande indica frame che ARCore non ha dato.
 */
@Serializable
data class PoseSample(
    val type: String = TYPE,
    val seq: Int,
    val timestampNs: Long,
    val monotonicNs: Long = 0,
    val tracking: String,
    /** Posa della camera fisica (`Camera.getPose`): vedi [ArCameraProjection]. */
    val camera: RecPose? = null,
) {
    companion object { const val TYPE = "pose" }
}

/**
 * Keyframe RGB: un JPEG in [path] (relativo alla cartella della registrazione) con tutto ciò che serve per proiettarci sopra un
 * punto 3D: posa della camera nello stesso frame, intrinseche dell'immagine, risoluzione. L'immagine è esattamente l'immagine
 * CPU di ARCore (orientamento del sensore, nessuna rotazione né ridimensionamento: [transform] = "none").
 */
@Serializable
data class RgbKeyframe(
    val type: String = TYPE,
    val seq: Int,
    /** Il frame ARCore ([PoseSample.seq]) da cui è presa l'immagine. */
    val frameSeq: Int,
    /** Timestamp del frame ARCore. */
    val timestampNs: Long,
    /** Timestamp dell'immagine CPU (`Image.getTimestamp`): coincide con [timestampNs] su ARCore; se no, la posa va interpolata. */
    val imageTimestampNs: Long,
    val monotonicNs: Long = 0,
    /** Distanza dal keyframe RGB precedente (null per il primo). */
    val deltaPrevNs: Long? = null,
    val tracking: String,
    /** Posa della camera fisica nel frame dell'immagine. */
    val camera: RecPose,
    /** Posa orientata come lo schermo (solo informativa: per proiettare si usa [camera]). */
    val cameraDisplay: RecPose? = null,
    val intrinsics: CameraIntrinsics,
    val width: Int,
    val height: Int,
    val path: String,
    val format: String = "jpeg",
    /** Formato dell'immagine CPU prima della compressione. */
    val sourceFormat: String = "YUV_420_888",
    val jpegQuality: Int = 85,
    /** Trasformazione applicata ai pixel rispetto all'immagine CPU di ARCore: "none" (nessuna). */
    val transform: String = "none",
    /** Rotazione dello schermo (`Surface.ROTATION_*`) al momento dello scatto: serve solo a mostrare l'immagine dritta. */
    val displayRotation: Int = 0,
    val sensorOrientationDeg: Int? = null,
    /** Byte del JPEG scritto. */
    val bytes: Long? = null,
    /** Keyframe depth più recente al momento dello scatto e differenza di tempo (immagine − depth, ns). */
    val lastDepthSeq: Int? = null,
    val lastDepthDeltaNs: Long? = null,
    /** A quale immagine appartengono [intrinsics]: l'immagine CPU (`Camera.getImageIntrinsics`), alla risoluzione [width] × [height]. */
    val intrinsicsSource: String = IntrinsicsSource.CPU_IMAGE,
    /**
     * Come è stata associata la posa: [PoseMatch.FRAME] = l'immagine è del frame [frameSeq] (letta da quel frame) e [camera] è la
     * sua posa. Il timestamp dell'immagine precede di solito quello del frame di qualche ms: [imageToFrameNs] lo conserva.
     */
    val poseMatch: String = PoseMatch.FRAME,
    /** timestamp del frame − timestamp dell'immagine (ns): offset normale del sensore, solo diagnostico. */
    val imageToFrameNs: Long? = null,
) {
    companion object { const val TYPE = "rgb" }
}

/** Da quale immagine vengono le intrinseche di un keyframe. */
object IntrinsicsSource {
    /** `Camera.getImageIntrinsics()`: immagine CPU (quella salvata come RGB), alla sua risoluzione. */
    const val CPU_IMAGE = "cpu-image-intrinsics"

    /**
     * `Camera.getTextureIntrinsics()` riportate alla risoluzione della depth: la depth di ARCore ha il campo visivo della texture
     * GPU (stesso rapporto d'aspetto), non quello dell'immagine CPU, che può avere un altro rapporto (per esempio 4:3 contro 16:9).
     */
    const val TEXTURE_SCALED = "texture-intrinsics-scaled"

    /** Come [TEXTURE_SCALED], ma la depth ha un rapporto d'aspetto diverso dalla texture: le intrinseche vanno verificate. */
    const val TEXTURE_SCALED_ASPECT_MISMATCH = "texture-intrinsics-scaled-aspect-mismatch"

    /** Registrazioni M0.2 prima della correzione: intrinseche CPU scalate (SBAGLIATE se l'aspetto della depth è diverso). */
    const val LEGACY_IMAGE_SCALED = "image-intrinsics-scaled"
}

/** Come è stata associata una posa a un'immagine. */
object PoseMatch {
    /** L'immagine appartiene al frame ARCore indicato: la posa è quella di quel frame (esatta). */
    const val FRAME = "frame"

    /** Il frame non era più in memoria: posa del frame in cui l'immagine è stata letta (approssimata). */
    const val READ_FRAME = "read-frame"

    /**
     * Nessun frame secondo la regola di [FrameAssignment], ma una posa entro [FrameAssignment.MAX_IMAGE_TO_FRAME_NS] dal timestamp
     * dell'immagine: la posa più vicina (associazione temporale, non certa).
     */
    const val TIMESTAMP = "timestamp"

    /** Nessuna posa abbastanza vicina (per esempio frame già usciti dalla memoria del telefono): la posa non c'è, non si inventa. */
    const val UNAVAILABLE = "unavailable"
}

/**
 * Keyframe depth: la depth di ARCore (`acquireDepthImage16Bits`) così com'è, senza riempire i buchi né ridimensionare. File
 * [path]: [DepthRaw.FORMAT] (`width` × `height` valori uint16 little-endian, millimetri, riga per riga, senza padding; 0 = nessun
 * dato). Se disponibili, anche la depth "raw" (`acquireRawDepthImage16Bits`, non filtrata, [rawPath], con il suo timestamp e le sue
 * dimensioni) e la sua confidenza (`acquireRawDepthConfidenceImage`, [confidencePath], 1 byte per pixel, 0..255); [rawStatus] e
 * [confidenceStatus] dicono com'è andata ([StreamStatus]).
 *
 * Sincronizzazione per frame: la depth appartiene al frame ARCore [poseFrameSeq], quello la cui immagine ha generato la depth (il
 * primo frame con timestamp ≥ timestamp della depth e il precedente < ); [camera] è la posa di quel frame ([poseExact] = true,
 * [poseMatch] = [PoseMatch.FRAME]). Il timestamp della depth è quello dell'immagine, di solito pochi ms prima del frame: è normale.
 */
@Serializable
data class DepthKeyframe(
    val type: String = TYPE,
    val seq: Int,
    /** Il frame ARCore in cui la depth è stata letta. */
    val frameSeq: Int,
    val timestampNs: Long,
    val frameTimestampNs: Long,
    val monotonicNs: Long = 0,
    val deltaPrevNs: Long? = null,
    val camera: RecPose? = null,
    /** Timestamp del frame della posa [camera]. */
    val poseTimestampNs: Long? = null,
    val poseExact: Boolean = false,
    val width: Int,
    val height: Int,
    val format: String = DepthRaw.FORMAT,
    val path: String,
    val rawPath: String? = null,
    val confidencePath: String? = null,
    /** Intrinseche della depth, alla sua risoluzione; [intrinsicsSource] dice da dove vengono ([IntrinsicsSource]). */
    val intrinsics: CameraIntrinsics? = null,
    val intrinsicsSource: String = IntrinsicsSource.TEXTURE_SCALED,
    /** Keyframe RGB più recente e differenza di tempo (depth − RGB, ns). */
    val lastRgbSeq: Int? = null,
    val lastRgbDeltaNs: Long? = null,
    /** Il frame ARCore a cui appartiene la depth (null nelle registrazioni prima della correzione). */
    val poseFrameSeq: Int? = null,
    /** [PoseMatch]: come è stata associata [camera]. */
    val poseMatch: String? = null,
    /** Depth raw: timestamp e dimensioni propri (possono differire dalla depth filtrata). */
    val rawTimestampNs: Long? = null,
    val rawWidth: Int? = null,
    val rawHeight: Int? = null,
    /** [StreamStatus] della depth raw e della confidenza, con il motivo se non salvate. */
    val rawStatus: String? = null,
    val rawDetail: String? = null,
    val confidenceStatus: String? = null,
    val confidenceDetail: String? = null,
    /**
     * Posa della depth raw, dal SUO timestamp ([rawTimestampNs]), non da quello della depth filtrata (la raw di ARCore è spesso più
     * vecchia, anche di secondi). [rawPoseMatch]: [PoseMatch.FRAME], [PoseMatch.TIMESTAMP] o [PoseMatch.UNAVAILABLE] (con
     * [rawPoseDetail]); [rawPoseDeltaNs] = timestamp della posa − timestamp della raw. Null se la raw non c'è.
     */
    val rawPoseFrameSeq: Int? = null,
    val rawPoseMatch: String? = null,
    val rawPoseDeltaNs: Long? = null,
    val rawCamera: RecPose? = null,
    val rawPoseDetail: String? = null,
) {
    companion object { const val TYPE = "depth" }
}

/** Nomi degli stream nei conteggi ([StreamCounts]) e nei campioni persi ([MissingSample.kind]). */
object CaptureStream {
    const val FRAME = "frame"
    const val RGB = "rgb"
    const val DEPTH = "depth"
    const val RAW_DEPTH = "rawDepth"
    const val CONFIDENCE = "confidence"
    val ALL = listOf(FRAME, RGB, DEPTH, RAW_DEPTH, CONFIDENCE)
}

/**
 * Esito di un tentativo su uno stream. [ACQUIRED]: dato preso da ARCore e messo in coda; [SAVED]: scritto su disco con la sua riga;
 * [UNAVAILABLE]: ARCore non l'ha dato (non ancora pronto, o non supportato: motivo nel dettaglio); [FAILED]: errore di acquisizione,
 * di copia o di scrittura (motivo nel dettaglio); [MISSING]: acquisito o atteso ma non salvato (coda piena, limite di spazio).
 */
object StreamStatus {
    const val ACQUIRED = "acquired"
    const val SAVED = "saved"
    const val UNAVAILABLE = "unavailable"
    const val FAILED = "failed"
    const val MISSING = "missing"

    /** Acquisito ma uguale al campione già salvato (stesso timestamp e stesse dimensioni): non si salva di nuovo. */
    const val DUPLICATE = "duplicate"
}

/**
 * Conteggi di uno stream a fine registrazione. Vale sempre acquired = saved + failed (in scrittura) + missing (dopo l'acquisizione)
 * + duplicate.
 */
@Serializable
data class StreamCounts(
    val acquired: Int = 0,
    val saved: Int = 0,
    val unavailable: Int = 0,
    val failed: Int = 0,
    val missing: Int = 0,
    val duplicate: Int = 0,
)

/**
 * Un campione che si voleva registrare e non c'è. `kind`: uno di [CaptureStream] (o "points"). `reason`:
 * - "not-yet-available": ARCore non aveva ancora l'immagine (si riprova al frame dopo) → non disponibile;
 * - "unavailable": lo stream non è supportato o non c'è (dettaglio: l'eccezione di ARCore) → non disponibile;
 * - "not-new": ARCore ha ridato la stessa depth del keyframe precedente → non è un keyframe nuovo, nessuna perdita;
 * - "backlog": coda di scrittura piena, scartato per non bloccare l'acquisizione → perso;
 * - "limit": spazio massimo raggiunto → perso;
 * - "error": errore nell'acquisizione o nella copia (dettaglio: l'eccezione) → fallito;
 * - "write-failed": acquisito ma non scritto (dettaglio: l'eccezione) → fallito.
 * `count` raggruppa più campioni di fila con lo stesso motivo.
 */
@Serializable
data class MissingSample(
    val type: String = TYPE,
    val kind: String,
    val reason: String,
    val timestampNs: Long? = null,
    val monotonicNs: Long = 0,
    val count: Int = 1,
    val detail: String? = null,
) {
    companion object { const val TYPE = "missing" }
}

/**
 * Stato della pipeline circa una volta al secondo: quanto lavora il thread di disegno (dove gira ARCore) e quanto è piena la
 * coda di scrittura. Serve a dimostrare che la scrittura non rallenta l'acquisizione.
 */
@Serializable
data class CaptureStats(
    val type: String = TYPE,
    val monotonicNs: Long = 0,
    val elapsedMs: Long = 0,
    /** Chiamate di disegno e frame ARCore nuovi nell'intervallo. */
    val drawCalls: Int = 0,
    val arFrames: Int = 0,
    val intervalMs: Long = 0,
    /** Tempo di `Session.update()` (ms) nell'intervallo. */
    val updateAvgMs: Double = 0.0,
    val updateMaxMs: Double = 0.0,
    /** Tempo speso dal recorder sul thread di disegno (copie dei dati, nessuna scrittura) (ms). */
    val captureAvgMs: Double = 0.0,
    val captureMaxMs: Double = 0.0,
    /** Tempo massimo tra due chiamate di disegno (ms). */
    val drawGapMaxMs: Double = 0.0,
    val queueItems: Int = 0,
    val queueBytes: Long = 0,
    val maxQueueItems: Int = 0,
    val maxQueueBytes: Long = 0,
    val writtenRgb: Int = 0,
    val writtenDepth: Int = 0,
    val droppedRgb: Int = 0,
    val droppedDepth: Int = 0,
    val bytesWritten: Long = 0,
    /** Tempo del worker per keyframe RGB (conversione + JPEG + scrittura) (ms). */
    val rgbWriteAvgMs: Double = 0.0,
    val rgbWriteMaxMs: Double = 0.0,
) {
    companion object { const val TYPE = "stats" }
}

/** Disposizione della cartella (e dello ZIP) di una registrazione M0.2. */
object CaptureDataset {
    const val RECORDING = "recording.jsonl"
    const val RGB_DIR = "rgb"
    const val DEPTH_DIR = "depth"
    const val METADATA_DIR = "metadata"
    const val CAMERA = "metadata/camera.json"
    const val README = "metadata/README.json"

    fun rgbPath(seq: Int) = "$RGB_DIR/${pad(seq)}.jpg"
    fun depthPath(seq: Int) = "$DEPTH_DIR/${pad(seq)}.d16"
    fun rawDepthPath(seq: Int) = "$DEPTH_DIR/${pad(seq)}.raw.d16"
    fun confidencePath(seq: Int) = "$DEPTH_DIR/${pad(seq)}.conf.u8"

    private fun pad(seq: Int) = (seq + 1).toString().padStart(6, '0')
}

/** Depth grezza: uint16 little-endian, millimetri, 0 = nessun dato. */
object DepthRaw {
    const val FORMAT = "DEPTH16_MM_U16LE"
    const val CONFIDENCE_FORMAT = "U8_CONFIDENCE"

    fun encode(mm: IntArray): ByteArray {
        val out = ByteArray(mm.size * 2)
        for (i in mm.indices) {
            val v = mm[i].coerceIn(0, 0xFFFF)
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = (v ushr 8).toByte()
        }
        return out
    }

    fun decode(bytes: ByteArray, width: Int, height: Int): IntArray {
        require(bytes.size == width * height * 2) { "Depth di ${bytes.size} byte, attesi ${width * height * 2} (${width}×$height)" }
        return IntArray(width * height) { i -> (bytes[2 * i].toInt() and 0xFF) or ((bytes[2 * i + 1].toInt() and 0xFF) shl 8) }
    }
}

/** Pixel di un'immagine (u verso destra, v verso il basso, origine nell'angolo in alto a sinistra) e profondità davanti alla camera. */
data class PixelHit(val u: Double, val v: Double, val depthM: Double)

/**
 * Proiezione di un punto del mondo ARCore nell'immagine CPU (RGB) o nella depth, con le convenzioni di ARCore:
 * la posa della camera (`Camera.getPose`) porta il sistema camera nel mondo; nel sistema camera +X va a destra, +Y in alto,
 * −Z è la direzione in cui guarda la camera, "destra" e "alto" riferiti all'immagine del sensore letta da sinistra a destra e
 * dall'alto in basso. Quindi, con p = posa⁻¹ · punto: u = fx · p.x / (−p.z) + cx, v = cy − fy · p.y / (−p.z).
 */
object ArCameraProjection {
    const val CONVENTION =
        "Posa camera (Camera.getPose) camera→mondo; camera: +X destra, +Y alto, −Z avanti (immagine del sensore, senza rotazioni). " +
            "p = posa⁻¹·punto; u = fx·p.x/(−p.z) + cx; v = cy − fy·p.y/(−p.z); pixel (u destra, v in basso) dell'immagine CPU. " +
            "Ogni immagine con le SUE intrinseche: RGB = intrinseche dell'immagine CPU; depth = intrinseche della texture GPU scalate " +
            "alla risoluzione della depth (la depth ha il campo visivo della texture, che può avere un altro rapporto d'aspetto)."

    /** Il punto del mondo nell'immagine; null se è dietro la camera. Non controlla che cada dentro l'immagine. */
    fun project(pose: RecPose, k: CameraIntrinsics, x: Double, y: Double, z: Double): PixelHit? {
        val c = pose.inverseTransform(x, y, z)
        val depth = -c[2]
        if (depth <= 1e-9) return null
        return PixelHit(k.fx * c[0] / depth + k.cx, k.cy - k.fy * c[1] / depth, depth)
    }

    /** Il punto del mondo che si vede nel pixel (u, v) a [depthM] metri davanti alla camera (lungo −Z). */
    fun unproject(pose: RecPose, k: CameraIntrinsics, u: Double, v: Double, depthM: Double): DoubleArray {
        val cx = (u - k.cx) / k.fx * depthM
        val cy = (k.cy - v) / k.fy * depthM
        val p = pose.transformPoint(cx, cy, -depthM)
        return doubleArrayOf(p.x, p.y, p.z)
    }

    /** p = posa⁻¹ · punto (mondo → camera). */
    private fun RecPose.inverseTransform(x: Double, y: Double, z: Double): DoubleArray {
        val dx = x - this.x
        val dy = y - this.y
        val dz = z - this.z
        // Rotazione inversa = coniugato del quaternione.
        val r = RecPose(0.0, 0.0, 0.0, -qx, -qy, -qz, qw).rotate(dx, dy, dz)
        return doubleArrayOf(r.x, r.y, r.z)
    }
}
