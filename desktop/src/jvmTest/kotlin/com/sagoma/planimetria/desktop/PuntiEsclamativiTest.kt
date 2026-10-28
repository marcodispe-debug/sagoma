package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.PlanCheck
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * L'app vera: i «!» dei problemi sulla pianta. La camera piccola e senza finestra ha due norme nello stesso punto (un «!» con
 * il 2); la cucina sovrapposta al soggiorno un errore. Tocco sul «!» della camera: la scheda con i due problemi; «Togli dalla
 * pianta» toglie il primo (resta il segno dell'altro); nell'elenco di «Controlla la pianta» quel problema c'è ancora, con «Rimetti
 * sulla pianta». Immagini in build/punti-esclamativi-*.png.
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class PuntiEsclamativiTest {
    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @Test
    fun puntiSullaPianta() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val living = Room(
                1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0),
                openings = listOf(
                    Opening.default(1, OpeningKind.Door, 0, 250.0).copy(entrance = true),
                    Opening.default(2, OpeningKind.Window2, 2, 250.0),
                    Opening.default(3, OpeningKind.Door, 1, 150.0),
                ),
            )
            val bedroom = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(250.0, 300.0).map { it + Vec2(500.0, 0.0) })
            val kitchen = Room(
                3, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 250.0).map { it + Vec2(-150.0, 300.0) },
                openings = listOf(Opening.default(4, OpeningKind.Window1, 3, 125.0)),
            )
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(living, bedroom, kitchen))))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var t = 0L
            var store: ViewModelStore? = null
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                fun frames(n: Int = 10) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(20) }
                fun shot(name: String) {
                    t += 50_000_000L
                    File("build/punti-esclamativi-$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                fun nodes() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                fun marks() = nodes().mapNotNull { n ->
                    n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull { it.endsWith(" qui") }?.let { it to n.boundsInWindow.center }
                }
                fun withText(s: String) = nodes().filter { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == s } == true }
                fun tap(p: Offset) {
                    scene.sendPointerEvent(PointerEventType.Move, p)
                    scene.sendPointerEvent(PointerEventType.Press, p)
                    scene.sendPointerEvent(PointerEventType.Release, p)
                    frames()
                }
                frames(60)
                // Il controllo parte mezzo secondo dopo l'apertura.
                for (k in 0 until 40) if (marks().isEmpty()) frames(2)
                val vm = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.first()
                shot("pianta")
                val first = marks()
                assertTrue(first.size >= 2, "segni: $first")
                val camera = assertNotNull(first.firstOrNull { it.first == "2 problemi qui" }, "segno con due problemi: $first")
                tap(camera.second)
                shot("scheda")
                val hide = withText("Togli dalla pianta")
                assertEquals(2, hide.size, "pulsanti della scheda")
                tap(hide.first().boundsInWindow.center)
                frames(20)
                shot("tolto")
                assertEquals(1, vm.state.value.fullBuilding.hiddenChecks.size)
                assertTrue(marks().none { it.first == "2 problemi qui" }, "segni dopo: ${marks()}")
                assertEquals(first.size, marks().size, "il segno della camera resta, con un problema")
                // L'elenco: il problema tolto c'è ancora e si può rimettere.
                val chip = assertNotNull(nodes().firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("⚠") } == true }, "segnale")
                tap(chip.boundsInWindow.center)
                frames(10)
                shot("elenco")
                assertEquals(1, withText("Rimetti sulla pianta").size)
                assertTrue(PlanCheck.of(vm.state.value.fullBuilding).any { PlanCheck.hidden(vm.state.value.fullBuilding, it) })
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
