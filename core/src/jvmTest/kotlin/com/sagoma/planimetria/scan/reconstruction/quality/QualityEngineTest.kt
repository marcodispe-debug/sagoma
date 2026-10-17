package com.sagoma.planimetria.scan.reconstruction.quality

import com.sagoma.planimetria.scan.reconstruction.AmbiguityChannel
import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.EvidenceQuality
import com.sagoma.planimetria.scan.reconstruction.GapReason
import com.sagoma.planimetria.scan.reconstruction.MeasurementQuality
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.PerpendicularEvidence
import com.sagoma.planimetria.scan.reconstruction.SurfaceAmbiguity
import com.sagoma.planimetria.scan.reconstruction.ThicknessState
import com.sagoma.planimetria.scan.reconstruction.WallEnd
import com.sagoma.planimetria.scan.reconstruction.WallEvidence
import com.sagoma.planimetria.scan.reconstruction.WallGap
import com.sagoma.planimetria.scan.reconstruction.WallGeometry
import com.sagoma.planimetria.scan.reconstruction.WallSpan
import com.sagoma.planimetria.scan.reconstruction.WallThickness
import com.sagoma.planimetria.scan.reconstruction.WallUncertainty
import com.sagoma.planimetria.scan.reconstruction.bench.BenchPipeline
import com.sagoma.planimetria.scan.reconstruction.bench.OcclusionAuditTest
import com.sagoma.planimetria.scan.reconstruction.bench.Scenarios
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * M4 — QualityEngine. Q1–Q9 e Q11: test UNITARI con ingressi R3/R4/R2.1 costruiti a mano (indipendenza degli assi verificata sul
 * motore). Q10, determinismo e "rumore → σ": end-to-end sul simulatore del benchmark, solo proprietà fisicamente vere.
 */
class QualityEngineTest {

    // ------------------------------------------------------------------------------------------------ ingressi costruiti

    private fun wall(
        id: Int = 0, sigmaPos: Double = 0.005, sigmaHead: Double = 0.3, spans: List<Pair<Double, Double>> = listOf(0.0 to 4.0),
        start: EndState = EndState.OBSERVED, end: EndState = EndState.OBSERVED, views: Int = 4, evidence: EvidenceQuality = EvidenceQuality.HIGH,
        sources: List<Int> = listOf(id),
    ): EstimatedWall {
        val s = spans.first().first; val e = spans.last().second
        val geom = WallGeometry(0.0, -1.0, -3.0, 2.0, 3.0, 1.0, 0.0, s, e, 0.0, 2.6, 0.0, 2.6, 0.0, spans.map { WallSpan(it.first, it.second) })
        val gaps = spans.zipWithNext().map { (a, b) -> WallGap(a.second, b.first, GapReason.NOT_SEEN) }
        val unc = WallUncertainty(sigmaPos, sigmaHead, 0.01, 0.01, 0.014, 0.0, 0.0, sigmaPos, 0.005)
        val ev = WallEvidence(5000, 4800, 40, views, 60.0, 2.0, 0.004, 0.003, 0.95, 0.02, evidence, if (evidence == EvidenceQuality.HIGH) emptyList() else listOf("evidenza ridotta"))
        val reason = { st: EndState -> if (st == EndState.OBSERVED) EndReason.CORNER else EndReason.OCCLUDED }
        return EstimatedWall(id, sources, geom, unc, ev, WallEnd(s, start, reason(start), 0.01), WallEnd(e, end, reason(end), 0.01), gaps,
            WallThickness(ThicknessState.UNKNOWN, note = ""), 0.9, emptyList(), emptyList())
    }

    private fun facts(state: PerimeterState? = PerimeterState.CLOSED, walls: List<Int> = emptyList(), unobserved: List<Triple<Int, String, Double>> = emptyList()) =
        PerimeterFacts(state, walls, walls.associateWith { "nel perimetro principale" }, unobserved, null, null, null, null, null)

    private fun amb(id: Int, score: Double, stability: Double): SurfaceAmbiguity {
        val ch = AmbiguityChannel(10, score, 1.0, 0.6, 2)
        val zero = AmbiguityChannel(10, 0.0, 0.0, null, 0)
        return SurfaceAmbiguity(id, score, zero, zero, ch, zero, null, PerpendicularEvidence(0, null, 0), 2, stability, 2, 2, 2, 1.0, false, MeasurementQuality.HIGH)
    }

