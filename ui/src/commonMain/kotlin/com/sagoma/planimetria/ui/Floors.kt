package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.FloorDialog
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Roof
import com.sagoma.planimetria.model.RoofCovering
import com.sagoma.planimetria.model.RoofKind
import com.sagoma.planimetria.testi.T

/**
 * Pulsante del piano, in alto a sinistra: mostra il piano che si sta modificando e apre il menu per
 * cambiare piano, aggiungerne uno o modificare quello corrente.
 */
@Composable
fun FloorButton(building: Building, vm: EditorViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    var roofOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp, vertical = 0.dp),
        ) {
            Text("🏠 ${building.floor.name} ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // Dall'alto verso il basso, come i piani di una casa.
            building.floors.withIndex().reversed().forEach { (i, f) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (i == building.current) "✓ " else "    ") + f.name,
                            fontWeight = if (i == building.current) FontWeight.Bold else null,
                        )
                    },
                    onClick = { open = false; vm.selectFloor(i) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(T.piani.aggiungiPianoSopra) }, onClick = { open = false; vm.openFloorDialog(null) })
            DropdownMenuItem(text = { Text(T.piani.aggiungiPianoSotto) }, onClick = { open = false; vm.openFloorDialog(null, below = true) })
            DropdownMenuItem(text = { Text(T.piani.modifica(building.floor.name)) }, onClick = { open = false; vm.openFloorDialog(building.current) })
            DropdownMenuItem(text = { Text(T.piani.tettoMenu) }, onClick = { open = false; roofOpen = true })
        }
    }
    if (roofOpen) RoofDialog(building, vm, onClose = { roofOpen = false })
}

/**
 * Tetto inclinato sopra l'ultimo piano: forma, pendenza, sporto della gronda, verso del colmo e manto. Ogni scelta vale subito
 * (si vede nel 3D e in pianta) e si annulla con ↶; «Piano» toglie il tetto.
 */
@Composable
fun RoofDialog(building: Building, vm: EditorViewModel, onClose: () -> Unit) {
    val roof = building.roof
    val top = building.floors.last()
    val shape = roof?.let { com.sagoma.planimetria.geometry.Roofs.plan(it, top.plan) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(T.piani.tetto) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(T.piani.tettoSopra(top.name), style = MaterialTheme.typography.bodySmall)
                if (com.sagoma.planimetria.geometry.Roofs.footprint(top.plan) == null) Text(T.piani.nessunaStanza, style = MaterialTheme.typography.bodyMedium)
                val kinds = listOf(null, RoofKind.Shed, RoofKind.Gable, RoofKind.Hip)
                Choice(
                    T.piani.forma, listOf(T.piani.tettoPiano, T.piani.unaFalda, T.piani.dueFalde, T.piani.padiglione), kinds.indexOf(roof?.kind),
                ) { i -> vm.setRoof(kinds[i]?.let { k -> (roof ?: Roof()).copy(kind = k) }) }
                if (roof != null) {
                    val pitch = roof.pitchDeg.roundToInt()
                    val percent = (kotlin.math.tan(roof.pitchDeg * kotlin.math.PI / 180) * 100).roundToInt()
                    Stepper(
                        T.piani.pendenza, "$pitch° · $percent%",
                        onMinus = if (roof.pitchDeg > Roof.MIN_PITCH) ({ vm.setRoof(roof.copy(pitchDeg = roof.pitchDeg - 5)) }) else null,
                        onPlus = if (roof.pitchDeg < Roof.MAX_PITCH) ({ vm.setRoof(roof.copy(pitchDeg = roof.pitchDeg + 5)) }) else null,
                    )
                    Stepper(
                        T.piani.sporto, "${roof.overhang.roundToInt()} cm",
                        onMinus = if (roof.overhang > 0) ({ vm.setRoof(roof.copy(overhang = (roof.overhang - 10).coerceAtLeast(0.0))) }) else null,
                        onPlus = if (roof.overhang < Roof.MAX_OVERHANG) ({ vm.setRoof(roof.copy(overhang = roof.overhang + 10)) }) else null,
                    )
                    // Il padiglione ha il colmo sempre sul lato lungo; una falda gira di un quarto alla volta (quattro versi).
                    if (roof.kind != RoofKind.Hip) OutlinedButton(onClick = { vm.setRoof(roof.copy(turn = roof.turn + 1)) }) {
                        Text(if (roof.kind == RoofKind.Shed) T.piani.giraFalda else T.piani.giraColmo)
                    }
                    val coverings = RoofCovering.entries
                    Choice(T.piani.manto, listOf(T.piani.tegole, T.piani.ardesia, T.piani.lamiera), coverings.indexOf(roof.covering)) { i ->
                        vm.setRoof(roof.copy(covering = coverings[i]))
                    }
                    shape?.let { Text(T.piani.altezzaColmo(it.ridgeHeight.roundToInt()), style = MaterialTheme.typography.bodyMedium) }
                    Text(T.piani.tettoNel3D, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(T.comuni.chiudi) } },
    )
}

