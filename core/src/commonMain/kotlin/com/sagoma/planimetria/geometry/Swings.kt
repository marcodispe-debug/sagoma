package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Ante a battente che si aprono: lo spazio che spazzano (un quarto di cerchio attorno al cardine, dalla faccia del muro verso cui
 * si aprono, come le disegna la pianta) e cosa trovano sulla loro strada: altre ante, mobili, caloriferi, muri e pilastri.
 */
object Swings {
    /** Spicchio spazzato da un'anta della `opening` di `room`: quarto di cerchio (convesso), alto da `bottom` a `top` (cm da terra). */
    class Sweep(val room: Room, val opening: Opening, val points: List<Vec2>, val bottom: Double, val top: Double) {
        val box: Bounds = Polygon.bounds(points)
    }

    enum class ObstacleKind { Wall, Furniture, Radiator }

    /** Qualcosa sulla strada delle ante: `points` in pianta, alto da `bottom` a `top`; `id` del mobile o della stanza del calorifero. */
    class Obstacle(val kind: ObstacleKind, val id: Long, val points: List<Vec2>, val bottom: Double, val top: Double) {
        val box: Bounds = Polygon.bounds(points)
    }

    /** Superficie in comune (cm², 30 = 3 × 10 cm) da cui in su due cose si urtano: sfiorarsi non conta. */
    const val MIN_OVERLAP_CM2 = 30.0

    /** Spessore medio (cm) della parte in comune da cui in su due cose si urtano. */
    private const val MIN_DEPTH_CM = 1.5

    /** Pezzi dritti del quarto di cerchio. */
    private const val ARC_STEPS = 12

    /** L'anta parte così staccata dalla faccia del muro (cm): toccare il muro in cui sta non conta. */
    private const val CLEARANCE = 1.0

    /** Profondità (cm) di un calorifero dalla faccia del muro. */
    const val RADIATOR_DEPTH = 10.0

    /** Mobili più bassi di così (cm): tappeti, l'anta ci passa sopra. */
    private const val FLAT_CM = 3.0

    /** Spicchi delle ante a battente dell'apertura (vuoto per scorrevoli, fisse, vasistas, a libro e a bilico). */
    fun sweeps(room: Room, o: Opening): List<Sweep> {
        val i = o.wallIndex
        if (i !in 0 until room.wallCount || room.isRemoved(i) || !o.swings || o.folds || o.pivots) return emptyList()
        if (!o.width.isFinite() || !o.position.isFinite() || o.width <= 1.0 || o.height <= 0.0) return emptyList()
        val (a, b) = Openings.span(room, o)
        if (!a.x.isFinite() || !a.y.isFinite() || !b.x.isFinite() || !b.y.isFinite() || a.distanceTo(b) < 1.0) return emptyList()
        // Come le disegna la pianta: dalla faccia del muro verso cui si aprono, o dall'infisso se non sta a metà muro.
        val n = Openings.inwardNormal(room, i)
        val swing = if (o.opensInward) n else n * -1.0
        val frame = Openings.frameOffset(room, o)
        val base = (if (frame != 0.0) n * frame else swing * (room.thicknessOf(i) / 2)) + swing * CLEARANCE
        val leaves: List<Pair<Vec2, Vec2>> = when {
            o.leaves > 2 -> (0 until o.leaves).map { k ->
                val p0 = a + (b - a) * (k.toDouble() / o.leaves)
                val p1 = a + (b - a) * ((k + 1).toDouble() / o.leaves)
                if (k * 2 < o.leaves) p0 to p1 else p1 to p0
            }
            o.kind.leaves == 2 -> {
                val mid = a + (b - a) * o.leafSplit
                listOf(a to mid, b to mid)
            }
            o.kind.leaves == 1 -> {
                val lr = o.leafRange
                val la = a + (b - a) * lr.start
                val lb = a + (b - a) * lr.endInclusive
                listOf(if (o.hingeLeft) la to lb else lb to la)
            }
            else -> emptyList()
        }
        return leaves.mapNotNull { (hinge, free) ->
            val r = hinge.distanceTo(free) - CLEARANCE
            if (r < 5.0) return@mapNotNull null
            val e1 = (free - hinge) / hinge.distanceTo(free)
            val h = hinge + base
            val pts = listOf(h) + (0..ARC_STEPS).map { k ->
                val t = PI / 2 * k / ARC_STEPS
                h + e1 * (cos(t) * r) + swing * (sin(t) * r)
            }
            Sweep(room, o, pts, o.bottom, o.bottom + o.height)
        }
    }

    /** Tutte le ante a battente del piano: stanze e muri singoli. */
    fun sweeps(plan: FloorPlan): List<Sweep> = (plan.rooms + plan.freeWalls.map { it.host }).flatMap { r -> r.openings.flatMap { sweeps(r, it) } }

