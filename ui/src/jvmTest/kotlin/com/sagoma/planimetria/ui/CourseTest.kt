package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.Course
import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.GuidePlan
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.ShapeChoice
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guida interattiva nell'editor: ogni azione fa avanzare il passo giusto, le stanze nascono staccate e, portate al loro posto con
 * gli agganci, fanno la casa della piantina; la guida non lascia parlare né il tutorial di una volta né le mini guide.
 */
class CourseTest {
    private class MemoryTips(var seen: Set<String> = emptySet()) : TipStore {
        override fun seen() = seen
        override fun save(seen: Set<String>) { this.seen = seen }
    }

    private fun emptyProject(tips: TipStore = MemoryTips()) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(), autoLevel = true)
        override fun save(building: Building) {}
    }, tips).also { it.setViewport(1400f, 750f, 24f) }

    private val EditorViewModel.step get() = state.value.course?.step
    private fun EditorViewModel.room(type: RoomType): Room = state.value.plan.rooms.last { it.type == type }

    /** Di quanto la casa di prova è spostata rispetto alla piantina (lo studio fa da riferimento). */
    private fun EditorViewModel.offset(): Vec2 = room(RoomType.Studio).interior().minBy { it.x + it.y } - GuidePlan.studio.points[0]

    /** La stanza `target` della piantina è al suo posto nella casa di prova, al centimetro (il bagno entro 3 cm). */
    private fun EditorViewModel.inPlace(target: GuidePlan.Room): Boolean {
        val inner = room(target.type).interior()
        val o = offset()
        return inner.size == target.points.size && target.points.all { p -> inner.any { it.distanceTo(p + o) < 3.0 } }
    }

    /** Trascina la stanza dal nome di `delta`, come col mouse. */
    private fun EditorViewModel.moveRoom(type: RoomType, delta: Vec2) {
        val r = room(type)
        val at = Polygon.labelPoint(r.points)
        beginDrag(DragTarget.RoomLabel(r.id), at)
        dragBy(DragTarget.RoomLabel(r.id), delta)
        endDrag()
    }

    /** La stanza `type` portata (più o meno: entro 10 cm) dove va: gli agganci la attaccano al centimetro. */
    private fun EditorViewModel.bringToPlace(type: RoomType, target: GuidePlan.Room) {
        val goal = Polygon.bounds(target.points.map { it + offset() })
        val now = Polygon.bounds(room(type).interior())
        moveRoom(type, Vec2(goal.minX - now.minX + 8.0, goal.minY - now.minY - 6.0))
    }

    /** Distanza minima tra la stanza `type` e le altre (in mezzeria). */
    private fun EditorViewModel.gap(type: RoomType): Double {
        val r = room(type)
        val b = Polygon.bounds(r.points)
        return state.value.plan.rooms.filter { it.id != r.id }.minOf { o ->
            val ob = Polygon.bounds(o.points)
            maxOf(ob.minX - b.maxX, b.minX - ob.maxX, ob.minY - b.maxY, b.minY - ob.maxY)
        }
    }

    @Test
    fun `lo studio insegna i modi per cambiare forma e le stanze nuove si portano al loro posto`() {
        val tips = MemoryTips()
        val vm = emptyProject(tips)
        vm.startCourse(Lesson.Square)
        assertEquals(CourseStep.Welcome, vm.step)
        vm.nextCourseStep()
        assertEquals(CourseStep.SquarePick, vm.step)
        vm.pickShape(RoomShape.Rectangle)
        assertEquals(CourseStep.SquarePick, vm.step, "la forma sbagliata non fa avanzare")
        vm.backToShapePicker()
        vm.pickShape(RoomShape.Square)
        assertEquals(CourseStep.SquareMeasures, vm.step)
        vm.createRoom(RoomType.Studio, ShapeDimensions(330.0, 330.0), 270.0)
        assertEquals(CourseStep.SquareWall, vm.step)
        assertNull(vm.state.value.tutorial, "la prima stanza non fa partire il tutorial di una volta")
        assertNull(vm.state.value.tip, "durante la guida niente mini guide")

        // Il muro di destra trascinato: lo studio si allarga, e la guida chiede la misura esatta in Info.
        val studio = vm.room(RoomType.Studio)
        val right = studio.points.indices.maxBy { (studio.points[it] + studio.points[(it + 1) % 4]).x }
        vm.beginDrag(DragTarget.Wall(studio.id, right), (studio.points[right] + studio.points[(right + 1) % 4]) / 2.0)
        vm.dragBy(DragTarget.Wall(studio.id, right), Vec2(68.0, 0.0))
        vm.endDrag()
        assertEquals(CourseStep.SquareInfo, vm.step)
        assertEquals(GuideTarget.Info, Course.hints(CourseStep.SquareInfo, vm.state.value).first().target, "lo studio è già selezionato: si parte da Info")
        vm.setWallLength(studio.id, 0, 330.0)
        assertEquals(CourseStep.SquareCorner, vm.step)
        assertFalse(vm.state.value.infoExpanded)

        // L'angolo storto, poi Annulla: lo studio torna dritto. Annullare un'altra modifica non basta.
        val corner = vm.room(RoomType.Studio).points.indices.maxBy { vm.room(RoomType.Studio).points[it].let { p -> p.x + p.y } }
        vm.beginDrag(DragTarget.Corner(studio.id, corner), vm.room(RoomType.Studio).points[corner])
        vm.dragBy(DragTarget.Corner(studio.id, corner), Vec2(60.0, 36.0))
        vm.endDrag()
        assertEquals(CourseStep.SquareUndo, vm.step)
        vm.undo()
        assertEquals(CourseStep.SquareDone, vm.step)
        assertTrue(vm.room(RoomType.Studio).interiorLengths().all { kotlin.math.abs(it - 330.0) < 0.5 })

        // Il soggiorno nasce staccato, sopra il suo posto; trascinato vicino, gli agganci lo attaccano sopra lo studio.
        vm.nextCourseStep()
        assertEquals(CourseStep.LPick, vm.step)
        vm.startAddRoom()
        vm.pickShape(RoomShape.L)
        vm.pickLOrientation(LOrientation.BottomRight)
        val sg = GuidePlan.soggiorno
        vm.createRoom(RoomType.Soggiorno, ShapeDimensions(640.0, 270.0, GuidePlan.side(sg, 2).toDouble(), GuidePlan.side(sg, 3).toDouble()), 270.0)
        assertEquals(CourseStep.Snap, vm.step)
        assertTrue(vm.gap(RoomType.Soggiorno) > 90.0, "il soggiorno non nasce attaccato: ${vm.gap(RoomType.Soggiorno)}")
        assertFalse(vm.inPlace(sg))
        vm.nextCourseStep()
        assertEquals(CourseStep.LMove, vm.step)
        vm.moveRoom(RoomType.Soggiorno, Vec2(400.0, 0.0))
        assertEquals(CourseStep.LMove, vm.step, "spostato altrove non basta")
        vm.bringToPlace(RoomType.Soggiorno, sg)
        assertTrue(vm.inPlace(sg))
        assertEquals(CourseStep.LDone, vm.step)

        // La camera a destra del soggiorno, il bagno sotto la camera.
        vm.nextCourseStep()
        vm.startAddRoom()
        vm.pickShape(RoomShape.Rectangle)
        vm.createRoom(RoomType.Camera, ShapeDimensions(400.0, 330.0), 270.0)
        assertEquals(CourseStep.RectMove, vm.step)
        assertTrue(vm.gap(RoomType.Camera) > 90.0)
        vm.bringToPlace(RoomType.Camera, GuidePlan.camera)
        assertEquals(CourseStep.RectDone, vm.step)
        vm.nextCourseStep()
        vm.startAddRoom()
        vm.pickSurvey()
        assertEquals(CourseStep.SurveyMeasures, vm.step)
        vm.createRoomFromSurvey(RoomType.Bagno, com.sagoma.planimetria.geometry.Survey.solve(GuidePlan.bathSides.map { it.toDouble() }, listOf(GuidePlan.bathDiagonal.toDouble()))!!, 270.0, Room.WALL_THICKNESS)
        assertEquals(CourseStep.SurveyMove, vm.step)
        assertTrue(vm.gap(RoomType.Bagno) > 90.0)
        vm.bringToPlace(RoomType.Bagno, GuidePlan.bagno)
        assertEquals(CourseStep.SurveyDone, vm.step)

        // L'ingresso comincia nell'angolo tra soggiorno, camera e bagno, e coi muri della piantina chiude la casa.
        vm.nextCourseStep()
        vm.startAddRoom()
        vm.startDrawWalls()
        assertEquals(CourseStep.WallsFirst, vm.step)
        val start = Course.hints(CourseStep.WallsFirst, vm.state.value).single().marks.single().points.single()
        assertTrue(start.distanceTo(GuidePlan.ingresso.points[0] + vm.offset()) < 0.01)
        vm.drawWallsTap(start + Vec2(2.0, -3.0))
        assertEquals(CourseStep.WallsTyped, vm.step)
        for ((arrow, length) in GuidePlan.entranceWalls) vm.drawWallsTyped(length.toDouble(), arrow)
        assertEquals(CourseStep.WallsClose, vm.step)
        vm.drawWallsClose()
        vm.finishDrawWalls(RoomType.Corridoio, 270.0, Room.WALL_THICKNESS)
        assertEquals(CourseStep.WallsDone, vm.step)
        for (r in listOf(GuidePlan.studio, GuidePlan.soggiorno, GuidePlan.camera, GuidePlan.bagno, GuidePlan.ingresso)) {
            assertTrue(vm.inPlace(r), "${r.type} non è al suo posto")
        }
        assertNull(vm.state.value.tip, "durante la guida niente mini guide")

        vm.finishCourse(completed = false)
        assertNull(vm.step)
        assertTrue(TipStore.COURSE_DONE in tips.seen && TipStore.TUTORIAL_DONE in tips.seen)
    }

    @Test
    fun `il muro singolo nasce nella camera e diventa il tramezzo della cabina armadio`() {
        val vm = emptyProject()
        vm.startCourse(Lesson.FreeWall)
        assertEquals(CourseStep.FreeWallPick, vm.step)
        assertTrue(vm.state.value.plan.freeWalls.isEmpty())
        val camera = vm.room(RoomType.Camera)
        assertTrue(Course.hints(CourseStep.FreeWallPick, vm.state.value).single().marks.isNotEmpty(), "prima si seleziona la camera")
        vm.focusRoom(camera.id)
        assertEquals(GuideTarget.Elements, Course.hints(CourseStep.FreeWallPick, vm.state.value).first().target)
        vm.startAddRoom()
        assertEquals(GuideTarget.Shape(ShapeChoice.FreeWall), Course.hints(CourseStep.FreeWallPick, vm.state.value).single().target)
        vm.addFreeWall()
        assertEquals(CourseStep.FreeWallMove, vm.step)
        assertNull(vm.state.value.tip, "durante la guida niente mini guide")

        // Nato dritto attraverso la camera, da un muro all'altro: si porta di lato, più o meno, fino al suo posto.
        val w = vm.state.value.plan.freeWalls.single()
        val inner = Polygon.bounds(camera.interior())
        assertTrue(kotlin.math.abs(w.start.x - w.end.x) < 0.01 && w.start.y < w.end.y, "verticale, dall'alto in basso")
        assertTrue(kotlin.math.abs(w.start.y - inner.minY) < 0.5 && kotlin.math.abs(w.end.y - inner.maxY) < 0.5, "da un muro all'altro")
        val top = GuidePlan.tramezzo.start + vm.offset()
        vm.beginDrag(DragTarget.FreeWallBody(w.id), w.mid)
        vm.dragBy(DragTarget.FreeWallBody(w.id), Vec2(30.0, 0.0))
        vm.endDrag()
        assertEquals(CourseStep.FreeWallMove, vm.step, "non ancora al suo posto")
        val w1 = vm.state.value.plan.freeWalls.single()
        vm.beginDrag(DragTarget.FreeWallBody(w.id), w1.mid)
        vm.dragBy(DragTarget.FreeWallBody(w.id), Vec2(top.x - w1.mid.x + 5.0, 0.0))
        vm.endDrag()
        assertEquals(CourseStep.FreeWallEnd, vm.step)

        // L'estremità in basso trascinata in su: resta il passaggio; poi lunghezza e spessore esatti in Info.
        val w2 = vm.state.value.plan.freeWalls.single()
        val end = GuidePlan.tramezzo.end + vm.offset()
        vm.beginDrag(DragTarget.FreeWallEnd(w.id, 1), w2.end)
        vm.dragBy(DragTarget.FreeWallEnd(w.id, 1), Vec2(0.0, end.y - w2.end.y + 6.0))
        vm.endDrag()
        assertEquals(CourseStep.FreeWallInfo, vm.step)
        val w3 = vm.state.value.plan.freeWalls.single()
        vm.updateFreeWall(w3.copy(end = w3.start + (w3.end - w3.start).normalized() * GuidePlan.tramezzo.length))
        assertEquals(CourseStep.FreeWallInfo, vm.step, "manca lo spessore")
        vm.updateFreeWall(vm.state.value.plan.freeWalls.single().copy(thickness = GuidePlan.tramezzo.thickness))
        assertEquals(CourseStep.FreeWallDone, vm.step)
        val done = vm.state.value.plan.freeWalls.single()
        assertTrue(done.start.distanceTo(top) < 6.0 && done.end.distanceTo(end) < 6.0, "il tramezzo è dove sta sulla piantina: $done")

        // Dalla lezione dopo, la casa di prova ha già il tramezzo al suo posto.
        vm.startCourse(Lesson.Elements)
        assertEquals(listOf(GuidePlan.tramezzo.start to GuidePlan.tramezzo.end), vm.state.value.plan.freeWalls.map { it.start to it.end })
    }

    @Test
    fun `una lezione scelta dal benvenuto riparte dalle stanze delle lezioni prima, al loro posto`() {
        val vm = emptyProject()
        vm.startCourse(Lesson.Survey)
        assertEquals(CourseStep.SurveyPick, vm.step)
        assertEquals(listOf(RoomType.Studio, RoomType.Soggiorno, RoomType.Camera), vm.state.value.plan.rooms.map { it.type })
        for (r in listOf(GuidePlan.studio, GuidePlan.soggiorno, GuidePlan.camera)) assertTrue(vm.inPlace(r))
        assertNull(vm.state.value.creation)
    }

    /** «Salta passo» sul passo `step` (le schede da leggere con «Avanti»). */
    private fun EditorViewModel.skip(step: CourseStep) {
        assertEquals(step, this.step)
        if (step.action) skipCourseStep() else nextCourseStep()
    }

    @Test
    fun `saltando i passi la casa diventa quella che avrebbero fatto, e i passi dopo si possono fare`() {
        val vm = emptyProject()
        vm.startCourse(Lesson.Square)
        vm.skip(CourseStep.Welcome)
        vm.skip(CourseStep.SquarePick)
        // Senza lo studio, il muro da trascinare non ci sarebbe: saltando la creazione lo studio nasce, e il passo dopo si fa a mano.
        vm.skip(CourseStep.SquareMeasures)
        assertEquals(CourseStep.SquareWall, vm.step)
        assertNull(vm.state.value.creation, "la finestra delle forme si chiude")
        val studio = vm.room(RoomType.Studio)
        assertTrue(studio.interiorLengths().all { kotlin.math.abs(it - 330.0) < 0.5 })
        val right = studio.points.indices.maxBy { (studio.points[it] + studio.points[(it + 1) % 4]).x }
        vm.beginDrag(DragTarget.Wall(studio.id, right), (studio.points[right] + studio.points[(right + 1) % 4]) / 2.0)
        vm.dragBy(DragTarget.Wall(studio.id, right), Vec2(68.0, 0.0))
        vm.endDrag()
        assertEquals(CourseStep.SquareInfo, vm.step)
        vm.skip(CourseStep.SquareInfo)
        // L'angolo saltato viene trascinato dalla guida: Annulla lo rimette dritto, come se l'avesse fatto l'utente.
        vm.skip(CourseStep.SquareCorner)
        assertEquals(CourseStep.SquareUndo, vm.step)
        assertFalse(vm.state.value.plan.rooms.single().interiorLengths().all { kotlin.math.abs(it - 330.0) < 0.5 })
        vm.undo()
        assertEquals(CourseStep.SquareDone, vm.step)

        // Tutto il resto saltato: ogni stanza nasce e va al suo posto, l'ingresso, il tramezzo, il balcone.
        while (vm.step != CourseStep.ElementsDone) vm.skip(vm.step!!)
        for (r in listOf(GuidePlan.studio, GuidePlan.soggiorno, GuidePlan.camera, GuidePlan.bagno, GuidePlan.ingresso)) {
            assertTrue(vm.inPlace(r), "${r.type} non è al suo posto")
        }
        assertEquals(1, vm.state.value.plan.rooms.count { it.type == RoomType.Soggiorno }, "nessuna stanza doppia")
        val w = vm.state.value.plan.freeWalls.single()
        assertTrue(w.start.distanceTo(GuidePlan.tramezzo.start + vm.offset()) < 0.5 && w.end.distanceTo(GuidePlan.tramezzo.end + vm.offset()) < 0.5)
        assertEquals(GuidePlan.tramezzo.thickness, w.thickness)
        assertTrue(vm.state.value.plan.rooms.any { it.type == RoomType.Balcone })
        assertNull(vm.state.value.wallDraw)
    }

    @Test
    fun `saltando un passo a metà di una lezione si può fare il passo dopo`() {
        val vm = emptyProject()
        vm.startCourse(Lesson.LShape)
        vm.skip(CourseStep.LPick)
        vm.skip(CourseStep.LSide)
        vm.skip(CourseStep.LMeasures)
        assertEquals(CourseStep.Snap, vm.step)
        assertTrue(vm.gap(RoomType.Soggiorno) > 90.0, "nasce staccato, come creato a mano")
        vm.nextCourseStep()
        vm.bringToPlace(RoomType.Soggiorno, GuidePlan.soggiorno)
        assertEquals(CourseStep.LDone, vm.step)

        // Il disegno dell'ingresso saltato a metà: l'ingresso c'è già, chiuso al suo posto.
        vm.startCourse(Lesson.Walls)
        vm.startAddRoom()
        vm.startDrawWalls()
        vm.drawWallsTap(Course.hints(CourseStep.WallsFirst, vm.state.value).single().marks.single().points.single())
        vm.skip(CourseStep.WallsTyped)
        assertEquals(CourseStep.WallsDone, vm.step)
        assertTrue(vm.inPlace(GuidePlan.ingresso))
        assertNull(vm.state.value.wallDraw)

        // Il tramezzo saltato: nasce nella camera e il passo dopo (portarlo al suo posto) si fa a mano.
        vm.startCourse(Lesson.FreeWall)
        vm.skip(CourseStep.FreeWallPick)
        assertEquals(CourseStep.FreeWallMove, vm.step)
        assertEquals(1, vm.state.value.plan.freeWalls.size)
    }

    @Test
    fun `sopra la planimetria saltando i passi la taratura e le stanze vengono come sulla piantina`() {
        val vm = emptyProject()
        vm.startCourse(Lesson.OverPlan)
        assertFalse(Course.canSkip(CourseStep.TraceLoad), "senza la piantina il resto della lezione non si fa")
        vm.skip(CourseStep.TraceStart)
        assertEquals(CourseStep.TraceLoad, vm.step)
        // La piantina della guida, grande la metà e non tarata, come la carica l'app.
        vm.setUnderlay(com.sagoma.planimetria.model.Underlay("guida.png", GuidePlan.width / 2, GuidePlan.height / 2, origin = Vec2(-300.0, -200.0), cmPerPx = 3.7))
        assertEquals(CourseStep.CalibA, vm.step)
        vm.skip(CourseStep.CalibA)
        assertEquals(CourseStep.PlanShape, vm.step)
        val u = vm.state.value.plan.underlay!!
        val a = GuidePlan.onUnderlay(u, GuidePlan.calibrationA)
        val b = GuidePlan.onUnderlay(u, GuidePlan.calibrationB)
        assertTrue(kotlin.math.abs(a.distanceTo(b) - GuidePlan.calibrationCm) < 0.5, "tarata: ${a.distanceTo(b)}")
        assertTrue(vm.state.value.creation is com.sagoma.planimetria.editor.CreationStep.PickShape, "si torna alla scelta della stanza")

        while (vm.step != CourseStep.Finished) vm.skip(vm.step!!)
        fun onPlan(target: GuidePlan.Room): Boolean {
            val inner = vm.room(target.type).interior()
            return inner.size == target.points.size && target.points.all { p -> inner.any { it.distanceTo(GuidePlan.onUnderlay(u, p)) < 1.0 } }
        }
        for (r in listOf(GuidePlan.camera, GuidePlan.bagno, GuidePlan.soggiorno)) assertTrue(onPlan(r), "${r.type} non è sulla piantina")
    }

    @Test
    fun `indietro torna al passo prima con la casa com'era, e il passo si può rifare`() {
        val vm = emptyProject()
        vm.startCourse(Lesson.Square)
        assertFalse(vm.state.value.course!!.canGoBack, "al primo passo non c'è un passo prima")
        vm.nextCourseStep()
        vm.pickShape(RoomShape.Square)
        vm.createRoom(RoomType.Studio, ShapeDimensions(330.0, 330.0), 270.0)
        assertEquals(CourseStep.SquareWall, vm.step)
        assertTrue(vm.state.value.course!!.canGoBack)

        // Indietro: lo studio sparisce e torna la finestra delle misure, come prima di creare la stanza.
        vm.previousCourseStep()
        assertEquals(CourseStep.SquareMeasures, vm.step)
        assertTrue(vm.state.value.plan.rooms.isEmpty())
        assertTrue(vm.state.value.creation is com.sagoma.planimetria.editor.CreationStep.Measures)
        vm.createRoom(RoomType.Studio, ShapeDimensions(330.0, 330.0), 270.0)
        assertEquals(CourseStep.SquareWall, vm.step, "il passo si rifà")

        // Anche dopo un passo saltato: indietro rimette lo studio dritto, prima del muro trascinato dalla guida.
        vm.skipCourseStep()
        assertEquals(CourseStep.SquareInfo, vm.step)
        vm.previousCourseStep()
        assertEquals(CourseStep.SquareWall, vm.step)
        assertTrue(vm.room(RoomType.Studio).interiorLengths().all { kotlin.math.abs(it - 330.0) < 0.5 })
        vm.previousCourseStep()
        vm.previousCourseStep()
        vm.previousCourseStep()
        assertEquals(CourseStep.Welcome, vm.step)
        assertFalse(vm.state.value.course!!.canGoBack)
    }
}
