package com.sagoma.planimetria.editor


/**
 * Suggerimenti al primo utilizzo: ognuno compare una volta sola, la prima volta che si usa la sua
 * funzione. I nomi delle costanti finiscono nel file dei suggerimenti visti: si aggiungono, non si rinominano.
 */
enum class Tip {
    Welcome,
    Room,
    Wall,
    NewRoom,
    Opening,
    Fixture,
    Ruler,
    WallCut,
    View3D,
    Stair,
    Outdoor,
    Structure,
    Measure,
    FreeWall,
    Floors,
    Tracing,
    Calibrate,
    WallDraw,
    Underlay,
    ExcludedZone,
    RoomInfo,
    Snap,
    ;

    /** Titolo e testo, nella lingua dei testi ([com.sagoma.planimetria.testi.Suggerimenti]). */
    val title: String get() = com.sagoma.planimetria.testi.T.suggerimenti.titolo(this)
    val text: String get() = com.sagoma.planimetria.testi.T.suggerimenti.testo(this)
}

/**
 * Guide già viste (nomi dei suggerimenti e [TUTORIAL_DONE] per il tutorial), conservate tra un avvio
 * e l'altro.
 */
interface TipStore {
    fun seen(): Set<String>
    fun save(seen: Set<String>)

    companion object {
        /** Tutorial guidato completato o saltato. */
        const val TUTORIAL_DONE = "tutorial"

        /** Guida al ricalco di una piantina completata o chiusa con "Esci". */
        const val TRACING_GUIDE_DONE = "guida-ricalco"

        /** Guida interattiva (le lezioni sul progetto di prova) finita o chiusa con "Esci": al primo avvio non riparte. */
        const val COURSE_DONE = "guida-interattiva"
    }
}

object NoTipStore : TipStore {
    override fun seen(): Set<String> = emptySet()
    override fun save(seen: Set<String>) = Unit
}

/**
 * Coda dei suggerimenti: uno alla volta; quelli chiesti mentre ne è aperto un altro aspettano il loro turno.
 * `current` è quello da mostrare (null = nessuno).
 *
 * Ogni scrittura riparte da ciò che c'è nell'archivio: ci scrivono anche gli altri progetti aperti (la guida interattiva passa
 * da un progetto di prova all'altro) e l'elenco dei progetti di prova, che non vanno persi.
 */
class TipQueue(private val store: TipStore) {
    private var stored: Set<String> = store.seen()
    private val seen = Tip.entries.filter { it.name in stored }.toMutableSet()

    /** Le voci salvate; dove l'archivio non conserva niente ([NoTipStore]) quelle scritte finora. */
    private fun saved(): Set<String> = store.seen().ifEmpty { stored }

    private fun write(change: MutableSet<String>.() -> Unit) {
        val next = saved().toMutableSet().apply(change)
        stored = next
        store.save(next)
        seen.clear()
        seen += Tip.entries.filter { it.name in next }
    }

    private fun flag(name: String, value: Boolean) = write { if (value) add(name) else remove(name) }

    /** Tutorial guidato già completato o saltato. */
    var tutorialDone: Boolean
        get() = TipStore.TUTORIAL_DONE in saved()
        set(value) = flag(TipStore.TUTORIAL_DONE, value)

    /** Guida al ricalco già completata o chiusa. */
    var tracingGuideDone: Boolean
        get() = TipStore.TRACING_GUIDE_DONE in saved()
        set(value) = flag(TipStore.TRACING_GUIDE_DONE, value)

    /** Guida interattiva già finita o chiusa. */
    var courseDone: Boolean
        get() = TipStore.COURSE_DONE in saved()
        set(value) = flag(TipStore.COURSE_DONE, value)

    private val waiting = ArrayDeque<Tip>()
    var current: Tip? = null
        private set

    /** Chiede di mostrare `tip` se non è mai stato visto. */
    fun request(tip: Tip) {
        if (tip in seen || tip == current || tip in waiting) return
        if (current == null) current = tip else waiting.addLast(tip)
    }

    /** "Ho capito": il suggerimento non ricompare più, si passa al prossimo in attesa. */
    fun dismiss() {
        current?.let { t -> write { add(t.name) } }
        current = waiting.removeFirstOrNull()
    }

    /** Segna come visti i suggerimenti indicati (es. quelli delle funzioni già spiegate dal tutorial). */
    fun markSeen(tips: Collection<Tip>) {
        write { addAll(tips.map { it.name }) }
        waiting.removeAll { it in tips }
        if (current in tips) current = waiting.removeFirstOrNull()
    }

    /** I suggerimenti indicati tornano come mai visti (la guida interattiva li fa leggere). */
    fun unsee(tips: Collection<Tip>) = write { removeAll(tips.map { it.name }.toSet()) }

    /** "Salta tutti": nessun suggerimento comparirà più (finché non si chiede di rivederli). */
    fun skipAll() {
        write { addAll(Tip.entries.map { it.name }) }
        waiting.clear()
        current = null
    }

    /** "Rivedi i suggerimenti": tutti tornano come mai visti. */
    fun reset() {
        write { removeAll(Tip.entries.map { it.name }.toSet()) }
        waiting.clear()
        current = null
    }
}
