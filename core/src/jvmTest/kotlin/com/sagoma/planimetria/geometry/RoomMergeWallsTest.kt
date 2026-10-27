package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Togliendo il muro in comune le due stanze diventano un ambiente unico: gli altri muri restano come erano, con il loro
 * spessore e il filo interno fermo, e la stanza tiene pavimento e pareti scelti.
 */
class RoomMergeWallsTest {
    // Soggiorno 0..400 e cucina 400..700 (alte 300): il muro destro del soggiorno (1) è in comune col sinistro della cucina (3).
    private val soggiorno = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
    private val cucina = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })

    /** Il muro di `r` che passa per il punto `p`. */
    private fun wallAt(r: Room, p: Vec2): Int = (0 until r.wallCount).single { Polygon.distanceToSegment(p, r.wallStart(it), r.wallEnd(it)) < 1.0 }

    @Test
    fun `lo spessore dei muri della stanza resta dopo l'unione`() {
        val plan = FloorPlan(listOf(soggiorno.copy(wallThickness = 30.0), cucina.copy(wallThickness = 30.0)))
        val merged = assertNotNull(RoomMerge.merge(plan, 1, 1)).plan.rooms.single()
        assertEquals(30.0, merged.wallThickness)
        // Ambiente 700×300 sulla mezzeria, muri da 30: dentro 670×270.
        assertEquals(670.0 * 270.0, merged.interiorArea(), 1e-6)
    }

    @Test
    fun `un muro ingrossato verso fuori resta ingrossato e il filo interno non si muove`() {
        // Muro a sinistra del soggiorno (3) da 40 cm, ingrossato verso fuori di 12,5 cm (come fa l'editor).
        val thick = soggiorno.copy(wallThicknesses = mapOf(3 to 40.0), wallOffsets = mapOf(3 to 12.5))
        val before = thick.interiorArea() + cucina.interiorArea() + 15.0 * 285.0 // più la striscia del muro tolto
        val merged = assertNotNull(RoomMerge.merge(FloorPlan(listOf(thick, cucina)), 1, 1)).plan.rooms.single()
        val left = wallAt(merged, Vec2(0.0, 150.0))
        assertEquals(40.0, merged.thicknessOf(left), "il muro a sinistra è ancora da 40")
        assertEquals(12.5, merged.wallShift(left), 1e-9, "ancora spostato verso fuori")
        assertEquals(before, merged.interiorArea(), 1e-6)
    }

    @Test
    fun `pavimento e pareti della stanza restano`() {
        val plan = FloorPlan(listOf(soggiorno.copy(floorFinish = FloorFinish.OakParquet, wallPaint = WallPaint.Sage, floorMaterial = "parquet-rovere"), cucina))
        val merged = assertNotNull(RoomMerge.merge(plan, 1, 1)).plan.rooms.single()
        assertEquals(FloorFinish.OakParquet, merged.floorFinish)
        assertEquals(WallPaint.Sage, merged.wallPaint)
        assertEquals("parquet-rovere", merged.floorMaterial)
    }

    @Test
    fun `le pareti che venivano dall'altra stanza tengono il loro colore`() {
        val plan = FloorPlan(listOf(soggiorno.copy(wallPaint = WallPaint.Sage), cucina.copy(wallPaint = WallPaint.Sky, wallThicknesses = mapOf(1 to 30.0), wallOffsets = mapOf(1 to 7.5))))
        val merged = assertNotNull(RoomMerge.merge(plan, 1, 1)).plan.rooms.single()
        val right = wallAt(merged, Vec2(700.0, 150.0))
        assertEquals(WallPaint.Sky, merged.wallPaints[right])
        assertEquals(30.0, merged.thicknessOf(right))
        assertEquals(null, merged.wallPaints[wallAt(merged, Vec2(0.0, 150.0))], "le pareti del soggiorno hanno il colore della stanza")
        assertEquals(WallPaint.Sage, merged.wallPaint)
    }
}
