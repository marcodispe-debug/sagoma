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
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileThemeStore
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Foto della scheda Finitura di un muro toccato da dentro la stanza e da fuori, sul computer e sul telefono. Non controlla
 * niente: serve a guardarle. Parte solo con SAGOMA_FOTO_INFO=true; immagini in build/lato-muro/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class LatoMuroFotoTest {
    private class Formato(val nome: String, val w: Int, val h: Int, val density: Float)

    private val formati = listOf(Formato("pc", 1400, 900, 1f), Formato("tel", 1080, 2340, 2.625f))

    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_INFO") != "true") return
        val out = File("build/lato-muro").also { it.mkdirs() }
        val dir = Files.createTempDirectory("sagoma-lato").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val sala = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
            val cucina = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 400.0).map { it + Vec2(500.0, 0.0) })
            repo.planStore(p.id).save(Building.single(FloorPlan(rooms = listOf(sala, cucina))))
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            repo.markOpened(p.id)
            // Muro in alto del soggiorno toccato da dentro e da fuori; muro in comune toccato dalla parte della cucina.
            val tocchi = listOf(
                "1-dentro" to (DragTarget.Wall(1, 0) to Vec2(250.0, 4.0)),
                "2-fuori" to (DragTarget.Wall(1, 0) to Vec2(250.0, -4.0)),
                "3-muro-in-comune-lato-cucina" to (DragTarget.Wall(1, 1) to Vec2(504.0, 200.0)),
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
                    fun tap(o: androidx.compose.ui.geometry.Offset) {
                        scene.sendPointerEvent(PointerEventType.Move, o)
                        scene.sendPointerEvent(PointerEventType.Press, o)
                        scene.sendPointerEvent(PointerEventType.Release, o)
                        frames(10)
                    }
                    for ((nome, tocco) in tocchi) {
                        vm.deselectAll()
                        frames(3)
                        vm.onTap(tocco.first, tocco.second)
                        vm.setInfoExpanded(true)
                        frames(20)
                        val finitura = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                            .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                            .firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty().contains("Finitura") }
                        finitura?.let { tap(it.boundsInWindow.center) }
                        t += 50_000_000L
                        File(out, "${f.nome}-$nome.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                    }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
