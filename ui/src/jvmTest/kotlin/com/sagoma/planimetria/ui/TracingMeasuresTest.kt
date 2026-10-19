package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.UnderlayTool
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Misure dopo il ricalco di una piantina vera: pixel misurati sull'immagine di una pianta di progetto 1170×2026 con le quote
 * interne (camera doppia 3,7 × 3,2 m: filo interno dei muri a x 185 e 557, y 350 e 675). Si tara sulla quota 3,7 e si
 * cliccano i quattro angoli interni: le misure della stanza devono essere quelle della piantina, non 15 cm in meno.
 */
class TracingMeasuresTest {
    private fun emptyProject() = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(), autoLevel = true)
        override fun save(building: Building) {}
    })

    /** Come la finestra dello sfondo: immagine larga 12 m sul lato lungo, centrata. */
    private val start = Underlay("p14.jpg", 1170, 2026, origin = Vec2(-300.0, -500.0), cmPerPx = 1200.0 / 2026)

    private fun EditorViewModel.px(x: Double, y: Double): Vec2 {
        val u = state.value.plan.underlay!!
        return u.origin + Vec2(x, y) * u.cmPerPx
    }

    private fun tracedRoom(wallThickness: Double): List<Double> = traced(wallThickness).state.value.plan.rooms.single().interiorLengths()

    private fun traced(wallThickness: Double): EditorViewModel {
        val vm = emptyProject()
        vm.startTracing()
        vm.setUnderlay(start)
        vm.setUnderlayTool(UnderlayTool.Calibrate())
        vm.underlayTap(vm.px(185.0, 500.0))
        vm.underlayTap(vm.px(557.0, 500.0))
        vm.calibrateUnderlay(370.0)
        for ((x, y) in listOf(185.0 to 350.0, 557.0 to 350.0, 557.0 to 675.0, 185.0 to 675.0, 185.0 to 350.0)) vm.drawWallsTap(vm.px(x, y))
        vm.finishDrawWalls(RoomType.entries.first(), 270.0, wallThickness)
        return vm
    }

    @Test
    fun `gli angoli cliccati sulla piantina sono il filo interno dei muri`() {
        val l = tracedRoom(15.0)
        assertEquals(370.0, l[0], 0.5)
        assertEquals(370.0 * 325 / 372, l[1], 0.5) // 323 cm: sulla piantina 3,2 (arrotondato)
        assertEquals(370.0, l[2], 0.5)
        assertEquals(370.0 * 325 / 372, l[3], 0.5)
    }

    @Test
    fun `lo spessore dei muri non cambia le misure interne`() {
        tracedRoom(15.0).zip(tracedRoom(30.0)).forEach { (a, b) -> assertEquals(a, b, 1e-6) }
    }

    @Test
    fun `la stanza accanto non si aggancia alla mezzeria di quella gia fatta`() {
        val vm = traced(15.0)
        vm.startDrawWalls()
        // Angolo del bagno sotto la camera, oltre il muro: l'angolo in mezzeria della camera è a 8 cm.
        val corner = vm.px(185.0, 685.0)
        vm.drawWallsTap(corner)
        val wd = vm.state.value.wallDraw!!
        assertEquals(corner, wd.points.single())
        assertEquals(true, wd.onPlan)
    }

    @Test
    fun `senza piantina gli angoli restano la mezzeria`() {
        val vm = emptyProject()
        vm.startDrawWalls()
        for (p in listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0), Vec2(0.0, 0.0))) vm.drawWallsTap(p)
        vm.finishDrawWalls(RoomType.entries.first(), 270.0, 15.0)
        assertEquals(listOf(385.0, 285.0, 385.0, 285.0), vm.state.value.plan.rooms.single().interiorLengths().map { kotlin.math.round(it * 1e6) / 1e6 })
    }

    @Test
    fun `rifai taratura a meta stanza - gli angoli gia messi seguono la piantina`() {
        val vm = emptyProject()
        vm.startTracing()
        vm.setUnderlay(start)
        vm.setUnderlayTool(UnderlayTool.Calibrate())
        vm.underlayTap(vm.px(185.0, 500.0))
        vm.underlayTap(vm.px(557.0, 500.0))
        vm.calibrateUnderlay(300.0) // misura sbagliata
        vm.drawWallsTap(vm.px(185.0, 350.0))
        vm.drawWallsTap(vm.px(557.0, 350.0))

        vm.recalibrateUnderlay()
        assertEquals(UnderlayTool.Calibrate(), vm.state.value.underlayTool)
        assertEquals(2, vm.state.value.wallDraw!!.points.size, "la stanza a metà resta")
        vm.underlayTap(vm.px(185.0, 500.0))
        vm.underlayTap(vm.px(557.0, 500.0))
        vm.calibrateUnderlay(370.0)
        assertEquals(null, vm.state.value.underlayTool)
        val wd = vm.state.value.wallDraw!!
        assertEquals(vm.px(557.0, 350.0).x, wd.points[1].x, 1e-6)
        assertEquals(vm.px(557.0, 350.0).y, wd.points[1].y, 1e-6)

        for ((x, y) in listOf(557.0 to 675.0, 185.0 to 675.0, 185.0 to 350.0)) vm.drawWallsTap(vm.px(x, y))
        vm.finishDrawWalls(RoomType.entries.first(), 270.0, 15.0)
        val l = vm.state.value.plan.rooms.single().interiorLengths()
        assertEquals(370.0, l[0], 0.5)
        assertEquals(370.0 * 325 / 372, l[1], 0.5)
    }

    @Test
    fun `rifai taratura mostra la piantina nascosta`() {
        val vm = traced(15.0)
        vm.setUnderlayVisible(false)
        vm.recalibrateUnderlay()
        assertEquals(true, vm.state.value.plan.underlay!!.visible)
        assertEquals(UnderlayTool.Calibrate(), vm.state.value.underlayTool)
    }
}
