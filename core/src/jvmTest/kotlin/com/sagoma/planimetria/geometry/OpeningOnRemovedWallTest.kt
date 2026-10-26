package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Trascinando una porta lungo la stanza non finisce mai su un muro eliminato (lato aperto, dove non c'è muro). */
class OpeningOnRemovedWallTest {
    private val room = Room(1, "Balcone", RoomType.Balcone, RoomFactory.rectangle(400.0, 300.0)).copy(removedWalls = setOf(1))

    @Test
    fun `vicino al lato aperto la porta resta sul muro accanto`() {
        // Proprio sul lato aperto (muro 1, a destra): si sceglie il muro più vicino che c'è davvero.
        val mid = (room.wallStart(1) + room.wallEnd(1)) / 2.0
        val (j, _) = Openings.alongPerimeter(room, mid, 80.0)!!
        assertNotEquals(1, j)
        // Sugli altri muri tutto come prima.
        assertEquals(3, Openings.alongPerimeter(room, (room.wallStart(3) + room.wallEnd(3)) / 2.0 + Vec2(5.0, 0.0), 80.0)!!.first)
    }

    @Test
    fun `stanza con tutti i muri eliminati - la porta non si sposta`() {
        val open = room.copy(removedWalls = (0 until room.wallCount).toSet())
        assertEquals(null, Openings.alongPerimeter(open, Vec2(10.0, 10.0), 80.0))
    }
}
