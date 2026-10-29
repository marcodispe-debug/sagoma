package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.math.asin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Computo con i muri singoli (tramezzi, muretti): prima non c'erano, e le loro due facce mancavano dalla pittura e dal
 * battiscopa. Stanza 500 × 400 alta 270, muri da 30; tramezzo da parete a parete, lungo 370.
 */
class TakeoffFreeWallsTest {
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0), wallThickness = 30.0)
    private val partition = FreeWall(1, Vec2(250.0, 15.0), Vec2(250.0, 385.0))

    private fun takeoff(vararg walls: FreeWall) = Takeoff.of(Building.single(FloorPlan(listOf(room), freeWalls = walls.toList())))

    @Test
    fun `tramezzo con una porta`() {
        val door = Opening.default(10, OpeningKind.Door, 0, 185.0)
        val res = takeoff(partition.copy(openings = listOf(door)))
        val line = res.rooms.single { it.room == T.computo.muriSingoli }
        // Due facce da 370 × 270, ognuna senza la porta 80 × 210.
        assertEquals(2 * (370 * 270 - 80 * 210) / 10_000.0, line.wallArea, 1e-6)
        assertEquals(2 * 80 * 210 / 10_000.0, line.openingsArea, 1e-6)
        assertEquals(2 * (370 - 80) / 100.0, line.skirting, 1e-6)
        assertEquals(1, line.doors)
        assertEquals(0.0, line.floorArea)
        // I totali per finitura tornano con quelli delle righe.
        assertEquals(res.totalWalls, res.wallFinishes.sumOf { it.quantity }, 1e-6)
        assertEquals(takeoff().totalWalls + line.wallArea, res.totalWalls, 1e-6)
    }

    @Test
    fun `muretto basso, curvo e muro da demolire`() {
        val low = partition.copy(height = 100.0)
        assertEquals(2 * 370 * 100 / 10_000.0, takeoff(low).rooms.single { it.room == T.computo.muriSingoli }.wallArea, 1e-6)
        // Curvo, scostato di 50 a metà: le due facce sono lunghe in media quanto l'arco della mezzeria.
        val r = (185.0 * 185.0 + 50.0 * 50.0) / (2 * 50.0)
        val arc = 2 * r * asin(185.0 / r)
        val curved = takeoff(partition.copy(bulge = 50.0)).rooms.single { it.room == T.computo.muriSingoli }
        assertEquals(2 * arc * 270 / 10_000.0, curved.wallArea, 1e-6)
        assertEquals(2 * arc / 100.0, curved.skirting, 1e-6)
        // Da demolire: non si finisce.
        assertNull(takeoff(partition.copy(phase = Phase.Demolish)).rooms.singleOrNull { it.room == T.computo.muriSingoli })
    }
}
