package com.sagoma.planimetria.scan.recording

import kotlin.math.abs

/**
 * Indice di una registrazione M0.2: dal frame ARCore alla posa, e tra RGB e depth. La sincronizzazione è per frame: un'immagine
 * appartiene a un frame ARCore e prende la posa di quel frame (vedi [FrameAssignment]). Funziona anche con le registrazioni M0
 * (senza righe `pose`: usa le pose dei frame).
 */
class CaptureIndex(private val recording: ScanRecording) {
    /** Pose ordinate per timestamp (righe `pose`, o le pose dei frame se mancano). */
    private val poses: List<PoseSample> = recording.poses.ifEmpty {
        recording.frames.mapNotNull { f -> f.camera?.let { PoseSample(seq = f.frameSeq ?: f.index, timestampNs = f.timestampNs, tracking = f.tracking, camera = it) } }
    }.sortedBy { it.timestampNs }
    private val poseTimes = LongArray(poses.size) { poses[it].timestampNs }
    private val bySeq: Map<Int, PoseSample> = poses.associateBy { it.seq }
    private val rgbTimes = recording.rgb.sortedBy { it.timestampNs }
    private val depthTimes = recording.depth.sortedBy { it.timestampNs }

    /** Elemento trovato e differenza di tempo (trovato − richiesto, ns). [exact]: associazione certa (stesso frame). */
    data class Match<T>(val item: T, val deltaNs: Long, val exact: Boolean = deltaNs == 0L)

    /** Posa con timestamp più vicino a [timestampNs]. */
    fun poseAt(timestampNs: Long): Match<PoseSample>? = nearest(poseTimes, timestampNs)?.let { Match(poses[it], poses[it].timestampNs - timestampNs) }

    /** La posa del frame ARCore [seq], se registrata. */
    fun poseOfFrame(seq: Int): PoseSample? = bySeq[seq]

    /** Il frame ARCore a cui appartiene un'immagine con timestamp [imageTs] ([FrameAssignment]). */
    fun frameOfImage(imageTs: Long): PoseSample? = FrameAssignment.frameFor(imageTs, poseTimes)?.let { poses[it] }

    /**
     * La posa del frame dell'immagine RGB: l'immagine è letta dal frame [RgbKeyframe.frameSeq], quindi è esatta se quel frame è
     * registrato. Il delta è timestamp del frame − timestamp dell'immagine (l'offset normale del sensore, pochi ms).
     */
    fun poseFor(rgb: RgbKeyframe): Match<PoseSample>? {
        bySeq[rgb.frameSeq]?.let { return Match(it, it.timestampNs - rgb.imageTimestampNs, exact = true) }
        return poseAt(rgb.timestampNs)?.let { it.copy(exact = false) }
    }

    /**
     * La posa del frame da cui viene la depth: [DepthKeyframe.poseFrameSeq] se registrato dal telefono, altrimenti il frame che si
     * ricava dal timestamp della depth ([frameOfImage]); in mancanza, la posa più vicina (non esatta).
     */
    fun poseFor(depth: DepthKeyframe): Match<PoseSample>? {
        val p = depth.poseFrameSeq?.let { bySeq[it] } ?: frameOfImage(depth.timestampNs)
        if (p != null) return Match(p, p.timestampNs - depth.timestampNs, exact = true)
        return poseAt(depth.timestampNs)?.let { it.copy(exact = false) }
    }

    /** Posa della depth raw ([DepthKeyframe.rawTimestampNs]) e come è stata associata ([PoseMatch]). */
    data class RawPose(val pose: PoseSample?, val kind: String, val deltaNs: Long?, val detail: String?)

    /**
     * La posa della depth raw dal SUO timestamp: quella registrata dal telefono ([DepthKeyframe.rawPoseFrameSeq]) se c'è,
     * altrimenti ricavata qui con [FrameAssignment.match] sulle pose della registrazione. Null se la raw non c'è.
     */
    fun rawPoseFor(depth: DepthKeyframe): RawPose? {
        val rawTs = depth.rawTimestampNs ?: return null
        if (depth.rawPath == null) return null // solo le raw salvate (non le ripetute né quelle mancanti)
        depth.rawPoseFrameSeq?.let { seq -> bySeq[seq]?.let { return RawPose(it, depth.rawPoseMatch ?: PoseMatch.FRAME, it.timestampNs - rawTs, null) } }
        if (depth.rawPoseMatch == PoseMatch.UNAVAILABLE && recording.poses.isEmpty()) return RawPose(null, PoseMatch.UNAVAILABLE, null, depth.rawPoseDetail)
        val m = FrameAssignment.match(rawTs, poseTimes)
        val p = m.index?.let { poses[it] }
        return RawPose(p, m.kind, p?.let { it.timestampNs - rawTs }, m.detail)
    }

