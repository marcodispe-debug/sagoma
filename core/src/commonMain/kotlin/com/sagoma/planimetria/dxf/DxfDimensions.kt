package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.ExteriorDimensions
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.arcOf
import com.sagoma.planimetria.geometry.expandCurves
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.geometry.measuredThicknesses
import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.geometry.toRadians
import com.sagoma.planimetria.geometry.wallShift
import com.sagoma.planimetria.model.Dimension
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.PlanUnits
import com.sagoma.planimetria.model.TextNote
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Quote e testi della pianta nel DXF, come sulla pianta e nel PDF ma in centimetri veri (a 1:100 una scritta da 15 cm è
 * alta 1,5 mm sul foglio): le quote a catena fuori dalla casa, le misure dei muri dentro le stanze, larghezza e altezza di
 * porte e finestre, le quote e i testi messi a mano. Le scritte di traverso si leggono dal basso o da destra.
 */
internal object DxfDimensions {
    /** Altezza delle scritte delle quote (cm). */
    const val TEXT = 15.0
    /** Altezza delle misure di porte e finestre (cm). */
    private const val OPENING_TEXT = 12.0
    /** Spazio tra una scritta e la sua linea (cm). */
    private const val GAP = 3.0
    /** Mezza lunghezza dei trattini obliqui (cm). */
    private const val TICK = 8.0
    /** Distanza delle due file di quote esterne dal bordo del disegno (cm). */
    const val ROW1 = 50.0
    const val ROW2 = 85.0
    /** Distanza della linea di quota dal filo interno del muro, dentro la stanza (cm). */
    private const val INSIDE = 20.0

    /** Riquadro (in pianta) da non coprire con le misure: i nomi delle stanze. */
    class Box(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
        fun overlaps(o: Box) = minX < o.maxX && o.minX < maxX && minY < o.maxY && o.minY < maxY
    }

    /** Angolo (gradi, in senso orario come sulla pianta) per scrivere lungo `d`: da sinistra a destra, o dal basso in alto. */
    fun readable(d: Vec2): Double {
        var deg = toDegrees(atan2(d.y, d.x))
        if (deg >= 90.0 - 1e-6) deg -= 180.0
        if (deg < -90.0 - 1e-6) deg += 180.0
        return deg
    }

    /** Verso l'alto della scritta girata di `deg` (pianta, y in giù). */
    private fun up(deg: Double): Vec2 = toRadians(deg).let { Vec2(sin(it), -cos(it)) }

    private fun DxfWriter.slashes(layer: DxfWriter.Layer, dir: Vec2, vararg at: Vec2) {
        val s = Vec2(dir.x - dir.y, dir.y + dir.x) * (TICK / sqrt(2.0))
        for (p in at) line(layer, p - s, p + s)
    }

    /**
     * Catene esterne: su ogni lato della casa una fila con muri, stanze, tramezzi (e balconi che sporgono) e, più fuori, il
     * totale. Le misure fuori dalla linea, o dentro se lì non c'è posto; se non c'è posto né fuori né dentro non si scrivono.
     */
    fun chains(w: DxfWriter, layer: DxfWriter.Layer, d: ExteriorDimensions) {
        for (c in d.chains) {
            if (c.marks.size < 2) continue
            val deg = readable(c.along)
            fun p(t: Double, row: Double) = c.at(t) + c.out * row
            w.line(layer, p(c.marks.first(), ROW1), p(c.marks.last(), ROW1))
            for (t in c.marks) {
                w.line(layer, p(t, ROW1 - 15.0), p(t, ROW1 + 8.0))
                w.slashes(layer, c.along, p(t, ROW1))
            }
            // Corsie per le scritte, fuori e dentro la linea: tratti già occupati (cm lungo la catena).
            val lanes = List(2) { mutableListOf<ClosedFloatingPointRange<Double>>() }
            for ((a, b) in c.marks.zipWithNext()) {
                val len = b - a
                if (len < 0.05) continue
                val text = PlanUnits.format(len)
                val mid = (a + b) / 2
                val half = DxfWriter.width(TEXT, text) / 2 + GAP
                val span = (mid - half)..(mid + half)
                val lane = lanes.indexOfFirst { l -> l.none { it.start < span.endInclusive && span.start < it.endInclusive } }
                if (lane < 0) continue
                lanes[lane] += span
                val side = if (lane == 0) 1.0 else -1.0
                w.text(layer, p(mid, ROW1 + side * (GAP + TEXT / 2)), TEXT, text, deg)
            }
            val s = c.total.start
            val e = c.total.endInclusive
            w.line(layer, p(s, ROW2), p(e, ROW2))
            for (t in listOf(s, e)) {
                w.line(layer, p(t, ROW1 + 8.0), p(t, ROW2 + 8.0))
                w.slashes(layer, c.along, p(t, ROW2))
            }
            w.text(layer, p((s + e) / 2, ROW2 + GAP + TEXT / 2), TEXT, PlanUnits.format(e - s), deg)
        }
    }

