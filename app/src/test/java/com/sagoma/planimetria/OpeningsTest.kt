package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Snapping
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningsTest {

    private fun room(id: Long, w: Double, h: Double, at: Vec2 = Vec2.Zero, openings: List<Opening> = emptyList()) =
        Room(id, "R$id", RoomType.Altro, RoomFactory.rectangle(w, h).map { it + at }, openings = openings)

    @Test
    fun `l'apertura resta dentro il muro`() {
        assertEquals(40.0, Openings.clampPosition(400.0, 80.0, 10.0), 1e-9)
        assertEquals(360.0, Openings.clampPosition(400.0, 80.0, 390.0), 1e-9)
        assertEquals(50.0, Openings.clampPosition(100.0, 150.0, 10.0), 1e-9)
    }

    @Test
    fun `span segue il muro anche dopo averlo accorciato`() {
        val door = Opening.default(1, OpeningKind.Door, 0, 450.0)
        var r = room(1, 500.0, 400.0, openings = listOf(door))
        r = r.copy(points = Snapping.setWallLength(r.points, 0, 300.0))
        val (a, b) = Openings.span(r, door)
        assertEquals(Vec2(220.0, 0.0), a)
        assertEquals(Vec2(300.0, 0.0), b)
    }

    @Test
    fun `la normale interna punta dentro la stanza`() {
        val r = room(1, 500.0, 400.0)
        for (i in 0 until r.wallCount) {
            val mid = (r.wallStart(i) + r.wallEnd(i)) / 2.0
            val probe = mid + Openings.inwardNormal(r, i) * 10.0
            assertTrue(com.sagoma.planimetria.geometry.Polygon.contains(r.points, probe))
        }
    }

    @Test
    fun `porta su muro a contatto crea il varco nella stanza confinante`() {
        // A: 0..400, B: 408..708 → muri paralleli a 8 cm (entro i 12 cm di tolleranza).
        val door = Opening.default(1, OpeningKind.Door, 1, 200.0) // muro destro di A, da y=0 a y=400
        val a = room(1, 400.0, 400.0, openings = listOf(door))
        val b = room(2, 300.0, 300.0, at = Vec2(408.0, 50.0))
        val gaps = Openings.sharedGaps(FloorPlan(listOf(a, b)))
        assertEquals(1, gaps.size)
        val g = gaps.single()
        assertEquals(2L, g.roomId)
        assertEquals(3, g.wallIndex) // muro sinistro di B
        assertEquals(setOf(160.0, 240.0), setOf(g.a.y, g.b.y))
        assertTrue(g.a.x == 408.0 && g.b.x == 408.0)
    }

    @Test
    fun `l'ingombro della pianta include le ante che si aprono verso l'esterno`() {
        val inward = Opening.default(1, OpeningKind.Door, 0, 200.0) // muro superiore, verso l'interno
        val outward = inward.copy(id = 2, position = 400.0, opensInward = false)
        assertEquals(0.0, Openings.planBounds(FloorPlan(listOf(room(1, 500.0, 400.0, openings = listOf(inward)))))!!.minY, 1e-9)
        // Porta da 80 cm verso l'esterno sul muro superiore: sporge di 80 cm + metà muro sopra y = 0.
        val b = Openings.planBounds(FloorPlan(listOf(room(1, 500.0, 400.0, openings = listOf(outward)))))!!
        assertEquals(-(80.0 + 7.5), b.minY, 1e-9)
        assertEquals(400.0, b.maxY, 1e-9)
    }

    @Test
    fun `porte a 2 ante aprono il varco condiviso, balconi e porta-finestra no`() {
        fun gaps(kind: OpeningKind): Int {
            val a = room(1, 400.0, 400.0, openings = listOf(Opening.default(1, kind, 1, 200.0)))
            val b = room(2, 300.0, 300.0, at = Vec2(408.0, 50.0))
            return Openings.sharedGaps(FloorPlan(listOf(a, b))).size
        }
        assertEquals(1, gaps(OpeningKind.Door2))
        assertEquals(0, gaps(OpeningKind.Balcony1))
        assertEquals(0, gaps(OpeningKind.Balcony2))
    }

    @Test
    fun `caratteristiche dei nuovi infissi`() {
        // Scelta del lato solo con un'anta; davanzale per gli infissi vetrati; balconi a terra.
        assertTrue(Opening.default(1, OpeningKind.Door, 0, 0.0).hasSideChoice)
        assertTrue(!Opening.default(1, OpeningKind.Door2, 0, 0.0).hasSideChoice)
        assertTrue(OpeningKind.Balcony2.hasSill && !OpeningKind.Door2.hasSill)
        for (k in listOf(OpeningKind.Balcony1, OpeningKind.Balcony2)) {
            assertEquals(0.0, Opening.default(1, k, 0, 0.0).sillHeight, 1e-9)
        }
        // I tipi offerti (anche scorrevoli) si salvano e si ricaricano identici.
        val offered = OpeningKind.entries.filter { it != OpeningKind.FrenchDoor }
        val openings = offered.mapIndexed { i, k -> Opening.default(i + 1L, k, 0, 200.0).copy(sliding = k.hasLeaves && i % 2 == 0) }
        val plan = FloorPlan(listOf(room(1, 400.0, 400.0, openings = openings)))
        assertEquals(plan, com.sagoma.planimetria.persistence.PlanJson.decode(com.sagoma.planimetria.persistence.PlanJson.encode(plan)))
    }

    @Test
    fun `le ante scorrevoli non hanno arco né ingombro esterno`() {
        val slidingOut = Opening.default(1, OpeningKind.Door, 0, 200.0).copy(opensInward = false, sliding = true)
        assertTrue(!slidingOut.swings)
        // A battente verso l'esterno sporgerebbe di 87,5 cm; scorrevole resta nel muro.
        val b = Openings.planBounds(FloorPlan(listOf(room(1, 500.0, 400.0, openings = listOf(slidingOut)))))!!
        assertEquals(0.0, b.minY, 1e-9)
    }

    @Test
    fun `la porta-finestra dei file vecchi diventa un balcone ad 1 anta`() {
        val old = """{"version":1,"plan":{"rooms":[{"id":1,"name":"S","type":"Altro",""" +
            """"points":[{"x":0.0,"y":0.0},{"x":400.0,"y":0.0},{"x":400.0,"y":400.0},{"x":0.0,"y":400.0}],""" +
            """"openings":[{"id":1,"kind":"FrenchDoor","wallIndex":0,"position":200.0,"width":90.0,"height":220.0}]}]}}"""
        val o = com.sagoma.planimetria.persistence.PlanJson.decode(old).rooms.single().openings.single()
        assertEquals(OpeningKind.Balcony1, o.kind)
        assertEquals(90.0, o.width, 1e-9)
        assertEquals(220.0, o.height, 1e-9)
    }

    @Test
    fun `finestre e muri lontani non creano varchi condivisi`() {
        val window = Opening.default(1, OpeningKind.Window1, 1, 200.0)
        val a = room(1, 400.0, 400.0, openings = listOf(window))
        val b = room(2, 300.0, 300.0, at = Vec2(408.0, 50.0))
        assertTrue(Openings.sharedGaps(FloorPlan(listOf(a, b))).isEmpty())

        val door = Opening.default(1, OpeningKind.Door, 1, 200.0)
        val far = room(3, 300.0, 300.0, at = Vec2(430.0, 50.0))
        assertTrue(Openings.sharedGaps(FloorPlan(listOf(a.copy(openings = listOf(door)), far))).isEmpty())
    }
}
