package com.sagoma.planimetria.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TutorialStep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Mini animazioni delle guide: una piccola scena che si ripete in loop e mostra il gesto da fare
 * (un dito che tocca, trascina, apre la tendina...). Le usano le schede del tutorial, i suggerimenti
 * e le miniguide del pulsante "Guide".
 */
enum class Sketch {
    Welcome, OpenInfo, WallLengths, ChangeType, MoveWall, MoveCorner, AddDoor, MoveDoor, AddFixture,
    AddRoom, MoveRoom, Undo, Ruler, Done, WallCut, View3D,
    AtticIntro, AtticRoom, AtticLow, AtticCut, AtticStart, AtticRepeat, AtticCheck, Attic3D,
    AddStair, StairOptions, AddFloor, Floors3D, Outdoor, Structure, Measure, FreeWall,
}

/** Animazione che accompagna un passo del tutorial. */
val TutorialStep.sketch: Sketch
    get() = when (this) {
        TutorialStep.Intro -> Sketch.Welcome
        TutorialStep.OpenInfo -> Sketch.OpenInfo
        TutorialStep.WallLengths -> Sketch.WallLengths
        TutorialStep.ChangeType -> Sketch.ChangeType
        TutorialStep.MoveWall -> Sketch.MoveWall
        TutorialStep.MoveCorner -> Sketch.MoveCorner
        TutorialStep.AddDoor -> Sketch.AddDoor
        TutorialStep.MoveDoor -> Sketch.MoveDoor
        TutorialStep.AddFixture -> Sketch.AddFixture
        TutorialStep.AddRoom -> Sketch.AddRoom
        TutorialStep.MoveRoom -> Sketch.MoveRoom
        TutorialStep.Undo -> Sketch.Undo
        TutorialStep.Ruler -> Sketch.Ruler
        TutorialStep.Done -> Sketch.Done
    }

/** Animazione che accompagna un suggerimento al primo utilizzo. */
val Tip.sketch: Sketch
    get() = when (this) {
        Tip.Welcome -> Sketch.Welcome
        Tip.Room -> Sketch.MoveCorner
        Tip.Wall -> Sketch.MoveWall
        Tip.NewRoom -> Sketch.MoveRoom
        Tip.Opening -> Sketch.MoveDoor
        Tip.Fixture -> Sketch.AddFixture
        Tip.Ruler -> Sketch.Ruler
        Tip.WallCut -> Sketch.WallCut
        Tip.View3D -> Sketch.View3D
        Tip.Stair -> Sketch.AddStair
        Tip.Floors -> Sketch.AddFloor
        Tip.Outdoor -> Sketch.Outdoor
        Tip.Structure -> Sketch.Structure
        Tip.Measure -> Sketch.Measure
        Tip.FreeWall -> Sketch.FreeWall
    }

/** Disegna `sketch` in loop (un giro ogni `durationMs`), centrato in un quadrato dentro `modifier`. */
@Composable
fun AnimatedSketch(sketch: Sketch, modifier: Modifier = Modifier, durationMs: Int = 4200) {
    val measurer = rememberTextMeasurer()
    // Cambiando scena l'animazione riparte dall'inizio.
    key(sketch) {
        val transition = rememberInfiniteTransition(label = "guida")
        val t by transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "tempo",
        )
        Canvas(modifier) { drawSketch(sketch, Frame(size, measurer, t)) }
    }
}

private val SkWall = Color(0xFF3A3F44)
private val SkSel = Color(0xFF1971C2)
private val SkFloor = Color(0xFFF1F3F5)
private val SkKitchen = Color(0xFFFBD5BE)
private val SkRoom2 = Color(0xFFD3DDFB)
private val SkDoor = Color(0xFF8B5E3C)
private val SkElectric = Color(0xFF1C7ED6)
private val SkCut = Color(0xFFB5501D)
private val SkLow = Color(0xFF862E9C)
private val SkWarn = Color(0xFFE03131)
private val SkWindow = Color(0xFF0B7285)
private val SkOk = Color(0xFF2F9E44)
private val SkText = Color(0xFF212529)
private val SkElevation = Color(0xFFDEE2E6)

