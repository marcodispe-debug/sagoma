package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Su dati SINTETICI: "visto oltre l'angolo" confermato dai punti della nuvola e messa in squadra degli angoli. Verificano il
 * codice, non l'accuratezza reale (per quella: `:core:realDataQuality` e `docs/risultati-verifica-pareti.md`).
 */
class SketchSquaringTest {
    private fun world(vararg pts: Pair<Double, Double>): List<ArXZ> = pts.map { (x, z) ->
        val r = Math.toRadians(17.0)
        ArXZ(x * cos(r) - z * sin(r) + 1.3, x * sin(r) + z * cos(r) - 0.6)
    }

    private val rect = world(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0)
    private val lRoom = world(0.0 to 0.0, 5.0 to 0.0, 5.0 to 2.0, 3.0 to 2.0, 3.0 to 4.0, 0.0 to 4.0)
    private fun sketchCm(vararg pts: Pair<Double, Double>) = RoomSketch.fromPlanInterior(pts.map { (x, y) -> Vec2(x, y) })
    private val rectSketch = sketchCm(0.0 to 0.0, 410.0 to 0.0, 410.0 to 290.0, 0.0 to 290.0)
    private val lSketch = sketchCm(0.0 to 0.0, 490.0 to 0.0, 490.0 to 200.0, 300.0 to 200.0, 300.0 to 390.0, 0.0 to 390.0)

    private fun seen(room: List<ArXZ>, k: Int, from: Double = 0.15, to: Double = 0.85, support: List<Double>? = null): ConfirmedLine {
        val a = room[k]; val b = room[(k + 1) % room.size]
        val d = (b - a).normalized()
        return ConfirmedLine(a + (b - a) * from, a + (b - a) * to, ArXZ(-d.z, d.x), 0.005, 0.5, supportAlongM = support)
    }

    /** La parete ruotata di [deg] attorno al centro della parte vista (errore di orientamento della scansione). */
    private fun ConfirmedLine.tilted(deg: Double): ConfirmedLine {
        val r = Math.toRadians(deg)
        val mid = (a + b) * 0.5
        fun rot(p: ArXZ) = (p - mid).let { v -> mid + ArXZ(v.x * cos(r) - v.z * sin(r), v.x * sin(r) + v.z * cos(r)) }
        return copy(a = rot(a), b = rot(b))
    }

    private fun all(room: List<ArXZ>) = room.indices.associateWith { seen(room, it) }
    private fun beyond(g: SketchGeometry, side: Int) = g.blocking.any { it.code == IssueCode.OBSERVED_BEYOND_CORNER && it.sides == listOf(side) }

    // ---------- Visto oltre l'angolo: conta solo se lo confermano anche i punti ----------

    /** Parete 0 vista (dal poligono) dal 10% fino a 30 cm oltre l'angolo 1: tratto lungo 3,9 m, l'angolo 1 è a 3,6 m. */
    private fun overshooting(support: List<Double>?, reversed: Boolean = false): ConfirmedLine {
        val l = seen(rect, 0, 0.1, 1.075, support)
        if (!reversed) return l
        val len = l.observedLengthM
        return l.copy(a = l.b, b = l.a, supportAlongM = support?.map { len - it }?.sorted())
    }

    /** [perMeter] punti per metro, regolari, sul tratto [from]–[to] (metri lungo la parete dal suo inizio visto). */
    private fun points(from: Double, to: Double, perMeter: Double) = ((to - from) * perMeter).toInt().let { n -> List(n) { from + (to - from) * (it + 0.5) / n } }

    /** Parete densa (100 punti/m, come le pareti piastrellate di scan4: 140–405) solo dentro la stanza (angolo 1 a 3,6 m). */
    private val wallOnly = points(0.0, 3.6, 100.0)

    private fun solveWith(l: ConfirmedLine) = SketchSolver.solve(SketchGeometryInput(rectSketch, all(rect) + (0 to l)))

    @Test
    fun `poligono oltre l'angolo senza punti oltre non blocca`() {
        for (rev in listOf(false, true)) assertFalse(beyond(solveWith(overshooting(wallOnly, rev)), 0), "invertita=$rev")
    }

