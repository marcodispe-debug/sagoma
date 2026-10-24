package com.sagoma.planimetria.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LengthUnit
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.persistence.ProjectRepository
import com.sagoma.planimetria.reading.PlanReader
import com.sagoma.planimetria.reading.PlanReaderResult
import com.sagoma.planimetria.reading.PlanReading
import com.sagoma.planimetria.testi.T
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Leggi la piantina da sola": dalla risposta del server alle stanze nella pianta, sopra lo sfondo già salvato. */
class ReadPlanFlowTest {
    /** Due stanze lette sulla copia piccola (1000 × 500): 2 cm per pixel, 4,00 × 3,00 e 3,00 × 3,00. */
    private val reading = PlanReading(
        image = PlanReading.ImageSize(1000, 500),
        rooms = listOf(
            PlanReading.ReadRoom("Cucina", listOf(listOf(100.0, 100.0), listOf(300.0, 100.0), listOf(300.0, 250.0), listOf(100.0, 250.0)), listOf(4.0, 3.0)),
            PlanReading.ReadRoom("Camera", listOf(listOf(306.0, 100.0), listOf(456.0, 100.0), listOf(456.0, 250.0), listOf(306.0, 250.0)), listOf(3.0, 3.0)),
        ),
        openings = listOf(PlanReading.ReadOpening("door", listOf(listOf(300.0, 150.0), listOf(306.0, 190.0)))),
        units = "m",
    )

    private class FakeReader(val result: PlanReaderResult) : PlanReader {
        var sent: Triple<Int, Int, Int>? = null
        override suspend fun read(image: ByteArray, mediaType: String, width: Int, height: Int): PlanReaderResult {
            sent = Triple(image.size, width, height)
            return result
        }
    }

    private class FakePlatform : Platform {
        val toasts = mutableListOf<String>()
        var asked = 0
        override val assetStore: com.sagoma.planimetria.assets.AssetStore get() = error("non serve")
        override val projects: ProjectRepository get() = error("non serve")
        override val tips: TipStore get() = error("non serve")
        override fun toast(message: String) { toasts += message }
        override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {}
        override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) {}
        override fun shareText(subject: String, text: String) {}
        override fun decodeImage(bytes: ByteArray): ImageBitmap = error("non serve")
        override fun encodePng(image: ImageBitmap): ByteArray? = null
        // La copia per il server: 1000 × 500 (lo sfondo salvato è 3000 × 1500).
        override suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage {
            asked = maxSide
            return ImportedImage(ByteArray(7), 1000, 500)
        }
        override fun createPdf(pages: List<PdfPage>, density: Density): ByteArray? = null
        override fun formatDate(epochMillis: Long, withTime: Boolean): String = ""
        override fun now(): Long = 0
        override fun createSceneRenderer(): SceneRenderer = error("non serve")
    }

    private fun vm(plan: FloorPlan = FloorPlan()) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(plan)
        override fun save(building: Building) {}
    })

    private val background = ImportedImage(ByteArray(10), 3000, 1500)
    private val file = PickedFile("piantina.jpg", ByteArray(3))

    @Test
    fun stanzeLetteSulloSfondoTarato() = runBlocking {
        val platform = FakePlatform()
        val reader = FakeReader(PlanReaderResult.Ok(reading))
        val vm = vm()
        assertTrue(readPlan(platform, reader, file, false, background, "sfondo-1.jpg", vm))
        assertEquals(Triple(7, 1000, 500), reader.sent)
        assertEquals(PlanReader.sideFor(3000, 1500), platform.asked)
        val plan = vm.state.value.plan
        assertEquals(listOf("Cucina", "Camera"), plan.rooms.map { it.name })
        assertEquals(1, plan.rooms.sumOf { it.openings.size })
        // Sfondo: il file già salvato, nei suoi pixel (3000 × 1500), tarato: 2 cm per pixel della copia = 2/3 cm per pixel suo.
        val u = plan.underlay!!
        assertEquals("sfondo-1.jpg", u.file)
        assertEquals(3000, u.widthPx)
        assertEquals(2.0 / 3, u.cmPerPx, 1e-9)
        assertEquals(LengthUnit.Meters, vm.state.value.building.units)
        assertEquals(T.sfondo.piantinaLetta(2), platform.toasts.single())
        // Annulla: si torna alla pianta vuota in un passo.
        vm.undo()
        assertTrue(vm.state.value.plan.rooms.isEmpty())
    }

    @Test
    fun rifiutiEServerAssente() = runBlocking {
        val platform = FakePlatform()
        val vm = vm()
        assertFalse(readPlan(platform, FakeReader(PlanReaderResult.Refused("troppe_letture", null)), file, false, background, "s.jpg", vm))
        assertFalse(readPlan(platform, FakeReader(PlanReaderResult.Refused("non_una_piantina", null)), file, false, background, "s.jpg", vm))
        assertFalse(readPlan(platform, FakeReader(PlanReaderResult.Unreachable), file, false, background, "s.jpg", vm))
        assertEquals(listOf(T.sfondo.troppeLetture, T.sfondo.nonUnaPiantina, T.sfondo.letturaNonRaggiungibile), platform.toasts)
        assertTrue(vm.state.value.plan.rooms.isEmpty())
    }

    @Test
    fun nonSovrascriveUnaPiantaGiaIniziata() = runBlocking {
        val platform = FakePlatform()
        val vm = vm(FloorPlan(rooms = listOf(Room(1, "Bagno", RoomType.Bagno, RoomFactory.rectangle(200.0, 200.0)))))
        assertFalse(readPlan(platform, FakeReader(PlanReaderResult.Ok(reading)), file, false, background, "s.jpg", vm))
        assertEquals(listOf("Bagno"), vm.state.value.plan.rooms.map { it.name })
    }
}
