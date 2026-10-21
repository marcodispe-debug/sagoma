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
import com.sagoma.planimetria.testi.T

/** "+ Arredi": apre il catalogo dei mobili. */
@Composable
fun FurnitureButton(vm: EditorViewModel, buttonModifier: Modifier = Modifier, contentPadding: PaddingValues = ButtonDefaults.ContentPadding) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = buttonModifier, contentPadding = contentPadding) { Text(T.arredi.arredi, maxLines = 1) }
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
    val shown = if (query.isBlank()) FurnitureCatalog.visible.filter { it.category == category } else FurnitureCatalog.search(query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.arredi.arredi2(FurnitureCatalog.visible.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(T.arredi.cercaDivanoLavelloLampada) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isBlank()) ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(T.nomi.categoria(c)) })
                    }
                }
                if (shown.isEmpty()) Text(T.arredi.nessunArredoQuestoNome, style = MaterialTheme.typography.bodyMedium)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 440.dp),
                ) {
                    items(shown, key = { it.model }) { item -> CatalogCard(item) { onPick(item) } }
                }
                TextButton(onClick = { credits = true }) { Text(T.arredi.fontiLicenzeModelli) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T.comuni.chiudi) } },
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
                val kitchen = com.sagoma.planimetria.model.KitchenModules.parse(item.model)
                val parts = if (kitchen == null) com.sagoma.planimetria.geometry.GeneratedGeometry.parts(item.model, item.width, item.depth, item.height) else null
                // I mobili generati alla misura non hanno file da scaricare.
                if (thumb == null && kitchen == null && parts == null) LaunchedEffect(item.model, assetsVersion) {
                    assetRequests.request(AssetPaths.furnitureThumbnail(item.model), AssetPriority.Visible)
                }
                if (thumb != null) {
                    Image(thumb, contentDescription = item.nome, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 84.dp, height = 66.dp))
                } else if (kitchen != null) Canvas(Modifier.size(width = 88.dp, height = 70.dp)) {
                    // Modulo della cucina: visto dal davanti, con ante, cassetti e forno.
                    kitchenFrontView(kitchen, item.width, item.height, 6.dp.toPx())
                } else if (parts != null) Canvas(Modifier.size(width = 88.dp, height = 70.dp)) {
                    // Mobile generato alla misura: visto dal davanti.
                    partsFrontView(parts, item.width, item.height, 6.dp.toPx())
                } else Canvas(Modifier.size(width = 88.dp, height = 70.dp)) {
                    // Senza miniatura: il simbolo in pianta, in scala.
                    val k = minOf((size.width - 16.dp.toPx()) / item.width.toFloat(), (size.height - 16.dp.toPx()) / item.depth.toFloat())
                    translate(size.width / 2, size.height / 2) {
                        furnitureSymbol(item.symbolKind, item.width.toFloat() * k, item.depth.toFloat() * k, Color(0xFF6B7178))
                    }
                }
            }
            Text(item.nome, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp).height(30.dp))
            Text(
                T.arredi.testoTesto(formatCm(item.width), formatCm(item.depth), formatCm(item.height)),
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
            .map { (k, v) -> k to v.map { it.nome } }
            .sortedBy { it.first.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.arredi.fontiLicenze) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        T.arredi.modelli3dArrediVengono,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (attributed.isNotEmpty()) item {
                    Column {
                        Text(T.arredi.modelliLicenzaCcBy, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            T.arredi.libreriaScopiaSweetHome,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(attributed.sortedBy { it.source }) { m ->
                    Text(
                        T.arredi.di(m.nome, m.author.ifBlank { T.arredi.autoreSconosciuto }, m.license, m.source.substringAfter("(").removeSuffix(")")),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (groups.isNotEmpty()) item {
                    Text(T.arredi.modelliPubblicoDominioCc0, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                items(groups) { (key, labels) ->
                    Column {
                        Row { Text(key.first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                        Text(T.arredi.autoreLicenza(key.second.ifBlank { "—" }, key.third.ifBlank { "—" }), style = MaterialTheme.typography.bodySmall)
                        Text(labels.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T.comuni.chiudi) } },
    )
}
