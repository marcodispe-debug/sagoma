package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Survey
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Muri di spessore diverso, forme libere e stanze da rilievo. */
class WallsTest {
    // 500 × 400 in mezzeria: muro 0 in alto, 1 a destra, 2 in basso, 3 a sinistra.
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))

    @Test
    fun `lo spessore di ogni muro cambia le misure interne`() {
        assertEquals(listOf(485.0, 385.0, 485.0, 385.0), room.interiorLengths().map { Math.round(it * 10) / 10.0 })
        // Muro di sinistra portante da 40 cm: la stanza perde 12,5 cm in larghezza.
        val r = room.copy(wallThicknesses = mapOf(3 to 40.0))
        val l = r.interiorLengths()
        assertEquals(472.5, l[0], 1e-6)
        assertEquals(385.0, l[3], 1e-6)
        assertEquals(472.5 * 385.0, r.interiorArea(), 1e-3)
        // Tutti i muri da 30 cm.
        assertEquals(470.0, room.copy(wallThickness = 30.0).interiorLengths()[0], 1e-6)
    }

    @Test
    fun `un angolo a metà muro divide il muro, le aperture restano sul loro tratto`() {
        val door = Opening(1, OpeningKind.Door, wallIndex = 0, position = 400.0, width = 80.0, height = 210.0)
        val window = Opening(2, OpeningKind.Window1, wallIndex = 2, position = 100.0, width = 100.0, height = 120.0)
        val r = room.copy(openings = listOf(door, window), wallThicknesses = mapOf(2 to 30.0)).splitWall(0)
        assertEquals(5, r.wallCount)
        assertEquals(Vec2(250.0, 0.0), r.points[1])
        // La porta a 400 cm dall'inizio sta nella seconda metà: muro 1, a 150 cm.
        assertEquals(1, r.opening(1)!!.wallIndex)
        assertEquals(150.0, r.opening(1)!!.position, 1e-9)
        // Il muro 2 è diventato il 3, con il suo spessore.
        assertEquals(3, r.opening(2)!!.wallIndex)
        assertEquals(30.0, r.thicknessOf(3), 1e-9)
        // Unendo di nuovo si torna alla stanza di prima (porta di nuovo a 400 cm).
        val back = r.removeCorner(1)!!
        assertEquals(room.points, back.points)
        assertEquals(0, back.opening(1)!!.wallIndex)
        assertEquals(400.0, back.opening(1)!!.position, 1e-9)
        assertEquals(30.0, back.thicknessOf(2), 1e-9)
        assertNull(Room(2, "T", RoomType.Altro, listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(0.0, 100.0))).removeCorner(1))
    }

    @Test
    fun `rilievo - un rettangolo dai lati e dalla diagonale`() {
        val pts = Survey.solve(listOf(400.0, 300.0, 400.0, 300.0), listOf(500.0))!!
        assertEquals(Vec2(0.0, 0.0), pts[0])
        assertEquals(400.0, pts[1].x, 1e-6)
        assertEquals(400.0, pts[2].x, 1e-6)
        assertEquals(300.0, pts[2].y, 1e-6)
        assertEquals(0.0, pts[3].x, 1e-6)
        assertEquals(300.0, pts[3].y, 1e-6)
    }

    @Test
    fun `rilievo - stanza fuori squadra e misure impossibili`() {
        // Diagonale più corta: l'angolo in B non è più a 90°.
        val pts = Survey.solve(listOf(400.0, 300.0, 400.0, 300.0), listOf(480.0))!!
        val lati = pts.indices.map { pts[it].distanceTo(pts[(it + 1) % pts.size]) }
        for ((a, b) in lati.zip(listOf(400.0, 300.0, 400.0, 300.0))) assertEquals(b, a, 1e-6)
        assertTrue(pts[2].x < 400.0)
        // Una diagonale più lunga della somma dei lati non chiude il triangolo.
        assertNull(Survey.solve(listOf(400.0, 300.0, 400.0, 300.0), listOf(900.0)))
        // Pentagono: due diagonali.
        assertEquals(2, Survey.diagonalsNeeded(5))
        assertTrue(Survey.solve(listOf(300.0, 200.0, 150.0, 200.0, 300.0), listOf(360.0, 330.0)) != null)
    }
}
