package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Catena di quote su un lato della casa, fuori dal disegno, come nelle tavole degli architetti. La prima fila va da un
 * segno all'altro di [marks]: spessore del muro esterno, luce della stanza, tramezzo, luce della stanza dopo… e i balconi
 * che sporgono oltre la casa. La seconda fila è il totale della casa ([total], senza balconi).
 * Le posizioni sono in cm lungo [along]; un punto della pianta è `along * t + out * s`.
 */
data class DimensionChain(
    /** Direzione della catena (unitaria). */
    val along: Vec2,
    /** Normale verso fuori dalla casa, da questo lato. */
    val out: Vec2,
    /** Il punto più esterno del disegno da questo lato (lungo [out], cm): le file di quote stanno oltre. */
    val edge: Double,
    /** Segni della prima fila, in ordine. */
    val marks: List<Double>,
    val total: ClosedFloatingPointRange<Double>,
) {
    /** Punto della pianta a `t` lungo la catena, sul bordo esterno del disegno. */
    fun at(t: Double): Vec2 = along * t + out * edge
}

/**
 * Quote esterne della pianta. [covered]: muri (id della stanza, indice del muro) la cui misura interna si legge già nelle
 * catene, quindi dentro la stanza non serve ripeterla; gli altri muri si quotano dentro. [exterior]: per ogni muro della
 * pianta srotolata ([expandCurves]) i tratti che danno fuori, in cm dall'inizio del muro (per scrivere fuori le misure di
 * porte e finestre).
 */
class ExteriorDimensions(
    val chains: List<DimensionChain>,
    val covered: Set<Pair<Long, Int>>,
    val exterior: Map<Pair<Long, Int>, List<ClosedFloatingPointRange<Double>>>,
) {
    /** Il punto a `t` cm dall'inizio del muro `wall` (della stanza srotolata) dà fuori casa. */
    fun outsideAt(roomId: Long, wall: Int, t: Double): Boolean = exterior[roomId to wall].orEmpty().any { t in it }
}

object ExteriorChains {
    /** Distanza dai fili dei muri a cui si guarda cosa c'è (cm): appena fuori dal filo esterno, appena dentro quello interno. */
    private const val PROBE = 2.0
    /** Due segni più vicini di così sono lo stesso segno. */
    private const val SAME_MARK = 0.5
    /** Muri considerati paralleli al lato (coseno). */
    private const val FACING = 0.999

    private class Shape(val id: Long, val room: com.sagoma.planimetria.model.Room, val logical: com.sagoma.planimetria.model.Room, val origin: List<Int>) {
        val n = room.wallCount
        val ext: List<Vec2> = Polygon.inset(room.points, room.outerThicknesses.map { -it / 2 })
        val int: List<Vec2> = Polygon.inset(room.points, room.innerThicknesses.map { it / 2 })
        fun next(i: Int) = (i + 1) % n
    }

    /**
     * Tratto di facciata rivolto verso un lato: da `from` a `to` lungo la catena, col filo interno del muro a `inner` e quello
     * esterno a `outer` (lungo out).
     */
    private class Facade(val shape: Shape, val wall: Int, val from: Double, val to: Double, val inner: Double, val outer: Double)

