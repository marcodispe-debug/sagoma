package com.sagoma.planimetria.geometry

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
