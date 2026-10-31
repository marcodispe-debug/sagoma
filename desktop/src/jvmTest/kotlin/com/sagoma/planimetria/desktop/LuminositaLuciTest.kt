package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Luminosità scelta per ogni punto luce, nella vista 3D del computer: di notte, in un salone lungo con due plafoniere, la metà con
 * quella più forte è più chiara. Le immagini si salvano in build/prova-3d-luminosita-*.png per controllarle a occhio.
 */
class LuminositaLuciTest {
    private fun hall(left: Double, right: Double) = FloorPlan(listOf(Room(
        1, "Salone", RoomType.Soggiorno, RoomFactory.rectangle(800.0, 400.0),
        fixtures = listOf(
            Fixture(1, FixtureKind.CeilingLight, point = Vec2(200.0, 200.0), brightness = left),
            Fixture(2, FixtureKind.CeilingLight, point = Vec2(600.0, 200.0), brightness = right),
        ),
    )))

    /** Luce media (0–255) della metà sinistra e di quella destra del pavimento, visto dall'alto. */
    private fun halves(name: String, left: Double, right: Double): Pair<Double, Double> {
        val r = DesktopGlSceneRenderer()
        r.ambientOcclusion = false
        r.setScene(Scene3D.build(hall(left, right), null, null, ceilings = false))
        r.setDaylight(22f, "auto")
        r.setCamera(Vec3(400.0, 900.0, 650.0), Vec3(400.0, 0.0, 200.0), Vec3.Up, 50.0, 5.0, walking = false)
        val img = r.renderNow(960, 600)
        assertNotNull(img)
        File("build/prova-3d-luminosita-$name.png").writeBytes(SkiaImages.encodePng(img)!!)
        val px = IntArray(960 * 600)
        img.readPixels(px)
        r.release()
        fun lum(c: Int) = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3.0
        // Solo il pavimento (il buio attorno alla casa è uguale nelle due metà).
        var l = 0.0
        var rr = 0.0
        for (y in 220 until 340) for (x in 250 until 710) if (x < 480) l += lum(px[y * 960 + x]) else rr += lum(px[y * 960 + x])
        val n = 120.0 * 230
        return l / n to rr / n
    }

    @Test
    fun ogniPuntoLuceConLaSuaLuminosita() {
        val (l0, r0) = halves("uguali", 1.0, 1.0)
        assertTrue(kotlin.math.abs(l0 - r0) < 2.0, "uguali: $l0 / $r0")
        val (l1, r1) = halves("sinistra-bassa", 0.3, 2.0)
        assertTrue(r1 > l1 + 20, "a destra più luce: $l1 / $r1")
        assertTrue(l1 < l0 - 10 && r1 > r0 + 10, "abbassata più scura, alzata più chiara: $l0 → $l1, $r0 → $r1")
        val (l2, r2) = halves("sinistra-spenta", 0.0, 1.0)
        assertTrue(l2 < l1 && r2 > l2 + 20, "a sinistra spenta: $l2 / $r2")
    }
}
