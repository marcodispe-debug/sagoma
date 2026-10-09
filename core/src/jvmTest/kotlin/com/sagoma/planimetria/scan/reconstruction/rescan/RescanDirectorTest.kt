package com.sagoma.planimetria.scan.reconstruction.rescan

import com.sagoma.planimetria.scan.reconstruction.AmbiguityChannel
import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.EvidenceQuality
import com.sagoma.planimetria.scan.reconstruction.GapReason
import com.sagoma.planimetria.scan.reconstruction.MeasurementQuality
import com.sagoma.planimetria.scan.reconstruction.MissingSide
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.PerpendicularEvidence
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
import com.sagoma.planimetria.scan.reconstruction.bench.BenchPipeline
import com.sagoma.planimetria.scan.reconstruction.bench.Scenarios
import com.sagoma.planimetria.scan.reconstruction.quality.PerimeterFacts
import com.sagoma.planimetria.scan.reconstruction.quality.QualityEngine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M5 — RescanDirector con ingressi M4/R3/R4 costruiti a mano (più un controllo end-to-end sul simulatore). Verifica localizzazione,
 * gravità, priorità deterministica, fusione delle cause nello stesso punto, stop NO_RESCAN_NEEDED e assenza di geometria inventata.
 */
class RescanDirectorTest {

    /** Parete orizzontale lungo x a quota z = [z] (dall'origine x0), normale verso −z (le camere stanno a z minore). */
    private fun wall(
        id: Int, z: Double = 3.0, x0: Double = 0.0, spans: List<Pair<Double, Double>> = listOf(0.0 to 4.0),
        start: EndState = EndState.OBSERVED, startReason: EndReason = EndReason.CORNER, end: EndState = EndState.OBSERVED, endReason: EndReason = EndReason.CORNER,
        views: Int = 4, sigmaPos: Double = 0.005, sigmaHead: Double = 0.3, sources: List<Int> = listOf(id), gapReason: GapReason = GapReason.NOT_SEEN,
    ): EstimatedWall {
        val s = spans.first().first; val e = spans.last().second
        val geom = WallGeometry(0.0, -1.0, -z, x0, z, 1.0, 0.0, s, e, 0.0, 2.6, 0.0, 2.6, 0.0, spans.map { WallSpan(it.first, it.second) })
        val gaps = spans.zipWithNext().map { (a, b) -> WallGap(a.second, b.first, gapReason) }
        val ev = WallEvidence(5000, 4800, 40, views, 60.0, 2.0, 0.004, 0.003, 0.95, 0.02, if (views >= 2) EvidenceQuality.HIGH else EvidenceQuality.MEDIUM, emptyList())
        return EstimatedWall(id, sources, geom, WallUncertainty(sigmaPos, sigmaHead, 0.01, 0.01, 0.014, 0.0, 0.0, sigmaPos, 0.005), ev,
            WallEnd(s, start, startReason, 0.01), WallEnd(e, end, endReason, 0.01), gaps, WallThickness(ThicknessState.UNKNOWN, note = ""), 0.9, emptyList(), emptyList())
    }

    private fun amb(id: Int, score: Double, stability: Double, side: String = "left"): SurfaceAmbiguity {
        val on = AmbiguityChannel(10, score, 1.0, 0.6, 2); val off = AmbiguityChannel(10, 0.0, 0.0, null, 0)
        return SurfaceAmbiguity(id, score, off, if (side == "above") on else off, if (side == "left") on else off, if (side == "right") on else off, null,
            PerpendicularEvidence(0, null, 0), 2, stability, 2, 2, 2, 1.0, false, MeasurementQuality.HIGH)
    }

    private class Case(val walls: List<EstimatedWall>, val state: PerimeterState?, val room: List<Int>, val missing: MissingSide?, val stretches: List<UnobservedStretch>, val ambiguity: Map<Int, SurfaceAmbiguity>)

