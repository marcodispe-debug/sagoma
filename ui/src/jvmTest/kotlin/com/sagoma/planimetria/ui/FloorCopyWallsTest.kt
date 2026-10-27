package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Piano nuovo «partendo dai muri» del piano di sotto: i muri restano come sono (spessori, muri spostati, muri curvi). */
class FloorCopyWallsTest {
    private val room = Room(
        1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0),
        wallThickness = 30.0, wallThicknesses = mapOf(1 to 12.0), wallOffsets = mapOf(0 to 7.5), wallBulges = mapOf(2 to 60.0),
        openings = listOf(Opening(1, OpeningKind.Door, 3, 200.0, 80.0, 210.0)),
    )

    @Test
    fun `i muri copiati tengono spessore, spostamento e curva`() {
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = Building.single(FloorPlan(listOf(room)))
            override fun save(building: Building) {}
        })
        vm.addFloor("Primo piano", 300.0, copyRooms = true)
        val copy = vm.state.value.plan.rooms.single()
        assertEquals(room.points, copy.points)
        assertEquals(30.0, copy.wallThickness)
        assertEquals(mapOf(1 to 12.0), copy.wallThicknesses)
        assertEquals(mapOf(0 to 7.5), copy.wallOffsets)
        assertEquals(mapOf(2 to 60.0), copy.wallBulges)
        // Come prima: niente porte e finestre (sono diverse da piano a piano).
        assertTrue(copy.openings.isEmpty())
    }
}
