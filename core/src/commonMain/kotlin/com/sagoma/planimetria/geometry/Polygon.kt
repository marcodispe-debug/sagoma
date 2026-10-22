package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width get() = maxX - minX
    val height get() = maxY - minY
    val center get() = Vec2((minX + maxX) / 2, (minY + maxY) / 2)

    fun union(o: Bounds) = Bounds(min(minX, o.minX), min(minY, o.minY), max(maxX, o.maxX), max(maxY, o.maxY))
}

object Polygon {

    /** Area con segno (formula di Gauss). Positiva per ordine orario su schermo (y verso il basso). */
    fun signedArea(pts: List<Vec2>): Double {
        var s = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2
    }

    /** Area in cm², valida per qualsiasi poligono semplice (anche a L). */
    fun area(pts: List<Vec2>): Double = abs(signedArea(pts))

    /**
     * Contorno spostato verso l'interno di `d` su ogni lato: ogni vertice è l'intersezione dei due lati
     * adiacenti traslati. Vale per qualsiasi poligono semplice (anche a L o con angoli non retti).
     */
    fun inset(pts: List<Vec2>, d: Double): List<Vec2> = inset(pts, List(pts.size) { d })

    /** Come [inset], ma ogni lato `i` (da `pts[i]` a `pts[i + 1]`) si sposta della sua distanza `d[i]`. */
    fun inset(pts: List<Vec2>, d: List<Double>): List<Vec2> {
        val n = pts.size
        if (n < 3) return pts
        // Normale interna: perp() per i poligoni orari su schermo, opposta se l'ordine è invertito.
        val sign = if (signedArea(pts) >= 0) 1.0 else -1.0
        return List(n) { i ->
            val prev = pts[(i - 1 + n) % n]
            val cur = pts[i]
            val next = pts[(i + 1) % n]
            val d1 = (cur - prev).normalized()
            val d2 = (next - cur).normalized()
            val p1 = cur + d1.perp() * (d[(i - 1 + n) % n] * sign)
            val p2 = cur + d2.perp() * (d[i] * sign)
            val cross = d1.x * d2.y - d1.y * d2.x
            if (abs(cross) < 1e-9) {
                p1 // lati allineati (o degeneri): basta traslare il punto
            } else {
                val w = p2 - p1
                p1 + d1 * ((w.x * d2.y - w.y * d2.x) / cross)
            }
        }
    }

    /**
     * Come [inset], ma dove due lati allineati hanno spessori diversi (un muro che cambia spessore a metà)
     * il contorno fa lo scalino: in quel vertice ci sono due punti, uno su ciascun lato spostato. Per
     * disegnare i muri e misurare le aree; [inset] invece tiene un punto per vertice.
     */
    fun insetSteps(pts: List<Vec2>, d: List<Double>): List<Vec2> {
        val n = pts.size
        if (n < 3) return pts
        val sign = if (signedArea(pts) >= 0) 1.0 else -1.0
        val plain = inset(pts, d)
        val out = ArrayList<Vec2>(n + 2)
        for (i in 0 until n) {
            val prevD = d[(i - 1 + n) % n]
            if (straightAt(pts, i) && abs(prevD - d[i]) > 1e-6) {
                val cur = pts[i]
                val d1 = (cur - pts[(i - 1 + n) % n]).normalized()
                val d2 = (pts[(i + 1) % n] - cur).normalized()
                out += cur + d1.perp() * (prevD * sign)
                out += cur + d2.perp() * (d[i] * sign)
            } else out += plain[i]
        }
        return out
    }

    /** Il vertice `i` sta su una retta: i due lati che vi si incontrano sono allineati e vanno nello stesso verso. */
    fun straightAt(pts: List<Vec2>, i: Int): Boolean {
        val n = pts.size
        val d1 = (pts[i] - pts[(i - 1 + n) % n]).normalized()
        val d2 = (pts[(i + 1) % n] - pts[i]).normalized()
        return abs(d1.x * d2.y - d1.y * d2.x) < 1e-6 && (d1 dot d2) > 0
    }

    /**
     * Estremi di ogni lato del contorno spostato (lato `i` spostato di `d[i]`), entrambi sulla retta del lato:
     * anche dove il lato accanto è allineato ma con un altro spessore (lì [inset] mette il punto sull'altro lato).
     */
    fun insetEdges(pts: List<Vec2>, d: List<Double>): List<Pair<Vec2, Vec2>> {
        val n = pts.size
        val inner = inset(pts, d)
        val sign = if (signedArea(pts) >= 0) 1.0 else -1.0
        return List(n) { i ->
            val a = pts[i]
            val u = (pts[(i + 1) % n] - a).normalized()
            val base = a + u.perp() * (d[i] * sign)
            fun onLine(p: Vec2) = base + u * ((p - base) dot u)
            onLine(inner[i]) to onLine(inner[(i + 1) % n])
        }
    }