    private fun case(walls: List<EstimatedWall>, state: PerimeterState? = PerimeterState.CLOSED, room: List<Int> = walls.map { it.id }, missing: MissingSide? = null,
                     stretches: List<UnobservedStretch> = emptyList(), ambiguity: Map<Int, SurfaceAmbiguity> = emptyMap()) = Case(walls, state, room, missing, stretches, ambiguity)

    private val plans = mapOf(0 to SurfacePlan(2.0, 3.0, 1.0, 0.0, -2.0, 2.0), 1 to SurfacePlan(2.0, 0.0, 1.0, 0.0, -2.0, 2.0))

    private fun plan(c: Case): RescanPlan {
        val facts = PerimeterFacts(c.state, c.room, c.room.associateWith { "nel perimetro principale" },
            c.stretches.filter { it.kind != "gap interno R3" }.map { Triple(it.wallId, it.kind, it.lengthM) }, null, null, null, null, null)
        val q = QualityEngine.evaluate(c.walls, facts, c.ambiguity, c.walls.associate { it.id to 4.0 }, 12.0)
        return RescanDirector.plan(q, c.walls, PerimeterGeometry(c.state, c.missing, c.stretches, emptyList()), c.ambiguity, plans)
    }

    private fun RescanPlan.req(id: String) = assertNotNull(requests.firstOrNull { it.id == id }, "richiesta $id; presenti: ${requests.map { it.id }}")

    @Test
    fun `1 - start PARTIAL produce una richiesta localizzata allo start`() {
        val p = plan(case(listOf(wall(0, start = EndState.PARTIAL, startReason = EndReason.OCCLUDED))))
        val r = p.req("W0-START")
        assertEquals(RescanRequestType.RESCAN_WALL_START, r.type); assertEquals(Severity.BLOCKING, r.severity)
        assertEquals(TargetKind.POINT, r.target.kind); assertEquals(0.0, r.target.ax); assertEquals(3.0, r.target.az)
        assertEquals(ObservationKind.OBLIQUE_PAST_OCCLUSION, r.observation); assertEquals("OCCLUDED", r.sourceReason)
        assertEquals(false, r.visibilityVerified)
        assertEquals(RescanDecision.RESCAN_RECOMMENDED, p.decision)
    }

    @Test
    fun `2 - end UNCERTAIN produce una richiesta all'end`() {
        val r = plan(case(listOf(wall(0, end = EndState.UNCERTAIN, endReason = EndReason.UNSTABLE)))).req("W0-END")
        assertEquals(RescanRequestType.RESCAN_WALL_END, r.type)
        assertEquals(4.0, r.target.ax); assertEquals(3.0, r.target.az)
        assertEquals(ObservationKind.MORE_VIEWS_OF_END, r.observation)
        assertEquals(0.0, r.lookDirX!!, 0.0); assertEquals(1.0, r.lookDirZ!!, 0.0)
    }

    @Test
    fun `3 - grande gap interno produce una richiesta sul body`() {
        val r = plan(case(listOf(wall(0, spans = listOf(0.0 to 1.5, 2.5 to 4.0))))).req("W0-BODY-0")
        assertEquals(RescanRequestType.RESCAN_WALL_BODY, r.type); assertEquals(Severity.BLOCKING, r.severity)
        assertEquals(1.5, r.target.ax); assertEquals(2.5, r.target.bx); assertEquals(1.0, r.unobservedLengthM!!, 1e-12)
    }

    @Test
    fun `3b - soglia di 20 cm inclusa, sotto nessuna richiesta`() {
        assertTrue(plan(case(listOf(wall(0, spans = listOf(0.0 to 1.5, 1.69 to 4.0))))).requests.none { it.type == RescanRequestType.RESCAN_WALL_BODY })
        assertTrue(plan(case(listOf(wall(0, spans = listOf(0.0 to 1.5, 1.75 to 4.0))))).requests.any { it.type == RescanRequestType.RESCAN_WALL_BODY })
        val exact = plan(case(listOf(wall(0)), stretches = listOf(UnobservedStretch(0, 4.0, 3.0, 4.2, 3.0, "prolungamento fino all'angolo", "x"))))
        assertEquals(0.2, exact.req("W0-END").unobservedLengthM!!, 1e-9)
        val below = plan(case(listOf(wall(0)), stretches = listOf(UnobservedStretch(0, 4.0, 3.0, 4.19, 3.0, "prolungamento fino all'angolo", "x"))))
        assertTrue(below.requests.isEmpty())
    }

