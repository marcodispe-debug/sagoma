package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.WallDraw
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType

private val DraftWall = Color(0xFF3A3F44)
private val DraftPreview = Color(0xFF1D6FD1)

/**
 * Muri in disegno: quelli già messi con il loro spessore, l'anteprima del prossimo (tratteggiata, con la
 * lunghezza) e gli angoli; il primo angolo è evidenziato quando si può chiudere la forma.
 */
internal fun DrawScope.drawWallDraft(wd: WallDraw, cam: Camera, measurer: TextMeasurer) {
    fun o(p: com.sagoma.planimetria.model.Vec2) = Offset(cam.toScreenX(p.x), cam.toScreenY(p.y))
    val thick = (Room.WALL_THICKNESS * cam.scale).toFloat().coerceAtLeast(2.dp.toPx())
    for (i in 0 until wd.points.size - 1) {
        drawLine(DraftWall.copy(alpha = 0.85f), o(wd.points[i]), o(wd.points[i + 1]), strokeWidth = thick, cap = StrokeCap.Square)
        lengthLabel(measurer, o(wd.points[i]), o(wd.points[i + 1]), wd.points[i].distanceTo(wd.points[i + 1]), DraftWall)
    }
    val last = wd.points.lastOrNull()
    val cursor = wd.cursor
    if (last != null && cursor != null && !wd.closing && cursor.distanceTo(last) > 0.5) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
        drawLine(DraftPreview, o(last), o(cursor), strokeWidth = 2.dp.toPx(), pathEffect = dash)
        lengthLabel(measurer, o(last), o(cursor), last.distanceTo(cursor), DraftPreview)
    }
    wd.points.forEachIndexed { i, p ->
        val c = o(p)
        if (i == 0 && wd.points.size >= 3) drawCircle(DraftPreview, 9.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, 4.dp.toPx(), c)
        drawCircle(DraftPreview, 4.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
    }
    if (last == null && cursor != null) drawCircle(DraftPreview, 4.dp.toPx(), o(cursor))
}

/** Lunghezza del muro al centro del segmento, in un'etichetta. */
private fun DrawScope.lengthLabel(measurer: TextMeasurer, a: Offset, b: Offset, cm: Double, color: Color) {
    val t = measurer.measure("${formatCm(cm)} cm", TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White))
    val mid = (a + b) / 2f
    val pad = 4.dp.toPx()
    val tl = mid - Offset(t.size.width / 2f, t.size.height + 10.dp.toPx())
    drawRoundRect(color, tl - Offset(pad, pad / 2), Size(t.size.width + 2 * pad, t.size.height + pad), CornerRadius(4.dp.toPx()))
    drawText(t, topLeft = tl)
}

/**
 * Scheda del disegno dei muri: cosa fare, lunghezza (e angolo) del prossimo muro da scrivere, e i comandi
 * Ultimo (toglie l'ultimo angolo), Chiudi stanza, Esci.
 */
@Composable
fun WallDrawCard(state: EditorUiState, vm: EditorViewModel) {
    val wd = state.wallDraw ?: return
    var length by remember { mutableStateOf("") }
    var angle by remember { mutableStateOf("") }
    fun add() {
        val l = length.replace(',', '.').toDoubleOrNull() ?: return
        vm.drawWallsTyped(l, angle.replace(',', '.').toDoubleOrNull())
        length = ""
    }
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✏ Muro per muro", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    wd.points.isEmpty() -> "Tocca (o clicca) dove inizia il primo muro."
                    wd.points.size < 3 -> "Tocca il prossimo angolo, oppure scrivi la lunghezza del muro. Misure sull'asse dei muri."
                    else -> "Continua, oppure tocca il primo angolo (cerchiato) o \"Chiudi stanza\" per finire."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (wd.points.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val keys = Modifier.onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { add(); true } else false
                }
                OutlinedTextField(
                    value = length, onValueChange = { length = it }, label = { Text("Lunghezza cm") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.width(140.dp).then(keys),
                )
                OutlinedTextField(
                    value = angle, onValueChange = { angle = it }, label = { Text("Angolo °") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.width(100.dp).then(keys),
                )
                Button(onClick = { add() }, enabled = length.isNotBlank()) { Text("Aggiungi") }
            }
            if (wd.points.isNotEmpty()) Text(
                "Senza angolo il muro va verso il puntatore (0°, 45° e 90° si bloccano da soli). 0° = destra, 90° = su.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = vm::drawWallsUndo, enabled = wd.points.isNotEmpty()) { Text("↶ Ultimo") }
                TextButton(onClick = vm::drawWallsClose, enabled = wd.points.size >= 3) { Text("Chiudi stanza") }
                TextButton(onClick = vm::cancelDrawWalls) { Text("Esci") }
            }
        }
    }
    if (wd.closing) WallDrawCloseDialog(vm)
}

/** Forma chiusa: tipo di stanza, altezza del soffitto e spessore dei muri. */
@Composable
private fun WallDrawCloseDialog(vm: EditorViewModel) {
    var type by remember { mutableStateOf(RoomType.entries.first()) }
    var ceiling by remember { mutableStateOf(formatCm(Room.DEFAULT_CEILING_HEIGHT)) }
    var thickness by remember { mutableStateOf(formatCm(Room.WALL_THICKNESS)) }
    val c = ceiling.replace(',', '.').toDoubleOrNull()
    val t = thickness.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = vm::drawWallsResume,
        title = { Text("Nuova stanza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo di stanza", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rt in RoomType.entries) FilterChip(selected = rt == type, onClick = { type = rt }, label = { Text(rt.label) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectAllTextField(value = ceiling, onValueChange = { ceiling = it }, label = "Soffitto cm", modifier = Modifier.weight(1f))
                    SelectAllTextField(value = thickness, onValueChange = { thickness = it }, label = "Spessore muri cm", modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = c != null && c > 0 && t != null && t in 3.0..120.0, onClick = { vm.finishDrawWalls(type, c!!, t!!) }) { Text("Crea stanza") }
        },
        dismissButton = { TextButton(onClick = vm::drawWallsResume) { Text("Continua a disegnare") } },
    )
}
