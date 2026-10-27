package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.geometry.ExteriorDimensions
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.geometry.wallShift
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.PlanUnits
import com.sagoma.planimetria.model.Vec2
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

private val ChainColor = Color(0xFF495057)

/**
 * Grandezza delle scritte delle quote rispetto a quella normale: a ogni zoom cresce un poco (non quanto il disegno), da 1
 * con la pianta piccola fino a 2 volte da molto vicino.
 */
internal fun dimensionTextScale(cam: Camera, density: Float): Float =
    ((cam.scale / density) / 0.6f).pow(0.3f).coerceIn(1f, 2f)

/** Distanze delle due file di quote dal bordo del disegno, e quanto spazio prendono in tutto (per i margini). */
internal object ChainLayout {
    val ROW1 = 30.dp
    val ROW_GAP = 18.dp
    /** Spazio totale fuori dal disegno con le scritte a grandezza normale. */
    val SPACE = 64.dp
}

private fun Camera.o(v: Vec2) = Offset(toScreenX(v.x), toScreenY(v.y))
private fun Vec2.f() = Offset(x.toFloat(), y.toFloat())

/** Angolo (gradi) per scrivere lungo `d` senza capovolgere il testo. */
private fun readable(d: Offset): Float {
    var deg = toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat()
    if (deg > 90f) deg -= 180f
    if (deg <= -90f) deg += 180f
    return deg
}

/**
 * Quote esterne a catena come nelle tavole: su ogni lato della casa una fila con muri, stanze, tramezzi (e balconi che
 * sporgono) e, più fuori, il totale. Trattini obliqui ai segni; le misure sopra la linea, oppure sotto se lì non c'è posto
 * (tratti stretti come i tramezzi); se non c'è posto né sopra né sotto, quella misura non si scrive.
 */
internal fun DrawScope.drawExteriorChains(d: ExteriorDimensions, cam: Camera, measurer: TextMeasurer, textScale: Float = 1f) {
    val style = TextStyle(fontSize = 11.sp * textScale, color = ChainColor)
    val stroke = 1.dp.toPx()
    val tick = 4.dp.toPx() * textScale
    val gap = 2.dp.toPx()
    val row1 = ChainLayout.ROW1.toPx() * textScale
    val row2 = row1 + ChainLayout.ROW_GAP.toPx() * textScale
    for (c in d.chains) {
        if (c.marks.size < 2) continue
        val out = c.out.f()
        val along = c.along.f()
        val deg = readable(along)
        val slash = (along + out) * (tick / sqrt(2f))
        fun p(t: Double, row: Float) = cam.o(c.at(t)) + out * row
        fun mark(t: Double, row: Float, from: Float, to: Float) {
            drawLine(ChainColor, p(t, from), p(t, to), strokeWidth = stroke * 0.7f)
            val q = p(t, row)
            drawLine(ChainColor, q - slash, q + slash, strokeWidth = stroke * 1.6f)
        }
        // Prima fila: richiami corti dal lato della casa e un poco oltre la linea.
        drawLine(ChainColor, p(c.marks.first(), row1), p(c.marks.last(), row1), strokeWidth = stroke)
        for (t in c.marks) mark(t, row1, row1 - 6.dp.toPx(), row1 + 3.dp.toPx())
        // Corsie per le scritte, fuori e dentro la linea: tratti già occupati (px lungo la catena).
        val lanes = List(2) { mutableListOf<ClosedFloatingPointRange<Float>>() }
        for ((a, b) in c.marks.zipWithNext()) {
            val len = b - a
            if (len < 0.05) continue
            val text = measurer.measure(PlanUnits.format(len), style)
            val mid = (a + b) / 2
            val at = (mid * cam.scale).toFloat()
            val half = text.size.width / 2f + gap
            val span = (at - half)..(at + half)
            val lane = lanes.indexOfFirst { l -> l.none { it.start < span.endInclusive && span.start < it.endInclusive } }
            if (lane < 0) continue
            lanes[lane] += span
            val side = if (lane == 0) 1f else -1f
            val center = p(mid, row1) + out * (side * (gap + text.size.height / 2f))
            rotate(deg, pivot = center) { drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f)) }
        }
        // Seconda fila: il totale della casa, con i richiami che arrivano dalla prima.
        val (s, e) = c.total.start to c.total.endInclusive
        drawLine(ChainColor, p(s, row2), p(e, row2), strokeWidth = stroke)
        mark(s, row2, row1 + 3.dp.toPx(), row2 + 3.dp.toPx())
        mark(e, row2, row1 + 3.dp.toPx(), row2 + 3.dp.toPx())
        val text = measurer.measure(PlanUnits.format(e - s), style)
        val center = p((s + e) / 2, row2) + out * (gap + text.size.height / 2f)
        rotate(deg, pivot = center) { drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f)) }
    }
}

/**
 * Larghezza e altezza di porte e finestre, una sopra l'altra come nelle tavole («90» sopra «210»), scritte di traverso al
 * muro accanto all'apertura: fuori dalla casa sui muri esterni; sui muri interni dal lato dove la porta non apre.
 * `plan` è la pianta srotolata ([com.sagoma.planimetria.geometry.expandCurves]), come per [drawPlan].
 */
internal fun DrawScope.drawOpeningSizes(
    plan: FloorPlan, d: ExteriorDimensions, cam: Camera, measurer: TextMeasurer, background: Color, textScale: Float = 1f,
) {
    val style = TextStyle(fontSize = 10.sp * textScale, color = ChainColor)
    val gap = 3.dp.toPx()
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
            // Fuori casa; dentro casa dal lato opposto a quello in cui apre la porta (finestre e scorrevoli: nella stanza).
            val toOutside = outside || (!o.kind.glazed && !o.sliding && o.opensInward)
            val n = if (toOutside) outward else outward * -1.0
            val face = r.thicknessOf(o.wallIndex) / 2 + (if (toOutside) 1 else -1) * r.wallShift(o.wallIndex)
            val w = measurer.measure(PlanUnits.format(o.width), style)
            val h = measurer.measure(PlanUnits.format(o.height), style)
            val stack = w.size.height + h.size.height + 3f
            if (stack > a.distanceTo(b) * cam.scale * 1.8f) continue // troppo piccola a questo zoom
            val long = max(w.size.width, h.size.width).toFloat()
            val nf = n.f()
            val center = cam.o(mid + n * face) + nf * (gap + long / 2)
            rotate(readable(nf), pivot = center) {
                drawRoundRect(
                    background.copy(alpha = 0.85f), topLeft = center - Offset(long / 2 + 2f, stack / 2 + 1f),
                    size = Size(long + 4f, stack + 2f), cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawText(w, topLeft = Offset(center.x - w.size.width / 2f, center.y - w.size.height - 1.5f))
                drawLine(ChainColor, Offset(center.x - long / 2, center.y), Offset(center.x + long / 2, center.y), strokeWidth = 0.8.dp.toPx())
                drawText(h, topLeft = Offset(center.x - h.size.width / 2f, center.y + 1.5f))
            }
        }
    }
}
