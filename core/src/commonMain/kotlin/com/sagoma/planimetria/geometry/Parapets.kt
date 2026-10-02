package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Parapetti di balconi e terrazze: vanno solo sui tratti che danno sul vuoto. Dove il balcone tocca la casa
 * c'è il muro della casa; dove tocca un altro balcone o terrazza i due formano un'unica superficie, senza
 * ringhiera in mezzo.
 */
object Parapets {
    /** Tratti (in cm lungo il muro, da 0 alla sua lunghezza) del muro `i` di `room` che danno sul vuoto. */
    fun openStretches(plan: FloorPlan, room: Room, i: Int): List<ClosedFloatingPointRange<Double>> {
        // Lato eliminato: aperto, senza parapetto.
        if (room.isRemoved(i)) return emptyList()
        val s = room.wallStart(i)
        val e = room.wallEnd(i)
        val len = s.distanceTo(e)
        if (len < 1.0) return emptyList()
        val u = (e - s) / len
        var open = listOf(0.0..len)
        for (o in plan.rooms) {
            if (o.id == room.id) continue
            for (j in 0 until o.wallCount) {
                if (!Dimensions.inContact(s, e, o.wallStart(j), o.wallEnd(j))) continue
                val t1 = ((o.wallStart(j) - s) dot u).coerceIn(0.0, len)
                val t2 = ((o.wallEnd(j) - s) dot u).coerceIn(0.0, len)
                if (abs(t2 - t1) > 0.5) open = open.flatMap { cut(it, min(t1, t2)..max(t1, t2)) }
            }
        }
        return open.filter { it.endInclusive - it.start > 0.5 }
    }

    /** Tutti i tratti di parapetto del balcone o terrazza, come segmenti in pianta. */
    fun segments(plan: FloorPlan, room: Room): List<Pair<Vec2, Vec2>> = (0 until room.wallCount).flatMap { i ->
        val s = room.wallStart(i)
        val u = (room.wallEnd(i) - s).normalized()
        openStretches(plan, room, i).map { r -> (s + u * r.start) to (s + u * r.endInclusive) }
    }

    internal fun cut(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>): List<ClosedFloatingPointRange<Double>> {
        if (b.endInclusive <= a.start || b.start >= a.endInclusive) return listOf(a)
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        if (b.start > a.start) out += a.start..b.start
        if (b.endInclusive < a.endInclusive) out += b.endInclusive..a.endInclusive
        return out
    }
}
