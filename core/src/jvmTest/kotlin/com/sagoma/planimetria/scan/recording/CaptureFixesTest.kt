package com.sagoma.planimetria.scan.recording

import com.sagoma.planimetria.scan.recording.analysis.CaptureReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Correzioni di M0.2 dopo la prima registrazione reale (Motorola Edge 70 Fusion, 2026-10-07): valori non finiti nella nuvola di
 * punti, perdite esplicite, intrinseche della depth dalla texture, sincronizzazione per frame, stati degli stream.
 */
class CaptureFixesTest {
    // Valori reali della registrazione scan-20261007-232317 (solo come dati di prova).
    private val cpu = CameraIntrinsics(445.44116, 442.32663, 323.95255, 242.75182, 640, 480)
    private val texture = CameraIntrinsics(1336.32349, 1326.97986, 972.8576, 549.25543, 1920, 1080)
    private val sensorOffsetNs = 2_600_000L // timestamp del frame − timestamp dell'immagine, come sul telefono

    private fun ts(seq: Int) = 1_000_000_000L + seq * 33_333_333L
    private fun poseAt(seq: Int) = RecPose(seq * 0.01, 1.5, 0.0)

    private val header = RecordingHeader(
        version = 2,
        session = SessionInfo(depthMode = "AUTOMATIC", depthSupported = true),
        capture = CaptureSettings(CaptureMode.ENVIRONMENT, 100, 200, 500, 85, maxQueueBytes = 1 shl 20),
        cameraConfig = CameraConfigInfo("0", "BACK", 640, 480, 1920, 1080, 30, 30),
        camera = CameraModelInfo(90, cpu, texture),
    )

    // ---------- 1. Valori non finiti e frame persi ----------

    private fun cloudFrame(index: Int, xyz: List<Double>, conf: List<Double>, ids: List<Int>, planes: List<RecordedPlane> = emptyList()) =
        RecordedFrame(index = index, timestampNs = ts(index), tracking = "TRACKING", camera = poseAt(index), planes = planes, points = RecordedPointCloud(ts(index), xyz, conf, ids), frameSeq = index)

    @Test
    fun `NaN e infinito nella nuvola - il JSON li rifiuta, il frame non sparisce e i punti validi restano`() {
        val f = cloudFrame(
            0,
            xyz = listOf(0.1, 0.2, 0.3, Double.NaN, 1.0, 1.0, 2.0, 2.0, 2.0, 3.0, Double.POSITIVE_INFINITY, 3.0),
            conf = listOf(0.9, 0.8, Double.NaN, 0.7),
            ids = listOf(10, 11, 12, 13),
        )
        // Questa era la causa: la serializzazione del frame intero falliva.
        assertFailsWith<Exception> { ScanRecordingJson.frameLine(f) }
        val result = CaptureLines.frame(f)
        assertNull(result.missing)
        val back = ScanRecordingJson.decode(ScanRecordingJson.headerLine(header) + "\n" + assertNotNull(result.line)).frames.single()
        val cloud = assertNotNull(back.points)
        assertEquals(listOf(10), cloud.ids) // 11 (x NaN), 12 (confidenza NaN), 13 (y infinito) scartati
        assertEquals(listOf(0.1, 0.2, 0.3), cloud.xyz)
        assertEquals(listOf(0.9), cloud.confidence)
        assertTrue(cloud.isConsistent)
        assertEquals(3, back.invalidPoints)
        assertEquals(0, back.invalidPlanes)
        assertFalse(back.invalidCamera)
    }

