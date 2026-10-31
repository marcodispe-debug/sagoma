package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Due aperture una sopra l'altra in fondo a un muro curvo (per esempio dopo un incolla o un'unione di stanze): la
 * seconda non ha un suo pezzo dritto, e srotolando il muro si cercava un pezzo dopo l'ultimo angolo, quindi pianta e 3D
 * si fermavano con un errore. Trovato con la prova di resistenza.
 */
class CurvedWallOverlappingOpeningsTest {
    private val wall = 1 // muro di destra, da 300 cm
    private fun window(id: Long, position: Double) = Opening.default(id, OpeningKind.Window1, wall, position)

    private val room = Room(
        1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0),
        wallBulges = mapOf(wall to 60.0),
        openings = listOf(window(1, 280.0), window(2, 280.0)),
    )

    @Test
    fun srotolareNonSiFerma() {
        val expanded = room.expandCurves().room
        assertEquals(2, expanded.openings.size)
        assertTrue(expanded.openings.all { it.wallIndex < expanded.wallCount && it.width > 0 })
    }

    @Test
    fun anche3DEComputo() {
        val plan = FloorPlan(listOf(room))
        Scene3D.build(plan, null, null, ceilings = true)
        Takeoff.of(com.sagoma.planimetria.model.Building.single(plan))
    }
}
