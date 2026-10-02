package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.DimensionDraw
import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.geometry.toRadians
import com.sagoma.planimetria.model.Dimension
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.TextNote
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Colore delle quote manuali e dei testi (blu scuro, come le quote del PDF). */
internal val NoteColor = Color(0xFF1D3557)
private val NoteSelected = Color(0xFF1971C2)

private fun Camera.px(p: Vec2) = Offset(toScreenX(p.x), toScreenY(p.y))

/**
 * Quota manuale come nei disegni tecnici: linee di richiamo dagli estremi (staccate di poco dal punto
 * misurato, poco oltre la linea), linea di quota con i trattini obliqui alle estremità, misura sopra la linea
 * (sempre leggibile, da sinistra a destra o dal basso in alto).
 */
internal fun DrawScope.drawDimension(d: Dimension, cam: Camera, measurer: TextMeasurer, selected: Boolean = false, color: Color = NoteColor) {
    val c = if (selected) NoteSelected else color
    val pa = cam.px(d.a); val pb = cam.px(d.b)
    val la = cam.px(d.lineA); val lb = cam.px(d.lineB)
    val sw = (if (selected) 1.6 else 1.0).dp.toPx()
    // Linee di richiamo.
    val ext = la - pa
    val extLen = ext.getDistance()
    if (extLen > 4.dp.toPx()) {
        val u = ext / extLen
        val gap = 2.dp.toPx(); val over = 3.dp.toPx()
        drawLine(c, pa + u * gap, la + u * over, 0.8.dp.toPx())
        drawLine(c, pb + u * gap, lb + u * over, 0.8.dp.toPx())
    }
    // Linea di quota e trattini a 45°.
    drawLine(c, la, lb, sw)
    val line = lb - la
    val len = line.getDistance()
    if (len < 1f) return
    val dir = line / len
    val tick = 4.dp.toPx()
    val slash = Offset(dir.x - dir.y, dir.y + dir.x) / 1.4142135f * tick
    drawLine(c, la - slash, la + slash, sw * 1.4f)
    drawLine(c, lb - slash, lb + slash, sw * 1.4f)
    // Misura al centro, ruotata lungo la linea ma sempre leggibile.
    var angle = toDegrees(atan2(dir.y.toDouble(), dir.x.toDouble())).toFloat()
    if (angle > 90f) angle -= 180f
    if (angle <= -90f) angle += 180f
    val text = measurer.measure(d.label, TextStyle(fontSize = 11.sp, color = c, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
    val mid = (la + lb) / 2f
    rotate(angle, pivot = mid) {
        drawText(text, topLeft = Offset(mid.x - text.size.width / 2f, mid.y - text.size.height - 1.dp.toPx()))
    }
    if (selected) {
        for (p in listOf(pa, pb)) { drawCircle(Color.White, 7.dp.toPx(), p); drawCircle(NoteSelected, 5.dp.toPx(), p) }
        // Maniglia della linea a un quarto (al centro c'è la misura).
        val grip = la + (lb - la) * 0.25f
        drawCircle(Color.White, 6.dp.toPx(), grip)
        drawCircle(NoteSelected, 4.dp.toPx(), grip, style = Stroke(2.dp.toPx()))
    }
}

/** Anteprima dello strumento "Quota": il primo punto e la quota fino al puntatore. */
internal fun DrawScope.drawDimensionDraft(draw: DimensionDraw, cam: Camera, measurer: TextMeasurer) {
    val first = draw.first
    val cursor = draw.cursor
    if (first == null) {
        cursor?.let { drawCircle(NoteSelected, 4.dp.toPx(), cam.px(it)) }
        return
    }
    drawCircle(NoteSelected, 4.dp.toPx(), cam.px(first))
    if (cursor != null && first.distanceTo(cursor) >= 1.0) drawDimension(Dimension(0, first, cursor, offset = 0.0), cam, measurer, color = NoteSelected)
}

/** Testo misurato alla scala della vista (alto `size` cm in pianta, almeno leggibile). */
internal fun measureNote(t: TextNote, cam: Camera, measurer: TextMeasurer, density: Density, selected: Boolean = false): TextLayoutResult {
    val px = (t.size * cam.scale).toFloat().coerceIn(7f, 400f)
    val fs = with(density) { px.toSp() }
    return measurer.measure(t.text, TextStyle(fontSize = fs, color = if (selected) NoteSelected else NoteColor, fontWeight = FontWeight.Medium))
}

/** Il punto (schermo) è sul testo: lo si riporta nel sistema del testo (ruotato) e si guarda il riquadro. */
internal fun noteContains(t: TextNote, layout: TextLayoutResult, cam: Camera, p: Offset, pad: Float): Boolean {
    val c = cam.px(t.at)
    val a = -toRadians(t.rotation)
    val d = p - c
    val x = (d.x * cos(a) - d.y * sin(a)).toFloat()
    val y = (d.x * sin(a) + d.y * cos(a)).toFloat()
    return abs(x) <= layout.size.width / 2f + pad && abs(y) <= layout.size.height / 2f + pad
}

/**
 * Testo sulla pianta, centrato nel suo punto e ruotato; con la freccia di richiamo fino a `arrowTo`
 * (la freccia passa sotto il testo, che ha uno sfondo per restare leggibile).
 */
internal fun DrawScope.drawTextNote(t: TextNote, cam: Camera, measurer: TextMeasurer, background: Color, selected: Boolean = false) {
    val c = if (selected) NoteSelected else NoteColor
    val layout = measureNote(t, cam, measurer, this, selected)
    val center = cam.px(t.at)
    t.arrowTo?.let { tip ->
        val e = cam.px(tip)
        val v = e - center
        val l = v.getDistance()
        if (l > 1f) {
            val u = v / l
            drawLine(c, center, e, 1.dp.toPx())
            val head = 8.dp.toPx()
            val side = Offset(-u.y, u.x) * (head * 0.4f)
            val path = Path().apply { moveTo(e.x, e.y); lineTo(e.x - u.x * head + side.x, e.y - u.y * head + side.y); lineTo(e.x - u.x * head - side.x, e.y - u.y * head - side.y); close() }
            drawPath(path, c)
        }
        if (selected) { drawCircle(Color.White, 7.dp.toPx(), e); drawCircle(NoteSelected, 5.dp.toPx(), e) }
    }
    rotate(t.rotation.toFloat(), pivot = center) {
        val w = layout.size.width.toFloat(); val h = layout.size.height.toFloat()
        val pad = 2.dp.toPx()
        drawRect(background.copy(alpha = 0.85f), Offset(center.x - w / 2 - pad, center.y - h / 2 - pad), Size(w + 2 * pad, h + 2 * pad))
        if (selected) drawRect(NoteSelected, Offset(center.x - w / 2 - pad, center.y - h / 2 - pad), Size(w + 2 * pad, h + 2 * pad), style = Stroke(1.2.dp.toPx()))
        drawText(layout, topLeft = Offset(center.x - w / 2, center.y - h / 2))
    }
}

/** Quote e testi del piano (quelli dei livelli visibili). */
internal fun DrawScope.drawNotes(
    plan: FloorPlan, cam: Camera, measurer: TextMeasurer, background: Color,
    showDimensions: Boolean, showTexts: Boolean, selectedDimension: Long? = null, selectedText: Long? = null,
) {
    if (showDimensions) for (d in plan.dimensions) drawDimension(d, cam, measurer, d.id == selectedDimension)
    if (showTexts) for (t in plan.annotations) drawTextNote(t, cam, measurer, background, t.id == selectedText)
}
