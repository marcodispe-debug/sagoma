package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Furnishings
import com.sagoma.planimetria.geometry.GroupItem
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.model.Vec2

private val SelectBlue = Color(0xFF1D6FD1)
private val CrossingGreen = Color(0xFF2E9E55)

/**
 * Selezione multipla sulla pianta: contorno azzurro degli oggetti selezionati e rettangolo di selezione
 * (azzurro da sinistra a destra = solo ciò che è dentro; verde da destra a sinistra = anche ciò che tocca).
 */
internal fun DrawScope.drawMultiSelection(state: EditorUiState, levelHeight: Double) {
    val cam = state.camera
    fun o(p: Vec2) = Offset(cam.toScreenX(p.x), cam.toScreenY(p.y))
    drawItemOutlines(state.plan, state.multi, cam, levelHeight)
    state.selectRect?.let { (a, b) ->
        val crossing = b.x < a.x
        val color = if (crossing) CrossingGreen else SelectBlue
        val tl = Offset(minOf(o(a).x, o(b).x), minOf(o(a).y, o(b).y))
        val size = Size(kotlin.math.abs(o(a).x - o(b).x), kotlin.math.abs(o(a).y - o(b).y))
        drawRect(color.copy(alpha = 0.10f), tl, size)
        drawRect(
            color, tl, size,
            style = Stroke(1.5.dp.toPx(), pathEffect = if (crossing) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null),
        )
    }
}

/** Contorni degli oggetti `items` di `plan`, spostati di `offset` (anteprima di "Incolla"). */
internal fun DrawScope.drawItemOutlines(
    plan: com.sagoma.planimetria.model.FloorPlan,
    items: Set<GroupItem>,
    cam: com.sagoma.planimetria.editor.Camera,
    levelHeight: Double,
    offset: Vec2 = Vec2.Zero,
    color: Color = SelectBlue,
    dashed: Boolean = false,
) {
    fun o(p: Vec2) = Offset(cam.toScreenX(p.x + offset.x), cam.toScreenY(p.y + offset.y))
    val stroke = Stroke(2.5.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null)
    fun outline(pts: List<Vec2>, closed: Boolean = true) {
        if (pts.size < 2) return
        val path = Path().apply {
            moveTo(o(pts[0]).x, o(pts[0]).y)
            for (p in pts.drop(1)) lineTo(o(p).x, o(p).y)
            if (closed) close()
        }
        drawPath(path, color.copy(alpha = 0.12f))
        drawPath(path, color, style = stroke)
    }
    for (item in items) when (item) {
        is GroupItem.RoomItem -> plan.room(item.id)?.let { outline(it.points) }
        is GroupItem.FurnitureItem -> plan.furniture(item.id)?.let { outline(Furnishings.outline(it)) }
        is GroupItem.ColumnItem -> plan.column(item.id)?.let { outline(Structure.outline(it)) }
        is GroupItem.BeamItem -> plan.beam(item.id)?.let { outline(Structure.outline(it)) }
        is GroupItem.FreeWallItem -> plan.freeWall(item.id)?.let { outline(Structure.outline(it)) }
        is GroupItem.StairItem -> plan.stair(item.id)?.let { s -> Stairs.layout(s, levelHeight).pieces.forEach { outline(it) } }
        is GroupItem.RulerItem -> plan.ruler(item.id)?.let { outline(listOf(it.start, it.end), closed = false) }
        is GroupItem.DimensionItem -> plan.dimension(item.id)?.let { outline(listOf(it.a, it.lineA, it.lineB, it.b), closed = false) }
        is GroupItem.AnnotationItem -> plan.annotation(item.id)?.let { t ->
            // Riquadro indicativo attorno al punto del testo (alto `size` cm), più la freccia.
            val h = t.size; val w = t.size * 0.6 * t.text.length.coerceAtLeast(1)
            outline(listOf(t.at + Vec2(-w / 2, -h / 2), t.at + Vec2(w / 2, -h / 2), t.at + Vec2(w / 2, h / 2), t.at + Vec2(-w / 2, h / 2)))
            t.arrowTo?.let { outline(listOf(t.at, it), closed = false) }
        }
    }
}

/**
 * Comandi della selezione multipla: quanti oggetti, ruota, specchia, copia, serie, allinea, distribuisci,
 * sposta di x cm, elimina, fine.
 */
