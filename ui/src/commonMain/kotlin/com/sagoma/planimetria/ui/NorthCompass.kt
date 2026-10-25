package com.sagoma.planimetria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.geometry.CalendarDay
import com.sagoma.planimetria.model.HouseSite
import com.sagoma.planimetria.model.Place
import com.sagoma.planimetria.model.Places
import com.sagoma.planimetria.testi.T
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Gradi della direzione da [center] a [p], in senso orario dall'alto dello schermo (0–360). */
private fun screenAngle(center: Offset, p: Offset): Double {
    val a = atan2((p.x - center.x).toDouble(), -(p.y - center.y).toDouble()) * 180.0 / PI
    return (a + 360.0) % 360.0
}

/** Nord trascinato: gradi interi, che si fermano sui quattro lati dello schermo quando ci si passa vicino (3°). */
private fun snapNorth(deg: Double): Double {
    val side = (deg / 90.0).roundToInt() * 90.0
    return (if (abs(deg - side) <= 3.0) side else deg.roundToInt().toDouble()) % 360.0
}

/**
 * Rosa dei venti sulla pianta: la N punta dove l'utente ha detto che è il nord. Si trascina per girarla (la pianta resta
 * ferma, si orienta solo il sole); un tocco apre la finestra con i gradi e la città.
 */
@Composable
fun NorthCompass(northDeg: Double, onNorth: (Double) -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(northDeg)
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val description = T.vista3D.bussola
    Surface(modifier.size(56.dp), shape = CircleShape, tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Box(
            Modifier
                .semantics { contentDescription = description }
                .pointerInput(Unit) { detectTapGestures { onOpen() } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        if ((change.position - center).getDistance() > 4f) {
                            val deg = snapNorth(screenAngle(center, change.position))
                            if (deg != current) onNorth(deg)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(56.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f
                rotate(northDeg.toFloat(), c) {
                    // Freccia: metà verso il nord colorata, metà verso il sud chiara; tacche su est, sud e ovest (sul nord c'è la N).
                    val north = Path().apply {
                        moveTo(c.x, c.y - r * 0.42f); lineTo(c.x + r * 0.2f, c.y); lineTo(c.x - r * 0.2f, c.y); close()
                    }
                    val south = Path().apply {
                        moveTo(c.x, c.y + r * 0.6f); lineTo(c.x + r * 0.2f, c.y); lineTo(c.x - r * 0.2f, c.y); close()
                    }
                    drawPath(north, primary)
                    drawPath(south, ink.copy(alpha = 0.25f))
                    for (i in 1 until 4) rotate(i * 90f, c) {
                        drawLine(ink.copy(alpha = 0.45f), Offset(c.x, c.y - r * 0.92f), Offset(c.x, c.y - r * 0.78f), strokeWidth = 2f)
                    }
                }
            }
            // La lettera N sta sulla punta della freccia, sempre dritta.
            val a = northDeg * PI / 180.0
            Text(
                "N",
                color = primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(
                    start = (if (kotlin.math.sin(a) > 0) (kotlin.math.sin(a) * 40).dp else 0.dp),
                    end = (if (kotlin.math.sin(a) < 0) (-kotlin.math.sin(a) * 40).dp else 0.dp),
                    top = (if (kotlin.math.cos(a) < 0) (-kotlin.math.cos(a) * 40).dp else 0.dp),
                    bottom = (if (kotlin.math.cos(a) > 0) (kotlin.math.cos(a) * 40).dp else 0.dp),
                ),
            )
        }
    }
}

/** Finestra "🧭 Nord e città": gradi del nord rispetto alla pianta e città della casa, tutto facoltativo. */
@Composable
fun HouseSiteDialog(site: HouseSite, onNorth: (Double) -> Unit, onPlace: (Place?) -> Unit, onDismiss: () -> Unit) {
    var degrees by remember { mutableStateOf(site.northDeg.roundToInt().toString()) }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.vista3D.nordCitta) },
        text = {
            Column {
                Text(T.vista3D.spiegazioneNord, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NorthCompass(site.northDeg, onNorth = { onNorth(it); degrees = it.roundToInt().toString() }, onOpen = {})
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = degrees,
                        onValueChange = { v ->
                            degrees = v.filter { it.isDigit() || it == '-' }.take(4)
                            degrees.toIntOrNull()?.let { onNorth(it.toDouble()) }
                        },
                        label = { Text(T.vista3D.nordGradi) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = { onNorth(0.0); degrees = "0" }) { Text(T.vista3D.nordInAlto) }
                Spacer(Modifier.height(4.dp))
                Text(T.vista3D.cittaScelta(site.place?.name ?: T.vista3D.nessunaCitta), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(T.vista3D.cercaCitta) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val found = remember(query) { Places.search(query) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    if (query.isBlank()) item {
                        PlaceRow(T.vista3D.nessunaCitta, site.place == null) { onPlace(null) }
                    }
                    items(found) { p -> PlaceRow(p.name, site.place?.name == p.name) { onPlace(p) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T.comuni.chiudi) } },
    )
}

@Composable
private fun PlaceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Giorni veloci per il sole: oggi (null), equinozi e solstizi dell'anno in corso. */
private fun quickDays(): List<Pair<String, CalendarDay?>> {
    val y = CalendarDay.today().year
    return listOf(
        T.vista3D.oggi to null,
        T.vista3D.marzo21 to CalendarDay(y, 3, 21),
        T.vista3D.giugno21 to CalendarDay(y, 6, 21),
        T.vista3D.settembre21 to CalendarDay(y, 9, 21),
        T.vista3D.dicembre21 to CalendarDay(y, 12, 21),
    )
}

/** Pulsante del giorno del sole ("📅 Oggi", "📅 21 dicembre"…): si sceglie da un elenco breve. */
@Composable
fun SunDayButton(view: View3DState, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val days = quickDays()
    val label = days.firstOrNull { it.second == view.day }?.first ?: T.vista3D.oggi
    Box {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (compact) 4.dp else 12.dp),
        ) { Text(if (compact) "📅" else "📅 $label", maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((name, d) in days) DropdownMenuItem(
                text = { Text(if (d == view.day) "✓ $name" else name) },
                onClick = { open = false; view.day = d; view.refresh() },
            )
        }
    }
}
