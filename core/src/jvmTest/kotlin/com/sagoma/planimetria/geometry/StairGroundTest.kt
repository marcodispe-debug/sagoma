package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Marco (30/10): «nella camminata puoi fare fare le scale e cambiare piano?». Chi cammina poggia sul gradino sotto i piedi: le
 * scale del piano salgono, e dal piano di sopra quelle che arrivano da sotto scendono nel vano del pavimento.
 */
class StairGroundTest {
    private fun room(id: Long) = Room(id, "Stanza $id", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 700.0))
    /** Scala dritta di 300 cm (17 alzate da 17,6 cm, 16 pedate da 28): si sale da y = 574 verso y = 126. */
    private val stair = Stair(1, StairKind.Straight, Vec2(300.0, 350.0))
    private val ground = FloorPlan(listOf(room(1)), stairs = listOf(stair))
    private val upper = FloorPlan(listOf(room(2)))
    private val riser = 300.0 / 17

    @Test
    fun al_piano_della_scala_si_sale_gradino_per_gradino() {
        val scene = Scene3D.build(ground, null, null, ceilings = true, levelHeight = 300.0, above = Floor(2, "Primo", upper, 300.0))
        assertEquals(0.0, scene.groundAt(Vec2(100.0, 100.0)), 1e-9)
        assertEquals(riser, scene.groundAt(Vec2(300.0, 560.0)), 1e-6)
        assertEquals(10 * riser, scene.groundAt(Vec2(300.0, 300.0), 150.0), 1e-6)
        assertEquals(16 * riser, scene.groundAt(Vec2(300.0, 140.0), 270.0), 1e-6)
        // Accanto alla scala c'è il pavimento.
        assertEquals(0.0, scene.groundAt(Vec2(240.0, 300.0)), 1e-9)
    }

    @Test
    fun dal_piano_di_sopra_la_scala_scende_nel_vano() {
        val scene = Scene3D.build(upper, null, null, ceilings = true, levelHeight = 300.0, below = listOf(Floor(1, "Terra", ground, 300.0)))
        assertEquals(0.0, scene.groundAt(Vec2(100.0, 100.0)), 1e-9)
        // Nel vano non c'è pavimento: solo i gradini, sotto il piano.
        assertEquals(16 * riser - 300, scene.groundAt(Vec2(300.0, 140.0)), 1e-6)
        assertEquals(10 * riser - 300, scene.groundAt(Vec2(300.0, 300.0)), 1e-6)
        assertTrue(scene.groundAt(Vec2(300.0, 560.0)) < -250)
    }
}
