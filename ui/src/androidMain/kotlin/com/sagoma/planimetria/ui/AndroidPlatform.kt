package com.sagoma.planimetria.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.persistence.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Servizi della piattaforma su Android. Va creata in `onCreate` dell'attività, perché registra i selettori
 * di file di sistema (Storage Access Framework: nessun permesso di archiviazione).
 */
class AndroidPlatform(
    private val activity: ComponentActivity,
    override val isPro: Boolean,
    override val projects: ProjectRepository,
    override val tips: TipStore,
    private val sceneRenderer: (Context, AssetStore) -> SceneRenderer,
) : Platform {

    /** "Salva con nome": nome proposto e tipo MIME → dove salvare. */
    private class Save : ActivityResultContract<Pair<String, String>, Uri?>() {
        override fun createIntent(context: Context, input: Pair<String, String>) =
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
    }

    /** "Apri": tipi MIME accettati → file scelto. */
    private class Open : ActivityResultContract<Array<String>, Uri?>() {
        override fun createIntent(context: Context, input: Array<String>) =
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if (input.size == 1) input[0] else ANY_FILE)
                .apply { if (input.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, input) }
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
    }

    private var pendingSave: Pair<ByteArray, (Boolean) -> Unit>? = null
    private var pendingOpen: ((PickedFile?) -> Unit)? = null

    private val saveLauncher = activity.registerForActivityResult(Save()) { uri ->
        val (bytes, done) = pendingSave ?: return@registerForActivityResult
        pendingSave = null
        if (uri == null) return@registerForActivityResult
        val ok = runCatching { activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
        done(ok)
    }

    private val openLauncher = activity.registerForActivityResult(Open()) { uri ->
        val done = pendingOpen ?: return@registerForActivityResult
        pendingOpen = null
        if (uri == null) { done(null); return@registerForActivityResult }
        val bytes = runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        done(bytes?.let { PickedFile(uri.lastPathSegment ?: "file", it) })
    }

    override fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {
        pendingSave = bytes to onDone
        saveLauncher.launch(suggestedName to mimeType)
    }

    override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) {
        pendingOpen = onResult
        openLauncher.launch(mimeTypes.toTypedArray())
    }

    override fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(send, subject))
    }

    override val assetStore: AssetStore = createAndroidAssetStore(activity, isPro)

    override fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    override fun encodePng(image: ImageBitmap): ByteArray? {
        val out = ByteArrayOutputStream()
        return if (image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
    }

    override suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage? = withContext(Dispatchers.IO) {
        val bmp = runCatching { if (pdf) renderPdf(bytes, maxSide) else decodeScaled(bytes, maxSide) }.getOrNull() ?: return@withContext null
        val side = max(bmp.width, bmp.height)
        val img = if (side <= maxSide) bmp else Bitmap.createScaledBitmap(bmp, bmp.width * maxSide / side, bmp.height * maxSide / side, true)
        val out = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.JPEG, 90, out)
        ImportedImage(out.toByteArray(), img.width, img.height)
    }

    /** Prima pagina del PDF su sfondo bianco, `maxSide` pixel sul lato lungo. */
    private fun renderPdf(bytes: ByteArray, maxSide: Int): Bitmap? {
        val tmp = File.createTempFile("sfondo", ".pdf", activity.cacheDir)
        try {
            tmp.writeBytes(bytes)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    r.openPage(0).use { page ->
                        val k = maxSide.toFloat() / max(page.width, page.height)
                        val bmp = Bitmap.createBitmap((page.width * k).roundToInt(), (page.height * k).roundToInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bmp
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }

    /** Foto girata come è stata scattata (EXIF) e già ridotta in lettura. */
    private fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, info, _ ->
                val side = max(info.size.width, info.size.height)
                if (side > maxSide) d.setTargetSampleSize((side + maxSide - 1) / maxSide)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }

    override fun createPdf(pages: List<PdfPage>, density: Density): ByteArray? {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, p ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(p.width, p.height, i + 1).create())
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(page.canvas), Size(p.width.toFloat(), p.height.toFloat())) {
                    p.draw(this)
                }
                doc.finishPage(page)
            }
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    override fun formatDate(epochMillis: Long, withTime: Boolean): String =
        (if (withTime) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.SHORT)).format(Date(epochMillis))

    override fun now(): Long = System.currentTimeMillis()

    override fun createSceneRenderer(): SceneRenderer = sceneRenderer(activity, assetStore)

    override fun gestureExclusion(modifier: Modifier): Modifier = modifier.systemGestureExclusion()

    @androidx.compose.runtime.Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

    override fun setStatusBarHidden(hidden: Boolean) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}
