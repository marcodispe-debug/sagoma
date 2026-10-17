package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.SnapEngine
import com.sagoma.planimetria.geometry.SnapKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** I punti del piano di sotto sono riferimenti di aggancio per le stanze di sopra, senza mai modificarlo. */
class SnapBelowTest {
    // Piano 0: stanza 400 × 300 con l'angolo in (100, 50).
    private val lower = FloorPlan(listOf(Room(1, "Sotto", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(100.0, 50.0) })))
    // Piano 1: una stanza più piccola, spostata.
    private val upper = FloorPlan(listOf(Room(1, "Sopra", RoomType.Camera, RoomFactory.rectangle(200.0, 150.0).map { it + Vec2(700.0, 700.0) })))
    private val tol = 10.0

    @Test
    fun `al piano 0 non ci sono riferimenti in piu e l'aggancio normale funziona`() {
        val own = SnapEngine.targets(lower)
        val same = SnapEngine.targetsWithBelow(lower, null)
        assertEquals(own.endpoints, same.endpoints)
        assertEquals(own.midpoints, same.midpoints)
        assertEquals(own.centers, same.centers)
        val r = SnapEngine.snap(Vec2(103.0, 52.0), same, tol)
        assertEquals(Vec2(100.0, 50.0), r.point)
        assertEquals(SnapKind.Endpoint, r.guides.single().kind)
    }

    @Test
    fun `al piano 1 si espongono gli angoli, i punti medi dei muri e gli allineamenti del piano 0`() {
        val t = SnapEngine.targetsWithBelow(upper, lower)
        val lowerCorners = lower.rooms.single().points
        assertTrue(t.endpoints.containsAll(lowerCorners))
        assertTrue(t.endpoints.containsAll(upper.rooms.single().points)) // quelli del piano corrente restano
        assertTrue(t.midpoints.contains(Vec2(300.0, 50.0))) // punto medio del muro in alto di sotto
        // Punto lontano da tutto tranne che dall'asse x di un angolo di sotto: si allinea (guida verticale), senza saltare sul punto.
        val aligned = SnapEngine.snap(Vec2(503.0, 900.0), t, tol)
        assertEquals(500.0, aligned.point.x, 1e-9)
        assertEquals(900.0, aligned.point.y, 1e-9)
        assertEquals(SnapKind.AlignVertical, aligned.guides.single().kind)
    }

    @Test
    fun `lo scatto sull'angolo di sotto vale entro la tolleranza e non oltre`() {
        val t = SnapEngine.targetsWithBelow(upper, lower)
        val near = SnapEngine.snap(Vec2(496.0, 353.0), t, tol) // 5 cm dall'angolo (500, 350)
        assertEquals(Vec2(500.0, 350.0), near.point)
        assertEquals(SnapKind.Endpoint, near.guides.single().kind)
        val far = SnapEngine.snap(Vec2(515.0, 365.0), t, tol) // 21 cm: nessun aggancio a punto (e nessun asse entro 10 cm)
        assertFalse(far.snapped)
        assertEquals(Vec2(515.0, 365.0), far.point)
        // Senza il piano di sotto lo stesso punto non scatta.
        assertFalse(SnapEngine.snap(Vec2(496.0, 353.0), SnapEngine.targets(upper), tol).snapped)
    }

    @Test
    fun `i riferimenti non modificano il piano di sotto`() {
        val before = lower.copy()
        val t = SnapEngine.targetsWithBelow(upper, lower)
        SnapEngine.snap(Vec2(496.0, 353.0), t, tol)
        SnapEngine.snapDrawing(Vec2(104.0, 52.0), null, null, t, emptyList(), tol)
        assertEquals(before, lower)
        assertEquals(RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(100.0, 50.0) }, lower.rooms.single().points)
    }

    @Test
    fun `disegnando i muri di sopra il primo punto scatta sull'angolo di sotto`() {
        val t = SnapEngine.targetsWithBelow(FloorPlan(), lower)
        val r = SnapEngine.snapDrawing(Vec2(98.0, 53.0), null, null, t, emptyList(), tol)
        assertEquals(Vec2(100.0, 50.0), r.point)
        assertEquals(SnapKind.Endpoint, r.guides.single().kind)
    }
}