    @Test
    fun `4 - poche viste producono una richiesta di nuova vista`() {
        val r = plan(case(listOf(wall(0, views = 1)))).req("W0-VIEWS")
        assertEquals(RescanRequestType.RESCAN_LOW_VIEW_COUNT, r.type); assertEquals(Severity.IMPORTANT, r.severity)
        assertEquals(ObservationKind.ADDITIONAL_VIEWPOINT, r.observation)
    }

    @Test
    fun `5 - perimetro OPEN ha la priorita piu alta`() {
        val p = plan(case(listOf(wall(0, views = 1), wall(1, z = 0.0, end = EndState.PARTIAL)), state = PerimeterState.OPEN))
        assertEquals("ROOM-PERIMETER", p.requests.first().id)
        assertEquals(RescanRequestType.RESCAN_OPEN_PERIMETER, p.requests.first().type)
        assertEquals(TargetKind.NONE, p.requests.first().target.kind, "senza lato mancante R4 la posizione non è inventata")
        assertTrue(!p.requests.first().target.positionKnown)
        assertEquals(listOf("ROOM-PERIMETER", "W1-END", "W0-VIEWS"), p.requests.map { it.id })
    }

    @Test
    fun `6 - perimetro UNCERTAIN con lato mancante - una sola richiesta, le estremita confluiscono`() {
        val missing = MissingSide(0, 1, 4.0, 3.0, EndState.PARTIAL, EndReason.NOT_SEEN, 4.0, 0.0, EndState.UNCERTAIN, EndReason.NOT_SEEN, "lato non osservato")
        val p = plan(case(listOf(wall(0, end = EndState.PARTIAL, endReason = EndReason.NOT_SEEN), wall(1, z = 0.0, start = EndState.UNCERTAIN, startReason = EndReason.NOT_SEEN)),
            state = PerimeterState.UNCERTAIN, missing = missing))
        val r = p.req("ROOM-PERIMETER")
        assertEquals(RescanRequestType.RESCAN_UNCERTAIN_PERIMETER, r.type); assertEquals(ObservationKind.CLOSE_MISSING_SIDE, r.observation)
        assertEquals(4.0, r.target.ax); assertEquals(3.0, r.target.az); assertEquals(4.0, r.target.bx); assertEquals(0.0, r.target.bz)
        assertTrue(p.requests.none { it.id == "W0-END" || it.id == "W1-START" }, "nessuna richiesta duplicata sullo stesso punto")
        assertTrue(r.contributingReasons.any { it.startsWith("W0:") } && r.contributingReasons.any { it.startsWith("W1:") })
    }

    @Test
    fun `7 - ambiguita instabile chiede una vista in piu senza classificare, stabile e solo un limite`() {
        val unstable = plan(case(listOf(wall(0)), ambiguity = mapOf(0 to amb(0, 0.4, 0.05, "left"))))
        val r = unstable.req("W0-AMBIGUITY")
        assertEquals(Severity.SECONDARY, r.severity); assertEquals(ObservationKind.VIEW_AMBIGUITY_ZONE, r.observation)
        assertEquals(TargetKind.SEGMENT, r.target.kind); assertEquals(-0.5, r.target.ax!!, 1e-12); assertEquals(-0.05, r.target.bx!!, 1e-12)
        assertEquals(RescanDecision.NO_RESCAN_NEEDED, unstable.decision, "una richiesta secondaria non impedisce lo stop")
        val stable = plan(case(listOf(wall(0)), ambiguity = mapOf(0 to amb(0, 0.4, 0.38, "left"))))
        assertTrue(stable.requests.isEmpty()); assertEquals("STABLE_ALTERNATIVE_SURFACE_EVIDENCE", stable.limitations.single().code)
        val text = RescanReport.json(unstable) + RescanReport.summary(unstable, "t") + RescanReport.json(stable) + RescanReport.summary(stable, "t")
        for (w in listOf("mobile", "armadio", "pilastro", "furniture", "object", "oggetto")) assertTrue(!text.contains(w, ignoreCase = true), "parola semantica: $w")
    }

