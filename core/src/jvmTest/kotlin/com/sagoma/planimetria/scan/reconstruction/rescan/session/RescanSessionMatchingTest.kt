package com.sagoma.planimetria.scan.reconstruction.rescan.session

import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.EvidenceQuality
import com.sagoma.planimetria.scan.reconstruction.GapReason
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M5.1.1 — regressioni dei difetti trovati dall'audit (F1–F5): split, superficie parallela, merge con estremità interne, alias dopo
 * il merge, duplicati, tratti confluiti in un'estremità, eventi iniziali senza "precedente", ordine, serializzazione e ripresa.
 * Pareti orizzontali a z = 3 (normale verso −z); B contiene sempre A come prefisso (stessa sessione).
 */
class RescanSessionMatchingTest {

    private fun wall(
        id: Int, z: Double = 3.0, x0: Double = 0.0, spans: List<Pair<Double, Double>> = listOf(0.0 to 4.0),
        start: EndState = EndState.OBSERVED, end: EndState = EndState.OBSERVED, views: Int = 4,
    ): EstimatedWall {
        val s = spans.first().first; val e = spans.last().second
        val geom = WallGeometry(0.0, -1.0, -z, x0, z, 1.0, 0.0, s, e, 0.0, 2.6, 0.0, 2.6, 0.0, spans.map { WallSpan(it.first, it.second) })
        val gaps = spans.zipWithNext().map { (a, b) -> WallGap(a.second, b.first, GapReason.NOT_SEEN) }
        val ev = WallEvidence(5000, 4800, 40, views, 60.0, 2.0, 0.004, 0.003, 0.95, 0.02, if (views >= 2) EvidenceQuality.HIGH else EvidenceQuality.MEDIUM, emptyList())
        return EstimatedWall(id, listOf(id), geom, WallUncertainty(0.005, 0.3, 0.01, 0.01, 0.014, 0.0, 0.0, 0.005, 0.005), ev,
            WallEnd(s, start, if (start == EndState.OBSERVED) EndReason.CORNER else EndReason.NOT_SEEN, 0.01),
            WallEnd(e, end, if (end == EndState.OBSERVED) EndReason.CORNER else EndReason.NOT_SEEN, 0.01), gaps, WallThickness(ThicknessState.UNKNOWN, note = ""), 0.9, emptyList(), emptyList())
    }

    private fun scan(poses: Int, walls: List<EstimatedWall>, stretches: List<UnobservedStretch> = emptyList(), reverse: Boolean = false): ScanInput {
        val room = walls.map { it.id }
        val facts = PerimeterFacts(PerimeterState.CLOSED, room, room.associateWith { "x" }, stretches.map { Triple(it.wallId, it.kind, it.lengthM) }, null, null, null, null, null)
        val q = QualityEngine.evaluate(walls, facts, emptyMap(), walls.associate { it.id to 4.0 }, 12.0)
        val geo = PerimeterGeometry(PerimeterState.CLOSED, null, stretches, emptyList())
        val p = RescanDirector.plan(q, walls, geo, emptyMap(), emptyMap())
        val plan = if (reverse) RescanPlan(p.config, p.decision, p.requests.reversed(), p.limitations, p.decisionReason) else p
        val ps = (0 until poses).map { PoseSample(seq = it, timestampNs = it * 1_000_000L, tracking = "TRACKING", camera = RecPose(it * 0.01, 1.4, 0.0)) }
        return ScanInput("S$poses", 1000L, ps, emptyList(), if (reverse) walls.reversed() else walls, q, plan, geo)
    }

    private fun run(vararg steps: List<EstimatedWall>, reverse: Boolean = false, roundTrip: Boolean = false): RescanSession {
        var s: RescanSession? = null
        for ((i, w) in steps.withIndex()) {
            val prev = if (roundTrip) s?.let { RescanSessionJson.decode(RescanSessionJson.encode(it)) } else s
            s = RescanSessionEngine.update(prev, scan(10 * (i + 1), w, reverse = reverse))
            assertEquals(emptyList(), RescanSessionEngine.validate(s), "transizioni ammesse")
        }
        return s!!
    }

