package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val StairLine = Color(0xFF495057)
private val StairFill = Color(0xFFF3EEE6)
private val StairSelected = Color(0xFF1971C2)
private val GhostColor = Color(0xFFADB5BD)
private val VoidFill = Color(0xFFE9ECEF)

private fun Camera.px(v: Vec2) = Offset(toScreenX(v.x), toScreenY(v.y))

private fun polyPath(pts: List<Vec2>, cam: Camera) = Path().apply {
    pts.forEachIndexed { i, p -> val o = cam.px(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

/**
 * Il piano di sotto in trasparenza: il contorno dei muri in grigio tratteggiato, sotto la pianta del piano
 * corrente, per allineare i muri dei piani.
 */
internal fun DrawScope.drawFloorBelow(below: FloorPlan?, cam: Camera) {
    below ?: return
    val w = (Room.WALL_THICKNESS * cam.scale).toFloat().coerceIn(1.5f, 6.dp.toPx())
    val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
    for (r in below.rooms) {
        drawPath(polyPath(r.points, cam), GhostColor.copy(alpha = 0.7f), style = Stroke(w, join = StrokeJoin.Miter, pathEffect = dash))
    }
}

/** Scale del piano (simbolo completo) e, dal piano di sotto, il vuoto delle scale che arrivano qui. */
internal fun DrawScope.drawStairs(state: EditorUiState, measurer: TextMeasurer) {
    val cam = state.camera
    val walls = state.plan.rooms.flatMap { r -> (0 until r.wallCount).map { r.wallStart(it) to r.wallEnd(it) } }
    val selectedWell = (state.selection as? Selection.StairWell)?.stairId
    state.planBelow?.let { below ->
        for (s in below.stairs) drawStairArrival(s, state.levelHeightBelow, cam, measurer, walls, s.id == selectedWell)
    }
    val selected = (state.selection as? Selection.Stair)?.stairId
    for (s in state.plan.stairs) drawStair(s, state.levelHeight, cam, measurer, s.id == selected)
}

/**
 * Simbolo della scala: pedate e pianerottoli, il contorno, la linea di salita con il pallino alla
 * partenza e la freccia all'arrivo, e la scritta "sale".
 */
internal fun DrawScope.drawStair(s: Stair, rise: Double, cam: Camera, measurer: TextMeasurer, selected: Boolean) {
    val l = Stairs.layout(s, rise)
    val line = if (selected) StairSelected else StairLine
    val thin = 1.dp.toPx()
    for (st in l.steps) {
        val p = polyPath(st.polygon, cam)
        drawPath(p, if (selected) StairSelected.copy(alpha = 0.12f) else StairFill)
        drawPath(p, line, style = Stroke(thin))
    }
    for (pc in l.pieces) drawPath(polyPath(pc, cam), line, style = Stroke(if (selected) 3.dp.toPx() else 1.8.dp.toPx(), join = StrokeJoin.Miter))
    drawWalkLine(l.path, cam, line, dashed = false)
    val start = cam.px(l.path.first())
    drawCircle(line, 3.5.dp.toPx(), start)
    val layout = measurer.measure("sale", TextStyle(fontSize = 10.sp, color = line, fontWeight = FontWeight.Medium))
    val d = l.path[1] - l.path[0]
    val side = Vec2(-d.y, d.x).normalized() * (s.width * 0.28)
    val at = cam.px(l.path.first() + side)
    if (cam.scale * s.width > 40) drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

/** Arrivo di una scala dal piano di sotto: il vuoto nel pavimento, tratteggiato e con "scende". */
internal fun DrawScope.drawStairArrival(
    s: Stair,
    rise: Double,
    cam: Camera,
    measurer: TextMeasurer,
    walls: List<Pair<Vec2, Vec2>> = emptyList(),
    selected: Boolean = false,
) {
    val l = Stairs.layout(s, rise)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
    val covered = Stairs.coveredOf(s, l)
    // Gradini coperti dal pavimento: si vedono appena, tratteggio fitto e leggero (linee nascoste).
    if (covered > 0) {
        val hidden = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        for (st in l.steps.take(covered)) drawPath(polyPath(st.polygon, cam), StairLine.copy(alpha = 0.35f), style = Stroke(1.dp.toPx(), pathEffect = hidden))
    }
    val well = Stairs.well(s, rise, l)
    for (pc in well) {
        val p = polyPath(pc, cam)
        drawPath(p, if (selected) StairSelected.copy(alpha = 0.18f) else VoidFill)
        // Contorno del vuoto (con il pavimento sopra i primi gradini, i gradini scoperti: solo le linee sottili).
        drawPath(p, StairLine, style = Stroke((if (covered > 0) 1.0 else 1.5).dp.toPx(), pathEffect = dash))
        // Croce del vuoto, come nei disegni tecnici.
        if (covered == 0 && pc.size == 4) {
            drawLine(StairLine.copy(alpha = 0.35f), cam.px(pc[0]), cam.px(pc[2]), 1.dp.toPx())
            drawLine(StairLine.copy(alpha = 0.35f), cam.px(pc[1]), cam.px(pc[3]), 1.dp.toPx())
        }
    }
    // Ringhiera sul bordo del vuoto: doppia linea (metallo, legno) o azzurra (vetro).
    if (s.wellRailing != StairRailing.None) {
        val w = (8 * cam.scale).toFloat().coerceIn(3.dp.toPx(), 7.dp.toPx())
        for ((a, b) in Stairs.wellRailingRuns(s, rise, walls)) {
            val color = when (s.wellRailing) {
                StairRailing.Glass -> Color(0xFF0B7285)
                StairRailing.Wood -> Color(0xFF8A5E3B)
                else -> StairLine
            }
            drawLine(if (selected) StairSelected else color, cam.px(a), cam.px(b), w, cap = StrokeCap.Butt)
            if (s.wellRailing != StairRailing.Glass) drawLine(Color.White, cam.px(a), cam.px(b), w * 0.4f, cap = StrokeCap.Butt)
        }
    }
    drawWalkLine(l.path.reversed(), cam, StairLine.copy(alpha = 0.7f), dashed = true)
    // "scende" al centro del vuoto (più corto se il pavimento copre i primi gradini).
    val wellBounds = com.sagoma.planimetria.geometry.Polygon.bounds(well.flatten())
    val c = cam.px(wellBounds.center)
    val layout = measurer.measure("scende",TextStyle(fontSize = 10.sp, color = StairLine))
    if (cam.scale * maxOf(wellBounds.width, wellBounds.height) > layout.size.width * 0.8) {
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
    }
}

/** Linea di salita (o discesa) con la freccia all'ultimo punto. */
private fun DrawScope.drawWalkLine(path: List<Vec2>, cam: Camera, color: Color, dashed: Boolean) {
    if (path.size < 2) return
    val p = Path().apply {
        path.forEachIndexed { i, v -> val o = cam.px(v); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    }
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
    drawPath(p, color, style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    val end = cam.px(path.last())
    val prev = cam.px(path[path.size - 2])
    val ang = atan2(end.y - prev.y, end.x - prev.x)
    val len = 9.dp.toPx()
    for (da in listOf(2.6f, -2.6f)) {
        drawLine(color, end, Offset(end.x + cos(ang + da) * len, end.y + sin(ang + da) * len), 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}
