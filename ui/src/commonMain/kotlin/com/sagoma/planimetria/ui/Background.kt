package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.UnderlayTool
import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** Progetto aperto: le immagini di sfondo stanno tra i suoi file. */
val LocalProjectId = staticCompositionLocalOf<Long?> { null }

/** Immagini di sfondo già lette (sono grandi: se ne tengono poche, le ultime usate). */
private object UnderlayImages {
    private val cache = LinkedHashMap<String, ImageBitmap>()
    fun get(key: String): ImageBitmap? = cache.remove(key)?.also { cache[key] = it }
    fun put(key: String, img: ImageBitmap) {
        cache[key] = img
        while (cache.size > 2) cache.remove(cache.keys.first())
    }
}

/** Lato lungo massimo dell'immagine salvata (px): abbastanza per leggere le quote, senza esaurire la memoria. */
private const val MAX_SIDE = 3000

@Composable
fun rememberUnderlayImage(u: Underlay?): ImageBitmap? {
    val platform = LocalPlatform.current
    val projectId = LocalProjectId.current
    val key = if (u != null && projectId != null) "$projectId/${u.file}" else null
    val img by produceState(key?.let { UnderlayImages.get(it) }, key) {
        if (key == null || u == null || projectId == null) { value = null; return@produceState }
        UnderlayImages.get(key)?.let { value = it; return@produceState }
        value = platform.projects.readFile(projectId, u.file)?.let(platform::decodeImage)?.also { UnderlayImages.put(key, it) }
    }
    return img
}

internal fun DrawScope.drawUnderlay(u: Underlay, img: ImageBitmap, cam: Camera) {
    if (!u.visible) return
    translate(cam.toScreenX(u.origin.x), cam.toScreenY(u.origin.y)) {
        scale((u.cmPerPx * cam.scale).toFloat(), pivot = Offset.Zero) {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), alpha = u.opacity.toFloat(), filterQuality = FilterQuality.Low)
        }
    }
}

/** Punti toccati per la taratura e la linea tra loro. */
internal fun DrawScope.drawCalibration(t: UnderlayTool.Calibrate, cam: Camera, measurer: TextMeasurer) {
    val color = Color(0xFFD7263D)
    val pts = listOfNotNull(t.first, t.second).map { Offset(cam.toScreenX(it.x), cam.toScreenY(it.y)) }
    if (pts.size == 2) drawLine(color, pts[0], pts[1], strokeWidth = 2.dp.toPx())
    for (p in pts) {
        drawCircle(color, 7.dp.toPx(), p, alpha = 0.25f)
        drawLine(color, p - Offset(10.dp.toPx(), 0f), p + Offset(10.dp.toPx(), 0f), strokeWidth = 1.5.dp.toPx())
        drawLine(color, p - Offset(0f, 10.dp.toPx()), p + Offset(0f, 10.dp.toPx()), strokeWidth = 1.5.dp.toPx())
    }
    pts.forEachIndexed { i, p ->
        val l = measurer.measure(if (i == 0) "A" else "B", TextStyle(fontSize = 13.sp, color = color))
        drawText(l, topLeft = p + Offset(8.dp.toPx(), -8.dp.toPx() - l.size.height))
    }
}

/**
 * "Pianta di sfondo": si carica una foto o un PDF della pianta esistente, la si tara su una misura nota e la
 * si sposta sotto al disegno; poi si ricalcano le stanze sopra. Con `tracing` è il primo passo di "Ricalca una piantina",
 * scelto al posto della prima stanza.
 */
