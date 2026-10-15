package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.WallOutline
import com.sagoma.planimetria.scan.assisted.AssistedScene.camera
import com.sagoma.planimetria.scan.assisted.AssistedScene.plane
import com.sagoma.planimetria.scan.assisted.AssistedScene.scene
import com.sagoma.planimetria.scan.assisted.AssistedScene.sway
import com.sagoma.planimetria.scan.assisted.AssistedScene.wallA
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import java.io.File
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prova M2.0 dal vivo: frame uno alla volta, sessione con conferme, perimetro solo dalle pareti confermate. */
class AssistedLiveTest {
    private fun line(ax: Double, az: Double, bx: Double, bz: Double, minY: Double = -1.2, maxY: Double = 1.4): WallLine {
        val d = ArXZ(bx - ax, bz - az).normalized()
        val n = ArXZ(-d.z, d.x)
        return WallLine(ArXZ(ax, az), ArXZ(bx, bz), d, n, ArXZ(ax, az) dot n, minY, maxY, 0.0)
    }

    private fun wall(id: Int, l: WallLine) =
        ConfirmedWall(id, l, l.normal, l.length, null, 0.9, Quality.HIGH, 0.02, 50, listOf(id), id * 1000L, null, emptyList())

    /** Stanza 4 × 3 m vista a tratti: nessuna parete arriva agli angoli (come dietro i mobili). */
    private val room = listOf(
        wall(1, line(0.3, 0.0, 3.6, 0.0)),
        wall(2, line(4.0, 0.4, 4.0, 2.5)),
        wall(3, line(3.7, 3.0, 0.5, 3.0)),
        wall(4, line(0.0, 2.8, 0.0, 0.2)),
    )

    // ---------- Frame uno alla volta ----------

    private fun frames() = scene(14_000) {
        val ang = Math.toRadians(70.0)
        val b = plane(2, 4.0, 0.0, 4.0 + 3.0 * kotlin.math.cos(ang), 3.0 * kotlin.math.sin(ang))
        planes = { listOf(wallA(), b) }
        cam = { t ->
            when {
                t < 4_000 -> camera(2.0 + 0.02 * sway(t, 1), 1.4, 1.5, 2.0, 1.25, 0.0)
                t < 5_500 -> camera(2.0, 1.4, 1.5, -6.0, 1.25, 1.5)
                t < 10_000 -> camera(2.0, 1.4, 1.5, 4.0 + 1.5 * kotlin.math.cos(ang), 1.25, 1.5 * kotlin.math.sin(ang))
                else -> AssistedScene.standardCam
            }
        }
        points = { _, i -> if (i == 0) AssistedScene.wallPoints(0.0, 0.02) else null }
    }

    @Test
    fun `il costruttore incrementale da gli stessi frame del calcolo in blocco`() {
        val rec = frames()
        val batch = AimFrames.from(rec)
        val builder = AimFrameBuilder()
        val live = rec.sortedWith(compareBy({ it.elapsedMs }, { it.index })).map { builder.next(it) }
        assertEquals(batch, live)
    }

    @Test
    fun `la sessione frame per frame da le stesse proposte della simulazione`() {
        val frames = AimFrames.from(frames())
        var s = AssistedSession()
        val events = ArrayList<AssistedEvent>()
        for (f in frames) { s = s.step(f); events += s.events }
        assertEquals(AssistedSimulation.run(frames).noConfirm.events, events)
        assertTrue(events.any { it is AssistedEvent.Proposed })
        assertTrue(s.walls.isEmpty()) // nessuna parete senza conferma
    }

    // ---------- Perimetro dalle pareti confermate ----------

    @Test
    fun `quattro pareti confermate chiudono la stanza con i lati sulle pareti`() {
        val p = assertNotNull(AssistedPerimeters.of(room))
        assertEquals(4, p.sides.size)
        assertEquals(setOf(1, 2, 3, 4), p.sides.map { it.wallId }.toSet())
        assertEquals(emptyList(), p.unusedWallIds)
        assertEquals(14.0, p.perimeterM, 1e-9)
        assertEquals(12.0, p.areaM2, 1e-9)
        for (i in p.corners.indices) assertEquals(90.0, p.interiorAngleDeg(i), 1e-9)
        assertEquals(listOf(5.0, 5.0), p.diagonalsM.map { (it * 1e9).let(Math::round) / 1e9 })
        // Ogni lato: la parte osservata è quella vista, il resto è prolungamento (dichiarato).
        val bySide = p.sides.associateBy { it.wallId }
        assertEquals(3.3, bySide.getValue(1).observedM, 1e-9); assertEquals(0.7, bySide.getValue(1).extendedM, 1e-9)
        assertEquals(2.1, bySide.getValue(2).observedM, 1e-9); assertEquals(0.9, bySide.getValue(2).extendedM, 1e-9)
    }

