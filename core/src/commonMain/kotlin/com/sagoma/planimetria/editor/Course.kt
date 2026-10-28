package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Guida interattiva: lezioni brevi in cui l'utente fa davvero le azioni, su un progetto di prova. A ogni passo la guida
 * indica cosa cliccare ([GuideHint]) e va avanti da sola quando l'azione è fatta. Le lezioni prima dell'ultima costruiscono la
 * casa della piantina della guida ([GuidePlan]), ogni cosa al suo posto: lo studio, il soggiorno attaccato sopra, la camera a
 * destra, il bagno sotto la camera, l'ingresso nello spazio che resta, il tramezzo della cabina armadio nella camera, poi porte,
 * finestre e il resto. L'ultima («Sopra la tua planimetria») si fa in un secondo progetto, sopra l'immagine della stessa piantina.
 */
enum class Lesson {
    Square, LShape, Rectangle, Survey, Walls, FreeWall, Elements, OverPlan;

    /** La stanza della piantina che si disegna nella lezione (null: nessuna). */
    val planRoom: GuidePlan.Room?
        get() = when (this) {
            Square -> GuidePlan.studio
            LShape -> GuidePlan.soggiorno
            Rectangle -> GuidePlan.camera
            Survey -> GuidePlan.bagno
            Walls -> GuidePlan.ingresso
            else -> null
        }
}

/** Passi della guida interattiva, lezione per lezione. Quelli con `action = false` sono schede da leggere, con "Avanti". */
enum class CourseStep(val lesson: Lesson, val action: Boolean = true) {
    Welcome(Lesson.Square, action = false),
    SquarePick(Lesson.Square),
    SquareMeasures(Lesson.Square),
    SquareWall(Lesson.Square),
    SquareInfo(Lesson.Square),
    SquareCorner(Lesson.Square),
    SquareUndo(Lesson.Square),
    SquareDone(Lesson.Square, action = false),

    LPick(Lesson.LShape),
    LSide(Lesson.LShape),
    LMeasures(Lesson.LShape),
    Snap(Lesson.LShape, action = false),
    LMove(Lesson.LShape),
    LDone(Lesson.LShape, action = false),

    RectPick(Lesson.Rectangle),
    RectMeasures(Lesson.Rectangle),
    RectMove(Lesson.Rectangle),
    RectDone(Lesson.Rectangle, action = false),

    SurveyPick(Lesson.Survey),
    SurveyMeasures(Lesson.Survey),
    SurveyMove(Lesson.Survey),
    SurveyDone(Lesson.Survey, action = false),

    WallsPick(Lesson.Walls),
    WallsFirst(Lesson.Walls),
    WallsTyped(Lesson.Walls),
    WallsClose(Lesson.Walls),
    WallsDone(Lesson.Walls, action = false),

    FreeWallPick(Lesson.FreeWall),
    FreeWallMove(Lesson.FreeWall),
    FreeWallEnd(Lesson.FreeWall),
    FreeWallInfo(Lesson.FreeWall),
    FreeWallDone(Lesson.FreeWall, action = false),

    Door(Lesson.Elements),
    Window(Lesson.Elements),
    Balcony(Lesson.Elements),
    FrenchWindow(Lesson.Elements),
    Radiator(Lesson.Elements),
    Light(Lesson.Elements),
    Stair(Lesson.Elements),
    Info(Lesson.Elements),
    Check(Lesson.Elements),
    ElementsDone(Lesson.Elements, action = false),

    TraceStart(Lesson.OverPlan),
    TraceLoad(Lesson.OverPlan),
    CalibA(Lesson.OverPlan),
    CalibB(Lesson.OverPlan),
    CalibValue(Lesson.OverPlan),
    PlanShape(Lesson.OverPlan),
    PlanMeasures(Lesson.OverPlan),
    InnerFace(Lesson.OverPlan, action = false),
    MoveRoom(Lesson.OverPlan),
    Stretch(Lesson.OverPlan),
    Bath(Lesson.OverPlan),
    MoveBath(Lesson.OverPlan),
    CornerC(Lesson.OverPlan),
    CornerD(Lesson.OverPlan),
    LivingPick(Lesson.OverPlan),
    LivingSide(Lesson.OverPlan),
    LivingMeasures(Lesson.OverPlan),
    Attach(Lesson.OverPlan),
    LivingWalls(Lesson.OverPlan),
    LivingInfo(Lesson.OverPlan),
    PlanFade(Lesson.OverPlan),
    Finished(Lesson.OverPlan, action = false),
    ;

    companion object {
        fun first(l: Lesson): CourseStep = entries.first { it.lesson == l }
    }
}

/** Le scelte della finestra «Scegli la forma della stanza». */
enum class ShapeChoice { Square, Rectangle, L, Survey, Walls, Trace, FreeWall }

/** Campi in cui la guida fa scrivere una misura. `index` in [GuideTarget.Field] dice quale lato o diagonale. */
enum class GuideField { Side, Width, Depth, MainWidth, MainDepth, ProtrusionWidth, ProtrusionDepth, SurveySide, Diagonal, WallLength, Calibration, WallInfo, FreeWallLength, FreeWallThickness }

/** Voci del menu «Elementi architettonici» che la guida indica. */
enum class GuideMenu { Room, Doors, Door, Windows, Window, FrenchWindows, FrenchWindow, Systems, Radiator, Lights, Chandelier, Stairs, Spiral, Outdoor, Balcony }

/** Comandi dell'interfaccia che la guida interattiva sa indicare (l'interfaccia li segna con `Modifier.guide`). */
sealed interface GuideTarget {
    data class Shape(val choice: ShapeChoice) : GuideTarget
    data class LSide(val orientation: LOrientation) : GuideTarget
    data class Type(val type: RoomType) : GuideTarget
    data class Field(val field: GuideField, val index: Int = 0) : GuideTarget
    /** «Crea stanza» (o «Crea» per balconi e terrazze). */
    data object Create : GuideTarget
    /** «Indietro» nelle finestre della creazione: torna alla scelta delle forme. */
    data object Back : GuideTarget
    /** Il pulsante «Elementi architettonici»: fatto quando il suo menu è aperto. */
    data object Elements : GuideTarget
    data class Menu(val item: GuideMenu) : GuideTarget
    data class Arrow(val arrow: WallArrow) : GuideTarget
    data object AddWall : GuideTarget
    data object CloseRoom : GuideTarget
    /** «Usa la piantina della guida», nella finestra della piantina da ricalcare. */
    data object GuidePlanImage : GuideTarget
    /** «Tara», nella finestra della misura reale. */
    data object Calibrate : GuideTarget
    /** «◐ Piantina e disegno»: fatto quando il pannello è aperto. */
    data object PlanPanel : GuideTarget
    /** Il cursore della trasparenza della piantina, nel pannello «◐ Piantina e disegno». */
    data object PlanOpacity : GuideTarget
    /** «Info» sulla striscia in basso: fatto quando la tendina è aperta. */
    data object Info : GuideTarget
    /** La scheda «Misure» nella tendina Info di una stanza: fatto quando è aperta. */
    data object MeasuresTab : GuideTarget
    /** «🧲 Sì / No» sulla striscia in basso: agganci accesi o spenti. */
    data object Snap : GuideTarget
    /** Tutta la tendina Info: mai da cliccare, serve solo a tenere il fumetto fuori dalla tendina aperta. */
    data object InfoPanel : GuideTarget
    /** «↶ Annulla» in alto. */
    data object Undo : GuideTarget
    /** Il menu «⋮» in alto: fatto quando è aperto. */
    data object More : GuideTarget
    /** La voce «Controlla la pianta» del menu «⋮». */
    data object CheckItem : GuideTarget
    /** «Chiudi» nella finestra «Controlla la pianta». */
    data object CheckClose : GuideTarget
}

/**
 * Segno sulla pianta, in cm: con un punto solo è dove toccare o da dove trascinare; con `goal` è dove arrivare (un punto, una
 * linea o un contorno, tratteggiati).
 */
data class PlanMark(val points: List<Vec2>, val goal: Boolean = false)

/**
 * Un'indicazione della guida: cosa dire e cosa evidenziare, un comando ([target]) o dei segni sulla pianta ([marks]). Per un
 * campo, `expect` è la misura da scrivere: finché il campo non la contiene l'indicazione resta sul campo. [keepVisible]: punti
 * della pianta (in cm) che il fumetto non deve coprire, oltre ai segni.
 */
data class GuideHint(
    val text: String,
    val target: GuideTarget? = null,
    val expect: String? = null,
    val marks: List<PlanMark> = emptyList(),
    val keepVisible: List<Vec2> = emptyList(),
)

