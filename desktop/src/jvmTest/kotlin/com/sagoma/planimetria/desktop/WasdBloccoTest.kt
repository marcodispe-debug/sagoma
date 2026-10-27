package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
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
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.SagomaApp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * L'app vera come sul computer: WASD non si deve bloccare (Marco: «a volte wasd delle viste si blocca»). Si bloccava quando
 * la tastiera restava su niente, dopo un pulsante che sparisce («Chiudi» di un pannello, la ✕ dell'ora nel 3D) o dopo Invio
 * nel nome della stanza; e un W lasciato con Ctrl giù restava «tenuto». Qui i tasti che nessuno usa vanno a
 * `platform.unhandledKeys`, come fa la finestra vera (`Window(onKeyEvent)` in Main.kt).
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class WasdBloccoTest {
    private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

    @Test
    fun wasdNonSiBlocca() {
        val dir = Files.createTempDirectory("sagoma-prova").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(room))))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE) }
            val platform = DesktopPlatform(projects = repo, tips = tips, assets = null)
            var store: ViewModelStore? = null
            var t = 0L
            ImageComposeScene(1400, 900, Density(1f)).use { scene ->
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                fun frames(n: Int = 6) = repeat(n) { t += 50_000_000L; scene.render(t); Thread.sleep(10) }
                fun vm() = store!!.keys().mapNotNull { store!![it] as? EditorViewModel }.single()
                fun nodes() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
                fun label(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: ""
                // Come la finestra: prima l'app, poi chi riceve i tasti che nessuno ha usato.
                fun key(e: KeyEvent) = scene.sendKeyEvent(e) || platform.unhandledKeys?.invoke(e) == true
                fun press(k: Key) = key(KeyEvent(k, KeyEventType.KeyDown))
                fun release(k: Key) = key(KeyEvent(k, KeyEventType.KeyUp))
                fun click(at: Offset) {
                    scene.sendPointerEvent(PointerEventType.Move, at)
                    scene.sendPointerEvent(PointerEventType.Press, at)
                    scene.sendPointerEvent(PointerEventType.Release, at)
                    frames()
                }
                fun click(text: String) = click(nodes().first { label(it).contains(text) }.boundsInWindow.center)
                /** W tenuto un po': la pianta (2D) scorre; nel 3D il tasto arriva alla vista. */
                fun wMoves(what: String) {
                    val before = vm().state.value.camera
                    assertTrue(press(Key.W), "$what: W deve arrivare all'editor")
                    frames(4)
                    assertTrue(release(Key.W), "$what: anche W lasciato")
                    frames(2)
                    if (!vm().state.value.view3d) assertNotEquals(before, vm().state.value.camera, "$what: la pianta deve scorrere")
                    // La tastiera è tornata all'editor: il tasto dopo ci arriva da solo, senza passare dalla finestra.
                    assertTrue(scene.sendKeyEvent(KeyEvent(Key.W, KeyEventType.KeyDown)), "$what: la tastiera torna all'editor")
                    scene.sendKeyEvent(KeyEvent(Key.W, KeyEventType.KeyUp))
                    frames(2)
                }
                frames(20)
                click(Offset(650f, 450f))
                wMoves("dopo un clic sulla pianta")

                // «Chiudi» del pannello «Piantina e disegno»: il pulsante sparisce e la tastiera resta su niente.
                click("Piantina e disegno")
                click("Chiudi")
                wMoves("dopo «Chiudi» di Piantina e disegno")

                // Invio nel nome della stanza: il campo lascia la tastiera.
                vm().showRoomInfo(1)
                vm().setInfoExpanded(true)
                frames(10)
                click(nodes().first { it.config.contains(SemanticsProperties.EditableText) }.boundsInWindow.center)
                press(Key.Enter)
                release(Key.Enter)
                frames()
                wMoves("dopo Invio nel nome della stanza")

                // W lasciato con Ctrl giù (per esempio durante un Ctrl+Z): la pianta si ferma lo stesso.
                press(Key.W)
                frames(2)
                key(KeyEvent(Key.CtrlLeft, KeyEventType.KeyDown, isCtrlPressed = true))
                key(KeyEvent(Key.W, KeyEventType.KeyUp, isCtrlPressed = true))
                key(KeyEvent(Key.CtrlLeft, KeyEventType.KeyUp))
                frames(2)
                val stopped = vm().state.value.camera
                frames(6)
                assertEquals(stopped, vm().state.value.camera, "W lasciato con Ctrl giù: la pianta non deve continuare da sola")

                // 3D: la ✕ del pannello dell'ora sparisce dopo il clic.
                click("Vista 3D")
                frames(10)
                wMoves("3D appena aperto")
                click("11:00")
                click("✕")
                wMoves("3D dopo la ✕ del pannello dell'ora")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
