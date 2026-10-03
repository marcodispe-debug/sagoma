package com.sagoma.planimetria.assets

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Un negozio che all'inizio è vuoto (come [EmptyAssetStore]) e che, quando quello vero è pronto, si collega a esso con
 * [bind]. Serve a chi deve consegnare subito un [AssetStore] (per esempio una `Platform`) mentre il negozio vero si apre
 * in background: nessuna operazione aspetta il collegamento, prima di [bind] si risponde "non disponibile" e dopo si
 * passa tutto al negozio collegato. Si collega una volta sola.
 */
@OptIn(ExperimentalAtomicApi::class)
class LateBoundAssetStore : AssetStore {
    private val target = AtomicReference<AssetStore?>(null)

    /**
     * Collega il negozio vero. Si può fare una sola volta (da qualunque thread: se due lo fanno insieme riesce uno solo);
     * un secondo collegamento lancia [IllegalStateException] e non cambia niente.
     */
    fun bind(store: AssetStore) {
        require(store !== this) { "Un negozio non si può collegare a se stesso" }
        check(target.compareAndSet(null, store)) { "Negozio già collegato" }
    }

    private fun current(): AssetStore = target.load() ?: EmptyAssetStore

    override fun availability(path: String): AssetAvailability = current().availability(path)
    override fun peek(path: String): ByteArray? = current().peek(path)
    override suspend fun ensure(path: String): AssetAvailability = current().ensure(path)
    override suspend fun read(path: String): ByteArray? = current().read(path)
}
