package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Soglia di una porta con un piano sotto: il muro scende nello spessore del solaio, ma nel vano della porta il suo pezzo
 * sotto il pavimento non deve arrivare alla quota del pavimento (le due facce alla stessa quota sfarfallano).
 */
class DoorThresholdTest {
    private val left = Room(1, "Camera", RoomType.Camera, listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)))
        .copy(openings = listOf(Opening(1, OpeningKind.Door, 1, 150.0, 80.0, 210.0)))
    private val right = Room(2, "Bagno", RoomType.Bagno, listOf(Vec2(400.0, 0.0), Vec2(700.0, 0.0), Vec2(700.0, 300.0), Vec2(400.0, 300.0)))

    @Test
    fun `nel vano della porta a terra c'e solo il pavimento`() {
        val plan = FloorPlan(listOf(left, right))
        val below = Floor(2, "Piano interrato", plan.copy(rooms = plan.rooms.map { it.copy(openings = emptyList()) }))
        for (floors in listOf(emptyList(), listOf(below))) {
            val scene = Scene3D.build(plan, null, null, ceilings = false, below = floors)
            // Facce rivolte in su, a quota 0, tutte dentro il vano della porta (i pavimenti invece arrivano dagli angoli delle stanze).
            val v = scene.opaque
            val atFloor = (0 until v.size / 30).filter { t ->
                (0 until 3).all { j ->
                    val o = t * 30 + j * 10
                    abs(v[o + 1]) < 0.05f && v[o + 4] > 0.9f && v[o] in 390f..410f && v[o + 2] in 100f..200f
                }
            }
            assertTrue(atFloor.isEmpty(), "con ${floors.size} piani sotto, ${atFloor.size} facce a quota 0 nel vano della porta")
        }
    }
}