/**
 * Finestra per un nuovo piano (nome, interpiano, copia delle stanze del piano più alto, o del più basso per un piano sotto)
 * o per modificare un piano esistente (nome, interpiano, eliminazione).
 */
@Composable
fun FloorEditDialog(dialog: FloorDialog, building: Building, vm: EditorViewModel) {
    val index = dialog.index
    val existing = index?.let { building.floors.getOrNull(it) }
    // Piano nuovo: parte dal piano accanto, il più alto (sopra) o il più basso (sotto).
    val next = if (dialog.below) building.floors.first() else building.floors.last()
    val newLevel = if (dialog.below) building.level(0) - 1 else building.level(building.floors.lastIndex) + 1
    var name by remember(dialog) { mutableStateOf(existing?.name ?: Building.floorName(newLevel)) }
    var level by remember(dialog) { mutableStateOf(formatCm(existing?.levelHeight ?: next.levelHeight)) }
    var auto by remember(dialog) { mutableStateOf(existing?.autoLevel ?: true) }
    var copyRooms by remember(dialog) { mutableStateOf(true) }
    var confirmDelete by remember(dialog) { mutableStateOf(false) }
    // Automatico: altezza della stanza più alta più il solaio. Un piano nuovo parte dalle stanze che copia (o dal valore standard).
    val autoValue = Floor.autoLevelHeight(existing?.plan ?: if (copyRooms) next.plan else FloorPlan())
    val levelValue = if (auto) autoValue else parsePositive(level)?.takeIf { it in 150.0..1000.0 }

    if (confirmDelete && existing != null && index != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(T.piani.eliminare(existing.name)) },
            text = { Text(T.piani.verrannoTolteTutteStanze) },
            confirmButton = { TextButton(onClick = { vm.deleteFloor(index) }) { Text(T.comuni.elimina, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(T.comuni.mantieni) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = vm::closeFloorDialog,
        title = { Text(if (existing == null) T.piani.nuovoPiano else T.piani.modificaPiano) },
        text = {
            // Scorre: col telefono in orizzontale le spiegazioni sotto i campi venivano tagliate.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectAllTextField(name, { name = it }, T.comuni.nome, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = auto, onCheckedChange = { on ->
                        auto = on
                        if (!on) level = formatCm(autoValue) // passando a manuale si parte dal valore calcolato
                    })
                    Text(T.piani.interpianoAutomatico, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
                if (auto) {
                    Text(T.piani.interpianoCm(formatCm(autoValue)), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        T.piani.calcolatoAltezzaStanzaPiu(Floor.SLAB.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SelectAllTextField(
                        level, { level = it }, T.piani.interpiano, Modifier.fillMaxWidth(),
                        numeric = true, suffix = "cm", isError = levelValue == null,
                    )
                    Text(
                        T.piani.pavimentoQuestoPianoQuello(Floor.SLAB.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(T.piani.scaleQuestoPianoSalgono, style = MaterialTheme.typography.bodySmall)
                if (existing == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copyRooms, onCheckedChange = { copyRooms = it })
                        Text(
                            T.piani.partiMuriLiModifichi(next.name),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (existing != null && building.floors.size > 1) {
                    TextButton(onClick = { confirmDelete = true }) { Text(T.piani.eliminaQuestoPiano, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = levelValue != null,
                onClick = {
                    val h = levelValue ?: return@TextButton
                    if (index == null) vm.addFloor(name, h, copyRooms, auto, below = dialog.below) else vm.updateFloor(index, name, h, auto)
                },
            ) { Text(if (existing == null) T.piani.aggiungi else T.comuni.salva) }
        },
        dismissButton = { TextButton(onClick = vm::closeFloorDialog) { Text(T.comuni.annulla) } },
    )
}
