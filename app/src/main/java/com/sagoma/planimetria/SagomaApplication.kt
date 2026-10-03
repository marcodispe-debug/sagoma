package com.sagoma.planimetria

import android.app.Application
import android.util.Log
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.LateBoundAssetStore
import com.sagoma.planimetria.assets.remote.RemoteAssets
import com.sagoma.planimetria.assets.remote.createJvmRemoteAssets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File

/**
 * Possiede ciò che deve vivere quanto il processo e non una schermata. Una `Activity` si ricrea a ogni rotazione o cambio di
 * configurazione (l'app non dichiara `configChanges`), mentre il sistema remoto ha una cache e un manifest salvato su disco che
 * non possono avere due istanze insieme: per questo sta qui e le `Activity` lo leggono soltanto, senza mai crearlo né chiuderlo.
 *
 * Il sistema remoto non si chiude mai: a fine processo Android termina tutto senza avvisare (`onTerminate` non viene chiamato sui
 * dispositivi), e la cache scrive in modo atomico, quindi un'interruzione non lascia file a metà.
 */
class SagomaApplication : Application() {
    /**
     * Il sistema remoto del processo, uguale per ogni `Activity`: si ottiene con `await()` da una coroutine (per esempio
     * `lifecycleScope`), mai bloccando il thread principale, perché aprirlo legge dal disco (verifica della cache, manifest salvato).
     * Il possesso resta qui, il lavoro bloccante gira fuori dal thread principale: parte in `onCreate` su `Dispatchers.IO` e il
     * risultato si ottiene una volta sola, anche se le `Activity` si ricreano mentre è in corso.
     *
     * Vale `null` nella versione free e finché non sono configurati gli indirizzi (vedi `res/values/remote_assets.xml`: vuoti =
     * nessun remoto, nessuna cache, nessun thread: il risultato è già pronto e non parte alcun lavoro), e se l'apertura fallisce.
     */
    lateinit var remoteAssets: Deferred<RemoteAssets?>
        private set

    /**
     * Il negozio remoto da mettere dietro agli asset impacchettati, da consegnare subito a una `Platform` (nessuna attesa, nessun
     * `await`): vuoto finché `remoteAssets` non è pronto, poi risponde con lo store di quello. È collegato una sola volta, dallo
     * stesso lavoro che apre il sistema remoto, quindi vale per ogni `Platform` e `Activity`, anche se si ricreano nel frattempo.
     * `null` dove il remoto è spento (free o indirizzi vuoti): allora non c'è niente da comporre.
     */
    var remoteStore: AssetStore? = null
        private set

    override fun onCreate() {
        super.onCreate()
        remoteAssets = openRemoteAssets()
    }

    private fun openRemoteAssets(): Deferred<RemoteAssets?> {
        // Solo la lettura di due stringhe sul thread principale: se il remoto è spento non si avvia nulla.
        if (!Flavor.isPro) return remoteDisabled()
        val manifestUrl = getString(R.string.remote_manifest_url).trim()
        val blobBaseUrl = getString(R.string.remote_blob_base_url).trim()
        if (manifestUrl.isEmpty() || blobBaseUrl.isEmpty()) return remoteDisabled()
        val late = LateBoundAssetStore()
        remoteStore = late
        return CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
            try {
                // Stessa cartella di base della cache degli asset impacchettati: le cartelle remote non vengono mai toccate dalla sua pulizia.
                createJvmRemoteAssets(File(noBackupFilesDir, "asset-cache"), manifestUrl, blobBaseUrl, REMOTE_CACHE_BYTES)
                    ?.also { late.bind(it.store) } // una sola volta: questo è l'unico punto che lo fa
            } catch (e: Exception) {
                // Il remoto è un di più: se non si apre l'app funziona con gli asset impacchettati.
                Log.w(TAG, "Sistema remoto non disponibile", e)
                null
            }
        }
    }

    // Non `CompletableDeferred(null)`: `null` sceglierebbe il costruttore con il `Job` padre e darebbe un risultato mai completato.
    private fun remoteDisabled(): Deferred<RemoteAssets?> = CompletableDeferred<RemoteAssets?>().also { it.complete(null) }

    private companion object {
        const val TAG = "SagomaApplication"

        /** Spazio massimo della cache dei blob remoti (valore di partenza, da tarare). */
        const val REMOTE_CACHE_BYTES = 512L * 1024 * 1024
    }
}
