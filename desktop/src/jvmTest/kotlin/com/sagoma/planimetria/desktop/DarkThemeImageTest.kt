package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
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
import com.sagoma.planimetria.persistence.FileThemeStore
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.persistence.ThemeChoice
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * L'app vera col tema scuro salvato: l'editor si apre scuro (la pianta resta un foglio chiaro) e anche l'elenco dei
 * progetti. Immagini in build/prova-tema-scuro-editor.png, -stanza.png, -info.png (caratteristiche della stanza) e -progetti.png.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DarkThemeImageTest {
    @Test
    fun temaScuroSalvato() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)).copy(
                openings = listOf(Opening.default(1, OpeningKind.Window2, 0, 250.0)),
            )
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            FileThemeStore(dir).save(ThemeChoice.Dark)

            fun shot(name: String, taps: List<Pair<Offset, String>> = emptyList()): Int {
                val platform = DesktopPlatform(projects = repo, tips = tips, theme = FileThemeStore(dir), assets = null)
                var t = 0L
                ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                    scene.setContent { freshViewModels { SagomaApp(platform) } }
                    repeat(20) { t += 50_000_000L; scene.render(t); Thread.sleep(10) }
                    t += 50_000_000L
                    val img = scene.render(t)
                    File("build/$name").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                    for ((tap, tapName) in taps) {
                        scene.sendPointerEvent(PointerEventType.Move, tap)
                        scene.sendPointerEvent(PointerEventType.Press, tap)
                        scene.sendPointerEvent(PointerEventType.Release, tap)
                        repeat(20) { t += 50_000_000L; scene.render(t); Thread.sleep(10) }
                        t += 50_000_000L
                        File("build/$tapName").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                    }
                    return img.peekPixels()!!.getColor(4, 4)
                }
            }

            repo.markOpened(p.id)
            val editorCorner = shot(
                "prova-tema-scuro-editor.png",
                // La scheda della stanza la seleziona; "Info" ne apre le caratteristiche.
                listOf(Offset(245f, 30f) to "prova-tema-scuro-stanza.png", Offset(1311f, 803f) to "prova-tema-scuro-info.png"),
            )
            repo.markOpened(-1)
            val projectsCorner = shot("prova-tema-scuro-progetti.png")
            for (c in listOf(editorCorner, projectsCorner)) {
                val lum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                assertTrue(lum < 3 * 80, "lo sfondo dell'app dovrebbe essere scuro: ${Integer.toHexString(c)}")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
