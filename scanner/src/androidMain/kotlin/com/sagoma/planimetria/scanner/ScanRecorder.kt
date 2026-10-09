package com.sagoma.planimetria.scanner

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Camera
import com.google.ar.core.CameraIntrinsics as ArIntrinsics
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.NotYetAvailableException
import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.CaptureDataset
import com.sagoma.planimetria.scan.recording.CaptureLines
import com.sagoma.planimetria.scan.recording.CaptureStream
import com.sagoma.planimetria.scan.recording.DepthIntrinsics
import com.sagoma.planimetria.scan.recording.FrameAssignment
import com.sagoma.planimetria.scan.recording.IntrinsicsSource
import com.sagoma.planimetria.scan.recording.PoseMatch
import com.sagoma.planimetria.scan.recording.RawDepthDedup
import com.sagoma.planimetria.scan.recording.RecordingSanitizer
import com.sagoma.planimetria.scan.recording.StreamCounts
import com.sagoma.planimetria.scan.recording.StreamStatus
import com.sagoma.planimetria.scan.recording.CaptureMode
import com.sagoma.planimetria.scan.recording.CaptureSettings
import com.sagoma.planimetria.scan.recording.CaptureStats
import com.sagoma.planimetria.scan.recording.DepthKeyframe
import com.sagoma.planimetria.scan.recording.MissingSample
import com.sagoma.planimetria.scan.recording.PoseSample
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.RecordedFrame
import com.sagoma.planimetria.scan.recording.RecordedPlane
import com.sagoma.planimetria.scan.recording.RecordedPointCloud
import com.sagoma.planimetria.scan.recording.RecordingEnd
import com.sagoma.planimetria.scan.recording.RecordingHeader
import com.sagoma.planimetria.scan.recording.RgbKeyframe
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.round

/**
 * Registra una sessione di scansione (M0.2) in una cartella: `recording.jsonl` (formato in `core/.../scan/recording`), keyframe
 * RGB in `rgb/`, depth grezza in `depth/`, metadati in `metadata/`. Solo osserva: non cambia niente nel rilevamento.
 *
 * Acquisizione e scrittura sono separate. [onFrame] gira sul thread di disegno (dove gira ARCore) e fa solo copie veloci: posa,
 * piani e punti in array, piani dell'immagine YUV e della depth in byte; poi rilascia subito le immagini di ARCore e mette il
 * lavoro in coda. Un solo thread di scrittura (quindi nell'ordine di acquisizione) converte in JPEG, serializza il JSON e scrive
 * su disco. Se la coda supera [CaptureSettings.maxQueueBytes] si scartano keyframe, registrandoli come `missing` (motivo
 * `backlog`): l'acquisizione non aspetta mai il disco.
 *
 * Tutto è nel mondo ARCore, senza conversioni. [onFrame] solo dal thread di disegno, il resto dal thread principale.
 */
