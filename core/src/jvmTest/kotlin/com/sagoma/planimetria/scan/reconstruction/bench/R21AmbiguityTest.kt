package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.PerimeterReport
import com.sagoma.planimetria.scan.reconstruction.PerimeterSolver
import com.sagoma.planimetria.scan.reconstruction.Surface
import com.sagoma.planimetria.scan.reconstruction.SurfaceAmbiguity
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimator
import com.sagoma.planimetria.scan.reconstruction.WallReport
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * R2.1 — evidenza di superficie alternativa (ambiguityScore). Scene del simulatore del benchmark (verità nota). Verifica la formula
 * congelata (max di copertura · continuità su E1, E4, E5 sinistra, E5 destra), che E2/E3/NO_BACKFACE_INFORMATION abbiano contributo
 * zero, determinismo, stabilità, i casi indistinguibili G e che R2/R3/R4 non cambino.
 */
class R21AmbiguityTest {
    private val occ = OcclusionAuditTest()
    private fun caseOf(id: String) = occ.partC().first { it.sc.id == id }.sc

    /** Pannello davanti a GT1 a 40 cm, visto da 8 posizioni ad arco: la parete dietro il fronte si vede di sbieco (E1). */
    private val e1Scene = Scenario("E1T", "pannello a 40 cm, 8 viste", FurnitureAuditTest().panelRoom("E1T", 0.4), occ.arc(2.0, 2.6, 8), occ.noisy, "")

    /**
     * E2 senza E5: fronte basso isolato (nessuna parete entro 1,5 m dietro) e un pannello parallelo arretrato di 60 cm che parte
     * 60 cm SOPRA il bordo del fronte: R2 vede una parallela adiacente "sopra" (E2), ma le bande di E4 (fino a 50 cm sopra) ed E5
     * (ai lati, alle quote del fronte) non contengono nessuna superficie arretrata.
     */
    private val e2Scene = Scenario(
        "E2T", "E2 senza E5",
        GtRoom("E2T", Scenarios.rect(4.0, 5.0), panels = listOf(GtPanel(0, "fronte basso", 1.2, 2.0, 2.8, 2.0, 0.0, 1.0), GtPanel(1, "pannello alto arretrato", 1.2, 2.6, 2.8, 2.6, 1.6, 2.4))),
        occ.ring2, occ.noisy, "",
    )

    companion object { private val runs = HashMap<String, BenchRun>() }
    private fun run(sc: Scenario): BenchRun = runs.getOrPut(sc.id + sc.stations.size) { BenchPipeline.run(sc) }