    @Test
    fun `poligono e punti oltre l'angolo alla densita della parete bloccano ancora`() {
        val over = wallOnly + points(3.6, 3.9, 100.0)
        for (rev in listOf(false, true)) assertTrue(beyond(solveWith(overshooting(over, rev)), 0), "invertita=$rev")
        // Anche visto peggio vicino all'angolo (metà densità): è una parete vera, blocca.
        assertTrue(beyond(solveWith(overshooting(wallOnly + points(3.6, 3.9, 50.0))), 0))
    }

    @Test
    fun `il blocco dice quale angolo e quale altra parete, con l'angolo misurato e quello dello schizzo`() {
        // Parete 0 oltre l'angolo 1 (fine del lato 0): l'altro lato di quell'angolo è il lato 1.
        val end = solveWith(overshooting(wallOnly + points(3.6, 3.9, 100.0))).blocking.single { it.code == IssueCode.OBSERVED_BEYOND_CORNER }
        assertEquals(1.0, end.values["angolo"])
        assertEquals(1.0, end.values["altroLato"])
        assertEquals(90.0, end.values.getValue("angoloDeg"), 1e-6)
        assertEquals(90.0, end.values.getValue("schizzoDeg"), 1e-6)
        // Parete 0 vista oltre l'angolo 0 (inizio del lato 0): l'altro lato è il lato 3.
        val start = SketchSolver.solve(SketchGeometryInput(rectSketch, all(rect) + (0 to seen(rect, 0, -0.075, 0.9))))
            .blocking.single { it.code == IssueCode.OBSERVED_BEYOND_CORNER }
        assertEquals(0.0, start.values["angolo"])
        assertEquals(3.0, start.values["altroLato"])
    }

    @Test
    fun `parete poco densa nel dubbio blocca come prima`() {
        // 8 punti/m (parete liscia): oltre l'angolo se ne attendono 2,4, troppo pochi per dire che è un artefatto.
        val sparse = points(0.0, 3.6, 8.0)
        assertTrue(beyond(solveWith(overshooting(sparse)), 0))
        assertTrue(beyond(solveWith(overshooting(sparse + points(3.6, 3.9, 8.0))), 0))
    }

    @Test
    fun `stesso esito con il doppio dei punti (scansione piu lunga)`() {
        for (k in listOf(1.0, 2.0, 4.0)) {
            assertFalse(beyond(solveWith(overshooting(points(0.0, 3.6, 100.0 * k))), 0), "×$k")
            assertTrue(beyond(solveWith(overshooting(points(0.0, 3.6, 100.0 * k) + points(3.6, 3.9, 100.0 * k))), 0), "×$k")
        }
    }

    @Test
    fun `senza punti o con pochi punti resta il controllo di prima`() {
        assertTrue(beyond(solveWith(overshooting(null)), 0))
        assertTrue(beyond(solveWith(overshooting(List(9) { 0.5 + it * 0.1 })), 0))
    }

    @Test
    fun `i punti non aggiungono blocchi che il poligono non da`() {
        // Punti oltre l'angolo ma poligono entro l'angolo: nessun blocco (conta solo ciò che confermano entrambi).
        val l = seen(rect, 0, 0.1, 0.9, wallOnly + points(3.9, 4.1, 100.0))
        assertFalse(beyond(SketchSolver.solve(SketchGeometryInput(rectSketch, all(rect) + (0 to l))), 0))
    }

    // ---------- Messa in squadra ----------

    private val tilts = listOf(1.5, -1.0, 2.0, -1.5)

    private fun offerFor(sketch: RoomSketch, lines: Map<Int, ConfirmedLine>): SketchSquaring.Offer? {
        val input = SketchGeometryInput(sketch, lines)
        return SketchSquaring.offer(input, SketchSolver.solve(input))
    }