class ScanRecorder internal constructor(
    /** Cartella della registrazione. */
    val directory: File,
    private val header: RecordingHeader,
    val depthSupported: Boolean?,
) {
    /** Stato per la schermata di debug. */
    data class Stats(
        val frames: Int,
        val verticalPlanes: Int,
        val pointSamples: Int,
        val elapsedMs: Long,
        val depthSupported: Boolean?,
        val file: File,
        val finished: Boolean,
        val full: Boolean,
        val mode: String,
        val rgb: Int,
        val depth: Int,
        val droppedRgb: Int,
        val droppedDepth: Int,
        val queueItems: Int,
        /** Frame ARCore al secondo nell'ultimo secondo. */
        val arHz: Double,
    )

    /** Per compatibilità con chi mostra il percorso: la cartella. */
    val file: File get() = directory

    private val settings: CaptureSettings = header.capture ?: settingsFor(CaptureMode.ENVIRONMENT)
    private val startedAtNs = SystemClock.elapsedRealtimeNanos()
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    @Volatile private var endedAtNs = 0L
    @Volatile private var full = false

    // ---------- Stato del thread di disegno ----------
    private var poseSeq = 0
    private var lastFrameTs = Long.MIN_VALUE
    private var lastFrameLineTs = Long.MIN_VALUE
    private var lastRgbTs = Long.MIN_VALUE
    private var lastDepthTryTs = Long.MIN_VALUE
    private var lastDepthImageTs = Long.MIN_VALUE
    private var lastCloudTimestamp = Long.MIN_VALUE
    private var frameIndex = 0
    private var rgbSeq = 0
    private var depthSeq = 0
    private var lastRgbCaptured: Pair<Int, Long>? = null // seq, timestamp
    private var lastDepthCaptured: Pair<Int, Long>? = null
    private var queuedRgbBytesEstimate = 0L
    private val planeKeys = HashMap<Plane, Int>()
    private val ring = PoseRing(RING_SIZE)
    private val missingAgg = LinkedHashMap<String, MissingAgg>()
    // Statistiche dell'intervallo corrente (circa 1 s).
    private var statsStartNs = 0L
    private var drawCalls = 0
    private var arFrames = 0
    private var updateSumNs = 0L
    private var updateMaxNs = 0L
    private var captureSumNs = 0L
    private var captureMaxNs = 0L
    private var lastDrawNs = 0L
    private var drawGapMaxNs = 0L
    private var maxQueueItems = 0
    private var maxQueueBytes = 0L
    @Volatile private var lastArHz = 0.0

    // ---------- Coda e thread di scrittura ----------
    private val queue = LinkedBlockingQueue<Job>()
    private val queuedBytes = AtomicLong()
    private val written = CountDownLatch(1)
    @Volatile private var frameLines = 0
    @Volatile private var poseLines = 0
    @Volatile private var rgbWritten = 0
    @Volatile private var depthWritten = 0
    @Volatile private var droppedRgb = 0
    @Volatile private var droppedDepth = 0
    @Volatile private var pointSamples = 0
    @Volatile private var bytesWritten = 0L
    @Volatile private var verticalCount = 0
    private val verticalKeys = HashSet<Int>()
    /** Punti scartati perché non finiti (vedi [RecordingSanitizer]). */
    @Volatile private var invalidPoints = 0
    private val counts = Counts()

    /** Conteggi per stream ([StreamStatus]): li aggiornano sia il thread di disegno (acquisizione) sia quello di scrittura. */
    private class Counts {
        private val map = CaptureStream.ALL.associateWith { IntArray(6) }
        private fun add(stream: String, i: Int) = synchronized(this) { map.getValue(stream)[i]++ }
        fun acquired(s: String) = add(s, 0)
        fun saved(s: String) = add(s, 1)
        fun unavailable(s: String) = add(s, 2)
        fun failed(s: String) = add(s, 3)
        fun missing(s: String) = add(s, 4)
        fun duplicate(s: String) = add(s, 5)
        fun snapshot(): Map<String, StreamCounts> = synchronized(this) { map.mapValues { (_, c) -> StreamCounts(c[0], c[1], c[2], c[3], c[4], c[5]) } }
    }
    private var rgbWriteSumNs = 0L
    private var rgbWriteMaxNs = 0L
    private var rgbWriteCount = 0
    private val writer: BufferedWriter
    private val worker: Thread

    init {
        File(directory, CaptureDataset.RGB_DIR).mkdirs()
        File(directory, CaptureDataset.DEPTH_DIR).mkdirs()
        File(directory, CaptureDataset.METADATA_DIR).mkdirs()
        File(directory, CaptureDataset.CAMERA).writeText(ScanRecordingJson.cameraMetadata(header), Charsets.UTF_8)
        File(directory, CaptureDataset.README).writeText(ScanRecordingJson.readme(header), Charsets.UTF_8)
        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(File(directory, CaptureDataset.RECORDING)), Charsets.UTF_8), 1 shl 16)
        writer.write(ScanRecordingJson.headerLine(header))
        writer.newLine()
        writer.flush()
        writing[directory.absoluteFile] = written
        worker = Thread({ drain() }, "ScanRecorderWriter").apply { start() }
    }

    fun stats(): Stats = Stats(
        frames = frameLines, verticalPlanes = verticalCount, pointSamples = pointSamples,
        elapsedMs = ((if (closed.get()) endedAtNs else SystemClock.elapsedRealtimeNanos()) - startedAtNs) / 1_000_000,
        depthSupported = depthSupported, file = directory, finished = closed.get(), full = full, mode = settings.mode,
        rgb = rgbWritten, depth = depthWritten, droppedRgb = droppedRgb, droppedDepth = droppedDepth, queueItems = queue.size, arHz = lastArHz,
    )

    // ---------- Acquisizione (thread di disegno) ----------

    /**
     * Da chiamare a ogni disegno, subito dopo `Session.update()`. [updateNs]: quanto è durato `update()` (per la diagnosi).
     * Non lancia eccezioni: un errore qui non deve fermare la scansione.
     */
    fun onFrame(session: Session, frame: Frame, camera: Camera, floorY: Float?, updateNs: Long) {
        if (closed.get()) return
        val t0 = SystemClock.elapsedRealtimeNanos()
        synchronized(lock) {
            if (closed.get()) return
            try {
                capture(session, frame, camera, floorY, t0)
            } catch (e: Throwable) {
                Log.w(TAG, "Frame non registrato", e)
                missing(CaptureStream.FRAME, "error", frame.timestamp, t0, describe(e))
                counts.failed(CaptureStream.FRAME)
            }
            val t1 = SystemClock.elapsedRealtimeNanos()
            drawCalls++
            if (lastDrawNs != 0L) drawGapMaxNs = max(drawGapMaxNs, t0 - lastDrawNs)
            lastDrawNs = t0
            updateSumNs += updateNs
            updateMaxNs = max(updateMaxNs, updateNs)
            captureSumNs += t1 - t0
            captureMaxNs = max(captureMaxNs, t1 - t0)
            maxQueueItems = max(maxQueueItems, queue.size)
            maxQueueBytes = max(maxQueueBytes, queuedBytes.get())
            if (statsStartNs == 0L) statsStartNs = t0
            if (t1 - statsStartNs >= STATS_INTERVAL_NS) emitStats(t1)
        }
    }

    private fun capture(session: Session, frame: Frame, camera: Camera, floorY: Float?, now: Long) {
        val ts = frame.timestamp
        if (ts == 0L || ts == lastFrameTs) return // nessuna immagine nuova: niente di nuovo da registrare
        lastFrameTs = ts
        val seq = poseSeq++
        arFrames++
        val tracking = camera.trackingState.name
        val pose = poseArray(camera.pose)
        ring.put(ts, seq, pose)
        enqueue(Job.PoseLine(seq, ts, now, tracking, pose))

        if (lastFrameLineTs == Long.MIN_VALUE || ts - lastFrameLineTs >= settings.frameIntervalMs * 1_000_000) {
            lastFrameLineTs = ts
            if (frameIndex >= MAX_FRAMES) full = true
            else {
                enqueue(frameSnapshot(session, frame, camera, floorY, seq, ts, now, tracking, pose))
                counts.acquired(CaptureStream.FRAME)
            }
        }
        if (lastRgbTs == Long.MIN_VALUE || ts - lastRgbTs >= settings.rgbIntervalMs * 1_000_000) captureRgb(frame, camera, seq, ts, now, tracking, pose)
        if (depthActive && (lastDepthTryTs == Long.MIN_VALUE || ts - lastDepthTryTs >= settings.depthIntervalMs * 1_000_000)) {
            captureDepth(frame, camera, seq, ts, now)
        }
    }

    private val depthActive = header.session.depthMode.let { it != null && it != "DISABLED" }

    private fun frameSnapshot(
        session: Session, frame: Frame, camera: Camera, floorY: Float?, seq: Int, ts: Long, now: Long, tracking: String, pose: FloatArray,
    ): Job.FrameLine {
        val planes = session.getAllTrackables(Plane::class.java).map { p ->
            val poly = p.polygon // valido fino al prossimo update: si copia subito
            val start = poly.position()
            val n = poly.remaining() / 2 * 2
            val local = FloatArray(n) { poly.get(start + it) }
            val normal = p.centerPose.yAxis
            PlaneSnap(keyOf(p), p.type.name, p.trackingState.name, p.subsumedBy?.let { keyOf(it) }, poseArray(p.centerPose), normal, p.extentX, p.extentZ, local)
        }
        var cloud: CloudSnap? = null
        try {
            frame.acquirePointCloud().use { pc ->
                if (pc.timestamp != lastCloudTimestamp) {
                    lastCloudTimestamp = pc.timestamp
                    val points = pc.points
                    val ids = pc.ids
                    val pStart = points.position()
                    val iStart = ids.position()
                    val n = minOf(points.remaining() / 4, ids.remaining())
                    cloud = CloudSnap(pc.timestamp, FloatArray(n * 4) { points.get(pStart + it) }, IntArray(n) { ids.get(iStart + it) })
                }
            }
        } catch (e: Exception) {
            missing("points", "error", ts, now, e.javaClass.simpleName)
        }
        val failure = camera.trackingFailureReason.takeIf { it != TrackingFailureReason.NONE }?.name
        return Job.FrameLine(frameIndex++, seq, ts, (now - startedAtNs) / 1_000_000, tracking, failure, pose, poseArray(camera.displayOrientedPose), floorY, depthActive, planes, cloud)
    }

    private fun captureRgb(frame: Frame, camera: Camera, seq: Int, ts: Long, now: Long, tracking: String, pose: FloatArray) {
        if (bytesWritten >= MAX_BYTES) { lastRgbTs = ts; full = true; missing(CaptureStream.RGB, "limit", ts, now); counts.missing(CaptureStream.RGB); return }
        val image: Image = try {
            frame.acquireCameraImage()
        } catch (e: NotYetAvailableException) {
            missing(CaptureStream.RGB, "not-yet-available", ts, now) // si riprova al prossimo frame
            counts.unavailable(CaptureStream.RGB)
            return
        } catch (e: Exception) {
            lastRgbTs = ts
            missing(CaptureStream.RGB, "error", ts, now, describe(e))
            counts.failed(CaptureStream.RGB)
            return
        }
        lastRgbTs = ts
        image.use { img ->
            val w = img.width
            val h = img.height
            val estimate = w.toLong() * h * 3 / 2
            counts.acquired(CaptureStream.RGB)
            if (queuedBytes.get() + estimate > settings.maxQueueBytes) {
                droppedRgb++
                missing(CaptureStream.RGB, "backlog", ts, now)
                counts.missing(CaptureStream.RGB)
                return
            }
            val planes = img.planes
            val y = planes[0]
            val u = planes[1]
            val v = planes[2]
            val yuv = YuvCopy(
                w, h, copy(y.buffer), y.rowStride, copy(u.buffer), u.rowStride, u.pixelStride, copy(v.buffer), v.rowStride, v.pixelStride,
            )
            val k = intrinsics(camera.imageIntrinsics)
            val id = rgbSeq++
            val deltaPrev = lastRgbCaptured?.let { ts - it.second }
            val depthRef = lastDepthCaptured
            lastRgbCaptured = id to ts
            enqueue(
                Job.Rgb(
                    id, seq, ts, img.timestamp, now, deltaPrev, tracking, pose, poseArray(camera.displayOrientedPose), k, w, h, yuv,
                    depthRef?.first, depthRef?.let { ts - it.second },
                ),
            )
        }
    }

    private fun captureDepth(frame: Frame, camera: Camera, seq: Int, ts: Long, now: Long) {
        val image: Image = try {
            frame.acquireDepthImage16Bits()
        } catch (e: NotYetAvailableException) {
            missing(CaptureStream.DEPTH, "not-yet-available", ts, now) // opportunistica: si riprova al prossimo frame, senza aspettare
            counts.unavailable(CaptureStream.DEPTH)
            return
        } catch (e: Exception) {
            lastDepthTryTs = ts
            missing(CaptureStream.DEPTH, "error", ts, now, describe(e))
            counts.failed(CaptureStream.DEPTH)
            return
        }
        image.use { img ->
            val depthTs = img.timestamp
            if (depthTs == lastDepthImageTs) { missing(CaptureStream.DEPTH, "not-new", ts, now); return } // la stessa depth di prima: non è un keyframe nuovo
            lastDepthTryTs = ts
            lastDepthImageTs = depthTs
            val w = img.width
            val h = img.height
            counts.acquired(CaptureStream.DEPTH)
            if (queuedBytes.get() + w.toLong() * h * 4 > settings.maxQueueBytes) {
                droppedDepth++
                missing(CaptureStream.DEPTH, "backlog", ts, now)
                counts.missing(CaptureStream.DEPTH)
                return
            }
            val depth = packRows(img.planes[0], w, h, 2)
            var raw = acquireRaw(frame)
            var confidence = if (raw.data != null) acquireConfidence(frame, raw.width, raw.height) else {
                counts.unavailable(CaptureStream.CONFIDENCE)
                Optional(null, StreamStatus.UNAVAILABLE, "depth raw non disponibile")
            }
            // ARCore può ridare la stessa raw di prima (stesso timestamp e dimensioni): non la si salva di nuovo.
            val rawTs = raw.ts
            if (raw.data != null && rawTs != null && RawDepthDedup.sameSample(rawTs, raw.width, raw.height, lastRaw?.ts, lastRaw?.width, lastRaw?.height)) {
                val detail = "stessa raw del keyframe depth #${lastRaw?.depthSeq} (timestamp $rawTs)"
                counts.duplicate(CaptureStream.RAW_DEPTH)
                raw = Optional(null, StreamStatus.DUPLICATE, detail, rawTs, raw.width, raw.height)
                if (confidence.data != null) counts.duplicate(CaptureStream.CONFIDENCE)
                confidence = Optional(null, if (confidence.data != null) StreamStatus.DUPLICATE else confidence.status, if (confidence.data != null) detail else confidence.detail)
            }
            // La raw ha il suo timestamp (spesso più vecchio della depth filtrata): posa del SUO frame, mai quella della depth filtrata.
            val rawPose = if (raw.data != null && rawTs != null) ring.match(rawTs) else null
            // La depth appartiene al frame la cui immagine l'ha generata: il primo frame con timestamp ≥ quello della depth.
            val owner = ring.frameFor(depthTs)
            // Intrinseche: quelle della texture (stesso campo visivo della depth) alla risoluzione della depth. Non quelle dell'immagine CPU.
            val (k, kSource) = DepthIntrinsics.fromTexture(intrinsics(camera.textureIntrinsics), w, h)
            val id = depthSeq++
            if (raw.data != null && rawTs != null) lastRaw = LastRaw(rawTs, raw.width, raw.height, id)
            val deltaPrev = lastDepthCaptured?.let { depthTs - it.second }
            val rgbRef = lastRgbCaptured
            lastDepthCaptured = id to depthTs
            enqueue(
                Job.Depth(
                    id, seq, depthTs, ts, now, deltaPrev,
                    owner?.pose ?: poseArray(camera.pose), owner?.ts ?: ts, owner?.seq, owner != null,
                    w, h, depth, raw, confidence, k, kSource, rgbRef?.first, rgbRef?.let { depthTs - it.second }, rawPose,
                ),
            )
        }
    }

    /** L'ultima raw salvata: per riconoscere le ripetute. */
    private class LastRaw(val ts: Long, val width: Int, val height: Int, val depthSeq: Int)
    private var lastRaw: LastRaw? = null

    /** Un'immagine facoltativa (depth raw, confidenza): i dati, o lo stato ([StreamStatus]) e il motivo per cui mancano. */
    private class Optional(val data: ByteArray?, val status: String, val detail: String?, val ts: Long? = null, val width: Int = 0, val height: Int = 0)

    private fun acquireRaw(frame: Frame): Optional = try {
        frame.acquireRawDepthImage16Bits().use { r ->
            counts.acquired(CaptureStream.RAW_DEPTH)
            Optional(packRows(r.planes[0], r.width, r.height, 2), StreamStatus.ACQUIRED, null, r.timestamp, r.width, r.height)
        }
    } catch (e: Exception) {
        optionalFailure(CaptureStream.RAW_DEPTH, e)
    }

    private fun acquireConfidence(frame: Frame, w: Int, h: Int): Optional = try {
        frame.acquireRawDepthConfidenceImage().use { c ->
            if (c.width != w || c.height != h) {
                counts.failed(CaptureStream.CONFIDENCE)
                Optional(null, StreamStatus.FAILED, "confidenza ${c.width}×${c.height} diversa dalla depth raw ${w}×$h")
            } else {
                counts.acquired(CaptureStream.CONFIDENCE)
                Optional(packRows(c.planes[0], w, h, 1), StreamStatus.ACQUIRED, null, c.timestamp, w, h)
            }
        }
    } catch (e: Exception) {
        optionalFailure(CaptureStream.CONFIDENCE, e)
    }

    /** "Non ancora pronta" o "non supportata" = non disponibile; il resto è un errore. Il motivo è sempre l'eccezione di ARCore. */
    private fun optionalFailure(stream: String, e: Exception): Optional {
        val unavailable = e is NotYetAvailableException || e.javaClass.simpleName.contains("Unsupported") || e.javaClass.simpleName.contains("NotYetAvailable")
        if (unavailable) counts.unavailable(stream) else counts.failed(stream)
        return Optional(null, if (unavailable) StreamStatus.UNAVAILABLE else StreamStatus.FAILED, describe(e))
    }

    // ---------- Campioni persi e statistiche ----------

    private class MissingAgg(val kind: String, val reason: String, val firstTs: Long, val firstMono: Long, var count: Int, val detail: String?)

    /** I campioni persi di fila si raggruppano e si scrivono circa una volta al secondo (con il primo timestamp e il conteggio). */
    private fun missing(kind: String, reason: String, ts: Long, now: Long, detail: String? = null) {
        val key = "$kind/$reason/${detail ?: ""}"
        val agg = missingAgg[key]
        if (agg == null) missingAgg[key] = MissingAgg(kind, reason, ts, now, 1, detail) else agg.count++
    }

    private fun flushMissing() {
        for (m in missingAgg.values) enqueue(Job.Line(ScanRecordingJson.missingLine(MissingSample(kind = m.kind, reason = m.reason, timestampNs = m.firstTs, monotonicNs = m.firstMono, count = m.count, detail = m.detail))))
        missingAgg.clear()
    }

    private fun emitStats(now: Long) {
        flushMissing()
        val interval = now - statsStartNs
        lastArHz = if (interval > 0) arFrames * 1e9 / interval else 0.0
        enqueue(
            Job.Stats(
                CaptureStats(
                    monotonicNs = now, elapsedMs = (now - startedAtNs) / 1_000_000, drawCalls = drawCalls, arFrames = arFrames,
                    intervalMs = interval / 1_000_000,
                    updateAvgMs = ms(if (drawCalls == 0) 0 else updateSumNs / drawCalls), updateMaxMs = ms(updateMaxNs),
                    captureAvgMs = ms(if (drawCalls == 0) 0 else captureSumNs / drawCalls), captureMaxMs = ms(captureMaxNs),
                    drawGapMaxMs = ms(drawGapMaxNs), queueItems = queue.size, queueBytes = queuedBytes.get(),
                    maxQueueItems = maxQueueItems, maxQueueBytes = maxQueueBytes,
                ),
            ),
        )
        statsStartNs = now
        drawCalls = 0; arFrames = 0; updateSumNs = 0; updateMaxNs = 0; captureSumNs = 0; captureMaxNs = 0; drawGapMaxNs = 0
        maxQueueItems = 0; maxQueueBytes = 0
    }

    private fun enqueue(job: Job) {
        queuedBytes.addAndGet(job.bytes)
        queue.put(job)
    }

    // ---------- Chiusura ----------

    /** Chiude la registrazione: la coda viene scritta tutta dal thread di scrittura, poi la riga finale. Non blocca. */
    fun close(): File {
        synchronized(lock) {
            if (closed.compareAndSet(false, true)) {
                endedAtNs = SystemClock.elapsedRealtimeNanos()
                flushMissing()
                queue.put(Job.End((endedAtNs - startedAtNs) / 1_000_000))
            }
        }
        return directory
    }

    // ---------- Scrittura (thread di scrittura) ----------

    private fun drain() {
        var lastFlush = SystemClock.elapsedRealtime()
        try {
            while (true) {
                val job = queue.take()
                try {
                    write(job)
                } catch (e: Throwable) {
                    // Mai un buco silenzioso: il dato acquisito e non scritto diventa una riga `missing` con la causa.
                    Log.w(TAG, "Scrittura non riuscita", e)
                    writeFailure(job, e)
                } finally {
                    queuedBytes.addAndGet(-job.bytes)
                }
                if (job is Job.End) break
                val now = SystemClock.elapsedRealtime()
                if (job is Job.Stats || now - lastFlush > 1000) { writer.flush(); lastFlush = now }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Thread di scrittura interrotto", e)
        } finally {
            try { writer.flush(); writer.close() } catch (e: Exception) { Log.w(TAG, "Chiusura del recording", e) }
            writing.remove(directory.absoluteFile)
            written.countDown()
        }
    }

    private fun line(s: String) {
        writer.write(s)
        writer.newLine()
        bytesWritten += s.length + 1
    }

    /** Un frame, un RGB o una depth acquisiti e non scritti: riga `missing` "write-failed" con la causa, e il conteggio. */
    private fun writeFailure(job: Job, e: Throwable) {
        val (stream, ts, mono) = when (job) {
            is Job.FrameLine -> Triple(CaptureStream.FRAME, job.ts, 0L)
            is Job.Rgb -> Triple(CaptureStream.RGB, job.ts, job.mono)
            is Job.Depth -> Triple(CaptureStream.DEPTH, job.ts, job.mono)
            else -> return
        }
        counts.failed(stream)
        try { line(ScanRecordingJson.missingLine(CaptureLines.writeFailed(stream, ts, mono, e))) } catch (e2: Exception) { Log.w(TAG, "Riga missing non scritta", e2) }
    }

    private fun write(job: Job) {
        when (job) {
            is Job.Line -> line(job.text)
            is Job.PoseLine -> { line(ScanRecordingJson.poseLine(PoseSample(seq = job.seq, timestampNs = job.ts, monotonicNs = job.mono, tracking = job.tracking, camera = recPose(job.pose)))); poseLines++ }
            is Job.FrameLine -> writeFrame(job)
            is Job.Rgb -> writeRgb(job)
            is Job.Depth -> writeDepth(job)
            is Job.Stats -> line(
                ScanRecordingJson.statsLine(
                    job.stats.copy(
                        writtenRgb = rgbWritten, writtenDepth = depthWritten, droppedRgb = droppedRgb, droppedDepth = droppedDepth, bytesWritten = bytesWritten,
                        rgbWriteAvgMs = ms(if (rgbWriteCount == 0) 0 else rgbWriteSumNs / rgbWriteCount), rgbWriteMaxMs = ms(rgbWriteMaxNs),
                    ),
                ).also { rgbWriteSumNs = 0; rgbWriteMaxNs = 0; rgbWriteCount = 0 },
            )
            is Job.End -> line(
                ScanRecordingJson.endLine(
                    RecordingEnd(
                        frames = frameLines, durationMs = job.durationMs, verticalPlanesObserved = verticalKeys.size, pointSamples = pointSamples,
                        poses = poseLines, rgb = rgbWritten, depth = depthWritten, droppedRgb = droppedRgb, droppedDepth = droppedDepth,
                        drained = queue.isEmpty(), streams = counts.snapshot(),
                    ),
                ),
            )
        }
    }

    private fun writeFrame(j: Job.FrameLine) {
        val planes = j.planes.map { p ->
            if (p.kind == Plane.Type.VERTICAL.name) verticalKeys.add(p.key)
            RecordedPlane(
                key = p.key, kind = p.kind, tracking = p.tracking, subsumedBy = p.subsumedBy, pose = recPose(p.pose),
                normal = listOf(r5(p.normal[0]), r5(p.normal[1]), r5(p.normal[2])), extentX = r5(p.extentX), extentZ = r5(p.extentZ),
                polygon = p.polygon.map { r5(it) },
            )
        }
        verticalCount = verticalKeys.size
        val cloud = j.cloud?.let { c ->
            val n = c.ids.size
            val xyz = ArrayList<Double>(n * 3)
            val conf = ArrayList<Double>(n)
            for (i in 0 until n) {
                xyz.add(r5(c.points[4 * i])); xyz.add(r5(c.points[4 * i + 1])); xyz.add(r5(c.points[4 * i + 2]))
                conf.add(r5(c.points[4 * i + 3]))
            }
            RecordedPointCloud(c.ts, xyz, conf, c.ids.toList())
        }
        val frame = RecordedFrame(
            index = j.index, timestampNs = j.ts, elapsedMs = j.elapsedMs, tracking = j.tracking, failureReason = j.failure,
            camera = recPose(j.pose), cameraDisplay = recPose(j.display), floorY = j.floorY?.let { r5(it) }, depthInUse = j.depthInUse,
            planes = planes, points = cloud, frameSeq = j.seq,
        )
        // Valori non finiti (NaN, ±∞: con la depth attiva sono nella nuvola di ARCore) tolti e contati; se la riga non si può
        // scrivere lo stesso, diventa una riga `missing` con la causa (mai un frame sparito senza traccia).
        val clean = RecordingSanitizer.frame(frame)
        val result = CaptureLines.frame(clean, SystemClock.elapsedRealtimeNanos())
        val text = result.line
        if (text != null) {
            line(text)
            frameLines++
            pointSamples += clean.points?.count ?: 0
            invalidPoints += clean.invalidPoints
            counts.saved(CaptureStream.FRAME)
        } else {
            counts.failed(CaptureStream.FRAME)
            result.missing?.let { line(ScanRecordingJson.missingLine(it)) }
        }
    }

    private fun writeRgb(j: Job.Rgb) {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val path = CaptureDataset.rgbPath(j.id)
        val file = File(directory, path)
        val nv21 = j.yuv.toNv21()
        BufferedOutputStream(FileOutputStream(file), 1 shl 16).use { out ->
            if (!YuvImage(nv21, ImageFormat.NV21, j.width, j.height, null).compressToJpeg(Rect(0, 0, j.width, j.height), settings.jpegQuality, out)) {
                throw IllegalStateException("JPEG non riuscito")
            }
        }
        val size = file.length()
        bytesWritten += size
        line(
            ScanRecordingJson.rgbLine(
                RgbKeyframe(
                    seq = j.id, frameSeq = j.frameSeq, timestampNs = j.ts, imageTimestampNs = j.imageTs, monotonicNs = j.mono, deltaPrevNs = j.deltaPrev,
                    tracking = j.tracking, camera = recPose(j.pose), cameraDisplay = recPose(j.display), intrinsics = j.k, width = j.width, height = j.height,
                    path = path, jpegQuality = settings.jpegQuality, displayRotation = header.screen.displayRotation,
                    sensorOrientationDeg = header.camera?.sensorOrientationDeg, bytes = size, lastDepthSeq = j.lastDepthSeq, lastDepthDeltaNs = j.lastDepthDelta,
                    intrinsicsSource = IntrinsicsSource.CPU_IMAGE, poseMatch = PoseMatch.FRAME, imageToFrameNs = j.ts - j.imageTs,
                ),
            ),
        )
        rgbWritten++
        counts.saved(CaptureStream.RGB)
        val dt = SystemClock.elapsedRealtimeNanos() - t0
        rgbWriteSumNs += dt; rgbWriteMaxNs = max(rgbWriteMaxNs, dt); rgbWriteCount++
    }

    private fun writeDepth(j: Job.Depth) {
        val path = CaptureDataset.depthPath(j.id)
        writeBytes(path, j.depth)
        // Raw e confidenza: facoltative, ognuna con il suo esito (un errore di scrittura qui non fa perdere la depth principale).
        val raw = writeOptional(CaptureStream.RAW_DEPTH, j.raw, CaptureDataset.rawDepthPath(j.id))
        val conf = writeOptional(CaptureStream.CONFIDENCE, j.confidence, CaptureDataset.confidencePath(j.id))
        line(
            ScanRecordingJson.depthLine(
                DepthKeyframe(
                    seq = j.id, frameSeq = j.frameSeq, timestampNs = j.ts, frameTimestampNs = j.frameTs, monotonicNs = j.mono, deltaPrevNs = j.deltaPrev,
                    camera = recPose(j.pose), poseTimestampNs = j.poseTs, poseExact = j.poseExact, width = j.width, height = j.height,
                    path = path, rawPath = raw.first, confidencePath = conf.first, intrinsics = j.k, intrinsicsSource = j.kSource,
                    lastRgbSeq = j.lastRgbSeq, lastRgbDeltaNs = j.lastRgbDelta,
                    poseFrameSeq = j.poseFrameSeq, poseMatch = if (j.poseExact) PoseMatch.FRAME else PoseMatch.READ_FRAME,
                    rawTimestampNs = j.raw.ts, rawWidth = j.raw.width.takeIf { j.raw.data != null }, rawHeight = j.raw.height.takeIf { j.raw.data != null },
                    rawStatus = raw.second, rawDetail = raw.third, confidenceStatus = conf.second, confidenceDetail = conf.third,
                    rawPoseFrameSeq = j.rawPose?.seq, rawPoseMatch = j.rawPose?.kind, rawPoseDeltaNs = j.rawPose?.deltaNs,
                    rawCamera = j.rawPose?.pose?.let { recPose(it) }, rawPoseDetail = j.rawPose?.detail,
                ),
            ),
        )
        depthWritten++
        counts.saved(CaptureStream.DEPTH)
    }

    /** Scrive un'immagine facoltativa: (percorso, stato, motivo). Se non c'era, lo stato e il motivo dell'acquisizione. */
    private fun writeOptional(stream: String, o: Optional, path: String): Triple<String?, String, String?> {
        val data = o.data ?: return Triple(null, o.status, o.detail)
        return try {
            writeBytes(path, data)
            counts.saved(stream)
            Triple(path, StreamStatus.SAVED, null)
        } catch (e: Exception) {
            counts.failed(stream)
            Triple(null, StreamStatus.FAILED, "scrittura: " + describe(e))
        }
    }

    private fun writeBytes(path: String, bytes: ByteArray) {
        FileOutputStream(File(directory, path)).use { it.write(bytes) }
        bytesWritten += bytes.size
    }

    // ---------- Dati in coda ----------

    private class PlaneSnap(
        val key: Int, val kind: String, val tracking: String, val subsumedBy: Int?, val pose: FloatArray, val normal: FloatArray,
        val extentX: Float, val extentZ: Float, val polygon: FloatArray,
    )

    private class CloudSnap(val ts: Long, val points: FloatArray, val ids: IntArray)

    /** Copia dei piani YUV_420_888 (con i loro passi): la conversione in NV21 e il JPEG si fanno sul thread di scrittura. */
    private class YuvCopy(
        val width: Int, val height: Int,
        val y: ByteArray, val yRow: Int,
        val u: ByteArray, val uRow: Int, val uPixel: Int,
        val v: ByteArray, val vRow: Int, val vPixel: Int,
    ) {
        val bytes: Long get() = (y.size + u.size + v.size).toLong()

        fun toNv21(): ByteArray {
            val out = ByteArray(width * height * 3 / 2)
            var o = 0
            for (r in 0 until height) {
                System.arraycopy(y, r * yRow, out, o, width)
                o += width
            }
            for (r in 0 until height / 2) {
                for (c in 0 until width / 2) {
                    out[o++] = v[r * vRow + c * vPixel]
                    out[o++] = u[r * uRow + c * uPixel]
                }
            }
            return out
        }
    }

    private sealed class Job(val bytes: Long) {
        class Line(val text: String) : Job(text.length.toLong())
        class PoseLine(val seq: Int, val ts: Long, val mono: Long, val tracking: String, val pose: FloatArray) : Job(64)
        class FrameLine(
            val index: Int, val seq: Int, val ts: Long, val elapsedMs: Long, val tracking: String, val failure: String?, val pose: FloatArray,
            val display: FloatArray, val floorY: Float?, val depthInUse: Boolean, val planes: List<PlaneSnap>, val cloud: CloudSnap?,
        ) : Job(planes.sumOf { it.polygon.size * 4L + 64 } + (cloud?.points?.size ?: 0) * 8L)
        class Rgb(
            val id: Int, val frameSeq: Int, val ts: Long, val imageTs: Long, val mono: Long, val deltaPrev: Long?, val tracking: String,
            val pose: FloatArray, val display: FloatArray, val k: CameraIntrinsics, val width: Int, val height: Int, val yuv: YuvCopy,
            val lastDepthSeq: Int?, val lastDepthDelta: Long?,
        ) : Job(yuv.bytes)
        class Depth(
            val id: Int, val frameSeq: Int, val ts: Long, val frameTs: Long, val mono: Long, val deltaPrev: Long?, val pose: FloatArray,
            val poseTs: Long, val poseFrameSeq: Int?, val poseExact: Boolean, val width: Int, val height: Int, val depth: ByteArray, val raw: Optional,
            val confidence: Optional, val k: CameraIntrinsics, val kSource: String, val lastRgbSeq: Int?, val lastRgbDelta: Long?,
            val rawPose: PoseRing.RawMatch?,
        ) : Job(depth.size.toLong() + (raw.data?.size ?: 0) + (confidence.data?.size ?: 0))
        class Stats(val stats: CaptureStats) : Job(256)
        class End(val durationMs: Long) : Job(0)
    }

    /** Ultime pose per timestamp: la depth arriva con il timestamp del frame da cui è calcolata, spesso qualche frame prima. */
    private class PoseRing(private val size: Int) {
        class Hit(val ts: Long, val seq: Int, val pose: FloatArray)

        private val ts = LongArray(size)
        private val seqs = IntArray(size)
        private val poses = arrayOfNulls<FloatArray>(size)
        private var next = 0
        private var count = 0

        fun put(t: Long, seq: Int, pose: FloatArray) {
            ts[next] = t
            seqs[next] = seq
            poses[next] = pose
            next = (next + 1) % size
            if (count < size) count++
        }

        /** Posa associata a un'immagine con [FrameAssignment.match]: frame, tipo, posa e delta (posa − immagine), o il motivo se non c'è. */
        class RawMatch(val seq: Int?, val kind: String, val pose: FloatArray?, val deltaNs: Long?, val detail: String?)

        fun match(imageTs: Long): RawMatch {
            val start = (next - count + size) % size
            val times = LongArray(count) { ts[(start + it) % size] }
            val m = FrameAssignment.match(imageTs, times)
            val slot = m.index?.let { (start + it) % size } ?: return RawMatch(null, m.kind, null, null, m.detail)
            return RawMatch(seqs[slot], m.kind, poses[slot], ts[slot] - imageTs, null)
        }

        /** Il frame a cui appartiene un'immagine con timestamp [imageTs] ([FrameAssignment]), se è ancora in memoria. */
        fun frameFor(imageTs: Long): Hit? {
            val start = (next - count + size) % size // il più vecchio: i timestamp crescono nell'ordine di inserimento
            val times = LongArray(count) { ts[(start + it) % size] }
            val i = FrameAssignment.frameFor(imageTs, times) ?: return null
            val slot = (start + i) % size
            return poses[slot]?.let { Hit(ts[slot], seqs[slot], it) }
        }
    }

    private fun keyOf(p: Plane): Int = planeKeys.getOrPut(p) { planeKeys.size }

    companion object {
        private const val TAG = "ScanRecorder"

        /** Cartelle (e file JSONL di M0) delle registrazioni (file dell'app: si recuperano anche con `adb pull`). */
        fun directory(context: android.content.Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "scan-recordings").apply { mkdirs() }

        /** L'ultima registrazione salvata (cartella M0.2 o file M0), anche di una sessione precedente. */
        fun latest(context: android.content.Context): File? =
            directory(context).listFiles { f ->
                f.name.startsWith("scan-") && ((f.isDirectory && File(f, CaptureDataset.RECORDING).isFile) || (f.isFile && f.name.endsWith(".jsonl")))
            }?.maxByOrNull { it.lastModified() }

        /** Frequenze e qualità per modalità (vedi [CaptureMode]). */
        fun settingsFor(mode: String): CaptureSettings = when (mode) {
            CaptureMode.OBJECT -> CaptureSettings(mode, frameIntervalMs = 500, rgbIntervalMs = 500, depthIntervalMs = 500, jpegQuality = 92, maxQueueBytes = MAX_QUEUE_BYTES)
            else -> CaptureSettings(CaptureMode.ENVIRONMENT, frameIntervalMs = 100, rgbIntervalMs = 200, depthIntervalMs = 500, jpegQuality = 85, maxQueueBytes = MAX_QUEUE_BYTES)
        }

        /** Registrazioni il cui thread di scrittura non ha ancora finito. */
        private val writing = ConcurrentHashMap<File, CountDownLatch>()

        /** Aspetta che la registrazione nella cartella sia scritta del tutto (true se lo è). Non dal thread principale. */
        fun awaitWritten(dir: File, timeoutMs: Long): Boolean = writing[dir.absoluteFile]?.await(timeoutMs, TimeUnit.MILLISECONDS) ?: true

        /**
         * Scrive la cartella della registrazione in uno ZIP (dentro una cartella con lo stesso nome), senza caricarla in memoria.
         * Aspetta prima che il thread di scrittura abbia finito. Non dal thread principale.
         */
        fun writeZip(dir: File, out: OutputStream) {
            if (!awaitWritten(dir, 60_000)) throw IllegalStateException("La registrazione è ancora in scrittura")
            ZipOutputStream(BufferedOutputStream(out, 1 shl 16)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                val buffer = ByteArray(1 shl 16)
                for (f in dir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(dir).invariantSeparatorsPath }) {
                    zip.putNextEntry(ZipEntry(dir.name + "/" + f.relativeTo(dir).invariantSeparatorsPath))
                    f.inputStream().use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            zip.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        /** Limite di sicurezza delle righe `frame`: 10 minuti a 10 al secondo. */
        const val MAX_FRAMES = 6000
        /** Spazio massimo di una registrazione (oltre, niente più RGB: registrato come `missing`, motivo `limit`). */
        private const val MAX_BYTES = 2L * 1024 * 1024 * 1024
        /** Memoria massima dei dati in coda di scrittura. */
        private const val MAX_QUEUE_BYTES = 96L * 1024 * 1024
        private const val STATS_INTERVAL_NS = 1_000_000_000L
        /** Pose in memoria per associare le immagini al loro frame: 10 s a 30 Hz (la depth raw di ARCore può essere vecchia di secondi). */
        private const val RING_SIZE = 300

        private fun copy(b: java.nio.ByteBuffer): ByteArray {
            val d = b.duplicate()
            val out = ByteArray(d.remaining())
            d.get(out)
            return out
        }

        /** Righe dell'immagine (con il loro passo) impacchettate senza padding, nell'ordine dei byte in memoria (little-endian). */
        private fun packRows(plane: Image.Plane, w: Int, h: Int, bytesPerPixel: Int): ByteArray {
            val src = plane.buffer.duplicate()
            val start = src.position()
            val row = w * bytesPerPixel
            val out = ByteArray(row * h)
            if (plane.pixelStride == bytesPerPixel) {
                for (r in 0 until h) {
                    src.position(start + r * plane.rowStride)
                    src.get(out, r * row, row)
                }
            } else {
                for (r in 0 until h) for (c in 0 until w) for (b in 0 until bytesPerPixel) {
                    out[r * row + c * bytesPerPixel + b] = src.get(start + r * plane.rowStride + c * plane.pixelStride + b)
                }
            }
            return out
        }

        private fun poseArray(p: Pose) = floatArrayOf(p.tx(), p.ty(), p.tz(), p.qx(), p.qy(), p.qz(), p.qw())

        private fun recPose(p: FloatArray) = RecPose(r5(p[0]), r5(p[1]), r5(p[2]), r5(p[3]), r5(p[4]), r5(p[5]), r5(p[6]))

        internal fun intrinsics(k: ArIntrinsics): CameraIntrinsics {
            val f = k.focalLength
            val c = k.principalPoint
            val d = k.imageDimensions
            return CameraIntrinsics(r5(f[0]), r5(f[1]), r5(c[0]), r5(c[1]), d[0], d[1])
        }

        private fun r5(v: Float): Double = round(v.toDouble() * 100_000.0) / 100_000.0

        /** Classe e messaggio dell'eccezione, per il motivo registrato nel dataset. */
        private fun describe(e: Throwable): String = e.javaClass.simpleName + (e.message?.let { ": " + it.take(200) } ?: "")
        private fun ms(ns: Long): Double = round(ns / 10_000.0) / 100.0
    }
}
