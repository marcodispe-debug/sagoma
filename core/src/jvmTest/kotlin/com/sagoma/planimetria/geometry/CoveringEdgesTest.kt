package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallCut
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.model.WindowShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rivestimenti sui bordi inclinati (sopra un arco, sotto il soffitto in pendenza): i pezzi seguono il bordo senza scalini e
 * senza sporgere. E in mansarda il muro basso arriva fino al soffitto, senza fessura in cima.
 */
class CoveringEdgesTest {
    // Muro 0 da (0, 0) a (500, 0), spesso 30: faccia verso la stanza a z = 15, rivestimento appena davanti (z = 15,15).
    private val wallpaper = WallFinish(WallPaint.White.argb, covering = Covering.Wallpaper)
    private val stripe = Rgba.argb(wallpaper.coverArgb)

    private fun room() = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0), wallThickness = 30.0, wallFinish = wallpaper)

    /** Triangoli delle righe della carta da parati sulla faccia del muro 0, in (x, y). */
    private fun stripes(s: Scene3D): List<List<Vec2>> {
        val n = Scene3D.FLOATS_PER_VERTEX
        val src = s.opaque
        val out = mutableListOf<List<Vec2>>()
        var k = 0
        while (k + 3 * n <= src.size) {
            val vs = (0 until 3).map { k + it * n }
            if (vs.all { abs(src[it + 2] - 15.15f) < 0.01f && abs(src[it + 6] - stripe.r) < 1e-4f && abs(src[it + 7] - stripe.g) < 1e-4f && abs(src[it + 8] - stripe.b) < 1e-4f }) {
                out += vs.map { Vec2(src[it].toDouble(), src[it + 1].toDouble()) }
            }
            k += 3 * n
        }
        return out
    }

    private fun covered(tris: List<List<Vec2>>, t: Double, y: Double) = tris.any { Polygon.contains(it, Vec2(t, y)) }

    /** Punti al centro delle righe (larghe 5 cm, ogni 16 cm) tra `from` e `to`. */
    private fun stripeCenters(from: Double, to: Double) = generateSequence(0.0) { it + 16.0 }.map { it + 2.5 }.dropWhile { it < from }.takeWhile { it < to }.toList()

    @Test
    fun `la carta da parati segue l'arco`() {
        // Finestra ad arco da 140 a 260, da 90 a 250, con lo sguincio di 12: sulla faccia il varco va da 128 a 272.
        val window = Opening.default(10, OpeningKind.Window2, 0, 200.0).copy(windowShape = WindowShape.Arched, height = 160.0, splay = 12.0)
        val s = Scene3D.build(FloorPlan(listOf(room().copy(openings = listOf(window)))), null, null, ceilings = true)
        val tris = stripes(s)
        // Lontano dalle imposte, dove le fettine dritte del muro stanno a meno di 2 mm dall'arco vero.
        val centers = stripeCenters(140.0, 260.0)
        assertTrue(centers.size >= 7)
        for (t in centers) {
            val edge = Openings.archTop(PassageStyle.Arched, 128.0, 272.0, 90.0, 250.0, t)
            assertTrue(covered(tris, t, edge + 0.6), "riga a $t: scoperto sopra l'arco (${edge + 0.6})")
            assertFalse(covered(tris, t, edge - 0.6), "riga a $t: carta sul vuoto dell'arco (${edge - 0.6})")
        }
    }

    @Test
    fun `sotto la pendenza la carta arriva al soffitto e non lo passa`() {
        // Mansarda: muro 3 (x = 0) alto 140, muro 0 tagliato che sale da 140 a 270 nei primi 250 cm. La finestra da 140 a 260,
        // alta fino a 210 e con lo sguincio di 15, passa la pendenza: a sinistra (da 125) la cima del muro è sotto quella della finestra.
        val window = Opening.default(10, OpeningKind.Window2, 0, 200.0).copy(splay = 15.0)
        val r = room().copy(wallHeights = mapOf(3 to 140.0), wallCuts = mapOf(0 to WallCut(towardEnd = false, start = 250.0)), openings = listOf(window))
        val s = Scene3D.build(FloorPlan(listOf(r)), null, null, ceilings = true)
        val tris = stripes(s)
        for (t in stripeCenters(20.0, 480.0)) {
            val top = Ceilings.wallTopAt(r, 0, t)
            val overWindow = t > 125 && t < 275
            if (!overWindow || top > 211) assertTrue(covered(tris, t, top - 0.6), "riga a $t: scoperta sotto il soffitto (${top - 0.6})")
            assertFalse(covered(tris, t, top + 0.6), "riga a $t: sopra il soffitto (${top + 0.6})")
            // Sopra la finestra (dove la cima del muro è più alta di lei) la carta c'è.
            if (overWindow && top > 211) assertTrue(covered(tris, t, (210 + top) / 2), "riga a $t: scoperta sopra la finestra")
        }
    }

    @Test
    fun `il muro basso della mansarda arriva al soffitto`() {
        // Il soffitto sale dalla mezzeria del muro basso (140) di 130 cm ogni 250: sulla faccia, 15 cm più in là, è a 147,8.
        val r = room().copy(wallFinish = null, wallHeights = mapOf(3 to 140.0), wallCuts = mapOf(0 to WallCut(towardEnd = false, start = 250.0)))
        val s = Scene3D.build(FloorPlan(listOf(r)), null, null, ceilings = true)
        assertEquals(147.8, Ceilings.heightAt(r, Vec2(15.0, 200.0)), 1e-6)
        for (y in listOf(139.0, 142.0, 146.0, 147.5)) {
            val hit = s.hit(Vec3(250.0, y, 200.0), Vec3(-1.0, 0.0, 0.0))
            assertEquals(Pick.Wall(1, 3), hit?.pick, "a $y cm")
            assertEquals(15.0, hit!!.point.x, 1e-6, "a $y cm")
        }
        // Senza soffitto (vista dall'alto) il muro resta alto com'è.
        val open = Scene3D.build(FloorPlan(listOf(r)), null, null, ceilings = false)
        assertTrue(open.hit(Vec3(250.0, 144.0, 200.0), Vec3(-1.0, 0.0, 0.0))?.pick != Pick.Wall(1, 3))
    }
}
