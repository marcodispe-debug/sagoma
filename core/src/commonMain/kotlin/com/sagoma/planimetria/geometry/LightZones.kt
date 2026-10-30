package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Zone in cui una lampada può illuminare: i rettangoli che compongono la sua stanza. Il renderer tiene la luce dentro queste
 * zone (la luce non passa i muri); con il solo riquadro della stanza, in una stanza a L o a U la luce finiva anche nella
 * stanza accanto, nell'angolo che il riquadro copre.
 */
object LightZones {
    /** Rettangoli al massimo per lampada (quelli che il renderer sa controllare). */
    const val MAX = 4

    /**
     * Stanza con i muri tutti dritti in orizzontale o in verticale: i suoi rettangoli (righe della griglia dei vertici, unite in
     * verticale quando hanno la stessa larghezza). Altrimenti, o con più di [MAX] rettangoli, il suo riquadro. Con meno di 3 punti nessuno.
     */
    fun of(points: List<Vec2>): List<Bounds> {
        // Stanza senza forma (file rovinato): nessun rettangolo, la luce non ci arriva.
        if (points.size < 3) return emptyList()
        val box = Polygon.bounds(points)
        val n = points.size
        if (n <= 4) return listOf(box)
        val straight = (0 until n).all { val a = points[it]; val b = points[(it + 1) % n]; abs(a.x - b.x) < EPS || abs(a.y - b.y) < EPS }
        if (!straight) return listOf(box)
        fun steps(v: List<Double>) = v.sorted().fold(mutableListOf<Double>()) { acc, x -> if (acc.isEmpty() || x - acc.last() > EPS) acc += x; acc }
        val xs = steps(points.map { it.x })
        val ys = steps(points.map { it.y })
        // Rettangoli aperti: colonne da i0 a i1 (escluso), righe da j0 a j1 (escluso).
        val rects = mutableListOf<IntArray>()
        var open = listOf<IntArray>()
        for (j in 0 until ys.size - 1) {
            val cy = (ys[j] + ys[j + 1]) / 2
            val next = mutableListOf<IntArray>()
            var i = 0
            while (i < xs.size - 1) {
                if (!Polygon.contains(points, Vec2((xs[i] + xs[i + 1]) / 2, cy))) { i++; continue }
                val i0 = i
                while (i < xs.size - 1 && Polygon.contains(points, Vec2((xs[i] + xs[i + 1]) / 2, cy))) i++
                val above = open.firstOrNull { it[0] == i0 && it[1] == i }
                if (above != null) { above[3] = j + 1; next += above }
                else intArrayOf(i0, i, j, j + 1).also { rects += it; next += it }
            }
            open = next
        }
        if (rects.isEmpty() || rects.size > MAX) return listOf(box)
        return rects.map { Bounds(xs[it[0]], ys[it[2]], xs[it[1]], ys[it[3]]) }
    }

    /** Tratto minimo (cm) di muro tolto in comune perché due stanze siano un ambiente solo (come i varchi di [LightSpill]). */
    private const val OPEN_MIN_CM = 20.0

    /**
     * Gli ambienti aperti del piano: le stanze al chiuso unite dove tutte e due hanno tolto il muro in comune (soggiorno, cucina e
     * ingresso senza muri in mezzo). Ogni stanza (con almeno tre angoli) sta in un gruppo solo; quelle chiuse da sole.
     */
    fun spaces(plan: FloorPlan): List<List<Room>> {
        val indoor = plan.rooms.filter { !it.outdoor && it.points.size >= 3 }
        val group = IntArray(indoor.size) { it }
        fun root(i: Int): Int = if (group[i] == i) i else root(group[i]).also { group[i] = it }
        for (i in indoor.indices) for (j in i + 1 until indoor.size) if (open(indoor[i], indoor[j])) group[root(i)] = root(j)
        return indoor.indices.groupBy { root(it) }.values.map { g -> g.map { indoor[it] } }
    }

