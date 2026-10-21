package com.sagoma.planimetria.model

import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.en.Inglese
import com.sagoma.planimetria.testi.es.Spagnolo
import com.sagoma.planimetria.testi.fr.Francese
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals("Shaft", AutoNames.zone("Cavedio", Inglese))
        assertEquals("Project 4", AutoNames.project("Progetto 4", Inglese))
        assertEquals(Inglese.progetti.mioProgetto, AutoNames.project("Il mio progetto", Inglese))
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

    @Test
    fun franceseESpagnolo() {
        assertEquals("Cuisine 2", AutoNames.room("Cucina 2", Francese))
        assertEquals("Cocina 2", AutoNames.room("Cuisine 2", Spagnolo))
        assertEquals("Camera da letto", AutoNames.room("Dormitorio", Italiano))
        assertEquals("Rez-de-chaussée", AutoNames.floor("Planta baja", Francese))
        assertEquals("Planta 7", AutoNames.floor("Floor 7", Spagnolo))
        assertEquals("Projet 3", AutoNames.project("Proyecto 3", Francese))
        assertEquals(Spagnolo.progetti.mioProgetto, AutoNames.project(Francese.progetti.mioProgetto, Spagnolo))
        assertEquals(Francese.muroPerMuro.cavedio, AutoNames.zone("Cavedio", Francese))
    }

    /** Un nome automatico di una lingua non deve essere quello di un'altra stanza o di un altro piano in un'altra lingua. */
    @Test
    fun nessunNomeInComuneTraLingue() {
        val stanze = Lingue.tutte.flatMap { l -> RoomType.entries.map { l.nomi.roomDefaultName(it) to it } }
        for ((nome, tipi) in stanze.groupBy({ it.first }, { it.second })) {
            assertTrue(tipi.distinct().size == 1, "\"$nome\" è il nome di più stanze: $tipi")
        }
        val piani = Lingue.tutte.flatMap { l -> (0..30).map { Building.floorName(it, l) to it } }
        for ((nome, numeri) in piani.groupBy({ it.first }, { it.second })) {
            assertTrue(numeri.distinct().size == 1, "\"$nome\" è il nome di più piani: $numeri")
        }
    }
}
