package com.sagoma.planimetria.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Nelle prove più schermate vivono nello stesso programma: ognuna ha i suoi ViewModel, altrimenti il
 * "progetto 1" di una prova riuserebbe l'editor del "progetto 1" della prova precedente.
 *
 * Ognuna ha anche il suo ciclo di vita, sempre "attivo": senza, `ImageComposeScene` usa un ciclo di vita unico per
 * tutto il programma e non protetto tra thread. Le prove compongono sul loro thread, mentre `collectAsStateWithLifecycle`
 * si registra dal thread di Swing: a volte la lista interna si rompe (IndexOutOfBoundsException: Index -1 in
 * `LifecycleRegistry.calculateTargetState`) e la prova fallisce per caso, ora l'una ora l'altra.
 */
@Composable
internal fun freshViewModels(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner, LifecycleOwner {
            override val viewModelStore = ViewModelStore()
            override val lifecycle: Lifecycle = ResumedLifecycle(this)
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalLifecycleOwner provides owner) { content() }
}

/** Ciclo di vita fermo su RESUMED, che si può usare da più thread: chi si registra riceve subito create, start e resume. */
private class ResumedLifecycle(private val owner: LifecycleOwner) : Lifecycle() {
    private val observers = mutableSetOf<LifecycleObserver>()

    override val currentState: State get() = State.RESUMED

    override fun addObserver(observer: LifecycleObserver) {
        synchronized(observers) { if (!observers.add(observer)) return }
        when (observer) {
            is DefaultLifecycleObserver -> { observer.onCreate(owner); observer.onStart(owner); observer.onResume(owner) }
            is LifecycleEventObserver -> for (e in listOf(Event.ON_CREATE, Event.ON_START, Event.ON_RESUME)) observer.onStateChanged(owner, e)
        }
    }

    override fun removeObserver(observer: LifecycleObserver) {
        synchronized(observers) { observers.remove(observer) }
    }
}
