package com.sagoma.planimetria.scan.sketch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Registro degli eventi della scansione guidata dallo schizzo, per le prove sul campo: FILE LATERALE [SketchSessionLog.FILE_NAME]
 * nella cartella della registrazione (e quindi nel suo ZIP). La registrazione (`recording.jsonl`) non cambia: le registrazioni
 * vecchie restano identiche e leggibili, gli strumenti esistenti ignorano il file in più. Serve a riprodurre il flusso con le
 * scelte dell'utente vero: lato di partenza, pareti confermate (ordine, istante, retta), risposte della revisione (con i valori
 * mostrati), misure a mano, esito finale.
 *
 * Tempi: `sessionMs` = tempo della sessione (lo stesso `elapsedMs` dei frame dati al motore); `arTimestampNs` = lo stesso istante
 * sull'orologio di ARCore, quello dei `timestampNs` della registrazione (lo calcola chi scrive dal frame più recente).
 */

/** Prima riga del file: formato, versione e lo schizzo (angoli interni in metri, x z alternati; aperture "lato:tipo"). */
@Serializable
data class SessionLogHeader(
    val type: String = "header",
    val format: String = SketchSessionLog.FORMAT,
    val version: Int = SketchSessionLog.VERSION,
    val sketch: List<Double> = emptyList(),
    val openings: List<String> = emptyList(),
)

/** Un evento. Solo i campi che servono al tipo [kind] sono valorizzati. */
@Serializable
data class SessionLogEntry(
    val type: String = "event",
    val seq: Int = 0,
    /** Uno dei [SketchSessionLog.Kind]. */
    val kind: String,
    val sessionMs: Long? = null,
    val arTimestampNs: Long? = null,
    val side: Int? = null,
    val wallId: Int? = null,
    /** Retta confermata: ax, az, bx, bz, interno x, interno z (metri, mondo ARCore). */
    val line: List<Double>? = null,
    /**
     * Retta confermata: incertezze (posizione m, direzione gradi), lato interno noto (1/0) e punti di sostegno (metri lungo la retta
     * da a), cioè tutto ciò che serve a rifare offline la stessa pianta con il nucleo.
     */
    val sigma: List<Double>? = null,
    val inwardKnown: Boolean? = null,
    val support: List<Double>? = null,
    /** Chiave della domanda e risposta (nome di [Answer]; null = risposta tolta). Per la partenza: il riferimento dichiarato. */
    val key: String? = null,
    val answer: String? = null,
    /** I valori della domanda quando l'utente ha risposto (per esempio lo spostamento mostrato per la messa in squadra). */
    val values: Map<String, Double>? = null,
    /** Misura a mano (m) o 1/0 per una stima dello schizzo accettata/tolta. */
    val value: Double? = null,
    val note: String? = null,
    /** Esito applicato: angoli della pianta (metri, x z alternati). */
    val corners: List<Double>? = null,
    /** [SketchSessionLog.Kind.BLOCKING]: i blocchi presenti in quel momento ("CODICE[lati] chiave=valore …"); vuoto = nessuno. */
    val blocking: List<String>? = null,
)

object SketchSessionLog {
    const val FILE_NAME = "session-events.jsonl"
    const val FORMAT = "sagoma-sketch-session-events"
    const val VERSION = 1

    enum class Kind { START_SIDE, WALL_ASSIGNED, WALL_REMOVED, ANSWER, START_DECLARATION, MANUAL_LENGTH, SKETCH_LENGTH_ACCEPTED, BLOCKING, APPLIED, CANCELLED }

    /** I blocchi presenti, in forma breve e stabile (per scrivere un evento [Kind.BLOCKING] solo quando cambiano). */
    fun blockingSummary(issues: List<GeometryIssue>): List<String> = issues.filter { it.severity == Severity.BLOCKING }
        .map { i -> i.key + i.values.entries.sortedBy { it.key }.joinToString("") { (k, v) -> " $k=${kotlin.math.round(v * 1000) / 1000}" } }
        .sorted()

    fun blocking(s: SketchSession, summary: List<String>) =
        SessionLogEntry(kind = Kind.BLOCKING.name, sessionMs = s.engine.lastTimeMs.takeIf { it != Long.MIN_VALUE }, blocking = summary)

