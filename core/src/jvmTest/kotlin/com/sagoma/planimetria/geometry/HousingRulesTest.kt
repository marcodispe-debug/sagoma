package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.ExcludedZone
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.SashType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WindowShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Luce e aria delle stanze: quali finestre contano, con che superficie, e quali stanze fanno un ambiente solo. */
class HousingRulesTest {
    private fun rect(id: Long, type: RoomType, w: Double, h: Double, at: Vec2 = Vec2.Zero, openings: List<Opening> = emptyList()) =
        Room(id, type.name, type, RoomFactory.rectangle(w, h).map { it + at }, openings = openings)

    private val window = Opening.default(1, OpeningKind.Window2, 2, 200.0)

    @Test
    fun `finestre che contano`() {
        val room = rect(1, RoomType.Camera, 400.0, 400.0, openings = listOf(window))
        val plan = FloorPlan(listOf(room))
        assertEquals(14_400.0, HousingRules.windowArea(plan, listOf(room)), 1e-6)
        assertEquals(385.0 * 385.0, HousingRules.floorArea(listOf(room)), 1e-6)
        assertEquals("1/10,3", HousingRules.fraction(HousingRules.ratio(plan, listOf(room))!!))
        assertTrue(HousingRules.belowMinimum(plan, listOf(room)))
        // Non contano: fissa, da chiudere, su un muro da demolire o tolto, porte piene, nicchie.
        for (o in listOf(
            window.copy(sash = SashType.Fixed),
            window.copy(phase = Phase.Demolish),
            Opening.default(2, OpeningKind.Door, 2, 200.0),
            Opening.default(3, OpeningKind.Niche, 2, 200.0),
        )) assertEquals(0.0, HousingRules.windowArea(FloorPlan(listOf(room.copy(openings = listOf(o)))), listOf(room.copy(openings = listOf(o)))), "$o")
        val demolished = room.copy(wallPhases = mapOf(2 to Phase.Demolish))
        assertEquals(0.0, HousingRules.windowArea(FloorPlan(listOf(demolished)), listOf(demolished)))
        val removed = room.copy(removedWalls = setOf(2))
        assertEquals(0.0, HousingRules.windowArea(FloorPlan(listOf(removed)), listOf(removed)))
        // Contano: fissa ma scorrevole (l'altra anta si apre), nuova, porta-finestra.
        for (o in listOf(window.copy(sash = SashType.Fixed, sliding = true), window.copy(phase = Phase.New), Opening.default(4, OpeningKind.Balcony2, 2, 200.0))) {
            val r = room.copy(openings = listOf(o))
            assertEquals(o.width * o.height, HousingRules.windowArea(FloorPlan(listOf(r)), listOf(r)), 1e-6, "$o")
        }
        // Oblò: un cerchio, non il quadrato.
        val round = room.copy(openings = listOf(window.copy(width = 80.0, height = 80.0, windowShape = WindowShape.Round)))
        assertEquals(Math.PI * 40 * 40, HousingRules.windowArea(FloorPlan(listOf(round)), listOf(round)), 30.0)
        // Le zone non calpestabili non sono pavimento.
        val zone = ExcludedZone(1, RoomFactory.rectangle(100.0, 100.0).map { it + Vec2(50.0, 50.0) })
        assertEquals(385.0 * 385.0 - 10_000, HousingRules.floorArea(listOf(room.copy(excludedZones = listOf(zone)))), 1e-6)
    }

    @Test
    fun `finestra verso un'altra stanza o verso il balcone`() {
        val room = rect(1, RoomType.Camera, 400.0, 400.0, openings = listOf(window))
        val below = rect(2, RoomType.Corridoio, 400.0, 200.0, Vec2(0.0, 400.0))
        assertEquals(0.0, HousingRules.windowArea(FloorPlan(listOf(room, below)), listOf(room)), "dà sul corridoio")
        val balcony = below.copy(type = RoomType.Balcone)
        assertEquals(14_400.0, HousingRules.windowArea(FloorPlan(listOf(room, balcony)), listOf(room)), 1e-6, "dà sul balcone")
    }

