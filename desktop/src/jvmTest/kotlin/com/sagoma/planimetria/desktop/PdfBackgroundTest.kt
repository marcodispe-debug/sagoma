package com.sagoma.planimetria.desktop

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PDF come pianta di sfondo sul computer: la prima pagina diventa un'immagine col lato lungo richiesto. */
class PdfBackgroundTest {
    private fun a4Landscape(): ByteArray = PDDocument().use { doc ->
        val page = PDPage(PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width))
        doc.addPage(page)
        // Un "muro" nero: deve restare scuro nell'immagine.
        PDPageContentStream(doc, page).use { it.addRect(100f, 100f, 400f, 20f); it.fill() }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test
    fun `prima pagina in PNG con il lato lungo richiesto`() {
        val png = assertNotNull(PdfBackground.firstPagePng(a4Landscape(), maxSide = 2000))
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertTrue(img.width > img.height, "orizzontale")
        assertTrue(img.width in 1990..2010, "lato lungo ${img.width}")
        // Il rettangolo pieno (x 100..500 pt, y dal basso 100..120 pt) è nero; lo sfondo è bianco.
        val k = img.width / PDRectangle.A4.height
        assertEquals(0, img.getRGB((300 * k).toInt(), img.height - (110 * k).toInt()) and 0xFFFFFF)
        assertEquals(0xFFFFFF, img.getRGB(5, 5) and 0xFFFFFF)
    }

    @Test
    fun `file che non è un PDF`() {
        assertNull(PdfBackground.firstPagePng("non sono un pdf".toByteArray(), maxSide = 2000))
    }
}
