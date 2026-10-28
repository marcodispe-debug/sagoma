package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Superficie lorda (pavimento e muri, contati una volta), muri esterni oltre 50 cm e superficie commerciale. */
class FloorAreasTest {
    private fun rect(id: Long, w: Double, h: Double, at: Vec2 = Vec2.Zero, type: RoomType = RoomType.Camera, t: Double = 15.0) =
        Room(id, "S$id", type, RoomFactory.rectangle(w, h).map { it + at }, wallThickness = t)

    private val a = rect(1, 400.0, 300.0)
    private val b = rect(2, 300.0, 300.0, Vec2(400.0, 0.0))

    @Test
    fun `due stanze con il muro in comune`() {
        // Filo esterno da −7,5 a 707,5 e da −7,5 a 307,5: il muro in mezzo conta una volta.
        assertEquals(715.0 * 315.0, FloorAreas.gross(FloorPlan(listOf(a, b))), 1e-6)
        // Ambiente unico (muro tolto da tutte e due): lì c'è pavimento, la superficie non cambia.
        assertEquals(715.0 * 315.0, FloorAreas.gross(FloorPlan(listOf(a.copy(removedWalls = setOf(1)), b.copy(removedWalls = setOf(3))))), 1e-6)
        // La stessa stanza disegnata due volte conta una volta.
        assertEquals(415.0 * 315.0, FloorAreas.gross(FloorPlan(listOf(a, a.copy(id = 9)))), 1e-6)
        // Balconi e terrazze non sono superficie lorda.
        assertEquals(715.0 * 315.0, FloorAreas.gross(FloorPlan(listOf(a, b, rect(3, 400.0, 150.0, Vec2(0.0, 300.0), RoomType.Balcone)))), 1e-6)
    }

    @Test
    fun `forme qualsiasi come il contorno esterno`() {
        val l = Room(1, "L", RoomType.Soggiorno, listOf(Vec2(0.0, 0.0), Vec2(600.0, 0.0), Vec2(600.0, 250.0), Vec2(300.0, 250.0), Vec2(300.0, 500.0), Vec2(0.0, 500.0)), wallThickness = 30.0)
        assertEquals(Polygon.area(l.exterior()), FloorAreas.gross(FloorPlan(listOf(l))), 1e-3)
        // Muro curvo: pezzi dritti lungo l'arco, come la pianta.
        val curved = a.copy(wallBulges = mapOf(0 to 60.0))
        val exp = curved.expandCurves().room
        assertEquals(Polygon.area(exp.exterior()), FloorAreas.gross(FloorPlan(listOf(curved))), 1e-3 * Polygon.area(exp.exterior()))
    }

    @Test
    fun `stanze a caso confrontate con una griglia fitta`() {
        for (seed in 0 until 25) {
            val rnd = Random(seed)
            val rooms = (1..rnd.nextInt(2, 5)).map { k ->
                val at = Vec2(rnd.nextInt(0, 6) * 100.0 + rnd.nextInt(0, 3) * 7.5, rnd.nextInt(0, 6) * 100.0)
                rect(k.toLong(), rnd.nextInt(2, 6) * 100.0, rnd.nextInt(2, 6) * 100.0, at, t = listOf(10.0, 15.0, 30.0)[rnd.nextInt(3)])
            }
            val gross = FloorAreas.gross(FloorPlan(rooms))
            val outs = rooms.map { it.exterior() }
            // Punti a passo di 1 cm, spostati di un poco: mai proprio sul filo di un muro.
            val step = 1.0
            var inside = 0
            var y = -50.0 + 0.37
            while (y < 1200) {
                var x = -50.0 + 0.41
                while (x < 1200) {
                    val p = Vec2(x, y)
                    if (outs.any { Polygon.contains(it, p) }) inside++
                    x += step
                }
                y += step
            }
            val raster = inside * step * step
            assertTrue(abs(gross - raster) <= 0.004 * raster, "caso $seed: $gross contro $raster")
        }
    }

    @Test
    fun `muri esterni oltre 50 cm e superficie commerciale`() {
        // Casa di pietra con i muri da 70: 20 cm in più su tutti e quattro i muri.
        val stone = rect(1, 500.0, 400.0, t = 70.0)
        assertEquals(20.0 * 1800.0, FloorAreas.extraWall(FloorPlan(listOf(stone))), 1e-6)
        // Il muro in comune con un'altra stanza non è esterno.
        val next = rect(2, 300.0, 400.0, Vec2(500.0, 0.0), t = 70.0)
        assertEquals(20.0 * (1800.0 - 400.0) + 20.0 * (1400.0 - 400.0), FloorAreas.extraWall(FloorPlan(listOf(stone, next))), 1e-6)
        assertEquals(0.0, FloorAreas.extraWall(FloorPlan(listOf(a, b))))
        // 100 m² sopra terra, 20 di cantina (a metà), 30 di balconi (25 al 30%, 5 al 10%).
        assertEquals(100.0 + 10.0 + 7.5 + 0.5, FloorAreas.commercial(100.0, 20.0, 30.0), 1e-9)
    }

    @Test
    fun `nel computo una riga per piano e la superficie commerciale`() {
        val balcony = rect(3, 400.0, 150.0, Vec2(0.0, 300.0), RoomType.Balcone)
        val cellar = rect(4, 400.0, 300.0, type = RoomType.Altro)
        val b2 = Building(listOf(Floor(1, "Interrato", FloorPlan(listOf(cellar))), Floor(2, "Piano terra", FloorPlan(listOf(a, b, balcony)))), ground = 1)
        val res = Takeoff.of(b2)
        assertEquals(listOf("Interrato", "Piano terra"), res.areas.map { it.floor })
        assertEquals(listOf(true, false), res.areas.map { it.basement })
        val ground = res.areas[1]
        assertEquals(715.0 * 315.0 / 10_000, ground.gross, 1e-9)
        assertEquals((a.usableArea() + b.usableArea()) / 10_000, ground.usable, 1e-9)
        assertEquals(balcony.usableArea() / 10_000, ground.outdoor, 1e-9)
        val expected = 715.0 * 315.0 / 10_000 + 0.5 * 415.0 * 315.0 / 10_000 + 0.3 * balcony.usableArea() / 10_000
        assertEquals(expected, res.commercial, 1e-9)
        val csv = Takeoff.csv(res)
        assertTrue(csv.contains("SUPERFICI\nPiano;Utile m²;Lorda m²;Balconi e terrazze m²\nInterrato;"), csv)
        assertTrue(csv.contains("Superficie commerciale indicativa;" + formatDecimal(expected, 2) + "\n"), csv)
    }
}
