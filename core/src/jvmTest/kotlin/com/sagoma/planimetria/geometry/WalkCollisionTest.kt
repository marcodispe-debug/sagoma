package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Camminando nel 3D Marco (30/10) si è ritrovato su uno sgabello della cucina e non poteva più muoversi: ogni passo finiva ancora
 * dentro o vicino allo sgabello, quindi era rifiutato. Ora da un mobile in cui ci si ritrova (e da un muro troppo vicino) si esce
 * sempre, mentre entrarci o attraversare un muro resta vietato.
 */
class WalkCollisionTest {
    private val radius = 20.0
    private val stool = Furniture(1, "sgabello", Vec2(250.0, 355.0), width = 35.0, depth = 35.0, height = 88.0)
    private val bed = Furniture(2, "letto", Vec2(110.0, 150.0), width = 160.0, depth = 200.0, height = 60.0)
    private val scene = Scene3D.build(
        FloorPlan(listOf(Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(500.0, 400.0))), furniture = listOf(stool, bed)),
        null, null, ceilings = true,
    )

    @Test
    fun dallo_sgabello_si_esce_in_ogni_direzione() {
        val p = stool.center
        assertTrue(scene.blocked(p, radius), "sopra lo sgabello si è bloccati")
        for (k in 0 until 8) {
            val a = 2 * PI * k / 8
            val q = Vec2(p.x + cos(a) * 17, p.y + sin(a) * 17)
            assertTrue(scene.canWalk(p, q, radius), "passo verso ${k * 45}° rifiutato")
        }
    }

    @Test
    fun dentro_il_letto_si_va_dove_si_vuole_fino_a_uscirne() {
        assertTrue(scene.canWalk(Vec2(60.0, 150.0), Vec2(80.0, 150.0), radius), "verso il centro del letto")
        assertTrue(scene.canWalk(Vec2(180.0, 150.0), Vec2(200.0, 150.0), radius), "fuori dal letto")
        assertFalse(scene.canWalk(Vec2(200.0, 150.0), Vec2(180.0, 150.0), radius), "di nuovo dentro")
    }

    @Test
    fun sullo_sgabello_non_si_sale() {
        assertFalse(scene.canWalk(Vec2(250.0, 250.0), Vec2(250.0, 315.0), radius))
        assertTrue(scene.canWalk(Vec2(250.0, 250.0), Vec2(250.0, 290.0), radius))
        assertFalse(scene.canWalk(Vec2(300.0, 355.0), Vec2(200.0, 355.0), radius), "un passo lungo che lo scavalca")
    }

    @Test
    fun dal_muro_troppo_vicino_ci_si_allontana_o_si_scivola() {
        val near = Vec2(250.0, 20.0)
        assertTrue(scene.blocked(near, radius))
        assertTrue(scene.canWalk(near, Vec2(250.0, 30.0), radius), "allontanarsi dal muro")
        assertTrue(scene.canWalk(near, Vec2(260.0, 20.0), radius), "scivolare lungo il muro")
        assertFalse(scene.canWalk(near, Vec2(250.0, 15.0), radius), "avvicinarsi ancora al muro")
    }

    @Test
    fun il_muro_non_si_attraversa() {
        assertFalse(scene.canWalk(Vec2(250.0, 40.0), Vec2(250.0, -40.0), radius))
    }
}