    private fun RescanSession.t(key: String) = assertNotNull(targets.firstOrNull { it.key == key }, "bersaglio $key; presenti ${targets.map { it.key }}")
    private fun SessionTarget.lastEvent() = history.last()

    // Scenari.
    private val splitA = listOf(wall(0, end = EndState.PARTIAL))
    private val splitB = listOf(wall(0, spans = listOf(0.0 to 1.5)), wall(1, spans = listOf(2.5 to 4.0), end = EndState.PARTIAL))
    private val mergeA = listOf(wall(0, spans = listOf(0.0 to 1.5), end = EndState.PARTIAL), wall(1, spans = listOf(2.5 to 4.0), start = EndState.PARTIAL, views = 1))
    private val mergeB = listOf(wall(0, spans = listOf(0.0 to 4.0), start = EndState.PARTIAL, views = 1))

    @Test
    fun `1 - split - l'estremita aperta non e risolta dal frammento sbagliato`() {
        val s = run(splitA, splitB)
        assertEquals(MatchStatus.SPLIT, s.wallMatches.last { it.previousKey == "SW1" }.status)
        val t = s.t("SW1-END")
        assertEquals(TargetState.PERSISTENT, t.state)
        assertEquals(TransitionReason.UNOBSERVED_NOT_MEASURABLE, t.lastEvent().reason)
        assertEquals(4.0, t.lastEvent().current!!.pointX); assertEquals(3.0, t.lastEvent().current!!.pointZ)
        assertEquals("PARTIAL", t.lastEvent().current!!.endpointState)
        assertEquals(1, s.targets.count { it.kind == TargetKind.WALL_ENDPOINT && it.last.open && it.last.pointX == 4.0 }, "nessun duplicato per l'estremità a x = 4")
        assertEquals(listOf(TargetState.NEW, TargetState.PROPOSED, TargetState.ATTEMPTED, TargetState.PERSISTENT), t.history.map { it.to })
    }

    @Test
    fun `2 - superficie parallela vicina - nessuna falsa risoluzione`() {
        val s = run(splitA, listOf(wall(0, z = 3.04, spans = listOf(1.0 to 3.0))))
        assertEquals(MatchStatus.MATCH_FOUND, s.wallMatches.last().status, "la parallela supera la tolleranza approvata")
        val t = s.t("SW1-END")
        assertEquals(TargetState.PERSISTENT, t.state)
        assertEquals(TransitionReason.TARGET_NOT_VERIFIABLE, t.lastEvent().reason, "il frammento non raggiunge il vecchio punto (1 m > 0,20 m)")
        assertTrue(t.history.none { it.to == TargetState.RESOLVED })
    }

    @Test
    fun `3 e 6 - merge - estremita interne coperte da superficie osservata, risolte con INTERNAL_COVERAGE`() {
        val s = run(mergeA, mergeB)
        assertEquals(setOf(MatchStatus.MERGE), s.wallMatches.filter { it.scanIndex == 1 }.map { it.status }.toSet())
        assertEquals(listOf(WallAlias("SW2", "SW1", 1, MatchStatus.MERGE)), s.wallAliases)
        for (k in listOf("SW1-END", "SW2-START")) {
            val t = s.t(k)
            assertEquals(TargetState.RESOLVED, t.state, k); assertEquals(TransitionReason.INTERNAL_COVERAGE, t.lastEvent().reason, k)
            assertTrue(t.internal); assertEquals(0, t.lastEvent().current!!.r3WallId)
        }
        assertEquals(1.5, s.t("SW1-END").lastEvent().current!!.pointX, "il punto verificato è il vecchio punto, non un'estremità lontana")
        // Il difetto nuovo a x = 0 non viene consumato: è un bersaglio nuovo.
        val n = s.t("SW1-START"); assertEquals(TargetState.PROPOSED, n.state); assertEquals(0.0, n.last.pointX)
        assertEquals(TargetState.PERSISTENT, s.t("SW2-VIEWS").state)
    }

