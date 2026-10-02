package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Rgba
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Versione pro: colori liberi e rivestimenti delle pareti. */
class FinishTest {

    private val room = Room(1, "Bagno", RoomType.Bagno, RoomFactory.rectangle(300.0, 250.0))

    @Test
    fun `la finitura della singola parete vince su quella della stanza, che vince sul colore di tavolozza`() {
        val tiles = WallFinish(0xFF123456, Covering.Tiles)
        val r = room.copy(wallPaint = WallPaint.Sage, wallFinish = tiles, wallPaints = mapOf(2 to WallPaint.Sky), wallFinishes = mapOf(3 to WallFinish(0xFFABCDEF)))
        assertEquals(tiles, r.finishOf(0))
        assertEquals(WallPaint.Sky.argb, r.finishOf(2).argb)
        assertEquals(0xFFABCDEF, r.finishOf(3).argb)
        // Senza finiture pro resta il colore di sempre.
        assertEquals(WallPaint.Sage.argb, room.copy(wallPaint = WallPaint.Sage).finishOf(1).argb)
    }

    @Test
    fun `altezza del rivestimento - quella tipica, una scelta, oppure tutta la parete`() {
        assertEquals(120.0, WallFinish(0, Covering.Tiles).coverHeight!!, 1e-9)
        assertEquals(90.0, WallFinish(0, Covering.Wainscot, coveringHeight = 90.0).coverHeight!!, 1e-9)
        assertNull(WallFinish(0, Covering.Tiles, coveringHeight = WallFinish.FULL_HEIGHT).coverHeight)
        assertNull(WallFinish(0, Covering.Brick).coverHeight)
        assertNull(WallFinish(0).coverHeight)
    }

    @Test
    fun `le finiture si salvano e si rileggono, e i file vecchi restano validi`() {
        val plan = FloorPlan(
            listOf(room.copy(wallFinish = WallFinish(0xFF808080, Covering.Metro, 0xFFFFFFFF, 150.0), wallFinishes = mapOf(1 to WallFinish(0xFF112233)))),
            facadeFinish = WallFinish(0xFFEEDDCC, Covering.Stone),
            freeWalls = listOf(FreeWall(1, Vec2(0.0, 0.0), Vec2(100.0, 0.0), finish = WallFinish(0xFF445566, Covering.Boards))),
        )
        val back = PlanJson.decode(PlanJson.encode(plan))
        assertEquals(plan, back)
        // Un file senza finiture pro si legge con i colori di tavolozza.
        val old = PlanJson.decode(PlanJson.encode(FloorPlan(listOf(room.copy(wallPaint = WallPaint.Sand)))))
        assertEquals(WallPaint.Sand.argb, old.rooms[0].finishOf(0).argb)
        assertNull(old.facadeFinish)
    }

    @Test
    fun `nel 3D il rivestimento aggiunge le fughe e colora la parte bassa, sopra resta la pittura`() {
        val paint = 0xFF3C6FA8
        val plain = Scene3D.build(FloorPlan(listOf(room.copy(wallFinish = WallFinish(paint)))), null, null, ceilings = false)
        val tiled = Scene3D.build(FloorPlan(listOf(room.copy(wallFinish = WallFinish(paint, Covering.Tiles, 0xFFF4F4F0)))), null, null, ceilings = false)
        assertTrue(tiled.opaque.size > plain.opaque.size + 1000)
        // Faccia interna del muro di sinistra (x = 7,5): piastrelle sotto 120 cm, pittura sopra.
        fun colorAt(scene: Scene3D, x: Double, y: Double, z: Double): Triple<Float, Float, Float>? {
            val d = scene.opaque
            var k = 0
            while (k < d.size) {
                val xs = listOf(d[k], d[k + 10], d[k + 20]).map { it.toDouble() }
                val ys = listOf(d[k + 1], d[k + 11], d[k + 21]).map { it.toDouble() }
                val zs = listOf(d[k + 2], d[k + 12], d[k + 22]).map { it.toDouble() }
                if (xs.all { kotlin.math.abs(it - x) < 0.01 } && kotlin.math.abs(d[k + 3]) > 0.9 && y in ys.min()..ys.max() && z in zs.min()..zs.max()) {
                    return Triple(d[k + 6], d[k + 7], d[k + 8])
                }
                k += 30
            }
            return null
        }
        val blue = Rgba.argb(paint)
        val white = Rgba.argb(0xFFF4F4F0)
        assertEquals(Triple(blue.r, blue.g, blue.b), colorAt(tiled, 7.5, 200.0, 125.0))
        assertEquals(Triple(white.r, white.g, white.b), colorAt(tiled, 7.5, 60.0, 125.0))
    }

    @Test
    fun `i materiali fotografici della versione pro hanno le loro mappe e misure sensate`() {
        val dir = java.io.File("src/pro/assets/materials")
        com.sagoma.planimetria.model.MaterialCatalog.load(java.io.File(dir, "materials.json").readText())
        val items = com.sagoma.planimetria.model.MaterialCatalog.items
        assertTrue(items.size >= 30)
        for (m in items) {
            assertTrue(m.id, m.size in 20.0..500.0 && m.label.isNotBlank())
            for (s in listOf("color", "normal", "orm", "thumb")) assertTrue("${m.id}_$s", java.io.File(dir, "${m.id}_$s.jpg").exists())
        }
        assertTrue(items.any { it.forFloor } && items.any { it.forWall })
    }

    @Test
    fun `pavimento e rivestimento fotografici diventano superfici con le coordinate della foto`() {
        com.sagoma.planimetria.model.MaterialCatalog.set(listOf(com.sagoma.planimetria.model.MaterialCatalog.Item("parquet", "Parquet", "Legno", size = 100.0, argb = 0xFFB08050)))
        val r = room.copy(floorMaterial = "parquet", wallFinish = WallFinish(0xFFFFFFFF, Covering.Material, coveringHeight = 120.0, material = "parquet"))
        val scene = Scene3D.build(FloorPlan(listOf(r)), null, null, ceilings = false)
        val floor = scene.textured["parquet"]!!
        assertEquals(0, floor.size % 8)
        // Le uv del pavimento sono le coordinate della pianta divise per il lato della foto (100 cm).
        var k = 0
        var found = false
        while (k < floor.size) {
            if (floor[k + 4] > 0.9f && kotlin.math.abs(floor[k + 1] - 0.03f) < 1e-3) {
                assertEquals(floor[k] / 100f, floor[k + 6], 1e-4f)
                found = true
            }
            k += 8
        }
        assertTrue(found)
        // Il rivestimento arriva a 120 cm: nessun vertice della parete sopra.
        k = 0
        while (k < floor.size) { if (kotlin.math.abs(floor[k + 4]) < 0.1f) assertTrue(floor[k + 1] <= 120.01f); k += 8 }
        com.sagoma.planimetria.model.MaterialCatalog.set(emptyList())
    }

    @Test
    fun `tutti i rivestimenti si disegnano, anche sui muri singoli`() {
        for (c in Covering.entries) {
            val f = WallFinish(0xFFDDDDDD, c)
            val plan = FloorPlan(
                listOf(room.copy(wallFinish = f)),
                facadeFinish = f,
                freeWalls = listOf(FreeWall(1, Vec2(100.0, 50.0), Vec2(100.0, 200.0), height = 150.0, finish = f)),
            )
            val scene = Scene3D.build(plan, null, null, ceilings = false)
            assertTrue("$c", scene.opaque.isNotEmpty() && scene.opaque.size % Scene3D.FLOATS_PER_VERTEX == 0)
        }
    }
}
