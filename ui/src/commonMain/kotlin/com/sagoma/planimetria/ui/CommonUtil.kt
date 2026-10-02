package com.sagoma.planimetria.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Funzioni di appoggio in Kotlin puro che sostituiscono quelle di Android (colori HSV, misure dello schermo).

/** Colore ARGB → tonalità (0–360), saturazione e luminosità (0–1), come `android.graphics.Color.colorToHSV`. */
fun colorToHsv(argb: Int, out: FloatArray) {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    out[0] = if (h < 0) h + 360f else h
    out[1] = if (mx == 0f) 0f else d / mx
    out[2] = mx
}

/** Tonalità, saturazione, luminosità → colore ARGB opaco, come `android.graphics.Color.HSVToColor`. */
fun hsvToColor(hsv: FloatArray): Int {
    val h = ((hsv[0] % 360f) + 360f) % 360f
    val s = hsv[1].coerceIn(0f, 1f)
    val v = hsv[2].coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2f - 1))
    val m = v - c
    val (r, g, b) = when (floor(h / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/**
 * Scatti della rotellina del mouse, uguali su tutte le piattaforme: il computer dà ±1 per scatto, i browser
 * valori molto più grandi (in pixel). Si tiene il verso e al massimo uno scatto per evento; i piccoli
 * valori del touchpad restano proporzionali.
 */
fun wheelNotches(deltaY: Float): Float = deltaY.coerceIn(-1f, 1f)

/** Chiede il fuoco della tastiera; se l'elemento non è (ancora) nella pagina non succede nulla. */
fun androidx.compose.ui.focus.FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}

/** Altezza della finestra dell'app (al posto di `LocalConfiguration.screenHeightDp`). */
@Composable
fun windowHeight(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
