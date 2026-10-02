package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPattern
import com.sagoma.planimetria.model.LampModel
import com.sagoma.planimetria.model.RadiatorModel
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.StairStructure
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WindowModel
import kotlin.math.min

/*
 * Anteprime dei modelli nelle schede di scelta: disegni frontali (porte, finestre, termosifoni, lampadari),
 * di lato (strutture e ringhiere delle scale) o in pianta (tipi di scala, pavimenti).
 */

private val Ink = Color(0xFF3A3F44)
private val GlassBlue = Color(0xFFBFDCEB)
private val WallBack = Color(0xFFE9E6E0)
private val Metal = Color(0xFF4A4E53)
private val ShutterGreen = Color(0xFF46704F)

/** Stesso colore, più scuro (`k` < 1) o più chiaro (`k` > 1). */
internal fun Color.shade(k: Float): Color =
    if (k <= 1f) Color(red * k, green * k, blue * k, alpha)
    else Color(red + (1 - red) * (k - 1), green + (1 - green) * (k - 1), blue + (1 - blue) * (k - 1), alpha)

private fun DrawScope.px(fx: Float, fy: Float) = Offset(size.width * fx, size.height * fy)
private fun DrawScope.box(l: Float, t: Float, r: Float, b: Float, color: Color, stroke: Float? = null) {
    val tl = px(l, t)
    val sz = Size(size.width * (r - l), size.height * (b - t))
    if (stroke == null) drawRect(color, tl, sz) else drawRect(color, tl, sz, style = Stroke(stroke))
}

// ---------- Porte ----------

internal fun DrawScope.doorPreview(model: DoorModel, color: Color) {
    box(0.08f, 0.04f, 0.92f, 0.98f, WallBack)
    // Stipite e anta.
    box(0.26f, 0.06f, 0.74f, 0.98f, color.shade(0.8f))
    val l = 0.3f
    val r = 0.7f
    val t = 0.1f
    val b = 0.98f
    box(l, t, r, b, color)
    val line = color.shade(0.72f)
    val thin = 1.2f * density
    when (model) {
        DoorModel.Flush -> Unit
        DoorModel.Panels -> {
            box(l + 0.05f, t + 0.06f, r - 0.05f, 0.5f, line, thin)
            box(l + 0.05f, 0.56f, r - 0.05f, b - 0.06f, line, thin)
        }
        DoorModel.Glazed -> {
            box(l + 0.06f, t + 0.07f, r - 0.06f, 0.58f, GlassBlue)
            box(l + 0.06f, t + 0.07f, r - 0.06f, 0.58f, line, thin)
            box(l + 0.06f, 0.66f, r - 0.06f, b - 0.07f, line, thin)
        }
        DoorModel.Planks -> {
            var x = l + (r - l) / 5
            while (x < r - 0.01f) { drawLine(line, px(x, t), px(x, b), thin); x += (r - l) / 5 }
        }
        DoorModel.Modern -> {
            for (y in listOf(0.3f, 0.55f, 0.8f)) drawLine(line, px(l + 0.03f, y), px(r - 0.03f, y), thin)
            drawLine(Metal, px(r - 0.07f, 0.35f), px(r - 0.07f, 0.75f), 2.5f * density, cap = StrokeCap.Round)
        }
    }
    if (model != DoorModel.Modern) drawCircle(Metal, 2.2f * density, px(r - 0.06f, 0.58f))
}

// ---------- Finestre ----------

