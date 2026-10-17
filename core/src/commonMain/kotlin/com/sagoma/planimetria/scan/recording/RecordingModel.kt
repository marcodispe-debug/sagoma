package com.sagoma.planimetria.scan.recording

import kotlinx.serialization.Serializable

/**
 * Registrazione di una sessione di scansione ARCore, per riprodurla sul computer senza il telefono. Solo dati: nessun tipo
 * di Android o di ARCore. Formato JSONL (una riga JSON per riga): intestazione, frame, riga finale.
 *
 * Le coordinate NON sono convertite: tutto è come lo dà ARCore (vedi [RecordingFormat.COORDINATES]). La conversione nella
 * pianta di Sagoma avviene solo dopo, in [com.sagoma.planimetria.scan.ScanCoordinates].
 */
object RecordingFormat {
    const val NAME = "sagoma-scan-recording"

    /**
     * Cresce quando il formato cambia. I file con una versione più nuova non si leggono; le più vecchie sì.
     * 1 = M0 (solo frame: pose, piani, punti). 2 = M0.2 (anche righe `pose`, `rgb`, `depth`, `missing`, `stats` e, nella
     * cartella della registrazione, le immagini: vedi [CaptureDataset]); i campi nuovi dell'intestazione sono tutti facoltativi.
     */
    const val VERSION = 2

    const val COORDINATES: String =
        "Mondo ARCore: metri, destrorso, y verso l'ALTO (verticale), origine e direzione orizzontale arbitrarie (dipendono da come è " +
            "partita la sessione). Pose: traslazione (x, y, z) nel mondo e quaternione di rotazione (qx, qy, qz, qw) locale→mondo. " +
            "Piano: il poligono è in coordinate LOCALI del piano ([x0, z0, x1, z1, ...], y = 0; metri) e si porta nel mondo con la " +
            "posa del piano (centerPose); la normale (asse Y locale) è già data anche nel mondo. Punti: posizioni nel mondo. " +
            "Camera: `camera` è la posa della camera fisica, `cameraDisplay` quella orientata come lo schermo. Valori numerici " +
            "arrotondati a 5 decimali (0,01 mm). Nessuna coordinata di Sagoma (cm, y verso il basso) in questo file."
}

@Serializable
data class DeviceInfo(
    val manufacturer: String? = null,
    val model: String? = null,
    val androidSdk: Int? = null,
)

@Serializable
data class ArCoreInfo(
    /** Versione della libreria ARCore con cui è compilata l'app. */
    val sdkVersion: String? = null,
    /** Versione dell'app "Servizi Google Play per la RA" installata sul telefono. */
    val servicesVersion: String? = null,
)

@Serializable
data class ScreenInfo(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    /** Rotazione dello schermo in `Surface.ROTATION_*` (0, 1, 2, 3). */
    val displayRotation: Int = 0,
    val densityDpi: Int? = null,
    /** Dimensione dell'immagine della fotocamera usata da ARCore, se nota. */
    val cameraImageWidth: Int? = null,
    val cameraImageHeight: Int? = null,
)

@Serializable
data class SessionInfo(
    val planeFindingMode: String? = null,
    val updateMode: String? = null,
    val focusMode: String? = null,
    val lightEstimationMode: String? = null,
    /** Modalità profondità configurata (per ora "DISABLED": la profondità non è usata). */
    val depthMode: String? = null,
    /** Il telefono supporta la profondità automatica? null = non verificato. */
    val depthSupported: Boolean? = null,
)

@Serializable
data class RecordingHeader(
    val type: String = TYPE,
    val format: String = RecordingFormat.NAME,
    val version: Int = RecordingFormat.VERSION,
    val createdAtMillis: Long = 0,
    /** Ogni quanto (ms) al massimo si registra un frame. */
    val recordIntervalMs: Long = 0,
    val device: DeviceInfo = DeviceInfo(),
    val arcore: ArCoreInfo = ArCoreInfo(),
    val screen: ScreenInfo = ScreenInfo(),
    val session: SessionInfo = SessionInfo(),
    val coordinates: String = RecordingFormat.COORDINATES,
    /** M0.2: modalità e frequenze di acquisizione (null nelle registrazioni M0). */
    val capture: CaptureSettings? = null,
    /** M0.2: configurazione della fotocamera in uso (risoluzione dell'immagine CPU e della texture, fps, sensore di profondità). */
    val cameraConfig: CameraConfigInfo? = null,
    /** M0.2: tutte le configurazioni che ARCore offre su questo telefono (fotocamera posteriore). */
    val availableCameraConfigs: List<CameraConfigInfo> = emptyList(),
    /** M0.2: modello della fotocamera (intrinseche, orientamento del sensore, convenzioni di proiezione). */
    val camera: CameraModelInfo? = null,
) {
    companion object { const val TYPE = "header" }
}

/** Posa nel mondo ARCore: traslazione in metri e quaternione (x, y, z, w) locale→mondo. */
@Serializable
data class RecPose(
    val x: Double,
    val y: Double,
    val z: Double,
    val qx: Double = 0.0,
    val qy: Double = 0.0,
    val qz: Double = 0.0,
    val qw: Double = 1.0,
)