    /**
     * Misure interne dei muri, come sulla pianta: dentro la stanza, parallele al filo interno, con richiami e trattini. Non si
     * ripetono quelle già nelle catene esterne né il lato di fronte uguale; la scritta evita porte, finestre e nomi delle stanze.
     */
    fun walls(w: DxfWriter, layer: DxfWriter.Layer, plan: FloorPlan, d: ExteriorDimensions, avoid: List<Box>) {
        for (logical in plan.rooms) {
            if (logical.wallCount < 3) continue
            val expanded = logical.expandCurves()
            val room = expanded.room
            val edges = Polygon.insetEdges(room.points, room.measuredThicknesses.map { it / 2 })
            val lengths = room.interiorLengths()
            val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
            val drawn = mutableListOf<Pair<Vec2, Vec2>>()
            for (i in 0 until room.wallCount) {
                if (logical.arcOf(expanded.origin[i]) != null) continue
                if ((logical.id to expanded.origin[i]) in d.covered) continue
                val len = lengths[i]
                if (Dimensions.openSideDrawnBefore(plan, room, i, len)) continue
                val (p, q) = edges[i]
                if (!(len >= 3 * INSIDE) || !len.isFinite()) continue
                val u = (q - p).normalized()
                val repeated = drawn.any { (a, b) ->
                    val v = b - a
                    val ta = (a - p) dot u
                    val tb = (b - p) dot u
                    abs(u.x * v.y - u.y * v.x) < 0.01 * v.length && abs(v.length - p.distanceTo(q)) < 0.5 &&
                        min(max(ta, tb), p.distanceTo(q)) - max(min(ta, tb), 0.0) >= 0.9 * p.distanceTo(q)
                }
                if (repeated) continue
                val n = u.perp() * sign // verso l'interno della stanza
                val a = p + n * INSIDE
                val b = q + n * INSIDE
                w.line(layer, p + n * GAP, a + n * GAP)
                w.line(layer, q + n * GAP, b + n * GAP)
                w.line(layer, a, b)
                w.slashes(layer, u, a, b)
                drawn += p to q

                val text = PlanUnits.format(len)
                val textLen = DxfWriter.width(TEXT, text) + 4 * GAP
                if (textLen > len) continue
                val free = Dimensions.freeAnchor(plan, room.wallStart(i), room.wallEnd(i), textLen)
                val preferred = ((free - p) dot u).coerceIn(textLen / 2, len - textLen / 2)
                val tw = DxfWriter.width(TEXT, text)
                val boxW = abs(tw * u.x) + abs(TEXT * u.y)
                val boxH = abs(tw * u.y) + abs(TEXT * u.x)
                fun center(along: Double) = p + u * along + n * (INSIDE + GAP + TEXT / 2)
                fun clear(along: Double): Boolean {
                    val c = center(along)
                    val box = Box(c.x - boxW / 2, c.y - boxH / 2, c.x + boxW / 2, c.y + boxH / 2)
                    return avoid.none { it.overlaps(box) }
                }
                val candidates = listOf(preferred) + listOf(0.25, 0.75, 0.15, 0.85).map { (len * it).coerceIn(textLen / 2, len - textLen / 2) }
                val along = candidates.firstOrNull(::clear) ?: continue
                w.text(layer, center(along), TEXT, text, readable(u))
            }
        }
    }