/** Scena in coordinate unitarie (0..1) dentro il quadrato più grande che sta nel riquadro; `t` è il tempo 0..1. */
private class Frame(size: Size, val m: TextMeasurer, val t: Float) {
    val s = min(size.width, size.height)
    val ox = (size.width - s) / 2
    val oy = (size.height - s) / 2
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun p(o: Offset) = p(o.x, o.y)
    fun l(v: Float) = v * s
    /** Avanzamento lineare 0..1 tra i tempi a e b. */
    fun lin(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    /** Come [lin], ma che parte e arriva dolcemente. */
    fun seg(a: Float, b: Float) = ease(lin(a, b))
    /** Onda del tocco: 0..1 per un breve istante dopo `at`, altrimenti 0. */
    fun tap(vararg at: Float): Float = at.firstOrNull { t >= it && t < it + 0.1f }?.let { (t - it) / 0.1f } ?: 0f
    /** Dito visibile da `show` a `hide` (con dissolvenza). */
    fun shown(show: Float, hide: Float) = lin(show, show + 0.06f) * (1 - lin(hide, hide + 0.08f))
}

private fun ease(x: Float) = x * x * (3 - 2 * x)
private fun mix(a: Float, b: Float, x: Float) = a + (b - a) * x
private fun mix(a: Offset, b: Offset, x: Float) = Offset(mix(a.x, b.x, x), mix(a.y, b.y, x))
/** Va e torna: 0 → 1 → 0 in un giro. */
private fun pingPong(t: Float) = ease(if (t < 0.5f) t * 2 else 2 - t * 2)

/** Posizione lungo una sequenza di tappe (tempo, punto), con partenza e arrivo dolci. */
private fun along(t: Float, vararg keys: Pair<Float, Offset>): Offset {
    if (t <= keys.first().first) return keys.first().second
    for (k in 0 until keys.size - 1) {
        val (t0, p0) = keys[k]
        val (t1, p1) = keys[k + 1]
        if (t <= t1) return mix(p0, p1, ease(((t - t0) / (t1 - t0)).coerceIn(0f, 1f)))
    }
    return keys.last().second
}

private fun DrawScope.drawSketch(sketch: Sketch, f: Frame) = when (sketch) {
    Sketch.Welcome -> welcome(f)
    Sketch.OpenInfo -> openInfo(f)
    Sketch.WallLengths -> wallLengths(f)
    Sketch.ChangeType -> changeType(f)
    Sketch.MoveWall -> moveWall(f)
    Sketch.MoveCorner -> moveCorner(f)
    Sketch.AddDoor -> addDoor(f)
    Sketch.MoveDoor -> moveDoor(f)
    Sketch.AddFixture -> addFixture(f)
    Sketch.AddRoom -> addRoom(f)
    Sketch.MoveRoom -> moveRoom(f)
    Sketch.Undo -> undo(f)
    Sketch.Ruler -> ruler(f)
    Sketch.Done -> done(f)
    Sketch.WallCut -> {
        val start = mix(0.4f, 0.7f, pingPong(f.t))
        elevation(f, low = 120f / 270f, start = start, handle = true)
        finger(f, Offset(start, 0.24f), 1f, 0f)
    }
    Sketch.View3D -> view3d(f)
    Sketch.AtticIntro -> elevation(f, low = mix(1f, 120f / 270f, f.seg(0.15f, 0.55f)), start = 0.5f)
    Sketch.AtticRoom -> atticRoom(f)
    Sketch.AtticLow -> atticLow(f)
    Sketch.AtticCut -> atticCut(f)
    Sketch.AtticStart -> {
        val start = mix(0.36f, 0.68f, pingPong(f.t))
        elevation(f, low = 120f / 270f, start = start, handle = true, measure = true)
        finger(f, Offset(start, 0.24f), 1f, 0f)
    }
    Sketch.AtticRepeat -> atticRepeat(f)
    Sketch.AtticCheck -> atticCheck(f)
    Sketch.Attic3D -> attic3d(f)
    Sketch.AddStair -> addStair(f)
    Sketch.StairOptions -> stairOptions(f)
    Sketch.AddFloor -> addFloor(f)
    Sketch.Floors3D -> floors3d(f)
    Sketch.Outdoor -> outdoor(f)
    Sketch.Structure -> structure(f)
    Sketch.Measure -> measure(f)
    Sketch.FreeWall -> freeWall(f)
}

/**
 * Muro singolo: compare attraverso la stanza; il pallino in basso viene trascinato verso l'alto e poi
 * a destra, e il muro gira raddrizzandosi a 90° e si aggancia al muro di destra.
 */
private fun DrawScope.freeWall(f: Frame) {
    room(f, Rect(0.1f, 0.12f, 0.9f, 0.82f))
    val a = f.seg(0.05f, 0.2f)
    if (a <= 0f) return
    // Parte dal muro di sinistra; l'estremità trascinata è un po' storta, si raddrizza a 90° e arriva al
    // muro di destra, dove si aggancia.
    val start = Offset(0.1f, 0.47f)
    val drag = f.seg(0.2f, 0.7f)
    val x = mix(0.35f, 0.9f, drag)
    val y = if (drag < 0.3f) mix(0.58f, 0.47f, drag / 0.3f) else 0.47f
    val end = Offset(x, y)
    val color = if (f.t in 0.2f..0.85f) SkSel else SkWall
    drawLine(color.copy(alpha = a), f.p(start), f.p(end), strokeWidth = f.l(0.03f), cap = StrokeCap.Butt)
    if (f.t in 0.2f..0.85f) drawCircle(SkSel, f.l(0.025f), f.p(end))
    finger(f, end, f.shown(0.16f, 0.84f), f.tap(0.18f))
}

/** Si tocca il calorifero, poi il muro di fronte: compare la quota con la distanza. */
private fun DrawScope.measure(f: Frame) {
    room(f, Rect(0.1f, 0.12f, 0.9f, 0.82f))
    val orange = Color(0xFFD9480F)
    // Calorifero sul muro di sinistra.
    val radTl = f.p(0.13f, 0.34f)
    val first = f.t > 0.18f
    val second = f.t > 0.42f
    drawRect(if (first) orange.copy(alpha = 0.3f) else Color(0xFFFFE3D3), radTl, Size(f.l(0.06f), f.l(0.26f)))
    drawRect(if (first) orange else Color(0xFFD9480F).copy(alpha = 0.6f), radTl, Size(f.l(0.06f), f.l(0.26f)), style = Stroke(f.l(0.008f)))
    if (second) wall(f, Offset(0.9f, 0.12f), Offset(0.9f, 0.82f), orange.copy(alpha = 0.6f), 0.05f)
    val d = f.seg(0.46f, 0.6f)
    if (d > 0f) {
        val y = 0.47f
        drawLine(orange, f.p(0.19f, y), f.p(mix(0.19f, 0.88f, d), y), strokeWidth = f.l(0.01f))
        if (d >= 1f) {
            drawLine(orange, f.p(0.19f, y - 0.03f), f.p(0.19f, y + 0.03f), strokeWidth = f.l(0.01f))
            drawLine(orange, f.p(0.88f, y - 0.03f), f.p(0.88f, y + 0.03f), strokeWidth = f.l(0.01f))
            text(f, "341,5 cm", Offset(0.53f, y - 0.07f), color = orange, bold = true, size = 0.07f)
        }
    }
    val pos = along(f.t, 0f to Offset(0.16f, 0.47f), 0.3f to Offset(0.16f, 0.47f), 0.4f to Offset(0.9f, 0.6f))
    finger(f, pos, f.shown(0.02f, 0.5f), f.tap(0.16f, 0.42f))
}

/**
 * Colonna trascinata verso il muro, dove si accosta (pilastro); poi la trave a soffitto tratteggiata
 * che si allunga fino al muro opposto.
 */
private fun DrawScope.structure(f: Frame) {
    room(f, Rect(0.1f, 0.1f, 0.9f, 0.78f))
    // Colonna: dal centro al muro di destra, dove si ferma contro la faccia.
    val x = mix(0.5f, 0.81f, f.seg(0.1f, 0.4f)).let { if (f.t > 0.4f) mix(it, 0.8175f, f.seg(0.4f, 0.44f)) else it }
    val colTl = f.p(x - 0.05f, 0.4f)
    drawRect(if (f.t in 0.06f..0.46f) SkSel else SkWall, colTl, Size(f.l(0.1f), f.l(0.1f)))
    finger(f, Offset(x, 0.45f), f.shown(0.02f, 0.46f), f.tap(0.06f))
    // Trave: compare e si allunga fino al muro in alto.
    val a = f.seg(0.52f, 0.6f)
    if (a > 0f) {
        val y1 = mix(0.62f, 0.12f, f.seg(0.62f, 0.86f))
        val dash = PathEffect.dashPathEffect(floatArrayOf(f.l(0.025f), f.l(0.018f)))
        val tl = f.p(0.3f, y1)
        drawRect(Color(0xFF6C4A2E).copy(alpha = 0.1f * a), tl, Size(f.l(0.08f), f.l(0.74f - y1)))
        drawRect(Color(0xFF6C4A2E).copy(alpha = a), tl, Size(f.l(0.08f), f.l(0.74f - y1)), style = Stroke(f.l(0.008f), pathEffect = dash))
        drawCircle(SkSel.copy(alpha = a), f.l(0.025f), f.p(0.34f, y1))
        finger(f, Offset(0.34f, y1), f.shown(0.6f, 0.9f), f.tap(0.62f))
    }
}

/**
 * Balcone: compare appoggiato alla casa con la ringhiera (doppia linea) sui lati verso l'esterno, poi sul
 * muro della stanza si apre la porta-finestra.
 */
private fun DrawScope.outdoor(f: Frame) {
    room(f, Rect(0.08f, 0.14f, 0.56f, 0.78f), fill = SkRoom2, label = "Camera")
    val a = f.seg(0.12f, 0.32f)
    if (a > 0f) {
        val x1 = mix(0.6f, 0.9f, a)
        val top = 0.3f
        val bottom = 0.64f
        drawRect(Color(0xFFC3FAE8).copy(alpha = a), f.p(0.56f, top), Size(f.l(x1 - 0.56f), f.l(bottom - top)))
        val rail = Path().apply {
            moveTo(f.p(0.56f, top).x, f.p(0.56f, top).y)
            lineTo(f.p(x1, top).x, f.p(x1, top).y)
            lineTo(f.p(x1, bottom).x, f.p(x1, bottom).y)
            lineTo(f.p(0.56f, bottom).x, f.p(0.56f, bottom).y)
        }
        drawPath(rail, SkWall.copy(alpha = a), style = Stroke(f.l(0.022f)))
        drawPath(rail, Color.White.copy(alpha = a), style = Stroke(f.l(0.009f)))
        text(f, "Balcone", Offset((0.56f + x1) / 2, (top + bottom) / 2), alpha = f.lin(0.3f, 0.36f), size = 0.06f)
    }
    // Porta-finestra sul muro della stanza.
    val d = f.seg(0.5f, 0.64f)
    if (d > 0f) {
        wall(f, Offset(0.56f, 0.38f), Offset(0.56f, 0.56f), Color.White, width = 0.04f)
        drawLine(SkWindow, f.p(0.56f, 0.38f), f.p(0.56f, mix(0.38f, 0.56f, d)), strokeWidth = f.l(0.014f))
        drawArc(
            SkWindow, 270f, 90f * d, useCenter = false, topLeft = f.p(0.56f - 0.18f, 0.38f), size = Size(f.l(0.36f), f.l(0.36f)),
            style = Stroke(f.l(0.006f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(f.l(0.02f), f.l(0.015f)))),
        )
    }
    button(f, "+ Balconi e terrazze", Offset(0.5f, 0.9f), 0.8f, pressed = f.t in 0.04f..0.12f)
    finger(f, Offset(0.5f, 0.9f), f.shown(0.0f, 0.14f), f.tap(0.05f))
}

// ---- Scale e piani ----

/**
 * Scala vista in pianta: rampa dritta larga `w` e lunga `len` a partire da (x, y) verso l'alto, con le
 * pedate, la linea di salita e la freccia. `turn`: 0 dritta, 1 a L verso destra (seconda rampa orizzontale).
 */
private fun DrawScope.stairPlan(f: Frame, x: Float, y: Float, w: Float, len: Float, color: Color, turn: Boolean = false, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val c = color.copy(alpha = alpha)
    val fill = Color(0xFFF3EEE6).copy(alpha = alpha)
    val steps = 7
    val g = len / steps
    fun r(l: Float, t: Float, rr: Float, b: Float) {
        drawRect(fill, f.p(l, t), Size(f.l(rr - l), f.l(b - t)))
        drawRect(c, f.p(l, t), Size(f.l(rr - l), f.l(b - t)), style = Stroke(f.l(0.006f)))
    }
    for (i in 0 until steps) r(x, y - (i + 1) * g, x + w, y - i * g)
    val top = y - len
    val mid = x + w / 2
    val line = Path().apply {
        moveTo(f.p(mid, y - g / 2).x, f.p(mid, y - g / 2).y)
        if (turn) {
            r(x, top - w, x + w, top)
            for (j in 0 until 4) r(x + w + j * g, top - w, x + w + (j + 1) * g, top)
            lineTo(f.p(mid, top - w / 2).x, f.p(mid, top - w / 2).y)
            lineTo(f.p(x + w + 4 * g, top - w / 2).x, f.p(x + w + 4 * g, top - w / 2).y)
        } else lineTo(f.p(mid, top).x, f.p(mid, top).y)
    }
    drawPath(line, c, style = Stroke(f.l(0.008f)))
    drawCircle(c, f.l(0.014f), f.p(mid, y - g / 2))
    val end = if (turn) Offset(x + w + 4 * g, top - w / 2) else Offset(mid, top)
    val dir = if (turn) Offset(1f, 0f) else Offset(0f, -1f)
    val back = Offset(-dir.x, -dir.y)
    val side = Offset(-dir.y, dir.x)
    for (sgn in listOf(1f, -1f)) {
        drawLine(c, f.p(end), f.p(end + back * 0.035f + side * (0.025f * sgn)), strokeWidth = f.l(0.008f))
    }
}

/** "+ Scale" → tipo → la scala compare nella stanza; trascinata verso un muro, ci si accosta. */
private fun DrawScope.addStair(f: Frame) {
    room(f, Rect(0.1f, 0.08f, 0.9f, 0.68f))
    val a = f.seg(0.34f, 0.44f)
    val x = mix(0.42f, 0.14f, f.seg(0.52f, 0.8f)).let { if (f.t > 0.8f) mix(it, 0.127f, f.seg(0.8f, 0.84f)) else it }
    stairPlan(f, x, 0.62f, 0.14f, 0.44f, if (f.t in 0.44f..0.86f) SkSel else SkWall, alpha = a)
    button(f, "+ Scale", Offset(0.3f, 0.88f), 0.4f, pressed = f.t in 0.08f..0.16f)
    menu(f, "Rampa dritta", Offset(0.36f, 0.76f), 0.5f, highlight = f.t in 0.26f..0.34f, alpha = f.lin(0.14f, 0.18f) * (1 - f.lin(0.34f, 0.38f)))
    val pos = if (f.t < 0.5f) along(f.t, 0f to Offset(0.3f, 0.88f), 0.16f to Offset(0.3f, 0.88f), 0.24f to Offset(0.36f, 0.76f), 0.4f to Offset(0.36f, 0.76f), 0.5f to Offset(0.49f, 0.4f))
    else Offset(x + 0.07f, 0.4f)
    finger(f, pos, f.shown(0.02f, 0.88f), f.tap(0.1f, 0.28f))
}

/** Dalla tendina Info: il tipo cambia da dritta ad L e a chiocciola. */
private fun DrawScope.stairOptions(f: Frame) {
    room(f, Rect(0.08f, 0.06f, 0.92f, 0.66f))
    val phase = when {
        f.t < 0.33f -> 0
        f.t < 0.66f -> 1
        else -> 2
    }
    when (phase) {
        0 -> stairPlan(f, 0.2f, 0.6f, 0.13f, 0.42f, SkSel)
        1 -> stairPlan(f, 0.2f, 0.6f, 0.13f, 0.3f, SkSel, turn = true)
        else -> {
            val c = f.p(0.4f, 0.36f)
            val rr = f.l(0.18f)
            drawCircle(Color(0xFFF3EEE6), rr, c)
            drawCircle(SkSel, rr, c, style = Stroke(f.l(0.008f)))
            for (k in 0 until 12) {
                val ang = k * PI.toFloat() * 2 / 12
                drawLine(SkSel, c + Offset(cos(ang), sin(ang)) * f.l(0.025f), c + Offset(cos(ang), sin(ang)) * rr, strokeWidth = f.l(0.005f))
            }
            drawCircle(SkSel, f.l(0.025f), c)
        }
    }
    listOf("Dritta", "A L", "Chiocciola").forEachIndexed { i, s ->
        button(f, s, Offset(0.2f + i * 0.3f, 0.84f), 0.27f, pressed = false, filled = i == phase)
    }
    finger(f, Offset(0.2f + phase * 0.3f, 0.86f), 1f, f.tap(0.02f, 0.35f, 0.68f))
}

/** Menu del piano → "Aggiungi un piano sopra": il piano di sotto resta in grigio e si disegna quello nuovo. */
private fun DrawScope.addFloor(f: Frame) {
    val newFloor = f.t > 0.4f
    button(f, if (newFloor) "🏠 Primo piano ▾" else "🏠 Piano terra ▾", Offset(0.3f, 0.08f), 0.52f, pressed = f.t in 0.08f..0.16f, h = 0.1f)
    menu(f, "+ Aggiungi un piano sopra", Offset(0.44f, 0.2f), 0.78f, highlight = f.t in 0.28f..0.36f, alpha = f.lin(0.14f, 0.18f) * (1 - f.lin(0.36f, 0.4f)))
    val ground = Rect(0.12f, 0.32f, 0.88f, 0.9f)
    if (!newFloor) {
        room(f, ground, fill = SkKitchen)
        wall(f, Offset(0.5f, 0.32f), Offset(0.5f, 0.9f), SkWall)
        stairPlan(f, 0.72f, 0.86f, 0.1f, 0.3f, SkWall)
    } else {
        // Piano di sotto in trasparenza, vuoto della scala e stanze nuove che compaiono.
        val dash = PathEffect.dashPathEffect(floatArrayOf(f.l(0.03f), f.l(0.02f)))
        drawRect(Color(0xFFADB5BD), f.p(ground.left, ground.top), Size(f.l(ground.width), f.l(ground.height)), style = Stroke(f.l(0.02f), pathEffect = dash))
        val a = f.seg(0.45f, 0.62f)
        room(f, Rect(0.12f, 0.32f, 0.62f, 0.9f), fill = SkRoom2, alpha = a, label = "Camera")
        val voidTl = f.p(0.72f, 0.56f)
        drawRect(Color(0xFFE9ECEF), voidTl, Size(f.l(0.1f), f.l(0.3f)))
        drawRect(SkWall, voidTl, Size(f.l(0.1f), f.l(0.3f)), style = Stroke(f.l(0.006f), pathEffect = dash))
        drawLine(SkWall.copy(alpha = 0.4f), voidTl, f.p(0.82f, 0.86f), strokeWidth = f.l(0.005f))
        drawLine(SkWall.copy(alpha = 0.4f), f.p(0.82f, 0.56f), f.p(0.72f, 0.86f), strokeWidth = f.l(0.005f))
    }
    val pos = along(f.t, 0f to Offset(0.3f, 0.08f), 0.18f to Offset(0.3f, 0.08f), 0.26f to Offset(0.44f, 0.2f))
    finger(f, pos, f.shown(0.02f, 0.42f), f.tap(0.1f, 0.3f))
}

/** In 3D il piano scelto sta sopra quelli di sotto; una scala li collega. */
private fun DrawScope.floors3d(f: Frame) {
    val ang = 0.5f + 0.5f * sin(f.t * 2 * PI.toFloat())
    val xs = 1f
    val zs = 0.7f
    val h = 0.55f
    val drop = mix(1.2f, 0f, f.seg(0.1f, 0.45f)) // il piano di sopra scende al suo posto
    val wallColor = Color(0xFFCED4DA)
    fun floorFaces(y: Float, floorColor: Color) = listOf(
        quad(P3(-xs, y, -zs), P3(xs, y, -zs), P3(xs, y, zs), P3(-xs, y, zs)) to floorColor,
        quad(P3(-xs, y, -zs), P3(xs, y, -zs), P3(xs, y + h, -zs), P3(-xs, y + h, -zs)) to wallColor,
        quad(P3(-xs, y, zs), P3(xs, y, zs), P3(xs, y + h, zs), P3(-xs, y + h, zs)) to wallColor,
        quad(P3(-xs, y, -zs), P3(-xs, y, zs), P3(-xs, y + h, zs), P3(-xs, y + h, -zs)) to wallColor,
        quad(P3(xs, y, -zs), P3(xs, y, zs), P3(xs, y + h, zs), P3(xs, y + h, -zs)) to wallColor,
    )
    // Due piani: si disegnano separati, prima quello di sotto (abbassato) e poi quello di sopra.
    solidAt(f, floorFaces(0f, Color(0xFFF8E4C8)), ang, cy = 0.78f)
    solidAt(f, floorFaces(0f, Color(0xFFD3DDFB)), ang, cy = 0.78f - (0.62f + drop) * 0.34f * 0.95f, alpha = f.lin(0f, 0.12f))
}

/** Come [solid], con il centro verticale della scena a `cy`. */
private fun DrawScope.solidAt(f: Frame, faces: List<Pair<List<P3>, Color>>, ang: Float, cy: Float, alpha: Float = 1f) {
    val projected = faces.map { (pts, color) ->
        val pr = pts.map { project(f, it, ang, cy = cy) }
        Triple(pr.map { it.first }, pr.map { it.second }.average().toFloat(), color to pts.all { it.y == pts.first().y })
    }.sortedBy { if (it.third.second) -10f else it.second }
    for ((pts, depth, info) in projected) {
        val (color, flat) = info
        val path = Path().apply { pts.forEachIndexed { i, c -> if (i == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y) }; close() }
        val near = !flat && depth > 0.15f
        drawPath(path, color.copy(alpha = (if (near) 0.4f else 1f) * alpha))
        drawPath(path, SkWall.copy(alpha = (if (near) 0.7f else 1f) * alpha), style = Stroke(f.l(if (near) 0.008f else 0.011f)))
    }
}

// ---- Pezzi comuni ----

private fun DrawScope.text(f: Frame, s: String, center: Offset, color: Color = SkText, size: Float = 0.068f, bold: Boolean = false, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val layout = f.m.measure(
        s, TextStyle(fontSize = f.l(size).toSp(), color = color.copy(alpha = color.alpha * alpha), fontWeight = if (bold) FontWeight.Bold else null),
    )
    val c = f.p(center)
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
}

/** Stanza rettangolare (coordinate unitarie), eventualmente selezionata con i pallini agli angoli. */
private fun DrawScope.room(f: Frame, r: Rect, fill: Color = SkFloor, sel: Boolean = false, label: String? = null, alpha: Float = 1f) {
    polygon(f, listOf(Offset(r.left, r.top), Offset(r.right, r.top), Offset(r.right, r.bottom), Offset(r.left, r.bottom)), fill, sel, alpha)
    label?.let { text(f, it, r.center, alpha = alpha) }
}

private fun DrawScope.polygon(f: Frame, pts: List<Offset>, fill: Color, sel: Boolean = false, alpha: Float = 1f) {
    val path = Path().apply {
        pts.forEachIndexed { i, q -> val c = f.p(q); if (i == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y) }
        close()
    }
    drawPath(path, fill.copy(alpha = alpha))
    drawPath(path, (if (sel) SkSel else SkWall).copy(alpha = alpha), style = Stroke(f.l(0.03f)))
    if (sel) for (q in pts) {
        drawCircle(SkSel.copy(alpha = alpha), f.l(0.03f), f.p(q))
        drawCircle(Color.White.copy(alpha = alpha), f.l(0.013f), f.p(q))
    }
}

private fun DrawScope.wall(f: Frame, a: Offset, b: Offset, color: Color, width: Float = 0.036f) =
    drawLine(color, f.p(a), f.p(b), strokeWidth = f.l(width), cap = StrokeCap.Square)

/** Dito: un cerchio grigio con il bordo bianco; `press` (0..1) disegna l'onda del tocco. */
private fun DrawScope.finger(f: Frame, at: Offset, alpha: Float, press: Float) {
    if (alpha <= 0f) return
    val c = f.p(at)
    if (press > 0f) {
        drawCircle(SkSel.copy(alpha = 0.45f * alpha * (1 - press)), f.l(0.06f + 0.07f * press), c, style = Stroke(f.l(0.014f)))
    }
    drawCircle(Color(0xFF212529).copy(alpha = 0.28f * alpha), f.l(0.06f), c)
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), f.l(0.06f), c, style = Stroke(f.l(0.012f)))
}

