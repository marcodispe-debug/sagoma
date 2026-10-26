package com.sagoma.planimetria.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.sagoma.planimetria.editor.Course
import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.CourseUi
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.GuideHint
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Colore della guida interattiva: riquadro sul comando da usare, segni sulla pianta, bordo del fumetto. */
internal val GuideOrange = Color(0xFFFF8A00)

/**
 * Dove sono sullo schermo i comandi che la guida interattiva sa indicare: ogni comando segnato con [guide] scrive qui il suo
 * riquadro (in coordinate della finestra) e se ha già fatto il suo (menu aperto, tipo scelto, misura scritta). La guida
 * evidenzia la prima indicazione del passo il cui comando è sullo schermo.
 */
@Stable
internal class GuideTargets {
    data class Entry(val bounds: Rect = Rect.Zero, val root: LayoutCoordinates? = null, val done: Boolean = false, val value: String? = null)

    val entries = mutableStateMapOf<GuideTarget, Entry>()

    /** Il comando evidenziato ora (riquadro arancione che pulsa). */
    var active by mutableStateOf<GuideTarget?>(null)

    /** Radice della finestra principale: i comandi con un'altra radice sono in una finestra di dialogo o in un menu. */
    var mainRoot by mutableStateOf<LayoutCoordinates?>(null)

    /** Riquadro della pianta, in coordinate della finestra (per i segni sulla pianta). */
    var canvas by mutableStateOf<Rect?>(null)

    fun update(target: GuideTarget, change: (Entry) -> Entry) {
        val old = entries[target] ?: Entry()
        val new = change(old)
        if (new != old) entries[target] = new
    }

    fun inPopup(e: Entry) = e.root != null && mainRoot != null && e.root !== mainRoot

    fun mainRootFrom(c: LayoutCoordinates) {
        val root = c.findRootCoordinates()
        if (mainRoot !== root) mainRoot = root
    }
}

/** Il registro dei comandi del progetto aperto (null fuori dall'editor: [guide] non fa niente). */
internal val LocalGuide = staticCompositionLocalOf<GuideTargets?> { null }

/**
 * Segna il comando `target` per la guida interattiva: quando è il comando da usare ha intorno il riquadro arancione e, se sta in
 * una lista che scorre, viene portato in vista. `done`: ha già fatto il suo (per esempio il menu è già aperto); `value`: il testo
 * scritto in un campo, che la guida confronta con la misura da scrivere.
 */
internal fun Modifier.guide(target: GuideTarget?, done: Boolean = false, value: String? = null): Modifier =
    if (target == null) this else this.then(
        Modifier.composed {
            val g = LocalGuide.current ?: return@composed Modifier
            DisposableEffect(g, target) { onDispose { g.entries.remove(target) } }
            SideEffect { g.update(target) { it.copy(done = done, value = value) } }
            val active = g.active == target
            val bring = remember { BringIntoViewRequester() }
            LaunchedEffect(active) { if (active) runCatching { bring.bringIntoView() } }
            Modifier
                .bringIntoViewRequester(bring)
                .onGloballyPositioned { c -> g.update(target) { it.copy(bounds = c.boundsInWindow(), root = c.findRootCoordinates()) } }
                // Il nome nell'albero dell'accessibilità serve anche alle prove, che cliccano dove indica la guida.
                .then(if (active) Modifier.guideRing().testTag("guida:$target") else Modifier)
        },
    )

/** La pianta: i segni della guida si disegnano nelle sue coordinate. */
internal fun Modifier.guideCanvas(): Modifier = composed {
    val g = LocalGuide.current ?: return@composed Modifier
    Modifier.testTag("pianta").onGloballyPositioned { c ->
        g.mainRootFrom(c)
        val b = c.boundsInWindow()
        if (g.canvas != b) g.canvas = b
    }
}

@Composable
private fun Modifier.guideRing(): Modifier {
    val pulse by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
    return drawWithContent {
        drawContent()
        val w = (2.5f + 2f * pulse).dp.toPx()
        drawRoundRect(
            GuideOrange.copy(alpha = 0.6f + 0.4f * pulse),
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(w),
        )
    }
}

