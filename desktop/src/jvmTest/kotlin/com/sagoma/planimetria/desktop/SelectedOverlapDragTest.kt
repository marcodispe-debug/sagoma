package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * L'app vera: plafoniera sopra il letto (il caso di Marco in camera da letto). Con la plafoniera selezionata, trascinandola
 * da dove sta sopra il letto si sposta la plafoniera, non il letto.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SelectedOverlapDragTest {
    @Test
    fun plafonieraSopraIlLetto() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val lamp = Vec2(250.0, 200.0)
            val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0)).copy(
                fixtures = listOf(Fixture(1, FixtureKind.CeilingLight, point = lamp, elevation = 270.0)),
            )
            val bed = Furniture(1, "letto-prova", center = Vec2(230.0, 220.0), width = 160.0, depth = 200.0, height = 50.0)
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room), furniture = listOf(bed))))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var store: ViewModelStore? = null
            var t = 0L
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                fun frames(n: Int = 6) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(10) }
                fun vm() = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.single()
                frames(20)
                // La plafoniera scelta dall'elenco degli oggetti sovrapposti.
                vm().onTap(DragTarget.Fixture(1, 1), lamp)
                frames()
                assertEquals(Selection.Fixture(1, 1), vm().state.value.selection)
                val cam = vm().state.value.camera
                val start = Offset(cam.toScreenX(lamp.x) + CANVAS_X, cam.toScreenY(lamp.y) + CANVAS_Y)
                scene.sendPointerEvent(PointerEventType.Move, start)
                scene.sendPointerEvent(PointerEventType.Press, start)
                for (k in 1..10) {
                    t += 16_000_000L
                    scene.sendPointerEvent(PointerEventType.Move, start + Offset(8f * k, 0f))
                    scene.render(t)
                }
                scene.sendPointerEvent(PointerEventType.Release, start + Offset(80f, 0f))
                frames()
                val plan = vm().state.value.plan
                assertEquals(bed.center, plan.furniture.single().center, "il letto non si deve muovere")
                val moved = plan.room(1)!!.fixture(1)!!.point
                assertTrue(moved.x - lamp.x > 80 / cam.scale * 0.8, "la plafoniera si sposta: da $lamp a $moved")
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private companion object {
        /** Dove comincia la pianta nella finestra (sotto la barra in alto), come in VideoStanzeTest. */
        const val CANVAS_X = 0.5f
        const val CANVAS_Y = 73f
    }
}
