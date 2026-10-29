package com.sagoma.planimetria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Laying
import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.model.LayingStart
import com.sagoma.planimetria.model.MaterialLayout
import com.sagoma.planimetria.model.MaterialPieces
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import com.sagoma.planimetria.model.ProPalette
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.testi.T
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Colori scelti di recente con il selettore libero (per questa sessione), il più recente per primo. */
object RecentColors {
    val list = mutableStateListOf<Long>()
    fun add(argb: Long) {
        list.remove(argb)
        list.add(0, argb)
        while (list.size > 8) list.removeAt(list.lastIndex)
    }
}

/**
 * Finitura di una parete: pittura di qualsiasi colore (tavolozza estesa, colori recenti o selettore libero) e,
 * se si vuole, un materiale fotografico della libreria con fin dove arriva da terra. I rivestimenti disegnati
 * di prima (carta da parati, piastrelle, mattoni…) non si scelgono più: in un progetto che li ha restano
 * finché non si cambiano, e si possono togliere.
 */
@Composable
fun FinishPicker(label: String, finish: WallFinish, onChange: (WallFinish) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FreeColorPicker(T.finiture.pittura, finish.argb) { onChange(finish.copy(argb = it)) }
        val old = finish.covering != Covering.None && finish.covering != Covering.Material
        if (old) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Text(T.finiture.rivestimentoDiPrima(T.lingua.minuscola(finish.covering.label)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(finish.copy(covering = Covering.None, coveringArgb = null, coveringHeight = null)) }) {
                Text(T.schedaStanza.togli, color = MaterialTheme.colorScheme.error)
            }
        }
        MaterialPicker(T.finiture.materiale, finish.material.takeIf { finish.covering == Covering.Material }, forFloor = false, allowNone = true) { id ->
            onChange(
                if (id == null) finish.copy(covering = Covering.None, coveringArgb = null, coveringHeight = null, material = null)
                else finish.copy(
                    covering = Covering.Material, coveringArgb = null, material = id,
                    coveringHeight = finish.coveringHeight.takeIf { finish.covering == Covering.Material },
                    layout = MaterialPieces.relay(finish.layout, finish.material, id),
                ),
            )
        }
        if (finish.covering == Covering.Material && finish.material != null) {
            MaterialLayoutControls(finish.layout, finish.material) { onChange(finish.copy(layout = it)) }
        }
        if (finish.covering != Covering.None) {
            val full = finish.coverHeight == null
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Checkbox(checked = full, onCheckedChange = { on ->
                    onChange(finish.copy(coveringHeight = if (on) WallFinish.FULL_HEIGHT else 120.0))
                })
                Text(T.finiture.tuttaParete, style = MaterialTheme.typography.bodyMedium)
            }
            if (!full) {
                val h = finish.coverHeight ?: 120.0
                Text(T.finiture.altoCmTerra(h.roundToInt()), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = h.toFloat(),
                    onValueChange = { onChange(finish.copy(coveringHeight = (it / 5).roundToInt() * 5.0)) },
                    valueRange = 20f..260f,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

/**
 * Verso e misura dei pezzi di un materiale fotografico (parquet nell'altro verso, doghe più larghe, posa in diagonale) e,
 * se la foto lo permette, la posa a pezzi: schema, misure dei pezzi, fuga e suo colore, punto di partenza. I cursori si
 * applicano quando si lasciano.
 */
@Composable
fun MaterialLayoutControls(layout: MaterialLayout, material: String?, pieces: Boolean = true, onChange: (MaterialLayout) -> Unit) {
    var rotation by remember(layout.rotation) { mutableStateOf(layout.rotation.toFloat()) }
    var scale by remember(layout.scale) { mutableStateOf((layout.scale * 100).toFloat()) }
    val item = MaterialCatalog.item(material)
    // Senza `pieces` (es. l'anta di una porta) la foto non si posa a pezzi: solo verso e misura.
    val canLay = pieces && MaterialPieces.canLay(material)
    val laying = layout.laying?.takeIf { canLay }
    Column(Modifier.fillMaxWidth().padding(end = 8.dp)) {
        if (item != null && pieces) {
            if (canLay) {
                Choice(T.finiture.posa, listOf(T.finiture.comeNellaFoto, T.finiture.aPezzi), if (laying == null) 0 else 1) { i ->
                    onChange(layout.copy(laying = if (i == 0) null else MaterialPieces.defaultLaying(item)))
                }
            } else Text(T.finiture.fotoGiaPosata, style = MaterialTheme.typography.bodySmall)
        }
        Text(T.finiture.verso(rotation.roundToInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Slider(
            value = rotation,
            onValueChange = { rotation = (it / 5).roundToInt() * 5f },
            onValueChangeFinished = { onChange(layout.copy(rotation = rotation.toDouble())) },
            valueRange = 0f..180f,
            steps = 35,
        )
        if (laying != null && item != null) {
            LayingControls(laying, item) { onChange(layout.copy(laying = it)) }
        } else {
            Text(T.finiture.misuraPezzi(scale.roundToInt()), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = scale,
                onValueChange = { scale = (it / 5).roundToInt() * 5f },
                onValueChangeFinished = { onChange(layout.copy(scale = scale / 100.0)) },
                valueRange = (MaterialLayout.MIN_SCALE * 100).toFloat()..(MaterialLayout.MAX_SCALE * 100).toFloat(),
            )
        }
        if (layout != MaterialLayout()) TextButton(onClick = { onChange(MaterialLayout()) }) { Text(T.finiture.comeNelCatalogo) }
    }
}

/** Lati dei pezzi (cm) che si possono scegliere: i formati più comuni di piastrelle, listoni e lastre. */
private val PIECE_SIDES = listOf(
    2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 7.5, 8.0, 9.0, 10.0, 12.0, 12.5, 14.0, 15.0, 18.0, 20.0, 22.5, 25.0, 30.0, 33.0, 40.0,
    45.0, 50.0, 60.0, 75.0, 80.0, 90.0, 100.0, 120.0, 150.0, 160.0, 180.0, 200.0, 240.0, 280.0, 300.0,
)

/** Larghezze della fuga (mm). */
private val JOINTS_MM = listOf(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0)

/** Colori della fuga più usati: bianco, grigi, antracite, nero, sabbia, beige, tortora, marrone. */
private val JOINT_COLORS = listOf(
    0xFFF4F2EE, 0xFFD3D0CA, 0xFFA7A49E, 0xFF8C8984, 0xFF4A4946, 0xFF1E1E1E, 0xFFD8C8A8, 0xFFC5B392, 0xFF9C8E7E, 0xFF6E5139,
)

/** Numero con al più un decimale, senza «,0». */
private fun short(v: Double): String =
    if (kotlin.math.abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString() else com.sagoma.planimetria.geometry.formatDecimal(v, 1)

/** Schema, misure dei pezzi, fuga e partenza della posa a pezzi. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun LayingControls(laying: Laying, item: MaterialCatalog.Item, onChange: (Laying) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in LayingPattern.entries) {
            androidx.compose.material3.FilterChip(selected = laying.pattern == p, onClick = { onChange(laying.copy(pattern = p)) }, label = { Text(p.label) })
        }
    }
    StopSlider(laying.width, PIECE_SIDES, { T.finiture.larghezzaPezzo(short(it)) }) { w ->
        onChange(laying.copy(width = w, length = maxOf(w, laying.length)))
    }
    StopSlider(laying.length, PIECE_SIDES, { T.finiture.lunghezzaPezzo(short(it)) }) { l ->
        onChange(laying.copy(length = l, width = minOf(l, laying.width)))
    }
    StopSlider(laying.joint * 10, JOINTS_MM, { T.finiture.fuga(short(it)) }) { onChange(laying.copy(joint = it / 10)) }
    // Colore della fuga: quello della foto (se ha le fughe), i colori più usati, quello scelto e uno qualsiasi.
    val photo = (MaterialPieces.of(item.id) as? MaterialPieces.Pieces)?.groutArgb
    val base = listOfNotNull(photo) + JOINT_COLORS
    // Un colore scelto fuori dalla tavolozza va in testa, così si vede subito quale è.
    val colors = ((if (laying.jointArgb in base) base else listOf(laying.jointArgb) + base) + RecentColors.list).distinct()
    var custom by remember { mutableStateOf(false) }
    Text(T.finiture.coloreFuga, style = MaterialTheme.typography.labelMedium)
    ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 2.dp, end = 8.dp)) {
        item {
            Box(
                Modifier
                    .size(30.dp)
                    .border(1.dp, Color(0x33000000), CircleShape)
                    .padding(2.dp)
                    .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), CircleShape)
                    .clickable { custom = true },
            )
        }
        items(colors) { c ->
            val isSelected = c == laying.jointArgb
            Box(
                Modifier
                    .size(30.dp)
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                    .padding(if (isSelected) 5.dp else 2.dp)
                    .background(Color(c), CircleShape)
                    .clickable { onChange(laying.copy(jointArgb = c)) },
            )
        }
    }
    if (custom) CustomColorDialog(laying.jointArgb, onDismiss = { custom = false }) {
        custom = false
        RecentColors.add(it)
        onChange(laying.copy(jointArgb = it))
    }
    Choice(
        T.finiture.partenza, listOf(T.finiture.dalCentro, T.finiture.daUnAngolo), LayingStart.entries.indexOf(laying.start),
        Modifier.padding(top = 6.dp),
    ) { onChange(laying.copy(start = LayingStart.entries[it])) }
}

/**
 * Cursore sui valori `stops`: mostra il valore scelto (anche se non è tra quelli, come le misure prese dalla foto) finché non
 * lo si sposta, e lo applica quando lo si lascia.
 */
@Composable
private fun StopSlider(value: Double, stops: List<Double>, label: (Double) -> String, onChange: (Double) -> Unit) {
    val nearest = stops.indices.minBy { kotlin.math.abs(stops[it] - value) }
    var index by remember(value) { mutableStateOf<Int?>(null) }
    Text(label(index?.let { stops[it] } ?: value), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    Slider(
        value = (index ?: nearest).toFloat(),
        onValueChange = { index = it.roundToInt().coerceIn(0, stops.lastIndex) },
        onValueChangeFinished = { index?.let { onChange(stops[it]) } },
        valueRange = 0f..stops.lastIndex.toFloat(),
        steps = stops.size - 2,
    )
}

/**
 * Pallini della tavolozza estesa, con i colori recenti in testa e il pulsante per un colore qualsiasi.
 * `first`: colori con il loro nome da mettere prima della tavolozza (es. i legni degli infissi).
 */
@Composable
fun FreeColorPicker(label: String, argb: Long, first: List<Pair<String, Long>> = emptyList(), onSelect: (Long) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val colors = (RecentColors.list + first.map { it.second } + ProPalette.colors.map { it.second }).distinct()
    Column(Modifier.fillMaxWidth()) {
        Text("$label: ${first.firstOrNull { it.second == argb }?.first ?: ProPalette.nameOf(argb)}", style = MaterialTheme.typography.labelMedium)
        ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 2.dp, end = 8.dp)) {
            item {
                // Arcobaleno: apre il selettore libero.
                Box(
                    Modifier
                        .size(34.dp)
                        .border(1.dp, Color(0x33000000), CircleShape)
                        .padding(2.dp)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), CircleShape)
                        .clickable { custom = true },
                )
            }
            items(colors) { c ->
                val isSelected = c == argb
                Box(
                    Modifier
                        .size(34.dp)
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                        .padding(if (isSelected) 5.dp else 2.dp)
                        .background(Color(c), CircleShape)
                        .clickable { onSelect(c) },
                )
            }
        }
    }
    if (custom) CustomColorDialog(argb, onDismiss = { custom = false }) {
        custom = false
        RecentColors.add(it)
        onSelect(it)
    }
}

/** Colore qualsiasi: tinta, saturazione, luminosità oppure codice esadecimale. */
@Composable
private fun CustomColorDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val hsv = remember { FloatArray(3).also { colorToHsv(initial.toInt(), it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    fun current(): Long = hsvToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL
    var hex by remember { mutableStateOf(com.sagoma.planimetria.geometry.hex6(initial)) }
    fun syncHex() { hex = com.sagoma.planimetria.geometry.hex6(current()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.finiture.colorePersonalizzato) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(Color(current()), RoundedCornerShape(10.dp)).border(1.dp, Color(0x33000000), RoundedCornerShape(10.dp)))
                fun hsv(h: Float, s: Float, v: Float) = Color(hsvToColor(floatArrayOf(h, s, v)))
                // Sopra ogni cursore la sfumatura che si ottiene spostandolo.
                @Composable
                fun strip(colors: List<Color>) = Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).background(Brush.horizontalGradient(colors), RoundedCornerShape(5.dp)))
                Text(T.finiture.tinta, style = MaterialTheme.typography.labelMedium)
                strip((0..6).map { hsv(it * 60f, 0.8f, 0.9f) })
                Slider(value = hue, onValueChange = { hue = it; syncHex() }, valueRange = 0f..360f)
                Text(T.finiture.saturazione, style = MaterialTheme.typography.labelMedium)
                strip(listOf(hsv(hue, 0f, value), hsv(hue, 1f, value)))
                Slider(value = sat, onValueChange = { sat = it; syncHex() })
                Text(T.finiture.luminosita, style = MaterialTheme.typography.labelMedium)
                strip(listOf(Color.Black, hsv(hue, sat, 1f)))
                Slider(value = value, onValueChange = { value = it; syncHex() })
                OutlinedTextField(
                    value = hex,
                    onValueChange = { t ->
                        hex = t.removePrefix("#").take(6).uppercase()
                        hex.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { v ->
                            val out = FloatArray(3)
                            colorToHsv((0xFF000000 or v).toInt(), out)
                            hue = out[0]; sat = out[1]; value = out[2]
                        }
                    },
                    label = { Text(T.finiture.codiceEsC9d3bf) },
                    prefix = { Text("#") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current()) }) { Text(T.finiture.usaQuestoColore) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T.comuni.annulla) } },
    )
}

