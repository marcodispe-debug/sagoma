package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.ExcludedZone
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2

/*
 * Misure di una stanza sul filo interno dei muri, ognuno con il suo spessore. Il poligono della stanza
 * passa per la mezzeria dei muri: il lato interno sta a metà spessore verso la stanza.
 */

/** Contorno sul filo interno dei muri. */
fun Room.interior(): List<Vec2> = Polygon.inset(points, innerThicknesses.map { it / 2 })

/** Contorno sul filo esterno dei muri. */
fun Room.exterior(): List<Vec2> = Polygon.inset(points, outerThicknesses.map { -it / 2 })

/** Filo interno con gli scalini dove un muro cambia spessore a metà (per disegnare e misurare, non un punto per angolo). */
fun Room.interiorOutline(): List<Vec2> = expandCurves().room.let { r -> Polygon.insetSteps(r.points, r.innerThicknesses.map { it / 2 }) }

/** Filo esterno con gli scalini dove un muro cambia spessore a metà. */
fun Room.exteriorOutline(): List<Vec2> = expandCurves().room.let { r -> Polygon.insetSteps(r.points, r.outerThicknesses.map { -it / 2 }) }

/** Lunghezza di ogni muro sul lato interno (cm). */
fun Room.interiorLengths(): List<Double> = Polygon.interiorEdgeLengths(points, innerThicknesses).mapIndexed { i, l ->
    // Muro curvo: la lunghezza dell'arco sul filo interno.
    arcOf(i)?.arcLength(thicknessOf(i) / 2) ?: l
}

/** Superficie calpestabile (cm²). */
fun Room.interiorArea(): Double = expandCurves().room.let { Polygon.interiorArea(it.points, it.innerThicknesses) }

/** cm² della zona non calpestabile che cadono dentro la stanza (una zona disegnata fin dentro il muro conta solo per la parte interna). */
fun Room.zoneArea(zone: ExcludedZone): Double {
    val inner = interiorOutline()
    return when {
        Polygon.isConvex(zone.points) -> Polygon.area(Polygon.clipConvex(inner, zone.points))
        Polygon.isConvex(inner) -> Polygon.area(Polygon.clipConvex(zone.points, inner))
        else -> Polygon.area(zone.points)
    }
}

/** Area utile in cm²: quella interna ai muri meno le zone non calpestabili (come i m² scritti sulle piantine). */
fun Room.usableArea(): Double = (interiorArea() - excludedZones.sumOf { zoneArea(it) }).coerceAtLeast(0.0)

/** L'angolo `k` sta a metà di un muro dritto: i due muri che vi si incontrano sono dritti e allineati. */
fun Room.isStraightCorner(k: Int): Boolean {
    val n = wallCount
    if (n < 3 || k !in 0 until n) return false
    return arcOf((k - 1 + n) % n) == null && arcOf(k) == null && Polygon.straightAt(points, k)
}

/** Di quanto la mezzeria vera del muro `i` sta fuori dalla linea della stanza (cm; vedi [Room.wallOffsets]). Solo muri dritti. */
fun Room.wallShift(i: Int): Double = if (arcOf(i) != null) 0.0 else wallOffsets[i] ?: 0.0

/** Spessore di ogni muro misurato dalla linea della stanza verso dentro, ×2 (il filo interno sta a metà di questo). */
val Room.innerThicknesses: List<Double> get() = List(wallCount) { thicknessOf(it) - 2 * wallShift(it) }

/** Spessore di ogni muro misurato dalla linea della stanza verso fuori, ×2 (il filo esterno sta a metà di questo). */
val Room.outerThicknesses: List<Double> get() = List(wallCount) { thicknessOf(it) + 2 * wallShift(it) }

/** L'angolo `k` è uno scalino: sta a metà di un muro dritto, che lì cambia spessore. */
fun Room.isStepCorner(k: Int): Boolean =
    isStraightCorner(k) && kotlin.math.abs(thicknessOf((k - 1 + wallCount) % wallCount) - thicknessOf(k)) > 1e-6

/**
 * Scalino `k` spostato lungo il muro, a `t` cm dall'angolo prima (al massimo fino a 10 cm dagli angoli accanto). Porte,
 * finestre e impianti del tratto dopo restano dove sono.
 */
fun Room.slideStepCorner(k: Int, t: Double): Room {
    val n = wallCount
    val a = points[(k - 1 + n) % n]
    val b = points[(k + 1) % n]
    val total = a.distanceTo(b)
    if (total < 20.0) return this
    val u = (b - a) / total
    val old = (points[k] - a) dot u
    val now = t.coerceIn(10.0, total - 10.0)
    val dt = now - old
    return copy(
        points = points.toMutableList().also { it[k] = a + u * now },
        openings = openings.map { if (it.wallIndex == k) it.copy(position = (it.position - dt).coerceAtLeast(0.0)) else it },
        fixtures = fixtures.map { if (it.kind.mount == com.sagoma.planimetria.model.Mount.Wall && it.wallIndex == k) it.copy(position = (it.position - dt).coerceAtLeast(0.0)) else it },
    )
}
