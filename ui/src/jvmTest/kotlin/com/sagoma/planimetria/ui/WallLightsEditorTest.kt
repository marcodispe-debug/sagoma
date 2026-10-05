package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plafoniera e faretto a parete: posizionamento, quota, spostamento, copia, cancellazione, rimozione del muro. */
class WallLightsEditorTest {
    // Stanza 400 × 300: muro 0 in alto (y = 0, verso destra), muro 1 a destra (x = 400), muro 2 in basso, muro 3 a sinistra.
    private val room = Room(1, "Sala", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
    private val kinds = listOf(FixtureKind.WallLight, FixtureKind.WallSpot)

    private fun vm(fixtures: List<Fixture> = emptyList()): EditorViewModel {
        val b = Building.single(FloorPlan(listOf(room.copy(fixtures = fixtures))))
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun EditorViewModel.fixtures() = state.value.plan.room(1)!!.fixtures

    private fun EditorViewModel.place(kind: FixtureKind, wall: Int, at: Vec2): Fixture {
        startPlacingFixture(kind)
        onTap(DragTarget.Wall(1, wall), at)
        return fixtures().maxByOrNull { it.id }!!
    }

    @Test
    fun `si mettono sul muro toccato, a 220 cm da terra`() {
        for (kind in kinds) {
            val vm = vm()
            val f = vm.place(kind, 0, Vec2(150.0, 2.0))
            assertEquals(kind, f.kind)
            assertEquals(Mount.Wall, f.kind.mount)
            assertEquals(0, f.wallIndex)
            assertEquals(150.0, f.position, 1e-9)
            assertEquals(220.0, f.elevation, 0.0)
            assertNull(vm.state.value.pendingFixture)
            // Su un altro muro: stessa cosa, con la posizione lungo quel muro (muro 1: x = 400, dall'alto verso il basso).
            val g = vm.place(kind, 1, Vec2(398.0, 90.0))
            assertEquals(1, g.wallIndex)
            assertEquals(90.0, g.position, 1e-9)
        }
    }

    @Test
    fun `la quota si modifica`() {
        val vm = vm()
        val f = vm.place(FixtureKind.WallSpot, 0, Vec2(150.0, 2.0))
        vm.updateFixture(1, f.copy(elevation = 180.0))
        assertEquals(180.0, vm.fixtures().single().elevation, 0.0)
        // E resta sul muro.
        assertEquals(0, vm.fixtures().single().wallIndex)
    }

    @Test
    fun `il trascinamento scorre lungo il muro e resta entro la sua lunghezza`() {
        val vm = vm()
        val f = vm.place(FixtureKind.WallLight, 0, Vec2(150.0, 2.0))
        vm.beginDrag(DragTarget.Fixture(1, f.id), Vec2(150.0, 0.0))
        vm.dragBy(DragTarget.Fixture(1, f.id), Vec2(100.0, 0.0))
        vm.endDrag()
        assertEquals(250.0, vm.fixtures().single().position, 1e-9)
        assertEquals(0, vm.fixtures().single().wallIndex)
        // Oltre la fine del muro: si ferma entro la lunghezza (o passa al muro dopo, ma mai fuori da un muro).
        vm.beginDrag(DragTarget.Fixture(1, f.id), Vec2(250.0, 0.0))
        vm.dragBy(DragTarget.Fixture(1, f.id), Vec2(900.0, 0.0))
        vm.endDrag()
        val moved = vm.fixtures().single()
        val len = vm.state.value.plan.room(1)!!.wallLength(moved.wallIndex)
        assertTrue(moved.position in 0.0..len, "posizione ${moved.position} fuori dal muro ${moved.wallIndex} lungo $len")
    }

    @Test
    fun `passa da un muro all'altro`() {
        for (kind in kinds) {
            val vm = vm()
            val f = vm.place(kind, 0, Vec2(150.0, 2.0))
            // Dal punto (150, 0) al punto (450, 120): vicino al muro di destra (x = 400), a 120 cm dall'alto.
            vm.beginDrag(DragTarget.Fixture(1, f.id), Vec2(150.0, 0.0))
            vm.dragBy(DragTarget.Fixture(1, f.id), Vec2(300.0, 120.0))
            vm.endDrag()
            val moved = vm.fixtures().single()
            assertEquals(1, moved.wallIndex)
            assertEquals(120.0, moved.position, 1e-6)
            assertEquals(220.0, moved.elevation, 0.0) // la quota non cambia passando di muro
        }
    }

    @Test
    fun `si copiano e si cancellano come gli altri impianti a muro`() {
        for (kind in kinds) {
            val vm = vm()
            val f = vm.place(kind, 0, Vec2(150.0, 2.0))
            vm.selectFixture(1, f.id)
            assertTrue(vm.duplicateSelected())
            val all = vm.fixtures()
            assertEquals(2, all.size)
            assertTrue(all.all { it.kind == kind && it.kind.mount == Mount.Wall && it.elevation == 220.0 })
            assertEquals(2, all.map { it.id }.toSet().size)
            vm.removeFixture(1, all.last().id)
            assertEquals(listOf(f.id), vm.fixtures().map { it.id })
            vm.removeFixture(1, f.id)
            assertTrue(vm.fixtures().isEmpty())
        }
    }

    @Test
    fun `eliminando il muro spariscono solo le luci di quel muro`() {
        val vm = vm()
        vm.place(FixtureKind.WallLight, 0, Vec2(150.0, 2.0))
        vm.place(FixtureKind.WallSpot, 0, Vec2(300.0, 2.0))
        val other = vm.place(FixtureKind.WallSpot, 1, Vec2(398.0, 90.0))
        vm.setWallRemoved(1, 0, true)
        assertEquals(listOf(other.id), vm.fixtures().map { it.id })
    }
}