/** Cosa mostra il fumetto della guida: lezione, passo dentro la lezione e avanzamento dentro il passo. */
data class CourseUi(
    val step: CourseStep,
    val lesson: Int,
    val lessons: Int,
    val number: Int,
    val total: Int,
    val detail: String?,
    /** «Indietro»: c'è un passo prima a cui tornare. */
    val canGoBack: Boolean = false,
)

/** Avanzamento della guida interattiva. Le azioni arrivano come [TutorialEvent], come per il tutorial. */
class Course {
    var step: CourseStep? = null
        private set

    val active: Boolean get() = step != null

    fun start(lesson: Lesson) {
        step = CourseStep.first(lesson)
    }

    fun stop() {
        step = null
    }

    /** Passo successivo ("Avanti", "Salta passo" o passo completato); null dopo l'ultimo. */
    fun next() {
        val s = step ?: return
        step = CourseStep.entries.getOrNull(s.ordinal + 1)
    }

    /** Registra un'azione dell'utente sulla casa `s` (già aggiornata); true se il passo è cambiato. */
    fun on(e: TutorialEvent, s: EditorUiState): Boolean {
        val done = when (step) {
            CourseStep.SquarePick, CourseStep.PlanShape -> e == TutorialEvent.ShapePicked(ShapeChoice.Square)
            CourseStep.LivingPick -> e == TutorialEvent.ShapePicked(ShapeChoice.L)
            CourseStep.LivingSide -> e == TutorialEvent.LSidePicked
            CourseStep.RectPick -> e == TutorialEvent.ShapePicked(ShapeChoice.Rectangle)
            CourseStep.LPick -> e == TutorialEvent.ShapePicked(ShapeChoice.L)
            CourseStep.LSide -> e == TutorialEvent.LSidePicked
            CourseStep.SurveyPick -> e == TutorialEvent.ShapePicked(ShapeChoice.Survey)
            CourseStep.WallsPick -> e == TutorialEvent.ShapePicked(ShapeChoice.Walls)
            // Lo studio: muro trascinato, misure esatte in Info, angolo trascinato e poi Annulla, che lo rimette dritto.
            CourseStep.SquareWall -> e == TutorialEvent.WallDragged
            CourseStep.SquareInfo -> CourseHints(s).squareExact()
            CourseStep.SquareCorner -> e == TutorialEvent.CornerDragged
            CourseStep.SquareUndo -> e == TutorialEvent.Undo && CourseHints(s).squareExact()
            // Le stanze nascono staccate: si portano al loro posto, dove gli agganci le attaccano alle altre.
            CourseStep.LMove, CourseStep.RectMove, CourseStep.SurveyMove ->
                e == TutorialEvent.RoomMoved && CourseHints(s).inPlace(this.step?.lesson?.planRoom)
            CourseStep.SquareMeasures, CourseStep.RectMeasures, CourseStep.LMeasures, CourseStep.SurveyMeasures,
            CourseStep.WallsClose, CourseStep.PlanMeasures, CourseStep.Bath, CourseStep.LivingMeasures -> e == TutorialEvent.RoomAdded
            CourseStep.Balcony -> e == TutorialEvent.RoomAdded && s.plan.rooms.lastOrNull()?.type?.outdoor == true
            CourseStep.WallsFirst -> e is TutorialEvent.CornersPlaced && e.count >= 1
            CourseStep.WallsTyped -> e is TutorialEvent.CornersPlaced && e.count >= GuidePlan.entranceWalls.size + 1
            CourseStep.Door, CourseStep.Window, CourseStep.FrenchWindow -> e == TutorialEvent.OpeningAdded
            CourseStep.Radiator, CourseStep.Light -> e == TutorialEvent.FixtureAdded
            CourseStep.Stair -> e == TutorialEvent.StairAdded
            CourseStep.Info -> e == TutorialEvent.InfoOpened
            CourseStep.Check -> e == TutorialEvent.PlanChecked
            CourseStep.TraceStart -> e == TutorialEvent.TracingStarted
            CourseStep.TraceLoad -> e == TutorialEvent.UnderlayLoaded
            // Durante la taratura il passo segue i punti toccati: il terzo tocco ricomincia da A, e la guida torna indietro.
            CourseStep.CalibA, CourseStep.CalibB, CourseStep.CalibValue -> return when (e) {
                is TutorialEvent.CalibrationPoints -> moveTo(if (e.count >= 2) CourseStep.CalibValue else CourseStep.CalibB)
                TutorialEvent.Calibrated -> moveTo(CourseStep.PlanShape)
                else -> false
            }
            CourseStep.MoveRoom, CourseStep.MoveBath -> e == TutorialEvent.RoomMoved
            CourseStep.Stretch -> e == TutorialEvent.WallDragged
            CourseStep.CornerC, CourseStep.CornerD -> e == TutorialEvent.CornerDragged
            // Soggiorno: attaccato alla camera, muri più o meno sulla piantina, poi le misure esatte scritte in Info.
            CourseStep.Attach -> e == TutorialEvent.RoomMoved && CourseHints(s).attached()
            CourseStep.LivingWalls -> (e == TutorialEvent.WallDragged || e == TutorialEvent.CornerDragged) && CourseHints(s).livingWalls()?.isEmpty() == true
            CourseStep.LivingInfo -> CourseHints(s).livingExact()
            CourseStep.PlanFade -> e == TutorialEvent.PlanViewChanged
            // Il tramezzo: nato nella camera, portato al suo posto, accorciato, poi lunghezza e spessore esatti in Info.
            CourseStep.FreeWallPick -> e == TutorialEvent.FreeWallAdded
            CourseStep.FreeWallMove -> e == TutorialEvent.FreeWallMoved && CourseHints(s).freeWallInPlace()
            CourseStep.FreeWallEnd -> e == TutorialEvent.FreeWallEndDragged && CourseHints(s).freeWallShortened()
            CourseStep.FreeWallInfo -> CourseHints(s).freeWallExact()
            else -> false
        }
        if (done) next()
        return done
    }

    /**
     * «Salta passo»: il passo dopo; dentro «Muro per muro» si salta tutto il disegno dell'ingresso, che la guida mette da sola
     * (vedi [skipped]).
     */
    fun skip() {
        step = when (step) {
            CourseStep.WallsFirst, CourseStep.WallsTyped, CourseStep.WallsClose -> CourseStep.WallsDone
            else -> return next()
        }
    }

    /** «Indietro»: di nuovo al passo `target` (uno già fatto). */
    fun goTo(target: CourseStep) {
        step = target
    }

    private fun moveTo(target: CourseStep): Boolean {
        if (step == target) return false
        step = target
        return true
    }

    /** Scheda da mostrare per la casa `s`. */
    fun ui(s: EditorUiState): CourseUi? {
        val st = step ?: return null
        val steps = CourseStep.entries.filter { it.lesson == st.lesson && it.action }
        val detail = when (st) {
            CourseStep.WallsTyped -> T.corso.muriFatti(((s.wallDraw?.points?.size ?: 1) - 1).coerceAtLeast(0), GuidePlan.entranceWalls.size)
            else -> null
        }
        return CourseUi(st, st.lesson.ordinal + 1, Lesson.entries.size, steps.indexOf(st) + 1, steps.size, detail)
    }

