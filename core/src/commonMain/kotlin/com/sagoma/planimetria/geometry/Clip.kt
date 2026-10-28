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
        if (hole.size < 3 || !overlaps(poly, hole) || apart(poly, hole) || apart(hole, poly)) return listOf(poly)
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
        val area = Box(poly)
        val near = holes.mapNotNull { if (it.size < 3) null else Box(it).takeIf { b -> b.overlaps(area) }?.let { b -> Hole(it, b) } }
        if (near.isEmpty()) return listOf(poly)
        // Ogni pezzo ricorda il primo buco che non ha ancora provato: si taglia col primo che lo tocca davvero e i pezzi
        // nuovi ripartono dal buco dopo. Così ogni pezzo guarda i buchi una volta sola, invece di riprovare tutti i pezzi
        // rimasti a ogni buco (con tante stanze sovrapposte erano migliaia di pezzi per migliaia di buchi: minuti).
        val out = mutableListOf<List<Vec2>>()
        val todo = ArrayDeque<Pair<List<Vec2>, Int>>()
        todo.addLast(poly to 0)
        while (todo.isNotEmpty()) {
            val (piece, from) = todo.removeLast()
            val box = Box(piece)
            var cut: List<List<Vec2>>? = null
            var cutAt = 0
            var inside = false
            for (k in from until near.size) {
                val hole = near[k]
                if (!hole.touches(box)) continue
                // Un pezzo tutto dentro un buco che deve ancora venire sparisce comunque: tolto subito, non si fa a pezzi.
                if (hole.contains(piece, box)) { inside = true; break }
                if (cut == null) {
                    val c = subtract(piece, hole.points)
                    if (c.size != 1 || c[0] !== piece) { cut = c; cutAt = k }
                }
            }
            if (inside) continue
            if (cut == null) out += piece else for (c in cut) todo.addLast(c to cutAt + 1)
        }
        return out
    }

    /** Il rettangolo che contiene un poligono. */
    private class Box(points: List<Vec2>) {
        var minX = Double.POSITIVE_INFINITY; private set
        var maxX = Double.NEGATIVE_INFINITY; private set
        var minY = Double.POSITIVE_INFINITY; private set
        var maxY = Double.NEGATIVE_INFINITY; private set

        init {
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
        }

        /** I due rettangoli si sovrappongono (toccarsi su un lato non basta). */
        fun overlaps(o: Box) = o.maxX > minX && maxX > o.minX && o.maxY > minY && maxY > o.minY
    }

    /** Buco convesso con il suo verso e il rettangolo che lo contiene, calcolati una volta sola. */
    private class Hole(val points: List<Vec2>, private val box: Box) {
        private val sign = if (Polygon.signedArea(points) >= 0) 1.0 else -1.0

        /** Il rettangolo del buco e quello del pezzo si sovrappongono (se no il buco non lo taglia). */
        fun touches(piece: Box) = box.overlaps(piece)

        /** Il poligono `piece` (dentro il rettangolo `pieceBox`) sta tutto dentro il buco (bordi compresi). */
        fun contains(piece: List<Vec2>, pieceBox: Box): Boolean {
            if (pieceBox.minX < box.minX - 1e-9 || pieceBox.maxX > box.maxX + 1e-9 || pieceBox.minY < box.minY - 1e-9 || pieceBox.maxY > box.maxY + 1e-9) return false
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

    /**
     * Il poligono convesso `b` sta tutto da una parte di un lato di `a` (al più lo tocca): i due non si sovrappongono, e il
     * buco non deve fare a pezzi il poligono lungo i prolungamenti dei suoi lati (pezzi inutili che si moltiplicavano).
     */
    private fun apart(a: List<Vec2>, b: List<Vec2>): Boolean {
        val sign = if (Polygon.signedArea(a) >= 0) 1.0 else -1.0
        for (k in a.indices) {
            val p = a[k]
            val q = a[(k + 1) % a.size]
            // Un lato cortissimo (resto di un taglio) non ha una direzione sicura: non decide niente.
            val length = p.distanceTo(q)
            if (length < 1e-6) continue
            if (b.all { cross(p, q, it) * sign <= 1e-7 * length }) return true
        }
        return false
    }

    /** I rettangoli che contengono i due poligoni si toccano (se no il buco non taglia niente). */
    private fun overlaps(a: List<Vec2>, b: List<Vec2>): Boolean =
        a.maxOf { it.x } > b.minOf { it.x } && b.maxOf { it.x } > a.minOf { it.x } &&
            a.maxOf { it.y } > b.minOf { it.y } && b.maxOf { it.y } > a.minOf { it.y }
}
