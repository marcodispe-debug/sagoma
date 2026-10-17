package com.sagoma.planimetria.ui

import com.sagoma.planimetria.scan.TrackingIssue

/**
 * Testi della scansione ESISTENTE ("Scansiona con il telefono"), identici a quelli che `ArScanView` scriveva prima che il motivo
 * della perdita di tracciamento diventasse un codice ([TrackingIssue]). Restano qui, in italiano come il resto di quella
 * schermata, per non cambiare quello che l'utente vede.
 */
internal object LegacyScanText {
    fun trackingHint(issue: TrackingIssue?): String? = when (issue) {
        TrackingIssue.LOW_LIGHT -> "C'è poca luce: accendi le luci della stanza."
        TrackingIssue.EXCESSIVE_MOTION -> "Muovi il telefono più lentamente."
        TrackingIssue.INSUFFICIENT_FEATURES -> "Inquadra superfici con più dettagli (non solo pareti bianche)."
        TrackingIssue.CAMERA_UNAVAILABLE -> "Fotocamera non disponibile."
        null -> null
    }
}