internal fun DrawScope.windowPreview(model: WindowModel, shading: Shading, color: Color) {
    box(0.0f, 0.0f, 1f, 1f, WallBack)
    val l = 0.3f
    val r = 0.7f
    val t = 0.2f
    val b = 0.86f
    // Persiane aperte ai lati.
    if (shading == Shading.Shutters) {
        val sc = if (color.red > 0.9f && color.green > 0.9f) ShutterGreen else color
        for ((a, z) in listOf(0.1f to l, r to 0.9f)) {
            box(a, t, z, b, sc)
            var y = t + 0.05f
            while (y < b) { drawLine(sc.shade(0.7f), px(a, y), px(z, y), 1f * density); y += 0.06f }
        }
    }
    val frame = when (model) {
        WindowModel.Minimal -> 0.025f
        else -> 0.05f
    }
    val fc = if (model == WindowModel.Minimal && color.red > 0.9f) Color(0xFF383B3F) else color
    box(l, t, r, b, fc)
    box(l + frame, t + frame * 1.3f, r - frame, b - frame * 1.3f, GlassBlue)
    // Montante centrale (due ante).
    drawLine(fc, px(0.5f, t), px(0.5f, b), size.width * frame * 0.8f)
    if (model == WindowModel.English) {
        for (x in listOf(l + (0.5f - l) / 2, 0.5f + (r - 0.5f) / 2)) drawLine(fc, px(x, t), px(x, b), 1.2f * density)
        for (y in listOf(t + (b - t) / 3, t + 2 * (b - t) / 3)) drawLine(fc, px(l, y), px(r, y), 1.2f * density)
    }
    if (shading == Shading.RollerShutter) {
        box(l - 0.03f, t - 0.1f, r + 0.03f, t, Color(0xFF8D9095))
        box(l, t, r, t + (b - t) * 0.4f, Color(0xFFB9BBBE))
        var y = t + 0.04f
        while (y < t + (b - t) * 0.4f) { drawLine(Color(0xFF8D9095), px(l, y), px(r, y), 1f * density); y += 0.05f }
    }
}

// ---------- Scale ----------

/** Il tipo di scala visto in pianta: gradini e linea di salita. */
internal fun DrawScope.stairKindPreview(kind: StairKind) {
    val s = Stair(0, kind, Vec2.Zero, risers = 13)
    val layout = Stairs.layout(s, 230.0)
    val bb = layout.bounds
    val k = min(size.width * 0.86f / bb.width.toFloat(), size.height * 0.86f / bb.height.toFloat())
    fun o(v: Vec2) = Offset(size.width / 2 + ((v.x - bb.center.x) * k).toFloat(), size.height / 2 + ((v.y - bb.center.y) * k).toFloat())
    for (st in layout.steps) {
        val p = Path().apply { st.polygon.forEachIndexed { i, v -> val q = o(v); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }
        drawPath(p, Color(0xFFF3EEE6))
        drawPath(p, Ink, style = Stroke(0.8f * density))
    }
    val line = Path().apply { layout.path.forEachIndexed { i, v -> val q = o(v); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) } }
    drawPath(line, Color(0xFF1971C2), style = Stroke(1.4f * density))
    drawCircle(Color(0xFF1971C2), 2.2f * density, o(layout.path.first()))
}

/** Profilo di cinque gradini visti di lato, nel materiale `color`. */
private fun DrawScope.sideSteps(color: Color, solid: Boolean) {
    val n = 5
    val x0 = 0.12f
    val w = 0.15f
    val h = 0.15f
    val floor = 0.92f
    if (solid) {
        val p = Path().apply {
            moveTo(px(x0, floor).x, px(x0, floor).y)
            for (i in 0 until n) {
                val x = x0 + i * w
                val y = floor - (i + 1) * h
                lineTo(px(x, y).x, px(x, y).y)
                lineTo(px(x + w, y).x, px(x + w, y).y)
            }
            lineTo(px(x0 + n * w, floor).x, px(x0 + n * w, floor).y)
            close()
        }
        drawPath(p, Color(0xFFE6E3DD))
        drawPath(p, Ink, style = Stroke(0.8f * density))
    }
    for (i in 0 until n) {
        val x = x0 + i * w
        val y = floor - (i + 1) * h
        box(x - 0.01f, y, x + w, y + 0.05f, color)
    }
}