/**
 * Materiali fotografici della libreria: categorie e campioni. `forFloor` mostra quelli
 * adatti ai pavimenti, altrimenti quelli per le pareti, senza le categorie `exclude`; con `allowNone` c'è anche "Nessuno".
 */
@Composable
fun MaterialPicker(
    label: String, selected: String?, forFloor: Boolean, allowNone: Boolean = false, exclude: Set<String> = emptySet(), onSelect: (String?) -> Unit,
) {
    val all = remember(forFloor, exclude) { MaterialCatalog.items.filter { (if (forFloor) it.forFloor else it.forWall) && it.category !in exclude } }
    if (all.isEmpty()) return
    val categories = remember(all) { all.map { it.category }.distinct() }
    var category by remember(selected) { mutableStateOf(MaterialCatalog.item(selected)?.category?.takeIf { it in categories } ?: categories.first()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${MaterialCatalog.item(selected)?.nome ?: T.lingua.minuscola(T.finiture.nessuno)}", style = MaterialTheme.typography.labelMedium)
        ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(categories) { c ->
                androidx.compose.material3.FilterChip(selected = c == category, onClick = { category = c }, label = { Text(T.nomi.categoria(c)) })
            }
        }
        ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            if (allowNone) item {
                Swatch(T.finiture.nessuno, null, selected == null) { onSelect(null) }
            }
            items(all.filter { it.category == category }) { m ->
                Swatch(m.nome, m.id, m.id == selected) { onSelect(m.id) }
            }
        }
    }
}

@Composable
private fun Swatch(label: String, id: String?, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        val thumb = id?.let { FurnitureAssets.materialThumb(it) }
        Box(
            Modifier
                .size(64.dp)
                .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000), RoundedCornerShape(10.dp))
                .padding(if (isSelected) 4.dp else 1.dp)
                .background(Color(0xFFF1F0EC), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) androidx.compose.foundation.Image(thumb, contentDescription = label, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(7.dp)))
            else Text("—", style = MaterialTheme.typography.titleMedium)
        }
        // Fino a tre righe: con due «Parquet rovere miele» perdeva l'ultima parola senza segno.
        Text(
            label, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

