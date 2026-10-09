package com.sagoma.planimetria.scan.reconstruction

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R4 su pareti R3 costruite a mano (stessa forma dell'uscita di R3, nessuna modifica a R3): ogni test controlla un comportamento
 * richiesto. Convenzione R3: normale verso le camere, interno a destra percorrendo ogni parete da start a end.
 */
class PerimeterSolverTest {
    private val o = EndState.OBSERVED; private val pa = EndState.PARTIAL; private val un = EndState.UNCERTAIN
    private val corner = EndReason.CORNER

    /** Parete R3 dal punto a al punto b (start → end), con stati, σ, qualità e confidenza dati. */
    private fun wall(
        id: Int, ax: Double, az: Double, bx: Double, bz: Double,
        start: EndState = o, startReason: EndReason = corner, end: EndState = o, endReason: EndReason = corner,
        sPos: Double = 0.005, sThDeg: Double = 0.3, sEnd: Double = 0.02, sStart: Double = sEnd,
        quality: EvidenceQuality = EvidenceQuality.HIGH, recognition: Double = 0.8,
        startEv: EndEvidence? = null, endEv: EndEvidence? = null,
    ): EstimatedWall {
        val l = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
        val ux = (bx - ax) / l; val uz = (bz - az) / l
        val nx = uz; val nz = -ux // R3: u = (−nz, nx)
        val cx = (ax + bx) / 2; val cz = (az + bz) / 2
        val g = WallGeometry(nx, nz, nx * cx + nz * cz, cx, cz, ux, uz, -l / 2, l / 2, 0.0, 2.4, 0.0, 2.4, Math.toDegrees(atan2(uz, ux)), listOf(WallSpan(-l / 2, l / 2)))
        return EstimatedWall(
            id, listOf(id), g, WallUncertainty(sPos, sThDeg, sStart, sEnd, sqrt(sStart * sStart + sEnd * sEnd), 0.0, 0.0, sPos, 0.02),
            WallEvidence(1000, 900, 10, 10, 60.0, 2.0, 0.01, 0.005, 1.0, 0.0, quality, emptyList()),
            WallEnd(-l / 2, start, startReason, sStart, startEv), WallEnd(l / 2, end, endReason, sEnd, endEv), emptyList(),
            WallThickness(ThicknessState.UNKNOWN, note = ""), recognition, emptyList(), emptyList(),
        )
    }

    private val unknown = SpaceQuery { _, _, _ -> 0 }
    private val roomCams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 1.5), doubleArrayOf(3.0, 2.0), doubleArrayOf(1.5, 2.2), doubleArrayOf(2.8, 0.8), doubleArrayOf(0.6, 2.6), doubleArrayOf(3.5, 0.5))

    private fun solve(walls: List<EstimatedWall>, space: SpaceQuery = unknown, cams: List<DoubleArray> = roomCams, r2: List<R2Trace> = emptyList()) =
        PerimeterSolver.solve(PerimeterInput(walls, space, 0.10, cams, r2))

    /** Stanza 4 × 3: (0,0)→(0,3)→(4,3)→(4,0), percorsa con l'interno a destra. */
    private fun square(sPos: Double = 0.005, sThDeg: Double = 0.3, shrink: Double = 0.0) = listOf(
        wall(1, 0.0, shrink, 0.0, 3 - shrink, sPos = sPos, sThDeg = sThDeg), wall(2, shrink, 3.0, 4 - shrink, 3.0, sPos = sPos, sThDeg = sThDeg),
        wall(3, 4.0, 3 - shrink, 4.0, shrink, sPos = sPos, sThDeg = sThDeg), wall(4, 4 - shrink, 0.0, shrink, 0.0, sPos = sPos, sThDeg = sThDeg),
    )

    private fun near(c: PerimeterCorner, x: Double, z: Double, tol: Double) = abs(c.x - x) <= tol && abs(c.z - z) <= tol
    private fun link(r: PerimeterResult, from: Int, to: Int) = r.candidates.first { it.fromWall == from && it.toWall == to }

    // 1
    @Test
    fun `1 - poligono chiuso esatto`() {
        val r = solve(square())
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        val m = assertNotNull(r.main)
        assertEquals(listOf(1, 2, 3, 4), m.wallIds)
        assertEquals(4, m.corners.size)
        for ((x, z) in listOf(0.0 to 3.0, 4.0 to 3.0, 4.0 to 0.0, 0.0 to 0.0)) assertTrue(m.corners.any { near(it, x, z, 1e-9) })
        for (c in m.corners) assertEquals(90.0, c.interiorAngleDeg, 1e-9)
        val k = assertNotNull(m.closure)
        assertEquals(0.0, k.errorM, 1e-9); assertEquals(1.0, k.pValue, 1e-9); assertTrue(k.simplePolygon); assertEquals(-360.0, k.turnSumDeg, 1e-9)
        assertEquals(14.0, m.perimeterLengthM!!, 1e-9); assertEquals(12.0, m.areaM2!!, 1e-9)
        assertTrue(m.unobserved.isEmpty()); assertTrue(r.hypotheses.isEmpty())
        assertTrue(r.walls.all { it.role == WallRole.PERIMETER })
    }

    // 2
    @Test
    fun `2 - poligono chiuso rumoroso`() {
        val walls = listOf(
            wall(1, 0.004, 0.012, -0.003, 2.985), wall(2, 0.010, 3.004, 3.988, 2.996),
            wall(3, 4.003, 3.015, 3.996, -0.010), wall(4, 3.990, 0.003, -0.012, -0.004),
        )
        val r = solve(walls)
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        val m = r.main!!
        for ((x, z) in listOf(0.0 to 3.0, 4.0 to 3.0, 4.0 to 0.0, 0.0 to 0.0)) assertTrue(m.corners.any { near(it, x, z, 3 * it.positionSigmaM) }, "angolo vicino a ($x, $z)")
        for (c in m.corners) assertEquals(90.0, c.interiorAngleDeg, 3 * c.angleSigmaDeg)
        assertTrue(m.closure!!.errorM > 0); assertTrue(m.closure!!.compatible)
    }

    // 3
    @Test
    fun `3 - poligono non ortogonale - angoli misurati, non imposti`() {
        val pts = listOf(0.0 to 0.0, 0.0 to 3.0, 2.0 to 4.0, 4.5 to 2.5, 3.5 to 0.0)
        val walls = pts.indices.map { i -> val a = pts[i]; val b = pts[(i + 1) % pts.size]; wall(i + 1, a.first, a.second, b.first, b.second) }
        val r = solve(walls, cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 2.0), doubleArrayOf(3.0, 1.5), doubleArrayOf(1.5, 3.0), doubleArrayOf(3.5, 2.0)))
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        val m = r.main!!
        assertEquals(5, m.corners.size)
        assertEquals(540.0, m.corners.sumOf { it.interiorAngleDeg }, 1e-6)
        // Angolo vero in (2, 4) tra (0,3)→(2,4) e (2,4)→(4.5,2.5).
        val c = m.corners.first { near(it, 2.0, 4.0, 1e-6) }
        val a1 = atan2(1.0, 2.0); val a2 = atan2(-1.5, 2.5)
        assertEquals(180 + Math.toDegrees(a2 - a1), c.interiorAngleDeg, 1e-6)
        assertTrue(m.corners.count { abs(it.interiorAngleDeg - 90) > 5 } >= 3, "nessuna ortogonalità imposta")
    }

    // 4
    @Test
    fun `4 - frammenti della stessa parete con gap nascosto da un oggetto`() {
        val s = square()
        val walls = listOf(
            s[0], wall(21, 0.0, 3.0, 1.5, 3.0, end = pa, endReason = EndReason.OCCLUDED), wall(22, 1.9, 3.0, 4.0, 3.0, start = pa, startReason = EndReason.OCCLUDED), s[2], s[3],
        )
        val space = SpaceQuery { x, _, z -> if (x in 1.5..1.9 && z in 2.5..2.85) 2 else 0 }
        val r = solve(walls, space)
        val l = link(r, 21, 22)
        assertEquals(LinkKind.SAME_WALL, l.kind); assertEquals(LinkDecision.ACCEPTED, l.decision)
        assertEquals(GapReason.OCCLUDED, l.gapReason); assertEquals(0.4, l.unobservedM, 1e-9)
        val m = r.main!!
        assertEquals(listOf(1, 21, 22, 3, 4), m.wallIds)
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        assertEquals(14.0 - 0.4, m.observedLengthM, 1e-9) // il gap non è misura
        assertTrue(m.unobserved.any { it.kind.startsWith("gap tra frammenti") && abs(it.lengthM - 0.4) < 1e-9 })
        // Il collegamento "saltando" il frammento W21 è rifiutato.
        assertEquals(LinkDecision.REJECTED, link(r, 1, 22).decision)
    }

    // 5
    @Test
    fun `5 - frammenti con gap non osservato - restano separati e niente CLOSED`() {
        val s = square()
        val walls = listOf(
            s[0], wall(21, 0.0, 3.0, 1.4, 3.0, end = pa, endReason = EndReason.NOT_SEEN), wall(22, 2.2, 3.0, 4.0, 3.0, start = pa, startReason = EndReason.NOT_SEEN), s[2], s[3],
        )
        val r = solve(walls)
        val l = link(r, 21, 22)
        assertEquals(LinkDecision.ACCEPTED, l.decision); assertEquals(GapReason.NOT_SEEN, l.gapReason); assertEquals(LinkSupport.INFERRED, l.support)
        val m = r.main!!
        assertEquals(PerimeterState.UNCERTAIN, m.state)
        assertTrue(m.reasons.any { "NON osservato" in it }, m.reasons.toString())
        assertEquals(14.0 - 0.8, m.observedLengthM, 1e-9)
    }

    // 6
    @Test
    fun `6 - parete mancante - catena aperta, nessuna parete creata`() {
        val walls = listOf(
            wall(1, 0.0, 0.3, 0.0, 3.0, start = pa, startReason = EndReason.NOT_SEEN), wall(2, 0.0, 3.0, 4.0, 3.0),
            wall(3, 4.0, 3.0, 4.0, 0.3, end = pa, endReason = EndReason.NOT_SEEN),
        )
        val r = solve(walls)
        assertEquals(PerimeterState.PARTIAL, r.state, PerimeterReport.text(r))
        val m = r.main!!
        assertEquals(listOf(1, 2, 3), m.wallIds)
        val ms = assertNotNull(m.missing)
        assertEquals(3, ms.afterWall); assertEquals(1, ms.beforeWall)
        assertEquals(EndState.PARTIAL, ms.fromState); assertEquals(EndState.PARTIAL, ms.toState)
        assertNull(m.closure)
        val h = r.hypotheses.single()
        assertEquals(HypothesisKind.MISSING_SIDE, h.kind); assertEquals("INFERRED / NOT OBSERVED", h.status)
        assertEquals(4.0, h.lengthM, 1e-9)
        assertEquals(3, r.walls.size) // nessuna parete inventata
        assertEquals(LinkDecision.REJECTED, link(r, 3, 1).decision)
    }

    // 7
    @Test
    fun `7 - estremità PARTIAL - angolo dedotto, prolungamento non osservato, niente CLOSED`() {
        val s = square()
        val walls = listOf(s[0], wall(2, 0.0, 3.0, 3.7, 3.0, end = pa, endReason = EndReason.OCCLUDED), wall(3, 4.0, 2.7, 4.0, 0.0, start = pa, startReason = EndReason.OCCLUDED), s[3])
        val r = solve(walls)
        val l = link(r, 2, 3)
        assertEquals(LinkDecision.ACCEPTED, l.decision); assertEquals(LinkSupport.INFERRED, l.support)
        assertEquals(4.0, l.cornerX!!, 1e-9); assertEquals(3.0, l.cornerZ!!, 1e-9); assertEquals(0.6, l.unobservedM, 1e-9)
        val m = r.main!!
        assertEquals(PerimeterState.UNCERTAIN, m.state)
        assertEquals(2, m.unobserved.count { it.kind.startsWith("prolungamento") })
        assertTrue(m.reasons.any { "dedotto" in it })
        assertEquals(13.4, m.observedLengthM, 1e-9)
    }

    // 8
    @Test
    fun `8 - estremità UNCERTAIN - collegamento possibile ma niente CLOSED`() {
        val s = square()
        val walls = listOf(s[0], wall(2, 0.0, 3.0, 4.05, 3.0, end = un, endReason = EndReason.UNSTABLE, sEnd = 0.10, sStart = 0.02), s[2], s[3])
        val r = solve(walls)
        val l = link(r, 2, 3)
        assertEquals(LinkDecision.ACCEPTED, l.decision); assertEquals(LinkSupport.OBSERVED, l.support)
        assertEquals(0.7, l.components.endpointState, 1e-9)
        assertEquals(PerimeterState.UNCERTAIN, r.state)
        assertTrue(r.main!!.reasons.any { "instabile" in it })
        assertEquals(1, r.main!!.uncertainty.endsUncertain)
    }

    // 9
    @Test
    fun `9 - parete vista oltre una porta - fuori dal perimetro`() {
        val s = square()
        val walls = listOf(
            s[0], s[1], s[2],
            wall(41, 4.0, 0.0, 2.4, 0.0, end = o, endReason = EndReason.FREE_BEYOND), wall(42, 1.6, 0.0, 0.0, 0.0, start = o, startReason = EndReason.FREE_BEYOND),
            wall(5, 3.0, -1.5, 1.0, -1.5, start = pa, startReason = EndReason.NOT_SEEN, end = pa, endReason = EndReason.NOT_SEEN),
        )
        val door = SpaceQuery { x, _, z -> if (z < 0 && z > -1.5 && x in 1.6..2.4) 1 else 0 }
        val r = solve(walls, door)
        val beyond = r.walls.first { it.wallId == 5 }
        assertEquals(WallRole.BEYOND_OPENING, beyond.role, beyond.reasons.toString())
        assertTrue(beyond.reasons.any { "NON osservato" in it })
        val m = r.main!!
        assertFalse(5 in m.wallIds)
        assertEquals(listOf(1, 2, 3, 41, 42), m.wallIds)
        assertEquals(GapReason.SEEN_THROUGH, link(r, 41, 42).gapReason)
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
    }

    // 10
    @Test
    fun `10 - parete estranea parallela - non entra e non disturba`() {
        val walls = square() + wall(5, 1.0, 2.5, 3.0, 2.5, start = o, startReason = EndReason.FREE_BEYOND, end = o, endReason = EndReason.FREE_BEYOND)
        val r = solve(walls)
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        assertEquals(listOf(1, 2, 3, 4), r.main!!.wallIds)
        assertEquals(WallRole.UNLINKED, r.walls.first { it.wallId == 5 }.role)
        assertTrue(r.candidates.filter { it.fromWall == 5 || it.toWall == 5 }.all { it.decision == LinkDecision.REJECTED })
    }

    // 11
    @Test
    fun `11 - falso collegamento per prossimità`() {
        // Estremità a 12 cm, ma l'intersezione delle linee è 1,16 m dentro la parete osservata A.
        val a = wall(1, 0.0, 0.0, 0.0, 3.0, end = pa, endReason = EndReason.NOT_SEEN)
        val b = wall(2, 0.12, 3.02, 0.42, 5.97, start = pa, startReason = EndReason.NOT_SEEN)
        val r = solve(listOf(a, b), cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.5, 4.0)))
        val l = link(r, 1, 2)
        assertEquals(LinkDecision.REJECTED, l.decision)
        assertTrue(l.reasons.any { "prosegue" in it }, l.reasons.toString())
        assertEquals(PerimeterState.OPEN, r.state)
        assertNull(r.main)
    }

    // 12
    @Test
    fun `12 - collegamento incompatibile per angolo`() {
        val s4 = Math.sin(Math.toRadians(4.0)); val c4 = Math.cos(Math.toRadians(4.0))
        val a = wall(1, 0.0, 0.0, 0.0, 3.0)
        // B parte a 10 cm dall'estremità di A ma ruotata di 4°: l'angolo cadrebbe a 1,43 m dalle estremità osservate (oltre 3σ anche
        // con la grande incertezza dell'intersezione di linee quasi parallele).
        val b = wall(2, -0.10, 3.0, -0.10 + 2 * s4, 3.0 + 2 * c4)
        // C torna indietro quasi parallela: linee antiparallele.
        val c = wall(3, 0.05, 3.0, 0.07, 0.0)
        val r = solve(listOf(a, b, c), cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(-1.0, 1.0)))
        val ab = link(r, 1, 2); val ac = link(r, 1, 3)
        assertEquals(LinkKind.CORNER, ab.kind); assertEquals(LinkDecision.REJECTED, ab.decision)
        assertTrue(ab.reasons.any { "OSSERVATA" in it }, ab.reasons.toString())
        assertEquals(LinkDecision.REJECTED, ac.decision)
        assertTrue(ac.reasons.any { "antiparallele" in it }, ac.reasons.toString())
        assertTrue(r.candidates.none { it.decision == LinkDecision.ACCEPTED })
    }

    // 13
    @Test
    fun `13 - propagazione delle incertezze`() {
        val sPos = 0.01; val tiny = 1e-6
        val right = solve(listOf(wall(1, 0.0, 0.0, 0.0, 3.0, sPos = sPos, sThDeg = tiny), wall(2, 0.0, 3.0, 4.0, 3.0, sPos = sPos, sThDeg = tiny)))
        assertEquals(sqrt(2.0) * sPos, link(right, 1, 2).cornerSigmaM!!, 1e-6)
        // 60° interni: σ dell'angolo × 1/sin(120°).
        val sixty = solve(listOf(wall(1, 0.0, 0.0, 0.0, 3.0, sPos = sPos, sThDeg = tiny), wall(2, 0.0, 3.0, 0.866025403784 * 3, 1.5, sPos = sPos, sThDeg = tiny)))
        val l60 = link(sixty, 1, 2)
        assertEquals(60.0, 180 + l60.turnDeg, 1e-6)
        assertEquals(sqrt(2.0) * sPos / Math.sin(Math.toRadians(120.0)), l60.cornerSigmaM!!, 1e-6)
        // Raddoppiare la σ di posizione raddoppia la σ dell'angolo; la direzione aggiunge la sua parte.
        val double = solve(listOf(wall(1, 0.0, 0.0, 0.0, 3.0, sPos = 2 * sPos, sThDeg = tiny), wall(2, 0.0, 3.0, 4.0, 3.0, sPos = 2 * sPos, sThDeg = tiny)))
        assertEquals(2 * link(right, 1, 2).cornerSigmaM!!, link(double, 1, 2).cornerSigmaM!!, 1e-6)
        val withHeading = solve(listOf(wall(1, 0.0, 0.0, 0.0, 3.0, sPos = sPos, sThDeg = 1.0), wall(2, 0.0, 3.0, 4.0, 3.0, sPos = sPos, sThDeg = 1.0)))
        assertTrue(link(withHeading, 1, 2).cornerSigmaM!! > link(right, 1, 2).cornerSigmaM!!)
        assertEquals(sqrt(2.0), link(withHeading, 1, 2).turnSigmaDeg, 1e-9)
        // Anello: σ del perimetro e dell'area propagate dagli angoli.
        val m = solve(square(sPos = sPos, sThDeg = tiny)).main!!
        assertEquals(sPos * sqrt(8.0), m.uncertainty.perimeterSigmaM!!, 1e-6)
        assertEquals(sPos * sqrt(50.0), m.uncertainty.areaSigmaM2!!, 1e-6)
        assertEquals(sqrt(8 * 0.02 * 0.02 + 8 * sPos * sPos), m.closure!!.propagatedSigmaM, 1e-6)
    }

    // 14
    @Test
    fun `14 - errore di chiusura compatibile con le sigma`() {
        val r = solve(square(shrink = 0.025))
        val k = r.main!!.closure!!
        assertTrue(k.errorM > 0.05, "errore ${k.errorM}")
        assertTrue(k.compatible, "p = ${k.pValue}")
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
    }

    // 15
    @Test
    fun `15 - errore di chiusura NON compatibile con le sigma`() {
        val r = solve(square(shrink = 0.05))
        // Ogni collegamento singolo regge (entro 3σ)…
        assertTrue(r.candidates.filter { it.kind == LinkKind.CORNER && it.fromWall % 4 + 1 == it.toWall }.all { it.decision == LinkDecision.ACCEPTED && abs(it.zFrom) < 3 && abs(it.zTo) < 3 })
        // …ma l'insieme degli scarti non è compatibile con le incertezze propagate.
        val k = r.main!!.closure!!
        assertFalse(k.compatible, "p = ${k.pValue}")
        assertEquals(PerimeterState.UNCERTAIN, r.state)
        assertTrue(r.main!!.reasons.any { "NON compatibile" in it })
    }

    // 16
    @Test
    fun `16 - soluzione ambigua tra due perimetri`() {
        val walls = listOf(
            wall(1, 0.0, 0.0, 0.0, 3.0), wall(2, 0.0, 3.0, 3.0, 3.0, end = pa, endReason = EndReason.OCCLUDED),
            wall(5, 4.0, 2.0, 4.0, 0.0, start = pa, startReason = EndReason.OCCLUDED),
            wall(6, 3.6, 2.0, 3.6, 1.0, start = pa, startReason = EndReason.OCCLUDED, end = pa, endReason = EndReason.OCCLUDED),
            wall(4, 4.0, 0.0, 0.0, 0.0),
        )
        val cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 1.5), doubleArrayOf(1.5, 2.2), doubleArrayOf(3.0, 0.5), doubleArrayOf(3.8, 1.5))
        val r = solve(walls, cams = cams)
        assertEquals(LinkDecision.AMBIGUOUS, link(r, 2, 5).decision)
        assertEquals(LinkDecision.AMBIGUOUS, link(r, 2, 6).decision)
        assertEquals(PerimeterState.UNCERTAIN, r.state, PerimeterReport.text(r))
        assertTrue(r.main!!.alternatives.size >= 2)
        assertNotEquals(PerimeterState.CLOSED, r.state)
    }

    // 17
    @Test
    fun `17 - evidenze insufficienti - nessuna chiusura`() {
        val walls = listOf(
            wall(1, 0.0, 0.0, 0.0, 1.0, start = pa, startReason = EndReason.NOT_SEEN, end = pa, endReason = EndReason.NOT_SEEN, quality = EvidenceQuality.LOW, recognition = 0.4),
            wall(2, 1.0, 3.0, 2.0, 3.0, start = pa, startReason = EndReason.NOT_SEEN, end = pa, endReason = EndReason.NOT_SEEN, quality = EvidenceQuality.LOW, recognition = 0.4),
        )
        val r = solve(walls, cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.5, 2.0)))
        assertNotEquals(PerimeterState.CLOSED, r.state)
        assertTrue(r.perimeters.none { it.state == PerimeterState.CLOSED })
        r.main?.let { m ->
            assertEquals(PerimeterState.PARTIAL, m.state)
            assertTrue(m.corners.all { it.support == LinkSupport.INFERRED })
            assertEquals(3.0, m.unobserved.sumOf { it.lengthM }, 1e-9)
            assertEquals(EvidenceQuality.LOW, m.uncertainty.worstEvidence); assertEquals(0.4, m.uncertainty.minRecognition, 1e-9)
        }
    }

    /** Stanza a L con due pareti in due frammenti, una porta e una parete oltre la porta. */
    private fun lRoom(): Pair<List<EstimatedWall>, SpaceQuery> {
        val walls = listOf(
            wall(11, 0.0, 0.0, 0.0, 1.6, end = pa, endReason = EndReason.OCCLUDED), wall(12, 0.0, 2.0, 0.0, 4.0, start = pa, startReason = EndReason.OCCLUDED),
            wall(2, 0.0, 4.0, 2.0, 4.0), wall(3, 2.0, 4.0, 2.0, 2.0), wall(4, 2.0, 2.0, 4.0, 2.0), wall(5, 4.0, 2.0, 4.0, 0.0),
            wall(61, 4.0, 0.0, 2.6, 0.0, end = o, endReason = EndReason.FREE_BEYOND), wall(62, 2.2, 0.0, 0.0, 0.0, start = o, startReason = EndReason.FREE_BEYOND),
            wall(7, 3.0, -1.5, 1.8, -1.5, start = pa, startReason = EndReason.NOT_SEEN, end = pa, endReason = EndReason.NOT_SEEN),
        )
        val space = SpaceQuery { x, _, z ->
            when {
                x in 0.2..0.6 && z in 1.6..2.0 -> 2 // mobile davanti al gap della parete sinistra
                z < 0 && z > -1.5 && x in 2.2..2.6 -> 1 // porta
                else -> 0
            }
        }
        return walls to space
    }
    private val lCams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.0, 3.0), doubleArrayOf(3.0, 1.0), doubleArrayOf(1.5, 0.8), doubleArrayOf(0.8, 2.5), doubleArrayOf(3.2, 1.4))

    // 18
    @Test
    fun `18 - più frammenti e più pareti, angolo rientrante`() {
        val (walls, space) = lRoom()
        val r = solve(walls, space, lCams)
        val m = r.main!!
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        assertEquals(listOf(2, 3, 4, 5, 61, 62, 11, 12), m.wallIds) // anello: parte dalla parete con id minore
        assertEquals(6, m.corners.size)
        assertEquals(1, m.corners.count { abs(it.interiorAngleDeg - 270) < 1e-6 })
        assertEquals(5, m.corners.count { abs(it.interiorAngleDeg - 90) < 1e-6 })
        assertEquals(GapReason.OCCLUDED, link(r, 11, 12).gapReason)
        assertEquals(GapReason.SEEN_THROUGH, link(r, 61, 62).gapReason)
        assertEquals(WallRole.BEYOND_OPENING, r.walls.first { it.wallId == 7 }.role)
        assertEquals(12.0, m.areaM2!!, 1e-9)
    }

    // 19
    @Test
    fun `19 - deterministico e indipendente dall'ordine di ingresso`() {
        val (walls, space) = lRoom()
        val a = solve(walls, space, lCams)
        val b = solve(walls.shuffled(Random(7)), space, lCams)
        val c = solve(walls.reversed(), space, lCams)
        for (x in listOf(b, c)) {
            assertEquals(PerimeterReport.text(a), PerimeterReport.text(x))
            assertEquals(PerimeterReport.csv(a), PerimeterReport.csv(x))
            assertEquals(PerimeterReport.json(a), PerimeterReport.json(x))
        }
    }

    // 20
    @Test
    fun `20 - catena completa R1-R2-R3-R4 sulla stanza sintetica`() {
        val room = SyntheticRoom(noiseM = 0.01)
        val (rec, blobs) = room.recording()
        val map = GlobalMap.build(rec, { blobs[it] })
        val s = SurfaceExtractor.extract(map, rec)
        val walls = WallEstimator.estimate(WallEstimator.inputFrom(map, s, rec))
        val r3Before = WallReport.csv(walls)
        val r = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, walls))
        assertEquals(r3Before, WallReport.csv(walls), "R4 non modifica l'uscita di R3")
        val ids = walls.walls.map { it.id }.toSet()
        for (p in r.perimeters) assertTrue(ids.containsAll(p.wallIds), "solo pareti R3")
        assertEquals(walls.walls.size, r.walls.size)
        val m = assertNotNull(r.main, PerimeterReport.text(r))
        assertTrue(m.state != PerimeterState.OPEN)
        // Gli angoli stanno sugli angoli veri della stanza (0..4 × 0..3), entro 3σ + 2 cm.
        val truth = listOf(0.0 to 0.0, 0.0 to 3.0, 4.0 to 3.0, 4.0 to 0.0)
        for (c in m.corners) assertTrue(truth.any { (x, z) -> abs(c.x - x) <= 3 * c.positionSigmaM + 0.02 && abs(c.z - z) <= 3 * c.positionSigmaM + 0.02 }, "angolo (${c.x}, ${c.z}) σ ${c.positionSigmaM}\n" + PerimeterReport.text(r))
        if (m.state == PerimeterState.CLOSED) { assertEquals(4, m.corners.size); assertEquals(12.0, m.areaM2!!, 0.25) }
        // Le ipotesi non sono mai pareti.
        assertTrue(r.hypotheses.all { it.status == "INFERRED / NOT OBSERVED" })
        println("R4 su stanza sintetica: ${r.state} — " + m.wallIds.joinToString("→") { "W$it" } + " · " + m.reasons.joinToString("; "))
    }

    @Test
    fun `prolungamento fuori dal volume esplorato - rifiutato`() {
        // Due pareti con estremità non osservate: l'angolo cadrebbe dove la mappa non esiste (z > 3,5 fuori dal volume).
        val a = wall(1, 0.0, 0.0, 0.0, 1.0, end = pa, endReason = EndReason.NOT_SEEN)
        val b = wall(2, 1.0, 6.0, 2.0, 6.0, start = pa, startReason = EndReason.NOT_SEEN)
        val bounded = SpaceQuery { _, _, z -> if (z > 3.5) -1 else 0 }
        val r = solve(listOf(a, b), bounded, cams = listOf(doubleArrayOf(1.0, 0.5), doubleArrayOf(1.5, 3.0)))
        val l = link(r, 1, 2)
        assertEquals(LinkDecision.REJECTED, l.decision)
        assertTrue(l.reasons.any { "volume esplorato" in it }, l.reasons.toString())
        // Con la mappa che copre la zona lo stesso collegamento è solo dedotto (non rifiutato per mancanza di volume).
        assertEquals(LinkSupport.INFERRED, link(solve(listOf(a, b), unknown, listOf(doubleArrayOf(1.0, 0.5), doubleArrayOf(1.5, 3.0))), 1, 2).support)
    }

    // ------------------------------------------------------------------------- R4.1: incertezza ≠ evidenza (A–H)

    /** Evidenza R3.1 di un'estremità: σ di misura (ripetibilità) e σ terminale (evidenza mancante), tenute separate. */
    private fun ev(measurement: Double, terminal: Double) = EndEvidence(1.0, 1.0, 1.0, 1.0, terminal * sqrt(12.0), 1.0, null, 10, measurement, terminal, null, null, null)

    /**
     * Quadrato 4 × 3 con tutte le estremità osservate esattamente, tranne l'inizio della parete z = 3 (W2), che si ferma a [gap] m
     * dall'angolo (0, 3) con stato, σ totale e σ di misura dati (σ terminale = il resto).
     */
    private fun squareWithEnd(gap: Double, sigma: Double, measurement: Double, state: EndState, reason: EndReason): PerimeterResult {
        val term = sqrt(max(0.0, sigma * sigma - measurement * measurement))
        val s = square()
        return solve(listOf(s[0], wall(2, gap, 3.0, 4.0, 3.0, start = state, startReason = reason, sStart = sigma, startEv = ev(measurement, term)), s[2], s[3]))
    }

    /** Nessun tratto non osservato dentro una chiusura e, se non chiude, l'angolo (0, 3) è dichiarato dedotto con il suo tratto. */
    private fun assertGapNotEvidence(r: PerimeterResult, gap: Double) {
        assertNotEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        val l = link(r, 1, 2)
        assertEquals(LinkSupport.INFERRED, l.support)
        val e = assertNotNull(l.toEnd)
        assertEquals(EndState.PARTIAL, e.endpointState)
        assertTrue(e.inferredCorner); assertFalse(e.gapObserved)
        assertEquals(gap, e.gapToIntersectionM, 1e-9)
        r.main?.let { m ->
            assertTrue(m.unobserved.any { it.wallId == 2 && it.kind.startsWith("prolungamento") && abs(it.lengthM - gap) < 1e-9 }, "il gap resta un tratto non osservato")
            // Il gap non entra nel χ²: l'errore di chiusura contiene solo le parti osservate (qui esatte); il gap è contato a parte.
            m.closure?.let { k ->
                assertTrue(k.unobservedM >= gap - 1e-9)
                assertTrue(k.errorM < 0.005, "errore ${k.errorM}: il gap non deve diventare errore di misura")
                assertEquals(8, k.dof) // 7 termini osservati (l'estremità di W1 arriva all'angolo), arrotondati per eccesso a 8
            }
        }
    }

    @Test
    fun `R4_1 A - estremità OSSERVATA con piccolo errore - può chiudere`() {
        val r = squareWithEnd(0.01, 0.016, 0.016, o, corner)
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        val e = link(r, 1, 2).toEnd!!
        assertTrue(e.observedEnd && e.gapObserved && !e.inferredCorner)
        assertEquals(8, r.main!!.closure!!.dof) // la parte osservata entra nel χ²
    }

    @Test
    fun `R4_1 B - PARTIAL a 8 cm con sigma 1,6 cm - non chiude`() = assertGapNotEvidence(squareWithEnd(0.08, 0.016, 0.016, pa, EndReason.SURFACE_NEAR), 0.08)

    @Test
    fun `R4_1 C - PARTIAL a 8 cm con sigma 7,9 cm - non chiude, nemmeno se tutta la sigma fosse di misura`() {
        assertGapNotEvidence(squareWithEnd(0.08, 0.079, 0.016, pa, EndReason.SURFACE_NEAR), 0.08) // σ terminale: evidenza mancante
        assertGapNotEvidence(squareWithEnd(0.08, 0.079, 0.079, pa, EndReason.SURFACE_NEAR), 0.08) // anche tutta di misura
    }

    @Test
    fun `R4_1 D - PARTIAL a 20 cm con sigma 7,9 cm - non chiude`() {
        assertGapNotEvidence(squareWithEnd(0.20, 0.079, 0.016, pa, EndReason.SURFACE_NEAR), 0.20)
        assertGapNotEvidence(squareWithEnd(0.20, 0.079, 0.079, pa, EndReason.SURFACE_NEAR), 0.20)
    }

    @Test
    fun `R4_1 E - PARTIAL a 50 cm con sigma 20 cm - non chiude`() = assertGapNotEvidence(squareWithEnd(0.50, 0.20, 0.016, pa, EndReason.SURFACE_NEAR), 0.50)

    @Test
    fun `R4_1 F - due pareti che si intersecano ma nessuna ha osservato l'angolo - non chiude`() {
        val s = square()
        val r = solve(listOf(
            wall(1, 0.0, 0.0, 0.0, 2.95, end = pa, endReason = EndReason.SURFACE_NEAR, sEnd = 0.079, sStart = 0.02, endEv = ev(0.016, 0.077)),
            wall(2, 0.05, 3.0, 4.0, 3.0, start = pa, startReason = EndReason.SURFACE_NEAR, sStart = 0.079, sEnd = 0.02, startEv = ev(0.016, 0.077)),
            s[2], s[3],
        ))
        assertNotEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        val l = link(r, 1, 2)
        assertEquals(LinkSupport.INFERRED, l.support)
        assertTrue(l.fromEnd!!.inferredCorner && l.toEnd!!.inferredCorner)
        assertEquals(PerimeterState.UNCERTAIN, r.main!!.state)
        assertTrue(r.main!!.reasons.any { "dedotto" in it && "PARTIAL" in it }, r.main!!.reasons.toString())
    }

    @Test
    fun `R4_1 G - angolo realmente osservato con rumore elevato - può chiudere`() {
        val r = squareWithEnd(0.06, 0.05, 0.05, o, corner)
        assertEquals(PerimeterState.CLOSED, r.state, PerimeterReport.text(r))
        assertTrue(link(r, 1, 2).toEnd!!.gapObserved)
    }

    @Test
    fun `R4_1 H - connessioni sane invariate - OSSERVATE entro la misura e PARTIAL che superano l'angolo`() {
        // Tutte OSSERVATE all'angolo: stesso esito di prima (CLOSED, supporto osservato, χ² su tutte le estremità).
        val sq = solve(square())
        assertEquals(PerimeterState.CLOSED, sq.state)
        assertTrue(sq.candidates.filter { it.decision == LinkDecision.ACCEPTED }.all { it.support == LinkSupport.OBSERVED && it.fromEnd!!.gapObserved && it.toEnd!!.gapObserved })
        // PARTIAL i cui dati arrivano oltre l'angolo (gap ≤ 0): l'angolo è coperto dai dati, supporto osservato.
        val s = square()
        val over = solve(listOf(s[0], wall(2, -0.03, 3.0, 4.0, 3.0, start = pa, startReason = EndReason.OCCLUDED, sStart = 0.02, startEv = ev(0.02, 0.0)), s[2], s[3]))
        val l = link(over, 1, 2)
        assertEquals(LinkSupport.OBSERVED, l.support); assertTrue(l.toEnd!!.gapObserved)
        assertEquals(PerimeterState.CLOSED, over.state, PerimeterReport.text(over))
    }

    @Test
    fun `R4_1 - angolo spiegato solo dalla sigma dell'intersezione (linee quasi parallele) resta dedotto`() {
        // B ruotata di 12° rispetto ad A; le linee si incontrano in (0; 3,30): 30 cm oltre la fine di A e 30 cm prima dell'inizio di B,
        // entrambe OSSERVATE/CORNER con σ di misura 3 cm. Lo scarto (10σ di misura) è compatibile solo per la σ dell'intersezione.
        val a = wall(1, 0.0, 0.0, 0.0, 3.0, sPos = 0.02, sThDeg = 1.5, sEnd = 0.03, endEv = ev(0.03, 0.0))
        val s12 = Math.sin(Math.toRadians(12.0)); val c12 = Math.cos(Math.toRadians(12.0))
        val bx = 0.30 * s12; val bz = 3.30 + 0.30 * c12
        val b = wall(2, bx, bz, bx + 2 * s12, bz + 2 * c12, sPos = 0.02, sThDeg = 1.5, sStart = 0.03, startEv = ev(0.03, 0.0))
        val l = link(solve(listOf(a, b), cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.0, 4.0))), 1, 2)
        assertNotEquals(LinkDecision.REJECTED, l.decision, l.reasons.toString())
        assertEquals(0.30, l.fromEnd!!.gapToIntersectionM, 1e-6); assertEquals(0.30, l.toEnd!!.gapToIntersectionM, 1e-6)
        assertTrue(abs(l.zFrom) <= 3 && abs(l.zTo) <= 3, "compatibile per la σ propagata: z ${l.zFrom}, ${l.zTo}")
        assertTrue(l.fromEnd!!.intersectionSigmaM > 3 * 0.03)
        assertEquals(LinkSupport.INFERRED, l.support, l.reasons.toString())
        // Regola generale verificata sui valori riportati: un'estremità OSSERVATA conta come angolo osservato solo entro 3σ di misura.
        for (e in listOfNotNull(l.fromEnd, l.toEnd)) assertEquals(e.gapToIntersectionM <= 0 || (e.observedEnd && e.gapToIntersectionM <= 3 * e.measurementSigmaM), e.gapObserved)
    }

    @Test
    fun `chi quadro - sopravvivenza per gradi di liberta pari`() {
        assertEquals(1.0, PerimeterSolver.chi2SurvivalEven(0.0, 8), 1e-12)
        assertEquals(Math.exp(-1.0), PerimeterSolver.chi2SurvivalEven(2.0, 2), 1e-12)
        assertEquals(0.10, PerimeterSolver.chi2SurvivalEven(13.36, 8), 1e-3) // tabella χ²(8): 13,36 ↔ 0,10
    }
}
