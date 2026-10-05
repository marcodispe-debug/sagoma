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

/** Plafoniera e faretto a parete: impianti a muro (come presa e interruttore) che sono anche luci vere in 3D. */
class WallLightsTest {
    /** I vertici della scena sono float: un errore di qualche milionesimo di cm non conta. */
    private val EPS = 1e-3

    private val wallKinds = listOf(FixtureKind.WallLight, FixtureKind.WallSpot)

    private fun room(points: List<Vec2>, fixtures: List<Fixture> = emptyList(), wallHeights: Map<Int, Double> = emptyMap()) =
        Room(1, "Stanza", RoomType.Soggiorno, points, wallHeights = wallHeights, fixtures = fixtures)

    private fun rotated(points: List<Vec2>, deg: Double): List<Vec2> {
        val r = Math.toRadians(deg)
        return points.map { Vec2(it.x * Math.cos(r) - it.y * Math.sin(r), it.x * Math.sin(r) + it.y * Math.cos(r)) }
    }

    /**
     * Stanze con pareti in direzioni diverse: rettangolo, L e rettangolo ruotato di 30°. I poligoni sono sempre orari su
     * schermo, come per tutti gli impianti a muro (vedi `Openings.inwardNormal`).
     */
    private val shapes = mapOf(
        "rettangolo" to RoomFactory.rectangle(500.0, 400.0),
        "L" to RoomFactory.lShape(ShapeDimensions(600.0, 400.0, 250.0, 200.0), LOrientation.TopRight),
        "ruotata 30°" to rotated(RoomFactory.rectangle(500.0, 400.0), 30.0).map { it + Vec2(600.0, 100.0) },
    )

    @Test
    fun `i nuovi tipi sono impianti a muro`() {
        for (k in wallKinds) assertEquals(Mount.Wall, k.mount)
        assertEquals("Plafoniera a parete", FixtureKind.WallLight.label)
        assertEquals("Faretto a parete", FixtureKind.WallSpot.label)
        // Quelli a soffitto non cambiano.
        for (k in listOf(FixtureKind.Spotlight, FixtureKind.Neon, FixtureKind.Chandelier, FixtureKind.CeilingLight, FixtureKind.LedStrip)) assertEquals(Mount.Ceiling, k.mount)
        for (k in listOf(FixtureKind.Radiator, FixtureKind.Outlet, FixtureKind.Switch, FixtureKind.WaterPoint)) assertEquals(Mount.Wall, k.mount)
    }

    @Test
    fun `sono luci i tipi a soffitto e quelli a parete, non gli altri impianti a muro`() {
        val lights = setOf(
            FixtureKind.Spotlight, FixtureKind.Neon, FixtureKind.Chandelier, FixtureKind.CeilingLight, FixtureKind.LedStrip,
            FixtureKind.WallLight, FixtureKind.WallSpot, FixtureKind.WallLedStrip,
        )
        for (k in FixtureKind.entries) assertEquals(k.name, k in lights, k.isLight)
        assertTrue(FixtureKind.WallLight.isLight && FixtureKind.WallSpot.isLight)
    }

    @Test
    fun `quota iniziale 220 cm`() {
        for (k in wallKinds) assertEquals(220.0, Fixture.default(1, k).elevation, 0.0)
        assertEquals(220.0, Fixture.WALL_LIGHT_ELEVATION, 0.0)
        // Gli altri impianti a muro hanno le quote di prima.
        assertEquals(30.0, Fixture.default(1, FixtureKind.Outlet).elevation, 0.0)
        assertEquals(110.0, Fixture.default(1, FixtureKind.Switch).elevation, 0.0)
    }

