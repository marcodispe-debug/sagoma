package com.sagoma.planimetria.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.persistence.ThemeChoice
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.T

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
    var language by remember { mutableStateOf(platform.language.load()) }
    // I testi si leggono da T mentre si disegna: cambiando lingua tutto si ridisegna da capo (key più sotto).
    val lingua = Lingue.scegli(language, platform.systemLanguage)
    T.lingua = lingua
    val languageSetting = LanguageSetting(language) { c -> language = c; platform.language.save(c) }
    CompositionLocalProvider(LocalPlatform provides platform, LocalThemeSetting provides themeSetting, LocalLanguageSetting provides languageSetting) {
        MaterialTheme(colorScheme = if (dark) SagomaDarkColors else SagomaLightColors) {
            val projects = platform.projects
            val courseProjects = remember { CourseProjects(platform) }
            // Primo avvio sul computer: la guida interattiva, sul suo progetto di prova (i progetti dell'utente non si toccano).
            val firstCourse = remember { platform.interactiveGuide && TipStore.COURSE_DONE !in platform.tips.seen() }
            var courseLesson by remember { mutableStateOf(if (firstCourse) Lesson.Square else null) }
            var open by rememberSaveable { mutableStateOf(if (firstCourse) courseProjects.project(Lesson.Square) else projects.lastOpened ?: -1L) }
            key(lingua.codice) {
                Surface {
                    if (open < 0) {
                        // Tornati all'elenco, il ViewModel del progetto chiuso (già salvato) si butta: riaprendolo, o aprendo un progetto
                        // nuovo che riprende il numero di uno eliminato, si riparte da quello che c'è su disco e non dalla casa di prima.
                        val vmOwner = LocalViewModelStoreOwner.current
                        LaunchedEffect(Unit) { vmOwner?.viewModelStore?.clear() }
                        ProjectsScreen { id -> projects.markOpened(id); open = id }
                    } else if (!FurnitureAssets.ready) {
                        // Un attimo, all'avvio: i cataloghi (arredi, materiali) si stanno ancora leggendo e il progetto li usa.
                        Box(Modifier.fillMaxSize())
                    } else {
                        platform.BackHandler(enabled = true) { open = -1L }
                        key(open) {
                            // Il progetto aperto: le sue immagini (piantina, foto delle stanze) stanno tra i suoi file.
                            androidx.compose.runtime.CompositionLocalProvider(LocalProjectId provides open, LocalGuide provides remember { GuideTargets() }) {
                                EditorScreen(
                                    open,
                                    onOpenProjects = { open = -1L },
                                    courseLesson = courseLesson,
                                    onCourseStarted = { courseLesson = null },
                                    onCourse = { lesson ->
                                        courseLesson = lesson
                                        open = courseProjects.project(lesson)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * I due progetti di prova della guida interattiva: uno per le lezioni sulle stanze, uno per quella sopra la planimetria (che
 * parte da un progetto vuoto). Creati la prima volta; il loro numero e la data di creazione stanno con le guide già viste.
 */
internal class CourseProjects(private val platform: Platform) {
    fun project(lesson: Lesson): Long {
        val tips = platform.tips
        val projects = platform.projects
        val key = if (lesson == Lesson.OverPlan) "guida-progetto-2=" else "guida-progetto-1="
        val saved = tips.seen().firstOrNull { it.startsWith(key) }?.removePrefix(key)?.split(':')
        val id = saved?.getOrNull(0)?.toLongOrNull()
        val created = saved?.getOrNull(1)?.toLongOrNull()
        // Solo se è proprio quello: un progetto nuovo può riprendere il numero di uno eliminato, e la guida lo svuoterebbe.
        if (id != null) projects.info(id)?.takeIf { it.created == created }?.let { return it.id }
        val p = projects.create(if (lesson == Lesson.OverPlan) T.corso.progettoProvaPlanimetria else T.corso.progettoProva)
        tips.save(tips.seen().filterNot { it.startsWith(key) }.toSet() + "$key${p.id}:${p.created}")
        return p.id
    }
}
