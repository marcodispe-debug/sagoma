package com.sagoma.planimetria

import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.editor.TipQueue
import com.sagoma.planimetria.editor.Tutorial
import com.sagoma.planimetria.editor.TutorialEvent
import com.sagoma.planimetria.editor.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class TutorialTest {

    private fun startedAtWalls() = Tutorial().apply {
        start()
        next() // OpenInfo
        on(TutorialEvent.InfoOpened)
    }

    @Test
    fun `il primo passo chiede larghezza e profondita`() {
        val t = startedAtWalls()
        assertEquals(TutorialStep.WallLengths, t.step)
        assertFalse(t.on(TutorialEvent.WallLength(1, 0, 4)))
        assertEquals("Lati cambiati: 1 di 2 · ora un lato nell'altra direzione", t.ui!!.detail)
        // Lato opposto: si era già adeguato da solo, non basta.
        assertFalse(t.on(TutorialEvent.WallLength(1, 2, 4)))
        // Lato nell'altra direzione: passo completato.
        assertTrue(t.on(TutorialEvent.WallLength(1, 1, 4)))
        assertEquals(TutorialStep.ChangeType, t.step)
    }

    @Test
    fun `ogni passo si completa solo con la sua azione`() {
        val t = startedAtWalls()
        t.next() // ChangeType
        assertFalse(t.on(TutorialEvent.CornerDragged))
        assertTrue(t.on(TutorialEvent.RoomTypeChanged))
        assertEquals(TutorialStep.MoveWall, t.step)
        val sequence = listOf(
            TutorialEvent.WallDragged, TutorialEvent.CornerDragged,
            TutorialEvent.OpeningAdded, TutorialEvent.OpeningDragged, TutorialEvent.FixtureAdded,
            TutorialEvent.RoomAdded, TutorialEvent.RoomMoved, TutorialEvent.Undo, TutorialEvent.RulerAdded,
        )
        for (e in sequence) assertTrue("$e", t.on(e))
        assertEquals(TutorialStep.Done, t.step)
        t.next()
        assertNull(t.step)
    }

    @Test
    fun `tutorial fatto resta ricordato insieme ai suggerimenti`() {
        val dir = Files.createTempDirectory("tut").toFile()
        val q = TipQueue(FileTipStore(dir))
        assertFalse(q.tutorialDone)
        q.tutorialDone = true
        q.skipAll()
        val again = TipQueue(FileTipStore(dir))
        assertTrue(again.tutorialDone)
        again.reset() // rivedere i suggerimenti non fa ripartire il tutorial
        assertTrue(TipQueue(FileTipStore(dir)).tutorialDone)
    }
}
