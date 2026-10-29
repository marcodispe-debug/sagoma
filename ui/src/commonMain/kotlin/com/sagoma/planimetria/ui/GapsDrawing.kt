package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.MeasureTarget
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.WallDistances
import com.sagoma.planimetria.model.Vec2

/** Oggetto selezionato di cui si misurano le distanze dai muri (porte e finestre, colonne, arredi, scale, impianti). */
internal fun wallGapTarget(sel: Selection?): MeasureTarget? = when (sel) {
    is Selection.Opening -> MeasureTarget.Opening(sel.roomId, sel.openingId)
    is Selection.Column -> MeasureTarget.Column(sel.columnId)
    is Selection.Furniture -> MeasureTarget.Furniture(sel.furnitureId)
    is Selection.Stair -> MeasureTarget.Stair(sel.stairId)
    is Selection.Fixture -> MeasureTarget.Fixture(sel.roomId, sel.fixtureId)
    else -> null
}

/**
 * Distanze dell'oggetto selezionato dai muri, in arancione: mentre lo si trascina (o sposta con i tasti), e sempre con
 * «Distanze dai muri» acceso nella sua scheda. Porte, finestre e balconi: dai due angoli del loro muro, un poco dentro la
 * stanza; gli altri oggetti: dal muro più vicino a sinistra, a destra, sopra e sotto.
 */
internal fun DrawScope.drawWallGaps(state: EditorUiState, measurer: TextMeasurer, avoid: List<Rect> = emptyList()) {
    if (!state.dragging && !state.wallGaps) return
    val target = wallGapTarget(state.selection) ?: return
    val gaps = WallDistances.of(state.plan, target, state.levelHeight)
    if (gaps.isEmpty()) return
    val cam = state.camera
    fun q(v: Vec2) = Offset(cam.toScreenX(v.x), cam.toScreenY(v.y))
    val w = 1.5.dp.toPx()
    val tick = 6.dp.toPx()
    // Porte e finestre: la quota sta dentro la stanza, staccata dalla faccia del muro, con il richiamo tratteggiato.
    val inward = (target as? MeasureTarget.Opening)?.let { t ->
        val room = state.plan.room(t.roomId) ?: return
        val o = room.opening(t.openingId) ?: return
        Openings.sideGaps(room, o).firstOrNull()?.inward?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
    }
    val off = inward?.let { it * 16.dp.toPx() } ?: Offset.Zero
    val placed = mutableListOf<Rect>()
    for (g in gaps) {
        val a = q(g.from) + off
        val b = q(g.to) + off
        val dir = Offset(g.toward.x.toFloat(), g.toward.y.toFloat())
        val across = Offset(-dir.y, dir.x)
        if (inward != null) drawLine(MeasureColor, q(g.from), a, w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
        drawLine(MeasureColor, a, b, w)
        for (p in listOf(a, b)) drawLine(MeasureColor, p - across * tick, p + across * tick, w)
        val label = measurer.measure(gapText(g.distance), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White))
        val pad = 4.dp.toPx()
        val box = Size(label.size.width + 2 * pad, label.size.height + pad)
        // A metà della quota (per porte e finestre ancora più dentro, così non coprono il muro); se copre un'altra scritta o
        // il nome della stanza si sposta di lato.
        val base = (a + b) / 2f + (inward?.let { it * (box.height / 2 + 6.dp.toPx()) } ?: Offset.Zero)
        fun rectAt(shift: Float) = (base + across * shift).let { c -> Rect(Offset(c.x - box.width / 2, c.y - box.height / 2), box) }
        val r = listOf(0f, box.height + 2f, -(box.height + 2f), 2 * box.height, -2 * box.height).map(::rectAt)
            .firstOrNull { r -> (placed + avoid).none { it.overlaps(r) } } ?: rectAt(0f)
        placed += r
        drawRoundRect(MeasureColor, r.topLeft, box, CornerRadius(5.dp.toPx()))
        drawText(label, topLeft = Offset(r.left + pad, r.top + pad / 2))
    }
}

/** "85 cm" per i centimetri interi, altrimenti col decimale ("85,5 cm"). */
private fun gapText(cm: Double): String {
    val r = kotlin.math.round(cm * 10) / 10
    return if (r == kotlin.math.round(r)) "${r.toLong()} cm" else formatDistance(r)
}