/** Stesso numero, scritto con la virgola o il punto ("330", "330,0"). */
private fun sameNumber(text: String?, expect: String): Boolean {
    val a = text?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return false
    val b = expect.replace(',', '.').toDoubleOrNull() ?: return false
    return kotlin.math.abs(a - b) < 0.01
}

/**
 * La guida interattiva sopra l'editor: lo schermo scurito tranne il comando da usare, i segni sulla pianta (dove cliccare, da
 * dove a dove trascinare) e il fumetto con la spiegazione. Non ferma i clic: si può sempre fare altro, e il passo si completa
 * facendo l'azione (o con "Salta passo").
 */
@Composable
internal fun CourseGuide(
    course: CourseUi,
    state: EditorUiState,
    vm: EditorViewModel,
    onLesson: (Lesson) -> Unit,
    onOpenProjects: () -> Unit,
) {
    val g = LocalGuide.current ?: return
    val hints = Course.hints(course.step, state)
    // Con una finestra o un menu aperti valgono solo i comandi lì dentro: quelli sotto non si possono cliccare.
    val popupOpen = g.entries.values.any { g.inPopup(it) && !it.bounds.isEmpty }
    fun available(h: GuideHint): Boolean {
        val t = h.target ?: return true
        val e = g.entries[t] ?: return false
        // Un campo fuori dalla parte visibile di una lista che scorre ha il riquadro vuoto: va bene lo stesso, quando è il suo
        // turno la lista scorre fino a lui.
        if ((e.bounds.isEmpty && t !is GuideTarget.Field) || (popupOpen && !g.inPopup(e))) return false
        return !(e.done || h.expect?.let { sameNumber(e.value, it) } == true)
    }
    val hint = hints.firstOrNull(::available)
    val target = hint?.target
    SideEffect { if (g.active != target) g.active = target }
    DisposableEffect(g) { onDispose { g.active = null } }
    val entry = target?.let { g.entries[it] }
    val inPopup = entry != null && g.inPopup(entry)
    val canvas = g.canvas
    val marks = hint?.marks.orEmpty()
    fun screen(p: Vec2): Offset = Offset(state.camera.toScreenX(p.x), state.camera.toScreenY(p.y)) + (canvas?.topLeft ?: Offset.Zero)

    val pulse by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        Modifier.fillMaxSize().onGloballyPositioned { c ->
            g.mainRootFrom(c)
            origin = c.positionInWindow()
        },
    ) {
        // Lo schermo scurito con un buco sul comando (nelle finestre di dialogo c'è già il loro velo).
        if (entry != null && !inPopup) {
            val hole = entry.bounds.translate(-origin).inflate(6.dp.toPx())
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(hole, CornerRadius(14.dp.toPx())))
            }
            drawPath(path, Color.Black.copy(alpha = 0.45f))
        }
        if (canvas != null && marks.isNotEmpty()) {
            val clip = canvas.translate(-origin)
            drawContext.canvas.save()
            drawContext.canvas.clipRect(clip)
            drawMarks(marks.map { m -> m.points.map { screen(it) - origin } to m.goal }, pulse)
            drawContext.canvas.restore()
        }
    }

    // Il fumetto accanto al comando (o ai segni sulla pianta), senza coprirli. Rifatto a ogni indicazione, così resta sopra alle
    // finestre di dialogo e ai menu appena aperti.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val near = with(density) { 70.dp.toPx() }
    // I comandi segnati stanno dentro la finestra di dialogo, a circa 24 dp dal bordo.
    val dialogPad = with(density) { 24.dp.toPx() }
    val avoid: Rect? = when {
        // In una finestra di dialogo: tutta la finestra (i suoi comandi segnati), il fumetto le sta accanto.
        entry != null && inPopup -> union(g.entries.values.filter { it.root === entry.root && !it.bounds.isEmpty }.map { it.bounds })?.inflate(dialogPad)
        // Sulla finestra principale: il comando e quelli segnati lì vicino (per esempio tutta la scheda dei muri).
        entry != null -> nearby(entry.bounds, g.entries.values.filter { !g.inPopup(it) && !it.bounds.isEmpty }.map { it.bounds }, near)
        // I segni con il loro cerchio intorno (circa 24 dp).
        canvas != null && marks.isNotEmpty() -> (marks.flatMap { it.points } + hint?.keepVisible.orEmpty()).map(::screen).let { pts ->
            Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y }).inflate(dialogPad)
        }
        // Una scheda senza comando con una finestra aperta (il benvenuto sopra la scelta della forma): non coprire la finestra.
        popupOpen -> union(g.entries.values.filter { g.inPopup(it) && !it.bounds.isEmpty }.map { it.bounds })?.inflate(dialogPad)
        else -> null
    }
    val marginPx = with(density) { 16.dp.roundToPx() }
    key(course.step, target, popupOpen) {
        Popup(popupPositionProvider = BubblePosition(avoid, marginPx), properties = PopupProperties(focusable = false)) {
            CourseBubble(course, hint, vm, onLesson, onOpenProjects)
        }
    }
}

