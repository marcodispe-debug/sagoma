package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.model.Beam
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Vec2
import kotlin.math.atan2

private val ColumnFill = Color(0xFF4A4E53)
private val BeamLine = Color(0xFF6C4A2E)
private val StructureSelected = Color(0xFF1971C2)

private fun Camera.pt(v: Vec2) = Offset(toScreenX(v.x), toScreenY(v.y))

private fun outlinePath(pts: List<Vec2>, cam: Camera) = Path().apply {
    pts.forEachIndexed { i, p -> val o = cam.pt(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

/** Colonne (piene, come i muri) e travi (tratteggiate: stanno sopra, a soffitto) del piano. */
internal fun DrawScope.drawStructures(state: EditorUiState, measurer: TextMeasurer) {
    val cam = state.camera
    val selColumn = (state.selection as? Selection.Column)?.columnId
    val selBeam = (state.selection as? Selection.Beam)?.beamId
    val selWall = (state.selection as? Selection.FreeWall)?.wallId
    for (w in state.plan.freeWalls) drawFreeWall(w, cam, measurer, w.id == selWall)
    for (b in state.plan.beams) drawBeam(b, cam, measurer, b.id == selBeam)
    for (c in state.plan.columns) drawColumn(c, cam, c.id == selColumn)
}

internal fun DrawScope.drawStructures(plan: FloorPlan, cam: Camera, measurer: TextMeasurer) {
    for (w in plan.freeWalls) drawFreeWall(w, cam, measurer, false)
    for (b in plan.beams) drawBeam(b, cam, measurer, false)
    for (c in plan.columns) drawColumn(c, cam, false)
}

private val FreeWallColor = Color(0xFF3A3F44)

/** Muro singolo: pieno come i muri delle stanze, con la lunghezza scritta accanto. */
internal fun DrawScope.drawFreeWall(w: FreeWall, cam: Camera, measurer: TextMeasurer, selected: Boolean) {
    // Tavola comparativa: da demolire in giallo, nuovo in rosso (con il bordo scuro).
    val fill = when {
        selected -> StructureSelected
        w.phase == com.sagoma.planimetria.model.Phase.Demolish -> PhaseDemolishColor
        w.phase == com.sagoma.planimetria.model.Phase.New -> PhaseNewColor
        else -> FreeWallColor
    }
    val path = outlinePath(Structure.outline(w), cam)
    drawPath(path, fill)
    if (!selected && w.phase != com.sagoma.planimetria.model.Phase.Existing) drawPath(path, FreeWallColor, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    val a = cam.pt(w.start)
    val e = cam.pt(w.end)
    val label = measurer.measure(formatCm(w.length), TextStyle(fontSize = 10.sp, color = if (selected) StructureSelected else Color(0xFF495057)))
    if ((e - a).getDistance() > label.size.width * 1.5f) {
        var deg = com.sagoma.planimetria.geometry.toDegrees(atan2((e.y - a.y).toDouble(), (e.x - a.x).toDouble())).toFloat()
        if (deg > 90f) deg -= 180f
        if (deg < -90f) deg += 180f
        val mid = (a + e) / 2f
        val gap = (w.thickness / 2 * cam.scale).toFloat() + 2.dp.toPx()
        rotate(deg, mid) {
            drawText(label, topLeft = Offset(mid.x - label.size.width / 2f, mid.y - gap - label.size.height))
        }
    }
    if (selected) for (p in listOf(a, e)) {
        drawCircle(Color.White, 8.dp.toPx(), p)
        drawCircle(StructureSelected, 6.dp.toPx(), p)
    }
}

internal fun DrawScope.drawColumn(c: Column, cam: Camera, selected: Boolean) {
    val color = if (selected) StructureSelected else ColumnFill
    if (c.shape == ColumnShape.Round) {
        drawCircle(color, (c.width / 2 * cam.scale).toFloat(), cam.pt(c.center))
    } else {
        drawPath(outlinePath(Structure.outline(c), cam), color)
    }
    if (selected) drawCircle(Color.White, 3.dp.toPx(), cam.pt(c.center))
}

/** Trave: contorno tratteggiato, linea di mezzeria e misure "30 × 40" lungo la trave. */
internal fun DrawScope.drawBeam(b: Beam, cam: Camera, measurer: TextMeasurer, selected: Boolean) {
    val color = if (selected) StructureSelected else BeamLine
    val dash = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 5.dp.toPx()))
    val path = outlinePath(Structure.outline(b), cam)
    drawPath(path, color.copy(alpha = if (selected) 0.16f else 0.08f))
    drawPath(path, color, style = Stroke(1.6.dp.toPx(), pathEffect = dash))
    drawLine(color.copy(alpha = 0.5f), cam.pt(b.start), cam.pt(b.end), 0.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
    val a = cam.pt(b.start)
    val e = cam.pt(b.end)
    val label = measurer.measure("trave ${formatCm(b.width)}×${formatCm(b.depth)}", TextStyle(fontSize = 10.sp, color = color))
    if ((e - a).getDistance() > label.size.width * 1.3f) {
        var deg = com.sagoma.planimetria.geometry.toDegrees(atan2((e.y - a.y).toDouble(), (e.x - a.x).toDouble())).toFloat()
        if (deg > 90f) deg -= 180f
        if (deg < -90f) deg += 180f
        val mid = (a + e) / 2f
        rotate(deg, mid) {
            drawText(label, topLeft = Offset(mid.x - label.size.width / 2f, mid.y - label.size.height - 2.dp.toPx()))
        }
    }
    if (selected) for (p in listOf(a, e)) {
        drawCircle(Color.White, 8.dp.toPx(), p)
        drawCircle(StructureSelected, 6.dp.toPx(), p)
    }
}
