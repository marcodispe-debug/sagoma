package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import com.sagoma.planimetria.geometry.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tastiera e mouse nella vista 3D (computer). */
class View3DControlsTest {
    @Test
    fun `dall'alto gli stessi comandi della camminata`() {
        val v = View3DState()
        // ← gira come camminando.
        val yaw = v.camera.yaw
        v.keys += Key.DirectionLeft
        v.step(1.0)
        assertEquals(yaw + 90.0, v.camera.yaw, 1e-9)
        v.keys.clear()
        // ↑ va avanti nella direzione in cui si guarda, restando sul pavimento; l'inclinazione non cambia.
        val pitch = v.camera.pitch
        val target = v.camera.target
        val ahead = Vec3(v.camera.forward.x, 0.0, v.camera.forward.z).normalized()
        v.keys += Key.DirectionUp
        v.step(0.5)
        assertEquals(pitch, v.camera.pitch, 1e-9)
        assertTrue((v.camera.target - target).dot(ahead) > 100.0)
        assertEquals(0.0, v.camera.target.y, 1e-9)
        v.keys.clear()
        // PagSu alza lo sguardo (vista più orizzontale).
        v.keys += Key.PageUp
        v.step(0.2)
        assertEquals(pitch - 9.0, v.camera.pitch, 1e-9)
        v.keys.clear()
        // Il joystick sposta di lato.
        val t2 = v.camera.target
        v.joystick = Offset(1f, 0f)
        assertTrue(v.moving)
        v.step(0.5)
        assertTrue((v.camera.target - t2).length > 100.0)
    }

    @Test
    fun `camminando le frecce fanno avanzare e girare, senza attraversare i muri`() {
        val v = View3DState()
        v.camera.mode = com.sagoma.planimetria.geometry.Camera3D.Mode.Walk
        v.camera.eye = Vec3(0.0, 160.0, 0.0)
        v.camera.walkYaw = 0.0
        v.startWalkingForTest()
        v.keys += Key.DirectionUp
        v.step(1.0)
        assertEquals(-170.0, v.camera.eye.z, 1e-6) // avanti = verso −z con yaw 0
        v.keys.clear()
        v.keys += Key.DirectionRight
        v.step(1.0)
        assertEquals(-90.0, v.camera.walkYaw, 1e-9)
        // Un muro davanti blocca il passo.
        v.keys.clear()
        v.isBlocked = { true }
        val eye = v.camera.eye
        v.keys += Key.DirectionUp
        v.step(1.0)
        assertEquals(eye, v.camera.eye)
    }

    @Test
    fun `A e D spostano di lato senza girare lo sguardo, dall'alto e camminando`() {
        val v = View3DState()
        val yaw = v.camera.yaw
        val t = v.camera.target
        val ahead = Vec3(v.camera.forward.x, 0.0, v.camera.forward.z).normalized()
        v.keys += Key.D
        v.step(0.5)
        assertEquals(yaw, v.camera.yaw, 1e-9)
        val moved = v.camera.target - t
        assertTrue(moved.length > 100.0)
        assertEquals(0.0, moved.dot(ahead), 1e-6) // di lato, non avanti
        v.keys.clear()
        v.camera.mode = com.sagoma.planimetria.geometry.Camera3D.Mode.Walk
        v.camera.eye = Vec3(0.0, 160.0, 0.0)
        v.camera.walkYaw = 0.0
        v.startWalkingForTest()
        v.keys += Key.A
        v.step(1.0)
        assertEquals(0.0, v.camera.walkYaw, 1e-9)
        assertEquals(-170.0, v.camera.eye.x, 1e-6) // a sinistra = verso −x con yaw 0
        assertEquals(0.0, v.camera.eye.z, 1e-6)
    }

    @Test
    fun `dall'alto si parte piano e in tre decimi di secondo si va alla velocità piena`() {
        val v = View3DState()
        v.keys += Key.W
        val steps = (1..30).map {
            val t = v.camera.target
            v.step(1.0 / 60)
            (v.camera.target - t).length
        }
        val full = v.camera.distance * 0.4 / 60
        assertTrue(steps.first() < full * 0.4, "primo passo ${steps.first()} su $full")
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a - 1e-9 }, "accelera senza strappi: $steps")
        assertEquals(full, steps.last(), 1e-9)
        // Fermi e si riparte: di nuovo piano.
        v.keys.clear()
        v.step(1.0 / 60)
        v.keys += Key.W
        val t = v.camera.target
        v.step(1.0 / 60)
        assertTrue((v.camera.target - t).length < full * 0.4)
    }

    @Test
    fun `il tasto destro sposta la vista parallela al pavimento`() {
        val v = View3DState()
        val t = v.camera.target
        v.panBy(Offset(100f, 0f), 800f)
        assertTrue(abs((v.camera.target - t).length) > 1.0)
        assertEquals(0.0, v.camera.target.y, 1e-9)
    }
}