internal fun DrawScope.structurePreview(structure: StairStructure, color: Color) {
    when (structure) {
        StairStructure.Masonry -> sideSteps(color, solid = true)
        StairStructure.Floating -> {
            box(0.05f, 0.05f, 0.95f, 0.1f, WallBack)
            box(0.05f, 0.1f, 0.95f, 0.95f, WallBack)
            sideSteps(color, solid = false)
        }
        StairStructure.Stringers -> {
            sideSteps(color, solid = false)
            drawLine(Metal, px(0.1f, 0.96f), px(0.88f, 0.2f), 4f * density)
        }
    }
}

internal fun DrawScope.railingPreview(railing: StairRailing, color: Color) {
    sideSteps(color, solid = true)
    val top = listOf(px(0.18f, 0.5f), px(0.8f, 0.05f))
    when (railing) {
        StairRailing.None -> Unit
        StairRailing.Metal -> {
            for (i in 0 until 5) {
                val x = 0.195f + i * 0.15f
                val base = 0.92f - (i + 1) * 0.15f
                drawLine(Metal, px(x, base), Offset(px(x, 0f).x, top[0].y + (top[1].y - top[0].y) * (i / 4f)), 1.2f * density)
            }
            drawLine(Metal, top[0], top[1], 2.4f * density, cap = StrokeCap.Round)
        }
        StairRailing.Glass -> {
            val p = Path().apply {
                moveTo(px(0.16f, 0.72f).x, px(0.16f, 0.72f).y)
                lineTo(top[0].x, top[0].y + 4 * density)
                lineTo(top[1].x, top[1].y + 4 * density)
                lineTo(px(0.82f, 0.16f).x, px(0.82f, 0.16f).y)
                close()
            }
            drawPath(p, GlassBlue.copy(alpha = 0.8f))
            drawLine(Metal, top[0], top[1], 2f * density, cap = StrokeCap.Round)
        }
        StairRailing.Wood -> {
            val wood = Color(0xFF8A5E3B)
            for (i in 0 until 5) {
                val x = 0.195f + i * 0.15f
                val base = 0.92f - (i + 1) * 0.15f
                drawLine(wood, px(x, base), Offset(px(x, 0f).x, top[0].y + (top[1].y - top[0].y) * (i / 4f)), 2.6f * density)
            }
            drawLine(wood, top[0], top[1], 4f * density, cap = StrokeCap.Round)
        }
    }
}

/** Ringhiera sul bordo del vano scala, vista di fronte: il pavimento, il vuoto dietro e la ringhiera. */
internal fun DrawScope.wellRailingPreview(railing: StairRailing) {
    box(0f, 0f, 1f, 1f, WallBack)
    // Vuoto della scala (più scuro) e bordo del solaio.
    box(0.1f, 0.62f, 0.9f, 1f, Color(0xFFBDB8AF))
    box(0f, 0.86f, 1f, 1f, Color(0xFFC8A276))
    val l = 0.14f
    val r = 0.86f
    val top = 0.24f
    val base = 0.86f
    when (railing) {
        StairRailing.None -> Unit
        StairRailing.Metal -> {
            var x = l
            while (x <= r + 0.001f) { drawLine(Metal, px(x, base), px(x, top), 1.1f * density); x += 0.06f }
            drawLine(Metal, px(l, top), px(r, top), 2.4f * density, cap = StrokeCap.Round)
        }
        StairRailing.Glass -> {
            box(l, top + 0.04f, r, base, GlassBlue.copy(alpha = 0.85f))
            drawLine(Metal, px(l, top), px(r, top), 2.4f * density, cap = StrokeCap.Round)
        }
        StairRailing.Wood -> {
            val wood = Color(0xFF8A5E3B)
            var x = l
            while (x <= r + 0.001f) { drawLine(wood, px(x, base), px(x, top), 2.6f * density); x += 0.1f }
            drawLine(wood, px(l, top), px(r, top), 4f * density, cap = StrokeCap.Round)
        }
    }
}

