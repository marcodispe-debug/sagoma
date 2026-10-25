package com.sagoma.planimetria.editor

import com.sagoma.planimetria.testi.T

/**
 * Passi del tutorial guidato. Ogni passo con `action = true` chiede all'utente di fare qualcosa sulla
 * pianta e si completa da solo quando l'ha fatto; gli altri si superano con un pulsante.
 */
enum class TutorialStep(val action: Boolean = true) {
    Intro(action = false),
    OpenInfo,
    WallLengths,
    ChangeType,
    MoveWall,
    MoveCorner,
    AddDoor,
    MoveDoor,
    AddFixture,
    AddRoom,
    MoveRoom,
    Undo,
    Done(action = false),

    // ---------- Guida al ricalco di una piantina ----------

    TraceLoad,
    TraceCalibA,
    TraceCalibB,
    TraceCalibValue,
    TracePick,
    TraceFirstCorner,
    TraceCorners,
    TraceClose,
    TraceCheck,
    TraceFade,
    TraceNext,
    TraceDone(action = false),
    ;

    /** Titolo e testo, nella lingua dei testi ([com.sagoma.planimetria.testi.TestiTutorial]). */
    val title: String get() = com.sagoma.planimetria.testi.T.tutorial.titolo(this)
    val text: String get() = com.sagoma.planimetria.testi.T.tutorial.testo(this)
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
    data object UnderlayLoaded : TutorialEvent
    /** Taratura: punti già toccati (1 = solo A, 2 = A e B). */
    data class CalibrationPoints(val count: Int) : TutorialEvent
    data object Calibrated : TutorialEvent
    /** Muro per muro: angoli messi finora nella stanza che si sta disegnando. */
    data class CornersPlaced(val count: Int) : TutorialEvent
    /** Piantina o disegno mostrati, nascosti o resi più trasparenti. */
    data object PlanViewChanged : TutorialEvent

    /** Scelta nella finestra della forma della stanza (Quadrato, Rettangolo, A L, Da rilievo, Muro per muro). */
    data class ShapePicked(val choice: ShapeChoice) : TutorialEvent

    // Azioni che contano solo per la guida interattiva (Course).

    /** Scelto da che parte sporge la L. */
    data object LSidePicked : TutorialEvent
    /** «Ricalca una piantina». */
    data object TracingStarted : TutorialEvent
    data object StairAdded : TutorialEvent
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
            TutorialStep.TraceLoad -> event == TutorialEvent.UnderlayLoaded
            // Durante la taratura il passo segue i punti toccati: il terzo tocco ricomincia da A, e la guida torna indietro.
            TutorialStep.TraceCalibA, TutorialStep.TraceCalibB, TutorialStep.TraceCalibValue -> return when (event) {
                is TutorialEvent.CalibrationPoints -> moveTo(if (event.count >= 2) TutorialStep.TraceCalibValue else TutorialStep.TraceCalibB)
                TutorialEvent.Calibrated -> moveTo(TutorialStep.TracePick)
                else -> false
            }
            // Dopo la taratura si sceglie come disegnare la prima stanza: «Muro per muro» angolo per angolo, o una forma.
            TutorialStep.TracePick -> return when (event) {
                TutorialEvent.ShapePicked(ShapeChoice.Walls) -> moveTo(TutorialStep.TraceFirstCorner)
                TutorialEvent.RoomAdded -> moveTo(TutorialStep.TraceCheck)
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
                TutorialStep.WallLengths -> T.tutorial.latiCambiati(edited.maxOfOrNull { it.value.size } ?: 0)
                TutorialStep.TraceCorners -> T.tutorial.angoliServono(corners)
                TutorialStep.TraceClose -> T.tutorial.angoliCliccati(corners)
                else -> null
            }
            val steps = course.steps
            return TutorialUi(s, steps.take(steps.indexOf(s) + 1).count { it.action }, steps.count { it.action }, detail, course)
        }
}
