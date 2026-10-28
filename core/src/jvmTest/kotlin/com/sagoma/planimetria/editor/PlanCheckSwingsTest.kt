package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Controlla la pianta»: ante a battente che aprendosi si urtano, urtano un mobile o un calorifero, o un muro. */
class PlanCheckSwingsTest {
    @AfterTest
    fun italiano() {
        T.lingua = com.sagoma.planimetria.testi.Italiano
    }

    private fun rect(id: Long, name: String, w: Double, h: Double, openings: List<Opening>, type: RoomType = RoomType.Soggiorno) =
        Room(id, name, type, RoomFactory.rectangle(w, h), wallThickness = 10.0, openings = openings)

    private fun swings(plan: FloorPlan) = PlanCheck.of(Building.single(plan)).filter { it.kind.name.startsWith("Swing") }

    private fun door(id: Long, wall: Int, position: Double, hingeLeft: Boolean = true, inward: Boolean = true) =
        Opening.default(id, OpeningKind.Door, wall, position).copy(hingeLeft = hingeLeft, opensInward = inward)

    @Test
    fun `due porte nello stesso angolo si urtano`() {
        // Porta sul muro in alto con l'anta che arriva nell'angolo, porta sul muro a destra incernierata nell'angolo.
        val room = rect(1, "Disimpegno", 300.0, 300.0, listOf(door(1, 0, 250.0), door(2, 1, 50.0)))
        val plan = FloorPlan(listOf(room))
        val i = swings(plan).single()
        assertEquals(PlanCheck.Kind.SwingClash, i.kind)
        assertEquals(Selection.Opening(1, 2), i.selection)
        assertEquals(
            "«Disimpegno»: due ante si urtano aprendosi (porta ad 1 anta e porta ad 1 anta). Cambia il lato del cardine o il verso di apertura.",
            PlanCheck.describe(i, Building.single(plan)),
        )
        // La seconda porta che si apre verso fuori: niente da segnalare.
        assertEquals(emptyList(), swings(FloorPlan(listOf(room.copy(openings = listOf(door(1, 0, 250.0), door(2, 1, 50.0, inward = false)))))))
    }

    @Test
    fun `porta che urta un mobile, non il tappeto`() {
        val room = rect(1, "Camera", 400.0, 300.0, listOf(door(1, 0, 100.0)), RoomType.Camera)
        // Anta incernierata a x = 60: spazza fino a x = 139 e y = 85.
        val wardrobe = Furniture(1, "armadio-di-prova", Vec2(120.0, 50.0), width = 60.0, depth = 40.0, height = 200.0)
        val rug = Furniture(2, "tappeto-di-prova", Vec2(100.0, 50.0), width = 100.0, depth = 80.0, height = 1.0)
        val plan = FloorPlan(listOf(room), furniture = listOf(rug, wardrobe))
        val i = swings(plan).single()
        assertEquals(PlanCheck.Kind.SwingHitsThing, i.kind)
        assertEquals(1L, i.furniture)
        assertEquals(Selection.Opening(1, 1), i.selection)
        assertTrue(PlanCheck.describe(i, Building.single(plan)).startsWith("«Camera»: aprendosi, l'anta urta «"), PlanCheck.describe(i, Building.single(plan)))
        // Il mobile spostato oltre l'anta, o un pensile sopra la porta: niente.
        assertEquals(emptyList(), swings(plan.copy(furniture = listOf(rug, wardrobe.copy(center = Vec2(300.0, 200.0))))))
        assertEquals(emptyList(), swings(plan.copy(furniture = listOf(wardrobe.copy(elevation = 215.0, height = 60.0)))))
    }

    @Test
    fun `porta che urta il calorifero`() {
        // Corridoio fondo 95 (dentro 85): l'anta arriva a 85, il calorifero sul muro di fronte sporge fino a 80.
        val radiator = Fixture.default(1, FixtureKind.Radiator).copy(wallIndex = 2, position = 300.0)
        val hall = rect(1, "Corridoio", 400.0, 95.0, listOf(door(1, 0, 100.0)), RoomType.Corridoio).copy(fixtures = listOf(radiator))
        val plan = FloorPlan(listOf(hall))
        val i = swings(plan).single()
        assertEquals(PlanCheck.Kind.SwingHitsThing, i.kind)
        assertEquals(null, i.furniture)
        assertEquals("«Corridoio»: aprendosi, l'anta urta «Calorifero» (porta ad 1 anta). Sposta «Calorifero» o cambia il lato del cardine o il verso di apertura.", PlanCheck.describe(i, Building.single(plan)))
        // Più in là lungo il muro: l'anta non ci arriva.
        assertEquals(emptyList(), swings(FloorPlan(listOf(hall.copy(fixtures = listOf(radiator.copy(position = 100.0)))))))
    }