    @Test
    fun `piani e pose non finiti - scartati e contati, il resto del frame resta`() {
        val good = RecordedPlane(1, "VERTICAL", "TRACKING", null, RecPose(0.0, 1.0, -3.0), listOf(0.0, 0.0, 1.0), 4.0, 2.5, listOf(-2.0, -1.0, 2.0, 1.0))
        val bad = good.copy(key = 2, polygon = listOf(-2.0, Double.NaN, 2.0, 1.0))
        val badPose = good.copy(key = 3, pose = RecPose(Double.NEGATIVE_INFINITY, 0.0, 0.0))
        val f = cloudFrame(0, listOf(0.0, 0.0, 0.0), listOf(1.0), listOf(1), planes = listOf(good, bad, badPose))
            .copy(cameraDisplay = RecPose(Double.NaN, 0.0, 0.0), floorY = Double.NaN)
        val clean = RecordingSanitizer.frame(f)
        assertEquals(listOf(1), clean.planes.map { it.key })
        assertEquals(2, clean.invalidPlanes)
        assertTrue(clean.invalidCamera)
        assertNull(clean.cameraDisplay)
        assertEquals(f.camera, clean.camera)
        assertNull(clean.floorY)
        assertNotNull(CaptureLines.frame(f).line)
        // Un frame già pulito non cambia.
        val ok = cloudFrame(1, listOf(0.0, 0.0, 0.0), listOf(1.0), listOf(1))
        assertTrue(RecordingSanitizer.frame(ok) === ok)
    }

    private fun recording(frames: List<RecordedFrame>, missing: List<MissingSample> = emptyList(), end: RecordingEnd? = RecordingEnd(frames = frames.size)) =
        ScanRecording(header, frames, end, poses = (0..40).map { PoseSample(seq = it, timestampNs = ts(it), tracking = "TRACKING", camera = poseAt(it)) }, missing = missing)

    private fun plain(i: Int) = RecordedFrame(index = i, timestampNs = ts(i), tracking = "TRACKING", camera = poseAt(i), frameSeq = i)

    @Test
    fun `frame acquisiti e non scritti senza spiegazione - errore, il report non dice nessuna perdita`() {
        // Come la registrazione reale: frame numerati all'acquisizione 0..9, scritti solo 0, 4 e 9.
        val r = recording(listOf(plain(0), plain(4), plain(9)))
        val counts = CaptureStreams.of(r).getValue(CaptureStream.FRAME)
        assertEquals(10, counts.counts.acquired)
        assertEquals(3, counts.counts.saved)
        assertEquals(7, counts.unexplained)
        assertEquals(7, counts.counts.missing)
        assertTrue(CaptureValidation.validate(r).any { it.severity == "error" && "7 acquisiti e non salvati" in it.what })
        val text = CaptureReport.text(CaptureReport.analyze(r))
        assertTrue("di cui 7 senza spiegazione" in text, text)
    }

    @Test
    fun `frame non serializzabile - riga missing esplicita con la causa, nessuna perdita non spiegata`() {
        val failure = CaptureLines.writeFailed(CaptureStream.FRAME, ts(1), 5, IllegalArgumentException("Unexpected special floating-point value NaN"))
        assertEquals("write-failed", failure.reason)
        assertEquals("IllegalArgumentException: Unexpected special floating-point value NaN", failure.detail)
        val r = recording(listOf(plain(0), plain(2)), missing = listOf(failure))
        val c = CaptureStreams.of(r).getValue(CaptureStream.FRAME)
        assertEquals(3, c.counts.acquired)
        assertEquals(2, c.counts.saved)
        assertEquals(1, c.counts.failed)
        assertEquals(0, c.unexplained)
        val issues = CaptureValidation.validate(r)
        assertTrue(issues.none { it.severity == "error" }, issues.toString())
        assertTrue(issues.any { "frame: 1 non scritti (IllegalArgumentException" in it.what })
        // La riga missing passa dal file e torna uguale.
        assertEquals(r, ScanRecordingJson.decode(ScanRecordingJson.encode(r)))
    }

    @Test
    fun `punti scartati - nel report e negli avvisi`() {
        val f = RecordingSanitizer.frame(cloudFrame(0, listOf(Double.NaN, 0.0, 0.0, 1.0, 1.0, 1.0), listOf(0.5, 0.5), listOf(1, 2)))
        val r = recording(listOf(f))
        val t = CaptureReport.analyze(r)
        assertEquals(1, t.invalidPoints)
        assertEquals(1, t.framesWithInvalidPoints)
        assertTrue("Valori non finiti scartati: 1 punti in 1 frame" in CaptureReport.text(t))
        assertTrue(CaptureValidation.validate(r).any { "Punti con valori non finiti scartati: 1" in it.what })
    }

