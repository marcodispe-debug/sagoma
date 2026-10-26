package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.SuppliedObject
import com.sagoma.planimetria.model.SuppliedObjects
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La cucina del negozio dal suo IFC. I due file di prova sono fatti come quelli dei programmi da negozio (solo mobili, niente
 * muri): una cucina a L di 13 mobili (5 basi, colonna frigo, 2 basi sul lato corto, cappa e 4 pensili ripetuti).
 * - ifc4-mm: IFC4 in millimetri, facce triangolate con i colori, pensili come oggetto ripetuto, lotto a 500 km dall'origine;
 * - ifc2x3-m-girata: IFC2x3 in metri, Brep a facce ed estrusioni di rettangoli, tutta la cucina girata di 90°.
 */
class IfcFurnitureTest {
    private fun file(name: String) = IfcFurnitureTest::class.java.getResource("/ifc/$name")!!.readText()

    private fun read(name: String) = IfcFurniture.read(file(name), id = 1, name = "Cucina Rossi")

    private fun near(expected: Double, actual: Double, tol: Double = 0.5) = assertTrue(abs(expected - actual) <= tol, "atteso $expected, letto $actual")

    @Test
    fun cucinaIfc4InMillimetri() {
        val r = read("cucina-negozio-ifc4-mm.ifc")
        val obj = assertNotNull(r.obj)
        assertEquals(13, obj.elements.size)
        // Da x −62 a 420 cm (fine della colonna frigo), profondità da 0 a 172 cm, in alto fino alla cappa a 250 cm.
        near(482.0, obj.width); near(172.0, obj.depth); near(250.0, obj.height)
        // 13 mobili, 12 triangoli per ogni scatola: 5 basi da 3 pezzi (+1 lavello, +2 forno), frigo, 2 × 3 lato corto, cappa 2, pensili 4.
        assertEquals((15 + 1 + 2 + 1 + 6 + 2 + 4) * 12, r.triangles)
        // I colori del file: bianco, rovere, acciaio, nero, piano scuro, zoccolo.
        assertEquals(6, obj.parts.size)
        assertFalse(obj.parts.any { it.argb == IfcFurniture.DEFAULT_COLOR })
        // I pensili ripetuti stanno ognuno al suo posto, appesi da 150 a 222 cm.
        val pensili = obj.elements.filter { it.name == "Pensile 60" }
        // Centro della cucina a x = (−62 + 420) / 2 = 179 cm: i pensili partono da 0, 60, 150 e 210 cm.
        assertEquals(listOf(0, 60, 150, 210).map { (it - 179) * 10 }, pensili.map { it.x0 }.sorted())
        assertTrue(pensili.all { it.y0 == 1500 && it.y1 == 2220 })
        // Il centro nel file (lotto lontano): 50 000 m più metà cucina.
        val c = assertNotNull(r.center)
        near(5_000_000.0 + 179.0, c.x, 1.0)
    }

    @Test
    fun cucinaIfc2x3InMetriGirata() {
        val r = read("cucina-negozio-ifc2x3-m-girata.ifc")
        val obj = assertNotNull(r.obj)
        assertEquals(13, obj.elements.size)
        // Girata di 90°: larghezza e profondità si scambiano.
        near(172.0, obj.width); near(482.0, obj.depth); near(250.0, obj.height)
        assertEquals(6, obj.parts.size)
        assertTrue(r.skipped.isEmpty(), r.skipped.toString())
    }

    @Test
    fun daUnFileDiArchitetturaNessunMobile() {
        val house = Building.single(FloorPlan(rooms = listOf(Room(1, "Cucina", RoomType.Cucina, listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0))))))
        val r = IfcFurniture.read(IfcExport.export(house, "Casa", key = "k"), id = 1, name = "x")
        assertNull(r.obj)
    }

    private fun kitchenHouse(): Pair<Building, SuppliedObject> {
        val obj = assertNotNull(read("cucina-negozio-ifc4-mm.ifc").obj)
        val room = Room(1, "Cucina", RoomType.Cucina, listOf(Vec2(0.0, 0.0), Vec2(500.0, 0.0), Vec2(500.0, 400.0), Vec2(0.0, 400.0)))
        val f = Furniture(1, SuppliedObjects.model(obj.id), Vec2(250.0, 120.0), 0.0, obj.width, obj.depth, obj.height)
        return Building.single(FloorPlan(rooms = listOf(room), furniture = listOf(f), supplied = listOf(obj))) to obj
    }

    @Test
    fun nel3DCiSonoTuttiITriangoli() {
        val (house, obj) = kitchenHouse()
        val without = Scene3D.build(house.floor.plan.copy(furniture = emptyList(), supplied = emptyList()), null, null, ceilings = false)
        val with = Scene3D.build(house.floor.plan, null, null, ceilings = false)
        val tris = obj.parts.sumOf { it.triangles.size / 3 }
        // 10 float per vertice, 3 vertici per triangolo.
        assertEquals(tris * 30, with.opaque.size - without.opaque.size)
        // Nessun modello da scaricare: la forma è nel progetto.
        assertTrue(with.furniture.isEmpty())
        // Da sola (senza stanze): appoggiata al pavimento, alta come nel file.
        val alone = Scene3D.build(house.floor.plan.copy(rooms = emptyList()), null, null, ceilings = false)
        val ys = (0 until alone.opaque.size / 10).map { alone.opaque[it * 10 + 1].toDouble() }
        near(0.0, ys.min(), 0.1); near(250.0, ys.max(), 0.1)
    }

    @Test
    fun ilProgettoLaSalvaELaRilegge() {
        val (house, obj) = kitchenHouse()
        val back = PlanJson.decodeBuilding(PlanJson.encode(house))
        assertEquals(obj, back.floor.plan.supplied.single())
        assertEquals(obj, back.floor.plan.suppliedFor(back.floor.plan.furniture.single().model))
    }

    @Test
    fun laCucinaNonEsceMaiNellIfc() {
        val (house, _) = kitchenHouse()
        val text = IfcExport.export(house, "Casa", key = "k")
        assertFalse("FURNISHING" in text || "FURNITURE" in text)
        assertFalse("Pensile" in text || "Cucina Rossi" in text || "fornito:" in text)
        assertNull(IfcFurniture.read(text, id = 1, name = "x").obj)
    }
}
