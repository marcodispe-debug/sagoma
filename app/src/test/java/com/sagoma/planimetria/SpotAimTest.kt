package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Camera3D
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.SpotAim
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpotAimTest {
    // Normale interna del muro 0 di un rettangolo in alto: verso +z (pianta y).
    private val n = Vec2(0.0, 1.0)
    private val eps = 1e-9

    private fun assertDir(x: Double, y: Double, z: Double, d: Vec3, tol: Double = 1e-9) {
        assertEquals("x", x, d.x, tol); assertEquals("y", y, d.y, tol); assertEquals("z", z, d.z, tol)
    }

    @Test
    fun `yaw 0 e tilt 45 e il comportamento di prima`() {
        val s = Math.sqrt(0.5)
        assertDir(0.0, -s, s, SpotAim.direction(n, 0.0, 45.0))
    }

    @Test
    fun `yaw 90 va lungo il muro`() {
        val t = n.perp()
        assertDir(t.x, 0.0, t.y, SpotAim.direction(n, 90.0, 0.0))
    }

    @Test
    fun `yaw 180 va verso il muro e 270 dalla parte opposta`() {
        assertDir(0.0, 0.0, -1.0, SpotAim.direction(n, 180.0, 0.0))
        val t = n.perp()
        assertDir(-t.x, 0.0, -t.y, SpotAim.direction(n, 270.0, 0.0))
    }

    @Test
    fun `tilt 0 orizzontale, positivo in basso, negativo in alto`() {
        assertEquals(0.0, SpotAim.direction(n, 0.0, 0.0).y, eps)
        assert(SpotAim.direction(n, 0.0, 30.0).y < 0)
        assert(SpotAim.direction(n, 0.0, -30.0).y > 0)
        assertDir(0.0, -1.0, 0.0, SpotAim.direction(n, 0.0, 90.0), 1e-9)
        assertDir(0.0, 1.0, 0.0, SpotAim.direction(n, 0.0, -90.0), 1e-9)
    }

    @Test
    fun `yaw e tilt si normalizzano`() {
        assertEquals(10.0, SpotAim.normalizeYaw(370.0), eps)
        assertEquals(350.0, SpotAim.normalizeYaw(-10.0), eps)
        assertEquals(0.0, SpotAim.normalizeYaw(360.0), eps)
        assertEquals(90.0, SpotAim.clampTilt(120.0), eps)
        assertEquals(-90.0, SpotAim.clampTilt(-120.0), eps)
        assertDir(0.0, 0.0, 1.0, SpotAim.direction(n, 360.0, 0.0))
    }

    @Test
    fun `la direzione e un versore`() {
        for (yaw in 0..350 step 25) for (tilt in -90..90 step 15) assertEquals(1.0, SpotAim.direction(n, yaw.toDouble(), tilt.toDouble()).length, 1e-9)
    }

    @Test
    fun `da direzione a yaw e tilt, e andata e ritorno`() {
        val a = SpotAim.fromDirection(n, SpotAim.direction(n, 120.0, 30.0))
        assertEquals(120.0, a.yaw, 1e-6); assertEquals(30.0, a.tilt, 1e-6)
        for (yaw in 0..359 step 17) for (tilt in -80..80 step 10) {
            val r = SpotAim.fromDirection(n, SpotAim.direction(n, yaw.toDouble(), tilt.toDouble()))
            assertEquals(yaw.toDouble(), r.yaw, 1e-6); assertEquals(tilt.toDouble(), r.tilt, 1e-6)
        }
        // Anche con una normale diversa e un vettore non unitario.
        val m = Vec2(-1.0, 0.0)
        val r = SpotAim.fromDirection(m, SpotAim.direction(m, 200.0, -20.0) * 7.0)
        assertEquals(200.0, r.yaw, 1e-6); assertEquals(-20.0, r.tilt, 1e-6)
    }

    @Test
    fun `a 90 gradi in su o in giu resta l ultimo yaw`() {
        val up = SpotAim.fromDirection(n, Vec3(0.0, 1.0, 0.0), previousYaw = 135.0)
        assertEquals(135.0, up.yaw, eps); assertEquals(-90.0, up.tilt, 1e-9)
        val down = SpotAim.fromDirection(n, Vec3(0.0, -1.0, 0.0), previousYaw = 300.0)
        assertEquals(300.0, down.yaw, eps); assertEquals(90.0, down.tilt, 1e-9)
    }

    @Test
    fun `il raggio del dito sulla sfera da la direzione sotto il dito`() {
        val spot = Vec3(100.0, 220.0, 5.0)
        val want = SpotAim.direction(n, 70.0, 20.0)
        val target = spot + want * SpotAim.HANDLE_LENGTH
        val origin = Vec3(300.0, 300.0, 400.0)
        val d = SpotAim.sphereDirection(spot, origin, (target - origin).normalized(), SpotAim.HANDLE_LENGTH)!!
        // Il raggio incontra la sfera in due punti: si prende quello piu vicino all'osservatore.
        assertEquals(1.0, d.length, 1e-9)
        val hit = spot + d * SpotAim.HANDLE_LENGTH
        assertEquals(0.0, ((hit - origin).normalized() - (target - origin).normalized()).length, 1e-9)
        // Raggio che manca la sfera: punto piu vicino, comunque una direzione valida.
        val miss = SpotAim.sphereDirection(spot, origin, (spot + Vec3(500.0, 0.0, 0.0) - origin).normalized(), 60.0)
        assertNotNull(miss); assertEquals(1.0, miss!!.length, 1e-9)
        // Raggio sul centro: resta una direzione valida (quella piu vicina alla precedente).
        val c = SpotAim.sphereDirection(spot, origin, (spot - origin).normalized(), 60.0, previous = want)!!
        assertEquals(1.0, c.length, 1e-9)
        // Senza direzione precedente vale il punto piu vicino all'osservatore; con quella precedente, anche l'emisfero lontano.
        val far = SpotAim.sphereDirection(spot, origin, (target - origin).normalized(), SpotAim.HANDLE_LENGTH, previous = want)!!
        assertEquals(0.0, (far - want).length, 1e-9)
    }

    @Test
    fun `un giro completo del dito in orizzontale copre tutti i 360 gradi`() {
        val spot = Vec3(0.0, 100.0, 0.0)
        val origin = Vec3(0.0, 100.0, 600.0)
        var prev = 0.0
        var prevDir: Vec3? = null
        val seen = HashSet<Int>()
        for (k in 0 until 72) {
            val want = SpotAim.direction(n, k * 5.0, 0.0)
            val p = spot + want * 60.0
            val cur = SpotAim.direction(n, prev, 0.0)
            val predicted = prevDir?.let { Vec3(cur.x * 2 - it.x, cur.y * 2 - it.y, cur.z * 2 - it.z) }
            val a = SpotAim.aimFromRay(n, spot, origin, (p - origin).normalized(), prev, 0.0, predicted)!!
            prevDir = cur
            seen += Math.round(a.yaw / 5.0).toInt() % 72
            prev = a.yaw
        }
        assertEquals(72, seen.size)
    }

    @Test
    fun `la maniglia sta sulla sfera lungo il fascio`() {
        val spot = Vec3(1.0, 2.0, 3.0)
        val d = SpotAim.direction(n, 33.0, 12.0)
        assertEquals(SpotAim.HANDLE_LENGTH, (SpotAim.handle(spot, d) - spot).length, 1e-9)
    }

    private fun roomWithSpot(yaw: Double, tilt: Double): Room =
        Room(1, "S", RoomType.Altro, RoomFactory.rectangle(400.0, 300.0),
            fixtures = listOf(Fixture(1, FixtureKind.WallSpot, wallIndex = 0, position = 150.0, elevation = 220.0, aimYaw = yaw, aimTilt = tilt)))

    @Test
    fun `la luce della scena usa solo SpotAim e porta l id dell apparecchio`() {
        val room = roomWithSpot(90.0, 10.0)
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val light = scene.lights.single()
        val f = room.fixtures.single()
        val d = light.direction!!
        assertEquals(0.0, (d - SpotAim.directionOf(room, f)).length, 1e-9)
        assertEquals(f.id, light.fixtureId)
        // withLights sostituisce solo le luci.
        val moved = scene.withLights(listOf(light.copy(direction = Vec3(0.0, -1.0, 0.0))))
        assertEquals(-1.0, moved.lights.single().direction!!.y, 0.0)
        assertEquals(scene.opaque.size, moved.opaque.size)
    }

    @Test
    fun `un faretto vecchio senza yaw e tilt si comporta come prima`() {
        val f = Fixture(1, FixtureKind.WallSpot, wallIndex = 0, position = 100.0)
        assertEquals(0.0, f.aimYaw, 0.0); assertEquals(45.0, f.aimTilt, 0.0)
        assertEquals(Mount.Wall, f.kind.mount)
    }

    @Test
    fun `la proiezione e l inverso del raggio`() {
        val cam = Camera3D()
        val (w, h) = 800f to 600f
        for ((x, y) in listOf(100f to 80f, 400f to 300f, 700f to 500f)) {
            val (o, d) = cam.ray(x, y, w, h)
            val (px, py) = cam.project(o + d * 700.0, w, h)!!
            assertEquals(x.toDouble(), px.toDouble(), 1e-2); assertEquals(y.toDouble(), py.toDouble(), 1e-2)
        }
        // Dietro l'osservatore: niente.
        val (o, d) = cam.ray(400f, 300f, w, h)
        assertNull(cam.project(o - d * 100.0, w, h))
    }
}
