package com.sagoma.planimetria.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.assets.AssetRequests
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.NoAssetRequests
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.persistence.ProjectRepository

/** File scelto dall'utente: nome e contenuto. */
class PickedFile(val name: String, val bytes: ByteArray)

/** Immagine pronta da salvare nel progetto (JPEG ridotto) con le sue misure in pixel. */
class ImportedImage(val jpeg: ByteArray, val width: Int, val height: Int)

/** Pagina di un PDF da creare: misure in punti tipografici (1/72 di pollice). */
class PdfPage(val width: Int, val height: Int, val draw: DrawScope.() -> Unit)

/**
 * Servizi che ogni piattaforma (Android, computer, browser) realizza a modo suo: tutto il resto
 * dell'interfaccia è in comune. Si legge da [LocalPlatform].
 */
interface Platform {
    /** Versione pro: colori e rivestimenti liberi, arredi 3D, motore grafico avanzato. */
    val isPro: Boolean

    /** Archivio dei progetti. */
    val projects: ProjectRepository

    /** Suggerimenti già visti e tutorial completato. */
    val tips: TipStore

    /** Barra di stato nascosta (schermo basso, in orizzontale); non fa nulla dove non c'è. */
    fun setStatusBarHidden(hidden: Boolean) {}

    /** Messaggio breve in basso. */
    fun toast(message: String)

    /** Chiede dove salvare e scrive il file; `onDone(true)` se è andato a buon fine. */
    fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit = {})

    /** Chiede un file da aprire (tipi MIME accettati; [ANY_FILE] per tutti); null se l'utente annulla. */
    fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit)

    /** Condivide un testo con le altre app (mail, messaggi…). */
    fun shareText(subject: String, text: String)

    /**
     * Da dove arrivano i file di arredi, materiali e luci (catalogo, miniature, modelli 3D, texture):
     * nell'app, in una cartella o, più avanti, scaricati. Dove non ci sono è vuoto (EmptyAssetStore).
     */
    val assetStore: AssetStore

    /**
     * Come chiedere che un file diventi disponibile (e saperne l'arrivo) quando gli asset vengono da un sistema remoto; dove non
     * c'è non fa niente.
     */
    val assetRequests: AssetRequests get() = NoAssetRequests

    /** Immagine (PNG, JPEG) da mostrare. */
    fun decodeImage(bytes: ByteArray): ImageBitmap?

    /** Immagine in PNG, per esportare la pianta. */
    fun encodePng(image: ImageBitmap): ByteArray?

    /**
     * Foto o prima pagina di un PDF da usare come pianta di sfondo: girata come è stata scattata e ridotta a
     * `maxSide` pixel sul lato lungo. Null se non si riesce a leggere (o i PDF non sono supportati).
     */
    suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage?

    /** PDF vettoriale con le pagine date (disegnate alla densità `density`); null se non supportato. */
    fun createPdf(pages: List<PdfPage>, density: Density): ByteArray?

    /** Data (e ora) nel formato della lingua del sistema. */
    fun formatDate(epochMillis: Long, withTime: Boolean = false): String

    /** Ora attuale in millisecondi. */
    fun now(): Long

    /** Scansione delle stanze con la fotocamera; null dove non esiste. */
    val roomScanner: RoomScanner? get() = null

    /** Chi disegna la vista 3D. */
    fun createSceneRenderer(): SceneRenderer

    /**
     * Zona in cui un trascinamento non deve diventare il gesto "indietro" del sistema (solo Android).
     */
    fun gestureExclusion(modifier: Modifier): Modifier = modifier

    /** Tasto o gesto "indietro" del sistema (dove esiste). */
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit) {}
}

/**
 * Scansione di una stanza con la fotocamera (realtà aumentata): c'è solo dove la piattaforma la sa fare (Android); altrove
 * [Platform.roomScanner] è null. Il risultato è in centimetri, nel sistema di Sagoma, senza tipi di ARCore.
 */
interface RoomScanner {
    /** Schermata della scansione; `onResult(null)` se l'utente annulla o la scansione non è possibile. */
    @Composable
    fun Screen(onResult: (com.sagoma.planimetria.scan.ScanResult?) -> Unit)
}

/** Tipo MIME che accetta qualsiasi file. */
const val ANY_FILE = "*" + "/" + "*"

val LocalPlatform =staticCompositionLocalOf<Platform> { error("Platform non impostata") }
