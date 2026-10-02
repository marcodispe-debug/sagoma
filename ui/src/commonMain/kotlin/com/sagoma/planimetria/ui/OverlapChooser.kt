package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.model.FurnitureCatalog

/** Nome da mostrare per un oggetto della pianta ("Calorifero", "Finestra 2 ante · Soggiorno", "Muro · Cucina"…). */
internal fun targetLabel(state: EditorUiState, t: DragTarget): String {
    val plan = state.plan
    return when (t) {
        is DragTarget.Wall -> plan.room(t.roomId)?.let { r -> "Muro · ${r.name} (${formatCm(r.wallLength(t.index))} cm)" } ?: "Muro"
        is DragTarget.Opening -> plan.room(t.roomId)?.let { r -> r.opening(t.openingId)?.let { "${openingLabel(it)} · ${r.name}" } } ?: "Apertura"
        is DragTarget.Fixture -> plan.room(t.roomId)?.let { r -> r.fixture(t.fixtureId)?.let { "${it.kind.label} · ${r.name}" } } ?: "Impianto"
        is DragTarget.FurnitureBody -> plan.furniture(t.furnitureId)?.let { FurnitureCatalog.item(it.model)?.label } ?: "Arredo"
        is DragTarget.Column -> "Colonna"
        is DragTarget.BeamBody -> "Trave"
        is DragTarget.FreeWallBody -> "Muro singolo"
        is DragTarget.Stair -> "Scala"
        else -> "Oggetto"
    }
}

/**
 * Oggetti sovrapposti nel punto toccato (es. finestra e calorifero sullo stesso tratto di muro): si sceglie
 * quale selezionare.
 */
@Composable
internal fun OverlapChooser(state: EditorUiState, targets: List<DragTarget>, onPick: (DragTarget) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cosa vuoi selezionare?") },
        text = {
            Column {
                Text("Qui ci sono più oggetti uno sull'altro.", style = MaterialTheme.typography.bodySmall)
                for (t in targets) {
                    TextButton(onClick = { onPick(t) }, modifier = Modifier.fillMaxWidth()) {
                        Text(targetLabel(state, t), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
