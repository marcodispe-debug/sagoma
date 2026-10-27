package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Laying
import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.model.LayingStart
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.MaterialLayout
import com.sagoma.planimetria.model.MaterialPieces
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Foto dall'alto di pavimenti posati a pezzi (stanza a L), disegnate qui senza scheda video: ogni triangolo con la sua foto,
 * sopra il colore della fuga. Parte solo con SAGOMA_FOTO_POSA=true; immagini in build/posa/.
 */
class LayingFotoTest {
    private val saved = MaterialCatalog.items

    @AfterTest
    fun restore() = MaterialCatalog.set(saved)

    private val dir = File("../catalogo/src/main/assets/materials")
    private val textures = HashMap<String, BufferedImage>()

    private fun texture(id: String) = textures.getOrPut(id) { ImageIO.read(File(dir, "${id}_color.jpg")) }

    /** Pavimento visto dall'alto, `px` pixel per cm. */
    private fun draw(room: Room, px: Double, title: String): BufferedImage {
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val b = Polygon.bounds(room.points)
        val w = ((b.maxX - b.minX) * px).toInt()
        val h = ((b.maxY - b.minY) * px).toInt()
        val img = BufferedImage(w, h + 26, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h + 26)
        val laying = room.floorLayout.laying
        g.color = Color((laying?.jointArgb ?: 0xFF888888).toInt())
        g.fillRect(0, 0, w, h)
        for ((id, v) in scene.textured) {
            val tex = texture(id)
            for (t in v.indices step 24) {
                val xs = DoubleArray(3) { (v[t + it * 8].toDouble() - b.minX) * px }
                val ys = DoubleArray(3) { (v[t + it * 8 + 2].toDouble() - b.minY) * px }
                val us = DoubleArray(3) { v[t + it * 8 + 6].toDouble() }
                val vs = DoubleArray(3) { v[t + it * 8 + 7].toDouble() }
                val area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0])
                if (kotlin.math.abs(area) < 1e-9) continue
                for (y in max(0, floor(ys.min()).toInt())..min(h - 1, ys.max().toInt())) {
                    for (x in max(0, floor(xs.min()).toInt())..min(w - 1, xs.max().toInt())) {
                        val cx = x + 0.5
                        val cy = y + 0.5
                        val l1 = ((xs[1] - cx) * (ys[2] - cy) - (xs[2] - cx) * (ys[1] - cy)) / area
                        val l2 = ((xs[2] - cx) * (ys[0] - cy) - (xs[0] - cx) * (ys[2] - cy)) / area
                        val l3 = 1 - l1 - l2
                        if (l1 < 0 || l2 < 0 || l3 < 0) continue
                        val u = l1 * us[0] + l2 * us[1] + l3 * us[2]
                        val vv = l1 * vs[0] + l2 * vs[1] + l3 * vs[2]
                        val tx = ((u - floor(u)) * tex.width).toInt().coerceIn(0, tex.width - 1)
                        val ty = ((vv - floor(vv)) * tex.height).toInt().coerceIn(0, tex.height - 1)
                        img.setRGB(x, y, tex.getRGB(tx, ty))
                    }
                }
            }
        }
        g.color = Color.BLACK
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
        g.drawString(title, 6, h + 18)
        g.dispose()
        return img
    }

    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_POSA") != "true") return
        MaterialCatalog.load(File(dir, "materials.json").readText())
        val out = File("build/posa").apply { mkdirs() }
        val shape = listOf(
            com.sagoma.planimetria.model.Vec2(0.0, 120.0), com.sagoma.planimetria.model.Vec2(260.0, 120.0), com.sagoma.planimetria.model.Vec2(260.0, 0.0),
            com.sagoma.planimetria.model.Vec2(420.0, 0.0), com.sagoma.planimetria.model.Vec2(420.0, 440.0), com.sagoma.planimetria.model.Vec2(0.0, 440.0),
        )
        fun room(material: String, layout: MaterialLayout) = Room(1, "Prova", RoomType.Soggiorno, shape).copy(floorMaterial = material, floorLayout = layout)
        val cases = listOf(
            Triple("woodfloor051", MaterialLayout(), "Listoni rovere, come nella foto"),
            Triple("woodfloor051", MaterialLayout(laying = MaterialPieces.defaultLaying(MaterialCatalog.item("woodfloor051")!!)), "Listoni rovere, a correre come la foto"),
            Triple("woodfloor040", MaterialLayout(laying = Laying(LayingPattern.SpinaDiPesce, 10.0, 60.0, 0.1, 0xFF4A3520)), "Parquet rovere miele, spina di pesce 10 x 60"),
            Triple("woodfloor040", MaterialLayout(rotation = 90.0, laying = Laying(LayingPattern.ACorrere, 20.0, 120.0, 0.1, 0xFF4A3520)), "Parquet rovere miele, a correre 20 x 120, girato"),
            Triple("ph_laminate_floor_02", MaterialLayout(laying = Laying(LayingPattern.Cesto, 15.0, 60.0, 0.2, 0xFF3C3226)), "Laminato rovere, a cesto 15 x 60"),
            Triple("marble021", MaterialLayout(laying = Laying(LayingPattern.Dritta, 60.0, 60.0, 0.3, 0xFF9A958C)), "Marmo di Carrara, dritta 60 x 60, fuga 3 mm grigia"),
            Triple("marble021", MaterialLayout(rotation = 45.0, laying = Laying(LayingPattern.Dritta, 60.0, 60.0, 0.2, 0xFFD3D0CA, LayingStart.Angolo)), "Marmo di Carrara, in diagonale da un angolo"),
            Triple("tiles139", MaterialLayout(laying = MaterialPieces.defaultLaying(MaterialCatalog.item("tiles139")!!)), "Gres beige, come la foto (dritta)"),
            Triple("tiles139", MaterialLayout(laying = Laying(LayingPattern.Sfalsata, 30.0, 60.0, 0.5, 0xFF2B2B2B)), "Gres beige, sfalsata 30 x 60, fuga nera 5 mm"),
            Triple("tiles074", MaterialLayout(laying = MaterialPieces.defaultLaying(MaterialCatalog.item("tiles074")!!).copy(jointArgb = 0xFFF4F2EE)), "Scacchiera in marmo, fuga bianca"),
            Triple("tiles081", MaterialLayout(laying = MaterialPieces.defaultLaying(MaterialCatalog.item("tiles081")!!).copy(width = 25.0, length = 25.0)), "Cementine bianco e nero 25 x 25"),
            Triple("concrete042a", MaterialLayout(laying = Laying(LayingPattern.Terzi, 40.0, 80.0, 0.3, 0xFF6E6C68)), "Cemento grigio, lastre 40 x 80 a un terzo"),
        )
        for ((k, c) in cases.withIndex()) {
            val (id, layout, title) = c
            val r = room(id, layout)
            val count = LayingPieces.count(r)
            val img = draw(r, 2.0, title + (count?.let { " - ${it.whole} interi, ${it.cut} tagliati" } ?: ""))
            ImageIO.write(img, "png", File(out, "posa-%02d-%s.png".format(k + 1, id)))
        }
    }
}
