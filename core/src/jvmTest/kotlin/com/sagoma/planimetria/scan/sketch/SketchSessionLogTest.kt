package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.assisted.AssistedScene
import com.sagoma.planimetria.scan.assisted.AssistedState
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Registro degli eventi della scansione guidata: file laterale della registrazione, che resta invariata e compatibile. */
class SketchSessionLogTest {
    private val sketch = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)))
    private fun line(ax: Double, az: Double, bx: Double, bz: Double) =
        ConfirmedLine(ArXZ(ax, az), ArXZ(bx, bz), ArXZ(0.0, 1.0), 0.005, 0.5, supportAlongM = listOf(0.1, 0.2, 0.3))

    private val fresh = SketchSession(sketch, listOf(SketchOpening(2, OpeningMark.DOOR)))
    private val squareKey = GeometryIssue.keyOf(IssueCode.ANGLES_NEAR_SQUARE, emptyList())

    private fun at(s: SketchSession, ms: Long) = s.copy(engine = AssistedState.initial().copy(lastTimeMs = ms))

    private val scanned = at(
        fresh.copy(
            startSide = 0, startConfirmed = true,
            assigned = mapOf(
                0 to AssignedWall(1, line(0.0, 0.0, 3.0, 0.0), 1_000, emptyList()),
                1 to AssignedWall(2, line(4.0, 0.5, 4.0, 2.5), 2_500, emptyList()),
            ),
            answers = mapOf(squareKey to Answer.SQUARE_ANGLES),
            manualLengthsM = mapOf(2 to 4.02),
            startDeclaration = StartDeclaration(StartReference.DOOR, "porta del bagno"),
        ),
        4_000,
    )

    @Test
    fun `dallo stato iniziale si scrive tutto cio che l'utente ha fatto, con gli istanti delle conferme`() {
        val issues = listOf(GeometryIssue(IssueCode.ANGLES_NEAR_SQUARE, emptyList(), mapOf("spostamentoMaxM" to 0.043, "scartoMaxDeg" to 2.4)))
        val e = SketchSessionLog.diff(fresh, scanned, issues)
        assertEquals(listOf("START_SIDE", "WALL_ASSIGNED", "WALL_ASSIGNED", "ANSWER", "START_DECLARATION", "MANUAL_LENGTH"), e.map { it.kind })
        val walls = e.filter { it.kind == "WALL_ASSIGNED" }
        assertEquals(listOf(0, 1), walls.map { it.side })
        assertEquals(listOf(1, 2), walls.map { it.wallId })
        assertEquals(listOf(1_000L, 2_500L), walls.map { it.sessionMs }) // istante della conferma, non della scrittura
        assertEquals(listOf(0.0, 0.0, 3.0, 0.0, 0.0, 1.0), walls[0].line)
        assertEquals(3.0, walls[0].value) // punti di sostegno della parete
        // Dall'evento si rifà la stessa retta che ha usato il nucleo (anche i punti di sostegno): pianta riproducibile offline.
        assertEquals(scanned.assigned.getValue(0).line, SketchSessionLog.confirmedLine(walls[0]))
        assertEquals(scanned.assigned.getValue(1).line, SketchSessionLog.confirmedLine(walls[1]))
        val answer = e.single { it.kind == "ANSWER" }
        assertEquals(squareKey, answer.key)
        assertEquals("SQUARE_ANGLES", answer.answer)
        assertEquals(0.043, answer.values!!.getValue("spostamentoMaxM")) // lo spostamento che l'utente ha visto
        assertEquals("DOOR", e.single { it.kind == "START_DECLARATION" }.key)
        assertEquals(4.02, e.single { it.kind == "MANUAL_LENGTH" }.value)
        // Senza cambiamenti nessun evento.
        assertTrue(SketchSessionLog.diff(scanned, at(scanned, 9_000)).isEmpty())
    }

    @Test
    fun `parete spostata su un altro lato e risposta tolta`() {
        val moved = at(scanned.copy(assigned = mapOf(3 to scanned.assigned.getValue(0), 1 to scanned.assigned.getValue(1)), answers = emptyMap()), 6_000)
        val e = SketchSessionLog.diff(scanned, moved)
        val removed = e.single { it.kind == "WALL_REMOVED" }
        assertEquals(0, removed.side)
        val reassigned = e.single { it.kind == "WALL_ASSIGNED" }
        assertEquals(3, reassigned.side)
        assertEquals(6_000L, reassigned.sessionMs) // istante dello spostamento, non della conferma originale
        val cleared = e.single { it.kind == "ANSWER" }
        assertNull(cleared.answer)
    }

    @Test
    fun `i blocchi si riassumono in modo stabile e solo i blocchi`() {
        val issues = listOf(
            GeometryIssue(IssueCode.OBSERVED_BEYOND_CORNER, listOf(0), mapOf("oltreM" to 0.12357, "angolo" to 0.0)),
            GeometryIssue(IssueCode.VERTEX_UNCERTAIN, listOf(0, 1), mapOf("sigmaM" to 0.06)), // avviso: non conta
            GeometryIssue(IssueCode.ANGLES_NEAR_SQUARE, emptyList(), mapOf("spostamentoMaxM" to 0.07)), // domanda: non conta
        )
        assertEquals(listOf("OBSERVED_BEYOND_CORNER[0] angolo=0.0 oltreM=0.124"), SketchSessionLog.blockingSummary(issues))
        assertEquals(emptyList(), SketchSessionLog.blockingSummary(issues.drop(1)))
        val e = SketchSessionLog.blocking(scanned, SketchSessionLog.blockingSummary(issues))
        assertEquals(e, SketchSessionLog.parse(SketchSessionLog.encode(e)).second.single())
    }

    @Test
    fun `scrittura e lettura, righe sconosciute o rovinate si saltano`() {
        val header = SketchSessionLog.header(fresh)
        assertEquals(listOf("2:DOOR"), header.openings)
        val events = SketchSessionLog.diff(fresh, scanned).mapIndexed { i, e -> e.copy(seq = i, arTimestampNs = 1_000_000_000L + i) } +
            SketchSessionLog.applied(scanned, listOf(ArXZ(0.0, 0.0), ArXZ(4.0, 0.0), ArXZ(4.0, 3.0), ArXZ(0.0, 3.0)))
        val text = buildString {
            appendLine(SketchSessionLog.encode(header))
            appendLine("{\"type\":\"futuro\",\"x\":1}")
            appendLine("non è json")
            for (e in events) appendLine(SketchSessionLog.encode(e))
            appendLine("{\"type\":\"event\",\"kind\":\"ANSWER\",\"campoNuovo\":true}")
        }
        val (h, read) = SketchSessionLog.parse(text)
        assertEquals(header, h)
        assertEquals(events + SessionLogEntry(kind = "ANSWER"), read)
        assertEquals("APPLIED", read[read.size - 2].kind)
        assertEquals(8, read[read.size - 2].corners!!.size)
    }

    @Test
    fun `la registrazione non cambia - si legge con o senza il file laterale`() {
        val rec = AssistedScene.recording(AssistedScene.scene(2_000) { planes = { listOf(AssistedScene.wallA()) } })
        val text = buildString {
            appendLine(ScanRecordingJson.headerLine(rec.header))
            for (f in rec.frames) appendLine(ScanRecordingJson.frameLine(f))
            appendLine(ScanRecordingJson.endLine(rec.end!!))
        }
        val dir = Files.createTempDirectory("sagoma-rec").toFile()
        try {
            File(dir, "recording.jsonl").writeText(text)
            // Registrazione vecchia: nessun file laterale.
            val old = CaptureDatasetFiles.open(dir)
            assertEquals(rec.frames.size, old.recording.frames.size)
            assertNull(old.read(SketchSessionLog.FILE_NAME))
            // Registrazione nuova: stesso recording.jsonl, più il file degli eventi.
            File(dir, SketchSessionLog.FILE_NAME).writeText(SketchSessionLog.encode(SketchSessionLog.header(fresh)) + "\n" +
                SketchSessionLog.encode(SketchSessionLog.cancelled(scanned)) + "\n")
            val now = CaptureDatasetFiles.open(dir)
            assertEquals(old.recording, now.recording)
            val (h, ev) = SketchSessionLog.parse(assertNotNull(now.read(SketchSessionLog.FILE_NAME)).decodeToString())
            assertNotNull(h)
            assertEquals(listOf("CANCELLED"), ev.map { it.kind })
        } finally {
            dir.deleteRecursively()
        }
    }
}
