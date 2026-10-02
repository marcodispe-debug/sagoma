package com.sagoma.planimetria.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.persistence.ProjectInfo

/**
 * Elenco dei progetti: apri, nuovo, rinomina e dati del cliente, duplica (varianti), elimina, esporta e
 * importa un progetto come file .sagoma da mandare o tenere in copia.
 */
@Composable
fun ProjectsScreen(onOpen: (Long) -> Unit) {
    val platform = LocalPlatform.current
    val store = platform.projects
    var version by remember { mutableIntStateOf(0) } // si incrementa per rileggere l'elenco
    val projects = remember(version) { store.list() }
    var editing by remember { mutableStateOf<ProjectInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProjectInfo?>(null) }

    fun export(p: ProjectInfo) {
        val bytes = runCatching { store.export(p.id) }.getOrNull()
        if (bytes == null) { platform.toast("Esportazione non riuscita"); return }
        val name = p.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "progetto" }
        platform.saveFile("$name.sagoma", "application/octet-stream", bytes) { ok ->
            platform.toast(if (ok) "Progetto esportato" else "Esportazione non riuscita")
        }
    }
    fun import() = platform.openFile(listOf(ANY_FILE)) { file ->
        if (file == null) return@openFile
        val p = runCatching { store.import(file.bytes) }.getOrNull()
        if (p == null) platform.toast("Il file non è un progetto valido")
        else { platform.toast("Importato: ${p.name}"); version++ }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Progetti", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { creating = true }) { Text("+ Nuovo progetto") }
            OutlinedButton(onClick = { import() }) { Text("Importa") }
        }
        HorizontalDivider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { p ->
                ProjectCard(
                    p,
                    onOpen = { onOpen(p.id) },
                    onEdit = { editing = p },
                    onDuplicate = { store.duplicate(p.id, "${p.name} (copia)"); version++ },
                    onExport = { export(p) },
                    onDelete = if (projects.size > 1) ({ confirmDelete = p }) else null,
                )
            }
        }
    }

    if (creating) ProjectInfoDialog(ProjectInfo(0, ""), title = "Nuovo progetto", onDismiss = { creating = false }) { p ->
        creating = false
        val created = store.create(p.name, p.client, p.address)
        store.update(created.copy(author = p.author))
        onOpen(created.id)
    }
    editing?.let { e ->
        ProjectInfoDialog(e, title = "Dati del progetto", onDismiss = { editing = null }) { p ->
            store.update(p)
            editing = null
            version++
        }
    }
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Eliminare \"${p.name}\"?") },
            text = { Text("Il progetto viene cancellato da questo telefono. Se vuoi tenerne una copia, esportalo prima.") },
            confirmButton = { TextButton(onClick = { store.delete(p.id); confirmDelete = null; version++ }) { Text("Elimina", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun ProjectCard(p: ProjectInfo, onOpen: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onExport: () -> Unit, onDelete: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                val details = listOf(p.client, p.address).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
                if (p.modified > 0) Text(
                    "Modificato il " + LocalPlatform.current.formatDate(p.modified, withTime = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("⋮") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Apri") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Nome e dati del cliente") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplica (variante)") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Esporta file .sagoma") }, onClick = { menu = false; onExport() })
                    if (onDelete != null) DropdownMenuItem(text = { Text("Elimina", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** Nome del progetto, cliente, indirizzo e progettista: finiscono nel cartiglio delle tavole PDF. */
@Composable
fun ProjectInfoDialog(initial: ProjectInfo, title: String, onDismiss: () -> Unit, onSave: (ProjectInfo) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var client by remember { mutableStateOf(initial.client) }
    var address by remember { mutableStateOf(initial.address) }
    var author by remember { mutableStateOf(initial.author) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectAllTextField(value = name, onValueChange = { name = it }, label = "Nome del progetto", modifier = Modifier.fillMaxWidth())
                SelectAllTextField(value = client, onValueChange = { client = it }, label = "Cliente", modifier = Modifier.fillMaxWidth())
                SelectAllTextField(value = address, onValueChange = { address = it }, label = "Indirizzo", modifier = Modifier.fillMaxWidth())
                SelectAllTextField(value = author, onValueChange = { author = it }, label = "Progettista", modifier = Modifier.fillMaxWidth())
                Text("Questi dati compaiono nel cartiglio delle tavole PDF.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(initial.copy(name = name.trim(), client = client.trim(), address = address.trim(), author = author.trim())) }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
