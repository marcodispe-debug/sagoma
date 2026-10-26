package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.WallCut
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sottotetto: aggiungendo un angolo a metà di una parete tagliata in diagonale, la discesa resta dov'era.
 * Stanza 400 × 300, muro 1 (a destra) basso 100 cm, parete in alto (muro 0) che scende verso di lui negli ultimi 150 cm.
 */
class SplitAtticWallTest {
    private fun attic(towardEnd: Boolean): Room {
        val r = Room(1, "Mansarda", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0), ceilingHeight = 270.0)
        val low = if (towardEnd) 1 else 3
        return r.copy(wallHeights = mapOf(low to 100.0), wallCuts = mapOf(0 to WallCut(towardEnd, 150.0)))
    }

    @Test
    fun `angolo basso alla fine - la discesa passa sulla seconda meta`() {
        val before = attic(towardEnd = true)
        assertEquals(100.0, Ceilings.wallTopAt(before, 0, 400.0), 1e-6)
        val split = before.splitWall(0)
        // Prima metà (0→200): tutta alta. Seconda metà (200→400): alta fino a 50 cm, poi scende a 100 cm in fondo.
        assertEquals(270.0, Ceilings.wallTopAt(split, 0, 0.0), 1e-6)
        assertEquals(270.0, Ceilings.wallTopAt(split, 0, 200.0), 1e-6)
        assertEquals(270.0, Ceilings.wallTopAt(split, 1, 50.0), 1e-6)
        assertEquals(185.0, Ceilings.wallTopAt(split, 1, 125.0), 1e-6)
        assertEquals(100.0, Ceilings.wallTopAt(split, 1, 200.0), 1e-6)
    }

    @Test
    fun `angolo basso all'inizio - la discesa resta sulla prima meta`() {
        val split = attic(towardEnd = false).splitWall(0)
        assertEquals(100.0, Ceilings.wallTopAt(split, 0, 0.0), 1e-6)
        assertEquals(270.0, Ceilings.wallTopAt(split, 0, 150.0), 1e-6)
        assertEquals(270.0, Ceilings.wallTopAt(split, 1, 100.0), 1e-6)
    }

    @Test
    fun `aggiungere e togliere l'angolo riporta la parete com'era`() {
        for (toward in listOf(true, false)) {
            val before = attic(toward)
            val back = before.splitWall(0).removeCorner(1)!!
            assertEquals(before.wallCuts, back.wallCuts, "angolo basso alla fine: $toward")
            for (t in listOf(0.0, 100.0, 200.0, 300.0, 400.0)) assertEquals(Ceilings.wallTopAt(before, 0, t), Ceilings.wallTopAt(back, 0, t), 1e-6)
        }
    }
}
