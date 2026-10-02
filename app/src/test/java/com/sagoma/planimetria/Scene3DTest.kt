package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Camera3D
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Triangulation
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Scene3DTest {

    private fun room(id: Long, w: Double, h: Double, at: Vec2 = Vec2.Zero, openings: List<Opening> = emptyList()) =
        Room(id, "R$id", RoomType.Altro, RoomFactory.rectangle(w, h).map { it + at }, openings = openings)

    @Test
    fun `la triangolazione copre esattamente una stanza a L`() {
        val l = listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 200.0), Vec2(200.0, 200.0), Vec2(200.0, 400.0), Vec2(0.0, 400.0))
        val tris = Triangulation.triangulate(l)
        assertEquals(l.size - 2, tris.size)
        val area = tris.sumOf { (a, b, c) -> Polygon.area(listOf(l[a], l[b], l[c])) }
        assertEquals(Polygon.area(l), area, 1e-6)
    }

    @Test
    fun `una porta lascia un varco vero nel muro`() {
        // Muro 0 in alto (z = 0), porta al centro (x = 150).
        val door = Opening.default(1, OpeningKind.Door, 0, 150.0)
        val scene = Scene3D.build(FloorPlan(listOf(room(1, 300.0, 200.0, openings = listOf(door)))), null, null, ceilings = false)
        // Raggio orizzontale a 1 m da terra, da fuori verso dentro, proprio nel vano porta.
        val through = scene.hit(Vec3(150.0, 100.0, -100.0), Vec3(0.0, 0.0, 1.0))!!
        assertNotEquals(Pick.Wall(1, 0), through.pick)
        assertTrue(through.point.z > 50) // passa il muro e arriva più in là
        // Accanto alla porta il muro c'è.
        val wall = scene.hit(Vec3(40.0, 100.0, -100.0), Vec3(0.0, 0.0, 1.0))!!
        assertEquals(Pick.Wall(1, 0), wall.pick)
        // Sopra la porta (architrave) il muro appartiene all'apertura: toccarlo seleziona la porta.
        val lintel = scene.hit(Vec3(150.0, 240.0, -100.0), Vec3(0.0, 0.0, 1.0))!!
        assertEquals(Pick.Opening(1, 1), lintel.pick)
    }

    @Test
    fun `una finestra ha davanzale e vetro`() {
        val win = Opening.default(1, OpeningKind.Window1, 0, 150.0) // davanzale a 90 cm
        val scene = Scene3D.build(FloorPlan(listOf(room(1, 300.0, 200.0, openings = listOf(win)))), null, null, ceilings = false)
        assertTrue(scene.transparent.isNotEmpty())
        val below = scene.hit(Vec3(150.0, 50.0, -100.0), Vec3(0.0, 0.0, 1.0))!!
        assertEquals(Pick.Opening(1, 1), below.pick)
        assertTrue(below.point.z < 10) // il muro sotto il davanzale ferma il raggio
    }

    @Test
    fun `nel muro in comune ogni stanza ha la sua metà, e agli incroci nessuna sconfina nell'altra`() {
        // R1 0..300, R2 300..600: il muro in comune è il destro di R1 e il sinistro di R2.
        val scene = Scene3D.build(FloorPlan(listOf(room(1, 300.0, 400.0), room(2, 300.0, 400.0, Vec2(300.0, 0.0)))), null, null, ceilings = false)
        assertEquals(Pick.Wall(1, 1), scene.hit(Vec3(150.0, 100.0, 200.0), Vec3(1.0, 0.0, 0.0))!!.pick)
        assertEquals(Pick.Wall(2, 3), scene.hit(Vec3(450.0, 100.0, 200.0), Vec3(-1.0, 0.0, 0.0))!!.pick)
        fun xs(p: Pick) = scene.picks.filter { it.pick == p }.flatMap { listOf(it.a.x, it.b.x, it.c.x) }
        // Le due metà non oltrepassano la mezzeria (x = 300).
        assertTrue(xs(Pick.Wall(1, 1)).all { it <= 300.01 })
        assertTrue(xs(Pick.Wall(2, 3)).all { it >= 299.99 })
        // Il muro in alto di R1 si ferma all'incrocio con il muro in comune: non entra nella parte di R2.
        assertTrue(xs(Pick.Wall(1, 0)).all { it <= 300.01 })
        assertTrue(xs(Pick.Wall(2, 0)).all { it >= 299.99 })
        // Negli spigoli esterni della casa invece la facciata chiude l'angolo (fino a x = -7,5).
        assertEquals(-7.5, xs(Pick.Wall(1, 0)).min(), 1e-9)
    }

    @Test
    fun `camminando si passa dalla porta ma non dal muro`() {
        val door = Opening.default(1, OpeningKind.Door, 0, 150.0)
        val scene = Scene3D.build(FloorPlan(listOf(room(1, 300.0, 200.0, openings = listOf(door)))), null, null, ceilings = false)
        assertFalse(scene.blocked(Vec2(150.0, 0.0), 15.0))
        assertTrue(scene.blocked(Vec2(40.0, 0.0), 15.0))
    }

    @Test
    fun `il raggio al centro dello schermo va dove guarda la telecamera`() {
        val cam = Camera3D()
        val (_, dir) = cam.ray(500f, 400f, 1000f, 800f)
        val f = cam.forward
        assertEquals(1.0, dir dot f, 1e-6)
    }
}