    @Test
    fun `8 - nessun difetto produce NO_RESCAN_NEEDED`() {
        val p = plan(case(listOf(wall(0), wall(1, z = 0.0))))
        assertEquals(RescanDecision.NO_RESCAN_NEEDED, p.decision)
        assertTrue(p.requests.isEmpty())
    }

    @Test
    fun `9 - determinismo`() {
        val c = case(listOf(wall(0, end = EndState.PARTIAL, spans = listOf(0.0 to 1.0, 1.6 to 4.0)), wall(1, z = 0.0, views = 1), wall(2, z = 6.0, start = EndState.UNCERTAIN)),
            state = PerimeterState.PARTIAL, room = listOf(0, 1), ambiguity = mapOf(0 to amb(0, 0.3, 0.1)))
        assertEquals(RescanReport.json(plan(c)), RescanReport.json(plan(c)))
        assertEquals(RescanReport.csv(plan(c)), RescanReport.csv(plan(c)))
    }

    @Test
    fun `10 - nessuna richiesta contiene geometria inventata`() {
        val walls = listOf(wall(0, end = EndState.PARTIAL, spans = listOf(0.0 to 1.0, 1.6 to 4.0)), wall(1, z = 0.0, x0 = 0.5, views = 1), wall(2, z = 6.0, start = EndState.UNCERTAIN))
        val stretches = listOf(UnobservedStretch(1, 4.5, 0.0, 4.9, 0.0, "prolungamento fino all'angolo", "x"), UnobservedStretch(2, 7.0, 6.0, 7.5, 6.0, "gap tra frammenti W2–W3", "x"))
        val p = plan(case(walls, state = PerimeterState.PARTIAL, room = listOf(0, 1), stretches = stretches, ambiguity = mapOf(0 to amb(0, 0.3, 0.1, "right"))))
        val known = mutableListOf<Pair<Double, Double>>()
        for (w in walls) { val g = w.geometry; (listOf(g.startU, g.endU) + w.gaps.flatMap { listOf(it.fromU, it.toU) }).forEach { u -> g.pointAt(u).let { known.add(it[0] to it[1]) } } }
        for (s in stretches) { known.add(s.ax to s.az); known.add(s.bx to s.bz) }
        plans.values.forEach { sp -> listOf(sp.uMin - 0.5, sp.uMin - 0.05, sp.uMax + 0.05, sp.uMax + 0.5, sp.uMin, sp.uMax).forEach { u -> known.add(sp.cx + sp.ux * u to sp.cz + sp.uz * u) } }
        for (r in p.requests) {
            val t = r.target
            for (pt in listOfNotNull(t.ax?.let { it to t.az!! }, t.bx?.let { it to t.bz!! })) assertTrue(known.any { abs(it.first - pt.first) < 1e-12 && abs(it.second - pt.second) < 1e-12 }, "${r.id}: coordinata $pt non presente nei dati")
            if (t.kind == TargetKind.NONE) assertTrue(!t.positionKnown)
            assertEquals(false, r.visibilityVerified)
        }
    }

