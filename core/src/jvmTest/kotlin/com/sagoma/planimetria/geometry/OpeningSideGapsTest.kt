package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Distanze di porte e finestre dagli angoli del loro muro, quelle che si vedono mentre le si trascina. */
class OpeningSideGapsTest {
    private val window = Opening.default(1, OpeningKind.Window1, 0, 100.0)
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).copy(wallThickness = 15.0, openings = listOf(window))

    @Test
    fun `misure sul filo interno, da angolo a bordo`() {
        val (left, right) = Openings.sideGaps(room, window)
        // Finestra da 80 col centro a 100 dalla mezzeria dell'angolo: il bordo è a 60, il filo interno comincia a 7,5.
        assertEquals(52.5, left.length, 1e-6)
        // Le due distanze più la finestra fanno il muro misurato dentro.
        assertEquals(room.interiorLengths()[0], left.length + right.length + window.width, 1e-6)
        assertEquals(Vec2(7.5, 7.5), left.from)
        assertEquals(Vec2(60.0, 7.5), left.to)
        // Il lato della stanza: un poco dentro dalla quota si è nella stanza.
        assertTrue(Polygon.contains(room.points, (left.from + left.to) / 2.0 + left.inward * 20.0))
    }

    @Test
    fun `stanza disegnata al contrario - la quota sta comunque dentro`() {
        val r = room.copy(points = room.points.reversed())
        val o = window.copy(wallIndex = 1)
        val (a, b) = Openings.sideGaps(r, o)
        assertEquals(r.interiorLengths()[1], a.length + b.length + o.width, 1e-6)
        assertTrue(Polygon.contains(r.points, (a.from + a.to) / 2.0 + a.inward * 20.0))
    }

    @Test
    fun `finestra contro l'angolo - zero`() {
        val o = window.copy(position = 0.0)
        assertEquals(0.0, Openings.sideGaps(room.copy(openings = listOf(o)), o).first().length, 1e-6)
    }

    @Test
    fun `muro singolo - fino alle sue estremita`() {
        val w = FreeWall(5, Vec2(0.0, 0.0), Vec2(300.0, 0.0), openings = listOf(window))
        val (a, b) = Openings.sideGaps(w.host, window)
        assertEquals(60.0, a.length, 1e-6)
        assertEquals(160.0, b.length, 1e-6)
    }
}
