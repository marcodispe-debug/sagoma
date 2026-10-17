package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Le richieste dell'interfaccia verso un [AssetPrefetcher] che si apre dopo, in background: si consegna subito a chi le fa e si
 * collega con [bind] quando il prefetcher esiste. Prima del collegamento le richieste non fanno niente (non si accodano): al
 * collegamento [version] cresce e chi osserva richiede di nuovo ciò che gli manca. Mai attese né blocchi.
 *
 * [version] cresce anche a ogni asset arrivato ([AssetPrefetcher.arrived]) e quando chi possiede il sistema chiama [invalidate]
 * (per esempio dopo un manifest nuovo: le richieste fatte prima non avevano niente da scaricare). Essendo uno stato e non un
 * evento, un osservatore che arriva tardi vede comunque che qualcosa è cambiato.
 */
@OptIn(ExperimentalAtomicApi::class)
class LateBoundAssetRequests : AssetRequests {
    private val claimed = AtomicInt(0)
    private val target = AtomicReference<AssetPrefetcher?>(null)
    private val versionState = MutableStateFlow(0L)

    override val version: StateFlow<Long> = versionState.asStateFlow()

    /**
     * Collega il prefetcher; `scope` è dove si ascoltano gli arrivi. Una volta sola: un secondo collegamento lancia
     * [IllegalStateException] e non cambia niente.
     */
    fun bind(prefetcher: AssetPrefetcher, scope: CoroutineScope) {
        check(claimed.compareAndSet(0, 1)) { "Richieste già collegate" }
        // Prima ci si iscrive agli arrivi, poi si accettano le richieste: nessun arrivo può sfuggire.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { prefetcher.arrived.collect { invalidate() } }
        target.store(prefetcher)
        invalidate()
    }

    /** Dice a chi osserva di ricontrollare (fa crescere [version]). */
    fun invalidate() {
        versionState.update { it + 1 }
    }

    override fun request(path: String, priority: AssetPriority) {
        target.load()?.request(path, priority)
    }
}
