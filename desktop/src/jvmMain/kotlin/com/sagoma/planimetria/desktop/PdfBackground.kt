package com.sagoma.planimetria.desktop

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Prima pagina di un PDF (catasto, progetto) come immagine PNG da usare come pianta di sfondo, con Apache PDFBox. Il lato lungo
 * è circa `maxSide` pixel (al massimo 300 dpi, così una tavola piccola non diventa enorme); null se il file non si legge.
 */
internal object PdfBackground {
    fun firstPagePng(bytes: ByteArray, maxSide: Int): ByteArray? = runCatching {
        Loader.loadPDF(bytes).use { doc ->
            if (doc.numberOfPages == 0) return null
            val box = doc.getPage(0).mediaBox
            // Punti tipografici (1/72 di pollice) → dpi per arrivare a maxSide sul lato lungo (la pagina girata non cambia il lato lungo).
            val dpi = (72f * maxSide / max(box.width, box.height)).coerceIn(36f, 300f)
            val img = PDFRenderer(doc).renderImageWithDPI(0, dpi, ImageType.RGB)
            ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        }
    }.getOrNull()
}
