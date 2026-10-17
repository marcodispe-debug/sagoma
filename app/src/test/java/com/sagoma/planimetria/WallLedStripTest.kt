package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Striscia LED a parete: luce lineare diffusa che segue il muro; la striscia a soffitto resta com'è. */
class WallLedStripTest {
    /** I vertici della scena sono float: un errore di qualche milionesimo di cm non conta. */
    private val EPS = 1e-3

    private fun room(points: List<Vec2>, fixtures: List<Fixture> = emptyList(), wallHeights: Map<Int, Double> = emptyMap()) =
        Room(1, "Stanza", RoomType.Soggiorno, points, wallHeights = wallHeights, fixtures = fixtures)

    private fun rotated(points: List<Vec2>, deg: Double): List<Vec2> {
        val r = Math.toRadians(deg)
        return points.map { Vec2(it.x * Math.cos(r) - it.y * Math.sin(r), it.x * Math.sin(r) + it.y * Math.cos(r)) }
    }

    private val shapes = mapOf(
        "rettangolo" to RoomFactory.rectangle(600.0, 400.0),
        "L" to RoomFactory.lShape(ShapeDimensions(600.0, 400.0, 250.0, 200.0), LOrientation.TopRight),
        "ruotata 30°" to rotated(RoomFactory.rectangle(600.0, 400.0), 30.0).map { it + Vec2(600.0, 100.0) },
    )

    private fun lampVertices(scene: Scene3D): List<Vec3> {
        val a = scene.opaque
        val out = mutableListOf<Vec3>()
        var i = 0
        while (i + 9 < a.size) {
            if (Math.abs(a[i + 6] - 1f) < 0.005f && Math.abs(a[i + 7] - 0.93f) < 0.005f && Math.abs(a[i + 8] - 0.62f) < 0.005f) {
                out += Vec3(a[i].toDouble(), a[i + 1].toDouble(), a[i + 2].toDouble())
            }
            i += 10
        }
        return out
    }

    private fun scene(r: Room) = Scene3D.build(FloorPlan(listOf(r)), null, null, ceilings = false)

    @Test
    fun `tipo e montaggio a parete, la striscia a soffitto non cambia`() {
        assertEquals(Mount.Wall, FixtureKind.WallLedStrip.mount)
        assertFalse(FixtureKind.WallLedStrip.linear) // niente punto e rotazione: segue il muro
        assertEquals("Striscia LED a parete", FixtureKind.WallLedStrip.label)
        assertEquals(Mount.Ceiling, FixtureKind.LedStrip.mount)
        assertTrue(FixtureKind.LedStrip.linear)
        assertEquals("Striscia LED", FixtureKind.LedStrip.label)
    }

    @Test
    fun `e una luce`() {
        assertTrue(FixtureKind.WallLedStrip.isLight)
        assertTrue(FixtureKind.LedStrip.isLight)
        assertFalse(FixtureKind.Outlet.isLight)
    }

    @Test
    fun `lunghezza e quota predefinite`() {
        val f = Fixture.default(1, FixtureKind.WallLedStrip)
        assertEquals(200.0, f.length, 0.0)
        assertEquals(250.0, f.elevation, 0.0)
        assertEquals(0.0, f.rotation, 0.0)
        // La striscia a soffitto ha i valori di prima.
        val ceiling = Fixture.default(1, FixtureKind.LedStrip)
        assertEquals(200.0, ceiling.length, 0.0)
        assertEquals(0.0, ceiling.elevation, 0.0)
    }

    @Test
    fun `si salva e si rilegge`() {
        val plan = FloorPlan(listOf(room(RoomFactory.rectangle(600.0, 400.0), listOf(Fixture(1, FixtureKind.WallLedStrip, wallIndex = 2, position = 300.0, length = 150.0, elevation = 240.0)))))
        val text = PlanJson.encode(plan)
        assertTrue(text.contains("WallLedStrip"))
        assertEquals(plan, PlanJson.decode(text))
    }

