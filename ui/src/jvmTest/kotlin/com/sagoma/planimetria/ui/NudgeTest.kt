package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** WASD e frecce con un oggetto selezionato: piccoli spostamenti, un solo «Annulla» per tasto tenuto. */
class NudgeTest {
    private fun vm(plan: FloorPlan) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(plan)
        override fun save(building: Building) {}
    })

    @Test
    fun `finestra - i passi si sommano e si annullano insieme`() {
        val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).copy(
            openings = listOf(Opening.default(1, OpeningKind.Window1, 0, 200.0)),
        )
        val vm = vm(FloorPlan(listOf(room)))
        val t = DragTarget.Opening(1, 1)
        repeat(5) { vm.nudge(t, Vec2(2.0, 0.0)) }
        assertTrue(vm.state.value.dragging, "mentre il tasto è giù si vedono le distanze")
        vm.endNudge()
        assertFalse(vm.state.value.dragging)
        assertEquals(210.0, vm.state.value.plan.room(1)!!.opening(1)!!.position, 1e-6)
        vm.undo()
        assertEquals(200.0, vm.state.value.plan.room(1)!!.opening(1)!!.position, 1e-6)
    }

    @Test
    fun `colonna - niente agganci, si sposta proprio di poco`() {
        val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0))
        val vm = vm(FloorPlan(listOf(room), columns = listOf(Column(7, center = Vec2(100.0, 100.0)))))
        vm.nudge(DragTarget.Column(7), Vec2(0.0, -1.5))
        vm.endNudge()
        assertEquals(Vec2(100.0, 98.5), vm.state.value.plan.column(7)!!.center)
    }
}

/** Quale oggetto spostano i tasti: quello selezionato, muro compreso; niente se non c'è selezione. */
class NudgeTargetTest {
    @Test
    fun bersagli() {
        assertEquals(DragTarget.Wall(1, 2), nudgeTarget(Selection.Wall(1, 2)))
        assertEquals(DragTarget.Opening(1, 3), nudgeTarget(Selection.Opening(1, 3)))
        assertEquals(null, nudgeTarget(null))
    }
}

/** Distanze dai muri scritte nella scheda: l'oggetto va proprio lì, con un solo «Annulla». */
class SetWallDistanceTest {
    @Test
    fun `porta al centro del muro e colonna a 100 dal muro`() {
        val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).copy(
            wallThickness = 20.0,
            openings = listOf(Opening.default(1, OpeningKind.Door, 0, 80.0)),
        )
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = Building.single(FloorPlan(listOf(room), columns = listOf(Column(7, center = Vec2(200.0, 150.0)))))
            override fun save(building: Building) {}
        })
        fun gaps(t: com.sagoma.planimetria.geometry.MeasureTarget) =
            com.sagoma.planimetria.geometry.WallDistances.of(vm.state.value.plan, t, 300.0)
        val door = com.sagoma.planimetria.geometry.MeasureTarget.Opening(1, 1)
        vm.centerOnWall(DragTarget.Opening(1, 1), door)
        assertEquals(200.0, vm.state.value.plan.room(1)!!.opening(1)!!.position, 1e-6)
        val after = gaps(door)
        assertEquals(after[0].distance, after[1].distance, 1e-6)
        vm.undo()
        assertEquals(80.0, vm.state.value.plan.room(1)!!.opening(1)!!.position, 1e-6)

        val col = com.sagoma.planimetria.geometry.MeasureTarget.Column(7)
        val left = com.sagoma.planimetria.geometry.WallSide.Left
        vm.setWallDistance(DragTarget.Column(7), col, left, 100.0)
        assertEquals(100.0, gaps(col).first { it.side == left }.distance, 1e-6)
        // Lo stesso numero scritto due volte (Invio, poi il campo perde il fuoco): la colonna resta lì.
        vm.setWallDistance(DragTarget.Column(7), col, left, 100.0)
        assertEquals(100.0, gaps(col).first { it.side == left }.distance, 1e-6)
    }

    @Test
    fun `colonna appoggiata sopra un muro - ogni distanza scritta arriva esatta`() {
        // Stanza a L come quella di Marco: la colonna da 30 sta a cavallo del muro in alto (filo interno a 10).
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, listOf(
            Vec2(0.0, 0.0), Vec2(600.0, 0.0), Vec2(600.0, 400.0), Vec2(300.0, 400.0), Vec2(300.0, 250.0), Vec2(0.0, 250.0),
        )).copy(wallThickness = 20.0)
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = Building.single(FloorPlan(listOf(room), columns = listOf(Column(2, center = Vec2(450.0, 12.0)))))
            override fun save(building: Building) {}
        })
        val col = com.sagoma.planimetria.geometry.MeasureTarget.Column(2)
        fun gaps() = com.sagoma.planimetria.geometry.WallDistances.of(vm.state.value.plan, col, 300.0).associate { it.side to it.distance }
        // Il muro sotto la colonna non conta: niente «0 cm» finti.
        assertTrue(gaps().values.none { it < 1e-6 })
        for ((side, want) in listOf(com.sagoma.planimetria.geometry.WallSide.Left to 100.0, com.sagoma.planimetria.geometry.WallSide.Right to 82.0,
            com.sagoma.planimetria.geometry.WallSide.Down to 82.0, com.sagoma.planimetria.geometry.WallSide.Up to 82.0)) {
            vm.setWallDistance(DragTarget.Column(2), col, side, want)
            assertEquals(want, gaps().getValue(side), 1e-6, "$side")
        }
    }
}
