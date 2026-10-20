package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "◐ Piantina e disegno": trasparenza dal vivo della piantina (un solo passo di Annulla) e vista del disegno (nessuna modifica). */
class VisibilityTest {
    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(underlay = Underlay("sfondo.jpg", 1000, 600, opacity = 0.5)), autoLevel = true)
        override fun save(building: Building) {}
    })

    @Test
    fun `piantina, la trasparenza cambia mentre si trascina ed e' un solo passo di Annulla`() {
        val vm = vm()
        assertFalse(vm.state.value.canUndo)
        vm.previewUnderlayOpacity(0.4)
        assertEquals(0.4, vm.state.value.plan.underlay!!.opacity, 1e-9)
        vm.previewUnderlayOpacity(0.2)
        vm.previewUnderlayOpacity(0.9)
        assertEquals(0.9, vm.state.value.plan.underlay!!.opacity, 1e-9)
        vm.endUnderlayOpacity()
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(0.5, vm.state.value.plan.underlay!!.opacity, 1e-9)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `piantina, trascinare e tornare al valore di prima non lascia passi di Annulla`() {
        val vm = vm()
        vm.previewUnderlayOpacity(0.7)
        vm.previewUnderlayOpacity(0.5)
        vm.endUnderlayOpacity()
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `disegno nascosto o trasparente senza toccare la casa`() {
        val vm = vm()
        val before = vm.state.value.fullBuilding
        vm.setDrawingOpacity(0.3)
        vm.setDrawingVisible(false)
        val s = vm.state.value
        assertEquals(0.3, s.drawingOpacity, 1e-9)
        assertFalse(s.drawingVisible)
        assertEquals(before, s.fullBuilding)
        assertFalse(s.canUndo)
        vm.setDrawingOpacity(0.0) // il minimo resta visibile: per nasconderlo c'è l'interruttore
        assertEquals(0.1, vm.state.value.drawingOpacity, 1e-9)
    }
}
