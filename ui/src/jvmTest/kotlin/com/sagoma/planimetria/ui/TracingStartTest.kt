package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.CreationStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.UnderlayTool
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** "Ricalca una piantina" al posto della prima stanza: sfondo, taratura, poi muro per muro; senza sfondo si torna alla scelta. */
class TracingStartTest {
    private fun emptyProject() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(), autoLevel = true)
        override fun save(building: Building) {}
    })

    private fun EditorViewModel.loadPlan() {
        // Come la finestra dello sfondo dopo aver letto il file: immagine di 1000 px, poi la taratura.
        setUnderlay(Underlay("sfondo.jpg", 1000, 600, cmPerPx = 1.2))
        setUnderlayTool(UnderlayTool.Calibrate())
    }

    @Test
    fun `progetto nuovo, ricalco, taratura e poi muro per muro`() {
        val vm = emptyProject()
        assertEquals(CreationStep.PickShape(cancellable = false), vm.state.value.creation)
        vm.startTracing()
        assertNull(vm.state.value.creation)
        assertNotNull(vm.state.value.tracing)

        vm.loadPlan()
        vm.underlayTap(Vec2(0.0, 0.0))
        vm.underlayTap(Vec2(300.0, 0.0))
        vm.calibrateUnderlay(450.0)

        val s = vm.state.value
        assertNull(s.tracing)
        assertNull(s.underlayTool)
        assertNull(s.creation)
        assertNotNull(s.wallDraw, "dopo la taratura si ripassano i muri")
        assertEquals(1.8, s.plan.underlay!!.cmPerPx, 1e-9)
    }

    @Test
    fun `chiudere senza caricare nulla riporta alla scelta della prima stanza`() {
        val vm = emptyProject()
        vm.startTracing()
        vm.endTracing()
        val s = vm.state.value
        assertNull(s.tracing)
        assertEquals(CreationStep.PickShape(cancellable = false), s.creation)
        assertNull(s.wallDraw)
    }

    @Test
    fun `Fine senza tarare passa comunque al disegno dei muri`() {
        val vm = emptyProject()
        vm.startTracing()
        vm.loadPlan()
        vm.setUnderlayTool(null)
        val s = vm.state.value
        assertNull(s.tracing)
        assertNotNull(s.wallDraw)
        assertNotNull(s.plan.underlay)
    }

    @Test
    fun `senza scelta della stanza aperta il ricalco non parte`() {
        val vm = emptyProject()
        vm.startTracing()
        vm.startTracing() // secondo tocco: la scelta è già chiusa
        vm.endTracing()
        assertEquals(CreationStep.PickShape(cancellable = false), vm.state.value.creation)
        assertNull(vm.state.value.tracing)
    }
}
