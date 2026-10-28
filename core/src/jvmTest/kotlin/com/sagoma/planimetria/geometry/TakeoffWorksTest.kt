package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opere nel computo: muri da demolire e nuovi (m² e m³), vani da aprire e aperture da chiudere. */
class TakeoffWorksTest {
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 30.0)
    private val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }, wallThickness = 30.0)

    @Test
    fun `muro da demolire in comune contato una volta, meno la porta`() {
        // Il muro in comune (x = 400, lungo 300, alto 270, spesso 30) da demolire, segnato in tutte e due le stanze, con una porta.
        val door = Opening.default(1, OpeningKind.Door, 3, 150.0)
        val plan = FloorPlan(listOf(living.copy(wallPhases = mapOf(1 to Phase.Demolish)), kitchen.copy(wallPhases = mapOf(3 to Phase.Demolish), openings = listOf(door))))
        val w = Phases.works(plan)
        assertEquals(3.0 * 2.7 - 0.8 * 2.1, w.demolishM2, 1e-9)
        assertEquals((3.0 * 2.7 - 0.8 * 2.1) * 0.3, w.demolishM3, 1e-9)
        assertEquals(0.0, w.newM2)
        // La porta va via con il muro: non è un'apertura da chiudere.
        assertEquals(0, w.openingsClosed)
    }

    @Test
    fun `vani da aprire, aperture da chiudere e muri nuovi`() {
        val window = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(phase = Phase.New)
        val closed = Opening.default(2, OpeningKind.Door, 2, 200.0).copy(phase = Phase.Demolish)
        val wall = FreeWall(1, Vec2(100.0, 0.0), Vec2(100.0, 300.0), thickness = 10.0, phase = Phase.New, openings = listOf(Opening.default(3, OpeningKind.Door, 0, 150.0)))
        val plan = FloorPlan(listOf(living.copy(openings = listOf(window, closed))), freeWalls = listOf(wall))
        val w = Phases.works(plan, 300.0)
        assertEquals(1, w.openingsNew)
        assertEquals(1.2 * 1.2, w.openingsNewM2, 1e-9)
        assertEquals(1.2 * 1.2 * 0.3, w.openingsNewM3, 1e-9)
        assertEquals(1, w.openingsClosed)
        assertEquals(0.8 * 2.1, w.openingsClosedM2, 1e-9)
        val h = Structure.freeWallHeight(plan, wall, 300.0)
        assertEquals((300.0 * h - 80.0 * 210.0) / 1e4, w.newM2, 1e-9)
        assertEquals((300.0 * h - 80.0 * 210.0) * 10.0 / 1e6, w.newM3, 1e-9)
        assertEquals(0.0, w.demolishM2)
        // Le voci nell'ordine del cantiere, solo quelle che ci sono.
        assertEquals(listOf(T.computo.vaniDaAprire, T.computo.apertureDaChiudere, T.computo.muriNuovi), Takeoff.worksItems(w).map { it.label })
        assertEquals(listOf(1, 1, null), Takeoff.worksItems(w).map { it.count })
    }

    @Test
    fun `con piu piani anche il totale della casa`() {
        val demolished = living.copy(wallPhases = mapOf(0 to Phase.Demolish))
        val b = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(demolished))), Floor(2, "Primo piano", FloorPlan(listOf(demolished)))))
        val csv = Takeoff.csv(Takeoff.of(b))
        assertTrue(csv.contains("Primo piano;" + T.computo.muriDaDemolire + ";;10,80;3,24\n" + T.computo.totale + ";" + T.computo.muriDaDemolire + ";;21,60;6,48\n"), csv)
    }

    @Test
    fun `nel computo una riga per i piani con le opere`() {
        val demolished = living.copy(wallPhases = mapOf(0 to Phase.Demolish))
        val b = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(demolished))), Floor(2, "Primo piano", FloorPlan(listOf(kitchen)))))
        val res = Takeoff.of(b)
        assertEquals(listOf("Piano terra"), res.works.map { it.floor })
        assertEquals(4.0 * 2.7, res.totalWorks.demolishM2, 1e-9)
        val csv = Takeoff.csv(res)
        assertTrue(csv.contains(T.computo.opere2 + "\n" + T.computo.pianoVoceNumeroM2M3 + "\nPiano terra;" + T.computo.muriDaDemolire + ";;10,80;3,24\n"), csv)
        assertEquals(listOf(T.computo.muriDaDemolire), Takeoff.worksItems(res.totalWorks).map { it.label })
        // Senza demolizioni né parti nuove la tabella non c'è.
        assertTrue(Takeoff.of(Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(living)))))).works.isEmpty())
    }
}
