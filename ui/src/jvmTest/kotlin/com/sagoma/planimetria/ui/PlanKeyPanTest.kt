package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
/** WASD nella pianta: tenuti premuti fanno scorrere il disegno in modo continuo, in proporzione al tempo. */
class PlanKeyPanTest {
    private fun PlanKeyPan.press(k: Key) = onKey(KeyEvent(k, KeyEventType.KeyDown))
    private fun PlanKeyPan.release(k: Key) = onKey(KeyEvent(k, KeyEventType.KeyUp))

    @Test
    fun `scorre in proporzione al tempo e si ferma lasciando il tasto`() {
        val p = PlanKeyPan()
        assertTrue(p.press(Key.D))
        assertTrue(p.moving)
        // D: la vista va a destra, il disegno scorre a sinistra.
        assertEquals(Offset(-90f, 0f), p.step(0.1))
        assertEquals(Offset(-45f, 0f), p.step(0.05))
        assertTrue(p.release(Key.D))
        assertFalse(p.moving)
        assertEquals(Offset.Zero, p.step(0.1))
    }

    @Test
    fun `in diagonale con due tasti, con Maiusc tre volte piu veloce`() {
        val p = PlanKeyPan()
        p.press(Key.W)
        p.press(Key.A)
        assertEquals(Offset(90f, 90f), p.step(0.1))
        // Maiusc non è un tasto di movimento: non si consuma (resta per le altre scorciatoie).
        assertFalse(p.press(Key.ShiftLeft))
        assertEquals(Offset(270f, 270f), p.step(0.1))
        // Tasto ripetuto dal sistema mentre è tenuto: conta una volta sola.
        p.press(Key.W)
        p.release(Key.W)
        assertEquals(Offset(270f, 0f), p.step(0.1))
    }

    @Test
    fun `gli altri tasti non si toccano`() {
        val p = PlanKeyPan()
        assertFalse(p.press(Key.Delete))
        assertFalse(p.moving)
    }
}
