package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Stanza da rilievo: dai lati misurati sul posto (filo interno, in ordine, a partire dall'angolo A) e dalle
 * diagonali dall'angolo A agli angoli C, D, … si ricava la forma vera, anche fuori squadra.
 *
 * Il poligono si costruisce a triangoli attorno ad A: A-B-C, A-C-D, … Per una stanza di n lati servono
 * n − 3 diagonali (nessuna per un triangolo, una per un quadrilatero). Va bene per le stanze che da A
 * "si vedono tutte" (convesse, o con le rientranze lontane da A).
 */
object Survey {
    /** Diagonali necessarie per `sides` lati. */
    fun diagonalsNeeded(sides: Int): Int = (sides - 3).coerceAtLeast(0)

    /**
     * Angoli della stanza (filo interno, cm) in ordine orario sullo schermo, A in (0, 0) e B sull'asse x;
     * null se le misure non sono compatibili (un triangolo non si chiude).
     */
    fun solve(sides: List<Double>, diagonals: List<Double>): List<Vec2>? {
        val n = sides.size
        if (n < 3 || diagonals.size != diagonalsNeeded(n) || sides.any { it <= 0 } || diagonals.any { it <= 0 }) return null
        // Distanza di ogni angolo da A: B è il primo lato, poi le diagonali, l'ultimo angolo è l'ultimo lato.
        val fromA = listOf(0.0, sides[0]) + diagonals + sides[n - 1]
        val pts = mutableListOf(Vec2(0.0, 0.0), Vec2(sides[0], 0.0))
        for (k in 1 until n - 1) {
            val pk = pts[k]
            val r0 = fromA[k + 1] // |A P(k+1)|
            val r1 = sides[k] // |P(k) P(k+1)|
            val next = circleIntersection(Vec2(0.0, 0.0), r0, pk, r1) ?: return null
            pts += next
        }
        return pts
    }

    /**
     * Intersezione dei cerchi (c0, r0) e (c1, r1) dalla parte "oraria" (a destra andando da c0 a c1 su uno
     * schermo con y verso il basso), così la stanza gira sempre nello stesso verso.
     */
    private fun circleIntersection(c0: Vec2, r0: Double, c1: Vec2, r1: Double): Vec2? {
        val d = c0.distanceTo(c1)
        if (d < 1e-9) return null
        // Tolleranza di mezzo centimetro sulle misure (un triangolo quasi piatto resta valido).
        if (d > r0 + r1 + 0.5 || d < abs(r0 - r1) - 0.5) return null
        val a = (r0 * r0 - r1 * r1 + d * d) / (2 * d)
        val h = sqrt((r0 * r0 - a * a).coerceAtLeast(0.0))
        val u = (c1 - c0) / d
        val base = c0 + u * a
        // perp() ruota di 90° verso y positivi: su schermo è "sotto" la direzione di marcia.
        return base + u.perp() * h
    }
}
