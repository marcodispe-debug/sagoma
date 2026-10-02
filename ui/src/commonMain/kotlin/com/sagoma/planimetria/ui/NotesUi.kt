package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.model.Dimension
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Layer
import com.sagoma.planimetria.model.Vec2

/** Dimensioni dei testi (altezza in cm sulla pianta: a 1:50 un testo da 25 cm è alto 5 mm sul foglio). */
private val TextSizes = listOf("Piccolo" to 15.0, "Medio" to 25.0, "Grande" to 40.0)

/** Scheda in alto mentre è attivo lo strumento "Quota" o "Testo". */
@Composable
fun NotesToolCard(state: EditorUiState, vm: EditorViewModel) {
    val draw = state.dimensionDraw
    val hint = when {
        draw != null -> if (draw.first == null) "📏 Quota: tocca il primo punto (si aggancia ad angoli e facce dei muri)"
        else "📏 Quota: tocca il secondo punto"
        state.textPlacing -> "T Testo: tocca dove scriverlo"
        else -> return
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hint, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = { if (state.dimensionDraw != null) vm.cancelDimension() else vm.cancelText() }) {
                Text(if (state.dimensionDraw != null) "Fine" else "Annulla", color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

/** Finestra per scrivere un testo nuovo nel punto scelto. */
@Composable
fun NewTextDialog(at: Vec2, vm: EditorViewModel) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(TextSizes[1].second) }
    AlertDialog(
        onDismissRequest = vm::cancelText,
        title = { Text("Nuovo testo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectAllTextField(value = text, onValueChange = { text = it }, label = "Testo", modifier = Modifier.fillMaxWidth())
                Choice("Dimensione", TextSizes.map { it.first }, TextSizes.indexOfFirst { it.second == size }, Modifier.fillMaxWidth()) { size = TextSizes[it].second }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { vm.addAnnotation(at, text, size) }) { Text("Metti") } },
        dismissButton = { TextButton(onClick = vm::cancelText) { Text("Annulla") } },
    )
}

/** Caratteristiche di una quota manuale: misura, scritta al posto della misura, distanza della linea. */
@Composable
internal fun DimensionPanel(sel: Selection.Dimension, plan: FloorPlan, vm: EditorViewModel) {
    val d = plan.dimension(sel.dimensionId) ?: return
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeDimension(d.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    Text("Misura: ${Dimension.formatLength(d.length)} cm (si aggiorna spostando le estremità).", style = MaterialTheme.typography.bodyMedium)
    var label by remember(d.id) { mutableStateOf(d.text.orEmpty()) }
    SelectAllTextField(
        value = label,
        onValueChange = { label = it; vm.updateDimension(d.copy(text = it.ifBlank { null })) },
        label = "Scritta al posto della misura (vuoto = la misura)",
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    )
    var off by remember(d.id, d.offset) { mutableStateOf(formatCm(kotlin.math.abs(d.offset))) }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectAllTextField(
            value = off,
            onValueChange = { off = it; it.replace(',', '.').toDoubleOrNull()?.let { v -> vm.updateDimension(d.copy(offset = if (d.offset < 0) -v else v)) } },
            label = "Distanza della linea (cm)",
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { vm.updateDimension(d.copy(offset = -d.offset)) }) { Text("⇅ Dall'altra parte") }
    }
    Text(
        "Trascina la linea per allontanarla, le estremità per misurare altro: si agganciano ad angoli e facce dei muri.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Caratteristiche di un testo: il testo, la dimensione, la rotazione e la freccia di richiamo. */
@Composable
internal fun TextNotePanel(sel: Selection.Annotation, plan: FloorPlan, vm: EditorViewModel) {
    val t = plan.annotation(sel.annotationId) ?: return
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeAnnotation(t.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    var text by remember(t.id) { mutableStateOf(t.text) }
    SelectAllTextField(
        value = text,
        onValueChange = { text = it; if (it.isNotBlank()) vm.updateAnnotation(t.copy(text = it)) },
        label = "Testo",
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    )
    val sizeIndex = TextSizes.indexOfFirst { it.second == t.size }.takeIf { it >= 0 } ?: TextSizes.indices.minBy { kotlin.math.abs(TextSizes[it].second - t.size) }
    Choice("Dimensione", TextSizes.map { it.first }, sizeIndex, Modifier.fillMaxWidth().padding(end = 8.dp)) { vm.updateAnnotation(t.copy(size = TextSizes[it].second)) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.updateAnnotation(t.copy(rotation = if (t.rotation == 0.0) -90.0 else 0.0)) }) {
            Text(if (t.rotation == 0.0) "↺ In verticale" else "↻ In orizzontale")
        }
        if (t.arrowTo == null) OutlinedButton(onClick = { vm.updateAnnotation(t.copy(arrowTo = t.at + Vec2(t.size * 3, t.size * 2))) }) { Text("↘ Freccia") }
        else OutlinedButton(onClick = { vm.updateAnnotation(t.copy(arrowTo = null)) }) { Text("Togli freccia") }
    }
    Text(
        "Trascina il testo per spostarlo" + if (t.arrowTo != null) "; trascina la punta della freccia per indicare un punto." else ".",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Scelta della vista stato di fatto / progetto (gialli e rossi, stato di fatto, stato di progetto). */
@Composable
fun PhaseViewButton(state: EditorUiState, vm: EditorViewModel) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }) {
            val icon = when (state.phaseView) {
                com.sagoma.planimetria.geometry.PhaseView.Compare -> "🟡🔴"
                com.sagoma.planimetria.geometry.PhaseView.Existing -> "◻"
                com.sagoma.planimetria.geometry.PhaseView.Project -> "◼"
            }
            Text("$icon ${state.phaseView.label} ▾")
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (v in com.sagoma.planimetria.geometry.PhaseView.entries) {
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Column {
                            Text(v.label)
                            Text(
                                when (v) {
                                    com.sagoma.planimetria.geometry.PhaseView.Compare -> "Tutto: demolizioni in giallo, costruzioni in rosso"
                                    com.sagoma.planimetria.geometry.PhaseView.Existing -> "Com'è ora, senza le parti nuove"
                                    com.sagoma.planimetria.geometry.PhaseView.Project -> "Come sarà, senza le parti demolite (anche nel 3D)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    onClick = { open = false; vm.setPhaseView(v) },
                )
            }
        }
    }
}

/** Legenda della tavola comparativa (quando nel piano c'è qualcosa da demolire o di nuovo). */
@Composable
fun PhaseLegend(state: EditorUiState, modifier: Modifier = Modifier) {
    if (state.phaseView != com.sagoma.planimetria.geometry.PhaseView.Compare || !com.sagoma.planimetria.geometry.Phases.hasChanges(state.plan)) return
    Surface(modifier, shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((color, label) in listOf(PhaseDemolishColor to "Da demolire", PhaseNewColor to "Nuovo")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.foundation.Canvas(Modifier.padding(0.dp).size(14.dp)) { drawRect(color); drawRect(androidx.compose.ui.graphics.Color(0xFF3A3F44), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)) }
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Livelli: cosa si vede (sulla pianta e nel PDF) e cosa si può toccare. */
@Composable
fun LayersDialog(state: EditorUiState, vm: EditorViewModel, onDismiss: () -> Unit) {
    val layers = state.building.layers
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Livelli") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Nascosto: non si vede sulla pianta né nel PDF e nell'immagine. Bloccato: si vede ma non si tocca. Stanze, muri, porte e finestre sono sempre attivi.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("", modifier = Modifier.weight(1f))
                    Text("Visibile", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Bloccato", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                }
                for (l in Layer.entries) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Checkbox(checked = layers.visible(l), onCheckedChange = { vm.setLayerVisible(l, it) })
                        Checkbox(checked = l in layers.locked, onCheckedChange = { vm.setLayerLocked(l, it) }, enabled = layers.visible(l))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fatto") } },
    )
}
