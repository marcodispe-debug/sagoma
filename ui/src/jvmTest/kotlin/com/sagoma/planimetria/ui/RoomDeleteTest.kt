package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.CreationStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L'ultima stanza di un piano si può eliminare: i piani di sopra restano vuoti, il piano terra riparte dalla scelta iniziale. */
class RoomDeleteTest {
    private fun room(id: Long, dx: Double = 0.0) =
        Room(id, "Stanza $id", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(dx, 0.0) })

    private fun vm(vararg rooms: Room): EditorViewModel {
        val b = Building.single(FloorPlan(rooms.toList()), autoLevel = true)
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun EditorViewModel.deleteWithConfirm(id: Long) {
        requestDeleteRoom(id)
        assertEquals(id, state.value.confirmDeleteRoomId, "la richiesta di eliminazione non deve essere bloccata")
        deleteRoom(id)
    }

    @Test
    fun `piano superiore, l'unica stanza si elimina e il piano resta vuoto`() {
        val vm = vm(room(1))
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        assertEquals(1, vm.state.value.building.current)
        assertEquals(1, vm.state.value.plan.rooms.size)
        vm.deleteWithConfirm(vm.state.value.plan.rooms.single().id)
        val s = vm.state.value
        assertTrue(s.plan.rooms.isEmpty())
        assertEquals(2, s.fullBuilding.floors.size) // il piano c'è ancora
        assertEquals(1, s.building.current)
        assertNull(s.creation) // nessuna stanza sostitutiva e nessuna scelta obbligatoria
        assertNull(s.confirmDeleteRoomId)
        assertEquals(1, s.fullBuilding.floors[0].plan.rooms.size) // il piano terra non si tocca
    }

    @Test
    fun `piano terra, l'unica stanza si elimina e si riparte dalla scelta iniziale`() {
        val vm = vm(room(1))
        assertNull(vm.state.value.creation)
        vm.deleteWithConfirm(1)
        val s = vm.state.value
        assertTrue(s.plan.rooms.isEmpty())
        // Come all'avvio di un progetto vuoto: scelta della forma, non annullabile.
        assertEquals(CreationStep.PickShape(cancellable = false), s.creation)
        // E da lì si può creare di nuovo la stanza iniziale.
        vm.pickShape(RoomShape.Rectangle)
        vm.createRoom(RoomType.Camera, ShapeDimensions(300.0, 300.0), 270.0)
        assertEquals(1, vm.state.value.plan.rooms.size)
        assertNull(vm.state.value.creation)
    }

    @Test
    fun `piano terra con piu stanze, eliminarne una non cambia il flusso`() {
        val vm = vm(room(1), room(2, dx = 500.0))
        vm.deleteWithConfirm(1)
        assertEquals(listOf(2L), vm.state.value.plan.rooms.map { it.id })
        assertNull(vm.state.value.creation)
    }

    @Test
    fun `annullando l'eliminazione del piano terra torna la stanza e sparisce la scelta`() {
        val vm = vm(room(1))
        vm.deleteWithConfirm(1)
        assertNotNull(vm.state.value.creation)
        vm.undo()
        assertEquals(listOf(1L), vm.state.value.plan.rooms.map { it.id })
        assertNull(vm.state.value.creation)
    }

    @Test
    fun `il piano terra svuotato con piani sopra riparte comunque dalla scelta`() {
        val vm = vm(room(1))
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        vm.selectFloor(0)
        vm.deleteWithConfirm(1)
        assertTrue(vm.state.value.plan.rooms.isEmpty())
        assertEquals(CreationStep.PickShape(cancellable = false), vm.state.value.creation)
        assertEquals(1, vm.state.value.fullBuilding.floors[1].plan.rooms.size)
    }
}
