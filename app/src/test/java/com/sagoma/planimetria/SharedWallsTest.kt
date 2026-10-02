package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Snapping
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedWallsTest {

    private fun room(id: Long, w: Double, h: Double, at: Vec2) =
        Room(id, "R$id", RoomType.Altro, RoomFactory.rectangle(w, h).map { it + at })

    // Stanza 1: 0..300; stanza 2: 300..600 → il muro destro della 1 (indice 1) e il sinistro della 2 (indice 3) coincidono.
    private val plan = FloorPlan(listOf(room(1, 300.0, 400.0, Vec2.Zero), room(2, 300.0, 400.0, Vec2(300.0, 0.0))))

    @Test
    fun `l'altezza di un muro in comune vale per entrambe le stanze`() {
        val p = Dimensions.withWallHeight(plan, 1, 1, 220.0)
        assertEquals(220.0, p.room(1)!!.wallHeight(1), 1e-9)
        assertEquals(220.0, p.room(2)!!.wallHeight(3), 1e-9)
        // Gli altri muri restano all'altezza soffitto.
        assertEquals(270.0, p.room(2)!!.wallHeight(1), 1e-9)
        // E ripristinare lo standard vale per entrambe.
        val back = Dimensions.withWallHeight(p, 2, 3, null)
        assertTrue(back.room(1)!!.wallHeights.isEmpty() && back.room(2)!!.wallHeights.isEmpty())
    }

    @Test
    fun `un muro non in comune cambia solo nella sua stanza`() {
        val p = Dimensions.withWallHeight(plan, 1, 0, 220.0)
        assertEquals(220.0, p.room(1)!!.wallHeight(0), 1e-9)
        assertTrue(p.room(2)!!.wallHeights.isEmpty())
    }

    @Test
    fun `muro trascinato vicino a un muro parallelo si sovrappone esattamente`() {
        // Stanza 4 (0..260) e stanza 3 (304..500): trascino il muro destro della 4 di 30 cm verso destra.
        val quattro = room(4, 260.0, 400.0, Vec2.Zero)
        val tre = room(3, 196.0, 400.0, Vec2(304.0, 0.0))
        val moved = Snapping.moveWallSnapped(quattro.points, 1, Vec2(30.0, 7.0), Snapping.wallsOf(tre))
        assertEquals(304.0, moved[1].x, 1e-9)
        assertEquals(304.0, moved[2].x, 1e-9)
        // Lontano (oltre 25 cm) resta dove lo si porta.
        val free = Snapping.moveWallSnapped(quattro.points, 1, Vec2(5.0, 0.0), Snapping.wallsOf(tre))
        assertEquals(265.0, free[1].x, 1e-9)
    }

    @Test
    fun `nessun aggancio con muri paralleli ma non affiancati`() {
        val quattro = room(4, 260.0, 400.0, Vec2.Zero)
        val lontana = room(9, 200.0, 300.0, Vec2(270.0, 600.0)) // più in basso: nessuna sovrapposizione verticale
        val moved = Snapping.moveWallSnapped(quattro.points, 1, Vec2(5.0, 0.0), Snapping.wallsOf(lontana))
        assertEquals(265.0, moved[1].x, 1e-9)
    }

    @Test
    fun `spostando una stanza il suo muro si sovrappone a quello vicino anche lontano dagli angoli`() {
        val fissa = room(1, 400.0, 400.0, Vec2.Zero)
        val mobile = room(2, 200.0, 200.0, Vec2(600.0, 600.0))
        // Angolo in alto a sinistra portato a (412, 100): 12 cm dal muro destro della fissa (x = 400),
        // ma a ~100 cm dagli angoli della fissa, quindi l'aggancio tra angoli non interviene.
        val delta = Snapping.snapRoomTranslation(mobile, listOf(fissa), Vec2(412.0 - 600.0, 100.0 - 600.0))
        assertEquals(400.0, 600.0 + delta.x, 1e-9) // muro sinistro della mobile sul muro destro della fissa
        assertEquals(100.0, 600.0 + delta.y, 1e-9) // in verticale resta dove la si porta
    }

    @Test
    fun `spostando una stanza vicino a un angolo i muri si allineano su entrambi gli assi`() {
        val fissa = room(1, 400.0, 400.0, Vec2.Zero)
        val grande = room(2, 300.0, 500.0, Vec2(1000.0, 1000.0))
        // Stanza più alta della fissa: la porto con l'angolo in alto a sinistra a (418, -20).
        // Nessun suo angolo è entro 25 cm da un angolo della fissa ((418,-20)-(400,0) = 26,9 cm),
        // ma il muro sinistro è a 18 cm dal muro destro della fissa e quello superiore a 20 cm dal superiore.
        val delta = Snapping.snapRoomTranslation(grande, listOf(fissa), Vec2(418.0 - 1000.0, -20.0 - 1000.0))
        assertEquals(400.0, 1000.0 + delta.x, 1e-9)
        assertEquals(0.0, 1000.0 + delta.y, 1e-9)
    }
}