    /** Il poligono è convesso (tutti gli angoli girano dallo stesso lato; lati allineati ammessi). */
    fun isConvex(pts: List<Vec2>): Boolean {
        if (pts.size < 3) return false
        var sign = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val c = pts[(i + 2) % pts.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-9) continue
            if (sign == 0.0) sign = cross else if (cross * sign < 0) return false
        }
        return true
    }

    /**
     * Parte di `subject` (qualsiasi poligono semplice) che sta dentro il poligono convesso `clip` (Sutherland–Hodgman).
     * Con un soggetto concavo possono restare lati sovrapposti, ma l'area è giusta.
     */
    fun clipConvex(subject: List<Vec2>, clip: List<Vec2>): List<Vec2> {
        if (clip.size < 3) return emptyList()
        val sign = if (signedArea(clip) >= 0) 1.0 else -1.0
        var out = subject
        for (k in clip.indices) {
            if (out.isEmpty()) break
            val a = clip[k]
            val b = clip[(k + 1) % clip.size]
            fun side(p: Vec2) = ((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)) * sign
            val input = out
            out = buildList {
                for (i in input.indices) {
                    val p = input[i]
                    val q = input[(i + 1) % input.size]
                    val fp = side(p)
                    val fq = side(q)
                    if (fp >= 0) add(p)
                    if ((fp >= 0) != (fq >= 0)) add(p + (q - p) * (fp / (fp - fq)))
                }
            }
        }
        return out
    }

    /**
     * Area calpestabile in cm²: misurata sul filo interno dei muri, cioè sul contorno ristretto di
     * metà spessore (il poligono della stanza passa per la mezzeria dei muri).
     */
    fun interiorArea(pts: List<Vec2>, wallThickness: Double): Double = interiorArea(pts, List(pts.size) { wallThickness })

    /** Come sopra, con lo spessore di ogni muro. */
    fun interiorArea(pts: List<Vec2>, thicknesses: List<Double>): Double {
        val inner = insetSteps(pts, thicknesses.map { it / 2 })
        val sa = signedArea(inner)
        // Stanza più piccola dello spessore dei muri: il contorno si ribalta, l'area utile è zero.
        return if (sa * signedArea(pts) <= 0) 0.0 else abs(sa)
    }

    /**
     * Lunghezza di ogni muro misurata sul lato interno (il lato `i` del contorno ristretto di metà
     * spessore). Per un rettangolo 500×400 in mezzeria con muri da 15 cm: 485, 385, 485, 385.
     */
    fun interiorEdgeLengths(pts: List<Vec2>, wallThickness: Double): List<Double> = interiorEdgeLengths(pts, List(pts.size) { wallThickness })

    fun interiorEdgeLengths(pts: List<Vec2>, thicknesses: List<Double>): List<Double> {
        val inner = inset(pts, thicknesses.map { it / 2 })
        val flipped = signedArea(inner) * signedArea(pts) <= 0
        val edges = insetEdges(pts, thicknesses.map { it / 2 })
        return pts.indices.map { i -> if (flipped) 0.0 else edges[i].first.distanceTo(edges[i].second) }
    }

    /**
     * Contorno in mezzeria dei muri a partire dal contorno interno (misure calpestabili): l'inverso di
     * [inset], usato per creare una stanza dalle misure interne inserite dall'utente.
     */
    fun fromInterior(interior: List<Vec2>, wallThickness: Double): List<Vec2> = inset(interior, -wallThickness / 2)

    fun bounds(pts: List<Vec2>): Bounds = Bounds(
        pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y },
    )

    fun contains(pts: List<Vec2>, p: Vec2): Boolean {
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val len2 = ab dot ab
        if (len2 == 0.0) return p.distanceTo(a)
        val t = ((p - a) dot ab / len2).coerceIn(0.0, 1.0)
        return p.distanceTo(a + ab * t)
    }

    private fun distanceToEdges(pts: List<Vec2>, p: Vec2): Double =
        pts.indices.minOf { distanceToSegment(p, pts[it], pts[(it + 1) % pts.size]) }

    /**
     * Punto interno "più lontano dai bordi" (approssimato su griglia): posizione dell'etichetta.
     * Per forme a L il baricentro può cadere fuori, questo no.
     */
    fun labelPoint(pts: List<Vec2>): Vec2 {
        val b = bounds(pts)
        val steps = 16
        var best = b.center
        var bestD = if (contains(pts, best)) distanceToEdges(pts, best) else -1.0
        for (i in 1 until steps) for (j in 1 until steps) {
            val p = Vec2(b.minX + b.width * i / steps, b.minY + b.height * j / steps)
            if (!contains(pts, p)) continue
            val d = distanceToEdges(pts, p)
            if (d > bestD + 0.5) { best = p; bestD = d }
        }
        return best
    }
}
