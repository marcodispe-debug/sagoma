package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Muro per muro" scritto: freccia (verso dove va) e angolo tra i due muri, con anteprima. */
class WallDrawArrowTest {
    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan())
        override fun save(building: Building) {}
    })

    private fun near(expected: Vec2, actual: Vec2?) =
        assertTrue(actual != null && expected.distanceTo(actual) < 1e-6, "atteso $expected, ottenuto $actual")

    @Test
    fun `verso destra 400 poi verso giu 300 con 70 gradi`() {
        val vm = vm()
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(0.0, 0.0))
        val start = vm.state.value.wallDraw!!.points.single()
        assertTrue(vm.drawWallsTyped(400.0, WallArrow.RIGHT))
        near(start + Vec2(400.0, 0.0), vm.state.value.wallDraw!!.points.last())
        assertTrue(vm.drawWallsTyped(300.0, WallArrow.DOWN, 70.0))
        val a = Math.toRadians(70.0)
        near(start + Vec2(400.0 - 300.0 * cos(a), 300.0 * sin(a)), vm.state.value.wallDraw!!.points.last())
    }

    @Test
    fun `rettangolo con le sole frecce si chiude e diventa una stanza`() {
        val vm = vm()
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(0.0, 0.0))
        assertTrue(vm.drawWallsTyped(400.0, WallArrow.RIGHT))
        assertTrue(vm.drawWallsTyped(300.0, WallArrow.DOWN))
        assertTrue(vm.drawWallsTyped(400.0, WallArrow.LEFT))
        vm.drawWallsClose()
        vm.finishDrawWalls(RoomType.Camera, 270.0, Room.WALL_THICKNESS)
        val room = vm.state.value.plan.rooms.single()
        assertEquals(4, room.wallCount)
    }

    @Test
    fun `freccia e angolo che non danno un muro non aggiungono niente`() {
        val vm = vm()
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(0.0, 0.0))
        vm.drawWallsTyped(400.0, WallArrow.RIGHT)
        val before = vm.state.value.wallDraw!!.points
        assertFalse(vm.drawWallsTyped(300.0, WallArrow.LEFT)) // indietro sul muro precedente
        assertFalse(vm.drawWallsTyped(300.0, WallArrow.RIGHT, 70.0)) // dritto: da che parte girare?
        assertFalse(vm.drawWallsTyped(300.0, WallArrow.DOWN, 180.0)) // fuori da 1–179°
        assertEquals(before, vm.state.value.wallDraw!!.points)
    }

    @Test
    fun `l'anteprima mostra dove finira il muro prima di aggiungerlo`() {
        val vm = vm()
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(0.0, 0.0))
        vm.drawWallsTyped(400.0, WallArrow.RIGHT)
        val last = vm.state.value.wallDraw!!.points.last()
        vm.drawWallsTypedPreview(300.0, WallArrow.DOWN, 90.0)
        near(last + Vec2(0.0, 300.0), vm.state.value.wallDraw!!.cursor)
        assertEquals(2, vm.state.value.wallDraw!!.points.size) // solo anteprima
        // Aggiungendo, il muro finisce proprio dove mostrava l'anteprima.
        vm.drawWallsTyped(300.0, WallArrow.DOWN, 90.0)
        near(last + Vec2(0.0, 300.0), vm.state.value.wallDraw!!.points.last())
    }

    @Test
    fun `senza freccia resta il comportamento di prima - verso il puntatore`() {
        val vm = vm()
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(0.0, 0.0))
        val start = vm.state.value.wallDraw!!.points.single()
        vm.drawWallsCursor(start + Vec2(0.0, -500.0))
        assertTrue(vm.drawWallsTyped(250.0))
        near(start + Vec2(0.0, -250.0), vm.state.value.wallDraw!!.points.last())
    }
}
