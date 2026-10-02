package com.sagoma.planimetria.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * L'app intera, uguale su tutte le piattaforme: all'avvio si riapre l'ultimo progetto; dal menu ⋮ →
 * Progetti (o con "indietro") si torna all'elenco.
 */
@Composable
fun SagomaApp(platform: Platform) {
    CompositionLocalProvider(LocalPlatform provides platform) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2F5D62))) {
            val projects = platform.projects
            var open by rememberSaveable { mutableStateOf(projects.lastOpened ?: -1L) }
            Surface {
                if (open < 0) {
                    ProjectsScreen { id -> projects.markOpened(id); open = id }
                } else {
                    platform.BackHandler(enabled = true) { open = -1L }
                    key(open) {
                        EditorScreen(open, onOpenProjects = { open = -1L })
                    }
                }
            }
        }
    }
}
