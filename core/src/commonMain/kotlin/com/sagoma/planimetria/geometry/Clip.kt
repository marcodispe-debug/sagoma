package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Vec2

/** Ritagli di poligoni convessi: servono a lasciare il vuoto della scala nel pavimento del piano di sopra. */
object Clip {

    private fun cross(a: Vec2, b: Vec2, p: Vec2) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

    /** Parte del poligono convesso `poly` in cui `cross(a, b, p) * side >= 0` (Sutherland–Hodgman). */
    private fun keep(poly: List<Vec2>, a: Vec2, b: Vec2, side: Double): List<Vec2> {
        if (poly.isEmpty()) return poly
        val out = mutableListOf<Vec2>()
        for (k in poly.indices) {
            val p = poly[k]
            val q = poly[(k + 1) % poly.size]
            val fp = cross(a, b, p) * side
            val fq = cross(a, b, q) * side
            if (fp >= 0) out += p
            if ((fp >= 0) != (fq >= 0)) {
                val t = fp / (fp - fq)
                out += p + (q - p) * t
            }
        }
        return out
    }

    /**
     * Il poligono convesso `poly` meno il poligono convesso `hole`: una lista di pezzi convessi. Per ogni
     * lato del buco si tiene la parte che sta fuori e si prosegue con quella che sta dentro.
     */
    fun subtract(poly: List<Vec2>, hole: List<Vec2>): List<List<Vec2>> {
        if (hole.size < 3 || !overlaps(poly, hole)) return listOf(poly)
        val sign = if (Polygon.signedArea(hole) >= 0) 1.0 else -1.0
        val out = mutableListOf<List<Vec2>>()
        var rest = poly
        for (k in hole.indices) {
            val a = hole[k]
            val b = hole[(k + 1) % hole.size]
            val outside = keep(rest, a, b, -sign)
            if (outside.size >= 3 && Polygon.area(outside) > MIN_PIECE) out += outside
            rest = keep(rest, a, b, sign)
            if (rest.size < 3 || Polygon.area(rest) < 1e-6) return out
        }
        return out
    }

    /**
     * Il poligono convesso `poly` meno tutti i buchi convessi. Si guardano solo i buchi che lo toccano: con tanti buchi
     * (es. tutti i triangoli delle stanze del piano di sotto) provarli tutti su ogni pezzo bloccava il 3D per minuti.
     */
    fun subtractAll(poly: List<Vec2>, holes: List<List<Vec2>>): List<List<Vec2>> {
        val near = holes.filter { it.size >= 3 && overlaps(poly, it) }.map(::Hole)
        if (near.isEmpty()) return listOf(poly)
        // Un pezzo tutto dentro un buco che deve ancora venire sparisce comunque: tolto subito, non si fa a pezzi.
        // Basta guardare i pezzi nuovi: uno rimasto uguale era già stato provato con gli stessi buchi.
        fun alive(piece: List<Vec2>, from: Int) = (from until near.size).none { near[it].contains(piece) }
        var pieces = if (alive(poly, 0)) listOf(poly) else return emptyList()
        for ((k, hole) in near.withIndex()) {
            pieces = pieces.flatMap { piece ->
                val cut = subtract(piece, hole.points)
                if (cut.size == 1 && cut[0] === piece) cut else cut.filter { alive(it, k + 1) }
            }
            if (pieces.isEmpty()) break
        }
        return pieces
    }

    /** Parte comune di due poligoni convessi, o null se non si toccano (o la parte è una scheggia). */
    fun intersect(a: List<Vec2>, b: List<Vec2>): List<Vec2>? {
        if (a.size < 3 || b.size < 3 || !overlaps(a, b)) return null
        val sign = if (Polygon.signedArea(b) >= 0) 1.0 else -1.0
        var rest = a
        for (k in b.indices) {
            rest = keep(rest, b[k], b[(k + 1) % b.size], sign)
            if (rest.size < 3) return null
        }
        return rest.takeIf { Polygon.area(it) > MIN_PIECE }
    }

    /** Buco convesso con il suo verso e il rettangolo che lo contiene, calcolati una volta sola. */
    private class Hole(val points: List<Vec2>) {
        private val sign = if (Polygon.signedArea(points) >= 0) 1.0 else -1.0
        private val minX = points.minOf { it.x }
        private val maxX = points.maxOf { it.x }
        private val minY = points.minOf { it.y }
        private val maxY = points.maxOf { it.y }

        /** Il poligono `piece` sta tutto dentro il buco (bordi compresi). */
        fun contains(piece: List<Vec2>): Boolean {
            if (piece.any { it.x < minX - 1e-9 || it.x > maxX + 1e-9 || it.y < minY - 1e-9 || it.y > maxY + 1e-9 }) return false
            for (k in points.indices) {
                val a = points[k]
                val b = points[(k + 1) % points.size]
                if (piece.any { cross(a, b, it) * sign < -1e-9 }) return false
            }
            return true
        }
    }

    /** Pezzi più piccoli di così (cm²) non si tengono: sono schegge invisibili che moltiplicherebbero i tagli. */
    private const val MIN_PIECE = 0.01

    /** I rettangoli che contengono i due poligoni si toccano (se no il buco non taglia niente). */
    private fun overlaps(a: List<Vec2>, b: List<Vec2>): Boolean =
        a.maxOf { it.x } > b.minOf { it.x } && b.maxOf { it.x } > a.minOf { it.x } &&
            a.maxOf { it.y } > b.minOf { it.y } && b.maxOf { it.y } > a.minOf { it.y }
}