    fun of(plan: FloorPlan): ExteriorDimensions {
        val shapes = plan.rooms.filter { !it.outdoor && it.wallCount >= 3 }.map { r ->
            val e = r.expandCurves()
            Shape(r.id, e.room, r, e.origin)
        }
        if (shapes.isEmpty()) return ExteriorDimensions(emptyList(), emptySet(), emptyMap())

        // Muri pieni (quadrilateri tra filo esterno e filo interno), senza i muri eliminati; più i muri singoli.
        val solids = shapes.flatMap { s ->
            (0 until s.n).filter { it !in s.room.removedWalls && s.room.wallLength(it) > 1e-6 }
                .map { i -> listOf(s.ext[i], s.ext[s.next(i)], s.int[s.next(i)], s.int[i]) }
        } + plan.freeWalls.filter { it.arc() == null && it.start.distanceTo(it.end) > 1e-6 }.map { w ->
            val half = (w.end - w.start).normalized().perp() * (w.thickness / 2)
            listOf(w.start + half, w.end + half, w.end - half, w.start - half)
        }

        // Tratti di ogni muro che danno fuori: appena fuori dal filo esterno non c'è un'altra stanza.
        val exterior = mutableMapOf<Pair<Long, Int>, List<ClosedFloatingPointRange<Double>>>()
        for (s in shapes) for (i in 0 until s.n) {
            if (i in s.room.removedWalls) continue
            val a = s.room.wallStart(i)
            val len = s.room.wallLength(i)
            if (len < 1.0) continue
            val u = (s.room.wallEnd(i) - a) / len
            val o = s.room.outwardOf(i)
            val outer = (s.ext[i] - a) dot o
            val from = (s.ext[i] - a) dot u
            val to = (s.ext[s.next(i)] - a) dot u
            if (to - from < 1.0) continue
            val base = a + o * (outer + PROBE)
            val covered = shapes.filter { it !== s }.flatMap { other -> lineInside(other.ext, base, u) }
            val free = subtract(from..to, merge(covered)).filter { it.endInclusive - it.start >= 1.0 }
            if (free.isNotEmpty()) exterior[s.id to i] = free
        }

        // Direzioni della casa: quella dei muri più lunghi (a meno di 90°), e la perpendicolare.
        val angle = mainAngle(shapes)
        val axis = Vec2(cos(angle), sin(angle))
        val sides = listOf(axis.perp() * -1.0, axis.perp(), axis * -1.0, axis)

        val facades = List(4) { mutableListOf<Facade>() }
        for (s in shapes) for (i in 0 until s.n) {
            val stretches = exterior[s.id to i] ?: continue
            val o = s.room.outwardOf(i)
            val side = sides.indexOfFirst { (it dot o) > FACING }
            if (side < 0) continue
            val along = alongOf(sides[side])
            val a = s.room.wallStart(i)
            val u = (s.room.wallEnd(i) - a).normalized()
            val inner = ((s.int[i] dot o) + (s.int[s.next(i)] dot o)) / 2
            val outer = ((s.ext[i] dot o) + (s.ext[s.next(i)] dot o)) / 2
            for (r in stretches) {
                val p = (a + u * r.start) dot along
                val q = (a + u * r.endInclusive) dot along
                facades[side] += Facade(s, i, min(p, q), max(p, q), inner, outer)
            }
        }

        val chains = mutableListOf<DimensionChain>()
        val covered = mutableSetOf<Pair<Long, Int>>()
        for (side in 0 until 4) {
            if (facades[side].isEmpty()) continue
            val out = sides[side]
            val along = alongOf(out)
            val house = shapes.flatMap { it.ext }.map { it dot along }
            val total = house.min()..house.max()
            val marks = mutableListOf(total.start, total.endInclusive)
            for (f in facades[side]) {
                // La facciata continua dritta in quella di un'altra stanza (muro in comune o eliminato tra le due): lì non finisce
                // niente. I tramezzi si segnano sotto, dai muri pieni; un muro eliminato non lascia il suo spessore nella catena.
                fun continues(t: Double, forward: Boolean) = facades[side].any { g ->
                    g !== f && abs(g.outer - f.outer) < SAME_MARK &&
                        if (forward) g.from <= t + SAME_MARK && g.to > t + SAME_MARK else g.to >= t - SAME_MARK && g.from < t - SAME_MARK
                }
                if (!continues(f.from, forward = false)) marks += f.from
                if (!continues(f.to, forward = true)) marks += f.to
                // Appena dentro il filo interno della facciata: dove cominciano e finiscono i muri (gli spessori) e le stanze.
                val base = along * 0.0 + out * (f.inner - PROBE)
                for (r in merge(solids.flatMap { lineInside(it, base, along) })) {
                    if (r.start > f.from + 0.01 && r.start < f.to - 0.01) marks += r.start
                    if (r.endInclusive > f.from + 0.01 && r.endInclusive < f.to - 0.01) marks += r.endInclusive
                }
            }
            // Balconi e terrazze che sporgono oltre la casa.
            for (r in plan.rooms.filter { it.outdoor && it.points.size >= 3 }) {
                val t = r.points.map { it dot along }
                if (t.min() < total.start - 1.0) marks += t.min()
                if (t.max() > total.endInclusive + 1.0) marks += t.max()
            }
            val sorted = dedupe(marks.sorted())
            // Il bordo del disegno comprende i bovindi, che sporgono dal muro: le quote passano oltre.
            val bays = shapes.flatMap { s -> s.room.openings.filter { it.isBay && it.wallIndex < s.n }.flatMap { Openings.bayOutline(s.room, it) } }
            val edge = (shapes.flatMap { it.ext } + plan.rooms.filter { it.outdoor }.flatMap { it.points } +
                plan.freeWalls.flatMap { listOf(it.start, it.end) } + bays).maxOf { it dot out }
            chains += DimensionChain(along, out, edge, sorted, total)

            // Stanze la cui misura lungo la facciata è un segmento della catena: dentro non si ripete.
            for (f in facades[side]) {
                val s = f.shape
                val i = f.wall
                if (s.logical.arcOf(s.origin[i]) != null) continue
                val prev = (i - 1 + s.n) % s.n
                if (prev in s.room.removedWalls || s.next(i) in s.room.removedWalls) continue
                val p = s.int[i] dot along
                val q = s.int[s.next(i)] dot along
                val lo = min(p, q)
                val hi = max(p, q)
                if (f.from > lo + SAME_MARK || f.to < hi - SAME_MARK) continue
                if (sorted.none { abs(it - lo) < SAME_MARK + 0.1 } || sorted.none { abs(it - hi) < SAME_MARK + 0.1 }) continue
                if (sorted.any { it > lo + SAME_MARK + 0.1 && it < hi - SAME_MARK - 0.1 }) continue
                val lengths = s.room.interiorLengths()
                val u = (s.room.wallEnd(i) - s.room.wallStart(i)).normalized()
                for (j in 0 until s.n) {
                    if (j in s.room.removedWalls || s.logical.arcOf(s.origin[j]) != null) continue
                    val v = s.room.wallEnd(j) - s.room.wallStart(j)
                    if (v.length < 1e-6 || abs(u.x * v.y - u.y * v.x) / v.length > 0.01) continue
                    if (abs(lengths[j] - lengths[i]) < SAME_MARK) covered += s.id to s.origin[j]
                }
            }
        }
        return ExteriorDimensions(chains, covered, exterior)
    }

