package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.PendingOpening
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
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

/** Porte e finestre sui muri singoli: si mettono, si spostano lungo il muro, si copiano, si salvano e vanno nel 3D. */
class FreeWallOpeningTest {
    // Soggiorno 600×400 diviso da un muro singolo verticale a x = 300, da y = 0 a y = 400.
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 400.0))
    private val wall = FreeWall(1, Vec2(300.0, 0.0), Vec2(300.0, 400.0), thickness = 10.0)
    private val host = FreeWall.hostId(1)

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(living), freeWalls = listOf(wall)))
        override fun save(building: Building) {}
    })

    private fun EditorViewModel.wall() = state.value.plan.freeWall(1)!!

    @Test
    fun `porta sul muro singolo toccato`() {
        val vm = vm()
        vm.startPlacingOpening(PendingOpening(OpeningKind.Door))
        vm.onTap(DragTarget.FreeWallBody(1), Vec2(302.0, 150.0))
        val o = vm.wall().openings.single()
        assertEquals(OpeningKind.Door, o.kind)
        assertEquals(0, o.wallIndex)
        assertEquals(150.0, o.position, 1e-9)
        assertEquals(Selection.Opening(host, o.id), vm.state.value.selection)
        // Nessuna stanza a fuoco: il muro singolo non è una stanza.
        assertNull(vm.state.value.focusedRoomId)
        // Il muro resta pieno solo fuori dalla porta (80 cm centrata a 150).
        assertEquals(listOf(0.0..110.0, 190.0..400.0), Structure.solidRanges(vm.wall()))
        // Si salva e si rilegge uguale; il 3D si costruisce con la porta.
        val b = vm.state.value.fullBuilding
        assertEquals(b, PlanJson.decodeBuilding(PlanJson.encode(b)))
        val scene = Scene3D.build(vm.state.value.plan, null, null, ceilings = true)
        assertTrue(scene.toString().isNotEmpty())
    }

    @Test
    fun `l'apertura si sposta lungo il muro e si modifica`() {
        val vm = vm()
        vm.startPlacingOpening(PendingOpening(OpeningKind.Window2))
        vm.onTap(DragTarget.FreeWallBody(1), Vec2(300.0, 200.0))
        val o = vm.wall().openings.single()
        vm.beginDrag(DragTarget.Opening(host, o.id), Vec2(300.0, 200.0))
        vm.dragBy(DragTarget.Opening(host, o.id), Vec2(40.0, 100.0))
        vm.endDrag()
        val moved = vm.wall().openings.single()
        // Resta sul muro singolo (non passa sul "ritorno" del muro) e segue il dito lungo il muro.
        assertEquals(0, moved.wallIndex)
        assertEquals(300.0, moved.position, 1e-9)
        vm.updateOpening(host, moved.copy(width = 150.0))
        assertEquals(150.0, vm.wall().openings.single().width, 1e-9)
        // Il muro trascinato si porta dietro la finestra.
        vm.beginDrag(DragTarget.FreeWallBody(1), Vec2(300.0, 50.0))
        vm.dragBy(DragTarget.FreeWallBody(1), Vec2(-100.0, 0.0))
        vm.endDrag()
        val (a, b) = Openings.span(vm.wall().host, vm.wall().openings.single())
        assertEquals(200.0, a.x, 1.0)
        assertEquals(200.0, b.x, 1.0)
    }

    @Test
    fun `duplica e rimuovi l'apertura`() {
        val vm = vm()
        vm.startPlacingOpening(PendingOpening(OpeningKind.Door))
        vm.onTap(DragTarget.FreeWallBody(1), Vec2(300.0, 100.0))
        assertTrue(vm.duplicateSelected())
        val ids = vm.wall().openings.map { it.id }
        assertEquals(2, ids.toSet().size)
        assertTrue(vm.deleteSelected())
        assertEquals(1, vm.wall().openings.size)
        vm.undo()
        assertEquals(2, vm.wall().openings.size)
    }

    @Test
    fun `gli impianti non vanno sui muri singoli`() {
        val vm = vm()
        vm.startPlacingFixture(com.sagoma.planimetria.model.FixtureKind.Outlet)
        vm.onTap(DragTarget.FreeWallBody(1), Vec2(300.0, 100.0))
        assertTrue(vm.state.value.plan.rooms.all { it.fixtures.isEmpty() })
    }
}
