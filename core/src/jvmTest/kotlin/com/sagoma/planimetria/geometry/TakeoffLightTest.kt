package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Computo: la tabella «Luce e aria» (rapporto aeroilluminante) delle stanze da abitare, anche nel CSV. */
class TakeoffLightTest {
    private fun rect(id: Long, name: String, type: RoomType, w: Double, h: Double, at: Vec2 = Vec2.Zero, openings: List<Opening> = emptyList()) =
        Room(id, name, type, RoomFactory.rectangle(w, h).map { it + at }, openings = openings)

    private val living = rect(1, "Soggiorno", RoomType.Soggiorno, 400.0, 300.0, openings = listOf(Opening.default(1, OpeningKind.Balcony2, 0, 200.0)))
    private val kitchen = rect(2, "Cucina", RoomType.Cucina, 300.0, 300.0, Vec2(400.0, 0.0), openings = listOf(Opening.default(2, OpeningKind.Window1, 0, 150.0)))
    private val bedroom = rect(3, "Camera", RoomType.Camera, 400.0, 400.0, Vec2(0.0, 300.0), openings = listOf(Opening.default(3, OpeningKind.Window2, 2, 200.0)))
    private val bath = rect(4, "Bagno", RoomType.Bagno, 300.0, 250.0, Vec2(400.0, 300.0), openings = listOf(Opening.default(4, OpeningKind.Window1, 1, 120.0)))

    @Test
    fun `una riga per ambiente da abitare`() {
        val res = Takeoff.of(Building.single(FloorPlan(listOf(living, kitchen, bedroom, bath))))
        // Il bagno non è una stanza da abitare.
        assertEquals(listOf("Soggiorno", "Cucina", "Camera"), res.light.map { it.rooms })
        val l = res.light[0]
        assertEquals(385.0 * 285.0 / 10_000, l.floorArea, 1e-9)
        assertEquals(120.0 * 240.0 / 10_000, l.windowArea, 1e-9)
        assertTrue(l.enough)
        // Camera 14,82 m² con 1,44 m² di finestre: 1/10,3, non basta.
        val c = res.light[2]
        assertFalse(c.enough)
        assertEquals("1/10,3", HousingRules.fraction(c.ratio))
        val csv = Takeoff.csv(res)
        assertTrue(csv.contains("LUCE E ARIA\nPiano;Stanza;Pavimento m²;Finestre apribili m²;Rapporto;Almeno 1/8\n"), csv)
        assertTrue(csv.contains("Piano terra;Camera;14,82;1,44;1/10,3;no\n"), csv)
        assertTrue(csv.contains("Piano terra;Soggiorno;10,97;2,88;1/3,8;sì\n"), csv)
    }

    @Test
    fun `cucina a vista e stato di progetto`() {
        // Il muro tra soggiorno e cucina si demolisce: nel progetto è un ambiente solo, con le finestre di tutti e due.
        val l = living.copy(wallPhases = mapOf(1 to Phase.Demolish))
        val k = kitchen.copy(wallPhases = mapOf(3 to Phase.Demolish))
        val res = Takeoff.of(Building.single(FloorPlan(listOf(l, k))))
        val one = res.light.single()
        assertEquals("Soggiorno + Cucina", one.rooms)
        assertEquals((120.0 * 240 + 80.0 * 120) / 10_000, one.windowArea, 1e-9)
        // Una finestra da chiudere non conta, una da aprire sì.
        val closed = Takeoff.of(Building.single(FloorPlan(listOf(bedroom.copy(openings = bedroom.openings.map { it.copy(phase = Phase.Demolish) })))))
        assertEquals(0.0, closed.light.single().windowArea)
        // Una camera tutta da demolire non c'è più.
        val gone = bedroom.copy(wallPhases = (0 until 4).associateWith { Phase.Demolish })
        assertTrue(Takeoff.of(Building.single(FloorPlan(listOf(gone)))).light.isEmpty())
        // Senza stanze da abitare il CSV non ha la tabella.
        assertFalse(Takeoff.csv(Takeoff.of(Building.single(FloorPlan(listOf(bath))))).contains("LUCE E ARIA"))
    }
}
