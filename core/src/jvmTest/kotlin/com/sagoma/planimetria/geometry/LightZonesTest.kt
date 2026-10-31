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
    fun `di sera le lampade dell'ambiente aperto illuminano tutto l'ambiente`() {
        // Come sopra, con il lampadario in soggiorno, la plafoniera in cucina e in camera, e una porta tra soggiorno e camera.
        fun light(id: Long, kind: com.sagoma.planimetria.model.FixtureKind, at: Vec2) = com.sagoma.planimetria.model.Fixture(id, kind, point = at)
        val door = com.sagoma.planimetria.model.Opening.default(2, com.sagoma.planimetria.model.OpeningKind.Door, 2, 250.0)
        val window = com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Window2, 0, 250.0)
        val living = rect(1, "Soggiorno", 500.0, 400.0, Vec2.Zero, removed = setOf(1), openings = listOf(window, door))
            .copy(fixtures = listOf(light(1, com.sagoma.planimetria.model.FixtureKind.Chandelier, Vec2(250.0, 200.0))))
        val kitchen = rect(2, "Cucina", 300.0, 400.0, Vec2(500.0, 0.0), removed = setOf(2, 3))
            .copy(fixtures = listOf(light(2, com.sagoma.planimetria.model.FixtureKind.CeilingLight, Vec2(650.0, 200.0))))
        val hall = rect(3, "Ingresso", 300.0, 200.0, Vec2(500.0, 400.0), removed = setOf(0))
        val bedroom = rect(4, "Camera", 500.0, 200.0, Vec2(0.0, 400.0))
            .copy(fixtures = listOf(light(3, com.sagoma.planimetria.model.FixtureKind.CeilingLight, Vec2(250.0, 500.0))))
        val scene = Scene3D.build(com.sagoma.planimetria.model.FloorPlan(listOf(living, kitchen, hall, bedroom)), focusedRoomId = null, selected = null, ceilings = true)
        val open = scene.windows.single().zones
        val lamps = scene.lights.filter { !it.spill }
        assertEquals(3, lamps.size)
        // Lampadario e plafoniera della cucina: in soggiorno, cucina e ingresso, non in camera.
        for (l in lamps.filter { it.position.z < 400 }) {
            assertEquals(open, l.zones)
            assertTrue(l.room!!.minX <= 0.0 && l.room!!.maxX >= 800.0 && l.room!!.maxY >= 600.0, "riquadro di tutto l'ambiente: ${l.room}")
        }
        assertEquals(null, lamps.single { it.position.z > 400 }.zones)
        // Tra le stanze dell'ambiente niente luce di passaggio (le lampade ci arrivano già): solo dalla porta della camera, nei due versi.
        val (outside, spill) = scene.lights.filter { it.spill }.partition { it.profile == LightProfile.Window }
        assertEquals(2, spill.size)
        // Fuori, davanti alla finestra del soggiorno, esce la luce di tutto l'ambiente.
        assertTrue(outside.single().zones!!.single().maxY < 0.0, "fuori davanti alla finestra: ${outside.single().zones}")
        assertEquals(Polygon.bounds(bedroom.points), spill.single { it.position.z > 400 }.room)
        assertEquals(open, spill.single { it.position.z < 400 }.zones, "dalla camera la luce entra in tutto l'ambiente")
    }

    @Test
    fun `ingresso aperto sul soggiorno con un varco largo ha la stessa luce, di giorno e di sera`() {
        // Come l'appartamento di Marco: cucina 450×400 e ingresso 300×400 sopra il soggiorno 750×550. Tra cucina e soggiorno
        // niente muro, tra ingresso e soggiorno un passaggio senza porta largo 200, tra cucina e ingresso il muro chiuso.
        // Finestra e lampada in soggiorno, una plafoniera in cucina e una nell'ingresso.
        fun light(id: Long, at: Vec2) = com.sagoma.planimetria.model.Fixture(id, com.sagoma.planimetria.model.FixtureKind.CeilingLight, point = at)
        val window = com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Window2, 3, 375.0)
        val livingPoints = listOf(Vec2(0.0, 400.0), Vec2(450.0, 400.0), Vec2(750.0, 400.0), Vec2(750.0, 950.0), Vec2(0.0, 950.0))
        val living = com.sagoma.planimetria.model.Room(1, "Soggiorno", com.sagoma.planimetria.model.RoomType.Soggiorno, livingPoints,
            openings = listOf(window), removedWalls = setOf(0), fixtures = listOf(light(1, Vec2(375.0, 675.0))))
        val kitchen = rect(3, "Cucina", 450.0, 400.0, Vec2.Zero, removed = setOf(2)).copy(fixtures = listOf(light(3, Vec2(225.0, 200.0))))
        fun hall(width: Double) = rect(2, "Ingresso", 300.0, 400.0, Vec2(450.0, 0.0),
            openings = listOf(com.sagoma.planimetria.model.Opening.default(2, com.sagoma.planimetria.model.OpeningKind.Passage, 2, 150.0).copy(width = width)))
            .copy(fixtures = listOf(light(2, Vec2(600.0, 200.0))))
        val plan = com.sagoma.planimetria.model.FloorPlan(listOf(living, hall(200.0), kitchen))
        assertEquals(listOf(setOf(1L, 2L, 3L)), LightZones.spaces(plan).map { s -> s.map { it.id }.toSet() })
        val scene = Scene3D.build(plan, focusedRoomId = null, selected = null, ceilings = true)
        val inHall = Vec2(600.0, 200.0)
        val inLiving = Vec2(375.0, 675.0)
        val inKitchen = Vec2(225.0, 200.0)
        // Di giorno la luce della finestra arriva anche nell'ingresso, con la stessa esposizione.
        assertTrue(scene.windows.single().zones.has(inHall), "luce del giorno nell'ingresso: ${scene.windows.single().zones}")
        assertEquals(1, scene.rooms.size)
        // Di sera ogni lampada illumina tutto l'ambiente (anche cucina e ingresso tra loro: con zone diverse per lampada la
        // luce cambierebbe di colpo tra cucina e soggiorno), e tra le stanze niente luce di passaggio.
        val lamps = scene.lights.filter { !it.spill }
        assertEquals(3, lamps.size)
        for (l in lamps) assertTrue(l.zones!!.has(inHall) && l.zones!!.has(inLiving) && l.zones!!.has(inKitchen), "in tutto l'ambiente: ${l.zones}")
        assertTrue(scene.lights.none { it.spill && it.profile != LightProfile.Window }, "nessuna luce di passaggio tra le stanze aperte")
        // Con un varco stretto (80 su 300) l'ingresso resta per conto suo: la luce passa dal varco come da una porta.
        val narrow = com.sagoma.planimetria.model.FloorPlan(listOf(living, hall(80.0), kitchen))
        assertEquals(2, LightZones.spaces(narrow).size)
        val apart = Scene3D.build(narrow, focusedRoomId = null, selected = null, ceilings = true)
        assertTrue(!apart.windows.single().zones.has(inHall))
        assertEquals(2, apart.lights.count { it.spill && it.profile == LightProfile.Wall })
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
