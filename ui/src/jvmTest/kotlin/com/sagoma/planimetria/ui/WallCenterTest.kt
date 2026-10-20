package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Toccando un muro, la vista lo porta al centro dello spazio libero senza cambiare lo zoom. */
class WallCenterTest {
    private fun project(room: Room) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(rooms = listOf(room)), autoLevel = true)
        override fun save(building: Building) {}
    })

    @Test
    fun `il muro selezionato va al centro dello spazio libero`() {
        val room = Room(id = 1, name = "Soggiorno", type = RoomType.Soggiorno, points = listOf(Vec2(0.0, 0.0), Vec2(1000.0, 0.0), Vec2(1000.0, 600.0), Vec2(0.0, 600.0)))
        val vm = project(room)
        vm.setCamera(Camera(0.5f, 0f, 0f))
        vm.selectWall(1, 1) // lato destro: centro (1000, 300)
        val mid = (room.wallStart(1) + room.wallEnd(1)) / 2.0
        val cam = vm.centeredCamera(visibleRightPx = 800f, visibleBottomPx = 700f, marginPx = 24f, visibleTopPx = 100f)!!
        assertEquals(0.5f, cam.scale)
        assertEquals(400.0, mid.x * cam.scale + cam.offsetX, 0.5)
        assertEquals(400.0, mid.y * cam.scale + cam.offsetY, 0.5)
    }
}
