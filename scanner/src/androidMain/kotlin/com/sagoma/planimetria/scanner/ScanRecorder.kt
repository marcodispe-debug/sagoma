package com.sagoma.planimetria.scanner

import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Camera
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.RecordedFrame
import com.sagoma.planimetria.scan.recording.RecordedPlane
import com.sagoma.planimetria.scan.recording.RecordedPointCloud
import com.sagoma.planimetria.scan.recording.RecordingEnd
import com.sagoma.planimetria.scan.recording.RecordingHeader
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.round

/**
 * Registra una sessione di scansione in un file JSONL (formato in `core/.../scan/recording`) per riprodurla sul computer.
 * Solo osserva: non cambia niente nel rilevamento. Si scrive una riga per frame, al massimo ogni [RecordingHeader.recordIntervalMs],
 * e il file resta leggibile anche se l'app viene interrotta.
 *
 * Tutto è nel mondo ARCore, senza conversioni: pose e punti nel mondo, poligoni dei piani nel sistema locale del piano.
 * I metodi sono sincronizzati: [record] gira sul thread di disegno, il resto sul thread principale.
 */
class ScanRecorder internal constructor(
    val file: File,
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
    )

    private val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8))
    private val startedAt = SystemClock.elapsedRealtime()
    private var lastRecorded = -1_000_000L
    private var lastCloudTimestamp = Long.MIN_VALUE
    private var frames = 0
    private var pointSamples = 0
    private var closed = false
    private var full = false
    private var endedAt = 0L
    private val planeKeys = HashMap<Plane, Int>()
    private val verticalKeys = HashSet<Int>()

    init {
        writer.write(ScanRecordingJson.headerLine(header))
        writer.newLine()
        writer.flush()
    }

    @Synchronized
    fun stats(): Stats = Stats(
        frames, verticalKeys.size, pointSamples,
        if (closed) endedAt - startedAt else SystemClock.elapsedRealtime() - startedAt,
        depthSupported, file, closed, full,
    )

    /** Registra il frame (se è passato abbastanza tempo dall'ultimo). Non lancia eccezioni: un errore qui non deve fermare la scansione. */
    @Synchronized
    fun record(session: Session, frame: Frame, camera: Camera, floorY: Float?) {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecorded < header.recordIntervalMs) return
        if (frames >= MAX_FRAMES) { full = true; return }
        lastRecorded = now
        try {
            val planes = session.getAllTrackables(Plane::class.java).map { recordedPlane(it) }
            for (p in planes) if (p.kind == Plane.Type.VERTICAL.name) verticalKeys.add(p.key)
            val cloud = recordedPointCloud(frame)
            if (cloud != null) pointSamples += cloud.count
            val failure = camera.trackingFailureReason.takeIf { it != TrackingFailureReason.NONE }?.name
            val f = RecordedFrame(
                index = frames,
                timestampNs = frame.timestamp,
                elapsedMs = now - startedAt,
                tracking = camera.trackingState.name,
                failureReason = failure,
                camera = recPose(camera.pose),
                cameraDisplay = recPose(camera.displayOrientedPose),
                floorY = floorY?.let { r5(it.toDouble()) },
                depthInUse = false, // la configurazione ha la profondità disattivata
                planes = planes,
                points = cloud,
            )
            writer.write(ScanRecordingJson.frameLine(f))
            writer.newLine()
            frames++
            if (frames % FLUSH_EVERY == 0) writer.flush()
        } catch (e: Throwable) {
            Log.w(TAG, "Frame non registrato", e)
        }
    }

    /** Chiude il file con la riga finale. Si può chiamare più volte. */
    @Synchronized
    fun close(): File {
        if (!closed) {
            closed = true
            endedAt = SystemClock.elapsedRealtime()
            try {
                writer.write(ScanRecordingJson.endLine(RecordingEnd(frames = frames, durationMs = endedAt - startedAt, verticalPlanesObserved = verticalKeys.size, pointSamples = pointSamples)))
                writer.newLine()
                writer.flush()
                writer.close()
            } catch (e: Exception) {
                Log.w(TAG, "Chiusura del recording", e)
            }
        }
        return file
    }

    private fun keyOf(p: Plane): Int = planeKeys.getOrPut(p) { planeKeys.size }

    private fun recordedPlane(p: Plane): RecordedPlane {
        val pose = p.centerPose
        val normal = pose.yAxis // asse Y locale = normale del piano, nel mondo
        val poly = p.polygon // x, z locali, a coppie; valido fino al prossimo aggiornamento: lo si copia subito
        val start = poly.position()
        val count = poly.remaining() / 2 * 2
        val local = ArrayList<Double>(count)
        for (i in 0 until count) local.add(r5(poly.get(start + i).toDouble()))
        return RecordedPlane(
            key = keyOf(p),
            kind = p.type.name,
            tracking = p.trackingState.name,
            subsumedBy = p.subsumedBy?.let { keyOf(it) },
            pose = recPose(pose),
            normal = listOf(r5(normal[0].toDouble()), r5(normal[1].toDouble()), r5(normal[2].toDouble())),
            extentX = r5(p.extentX.toDouble()),
            extentZ = r5(p.extentZ.toDouble()),
            polygon = local,
        )
    }

    /**
     * Nuvola di punti del frame, solo se è cambiata dall'ultima registrata. `getPoints()` dà quattro valori per punto
     * (x, y, z, confidenza), `getIds()` un identificatore persistente per punto; la nuvola va rilasciata (`release`) sempre.
     */
    private fun recordedPointCloud(frame: Frame): RecordedPointCloud? {
        val cloud = try { frame.acquirePointCloud() } catch (e: Exception) { return null }
        try {
            val ts = cloud.timestamp
            if (ts == lastCloudTimestamp) return null
            lastCloudTimestamp = ts
            val points = cloud.points
            val ids = cloud.ids
            val pStart = points.position()
            val iStart = ids.position()
            val n = minOf(points.remaining() / 4, ids.remaining())
            val xyz = ArrayList<Double>(n * 3)
            val confidence = ArrayList<Double>(n)
            val idList = ArrayList<Int>(n)
            for (i in 0 until n) {
                xyz.add(r5(points.get(pStart + 4 * i).toDouble()))
                xyz.add(r5(points.get(pStart + 4 * i + 1).toDouble()))
                xyz.add(r5(points.get(pStart + 4 * i + 2).toDouble()))
                confidence.add(r5(points.get(pStart + 4 * i + 3).toDouble()))
                idList.add(ids.get(iStart + i))
            }
            return RecordedPointCloud(ts, xyz, confidence, idList)
        } finally {
            cloud.release()
        }
    }

    private fun recPose(p: Pose) = RecPose(
        r5(p.tx().toDouble()), r5(p.ty().toDouble()), r5(p.tz().toDouble()),
        r5(p.qx().toDouble()), r5(p.qy().toDouble()), r5(p.qz().toDouble()), r5(p.qw().toDouble()),
    )

    private fun r5(v: Double): Double = round(v * 100_000.0) / 100_000.0

    companion object {
        private const val TAG = "ScanRecorder"

        /** Cartella dei recording (file dell'app: si recupera anche con `adb pull`). */
        fun directory(context: android.content.Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "scan-recordings").apply { mkdirs() }

        /** L'ultimo recording salvato (anche di una sessione precedente), se ce n'è uno. */
        fun latest(context: android.content.Context): File? =
            directory(context).listFiles { f -> f.isFile && f.name.startsWith("scan-") && f.name.endsWith(".jsonl") }
                ?.maxByOrNull { it.lastModified() }

        /** Un frame ogni tanto, non 30 al secondo: bastano per analizzare e il file resta di pochi MB. */
        const val DEFAULT_INTERVAL_MS = 100L
        /** Limite di sicurezza: 10 minuti a 10 frame al secondo. */
        const val MAX_FRAMES = 6000
        private const val FLUSH_EVERY = 10
    }
}
