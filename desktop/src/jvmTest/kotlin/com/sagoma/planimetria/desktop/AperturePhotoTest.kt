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
            // Seconda stanza, a destra: i tipi del secondo gruppo.
            val room2 = Room(2, "Studio", RoomType.Studio, RoomFactory.rectangle(600.0, 450.0).map { it + com.sagoma.planimetria.model.Vec2(800.0, 0.0) }).copy(
                openings = listOf(
                    o(21, OpeningKind.Window1, 0, 80.0, OpeningPreset.PivotSash),
                    o(22, OpeningKind.Window2, 0, 270.0, OpeningPreset.ThreeLeaves).copy(shading = com.sagoma.planimetria.model.Shading.InteriorShutters),
                    o(23, OpeningKind.Window1, 0, 470.0, OpeningPreset.RoundWindow),
                    o(24, OpeningKind.Door, 1, 110.0, OpeningPreset.Pivot),
                    o(25, OpeningKind.Door, 1, 320.0, OpeningPreset.Glazed),
                    o(26, OpeningKind.Balcony2, 2, 150.0, OpeningPreset.FourLeaves),
                    o(27, OpeningKind.Balcony2, 2, 440.0, OpeningPreset.LiftSlide).copy(width = 220.0),
                    o(28, OpeningKind.Door, 3, 120.0, OpeningPreset.Armored),
                    o(29, OpeningKind.Window2, 3, 320.0, OpeningPreset.ArchedWindow).copy(shading = com.sagoma.planimetria.model.Shading.Venetian),
                ),
            )
            // Terza stanza, sotto: inferriata, sopraluce, legno, persiana alla romana e bovindo.
            val room3 = Room(3, "Camera", RoomType.Camera, RoomFactory.rectangle(600.0, 450.0).map { it + com.sagoma.planimetria.model.Vec2(0.0, 650.0) }).copy(
                openings = listOf(
                    o(31, OpeningKind.Window1, 0, 100.0, OpeningPreset.Grille),
                    o(32, OpeningKind.Window1, 0, 300.0, OpeningPreset.TopLight),
                    o(33, OpeningKind.Window2, 0, 480.0, OpeningPreset.WoodWindow),
                    o(34, OpeningKind.Window1, 2, 130.0, OpeningPreset.RomanShutter),
                    o(35, OpeningKind.Window2, 2, 380.0, OpeningPreset.BayWindow),
                    o(36, OpeningKind.Door, 1, 225.0, OpeningPreset.GarageUpAndOver),
                    o(37, OpeningKind.Door, 3, 225.0, OpeningPreset.GarageSectional),
                ),
            )
            repo.planStore(p.id).save(Building.single(FloorPlan(rooms = listOf(room, room2, room3))))
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            repo.markOpened(p.id)

            val schede = listOf(
                "01-finestra-fissa" to 1L, "02-vasistas" to 2L, "03-anta-ribalta" to 3L, "04-porta-ante-disuguali" to 4L,
                "05-porta-scorrevole-esterna" to 5L, "06-porta-a-libro" to 6L, "07-porta-filo-muro" to 7L, "08-nicchia" to 8L,
                "09-arco-sesto-acuto" to 9L, "10-arco-ribassato" to 10L, "11-vetrata-loft" to 11L,
            ).map { (n, id) -> Triple(n, 1L, id) } + listOf(
                "21-finestra-bilico" to 21L, "22-finestra-3-ante-scuri" to 22L, "23-oblo" to 23L, "24-porta-bilico" to 24L,
                "25-porta-sopraluce" to 25L, "26-balcone-4-ante" to 26L, "27-alzante" to 27L, "28-porta-blindata" to 28L, "29-finestra-arco-veneziana" to 29L,
            ).map { (n, id) -> Triple(n, 2L, id) } + listOf(
                "31-inferriata" to 31L, "32-sopraluce" to 32L, "33-legno-fermavetro" to 33L, "34-persiana-romana" to 34L, "35-bovindo" to 35L,
                "36-basculante" to 36L, "37-sezionale" to 37L,
            ).map { (n, id) -> Triple(n, 3L, id) }
            val platform = DesktopPlatform(projects = repo, tips = tips, theme = FileThemeStore(dir), assets = null)
            var t = 0L
            var store: ViewModelStore? = null
            ImageComposeScene(1800, 1100, Density(1f)).use { scene ->
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
                for ((nome, roomId, id) in schede) {
                    vm.deselectAll()
                    frames(3)
                    vm.selectOpening(roomId, id)
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
