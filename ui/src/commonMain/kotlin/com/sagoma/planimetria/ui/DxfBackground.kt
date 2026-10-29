package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.dxf.DxfDrawing
import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.model.Vec2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Immagine della pianta DXF per lo sfondo: `origin` è il suo angolo in alto a sinistra sulla pianta (cm). */
internal class DxfRaster(val image: ImageBitmap, val origin: Vec2, val cmPerPx: Double)

/** Margine attorno al disegno (px). */
private const val PAD = 16

/** Mezzo centimetro per pixel al massimo di dettaglio: le linee di un disegno piccolo non diventano un'immagine enorme. */
private const val MIN_CM_PER_PX = 0.5

private val DxfInk = Color(0xFF1E1E1E)
private val DxfFill = Color(0xFF8C8C8C)

/**
 * La pianta DXF disegnata in un'immagine trasparente (lato lungo al massimo `maxSide` px), linee scure e scritte, già in scala:
 * un pixel è [DxfRaster.cmPerPx] cm. Le linee lontanissime dal disegno (fuori dal riquadro di [DxfDrawing]) restano fuori.
 */
internal fun dxfRaster(d: DxfDrawing, measurer: TextMeasurer, maxSide: Int): DxfRaster {
    val inner = (maxSide - 2 * PAD).coerceAtLeast(1)
    val size = max(d.width, d.height)
    val cmPerPx = if (size > 0) max(size / inner, MIN_CM_PER_PX) else 1.0
    val w = (ceil(d.width / cmPerPx).toInt() + 2 * PAD).coerceIn(1, maxSide)
    val h = (ceil(d.height / cmPerPx).toInt() + 2 * PAD).coerceIn(1, maxSide)
    val ox = d.minX - PAD * cmPerPx
    val oy = d.minY - PAD * cmPerPx
    fun x(cm: Double) = ((cm - ox) / cmPerPx).toFloat()
    fun y(cm: Double) = ((cm - oy) / cmPerPx).toFloat()
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
        val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val box = Bounds(ox, oy, ox + w * cmPerPx, oy + h * cmPerPx)
        fun outside(p: DoubleArray): Boolean {
            var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
            for (k in 0 until p.size / 2) {
                x0 = min(x0, p[2 * k]); x1 = max(x1, p[2 * k]); y0 = min(y0, p[2 * k + 1]); y1 = max(y1, p[2 * k + 1])
            }
            return x1 < box.minX || x0 > box.maxX || y1 < box.minY || y0 > box.maxY
        }
        // Muri campiti: grigi, sotto alle linee. Uno per uno (due pezzi girati al contrario insieme si annullerebbero); i contorni
        // dello stesso pieno a pari e dispari, così i buchi restano vuoti.
        for (loops in d.fills) {
            if (loops.all { it.size < 6 || outside(it) }) continue
            val fill = Path().apply { fillType = PathFillType.EvenOdd }
            for (p in loops) {
                if (p.size < 6) continue
                fill.moveTo(x(p[0]), y(p[1]))
                for (k in 1 until p.size / 2) fill.lineTo(x(p[2 * k]), y(p[2 * k + 1]))
                fill.close()
            }
            drawPath(fill, DxfFill)
        }
        // Tante spezzate in un percorso solo (più veloce), a gruppi per non farlo troppo grande.
        var path = Path()
        var count = 0
        for (p in d.paths) {
            if (outside(p)) continue
            path.moveTo(x(p[0]), y(p[1]))
            for (k in 1 until p.size / 2) path.lineTo(x(p[2 * k]), y(p[2 * k + 1]))
            count += p.size / 2
            if (count > 20_000) {
                drawPath(path, DxfInk, style = stroke)
                path = Path()
                count = 0
            }
        }
        if (count > 0) drawPath(path, DxfInk, style = stroke)
        for (t in d.texts) {
            val px = t.height / cmPerPx
            if (px < 4 || t.at.x !in box.minX..box.maxX || t.at.y !in box.minY..box.maxY) continue
            // L'altezza del DXF è quella delle maiuscole: il carattere è un po' più grande.
            val style = TextStyle(color = DxfInk, fontSize = (min(px, 1000.0) / 0.72).sp)
            val layouts = t.lines.map { measurer.measure(it, style) }
            val total = layouts.sumOf { it.size.height }.toFloat()
            val first = layouts.firstOrNull() ?: continue
            val anchor = Offset(x(t.at.x), y(t.at.y))
            val top = when (t.valign) {
                0 -> -first.firstBaseline
                1 -> -total
                2 -> -total / 2
                else -> 0f
            }
            rotate(t.rotation.toFloat(), pivot = anchor) {
                var dy = top
                for (l in layouts) {
                    val dx = when (t.align) { 1 -> -l.size.width / 2f; 2 -> -l.size.width.toFloat(); else -> 0f }
                    drawText(l, topLeft = anchor + Offset(dx, dy))
                    dy += l.size.height
                }
            }
        }
    }
    return DxfRaster(image, Vec2(ox, oy), cmPerPx)
}

/**
 * La pianta DXF va lasciata dov'è (stesse coordinate della pianta): quando ci sta sopra per buona parte, come la pianta esportata
 * da sagoma e rimandata dal professionista. Altrimenti (coordinate lontane, pianta ancora vuota) si mette sopra e si sposta.
 */
internal fun dxfOverPlan(d: DxfDrawing, plan: Bounds?): Boolean {
    if (plan == null || d.width <= 0 || d.height <= 0) return false
    val w = min(d.maxX, plan.maxX) - max(d.minX, plan.minX)
    val h = min(d.maxY, plan.maxY) - max(d.minY, plan.minY)
    return w > 0.5 * min(d.width, plan.width) && h > 0.5 * min(d.height, plan.height)
}
