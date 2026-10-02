package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.Clip
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.GroupItem
import com.sagoma.planimetria.model.Mount

/** Nome di ciò che è negli appunti ("calorifero", "porta", "3 oggetti"…). */
internal fun clipLabel(clip: Clip): String = when (clip) {
    is Clip.FixtureClip -> clip.fixture.kind.label.lowercase()
    is Clip.OpeningClip -> clip.opening.kind.label.lowercase()
    is Clip.GroupClip -> clip.items.singleOrNull()?.let {
        when (it) {
            is GroupItem.RoomItem -> "stanza"
            is GroupItem.FurnitureItem -> "arredo"
            is GroupItem.ColumnItem -> "colonna"
            is GroupItem.BeamItem -> "trave"
            is GroupItem.FreeWallItem -> "muro"
            is GroupItem.StairItem -> "scala"
            is GroupItem.RulerItem -> "metro"
            is GroupItem.DimensionItem -> "quota"
            is GroupItem.AnnotationItem -> "testo"
        }
    } ?: "${clip.items.size} oggetti"
}

/**
 * Pulsanti "⧉ Copia" (se c'è qualcosa di selezionato da copiare) e "📋 Incolla" (se gli appunti non sono vuoti),
 * sulla pianta in basso a sinistra.
 */
@Composable
fun CopyPasteChips(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    if (state.pasting || state.view3d || state.wallDraw != null || state.measure != null) return
    val canCopy = state.multi.isEmpty() && vm.clipOf(state) != null
    val clip = state.clipboard
    if (!canCopy && clip == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canCopy) FilledTonalButton(onClick = { vm.copy() }) { Text("⧉ Copia") }
        if (clip != null) FilledTonalButton(onClick = vm::startPaste) { Text("📋 Incolla ${clipLabel(clip)}") }
    }
}

/** Scheda della modalità "Incolla": cosa toccare, e Fine. */
@Composable
fun PasteCard(state: EditorUiState, vm: EditorViewModel) {
    val clip = state.clipboard?.takeIf { state.pasting } ?: return
    val what = clipLabel(clip)
    val hint = when (clip) {
        is Clip.FixtureClip ->
            if (clip.fixture.kind.mount == Mount.Wall) "Tocca un muro per incollare: $what"
            else "Tocca il punto della stanza in cui incollare: $what"
        is Clip.OpeningClip -> "Tocca un muro per incollare: $what"
        is Clip.GroupClip -> "Tocca dove mettere la copia ($what)"
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "📋 $hint. Ogni tocco una copia, con le stesse impostazioni.",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = vm::stopPaste) { Text("Fine", color = MaterialTheme.colorScheme.inversePrimary) }
        }
    }
}

/** Anteprima tratteggiata della copia sotto il puntatore (sul computer), per gli oggetti della pianta. */
internal fun DrawScope.drawPastePreview(state: EditorUiState) {
    if (!state.pasting) return
    val clip = state.clipboard as? Clip.GroupClip ?: return
    val at = state.pasteCursor ?: return
    drawItemOutlines(clip.source, clip.items, state.camera, state.levelHeight, offset = at - clip.center, dashed = true)
}
