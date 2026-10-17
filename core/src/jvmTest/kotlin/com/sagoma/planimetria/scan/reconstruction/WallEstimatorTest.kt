package com.sagoma.planimetria.scan.reconstruction

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** R3 su ingressi sintetici costruiti a mano: ogni test controlla un comportamento richiesto. */
class WallEstimatorTest {
    /** Una superficie sintetica: tratto di parete verticale, punti visti da [groups] camere davanti, rumore e spostamento temporale noti. */
    private data class Sim(
        val id: Int,
        val ax: Double, val az: Double, val bx: Double, val bz: Double,
        val y0: Double = 0.1, val y1: Double = 2.2,
        val kind: SurfaceKind = SurfaceKind.VERTICAL_STRUCTURAL,
        val noise: Double = 0.005,
        val groups: List<Int> = (0 until 10).toList(),
        /** Spostamento temporale (m) lungo la normale, per gruppo. */
        val dispNormal: (Int) -> Double = { 0.0 },
        val dispAlong: (Int) -> Double = { 0.0 },
        /** Scarto sistematico lungo la normale, per gruppo (per simulare misure distorte). */
        val bias: (Int) -> Double = { 0.0 },
        val skip: (Double) -> Boolean = { false },
        val stepU: Double = 0.03,
        val outlierEvery: Int = 0,
        val cameraSide: Double = 1.0,
    )

    private class Built(val input: WallInput)

