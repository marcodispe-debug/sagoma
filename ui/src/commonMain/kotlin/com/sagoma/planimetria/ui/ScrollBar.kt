package com.sagoma.planimetria.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Barra di scorrimento verticale per un contenuto con [state]: una linea sottile con il cursore, che si trascina
 * o si tocca per saltare in quel punto. Alta quanto la parte visibile; non compare se il contenuto ci sta tutto.
 * Va messa sopra il contenuto (in un Box, allineata in alto a destra) lasciando a destra un margine libero.
 */
@Composable
internal fun ScrollBar(state: ScrollState, modifier: Modifier = Modifier) {
    val max = state.maxValue
    val viewport = state.viewportSize
    if (max <= 0 || max == Int.MAX_VALUE || viewport <= 0) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val minThumb = with(density) { 32.dp.toPx() }
    val track = viewport.toFloat()
    val thumb = (track * track / (track + max)).coerceIn(minThumb.coerceAtMost(track), track)
    val free = (track - thumb).coerceAtLeast(1f)
    val top = free * state.value / max
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .width(14.dp)
            .height(with(density) { track.toDp() })
            // Trascinando il cursore il contenuto scorre in proporzione (tutta la corsa = tutto il contenuto).
            .draggable(rememberDraggableState { d -> state.dispatchRawDelta(d * max / free) }, Orientation.Vertical)
            .pointerInput(state) {
                detectTapGestures { p -> scope.launch { state.animateScrollTo(((p.y - thumb / 2) / free * max).roundToInt().coerceIn(0, max)) } }
            },
    ) {
        Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().background(color.copy(alpha = 0.25f), RoundedCornerShape(1.dp)))
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, top.roundToInt()) }
                .width(6.dp)
                .height(with(density) { thumb.toDp() })
                .background(color.copy(alpha = 0.65f), RoundedCornerShape(3.dp)),
        )
    }
}
