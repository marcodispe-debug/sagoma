package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.KitchenModules
import com.sagoma.planimetria.model.KitchenModules.Front
import com.sagoma.planimetria.model.KitchenModules.Kind
import com.sagoma.planimetria.model.KitchenModules.Spec
import com.sagoma.planimetria.model.KitchenModules.Top
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Finiture della cucina: per tutti i moduli insieme (un solo passo da annullare) o per uno solo; gli altri arredi non cambiano. */
class KitchenFinishTest {
    private fun module(id: Long, kind: Kind, x: Double) =
        Furniture(id, KitchenModules.model(Spec(kind)), Vec2(x, 30.0), 0.0, kind.width, kind.depth, kind.height, kind.elevation)

    private val sofa = Furniture(9, "ph_sofa_01", Vec2(300.0, 300.0), 0.0, 157.0, 66.0, 80.0)

    private fun vm(): EditorViewModel {
        val plan = FloorPlan(furniture = listOf(module(1, Kind.BASE_DOORS, 30.0), module(2, Kind.SINK, 100.0), module(3, Kind.WALL_DOORS, 30.0), sofa))
        val b = Building.single(plan)
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun EditorViewModel.spec(id: Long) = KitchenModules.parse(state.value.plan.furniture.first { it.id == id }.model)!!

    @Test
    fun `ante cambiate per tutta la cucina con un solo annulla`() {
        val vm = vm()
        vm.setKitchenFinish(1, Front.WALNUT, null, all = true)
        for (id in listOf(1L, 2L, 3L)) assertEquals(Front.WALNUT, vm.spec(id).front)
        assertEquals(Top.LAMINATE_WHITE, vm.spec(2).top)
        assertEquals("ph_sofa_01", vm.state.value.plan.furniture.first { it.id == 9L }.model)
        vm.undo()
        for (id in listOf(1L, 2L, 3L)) assertEquals(Front.WHITE, vm.spec(id).front)
    }

    @Test
    fun `piano cambiato solo per un modulo`() {
        val vm = vm()
        vm.setKitchenFinish(2, null, Top.CARRARA, all = false)
        assertEquals(Top.CARRARA, vm.spec(2).top)
        assertEquals(Top.LAMINATE_WHITE, vm.spec(1).top)
        assertEquals(Front.WHITE, vm.spec(2).front)
    }
}
