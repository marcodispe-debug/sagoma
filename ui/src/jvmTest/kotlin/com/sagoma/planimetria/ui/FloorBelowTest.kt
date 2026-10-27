package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.AutoNames
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.en.Inglese
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Piani sotto il piano terra (interrati): si aggiungono sotto il più basso e il piano terra resta quello. */
class FloorBelowTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    private fun vm(building: Building): EditorViewModel = EditorViewModel(object : PlanStore {
        override fun load() = building
        override fun save(building: Building) {}
    })

    private val house = Building.single(FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)))), autoLevel = true)

    @Test
    fun `un piano sotto va in fondo, diventa quello corrente e il piano terra resta il piano terra`() {
        val vm = vm(house)
        vm.addFloor("", 300.0, copyRooms = true, autoLevel = true, below = true)
        val b = vm.state.value.fullBuilding
        assertEquals(listOf("Piano interrato", "Piano terra"), b.floors.map { it.name })
        assertEquals(0, b.current)
        assertEquals(1, b.groundIndex)
        assertEquals(listOf(-1, 0), b.floors.indices.map(b::level))
        // Parte dai muri del piano più basso, senza aperture né impianti.
        assertEquals(house.floor.plan.rooms.map { it.points }, b.floor.plan.rooms.map { it.points })
        // Un altro sotto, poi uno sopra: i nomi seguono la posizione rispetto al piano terra.
        vm.addFloor("", 300.0, copyRooms = false, autoLevel = true, below = true)
        vm.addFloor("", 300.0, copyRooms = false, autoLevel = true)
        assertEquals(listOf("2° interrato", "Piano interrato", "Piano terra", "Primo piano"), vm.state.value.fullBuilding.floors.map { it.name })
        assertEquals(2, vm.state.value.fullBuilding.groundIndex)
    }

    @Test
    fun `togliendo un interrato il piano terra resta lo stesso, e si annulla`() {
        val vm = vm(house)
        vm.addFloor("Cantina", 300.0, copyRooms = true, autoLevel = true, below = true)
        vm.deleteFloor(0)
        val b = vm.state.value.fullBuilding
        assertEquals(listOf("Piano terra"), b.floors.map { it.name })
        assertEquals(0, b.groundIndex)
        vm.undo()
        assertEquals(1, vm.state.value.fullBuilding.groundIndex)
    }

    @Test
    fun `il piano terra si salva nel file, i file vecchi partono dal piano piu basso`() {
        val vm = vm(house)
        vm.addFloor("", 300.0, copyRooms = true, autoLevel = true, below = true)
        val b = vm.state.value.fullBuilding
        assertEquals(1, PlanJson.decodeBuilding(PlanJson.encode(b)).groundIndex)
        assertEquals(0, PlanJson.decodeBuilding(PlanJson.encode(house)).groundIndex)
    }

    @Test
    fun `i nomi degli interrati si traducono`() {
        assertEquals("Basement", Building.floorName(-1, Inglese))
        assertEquals("Basement 2", AutoNames.floor("2° interrato", Inglese))
        assertEquals("Piano interrato", AutoNames.floor("Basement", Italiano))
    }
}