private fun union(rects: List<Rect>): Rect? =
    rects.reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }

/** `start` allargato a tutti i riquadri a meno di `gap` da lui (e da quelli già presi). */
private fun nearby(start: Rect, rects: List<Rect>, gap: Float): Rect {
    var r = start
    val left = rects.toMutableList()
    while (true) {
        val close = left.filter { it.overlaps(r.inflate(gap)) }
        if (close.isEmpty()) return r
        left -= close.toSet()
        r = union(close + r)!!
    }
}

/** Segni sulla pianta (già in pixel): punti da cliccare o da cui trascinare che pulsano, arrivi tratteggiati e la freccia. */
private fun DrawScope.drawMarks(marks: List<Pair<List<Offset>, Boolean>>, pulse: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 7.dp.toPx()))
    val starts = marks.filter { !it.second }.flatMap { it.first }
    val goals = marks.filter { it.second }.map { it.first }
    for (pts in goals) {
        when {
            pts.size >= 3 -> {
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (p in pts.drop(1)) lineTo(p.x, p.y)
                    close()
                }
                drawPath(path, GuideOrange.copy(alpha = 0.12f))
                drawPath(path, GuideOrange, style = Stroke(3.dp.toPx(), pathEffect = dash))
            }
            pts.size == 2 -> drawLine(GuideOrange, pts[0], pts[1], 3.dp.toPx(), pathEffect = dash)
            pts.size == 1 -> {
                drawCircle(GuideOrange, 16.dp.toPx(), pts[0], style = Stroke(3.dp.toPx(), pathEffect = dash))
                drawCircle(GuideOrange, 4.dp.toPx(), pts[0])
            }
        }
    }
    // Da dove a dove: dal primo punto al centro dell'arrivo.
    val from = starts.firstOrNull()
    val to = goals.firstOrNull()?.let { pts -> Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat()) }
    if (from != null && to != null && (to - from).getDistance() > 30.dp.toPx()) {
        drawLine(GuideOrange, from, to, 2.5f.dp.toPx(), pathEffect = dash)
        val a = atan2(to.y - from.y, to.x - from.x)
        val l = 14.dp.toPx()
        for (d in listOf(2.6f, -2.6f)) drawLine(GuideOrange, to, to + Offset(cos(a + d) * l, sin(a + d) * l), 3.dp.toPx())
    }
    for (p in starts) {
        drawCircle(GuideOrange.copy(alpha = 0.25f + 0.2f * pulse), (14f + 10f * pulse).dp.toPx(), p)
        drawCircle(GuideOrange, 9.dp.toPx(), p, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 3.dp.toPx(), p)
    }
}

/**
 * Posto del fumetto: a destra di ciò che non deve coprire, se c'è spazio, altrimenti a sinistra, sotto o sopra; senza niente
 * da evitare in alto a destra, dove copre meno la pianta.
 */