/**
 * Un piano di ARCore in un frame. `kind`: "VERTICAL", "HORIZONTAL_UPWARD_FACING", "HORIZONTAL_DOWNWARD_FACING" (come
 * `Plane.Type`). `tracking`: "TRACKING", "PAUSED", "STOPPED". `key` identifica lo stesso piano tra un frame e l'altro (la
 * assegna chi registra); `subsumedBy` è la chiave del piano che lo ha assorbito, se c'è.
 */
@Serializable
data class RecordedPlane(
    val key: Int,
    val kind: String,
    val tracking: String,
    val subsumedBy: Int? = null,
    val pose: RecPose,
    /** Normale del piano nel mondo (asse Y locale della posa), come la dà ARCore. */
    val normal: List<Double> = emptyList(),
    val extentX: Double = 0.0,
    val extentZ: Double = 0.0,
    /** Poligono nel sistema LOCALE del piano: [x0, z0, x1, z1, ...] in metri. */
    val polygon: List<Double> = emptyList(),
)

/**
 * Nuvola di punti di ARCore: `xyz` ([x0, y0, z0, x1, ...], nel mondo), `confidence` (0..1) e `ids` (identificatori persistenti)
 * hanno lo stesso numero di punti. `timestampNs` è quello della nuvola (cambia quando ARCore la aggiorna).
 */
@Serializable
data class RecordedPointCloud(
    val timestampNs: Long,
    val xyz: List<Double> = emptyList(),
    val confidence: List<Double> = emptyList(),
    val ids: List<Int> = emptyList(),
) {
    val count: Int get() = ids.size
    val isConsistent: Boolean get() = xyz.size == ids.size * 3 && confidence.size == ids.size
}

@Serializable
data class RecordedFrame(
    val type: String = TYPE,
    val index: Int,
    /** Timestamp del frame di ARCore (nanosecondi). */
    val timestampNs: Long,
    /** Millisecondi dall'inizio della registrazione. */
    val elapsedMs: Long = 0,
    /** `Camera.trackingState`: "TRACKING", "PAUSED", "STOPPED". */
    val tracking: String,
    /** `Camera.trackingFailureReason` (null se NONE o sconosciuto). */
    val failureReason: String? = null,
    val camera: RecPose? = null,
    val cameraDisplay: RecPose? = null,
    /** Quota del pavimento (mondo, metri) come la stimava l'app in quel momento: minimo dei piani orizzontali rivolti in su. */
    val floorY: Double? = null,
    /** La profondità era in uso in questo frame? (Per ora mai: configurazione `DISABLED`.) */
    val depthInUse: Boolean = false,
    val planes: List<RecordedPlane> = emptyList(),
    /** Presente solo nei frame in cui la nuvola di punti è cambiata. */
    val points: RecordedPointCloud? = null,
    /** M0.2: numero progressivo del frame ARCore ([PoseSample.seq]) da cui viene questo frame. */
    val frameSeq: Int? = null,
    /**
     * M0.2: punti della nuvola scartati perché avevano coordinate o confidenza non finite (NaN, ±∞): il JSON non le può
     * rappresentare. Tutti gli altri punti restano. Vedi [RecordingSanitizer].
     */
    val invalidPoints: Int = 0,
    /** M0.2: piani scartati per valori non finiti nella posa, nella normale, nelle estensioni o nel poligono. */
    val invalidPlanes: Int = 0,
    /** M0.2: la posa della camera (o quella orientata come lo schermo) aveva valori non finiti ed è stata tolta. */
    val invalidCamera: Boolean = false,
) {
    companion object { const val TYPE = "frame" }
}

@Serializable
data class RecordingEnd(
    val type: String = TYPE,
    val frames: Int = 0,
    val durationMs: Long = 0,
    val verticalPlanesObserved: Int = 0,
    val pointSamples: Int = 0,
    /** M0.2: righe `pose` (frame ARCore distinti visti), keyframe RGB e depth scritti, campioni persi (null in M0). */
    val poses: Int? = null,
    val rgb: Int? = null,
    val depth: Int? = null,
    val droppedRgb: Int? = null,
    val droppedDepth: Int? = null,
    /** M0.2: la coda di scrittura è stata svuotata del tutto prima di chiudere (false = chiusa a forza, dati in coda persi). */
    val drained: Boolean? = null,
    /** M0.2: conteggi per stream ("frame", "rgb", "depth", "rawDepth", "confidence"): vedi [StreamCounts]. */
    val streams: Map<String, StreamCounts>? = null,
) {
    companion object { const val TYPE = "end" }
}

/**
 * Registrazione letta per intero. `end` manca se l'app è stata interrotta prima di chiuderla. Le liste M0.2 sono vuote nelle
 * registrazioni M0. Ogni lista è nell'ordine del file, che è l'ordine di acquisizione.
 */
data class ScanRecording(
    val header: RecordingHeader,
    val frames: List<RecordedFrame>,
    val end: RecordingEnd? = null,
    val poses: List<PoseSample> = emptyList(),
    val rgb: List<RgbKeyframe> = emptyList(),
    val depth: List<DepthKeyframe> = emptyList(),
    val missing: List<MissingSample> = emptyList(),
    val stats: List<CaptureStats> = emptyList(),
) {
    /** La registrazione non è stata chiusa (app interrotta, batteria, crash): l'ultima parte può mancare. */
    val truncated: Boolean get() = end == null
}
