package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Muro che cambia spessore a metà: il lato destro spezzato in due tratti, uno da 15 e uno da 55 cm. */
class WallStepTest {
    // Stanza 400×300 in mezzeria; il muro destro (x = 400) diviso a y = 100: da 0 a 100 spesso 15, da 100 a 300 spesso 55.
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).splitWall(1)
        .let { it.copy(wallThicknesses = mapOf(2 to 55.0)) }
        .let { r -> r.copy(points = r.points.mapIndexed { i, p -> if (i == 2) Vec2(400.0, 100.0) else p }) }

    @Test
    fun `il contorno fa lo scalino`() {
        val outer = room.exteriorOutline()
        val inner = room.interiorOutline()
        // Due punti in più (uno fuori, uno dentro) nel vertice dello scalino.
        assertEquals(room.wallCount + 1, outer.size)
        assertEquals(room.wallCount + 1, inner.size)
        assertTrue(outer.any { it.distanceTo(Vec2(407.5, 100.0)) < 1e-6 })
        assertTrue(outer.any { it.distanceTo(Vec2(427.5, 100.0)) < 1e-6 })
        assertTrue(inner.any { it.distanceTo(Vec2(392.5, 100.0)) < 1e-6 })
        assertTrue(inner.any { it.distanceTo(Vec2(372.5, 100.0)) < 1e-6 })
    }

    @Test
    fun `misure interne con lo scalino`() {
        // Interno: x da 7,5 a 392,5 (sopra lo scalino) e a 372,5 (sotto), y da 7,5 a 292,5.
        val expected = 385.0 * 92.5 + 365.0 * 192.5
        assertEquals(expected, room.interiorArea(), 1e-6)
        val l = room.interiorLengths()
        assertEquals(92.5, l[1], 1e-6)
        assertEquals(192.5, l[2], 1e-6)
        assertEquals(365.0, l[3], 1e-6)
        // Il 3D si costruisce.
        Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
    }
}

/** Finestre con sguincio e infisso verso l'esterno, porta d'ingresso, misure in metri. */
class OpeningDetailsTest {
    private val window = com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Window2, 0, 200.0)
        .copy(frame = com.sagoma.planimetria.model.FramePosition.Outside, splay = 10.0)
    private val door = com.sagoma.planimetria.model.Opening.default(2, com.sagoma.planimetria.model.OpeningKind.Door, 2, 200.0).copy(entrance = true)
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 50.0)
        .copy(openings = listOf(window, door))

    @Test
    fun `infisso a filo esterno e sguincio`() {
        // Muro da 50: l'infisso sta a 25 − 6 = 19 cm dalla mezzeria verso l'esterno.
        assertEquals(-19.0, Openings.frameOffset(room, window), 1e-9)
        assertEquals(10.0, Openings.splayOf(room, window), 1e-9)
        // Le porte restano a metà muro e senza sguincio.
        assertEquals(0.0, Openings.frameOffset(room, door.copy(frame = com.sagoma.planimetria.model.FramePosition.Outside)), 1e-9)
        assertEquals(0.0, Openings.splayOf(room, door.copy(splay = 10.0)), 1e-9)
        // Si salva e si rilegge uguale; il 3D si costruisce.
        val b = com.sagoma.planimetria.model.Building.single(FloorPlan(listOf(room)))
            .copy(units = com.sagoma.planimetria.model.LengthUnit.Meters)
        val json = com.sagoma.planimetria.persistence.PlanJson.encode(b)
        assertEquals(b, com.sagoma.planimetria.persistence.PlanJson.decodeBuilding(json))
        Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
    }

    @Test
    fun `misure in metri`() {
        val p = com.sagoma.planimetria.model.PlanUnits
        val before = p.unit
        try {
            p.unit = com.sagoma.planimetria.model.LengthUnit.Meters
            assertEquals("5,03", p.format(503.0).replace('.', ','))
            assertEquals("0,75 m", p.withUnit(75.0).replace('.', ','))
            p.unit = com.sagoma.planimetria.model.LengthUnit.Centimeters
            assertEquals("503 cm", p.withUnit(503.0))
        } finally {
            p.unit = before
        }
    }
}