    @Test
    fun `ambiente unico dove tutte e due le stanze hanno tolto il muro`() {
        val living = rect(1, RoomType.Soggiorno, 400.0, 300.0).copy(removedWalls = setOf(1))
        val kitchen = rect(2, RoomType.Cucina, 300.0, 300.0, Vec2(400.0, 0.0)).copy(removedWalls = setOf(3))
        val hall = rect(3, RoomType.Corridoio, 200.0, 300.0, Vec2(700.0, 0.0)).copy(removedWalls = setOf(3))
        val plan = FloorPlan(listOf(living, kitchen, hall))
        assertEquals(listOf(1L, 2L), HousingRules.space(plan, living).map { it.id })
        assertEquals(listOf(2L, 1L), HousingRules.space(plan, kitchen).map { it.id })
        // La cucina ha il muro verso il corridoio: il corridoio aperto da solo non si unisce.
        assertEquals(listOf(3L), HousingRules.space(plan, hall).map { it.id })
        // Con anche la cucina aperta verso il corridoio: tre stanze, anche partendo dal soggiorno.
        val open = plan.copy(rooms = listOf(living, kitchen.copy(removedWalls = setOf(1, 3)), hall))
        assertEquals(setOf(1L, 2L, 3L), HousingRules.space(open, living).map { it.id }.toSet())
        // Muro in comune da demolire (stato di progetto): anche così diventano un ambiente solo.
        val project = FloorPlan(listOf(living.copy(removedWalls = emptySet(), wallPhases = mapOf(1 to Phase.Demolish)), kitchen.copy(removedWalls = emptySet(), wallPhases = mapOf(3 to Phase.Demolish))))
        assertEquals(listOf(1L, 2L), HousingRules.space(project, project.rooms[0]).map { it.id })
        // Gli ambienti da abitare del piano, una volta sola, il soggiorno per primo; il corridoio da solo no.
        assertEquals(listOf(listOf(1L, 2L)), HousingRules.livingSpaces(plan.copy(rooms = listOf(kitchen, living, hall))).map { s -> s.map { it.id } })
        // Una stanza tutta da demolire non c'è più.
        val gone = kitchen.copy(wallPhases = (0 until 4).associateWith { Phase.Demolish })
        assertTrue(HousingRules.demolished(gone))
        assertEquals(listOf(listOf(1L)), HousingRules.livingSpaces(FloorPlan(listOf(living, gone))).map { s -> s.map { it.id } })
        // Il balcone non è mai parte dell'ambiente.
        val balcony = rect(4, RoomType.Balcone, 400.0, 150.0, Vec2(0.0, 300.0)).copy(removedWalls = setOf(0))
        val withBalcony = FloorPlan(listOf(living.copy(removedWalls = setOf(1, 2)), kitchen, balcony))
        assertEquals(listOf(1L, 2L), HousingRules.space(withBalcony, withBalcony.rooms[0]).map { it.id })
    }

    @Test
    fun `soffitto piano o in pendenza`() {
        val room = rect(1, RoomType.Camera, 400.0, 400.0)
        assertTrue(HousingRules.flatCeiling(room))
        assertTrue(HousingRules.flatCeiling(room.copy(wallHeights = mapOf(0 to 270.0))))
        assertFalse(HousingRules.flatCeiling(room.copy(wallHeights = mapOf(0 to 180.0))))
        assertFalse(HousingRules.flatCeiling(room.copy(wallCuts = mapOf(0 to com.sagoma.planimetria.model.WallCut(true, 100.0)))))
    }

    @Test
    fun `sanitari del bagno e metri quadri per persona`() {
        val saved = com.sagoma.planimetria.model.FurnitureCatalog.items
        try {
            fun item(model: String, label: String, category: String = "Bagno") =
                com.sagoma.planimetria.model.FurnitureCatalog.Item(model = model, label = label, category = category, width = 40.0, depth = 40.0, height = 40.0)
            com.sagoma.planimetria.model.FurnitureCatalog.set(
                listOf(
                    item("wc", "WC sospeso"), item("bide", "Bidè"), item("porta", "Porta doccia"), item("vasca", "Vasca ad angolo"),
                    item("lavandino", "Lavandino con specchio"), item("lavello", "Lavello nero a una vasca", "Cucina"), item("tappeto", "Tappetino grigio"),
                ),
            )
            fun f(model: String, x: Double = 100.0) = com.sagoma.planimetria.model.Furniture(1, model, Vec2(x, 100.0), width = 40.0, depth = 40.0, height = 40.0)
            assertEquals(HousingRules.Sanitary.Toilet, HousingRules.sanitary(f("wc")))
            assertEquals(HousingRules.Sanitary.Bidet, HousingRules.sanitary(f("bide")))
            assertEquals(HousingRules.Sanitary.BathOrShower, HousingRules.sanitary(f("porta")))
            assertEquals(HousingRules.Sanitary.BathOrShower, HousingRules.sanitary(f("vasca")))
            assertEquals(HousingRules.Sanitary.Basin, HousingRules.sanitary(f("lavandino")))
            // Il lavello della cucina «a una vasca» e il tappetino non sono sanitari.
            assertEquals(null, HousingRules.sanitary(f("lavello")))
            assertEquals(null, HousingRules.sanitary(f("tappeto")))
            // Nella stanza contano gli arredi col centro dentro.
            val bath = rect(1, RoomType.Bagno, 200.0, 250.0)
            val plan = FloorPlan(listOf(bath), furniture = listOf(f("wc"), f("lavandino"), f("bide", 500.0)))
            assertEquals(setOf(HousingRules.Sanitary.Toilet, HousingRules.Sanitary.Basin), HousingRules.sanitaries(plan, bath))
        } finally {
            com.sagoma.planimetria.model.FurnitureCatalog.set(saved)
        }
        assertEquals(140_000.0, HousingRules.minHome(1))
        assertEquals(560_000.0, HousingRules.minHome(4))
        assertEquals(760_000.0, HousingRules.minHome(6))
        assertEquals(280_000.0, HousingRules.minStudio(1))
        assertEquals(380_000.0, HousingRules.minStudio(2))
    }
}
