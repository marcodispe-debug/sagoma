package com.sagoma.planimetria.ui.sketch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sagoma.planimetria.editor.CreationIntent
import com.sagoma.planimetria.editor.CreationStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.ui.AppFeature
import com.sagoma.planimetria.ui.FeatureAccess
import com.sagoma.planimetria.ui.LocalPlatform

/**
 * Inizio della creazione di una stanza dove la scansione guidata è disponibile:
 *  1. "Conosci le misure della stanza?" — Sì: il menu di sempre (percorso A); No: passo 2.
 *  2. "Forma e misure approssimative" oppure "… + scansione per misure più precise".
 *  3. Il menu delle forme (solo forme e muro per muro, misure indicative): si crea lo SCHIZZO e si torna all'editor.
 * Altrove (funzione non disponibile) e per balconi e terrazze: subito il menu di sempre, come prima.
 * `shapeMenu(sketchMode, backToQuestion)` è il menu delle forme esistente.
 */
@Composable
fun PickShapeEntry(
    step: CreationStep.PickShape,
    vm: EditorViewModel,
    props: DialogProperties,
    shapeMenu: @Composable (sketchMode: Boolean, backToQuestion: (() -> Unit)?) -> Unit,
) {
    val ui by vm.state.collectAsState()
    val guided = FeatureAccess.isEnabled(LocalPlatform.current, AppFeature.SKETCH_GUIDED_SCAN) && ui.creationType?.outdoor != true
    if (!guided) { shapeMenu(false, null); return }
    var askVariant by remember(step) { mutableStateOf(false) }
    val t = LocalSketchScanText.current
    val intent = ui.creationIntent
    when {
        intent == null && !askVariant -> AlertDialog(
            onDismissRequest = vm::cancelCreation,
            properties = props,
            title = { Text(t.knowMeasuresQuestion) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Choice(t.knowMeasuresYes, t.knowMeasuresYesHint) { vm.setCreationIntent(CreationIntent.EXACT) }
                    Choice(t.knowMeasuresNo, t.knowMeasuresNoHint) { askVariant = true }
                }
            },
            confirmButton = {},
            dismissButton = { if (step.cancellable) TextButton(onClick = vm::cancelCreation) { Text(t.cancel) } },
        )
        intent == null -> AlertDialog(
            onDismissRequest = vm::cancelCreation,
            properties = props,
            title = { Text(t.approxTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Choice(t.approxOnly, t.approxOnlyHint) { vm.setCreationIntent(CreationIntent.APPROX) }
                    Choice(t.approxWithScan, t.approxWithScanHint) { vm.setCreationIntent(CreationIntent.APPROX_SCAN) }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row {
                    TextButton(onClick = { askVariant = false }) { Text(t.back) }
                    if (step.cancellable) TextButton(onClick = vm::cancelCreation) { Text(t.cancel) }
                }
            },
        )
        else -> shapeMenu(intent.isSketch) { vm.setCreationIntent(null); askVariant = intent.isSketch }
    }
}

@Composable
private fun Choice(title: String, hint: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
