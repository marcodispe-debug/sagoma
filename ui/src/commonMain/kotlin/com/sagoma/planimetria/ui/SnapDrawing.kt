package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.geometry.SnapGuide
import com.sagoma.planimetria.geometry.SnapKind

/** Colore delle guide di aggancio (magenta, come nei programmi CAD: si distingue da tutto il resto). */
private val SnapColor = Color(0xFFD6247F)

/**
 * Guide dell'aggancio durante un trascinamento: linea tratteggiata dal punto di riferimento (allineamenti),
 * simbolo sul punto agganciato (quadrato = estremità, triangolo = punto medio, cerchio = centro, croce =
 * allineamento) e il nome dell'aggancio in un'etichetta.
 */
internal fun DrawScope.drawSnapGuides(guides: List<SnapGuide>, cam: Camera, measurer: TextMeasurer) {
    if (guides.isEmpty()) return
    val stroke = 1.2.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    fun o(x: Double, y: Double) = Offset(cam.toScreenX(x), cam.toScreenY(y))
    for (g in guides) {
        val p = o(g.point.x, g.point.y)
        g.from?.let { f ->
            val a = o(f.x, f.y)
            // La linea prosegue un poco oltre, come le guide di inferenza dei CAD.
            val dir = (p - a).let { d -> val l = d.getDistance(); if (l < 1f) Offset.Zero else d / l }
            drawLine(SnapColor, a - dir * 12.dp.toPx(), p + dir * 30.dp.toPx(), strokeWidth = stroke, pathEffect = dash)
            drawCircle(SnapColor, 2.5.dp.toPx(), a)
        }
    }
    // Simbolo e nome sul punto (una volta sola anche se due guide lo condividono).
    val g = guides.first()
    val p = o(g.point.x, g.point.y)
    val r = 6.dp.toPx()
    when (g.kind) {
        SnapKind.Endpoint -> drawRect(SnapColor, p - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(stroke * 1.6f))
        SnapKind.Midpoint -> {
            val path = Path().apply { moveTo(p.x, p.y - r * 1.15f); lineTo(p.x + r, p.y + r * 0.7f); lineTo(p.x - r, p.y + r * 0.7f); close() }
            drawPath(path, SnapColor, style = Stroke(stroke * 1.6f))
        }
        SnapKind.Center -> drawCircle(SnapColor, r, p, style = Stroke(stroke * 1.6f))
        else -> {
            drawLine(SnapColor, p - Offset(r, r), p + Offset(r, r), strokeWidth = stroke * 1.6f)
            drawLine(SnapColor, p - Offset(r, -r), p + Offset(r, -r), strokeWidth = stroke * 1.6f)
        }
    }
    val label = measurer.measure(g.kind.label, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White))
    val pad = 4.dp.toPx()
    val topLeft = p + Offset(10.dp.toPx(), -10.dp.toPx() - label.size.height - pad)
    drawRoundRect(SnapColor, topLeft - Offset(pad, pad / 2), Size(label.size.width + 2 * pad, label.size.height + pad), CornerRadius(4.dp.toPx()))
    drawText(label, topLeft = topLeft)
}
