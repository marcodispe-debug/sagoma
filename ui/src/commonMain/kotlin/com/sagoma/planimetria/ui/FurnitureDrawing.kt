package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import kotlin.math.roundToInt
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.geometry.Furnishings
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.FurnitureSymbol

private val FurnitureFill = Color(0xFFFFFFFF)
private val FurnitureLine = Color(0xFF6B7178)
private val FurnitureSelected = Color(0xFF1971C2)

/** Arredi del piano: sotto muri e strutture, i tappeti per primi. */
internal fun DrawScope.drawFurniture(items: List<Furniture>, cam: Camera, selectedId: Long?) {
    for (f in items.sortedBy { if (it.height < 3) 0 else 1 }) {
        val sel = f.id == selectedId
        val symbol = FurnitureCatalog.item(f.model)?.symbolKind ?: FurnitureSymbol.Cabinet
        val c = Offset(cam.toScreenX(f.center.x), cam.toScreenY(f.center.y))
        val w = (f.width * cam.scale).toFloat()
        val d = (f.depth * cam.scale).toFloat()
        val top = FurnitureAssets.top(f.model)
        translate(c.x, c.y) {
            rotate(f.rotation.toFloat(), Offset.Zero) {
            // Specchiato: disegno e vista dall'alto ribaltati sinistra ↔ destra.
            scale(if (f.mirrored) -1f else 1f, 1f, Offset.Zero) {
                if (top != null && w >= 2f && d >= 2f) {
                    // Vista dall'alto del modello, stirata sull'ingombro scelto, con un contorno sottile.
                    // I tappeti tondi si ritagliano a ovale.
                    val round = FurnitureCatalog.rugOf(f.model)?.second == true
                    fun image() = drawImage(
                        top,
                        dstOffset = IntOffset((-w / 2).roundToInt(), (-d / 2).roundToInt()),
                        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), d.roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.Medium,
                    )
                    if (round) {
                        val oval = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(-w / 2, -d / 2, w / 2, d / 2)) }
                        clipPath(oval) { image() }
                    } else image()
                    val color = if (sel) FurnitureSelected else FurnitureLine.copy(alpha = 0.45f)
                    val stroke = Stroke((if (sel) 1.6 else 0.8).dp.toPx())
                    if (round) {
                        if (sel) drawOval(FurnitureSelected.copy(alpha = 0.14f), Offset(-w / 2, -d / 2), Size(w, d))
                        drawOval(color, Offset(-w / 2, -d / 2), Size(w, d), style = stroke)
                    } else {
                        if (sel) drawRect(FurnitureSelected.copy(alpha = 0.14f), Offset(-w / 2, -d / 2), Size(w, d))
                        drawRect(color, Offset(-w / 2, -d / 2), Size(w, d), style = stroke)
                    }
                } else {
                    furnitureSymbol(symbol, w, d, if (sel) FurnitureSelected else FurnitureLine, sel)
                }
            }
            }
        }
        if (sel) {
            // Maniglia di rotazione davanti al mobile, legata al centro da una linea sottile.
            val h = Furnishings.handle(f)
            val ho = Offset(cam.toScreenX(h.x), cam.toScreenY(h.y))
            drawLine(FurnitureSelected.copy(alpha = 0.6f), c, ho, 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            drawCircle(Color.White, 10.dp.toPx(), ho)
            drawCircle(FurnitureSelected, 8.dp.toPx(), ho)
            drawCircle(Color.White, 3.dp.toPx(), ho)
        }
    }
}

/**
 * Simbolo in pianta di un arredo largo `w` e profondo `d` (px), centrato nell'origine, con il davanti
 * verso +y (il basso). Linee sottili grigie, come nei disegni degli architetti.
 */
