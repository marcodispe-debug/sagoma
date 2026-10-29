package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallCut
import com.sagoma.planimetria.testi.T
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Computo: i m² dei soffitti da dipingere, con la falda vera in mansarda e senza balconi. Stanza 500 × 400, muri da 30. */
class TakeoffCeilingsTest {
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0), wallThickness = 30.0)

    private fun takeoff(vararg rooms: Room) = Takeoff.of(Building.single(FloorPlan(rooms.toList())))

    @Test
    fun `soffitto piano e balcone`() {
        val balcony = Room(2, "Balcone", RoomType.Balcone, RoomFactory.rectangle(500.0, 150.0).map { Vec2(it.x, it.y - 150) })
        val res = takeoff(room, balcony)
        // Dentro i muri: 470 × 370. Il balcone non ha soffitto.
        assertEquals(4.7 * 3.7, res.ceilings, 1e-6)
        assertTrue(Takeoff.csv(res).lines().any { it == "${T.computo.soffitti};17,39" }, Takeoff.csv(res))
    }

    @Test
    fun `mansarda`() {
        // Muro 3 basso (140), il soffitto sale di 130 nei primi 250 cm: la falda è più lunga della sua pianta di √(1 + 0,52²).
        val r = room.copy(wallHeights = mapOf(3 to 140.0), wallCuts = mapOf(0 to WallCut(false, 250.0), 2 to WallCut(true, 250.0)))
        val k = sqrt(1 + 0.52 * 0.52)
        // 235 cm in pendenza (da 15 a 250) e 235 in piano (da 250 a 485), larghi 370.
        assertEquals((2.35 * 3.7 * k + 2.35 * 3.7), takeoff(r).ceilings, 1e-6)
        // Altezza media: in pendenza da 147,8 (a 15 cm dal muro basso) a 270, poi 270 in piano, metà e metà.
        assertEquals(((147.8 + 270.0) / 2 + 270.0) / 2, Ceilings.averageHeight(r), 1e-6)
        assertEquals(270.0, Ceilings.averageHeight(room), 1e-9)
        assertEquals(4.7 * 3.7 * Ceilings.averageHeight(r) / 100, Ceilings.volumeM3(r), 1e-6)
    }
}