private class BubblePosition(private val avoid: Rect?, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        fun clampX(x: Int) = x.coerceIn(margin, (windowSize.width - w - margin).coerceAtLeast(margin))
        fun clampY(y: Int) = y.coerceIn(margin, (windowSize.height - h - margin).coerceAtLeast(margin))
        val r = avoid ?: return IntOffset(clampX(windowSize.width - w - margin), clampY(windowSize.height / 9))
        val cy = clampY((r.center.y - h / 2f).roundToInt())
        val cx = clampX((r.center.x - w / 2f).roundToInt())
        val right = r.right.roundToInt() + margin
        if (right + w <= windowSize.width - margin) return IntOffset(right, cy)
        val left = r.left.roundToInt() - margin - w
        if (left >= margin) return IntOffset(left, cy)
        val below = r.bottom.roundToInt() + margin
        if (below + h <= windowSize.height - margin) return IntOffset(cx, below)
        val above = r.top.roundToInt() - margin - h
        if (above >= margin) return IntOffset(cx, above)
        return IntOffset(clampX(windowSize.width - w - margin), clampY(windowSize.height - h - margin))
    }
}

/** Il fumetto della guida: lezione e passo, cosa fare, e i pulsanti per andare avanti o uscire. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseBubble(course: CourseUi, hint: GuideHint?, vm: EditorViewModel, onLesson: (Lesson) -> Unit, onOpenProjects: () -> Unit) {
    val c = T.corso
    val step = course.step
    val onColor = MaterialTheme.colorScheme.onSurface
    Surface(
        // Il benvenuto è più largo, per la piantina della casa da disegnare.
        Modifier.widthIn(max = if (step == CourseStep.Welcome) 420.dp else 360.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, GuideOrange),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp)) {
            Text(
                c.intestazione(course.lesson, course.lessons, c.lezione(step.lesson)),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB85F00),
            )
            if (step.action) {
                Text(c.passo(course.number, course.total), style = MaterialTheme.typography.labelSmall, color = onColor.copy(alpha = 0.7f))
            } else {
                Text(c.titoloScheda(step), style = MaterialTheme.typography.titleMedium, color = onColor, modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                if (step.action) hint?.text ?: c.nienteDaIndicare else c.testoScheda(step),
                style = MaterialTheme.typography.bodyMedium,
                color = onColor,
                modifier = Modifier.padding(top = 4.dp, end = 6.dp),
            )
            course.detail?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
            // Al benvenuto la piantina della casa da disegnare, e si può andare dritti a una lezione (l'ultima si fa nel suo progetto).
            if (step == CourseStep.Welcome) {
                val measurer = rememberTextMeasurer()
                val plan = remember(measurer) { guidePlanBitmap(measurer, overview = true) }
                Image(
                    plan,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 8.dp, end = 6.dp).fillMaxWidth().height(240.dp)
                        .border(1.dp, onColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(4.dp),
                )
                Text(c.vaiAllaLezione, style = MaterialTheme.typography.labelMedium, color = onColor.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (l in Lesson.entries.drop(1)) OutlinedButton(
                        onClick = { if (l == Lesson.OverPlan) { vm.leaveCourse(); onLesson(l) } else vm.startCourse(l) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(36.dp),
                    ) { Text("${l.ordinal + 1}") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val quiet = onColor.copy(alpha = 0.7f)
                when (step) {
                    CourseStep.Welcome -> {
                        TextButton(onClick = { vm.finishCourse(completed = false) }) { Text(T.editor.noGrazie, color = quiet) }
                        TextButton(onClick = vm::nextCourseStep) { Text(T.editor.inizia) }
                    }
                    CourseStep.ElementsDone -> {
                        TextButton(onClick = { vm.finishCourse() }) { Text(c.bastaCosi, color = quiet) }
                        TextButton(onClick = { vm.leaveCourse(); onLesson(Lesson.OverPlan) }) { Text(c.apriProgettoNuovo) }
                    }
                    CourseStep.Finished -> {
                        TextButton(onClick = { vm.finishCourse() }) { Text(T.comuni.fine, color = quiet) }
                        TextButton(onClick = { vm.finishCourse(); onOpenProjects() }) { Text(c.vaiAiProgetti) }
                    }
                    else -> if (step.action) {
                        TextButton(onClick = { vm.finishCourse(completed = false) }) { Text(T.comuni.esci, color = quiet) }
                        TextButton(onClick = vm::nextCourseStep) { Text(T.editor.saltaPasso) }
                    } else {
                        TextButton(onClick = { vm.finishCourse(completed = false) }) { Text(T.comuni.esci, color = quiet) }
                        TextButton(onClick = vm::nextCourseStep) { Text(c.avanti) }
                    }
                }
            }
        }
    }
}
