package com.sagoma.planimetria.scan

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanGeometryTest {
    /** Punto AR sul pavimento (y = quota del pavimento, qui −1,3 m: non deve contare). */
    private fun ar(x: Double, z: Double) = ArPoint(x, -1.3, z)

    private fun draft(vararg xz: Pair<Double, Double>) =
        xz.fold(ScanDraft()) { d, (x, z) -> d.add(ar(x, z)) }

    @Test
    fun `metri in centimetri`() {
        val p = ScanCoordinates.toPlan(ArPoint(1.5, 0.7, 2.0))
        assertEquals(150.0, p.x, 1e-9)
        assertEquals(200.0, p.y, 1e-9)
    }

    @Test
    fun `assi - x di AR e x della pianta, z di AR e y della pianta, la quota non entra`() {
        // Solo x: si sposta solo la x della pianta.
        val a = ScanCoordinates.toPlan(ArPoint(0.0, 0.0, 0.0))
        val b = ScanCoordinates.toPlan(ArPoint(1.0, 0.0, 0.0))
        assertEquals(100.0, b.x - a.x, 1e-9); assertEquals(0.0, b.y - a.y, 1e-9)
        // Solo z: si sposta solo la y della pianta, nello stesso verso (z cresce = y cresce, verso il basso).
        val c = ScanCoordinates.toPlan(ArPoint(0.0, 0.0, 1.0))
        assertEquals(0.0, c.x - a.x, 1e-9); assertEquals(100.0, c.y - a.y, 1e-9)
        // Solo la quota: nessun effetto sulla pianta.
        val d = ScanCoordinates.toPlan(ArPoint(0.0, 5.0, 0.0))
        assertEquals(a, d)
    }

    @Test
    fun `la trasformazione non specchia - un giro orario visto dall'alto resta orario`() {
        // Dall'alto con x a destra e z in basso: (0,0) → (4,0) → (4,3) → (0,3) è un giro orario sullo schermo.
        val corners = draft(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0).corners
        assertTrue(Polygon.signedArea(corners) > 0, "positiva = oraria su schermo")
        // E il giro opposto è antiorario.
        assertTrue(Polygon.signedArea(draft(0.0 to 0.0, 0.0 to 3.0, 4.0 to 3.0, 4.0 to 0.0).corners) < 0)
    }

    @Test
    fun `distanza tra punti consecutivi, in pianta`() {
        val d = draft(0.0 to 0.0, 3.0 to 0.0, 3.0 to 4.0)
        assertEquals(listOf(300.0, 400.0), d.edgeLengthsCm.map { Math.round(it).toDouble() })
        // La differenza di quota non conta.
        assertEquals(500.0, ScanCoordinates.distanceCm(ArPoint(0.0, 0.0, 0.0), ArPoint(3.0, 9.0, 4.0)), 1e-9)
    }

    @Test
    fun `chiusura del poligono, in tutti i modi`() {
        val open = draft(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0)
        assertFalse(open.closed)
        assertEquals(3, open.edgeLengthsCm.size)
        // Chiudere aggiunge il lato di ritorno.
        val closed = open.close()
        assertTrue(closed.closed)
        assertEquals(4, closed.edgeLengthsCm.size)
        assertEquals(300.0, closed.edgeLengthsCm.last(), 1e-6)
        assertEquals(1400.0, closed.perimeterCm, 1e-6)
        assertEquals(12.0, closed.areaM2, 1e-9)
        // Con meno di tre punti non si chiude.
        assertFalse(draft(0.0 to 0.0, 1.0 to 0.0).close().closed)
        assertFalse(draft(0.0 to 0.0, 1.0 to 0.0).canClose)
        // Toccando vicino al primo punto si chiude senza aggiungere un punto.
        val auto = open.add(ar(0.1, 0.1))
        assertTrue(auto.closed)
        assertEquals(4, auto.points.size)
        // Da chiuso non si aggiungono punti.
        assertEquals(closed, closed.add(ar(9.0, 9.0)))
    }

    @Test
    fun `annulla ultimo punto e cancella`() {
        val d = draft(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0)
        assertEquals(2, d.undoLast().points.size)
        // Da chiuso, annullare riapre il poligono senza perdere punti.
        val reopened = d.close().undoLast()
        assertFalse(reopened.closed)
        assertEquals(3, reopened.points.size)
        assertEquals(ScanDraft(), d.clear())
        assertEquals(ScanDraft(), ScanDraft().undoLast())
        // Un doppio tocco (stesso punto) non aggiunge niente.
        assertEquals(d, d.add(ar(4.0, 3.01)))
    }

    @Test
    fun `rettangolo noto 4 x 3 metri`() {
        val room = draft(1.0 to 2.0, 5.0 to 2.0, 5.0 to 5.0, 1.0 to 5.0).close().toScannedRoom()
        assertNotNull(room)
        val c = room.corners
        assertEquals(listOf(Vec2(100.0, 200.0), Vec2(500.0, 200.0), Vec2(500.0, 500.0), Vec2(100.0, 500.0)), c)
        assertEquals(120_000.0, Polygon.area(c), 1e-6)
        // Per Sagoma: stesso rettangolo con l'angolo in alto a sinistra in (0, 0), lato lungo orizzontale.
        val interior = ScanGeometry.alignedInterior(c)
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)), interior)
    }

    @Test
    fun `stanza ruotata e in senso antiorario - si raddrizza senza cambiare le misure`() {
        // Rettangolo 5 × 3,5 m ruotato di 37° nel mondo AR, percorso in senso antiorario.
        val ang = 37 * PI / 180
        fun rot(x: Double, z: Double) = ar(x * cos(ang) - z * sin(ang) + 2.0, x * sin(ang) + z * cos(ang) - 1.0)
        val d = ScanDraft(listOf(rot(0.0, 0.0), rot(0.0, 3.5), rot(5.0, 3.5), rot(5.0, 0.0)), closed = true)
        assertTrue(Polygon.signedArea(d.corners) < 0, "antioraria")
        val interior = ScanGeometry.alignedInterior(d.corners)
        assertTrue(Polygon.signedArea(interior) > 0, "oraria per Sagoma")
        // Lati orizzontali e verticali, misure intatte.
        for (i in interior.indices) {
            val a = interior[i]; val b = interior[(i + 1) % interior.size]
            assertTrue(a.x == b.x || a.y == b.y, "lato $i non allineato: $a → $b")
        }
        assertEquals(500.0 * 350.0, Polygon.area(interior), 5.0)
        val b = Polygon.bounds(interior)
        assertEquals(0.0, b.minX, 0.0); assertEquals(0.0, b.minY, 0.0)
        assertEquals(500.0, b.width, 0.2); assertEquals(350.0, b.height, 0.2)
    }

    @Test
    fun `stanza a L con sei angoli`() {
        val d = draft(0.0 to 0.0, 5.0 to 0.0, 5.0 to 2.0, 2.5 to 2.0, 2.5 to 4.0, 0.0 to 4.0).close()
        val room = d.toScannedRoom()
        assertNotNull(room)
        assertEquals(6, room.corners.size)
        assertEquals(6, d.edgeLengthsCm.size)
        assertEquals(5.0 * 2.0 + 2.5 * 2.0, d.areaM2, 1e-9)
        val interior = ScanGeometry.alignedInterior(room.corners)
        assertEquals(6, interior.size)
        assertEquals(d.areaM2 * 10_000.0, Polygon.area(interior), 1.0)
    }

    @Test
    fun `quadrilatero qualsiasi fuori squadra con 4 angoli`() {
        val d = draft(0.0 to 0.0, 4.2 to 0.3, 3.9 to 3.1, -0.2 to 2.8).close()
        val room = d.toScannedRoom()
        assertNotNull(room)
        val interior = ScanGeometry.alignedInterior(room.corners)
        // Le lunghezze dei lati si conservano.
        val before = d.edgeLengthsCm
        val after = interior.indices.map { interior[it].distanceTo(interior[(it + 1) % interior.size]) }
        before.indices.forEach { assertEquals(before[it], after[it], 0.2) }
    }

    @Test
    fun `perimetro che si incrocia o troppo piccolo non diventa una stanza`() {
        // A "farfalla": i lati si attraversano.
        assertNull(draft(0.0 to 0.0, 4.0 to 3.0, 4.0 to 0.0, 0.0 to 3.0).close().toScannedRoom())
        // Non chiuso.
        assertNull(draft(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0).toScannedRoom())
        // Troppo piccolo (20 × 20 cm).
        assertNull(draft(0.0 to 0.0, 0.2 to 0.0, 0.2 to 0.2, 0.0 to 0.2).close().toScannedRoom())
        assertTrue(ScanGeometry.isSimple(draft(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0).corners))
    }
}
