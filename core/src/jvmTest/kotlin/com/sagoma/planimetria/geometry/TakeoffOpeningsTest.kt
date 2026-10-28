package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.Finish
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.GarageDoor
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.en.Inglese
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Computo: l'elenco di porte e finestre uguali (tipo, aspetto, misure del vano), con quante sono e dove, per chiedere i preventivi. */
class TakeoffOpeningsTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    private fun room(id: Long, name: String, dx: Double, vararg openings: Opening) =
        Room(id, name, RoomType.Camera, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(dx, 0.0) }).copy(openings = openings.toList())

    private val window = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(width = 120.0, height = 140.0, shading = Shading.Shutters, grille = true)

    private val house = Building(
        listOf(
            Floor(
                1, "Piano terra",
                FloorPlan(
                    listOf(
                        room(1, "Cucina", 0.0, window, Opening.default(2, OpeningKind.Door, 2, 200.0).copy(doorModel = DoorModel.Panels)),
                        room(2, "Camera", 500.0, window, window.copy(id = 2, wallIndex = 2, width = 100.0), Opening.default(3, OpeningKind.Passage, 1, 150.0)),
                        // Garage: porta basculante, e una finestra da chiudere che non va ordinata.
                        room(
                            3, "Garage", 1000.0,
                            Opening.default(1, OpeningKind.Door2, 0, 200.0).copy(garage = GarageDoor.UpAndOver, width = 250.0),
                            window.copy(id = 2, wallIndex = 1, phase = Phase.Demolish),
                        ),
                    ),
                    freeWalls = listOf(FreeWall(1, Vec2(0.0, 600.0), Vec2(300.0, 600.0), openings = listOf(Opening.default(1, OpeningKind.Door, 0, 150.0)))),
                ),
            ),
        ),
    )

    @Test
    fun `porte e finestre uguali insieme, con quante sono e dove`() {
        val list = Takeoff.of(house).openings
        // Prima le porte (1 anta, poi 2 ante), poi le finestre; uguali in una riga sola.
        assertEquals(listOf("Porta ad 1 anta", "Porta ad 1 anta", "Porta basculante (garage)", "Finestra 2 ante", "Finestra 2 ante"), list.map { it.name })
        val big = list.single { it.size == "120×140 cm" }
        assertEquals(2, big.count)
        assertEquals("Cucina, Camera", big.where)
        assertEquals("Classica, bianco · persiane · inferriata", big.details)
        assertEquals(1, list.single { it.size == "100×140 cm" }.count)
        // La porta della cucina (a pannelli) e quella del muro singolo (liscia) sono diverse.
        val doors = list.filter { it.name == "Porta ad 1 anta" }
        assertEquals(setOf("A pannelli, rovere", "Liscia, rovere"), doors.map { it.details }.toSet())
        assertEquals("Muro singolo", doors.single { it.details == "Liscia, rovere" }.where)
        // Porta da garage: niente modello d'anta, bianca di serie.
        assertEquals("Bianco", list.single { it.name == "Porta basculante (garage)" }.details)
        // Varchi senza porta e finestre da chiudere non si ordinano.
        assertEquals(6, list.sumOf { it.count })
    }

    @Test
    fun `colori soliti dei modelli e accessori nella descrizione`() {
        fun d(o: Opening) = Openings.description(o)
        val w = Opening.default(1, OpeningKind.Window1, 0, 100.0)
        assertEquals("Loft", d(w.copy(windowModel = WindowModel.Loft)))
        assertEquals("Minimal, antracite", d(w.copy(windowModel = WindowModel.Minimal)))
        assertEquals("Legno con fermavetro, rovere · persiana alla romana", d(w.copy(windowModel = WindowModel.Wood, shading = Shading.RomanShutter)))
        assertEquals("Legno con fermavetro, noce", d(w.copy(windowModel = WindowModel.Wood, color = Finish.Walnut)))
        assertEquals("Classica, bianco · sopraluce · zanzariera · tenda da sole", d(w.copy(topLight = true, insectScreen = true, awning = true)))
        // Il bovindo non ha oscuranti né inferriata.
        assertEquals("Classica, bianco", d(w.copy(kind = OpeningKind.Window2, bay = true, shading = Shading.Shutters, grille = true)))
        assertEquals("", d(Opening.default(1, OpeningKind.Passage, 0, 100.0)))
        val glazedDoor = Opening.default(1, OpeningKind.Door, 0, 100.0).copy(transom = 40.0, sidelight = 30.0, width = 110.0, height = 250.0)
        assertEquals("Liscia, rovere · sopraluce · vetro di fianco", d(glazedDoor))
    }

    @Test
    fun `nel CSV una riga per gruppo, e in inglese`() {
        val csv = Takeoff.csv(Takeoff.of(house))
        assertTrue(csv.contains("PORTE E FINESTRE;Aspetto;Misure del vano;Quantità;Dove"), csv)
        assertTrue(csv.contains("Finestra 2 ante;Classica, bianco · persiane · inferriata;120×140 cm;2;Cucina, Camera"), csv)
        T.lingua = Inglese
        val en = Takeoff.of(house).openings
        assertEquals("Classic, white · shutters · bars", en.single { it.size == "120×140 cm" }.details, en.toString())
    }

    @Test
    fun `con più piani si dice anche il piano`() {
        val two = Building(house.floors + Floor(2, "Primo piano", FloorPlan(listOf(room(9, "Studio", 0.0, window)))))
        val big = Takeoff.of(two).openings.single { it.size == "120×140 cm" }
        assertEquals(3, big.count)
        assertEquals("Cucina (Piano terra), Camera (Piano terra), Studio (Primo piano)", big.where)
    }

    @Test
    fun `davanzali e soglie - lastre uguali insieme, con le misure da ordinare`() {
        val marble = window.copy(sillMaterial = StairMaterial.Marble)
        val plan = FloorPlan(
            listOf(
                // Muri da 15: la lastra è profonda 15 + 2 dentro + 3 fuori; lunga il vano più 3 + 3 di orecchie.
                room(1, "Cucina", 0.0, marble, Opening.default(2, OpeningKind.Balcony2, 2, 200.0).copy(sillMaterial = StairMaterial.Stone)),
                // Con lo sguincio di 10 cm per lato la lastra è più lunga di 20.
                room(2, "Camera", 500.0, marble, marble.copy(id = 2, wallIndex = 2, splay = 10.0), window.copy(id = 3, wallIndex = 1)),
            ),
            // Muro singolo da 10.
            freeWalls = listOf(FreeWall(1, Vec2(0.0, 600.0), Vec2(300.0, 600.0), thickness = 10.0, openings = listOf(Opening.default(1, OpeningKind.Window1, 0, 150.0).copy(sillMaterial = StairMaterial.Marble)))),
        )
        val sills = Takeoff.of(Building.single(plan)).sills
        assertEquals(
            listOf(
                Takeoff.Item("Davanzale in marmo", "126×20×3 cm", 2, "Cucina, Camera"),
                Takeoff.Item("Davanzale in marmo", "146×20×3 cm", 1, "Camera"),
                Takeoff.Item("Davanzale in marmo", "86×15×3 cm", 1, "Muro singolo"),
                Takeoff.Item("Soglia in pietra", "126×20×3 cm", 1, "Cucina"),
            ).sortedBy { it.name },
            sills,
        )
        assertTrue(Takeoff.csv(Takeoff.of(Building.single(plan))).contains("DAVANZALI E SOGLIE;Misure;Quantità;Dove\nDavanzale in marmo;126×20×3 cm;2;Cucina, Camera"))
        // Senza lastre la sezione non c'è.
        assertTrue(Takeoff.of(house).sills.isEmpty())
        assertTrue(!Takeoff.csv(Takeoff.of(house)).contains("DAVANZALI"))
    }
}
