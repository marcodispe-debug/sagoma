package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.arcOf
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Muro curvo: la curva si sceglie con il pallino a metà muro o scrivendo la freccia. */
class CurvedWallEditTest {
    private fun project() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(rooms = listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)))), autoLevel = true)
        override fun save(building: Building) {}
    })

    private fun EditorViewModel.room() = state.value.plan.rooms.single()

    @Test
    fun `freccia scritta, raddrizza e annulla`() {
        val vm = project()
        vm.setWallBulge(1, 1, 40.0)
        assertEquals(40.0, vm.room().arcOf(1)!!.bulge, 1e-9)
        // Al massimo mezzo cerchio.
        vm.setWallBulge(1, 1, 500.0)
        assertEquals(150.0, vm.room().arcOf(1)!!.bulge, 1e-9)
        vm.setWallBulge(1, 1, 0.0)
        assertNull(vm.room().arcOf(1))
        vm.undo()
        assertEquals(150.0, vm.room().arcOf(1)!!.bulge, 1e-9)
    }

    @Test
    fun `il pallino curva il muro e vicino al dritto lo raddrizza`() {
        val vm = project()
        vm.setCamera(Camera(1f, 0f, 0f))
        vm.selectWall(1, 1)
        val handle = assertNotNull(bulgeHandle(vm.state.value, 16f))
        assertTrue(handle.x > 400.0)
        val target = DragTarget.WallBulge(1, 1)
        vm.beginDrag(target, handle)
        vm.dragBy(target, Vec2(70.0, 5.0))
        vm.endDrag()
        assertEquals(70.0, vm.room().arcOf(1)!!.bulge, 1e-9)
        vm.beginDrag(target, handle)
        vm.dragBy(target, Vec2(-68.0, 0.0))
        vm.endDrag()
        assertNull(vm.room().arcOf(1))
    }
}