    /** Superficie verticale con più punti dell'etichetta di verità [label] e normale lungo z. */
    private fun main(run: BenchRun, label: Int): Surface = run.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL && abs(it.plane.nz) > 0.9 }
        .maxByOrNull { occ.labels(run, it)[label] ?: 0 }!!.also { assertTrue((occ.labels(run, it)[label] ?: 0) > 0, "superficie della verità trovata") }

    private fun amb(run: BenchRun, sf: Surface): SurfaceAmbiguity = assertNotNull(run.surfaces.ambiguity[sf.id], "ambiguità calcolata per S${sf.id}")
    private fun formula(a: SurfaceAmbiguity) = max(max(a.behind.score, a.above.score), max(a.left.score, a.right.score))

    @Test
    fun `1 - score zero senza evidenza alternativa (parete libera)`() {
        val r = run(caseOf("O0")); val a = amb(r, main(r, Label.wall(1)))
        assertEquals(0.0, a.score)
        assertEquals(0.0, a.stability)
    }

    @Test
    fun `2 - score positivo con E1 (superficie osservata dietro il fronte)`() {
        val r = run(e1Scene); val a = amb(r, main(r, Label.panel(0)))
        assertTrue(a.behind.score > 0 && a.behind.distanceM!! >= 0.15, "E1 acceso: ${a.behind}")
        assertTrue(a.score >= a.behind.score && a.score > 0)
    }

    @Test
    fun `3 - E2 rilevato ed esposto con contributo zero`() {
        val r = run(caseOf("C2")); val a = amb(r, main(r, Label.wall(3)))
        assertNotNull(a.adjacent, "E2 rilevato sul fronte del pilastro C2")
        assertEquals(formula(a), a.score, "lo score è solo la formula congelata")
    }

    @Test
    fun `3b - E2 presente ed E5 assente danno score zero`() {
        val r = run(e2Scene); val a = amb(r, main(r, Label.panel(0)))
        assertEquals("sopra", a.adjacent?.where, "E2 (parallela arretrata sopra) rilevato: ${a.adjacent}")
        assertEquals(0.0, a.left.score); assertEquals(0.0, a.right.score); assertEquals(0.0, a.above.score); assertEquals(0.0, a.behind.score)
        assertEquals(0.0, a.score, "E2 non entra nello score, nemmeno indirettamente")
    }

    @Test
    fun `4 - E3 rilevato ed esposto con contributo zero`() {
        val r = run(caseOf("C1")); val a = amb(r, main(r, Label.wall(1)))
        assertTrue(a.perpendicular.sidesBehind >= 1, "E3 (fianco verso dietro) rilevato sulla parete accanto alla rientranza")
        assertEquals(formula(a), a.score)
    }

    @Test
    fun `5 - score positivo con E4 (parete sopra il fronte)`() {
        val r = run(caseOf("O11")); val a = amb(r, main(r, Label.box(0)))
        assertTrue(a.above.score > 0, "E4 acceso: ${a.above}")
        assertTrue(a.score >= a.above.score && a.score > 0)
    }

    @Test
    fun `6 - score positivo con E5 (parete ai lati del fronte)`() {
        val r = run(caseOf("O5")); val a = amb(r, main(r, Label.box(0)))
        assertTrue(max(a.left.score, a.right.score) > 0, "E5 acceso")
        assertTrue(a.score > 0)
    }

    @Test
    fun `7 - E6 da solo (nessuna informazione dietro) da score zero`() {
        val r = run(caseOf("O9")); val a = amb(r, main(r, Label.box(0)))
        assertTrue(a.noBackfaceInformation, "NO_BACKFACE_INFORMATION")
        assertEquals(0.0, a.score); assertEquals(0.0, a.stability)
    }

    @Test
    fun `8 e 9 - score e stabilita deterministici`() {
        val a = BenchPipeline.run(caseOf("O5")).surfaces.ambiguity
        val b = BenchPipeline.run(caseOf("O5")).surfaces.ambiguity
        assertEquals(a, b)
        for (x in a.values) assertTrue(x.stability <= x.score + 1e-12 && x.score in 0.0..1.0 && x.stability in 0.0..1.0)
    }

    @Test
    fun `9b - con una sola vista la stabilita e zero`() {
        val sc = caseOf("O5").let { it.copy(id = "O5v1", stations = occ.arc(2.0, 2.395, 1)) }
        val r = run(sc); val a = amb(r, main(r, Label.box(0)))
        assertEquals(1, a.totalViews)
        assertEquals(0.0, a.stability, "togliendo l'unica vista non resta evidenza")
    }

    @Test
    fun `10 - G0-G3 producono lo stesso risultato della parete equivalente`() {
        val g = occ.partG()
        val res = g.map { c ->
            val r = BenchPipeline.run(c.sc)
            val sf = r.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL && abs(it.plane.nz) > 0.9 && it.plane.centroid[2] > 2.0 }.maxByOrNull { it.areaM2 }!!
            Triple(sf.kind, sf.structuralScore, r.surfaces.ambiguity.getValue(sf.id))
        }
        assertEquals(SurfaceKind.VERTICAL_STRUCTURAL, res[0].first)
        for (x in res) assertEquals(res[0], x, "osservazioni identiche → stessa classe, stesso punteggio, stessa evidenza")
    }

    @Test
    fun `11 - la formula e solo E1 E4 E5 e le classi R2 non dipendono dall'ambiguita`() {
        for (id in listOf("O0", "O5", "O9", "O11", "C1", "C2")) {
            val r = run(caseOf(id))
            assertEquals(r.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL }.map { it.id }.toSet(), r.surfaces.ambiguity.keys)
            for (a in r.surfaces.ambiguity.values) assertEquals(formula(a), a.score, "$id S${a.surfaceId}")
        }
    }

    @Test
    fun `12 - R3 e R4 ricevono e producono esattamente gli stessi dati con o senza ambiguita`() {
        for (id in listOf("O5", "C2")) {
            val r = run(caseOf(id)); val s = r.surfaces
            val s0 = SurfaceResult(s.surfaces, s.floor, s.ceilingY, s.cameraMedianY, s.voxelsWithNormal, s.voxelsAssigned, s.pointsAssigned, s.pointsTotal, s.arcore, s.voxelSurface, s.noiseEstimateM, s.thresholds)
            assertTrue(s0.ambiguity.isEmpty() && s.ambiguity.isNotEmpty())
            val w = WallEstimator.estimate(WallEstimator.inputFrom(r.map, s, r.sim.recording)); val w0 = WallEstimator.estimate(WallEstimator.inputFrom(r.map, s0, r.sim.recording))
            assertEquals(WallReport.csv(w0) + WallReport.endsCsv(w0), WallReport.csv(w) + WallReport.endsCsv(w))
            val p = PerimeterSolver.solve(PerimeterSolver.inputFrom(r.map, s, w)); val p0 = PerimeterSolver.solve(PerimeterSolver.inputFrom(r.map, s0, w0))
            assertEquals(PerimeterReport.csv(p0) + PerimeterReport.json(p0), PerimeterReport.csv(p) + PerimeterReport.json(p))
        }
    }

    @Test
    fun `coerenza con l'audit della specifica (stessa cella e stesso raggio di vista)`() {
        for (id in listOf("O0", "O5", "O11", "C2")) {
            val r = run(caseOf(id))
            val views = OcclusionAudit.views(r.map, 0.5); val g = OcclusionAudit.grid(r.map, r.surfaces, views, 0.05)
            for (sf in r.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL }) {
                val b = OcclusionAudit.backface(r.map, r.surfaces, g, views, sf)
                val e1 = if ((b.behindHitMedianD ?: 0.0) >= 0.15) b.behindHitFrac * (b.behindContinuity ?: 0.0) else 0.0
                val audit = listOf(e1, b.bands.getValue("sopra"), b.bands.getValue("sinistra"), b.bands.getValue("destra"))
                    .map { if (it is Double) it else (it as OcclusionAudit.Band).let { x -> x.recessedFrac * (x.continuity ?: 0.0) } }.max()
                val a = r.surfaces.ambiguity.getValue(sf.id)
                assertEquals(audit, a.score, 1e-12, "$id S${sf.id}: produzione = audit")
                assertEquals(b.frontViews, a.frontViews)
            }
        }
    }
}
