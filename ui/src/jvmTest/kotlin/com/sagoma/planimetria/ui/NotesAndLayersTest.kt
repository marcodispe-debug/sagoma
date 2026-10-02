package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.GroupItem
import com.sagoma.planimetria.geometry.GroupOps
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Layer
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Quote manuali, testi e livelli. */
class NotesAndLayersTest {
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))

    private fun vm() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(listOf(room), columns = listOf(Column(1, Vec2(250.0, 200.0)))))
        override fun save(building: Building) {}
    }).also { it.setViewport(1000f, 800f, 24f) }

    @Test
    fun `quota con due tocchi agganciati agli angoli`() {
        val vm = vm()
        vm.startDimension()
        vm.dimensionTap(Vec2(1.0, -1.0)) // vicino all'angolo (0,0)
        vm.dimensionTap(Vec2(498.0, 2.0)) // vicino all'angolo (500,0)
        val d = vm.state.value.plan.dimensions.single()
        assertEquals(Vec2(0.0, 0.0), d.a)
        assertEquals(Vec2(500.0, 0.0), d.b)
        assertEquals("500", d.label)
        // La linea va fuori dalla stanza (sopra, y < 0), non sul disegno.
        assertTrue(d.lineA.y < 0)
        // Si resta nello strumento per la quota successiva.
        assertNotNull(vm.state.value.dimensionDraw)
        vm.cancelDimension()
        // Trascinando la linea si allontana (a passi di 5 cm).
        vm.beginDrag(DragTarget.DimensionLine(d.id), d.lineA)
        vm.dragBy(DragTarget.DimensionLine(d.id), d.normal * (if (d.offset > 0) 22.0 else -22.0))
        vm.endDrag()
        assertEquals(60.0, kotlin.math.abs(vm.state.value.plan.dimensions.single().offset))
        assertEquals(Selection.Dimension(d.id), vm.state.value.selection)
        // Canc la elimina; Annulla la riporta.
        assertTrue(vm.deleteSelected())
        assertTrue(vm.state.value.plan.dimensions.isEmpty())
        vm.undo()
        assertEquals(1, vm.state.value.plan.dimensions.size)
    }

    @Test
    fun `testo con freccia, salvato e riletto, nei gruppi`() {
        val vm = vm()
        vm.startText()
        vm.textTap(Vec2(100.0, 100.0))
        assertEquals(Vec2(100.0, 100.0), vm.state.value.textDialogAt)
        vm.addAnnotation(Vec2(100.0, 100.0), "Nuovo parquet", 25.0)
        val t = vm.state.value.plan.annotations.single()
        assertEquals(Selection.Annotation(t.id), vm.state.value.selection)
        vm.updateAnnotation(t.copy(arrowTo = Vec2(200.0, 200.0)))
        val b = vm.state.value.fullBuilding
        assertEquals(b, PlanJson.decodeBuilding(PlanJson.encode(b)))
        // Nel gruppo: si sposta con il resto e si copia.
        val plan = vm.state.value.plan
        val moved = GroupOps.translate(plan, setOf(GroupItem.AnnotationItem(t.id)), Vec2(10.0, 0.0)).annotation(t.id)!!
        assertEquals(Vec2(110.0, 100.0), moved.at)
        assertEquals(Vec2(210.0, 200.0), moved.arrowTo)
        assertTrue(vm.duplicateSelected())
        assertEquals(2, vm.state.value.plan.annotations.size)
    }

    @Test
    fun `livelli nascosti non si selezionano e annulla non li cambia`() {
        val vm = vm()
        vm.startDimension(); vm.dimensionTap(Vec2(0.0, 0.0)); vm.dimensionTap(Vec2(500.0, 0.0)); vm.cancelDimension()
        vm.setLayerVisible(Layer.Structure, false)
        vm.setLayerLocked(Layer.Dimensions, true)
        vm.selectAll()
        val multi = vm.state.value.multi
        assertTrue(multi.none { it is GroupItem.ColumnItem || it is GroupItem.DimensionItem })
        assertTrue(multi.any { it is GroupItem.RoomItem })
        vm.clearMulti()
        // Annulla la quota: i livelli restano come sono (nascosti, bloccati).
        vm.undo()
        assertFalse(vm.state.value.building.layers.visible(Layer.Structure))
        assertTrue(Layer.Dimensions in vm.state.value.building.layers.locked)
        // E si salvano con il progetto.
        val b = vm.state.value.fullBuilding
        assertEquals(b.layers, PlanJson.decodeBuilding(PlanJson.encode(b)).layers)
    }
}
