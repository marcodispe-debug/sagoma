package com.sagoma.planimetria.scan.reconstruction.rescan.session

import com.sagoma.planimetria.geometry.formatDecimal
import kotlinx.serialization.json.Json

/** Serializzazione JSON deterministica della sessione (ordine dei campi = dichiarazione, liste già ordinate dal motore). */
object RescanSessionJson {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    fun encode(s: RescanSession): String = json.encodeToString(RescanSession.serializer(), s) + "\n"
    fun decode(text: String): RescanSession = json.decodeFromString(RescanSession.serializer(), text)
}

/** Report di M5.1: file NUOVI. Racconta la storia dei bersagli con i motivi codificati e i valori prima/dopo. */
object RescanSessionReport {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else formatDecimal(v, d, '.')

    private fun obs(o: TargetObservation?): String {
        if (o == null) return ""
        return listOfNotNull(
            if (o.open) "aperto" else "chiuso",
            o.endpointState?.let { "estremità $it" }, o.unobservedM?.let { "non osservato ${f(it)} m" },
            o.perimeterState?.let { "perimetro $it" }, o.missingSideM?.let { "lato mancante ${f(it)} m" },
            o.viewCount?.let { "viste $it" }, o.measurementLevel?.let { "misura $it" }, o.ambiguityScore?.let { "ambiguità ${f(it)}" }, o.ambiguityStability?.let { "stabilità ${f(it)}" },
        ).joinToString(", ")
    }

    fun eventsCsv(s: RescanSession): String = buildString {
        append("scansione;bersaglio;tipo;parete;da;a;motivo;variazioneM;corrispondenzaParete;precedente;corrente;bersaglioCollegato;candidati\n")
        val rows = s.targets.flatMap { t -> t.history.map { e -> t to e } }.sortedWith(compareBy({ it.second.scanIndex }, { it.first.key }))
        for ((t, e) in rows) append(listOf(e.scanIndex, t.key, t.kind, t.wallKey ?: "", e.from ?: "", e.to, e.reason, f(e.changeM), e.matchStatus ?: "", e.previous?.let { obs(it) } ?: "nessuno", obs(e.current), e.relatedTargetKey ?: "",
            e.candidates.joinToString(" ") { c -> "W${c.fragment.r3WallId}@${c.fragment.scanIndex}/${c.wallKey}${c.end?.let { "/$it" } ?: ""}=${c.verdict}" + (c.followedBy?.let { "→$it" } ?: "") + (c.alternativeKey?.let { "[$it]" } ?: "") })
            .joinToString(";") { it.toString().replace(";", ",") }).append('\n')
    }

    fun summary(s: RescanSession): String = buildString {
        appendLine("M5.1 — SESSIONE DI RISCANSIONE ${s.sessionId}")
        appendLine("ATTEMPTED = una nuova scansione è stata elaborata con il bersaglio aperto: NON prova che l'utente abbia guardato lì.")
        appendLine("Nessun EXHAUSTED, nessun bersaglio nascosto o eliminato. Pareti ritrovate con: angolo ≤ 1,5°, posizione ≤ 3 cm + 3σ, estensioni ≤ 0,20 m.")
        appendLine()
        for (sc in s.scans) appendLine("Scansione ${sc.index}: ${sc.dataset} · ${sc.frameStatus} · M5 ${sc.m5Decision} · richieste ${sc.m5RequestIds.joinToString(" ").ifEmpty { "—" }}")
        val last = s.scans.last().index
        val m = s.wallMatches.filter { it.scanIndex == last }
        if (m.isNotEmpty()) {
            appendLine(); appendLine("PARETI (scansione $last)")
            for (w in m) appendLine("  ${w.previousKey ?: "—"} → ${w.currentR3WallId?.let { "W$it" } ?: "—"}: ${w.status}" +
                (if (w.deltaPositionM != null) " · Δposizione ${f(w.deltaPositionM)} m (tolleranza ${f(w.positionToleranceM)}) · Δangolo ${f(w.deltaAngleDeg, 2)}° · sovrapposizione ${f(w.overlapM)} m" else "") +
                (if (w.status == MatchStatus.NO_GEOMETRIC_MATCH) " · più vicina W${w.nearestR3WallId ?: "—"}, regole non soddisfatte: ${w.failedRules.joinToString(" ")}" else "") +
                (if (w.relations.isNotEmpty()) " · relazioni ${w.relations.joinToString(" ")}" else "") +
                (if (w.candidates.isNotEmpty()) " · frammenti ${w.candidates.joinToString(" ") { "W${it.fragment.r3WallId}:${it.role}" }}" else
                    if (w.alternatives.isNotEmpty()) " · alternative ${w.alternatives.joinToString(" ")}" else "") +
                (if (w.mergedWith.isNotEmpty()) " · fusa con ${w.mergedWith.joinToString(" ")}" else "") +
                (if (w.ambiguousWith.isNotEmpty()) " · ambigua con ${w.ambiguousWith.joinToString(" ")}" else "") +
                (w.dormantSinceScan?.let { " · ritrovata (non osservata dalla scansione $it)" } ?: ""))
        }
        val rel = s.wallRelations.filter { it.scanIndex == last }
        if (rel.isNotEmpty()) { appendLine(); appendLine("RELAZIONI TRA PARETI (scansione $last; pareti distinte, non identità)"); for (r in rel) appendLine("  ${r.a} ~ ${r.b}: ${r.kind} (${r.basis})") }
        val alts = s.targetAlternatives.filter { it.status == AlternativeStatus.ACTIVE }
        if (alts.isNotEmpty()) { appendLine(); appendLine("ALTERNATIVE SOSPESE (attive; mai eliminate, rivalutate a ogni scansione)"); for (a in alts) appendLine("  ${a.targetKey}/${a.altKey}: parete ${a.wallKey}${a.end?.let { " $it" } ?: ""} · ultimo verdetto ${a.lastVerdict} (scansione ${a.lastEvaluatedScan})") }
        if (s.targetLinks.isNotEmpty()) { appendLine(); appendLine("COLLEGAMENTI DI INCERTEZZA (NON sono identità confermate)"); for (l in s.targetLinks) appendLine("  ${l.newTargetKey} → ${l.historicalTargetKey} (scansione ${l.scanIndex}): evidenze mancanti ${l.failedConditions.joinToString(" ")}") }
        appendLine(); appendLine("BERSAGLI")
        for (state in TargetState.values()) {
            val ts = s.targets.filter { it.state == state }
            if (ts.isEmpty()) continue
            appendLine("  $state (${ts.size})")
            for (t in ts) {
                val e = t.history.last()
                appendLine("    ${t.key} [${t.kind}] tentativi ${t.attempts}${if (t.reopenCount > 0) " · riaperture ${t.reopenCount}" else ""}${if (t.frameEpoch > 0) " · epoca coordinate ${t.frameEpoch}" else ""} · ultimo motivo ${e.reason}" +
                    (e.changeM?.let { " (${f(it)} m)" } ?: ""))
                val first = e.reason == TransitionReason.EMITTED || e.reason == TransitionReason.FIRST_SEEN
                appendLine("      ${if (first) "" else "precedente: ${e.previous?.let { obs(it) } ?: "nessuno"} · "}corrente: ${obs(e.current).ifEmpty { "—" }}${e.relatedTargetKey?.let { " · collegato a $it" } ?: ""}")
            }
        }
    }
}
