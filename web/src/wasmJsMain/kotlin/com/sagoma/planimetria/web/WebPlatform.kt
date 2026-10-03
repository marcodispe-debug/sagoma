package com.sagoma.planimetria.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.EmptyAssetStore
import com.sagoma.planimetria.ui.ImportedImage
import com.sagoma.planimetria.ui.PdfPage
import com.sagoma.planimetria.ui.PickedFile
import com.sagoma.planimetria.ui.Platform
import com.sagoma.planimetria.ui.SceneRenderer
import com.sagoma.planimetria.ui.SkiaImages
import com.sagoma.planimetria.ui.UnavailableSceneRenderer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

// Funzioni del browser usate dall'app (JavaScript).

private fun jsNow(): Double = js("Date.now()")

private fun jsDate(ms: Double, withTime: Boolean): String =
    js("withTime ? new Date(ms).toLocaleString(undefined, {dateStyle: 'medium', timeStyle: 'short'}) : new Date(ms).toLocaleDateString()")

private fun jsDownload(bytes: Uint8Array, name: String, mime: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([bytes], { type: mime }));
        const a = document.createElement('a');
        a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 30000);
    }""",
)

private fun jsPickFile(accept: String, onFile: (String?, Uint8Array?) -> Unit): Unit = js(
    """{
        const input = document.createElement('input');
        input.type = 'file'; input.accept = accept;
        input.onchange = () => {
            const f = input.files && input.files[0];
            if (!f) { onFile(null, null); return; }
            f.arrayBuffer().then(b => onFile(f.name, new Uint8Array(b)));
        };
        input.click();
    }""",
)

private fun jsCopy(text: String): Unit = js("{ if (navigator.clipboard) navigator.clipboard.writeText(text); }")

private fun ByteArray.toUint8Array(): Uint8Array {
    val a = Uint8Array(size)
    for (i in indices) a[i] = this[i]
    return a
}

private fun Uint8Array.toByteArray(): ByteArray = ByteArray(length) { this[it] }

/** Servizi della piattaforma nel browser. */
class WebPlatform : Platform {
    override val isPro: Boolean = false
    override val projects = BrowserProjectRepository(::now)
    override val tips = BrowserTipStore()

    /** Messaggio breve mostrato in basso. */
    var message by mutableStateOf<String?>(null)

    override fun toast(message: String) {
        this.message = message
    }

    override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {
        val ok = runCatching { jsDownload(bytes.toUint8Array(), suggestedName, mimeType) }.isSuccess
        onDone(ok)
    }

    override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) {
        val accept = mimeTypes.filter { it != com.sagoma.planimetria.ui.ANY_FILE }.joinToString(",")
        jsPickFile(accept) { name, data -> onResult(if (name == null || data == null) null else PickedFile(name, data.toByteArray())) }
    }

    override fun shareText(subject: String, text: String) {
        jsCopy(text)
        toast("Copiato negli appunti: incollalo dove vuoi")
    }

    /** Nessun asset sul web per ora: arredi e materiali 3D arriveranno con il catalogo online (un altro [AssetStore]). */
    override val assetStore: AssetStore = EmptyAssetStore

    override fun decodeImage(bytes: ByteArray): ImageBitmap? = SkiaImages.decode(bytes)
    override fun encodePng(image: ImageBitmap): ByteArray? = SkiaImages.encodePng(image)
    override suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage? =
        if (pdf) null else SkiaImages.importImage(bytes, maxSide)

    override fun createPdf(pages: List<PdfPage>, density: Density): ByteArray = SkiaImages.rasterPdf(pages, density, dpi = 200)

    override fun formatDate(epochMillis: Long, withTime: Boolean): String = jsDate(epochMillis.toDouble(), withTime)
    override fun now(): Long = jsNow().toLong()

    override fun createSceneRenderer(): SceneRenderer =
        WebGlSceneRenderer().takeIf { it.available } ?: UnavailableSceneRenderer("Questo browser non supporta la grafica 3D (WebGL).")
}
