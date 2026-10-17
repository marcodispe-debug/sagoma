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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Catalogo scaricato a richiesta: la vista 3D ridà la scena al renderer solo quando arriva un file che mancava. */
class SceneAssetsReloadTest {
    private class CountingRenderer : SceneRenderer {
        var scenes = 0
        @Composable override fun Surface(modifier: Modifier) {}
        override fun setScene(scene: Scene3D) { scenes++ }
        override fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean) {}
    }

    private class FakePlatform(private val renderer: SceneRenderer) : Platform {
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
        override fun createSceneRenderer(): SceneRenderer = renderer
    }

    @Test
    fun `la scena si ricarica quando arriva un file che mancava, non a ogni controllo`() {
        val renderer = CountingRenderer()
        val view = View3DState()
        view.attach(FakePlatform(renderer))
        view.setScene(Scene3D.build(FloorPlan(), null, null, ceilings = false))
        val start = renderer.scenes
        val here = mutableSetOf<String>()

        // Primo controllo: mancano due modelli, niente da ricaricare.
        assertFalse(view.assetsChecked(setOf("a.glb", "b.glb")) { it in here })
        // Nulla di nuovo: nessuna ricarica.
        assertFalse(view.assetsChecked(setOf("a.glb", "b.glb")) { it in here })
        // Arriva il primo: una ricarica.
        here += "a.glb"
        assertTrue(view.assetsChecked(setOf("b.glb")) { it in here })
        // Arriva il secondo: un'altra.
        here += "b.glb"
        assertTrue(view.assetsChecked(emptySet()) { it in here })
        // Tutto qui: i controlli successivi non ricaricano più.
        assertFalse(view.assetsChecked(emptySet()) { it in here })
        assertEquals(start + 2, renderer.scenes)
    }

    @Test
    fun `un file che non serve piu alla scena nuova non provoca ricariche`() {
        val renderer = CountingRenderer()
        val view = View3DState()
        view.attach(FakePlatform(renderer))
        view.setScene(Scene3D.build(FloorPlan(), null, null, ceilings = false))
        assertFalse(view.assetsChecked(setOf("vecchio.glb")) { false })
        val start = renderer.scenes
        // L'arredo è stato tolto: non manca più niente, ma non è nemmeno arrivato.
        assertFalse(view.assetsChecked(emptySet()) { false })
        assertEquals(start, renderer.scenes)
    }
}
