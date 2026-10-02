package com.sagoma.planimetria.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Nelle prove più schermate vivono nello stesso programma: ognuna ha i suoi ViewModel, altrimenti il
 * "progetto 1" di una prova riuserebbe l'editor del "progetto 1" della prova precedente.
 */
@Composable
internal fun freshViewModels(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
}
