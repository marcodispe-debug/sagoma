package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.ExcludedZone
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/** Il computo conta il pavimento come la pianta: senza le zone non calpestabili. */
class TakeoffExcludedZoneTest {
    @Test
    fun zonaNonCalpestabileTolta() {
        // Stanza 400 × 300 in mezzeria, muri da 15: dentro 385 × 285. Zona di 100 × 100 tutta dentro.
        val zone = ExcludedZone(1, listOf(Vec2(50.0, 50.0), Vec2(150.0, 50.0), Vec2(150.0, 150.0), Vec2(50.0, 150.0)))
        val room = Room(1, "Bagno", RoomType.Bagno, RoomFactory.rectangle(400.0, 300.0), excludedZones = listOf(zone))
        val res = Takeoff.of(Building.single(FloorPlan(listOf(room))))
        val line = res.rooms.single()
        assertEquals(room.usableArea() / 10_000, line.floorArea, 1e-9)
        assertEquals(3.85 * 2.85 - 1.0, line.floorArea, 1e-6)
        assertEquals(line.floorArea, res.floorMaterials.single().quantity, 1e-9)
    }
}
