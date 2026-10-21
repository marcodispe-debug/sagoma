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
import com.sagoma.planimetria.testi.T

/** Nome da mostrare per un oggetto della pianta ("Calorifero", "Finestra 2 ante · Soggiorno", "Muro · Cucina"…). */
internal fun targetLabel(state: EditorUiState, t: DragTarget): String {
    val plan = state.plan
    return when (t) {
        is DragTarget.Wall -> plan.room(t.roomId)?.let { r -> T.vari.muroCm(r.name, formatCm(r.wallLength(t.index))) } ?: T.vari.muro
        is DragTarget.Opening -> plan.room(t.roomId)?.let { r -> r.opening(t.openingId)?.let { "${openingLabel(it)} · ${r.name}" } } ?: T.vari.apertura
        is DragTarget.Fixture -> plan.room(t.roomId)?.let { r -> r.fixture(t.fixtureId)?.let { "${it.kind.label} · ${r.name}" } } ?: T.vari.impianto
        is DragTarget.FurnitureBody -> plan.furniture(t.furnitureId)?.let { FurnitureCatalog.item(it.model)?.nome } ?: T.vari.arredo
        is DragTarget.Column -> T.vari.colonna
        is DragTarget.BeamBody -> T.vari.trave
        is DragTarget.FreeWallBody -> T.vari.muroSingolo
        is DragTarget.Stair -> T.vari.scala
        else -> T.vari.oggetto
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
        title = { Text(T.vari.cosaVuoiSelezionare) },
        text = {
            Column {
                Text(T.vari.quiSonoPiuOggetti, style = MaterialTheme.typography.bodySmall)
                for (t in targets) {
                    TextButton(onClick = { onPick(t) }, modifier = Modifier.fillMaxWidth()) {
                        Text(targetLabel(state, t), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(T.comuni.annulla) } },
    )
}
