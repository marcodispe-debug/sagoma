package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Distances
import com.sagoma.planimetria.geometry.MeasureTarget
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeWallTest {

    // Stanza 600 × 400 (mezzerie), muri spessi 15: facce interne a x = 7,5 / 592,5 e y = 7,5 / 392,5.
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 400.0))
    private val plan = FloorPlan(listOf(room))

    @Test
    fun `il muro proposto divide la stanza da un muro all'altro`() {
        val w = Structure.defaultFreeWall(1, room, Vec2(300.0, 200.0), plan)
        assertEquals(7.5, minOf(w.start.y, w.end.y), 1e-6)
        assertEquals(392.5, maxOf(w.start.y, w.end.y), 1e-6)
        assertEquals(10.0, w.thickness, 0.0)
    }

    @Test
    fun `l'estremità si aggancia alla faccia di un muro della stanza e all'estremità di un altro muro singolo`() {
        val w = FreeWall(1, Vec2(100.0, 200.0), Vec2(580.0, 200.0))
        // Vicino al muro di destra: si appoggia alla sua faccia interna.
        assertEquals(Vec2(592.5, 200.0), Structure.snapWallEnd(plan, w.end, w.start, w.id))
        // Un secondo muro singolo che parte da vicino all'estremità del primo: si unisce lì (angolo).
        val p = plan.copy(freeWalls = listOf(w))
        assertEquals(Vec2(100.0, 200.0), Structure.snapWallEnd(p, Vec2(108.0, 195.0), Vec2(100.0, 50.0), 2))
        // Vicino alla faccia di un muro singolo (a T): si appoggia al suo fianco (y = 195).
        assertEquals(Vec2(300.0, 195.0), Structure.snapWallEnd(p, Vec2(300.0, 185.0), Vec2(300.0, 50.0), 2))
    }

    @Test
    fun `spostato vicino a un muro parallelo ci si accosta, anche a un altro muro singolo`() {
        val w = FreeWall(1, Vec2(20.0, 7.5), Vec2(20.0, 392.5))
        // Fianco sinistro a x = 15: a 7,5 cm dalla faccia del muro di sinistra, ci si accosta.
        assertEquals(12.5, Structure.snapped(plan, w).start.x, 1e-6)
        val other = FreeWall(2, Vec2(300.0, 7.5), Vec2(300.0, 392.5))
        val near = FreeWall(3, Vec2(318.0, 7.5), Vec2(318.0, 392.5))
        // Fianchi a 305 e 313: si accosta al fianco dell'altro muro singolo (centro a 305 + 5), senza storcersi.
        val s = Structure.snapped(plan.copy(freeWalls = listOf(other, near)), near)
        assertEquals(310.0, s.start.x, 1e-6)
        assertEquals(310.0, s.end.x, 1e-6)
    }

    @Test
    fun `in 3D arriva al soffitto o più in basso, si tocca e blocca chi cammina, e la distanza si misura`() {
        val w = FreeWall(1, Vec2(300.0, 7.5), Vec2(300.0, 392.5))
        val p = plan.copy(freeWalls = listOf(w, FreeWall(2, Vec2(100.0, 7.5), Vec2(100.0, 200.0), height = 100.0)))
        val scene = Scene3D.build(p, null, null, ceilings = false)
        val hit = scene.hit(Vec3(300.0, 1000.0, 200.0), Vec3(0.0, -1.0, 0.0))!!
        assertEquals(Pick.FreeWall(1), hit.pick)
        assertEquals(270.0, hit.point.y, 0.5)
        assertEquals(100.0, scene.hit(Vec3(100.0, 1000.0, 100.0), Vec3(0.0, -1.0, 0.0))!!.point.y, 0.5)
        assertTrue(scene.blocked(Vec2(300.0, 200.0), 10.0))
        // Dal muro di sinistra (faccia 7,5) al fianco del tramezzo (295).
        assertEquals(295.0 - 7.5, Distances.between(p, MeasureTarget.Wall(1, 3), MeasureTarget.FreeWall(1), 300.0)!!.distance, 1e-6)
        assertEquals(p, PlanJson.decode(PlanJson.encode(p)))
    }
}
