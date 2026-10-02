package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.roundToInt

/** Immagini e PDF con Skia: in comune tra computer e browser. */
object SkiaImages {
    fun decode(bytes: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    fun encodePng(image: ImageBitmap): ByteArray? =
        runCatching { Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes }.getOrNull()

    /** Immagine ridotta a `maxSide` pixel sul lato lungo, in JPEG. */
    fun importImage(bytes: ByteArray, maxSide: Int): ImportedImage? = runCatching {
        val img = Image.makeFromEncoded(bytes)
        val k = minOf(1f, maxSide.toFloat() / max(img.width, img.height))
        val w = (img.width * k).roundToInt().coerceAtLeast(1)
        val h = (img.height * k).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.clear(0xFFFFFFFF.toInt())
        surface.canvas.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), SamplingMode.LINEAR, null, true)
        val jpeg = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)?.bytes ?: return null
        ImportedImage(jpeg, w, h)
    }.getOrNull()

    /** PDF con ogni pagina disegnata a `dpi` punti per pollice e salvata in JPEG ([RasterPdf]). */
    fun rasterPdf(pages: List<PdfPage>, density: Density, dpi: Int = 300): ByteArray {
        val k = dpi / 72f
        val out = pages.map { p ->
            val w = (p.width * k).roundToInt()
            val h = (p.height * k).roundToInt()
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
                scale(k, pivot = Offset.Zero) { p.draw(this) }
            }
            val jpeg = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, 92)!!.bytes
            RasterPdf.Page(p.width, p.height, jpeg, w, h)
        }
        return RasterPdf.write(out)
    }
}
