package com.sagoma.planimetria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.access.Rights
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Marchio del piano gratuito: la scritta ripetuta in diagonale su tutta l'area del progetto (pianta, vista 3D, immagini e
 * tavole esportate), così resta anche in uno screenshot. È grigia e trasparente: si legge sul bianco della pianta e sul
 * cielo del 3D, ma non copre quote né impedisce di capire il layout. Chi lo vede lo decide [Rights], non questo file.
 */
object Watermark {
    const val TEXT = "Sagoma"

    /** Grigio medio trasparente: visibile sia su fondo chiaro sia su fondo scuro. */
    private val Ink = Color(0xFF808080).copy(alpha = 0.22f)
    private const val ANGLE_DEG = -30f

    /** Disegna il marchio su tutta l'area, con lettere alte [letterPx] pixel. */
    fun DrawScope.drawWatermark(measurer: TextMeasurer, letterPx: Float) {
        if (size.width <= 0f || size.height <= 0f || letterPx <= 0f) return
        val t = measurer.measure(TEXT, TextStyle(color = Ink, fontSize = letterPx.toSp(), fontWeight = FontWeight.SemiBold), density = this)
        val tw = t.size.width.toFloat()
        val th = t.size.height.toFloat()
        val stepX = tw * 2f
        val stepY = th * 3.5f
        // La griglia ruotata deve coprire anche gli angoli: si disegna su un quadrato grande quanto la diagonale.
        val half = hypot(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val cols = ceil(2 * half / stepX).toInt() + 1
        val rows = ceil(2 * half / stepY).toInt() + 1
        clipRect {
            rotate(ANGLE_DEG, c) {
                for (r in 0..rows) {
                    // Righe sfalsate di mezza scritta: niente colonne allineate.
                    val shift = if (r % 2 == 0) 0f else stepX / 2f
                    val y = c.y - half + r * stepY
                    for (k in -1..cols) drawText(t, topLeft = Offset(c.x - half + k * stepX + shift, y))
                }
            }
        }
    }

    /** Lettere delle esportazioni, in proporzione all'immagine: la scritta ha la stessa presenza a ogni dimensione. */
    fun exportLetterPx(width: Float, height: Float): Float = maxOf(width, height) / 28f
}

/**
 * Il marchio sopra la pianta o la vista 3D, se l'account lo prevede. Non riceve tocchi: sotto si continua a lavorare.
 */
@Composable
fun WatermarkOverlay(modifier: Modifier = Modifier) {
    if (!Rights.showsWatermark(LocalPlatform.current.account)) return
    val measurer = rememberTextMeasurer()
    Canvas(modifier) { with(Watermark) { drawWatermark(measurer, 22.dp.toPx()) } }
}
