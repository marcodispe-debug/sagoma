package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.KitchenModules
import com.sagoma.planimetria.model.KitchenModules.Front
import com.sagoma.planimetria.model.KitchenModules.Kind
import com.sagoma.planimetria.model.KitchenModules.Spec
import com.sagoma.planimetria.model.KitchenModules.Top
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.model.ParametricFurniture.Kind as PKind
import com.sagoma.planimetria.model.ParametricFurniture.Spec as PSpec
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import java.io.File
import kotlin.test.Test

/** TEMPORANEO (non va nel repository): immagini di controllo dei moduli della cucina, pianta e schede del catalogo. */
class TmpKitchenImagesTest {
    @Test
    fun immagini() {
        System.getenv("SAGOMA_KITCHEN_IMAGES") ?: return
        // Stanza 380 × 300 interna (muri di 15 cm in mezzeria): cucina lungo il muro in alto, pensili sopra le basi.
        val room = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(395.0, 315.0))
        val front = Front.SAGE
        val top = Top.CARRARA
        var x = 7.5
        val mods = ArrayList<Furniture>()
        fun add(kind: Kind, w: Double = kind.width, mirrored: Boolean = false) {
            mods += Furniture(mods.size + 1L, KitchenModules.model(Spec(kind, front, top)), Vec2(x + w / 2, 7.5 + kind.depth / 2), 0.0, w, kind.depth, kind.height, kind.elevation, mirrored)
        }
        add(Kind.TALL_FRIDGE); x += 60
        add(Kind.TALL_OVEN); x += 60
        val basesStart = x
        add(Kind.BASE_DRAWERS); x += 60
        add(Kind.SINK); x += 80
        add(Kind.DISHWASHER); x += 60
        add(Kind.OVEN_HOB); x += 60
        x = basesStart
        add(Kind.WALL_DOORS); x += 60
        add(Kind.WALL_DOORS, 80.0); x += 80
        add(Kind.WALL_DOORS); x += 60
        add(Kind.WALL_HOOD); x += 60
        val plan = FloorPlan(listOf(room), furniture = mods)
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        File("build").mkdirs()
        PlanExport.render(plan, measurer, density)?.let { File("build/prova-cucina-pianta.png").writeBytes(SkiaImages.encodePng(it)!!) }

        // Schede del catalogo: tutti i tipi, vista dal davanti, in due finiture.
        val kinds = Kind.entries
        val cw = 150; val ch = 120
        val img = ImageBitmap(cw * kinds.size, ch * 2)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
            drawRect(Color(0xFFF6F5F2))
            for ((row, finish) in listOf(Spec(Kind.BASE_DOORS, Front.WHITE, Top.LAMINATE_WHITE), Spec(Kind.BASE_DOORS, Front.WALNUT, Top.MARQUINIA)).withIndex()) {
                for ((i, k) in kinds.withIndex()) {
                    translate((i * cw).toFloat(), (row * ch).toFloat()) {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, cw.toFloat(), ch.toFloat())
                        val sub = this
                        with(sub) {
                            // Un riquadro grande quanto la scheda.
                            val s = Spec(k, finish.front, finish.top)
                            drawIntoBox(cw, ch) { kitchenFrontView(s, k.width, k.height, 10f) }
                        }
                        drawContext.canvas.restore()
                    }
                }
            }
        }
        File("build/prova-cucina-schede.png").writeBytes(SkiaImages.encodePng(img)!!)
    }

    @Test
    fun mobili() {
        System.getenv("SAGOMA_KITCHEN_IMAGES") ?: return
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        File("build").mkdirs()
        fun p(id: Long, k: PKind, f: Front, x: Double, y: Double, rot: Double = 0.0, w: Double = k.width, h: Double = k.height) =
            Furniture(id, ParametricFurniture.model(PSpec(k, f)), Vec2(x, y), rot, w, k.depth, h, k.elevation)
        val inTop = 7.5
        val inBottom = 420.0 - 7.5
        val letto = listOf(
            p(1, PKind.BED, Front.OAK, 230.0, inTop + 102.5),
            p(2, PKind.NIGHTSTAND, Front.OAK, 120.0, inTop + 20.0),
            p(3, PKind.NIGHTSTAND, Front.OAK, 340.0, inTop + 20.0),
            p(4, PKind.CHEST, Front.NAVY, 435.0, inTop + 25.0),
            p(5, PKind.WARDROBE, Front.WHITE, 95.0, inBottom - 30.0, rot = 180.0),
            p(6, PKind.SLIDING_WARDROBE, Front.SAGE, 330.0, inBottom - 32.5, rot = 180.0, w = 270.0),
        )
        val giorno = listOf(
            p(1, PKind.BOOKCASE, Front.OAK, 65.0, inTop + 17.5),
            p(2, PKind.TV_UNIT, Front.ANTHRACITE, 235.0, inTop + 21.0),
            p(3, PKind.SHELF, Front.WALNUT, 235.0, inTop + 11.0),
            p(4, PKind.SIDEBOARD, Front.WALNUT, 410.0, inTop + 22.5),
            p(5, PKind.TABLE, Front.OAK, 250.0, 230.0),
            p(6, PKind.DESK, Front.WHITE, 90.0, inBottom - 30.0, rot = 180.0),
            p(7, PKind.BOOKCASE, Front.PEARL, 360.0, inBottom - 17.5, rot = 180.0, w = 250.0, h = 240.0),
        )
        for ((name, items) in listOf("camera" to letto, "soggiorno" to giorno)) {
            val plan = FloorPlan(listOf(Room(1, name, RoomType.Camera, RoomFactory.rectangle(500.0, 420.0))), furniture = items)
            PlanExport.render(plan, measurer, density)?.let { File("build/prova-mobili-pianta-$name.png").writeBytes(SkiaImages.encodePng(it)!!) }
        }
        // Schede del catalogo: tutti i tipi, in rovere e in bianco, come nel catalogo (88 × 70 dp a 1,5).
        val kinds = PKind.entries
        val cw = 132; val ch = 105
        val img = ImageBitmap(cw * kinds.size, ch * 2)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
            drawRect(Color(0xFFF6F5F2))
            for ((row, finish) in listOf(Front.OAK, Front.WHITE).withIndex()) for ((i, k) in kinds.withIndex()) {
                translate((i * cw).toFloat(), (row * ch).toFloat()) {
                    val parts = com.sagoma.planimetria.geometry.ParametricGeometry.parts(PSpec(k, finish), k.width, k.depth, k.height)
                    drawIntoBox(cw, ch) { partsFrontView(parts, k.width, k.height, 9f) }
                }
            }
        }
        File("build/prova-mobili-schede.png").writeBytes(SkiaImages.encodePng(img)!!)
    }

    /** Disegna in un riquadro w × h a partire dall'origine corrente (la vista dal davanti usa `size`). */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoBox(w: Int, h: Int, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(this, layoutDirection, Canvas(bmp), Size(w.toFloat(), h.toFloat())) { block() }
        drawImage(bmp)
    }
}
