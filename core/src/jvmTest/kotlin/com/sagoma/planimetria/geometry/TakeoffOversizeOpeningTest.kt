package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Computo con un'apertura scritta più grande del muro (es. 2100 invece di 210, 800 invece di 80): il vano non può essere
 * più largo del muro né più alto della parete. Prima si toglievano dalla pittura metri quadri che non esistono.
 * Stanza 400 × 300, alta 270, muri da 15 cm.
 */
class TakeoffOversizeOpeningTest {
    private fun line(vararg openings: Opening) =
        Takeoff.of(Building.single(FloorPlan(listOf(Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0), openings = openings.toList()))))).rooms.single()

    private val plain = line()

    @Test
    fun portaAltaPiuDellaParete() {
        val door = Opening.default(1, OpeningKind.Door, 1, 150.0).copy(height = 2100.0) // muro da 300 cm
        val l = line(door)
        // Il vano è al massimo alto quanto la parete: 80 × 270.
        assertEquals(0.8 * 2.7, l.openingsArea, 1e-6)
        assertEquals(plain.wallArea - 0.8 * 2.7, l.wallArea, 1e-6)
    }

    @Test
    fun finestraPiuLargaDelMuro() {
        val window = Opening.default(1, OpeningKind.Window1, 1, 150.0).copy(width = 800.0) // muro da 300 cm
        val l = line(window)
        assertEquals(3.0 * 1.2, l.openingsArea, 1e-6)
    }

    @Test
    fun finestraConDavanzaleSopraIlSoffitto() {
        val window = Opening.default(1, OpeningKind.Window1, 1, 150.0).copy(sillHeight = 300.0)
        assertEquals(0.0, line(window).openingsArea, 1e-6)
        assertEquals(plain.wallArea, line(window).wallArea, 1e-6)
    }
}