    private fun eval(walls: List<EstimatedWall>, pf: PerimeterFacts = facts(walls = walls.map { it.id }), ambiguity: Map<Int, SurfaceAmbiguity> = walls.associate { it.id to amb(it.id, 0.0, 0.0) }) =
        QualityEngine.evaluate(walls, pf, ambiguity, walls.associate { it.id to 4.0 }, 12.0)

    // ------------------------------------------------------------------------------------------------------- Q1–Q9, Q11

    @Test
    fun `Q1 parete precisa e completamente osservata`() {
        val w = eval(listOf(wall())).walls.single()
        assertEquals(QualityLevel.HIGH, w.measurement.level)
        assertEquals(QualityLevel.HIGH, w.completeness.level)
        assertEquals(1.0, w.completeness.coverageRatio)
        assertTrue(w.defects.isEmpty())
    }

    @Test
    fun `Q2 parete precisa ma PARTIAL`() {
        val w = eval(listOf(wall(end = EndState.PARTIAL))).walls.single()
        assertEquals(QualityLevel.HIGH, w.measurement.level)
        assertTrue(w.completeness.level == QualityLevel.MEDIUM || w.completeness.level == QualityLevel.LOW)
        assertTrue(w.defects.any { it.code == DefectCode.PARTIAL_END })
    }

    @Test
    fun `Q3 parete rumorosa - la misura degrada, la completezza no`() {
        val clean = eval(listOf(wall())).walls.single()
        val noisy = eval(listOf(wall(sigmaPos = 0.05, sigmaHead = 2.5))).walls.single()
        assertEquals(QualityLevel.LOW, noisy.measurement.level)
        assertEquals(clean.completeness, noisy.completeness)
        assertTrue(noisy.defects.any { it.code == DefectCode.HIGH_POSITION_UNCERTAINTY } && noisy.defects.any { it.code == DefectCode.HIGH_DIRECTION_UNCERTAINTY })
    }

    @Test
    fun `Q4 poche viste - degrada l'evidenza, non la misura`() {
        val clean = eval(listOf(wall())).walls.single()
        val few = eval(listOf(wall(views = 1, evidence = EvidenceQuality.MEDIUM))).walls.single()
        assertEquals(QualityLevel.LOW, few.evidence.level)
        assertEquals(clean.measurement, few.measurement)
        assertEquals(clean.completeness, few.completeness)
        assertTrue(few.defects.any { it.code == DefectCode.LOW_VIEW_COUNT })
    }

    @Test
    fun `Q5 ambiguityScore alto - cresce solo l'ambiguita`() {
        val base = eval(listOf(wall())).walls.single()
        val w = eval(listOf(wall()), ambiguity = mapOf(0 to amb(0, 0.5, 0.45))).walls.single()
        assertEquals(QualityLevel.LOW, w.ambiguity.level)
        assertEquals(0.5, w.ambiguity.maxScore)
        assertEquals(base.measurement, w.measurement)
        assertEquals(base.completeness, w.completeness)
        assertEquals(base.evidence, w.evidence)
        assertTrue(w.defects.any { it.code == DefectCode.HIGH_AMBIGUITY } && w.defects.none { it.code == DefectCode.UNSTABLE_AMBIGUITY })
        val unstable = eval(listOf(wall()), ambiguity = mapOf(0 to amb(0, 0.5, 0.05))).walls.single()
        assertTrue(unstable.ambiguity.unstable && unstable.defects.any { it.code == DefectCode.UNSTABLE_AMBIGUITY })
    }

    @Test
    fun `Q6 ambiguityScore basso - ambiguita bassa`() {
        val w = eval(listOf(wall()), ambiguity = mapOf(0 to amb(0, 0.05, 0.05))).walls.single()
        assertEquals(QualityLevel.HIGH, w.ambiguity.level)
        assertTrue(w.defects.none { it.code == DefectCode.HIGH_AMBIGUITY })
    }

    @Test
    fun `Q6b una parete fonde piu superfici - massimo conservativo e media pesata`() {
        val w = QualityEngine.evaluate(listOf(wall(sources = listOf(1, 2))), facts(walls = listOf(0)), mapOf(1 to amb(1, 0.4, 0.3), 2 to amb(2, 0.0, 0.0)), mapOf(1 to 1.0, 2 to 3.0), null).walls.single()
        assertEquals(0.4, w.ambiguity.maxScore); assertEquals(1, w.ambiguity.sourceSurfaceId); assertEquals(0.3, w.ambiguity.sourceSurfaceStability)
        assertEquals(0.1, w.ambiguity.areaWeightedMean!!, 1e-12)
        assertEquals(QualityLevel.LOW, w.ambiguity.level, "il giudizio usa il massimo")
    }

