package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.testi.T
import kotlin.math.roundToInt

/**
 * "◐ Piantina e disegno": mostra/nascondi e trasparenza della piantina importata e del disegno, regolate dal vivo sulla pianta.
 * La piantina si salva nel progetto (e si annulla in un passo); il disegno è solo un modo di guardare.
 */
@Composable
fun VisibilityPanel(state: EditorUiState, vm: EditorViewModel, onLoadPlan: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.width(280.dp), shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val u = state.plan.underlay
            if (u == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(T.vari.piantinaNessuna, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onLoadPlan) { Text(T.vari.carica) }
                }
            } else VisibilityRow(
                label = T.vari.piantina,
                visible = u.visible,
                opacity = u.opacity.toFloat(),
                onVisible = vm::setUnderlayVisible,
                onOpacity = { vm.previewUnderlayOpacity(it.toDouble()) },
                onOpacityDone = vm::endUnderlayOpacity,
            )
            if (u != null) TextButton(onClick = vm::recalibrateUnderlay) { Text(T.vari.rifaiTaratura) }
            VisibilityRow(
                label = T.vari.disegno,
                visible = state.drawingVisible,
                opacity = state.drawingOpacity.toFloat(),
                onVisible = vm::setDrawingVisible,
                onOpacity = { vm.setDrawingOpacity(it.toDouble()) },
                onOpacityDone = {},
            )
        }
    }
}

@Composable
private fun VisibilityRow(
    label: String,
    visible: Boolean,
    opacity: Float,
    onVisible: (Boolean) -> Unit,
    onOpacity: (Float) -> Unit,
    onOpacityDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (visible) "$label · ${(opacity * 100).roundToInt()}%" else T.vari.nascosto(label),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Switch(checked = visible, onCheckedChange = onVisible)
    }
    // Il cursore cambia la pianta mentre lo si trascina, non solo quando lo si lascia.
    Slider(value = opacity, onValueChange = onOpacity, valueRange = 0.1f..1f, enabled = visible, onValueChangeFinished = onOpacityDone)
}
