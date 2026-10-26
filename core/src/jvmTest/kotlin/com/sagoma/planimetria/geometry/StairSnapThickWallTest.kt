package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/** Scala trascinata vicino a un muro: si accosta alla faccia vera del muro, qualunque sia il suo spessore. */
class StairSnapThickWallTest {
    private val rise = 300.0

    private fun minX(plan: FloorPlan, x: Double): Double {
        val s = Stair(1, StairKind.Straight, Vec2(0.0, 300.0))
        val half = Stairs.layout(s, rise).bounds.let { (it.maxX - it.minX) / 2 }
        return Stairs.layout(Stairs.snapped(plan, s.copy(center = Vec2(x + half, 300.0)), rise), rise).bounds.minX
    }

    @Test
    fun `muro da 30 cm - la scala si ferma sul filo interno a 15`() {
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0), wallThickness = 30.0)
        assertEquals(15.0, minX(FloorPlan(listOf(room)), 22.0), 1e-9)
    }

    @Test
    fun `muro spostato verso fuori di 10 cm - filo interno a meno 2,5`() {
        // Muro di sinistra con la mezzeria a x = −10: il filo interno sta a −10 + 7,5.
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0), wallOffsets = mapOf(3 to 10.0))
        assertEquals(-2.5, minX(FloorPlan(listOf(room)), 3.0), 1e-9)
    }

    @Test
    fun `si accosta anche a un muro singolo`() {
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))
        val plan = FloorPlan(listOf(room), freeWalls = listOf(FreeWall(1, Vec2(200.0, 0.0), Vec2(200.0, 600.0), thickness = 10.0)))
        assertEquals(205.0, minX(plan, 212.0), 1e-9)
    }
}
