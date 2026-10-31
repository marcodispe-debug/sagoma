package com.sagoma.planimetria.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * WASD e frecce tenuti premuti nella pianta: il disegno scorre in modo continuo, fotogramma per fotogramma, come la vista 3D,
 * e non a scatti con la ripetizione dei tasti del sistema. Con Maiusc tre volte più veloce.
 */
class PlanKeyPan {
    /** Tasti tenuti premuti (movimento e Maiusc). */
    val keys = mutableStateListOf<Key>()

    /** Un tasto di movimento è tenuto premuto: la pianta scorre a ogni fotogramma. */
    val moving: Boolean get() = keys.any { direction(it) != null }

    /** Tasto premuto o lasciato: true se è un tasto di movimento (consumato), false per tutti gli altri (anche Maiusc). */
    fun onKey(e: KeyEvent): Boolean {
        val k = e.key
        val move = direction(k) != null
        if (!move && k != Key.ShiftLeft && k != Key.ShiftRight) return false
        when (e.type) {
            KeyEventType.KeyDown -> if (k !in keys) keys += k
            KeyEventType.KeyUp -> keys -= k
        }
        return move
    }

    /** Di quanti pixel scorre la pianta in `dt` secondi con i tasti tenuti adesso. */
    fun step(dt: Double): Offset {
        var x = 0f
        var y = 0f
        for (k in keys) direction(k)?.let { (dx, dy) -> x += dx; y += dy }
        if (x == 0f && y == 0f) return Offset.Zero
        val fast = if (Key.ShiftLeft in keys || Key.ShiftRight in keys) 3f else 1f
        val d = (SPEED * fast * dt).toFloat()
        return Offset(x.coerceIn(-1f, 1f) * d, y.coerceIn(-1f, 1f) * d)
    }

    companion object {
        /** Pixel al secondo. */
        const val SPEED = 900.0

        /**
         * Direzione in cui scorre il disegno (la vista va dalla parte opposta, come con le frecce di un gioco: W o ↑ guardano
         * più in su). null = non è un tasto di movimento.
         */
        fun direction(k: Key): Pair<Float, Float>? = when (k) {
            Key.W, Key.DirectionUp -> 0f to 1f
            Key.S, Key.DirectionDown -> 0f to -1f
            Key.A, Key.DirectionLeft -> 1f to 0f
            Key.D, Key.DirectionRight -> -1f to 0f
            else -> null
        }
    }
}
