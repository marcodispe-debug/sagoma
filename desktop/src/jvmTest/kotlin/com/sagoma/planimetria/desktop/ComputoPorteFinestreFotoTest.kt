package com.sagoma.planimetria.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.GarageDoor
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileThemeStore
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.ui.LocalPlatform
import com.sagoma.planimetria.ui.TakeoffDialog
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Foto del computo con l'elenco «Porte e finestre» sul computer e sul telefono. Non controlla niente: serve a guardarlo.
 * Parte solo con SAGOMA_FOTO_COMPUTO=true; immagini in build/computo/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ComputoPorteFinestreFotoTest {
    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_COMPUTO") != "true") return
        val out = File("build/computo").also { it.mkdirs() }
        val window = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(width = 120.0, height = 140.0, shading = Shading.Shutters, grille = true, sillMaterial = StairMaterial.Marble)
        val plan = FloorPlan(
            listOf(
                Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(400.0, 300.0)).copy(
                    openings = listOf(window, Opening.default(2, OpeningKind.Door, 2, 200.0).copy(doorModel = DoorModel.Panels)),
                ),
                Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(400.0, 0.0) }).copy(
                    openings = listOf(
                        window,
                        Opening.default(2, OpeningKind.Window1, 1, 150.0).copy(windowModel = WindowModel.Wood, shading = Shading.RomanShutter, insectScreen = true),
                        Opening.default(3, OpeningKind.Balcony2, 2, 200.0).copy(awning = true, insectScreen = true, sillMaterial = StairMaterial.Stone),
                    ),
                ),
                Room(3, "Garage", RoomType.Altro, RoomFactory.rectangle(400.0, 600.0).map { it + Vec2(800.0, 0.0) }).copy(
                    openings = listOf(Opening.default(1, OpeningKind.Door2, 0, 200.0).copy(garage = GarageDoor.Sectional, width = 250.0, height = 215.0)),
                ),
            ),
        )
        val dir = Files.createTempDirectory("sagoma-computo").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val platform = DesktopPlatform(projects = repo, tips = FileTipStore(dir), theme = FileThemeStore(dir), assets = null)
            val vm = EditorViewModel(object : PlanStore {
                override fun load() = Building.single(plan)
                override fun save(building: Building) {}
            })
            for ((nome, w, h, d) in listOf(Quad("pc", 1400, 900, 1f), Quad("tel", 1080, 2340, 2.625f))) {
                ImageComposeScene(w, h, Density(d)).use { scene ->
                    scene.setContent {
                        CompositionLocalProvider(LocalPlatform provides platform) {
                            androidx.compose.material3.MaterialTheme { TakeoffDialog(vm.state.value, null, onDismiss = {}) }
                        }
                    }
                    var t = 0L
                    repeat(20) { t += 50_000_000L; scene.render(t) }
                    File(out, "$nome-porte-finestre.png").writeBytes(scene.render(t + 50_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                    // In fondo all'elenco (rotella del mouse sul computo): davanzali e soglie.
                    val center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
                    repeat(30) { scene.sendPointerEvent(PointerEventType.Scroll, center, scrollDelta = androidx.compose.ui.geometry.Offset(0f, 3f)) }
                    repeat(20) { t += 50_000_000L; scene.render(t) }
                    File(out, "$nome-davanzali.png").writeBytes(scene.render(t + 50_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private data class Quad(val nome: String, val w: Int, val h: Int, val d: Float)
}