/** Pulsante della barra (contorno arrotondato); `pressed` lo riempie mentre viene toccato. */
private fun DrawScope.button(f: Frame, label: String, center: Offset, w: Float, pressed: Boolean, h: Float = 0.11f, filled: Boolean = false) {
    val tl = f.p(center.x - w / 2, center.y - h / 2)
    val size = Size(f.l(w), f.l(h))
    val r = CornerRadius(f.l(h / 2))
    if (pressed || filled) drawRoundRect(if (filled) SkSel else SkSel.copy(alpha = 0.18f), tl, size, r)
    drawRoundRect(SkSel.copy(alpha = 0.7f), tl, size, r, style = Stroke(f.l(0.008f)))
    text(f, label, center, color = if (filled) Color.White else SkSel, size = 0.058f, bold = true)
}

/** Menu a tendina con una voce sola (evidenziata mentre viene toccata). */
private fun DrawScope.menu(f: Frame, label: String, center: Offset, w: Float, highlight: Boolean, alpha: Float) {
    if (alpha <= 0f) return
    val h = 0.12f
    val tl = f.p(center.x - w / 2, center.y - h / 2)
    val size = Size(f.l(w), f.l(h))
    drawRoundRect(Color.White.copy(alpha = alpha), tl, size, CornerRadius(f.l(0.02f)))
    if (highlight) drawRoundRect(SkSel.copy(alpha = 0.15f * alpha), tl, size, CornerRadius(f.l(0.02f)))
    drawRoundRect(Color(0xFFADB5BD).copy(alpha = alpha), tl, size, CornerRadius(f.l(0.02f)), style = Stroke(f.l(0.006f)))
    text(f, label, center, size = 0.058f, alpha = alpha)
}

