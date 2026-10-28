package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
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

    @Test
    fun `Correggi corregge sul suo piano e si annulla`() {
        // Al primo piano una finestra fino a 300 cm sul muro alto 270; si sta guardando il piano terra.
        val tall = living.copy(openings = living.openings + Opening(8, OpeningKind.Window1, 3, 150.0, 80.0, 200.0, sillHeight = 100.0))
        val two = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(living, kitchen))), Floor(2, "Primo piano", FloorPlan(listOf(tall, kitchen)))))
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = two
            override fun save(building: Building) {}
        })
        fun over() = PlanCheck.of(vm.state.value.fullBuilding).filter { it.kind == PlanCheck.Kind.OpeningOverWall }
        val issue = over().single()
        assertEquals(true, vm.fixIssue(issue))
        assertEquals(emptyList(), over())
        assertEquals(170.0, vm.state.value.fullBuilding.floors[1].plan.rooms[0].openings.single { it.id == 8L }.height)
        // Il piano terra è rimasto quello di prima, e lo si sta ancora guardando.
        assertEquals(0, vm.state.value.building.current)
        assertEquals(two.floors[0].plan.rooms, vm.state.value.fullBuilding.floors[0].plan.rooms)
        vm.undo()
        assertEquals(1, over().size)
        // Già corretto (o non più lì): false, e niente cambia.
        vm.redo()
        val after = vm.state.value.fullBuilding
        assertEquals(false, vm.fixIssue(issue))
        assertEquals(after, vm.state.value.fullBuilding)
    }

    @Test
    fun `Togli dalla pianta si salva nel progetto e si annulla`() {
        val overlapped = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(living, kitchen.copy(points = kitchen.points.map { it + Vec2(-100.0, 0.0) }))))))
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = overlapped
            override fun save(building: Building) {}
        })
        fun overlap() = PlanCheck.of(vm.state.value.fullBuilding).single { it.kind == PlanCheck.Kind.Overlap }
        fun marks() = checkMarks(PlanCheck.of(vm.state.value.fullBuilding), vm.state.value.fullBuilding, 0, vm.state.value.plan)
        val before = marks()
        assertEquals(true, before.any { m -> m.issues.any { it.kind == PlanCheck.Kind.Overlap } })
        vm.setCheckHidden(overlap(), true)
        assertEquals(true, PlanCheck.hidden(vm.state.value.fullBuilding, overlap()))
        assertEquals(false, marks().any { m -> m.issues.any { it.kind == PlanCheck.Kind.Overlap } })
        assertEquals(before.sumOf { it.issues.size } - 1, marks().sumOf { it.issues.size })
        // Nella casa (si salva con il progetto).
        assertEquals(1, vm.state.value.fullBuilding.hiddenChecks.size)
        vm.undo()
        assertEquals(false, PlanCheck.hidden(vm.state.value.fullBuilding, overlap()))
        vm.redo()
        assertEquals(true, PlanCheck.hidden(vm.state.value.fullBuilding, overlap()))
        vm.setCheckHidden(overlap(), false)
        assertEquals(emptySet(), vm.state.value.fullBuilding.hiddenChecks)
        // Tutto un gruppo insieme, e un ↶ solo lo rimette.
        val rules = PlanCheck.of(vm.state.value.fullBuilding).filter { it.kind.rule }
        assertEquals(true, rules.size > 1)
        vm.setChecksHidden(rules, true)
        assertEquals(rules.size, vm.state.value.fullBuilding.hiddenChecks.size)
        assertEquals(true, rules.all { PlanCheck.hidden(vm.state.value.fullBuilding, it) })
        vm.undo()
        assertEquals(emptySet(), vm.state.value.fullBuilding.hiddenChecks)
    }

    @Test
    fun `da lontano i segni vicini diventano uno`() {
        val b = Building.single(FloorPlan(listOf(living, kitchen.copy(points = kitchen.points.map { it + Vec2(-100.0, 0.0) }))))
        val marks = checkMarks(PlanCheck.of(b), b, 0, b.floors[0].plan)
        assertEquals(true, marks.size >= 2, "segni: ${marks.size}")
        val error = marks.first { it.issues.first().kind.error }
        val rule = marks.first { it.issues.first().kind.rule }
        // Il segno delle norme a 10 px da quello dell'errore, un terzo lontano: due segni, il primo nel punto dell'errore.
        val far = CheckMark(Vec2(9999.0, 0.0), false, rule.issues)
        val merged = mergeClose(listOf(rule to Offset(110f, 100f), error to Offset(100f, 100f), far to Offset(400f, 100f)), 28f)
        assertEquals(2, merged.size)
        val (one, at) = merged.first()
        assertEquals(Offset(100f, 100f), at)
        assertEquals(error.at, one.at)
        assertEquals(error.issues + rule.issues, one.issues)
        assertEquals(far, merged[1].first)
        // Abbastanza lontani: restano com'erano.
        assertEquals(listOf(error, rule), mergeClose(listOf(error to Offset(0f, 0f), rule to Offset(40f, 0f)), 28f).map { it.first })
    }
}
