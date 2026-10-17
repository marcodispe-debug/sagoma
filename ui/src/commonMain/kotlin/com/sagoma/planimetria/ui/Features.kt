package com.sagoma.planimetria.ui

import com.sagoma.planimetria.access.Right
import com.sagoma.planimetria.access.Rights
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog

/**
 * Funzioni che possono essere disponibili o no. L'app è una sola: le schermate chiedono "questa funzione c'è?" con una
 * chiave, mai "che utente è?". Qui si mettono insieme due cose: se la piattaforma la sa fare (scanner, catalogo presente)
 * e se l'account ne ha il diritto, che decide solo [Rights].
 */
enum class AppFeature {
    /** Percorso B: schizzo della stanza e scansione guidata con il telefono. */
    SKETCH_GUIDED_SCAN,

    /** Inserire arredi dal catalogo. */
    FURNITURE,

    /** Pavimenti con materiali fotografici. */
    PHOTO_FLOORS,
}

object FeatureAccess {
    fun isEnabled(platform: Platform, feature: AppFeature): Boolean = when (feature) {
        // La scansione, quando c'è, è gratuita: serve solo il telefono.
        AppFeature.SKETCH_GUIDED_SCAN -> platform.roomScanner != null
        AppFeature.FURNITURE -> FurnitureCatalog.items.isNotEmpty() && Rights.has(platform.account, Right.InsertFurniture)
        AppFeature.PHOTO_FLOORS -> MaterialCatalog.items.any { it.forFloor }
    }
}
