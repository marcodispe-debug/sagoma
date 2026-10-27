package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Casa con tantissime stanze (es. un condominio intero disegnato su un piano): il 3D confrontava ogni muro con tutti gli
 * altri muri e tutte le porte della casa, e ci metteva decine di secondi (qui 10 s, ora 1,5 s). Ora guarda solo le stanze vicine.
 */
class ManyRoomsSceneTest {
    private fun grid(n: Int): FloorPlan {
        var id = 1L
        val rooms = (0 until n).flatMap { r ->
            (0 until n).map { c ->
                // Ogni lato in 10 pezzi, come un muro curvo srotolato.
                val corners = RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(c * 300.0, r * 300.0) }
                val pts = corners.indices.flatMap { k -> val a = corners[k]; val b = corners[(k + 1) % 4]; (0 until 10).map { j -> a + (b - a) * (j / 10.0) } }
                Room(id, "Stanza $id", RoomType.Camera, pts, openings = listOf(Opening(id, OpeningKind.Door, wallIndex = 15, position = 15.0, width = 20.0, height = 210.0))).also { id++ }
            }
        }
        return FloorPlan(rooms)
    }

    @Test
    fun quattrocentoStanzeInPocoTempo() {
        val plan = grid(20)
        val t0 = System.nanoTime()
        Scene3D.build(plan, null, null, ceilings = true)
        val seconds = (System.nanoTime() - t0) / 1e9
        assertTrue(seconds < 5, "3D di ${plan.rooms.size} stanze in $seconds s")
    }
}