    companion object {
        /**
         * Le indicazioni del passo, nell'ordine in cui servono: l'interfaccia evidenzia la prima che è sullo schermo e non è
         * ancora fatta. Dipendono dalla casa (dove sono le stanze, cosa è aperto), così chi esce dal percorso viene riportato.
         */
        fun hints(step: CourseStep, s: EditorUiState): List<GuideHint> = CourseHints(s).of(step)

        /** Cosa inquadrare all'inizio del passo (null: la vista resta com'è). */
        fun focus(step: CourseStep, s: EditorUiState): Bounds? = CourseHints(s).focus(step)

        /**
         * Dove nasce una stanza creata durante la guida (in mezzeria; `interior` è il suo filo interno): staccata dalle altre, così
         * si impara a spostarla; il balcone invece già al suo posto. Null: dove la mette Sagoma di solito (la prima stanza).
         */
        fun place(step: CourseStep?, s: EditorUiState, interior: List<Vec2>, shape: List<Vec2>, outdoor: Boolean): List<Vec2>? =
            CourseHints(s).place(step, interior, shape, outdoor)

        /** «Salta passo» c'è: non per caricare la piantina della guida, senza la quale il resto della lezione non si può fare. */
        fun canSkip(step: CourseStep): Boolean = step.action && step != CourseStep.TraceLoad

        /**
         * La stanza che nasce quando si salta un passo «Crea stanza»: tipo e filo interno (dove nasce lo decide [place], come
         * per quelle create a mano). Null: il passo non crea una stanza.
         */
        fun skippedRoom(step: CourseStep): Pair<RoomType, List<Vec2>>? = when (step) {
            CourseStep.SquareMeasures -> GuidePlan.studio.let { it.type to it.points }
            CourseStep.LMeasures -> GuidePlan.soggiorno.let { it.type to it.points }
            CourseStep.RectMeasures -> GuidePlan.camera.let { it.type to it.points }
            CourseStep.SurveyMeasures -> GuidePlan.bagno.let { it.type to it.points }
            CourseStep.Balcony -> GuidePlan.balcone.let { it.type to it.points }
            CourseStep.PlanMeasures -> GuidePlan.side(GuidePlan.camera, 1).toDouble().let { RoomType.Camera to RoomFactory.rectangle(it, it) }
            CourseStep.Bath -> GuidePlan.side(GuidePlan.bagno, 0).toDouble().let { RoomType.Bagno to RoomFactory.rectangle(it, it) }
            // Il soggiorno a L con misure di prova, un po' più piccolo di quello disegnato: i muri si portano poi sulla piantina.
            CourseStep.LivingMeasures -> RoomType.Soggiorno to GuidePlan.soggiorno.points.map { it * SKIPPED_LIVING_SCALE }
            else -> null
        }

        /**
         * La casa come l'avrebbe lasciata il passo `step` fatto a mano (muri trascinati, stanze al loro posto, misure esatte,
         * taratura), così dopo «Salta passo» i passi seguenti si possono fare. Null: il passo non cambia la casa.
         */
        fun skipped(step: CourseStep, s: EditorUiState): FloorPlan? = CourseHints(s).skipped(step)

        /** Il passo `step` è già fatto sulla casa `s`: dopo un passo saltato la guida va avanti da sola. */
        fun ready(step: CourseStep, s: EditorUiState): Boolean = CourseHints(s).ready(step)

        /** La casa di prova com'è all'inizio della lezione `lesson`: le stanze (e il tramezzo) delle lezioni prima, al loro posto. */
        fun house(lesson: Lesson): FloorPlan {
            val before = Lesson.entries.takeWhile { it != lesson }
            val rooms = before.mapNotNull { it.planRoom }.fold(FloorPlan()) { plan, r ->
                val room = Room(
                    id = plan.nextId,
                    name = RoomFactory.autoName(plan, r.type),
                    type = r.type,
                    points = Polygon.fromInterior(r.points, Room.WALL_THICKNESS),
                    floorFinish = r.type.defaultFloor,
                )
                plan.copy(rooms = plan.rooms + room)
            }
            return if (Lesson.FreeWall !in before) rooms
            else rooms.copy(freeWalls = rooms.freeWalls + GuidePlan.tramezzo.copy(id = rooms.nextFreeWallId))
        }
    }
}

/** Le indicazioni dei passi, calcolate sulla casa `s`. */
private class CourseHints(private val s: EditorUiState) {
    private val c = T.corso
    private val plan: FloorPlan get() = s.plan

    private fun hint(target: GuideTarget, text: String, expect: Int? = null) = GuideHint(text, target, expect?.toString())
    private fun field(f: GuideField, value: Int, text: String, index: Int = 0) = hint(GuideTarget.Field(f, index), text, value)
    private fun mark(text: String, vararg marks: PlanMark) = GuideHint(text, marks = marks.toList())

    /** «Elementi architettonici» e poi le voci del menu, una dopo l'altra. */
    private fun menu(first: String, vararg items: Pair<GuideMenu, String>): List<GuideHint> =
        listOf(hint(GuideTarget.Elements, first)) + items.map { (m, t) -> hint(GuideTarget.Menu(m), t) }

    /** Scelta della forma: nella finestra se è aperta, altrimenti prima la si apre da «Elementi architettonici» → «Stanza». */
    private fun pick(choice: ShapeChoice, text: String, open: String = c.apriElementi): List<GuideHint> =
        if (s.creation is CreationStep.PickShape) listOf(hint(GuideTarget.Shape(choice), text))
        else menu(open, GuideMenu.Room to c.poiStanza) + hint(GuideTarget.Shape(choice), text)

    private fun room(type: RoomType): Room? = plan.rooms.lastOrNull { it.type == type }
    private fun indoor(type: RoomType, fallback: Int): Room? =
        room(type) ?: plan.rooms.filter { !it.type.outdoor }.let { it.getOrNull(fallback) ?: it.lastOrNull() }