    @Test
    fun `porta che non si apre del tutto in un corridoio stretto`() {
        // Dentro 70 cm, anta da 80.
        val hall = rect(1, "Corridoio", 400.0, 80.0, listOf(door(1, 0, 100.0)), RoomType.Corridoio)
        val i = swings(FloorPlan(listOf(hall))).single()
        assertEquals(PlanCheck.Kind.SwingHitsWall, i.kind)
        T.lingua = com.sagoma.planimetria.testi.en.Inglese
        assertEquals(
            "«Corridoio»: the leaf can't open fully, it hits a wall or a column (single door). Move the opening or swap the hinge side or the opening direction.",
            PlanCheck.describe(i, Building.single(FloorPlan(listOf(hall)))),
        )
        // Verso fuori, o con il muro della porta da demolire (nella casa come sarà la porta non c'è): niente.
        assertEquals(emptyList(), swings(FloorPlan(listOf(hall.copy(openings = listOf(door(1, 0, 100.0, inward = false)))))))
        assertEquals(emptyList(), swings(FloorPlan(listOf(hall.copy(wallPhases = mapOf(0 to Phase.Demolish))))))
        // Una porta scorrevole non spazza niente.
        assertEquals(emptyList(), swings(FloorPlan(listOf(hall.copy(openings = listOf(door(1, 0, 100.0).copy(sliding = true)))))))
    }

    @Test
    fun `Correggi gira l anta`() {
        fun fix(plan: FloorPlan): FloorPlan? {
            val b = Building.single(plan)
            val i = swings(plan).first()
            assertTrue(PlanCheck.canFix(i))
            return PlanCheck.fixed(b, i)?.floors?.get(0)?.plan
        }
        // Due porte una di fronte all'altra nel corridoio largo 150, i cardini verso il centro: la seconda (quella che «Mostra»
        // seleziona) con il cardine dall'altra parte; il verso resta (porte che danno fuori).
        val facing = FloorPlan(listOf(rect(1, "Corridoio", 400.0, 150.0, listOf(door(1, 0, 100.0, hingeLeft = false), door(2, 2, 240.0, hingeLeft = false)), RoomType.Corridoio)))
        val turned = assertNotNull(fix(facing), "di fronte")
        assertEquals(emptyList(), swings(turned))
        assertEquals(listOf(false, true), turned.rooms.single().openings.map { it.hingeLeft })
        assertTrue(turned.rooms.single().openings.all { it.opensInward })
        assertTrue(PlanCheck.triesFirst(swings(facing).single()))
        assertTrue(PlanCheck.canFix(Building.single(facing), swings(facing).single()))
        // Nell'angolo il cardine non basta: si toccano comunque, e «Correggi» non c'è.
        val corner = FloorPlan(listOf(rect(1, "Disimpegno", 300.0, 300.0, listOf(door(1, 0, 250.0), door(2, 1, 50.0)))))
        assertNull(fix(corner))
        assertEquals(false, PlanCheck.canFix(Building.single(corner), swings(corner).single()))
        // Già girata (elenco vecchio): niente da fare.
        assertNull(PlanCheck.fixed(Building.single(turned), swings(facing).single()))
        // Corridoio stretto con la camera dall'altra parte del muro: la porta si apre verso la camera.
        val hall = rect(1, "Corridoio", 400.0, 80.0, listOf(door(1, 0, 100.0)), RoomType.Corridoio)
        val bedroom = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(0.0, -300.0) }, wallThickness = 10.0)
        val out = assertNotNull(fix(FloorPlan(listOf(hall, bedroom))), "corridoio").rooms.first().openings.single()
        assertEquals(false, out.opensInward)
        assertEquals(true, out.hingeLeft)
        assertEquals(emptyList(), swings(FloorPlan(listOf(hall.copy(openings = listOf(out)), bedroom))))
        // La porta che dà fuori non si gira verso fuori, e il cardine dall'altra parte non basta: non si corregge da sola.
        assertNull(fix(FloorPlan(listOf(hall))))
        // Il calorifero proprio davanti alla porta: girarla non serve.
        val radiator = Fixture.default(1, FixtureKind.Radiator).copy(wallIndex = 2, position = 300.0)
        assertNull(fix(FloorPlan(listOf(hall.copy(fixtures = listOf(radiator)).let { it.copy(points = RoomFactory.rectangle(400.0, 95.0)) }))))
    }

    @Test
    fun `le due ante della stessa porta e la porta accanto all angolo non contano`() {
        // Porta a due ante in mezzo al muro; porta con il cardine vicino all'angolo (l'anta aperta va lungo il muro accanto).
        val room = rect(1, "Soggiorno", 400.0, 300.0, listOf(Opening.default(1, OpeningKind.Door2, 0, 200.0), door(2, 1, 50.0)))
        assertEquals(emptyList(), swings(FloorPlan(listOf(room))))
    }
}
