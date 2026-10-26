package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Distanza dal muro com'è disegnato, per prenderlo col dito in tutta la sua larghezza: muro 1 della stanza 400 × 300
 * (a destra, x = 400). Prima il tocco contava sempre un muro da 15 cm sulla linea della stanza.
 */
class WallBodyDistanceTest {
    private val base = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))

    @Test
    fun `muro da 15 - come prima`() {
        assertEquals(0.0, base.distanceToWallBody(1, Vec2(405.0, 150.0)), 1e-9)
        assertEquals(2.5, base.distanceToWallBody(1, Vec2(390.0, 150.0)), 1e-9)
    }

    @Test
    fun `muro da 50 - dentro il muro anche a 20 cm dalla linea`() {
        val thick = base.copy(wallThickness = 50.0)
        assertEquals(0.0, thick.distanceToWallBody(1, Vec2(420.0, 150.0)), 1e-9)
        assertEquals(5.0, thick.distanceToWallBody(1, Vec2(370.0, 150.0)), 1e-9)
    }

    @Test
    fun `muro spostato verso fuori di 20 cm`() {
        val shifted = base.copy(wallOffsets = mapOf(1 to 20.0))
        assertEquals(0.0, shifted.distanceToWallBody(1, Vec2(425.0, 150.0)), 1e-9)
        assertEquals(5.0, shifted.distanceToWallBody(1, Vec2(407.5, 150.0)), 1e-9)
    }

    @Test
    fun `muro curvo - dentro il muro in cima alla curva`() {
        val curved = base.copy(wallBulges = mapOf(1 to 60.0))
        assertEquals(0.0, curved.distanceToWallBody(1, Vec2(460.0, 150.0)), 1e-6)
    }

    @Test
    fun `vano della porta largo quanto il muro`() {
        val room = base.copy(wallThickness = 40.0, openings = listOf(Opening(1, OpeningKind.Door, 1, 150.0, 80.0, 210.0)))
        val o = room.openings.single()
        assertEquals(0.0, room.distanceToOpeningBody(o, Vec2(418.0, 150.0)), 1e-9)
        assertEquals(10.0, room.distanceToOpeningBody(o, Vec2(370.0, 150.0)), 1e-9)
    }
}
