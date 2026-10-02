package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.GroupItem
import com.sagoma.planimetria.geometry.GroupOps
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Eliminare un muro di una stanza: lato aperto, anche per la stanza accanto; si rimette; segue il muro. */
class RemoveWallTest {
    // Soggiorno 400×300 e cucina 300×300 accanto a destra: il muro 1 del soggiorno (x = 400) è in comune.
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
        .copy(openings = listOf(Opening.default(1, OpeningKind.Door, 1, 150.0), Opening.default(2, OpeningKind.Window2, 0, 200.0)))
    private val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(living, kitchen)))
        override fun save(building: Building) {}
    })

    /** Indice del muro della stanza sulla retta x = 400. */
    private fun sharedIndex(r: Room) = (0 until r.wallCount).first { r.wallStart(it).x == 400.0 && r.wallEnd(it).x == 400.0 }

    @Test
    fun `muro in comune eliminato per entrambe le stanze e poi rimesso`() {
        val vm = vm()
        val i = sharedIndex(living)
        vm.setWallRemoved(1, i, removed = true)
        val p = vm.state.value.plan
        assertTrue(p.room(1)!!.isRemoved(i))
        assertTrue(p.room(2)!!.isRemoved(sharedIndex(kitchen)))
        // La porta sul muro eliminato sparisce, la finestra sull'altro muro resta.
        assertEquals(listOf(2L), p.room(1)!!.openings.map { it.id })
        // Si salva e si rilegge uguale; il 3D si costruisce.
        val b = vm.state.value.fullBuilding
        assertEquals(b, PlanJson.decodeBuilding(PlanJson.encode(b)))
        Scene3D.build(p, null, null, ceilings = true)
        vm.setWallRemoved(1, i, removed = false)
        assertFalse(vm.state.value.plan.room(1)!!.isRemoved(i))
        assertFalse(vm.state.value.plan.room(2)!!.isRemoved(sharedIndex(kitchen)))
        vm.undo()
        assertTrue(vm.state.value.plan.room(1)!!.isRemoved(i))
    }

    @Test
    fun `canc elimina il muro selezionato`() {
        val vm = vm()
        vm.selectWall(1, 0)
        assertTrue(vm.deleteSelected())
        assertTrue(vm.state.value.plan.room(1)!!.isRemoved(0))
        assertEquals(Selection.Wall(1, 0), vm.state.value.selection)
    }

    @Test
    fun `lo stato segue il muro con angoli aggiunti o tolti e specchiando`() {
        val r = living.copy(removedWalls = setOf(1))
        // Angolo a metà del muro 1: entrambe le metà restano eliminate, il muro dopo scala di uno.
        val split = r.splitWall(1)
        assertEquals(setOf(1, 2), split.removedWalls)
        // Si riunisce: torna un solo muro eliminato.
        assertEquals(setOf(1), split.removeCorner(2)!!.removedWalls)
        // Unendo un muro eliminato con uno no, il muro unito c'è.
        assertEquals(emptySet(), r.removeCorner(2)!!.removedWalls)
        // Specchiata: il muro eliminato è sempre quello sulla destra... ora a sinistra.
        val m = GroupOps.mirror(FloorPlan(listOf(r)), setOf(GroupItem.RoomItem(1)), Vec2(200.0, 150.0), horizontal = true).room(1)!!
        val k = m.removedWalls.single()
        assertEquals(0.0, m.wallStart(k).x, 1e-6)
        assertEquals(0.0, m.wallEnd(k).x, 1e-6)
    }
}
