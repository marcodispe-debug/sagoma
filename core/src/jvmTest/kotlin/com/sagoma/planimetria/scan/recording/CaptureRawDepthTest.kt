package com.sagoma.planimetria.scan.recording

import com.sagoma.planimetria.scan.recording.analysis.CaptureReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Depth raw (M0.2, dopo scan-20261007-234946): ARCore ridà a volte la stessa raw (29 su 257) e la raw è più vecchia della depth
 * filtrata (da 33 ms a 4,4 s): non si salva due volte e prende la posa del SUO timestamp, mai quella della depth filtrata.
 */
class CaptureRawDepthTest {
    private val texture = CameraIntrinsics(1336.32349, 1326.97986, 972.8576, 549.25543, 1920, 1080)
    private val k = DepthIntrinsics.fromTexture(texture, 160, 90).first
    private val offset = 2_600_000L // timestamp del frame − timestamp dell'immagine

    private fun ts(seq: Int) = 1_000_000_000L + seq * 33_333_333L
    private fun poseAt(seq: Int) = RecPose(seq * 0.01, 1.5, -seq * 0.002)
    private val poses = (0..200).map { PoseSample(seq = it, timestampNs = ts(it), tracking = "TRACKING", camera = poseAt(it)) }
    private val header = RecordingHeader(
        session = SessionInfo(depthMode = "AUTOMATIC", depthSupported = true),
        capture = CaptureSettings(CaptureMode.ENVIRONMENT, 100, 200, 500, 85, maxQueueBytes = 1 shl 20),
        cameraConfig = CameraConfigInfo("0", "BACK", 640, 480, 1920, 1080, 30, 30),
    )

    /** Depth filtrata del frame [frame]; raw dal frame [rawFrame] (più vecchia), con l'associazione come la fa il telefono. */
    private fun depth(seq: Int, frame: Int, rawFrame: Int?, status: String = StreamStatus.SAVED): DepthKeyframe {
        val rawTs = rawFrame?.let { ts(it) - offset }
        val m = rawTs?.let { FrameAssignment.match(it, LongArray(poses.size) { i -> poses[i].timestampNs }) }
        val saved = status == StreamStatus.SAVED && rawFrame != null
        return DepthKeyframe(
            seq = seq, frameSeq = frame, timestampNs = ts(frame) - offset, frameTimestampNs = ts(frame), camera = poseAt(frame),
            poseTimestampNs = ts(frame), poseExact = true, width = 160, height = 90, path = CaptureDataset.depthPath(seq), intrinsics = k,
            poseFrameSeq = frame, poseMatch = PoseMatch.FRAME,
            rawPath = if (saved) CaptureDataset.rawDepthPath(seq) else null, rawTimestampNs = rawTs, rawWidth = 160, rawHeight = 90,
            rawStatus = if (rawFrame == null) StreamStatus.UNAVAILABLE else status,
            confidencePath = if (saved) CaptureDataset.confidencePath(seq) else null,
            confidenceStatus = if (rawFrame == null) StreamStatus.UNAVAILABLE else status,
            rawPoseFrameSeq = if (saved) m?.index?.let { poses[it].seq } else null,
            rawPoseMatch = if (saved) m?.kind else null,
            rawPoseDeltaNs = if (saved) m?.index?.let { poses[it].timestampNs - rawTs!! } else null,
            rawCamera = if (saved) m?.index?.let { poses[it].camera } else null,
        )
    }

    private fun rec(vararg d: DepthKeyframe) = ScanRecording(header, emptyList(), RecordingEnd(), poses = poses, depth = d.toList())

    @Test
    fun `raw ripetuta - riconosciuta da timestamp e dimensioni, una raw nuova non si perde`() {
        assertTrue(RawDepthDedup.sameSample(ts(5), 160, 90, ts(5), 160, 90))
        assertFalse(RawDepthDedup.sameSample(ts(6), 160, 90, ts(5), 160, 90)) // timestamp nuovo: si salva
        assertFalse(RawDepthDedup.sameSample(ts(5), 320, 180, ts(5), 160, 90)) // stesso timestamp ma altre dimensioni: non è lo stesso campione
        assertFalse(RawDepthDedup.sameSample(ts(5), 160, 90, null, null, null)) // la prima

        // Il keyframe 1 ripete la raw del keyframe 0: non salvata, contata come ripetuta.
        val r = rec(depth(0, 20, 15), depth(1, 35, 15, StreamStatus.DUPLICATE), depth(2, 50, 44))
        val raw = CaptureStreams.of(r).getValue(CaptureStream.RAW_DEPTH)
        assertEquals(StreamCounts(acquired = 3, saved = 2, unavailable = 0, failed = 0, missing = 0, duplicate = 1), raw.counts)
        assertEquals(0, raw.unexplained)
        assertEquals(1, CaptureStreams.of(r).getValue(CaptureStream.CONFIDENCE).counts.duplicate)
        assertTrue(CaptureValidation.validate(r).none { it.severity == "error" }, CaptureValidation.validate(r).toString())
        val text = CaptureReport.text(CaptureReport.analyze(r))
        assertTrue("persi  ripetuti" in text, text)
        assertTrue(Regex("""rawDepth\s+3\s+2\s+0\s+0\s+0\s+1""").containsMatchIn(text), text)
        // Una ripetuta salvata di nuovo è un errore.
        val again = rec(depth(0, 20, 15), depth(1, 35, 15, StreamStatus.DUPLICATE).copy(rawPath = CaptureDataset.rawDepthPath(1)))
        assertTrue(CaptureValidation.validate(again).any { "raw ripetuta ma salvata" in it.what || "file raw presente ma stato duplicate" in it.what })
        // Conteggi del telefono con le ripetute: nessuna perdita non spiegata.
        val device = r.copy(end = RecordingEnd(streams = mapOf(CaptureStream.RAW_DEPTH to StreamCounts(acquired = 3, saved = 2, duplicate = 1))))
        assertEquals(0, CaptureStreams.of(device).getValue(CaptureStream.RAW_DEPTH).unexplained)
    }

