package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.assets.AssetPlanner
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.KitchenModules
import com.sagoma.planimetria.model.KitchenModules.Front
import com.sagoma.planimetria.model.KitchenModules.Kind
import com.sagoma.planimetria.model.KitchenModules.Spec
import com.sagoma.planimetria.model.KitchenModules.Top
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Moduli della cucina generati alla misura: nomi nel progetto, catalogo, geometria senza stiramenti, scena e salvataggio. */
class KitchenModulesTest {
    @AfterTest
    fun reset() {
        FurnitureCatalog.set(emptyList())
        MaterialCatalog.set(emptyList())
    }

    private fun module(kind: Kind, w: Double = kind.width, front: Front = Front.WHITE, top: Top = Top.LAMINATE_WHITE, mirrored: Boolean = false) =
        Furniture(1, KitchenModules.model(Spec(kind, front, top)), Vec2(200.0, 100.0), 0.0, w, kind.depth, kind.height, kind.elevation, mirrored)

    @Test
    fun `il nome del modello descrive tipo e finiture e si rilegge uguale`() {
        for (k in Kind.entries) for (f in Front.entries) for (t in Top.entries) {
            val s = Spec(k, f, t)
            assertEquals(s, KitchenModules.parse(KitchenModules.model(s)))
        }
        assertEquals("cucina:base:noce:carrara", KitchenModules.model(Spec(Kind.BASE_DOORS, Front.WALNUT, Top.CARRARA)))
        assertNull(KitchenModules.parse("ph_sofa_01"))
        assertNull(KitchenModules.parse("cucina:sconosciuto:bianco:lam-bianco"))
        // Finiture sconosciute (file di una versione più nuova): il modulo resta, con le finiture di partenza.
        assertEquals(Spec(Kind.SINK), KitchenModules.parse("cucina:lavello:viola:oro"))
    }

    @Test
    fun `catalogo - un modulo per tipo nella sua categoria, prima della cucina, con le misure standard`() {
        FurnitureCatalog.set(listOf(FurnitureCatalog.Item("frigo1", "Frigorifero", "Cucina", width = 60.0, depth = 60.0, height = 180.0)))
        FurnitureCatalog.addKitchen()
        FurnitureCatalog.addKitchen() // due volte: nessun doppione
        val modules = FurnitureCatalog.items.filter { KitchenModules.isModule(it.model) }
        assertEquals(Kind.entries.size, modules.size)
        assertTrue(modules.all { it.category == KitchenModules.CATEGORY })
        val cats = FurnitureCatalog.categories
        assertTrue(cats.indexOf(KitchenModules.CATEGORY) < cats.indexOf("Cucina"))
        val wall = modules.first { KitchenModules.parse(it.model)!!.kind == Kind.WALL_DOORS }
        assertEquals(145.0, wall.elevation)
        assertEquals(35.0, wall.depth)
        // Una finitura che il catalogo non elenca ha comunque il suo nome completo (pannello, computo).
        val item = FurnitureCatalog.item("cucina:base:noce:carrara")
        assertNotNull(item)
        assertEquals("Base con ante · Noce · piano marmo di Carrara", item.label)
    }

    @Test
    fun `ogni pezzo sta dentro l'ingombro del modulo`() {
        for (k in Kind.entries) for (w in listOf(30.0, 45.0, 60.0, 90.0, 120.0)) {
            val s = Spec(k, Front.OAK, Top.CARRARA)
            val d = k.depth
            val h = k.height
            for (p in KitchenGeometry.parts(s, w, d, h)) {
                assertTrue(p.x0 >= -w / 2 - 1e-9 && p.x1 <= w / 2 + 1e-9, "$k $w: x ${p.x0}..${p.x1}")
                assertTrue(p.z0 >= -d / 2 - 1e-9 && p.z1 <= d / 2 + 1e-9, "$k $w: z ${p.z0}..${p.z1}")
                assertTrue(p.y0 >= -1e-9, "$k $w: y ${p.y0}")
                // Sopra il modulo solo rubinetto, vasca e piano cottura.
                if (p.y1 > h + 1e-9) assertTrue(k == Kind.SINK || k == Kind.OVEN_HOB, "$k: y ${p.y1} > $h")
            }
        }
    }

