package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Distances
import com.sagoma.planimetria.geometry.MeasureTarget
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

class DistancesTest {

    // Stanza 600 × 400 (mezzerie): muro 0 in alto, 1 a destra, 2 in basso, 3 a sinistra; muri spessi 15.
    private val radiator = Fixture.default(1, FixtureKind.Radiator).copy(wallIndex = 3, position = 200.0) // largo 80, sporge 10
    private val outlet = Fixture.default(2, FixtureKind.Outlet).copy(wallIndex = 0, position = 100.0)
    private val window = Opening.default(1, OpeningKind.Window1, 1, 200.0)
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 400.0), openings = listOf(window), fixtures = listOf(radiator, outlet))
    private val plan = FloorPlan(listOf(room), columns = listOf(Column(1, Vec2(300.0, 200.0)), Column(2, Vec2(300.0, 300.0), ColumnShape.Round, width = 40.0)))

    private fun d(a: MeasureTarget, b: MeasureTarget) = Distances.between(plan, a, b, 300.0)!!

    @Test
    fun `calorifero e muro di fronte - distanza tra la sporgenza del calorifero e la faccia del muro`() {
        // Faccia interna del muro di destra a x = 592,5; il calorifero sporge fino a x = 7,5 + 10.
        val r = d(MeasureTarget.Fixture(1, 1), MeasureTarget.Wall(1, 1))
        assertEquals(592.5 - 17.5, r.distance, 1e-6)
        // Con il muro su cui è appeso si toccano.
        assertEquals(0.0, d(MeasureTarget.Fixture(1, 1), MeasureTarget.Wall(1, 3)).distance, 1e-6)
    }

    @Test
    fun `colonne, prese e finestre`() {
        // Colonna 30 × 30 al centro: dalla faccia del muro di sinistra (7,5) al suo lato (285).
        assertEquals(285.0 - 7.5, d(MeasureTarget.Column(1), MeasureTarget.Wall(1, 3)).distance, 1e-6)
        // Tra le due colonne: dal lato inferiore della quadrata (215) al bordo della rotonda (280).
        assertEquals(65.0, d(MeasureTarget.Column(1), MeasureTarget.Column(2)).distance, 0.5)
        // Presa sul muro in alto (punto sul filo interno, y = 7,5) e colonna quadrata (lato superiore y = 185).
        assertEquals(kotlin.math.hypot(285.0 - 100.0, 185.0 - 7.5), d(MeasureTarget.Fixture(1, 2), MeasureTarget.Column(1)).distance, 1e-6)
        // Finestra sul muro di destra (spessore del muro) e colonna: dal lato destro della colonna (315) a 592,5.
        assertEquals(592.5 - 315.0, d(MeasureTarget.Opening(1, 1), MeasureTarget.Column(1)).distance, 1e-6)
    }

    @Test
    fun `punti più vicini tra segmenti`() {
        val (p, q) = Distances.closest(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(5.0, 3.0), Vec2(5.0, 8.0))
        assertEquals(Vec2(5.0, 0.0), p)
        assertEquals(Vec2(5.0, 3.0), q)
        val (a, b) = Distances.closest(Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(3.0, 4.0), Vec2(3.0, 4.0))
        assertEquals(5.0, a.distanceTo(b), 1e-9)
    }

    @Test
    fun `trave quasi orizzontale o verticale si raddrizza, obliqua resta com'è`() {
        val o = Vec2(0.0, 0.0)
        // 4° dall'orizzontale: diventa orizzontale, stessa lunghezza.
        val p = Vec2(300.0 * kotlin.math.cos(Math.toRadians(4.0)), 300.0 * kotlin.math.sin(Math.toRadians(4.0)))
        val s = Structure.snapAngle(p, o)
        assertEquals(300.0, s.x, 1e-6)
        assertEquals(0.0, s.y, 1e-6)
        // Quasi verticale verso l'alto.
        val v = Structure.snapAngle(Vec2(10.0, -200.0), o)
        assertEquals(0.0, v.x, 1e-6)
        assertEquals(-Vec2(10.0, -200.0).length, v.y, 1e-6)
        // A 30° resta obliqua.
        val q = Vec2(200.0, 115.0)
        assertEquals(q, Structure.snapAngle(q, o))
    }
}
