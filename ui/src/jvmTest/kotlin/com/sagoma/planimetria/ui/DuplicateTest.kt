package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** "⧉ Duplica" su ogni tipo di oggetto: copia con le stesse impostazioni, accanto, subito selezionata. */
class DuplicateTest {
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)).copy(
        openings = listOf(Opening.default(1, OpeningKind.Window2, 0, 100.0).copy(sillHeight = 95.0)),
        fixtures = listOf(Fixture(1, FixtureKind.Radiator, wallIndex = 1, position = 100.0, length = 80.0, height = 60.0, elevation = 15.0)),
    )
    private val balcony = Room(2, "Balcone", RoomType.Balcone, RoomFactory.rectangle(150.0, 300.0).map { it + Vec2(500.0, 0.0) })

    private fun vm(): EditorViewModel {
        val b = Building.single(FloorPlan(listOf(room, balcony), columns = listOf(Column(1, Vec2(700.0, 500.0)))))
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    @Test
    fun `calorifero duplicato sullo stesso muro con le stesse impostazioni`() {
        val vm = vm()
        vm.selectFixture(1, 1)
        assertTrue(vm.duplicateSelected())
        val fixtures = vm.state.value.plan.room(1)!!.fixtures
        assertEquals(2, fixtures.size)
        val copy = fixtures.last()
        assertEquals(1, copy.wallIndex)
        assertEquals(15.0, copy.elevation)
        assertNotEquals(fixtures.first().position, copy.position)
        assertEquals(Selection.Fixture(1, copy.id), vm.state.value.selection)
    }

    @Test
    fun `finestra, colonna, stanza e balcone`() {
        val vm = vm()
        vm.selectOpening(1, 1)
        vm.duplicateSelected()
        val windows = vm.state.value.plan.room(1)!!.openings
        assertEquals(2, windows.size)
        assertEquals(95.0, windows.last().sillHeight)

        vm.selectColumn(1)
        vm.duplicateSelected()
        assertEquals(2, vm.state.value.plan.columns.size)

        vm.focusRoom(1)
        vm.duplicateSelected()
        val rooms = vm.state.value.plan.rooms
        assertEquals(3, rooms.size)
        // La copia ha anche porte, finestre e impianti, e non si sovrappone alla casa.
        assertEquals(2, rooms.last().openings.size)
        assertTrue(rooms.last().points.minOf { it.x } > 650.0)

        vm.focusRoom(2)
        vm.duplicateSelected()
        assertEquals(RoomType.Balcone, vm.state.value.plan.rooms.last().type)
    }
}
