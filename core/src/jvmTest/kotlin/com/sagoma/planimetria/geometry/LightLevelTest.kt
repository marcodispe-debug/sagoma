package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.LightLevel
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Luminosità scelta per ogni punto luce: impianti e lampade del catalogo, nel 3D e nel progetto salvato. */
class LightLevelTest {
    private fun room(vararg fixtures: Fixture) =
        Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0), fixtures = fixtures.toList())

    private fun lights(plan: FloorPlan) = Scene3D.build(plan, focusedRoomId = null, selected = null, ceilings = true).lights.filterNot { it.spill }

    private fun ceilingLight(id: Long, brightness: Double = 1.0) =
        Fixture(id, FixtureKind.CeilingLight, point = Vec2(150.0 + id * 50, 200.0), brightness = brightness)

    @Test
    fun `al 100 per cento ogni luce fa i lumen di sempre`() {
        val kinds = listOf(FixtureKind.Spotlight, FixtureKind.CeilingLight, FixtureKind.Chandelier, FixtureKind.Neon, FixtureKind.LedStrip)
        for (k in kinds) {
            val f = Fixture.default(1, k).copy(point = Vec2(250.0, 200.0))
            assertEquals(Scene3D.lumensOf(k), lights(FloorPlan(listOf(room(f)))).sumOf { it.lumens }, 1e-6, k.name)
        }
        assertEquals(listOf(400.0, 1500.0, 2000.0, 2500.0, 1000.0), kinds.map { Scene3D.lumensOf(it) })
        assertEquals(0.0, Scene3D.lumensOf(FixtureKind.Radiator))
    }

    @Test
    fun `ogni luce ha la sua luminosità`() {
        val plan = FloorPlan(listOf(room(ceilingLight(1, 0.5), ceilingLight(2), ceilingLight(3, 1.6))))
        assertEquals(listOf(750.0, 1500.0, 2400.0), lights(plan).map { it.lumens }.sorted())
    }

    @Test
    fun `spenta non fa luce`() {
        val plan = FloorPlan(listOf(room(ceilingLight(1, 0.0), ceilingLight(2))))
        assertEquals(listOf(1500.0), lights(plan).map { it.lumens })
        val strip = Fixture.default(3, FixtureKind.WallLedStrip).copy(wallIndex = 0, position = 250.0, brightness = 0.0)
        val spot = Fixture.default(4, FixtureKind.WallSpot).copy(wallIndex = 1, position = 200.0, brightness = 0.0)
        assertTrue(lights(FloorPlan(listOf(room(strip, spot)))).isEmpty())
    }

    @Test
    fun `spenta la lampada non brilla`() {
        // Le parti che brillano si disegnano senza normale (il renderer le fa luminose da sole).
        fun glowing(vararg fixtures: Fixture): Int {
            val o = Scene3D.build(FloorPlan(listOf(room(*fixtures))), focusedRoomId = null, selected = null, ceilings = true).opaque
            var n = 0
            var k = 0
            while (k < o.size) {
                if (o[k + 3] == 0f && o[k + 4] == 0f && o[k + 5] == 0f) n++
                k += Scene3D.FLOATS_PER_VERTEX
            }
            return n
        }
        val none = glowing()
        assertTrue(glowing(ceilingLight(1)) > none)
        assertTrue(glowing(ceilingLight(1, 0.3)) > none, "abbassata brilla ancora")
        assertEquals(none, glowing(ceilingLight(1, 0.0)))
        assertEquals(none, glowing(Fixture.default(2, FixtureKind.Chandelier).copy(point = Vec2(250.0, 200.0), brightness = 0.0)))
        assertEquals(none, glowing(Fixture.default(3, FixtureKind.WallLight).copy(wallIndex = 1, position = 200.0, brightness = 0.0)))
    }

    @Test
    fun `strisce LED e luci a parete si dividono il flusso scelto`() {
        val strip = Fixture.default(1, FixtureKind.WallLedStrip).copy(wallIndex = 0, position = 250.0, brightness = 1.5)
        val l = lights(FloorPlan(listOf(room(strip))))
        assertTrue(l.size > 1)
        assertEquals(Scene3D.WALL_LED_LUMENS * 1.5, l.sumOf { it.lumens }, 1e-6)
        val wall = Fixture.default(2, FixtureKind.WallLight).copy(wallIndex = 1, position = 200.0, brightness = 0.25)
        assertEquals(listOf(200.0), lights(FloorPlan(listOf(room(wall)))).map { it.lumens })
    }

    @Test
    fun `anche le lampade del catalogo`() {
        val saved = FurnitureCatalog.items
        try {
            FurnitureCatalog.set(listOf(
                FurnitureCatalog.Item("prova/lampada-terra", "Lampada da terra", "Luci", "Lamp", 40.0, 40.0, 160.0),
                FurnitureCatalog.Item("prova/lampadario", "Lampadario", "Luci", "Lamp", 60.0, 60.0, 60.0, elevation = 190.0, ceiling = true),
            ))
            val floor = Furniture(1, "prova/lampada-terra", Vec2(100.0, 100.0), width = 40.0, depth = 40.0, height = 160.0, brightness = 1.5)
            val hanging = Furniture(2, "prova/lampadario", Vec2(300.0, 200.0), width = 60.0, depth = 60.0, height = 60.0, elevation = 190.0)
            val off = Furniture(3, "prova/lampada-terra", Vec2(400.0, 300.0), width = 40.0, depth = 40.0, height = 160.0, brightness = 0.0)
            val plan = FloorPlan(listOf(room()), furniture = listOf(floor, hanging, off))
            assertEquals(listOf(Scene3D.lampLumens(false) * 1.5, Scene3D.lampLumens(true)), lights(plan).map { it.lumens }.sorted())
        } finally {
            FurnitureCatalog.set(saved)
        }
    }

    @Test
    fun `si salva nel progetto e i progetti vecchi restano al 100 per cento`() {
        val plan = FloorPlan(listOf(room(ceilingLight(1, 0.4), ceilingLight(2))))
        val text = PlanJson.encode(plan)
        assertEquals(1, Regex("brightness").findAll(text).count(), "il valore normale non si scrive")
        assertEquals(listOf(0.4, 1.0), PlanJson.decode(text).rooms[0].fixtures.map { it.brightness })
    }

    @Test
    fun `un valore fuori misura resta nei limiti`() {
        assertEquals(LightLevel.MAX, LightLevel.of(9.0))
        assertEquals(0.0, LightLevel.of(-1.0))
        assertEquals(1.0, LightLevel.of(Double.NaN))
        val plan = FloorPlan(listOf(room(ceilingLight(1, 50.0), ceilingLight(2, -3.0))))
        assertEquals(listOf(1500.0 * LightLevel.MAX), lights(plan).map { it.lumens })
    }
}