    @Test
    fun `con tre pareti la stanza non si chiude e nessun lato viene aggiunto`() {
        assertNull(AssistedPerimeters.of(room.take(3)))
        var s = AssistedSession()
        assertNull(s.perimeter); assertNull(s.result())
        s = s.copy(state = s.state.copy(walls = room.take(3)))
        assertNull(s.perimeter); assertNull(s.result())
    }

    @Test
    fun `una parete confermata fuori dal perimetro resta esclusa`() {
        val mobile = wall(5, line(1.0, 1.2, 2.0, 1.2)) // parallela, dentro la stanza, lontana dagli angoli
        val p = assertNotNull(AssistedPerimeters.of(room + mobile))
        assertEquals(listOf(5), p.unusedWallIds)
        assertEquals(setOf(1, 2, 3, 4), p.sides.map { it.wallId }.toSet())
    }

    @Test
    fun `il perimetro e quello di WallOutline sulle sole pareti confermate`() {
        val p = assertNotNull(AssistedPerimeters.of(room))
        assertEquals(WallOutline.layout(room.map(AssistedPerimeters::detected)).closed, p.corners)
    }

    @Test
    fun `la stanza per Sagoma nasce solo dal perimetro chiuso`() {
        val s = AssistedSession(floorY = -1.2).copy(state = AssistedScan.initial().copy(walls = room))
        val r = assertNotNull(s.result())
        assertEquals(4, r.room.corners.size)
        assertEquals(4, r.walls.size)
        // Annullare l'ultima parete riapre la stanza.
        val undone = s.undo(99_000)
        assertEquals(3, undone.walls.size)
        assertNull(undone.result())
    }

    // ---------- Dati reali: scan4 ----------

    /**
     * Riferimento empirico R3 su scan4 (pareti W0, W1, W2, W4 di `risultati4/walls.csv`): rette delle pareti nel mondo ARCore.
     * Il perimetro di riferimento si calcola con lo stesso [AssistedPerimeters] (lati 1,93 · 2,15 · 1,94 · 2,13 m).
     */
    private val r3 = mapOf(
        0 to line(-0.7781, -0.9869, -0.8678, 0.9108),
        1 to line(-0.8666, 0.9241, 1.0288, 1.0442),
        2 to line(1.0571, 0.9836, 1.1601, -0.8633),
        4 to line(0.9016, -1.1175, -0.4446, -1.1893),
    )


    private fun angleDeg(a: WallLine, b: WallLine) = Math.toDegrees(asin(min(1.0, abs(a.direction.cross(b.direction)))))
    private fun offset(a: WallLine, b: WallLine): Double {
        val mid = (b.a + b.b) * 0.5
        return abs((mid - a.a).cross(a.direction))
    }

    /** scan4 caricato una volta sola per tutti i test (circa 7 s). */
    private object Scan4 {
        val rec by lazy { scan4File()?.let { CaptureDatasetFiles.open(it).recording } }
        val batch by lazy { rec?.let { AimFrames.from(it) } }
        val live by lazy {
            rec?.let { r -> val b = AimFrameBuilder(); r.frames.sortedWith(compareBy({ it.elapsedMs }, { it.index })).map { b.next(it) } }
        }

        fun scan4File(): File? {
            val f = File(System.getenv("SAGOMA_SCAN4_ZIP") ?: "../test stanza1/scan4.zip")
            return if (f.isFile) f else null.also { println("SALTATO: scan4 non trovato (${f.absolutePath}); impostare SAGOMA_SCAN4_ZIP") }
        }
    }

    /**
     * Utente simulato su scan4: conferma la prima proposta (non doppione) compatibile con una parete di riferimento R3 ancora
     * libera (≤ 6° e ≤ 10 cm). È una scelta di prova, NON la capacità di un utente vero: quella si misura sul telefono.
     * Restituisce la sessione e l'abbinamento parete confermata → parete R3.
     */
    private fun confirmedOnScan4(live: List<AimFrame>): Pair<AssistedSession, Map<Int, Int>> {
        var s = AssistedSession()
        val matched = HashMap<Int, Int>()
        for (f in live) {
            s = s.step(f)
            val c = s.candidate ?: continue
            if (c.state != CandidateState.PROPOSED || c.duplicateOf != null) continue
            val target = r3.entries.firstOrNull { (k, l) -> k !in matched.values && angleDeg(l, c.line) <= 6.0 && offset(l, c.line) <= 0.10 } ?: continue
            s = s.confirm(f.timeMs)
            matched[s.walls.last().id] = target.key
        }
        return s to matched
    }

    // Pipeline: deve passare anche se la geometria non rispetta le soglie.

    @Test
    fun `scan4 pipeline frame per frame uguale al calcolo in blocco e alla simulazione`() {
        val batch = Scan4.batch ?: return
        val live = Scan4.live!!
        assertEquals(batch.size, live.size)
        assertTrue(batch.indices.all { batch[it] == live[it] }, "frame incrementali diversi da quelli in blocco")
        var free = AssistedSession()
        val ev = ArrayList<AssistedEvent>()
        for (f in live) { free = free.step(f); ev += free.events }
        assertEquals(AssistedSimulation.run(batch).noConfirm.events, ev)
        assertTrue(free.walls.isEmpty()) // nessuna parete senza conferma
    }

