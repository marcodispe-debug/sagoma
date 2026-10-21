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
    }
}

object NoTipStore : TipStore {
    override fun seen(): Set<String> = emptySet()
    override fun save(seen: Set<String>) = Unit
}

/**
 * Coda dei suggerimenti: uno alla volta; quelli chiesti mentre ne è aperto un altro aspettano il loro turno.
 * `current` è quello da mostrare (null = nessuno).
 */
class TipQueue(private val store: TipStore) {
    private val stored = store.seen().toMutableSet()
    private val seen = Tip.entries.filter { it.name in stored }.toMutableSet()

    private fun persist() {
        stored.removeAll { name -> Tip.entries.any { it.name == name } }
        stored += seen.map { it.name }
        store.save(stored)
    }

    /** Tutorial guidato già completato o saltato. */
    var tutorialDone: Boolean
        get() = TipStore.TUTORIAL_DONE in stored
        set(value) {
            if (value) stored += TipStore.TUTORIAL_DONE else stored -= TipStore.TUTORIAL_DONE
            store.save(stored)
        }

    /** Guida al ricalco già completata o chiusa. */
    var tracingGuideDone: Boolean
        get() = TipStore.TRACING_GUIDE_DONE in stored
        set(value) {
            if (value) stored += TipStore.TRACING_GUIDE_DONE else stored -= TipStore.TRACING_GUIDE_DONE
            store.save(stored)
        }
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
        current?.let { seen += it; persist() }
        current = waiting.removeFirstOrNull()
    }

    /** Segna come visti i suggerimenti indicati (es. quelli delle funzioni già spiegate dal tutorial). */
    fun markSeen(tips: Collection<Tip>) {
        seen += tips
        persist()
        waiting.removeAll { it in tips }
        if (current in tips) current = waiting.removeFirstOrNull()
    }

    /** "Salta tutti": nessun suggerimento comparirà più (finché non si chiede di rivederli). */
    fun skipAll() {
        seen += Tip.entries
        persist()
        waiting.clear()
        current = null
    }

    /** "Rivedi i suggerimenti": tutti tornano come mai visti. */
    fun reset() {
        seen.clear()
        persist()
        waiting.clear()
        current = null
    }
}
