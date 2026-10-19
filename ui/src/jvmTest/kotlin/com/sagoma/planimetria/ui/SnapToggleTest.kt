package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Agganci spenti dalla tendina Info: angoli e stanze vanno esattamente dove li si porta. */
class SnapToggleTest {
    private val a = Room(1, "A", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0))
    private val b = Room(2, "B", RoomType.Bagno, RoomFactory.rectangle(200.0, 300.0).map { it + Vec2(600.0, 0.0) })

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(a, b)))
        override fun save(building: Building) {}
    })

    @Test
    fun `angolo trascinato vicino a un altro - con gli agganci scatta, senza resta dove lo porto`() {
        // Angolo (400, 0) di A portato a 4 cm dall'angolo (600, 0) di B.
        val delta = Vec2(196.0, 3.0)
        val on = vm()
        on.beginDrag(DragTarget.Corner(1, 1), a.points[1]); on.dragBy(DragTarget.Corner(1, 1), delta); on.endDrag()
        assertEquals(Vec2(600.0, 0.0), on.state.value.plan.room(1)!!.points[1])

        val off = vm()
        off.setSnapOn(false)
        off.beginDrag(DragTarget.Corner(1, 1), a.points[1]); off.dragBy(DragTarget.Corner(1, 1), delta); off.endDrag()
        assertEquals(a.points[1] + delta, off.state.value.plan.room(1)!!.points[1])
    }

    @Test
    fun `stanza spostata senza agganci non si accosta all'altra`() {
        val vm = vm()
        vm.setSnapOn(false)
        val delta = Vec2(195.0, 2.0)
        vm.beginDrag(DragTarget.RoomLabel(1), Vec2(200.0, 150.0)); vm.dragBy(DragTarget.RoomLabel(1), delta); vm.endDrag()
        assertEquals(a.points.map { it + delta }, vm.state.value.plan.room(1)!!.points)
    }

    @Test
    fun `muro per muro senza agganci - niente direzioni bloccate, ma il primo angolo chiude`() {
        val vm = vm()
        vm.setSnapOn(false)
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(0.0, 500.0))
        vm.drawWallsTap(Vec2(300.0, 503.0)) // quasi orizzontale: resta storto
        assertEquals(Vec2(300.0, 503.0), vm.state.value.wallDraw!!.points.last())
        vm.drawWallsTap(Vec2(300.0, 800.0))
        vm.drawWallsTap(Vec2(1.0, 1.0 + 500.0)) // vicino al primo angolo: chiude
        assertEquals(true, vm.state.value.wallDraw!!.closing)
        vm.setSnapOn(true)
        assertEquals(true, vm.state.value.snapOn)
    }
}
