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
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.Beam
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileThemeStore
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Foto di tutte le schede Info (stanza, muro, porta, finestra, impianti, scala, arredo, muro singolo, colonna, trave) sul
 * computer, sul computer con la finestra molto alta (si vede tutta la scheda) e sul telefono. Non controlla niente: serve a
 * guardarle. Parte solo con SAGOMA_FOTO_INFO=true; immagini in build/schede-info/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SchedeInfoFotoTest {
    private class Formato(val nome: String, val w: Int, val h: Int, val density: Float)

    private val formati = listOf(
        Formato("pc", 1400, 900, 1f),
        Formato("pc-tutta", 1400, 2600, 1f),
        Formato("tel", 1080, 2340, 2.625f),
    )

    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_INFO") != "true") return
        val out = File("build/schede-info").also { it.mkdirs() }
        val dir = Files.createTempDirectory("sagoma-info").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)).copy(
                openings = listOf(
                    Opening.default(1, OpeningKind.Window2, 0, 250.0),
                    Opening.default(2, OpeningKind.Door, 2, 120.0),
                ),
                fixtures = listOf(
                    Fixture.default(1, FixtureKind.Radiator).copy(wallIndex = 1, position = 150.0),
                    Fixture.default(2, FixtureKind.WallSpot).copy(wallIndex = 3, position = 200.0),
                ),
            )
            val plan = FloorPlan(
                rooms = listOf(room),
                stairs = listOf(Stair(1, StairKind.LTurn, Vec2(750.0, 200.0))),
                columns = listOf(Column(1, Vec2(400.0, 300.0))),
                beams = listOf(Beam(1, Vec2(0.0, 200.0), Vec2(500.0, 200.0))),
                freeWalls = listOf(FreeWall(1, Vec2(100.0, 100.0), Vec2(100.0, 300.0))),
                furniture = listOf(Furniture(1, "ph_sofa_01", Vec2(250.0, 300.0), width = 157.0, depth = 66.0, height = 80.0)),
            )
            repo.planStore(p.id).save(Building.single(plan))
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            repo.markOpened(p.id)

            val schede: List<Pair<String, (EditorViewModel) -> Unit>> = listOf(
                "01-stanza" to { vm -> vm.showRoomInfo(1) },
                "02-muro" to { vm -> vm.selectWall(1, 1) },
                "03-finestra" to { vm -> vm.selectOpening(1, 1) },
                "04-porta" to { vm -> vm.selectOpening(1, 2) },
                "05-termosifone" to { vm -> vm.selectFixture(1, 1) },
                "06-faretto" to { vm -> vm.selectFixture(1, 2) },
                "07-scala" to { vm -> vm.selectStair(1) },
                "08-arredo" to { vm -> vm.selectFurniture(1) },
                "09-muro-singolo" to { vm -> vm.selectFreeWall(1) },
                "10-colonna" to { vm -> vm.selectColumn(1) },
                "11-trave" to { vm -> vm.selectBeam(1) },
            )
            for (f in formati) {
                val platform = DesktopPlatform(projects = repo, tips = tips, theme = FileThemeStore(dir), assets = null)
                var t = 0L
                var store: ViewModelStore? = null
                ImageComposeScene(f.w, f.h, Density(f.density)).use { scene ->
                    fun frames(n: Int) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(5) }
                    scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                    frames(30)
                    val vm = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.first()
                    for ((nome, select) in schede) {
                        vm.deselectAll()
                        frames(3)
                        select(vm)
                        vm.setInfoExpanded(true)
                        frames(20)
                        t += 50_000_000L
                        File(out, "${f.nome}-$nome.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                        // Con le schede: una foto per ogni linguetta (tocco sulla linguetta, poi foto).
                        // Le linguette si cercano ogni volta: la tendina può essersi spostata dopo il tocco precedente.
                        fun tabs() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                            .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                            .map { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() to n.boundsInWindow.center }
                        fun tap(p: androidx.compose.ui.geometry.Offset) {
                            scene.sendPointerEvent(PointerEventType.Move, p)
                            scene.sendPointerEvent(PointerEventType.Press, p)
                            scene.sendPointerEvent(PointerEventType.Release, p)
                            frames(10)
                        }
                        for (k in 1 until tabs().size) {
                            val tab = tabs()[k]
                            tap(tab.second)
                            t += 50_000_000L
                            File(out, "${f.nome}-$nome-${k + 1}-${tab.first.lowercase().replace(' ', '-')}.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                        }
                        // Si torna alla prima scheda, come la troverà chi la apre.
                        tabs().firstOrNull()?.let { tap(it.second) }
                    }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
