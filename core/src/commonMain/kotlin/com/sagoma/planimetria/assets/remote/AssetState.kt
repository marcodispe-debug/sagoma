package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability

/**
 * Stato dettagliato di un asset in un negozio remoto, per l'interfaccia (barre di avanzamento, "riprova"…).
 * [com.sagoma.planimetria.assets.AssetAvailability] resta il riassunto leggero: [availability] ne è la proiezione.
 */
sealed class AssetState {
    /** Nel catalogo, non ancora sul dispositivo. */
    data object Remote : AssetState()

    /** In scaricamento; [progress] da 0.0 a 1.0 (0 se la dimensione non si sa). */
    data class Downloading(val progress: Float) : AssetState() {
        init {
            require(progress in 0f..1f) { "progress fuori da 0..1: $progress" }
        }
    }

    /** Sul dispositivo e leggibile. */
    data object Available : AssetState()

    /** L'ultimo tentativo è fallito. */
    data class Failed(val error: RemoteError) : AssetState()

    /** Questa versione dell'app non lo sa usare. */
    data object Incompatible : AssetState()
}

/** Il riassunto leggero di uno stato: solo [AssetState.Available] si legge adesso; [AssetState.Failed] e [AssetState.Downloading] si possono (ri)provare. */
fun AssetState.availability(): AssetAvailability = when (this) {
    AssetState.Available -> AssetAvailability.Available
    AssetState.Incompatible -> AssetAvailability.Incompatible
    AssetState.Remote, is AssetState.Downloading, is AssetState.Failed -> AssetAvailability.Remote
}