    /** La depth con timestamp più vicino all'immagine RGB, e differenza (depth − RGB, ns). */
    fun depthFor(rgb: RgbKeyframe): Match<DepthKeyframe>? =
        nearestBy(depthTimes, rgb.timestampNs) { it.timestampNs }?.let { Match(it, it.timestampNs - rgb.timestampNs) }

    /** L'immagine RGB con timestamp più vicino alla depth, e differenza (RGB − depth, ns). */
    fun rgbFor(depth: DepthKeyframe): Match<RgbKeyframe>? =
        nearestBy(rgbTimes, depth.timestampNs) { it.timestampNs }?.let { Match(it, it.timestampNs - depth.timestampNs) }

    private fun nearest(times: LongArray, t: Long): Int? {
        if (times.isEmpty()) return null
        var lo = 0
        var hi = times.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < t) lo = mid + 1 else hi = mid
        }
        // lo = primo >= t (o l'ultimo): confronta con il precedente.
        return if (lo > 0 && abs(times[lo - 1] - t) <= abs(times[lo] - t)) lo - 1 else lo
    }

    private fun <T> nearestBy(sorted: List<T>, t: Long, time: (T) -> Long): T? {
        if (sorted.isEmpty()) return null
        val times = LongArray(sorted.size) { time(sorted[it]) }
        return nearest(times, t)?.let { sorted[it] }
    }
}

/**
 * Conteggi per stream di una registrazione: quelli del telefono ([RecordingEnd.streams]) se ci sono, altrimenti ricostruiti dai
 * dati. In entrambi i casi si aggiungono le perdite NON spiegate: buchi nella numerazione (frame, RGB, depth sono numerati
 * all'acquisizione) che non corrispondono a nessuna riga `missing`. Così un frame acquisito e non scritto non passa mai per "0 persi".
 */
object CaptureStreams {
    /** Motivi → categoria ([StreamStatus]); "not-new" non è una perdita (la stessa depth di prima). */
    fun category(reason: String): String? = when (reason) {
        "not-yet-available", "unavailable" -> StreamStatus.UNAVAILABLE
        "error", "write-failed" -> StreamStatus.FAILED
        "backlog", "limit" -> StreamStatus.MISSING
        else -> null
    }

    data class Counts(val counts: StreamCounts, val unexplained: Int, val fromDevice: Boolean)

