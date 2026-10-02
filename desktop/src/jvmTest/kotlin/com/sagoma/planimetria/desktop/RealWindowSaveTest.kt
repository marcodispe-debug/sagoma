package com.sagoma.planimetria.desktop

import androidx.compose.ui.awt.ComposeWindow
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
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Salvataggio automatico in una finestra vera (come quella dell'app sul computer), su un progetto di prova:
 * clic su una stanza, Ctrl+D, e il file deve contenere la copia.
 */
class RealWindowSaveTest {
    @Test
    fun salvaNellaFinestraVera() {
        // Apre una finestra vera sullo schermo: solo su richiesta (SAGOMA_FINESTRA=1), e da sola. Insieme alle
        // altre prove l'interfaccia grafica può essere ancora occupata e la prova resta bloccata.
        if (GraphicsEnvironment.isHeadless() || System.getenv("SAGOMA_FINESTRA") != "1") return
        val dir = Files.createTempDirectory("sagoma-finestra").toFile()
        var window: ComposeWindow? = null
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            repo.planStore(p.id).save(Building.single(FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))))))
            repo.markOpened(p.id)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE) }
            val platform = DesktopPlatform(isPro = true, projects = repo, tips = tips, assets = null)
            SwingUtilities.invokeAndWait {
                window = ComposeWindow().apply {
                    setSize(1200, 800)
                    setLocation(40, 40)
                    setContent { SagomaApp(platform) }
                    isVisible = true
                }
            }
            Thread.sleep(3000)
            val w = window!!
            fun target(x: Int, y: Int) = SwingUtilities.getDeepestComponentAt(w.contentPane, x, y) ?: w.contentPane
            // Clic in mezzo alla finestra (dentro la stanza).
            SwingUtilities.invokeAndWait {
                val c = target(600, 420)
                val pt = SwingUtilities.convertPoint(w.contentPane, 600, 420, c)
                val now = System.currentTimeMillis()
                c.dispatchEvent(MouseEvent(c, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK, pt.x, pt.y, 1, false, MouseEvent.BUTTON1))
                c.dispatchEvent(MouseEvent(c, MouseEvent.MOUSE_RELEASED, now + 50, 0, pt.x, pt.y, 1, false, MouseEvent.BUTTON1))
            }
            Thread.sleep(800)
            // Ctrl+D al componente che ha la tastiera.
            SwingUtilities.invokeAndWait {
                val c = w.mostRecentFocusOwner ?: target(600, 420)
                val now = System.currentTimeMillis()
                c.dispatchEvent(KeyEvent(c, KeyEvent.KEY_PRESSED, now, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED))
                c.dispatchEvent(KeyEvent(c, KeyEvent.KEY_PRESSED, now, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_D, KeyEvent.CHAR_UNDEFINED))
                c.dispatchEvent(KeyEvent(c, KeyEvent.KEY_RELEASED, now, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_D, KeyEvent.CHAR_UNDEFINED))
                c.dispatchEvent(KeyEvent(c, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED))
            }
            Thread.sleep(2500)
            val rooms = repo.planStore(p.id).load()!!.floor.plan.rooms
            println("Messaggio: ${platform.message} · stanze salvate: ${rooms.size}")
            assertEquals(2, rooms.size, "la copia non è stata salvata")
        } finally {
            SwingUtilities.invokeAndWait { window?.dispose() }
            dir.deleteRecursively()
        }
    }
}
