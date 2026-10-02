package com.sagoma.planimetria.ui

/**
 * PDF minimo con un'immagine JPEG a tutta pagina per ogni foglio. Serve dove la piattaforma non sa creare
 * PDF vettoriali (computer, browser): le pagine si disegnano ad alta risoluzione e le misure della pagina
 * restano in punti, quindi la stampa al 100% è in scala esatta.
 */
object RasterPdf {
    /** Una pagina: misure in punti e immagine JPEG (larghezza e altezza in pixel). */
    class Page(val widthPt: Int, val heightPt: Int, val jpeg: ByteArray, val widthPx: Int, val heightPx: Int)

    fun write(pages: List<Page>): ByteArray {
        val out = Out()
        val offsets = mutableListOf<Int>()
        fun obj(n: Int, body: () -> Unit) {
            while (offsets.size < n) offsets += 0
            offsets[n - 1] = out.size
            out.text("$n 0 obj\n")
            body()
            out.text("\nendobj\n")
        }
        out.text("%PDF-1.4\n%âãÏÓ\n")
        // 1 = catalogo, 2 = elenco pagine, poi per ogni pagina: pagina, contenuto, immagine.
        val kids = pages.indices.joinToString(" ") { "${3 + it * 3} 0 R" }
        obj(1) { out.text("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) { out.text("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>") }
        pages.forEachIndexed { i, p ->
            val page = 3 + i * 3
            val content = page + 1
            val image = page + 2
            obj(page) {
                out.text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${p.widthPt} ${p.heightPt}] ")
                out.text("/Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>")
            }
            val stream = "q ${p.widthPt} 0 0 ${p.heightPt} 0 0 cm /Im0 Do Q"
            obj(content) { out.text("<< /Length ${stream.length} >>\nstream\n$stream\nendstream") }
            obj(image) {
                out.text("<< /Type /XObject /Subtype /Image /Width ${p.widthPx} /Height ${p.heightPx} /ColorSpace /DeviceRGB ")
                out.text("/BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n")
                out.bytes(p.jpeg)
                out.text("\nendstream")
            }
        }
        val xref = out.size
        out.text("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) out.text(o.toString().padStart(10, '0') + " 00000 n \n")
        out.text("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    /** Byte in uscita; il testo della struttura del PDF è solo ASCII (Latin-1 per la riga di intestazione). */
    private class Out {
        private var data = ByteArray(1 shl 16)
        var size = 0
            private set

        fun bytes(b: ByteArray) {
            if (size + b.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + b.size))
            b.copyInto(data, size)
            size += b.size
        }

        fun text(s: String) = bytes(ByteArray(s.length) { s[it].code.toByte() })
        fun toByteArray() = data.copyOf(size)
    }
}