/** Muro curvo fatto di pezzi dritti lungo un arco, e sguincio arrotondato. */
class CurvedWallTest {
    // Stanza 400×300; il muro 1 (destro, x = 400) ha una finestra a metà.
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
        .copy(openings = listOf(com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Window1, 1, 150.0).copy(width = 60.0)))

    @Test
    fun `sguincio arrotondato`() {
        val p = Openings.splayProfile(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(0.0, 1.0), 20.0, 10.0, -1.0, rounded = true)
        assertTrue(p.size > 2)
        assertEquals(Vec2(0.0, 0.0), p.first())
        assertEquals(-10.0, p.last().x, 1e-9)
        assertEquals(20.0, p.last().y, 1e-9)
        val w = room.copy(wallThickness = 40.0, openings = room.openings.map { it.copy(splay = 10.0, splayRounded = true) })
        Scene3D.build(FloorPlan(listOf(w)), null, null, ceilings = true)
    }

    @Test
    fun `l'arco passa dagli angoli e sporge a metà`() {
        val r = room.copy(wallBulges = mapOf(1 to 50.0))
        val arc = r.arcOf(1)!!
        assertEquals(0.0, arc.at(-1.0).distanceTo(Vec2(400.0, 0.0)), 1e-6)
        assertEquals(0.0, arc.at(1.0).distanceTo(Vec2(400.0, 300.0)), 1e-6)
        assertEquals(0.0, arc.at(0.0).distanceTo(Vec2(450.0, 150.0)), 1e-6)
        // Raggio: (150² + 50²) / (2·50) = 250.
        assertEquals(250.0, arc.radius, 1e-6)
        assertEquals(50.0, WallArc.bulgeForRadius(300.0, 250.0), 1e-6)
        assertEquals(50.0, WallArc.bulgeForDegrees(300.0, arc.degrees), 1e-6)
        // Verso l'interno la stanza si restringe, verso l'esterno si allarga.
        val straight = room.interiorArea()
        assertTrue(r.interiorArea() > straight + 0.6 * 300 * 50)
        assertTrue(room.copy(wallBulges = mapOf(1 to -50.0)).interiorArea() < straight - 0.6 * 300 * 50)
        assertTrue(r.interiorLengths()[1] > 300.0)
    }

    @Test
    fun `la finestra sul muro curvo ha il suo pezzo dritto`() {
        val r = room.copy(wallBulges = mapOf(1 to 60.0))
        val e = r.expandCurves()
        assertTrue(e.room.wallCount > r.wallCount + 4)
        val o = e.room.openings.single()
        assertEquals(1, e.origin[o.wallIndex])
        assertEquals(60.0, o.width, 1e-6)
        assertEquals(60.0, e.room.wallLength(o.wallIndex), 1e-6)
        assertEquals(30.0, o.position, 1e-6)
        // La finestra sta sull'arco.
        val (a, b) = Openings.span(r, r.openings.single())
        val arc = r.arcOf(1)!!
        assertTrue(arc.distanceTo(a) < 0.5 && arc.distanceTo(b) < 0.5)
        Scene3D.build(FloorPlan(listOf(r)), null, null, ceilings = true)
    }

    @Test
    fun `angolo a metà di un muro curvo, specchio e salvataggio`() {
        val r = room.copy(wallBulges = mapOf(1 to 50.0))
        val arc = r.arcOf(1)!!
        val center = arc.at(0.0) - arc.outward * arc.radius
        val split = r.splitWall(1)
        assertEquals(0.0, split.points[2].distanceTo(Vec2(450.0, 150.0)), 1e-6)
        for (i in 1..2) {
            val half = split.arcOf(i)!!
            assertEquals(250.0, half.at(0.0).distanceTo(center), 1e-6)
        }
        val mirrored = GroupOps.mirror(FloorPlan(listOf(r)), setOf(GroupItem.RoomItem(1)), Vec2(200.0, 150.0), horizontal = true).rooms.single()
        val m = (0 until mirrored.wallCount).single { mirrored.arcOf(it) != null }
        // Lo specchio tiene la curva verso l'esterno (qualunque sia l'asse).
        val apex = mirrored.wallMiddle(m)
        assertEquals(0.0, minOf(apex.distanceTo(Vec2(-50.0, 150.0)), apex.distanceTo(Vec2(450.0, 150.0))), 1e-6)
        val back = com.sagoma.planimetria.persistence.PlanJson.decode(com.sagoma.planimetria.persistence.PlanJson.encode(FloorPlan(listOf(r))))
        assertEquals(mapOf(1 to 50.0), back.rooms.single().wallBulges)
    }

    @Test
    fun `accanto alla finestra il muro curvo parte nella direzione della finestra`() {
        val r = room.copy(wallBulges = mapOf(1 to 90.0), wallThickness = 30.0)
        val e = r.expandCurves().room
        val o = e.openings.single()
        val dir = (e.wallEnd(o.wallIndex) - e.wallStart(o.wallIndex)).normalized()
        // Il primo pezzo accanto alla finestra è quasi nella sua direzione (meno di 2°), poi curva.
        for (j in listOf(o.wallIndex - 1, o.wallIndex + 1)) {
            val d = (e.wallEnd(j) - e.wallStart(j)).normalized()
            assertTrue((d dot dir) > kotlin.math.cos(2.0 * kotlin.math.PI / 180))
            assertTrue(e.wallLength(j) < 15.0)
        }
        // Più in là torna sulla curva.
        val arc = r.arcOf(1)!!
        assertTrue(arc.distanceTo(e.wallStart(o.wallIndex - 20)) < 0.5)
    }

    @Test
    fun `angolo al tondo con un muro di altro spessore, senza punte`() {
        // Angolo smussato a 45° e curvato di 90°: la curva finisce tangente ai muri accanto, che sono più sottili.
        val pts = listOf(Vec2(0.0, 0.0), Vec2(300.0, 0.0), Vec2(400.0, 100.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0))
        val chord = pts[1].distanceTo(pts[2])
        val r = Room(1, "Camera", RoomType.Camera, pts, wallThickness = 28.0)
            .copy(wallThicknesses = mapOf(1 to 44.0), wallBulges = mapOf(1 to chord / 2 * kotlin.math.tan(kotlin.math.PI / 8)))
        for (p in r.exteriorOutline() + r.interiorOutline()) {
            assertTrue(p.x in -30.0..430.0 && p.y in -30.0..330.0, "punto fuori posto: $p")
        }
    }
}