/** Porta a un'anta nel muro orizzontale alla quota `wallY`, cardine in `hingeX`, che si apre verso il basso. */
private fun DrawScope.door(f: Frame, hingeX: Float, wallY: Float, w: Float, color: Color, open: Float = 1f) {
    if (open <= 0f) return
    wall(f, Offset(hingeX, wallY), Offset(hingeX + w, wallY), SkFloor, width = 0.04f)
    val ang = open * PI.toFloat() / 2
    drawLine(color, f.p(hingeX, wallY), f.p(hingeX + w * cos(ang), wallY + w * sin(ang)), strokeWidth = f.l(0.016f))
    drawArc(
        color, 0f, open * 90f, useCenter = false, topLeft = f.p(hingeX - w, wallY - w), size = Size(f.l(2 * w), f.l(2 * w)),
        style = Stroke(f.l(0.008f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(f.l(0.02f), f.l(0.015f)))),
    )
}

/** Presa di corrente sul muro verticale sinistro (in x), rivolta verso l'interno. */
private fun DrawScope.outlet(f: Frame, at: Offset, scale: Float) {
    if (scale <= 0f) return
    val r = f.l(0.042f * scale)
    val c = f.p(at)
    drawCircle(Color.White, r, c)
    drawCircle(SkElectric, r, c, style = Stroke(f.l(0.01f)))
    val d = r * 0.35f
    drawLine(SkElectric, Offset(c.x - d, c.y - r * 0.4f), Offset(c.x - d, c.y + r * 0.4f), strokeWidth = f.l(0.01f))
    drawLine(SkElectric, Offset(c.x + d, c.y - r * 0.4f), Offset(c.x + d, c.y + r * 0.4f), strokeWidth = f.l(0.01f))
}

