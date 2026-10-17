package com.sagoma.planimetria.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PNG esportato con e senza marchio del piano gratuito (immagini di controllo in build/). */
class WatermarkImageTest {
    @Test
    fun `il marchio copre l'immagine esportata solo se richiesto`() {
        val plan = FloorPlan(
            listOf(
                Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)),
                Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 400.0).map { it + Vec2(500.0, 0.0) }),
            ),
        )
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val clean = PlanExport.render(plan, measurer, density)!!
        val marked = PlanExport.render(plan, measurer, density, watermark = true)!!
        File("build").mkdirs()
        File("build/prova-watermark.png").writeBytes(SkiaImages.encodePng(marked)!!)
        assertEquals(clean.width, marked.width)
        val a = clean.toPixelMap()
        val b = marked.toPixelMap()
        // Pixel diversi in ogni quarto dell'immagine: il marchio è su tutta l'area, non in un angolo.
        for (qx in 0..1) for (qy in 0..1) {
            var diff = 0
            for (x in qx * a.width / 2 until (qx + 1) * a.width / 2 step 3) for (y in qy * a.height / 2 until (qy + 1) * a.height / 2 step 3) {
                if (a[x, y] != b[x, y]) diff++
            }
            assertTrue(diff > 100, "quarto $qx,$qy senza marchio ($diff pixel diversi)")
        }
    }
}
