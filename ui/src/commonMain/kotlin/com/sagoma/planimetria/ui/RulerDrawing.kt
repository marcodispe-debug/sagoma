package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.geometry.Rulers
import com.sagoma.planimetria.model.Ruler
import kotlin.math.floor
import kotlin.math.max

private val RulerColor = Color(0xFFE67700)
private val RulerBand = Color(0xFFFFD43B)
private val DeleteColor = Color(0xFFE03131)

/**
 * Posizioni a schermo del metro e delle sue maniglie. Le maniglie stanno a distanza fissa in dp dal
 * corpo, indipendente dallo zoom (§9): usata sia per disegnare sia per il test di tocco.
 */
internal class RulerGeom(val ruler: Ruler, cam: Camera, density: Density) {
    val start = Offset(cam.toScreenX(ruler.start.x), cam.toScreenY(ruler.start.y))
    val end = Offset(cam.toScreenX(ruler.end.x), cam.toScreenY(ruler.end.y))
    val mid = (start + end) / 2f
    private val d = ruler.direction
    /** Direzione lungo il metro e perpendicolare (lato maniglie); lo schermo non ruota, quindi coincidono col mondo. */
    val u = Offset(d.x.toFloat(), d.y.toFloat())
    val n = Offset(-u.y, u.x)
    val halfLenPx = (end - start).getDistance() / 2f

    val handleGapPx = with(density) { 40.dp.toPx() }
    val bandPx = with(density) { 18.dp.toPx() }
    val rotateHandle = mid + n * handleGapPx
    /** Il × resta sempre ben separato dalla maniglia di rotazione, anche su un metro corto. */
    val deleteHandle = mid + n * handleGapPx - u * max(halfLenPx, with(density) { 44.dp.toPx() })

    val rotateRadius = with(density) { 10.dp.toPx() }
    val deleteRadius = with(density) { 7.dp.toPx() }

    // Zone di presa, più larghe dell'elemento visibile (§13); il corpo è più largo della linea.
    val rotateHit = with(density) { 22.dp.toPx() }
    val deleteHit = with(density) { 16.dp.toPx() }
    val endHit = with(density) { 22.dp.toPx() }
    val bodyHit = with(density) { 20.dp.toPx() }

    fun hitTest(p: Offset): DragTarget? {
        val id = ruler.id
        if ((p - deleteHandle).getDistance() <= deleteHit) return DragTarget.RulerDelete(id)
        if ((p - rotateHandle).getDistance() <= rotateHit) return DragTarget.RulerRotate(id)
        val dEnd = (p - end).getDistance()
        val dStart = (p - start).getDistance()
        if (minOf(dEnd, dStart) <= endHit) return DragTarget.RulerEnd(id, if (dEnd <= dStart) 1 else 0)
        // Corpo: striscia che copre la linea e il nastro con le tacche (lato -n).
        val rel = p - start
        val along = rel.x * u.x + rel.y * u.y
        val across = rel.x * n.x + rel.y * n.y
        if (along in 0f..(halfLenPx * 2) && across in -(bandPx + bodyHit / 2)..(bodyHit / 2)) return DragTarget.RulerBody(id)
        return null
    }
}

/** `showHandles = false` disegna solo la misura (esportazione): niente maniglia di rotazione né ×. */
internal fun DrawScope.drawRuler(g: RulerGeom, cam: Camera, rotating: Boolean, measurer: TextMeasurer, showHandles: Boolean = true) {
    val r = g.ruler
    // Nastro con le tacche sul lato opposto alle maniglie.
    val band = Path().apply {
        moveTo(g.start.x, g.start.y)
        lineTo(g.end.x, g.end.y)
        (g.end - g.n * g.bandPx).let { lineTo(it.x, it.y) }
        (g.start - g.n * g.bandPx).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(band, RulerBand.copy(alpha = 0.35f))
    drawLine(RulerColor, g.start, g.end, strokeWidth = 2.dp.toPx())

    // Tacche ogni 10 cm, più lunghe ogni 50 cm (saltate se troppo fitte per lo zoom).
    val px10 = 10 * cam.scale
    val count = floor(r.length / 10 + 1e-6).toInt()
    for (k in 0..count) {
        val major = k % 5 == 0
        if (!major && px10 < 4f) continue
        if (major && px10 * 5 < 4f) continue
        val p = g.start + g.u * (k * px10)
        val len = if (major) 12.dp.toPx() else 6.dp.toPx()
        drawLine(RulerColor, p, p - g.n * len, strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx())
    }

    // Estremità: linee sottili perpendicolari, per vedere con precisione inizio e fine della misura.
    val endMark = 14.dp.toPx()
    for (p in listOf(g.start, g.end)) drawLine(RulerColor, p + g.n * endMark, p - g.n * (g.bandPx + endMark / 2), strokeWidth = 1.2.dp.toPx())

    // Lunghezza sotto il nastro.
    val lenText = measurer.measure("${formatCm(r.length)} cm", TextStyle(fontSize = 12.sp, color = RulerColor, fontWeight = FontWeight.SemiBold))
    val lenPos = g.mid - g.n * (g.bandPx + 14.dp.toPx())
    drawText(lenText, topLeft = Offset(lenPos.x - lenText.size.width / 2f, lenPos.y - lenText.size.height / 2f))

    if (!showHandles) return

    // Maniglia di rotazione, collegata al centro da una linea tratteggiata.
    drawLine(
        RulerColor.copy(alpha = 0.6f), g.mid, g.rotateHandle, strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
    drawCircle(Color.White, g.rotateRadius, g.rotateHandle)
    drawCircle(RulerColor, g.rotateRadius, g.rotateHandle, style = Stroke(2.dp.toPx()))
    drawArc(
        RulerColor, startAngle = 20f, sweepAngle = 250f, useCenter = false,
        topLeft = g.rotateHandle - Offset(g.rotateRadius * 0.5f, g.rotateRadius * 0.5f),
        size = androidx.compose.ui.geometry.Size(g.rotateRadius, g.rotateRadius),
        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
    )

    // Pulsante × (più piccolo, ruota insieme al metro).
    drawCircle(DeleteColor, g.deleteRadius, g.deleteHandle)
    val a = (g.u + g.n) * (g.deleteRadius * 0.4f)
    val b = (g.u - g.n) * (g.deleteRadius * 0.4f)
    drawLine(Color.White, g.deleteHandle - a, g.deleteHandle + a, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color.White, g.deleteHandle - b, g.deleteHandle + b, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)

    // Gradi durante la rotazione: dal lato opposto al dito, lontano dal punto di contatto.
    if (rotating) {
        val deg = Rulers.angleDeg(r)
        val t = measurer.measure(
            com.sagoma.planimetria.geometry.formatDecimal(deg, 0) + "°",
            TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold),
        )
        val c = g.mid - g.n * (g.bandPx + 64.dp.toPx())
        val pad = 6.dp.toPx()
        drawRoundRect(
            RulerColor,
            topLeft = Offset(c.x - t.size.width / 2f - pad, c.y - t.size.height / 2f - pad),
            size = androidx.compose.ui.geometry.Size(t.size.width + 2 * pad, t.size.height + 2 * pad),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pad),
        )
        drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
    }
}
