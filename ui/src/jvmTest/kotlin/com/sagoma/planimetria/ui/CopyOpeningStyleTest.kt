package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.PendingOpening
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Copia stile su tutte le finestre del piano»: l'aspetto di una finestra passa a tutti gli infissi del piano, in una modifica sola. */
class CopyOpeningStyleTest {
    private val kitchen = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(400.0, 300.0)).copy(
        openings = listOf(
            Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(windowModel = WindowModel.Wood, shading = Shading.RomanShutter, grille = true),
            Opening.default(2, OpeningKind.Window1, 1, 150.0),
            Opening.default(3, OpeningKind.Door, 2, 100.0),
            Opening.default(4, OpeningKind.Balcony1, 3, 150.0).copy(sliding = true),
        ),
    )
    private val bedroom = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }).copy(
        openings = listOf(Opening.default(1, OpeningKind.Window1, 0, 150.0), Opening.default(2, OpeningKind.Door, 3, 100.0).copy(doorModel = DoorModel.Panels)),
    )
    private val wall = FreeWall(1, Vec2(0.0, 500.0), Vec2(300.0, 500.0), openings = listOf(Opening.default(1, OpeningKind.Window1, 0, 150.0)))

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(kitchen, bedroom), freeWalls = listOf(wall)))
        override fun save(building: Building) {}
    })

    @Test
    fun `lo stile della finestra va su tutte le finestre del piano, balconi e porte restano`() {
        val vm = vm()
        assertEquals(3, vm.copyOpeningStyle(1, 1))
        val plan = vm.state.value.plan
        val windows = plan.rooms.flatMap { it.openings }.filter { it.kind.isWindow } + plan.freeWalls.single().openings
        assertEquals(4, windows.size)
        for (o in windows) {
            assertEquals(WindowModel.Wood, o.windowModel)
            assertEquals(Shading.RomanShutter, o.shading)
            assertTrue(o.grille)
        }
        // Il balcone e le porte non cambiano.
        assertEquals(kitchen.openings[3], plan.room(1)!!.opening(4))
        assertEquals(DoorModel.Panels, plan.room(2)!!.opening(2)!!.doorModel)
        assertEquals(kitchen.openings[2], plan.room(1)!!.opening(3))
        // Di nuovo: niente da cambiare.
        assertEquals(0, vm.copyOpeningStyle(1, 1))
    }

    @Test
    fun `lo stile del balcone va solo sui balconi, scorrevoli o no, e le misure restano`() {
        val vm = vm()
        vm.startPlacingOpening(PendingOpening(OpeningKind.Balcony2))
        vm.onTap(DragTarget.Wall(2, 0), Vec2(500.0, 0.0))
        val added = vm.state.value.plan.room(2)!!.openings.last()
        vm.updateOpening(1, kitchen.openings[3].copy(windowModel = WindowModel.Wood, shading = Shading.RomanShutter))
        assertEquals(1, vm.copyOpeningStyle(1, 4))
        val plan = vm.state.value.plan
        val styled = plan.room(2)!!.opening(added.id)!!
        assertEquals(WindowModel.Wood, styled.windowModel)
        assertEquals(Shading.RomanShutter, styled.shading)
        assertEquals(added.width, styled.width)
        // Le finestre restano come erano.
        assertEquals(kitchen.openings[0], plan.room(1)!!.opening(1))
        assertEquals(bedroom.openings[0], plan.room(2)!!.opening(1))
        assertEquals(wall.openings[0], plan.freeWalls.single().openings.single())
    }

    @Test
    fun `si annulla tutto con un solo passo indietro`() {
        val vm = vm()
        val before = vm.state.value.plan
        vm.copyOpeningStyle(1, 1)
        vm.undo()
        assertEquals(before, vm.state.value.plan)
    }

    @Test
    fun `dalla porta lo stile va solo sulle porte, anche dal muro singolo`() {
        val vm = vm()
        assertEquals(1, vm.copyOpeningStyle(2, 2))
        assertEquals(DoorModel.Panels, vm.state.value.plan.room(1)!!.opening(3)!!.doorModel)
        // Dal muro singolo (id negativo) la finestra passa il suo stile (quello di serie) alle altre.
        assertEquals(1, vm.copyOpeningStyle(FreeWall.hostId(1), 1))
        assertEquals(WindowModel.Classic, vm.state.value.plan.room(1)!!.opening(1)!!.windowModel)
    }

    @Test
    fun `su tutta la casa - anche le finestre degli altri piani, con un solo passo indietro`() {
        val upstairs = Room(3, "Camera su", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).copy(
            openings = listOf(Opening.default(1, OpeningKind.Window1, 0, 150.0), Opening.default(2, OpeningKind.Door, 2, 100.0)),
        )
        val house = Building(listOf(Floor(1, "PT", FloorPlan(listOf(kitchen, bedroom), freeWalls = listOf(wall))), Floor(2, "P1", FloorPlan(listOf(upstairs)))))
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = house
            override fun save(building: Building) {}
        })
        val before = vm.state.value.fullBuilding
        // Solo il piano: sopra non cambia niente.
        assertEquals(3, vm.copyOpeningStyle(1, 1))
        assertEquals(WindowModel.Classic, vm.state.value.fullBuilding.floors[1].plan.room(3)!!.opening(1)!!.windowModel)
        vm.undo()
        // Tutta la casa: la finestra di sopra cambia, la porta no.
        assertEquals(4, vm.copyOpeningStyle(1, 1, allFloors = true))
        val up = vm.state.value.fullBuilding.floors[1].plan.room(3)!!
        assertEquals(WindowModel.Wood, up.opening(1)!!.windowModel)
        assertTrue(up.opening(1)!!.grille)
        assertEquals(upstairs.openings[1], up.opening(2))
        assertEquals(0, vm.state.value.fullBuilding.current)
        vm.undo()
        assertEquals(before, vm.state.value.fullBuilding)
    }

    @Test
    fun `finestre e balconi nuovi con il davanzale di marmo, le porte no`() {
        val vm = vm()
        for ((kind, at) in listOf(OpeningKind.Window1 to 300.0, OpeningKind.Balcony2 to 100.0, OpeningKind.Door to 200.0)) {
            vm.startPlacingOpening(PendingOpening(kind))
            vm.onTap(DragTarget.Wall(2, 0), Vec2(400.0 + at, 0.0))
        }
        val added = vm.state.value.plan.room(2)!!.openings.drop(2)
        assertEquals(listOf(StairMaterial.Marble, StairMaterial.Marble, null), added.map { it.sill })
    }
}
