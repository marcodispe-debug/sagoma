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
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Laying
import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.model.MaterialLayout
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileThemeStore
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Foto della scheda Finiture con la posa a pezzi (spina di pesce), sul computer e sul telefono, con la finestra alta per
 * vedere tutta la scheda. Non controlla niente: serve a guardarla. Parte solo con SAGOMA_FOTO_POSA=true; immagini in build/posa/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PosaFotoTest {
    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_POSA") != "true") return
        val out = File("build/posa").also { it.mkdirs() }
        File("../catalogo/src/main/assets/materials/materials.json").takeIf { it.isFile }?.let { com.sagoma.planimetria.model.MaterialCatalog.load(it.readText()) }
        val dir = Files.createTempDirectory("sagoma-posa").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Posa")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(515.0, 415.0)).copy(
                floorMaterial = "woodfloor040",
                floorLayout = MaterialLayout(laying = Laying(LayingPattern.SpinaDiPesce, 10.0, 60.0, 0.1, 0xFF4A3520)),
            )
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            repo.markOpened(p.id)
            for ((nome, w, h, d) in listOf(Quad("pc", 1400, 2600, 1f), Quad("tel", 1080, 6000, 2.625f))) {
                val platform = DesktopPlatform(projects = repo, tips = tips, theme = FileThemeStore(dir), assets = File("../catalogo/src/main/assets").takeIf { it.isDirectory })
                var t = 0L
                var store: ViewModelStore? = null
                ImageComposeScene(w, h, Density(d)).use { scene ->
                    fun frames(n: Int) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(5) }
                    scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                    frames(30)
                    val vm = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.first()
                    vm.showRoomInfo(1)
                    vm.setInfoExpanded(true)
                    frames(20)
                    val tab = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                        .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                        .firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty().contains("Finiture") }
                    if (tab != null) {
                        val c = tab.boundsInWindow.center
                        scene.sendPointerEvent(PointerEventType.Move, c)
                        scene.sendPointerEvent(PointerEventType.Press, c)
                        scene.sendPointerEvent(PointerEventType.Release, c)
                        frames(15)
                    }
                    t += 50_000_000L
                    File(out, "scheda-$nome.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private data class Quad(val nome: String, val w: Int, val h: Int, val d: Float)
}
