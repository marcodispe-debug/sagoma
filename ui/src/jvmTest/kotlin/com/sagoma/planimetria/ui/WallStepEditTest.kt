package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.exteriorOutline
import com.sagoma.planimetria.geometry.interiorOutline
import com.sagoma.planimetria.geometry.isStepCorner
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Muro che cambia spessore da un punto in poi: si divide, si scrive lo spessore, lo scalino scorre lungo il muro. */
class WallStepEditTest {
    // Camera 400×300; sul muro destro (x = 400, da y = 0 a y = 300) una finestra a 250 cm dall'inizio.
    private val window = Opening.default(1, OpeningKind.Window1, 1, 250.0).copy(width = 60.0)
    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).copy(openings = listOf(window)))))
        override fun save(building: Building) {}
    })

    private fun EditorViewModel.room() = state.value.plan.rooms.single()

    @Test
    fun `spessore diverso da un punto, scalino che scorre e finestra ferma`() {
        val vm = vm()
        val (a0, b0) = Openings.span(vm.room(), vm.room().openings.single())
        vm.startWallStep(1, 1)
        assertEquals(Selection.Wall(1, 2), vm.state.value.selection)
        assertEquals(Vec2(400.0, 150.0), vm.room().points[2])
        vm.setWallThickness(1, 2, 30.0)
        assertTrue(vm.room().isStepCorner(2))
        // Il filo verso la stanza resta dritto; il tratto più spesso sporge solo fuori.
        val t = vm.room().thicknessOf(1) / 2
        for (p in vm.room().interiorOutline().filter { it.x > 300 }) assertEquals(400.0 - t, p.x, 1e-6)
        assertTrue(vm.room().exteriorOutline().any { kotlin.math.abs(it.x - (400.0 - t + 30.0)) < 1e-6 })
        assertTrue(vm.room().exteriorOutline().any { kotlin.math.abs(it.x - (400.0 + t)) < 1e-6 })
        // Si salva e si rilegge uguale.
        val plan = vm.state.value.plan
        assertEquals(plan, com.sagoma.planimetria.persistence.PlanJson.decode(com.sagoma.planimetria.persistence.PlanJson.encode(plan)))
        com.sagoma.planimetria.geometry.Scene3D.build(plan, null, null, ceilings = true)
        // Trascinato di lato, lo scalino resta sul muro e scorre lungo di esso.
        val target = DragTarget.Corner(1, 2)
        vm.beginDrag(target, Vec2(400.0, 150.0))
        vm.dragBy(target, Vec2(35.0, -50.0))
        vm.endDrag()
        assertEquals(0.0, vm.room().points[2].distanceTo(Vec2(400.0, 100.0)), 1e-9)
        // Lunghezza scritta del secondo tratto: lo scalino si sposta, l'altra estremità resta ferma.
        vm.setStepPartLength(1, 2, 120.0)
        assertEquals(0.0, vm.room().points[2].distanceTo(Vec2(400.0, 180.0)), 1e-9)
        vm.setStepPartLength(1, 1, 60.0)
        assertEquals(0.0, vm.room().points[2].distanceTo(Vec2(400.0, 60.0)), 1e-9)
        // La finestra non si è mossa lungo il muro (di lato sta a metà del tratto più spesso).
        val o = vm.room().openings.single()
        val (a, b) = Openings.span(vm.room(), o)
        assertEquals(setOf(a0.y, b0.y), setOf(a.y, b.y))
        assertEquals(400.0 + 7.5, a.x, 1e-6)
    }
}
