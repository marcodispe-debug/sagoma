package com.sagoma.planimetria.editor

import com.sagoma.planimetria.model.Building

/**
 * «Controlla la pianta» in tempo reale, mentre si disegna: quanti problemi ci sono (il segnale sulla pianta, che apre l'elenco) e
 * quali errori di disegno (⚠) sono appena comparsi con una modifica (l'avviso breve con «Mostra» e «Correggi»).
 */
object LiveCheck {
    /** Il controllo parte dopo tanto (ms) dall'ultima modifica: mentre si scrive una misura o si sposta qualcosa non si ripete. */
    const val DELAY_MS = 500L

    /** L'avviso di un errore nuovo resta tanto (ms), poi basta il segnale. */
    const val NOTICE_MS = 10_000L

    /** Problemi per gruppo: errori di disegno (⚠), cose da controllare (?), minimi delle norme (📏). */
    data class Counts(val errors: Int, val checks: Int, val rules: Int) {
        val total: Int get() = errors + checks + rules
    }

    fun counts(issues: List<PlanCheck.Issue>): Counts = Counts(
        errors = issues.count { it.kind.error },
        checks = issues.count { !it.kind.error && !it.kind.rule },
        rules = issues.count { it.kind.rule },
    )

    /**
     * Lo stesso problema anche quando le sue misure cambiano un po' (la stanza sovrapposta che si sposta ancora): tipo, piano,
     * stanze coinvolte e cosa selezionare.
     */
    fun key(i: PlanCheck.Issue): Any = listOf(i.kind, i.floor, i.rooms.sorted(), i.selection)

    /**
     * Errori di disegno (⚠) di `now` che in `before` non c'erano: quelli appena fatti, da dire subito. Conta quanti errori di
     * ogni tipo ci sono tra le stesse stanze: un errore che resta ma cambia muro selezionato (angolo tolto, muri rinumerati)
     * non è nuovo.
     */
    fun newErrors(before: List<PlanCheck.Issue>, now: List<PlanCheck.Issue>): List<PlanCheck.Issue> {
        fun group(i: PlanCheck.Issue) = Triple(i.kind, i.floor, i.rooms.toSet())
        val old = before.filter { it.kind.error }.groupingBy(::group).eachCount()
        val oldKeys = before.mapTo(HashSet()) { key(it) }
        val errors = now.filter { it.kind.error }
        val grown = errors.groupingBy(::group).eachCount().filter { (g, n) -> n > (old[g] ?: 0) }.keys
        return errors.filter { group(it) in grown && key(it) !in oldKeys }
    }

    /**
     * L'errore appena fatto da dire con l'avviso ([newErrors]): il primo, ma non uno tolto dalla pianta nella casa `b` (quella di
     * `now`), che si è già visto.
     */
    fun notice(before: List<PlanCheck.Issue>, now: List<PlanCheck.Issue>, b: Building): PlanCheck.Issue? =
        newErrors(before, now).firstOrNull { !PlanCheck.hidden(b, it) }

    /** L'avviso `notice` vale ancora (l'errore c'è ancora in `now`): annullato o corretto, l'avviso sparisce. */
    fun stillThere(notice: PlanCheck.Issue, now: List<PlanCheck.Issue>): PlanCheck.Issue? {
        val k = key(notice)
        return now.firstOrNull { key(it) == k }
    }
}
