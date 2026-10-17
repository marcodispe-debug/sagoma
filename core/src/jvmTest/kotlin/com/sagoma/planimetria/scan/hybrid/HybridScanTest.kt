package com.sagoma.planimetria.scan.hybrid

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.sketch.RoomSketch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prototipo del metodo ibrido su una stanza SINTETICA (4 × 3 m, alta 2,6 m, rumore 2 cm): prova la LOGICA (riferimenti, esclusioni,
 * frammenti dubbi, proposte, schizzo, scatola). Non è una prova di accuratezza reale: per quella servono le registrazioni (P0).
 */
class HybridScanTest {
    private val sketch = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)))
    private val rnd = Random(2026)
    private fun noise(s: Double): Double { val u = rnd.nextDouble().coerceAtLeast(1e-12); val v = rnd.nextDouble(); return s * sqrt(-2 * ln(u)) * cos(2 * Math.PI * v) }

    /** Un insieme di punti di una superficie, con il flag "al centro dell'immagine". */
    private class Patch(val pts: List<DoubleArray>, val central: Boolean)

    private fun grid(n: Int, f: (Double, Double) -> DoubleArray): List<DoubleArray> =
        (0 until n).flatMap { i -> (0 until n).map { j -> f((i + 0.5) / n, (j + 0.5) / n) } }

    /** Parete del rettangolo 4 × 3 (lati come lo schizzo), spostata di [offset] verso l'interno, tratto [u0]–[u1] (0–1) e quote. */
    private fun wall(side: Int, u0: Double = 0.2, u1: Double = 0.8, y0: Double = 0.5, y1: Double = 2.0, offset: Double = 0.0, s: Double = 0.02, central: Boolean = true, n: Int = 40): Patch {
        val c = listOf(ArXZ(0.0, 0.0), ArXZ(4.0, 0.0), ArXZ(4.0, 3.0), ArXZ(0.0, 3.0))
        val a = c[side]; val b = c[(side + 1) % 4]
        val d = (b - a).normalized(); val inward = ArXZ(-d.z, d.x).let { if ((ArXZ(2.0, 1.5) - a) dot it > 0) it else it * -1.0 }
        return Patch(grid(n) { p, q ->
            val pt = a + (b - a) * (u0 + (u1 - u0) * p) + inward * (offset + noise(s))
            doubleArrayOf(pt.x, y0 + (y1 - y0) * q, pt.z)
        }, central)
    }

    private fun horizontal(y: Double, x0: Double = 1.0, x1: Double = 3.0, z0: Double = 0.8, z1: Double = 2.2, s: Double = 0.02, central: Boolean = true) =
        Patch(grid(40) { p, q -> doubleArrayOf(x0 + (x1 - x0) * p, y + noise(s), z0 + (z1 - z0) * q) }, central)

    private var frameId = 0
    private var t = 0L

    /** Mondo di ARCore corrente rispetto a quello della stanza (per simulare le perdite di posizione): si applica a punti e camera. */
    private var arWorld = Rigid()

    private fun frame(vararg patches: Patch, cam: DoubleArray = doubleArrayOf(2.0, 1.4, 1.5)): DepthPoints {
        val all = patches.flatMap { p -> p.pts.map { it to p.central } }
        t += 500
        val w = arWorld
        return DepthPoints(frameId++, t, w.x(cam[0], cam[2]), w.y(cam[1]), w.z(cam[0], cam[2]),
            DoubleArray(all.size) { w.x(all[it].first[0], all[it].first[2]) }, DoubleArray(all.size) { w.y(all[it].first[1]) }, DoubleArray(all.size) { w.z(all[it].first[0], all[it].first[2]) },
            BooleanArray(all.size) { all[it].second }, 14400)
    }

    private fun feed(s: HybridScan, times: Int, vararg patches: Patch) = repeat(times) { s.addDepth(frame(*patches)) }

    /** Fino alle pareti: pavimento confermato e percorso (ordine pavimento → pareti → soffitto, P0.19). */
    private fun toWalls(heightM: Double? = 2.7): HybridScan {
        val s = HybridScan(sketch, heightM)
        s.chooseStartSide(0)
        feed(s, 4, horizontal(0.0))
        assertTrue(s.confirmReference())
        feed(s, 4, horizontal(0.0, 0.3, 3.7, 0.3, 2.7, central = false))
        assertTrue(s.nextPhase())
        assertEquals(HybridPhase.WALLS, s.snapshot().phase)
        return s
    }

    /** Il soffitto, per ultimo: confermato, percorso, poi il riepilogo. */
    private fun ceilingAtEnd(s: HybridScan) {
        assertTrue(s.nextPhase())
        assertEquals(HybridPhase.CEILING, s.snapshot().phase)
        feed(s, 4, horizontal(2.6))
        assertTrue(s.confirmReference())
        feed(s, 4, horizontal(2.6, 0.3, 3.7, 0.3, 2.7, central = false))
        assertTrue(s.nextPhase())
        assertEquals(HybridPhase.REVIEW, s.snapshot().phase)
    }

    private fun acquire(s: HybridScan, side: Int, vararg sweep: Patch, from: Double = 0.05) {
        feed(s, 4, wall(side))
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status, "lato $side: ${s.snapshot().candidate}")
        assertTrue(s.confirmReference())
        feed(s, 4, wall(side, from, 0.95, 0.3, 2.4, central = false), *sweep)
    }

    @Test
    fun `senza profondità lo dice, non usa altro`() {
        val s = HybridScan(sketch, null)
        s.chooseStartSide(0)
        assertEquals(CandidateStatus.NO_DEPTH, s.snapshot().candidate.status)
        assertFalse(s.confirmReference())
        s.addDepth(frame()) // fotogramma senza punti validi
        assertEquals(CandidateStatus.NO_DEPTH, s.snapshot().candidate.status)
    }

    @Test
    fun `pavimento - riferimento solo se orizzontale e sotto la camera, poi la superficie cresce`() {
        val s = HybridScan(sketch, null)
        s.chooseStartSide(0)
        feed(s, 4, wall(0))
        assertEquals(CandidateStatus.WRONG_ORIENTATION, s.snapshot().candidate.status)
        feed(s, 6, horizontal(2.6))
        assertEquals(CandidateStatus.WRONG_SIDE, s.snapshot().candidate.status) // soffitto mentre si chiede il pavimento
        feed(s, 6, horizontal(0.0))
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status)
        assertTrue(s.confirmReference())
        val before = s.snapshot().floor.areaM2
        // Celle giudicate con almeno 20 punti (P0.18): qui circa 2 punti per cella e per immagine.
        feed(s, 12, horizontal(0.0, 0.3, 3.7, 0.3, 2.7, central = false))
        val f = s.snapshot().floor
        assertTrue(f.areaM2 > before, "${f.areaM2} > $before")
        assertEquals(0.0, f.heightY!!, 0.01)
    }

    @Test
    fun `parete - il mobile parallelo spostato non entra, il pannello a 4 cm resta dubbio, la retta è sul muro`() {
        val s = toWalls()
        // Il pannello copre il muro dietro di sé: lì si vede solo il pannello (tratto 0,02–0,15), il muro dal tratto 0,20 in poi.
        acquire(s, 0,
            wall(0, 0.55, 0.95, 0.3, 2.0, offset = 0.60, central = false), // armadio a 60 cm: oltre la tolleranza, mai dentro
            wall(0, 0.02, 0.15, 0.3, 1.0, offset = 0.045, s = 0.005, central = false), // pannello a 4,5 cm: frammento dubbio
            from = 0.25,
        )
        val w = s.snapshot().walls[0]
        assertEquals(WallState.MEASURED, w.state)
        assertTrue(w.fragments.any { it.status == FragmentStatus.DOUBTFUL }, "${w.fragments}")
        assertEquals(0.0, w.point!!.z, 0.01) // il muro è z = 0
        assertTrue(abs(w.direction!!.z) < 0.01)
    }

    @Test
    fun `nuova parete proposta col cambio di direzione, mobili e superfici fuori schizzo segnalati`() {
        val s = toWalls()
        acquire(s, 0)
        assertEquals(1, s.targetSide)
        // Davanti al lato 0 c'è un mobile parallelo: per il lato 1 è a 90° dallo schizzo → non si considera affatto.
        feed(s, 6, wall(0, offset = 0.6))
        val m = s.snapshot().candidate
        assertEquals(CandidateStatus.OFF_SKETCH, m.status)
        assertNull(m.plane); assertNull(m.patch)
        // Una superficie in diagonale (45°): nemmeno questa si considera, né si può confermare (neanche "comunque").
        val diag = Patch(grid(40) { p, q -> doubleArrayOf(2.5 + p, 0.5 + 1.5 * q, 0.5 + p) }, true)
        feed(s, 6, diag)
        val c = s.snapshot().candidate
        assertEquals(CandidateStatus.OFF_SKETCH, c.status)
        assertEquals(45.0, c.deviationDeg!!, 2.0)
        assertNull(c.patch)
        assertFalse(s.confirmReference())
        assertFalse(s.confirmReference())
        // Il lato 1 (x = 4): compatibile con lo schizzo → proposto, disegnato, con l'angolo verso il lato 0 già acquisito.
        feed(s, 6, wall(1))
        val r = s.snapshot().candidate
        assertEquals(CandidateStatus.READY, r.status)
        assertEquals(1, r.side)
        assertNotNull(r.patch)
        assertEquals(0, r.adjacentSide)
        assertEquals(90.0, r.adjacentAngleDeg!!, 1.5)
        assertEquals(90.0, r.sketchAdjacentAngleDeg!!, 1e-6)
        // Il rettangolo disegnato sta sulla parete vera (x = 4) e copre il tratto inquadrato (y 0,5–2,0).
        assertTrue(r.patch!!.all { abs(it.x - 4.0) < 0.05 }, "${r.patch}")
        assertTrue(r.patch!!.minOf { it.y } < 0.7 && r.patch!!.maxOf { it.y } > 1.8)
    }

    @Test
    fun `se gli ultimi fotogrammi non vedono lo stesso piano si chiede di tenere fermo, mostrando comunque la superficie`() {
        val s = toWalls()
        // Fotogrammi alternati su due pareti diverse (il telefono si muove).
        repeat(3) { s.addDepth(frame(wall(0))); s.addDepth(frame(wall(0, offset = 0.3))) }
        val c = s.snapshot().candidate
        assertEquals(CandidateStatus.UNSTABLE, c.status, "$c")
        assertNotNull(c.patch)
        assertFalse(s.confirmReference())
        // Fermo sulla stessa parete: pronto.
        feed(s, 4, wall(0))
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status)
    }

    @Test
    fun `mobile davanti al muro con il muro visibile dietro - avviso e nessuna conferma, si conferma solo il muro libero`() {
        val s = toWalls()
        for (k in 0 until 4) {
            if (k == 1) {
                // Armadio davanti al lato 1 (60 cm), il muro si vede sopra (punti non centrali).
                feed(s, 6, wall(1, offset = 0.6, y0 = 0.3, y1 = 1.7), wall(1, y0 = 1.9, y1 = 2.5, central = false))
                assertEquals(CandidateStatus.SURFACE_BEHIND, s.snapshot().candidate.status)
                assertEquals(0.6, s.snapshot().candidate.offsetM!!, 0.05)
                assertFalse(s.confirmReference()) // nessuna forzatura
                assertEquals(WallState.MISSING, s.snapshot().walls[1].state)
                acquire(s, 1) // inquadrato il muro libero
            } else acquire(s, k)
        }
        val snap = s.snapshot()
        assertEquals(BoxState.COMPLETE, snap.box)
        assertTrue(snap.discrepancies.none { it.kind == DiscrepancyKind.UNCERTAIN_ASSOCIATION })
    }

    @Test
    fun `stanza completa - angoli dalle intersezioni, scatola chiusa, altezza confrontata con lo schizzo senza imporla`() {
        val s = toWalls(heightM = 2.7)
        for (k in 0 until 4) acquire(s, k)
        assertEquals(HybridPhase.WALLS, s.snapshot().phase) // tutte confermate: si resta per percorrerle
        assertNull(s.snapshot().targetSide)
        ceilingAtEnd(s)
        val snap = s.snapshot()
        assertEquals(BoxState.COMPLETE, snap.box)
        val truth = listOf(ArXZ(0.0, 0.0), ArXZ(4.0, 0.0), ArXZ(4.0, 3.0), ArXZ(0.0, 3.0))
        for (k in 0 until 4) assertTrue(snap.corners[k]!!.distanceTo(truth[k]) < 0.02, "angolo $k: ${snap.corners[k]}")
        assertEquals(2.6, snap.heightM!!, 0.02)
        val h = snap.discrepancies.first { it.kind == DiscrepancyKind.HEIGHT }
        assertEquals(2.7, h.expected!!, 1e-9)
        assertEquals(2.6, h.measured!!, 0.02)
    }

    @Test
    fun `schizzo diverso dai dati - la discrepanza si mostra, la geometria resta quella misurata`() {
        // Schizzo 4 × 2 m, stanza vera 4 × 3: i lati corti differiscono del 50%.
        val small = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 200.0), Vec2(0.0, 200.0)))
        val s = HybridScan(small, null)
        s.chooseStartSide(0)
        feed(s, 4, horizontal(0.0)); s.confirmReference(); s.nextPhase()
        for (k in 0 until 4) acquire(s, k)
        assertTrue(s.nextPhase())
        s.skipCeiling()
        val snap = s.snapshot()
        assertEquals(HybridPhase.REVIEW, snap.phase)
        assertTrue(snap.discrepancies.any { it.kind == DiscrepancyKind.LENGTH && it.sides == listOf(1) }, "${snap.discrepancies}")
        assertEquals(3.0, snap.corners[1]!!.distanceTo(snap.corners[2]!!), 0.02) // misurato, non lo schizzo
        assertNull(snap.heightM) // soffitto saltato: altezza non stimata, mai quella dello schizzo
    }

    @Test
    fun `rifare una parete la toglie e la scatola torna incompleta`() {
        val s = toWalls()
        for (k in 0 until 4) acquire(s, k)
        s.redoWall(2)
        val snap = s.snapshot()
        assertEquals(WallState.MISSING, snap.walls[2].state)
        assertEquals(BoxState.INCOMPLETE, snap.box)
        assertEquals(2, snap.targetSide)
        assertNull(snap.corners[2]); assertNull(snap.corners[3])
        assertNotNull(snap.corners[1])
        assertTrue(snap.discrepancies.any { it.kind == DiscrepancyKind.MISSING_WALL && it.sides == listOf(2) })
    }

    @Test
    fun `dopo un salto la raccolta continua, i fotogrammi con una superficie confermata spostata si scartano`() {
        val s = toWalls()
        acquire(s, 0)
        val before = s.snapshot().walls[0].fragments.sumOf { it.points }
        s.poseJump()
        assertTrue(s.snapshot().unverified)
        // Il telefono rivede la parete 0 ma spostata di 12 cm (il mondo è saltato): quei punti non si usano, e lo si dice.
        feed(s, 3, wall(0, offset = 0.12))
        val a = s.snapshot()
        assertTrue(a.unverified)
        assertEquals(3, a.discardedFrames)
        assertEquals(-0.12, a.lastDisplacementM!!, 0.02)
        assertEquals(before, a.walls[0].fragments.sumOf { it.points })
        // Niente blocchi: inquadrando il lato 1 (nessuna superficie confermata in vista) si raccoglie e si può confermare.
        feed(s, 4, wall(1))
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status)
        assertTrue(s.snapshot().uncheckedFrames >= 4)
        assertTrue(s.confirmReference())
        // Rivede la parete 0 al suo posto: posizione verificata, si torna normali.
        feed(s, 2, wall(0))
        assertFalse(s.snapshot().unverified)
        assertTrue(s.snapshot().walls[0].fragments.sumOf { it.points } > before)
        assertEquals(1, s.snapshot().jumps)
    }

    @Test
    fun `un salto prima di qualsiasi conferma non cambia niente`() {
        val s = HybridScan(sketch, null)
        s.chooseStartSide(0)
        s.poseJump()
        assertFalse(s.snapshot().unverified)
        feed(s, 4, horizontal(0.0))
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status)
    }

    @Test
    fun `dopo una perdita di posizione con il mondo ruotato, riconfermando due pareti tutto torna al suo posto`() {
        val s = toWalls()
        acquire(s, 0); acquire(s, 1)
        // ARCore riprende con il mondo ruotato di 30° e spostato.
        arWorld = Rigid(Math.toRadians(30.0), 0.5, 0.05, -0.3)
        s.poseJump(afterPause = true)
        val r0 = s.snapshot()
        assertEquals(2, r0.realignNeeded)
        assertEquals(1, r0.realignTarget) // l'ultima confermata
        assertFalse(s.confirmReference()) // durante il riallineamento non si confermano superfici nuove
        val before = r0.walls[1].fragments.sumOf { it.points }
        feed(s, 4, wall(1))
        assertEquals(before, s.snapshot().walls[1].fragments.sumOf { it.points }) // niente raccolta col mondo incerto
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status)
        assertTrue(s.confirmRealign())
        assertEquals(0, s.snapshot().realignTarget) // ora la parete non parallela
        feed(s, 4, wall(0))
        assertTrue(s.confirmRealign())
        val r1 = s.snapshot()
        assertEquals(0, r1.realignNeeded)
        assertEquals(30.0, Math.toDegrees(r1.world.yaw), 0.5)
        assertEquals(0.5, r1.world.tx, 0.03); assertEquals(-0.3, r1.world.tz, 0.03)
        // La parete del lato 2, vista nel mondo ruotato, ora passa il filtro dello schizzo e la stanza si chiude giusta.
        acquire(s, 2); acquire(s, 3)
        val snap = s.snapshot()
        assertEquals(BoxState.COMPLETE, snap.box)
        val truth = listOf(ArXZ(0.0, 0.0), ArXZ(4.0, 0.0), ArXZ(4.0, 3.0), ArXZ(0.0, 3.0))
        for (k in 0 until 4) assertTrue(snap.corners[k]!!.distanceTo(truth[k]) < 0.03, "angolo $k: ${snap.corners[k]}")
    }

    @Test
    fun `la conferma vale anche se un istante prima del tocco arriva un'immagine instabile`() {
        val s = toWalls()
        feed(s, 4, wall(0))
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status)
        s.addDepth(frame(wall(0, offset = 0.3))) // un'immagine su un'altra superficie: candidato instabile
        assertEquals(CandidateStatus.UNSTABLE, s.snapshot().candidate.status)
        assertTrue(s.confirmReference())
        assertEquals(WallState.COLLECTING, s.snapshot().walls[0].state.let { if (it == WallState.MEASURED) WallState.COLLECTING else it })
    }

    @Test
    fun `l'ultimo candidato pronto non vale dopo un salto né per la parete successiva del riallineamento`() {
        val s = toWalls()
        acquire(s, 0)
        // Pronta, poi un salto: il piano visto prima del salto non si può più confermare.
        feed(s, 4, wall(1))
        assertEquals(CandidateStatus.READY, s.snapshot().candidate.status)
        s.poseJump()
        assertFalse(s.confirmReference())
        acquire(s, 1)
        // Riallineamento: riconfermata la prima parete, un secondo tocco subito dopo (senza immagini nuove) non deve usare quel
        // piano per la parete successiva.
        arWorld = Rigid(Math.toRadians(20.0), 0.2, 0.0, 0.1)
        s.poseJump(afterPause = true)
        feed(s, 4, wall(1))
        assertTrue(s.confirmRealign())
        assertEquals(0, s.snapshot().realignTarget)
        assertFalse(s.confirmRealign())
        assertEquals(0, s.snapshot().realignTarget)
        assertEquals(listOf(1), s.snapshot().realignDone)
        feed(s, 4, wall(0))
        assertTrue(s.confirmRealign())
        assertEquals(20.0, Math.toDegrees(s.snapshot().world.yaw), 0.5)
    }

    @Test
    fun `le superfici viste si colorano a tratti - verdi quelli registrati come buoni, sul piano giusto`() {
        val s = toWalls()
        acquire(s, 0)
        val snap = s.snapshot()
        val cov = s.coverage()
        val floor = cov.filter { it.kind == SurfaceKind.FLOOR }
        val wall = cov.filter { it.kind == SurfaceKind.WALL }
        assertTrue(floor.isNotEmpty() && wall.isNotEmpty())
        // Pavimento: l'area verde è quella accettata dal motore; le celle di una riga sono unite.
        val greenFloor = floor.filter { it.status == FragmentStatus.ACCEPTED }
        val area = greenFloor.sumOf { q -> abs(q.corners[2].x - q.corners[0].x) * abs(q.corners[2].z - q.corners[0].z) }
        assertEquals(snap.floor.areaM2, area, 1e-6)
        assertTrue(greenFloor.size < snap.floor.cells.size / 5, "tratti ${greenFloor.size}, celle ${snap.floor.cells.size}")
        for (q in floor) for (p in q.corners) assertEquals(snap.floor.reference!!.cy, p.y, 1e-9)
        // Parete del lato 0 (z = 0, x da 0 a 4): i tratti stanno sul muro.
        assertTrue(wall.all { it.side == 0 })
        assertTrue(wall.any { it.status == FragmentStatus.ACCEPTED })
        for (q in wall) for (p in q.corners) {
            assertTrue(abs(p.z) < 0.05, "z ${p.z}")
            assertTrue(p.x > -0.15 && p.x < 4.15, "x ${p.x}")
        }
        // Anche il soffitto, fatto per ultimo, si colora.
        ceilingAtEnd(s)
        assertTrue(s.coverage().any { it.kind == SurfaceKind.CEILING })
    }

    @Test
    fun `ordine pavimento, pareti, soffitto - durante il soffitto le pareti non raccolgono e un salto non chiede il riallineamento`() {
        val s = toWalls()
        for (k in 0 until 4) acquire(s, k)
        assertTrue(s.nextPhase())
        assertEquals(HybridPhase.CEILING, s.snapshot().phase)
        val before = s.snapshot().walls.map { it.points }
        feed(s, 4, wall(0, 0.05, 0.95, 0.3, 2.4, central = false))
        assertEquals(before, s.snapshot().walls.map { it.points }) // pareti ferme
        s.poseJump(afterPause = true, rotationDeg = 40.0)
        assertNull(s.snapshot().realignTarget)
        feed(s, 4, horizontal(2.6))
        assertTrue(s.confirmReference())
        feed(s, 4, horizontal(2.6, 0.3, 3.7, 0.3, 2.7, central = false))
        assertTrue(s.nextPhase())
        val snap = s.snapshot()
        assertEquals(HybridPhase.REVIEW, snap.phase)
        assertEquals(BoxState.COMPLETE, snap.box)
        assertEquals(2.6, snap.heightM!!, 0.02)
        // Dal riepilogo, con tutte le pareti, si torna al soffitto.
        s.backToScan()
        assertEquals(HybridPhase.CEILING, s.snapshot().phase)
    }

    @Test
    fun `riallineamento - se la seconda parete riconfermata non torna con la prima si rifiuta e si ricomincia`() {
        val s = toWalls()
        acquire(s, 0); acquire(s, 1)
        arWorld = Rigid(Math.toRadians(30.0), 0.5, 0.0, -0.3)
        s.poseJump(afterPause = true)
        feed(s, 4, wall(1))
        assertTrue(s.confirmRealign())
        assertEquals(0, s.snapshot().realignTarget)
        // Al posto del lato 0 si inquadra il lato 2, quello opposto: rotazione diversa di 180°.
        feed(s, 4, wall(2))
        assertFalse(s.confirmRealign())
        val r = s.snapshot()
        assertTrue(r.realignMismatch)
        assertEquals(1, r.realignTarget) // si ricomincia dalla prima
        assertTrue(r.realignDone.isEmpty())
        assertEquals(0.0, r.world.yaw, 1e-9) // nessuna correzione applicata
        // Rifatto bene: lato 1 e lato 0.
        feed(s, 4, wall(1)); assertTrue(s.confirmRealign())
        feed(s, 4, wall(0)); assertTrue(s.confirmRealign())
        assertFalse(s.snapshot().realignMismatch)
        assertEquals(30.0, Math.toDegrees(s.snapshot().world.yaw), 0.5)
    }

    @Test
    fun `pavimento con una zona vista 4 cm più in alto - solo quella zona è dubbia, il resto resta accettato`() {
        // Come nelle prove del 2026-10-13 sera: la quota vista dal telefono cambia da un punto all'altro della stanza.
        val s = HybridScan(sketch, null)
        s.chooseStartSide(0)
        feed(s, 6, horizontal(0.0, 0.5, 1.8, 0.8, 2.2))
        assertTrue(s.confirmReference())
        // Rumore nuovo a ogni immagine (feed ripete gli stessi punti: qui la mediana di cella deve venire da misure diverse).
        repeat(15) { s.addDepth(frame(horizontal(0.0, 0.3, 2.0, 0.3, 2.7, central = false), horizontal(0.04, 2.0, 3.7, 0.3, 2.7, central = false))) }
        val f = s.snapshot().floor
        val accepted = f.fragments.filter { it.status == FragmentStatus.ACCEPTED }
        val doubtful = f.fragments.filter { it.status == FragmentStatus.DOUBTFUL }
        assertTrue(accepted.isNotEmpty() && accepted.all { it.centerX < 2.0 }, "$accepted")
        assertTrue(doubtful.any { it.centerX > 2.0 && abs(it.medianOffsetM - 0.04) < 0.01 }, "$doubtful")
        assertTrue(f.areaM2 in 3.5..4.5, "accettati ${f.areaM2} m²")
        assertEquals(0.0, f.heightY!!, 0.01)
    }

    @Test
    fun `rifare il riferimento del pavimento cancella quello vecchio e si riconferma`() {
        val s = HybridScan(sketch, null)
        s.chooseStartSide(0)
        feed(s, 6, horizontal(-0.04, 0.5, 1.8, 0.8, 2.2)) // come su un riflesso: 4 cm sotto
        assertTrue(s.confirmReference())
        s.redoHorizontal()
        assertNull(s.snapshot().floor.reference)
        assertEquals(HybridPhase.FLOOR, s.snapshot().phase)
        assertTrue(s.coverage().none { it.kind == SurfaceKind.FLOOR })
        feed(s, 6, horizontal(0.0, 0.5, 1.8, 0.8, 2.2))
        assertTrue(s.confirmReference())
        assertEquals(0.0, s.snapshot().floor.reference!!.cy, 0.01)
    }

    @Test
    fun `rifare la parete da riconfermare passa il riallineamento a un'altra parete`() {
        val s = toWalls()
        acquire(s, 0); acquire(s, 1)
        s.poseJump(afterPause = true)
        assertEquals(1, s.snapshot().realignTarget)
        s.redoWall(1)
        assertEquals(0, s.snapshot().realignTarget)
        s.redoWall(0)
        assertNull(s.snapshot().realignTarget) // nessuna parete acquisita: niente da riallineare
    }
}
