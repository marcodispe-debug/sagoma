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
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * L'app vera: «Controlla la pianta» in tempo reale. Una stanza copiata e incollata sopra sé stessa (Ctrl+C, Ctrl+V col mouse
 * poco più in là) fa due stanze sovrapposte: compare l'avviso rosato in alto, e il segnale diventa rosso. Immagini in
 * build/controllo-dal-vivo-prima.png e -dopo.png.
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class ControlloDalVivoTest {
    private fun ImageComposeScene.press(k: Key) {
        sendKeyEvent(KeyEvent(Key.CtrlLeft, KeyEventType.KeyDown, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(k, KeyEventType.KeyDown, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(k, KeyEventType.KeyUp, isCtrlPressed = true))
        sendKeyEvent(KeyEvent(Key.CtrlLeft, KeyEventType.KeyUp))
    }

    /** Punti rosati (sfondo degli avvisi di errore) nella striscia in alto della pianta. */
    private fun pinkTop(img: Image): Int {
        val px = img.peekPixels()!!
        var n = 0
        for (y in 0 until 260 step 2) for (x in 0 until img.width step 2) {
            val c = px.getColor(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r > 220 && r - g in 15..60 && r - b in 15..60) n++
        }
        return n
    }

    @Test
    fun avvisoQuandoNasceUnErrore() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(
                1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0),
                openings = listOf(Opening.default(1, OpeningKind.Door, 0, 250.0).copy(entrance = true), Opening.default(2, OpeningKind.Window2, 2, 250.0)),
            )
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var t = 0L
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels { SagomaApp(platform) } }
                fun frame(): Image { t += 50_000_000L; return scene.render(t).also { Thread.sleep(20) } }
                fun frames(n: Int = 10) = repeat(n) { frame() }
                frames(60)
                val before = frame()
                File("build/controllo-dal-vivo-prima.png").writeBytes(before.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                val pinkBefore = pinkTop(before)
                // Clic sulla stanza, Ctrl+C, mouse un po' più in là, Ctrl+V: la copia finisce quasi sopra l'originale.
                val c = Offset(650f, 450f)
                scene.sendPointerEvent(PointerEventType.Move, c)
                scene.sendPointerEvent(PointerEventType.Press, c)
                scene.sendPointerEvent(PointerEventType.Release, c)
                frames()
                scene.press(Key.C)
                frames()
                scene.sendPointerEvent(PointerEventType.Move, Offset(700f, 470f))
                frames()
                scene.press(Key.V)
                // Il controllo parte mezzo secondo dopo l'ultima modifica.
                var after = frame()
                for (k in 0 until 80) {
                    after = frame()
                    if (pinkTop(after) > pinkBefore + 2000) break
                }
                frames(5)
                after = frame()
                File("build/controllo-dal-vivo-dopo.png").writeBytes(after.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                assertTrue(pinkTop(after) > pinkBefore + 2000, "avviso non comparso: punti rosati ${pinkTop(after)} prima $pinkBefore")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
