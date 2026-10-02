package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.SnapEngine
import com.sagoma.planimetria.geometry.SnapKind
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aggancio CAD dei punti trascinati, con le guide da mostrare. */
class SnapEngineTest {
    // Due stanze: A 400×300 nell'origine, B 300×300 accanto (x da 600 a 900).
    private val a = Room(1, "A", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
    private val b = Room(2, "B", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(600.0, 0.0) })
    private val plan = FloorPlan(listOf(a, b), columns = listOf(Column(1, Vec2(500.0, 500.0))))

    @Test
    fun `estremità, centro e punto medio`() {
        val t = SnapEngine.targets(plan, excludeRoom = 1)
        val e = SnapEngine.snap(Vec2(605.0, 294.0), t, 10.0)
        assertEquals(Vec2(600.0, 300.0), e.point)
        assertEquals(SnapKind.Endpoint, e.guides.single().kind)
        assertEquals(SnapKind.Center, SnapEngine.snap(Vec2(503.0, 497.0), t, 10.0).guides.single().kind)
        val m = SnapEngine.snap(Vec2(604.0, 152.0), t, 10.0)
        assertEquals(Vec2(600.0, 150.0), m.point)
        assertEquals(SnapKind.Midpoint, m.guides.single().kind)
    }

    @Test
    fun `disegno dei muri - direzione bloccata, allineamento e chiusura`() {
        val none = SnapEngine.Targets(emptyList(), emptyList(), emptyList())
        // Muro quasi orizzontale: diventa orizzontale, con la stessa lunghezza.
        val h = SnapEngine.snapDrawing(Vec2(400.0, 12.0), Vec2(0.0, 0.0), Vec2(0.0, 0.0), none, listOf(Vec2(0.0, 0.0)), 10.0)
        assertEquals(0.0, h.point.y, 1e-9)
        assertEquals(SnapKind.Horizontal, h.guides.first().kind)
        // Quasi verticale verso il basso, con la fine allineata alla y di un altro angolo.
        val drawn = listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0))
        val v = SnapEngine.snapDrawing(Vec2(405.0, 296.0), Vec2(400.0, 0.0), drawn.first(), SnapEngine.Targets(listOf(Vec2(900.0, 300.0)), emptyList(), emptyList()), drawn, 10.0)
        assertEquals(Vec2(400.0, 300.0), v.point)
        assertTrue(v.guides.any { it.kind == SnapKind.Vertical } && v.guides.any { it.kind == SnapKind.AlignHorizontal })
        // Con almeno 3 angoli, vicino al primo si chiude.
        val three = listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0))
        val c = SnapEngine.snapDrawing(Vec2(4.0, 5.0), three.last(), three.first(), none, three, 10.0)
        assertEquals(SnapKind.Close, c.guides.single().kind)
        assertEquals(Vec2(0.0, 0.0), c.point)
    }

    @Test
    fun `allineamento lontano e angolo retto`() {
        val t = SnapEngine.targets(plan, excludeRoom = 1)
        // A 3 m di distanza ma con la y quasi uguale a quella dell'angolo di B: si allinea.
        val r = SnapEngine.snap(Vec2(300.0, 296.0), t, 10.0)
        assertEquals(300.0, r.point.y, 1e-9)
        assertEquals(300.0, r.point.x, 1e-9)
        assertEquals(SnapKind.AlignHorizontal, r.guides.single().kind)
        // Allineato con entrambi i vicini: angolo retto.
        val sq = SnapEngine.snap(Vec2(396.0, 304.0), SnapEngine.Targets(emptyList(), emptyList(), emptyList()), 10.0, listOf(Vec2(400.0, 0.0), Vec2(0.0, 300.0)))
        assertEquals(Vec2(400.0, 300.0), sq.point)
        assertTrue(sq.guides.all { it.kind == SnapKind.RightAngle })
        // Lontano da tutto: nessun aggancio.
        assertFalse(SnapEngine.snap(Vec2(1500.0, 1500.0), t, 10.0).snapped)
    }
}
