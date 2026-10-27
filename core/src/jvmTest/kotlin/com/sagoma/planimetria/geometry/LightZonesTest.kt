package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La luce di una stanza a L resta nei suoi due rettangoli: non entra nell'angolo della stanza accanto che il riquadro copre. */
class LightZonesTest {
    // L'ingresso a L di un progetto di prova: il riquadro (x -656..-206, y 83..371) copre un angolo del soggiorno.
    private val hall = listOf(Vec2(-656.0, 213.0), Vec2(-346.0, 213.0), Vec2(-346.0, 83.0), Vec2(-206.0, 83.0), Vec2(-206.0, 371.0), Vec2(-656.0, 371.0))

    private fun List<Bounds>.has(p: Vec2) = any { p.x in it.minX..it.maxX && p.y in it.minY..it.maxY }

    @Test
    fun `stanza a L in due rettangoli, senza l'angolo del soggiorno`() {
        val z = LightZones.of(hall)
        assertEquals(2, z.size)
        assertTrue(z.has(Vec2(-300.0, 150.0)) && z.has(Vec2(-500.0, 300.0)), "tutta la stanza")
        assertTrue(!z.has(Vec2(-500.0, 150.0)), "l'angolo del soggiorno resta fuori")
        assertEquals(Polygon.area(hall), z.sumOf { (it.maxX - it.minX) * (it.maxY - it.minY) }, 1e-6)
    }

    @Test
    fun `rettangolo e stanza storta restano il riquadro`() {
        val rect = RoomFactory.rectangle(400.0, 300.0)
        assertEquals(listOf(Polygon.bounds(rect)), LightZones.of(rect))
        val slanted = listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 200.0), Vec2(300.0, 300.0), Vec2(100.0, 300.0), Vec2(0.0, 200.0))
        assertEquals(listOf(Polygon.bounds(slanted)), LightZones.of(slanted))
    }

    @Test
    fun `nella scena la striscia LED dell'ingresso a L ha i rettangoli dell'ingresso`() {
        val plan = com.sagoma.planimetria.model.FloorPlan(listOf(
            com.sagoma.planimetria.model.Room(5, "Ingresso", com.sagoma.planimetria.model.RoomType.Altro, hall,
                fixtures = listOf(com.sagoma.planimetria.model.Fixture(1, com.sagoma.planimetria.model.FixtureKind.CeilingLight, point = Vec2(-300.0, 250.0)))),
        ))
        val light = Scene3D.build(plan, focusedRoomId = null, selected = null, ceilings = false).lights.single()
        assertEquals(LightZones.of(hall), light.zones)
    }
}
