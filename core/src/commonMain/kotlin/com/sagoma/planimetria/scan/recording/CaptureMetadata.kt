package com.sagoma.planimetria.scan.recording

import kotlinx.serialization.Serializable

/** `metadata/camera.json`: tutto ciò che riguarda la fotocamera, estratto dall'intestazione (che resta la fonte completa). */
@Serializable
data class CameraMetadata(
    val format: String = RecordingFormat.NAME,
    val version: Int = RecordingFormat.VERSION,
    val device: DeviceInfo = DeviceInfo(),
    val arcore: ArCoreInfo = ArCoreInfo(),
    val session: SessionInfo = SessionInfo(),
    val capture: CaptureSettings? = null,
    val cameraConfig: CameraConfigInfo? = null,
    val availableCameraConfigs: List<CameraConfigInfo> = emptyList(),
    val camera: CameraModelInfo? = null,
    val displayRotation: Int = 0,
) {
    companion object {
        fun of(h: RecordingHeader) = CameraMetadata(
            device = h.device, arcore = h.arcore, session = h.session, capture = h.capture, cameraConfig = h.cameraConfig,
            availableCameraConfigs = h.availableCameraConfigs, camera = h.camera, displayRotation = h.screen.displayRotation,
        )
    }
}

/** `metadata/README.json`: come è fatta la cartella e come si usano i dati, per chi la apre senza il codice di Sagoma. */
@Serializable
data class DatasetReadme(
    val format: String = RecordingFormat.NAME,
    val version: Int = RecordingFormat.VERSION,
    val mode: String? = null,
    val layout: Map<String, String> = LAYOUT,
    val lineTypes: Map<String, String> = LINE_TYPES,
    val coordinates: String = RecordingFormat.COORDINATES,
    val projection: String = ArCameraProjection.CONVENTION,
    val depthFormat: String = DEPTH,
    val synchronization: String = SYNC,
) {
    companion object {
        fun of(h: RecordingHeader) = DatasetReadme(mode = h.capture?.mode)

        val LAYOUT = mapOf(
            CaptureDataset.RECORDING to "Flusso temporale JSONL: intestazione, poi righe nell'ordine di acquisizione, poi la riga 'end' (manca se interrotta).",
            "${CaptureDataset.RGB_DIR}/NNNNNN.jpg" to "Keyframe RGB: JPEG dell'immagine CPU di ARCore, orientamento del sensore, nessun ridimensionamento.",
            "${CaptureDataset.DEPTH_DIR}/NNNNNN.d16" to "Depth ARCore (acquireDepthImage16Bits): uint16 LE, mm, 0 = nessun dato.",
            "${CaptureDataset.DEPTH_DIR}/NNNNNN.raw.d16" to "Depth raw ARCore (acquireRawDepthImage16Bits), stesso formato; facoltativa.",
            "${CaptureDataset.DEPTH_DIR}/NNNNNN.conf.u8" to "Confidenza della depth raw, 1 byte per pixel (0..255); facoltativa.",
            CaptureDataset.CAMERA to "Configurazione della fotocamera, configurazioni disponibili, intrinseche iniziali.",
            CaptureDataset.README to "Questo file.",
        )

        val LINE_TYPES = mapOf(
            RecordingHeader.TYPE to "Intestazione: dispositivo, ARCore, sessione, modalità e frequenze, fotocamera.",
            RecordedFrame.TYPE to "Snapshot M0: posa, piani e nuvola di punti di ARCore (a frequenza ridotta).",
            PoseSample.TYPE to "Posa di OGNI frame ARCore (seq progressivo, timestamp ARCore).",
            RgbKeyframe.TYPE to "Keyframe RGB: file, posa dello stesso frame, intrinseche, risoluzione, depth più vicina.",
            DepthKeyframe.TYPE to "Keyframe depth: file, timestamp della depth, posa (esatta se poseExact), intrinseche, RGB più vicino.",
            MissingSample.TYPE to "Campioni persi: tipo, motivo (not-yet-available, backlog, error, limit), quanti.",
            CaptureStats.TYPE to "Stato della pipeline circa ogni secondo: frame ARCore, tempi di update, coda di scrittura.",
            RecordingEnd.TYPE to "Fine: conteggi e se la coda è stata scritta tutta (drained).",
        )

        const val DEPTH = "${DepthRaw.FORMAT}: width × height uint16 little-endian, millimetri, riga per riga senza padding; 0 = nessun dato. " +
            "Nessun riempimento né interpolazione. Confidenza: ${DepthRaw.CONFIDENCE_FORMAT}, 1 byte per pixel."

        const val SYNC = "Sincronizzazione per frame: ogni rgb/depth indica il frame ARCore a cui appartiene (rgb: frameSeq; depth: poseFrameSeq) " +
            "e porta la posa di quel frame; le righe 'pose' danno la posa di ogni frame. Il timestamp delle immagini precede di pochi ms " +
            "quello del frame (imageToFrameNs): è normale. Per abbinare RGB e depth si usa il timestamp più vicino (delta registrato)."
    }
}
