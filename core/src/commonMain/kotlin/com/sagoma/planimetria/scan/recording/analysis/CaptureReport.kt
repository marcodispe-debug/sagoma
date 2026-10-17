package com.sagoma.planimetria.scan.recording.analysis

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.recording.CaptureIndex
import com.sagoma.planimetria.scan.recording.CaptureIssue
import com.sagoma.planimetria.scan.recording.CaptureValidation
import com.sagoma.planimetria.scan.recording.CaptureStreams
import com.sagoma.planimetria.scan.recording.PoseMatch
import com.sagoma.planimetria.scan.recording.StreamCounts
import com.sagoma.planimetria.scan.recording.ScanRecording
import kotlin.math.abs

/**
 * Affidabilità temporale di una registrazione (M0.2, ma va anche su M0): frequenze reali dei frame ARCore, dell'RGB e della
 * depth, buchi, campioni persi, coda di scrittura e sincronizzazione con le pose. Kotlin puro e deterministico.
 */
data class CaptureTiming(
    val mode: String?,
    val durationMs: Double,
    val truncated: Boolean,
    /** Frame ARCore distinti (righe `pose`; in M0 i frame registrati). */
    val arFrames: Int,
    val arHz: Double,
    val arIntervalMs: Dist,
    /** Buchi tra frame ARCore consecutivi oltre [GAP_MS]. */
    val arGaps: Int,
    val frameLines: Int,
    val frameLineHz: Double,
    val frameLineIntervalMs: Dist,
    val rgb: Int,
    val rgbHz: Double,
    val rgbTargetHz: Double?,
    val rgbIntervalMs: Dist,
    val rgbResolutions: List<String>,
    val depth: Int,
    val depthHz: Double,
    val depthTargetHz: Double?,
    val depthIntervalMs: Dist,
    val depthResolutions: List<String>,
    /** Percentuale dei frame ARCore che hanno un keyframe RGB / depth. */
    val rgbFramePct: Double,
    val depthFramePct: Double,
    /** Campioni persi per (tipo, motivo). */
    val missing: List<Pair<String, Int>>,
    val maxQueueItems: Int,
    val maxQueueBytes: Long,
    val updateMaxMs: Double,
    val captureMaxMs: Double,
    val drawGapMaxMs: Double,
    /** |posa − RGB| (ms) e quante RGB hanno la posa esatta del proprio frame. */
    val rgbPoseDeltaMs: Dist,
    val rgbPoseExact: Int,
    /** |posa − depth| (ms), quante depth hanno la posa esatta, ritardo della depth rispetto al frame in cui è stata letta (ms). */
    val depthPoseDeltaMs: Dist,
    val depthPoseExact: Int,
    val depthAgeMs: Dist,
    /** |depth più vicina − RGB| (ms). */
    val rgbDepthDeltaMs: Dist,
    /** Conteggi per stream (acquisiti, salvati, non disponibili, falliti, persi) e perdite non spiegate. */
    val streams: Map<String, StreamCounts>,
    val unexplained: Map<String, Int>,
    /** Punti e piani scartati perché non finiti, e frame che ne avevano. */
    val invalidPoints: Int,
    val invalidPlanes: Int,
    val framesWithInvalidPoints: Int,
    /** Depth raw: quante associate alla posa per frame, per timestamp, senza posa; |posa − raw| e quanto la raw è più vecchia della depth filtrata (ms). */
    val rawPoseFrame: Int,
    val rawPoseTimestamp: Int,
    val rawPoseNone: Int,
    val rawPoseDeltaMs: Dist,
    val rawAgeMs: Dist,
    val issues: List<CaptureIssue>,
) {
    companion object {
        const val GAP_MS = 200.0
    }
}

