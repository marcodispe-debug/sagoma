package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Parapets
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

/** Parapetti: solo verso il vuoto, non contro la casa né tra due balconi che si toccano. */
class ParapetsTest {
    // Casa 400×300; a destra due balconi uno sotto l'altro (200×150 ciascuno) contro il muro della casa.
    private val house = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
    private val b1 = Room(2, "Balcone", RoomType.Balcone, RoomFactory.rectangle(200.0, 150.0).map { it + Vec2(400.0, 0.0) })
    private val b2 = Room(3, "Balcone 2", RoomType.Balcone, RoomFactory.rectangle(200.0, 150.0).map { it + Vec2(400.0, 150.0) })
    private val plan = FloorPlan(listOf(house, b1, b2))

    private fun total(r: Room) = Parapets.segments(plan, r).sumOf { (a, b) -> a.distanceTo(b) }

    @Test
    fun `niente ringhiera tra due balconi che si toccano`() {
        // Ogni balcone: 200 (lato lungo verso il vuoto) + 150 (lato corto esterno); niente verso la casa né in mezzo.
        assertEquals(350.0, total(b1), 1e-6)
        assertEquals(350.0, total(b2), 1e-6)
    }

    @Test
    fun `balcone da solo contro la casa`() {
        val alone = FloorPlan(listOf(house, b1))
        assertEquals(200.0 + 150.0 + 200.0, Parapets.segments(alone, b1).sumOf { (a, b) -> a.distanceTo(b) }, 1e-6)
    }
}
