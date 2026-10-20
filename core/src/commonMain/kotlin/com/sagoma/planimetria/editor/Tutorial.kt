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
        "Premi «🧱 Elementi architettonici» → Porte, scegli un tipo e poi tocca il muro su cui metterla.",
    ),
    MoveDoor(
        "Sposta la porta",
        "Trascina la porta lungo il muro. Da Info ne cambi misure, verso di apertura o la rendi scorrevole.",
    ),
    AddFixture(
        "Impianti",
        "Premi «🧱 Elementi architettonici» → Impianti e scegli, ad esempio, Presa di corrente, poi tocca un muro. Le luci invece si mettono toccando un punto dentro la stanza.",
    ),
    AddRoom(
        "Aggiungi una stanza",
        "Premi «🧱 Elementi architettonici» → Stanza, scegli forma e misure e conferma.",
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
        "Premi «🛠 Strumenti» → Metro: trascina le estremità per allungarlo, il centro per spostarlo, la maniglia tonda per ruotarlo; con × lo elimini.",
    ),
    Done(
        "Fatto!",
        "Ora conosci le funzioni principali. Da «❔ Guide», in alto a destra, rifai il tutorial quando vuoi e trovi una guida per ogni funzione.",
        action = false,
    ),

    // ---------- Guida al ricalco di una piantina ----------

    TraceLoad(
        "Carica la piantina",
        "Premi «Foto / immagine» (o «PDF» sul computer) e scegli la piantina della casa: catasto, progetto o una foto dritta.",
    ),
    TraceCalibA(
        "Taratura: il primo punto",
        "Cerca sulla piantina una misura scritta, meglio se lunga (ad esempio un muro quotato 4,72). " +
            "Ingrandisci e tocca l'inizio di quella misura: compare il punto A.",
    ),
    TraceCalibB(
        "Taratura: il secondo punto",
        "Ora tocca la fine della stessa misura: compare il punto B. Se sbagli, un altro tocco ricomincia da A.",
    ),
    TraceCalibValue(
        "Taratura: la misura vera",
        "Nella scheda in alto scrivi quanto è lunga davvero la misura tra A e B (in cm, come sulla piantina) e conferma: " +
            "la piantina va in scala.",
    ),
    TraceFirstCorner(
        "Il primo angolo",
        "Ora sei in «Muro per muro». Scegli una stanza e clicca un suo angolo, sul lato interno del muro: " +
            "lo spessore dei muri si aggiunge da solo verso l'esterno.",
    ),
    TraceCorners(
        "Gli altri angoli",
        "Clicca gli altri angoli uno dopo l'altro, girando intorno alla stanza. Se sbagli, «↶ Ultimo» toglie l'ultimo angolo.",
    ),
    TraceClose(
        "Chiudi la stanza",
        "Quando hai cliccato tutti gli angoli, clicca di nuovo il primo (quello cerchiato) o premi «Chiudi stanza»; " +
            "poi scegli il tipo di stanza e conferma.",
    ),
    TraceCheck(
        "Controlla le misure",
        "Apri Info della stanza (striscia in basso, ▲ o scorri in su): confronta m² e muri con la piantina. " +
            "Se una parte non conta nei m² (mansarda bassa, cavedio) usa «▧ Aggiungi zona non calpestabile» nella scheda Misure.",
    ),
    TraceFade(
        "Piantina e disegno",
        "Chiudi la tendina e premi «◐ Piantina e disegno» in basso a destra: rendi la piantina più chiara o nascondila " +
            "per vedere bene il disegno.",
    ),
    TraceNext(
        "La stanza accanto",
        "Premi «🧱 Elementi architettonici» → Stanza, scegli «Muro per muro» e ricalca la stanza accanto, angolo dopo angolo come prima.",
    ),
    TraceDone(
        "Fatto!",
        "Continua così con le altre stanze. Se gli agganci ti spostano i punti, spegnili con 🧲. " +
            "Con «Fine», se non l'hai ancora fatto, parte il tutorial delle altre funzioni (porte, impianti, misure).",
        action = false,
    ),
}

