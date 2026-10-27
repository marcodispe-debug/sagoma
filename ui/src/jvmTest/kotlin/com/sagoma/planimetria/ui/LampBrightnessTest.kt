package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Luminosità delle lampade scelta da chi usa l'app: resta nel progetto (per il 3D e per i render). */
class LampBrightnessTest {
    private val house = Building.single(FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)))), autoLevel = true)

    private fun vm(): EditorViewModel = EditorViewModel(object : PlanStore {
        override fun load() = house
        override fun save(building: Building) {}
    })

    @Test
    fun `si salva nel progetto e si rilegge`() {
        val vm = vm()
        vm.setLampBrightness(0.6)
        val saved = PlanJson.encode(vm.state.value.fullBuilding)
        assertEquals(0.6, PlanJson.decodeBuilding(saved).lampBrightness)
    }

    @Test
    fun `resta tra 25 e 200 per cento`() {
        val vm = vm()
        vm.setLampBrightness(5.0)
        assertEquals(Building.MAX_LAMP_BRIGHTNESS, vm.state.value.building.lampBrightness)
        vm.setLampBrightness(0.0)
        assertEquals(Building.MIN_LAMP_BRIGHTNESS, vm.state.value.building.lampBrightness)
    }

    @Test
    fun `i progetti vecchi restano al 100 per cento e il valore normale non si scrive`() {
        val old = PlanJson.encode(house)
        assertTrue("lampBrightness" !in old)
        assertEquals(1.0, PlanJson.decodeBuilding(old).lampBrightness)
    }
}
