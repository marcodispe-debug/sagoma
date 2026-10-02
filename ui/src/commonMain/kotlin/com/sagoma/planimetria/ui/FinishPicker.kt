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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.MaterialCatalog
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import com.sagoma.planimetria.model.ProPalette
import com.sagoma.planimetria.model.WallFinish
import kotlin.math.roundToInt

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
 * Versione pro: finitura di una parete. Pittura di qualsiasi colore (tavolozza estesa, colori recenti o
 * selettore libero) e rivestimento (carta da parati, piastrelle, mattoni, pietra, boiserie, perlinato) con
 * il suo colore e fin dove arriva da terra.
 */
@Composable
fun FinishPicker(label: String, finish: WallFinish, onChange: (WallFinish) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FreeColorPicker("Pittura", finish.argb) { onChange(finish.copy(argb = it)) }
        val coverings = Covering.entries.filter { it != Covering.Material || MaterialCatalog.items.any { m -> m.forWall } }
        ModelPicker("Rivestimento", coverings, finish.covering, { it.label }, { c ->
            val material = if (c == Covering.Material) finish.material ?: MaterialCatalog.items.firstOrNull { it.forWall }?.id else finish.material
            onChange(finish.copy(covering = c, coveringArgb = null, coveringHeight = null, material = material))
        }) { coveringPreview(it, if (it == finish.covering) finish.coverArgb else it.defaultArgb, finish.argb) }
        if (finish.covering == Covering.Material) {
            MaterialPicker("Materiale", finish.material, forFloor = false) { onChange(finish.copy(material = it)) }
        }
        if (finish.covering != Covering.None) {
            if (finish.covering != Covering.Material) {
                FreeColorPicker("Colore ${finish.covering.label.lowercase()}", finish.coverArgb) { onChange(finish.copy(coveringArgb = it)) }
            }
            val full = finish.coverHeight == null
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Checkbox(checked = full, onCheckedChange = { on ->
                    onChange(finish.copy(coveringHeight = if (on) WallFinish.FULL_HEIGHT else 120.0))
                })
                Text("Tutta la parete", style = MaterialTheme.typography.bodyMedium)
            }
            if (!full) {
                val h = finish.coverHeight ?: 120.0
                Text("Alto ${h.roundToInt()} cm da terra", style = MaterialTheme.typography.labelMedium)
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

/** Pallini della tavolozza estesa, con i colori recenti in testa e il pulsante per un colore qualsiasi. */
@Composable
fun FreeColorPicker(label: String, argb: Long, onSelect: (Long) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val colors = (RecentColors.list + ProPalette.colors.map { it.second }).distinct()
    Column(Modifier.fillMaxWidth()) {
        Text("$label: ${ProPalette.nameOf(argb)}", style = MaterialTheme.typography.labelMedium)
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
        title = { Text("Colore personalizzato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(Color(current()), RoundedCornerShape(10.dp)).border(1.dp, Color(0x33000000), RoundedCornerShape(10.dp)))
                fun hsv(h: Float, s: Float, v: Float) = Color(hsvToColor(floatArrayOf(h, s, v)))
                // Sopra ogni cursore la sfumatura che si ottiene spostandolo.
                @Composable
                fun strip(colors: List<Color>) = Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).background(Brush.horizontalGradient(colors), RoundedCornerShape(5.dp)))
                Text("Tinta", style = MaterialTheme.typography.labelMedium)
                strip((0..6).map { hsv(it * 60f, 0.8f, 0.9f) })
                Slider(value = hue, onValueChange = { hue = it; syncHex() }, valueRange = 0f..360f)
                Text("Saturazione", style = MaterialTheme.typography.labelMedium)
                strip(listOf(hsv(hue, 0f, value), hsv(hue, 1f, value)))
                Slider(value = sat, onValueChange = { sat = it; syncHex() })
                Text("Luminosità", style = MaterialTheme.typography.labelMedium)
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
                    label = { Text("Codice (es. C9D3BF)") },
                    prefix = { Text("#") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current()) }) { Text("Usa questo colore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/**
 * Materiali fotografici della libreria (versione pro): categorie e campioni. `forFloor` mostra quelli
 * adatti ai pavimenti, altrimenti quelli per le pareti; con `allowNone` c'è anche "Nessuno".
 */
@Composable
fun MaterialPicker(label: String, selected: String?, forFloor: Boolean, allowNone: Boolean = false, onSelect: (String?) -> Unit) {
    val all = remember(forFloor) { MaterialCatalog.items.filter { if (forFloor) it.forFloor else it.forWall } }
    if (all.isEmpty()) return
    val categories = remember(all) { all.map { it.category }.distinct() }
    var category by remember(selected) { mutableStateOf(MaterialCatalog.item(selected)?.category?.takeIf { it in categories } ?: categories.first()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${MaterialCatalog.item(selected)?.label ?: "nessuno"}", style = MaterialTheme.typography.labelMedium)
        ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(categories) { c ->
                androidx.compose.material3.FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) })
            }
        }
        ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            if (allowNone) item {
                Swatch("Nessuno", null, selected == null) { onSelect(null) }
            }
            items(all.filter { it.category == category }) { m ->
                Swatch(m.label, m.id, m.id == selected) { onSelect(m.id) }
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
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Anteprima di un rivestimento: un pezzo di parete dipinta con sotto il rivestimento. */
fun DrawScope.coveringPreview(c: Covering, coverArgb: Long, paintArgb: Long) {
    val w = size.width
    val h = size.height
    val cover = Color(coverArgb)
    drawRect(Color(paintArgb))
    val top = if (c.defaultHeight == null) 0f else h * 0.35f
    if (c == Covering.None) return
    val dark = lerp(cover, Color.Black, 0.25f)
    val grout = lerp(cover, Color(0xFF8C8C88), 0.5f)
    val s = h / 12f
    fun rect(x: Float, y: Float, rw: Float, rh: Float, col: Color) {
        val x0 = x.coerceAtLeast(0f)
        val x1 = (x + rw).coerceAtMost(w)
        val y0 = y.coerceAtLeast(top)
        val y1 = (y + rh).coerceAtMost(h)
        if (x1 > x0 && y1 > y0) drawRect(col, Offset(x0, y0), Size(x1 - x0, y1 - y0))
    }
    when (c) {
        Covering.None -> Unit
        Covering.Material -> {
            // Un campione a scacchi di colori caldi: "materiale vero".
            rect(0f, top, w, h, cover)
            var x = 0f
            var k = 0
            while (x < w) { if (k % 2 == 0) rect(x, top, s * 2f, h, lerp(cover, Color.Black, 0.08f)); x += s * 2f; k++ }
        }
        Covering.Wallpaper -> {
            rect(0f, 0f, w, h, lerp(cover, Color.White, 0.45f))
            var x = 0f
            while (x < w) { rect(x, 0f, s * 0.8f, h, cover); rect(x + s * 1.4f, 0f, s * 0.16f, h, cover); x += s * 2.6f }
        }
        Covering.Tiles, Covering.Metro -> {
            rect(0f, top, w, h, cover)
            val tw = if (c == Covering.Tiles) s * 2.5f else s * 2.4f
            val th = if (c == Covering.Tiles) s * 2.5f else s * 1.2f
            var row = 0
            var y = h
            while (y > top) {
                rect(0f, y - 0.8f, w, 1.6f, grout)
                var x = if (c == Covering.Metro && row % 2 == 1) tw / 2 else 0f
                while (x < w) { rect(x - 0.8f, y - th, 1.6f, th, grout); x += tw }
                y -= th; row++
            }
        }
        Covering.Boards -> {
            rect(0f, top, w, h, cover)
            var x = 0f
            while (x < w) { rect(x, top, 1.6f, h, dark); x += s * 1.6f }
        }
        Covering.Wainscot -> {
            rect(0f, top, w, h, cover)
            var x = s
            while (x < w) {
                rect(x, top + s, s * 4f, h - top - s * 2f, lerp(cover, Color.Black, 0.05f))
                rect(x, top + s, s * 4f, 2f, dark); rect(x, h - s - 2f, s * 4f, 2f, dark)
                rect(x, top + s, 2f, h - top - s * 2f, dark); rect(x + s * 4f - 2f, top + s, 2f, h - top - s * 2f, dark)
                x += s * 5f
            }
        }
        Covering.Brick, Covering.Stone -> {
            rect(0f, top, w, h, Color(0xFFCCC7BD))
            val bh = if (c == Covering.Brick) s * 1.1f else s * 2.4f
            var y = h
            var row = 0
            while (y > top) {
                var x = if (row % 2 == 1) -s * 2f else 0f
                var k = 0
                while (x < w) {
                    val bw = if (c == Covering.Brick) s * 3.6f else s * (3f + ((row * 7 + k * 5) % 4))
                    val shade = ((row * 3 + k * 5) % 5 - 2) * 0.05f
                    val col = if (shade >= 0) lerp(cover, Color.White, shade) else lerp(cover, Color.Black, -shade)
                    rect(x + 1f, y - bh + 1f, bw - 2f, bh - 2f, col)
                    x += bw; k++
                }
                y -= bh; row++
            }
        }
    }
}
