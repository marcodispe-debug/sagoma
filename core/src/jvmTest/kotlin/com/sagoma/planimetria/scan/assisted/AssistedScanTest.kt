package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.assisted.AssistedScene.camera
import com.sagoma.planimetria.scan.assisted.AssistedScene.noise
import com.sagoma.planimetria.scan.assisted.AssistedScene.plane
import com.sagoma.planimetria.scan.assisted.AssistedScene.scene
import com.sagoma.planimetria.scan.assisted.AssistedScene.standardCam
import com.sagoma.planimetria.scan.assisted.AssistedScene.sway
import com.sagoma.planimetria.scan.assisted.AssistedScene.wallA
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistedScanTest {
    private fun run(frames: List<com.sagoma.planimetria.scan.recording.RecordedFrame>, p: AssistedParams = AssistedParams(), g: (AimFrame, AssistedState) -> Run.Gesture? = { _, _ -> null }) = Run(aimFrames(frames), p, g)

    @Test
    fun `un muro stabile diventa proposta dopo circa un secondo e mezzo e resta non confermato`() {
        val r = run(scene(4_000) { planes = { listOf(wallA()) } })
        val proposed = r.firstProposedMs()
        assertNotNull(proposed)
        assertTrue(proposed in 1_300L..1_800L, "proposta a $proposed ms")
        val c = r.last.candidate
        assertNotNull(c)
        assertEquals(CandidateState.PROPOSED, c.state)
        assertEquals(4.0, c.line.length, 0.01)
        assertEquals(2.5, c.line.heightM, 0.01)
        assertEquals(0.0, abs(c.line.offset), 0.001)
        assertEquals(0.0, c.line.headingDeg % 180.0, 0.01)
        assertEquals(1, r.eventsOf<AssistedEvent.Appeared>().size)
        assertTrue(r.last.walls.isEmpty(), "nessuna parete senza conferma")
        assertEquals(0, r.eventsOf<AssistedEvent.Confirmed>().size)
    }

    @Test
    fun `un piano verticale non diventa mai parete senza l evento di conferma`() {
        val r = run(scene(8_000) { planes = { listOf(wallA()) } })
        assertEquals(CandidateState.PROPOSED, r.last.candidate?.state)
        assertTrue(r.last.walls.isEmpty())
        assertTrue(r.events.none { it is AssistedEvent.Confirmed })
        // Con la conferma si congela l'istantanea.
        val c = run(scene(4_000) { planes = { listOf(wallA()) } }) { f, s -> if (f.timeMs == 3_000L && s.candidate?.state == CandidateState.PROPOSED) Run.Gesture.CONFIRM else null }
        assertEquals(1, c.last.walls.size)
        assertEquals(1, c.eventsOf<AssistedEvent.Confirmed>().size)
        assertEquals(4.0, c.last.walls[0].observedLengthM, 0.01)
        // Il muro è congelato: dopo la conferma il piano può cambiare, la parete no.
        val moved = run(
            scene(6_000) { planes = { t -> listOf(if (t < 3_000) wallA() else plane(1, 0.0, 0.0, 4.0, 0.0, shift = 0.3, y1 = 1.5)) } },
        ) { f, s -> if (f.timeMs == 3_000L && s.candidate?.state == CandidateState.PROPOSED) Run.Gesture.CONFIRM else null }
        assertEquals(2.5, moved.last.walls[0].line.heightM, 0.01)
        assertEquals(0.0, moved.last.walls[0].line.offset, 0.001)
    }

    @Test
    fun `piccoli movimenti della camera non impediscono la proposta e non spostano la linea`() {
        val r = run(scene(5_000) {
            planes = { listOf(wallA()) }
            cam = { t -> camera(2.0 + 0.03 * sway(t, 1), 1.4 + 0.02 * sway(t, 2), 2.0 + 0.03 * sway(t, 3), 2.0 + 0.4 * sway(t, 4), 1.25, 0.0) }
        })
        assertNotNull(r.firstProposedMs())
        val c = r.last.candidate!!
        assertEquals(CandidateState.PROPOSED, c.state)
        assertEquals(0.0, c.line.offset, 0.001)
        assertTrue(c.maxSpeedMps <= 0.5)
    }

    @Test
    fun `il piano che si sposta di pochi millimetri resta la stessa candidata`() {
        val r = run(scene(5_000) { planes = { t -> listOf(plane(1, 0.0, 0.0, 4.0, 0.0, shift = 0.004 * noise(t, 5))) } })
        assertNotNull(r.firstProposedMs())
        assertEquals(1, r.eventsOf<AssistedEvent.Appeared>().size)
        assertTrue(r.events.none { it is AssistedEvent.Switched })
        assertEquals(CandidateState.PROPOSED, r.last.candidate?.state)
    }

    @Test
    fun `il piano che salta di venti centimetri e una nuova candidata e ricomincia la stabilizzazione`() {
        val r = run(scene(6_000) { planes = { t -> listOf(plane(1, 0.0, 0.0, 4.0, 0.0, shift = if (t < 2_500) 0.0 else 0.2)) } })
        assertEquals(1, r.eventsOf<AssistedEvent.Switched>().size)
        val sw = r.eventsOf<AssistedEvent.Switched>()[0].timeMs
        assertTrue(sw in 2_750L..2_900L, "cambio a $sw")
        assertEquals(CandidateState.TRACKING, r.stateAt(sw + 200).candidate?.state)
        assertEquals(CandidateState.PROPOSED, r.last.candidate?.state)
        assertEquals(0.2, r.last.candidate!!.line.offset, 0.001)
    }

    @Test
    fun `un piano diviso da ARCore e poi riunito resta una sola candidata`() {
        // 0..2 s: due frammenti 0..2,2 e 2,1..4; da 2 s il primo assorbe il secondo e diventa 0..4. La mira va da x=1 a x=3.
        val r = run(scene(6_000) {
            planes = { t ->
                if (t < 2_000) listOf(plane(2, 0.0, 0.0, 2.2, 0.0), plane(3, 2.1, 0.0, 4.0, 0.0))
                else listOf(plane(2, 0.0, 0.0, 4.0, 0.0), plane(3, 2.1, 0.0, 4.0, 0.0, subsumedBy = 2))
            }
            cam = { t -> camera(2.0, 1.4, 2.0, if (t < 3_000) 1.0 else 3.0, 1.25, 0.0) }
        })
        assertEquals(1, r.eventsOf<AssistedEvent.Appeared>().size)
        assertTrue(r.events.none { it is AssistedEvent.Switched || it is AssistedEvent.Lost })
        assertEquals(CandidateState.PROPOSED, r.last.candidate?.state)
        assertEquals(4.0, r.last.candidate!!.line.length, 0.02)
        // I due frammenti uniti già prima: la lunghezza della candidata supera quella del frammento mirato.
        val early = r.stateAt(1_500).candidate!!
        assertEquals(4.0, early.line.length, 0.05)
        assertTrue(early.planeKeys.containsAll(listOf(2, 3)))
    }

    @Test
    fun `una perdita di mira sotto il secondo non cancella la candidata`() {
        val aimOff = camera(2.0, 1.4, 2.0, 7.0, 1.25, 0.0) // guarda oltre il muro: nessun colpo
        val r = run(scene(6_000) { planes = { listOf(wallA()) }; cam = { t -> if (t in 2_500L until 3_100L) aimOff else standardCam } })
        assertTrue(r.events.none { it is AssistedEvent.Lost })
        assertEquals(1, r.eventsOf<AssistedEvent.Appeared>().size)
        assertNotNull(r.stateAt(2_900).candidate)
        assertTrue(Reason.AIM_LOST in r.stateAt(2_900).candidate!!.reasons)
        assertEquals(CandidateState.PROPOSED, r.last.candidate?.state)
    }

    @Test
    fun `una perdita di mira oltre il secondo azzera la candidata e si riparte da capo`() {
        val aimOff = camera(2.0, 1.4, 2.0, 7.0, 1.25, 0.0)
        val r = run(scene(8_000) { planes = { listOf(wallA()) }; cam = { t -> if (t in 3_000L until 4_500L) aimOff else standardCam } })
        assertEquals(1, r.eventsOf<AssistedEvent.Lost>().size)
        assertEquals(2, r.eventsOf<AssistedEvent.Appeared>().size)
        assertNull(r.stateAt(4_400).candidate)
        // Dopo il ritorno la proposta non è immediata: servono di nuovo circa 1,5 s.
        assertEquals(2, r.eventsOf<AssistedEvent.Proposed>().size)
        val second = r.eventsOf<AssistedEvent.Proposed>()[1].timeMs
        assertTrue(second - 4_500 >= 1_300, "seconda proposta dopo ${second - 4_500} ms")
        assertEquals(CandidateState.PROPOSED, r.last.candidate?.state)
    }

    @Test
    fun `un muro corto non viene mai proposto`() {
        val r = run(scene(5_000) { planes = { listOf(plane(1, 1.6, 0.0, 2.4, 0.0)) } })
        assertTrue(r.events.none { it is AssistedEvent.Proposed })
        assertTrue(Reason.SHORT in r.last.candidate!!.reasons)
        assertNotEquals(CandidateState.PROPOSED, r.last.candidate!!.state)
    }

    @Test
    fun `una superficie molto inclinata non viene proposta`() {
        val r = run(scene(5_000) { planes = { listOf(plane(1, 0.0, 0.0, 4.0, 0.0, tiltDeg = 15.0)) } })
        assertTrue(r.events.none { it is AssistedEvent.Proposed })
        assertTrue(Reason.TILTED in r.last.candidate!!.reasons)
    }

    @Test
    fun `un piano che oscilla di direzione non e stabile`() {
        val r = run(scene(5_000) { planes = { t -> listOf(plane(1, 0.0, 0.0, 4.0, 0.0, extraHeading = 4.0 * noise(t, 7))) } }, AssistedParams(sameAngleTolDeg = 10.0))
        assertTrue(r.events.none { it is AssistedEvent.Proposed })
        assertTrue(r.states.drop(40).any { Reason.HEADING_UNSTABLE in (it.candidate?.reasons ?: emptyList()) })
    }

    @Test
    fun `la camera troppo veloce non propone`() {
        val r = run(scene(5_000) { planes = { listOf(wallA()) }; cam = { t -> camera(1.0 + 1.0 * t / 1000.0 * 0.0 + 0.8 * sin(t / 1000.0 * 2.0) , 1.4, 2.0, 2.0, 1.25, 0.0) } })
        assertTrue(r.states.any { Reason.TOO_FAST in (it.candidate?.reasons ?: emptyList()) })
    }

    @Test
    fun `una superficie parallela davanti a un muro confermato porta un avviso ma non e un doppione`() {
        val furniture = plane(7, 0.5, 0.45, 3.5, 0.45, y0 = 0.4, y1 = 1.6)
        val r = run(scene(9_000) {
            planes = { t -> if (t < 4_000) listOf(wallA()) else listOf(wallA(), furniture) }
            cam = { t -> if (t < 4_000) standardCam else camera(2.0, 1.4, 2.0, 2.0, 1.0, 0.45) }
        }) { f, s -> if (s.candidate?.state == CandidateState.PROPOSED && s.walls.isEmpty() && f.timeMs >= 3_000) Run.Gesture.CONFIRM else null }
        assertEquals(1, r.last.walls.size)
        val c = r.last.candidate!!
        assertEquals(7, c.aimedPlaneKey)
        assertEquals(CandidateState.PROPOSED, c.state)
        assertTrue(Advisory.PARALLEL_BEHIND in c.advisories, "avvisi: ${c.advisories}")
        assertTrue(Advisory.NOT_REACHING_FLOOR in c.advisories, "avvisi: ${c.advisories}")
        assertNull(c.duplicateOf)
        // Si può comunque confermare (decide l'utente).
        val ok = AssistedScan.confirm(r.last, 9_000)
        assertEquals(2, ok.state.walls.size)
    }

    @Test
    fun `riconfermare la stessa parete e un doppione rifiutato e la conferma senza proposta e rifiutata`() {
        val r = run(scene(9_000) { planes = { listOf(wallA()) } }) { f, s -> if (s.candidate?.state == CandidateState.PROPOSED && s.walls.isEmpty()) Run.Gesture.CONFIRM else null }
        assertEquals(1, r.last.walls.size)
        val c = r.last.candidate!!
        assertEquals(CandidateState.PROPOSED, c.state)
        assertEquals(1, c.duplicateOf)
        val again = AssistedScan.confirm(r.last, 9_000)
        assertEquals(1, again.state.walls.size)
        assertTrue(again.events.single() is AssistedEvent.Rejected)
        // Nessuna proposta.
        val none = AssistedScan.confirm(AssistedScan.initial(), 0)
        assertTrue(none.events.single() is AssistedEvent.Rejected)
        assertTrue(none.state.walls.isEmpty())
    }

    @Test
    fun `annulla toglie l ultima parete e la candidata riparte`() {
        val r = run(scene(6_000) { planes = { listOf(wallA()) } }) { f, s -> if (s.candidate?.state == CandidateState.PROPOSED && s.walls.isEmpty()) Run.Gesture.CONFIRM else null }
        val u = AssistedScan.undo(r.last, 6_000)
        assertTrue(u.state.walls.isEmpty())
        assertTrue(u.events.single() is AssistedEvent.Undone)
        assertTrue(AssistedScan.undo(u.state, 6_001).events.single() is AssistedEvent.Rejected)
    }

    @Test
    fun `due pareti non ortogonali sono entrambe accettate con le loro direzioni`() {
        val a = plane(1, 0.0, 0.0, 4.0, 0.0)
        val ang = Math.toRadians(70.0)
        val bx = 4.0 + 3.0 * cos(ang); val bz = 3.0 * sin(ang)
        val b = plane(2, 4.0, 0.0, bx, bz)
        val tx = 4.0 + 1.5 * cos(ang); val tz = 1.5 * sin(ang)
        val r = run(scene(10_000) {
            planes = { listOf(a, b) }
            cam = { t -> if (t < 4_000) camera(2.0, 1.4, 1.5, 2.0, 1.25, 0.0) else camera(2.0, 1.4, 1.5, tx, 1.25, tz) }
        }) { f, s -> if (s.candidate?.state == CandidateState.PROPOSED && s.candidate.duplicateOf == null && f.timeMs >= 2_500) Run.Gesture.CONFIRM else null }
        assertEquals(2, r.last.walls.size, "eventi: ${r.events}")
        val w1 = r.last.walls[0]; val w2 = r.last.walls[1]
        assertEquals(setOf(1), w1.planeKeys.toSet())
        assertEquals(setOf(2), w2.planeKeys.toSet())
        assertEquals(70.0, Aim.angleBetween(w1.line, w2.line), 0.5)
        assertEquals(4.0, w1.line.length, 0.02)
        assertEquals(3.0, w2.line.length, 0.02)
    }

    @Test
    fun `i punti non spostano la linea del piano ma il disaccordo emerge come avviso`() {
        val good = run(scene(5_000) { planes = { listOf(wallA()) }; points = { _, i -> if (i == 0) AssistedScene.wallPoints(0.0, 0.02) else null } })
        val gc = good.last.candidate!!
        assertTrue(gc.evidence.support >= 10, "supporto ${gc.evidence.support}")
        assertTrue(Advisory.POINTS_DISAGREE !in gc.advisories && Advisory.FEW_POINTS !in gc.advisories, "avvisi ${gc.advisories}")
        val bad = run(scene(5_000) { planes = { listOf(wallA()) }; points = { _, i -> if (i == 0) AssistedScene.wallPoints(0.055, 0.004) else null } })
        val bc = bad.last.candidate!!
        assertTrue(Advisory.POINTS_DISAGREE in bc.advisories, "avvisi ${bc.advisories} supporto ${bc.evidence}")
        assertEquals(0.0, abs(bc.line.offset), 0.001) // la linea e rimasta quella del piano
        assertEquals(CandidateState.PROPOSED, bc.state)
        assertEquals(0.055, bc.evidence.biasM!!.let { abs(it) }, 0.01)
        // Senza punti: nessuna evidenza e nessun blocco.
        val none = run(scene(5_000) { planes = { listOf(wallA()) } }).last.candidate!!
        assertEquals(false, none.evidence.available)
        assertEquals(CandidateState.PROPOSED, none.state)
    }

    @Test
    fun `l ordine dei frame nel file non cambia il risultato`() {
        val frames = scene(6_000) { planes = { listOf(wallA()) }; cam = { t -> camera(2.0 + 0.03 * sway(t, 1), 1.4, 2.0, 2.0 + 0.4 * sway(t, 4), 1.25, 0.0) } }
        val shuffled = frames.shuffled(kotlin.random.Random(42))
        val a = aimFrames(frames); val b = aimFrames(shuffled)
        assertEquals(a, b)
        val ra = Run(a); val rb = Run(b)
        assertEquals(ra.events, rb.events)
        assertEquals(ra.last.candidate, rb.last.candidate)
        val sa = AssistedSimulation.run(a); val sb = AssistedSimulation.run(shuffled.let { aimFrames(it) })
        assertEquals(AssistedSimulationReport.csv(sa), AssistedSimulationReport.csv(sb))
        assertEquals(AssistedSimulationReport.text(sa, "x"), AssistedSimulationReport.text(sb, "x"))
    }

    @Test
    fun `senza tracking non c e mira ne proposta`() {
        val r = run(scene(4_000) { planes = { listOf(wallA()) }; tracking = { false } })
        assertTrue(r.events.isEmpty())
        assertNull(r.last.candidate)
    }

    @Test
    fun `un frame fuori ordine viene ignorato dal riduttore`() {
        val frames = aimFrames(scene(2_000) { planes = { listOf(wallA()) } })
        var s = AssistedScan.initial()
        for (f in frames) s = AssistedScan.step(s, f).state
        val before = s
        val after = AssistedScan.step(s, frames[3]).state
        assertEquals(before, after)
    }
}

private fun assertNotEquals(a: Any?, b: Any?) = assertTrue(a != b)
