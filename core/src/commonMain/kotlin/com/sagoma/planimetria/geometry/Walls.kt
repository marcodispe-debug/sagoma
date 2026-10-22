package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.ExcludedZone
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2

/*
 * Misure di una stanza sul filo interno dei muri, ognuno con il suo spessore. Il poligono della stanza
 * passa per la mezzeria dei muri: il lato interno sta a metà spessore verso la stanza.
 */

/** Contorno sul filo interno dei muri. */
fun Room.interior(): List<Vec2> = Polygon.inset(points, thicknesses.map { it / 2 })

/** Contorno sul filo esterno dei muri. */
fun Room.exterior(): List<Vec2> = Polygon.inset(points, thicknesses.map { -it / 2 })

/** Filo interno con gli scalini dove un muro cambia spessore a metà (per disegnare e misurare, non un punto per angolo). */
fun Room.interiorOutline(): List<Vec2> = expandCurves().room.let { r -> Polygon.insetSteps(r.points, r.thicknesses.map { it / 2 }) }

/** Filo esterno con gli scalini dove un muro cambia spessore a metà. */
fun Room.exteriorOutline(): List<Vec2> = expandCurves().room.let { r -> Polygon.insetSteps(r.points, r.thicknesses.map { -it / 2 }) }

/** Lunghezza di ogni muro sul lato interno (cm). */
fun Room.interiorLengths(): List<Double> = Polygon.interiorEdgeLengths(points, thicknesses).mapIndexed { i, l ->
    // Muro curvo: la lunghezza dell'arco sul filo interno.
    arcOf(i)?.arcLength(thicknessOf(i) / 2) ?: l
}

/** Superficie calpestabile (cm²). */
fun Room.interiorArea(): Double = expandCurves().room.let { Polygon.interiorArea(it.points, it.thicknesses) }

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
