package com.sagoma.planimetria.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import com.sagoma.planimetria.ui.Marchio
import com.sagoma.planimetria.ui.ScrittaSagoma
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Toolkit

/**
 * La barra in alto della finestra del PC, scura col colore del marchio e la scritta «Sagoma» con la «o» a pianta, al posto
 * della barra di Windows (la finestra è senza bordi: [androidx.compose.ui.window.WindowDecoration.Undecorated]). Si trascina
 * per spostare la finestra, col doppio clic si ingrandisce o torna com'era; a destra riduci, ingrandisci e chiudi.
 */
@Composable
internal fun FrameWindowScope.BarraFinestra(onClose: () -> Unit) {
    val ingrandita = remember { Ingrandimento(window) }
    Row(Modifier.fillMaxWidth().height(ALTEZZA).background(Marchio.Inchiostro), verticalAlignment = Alignment.CenterVertically) {
        val scritta: @Composable () -> Unit = {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onDoubleTap = { ingrandita.cambia() }) },
                contentAlignment = Alignment.CenterStart,
            ) {
                ScrittaSagoma(Modifier.padding(start = 14.dp).height(17.dp), lettere = Color.White)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            // Ingrandita non si sposta trascinando: prima si rimette com'era (doppio clic o pulsante).
            if (ingrandita.attiva) scritta() else WindowDraggableArea { scritta() }
        }
        PulsanteBarra(onClick = { window.extendedState = window.extendedState or Frame.ICONIFIED }) { c ->
            drawLine(c, Offset(size.width * 0.5f - 5.dp.toPx(), center.y), Offset(size.width * 0.5f + 5.dp.toPx(), center.y), 1.dp.toPx())
        }
        PulsanteBarra(onClick = { ingrandita.cambia() }) { c ->
            val l = 9.dp.toPx()
            val stroke = Stroke(1.dp.toPx())
            if (ingrandita.attiva) {
                val d = 2.dp.toPx()
                drawRect(c, Offset(center.x - l / 2, center.y - l / 2 + d), Size(l - d, l - d), style = stroke)
                drawLine(c, Offset(center.x - l / 2 + d, center.y - l / 2), Offset(center.x + l / 2, center.y - l / 2), stroke.width)
                drawLine(c, Offset(center.x + l / 2, center.y - l / 2), Offset(center.x + l / 2, center.y + l / 2 - d), stroke.width)
            } else {
                drawRect(c, Offset(center.x - l / 2, center.y - l / 2), Size(l, l), style = stroke)
            }
        }
        PulsanteBarra(onClick = onClose, sopra = Color(0xFFE81123)) { c ->
            val l = 5.dp.toPx()
            drawLine(c, Offset(center.x - l, center.y - l), Offset(center.x + l, center.y + l), 1.dp.toPx())
            drawLine(c, Offset(center.x - l, center.y + l), Offset(center.x + l, center.y - l), 1.dp.toPx())
        }
    }
}

private val ALTEZZA = 34.dp

/** Pulsante a destra nella barra, largo come quelli di Windows; col mouse sopra si schiarisce (rosso quello di chiusura). */
@Composable
private fun PulsanteBarra(onClick: () -> Unit, sopra: Color = Color.White.copy(alpha = 0.12f), segno: DrawScope.(Color) -> Unit) {
    val interazione = remember { MutableInteractionSource() }
    val evidenziato by interazione.collectIsHoveredAsState()
    Canvas(
        Modifier.width(46.dp).fillMaxHeight()
            .background(if (evidenziato) sopra else Color.Transparent)
            .hoverable(interazione)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
    ) { segno(Color.White) }
}

/**
 * Ingrandire a mano invece che con lo stato «massimizzato» di Windows: una finestra senza bordi massimizzata copre anche la
 * barra delle applicazioni. Qui prende l'area libera dello schermo dove si trova (anche col secondo monitor), e si ricorda
 * dov'era per tornare com'era.
 */
private class Ingrandimento(private val window: java.awt.Window) {
    var attiva by mutableStateOf(false)
        private set
    private var prima: Rectangle? = null

    fun cambia() {
        if (attiva) {
            prima?.let { window.bounds = it }
            attiva = false
        } else {
            val gc = window.graphicsConfiguration
            val schermo = gc.bounds
            val bordi = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            prima = window.bounds
            window.bounds = Rectangle(
                schermo.x + bordi.left, schermo.y + bordi.top,
                schermo.width - bordi.left - bordi.right, schermo.height - bordi.top - bordi.bottom,
            )
            attiva = true
        }
    }
}
