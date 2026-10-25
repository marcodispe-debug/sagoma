package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.GuidePlan
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * La piantina della guida interattiva ([GuidePlan]) come immagine PNG, da caricare come una planimetria vera nella lezione
 * «Sopra la tua planimetria»: muri neri, nomi, superfici e misure dei lati come su una piantina, nella lingua scelta. Un pixel è
 * un centimetro. Null se la piattaforma non sa scrivere il PNG.
 */
internal fun guidePlanPng(platform: Platform, measurer: TextMeasurer): ByteArray? = platform.encodePng(guidePlanBitmap(measurer))

/**
 * La piantina della guida come immagine. `overview`: per vederla in piccolo nell'app (il benvenuto della guida, la casa da
 * disegnare), solo muri e nomi grandi, senza misure.
 */
internal fun guidePlanBitmap(measurer: TextMeasurer, overview: Boolean = false): ImageBitmap {
    val w = GuidePlan.width
    val h = GuidePlan.height
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
        drawGuidePlan(measurer, overview)
    }
    return image
}

private val Ink = Color(0xFF1E1E1E)
private val Gray = Color(0xFF5A5A5A)

private fun DrawScope.drawGuidePlan(measurer: TextMeasurer, overview: Boolean) {
    drawRect(Color.White)
    fun px(p: Vec2) = GuidePlan.pixel(p).let { Offset(it.x.toFloat(), it.y.toFloat()) }
    fun path(r: GuidePlan.Room) = Path().apply {
        r.points.forEachIndexed { i, p -> px(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        close()
    }
    // Parapetti di terrazza e balcone: 10 cm tutto intorno; contro la casa li copre il muro, disegnato dopo.
    for (o in GuidePlan.outdoor) drawPath(path(o), Color(0xFF8A8A8A), style = Stroke(20f, join = StrokeJoin.Miter))
    // I muri: ogni stanza contornata da 30 cm verso fuori; dove due stanze sono a 10 cm, il muro resta di 10.
    for (r in GuidePlan.rooms) drawPath(path(r), Ink, style = Stroke(60f, miter = 10f, join = StrokeJoin.Miter))
    for (r in GuidePlan.rooms) drawPath(path(r), Color.White)
    // Il pavimento all'aperto un po' grigio, per distinguerlo dalle stanze.
    for (o in GuidePlan.outdoor) drawPath(path(o), Color(0xFFF1F1F1))

    fun text(s: String, at: Offset, size: Int, color: Color, bold: Boolean = false, degrees: Float = 0f) {
        val layout = measurer.measure(s, TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
        rotate(degrees, at) { drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f)) }
    }
    val letters = "ABCDEFGHIJKL"
    if (overview) {
        for (r in GuidePlan.rooms + GuidePlan.outdoor) {
            val n = r.points.size
            val name = r.name().uppercase()
            // Grande, ma dentro la stanza (i nomi lunghi, come «Camera da letto», un po' più piccoli).
            val room = r.points.maxOf { it.x } - r.points.minOf { it.x }
            val big = if (r.smallName) 54 else 66
            val wide = measurer.measure(name, TextStyle(fontSize = big.sp, fontWeight = FontWeight.Bold)).size.width
            val size = if (wide > room * 0.9) (big * room * 0.9 / wide).toInt() else big
            text(name, px(r.label ?: Vec2(r.points.sumOf { it.x } / n, r.points.sumOf { it.y } / n)), size, Ink, bold = true)
        }
        return
    }
    // Nome, superficie e misura di ogni lato (scritta dentro la stanza, vicino al muro).
    for (r in GuidePlan.rooms + GuidePlan.outdoor) {
        val n = r.points.size
        val label = px(r.label ?: Vec2(r.points.sumOf { it.x } / n, r.points.sumOf { it.y } / n))
        text(r.name().uppercase(), label - Offset(0f, 14f), if (r.smallName) 20 else 24, Ink, bold = true)
        text("${fmt2(Polygon.area(r.points) / 10000)} m²", label + Offset(0f, 16f), 19, Gray)
        for (i in 0 until n) {
            val a = r.points[i]
            val b = r.points[(i + 1) % n]
            // Verso l'interno: le stanze sono girate in senso orario sullo schermo, l'interno è a destra del lato.
            val len = hypot(b.x - a.x, b.y - a.y)
            val inward = Vec2(-(b.y - a.y) / len, (b.x - a.x) / len)
            val side = GuidePlan.side(r, i).toString()
            val s = if (r.letters) "${letters[i]}${letters[(i + 1) % n]} $side" else side
            var deg = (atan2(b.y - a.y, b.x - a.x) * 180 / PI).toFloat()
            if (deg > 90f) deg -= 180f
            if (deg < -90f) deg += 180f
            text(s, px((a + b) / 2.0 + inward * 24.0), 19, Gray, degrees = deg)
        }
    }
    // Stanza storta: lettere agli angoli e diagonale AC tratteggiata, con la misura vicino a C.
    for (r in GuidePlan.rooms.filter { it.letters }) {
        val a = r.points[0]
        val c = r.points[2]
        drawLine(Color(0xFF8A8A8A), px(a), px(c), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
        val l = a.distanceTo(c)
        val at = a + (c - a) * 0.8 + Vec2(-(c.y - a.y), c.x - a.x) * (22.0 / l)
        text("AC ${l.roundToInt()}", px(at), 19, Gray, degrees = (atan2(c.y - a.y, c.x - a.x) * 180 / PI).toFloat())
        // Le lettere appena dentro gli angoli: A in alto a sinistra, poi in senso orario.
        val push = listOf(Vec2(12.0, 18.0), Vec2(-14.0, 18.0), Vec2(-14.0, -16.0), Vec2(12.0, -16.0))
        r.points.forEachIndexed { i, p -> text(letters[i].toString(), px(p + push[i % 4]), 20, Ink, bold = true) }
    }
    val note = measurer.measure(T.corso.notaMisure, TextStyle(color = Gray, fontSize = 18.sp))
    drawText(note, topLeft = Offset(40f, size.height - 20f - note.size.height))
}
