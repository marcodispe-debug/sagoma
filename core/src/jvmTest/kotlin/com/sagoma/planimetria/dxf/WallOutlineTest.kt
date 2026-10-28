package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contorno dei muri con la griglia: deve venire uguale al modo lento e sicuro (ogni lato con tutti gli altri, ogni punto con
 * tutti i pezzi), anche con pezzi storti, sovrapposti, lunghissimi o rovinati.
 */
class WallOutlineTest {
    /** Il modo lento: come la prima versione, senza griglia. */
    private fun slow(pieces: List<List<Vec2>>): List<Pair<Vec2, Vec2>> {
        val ps = pieces.filter { pc -> pc.size >= 3 && pc.all { it.x.isFinite() && it.y.isFinite() } && abs(area(pc)) > 1e-4 }
        val edges = ps.flatMap { p -> p.indices.map { k -> p[k] to p[(k + 1) % p.size] } }.filter { (a, b) -> a.distanceTo(b) > 1e-3 }
        fun holds(p: List<Vec2>, q: Vec2): Boolean {
            if (q.x <= p.minOf { it.x } || q.x >= p.maxOf { it.x } || q.y <= p.minOf { it.y } || q.y >= p.maxOf { it.y }) return false
            val sign = if (area(p) >= 0) 1.0 else -1.0
            return p.indices.all { k ->
                val a = p[k]
                val b = p[(k + 1) % p.size]
                ((b.x - a.x) * (q.y - a.y) - (b.y - a.y) * (q.x - a.x)) * sign > 0
            }
        }
        fun inside(q: Vec2) = ps.any { holds(it, q) }
        val kept = mutableListOf<Pair<Vec2, Vec2>>()
        for ((a, b) in edges) {
            val d = b - a
            val len = d.length
            val ts = mutableListOf(0.0, 1.0)
            for ((c, e) in edges) {
                val f = e - c
                val den = d.x * f.y - d.y * f.x
                if (abs(den) > 1e-9 * len * f.length) {
                    val w = c - a
                    val t = (w.x * f.y - w.y * f.x) / den
                    val s = (w.x * d.y - w.y * d.x) / den
                    if (t > 0 && t < 1 && s >= -1e-9 && s <= 1 + 1e-9) ts += t
                } else for (p in listOf(c, e)) {
                    if (abs((p.x - a.x) * d.y - (p.y - a.y) * d.x) / len > 1e-3) continue
                    val t = ((p - a) dot d) / (len * len)
                    if (t > 0 && t < 1) ts += t
                }
            }
            ts.sort()
            val n = Vec2(-d.y, d.x) / len * 0.02
            for (k in 0 until ts.size - 1) {
                if ((ts[k + 1] - ts[k]) * len < 1e-3) continue
                val m = a + d * ((ts[k] + ts[k + 1]) / 2)
                if (inside(m + n) != inside(m - n)) kept += (a + d * ts[k]) to (a + d * ts[k + 1])
            }
        }
        // Stessa unione dei pezzi sulla stessa linea.
        val lines = LinkedHashMap<Pair<Long, Long>, MutableList<Pair<Double, Double>>>()
        val dirs = HashMap<Pair<Long, Long>, Pair<Double, Double>>()
        for ((a, b) in kept) {
            var d = (b - a).normalized()
            if (d.x < -1e-9 || (abs(d.x) <= 1e-9 && d.y < 0)) d = d * -1.0
            var angle = atan2(d.y, d.x)
            if (angle <= -kotlin.math.PI / 2 + 1e-7) { angle += kotlin.math.PI; d = d * -1.0 }
            val offset = d.x * a.y - d.y * a.x
            val key = round(angle * 1e5).toLong() to round(offset * 100).toLong()
            dirs.getOrPut(key) { angle to offset }
            lines.getOrPut(key) { mutableListOf() } += min(a dot d, b dot d) to max(a dot d, b dot d)
        }
        val out = mutableListOf<Pair<Vec2, Vec2>>()
        for ((key, intervals) in lines) {
            val (angle, offset) = dirs.getValue(key)
            val d = Vec2(cos(angle), sin(angle))
            fun at(t: Double) = d * t + Vec2(-d.y, d.x) * offset
            val sorted = intervals.sortedBy { it.first }
            var s = sorted.first().first
            var e = sorted.first().second
            for ((x, y) in sorted.drop(1)) {
                if (x <= e + 0.01) e = max(e, y) else { out += at(s) to at(e); s = x; e = y }
            }
            out += at(s) to at(e)
        }
        return out
    }