    // encodeDefaults: il campo `type` (sempre uguale al suo valore predefinito) deve essere scritto, è ciò che distingue le righe.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun header(s: SketchSession) = SessionLogHeader(
        sketch = s.sketch.corners.flatMap { listOf(it.x, it.z) },
        openings = s.openings.map { "${it.side}:${it.kind.name}" },
    )

    /**
     * Cosa è cambiato da [before] ad [after], come eventi (senza `seq` né `arTimestampNs`, che aggiunge chi scrive). Per avere lo
     * stato completo da un certo momento si parte da una sessione nuova con lo stesso schizzo. [issues]: i problemi mostrati in
     * quel momento, per conservare i valori delle domande a cui l'utente risponde.
     */
    fun diff(before: SketchSession, after: SketchSession, issues: List<GeometryIssue> = emptyList()): List<SessionLogEntry> {
        val now = after.engine.lastTimeMs.takeIf { it != Long.MIN_VALUE }
        val out = ArrayList<SessionLogEntry>()
        if (before.startSide != after.startSide) out += SessionLogEntry(kind = Kind.START_SIDE.name, sessionMs = now, side = after.startSide)
        for (side in (before.assigned.keys + after.assigned.keys).sorted()) {
            val a = before.assigned[side]; val b = after.assigned[side]
            if (a == b) continue
            if (b == null) { out += SessionLogEntry(kind = Kind.WALL_REMOVED.name, sessionMs = now, side = side, wallId = a?.wallId); continue }
            val l = b.line
            // Parete nuova: l'istante della conferma. Parete già confermata e spostata su un altro lato: l'istante dello spostamento.
            val isNew = before.assigned.values.none { it.wallId == b.wallId }
            out += SessionLogEntry(
                kind = Kind.WALL_ASSIGNED.name, sessionMs = if (isNew) b.confirmedAtMs else now, side = side, wallId = b.wallId,
                line = listOf(l.a.x, l.a.z, l.b.x, l.b.z, l.inward.x, l.inward.z), value = l.supportAlongM?.size?.toDouble(),
                sigma = listOf(l.sigmaOffsetM, l.sigmaDirectionDeg), inwardKnown = l.inwardKnown, support = l.supportAlongM,
            )
        }
        for (key in (before.answers.keys + after.answers.keys).sorted()) {
            val a = before.answers[key]; val b = after.answers[key]
            if (a == b) continue
            out += SessionLogEntry(kind = Kind.ANSWER.name, sessionMs = now, key = key, answer = b?.name, values = issues.firstOrNull { it.key == key }?.values?.takeIf { it.isNotEmpty() })
        }
        if (before.startDeclaration != after.startDeclaration) {
            out += SessionLogEntry(kind = Kind.START_DECLARATION.name, sessionMs = now, key = after.startDeclaration?.reference?.name, note = after.startDeclaration?.note)
        }
        for (side in (before.manualLengthsM.keys + after.manualLengthsM.keys).sorted()) {
            if (before.manualLengthsM[side] != after.manualLengthsM[side]) out += SessionLogEntry(kind = Kind.MANUAL_LENGTH.name, sessionMs = now, side = side, value = after.manualLengthsM[side])
        }
        for (side in (before.acceptedSketchLengths + after.acceptedSketchLengths).sorted()) {
            val was = side in before.acceptedSketchLengths; val isNow = side in after.acceptedSketchLengths
            if (was != isNow) out += SessionLogEntry(kind = Kind.SKETCH_LENGTH_ACCEPTED.name, sessionMs = now, side = side, value = if (isNow) 1.0 else 0.0)
        }
        return out
    }

    fun applied(s: SketchSession, corners: List<com.sagoma.planimetria.scan.ArXZ>) =
        SessionLogEntry(kind = Kind.APPLIED.name, sessionMs = s.engine.lastTimeMs.takeIf { it != Long.MIN_VALUE }, corners = corners.flatMap { listOf(it.x, it.z) })

    fun cancelled(s: SketchSession) = SessionLogEntry(kind = Kind.CANCELLED.name, sessionMs = s.engine.lastTimeMs.takeIf { it != Long.MIN_VALUE })

    /** La retta confermata di un evento [Kind.WALL_ASSIGNED], come la usa il nucleo (null se l'evento non ne ha una). */
    fun confirmedLine(e: SessionLogEntry): ConfirmedLine? {
        val l = e.line?.takeIf { it.size == 6 } ?: return null
        val s = e.sigma?.takeIf { it.size == 2 } ?: listOf(0.0, 0.0)
        return ConfirmedLine(
            com.sagoma.planimetria.scan.ArXZ(l[0], l[1]), com.sagoma.planimetria.scan.ArXZ(l[2], l[3]), com.sagoma.planimetria.scan.ArXZ(l[4], l[5]),
            s[0], s[1], inwardKnown = e.inwardKnown ?: true, supportAlongM = e.support,
        )
    }

    fun encode(h: SessionLogHeader): String = json.encodeToString(SessionLogHeader.serializer(), h)
    fun encode(e: SessionLogEntry): String = json.encodeToString(SessionLogEntry.serializer(), e)

    /** Il file letto: intestazione (se c'è) ed eventi; righe vuote, di tipo sconosciuto o illeggibili si saltano. */
    fun parse(text: String): Pair<SessionLogHeader?, List<SessionLogEntry>> {
        var header: SessionLogHeader? = null
        val events = ArrayList<SessionLogEntry>()
        for (line in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            val type = runCatching { json.parseToJsonElement(line).jsonObject["type"]?.jsonPrimitive?.content }.getOrNull()
            when (type) {
                "header" -> header = runCatching { json.decodeFromString(SessionLogHeader.serializer(), line) }.getOrNull()
                "event" -> runCatching { json.decodeFromString(SessionLogEntry.serializer(), line) }.getOrNull()?.let { events += it }
            }
        }
        return header to events
    }
}
