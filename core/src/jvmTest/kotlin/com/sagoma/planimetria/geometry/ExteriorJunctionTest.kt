package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Muro esterno che continua in un'altra stanza girando: il bagno col muro di fuori storto e l'ingresso col muro dritto,
 * dallo stesso angolo (il progetto «Prova della guida» di Marco). Guardando la facciata non ci sono scalini né fessure.
 */
class ExteriorJunctionTest {
    private fun bagno(cornerY: Double) = Room(
        2, "Bagno", RoomType.Bagno,
        listOf(Vec2(-206.1, 83.08), Vec2(68.9, 83.08), Vec2(68.9, 316.78), Vec2(-206.1, cornerY)),
    )

    private val ingresso = Room(
        5, "Stanza 2", RoomType.Altro,
        listOf(
            Vec2(-656.1, 213.08), Vec2(-346.1, 213.08), Vec2(-346.1, 83.08), Vec2(-206.1, 83.08),
            Vec2(-206.1, 371.33), Vec2(-656.1, 371.33),
        ),
    )

    /** Dove un raggio orizzontale, da fuori verso la casa, incontra la facciata a `x` (y in pianta). */
    private fun facadeAt(scene: Scene3D, x: Double): Double {
        val hit = assertNotNull(scene.hit(Vec3(x, 150.0, 1000.0), Vec3(0.0, 0.0, -1.0)), "nessun muro a x = $x")
        return hit.point.z
    }

    private fun assertSmoothFacade(cornerY: Double) {
        val scene = Scene3D.build(FloorPlan(listOf(bagno(cornerY), ingresso)), null, null, ceilings = false)
        var x = -240.0
        var prev = facadeAt(scene, x)
        while (x < -180.0) {
            x += 0.5
            val y = facadeAt(scene, x)
            // Le facce sono quasi orizzontali (il muro del bagno scende di 0,19 cm per cm): fra due raggi vicini al
            // massimo un decimo di cm; uno scalino o una fessura fanno saltare di centimetri.
            assertTrue(abs(y - prev) < 0.3, "salto di ${y - prev} cm nella facciata a x = $x")
            assertTrue(y > 371.5, "facciata rientrata a y = $y, x = $x")
            prev = y
        }
    }

    @Test
    fun `angoli che combaciano`() = assertSmoothFacade(371.33)

    @Test
    fun `angolo del bagno spostato di un cm e mezzo`() = assertSmoothFacade(369.66)

    @Test
    fun `angoli vicini si saldano, la pianta no`() {
        val plan = FloorPlan(listOf(bagno(369.66), ingresso))
        val welded = CornerWeld.weld(plan)
        assertEquals(welded.rooms[0].points[3], welded.rooms[1].points[4])
        // Gli angoli già lontani restano dove sono.
        assertEquals(plan.rooms[1].points[0], welded.rooms[1].points[0])
        val far = FloorPlan(listOf(bagno(360.0), ingresso))
        assertEquals(far, CornerWeld.weld(far))
    }
}
