package com.sagoma.planimetria.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.ClasspathAssetStore
import com.sagoma.planimetria.assets.CompositeAssetStore
import com.sagoma.planimetria.assets.DirectoryAssetStore
import com.sagoma.planimetria.assets.buildCachedAssetStore
import com.sagoma.planimetria.assets.codeSourceStamp
import com.sagoma.planimetria.assets.directoryFingerprint
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.persistence.ProjectRepository
import com.sagoma.planimetria.ui.ImportedImage
import com.sagoma.planimetria.ui.PdfPage
import com.sagoma.planimetria.ui.PickedFile
import com.sagoma.planimetria.ui.Platform
import com.sagoma.planimetria.ui.SceneRenderer
import com.sagoma.planimetria.ui.SkiaImages
import com.sagoma.planimetria.ui.UnavailableSceneRenderer
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Servizi della piattaforma sul computer: finestre di dialogo di sistema per i file, Skia per immagini e PDF. */
class DesktopPlatform(
    override val isPro: Boolean,
    override val projects: ProjectRepository,
    override val tips: TipStore,
    private val assets: File?,
    /**
     * Cartella della cache su disco degli asset; `null` = nessuna cache (come nelle prove). Deve stare fuori dalla
     * cartella degli asset (`-Dsagoma.assets`), che non si tocca mai: se ci sta dentro la cache non si usa.
     */
    assetCacheDir: File? = null,
) : Platform {
    /** Finestra principale (per le finestre di dialogo). */
    var frame: Frame? = null

    /** Messaggio breve mostrato in basso dalla finestra principale. */
    var message by mutableStateOf<String?>(null)

    override fun toast(message: String) {
        this.message = message
    }

    /** Estensione del file per i tipi MIME usati dall'app. */
    private fun extensions(mime: String): List<String> = when (mime) {
        "image/png" -> listOf("png")
        "image/*" -> listOf("png", "jpg", "jpeg", "webp", "bmp")
        "application/pdf" -> listOf("pdf")
        "text/csv" -> listOf("csv")
        else -> emptyList()
    }

    override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {
        val d = FileDialog(frame, "Salva", FileDialog.SAVE)
        d.file = suggestedName
        d.isVisible = true
        val name = d.file ?: return
        onDone(runCatching { File(d.directory, name).writeBytes(bytes) }.isSuccess)
    }

    override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) {
        val d = FileDialog(frame, "Apri", FileDialog.LOAD)
        val ext = mimeTypes.flatMap(::extensions)
        if (ext.isNotEmpty()) d.setFilenameFilter { _, n -> ext.any { n.lowercase().endsWith(".$it") } }
        d.isVisible = true
        val name = d.file ?: return onResult(null)
        onResult(runCatching { PickedFile(name, File(d.directory, name).readBytes()) }.getOrNull())
    }

    /** Sul computer "condividi" copia il testo negli appunti (si incolla in una mail o in Excel). */
    override fun shareText(subject: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        toast("Copiato negli appunti: incollalo dove vuoi")
    }

    /**
     * Come prima: prima la cartella `-Dsagoma.assets` (se c'è), poi le risorse del programma; davanti, se c'è
     * `assetCacheDir`, la cache su disco. La cache vale per questa versione della sorgente (impronta della
     * cartella degli asset e del programma): se cambiano si riparte da una cache nuova.
     */
    override val assetStore: AssetStore = buildCachedAssetStore(
        source = CompositeAssetStore(
            listOfNotNull(assets?.let { DirectoryAssetStore(it) }, ClasspathAssetStore(javaClass.classLoader)),
        ),
        cacheBaseDir = assetCacheDir,
        sourceStamp = if (assetCacheDir == null) null
        else "assets=" + (assets?.let { directoryFingerprint(it) } ?: "none") + ";" + codeSourceStamp(DesktopPlatform::class.java),
        maxBytes = DESKTOP_ASSET_CACHE_BYTES,
        protectedDirs = listOfNotNull(assets),
    )

    override fun decodeImage(bytes: ByteArray): ImageBitmap? = SkiaImages.decode(bytes)
    override fun encodePng(image: ImageBitmap): ByteArray? = SkiaImages.encodePng(image)

    /** Immagini sì; i PDF come sfondo arriveranno (per ora si usa una foto o uno screenshot del PDF). */
    override suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage? =
        if (pdf) null else SkiaImages.importImage(bytes, maxSide)

    override fun createPdf(pages: List<PdfPage>, density: Density): ByteArray = SkiaImages.rasterPdf(pages, density)

    override fun formatDate(epochMillis: Long, withTime: Boolean): String =
        (if (withTime) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.SHORT)).format(Date(epochMillis))

    override fun now(): Long = System.currentTimeMillis()

    override fun createSceneRenderer(): SceneRenderer = runCatching { DesktopGlSceneRenderer(assetStore::peek) }.getOrElse {
        UnavailableSceneRenderer("La vista 3D non è disponibile su questo computer (OpenGL non trovato).")
    }
}

/** Spazio massimo della cache su disco degli asset sul computer. */
private const val DESKTOP_ASSET_CACHE_BYTES = 512L * 1024 * 1024
