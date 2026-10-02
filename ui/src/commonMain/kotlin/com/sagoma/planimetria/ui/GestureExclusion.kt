package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Zone invisibili sopra le maniglie trascinabili (angoli, estremità e maniglie del metro): chiedono
 * ad Android di non interpretare come gesto "indietro" un trascinamento che parte vicino al bordo
 * dello schermo. Non hanno input proprio, quindi i tocchi arrivano comunque al canvas sottostante.
 */
@Composable
fun GestureExclusionZones(points: List<Offset>, size: Dp = 48.dp) {
    val platform = LocalPlatform.current
    for (p in points) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place((p.x - placeable.width / 2f).roundToInt(), (p.y - placeable.height / 2f).roundToInt())
                    }
                }
                .size(size)
                .let(platform::gestureExclusion),
        )
    }
}
