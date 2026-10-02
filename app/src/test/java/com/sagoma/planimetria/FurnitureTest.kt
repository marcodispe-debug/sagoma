package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Furnishings
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Versione pro: arredi. */
class FurnitureTest {

    // Stanza 500 × 400: facce interne dei muri a 7,5 e 492,5 (x), 7,5 e 392,5 (y).
    private val room = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(500.0, 400.0))
    private val plan = FloorPlan(listOf(room))
    private fun base(id: Long, center: Vec2) = Furniture(id, "kitchenCabinet", center, 0.0, 60.0, 60.0, 90.0)

    @Test
    fun `il catalogo della versione pro ha per ogni modello misure, nome, licenza e i suoi file`() {
        val dir = java.io.File("src/pro/assets/furniture")
        FurnitureCatalog.load(java.io.File(dir, "catalog.json").readText())
        val items = FurnitureCatalog.items
        assertTrue(items.size >= 100)
        assertEquals(items.size, items.map { it.model }.toSet().size)
        for (i in items) {
            assertTrue(i.model, i.width > 0 && i.depth > 0 && i.height > 0 && i.label.isNotBlank() && i.license.isNotBlank())
            for (suffix in listOf(".glb", ".png", "_top.png")) assertTrue(i.model + suffix, java.io.File(dir, i.model + suffix).exists())
        }
        assertTrue(FurnitureCatalog.search("divano").isNotEmpty())
        assertEquals("Soggiorno", FurnitureCatalog.categories.first())
    }

    @Test
    fun `i mobili del vecchio catalogo diventano modelli esistenti, con le stesse misure`() {
        FurnitureCatalog.load(java.io.File("src/pro/assets/furniture/catalog.json").readText())
        for ((old, new) in FurnitureCatalog.RENAMED) assertNotNull("$old → $new", FurnitureCatalog.item(new))
        val old = Furniture(1, "toiletSquare", Vec2(100.0, 100.0), 90.0, 38.0, 55.0, 80.0)
        val back = PlanJson.decode(PlanJson.encode(plan.copy(furniture = listOf(old)))).furniture.single()
        assertEquals(old.copy(model = "ob_389d5427a1f344c4"), back)
    }

    @Test
    fun `un mobile vicino al muro si accosta alla sua faccia, e si mette in fila con un altro`() {
        val a = Furnishings.snapped(plan, base(1, Vec2(100.0, 45.0)))
        assertEquals(7.5 + 30.0, a.center.y, 1e-9) // dietro contro la faccia interna del muro in alto
        val withA = plan.copy(furniture = listOf(a))
        // Il secondo, lasciato a 8 cm dal primo, gli si accosta di fianco.
        val b = Furnishings.snapped(withA, base(2, Vec2(100.0 + 60.0 + 8.0, 40.0)))
        assertEquals(160.0, b.center.x, 1e-9)
        assertEquals(a.center.y, b.center.y, 1e-9)
    }

    @Test
    fun `accostato al muro con il davanti, il mobile si gira con lo schienale al muro`() {
        // A 0° il davanti guarda in basso: vicino al muro in basso si gira di 180°.
        val sofa = Furnishings.snapped(plan, Furniture(1, "loungeSofa", Vec2(250.0, 340.0), 0.0, 210.0, 90.0, 85.0))
        assertEquals(392.5 - 45.0, sofa.center.y, 1e-9)
        assertEquals(180.0, sofa.rotation, 1e-9)
        // Vicino al muro in alto il retro tocca già il muro: resta com'è.
        assertEquals(0.0, Furnishings.snapped(plan, Furniture(2, "loungeSofa", Vec2(250.0, 60.0), 0.0, 210.0, 90.0, 85.0)).rotation, 1e-9)
    }

    @Test
    fun `la maniglia sta davanti e girandola il mobile si raddrizza a 90 gradi`() {
        val f = base(1, Vec2(200.0, 200.0))
        // A 0° il davanti guarda in basso (y cresce).
        assertEquals(Vec2(200.0, 200.0 + 30.0 + Furnishings.HANDLE), Furnishings.handle(f))
        // Dito a destra del mobile: il davanti guarda a destra → rotazione 270° (−90°).
        assertEquals(270.0, Furnishings.rotationToward(f, Vec2(300.0, 203.0)), 1e-9)
        assertEquals(0.0, Furnishings.rotationToward(f, Vec2(204.0, 300.0)), 1e-9)
        // Lontano da un multiplo di 90° resta dove lo si porta.
        assertEquals(315.0, Furnishings.rotationToward(f, Vec2(300.0, 300.0)), 1e-9)
        // Ruotato di 90° l'ingombro scambia larghezza e profondità.
        val rotated = base(2, Vec2(200.0, 200.0)).copy(width = 120.0, depth = 60.0, rotation = 90.0)
        val b = Polygon.bounds(Furnishings.outline(rotated))
        assertEquals(60.0, b.width, 1e-9)
        assertEquals(120.0, b.height, 1e-9)
    }

    @Test
    fun `i pensili e le lampade a soffitto nascono alla loro altezza`() {
        val upperItem = FurnitureCatalog.Item("pensile", "Pensile", "Cucina", width = 60.0, depth = 35.0, height = 70.0, elevation = 145.0)
        val lampItem = FurnitureCatalog.Item("plafoniera", "Plafoniera", "Luci", width = 45.0, depth = 45.0, height = 25.0, ceiling = true)
        val upper = Furnishings.create(plan, upperItem, Vec2(250.0, 200.0), 300.0)
        assertEquals(145.0, upper.elevation, 1e-9)
        val lamp = Furnishings.create(plan, lampItem, Vec2(250.0, 200.0), 300.0)
        assertEquals(270.0 - 25.0, lamp.elevation, 1e-9)
    }

    @Test
    fun `gli arredi si salvano, nel 3D si toccano e bloccano chi cammina, i tappeti no`() {
        val sofa = Furniture(1, "ph_sofa_03", Vec2(250.0, 200.0), 0.0, 210.0, 90.0, 85.0)
        val rug = Furniture(2, "sh_scopia_round_carpet", Vec2(250.0, 320.0), 0.0, 200.0, 100.0, 1.5)
        val p = plan.copy(furniture = listOf(sofa, rug))
        assertEquals(p, PlanJson.decode(PlanJson.encode(p)))
        val scene = Scene3D.build(p, null, Pick.Furniture(1), ceilings = false)
        val hit = scene.hit(Vec3(250.0, 1000.0, 200.0), Vec3(0.0, -1.0, 0.0))!!
        assertEquals(Pick.Furniture(1), hit.pick)
        assertEquals(85.0, hit.point.y, 0.5)
        // Chi cammina si ferma contro il davanti del divano (a y = 245).
        assertTrue(scene.blocked(Vec2(250.0, 252.0), 10.0))
        assertTrue(!scene.blocked(Vec2(250.0, 320.0), 10.0))
        assertEquals(listOf(true, false), scene.furniture.map { it.selected })
        assertEquals(sofa.model, scene.furniture[0].model)
    }
}
