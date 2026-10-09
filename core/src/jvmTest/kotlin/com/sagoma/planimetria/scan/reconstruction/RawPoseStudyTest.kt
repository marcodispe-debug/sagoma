package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.CameraModelInfo
import com.sagoma.planimetria.scan.recording.CaptureDataset
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
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
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lo studio della posa della raw deve riconoscere la verità quando la conosciamo: camera in movimento (stanza sintetica), metà delle
 * raw recenti (riferimento) e metà vecchie di 6 frame (200 ms). Caso "A vera": il contenuto è dell'istante dichiarato. Caso "C vera":
 * il contenuto è del frame corrente ma il timestamp dichiara 6 frame prima (come sospettiamo sul Motorola).
 */
class RawPoseStudyTest {
    private val room = SyntheticRoom(noiseM = 0.01)
    private val frameNs = 33_333_333L
    private val offsetNs = 2_600_000L // immagine 2,6 ms prima del frame, come sul telefono

    private fun poseAt(i: Int): RecPose {
        val t = i * frameNs / 1e9
        val a = 0.6 * t
        return room.pose(2.0 + 0.4 * cos(a), 1.4, 1.6 + 0.4 * sin(a), 25.0 * t * 1.0, -20.0)
    }

    private fun dataset(contentFromDeclaredTime: Boolean, lag: Int = 6, step: Int = 10): Pair<ScanRecording, Map<String, ByteArray>> {
        val n = 420
        val poses = (0 until n).map { PoseSample(seq = it, timestampNs = 1_000_000_000L + it * frameNs, tracking = "TRACKING", camera = poseAt(it)) }
        val blobs = HashMap<String, ByteArray>()
        val depth = mutableListOf<DepthKeyframe>()
        var seq = 0
        for (cur in 20 until n step step) {
            val old = seq % 2 == 1
            val declared = if (old) cur - lag else cur
            val content = if (old && contentFromDeclaredTime) cur - lag else cur
            val mm = room.render(poses[content].camera!!, seq)
            blobs[CaptureDataset.rawDepthPath(seq)] = DepthRaw.encode(mm)
            blobs[CaptureDataset.depthPath(seq)] = DepthRaw.encode(room.render(poses[cur].camera!!, seq + 1000))
            blobs[CaptureDataset.confidencePath(seq)] = ByteArray(room.width * room.height) { 255.toByte() }
            val curTs = poses[cur].timestampNs
            depth.add(
                DepthKeyframe(
                    seq = seq, frameSeq = cur, timestampNs = curTs - offsetNs, frameTimestampNs = curTs, camera = poses[cur].camera, poseTimestampNs = curTs,
                    poseExact = true, width = room.width, height = room.height, path = CaptureDataset.depthPath(seq), intrinsics = room.k,
                    intrinsicsSource = IntrinsicsSource.TEXTURE_SCALED, poseFrameSeq = cur, poseMatch = PoseMatch.FRAME,
                    rawPath = CaptureDataset.rawDepthPath(seq), confidencePath = CaptureDataset.confidencePath(seq),
                    rawTimestampNs = poses[declared].timestampNs - offsetNs, rawWidth = room.width, rawHeight = room.height,
                    rawStatus = StreamStatus.SAVED, confidenceStatus = StreamStatus.SAVED, rawPoseFrameSeq = declared, rawPoseMatch = PoseMatch.FRAME,
                    rawPoseDeltaNs = offsetNs, rawCamera = poses[declared].camera,
                ),
            )
            seq++
        }
        val k = room.k
        val header = RecordingHeader(
            session = SessionInfo(depthMode = "AUTOMATIC", depthSupported = true),
            capture = CaptureSettings(CaptureMode.ENVIRONMENT, 100, 200, 500, 85, maxQueueBytes = 1 shl 20),
            camera = CameraModelInfo(90, null, CameraIntrinsics(k.fx * 12, k.fy * 12, k.cx * 12, k.cy * 12, k.width * 12, k.height * 12)),
        )
        return ScanRecording(header, emptyList(), RecordingEnd(), poses = poses, depth = depth) to blobs
    }

    @Test
    fun `contenuto dell'istante dichiarato - lo studio conferma A`() {
        val (r, blobs) = dataset(contentFromDeclaredTime = true)
        val res = RawPoseStudy.run(r, { blobs[it] })
        val report = RawPoseStudy.report(res, "sintetico A")
        assertTrue("supportano A" in res.verdict, report)
        assertTrue(res.confidence.startsWith("alta"), report)
        val old = res.surface.getValue(RawAssociation.A to "vecchie (> 150 ms)")
        assertTrue(old.medianM < res.surface.getValue(RawAssociation.C to "vecchie (> 150 ms)").medianM, report)
    }

