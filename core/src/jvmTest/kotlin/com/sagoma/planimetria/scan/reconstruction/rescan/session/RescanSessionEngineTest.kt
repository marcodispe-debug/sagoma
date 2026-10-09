package com.sagoma.planimetria.scan.reconstruction.rescan.session

import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.EvidenceQuality
import com.sagoma.planimetria.scan.reconstruction.GapReason
import com.sagoma.planimetria.scan.reconstruction.MissingSide
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.SurfaceAmbiguity
import com.sagoma.planimetria.scan.reconstruction.ThicknessState
import com.sagoma.planimetria.scan.reconstruction.UnobservedStretch
import com.sagoma.planimetria.scan.reconstruction.WallEnd
import com.sagoma.planimetria.scan.reconstruction.WallEvidence
import com.sagoma.planimetria.scan.reconstruction.WallGap
import com.sagoma.planimetria.scan.reconstruction.WallGeometry
import com.sagoma.planimetria.scan.reconstruction.WallSpan
import com.sagoma.planimetria.scan.reconstruction.WallThickness
import com.sagoma.planimetria.scan.reconstruction.WallUncertainty
import com.sagoma.planimetria.scan.reconstruction.quality.PerimeterFacts
import com.sagoma.planimetria.scan.reconstruction.quality.QualityEngine
import com.sagoma.planimetria.scan.reconstruction.rescan.PerimeterGeometry
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanDirector
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanPlan
import com.sagoma.planimetria.scan.recording.PoseSample
import com.sagoma.planimetria.scan.recording.RecPose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M5.1 — sessione di riscansione con ingressi COSTRUITI (pareti R3, perimetro R4, M4 e M5 reali calcolati su di essi). Le
 * registrazioni sono solo pose sintetiche: B contiene A come prefisso (stessa sessione), salvo nei test di mismatch.
 */
class RescanSessionEngineTest {

    private fun wall(
        id: Int, z: Double = 3.0, x0: Double = 0.0, spans: List<Pair<Double, Double>> = listOf(0.0 to 4.0),
        start: EndState = EndState.OBSERVED, end: EndState = EndState.OBSERVED, views: Int = 4, sigmaPos: Double = 0.005, sigmaHead: Double = 0.3,
    ): EstimatedWall {
        val s = spans.first().first; val e = spans.last().second
        val geom = WallGeometry(0.0, -1.0, -z, x0, z, 1.0, 0.0, s, e, 0.0, 2.6, 0.0, 2.6, 0.0, spans.map { WallSpan(it.first, it.second) })
        val gaps = spans.zipWithNext().map { (a, b) -> WallGap(a.second, b.first, GapReason.NOT_SEEN) }
        val ev = WallEvidence(5000, 4800, 40, views, 60.0, 2.0, 0.004, 0.003, 0.95, 0.02, if (views >= 2) EvidenceQuality.HIGH else EvidenceQuality.MEDIUM, emptyList())
        return EstimatedWall(id, listOf(id), geom, WallUncertainty(sigmaPos, sigmaHead, 0.01, 0.01, 0.014, 0.0, 0.0, sigmaPos, 0.005), ev,
            WallEnd(s, start, if (start == EndState.OBSERVED) EndReason.CORNER else EndReason.NOT_SEEN, 0.01),
            WallEnd(e, end, if (end == EndState.OBSERVED) EndReason.CORNER else EndReason.NOT_SEEN, 0.01), gaps, WallThickness(ThicknessState.UNKNOWN, note = ""), 0.9, emptyList(), emptyList())
    }

