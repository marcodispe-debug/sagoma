package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.FloorDialog
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan

/**
 * Pulsante del piano, in alto a sinistra: mostra il piano che si sta modificando e apre il menu per
 * cambiare piano, aggiungerne uno o modificare quello corrente.
 */
@Composable
fun FloorButton(building: Building, vm: EditorViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
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
            DropdownMenuItem(text = { Text("+ Aggiungi un piano sopra") }, onClick = { open = false; vm.openFloorDialog(null) })
            DropdownMenuItem(text = { Text("✎ Modifica «${building.floor.name}»") }, onClick = { open = false; vm.openFloorDialog(building.current) })
        }
    }
}

/**
 * Finestra per un nuovo piano (nome, interpiano, copia delle stanze del piano più alto) o per modificare
 * un piano esistente (nome, interpiano, eliminazione).
 */
@Composable
fun FloorEditDialog(dialog: FloorDialog, building: Building, vm: EditorViewModel) {
    val index = dialog.index
    val existing = index?.let { building.floors.getOrNull(it) }
    var name by remember(dialog) { mutableStateOf(existing?.name ?: Building.floorName(building.floors.size)) }
    var level by remember(dialog) { mutableStateOf(formatCm(existing?.levelHeight ?: building.floors.last().levelHeight)) }
    var auto by remember(dialog) { mutableStateOf(existing?.autoLevel ?: true) }
    var copyRooms by remember(dialog) { mutableStateOf(true) }
    var confirmDelete by remember(dialog) { mutableStateOf(false) }
    // Automatico: altezza della stanza più alta più il solaio. Un piano nuovo parte dalle stanze che copia (o dal valore standard).
    val autoValue = Floor.autoLevelHeight(existing?.plan ?: if (copyRooms) building.floors.last().plan else FloorPlan())
    val levelValue = if (auto) autoValue else parsePositive(level)?.takeIf { it in 150.0..1000.0 }

    if (confirmDelete && existing != null && index != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${existing.name}?") },
            text = { Text("Verranno tolte tutte le stanze e le scale di questo piano. Potrai recuperarlo con ↶ Annulla in alto.") },
            confirmButton = { TextButton(onClick = { vm.deleteFloor(index) }) { Text("Elimina", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Mantieni") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = vm::closeFloorDialog,
        title = { Text(if (existing == null) "Nuovo piano" else "Modifica piano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectAllTextField(name, { name = it }, "Nome", Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = auto, onCheckedChange = { on ->
                        auto = on
                        if (!on) level = formatCm(autoValue) // passando a manuale si parte dal valore calcolato
                    })
                    Text("Interpiano automatico", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
                if (auto) {
                    Text("Interpiano: ${formatCm(autoValue)} cm", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Calcolato come altezza della stanza più alta del piano più ${Floor.SLAB.toInt()} cm di solaio: si aggiorna se cambi l'altezza delle stanze.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SelectAllTextField(
                        level, { level = it }, "Interpiano", Modifier.fillMaxWidth(),
                        numeric = true, suffix = "cm", isError = levelValue == null,
                    )
                    Text(
                        "Dal pavimento di questo piano a quello del piano di sopra: l'altezza del soffitto più lo spessore del solaio (di solito ${Floor.SLAB.toInt()} cm). " +
                            "Resta quello che scrivi, anche se cambi l'altezza delle stanze.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Le scale di questo piano salgono di questa altezza.", style = MaterialTheme.typography.bodySmall)
                if (existing == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copyRooms, onCheckedChange = { copyRooms = it })
                        Text(
                            "Parti dai muri di «${building.floors.last().name}» (poi li modifichi)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (existing != null && building.floors.size > 1) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Elimina questo piano", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = levelValue != null,
                onClick = {
                    val h = levelValue ?: return@TextButton
                    if (index == null) vm.addFloor(name, h, copyRooms, auto) else vm.updateFloor(index, name, h, auto)
                },
            ) { Text(if (existing == null) "Aggiungi" else "Salva") }
        },
        dismissButton = { TextButton(onClick = vm::closeFloorDialog) { Text("Annulla") } },
    )
}