    @Test
    fun `si salvano e si rileggono, e i file senza i nuovi tipi restano uguali`() {
        val fixtures = listOf(
            Fixture(1, FixtureKind.WallLight, wallIndex = 1, position = 120.0, elevation = 200.0),
            Fixture(2, FixtureKind.WallSpot, wallIndex = 3, position = 80.0, elevation = 230.0),
            Fixture(3, FixtureKind.Spotlight, point = Vec2(100.0, 100.0)),
        )
        val plan = FloorPlan(listOf(room(RoomFactory.rectangle(500.0, 400.0), fixtures)))
        val text = PlanJson.encode(plan)
        assertTrue(text.contains("WallLight") && text.contains("WallSpot"))
        assertEquals(plan, PlanJson.decode(text))
        val old = FloorPlan(listOf(room(RoomFactory.rectangle(500.0, 400.0), listOf(Fixture(1, FixtureKind.Outlet, wallIndex = 0, position = 50.0, elevation = 30.0)))))
        assertEquals(old, PlanJson.decode(PlanJson.encode(old)))
    }

    /** Vertici dei triangoli luminosi (colore giallo caldo) della scena. */
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

    @Test
    fun `orientamento secondo la normale di ogni parete, anche L e ruotata`() {
        for ((name, points) in shapes) for (kind in wallKinds) {
            val probe = room(points)
            for (i in 0 until probe.wallCount) {
                val f = Fixture(1, kind, wallIndex = i, position = probe.wallLength(i) / 2, elevation = 220.0)
                val r = room(points, listOf(f))
                val inward = Openings.inwardNormal(r, i)
                val (anchor, normal) = Fixtures.wallAnchor(r, f)
                assertEquals("$name/$kind/$i normale", 0.0, (normal - inward).length, 1e-9)
                // Il punto di attacco sta sul filo interno del muro, dentro la stanza.
                assertTrue("$name/$kind/$i attacco", Polygon.contains(r.points, anchor + inward * 3.0))
                assertFalse("$name/$kind/$i attacco fuori", Polygon.contains(r.points, anchor - inward * (r.thicknessOf(i) + 30.0)))
            }
        }
    }

