package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Computo con lo stato di fatto e di progetto: si conta come sarà la casa. Prima una porta da chiudere si toglieva ancora dalla
 * pittura e un muro da demolire si dipingeva e aveva il battiscopa. Stanza 400 × 300, alta 270, muri da 15.
 */
class TakeoffPhasesTest {
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0))
    private fun line(r: Room) = Takeoff.of(Building.single(FloorPlan(listOf(r)))).rooms.single()
    private val plain = line(room)

    @Test
    fun `porta da chiudere e porta nuova`() {
        val door = Opening.default(1, OpeningKind.Door, 1, 150.0)
        // Da chiudere: il muro resta pieno, come se non ci fosse.
        val closed = line(room.copy(openings = listOf(door.copy(phase = Phase.Demolish))))
        assertEquals(plain.wallArea, closed.wallArea, 1e-9)
        assertEquals(plain.skirting, closed.skirting, 1e-9)
        assertEquals(0, closed.doors)
        // Nuova: si toglie come una porta che c'è già.
        assertEquals(line(room.copy(openings = listOf(door))), line(room.copy(openings = listOf(door.copy(phase = Phase.New)))))
        // Il computo dice che è lo stato di progetto solo quando c'è qualcosa da demolire o di nuovo.
        assertEquals(true, Takeoff.of(Building.single(FloorPlan(listOf(room.copy(openings = listOf(door.copy(phase = Phase.New))))))).phased)
        assertEquals(false, Takeoff.of(Building.single(FloorPlan(listOf(room.copy(openings = listOf(door)))))).phased)
    }

    @Test
    fun `muro da demolire`() {
        // Il muro 1 (lungo 300) da demolire: come un lato aperto.
        val demolished = line(room.copy(wallPhases = mapOf(1 to Phase.Demolish)))
        assertEquals(line(room.copy(removedWalls = setOf(1))), demolished)
        assertEquals(true, demolished.wallArea < plain.wallArea - 7.0)
    }
}
