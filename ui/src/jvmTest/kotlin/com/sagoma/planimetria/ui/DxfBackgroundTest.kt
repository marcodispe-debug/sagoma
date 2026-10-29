package com.sagoma.planimetria.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sagoma.planimetria.dxf.DxfReader
import com.sagoma.planimetria.dxf.PlanDxf
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.UnderlayTool
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pianta DXF come sfondo: immagine già in scala, al suo posto sopra la pianta, senza taratura. */
class DxfBackgroundTest {
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 30.0)
    private val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }, wallThickness = 30.0)
    private val plan = FloorPlan(listOf(living.copy(openings = listOf(Opening.default(1, OpeningKind.Door, 1, 150.0))), kitchen))
    private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    @Test
    fun `immagine in scala con i muri dove sono sulla pianta`() {
        val d = assertIs<DxfReader.Result.Ok>(DxfReader.read(PlanDxf.bytes(plan))).drawing
        val r = dxfRaster(d, measurer, 3000)
        File("build").mkdirs()
        File("build/prova-dxf-sfondo.png").writeBytes(SkiaImages.encodePng(r.image)!!)
        assertTrue(r.image.width <= 3000 && r.image.height <= 3000)
        assertEquals(0.5, r.cmPerPx, 1e-9, "disegno piccolo: mezzo centimetro per pixel")
        val px = r.image.toPixelMap()
        fun at(p: Vec2) = px[((p.x - r.origin.x) / r.cmPerPx).roundToInt(), ((p.y - r.origin.y) / r.cmPerPx).roundToInt()]
        // Sul filo esterno del muro di sopra c'è una linea scura; il muro è pieno, grigio; nella stanza è trasparente.
        assertTrue(at(Vec2(200.0, -15.0)).alpha > 0.5f && at(Vec2(200.0, -15.0)).red < 0.3f)
        assertTrue(at(Vec2(200.0, 0.0)).alpha > 0.9f && at(Vec2(200.0, 0.0)).red in 0.4f..0.7f)
        assertTrue(at(Vec2(100.0, 250.0)).alpha < 0.1f)
        // La scritta del nome al centro della cucina: qualche pixel scuro lì attorno.
        val c = Vec2(550.0, 133.0)
        var ink = 0
        for (dx in -60..60 step 2) for (dy in -12..12 step 2) if (at(c + Vec2(dx.toDouble(), dy.toDouble())).alpha > 0.5f) ink++
        assertTrue(ink > 10, "nome della cucina: $ink")
    }

    @Test
    fun `al suo posto se sta sopra la pianta, altrimenti da spostare`() {
        val d = assertIs<DxfReader.Result.Ok>(DxfReader.read(PlanDxf.bytes(plan))).drawing
        assertTrue(dxfOverPlan(d, Openings.planBounds(plan)))
        assertFalse(dxfOverPlan(d, null))
        val far = plan.copy(rooms = plan.rooms.map { r -> r.copy(points = r.points.map { it + Vec2(100_000.0, 0.0) }) })
        assertFalse(dxfOverPlan(d, Openings.planBounds(far)))
    }

    @Test
    fun `nell'editor - niente taratura, si sposta solo se serve`() {
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = Building.single(plan, autoLevel = true)
            override fun save(building: Building) {}
        })
        val u = Underlay("sfondo.png", 1500, 700, origin = Vec2(-120.0, -120.0), cmPerPx = 0.5, opacity = 0.6)
        vm.setScaledUnderlay(u, move = false)
        assertEquals(u, vm.state.value.plan.underlay)
        assertNull(vm.state.value.underlayTool)
        vm.setScaledUnderlay(u.copy(file = "altro.png"), move = true)
        assertEquals(UnderlayTool.Move, vm.state.value.underlayTool)
        // Un solo passo di Annulla per ogni sfondo caricato.
        vm.undo()
        assertEquals("sfondo.png", vm.state.value.plan.underlay?.file)
        assertTrue(abs(vm.state.value.plan.underlay!!.cmPerPx - 0.5) < 1e-9)
    }
}