object CaptureReport {
    fun analyze(r: ScanRecording, files: Set<String>? = null): CaptureTiming {
        val arTimes = r.poses.map { it.timestampNs }.ifEmpty { r.frames.map { it.timestampNs } }
        val frameTimes = r.frames.map { it.timestampNs }
        val rgbTimes = r.rgb.map { it.timestampNs }
        val depthTimes = r.depth.map { it.timestampNs }
        val all = arTimes + frameTimes + rgbTimes + depthTimes
        val durationMs = if (all.size < 2) 0.0 else (all.max() - all.min()) / 1e6
        val index = CaptureIndex(r)
        val streams = CaptureStreams.of(r)
        val rawPoses = r.depth.mapNotNull { index.rawPoseFor(it) }
        val c = r.header.capture

        val rgbPose = r.rgb.mapNotNull { index.poseFor(it) }
        val depthPose = r.depth.mapNotNull { index.poseFor(it) }
        val arIntervals = intervalsMs(arTimes)
        return CaptureTiming(
            mode = c?.mode,
            durationMs = durationMs,
            truncated = r.truncated,
            arFrames = arTimes.size,
            arHz = hz(arTimes),
            arIntervalMs = Dist.of(arIntervals),
            arGaps = arIntervals.count { it > CaptureTiming.GAP_MS },
            frameLines = frameTimes.size,
            frameLineHz = hz(frameTimes),
            frameLineIntervalMs = Dist.of(intervalsMs(frameTimes)),
            rgb = r.rgb.size,
            rgbHz = hz(rgbTimes),
            rgbTargetHz = c?.rgbIntervalMs?.let { 1000.0 / it },
            rgbIntervalMs = Dist.of(intervalsMs(rgbTimes)),
            rgbResolutions = r.rgb.map { "${it.width}×${it.height}" }.distinct(),
            depth = r.depth.size,
            depthHz = hz(depthTimes),
            depthTargetHz = c?.depthIntervalMs?.let { 1000.0 / it },
            depthIntervalMs = Dist.of(intervalsMs(depthTimes)),
            depthResolutions = r.depth.map { "${it.width}×${it.height}" }.distinct(),
            rgbFramePct = pct(r.rgb.map { it.frameSeq }.distinct().size, arTimes.size),
            depthFramePct = pct(r.depth.map { it.frameSeq }.distinct().size, arTimes.size),
            missing = r.missing.groupBy { "${it.kind}/${it.reason}" }.map { (k, v) -> k to v.sumOf { it.count } }.sortedBy { it.first },
            maxQueueItems = r.stats.maxOfOrNull { it.maxQueueItems } ?: 0,
            maxQueueBytes = r.stats.maxOfOrNull { it.maxQueueBytes } ?: 0,
            updateMaxMs = r.stats.maxOfOrNull { it.updateMaxMs } ?: 0.0,
            captureMaxMs = r.stats.maxOfOrNull { it.captureMaxMs } ?: 0.0,
            drawGapMaxMs = r.stats.maxOfOrNull { it.drawGapMaxMs } ?: 0.0,
            rgbPoseDeltaMs = Dist.of(rgbPose.map { abs(it.deltaNs) / 1e6 }),
            rgbPoseExact = rgbPose.count { it.exact },
            depthPoseDeltaMs = Dist.of(depthPose.map { abs(it.deltaNs) / 1e6 }),
            depthPoseExact = depthPose.count { it.exact },
            depthAgeMs = Dist.of(r.depth.map { (it.frameTimestampNs - it.timestampNs) / 1e6 }),
            rgbDepthDeltaMs = Dist.of(r.rgb.mapNotNull { index.depthFor(it) }.map { abs(it.deltaNs) / 1e6 }),
            streams = streams.mapValues { it.value.counts },
            unexplained = streams.mapValues { it.value.unexplained }.filterValues { it > 0 },
            invalidPoints = r.frames.sumOf { it.invalidPoints },
            invalidPlanes = r.frames.sumOf { it.invalidPlanes },
            framesWithInvalidPoints = r.frames.count { it.invalidPoints > 0 },
            rawPoseFrame = rawPoses.count { it.kind == PoseMatch.FRAME },
            rawPoseTimestamp = rawPoses.count { it.kind == PoseMatch.TIMESTAMP },
            rawPoseNone = rawPoses.count { it.kind == PoseMatch.UNAVAILABLE },
            rawPoseDeltaMs = Dist.of(rawPoses.mapNotNull { it.deltaNs }.map { abs(it) / 1e6 }),
            rawAgeMs = Dist.of(r.depth.filter { it.rawPath != null }.mapNotNull { d -> d.rawTimestampNs?.let { (d.timestampNs - it) / 1e6 } }),
            issues = CaptureValidation.validate(r, files),
        )
    }

