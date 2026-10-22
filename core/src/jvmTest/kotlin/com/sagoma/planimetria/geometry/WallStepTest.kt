package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Muro che cambia spessore a metà: il lato destro spezzato in due tratti, uno da 15 e uno da 55 cm. */
class WallStepTest {
    // Stanza 400×300 in mezzeria; il muro destro (x = 400) diviso a y = 100: da 0 a 100 spesso 15, da 100 a 300 spesso 55.
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0)).splitWall(1)
        .let { it.copy(wallThicknesses = mapOf(2 to 55.0)) }
        .let { r -> r.copy(points = r.points.mapIndexed { i, p -> if (i == 2) Vec2(400.0, 100.0) else p }) }

    @Test
    fun `il contorno fa lo scalino`() {
        val outer = room.exteriorOutline()
        val inner = room.interiorOutline()
        // Due punti in più (uno fuori, uno dentro) nel vertice dello scalino.
        assertEquals(room.wallCount + 1, outer.size)
        assertEquals(room.wallCount + 1, inner.size)
        assertTrue(outer.any { it.distanceTo(Vec2(407.5, 100.0)) < 1e-6 })
        assertTrue(outer.any { it.distanceTo(Vec2(427.5, 100.0)) < 1e-6 })
        assertTrue(inner.any { it.distanceTo(Vec2(392.5, 100.0)) < 1e-6 })
        assertTrue(inner.any { it.distanceTo(Vec2(372.5, 100.0)) < 1e-6 })
    }

    @Test
    fun `misure interne con lo scalino`() {
        // Interno: x da 7,5 a 392,5 (sopra lo scalino) e a 372,5 (sotto), y da 7,5 a 292,5.
        val expected = 385.0 * 92.5 + 365.0 * 192.5
        assertEquals(expected, room.interiorArea(), 1e-6)
        val l = room.interiorLengths()
        assertEquals(92.5, l[1], 1e-6)
        assertEquals(192.5, l[2], 1e-6)
        assertEquals(365.0, l[3], 1e-6)
        // Il 3D si costruisce.
        Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
    }
}

/** Finestre con sguincio e infisso verso l'esterno, porta d'ingresso, misure in metri. */
class OpeningDetailsTest {
    private val window = com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Window2, 0, 200.0)
        .copy(frame = com.sagoma.planimetria.model.FramePosition.Outside, splay = 10.0)
    private val door = com.sagoma.planimetria.model.Opening.default(2, com.sagoma.planimetria.model.OpeningKind.Door, 2, 200.0).copy(entrance = true)
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 50.0)
        .copy(openings = listOf(window, door))

    @Test
    fun `infisso a filo esterno e sguincio`() {
        // Muro da 50: l'infisso sta a 25 − 6 = 19 cm dalla mezzeria verso l'esterno.
        assertEquals(-19.0, Openings.frameOffset(room, window), 1e-9)
        assertEquals(10.0, Openings.splayOf(room, window), 1e-9)
        // Le porte restano a metà muro e senza sguincio.
        assertEquals(0.0, Openings.frameOffset(room, door.copy(frame = com.sagoma.planimetria.model.FramePosition.Outside)), 1e-9)
        assertEquals(0.0, Openings.splayOf(room, door.copy(splay = 10.0)), 1e-9)
        // Si salva e si rilegge uguale; il 3D si costruisce.
        val b = com.sagoma.planimetria.model.Building.single(FloorPlan(listOf(room)))
            .copy(units = com.sagoma.planimetria.model.LengthUnit.Meters)
        val json = com.sagoma.planimetria.persistence.PlanJson.encode(b)
        assertEquals(b, com.sagoma.planimetria.persistence.PlanJson.decodeBuilding(json))
        Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
    }

    @Test
    fun `misure in metri`() {
        val p = com.sagoma.planimetria.model.PlanUnits
        val before = p.unit
        try {
            p.unit = com.sagoma.planimetria.model.LengthUnit.Meters
            assertEquals("5,03", p.format(503.0).replace('.', ','))
            assertEquals("0,75 m", p.withUnit(75.0).replace('.', ','))
            p.unit = com.sagoma.planimetria.model.LengthUnit.Centimeters
            assertEquals("503 cm", p.withUnit(503.0))
        } finally {
            p.unit = before
        }
    }
}
