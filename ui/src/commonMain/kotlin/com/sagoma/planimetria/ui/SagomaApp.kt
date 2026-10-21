package com.sagoma.planimetria.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.sagoma.planimetria.persistence.ThemeChoice

/**
 * L'app intera, uguale su tutte le piattaforme: all'avvio si riapre l'ultimo progetto; dal menu ⋮ →
 * Progetti (o con "indietro") si torna all'elenco.
 */
@Composable
fun SagomaApp(platform: Platform) {
    var theme by remember { mutableStateOf(platform.theme.load()) }
    val dark = when (theme) {
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
        ThemeChoice.System -> isSystemInDarkTheme()
    }
    LaunchedEffect(dark) { platform.setDarkTheme(dark) }
    val themeSetting = ThemeSetting(theme) { c -> theme = c; platform.theme.save(c) }
    CompositionLocalProvider(LocalPlatform provides platform, LocalThemeSetting provides themeSetting) {
        MaterialTheme(colorScheme = if (dark) SagomaDarkColors else SagomaLightColors) {
            val projects = platform.projects
            var open by rememberSaveable { mutableStateOf(projects.lastOpened ?: -1L) }
            Surface {
                if (open < 0) {
                    // Tornati all'elenco, il ViewModel del progetto chiuso (già salvato) si butta: riaprendolo, o aprendo un progetto
                    // nuovo che riprende il numero di uno eliminato, si riparte da quello che c'è su disco e non dalla casa di prima.
                    val vmOwner = LocalViewModelStoreOwner.current
                    LaunchedEffect(Unit) { vmOwner?.viewModelStore?.clear() }
                    ProjectsScreen { id -> projects.markOpened(id); open = id }
                } else {
                    platform.BackHandler(enabled = true) { open = -1L }
                    key(open) {
                        // Il progetto aperto: le sue immagini (piantina, foto delle stanze) stanno tra i suoi file.
                        androidx.compose.runtime.CompositionLocalProvider(LocalProjectId provides open) {
                            EditorScreen(open, onOpenProjects = { open = -1L })
                        }
                    }
                }
            }
        }
    }
}