    @Test
    fun `la posizione sul muro tiene la striscia dentro il muro`() {
        val r = room(RoomFactory.rectangle(600.0, 400.0))
        val strip = Fixture.default(1, FixtureKind.WallLedStrip)
        // Vicino all'inizio e alla fine del muro (lungo 600): la striscia da 200 cm resta tutta dentro.
        assertEquals(100.0, Openings.clampPosition(r.wallLength(0), strip.length, 10.0), 0.0)
        assertEquals(500.0, Openings.clampPosition(r.wallLength(0), strip.length, 590.0), 0.0)
        // L'ancoraggio sta sul filo interno del muro, con la normale verso la stanza.
        val f = strip.copy(wallIndex = 0, position = 10.0)
        val (p, n) = Fixtures.wallAnchor(r.copy(fixtures = listOf(f)), f)
        assertEquals(100.0, (p - r.wallStart(0)) dot (r.wallEnd(0) - r.wallStart(0)).normalized(), 1e-9) // 100 dall'inizio del muro
        assertEquals(0.0, (n - Openings.inwardNormal(r, 0)).length, 1e-9)
    }

    @Test
    fun `segue il muro e ignora la rotazione, anche su pareti L e ruotate`() {
        for ((name, points) in shapes) {
            val probe = room(points)
            for (i in 0 until probe.wallCount) {
                val len = probe.wallLength(i)
                if (len < 200.0) continue
                // `rotation` non ha effetto: stessa geometria con 0° e con 77°.
                val geometry = listOf(0.0, 77.0).map { rot ->
                    val f = Fixture(1, FixtureKind.WallLedStrip, wallIndex = i, position = len / 2, length = 200.0, elevation = 250.0, rotation = rot)
                    lampVertices(scene(room(points, listOf(f))))
                }
                assertTrue("$name/$i: serve la striscia", geometry[0].isNotEmpty())
                assertEquals("$name/$i: rotazione ignorata", geometry[0].size, geometry[1].size)
                for ((a, b) in geometry[0].zip(geometry[1])) assertEquals((a - b).length, 0.0, EPS)

                val r = room(points, listOf(Fixture(1, FixtureKind.WallLedStrip, wallIndex = i, position = len / 2, length = 200.0, elevation = 250.0)))
                val inward = Openings.inwardNormal(r, i)
                val t = r.thicknessOf(i) / 2
                val start = r.wallStart(i)
                val u = (r.wallEnd(i) - start).normalized()
                val v = lampVertices(scene(r))
                for (p in v) {
                    val q = Vec2(p.x, p.z) - start
                    val away = q dot inward
                    val along = q dot u
                    assertTrue("$name/$i: dentro il muro ($away)", away >= t + 0.4 - EPS)
                    assertTrue("$name/$i: troppo sporgente ($away)", away <= t + Scene3D.WALL_LED_DEPTH + 0.5 + EPS)
                    assertTrue("$name/$i: quota ${p.y}", p.y >= 250.0 - Scene3D.WALL_LED_HEIGHT / 2 - EPS && p.y <= 250.0 + Scene3D.WALL_LED_HEIGHT / 2 + EPS)
                    assertTrue("$name/$i: fuori dal tratto ($along)", along >= len / 2 - 100.0 - EPS && along <= len / 2 + 100.0 + EPS)
                }
                // Lunga quanto la striscia, lungo il muro.
                assertEquals(200.0, v.maxOf { ((Vec2(it.x, it.z) - start) dot u) } - v.minOf { ((Vec2(it.x, it.z) - start) dot u) }, EPS)
            }
        }
    }

