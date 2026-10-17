package com.sagoma.planimetria.scan

/**
 * Perché il tracciamento della fotocamera si è perso, come CODICE (le parole le sceglie chi lo mostra). Corrisponde a
 * `TrackingFailureReason` di ARCore, senza dipendere da ARCore: così si prova sul computer.
 */
enum class TrackingIssue {
    LOW_LIGHT,
    EXCESSIVE_MOTION,
    INSUFFICIENT_FEATURES,
    CAMERA_UNAVAILABLE;

    companion object {
        /** Dal nome del motivo di ARCore (`TrackingFailureReason.name`); null se non c'è un motivo da mostrare. */
        fun fromArCore(reasonName: String): TrackingIssue? = when (reasonName) {
            "INSUFFICIENT_LIGHT" -> LOW_LIGHT
            "EXCESSIVE_MOTION" -> EXCESSIVE_MOTION
            "INSUFFICIENT_FEATURES" -> INSUFFICIENT_FEATURES
            "CAMERA_UNAVAILABLE" -> CAMERA_UNAVAILABLE
            else -> null
        }
    }
}