    fun text(t: CaptureTiming, title: String? = null): String = buildString {
        fun line(s: String = "") = append(s).append('\n')
        fun hzText(real: Double, target: Double?) = "${f(real)} Hz" + (target?.let { " (obiettivo ${f(it)} Hz)" } ?: "")
        line("=== Acquisizione${title?.let { " — $it" } ?: ""} ===")
        line("Modalità: ${t.mode ?: "M0 (senza RGB/depth)"} · durata ${f(t.durationMs / 1000, 1)} s" + if (t.truncated) " · TRONCATA (manca la riga finale)" else "")
        line("Frame ARCore: ${t.arFrames} · ${f(t.arHz)} Hz · intervalli ${dist(t.arIntervalMs)} · buchi > ${CaptureTiming.GAP_MS.toInt()} ms: ${t.arGaps}")
        line("Righe frame (piani/punti): ${t.frameLines} · ${f(t.frameLineHz)} Hz · intervalli ${dist(t.frameLineIntervalMs)}")
        line("RGB: ${t.rgb} · ${hzText(t.rgbHz, t.rgbTargetHz)} · intervalli ${dist(t.rgbIntervalMs)} · risoluzione ${t.rgbResolutions.ifEmpty { listOf("—") }.joinToString()}")
        line("Depth: ${t.depth} · ${hzText(t.depthHz, t.depthTargetHz)} · intervalli ${dist(t.depthIntervalMs)} · risoluzione ${t.depthResolutions.ifEmpty { listOf("—") }.joinToString()}")
        line("Frame ARCore con RGB: ${f(t.rgbFramePct, 1)}% · con depth: ${f(t.depthFramePct, 1)}%")
        line("Stream          acquisiti  salvati  non disp.  falliti  persi  ripetuti")
        for ((name, c) in t.streams) line("  " + name.padEnd(14) + c.acquired.toString().padStart(9) + c.saved.toString().padStart(9) + c.unavailable.toString().padStart(11) + c.failed.toString().padStart(9) + c.missing.toString().padStart(7) + c.duplicate.toString().padStart(10) + (t.unexplained[name]?.let { "  (di cui $it senza spiegazione)" } ?: ""))
        line("Motivi: " + (t.missing.joinToString(", ") { "${it.first} ${it.second}" }.ifEmpty { "nessuno" }))
        if (t.invalidPoints + t.invalidPlanes > 0) line("Valori non finiti scartati: ${t.invalidPoints} punti in ${t.framesWithInvalidPoints} frame · ${t.invalidPlanes} piani")
        line("Coda di scrittura: max ${t.maxQueueItems} elementi · ${f(t.maxQueueBytes / 1048576.0, 1)} MB")
        line("Thread di disegno: update max ${f(t.updateMaxMs, 1)} ms · recorder max ${f(t.captureMaxMs, 1)} ms · pausa max tra due disegni ${f(t.drawGapMaxMs, 0)} ms")
        line("Sincronizzazione RGB↔posa (per frame): esatte ${t.rgbPoseExact}/${t.rgb} · offset immagine→frame ${dist(t.rgbPoseDeltaMs)}")
        line("Sincronizzazione depth↔posa (per frame): esatte ${t.depthPoseExact}/${t.depth} · offset depth→frame ${dist(t.depthPoseDeltaMs)} · distanza dal frame di lettura ${dist(t.depthAgeMs)}")
        line("Depth raw↔posa (dal timestamp della raw): per frame ${t.rawPoseFrame} · per timestamp ${t.rawPoseTimestamp} · senza posa ${t.rawPoseNone} · |posa − raw| ${dist(t.rawPoseDeltaMs)} · raw più vecchia della depth di ${dist(t.rawAgeMs)}")
        line("RGB↔depth più vicina: |Δ| ${dist(t.rgbDepthDeltaMs)}")
        if (t.issues.isEmpty()) line("Controlli: nessun problema")
        else {
            line("Controlli: ${t.issues.count { it.severity == "error" }} errori, ${t.issues.count { it.severity == "warning" }} avvisi")
            for (i in t.issues.take(30)) line("  $i")
            if (t.issues.size > 30) line("  … altri ${t.issues.size - 30}")
        }
    }

    private fun intervalsMs(ts: List<Long>): List<Double> = ts.zipWithNext { a, b -> (b - a) / 1e6 }

    private fun hz(ts: List<Long>): Double {
        if (ts.size < 2) return 0.0
        val span = (ts.last() - ts.first()) / 1e9
        return if (span <= 0) 0.0 else (ts.size - 1) / span
    }

    private fun pct(n: Int, of: Int) = if (of == 0) 0.0 else n * 100.0 / of
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun dist(d: Dist) = if (d.count == 0) "—" else "mediana ${f(d.median, 0)} · media ${f(d.mean, 0)} · p90 ${f(d.p90, 0)} · max ${f(d.max, 0)} ms"
}
