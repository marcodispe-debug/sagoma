package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/** L'app vera come sul computer: clic su una stanza, poi clic col tasto destro sulla pianta: non resta selezionato niente. */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class RightClickDeselectTest {
    private fun ImageComposeScene.ctrl(k: Key) {
        sendKeyEvent(KeyEvent(Key.CtrlLeft, KeyEventType.KeyDown, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(k, KeyEventType.KeyDown, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(k, KeyEventType.KeyUp, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(Key.CtrlLeft, KeyEventType.KeyUp))
    }

    @Test
    fun tastoDestroDeseleziona() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var t = 0L
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels { SagomaApp(platform) } }
                fun frames(n: Int = 10) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(20) }
                frames(20)
                val c = Offset(650f, 450f)
                scene.sendPointerEvent(PointerEventType.Move, c)
                scene.sendPointerEvent(PointerEventType.Press, c)
                scene.sendPointerEvent(PointerEventType.Release, c)
                frames()
                scene.ctrl(Key.C)
                frames()
                assertTrue(platform.message?.startsWith("Copiato") == true, "stanza selezionata: ${platform.message}")
                // Clic destro sulla stanza.
                val right = PointerButtons(isSecondaryPressed = true)
                scene.sendPointerEvent(PointerEventType.Press, c, buttons = right, button = PointerButton.Secondary)
                scene.sendPointerEvent(PointerEventType.Release, c, buttons = PointerButtons(), button = PointerButton.Secondary)
                frames()
                platform.message = null
                scene.ctrl(Key.C)
                frames()
                assertTrue(platform.message?.startsWith("Copiato") != true, "dopo il clic destro non c'è niente da copiare: ${platform.message}")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