    /** Direzione della catena per il lato che guarda verso `out`: da sinistra a destra, o dall'alto in basso. */
    private fun alongOf(out: Vec2): Vec2 = out.perp().let { if (it.x < -1e-9 || (abs(it.x) <= 1e-9 && it.y < 0)) it * -1.0 else it }

    /** Angolo (radianti, tra 0 e 90°) dei muri dritti che, sommati, sono più lunghi: le direzioni principali della casa. */
    private fun mainAngle(shapes: List<Shape>): Double {
        val walls = shapes.flatMap { s ->
            (0 until s.n).map { i ->
                val d = s.room.wallEnd(i) - s.room.wallStart(i)
                val a = atan2(d.y, d.x)
                ((a % (PI / 2)) + PI / 2) % (PI / 2) to d.length
            }
        }.filter { it.second > 1.0 }
        if (walls.isEmpty()) return 0.0
        fun diff(a: Double, b: Double): Double { val d = abs(a - b) % (PI / 2); return min(d, PI / 2 - d) }
        val tol = PI / 360
        val best = walls.maxBy { (a, _) -> walls.filter { diff(it.first, a) < tol }.sumOf { it.second } }.first
        // Media pesata dei muri vicini a quell'angolo (attenti al passaggio 0° ↔ 90°).
        var sum = 0.0
        var weight = 0.0
        for ((a, l) in walls) {
            if (diff(a, best) >= tol) continue
            var d = a - best
            if (d > PI / 4) d -= PI / 2
            if (d < -PI / 4) d += PI / 2
            sum += d * l
            weight += l
        }
        return best + sum / weight
    }

    /** Tratti (parametro t) in cui la retta `base + dir * t` sta dentro il poligono semplice `poly` (regola pari-dispari). */
    internal fun lineInside(poly: List<Vec2>, base: Vec2, dir: Vec2): List<ClosedFloatingPointRange<Double>> {
        val n = poly.size
        if (n < 3) return emptyList()
        val ts = mutableListOf<Double>()
        for (k in 0 until n) {
            val a = poly[k]
            val b = poly[(k + 1) % n]
            val da = dir.x * (a.y - base.y) - dir.y * (a.x - base.x)
            val db = dir.x * (b.y - base.y) - dir.y * (b.x - base.x)
            if ((da > 0) == (db > 0)) continue
            val p = a + (b - a) * (da / (da - db))
            ts += (p - base) dot dir
        }
        ts.sort()
        return (0 until ts.size / 2).map { ts[2 * it]..ts[2 * it + 1] }.filter { it.endInclusive > it.start }
    }

    private fun merge(rs: List<ClosedFloatingPointRange<Double>>): List<ClosedFloatingPointRange<Double>> {
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        for (r in rs.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && r.start <= last.endInclusive + 1e-6) out[out.size - 1] = last.start..max(last.endInclusive, r.endInclusive)
            else out += r
        }
        return out
    }

    private fun subtract(range: ClosedFloatingPointRange<Double>, holes: List<ClosedFloatingPointRange<Double>>): List<ClosedFloatingPointRange<Double>> {
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        var cursor = range.start
        for (h in holes) {
            if (h.endInclusive <= cursor || h.start >= range.endInclusive) continue
            if (h.start > cursor) out += cursor..h.start
            cursor = max(cursor, h.endInclusive)
        }
        if (cursor < range.endInclusive) out += cursor..range.endInclusive
        return out
    }

    private fun dedupe(sorted: List<Double>): List<Double> {
        val out = mutableListOf<Double>()
        for (t in sorted) if (out.isEmpty() || t - out.last() >= SAME_MARK) out += t
        return out
    }
}