internal fun DrawScope.furnitureSymbol(s: FurnitureSymbol, w: Float, d: Float, line: Color, selected: Boolean = false) {
    val sw = 1.2.dp.toPx()
    val stroke = Stroke(sw)
    val l = -w / 2
    val t = -d / 2
    val fill = if (selected) FurnitureSelected.copy(alpha = 0.12f) else FurnitureFill
    val m = minOf(w, d)
    fun rect(x: Float, y: Float, rw: Float, rh: Float, r: Float = 0f, filled: Boolean = false, color: Color = line) {
        if (rw <= 0 || rh <= 0) return
        if (filled) drawRoundRect(fill, Offset(x, y), Size(rw, rh), CornerRadius(r))
        drawRoundRect(color, Offset(x, y), Size(rw, rh), CornerRadius(r), style = stroke)
    }
    fun oval(cx: Float, cy: Float, rw: Float, rh: Float, filled: Boolean = false) {
        if (filled) drawOval(fill, Offset(cx - rw / 2, cy - rh / 2), Size(rw, rh))
        drawOval(line, Offset(cx - rw / 2, cy - rh / 2), Size(rw, rh), style = stroke)
    }
    when (s) {
        FurnitureSymbol.Bed -> {
            rect(l, t, w, d, m * 0.03f, filled = true)
            rect(l, t, w, d * 0.07f) // testiera
            val pw = if (w > d * 0.6f) (w - m * 0.15f) / 2 else w - m * 0.15f
            val n = if (w > d * 0.6f) 2 else 1
            for (k in 0 until n) rect(l + m * 0.05f + k * (pw + m * 0.05f), t + d * 0.1f, pw, d * 0.13f, m * 0.05f)
            // Risvolto del piumone.
            drawLine(line, Offset(l, t + d * 0.32f), Offset(l + w, t + d * 0.32f), sw)
            drawLine(line, Offset(l, t + d * 0.32f), Offset(l + w * 0.25f, t + d * 0.40f), sw)
        }
        FurnitureSymbol.Sofa, FurnitureSymbol.Armchair -> {
            rect(l, t, w, d, m * 0.12f, filled = true)
            val back = minOf(d * 0.25f, m * 0.3f)
            val arm = minOf(w * 0.14f, m * 0.25f)
            rect(l, t, w, back, m * 0.08f) // schienale
            rect(l, t, arm, d, m * 0.08f)
            rect(l + w - arm, t, arm, d, m * 0.08f)
            if (s == FurnitureSymbol.Sofa && w > d * 1.3f) {
                val seats = if (w > 180 * (d / 90)) 3 else 2
                val sw2 = (w - 2 * arm) / seats
                for (k in 1 until seats) drawLine(line, Offset(l + arm + k * sw2, t + back), Offset(l + arm + k * sw2, t + d), sw)
            }
        }
        FurnitureSymbol.CornerSofa -> {
            // A L: schienali lungo il retro e un fianco, seduta larga quanto un divano.
            val seat = m * 0.4f
            val back = m * 0.12f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(l, t); lineTo(l + w, t); lineTo(l + w, t + seat); lineTo(l + seat, t + seat); lineTo(l + seat, t + d); lineTo(l, t + d); close()
            }
            drawPath(path, fill)
            drawPath(path, line, style = stroke)
            rect(l, t, w, back, m * 0.03f)
            rect(l, t, back, d, m * 0.03f)
            drawLine(line, Offset(l + seat, t + back), Offset(l + seat, t + seat), sw)
        }
        FurnitureSymbol.Table, FurnitureSymbol.Desk -> {
            rect(l, t, w, d, m * 0.04f, filled = true)
            rect(l + m * 0.06f, t + m * 0.06f, w - m * 0.12f, d - m * 0.12f, m * 0.03f, color = line.copy(alpha = 0.4f))
        }
        FurnitureSymbol.RoundTable -> { oval(0f, 0f, w, d, filled = true) }
        FurnitureSymbol.Chair -> {
            rect(l, t + d * 0.1f, w, d * 0.9f, m * 0.15f, filled = true)
            rect(l, t, w, d * 0.2f, m * 0.1f, filled = true) // schienale
        }
        FurnitureSymbol.Cabinet -> {
            rect(l, t, w, d, 0f, filled = true)
            // Ante sul davanti.
            drawLine(line, Offset(l, t + d * 0.85f), Offset(l + w, t + d * 0.85f), sw)
            if (w > d) drawLine(line, Offset(0f, t + d * 0.85f), Offset(0f, t + d), sw)
        }
        FurnitureSymbol.Appliance -> {
            rect(l, t, w, d, m * 0.05f, filled = true)
            oval(0f, d * 0.05f, m * 0.6f, m * 0.6f)
        }
        FurnitureSymbol.Hob -> {
            rect(l, t, w, d, 0f, filled = true)
            for (dx in listOf(-0.22f, 0.22f)) for (dy in listOf(-0.22f, 0.22f)) oval(w * dx, d * dy, m * 0.3f, m * 0.3f)
        }
        FurnitureSymbol.Sink -> {
            rect(l, t, w, d, m * 0.06f, filled = true)
            oval(0f, d * 0.08f, w * 0.6f, d * 0.6f)
            drawCircle(line, sw * 1.5f, Offset(0f, t + d * 0.14f))
        }
        FurnitureSymbol.Toilet -> {
            rect(l, t, w, d * 0.28f, m * 0.08f, filled = true) // cassetta
            oval(0f, t + d * 0.62f, w * 0.9f, d * 0.72f, filled = true)
        }
        FurnitureSymbol.Tub -> {
            rect(l, t, w, d, m * 0.12f, filled = true)
            rect(l + m * 0.08f, t + m * 0.08f, w - m * 0.16f, d - m * 0.16f, m * 0.3f)
            drawCircle(line, sw * 1.5f, Offset(l + w * 0.12f, 0f))
        }
        FurnitureSymbol.Shower -> {
            rect(l, t, w, d, 0f, filled = true)
            drawLine(line, Offset(l, t), Offset(l + w, t + d), sw)
            drawLine(line, Offset(l + w, t), Offset(l, t + d), sw)
            drawCircle(line, m * 0.06f, Offset.Zero, style = stroke)
        }
        FurnitureSymbol.Rug -> {
            rect(l, t, w, d, m * 0.04f, color = line.copy(alpha = 0.7f))
            rect(l + m * 0.06f, t + m * 0.06f, w - m * 0.12f, d - m * 0.12f, m * 0.02f, color = line.copy(alpha = 0.35f))
        }
        FurnitureSymbol.RoundRug -> {
            drawOval(line.copy(alpha = 0.7f), Offset(l, t), Size(w, d), style = stroke)
            drawOval(line.copy(alpha = 0.35f), Offset(l + m * 0.08f, t + m * 0.08f), Size(w - m * 0.16f, d - m * 0.16f), style = stroke)
        }
        FurnitureSymbol.Plant -> {
            oval(0f, 0f, w, d, filled = true)
            for (k in 0 until 6) {
                val a = com.sagoma.planimetria.geometry.toRadians(k * 60.0)
                drawLine(line, Offset.Zero, Offset((kotlin.math.cos(a) * w * 0.4).toFloat(), (kotlin.math.sin(a) * d * 0.4).toFloat()), sw)
            }
        }
        FurnitureSymbol.Lamp -> {
            oval(0f, 0f, w, d, filled = true)
            drawLine(line, Offset(l + w * 0.2f, 0f), Offset(l + w * 0.8f, 0f), sw)
            drawLine(line, Offset(0f, t + d * 0.2f), Offset(0f, t + d * 0.8f), sw)
        }
        FurnitureSymbol.Tv -> {
            rect(l, t, w, d, 0f, filled = true)
            drawLine(line, Offset(l + w * 0.1f, t + d), Offset(l + w * 0.9f, t + d), sw * 2.5f)
        }
    }
}
