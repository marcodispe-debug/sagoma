package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Colonna accostata al muro: va sulla faccia vera anche dei muri spostati di lato, e non si aggancia alla linea dritta
 * dei muri curvi. Stanza 400 × 300, muro 1 a destra (x = 400) spesso 20; colonna 30 × 30.
 */
class SnapShiftedWallTest {
    private val base = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 20.0)

    private fun snappedX(room: Room, x: Double) = Structure.snapped(FloorPlan(listOf(room)), Column(1, Vec2(x, 150.0))).center.x

    @Test
    fun `muro dritto - faccia interna a 390`() {
        assertEquals(375.0, snappedX(base, 372.0), 1e-6)
    }

    @Test
    fun `muro spostato verso fuori di 12 cm - faccia interna a 402`() {
        assertEquals(387.0, snappedX(base.copy(wallOffsets = mapOf(1 to 12.0)), 384.0), 1e-6)
    }

    @Test
    fun `muro curvo - la colonna non si aggancia alla corda`() {
        assertEquals(372.0, snappedX(base.copy(wallBulges = mapOf(1 to 60.0)), 372.0), 1e-6)
    }
}
