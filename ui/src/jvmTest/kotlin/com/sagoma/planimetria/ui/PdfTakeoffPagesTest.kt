package com.sagoma.planimetria.ui

import com.sagoma.planimetria.geometry.Takeoff
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
}
