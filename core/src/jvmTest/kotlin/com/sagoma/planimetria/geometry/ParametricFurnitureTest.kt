package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.assets.AssetPlanner
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.GeneratedFurniture
import com.sagoma.planimetria.model.KitchenModules.Front
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.model.ParametricFurniture.Kind
import com.sagoma.planimetria.model.ParametricFurniture.Spec
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Armadi, letti, librerie, tavoli e gli altri mobili generati alla misura: nomi, catalogo, geometria senza stiramenti, scena. */
class ParametricFurnitureTest {
    @AfterTest
    fun reset() {
        FurnitureCatalog.set(emptyList())
    }

    private fun piece(kind: Kind, w: Double = kind.width, finish: Front = Front.OAK, mirrored: Boolean = false) =
        Furniture(1, ParametricFurniture.model(Spec(kind, finish)), Vec2(200.0, 150.0), 0.0, w, kind.depth, kind.height, kind.elevation, mirrored)

    private fun parts(kind: Kind, w: Double = kind.width, h: Double = kind.height) = ParametricGeometry.parts(Spec(kind), w, kind.depth, h)

    @Test
    fun `il nome del modello descrive tipo e finitura e si rilegge uguale`() {
        for (k in Kind.entries) for (f in Front.entries) {
            val s = Spec(k, f)
            assertEquals(s, ParametricFurniture.parse(ParametricFurniture.model(s)))
        }
        assertEquals("mobile:armadio:noce", ParametricFurniture.model(Spec(Kind.WARDROBE, Front.WALNUT)))
        assertNull(ParametricFurniture.parse("ph_sofa_01"))
        assertNull(ParametricFurniture.parse("mobile:sconosciuto:rovere"))
        assertNull(ParametricFurniture.parse("cucina:base:bianco:lam-bianco"))
        // Finitura sconosciuta (file di una versione più nuova): il mobile resta, in rovere.
        assertEquals(Spec(Kind.BED), ParametricFurniture.parse("mobile:letto:oro"))
        // Codici tutti diversi: nessun tipo ne nasconde un altro.
        assertEquals(Kind.entries.size, Kind.entries.map { it.code }.toSet().size)
        assertTrue(GeneratedFurniture.isGenerated("mobile:tavolo:bianco"))
        assertTrue(GeneratedFurniture.isGenerated("cucina:base:bianco:lam-bianco"))
        assertTrue(!GeneratedFurniture.isGenerated("ph_sofa_01"))
        assertNull(GeneratedGeometry.parts("ph_sofa_01", 100.0, 50.0, 80.0))
    }

    @Test
    fun `catalogo - un mobile per tipo nella sua categoria, con le misure standard`() {
        FurnitureCatalog.set(listOf(FurnitureCatalog.Item("letto1", "Letto", "Camera", width = 160.0, depth = 200.0, height = 90.0)))
        FurnitureCatalog.addParametric()
        FurnitureCatalog.addParametric() // due volte: nessun doppione
        val generated = FurnitureCatalog.items.filter { ParametricFurniture.isModel(it.model) }
        assertEquals(Kind.entries.size, generated.size)
        for (i in generated) {
            val k = ParametricFurniture.parse(i.model)!!.kind
            assertEquals(k.category, i.category)
            assertEquals(k.label, i.label)
            assertEquals(k.width, i.width)
        }
        assertTrue("Studio" in FurnitureCatalog.categories && "Pranzo" in FurnitureCatalog.categories)
        assertEquals(140.0, generated.first { it.model.startsWith("mobile:mensola:") }.elevation)
        // Una finitura che il catalogo non elenca ha comunque il suo nome completo.
        val item = FurnitureCatalog.item("mobile:libreria:blu")
        assertNotNull(item)
        assertEquals("Libreria su misura · Blu notte", item.label)
    }

    @Test
    fun `ogni pezzo sta dentro l'ingombro del mobile, a qualsiasi misura`() {
        for (k in Kind.entries) for (w in listOf(40.0, 60.0, 90.0, 150.0, 240.0, 320.0)) for (hk in listOf(0.6, 1.0, 1.3)) {
            val d = k.depth
            val h = k.height * hk
            val list = ParametricGeometry.parts(Spec(k, Front.WALNUT), w, d, h)
            assertTrue(list.isNotEmpty(), "$k $w")
            for (p in list) {
                assertTrue(p.x0 >= -w / 2 - 1e-9 && p.x1 <= w / 2 + 1e-9, "$k $w×$h: x ${p.x0}..${p.x1}")
                assertTrue(p.z0 >= -d / 2 - 1e-9 && p.z1 <= d / 2 + 1e-9, "$k $w×$h: z ${p.z0}..${p.z1}")
                assertTrue(p.y0 >= -1e-9 && p.y1 <= h + 1e-9, "$k $w×$h: y ${p.y0}..${p.y1}")
                assertTrue(p.x1 > p.x0 && p.y1 > p.y0 && p.z1 > p.z0, "$k $w×$h: pezzo vuoto $p")
            }
        }
    }