/** Percorsi guidati: il tutorial delle funzioni principali e la guida al ricalco di una piantina. */
enum class TutorialCourse(val steps: List<TutorialStep>) {
    Main(TutorialStep.entries.takeWhile { it != TutorialStep.TraceLoad }),
    Tracing(TutorialStep.entries.dropWhile { it != TutorialStep.TraceLoad }),
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
    data object UnderlayLoaded : TutorialEvent
    /** Taratura: punti già toccati (1 = solo A, 2 = A e B). */
    data class CalibrationPoints(val count: Int) : TutorialEvent
    data object Calibrated : TutorialEvent
    /** Muro per muro: angoli messi finora nella stanza che si sta disegnando. */
    data class CornersPlaced(val count: Int) : TutorialEvent
    /** Piantina o disegno mostrati, nascosti o resi più trasparenti. */
    data object PlanViewChanged : TutorialEvent
}

/** Cosa mostra la scheda del tutorial. `detail`: avanzamento dentro il passo (es. lati cambiati). */
data class TutorialUi(
    val step: TutorialStep,
    val number: Int,
    val total: Int,
    val detail: String?,
    val course: TutorialCourse = TutorialCourse.Main,
)

/** Avanzamento del tutorial: percorso, passo corrente e, per il passo delle misure, i lati già modificati. */
class Tutorial {
    var step: TutorialStep? = null
        private set
    var course: TutorialCourse = TutorialCourse.Main
        private set

    /** Lati modificati per stanza, nel passo "Cambia le misure". */
    private val edited = mutableMapOf<Long, MutableSet<Int>>()
    private val wallCounts = mutableMapOf<Long, Int>()

    /** Angoli della stanza che si sta ricalcando, nella guida al ricalco. */
    private var corners = 0

    val active: Boolean get() = step != null

    fun start(course: TutorialCourse = TutorialCourse.Main, from: TutorialStep = course.steps.first()) {
        this.course = course
        step = from
        edited.clear()
        corners = 0
    }

    fun stop() {
        step = null
    }

    /** Passo successivo (pulsante "Inizia"/"Salta passo", o passo completato). Null dopo l'ultimo. */
    fun next() {
        val s = step ?: return
        step = course.steps.getOrNull(course.steps.indexOf(s) + 1)
    }

    /** Registra un'azione dell'utente; true se ha completato il passo corrente (e si è passati al successivo). */
    fun on(event: TutorialEvent): Boolean {
        if (event is TutorialEvent.CornersPlaced) corners = event.count.coerceAtLeast(0)
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
            TutorialStep.TraceLoad -> event == TutorialEvent.UnderlayLoaded
            // Durante la taratura il passo segue i punti toccati: il terzo tocco ricomincia da A, e la guida torna indietro.
            TutorialStep.TraceCalibA, TutorialStep.TraceCalibB, TutorialStep.TraceCalibValue -> return when (event) {
                is TutorialEvent.CalibrationPoints -> moveTo(if (event.count >= 2) TutorialStep.TraceCalibValue else TutorialStep.TraceCalibB)
                TutorialEvent.Calibrated -> moveTo(TutorialStep.TraceFirstCorner)
                else -> false
            }
            TutorialStep.TraceFirstCorner -> event is TutorialEvent.CornersPlaced && event.count >= 1
            TutorialStep.TraceCorners -> event is TutorialEvent.CornersPlaced && event.count >= 3
            TutorialStep.TraceClose, TutorialStep.TraceNext -> event == TutorialEvent.RoomAdded
            TutorialStep.TraceCheck -> event == TutorialEvent.InfoOpened
            TutorialStep.TraceFade -> event == TutorialEvent.PlanViewChanged
            else -> false
        }
        if (done) next()
        return done
    }

    private fun moveTo(target: TutorialStep): Boolean {
        if (step == target) return false
        step = target
        return true
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
            val detail = when (s) {
                TutorialStep.WallLengths -> when (edited.maxOfOrNull { it.value.size } ?: 0) {
                    0 -> "Lati cambiati: 0 di 2"
                    else -> "Lati cambiati: 1 di 2 · ora un lato nell'altra direzione"
                }
                TutorialStep.TraceCorners -> "Angoli cliccati: $corners · ne servono almeno 3"
                TutorialStep.TraceClose -> "Angoli cliccati: $corners"
                else -> null
            }
            val steps = course.steps
            return TutorialUi(s, steps.take(steps.indexOf(s) + 1).count { it.action }, steps.count { it.action }, detail, course)
        }
}
