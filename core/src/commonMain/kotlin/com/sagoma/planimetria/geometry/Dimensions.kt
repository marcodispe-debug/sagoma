package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Quota di un muro, tracciata sul segmento in mezzeria a→b (lungo [extent]).
 * - `length`: il valore scritto, cioè la lunghezza del muro sul lato interno della stanza.
 * - `side`: normale unitaria del lato su cui scrivere il testo (ignorata se `onWall`).
 * - `onWall`: muro in comune tra due stanze che misura uguale dai due lati, quotato una sola volta
 *   con il testo centrato sul muro stesso, così non "appartiene" a nessuna delle due stanze.
 * - `blocked`: tratti del muro (in cm dall'inizio `a`) occupati da aperture, da non coprire col testo.
 */
data class DimensionLine(
    val a: Vec2,
    val b: Vec2,
    val length: Double,
    val side: Vec2,
    val onWall: Boolean = false,
    val blocked: List<ClosedFloatingPointRange<Double>> = emptyList(),
) {
    /** Lunghezza del segmento in mezzeria su cui si posiziona il testo. */
    val extent: Double get() = a.distanceTo(b)

    /**
     * Punto (distanza dall'inizio del muro) in cui centrare un testo lungo `textCm`: il centro del
     * muro se è libero, altrimenti il centro del tratto libero più lungo.
     */
    fun anchorT(textCm: Double): Double {
        val half = textCm / 2
        val mid = extent / 2
        if (blocked.none { it.start < mid + half && it.endInclusive > mid - half }) return mid
        val free = freeIntervals()
        return free.maxByOrNull { it.endInclusive - it.start }?.let { (it.start + it.endInclusive) / 2 } ?: mid
    }

    fun pointAt(t: Double): Vec2 = a + (b - a).normalized() * t

    private fun freeIntervals(): List<ClosedFloatingPointRange<Double>> {
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        var cursor = 0.0
        for (r in blocked.sortedBy { it.start }) {
            if (r.start > cursor) out += cursor..r.start
            cursor = max(cursor, r.endInclusive)
        }
        if (cursor < extent) out += cursor..extent
        return out
    }
}

object Dimensions {

    /** Spazio lasciato libero ai lati di ogni apertura. */
    private const val OPENING_MARGIN_CM = 10.0

    /** Un muro della pianta con la sua misura sul lato interno della stanza. */
    private class Wall(val roomId: Long, val a: Vec2, val b: Vec2, val interior: Double)

    /**
     * Quote di tutti i muri della pianta, con le lunghezze misurate sul lato interno di ogni stanza e
     * senza doppioni: un muro in comune che misura uguale dai due lati viene quotato una volta sola,
     * sul muro; se le misure interne differiscono ciascuna stanza scrive la propria verso il proprio
     * interno, così i testi non si sovrappongono.
     */
    fun of(plan: FloorPlan): List<DimensionLine> {
        val openingSpans = plan.rooms.flatMap { r ->
            r.openings.filter { it.wallIndex < r.wallCount }.map { Openings.span(r, it) }
        } + wallFixtureSpans(plan)
        val wallsByRoom = plan.rooms.associate { r ->
            val lengths = r.interiorLengths()
            r.id to (0 until r.wallCount).map { i -> Wall(r.id, r.wallStart(i), r.wallEnd(i), lengths[i]) }
        }
        val all = wallsByRoom.values.flatten()
        val out = mutableListOf<DimensionLine>()
        val done = mutableListOf<Wall>()
        for (room in plan.rooms) for ((i, w) in wallsByRoom.getValue(room.id).withIndex()) {
            if (w.a.distanceTo(w.b) < 1e-6) continue
            val inward = Openings.inwardNormal(room, i)
            val blocked = blockedOn(w.a, w.b, openingSpans)
            val contacts = all.filter { it.roomId != room.id && sharesWall(w.a, w.b, it.a, it.b) }

            if (contacts.isEmpty()) {
                out += DimensionLine(w.a, w.b, w.interior, inward * -1.0, blocked = blocked)
                continue
            }
            val sameLength = contacts.any { abs(it.interior - w.interior) < 0.5 }
            if (sameLength) {
                if (done.any { sharesWall(w.a, w.b, it.a, it.b) && abs(it.interior - w.interior) < 0.5 }) continue
                out += DimensionLine(w.a, w.b, w.interior, Vec2.Zero, onWall = true, blocked = blocked)
            } else {
                out += DimensionLine(w.a, w.b, w.interior, inward, blocked = blocked)
            }
            done += w
        }
        return out
    }

    /**
     * Dove centrare un'etichetta lunga `textCm` sul muro a→b senza coprire aperture: di qualunque
     * stanza, quindi anche quelle della stanza confinante sullo stesso muro. Usata anche dai campi lunghezza.
     */
    fun freeAnchor(plan: FloorPlan, a: Vec2, b: Vec2, textCm: Double): Vec2 {
        val spans = plan.rooms.flatMap { r -> r.openings.filter { it.wallIndex < r.wallCount }.map { Openings.span(r, it) } } +
            wallFixtureSpans(plan)
        val line = DimensionLine(a, b, a.distanceTo(b), Vec2.Zero, blocked = blockedOn(a, b, spans))
        return line.pointAt(line.anchorT(textCm))
    }

    /**
     * Tratti di muro (in mezzeria) occupati dagli impianti a muro: il calorifero per la sua larghezza,
     * prese e interruttori per 20 cm attorno al simbolo. Anche loro non vanno coperti dalle etichette.
     */
    private fun wallFixtureSpans(plan: FloorPlan): List<Pair<Vec2, Vec2>> = plan.rooms.flatMap { r ->
        r.fixtures.filter { it.kind.mount == Mount.Wall && it.wallIndex < r.wallCount }.map { f ->
            val start = r.wallStart(f.wallIndex)
            val u = (r.wallEnd(f.wallIndex) - start).normalized()
            val half = if (f.length > 0) f.length / 2 else 10.0
            val c = Openings.clampPosition(r.wallLength(f.wallIndex), f.length, f.position)
            (start + u * (c - half)) to (start + u * (c + half))
        }
    }

    /** Tratti del muro a→b coperti da aperture (di qualunque stanza) che stanno su quel muro. */
    private fun blockedOn(a: Vec2, b: Vec2, spans: List<Pair<Vec2, Vec2>>): List<ClosedFloatingPointRange<Double>> {
        val u = (b - a).normalized()
        val len = a.distanceTo(b)
        return spans.filter { (p, q) -> inContact(a, b, p, q) }.map { (p, q) ->
            val t1 = (p - a) dot u
            val t2 = (q - a) dot u
            max(0.0, min(t1, t2) - OPENING_MARGIN_CM)..min(len, max(t1, t2) + OPENING_MARGIN_CM)
        }
    }

    /**
     * Imposta l'altezza del muro `index` della stanza `roomId` (null = altezza soffitto) e la stessa
     * sui muri delle altre stanze in comune con esso: è lo stesso muro fisico. Un'altezza uguale al
     * soffitto di una stanza vale come "standard" per quella stanza.
     */
    fun withWallHeight(plan: FloorPlan, roomId: Long, index: Int, height: Double?): FloorPlan {
        val room = plan.room(roomId) ?: return plan
        if (index >= room.wallCount) return plan
        val a = room.wallStart(index)
        val b = room.wallEnd(index)
        fun Room.withHeight(i: Int): Room {
            val map = if (height == null || height == ceilingHeight) wallHeights - i else wallHeights + (i to height)
            return copy(wallHeights = map)
        }
        return plan.copy(
            rooms = plan.rooms.map { r ->
                if (r.id == roomId) r.withHeight(index)
                else (0 until r.wallCount)
                    .filter { j -> sharesWall(a, b, r.wallStart(j), r.wallEnd(j)) }
                    .fold(r) { acc, j -> acc.withHeight(j) }
            },
        )
    }

    /** Segmenti paralleli, entro la tolleranza dei muri condivisi, che si sovrappongono lungo la loro direzione. */
    fun inContact(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Boolean = overlap(a, b, c, d) >= 1.0

    /**
     * Muro davvero in comune tra due stanze: a contatto per almeno metà del più corto. Due muri
     * allineati che si toccano solo per pochi centimetri agli estremi (stanze una dopo l'altra) non contano.
     */
    fun sharesWall(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Boolean =
        overlap(a, b, c, d) >= 0.5 * min(a.distanceTo(b), c.distanceTo(d))

    /** Lunghezza del tratto in cui a→b e c→d sono a contatto (0 se non paralleli o troppo distanti). */
    fun overlap(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Double {
        val len = c.distanceTo(d)
        if (len < 1e-6 || a.distanceTo(b) < 1e-6) return 0.0
        val u = (b - a).normalized()
        val v = (d - c) / len
        if (abs(u.x * v.y - u.y * v.x) > 0.01) return 0.0
        if (abs((a - c) dot v.perp()) > Openings.SHARED_WALL_TOLERANCE_CM) return 0.0
        val ta = (a - c) dot v
        val tb = (b - c) dot v
        return max(0.0, minOf(maxOf(ta, tb), len) - maxOf(minOf(ta, tb), 0.0))
    }
}