    @Test
    fun `la proposta dice di quanto cambia la pianta e la squadra porta gli angoli a 90`() {
        val lines = rect.indices.associateWith { seen(rect, it).tilted(tilts[it]) }
        val g = SketchSolver.solve(SketchGeometryInput(rectSketch, lines))
        val o = assertNotNull(offerFor(rectSketch, lines))
        assertEquals(g.cornerResults.maxOf { kotlin.math.abs(it.interiorAngleDeg - 90.0) }, o.maxAngleDeviationDeg, 1e-9)
        for (c in o.squared.cornerResults) assertEquals(90.0, c.interiorAngleDeg, 1e-9)
        // Lo spostamento è quello tra gli angoli delle due piante, lo stesso angolo per angolo.
        assertEquals(g.corners!!.indices.maxOf { g.corners!![it].distanceTo(o.squared.corners!![it]) }, o.maxShiftM, 1e-12)
        assertTrue(o.maxShiftM > 0.01)
        // Le posizioni misurate restano: ogni parete passa ancora per il centro della sua parte vista.
        for ((k, l) in SketchSquaring.squaredLines(lines)) {
            val orig = lines.getValue(k)
            assertEquals(0.0, ((orig.a + orig.b) * 0.5).distanceTo((l.a + l.b) * 0.5), 1e-12)
            assertEquals(orig.observedLengthM, l.observedLengthM, 1e-12)
        }
    }

    @Test
    fun `nessun limite in gradi - anche uno scarto grande si propone con il suo spostamento`() {
        val big = rect.indices.associateWith { seen(rect, it).tilted(if (it == 1) 6.0 else 0.0) }
        val o = assertNotNull(offerFor(rectSketch, big))
        assertTrue(o.maxAngleDeviationDeg > 5.0)
        assertTrue(o.maxShiftM > 0.1, "${o.maxShiftM}")
    }

    @Test
    fun `lo stesso scarto pesa di piu in una stanza piu grande`() {
        val small = world(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0)
        val large = world(0.0 to 0.0, 8.0 to 0.0, 8.0 to 6.0, 0.0 to 6.0)
        val oSmall = assertNotNull(offerFor(rectSketch, small.indices.associateWith { seen(small, it).tilted(tilts[it]) }))
        val oLarge = assertNotNull(offerFor(rectSketch, large.indices.associateWith { seen(large, it).tilted(tilts[it]) }))
        assertEquals(oSmall.maxAngleDeviationDeg, oLarge.maxAngleDeviationDeg, 1e-6)
        assertTrue(oLarge.maxShiftM > 3 * oSmall.maxShiftM, "${oSmall.maxShiftM} ${oLarge.maxShiftM}")
    }

    @Test
    fun `piante uguali al centimetro nessuna domanda`() {
        assertNull(offerFor(rectSketch, all(rect)))
        // 0,05° su una stanza di 4 × 3 m: gli angoli si spostano di pochi millimetri.
        assertNull(offerFor(rectSketch, rect.indices.associateWith { seen(rect, it).tilted(if (it == 0) 0.05 else 0.0) }))
    }

    @Test
    fun `schizzo non ad angoli retti o una sola parete nessuna proposta`() {
        val trapezio = sketchCm(0.0 to 0.0, 400.0 to 0.0, 350.0 to 300.0, 50.0 to 300.0)
        val trap = world(0.0 to 0.0, 4.0 to 0.0, 3.5 to 3.0, 0.5 to 3.0)
        assertNull(offerFor(trapezio, trap.indices.associateWith { seen(trap, it).tilted(1.0) }))
        assertNull(offerFor(rectSketch, mapOf(0 to seen(rect, 0).tilted(2.0))))
    }

    @Test
    fun `stanza a L con l'angolo rientrante a 270`() {
        val t = listOf(1.0, -2.0, 1.5, -1.0, 2.0, -1.5)
        val o = assertNotNull(offerFor(lSketch, lRoom.indices.associateWith { seen(lRoom, it).tilted(t[it]) }))
        val angles = o.squared.cornerResults.map { Math.round(it.interiorAngleDeg * 1e6) / 1e6 }
        assertEquals(listOf(90.0, 90.0, 90.0, 270.0, 90.0, 90.0), angles)
    }
}
