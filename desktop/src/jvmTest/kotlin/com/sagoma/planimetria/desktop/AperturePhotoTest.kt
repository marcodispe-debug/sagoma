package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.OpeningPreset
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileThemeStore
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Foto dei tipi nuovi di porte, finestre e varchi: la pianta con tutti insieme e la scheda Info di ognuno, linguetta per
 * linguetta. Non controlla niente: serve a guardarle. Parte solo con SAGOMA_FOTO_APERTURE=true; immagini in build/aperture/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class AperturePhotoTest {
    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_APERTURE") != "true") return
        val out = File("build/aperture").also { it.mkdirs() }
        val dir = Files.createTempDirectory("sagoma-aperture").toFile()
        fun o(id: Long, kind: OpeningKind, wall: Int, pos: Double, preset: OpeningPreset = OpeningPreset.None) =
            preset.apply(Opening.default(id, kind, wall, pos)).copy(position = pos)
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0)).copy(
                openings = listOf(
                    o(1, OpeningKind.Window1, 0, 90.0, OpeningPreset.Fixed),
                    o(2, OpeningKind.Window1, 0, 250.0, OpeningPreset.Hopper),
                    o(3, OpeningKind.Window1, 0, 400.0, OpeningPreset.TiltTurn),
                    o(4, OpeningKind.Door2, 1, 110.0, OpeningPreset.UnequalLeaves),
                    o(5, OpeningKind.Door, 1, 320.0, OpeningPreset.WallSlide),
                    o(6, OpeningKind.Door, 2, 110.0, OpeningPreset.Folding),
                    o(7, OpeningKind.Door, 2, 270.0, OpeningPreset.Frameless),
                    o(8, OpeningKind.Niche, 2, 420.0),
                    o(9, OpeningKind.Passage, 3, 80.0).copy(width = 100.0, style = PassageStyle.Pointed),
                    o(10, OpeningKind.Passage, 3, 220.0).copy(width = 100.0, style = PassageStyle.Segmental),
                    o(11, OpeningKind.Window2, 3, 360.0, OpeningPreset.InteriorGlazing).copy(width = 140.0),
                ),
            )
            repo.planStore(p.id).save(Building.single(FloorPlan(rooms = listOf(room))))
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            repo.markOpened(p.id)

            val schede = listOf(
                "01-finestra-fissa" to 1L, "02-vasistas" to 2L, "03-anta-ribalta" to 3L, "04-porta-ante-disuguali" to 4L,
                "05-porta-scorrevole-esterna" to 5L, "06-porta-a-libro" to 6L, "07-porta-filo-muro" to 7L, "08-nicchia" to 8L,
                "09-arco-sesto-acuto" to 9L, "10-arco-ribassato" to 10L, "11-vetrata-loft" to 11L,
            )
            val platform = DesktopPlatform(projects = repo, tips = tips, theme = FileThemeStore(dir), assets = null)
            var t = 0L
            var store: ViewModelStore? = null
            ImageComposeScene(1400, 1300, Density(1f)).use { scene ->
                fun frames(n: Int) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(5) }
                fun shot(name: String) {
                    t += 50_000_000L
                    File(out, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                frames(30)
                val vm = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.first()
                vm.deselectAll()
                frames(10)
                shot("00-pianta")
                fun tabs() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                    .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                    .map { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() to n.boundsInWindow.center }
                fun tap(p: androidx.compose.ui.geometry.Offset) {
                    scene.sendPointerEvent(PointerEventType.Move, p)
                    scene.sendPointerEvent(PointerEventType.Press, p)
                    scene.sendPointerEvent(PointerEventType.Release, p)
                    frames(10)
                }
                for ((nome, id) in schede) {
                    vm.deselectAll()
                    frames(3)
                    vm.selectOpening(1, id)
                    vm.setInfoExpanded(true)
                    frames(20)
                    shot(nome)
                    for (k in 1 until tabs().size) {
                        val tab = tabs()[k]
                        tap(tab.second)
                        shot("$nome-${k + 1}-${tab.first.lowercase().replace(' ', '-')}")
                    }
                    tabs().firstOrNull()?.let { tap(it.second) }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
