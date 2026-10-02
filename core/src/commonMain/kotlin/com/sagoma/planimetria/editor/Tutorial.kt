package com.sagoma.planimetria.editor

/**
 * Passi del tutorial guidato. Ogni passo con `action = true` chiede all'utente di fare qualcosa sulla
 * pianta e si completa da solo quando l'ha fatto; gli altri si superano con un pulsante.
 */
enum class TutorialStep(val title: String, val text: String, val action: Boolean = true) {
    Intro(
        "Tutorial",
        "Ti guido passo passo nelle funzioni principali: ogni passo ti chiede di provare tu. " +
            "Sono modifiche vere alla pianta, ma alla fine puoi annullarle con ↶.",
        action = false,
    ),
    OpenInfo(
        "Le informazioni della stanza",
        "Tocca una stanza per selezionarla: in basso compare la striscia Info. Premi Info, la freccia ▲ " +
            "o scorri la striscia verso l'alto per aprire le sue caratteristiche.",
    ),
    WallLengths(
        "Cambia le misure",
        "Nella tendina, sotto «Muri», c'è la lunghezza di ogni lato: scrivi un nuovo valore e conferma. " +
            "Il lato opposto si adegua da solo. Poi cambia un lato nell'altra direzione. Le quote sulla pianta si aggiornano.",
    ),
    ChangeType(
        "Tipo di stanza",
        "Sempre nella tendina, scegli un tipo di stanza, ad esempio Cucina: colore e nome cambiano da soli.",
    ),
    MoveWall(
        "Sposta un muro",
        "Chiudi la tendina (▼ o scorri in giù). Poi tocca un muro per selezionarlo e trascinalo: " +
            "la stanza si allarga o si stringe, e il muro si aggancia a quelli delle stanze vicine.",
    ),
    MoveCorner(
        "Sposta un angolo",
        "Trascina uno dei pallini blu agli angoli della stanza: puoi anche storcere i muri.",
    ),
    AddDoor(
        "Aggiungi una porta",
        "Premi + Porte, scegli un tipo e poi tocca il muro su cui metterla.",
    ),
    MoveDoor(
        "Sposta la porta",
        "Trascina la porta lungo il muro. Da Info ne cambi misure, verso di apertura o la rendi scorrevole.",
    ),
    AddFixture(
        "Impianti",
        "Premi + Impianti e scegli, ad esempio, Presa di corrente, poi tocca un muro. Le luci invece si mettono toccando un punto dentro la stanza.",
    ),
    AddRoom(
        "Aggiungi una stanza",
        "Premi + Aggiungi stanza (in orizzontale + Stanza), scegli forma e misure e conferma.",
    ),
    MoveRoom(
        "Accosta le stanze",
        "Trascina la nuova stanza tenendola per il nome e avvicinala a un'altra: quando i muri sono vicini si agganciano da soli.",
    ),
    Undo(
        "Annulla",
        "Hai sbagliato qualcosa? Premi ↶ Annulla in alto per tornare indietro di un passo (↷ Ripeti lo rifà).",
    ),
    Ruler(
        "Il metro",
        "Premi 📏 Metro: trascina le estremità per allungarlo, il centro per spostarlo, la maniglia tonda per ruotarlo; con × lo elimini.",
    ),
    Done(
        "Fatto!",
        "Ora conosci le funzioni principali. Dal menu ⋮ puoi esportare la pianta in PNG e rifare il tutorial quando vuoi.",
        action = false,
    ),
}

/** Azioni dell'utente che possono completare un passo del tutorial. */
sealed interface TutorialEvent {
    data class WallLength(val roomId: Long, val index: Int, val wallCount: Int) : TutorialEvent
    data object WallDragged : TutorialEvent
    data object CornerDragged : TutorialEvent
    data object InfoOpened : TutorialEvent
    data object RoomTypeChanged : TutorialEvent
    data object OpeningAdded : TutorialEvent
    data object OpeningDragged : TutorialEvent
    data object FixtureAdded : TutorialEvent
    data object RoomAdded : TutorialEvent
    data object RoomMoved : TutorialEvent
    data object Undo : TutorialEvent
    data object RulerAdded : TutorialEvent
}

/** Cosa mostra la scheda del tutorial. `detail`: avanzamento dentro il passo (es. lati cambiati). */
data class TutorialUi(val step: TutorialStep, val number: Int, val total: Int, val detail: String?)

/** Avanzamento del tutorial: passo corrente e, per il primo passo, i lati già modificati. */
class Tutorial {
    var step: TutorialStep? = null
        private set

    /** Lati modificati per stanza, nel passo "Cambia le misure". */
    private val edited = mutableMapOf<Long, MutableSet<Int>>()
    private val wallCounts = mutableMapOf<Long, Int>()

    val active: Boolean get() = step != null

    fun start() {
        step = TutorialStep.Intro
        edited.clear()
    }

    fun stop() {
        step = null
    }

    /** Passo successivo (pulsante "Inizia"/"Salta passo", o passo completato). Null dopo l'ultimo. */
    fun next() {
        val s = step ?: return
        step = TutorialStep.entries.getOrNull(s.ordinal + 1)
    }

    /** Registra un'azione dell'utente; true se ha completato il passo corrente (e si è passati al successivo). */
    fun on(event: TutorialEvent): Boolean {
        val done = when (step) {
            TutorialStep.WallLengths -> event is TutorialEvent.WallLength && recordWall(event)
            TutorialStep.MoveWall -> event == TutorialEvent.WallDragged
            TutorialStep.MoveCorner -> event == TutorialEvent.CornerDragged
            TutorialStep.OpenInfo -> event == TutorialEvent.InfoOpened
            TutorialStep.ChangeType -> event == TutorialEvent.RoomTypeChanged
            TutorialStep.AddDoor -> event == TutorialEvent.OpeningAdded
            TutorialStep.MoveDoor -> event == TutorialEvent.OpeningDragged
            TutorialStep.AddFixture -> event == TutorialEvent.FixtureAdded
            TutorialStep.AddRoom -> event == TutorialEvent.RoomAdded
            TutorialStep.MoveRoom -> event == TutorialEvent.RoomMoved
            TutorialStep.Undo -> event == TutorialEvent.Undo
            TutorialStep.Ruler -> event == TutorialEvent.RulerAdded
            else -> false
        }
        if (done) next()
        return done
    }

    /**
     * Due lati della stessa stanza in direzioni diverse (larghezza e profondità): in una stanza a 4 lati
     * il lato opposto si adegua da solo, quindi cambiarlo non insegna niente di nuovo. Nelle forme a L
     * bastano due lati qualsiasi.
     */
    private fun recordWall(e: TutorialEvent.WallLength): Boolean {
        edited.getOrPut(e.roomId) { mutableSetOf() } += e.index
        wallCounts[e.roomId] = e.wallCount
        return edited.any { (roomId, set) -> hasPair(set, wallCounts[roomId] ?: 0) }
    }

    private fun hasPair(set: Set<Int>, wallCount: Int): Boolean =
        if (wallCount == 4) set.any { i -> (i + 1) % 4 in set || (i + 3) % 4 in set } else set.size >= 2

    val ui: TutorialUi?
        get() {
            val s = step ?: return null
            val detail = if (s == TutorialStep.WallLengths) {
                val best = edited.maxOfOrNull { it.value.size } ?: 0
                when (best) {
                    0 -> "Lati cambiati: 0 di 2"
                    else -> "Lati cambiati: 1 di 2 · ora un lato nell'altra direzione"
                }
            } else null
            return TutorialUi(s, s.ordinal, TutorialStep.entries.size - 2, detail)
        }
}
