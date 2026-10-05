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
import kotlin.test.assertTrue

/** Striscia LED a parete: posizionamento, limiti del muro, lunghezza e quota, spostamento, copia, rimozione. */
class WallLedStripEditorTest {
    // Stanza 600 × 300: muro 0 in alto (600), muro 1 a destra (300), muro 2 in basso (600), muro 3 a sinistra (300).
    private val room = Room(1, "Sala", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 300.0))

    private fun vm(): EditorViewModel {
        val b = Building.single(FloorPlan(listOf(room)))
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun EditorViewModel.fixtures() = state.value.plan.room(1)!!.fixtures

    private fun EditorViewModel.place(wall: Int, at: Vec2): Fixture {
        startPlacingFixture(FixtureKind.WallLedStrip)
        onTap(DragTarget.Wall(1, wall), at)
        return fixtures().maxByOrNull { it.id }!!
    }

    @Test
    fun `si mette sul muro con lunghezza 200 e quota 250 e sta dentro il muro`() {
        val vm = vm()
        val f = vm.place(0, Vec2(300.0, 2.0))
        assertEquals(FixtureKind.WallLedStrip, f.kind)
        assertEquals(Mount.Wall, f.kind.mount)
        assertEquals(200.0, f.length, 0.0)
        assertEquals(250.0, f.elevation, 0.0)
        assertEquals(300.0, f.position, 1e-9)
        // Vicino all'inizio e alla fine del muro la striscia resta tutta dentro (posizione tra 100 e 500).
        assertEquals(100.0, vm.place(0, Vec2(5.0, 2.0)).position, 1e-9)
        assertEquals(500.0, vm.place(0, Vec2(595.0, 2.0)).position, 1e-9)
    }

    @Test
    fun `lunghezza e quota si modificano`() {
        val vm = vm()
        val f = vm.place(0, Vec2(300.0, 2.0))
        vm.updateFixture(1, f.copy(length = 120.0, elevation = 235.0))
        val g = vm.fixtures().single()
        assertEquals(120.0, g.length, 0.0)
        assertEquals(235.0, g.elevation, 0.0)
        assertEquals(0.0, g.rotation, 0.0) // nessuna rotazione indipendente
    }

    @Test
    fun `il trascinamento scorre lungo il muro e resta dentro`() {
        val vm = vm()
        val f = vm.place(0, Vec2(300.0, 2.0))
        vm.beginDrag(DragTarget.Fixture(1, f.id), Vec2(300.0, 0.0))
        vm.dragBy(DragTarget.Fixture(1, f.id), Vec2(-150.0, 0.0))
        vm.endDrag()
        assertEquals(150.0, vm.fixtures().single().position, 1e-9)
        // Oltre l'inizio del muro: si ferma a metà lunghezza dall'estremo (100).
        vm.beginDrag(DragTarget.Fixture(1, f.id), Vec2(150.0, 0.0))
        vm.dragBy(DragTarget.Fixture(1, f.id), Vec2(-100.0, 0.0))
        vm.endDrag()
        val moved = vm.fixtures().single()
        val len = room.wallLength(moved.wallIndex)
        assertTrue(moved.position >= 100.0 && moved.position <= len - 100.0, "posizione ${moved.position} sul muro ${moved.wallIndex} lungo $len")
    }

    @Test
    fun `passa al muro vicino se e abbastanza lungo e resta dentro quel muro`() {
        val vm = vm()
        val f = vm.place(0, Vec2(595.0, 2.0)) // in fondo al muro in alto: posizione 500, cioè il punto (500, 0)
        assertEquals(500.0, f.position, 1e-9)
        // Il punto (500, 0) più (95, 150) è (595, 150): vicino al muro destro (lungo 300, più della striscia da 200).
        vm.beginDrag(DragTarget.Fixture(1, f.id), Vec2(500.0, 0.0))
        vm.dragBy(DragTarget.Fixture(1, f.id), Vec2(95.0, 150.0))
        vm.endDrag()
        val onRight = vm.fixtures().single { it.id == f.id }
        assertEquals(1, onRight.wallIndex)
        assertEquals(150.0, onRight.position, 1e-6) // 150 dall'alto
        assertTrue(onRight.position in 100.0..200.0)
        assertEquals(250.0, onRight.elevation, 0.0) // la quota non cambia passando di muro
        assertEquals(200.0, onRight.length, 0.0)
    }

    @Test
    fun `una striscia piu lunga del muro non passa su quel muro`() {
        val vm = vm()
        val f = vm.place(0, Vec2(300.0, 2.0))
        vm.updateFixture(1, f.copy(length = 350.0)) // più lunga dei muri da 300
        vm.beginDrag(DragTarget.Fixture(1, f.id), Vec2(300.0, 0.0))
        vm.dragBy(DragTarget.Fixture(1, f.id), Vec2(300.0, 150.0))
        vm.endDrag()
        assertEquals(0, vm.fixtures().single().wallIndex) // il muro destro (300) è troppo corto
    }

    @Test
    fun `si copia, si cancella e sparisce con il muro`() {
        val vm = vm()
        val f = vm.place(0, Vec2(300.0, 2.0))
        vm.selectFixture(1, f.id)
        assertTrue(vm.duplicateSelected())
        val all = vm.fixtures()
        assertEquals(2, all.size)
        assertTrue(all.all { it.kind == FixtureKind.WallLedStrip && it.length == 200.0 && it.elevation == 250.0 })
        vm.removeFixture(1, all.last().id)
        assertEquals(listOf(f.id), vm.fixtures().map { it.id })
        vm.setWallRemoved(1, 0, true)
        assertTrue(vm.fixtures().isEmpty())
    }
}
