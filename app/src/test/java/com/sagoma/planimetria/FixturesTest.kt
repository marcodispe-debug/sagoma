package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Test

class FixturesTest {

    private fun room(fixtures: List<Fixture>) =
        Room(1, "S", RoomType.Altro, RoomFactory.rectangle(500.0, 400.0), fixtures = fixtures)

    private fun assertVec(e: Vec2, a: Vec2) {
        assertEquals(e.x, a.x, 1e-9)
        assertEquals(e.y, a.y, 1e-9)
    }

    @Test
    fun `impianto a muro sul filo interno del muro`() {
        // Presa sul muro superiore (y = 0) a 100 cm dall'inizio: sta a 7,5 cm verso l'interno.
        val presa = Fixture.default(1, FixtureKind.Outlet).copy(wallIndex = 0, position = 100.0)
        val (p, n) = Fixtures.wallAnchor(room(listOf(presa)), presa)
        assertVec(Vec2(100.0, 7.5), p)
        assertVec(Vec2(0.0, 1.0), n)
    }

    @Test
    fun `il calorifero resta dentro il muro anche se lo si porta oltre la fine`() {
        val cal = Fixture.default(1, FixtureKind.Radiator).copy(wallIndex = 0, position = 490.0) // largo 80
        val (p, _) = Fixtures.wallAnchor(room(listOf(cal)), cal)
        assertEquals(460.0, p.x, 1e-9) // 500 − 80/2
        // Il suo centro è a metà della profondità, verso l'interno.
        assertVec(Vec2(460.0, 7.5 + Fixtures.RADIATOR_DEPTH / 2), Fixtures.center(room(listOf(cal)), cal))
    }

    @Test
    fun `luci lineari secondo lunghezza e rotazione`() {
        val neon = Fixture.default(1, FixtureKind.Neon).copy(point = Vec2(200.0, 200.0), rotation = 90.0) // lungo 120
        val (a, b) = Fixtures.linearEnds(neon)
        assertVec(Vec2(200.0, 140.0), a)
        assertVec(Vec2(200.0, 260.0), b)
    }

    @Test
    fun `spostando la stanza le luci la seguono, gli impianti a muro restano legati al muro`() {
        val luce = Fixture.default(1, FixtureKind.CeilingLight).copy(point = Vec2(250.0, 200.0))
        val presa = Fixture.default(2, FixtureKind.Outlet).copy(wallIndex = 0, position = 100.0)
        val moved = Fixtures.movedRoom(room(listOf(luce, presa)), Vec2(300.0, -50.0))
        assertVec(Vec2(550.0, 150.0), moved.fixture(1)!!.point)
        assertEquals(100.0, moved.fixture(2)!!.position, 1e-9)
        assertVec(Vec2(400.0, -42.5), Fixtures.wallAnchor(moved, moved.fixture(2)!!).first)
    }

    @Test
    fun `tutti gli impianti si salvano e si ricaricano`() {
        val all = FixtureKind.entries.mapIndexed { i, k ->
            Fixture.default(i + 1L, k).copy(wallIndex = 1, position = 150.0, point = Vec2(100.0 + i, 120.0), rotation = 30.0)
        }
        val plan = FloorPlan(listOf(room(all)))
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
    }
}
