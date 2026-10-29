package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Piano di sopra più piccolo di quello di sotto, con la scala di sotto dove il piano di sopra non arriva (foto di Marco del
 * 29/10): la stanza della scala restava aperta in alto e dal piano di sopra si vedevano il suo pavimento e la scala. Ora il
 * tetto piano la chiude come le altre stanze, tranne il vano della scala. E camminando, da fuori, tra la cima dei muri di
 * sotto e i muri di sopra c'era una fessura da cui si vedeva dentro il piano di sopra.
 */
class RoofOverStairTest {
    private fun room(id: Long, w: Double, h: Double, at: Vec2 = Vec2.Zero) =
        Room(id, "Stanza $id", RoomType.Soggiorno, RoomFactory.rectangle(w, h).map { it + at })

    /** Quote delle facce opache rivolte in su che coprono il punto `p` in pianta. */
    private fun upAt(scene: Scene3D, p: Vec2): List<Double> {
        val d = scene.opaque
        val out = mutableListOf<Double>()
        var i = 0
        while (i + 30 <= d.size) {
            val ny = d[i + 4]
            if (ny > 0.9f) {
                val a = Vec2(d[i].toDouble(), d[i + 2].toDouble())
                val b = Vec2(d[i + 10].toDouble(), d[i + 12].toDouble())
                val c = Vec2(d[i + 20].toDouble(), d[i + 22].toDouble())
                if (Polygon.contains(listOf(a, b, c), p)) out += d[i + 1].toDouble()
            }
            i += 30
        }
        return out
    }

    /** Distanza del primo triangolo opaco colpito dal raggio, o null. */
    private fun firstHit(scene: Scene3D, o: Vec3, d: Vec3): Double? {
        val v = scene.opaque
        var best: Double? = null
        var i = 0
        while (i + 30 <= v.size) {
            fun p(k: Int) = Vec3(v[i + k].toDouble(), v[i + k + 1].toDouble(), v[i + k + 2].toDouble())
            val a = p(0); val b = p(10); val c = p(20)
            val e1 = b - a; val e2 = c - a
            val q = d cross e2
            val det = e1 dot q
            if (abs(det) > 1e-9) {
                val s = o - a
                val u = (s dot q) / det
                val r = s cross e1
                val w = (d dot r) / det
                val t = (e2 dot r) / det
                if (u >= 0 && w >= 0 && u + w <= 1 && t > 1e-3 && (best == null || t < best)) best = t
            }
            i += 30
        }
        return best
    }

    private val stair = Stair(1, StairKind.Straight, Vec2(700.0, 330.0))
    private val ground = FloorPlan(listOf(room(1, 800.0, 600.0), room(2, 300.0, 600.0, Vec2(800.0, 0.0))), stairs = listOf(stair))
    private val upper = FloorPlan(listOf(room(3, 500.0, 400.0)))

    @Test
    fun `il tetto piano copre la stanza della scala tranne il vano`() {
        // Piano terra: soggiorno 800×600 con la scala a destra e una camera 300×600 accanto; di sopra una stanza 500×400.
        val scene = Scene3D.build(upper, null, null, ceilings = false, levelHeight = 300.0, below = listOf(Floor(1, "Terra", ground, 300.0)))
        // Il tetto piano del piano di sotto: 2 cm sopra il suo soffitto (270), 300 sotto il piano di sopra.
        val lid = -300.0 + 270.0 + 2.0
        fun roofed(p: Vec2) = upAt(scene, p).any { abs(it - lid) < 0.5 }
        val well = Stairs.well(stair, 300.0)
        val open = Vec2(300.0, 520.0)
        assertTrue(well.none { Polygon.contains(it, open) } && !Polygon.contains(upper.rooms[0].points, open))
        assertTrue(roofed(open), "stanza della scala aperta in alto: ${upAt(scene, open)}")
        assertTrue(roofed(Vec2(950.0, 300.0)), "camera accanto")
        // La scala sta tutta fuori dalla stanza di sopra: il tetto la copre, aperto solo sopra gli ultimi gradini, che lo
        // attraverserebbero.
        val l = Stairs.layout(stair, 300.0)
        fun center(st: Stairs.Step) = st.polygon.reduce { a, b -> a + b } / st.polygon.size.toDouble()
        assertTrue(roofed(center(l.steps.first())), "primi gradini senza tetto")
        assertTrue(!roofed(center(l.steps.last())), "ultimo gradino sotto il tetto")
        assertTrue(well.isNotEmpty())
    }

