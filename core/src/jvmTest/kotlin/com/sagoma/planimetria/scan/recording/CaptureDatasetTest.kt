package com.sagoma.planimetria.scan.recording

import com.sagoma.planimetria.scan.recording.analysis.CaptureReport
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalyzer
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M0.2: formato multimodale (pose, RGB, depth, persi, statistiche), sincronizzazione, validazione, report e lettura da ZIP. */
class CaptureDatasetTest {
    private val k = CameraIntrinsics(fx = 500.0, fy = 500.0, cx = 320.0, cy = 240.0, width = 640, height = 480)

    private fun header(mode: String = CaptureMode.ENVIRONMENT, rgbMs: Long = 200, depthMs: Long = 500) = RecordingHeader(
        createdAtMillis = 1_700_000_000_000,
        recordIntervalMs = 100,
        device = DeviceInfo("motorola", "motorola edge 70 fusion", 36),
        arcore = ArCoreInfo("1.44.0", "1.56"),
        screen = ScreenInfo(1272, 2560, 0, 450, 640, 480),
        session = SessionInfo("HORIZONTAL_AND_VERTICAL", "LATEST_CAMERA_IMAGE", "AUTO", "DISABLED", "AUTOMATIC", depthSupported = true),
        capture = CaptureSettings(mode, frameIntervalMs = 100, rgbIntervalMs = rgbMs, depthIntervalMs = depthMs, jpegQuality = 85, maxQueueBytes = 1 shl 20),
        cameraConfig = CameraConfigInfo("0", "BACK", 640, 480, 1920, 1080, 30, 30, "DO_NOT_USE", "DO_NOT_USE"),
        availableCameraConfigs = listOf(
            CameraConfigInfo("0", "BACK", 640, 480, 1920, 1080, 30, 30, "DO_NOT_USE", "DO_NOT_USE"),
            CameraConfigInfo("0", "BACK", 1920, 1080, 1920, 1080, 30, 30, "DO_NOT_USE", "DO_NOT_USE"),
        ),
        camera = CameraModelInfo(sensorOrientationDeg = 90, imageIntrinsics = k),
    )

    /** La camera si sposta di 1 cm a frame lungo x e guarda verso −z (posa identità ruotata solo in posizione). */
    private fun poseAt(seq: Int) = RecPose(seq * 0.01, 1.5, 0.0)

    private fun ts(seq: Int) = 1_000_000_000L + seq * 33_333_333L

    /**
     * Registrazione sintetica di [n] frame ARCore a 30 Hz: pose per ogni frame, RGB ogni 6 frame (5 Hz), depth ogni 15 (2 Hz) con
     * il timestamp di 2 frame prima (come ARCore, che la calcola in ritardo).
     */
    private fun synthetic(n: Int = 90, mode: String = CaptureMode.ENVIRONMENT, end: Boolean = true): ScanRecording {
        val poses = (0 until n).map { PoseSample(seq = it, timestampNs = ts(it), monotonicNs = ts(it) + 5, tracking = "TRACKING", camera = poseAt(it)) }
        val rgb = (0 until n step 6).mapIndexed { i, s ->
            RgbKeyframe(
                seq = i, frameSeq = s, timestampNs = ts(s), imageTimestampNs = ts(s), deltaPrevNs = if (i == 0) null else ts(s) - ts(s - 6),
                tracking = "TRACKING", camera = poseAt(s), intrinsics = k, width = 640, height = 480, path = CaptureDataset.rgbPath(i),
            )
        }
        val depth = (15 until n step 15).mapIndexed { i, s ->
            DepthKeyframe(
                seq = i, frameSeq = s, timestampNs = ts(s - 2), frameTimestampNs = ts(s), camera = poseAt(s - 2), poseTimestampNs = ts(s - 2),
                poseExact = true, width = 160, height = 120, path = CaptureDataset.depthPath(i), intrinsics = k.scaledTo(160, 120),
            )
        }
        val frames = (0 until n step 3).mapIndexed { i, s ->
            RecordedFrame(index = i, timestampNs = ts(s), elapsedMs = s * 33L, tracking = "TRACKING", camera = poseAt(s), frameSeq = s)
        }
        return ScanRecording(
            header(mode), frames,
            if (end) RecordingEnd(frames = frames.size, durationMs = n * 33L, poses = n, rgb = rgb.size, depth = depth.size, droppedRgb = 0, droppedDepth = 0, drained = true) else null,
            poses, rgb, depth,
            listOf(MissingSample(kind = "depth", reason = "not-yet-available", timestampNs = ts(1), count = 14)),
            listOf(CaptureStats(elapsedMs = 1000, drawCalls = 31, arFrames = 30, updateAvgMs = 1.2, updateMaxMs = 4.0, captureMaxMs = 2.5, maxQueueItems = 7, maxQueueBytes = 900_000)),
        )
    }