    // ---------- 2. Intrinseche della depth ----------

    @Test
    fun `intrinseche depth - dalla texture alla risoluzione della depth, non dall'immagine CPU`() {
        val (k, source) = DepthIntrinsics.fromTexture(texture, 160, 90)
        assertEquals(IntrinsicsSource.TEXTURE_SCALED, source)
        assertEquals(111.36, k.fx, 0.01)
        assertEquals(110.58, k.fy, 0.01)
        assertEquals(81.07, k.cx, 0.01)
        assertEquals(45.77, k.cy, 0.01)
        assertEquals(160, k.width)
        assertEquals(90, k.height)
        // Pixel quadrati come nell'RGB (fx/fy uguale), al contrario del vecchio calcolo dall'immagine CPU 4:3.
        assertEquals(cpu.fx / cpu.fy, k.fx / k.fy, 1e-3)
        val legacy = cpu.scaledTo(160, 90)
        assertEquals(82.94, legacy.fy, 0.01)
        // Rapporto d'aspetto diverso dalla texture: segnalato.
        assertEquals(IntrinsicsSource.TEXTURE_SCALED_ASPECT_MISMATCH, DepthIntrinsics.fromTexture(texture, 160, 120).second)
    }

    private fun depth(seq: Int, frame: Int, k: CameraIntrinsics?, source: String = IntrinsicsSource.TEXTURE_SCALED, poseFrameSeq: Int? = frame, exact: Boolean = true) = DepthKeyframe(
        seq = seq, frameSeq = frame, timestampNs = ts(frame) - sensorOffsetNs, frameTimestampNs = ts(frame), camera = poseAt(frame),
        poseTimestampNs = ts(frame), poseExact = exact, width = 160, height = 90, path = CaptureDataset.depthPath(seq),
        intrinsics = k, intrinsicsSource = source, poseFrameSeq = poseFrameSeq, poseMatch = PoseMatch.FRAME,
    )

    private fun rgb(seq: Int, frame: Int) = RgbKeyframe(
        seq = seq, frameSeq = frame, timestampNs = ts(frame), imageTimestampNs = ts(frame) - sensorOffsetNs, tracking = "TRACKING",
        camera = poseAt(frame), intrinsics = cpu, width = 640, height = 480, path = CaptureDataset.rgbPath(seq), imageToFrameNs = sensorOffsetNs,
    )

    @Test
    fun `intrinseche RGB e depth a risoluzioni diverse - coerenti passano, quelle vecchie sono un errore`() {
        val good = ScanRecording(header, emptyList(), RecordingEnd(), poses = (0..20).map { PoseSample(seq = it, timestampNs = ts(it), tracking = "TRACKING", camera = poseAt(it)) },
            rgb = listOf(rgb(0, 3), rgb(1, 10)), depth = listOf(depth(0, 4, DepthIntrinsics.fromTexture(texture, 160, 90).first)))
        assertTrue(CaptureValidation.validate(good).none { it.severity == "error" }, CaptureValidation.validate(good).toString())
        val old = good.copy(depth = listOf(depth(0, 4, cpu.scaledTo(160, 90), IntrinsicsSource.LEGACY_IMAGE_SCALED)))
        assertTrue(CaptureValidation.validate(old).any { it.severity == "error" && "intrinseche incoerenti" in it.what })
        // Le RGB restano con le intrinseche dell'immagine CPU, la depth con quelle della sua risoluzione: proiettano lo stesso punto
        // nello stesso punto relativo dell'inquadratura in orizzontale.
        val p = assertNotNull(ArCameraProjection.project(RecPose(0.0, 0.0, 0.0), cpu, 0.3, 0.1, -2.0))
        val d = assertNotNull(ArCameraProjection.project(RecPose(0.0, 0.0, 0.0), DepthIntrinsics.fromTexture(texture, 160, 90).first, 0.3, 0.1, -2.0))
        assertEquals((p.u - cpu.cx) / cpu.fx, (d.u - 81.07) / 111.36, 1e-3)
    }

