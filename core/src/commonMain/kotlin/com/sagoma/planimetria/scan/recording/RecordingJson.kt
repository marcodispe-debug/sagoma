package com.sagoma.planimetria.scan.recording

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** File che non è una registrazione valida (formato, versione o righe illeggibili). */
class RecordingFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Lettura e scrittura del formato JSONL: una riga per elemento, la prima è l'intestazione, poi i frame (e in M0.2 le righe
 * `pose`, `rgb`, `depth`, `missing`, `stats`, nell'ordine di acquisizione), poi (se la registrazione si è chiusa bene) la riga
 * finale. Chi registra scrive riga per riga così un'interruzione lascia comunque un file leggibile.
 */
object ScanRecordingJson {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun headerLine(h: RecordingHeader): String = json.encodeToString(RecordingHeader.serializer(), h)
    fun frameLine(f: RecordedFrame): String = json.encodeToString(RecordedFrame.serializer(), f)
    fun endLine(e: RecordingEnd): String = json.encodeToString(RecordingEnd.serializer(), e)
    fun poseLine(p: PoseSample): String = json.encodeToString(PoseSample.serializer(), p)
    fun rgbLine(r: RgbKeyframe): String = json.encodeToString(RgbKeyframe.serializer(), r)
    fun depthLine(d: DepthKeyframe): String = json.encodeToString(DepthKeyframe.serializer(), d)
    fun missingLine(m: MissingSample): String = json.encodeToString(MissingSample.serializer(), m)
    fun statsLine(s: CaptureStats): String = json.encodeToString(CaptureStats.serializer(), s)

    /** Metadati della cartella M0.2 (`metadata/camera.json`, `metadata/README.json`): JSON leggibile. */
    fun cameraMetadata(h: RecordingHeader): String = pretty.encodeToString(CameraMetadata.serializer(), CameraMetadata.of(h))
    fun readme(h: RecordingHeader): String = pretty.encodeToString(DatasetReadme.serializer(), DatasetReadme.of(h))
    private val pretty = Json { encodeDefaults = true; prettyPrint = true }

    /**
     * Scrive una registrazione intera. Le righe M0.2 si scrivono per tipo, dopo i frame: la lettura le separa comunque per tipo
     * (l'ordine conta solo dentro ciascun tipo).
     */
    fun encode(r: ScanRecording): String = buildString {
        append(headerLine(r.header)).append('\n')
        for (f in r.frames) append(frameLine(f)).append('\n')
        for (p in r.poses) append(poseLine(p)).append('\n')
        for (x in r.rgb) append(rgbLine(x)).append('\n')
        for (d in r.depth) append(depthLine(d)).append('\n')
        for (m in r.missing) append(missingLine(m)).append('\n')
        for (s in r.stats) append(statsLine(s)).append('\n')
        r.end?.let { append(endLine(it)).append('\n') }
    }

    /**
     * Legge un file. Rifiuta ciò che non è una registrazione di Sagoma o ha una versione più nuova di quella conosciuta. Se
     * `tolerateTruncation`, l'ultima riga illeggibile (app chiusa a metà scrittura) viene ignorata; le altre no.
     */
    fun decode(text: String, tolerateTruncation: Boolean = true): ScanRecording {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) throw RecordingFormatException("File vuoto")
        val header = try {
            json.decodeFromString(RecordingHeader.serializer(), lines.first())
        } catch (e: Exception) {
            throw RecordingFormatException("Intestazione illeggibile", e)
        }
        if (header.type != RecordingHeader.TYPE || header.format != RecordingFormat.NAME) {
            throw RecordingFormatException("Non è una registrazione di scansione di Sagoma")
        }
        if (header.version > RecordingFormat.VERSION) {
            throw RecordingFormatException("Versione ${header.version} troppo nuova (questa legge fino alla ${RecordingFormat.VERSION})")
        }
        val frames = mutableListOf<RecordedFrame>()
        val poses = mutableListOf<PoseSample>()
        val rgb = mutableListOf<RgbKeyframe>()
        val depth = mutableListOf<DepthKeyframe>()
        val missing = mutableListOf<MissingSample>()
        val stats = mutableListOf<CaptureStats>()
        var end: RecordingEnd? = null
        for ((n, line) in lines.withIndex().drop(1)) {
            val isLast = n == lines.lastIndex
            try {
                val obj: JsonObject = json.parseToJsonElement(line).jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    RecordedFrame.TYPE -> frames.add(json.decodeFromString(RecordedFrame.serializer(), line))
                    PoseSample.TYPE -> poses.add(json.decodeFromString(PoseSample.serializer(), line))
                    RgbKeyframe.TYPE -> rgb.add(json.decodeFromString(RgbKeyframe.serializer(), line))
                    DepthKeyframe.TYPE -> depth.add(json.decodeFromString(DepthKeyframe.serializer(), line))
                    MissingSample.TYPE -> missing.add(json.decodeFromString(MissingSample.serializer(), line))
                    CaptureStats.TYPE -> stats.add(json.decodeFromString(CaptureStats.serializer(), line))
                    RecordingEnd.TYPE -> end = json.decodeFromString(RecordingEnd.serializer(), line)
                    else -> throw RecordingFormatException("Riga ${n + 1}: tipo sconosciuto")
                }
            } catch (e: Exception) {
                if (isLast && tolerateTruncation) break
                throw if (e is RecordingFormatException) e else RecordingFormatException("Riga ${n + 1} illeggibile", e)
            }
        }
        return ScanRecording(header, frames, end, poses, rgb, depth, missing, stats)
    }
}
