package com.sagoma.planimetria.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.persistence.ProjectRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNotSame

/**
 * Riaprendo la vista 3D il renderer che sa ripartire su una superficie nuova si riusa (sul telefono: niente motore, materiali e
 * luci ambiente da rifare); gli altri si liberano e si ricreano come prima. Uscendo dall'editor si libera tutto.
 */
class Renderer3DReuseTest {
    private class FakeRenderer(override val reusable: Boolean) : SceneRenderer {
        var scenes = 0
        var detached = 0
        var released = 0
        @Composable override fun Surface(modifier: Modifier) {}
        override fun setScene(scene: Scene3D) { scenes++ }
        override fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean) {}
        override fun detachSurface() { detached++ }
        override fun release() { released++ }
    }

    private class FakePlatform(private val reusable: Boolean) : Platform {
        val created = mutableListOf<FakeRenderer>()
        override val assetStore: AssetStore get() = error("non serve")
        override val projects: ProjectRepository get() = error("non serve")
        override val tips: TipStore get() = error("non serve")
        override fun toast(message: String) {}
        override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {}
        override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) {}
        override fun shareText(subject: String, text: String) {}
        override fun decodeImage(bytes: ByteArray): ImageBitmap = error("non serve")
        override fun encodePng(image: ImageBitmap): ByteArray? = null
        override suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage? = null
        override fun createPdf(pages: List<PdfPage>, density: Density): ByteArray? = null
        override fun formatDate(epochMillis: Long, withTime: Boolean): String = ""
        override fun now(): Long = 0
        override fun createSceneRenderer(): SceneRenderer = FakeRenderer(reusable).also { created += it }
    }

    @Test
    fun `chiudendo e riaprendo il 3D il renderer riutilizzabile resta lo stesso`() {
        val platform = FakePlatform(reusable = true)
        val view = View3DState()
        val first = view.attach(platform) as FakeRenderer
        view.setScene(Scene3D.build(FloorPlan(), null, null, ceilings = false))
        view.detach()
        assertEquals(1, first.detached)
        assertEquals(0, first.released)
        // Riapertura: stesso renderer, riceve la scena, nessun renderer nuovo.
        val scenesBefore = first.scenes
        val second = view.attach(platform)
        assertSame(first, second)
        assertEquals(1, platform.created.size)
        assertEquals(scenesBefore + 1, first.scenes)
        // Uscendo dall'editor si libera (una volta sola, anche chiamando di nuovo).
        view.detach()
        view.dispose()
        view.dispose()
        assertEquals(1, first.released)
    }

    @Test
    fun `il renderer non riutilizzabile si libera a ogni chiusura come prima`() {
        val platform = FakePlatform(reusable = false)
        val view = View3DState()
        val first = view.attach(platform) as FakeRenderer
        view.detach()
        assertEquals(1, first.released)
        assertEquals(0, first.detached)
        val second = view.attach(platform)
        assertNotSame(first, second)
        view.detach()
        view.dispose()
        assertEquals(1, (second as FakeRenderer).released)
        assertEquals(1, first.released)
    }

    @Test
    fun `con una piattaforma nuova il renderer tenuto da parte si libera`() {
        val old = FakePlatform(reusable = true)
        val view = View3DState()
        val first = view.attach(old) as FakeRenderer
        view.detach()
        // Per esempio l'attività è stata ricreata: il renderer vecchio apparteneva all'altra.
        val fresh = FakePlatform(reusable = true)
        val second = view.attach(fresh)
        assertNotSame(first, second)
        assertEquals(1, first.released)
        assertEquals(1, fresh.created.size)
    }

    @Test
    fun `chiudere l'editor con il 3D aperto libera il renderer una volta`() {
        val platform = FakePlatform(reusable = true)
        val view = View3DState()
        val r = view.attach(platform) as FakeRenderer
        view.dispose()
        assertEquals(1, r.detached)
        assertEquals(1, r.released)
    }

    @Test
    fun `in secondo piano si libera il renderer del 3D chiuso, non quello aperto`() {
        val platform = FakePlatform(reusable = true)
        val view = View3DState()
        val first = view.attach(platform) as FakeRenderer
        // 3D aperto: resta.
        view.releaseIfClosed()
        assertEquals(0, first.released)
        // 3D chiuso: si libera, e la prossima apertura ne crea uno nuovo.
        view.detach()
        view.releaseIfClosed()
        assertEquals(1, first.released)
        val second = view.attach(platform)
        assertNotSame(first, second)
        view.dispose()
        assertEquals(1, first.released)
        assertEquals(1, (second as FakeRenderer).released)
    }
}
