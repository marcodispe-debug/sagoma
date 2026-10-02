package com.sagoma.planimetria

import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class TipsTest {

    private val dir = Files.createTempDirectory("tips").toFile()

    @Test
    fun `un suggerimento visto non ricompare, nemmeno al riavvio`() {
        val q = TipQueue(FileTipStore(dir))
        q.request(Tip.Welcome)
        assertEquals(Tip.Welcome, q.current)
        q.dismiss()
        assertNull(q.current)
        q.request(Tip.Welcome)
        assertNull(q.current)
        // Nuovo avvio dell'app: il file ricorda che è già stato visto.
        val again = TipQueue(FileTipStore(dir))
        again.request(Tip.Welcome)
        assertNull(again.current)
        again.request(Tip.Wall)
        assertEquals(Tip.Wall, again.current)
    }

    @Test
    fun `uno alla volta - gli altri aspettano il loro turno`() {
        val q = TipQueue(FileTipStore(dir))
        q.request(Tip.Welcome)
        q.request(Tip.Ruler)
        q.request(Tip.Ruler) // doppione ignorato
        assertEquals(Tip.Welcome, q.current)
        q.dismiss()
        assertEquals(Tip.Ruler, q.current)
        q.dismiss()
        assertNull(q.current)
    }

    @Test
    fun `salta tutti e rivedi`() {
        val q = TipQueue(FileTipStore(dir))
        q.request(Tip.Room)
        q.skipAll()
        for (t in Tip.entries) q.request(t)
        assertNull(q.current)
        q.reset()
        q.request(Tip.Opening)
        assertEquals(Tip.Opening, q.current)
    }
}
