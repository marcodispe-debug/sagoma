package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/** Distanze degli oggetti dai muri della stanza («📏 Distanze dai muri»). */
class WallDistancesTest {
    // Stanza 400 × 300 in mezzeria con muri da 20: dentro va da 10 a 390 e da 10 a 290.
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).copy(
        wallThickness = 20.0,
        openings = listOf(Opening.default(1, OpeningKind.Window1, 0, 100.0)),
    )

    @Test
    fun `colonna - quattro distanze dal bordo`() {
        val plan = FloorPlan(listOf(room), columns = listOf(Column(1, center = Vec2(100.0, 200.0), width = 30.0, depth = 30.0)))
        val d = WallDistances.of(plan, MeasureTarget.Column(1), 300.0).associate { it.side to it.distance }
        assertEquals(75.0, d.getValue(WallSide.Left), 1e-6)
        assertEquals(275.0, d.getValue(WallSide.Right), 1e-6)
        assertEquals(175.0, d.getValue(WallSide.Up), 1e-6)
        assertEquals(75.0, d.getValue(WallSide.Down), 1e-6)
    }

    @Test
    fun `colonna fuori dalle stanze - nessuna distanza`() {
        val plan = FloorPlan(listOf(room), columns = listOf(Column(1, center = Vec2(900.0, 900.0))))
        assertEquals(emptyList(), WallDistances.of(plan, MeasureTarget.Column(1), 300.0))
    }

    @Test
    fun `finestra sul muro in alto - sinistra e destra come la si vede da dentro`() {
        val d = WallDistances.of(FloorPlan(listOf(room)), MeasureTarget.Opening(1, 1), 300.0).associateBy { it.side }
        // Muro in alto visto da dentro: si guarda in su, la sinistra è la sinistra dello schermo.
        assertEquals(50.0, d.getValue(WallSide.Left).distance, 1e-6)
        assertEquals(250.0, d.getValue(WallSide.Right).distance, 1e-6)
        assertEquals(-1.0, d.getValue(WallSide.Left).toward.x, 1e-9)
        assertEquals(0.0, d.getValue(WallSide.Left).toward.y, 1e-9)
    }
}
