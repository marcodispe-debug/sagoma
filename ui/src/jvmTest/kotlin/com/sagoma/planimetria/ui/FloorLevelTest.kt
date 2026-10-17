package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.MergeRequest
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Interpiano automatico: altezza della stanza più alta del piano più il solaio (30 cm). */
class FloorLevelTest {
    private fun room(id: Long, ceiling: Double, type: RoomType = RoomType.Soggiorno, dx: Double = 0.0) =
        Room(id, "Stanza $id", type, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(dx, 0.0) }, ceilingHeight = ceiling)

    private fun vm(building: Building): EditorViewModel = EditorViewModel(object : PlanStore {
        override fun load() = building
        override fun save(building: Building) {}
    })

    private fun auto(vararg rooms: Room) = Building.single(FloorPlan(rooms.toList()), autoLevel = true)
    private fun manual(level: Double, vararg rooms: Room) = Building(listOf(Floor(1, "Piano terra", FloorPlan(rooms.toList()), level)))

    private fun EditorViewModel.addRoomWithCeiling(ceiling: Double) {
        startAddRoom()
        pickShape(RoomShape.Rectangle)
        createRoom(RoomType.Camera, ShapeDimensions(300.0, 300.0), ceiling)
    }

    private fun EditorViewModel.level() = state.value.fullBuilding.floor.levelHeight
    private fun EditorViewModel.stateLevel() = state.value.levelHeight

    @Test
    fun `automatico, le stanze standard danno 300 e gli altri valori seguono`() {
        for ((ceiling, level) in listOf(270.0 to 300.0, 280.0 to 310.0, 250.0 to 280.0, 300.0 to 330.0)) {
            val vm = vm(auto(room(1, 270.0)))
            vm.setCeilingHeight(1, ceiling)
            assertEquals(level, vm.level(), "stanza $ceiling")
            assertEquals(level, vm.stateLevel(), "stanza $ceiling (stato)")
        }
    }

    @Test
    fun `un progetto nuovo e un piano vuoto sono automatici e partono da 300`() {
        val b = Building.single(FloorPlan(), autoLevel = true)
        assertTrue(b.floor.autoLevel)
        assertEquals(300.0, b.floor.levelHeight)
        assertEquals(300.0, Floor.autoLevelHeight(FloorPlan()))
    }

    @Test
    fun `con piu stanze vince la piu alta anche quando cambia`() {
        val vm = vm(auto(room(1, 270.0), room(2, 250.0, dx = 500.0)))
        assertEquals(300.0, vm.level())
        vm.setCeilingHeight(2, 320.0)
        assertEquals(350.0, vm.level())
        vm.setCeilingHeight(2, 240.0)
        assertEquals(300.0, vm.level())
        vm.setCeilingHeight(1, 260.0)
        assertEquals(290.0, vm.level()) // la più alta ora è la prima: 260
        vm.setCeilingHeight(1, 230.0)
        assertEquals(270.0, vm.level()) // 240 + 30
    }

    @Test
    fun `i balconi non contano`() {
        val vm = vm(auto(room(1, 270.0), room(2, 400.0, type = RoomType.Balcone, dx = 500.0)))
        assertEquals(300.0, vm.level())
        vm.setCeilingHeight(2, 500.0)
        assertEquals(300.0, vm.level())
        assertEquals(300.0, Floor.autoLevelHeight(FloorPlan(listOf(room(2, 500.0, type = RoomType.Balcone)))))
    }

    @Test
    fun `creare, eliminare e unire stanze riallinea l'interpiano`() {
        val vm = vm(auto(room(1, 270.0)))
        vm.addRoomWithCeiling(300.0)
        assertEquals(330.0, vm.level())
        val created = vm.state.value.plan.rooms.maxByOrNull { it.id }!!
        vm.deleteRoom(created.id)
        assertEquals(300.0, vm.level())

        val merge = vm(auto(room(1, 250.0), room(2, 300.0, dx = 400.0)))
        assertEquals(330.0, merge.level())
        merge.mergeWall(MergeRequest(1, 1, 2))
        val rooms = merge.state.value.plan.rooms
        assertEquals(1, rooms.size)
        assertEquals(rooms.maxOf { it.ceilingHeight } + Floor.SLAB, merge.level())
    }

    @Test
    fun `manuale, cambiare l'altezza della stanza non muove l'interpiano`() {
        val vm = vm(manual(300.0, room(1, 270.0)))
        vm.setCeilingHeight(1, 320.0)
        assertEquals(300.0, vm.level())
        assertEquals(300.0, vm.stateLevel())
        vm.addRoomWithCeiling(350.0)
        assertEquals(2, vm.state.value.plan.rooms.size)
        assertEquals(300.0, vm.level())
    }

    @Test
    fun `da manuale ad automatico ricalcola, da automatico a manuale blocca il valore`() {
        val vm = vm(manual(345.0, room(1, 280.0)))
        vm.updateFloor(0, "Piano terra", 345.0, autoLevel = true)
        assertTrue(vm.state.value.fullBuilding.floor.autoLevel)
        assertEquals(310.0, vm.level())
        vm.updateFloor(0, "Piano terra", 310.0, autoLevel = false)
        vm.setCeilingHeight(1, 300.0)
        assertFalse(vm.state.value.fullBuilding.floor.autoLevel)
        assertEquals(310.0, vm.level())
        vm.updateFloor(0, "Piano terra", 400.0, autoLevel = false)
        assertEquals(400.0, vm.level())
    }

    @Test
    fun `un piano nuovo automatico parte dalle stanze copiate`() {
        val vm = vm(auto(room(1, 280.0)))
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        val b = vm.state.value.fullBuilding
        assertEquals(1, b.current)
        assertTrue(b.floor.autoLevel)
        assertEquals(310.0, b.floor.levelHeight)
        assertEquals(310.0, b.floors[0].levelHeight) // il piano di sotto non cambia
        vm.addFloor("Mansarda", 333.0, copyRooms = false, autoLevel = true)
        assertEquals(300.0, vm.level()) // piano vuoto: 270 + 30
        vm.addFloor("Cantina", 333.0, copyRooms = false) // senza il parametro: manuale, come prima
        assertEquals(333.0, vm.level())
        assertFalse(vm.state.value.fullBuilding.floor.autoLevel)
    }

    @Test
    fun `la nuova scala sale dell'interpiano risultante`() {
        for ((ceiling, level) in listOf(270.0 to 300.0, 280.0 to 310.0, 300.0 to 330.0)) {
            val vm = vm(auto(room(1, ceiling)))
            vm.addStair(StairKind.Straight)
            val stair = vm.state.value.plan.stairs.single()
            assertEquals(level, vm.stateLevel())
            val layout = Stairs.layout(stair, vm.stateLevel())
            // L'ultima alzata porta al pavimento di sopra: ultimo gradino + alzata = interpiano.
            assertEquals(level, layout.steps.last().top + layout.riser, 1e-9, "stanza $ceiling")
        }
    }

    @Test
    fun `una scala esistente in un piano manuale non cambia`() {
        val vm = vm(manual(300.0, room(1, 270.0)))
        vm.addStair(StairKind.Straight)
        val before = vm.state.value.plan.stairs.single()
        vm.setCeilingHeight(1, 320.0)
        assertEquals(before, vm.state.value.plan.stairs.single())
        assertEquals(300.0, vm.stateLevel())
    }

    @Test
    fun `la quota dei piani resta coerente`() {
        val vm = vm(auto(room(1, 270.0)))
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        vm.addFloor("Secondo piano", 300.0, copyRooms = true, autoLevel = true)
        vm.selectFloor(0)
        vm.setCeilingHeight(1, 280.0)
        val b = vm.state.value.fullBuilding
        assertEquals(listOf(310.0, 300.0, 300.0), b.floors.map { it.levelHeight })
        assertEquals(0.0, b.elevation(0))
        assertEquals(310.0, b.elevation(1))
        assertEquals(610.0, b.elevation(2))
    }

    @Test
    fun `i file vecchi senza il campo sono manuali e non cambiano`() {
        val old = """{"version":2,"building":{"floors":[{"id":1,"name":"Piano terra","plan":{"rooms":[{"id":1,"name":"Sala","type":"Soggiorno","points":[{"x":0.0,"y":0.0},{"x":400.0,"y":0.0},{"x":400.0,"y":300.0},{"x":0.0,"y":300.0}],"ceilingHeight":290.0}]},"levelHeight":345.0}]}}"""
        val b = PlanJson.decodeBuilding(old)
        assertFalse(b.floor.autoLevel)
        assertEquals(345.0, b.floor.levelHeight)
        val vm = vm(b)
        vm.setCeilingHeight(1, 250.0)
        assertEquals(345.0, vm.level())
        // Senza `levelHeight` nel file resta il default, anch'esso manuale.
        val noLevel = PlanJson.decodeBuilding(old.replace(""","levelHeight":345.0""", ""))
        assertFalse(noLevel.floor.autoLevel)
        assertEquals(Floor.DEFAULT_LEVEL_HEIGHT, noLevel.floor.levelHeight)
        assertEquals(b.floor.plan, PlanJson.decodeBuilding(PlanJson.encode(b)).floor.plan)
    }

    @Test
    fun `salvataggio e riapertura conservano la modalita e il valore`() {
        val a = vm(auto(room(1, 280.0))).state.value.fullBuilding
        assertTrue(a.floor.autoLevel)
        assertEquals(310.0, a.floor.levelHeight)
        assertEquals(a, PlanJson.decodeBuilding(PlanJson.encode(a)))
        val m = manual(345.0, room(1, 280.0))
        val back = PlanJson.decodeBuilding(PlanJson.encode(m))
        assertEquals(m, back)
        assertFalse(back.floor.autoLevel)
    }

    @Test
    fun `annulla e ripristina riportano anche l'interpiano`() {
        val vm = vm(auto(room(1, 270.0)))
        vm.setCeilingHeight(1, 300.0)
        assertEquals(330.0, vm.level())
        vm.undo()
        assertEquals(270.0, vm.state.value.plan.room(1)!!.ceilingHeight)
        assertEquals(300.0, vm.level())
        assertEquals(300.0, vm.stateLevel())
        vm.redo()
        assertEquals(330.0, vm.level())
        assertEquals(330.0, vm.stateLevel())
        // Anche il passaggio da automatico a manuale si annulla.
        vm.updateFloor(0, "Piano terra", 330.0, autoLevel = false)
        vm.undo()
        assertTrue(vm.state.value.fullBuilding.floor.autoLevel)
    }

    @Test
    fun `scenario completo, abbassare la stanza del piano 0 sposta i piani sopra e adatta la scala`() {
        val vm = vm(auto(room(1, 270.0)))
        vm.addStair(StairKind.Straight)
        val stair = vm.state.value.plan.stairs.single()
        assertEquals(null, stair.risers) // scala automatica: il numero di alzate segue il dislivello
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        vm.addFloor("Secondo piano", 300.0, copyRooms = true, autoLevel = true)
        vm.selectFloor(0)

        val before = vm.state.value.fullBuilding
        assertEquals(300.0, before.floors[0].levelHeight)
        assertEquals(300.0, before.elevation(1))
        assertEquals(600.0, before.elevation(2))
        val layoutBefore = Stairs.layout(stair, vm.stateLevel())
        assertEquals(17, layoutBefore.risers)

        vm.setCeilingHeight(1, 250.0)

        val after = vm.state.value.fullBuilding
        assertEquals(280.0, after.floors[0].levelHeight)
        assertEquals(280.0, vm.stateLevel())
        assertEquals(280.0, after.elevation(1)) // nessun vuoto di 20 cm tra piano 0 e piano 1
        assertEquals(580.0, after.elevation(2))
        assertEquals(listOf(300.0, 300.0), after.floors.drop(1).map { it.levelHeight }) // gli altri interpiani non cambiano
        // La scala non ha quote salvate: è la stessa, e usa il nuovo dislivello.
        assertEquals(stair, vm.state.value.plan.stairs.single())
        val layout = Stairs.layout(stair, vm.stateLevel())
        assertEquals(16, layout.risers) // ricalcolate: ceil(280 / 18)
        assertEquals(280.0 / 16, layout.riser, 1e-9)
        assertEquals(280.0, layout.steps.last().top + layout.riser, 1e-9) // ultimo gradino + alzata = dislivello

        vm.undo()
        val undone = vm.state.value.fullBuilding
        assertEquals(270.0, vm.state.value.plan.room(1)!!.ceilingHeight)
        assertEquals(300.0, undone.floors[0].levelHeight)
        assertEquals(300.0, undone.elevation(1))
        assertEquals(600.0, undone.elevation(2))
        assertEquals(300.0, vm.stateLevel())
        assertEquals(17, Stairs.layout(vm.state.value.plan.stairs.single(), vm.stateLevel()).risers)
    }
}
