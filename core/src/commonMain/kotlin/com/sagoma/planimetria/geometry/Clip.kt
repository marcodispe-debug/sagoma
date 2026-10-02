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
        if (hole.size < 3) return listOf(poly)
        val sign = if (Polygon.signedArea(hole) >= 0) 1.0 else -1.0
        val out = mutableListOf<List<Vec2>>()
        var rest = poly
        for (k in hole.indices) {
            val a = hole[k]
            val b = hole[(k + 1) % hole.size]
            val outside = keep(rest, a, b, -sign)
            if (outside.size >= 3 && Polygon.area(outside) > 1e-6) out += outside
            rest = keep(rest, a, b, sign)
            if (rest.size < 3 || Polygon.area(rest) < 1e-6) return out
        }
        return out
    }

    /** Il poligono convesso `poly` meno tutti i buchi convessi. */
    fun subtractAll(poly: List<Vec2>, holes: List<List<Vec2>>): List<List<Vec2>> =
        holes.fold(listOf(poly)) { pieces, hole -> pieces.flatMap { subtract(it, hole) } }
}
