package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Guardando un interrato la casa sta in fondo a uno scavo: prato alla quota del terreno, terra tagliata attorno. */
class ExcavationTest {
    private val cellar = FloorPlan(listOf(Room(1, "Cantina", RoomType.Altro, RoomFactory.rectangle(500.0, 400.0))))

    private fun soil(scene: Scene3D): Int {
        var n = 0
        var k = 0
        while (k < scene.opaque.size) {
            val r = scene.opaque[k + 6]; val g = scene.opaque[k + 7]; val b = scene.opaque[k + 8]
            if (r > g + 0.05f && g > b + 0.05f && scene.opaque[k + 4] == 0f) n++ // marrone, su una parete verticale
            k += Scene3D.FLOATS_PER_VERTEX
        }
        return n
    }

    @Test
    fun `un interrato ha lo scavo, con il prato attorno al buco`() {
        val scene = Scene3D.build(cellar, focusedRoomId = null, selected = null, ceilings = false, groundLevel = 300.0)
        val hole = assertNotNull(scene.excavation)
        assertTrue(hole.minX < 0 && hole.minY < 0 && hole.maxX > 500 && hole.maxY > 400, "lo scavo gira attorno alla casa: $hole")
        assertTrue(soil(scene) > 0, "le pareti di terra ci sono")
        // Il prato non copre la casa: nessun rettangolo entra nel buco.
        val lawn = scene.lawn(4000.0)
        assertEquals(4, lawn.size)
        val center = Vec2(250.0, 200.0)
        assertTrue(lawn.none { center.x in it.minX..it.maxX && center.y in it.minY..it.maxY })
    }

    @Test
    fun `al piano terra e senza interrati niente scavo, il prato e un solo rettangolo`() {
        for (g in listOf(null, 0.0)) {
            val scene = Scene3D.build(cellar, focusedRoomId = null, selected = null, ceilings = false, groundLevel = g)
            assertNull(scene.excavation)
            assertEquals(0, soil(scene))
            assertEquals(1, scene.lawn(4000.0).size)
        }
    }
}