    fun of(r: ScanRecording): Map<String, Counts> {
        val device = r.end?.streams
        return CaptureStream.ALL.associateWith { s ->
            val numbered: List<Int> = when (s) {
                CaptureStream.FRAME -> r.frames.map { it.index }
                CaptureStream.RGB -> r.rgb.map { it.seq }
                CaptureStream.DEPTH -> r.depth.map { it.seq }
                else -> emptyList()
            }
            val lost = r.missing.filter { it.kind == s }
            val failedAfter = lost.filter { it.reason == "write-failed" }.sumOf { it.count }
            val missingAfter = lost.filter { category(it.reason) == StreamStatus.MISSING }.sumOf { it.count }
            val saved = when (s) {
                CaptureStream.FRAME -> r.frames.size
                CaptureStream.RGB -> r.rgb.size
                CaptureStream.DEPTH -> r.depth.size
                CaptureStream.RAW_DEPTH -> r.depth.count { it.rawPath != null }
                else -> r.depth.count { it.confidencePath != null }
            }
            // Acquisiti: dal telefono, o dai numeri assegnati all'acquisizione (da 0 al massimo visto; i keyframe scartati per
            // coda piena non prendono un numero e si aggiungono). Non spiegati: acquisiti − salvati − falliti in scrittura − persi.
            // Ripetuti (raw e confidenza): acquisiti ma uguali al campione già salvato.
            val duplicate = when (s) {
                CaptureStream.RAW_DEPTH -> r.depth.count { it.rawStatus == StreamStatus.DUPLICATE }
                CaptureStream.CONFIDENCE -> r.depth.count { it.confidenceStatus == StreamStatus.DUPLICATE }
                else -> 0
            }
            val d = device?.get(s)
            val acquired = when {
                d != null -> d.acquired
                numbered.isEmpty() -> saved + failedAfter + missingAfter + duplicate
                else -> numbered.max() + 1 + missingAfter
            }
            val unexplained = (acquired - saved - failedAfter - missingAfter - duplicate).coerceAtLeast(0)
            val fromData = StreamCounts(
                acquired = acquired,
                saved = saved,
                unavailable = lost.filter { category(it.reason) == StreamStatus.UNAVAILABLE }.sumOf { it.count } +
                    if (s == CaptureStream.RAW_DEPTH) r.depth.count { it.rawStatus == StreamStatus.UNAVAILABLE }
                    else if (s == CaptureStream.CONFIDENCE) r.depth.count { it.confidenceStatus == StreamStatus.UNAVAILABLE } else 0,
                failed = lost.filter { category(it.reason) == StreamStatus.FAILED }.sumOf { it.count } +
                    if (s == CaptureStream.RAW_DEPTH) r.depth.count { it.rawStatus == StreamStatus.FAILED }
                    else if (s == CaptureStream.CONFIDENCE) r.depth.count { it.confidenceStatus == StreamStatus.FAILED } else 0,
                missing = missingAfter + unexplained,
                duplicate = duplicate,
            )
            if (d != null) Counts(d.copy(missing = d.missing + unexplained), unexplained, true) else Counts(fromData, unexplained, false)
        }
    }
}

/** Un problema trovato nel dataset. `severity`: "error" (dati inutilizzabili o incoerenti), "warning" (da sapere). */
data class CaptureIssue(val severity: String, val what: String) {
    override fun toString() = "[$severity] $what"
}

/** Controlli di coerenza di una registrazione M0.2 (e dei file della cartella, se si conoscono). */
object CaptureValidation {
    /** Rapporto fx/fy tollerato tra RGB e depth (pixel quadrati in entrambe: devono coincidere). */
    const val PIXEL_ASPECT_TOLERANCE = 0.03

