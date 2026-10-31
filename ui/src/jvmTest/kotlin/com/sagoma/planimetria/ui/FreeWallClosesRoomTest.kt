package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Muro singolo portato sul lato eliminato di una stanza: torna quel lato e le sue porte restano fisicamente come
 * erano (cardine allo stesso capo, anta che si apre dalla stessa parte), in qualunque verso sia stato disegnato il muro.
 */
class FreeWallClosesRoomTest {
    // Stanza 400 × 300 senza il lato destro (muro 1, da (400, 0) a (400, 300)).
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).copy(removedWalls = setOf(1))
    private val door = Opening(1, OpeningKind.Door, 0, 150.0, 80.0, 210.0, hingeLeft = true, opensInward = true)

    /**
     * Da che parte sta il cardine rispetto al centro della porta e verso in cui si apre l'anta, nel mondo (il muro trascinato
     * si aggancia ai muri della stanza e può scorrere di qualche cm: conta da che parte, non dove).
     */
    private fun physical(r: Room, o: Opening): Pair<Vec2, Vec2> {
        val (a, b) = Openings.span(r, o)
        val n = Openings.inwardNormal(r, o.wallIndex)
        return ((if (o.hingeLeft) a else b) - (a + b) / 2.0).normalized() to (if (o.opensInward) n else n * -1.0)
    }

    private fun check(start: Vec2, end: Vec2) {
        val wall = FreeWall(1, start + Vec2(8.0, 0.0), end + Vec2(8.0, 0.0), openings = listOf(door))
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = Building.single(FloorPlan(listOf(room), freeWalls = listOf(wall)))
            override fun save(building: Building) {}
        })
        val before = physical(wall.copy(start = start, end = end).host, door)
        vm.beginDrag(DragTarget.FreeWallBody(1), wall.mid)
        vm.dragBy(DragTarget.FreeWallBody(1), Vec2(-8.0, 0.0))
        vm.endDrag()
        val plan = vm.state.value.plan
        assertTrue(plan.freeWalls.isEmpty(), "il muro singolo è tornato il lato della stanza")
        val r = plan.room(1)!!
        assertEquals(emptySet(), r.removedWalls)
        val o = r.openings.single()
        assertEquals(1, o.wallIndex)
        val after = physical(r, o)
        assertTrue(before.first.distanceTo(after.first) < 1e-6, "cardine: prima ${before.first}, dopo ${after.first}")
        assertTrue(before.second.distanceTo(after.second) < 1e-6, "verso dell'anta: prima ${before.second}, dopo ${after.second}")
    }

    @Test
    fun `muro disegnato nello stesso verso del lato`() = check(Vec2(400.0, 0.0), Vec2(400.0, 300.0))

    @Test
    fun `muro disegnato al contrario`() = check(Vec2(400.0, 300.0), Vec2(400.0, 0.0))
}
