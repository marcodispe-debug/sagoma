package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.usableArea
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.ExcludedZone
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Zone non calpestabili: come nella mansarda di Marco, l'area fitness ha una fascia "non accessibile" (soffitto basso) e
 * un cavedio che la piantina non conta nei m².
 */
class ExcludedZoneTest {
    /** Stanza 400 × 300 sul filo interno (muri da 15: mezzeria allargata di 7,5). */
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(415.0, 315.0).map { it - Vec2(7.5, 7.5) })

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(room)))
        override fun save(building: Building) {}
    })

    @Test
    fun `la zona disegnata si toglie dai metri quadri`() {
        val vm = vm()
        assertEquals(12.0, room.interiorArea() / 1e4, 1e-6)
        vm.startExcludedZone(1)
        // Fascia lungo il muro di sinistra, 80 × 300, agganciata agli angoli interni.
        for (p in listOf(Vec2(0.0, 0.0), Vec2(80.0, 0.0), Vec2(80.0, 300.0), Vec2(0.0, 300.0), Vec2(0.0, 0.0))) vm.drawWallsTap(p)
        assertEquals(true, vm.state.value.wallDraw!!.closing)
        vm.finishExcludedZone("Non accessibile")
        val r = vm.state.value.plan.room(1)!!
        assertNull(vm.state.value.wallDraw)
        assertEquals(1, r.excludedZones.size)
        assertEquals(12.0, r.interiorArea() / 1e4, 1e-6, "il pavimento resta quello")
        assertEquals(12.0 - 2.4, r.usableArea() / 1e4, 1e-6)

        vm.undo()
        assertEquals(0, vm.state.value.plan.room(1)!!.excludedZones.size)
        vm.redo()
        vm.removeExcludedZone(1, 1)
        assertEquals(12.0, vm.state.value.plan.room(1)!!.usableArea() / 1e4, 1e-6)
    }

    @Test
    fun `conta solo la parte dentro i muri`() {
        // Zona disegnata fin dentro il muro (da -20): conta solo da 0 a 80.
        val r = room.copy(excludedZones = listOf(ExcludedZone(1, listOf(Vec2(-20.0, -20.0), Vec2(80.0, -20.0), Vec2(80.0, 320.0), Vec2(-20.0, 320.0)))))
        assertEquals(12.0 - 2.4, r.usableArea() / 1e4, 1e-6)
    }

    @Test
    fun `area fitness della mansarda - 16,52 meno zona e cavedio fa circa 14,35`() {
        // Misure del progetto di Marco (filo interno, cm): L di 303 × 440 più 169 × 188 sotto il bagno.
        val inner = listOf(Vec2(0.0, 0.0), Vec2(303.0, 0.0), Vec2(303.0, 251.8), Vec2(472.1, 251.8), Vec2(472.1, 440.2), Vec2(0.0, 440.2))
        val axis = com.sagoma.planimetria.geometry.Polygon.inset(inner, -7.5)
        val zones = listOf(
            ExcludedZone(1, listOf(Vec2(0.0, 0.0), Vec2(78.0, 0.0), Vec2(78.0, 245.0), Vec2(0.0, 245.0))),
            ExcludedZone(2, listOf(Vec2(257.0, 0.0), Vec2(303.0, 0.0), Vec2(303.0, 41.0), Vec2(257.0, 41.0)), "Cavedio"),
        )
        val r = Room(2, "Area fitness", RoomType.Altro, axis, excludedZones = zones)
        assertEquals(16.52, r.interiorArea() / 1e4, 0.01)
        assertEquals(14.35, r.usableArea() / 1e4, 0.1)
    }

    @Test
    fun `la zona segue la stanza spostata e si salva`() {
        val r = room.copy(excludedZones = listOf(ExcludedZone(1, listOf(Vec2(0.0, 0.0), Vec2(80.0, 0.0), Vec2(80.0, 300.0), Vec2(0.0, 300.0)))))
        val moved = Fixtures.movedRoom(r, Vec2(100.0, 50.0))
        assertEquals(Vec2(100.0, 50.0), moved.excludedZones.single().points.first())
        assertEquals(r.usableArea(), moved.usableArea(), 1e-6)
        val back = PlanJson.decode(PlanJson.encode(FloorPlan(listOf(r)))).rooms.single()
        assertEquals(r.excludedZones, back.excludedZones)
    }
}
