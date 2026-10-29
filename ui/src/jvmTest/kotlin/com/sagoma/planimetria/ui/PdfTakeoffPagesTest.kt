package com.sagoma.planimetria.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.T
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Computo nella tavola PDF con tante righe (una casa grande, tanti arredi): finita la seconda colonna le righe scendevano
 * sopra il cartiglio e fuori dal foglio. Ora continuano in un foglio nuovo.
 */
class PdfTakeoffPagesTest {
    private fun result(rooms: Int, furniture: Int) = Takeoff.Result(
        rooms = List(rooms) { Takeoff.RoomLine("Piano terra", "Stanza ${it + 1}", 12.0, 14.0, 30.0, 2.0, 13.0, "Parquet", "Pittura", 1, 1) },
        floorMaterials = listOf(Takeoff.Total("Parquet", 12.0 * rooms, "m²")),
        wallFinishes = listOf(Takeoff.Total("Pittura", 30.0 * rooms, "m²")),
        furniture = List(furniture) { Takeoff.Item("Arredo $it", "80 × 40 × 70", 1, "Stanza 1") },
        fixtures = emptyList(),
    )

    @Test
    fun casaPiccolaUnFoglio() {
        assertEquals(1, PdfExport.takeoffSheets(result(8, 5), PdfExport.Paper.A4).size)
    }

    @Test
    fun tanteRigheContinuanoInUnAltroFoglio() {
        for (paper in PdfExport.Paper.entries) {
            val sheets = PdfExport.takeoffSheets(result(120, 80), paper)
            assertTrue(sheets.size > 1, "${paper.label}: ${sheets.size} fogli")
            val maxY = paper.h - PdfExport.MARGIN - PdfExport.BLOCK_H - 10
            for (sheet in sheets) for (l in sheet) assertTrue(l.y <= maxY, "${paper.label}: riga a ${l.y} oltre $maxY")
            // Nessuna riga persa: 1 titolo + intestazione + 120 stanze + totale, poi pavimenti (3), pareti (2), arredi (81).
            assertEquals(3 + 120 + 3 + 2 + 81, sheets.sumOf { it.size })
        }
    }

    @Test
    fun porteEFinestreConLAspettoSotto() {
        val res = result(3, 0).copy(
            openings = listOf(
                Takeoff.OpeningItem("Porta ad 1 anta", "Liscia, rovere", "80×210 cm", 4, "Cucina, Camera"),
                Takeoff.OpeningItem("Finestra 2 ante", "", "120×140 cm", 2, "Cucina"),
            ),
        )
        val lines = PdfExport.takeoffSheets(res, PdfExport.Paper.A4).single()
        val i = lines.indexOfFirst { it.title && it.cells[0] == "Porte e finestre" }
        assertTrue(i >= 0)
        assertEquals(listOf("4×", "Porta ad 1 anta", "80×210 cm", "Cucina, Camera"), lines[i + 1].cells)
        assertEquals(listOf("", "Liscia, rovere"), lines[i + 2].cells)
        // Senza aspetto, niente riga vuota sotto.
        assertEquals(listOf("2×", "Finestra 2 ante", "120×140 cm", "Cucina"), lines[i + 3].cells)
        assertEquals(i + 4, lines.size)
    }

    @Test
    fun tabellaLuceEAria() {
        val res = result(4, 0).copy(light = listOf(Takeoff.LightLine("Piano terra", "Soggiorno + Cucina", 19.1, 2.88), Takeoff.LightLine("Piano terra", "Camera", 14.82, 1.44)))
        val lines = PdfExport.takeoffSheets(res, PdfExport.Paper.A4).flatten()
        val at = lines.indexOfFirst { it.title && it.cells == listOf(com.sagoma.planimetria.testi.T.computo.luceAria) }
        assertTrue(at >= 0)
        // Titolo, intestazione, due ambienti e la nota.
        assertEquals(listOf("Soggiorno + Cucina", "19,10", "2,88", "1/6,6", "sì"), lines[at + 2].cells)
        assertEquals(listOf("Camera", "14,82", "1,44", "1/10,3", "no"), lines[at + 3].cells)
        assertEquals(at + 5, lines.size)
    }

