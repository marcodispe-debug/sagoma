package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.WallDistances
import com.sagoma.planimetria.geometry.WallSide
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.testi.T
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * «📏 Distanze dai muri» nella scheda di un oggetto: acceso, le distanze si vedono sulla pianta e qui si possono scrivere
 * (l'oggetto si sposta finché la distanza è quella). Porte e finestre: i due angoli del muro e «Al centro del muro».
 */
@Composable
internal fun WallGapsInfo(sel: Selection, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val target = wallGapTarget(sel) ?: return
    val drag = nudgeTarget(sel) ?: return
    val on by remember(vm) { vm.state.map { it.wallGaps }.distinctUntilChanged() }.collectAsState(vm.state.value.wallGaps)
    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = vm::toggleWallGaps) { Text(if (on) T.schedaStanza.nascondiDistanzeDaiMuri else T.schedaStanza.distanzeDaiMuri) }
        if (!on) return@Column
        val gaps = WallDistances.of(plan, target, levelHeight).sortedBy { it.side.ordinal }
        // Un campo per lato (key): fuori casa i lati con un muro possono cambiare spostando l'oggetto.
        for (row in gaps.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (g in row) key(g.side) {
                CommitNumberField(sideLabel(g.side), g.distance, Modifier.weight(1f), allowZero = true) { v -> vm.setWallDistance(drag, target, g.side, v) }
            }
        }
        if (sel is Selection.Opening && gaps.size == 2) TextButton(onClick = { vm.centerOnWall(drag, target) }) {
            Text(T.schedaStanza.alCentroDelMuro)
        }
    }
}

private fun sideLabel(s: WallSide) = when (s) {
    WallSide.Left -> T.schedaStanza.muroSinistra
    WallSide.Right -> T.schedaStanza.muroDestra
    WallSide.Up -> T.schedaStanza.muroSopra
    WallSide.Down -> T.schedaStanza.muroSotto
}
