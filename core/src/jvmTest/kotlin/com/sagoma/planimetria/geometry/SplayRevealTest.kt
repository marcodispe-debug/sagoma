package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FramePosition
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.WindowShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sguincio delle finestre nel 3D: il vano va dal bordo del varco sulla mezzeria del muro a quello più largo sulla faccia verso la
 * stanza, e segue i lati, l'arco e il cerchio dell'oblò. Niente buchi tra vano e muro, niente doppie facce (sul computer
 * sfarfallano a puntini scuri), facce rivolte verso il varco e la stanza (sul telefono il retro non si vede).
 */
class SplayRevealTest {
    // Muro 0 da (0, 0) a (400, 0), spesso 30: mezzeria a z = 0, faccia verso la stanza a z = 15. Finestra a metà muro.
    private val half = 15.0
    private val splay = 12.0
    private val pick = Pick.Opening(1, 10)

    private fun scene(o: Opening): Scene3D {
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 350.0), wallThickness = 2 * half, openings = listOf(o))
        return Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
    }

    private val rect = Opening.default(10, OpeningKind.Window2, 0, 200.0).copy(splay = splay, frame = FramePosition.Outside)
    private val arched = rect.copy(windowShape = WindowShape.Arched, height = 160.0)
    private val round = Opening.default(10, OpeningKind.Window1, 0, 200.0)
        .copy(windowShape = WindowShape.Round, width = 80.0, height = 80.0, sillHeight = 110.0, splay = splay, frame = FramePosition.Outside)

    /** Cosa si vede guardando il muro dalla stanza, dritti, nel punto (t, y): oggetto e profondità dalla mezzeria. */
    private fun look(s: Scene3D, t: Double, y: Double): Pair<Pick, Double>? =
        s.hit(Vec3(t, y, 200.0), Vec3(0.0, 0.0, -1.0))?.let { it.pick to it.point.z }

    /** Punto (t, y) nel vano: si vede lo sguincio, tra la mezzeria e la faccia. */
    private fun assertReveal(s: Scene3D, t: Double, y: Double, what: String) {
        val (p, depth) = look(s, t, y) ?: error("$what: non si vede niente in ($t, $y)")
        assertEquals(pick, p, "$what in ($t, $y)")
        assertTrue(depth > 0.3 && depth < half - 0.3, "$what in ($t, $y): profondità $depth")
    }

    /**
     * Punto (t, y) appena fuori dal varco allargato: c'è la faccia del muro, senza fessure (sopra, sotto e attorno al varco il muro
     * si tocca per scegliere l'apertura).
     */
    private fun assertFace(s: Scene3D, t: Double, y: Double, what: String) {
        val (p, depth) = look(s, t, y) ?: error("$what: muro bucato in ($t, $y)")
        assertTrue(p == Pick.Wall(1, 0) || p == pick, "$what in ($t, $y): $p")
        assertEquals(half, depth, 1e-6, "$what in ($t, $y)")
    }

    /**
     * Triangoli del vano: dell'apertura, tra la mezzeria e la faccia verso la stanza (l'infisso sta fuori), e non piatti lungo il
     * muro, in orizzontale o di traverso come i pezzi di muro sopra e sotto il varco.
     */
    private fun reveal(s: Scene3D): List<Scene3D.PickTri> = s.picks.filter { t ->
        val vs = listOf(t.a, t.b, t.c)
        fun flat(c: (Vec3) -> Double) = vs.maxOf(c) - vs.minOf(c) < 1e-6
        t.pick == pick && vs.all { it.z in -0.01..half + 0.01 } && !flat { it.x } && !flat { it.y } && !flat { it.z }
    }

    /** Punto (t, y) dentro il varco vero: lo sguincio non lo copre, si arriva all'infisso (dalla parte esterna). */
    private fun assertOpen(s: Scene3D, t: Double, y: Double, what: String) {
        val depth = look(s, t, y)?.second ?: return
        assertTrue(depth < 0.0, "$what in ($t, $y): profondità $depth")
    }

    @Test
    fun `finestra rettangolare`() {
        for (rounded in listOf(false, true)) {
            val s = scene(rect.copy(splayRounded = rounded))
            val what = if (rounded) "arrotondato" else "dritto"
            val y = 150.0
            for ((t, out) in listOf(140.0 to -1.0, 260.0 to 1.0)) {
                assertReveal(s, t + out * splay / 2, y, what)
                assertFace(s, t + out * (splay + 1), y, what)
                assertOpen(s, t - out, y, what)
            }
            // Sopra e sotto il varco resta dritto: il muro arriva fino al bordo.
            assertFace(s, 200.0, 211.0, what)
            assertFace(s, 200.0, 89.0, what)
        }
    }

    @Test
    fun `finestra ad arco`() {
        // Varco da 140 a 260, da 90 a 250; sulla faccia da 128 a 272, con la chiave dell'arco alla stessa quota.
        for (rounded in listOf(false, true)) {
            val s = scene(arched.copy(splayRounded = rounded))
            val what = if (rounded) "arco arrotondato" else "arco dritto"
            for (out in listOf(-1.0, 1.0)) {
                assertReveal(s, 200.0 + out * (60.0 + splay / 2), 150.0, what)
                assertFace(s, 200.0 + out * (60.0 + splay + 1), 150.0, what)
                // Lungo l'arco, a metà tra la chiave e l'imposta: tra il bordo vero e quello allargato c'è il vano.
                val t = 200.0 + out * 30.0
                val inner = Openings.archTop(PassageStyle.Arched, 140.0, 260.0, 90.0, 250.0, t)
                val outer = Openings.archTop(PassageStyle.Arched, 140.0 - splay, 260.0 + splay, 90.0, 250.0, t)
                assertTrue(outer > inner + 2, "$what: l'arco sulla faccia è più largo")
                assertReveal(s, t, (inner + outer) / 2, what)
                assertFace(s, t, outer + 1, what)
                assertOpen(s, t, inner - 1, what)
            }
            assertFace(s, 200.0, 251.0, what)
        }
    }

    @Test
    fun `oblo`() {
        // Cerchio di raggio 40 con il centro a 150; sulla faccia raggio 52, stesso centro.
        for (rounded in listOf(false, true)) {
            val s = scene(round.copy(splayRounded = rounded))
            val what = if (rounded) "oblò arrotondato" else "oblò dritto"
            for (k in 0 until 8) {
                val a = 2 * PI * (k + 0.5) / 8
                fun at(r: Double) = (200.0 + r * cos(a)) to (150.0 + r * sin(a))
                at(40.0 + splay / 2).let { (t, y) -> assertReveal(s, t, y, "$what a ${k * 45 + 22}°") }
                at(40.0 + splay + 1).let { (t, y) -> assertFace(s, t, y, "$what a ${k * 45 + 22}°") }
                at(39.0).let { (t, y) -> assertOpen(s, t, y, "$what a ${k * 45 + 22}°") }
            }
        }
    }

    @Test
    fun `l'oblo non si allarga sotto il pavimento ne sopra il muro`() {
        // Davanzale a 5 cm: sguincio di 5 cm al massimo. Cima a 260 con il muro alto 270: 9 cm al massimo.
        for ((sill, low, high) in listOf(Triple(5.0, 0.0, 85.0 + 5.0), Triple(180.0, 180.0 - 9.0, 269.0))) {
            val s = scene(round.copy(sillHeight = sill, splay = 30.0))
            val ys = reveal(s).flatMap { listOf(it.a.y, it.b.y, it.c.y) }
            assertEquals(low, ys.min(), 1e-6, "davanzale $sill")
            assertTrue(ys.max() <= high + 1e-6, "davanzale $sill: cima ${ys.max()}")
        }
    }

    @Test
    fun `niente doppie facce e vano rivolto verso il varco`() {
        val n = Scene3D.FLOATS_PER_VERTEX
        // Vertici arrotondati al decimo di mm (come float, così i triangoli toccabili ritrovano quelli da disegnare).
        fun key(vs: List<Vec3>) = vs.map { v -> listOf(v.x, v.y, v.z).map { round(it.toFloat() * 10.0).toLong() } }
            .sortedWith(compareBy({ it[0] }, { it[1] }, { it[2] })).flatten()
        for (base in listOf(rect, arched, round)) for (rounded in listOf(false, true)) for (frame in FramePosition.entries) {
            val o = base.copy(splayRounded = rounded, frame = frame)
            val what = "${o.windowShape} ${if (rounded) "arrotondato" else "dritto"} $frame"
            val s = scene(o)
            val src = s.opaque
            // Triangoli uguali con le normali opposte: sul computer (che disegna anche il retro) sfarfallano.
            val normals = HashMap<List<Long>, MutableList<Vec3>>()
            var k = 0
            while (k + 3 * n <= src.size) {
                val vs = (0 until 3).map { v -> Vec3(src[k + v * n].toDouble(), src[k + v * n + 1].toDouble(), src[k + v * n + 2].toDouble()) }
                val nor = Vec3(src[k + 3].toDouble(), src[k + 4].toDouble(), src[k + 5].toDouble())
                val list = normals.getOrPut(key(vs)) { ArrayList() }
                assertTrue(list.none { (it dot nor) < -0.5 }, "$what: doppia faccia $vs")
                list += nor
                k += 3 * n
            }
            if (frame != FramePosition.Outside) continue
            // Il vano guarda la stanza e l'asse del varco, cioè la verticale a metà (dal davanzale all'imposta dell'arco, a 250 − 48) o il centro dell'oblò.
            val tris = reveal(s)
            assertTrue(tris.size >= 4, "$what: vano assente")
            for (t in tris) {
                val nor = normals[key(listOf(t.a, t.b, t.c))]?.single() ?: error("$what: vano senza triangolo da disegnare")
                val c = (t.a + t.b + t.c) * (1.0 / 3)
                val axis = if (base === round) 150.0 else c.y.coerceIn(90.0, if (base === arched) 202.0 else 210.0)
                assertTrue(nor.z > -1e-3, "$what: vano rivolto verso il muro in $c")
                assertTrue(nor.x * (200.0 - c.x) + nor.y * (axis - c.y) > 0, "$what: vano rivolto fuori dal varco in $c")
            }
        }
    }

    @Test
    fun `stessa cosa nell'altra stanza del muro in comune`() {
        // Due stanze attaccate: la finestra della prima sul muro in comune, sguincio verso la prima e niente doppie facce.
        val a = Room(1, "A", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 350.0), wallThickness = 2 * half, openings = listOf(rect.copy(wallIndex = 2)))
        val b = Room(2, "B", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0).map { it.copy(y = it.y + 350.0) }, wallThickness = 2 * half)
        val s = Scene3D.build(FloorPlan(listOf(a, b)), null, null, ceilings = true)
        // Dalla prima stanza (sotto il muro y = 350) si vede il vano, dalla seconda il varco dritto.
        val fromA = s.hit(Vec3(140.0 - splay / 2, 150.0, 200.0), Vec3(0.0, 0.0, 1.0))
        assertEquals(pick, fromA?.pick)
        assertTrue(fromA!!.point.z > 350.0 - half + 0.3 && fromA.point.z < 350.0 - 0.3, "profondità ${fromA.point.z}")
        val fromB = s.hit(Vec3(140.0 - splay / 2, 150.0, 500.0), Vec3(0.0, 0.0, -1.0))
        assertEquals(350.0 + half, fromB!!.point.z, 1e-6)
    }
}