// ---- Scene del tutorial e dei suggerimenti ----

/** Due dita allargano (zoom), poi un tocco seleziona la stanza. */
private fun DrawScope.welcome(f: Frame) {
    val z = f.seg(0.08f, 0.42f)
    val k = mix(0.62f, 1f, z)
    val c = Offset(0.5f, 0.5f)
    room(f, Rect(c.x - 0.34f * k, c.y - 0.26f * k, c.x + 0.34f * k, c.y + 0.26f * k), sel = f.t > 0.64f, label = "Soggiorno")
    val pinch = f.shown(0.02f, 0.44f)
    val d = mix(0.08f, 0.24f, z)
    finger(f, Offset(c.x - d, c.y + d * 0.7f), pinch, 0f)
    finger(f, Offset(c.x + d, c.y - d * 0.7f), pinch, 0f)
    finger(f, Offset(c.x + 0.12f, c.y + 0.14f), f.shown(0.52f, 0.9f), f.tap(0.6f))
}

/** Tocco sulla stanza, poi su Info: la tendina sale. */
private fun DrawScope.openInfo(f: Frame) {
    room(f, Rect(0.18f, 0.08f, 0.82f, 0.5f), sel = f.t > 0.16f, label = "Camera")
    val strip = f.lin(0.2f, 0.28f)
    if (strip > 0f) {
        val tl = f.p(0.05f, 0.8f)
        drawRoundRect(Color.White.copy(alpha = strip), tl, Size(f.l(0.9f), f.l(0.14f)), CornerRadius(f.l(0.04f)))
        drawRoundRect(Color(0xFFADB5BD).copy(alpha = strip), tl, Size(f.l(0.9f), f.l(0.14f)), CornerRadius(f.l(0.04f)), style = Stroke(f.l(0.006f)))
        text(f, "Camera", Offset(0.26f, 0.87f), alpha = strip, size = 0.06f)
        text(f, "Info ▲", Offset(0.78f, 0.87f), color = SkSel, alpha = strip, size = 0.06f, bold = true)
    }
    val up = f.seg(0.58f, 0.78f)
    if (up > 0f) {
        val top = mix(0.95f, 0.36f, up)
        val tl = f.p(0.03f, top)
        drawRoundRect(Color.White, tl, Size(f.l(0.94f), f.l(1f - top)), CornerRadius(f.l(0.05f)))
        drawRoundRect(Color(0xFFADB5BD), tl, Size(f.l(0.94f), f.l(1f - top)), CornerRadius(f.l(0.05f)), style = Stroke(f.l(0.006f)))
        val rows = listOf("Nome · Camera", "Tipo · Camera", "Muri · 400 × 300")
        rows.forEachIndexed { i, s -> val y = top + 0.12f + i * 0.15f; if (y < 0.95f) text(f, s, Offset(0.5f, y), size = 0.06f) }
    }
    val pos = along(f.t, 0f to Offset(0.56f, 0.34f), 0.3f to Offset(0.56f, 0.34f), 0.44f to Offset(0.78f, 0.87f))
    finger(f, pos, f.shown(0.02f, 0.62f), f.tap(0.1f, 0.48f))
}

/** Si scrive una nuova lunghezza nel campo: la stanza si allarga e la quota si aggiorna. */
private fun DrawScope.wallLengths(f: Frame) {
    val grow = f.seg(0.58f, 0.8f)
    val w = mix(0.52f, 0.65f, grow)
    val left = 0.5f - w / 2
    room(f, Rect(left, 0.18f, left + w, 0.5f))
    // Quota sopra la stanza.
    val y = 0.09f
    drawLine(SkText, f.p(left, y), f.p(left + w, y), strokeWidth = f.l(0.006f))
    for (x in listOf(left, left + w)) drawLine(SkText, f.p(x, y - 0.025f), f.p(x, y + 0.025f), strokeWidth = f.l(0.006f))
    text(f, "${mix(400f, 500f, grow).roundToInt()}", Offset(0.5f, y - 0.045f), size = 0.058f)
    // Campo numerico.
    val focused = f.t in 0.14f..0.56f
    val typed = when {
        f.t < 0.2f -> "400"
        f.t < 0.28f -> ""
        f.t < 0.34f -> "5"
        f.t < 0.4f -> "50"
        else -> "500"
    }
    val tl = f.p(0.14f, 0.62f)
    drawRoundRect(Color.White, tl, Size(f.l(0.72f), f.l(0.16f)), CornerRadius(f.l(0.02f)))
    drawRoundRect(if (focused) SkSel else Color(0xFFADB5BD), tl, Size(f.l(0.72f), f.l(0.16f)), CornerRadius(f.l(0.02f)), style = Stroke(f.l(if (focused) 0.012f else 0.006f)))
    text(f, "Lato 1", Offset(0.3f, 0.7f), color = Color(0xFF868E96), size = 0.055f)
    text(f, "$typed cm", Offset(0.62f, 0.7f), bold = true)
    button(f, "OK", Offset(0.76f, 0.9f), 0.2f, pressed = f.t in 0.5f..0.58f, h = 0.1f)
    val pos = along(f.t, 0f to Offset(0.62f, 0.72f), 0.42f to Offset(0.62f, 0.72f), 0.5f to Offset(0.76f, 0.9f))
    finger(f, pos, f.shown(0.04f, 0.66f), f.tap(0.12f, 0.5f))
}

