package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dalla pianta al 3D: la stanza nuova nasce attaccata (una casa, non scatole separate) e con un pavimento vero. */
class NewRoomTo3DTest {
    private fun room(id: Long, w: Double, d: Double, x: Double = 0.0, y: Double = 0.0) =
        Room(id, "S$id", RoomType.Altro, RoomFactory.rectangle(w, d).map { it + com.sagoma.planimetria.model.Vec2(x, y) })

    @Test
    fun `la stanza nuova si attacca a destra di quella aperta`() {
        val plan = FloorPlan(listOf(room(1, 500.0, 400.0), room(2, 300.0, 300.0, y = 400.0)))
        val placed = Polygon.bounds(RoomFactory.placeBeside(plan, RoomFactory.rectangle(200.0, 200.0), anchorId = 2))
        assertEquals(300.0, placed.minX, 1e-9)
        assertEquals(400.0, placed.minY, 1e-9)
    }

    @Test
    fun `se a destra è occupato prova sotto, a sinistra, sopra`() {
        // A destra della 1 c'è già la 2: la nuova va sotto la 1.
        val plan = FloorPlan(listOf(room(1, 400.0, 400.0), room(2, 300.0, 400.0, x = 400.0)))
        val placed = Polygon.bounds(RoomFactory.placeBeside(plan, RoomFactory.rectangle(200.0, 200.0), anchorId = 1))
        assertEquals(0.0, placed.minX, 1e-9)
        assertEquals(400.0, placed.minY, 1e-9)
    }

    @Test
    fun `nuove stanze con il pavimento del loro tipo e cambio tipo`() {
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = Building.single(FloorPlan(listOf(room(1, 400.0, 400.0))))
            override fun save(building: Building) {}
        })
        vm.startAddRoom()
        vm.pickShape(com.sagoma.planimetria.model.RoomShape.Rectangle)
        vm.createRoom(RoomType.Bagno, com.sagoma.planimetria.geometry.ShapeDimensions(200.0, 250.0), 270.0)
        val bagno = vm.state.value.plan.rooms.last()
        assertEquals(FloorFinish.LightTiles, bagno.floorFinish)
        // Attaccato alla stanza che c'era: muro in comune a x = 400.
        assertEquals(400.0, Polygon.bounds(bagno.points).minX, 1e-6)
        vm.changeRoomType(bagno.id, RoomType.Camera)
        assertEquals(FloorFinish.OakParquet, vm.state.value.plan.room(bagno.id)!!.floorFinish)
        vm.setRoomLook(bagno.id, floor = FloorFinish.Marble)
        vm.changeRoomType(bagno.id, RoomType.Cucina)
        assertEquals(FloorFinish.Marble, vm.state.value.plan.room(bagno.id)!!.floorFinish, "scelto a mano: resta")
    }
}