    @Test
    fun `allargando un modulo le ante si ridisegnano - maniglie, fughe e cassetti non si stirano`() {
        fun doors(w: Double) = KitchenGeometry.fronts(Spec(Kind.BASE_DOORS), w, 90.0)
        assertEquals(1, doors(45.0).size)
        assertEquals(2, doors(60.0).size)
        assertEquals(2, doors(120.0).size)
        // Maniglie lunghe al massimo 16 cm a qualsiasi larghezza.
        for (w in listOf(45.0, 60.0, 120.0)) for (p in doors(w)) {
            val (a, b, _) = KitchenGeometry.handle(p)!!
            assertTrue(b - a <= 16.0 + 1e-9)
        }
        // Fuga tra le due ante di 0,3 cm.
        val two = doors(80.0)
        assertEquals(KitchenGeometry.GAP, two[1].x0 - two[0].x1, 1e-9)
        // Cassetti: alto 16 cm (meno la fuga) e due uguali, qualunque sia la larghezza.
        for (w in listOf(40.0, 60.0, 100.0)) {
            val drawers = KitchenGeometry.fronts(Spec(Kind.BASE_DRAWERS), w, 90.0).sortedByDescending { it.y0 }
            assertEquals(3, drawers.size)
            assertEquals(16.0 - KitchenGeometry.GAP, drawers[0].height, 1e-9)
            assertEquals(drawers[1].height, drawers[2].height, 1e-9)
        }
        // Base: frontali tra lo zoccolo e il piano; pensile: tutta l'altezza, maniglia in basso.
        val base = KitchenGeometry.fronts(Spec(Kind.BASE_DOORS), 60.0, 90.0)
        assertTrue(base.all { it.y0 >= KitchenGeometry.PLINTH && it.y1 <= 90.0 - KitchenGeometry.TOP })
        val wall = KitchenGeometry.fronts(Spec(Kind.WALL_DOORS), 60.0, 72.0)
        assertTrue(wall.all { it.handle == KitchenGeometry.HandleAt.BOTTOM })
    }

    @Test
    fun `i frontali non si sovrappongono`() {
        for (k in Kind.entries) {
            val f = KitchenGeometry.fronts(Spec(k), 90.0, k.height)
            for (i in f.indices) for (j in i + 1 until f.size) {
                val a = f[i]; val b = f[j]
                val overlap = a.x0 < b.x1 - 1e-9 && b.x0 < a.x1 - 1e-9 && a.y0 < b.y1 - 1e-9 && b.y0 < a.y1 - 1e-9
                assertTrue(!overlap, "$k: $a e $b")
            }
        }
    }

    @Test
    fun `nella scena il modulo e disegnato dalla geometria, non come file 3D, e il marmo del piano e una foto`() {
        MaterialCatalog.set(listOf(MaterialCatalog.Item("marble021", "Marmo di Carrara", "Marmo e pietra", size = 120.0)))
        val room = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(400.0, 300.0))
        val empty = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val plan = FloorPlan(listOf(room), furniture = listOf(module(Kind.SINK, top = Top.CARRARA)))
        val scene = Scene3D.build(plan, null, null, ceilings = false)
        assertTrue(scene.furniture.isEmpty())
        assertTrue(scene.opaque.size > empty.opaque.size)
        assertTrue((scene.textured["marble021"]?.size ?: 0) >= 6 * 8)
        // Per la pianta e in anticipo non si scarica niente.
        assertTrue(AssetPlanner.forPlans(listOf(plan)).isEmpty())
    }

    @Test
    fun `nel progetto resta solo il nome del modello - il file si riscrive identico`() {
        val plan = FloorPlan(furniture = listOf(module(Kind.CORNER, w = 90.0, front = Front.SAGE, top = Top.STONE, mirrored = true)))
        val text = PlanJson.encode(plan)
        assertTrue("\"model\":\"cucina:angolo:salvia:pietra\"" in text, text)
        val back = PlanJson.decode(text)
        assertEquals(plan.furniture, back.furniture)
        assertEquals(text, PlanJson.encode(back))
    }
}
