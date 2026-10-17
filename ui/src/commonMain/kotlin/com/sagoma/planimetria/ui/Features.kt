package com.sagoma.planimetria.ui

/**
 * Funzioni che possono essere disponibili o no. Il prodotto finale è UN'app sola: quali funzioni ci sono dipende dal piano o dai
 * diritti dell'utente, non dalla lingua né da app separate. Le schermate chiedono "questa funzione c'è?" con una chiave, mai
 * "è la versione Pro?": così, quando arriveranno piani e diritti, cambia solo [FeatureAccess].
 */
enum class AppFeature {
    /** Percorso B: schizzo della stanza e scansione guidata con il telefono. */
    SKETCH_GUIDED_SCAN,
}

object FeatureAccess {
    /**
     * Oggi la regola è tecnica: la variante Pro (`Platform.isPro`, impostata dalle varianti di build free/pro) e la presenza
     * dello scanner (solo Android). È il meccanismo ATTUALE, non il modello del prodotto: domani qui si leggeranno piano e diritti.
     */
    fun isEnabled(platform: Platform, feature: AppFeature): Boolean = when (feature) {
        AppFeature.SKETCH_GUIDED_SCAN -> platform.isPro && platform.roomScanner != null
    }
}
