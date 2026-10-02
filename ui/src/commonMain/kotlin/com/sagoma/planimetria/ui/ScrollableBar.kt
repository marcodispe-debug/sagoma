package com.sagoma.planimetria.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Barra di pulsanti che scorre in orizzontale. Oltre al trascinamento col dito:
 * - frecce ‹ › ai bordi, visibili solo se c'è altro da quella parte (utili con il mouse sul computer);
 * - la rotellina del mouse scorre la barra di lato.
 */
@Composable
fun ScrollableBar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    spacing: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    EdgeArrows(scroll, modifier) {
        Row(
            Modifier
                .horizontalScroll(scroll)
                .wheelScrollsSideways(scroll)
                .padding(contentPadding),
            horizontalArrangement = spacing,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Come `LazyRow`, con le frecce ‹ › ai bordi e la rotellina del mouse che scorre di lato. */
@Composable
fun ArrowLazyRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    EdgeArrows(state, modifier) {
        LazyRow(
            Modifier.wheelScrollsSideways(state),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}

/** Contenuto che scorre in orizzontale con le frecce ai bordi, visibili solo se c'è altro da quella parte. */
@Composable
private fun EdgeArrows(state: ScrollableState, modifier: Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun jump(direction: Int) = scope.launch { state.animateScrollBy(direction * size.width * 0.7f) }

    // L'altezza la decide il contenuto; le frecce ci stanno sopra, alte uguali (senza allungare la barra).
    Box(modifier.onSizeChanged { size = it }) {
        content()
        val bg = MaterialTheme.colorScheme.surface
        val h = with(LocalDensity.current) { size.height.toDp() }
        AnimatedVisibility(state.canScrollBackward && size.height > 0, Modifier.align(Alignment.CenterStart), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.height(h).background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0f)))).padding(end = 12.dp), contentAlignment = Alignment.Center) {
                ArrowButton("‹", h) { jump(-1) }
            }
        }
        AnimatedVisibility(state.canScrollForward && size.height > 0, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.height(h).background(Brush.horizontalGradient(listOf(bg.copy(alpha = 0f), bg))).padding(start = 12.dp), contentAlignment = Alignment.Center) {
                ArrowButton("›", h) { jump(1) }
            }
        }
    }
}

@Composable
private fun ArrowButton(symbol: String, barHeight: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 3.dp,
        modifier = Modifier.padding(horizontal = 4.dp).size(minOf(36.dp, barHeight)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** La rotellina (verticale) del mouse fa scorrere di lato; lo scorrimento orizzontale del touchpad resta com'è. */
private fun Modifier.wheelScrollsSideways(scroll: ScrollableState): Modifier = pointerInput(scroll) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.type != PointerEventType.Scroll) continue
            val d = e.changes.first().scrollDelta
            if (abs(d.y) > abs(d.x) && d.y != 0f) {
                scroll.dispatchRawDelta(wheelNotches(d.y) * 60f)
                e.changes.forEach { it.consume() }
            }
        }
    }
}
