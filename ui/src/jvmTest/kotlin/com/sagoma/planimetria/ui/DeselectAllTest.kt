package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tasto destro del mouse: deseleziona tutto (oggetto, stanza attiva, gruppo). */
class DeselectAllTest {
    private val house = Building.single(FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)))), autoLevel = true)

    private fun vm(): EditorViewModel = EditorViewModel(object : PlanStore {
        override fun load() = house
        override fun save(building: Building) {}
    })

    @Test
    fun `toglie muro selezionato e stanza attiva`() {
        val vm = vm()
        vm.selectWall(1, 0)
        assertTrue(vm.state.value.selection is Selection.Wall)
        vm.deselectAll()
        assertNull(vm.state.value.selection)
        assertNull(vm.state.value.focusedRoomId)
    }

    @Test
    fun `svuota il gruppo selezionato`() {
        val vm = vm()
        vm.selectAll()
        assertTrue(vm.state.value.multi.isNotEmpty())
        vm.deselectAll()
        assertEquals(emptySet(), vm.state.value.multi)
    }
}
