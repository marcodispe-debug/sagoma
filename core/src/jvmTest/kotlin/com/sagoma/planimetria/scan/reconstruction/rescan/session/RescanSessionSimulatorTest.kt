package com.sagoma.planimetria.scan.reconstruction.rescan.session

import com.sagoma.planimetria.scan.reconstruction.bench.BenchPipeline
import com.sagoma.planimetria.scan.reconstruction.bench.BenchRun
import com.sagoma.planimetria.scan.reconstruction.bench.GtRoom
import com.sagoma.planimetria.scan.reconstruction.bench.Scenario
import com.sagoma.planimetria.scan.reconstruction.bench.Scenarios
import com.sagoma.planimetria.scan.reconstruction.bench.SensorParams
import com.sagoma.planimetria.scan.reconstruction.bench.Station
import com.sagoma.planimetria.scan.reconstruction.quality.QualityEngine
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanDirector
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M5.1 end-to-end sul simulatore: la stessa stanza acquisita in tre passi CUMULATIVI (A ⊂ B ⊂ C: ogni registrazione contiene
 * tutti i frame della precedente più nuove viste, stesso sistema di coordinate), attraverso R1 → R2 → R2.1 → R3 → R4 → M4 → M5 → M5.1.
 * Con SAGOMA_RESCAN_SESSION_OUT=<cartella> scrive sessione, riepilogo ed eventi di ogni passo.
 */
class RescanSessionSimulatorTest {
    private val sensor = SensorParams(depthSigmaM = 0.006, dropout = 0.03)
    private val room = GtRoom("SESS", Scenarios.rect())
    private val a = listOf(Station(2.0, 1.4, 1.5, yawFromDeg = 0.0, yawToDeg = 90.0))
    private val b = a + Station(2.0, 1.4, 1.5, yawFromDeg = 105.0, yawToDeg = 195.0)
    private val c = b + Station(2.0, 1.4, 1.5, yawFromDeg = 210.0, yawToDeg = 345.0) + Station(2.6, 1.5, 1.8, yawFromDeg = 7.5, yawToDeg = 352.5)

    private fun input(name: String, st: List<Station>): ScanInput {
        val r: BenchRun = BenchPipeline.run(Scenario(name, name, room, st, sensor, ""))
        val q = QualityEngine.evaluate(r.map, r.surfaces, r.walls, r.r4)
        val plan = RescanDirector.plan(r.surfaces, r.walls, r.r4, q)
        val rec = r.sim.recording
        return ScanInput(name, rec.header.createdAtMillis, rec.poses, rec.depth, r.walls.walls, q, plan, RescanDirector.perimeterGeometry(r.r4))
    }

    private fun sequence(): List<RescanSession> {
        val s1 = RescanSessionEngine.update(null, input("A", a))
        val s2 = RescanSessionEngine.update(s1, input("B", b))
        val s3 = RescanSessionEngine.update(s2, input("C", c))
        return listOf(s1, s2, s3)
    }

    @Test
    fun `A B C cumulative sul simulatore - stesso sistema, storia valida e deterministica`() {
        val seq = sequence()
        val out = System.getenv("SAGOMA_RESCAN_SESSION_OUT")?.let { File(it).also { d -> d.mkdirs() } }
        for ((i, s) in seq.withIndex()) {
            assertEquals(emptyList(), RescanSessionEngine.validate(s))
            out?.let { d ->
                File(d, "step${"ABC"[i]}-rescan-session.json").writeText(RescanSessionJson.encode(s))
                File(d, "step${"ABC"[i]}-rescan-session-summary.txt").writeText(RescanSessionReport.summary(s))
                File(d, "step${"ABC"[i]}-rescan-session-events.csv").writeText(RescanSessionReport.eventsCsv(s))
            }
        }
        val last = seq.last()
        assertEquals(listOf(FrameStatus.FIRST_SCAN, FrameStatus.SAME_FRAME_PREFIX, FrameStatus.SAME_FRAME_PREFIX), last.scans.map { it.frameStatus })
        // Nessun bersaglio eliminato tra i passi.
        assertTrue(seq.zipWithNext().all { (x, y) -> x.targets.map { it.key }.all { k -> y.targets.any { it.key == k } } })
        // Più viste: almeno un bersaglio migliora o si risolve con una prova misurata (mai per scomparsa).
        val outcomes = last.targets.flatMap { it.history }.filter { it.to == TargetState.IMPROVED || it.to == TargetState.RESOLVED }
        assertTrue(outcomes.isNotEmpty())
        assertTrue(outcomes.all { it.reason != TransitionReason.NOT_REFOUND && it.current != null })
        // Quando una parete non ha corrispondenza, la diagnostica della candidata più vicina è sempre presente.
        for (m in last.wallMatches.filter { it.status == MatchStatus.NO_GEOMETRIC_MATCH }) assertTrue(m.nearestR3WallId != null && m.failedRules.isNotEmpty())
        // Un bersaglio della parete scomparsa (SW2, il muro a x ≈ 0 non più prodotto da R3 in B) non è mai RESOLVED.
        for (t in last.targets.filter { it.wallKey == "SW2" }) assertEquals(TargetState.PERSISTENT, t.state)
        // Determinismo end-to-end.
        assertEquals(RescanSessionJson.encode(last), RescanSessionJson.encode(sequence().last()))
        println(RescanSessionReport.summary(last))
    }
}
