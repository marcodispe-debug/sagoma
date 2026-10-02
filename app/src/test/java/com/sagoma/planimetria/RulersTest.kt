package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Rulers
import com.sagoma.planimetria.model.Ruler
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RulersTest {

    private val horizontal = Ruler(1, Vec2(0.0, 0.0), Vec2(100.0, 0.0))

    private fun assertVec(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x, actual.x, 1e-6)
        assertEquals(expected.y, actual.y, 1e-6)
    }

    @Test
    fun `aggancio solo vicino ai multipli di 45`() {
        assertEquals(45.0, Rulers.snapAngle(47.0), 1e-9)
        assertEquals(0.0, Rulers.snapAngle(358.0), 1e-9)
        assertEquals(30.0, Rulers.snapAngle(30.0), 1e-9)
    }

    @Test
    fun `rotazione libera attorno al centro mantenendo la lunghezza`() {
        // Maniglia sulla perpendicolare: puntare a 120° (in coordinate schermo) → metro a 30°.
        val t = Math.toRadians(120.0)
        val r = Rulers.rotateToward(horizontal, horizontal.mid + Vec2(cos(t), sin(t)) * 40.0)
        assertEquals(100.0, r.length, 1e-6)
        assertVec(horizontal.mid, r.mid)
        assertVec(Vec2(cos(Math.toRadians(30.0)), sin(Math.toRadians(30.0))), r.direction)
    }

    @Test
    fun `rotazione vicino a 90 si aggancia esattamente`() {
        val t = Math.toRadians(90.0 + 92.0)
        val r = Rulers.rotateToward(horizontal, horizontal.mid + Vec2(cos(t), sin(t)) * 40.0)
        assertVec(Vec2(50.0, -50.0), r.start)
        assertVec(Vec2(50.0, 50.0), r.end)
    }

    @Test
    fun `angolo mostrato antiorario`() {
        assertEquals(90.0, Rulers.angleDeg(Ruler(1, Vec2(0.0, 0.0), Vec2(0.0, -10.0))), 1e-9)
        assertEquals(270.0, Rulers.angleDeg(Ruler(1, Vec2(0.0, 0.0), Vec2(0.0, 10.0))), 1e-9)
    }

    @Test
    fun `estremità si allungano lungo il metro senza ruotarlo`() {
        assertVec(Vec2(150.0, 0.0), Rulers.extend(horizontal, 1, Vec2(50.0, 30.0)).end)
        assertVec(Vec2(-20.0, 0.0), Rulers.extend(horizontal, 0, Vec2(-20.0, 5.0)).start)
        assertEquals(Ruler.MIN_LENGTH, Rulers.extend(horizontal, 1, Vec2(-500.0, 0.0)).length, 1e-9)
    }
}