    private fun open(a: Room, b: Room): Boolean = a.removedWalls.any { i ->
        i < a.wallCount && b.removedWalls.any { j ->
            j < b.wallCount && Dimensions.overlap(a.wallStart(i), a.wallEnd(i), b.wallStart(j), b.wallEnd(j)) >= OPEN_MIN_CM
        }
    }

    /**
     * I rettangoli dell'ambiente aperto fatto delle stanze `rooms` ([spaces]): quelli di ogni stanza, uniti dove insieme fanno un
     * rettangolo. Se restano più di [MAX], si uniscono a due a due quelli che aggiungono meno pavimento in più, purché il riquadro
     * non entri nelle stanze `others` (la luce resterebbe di là dal muro). null se non bastano [MAX] rettangoli.
     */
    fun merged(rooms: List<Room>, others: List<Room> = emptyList()): List<Bounds>? {
        val rects = rooms.flatMap { of(it.points) }.toMutableList()
        fun area(b: Bounds) = b.width * b.height
        fun common(a: Bounds, b: Bounds) = max(0.0, min(a.maxX, b.maxX) - max(a.minX, b.minX)) * max(0.0, min(a.maxY, b.maxY) - max(a.minY, b.minY))
        // Prima le coppie che insieme sono un rettangolo (stessa fascia, una accanto all'altra, o una dentro l'altra).
        fun exact(a: Bounds, b: Bounds): Boolean {
            val rows = abs(a.minY - b.minY) < JOIN_CM && abs(a.maxY - b.maxY) < JOIN_CM && a.minX <= b.maxX + JOIN_CM && b.minX <= a.maxX + JOIN_CM
            val cols = abs(a.minX - b.minX) < JOIN_CM && abs(a.maxX - b.maxX) < JOIN_CM && a.minY <= b.maxY + JOIN_CM && b.minY <= a.maxY + JOIN_CM
            return rows || cols || common(a, b) >= min(area(a), area(b)) - 1.0
        }
        fun unite(pick: () -> Pair<Int, Int>?): Boolean {
            val (i, j) = pick() ?: return false
            rects[i] = rects[i].union(rects[j])
            rects.removeAt(j)
            return true
        }
        fun pairs() = rects.indices.flatMap { i -> (i + 1 until rects.size).map { i to it } }
        while (unite { pairs().firstOrNull { (i, j) -> exact(rects[i], rects[j]) } }) Unit
        // Poi, se serve, le coppie con meno pavimento in più che non entrano nelle altre stanze (un poco dentro i loro muri).
        val blocked = others.filter { !it.outdoor }.flatMap { of(it.points) }.map { Bounds(it.minX + 5, it.minY + 5, it.maxX - 5, it.maxY - 5) }
        while (rects.size > MAX) {
            val ok = unite {
                pairs().filter { (i, j) -> val u = rects[i].union(rects[j]); blocked.none { common(u, it) > 1.0 } }
                    .minByOrNull { (i, j) -> area(rects[i].union(rects[j])) - area(rects[i]) - area(rects[j]) + common(rects[i], rects[j]) }
            }
            if (!ok) return null
        }
        return rects
    }

    /**
     * cm² di pavimento, soffitto e pareti delle stanze `rooms`, su cui si sparge la luce che rimbalza: una stanza con tutti i suoi
     * muri, un ambiente aperto ([spaces]) senza i muri tolti in mezzo.
     */
    fun surface(rooms: List<Room>): Double = rooms.sumOf { r ->
        2 * Polygon.area(r.points) + (0 until r.wallCount).filter { rooms.size == 1 || !r.isRemoved(it) }.sumOf { r.wallLength(it) } * r.ceilingHeight
    }

    /** Due rettangoli si uniscono esatti se i lati in comune combaciano entro questo (cm). */
    private const val JOIN_CM = 2.0

    private const val EPS = 0.5
}
