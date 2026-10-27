package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs

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
     * verticale quando hanno la stessa larghezza). Altrimenti, o con più di [MAX] rettangoli, il suo riquadro.
     */
    fun of(points: List<Vec2>): List<Bounds> {
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

    private const val EPS = 0.5
}
