package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.Finish
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LampModel
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.RadiatorModel
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.StairStructure
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    private val rise = 300.0
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 500.0))

    private fun triangles(plan: FloorPlan) = Scene3D.build(plan, null, null, ceilings = false).opaque.size / (3 * Scene3D.FLOATS_PER_VERTEX)

    @Test
    fun `scala a L con ventaglio - tre gradini a spicchio nell'angolo al posto del pianerottolo`() {
        val l = Stairs.layout(Stair(1, StairKind.LWinder, Vec2.Zero), rise)
        // 17 alzate: 16 gradini, nessun pianerottolo.
        assertEquals(16, l.steps.size)
        assertTrue(l.steps.none { it.landing })
        assertTrue(l.steps.zipWithNext().all { (a, b) -> b.top > a.top })
        // I tre spicchi riempiono il quadrato dell'angolo (90 × 90).
        val t1 = Stairs.firstFlightOf(Stair(1, StairKind.LWinder, Vec2.Zero), 17)
        val wedges = l.steps.subList(t1, t1 + 3)
        assertEquals(90.0 * 90.0, wedges.sumOf { Polygon.area(it.polygon) }, 1e-6)
        // Stesso ingombro della scala a L con lo stesso numero di pedate nelle rampe.
        assertEquals(Stairs.secondFlightOf(Stair(1, StairKind.LWinder, Vec2.Zero), 17), 13 - t1)
    }

    @Test
    fun `scala dritta con pianerottolo - le rampe sono una dietro l'altra`() {
        val s = Stair(1, StairKind.StraightLanding, Vec2.Zero, firstFlight = 6)
        val l = Stairs.layout(s, rise)
        assertEquals(1, l.steps.count { it.landing })
        assertEquals(90.0, l.bounds.width, 1e-9)
        assertEquals(15 * 28.0 + 90.0, l.bounds.height, 1e-9) // 6 + 9 pedate e il pianerottolo
        assertEquals(6, l.steps.indexOfFirst { it.landing })
    }

    @Test
    fun `tutti i modelli di scala si costruiscono in 3D, la ringhiera aggiunge elementi`() {
        for (kind in StairKind.entries) for (structure in StairStructure.entries) for (railing in StairRailing.entries) {
            val s = Stair(1, kind, Vec2(300.0, 250.0), structure = structure, railing = railing, material = StairMaterial.Marble)
            val scene = Scene3D.build(FloorPlan(listOf(room), stairs = listOf(s)), null, null, ceilings = false, levelHeight = rise)
            assertTrue("$kind $structure $railing", scene.opaque.isNotEmpty())
        }
        val bare = triangles(FloorPlan(listOf(room), stairs = listOf(Stair(1, StairKind.UTurn, Vec2(300.0, 250.0)))))
        val railed = triangles(FloorPlan(listOf(room), stairs = listOf(Stair(1, StairKind.UTurn, Vec2(300.0, 250.0), railing = StairRailing.Metal))))
        assertTrue(railed > bare)
    }

    @Test
    fun `la ringhiera lascia libere la partenza e l'arrivo, ma c'è sui fianchi`() {
        val down = com.sagoma.planimetria.geometry.Vec3(0.0, -1.0, 0.0)
        val kinds = listOf(StairKind.Straight, StairKind.StraightLanding, StairKind.LTurn, StairKind.LWinder, StairKind.UTurn)
        for (kind in kinds) for (railing in listOf(StairRailing.Metal, StairRailing.Glass, StairRailing.Wood)) for (rotation in listOf(0.0, 90.0, 180.0, 270.0)) {
            val s = Stair(1, kind, Vec2(450.0, 450.0), rotation = rotation, railing = railing, turnLeft = rotation >= 180)
            val l = Stairs.layout(s, rise)
            val big = room.copy(points = RoomFactory.rectangle(900.0, 900.0))
            val scene = Scene3D.build(FloorPlan(listOf(big), stairs = listOf(s)), null, null, ceilings = false, levelHeight = rise)
            // Davanti al primo gradino (bordo di partenza) e sul bordo di arrivo non c'è niente sopra il gradino.
            val first = l.steps.first().polygon
            val last = l.steps.last().polygon
            val dir = (l.path[1] - l.path[0]).normalized()
            val endDir = (l.path.last() - l.path[l.path.size - 2]).normalized()
            fun edgeMid(poly: List<Vec2>, d: Vec2, pick: (Double, Double) -> Boolean): Vec2 = poly.indices
                .map { (poly[it] + poly[(it + 1) % poly.size]) / 2.0 }
                .reduce { a, b -> if (pick(a dot d, b dot d)) a else b }
            val start = edgeMid(first, dir) { a, b -> a < b }
            val end = edgeMid(last, endDir) { a, b -> a > b }
            for ((p, top) in listOf(start + dir * 1.0 to l.steps.first().top, end - endDir * 1.0 to l.steps.last().top)) {
                val hit = scene.hit(com.sagoma.planimetria.geometry.Vec3(p.x, 1000.0, p.y), down)!!
                assertEquals("$kind $railing $rotation $p", top, hit.point.y, 1.0)
            }
            if (kind != StairKind.Straight) continue
            // Sul fianco, a metà scala, c'è il corrimano sopra il gradino.
            val mid = l.steps[l.steps.size / 2]
            val side = mid.polygon.indices.map { (mid.polygon[it] + mid.polygon[(it + 1) % mid.polygon.size]) / 2.0 }
                .maxBy { kotlin.math.abs((it - (mid.polygon.reduce { a, b -> a + b } / 4.0)) dot dir.perp()) }
            val hit = scene.hit(com.sagoma.planimetria.geometry.Vec3(side.x, 1000.0, side.y), down)!!
            assertTrue("$railing $rotation fianco", hit.point.y > mid.top + 60)
        }
    }

    @Test
    fun `porte, finestre, termosifoni e lampadari - ogni modello ha il suo aspetto`() {
        fun withDoor(model: DoorModel) = FloorPlan(listOf(room.copy(openings = listOf(Opening.default(1, OpeningKind.Door, 0, 300.0).copy(doorModel = model)))))
        val flush = triangles(withDoor(DoorModel.Flush))
        for (m in DoorModel.entries) if (m != DoorModel.Flush) assertTrue("$m", triangles(withDoor(m)) != flush)
        fun withWindow(model: WindowModel, shading: Shading) = FloorPlan(
            listOf(room.copy(openings = listOf(Opening.default(1, OpeningKind.Window2, 0, 300.0).copy(windowModel = model, shading = shading)))),
        )
        val plain = triangles(withWindow(WindowModel.Classic, Shading.None))
        assertTrue(triangles(withWindow(WindowModel.English, Shading.None)) > plain)
        assertTrue(triangles(withWindow(WindowModel.Classic, Shading.Shutters)) > plain)
        assertTrue(triangles(withWindow(WindowModel.Classic, Shading.RollerShutter)) > plain)
        for (m in RadiatorModel.entries) {
            val f = Fixture.default(1, FixtureKind.Radiator).copy(wallIndex = 0, position = 200.0, radiatorModel = m)
            assertTrue("$m", triangles(FloorPlan(listOf(room.copy(fixtures = listOf(f))))) > triangles(FloorPlan(listOf(room))))
        }
        for (m in LampModel.entries) {
            val f = Fixture.default(1, FixtureKind.Chandelier).copy(point = Vec2(300.0, 250.0), lampModel = m)
            assertTrue("$m", Scene3D.build(FloorPlan(listOf(room.copy(fixtures = listOf(f)))), null, null, ceilings = false).opaque.isNotEmpty())
        }
    }

    @Test
    fun `pavimenti con le fughe dentro la stanza`() {
        val none = triangles(FloorPlan(listOf(room)))
        for (f in FloorFinish.entries) {
            val n = triangles(FloorPlan(listOf(room.copy(floorFinish = f))))
            if (f.pattern == com.sagoma.planimetria.model.FloorPattern.None) assertEquals(none, n) else assertTrue("$f", n > none)
        }
    }

    @Test
    fun `modelli e finiture si salvano e si rileggono`() {
        val o = Opening.default(1, OpeningKind.Door, 0, 300.0).copy(doorModel = DoorModel.Glazed, color = Finish.Walnut)
        val w = Opening.default(2, OpeningKind.Window1, 1, 200.0).copy(windowModel = WindowModel.English, shading = Shading.Shutters)
        val f = Fixture.default(1, FixtureKind.Radiator).copy(radiatorModel = RadiatorModel.TowelRail)
        val plan = FloorPlan(
            listOf(room.copy(openings = listOf(o, w), fixtures = listOf(f), floorFinish = FloorFinish.OakParquet, wallPaint = WallPaint.Sage)),
            stairs = listOf(Stair(1, StairKind.LWinder, Vec2.Zero, structure = StairStructure.Stringers, railing = StairRailing.Glass, material = StairMaterial.DarkWood)),
        )
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
        // Colori di partenza: rovere per le porte, bianco per gli infissi.
        assertEquals(Finish.Oak, Opening.default(1, OpeningKind.Door, 0, 0.0).finish)
        assertEquals(Finish.White, Opening.default(1, OpeningKind.Window1, 0, 0.0).finish)
    }
}
