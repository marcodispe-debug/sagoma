package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import com.sagoma.planimetria.geometry.Openings

/**
 * Mentre si trascina una porta, una finestra o un balcone: le due quote dal bordo dell'apertura agli angoli del suo muro
 * (filo interno), un poco dentro la stanza. Al rilascio spariscono.
 */
internal fun DrawScope.drawOpeningGaps(state: EditorUiState, measurer: TextMeasurer) {
    if (!state.dragging) return
    val sel = state.selection as? Selection.Opening ?: return
    val room = state.plan.room(sel.roomId) ?: return
    val o = room.opening(sel.openingId) ?: return
    val gaps = Openings.sideGaps(room, o)
    if (gaps.isEmpty()) return
    val cam = state.camera
    fun q(v: com.sagoma.planimetria.model.Vec2) = Offset(cam.toScreenX(v.x), cam.toScreenY(v.y))
    val w = 1.5.dp.toPx()
    val tick = 6.dp.toPx()
    val inward = gaps.first().inward.let { Offset(it.x.toFloat(), it.y.toFloat()) }
    // Sul muro singolo si sta da un lato qualsiasi; nella stanza la quota sta dentro, staccata dalla faccia del muro.
    val off = inward * 16.dp.toPx()
    for (g in gaps) {
        val a = q(g.from) + off
        val b = q(g.to) + off
        // Richiamo tratteggiato dalla faccia del muro al bordo dell'apertura e tacche alle estremità.
        drawLine(MeasureColor, q(g.to), b, w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
        drawLine(MeasureColor, a, b, w)
        for (p in listOf(a, b)) drawLine(MeasureColor, p - inward * tick, p + inward * tick, w)
        val label = measurer.measure(gapText(g.length), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White))
        val pad = 4.dp.toPx()
        val box = Size(label.size.width + 2 * pad, label.size.height + pad)
        // L'etichetta sta ancora più dentro della linea, così non copre il muro né l'apertura.
        val c = (a + b) / 2f + inward * (box.height / 2 + 6.dp.toPx())
        val tl = Offset(c.x - box.width / 2, c.y - box.height / 2)
        drawRoundRect(MeasureColor, tl, box, CornerRadius(5.dp.toPx()))
        drawText(label, topLeft = Offset(tl.x + pad, tl.y + pad / 2))
    }
}

/** "85 cm" per i centimetri interi, altrimenti col decimale ("85,5 cm"). */
private fun gapText(cm: Double): String {
    val r = kotlin.math.round(cm * 10) / 10
    return if (r == kotlin.math.round(r)) "${r.toLong()} cm" else formatDistance(r)
}
