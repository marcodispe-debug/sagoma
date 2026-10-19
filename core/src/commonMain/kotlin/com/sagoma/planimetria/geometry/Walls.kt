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

/** Lunghezza di ogni muro sul lato interno (cm). */
fun Room.interiorLengths(): List<Double> = Polygon.interiorEdgeLengths(points, thicknesses)

/** Superficie calpestabile (cm²). */
fun Room.interiorArea(): Double = Polygon.interiorArea(points, thicknesses)

/** cm² della zona non calpestabile che cadono dentro la stanza (una zona disegnata fin dentro il muro conta solo per la parte interna). */
fun Room.zoneArea(zone: ExcludedZone): Double {
    val inner = interior()
    return when {
        Polygon.isConvex(zone.points) -> Polygon.area(Polygon.clipConvex(inner, zone.points))
        Polygon.isConvex(inner) -> Polygon.area(Polygon.clipConvex(zone.points, inner))
        else -> Polygon.area(zone.points)
    }
}

/** Area utile in cm²: quella interna ai muri meno le zone non calpestabili (come i m² scritti sulle piantine). */
fun Room.usableArea(): Double = (interiorArea() - excludedZones.sumOf { zoneArea(it) }).coerceAtLeast(0.0)
