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
import com.sagoma.planimetria.assets.remote.RemoteAssets
import com.sagoma.planimetria.assets.remote.createJvmRemoteAssets
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
 * Cartella della cache degli asset: %LOCALAPPDATA%\Sagoma\asset-cache su Windows (non nel profilo "roaming",
 * perché è grande e si può rifare), ~/.sagoma/asset-cache altrove. Mai dentro la cartella degli asset.
 */
private fun assetCacheDir(): File {
    val local = System.getenv("LOCALAPPDATA")
    return File(if (local != null) File(local, "Sagoma") else File(System.getProperty("user.home"), ".sagoma"), "asset-cache")
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

/** Spazio massimo della cache dei blob remoti sul computer (valore di partenza, da tarare). */
private const val REMOTE_CACHE_BYTES = 1024L * 1024 * 1024

/**
 * Il sistema remoto degli asset, uno per processo: si crea qui, una volta, e si passa a chi lo usa. Solo se si indicano gli indirizzi
 * (`-Dsagoma.remote.manifest=` e `-Dsagoma.remote.blobs=`, come `-Dsagoma.assets`); altrimenti `null`: nessuna cache remota, nessuna rete.
 * La cartella degli asset indicata con `-Dsagoma.assets` non si tocca mai.
 */
private fun createRemoteAssets(): RemoteAssets? {
    val manifestUrl = System.getProperty("sagoma.remote.manifest")?.trim().orEmpty()
    val blobBaseUrl = System.getProperty("sagoma.remote.blobs")?.trim().orEmpty()
    if (manifestUrl.isEmpty() || blobBaseUrl.isEmpty()) return null
    val assets = System.getProperty("sagoma.assets")?.let(::File)?.takeIf { it.isDirectory }
    return createJvmRemoteAssets(assetCacheDir(), manifestUrl, blobBaseUrl, REMOTE_CACHE_BYTES, protectedDirs = listOfNotNull(assets))
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e -> logError("thread ${t.name}", e) }
    // Una sola volta per processo, chiuso quando la finestra si chiude e il programma finisce (non a ogni ricomposizione).
    val remote = createRemoteAssets()
    try {
        app(remote)
    } finally {
        remote?.close()
    }
    // Finestra chiusa (progetto già salvato alla chiusura): il programma finisce davvero. Prima poteva
    // restare aperto, invisibile, in memoria.
    exitProcess(0)
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("UNUSED_PARAMETER") // lo usa la Platform nella tranche di collegamento
private fun app(remote: RemoteAssets?) = application(exitProcessOnExit = false) {
    val dir = dataDir()
    val assets = System.getProperty("sagoma.assets")?.let(::File)?.takeIf { it.isDirectory }
    val platform = DesktopPlatform(isPro = true, projects = FileProjectRepository(dir), tips = FileTipStore(dir), assets = assets, assetCacheDir = assetCacheDir())
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