    @Test
    fun tabellaSuperfici() {
        val res = result(4, 0).copy(areas = listOf(Takeoff.AreaLine("Interrato", 9.36, 14.96, 0.0, basement = true), Takeoff.AreaLine("Piano terra", 80.23, 115.93, 20.37, excluded = 7.62)))
        val lines = PdfExport.takeoffSheets(res, PdfExport.Paper.A4).flatten()
        val at = lines.indexOfFirst { it.title && it.cells == listOf(T.computo.superfici) }
        assertTrue(at >= 0)
        // Titolo, intestazione, due piani, la superficie commerciale e la nota; poi i pavimenti.
        assertEquals(listOf("Interrato", "9,36", "14,96", "0,00", ""), lines[at + 2].cells)
        assertEquals(listOf("Piano terra", "80,23", "115,93", "20,37", ""), lines[at + 3].cells)
        // 115,93 − 7,62 + 14,96 / 2 + 20,37 × 30%.
        assertEquals(listOf(T.computo.commerciale, "121,90", ""), lines[at + 4].cells)
        assertTrue(lines[at + 6].title)
    }

    @Test
    fun tabellaOpere() {
        val ground = com.sagoma.planimetria.geometry.Phases.Works(demolishM2 = 9.12, demolishM3 = 2.736, openingsClosed = 1, openingsClosedM2 = 1.68, openingsClosedM3 = 0.504)
        val first = com.sagoma.planimetria.geometry.Phases.Works(newM2 = 4.05, newM3 = 0.405)
        val res = result(4, 0).copy(works = listOf(Takeoff.WorksLine("Piano terra", ground), Takeoff.WorksLine("Primo piano", first)))
        val lines = PdfExport.takeoffSheets(res, PdfExport.Paper.A4).flatten()
        val at = lines.indexOfFirst { it.title && it.cells == listOf(T.computo.opere) }
        assertTrue(at >= 0)
        // Titolo, intestazione; per ogni piano il nome e le sue voci, poi il totale della casa e la nota.
        assertEquals(listOf(T.computo.voce, T.computo.numero, "m²", "m³"), lines[at + 1].cells)
        assertEquals(listOf("Piano terra"), lines[at + 2].cells)
        assertEquals(listOf(T.computo.muriDaDemolire, "", "9,12", "2,74"), lines[at + 3].cells)
        assertEquals(listOf(T.computo.apertureDaChiudere, "1", "1,68", "0,50"), lines[at + 4].cells)
        assertEquals(listOf("Primo piano"), lines[at + 5].cells)
        assertEquals(listOf(T.computo.muriNuovi, "", "4,05", "0,41"), lines[at + 6].cells)
        assertEquals(listOf(T.pdf.totale), lines[at + 7].cells)
        assertEquals(4, lines.drop(at + 8).takeWhile { !it.title }.size)
    }

    @Test
    fun noteDelComputoNonTagliateInNessunaLingua() {
        // Le note stanno in una riga sola della colonna (7,5 punti): oltre sarebbero tagliate con «…».
        val measurer = TextMeasurer(createFontFamilyResolver(), PdfExport.drawDensity, LayoutDirection.Ltr)
        val colW = (PdfExport.Paper.A4.w - 2 * PdfExport.MARGIN - 28) / 2
        try {
            for (l in Lingue.tutte) {
                T.lingua = l
                // Le voci delle opere stanno nella loro colonna (poco più di metà).
                val cells = listOf(T.computo.notaLucePdf, T.computo.notaSuperficiPdf, T.computo.notaOperePdf).map { it to colW } +
                    listOf(T.computo.muriDaDemolire, T.computo.vaniDaAprire, T.computo.apertureDaChiudere, T.computo.muriNuovi).map { it to colW * 0.55f }
                for ((s, cw) in cells) {
                    val w = measurer.measure(s, TextStyle(fontSize = (7.5f / PdfExport.drawDensity.density).sp), maxLines = 1).size.width
                    // Con un poco di margine: i caratteri del telefono o di Windows possono essere un poco più larghi.
                    assertTrue(w <= (cw - 6) * 0.95f, "${l.codice}: «$s» largo $w su ${cw - 6}")
                }
            }
        } finally {
            T.lingua = Italiano
        }
    }
}
