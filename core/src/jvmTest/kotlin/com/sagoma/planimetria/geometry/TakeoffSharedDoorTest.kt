package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Computo: la porta tra due stanze sta in una sola delle due, ma il vano buca il muro da entrambe le parti.
 * Anche la stanza accanto ha meno parete da dipingere e il battiscopa interrotto.
 */
class TakeoffSharedDoorTest {
    // Soggiorno 400 × 300 e cucina 300 × 300 a destra, muro in comune a x = 400; porta 80 × 210 nel soggiorno su quel muro.
    private val door = Opening(1, OpeningKind.Door, 1, 150.0, 80.0, 210.0)
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), openings = listOf(door))
    private val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })

    private fun lines(vararg rooms: Room) = Takeoff.of(Building.single(FloorPlan(rooms.toList()))).rooms.associateBy { it.room }

    @Test
    fun `la stanza accanto ha il vano della porta`() {
        val alone = lines(kitchen).getValue("Cucina")
        val withDoor = lines(living, kitchen).getValue("Cucina")
        assertEquals(alone.wallArea - 0.8 * 2.1, withDoor.wallArea, 1e-6)
        assertEquals(alone.skirting - 0.8, withDoor.skirting, 1e-6)
        assertEquals(0.8 * 2.1, withDoor.openingsArea, 1e-6)
        // La porta resta contata una volta sola, nella stanza dove è stata messa.
        assertEquals(0, withDoor.doors)
    }

    @Test
    fun `pareti per finitura uguali alle pareti delle stanze`() {
        val res = Takeoff.of(Building.single(FloorPlan(listOf(living, kitchen))))
        assertEquals(res.totalWalls, res.wallFinishes.sumOf { it.quantity }, 1e-6)
    }

    @Test
    fun `finestra tra due stanze - non buca la stanza accanto (come nel disegno)`() {
        val window = living.copy(openings = listOf(Opening(1, OpeningKind.Window1, 1, 150.0, 80.0, 120.0, 90.0)))
        assertEquals(lines(kitchen).getValue("Cucina").wallArea, lines(window, kitchen).getValue("Cucina").wallArea, 1e-6)
    }
}
