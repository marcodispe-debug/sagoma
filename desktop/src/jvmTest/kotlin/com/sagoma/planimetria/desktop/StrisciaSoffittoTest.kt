package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Camminando, in cima alle pareti rivolte verso il sole non c'è una striscia chiara sotto il soffitto (una specie di "zoccolo"
 * in alto, solo su alcune pareti): il sole non deve passare negli ultimi centimetri tra parete e soffitto. Stanza chiusa senza
 * piano di sopra, alle 11, guardando i quattro angoli in alto; le immagini restano in build/striscia-*.png.
 */
class StrisciaSoffittoTest {
    @Test
    fun niente_striscia_chiara_sotto_il_soffitto() {
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
        val r = DesktopGlSceneRenderer()
        // Senza occlusione ambientale: con la scheda grafica "software" del cloud lascia qualche puntino chiaro proprio sullo spigolo
        // (sul computer di Marco no), e qui conta solo il sole.
        r.ambientOcclusion = false
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        val eye = Vec3(250.0, 160.0, 200.0)
        val corners = listOf("a" to Vec3(0.0, 250.0, 0.0), "b" to Vec3(500.0, 250.0, 0.0), "c" to Vec3(500.0, 250.0, 400.0), "d" to Vec3(0.0, 250.0, 400.0))
        val found = mutableListOf<String>()
        for ((name, corner) in corners) {
            r.setCamera(eye, corner, Vec3.Up, 80.0, 5.0, walking = true)
            val img = r.renderNow(W, H)!!
            File("build/striscia-$name.png").writeBytes(SkiaImages.encodePng(img)!!)
            val pixels = IntArray(W * H)
            img.readPixels(pixels)
            val n = ridgeColumns(pixels)
            if (n > W / 50) found += "$name: $n colonne"
        }
        r.release()
        assertTrue(found.isEmpty(), "Striscia chiara in cima alle pareti: $found")
    }

    /**
     * Colonne dell'immagine in cui c'è una riga più chiara sia di quello che sta poco sopra sia di quello che sta poco sotto
     * (una striscia). Nella stanza vuota l'unico posto dove può esserci è l'incontro tra pareti e soffitto.
     */
    private fun ridgeColumns(pixels: IntArray): Int {
        fun light(x: Int, y: Int): Int = pixels[y * W + x].let { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) } / 3
        var columns = 0
        for (x in 0 until W) {
            if ((GAP until H - GAP).any { y -> light(x, y) - maxOf(light(x, y - GAP), light(x, y + GAP)) > 20 }) columns++
        }
        return columns
    }

    private companion object {
        const val W = 960
        const val H = 600
        const val GAP = 10
    }
}