    @Test
    fun `raw con timestamp diverso dalla depth filtrata - posa del proprio frame, non di quella della depth`() {
        val d = depth(0, frame = 120, rawFrame = 103) // raw di 17 frame prima (~567 ms), come sul Motorola
        val raw = assertNotNull(d.rawTimestampNs)
        assertTrue(d.timestampNs - raw > 500_000_000L)
        assertEquals(103, d.rawPoseFrameSeq)
        assertEquals(PoseMatch.FRAME, d.rawPoseMatch)
        assertEquals(offset, d.rawPoseDeltaNs)
        assertEquals(poseAt(103), d.rawCamera)
        assertTrue(d.rawCamera != d.camera) // non la posa della depth filtrata

        val r = rec(d)
        val p = assertNotNull(CaptureIndex(r).rawPoseFor(d))
        assertEquals(PoseMatch.FRAME, p.kind)
        assertEquals(103, p.pose?.seq)
        val t = CaptureReport.analyze(r)
        assertEquals(1, t.rawPoseFrame)
        assertEquals(567.0, t.rawAgeMs.median, 1.0)
        assertTrue(CaptureValidation.validate(r).none { it.severity == "error" }, CaptureValidation.validate(r).toString())
        // La posa della depth filtrata messa sulla raw (il vecchio comportamento) è un errore.
        val wrong = rec(d.copy(rawPoseFrameSeq = 120, rawCamera = poseAt(120)))
        assertTrue(CaptureValidation.validate(wrong).any { it.severity == "error" && "raw" in it.what })
    }

    @Test
    fun `raw associata alla posa giusta del proprio timestamp - per frame e per timestamp`() {
        val times = LongArray(poses.size) { poses[it].timestampNs }
        // Offset normale: regola dei frame, esatta.
        val f = FrameAssignment.match(ts(40) - offset, times)
        assertEquals(PoseMatch.FRAME, f.kind)
        assertEquals(40, f.index)
        // Dopo l'ultima posa nota ma entro 50 ms: la più vicina, per timestamp.
        val after = FrameAssignment.match(ts(200) + 20_000_000L, times)
        assertEquals(PoseMatch.TIMESTAMP, after.kind)
        assertEquals(200, after.index)
        // Le registrazioni senza i campi nuovi si risolvono offline con la stessa regola.
        val old = depth(0, 60, 50).copy(rawPoseFrameSeq = null, rawPoseMatch = null, rawPoseDeltaNs = null, rawCamera = null)
        val p = assertNotNull(CaptureIndex(rec(old)).rawPoseFor(old))
        assertEquals(PoseMatch.FRAME, p.kind)
        assertEquals(50, p.pose?.seq)
        assertEquals(offset, p.deltaNs)
    }

    @Test
    fun `raw senza posa compatibile - nessuna posa inventata, motivo esplicito`() {
        val times = LongArray(poses.size) { poses[it].timestampNs }
        val tooOld = FrameAssignment.match(ts(0) - 4_400_000_000L, times) // 4,4 s prima della prima posa in memoria
        assertNull(tooOld.index)
        assertEquals(PoseMatch.UNAVAILABLE, tooOld.kind)
        assertTrue("più vecchia della prima posa disponibile" in tooOld.detail!!, tooOld.detail)
        val tooNew = FrameAssignment.match(ts(200) + 80_000_000L, times)
        assertEquals(PoseMatch.UNAVAILABLE, tooNew.kind)
        assertTrue("più recente dell'ultima posa" in tooNew.detail!!)
        assertEquals(PoseMatch.UNAVAILABLE, FrameAssignment.match(5, LongArray(0)).kind)

        // Come la registra il telefono: posa assente, motivo, nessun frame.
        val d = depth(0, 30, 10).copy(
            rawTimestampNs = ts(0) - 4_400_000_000L, rawPoseFrameSeq = null, rawPoseMatch = PoseMatch.UNAVAILABLE, rawPoseDeltaNs = null,
            rawCamera = null, rawPoseDetail = tooOld.detail,
        )
        val r = rec(d)
        assertTrue(CaptureValidation.validate(r).none { it.severity == "error" }, CaptureValidation.validate(r).toString())
        val p = assertNotNull(CaptureIndex(r).rawPoseFor(d))
        assertNull(p.pose)
        assertEquals(PoseMatch.UNAVAILABLE, p.kind)
        assertEquals(1, CaptureReport.analyze(r).rawPoseNone)
        assertTrue("senza posa 1" in CaptureReport.text(CaptureReport.analyze(r)))
        // Una raw "senza posa" che ne porta una è incoerente.
        val bad = rec(d.copy(rawCamera = poseAt(0)))
        assertTrue(CaptureValidation.validate(bad).any { "raw senza posa ma con una posa" in it.what })
    }
}
