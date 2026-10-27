package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.MaterialLayout
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.model.Building
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verso e misura dei pezzi di un pavimento fotografico (il parquet nell'altro verso, con le doghe più larghe). */
class MaterialLayoutTest {
    private val saved = MaterialCatalog.items

    @AfterTest
    fun restore() = MaterialCatalog.set(saved)

    @Test
    fun `girato di 90 gradi e ingrandito`() {
        val plain = MaterialLayout()
        assertEquals(Vec2(1.5, 0.5), plain.uv(150.0, 50.0, 100.0))
        // Di 90°: quello che correva lungo x ora corre lungo y.
        val turned = MaterialLayout(rotation = 90.0).uv(100.0, 0.0, 100.0)
        assertEquals(0.0, turned.x, 1e-9)
        assertEquals(-1.0, turned.y, 1e-9)
        // Pezzi più grandi del 50%: la foto si ripete ogni 150 cm invece che ogni 100.
        assertEquals(1.0, MaterialLayout(scale = 1.5).uv(150.0, 0.0, 100.0).x, 1e-9)
    }

    @Test
    fun `il pavimento nel 3D usa verso e misura scelti`() {
        MaterialCatalog.set(listOf(MaterialCatalog.Item("parquet-prova", "Parquet", "Legno", size = 100.0, use = "pavimento")))
        val layout = MaterialLayout(rotation = 90.0, scale = 2.0)
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)).copy(floorMaterial = "parquet-prova", floorLayout = layout)
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val v = scene.textured.getValue("parquet-prova")
        assertTrue(v.isNotEmpty())
        for (k in v.indices step 8) {
            val uv = layout.uv(v[k].toDouble(), v[k + 2].toDouble(), 100.0)
            assertEquals(uv.x, v[k + 6].toDouble(), 1e-3)
            assertEquals(uv.y, v[k + 7].toDouble(), 1e-3)
        }
    }

    @Test
    fun `si salva nel progetto e i progetti vecchi restano come prima`() {
        val plain = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)).copy(floorMaterial = "parquet-prova")
        val old = PlanJson.encode(Building.single(FloorPlan(listOf(plain))))
        assertTrue("floorLayout" !in old)
        val turned = plain.copy(floorLayout = MaterialLayout(rotation = 45.0, scale = 1.5))
        val back = PlanJson.decodeBuilding(PlanJson.encode(Building.single(FloorPlan(listOf(turned)))))
        assertEquals(MaterialLayout(45.0, 1.5), back.floor.plan.rooms.single().floorLayout)
    }
}
