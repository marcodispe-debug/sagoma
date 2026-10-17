package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
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

/** Dal risultato della sessione alla STESSA stanza: id, muri, aperture in proporzione, posizione nella pianta. */
class SketchApplyTest {
    /** Schizzo 4 × 3 m (filo interno) con una porta sul muro 0 e una finestra sul muro 1. */
    private val sketchRoom: Room = run {
        val pts = Polygon.fromInterior(RoomFactory.rectangle(400.0, 300.0), Room.WALL_THICKNESS).map { it + Vec2(1000.0, 500.0) }
        Room(
            id = 7, name = "Camera", type = RoomType.Altro, points = pts,
            openings = listOf(
                Opening.default(1, OpeningKind.Door, 0, 100.0),
                Opening.default(2, OpeningKind.Window2, 1, 150.0),
            ),
        )
    }

    /** Angoli misurati: la stanza vera 4,5 × 3 m (filo interno), nello stesso ordine dei muri, ruotata e spostata nel mondo AR. */
    private fun measured(widthCm: Double = 450.0, depthCm: Double = 300.0, rotDeg: Double = 31.0): List<ArXZ> {
        val r = Math.toRadians(rotDeg)
        return RoomFactory.rectangle(widthCm, depthCm).map { p ->
            val x = p.x / 100; val z = p.y / 100
            ArXZ(x * cos(r) - z * sin(r) + 2.0, x * sin(r) + z * cos(r) - 3.0)
        }
    }

    @Test
    fun `stessa stanza, stessi muri, misure nuove, nello stesso punto della pianta`() {
        val p = SketchApply.preview(sketchRoom, measured())
        val room = assertNotNull(p.room)
        assertTrue(p.canApply)
        assertEquals(sketchRoom.id, room.id)
        assertEquals(sketchRoom.wallCount, room.wallCount)
        assertEquals(sketchRoom.name, room.name)
        val lengths = room.interiorLengths()
        assertEquals(listOf(450.0, 300.0, 450.0, 300.0), lengths.map { Math.round(it * 1000) / 1000.0 })
        // La forma misurata si sovrappone allo schizzo: stesso baricentro e stesso orientamento (lato 0 orizzontale).
        val c0 = sketchRoom.interior().let { it.reduce { a, b -> a + b } * (1.0 / it.size) }
        val c1 = room.interior().let { it.reduce { a, b -> a + b } * (1.0 / it.size) }
        assertEquals(0.0, c0.distanceTo(c1), 1e-6)
        val i = room.interior()
        assertEquals(0.0, i[1].y - i[0].y, 1e-6)
    }

    @Test
    fun `porte e finestre conservano larghezza e tipo, la posizione segue la lunghezza`() {
        val p = SketchApply.preview(sketchRoom, measured())
        val room = assertNotNull(p.room)
        for (o in sketchRoom.openings) {
            val n = room.openings.single { it.id == o.id }
            assertEquals(o.width, n.width); assertEquals(o.height, n.height); assertEquals(o.sillHeight, n.sillHeight)
            assertEquals(o.kind, n.kind); assertEquals(o.wallIndex, n.wallIndex); assertEquals(o.hingeLeft, n.hingeLeft)
            val expected = o.position * room.wallLength(o.wallIndex) / sketchRoom.wallLength(o.wallIndex)
            assertEquals(expected, n.position, 1e-9)
            assertEquals(expected, p.openings.single { it.openingId == o.id }.newPosition, 1e-9)
        }
        // Correzione dell'utente nella revisione: vince sulla proporzione.
        val fixed = assertNotNull(SketchApply.preview(sketchRoom, measured(), mapOf(1L to 60.0)).room)
        assertEquals(60.0, fixed.openings.single { it.id == 1L }.position, 1e-9)
    }

    @Test
    fun `un'apertura piu larga del muro nuovo blocca senza restringerla`() {
        // Muro 1 lungo 1 m (+ spessore) con una finestra da 1,2 m.
        val p = SketchApply.preview(sketchRoom, measured(depthCm = 90.0))
        assertFalse(p.canApply)
        val ch = p.openings.single { it.openingId == 2L }
        assertFalse(ch.fits)
        assertEquals(120.0, ch.width)
        assertEquals(120.0, assertNotNull(p.room).openings.single { it.id == 2L }.width) // non ristretta
    }

    @Test
    fun `la larghezza si corregge nella revisione ma mai oltre il muro`() {
        val narrow = measured(depthCm = 90.0) // muro 1 di 90 cm (filo interno) con una finestra da 120 cm
        val ok = SketchApply.preview(sketchRoom, narrow, widthOverrides = mapOf(2L to 80.0))
        assertTrue(ok.canApply)
        assertEquals(80.0, assertNotNull(ok.room).openings.single { it.id == 2L }.width)
        assertEquals(120.0, ok.openings.single { it.openingId == 2L }.width) // la larghezza di prima resta visibile
        // Più larga del muro: la correzione non vale, resta 120 cm e blocca.
        val tooWide = SketchApply.preview(sketchRoom, narrow, widthOverrides = mapOf(2L to 500.0))
        assertFalse(tooWide.canApply)
        assertEquals(120.0, tooWide.openings.single { it.openingId == 2L }.newWidth)
        // Altezza e davanzale non cambiano mai.
        val w = assertNotNull(ok.room).openings.single { it.id == 2L }
        val o = sketchRoom.openings.single { it.id == 2L }
        assertEquals(o.height, w.height); assertEquals(o.sillHeight, w.sillHeight)
    }

    @Test
    fun `angoli non validi non producono nessuna stanza`() {
        assertNull(SketchApply.preview(sketchRoom, measured().take(3)).room)
        val bow = measured().let { listOf(it[0], it[2], it[1], it[3]) }
        assertNull(SketchApply.preview(sketchRoom, bow).room)
        assertNull(SketchApply.preview(sketchRoom, measured().mapIndexed { k, c -> if (k == 2) ArXZ(Double.NaN, c.z) else c }).room)
    }

    @Test
    fun `schizzo e aperture dalla stanza`() {
        val s = SketchApply.sketchOf(sketchRoom)
        assertEquals(4, s.sideCount)
        assertEquals(4.0, s.sideLength(0), 1e-9)
        assertEquals(listOf(SketchOpening(0, OpeningMark.DOOR), SketchOpening(1, OpeningMark.WINDOW)), SketchApply.openingsOf(sketchRoom))
    }
}