    fun of(step: CourseStep): List<GuideHint> = when (step) {
        CourseStep.Welcome, CourseStep.SquareDone, CourseStep.RectDone, CourseStep.LDone, CourseStep.SurveyDone,
        CourseStep.WallsDone, CourseStep.ElementsDone, CourseStep.InnerFace, CourseStep.Finished -> emptyList()

        CourseStep.SquarePick -> pick(ShapeChoice.Square, c.scegliQuadrato)
        CourseStep.SquareMeasures -> measures(ShapeChoice.Square, c.scegliQuadrato) {
            listOf(
                hint(GuideTarget.Type(RoomType.Studio), c.tipoStudio),
                field(GuideField.Side, GuidePlan.side(GuidePlan.studio, 0), c.scriviLato(GuidePlan.side(GuidePlan.studio, 0))),
                hint(GuideTarget.Create, c.soffittoECrea),
            )
        }
        CourseStep.SquareWall -> squareWallHints()
        CourseStep.SquareInfo -> squareInfoHints()
        CourseStep.SquareCorner -> squareCornerHints()
        CourseStep.SquareUndo -> listOf(hint(GuideTarget.Undo, c.annullaAngolo))

        CourseStep.LPick -> pick(ShapeChoice.L, c.scegliL, c.apriElementiAltraStanza)
        CourseStep.LSide -> listOf(hint(GuideTarget.LSide(LOrientation.BottomRight), c.sporgeInBassoADestra(LOrientation.BottomRight.label)))
        CourseStep.LMeasures -> measures(ShapeChoice.L, c.scegliL) {
            val sg = GuidePlan.soggiorno
            listOf(
                hint(GuideTarget.Type(RoomType.Soggiorno), c.tipo(RoomType.Soggiorno.label)),
                field(GuideField.MainWidth, GuidePlan.side(sg, 0), c.larghezzaPrincipale(GuidePlan.side(sg, 0))),
                field(GuideField.MainDepth, GuidePlan.side(sg, 5), c.profonditaPrincipale(GuidePlan.side(sg, 5))),
                field(GuideField.ProtrusionWidth, GuidePlan.side(sg, 2), c.larghezzaProtuberanza(GuidePlan.side(sg, 2))),
                field(GuideField.ProtrusionDepth, GuidePlan.side(sg, 3), c.profonditaProtuberanza(GuidePlan.side(sg, 3))),
                hint(GuideTarget.Create, c.creaStanza),
            )
        }
        CourseStep.Snap -> listOf(hint(GuideTarget.Snap, c.testoScheda(CourseStep.Snap)))
        CourseStep.LMove -> moveHints(GuidePlan.soggiorno, c.spostaSoggiorno)

        CourseStep.RectPick -> pick(ShapeChoice.Rectangle, c.scegliRettangolo, c.apriElementiAltraStanza)
        CourseStep.RectMeasures -> measures(ShapeChoice.Rectangle, c.scegliRettangolo) {
            listOf(
                hint(GuideTarget.Type(RoomType.Camera), c.tipo(RoomType.Camera.label)),
                field(GuideField.Width, GuidePlan.side(GuidePlan.camera, 0), c.scriviLarghezza(GuidePlan.side(GuidePlan.camera, 0))),
                field(GuideField.Depth, GuidePlan.side(GuidePlan.camera, 1), c.scriviProfondita(GuidePlan.side(GuidePlan.camera, 1))),
                hint(GuideTarget.Create, c.creaStanza),
            )
        }
        CourseStep.RectMove -> moveHints(GuidePlan.camera, c.spostaCameraAccanto)

        CourseStep.SurveyPick -> pick(ShapeChoice.Survey, c.scegliRilievo, c.apriElementiAltraStanza)
        CourseStep.SurveyMeasures -> measures(ShapeChoice.Survey, c.scegliRilievo) {
            val letters = "ABCD"
            listOf(hint(GuideTarget.Type(RoomType.Bagno), c.tipoBagnoQuattroLati)) +
                GuidePlan.bathSides.mapIndexed { i, v ->
                    field(GuideField.SurveySide, v, if (i == 0) c.primoLatoRilievo(v) else c.lato("${letters[i]}${letters[(i + 1) % 4]}", v), index = i)
                } +
                field(GuideField.Diagonal, GuidePlan.bathDiagonal, c.diagonaleAC(GuidePlan.bathDiagonal)) +
                hint(GuideTarget.Create, c.anteprimaECrea)
        }
        CourseStep.SurveyMove -> moveHints(GuidePlan.bagno, c.spostaBagnoSotto)

        CourseStep.WallsPick -> pick(ShapeChoice.Walls, c.scegliMuroPerMuro, c.apriElementiAltraStanza)
        CourseStep.WallsFirst -> if (s.wallDraw == null) pick(ShapeChoice.Walls, c.scegliMuroPerMuro, c.apriElementiAltraStanza)
        else listOf(mark(c.primoAngolo, PlanMark(listOf(entranceStart()))))
        CourseStep.WallsTyped -> {
            val walls = GuidePlan.entranceWalls
            val k = ((s.wallDraw?.points?.size ?: 1) - 1).coerceIn(0, walls.size - 1)
            val (arrow, length) = walls[k]
            listOf(
                hint(GuideTarget.Arrow(arrow), c.verso(k + 1, arrow)),
                field(GuideField.WallLength, length, c.lunghezzaMuro(length)),
                hint(GuideTarget.AddWall, c.aggiungi),
            )
        }
        CourseStep.WallsClose -> if (s.wallDraw?.closing == true) listOf(
            hint(GuideTarget.Type(RoomType.Corridoio), c.tipoIngresso(RoomType.Corridoio.label)),
            hint(GuideTarget.Create, c.creaStanza),
        ) else listOf(hint(GuideTarget.CloseRoom, c.chiudiStanza))

        CourseStep.FreeWallPick -> freeWallPickHints()
        CourseStep.FreeWallMove -> freeWallMoveHints()
        CourseStep.FreeWallEnd -> freeWallEndHints()
        CourseStep.FreeWallInfo -> freeWallInfoHints()
        // La scheda finale: il tramezzo tratteggiato, e il fumetto che non lo copre.
        CourseStep.FreeWallDone -> listOfNotNull(freeWall()?.let { w -> mark("", PlanMark(listOf(w.start, w.end), goal = true)) })

        CourseStep.Door -> if (s.pendingOpening == null) menu(c.portaElementi, GuideMenu.Doors to c.porte, GuideMenu.Door to c.portaUnaAnta)
        else listOf(mark(c.cliccaMuroPorta, PlanMark(listOf(doorSpot()))))
        CourseStep.Window -> if (s.pendingOpening == null) menu(c.finestraElementi, GuideMenu.Windows to c.finestre, GuideMenu.Window to c.finestraDueAnte)
        else listOf(mark(c.cliccaMuroFinestra, PlanMark(listOf(windowSpot()))))
        CourseStep.Balcony -> when (s.creation) {
            is CreationStep.PickShape -> listOf(hint(GuideTarget.Shape(ShapeChoice.Rectangle), c.balconeRettangolo))
            null -> menu(c.balconeElementi, GuideMenu.Outdoor to c.balconiTerrazze, GuideMenu.Balcony to c.balcone)
            else -> measures(ShapeChoice.Rectangle, c.balconeRettangolo) {
                listOf(
                    field(GuideField.Width, GuidePlan.side(GuidePlan.balcone, 0), c.balconeLarghezza(GuidePlan.side(GuidePlan.balcone, 0))),
                    field(GuideField.Depth, GuidePlan.side(GuidePlan.balcone, 1), c.balconeProfondita(GuidePlan.side(GuidePlan.balcone, 1))),
                    hint(GuideTarget.Create, c.balconeCrea),
                )
            }
        }
        CourseStep.FrenchWindow -> if (s.pendingOpening == null) menu(c.portaFinestraElementi, GuideMenu.FrenchWindows to c.porteFinestre, GuideMenu.FrenchWindow to c.balconeDueAnte)
        else listOfNotNull(frenchSpot()?.let { mark(c.cliccaMuroBalcone, PlanMark(listOf(it))) })
        CourseStep.Radiator -> if (s.pendingFixture == null) menu(c.impiantiElementi, GuideMenu.Systems to c.impianti, GuideMenu.Radiator to c.calorifero)
        else listOf(mark(c.cliccaMuroCalorifero, PlanMark(listOf(radiatorSpot()))))
        CourseStep.Light -> if (s.pendingFixture == null) menu(c.luciElementi, GuideMenu.Systems to c.impianti, GuideMenu.Lights to c.luci, GuideMenu.Chandelier to c.lampadario)
        else listOf(mark(c.cliccaStanzaLuce, PlanMark(listOf(lightSpot()))))
        CourseStep.Stair -> stairHints()
        CourseStep.Info -> listOf(hint(GuideTarget.Info, c.info))
        CourseStep.Check -> listOf(hint(GuideTarget.More, c.controlloMenu), hint(GuideTarget.CheckItem, c.controlloVoce), hint(GuideTarget.CheckClose, c.controlloChiudi))

        CourseStep.TraceStart -> listOf(hint(GuideTarget.Shape(ShapeChoice.Trace), c.ricalca))
        CourseStep.TraceLoad -> listOf(hint(GuideTarget.GuidePlanImage, c.usaPiantinaGuida))
        CourseStep.CalibA -> underlayMark(GuidePlan.calibrationA, c.taraturaA(GuidePlan.calibrationCm))
        CourseStep.CalibB -> underlayMark(GuidePlan.calibrationB, c.taraturaB)
        CourseStep.CalibValue -> listOf(
            field(GuideField.Calibration, GuidePlan.calibrationCm, c.misuraReale(GuidePlan.calibrationCm)),
            hint(GuideTarget.Calibrate, c.tara),
        )
        CourseStep.PlanShape -> listOf(hint(GuideTarget.Shape(ShapeChoice.Square), c.sceltaDopoTaratura))
        CourseStep.PlanMeasures -> measures(ShapeChoice.Square, c.cameraDaQuadrato) {
            val side = GuidePlan.side(GuidePlan.camera, 1)
            listOf(
                hint(GuideTarget.Type(RoomType.Camera), c.tipo(RoomType.Camera.label)),
                field(GuideField.Side, side, c.latoCortoCamera(side)),
                hint(GuideTarget.Create, c.creaStanza),
            )
        }
        CourseStep.MoveRoom -> moveOnto(indoor(RoomType.Camera, 0), GuidePlan.camera, c.spostaCamera)
        CourseStep.Stretch -> stretchHints()
        CourseStep.Bath -> when (s.creation) {
            is CreationStep.PickShape -> listOf(hint(GuideTarget.Shape(ShapeChoice.Square), c.bagnoQuadrato))
            null -> menu(c.bagnoElementi, GuideMenu.Room to c.poiStanza)
            else -> measures(ShapeChoice.Square, c.bagnoQuadrato) {
                listOf(
                    hint(GuideTarget.Type(RoomType.Bagno), c.tipo(RoomType.Bagno.label)),
                    field(GuideField.Side, GuidePlan.side(GuidePlan.bagno, 0), c.latoBagno(GuidePlan.side(GuidePlan.bagno, 0))),
                    hint(GuideTarget.Create, c.creaStanza),
                )
            }
        }
        CourseStep.MoveBath -> moveOnto(room(RoomType.Bagno) ?: plan.rooms.lastOrNull(), GuidePlan.bagno, c.spostaBagno)
        CourseStep.CornerC -> cornerHints(2, c.angoloC)
        CourseStep.CornerD -> cornerHints(3, c.angoloD)

        CourseStep.LivingPick -> pick(ShapeChoice.L, c.soggiornoL, c.soggiornoElementi)
        CourseStep.LivingSide -> listOf(hint(GuideTarget.LSide(LOrientation.BottomRight), c.sporgeInBassoADestra(LOrientation.BottomRight.label)))
        CourseStep.LivingMeasures -> measures(ShapeChoice.L, c.soggiornoL) {
            listOf(hint(GuideTarget.Type(RoomType.Soggiorno), c.soggiornoMisureDopo), hint(GuideTarget.Create, c.creaStanza))
        }
        CourseStep.Attach -> attachHints()
        // Il fumetto non copre il soggiorno né quello disegnato: si vede dove arrivano i muri.
        CourseStep.LivingWalls -> livingWalls().orEmpty().take(1).map { (j, from, to) ->
            mark(c.muroSullaPiantina(j), PlanMark(listOf(from)), PlanMark(listOf(to), goal = true))
                .copy(keepVisible = living()?.points.orEmpty() + GuidePlan.soggiorno.points.mapNotNull(::onPlan))
        }
        CourseStep.LivingInfo -> livingInfoHints()
        CourseStep.PlanFade -> listOf(hint(GuideTarget.PlanPanel, c.apriPannello), hint(GuideTarget.PlanOpacity, c.trasparenzaPiantina))
    }

