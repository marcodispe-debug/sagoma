package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ringhiera del vano scala al piano di sopra: contro un muro spesso non serve (prima si contava un muro da 15 cm). */
class StairRailingThickWallTest {
    private val rise = 300.0
    private val s = Stair(1, StairKind.Straight, Vec2(250.0, 300.0), wellRailing = StairRailing.Metal)
    private val free = Stairs.wellRailingRuns(s, rise, emptyList()).sumOf { (a, b) -> a.distanceTo(b) }

    private fun railing(room: Room): Double {
        val plan = FloorPlan(listOf(room))
        // Scala accostata al muro di sinistra (alla sua faccia vera).
        val against = Stairs.snapped(plan, s.copy(center = Vec2(60.0, 300.0)), rise)
        return Stairs.wellRailingRunsAlong(against, rise, Structure.wallLines(plan)).sumOf { (a, b) -> a.distanceTo(b) }
    }

    @Test
    fun `muro da 15 - il lato contro il muro resta senza ringhiera`() {
        assertEquals(free - 16 * 28.0, railing(Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))), 1e-6)
    }

    @Test
    fun `muro da 40 - anche`() {
        assertEquals(free - 16 * 28.0, railing(Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0), wallThickness = 40.0)), 1e-6)
    }
}