    private fun files(r: ScanRecording) = (r.rgb.map { it.path } + r.depth.map { it.path } + CaptureDataset.RECORDING).toSet()

    // ---------- Formato ----------

    @Test
    fun `nuovo formato - serializzazione e lettura senza perdite`() {
        val r = synthetic()
        val back = ScanRecordingJson.decode(ScanRecordingJson.encode(r))
        assertEquals(r, back)
        assertEquals(2, back.header.version)
        assertEquals(CaptureMode.ENVIRONMENT, back.header.capture?.mode)
        assertEquals(2, back.header.availableCameraConfigs.size)
    }

    @Test
    fun `retrocompatibilità - una registrazione M0 (versione 1) si legge e si analizza`() {
        val m0 = """
            {"type":"header","format":"sagoma-scan-recording","version":1,"createdAtMillis":1,"recordIntervalMs":100,"device":{"manufacturer":"motorola","model":"x","androidSdk":36},"arcore":{"sdkVersion":"1.44.0","servicesVersion":"1.56"},"screen":{"widthPx":1272,"heightPx":2560,"displayRotation":0,"densityDpi":450,"cameraImageWidth":640,"cameraImageHeight":480},"session":{"planeFindingMode":"HORIZONTAL_AND_VERTICAL","updateMode":"LATEST_CAMERA_IMAGE","focusMode":"AUTO","lightEstimationMode":"DISABLED","depthMode":"AUTOMATIC","depthSupported":true},"coordinates":"..."}
            {"type":"frame","index":0,"timestampNs":1000000000,"elapsedMs":12,"tracking":"TRACKING","failureReason":null,"camera":{"x":0.0,"y":0.0,"z":0.0,"qx":0.0,"qy":0.0,"qz":0.0,"qw":1.0},"cameraDisplay":null,"floorY":null,"depthInUse":true,"planes":[],"points":null}
            {"type":"frame","index":1,"timestampNs":1100000000,"elapsedMs":112,"tracking":"TRACKING","failureReason":null,"camera":{"x":0.1,"y":0.0,"z":0.0,"qx":0.0,"qy":0.0,"qz":0.0,"qw":1.0},"cameraDisplay":null,"floorY":null,"depthInUse":true,"planes":[],"points":null}
            {"type":"end","frames":2,"durationMs":200,"verticalPlanesObserved":0,"pointSamples":0}
        """.trimIndent()
        val r = ScanRecordingJson.decode(m0)
        assertEquals(1, r.header.version)
        assertNull(r.header.capture)
        assertEquals(2, r.frames.size)
        assertTrue(r.poses.isEmpty() && r.rgb.isEmpty() && r.depth.isEmpty())
        assertNull(r.frames[0].frameSeq)
        assertEquals(2, RecordingAnalyzer.analyze(r).frames)
        // Il report di acquisizione funziona anche senza righe M0.2: usa i frame.
        val t = CaptureReport.analyze(r)
        assertEquals(2, t.arFrames)
        assertEquals(0, t.rgb)
        assertTrue(t.issues.none { it.severity == "error" }, t.issues.toString())
        // L'indice temporale usa le pose dei frame.
        assertEquals(1, CaptureIndex(r).poseAt(1_090_000_000L)?.item?.seq)
    }

