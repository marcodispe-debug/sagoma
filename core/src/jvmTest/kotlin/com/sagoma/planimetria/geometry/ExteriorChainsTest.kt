package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Quote esterne a catena, come nella pianta di riferimento di Marco: muri, stanze, tramezzi e totale su ogni lato. */
class ExteriorChainsTest {
    private fun rect(id: Long, x: Double, y: Double, w: Double, h: Double, type: RoomType = RoomType.Altro) =
        Room(id, "R$id", type, listOf(Vec2(x, y), Vec2(x + w, y), Vec2(x + w, y + h), Vec2(x, y + h)))

    private fun segments(c: DimensionChain) = c.marks.zipWithNext { a, b -> b - a }

    private fun assertSegments(expected: List<Double>, c: DimensionChain) {
        val got = segments(c)
        assertEquals(expected.size, got.size, "segmenti $got")
        expected.zip(got).forEach { (e, g) -> assertEquals(e, g, 1e-6, "segmenti $got") }
    }

    /** Catena del lato che guarda verso `out`. */
    private fun ExteriorDimensions.side(out: Vec2) = chains.single { (it.out dot out) > 0.999 }

    @Test
    fun `due stanze affiancate`() {
        val plan = FloorPlan(listOf(rect(1, 0.0, 0.0, 400.0, 300.0), rect(2, 400.0, 0.0, 300.0, 300.0)))
        val d = ExteriorChains.of(plan)
        assertEquals(4, d.chains.size)
        val top = d.side(Vec2(0.0, -1.0))
        // Muro esterno 15, stanza 385, tramezzo 15, stanza 285, muro esterno 15.
        assertSegments(listOf(15.0, 385.0, 15.0, 285.0, 15.0), top)
        assertEquals(715.0, top.total.endInclusive - top.total.start, 1e-6)
        assertEquals(-7.5, top.at(0.0).y, 1e-6) // la catena parte dal filo esterno
        assertSegments(listOf(15.0, 285.0, 15.0), d.side(Vec2(-1.0, 0.0)))
        // Tutte le misure delle stanze si leggono fuori: dentro non si ripetono.
        for (id in listOf(1L, 2L)) for (i in 0..3) assertTrue((id to i) in d.covered, "muro $i della stanza $id")
    }

    @Test
    fun `il muro eliminato tra due stanze non lascia il suo spessore nella catena`() {
        val plan = FloorPlan(listOf(rect(1, 0.0, 0.0, 400.0, 300.0).copy(removedWalls = setOf(1)), rect(2, 400.0, 0.0, 300.0, 300.0).copy(removedWalls = setOf(3))))
        val d = ExteriorChains.of(plan)
        // Muro esterno, ambiente unico fino all'altro muro esterno, muro esterno: niente 15 in mezzo dove c'era il tramezzo.
        assertSegments(listOf(15.0, 685.0, 15.0), d.side(Vec2(0.0, -1.0)))
        assertSegments(listOf(15.0, 685.0, 15.0), d.side(Vec2(0.0, 1.0)))
        assertEquals(715.0, d.side(Vec2(0.0, -1.0)).total.let { it.endInclusive - it.start }, 1e-6)
        // Le misure delle due stanze lungo la facciata non sono nella catena: si scrivono dentro.
        assertFalse((1L to 0) in d.covered)
        assertFalse((2L to 0) in d.covered)
    }

    @Test
    fun `la stanza in mezzo si quota dentro`() {
        val rooms = (0 until 9).map { k -> rect(k + 1L, (k % 3) * 200.0, (k / 3) * 200.0, 200.0, 200.0) }
        val d = ExteriorChains.of(FloorPlan(rooms))
        assertSegments(listOf(15.0, 185.0, 15.0, 185.0, 15.0, 185.0, 15.0), d.side(Vec2(0.0, -1.0)))
        for (i in 0..3) assertFalse((5L to i) in d.covered, "la stanza centrale non si vede da fuori")
        assertTrue((1L to 0) in d.covered)
    }

    @Test
    fun `il balcone che sporge entra nella catena ma non nel totale`() {
        val plan = FloorPlan(listOf(rect(1, 0.0, 0.0, 400.0, 300.0), rect(2, 407.5, 0.0, 120.0, 300.0, RoomType.Balcone)))
        val top = ExteriorChains.of(plan).side(Vec2(0.0, -1.0))
        assertSegments(listOf(15.0, 385.0, 15.0, 120.0), top)
        assertEquals(415.0, top.total.endInclusive - top.total.start, 1e-6)
    }

    @Test
    fun `stanza a L con il muro rientrato`() {
        val l = Room(
            1, "L", RoomType.Altro,
            listOf(Vec2(0.0, 0.0), Vec2(300.0, 0.0), Vec2(300.0, 200.0), Vec2(600.0, 200.0), Vec2(600.0, 400.0), Vec2(0.0, 400.0)),
        )
        val d = ExteriorChains.of(FloorPlan(listOf(l)))
        // Dall'alto: la parte alta (muro, stanza, muro del rientro) e poi quella rientrata fino al muro a destra.
        val top = d.side(Vec2(0.0, -1.0))
        assertSegments(listOf(15.0, 285.0, 15.0, 285.0, 15.0), top)
        assertEquals(615.0, top.total.endInclusive - top.total.start, 1e-6)
        // Il lato lungo in basso misura 585 dentro e si legge fuori; il lato in alto (285) pure.
        assertTrue((1L to 0) in d.covered)
        assertTrue((1L to 4) in d.covered)
    }

    @Test
    fun `casa girata`() {
        val k = Math.toRadians(30.0)
        fun turn(p: Vec2) = Vec2(p.x * cos(k) - p.y * sin(k), p.x * sin(k) + p.y * cos(k))
        val rooms = listOf(rect(1, 0.0, 0.0, 400.0, 300.0), rect(2, 400.0, 0.0, 300.0, 300.0)).map { it.copy(points = it.points.map(::turn)) }
        val d = ExteriorChains.of(FloorPlan(rooms))
        assertEquals(4, d.chains.size)
        val long = d.chains.filter { abs(it.total.endInclusive - it.total.start - 715.0) < 1e-6 }
        assertEquals(2, long.size)
        long.forEach { assertSegments(listOf(15.0, 385.0, 15.0, 285.0, 15.0), it) }
    }

    @Test
    fun `porte e finestre sui muri esterni stanno fuori`() {
        val plan = FloorPlan(listOf(rect(1, 0.0, 0.0, 400.0, 300.0), rect(2, 400.0, 0.0, 300.0, 300.0)))
        val d = ExteriorChains.of(plan)
        assertTrue(d.outsideAt(1, 0, 200.0)) // muro in alto della prima stanza
        assertFalse(d.outsideAt(1, 1, 150.0)) // muro in comune
        assertFalse(d.outsideAt(2, 3, 150.0))
    }
}
