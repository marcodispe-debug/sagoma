package com.sagoma.planimetria.reading

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stanze lette che si sovrappongono: una cede la parte in comune più un muro divisorio. */
class RoomOverlapsTest {
    private fun rect(x0: Int, y0: Int, x1: Int, y1: Int) =
        listOf(Vec2(x0.toDouble(), y0.toDouble()), Vec2(x1.toDouble(), y0.toDouble()), Vec2(x1.toDouble(), y1.toDouble()), Vec2(x0.toDouble(), y1.toDouble()))

    @Test
    fun cedeLaStanzaPiuGrandeDelloScrittoEDiventaAL() {
        // Soggiorno disegnato 24 m² ma scritto 10; il ripostiglio 3 × 3 rispetta i suoi 9 m² e resta com'è.
        val soggiorno = rect(0, 0, 600, 400)
        val ripostiglio = rect(400, 200, 700, 500)
        val (s, r) = RoomOverlaps.separated(listOf(soggiorno, ripostiglio), listOf(10.0, 9.0), listOf(0.0, 0.0), listOf(false, false))
        assertEquals(ripostiglio, r)
        val expected = listOf(Vec2(0.0, 0.0), Vec2(600.0, 0.0), Vec2(600.0, 190.0), Vec2(390.0, 190.0), Vec2(390.0, 400.0), Vec2(0.0, 400.0))
        assertEquals(expected.toSet(), s.toSet())
        assertEquals(0.0, RoomOverlaps.shared(s, r))
        assertTrue(Polygon.signedArea(s) > 0)
    }

    @Test
    fun senzaMetriQuadriCedonoBalconiEStanzePiuGrandi() {
        val camera = rect(0, 0, 400, 400)
        val balcone = rect(300, 100, 900, 300)
        val (c, b) = RoomOverlaps.separated(listOf(camera, balcone), listOf(null, null), listOf(0.0, 0.0), listOf(false, true))
        assertEquals(camera, c)
        assertEquals(setOf(Vec2(410.0, 100.0), Vec2(900.0, 100.0), Vec2(900.0, 300.0), Vec2(410.0, 300.0)), b.toSet())
        // Tra due stanze interne cede la più grande; quella con i metri quadri giusti vince sempre.
        val disimpegno = rect(350, 0, 500, 400)
        val (c2, d2) = RoomOverlaps.separated(listOf(camera, disimpegno), listOf(null, null), listOf(0.0, 0.0), listOf(false, false))
        assertEquals(disimpegno, d2)
        assertEquals(340.0, c2.maxOf { it.x })
        val (c3, d3) = RoomOverlaps.separated(listOf(camera, disimpegno), listOf(16.0, null), listOf(0.0, 0.0), listOf(false, false))
        assertEquals(camera, c3)
        assertEquals(410.0, d3.minOf { it.x })
    }

    @Test
    fun piccoleImprecisioniEStanzeStorteRestanoComeSono() {
        val a = rect(0, 0, 400, 300)
        val b = rect(398, 0, 700, 300) // 2 cm × 3 m: il muro letto un po' storto, non una stanza dentro l'altra
        assertEquals(listOf(a, b), RoomOverlaps.separated(listOf(a, b), listOf(null, null), listOf(0.0, 0.0), listOf(false, false)))
        val slanted = listOf(Vec2(300.0, 0.0), Vec2(700.0, 50.0), Vec2(700.0, 300.0), Vec2(300.0, 300.0))
        assertEquals(slanted, RoomOverlaps.separated(listOf(a, slanted), listOf(null, null), listOf(0.0, 0.0), listOf(false, false))[1])
    }
}
