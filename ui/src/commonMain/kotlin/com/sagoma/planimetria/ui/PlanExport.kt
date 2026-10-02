package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Esportazione PNG (§12). La pianta viene ridisegnata fuori schermo con lo stesso codice del canvas,
 * ma in uno stato pulito: nessuna selezione né maniglia, nome + area e quote su tutte le stanze,
 * sfondo bianco. I colori sono tutti espliciti, quindi non dipendono dal tema del dispositivo.
 */
object PlanExport {

    /** Lato lungo dell'immagine, in px. */
    private const val TARGET_LONG_SIDE_PX = 2400f
    private const val MAX_SIDE_PX = 4096f
    /** Margine attorno al contenuto (quote, nastro dei metri, etichette), in dp. */
    private const val MARGIN_DP = 72f

    /** Ingombro da esportare: stanze, metri e ante che si aprono verso l'esterno. */
    fun contentBounds(plan: FloorPlan): Bounds? = Openings.planBounds(plan)

    fun render(
        plan: FloorPlan, measurer: TextMeasurer, density: Density, levelHeight: Double = Floor.DEFAULT_LEVEL_HEIGHT,
        layers: com.sagoma.planimetria.model.LayerSettings = com.sagoma.planimetria.model.LayerSettings(),
    ): ImageBitmap? {
        fun shown(l: com.sagoma.planimetria.model.Layer) = layers.visible(l)
        val b = contentBounds(plan) ?: return null
        val margin = MARGIN_DP * density.density
        val contentW = max(b.width, 1.0)
        val contentH = max(b.height, 1.0)
        val scale = min(
            (TARGET_LONG_SIDE_PX - 2 * margin) / max(contentW, contentH),
            min((MAX_SIDE_PX - 2 * margin) / contentW, (MAX_SIDE_PX - 2 * margin) / contentH),
        ).toFloat()
        val w = ceil(contentW * scale + 2 * margin).toInt()
        val planH = ceil(contentH * scale + 2 * margin).toInt()
        // Nota in fondo all'immagine: chi la riceve deve sapere a cosa si riferiscono le misure.
        val note = measurer.measure(
            INTERIOR_MEASURES_NOTE,
            TextStyle(fontSize = 12.sp, color = Color(0xFF495057)),
            constraints = Constraints(maxWidth = (w - 2 * margin).toInt().coerceAtLeast(1)),
            density = density,
        )
        val h = planH + note.size.height + (margin / 2).toInt()
        val cam = Camera(scale, (margin - b.minX * scale).toFloat(), (margin - b.minY * scale).toFloat())
        val state = EditorUiState(plan = plan, camera = cam) // nulla a fuoco, nessuna selezione
        val labelPad = 8 * density.density

        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color.White)
            drawPlan(state, measurer, background = Color.White)
            // Solo i livelli visibili.
            if (shown(com.sagoma.planimetria.model.Layer.Structure)) for (s in plan.stairs) drawStair(s, levelHeight, cam, measurer, selected = false)
            if (shown(com.sagoma.planimetria.model.Layer.Furniture)) drawFurniture(plan.furniture, cam, null)
            if (shown(com.sagoma.planimetria.model.Layer.Structure)) drawStructures(plan, cam, measurer)
            val labels = measureRoomLabels(plan, measurer) { true }
            if (shown(com.sagoma.planimetria.model.Layer.WallLengths)) drawWallDimensions(plan, cam, measurer, avoid = labels.map { it.rect(cam, labelPad) })
            drawWallCuts(state)
            drawRoomLabels(labels, cam, labelPad) { true }
            if (shown(com.sagoma.planimetria.model.Layer.Fixtures)) drawAllFixtures(state)
            drawNotes(
                plan, cam, measurer, Color.White,
                showDimensions = shown(com.sagoma.planimetria.model.Layer.Dimensions), showTexts = shown(com.sagoma.planimetria.model.Layer.Texts),
            )
            if (shown(com.sagoma.planimetria.model.Layer.Rulers)) for (r in plan.rulers) drawRuler(RulerGeom(r, cam, density), cam, rotating = false, measurer, showHandles = false)
            drawText(note, topLeft = Offset(margin, planH.toFloat()))
        }
        return bitmap
    }

}