    @Test
    fun `contenuto del frame corrente con timestamp vecchio - lo studio riconosce C`() {
        val (r, blobs) = dataset(contentFromDeclaredTime = false)
        val res = RawPoseStudy.run(r, { blobs[it] })
        val report = RawPoseStudy.report(res, "sintetico C")
        assertTrue("supportano C" in res.verdict, report)
        assertTrue(res.confidence.startsWith("alta"), report)
    }

    @Test
    fun `senza movimento tra A e C - lo studio dichiara di non poter distinguere`() {
        // Ritardo 0: A e C coincidono, nessuna prova può distinguere.
        val (r, blobs) = dataset(contentFromDeclaredTime = true, lag = 0)
        val res = RawPoseStudy.run(r, { blobs[it] })
        assertTrue("NON CONCLUSIVO" in res.verdict, RawPoseStudy.report(res, "sintetico senza ritardo"))
    }

    @Test
    fun `bordi - contenuto dell'istante dichiarato - A supportata`() {
        val (r, blobs) = dataset(contentFromDeclaredTime = true, step = 3)
        val res = RawEdgeStudy.run(r, { blobs[it] })
        val report = RawEdgeStudy.report(res, "sintetico A")
        assertTrue(res.verdict.startsWith("A supportata"), report)
        val old = res.cases.filter { it.ageMs >= 150 && it.lambda != null }
        assertTrue(old.isNotEmpty() && Geo.median(old.map { it.lambda!! }.toDoubleArray()) < 0.3, report)
    }

    @Test
    fun `bordi - contenuto del frame corrente con timestamp vecchio - C supportata`() {
        val (r, blobs) = dataset(contentFromDeclaredTime = false, step = 3)
        val res = RawEdgeStudy.run(r, { blobs[it] })
        val report = RawEdgeStudy.report(res, "sintetico C")
        assertTrue(res.verdict.startsWith("C supportata"), report)
        val old = res.cases.filter { it.ageMs >= 150 && it.lambda != null }
        assertTrue(old.isNotEmpty() && Geo.median(old.map { it.lambda!! }.toDoubleArray()) > 0.7, report)
    }

    @Test
    fun `bordi - senza ritardo non si sceglie`() {
        val (r, blobs) = dataset(contentFromDeclaredTime = true, lag = 0)
        val res = RawEdgeStudy.run(r, { blobs[it] })
        assertTrue(res.verdict.startsWith("NON CONCLUSIVO"), RawEdgeStudy.report(res, "sintetico senza ritardo"))
    }

    @Test
    fun `interpolazione e test dei segni`() {
        val a = RecPose(0.0, 0.0, 0.0); val b = room.pose(1.0, 2.0, 3.0, 90.0, 0.0)
        val m = RawPoseStudy.interpolate(a, b, 0.5)
        assertEquals(0.5, m.x, 1e-12); assertEquals(1.0, m.y, 1e-12); assertEquals(1.5, m.z, 1e-12)
        val half = room.pose(0.0, 0.0, 0.0, 45.0, 0.0)
        assertEquals(half.qy, m.qy, 1e-9); assertEquals(half.qw, m.qw, 1e-9)
        assertEquals(1.0, RawPoseStudy.signTestP(5, 10), 1e-9)
        assertTrue(RawPoseStudy.signTestP(16, 19) < 0.01)
        assertTrue(RawPoseStudy.signTestP(13, 19) > 0.05)
    }

    /** Sulla registrazione reale (solo con SAGOMA_RECON_ZIP): scrive il report accanto allo ZIP. */
    @Test
    fun `registrazione reale`() {
        val path = System.getenv("SAGOMA_RECON_ZIP") ?: return
        val file = File(path)
        val ds = CaptureDatasetFiles.open(file)
        val res = RawPoseStudy.run(ds.recording, { ds.read(it) })
        val edges = RawEdgeStudy.run(ds.recording, { ds.read(it) })
        val text = RawPoseStudy.report(res, file.name) + "\n\n" + RawEdgeStudy.report(edges, file.name)
        File(file.absoluteFile.parentFile, "rawpose-study-" + file.nameWithoutExtension + ".txt").writeText(text)
        // Controllo visivo con l'RGB sulle tre raw vecchie dove A e C differiscono di più.
        val dir = File(file.absoluteFile.parentFile, "rawpose-overlay-" + file.nameWithoutExtension).apply { mkdirs() }
        val picks = res.cases.filter { it.ageMs > 150 && it.predictedShiftPx != null }.sortedByDescending { it.predictedShiftPx }.take(3)
        for (c in picks) for (a in listOf(RawAssociation.A, RawAssociation.C)) {
            RawPoseStudy.rgbOverlay(ds.recording, { ds.read(it) }, c.depthSeq, a)?.let { File(dir, "raw${c.depthSeq}-${a.name}.svg").writeText(it) }
        }
        println(text)
    }
}
