package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * L'app vera come sul computer: clic su una stanza, Ctrl+C e Ctrl+V dalla tastiera. La copia deve comparire
 * e i messaggi devono dire cosa è successo.
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class KeyboardCopyPasteTest {
    private fun ImageComposeScene.press(k: Key) {
        sendKeyEvent(KeyEvent(Key.CtrlLeft, KeyEventType.KeyDown, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(k, KeyEventType.KeyDown, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(k, KeyEventType.KeyUp, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(Key.CtrlLeft, KeyEventType.KeyUp))
    }

    @Test
    fun ctrlCeCtrlV() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            repo.markOpened(p.id)
            // Niente tutorial né suggerimenti: la schermata è quella di tutti i giorni.
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE) }
            val platform = DesktopPlatform(isPro = true, projects = repo, tips = tips, assets = null)
            var t = 0L
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels { SagomaApp(platform) } }
                fun frames(n: Int = 10) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(20) }
                frames(20)
                // Clic in mezzo alla pianta (dentro la stanza): la seleziona.
                val c = Offset(650f, 450f)
                scene.sendPointerEvent(PointerEventType.Move, c)
                scene.sendPointerEvent(PointerEventType.Press, c)
                scene.sendPointerEvent(PointerEventType.Release, c)
                frames()
                scene.press(Key.C)
                frames()
                assertTrue(platform.message?.startsWith("Copiato") == true, "dopo Ctrl+C: ${platform.message}")
                // Mouse spostato a destra e Ctrl+V: la copia compare lì.
                val there = Offset(1100f, 450f)
                scene.sendPointerEvent(PointerEventType.Move, there)
                frames()
                platform.message = null
                scene.press(Key.V)
                frames(40)
                assertEquals(null, platform.message, "dopo Ctrl+V")
            }
            // Salvata (salvataggio automatico): due stanze.
            Thread.sleep(1500)
            val rooms = repo.planStore(p.id).load()!!.floor.plan.rooms
            assertEquals(2, rooms.size, "stanze dopo Ctrl+V")
        } finally {
            dir.deleteRecursively()
        }
    }
}