    private fun area(p: List<Vec2>) = p.indices.sumOf { k -> val a = p[k]; val b = p[(k + 1) % p.size]; a.x * b.y - b.x * a.y } / 2

    /** Rettangolo largo `w` lungo da `a` a `b`. */
    private fun band(a: Vec2, b: Vec2, w: Double): List<Vec2> {
        val n = (b - a).normalized().let { Vec2(-it.y, it.x) } * (w / 2)
        return listOf(a + n, b + n, b - n, a - n)
    }

    private fun assertSame(expected: List<Pair<Vec2, Vec2>>, actual: List<Pair<Vec2, Vec2>>, what: String) {
        fun norm(l: List<Pair<Vec2, Vec2>>) = l.map { (a, b) -> if (a.x < b.x || (a.x == b.x && a.y <= b.y)) a to b else b to a }
            .sortedWith(compareBy({ round(it.first.x * 1000) }, { round(it.first.y * 1000) }, { round(it.second.x * 1000) }, { round(it.second.y * 1000) }))
        val e = norm(expected)
        val a = norm(actual)
        assertEquals(e.size, a.size, "$what: numero di linee")
        for ((x, y) in e.zip(a)) {
            assertTrue(x.first.distanceTo(y.first) < 1e-6 && x.second.distanceTo(y.second) < 1e-6, "$what: $x invece di $y")
        }
    }

    @Test
    fun `uguale al modo lento con muri a caso`() {
        val random = Random(28)
        repeat(40) { round ->
            val pieces = (0 until 30).map {
                // Metà sulla griglia da 10 cm (muri in comune, angoli che combaciano), metà storti; ogni tanto lunghissimi.
                val grid = random.nextBoolean()
                val a = if (grid) Vec2(random.nextInt(0, 60) * 10.0, random.nextInt(0, 60) * 10.0) else Vec2(random.nextDouble(0.0, 600.0), random.nextDouble(0.0, 600.0))
                val len = if (random.nextInt(10) == 0) random.nextDouble(2000.0, 20_000.0) else random.nextDouble(20.0, 400.0)
                val dir = if (grid) listOf(Vec2(1.0, 0.0), Vec2(0.0, 1.0)).random(random) else random.nextDouble(0.0, 6.3).let { Vec2(cos(it), sin(it)) }
                band(a, a + dir * len, if (grid) listOf(10.0, 20.0, 30.0).random(random) else random.nextDouble(5.0, 40.0))
            }
            assertSame(slow(pieces), WallOutline.boundary(pieces), "giro $round")
        }
    }

    @Test
    fun `pezzi rovinati e lati lunghissimi`() {
        val nan = listOf(Vec2(0.0, 0.0), Vec2(Double.NaN, 0.0), Vec2(10.0, 10.0))
        val flat = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(200.0, 0.0))
        val huge = band(Vec2(-1e7, 0.0), Vec2(1e7, 3e6), 30.0)
        val normal = band(Vec2(0.0, 50.0), Vec2(400.0, 50.0), 30.0)
        val pieces = listOf(nan, flat, huge, normal, emptyList())
        assertSame(slow(pieces), WallOutline.boundary(pieces), "rovinati")
        assertEquals(emptyList(), WallOutline.boundary(listOf(nan, flat)))
        // Un muro solo: i suoi quattro lati.
        assertEquals(4, WallOutline.boundary(listOf(normal)).size)
    }

    @Test
    fun `pezzi con un punto doppio coprono come gli altri`() {
        // Tagliati da una finestra con lo sguincio o da un muro tolto, i pezzi di muro hanno un punto ripetuto: prima non
        // coprivano niente e dentro il muro restavano linee (nella pianta DXF).
        val doubled = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 20.0), Vec2(100.0, 20.0), Vec2(0.0, 20.0))
        val over = listOf(Vec2(50.0, 10.0), Vec2(150.0, 10.0), Vec2(150.0, 30.0), Vec2(50.0, 30.0))
        // Contorno a 8 lati, nessuno dentro.
        assertEquals(8, WallOutline.boundary(listOf(doubled, over)).size)
    }
}