    /**
     * Misure nella finestra della forma `choice`. Con la finestra di un'altra forma si torna indietro alle forme; senza finestra
     * (chiusa con Annulla) si riapre.
     */
    private fun measures(choice: ShapeChoice, pickText: String, fields: () -> List<GuideHint>): List<GuideHint> {
        val open = s.creation
        val right = when (choice) {
            ShapeChoice.Survey -> open is CreationStep.Survey
            else -> open is CreationStep.Measures && open.shape == shapeOf(choice)
        }
        return when {
            right -> fields()
            choice == ShapeChoice.L && open is CreationStep.PickLOrientation ->
                listOf(hint(GuideTarget.LSide(LOrientation.BottomRight), c.sporgeInBassoADestra(LOrientation.BottomRight.label)))
            open is CreationStep.Measures || open is CreationStep.Survey || open is CreationStep.PickLOrientation -> listOf(hint(GuideTarget.Back, c.tornaIndietro))
            else -> pick(choice, pickText)
        }
    }

    private fun shapeOf(choice: ShapeChoice): RoomShape? = when (choice) {
        ShapeChoice.Square -> RoomShape.Square
        ShapeChoice.Rectangle -> RoomShape.Rectangle
        ShapeChoice.L -> RoomShape.L
        else -> null
    }

    // ---------- La casa della piantina, una stanza dopo l'altra ----------

    /** Quanto è spostata la casa di prova rispetto alla piantina: fa da riferimento lo studio, la prima stanza. */
    private fun offset(): Vec2? {
        val studio = room(RoomType.Studio) ?: return null
        return studio.interior().minBy { it.x + it.y } - GuidePlan.studio.points[0]
    }

    /** Dove va la stanza `r` della piantina nella casa di prova (filo interno). Null senza lo studio. */
    private fun goal(r: GuidePlan.Room): List<Vec2>? = offset()?.let { o -> r.points.map { it + o } }

    /**
     * La stanza della piantina `target` è al suo posto: attaccata alle altre come nel disegno (filo interno, a meno di qualche
     * centimetro: il bagno viene dalle misure arrotondate al centimetro).
     */
    fun inPlace(target: GuidePlan.Room?): Boolean {
        target ?: return false
        val r = room(target.type) ?: return false
        val g = goal(target) ?: return false
        val inner = r.interior()
        return inner.size == g.size && g.all { p -> inner.any { it.distanceTo(p) < PLACE_CM } }
    }

    /** La stanza da portare al suo posto: dal nome al contorno tratteggiato. Con gli agganci spenti, prima si accendono. */
    private fun moveHints(target: GuidePlan.Room, text: String): List<GuideHint> {
        val r = room(target.type) ?: return emptyList()
        val g = goal(target) ?: return emptyList()
        val move = mark(text, PlanMark(listOf(Polygon.labelPoint(r.points))), PlanMark(g, goal = true))
        return if (s.snapOn) listOf(move) else listOf(hint(GuideTarget.Snap, c.accendiAgganci), move)
    }

    fun place(step: CourseStep?, interior: List<Vec2>, shape: List<Vec2>, outdoor: Boolean): List<Vec2>? {
        if (step == null || plan.rooms.isEmpty()) return null
        val house = Polygon.bounds(plan.rooms.flatMap { it.points })
        val ib = Polygon.bounds(interior)
        // Il balcone nasce dove è sulla piantina, sopra la camera, con il parapetto sul muro della camera (come lo appoggia Sagoma).
        if (outdoor) {
            val g = goal(GuidePlan.balcone) ?: return null
            val camera = room(RoomType.Camera) ?: return null
            return shape.map { it + Vec2(Polygon.bounds(g).minX - ib.minX, camera.points.minOf { p -> p.y } - Polygon.bounds(shape).maxY) }
        }
        val g = step.lesson.planRoom?.let(::goal)
        // Senza un posto sulla piantina (per esempio sopra la planimetria): a destra della casa, staccata.
        if (g == null) {
            val sb = Polygon.bounds(shape)
            return shape.map { it + Vec2(house.maxX + GAP_CM - sb.minX, house.minY - sb.minY) }
        }
        // Al suo posto, poi allontanata dalla casa dalla parte dove andrà: si vede dove portarla, e non è ancora attaccata.
        val gb = Polygon.bounds(g)
        val at = shape.map { it + Vec2(gb.minX - ib.minX, gb.minY - ib.minY) }
        val ab = Polygon.bounds(at)
        val d = ab.center - house.center
        val away = if (abs(d.x) >= abs(d.y)) {
            if (d.x >= 0) Vec2(house.maxX + GAP_CM - ab.minX, 0.0) else Vec2(house.minX - GAP_CM - ab.maxX, 0.0)
        } else {
            if (d.y >= 0) Vec2(0.0, house.maxY + GAP_CM - ab.minY) else Vec2(0.0, house.minY - GAP_CM - ab.maxY)
        }
        return at.map { it + away }
    }

    // ---------- «Salta passo»: la casa come l'avrebbe lasciata il passo fatto ----------

    fun ready(step: CourseStep): Boolean = when (step) {
        CourseStep.SquareInfo -> squareExact()
        CourseStep.LMove, CourseStep.RectMove, CourseStep.SurveyMove -> inPlace(step.lesson.planRoom)
        CourseStep.FreeWallMove -> freeWallInPlace()
        CourseStep.FreeWallEnd -> freeWallShortened()
        CourseStep.FreeWallInfo -> freeWallExact()
        CourseStep.LivingWalls -> livingWalls()?.isEmpty() == true
        CourseStep.LivingInfo -> livingExact()
        else -> false
    }

    fun skipped(step: CourseStep): FloorPlan? = when (step) {
        // Lo studio: muro di destra più in là, misure esatte, angolo in fuori, di nuovo dritto (Annulla).
        CourseStep.SquareWall -> studio()?.let { r ->
            val right = r.points.sortedByDescending { it.x }.take(2).toSet()
            plan.replace(r.copy(points = r.points.map { if (it in right) it + Vec2(STRETCH_CM, 0.0) else it }))
        }
        CourseStep.SquareInfo, CourseStep.SquareUndo -> withRoom(GuidePlan.studio)
        CourseStep.SquareCorner -> studio()?.let { r ->
            val corner = r.points.maxBy { it.x + it.y }
            plan.replace(r.copy(points = r.points.map { if (it == corner) it + Vec2(CORNER_CM, CORNER_CM * 0.6) else it }))
        }
        CourseStep.LMove, CourseStep.RectMove, CourseStep.SurveyMove -> step.lesson.planRoom?.let(::withRoom)
        // L'ingresso disegnato muro per muro: già chiuso, al suo posto.
        CourseStep.WallsFirst, CourseStep.WallsTyped, CourseStep.WallsClose -> withRoom(GuidePlan.ingresso)
        CourseStep.FreeWallMove -> partition()?.let { (top, _) ->
            val w = freeWall()
            val camera = goal(GuidePlan.camera)
            val bottom = camera?.maxOf { it.y } ?: (top.y + GuidePlan.tramezzo.length)
            withFreeWall(if (w != null && upright(w)) w.copy(start = Vec2(top.x, w.start.y), end = Vec2(top.x, w.end.y)) else (w ?: newFreeWall()).copy(start = top, end = Vec2(top.x, bottom)))
        }
        CourseStep.FreeWallEnd -> partition()?.let { (top, end) -> withFreeWall((freeWall() ?: newFreeWall()).copy(start = top, end = end)) }
        CourseStep.FreeWallInfo -> partition()?.let { (top, end) ->
            withFreeWall((freeWall() ?: newFreeWall()).copy(start = top, end = end, thickness = GuidePlan.tramezzo.thickness))
        }
        // Sopra la piantina: taratura fatta, poi le stanze portate e allungate sopra quelle disegnate.
        CourseStep.CalibA, CourseStep.CalibB, CourseStep.CalibValue -> plan.underlay?.let { u ->
            val f = GuidePlan.width / (u.cmPerPx * u.widthPx)
            val a = GuidePlan.onUnderlay(u, GuidePlan.calibrationA)
            plan.copy(underlay = u.copy(cmPerPx = u.cmPerPx * f, origin = a + (u.origin - a) * f))
        }
        CourseStep.MoveRoom -> indoor(RoomType.Camera, 0)?.let { r -> onPlan(GuidePlan.camera.points[0])?.let { moved(r, it) } }
        CourseStep.Stretch -> indoor(RoomType.Camera, 0)?.let { r -> onDrawn(r, GuidePlan.camera) }
        CourseStep.MoveBath -> bath()?.let { r -> onPlan(GuidePlan.bagno.points[0])?.let { moved(r, it) } }
        CourseStep.CornerC, CourseStep.CornerD -> bath()?.takeIf { it.points.size == GuidePlan.bagno.points.size }?.let { r ->
            val drawn = GuidePlan.bagno.points.map { onPlan(it) ?: return@let null }
            val i = if (step == CourseStep.CornerC) 2 else 3
            val corner = Polygon.inset(drawn, -r.wallThickness / 2)[i]
            plan.replace(r.copy(points = r.points.mapIndexed { k, p -> if (k == i) corner else p }))
        }
        CourseStep.Attach -> living()?.let { r ->
            val d = (cameraCorner() ?: return@let null) - topRight(r)
            plan.replace(r.copy(points = r.points.map { it + d }))
        }
        CourseStep.LivingWalls, CourseStep.LivingInfo -> living()?.let { r -> onDrawn(r, GuidePlan.soggiorno) }
        else -> null
    }

