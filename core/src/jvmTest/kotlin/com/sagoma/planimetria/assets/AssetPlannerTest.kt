package com.sagoma.planimetria.assets

import com.sagoma.planimetria.assets.remote.AssetPriority
import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetPlannerTest {
    @AfterTest
    fun resetCatalogs() {
        FurnitureCatalog.set(emptyList())
        MaterialCatalog.set(emptyList())
    }

    private val S = AssetPriority.Scene
    private fun req(path: String, p: AssetPriority = S) = AssetRequest(path, p)
    private fun maps(id: String, p: AssetPriority = S) = listOf("color", "normal", "orm").map { req("materials/${id}_$it.jpg", p) }
    private val bounds = Bounds(0.0, 0.0, 300.0, 250.0)

    private fun placed(id: Long, model: String) = Scene3D.PlacedFurniture(id, model, Vec3(0.0, 0.0, 0.0), 0.0, 100.0, 50.0, 80.0, false)
    private fun tris(vertices: Int = 3) = FloatArray(vertices * 8)
    private fun scene(furniture: List<Scene3D.PlacedFurniture> = emptyList(), textured: Map<String, FloatArray> = emptyMap(), bounds: Bounds? = null) =
        Scene3D(FloatArray(0), FloatArray(0), emptyList(), emptyList(), bounds, furniture, textured)

    private fun furniture(id: Long, model: String) = Furniture(id, model, Vec2(100.0, 100.0), width = 100.0, depth = 50.0, height = 80.0)
    private fun plan(vararg models: String) = FloorPlan(furniture = models.mapIndexed { i, m -> furniture(i + 1L, m) })
    private fun building(vararg plans: FloorPlan) = Building(plans.mapIndexed { i, p -> Floor(i + 1L, "Piano $i", p) })

    // ---- scena ----

    @Test
    fun `una scena vuota non chiede niente`() {
        assertEquals(emptyList(), AssetPlanner.forScene(scene()))
        assertEquals(emptyList(), AssetPlanner.forScene(scene(bounds = null), environments = emptyList()))
    }

    @Test
    fun `un arredo da il suo modello con priorita Scene`() {
        assertEquals(listOf(req("furniture/ob_1.glb")), AssetPlanner.forScene(scene(listOf(placed(1, "ob_1")))))
    }

    @Test
    fun `un materiale fotografico da tutte e tre le mappe, in ordine alfabetico tra i materiali`() {
        val r = AssetPlanner.forScene(scene(textured = linkedMapOf("parquet" to tris(), "cotto" to tris(6))))
        assertEquals(maps("cotto") + maps("parquet"), r)
        // una superficie con meno di un triangolo non si disegna: niente mappe
        assertEquals(emptyList(), AssetPlanner.forScene(scene(textured = mapOf("vuoto" to FloatArray(0), "due" to tris(2)))))
    }

    @Test
    fun `l'erba c'e quando la scena ha un'estensione e mai da sola come duplicato`() {
        assertEquals(maps("grass005"), AssetPlanner.forScene(scene(bounds = bounds)))
        assertEquals(emptyList(), AssetPlanner.forScene(scene(bounds = null)))
        // gia presente tra i materiali: una sola volta
        val r = AssetPlanner.forScene(scene(textured = mapOf("grass005" to tris()), bounds = bounds))
        assertEquals(maps("grass005"), r)
        assertEquals("grass005", MaterialCatalog.GRASS)
    }

    @Test
    fun `le luci ambiente si chiedono solo se indicate`() {
        assertEquals(emptyList(), AssetPlanner.forScene(scene()))
        assertEquals(listOf(req("env/giorno.ibl"), req("env/tramonto.ibl")), AssetPlanner.forScene(scene(), listOf("giorno", "tramonto")))
        assertEquals(listOf(req("env/giorno.ibl")), AssetPlanner.forScene(scene(), listOf("giorno", "giorno", " ")))
    }

    @Test
    fun `ordine del renderer e nessun duplicato`() {
        val s = scene(
            furniture = listOf(placed(1, "b"), placed(2, "a"), placed(3, "b")),
            textured = mapOf("parquet" to tris()),
            bounds = bounds,
        )
        val r = AssetPlanner.forScene(s, listOf("giorno"))
        assertEquals(
            listOf(req("furniture/b.glb"), req("furniture/a.glb")) + maps("parquet") + maps("grass005") + listOf(req("env/giorno.ibl")),
            r,
        )
        assertEquals(r.size, r.map { it.path }.toSet().size)
        assertTrue(r.all { it.priority == S })
    }

    @Test
    fun `la stessa scena da lo stesso risultato anche con un'altra disposizione dei materiali`() {
        val a = scene(textured = linkedMapOf("a" to tris(), "b" to tris(), "c" to tris()), furniture = listOf(placed(1, "m")), bounds = bounds)
        val b = scene(textured = linkedMapOf("c" to tris(), "a" to tris(), "b" to tris()), furniture = listOf(placed(1, "m")), bounds = bounds)
        assertEquals(AssetPlanner.forScene(a, listOf("giorno")), AssetPlanner.forScene(a, listOf("giorno")))
        assertEquals(AssetPlanner.forScene(a), AssetPlanner.forScene(b))
    }

    @Test
    fun `nomi vuoti o non validi non producono richieste`() {
        val hostile = listOf("", " ", "a/b", "..", "../x", "..\\x", "x\\y")
        val s = scene(furniture = hostile.mapIndexed { i, m -> placed(i + 1L, m) } + placed(99, "valido"), textured = hostile.associateWith { tris() } + ("buono" to tris()))
        val r = AssetPlanner.forScene(s, environments = hostile + "giorno")
        assertEquals(listOf(req("furniture/valido.glb")) + maps("buono") + listOf(req("env/giorno.ibl")), r)
    }

    @Test
    fun `non dipende dai cataloghi caricati`() {
        val s = scene(listOf(placed(1, "ob_1")), mapOf("parquet" to tris()), bounds)
        val empty = AssetPlanner.forScene(s, listOf("giorno"))
        FurnitureCatalog.set(listOf(FurnitureCatalog.Item("ob_1", "Divano", "Soggiorno", width = 200.0, depth = 90.0, height = 80.0)))
        MaterialCatalog.set(listOf(MaterialCatalog.Item("parquet", "Parquet", "Legno")))
        assertEquals(empty, AssetPlanner.forScene(s, listOf("giorno")))
        assertTrue(empty.isNotEmpty())
    }

    // ---- scena costruita davvero dal modello ----

    private val room = Room(1, "Salotto", RoomType.Soggiorno, RoomFactory.rectangle(300.0, 250.0))

    @Test
    fun `da una scena costruita dalla pianta - arredi, tappeti e materiali veri`() {
        MaterialCatalog.set(listOf(MaterialCatalog.Item("parquet", "Parquet", "Legno", size = 100.0), MaterialCatalog.Item("lana", "Lana", "Moquette", size = 100.0, use = "tappeto")))
        val p = FloorPlan(
            rooms = listOf(room.copy(floorMaterial = "parquet")),
            furniture = listOf(furniture(1, "ob_divano"), furniture(2, "tappeto:lana:r"), furniture(3, "ob_divano")),
        )
        val scene = Scene3D.build(p, null, null, ceilings = false)
        val r = AssetPlanner.forScene(scene)
        // il divano (una volta), il materiale del pavimento e quello del tappeto (alfabetico), l'erba; il tappeto non ha modello
        assertEquals(listOf(req("furniture/ob_divano.glb")) + maps("lana") + maps("parquet") + maps("grass005"), r)
    }

    @Test
    fun `un materiale che non e nella libreria non entra nella scena e quindi non si chiede`() {
        MaterialCatalog.set(emptyList()) // "parquet" non esiste
        val scene = Scene3D.build(FloorPlan(listOf(room.copy(floorMaterial = "parquet"))), null, null, ceilings = false)
        assertTrue("parquet" !in scene.textured.keys)
        assertEquals(maps("grass005"), AssetPlanner.forScene(scene))
    }

    // ---- progetto ----

    @Test
    fun `un progetto con piu piani da modelli e viste dall'alto di tutti, una volta sola, Soon`() {
        val b = building(plan("ob_a", "ob_b"), plan("ob_b", "ob_c"))
        val soon = AssetPriority.Soon
        assertEquals(
            listOf(
                req("furniture/ob_a.glb", soon), req("furniture/ob_a_top.png", soon),
                req("furniture/ob_b.glb", soon), req("furniture/ob_b_top.png", soon),
                req("furniture/ob_c.glb", soon), req("furniture/ob_c_top.png", soon),
            ),
            AssetPlanner.forProject(b),
        )
        assertEquals(AssetPlanner.forProject(b), AssetPlanner.forPlans(b.floors.map { it.plan }))
        assertEquals(AssetPlanner.forProject(b), AssetPlanner.forProject(b)) // deterministico
    }

    @Test
    fun `un tappeto di materiale non ha modello ne vista dall'alto - serve il campione del materiale`() {
        val soon = AssetPriority.Soon
        val r = AssetPlanner.forProject(building(plan("tappeto:lana:r", "tappeto:lana:o", "tappeto:juta:r")))
        assertEquals(listOf(req("materials/lana_thumb.jpg", soon), req("materials/juta_thumb.jpg", soon)), r)
    }

    @Test
    fun `solo cio che appartiene al progetto, niente miniature del catalogo`() {
        assertEquals(emptyList(), AssetPlanner.forProject(building(FloorPlan(), FloorPlan())))
        val paths = AssetPlanner.forProject(building(plan("ob_a"))).map { it.path }
        assertTrue("furniture/ob_a.png" !in paths, "la miniatura del catalogo e della UI (Visible)")
        assertEquals(setOf("furniture/ob_a.glb", "furniture/ob_a_top.png"), paths.toSet())
    }

    @Test
    fun `la priorita del progetto si puo scegliere`() {
        val r = AssetPlanner.forProject(building(plan("ob_a")), AssetPriority.Visible)
        assertTrue(r.isNotEmpty() && r.all { it.priority == AssetPriority.Visible })
    }

    @Test
    fun `progetto con nomi vuoti non validi o tappeti senza materiale non produce richieste`() {
        val r = AssetPlanner.forProject(building(plan("", " ", "a/b", "../x", "tappeto::r", "tappeto: :o", "ob_ok")))
        assertEquals(listOf("furniture/ob_ok.glb", "furniture/ob_ok_top.png"), r.map { it.path })
    }

    @Test
    fun `il progetto non dipende dai cataloghi caricati`() {
        val b = building(plan("ob_a", "tappeto:lana:r"))
        val empty = AssetPlanner.forProject(b)
        FurnitureCatalog.set(listOf(FurnitureCatalog.Item("ob_a", "A", "Soggiorno", width = 1.0, depth = 1.0, height = 1.0)))
        MaterialCatalog.set(listOf(MaterialCatalog.Item("lana", "Lana", "Moquette", use = "tappeto")))
        assertEquals(empty, AssetPlanner.forProject(b))
        assertTrue(empty.size == 3)
    }

    // ---- unione ----

    @Test
    fun `combine tiene l'ordine della prima comparsa e la priorita piu alta, qualunque sia l'ordine`() {
        val scene = AssetPlanner.forScene(scene(listOf(placed(1, "ob_b"))))
        val project = AssetPlanner.forProject(building(plan("ob_a", "ob_b")))
        assertEquals(
            listOf(
                req("furniture/ob_a.glb", AssetPriority.Soon), req("furniture/ob_a_top.png", AssetPriority.Soon),
                req("furniture/ob_b.glb", S), req("furniture/ob_b_top.png", AssetPriority.Soon),
            ),
            AssetPlanner.combine(project, scene),
        )
        assertEquals(AssetPlanner.combine(project, scene).map { it.path }.toSet(), AssetPlanner.combine(scene, project).map { it.path }.toSet())
        assertEquals(S, AssetPlanner.combine(scene, project).first { it.path == "furniture/ob_b.glb" }.priority)
        val p = "furniture/x.glb"
        val all = AssetPriority.entries
        for (a in all) for (b in all) {
            assertEquals(all[minOf(a.ordinal, b.ordinal)], AssetPlanner.combine(listOf(req(p, a)), listOf(req(p, b))).single().priority)
        }
        assertEquals(emptyList(), AssetPlanner.combine())
        assertEquals(emptyList(), AssetPlanner.combine(listOf(req("../x"), req("/abs"))))
    }
}
