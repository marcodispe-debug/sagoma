package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.Distances
import com.sagoma.planimetria.geometry.MeasureTarget
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Vec2

internal val MeasureColor = Color(0xFFD9480F)

private fun Camera.q(v: Vec2) = Offset(toScreenX(v.x), toScreenY(v.y))

/** Nome dell'oggetto scelto, per la scheda della misura: "Calorifero (Soggiorno)", "Muro 3 (Cucina)"… */
internal fun measureName(plan: FloorPlan, t: MeasureTarget): String = when (t) {
    is MeasureTarget.Wall -> plan.room(t.roomId)?.let { "muro ${t.index + 1} (${it.name})" } ?: "muro"
    is MeasureTarget.Opening -> plan.room(t.roomId)?.let { r -> r.opening(t.openingId)?.let { "${openingLabel(it).lowercase()} (${r.name})" } } ?: "apertura"
    is MeasureTarget.Fixture -> plan.room(t.roomId)?.let { r -> r.fixture(t.fixtureId)?.let { "${it.kind.label.lowercase()} (${r.name})" } } ?: "impianto"
    is MeasureTarget.Column -> "colonna"
    is MeasureTarget.Beam -> "trave"
    is MeasureTarget.Stair -> "scala"
    is MeasureTarget.FreeWall -> "muro singolo"
    is MeasureTarget.Furniture -> plan.furniture(t.furnitureId)?.let { f -> com.sagoma.planimetria.model.FurnitureCatalog.item(f.model)?.label?.lowercase() } ?: "arredo"
}

/** Distanza con un decimale, come la si scrive: "123,4 cm". */
internal fun formatDistance(d: Double): String = com.sagoma.planimetria.geometry.formatDecimal(d, 1) + " cm"

/**
 * Strumento "Distanza": i due oggetti scelti evidenziati e, quando ci sono tutti e due, la quota tra i
 * due punti più vicini con la misura.
 */
internal fun DrawScope.drawMeasure(state: EditorUiState, measurer: TextMeasurer) {
    val m = state.measure ?: return
    val cam = state.camera
    for (t in listOfNotNull(m.first, m.second)) {
        val shape = Distances.shapeOf(state.plan, t, state.levelHeight) ?: continue
        for (poly in shape.polygons) {
            val path = Path().apply { poly.forEachIndexed { i, p -> val o = cam.q(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }
            drawPath(path, MeasureColor.copy(alpha = 0.22f))
            drawPath(path, MeasureColor, style = Stroke(2.dp.toPx()))
        }
        for ((a, b) in shape.segments) drawLine(MeasureColor, cam.q(a), cam.q(b), 4.dp.toPx(), cap = StrokeCap.Round)
        for (p in shape.points) drawCircle(MeasureColor, 7.dp.toPx(), cam.q(p), style = Stroke(2.5.dp.toPx()))
    }
    val a = m.first ?: return
    val b = m.second ?: return
    val r = Distances.between(state.plan, a, b, state.levelHeight) ?: return
    val pa = cam.q(r.a)
    val pb = cam.q(r.b)
    val w = 2.dp.toPx()
    drawLine(MeasureColor, pa, pb, w)
    // Tacche perpendicolari alle due estremità.
    val d = pb - pa
    val len = d.getDistance()
    if (len > 1f) {
        val n = Offset(-d.y / len, d.x / len) * 7.dp.toPx()
        for (p in listOf(pa, pb)) drawLine(MeasureColor, p - n, p + n, w)
    }
    val label = measurer.measure(
        if (r.distance < 0.05) "si toccano" else formatDistance(r.distance),
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White),
    )
    val mid = (pa + pb) / 2f
    val pad = 5.dp.toPx()
    val tl = Offset(mid.x - label.size.width / 2f - pad, mid.y - label.size.height - 10.dp.toPx())
    drawRoundRect(
        MeasureColor, tl, Size(label.size.width + 2 * pad, label.size.height + pad),
        androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
    )
    drawText(label, topLeft = Offset(tl.x + pad, tl.y + pad / 2))
}