    @Test
    fun `una versione più nuova si rifiuta`() {
        val text = ScanRecordingJson.encode(synthetic(10)).replaceFirst("\"version\":2", "\"version\":3")
        assertFailsWith<RecordingFormatException> { ScanRecordingJson.decode(text) }
    }

    @Test
    fun `metadati - camera e README sono JSON leggibili con le convenzioni`() {
        val h = header(CaptureMode.OBJECT)
        val camera = ScanRecordingJson.cameraMetadata(h)
        assertTrue("\"availableCameraConfigs\"" in camera && "\"imageIntrinsics\"" in camera && "\"OBJECT\"" in camera)
        val readme = ScanRecordingJson.readme(h)
        assertTrue(CaptureDataset.RECORDING in readme && "rgb/NNNNNN.jpg" in readme && DepthRaw.FORMAT in readme)
        assertTrue("\"mode\": \"OBJECT\"" in readme)
    }

    @Test
    fun `percorsi dei file - numerati da 1, sei cifre`() {
        assertEquals("rgb/000001.jpg", CaptureDataset.rgbPath(0))
        assertEquals("depth/000042.d16", CaptureDataset.depthPath(41))
        assertEquals("depth/000042.raw.d16", CaptureDataset.rawDepthPath(41))
        assertEquals("depth/000042.conf.u8", CaptureDataset.confidencePath(41))
    }

    // ---------- Sincronizzazione ----------

    @Test
    fun `RGB - la posa è quella del proprio frame, con lo stesso timestamp`() {
        val r = synthetic()
        val index = CaptureIndex(r)
        for (x in r.rgb) {
            val m = assertNotNull(index.poseFor(x))
            assertTrue(m.exact)
            assertEquals(x.frameSeq, m.item.seq)
            assertEquals(x.camera, m.item.camera)
        }
        assertTrue(CaptureValidation.validate(r, files(r)).none { it.severity == "error" })
    }