/** Si tocca "Cucina": colore e nome della stanza cambiano. */
private fun DrawScope.changeType(f: Frame) {
    val c = f.seg(0.45f, 0.6f)
    room(f, Rect(0.18f, 0.1f, 0.82f, 0.5f), fill = lerp(SkFloor, SkKitchen, c), label = if (f.t < 0.5f) "Stanza" else "Cucina")
    val chips = listOf("Camera", "Cucina", "Bagno")
    chips.forEachIndexed { i, s ->
        button(f, s, Offset(0.18f + i * 0.32f, 0.72f), 0.29f, pressed = i == 1 && f.t in 0.38f..0.46f, filled = i == 1 && f.t > 0.46f)
    }
    val pos = along(f.t, 0f to Offset(0.5f, 0.95f), 0.34f to Offset(0.5f, 0.74f))
    finger(f, pos, f.shown(0.18f, 0.66f), f.tap(0.38f))
}

/** Si prende il muro di destra e lo si trascina: la stanza si allarga. */
private fun DrawScope.moveWall(f: Frame) {
    val drag = f.seg(0.3f, 0.7f)
    val r = mix(0.6f, 0.84f, drag)
    room(f, Rect(0.16f, 0.2f, r, 0.72f), label = "${mix(400f, 560f, drag).roundToInt()} cm")
    if (f.t > 0.2f) wall(f, Offset(r, 0.2f), Offset(r, 0.72f), SkSel, 0.045f)
    text(f, "⟷", Offset(r + 0.08f, 0.3f), color = SkSel, alpha = f.lin(0.2f, 0.26f) * (1 - f.lin(0.3f, 0.34f)), size = 0.09f)
    finger(f, Offset(r, 0.46f), f.shown(0.04f, 0.8f), f.tap(0.2f))
}

/** Si trascina il pallino di un angolo: il muro si storce. */
private fun DrawScope.moveCorner(f: Frame) {
    val d = f.seg(0.3f, 0.7f)
    val br = Offset(mix(0.76f, 0.88f, d), mix(0.7f, 0.84f, d))
    polygon(f, listOf(Offset(0.16f, 0.18f), Offset(0.76f, 0.18f), br, Offset(0.16f, 0.7f)), SkFloor, sel = true)
    if (f.t in 0.2f..0.76f) drawCircle(SkSel.copy(alpha = 0.3f), f.l(0.06f), f.p(br))
    finger(f, br, f.shown(0.04f, 0.8f), f.tap(0.2f))
}

/** "+ Porte" → tipo di porta → tocco sul muro: la porta compare. */
private fun DrawScope.addDoor(f: Frame) {
    room(f, Rect(0.14f, 0.14f, 0.86f, 0.6f))
    door(f, 0.43f, 0.14f, 0.18f, SkDoor, open = f.seg(0.6f, 0.72f))
    button(f, "+ Porte", Offset(0.3f, 0.88f), 0.4f, pressed = f.t in 0.1f..0.2f)
    menu(f, "Porta 1 anta", Offset(0.36f, 0.72f), 0.5f, highlight = f.t in 0.34f..0.44f, alpha = f.lin(0.18f, 0.22f) * (1 - f.lin(0.44f, 0.48f)))
    val pos = along(f.t, 0f to Offset(0.3f, 0.88f), 0.24f to Offset(0.3f, 0.88f), 0.32f to Offset(0.36f, 0.72f), 0.44f to Offset(0.36f, 0.72f), 0.54f to Offset(0.52f, 0.15f))
    finger(f, pos, f.shown(0.02f, 0.76f), f.tap(0.12f, 0.35f, 0.56f))
}

/** La porta scorre lungo il muro. */
private fun DrawScope.moveDoor(f: Frame) {
    room(f, Rect(0.14f, 0.14f, 0.86f, 0.68f))
    val x = mix(0.22f, 0.58f, f.seg(0.28f, 0.7f))
    door(f, x, 0.14f, 0.2f, if (f.t > 0.18f) SkSel else SkDoor)
    finger(f, Offset(x + 0.1f, 0.2f), f.shown(0.04f, 0.8f), f.tap(0.18f))
}

/** "+ Impianti" → Presa → tocco sul muro: compare la presa. */
private fun DrawScope.addFixture(f: Frame) {
    room(f, Rect(0.14f, 0.12f, 0.86f, 0.6f))
    outlet(f, Offset(0.19f, 0.36f), f.seg(0.54f, 0.64f))
    button(f, "+ Impianti", Offset(0.32f, 0.88f), 0.46f, pressed = f.t in 0.1f..0.2f)
    menu(f, "Presa di corrente", Offset(0.4f, 0.72f), 0.6f, highlight = f.t in 0.32f..0.42f, alpha = f.lin(0.18f, 0.22f) * (1 - f.lin(0.42f, 0.46f)))
    val pos = along(f.t, 0f to Offset(0.32f, 0.88f), 0.22f to Offset(0.32f, 0.88f), 0.3f to Offset(0.4f, 0.72f), 0.42f to Offset(0.4f, 0.72f), 0.5f to Offset(0.15f, 0.36f))
    finger(f, pos, f.shown(0.02f, 0.74f), f.tap(0.12f, 0.33f, 0.52f))
}

/** "+ Aggiungi stanza": la nuova stanza compare accanto. */
private fun DrawScope.addRoom(f: Frame) {
    room(f, Rect(0.06f, 0.2f, 0.46f, 0.62f), fill = SkKitchen, label = "Cucina")
    val a = f.seg(0.3f, 0.45f)
    if (a > 0f) {
        val k = mix(0.7f, 1f, a)
        val c = Offset(0.74f, 0.42f)
        room(f, Rect(c.x - 0.18f * k, c.y - 0.18f * k, c.x + 0.18f * k, c.y + 0.18f * k), fill = SkRoom2, sel = true, label = "Stanza 2", alpha = a)
    }
    button(f, "+ Aggiungi stanza", Offset(0.5f, 0.88f), 0.72f, pressed = f.t in 0.12f..0.22f, filled = true)
    finger(f, Offset(0.5f, 0.88f), f.shown(0.02f, 0.3f), f.tap(0.14f))
}

/** La stanza nuova si trascina dal nome verso l'altra e si aggancia al suo muro. */
private fun DrawScope.moveRoom(f: Frame) {
    room(f, Rect(0.06f, 0.22f, 0.44f, 0.62f), fill = SkKitchen, label = "Cucina")
    val left = mix(mix(0.6f, 0.48f, f.seg(0.2f, 0.58f)), 0.44f, f.seg(0.62f, 0.66f))
    room(f, Rect(left, 0.26f, left + 0.34f, 0.62f), fill = SkRoom2, sel = true, label = "Stanza 2")
    val snap = f.lin(0.66f, 0.7f) * (1 - f.lin(0.86f, 0.94f))
    if (snap > 0f) wall(f, Offset(0.44f, 0.26f), Offset(0.44f, 0.62f), SkOk.copy(alpha = snap), 0.05f)
    finger(f, Offset(left + 0.17f, 0.49f), f.shown(0.04f, 0.8f), f.tap(0.14f))
}

/** Si sposta un muro per sbaglio, poi ↶ Annulla lo rimette com'era. */
private fun DrawScope.undo(f: Frame) {
    val out = mix(0.62f, 0.84f, f.seg(0.12f, 0.36f))
    val r = mix(out, 0.62f, f.seg(0.62f, 0.74f))
    room(f, Rect(0.16f, 0.26f, r, 0.76f))
    if (f.t in 0.08f..0.5f) wall(f, Offset(r, 0.26f), Offset(r, 0.76f), SkSel, 0.045f)
    button(f, "↶ Annulla", Offset(0.28f, 0.1f), 0.44f, pressed = f.t in 0.56f..0.66f, h = 0.1f)
    val pos = along(f.t, 0f to Offset(0.62f, 0.5f), 0.12f to Offset(0.62f, 0.5f), 0.36f to Offset(0.84f, 0.5f), 0.52f to Offset(0.28f, 0.1f))
    finger(f, pos, f.shown(0.02f, 0.8f), f.tap(0.08f, 0.57f))
}