    // ---------- 3. Sincronizzazione per frame ----------

    @Test
    fun `assegnazione al frame - l'immagine appartiene al primo frame con timestamp maggiore o uguale`() {
        val frames = LongArray(5) { ts(it) }
        assertEquals(2, FrameAssignment.frameFor(ts(2) - sensorOffsetNs, frames)) // offset normale del sensore
        assertEquals(2, FrameAssignment.frameFor(ts(2), frames)) // stesso timestamp
        assertEquals(2, FrameAssignment.frameFor(ts(1) + 1, frames)) // appena dopo il frame 1: è del 2
        assertNull(FrameAssignment.frameFor(ts(4) + 1, frames)) // dopo l'ultimo frame noto
        assertEquals(0, FrameAssignment.frameFor(ts(0) - sensorOffsetNs, frames))
        assertNull(FrameAssignment.frameFor(ts(0) - 60_000_000L, frames)) // troppo lontano dal primo frame
        assertNull(FrameAssignment.frameFor(5, LongArray(0)))
    }

    @Test
    fun `sincronizzazione per frame - RGB e depth esatte nonostante l'offset di 2,6 ms`() {
        val poses = (0..20).map { PoseSample(seq = it, timestampNs = ts(it), tracking = "TRACKING", camera = poseAt(it)) }
        val k = DepthIntrinsics.fromTexture(texture, 160, 90).first
        val r = ScanRecording(header, emptyList(), RecordingEnd(), poses = poses, rgb = listOf(rgb(0, 3), rgb(1, 10)), depth = listOf(depth(0, 4, k), depth(1, 12, k, poseFrameSeq = null)))
        val index = CaptureIndex(r)
        for (x in r.rgb) {
            val m = assertNotNull(index.poseFor(x))
            assertTrue(m.exact)
            assertEquals(x.frameSeq, m.item.seq)
            assertEquals(sensorOffsetNs, m.deltaNs)
        }
        // Con poseFrameSeq dal telefono e, per le registrazioni precedenti, ricavato dal timestamp: stesso risultato.
        for (d in r.depth) {
            val m = assertNotNull(index.poseFor(d))
            assertTrue(m.exact)
            assertEquals(d.frameSeq, m.item.seq)
            assertEquals(sensorOffsetNs, m.deltaNs)
        }
        val t = CaptureReport.analyze(r)
        assertEquals(2, t.rgbPoseExact)
        assertEquals(2, t.depthPoseExact)
        assertEquals(2.6, t.rgbPoseDeltaMs.median, 1e-9)
        val issues = CaptureValidation.validate(r)
        assertTrue(issues.none { it.severity == "error" }, issues.toString())
        assertTrue(issues.none { "fuori dal suo frame" in it.what }) // l'offset normale non è più un avviso
        // Una depth dichiarata del frame sbagliato (troppo lontano) è un errore.
        val wrong = r.copy(depth = listOf(depth(0, 4, k, poseFrameSeq = 9)))
        assertTrue(CaptureValidation.validate(wrong).any { "non è quello della depth" in it.what })
    }

    @Test
    fun `registrazione reale precedente - la depth risulta esatta anche senza poseFrameSeq`() {
        // Come in scan-20261007-232317: poseExact = false, poseTimestampNs = frame di lettura, depth 2,6 ms prima.
        val poses = (0..10).map { PoseSample(seq = it, timestampNs = ts(it), tracking = "TRACKING", camera = poseAt(it)) }
        val old = depth(0, 5, cpu.scaledTo(160, 90), IntrinsicsSource.LEGACY_IMAGE_SCALED, poseFrameSeq = null, exact = false)
        val m = assertNotNull(CaptureIndex(ScanRecording(header, emptyList(), poses = poses, depth = listOf(old))).poseFor(old))
        assertTrue(m.exact)
        assertEquals(5, m.item.seq)
    }

