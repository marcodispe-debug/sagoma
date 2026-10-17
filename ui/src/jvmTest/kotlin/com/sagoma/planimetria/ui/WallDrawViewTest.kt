package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Muro per muro": la vista inquadra la stanza in costruzione sotto la scheda, non le stanze già fatte. */
class WallDrawViewTest {
    private val existing = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(rooms = listOf(existing)))
        override fun save(building: Building) {}
    }).apply { setViewport(1000f, 2000f, 24f) }

    /** Centro dello spazio libero sotto la scheda alta [top], in pixel. */
    private fun assertCentered(vm: EditorViewModel, world: Vec2, top: Float) {
        val cam = vm.state.value.camera
        assertTrue(abs(cam.toScreenX(world.x) - 500f) < 1f, "x ${cam.toScreenX(world.x)}")
        assertTrue(abs(cam.toScreenY(world.y) - (top + (2000f - top) / 2)) < 1f, "y ${cam.toScreenY(world.y)}")
    }

    @Test
    fun `la stanza in costruzione va al centro dello spazio sotto la scheda, senza ingrandire`() {
        val vm = vm()
        vm.setCoveredEdges(600f, 0f)
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(2000.0, 2000.0)) // lontano dal soggiorno
        vm.drawWallsTyped(400.0, WallArrow.RIGHT)
        vm.drawWallsTyped(300.0, WallArrow.DOWN)
        val scaleBefore = vm.state.value.camera.scale
        val cam = assertNotNull(vm.wallDraftCamera(includeCursor = false))
        vm.setCamera(cam)
        assertTrue(cam.scale <= scaleBefore)
        val pts = vm.state.value.wallDraw!!.points
        val center = Vec2((pts.minOf { it.x } + pts.maxOf { it.x }) / 2, (pts.minOf { it.y } + pts.maxOf { it.y }) / 2)
        assertCentered(vm, center, 600f)
    }

    @Test
    fun `se la stanza in costruzione non ci sta lo zoom diminuisce`() {
        val vm = vm()
        vm.setCoveredEdges(600f, 0f)
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(0.0, 0.0))
        vm.drawWallsTyped(20000.0, WallArrow.RIGHT) // 200 m: non ci sta con lo zoom attuale
        val cam = assertNotNull(vm.wallDraftCamera(includeCursor = false))
        assertTrue(cam.toScreenX(vm.state.value.wallDraw!!.points.first().x) >= 0f)
        assertTrue(cam.toScreenX(vm.state.value.wallDraw!!.points.last().x) <= 1000f)
        assertTrue(cam.scale < vm.state.value.camera.scale)
    }

    @Test
    fun `scheda o tastiera che cambiano non riportano la vista sulle altre stanze`() {
        val vm = vm()
        vm.setCoveredEdges(600f, 0f)
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(3000.0, 3000.0))
        vm.drawWallsTyped(400.0, WallArrow.RIGHT)
        val mid = Vec2((vm.state.value.wallDraw!!.points.first().x + vm.state.value.wallDraw!!.points.last().x) / 2, vm.state.value.wallDraw!!.points.last().y)
        vm.setCoveredEdges(700f, 0f) // la scheda si allunga
        assertCentered(vm, mid, 700f)
        vm.setViewport(1000f, 1200f, 24f) // tastiera aperta
        val cam = vm.state.value.camera
        assertTrue(abs(cam.toScreenY(mid.y) - (700f + (1200f - 700f) / 2)) < 1f)
    }

    @Test
    fun `senza disegno in corso nessuna inquadratura speciale`() {
        assertNull(vm().wallDraftCamera(includeCursor = false))
        val vm = vm()
        vm.startDrawWalls()
        assertNull(vm.wallDraftCamera(includeCursor = false)) // nessun angolo ancora: resta la vista di prima
        assertEquals(1, vm.state.value.plan.rooms.size)
    }
}
