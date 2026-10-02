package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionsTest {

    private fun room(id: Long, w: Double, h: Double, at: Vec2) =
        Room(id, "R$id", RoomType.Altro, RoomFactory.rectangle(w, h).map { it + at })

    @Test
    fun `stanza isolata ha una quota per muro, verso l'esterno`() {
        val dims = Dimensions.of(FloorPlan(listOf(room(1, 600.0, 400.0, Vec2.Zero))))
        assertEquals(4, dims.size)
        val top = dims.first { it.a.y == 0.0 && it.b.y == 0.0 }
        assertEquals(Vec2(0.0, -1.0), top.side)
    }

    @Test
    fun `muro in comune della stessa lunghezza quotato una sola volta`() {
        val plan = FloorPlan(listOf(room(1, 600.0, 400.0, Vec2.Zero), room(2, 500.0, 400.0, Vec2(600.0, 0.0))))
        val dims = Dimensions.of(plan)
        assertEquals(7, dims.size)
        val shared = dims.filter { it.a.x == 600.0 && it.b.x == 600.0 }
        assertEquals(1, shared.size)
        assertTrue("la quota del muro in comune va sul muro", shared.single().onWall)
        // Il valore scritto è la lunghezza interna (400 in mezzeria − 15 di muri).
        assertEquals(385.0, shared.single().length, 1e-6)
        assertEquals(585.0, dims.first { it.a.y == 0.0 && it.b.y == 0.0 && it.b.x <= 600.0 }.length, 1e-6) // 600 − 15
    }

    @Test
    fun `muri allineati che si toccano solo agli estremi restano quotati all'esterno`() {
        // Stanza 2 sotto la 1, sovrapposta di 4 cm: i muri sinistri sono allineati ma non "in comune".
        val plan = FloorPlan(listOf(room(1, 600.0, 451.5, Vec2.Zero), room(2, 600.0, 650.0, Vec2(0.0, 447.5))))
        val left = Dimensions.of(plan).filter { it.a.x == 0.0 && it.b.x == 0.0 }
        assertEquals(2, left.size)
        assertTrue(left.all { !it.onWall && it.side.x == -1.0 }) // verso l'esterno (sinistra)
    }

    @Test
    fun `il campo lunghezza evita anche le aperture della stanza confinante`() {
        // Passaggio al centro del muro superiore della stanza 2, che coincide col muro inferiore della 1.
        val sopra = room(1, 600.0, 400.0, Vec2.Zero)
        val sotto = room(2, 600.0, 400.0, Vec2(0.0, 400.0)).copy(openings = listOf(Opening.default(1, OpeningKind.Passage, 0, 300.0)))
        val plan = FloorPlan(listOf(sopra, sotto))
        // Muro inferiore della stanza 1, percorso da destra (600,400) a sinistra (0,400).
        val p = Dimensions.freeAnchor(plan, sopra.wallStart(2), sopra.wallEnd(2), 60.0)
        assertTrue("il campo non deve stare sopra il passaggio (250..350): x=${p.x}", p.x < 250.0 || p.x > 350.0)
        assertEquals(400.0, p.y, 1e-9)
    }

    @Test
    fun `la quota evita l'apertura al centro del muro`() {
        // Porta da 80 cm al centro del muro superiore (600 cm): 260..340, più 10 cm di margine per lato.
        val r = room(1, 600.0, 400.0, Vec2.Zero).copy(openings = listOf(Opening.default(1, OpeningKind.Door, 0, 300.0)))
        val top = Dimensions.of(FloorPlan(listOf(r))).first { it.a.y == 0.0 && it.b.y == 0.0 }
        assertEquals(listOf(250.0..350.0), top.blocked)
        assertEquals(125.0, top.anchorT(60.0), 1e-9) // centro del primo dei due tratti liberi (0..250)
        // Senza aperture resta al centro.
        val free = Dimensions.of(FloorPlan(listOf(room(1, 600.0, 400.0, Vec2.Zero)))).first { it.a.y == 0.0 && it.b.y == 0.0 }
        assertEquals(300.0, free.anchorT(60.0), 1e-9)
    }

    @Test
    fun `muro in comune di lunghezza diversa quotato verso l'interno di ciascuna stanza`() {
        val plan = FloorPlan(listOf(room(1, 600.0, 400.0, Vec2.Zero), room(2, 300.0, 250.0, Vec2(600.0, 50.0))))
        val shared = Dimensions.of(plan).filter { it.a.x == 600.0 && it.b.x == 600.0 }
        assertEquals(2, shared.size)
        // Muro destro della stanza 1: quota verso sinistra (interno); muro sinistro della 2: verso destra.
        assertEquals(setOf(-1.0, 1.0), shared.map { it.side.x }.toSet())
    }
}