    /** La stanza `target` della piantina al suo posto nella casa di prova (aggiunta se manca). */
    private fun withRoom(target: GuidePlan.Room): FloorPlan? {
        val g = goal(target) ?: return null
        val r = room(target.type)
        val points = Polygon.fromInterior(g, r?.wallThickness ?: Room.WALL_THICKNESS)
        return if (r != null) plan.replace(r.copy(points = points))
        else plan.copy(rooms = plan.rooms + Room(plan.nextId, RoomFactory.autoName(plan, target.type), target.type, points, floorFinish = target.type.defaultFloor))
    }

    private fun newFreeWall(): FreeWall = FreeWall(plan.nextFreeWallId, Vec2.Zero, Vec2.Zero)

    private fun withFreeWall(w: FreeWall): FloorPlan =
        if (plan.freeWalls.any { it.id == w.id }) plan.replace(w) else plan.copy(freeWalls = plan.freeWalls + w)

    private fun bath(): Room? = room(RoomType.Bagno) ?: plan.rooms.lastOrNull()

    /** La stanza `r` spostata (senza cambiare forma) con l'angolo in alto a sinistra del filo interno in `corner`. */
    private fun moved(r: Room, corner: Vec2): FloorPlan {
        val d = corner - r.interior().minBy { it.x + it.y }
        return plan.replace(r.copy(points = r.points.map { it + d }))
    }

    /** La stanza `r` proprio sopra la stanza `target` disegnata sulla piantina. */
    private fun onDrawn(r: Room, target: GuidePlan.Room): FloorPlan? {
        val drawn = target.points.map { onPlan(it) ?: return null }
        return plan.replace(r.copy(points = Polygon.fromInterior(drawn, r.wallThickness)))
    }

    // ---------- Lo studio: i modi per cambiare forma a una stanza ----------

    private fun studio(): Room? = room(RoomType.Studio) ?: plan.rooms.firstOrNull { !it.type.outdoor }

    /** Selezionata: la striscia in basso, Info e i pallini degli angoli sono i suoi. */
    private fun selected(r: Room) = s.focusedRoomId == r.id && s.selection == null

    private fun select(r: Room): GuideHint = mark(c.selezionaStanza(r.name), PlanMark(listOf(Polygon.labelPoint(r.points))))

    /** Lo studio è di nuovo un quadrato dritto con il lato della piantina. */
    fun squareExact(): Boolean {
        val r = studio() ?: return false
        val side = GuidePlan.side(GuidePlan.studio, 0)
        val inner = r.interior()
        val n = inner.size
        if (n != 4 || r.interiorLengths().any { abs(it - side) >= 0.5 }) return false
        return (0 until n).all { k ->
            abs((inner[(k + 1) % n] - inner[k]).normalized() dot (inner[(k + 2) % n] - inner[(k + 1) % n]).normalized()) < 0.01
        }
    }

    /** Il muro di destra dello studio, trascinato più in là. */
    private fun squareWallHints(): List<GuideHint> {
        val r = studio() ?: return emptyList()
        val i = r.points.indices.maxBy { along(r, it).x }
        val from = along(r, i)
        return listOf(mark(c.trascinaMuroStudio, PlanMark(listOf(from)), PlanMark(listOf(from + Vec2(STRETCH_CM, 0.0)), goal = true)))
    }

    /** Le misure esatte in Info › Misure: il primo muro sbagliato tra quello in alto e quello di destra (ognuno sposta il muro dopo). */
    private fun squareInfoHints(): List<GuideHint> {
        val r = studio() ?: return emptyList()
        if (!selected(r)) return listOf(select(r))
        val side = GuidePlan.side(GuidePlan.studio, 0)
        val lengths = r.interiorLengths()
        return listOf(hint(GuideTarget.Info, c.misureEsatte(side)), hint(GuideTarget.MeasuresTab, c.schedaMisure)) +
            (0..1).filter { abs(lengths[it] - side) >= 0.5 }.map { i ->
                field(GuideField.WallInfo, side, c.muroStudio(i + 1, arrowOf(r.outwardOf(i)), side), index = i)
            }
    }

    /** L'angolo in basso a destra dello studio, trascinato in fuori. */
    private fun squareCornerHints(): List<GuideHint> {
        val r = studio() ?: return emptyList()
        if (!selected(r)) return listOf(select(r))
        val from = r.points.maxBy { it.x + it.y }
        return listOf(mark(c.trascinaAngoloStudio, PlanMark(listOf(from)), PlanMark(listOf(from + Vec2(CORNER_CM, CORNER_CM * 0.6)), goal = true)))
    }

    private fun arrowOf(v: Vec2): WallArrow = when {
        abs(v.x) >= abs(v.y) -> if (v.x > 0) WallArrow.RIGHT else WallArrow.LEFT
        else -> if (v.y > 0) WallArrow.DOWN else WallArrow.UP
    }

    // ---------- Il muro singolo: il tramezzo della cabina armadio, nella camera ----------

    private fun freeWall(): FreeWall? = plan.freeWalls.lastOrNull()

    /** Dove va il tramezzo nella casa di prova (in mezzeria): dall'alto in basso. Null senza lo studio. */
    private fun partition(): Pair<Vec2, Vec2>? = offset()?.let { o -> GuidePlan.tramezzo.start + o to GuidePlan.tramezzo.end + o }

    /** L'estremità in basso del muro singolo, quella da trascinare per accorciarlo. */
    private fun bottom(w: FreeWall): Vec2 = if (w.end.y >= w.start.y) w.end else w.start

    private fun upright(w: FreeWall): Boolean = abs(w.start.x - w.end.x) < 1.0

    private fun selectFreeWall(w: FreeWall): GuideHint = mark(c.selezionaMuroSingolo, PlanMark(listOf(w.mid)))

    /**
     * «Muro singolo» nella finestra delle forme: nasce nella stanza selezionata, così prima si seleziona la camera. Con la finestra
     * di un'altra forma si torna indietro.
     */
    private fun freeWallPickHints(): List<GuideHint> {
        val choose = hint(GuideTarget.Shape(ShapeChoice.FreeWall), c.scegliMuroSingolo)
        when (s.creation) {
            is CreationStep.PickShape -> return listOf(choose)
            null -> Unit
            else -> return listOf(hint(GuideTarget.Back, c.tornaIndietro))
        }
        val camera = room(RoomType.Camera)
        if (camera != null && (s.focusedRoomId != camera.id || s.selection != null)) {
            return listOf(mark(c.selezionaCameraTramezzo(camera.name), PlanMark(listOf(Polygon.labelPoint(camera.points)))))
        }
        return menu(c.apriElementiMuroSingolo, GuideMenu.Room to c.poiStanza) + choose
    }

    /** Il muro dritto, come nasce, portato di lato fino alla linea tratteggiata: lì va il tramezzo. */
    private fun freeWallMoveHints(): List<GuideHint> {
        val w = freeWall() ?: return freeWallPickHints()
        val (top, _) = partition() ?: return emptyList()
        val from = w.start + (w.end - w.start) * 0.7
        val line = listOf(Vec2(top.x, w.start.y), Vec2(top.x, w.end.y))
        return listOf(mark(c.spostaTramezzo, PlanMark(listOf(from)), PlanMark(listOf(Vec2(top.x, from.y)), goal = true), PlanMark(line, goal = true)))
    }

