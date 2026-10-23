package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.GroupItem
import com.sagoma.planimetria.geometry.GroupOps
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.geometry.arc
import com.sagoma.planimetria.geometry.middle
import com.sagoma.planimetria.geometry.pieces
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Muro singolo curvo: la curva si sceglie con il pallino a metà o scrivendo la freccia, come per i muri delle stanze. */
class CurvedFreeWallTest {
    // Soggiorno 600×400 diviso da un muro singolo verticale a x = 300, da y = 0 a y = 400.
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 400.0))
    private val wall = FreeWall(1, Vec2(300.0, 0.0), Vec2(300.0, 400.0), thickness = 10.0)

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(living), freeWalls = listOf(wall)))
        override fun save(building: Building) {}
    })

    private fun EditorViewModel.wall() = state.value.plan.freeWall(1)!!

    @Test
    fun `l'arco passa dalle estremità e sporge a metà`() {
        val w = wall.copy(bulge = 80.0)
        val arc = w.arc()!!
        assertEquals(0.0, arc.at(-1.0).distanceTo(w.start), 1e-6)
        assertEquals(0.0, arc.at(1.0).distanceTo(w.end), 1e-6)
        // Freccia positiva verso −perp(end − start): qui verso x crescenti.
        assertEquals(0.0, w.middle().distanceTo(Vec2(380.0, 200.0)), 1e-6)
        val pieces = w.pieces()
        assertTrue(pieces.size > 10)
        assertEquals(w.start, pieces.first().start)
        assertEquals(w.end, pieces.last().end)
        for (p in pieces) assertTrue(arc.distanceTo(p.start) < 0.2)
        // Il contorno è la striscia attorno all'arco: niente punte.
        for (p in Structure.outline(w)) assertTrue(arc.distanceTo(p) in 4.0..6.0, "punto fuori posto: $p")
        assertTrue(Structure.contains(w, Vec2(380.0, 200.0)))
        assertTrue(!Structure.contains(w, Vec2(300.0, 200.0)))
    }

    @Test
    fun `porta sul muro singolo curvo con il suo pezzo dritto, salvataggio, specchio e 3D`() {
        val door = Opening.default(1, OpeningKind.Door, 0, 200.0).copy(width = 80.0)
        val w = wall.copy(bulge = 80.0, openings = listOf(door))
        val piece = w.pieces().single { it.openings.isNotEmpty() }
        assertEquals(80.0, piece.length, 1e-6)
        val (a, b) = Openings.span(w.host, door)
        assertTrue(w.arc()!!.distanceTo(a) < 0.5 && w.arc()!!.distanceTo(b) < 0.5)
        // Due tratti pieni, uno per parte.
        assertEquals(2, Structure.solidOutlines(w).size)
        val plan = FloorPlan(listOf(living), freeWalls = listOf(w))
        assertEquals(80.0, PlanJson.decode(PlanJson.encode(plan)).freeWalls.single().bulge, 1e-9)
        Scene3D.build(plan, null, null, ceilings = true)
        // Lo specchio tiene la curva dalla parte giusta.
        val m = GroupOps.mirror(plan, setOf(GroupItem.FreeWallItem(1)), Vec2(300.0, 200.0), horizontal = true).freeWalls.single()
        assertEquals(0.0, m.middle().distanceTo(Vec2(220.0, 200.0)), 1e-6)
    }

    @Test
    fun `freccia scritta, pallino e raddrizza`() {
        val vm = vm()
        vm.setFreeWallBulge(1, 60.0)
        assertEquals(60.0, vm.wall().bulge, 1e-9)
        // Al massimo mezzo cerchio.
        vm.setFreeWallBulge(1, 900.0)
        assertEquals(200.0, vm.wall().bulge, 1e-9)
        vm.setFreeWallBulge(1, 0.0)
        assertNull(vm.wall().arc())
        vm.setCamera(Camera(1f, 0f, 0f))
        vm.selectFreeWall(1)
        val handle = freeWallBulgeHandle(vm.wall(), 16f)
        assertTrue(handle.x > 300.0)
        val target = DragTarget.FreeWallBulge(1)
        vm.beginDrag(target, handle)
        vm.dragBy(target, Vec2(-50.0, 3.0))
        vm.endDrag()
        assertEquals(-50.0, vm.wall().bulge, 1e-9)
        vm.undo()
        assertNull(vm.wall().arc())
    }
}
