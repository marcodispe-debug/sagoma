package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ambiente unico (soggiorno e cucina con il muro in comune tolto da tutte e due): sulla pianta i muri di fuori continuano, senza
 * la tacca larga quanto il muro tolto che restava sul loro lato interno.
 */
class OpenPlanDrawTest {
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 30.0)
    private val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }, wallThickness = 30.0)

    /** Colore della pianta disegnata nei punti dati (cm). */
    private fun colors(plan: FloorPlan, points: List<Vec2>): List<Color> {
        val cam = Camera(1.0f, 50f, 50f)
        val img = ImageBitmap(820, 420)
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(820f, 420f)) {
            drawRect(Color.White)
            drawPlan(EditorUiState(plan = plan, camera = cam), measurer, background = Color.White)
        }
        val px = img.toPixelMap()
        return points.map { px[cam.toScreenX(it.x).toInt(), cam.toScreenY(it.y).toInt()] }
    }

    private fun Color.dark() = red + green + blue < 1.0f

    @Test
    fun `muri di fuori senza tacca nell'ambiente unico`() {
        val open = FloorPlan(listOf(living.copy(removedWalls = setOf(1)), kitchen.copy(removedWalls = setOf(3))))
        // Sul lato interno dei muri di sopra e di sotto, dove arrivava il muro tolto: muro (scuro). In mezzo al varco: pavimento.
        val (top, bottom, middle) = colors(open, listOf(Vec2(400.0, 8.0), Vec2(400.0, 292.0), Vec2(400.0, 150.0)))
        assertTrue(top.dark(), "sopra: $top")
        assertTrue(bottom.dark(), "sotto: $bottom")
        assertTrue(!middle.dark(), "in mezzo: $middle")
    }

    @Test
    fun `stanza aperta da un lato - il muro finisce dritto`() {
        // Solo il soggiorno, aperto verso destra: i muri sopra e sotto finiscono pieni (anche la metà verso la stanza) al filo
        // esterno del lato tolto; più in là niente.
        val alone = FloorPlan(listOf(living.copy(removedWalls = setOf(1))))
        val (inner, outer, beyond, gap) = colors(alone, listOf(Vec2(408.0, 8.0), Vec2(408.0, -8.0), Vec2(420.0, 8.0), Vec2(400.0, 150.0)))
        assertTrue(inner.dark(), "metà verso la stanza: $inner")
        assertTrue(outer.dark(), "metà verso fuori: $outer")
        assertTrue(!beyond.dark(), "oltre: $beyond")
        assertTrue(!gap.dark(), "varco: $gap")
    }
}
