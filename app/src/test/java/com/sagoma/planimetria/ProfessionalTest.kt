package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.FileProjectRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

/** Progetti (archivio, varianti, esporta/importa), computo metrico e pianta di sfondo. */
class ProfessionalTest {
    @get:Rule val tmp = TemporaryFolder()

    // 500 × 400 in mezzeria, muri da 15: dentro 485 × 385, alta 270.
    private val room = Room(
        1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0),
        floorFinish = FloorFinish.OakParquet,
        openings = listOf(
            Opening.default(1, OpeningKind.Door, 0, 250.0),     // 80 × 210
            Opening.default(2, OpeningKind.Window2, 1, 200.0),  // 120 × 120, davanzale 90
        ),
    )

    @Test
    fun `computo di una stanza`() {
        val res = Takeoff.of(Building.single(FloorPlan(listOf(room))))
        val l = res.rooms.single()
        assertEquals(4.85 * 3.85, l.floorArea, 1e-6)
        assertEquals(17.4, l.perimeter, 1e-6)
        assertEquals(17.4 * 2.7 - 0.8 * 2.1 - 1.2 * 1.2, l.wallArea, 1e-6)
        // Il battiscopa si ferma davanti alla porta, non sotto la finestra.
        assertEquals(17.4 - 0.8, l.skirting, 1e-6)
        assertEquals(1, l.doors)
        assertEquals(1, l.windows)
        assertEquals("Parquet rovere", res.floorMaterials.single().what)
        assertEquals(l.wallArea, res.wallFinishes.sumOf { it.quantity }, 1e-6)
        val csv = Takeoff.csv(res)
        assertTrue(csv.contains("Soggiorno;18,67;17,40;"))
    }

    @Test
    fun `rivestimento fino a 120 cm e pittura sopra`() {
        val tiled = room.copy(wallFinish = WallFinish(0xFFF0EEE8, covering = Covering.Tiles))
        val walls = Takeoff.of(Building.single(FloorPlan(listOf(tiled)))).wallFinishes.associate { it.what to it.quantity }
        // Sotto 120: porta intera per 120 cm, finestra (davanzale 90) per 30 cm.
        assertEquals(17.4 * 1.2 - 0.8 * 1.2 - 1.2 * 0.3, walls.getValue("Piastrelle 20×20 fino a 120 cm"), 1e-6)
        assertEquals(17.4 * 1.5 - 0.8 * 0.9 - 1.2 * 0.9, walls.getValue("Pittura bianco"), 1e-6)
    }

    @Test
    fun `lista arredi raggruppa i modelli uguali`() {
        val chair = Furniture(1, "sedia-test", Vec2(100.0, 100.0), width = 45.0, depth = 50.0, height = 90.0)
        val plan = FloorPlan(listOf(room), furniture = listOf(chair, chair.copy(id = 2, center = Vec2(200.0, 100.0)), chair.copy(id = 3, width = 60.0)))
        val items = Takeoff.of(Building.single(plan)).furniture
        assertEquals(2, items.size)
        assertEquals(2, items.first { it.size.startsWith("45") }.count)
        assertEquals("Soggiorno", items.first().where)
    }

    @Test
    fun `il primo avvio porta la casa vecchia nel progetto 1`() {
        val root = tmp.newFolder()
        val plan = PlanJson.encode(FloorPlan(listOf(room)))
        File(root, "planimetria.json").writeText(plan)
        val store = FileProjectRepository(root)
        assertEquals(1L, store.lastOpened)
        assertEquals("La mia casa", store.list().single().name)
        assertEquals(plan, File(store.folder(1), "planimetria.json").readText())
        assertEquals(room, store.planStore(1).load()?.floor?.plan?.rooms?.single())
        // Una seconda apertura non ricrea nulla.
        assertEquals(1, FileProjectRepository(root).list().size)
    }

    @Test
    fun `duplica, esporta e importa un progetto`() {
        val store = FileProjectRepository(tmp.newFolder())
        store.planStore(1).save(Building.single(FloorPlan(listOf(room))))
        store.writeFile(1, "sfondo-1.jpg", byteArrayOf(1, 2, 3))
        store.update(store.info(1)!!.copy(name = "Casa Rossi", client = "Mario Rossi"))

        val copy = store.duplicate(1, "Casa Rossi – variante B")!!
        assertArrayEquals(byteArrayOf(1, 2, 3), store.readFile(copy.id, "sfondo-1.jpg"))

        val imported = store.import(store.export(1)!!)
        assertNotNull(imported)
        assertEquals("Casa Rossi", imported!!.name)
        assertEquals("Mario Rossi", imported.client)
        assertEquals(3, store.list().size)
        assertArrayEquals(byteArrayOf(1, 2, 3), store.readFile(imported.id, "sfondo-1.jpg"))
        assertEquals(room, store.planStore(imported.id).load()?.floor?.plan?.rooms?.single())
        // I nomi con cartelle non escono dalla cartella del progetto.
        assertNull(store.readFile(1, "../index.json"))

        // Un file qualsiasi non diventa un progetto.
        assertNull(store.import("ciao".encodeToByteArray()))
        assertEquals(3, store.list().size)

        store.delete(copy.id)
        assertEquals(2, store.list().size)
    }

    @Test
    fun `lo sfondo si salva con la pianta`() {
        val u = Underlay("sfondo-1.jpg", 3000, 2000, origin = Vec2(-100.0, 50.0), cmPerPx = 0.5, opacity = 0.4)
        val plan = FloorPlan(listOf(room), underlay = u)
        assertEquals(u, PlanJson.decode(PlanJson.encode(plan)).underlay)
        assertEquals(1500.0, u.widthCm, 1e-9)
    }
}
