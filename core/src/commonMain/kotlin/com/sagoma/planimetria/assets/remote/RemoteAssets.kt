package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collega i tre pezzi del sistema remoto: [manifests] (che manifest vale), [store] (dove si scaricano i file) e
 * [prefetcher] (in che ordine). È l'UNICO punto da cui si applicano gli aggiornamenti del manifest: chi vuole un manifest
 * nuovo chiama [refreshManifest] (o [install]) e non `ManifestRepository.refresh/replace` da solo, altrimenti il negozio
 * continuerebbe a usare quello vecchio.
 *
 * Non rifà niente di ciò che sa fare il negozio (un solo download per blob, tentativi, pausa dopo un errore, scarto di un
 * download non più atteso): qui si passa solo il resolver nuovo. Le operazioni già partite restano legate al loro blob; il
 * nuovo manifest vale per le successive.
 */
class RemoteAssets(
    val manifests: ManifestRepository,
    val store: RemoteAssetStore,
    val prefetcher: AssetPrefetcher,
) {
    private val applyLock = Mutex()

    /** Solo per i test: quante volte un resolver nuovo è stato davvero applicato allo store. */
    @kotlin.concurrent.Volatile
    internal var appliedCount = 0

    @kotlin.concurrent.Volatile
    private var closed = false

    /** Chi ha costruito il sistema può farsi avvisare della chiusura (la factory JVM ci libera la cartella che occupa). */
    @kotlin.concurrent.Volatile
    internal var onClosed: (() -> Unit)? = null

    /**
     * Chiede un manifest più recente (vedi [ManifestRepository.refresh]); se è `Updated` o `Unchanged` allinea lo store allo
     * snapshot corrente. Dopo [close] termina subito con `Failed(Cancelled)`: niente richiesta, niente salvataggio, niente applicazione.
     */
    suspend fun refreshManifest(): ManifestRefreshResult {
        if (closed) return ManifestRefreshResult.Failed(RemoteError.Cancelled)
        return applyIfUpdated(manifests.refresh())
    }

    /** Come [refreshManifest] ma con un testo già in mano (vedi [ManifestRepository.replace]); dopo [close] non fa niente (`Failed(Cancelled)`). */
    suspend fun install(text: String): ManifestRefreshResult {
        if (closed) return ManifestRefreshResult.Failed(RemoteError.Cancelled)
        return applyIfUpdated(manifests.replace(text))
    }

    /**
     * Allinea lo store allo snapshot corrente del repository: serve all'avvio (lo store nasce con il resolver che c'era, o
     * con [BlobResolver.EMPTY]) e dopo qualunque cambio avvenuto altrove. Non fa niente se lo store ha già quello giusto.
     * Usa sempre lo snapshot più recente del repository, quindi due aggiornamenti applicati fuori ordine convergono comunque.
     */
    suspend fun applyCurrent() {
        applyLock.withLock {
            if (closed) return // dopo close lo store non si aggiorna più
            val current = manifests.current ?: return
            if (current.resolver === store.snapshot) return
            store.replaceSnapshot(current.resolver)
            appliedCount++
            prefetcher.onManifestChanged()
        }
    }

    /** Chiude prefetcher e store; da qui in poi gli aggiornamenti del manifest non fanno più niente. Si può ripetere. */
    fun close() {
        closed = true
        prefetcher.close()
        store.close()
        val hook = onClosed
        onClosed = null
        hook?.invoke()
    }

    private suspend fun applyIfUpdated(r: ManifestRefreshResult): ManifestRefreshResult {
        // Dopo un'operazione riuscita (Updated o Unchanged) lo store è allineato al repository: un Unchanged può arrivare
        // mentre lo store è ancora su un resolver precedente (repository cambiato altrove, applicazione non completata); se è
        // già allineato `applyCurrent` non fa niente. Con Failed non si tocca niente: il manifest attivo e lo store restano.
        if (r is ManifestRefreshResult.Updated || r is ManifestRefreshResult.Unchanged) applyCurrent()
        return r
    }

    companion object {
        /**
         * Costruisce il sistema con lo snapshot che il repository ha adesso (o nessuno: [BlobResolver.EMPTY], tutto non
         * disponibile finché non arriva un manifest valido).
         */
        fun create(
            manifests: ManifestRepository,
            cache: AssetCache,
            fetcher: BlobFetcher,
            policy: FetchPolicy = FetchPolicy(),
            capabilities: Set<String> = emptySet(),
            maxParallel: Int = 3,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ): RemoteAssets {
            val store = RemoteAssetStore(manifests.resolver ?: BlobResolver.EMPTY, cache, fetcher, policy, capabilities, scope)
            return RemoteAssets(manifests, store, AssetPrefetcher(store, scope, maxParallel))
        }
    }
}