    fun freeWallInPlace(): Boolean {
        val w = freeWall() ?: return false
        val (top, _) = partition() ?: return false
        return upright(w) && abs(w.mid.x - top.x) <= FREE_WALL_FIT_CM
    }

    /** L'estremità in basso trascinata in su, fino a dove finisce il tramezzo: resta il passaggio per la cabina. */
    private fun freeWallEndHints(): List<GuideHint> {
        val w = freeWall() ?: return freeWallPickHints()
        if (s.selection != Selection.FreeWall(w.id)) return listOf(selectFreeWall(w))
        val (_, end) = partition() ?: return emptyList()
        val from = bottom(w)
        return listOf(mark(c.accorciaTramezzo, PlanMark(listOf(from)), PlanMark(listOf(Vec2(from.x, end.y)), goal = true)))
    }

    fun freeWallShortened(): Boolean {
        val w = freeWall() ?: return false
        val (_, end) = partition() ?: return false
        return upright(w) && abs(bottom(w).y - end.y) <= FREE_WALL_FIT_CM
    }

    private val partitionLength: Int get() = GuidePlan.tramezzo.length.roundToInt()
    private val partitionThickness: Int get() = GuidePlan.tramezzo.thickness.roundToInt()

    /** Lunghezza e spessore esatti nella scheda del muro singolo. */
    private fun freeWallInfoHints(): List<GuideHint> {
        val w = freeWall() ?: return freeWallPickHints()
        if (s.selection != Selection.FreeWall(w.id)) return listOf(selectFreeWall(w))
        return listOf(
            hint(GuideTarget.Info, c.infoTramezzo),
            field(GuideField.FreeWallLength, partitionLength, c.lunghezzaTramezzo(partitionLength)),
            field(GuideField.FreeWallThickness, partitionThickness, c.spessoreTramezzo(partitionThickness)),
        )
    }

    fun freeWallExact(): Boolean {
        val w = freeWall() ?: return false
        return abs(w.length - partitionLength) < 0.5 && abs(w.thickness - partitionThickness) < 0.5
    }

    // ---------- Dove cliccare sull'appartamento di prova ----------

    /** Primo angolo dell'ingresso: dove è sulla piantina, tra soggiorno, camera e bagno (senza lo studio, sotto le stanze). */
    fun entranceStart(): Vec2 {
        goal(GuidePlan.ingresso)?.let { return it[0] }
        val b = roomsBounds() ?: return Vec2.Zero
        val left = GuidePlan.ingresso.points.let { pts -> pts[0].x - pts.minOf { it.x } }
        return Vec2(b.minX + left + 30.0, b.maxY + 120.0)
    }

    private fun roomsBounds(): Bounds? = plan.rooms.flatMap { it.points }.takeIf { it.isNotEmpty() }?.let(Polygon::bounds)

    /** Muro `i` di `r`: estremi in mezzeria. */
    private fun wall(r: Room, i: Int) = r.points[i] to r.points[(i + 1) % r.points.size]

    /** Il muro `i` di `r` è in comune con un'altra stanza (ce n'è un'altra a meno di uno spessore dal suo centro). */
    private fun shared(r: Room, i: Int): Boolean {
        val (a, b) = wall(r, i)
        val mid = (a + b) / 2.0
        return plan.rooms.any { o -> o.id != r.id && o.points.indices.any { j -> wall(o, j).let { (p, q) -> distanceToSegment(mid, p, q) } <= (r.wallThickness + o.wallThickness) / 2 + 2.0 } }
    }

    private fun longestWall(r: Room, filter: (Int) -> Boolean): Int? =
        r.points.indices.filter(filter).maxByOrNull { wall(r, it).let { (a, b) -> a.distanceTo(b) } }

    /** Punto a `t` lungo il muro `i` (in mezzeria). */
    private fun along(r: Room, i: Int, t: Double = 0.5): Vec2 = wall(r, i).let { (a, b) -> a + (b - a) * t }

    private fun doorSpot(): Vec2 {
        val r = indoor(RoomType.Soggiorno, 2) ?: return Vec2.Zero
        val i = longestWall(r) { shared(r, it) } ?: longestWall(r) { true }!!
        return along(r, i, 0.35)
    }

    private fun windowSpot(): Vec2 {
        val r = indoor(RoomType.Soggiorno, 2) ?: return Vec2.Zero
        val i = longestWall(r) { !shared(r, it) } ?: 0
        return along(r, i)
    }

    /** Sul muro della casa dove si appoggia il balcone: il punto del muro più vicino al centro del lato del balcone verso la casa. */
    private fun frenchSpot(): Vec2? {
        val balcony = plan.rooms.lastOrNull { it.type.outdoor } ?: return null
        val houses = plan.rooms.filter { !it.type.outdoor }
        var best: Pair<Vec2, Double>? = null
        for (i in balcony.points.indices) {
            val (a, b) = wall(balcony, i)
            val mid = (a + b) / 2.0
            for (h in houses) for (j in h.points.indices) {
                val (p, q) = wall(h, j)
                val d = distanceToSegment(mid, p, q)
                if (best == null || d < best.second) best = projectOnSegment(mid, p, q) to d
            }
        }
        return best?.first
    }

    private fun radiatorSpot(): Vec2 {
        val r = indoor(RoomType.Studio, 0) ?: return Vec2.Zero
        val i = longestWall(r) { !shared(r, it) } ?: 0
        return along(r, i, 0.6)
    }

    private fun lightSpot(): Vec2 {
        val r = indoor(RoomType.Soggiorno, 2) ?: return Vec2.Zero
        return Polygon.labelPoint(r.points) + Vec2(0.0, 70.0)
    }

    private fun stairHints(): List<GuideHint> {
        val studio = indoor(RoomType.Studio, 0) ?: return emptyList()
        val chosen = s.focusedRoomId == studio.id || s.creation != null
        return if (!chosen) listOf(mark(c.cliccaStudio, PlanMark(listOf(Polygon.labelPoint(studio.points) + Vec2(0.0, 80.0)))))
        else menu(c.scalaElementi, GuideMenu.Stairs to c.scale, GuideMenu.Spiral to c.chiocciola)
    }

    // ---------- Sopra la piantina della guida ----------

    private fun onPlan(p: Vec2): Vec2? = plan.underlay?.let { GuidePlan.onUnderlay(it, p) }

    private fun underlayMark(p: Vec2, text: String): List<GuideHint> = listOfNotNull(onPlan(p)?.let { mark(text, PlanMark(listOf(it))) })

    /** Dove va la stanza `r`: il suo nome da trascinare e la stanza `target` disegnata sulla piantina. */
    private fun moveOnto(r: Room?, target: GuidePlan.Room, text: String): List<GuideHint> {
        r ?: return emptyList()
        val goal = target.points.mapNotNull(::onPlan).takeIf { it.size == target.points.size } ?: return emptyList()
        return listOf(mark(text, PlanMark(listOf(Polygon.labelPoint(r.points))), PlanMark(goal, goal = true)))
    }

    /** Muro di destra della camera fino al muro di destra della camera disegnata. */
    private fun stretchHints(): List<GuideHint> {
        val r = indoor(RoomType.Camera, 0) ?: return emptyList()
        val right = r.points.indices.maxByOrNull { along(r, it).x } ?: return emptyList()
        val from = along(r, right, 0.3)
        val targetX = onPlan(GuidePlan.camera.points[1])?.x ?: return emptyList()
        val to = Vec2(targetX + r.wallThickness / 2, from.y)
        return listOf(mark(c.allungaCamera(GuidePlan.side(GuidePlan.camera, 0)), PlanMark(listOf(from)), PlanMark(listOf(to), goal = true)))
    }

    /** Angolo `i` del bagno (pallino in mezzeria) fino all'angolo del bagno disegnato, mezzo muro più in fuori. */
    private fun cornerHints(i: Int, text: String): List<GuideHint> {
        val r = room(RoomType.Bagno) ?: plan.rooms.lastOrNull() ?: return emptyList()
        if (r.points.size != GuidePlan.bagno.points.size) return emptyList()
        val drawn = GuidePlan.bagno.points.mapNotNull(::onPlan).takeIf { it.size == GuidePlan.bagno.points.size } ?: return emptyList()
        val goal = Polygon.inset(drawn, -r.wallThickness / 2)[i]
        return listOf(mark(text, PlanMark(listOf(r.points[i])), PlanMark(listOf(goal), goal = true)))
    }

