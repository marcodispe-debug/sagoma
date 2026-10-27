package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Progetti salvati rovinati (o scritti da altri programmi): si aprono, e 3D e quote non cadono. */
class DamagedProjectTest {
    private fun reopen(plan: FloorPlan) = PlanJson.decode(PlanJson.encode(plan))

    @Test
    fun portaSuUnMuroColNumeroNegativo() {
        val room = Room(
            1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0),
            openings = listOf(Opening(1, OpeningKind.Door, wallIndex = -2, position = 100.0, width = 80.0, height = 210.0), Opening(2, OpeningKind.Window1, wallIndex = 1, position = 150.0, width = 80.0, height = 120.0)),
            fixtures = listOf(Fixture(1, FixtureKind.Radiator, wallIndex = -1, position = 100.0, length = 80.0, height = 60.0)),
        )
        val plan = reopen(FloorPlan(listOf(room)))
        assertEquals(listOf(2L), plan.rooms[0].openings.map { it.id }, "resta solo la finestra su un muro vero")
        assertTrue(plan.rooms[0].fixtures.isEmpty())
        Scene3D.build(plan, null, null, ceilings = true)
        Dimensions.of(plan)
    }

    @Test
    fun dueStanzeConLoStessoNumero() {
        val a = Room(5, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0))
        // Stessa id, più muri (una L): le quote prendevano i muri dell'altra e cadevano.
        val b = Room(5, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(300.0, 0.0) } .let { pts -> pts.take(2) + Vec2(700.0, 150.0) + pts.drop(2) })
        val plan = reopen(FloorPlan(listOf(a, b)))
        assertEquals(2, plan.rooms.map { it.id }.toSet().size)
        assertEquals(5L, plan.rooms[0].id, "la prima tiene il suo numero")
        Dimensions.of(plan)
        Scene3D.build(plan, null, null, ceilings = true)
    }

    @Test
    fun muroConSpessoreNegativo() {
        val room = Room(
            1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0), wallThickness = -25.0, wallThicknesses = mapOf(1 to 0.0),
            openings = listOf(Opening(1, OpeningKind.Window1, wallIndex = 1, position = 150.0, width = 80.0, height = 120.0, splay = 10.0)),
        )
        val plan = reopen(FloorPlan(listOf(room)))
        assertTrue(plan.rooms[0].thicknesses.all { it > 0 }, "${plan.rooms[0].thicknesses}")
        Scene3D.build(plan, null, null, ceilings = true)
    }

    @Test
    fun balconeLungoCentoChilometri() {
        // Misura scritta in metri invece che in cm, moltiplicata ancora: milioni di montanti finivano la memoria.
        val balcony = Room(1, "Balcone", RoomType.Balcone, RoomFactory.rectangle(10_000_000.0, 120.0))
        val scene = Scene3D.build(FloorPlan(listOf(balcony)), null, null, ceilings = true)
        assertTrue(scene.opaque.size < 5_000_000, "${scene.opaque.size} valori nel 3D")
    }
}