/** Colonna vista un po' dall'alto: rotonda (cilindro) o quadrata (parallelepipedo). */
internal fun DrawScope.columnPreview(shape: com.sagoma.planimetria.model.ColumnShape) {
    box(0f, 0f, 1f, 1f, WallBack)
    val side = Color(0xFFCFCBC4)
    val top = Color(0xFFE9E6E0)
    if (shape == com.sagoma.planimetria.model.ColumnShape.Round) {
        box(0.38f, 0.14f, 0.62f, 0.9f, side)
        drawOval(side.shade(0.85f), px(0.38f, 0.84f), Size(size.width * 0.24f, size.height * 0.12f))
        drawOval(top, px(0.38f, 0.08f), Size(size.width * 0.24f, size.height * 0.12f))
        drawOval(Ink, px(0.38f, 0.08f), Size(size.width * 0.24f, size.height * 0.12f), style = Stroke(0.8f * density))
    } else {
        box(0.36f, 0.16f, 0.58f, 0.92f, side)
        box(0.58f, 0.12f, 0.66f, 0.88f, side.shade(0.85f))
        val p = Path().apply {
            moveTo(px(0.36f, 0.16f).x, px(0.36f, 0.16f).y); lineTo(px(0.44f, 0.1f).x, px(0.44f, 0.1f).y)
            lineTo(px(0.66f, 0.1f).x, px(0.66f, 0.1f).y); lineTo(px(0.58f, 0.16f).x, px(0.58f, 0.16f).y); close()
        }
        drawPath(p, top)
        drawPath(p, Ink, style = Stroke(0.8f * density))
    }
}

// ---------- Termosifoni e lampadari ----------

internal fun DrawScope.radiatorPreview(model: RadiatorModel) {
    box(0f, 0f, 1f, 1f, WallBack)
    val c = Color(0xFFF7F7F5)
    val edge = Color(0xFFB0B2B5)
    when (model) {
        RadiatorModel.Panel -> {
            box(0.12f, 0.3f, 0.88f, 0.8f, c)
            box(0.12f, 0.3f, 0.88f, 0.8f, edge, 1f * density)
            var y = 0.38f
            while (y < 0.78f) { drawLine(edge, px(0.14f, y), px(0.86f, y), 0.8f * density); y += 0.07f }
        }
        RadiatorModel.Fins -> {
            var x = 0.12f
            while (x < 0.86f) {
                drawRoundRect(c, px(x, 0.28f), Size(size.width * 0.06f, size.height * 0.54f), CornerRadius(3f * density))
                drawRoundRect(edge, px(x, 0.28f), Size(size.width * 0.06f, size.height * 0.54f), CornerRadius(3f * density), style = Stroke(0.8f * density))
                x += 0.075f
            }
        }
        RadiatorModel.TowelRail -> {
            val chrome = Color(0xFF9EA3A8)
            drawLine(chrome, px(0.3f, 0.06f), px(0.3f, 0.95f), 3f * density, cap = StrokeCap.Round)
            drawLine(chrome, px(0.7f, 0.06f), px(0.7f, 0.95f), 3f * density, cap = StrokeCap.Round)
            var y = 0.12f
            while (y < 0.92f) { drawLine(chrome, px(0.3f, y), px(0.7f, y), 1.6f * density); y += if (y in 0.4f..0.5f) 0.14f else 0.07f }
        }
    }
}

