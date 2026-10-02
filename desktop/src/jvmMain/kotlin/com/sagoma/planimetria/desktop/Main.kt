package com.sagoma.planimetria.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sagoma.planimetria.Edition
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.FurnitureAssets
import com.sagoma.planimetria.ui.SagomaApp
import kotlinx.coroutines.delay
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/** Cartella dei dati: %APPDATA%\Sagoma su Windows, ~/.sagoma altrove. */
private fun dataDir(): File {
    val appData = System.getenv("APPDATA")
    return (if (appData != null) File(appData, "Sagoma") else File(System.getProperty("user.home"), ".sagoma")).also { it.mkdirs() }
}

/**
 * Errori del programma in `errori.log` nella cartella dei dati: Sagoma parte senza console, e altrimenti
 * un errore non lascerebbe traccia (la finestra sparirebbe e basta).
 */
private fun logError(where: String, e: Throwable) {
    runCatching {
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        File(dataDir(), "errori.log").appendText("---- ${LocalDateTime.now()} · $where\n$trace\n")
    }
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e -> logError("thread ${t.name}", e) }
    app()
    // Finestra chiusa (progetto già salvato alla chiusura): il programma finisce davvero. Prima poteva
    // restare aperto, invisibile, in memoria.
    exitProcess(0)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun app() = application(exitProcessOnExit = false) {
    val dir = dataDir()
    val assets = System.getProperty("sagoma.assets")?.let(::File)?.takeIf { it.isDirectory }
    val platform = DesktopPlatform(isPro = true, projects = FileProjectRepository(dir), tips = FileTipStore(dir), assets = assets)
    Edition.isPro = platform.isPro
    FurnitureAssets.init(platform)
    // Errore nella finestra: lo si scrive nel registro, lo si dice all'utente e si chiude tutto (invece di
    // lasciare il programma vivo senza finestra).
    val onWindowError = WindowExceptionHandlerFactory { window ->
        WindowExceptionHandler { e ->
            logError("finestra", e)
            JOptionPane.showMessageDialog(
                window,
                "Sagoma ha avuto un errore e deve chiudersi.\nIl progetto è salvato fino all'ultima modifica.\n" +
                    "Dettagli in: ${File(dataDir(), "errori.log").absolutePath}\n\n$e",
                "Sagoma", JOptionPane.ERROR_MESSAGE,
            )
            exitProcess(1)
        }
    }
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides onWindowError) {
        Window(onCloseRequest = ::exitApplication, title = "Sagoma", state = rememberWindowState(size = DpSize(1400.dp, 900.dp))) {
            platform.frame = window
            Box(Modifier.fillMaxSize()) {
                SagomaApp(platform)
                platform.message?.let { msg ->
                    LaunchedEffect(msg) { delay(2500); platform.message = null }
                    Snackbar(Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text(msg) }
                }
            }
        }
    }
}