    /**
     * Larghezza e altezza di porte e finestre, una sopra l'altra («90» sopra «210»), di traverso al muro accanto all'apertura:
     * fuori dalla casa sui muri esterni; sui muri interni dal lato dove la porta non apre. `plan` è la pianta srotolata.
     */
    fun openings(w: DxfWriter, layer: DxfWriter.Layer, plan: FloorPlan, d: ExteriorDimensions) {
        for (r in plan.rooms) {
            if (r.outdoor) continue
            for (o in r.openings) {
                if (o.wallIndex >= r.wallCount || !o.kind.hasLeaves) continue
                val (a, b) = Openings.span(r, o)
                val start = r.wallStart(o.wallIndex)
                val dir = r.wallEnd(o.wallIndex) - start
                if (dir.length < 1e-6) continue
                val u = dir.normalized()
                val mid = (a + b) / 2.0
                val outward = r.outwardOf(o.wallIndex)
                val outside = d.outsideAt(r.id, o.wallIndex, (mid - start) dot u)
                val toOutside = outside || (!o.kind.glazed && !o.sliding && o.opensInward)
                val n = if (toOutside) outward else outward * -1.0
                val face = r.thicknessOf(o.wallIndex) / 2 + (if (toOutside) 1 else -1) * r.wallShift(o.wallIndex)
                val width = PlanUnits.format(o.width)
                val height = PlanUnits.format(o.height)
                if (2 * OPENING_TEXT + 3 > a.distanceTo(b) * 1.8) continue // troppo piccola
                val long = max(DxfWriter.width(OPENING_TEXT, width), DxfWriter.width(OPENING_TEXT, height))
                val center = mid + n * (face + GAP + long / 2)
                val deg = readable(n)
                val x = Vec2(cos(toRadians(deg)), sin(toRadians(deg)))
                val up = up(deg)
                w.text(layer, center + up * (OPENING_TEXT / 2 + 1.5), OPENING_TEXT, width, deg)
                w.line(layer, center - x * (long / 2), center + x * (long / 2))
                w.text(layer, center - up * (OPENING_TEXT / 2 + 1.5), OPENING_TEXT, height, deg)
            }
        }
    }

    /** Quota messa a mano: richiami dagli estremi (staccati di poco), linea con i trattini a 45°, misura (o testo) sopra la linea. */
    fun manual(w: DxfWriter, layer: DxfWriter.Layer, dims: List<Dimension>) {
        for (d in dims) {
            if (!(d.length >= 0.5) || !d.offset.isFinite()) continue
            val ext = d.lineA - d.a
            val extLen = ext.length
            if (extLen > 10.0) {
                val e = ext / extLen
                w.line(layer, d.a + e * 5.0, d.lineA + e * 8.0)
                w.line(layer, d.b + e * 5.0, d.lineB + e * 8.0)
            }
            w.line(layer, d.lineA, d.lineB)
            w.slashes(layer, d.direction, d.lineA, d.lineB)
            val deg = readable(d.direction)
            w.text(layer, (d.lineA + d.lineB) / 2.0 + up(deg) * (GAP + TEXT / 2), TEXT, d.label, deg)
        }
    }

    /** Testo messo a mano, alto e girato come sulla pianta; la freccia di richiamo parte dal bordo della scritta. */
    fun notes(w: DxfWriter, layer: DxfWriter.Layer, notes: List<TextNote>) {
        for (t in notes) {
            if (t.text.isBlank() || !(t.size > 0) || !t.size.isFinite()) continue
            w.text(layer, t.at, t.size, t.text, t.rotation)
            val tip = t.arrowTo ?: continue
            val v = tip - t.at
            val l = v.length
            if (!(l > 1.0)) continue
            val u = v / l
            // Dove la freccia esce dal riquadro della scritta (nel verso della scritta girata).
            val r = toRadians(t.rotation)
            val lx = abs(u.x * cos(r) + u.y * sin(r))
            val ly = abs(-u.x * sin(r) + u.y * cos(r))
            val hw = DxfWriter.width(t.size, t.text.trim()) / 2 + GAP
            val hh = t.size / 2 + GAP
            val exit = min(if (lx > 1e-9) hw / lx else Double.MAX_VALUE, if (ly > 1e-9) hh / ly else Double.MAX_VALUE)
            val head = min(15.0, l / 3)
            if (exit < l - head) w.line(layer, t.at + u * exit, tip - u * head)
            val side = u.perp() * (head * 0.4)
            w.solid(layer, listOf(tip, tip - u * head + side, tip - u * head - side))
        }
    }
}
