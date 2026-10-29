package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/** Punti presa e punti luce minimi per stanza (CEI 64-8, livello 1). */
class ElectricRulesTest {
    private fun rect(id: Long, type: RoomType, w: Double, h: Double, name: String = type.name) =
        Room(id, name, type, RoomFactory.rectangle(w, h), wallThickness = 0.0)

    private fun min(r: Room, plan: FloorPlan = FloorPlan(listOf(r))) = ElectricRules.minimum(plan, r)

    @Test
    fun `stanze secondo la superficie`() {
        // Senza muri: la superficie è quella del rettangolo.
        assertEquals(null, min(rect(1, RoomType.Studio, 400.0, 200.0)))
        assertEquals(ElectricRules.Points(4, 1), min(rect(1, RoomType.Studio, 400.0, 201.0)))
        assertEquals(ElectricRules.Points(4, 1), min(rect(1, RoomType.Soggiorno, 400.0, 300.0)))
        assertEquals(ElectricRules.Points(5, 1), min(rect(1, RoomType.Soggiorno, 400.0, 301.0)))
        assertEquals(ElectricRules.Points(5, 1), min(rect(1, RoomType.Soggiorno, 400.0, 500.0)))
        assertEquals(ElectricRules.Points(6, 2), min(rect(1, RoomType.Soggiorno, 500.0, 500.0)))
        // Camera da letto: un punto presa in meno.
        assertEquals(ElectricRules.Points(4, 1), min(rect(1, RoomType.Camera, 400.0, 400.0)))
        assertEquals(ElectricRules.Points(3, 1), min(rect(1, RoomType.Cameretta, 300.0, 300.0)))
        assertEquals(ElectricRules.Points(5, 1), min(rect(1, RoomType.Cucina, 300.0, 300.0)))
        assertEquals(ElectricRules.Points(1, 1), min(rect(1, RoomType.Corridoio, 500.0, 120.0)))
        assertEquals(ElectricRules.Points(2, 2), min(rect(1, RoomType.Corridoio, 520.0, 120.0)))
        assertEquals(null, min(rect(1, RoomType.Balcone, 400.0, 200.0)))
        assertEquals(ElectricRules.Points(1, 1), min(rect(1, RoomType.Terrazza, 500.0, 200.0)))
        assertEquals(null, min(rect(1, RoomType.Altro, 400.0, 400.0)))
    }

    @Test
    fun `bagno, bagno con la lavatrice e bagno di servizio`() {
        val saved = FurnitureCatalog.items
        try {
            fun item(model: String, label: String, category: String) = FurnitureCatalog.Item(model = model, label = label, category = category, width = 60.0, depth = 60.0, height = 85.0)
            FurnitureCatalog.set(listOf(item("lavatrice", "Lavatrice frontale", "Cucina"), item("wc", "WC sospeso", "Bagno"), item("lavabo", "Lavabo sospeso", "Bagno"), item("doccia", "Box doccia", "Bagno")))
            fun f(id: Long, model: String) = Furniture(id, model, Vec2(50.0 + 60 * id, 100.0), width = 50.0, depth = 50.0, height = 50.0)
            val bath = rect(1, RoomType.Bagno, 300.0, 250.0, "Bagno")
            assertEquals(ElectricRules.Points(1, 2), min(bath))
            assertEquals(ElectricRules.Points(2, 2), min(bath, FloorPlan(listOf(bath), furniture = listOf(f(1, "lavatrice")))))
            // Solo vaso e lavabo, o il nome lo dice: bagno di servizio.
            assertEquals(ElectricRules.Points(1, 1), min(bath, FloorPlan(listOf(bath), furniture = listOf(f(1, "wc"), f(2, "lavabo")))))
            assertEquals(ElectricRules.Points(1, 2), min(bath, FloorPlan(listOf(bath), furniture = listOf(f(1, "wc"), f(2, "lavabo"), f(3, "doccia")))))
            assertEquals(ElectricRules.Points(1, 1), min(rect(1, RoomType.Bagno, 200.0, 150.0, "W.c.")))
        } finally {
            FurnitureCatalog.set(saved)
        }
    }

    @Test
    fun `punti messi nella stanza`() {
        val r = rect(1, RoomType.Camera, 400.0, 400.0).copy(
            fixtures = listOf(
                Fixture.default(1, FixtureKind.Outlet), Fixture.default(2, FixtureKind.Outlet), Fixture.default(3, FixtureKind.Switch),
                Fixture.default(4, FixtureKind.CeilingLight), Fixture.default(5, FixtureKind.WallSpot), Fixture.default(6, FixtureKind.Radiator),
            ),
        )
        assertEquals(ElectricRules.Points(2, 2), ElectricRules.points(r))
        assertEquals(true, ElectricRules.wired(listOf(r)))
        assertEquals(false, ElectricRules.wired(listOf(r.copy(fixtures = r.fixtures.filter { it.kind != FixtureKind.Outlet }))))
    }
}
