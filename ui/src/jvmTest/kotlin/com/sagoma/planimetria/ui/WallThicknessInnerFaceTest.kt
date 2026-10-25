package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.exteriorOutline
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.geometry.interiorOutline
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cambiando lo spessore il filo interno del muro resta fermo: il muro cresce o cala solo verso fuori. */
class WallThicknessInnerFaceTest {
    private fun vm(vararg rooms: Room) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(rooms.toList()))
        override fun save(building: Building) {}
    })

    private fun EditorViewModel.room(id: Long) = state.value.plan.room(id)!!

    // Camera 400×300 sulla mezzeria; muri da 15 cm: il filo interno è 385×285.
    private val camera = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0))

    @Test
    fun `un muro piu spesso cresce solo verso fuori`() {
        val vm = vm(camera)
        val inner = vm.room(1).interiorOutline().map { Vec2(round(it.x), round(it.y)) }
        val area = vm.room(1).interiorArea()
        // Muro destro (x = 400) da 15 a 40 cm.
        vm.setWallThickness(1, 1, 40.0)
        val r = vm.room(1)
        assertEquals(40.0, r.thicknessOf(1))
        assertEquals(inner, r.interiorOutline().map { Vec2(round(it.x), round(it.y)) })
        assertEquals(area, r.interiorArea(), 1e-6)
        assertEquals(listOf(385.0, 285.0, 385.0, 285.0), r.interiorLengths().map { round(it) })
        // Il filo esterno del muro destro sta a 392,5 + 40.
        assertTrue(r.exteriorOutline().any { kotlin.math.abs(it.x - 432.5) < 1e-6 })
        // Più sottile: ancora fermo dentro. Ritornato come gli altri: niente spostamento rimasto.
        vm.setWallThickness(1, 1, 8.0)
        assertEquals(area, vm.room(1).interiorArea(), 1e-6)
        vm.setWallThickness(1, 1, null)
        assertEquals(15.0, vm.room(1).thicknessOf(1))
        assertTrue(vm.room(1).wallOffsets.isEmpty())
        assertEquals(area, vm.room(1).interiorArea(), 1e-6)
    }

    @Test
    fun `spessore di tutti i muri della stanza a filo interno fermo`() {
        val vm = vm(camera)
        val area = vm.room(1).interiorArea()
        vm.setRoomWallThickness(1, 30.0)
        assertEquals(area, vm.room(1).interiorArea(), 1e-6)
        assertEquals(listOf(385.0, 285.0, 385.0, 285.0), vm.room(1).interiorLengths().map { round(it) })
        assertEquals(Polygon.bounds(vm.room(1).exteriorOutline()).maxX, 392.5 + 30.0, 1e-6)
    }

    @Test
    fun `muro in comune si allarga verso la stanza accanto`() {
        val bagno = Room(2, "Bagno", RoomType.Bagno, RoomFactory.rectangle(200.0, 300.0).map { it + Vec2(400.0, 0.0) })
        val vm = vm(camera, bagno)
        val areaCamera = vm.room(1).interiorArea()
        val areaBagno = vm.room(2).interiorArea()
        vm.setWallThickness(1, 1, 25.0)
        assertEquals(25.0, vm.room(2).thicknessOf(3))
        assertEquals(areaCamera, vm.room(1).interiorArea(), 1e-6)
        // Il bagno perde 10 cm di larghezza sul muro in comune.
        assertEquals(areaBagno - 10.0 * 285.0, vm.room(2).interiorArea(), 1e-6)
        // Le due stanze vedono il muro nello stesso posto.
        assertEquals(Polygon.bounds(vm.room(1).exteriorOutline()).maxX, Polygon.bounds(vm.room(2).interiorOutline()).minX, 1e-6)
    }

    private fun round(v: Double) = kotlin.math.round(v * 1000) / 1000
}