    /** [files]: i percorsi relativi presenti nella cartella o nello ZIP; null = non controllare i file. */
    fun validate(r: ScanRecording, files: Set<String>? = null): List<CaptureIssue> {
        val out = mutableListOf<CaptureIssue>()
        fun error(s: String) = out.add(CaptureIssue("error", s))
        fun warn(s: String) = out.add(CaptureIssue("warning", s))

        val h = r.header
        if (h.version >= 2 && h.capture == null && (r.rgb.isNotEmpty() || r.depth.isNotEmpty())) error("Intestazione senza impostazioni di acquisizione")
        h.capture?.let { c ->
            if (c.mode != CaptureMode.ENVIRONMENT && c.mode != CaptureMode.OBJECT) error("Modalità sconosciuta: ${c.mode}")
            if (c.rgbIntervalMs <= 0 || c.depthIntervalMs <= 0 || c.frameIntervalMs <= 0) error("Intervalli di acquisizione non positivi")
            if (c.jpegQuality !in 1..100) error("Qualità JPEG fuori scala: ${c.jpegQuality}")
        }
        h.cameraConfig?.let { cc ->
            if (cc.imageWidth <= 0 || cc.imageHeight <= 0) error("Configurazione fotocamera senza risoluzione dell'immagine CPU")
        }
        h.camera?.imageIntrinsics?.let { k -> checkIntrinsics(k, "intestazione, immagine CPU")?.let(::error) }
        h.camera?.textureIntrinsics?.let { k -> checkIntrinsics(k, "intestazione, texture")?.let(::error) }
        if (r.truncated) warn("Registrazione troncata: manca la riga finale (app interrotta?)")
        r.end?.drained?.let { if (!it) warn("La coda di scrittura non è stata svuotata alla chiusura: possono mancare gli ultimi dati") }

        // Perdite: niente buchi senza spiegazione.
        for ((stream, c) in CaptureStreams.of(r)) {
            if (c.unexplained > 0) error("$stream: ${c.unexplained} acquisiti e non salvati senza una riga 'missing' che lo spieghi")
        }
        r.missing.filter { it.reason == "write-failed" }.groupBy { it.kind }.forEach { (kind, l) ->
            warn("$kind: ${l.sumOf { it.count }} non scritti (${l.first().detail ?: "causa sconosciuta"})")
        }
        val invalidPoints = r.frames.sumOf { it.invalidPoints }
        if (invalidPoints > 0) warn("Punti con valori non finiti scartati: $invalidPoints in ${r.frames.count { it.invalidPoints > 0 }} frame")
        val invalidPlanes = r.frames.sumOf { it.invalidPlanes }
        if (invalidPlanes > 0) warn("Piani con valori non finiti scartati: $invalidPlanes")

        monotonic(r.poses.map { it.timestampNs }, "pose")?.let(::error)
        if (r.poses.zipWithNext().any { (a, b) -> b.seq != a.seq + 1 }) error("Numerazione delle pose non consecutiva")
        monotonic(r.rgb.map { it.timestampNs }, "RGB")?.let(::error)
        monotonic(r.depth.map { it.timestampNs }, "depth")?.let(::error)
        monotonic(r.frames.map { it.timestampNs }, "frame")?.let(::error)

        val index = CaptureIndex(r)
        val paths = HashSet<String>()
        for (x in r.rgb) {
            checkIntrinsics(x.intrinsics, "RGB #${x.seq}")?.let(::error)
            if (x.intrinsics.width != x.width || x.intrinsics.height != x.height) error("RGB #${x.seq}: intrinseche per ${x.intrinsics.width}×${x.intrinsics.height}, immagine ${x.width}×${x.height}")
            h.cameraConfig?.let { cc ->
                if (cc.imageWidth > 0 && (cc.imageWidth != x.width || cc.imageHeight != x.height)) error("RGB #${x.seq}: ${x.width}×${x.height} diversa dalla configurazione ${cc.imageWidth}×${cc.imageHeight}")
            }
            if (!paths.add(x.path)) error("RGB #${x.seq}: file ripetuto ${x.path}")
            if (files != null && x.path !in files) error("RGB #${x.seq}: manca il file ${x.path}")
            val offset = x.timestampNs - x.imageTimestampNs
            if (offset < 0 || offset > FrameAssignment.MAX_IMAGE_TO_FRAME_NS) warn("RGB #${x.seq}: timestamp dell'immagine fuori dal suo frame (${offset / 1_000_000.0} ms)")
            if (r.poses.isNotEmpty()) {
                val p = index.poseOfFrame(x.frameSeq)
                if (p == null) error("RGB #${x.seq}: manca la posa del suo frame ${x.frameSeq}")
                else if (p.timestampNs != x.timestampNs) error("RGB #${x.seq}: il frame ${x.frameSeq} ha un altro timestamp")
                else if (p.camera != null && p.camera != x.camera) error("RGB #${x.seq}: posa diversa da quella del suo frame")
            }
        }
        val rgbAspect = r.rgb.firstOrNull()?.intrinsics?.let { it.fx / it.fy } ?: h.camera?.imageIntrinsics?.let { it.fx / it.fy }
        for (d in r.depth) {
            if (d.width <= 0 || d.height <= 0) error("Depth #${d.seq}: dimensioni non valide")
            d.intrinsics?.let { k ->
                checkIntrinsics(k, "depth #${d.seq}")?.let(::error)
                if (k.width != d.width || k.height != d.height) error("Depth #${d.seq}: intrinseche per ${k.width}×${k.height}, depth ${d.width}×${d.height}")
                if (rgbAspect != null && abs((k.fx / k.fy) / rgbAspect - 1) > PIXEL_ASPECT_TOLERANCE) {
                    error("Depth #${d.seq}: intrinseche incoerenti (fx/fy ${fmt(k.fx / k.fy)} contro ${fmt(rgbAspect)} dell'RGB: scalate da un'immagine con un altro rapporto d'aspetto, fonte ${d.intrinsicsSource})")
                }
            }
            if (d.intrinsicsSource == IntrinsicsSource.TEXTURE_SCALED_ASPECT_MISMATCH) warn("Depth #${d.seq}: rapporto d'aspetto diverso dalla texture")
            if (d.timestampNs > d.frameTimestampNs) error("Depth #${d.seq}: timestamp della depth dopo il frame in cui è stata letta")
            if (d.poseExact && d.poseFrameSeq != null) {
                val p = index.poseOfFrame(d.poseFrameSeq)
                if (p != null && (p.timestampNs < d.timestampNs || p.timestampNs - d.timestampNs > FrameAssignment.MAX_IMAGE_TO_FRAME_NS)) {
                    error("Depth #${d.seq}: il frame ${d.poseFrameSeq} non è quello della depth")
                }
                if (p != null && p.camera != null && d.camera != null && p.camera != d.camera) error("Depth #${d.seq}: posa diversa da quella del suo frame")
            }
            for (p in listOfNotNull(d.path, d.rawPath, d.confidencePath)) {
                if (!paths.add(p)) error("Depth #${d.seq}: file ripetuto $p")
                if (files != null && p !in files) error("Depth #${d.seq}: manca il file $p")
            }
            if (d.rawPath != null && d.rawStatus != null && d.rawStatus != StreamStatus.SAVED) error("Depth #${d.seq}: file raw presente ma stato ${d.rawStatus}")
            if (d.rawStatus == StreamStatus.DUPLICATE && (d.rawPath != null || d.confidencePath != null)) error("Depth #${d.seq}: raw ripetuta ma salvata di nuovo")
            // Posa della raw: dal suo timestamp, coerente con la regola dei frame.
            if (d.rawPath != null && d.rawTimestampNs != null && d.rawPoseMatch != null && r.poses.isNotEmpty()) {
                val p = d.rawPoseFrameSeq?.let { index.poseOfFrame(it) }
                when (d.rawPoseMatch) {
                    PoseMatch.FRAME, PoseMatch.TIMESTAMP -> when {
                        p == null -> error("Depth #${d.seq}: manca la posa ${d.rawPoseFrameSeq} della raw")
                        abs(p.timestampNs - d.rawTimestampNs) > FrameAssignment.MAX_IMAGE_TO_FRAME_NS -> error("Depth #${d.seq}: posa della raw a ${abs(p.timestampNs - d.rawTimestampNs) / 1_000_000} ms dal suo timestamp")
                        d.rawPoseMatch == PoseMatch.FRAME && index.frameOfImage(d.rawTimestampNs)?.seq != p.seq ->
                            error("Depth #${d.seq}: il frame ${p.seq} non è quello della raw")
                        p.camera != null && d.rawCamera != null && p.camera != d.rawCamera -> error("Depth #${d.seq}: posa della raw diversa da quella del suo frame")
                        else -> {}
                    }
                    PoseMatch.UNAVAILABLE -> if (d.rawCamera != null || d.rawPoseFrameSeq != null) error("Depth #${d.seq}: raw senza posa ma con una posa registrata")
                }
            }
        }
        val rawRepeats = r.depth.filter { it.rawPath != null && it.rawTimestampNs != null }.groupBy { Triple(it.rawTimestampNs, it.rawWidth, it.rawHeight) }.values.sumOf { it.size - 1 }
        if (rawRepeats > 0) warn("Depth raw salvata più volte (stesso timestamp e dimensioni): $rawRepeats ripetute")
        if (h.version >= 2 && r.rgb.isEmpty()) warn("Nessun keyframe RGB")
        if (h.version >= 2 && r.depth.isEmpty() && h.session.depthMode == "AUTOMATIC") warn("Depth attiva ma nessun keyframe depth")
        return out
    }

    private fun fmt(v: Double) = (kotlin.math.round(v * 1000) / 1000).toString()

    private fun checkIntrinsics(k: CameraIntrinsics, where: String): String? = when {
        k.width <= 0 || k.height <= 0 -> "Intrinseche ($where): dimensioni non valide"
        !(k.fx > 0 && k.fy > 0) -> "Intrinseche ($where): fuoco non positivo"
        k.cx !in 0.0..k.width.toDouble() || k.cy !in 0.0..k.height.toDouble() -> "Intrinseche ($where): punto principale fuori dall'immagine"
        else -> null
    }

    private fun monotonic(ts: List<Long>, what: String): String? {
        val i = ts.zipWithNext().indexOfFirst { (a, b) -> b < a }
        return if (i < 0) null else "Timestamp $what non in ordine (elemento ${i + 1})"
    }
}
