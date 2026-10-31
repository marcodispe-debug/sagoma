package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs

/** Da che parte sta il muro, come la si vede sullo schermo (per le aperture: guardando il muro da dentro la stanza). */
enum class WallSide { Left, Right, Up, Down }

/**
 * Distanza di un oggetto da un muro: da `from` (bordo dell'oggetto) a `to` (faccia interna del muro), lunga `distance` cm.
 * `toward` è la direzione verso il muro: spostando l'oggetto di `toward * k` la distanza cala di `k`.
 */
data class WallDistance(val side: WallSide, val from: Vec2, val to: Vec2, val distance: Double, val toward: Vec2)

/**
 * Distanze di un oggetto dai muri della sua stanza, sul filo interno. Porte, finestre e balconi: i due angoli del loro muro.
 * Colonne, arredi, scale e impianti: il muro più vicino a sinistra, a destra, sopra e sotto (dentro la stanza in cui stanno,
 * o fuori casa dalla faccia esterna dei muri).
 */
object WallDistances {
    fun of(plan: FloorPlan, t: MeasureTarget, levelHeight: Double): List<WallDistance> = when (t) {
        is MeasureTarget.Opening -> openingDistances(plan, t)
        is MeasureTarget.Column, is MeasureTarget.Furniture, is MeasureTarget.Stair, is MeasureTarget.Fixture -> objectDistances(plan, t, levelHeight)
        else -> emptyList()
    }

    private fun openingDistances(plan: FloorPlan, t: MeasureTarget.Opening): List<WallDistance> {
        val room = plan.room(t.roomId) ?: return emptyList()
        val o = room.opening(t.openingId) ?: return emptyList()
        val gaps = Openings.sideGaps(room, o)
        if (gaps.size != 2) return emptyList()
        // Guardando il muro da dentro si guarda verso `−inward`; la sinistra (schermo con y in giù) è (f.y, −f.x).
        val facing = gaps[0].inward * -1.0
        val left = Vec2(facing.y, -facing.x)
        // Dal primo angolo al secondo, lungo il muro: verso il primo angolo si va con −u, verso il secondo con +u.
        val u = (gaps[1].from - gaps[0].from).normalized()
        return gaps.mapIndexed { k, g ->
            val dir = if (k == 0) u * -1.0 else u
            WallDistance(if ((dir dot left) > 0) WallSide.Left else WallSide.Right, g.to, g.from, g.length, dir)
        }
    }

    private val directions = listOf(
        WallSide.Left to Vec2(-1.0, 0.0), WallSide.Right to Vec2(1.0, 0.0),
        WallSide.Up to Vec2(0.0, -1.0), WallSide.Down to Vec2(0.0, 1.0),
    )

    private fun objectDistances(plan: FloorPlan, t: MeasureTarget, levelHeight: Double): List<WallDistance> {
        val shape = Distances.shapeOf(plan, t, levelHeight) ?: return emptyList()
        val pts = shape.polygons.flatten() + shape.segments.flatMap { listOf(it.first, it.second) } + shape.points
        if (pts.isEmpty()) return emptyList()
        val b = Polygon.bounds(pts)
        val c = b.center
        // Dentro una stanza: il filo interno dei suoi muri; fuori casa: il filo esterno di tutte le stanze. Muri singoli sempre.
        val room = roomAt(plan, c)
        // Un muro singolo su cui l'oggetto è appoggiato non conta: si misura dai muri che ha intorno.
        val outlines = (room?.let { listOf(it.interiorOutline()) } ?: plan.rooms.filter { it.wallCount >= 3 }.map { it.exteriorOutline() }) +
            plan.freeWalls.map { Structure.outline(it) }.filterNot { overlaps(it, b) }
        return directions.mapNotNull { (side, d) ->
            // Bordo dell'oggetto verso quel muro: il suo punto più sporgente da quella parte.
            val reach = pts.maxOf { (it - c) dot d }
            // Solo i muri oltre il bordo: un oggetto appoggiato sopra un muro non misura da quello che ha sotto, così
            // scrivendo una distanza la si ottiene esatta (il muro da cui si misura non cambia spostandolo).
            val hit = outlines.mapNotNull { rayHit(it, c, d, reach - 1e-6) }.minOrNull() ?: return@mapNotNull null
            val from = c + d * reach
            WallDistance(side, from, c + d * hit, (hit - reach).coerceAtLeast(0.0), d)
        }
    }

    /** Il contorno tocca il rettangolo (un vertice dentro l'altro o lati che si incrociano). */
    private fun overlaps(outline: List<Vec2>, b: Bounds): Boolean {
        val box = listOf(Vec2(b.minX, b.minY), Vec2(b.maxX, b.minY), Vec2(b.maxX, b.maxY), Vec2(b.minX, b.maxY))
        if (outline.any { Polygon.contains(box, it) } || box.any { Polygon.contains(outline, it) }) return true
        return outline.indices.any { i -> box.indices.any { j ->
            segmentsIntersect(outline[i], outline[(i + 1) % outline.size], box[j], box[(j + 1) % 4])
        } }
    }

    private fun segmentsIntersect(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Boolean {
        fun o(p: Vec2, q: Vec2, r: Vec2) = (q - p).cross(r - p)
        return (o(c, d, a) > 0) != (o(c, d, b) > 0) && (o(a, b, c) > 0) != (o(a, b, d) > 0)
    }

    /** Stanza che contiene il punto, la più piccola se più di una. */
    private fun roomAt(plan: FloorPlan, p: Vec2): Room? =
        plan.rooms.filter { it.wallCount >= 3 && Polygon.contains(it.interiorOutline(), p) }.minByOrNull { abs(Polygon.signedArea(it.points)) }

    /** Distanza dal punto `c`, lungo `d` (unitario), al primo lato del contorno oltre `minT`; null se non ne incontra. */
    private fun rayHit(outline: List<Vec2>, c: Vec2, d: Vec2, minT: Double): Double? {
        var best: Double? = null
        for (i in outline.indices) {
            val a = outline[i]
            val e = outline[(i + 1) % outline.size] - a
            val den = d.x * e.y - d.y * e.x
            if (abs(den) < 1e-12) continue
            val w = a - c
            val t = (w.x * e.y - w.y * e.x) / den
            val s = (w.x * d.y - w.y * d.x) / den
            if (t > maxOf(minT, 1e-6) && s in -1e-9..1.0 + 1e-9 && (best == null || t < best)) best = t
        }
        return best
    }
}