    @Test
    fun `luci distribuite lungo la striscia, diffuse e neutre, davanti alla parete`() {
        for ((name, points) in shapes) {
            val probe = room(points)
            val i = (0 until probe.wallCount).first { probe.wallLength(it) >= 400.0 }
            val len = probe.wallLength(i)
            for (length in listOf(50.0, 79.0, 80.0, 200.0, 400.0)) {
                val f = Fixture(1, FixtureKind.WallLedStrip, wallIndex = i, position = len / 2, length = length, elevation = 250.0)
                val r = room(points, listOf(f))
                val lights = scene(r).lights
                val count = maxOf(1, (length / 80.0).toInt())
                assertEquals("$name/$length: numero di luci", count, lights.size)
                val inward = Openings.inwardNormal(r, i)
                val start = r.wallStart(i)
                val u = (r.wallEnd(i) - start).normalized()
                val t = r.thicknessOf(i) / 2
                for ((k, l) in lights.withIndex()) {
                    assertNull("$name/$length: nessuna direzione (non è un faretto)", l.direction)
                    assertFalse("$name/$length: luce neutra", l.warm)
                    assertEquals(250.0, l.position.y, 1e-9)
                    val q = Vec2(l.position.x, l.position.z) - start
                    assertEquals("$name/$length: davanti alla parete", t + Scene3D.WALL_LED_DEPTH + 4.0, q dot inward, 1e-6)
                    assertEquals("$name/$length: lungo la striscia", len / 2 - length / 2 + length * (k + 0.5) / count, q dot u, 1e-6)
                    assertEquals(Scene3D.WALL_LED_LUMENS / count, l.lumens, 1e-9)
                    assertTrue("$name/$length: nella stanza", Polygon.contains(r.points, Vec2(l.position.x, l.position.z)))
                }
                assertEquals(1000.0, lights.sumOf { it.lumens }, 1e-9) // flusso totale
            }
        }
    }

    @Test
    fun `la striscia LED a soffitto non cambia`() {
        val f = Fixture(1, FixtureKind.LedStrip, point = Vec2(250.0, 200.0), length = 200.0, rotation = 0.0)
        val s = scene(room(RoomFactory.rectangle(600.0, 400.0), listOf(f)))
        assertEquals(2, s.lights.size)
        for ((k, l) in s.lights.withIndex()) {
            assertNull(l.direction)
            assertFalse(l.warm)
            assertEquals(500.0, l.lumens, 1e-9)
            assertEquals(Vec3(200.0 + 100.0 * k, 260.0, 200.0), l.position) // 270 - 10, una ogni 100 cm lungo i 200
        }
        // La geometria resta attaccata al soffitto (scatola da 268,5 a 270).
        val v = lampVertices(s)
        assertEquals(268.5, v.minOf { it.y }, EPS)
        assertEquals(270.0, v.maxOf { it.y }, EPS)
    }

    @Test
    fun `plafoniera e faretto a parete restano come corretti`() {
        for (kind in listOf(FixtureKind.WallLight, FixtureKind.WallSpot)) {
            val f = Fixture(1, kind, wallIndex = 0, position = 300.0, elevation = 220.0)
            val l = scene(room(RoomFactory.rectangle(600.0, 400.0), listOf(f))).lights.single()
            if (kind == FixtureKind.WallLight) assertNull(l.direction) else assertTrue(l.direction!!.y < 0.0)
            assertTrue(l.warm)
            assertEquals(220.0, l.position.y, 1e-9)
        }
    }

    @Test
    fun `una striscia sopra il bordo di una parete tagliata e segnalata`() {
        fun collisions(elevation: Double, wallHeights: Map<Int, Double>) = Collisions.of(
            FloorPlan(listOf(room(RoomFactory.rectangle(600.0, 400.0), listOf(Fixture(1, FixtureKind.WallLedStrip, wallIndex = 0, position = 300.0, length = 200.0, elevation = elevation)), wallHeights = wallHeights))),
        )
        assertTrue(collisions(250.0, mapOf(0 to 200.0)).any { it.fixtureId == 1L })
        assertTrue(collisions(150.0, mapOf(0 to 200.0)).none { it.fixtureId == 1L })
        assertTrue(collisions(250.0, emptyMap()).none { it.fixtureId == 1L })
    }
}
