package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.SpotAim
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Orientamento grafico del faretto a parete: un solo trascinamento = una sola operazione di annulla/ripristina. */
class WallSpotAimEditorTest {
    private val room = Room(1, "Sala", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))

    private fun vm(): EditorViewModel {
        val b = Building.single(FloorPlan(listOf(room)))
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun EditorViewModel.spot() = state.value.plan.room(1)!!.fixtures.single { it.kind == FixtureKind.WallSpot }

    private fun EditorViewModel.place(): Fixture {
        startPlacingFixture(FixtureKind.WallSpot)
        onTap(DragTarget.Wall(1, 0), Vec2(150.0, 2.0))
        return spot()
    }

    /** Raggio dalla posizione `from` verso la maniglia che corrisponde a (yaw, tilt). */
    private fun rayTo(f: Fixture, yaw: Double, tilt: Double, from: Vec3 = Vec3(200.0, 250.0, 900.0)): Pair<Vec3, Vec3> {
        val n = SpotAim.normal(room, f)
        val p = SpotAim.position(room, f) + SpotAim.direction(n, yaw, tilt) * SpotAim.HANDLE_LENGTH
        return from to (p - from).normalized()
    }

    /** Trascina la maniglia fino a (yaw, tilt) a piccoli passi, come fa il dito (la maniglia resta sotto il dito). */
    private fun EditorViewModel.dragTo(target: DragTarget.SpotAim, f: Fixture, yaw: Double, tilt: Double) {
        val from = spot()
        val steps = 20
        for (i in 1..steps) {
            val y = from.aimYaw + (yaw - from.aimYaw) * i / steps
            val t = from.aimTilt + (tilt - from.aimTilt) * i / steps
            val (o, d) = rayTo(f, y, t)
            dragAim(target, o, d)
        }
    }

    @Test
    fun `un faretto nuovo ha yaw 0 e inclinazione 45`() {
        val f = vm().place()
        assertEquals(0.0, f.aimYaw, 0.0)
        assertEquals(45.0, f.aimTilt, 0.0)
    }

    @Test
    fun `dragAim cambia solo l orientamento`() {
        val vm = vm()
        val f = vm.place()
        val target = DragTarget.SpotAim(1, f.id)
        vm.beginDrag(target, Vec2(150.0, 0.0))
        assertNotNull(vm.state.value.aimDrag)
        vm.dragTo(target, f, 130.0, 20.0)
        val g = vm.spot()
        assertEquals(130.0, g.aimYaw, 1e-6)
        assertEquals(20.0, g.aimTilt, 1e-6)
        assertEquals(f.copy(aimYaw = g.aimYaw, aimTilt = g.aimTilt), g)
        assertEquals(f.position, g.position, 0.0)
        assertEquals(f.wallIndex, g.wallIndex)
        assertEquals(f.elevation, g.elevation, 0.0)
        assertEquals(room.copy(fixtures = listOf(g)), vm.state.value.plan.room(1))
        vm.endDrag()
        assertNull(vm.state.value.aimDrag)
        assertEquals(g, vm.spot())
    }

    @Test
    fun `un trascinamento intero e una sola operazione annulla e ripristina`() {
        val vm = vm()
        val f = vm.place()
        val target = DragTarget.SpotAim(1, f.id)
        vm.beginDrag(target, Vec2(150.0, 0.0))
        for (yaw in listOf(20.0, 100.0, 200.0, 300.0)) vm.dragTo(target, f, yaw, 10.0)
        vm.endDrag()
        val after = vm.spot()
        assertEquals(300.0, after.aimYaw, 1e-6)
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(0.0, vm.spot().aimYaw, 0.0)
        assertEquals(45.0, vm.spot().aimTilt, 0.0)
        vm.redo()
        assertEquals(after, vm.spot())
    }

    @Test
    fun `un trascinamento senza movimento non lascia traccia nella cronologia`() {
        val vm = vm()
        val f = vm.place()
        val target = DragTarget.SpotAim(1, f.id)
        vm.undo() // annulla il posizionamento
        assertTrue(vm.state.value.plan.room(1)!!.fixtures.isEmpty())
        vm.redo()
        vm.beginDrag(target, Vec2(150.0, 0.0))
        vm.endDrag()
        vm.undo()
        assertTrue(vm.state.value.plan.room(1)!!.fixtures.isEmpty())
    }
}
