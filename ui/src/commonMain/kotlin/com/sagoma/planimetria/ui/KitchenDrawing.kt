package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.geometry.KitchenGeometry
import com.sagoma.planimetria.geometry.KitchenGeometry.FrontKind
import com.sagoma.planimetria.model.KitchenModules
import com.sagoma.planimetria.model.KitchenModules.Kind
import com.sagoma.planimetria.model.KitchenModules.Placement

private val PlanFill = Color(0xFFFFFFFF)
private val PlanSelectedFill = Color(0xFF1971C2).copy(alpha = 0.12f)

/**
 * Modulo della cucina in pianta, largo `w` e profondo `d` (px), centrato nell'origine, davanti verso +y. Come nei disegni
 * degli architetti: basi piene (con vasca e fuochi dove ci sono), pensili tratteggiati (stanno sopra le basi), colonne con la
 * croce.
 */
internal fun DrawScope.kitchenPlanSymbol(spec: KitchenModules.Spec, w: Float, d: Float, line: Color, selected: Boolean) {
    val sw = 1.2.dp.toPx()
    val stroke = Stroke(sw)
    val l = -w / 2
    val t = -d / 2
    val m = minOf(w, d)
    val fill = if (selected) PlanSelectedFill else PlanFill
    when (spec.kind.placement) {
        Placement.WALL -> {
            if (selected) drawRect(PlanSelectedFill, Offset(l, t), Size(w, d))
            // Anche la cappa solo tratteggiata: sotto ci sono già i fuochi della base.
            drawRect(line, Offset(l, t), Size(w, d), style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))))
        }
        Placement.TALL -> {
            drawRect(fill, Offset(l, t), Size(w, d))
            drawRect(line, Offset(l, t), Size(w, d), style = stroke)
            drawLine(line.copy(alpha = 0.7f), Offset(l, t), Offset(l + w, t + d), sw)
            drawLine(line.copy(alpha = 0.7f), Offset(l + w, t), Offset(l, t + d), sw)
        }
        Placement.BASE -> {
            drawRect(fill, Offset(l, t), Size(w, d))
            drawRect(line, Offset(l, t), Size(w, d), style = stroke)
            when (spec.kind) {
                Kind.SINK -> {
                    val bw = minOf(w * 0.75f, m * 0.95f)
                    val bd = d * 0.62f
                    drawRoundRect(line, Offset(-bw / 2, t + d * 0.18f), Size(bw, bd), androidx.compose.ui.geometry.CornerRadius(m * 0.08f), style = stroke)
                    drawCircle(line, sw * 1.6f, Offset(0f, t + d * 0.1f))
                }
                Kind.OVEN_HOB -> {
                    for (dx in listOf(-0.22f, 0.22f)) for (dy in listOf(-0.2f, 0.2f)) {
                        drawCircle(line, m * 0.15f, Offset(w * dx, d * dy), style = stroke)
                    }
                }
                Kind.CORNER -> {
                    // Il pannello cieco (sotto l'altra base) a sinistra, l'anta a destra.
                    val door = minOf(45f / 90f * w, w / 2)
                    drawLine(line.copy(alpha = 0.6f), Offset(l + w - door, t + d * 0.85f), Offset(l + w, t + d * 0.85f), sw)
                }
                else -> {
                    // Ante o cassetti sul davanti.
                    drawLine(line.copy(alpha = 0.6f), Offset(l, t + d * 0.88f), Offset(l + w, t + d * 0.88f), sw)
                }
            }
        }
    }
}

/**
 * Mobile generato visto dal davanti, nel rettangolo del disegno (scheda del catalogo): i suoi pezzi proiettati, dal retro al
 * davanti, con i colori veri e il bordo sottile; in scala.
 */