    private fun build(sims: List<Sim>, space: SpaceQuery = SpaceQuery { _, _, _ -> 0 }): Built {
        val x = mutableListOf<Double>(); val y = mutableListOf<Double>(); val z = mutableListOf<Double>()
        val w = mutableListOf<Double>(); val r = mutableListOf<Double>(); val g = mutableListOf<Int>(); val fr = mutableListOf<Int>()
        val dx = mutableListOf<Double>(); val dy = mutableListOf<Double>(); val dz = mutableListOf<Double>()
        val surfaces = mutableListOf<WallInputSurface>()
        val cams = HashMap<Int, DoubleArray>()
        var seed = 12345L
        fun rnd(): Double { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % 2001) / 1000.0 - 1.0 }
        for (s in sims) {
            val len = sqrt((s.bx - s.ax) * (s.bx - s.ax) + (s.bz - s.az) * (s.bz - s.az))
            val ux = (s.bx - s.ax) / len; val uz = (s.bz - s.az) / len
            // Normale verso le camere: lato sinistro della direzione × cameraSide.
            val nx = -uz * s.cameraSide; val nz = ux * s.cameraSide
            val idx = mutableListOf<Int>()
            for (gi in s.groups) {
                val gid = s.id * 100 + gi
                val camU = len * (gi + 0.5) / s.groups.size
                val cam = doubleArrayOf(s.ax + ux * camU + nx * 1.8, 1.4, s.az + uz * camU + nz * 1.8)
                cams[gid] = cam
                var u = 0.0; var k = 0
                while (u <= len + 1e-9) {
                    if (!s.skip(u)) {
                        var yy = s.y0
                        while (yy <= s.y1 + 1e-9) {
                            k++
                            val off = if (s.outlierEvery > 0 && k % s.outlierEvery == 0) 0.3 + abs(rnd()) * 0.7 else rnd() * s.noise + s.bias(gi)
                            val px = s.ax + ux * u + nx * off; val pz = s.az + uz * u + nz * off
                            idx.add(x.size)
                            x.add(px); y.add(yy); z.add(pz); w.add(1.0)
                            r.add(sqrt((px - cam[0]) * (px - cam[0]) + (yy - cam[1]) * (yy - cam[1]) + (pz - cam[2]) * (pz - cam[2])))
                            g.add(gid); fr.add(gid)
                            val dn = s.dispNormal(gi); val da = s.dispAlong(gi)
                            dx.add(nx * dn + ux * da); dy.add(0.0); dz.add(nz * dn + uz * da)
                            yy += 0.1
                        }
                    }
                    u += s.stepU
                }
            }
            surfaces.add(WallInputSurface(s.id, s.kind, Orientation.VERTICAL, 0.8, idx.toIntArray()))
        }
        val pts = WallPoints(
            x.toDoubleArray(), y.toDoubleArray(), z.toDoubleArray(), w.toDoubleArray(), r.toDoubleArray(), g.toIntArray(), fr.toIntArray(),
            dx.toDoubleArray(), dy.toDoubleArray(), dz.toDoubleArray(),
        )
        return Built(WallInput(pts, surfaces, 0.0, space, cams))
    }

    private fun run(sims: List<Sim>, space: SpaceQuery = SpaceQuery { _, _, _ -> 0 }) = WallEstimator.estimate(build(sims, space).input)

    /** Spazio: libero ovunque davanti alle pareti z = 0 (z > 0.02) e oltre x in [lo, hi] sul piano z = 0. */
    private fun freeBeyond(lo: Double, hi: Double) = SpaceQuery { x, _, z -> if (z > 0.02 || (z > -0.5 && (x < lo || x > hi))) 1 else 0 }

    // 1
    @Test
    fun `1 - parete singola perfettamente osservata`() {
        val res = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0)), freeBeyond(0.0, 3.0))
        assertEquals(1, res.walls.size)
        val w = res.walls[0]
        assertEquals(0.0, abs(w.geometry.d), 0.002)
        assertTrue(abs(w.geometry.nz) > 0.999)
        assertEquals(3.0, w.geometry.lengthM, 0.03)
        assertEquals(1, w.geometry.observedSpans.size)
        assertTrue(w.evidence.observedCoverage > 0.98)
        assertEquals(EndState.OBSERVED, w.start.state); assertEquals(EndReason.FREE_BEYOND, w.start.reason)
        assertEquals(EndState.OBSERVED, w.end.state)
        assertTrue(w.uncertainty.positionSigmaM < 0.005)
        assertEquals(listOf(1), w.sourceSurfaceIds)
    }

    // 2
    @Test
    fun `2 - parete con outlier - il fit non si sposta`() {
        val res = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, outlierEvery = 10)))
        val w = res.walls.single()
        assertEquals(0.0, abs(w.geometry.d), 0.003)
        assertTrue(w.evidence.outlierFraction in 0.08..0.12, "outlier ${w.evidence.outlierFraction}")
        assertTrue(w.evidence.fitRmsM < 0.01)
    }

    // 3 e 17
    @Test
    fun `3 e 17 - frammenti compatibili si fondono, il gap resta non osservato`() {
        val res = run(listOf(Sim(1, 0.0, 0.0, 1.4, 0.0), Sim(2, 1.8, 0.0, 3.0, 0.0)))
        assertEquals(1, res.walls.size)
        val w = res.walls[0]
        assertEquals(listOf(1, 2), w.sourceSurfaceIds)
        assertEquals(listOf(listOf(1, 2)), res.merges)
        assertEquals(1, w.gaps.size)
        val gap = w.gaps[0]
        val gx0 = w.geometry.pointAt(gap.fromU)[0]; val gx1 = w.geometry.pointAt(gap.toU)[0]
        assertTrue(minOf(gx0, gx1) in 1.35..1.5 && maxOf(gx0, gx1) in 1.75..1.85, "gap da $gx0 a $gx1")
        assertEquals(2, w.geometry.observedSpans.size)
        assertEquals(2.6, w.geometry.observedLengthM, 0.08) // 1,4 + 1,2: il gap di 0,4 m non è contato
        assertTrue(w.geometry.lengthM - w.geometry.observedLengthM > 0.3)
    }

    // 4
    @Test
    fun `4 - due pareti parallele a 10 cm non si fondono`() {
        val res = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0), Sim(2, 0.0, 0.10, 3.0, 0.10)))
        assertEquals(2, res.walls.size)
        assertTrue(res.merges.isEmpty())
    }

    // 5
    @Test
    fun `5 - orientamenti diversi non si fondono`() {
        val a = 25.0 * Math.PI / 180
        val res = run(listOf(Sim(1, 0.0, 0.0, 2.0, 0.0), Sim(2, 2.0, 0.0, 2.0 + 1.5 * cos(a), 1.5 * sin(a))))
        assertEquals(2, res.walls.size)
        assertTrue(res.merges.isEmpty())
    }

    // 6
    @Test
    fun `6 - estremità parzialmente osservate - nessuna estensione`() {
        val res = run(listOf(Sim(1, 0.0, 0.0, 2.0, 0.0)))
        val w = res.walls.single()
        val xs = listOf(w.geometry.pointAt(w.geometry.startU)[0], w.geometry.pointAt(w.geometry.endU)[0]).sorted()
        assertTrue(xs[0] >= -0.01 && xs[1] <= 2.01, "estremi $xs oltre i dati")
        assertEquals(EndState.PARTIAL, w.start.state); assertEquals(EndReason.NOT_SEEN, w.start.reason)
        assertEquals(EndState.PARTIAL, w.end.state)
    }

    // 7
    @Test
    fun `7 - parete dietro un oggetto - il tratto nascosto non viene inventato`() {
        // Oggetto davanti alla parete tra x = 1 e x = 2 (a 30 cm): lì la parete non ha punti.
        val space = SpaceQuery { x, _, z -> if (x in 1.0..2.0 && z in 0.25..0.4) 2 else if (z > 0.02) 1 else 0 }
        val res = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, skip = { it in 1.0..2.0 })), space)
        val w = res.walls.single()
        assertEquals(1, w.gaps.size)
        assertEquals(GapReason.OCCLUDED, w.gaps[0].reason)
        assertEquals(2.0, w.geometry.observedLengthM, 0.08)
        for (sp in w.geometry.observedSpans) {
            val xs = listOf(w.geometry.pointAt(sp.fromU)[0], w.geometry.pointAt(sp.toU)[0]).sorted()
            assertTrue(xs[1] <= 1.05 || xs[0] >= 1.95, "tratto osservato $xs dentro la zona nascosta")
        }
        assertTrue(w.possibleContinuation.any { "nascosto" in it })
    }

    // 8
    @Test
    fun `8 - una sola faccia - spessore sconosciuto`() {
        val w = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0))).walls.single()
        assertEquals(ThicknessState.UNKNOWN, w.thickness.state)
        assertEquals(null, w.thickness.valueM)
    }

    // 9
    @Test
    fun `9 - due facce compatibili - spessore stimato con la sua incertezza`() {
        // Faccia 1 a z = 0 vista da z > 0; faccia 2 a z = −0,12 vista da z < 0 (l'altra stanza). Tra le due nessuno spazio libero.
        val res = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0), Sim(2, 0.0, -0.12, 3.0, -0.12, cameraSide = -1.0)), SpaceQuery { _, _, z -> if (z > 0.02 || z < -0.14) 1 else 0 })
        assertEquals(2, res.walls.size)
        for (w in res.walls) {
            assertEquals(ThicknessState.MEASURED, w.thickness.state, w.thickness.note)
            assertEquals(0.12, assertNotNull(w.thickness.valueM), 0.005)
            assertTrue(assertNotNull(w.thickness.sigmaM) > 0)
        }
        assertEquals(res.walls[1].id, res.walls[0].thickness.otherWallId)
        // Con spazio libero visto tra le due facce: non è lo stesso muro → sconosciuto.
        val split = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0), Sim(2, 0.0, -0.12, 3.0, -0.12, cameraSide = -1.0)), SpaceQuery { _, _, z -> if (z > 0.02 || z < -0.14 || z in -0.10..-0.02) 1 else 0 })
        assertEquals(2, split.walls.size)
        assertTrue(split.walls.all { it.thickness.state == ThicknessState.UNKNOWN })
    }

    // 10, 11, 16
    @Test
    fun `10 11 16 - sigma temporale - alta pesa meno, bassa pesa normale, non domina il fit`() {
        // Metà dei gruppi vede la parete 5 cm più avanti (misura distorta). Con la stessa σ temporale i due insiemi pesano uguale.
        val biased: (Int) -> Double = { g -> if (g >= 5) 0.05 else 0.0 }
        val equal = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, bias = biased, dispNormal = { 0.005 }))).walls.single()
        assertEquals(0.025, abs(equal.geometry.d), 0.008)
        // Se i gruppi distorti hanno grande ambiguità temporale (20 cm), contano molto meno: il fit resta sui gruppi affidabili.
        val down = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, bias = biased, dispNormal = { g -> if (g >= 5) 0.20 else 0.005 }))).walls.single()
        assertTrue(abs(down.geometry.d) < 0.01, "d = ${down.geometry.d}")
        // Anche con 3 volte più gruppi distorti ad alta σ temporale, quelli non dominano.
        val many = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, groups = (0 until 12).toList(), bias = { g -> if (g >= 3) 0.05 else 0.0 }, dispNormal = { g -> if (g >= 3) 0.25 else 0.005 }))).walls.single()
        assertTrue(abs(many.geometry.d) < 0.02, "d = ${many.geometry.d}")
        assertTrue(abs(many.geometry.d) < 0.04) // senza pesi sarebbe ~0,0375
    }

    // 12
    @Test
    fun `12 - VERTICAL_OBJECT non viene promosso`() {
        val res = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, kind = SurfaceKind.VERTICAL_OBJECT)))
        assertTrue(res.walls.isEmpty())
        assertEquals(SurfaceKind.VERTICAL_OBJECT, res.excluded.single().kind)
        assertTrue("non viene mai promosso" in res.excluded.single().reason)
        val unknown = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, kind = SurfaceKind.UNKNOWN)))
        assertTrue(unknown.walls.isEmpty())
    }

    @Test
    fun `oggetto basso classificato strutturale - spazio libero visto sopra - escluso con il motivo`() {
        // Superficie alta solo 0,8 m (il fianco di un letto); sopra, sul suo piano, i raggi passano (visto libero).
        val space = SpaceQuery { _, y, z -> if (z > 0.02 || y > 0.95) 1 else 0 }
        val res = run(listOf(Sim(1, 0.0, 0.0, 2.0, 0.0, y0 = 0.1, y1 = 0.8)), space)
        assertTrue(res.walls.isEmpty())
        assertTrue("oggetto basso" in res.excluded.single().reason, res.excluded.single().reason)
        // La stessa superficie con sopra un'osservazione ignota (mai visto) resta una parete: non si scarta senza evidenza.
        val unknownAbove = run(listOf(Sim(1, 0.0, 0.0, 2.0, 0.0, y0 = 0.1, y1 = 0.8)), SpaceQuery { _, _, z -> if (z > 0.02) 1 else 0 })
        assertEquals(1, unknownAbove.walls.size)
    }

    // 13
    @Test
    fun `13 - pochi punti - scarto con motivo`() {
        val res = run(listOf(Sim(1, 0.0, 0.0, 0.1, 0.0, groups = listOf(0), y0 = 1.0, y1 = 1.2)))
        assertTrue(res.walls.isEmpty())
        assertTrue(res.excluded.single().reason.startsWith("evidenza insufficiente") || "fit impossibile" in res.excluded.single().reason)
        val oneView = run(listOf(Sim(1, 0.0, 0.0, 2.0, 0.0, groups = listOf(0))))
        assertTrue(oneView.walls.isEmpty())
        assertTrue("gruppo di vista" in oneView.excluded.single().reason)
    }

    // 14
    @Test
    fun `14 - deterministico`() {
        val sims = listOf(Sim(1, 0.0, 0.0, 1.4, 0.0, outlierEvery = 13), Sim(2, 1.8, 0.0, 3.0, 0.0), Sim(3, 3.0, 0.0, 3.0, 2.5))
        val a = run(sims, freeBeyond(0.0, 3.0)); val b = run(sims, freeBeyond(0.0, 3.0))
        assertEquals(a.walls, b.walls)
        assertEquals(WallReport.csv(a), WallReport.csv(b))
    }

    // 15
    @Test
    fun `15 - propagazione della sigma temporale`() {
        val w = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, dispNormal = { 0.02 }))).walls.single()
        assertEquals(0.02, w.uncertainty.temporalSigmaM, 1e-6)
        assertEquals(0.02 / sqrt(w.evidence.viewGroups.toDouble()), w.uncertainty.temporalContributionM, 1e-6)
        assertTrue(w.uncertainty.positionSigmaM >= w.uncertainty.temporalContributionM)
        // Lo spostamento lungo la parete non entra nella σ di posizione ma nelle estremità.
        val along = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0, dispAlong = { 0.10 }))).walls.single()
        val none = run(listOf(Sim(1, 0.0, 0.0, 3.0, 0.0))).walls.single()
        assertEquals(0.0, along.uncertainty.temporalSigmaM, 1e-4) // solo l'inclinazione numerica della normale stimata
        assertTrue(along.uncertainty.endSigmaM > none.uncertainty.endSigmaM)
    }

    @Test
    fun `catena completa R1-R2-R3 sulla stanza sintetica - pareti vere, mobile escluso, nessuna estensione`() {
        val room = SyntheticRoom(noiseM = 0.01)
        val (rec, blobs) = room.recording()
        val map = GlobalMap.build(rec, { blobs[it] })
        val s = SurfaceExtractor.extract(map, rec)
        val res = WallEstimator.estimate(WallEstimator.inputFrom(map, s, rec))
        // Ogni parete stimata sta su uno dei quattro muri veri (x = 0, x = 4, z = 0, z = 3) e dentro la stanza.
        assertTrue(res.walls.size >= 3)
        for (w in res.walls) {
            val g = w.geometry
            val a = g.pointAt(g.startU); val b = g.pointAt(g.endU)
            val onWall = listOf(a, b).all { q -> minOf(abs(q[0]), abs(q[0] - 4), abs(q[1]), abs(q[1] - 3)) < 0.05 }
            assertTrue(onWall, "W${w.id} non sta su un muro: $a → $b")
            assertTrue(listOf(a, b).all { q -> q[0] in -0.05..4.05 && q[1] in -0.05..3.05 }, "W${w.id} esce dalla stanza")
            assertEquals(ThicknessState.UNKNOWN, w.thickness.state)
        }
        // Il fronte del mobile (z = 1,1) non diventa parete.
        assertTrue(res.walls.none { abs(it.geometry.nz) > 0.9 && abs(it.geometry.cz - 1.1) < 0.1 })
        // Spostamento temporale: in questa registrazione la raw è del suo frame, quindi A = C e σ temporale nulla.
        assertTrue(res.walls.all { it.uncertainty.temporalSigmaM < 1e-6 })
    }

    // 18
    @Test
    fun `18 - estremità osservata (angolo) ed estremità nascosta restano diverse`() {
        // Parete lungo x da 0 a 2; all'inizio un'altra parete perpendicolare (x = 0); oltre la fine, un oggetto davanti.
        val space = SpaceQuery { x, _, z -> if (x in 2.05..2.6 && z in 0.1..0.6) 2 else if (z > 0.02 && x > 0.02) 1 else 0 }
        val res = run(listOf(Sim(1, 0.0, 0.0, 2.0, 0.0), Sim(2, 0.0, 2.0, 0.0, 0.0)), space)
        val w = res.walls.first { abs(it.geometry.nz) > 0.9 }
        val atZero = if (abs(w.geometry.pointAt(w.start.u)[0]) < 0.2) w.start else w.end
        val atTwo = if (atZero === w.start) w.end else w.start
        assertEquals(EndState.OBSERVED, atZero.state); assertEquals(EndReason.CORNER, atZero.reason)
        assertEquals(EndState.PARTIAL, atTwo.state); assertEquals(EndReason.OCCLUDED, atTwo.reason)
        assertTrue(w.possibleContinuation.isNotEmpty())
    }

    // ------------------------------------------------------------------------------------------- R3.1: estremità e loro σ

    /** Parete lungo x (z = 0) da [x0] a 3 m e, se [cornerX] non è null, una parete perpendicolare in x = [cornerX] (da z = 2 a 0). */
    private fun cornerCase(x0: Double, cornerX: Double? = 0.0, groups: Int = 10, skip: (Double) -> Boolean = { false }): Pair<EstimatedWall, WallEnd> {
        val sims = listOfNotNull(
            Sim(1, x0, 0.0, 3.0, 0.0, groups = (0 until groups).toList(), skip = skip),
            cornerX?.let { Sim(2, it, 2.0, it, 0.0) },
        )
        val res = run(sims, SpaceQuery { x, _, z -> if (z > 0.02 && x > (cornerX ?: -10.0) + 0.02) 1 else 0 })
        val w = res.walls.first { abs(it.geometry.nz) > 0.9 }
        val e = if (abs(w.geometry.pointAt(w.start.u)[0] - x0) < abs(w.geometry.pointAt(w.end.u)[0] - x0)) w.start else w.end
        return w to e
    }

    /** Regola R3.1, verificata sui valori riportati: CORNER ⇔ intersezione entro 3σ ed evidenza terminale forte. */
    private fun assertCornerRule(e: WallEnd) {
        val v = assertNotNull(e.evidence)
        val reach = v.surfaceGapSigmas != null && abs(v.surfaceGapSigmas!!) <= 3.0
        assertEquals(reach && v.strong, e.reason == EndReason.CORNER, "estremità $e")
        if (e.reason == EndReason.CORNER) assertTrue(abs(v.surfaceGapM!!) <= 3 * e.sigmaM)
    }

    @Test
    fun `R3_1 1 - parete che arriva esattamente all'angolo - CORNER osservato`() {
        val (_, e) = cornerCase(0.0)
        assertEquals(EndState.OBSERVED, e.state); assertEquals(EndReason.CORNER, e.reason)
        assertEquals(0.0, e.evidence!!.surfaceGapM!!, 0.015)
        assertCornerRule(e)
    }

    @Test
    fun `R3_1 2 - estremità a 2 cm dall'angolo - entro le sigma, CORNER`() {
        val (_, e) = cornerCase(0.02)
        assertEquals(0.02, e.evidence!!.surfaceGapM!!, 0.012)
        assertEquals(EndReason.CORNER, e.reason)
        assertCornerRule(e)
    }

    @Test
    fun `R3_1 3 - estremità a 5 cm dall'angolo - decide il rapporto con la sigma`() {
        val (_, e) = cornerCase(0.05)
        assertEquals(0.05, e.evidence!!.surfaceGapM!!, 0.012)
        assertCornerRule(e)
        if (e.reason != EndReason.CORNER) assertEquals(EndReason.SURFACE_NEAR, e.reason)
    }

    @Test
    fun `R3_1 4 - estremità a 8 cm dall'angolo - non è un angolo osservato`() {
        val (_, e) = cornerCase(0.08)
        val v = e.evidence!!
        assertEquals(0.08, v.surfaceGapM!!, 0.012)
        assertTrue(v.surfaceGapSigmas!! > 3, "z = ${v.surfaceGapSigmas}")
        assertEquals(EndReason.SURFACE_NEAR, e.reason); assertNotEquals(EndState.OBSERVED, e.state)
        assertEquals(2, v.surfaceId)
        assertCornerRule(e)
    }

    /** Ultimi [len] m (verso x = 0) con un campione ogni [every] passi da 3 cm; l'interno resta denso (riferimento locale). */
    private fun sparseNear0(every: Int, len: Double = 0.30): (Double) -> Boolean = { u -> u < len && (kotlin.math.round(u / 0.03).toInt() % every != 0) }

    @Test
    fun `R3_1 5 - evidenza terminale molto scarsa - sigma terminale positiva, evidenza debole`() {
        val (_, e) = cornerCase(0.0, cornerX = null, skip = sparseNear0(5, 0.20))
        val v = e.evidence!!
        assertTrue(v.thinZoneM > 0.1, "zona ${v.thinZoneM}")
        assertTrue(v.terminalSigmaM > 0.03); assertFalse(v.strong)
        assertEquals(sqrt(v.repeatSigmaM * v.repeatSigmaM + v.terminalSigmaM * v.terminalSigmaM), e.sigmaM, 1e-12)
        assertEquals(v.thinZoneM / sqrt(12.0), v.terminalSigmaM, 1e-12)
        assertTrue(v.density10 < 0.5)
    }

    @Test
    fun `R3_1 6 - evidenza terminale forte - sigma uguale alla ripetibilità`() {
        val (_, e) = cornerCase(0.0, cornerX = null)
        val v = e.evidence!!
        assertTrue(v.thinZoneM < 0.03, "zona ${v.thinZoneM}") // sotto il passo dei campioni (3 cm)
        assertTrue(v.strong)
        assertEquals(v.repeatSigmaM, e.sigmaM, 0.001)
        assertTrue(v.groupsNearEnd >= 3); assertTrue(v.density20 > 0.8)
    }

    @Test
    fun `R3_1 7 - gap terminale senza altra superficie - nessun angolo`() {
        // Dati fino a x = 0,1, poi buco di 25 cm, poi il resto: il tratto terminale è corto e isolato.
        val (w, e) = cornerCase(0.0, cornerX = null, skip = { u -> u in 0.11..0.35 })
        val v = e.evidence!!
        assertNull(v.surfaceId)
        assertEquals(0.25, v.terminalGapM!!, 0.06); assertTrue(v.lastSpanM < 0.15)
        assertTrue(e.reason != EndReason.CORNER && e.reason != EndReason.SURFACE_NEAR)
        assertNotEquals(EndState.OBSERVED, e.state)
        assertEquals(1, w.gaps.size)
    }

    @Test
    fun `R3_1 8 - altra superficie compatibile oltre l'estremità - SURFACE_NEAR con la distanza`() {
        val (_, e) = cornerCase(0.10, cornerX = 0.0)
        val v = e.evidence!!
        assertEquals(2, v.surfaceId); assertEquals(0.10, v.surfaceGapM!!, 0.012)
        assertEquals(EndReason.SURFACE_NEAR, e.reason); assertEquals(EndState.PARTIAL, e.state)
    }

    @Test
    fun `R3_1 9 - vero angolo osservato da più viste`() {
        val (_, e) = cornerCase(0.0, groups = 10)
        assertTrue(e.evidence!!.groupsNearEnd >= 8)
        assertEquals(EndReason.CORNER, e.reason); assertEquals(EndState.OBSERVED, e.state)
    }

    @Test
    fun `R3_1 10 - falso angolo per fine dell'evidenza - non CORNER`() {
        // Geometria esattamente all'angolo, ma l'evidenza si dirada negli ultimi 30 cm: l'angolo non è dimostrato.
        val (_, e) = cornerCase(0.0, skip = sparseNear0(5))
        assertFalse(e.evidence!!.strong)
        assertNotEquals(EndReason.CORNER, e.reason)
        assertNotEquals(EndState.OBSERVED, e.state)
        assertCornerRule(e)
    }

    @Test
    fun `R3_1 11 - la sigma cresce quando diminuisce l'evidenza terminale`() {
        val sig = listOf(1, 2, 3, 5).map { k -> cornerCase(0.0, cornerX = null, skip = if (k == 1) ({ false }) else sparseNear0(k)).second }
        val terminal = sig.map { it.evidence!!.terminalSigmaM }
        for (i in 1 until terminal.size) assertTrue(terminal[i] >= terminal[i - 1], "σ terminali $terminal")
        assertTrue(sig.last().sigmaM > sig.first().sigmaM)
        assertTrue(sig.last().evidence!!.density20 < sig.first().evidence!!.density20)
    }

    @Test
    fun `R3_1 12 - nessuna geometria aggiunta - l'estremità resta sul dato più esterno`() {
        for (x0 in listOf(0.02, 0.05, 0.08, 0.10)) {
            val (w, e) = cornerCase(x0)
            val x = w.geometry.pointAt(e.u)[0]
            assertEquals(x0, x, 0.006, "estremità in x = $x per dati da x = $x0") // rumore laterale di 5 mm, nessun prolungamento
            assertEquals(3.0 - x0, w.geometry.lengthM, 0.035) // passo dei campioni 3 cm all'altra estremità
        }
    }

    @Test
    fun `R3_1 13 - caso R1-R4 della stanza sintetica - nessuna estremità osservata con sigma incompatibile`() {
        val room = SyntheticRoom(noiseM = 0.01)
        val (rec, blobs) = room.recording()
        val map = GlobalMap.build(rec, { blobs[it] })
        val s = SurfaceExtractor.extract(map, rec)
        val res = WallEstimator.estimate(WallEstimator.inputFrom(map, s, rec))
        // Il caso di R4: parete z = 3 con l'inizio a x ≈ 0,08 (8 cm dall'angolo x = 0), σ di ripetibilità ≈ 1,6 cm.
        val w = res.walls.first { abs(it.geometry.nz) > 0.9 && abs(it.geometry.cz - 3) < 0.05 }
        val e = listOf(w.start, w.end).minBy { abs(w.geometry.pointAt(it.u)[0]) }
        val x = w.geometry.pointAt(e.u)[0]
        assertEquals(0.08, x, 0.02) // la geometria osservata è la stessa: nessun prolungamento
        val v = e.evidence!!
        assertEquals(x, v.surfaceGapM!!, 0.01)
        // Prima (R3): OBSERVED/CORNER con σ 1,6 cm (5,1σ). Ora: l'estremità non è dichiarata angolo osservato, e la σ include la zona
        // terminale diradata misurata (R2 assegna solo in parte i voxel vicino all'angolo).
        assertNotEquals(EndReason.CORNER, e.reason); assertNotEquals(EndState.OBSERVED, e.state)
        assertEquals(EndReason.SURFACE_NEAR, e.reason)
        assertTrue(v.repeatSigmaM < 0.02, "ripetibilità ${v.repeatSigmaM}") // la vecchia σ era solo questa
        assertTrue(e.sigmaM > v.repeatSigmaM && v.terminalSigmaM > 0)
        println("R3.1 caso 8 cm: x ${"%.4f".format(x)} · σ ripetibilità ${"%.4f".format(v.repeatSigmaM)} · zona ${"%.4f".format(v.thinZoneM)} · σ terminale ${"%.4f".format(v.terminalSigmaM)} · σ ${"%.4f".format(e.sigmaM)} · z ${"%.2f".format(v.surfaceGapSigmas)} · forte ${v.strong} · densità 5/10/20 ${"%.2f".format(v.density5)}/${"%.2f".format(v.density10)}/${"%.2f".format(v.density20)} · viste ${v.groupsNearEnd}")
        // Nessuna estremità della stanza resta OSSERVATA/CORNER con lo scarto dall'angolo oltre 3σ.
        for (ww in res.walls) for (ee in listOf(ww.start, ww.end)) assertCornerRule(ee)
        val r4 = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, res))
        println("R3.1 stanza sintetica: " + res.walls.joinToString(" | ") { "W${it.id} ${it.start.state}/${it.start.reason} σ${"%.3f".format(it.start.sigmaM)} → ${it.end.state}/${it.end.reason} σ${"%.3f".format(it.end.sigmaM)}" } + " · R4 ${r4.state}")
    }

    @Test
    fun `R3_1 14 - deterministico`() {
        val a = cornerCase(0.05, skip = sparseNear0(3)); val b = cornerCase(0.05, skip = sparseNear0(3))
        assertEquals(a.first, b.first)
        val sims = listOf(Sim(1, 0.08, 0.0, 3.0, 0.0), Sim(2, 0.0, 2.0, 0.0, 0.0))
        assertEquals(WallReport.endsCsv(run(sims)), WallReport.endsCsv(run(sims)))
    }
}
