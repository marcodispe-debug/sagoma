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
import com.sagoma.planimetria.testi.T

/** Nome di ciò che è negli appunti ("calorifero", "porta", "3 oggetti"…). */
internal fun clipLabel(clip: Clip): String = when (clip) {
    is Clip.FixtureClip -> clip.fixture.kind.label.lowercase()
    is Clip.OpeningClip -> clip.opening.kind.label.lowercase()
    is Clip.GroupClip -> clip.items.singleOrNull()?.let {
        when (it) {
            is GroupItem.RoomItem -> T.vari.stanza
            is GroupItem.FurnitureItem -> T.vari.arredo2
            is GroupItem.ColumnItem -> T.vari.colonna2
            is GroupItem.BeamItem -> T.vari.trave3
            is GroupItem.FreeWallItem -> T.vari.muro3
            is GroupItem.StairItem -> T.vari.scala2
            is GroupItem.RulerItem -> T.vari.metro
            is GroupItem.DimensionItem -> T.vari.quota
            is GroupItem.AnnotationItem -> T.vari.testoTesto
        }
    } ?: T.vari.oggetti(clip.items.size)
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
        if (canCopy) FilledTonalButton(onClick = { vm.copy() }) { Text(T.vari.copia) }
        if (clip != null) FilledTonalButton(onClick = vm::startPaste) { Text(T.vari.incolla(clipLabel(clip))) }
    }
}

/** Scheda della modalità "Incolla": cosa toccare, e Fine. */
@Composable
fun PasteCard(state: EditorUiState, vm: EditorViewModel) {
    val clip = state.clipboard?.takeIf { state.pasting } ?: return
    val what = clipLabel(clip)
    val hint = when (clip) {
        is Clip.FixtureClip ->
            if (clip.fixture.kind.mount == Mount.Wall) T.vari.toccaMuroIncollare(what)
            else T.vari.toccaPuntoStanzaCui(what)
        is Clip.OpeningClip -> T.vari.toccaMuroIncollare(what)
        is Clip.GroupClip -> T.vari.toccaDoveMettereCopia(what)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                T.vari.ogniToccoCopiaStesse(hint),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = vm::stopPaste) { Text(T.comuni.fine, color = MaterialTheme.colorScheme.inversePrimary) }
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