    @Test
    fun `Q7 perimetro OPEN o PARTIAL - degrada la completezza della stanza`() {
        val walls = listOf(wall(0), wall(1), wall(2), wall(3))
        val closed = eval(walls).room
        assertEquals(QualityLevel.HIGH, closed.completeness); assertEquals(QualityLevel.HIGH, closed.closureQuality)
        for (st in listOf(PerimeterState.PARTIAL, PerimeterState.OPEN, PerimeterState.UNCERTAIN)) {
            val r = eval(walls, facts(st, walls.map { it.id })).room
            assertNotEquals(QualityLevel.HIGH, r.completeness, "$st")
            assertEquals(QualityLevel.LOW, r.closureQuality)
            assertEquals(QualityLevel.LOW, r.overallQuality)
            assertTrue(r.defects.isNotEmpty())
        }
        val none = eval(walls, facts(null, emptyList())).room
        assertTrue(none.defects.any { it.code == DefectCode.OPEN_PERIMETER })
    }

    @Test
    fun `Q8 grande gap non osservato - osservato e non osservato separati, il dedotto resta non osservato`() {
        val w0 = wall(spans = listOf(0.0 to 1.5, 2.5 to 4.0))
        val w = eval(listOf(w0), facts(walls = listOf(0), unobserved = listOf(Triple(0, "prolungamento fino all'angolo", 1.2)))).walls.single()
        assertEquals(3.0, w.completeness.observedLengthM, 1e-12)
        assertEquals(1.0, w.completeness.unobservedR3M, 1e-12)
        assertEquals(1.2, w.completeness.unobservedR4M, 1e-12)
        assertEquals(2.2, w.completeness.unobservedLengthM, 1e-12)
        assertEquals(3.0 / 5.2, w.completeness.coverageRatio, 1e-12)
        assertEquals(QualityLevel.LOW, w.completeness.level)
        assertTrue(w.defects.any { it.code == DefectCode.UNOBSERVED_SEGMENT })
    }

    @Test
    fun `Q8b oltre un'estremita non osservata l'estensione e ignota, non zero`() {
        val open = eval(listOf(wall(end = EndState.UNCERTAIN)), facts(PerimeterState.PARTIAL, listOf(0))).walls.single()
        assertTrue(open.completeness.extentUnknown, "non osservato = limite inferiore, copertura = limite superiore")
        assertEquals(0.0, open.completeness.unobservedLengthM)
        assertTrue(eval(listOf(wall(end = EndState.UNCERTAIN)), facts(PerimeterState.PARTIAL, listOf(0))).room.coverageIsUpperBound)
        val deduced = eval(listOf(wall(end = EndState.PARTIAL)), facts(walls = listOf(0), unobserved = listOf(Triple(0, "prolungamento fino all'angolo", 0.5)))).walls.single()
        assertTrue(!deduced.completeness.extentUnknown, "R4 ha dedotto il tratto: è quantificato (e resta NON osservato)")
        assertEquals(0.5, deduced.completeness.unobservedR4M)
        val closed = eval(listOf(wall())).walls.single()
        assertTrue(!closed.completeness.extentUnknown)
        assertTrue(!eval(listOf(wall(0), wall(1))).room.coverageIsUpperBound)
    }

