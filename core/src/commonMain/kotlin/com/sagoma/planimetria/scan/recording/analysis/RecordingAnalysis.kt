package com.sagoma.planimetria.scan.recording.analysis

import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.RecordedPlane
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.rotate
import com.sagoma.planimetria.scan.recording.transformPoint
import com.sagoma.planimetria.geometry.toDegrees
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Analisi OFFLINE di una registrazione di scansione (formato JSONL v1): cosa ha visto davvero ARCore, per quanto tempo e dove.
 * Kotlin puro, deterministico, senza Android né ARCore. Non ricostruisce il perimetro e non cambia nessun algoritmo di
 * rilevamento: descrive i dati. Tutto è nel mondo ARCore (metri, y verso l'alto); in pianta x → destra e z → basso, come la
 * pianta di Sagoma (ma in metri e con origine/orientamento arbitrari).
 */

/** Statistiche semplici di una serie di numeri (vuota: tutti zero e `count` = 0). */
data class Dist(val count: Int, val min: Double, val max: Double, val mean: Double, val median: Double, val p10: Double, val p90: Double) {
    companion object {
        val EMPTY = Dist(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        fun of(values: List<Double>): Dist {
            if (values.isEmpty()) return EMPTY
            val s = values.sorted()
            val n = s.size
            val median = if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
            fun pct(p: Double) = s[min(n - 1, max(0, ceil(p * n).toInt() - 1))] // rango più vicino
            return Dist(n, s.first(), s.last(), s.sum() / n, median, pct(0.10), pct(0.90))
        }
    }
}

/** Il segmento in pianta di un piano verticale e le sue misure, ricavati da posa e poligono locale (senza fidarsi di altro). */
data class PlaneGeometry(
    /** Estremi dell'impronta orizzontale del poligono sul piano (metri, mondo: x e z). */
    val a: ArXZ,
    val b: ArXZ,
    val lengthM: Double,
    /** Direzione del muro in pianta, gradi da 0 (incluso) a 180 (escluso): non ha verso. */
    val headingDeg: Double,
    /** Direzione della normale in pianta (gradi da 0 a 360, x → 0°, z → 90°). */
    val normalHeadingDeg: Double,
    /** Inclinazione della normale rispetto all'orizzontale (0 = piano perfettamente verticale). */
    val tiltDeg: Double,
    val centerX: Double,
    val centerZ: Double,
    val minY: Double,
    val maxY: Double,
    /** Angolo (gradi) tra la normale registrata e quella ricavata dalla posa: deve essere ~0. */
    val normalMismatchDeg: Double?,
) {
    val heightM: Double get() = maxY - minY
}

/** Storia di un piano (per chiave) lungo tutta la registrazione. */
data class PlaneTrack(
    val key: Int,
    val kind: String,
    val firstFrame: Int,
    val lastFrame: Int,
    val firstMs: Long,
    val lastMs: Long,
    val observedFrames: Int,
    val trackingFrames: Int,
    val pausedFrames: Int,
    val stoppedFrames: Int,
    /** Piano che lo ha assorbito (l'ultimo visto), se è stato assorbito. */
    val subsumedBy: Int?,
    val firstSubsumedMs: Long?,
    val subsumedFrames: Int,
    /** Non compare nell'ultimo frame della registrazione. */
    val disappeared: Boolean,
    /** Quante volte è ricomparso dopo essere sparito. */
    val reappearances: Int,
    val lengthFirstM: Double?,
    val lengthLastM: Double?,
    val lengthMaxM: Double?,
    val heightMaxM: Double?,
    /** Geometria all'ultima osservazione (null per i piani orizzontali). */
    val last: PlaneGeometry?,
) {
    /** Tempo tra la prima e l'ultima osservazione (ms). */
    val persistenceMs: Long get() = lastMs - firstMs
    val isVertical: Boolean get() = kind == "VERTICAL"
}

data class TrajectoryPoint(val frame: Int, val elapsedMs: Long, val x: Double, val y: Double, val z: Double, val tracking: String)

data class HeatGrid(val cellM: Double, val minX: Double, val minZ: Double, val cols: Int, val rows: Int, val counts: List<Int>) {
    fun count(col: Int, row: Int) = counts[row * cols + col]
    val maxCount: Int get() = counts.maxOrNull() ?: 0
}

data class PointCloudStats(
    val updates: Int,
    val totalSamples: Int,
    val distinctIds: Int,
    val confidence: Dist,
    val pointsPerUpdate: Dist,
    /** Densità in pianta dei punti distinti (ultima posizione di ogni id). Null se non ci sono punti. */
    val heat: HeatGrid?,
)

data class FloorStats(
    val series: List<Pair<Long, Double>>,
    val stats: Dist,
    /** Ultimo valore meno primo (m): deriva della stima. */
    val driftM: Double,
    val framesWithoutFloor: Int,
)

data class TrackingStats(
    val framesByState: List<Pair<String, Int>>,
    val failureReasons: List<Pair<String, Int>>,
    /** Quante volte il tracking è passato da TRACKING a un altro stato. */
    val losses: Int,
    val longestNotTrackingMs: Long,
)

data class RecordingAnalysis(
    val deviceLabel: String,
    val frames: Int,
    val durationMs: Long,
    val frameIntervalMs: Dist,
    val trajectory: List<TrajectoryPoint>,
    /** Distanza percorsa dalla camera in pianta (xz) tra frame consecutivi con tracking (m). */
    val distanceM: Double,
    val distance3dM: Double,
    val speed: Dist,
    val tracking: TrackingStats,
    val floor: FloorStats,
    val planes: List<PlaneTrack>,
    val pointCloud: PointCloudStats,
    val depthSupported: Boolean?,
    val depthInUseFrames: Int,
    val normalMismatchMaxDeg: Double?,
) {
    val verticalPlanes: List<PlaneTrack> get() = planes.filter { it.isVertical }

    /** Piani verticali visti almeno per `minPersistenceMs` (0 = tutti). */
    fun vertical(minPersistenceMs: Long = 0): List<PlaneTrack> = verticalPlanes.filter { it.persistenceMs >= minPersistenceMs }

    val subsumedVertical: Int get() = verticalPlanes.count { it.subsumedBy != null }

    fun lengths(minPersistenceMs: Long = 0): Dist = Dist.of(vertical(minPersistenceMs).mapNotNull { it.last?.lengthM })
    fun heights(minPersistenceMs: Long = 0): Dist = Dist.of(vertical(minPersistenceMs).mapNotNull { it.last?.heightM })

    /** Istogramma delle direzioni dei muri in pianta: (inizio del bin in gradi, numero di piani, lunghezza totale m). */
    fun headingHistogram(binDeg: Int = 15, minPersistenceMs: Long = 0): List<Triple<Int, Int, Double>> {
        val bins = 180 / binDeg
        val counts = IntArray(bins)
        val lengths = DoubleArray(bins)
        for (p in vertical(minPersistenceMs)) {
            val g = p.last ?: continue
            val i = min(bins - 1, (g.headingDeg / binDeg).toInt())
            counts[i]++; lengths[i] += g.lengthM
        }
        return List(bins) { Triple(it * binDeg, counts[it], lengths[it]) }
    }
}

object RecordingAnalyzer {
    private const val HEAT_CELL_M = 0.10

    fun analyze(recording: ScanRecording): RecordingAnalysis {
        val frames = recording.frames
        val h = recording.header

        // ---- Tempo e traiettoria ----
        val t0 = frames.firstOrNull()?.elapsedMs ?: 0L
        val duration = recording.end?.durationMs ?: ((frames.lastOrNull()?.elapsedMs ?: 0L) - t0)
        val intervals = frames.zipWithNext { a, b -> (b.elapsedMs - a.elapsedMs).toDouble() }
        val trajectory = frames.mapNotNull { f -> f.camera?.let { TrajectoryPoint(f.index, f.elapsedMs, it.x, it.y, it.z, f.tracking) } }
        var dist = 0.0
        var dist3 = 0.0
        val speeds = ArrayList<Double>()
        var prev: RecordedFrameRef? = null
        for (f in frames) {
            val c = f.camera
            if (c != null && f.tracking == "TRACKING") {
                val p = prev
                if (p != null) {
                    val dx = c.x - p.x; val dy = c.y - p.y; val dz = c.z - p.z
                    val step = sqrt(dx * dx + dz * dz)
                    dist += step
                    dist3 += sqrt(dx * dx + dy * dy + dz * dz)
                    val dt = (f.timestampNs - p.tNs) / 1e9
                    if (dt > 0) speeds.add(sqrt(dx * dx + dy * dy + dz * dz) / dt)
                }
                prev = RecordedFrameRef(c.x, c.y, c.z, f.timestampNs)
            } else prev = null // il tracking si è interrotto: la distanza non si accumula sul salto
        }

        // ---- Tracking ----
        val byState = LinkedHashMap<String, Int>()
        val reasons = LinkedHashMap<String, Int>()
        var losses = 0
        var longestOut = 0L
        var outStart: Long? = null
        var wasTracking: Boolean? = null
        for (f in frames) {
            byState[f.tracking] = (byState[f.tracking] ?: 0) + 1
            f.failureReason?.let { reasons[it] = (reasons[it] ?: 0) + 1 }
            val tracking = f.tracking == "TRACKING"
            if (wasTracking == true && !tracking) losses++
            if (!tracking) { if (outStart == null) outStart = f.elapsedMs } else if (outStart != null) { longestOut = max(longestOut, f.elapsedMs - outStart); outStart = null }
            wasTracking = tracking
        }
        if (outStart != null) longestOut = max(longestOut, (frames.last().elapsedMs) - outStart)
        val tracking = TrackingStats(byState.entries.sortedBy { it.key }.map { it.key to it.value }, reasons.entries.sortedBy { it.key }.map { it.key to it.value }, losses, longestOut)

        // ---- Pavimento ----
        val floorSeries = frames.mapNotNull { f -> f.floorY?.let { f.elapsedMs to it } }
        val floor = FloorStats(
            floorSeries, Dist.of(floorSeries.map { it.second }),
            if (floorSeries.size >= 2) floorSeries.last().second - floorSeries.first().second else 0.0,
            frames.count { it.floorY == null },
        )

        // ---- Piani ----
        class Acc(val key: Int, val kind: String, val firstFrame: Int, val firstMs: Long) {
            var lastFrame = firstFrame; var lastMs = firstMs; var observed = 0
            var tracking = 0; var paused = 0; var stopped = 0
            var subsumedBy: Int? = null; var firstSubsumedMs: Long? = null; var subsumedFrames = 0
            var lastSeenFrameIndex = -1; var reappear = 0
            var lengthFirst: Double? = null; var lengthMax: Double? = null; var heightMax: Double? = null
            var last: PlaneGeometry? = null
        }
        val acc = LinkedHashMap<Int, Acc>()
        var mismatchMax: Double? = null
        for ((fi, f) in frames.withIndex()) {
            for (p in f.planes) {
                val a = acc.getOrPut(p.key) { Acc(p.key, p.kind, f.index, f.elapsedMs) }
                if (a.observed > 0 && a.lastSeenFrameIndex != fi - 1) a.reappear++
                a.lastSeenFrameIndex = fi
                a.observed++
                a.lastFrame = f.index; a.lastMs = f.elapsedMs
                when (p.tracking) { "TRACKING" -> a.tracking++; "PAUSED" -> a.paused++; else -> a.stopped++ }
                if (p.subsumedBy != null) {
                    a.subsumedBy = p.subsumedBy; a.subsumedFrames++
                    if (a.firstSubsumedMs == null) a.firstSubsumedMs = f.elapsedMs
                }
                val g = geometry(p)
                if (g != null) {
                    if (a.lengthFirst == null) a.lengthFirst = g.lengthM
                    a.lengthMax = max(a.lengthMax ?: 0.0, g.lengthM)
                    a.heightMax = max(a.heightMax ?: 0.0, g.heightM)
                    a.last = g
                    g.normalMismatchDeg?.let { mismatchMax = max(mismatchMax ?: 0.0, it) }
                }
            }
        }
        val lastIndex = frames.lastIndex
        val planes = acc.values.sortedBy { it.key }.map {
            PlaneTrack(
                key = it.key, kind = it.kind, firstFrame = it.firstFrame, lastFrame = it.lastFrame, firstMs = it.firstMs, lastMs = it.lastMs,
                observedFrames = it.observed, trackingFrames = it.tracking, pausedFrames = it.paused, stoppedFrames = it.stopped,
                subsumedBy = it.subsumedBy, firstSubsumedMs = it.firstSubsumedMs, subsumedFrames = it.subsumedFrames,
                disappeared = it.lastSeenFrameIndex != lastIndex, reappearances = it.reappear,
                lengthFirstM = it.lengthFirst, lengthLastM = it.last?.lengthM, lengthMaxM = it.lengthMax, heightMaxM = it.heightMax, last = it.last,
            )
        }

        // ---- Nuvola di punti ----
        val latest = LinkedHashMap<Int, DoubleArray>() // id → x, y, z, confidenza (ultima vista)
        var updates = 0
        var samples = 0
        val perUpdate = ArrayList<Double>()
        val conf = ArrayList<Double>()
        for (f in frames) {
            val pc = f.points ?: continue
            if (!pc.isConsistent) continue
            updates++
            samples += pc.count
            perUpdate.add(pc.count.toDouble())
            for (i in 0 until pc.count) {
                conf.add(pc.confidence[i])
                latest[pc.ids[i]] = doubleArrayOf(pc.xyz[3 * i], pc.xyz[3 * i + 1], pc.xyz[3 * i + 2], pc.confidence[i])
            }
        }
        val pointCloud = PointCloudStats(updates, samples, latest.size, Dist.of(conf), Dist.of(perUpdate), heat(latest.values))

        return RecordingAnalysis(
            deviceLabel = listOfNotNull(h.device.manufacturer, h.device.model).joinToString(" ").ifBlank { "dispositivo sconosciuto" },
            frames = frames.size, durationMs = duration, frameIntervalMs = Dist.of(intervals), trajectory = trajectory,
            distanceM = dist, distance3dM = dist3, speed = Dist.of(speeds), tracking = tracking, floor = floor, planes = planes,
            pointCloud = pointCloud, depthSupported = h.session.depthSupported, depthInUseFrames = frames.count { it.depthInUse },
            normalMismatchMaxDeg = mismatchMax,
        )
    }

    private class RecordedFrameRef(val x: Double, val y: Double, val z: Double, val tNs: Long)

    private fun heat(points: Collection<DoubleArray>): HeatGrid? {
        if (points.isEmpty()) return null
        var minX = Double.MAX_VALUE; var minZ = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        for (p in points) { minX = min(minX, p[0]); maxX = max(maxX, p[0]); minZ = min(minZ, p[2]); maxZ = max(maxZ, p[2]) }
        val x0 = floor(minX / HEAT_CELL_M) * HEAT_CELL_M
        val z0 = floor(minZ / HEAT_CELL_M) * HEAT_CELL_M
        val cols = ((maxX - x0) / HEAT_CELL_M).toInt() + 1
        val rows = ((maxZ - z0) / HEAT_CELL_M).toInt() + 1
        if (cols.toLong() * rows > 4_000_000L) return null // nuvola enorme o isolata: niente heatmap
        val counts = IntArray(cols * rows)
        for (p in points) counts[((p[2] - z0) / HEAT_CELL_M).toInt() * cols + ((p[0] - x0) / HEAT_CELL_M).toInt()]++
        return HeatGrid(HEAT_CELL_M, x0, z0, cols, rows, counts.toList())
    }

    /** Geometria di un piano non orizzontale (null se la normale è verticale: piano orizzontale). */
    fun geometry(p: RecordedPlane): PlaneGeometry? {
        val n = p.polygon.size / 2
        if (n < 3) return null
        val normal = p.pose.rotate(0.0, 1.0, 0.0)
        val hl = sqrt(normal.x * normal.x + normal.z * normal.z)
        if (hl < 1e-6) return null
        val nx = normal.x / hl
        val nz = normal.z / hl
        val tx = -nz
        val tz = nx
        var smin = Double.MAX_VALUE; var smax = -Double.MAX_VALUE
        var off = 0.0
        var ymin = Double.MAX_VALUE; var ymax = -Double.MAX_VALUE
        var cx = 0.0; var cz = 0.0
        for (i in 0 until n) {
            val w = p.pose.transformPoint(p.polygon[2 * i], 0.0, p.polygon[2 * i + 1])
            val s = w.x * tx + w.z * tz
            smin = min(smin, s); smax = max(smax, s)
            off += w.x * nx + w.z * nz
            ymin = min(ymin, w.y); ymax = max(ymax, w.y)
            cx += w.x; cz += w.z
        }
        off /= n
        val a = ArXZ(tx * smin + nx * off, tz * smin + nz * off)
        val b = ArXZ(tx * smax + nx * off, tz * smax + nz * off)
        val heading = (((toDegrees(atan2(tz, tx)) % 180.0) + 180.0) % 180.0).let { if (it >= 180.0) 0.0 else it }
        val normalHeading = ((toDegrees(atan2(nz, nx)) % 360.0) + 360.0) % 360.0
        val tilt = toDegrees(asin(min(1.0, abs(normal.y))))
        val mismatch = if (p.normal.size == 3) {
            val dot = p.normal[0] * normal.x + p.normal[1] * normal.y + p.normal[2] * normal.z
            val len = sqrt(p.normal[0] * p.normal[0] + p.normal[1] * p.normal[1] + p.normal[2] * p.normal[2])
            if (len < 1e-9) null else toDegrees(acos((dot / len).coerceIn(-1.0, 1.0)))
        } else null
        return PlaneGeometry(a, b, a.distanceTo(b), heading, normalHeading, tilt, cx / n, cz / n, ymin, ymax, mismatch)
    }
}
