package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Muro eliminato: le misure dei muri accanto arrivano fin dove era, senza togliere il suo spessore. */
class RemovedWallMeasuresTest {
    private fun rect(id: Long, x: Double, w: Double) =
        Room(id, "R$id", RoomType.Altro, listOf(Vec2(x, 0.0), Vec2(x + w, 0.0), Vec2(x + w, 300.0), Vec2(x, 300.0)))

    private val living = rect(1, 0.0, 400.0).copy(removedWalls = setOf(1))
    private val kitchen = rect(2, 400.0, 300.0).copy(removedWalls = setOf(3))

    @Test
    fun `i muri accanto si misurano fino alla linea del muro eliminato`() {
        // Muri da 15: in alto e in basso solo il mezzo muro esterno; il lato aperto resta la luce tra i due muri.
        assertEquals(listOf(392.5, 285.0, 392.5, 285.0), living.interiorLengths())
        assertEquals(listOf(292.5, 285.0, 292.5, 285.0), kitchen.interiorLengths())
        // Le due stanze insieme fanno la luce dell'ambiente unico, come la catena fuori (715 - 15 - 15).
        assertEquals(685.0, living.interiorLengths()[0] + kitchen.interiorLengths()[0], 1e-9)
        // Con il muro al suo posto le misure non cambiano.
        assertEquals(listOf(385.0, 285.0, 385.0, 285.0), rect(1, 0.0, 400.0).interiorLengths())
    }

    @Test
    fun `i metri quadri contano anche dove c'era il muro`() {
        // Fino alla linea tratteggiata: 392,5 × 285 e 292,5 × 285, insieme come l'ambiente unico 685 × 285.
        assertEquals(392.5 * 285.0, living.interiorArea(), 1e-6)
        assertEquals(292.5 * 285.0, kitchen.interiorArea(), 1e-6)
        assertEquals(685.0 * 285.0, living.usableArea() + kitchen.usableArea(), 1e-6)
        assertEquals(385.0 * 285.0, rect(1, 0.0, 400.0).interiorArea(), 1e-6)
    }

    @Test
    fun `le quote in pianta scrivono le misure senza lo spessore del muro eliminato`() {
        val lines = Dimensions.of(FloorPlan(listOf(living, kitchen)))
        val top = lines.filter { kotlin.math.abs(it.a.y) < 1e-6 && kotlin.math.abs(it.b.y) < 1e-6 }.map { it.length }.sorted()
        assertEquals(listOf(292.5, 392.5), top)
        // Il lato aperto, uguale dalle due parti, si scrive una volta sola sulla linea tratteggiata.
        assertTrue(lines.count { it.onWall && kotlin.math.abs(it.length - 285.0) < 1e-6 } == 1)
        // Dentro le stanze lo stesso: la luce del lato aperto la scrive solo il soggiorno, che viene prima.
        val plan = FloorPlan(listOf(living, kitchen))
        assertFalse(Dimensions.openSideDrawnBefore(plan, living, 1, 285.0))
        assertTrue(Dimensions.openSideDrawnBefore(plan, kitchen, 3, 285.0))
        assertFalse(Dimensions.openSideDrawnBefore(plan, kitchen, 0, 292.5))
    }
}