    @Test
    fun `scan4 pipeline quattro pareti confermate chiudono la stanza solo con i loro lati`() {
        val live = Scan4.live ?: return
        val (s, matched) = confirmedOnScan4(live)
        assertEquals(4, s.walls.size, "pareti confermate: ${s.walls.map { AssistedLog.wall(it) }}")
        assertEquals(setOf(0, 1, 2, 4), matched.values.toSet())
        val p = assertNotNull(s.perimeter, "le 4 pareti confermate non chiudono")
        assertEquals(4, p.sides.size)
        assertEquals(s.walls.map { it.id }.toSet(), p.sides.map { it.wallId }.toSet())
        assertTrue(p.unusedWallIds.isEmpty())
        assertEquals(4, assertNotNull(s.result()).room.corners.size)
    }

    // Qualità geometrica: soglie approvate per la prova. FALLISCE se una soglia non è rispettata (non si cambiano le soglie).

    @Test
    fun `scan4 qualita geometrica rispetto a R3 entro le soglie approvate`() {
        val live = Scan4.live ?: return
        val (s, matched) = confirmedOnScan4(live)
        val p = assertNotNull(s.perimeter)
        val ref = assertNotNull(AssistedPerimeters.of(r3.entries.map { (k, l) -> wall(k + 100, l) }))
        println("scan4 M2.0: " + AssistedLog.perimeter(p) + " · abbinamento confermata→R3 " + matched)
        println("scan4 R3:   " + AssistedLog.perimeter(ref))
        // Abbinamento esplicito e uguale in tutte le sessioni: lato ↔ parete R3; angolo ↔ COPPIA di pareti R3 che lo formano
        // (ogni angolo M2.0 si confronta con l'angolo R3 tra le stesse due pareti, calcolato con lo stesso metodo, non con un
        // valore scelto dall'intervallo 89,6–90,9°); diagonale ↔ coppia di angoli opposti.
        fun r3Of(wallId: Int) = matched.getValue(wallId)
        val refSide = ref.sides.associate { (it.wallId - 100) to it.lengthM }
        fun cornerKey(per: AssistedPerimeter, i: Int, map: (Int) -> Int) =
            setOf(map(per.sides[(i + per.sides.size - 1) % per.sides.size].wallId), map(per.sides[i].wallId))
        val refCorner = ref.corners.indices.associate { cornerKey(ref, it) { id -> id - 100 } to it }
        val rows = ArrayList<String>()
        var worstSide = 0.0; var worstAngle = 0.0; var worstDiag = 0.0
        for (side in p.sides) {
            val r = refSide.getValue(r3Of(side.wallId)); val d = side.lengthM - r
            worstSide = max(worstSide, abs(d)); rows += "lato R3 W${r3Of(side.wallId)}: ${"%.3f".format(side.lengthM)} vs ${"%.3f".format(r)} (Δ ${"%+.3f".format(d)})"
        }
        for (i in p.corners.indices) {
            val j = refCorner.getValue(cornerKey(p, i, ::r3Of))
            val d = p.interiorAngleDeg(i) - ref.interiorAngleDeg(j)
            worstAngle = max(worstAngle, abs(d)); rows += "angolo tra W${cornerKey(p, i, ::r3Of).sorted().joinToString(" e W")}: ${"%.2f".format(p.interiorAngleDeg(i))}° vs ${"%.2f".format(ref.interiorAngleDeg(j))}° (Δ ${"%+.2f".format(d)})"
        }
        for (i in 0..1) {
            val jA = refCorner.getValue(cornerKey(p, i, ::r3Of)); val jB = refCorner.getValue(cornerKey(p, i + 2, ::r3Of))
            val dm = p.corners[i].distanceTo(p.corners[i + 2]); val dr = ref.corners[jA].distanceTo(ref.corners[jB])
            worstDiag = max(worstDiag, abs(dm - dr)); rows += "diagonale ${cornerKey(p, i, ::r3Of)}–${cornerKey(p, i + 2, ::r3Of)}: ${"%.3f".format(dm)} vs ${"%.3f".format(dr)} (Δ ${"%+.3f".format(dm - dr)})"
        }
        rows += "perimetro ${"%.3f".format(p.perimeterM)} vs ${"%.3f".format(ref.perimeterM)} · area ${"%.3f".format(p.areaM2)} vs ${"%.3f".format(ref.areaM2)}"
        rows.forEach { println("  $it") }
        val failures = listOfNotNull(
            "lato ${"%.3f".format(worstSide)} m > 0,05".takeIf { worstSide > 0.05 },
            "diagonale ${"%.3f".format(worstDiag)} m > 0,05".takeIf { worstDiag > 0.05 },
            "angolo ${"%.2f".format(worstAngle)}° > 2".takeIf { worstAngle > 2.0 },
        )
        assertTrue(failures.isEmpty(), "soglie non rispettate: ${failures.joinToString("; ")}")
    }
}