    @Test
    fun `il vano sul tetto ha il bordo del solaio fino al pavimento di sopra`() {
        val scene = Scene3D.build(upper, null, null, ceilings = false, levelHeight = 300.0, below = listOf(Floor(1, "Terra", ground, 300.0)))
        // Facce verticali dal tetto piano (-28) al pavimento di sopra (0): la ringhiera del vano non resta sospesa.
        val v = scene.opaque
        var sides = 0
        var i = 0
        while (i + 30 <= v.size) {
            val ys = listOf(v[i + 1], v[i + 11], v[i + 21]).map { it.toDouble() }
            if (abs(v[i + 4]) < 0.1 && ys.all { abs(it + 28.0) < 0.5 || abs(it) < 0.5 } && ys.any { abs(it) < 0.5 } && ys.any { abs(it + 28.0) < 0.5 }) sides++
            i += 30
        }
        assertTrue(sides >= 4, "bordo del vano sul tetto: $sides triangoli")
    }

    @Test
    fun `camminando da fuori non si vede dentro il piano di sopra`() {
        // Piano terra corrente, camminando: di sopra la stanza 500×400 rientra rispetto al soggiorno 800×600.
        val scene = Scene3D.build(ground, null, null, ceilings = true, levelHeight = 300.0, above = Floor(2, "Primo", upper, 300.0))
        // Da fuori, poco sopra la cima del muro di sotto (270), verso il muro di sopra a y = 400: lo si colpisce sulla faccia
        // esterna (prima la fessura tra 270 e 301 lasciava vedere dentro).
        for (h in listOf(275.0, 290.0)) {
            val t = firstHit(scene, Vec3(250.0, h, 900.0), Vec3(0.0, 0.0, -1.0))
            assertTrue(t != null && abs(t - (900.0 - 407.5)) < 2.0, "a $h cm: $t")
        }
        // Dentro il soggiorno, appena sotto il soffitto: il muro di sopra non sporge sotto il soffitto.
        val inside = firstHit(scene, Vec3(250.0, 265.0, 500.0), Vec3(0.0, 0.0, -1.0))
        assertTrue(inside != null && inside > 450.0, "muro di sopra sotto il soffitto: $inside")
    }

    @Test
    fun `vano e ringhiera solo dentro la stanza di sopra`() {
        // Foto di Marco: il vano della scala usciva dalla stanza di sopra e la sua ringhiera stava fuori, sul tetto. Ora la stanza
        // di sopra copre solo la metà della scala verso l'arrivo: l'altra metà resta sotto il tetto, senza ringhiera.
        val s = Stair(1, StairKind.Straight, Vec2(400.0, 300.0), wellRailing = StairRailing.Metal)
        val l = Stairs.layout(s, 300.0)
        val start = l.path.first()
        val end = l.path.last()
        assertTrue(end.y < start.y, "la scala sale verso y più piccole: ${l.path}")
        val midY = (start.y + end.y) / 2
        val down = FloorPlan(listOf(room(1, 800.0, 600.0)), stairs = listOf(s))
        val up = FloorPlan(listOf(room(3, 800.0, midY)))
        val scene = Scene3D.build(up, null, null, ceilings = false, levelHeight = 300.0, below = listOf(Floor(1, "Terra", down, 300.0)))
        val side = l.bounds.minX
        // Ringhiera (chi cammina si ferma) lungo il lato del vano dentro la stanza, non fuori.
        assertTrue(scene.blocked(Vec2(side, (end.y + midY) / 2), 5.0), "ringhiera dentro la stanza")
        assertTrue(!scene.blocked(Vec2(side, (start.y + midY) / 2 + 20.0), 5.0), "ringhiera fuori dalla stanza, sul tetto")
        // Fuori dalla stanza sopra i primi gradini il tetto è chiuso.
        val lid = -300.0 + 270.0 + 2.0
        assertTrue(upAt(scene, Vec2(400.0, (start.y + midY) / 2 + 20.0)).any { abs(it - lid) < 0.5 }, "vano aperto fuori dalla stanza")
    }
}