    // ---------- Il soggiorno a L sopra la piantina ----------

    private fun living(): Room? = room(RoomType.Soggiorno)

    /**
     * Di quanti angoli è spostata la numerazione del soggiorno rispetto a quello disegnato: il muro `j` disegnato è il muro
     * `(j + k) % n` della stanza. Null se la stanza ha un'altra forma (per esempio la L sporge da un'altra parte).
     */
    private fun turn(r: Room): Int? {
        val drawn = GuidePlan.soggiorno.points
        val n = drawn.size
        if (r.points.size != n) return null
        fun dir(p: List<Vec2>, i: Int) = (p[(i + 1) % n] - p[i]).normalized()
        return (0 until n).firstOrNull { k -> (0 until n).all { j -> (dir(r.points, (j + k) % n) dot dir(drawn, j)) > 0.98 } }
    }

    /** Angolo in alto a sinistra della camera e in alto a destra del soggiorno (in mezzeria): attaccati, coincidono. */
    private fun cameraCorner(): Vec2? = indoor(RoomType.Camera, 0)?.points?.minByOrNull { it.x + it.y }
    private fun topRight(r: Room): Vec2 = r.points.maxBy { it.x - it.y }

    fun attached(): Boolean {
        val r = living() ?: return false
        val corner = cameraCorner() ?: return false
        return topRight(r).distanceTo(corner) < 1.0
    }

    /** Il soggiorno dal nome fino a dove si attacca alla camera (contorno interno, tratteggiato). */
    private fun attachHints(): List<GuideHint> {
        val r = living() ?: return emptyList()
        val corner = cameraCorner() ?: return emptyList()
        val d = corner - topRight(r)
        return listOf(mark(c.attaccaSoggiorno, PlanMark(listOf(Polygon.labelPoint(r.points))), PlanMark(r.interior().map { it + d }, goal = true)))
    }

    /**
     * Muri del soggiorno ancora lontani dalla piantina, nell'ordine in cui farli trascinare: muro disegnato, punto da cui
     * trascinare e dove arriva. La piantina si misura dall'angolo in alto a destra, quello attaccato alla camera, così i muri
     * in comune restano dove sono. Null se il soggiorno non c'è o ha un'altra forma.
     */
    fun livingWalls(): List<Triple<Int, Vec2, Vec2>>? {
        val r = living() ?: return null
        val k = turn(r) ?: return null
        val drawn = GuidePlan.soggiorno.points.map { onPlan(it) ?: return null }
        val n = drawn.size
        val inner = r.interior()
        val shift = inner[(1 + k) % n] - drawn[1]
        return LIVING_WALL_ORDER.mapNotNull { j ->
            val i = (j + k) % n
            val out = r.outwardOf(i)
            val off = ((inner[i] + inner[(i + 1) % n]) / 2.0 - (drawn[j] + shift)) dot out
            if (abs(off) <= LIVING_FIT_CM) null
            else along(r, i, 0.3).let { from -> Triple(j, from, from - out * off) }
        }
    }

    /**
     * Lunghezze da scrivere in Info, nell'ordine giusto (ognuna sposta solo il muro dopo, mai quelli attaccati alla camera):
     * muro della stanza, muro disegnato e misura. Gli altri due lati vengono giusti da soli.
     */
    private fun livingLengths(): List<Triple<Int, Int, Int>>? {
        val r = living() ?: return null
        val k = turn(r) ?: return null
        return (1..4).map { j -> Triple((j + k) % r.points.size, j, GuidePlan.side(GuidePlan.soggiorno, j)) }
    }

    fun livingExact(): Boolean {
        val r = living() ?: return false
        val lengths = r.interiorLengths()
        return livingLengths()?.all { (i, _, v) -> abs(lengths[i] - v) < 0.5 } == true
    }

    private fun livingInfoHints(): List<GuideHint> {
        val r = living() ?: return emptyList()
        if (!selected(r)) return listOf(select(r))
        val lengths = livingLengths() ?: return emptyList()
        return listOf(hint(GuideTarget.Info, c.misureInInfo), hint(GuideTarget.MeasuresTab, c.schedaMisure)) +
            lengths.mapIndexed { n, (i, j, v) -> field(GuideField.WallInfo, v, c.muroInfo(i + 1, j, v, first = n == 0), index = i) }
    }

    // ---------- Cosa inquadrare ----------

    fun focus(step: CourseStep): Bounds? = when (step) {
        CourseStep.SquareWall, CourseStep.SquareCorner -> studio()?.let { pad(Polygon.bounds(it.points), STRETCH_CM + 60.0) }
        // La stanza staccata, la casa e il posto dove va.
        CourseStep.Snap, CourseStep.LMove, CourseStep.RectMove, CourseStep.SurveyMove ->
            (plan.rooms.flatMap { it.points } + step.lesson.planRoom?.let(::goal).orEmpty()).takeIf { it.isNotEmpty() }?.let { pad(Polygon.bounds(it), 60.0) }
        CourseStep.WallsFirst -> roomsBounds()?.let { b ->
            val st = entranceStart()
            val pts = GuidePlan.ingresso.points.map { it - GuidePlan.ingresso.points[0] + st }
            pad(b.union(Polygon.bounds(pts)), 40.0)
        }
        CourseStep.FreeWallPick, CourseStep.FreeWallDone -> roomsBounds()?.let { pad(it, 60.0) }
        CourseStep.FreeWallMove, CourseStep.FreeWallEnd, CourseStep.FreeWallInfo -> room(RoomType.Camera)?.let { pad(Polygon.bounds(it.points), 60.0) }
        CourseStep.CalibA -> plan.underlay?.let { u -> Bounds(u.origin.x, u.origin.y, u.origin.x + u.widthCm, u.origin.y + u.heightCm) }
        CourseStep.InnerFace, CourseStep.MoveRoom, CourseStep.Stretch -> withGoal(indoor(RoomType.Camera, 0), GuidePlan.camera, 60.0)
        CourseStep.MoveBath -> withGoal(room(RoomType.Bagno), GuidePlan.bagno, 60.0)
        CourseStep.CornerC -> withGoal(room(RoomType.Bagno), GuidePlan.bagno, 40.0)
        CourseStep.Attach -> listOfNotNull(living(), indoor(RoomType.Camera, 0)).flatMap { it.points }
            .plus(GuidePlan.soggiorno.points.mapNotNull(::onPlan)).takeIf { it.isNotEmpty() }?.let { pad(Polygon.bounds(it), 60.0) }
        CourseStep.LivingWalls -> withGoal(living(), GuidePlan.soggiorno, 60.0)
        else -> null
    }

    private fun withGoal(r: Room?, target: GuidePlan.Room, margin: Double): Bounds? {
        val goal = target.points.mapNotNull(::onPlan)
        val pts = goal + r?.points.orEmpty()
        if (pts.isEmpty()) return null
        return pad(Polygon.bounds(pts), margin)
    }

    private fun pad(b: Bounds, margin: Double) = Bounds(b.minX - margin, b.minY - margin, b.maxX + margin, b.maxY + margin)
}

/** Quanto più in là si trascina il muro di destra dello studio, e l'angolo in basso a destra. */
private const val STRETCH_CM = 70.0
private const val CORNER_CM = 60.0

/** Il soggiorno a L creato saltando il passo delle misure, rispetto a quello disegnato. */
private const val SKIPPED_LIVING_SCALE = 0.85

/** Una stanza nuova nasce così lontana dalla casa (in mezzeria): staccata, ma vicina al suo posto. */
private const val GAP_CM = 120.0

/** Quanto può scostarsi dal disegno una stanza al suo posto: le misure del bagno sono arrotondate al centimetro. */
private const val PLACE_CM = 3.0

/** Quanto il tramezzo trascinato a mano può restare lontano dal suo posto: la lunghezza esatta si scrive dopo in Info. */
private const val FREE_WALL_FIT_CM = 12.0

/** Muri del soggiorno da trascinare, nell'ordine (numerazione di quello disegnato): sinistra, in basso, poi il resto. */
private val LIVING_WALL_ORDER = listOf(5, 4, 2, 3, 1, 0)

/** Quanto un muro può restare lontano da quello disegnato quando lo si trascina a mano: la misura esatta si scrive dopo. */
private const val LIVING_FIT_CM = 10.0

private fun projectOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
    val ab = b - a
    val len2 = ab dot ab
    if (len2 < 1e-9) return a
    val t = (((p - a) dot ab) / len2).coerceIn(0.0, 1.0)
    return a + ab * t
}

private fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double = p.distanceTo(projectOnSegment(p, a, b))
