package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Un muro toccato da dentro la stanza o da fuori: la scheda Finitura mostra quel lato. */
class WallSideTapTest {
    // Sala 400 × 300 (muro 1 a destra, x = 400) e cucina accostata a destra, da x = 400 a 700.
    private val sala = Room(1, "Sala", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
    private val cucina = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })

    private fun vm(vararg rooms: Room): EditorViewModel {
        val b = Building.single(FloorPlan(rooms.toList()))
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    @Test
    fun `toccato da dentro e il lato della stanza, da fuori e la facciata`() {
        val vm = vm(sala)
        vm.onTap(DragTarget.Wall(1, 0), Vec2(150.0, 3.0))
        assertEquals(Selection.Wall(1, 0), vm.state.value.selection)
        assertFalse(vm.state.value.wallExterior)
        vm.onTap(DragTarget.Wall(1, 0), Vec2(150.0, -3.0))
        assertEquals(Selection.Wall(1, 0), vm.state.value.selection)
        assertTrue(vm.state.value.wallExterior)
        // Si può passare all'altro lato dalla scheda; scegliendo un altro muro si riparte dal lato della stanza.
        vm.setWallSide(false)
        assertFalse(vm.state.value.wallExterior)
        vm.onTap(DragTarget.Wall(1, 1), Vec2(403.0, 100.0))
        assertTrue(vm.state.value.wallExterior)
        vm.selectWall(1, 2)
        assertFalse(vm.state.value.wallExterior)
    }

    @Test
    fun `muro in comune toccato da fuori diventa il muro dell'altra stanza`() {
        val vm = vm(sala, cucina)
        // Metà del muro verso la cucina: è la parete della cucina, dal suo lato.
        vm.onTap(DragTarget.Wall(1, 1), Vec2(404.0, 100.0))
        val sel = vm.state.value.selection as Selection.Wall
        assertEquals(2, sel.roomId)
        assertFalse(vm.state.value.wallExterior)
        assertEquals(3, sel.index) // muro di sinistra della cucina
        // Dalla scheda si passa all'altra parte: la parete del soggiorno, e ritorno.
        assertEquals(Selection.Wall(1, 1), vm.wallBehind(2, 3))
        assertEquals(Selection.Wall(2, 3), vm.wallBehind(1, 1))
        assertEquals(null, vm.wallBehind(1, 0)) // di là c'è la facciata
    }

    @Test
    fun `muro curvo toccato dalla parte bombata e il lato di fuori`() {
        val vm = vm(sala)
        vm.setWallBulge(1, 0, 60.0) // muro in alto che si gonfia verso fuori (y < 0)
        vm.onTap(DragTarget.Wall(1, 0), Vec2(200.0, -63.0))
        assertTrue(vm.state.value.wallExterior)
        vm.onTap(DragTarget.Wall(1, 0), Vec2(200.0, -57.0))
        assertFalse(vm.state.value.wallExterior)
    }

    @Test
    fun `nel 3D si evidenzia solo il lato scelto`() {
        val plan = FloorPlan(listOf(sala))
        val pick = com.sagoma.planimetria.geometry.Pick.Wall(1, 0)
        val inside = Scene3D.build(plan, 1, pick, ceilings = false)
        val outside = Scene3D.build(plan, 1, pick, ceilings = false, selectedExterior = true)
        assertNotEquals(inside.opaque.toList(), outside.opaque.toList())
    }
}
