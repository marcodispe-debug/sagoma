package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.Vec2
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gradini, davanzali e soglie con i materiali fotografici dei pavimenti: la foto nel 3D, il nome nelle descrizioni. */
class StepSillFloorMaterialTest {
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))

    @BeforeTest
    fun catalog() = MaterialCatalog.set(
        listOf(
            MaterialCatalog.Item("rovere", "Parquet rovere", "Legno", size = 120.0, argb = 0xFFC49A6C, use = "pavimento"),
            MaterialCatalog.Item("travertino", "Travertino", "Pietra", size = 60.0, argb = 0xFFE6DCCB),
        ),
    )

    @AfterTest
    fun reset() = MaterialCatalog.set(emptyList())

    @Test
    fun `gradini con la foto del pavimento - la foto sulle pedate, senza foto il colore di sempre`() {
        val stair = Stair(1, StairKind.Straight, Vec2(250.0, 200.0))
        val plain = Scene3D.build(FloorPlan(listOf(room), stairs = listOf(stair)), null, null, ceilings = false)
        assertTrue("rovere" !in plain.textured.keys)
        val photo = Scene3D.build(FloorPlan(listOf(room), stairs = listOf(stair.copy(treadMaterial = "rovere"))), null, null, ceilings = false)
        assertTrue(photo.textured["rovere"]!!.isNotEmpty())
    }

    @Test
    fun `davanzale con la foto del pavimento - nel 3D e nel nome, senza lastra niente`() {
        val window = Opening.default(1, OpeningKind.Window1, 0, 200.0).copy(sillMaterial = StairMaterial.Marble, sillFloorMaterial = "travertino")
        val scene = Scene3D.build(FloorPlan(listOf(room.copy(openings = listOf(window)))), null, null, ceilings = false)
        assertTrue(scene.textured["travertino"]!!.isNotEmpty())
        assertEquals("Travertino", window.sillLabel)
        assertTrue("travertino" in Openings.description(window))
        // Senza lastra la foto non conta.
        val none = window.copy(sillMaterial = null)
        assertNull(none.sillPhoto)
        assertNull(none.sillLabel)
        // Senza foto il nome è quello di sempre.
        assertEquals(StairMaterial.Marble.label, window.copy(sillFloorMaterial = null).sillLabel)
    }

    @Test
    fun `sotto la foto resta il materiale di sempre col colore piu vicino`() {
        assertEquals(StairMaterial.Wood, StairMaterial.closestTo(0xFFC49A6C))
        assertEquals(StairMaterial.Marble, StairMaterial.closestTo(0xFFF0EEEA))
        assertEquals(StairMaterial.DarkWood, StairMaterial.closestTo(0xFF70503A))
    }

    @Test
    fun `copia stile - anche la foto del davanzale passa alle altre finestre`() {
        val from = Opening.default(1, OpeningKind.Window1, 0, 200.0).copy(sillMaterial = StairMaterial.Stone, sillFloorMaterial = "travertino")
        val other = Opening.default(2, OpeningKind.Window2, 1, 200.0).withStyleOf(from)
        assertEquals("travertino", other.sillFloorMaterial)
        assertEquals(StairMaterial.Stone, other.sillMaterial)
    }
}
