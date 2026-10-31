package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Il colore esterno si sceglie una volta per tutta la casa; un piano può fare eccezione. */
class FacadeWholeHouseTest {
    private val stone = WallFinish(0xFFCCBBAA, Covering.Stone)
    private val ochre = WallFinish(0xFFD9A441)

    private fun vm(): EditorViewModel {
        val b = Building.single(FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)))), autoLevel = true)
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun EditorViewModel.facades() = state.value.fullBuilding.floors.map { it.plan.facadeOrPaint }

    @Test
    fun `il colore esterno vale per tutti i piani, anche quelli aggiunti dopo`() {
        val vm = vm()
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        vm.setFacadeFinish(ochre)
        assertEquals(listOf(ochre, ochre), vm.facades())
        vm.addFloor("Secondo piano", 300.0, copyRooms = true, autoLevel = true)
        assertEquals(listOf(ochre, ochre, ochre), vm.facades())
        vm.undo()
        vm.undo()
        assertEquals(2, vm.state.value.fullBuilding.floors.size)
        // Un solo Annulla toglie il colore da tutti i piani insieme.
        assertEquals(1, vm.facades().distinct().size)
        assertEquals(false, ochre in vm.facades())
    }

    @Test
    fun `anche un interrato aggiunto sotto prende il colore della casa`() {
        val vm = vm()
        vm.setFacadeFinish(ochre)
        vm.addFloor("", 300.0, copyRooms = true, autoLevel = true, below = true)
        assertEquals(listOf(ochre, ochre), vm.facades())
        assertEquals("Piano interrato", vm.state.value.fullBuilding.floors.first().name)
    }

    @Test
    fun `eccezione solo per il piano corrente`() {
        val vm = vm()
        vm.setFacadeFinish(ochre)
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        vm.setFacadeFinish(stone, onlyThisFloor = true)
        assertEquals(listOf(ochre, stone), vm.facades())
        vm.selectFloor(0)
        assertEquals(ochre, vm.state.value.plan.facadeOrPaint)
    }
}