internal fun DrawScope.lampPreview(model: LampModel) {
    box(0f, 0f, 1f, 0.06f, WallBack)
    val glow = Color(0xFFFFE8A3)
    val dark = Color(0xFF3A3F44)
    when (model) {
        LampModel.Modern -> {
            drawLine(dark, px(0.5f, 0.06f), px(0.5f, 0.5f), 1.2f * density)
            box(0.28f, 0.5f, 0.72f, 0.62f, glow)
            box(0.28f, 0.5f, 0.72f, 0.62f, dark, 1f * density)
        }
        LampModel.Classic -> {
            drawLine(dark, px(0.5f, 0.06f), px(0.5f, 0.55f), 1.4f * density)
            for (x in listOf(0.16f, 0.33f, 0.67f, 0.84f)) {
                drawLine(dark, px(0.5f, 0.62f), px(x, 0.5f), 1.4f * density)
                drawLine(dark, px(x, 0.5f), px(x, 0.42f), 1.4f * density)
                drawCircle(glow, 3.2f * density, px(x, 0.36f))
            }
            drawCircle(dark, 3f * density, px(0.5f, 0.62f))
        }
        LampModel.Bell -> {
            drawLine(dark, px(0.5f, 0.06f), px(0.5f, 0.38f), 1.2f * density)
            val p = Path().apply {
                moveTo(px(0.42f, 0.38f).x, px(0.42f, 0.38f).y); lineTo(px(0.58f, 0.38f).x, px(0.58f, 0.38f).y)
                lineTo(px(0.8f, 0.78f).x, px(0.8f, 0.78f).y); lineTo(px(0.2f, 0.78f).x, px(0.2f, 0.78f).y); close()
            }
            drawPath(p, Color(0xFF2F3337))
            drawCircle(glow, 4f * density, px(0.5f, 0.8f))
        }
        LampModel.Industrial -> {
            for ((x, y) in listOf(0.22f to 0.55f, 0.5f to 0.72f, 0.78f to 0.48f)) {
                drawLine(dark, px(x, 0.06f), px(x, y), 1f * density)
                val p = Path().apply {
                    moveTo(px(x - 0.03f, y).x, px(x - 0.03f, y).y); lineTo(px(x + 0.03f, y).x, px(x + 0.03f, y).y)
                    lineTo(px(x + 0.1f, y + 0.14f).x, px(x + 0.1f, y + 0.14f).y); lineTo(px(x - 0.1f, y + 0.14f).x, px(x - 0.1f, y + 0.14f).y); close()
                }
                drawPath(p, Color(0xFF4A4E53))
                drawCircle(glow, 2.4f * density, px(x, y + 0.15f))
            }
        }
    }
}

// ---------- Pavimenti ----------

/** Quadretto di pavimento con il suo disegno; `roomColor` per la tinta della stanza. */
internal fun DrawScope.floorPreview(finish: FloorFinish, roomColor: Color) {
    val base = finish.argb?.let { Color(it) } ?: roomColor.shade(1.55f)
    box(0f, 0f, 1f, 1f, base)
    val line = base.shade(0.8f)
    val w = 1f * density
    when (finish.pattern) {
        FloorPattern.None -> Unit
        FloorPattern.Planks -> {
            var y = 0.14f
            var row = 0
            while (y < 1f) {
                drawLine(line, px(0f, y), px(1f, y), w)
                val off = if (row % 2 == 0) 0.2f else 0.55f
                drawLine(line, px(off, y - 0.14f), px(off, y), w)
                drawLine(line, px(off + 0.45f, y - 0.14f), px(off + 0.45f, y), w)
                y += 0.14f; row++
            }
        }
        FloorPattern.Tiles, FloorPattern.LargeTiles -> {
            val step = if (finish.pattern == FloorPattern.Tiles) 0.25f else 0.5f
            var x = step
            while (x < 1f) { drawLine(line, px(x, 0f), px(x, 1f), w); x += step }
            var y = step
            while (y < 1f) { drawLine(line, px(0f, y), px(1f, y), w); y += step }
        }
    }
    if (finish == FloorFinish.Marble) {
        drawLine(base.shade(0.85f), px(0.1f, 0.2f), px(0.6f, 0.8f), 0.8f * density)
        drawLine(base.shade(0.88f), px(0.5f, 0.1f), px(0.9f, 0.6f), 0.6f * density)
    }
}

