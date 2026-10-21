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
import com.sagoma.planimetria.testi.T

/** Dimensioni dei testi (altezza in cm sulla pianta: a 1:50 un testo da 25 cm è alto 5 mm sul foglio). */
private val TextSizes = listOf(T.note.piccolo to 15.0, T.note.medio to 25.0, T.note.grande to 40.0)

/** Scheda in alto mentre è attivo lo strumento "Quota" o "Testo". */
@Composable
fun NotesToolCard(state: EditorUiState, vm: EditorViewModel) {
    val draw = state.dimensionDraw
    val hint = when {
        draw != null -> if (draw.first == null) T.note.quotaToccaPrimoPunto
        else T.note.quotaToccaSecondoPunto
        state.textPlacing -> T.note.tTestoToccaDove
        else -> return
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hint, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = { if (state.dimensionDraw != null) vm.cancelDimension() else vm.cancelText() }) {
                Text(if (state.dimensionDraw != null) T.comuni.fine else T.comuni.annulla, color = MaterialTheme.colorScheme.inversePrimary)
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
        title = { Text(T.note.nuovoTesto) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectAllTextField(value = text, onValueChange = { text = it }, label = T.note.testoTesto, modifier = Modifier.fillMaxWidth())
                Choice(T.note.dimensione, TextSizes.map { it.first }, TextSizes.indexOfFirst { it.second == size }, Modifier.fillMaxWidth()) { size = TextSizes[it].second }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { vm.addAnnotation(at, text, size) }) { Text(T.note.metti) } },
        dismissButton = { TextButton(onClick = vm::cancelText) { Text(T.comuni.annulla) } },
    )
}

/** Caratteristiche di una quota manuale: misura, scritta al posto della misura, distanza della linea. */
@Composable
internal fun DimensionPanel(sel: Selection.Dimension, plan: FloorPlan, vm: EditorViewModel) {
    val d = plan.dimension(sel.dimensionId) ?: return
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeDimension(d.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    Text(T.note.misuraCmAggiornaSpostando(Dimension.formatLength(d.length)), style = MaterialTheme.typography.bodyMedium)
    var label by remember(d.id) { mutableStateOf(d.text.orEmpty()) }
    SelectAllTextField(
        value = label,
        onValueChange = { label = it; vm.updateDimension(d.copy(text = it.ifBlank { null })) },
        label = T.note.scrittaPostoMisuraVuoto,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    )
    var off by remember(d.id, d.offset) { mutableStateOf(formatCm(kotlin.math.abs(d.offset))) }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectAllTextField(
            value = off,
            onValueChange = { off = it; it.replace(',', '.').toDoubleOrNull()?.let { v -> vm.updateDimension(d.copy(offset = if (d.offset < 0) -v else v)) } },
            label = T.note.distanzaLineaCm,
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { vm.updateDimension(d.copy(offset = -d.offset)) }) { Text(T.note.dallAltraParte) }
    }
    Text(
        T.note.trascinaLineaAllontanarlaEstremita,
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
        TextButton(onClick = { vm.removeAnnotation(t.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    var text by remember(t.id) { mutableStateOf(t.text) }
    SelectAllTextField(
        value = text,
        onValueChange = { text = it; if (it.isNotBlank()) vm.updateAnnotation(t.copy(text = it)) },
        label = T.note.testoTesto,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    )
    val sizeIndex = TextSizes.indexOfFirst { it.second == t.size }.takeIf { it >= 0 } ?: TextSizes.indices.minBy { kotlin.math.abs(TextSizes[it].second - t.size) }
    Choice(T.note.dimensione, TextSizes.map { it.first }, sizeIndex, Modifier.fillMaxWidth().padding(end = 8.dp)) { vm.updateAnnotation(t.copy(size = TextSizes[it].second)) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.updateAnnotation(t.copy(rotation = if (t.rotation == 0.0) -90.0 else 0.0)) }) {
            Text(if (t.rotation == 0.0) T.note.verticale else T.note.orizzontale)
        }
        if (t.arrowTo == null) OutlinedButton(onClick = { vm.updateAnnotation(t.copy(arrowTo = t.at + Vec2(t.size * 3, t.size * 2))) }) { Text(T.note.freccia) }
        else OutlinedButton(onClick = { vm.updateAnnotation(t.copy(arrowTo = null)) }) { Text(T.note.togliFreccia) }
    }
    Text(
        T.note.trascinaTestoSpostarlo + if (t.arrowTo != null) T.note.trascinaPuntaFrecciaIndicare else ".",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Legenda della tavola comparativa (quando nel piano c'è qualcosa da demolire o di nuovo). */
@Composable
fun PhaseLegend(state: EditorUiState, modifier: Modifier = Modifier) {
    if (state.phaseView != com.sagoma.planimetria.geometry.PhaseView.Compare || !com.sagoma.planimetria.geometry.Phases.hasChanges(state.plan)) return
    Surface(modifier, shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((color, label) in listOf(PhaseDemolishColor to T.note.demolire, PhaseNewColor to T.note.nuovo)) {
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
        title = { Text(T.note.livelli) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    T.note.nascostoNonVedePianta,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("", modifier = Modifier.weight(1f))
                    Text(T.note.visibile, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                    Text(T.note.bloccato, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(T.note.fatto) } },
    )
}
