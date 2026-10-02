package com.sagoma.planimetria.editor


/**
 * Suggerimenti al primo utilizzo: ognuno compare una volta sola, la prima volta che si usa la sua
 * funzione. I nomi delle costanti finiscono nel file dei suggerimenti visti: si aggiungono, non si rinominano.
 */
enum class Tip(val title: String, val text: String) {
    Welcome(
        "Benvenuto",
        "Tocca una stanza per selezionarla. Con due dita fai zoom, trascinando il fondo sposti la pianta. " +
            "Se sbagli, ↶ Annulla in alto rimette tutto com'era.",
    ),
    Room(
        "Stanza selezionata",
        "Trascina i pallini blu per spostare gli angoli e i muri per allungarla; trascinando il nome sposti tutta la stanza. " +
            "Per nome, tipo e misure esatte dei lati premi Info o scorri in su la striscia in basso.",
    ),
    Wall(
        "Muro",
        "Trascinalo per spostarlo: si aggancia ai muri delle stanze vicine. Da Info cambi l'altezza e, se è in comune " +
            "con un'altra stanza, puoi eliminarlo per unire le due stanze in un unico ambiente.",
    ),
    NewRoom(
        "Nuova stanza",
        "La stanza nuova viene messa accanto alle altre. Trascinala dal nome per avvicinarla: si aggancia ai muri delle stanze vicine.",
    ),
    Opening(
        "Porte e finestre",
        "Trascinala lungo il muro per spostarla. Da Info cambi misure, battente o scorrevole, lato del cardine e verso di apertura, oppure la rimuovi.",
    ),
    Fixture(
        "Impianti",
        "Gli impianti a muro scorrono lungo il muro, le luci si trascinano dove vuoi nella stanza. " +
            "Da Info imposti altezza da terra, misure e rotazione, oppure lo rimuovi.",
    ),
    Ruler(
        "Metro",
        "Trascina le estremità per allungarlo e il centro per spostarlo. Con la maniglia tonda lo ruoti (si aggancia ogni 45°), con × lo elimini.",
    ),
    WallCut(
        "Parete sotto il tetto",
        "La parete scende in diagonale fino all'altezza del muro basso. Trascina il pallino sul muro, o scrivi la distanza, " +
            "per scegliere da dove inizia a scendere. Il tratteggio mostra dove comincia la pendenza del soffitto; in 3D si vede tutto.",
    ),
    View3D(
        "Vista 3D",
        "Trascina con un dito per ruotare, con due dita zoomi e sposti. Tocca un muro, una porta o un impianto per selezionarlo: " +
            "se è selezionato lo trascini per spostarlo. Con 🚶 Cammina entri nelle stanze e ti muovi col joystick. " +
            "Sul computer: ↑ ↓ (o W S) avanti e indietro, ← → per girare, A D per spostarti di lato, PagSu/PagGiù " +
            "per alzare o abbassare lo sguardo, Maiusc per andare più veloce; rotellina per lo zoom, tasto destro " +
            "trascinato per spostare la vista. Il joystick in basso a sinistra fa lo stesso.",
    ),
    Stair(
        "Scala",
        "Trascinala per spostarla: vicino a un muro ci si accosta da sola. Da Info scegli tipo, larghezza, pedata e da che parte gira, " +
            "e la ruoti di 90°. Numero e altezza dei gradini si calcolano dall'interpiano; al piano di sopra compare il vuoto della scala.",
    ),
    Outdoor(
        "Balcone e terrazza",
        "È uno spazio all'aperto: al posto dei muri c'è il parapetto, contro la casa resta il muro della casa. Accostalo a un muro " +
            "e mettici una porta-finestra (+ Infissi → Balcone) sul muro della stanza. Da Info scegli ringhiera, muretto o vetro e l'altezza.",
    ),
    Structure(
        "Colonne e travi",
        "Trascina una colonna per spostarla: vicino a un muro ci si accosta e diventa un pilastro che sporge. Una trave si sposta " +
            "trascinandola e si allunga dai pallini alle estremità, che si appoggiano ai muri. Da Info cambi forma e misure.",
    ),
    Measure(
        "Distanza",
        "Tocca il primo oggetto e poi il secondo: muri, porte e finestre, termosifoni, prese, luci, colonne, travi, scale. " +
            "Compare la distanza tra i loro bordi più vicini, con la quota sulla pianta; se sposti gli oggetti si aggiorna.",
    ),
    FreeWall(
        "Muro singolo",
        "Un tramezzo che non chiude una stanza. Trascinalo per spostarlo e trascina i pallini alle estremità per allungarlo o girarlo: " +
            "le estremità si agganciano alle facce degli altri muri e alle estremità di altri muri singoli, e il muro si raddrizza a 90°. " +
            "Da Info cambi spessore, altezza e colore.",
    ),
    Floors(
        "Piani",
        "Con il pulsante del piano, in alto a sinistra, passi da un piano all'altro e ne aggiungi di nuovi. Il piano di sotto si vede " +
            "in grigio per allineare i muri, e le sue scale compaiono come vuoto nel pavimento. In 3D vedi il piano scelto sopra quelli di sotto.",
    ),
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
