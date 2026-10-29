package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Striscia da togliere dal disegno per un muro eliminato: si ferma sulla faccia dei muri che restano, va oltre gli angoli aperti. */
class OpenBandTest {
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 30.0, removedWalls = setOf(1))

    private fun whole(r: Room, i: Int) = WallGap(r.id, i, r.wallStart(i), r.wallEnd(i))

    private fun assertBox(expected: List<Double>, band: List<Vec2>) {
        val got = listOf(band.minOf { it.x }, band.minOf { it.y }, band.maxOf { it.x }, band.maxOf { it.y })
        for ((e, g) in expected.zip(got)) assertEquals(e, g, 1e-9, "$band")
    }

    @Test
    fun `tra le facce interne dei muri accanto`() {
        // Muro a x = 400 da 30: dal filo interno del muro sopra (y = 15) a quello del muro sotto (y = 285).
        assertBox(listOf(385.0, 15.0, 415.0, 285.0), Openings.openBand(living, whole(living, 1), 0.0))
        assertBox(listOf(384.5, 15.0, 415.5, 285.0), Openings.openBand(living, whole(living, 1), 0.5))
        // Un tratto a metà muro (la stanza accanto ha lì il suo muro): tagliato dritto dove finisce.
        assertBox(listOf(385.0, 100.0, 415.0, 285.0), Openings.openBand(living, WallGap(1, 1, Vec2(400.0, 100.0), Vec2(400.0, 300.0)), 0.0))
    }

    @Test
    fun `angolo aperto e muri spessi diversi`() {
        // Anche il muro sotto è eliminato: la striscia va oltre l'angolo (y = 300 + 30), che resta aperto.
        val corner = living.copy(removedWalls = setOf(1, 2))
        assertBox(listOf(385.0, 15.0, 415.0, 330.0), Openings.openBand(corner, whole(corner, 1), 0.0))
        // Muro sopra da 50: si ferma a 25.
        val thick = living.copy(wallThicknesses = mapOf(0 to 50.0))
        assertBox(listOf(385.0, 25.0, 415.0, 285.0), Openings.openBand(thick, whole(thick, 1), 0.0))
    }

    @Test
    fun `angolo storto - il taglio segue la faccia del muro accanto`() {
        // Trapezio: il muro a destra va da (400, 0) a (500, 300), quello sotto torna a sinistra.
        val r = Room(1, "S", RoomType.Soggiorno, listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(500.0, 300.0), Vec2(0.0, 300.0)), wallThickness = 20.0, removedWalls = setOf(1))
        val band = Openings.openBand(r, whole(r, 1), 0.0)
        assertEquals(4, band.size)
        // I due punti all'inizio stanno sulla faccia interna del muro sopra (y = 10), quelli alla fine su quella del muro sotto.
        assertTrue(abs(band[0].y - 10.0) < 1e-9 && abs(band[3].y - 10.0) < 1e-9, "$band")
        assertTrue(abs(band[1].y - 290.0) < 1e-9 && abs(band[2].y - 290.0) < 1e-9, "$band")
        // Girata al contrario (angoli in senso opposto) è la stessa striscia.
        val rev = r.copy(points = r.points.reversed(), removedWalls = setOf(1))
        val i = (0 until rev.wallCount).first { rev.wallStart(it) == Vec2(500.0, 300.0) }
        val other = Openings.openBand(rev.copy(removedWalls = setOf(i)), whole(rev, i), 0.0)
        assertEquals(band.map { it.x }.sorted().map { Math.round(it * 1e6) }, other.map { it.x }.sorted().map { Math.round(it * 1e6) })
    }
}
