package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.WallCut
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.model.WindowShape
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Computo con le forme vere: le finestre tonde e ad arco non si tolgono dalla pittura come rettangoli, e in mansarda la parete
 * tagliata in diagonale non conta come se fosse alta fino al soffitto. Stanza 500 × 400, muri da 30 cm, alta 270.
 */
class TakeoffShapesTest {
    private fun room(vararg openings: Opening) =
        Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0), wallThickness = 30.0, openings = openings.toList())

    private fun line(r: Room) = Takeoff.of(Building.single(FloorPlan(listOf(r)))).rooms.single()

    @Test
    fun `finestra tonda`() {
        // Oblò largo 80: un cerchio di raggio 40, non un rettangolo 80 × 120.
        val window = Opening.default(1, OpeningKind.Window1, 0, 250.0).copy(windowShape = WindowShape.Round)
        val l = line(room(window))
        assertEquals(PI * 0.4 * 0.4, l.openingsArea, 1e-3)
        assertEquals(line(room()).wallArea - l.openingsArea, l.wallArea, 1e-9)
    }

    @Test
    fun `finestra e varco ad arco`() {
        // Finestra 80 × 120: l'arco sale di 36 (il 30% dell'altezza) con il bordo di un'ellisse.
        val window = Opening.default(1, OpeningKind.Window1, 0, 250.0).copy(windowShape = WindowShape.Arched)
        assertEquals((80 * 84 + PI * 40 * 36 / 2) / 10_000, line(room(window)).openingsArea, 1e-4)
        // Varco 80 × 210 a tutto sesto: mezzo cerchio di raggio 40 sopra 170 cm dritti.
        val passage = Opening.default(1, OpeningKind.Passage, 0, 250.0).copy(style = PassageStyle.Arched)
        assertEquals((80 * 170 + PI * 40 * 40 / 2) / 10_000, line(room(passage)).openingsArea, 1e-4)
    }

    @Test
    fun `parete della mansarda tagliata in diagonale`() {
        // Muro 3 basso (140), muro 0 che sale da 140 a 270 nei primi 250 cm dei suoi 500: in media è alto 237,5.
        val r = room().copy(wallHeights = mapOf(3 to 140.0), wallCuts = mapOf(0 to WallCut(towardEnd = false, start = 250.0)))
        val l = r.interiorLengths()
        val expected = (l[0] * 237.5 + l[1] * 270 + l[2] * 270 + l[3] * 140) / 10_000
        assertEquals(expected, line(r).wallArea, 1e-6)
        // Anche nei totali per finitura.
        assertEquals(expected, Takeoff.of(Building.single(FloorPlan(listOf(r)))).wallFinishes.sumOf { it.quantity }, 1e-6)
    }

    @Test
    fun `finestra sotto la pendenza`() {
        // Finestra da 40 a 160 sul muro che sale: tagliata a 192 (la cima del muro a metà, come nel 3D) e, a sinistra, dove il
        // muro è più basso, solo fino alla cima del muro.
        val window = Opening.default(1, OpeningKind.Window2, 0, 100.0).copy(width = 120.0)
        val r = room(window).copy(wallHeights = mapOf(3 to 140.0), wallCuts = mapOf(0 to WallCut(towardEnd = false, start = 250.0)))
        // Da 40 a 100 la cima del muro (140 + 0,52 t) sotto 192; poi dritto a 192. Davanzale a 90.
        val hole = (50.0 * 100 + 0.26 * 100 * 100) - (50.0 * 40 + 0.26 * 40 * 40) + 60.0 * 102
        assertEquals(hole / 10_000, line(r).openingsArea, 1e-6)
    }

    @Test
    fun `rivestimento fino a metà di un oblò`() {
        // Piastrelle fino a 130: l'oblò (da 90 a 170) è metà sotto e metà sopra.
        val window = Opening.default(1, OpeningKind.Window1, 0, 250.0).copy(windowShape = WindowShape.Round)
        val r = room(window).copy(wallFinish = WallFinish(WallPaint.White.argb, covering = Covering.Tiles, coveringHeight = 130.0))
        val len = r.interiorLengths()[0]
        val parts = Takeoff.wallParts(r, 0, len)
        val half = PI * 40 * 40 / 2
        assertEquals((len * 130 - half) / 10_000, parts[0].second, 1e-3)
        assertEquals((len * 140 - half) / 10_000, parts[1].second, 1e-3)
    }
}