    @Test
    fun `Q9 due pareti eccellenti e una pessima - la pessima non sparisce nella media`() {
        val bad = wall(2, sigmaPos = 0.08, sigmaHead = 4.0, spans = listOf(0.0 to 0.8, 3.5 to 4.0), start = EndState.UNCERTAIN, end = EndState.PARTIAL, views = 1, evidence = EvidenceQuality.LOW)
        val q = eval(listOf(wall(0), wall(1), bad), ambiguity = mapOf(0 to amb(0, 0.0, 0.0), 1 to amb(1, 0.0, 0.0), 2 to amb(2, 0.6, 0.5)))
        val r = q.room
        assertEquals(QualityLevel.LOW, r.overallQuality)
        assertEquals(QualityLevel.LOW, r.geometryQuality); assertEquals(QualityLevel.LOW, r.semanticQuality); assertEquals(QualityLevel.LOW, r.evidenceQuality)
        assertEquals(2, r.positionSigma.worstWalls.first()); assertEquals(2, r.coverage.worstWalls.first()); assertEquals(2, r.ambiguity.worstWalls.first())
        assertEquals(2, r.positionSigma.distribution[QualityLevel.HIGH]); assertEquals(1, r.positionSigma.distribution[QualityLevel.LOW])
        assertEquals(0.08, r.positionSigma.max)
        assertEquals(QualityLevel.LOW, q.walls.first { it.wallId == 2 }.overall)
        assertTrue(q.walls.filter { it.wallId != 2 }.all { it.overall == QualityLevel.HIGH })
    }

    @Test
    fun `Q11 determinismo con ingressi costruiti`() {
        val walls = listOf(wall(0), wall(1, end = EndState.PARTIAL), wall(2, sigmaPos = 0.04))
        val a = eval(walls); val b = eval(walls)
        assertEquals(QualityReport.json(a), QualityReport.json(b))
        assertEquals(QualityReport.wallCsv(a) + QualityReport.roomCsv(a) + QualityReport.defectsCsv(a), QualityReport.wallCsv(b) + QualityReport.roomCsv(b) + QualityReport.defectsCsv(b))
    }

    // --------------------------------------------------------------------------------------------- end-to-end (simulatore)

    /** Parole semantiche (senza maiuscole) e codici di decisione di M5 (esatti). "nessuna decisione di rescansione" è ammesso. */
    private val forbiddenWords = listOf("mobile", "furniture", "armadio")
    private val forbiddenCodes = listOf("RESCAN_REQUIRED", "INFORMATION_LIMITATION", "objectProbability", "furnitureProbability")

    @Test
    fun `Q10 G0-G3 - nessuna falsa semantica e stesso risultato per osservazioni identiche`() {
        val out = OcclusionAuditTest().partG().map { c ->
            val r = BenchPipeline.run(c.sc)
            val q = QualityEngine.evaluate(r.map, r.surfaces, r.walls, r.r4)
            val text = QualityReport.json(q) + QualityReport.summary(q, "G") + QualityReport.wallCsv(q) + QualityReport.roomCsv(q) + QualityReport.defectsCsv(q)
            for (w in forbiddenWords) assertTrue(!text.contains(w, ignoreCase = true), "parola vietata nel report: $w")
            for (w in forbiddenCodes) assertTrue(!text.contains(w), "codice vietato nel report: $w")
            QualityReport.json(q)
        }
        for (j in out) assertEquals(out[0], j, "osservazioni identiche → stessa qualità")
    }

    @Test
    fun `determinismo end-to-end e segmenti dedotti da R4 non contati come osservati`() {
        val a = BenchPipeline.run(Scenarios.byId("S5")); val b = BenchPipeline.run(Scenarios.byId("S5"))
        val qa = QualityEngine.evaluate(a.map, a.surfaces, a.walls, a.r4); val qb = QualityEngine.evaluate(b.map, b.surfaces, b.walls, b.r4)
        assertEquals(QualityReport.json(qa), QualityReport.json(qb))
        for (w in qa.walls) {
            val r3 = a.walls.walls.first { it.id == w.wallId }
            assertEquals(r3.geometry.observedLengthM, w.completeness.observedLengthM, "osservato = solo gli span osservati di R3")
            val deduced = a.r4.perimeters.flatMap { it.unobserved }.filter { it.wallId == w.wallId && it.kind != "gap interno R3" }.sumOf { it.lengthM }
            assertEquals(deduced, w.completeness.unobservedR4M, 1e-12)
        }
    }

    @Test
    fun `piu rumore depth fa crescere la sigma di posizione media (proprieta fisica)`() {
        fun meanSigma(id: String) = BenchPipeline.run(Scenarios.byId(id)).let { r -> QualityEngine.evaluate(r.map, r.surfaces, r.walls, r.r4) }.room.positionSigma.mean!!
        val clean = meanSigma("S0"); val noisy = meanSigma("S1"); val noisier = meanSigma("S2")
        assertTrue(clean < noisy && noisy < noisier, "σ media: S0 ${clean}, S1 ${noisy}, S2 ${noisier}")
    }
}
