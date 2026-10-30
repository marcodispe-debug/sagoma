package com.sagoma.planimetria.ui

import androidx.compose.ui.input.key.Key
import com.sagoma.planimetria.geometry.Camera3D
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
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
 * Marco (30/10): «nella camminata puoi fare fare le scale e cambiare piano?». Camminando si sale la scala gradino per gradino, a
 * metà si passa al piano di sopra (la vista lo chiede al programma, che manda la scena nuova) e in cima si cammina sul suo
 * pavimento; tornando indietro si scende e si torna al piano di sotto.
 */
class StairWalkTest {
    private fun room(id: Long) = Room(id, "Stanza $id", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 700.0))
    private val ground = FloorPlan(listOf(room(1)), stairs = listOf(Stair(1, StairKind.Straight, Vec2(300.0, 350.0))))
    private val upper = FloorPlan(listOf(room(2)))
    private val groundScene = Scene3D.build(ground, null, null, ceilings = true, levelHeight = 300.0, above = Floor(2, "Primo", upper, 300.0))
    private val upperScene = Scene3D.build(upper, null, null, ceilings = true, levelHeight = 300.0, below = listOf(Floor(1, "Terra", ground, 300.0)))

    private val asked = mutableListOf<Int>()

    /** Come fa la vista quando cambia il piano: ostacoli, piani vicini e scena nuova. */
    private fun View3DState.onFloor(index: Int) {
        val scene = if (index == 0) groundScene else upperScene
        isBlocked = { scene.blocked(it, 20.0) }
        canWalk = { from, to -> scene.canWalk(from, to, 20.0) }
        floorHeight = 300.0
        floorAbove = index == 0
        floorBelowHeight = if (index == 1) 300.0 else null
        setScene(scene, index * 300.0)
    }

    private fun walker(): View3DState {
        val v = View3DState()
        v.onFloor(0)
        v.changeFloor = { asked += it }
        v.camera.mode = Camera3D.Mode.Walk
        v.startWalkingForTest()
        v.camera.eye = Vec3(300.0, Scene3D.EYE_HEIGHT, 640.0)
        v.camera.walkYaw = 0.0
        return v
    }

    @Test
    fun si_sale_al_piano_di_sopra_e_si_torna_giu() {
        val v = walker()
        v.keys += Key.W
        var frames = 0
        var highest = v.camera.eye.y
        while (asked.isEmpty() && frames < 600) { v.step(1.0 / 60); frames++; highest = maxOf(highest, v.camera.eye.y) }
        assertEquals(listOf(1), asked, "a metà scala si chiede il piano di sopra")
        assertTrue(highest > Scene3D.EYE_HEIGHT + 100, "gli occhi salgono con i gradini: $highest")
        // Arriva la scena del piano di sopra: stesso punto, misurato dal suo pavimento.
        val before = v.camera.eye
        v.onFloor(1)
        assertEquals(before.y - 300, v.camera.eye.y, 1e-9)
        assertEquals(before.z, v.camera.eye.z, 1e-9)
        repeat(240) { v.step(1.0 / 60) }
        assertTrue(v.camera.eye.z < 126, "in cima si esce dalla scala: ${v.camera.eye}")
        assertEquals(Scene3D.EYE_HEIGHT, v.camera.eye.y, 1e-6)
        assertEquals(listOf(1), asked)

        // Si torna indietro: giù per la scala, fino al piano di sotto.
        v.camera.walkYaw = 180.0
        frames = 0
        while (asked.size < 2 && frames < 600) { v.step(1.0 / 60); frames++ }
        assertEquals(listOf(1, -1), asked)
        val mid = v.camera.eye
        v.onFloor(0)
        assertEquals(mid.y + 300, v.camera.eye.y, 1e-9)
        repeat(240) { v.step(1.0 / 60) }
        v.keys.clear()
        repeat(60) { v.step(1.0 / 60) }
        assertTrue(v.camera.eye.z > 574, "in fondo si esce dalla scala: ${v.camera.eye}")
        assertEquals(Scene3D.EYE_HEIGHT, v.camera.eye.y, 1e-6)
    }

    @Test
    fun dal_fianco_la_scala_non_si_sale() {
        val v = walker()
        v.camera.eye = Vec3(200.0, Scene3D.EYE_HEIGHT, 300.0)
        v.walkMove(Vec3(100.0, 0.0, 0.0))
        assertTrue(v.camera.eye.x < 255, "dentro il fianco della scala: ${v.camera.eye}")
        assertEquals(Scene3D.EYE_HEIGHT, v.camera.eye.y, 1e-9)
    }

    @Test
    fun cambiando_piano_a_mano_si_cammina_sul_suo_pavimento() {
        val v = walker()
        v.onFloor(1)
        assertEquals(Scene3D.EYE_HEIGHT, v.camera.eye.y, 1e-9)
        assertEquals(emptyList(), asked)
    }
}