    private fun scan(
        name: String, poses: Int, walls: List<EstimatedWall>, state: PerimeterState? = PerimeterState.CLOSED, missing: MissingSide? = null,
        stretches: List<UnobservedStretch> = emptyList(), ambiguity: Map<Int, SurfaceAmbiguity> = emptyMap(), created: Long = 1000L, reverse: Boolean = false,
    ): ScanInput {
        val room = walls.map { it.id }
        val facts = PerimeterFacts(state, room, room.associateWith { "nel perimetro principale" }, stretches.map { Triple(it.wallId, it.kind, it.lengthM) }, null, null, null, null, null)
        val q = QualityEngine.evaluate(walls, facts, ambiguity, walls.associate { it.id to 4.0 }, 12.0)
        val geo = PerimeterGeometry(state, missing, stretches, emptyList())
        val p = RescanDirector.plan(q, walls, geo, ambiguity, emptyMap())
        val plan = if (reverse) RescanPlan(p.config, p.decision, p.requests.reversed(), p.limitations, p.decisionReason) else p
        val ps = (0 until poses).map { PoseSample(seq = it, timestampNs = it * 1_000_000L, tracking = "TRACKING", camera = RecPose(it * 0.01, 1.4, 0.0)) }
        return ScanInput(name, created, ps, emptyList(), if (reverse) walls.reversed() else walls, q, plan, geo)
    }

    private fun RescanSession.t(key: String) = assertNotNull(targets.firstOrNull { it.key == key }, "bersaglio $key; presenti ${targets.map { it.key }}")
    private fun ok(s: RescanSession) = assertEquals(emptyList(), RescanSessionEngine.validate(s))
    private fun ext(w: Int, ax: Double, az: Double, bx: Double, bz: Double, end: Boolean = true) =
        UnobservedStretch(w, ax, az, bx, bz, if (end) "prolungamento fino all'angolo" else "prolungamento dall'angolo", "x")

    @Test
    fun `1 e 2 - richiesta nuova NEW, poi PROPOSED quando la sessione la emette`() {
        val s = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        ok(s)
        val t = s.t("SW1-END")
        assertEquals(listOf(null to TargetState.NEW, TargetState.NEW to TargetState.PROPOSED), t.history.map { it.from to it.to })
        assertEquals(TransitionReason.FIRST_SEEN, t.history[0].reason); assertEquals(TransitionReason.EMITTED, t.history[1].reason)
        assertEquals(TargetState.PROPOSED, t.state); assertEquals(0, t.attempts)
        assertEquals(FrameStatus.FIRST_SCAN, s.scans.single().frameStatus)
    }

