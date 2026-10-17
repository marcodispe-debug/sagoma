package com.sagoma.planimetria.ui.sketch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.ui.LocalPlatform
import com.sagoma.planimetria.ui.hybrid.HybridScanText

/**
 * Percorso B: uno schizzo ancora da misurare ([com.sagoma.planimetria.model.Room.sketchPending]). Scheda NON bloccante tra quelle
 * in alto dell'editor (in basso copriva la barra di porte e finestre), con "Avvia scansione" sulla stessa stanza. Niente "Chiudi":
 * chiusa, non era intuitivo ritrovare la scansione nella scheda della stanza; l'avviso resta finché la stanza non è misurata.
 * Non compare per le stanze create con le misure (percorso A).
 */
@Composable
fun SketchPendingBanner(room: Room, vm: EditorViewModel) {
    val t = LocalSketchScanText.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.sketchBanner + " · " + room.name, color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.startSketchScan(room.id) }) { Text(t.startScan) }
        }
    }
}

/** Percorso B: la scansione guidata dallo schizzo, a schermo intero; il risultato aggiorna la STESSA stanza. */
@Composable
fun SketchScanHost(room: Room, vm: EditorViewModel) {
    val scanner = LocalPlatform.current.roomScanner
    if (scanner == null) {
        LaunchedEffect(Unit) { vm.cancelSketchScan() }
        return
    }
    // Dove c'è il prototipo della scansione ibrida, prima si sceglie quale scansione fare; la guidata resta quella di sempre.
    var mode by remember { mutableStateOf(if (scanner.hasHybridPrototype) null else ScanMode.GUIDED) }
    val onOutcome: (com.sagoma.planimetria.ui.SketchScanOutcome?) -> Unit = { outcome ->
        if (outcome == null) vm.cancelSketchScan()
        else if (!vm.applySketchScan(room.id, outcome.cornersAr, outcome.positionOverrides, outcome.widthOverrides)) vm.cancelSketchScan()
    }
    Dialog(
        // Il tasto indietro NON chiude da solo: la schermata chiede conferma prima di perdere le pareti confermate.
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            when (mode) {
                null -> ScanModeChoice(onChoose = { mode = it }, onCancel = { vm.cancelSketchScan() })
                ScanMode.GUIDED -> scanner.SketchScreen(room, onOutcome)
                ScanMode.HYBRID -> scanner.HybridScreen(room, onOutcome)
            }
        }
    }
}

private enum class ScanMode { GUIDED, HYBRID }

/** Scelta tra la scansione guidata (attuale) e il PROTOTIPO ibrido, riconoscibile come tale. */
@Composable
private fun ScanModeChoice(onChoose: (ScanMode) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Text(HybridScanText.chooseTitle, style = MaterialTheme.typography.titleLarge)
        Button(onClick = { onChoose(ScanMode.GUIDED) }, modifier = Modifier.fillMaxWidth()) { Text(HybridScanText.chooseGuided) }
        Text(HybridScanText.chooseGuidedHint, style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.OutlinedButton(onClick = { onChoose(ScanMode.HYBRID) }, modifier = Modifier.fillMaxWidth()) { Text(HybridScanText.chooseHybrid) }
        Text(HybridScanText.chooseHybridHint, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onCancel) { Text(HybridScanText.exit) }
    }
}