    @Test
    fun `11 - conflitto tra difetti - priorita deterministica e cause fuse`() {
        val walls = listOf(
            wall(0, end = EndState.PARTIAL, spans = listOf(0.0 to 3.5, 3.85 to 4.0)),
            wall(1, z = 0.0, views = 1, sigmaPos = 0.05),
            wall(2, z = 6.0, sigmaHead = 3.0, spans = listOf(0.0 to 1.0, 2.0 to 4.0)),
            wall(3, z = 9.0, spans = listOf(0.0 to 1.0, 1.5 to 4.0)),
        )
        val p = plan(case(walls, state = PerimeterState.CLOSED, ambiguity = mapOf(3 to amb(3, 0.5, 0.0))))
        // Gap di 35 cm vicino all'END PARTIAL di W0: una sola richiesta W0-END con il gap tra le cause.
        val end = p.req("W0-END")
        assertTrue(p.requests.none { it.id.startsWith("W0-BODY") }); assertTrue(end.contributingReasons.any { it.contains("tratto non osservato") })
        // Ordine: estremità/tratti (BLOCKING, lunghezza decrescente) → viste (IMPORTANT) → misura (IMPORTANT) → ambiguità (SECONDARY).
        assertEquals(listOf("W0-END", "W2-BODY-0", "W3-BODY-0", "W1-VIEWS", "W2-MEASUREMENT", "W3-AMBIGUITY"), p.requests.map { it.id })
        assertTrue(p.req("W1-VIEWS").contributingReasons.any { it.startsWith("misura LOW") }, "la misura LOW della stessa parete confluisce nelle viste")
        assertEquals((1..p.requests.size).toList(), p.requests.map { it.priorityRank })
    }

    @Test
    fun `12 - pareti fuori dalla stanza - una sola richiesta secondaria per parete`() {
        val p = plan(case(listOf(wall(0), wall(1, z = 0.0, start = EndState.PARTIAL, end = EndState.UNCERTAIN, views = 1)), room = listOf(0)))
        val out = p.requests.filter { it.wallId == 1 }
        assertEquals(1, out.size); assertEquals(Severity.SECONDARY, out.single().severity); assertTrue(!out.single().inRoom)
        assertTrue(out.single().contributingReasons.size >= 2)
        assertEquals(RescanDecision.NO_RESCAN_NEEDED, p.decision)
    }

    @Test
    fun `13 - due prolungamenti verso lo stesso angolo dedotto producono una sola richiesta`() {
        val walls = listOf(wall(0, end = EndState.PARTIAL, endReason = EndReason.NOT_SEEN, spans = listOf(0.0 to 3.0)), wall(1, z = 0.0, start = EndState.PARTIAL, startReason = EndReason.OCCLUDED, spans = listOf(1.0 to 4.0)))
        val stretches = listOf(UnobservedStretch(0, 3.0, 3.0, 4.0, 3.0, "prolungamento fino all'angolo", "x"), UnobservedStretch(1, 4.0, 3.0, 1.0, 0.0, "prolungamento dall'angolo", "y"))
        val p = plan(case(walls, stretches = stretches))
        assertTrue(p.requests.none { it.id == "W0-END" || it.id == "W1-START" }, "nessun duplicato sull'angolo")
        val r = p.req("CORNER-W0-W1")
        assertEquals(Severity.BLOCKING, r.severity)
        assertEquals(3.0, r.target.ax); assertEquals(3.0, r.target.az); assertEquals(1.0, r.target.bx); assertEquals(0.0, r.target.bz)
        assertEquals(1.0 + kotlin.math.sqrt(18.0), r.unobservedLengthM!!, 1e-9)
        assertTrue(r.contributingReasons.any { it.startsWith("W0:") } && r.contributingReasons.any { it.startsWith("W1:") })
    }

    @Test
    fun `determinismo end-to-end sul simulatore`() {
        fun once() = BenchPipeline.run(Scenarios.byId("S5")).let { r -> RescanDirector.plan(r.surfaces, r.walls, r.r4, QualityEngine.evaluate(r.map, r.surfaces, r.walls, r.r4)) }
        assertEquals(RescanReport.json(once()), RescanReport.json(once()))
    }
}
