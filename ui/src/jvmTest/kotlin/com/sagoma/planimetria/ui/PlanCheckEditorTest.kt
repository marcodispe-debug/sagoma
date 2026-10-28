package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.PlanCheck
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** «Controlla la pianta» → «Mostra»: si va al piano del problema, si seleziona e si inquadra. */
class PlanCheckEditorTest {
    private val living = Room(
        1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 15.0,
        openings = listOf(Opening.default(1, OpeningKind.Door, 0, 200.0).copy(entrance = true), Opening.default(2, OpeningKind.Door, 1, 150.0)),
    )
    private val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }, wallThickness = 15.0)

    @Test
    fun `Mostra va al piano, seleziona e inquadra`() {
        // Al primo piano la cucina ha anche lei la porta verso il soggiorno: due porte nello stesso punto.
        val twice = kitchen.copy(openings = listOf(Opening.default(6, OpeningKind.Door, 3, 150.0)))
        val two = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(living, kitchen))), Floor(2, "Primo piano", FloorPlan(listOf(living, twice)))))
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = two
            override fun save(building: Building) {}
        })
        vm.setViewport(1000f, 800f, 40f)
        val issue = assertNotNull(PlanCheck.of(vm.state.value.fullBuilding).firstOrNull { it.kind == PlanCheck.Kind.DoubleOpening })
        assertEquals(1, issue.floor)
        vm.showIssue(issue)
        val s = vm.state.value
        assertEquals(1, s.building.current)
        assertEquals(Selection.Opening(2, 6), s.selection)
        assertEquals(2L, s.focusedRoomId)
        // La porta (x = 400, y = 150) sta al centro della vista, e la zona inquadrata è larga almeno 5 m.
        val c = s.camera
        assertEquals(500f, c.toScreenX(400.0), 1f)
        assertEquals(400f, c.toScreenY(150.0), 1f)
        assertEquals(true, 500.0 * c.scale <= 1000f)
        // Un problema senza oggetto da selezionare (stanza senza porta) mette a fuoco la stanza.
        vm.showIssue(PlanCheck.Issue(PlanCheck.Kind.NoDoor, 0, listOf(2L), issue.area))
        assertEquals(0, vm.state.value.building.current)
        assertEquals(2L, vm.state.value.focusedRoomId)
        assertEquals(null, vm.state.value.selection)
    }
}