    @Test
    fun `RGB associato a una posa sbagliata - la validazione lo segnala`() {
        val r = synthetic()
        val wrongPose = r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 3) x.copy(camera = poseAt(x.frameSeq + 1)) else x })
        assertTrue(CaptureValidation.validate(wrongPose).any { "posa diversa" in it.what })
        val wrongFrame = r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 3) x.copy(frameSeq = x.frameSeq + 1) else x })
        assertTrue(CaptureValidation.validate(wrongFrame).any { "ha un altro timestamp" in it.what })
        val noPose = r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 3) x.copy(timestampNs = x.timestampNs + 1000) else x })
        assertTrue(CaptureValidation.validate(noPose).any { "ha un altro timestamp" in it.what })
    }

    @Test
    fun `depth - posa del timestamp della depth, non del frame in cui è stata letta`() {
        val r = synthetic()
        val index = CaptureIndex(r)
        for (d in r.depth) {
            val m = assertNotNull(index.poseFor(d))
            assertTrue(m.exact)
            assertEquals(d.frameSeq - 2, m.item.seq)
            assertEquals(d.camera, m.item.camera)
        }
        val t = CaptureReport.analyze(r)
        assertEquals(r.depth.size, t.depthPoseExact)
        assertEquals(66.7, t.depthAgeMs.median, 0.1) // 2 frame a 30 Hz
    }

    @Test
    fun `RGB e depth - la più vicina per timestamp, con il delta`() {
        val r = synthetic()
        val index = CaptureIndex(r)
        val d0 = r.depth[0] // timestamp del frame 13
        val rgb = assertNotNull(index.rgbFor(d0))
        assertEquals(12, rgb.item.frameSeq) // RGB ai frame 12 e 18: il 12 è il più vicino al 13
        assertEquals(ts(12) - ts(13), rgb.deltaNs)
        val back = assertNotNull(index.depthFor(r.rgb[2])) // RGB al frame 12
        assertEquals(0, back.item.seq)
        assertEquals(ts(13) - ts(12), back.deltaNs)
    }

    @Test
    fun `proiezione - un punto davanti alla camera cade nel pixel giusto, e torna indietro con la depth`() {
        val pose = RecPose(1.0, 1.5, 2.0) // nessuna rotazione: la camera guarda verso −z
        val center = assertNotNull(ArCameraProjection.project(pose, k, 1.0, 1.5, 0.0))
        assertEquals(320.0, center.u, 1e-9)
        assertEquals(240.0, center.v, 1e-9)
        assertEquals(2.0, center.depthM, 1e-9)
        // 0,2 m a destra e 0,1 m in alto a 2 m: u = 500·0,2/2 + 320 = 370; v = 240 − 500·0,1/2 = 215.
        val p = assertNotNull(ArCameraProjection.project(pose, k, 1.2, 1.6, 0.0))
        assertEquals(370.0, p.u, 1e-9)
        assertEquals(215.0, p.v, 1e-9)
        val back = ArCameraProjection.unproject(pose, k, p.u, p.v, p.depthM)
        assertEquals(1.2, back[0], 1e-9); assertEquals(1.6, back[1], 1e-9); assertEquals(0.0, back[2], 1e-9)
        // Dietro la camera: nessuna proiezione.
        assertNull(ArCameraProjection.project(pose, k, 1.0, 1.5, 3.0))
        // Camera ruotata di 90° attorno a y (guarda verso −x): il punto a −x davanti finisce al centro.
        val turned = RecPose(0.0, 0.0, 0.0, 0.0, sqrt(0.5), 0.0, sqrt(0.5))
        val q = assertNotNull(ArCameraProjection.project(turned, k, -3.0, 0.0, 0.0))
        assertEquals(320.0, q.u, 1e-6)
        assertEquals(240.0, q.v, 1e-6)
        // Le intrinseche della depth: stesso campo visivo, scalate.
        val kd = k.scaledTo(160, 120)
        val pd = assertNotNull(ArCameraProjection.project(pose, kd, 1.2, 1.6, 0.0))
        assertEquals(370.0 / 4, pd.u, 1e-9)
        assertEquals(215.0 / 4, pd.v, 1e-9)
    }

    // ---------- Depth opzionale, frame persi, ordine, troncamento ----------

    @Test
    fun `depth assente - nessun errore, percentuale zero, i persi sono contati`() {
        val r = synthetic().let { it.copy(depth = emptyList(), rgb = it.rgb.map { x -> x.copy(lastDepthSeq = null, lastDepthDeltaNs = null) }) }
        val t = CaptureReport.analyze(r, files(r))
        assertEquals(0, t.depth)
        assertEquals(0.0, t.depthFramePct)
        assertEquals(listOf("depth/not-yet-available" to 14), t.missing)
        assertTrue(t.issues.none { it.severity == "error" })
        assertTrue(t.issues.any { "nessun keyframe depth" in it.what })
        assertNull(CaptureIndex(r).depthFor(r.rgb[0]))
        // Depth DISABLED: nessun avviso.
        val off = r.copy(header = r.header.copy(session = r.header.session.copy(depthMode = "DISABLED")))
        assertTrue(CaptureValidation.validate(off).none { "depth" in it.what.lowercase() })
    }

    @Test
    fun `frame persi - backlog e buchi nella traiettoria si vedono nel report`() {
        val r = synthetic()
        // Togliamo 20 frame ARCore di fila (buco di ~700 ms) e registriamo RGB scartati per coda piena.
        val gap = r.copy(
            poses = r.poses.filter { it.seq !in 40..59 }.mapIndexed { i, p -> p.copy(seq = i) },
            rgb = r.rgb.filter { it.frameSeq !in 40..59 },
            missing = r.missing + MissingSample(kind = "rgb", reason = "backlog", timestampNs = ts(42), count = 3),
        )
        val t = CaptureReport.analyze(gap)
        assertEquals(1, t.arGaps)
        assertEquals(700.0, t.arIntervalMs.max, 1.0)
        assertTrue(("rgb/backlog" to 3) in t.missing)
        assertTrue("rgb/backlog 3" in CaptureReport.text(t))
    }

    @Test
    fun `ordine temporale - timestamp all'indietro e numerazione non consecutiva sono errori`() {
        val r = synthetic()
        val swapped = r.copy(rgb = r.rgb.toMutableList().also { val a = it[2]; it[2] = it[3]; it[3] = a })
        assertTrue(CaptureValidation.validate(swapped).any { "Timestamp RGB non in ordine" in it.what })
        val holes = r.copy(poses = r.poses.filter { it.seq != 10 })
        assertTrue(CaptureValidation.validate(holes).any { "non consecutiva" in it.what })
        val depthFuture = r.copy(depth = r.depth.mapIndexed { i, d -> if (i == 0) d.copy(timestampNs = d.frameTimestampNs + 1) else d })
        assertTrue(CaptureValidation.validate(depthFuture).any { "dopo il frame" in it.what })
    }

    @Test
    fun `registrazione troncata - si legge fino all'ultima riga intera e i file mancanti si segnalano`() {
        val r = synthetic(end = false)
        val text = ScanRecordingJson.encode(r)
        val cut = text.substring(0, text.trimEnd().lastIndexOf('\n') + 30) // ultima riga a metà
        val back = ScanRecordingJson.decode(cut)
        assertTrue(back.truncated)
        assertNull(back.end)
        val t = CaptureReport.analyze(back, files(back) - back.rgb.last().path)
        assertTrue(t.truncated)
        assertTrue(t.issues.any { "troncata" in it.what })
        assertTrue(t.issues.any { "manca il file ${back.rgb.last().path}" in it.what })
        assertTrue("TRONCATA" in CaptureReport.text(t))
    }

    @Test
    fun `coda non svuotata alla chiusura - avviso`() {
        val r = synthetic().let { it.copy(end = it.end!!.copy(drained = false)) }
        assertTrue(CaptureValidation.validate(r).any { "non è stata svuotata" in it.what })
    }

    // ---------- Metadati e intrinseche ----------

    @Test
    fun `intrinseche e metadati non validi si segnalano`() {
        val r = synthetic()
        fun errors(x: ScanRecording) = CaptureValidation.validate(x).filter { it.severity == "error" }.map { it.what }
        assertTrue(errors(r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 0) x.copy(intrinsics = k.copy(fx = 0.0)) else x })).any { "fuoco" in it })
        assertTrue(errors(r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 0) x.copy(intrinsics = k.copy(cx = 900.0)) else x })).any { "punto principale" in it })
        assertTrue(errors(r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 0) x.copy(width = 1280) else x })).any { "intrinseche per 640×480" in it })
        assertTrue(errors(r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 0) x.copy(width = 1920, height = 1080, intrinsics = k.scaledTo(1920, 1080)) else x })).any { "diversa dalla configurazione" in it })
        assertTrue(errors(r.copy(depth = r.depth.mapIndexed { i, d -> if (i == 0) d.copy(intrinsics = k) else d })).any { "intrinseche per 640×480, depth 160×120" in it })
        assertTrue(errors(r.copy(header = r.header.copy(capture = r.header.capture!!.copy(mode = "BOH")))).any { "Modalità sconosciuta" in it })
        assertTrue(errors(r.copy(header = r.header.copy(capture = r.header.capture!!.copy(jpegQuality = 0)))).any { "JPEG" in it })
        assertTrue(errors(r.copy(rgb = r.rgb.mapIndexed { i, x -> if (i == 1) x.copy(path = r.rgb[0].path) else x })).any { "file ripetuto" in it })
        assertTrue(errors(r).isEmpty())
    }

    @Test
    fun `depth grezza - codifica little-endian senza perdite`() {
        val mm = intArrayOf(0, 1, 255, 256, 1234, 65535)
        val bytes = DepthRaw.encode(mm)
        assertEquals(listOf<Byte>(0, 0, 1, 0, -1, 0, 0, 1, (1234 and 0xFF).toByte(), (1234 shr 8).toByte(), -1, -1), bytes.toList())
        assertTrue(mm.contentEquals(DepthRaw.decode(bytes, 3, 2)))
        assertFailsWith<IllegalArgumentException> { DepthRaw.decode(bytes, 4, 2) }
    }

    // ---------- Modalità oggetto e report ----------

    @Test
    fun `dataset oggetto - immagini grandi a frequenza bassa, report con le frequenze reali`() {
        val big = CameraIntrinsics(1500.0, 1500.0, 960.0, 540.0, 1920, 1080) // pixel quadrati, come la depth
        val base = synthetic(150)
        val r = base.copy(
            header = header(CaptureMode.OBJECT, rgbMs = 500).copy(cameraConfig = CameraConfigInfo("0", "BACK", 1920, 1080, 1920, 1080, 30, 30)),
            rgb = (0 until 150 step 15).mapIndexed { i, s ->
                RgbKeyframe(seq = i, frameSeq = s, timestampNs = ts(s), imageTimestampNs = ts(s), tracking = "TRACKING", camera = poseAt(s), intrinsics = big, width = 1920, height = 1080, path = CaptureDataset.rgbPath(i), jpegQuality = 92)
            },
        )
        val t = CaptureReport.analyze(r, files(r))
        assertEquals(CaptureMode.OBJECT, t.mode)
        assertEquals(listOf("1920×1080"), t.rgbResolutions)
        assertEquals(2.0, t.rgbHz, 0.01)
        assertEquals(2.0, t.rgbTargetHz!!, 1e-9)
        assertEquals(30.0, t.arHz, 0.01)
        assertTrue(t.issues.none { it.severity == "error" }, t.issues.toString())
        assertEquals(r, ScanRecordingJson.decode(ScanRecordingJson.encode(r)))
    }

    @Test
    fun `controllo temporale - frequenze, percentuali e coda calcolate dai dati, non dichiarate`() {
        val r = synthetic(91) // 91 pose → 90 intervalli = 3 s a 30 Hz
        val t = CaptureReport.analyze(r, files(r))
        assertEquals(30.0, t.arHz, 0.01)
        assertEquals(5.0, t.rgbHz, 0.01)
        assertEquals(16, t.rgb)
        assertEquals(100.0 * 16 / 91, t.rgbFramePct, 1e-9)
        assertEquals(2.0, t.depthHz, 0.01)
        assertEquals(0, t.arGaps)
        assertEquals(7, t.maxQueueItems)
        assertEquals(4.0, t.updateMaxMs)
        assertEquals(r.rgb.size, t.rgbPoseExact)
        assertTrue(abs(t.durationMs - 3000) < 1)
        assertTrue("5.00 Hz (obiettivo 5.00 Hz)" in CaptureReport.text(t))
    }

    // ---------- Cartella e ZIP ----------

    @Test
    fun `lettura dal computer - cartella e ZIP esportato dal telefono`() {
        val r = synthetic(30)
        val tmp = Files.createTempDirectory("m02").toFile()
        try {
            val dir = File(tmp, "scan-20261007-120000").apply { mkdirs() }
            File(dir, CaptureDataset.RECORDING).writeText(ScanRecordingJson.encode(r))
            for (x in r.rgb) File(dir, x.path).apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
            for (d in r.depth) File(dir, d.path).apply { parentFile.mkdirs() }.writeBytes(DepthRaw.encode(IntArray(160 * 120) { 1500 }))
            val fromDir = CaptureDatasetFiles.open(dir)
            assertEquals(r, fromDir.recording)
            assertTrue(CaptureValidation.validate(fromDir.recording, fromDir.files).none { it.severity == "error" })

            val zip = File(tmp, "scan.zip")
            ZipOutputStream(zip.outputStream()).use { z ->
                for (f in dir.walkTopDown().filter { it.isFile }) {
                    z.putNextEntry(ZipEntry(dir.name + "/" + f.relativeTo(dir).invariantSeparatorsPath))
                    z.write(f.readBytes())
                    z.closeEntry()
                }
            }
            val fromZip = CaptureDatasetFiles.open(zip)
            assertEquals(r, fromZip.recording)
            assertEquals(fromDir.files, fromZip.files)
            val depth = DepthRaw.decode(assertNotNull(fromZip.read(r.depth[0].path)), 160, 120)
            assertEquals(1500, depth[0])
            assertFalse(CaptureValidation.validate(fromZip.recording, fromZip.files).any { "manca il file" in it.what })
        } finally {
            tmp.deleteRecursively()
        }
    }
}
