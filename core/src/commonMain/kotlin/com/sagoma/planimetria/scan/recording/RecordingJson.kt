package com.sagoma.planimetria.scan.recording

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** File che non è una registrazione valida (formato, versione o righe illeggibili). */
class RecordingFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Lettura e scrittura del formato JSONL: una riga per elemento, la prima è l'intestazione, poi i frame, poi (se la registrazione
 * si è chiusa bene) la riga finale. Chi registra scrive riga per riga ([headerLine], [frameLine], [endLine]) così un'interruzione
 * lascia comunque un file leggibile.
 */
object ScanRecordingJson {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun headerLine(h: RecordingHeader): String = json.encodeToString(RecordingHeader.serializer(), h)
    fun frameLine(f: RecordedFrame): String = json.encodeToString(RecordedFrame.serializer(), f)
    fun endLine(e: RecordingEnd): String = json.encodeToString(RecordingEnd.serializer(), e)

    fun encode(r: ScanRecording): String = buildString {
        append(headerLine(r.header)).append('\n')
        for (f in r.frames) append(frameLine(f)).append('\n')
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
        var end: RecordingEnd? = null
        for ((n, line) in lines.withIndex().drop(1)) {
            val isLast = n == lines.lastIndex
            try {
                val obj: JsonObject = json.parseToJsonElement(line).jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    RecordedFrame.TYPE -> frames.add(json.decodeFromString(RecordedFrame.serializer(), line))
                    RecordingEnd.TYPE -> end = json.decodeFromString(RecordingEnd.serializer(), line)
                    else -> throw RecordingFormatException("Riga ${n + 1}: tipo sconosciuto")
                }
            } catch (e: Exception) {
                if (isLast && tolerateTruncation) break
                throw if (e is RecordingFormatException) e else RecordingFormatException("Riga ${n + 1} illeggibile", e)
            }
        }
        return ScanRecording(header, frames, end)
    }
}
