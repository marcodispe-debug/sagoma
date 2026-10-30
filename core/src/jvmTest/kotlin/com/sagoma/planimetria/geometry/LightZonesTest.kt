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

    private fun rect(id: Long, name: String, w: Double, h: Double, at: Vec2, removed: Set<Int> = emptySet(), openings: List<com.sagoma.planimetria.model.Opening> = emptyList()) =
        com.sagoma.planimetria.model.Room(id, name, com.sagoma.planimetria.model.RoomType.Altro, RoomFactory.rectangle(w, h).map { it + at },
            removedWalls = removed, openings = openings)

    @Test
    fun `soggiorno, cucina e ingresso senza muri sono un ambiente solo per la luce delle finestre`() {
        // Soggiorno 500×400 con le finestre; cucina 300×400 a destra e ingresso 300×200 sotto la cucina, senza muri in mezzo.
        // Camera 300×200 sotto il soggiorno, chiusa.
        val window = com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Window2, 0, 250.0)
        val living = rect(1, "Soggiorno", 500.0, 400.0, Vec2.Zero, removed = setOf(1), openings = listOf(window))
        val kitchen = rect(2, "Cucina", 300.0, 400.0, Vec2(500.0, 0.0), removed = setOf(2, 3))
        val hall = rect(3, "Ingresso", 300.0, 200.0, Vec2(500.0, 400.0), removed = setOf(0))
        val bedroom = rect(4, "Camera", 500.0, 200.0, Vec2(0.0, 400.0))
        val plan = com.sagoma.planimetria.model.FloorPlan(listOf(living, kitchen, hall, bedroom))
        assertEquals(listOf(setOf(1L, 2L, 3L), setOf(4L)), LightZones.spaces(plan).map { s -> s.map { it.id }.toSet() })
        val scene = Scene3D.build(plan, focusedRoomId = null, selected = null, ceilings = true)
        val w = scene.windows.single()
        // La luce del soggiorno arriva anche in cucina e nell'ingresso, non nella camera chiusa.
        assertTrue(w.zones.has(Vec2(650.0, 200.0)) && w.zones.has(Vec2(650.0, 500.0)) && w.zones.has(Vec2(250.0, 200.0)))
        assertTrue(!w.zones.has(Vec2(250.0, 500.0)), "la camera resta fuori")
        assertTrue(w.zones.size <= LightZones.MAX)
        // Si sparge su pareti, pavimento e soffitto di tutto l'ambiente (senza i muri tolti).
        val floor = 500.0 * 400 + 300.0 * 400 + 300.0 * 200
        val walls = (500.0 + 400 + 500 + 300 + 400 + 200 + 300 + 200) * 270
        assertEquals(2 * floor + walls, w.surface, 1.0)
        // L'esposizione: un solo ambiente per soggiorno, cucina e ingresso.
        val here = scene.rooms.filter { r -> r.zones.has(Vec2(650.0, 500.0)) }
        assertEquals(1, here.size)
        assertEquals(w.zones, here.single().zones)
        // Con il muro tra soggiorno e cucina rimesso, ognuno torna per conto suo.
        val closed = plan.copy(rooms = listOf(living.copy(removedWalls = emptySet()), kitchen.copy(removedWalls = setOf(2)), hall, bedroom))
        val alone = Scene3D.build(closed, focusedRoomId = null, selected = null, ceilings = true).windows.single()
        assertEquals(LightZones.of(living.points), alone.zones)
    }

    @Test
    fun `ambiente con troppi rettangoli`() {
        // Cinque stanze in fila a scalini, tutte aperte: i rettangoli si uniscono fino a quattro senza entrare nella stanza chiusa.
        val steps = (0 until 5).map { k -> rect(k + 1L, "S$k", 200.0, 200.0 + 40 * k, Vec2(200.0 * k, 0.0), removed = setOfNotNull(if (k > 0) 3 else null, if (k < 4) 1 else null)) }
        val merged = LightZones.merged(steps)!!
        assertTrue(merged.size <= LightZones.MAX)
        for (r in steps) assertTrue(merged.has(Polygon.bounds(r.points).center))
        // Una stanza chiusa dove andrebbero uniti: se non si riesce senza entrarci, null (ogni stanza resta da sola).
        val blocker = listOf(rect(9, "Chiusa", 1000.0, 100.0, Vec2(0.0, 205.0)))
        assertEquals(null, LightZones.merged(steps, blocker))
    }
}
