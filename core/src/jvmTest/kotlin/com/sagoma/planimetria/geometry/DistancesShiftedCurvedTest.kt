package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Strumento «Distanza» con muri spostati di lato e muri curvi: si misura dal muro vero (come è disegnato), non dalla
 * linea dritta tra i due angoli. Stanza 400 × 300, muro 1 a destra (x = 400), spessore 15; colonna 30 × 30 dentro.
 */
class DistancesShiftedCurvedTest {
    private val base = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 15.0)

    private fun distance(room: Room, column: Column) =
        Distances.between(FloorPlan(listOf(room), columns = listOf(column)), MeasureTarget.Wall(1, 1), MeasureTarget.Column(1), 270.0)!!.distance

    @Test
    fun `muro dritto - dal filo interno`() {
        // Filo interno a 392,5; bordo della colonna a 365.
        assertEquals(27.5, distance(base, Column(1, Vec2(350.0, 150.0))), 1e-6)
    }

    @Test
    fun `muro spostato verso fuori di 10 cm`() {
        // Mezzeria vera a 410, filo interno a 402,5.
        assertEquals(37.5, distance(base.copy(wallOffsets = mapOf(1 to 10.0)), Column(1, Vec2(350.0, 150.0))), 1e-6)
    }

    @Test
    fun `muro curvo verso fuori di 50 cm`() {
        // Arco di raggio 250 con centro a x = 200: a metà la mezzeria sta a 450, il filo interno (raggio 242,5) a 442,5.
        // Gli angoli della colonna (x = 435, y = 150 ± 15) sono i più vicini: lì il filo interno sta a 200 + √(242,5² − 15²).
        val expected = 200 + kotlin.math.sqrt(242.5 * 242.5 - 15.0 * 15.0) - 435
        assertEquals(expected, distance(base.copy(wallBulges = mapOf(1 to 50.0)), Column(1, Vec2(420.0, 150.0))), 0.1)
    }

    @Test
    fun `porta sul muro spesso - il vano e largo quanto il muro`() {
        val room = base.copy(wallThickness = 40.0, openings = listOf(Opening(1, OpeningKind.Door, 1, 150.0, 80.0, 210.0)))
        val d = Distances.between(FloorPlan(listOf(room), columns = listOf(Column(1, Vec2(350.0, 150.0)))), MeasureTarget.Opening(1, 1), MeasureTarget.Column(1), 270.0)!!
        // Faccia interna del vano a 380, bordo della colonna a 365.
        assertEquals(15.0, d.distance, 1e-6)
    }
}
