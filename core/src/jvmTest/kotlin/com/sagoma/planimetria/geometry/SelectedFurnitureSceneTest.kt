package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Selezionare un mobile nel 3D senza ricostruire la scena (Scene3D.withSelectedFurniture). */
class SelectedFurnitureSceneTest {

    private val room = Room(1, "Soggiorno", RoomType.Altro, RoomFactory.rectangle(500.0, 400.0))
    private fun furniture(id: Long, model: String, at: Vec2) = Furniture(id, model, at, width = 120.0, depth = 60.0, height = 80.0)
    private val plan = FloorPlan(
        listOf(room),
        furniture = listOf(furniture(10, "divano_prova", Vec2(150.0, 150.0)), furniture(11, "tavolo_prova", Vec2(350.0, 250.0))),
    )

    @Test
    fun `selezionare un mobile col modello dà la stessa scena della ricostruzione`() {
        val base = Scene3D.build(plan, null, null, ceilings = false)
        for (id in listOf(10L, 11L)) {
            val rebuilt = Scene3D.build(plan, null, Pick.Furniture(id), ceilings = false)
            val quick = base.withSelectedFurniture(id)!!
            assertContentEquals(rebuilt.opaque, quick.opaque)
            assertContentEquals(rebuilt.transparent, quick.transparent)
            assertEquals(rebuilt.furniture, quick.furniture)
            assertEquals(rebuilt.lights, quick.lights)
            assertEquals(rebuilt.windows, quick.windows)
            assertContentEquals(rebuilt.roofs, quick.roofs)
            assertEquals(rebuilt.groundLevel, quick.groundLevel)
            assertEquals(rebuilt.excavation, quick.excavation)
            assertEquals(rebuilt.rooms, quick.rooms)
            assertEquals(rebuilt.treads, quick.treads)
            assertEquals(rebuilt.noFloor, quick.noFloor)
            assertEquals(rebuilt.textured.keys, quick.textured.keys)
            for ((k, v) in rebuilt.textured) assertContentEquals(v, quick.textured.getValue(k))
            assertEquals(listOf(id), quick.furniture.filter { it.selected }.map { it.id })
        }
        // Nessuna selezione: tutti spenti.
        assertTrue(base.withSelectedFurniture(10)!!.withSelectedFurniture(null)!!.furniture.none { it.selected })
    }

    @Test
    fun `un mobile che non è tra quelli col modello chiede la ricostruzione`() {
        val base = Scene3D.build(plan, null, null, ceilings = false)
        assertNull(base.withSelectedFurniture(99))
    }

    @Test
    fun `i mobili dei piani di sotto non si selezionano anche se hanno lo stesso id`() {
        val below = Floor(id = 1, name = "Terra", plan = FloorPlan(listOf(room), furniture = listOf(furniture(10, "letto_prova", Vec2(200.0, 200.0)))))
        val base = Scene3D.build(plan, null, null, ceilings = false, below = listOf(below))
        val rebuilt = Scene3D.build(plan, null, Pick.Furniture(10), ceilings = false, below = listOf(below))
        val quick = base.withSelectedFurniture(10)!!
        assertEquals(rebuilt.furniture, quick.furniture)
        assertEquals(1, quick.furniture.count { it.selected })
        assertTrue(quick.furniture.single { it.selected }.onCurrentFloor)
    }
}
