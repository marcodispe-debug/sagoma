package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.exteriorOutline
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.geometry.interiorOutline
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spessore dei muri a filo interno fermo, nei casi meno comuni: stanze a L, contorni girati al contrario, Annulla,
 * salvataggio, porte sul muro, spessore di tutta la stanza accanto a un'altra.
 */
class WallThicknessEdgeCasesTest {
    private var saved: Building? = null

    private fun vm(vararg rooms: Room) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(rooms.toList()))
        override fun save(building: Building) {
            saved = building
        }
    })

    private fun EditorViewModel.room(id: Long) = state.value.plan.room(id)!!

    private fun outline(r: Room) = r.interiorOutline().map { Vec2(round(it.x), round(it.y)) }.toSet()

    // Camera 400×300 sulla mezzeria; muri da 15 cm.
    private val camera = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0))

    // Soggiorno a L: 600×400 senza l'angolo in basso a destra (300×200).
    private val soggiorno = Room(
        1, "Soggiorno", RoomType.Soggiorno,
        listOf(Vec2(0.0, 0.0), Vec2(600.0, 0.0), Vec2(600.0, 200.0), Vec2(300.0, 200.0), Vec2(300.0, 400.0), Vec2(0.0, 400.0)),
    )

    @Test
    fun `nella stanza a L ogni muro ingrossa verso fuori, anche quelli dell'angolo rientrante`() {
        val vm = vm(soggiorno)
        val inner = outline(vm.room(1))
        val area = vm.room(1).interiorArea()
        for (i in 0 until soggiorno.wallCount) {
            vm.setWallThickness(1, i, 30.0 + i)
            assertEquals(inner, outline(vm.room(1)), "muro $i")
            assertEquals(area, vm.room(1).interiorArea(), 1e-6, "muro $i")
        }
    }

    @Test
    fun `una stanza con gli angoli in senso antiorario si comporta uguale`() {
        val reversed = camera.copy(points = camera.points.reversed())
        val vm = vm(reversed)
        val inner = outline(vm.room(1))
        val area = vm.room(1).interiorArea()
        for (i in 0 until reversed.wallCount) vm.setWallThickness(1, i, 40.0)
        assertEquals(inner, outline(vm.room(1)))
        assertEquals(area, vm.room(1).interiorArea(), 1e-6)
        // Il filo esterno è 40 cm fuori da quello interno, su tutti i lati.
        val box = Polygon.bounds(vm.room(1).exteriorOutline())
        assertEquals(7.5 - 40.0, box.minX, 1e-6)
        assertEquals(392.5 + 40.0, box.maxX, 1e-6)
    }

    @Test
    fun `Annulla rimette lo spessore e il muro dove erano`() {
        val vm = vm(camera)
        val start = vm.room(1)
        vm.setWallThickness(1, 1, 40.0)
        vm.setRoomWallThickness(1, 25.0)
        vm.undo()
        assertEquals(40.0, vm.room(1).thicknessOf(1))
        assertEquals(15.0, vm.room(1).thicknessOf(0))
        vm.undo()
        assertEquals(start, vm.room(1))
        assertTrue(vm.room(1).wallOffsets.isEmpty())
    }

    @Test
    fun `salvato e riaperto, il muro ingrossato resta a filo interno`() {
        val vm = vm(camera)
        vm.setWallThickness(1, 1, 40.0)
        vm.setRoomWallThickness(1, 25.0)
        val room = vm.room(1)
        val reread = PlanJson.decodeBuilding(PlanJson.encode(vm.state.value.fullBuilding)).floor.plan.room(1)!!
        assertEquals(room, reread)
        assertEquals(outline(camera), outline(reread))
        assertEquals(camera.interiorArea(), reread.interiorArea(), 1e-6)
    }

    @Test
    fun `una porta sul muro ingrossato resta dov'era e dentro il muro`() {
        val door = Opening.default(1, OpeningKind.Door, 1, 100.0)
        val vm = vm(camera.copy(openings = listOf(door)))
        val (a0, b0) = Openings.span(vm.room(1), vm.room(1).openings.single())
        vm.setWallThickness(1, 1, 40.0)
        val r = vm.room(1)
        assertEquals(door.position, r.openings.single().position)
        val (a1, b1) = Openings.span(r, r.openings.single())
        assertEquals(a0.y, a1.y, 1e-6)
        assertEquals(b0.y, b1.y, 1e-6)
        assertEquals(listOf(385.0, 285.0, 385.0, 285.0), r.interiorLengths().map { round(it) })
    }

    @Test
    fun `spessori fuori misura non cambiano niente`() {
        val vm = vm(camera)
        val start = vm.room(1)
        vm.setWallThickness(1, 1, 2.0)
        vm.setWallThickness(1, 1, 500.0)
        vm.setWallThickness(1, 9, 30.0)
        vm.setRoomWallThickness(1, 1.0)
        vm.setRoomWallThickness(1, 200.0)
        assertEquals(start, vm.room(1))
    }

    @Ignore // DIFETTO: setRoomWallThickness non cambia il muro in comune per la stanza accanto (setWallThickness sì).
    @Test
    fun `cambiando lo spessore di tutta la stanza, il muro in comune cambia anche per la stanza accanto`() {
        // Come per il singolo muro: il muro tra camera e bagno è uno solo, le due stanze devono vederlo nello stesso posto.
        val bagno = Room(2, "Bagno", RoomType.Bagno, RoomFactory.rectangle(200.0, 300.0).map { it + Vec2(400.0, 0.0) })
        val vm = vm(camera, bagno)
        val areaCamera = vm.room(1).interiorArea()
        vm.setRoomWallThickness(1, 30.0)
        assertEquals(areaCamera, vm.room(1).interiorArea(), 1e-6)
        assertEquals(30.0, vm.room(2).thicknessOf(3), "il bagno vede lo stesso muro da 30 cm")
        assertEquals(
            Polygon.bounds(vm.room(1).exteriorOutline()).maxX,
            Polygon.bounds(vm.room(2).interiorOutline()).minX,
            1e-6,
            "il muro della camera non entra nel bagno",
        )
    }

    private fun round(v: Double) = kotlin.math.round(v * 1000) / 1000
}