    @Test
    fun `3 e 6 - nuova scansione con il bersaglio aperto - ATTEMPTED poi PERSISTENT`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, end = EndState.PARTIAL))))
        ok(b)
        assertEquals(FrameStatus.SAME_FRAME_PREFIX, b.scans.last().frameStatus)
        val t = b.t("SW1-END")
        assertEquals(TargetState.PERSISTENT, t.state); assertEquals(1, t.attempts)
        assertEquals(listOf(TargetState.PROPOSED to TargetState.ATTEMPTED, TargetState.ATTEMPTED to TargetState.PERSISTENT), t.history.drop(2).map { it.from to it.to })
        assertEquals(TransitionReason.UNOBSERVED_NOT_MEASURABLE, t.history.last().reason, "senza prolungamento R4 il non osservato non è misurabile")
    }

    @Test
    fun `4 - estremita migliorata - non osservato ridotto di almeno 20 cm`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL)), stretches = listOf(ext(0, 4.0, 3.0, 4.6, 3.0))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, end = EndState.PARTIAL, spans = listOf(0.0 to 4.3))), stretches = listOf(ext(0, 4.3, 3.0, 4.6, 3.0))))
        ok(b)
        val e = b.t("SW1-END").history.last()
        assertEquals(TargetState.IMPROVED, e.to); assertEquals(TransitionReason.UNOBSERVED_LENGTH_REDUCED, e.reason)
        assertEquals(0.3, e.changeM!!, 1e-9); assertEquals(0.6, e.previous!!.unobservedM!!, 1e-9); assertEquals(0.3, e.current!!.unobservedM!!, 1e-9)
        // Riduzione sotto 20 cm: nessun miglioramento.
        val c = RescanSessionEngine.update(b, scan("C", 30, listOf(wall(0, end = EndState.PARTIAL, spans = listOf(0.0 to 4.4))), stretches = listOf(ext(0, 4.4, 3.0, 4.6, 3.0))))
        assertEquals(TransitionReason.NO_MEASURABLE_IMPROVEMENT, c.t("SW1-END").history.last().reason)
        assertEquals(TargetState.PERSISTENT, c.t("SW1-END").state)
    }

    @Test
    fun `5 - estremita risolta - OBSERVED`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0))))
        ok(b)
        val e = b.t("SW1-END").history.last()
        assertEquals(TargetState.RESOLVED, e.to); assertEquals(TransitionReason.ENDPOINT_OBSERVED, e.reason)
        assertEquals("PARTIAL", e.previous!!.endpointState); assertEquals("OBSERVED", e.current!!.endpointState)
    }

    @Test
    fun `6b - da PARTIAL a UNCERTAIN non e risolto ne migliorato`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, end = EndState.UNCERTAIN))))
        assertEquals(TargetState.PERSISTENT, b.t("SW1-END").state)
    }

    @Test
    fun `7 - nuova richiesta mentre altre persistono`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL), wall(1, z = 0.0))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, end = EndState.PARTIAL), wall(1, z = 0.0, views = 1))))
        ok(b)
        assertEquals(TargetState.PERSISTENT, b.t("SW1-END").state)
        val v = b.t("SW2-VIEWS"); assertEquals(TargetState.PROPOSED, v.state); assertEquals(MatchStatus.MATCH_FOUND, v.history.first().matchStatus)
    }

    @Test
    fun `8 - due cause sullo stesso angolo - una richiesta M5, due bersagli che evolvono separatamente`() {
        val w0 = wall(0, end = EndState.PARTIAL, spans = listOf(0.0 to 3.0)); val w1 = wall(1, z = 0.0, start = EndState.PARTIAL, spans = listOf(1.0 to 4.0))
        val corner = listOf(ext(0, 3.0, 3.0, 4.0, 3.0), ext(1, 4.0, 3.0, 1.0, 0.0, end = false))
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(w0, w1), stretches = corner))
        val req = a.scans.single().m5RequestIds.filter { it.startsWith("CORNER-") }
        assertEquals(1, req.size, "M5 produce una sola richiesta d'angolo")
        assertEquals(req, a.t("SW1-END").last.m5RequestIds); assertEquals(req, a.t("SW2-START").last.m5RequestIds)
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, spans = listOf(0.0 to 3.0)), w1), stretches = listOf(ext(1, 4.0, 3.0, 1.0, 0.0, end = false))))
        ok(b)
        assertEquals(TargetState.RESOLVED, b.t("SW1-END").state)
        assertEquals(TargetState.PERSISTENT, b.t("SW2-START").state)
    }

    @Test
    fun `9 - ordine diverso di pareti e richieste - stessa sessione`() {
        val walls = listOf(wall(0, end = EndState.PARTIAL), wall(1, z = 0.0, views = 1), wall(2, z = 6.0, spans = listOf(0.0 to 1.0, 1.6 to 4.0)))
        val a1 = RescanSessionEngine.update(null, scan("A", 10, walls)); val a2 = RescanSessionEngine.update(null, scan("A", 10, walls, reverse = true))
        assertEquals(RescanSessionJson.encode(a1), RescanSessionJson.encode(a2))
        val b1 = RescanSessionEngine.update(a1, scan("B", 20, walls)); val b2 = RescanSessionEngine.update(a2, scan("B", 20, walls, reverse = true))
        assertEquals(RescanSessionJson.encode(b1), RescanSessionJson.encode(b2))
    }

    @Test
    fun `10 - coordinate leggermente diverse, stessa parete - stessa chiave`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(7, z = 3.01, x0 = 0.02, end = EndState.PARTIAL))))
        ok(b)
        val m = b.wallMatches.last()
        assertEquals(MatchStatus.MATCH_FOUND, m.status); assertEquals("SW1", m.previousKey); assertEquals(7, m.currentR3WallId)
        assertEquals(TargetState.PERSISTENT, b.t("SW1-END").state, "id R3 diverso (W7), stessa chiave di sessione")
        assertTrue(b.targets.none { it.key.startsWith("SW2") })
    }

    @Test
    fun `10b - parete spostata oltre la tolleranza - NO_GEOMETRIC_MATCH con diagnostica, mai RESOLVED`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, z = 3.10))))
        ok(b)
        val m = b.wallMatches.first { it.scanIndex == 1 && it.previousKey == "SW1" }
        assertEquals(MatchStatus.NO_GEOMETRIC_MATCH, m.status); assertEquals(0, m.nearestR3WallId)
        assertEquals(0.10, m.deltaPositionM!!, 1e-9); assertTrue("POSITION" in m.failedRules)
        val t = b.t("SW1-END"); assertEquals(TargetState.PERSISTENT, t.state); assertEquals(TransitionReason.NOT_REFOUND, t.history.last().reason)
    }

    @Test
    fun `11 - richiesta risolta che ricompare - RESOLVED NEW PROPOSED, storia intatta`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0))))
        val c = RescanSessionEngine.update(b, scan("C", 30, listOf(wall(0, end = EndState.UNCERTAIN))))
        ok(c)
        val t = c.t("SW1-END")
        assertEquals(TargetState.PROPOSED, t.state); assertEquals(1, t.reopenCount)
        assertEquals(listOf(TargetState.NEW, TargetState.PROPOSED, TargetState.ATTEMPTED, TargetState.RESOLVED, TargetState.NEW, TargetState.PROPOSED), t.history.map { it.to })
        assertEquals(TransitionReason.REAPPEARED, t.history[4].reason)
    }

    @Test
    fun `12 e 15 - serializzazione deterministica e determinismo completo`() {
        val walls = listOf(wall(0, end = EndState.PARTIAL), wall(1, z = 0.0, views = 1))
        val a = RescanSessionEngine.update(null, scan("A", 10, walls)); val b = RescanSessionEngine.update(a, scan("B", 20, walls))
        val text = RescanSessionJson.encode(b)
        assertEquals(b, RescanSessionJson.decode(text))
        assertEquals(text, RescanSessionJson.encode(RescanSessionJson.decode(text)))
        val again = RescanSessionEngine.update(RescanSessionEngine.update(null, scan("A", 10, walls)), scan("B", 20, walls))
        assertEquals(text, RescanSessionJson.encode(again))
        assertEquals(RescanSessionReport.eventsCsv(b), RescanSessionReport.eventsCsv(again))
    }

    @Test
    fun `13 e 14 - nessun EXHAUSTED e nessun bersaglio eliminato, anche dopo molti tentativi`() {
        assertTrue(TargetState.values().none { it.name.contains("EXHAUST") })
        var s = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        var count = s.targets.size
        for (i in 1..6) {
            s = RescanSessionEngine.update(s, scan("S$i", 10 + 10 * i, listOf(wall(0, end = EndState.PARTIAL))))
            assertTrue(s.targets.size >= count); count = s.targets.size
        }
        ok(s)
        assertEquals(TargetState.PERSISTENT, s.t("SW1-END").state); assertEquals(6, s.t("SW1-END").attempts)
    }

    @Test
    fun `16 - sistema di coordinate diverso - nessun confronto, tutto NEW, nessuna storia persa`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL)), state = PerimeterState.PARTIAL))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, end = EndState.PARTIAL)), state = PerimeterState.PARTIAL, created = 2000L))
        ok(b)
        assertEquals(FrameStatus.COORDINATE_FRAME_MISMATCH, b.scans.last().frameStatus)
        assertEquals(TargetState.PROPOSED, b.t("SW1-END").state, "il bersaglio del sistema precedente non viene toccato"); assertEquals(0, b.t("SW1-END").attempts)
        val n = b.t("SW2-END"); assertEquals(MatchStatus.COORDINATE_FRAME_MISMATCH, n.history.first().matchStatus); assertEquals(1, n.frameEpoch)
        assertEquals(TargetState.PROPOSED, b.t("PERIMETER").state); assertEquals(TargetState.PROPOSED, b.t("PERIMETER-2").state)
        assertTrue(b.wallMatches.any { it.status == MatchStatus.COORDINATE_FRAME_MISMATCH && it.previousKey == "SW1" })
        // Anche lo stesso inizio ma pose diverse (non prefisso) è un mismatch.
        val c = RescanSessionEngine.update(a, scan("C", 5, listOf(wall(0, end = EndState.PARTIAL))))
        assertEquals(FrameStatus.COORDINATE_FRAME_MISMATCH, c.scans.last().frameStatus)
    }

    @Test
    fun `17 - perimetro - avanzamento OPEN PARTIAL UNCERTAIN e chiusura`() {
        val walls = listOf(wall(0), wall(1, z = 0.0))
        val miss = { len: Double -> MissingSide(0, 1, 4.0, 3.0, EndState.OBSERVED, EndReason.CORNER, 4.0, 3.0 - len, EndState.OBSERVED, EndReason.CORNER, "x") }
        val a = RescanSessionEngine.update(null, scan("A", 10, walls, state = PerimeterState.OPEN))
        val b = RescanSessionEngine.update(a, scan("B", 20, walls, state = PerimeterState.PARTIAL, missing = miss(2.0)))
        assertEquals(TransitionReason.PERIMETER_STATE_ADVANCED, b.t("PERIMETER").history.last().reason)
        val c = RescanSessionEngine.update(b, scan("C", 30, walls, state = PerimeterState.PARTIAL, missing = miss(1.5)))
        assertEquals(TransitionReason.MISSING_SIDE_REDUCED, c.t("PERIMETER").history.last().reason); assertEquals(0.5, c.t("PERIMETER").history.last().changeM!!, 1e-9)
        val d = RescanSessionEngine.update(c, scan("D", 40, walls, state = PerimeterState.CLOSED))
        ok(d)
        assertEquals(TargetState.RESOLVED, d.t("PERIMETER").state); assertEquals(TransitionReason.PERIMETER_CLOSED, d.t("PERIMETER").history.last().reason)
    }

    @Test
    fun `18 - le transizioni non ammesse sono rifiutate`() {
        val s = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL))))
        val t = s.t("SW1-END")
        val bad = s.copy(targets = listOf(t.copy(history = t.history + TransitionEvent(1, TargetState.PROPOSED, TargetState.RESOLVED, TransitionReason.ENDPOINT_OBSERVED))))
        assertTrue(RescanSessionEngine.validate(bad).isNotEmpty())
        for ((from, to) in listOf(TargetState.NEW to TargetState.RESOLVED, TargetState.PERSISTENT to TargetState.RESOLVED, TargetState.PROPOSED to TargetState.NEW))
            assertTrue((from to to) !in RescanSessionEngine.ALLOWED)
    }

    @Test
    fun `19 - sequenza A B C - migliorata poi risolta, storia ricostruibile`() {
        val a = RescanSessionEngine.update(null, scan("A", 10, listOf(wall(0, end = EndState.PARTIAL)), stretches = listOf(ext(0, 4.0, 3.0, 4.8, 3.0))))
        val b = RescanSessionEngine.update(a, scan("B", 20, listOf(wall(0, end = EndState.PARTIAL, spans = listOf(0.0 to 4.4))), stretches = listOf(ext(0, 4.4, 3.0, 4.8, 3.0))))
        val c = RescanSessionEngine.update(b, scan("C", 30, listOf(wall(0, spans = listOf(0.0 to 4.8)))))
        ok(c)
        val h = c.t("SW1-END").history
        assertEquals(listOf(TargetState.NEW, TargetState.PROPOSED, TargetState.ATTEMPTED, TargetState.IMPROVED, TargetState.ATTEMPTED, TargetState.RESOLVED), h.map { it.to })
        assertEquals(listOf(0, 0, 1, 1, 2, 2), h.map { it.scanIndex })
        assertEquals(TransitionReason.UNOBSERVED_LENGTH_REDUCED, h[3].reason); assertEquals(0.4, h[3].changeM!!, 1e-9)
        assertEquals(TransitionReason.ENDPOINT_OBSERVED, h[5].reason)
        assertEquals(listOf(FrameStatus.FIRST_SCAN, FrameStatus.SAME_FRAME_PREFIX, FrameStatus.SAME_FRAME_PREFIX), c.scans.map { it.frameStatus })
    }
}
