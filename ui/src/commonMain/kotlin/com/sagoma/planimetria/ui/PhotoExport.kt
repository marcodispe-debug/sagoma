package com.sagoma.planimetria.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import com.sagoma.planimetria.access.OutputFormat
import com.sagoma.planimetria.access.Rights
import com.sagoma.planimetria.assets.AssetPlanner
import com.sagoma.planimetria.assets.MaterialResolution
import com.sagoma.planimetria.assets.isAvailable
import com.sagoma.planimetria.assets.remote.AssetPriority
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.testi.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.TimeSource

/** Lato lungo della foto: 3840 pixel, come uno schermo 4K (in orizzontale 3840 × 2160 con la vista in 16:9). */
const val PHOTO_LONG_SIDE = 3840

/** Attesa massima dei materiali 4K dal server prima di fare comunque la foto (con i 2K dove mancano). */
private const val PHOTO_MAX_WAIT_MS = 180_000L

/** Se per tanto tempo non arriva niente di nuovo dal server, si smette di aspettare (i 4K che mancano non ci sono). */
private const val PHOTO_QUIET_MS = 20_000L

/**
 * Restituisce l'azione "📷 Foto 4K": chiede al server le mappe 4K dei materiali della scena, aspetta che arrivino (o che non
 * arrivi più niente), fa disegnare la vista a 3840 pixel di lato lungo, aggiunge il marchio se l'account lo prevede e chiede dove
 * salvare il PNG. La vista resta usabile mentre la foto si prepara.
 */
@Composable
fun rememberPhoto4k(state: EditorUiState, view: View3DState): () -> Unit {
    val platform = LocalPlatform.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    return action@{
        if (view.photoBusy) return@action
        val scene = view.currentScene ?: return@action
        view.photoBusy = true
        platform.toast(T.vista3D.fotoInPreparazione)
        scope.launch {
            try {
                // 1. Materiali 4K della scena (solo sul server): si chiedono e si aspettano.
                val store = platform.assetStore
                val hd = "/${MaterialResolution.K4.folder}/"
                val wanted = AssetPlanner.forScene(scene, resolution = MaterialResolution.K4).map { it.path }.filter { hd in it }
                fun missing() = wanted.filter { !store.isAvailable(it) }
                for (p in missing()) platform.assetRequests.request(p, AssetPriority.Scene)
                val start = TimeSource.Monotonic.markNow()
                var quiet = TimeSource.Monotonic.markNow()
                var seen = platform.assetRequests.version.value
                while (missing().isNotEmpty() && start.elapsedNow().inWholeMilliseconds < PHOTO_MAX_WAIT_MS) {
                    delay(500)
                    val v = platform.assetRequests.version.value
                    if (v != seen) { seen = v; quiet = TimeSource.Monotonic.markNow() }
                    else if (quiet.elapsedNow().inWholeMilliseconds > PHOTO_QUIET_MS) break
                }
                val lessSharp = missing().isNotEmpty()
                // 2. La foto, disegnata dal renderer sul suo thread.
                val shot = suspendCancellableCoroutine { cont -> view.renderPhoto(PHOTO_LONG_SIDE) { cont.resume(it) } }
                if (shot == null) {
                    platform.toast(T.vista3D.fotoNonRiuscita)
                    return@launch
                }
                // 3. Marchio del piano gratuito, come sulle altre esportazioni (la vista 3D è un'uscita a sé).
                val watermark = !Rights.cleanOutput(platform.account, OutputFormat.View3D, state.plan.rooms.map { it.id })
                val image = if (watermark) withWatermark(shot, measurer, density) else shot
                val png = platform.encodePng(image)
                if (png == null) {
                    platform.toast(T.vista3D.fotoNonRiuscita)
                    return@launch
                }
                platform.saveFile("sagoma-3d-${isoDate(platform.now())}.png", "image/png", png) { ok ->
                    platform.toast(
                        when {
                            !ok -> T.vari.esportazioneNonRiuscita
                            lessSharp -> T.vista3D.fotoMaterialiMenoDefiniti
                            watermark -> T.vista3D.fotoSalvataMarchio
                            else -> T.vista3D.fotoSalvata
                        },
                    )
                }
            } finally {
                view.photoBusy = false
            }
        }
    }
}

/** La foto con sopra il marchio, in proporzione all'immagine come nelle altre esportazioni. */
private fun withWatermark(shot: ImageBitmap, measurer: androidx.compose.ui.text.TextMeasurer, density: androidx.compose.ui.unit.Density): ImageBitmap {
    val out = ImageBitmap(shot.width, shot.height)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(out), Size(shot.width.toFloat(), shot.height.toFloat())) {
        drawImage(shot)
        with(Watermark) { drawWatermark(measurer, Watermark.exportLetterPx(size.width, size.height)) }
    }
    return out
}
