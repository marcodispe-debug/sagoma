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
 * L'app vera: «Controlla la pianta» → «✓ Correggi tutti» nel gruppo degli errori. La finestra più alta del muro si accorcia e la
 * cucina sovrapposta al soggiorno si sposta fino al muro in comune, insieme; un solo ↶ rimette tutto. Immagini in
 * build/correggi-tutti-*.png.
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class CorreggiTuttiTest {
    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @Test
    fun correggiTutti() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val living = Room(
                1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0),
                openings = listOf(
                    Opening.default(1, OpeningKind.Door, 0, 250.0).copy(entrance = true),
                    Opening.default(2, OpeningKind.Window2, 2, 250.0),
                    // Fino a 300 cm sul muro alto 270.
                    Opening(3, OpeningKind.Window1, 3, 200.0, 80.0, 200.0, sillHeight = 100.0),
                ),
            )
            val kitchen = Room(
                3, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 250.0).map { it + Vec2(-150.0, 300.0) },
                openings = listOf(Opening.default(4, OpeningKind.Window1, 3, 125.0)),
            )
            val start = Building.single(FloorPlan(listOf(living, kitchen)))
            repo.planStore(p.id).save(start)
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
                    File("build/correggi-tutti-$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                fun nodes() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                fun withText(s: String) = nodes().filter { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == s } == true }
                fun tap(p: Offset) {
                    scene.sendPointerEvent(PointerEventType.Move, p)
                    scene.sendPointerEvent(PointerEventType.Press, p)
                    scene.sendPointerEvent(PointerEventType.Release, p)
                    frames()
                }
                fun chip() = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("⚠") } == true }
                frames(60)
                // Il controllo parte mezzo secondo dopo l'apertura.
                for (k in 0 until 40) if (chip() == null) frames(2)
                val vm = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.first()
                fun errors() = PlanCheck.of(vm.state.value.fullBuilding).filter { it.kind.error }.map { it.kind }.toSet()
                assertEquals(setOf(PlanCheck.Kind.OpeningOverWall, PlanCheck.Kind.Overlap), errors())
                val before = vm.state.value.fullBuilding
                tap(assertNotNull(chip(), "segnale").boundsInWindow.center)
                // Il pulsante conta anche la cucina appena si sa che spostarla funziona (si prova fuori dal filo della pianta).
                for (k in 0 until 40) if (withText("✓ Correggi tutti (2)").isEmpty()) frames(2)
                shot("elenco")
                tap(withText("✓ Correggi tutti (2)").single().boundsInWindow.center)
                for (k in 0 until 40) if (errors().isNotEmpty()) frames(2)
                frames(10)
                shot("corretti")
                assertEquals(emptySet(), errors())
                val plan = vm.state.value.plan
                assertEquals(170.0, plan.rooms.single { it.id == 1L }.openings.single { it.id == 3L }.height)
                assertTrue(PlanCheck.of(vm.state.value.fullBuilding).none { it.kind == PlanCheck.Kind.Overlap })
                // Il gruppo degli errori non c'è più: niente pulsante.
                assertTrue(nodes().none { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("✓ Correggi tutti") } == true })
                // Un solo ↶ rimette la finestra e la cucina.
                vm.undo()
                assertEquals(before, vm.state.value.fullBuilding)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
