package com.sagoma.planimetria.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
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
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.editor.WallDirection
import com.sagoma.planimetria.editor.WallDirectionProblem
import com.sagoma.planimetria.editor.WallDraw
import com.sagoma.planimetria.model.ExcludedZone
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.math.abs

private val DraftWall = Color(0xFF3A3F44)
private val DraftPreview = Color(0xFF1D6FD1)

/**
 * Muri in disegno: quelli già messi con il loro spessore, l'anteprima del prossimo (tratteggiata, con la
 * lunghezza) e gli angoli; il primo angolo è evidenziato quando si può chiudere la forma.
 */
internal fun DrawScope.drawWallDraft(wd: WallDraw, cam: Camera, measurer: TextMeasurer) {
    fun o(p: com.sagoma.planimetria.model.Vec2) = Offset(cam.toScreenX(p.x), cam.toScreenY(p.y))
    // Le zone non calpestabili non hanno muri: solo il contorno.
    val thick = if (wd.zoneRoomId != null) 2.dp.toPx() else (Room.WALL_THICKNESS * cam.scale).toFloat().coerceAtLeast(2.dp.toPx())
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
    var arrow by remember { mutableStateOf<WallArrow?>(null) }
    // Sopra una piantina in scala basta cliccare gli angoli: frecce, lunghezza e angolo solo a richiesta ("Scrivi le misure").
    var typing by remember { mutableStateOf(false) }
    val zone = wd.zoneRoomId != null
    val tracing = state.plan.underlay?.visible == true
    val simple = (tracing || zone) && !typing
    val firstWall = wd.points.size < 2
    val lengthCm = length.replace(',', '.').toDoubleOrNull()
    val cornerDeg = angle.replace(',', '.').toDoubleOrNull().takeIf { arrow != null && !firstWall }
    val typed = arrow?.let { vm.typedWallDirection(it, cornerDeg) }
    val problem = (typed as? WallDirection.Result.Invalid)?.problem
    // Anteprima tratteggiata del muro scritto, prima di "Aggiungi"; a ogni angolo messo (o tolto) e a ogni anteprima la
    // vista segue la stanza in costruzione, sotto questa scheda.
    LaunchedEffect(lengthCm, arrow, cornerDeg, wd.points.size) {
        vm.drawWallsTypedPreview(lengthCm, arrow, cornerDeg)
        followWallDraft(vm, includeCursor = arrow != null && lengthCm != null)
    }
    fun add() {
        val l = lengthCm ?: return
        if (vm.drawWallsTyped(l, arrow, cornerDeg)) {
            length = ""; angle = ""; arrow = null
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (zone) "▧ Zona non calpestabile" else "✏ Muro per muro", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    zone && simple && wd.points.size < 3 ->
                        "Clicca gli angoli della parte che non conta nei m² (zona non accessibile, cavedio, pilastro). Si agganciano agli angoli interni dei muri."
                    zone && simple -> "Continua sugli angoli, poi clicca il primo angolo (cerchiato) o \"Chiudi zona\" per finire."
                    simple && wd.points.size < 3 -> "Clicca sugli angoli della piantina, uno dopo l'altro. Vicino alle direzioni dritte e a 45° il muro si blocca da solo."
                    simple -> "Continua sugli angoli, poi clicca il primo angolo (cerchiato) o \"Chiudi stanza\" per finire."
                    wd.points.isEmpty() -> "Tocca (o clicca) dove inizia il primo muro."
                    wd.points.size < 3 -> "Tocca il prossimo angolo, oppure scrivi la lunghezza del muro. Misure sull'asse dei muri."
                    else -> "Continua, oppure tocca il primo angolo (cerchiato) o \"Chiudi stanza\" per finire."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (wd.points.isNotEmpty() && !simple) {
                // Verso dove va il muro: la freccia che torna indietro sul muro precedente non si può scegliere.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Verso:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
                    for (a in WallArrow.entries) {
                        val back = vm.typedWallDirection(a, null) is WallDirection.Result.Invalid
                        FilterChip(
                            selected = arrow == a, enabled = !back,
                            onClick = { arrow = if (arrow == a) null else a },
                            label = { Text(arrowLabel(a)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val keys = Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { add(); true } else false
                    }
                    OutlinedTextField(
                        value = length, onValueChange = { length = it }, label = { Text("Lunghezza cm") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f).then(keys),
                    )
                    // L'angolo tra i muri ha senso dal secondo muro e con una freccia che dice da che parte girare.
                    if (!firstWall) OutlinedTextField(
                        value = angle, onValueChange = { angle = it }, label = { Text("Angolo tra i muri °") }, singleLine = true,
                        enabled = arrow != null, placeholder = { Text("90") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f).then(keys),
                    )
                    Button(onClick = { add() }, enabled = lengthCm != null && lengthCm > 0 && problem == null) { Text("Aggiungi") }
                }
                Text(
                    when {
                        problem == WallDirectionProblem.ANGLE_OUT_OF_RANGE -> "L'angolo tra i muri va da 1° a 179°."
                        problem == WallDirectionProblem.ANGLE_WITHOUT_SIDE ->
                            "Con questa freccia il muro prosegue dritto: per girare scegli una freccia di lato, oppure togli l'angolo."
                        problem == WallDirectionProblem.BACKWARDS -> "Questa freccia torna indietro sul muro precedente."
                        arrow == null -> "Scegli verso dove va il muro. Senza freccia il muro va verso il puntatore (le direzioni dritte e a 45° si bloccano da sole)."
                        firstWall -> "Il muro va dritto ${arrowWhere(arrow!!)}."
                        else -> "Angolo tra questo muro e il precedente (90° = in squadra): il muro gira ${arrowWhere(arrow!!)}. " +
                            "Senza angolo va dritto ${arrowWhere(arrow!!)}. La linea tratteggiata mostra il muro prima di aggiungerlo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = vm::drawWallsUndo, enabled = wd.points.isNotEmpty()) { Text("↶ Ultimo") }
                TextButton(onClick = vm::drawWallsClose, enabled = wd.points.size >= 3) { Text(if (zone) "Chiudi zona" else "Chiudi stanza") }
                TextButton(onClick = vm::cancelDrawWalls) { Text("Esci") }
                // Muro sbiadito o tagliato nella piantina: si può sempre scrivere la misura.
                if ((tracing || zone) && wd.points.isNotEmpty()) TextButton(onClick = {
                    // Chiudendo le misure si toglie anche l'anteprima del muro scritto a metà.
                    if (typing) { length = ""; angle = ""; arrow = null }
                    typing = !typing
                }) { Text(if (typing) "Nascondi misure" else "Scrivi le misure") }
                // Misure che non tornano: si ritara sulla piantina, gli angoli già messi la seguono.
                if (tracing && !zone) TextButton(onClick = vm::recalibrateUnderlay) { Text("📏 Rifai taratura") }
            }
        }
    }
    if (wd.closing) if (zone) ExcludedZoneCloseDialog(vm) else WallDrawCloseDialog(vm, wd.onPlan)
}

/** Scorre (e se serve rimpicciolisce) la vista fino a [EditorViewModel.wallDraftCamera], con una breve animazione. */
private suspend fun followWallDraft(vm: EditorViewModel, includeCursor: Boolean) {
    val target = vm.wallDraftCamera(includeCursor) ?: return
    val start = vm.state.value.camera
    if (abs(target.offsetX - start.offsetX) < 1f && abs(target.offsetY - start.offsetY) < 1f && target.scale == start.scale) return
    animate(0f, 1f, animationSpec = tween(durationMillis = 280)) { t, _ ->
        vm.setCamera(
            Camera(
                scale = start.scale + (target.scale - start.scale) * t,
                offsetX = start.offsetX + (target.offsetX - start.offsetX) * t,
                offsetY = start.offsetY + (target.offsetY - start.offsetY) * t,
            ),
        )
    }
}

private fun arrowLabel(a: WallArrow) = when (a) {
    WallArrow.RIGHT -> "→ destra"
    WallArrow.DOWN -> "↓ giù"
    WallArrow.LEFT -> "← sinistra"
    WallArrow.UP -> "↑ su"
}

private fun arrowWhere(a: WallArrow) = when (a) {
    WallArrow.RIGHT -> "verso destra"
    WallArrow.DOWN -> "verso il basso"
    WallArrow.LEFT -> "verso sinistra"
    WallArrow.UP -> "verso l'alto"
}

/** Forma chiusa: tipo di stanza, altezza del soffitto e spessore dei muri. */
@Composable
private fun WallDrawCloseDialog(vm: EditorViewModel, onPlan: Boolean) {
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
                if (onPlan) Text(
                    "Gli angoli che hai cliccato sono il filo interno dei muri: le misure restano quelle, lo spessore si aggiunge verso l'esterno.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = c != null && c > 0 && t != null && t in 3.0..120.0, onClick = { vm.finishDrawWalls(type, c!!, t!!) }) { Text("Crea stanza") }
        },
        dismissButton = { TextButton(onClick = vm::drawWallsResume) { Text("Continua a disegnare") } },
    )
}

/** Zona chiusa: che cos'è (il nome compare sulla pianta, con i m² tolti). */
@Composable
private fun ExcludedZoneCloseDialog(vm: EditorViewModel) {
    var name by remember { mutableStateOf(ExcludedZone.DEFAULT_NAME) }
    AlertDialog(
        onDismissRequest = vm::drawWallsResume,
        title = { Text("Zona non calpestabile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in listOf(ExcludedZone.DEFAULT_NAME, "Cavedio", "Pilastro", "Canna fumaria")) {
                        FilterChip(selected = name == n, onClick = { name = n }, label = { Text(n) })
                    }
                }
                SelectAllTextField(value = name, onValueChange = { name = it }, label = "Nome", modifier = Modifier.fillMaxWidth())
                Text(
                    "La zona si toglie dai m² della stanza, come nelle planimetrie. Il pavimento nel computo resta quello intero.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.finishExcludedZone(name) }) { Text("Crea zona") } },
        dismissButton = { TextButton(onClick = vm::drawWallsResume) { Text("Continua a disegnare") } },
    )
}
