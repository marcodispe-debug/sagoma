package com.sagoma.planimetria.scan

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallScanTest {
    private fun xz(x: Double, z: Double) = ArXZ(x, z)

    private fun obs(key: Int, ax: Double, az: Double, bx: Double, bz: Double, y0: Double = -1.2, y1: Double = 0.9) =
        WallObservation(key, xz(ax, az), xz(bx, bz), y0, y1)

    /** Le quattro pareti di un rettangolo 4 × 3 m, ognuna accorciata di `hide` metri a ogni estremo (angoli nascosti dai mobili). */
    private fun rectangle(hide: Double = 0.0, firstKey: Int = 1) = listOf(
        obs(firstKey, 0.0 + hide, 0.0, 4.0 - hide, 0.0),
        obs(firstKey + 1, 4.0, 0.0 + hide, 4.0, 3.0 - hide),
        obs(firstKey + 2, 4.0 - hide, 3.0, 0.0 + hide, 3.0),
        obs(firstKey + 3, 0.0, 3.0 - hide, 0.0, 0.0 + hide),
    )

    private fun area(c: List<ArXZ>) = Polygon.area(c.map { Vec2(it.x, it.z) })

    private fun assertCorner(expected: ArXZ, corners: List<ArXZ>, tol: Double = 1e-6) =
        assertTrue(corners.any { it.distanceTo(expected) < tol }, "manca l'angolo $expected in $corners")

    private fun rotate(o: WallObservation, deg: Double, dx: Double, dz: Double): WallObservation {
        val r = deg * PI / 180
        fun rot(p: ArXZ) = ArXZ(p.x * cos(r) - p.z * sin(r) + dx, p.x * sin(r) + p.z * cos(r) + dz)
        return o.copy(a = rot(o.a), b = rot(o.b))
    }

    @Test
    fun `un piano che si aggiorna molte volte resta una parete`() {
        var scan = WallScan()
        // Lo stesso piano (stessa chiave) cresce da 1 a 4 metri, con un po' di rumore.
        for (k in 1..30) scan = scan.update(listOf(obs(7, 0.0, 0.001 * k, 1.0 + 0.1 * k, 0.001 * k)))
        val walls = scan.walls()
        assertEquals(1, walls.size)
        assertEquals(4.0, walls.single().length, 0.01)
        assertEquals(listOf(7), walls.single().keys)
    }

    @Test
    fun `piani diversi sulla stessa retta e vicini formano una parete`() {
        val scan = WallScan().update(listOf(obs(1, 0.0, 0.0, 1.5, 0.02), obs(2, 1.3, 0.01, 3.0, -0.02), obs(3, 3.2, 0.0, 4.0, 0.0)))
        val walls = scan.walls()
        assertEquals(1, walls.size)
        assertEquals(4.0, walls.single().length, 0.05)
        assertEquals(listOf(1, 2, 3), walls.single().keys)
    }

    @Test
    fun `pareti parallele distanti o separate da un vuoto grande restano pareti diverse`() {
        // Stessa direzione, 3 metri di distanza: pareti opposte.
        assertEquals(2, WallScan().update(listOf(obs(1, 0.0, 0.0, 4.0, 0.0), obs(2, 0.0, 3.0, 4.0, 3.0))).walls().size)
        // Sulla stessa retta ma con un vuoto di 2 metri (una porta larga o un mobile alto): due pareti.
        assertEquals(2, WallScan().update(listOf(obs(1, 0.0, 0.0, 1.0, 0.0), obs(2, 3.0, 0.0, 4.0, 0.0))).walls().size)
        // Angolo di 90°: non si fondono.
        assertEquals(2, WallScan().update(listOf(obs(1, 0.0, 0.0, 4.0, 0.0), obs(2, 4.0, 0.0, 4.0, 3.0))).walls().size)
    }

    @Test
    fun `i pezzi troppo corti non sono pareti`() {
        assertTrue(WallScan().update(listOf(obs(1, 0.0, 0.0, 0.3, 0.0))).walls().isEmpty())
    }

    @Test
    fun `parete con quote, direzione e conversione in centimetri`() {
        val w = WallScan().update(listOf(obs(1, 1.0, 2.0, 5.0, 2.0, y0 = -1.0, y1 = 1.3))).walls().single()
        assertEquals(4.0, w.length, 1e-9)
        assertEquals(0.0, w.headingDeg, 1e-9)
        // Con il pavimento a −1,2 m la parte rilevata va da 20 cm a 250 cm da terra.
        val s = w.toScanned(floorY = -1.2)
        assertEquals(Vec2(100.0, 200.0), s.a)
        assertEquals(Vec2(500.0, 200.0), s.b)
        assertEquals(400.0, s.lengthCm, 1e-6)
        assertEquals(20.0, s.bottomCm!!, 1e-6)
        assertEquals(250.0, s.topCm!!, 1e-6)
        assertNull(w.toScanned(null).bottomCm)
        // Una parete lungo z (verticale in pianta) ha direzione 90°.
        assertEquals(90.0, WallScan().update(listOf(obs(1, 0.0, 0.0, 0.0, 3.0))).walls().single().headingDeg, 1e-9)
    }

    @Test
    fun `quattro pareti intere ricostruiscono il rettangolo`() {
        val layout = WallOutline.layout(WallScan().update(rectangle()).walls())
        val closed = assertNotNull(layout.closed)
        assertEquals(4, closed.size)
        assertEquals(12.0, area(closed), 1e-6)
        for (c in listOf(xz(0.0, 0.0), xz(4.0, 0.0), xz(4.0, 3.0), xz(0.0, 3.0))) assertCorner(c, closed)
        assertTrue(layout.chains.isEmpty())
    }

    @Test
    fun `pareti nascoste negli angoli da mobili si prolungano fino all'angolo`() {
        // 60 cm di ogni parete vicino agli angoli non si vedono: il perimetro viene uguale.
        val layout = WallOutline.layout(WallScan().update(rectangle(hide = 0.6)).walls())
        val closed = assertNotNull(layout.closed)
        assertEquals(12.0, area(closed), 1e-6)
        for (c in listOf(xz(0.0, 0.0), xz(4.0, 0.0), xz(4.0, 3.0), xz(0.0, 3.0))) assertCorner(c, closed)
    }

    @Test
    fun `stanza a L resta a L, senza forzare un rettangolo`() {
        val l = listOf(
            obs(1, 0.0, 0.0, 5.0, 0.0), obs(2, 5.0, 0.0, 5.0, 2.0), obs(3, 5.0, 2.0, 2.5, 2.0),
            obs(4, 2.5, 2.0, 2.5, 4.0), obs(5, 2.5, 4.0, 0.0, 4.0), obs(6, 0.0, 4.0, 0.0, 0.0),
        )
        val closed = assertNotNull(WallOutline.layout(WallScan().update(l).walls()).closed)
        assertEquals(6, closed.size)
        assertEquals(15.0, area(closed), 1e-6)
        for (c in listOf(xz(0.0, 0.0), xz(5.0, 0.0), xz(5.0, 2.0), xz(2.5, 2.0), xz(2.5, 4.0), xz(0.0, 4.0))) assertCorner(c, closed)
    }

    @Test
    fun `stanza ruotata e spostata, con un po' di rumore, si ricostruisce lo stesso`() {
        // Rotazione di 37° e piccoli errori (3 cm, 1°): stessa area entro il 2 %.
        val noisy = rectangle(hide = 0.3).mapIndexed { i, o ->
            val r = (i + 1) * 0.5 * PI / 180
            rotate(o.copy(a = ArXZ(o.a.x, o.a.z + 0.03 * (i % 2)), b = ArXZ(o.b.x + 0.02, o.b.z)), 37.0 + sin(r), 2.0, -1.5)
        }
        val closed = assertNotNull(WallOutline.layout(WallScan().update(noisy).walls()).closed)
        assertEquals(4, closed.size)
        assertEquals(12.0, area(closed), 12.0 * 0.02)
    }

    @Test
    fun `tre pareti su quattro - serie aperta, da chiudere con un lato dritto`() {
        val three = rectangle().take(3)
        val layout = WallOutline.layout(WallScan().update(three).walls())
        assertNull(layout.closed)
        val chain = assertNotNull(layout.closableChain)
        assertEquals(4, chain.size) // estremo, due angoli, estremo
        assertCorner(xz(4.0, 0.0), chain)
        assertCorner(xz(4.0, 3.0), chain)
        // Chiudendo con un lato dritto si ottiene la stanza intera (3 pareti vere e una dritta tra gli estremi).
        val draft = WallOutline.toDraft(chain, floorY = -1.2)
        assertNotNull(draft.toScannedRoom())
        assertEquals(12.0, draft.areaM2, 1e-6)
    }

    @Test
    fun `una parete sola o due parallele non formano un perimetro`() {
        val one = WallOutline.layout(WallScan().update(listOf(obs(1, 0.0, 0.0, 4.0, 0.0))).walls())
        assertNull(one.closed); assertEquals(1, one.chains.size); assertEquals(2, one.chains.single().size); assertNull(one.closableChain)
        val two = WallOutline.layout(WallScan().update(listOf(obs(1, 0.0, 0.0, 4.0, 0.0), obs(2, 0.0, 3.0, 4.0, 3.0))).walls())
        assertNull(two.closed); assertEquals(2, two.chains.size)
        assertTrue(WallOutline.layout(emptyList()).chains.isEmpty())
    }

    @Test
    fun `un mobile contro un muro non rovina il perimetro`() {
        // Davanti di un armadio: un piano verticale corto e staccato dalle pareti.
        val withWardrobe = rectangle() + obs(9, 1.0, 1.0, 1.9, 1.0)
        val layout = WallOutline.layout(WallScan().update(withWardrobe).walls())
        val closed = assertNotNull(layout.closed)
        assertEquals(12.0, area(closed), 1e-6)
    }

    @Test
    fun `annulla l'ultima parete e cancella tutto`() {
        var scan = WallScan().update(rectangle())
        assertEquals(4, scan.walls().size)
        scan = scan.removeNewest()
        assertEquals(3, scan.walls().size)
        // ARCore continua ad aggiornare il piano scartato: non risorge.
        scan = scan.update(rectangle())
        assertEquals(3, scan.walls().size)
        assertTrue(scan.clear().walls().isEmpty())
        assertTrue(scan.clear().update(rectangle()).walls().isEmpty())
        assertTrue(WallScan().isEmpty)
        assertEquals(WallScan().removeNewest().walls(), emptyList())
    }

    @Test
    fun `dal perimetro delle pareti alla stanza di Sagoma`() {
        val closed = assertNotNull(WallOutline.layout(WallScan().update(rectangle(hide = 0.5)).walls()).closed)
        val room = WallOutline.toDraft(closed, floorY = -1.2).toScannedRoom()
        assertNotNull(room)
        assertEquals(120_000.0, Polygon.area(room.corners), 1.0)
        val interior = com.sagoma.planimetria.scan.ScanGeometry.alignedInterior(room.corners)
        assertEquals(400.0, Polygon.bounds(interior).width, 0.2)
        assertEquals(300.0, Polygon.bounds(interior).height, 0.2)
        assertTrue(abs(Polygon.signedArea(interior)) > 0)
    }
}
