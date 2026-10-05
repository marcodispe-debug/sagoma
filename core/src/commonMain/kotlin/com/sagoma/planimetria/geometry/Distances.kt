package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2

/** Oggetto scelto per misurare una distanza. */
sealed interface MeasureTarget {
    data class Wall(val roomId: Long, val index: Int) : MeasureTarget
    data class Opening(val roomId: Long, val openingId: Long) : MeasureTarget
    data class Fixture(val roomId: Long, val fixtureId: Long) : MeasureTarget
    data class Column(val columnId: Long) : MeasureTarget
    data class Beam(val beamId: Long) : MeasureTarget
    data class Stair(val stairId: Long) : MeasureTarget
    data class FreeWall(val wallId: Long) : MeasureTarget
    data class Furniture(val furnitureId: Long) : MeasureTarget
}

/**
 * Distanze tra oggetti della pianta: ogni oggetto diventa la sua forma in pianta (il muro con il suo
 * spessore, il calorifero con la sua sporgenza, la colonna, la scala…) e la distanza è quella tra i due
 * bordi più vicini, con i due punti da cui disegnare la quota.
 */
object Distances {

    /** Forma di un oggetto: poligoni (con il loro interno) e punti isolati (prese, faretti). */
    class Shape(val polygons: List<List<Vec2>>, val points: List<Vec2> = emptyList(), val segments: List<Pair<Vec2, Vec2>> = emptyList()) {
        val edges: List<Pair<Vec2, Vec2>>
            get() = polygons.flatMap { p -> p.indices.map { p[it] to p[(it + 1) % p.size] } } + segments + points.map { it to it }
    }

    /** Distanza tra due oggetti e i punti più vicini (`a` sul primo, `b` sul secondo). */
    data class Result(val distance: Double, val a: Vec2, val b: Vec2)

    private fun rect(a: Vec2, b: Vec2, n: Vec2, from: Double, to: Double) =
        listOf(a + n * from, b + n * from, b + n * to, a + n * to)

    fun shapeOf(plan: FloorPlan, t: MeasureTarget, levelHeight: Double): Shape? {
        val half = Room.WALL_THICKNESS / 2
        return when (t) {
            is MeasureTarget.Wall -> {
                val r = plan.room(t.roomId)?.takeIf { t.index < it.wallCount } ?: return null
                val s = r.wallStart(t.index)
                val e = r.wallEnd(t.index)
                val n = (e - s).normalized().perp()
                val h = r.thicknessOf(t.index) / 2
                Shape(listOf(rect(s, e, n, -h, h)))
            }
            is MeasureTarget.Opening -> {
                val r = plan.room(t.roomId) ?: return null
                val o = r.opening(t.openingId)?.takeIf { it.wallIndex < r.wallCount } ?: return null
                val (a, b) = Openings.span(r, o)
                val n = (b - a).normalized().perp()
                Shape(listOf(rect(a, b, n, -half, half)))
            }
            is MeasureTarget.Fixture -> {
                val r = plan.room(t.roomId) ?: return null
                val f = r.fixture(t.fixtureId) ?: return null
                when {
                    f.kind.mount == Mount.Wall && f.wallIndex < r.wallCount -> {
                        val (p, n) = Fixtures.wallAnchor(r, f)
                        if (f.kind == FixtureKind.Radiator) {
                            val along = n.perp()
                            Shape(listOf(rect(p - along * (f.length / 2), p + along * (f.length / 2), n, 0.0, Fixtures.RADIATOR_DEPTH)))
                        } else if (f.kind == FixtureKind.WallLedStrip) {
                            val along = n.perp()
                            Shape(emptyList(), segments = listOf((p - along * (f.length / 2)) to (p + along * (f.length / 2))))
                        } else Shape(emptyList(), points = listOf(p))
                    }
                    f.kind.linear -> Fixtures.linearEnds(f).let { (a, b) -> Shape(emptyList(), segments = listOf(a to b)) }
                    else -> Shape(emptyList(), points = listOf(f.point))
                }
            }
            is MeasureTarget.Column -> plan.column(t.columnId)?.let { Shape(listOf(Structure.outline(it))) }
            is MeasureTarget.Beam -> plan.beam(t.beamId)?.let { Shape(listOf(Structure.outline(it))) }
            is MeasureTarget.Stair -> plan.stair(t.stairId)?.let { Shape(Stairs.layout(it, levelHeight).pieces) }
            is MeasureTarget.FreeWall -> plan.freeWall(t.wallId)?.let { Shape(listOf(Structure.outline(it))) }
            is MeasureTarget.Furniture -> plan.furniture(t.furnitureId)?.let { Shape(listOf(Furnishings.outline(it))) }
        }
    }

    /** Punti più vicini tra i segmenti p1-q1 e p2-q2 (anche degeneri, cioè punti). */
    fun closest(p1: Vec2, q1: Vec2, p2: Vec2, q2: Vec2): Pair<Vec2, Vec2> {
        val d1 = q1 - p1
        val d2 = q2 - p2
        val r = p1 - p2
        val a = d1 dot d1
        val e = d2 dot d2
        val f = d2 dot r
        var s: Double
        var t: Double
        if (a < 1e-12 && e < 1e-12) return p1 to p2
        if (a < 1e-12) {
            s = 0.0
            t = (f / e).coerceIn(0.0, 1.0)
        } else {
            val c = d1 dot r
            if (e < 1e-12) {
                t = 0.0
                s = (-c / a).coerceIn(0.0, 1.0)
            } else {
                val b = d1 dot d2
                val den = a * e - b * b
                s = if (den > 1e-12) ((b * f - c * e) / den).coerceIn(0.0, 1.0) else 0.0
                t = (b * s + f) / e
                if (t < 0) { t = 0.0; s = (-c / a).coerceIn(0.0, 1.0) }
                else if (t > 1) { t = 1.0; s = ((b - c) / a).coerceIn(0.0, 1.0) }
            }
        }
        return (p1 + d1 * s) to (p2 + d2 * t)
    }

    /** Distanza tra due forme: zero se una entra nell'altra, altrimenti tra i bordi più vicini. */
    fun between(s1: Shape, s2: Shape): Result? {
        val e1 = s1.edges
        val e2 = s2.edges
        if (e1.isEmpty() || e2.isEmpty()) return null
        // Un vertice di una forma dentro l'altra: si toccano.
        for (poly in s1.polygons) for ((v, _) in e2) if (Polygon.contains(poly, v)) return Result(0.0, v, v)
        for (poly in s2.polygons) for ((v, _) in e1) if (Polygon.contains(poly, v)) return Result(0.0, v, v)
        var best: Result? = null
        for ((a1, b1) in e1) for ((a2, b2) in e2) {
            val (p, q) = closest(a1, b1, a2, b2)
            val d = p.distanceTo(q)
            if (best == null || d < best.distance) best = Result(d, p, q)
        }
        return best
    }

    fun between(plan: FloorPlan, a: MeasureTarget, b: MeasureTarget, levelHeight: Double): Result? {
        val s1 = shapeOf(plan, a, levelHeight) ?: return null
        val s2 = shapeOf(plan, b, levelHeight) ?: return null
        return between(s1, s2)
    }
}
