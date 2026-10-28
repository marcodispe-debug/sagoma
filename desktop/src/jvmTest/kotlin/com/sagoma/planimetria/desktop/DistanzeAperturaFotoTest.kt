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
import com.sagoma.planimetria.geometry.MeasureTarget
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.WallDistances
import com.sagoma.planimetria.geometry.WallSide
import com.sagoma.planimetria.model.Column
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
 * Foto delle distanze dagli angoli mentre si trascinano una finestra, una porta e un balcone (e dopo il rilascio,
 * senza), spostando la finestra con la tastiera e con «Distanze dai muri» acceso (colonna e finestra, distanze scritte).
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
            // Camera a L.
            Room(
                2, "Camera", RoomType.Camera,
                listOf(Vec2(520.0, 0.0), Vec2(880.0, 0.0), Vec2(880.0, 250.0), Vec2(700.0, 250.0), Vec2(700.0, 420.0), Vec2(520.0, 420.0)),
            ).copy(
                wallThickness = 30.0,
                openings = listOf(Opening.default(1, OpeningKind.Door, 5, 300.0)),
            ),
        ),
        columns = listOf(Column(1, center = Vec2(330.0, 140.0))),
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
                // Tastiera: con la finestra selezionata ogni freccia la sposta di pochi pixel; tenuta giù, le quote restano.
                repeat(12) { vm.nudge(DragTarget.Opening(1, 1), Vec2(4.0, 0.0)) }
                shot("tastiera-durante")
                vm.endNudge()
                shot("tastiera-rilasciata")
                // «📏 Distanze dai muri» acceso: la colonna con le 4 distanze, scrivibili nella scheda.
                vm.selectColumn(1)
                vm.setInfoExpanded(true)
                vm.toggleWallGaps()
                shot("colonna-distanze")
                // Scritta una distanza: 100 cm dal muro a sinistra.
                val left = WallDistances.of(vm.state.value.plan, MeasureTarget.Column(1), vm.state.value.levelHeight).first { it.side == WallSide.Left }
                vm.setWallDistance(DragTarget.Column(1), left, 100.0)
                shot("colonna-100-da-sinistra")
                // La finestra: i due angoli e «Al centro del muro».
                vm.selectOpening(1, 1)
                shot("finestra-distanze")
                val gaps = WallDistances.of(vm.state.value.plan, MeasureTarget.Opening(1, 1), vm.state.value.levelHeight)
                vm.setWallDistance(DragTarget.Opening(1, 1), gaps[0], (gaps[0].distance + gaps[1].distance) / 2)
                shot("finestra-al-centro")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