@Composable
fun MultiSelectionCard(state: EditorUiState, vm: EditorViewModel) {
    if (!state.selectMode) return
    var dialog by remember { mutableStateOf<String?>(null) }
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val n = state.multi.size
            Text(
                if (n == 0) "☐ Seleziona: trascina sul vuoto per un rettangolo (→ solo ciò che è dentro, ← anche ciò che tocca), tocca per aggiungere o togliere."
                else "$n ${if (n == 1) "oggetto selezionato" else "oggetti selezionati"} · trascinane uno per spostarli tutti",
                style = MaterialTheme.typography.bodySmall,
            )
            ScrollableBar(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp), spacing = Arrangement.spacedBy(6.dp)) {
                val on = n > 0
                OutlinedButton(onClick = { vm.groupRotate(clockwise = true) }, enabled = on) { Text("↻ 90°") }
                OutlinedButton(onClick = { vm.groupRotate(clockwise = false) }, enabled = on) { Text("↺ 90°") }
                OutlinedButton(onClick = { vm.groupMirror(horizontal = true) }, enabled = on) { Text("⇆ Specchia") }
                OutlinedButton(onClick = { vm.groupMirror(horizontal = false) }, enabled = on) { Text("⇅ Specchia") }
                OutlinedButton(onClick = { dialog = "sposta" }, enabled = on) { Text("Sposta di…") }
                OutlinedButton(onClick = { dialog = "copia" }, enabled = on) { Text("Duplica…") }
                OutlinedButton(onClick = { vm.copy() }, enabled = on) { Text("⧉ Copia") }
                OutlinedButton(onClick = { dialog = "serie" }, enabled = on) { Text("Serie…") }
                AlignMenu(vm, enabled = n >= 2)
                OutlinedButton(onClick = { vm.groupDelete() }, enabled = on) { Text("Elimina", color = if (on) MaterialTheme.colorScheme.error else Color.Unspecified) }
                TextButton(onClick = vm::selectAll) { Text("Tutto") }
                TextButton(onClick = vm::toggleSelectMode) { Text("Fine") }
            }
        }
    }
    when (dialog) {
        "sposta" -> OffsetDialog("Sposta di", withCount = false, onDismiss = { dialog = null }) { dx, dy, _ -> vm.groupTranslate(dx, dy) }
        "copia" -> OffsetDialog("Duplica spostando di", withCount = false, initial = 50.0, onDismiss = { dialog = null }) { dx, dy, _ -> vm.groupDuplicate(dx, dy) }
        "serie" -> OffsetDialog("Serie (copie in fila)", withCount = true, initial = 100.0, onDismiss = { dialog = null }) { dx, dy, c -> vm.groupDuplicate(dx, dy, c) }
    }
}

@Composable
private fun AlignMenu(vm: EditorViewModel, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) { Text("Allinea ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (a in com.sagoma.planimetria.geometry.Alignment.entries) {
                DropdownMenuItem(text = { Text(a.label) }, onClick = { open = false; vm.groupAlign(a) })
            }
            DropdownMenuItem(text = { Text("Distribuisci in orizzontale") }, onClick = { open = false; vm.groupDistribute(horizontal = true) })
            DropdownMenuItem(text = { Text("Distribuisci in verticale") }, onClick = { open = false; vm.groupDistribute(horizontal = false) })
        }
    }
}

/** Spostamento in cm (x verso destra, y verso il basso) e, per la serie, il numero di copie. */
@Composable
private fun OffsetDialog(title: String, withCount: Boolean, initial: Double = 0.0, onDismiss: () -> Unit, onOk: (Double, Double, Int) -> Unit) {
    var dx by remember { mutableStateOf(formatCm(initial)) }
    var dy by remember { mutableStateOf("0") }
    var count by remember { mutableStateOf("3") }
    val x = dx.replace(',', '.').toDoubleOrNull()
    val y = dy.replace(',', '.').toDoubleOrNull()
    val c = count.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectAllTextField(value = dx, onValueChange = { dx = it }, label = "→ destra cm", modifier = Modifier.weight(1f))
                    SelectAllTextField(value = dy, onValueChange = { dy = it }, label = "↓ giù cm", modifier = Modifier.weight(1f))
                }
                if (withCount) SelectAllTextField(value = count, onValueChange = { count = it }, label = "Numero di copie", modifier = Modifier.fillMaxWidth())
                Text("Valori negativi: verso sinistra o verso l'alto.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = x != null && y != null && (!withCount || (c != null && c in 1..200)), onClick = { onOk(x!!, y!!, c ?: 1); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
