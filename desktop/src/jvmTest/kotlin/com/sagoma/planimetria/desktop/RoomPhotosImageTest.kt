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
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomPhoto
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * La scheda Foto della stanza nell'app vera, sul computer: miniature con le note e una foto aperta in grande.
 * Immagini in build/foto-*.png. Tocchi: la stanza, Info, la scheda Foto, la prima miniatura.
 */
@OptIn(ExperimentalComposeUiApi::class)
class RoomPhotosImageTest {
    /** Finta foto di una parete: intonaco, battiscopa, una presa e un termosifone. */
    private fun wallPhoto(w: Int, h: Int, wall: Int): ByteArray {
        val s = Surface.makeRasterN32Premul(w, h)
        val c = s.canvas
        c.clear(wall)
        c.drawRect(Rect.makeXYWH(0f, h * 0.88f, w.toFloat(), h * 0.12f), Paint().apply { color = Color.makeRGB(150, 120, 90) })
        c.drawRect(Rect.makeXYWH(w * 0.15f, h * 0.70f, w * 0.06f, h * 0.08f), Paint().apply { color = Color.WHITE })
        c.drawRect(Rect.makeXYWH(w * 0.55f, h * 0.50f, w * 0.30f, h * 0.30f), Paint().apply { color = Color.makeRGB(235, 235, 235) })
        for (k in 0 until 8) c.drawRect(Rect.makeXYWH(w * (0.56f + k * 0.036f), h * 0.51f, w * 0.02f, h * 0.28f), Paint().apply { color = Color.makeRGB(200, 200, 200) })
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 85)!!.bytes
    }

    @Test
    fun computer() = shots("foto", 1400, 900, "245,30;1311,803;1340,328;1120,550")

    /** Telefono in verticale (393 × 830 dp): le cinque schede devono starci tutte. */
    @Test
    fun telefono() = shots("foto-telefono", 393, 830, System.getenv("FOTO_TOCCHI") ?: "200,30;304,737;334,620")

    private fun shots(prefix: String, width: Int, height: Int, tocchi: String) {
        val dir = Files.createTempDirectory("sagoma-foto").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val walls = listOf(Color.makeRGB(230, 220, 200), Color.makeRGB(205, 220, 230), Color.makeRGB(220, 230, 205))
            val notes = listOf("Parete nord: presa a 30 cm da terra", "", "Termosifone sotto la finestra")
            val photos = walls.mapIndexed { i, wall ->
                val photo = RoomPhoto("foto-$i.jpg", "foto-$i-mini.jpg", notes[i], taken = i.toLong())
                repo.writeFile(p.id, photo.file, wallPhoto(1600, 1200, wall))
                repo.writeFile(p.id, photo.thumb, wallPhoto(360, 270, wall))
                photo
            }
            val room = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(400.0, 300.0)).copy(photos = photos)
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE) }
            repo.markOpened(p.id)

            val taps = tocchi
                .split(';').map { it.split(',').let { (x, y) -> Offset(x.toFloat(), y.toFloat()) } }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var t = 0L
            ImageComposeScene(width, height, Density(1f)).use { scene ->
                scene.setContent { freshViewModels { SagomaApp(platform) } }
                fun settle() = repeat(20) { t += 50_000_000L; scene.render(t); Thread.sleep(20) }
                settle()
                taps.forEachIndexed { i, tap ->
                    scene.sendPointerEvent(PointerEventType.Move, tap)
                    scene.sendPointerEvent(PointerEventType.Press, tap)
                    scene.sendPointerEvent(PointerEventType.Release, tap)
                    settle()
                    t += 50_000_000L
                    File("build/$prefix-${i + 1}.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
            assertTrue(File("build/$prefix-${taps.size}.png").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
