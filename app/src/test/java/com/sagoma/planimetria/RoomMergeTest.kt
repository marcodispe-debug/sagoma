package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.RoomMerge
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMergeTest {

    private fun room(id: Long, w: Double, h: Double, at: Vec2) =
        Room(id, "R$id", RoomType.Altro, RoomFactory.rectangle(w, h).map { it + at })

    @Test
    fun `due stanze affiancate diventano un rettangolo unico`() {
        // R1 0..300, R2 300..600: muro destro di R1 (1) in comune col sinistro di R2 (3).
        val door = Opening.default(1, OpeningKind.Door, 1, 200.0) // sul muro eliminato
        val window = Opening.default(2, OpeningKind.Window1, 0, 150.0) // muro in alto di R2
        val radiator = Fixture.default(1, FixtureKind.Radiator).copy(wallIndex = 2, position = 100.0)
        val light = Fixture.default(2, FixtureKind.CeilingLight).copy(point = Vec2(450.0, 200.0))
        val plan = FloorPlan(
            listOf(
                room(1, 300.0, 400.0, Vec2.Zero).copy(openings = listOf(door), wallHeights = mapOf(0 to 250.0)),
                room(2, 300.0, 400.0, Vec2(300.0, 0.0)).copy(openings = listOf(window), fixtures = listOf(radiator, light)),
            ),
        )
        val r = RoomMerge.merge(plan, 1, 1)!!
        assertEquals(1, r.plan.rooms.size)
        val m = r.plan.rooms.single()
        assertEquals(1L, m.id)
        assertEquals(4, m.wallCount)
        assertEquals(600.0 * 400.0, Polygon.area(m.points), 1e-6)
        assertTrue(Polygon.signedArea(m.points) > 0)
        assertEquals(1, r.removedOpenings)
        assertEquals(0, r.removedFixtures)

        // La finestra resta sopra, a 150 cm dall'inizio del tratto di R2 (x = 450).
        val w = m.openings.single()
        val (a, b) = Openings.span(m, w)
        assertEquals(450.0, (a.x + b.x) / 2, 1e-6)
        assertEquals(0.0, a.y, 1e-6)
        // Il calorifero resta sul muro in basso di R2, la plafoniera dove era.
        val rad = m.fixtures.first { it.kind == FixtureKind.Radiator }
        assertEquals(400.0, m.wallStart(rad.wallIndex).y, 1e-6)
        assertEquals(400.0, m.wallEnd(rad.wallIndex).y, 1e-6)
        assertEquals(Vec2(450.0, 200.0), m.fixtures.first { it.kind == FixtureKind.CeilingLight }.point)
        // L'altezza personalizzata del muro in alto di R1 vale per il muro in alto unito.
        val top = (0 until m.wallCount).first { m.wallStart(it).y == 0.0 && m.wallEnd(it).y == 0.0 }
        assertEquals(250.0, m.wallHeight(top), 1e-9)
    }

    @Test
    fun `muro in comune solo in parte - resta il tratto non condiviso`() {
        // R2 più bassa: 300..600 × 0..200. Il muro destro di R1 resta da y = 200 a 400.
        val plan = FloorPlan(listOf(room(1, 300.0, 400.0, Vec2.Zero), room(2, 300.0, 200.0, Vec2(300.0, 0.0))))
        val m = RoomMerge.merge(plan, 1, 1)!!.plan.rooms.single()
        assertEquals(6, m.wallCount)
        assertEquals(300.0 * 400.0 + 300.0 * 200.0, Polygon.area(m.points), 1e-6)
        assertTrue(m.points.contains(Vec2(300.0, 200.0)) && m.points.contains(Vec2(300.0, 400.0)))
    }

    @Test
    fun `stanza salvata in senso antiorario - si unisce e le aperture restano uguali`() {
        val ccw = room(2, 300.0, 400.0, Vec2(300.0, 0.0)).let { it.copy(points = it.points.reversed()) }
        // Nel contorno invertito il muro in alto (y = 0) è quello da (600,0) a (300,0).
        val top = (0 until ccw.wallCount).first { ccw.wallStart(it).y == 0.0 && ccw.wallEnd(it).y == 0.0 }
        val door = Opening.default(5, OpeningKind.Door, top, 100.0) // centro a x = 500
        val plan = FloorPlan(listOf(room(1, 300.0, 400.0, Vec2.Zero), ccw.copy(openings = listOf(door))))
        val m = RoomMerge.merge(plan, 1, 1)!!.plan.rooms.single()
        val d = m.openings.single()
        val (a, b) = Openings.span(m, d)
        assertEquals(500.0, (a.x + b.x) / 2, 1e-6)
        // Il muro nuovo va nel verso opposto: cardine e verso di apertura si invertono.
        assertEquals(!door.hingeLeft, d.hingeLeft)
        assertEquals(!door.opensInward, d.opensInward)
    }

    @Test
    fun `muro in comune con due stanze - si unisce con quella scelta`() {
        // Sotto la stanza 600×400 due camere affiancate: 0..300 e 300..600.
        val plan = FloorPlan(
            listOf(
                room(1, 600.0, 400.0, Vec2.Zero),
                room(2, 300.0, 300.0, Vec2(0.0, 400.0)),
                room(3, 300.0, 300.0, Vec2(300.0, 400.0)),
            ),
        )
        assertEquals(listOf(2L, 3L), RoomMerge.mergeablePartners(plan, 1, 2).map { it.id }.sorted())
        val p = RoomMerge.merge(plan, 1, 2, otherId = 3)!!.plan
        assertEquals(listOf(1L, 2L), p.rooms.map { it.id })
        assertEquals(600.0 * 400.0 + 300.0 * 300.0, Polygon.area(p.room(1)!!.points), 1e-6)
        // La camera rimasta confina ancora con il nuovo ambiente e si può unire anche lei.
        val wall = (0 until p.room(1)!!.wallCount).first { RoomMerge.mergeablePartners(p, 1, it).isNotEmpty() }
        val all = RoomMerge.merge(p, 1, wall, otherId = 2)!!.plan
        assertEquals(600.0 * 700.0, Polygon.area(all.rooms.single().points), 1e-6)
        assertEquals(4, all.rooms.single().wallCount)
    }

    @Test
    fun `stanze che si toccano solo in un angolo non hanno muro in comune`() {
        val plan = FloorPlan(listOf(room(1, 300.0, 400.0, Vec2.Zero), room(2, 300.0, 400.0, Vec2(300.0, 400.0))))
        for (i in 0 until 4) assertNull(RoomMerge.merge(plan, 1, i))
    }

    @Test
    fun `muri laterali sfalsati di pochi cm diventano un unico muro dritto`() {
        // Sotto la stanza 500×400 ce n'è una da x = 3: il muro sinistro avrebbe un gradino di 3 cm.
        val plan = FloorPlan(listOf(room(1, 500.0, 400.0, Vec2.Zero), room(2, 300.0, 300.0, Vec2(3.0, 400.0))))
        val m = RoomMerge.merge(plan, 1, 2)!!.plan.rooms.single()
        assertEquals(6, m.wallCount)
        assertTrue(m.points.contains(Vec2(0.0, 700.0)) && m.points.contains(Vec2(0.0, 0.0)))
        assertTrue(m.points.none { it.x == 3.0 })
    }

    @Test
    fun `muri a pochi cm - il contorno unito resta squadrato`() {
        val plan = FloorPlan(listOf(room(1, 300.0, 400.0, Vec2.Zero), room(2, 300.0, 400.0, Vec2(305.0, 0.0))))
        val m = RoomMerge.merge(plan, 1, 1)
        assertNotNull(m)
        val pts = m!!.plan.rooms.single().points
        assertEquals(4, pts.size)
        assertTrue(pts.all { it.y == 0.0 || it.y == 400.0 })
    }
}