    @Test
    fun `in 3D la lampada sta fuori dal muro, verso la stanza, alla quota giusta, e la luce vera punta lungo la normale`() {
        for ((name, points) in shapes) for (kind in wallKinds) {
            val probe = room(points)
            for (i in 0 until probe.wallCount) {
                val len = probe.wallLength(i)
                val f = Fixture(1, kind, wallIndex = i, position = len / 2, elevation = 220.0)
                val r = room(points, listOf(f))
                val scene = Scene3D.build(FloorPlan(listOf(r)), null, null, ceilings = false)
                val inward = Openings.inwardNormal(r, i)
                val t = r.thicknessOf(i) / 2
                val start = r.wallStart(i)
                val u = (r.wallEnd(i) - start).normalized()
                val width = if (kind == FixtureKind.WallLight) Scene3D.WALL_LIGHT_WIDTH else Scene3D.WALL_SPOT_SIZE
                val height = if (kind == FixtureKind.WallLight) Scene3D.WALL_LIGHT_HEIGHT else Scene3D.WALL_SPOT_SIZE
                val depth = if (kind == FixtureKind.WallLight) Scene3D.WALL_LIGHT_DEPTH else Scene3D.WALL_SPOT_DEPTH
                val v = lampVertices(scene)
                assertTrue("$name/$kind/$i: serve la lampada", v.isNotEmpty())
                for (p in v) {
                    val q = Vec2(p.x, p.z) - start
                    val away = q dot inward // distanza dalla linea di mezzo del muro verso la stanza
                    val along = q dot u
                    assertTrue("$name/$kind/$i: dentro il muro ($away)", away >= t + 0.4 - EPS) // fuori dal muro
                    assertTrue("$name/$kind/$i: troppo sporgente ($away)", away <= t + depth + 0.6 + EPS)
                    assertTrue("$name/$kind/$i: quota ${p.y}", p.y >= 220.0 - height / 2 - EPS && p.y <= 220.0 + height / 2 + EPS)
                    assertTrue("$name/$kind/$i: lungo il muro ($along)", Math.abs(along - len / 2) <= width / 2 + EPS)
                }
                // Sporge davvero dal muro per la sua profondità.
                assertEquals(t + depth + 0.5, v.maxOf { (Vec2(it.x, it.z) - start) dot inward }, 1e-3) // i vertici sono float
                // Luce vera: una sola, davanti alla lampada, alla stessa quota, che punta lungo la normale della parete.
                val light = scene.lights.single()
                assertEquals(220.0, light.position.y, 1e-9)
                if (kind == FixtureKind.WallLight) {
                    // Plafoniera: luce diffusa, senza fascio.
                    assertNull("$name/$kind/$i: la plafoniera non ha direzione", light.direction)
                } else {
                    // Faretto: fascio lungo la normale della parete, inclinato di 45° verso il basso.
                    val d = light.direction!!
                    val theta = Math.toRadians(Scene3D.WALL_SPOT_TILT_DEG)
                    assertTrue("$name/$kind/$i: Y negativa (${d.y})", d.y < 0.0)
                    assertEquals("$name/$kind/$i: inclinazione", -Math.sin(theta), d.y, 1e-9)
                    // La componente orizzontale è parallela alla normale e ha lunghezza cos(theta).
                    val horizontal = Vec2(d.x, d.z)
                    assertEquals("$name/$kind/$i: orizzontale lungo la normale", 0.0, (horizontal - inward * Math.cos(theta)).length, 1e-9)
                    assertEquals("$name/$kind/$i: versore", 1.0, Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), 1e-9)
                }
                assertEquals(t + depth + 1.0, (Vec2(light.position.x, light.position.z) - start) dot inward, 1e-6)
                assertTrue("$name/$kind/$i luce nella stanza", Polygon.contains(r.points, Vec2(light.position.x, light.position.z)))
                assertEquals(if (kind == FixtureKind.WallLight) 800.0 else 400.0, light.lumens, 0.0)
            }
        }
    }

    @Test
    fun `la quota cambia la posizione della lampada e della luce`() {
        for (kind in wallKinds) {
            fun scene(elevation: Double) = Scene3D.build(
                FloorPlan(listOf(room(RoomFactory.rectangle(500.0, 400.0), listOf(Fixture(1, kind, wallIndex = 0, position = 250.0, elevation = elevation))))),
                null, null, ceilings = false,
            )
            val low = scene(150.0)
            val high = scene(230.0)
            assertEquals(150.0, low.lights.single().position.y, 1e-9)
            assertEquals(230.0, high.lights.single().position.y, 1e-9)
            assertTrue(lampVertices(high).minOf { it.y } > lampVertices(low).maxOf { it.y })
        }
    }

    @Test
    fun `le luci a parete non cambiano il comportamento delle luci a soffitto`() {
        val ceiling = room(RoomFactory.rectangle(500.0, 400.0), listOf(Fixture(1, FixtureKind.Spotlight, point = Vec2(250.0, 200.0))))
        val s = Scene3D.build(FloorPlan(listOf(ceiling)), null, null, ceilings = false)
        val l = s.lights.single()
        assertEquals(Vec3(250.0, 266.0, 200.0), l.position) // soffitto 270 - 4
        assertEquals(Vec3(0.0, -1.0, 0.0), l.direction)
        assertEquals(400.0, l.lumens, 0.0)
    }

    @Test
    fun `una luce a parete sopra il bordo di una parete tagliata e segnalata, come gli altri impianti a muro`() {
        fun collisions(elevation: Double) = Collisions.of(
            FloorPlan(listOf(room(RoomFactory.rectangle(500.0, 400.0), listOf(Fixture(1, FixtureKind.WallLight, wallIndex = 0, position = 250.0, elevation = elevation)), wallHeights = mapOf(0 to 200.0)))),
        )
        assertTrue(collisions(220.0).any { it.fixtureId == 1L })
        assertTrue(collisions(150.0).none { it.fixtureId == 1L })
    }
}