/** "📏 Metro": compare il righello, se ne trascina l'estremità e la misura cresce. */
private fun DrawScope.ruler(f: Frame) {
    button(f, "📏 Metro", Offset(0.5f, 0.88f), 0.44f, pressed = f.t in 0.08f..0.16f)
    val a = f.seg(0.14f, 0.22f)
    val y = 0.46f
    val ax = 0.14f
    val bx = mix(0.5f, 0.86f, f.seg(0.36f, 0.72f))
    if (a > 0f) {
        drawLine(Color(0xFFFCC419).copy(alpha = a), f.p(ax, y), f.p(bx, y), strokeWidth = f.l(0.05f))
        var x = ax
        var i = 0
        while (x <= bx) {
            val h = if (i % 5 == 0) 0.025f else 0.014f
            drawLine(SkText.copy(alpha = a), f.p(x, y - 0.025f), f.p(x, y - 0.025f + h), strokeWidth = f.l(0.005f))
            x += 0.03f; i++
        }
        for (e in listOf(ax, bx)) drawCircle(SkSel.copy(alpha = a), f.l(0.025f), f.p(e, y))
        text(f, "${((bx - ax) * 500).roundToInt()} cm", Offset((ax + bx) / 2, y - 0.08f), bold = true, alpha = a)
    }
    val pos = if (f.t < 0.34f) along(f.t, 0f to Offset(0.5f, 0.88f), 0.2f to Offset(0.5f, 0.88f), 0.32f to Offset(0.5f, y))
    else Offset(bx, y)
    finger(f, pos, f.shown(0.02f, 0.8f), f.tap(0.09f, 0.34f))
}

/** Un segno di spunta che si disegna. */
private fun DrawScope.done(f: Frame) {
    val k = f.seg(0.05f, 0.25f)
    drawCircle(SkOk, f.l(0.3f * k), f.p(0.5f, 0.5f))
    val a = Offset(0.36f, 0.5f)
    val b = Offset(0.46f, 0.6f)
    val c = Offset(0.66f, 0.4f)
    val p1 = f.seg(0.25f, 0.4f)
    val p2 = f.seg(0.4f, 0.58f)
    if (p1 > 0f) drawLine(Color.White, f.p(a), f.p(mix(a, b, p1)), strokeWidth = f.l(0.05f), cap = StrokeCap.Round)
    if (p2 > 0f) drawLine(Color.White, f.p(b), f.p(mix(b, c, p2)), strokeWidth = f.l(0.05f), cap = StrokeCap.Round)
}

// ---- 3D ----

private class P3(val x: Float, val y: Float, val z: Float)

/** Proiezione semplice dall'alto e di lato, dopo una rotazione `ang` attorno all'asse verticale. */
private fun project(f: Frame, q: P3, ang: Float, cy: Float = 0.6f, scale: Float = 0.34f): Pair<Offset, Float> {
    val xr = q.x * cos(ang) - q.z * sin(ang)
    val zr = q.x * sin(ang) + q.z * cos(ang)
    return f.p(0.5f + xr * scale, cy - q.y * scale * 0.95f + zr * scale * 0.45f) to zr
}

/**
 * Facce disegnate dalla più lontana alla più vicina; le pareti in primo piano sono trasparenti
 * così si vede dentro la stanza.
 */
private fun DrawScope.solid(f: Frame, faces: List<Pair<List<P3>, Color>>, ang: Float) {
    val projected = faces.map { (pts, color) ->
        val pr = pts.map { project(f, it, ang) }
        Triple(pr.map { it.first }, pr.map { it.second }.average().toFloat(), color to pts.all { it.y == 0f })
    }.sortedBy { if (it.third.second) -10f else it.second }
    for ((pts, depth, info) in projected) {
        val (color, floor) = info
        val path = Path().apply { pts.forEachIndexed { i, c -> if (i == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y) }; close() }
        val near = !floor && depth > 0.15f
        drawPath(path, color.copy(alpha = if (near) 0.4f else 1f))
        drawPath(path, SkWall.copy(alpha = if (near) 0.7f else 1f), style = Stroke(f.l(if (near) 0.008f else 0.011f)))
    }
}

private fun quad(a: P3, b: P3, c: P3, d: P3) = listOf(a, b, c, d)

/** Stanza che ruota; il dito trascina in orizzontale. */
private fun DrawScope.view3d(f: Frame) {
    val ang = f.t * 2 * PI.toFloat()
    val h = 0.7f
    val xs = 1f
    val zs = 0.75f
    val wallColor = Color(0xFFCED4DA)
    solid(
        f,
        listOf(
            quad(P3(-xs, 0f, -zs), P3(xs, 0f, -zs), P3(xs, 0f, zs), P3(-xs, 0f, zs)) to Color(0xFFF8E4C8),
            quad(P3(-xs, 0f, -zs), P3(xs, 0f, -zs), P3(xs, h, -zs), P3(-xs, h, -zs)) to wallColor,
            quad(P3(-xs, 0f, zs), P3(xs, 0f, zs), P3(xs, h, zs), P3(-xs, h, zs)) to wallColor,
            quad(P3(-xs, 0f, -zs), P3(-xs, 0f, zs), P3(-xs, h, zs), P3(-xs, h, -zs)) to wallColor,
            quad(P3(xs, 0f, -zs), P3(xs, 0f, zs), P3(xs, h, zs), P3(xs, h, -zs)) to wallColor,
        ),
        ang,
    )
    finger(f, Offset(mix(0.25f, 0.75f, f.lin(0.05f, 0.95f)), 0.92f), f.shown(0.0f, 0.9f), 0f)
}

/** Mansarda in 3D: muretto basso, pareti laterali tagliate in diagonale; oscilla a destra e sinistra. */
private fun DrawScope.attic3d(f: Frame) {
    val ang = 0.5f + 0.7f * sin(f.t * 2 * PI.toFloat())
    val xs = 1f
    val zs = 0.75f
    val high = 0.95f
    val low = high * 120f / 270f
    val start = 0.15f
    val wallColor = Color(0xFFCED4DA)
    fun side(z: Float) = listOf(P3(-xs, 0f, z), P3(-xs, high, z), P3(start, high, z), P3(xs, low, z), P3(xs, 0f, z))
    solid(
        f,
        listOf(
            quad(P3(-xs, 0f, -zs), P3(xs, 0f, -zs), P3(xs, 0f, zs), P3(-xs, 0f, zs)) to Color(0xFFF8E4C8),
            side(-zs) to wallColor,
            side(zs) to wallColor,
            quad(P3(-xs, 0f, -zs), P3(-xs, 0f, zs), P3(-xs, high, zs), P3(-xs, high, -zs)) to wallColor,
            quad(P3(xs, 0f, -zs), P3(xs, 0f, zs), P3(xs, low, zs), P3(xs, low, -zs)) to Color(0xFFE5D0EC),
        ),
        ang,
    )
}

// ---- Mansarda ----

/**
 * Parete laterale vista di fronte: alta a sinistra, dal punto `start` (0..1 sulla scena) scende fino al
 * muretto a destra, alto `low` volte la parete alta. `handle` mostra il pallino e la linea di inizio
 * discesa, `measure` la distanza dal muretto.
 */