    @Test
    fun `allargando un armadio si aggiungono ante - maniglie e fughe restano uguali`() {
        for (w in listOf(50.0, 100.0, 150.0, 220.0, 300.0)) {
            val face = Kind.WARDROBE.depth / 2 - 1.2
            val list = parts(Kind.WARDROBE, w)
            val doors = list.filter { abs(it.z1 - face) < 1e-9 && abs(it.z0 - (face - ParametricGeometry.FRONT)) < 1e-9 }
            assertEquals(ceil(w / ParametricGeometry.MAX_DOOR).toInt(), doors.size, "armadio $w")
            assertTrue(doors.all { it.x1 - it.x0 <= ParametricGeometry.MAX_DOOR }, "armadio $w")
            val sorted = doors.sortedBy { it.x0 }
            for (i in 1 until sorted.size) assertEquals(ParametricGeometry.GAP, sorted[i].x0 - sorted[i - 1].x1, 1e-9)
            // Maniglie (davanti alle ante): una per anta, lunghe al massimo 40 cm.
            val handles = list.filter { it.z0 >= face - 1e-9 }
            assertEquals(doors.size, handles.size)
            assertTrue(handles.all { it.y1 - it.y0 <= 40.0 + 1e-9 && it.x1 - it.x0 <= 1.2 + 1e-9 })
        }
    }

    @Test
    fun `la libreria aggiunge ripiani e montanti invece di stirarli`() {
        fun shelves(w: Double, h: Double) = parts(Kind.BOOKCASE, w, h).filter {
            abs(it.x0 - (-w / 2 + ParametricGeometry.PANEL)) < 1e-9 && abs(it.x1 - (w / 2 - ParametricGeometry.PANEL)) < 1e-9 && abs(it.y1 - it.y0 - ParametricGeometry.PANEL) < 1e-9
        }.sortedBy { it.y0 }
        assertTrue(shelves(100.0, 240.0).size > shelves(100.0, 120.0).size)
        for (h in listOf(100.0, 160.0, 200.0, 240.0)) {
            val s = shelves(100.0, h)
            assertTrue(s.isNotEmpty(), "libreria alta $h")
            for (i in 1 until s.size) assertTrue(s[i].y0 - s[i - 1].y0 in 29.0..50.0, "libreria alta $h: passo ${s[i].y0 - s[i - 1].y0}")
        }
        // Campate al massimo di 80 cm: montanti in più quando si allarga.
        fun uprights(w: Double) = parts(Kind.BOOKCASE, w).count { abs(it.x1 - it.x0 - ParametricGeometry.PANEL) < 1e-9 && it.y0 > 1.0 }
        assertEquals(0, uprights(80.0))
        assertEquals(1, uprights(150.0))
        assertEquals(3, uprights(300.0))
    }

    @Test
    fun `cassettiera e letti - piu cassetti se piu alta, due cuscini solo nel letto largo`() {
        fun drawers(h: Double) = parts(Kind.CHEST, 100.0, h).count { it.x1 - it.x0 > 90.0 && it.z1 <= Kind.CHEST.depth / 2 - 1.0 + 1e-9 && it.z0 >= Kind.CHEST.depth / 2 - 1.0 - ParametricGeometry.FRONT - 1e-9 }
        assertTrue(drawers(120.0) > drawers(70.0))
        assertTrue(drawers(70.0) >= 2)
        fun pillows(w: Double) = parts(Kind.BED, w).count { it.argb == 0xFFFAF9F6L }
        assertEquals(2, pillows(160.0))
        assertEquals(1, pillows(90.0))
    }

    @Test
    fun `nella scena il mobile e disegnato dalla geometria e non chiede file`() {
        val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 350.0))
        val empty = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        for (k in Kind.entries) {
            val plan = FloorPlan(listOf(room), furniture = listOf(piece(k)))
            val scene = Scene3D.build(plan, null, null, ceilings = false)
            assertTrue(scene.furniture.isEmpty(), "$k")
            assertTrue(scene.opaque.size > empty.opaque.size, "$k")
            assertTrue(AssetPlanner.forPlans(listOf(plan)).isEmpty(), "$k")
        }
    }

    @Test
    fun `nel progetto resta solo il nome del modello - il file si riscrive identico`() {
        val plan = FloorPlan(furniture = listOf(piece(Kind.SLIDING_WARDROBE, w = 270.0, finish = Front.SAGE, mirrored = true)))
        val text = PlanJson.encode(plan)
        assertTrue("\"model\":\"mobile:armadio-scorrevole:salvia\"" in text, text)
        val back = PlanJson.decode(text)
        assertEquals(plan.furniture, back.furniture)
        assertEquals(text, PlanJson.encode(back))
    }
}