internal fun DrawScope.partsFrontView(parts: List<KitchenGeometry.Part>, widthCm: Double, heightCm: Double, padding: Float) {
    val k = minOf((size.width - 2 * padding) / widthCm.toFloat(), (size.height - 2 * padding) / heightCm.toFloat())
    val ox = size.width / 2
    val oy = (size.height + heightCm.toFloat() * k) / 2
    val edge = Color(0xFF6B7178).copy(alpha = 0.5f)
    for (p in parts.sortedBy { it.z1 }) {
        val tl = Offset(ox + p.x0.toFloat() * k, oy - p.y1.toFloat() * k)
        val s = Size(((p.x1 - p.x0) * k).toFloat(), ((p.y1 - p.y0) * k).toFloat())
        drawRect(Color(p.argb), tl, s)
        if (s.width > 3 && s.height > 3) drawRect(edge, tl, s, style = Stroke(0.6.dp.toPx()))
    }
}

/**
 * Modulo visto dal davanti, nel rettangolo del disegno (per la scheda del catalogo): zoccolo, piano, ante, cassetti, forno e
 * maniglie, con le finiture vere, in scala.
 */
internal fun DrawScope.kitchenFrontView(spec: KitchenModules.Spec, widthCm: Double, heightCm: Double, padding: Float) {
    val k = minOf((size.width - 2 * padding) / widthCm.toFloat(), (size.height - 2 * padding) / heightCm.toFloat())
    val ox = size.width / 2
    val oy = (size.height + heightCm.toFloat() * k) / 2 // y del pavimento del modulo
    fun x(cm: Double) = ox + cm.toFloat() * k
    fun y(cm: Double) = oy - cm.toFloat() * k
    fun box(x0: Double, x1: Double, y0: Double, y1: Double, c: Color) = drawRect(c, Offset(x(x0), y(y1)), Size(x(x1) - x(x0), y(y0) - y(y1)))
    val front = Color(spec.front.argb)
    val edge = Color(0xFF6B7178).copy(alpha = 0.55f)
    val steel = Color(0xFFB7BABE)
    val dark = Color(0xFF1C1F22)
    val w = widthCm
    val h = heightCm
    // Struttura, zoccolo e piano.
    box(-w / 2, w / 2, 0.0, h, front)
    if (spec.kind.placement != Placement.WALL) box(-w / 2, w / 2, 0.0, KitchenGeometry.PLINTH, Color(KitchenGeometry.darker(spec.front.argb, 0.3)))
    if (spec.kind.hasTop) box(-w / 2 - 1, w / 2 + 1, h - KitchenGeometry.TOP, h, Color(spec.top.argb))
    for (p in KitchenGeometry.fronts(spec, w, h)) {
        when (p.kind) {
            FrontKind.DOOR, FrontKind.DRAWER, FrontKind.PANEL -> box(p.x0, p.x1, p.y0, p.y1, front)
            FrontKind.HOOD -> box(p.x0, p.x1, p.y0, p.y1, steel)
            FrontKind.OVEN -> {
                box(p.x0, p.x1, p.y0, p.y1, steel)
                box(p.x0 + 4, p.x1 - 4, p.y0 + 4, p.y1 - minOf(9.0, p.height * 0.18) - 3, dark)
            }
            FrontKind.MICROWAVE -> {
                box(p.x0, p.x1, p.y0, p.y1, steel)
                box(p.x0 + 3, p.x0 + p.width * 0.68 - 1, p.y0 + 3, p.y1 - 3, dark)
            }
        }
        drawRect(edge, Offset(x(p.x0), y(p.y1)), Size(x(p.x1) - x(p.x0), y(p.y0) - y(p.y1)), style = Stroke(0.8.dp.toPx()))
        KitchenGeometry.handle(p)?.let { (hx0, hx1, hy) -> box(hx0, hx1, hy - 0.8, hy + 0.8, Color(0xFF7E8286)) }
    }
    drawRect(edge, Offset(x(-w / 2), y(h)), Size(x(w / 2) - x(-w / 2), y(0.0) - y(h)), style = Stroke(0.8.dp.toPx()))
}
