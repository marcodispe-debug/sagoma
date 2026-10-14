package com.sagoma.planimetria.ui

import com.sagoma.planimetria.scan.sketch.Answer
import com.sagoma.planimetria.scan.sketch.GeometryIssue
import com.sagoma.planimetria.scan.sketch.IssueCode
import com.sagoma.planimetria.scan.sketch.RejectionReason
import com.sagoma.planimetria.scan.sketch.SessionRejection
import com.sagoma.planimetria.ui.sketch.SketchScanTextIt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Il confine dei testi del percorso B: ogni codice ha la sua frase; numeri, misure e plurali passano da qui. */
class SketchScanTextTest {
    private val t = SketchScanTextIt()

    @Test
    fun `ogni codice del nucleo e della sessione ha un testo`() {
        for (c in IssueCode.entries) assertTrue(t.issue(GeometryIssue(c, listOf(0, 1))).isNotBlank(), "$c")
        for (a in Answer.entries) assertTrue(t.answer(a).isNotBlank(), "$a")
        for (r in RejectionReason.entries) assertTrue(t.rejection(r).isNotBlank(), "$r")
        for (r in SessionRejection.entries) assertTrue(t.sessionRejection(r, 2).isNotBlank(), "$r")
    }

    @Test
    fun `misure e plurali dal confine, con entrambi i valori nelle discrepanze`() {
        assertEquals("2,15 m", t.meters(2.149))
        assertEquals("1 parete confermata", t.wallsConfirmed(1))
        assertEquals("3 pareti confermate", t.wallsConfirmed(3))
        // La conferma d'uscita elenca tutto quello che si perde, non conta solo le pareti.
        for (word in listOf("pareti confermate", "risposte", "misure manuali")) assertTrue(word in t.exitConfirm, word)
        val q = GeometryIssue(IssueCode.SKETCH_LENGTH_MISMATCH, listOf(1), mapOf("schizzoM" to 3.0, "misurataM" to 2.15))
        assertEquals("Lato 2: schizzo 3,00 m, scansione 2,15 m", t.issue(q))
    }
}
