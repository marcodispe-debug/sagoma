package com.sagoma.planimetria.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.assets.AssetPaths
import com.sagoma.planimetria.assets.remote.AssetPriority
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.model.FurnitureCatalog

/** "+ Arredi" (versione pro): apre il catalogo dei mobili. */
@Composable
fun FurnitureButton(vm: EditorViewModel, buttonModifier: Modifier = Modifier, contentPadding: PaddingValues = ButtonDefaults.ContentPadding) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = buttonModifier, contentPadding = contentPadding) { Text("🛋 Arredi", maxLines = 1) }
    if (open) FurnitureCatalogDialog(onDismiss = { open = false }) { item ->
        open = false
        vm.addFurniture(item)
    }
}

/** Catalogo: ricerca, categorie, schede con la miniatura del modello, nome e misure; crediti delle fonti. */
@Composable
private fun FurnitureCatalogDialog(onDismiss: () -> Unit, onPick: (FurnitureCatalog.Item) -> Unit) {
    val categories = remember { FurnitureCatalog.categories }
    var category by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var query by remember { mutableStateOf("") }
    var credits by remember { mutableStateOf(false) }
    val shown = if (query.isBlank()) FurnitureCatalog.items.filter { it.category == category } else FurnitureCatalog.search(query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Arredi (${FurnitureCatalog.items.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cerca: divano, lavello, lampada…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isBlank()) ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) })
                    }
                }
                if (shown.isEmpty()) Text("Nessun arredo con questo nome.", style = MaterialTheme.typography.bodyMedium)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 440.dp),
                ) {
                    items(shown, key = { it.model }) { item -> CatalogCard(item) { onPick(item) } }
                }
                TextButton(onClick = { credits = true }) { Text("Fonti e licenze dei modelli") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
    if (credits) CreditsDialog { credits = false }
}

@Composable
private fun CatalogCard(item: FurnitureCatalog.Item, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 88.dp, height = 70.dp).background(Color(0xFFF6F5F2), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                // Leggere la versione fa ricomporre la scheda quando arriva un asset: la miniatura si rilegge, e se manca si richiede di nuovo.
                val assetRequests = LocalPlatform.current.assetRequests
                val assetsVersion by assetRequests.version.collectAsState()
                val thumb = FurnitureAssets.thumbnail(item.model)
                if (thumb == null) LaunchedEffect(item.model, assetsVersion) {
                    assetRequests.request(AssetPaths.furnitureThumbnail(item.model), AssetPriority.Visible)
                }
                if (thumb != null) {
                    Image(thumb, contentDescription = item.label, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 84.dp, height = 66.dp))
                } else Canvas(Modifier.size(width = 88.dp, height = 70.dp)) {
                    // Senza miniatura: il simbolo in pianta, in scala.
                    val k = minOf((size.width - 16.dp.toPx()) / item.width.toFloat(), (size.height - 16.dp.toPx()) / item.depth.toFloat())
                    translate(size.width / 2, size.height / 2) {
                        furnitureSymbol(item.symbolKind, item.width.toFloat() * k, item.depth.toFloat() * k, Color(0xFF6B7178))
                    }
                }
            }
            Text(item.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp).height(30.dp))
            Text(
                "${formatCm(item.width)}×${formatCm(item.depth)}×${formatCm(item.height)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Da dove vengono i modelli: fonte, autore e licenza (CC-BY chiede di citare l'autore). */
@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    // Pubblico dominio (CC0): basta un elenco per fonte. CC-BY: per ogni modello nome, autore e collegamento.
    val (open, attributed) = remember { FurnitureCatalog.items.partition { it.license.uppercase().startsWith("CC0") || it.license.contains("Public", true) || it.license.contains("pubblico", true) } }
    val groups = remember {
        open.groupBy { Triple(it.source.substringBefore(" (").ifBlank { "—" }, it.author, it.license) }
            .map { (k, v) -> k to v.map { it.label } }
            .sortedBy { it.first.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fonti e licenze") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        "I modelli 3D degli arredi vengono da Poly Haven (polyhaven.com, licenza CC0: pubblico dominio), dalle " +
                            "librerie gratuite di Sweet Home 3D (sweethome3d.com) e da Sketchfab (sketchfab.com) tramite la raccolta " +
                            "Objaverse. Quelli con licenza CC-BY sono elencati uno per uno con autore e fonte. I materiali dei pavimenti e " +
                            "delle pareti e le luci ambiente vengono da ambientCG e Poly Haven (CC0). Grazie a tutti gli autori.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (attributed.isNotEmpty()) item {
                    Column {
                        Text("Modelli con licenza CC-BY (attribuzione)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Libreria Scopia di Sweet Home 3D: © Space Mushrooms e Scopia Visual Interfaces Systems, s.l. (scopia.es), " +
                                "licenza Creative Commons Attribuzione 3.0. Modelli di Sketchfab: licenza Creative Commons Attribuzione 4.0.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(attributed.sortedBy { it.source }) { m ->
                    Text(
                        "“${m.label}” di ${m.author.ifBlank { "autore sconosciuto" }} · ${m.license} · ${m.source.substringAfter("(").removeSuffix(")")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (groups.isNotEmpty()) item {
                    Text("Modelli di pubblico dominio (CC0)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                items(groups) { (key, labels) ->
                    Column {
                        Row { Text(key.first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                        Text("Autore: ${key.second.ifBlank { "—" }} · Licenza: ${key.third.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                        Text(labels.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}