private fun DrawScope.elevation(f: Frame, low: Float, start: Float, handle: Boolean = false, measure: Boolean = false) {
    val l = 0.14f
    val r = 0.86f
    val floor = 0.8f
    val top = 0.24f
    val lowY = floor - (floor - top) * low
    val pts = listOf(Offset(l, floor), Offset(l, top), Offset(start, top), Offset(r, lowY), Offset(r, floor))
    val path = Path().apply { pts.forEachIndexed { i, q -> val c = f.p(q); if (i == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y) }; close() }
    drawPath(path, SkElevation)
    drawPath(path, SkWall, style = Stroke(f.l(0.012f)))
    text(f, "270", Offset(0.07f, (top + floor) / 2), size = 0.055f)
    text(f, "${(270 * low).roundToInt()}", Offset(0.93f, (lowY + floor) / 2), color = SkLow, size = 0.055f)
    if (handle) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(f.l(0.025f), f.l(0.018f)))
        drawLine(SkCut, f.p(start, top), f.p(start, floor), strokeWidth = f.l(0.008f), pathEffect = dash)
        drawCircle(SkCut, f.l(0.028f), f.p(start, top))
    }
    if (measure) {
        val y = floor - 0.06f
        drawLine(SkCut, f.p(start, y), f.p(r, y), strokeWidth = f.l(0.006f))
        text(f, "${((r - start) / (r - l) * 600).roundToInt()} cm", Offset((start + r) / 2, y - 0.045f), color = SkCut, size = 0.052f, bold = true)
    }
}

private val AtticRect = Rect(0.12f, 0.2f, 0.74f, 0.66f)
private const val AtticCutX = 0.5f

/** Pianta della mansarda: il muretto a destra (viola quando è basso), le linee di inizio discesa. */
private fun DrawScope.atticPlan(f: Frame, lowColor: Color, lowLabel: String?, selected: Int?, cut1: Float, cut2: Float) {
    val r = AtticRect
    room(f, r)
    wall(f, Offset(r.right, r.top), Offset(r.right, r.bottom), lowColor)
    when (selected) {
        0 -> wall(f, Offset(r.left, r.top), Offset(r.right, r.top), SkSel, 0.045f)
        1 -> wall(f, Offset(r.right, r.top), Offset(r.right, r.bottom), SkSel, 0.045f)
        2 -> wall(f, Offset(r.left, r.bottom), Offset(r.right, r.bottom), SkSel, 0.045f)
    }
    lowLabel?.let {
        text(f, "muretto", Offset(0.87f, r.center.y - 0.05f), color = lowColor, size = 0.052f)
        text(f, it, Offset(0.87f, r.center.y + 0.04f), color = lowColor, size = 0.058f, bold = true)
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(f.l(0.025f), f.l(0.018f)))
    val mid = r.center.y
    if (cut1 > 0f) {
        drawLine(SkCut, f.p(AtticCutX, r.top), f.p(AtticCutX, mix(r.top, mid, cut1)), strokeWidth = f.l(0.008f), pathEffect = dash)
        drawCircle(SkCut.copy(alpha = cut1), f.l(0.026f), f.p(AtticCutX, r.top))
    }
    if (cut2 > 0f) {
        drawLine(SkCut, f.p(AtticCutX, r.bottom), f.p(AtticCutX, mix(r.bottom, mid, cut2)), strokeWidth = f.l(0.008f), pathEffect = dash)
        drawCircle(SkCut.copy(alpha = cut2), f.l(0.026f), f.p(AtticCutX, r.bottom))
    }
}

/** La stanza si disegna: prima la larghezza, poi la profondità. */
private fun DrawScope.atticRoom(f: Frame) {
    val r = AtticRect
    val w = mix(0.06f, r.width, f.seg(0.08f, 0.4f))
    val h = mix(0.06f, r.height, f.seg(0.4f, 0.7f))
    room(f, Rect(r.left, r.top, r.left + w, r.top + h), sel = f.t < 0.76f, label = if (f.t > 0.7f) "270 cm" else null)
    finger(f, Offset(r.left + w, r.top + h), f.shown(0.02f, 0.74f), 0f)
}

/** Si tocca il muretto e se ne abbassa l'altezza: da 270 a 120 cm, il muro diventa viola. */
private fun DrawScope.atticLow(f: Frame) {
    val k = f.seg(0.3f, 0.65f)
    val value = mix(270f, 120f, k).roundToInt()
    atticPlan(f, lerp(SkWall, SkLow, k), "$value cm", selected = if (f.t in 0.12f..0.3f) 1 else null, cut1 = 0f, cut2 = 0f)
    button(f, "Altezza muro: $value", Offset(0.5f, 0.86f), 0.72f, pressed = f.t in 0.22f..0.68f)
    finger(f, Offset(AtticRect.right, AtticRect.center.y), f.shown(0.02f, 0.2f), f.tap(0.1f))
}

/** Si tocca una parete laterale e si attiva il taglio: compare la linea di inizio discesa. */
private fun DrawScope.atticCut(f: Frame) {
    atticPlan(f, SkLow, "120 cm", selected = if (f.t > 0.12f) 0 else null, cut1 = f.seg(0.45f, 0.65f), cut2 = 0f)
    button(f, "Taglio diagonale", Offset(0.5f, 0.86f), 0.66f, pressed = f.t in 0.32f..0.4f, filled = f.t > 0.4f)
    val pos = along(f.t, 0f to Offset(0.32f, AtticRect.top), 0.18f to Offset(0.32f, AtticRect.top), 0.3f to Offset(0.5f, 0.86f))
    finger(f, pos, f.shown(0.02f, 0.6f), f.tap(0.1f, 0.34f))
}

/** Lo stesso sulla parete di fronte. */
private fun DrawScope.atticRepeat(f: Frame) {
    atticPlan(f, SkLow, "120 cm", selected = if (f.t > 0.12f) 2 else null, cut1 = 1f, cut2 = f.seg(0.45f, 0.65f))
    button(f, "Taglio diagonale", Offset(0.5f, 0.86f), 0.66f, pressed = f.t in 0.32f..0.4f, filled = f.t > 0.4f)
    val pos = along(f.t, 0f to Offset(0.32f, AtticRect.bottom), 0.18f to Offset(0.32f, AtticRect.bottom), 0.3f to Offset(0.5f, 0.86f))
    finger(f, pos, f.shown(0.02f, 0.6f), f.tap(0.1f, 0.34f))
}

/** Una finestra sotto la parte bassa sfora (avviso rosso); spostata verso la parte alta, ci sta. */
private fun DrawScope.atticCheck(f: Frame) {
    val start = 0.5f
    elevation(f, low = 120f / 270f, start = start)
    val r = 0.86f
    val floor = 0.8f
    val top = 0.24f
    val lowY = floor - (floor - top) * 120f / 270f
    fun topAt(x: Float) = if (x <= start) top else mix(top, lowY, (x - start) / (r - start))
    val w = 0.14f
    val x = mix(0.64f, 0.3f, f.seg(0.4f, 0.72f))
    val wy = 0.34f
    val tl = f.p(x, wy)
    drawRect(Color(0xFFD0EBFF), tl, Size(f.l(w), f.l(0.26f)))
    drawRect(SkWindow, tl, Size(f.l(w), f.l(0.26f)), style = Stroke(f.l(0.01f)))
    val collides = wy < topAt(x + w) - 0.005f
    if (collides) {
        val blink = 0.6f + 0.4f * sin(f.t * 12 * PI.toFloat())
        val c = Offset(x + w / 2, wy - 0.07f)
        drawCircle(SkWarn.copy(alpha = blink), f.l(0.04f), f.p(c))
        text(f, "!", c, color = Color.White, bold = true, size = 0.06f)
    } else if (f.t > 0.72f) {
        text(f, "✓", Offset(x + w / 2, wy - 0.07f), color = SkOk, bold = true, size = 0.08f)
    }
    finger(f, Offset(x + w / 2, wy + 0.13f), f.shown(0.3f, 0.8f), f.tap(0.36f))
}
