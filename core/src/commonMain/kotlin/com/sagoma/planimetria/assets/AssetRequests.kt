package com.sagoma.planimetria.assets

import com.sagoma.planimetria.assets.remote.AssetPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ciò che l'interfaccia vede del sistema che porta gli asset sul dispositivo: chiede un file e sa quando qualcosa è cambiato,
 * senza conoscere negozi, cache, manifest o rete. Chi decide cosa serve (`AssetPlanner`, o la UI per ciò che sta sullo
 * schermo) chiama [request]; chi lo recupera sta dietro; chi lo usa legge poi dallo [AssetStore] con `peek`.
 */
interface AssetRequests {
    /** Chiede che `path` diventi disponibile in locale. Non aspetta, non fallisce mai e non fa niente se non c'è da scaricare. */
    fun request(path: String, priority: AssetPriority)

    /**
     * Cambia (cresce) ogni volta che può essere utile ricontrollare lo store: è arrivato un asset, o il sistema è diventato pronto
     * o ha un manifest nuovo. Chi osserva ricontrolla e, per ciò che gli manca, richiede di nuovo (ripetere una richiesta è sicuro).
     */
    val version: StateFlow<Long>
}

/** Nessun sistema remoto (free, desktop, browser, remoto spento): le richieste non fanno niente e la versione non cambia mai. */
object NoAssetRequests : AssetRequests {
    override fun request(path: String, priority: AssetPriority) {}
    override val version: StateFlow<Long> = MutableStateFlow(0L)
}
