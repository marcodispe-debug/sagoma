package com.sagoma.planimetria.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.access.Account
import com.sagoma.planimetria.assets.AssetRequests
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.MaterialResolution
import com.sagoma.planimetria.assets.NoAssetRequests
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.persistence.LanguageStore
import com.sagoma.planimetria.persistence.NoLanguageStore
import com.sagoma.planimetria.persistence.NoThemeStore
import com.sagoma.planimetria.persistence.ProjectRepository
import com.sagoma.planimetria.persistence.ThemeStore

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
    /**
     * Chi usa l'app e cosa ha acquistato. Cosa è sbloccato lo decide [com.sagoma.planimetria.access.Rights], mai la piattaforma:
     * qui si dice solo da dove si legge l'account (oggi locale, senza registrazione).
     */
    val account: Account get() = Account.Guest

    /** Archivio dei progetti. */
    val projects: ProjectRepository

    /** Suggerimenti già visti e tutorial completato. */
    val tips: TipStore

    /** Tema scelto (chiaro, scuro, come il sistema); dove non si salva l'app resta chiara. */
    val theme: ThemeStore get() = NoThemeStore

    /** Lingua scelta (italiano, inglese, come il sistema); dove non si salva l'app resta in italiano. */
    val language: LanguageStore get() = NoLanguageStore

    /** Codice della lingua del sistema ("it", "en"…), per la scelta "come il sistema". */
    val systemLanguage: String get() = "it"

    /** Adatta le barre di sistema al tema dell'app (icone chiare sul tema scuro); non fa nulla dove non ci sono. */
    fun setDarkTheme(dark: Boolean) {}

    /** Barra di stato nascosta (schermo basso, in orizzontale); non fa nulla dove non c'è. */
    fun setStatusBarHidden(hidden: Boolean) {}

    /** Guida interattiva (le lezioni sul progetto di prova): pensata per mouse e schermo largo, c'è solo sul computer. */
    val interactiveGuide: Boolean get() = false

    /** Joystick sullo schermo per muoversi nel 3D: serve dove non ci sono tastiera e mouse; sul computer non c'è (WASD, frecce, mouse). */
    val walkJoystick: Boolean get() = true

    /** Definizione dei materiali nel 3D: 1K sul telefono (quelli dell'app), 2K sul computer (dal server, finché mancano l'1K). */
    val materialResolution: MaterialResolution get() = MaterialResolution.K1

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

    /**
     * Foto di una stanza da tenere nel progetto: come [importImage], con la qualità JPEG `quality` (0-100) per farla pesare poco.
     * Dove la piattaforma non la sa scegliere si usa la qualità delle piante di sfondo.
     */
    suspend fun importPhoto(bytes: ByteArray, maxSide: Int, quality: Int): ImportedImage? = importImage(bytes, false, maxSide)

    /** Si può scattare una foto con la fotocamera ([takePhoto])? Solo dove c'è (Android). */
    val canTakePhoto: Boolean get() = false

    /** Apre la fotocamera del sistema e restituisce la foto scattata; null se l'utente annulla o non si riesce. */
    fun takePhoto(onResult: (PickedFile?) -> Unit) = onResult(null)

    /** Un PDF si può usare come pianta di sfondo ([importImage] con `pdf`)? Dove no, si offrono solo le immagini. */
    val pdfAsBackground: Boolean get() = true

    /** PDF vettoriale con le pagine date (disegnate alla densità `density`); null se non supportato. */
    fun createPdf(pages: List<PdfPage>, density: Density): ByteArray?

    /** Data (e ora) nel formato della lingua del sistema. */
    fun formatDate(epochMillis: Long, withTime: Boolean = false): String

    /** Ora attuale in millisecondi. */
    fun now(): Long

    /** Chi legge la piantina al posto dell'utente ("Leggi la piantina da sola"); null dove non è configurato (nessun server). */
    val planReader: com.sagoma.planimetria.reading.PlanReader? get() = null

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

    /**
     * Percorso B: scansione guidata dallo schizzo `room` (la stanza stessa). `onResult(null)` se l'utente esce; altrimenti gli
     * angoli misurati (mondo AR, filo interno, ordine dei muri) e le posizioni delle aperture corrette nella revisione.
     * Di default non c'è: solo la piattaforma che la sa fare la implementa.
     */
    @Composable
    fun SketchScreen(room: com.sagoma.planimetria.model.Room, onResult: (SketchScanOutcome?) -> Unit) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onResult(null) }
    }

    /** PROTOTIPO: scansione ibrida (pavimento, soffitto, pareti dalla profondità) sulla stessa stanza. Di default non c'è. */
    @Composable
    fun HybridScreen(room: com.sagoma.planimetria.model.Room, onResult: (SketchScanOutcome?) -> Unit) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onResult(null) }
    }

    /** C'è il prototipo della scansione ibrida? */
    val hasHybridPrototype: Boolean get() = false
}

/** Esito della scansione guidata dallo schizzo, da applicare alla stessa stanza con `EditorViewModel.applySketchScan`. */
data class SketchScanOutcome(
    val cornersAr: List<com.sagoma.planimetria.scan.ArXZ>,
    val positionOverrides: Map<Long, Double>,
    val widthOverrides: Map<Long, Double> = emptyMap(),
)

/** Tipo MIME che accetta qualsiasi file. */
const val ANY_FILE = "*" + "/" + "*"

val LocalPlatform =staticCompositionLocalOf<Platform> { error("Platform non impostata") }
