package com.sagoma.planimetria.model

import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.en.Inglese
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cambiando lingua i nomi automatici di stanze e piani si traducono; quelli scritti dall'utente no. */
class AutoNamesTest {
    @Test
    fun nomiAutomaticiTradotti() {
        assertEquals("Kitchen", AutoNames.room("Cucina", Inglese))
        assertEquals("Kitchen 2", AutoNames.room("Cucina 2", Inglese))
        assertEquals("Room", AutoNames.room("Stanza", Inglese))
        assertEquals("Camera da letto 3", AutoNames.room("Bedroom 3", Italiano))
        assertEquals("Ground floor", AutoNames.floor("Piano terra", Inglese))
        assertEquals("Floor 7", AutoNames.floor("Piano 7", Inglese))
        assertEquals("Primo piano", AutoNames.floor("First floor", Italiano))
    }

    @Test
    fun nomiDellUtenteRestano() {
        assertEquals("Cucina di nonna", AutoNames.room("Cucina di nonna", Inglese))
        assertEquals("Cucina B", AutoNames.room("Cucina B", Inglese))
        assertEquals("Kitchen", AutoNames.room("Kitchen", Inglese))
        assertEquals("Mansarda", AutoNames.floor("Mansarda", Inglese))
    }

    @Test
    fun casaIntera() {
        val plan = FloorPlan(rooms = listOf(Room(id = 1, name = "Cucina", type = RoomType.Cucina, points = listOf(Vec2(0.0, 0.0), Vec2(300.0, 0.0), Vec2(300.0, 300.0), Vec2(0.0, 300.0)))))
        val b = Building(listOf(Floor(1, "Piano terra", plan))).withAutoNames(Inglese)
        assertEquals("Ground floor", b.floors[0].name)
        assertEquals("Kitchen", b.floors[0].plan.rooms[0].name)
        assertEquals(b, b.withAutoNames(Inglese))
    }
}
