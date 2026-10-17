package com.sagoma.planimetria.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.sagoma.planimetria.editor.CreationIntent
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.persistence.ProjectRepository
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.scan.ArXZ
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Percorso B nel ViewModel: "Conosci le misure?" → schizzo (forma e misure indicative) che resta DA MISURARE anche dopo la
 * riapertura ([Room.sketchPending]); la scansione parte solo con un tasto e aggiorna la stessa stanza in un solo passo
 * annullabile. Percorso A ("conosco le misure") invariato.
 */
class SketchFlowTest {
    private fun vm(b: Building = Building.single(FloorPlan())) = EditorViewModel(object : PlanStore {
        override fun load() = b
        override fun save(building: Building) {}
    })

    /** Rettangolo 4 × 3 m creato dal menu delle forme dopo la risposta [intent] (null = domanda non posta, come in Free). */
    private fun EditorViewModel.rectangle(intent: CreationIntent?) {
        setCreationIntent(intent)
        pickShape(RoomShape.Rectangle)
        createRoom(RoomType.Camera, ShapeDimensions(400.0, 300.0), 270.0)
    }

    private fun EditorViewModel.drawRectangle(intent: CreationIntent?) {
        setCreationIntent(intent)
        startDrawWalls()
        for (p in listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0))) drawWallsTap(p)
        drawWallsClose()
        finishDrawWalls(RoomType.Camera, 270.0, Room.WALL_THICKNESS)
    }

    @Test
    fun `conosco le misure - stanza normale, nessuno schizzo, nessuna scansione`() {
        for (intent in listOf(null, CreationIntent.EXACT)) {
            val vm = vm()
            vm.rectangle(intent)
            val s = vm.state.value
            val room = s.plan.rooms.single()
            assertFalse(room.sketchPending)
            assertNull(s.sketchScanRoomId)
            assertNull(s.creation)
            assertNull(s.creationIntent)
            vm.startSketchScan(room.id) // il tasto non c'è; anche chiamandolo non si apre
            assertNull(vm.state.value.sketchScanRoomId)
        }
    }

    @Test
    fun `la risposta vale solo nella scelta della forma e annullare la azzera`() {
        val vm = vm()
        vm.setCreationIntent(CreationIntent.APPROX_SCAN)
        assertEquals(CreationIntent.APPROX_SCAN, vm.state.value.creationIntent)
        vm.pickShape(RoomShape.Rectangle)
        vm.setCreationIntent(CreationIntent.EXACT) // fuori dalla scelta della forma: ignorato
        assertEquals(CreationIntent.APPROX_SCAN, vm.state.value.creationIntent)
        vm.backToShapePicker()
        vm.setCreationIntent(null) // "Indietro" dal menu delle forme: si torna alla domanda
        assertNull(vm.state.value.creationIntent)
        vm.setCreationIntent(CreationIntent.APPROX)
        vm.cancelCreation() // la prima stanza non si può annullare: resta tutto
        assertEquals(CreationIntent.APPROX, vm.state.value.creationIntent)
        vm.startAddRoom()
        assertNull(vm.state.value.creationIntent) // ogni nuova stanza riparte dalla domanda
        vm.setCreationIntent(CreationIntent.APPROX)
        vm.cancelCreation()
        assertNull(vm.state.value.creationIntent)
    }

    @Test
    fun `approssimative + scansione - schizzo da misurare, si torna all'editor, la scansione non parte da sola`() {
        val vm = vm()
        vm.rectangle(CreationIntent.APPROX_SCAN)
        val s = vm.state.value
        val room = s.plan.rooms.single() // una sola stanza
        assertTrue(room.sketchPending)
        assertNull(s.sketchScanRoomId) // prima porte e finestre, poi il tasto
        assertNull(s.creation)
        assertNull(s.creationIntent)
        assertFalse(room.id in s.dismissedSketchBanners) // l'avviso con "Avvia scansione" è visibile subito
        vm.startSketchScan(room.id)
        assertEquals(room.id, vm.state.value.sketchScanRoomId)
        assertEquals(listOf(room), vm.state.value.plan.rooms)
    }

    @Test
    fun `solo approssimative - schizzo da misurare senza avviso ora, scansione possibile dopo`() {
        val vm = vm()
        vm.rectangle(CreationIntent.APPROX)
        val s = vm.state.value
        val room = s.plan.rooms.single()
        assertTrue(room.sketchPending) // misurabile più tardi: tasto nella scheda, avviso alla riapertura
        assertNull(s.sketchScanRoomId)
        assertTrue(room.id in s.dismissedSketchBanners)
        vm.startSketchScan(room.id)
        assertEquals(room.id, vm.state.value.sketchScanRoomId)
    }

    @Test
    fun `avviso e tasto restano dopo chiusura e riapertura perche lo schizzo e salvato nel progetto`() {
        val vm = vm()
        vm.rectangle(CreationIntent.APPROX_SCAN)
        val room = vm.state.value.plan.rooms.single()
        vm.dismissSketchRoom(room.id)
        assertTrue(room.id in vm.state.value.dismissedSketchBanners)
        // Chiusura e riapertura: si rilegge il progetto dal testo salvato.
        val reopened = vm(PlanJson.decodeBuilding(PlanJson.encode(vm.state.value.fullBuilding)))
        assertTrue(reopened.state.value.plan.rooms.single().sketchPending)
        assertTrue(reopened.state.value.dismissedSketchBanners.isEmpty()) // l'avviso torna
        reopened.startSketchScan(room.id)
        assertEquals(room.id, reopened.state.value.sketchScanRoomId)
    }

    @Test
    fun `muro per muro - schizzo solo senza le misure`() {
        for ((intent, sketch) in listOf(null to false, CreationIntent.EXACT to false, CreationIntent.APPROX to true, CreationIntent.APPROX_SCAN to true)) {
            val vm = vm()
            vm.drawRectangle(intent)
            val s = vm.state.value
            assertEquals(sketch, s.plan.rooms.single().sketchPending, "$intent")
            assertNull(s.sketchScanRoomId)
            assertNull(s.creationIntent)
        }
    }

    @Test
    fun `annullare la scansione conserva lo schizzo da misurare e si puo ripartire`() {
        val vm = vm()
        vm.rectangle(CreationIntent.APPROX_SCAN)
        val room = vm.state.value.plan.rooms.single()
        vm.startSketchScan(room.id)
        // Uscita, Indietro, permesso negato o ARCore assente: tutti passano da cancelSketchScan.
        vm.cancelSketchScan()
        val s = vm.state.value
        assertNull(s.sketchScanRoomId)
        assertEquals(listOf(room), s.plan.rooms) // stesso schizzo, stesso id, ancora da misurare, nessun duplicato
        vm.startSketchScan(room.id)
        assertEquals(room.id, vm.state.value.sketchScanRoomId)
        // "Chiudi" sull'avviso: lo schizzo resta, ancora da misurare (tasto nella scheda).
        vm.cancelSketchScan(); vm.dismissSketchRoom(room.id)
        assertEquals(listOf(room), vm.state.value.plan.rooms)
    }

    @Test
    fun `la scansione guidata non si apre su stanze create con le misure`() {
        val vm = vm()
        vm.pickShape(RoomShape.Rectangle)
        vm.createRoom(RoomType.Camera, ShapeDimensions(400.0, 300.0), 270.0)
        val room = vm.state.value.plan.rooms.single()
        vm.startSketchScan(room.id)
        assertNull(vm.state.value.sketchScanRoomId)
    }

    /** Angoli misurati (mondo AR, filo interno, ordine dei muri) di una stanza 4,5 × 3 m. */
    private fun measured() = RoomFactory.rectangle(450.0, 300.0).map { ArXZ(it.x / 100 + 3.0, it.y / 100 - 1.0) }

    @Test
    fun `applicare aggiorna la stessa stanza in un solo passo annullabile`() {
        val vm = vm()
        vm.rectangle(CreationIntent.APPROX_SCAN)
        val before = vm.state.value.plan.rooms.single()
        vm.startSketchScan(before.id)
        assertTrue(vm.applySketchScan(before.id, measured()))
        val after = vm.state.value.plan.rooms.single()
        assertEquals(before.id, after.id)
        assertEquals(before.wallCount, after.wallCount)
        assertTrue(before.points != after.points)
        assertFalse(after.sketchPending) // misurata: niente più avviso né tasto
        assertNull(vm.state.value.sketchScanRoomId)
        vm.undo()
        assertEquals(before, vm.state.value.plan.rooms.single()) // un solo passo riporta allo schizzo, di nuovo da misurare
        assertTrue(vm.state.value.plan.rooms.single().sketchPending)
    }

    /** Schizzo con porta, finestra e un calorifero a parete, già nella pianta. */
    private fun sketchWithOpenings(): Room {
        val pts = com.sagoma.planimetria.geometry.Polygon.fromInterior(RoomFactory.rectangle(400.0, 300.0), Room.WALL_THICKNESS)
        return Room(
            id = 3, name = "Studio", type = RoomType.Altro, points = pts, sketchPending = true,
            openings = listOf(
                com.sagoma.planimetria.model.Opening.default(10, com.sagoma.planimetria.model.OpeningKind.Door, 0, 100.0),
                com.sagoma.planimetria.model.Opening.default(11, com.sagoma.planimetria.model.OpeningKind.Window2, 1, 150.0),
            ),
            fixtures = listOf(com.sagoma.planimetria.model.Fixture(20, com.sagoma.planimetria.model.FixtureKind.Radiator, wallIndex = 2, position = 120.0, length = 80.0, height = 60.0)),
        )
    }

    @Test
    fun `un solo annullamento ripristina l'intera stanza con aperture e impianti`() {
        val original = sketchWithOpenings()
        val vm = vm(Building.single(FloorPlan(rooms = listOf(original))))
        vm.startSketchScan(original.id)
        assertTrue(vm.applySketchScan(original.id, measured(), widthOverrides = mapOf(11L to 100.0)))
        val after = vm.state.value.plan.rooms.single()
        assertTrue(after != original)
        assertEquals(100.0, after.openings.single { it.id == 11L }.width) // larghezza corretta nella revisione
        assertTrue(after.fixtures.single().position != original.fixtures.single().position)
        vm.undo()
        assertEquals(original, vm.state.value.plan.rooms.single()) // TUTTO l'oggetto Room: muri, aperture, impianti
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `un risultato non valido non modifica stanza ne cronologia`() {
        val original = sketchWithOpenings()
        val vm = vm(Building.single(FloorPlan(rooms = listOf(original))))
        val before = vm.state.value
        // Finestra da 120 cm su un muro che diventa di 90 cm: blocca, niente di parziale.
        val narrow = RoomFactory.rectangle(450.0, 90.0).map { ArXZ(it.x / 100, it.y / 100) }
        assertFalse(vm.applySketchScan(original.id, narrow))
        // Larghezza corretta oltre il muro: non vale, quindi blocca ancora.
        assertFalse(vm.applySketchScan(original.id, narrow, widthOverrides = mapOf(11L to 200.0)))
        // Angoli che si incrociano: nessuna stanza.
        assertFalse(vm.applySketchScan(original.id, measured().let { listOf(it[0], it[2], it[1], it[3]) }))
        assertEquals(before.plan, vm.state.value.plan)
        assertEquals(original, vm.state.value.plan.rooms.single())
        assertEquals(before.canUndo, vm.state.value.canUndo)
    }

    @Test
    fun `angoli non validi non cambiano niente`() {
        val vm = vm()
        vm.pickShape(RoomShape.Rectangle)
        vm.createRoom(RoomType.Camera, ShapeDimensions(400.0, 300.0), 270.0)
        val before = vm.state.value
        val room = before.plan.rooms.single()
        assertFalse(vm.applySketchScan(room.id, measured().take(3)))
        assertEquals(before.plan, vm.state.value.plan)
        assertEquals(before.canUndo, vm.state.value.canUndo)
        assertFalse(vm.applySketchScan(999L, measured()))
    }

    // ---------- Disponibilità della funzione (separata da lingua e testi) ----------

    private class FakePlatform(private val scanner: RoomScanner?) : Platform {
        override val assetStore: com.sagoma.planimetria.assets.AssetStore get() = error("non serve")
        override val roomScanner: RoomScanner? get() = scanner
        override val projects: ProjectRepository get() = error("non serve")
        override val tips: TipStore get() = error("non serve")
        override fun toast(message: String) {}
        override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {}
        override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) {}
        override fun shareText(subject: String, text: String) {}
        override fun decodeImage(bytes: ByteArray): ImageBitmap = error("non serve")
        override fun encodePng(image: ImageBitmap): ByteArray? = null
        override suspend fun importImage(bytes: ByteArray, pdf: Boolean, maxSide: Int): ImportedImage? = null
        override fun createPdf(pages: List<PdfPage>, density: Density): ByteArray? = null
        override fun formatDate(epochMillis: Long, withTime: Boolean): String = ""
        override fun now(): Long = 0
        override fun createSceneRenderer(): SceneRenderer = error("non serve")
    }

    private object NoScanner : RoomScanner {
        @androidx.compose.runtime.Composable
        override fun Screen(onResult: (com.sagoma.planimetria.scan.ScanResult?) -> Unit) {}
    }

    @Test
    fun `la funzione c'e dove c'e lo scanner, per tutti gli account`() {
        assertFalse(FeatureAccess.isEnabled(FakePlatform(scanner = null), AppFeature.SKETCH_GUIDED_SCAN))
        assertTrue(FeatureAccess.isEnabled(FakePlatform(scanner = NoScanner), AppFeature.SKETCH_GUIDED_SCAN))
    }
}
