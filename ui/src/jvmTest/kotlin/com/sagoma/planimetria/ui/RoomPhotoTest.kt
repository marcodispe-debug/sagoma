package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.RoomMerge
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomPhoto
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.persistence.RoomPhotoFiles
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Foto della stanza: si aggiungono, si annotano, si tolgono (con Annulla), si salvano e i file non più usati si cancellano. */
class RoomPhotoTest {
    private val room = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(400.0, 300.0))
    private val photo = RoomPhoto("foto-1.jpg", "foto-1-mini.jpg", taken = 1)

    private fun vm(r: Room = room) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(r)))
        override fun save(building: Building) {}
    })

    @Test
    fun `aggiungere, annotare e togliere una foto si annulla`() {
        val vm = vm()
        vm.addRoomPhoto(1, photo)
        vm.setRoomPhotoNote(1, photo.file, "  presa a 30 cm  ")
        assertEquals(listOf(photo.copy(note = "presa a 30 cm")), vm.state.value.plan.room(1)!!.photos)
        vm.removeRoomPhoto(1, photo.file)
        assertTrue(vm.state.value.plan.room(1)!!.photos.isEmpty())
        vm.undo()
        assertEquals("presa a 30 cm", vm.state.value.plan.room(1)!!.photos.single().note)
    }

    @Test
    fun `le foto si salvano e i file vecchi restano leggibili`() {
        val withPhoto = room.copy(photos = listOf(photo.copy(note = "parete nord")))
        val text = PlanJson.encode(FloorPlan(listOf(withPhoto)))
        assertEquals(withPhoto, PlanJson.decode(text).rooms.single())
        // Senza foto il campo non si scrive: i file delle stanze senza foto restano identici a prima.
        assertFalse("photos" in PlanJson.encode(FloorPlan(listOf(room))))
    }

    @Test
    fun `unendo due stanze le foto restano tutte`() {
        val a = room.copy(photos = listOf(photo))
        val b = Room(2, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })
            .copy(photos = listOf(RoomPhoto("foto-2.jpg", "foto-2-mini.jpg")))
        val plan = FloorPlan(listOf(a, b))
        val wall = (0 until a.wallCount).first { RoomMerge.mergeablePartners(plan, 1, it).isNotEmpty() }
        val merged = RoomMerge.merge(plan, 1, wall, 2)?.plan?.room(1)
        assertEquals(listOf("foto-1.jpg", "foto-2.jpg"), merged?.photos?.map { it.file })
    }

    @Test
    fun `all'apertura si cancellano solo le foto che nessuna stanza usa`() {
        val dir = Files.createTempDirectory("sagoma-foto").toFile()
        try {
            val repo = FileProjectRepository(dir)
            val p = repo.create("Prova")
            for (n in listOf("foto-1.jpg", "foto-1-mini.jpg", "foto-9.jpg", "foto-9-mini.jpg", "sfondo-5.jpg")) repo.writeFile(p.id, n, byteArrayOf(1))
            val building = Building.single(FloorPlan(listOf(room.copy(photos = listOf(photo)))))
            repo.planStore(p.id).save(building)
            RoomPhotoFiles.prune(repo, p.id, building)
            assertEquals(setOf("foto-1.jpg", "foto-1-mini.jpg", "sfondo-5.jpg"), repo.files(p.id).toSet())
        } finally {
            dir.deleteRecursively()
        }
    }
}
