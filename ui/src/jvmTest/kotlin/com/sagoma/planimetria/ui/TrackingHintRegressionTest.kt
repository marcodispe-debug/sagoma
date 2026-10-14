package com.sagoma.planimetria.ui

import com.sagoma.planimetria.scan.TrackingIssue
import com.sagoma.planimetria.ui.sketch.SketchScanTextIt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regressione: il motivo della perdita di tracciamento ora è un codice, ma la scansione esistente deve mostrare ESATTAMENTE le
 * frasi di prima (quelle che `ArScanView.hint()` restituiva per ogni `TrackingFailureReason` di ARCore).
 */
class TrackingHintRegressionTest {
    /** Le frasi di prima, copiate alla lettera da `ArScanView.hint()` (versione df303a7), per nome del motivo di ARCore. */
    private val before = mapOf(
        "INSUFFICIENT_LIGHT" to "C'è poca luce: accendi le luci della stanza.",
        "EXCESSIVE_MOTION" to "Muovi il telefono più lentamente.",
        "INSUFFICIENT_FEATURES" to "Inquadra superfici con più dettagli (non solo pareti bianche).",
        "CAMERA_UNAVAILABLE" to "Fotocamera non disponibile.",
        "BAD_STATE" to null,
        "NONE" to null,
    )

    @Test
    fun `la scansione esistente mostra le stesse frasi di prima per ogni motivo di ARCore`() {
        for ((reason, text) in before) assertEquals(text, LegacyScanText.trackingHint(TrackingIssue.fromArCore(reason)), reason)
    }

    @Test
    fun `ogni codice ha una frase anche nel confine del percorso B`() {
        for (i in TrackingIssue.entries) assertTrue(SketchScanTextIt().trackingIssue(i).isNotBlank(), "$i")
        assertNull(TrackingIssue.fromArCore("QUALSIASI_ALTRO"))
    }
}