    @Test
    fun `4 - merge e terza scansione - nessun bersaglio fantasma`() {
        val s = run(mergeA, mergeB, mergeB)
        val v = s.t("SW2-VIEWS")
        assertEquals(TargetState.PERSISTENT, v.state); assertEquals(TransitionReason.NO_MEASURABLE_IMPROVEMENT, v.lastEvent().reason)
        assertEquals(2, v.attempts)
        assertTrue(s.targets.none { it.key == "SW1-VIEWS" }, "nessun duplicato NEW per lo stesso difetto")
        assertTrue(s.targets.none { it.lastEvent().reason == TransitionReason.NOT_REFOUND })
        assertEquals(TargetState.RESOLVED, s.t("SW1-END").state, "le estremità interne restano risolte")
    }

    @Test
    fun `4b - merge di due pareti con lo stesso difetto - COVERED_BY_OTHER_TARGET, nessuna risoluzione`() {
        val a = listOf(wall(0, spans = listOf(0.0 to 1.5), views = 1), wall(1, spans = listOf(2.5 to 4.0), views = 1))
        val b = listOf(wall(0, spans = listOf(0.0 to 4.0), views = 1))
        val s = run(a, b, b)
        assertEquals(TargetState.PERSISTENT, s.t("SW1-VIEWS").state); assertEquals(TransitionReason.NO_MEASURABLE_IMPROVEMENT, s.t("SW1-VIEWS").lastEvent().reason)
        val d = s.t("SW2-VIEWS")
        assertEquals(TargetState.PERSISTENT, d.state); assertEquals(TransitionReason.COVERED_BY_OTHER_TARGET, d.lastEvent().reason)
        assertEquals("SW1-VIEWS", d.lastEvent().relatedTargetKey)
        assertEquals(2, d.history.count { it.reason == TransitionReason.COVERED_BY_OTHER_TARGET }, "anche nella scansione successiva al merge")
        assertTrue(s.targets.none { it.state == TargetState.RESOLVED })
    }

    @Test
    fun `5 - estremita non osservata e non coperta - mai RESOLVED`() {
        // Merge con il vecchio punto (1,5) dentro un gap della parete fusa: BECAME_INTERNAL_GAP, collegato al tratto.
        val s = run(listOf(wall(0, spans = listOf(0.0 to 1.5), end = EndState.PARTIAL), wall(1, spans = listOf(2.5 to 4.0))),
            listOf(wall(0, spans = listOf(0.0 to 1.0, 2.0 to 4.0))))
        val t = s.t("SW1-END")
        assertEquals(TargetState.PERSISTENT, t.state); assertEquals(TransitionReason.BECAME_INTERNAL_GAP, t.lastEvent().reason); assertTrue(t.internal)
        val seg = s.targets.single { it.kind == TargetKind.WALL_SEGMENT }
        assertEquals(seg.key, t.lastEvent().relatedTargetKey, "il gap è seguito come tratto")
        // Una parete che si accorcia oltre 0,20 m: non verificabile (effetto conservativo approvato), mai risolta.
        val r = run(splitA, listOf(wall(0, spans = listOf(0.0 to 3.5), end = EndState.PARTIAL)))
        assertEquals(TransitionReason.TARGET_NOT_VERIFIABLE, r.t("SW1-END").lastEvent().reason)
        assertTrue(r.targets.flatMap { it.history }.none { it.to == TargetState.RESOLVED })
    }

