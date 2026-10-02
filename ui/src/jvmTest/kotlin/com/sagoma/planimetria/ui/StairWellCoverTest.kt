package com.sagoma.planimetria.ui

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pavimento del piano di sopra sopra i primi gradini: il vuoto nel solaio si accorcia. */
class StairWellCoverTest {
    private val rise = 300.0
    private val stair = Stair(1, StairKind.LTurn, Vec2(300.0, 300.0), wellRailing = StairRailing.Metal)

    @Test
    fun `coprire fino al pianerottolo accorcia il vuoto`() {
        val l = Stairs.layout(stair, rise)
        val landing = l.steps.indexOfFirst { it.landing }
        val covered = stair.copy(coveredSteps = landing + 1)
        val well = Stairs.well(covered, rise)
        fun area(pcs: List<List<Vec2>>) = pcs.sumOf { Polygon.area(it) }
        // Il vuoto è quello dei soli gradini dopo il pianerottolo.
        assertEquals(l.steps.drop(landing + 1).sumOf { Polygon.area(it.polygon) }, area(well), 1e-6)
        assertTrue(area(well) < area(Stairs.well(stair, rise)))
        // Il primo gradino e il pianerottolo non sono più nel vuoto.
        assertTrue(well.none { pc -> Polygon.contains(pc, Polygon.labelPoint(l.steps.first().polygon)) })
        assertTrue(well.none { pc -> Polygon.contains(pc, Polygon.labelPoint(l.steps[landing].polygon)) })
        // Altezza libera sopra il pianerottolo: interpiano − solaio − quota del pianerottolo.
        assertEquals(rise - Floor.SLAB - l.steps[landing].top, Stairs.headroom(covered, rise, Floor.SLAB)!!, 1e-9)
        assertNull(Stairs.headroom(stair, rise, Floor.SLAB))
        // Non si possono coprire tutti i gradini: gli ultimi due restano scoperti.
        assertEquals(Stairs.maxCovered(l), Stairs.coveredOf(stair.copy(coveredSteps = 999), l))
        // La ringhiera attorno al vuoto segue il vuoto più corto (meno ringhiera) e il 3D si costruisce.
        fun railLen(s: Stair) = Stairs.wellRailingRuns(s, rise, emptyList()).sumOf { (a, b) -> a.distanceTo(b) }
        assertTrue(railLen(covered) < railLen(stair))
        Scene3D.build(FloorPlan(), null, null, ceilings = true, levelHeight = rise, below = listOf(Floor(1, "Piano terra", FloorPlan(stairs = listOf(covered)), rise)))
    }
}
