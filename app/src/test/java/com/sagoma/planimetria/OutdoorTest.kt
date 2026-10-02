package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutdoorTest {

    // Camera 400 × 400; a destra un balcone 400 × 150 appoggiato al suo muro (x = 400).
    private val bedroom = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 400.0))
    private val balcony = Room(2, "Balcone", RoomType.Balcone, RoomFactory.rectangle(150.0, 400.0).map { it + Vec2(400.0, 0.0) })
    private val down = Vec3(0.0, -1.0, 0.0)

    @Test
    fun `il balcone non ha soffitto e il suo bordo è il parapetto`() {
        assertTrue(balcony.outdoor)
        assertTrue(Ceilings.triangles(balcony).isEmpty())
        assertEquals(0.0, Ceilings.volumeM3(balcony), 0.0)
        assertEquals(100.0, Ceilings.wallTopAt(balcony, 1, 50.0), 0.0)
        assertTrue(Ceilings.cutOptions(balcony.copy(wallHeights = mapOf(0 to 80.0)), 1).isEmpty())
    }

    @Test
    fun `in 3D parapetto verso l'esterno, muro della casa contro la camera`() {
        for (parapet in Parapet.entries) {
            val b = balcony.copy(parapet = parapet)
            val scene = Scene3D.build(FloorPlan(listOf(bedroom, b)), null, null, ceilings = true)
            // Lato esterno del balcone (x = 550): alto quanto il parapetto.
            val edge = scene.hit(Vec3(550.0, 1000.0, 200.0), down)!!
            assertEquals("$parapet", 100.0, edge.point.y, 0.5)
            assertEquals(Pick.Wall(2, 1), edge.pick)
            // Muro tra camera e balcone: a tutta altezza, da entrambe le parti.
            for (x in listOf(397.0, 403.0)) assertEquals("$parapet $x", 270.0, scene.hit(Vec3(x, 1000.0, 200.0), down)!!.point.y, 0.5)
        }
    }

    @Test
    fun `avvisi - una finestra sul parapetto non ci sta, una presa sul muro della casa sì`() {
        val window = Opening.default(1, OpeningKind.Window1, 1, 200.0)
        val outlet = Fixture.default(1, FixtureKind.Outlet).copy(wallIndex = 3, position = 200.0, elevation = 150.0)
        val b = balcony.copy(openings = listOf(window), fixtures = listOf(outlet))
        val hits = Collisions.of(FloorPlan(listOf(bedroom, b)))
        assertEquals(setOf(1L), hits.mapNotNull { it.openingId }.toSet())
        assertTrue(hits.none { it.fixtureId != null })
        // La porta-finestra della camera verso il balcone non dà avvisi.
        val door = Opening.default(1, OpeningKind.Balcony1, 1, 200.0)
        assertTrue(Collisions.of(FloorPlan(listOf(bedroom.copy(openings = listOf(door)), balcony))).isEmpty())
    }

    @Test
    fun `un balcone nuovo nasce appoggiato al muro destro della casa, con il lato lungo lungo il muro`() {
        // Casa a L: la camera sporge più a destra solo in basso (y 300..700).
        val kitchen = Room(3, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0))
        val room = Room(4, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0).map { it + Vec2(0.0, 300.0) })
        val pts = RoomFactory.placeAgainstHouse(FloorPlan(listOf(kitchen, room)), RoomFactory.rectangle(400.0, 150.0))
        val b = com.sagoma.planimetria.geometry.Polygon.bounds(pts)
        assertEquals(500.0, b.minX, 1e-9)
        assertEquals(300.0, b.minY, 1e-9)
        assertEquals(150.0, b.width, 1e-9)
        assertEquals(400.0, b.height, 1e-9)
    }

    @Test
    fun `parapetto salvato e riletto`() {
        val plan = FloorPlan(listOf(bedroom, balcony.copy(type = RoomType.Terrazza, parapet = Parapet.Glass, parapetHeight = 110.0)))
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
    }
}
