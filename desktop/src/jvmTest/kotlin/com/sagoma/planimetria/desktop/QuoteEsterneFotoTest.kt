package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.PlanExport
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Foto delle quote esterne a catena su una pianta come quella di riferimento di Marco (1145 × 1035, muri esterni da 45,
 * tramezzi da 15): l'app vera (a due zoom) e l'immagine esportata. Parte solo con SAGOMA_FOTO_QUOTE=true; immagini in
 * build/quote/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class QuoteEsterneFotoTest {
    /** Stanza con l'interno [x0, x1] × [y0, y1]: i muri esterni (`ext`: alto, destra, basso, sinistra) da 45, gli altri da 15. */
    private fun room(id: Long, name: String, type: RoomType, x0: Double, y0: Double, x1: Double, y1: Double, ext: String, vararg openings: Opening): Room {
        val t = ext.map { if (it == '1') 45.0 else 15.0 }
        val pts = listOf(
            Vec2(x0 - t[3] / 2, y0 - t[0] / 2), Vec2(x1 + t[1] / 2, y0 - t[0] / 2),
            Vec2(x1 + t[1] / 2, y1 + t[2] / 2), Vec2(x0 - t[3] / 2, y1 + t[2] / 2),
        )
        return Room(id, name, type, pts).copy(
            wallThickness = 15.0,
            wallThicknesses = t.withIndex().filter { it.value != 15.0 }.associate { it.index to it.value },
            openings = openings.toList(),
        )
    }

    private fun win(id: Long, wall: Int, pos: Double, w: Double, h: Double) = Opening.default(id, OpeningKind.Window2, wall, pos).copy(width = w, height = h)
    private fun door(id: Long, wall: Int, pos: Double, inward: Boolean = true) = Opening.default(id, OpeningKind.Door, wall, pos).copy(width = 90.0, height = 210.0, opensInward = inward)

    private val plan = FloorPlan(
        listOf(
            room(1, "Area fitness", RoomType.Altro, 45.0, 45.0, 350.0, 485.0, "1001", win(1, 3, 260.0, 143.0, 160.0)),
            room(2, "Bagno", RoomType.Bagno, 365.0, 45.0, 550.0, 280.0, "1000", win(1, 0, 95.0, 85.0, 120.0)),
            room(3, "Lavanderia", RoomType.Altro, 565.0, 45.0, 1100.0, 335.0, "1100", win(1, 0, 120.0, 85.0, 120.0)),
            room(4, "Bagno", RoomType.Bagno, 565.0, 350.0, 1100.0, 540.0, "0100", win(1, 1, 100.0, 200.0, 170.0)),
            room(5, "Camera matrimoniale", RoomType.Camera, 565.0, 555.0, 1100.0, 990.0, "0110", win(1, 1, 220.0, 200.0, 170.0), win(2, 2, 140.0, 228.0, 155.0)),
            room(6, "Scala", RoomType.Altro, 45.0, 500.0, 350.0, 600.0, "0001"),
            room(7, "Camera doppia", RoomType.Camera, 45.0, 615.0, 550.0, 990.0, "0011", win(1, 2, 300.0, 228.0, 155.0), win(2, 3, 220.0, 143.0, 160.0)),
            room(
                8, "Disimpegno", RoomType.Corridoio, 365.0, 295.0, 550.0, 600.0, "0000",
                door(1, 1, 150.0, inward = false), door(2, 2, 92.0, inward = false), door(3, 3, 100.0, inward = false),
            ),
            Room(9, "Poggiolo", RoomType.Balcone, listOf(Vec2(1145.0, 300.0), Vec2(1265.0, 300.0), Vec2(1265.0, 760.0), Vec2(1145.0, 760.0))),
        ),
    )

    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_QUOTE") != "true") return
        val out = File("build/quote").apply { mkdirs() }
        // Immagine esportata.
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val img = assertNotNull(PlanExport.render(plan, measurer, Density(1f)))
        File(out, "esportata.png").writeBytes(Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        // L'app vera, inquadrata e poi più vicina.
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Quote")
            repo.planStore(p.id).save(Building.single(plan))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var store: ViewModelStore? = null
            var t = 0L
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                fun frames(n: Int = 6) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(10) }
                fun shot(name: String) {
                    frames()
                    t += 50_000_000L
                    File(out, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                frames(20)
                shot("app")
                val vm = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.single()
                vm.zoomBy(2.2f, 700f, 450f)
                shot("app-zoom")
                vm.zoomBy(2.2f, 500f, 300f)
                shot("app-zoom2")
                scene.sendPointerEvent(PointerEventType.Move, androidx.compose.ui.geometry.Offset(5f, 5f))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
