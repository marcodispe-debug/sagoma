package com.sagoma.planimetria.desktop

import androidx.compose.ui.graphics.ImageBitmap
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Muovendosi il 3D del computer disegna un'immagine più piccola, che la vista allarga: deve essere la stessa immagine, solo meno
 * nitida (niente pezzi di immagini di prima sui bordi, occlusione e alone al loro posto). Appena ci si ferma arriva quella piena.
 * Le immagini restano in build/movimento-*.png.
 */
class Movimento3DTest {
    private fun renderer(): DesktopGlSceneRenderer {
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)).copy(
            openings = listOf(Opening.default(1, OpeningKind.Window2, 2, 180.0), Opening.default(2, OpeningKind.Window2, 1, 200.0)),
        )
        val r = DesktopGlSceneRenderer()
        r.setScene(Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true))
        r.setDaylight(10f, "auto")
        r.setCamera(Vec3(120.0, 160.0, 60.0), Vec3(400.0, 60.0, 380.0), Vec3.Up, 80.0, 5.0, walking = true)
        r.idle()
        return r
    }

    @Test
    fun immagine_piccola_uguale_a_quella_piena() {
        val r = renderer()
        val still = r.renderNow(W, H)!!
        // Prima un'immagine piena, poi quelle piccole: le parti delle texture fuori dall'immagine piccola hanno ancora la piena.
        r.motionScale = 0.5f
        val small = r.renderNow(W, H, DesktopGlSceneRenderer.Mode.MOVING)!!
        r.release()
        File("build/movimento-piena.png").writeBytes(SkiaImages.encodePng(still)!!)
        File("build/movimento-piccola.png").writeBytes(SkiaImages.encodePng(small)!!)
        assertEquals(W / 2, small.width)
        assertEquals(H / 2, small.height)
        val a = halved(still)
        val b = pixels(small)
        val w = W / 2
        val h = H / 2
        fun diff(xs: IntRange, ys: IntRange): Double {
            var sum = 0.0
            var n = 0
            for (y in ys) for (x in xs) {
                val p = a[y * w + x]
                val q = b[y * w + x]
                for (sh in intArrayOf(16, 8, 0)) sum += abs(((p shr sh) and 0xFF) - ((q shr sh) and 0xFF))
                n += 3
            }
            return sum / n
        }
        val all = diff(0 until w, 0 until h)
        val right = diff(w - 6 until w, 0 until h)
        val top = diff(0 until w, 0 until 6)
        val bottom = diff(0 until w, h - 6 until h)
        assertTrue(all < 6 && right < 12 && top < 12 && bottom < 12, "Immagine piccola diversa dalla piena: tutto $all, destra $right, sopra $top, sotto $bottom")
    }

    @Test
    fun da_fermi_torna_piena() {
        val r = renderer()
        r.renderNow(W, H)
        r.motionScale = 0.5f
        // Un'immagine subito dopo l'altra: ci si sta muovendo.
        r.setCamera(Vec3(130.0, 160.0, 60.0), Vec3(400.0, 60.0, 380.0), Vec3.Up, 80.0, 5.0, walking = true)
        r.idle()
        val moving = r.shown!!.width
        val deadline = System.currentTimeMillis() + 5000
        while (r.shown!!.width != W && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val after = r.shown!!.width
        r.release()
        assertTrue(moving < W, "Muovendosi l'immagine doveva essere più piccola: $moving")
        assertEquals(W, after, "Fermi, l'immagine doveva tornare piena")
    }

    @Test
    fun passando_la_porta_la_luce_non_scatta() {
        // Soggiorno con due finestre grandi, camera accanto con una piccola: camminando dall'uno all'altra l'esposizione cambia, ma
        // si abitua piano (come l'occhio) invece di scattare tutta insieme quando si passa la porta.
        val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 400.0)).copy(
            openings = listOf(
                Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(width = 220.0),
                Opening.default(2, OpeningKind.Window2, 2, 200.0).copy(width = 220.0),
                Opening.default(3, OpeningKind.Door, 1, 200.0),
            ),
        )
        val bedroom = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 400.0).map { it + Vec2(400.0, 0.0) })
            .copy(openings = listOf(Opening.default(4, OpeningKind.Window1, 2, 300.0).copy(width = 60.0, height = 60.0, sillHeight = 150.0)))
        val r = DesktopGlSceneRenderer()
        r.setScene(Scene3D.build(FloorPlan(listOf(living, bedroom)), null, null, ceilings = true))
        r.setDaylight(10f, "auto")
        var clock = 0L
        r.exposureClock = { clock += 20_000_000L; clock }
        // Come nell'app: ogni spostamento della telecamera chiede un'immagine, e finché l'occhio si abitua ne arrivano altre da sole.
        fun look(x: Double) = r.setCamera(Vec3(x, 160.0, 200.0), Vec3(x + 300, 150.0, 200.0), Vec3.Up, 70.0, 5.0, walking = true)
        fun quiet() {
            var last: ImageBitmap? = null
            while (r.shown !== last) {
                last = r.shown
                Thread.sleep(300)
                r.idle()
            }
        }
        fun still(x: Double): Double {
            look(x)
            quiet()
            return mean(r.renderNow(W / 2, H / 2)!!)
        }
        val before = still(330.0)
        val after = still(470.0)
        assertTrue(abs(after - before) > 15, "Le due stanze dovevano avere un'esposizione diversa: $before e $after")
        // Camminando a 5 cm per immagine: nessun salto grande tra un'immagine e l'altra.
        var prev = still(330.0)
        var worst = 0.0
        var x = 335.0
        while (x <= 470.0) {
            look(x)
            r.idle()
            val m = mean(r.shown!!)
            worst = maxOf(worst, abs(m - prev))
            prev = m
            x += 5.0
        }
        // Fermi nella camera: l'esposizione arriva da sola a quella giusta (poi l'immagine nitida).
        quiet()
        val settled = mean(r.shown!!)
        val target = still(470.0)
        r.release()
        assertTrue(worst < 0.4 * abs(after - before), "Passando la porta la luce scatta: salto $worst su ${abs(after - before)}")
        assertTrue(abs(settled - target) < 1.5, "Fermi, l'esposizione doveva arrivare a quella giusta: $settled invece di $target")
    }

    private fun mean(img: ImageBitmap): Double = pixels(img).sumOf { c ->
        0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)
    } / (img.width * img.height)

    private fun pixels(img: ImageBitmap): IntArray = IntArray(img.width * img.height).also { img.readPixels(it) }

    /** L'immagine piena a metà misure (media di 2 × 2 pixel). */
    private fun halved(img: ImageBitmap): IntArray {
        val p = pixels(img)
        val w = img.width / 2
        val h = img.height / 2
        return IntArray(w * h) { i ->
            val x = (i % w) * 2
            val y = (i / w) * 2
            var out = 0
            for (sh in intArrayOf(16, 8, 0)) {
                val c = (((p[y * img.width + x] shr sh) and 0xFF) + ((p[y * img.width + x + 1] shr sh) and 0xFF) +
                    ((p[(y + 1) * img.width + x] shr sh) and 0xFF) + ((p[(y + 1) * img.width + x + 1] shr sh) and 0xFF)) / 4
                out = out or (c shl sh)
            }
            out
        }
    }

    private companion object {
        const val W = 960
        const val H = 600
    }
}