    // ---------- 4. Stati degli stream ----------

    @Test
    fun `stati - acquired, saved, unavailable, failed, missing dal telefono e dai dati`() {
        val k = DepthIntrinsics.fromTexture(texture, 160, 90).first
        val poses = (0..20).map { PoseSample(seq = it, timestampNs = ts(it), tracking = "TRACKING", camera = poseAt(it)) }
        val d0 = depth(0, 4, k).copy(
            rawPath = CaptureDataset.rawDepthPath(0), rawStatus = StreamStatus.SAVED, rawTimestampNs = ts(4) - sensorOffsetNs, rawWidth = 160, rawHeight = 90,
            confidencePath = CaptureDataset.confidencePath(0), confidenceStatus = StreamStatus.SAVED,
        )
        val d1 = depth(1, 12, k).copy(rawStatus = StreamStatus.UNAVAILABLE, rawDetail = "NotYetAvailableException", confidenceStatus = StreamStatus.UNAVAILABLE, confidenceDetail = "depth raw non disponibile")
        val d2 = depth(2, 18, k).copy(rawStatus = StreamStatus.FAILED, rawDetail = "IllegalStateException: boh", confidenceStatus = StreamStatus.UNAVAILABLE)
        val missing = listOf(
            MissingSample(kind = CaptureStream.DEPTH, reason = "not-yet-available", count = 6),
            MissingSample(kind = CaptureStream.DEPTH, reason = "not-new", count = 4),
            MissingSample(kind = CaptureStream.RGB, reason = "backlog", count = 2),
            MissingSample(kind = CaptureStream.RGB, reason = "error", count = 1, detail = "IllegalStateException"),
        )
        val r = ScanRecording(header, emptyList(), null, poses = poses, rgb = listOf(rgb(0, 3), rgb(1, 10)), depth = listOf(d0, d1, d2), missing = missing)
        val s = CaptureStreams.of(r).mapValues { it.value.counts }
        assertEquals(StreamCounts(acquired = 3, saved = 3, unavailable = 6, failed = 0, missing = 0), s[CaptureStream.DEPTH]) // "not-new" non è una perdita
        assertEquals(StreamCounts(acquired = 4, saved = 2, unavailable = 0, failed = 1, missing = 2), s[CaptureStream.RGB])
        assertEquals(StreamCounts(acquired = 1, saved = 1, unavailable = 1, failed = 1, missing = 0), s[CaptureStream.RAW_DEPTH])
        assertEquals(StreamCounts(acquired = 1, saved = 1, unavailable = 2, failed = 0, missing = 0), s[CaptureStream.CONFIDENCE])
        assertTrue(CaptureStreams.of(r).values.all { it.unexplained == 0 })
        val text = CaptureReport.text(CaptureReport.analyze(r))
        assertTrue("acquisiti  salvati  non disp.  falliti  persi" in text, text)
        assertTrue(Regex("""rawDepth\s+1\s+1\s+1\s+1\s+0""").containsMatchIn(text), text)

        // Conteggi del telefono: hanno la precedenza, ma una perdita non spiegata si aggiunge comunque.
        val device = mapOf(CaptureStream.RGB to StreamCounts(acquired = 5, saved = 2, unavailable = 0, failed = 1, missing = 2))
        val withEnd = r.copy(end = RecordingEnd(streams = device))
        val c = CaptureStreams.of(withEnd).getValue(CaptureStream.RGB)
        assertTrue(c.fromDevice)
        assertEquals(1, c.unexplained) // 5 acquisiti − 2 salvati − 0 falliti in scrittura − 2 persi
        assertEquals(3, c.counts.missing)
        // Un file raw senza stato "saved" è incoerente.
        val bad = r.copy(depth = listOf(d0.copy(rawStatus = StreamStatus.FAILED)))
        assertTrue(CaptureValidation.validate(bad).any { "file raw presente ma stato failed" in it.what })
    }
}