@Composable
fun BackgroundDialog(state: EditorUiState, vm: EditorViewModel, projectId: Long, tracing: Boolean = false, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val u = state.plan.underlay
    var loading by remember { mutableStateOf(false) }

    fun import(file: PickedFile?, pdf: Boolean) {
        if (file == null) return
        loading = true
        scope.launch {
            val img = platform.importImage(file.bytes, pdf, MAX_SIDE)
            val name = "sfondo-${platform.now()}.jpg"
            val saved = img != null && runCatching { platform.projects.writeFile(projectId, name, img.jpeg) }.isSuccess
            loading = false
            if (img == null || !saved) {
                // Letta ma non salvata: di solito manca spazio (nel browser c'è un limite di pochi MB).
                platform.toast(if (img == null) T.sfondo.impossibileLeggereFile else T.sfondo.nonSpazioSalvareSfondo)
                return@launch
            }
            val w = img.width
            val h = img.height
            // Prima della taratura: l'immagine larga 12 m, centrata sulla pianta (o all'origine).
            val cmPerPx = 1200.0 / max(w, h)
            val center = Openings.planBounds(state.plan)?.center ?: (state.camera.toWorld(0f, 0f) + Vec2(600.0, 400.0))
            vm.setUnderlay(Underlay(name, w, h, origin = center - Vec2(w * cmPerPx / 2, h * cmPerPx / 2), cmPerPx = cmPerPx, opacity = 0.5))
            vm.setUnderlayTool(UnderlayTool.Calibrate())
            onDismiss()
        }
    }

    AlertDialog(
        // Mentre si legge il file non si chiude: l'immagine arriverebbe dopo, a finestra già chiusa.
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(if (tracing) T.sfondo.ricalcaPiantina else T.sfondo.piantaSfondo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.tutorial?.takeIf { it.step == TutorialStep.TraceLoad }?.let { t ->
                    Text(
                        T.sfondo.guidaRicalcoPasso(t.number, t.total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (tracing) {
                    Text(
                        if (platform.pdfAsBackground) T.sfondo.caricaPiantinaHaiFoto
                        else T.sfondo.caricaPiantinaHaiImmagine,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        T.sfondo.tocchiDueEstremiMisura,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else Text(
                    T.sfondo.mettiSottoDisegnoPianta,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !loading, onClick = { platform.openFile(listOf("image/*")) { import(it, pdf = false) } }) {
                        Text(if (u == null) T.sfondo.fotoImmagine else T.sfondo.altraImmagine)
                    }
                    if (platform.pdfAsBackground) {
                        OutlinedButton(enabled = !loading, onClick = { platform.openFile(listOf("application/pdf")) { import(it, pdf = true) } }) { Text(T.sfondo.pdf) }
                    }
                }
                if (loading) Text(T.sfondo.caricamento, style = MaterialTheme.typography.bodySmall)
                if (u != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(T.sfondo.visibile, Modifier.weight(1f))
                        Switch(checked = u.visible, onCheckedChange = { vm.setUnderlayVisible(it) })
                    }
                    Text(T.sfondo.trasparenza((u.opacity * 100).roundToInt()), style = MaterialTheme.typography.labelLarge)
                    // Dal vivo: la piantina sotto la finestra cambia mentre si trascina.
                    Slider(
                        value = u.opacity.toFloat(), onValueChange = { vm.previewUnderlayOpacity(it.toDouble()) },
                        valueRange = 0.1f..1f, onValueChangeFinished = vm::endUnderlayOpacity,
                    )
                    Text(
                        T.sfondo.scalaAttuale1Px(fmt2(u.cmPerPx), fmt2(u.widthCm / 100), fmt2(u.heightCm / 100)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.setUnderlayTool(UnderlayTool.Calibrate()); onDismiss() }) { Text(T.sfondo.tara) }
                        OutlinedButton(onClick = { vm.setUnderlayTool(UnderlayTool.Move); onDismiss() }) { Text(T.sfondo.sposta) }
                    }
                    TextButton(onClick = { vm.setUnderlay(null) }) { Text(T.sfondo.togliSfondo, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text(if (tracing && u == null) T.comuni.indietro else T.comuni.chiudi) } },
    )
}

/** Scheda in alto durante taratura o spostamento dello sfondo, con la richiesta della misura reale. */
@Composable
fun UnderlayToolCard(state: EditorUiState, vm: EditorViewModel) {
    val tool = state.underlayTool ?: return
    val hint = when (tool) {
        UnderlayTool.Move -> T.sfondo.trascinaDitoSpostarePianta
        is UnderlayTool.Calibrate -> when {
            tool.first == null -> T.sfondo.taraturaToccaInizioMisura
            tool.second == null -> T.sfondo.oraToccaFineMisura
            else -> T.sfondo.scriviMisuraRealeB
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hint, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = { vm.setUnderlayTool(null) }) { Text(T.comuni.fine, color = MaterialTheme.colorScheme.inversePrimary) }
        }
    }
    if (tool is UnderlayTool.Calibrate && tool.first != null && tool.second != null) {
        var text by remember(tool) { mutableStateOf("") }
        val cm = text.replace(',', '.').toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { vm.setUnderlayTool(UnderlayTool.Calibrate()) },
            title = { Text(T.sfondo.misuraReale) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(T.sfondo.quantoLungaDavveroDistanza)
                    SelectAllTextField(value = text, onValueChange = { text = it }, label = "cm", modifier = Modifier.fillMaxWidth())
                    Text(T.sfondo.piuMisuraLungaPiu, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = cm != null && cm > 0, onClick = { vm.calibrateUnderlay(cm!!) }) { Text(T.sfondo.tara2) } },
            dismissButton = { TextButton(onClick = { vm.setUnderlayTool(UnderlayTool.Calibrate()) }) { Text(T.sfondo.rifai) } },
        )
    }
}