    @Test
    fun `5b - parete che si allunga da 4,0 a 4,3 m resta IMPROVED, non RESOLVED`() {
        val a = RescanSessionEngine.update(null, scan(10, splitA, listOf(UnobservedStretch(0, 4.0, 3.0, 4.6, 3.0, "prolungamento fino all'angolo", "x"))))
        val b = RescanSessionEngine.update(a, scan(20, listOf(wall(0, spans = listOf(0.0 to 4.3), end = EndState.PARTIAL)), listOf(UnobservedStretch(0, 4.3, 3.0, 4.6, 3.0, "prolungamento fino all'angolo", "x"))))
        val e = b.t("SW1-END").lastEvent()
        assertEquals(TargetState.IMPROVED, e.to); assertEquals(TransitionReason.UNOBSERVED_LENGTH_REDUCED, e.reason); assertEquals(0.3, e.changeM!!, 1e-9)
    }

    @Test
    fun `F5 - tratto ancora non osservato ma confluito in un'estremita aperta`() {
        val s = run(listOf(wall(0, spans = listOf(0.0 to 2.0, 2.6 to 4.0))), listOf(wall(0, spans = listOf(0.0 to 2.0, 2.6 to 2.7), end = EndState.PARTIAL)))
        val seg = s.t("SW1-SEG-1")
        assertEquals(TargetState.PERSISTENT, seg.state); assertEquals(TransitionReason.SEGMENT_ABSORBED_BY_ENDPOINT, seg.lastEvent().reason)
        assertEquals("SW1-END", seg.lastEvent().relatedTargetKey)
        assertTrue(s.targets.flatMap { it.history }.none { it.reason == TransitionReason.NOT_REFOUND })
    }

    @Test
    fun `F4 - FIRST_SEEN ed EMITTED senza valore precedente, anche alla riapertura`() {
        val s = run(splitA, listOf(wall(0)), listOf(wall(0, end = EndState.UNCERTAIN)))
        val h = s.t("SW1-END").history
        for (e in h.filter { it.reason == TransitionReason.FIRST_SEEN || it.reason == TransitionReason.EMITTED }) assertNull(e.previous, "${e.reason} senza precedente")
        assertNotNull(h.first { it.reason == TransitionReason.REAPPEARED }.previous, "alla riapertura il precedente (risolto) è noto")
        assertTrue(RescanSessionReport.eventsCsv(s).lines().any { it.contains("EMITTED") && it.contains(";nessuno;") })
    }

    @Test
    fun `7 - ordine di pareti e richieste indifferente anche con split e merge`() {
        for ((a, b) in listOf(splitA to splitB, mergeA to mergeB)) {
            assertEquals(RescanSessionJson.encode(run(a, b, b)), RescanSessionJson.encode(run(a, b, b, reverse = true)))
        }
    }

    @Test
    fun `8 - serializzazione e ripresa dopo split e merge, sessioni precedenti leggibili`() {
        for ((a, b) in listOf(splitA to splitB, mergeA to mergeB)) {
            val direct = run(a, b, b); val resumed = run(a, b, b, roundTrip = true)
            val text = RescanSessionJson.encode(direct)
            assertEquals(text, RescanSessionJson.encode(resumed))
            assertEquals(text, RescanSessionJson.encode(RescanSessionJson.decode(text)))
        }
        // Una sessione scritta senza i campi nuovi (alias, internal, relatedTargetKey) resta leggibile con i valori di default.
        val old = Json { encodeDefaults = false }.encodeToString(RescanSession.serializer(), run(splitA, splitB))
        assertTrue(!old.contains("wallAliases") && !old.contains("relatedTargetKey") && !old.contains("\"internal\""))
        val back = RescanSessionJson.decode(old)
        assertEquals(emptyList(), back.wallAliases); assertTrue(back.targets.none { it.internal })
        assertEquals(emptyList(), RescanSessionEngine.validate(RescanSessionEngine.update(back, scan(30, splitB))))
    }
}
