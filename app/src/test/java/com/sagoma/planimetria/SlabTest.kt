package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo spessore del solaio (30 cm) si vede: fascia di cemento sotto il pavimento di sopra e bordi del vuoto della scala. */
class SlabTest {
    private val room = Room(1, "Stanza", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))
    private val stair = Stair(1, StairKind.Straight, Vec2(250.0, 300.0))
    private val ground = Floor(1, "Piano terra", FloorPlan(listOf(room), stairs = listOf(stair)))
    private val groundNoStair = Floor(1, "Piano terra", FloorPlan(listOf(room)))

    /** Vertici (x, y, z) dei triangoli del colore del solaio (cemento). */
    private fun slabVertices(scene: Scene3D): List<Triple<Float, Float, Float>> {
        val a = scene.opaque
        val out = mutableListOf<Triple<Float, Float, Float>>()
        var i = 0
        while (i + 9 < a.size) {
            if (Math.abs(a[i + 6] - 0.60f) < 0.005f && Math.abs(a[i + 7] - 0.59f) < 0.005f && Math.abs(a[i + 8] - 0.57f) < 0.005f) out += Triple(a[i], a[i + 1], a[i + 2])
            i += 10
        }
        return out
    }

    private fun upper(below: Floor) = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false, below = listOf(below))

    @Test
    fun `la facciata esterna non ha fasce di colore diverso, il solaio si vede solo all'interno`() {
        val v = slabVertices(upper(groundNoStair))
        assertTrue("lo spessore del solaio si deve vedere sulle facce interne", v.isNotEmpty())
        assertEquals(-Floor.SLAB.toFloat(), v.minOf { it.second }, 1e-3f) // da 30 cm sotto il pavimento
        assertEquals(0f, v.maxOf { it.second }, 1e-3f) // fino al pavimento
        // La stanza va da (0,0) a (500,600) in mezzeria dei muri: la facciata sta fuori da questo rettangolo (muro da 15 cm),
        // e lì non ci deve essere nessun triangolo del colore del solaio.
        assertTrue("fascia sulla facciata esterna", v.all { it.first in -1e-3f..500.001f && it.third in -1e-3f..600.001f })
        // Un raggio che arriva alla parete da fuori, nella zona del solaio (y = -15), trova la parete, non un'altra superficie.
        val scene = upper(groundNoStair)
        val hit = scene.hit(com.sagoma.planimetria.geometry.Vec3(-300.0, -15.0, 300.0), com.sagoma.planimetria.geometry.Vec3(1.0, 0.0, 0.0))
        assertTrue(hit != null && hit.point.x < 0.0)
    }

    @Test
    fun `al piano terra non c'e nessuna fascia`() {
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        assertTrue(slabVertices(scene).isEmpty())
    }

    @Test
    fun `il vuoto della scala nel pavimento mostra lo spessore del solaio`() {
        // Dentro la stanza, sul vuoto della scala: lontano dai muri (le fasce dei muri stanno sul perimetro).
        fun inHole(v: Triple<Float, Float, Float>) = v.first in 190f..310f && v.third in 50f..550f
        val without = slabVertices(upper(groundNoStair)).count(::inHole)
        val with = slabVertices(upper(ground)).filter(::inHole)
        assertEquals(0, without)
        assertTrue("i bordi del vuoto devono avere la fascia", with.isNotEmpty())
        assertTrue(with.all { it.second >= -Floor.SLAB.toFloat() - 1e-3f && it.second <= 1e-3f })
        assertEquals(-Floor.SLAB.toFloat(), with.minOf { it.second }, 1e-3f)
        assertEquals(0f, with.maxOf { it.second }, 1e-3f)
    }

    @Test
    fun `la fascia segue l'altezza del solaio`() {
        assertEquals(30.0, Floor.SLAB, 0.0) // lo spessore non e stato cambiato
    }

    @Test
    fun `i bordi del vuoto stanno sul contorno, mai dentro il vuoto, anche per scale a due pezzi`() {
        val big = Room(1, "Grande", RoomType.Altro, RoomFactory.rectangle(900.0, 900.0))
        for (kind in listOf(StairKind.Straight, StairKind.LTurn, StairKind.LWinder, StairKind.UTurn, StairKind.StraightLanding)) {
            val s = Stair(1, kind, Vec2(450.0, 600.0))
            val pieces = com.sagoma.planimetria.geometry.Stairs.well(s, 300.0)
            val scene = Scene3D.build(
                FloorPlan(listOf(big)), null, null, ceilings = false,
                below = listOf(Floor(1, "Piano terra", FloorPlan(listOf(big), stairs = listOf(s)))),
            )
            fun inUnion(p: Vec2) = pieces.any { com.sagoma.planimetria.geometry.Polygon.contains(it, p) }
            fun deep(p: Vec2) = listOf(Vec2(1.0, 0.0), Vec2(-1.0, 0.0), Vec2(0.0, 1.0), Vec2(0.0, -1.0)).all { inUnion(p + it) } && inUnion(p)
            val a = scene.opaque
            var drawn = 0
            var i = 0
            while (i + 29 < a.size) { // un triangolo = 3 vertici da 10 numeri
                fun v(k: Int) = Triple(a[i + 10 * k], a[i + 10 * k + 1], a[i + 10 * k + 2])
                fun slab(k: Int) = Math.abs(a[i + 10 * k + 6] - 0.60f) < 0.005f && Math.abs(a[i + 10 * k + 7] - 0.59f) < 0.005f && Math.abs(a[i + 10 * k + 8] - 0.57f) < 0.005f
                if (slab(0) && slab(1) && slab(2)) {
                    for ((p, q) in listOf(0 to 1, 1 to 2, 2 to 0)) {
                        val (x0, y0, z0) = v(p); val (x1, y1, z1) = v(q)
                        if (Math.abs(y0 - y1) > 1e-3f || x0 !in 20f..880f || z0 !in 20f..880f) continue // solo i bordi orizzontali nel vuoto
                        drawn++
                        for (t in 1..19) {
                            val pt = Vec2((x0 + (x1 - x0) * t / 20.0), (z0 + (z1 - z0) * t / 20.0))
                            assertTrue("$kind: bordo dentro il vuoto in $pt", !deep(pt))
                        }
                    }
                }
                i += 30
            }
            assertTrue("$kind: i bordi del vuoto devono esserci", drawn > 0)
        }
    }
}
