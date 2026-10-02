package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * L'app vera: finestra e calorifero sullo stesso punto del muro. Un clic lì apre la scelta (muro, calorifero,
 * finestra): immagine in build/prova-scelta-sovrapposti.png.
 */
@OptIn(ExperimentalComposeUiApi::class)
class OverlapChooserTest {
    @Test
    fun sceltaTraOggettiSovrapposti() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)).copy(
                openings = listOf(Opening.default(1, OpeningKind.Window2, 0, 250.0)),
                fixtures = listOf(Fixture(1, FixtureKind.Radiator, wallIndex = 0, position = 250.0, length = 100.0, height = 60.0, elevation = 12.0)),
            )
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE) }
            val platform = DesktopPlatform(isPro = true, projects = repo, tips = tips, assets = null)
            var t = 0L
            var found = false
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels { SagomaApp(platform) } }
                fun frames(n: Int = 6) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(10) }
                fun corner(): Int { t += 50_000_000L; val img = scene.render(t); return img.peekPixels()!!.getColor(8, 450) }
                frames(20)
                val before = corner()
                // Si scende lungo il centro della pianta finché un clic non apre la scelta (lo sfondo si scurisce).
                for (y in 60..600 step 3) {
                    val c = Offset(700f, y.toFloat())
                    scene.sendPointerEvent(PointerEventType.Move, c)
                    scene.sendPointerEvent(PointerEventType.Press, c)
                    scene.sendPointerEvent(PointerEventType.Release, c)
                    frames(3)
                    if (corner() != before) {
                        frames(10)
                        t += 50_000_000L
                        val img = scene.render(t)
                        File("build/prova-scelta-sovrapposti.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                        found = true
                        break
                    }
                }
            }
            assertTrue(found, "la scelta tra oggetti sovrapposti non è comparsa")
        } finally {
            dir.deleteRecursively()
        }
    }
}