    /** Muri (delle stanze e singoli) e pilastri del piano. `plan` com'è disegnata: i muri curvi si fanno a pezzi qui. */
    fun walls(plan: FloorPlan): List<Obstacle> {
        val out = mutableListOf<Obstacle>()
        val p = plan.expandCurves().plan
        for (q in p.rooms) for (j in 0 until q.wallCount) {
            if (q.isRemoved(j)) continue
            val s = q.wallStart(j)
            val e = q.wallEnd(j)
            if (s.distanceTo(e) < 1e-6) continue
            val inward = q.outwardOf(j) * -1.0
            val inner = inward * (q.innerThicknesses[j] / 2)
            val outer = inward * (q.outerThicknesses[j] / 2)
            out += Obstacle(ObstacleKind.Wall, q.id, listOf(s - outer, e - outer, e + inner, s + inner), 0.0, q.wallHeight(j))
        }
        for (w in p.freeWalls) if (w.length >= 1.0) {
            for (piece in w.pieces()) out += Obstacle(ObstacleKind.Wall, -w.id, Structure.outline(piece), 0.0, w.height ?: Double.MAX_VALUE)
        }
        for (c in p.columns) out += Obstacle(ObstacleKind.Wall, c.id, Structure.outline(c), 0.0, c.height ?: Double.MAX_VALUE)
        return out
    }

    /** Mobili (non i tappeti) e caloriferi del piano. */
    fun things(plan: FloorPlan): List<Obstacle> {
        val out = mutableListOf<Obstacle>()
        for (f in plan.furniture) {
            if (f.height <= FLAT_CM || f.width <= 0.0 || f.depth <= 0.0 || !f.center.x.isFinite() || !f.center.y.isFinite()) continue
            out += Obstacle(ObstacleKind.Furniture, f.id, Furnishings.outline(f), f.elevation, f.elevation + f.height)
        }
        for (q in plan.rooms) for (fx in q.fixtures) {
            val j = fx.wallIndex
            if (fx.kind != FixtureKind.Radiator || j !in 0 until q.wallCount || q.isRemoved(j) || q.arcOf(j) != null || fx.length <= 0.0) continue
            val s = q.wallStart(j)
            val len = s.distanceTo(q.wallEnd(j))
            if (len < 1e-6) continue
            val u = (q.wallEnd(j) - s) / len
            val inward = q.outwardOf(j) * -1.0
            val face = q.innerThicknesses[j] / 2
            val c = Openings.clampPosition(len, min(fx.length, len), fx.position)
            val p0 = s + u * (c - fx.length / 2) + inward * face
            val p1 = s + u * (c + fx.length / 2) + inward * face
            out += Obstacle(ObstacleKind.Radiator, q.id, listOf(p0, p1, p1 + inward * RADIATOR_DEPTH, p0 + inward * RADIATOR_DEPTH), fx.elevation, fx.elevation + fx.height)
        }
        return out
    }

    /** Superficie in comune (cm²) tra lo spicchio e l'ostacolo, se sono anche alla stessa altezza; 0 altrimenti. */
    fun overlap(s: Sweep, points: List<Vec2>, box: Bounds, bottom: Double, top: Double): Double {
        if (min(s.top, top) - maxOf(s.bottom, bottom) < 2.0) return 0.0
        if (box.minX >= s.box.maxX || s.box.minX >= box.maxX || box.minY >= s.box.maxY || s.box.minY >= box.maxY) return 0.0
        val piece = Polygon.clipConvex(points, s.points)
        if (piece.size < 3) return 0.0
        // Una striscia più sottile di così è solo sfiorata (o un errore di disegno di un centimetro): non conta.
        val area = Polygon.area(piece)
        val pb = Polygon.bounds(piece)
        return if (area / maxOf(pb.maxX - pb.minX, pb.maxY - pb.minY, 1e-9) < MIN_DEPTH_CM) 0.0 else area
    }

    fun overlap(s: Sweep, o: Obstacle): Double = overlap(s, o.points, o.box, o.bottom, o.top)

    fun overlap(a: Sweep, b: Sweep): Double = overlap(a, b.points, b.box, b.bottom, b.top)

    /**
     * Il muro `o` è quello in cui sta l'apertura dello spicchio (o il muro dell'altra stanza lì accanto): quasi parallelo al varco e
     * vicino al suo centro. Non conta: l'anta ci sta dentro da chiusa.
     */
    fun ownWall(s: Sweep, o: Obstacle): Boolean {
        if (o.kind != ObstacleKind.Wall) return false
        val (a, b) = Openings.span(s.room, s.opening)
        val mid = (a + b) / 2.0
        val u = (b - a).normalized()
        val pts = o.points
        // Lato lungo del rettangolo del muro (o del pezzo di muro singolo): la sua direzione e la distanza dal centro del varco.
        val edges = pts.indices.map { k -> pts[k] to pts[(k + 1) % pts.size] }.filter { (p, q) -> p.distanceTo(q) > 1e-6 }
        val long = edges.maxByOrNull { (p, q) -> p.distanceTo(q) } ?: return false
        val v = (long.second - long.first).normalized()
        if (abs(u.x * v.y - u.y * v.x) > 0.42) return false
        val reach = s.room.thicknessOf(s.opening.wallIndex) / 2 + 2.0
        return Polygon.contains(pts, mid) || edges.any { (p, q) -> Polygon.distanceToSegment(mid, p, q) <= reach }
    }
}
