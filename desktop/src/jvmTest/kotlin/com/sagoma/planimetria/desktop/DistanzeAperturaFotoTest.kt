package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Foto delle distanze dagli angoli mentre si trascinano una finestra, una porta e un balcone (e dopo il rilascio, senza).
 * Parte solo con SAGOMA_FOTO_DISTANZE=true; immagini in build/distanze/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DistanzeAperturaFotoTest {
    private val plan = FloorPlan(
        listOf(
            Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(520.0, 420.0)).copy(
                wallThickness = 30.0,
                openings = listOf(
                    Opening.default(1, OpeningKind.Window2, 0, 150.0),
                    Opening.default(2, OpeningKind.Balcony2, 2, 260.0),
                ),
            ),
            Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(360.0, 420.0).map { it + Vec2(520.0, 0.0) }).copy(
                wallThickness = 30.0,
                openings = listOf(Opening.default(1, OpeningKind.Door, 3, 300.0)),
            ),
        ),
    )

    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_DISTANZE") != "true") return
        val out = File("build/distanze").apply { mkdirs() }
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Distanze")
            repo.planStore(p.id).save(Building.single(plan))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var store: ViewModelStore? = null
            var t = 0L
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                fun frames(n: Int = 6) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(10) }
                fun shot(name: String) {
                    frames()
                    t += 50_000_000L
                    File(out, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                frames(20)
                val vm = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.single()
                fun drag(name: String, target: DragTarget.Opening, at: Vec2, by: Vec2) {
                    vm.beginDrag(target, at)
                    vm.dragBy(target, by * 0.5)
                    vm.dragBy(target, by)
                    shot("$name-durante")
                    vm.endDrag()
                    shot("$name-rilasciata")
                }
                drag("finestra", DragTarget.Opening(1, 1), Vec2(150.0, 0.0), Vec2(-70.0, 0.0))
                drag("porta", DragTarget.Opening(2, 1), Vec2(520.0, 120.0), Vec2(0.0, 90.0))
                drag("balcone", DragTarget.Opening(1, 2), Vec2(260.0, 420.0), Vec2(-80.0, 0.0))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
