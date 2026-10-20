package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.CreationStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.editor.TutorialCourse
import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.editor.UnderlayTool
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Guida al ricalco: ogni passo compare quando l'utente ha fatto quello prima. */
class TracingGuideTest {
    private class MemoryTips(var seen: Set<String> = emptySet()) : TipStore {
        override fun seen() = seen
        override fun save(seen: Set<String>) { this.seen = seen }
    }

    private fun emptyProject(tips: TipStore = MemoryTips()) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(), autoLevel = true)
        override fun save(building: Building) {}
    }, tips)

    private val EditorViewModel.step get() = state.value.tutorial?.step

    private fun EditorViewModel.loadPlan() {
        setUnderlay(Underlay("sfondo.jpg", 1000, 600, cmPerPx = 1.2))
        setUnderlayTool(UnderlayTool.Calibrate())
    }

    private fun EditorViewModel.square(x0: Double, y0: Double) {
        drawWallsTap(Vec2(x0, y0))
        drawWallsTap(Vec2(x0 + 400.0, y0))
        drawWallsTap(Vec2(x0 + 400.0, y0 + 300.0))
        drawWallsTap(Vec2(x0, y0 + 300.0))
    }

    @Test
    fun `dal caricamento della piantina alla seconda stanza, un passo per azione`() {
        val tips = MemoryTips()
        val vm = emptyProject(tips)
        vm.startTracing()
        assertEquals(TutorialStep.TraceLoad, vm.step)
        assertEquals(TutorialCourse.Tracing, vm.state.value.tutorial!!.course)
        assertNull(vm.state.value.tip, "durante la guida niente suggerimenti")

        vm.loadPlan()
        assertEquals(TutorialStep.TraceCalibA, vm.step)
        vm.underlayTap(Vec2(0.0, 0.0))
        assertEquals(TutorialStep.TraceCalibB, vm.step)
        vm.underlayTap(Vec2(300.0, 0.0))
        assertEquals(TutorialStep.TraceCalibValue, vm.step)
        vm.underlayTap(Vec2(10.0, 0.0)) // terzo tocco: si ricomincia da A, e la guida torna al punto B
        assertEquals(TutorialStep.TraceCalibB, vm.step)
        vm.underlayTap(Vec2(310.0, 0.0))
        vm.calibrateUnderlay(450.0)
        assertEquals(TutorialStep.TraceFirstCorner, vm.step)
        assertNotNull(vm.state.value.wallDraw)

        vm.drawWallsTap(Vec2(0.0, 0.0))
        assertEquals(TutorialStep.TraceCorners, vm.step)
        vm.drawWallsTap(Vec2(400.0, 0.0))
        assertEquals("Angoli cliccati: 2 · ne servono almeno 3", vm.state.value.tutorial!!.detail)
        vm.drawWallsTap(Vec2(400.0, 300.0))
        assertEquals(TutorialStep.TraceClose, vm.step)
        vm.drawWallsTap(Vec2(0.0, 300.0))
        vm.drawWallsClose()
        vm.finishDrawWalls(RoomType.Soggiorno, 270.0, 30.0)
        assertEquals(TutorialStep.TraceCheck, vm.step, "la prima stanza non fa partire il tutorial generale")

        vm.setInfoExpanded(true)
        assertEquals(TutorialStep.TraceFade, vm.step)
        vm.setInfoExpanded(false)
        vm.setDrawingOpacity(0.5)
        assertEquals(TutorialStep.TraceNext, vm.step)

        vm.startAddRoom()
        vm.startDrawWalls()
        vm.square(500.0, 0.0)
        vm.drawWallsClose()
        vm.finishDrawWalls(RoomType.Cucina, 270.0, 30.0)
        assertEquals(TutorialStep.TraceDone, vm.step)

        // "Fine": la guida non ricompare e parte il tutorial generale, mai fatto.
        vm.finishTutorial()
        assertTrue(TipStore.TRACING_GUIDE_DONE in tips.seen)
        assertEquals(TutorialCourse.Main, vm.state.value.tutorial!!.course)
        assertEquals(TutorialStep.Intro, vm.step)
    }

    @Test
    fun `Salta passo prepara la pianta per il passo dopo`() {
        val vm = emptyProject()
        vm.startTracing()
        vm.loadPlan()
        vm.nextTutorialStep() // salta A
        vm.nextTutorialStep() // salta B
        vm.nextTutorialStep() // salta la misura
        assertEquals(TutorialStep.TraceFirstCorner, vm.step)
        assertNull(vm.state.value.underlayTool)
        assertNotNull(vm.state.value.wallDraw)
    }

    @Test
    fun `Indietro senza piantina chiude la guida ma la ripropone la volta dopo`() {
        val tips = MemoryTips()
        val vm = emptyProject(tips)
        vm.startTracing()
        vm.endTracing()
        assertNull(vm.state.value.tutorial)
        assertEquals(CreationStep.PickShape(cancellable = false), vm.state.value.creation)
        assertFalse(TipStore.TRACING_GUIDE_DONE in tips.seen)
        vm.startTracing()
        assertEquals(TutorialStep.TraceLoad, vm.step)
    }

    @Test
    fun `Esci dal disegno sulla pianta vuota chiude la guida`() {
        val vm = emptyProject()
        vm.startTracing()
        vm.loadPlan()
        vm.setUnderlayTool(null)
        vm.cancelDrawWalls()
        assertNotNull(vm.state.value.creation)
        assertNull(vm.state.value.tutorial)
    }

    @Test
    fun `guida gia fatta - ricalca mostra solo il suggerimento`() {
        val vm = emptyProject(MemoryTips(setOf(TipStore.TRACING_GUIDE_DONE)))
        vm.startTracing()
        assertNull(vm.state.value.tutorial)
        assertNotNull(vm.state.value.tip)
    }

    @Test
    fun `dal menu con la piantina gia caricata si parte dalla taratura`() {
        val vm = emptyProject(MemoryTips(setOf(TipStore.TRACING_GUIDE_DONE, TipStore.TUTORIAL_DONE)))
        vm.startTracing()
        vm.loadPlan()
        vm.setUnderlayTool(null)
        vm.square(0.0, 0.0)
        vm.drawWallsClose()
        vm.finishDrawWalls(RoomType.Soggiorno, 270.0, 30.0)
        vm.startTracingGuide()
        assertEquals(TutorialStep.TraceCalibA, vm.step)
        assertTrue(vm.state.value.underlayTool is UnderlayTool.Calibrate)
        vm.finishTutorial(completed = false)
        assertNull(vm.state.value.tutorial, "tutorial generale già fatto: non riparte")
    }
}
