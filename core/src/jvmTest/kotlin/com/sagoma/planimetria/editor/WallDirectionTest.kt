package com.sagoma.planimetria.editor

import com.sagoma.planimetria.model.Vec2
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Freccia + angolo TRA I DUE MURI nel "muro per muro" (y verso il basso, come sulla pianta). */
class WallDirectionTest {
    private val right = Vec2(1.0, 0.0)

    private fun dir(prev: Vec2?, arrow: WallArrow, deg: Double?): Vec2 = assertIs<WallDirection.Result.Ok>(WallDirection.of(prev, arrow, deg)).dir

    private fun near(expected: Vec2, actual: Vec2) =
        assertTrue(expected.distanceTo(actual) < 1e-9, "atteso $expected, ottenuto $actual")

    /** Angolo nell'angolo della stanza: tra il muro precedente percorso all'indietro e il nuovo. */
    private fun cornerAngle(prev: Vec2, next: Vec2) = acos((prev * -1.0).normalized() dot next.normalized()) * 180 / Math.PI

    @Test
    fun `senza angolo il muro va dritto verso la freccia`() {
        for (a in WallArrow.entries) near(a.dir, dir(null, a, null)) // primo muro
        near(Vec2(0.0, 1.0), dir(right, WallArrow.DOWN, null))
        near(Vec2(1.0, 0.0), dir(right, WallArrow.RIGHT, null)) // prosegue dritto
    }

    @Test
    fun `per il primo muro l'angolo non conta`() {
        near(WallArrow.DOWN.dir, dir(null, WallArrow.DOWN, 70.0))
    }

    @Test
    fun `90 gradi e in squadra dalla parte della freccia`() {
        near(Vec2(0.0, 1.0), dir(right, WallArrow.DOWN, 90.0))
        near(Vec2(0.0, -1.0), dir(right, WallArrow.UP, 90.0))
    }

    @Test
    fun `verso giu con 70 gradi - il muro scende chiudendo 70 gradi col precedente`() {
        val d = dir(right, WallArrow.DOWN, 70.0)
        assertTrue(d.y > 0, "scende")
        assertTrue(d.x < 0, "con 70° (meno di 90) torna un po' indietro, verso sinistra")
        assertEquals(70.0, cornerAngle(right, d), 1e-9)
        near(Vec2(-cos(Math.toRadians(70.0)), sin(Math.toRadians(70.0))), d)
    }

    @Test
    fun `angolo ottuso - il muro si apre in avanti`() {
        val d = dir(right, WallArrow.DOWN, 120.0)
        assertTrue(d.y > 0 && d.x > 0)
        assertEquals(120.0, cornerAngle(right, d), 1e-9)
    }

    @Test
    fun `l'angolo tra i muri vale anche dopo un muro inclinato`() {
        val prev = Vec2(-cos(Math.toRadians(70.0)), sin(Math.toRadians(70.0))) // scende verso sinistra
        for (a in listOf(WallArrow.LEFT, WallArrow.RIGHT, WallArrow.UP, WallArrow.DOWN)) {
            val r = WallDirection.of(prev, a, 110.0)
            val d = assertIs<WallDirection.Result.Ok>(r).dir
            assertEquals(110.0, cornerAngle(prev, d), 1e-9, "$a")
            assertTrue((d dot a.dir) > -1e-9, "$a: gira dalla parte della freccia")
        }
    }

    @Test
    fun `combinazioni che non danno un muro`() {
        assertEquals(WallDirection.Result.Invalid(WallDirectionProblem.BACKWARDS), WallDirection.of(right, WallArrow.LEFT, null))
        assertEquals(WallDirection.Result.Invalid(WallDirectionProblem.BACKWARDS), WallDirection.of(right, WallArrow.LEFT, 90.0))
        assertEquals(WallDirection.Result.Invalid(WallDirectionProblem.ANGLE_WITHOUT_SIDE), WallDirection.of(right, WallArrow.RIGHT, 70.0))
        for (bad in listOf(0.0, 0.5, 180.0, 200.0, -70.0))
            assertEquals(WallDirection.Result.Invalid(WallDirectionProblem.ANGLE_OUT_OF_RANGE), WallDirection.of(right, WallArrow.DOWN, bad), "$bad")
    }
}
