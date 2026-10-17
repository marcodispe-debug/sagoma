package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Compatibilità dei progetti con [Room.sketchPending] (schizzo da misurare, percorso B): i file scritti prima si leggono uguali
 * (nessuno schizzo) e si riscrivono identici, carattere per carattere; il valore vero si salva e si rilegge.
 */
class SketchPendingCompatTest {
    /** Progetto salvato dalla versione precedente (versione 2 del formato): una camera con porta e finestra. */
    private val oldFile = """{"version":2,"building":{"floors":[{"id":1,"name":"Piano terra","plan":{"rooms":[{"id":1,"name":"Camera","type":"Camera","points":[{"x":0.0,"y":0.0},{"x":400.0,"y":0.0},{"x":400.0,"y":300.0},{"x":0.0,"y":300.0}],"openings":[{"id":2,"kind":"Door","wallIndex":0,"position":100.0,"width":80.0,"height":210.0},{"id":3,"kind":"Window2","wallIndex":1,"position":150.0,"width":120.0,"height":120.0,"sillHeight":90.0}]}]}}]}}"""

    @Test
    fun `un progetto vecchio si legge senza schizzi`() {
        val room = PlanJson.decodeBuilding(oldFile).floor.plan.rooms.single()
        assertFalse(room.sketchPending)
        assertEquals(2, room.openings.size)
    }

    @Test
    fun `un progetto vecchio si riscrive identico`() {
        assertEquals(oldFile, PlanJson.encode(PlanJson.decodeBuilding(oldFile)))
    }

    @Test
    fun `una stanza normale non scrive il campo`() {
        val b = Building.single(FloorPlan(rooms = listOf(Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)))))
        assertFalse("sketchPending" in PlanJson.encode(b))
    }

    @Test
    fun `lo schizzo da misurare si salva e si rilegge, e dopo la misura sparisce dal file`() {
        val sketch = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0), sketchPending = true)
        val text = PlanJson.encode(Building.single(FloorPlan(rooms = listOf(sketch))))
        assertTrue("\"sketchPending\":true" in text)
        val back = PlanJson.decodeBuilding(text).floor.plan.rooms.single()
        assertEquals(sketch, back)
        assertEquals(text, PlanJson.encode(PlanJson.decodeBuilding(text)))
        val measured = PlanJson.encode(Building.single(FloorPlan(rooms = listOf(back.copy(sketchPending = false)))))
        assertFalse("sketchPending" in measured)
    }

    @Test
    fun `campi sconosciuti accanto allo schizzo non impediscono la lettura`() {
        // Stesso meccanismo (ignoreUnknownKeys) con cui la versione precedente legge "sketchPending" senza conoscerlo: per lei
        // la stanza è normale e, se riscrive il file, il segno di schizzo si perde (la stanza resta intatta).
        val text = PlanJson.encode(Building.single(FloorPlan(rooms = listOf(Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0), sketchPending = true)))))
        val withUnknown = text.replace("\"sketchPending\":true", "\"sketchPending\":true,\"campoFuturo\":1")
        assertTrue(PlanJson.decodeBuilding(withUnknown).floor.plan.rooms.single().sketchPending)
    }
}
