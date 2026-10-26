package com.sagoma.planimetria.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Numeri scritti nei campi: virgola o punto, spazi intorno; mai infiniti o «non numeri» che rovinerebbero il progetto. */
class ParseNumberTest {
    @Test
    fun `virgola, punto e spazi`() {
        assertEquals(3.5, parseNumber("3,5"))
        assertEquals(3.5, parseNumber("3.5"))
        assertEquals(350.0, parseNumber(" 350 "))
        assertEquals(-20.0, parseNumber("-20"))
        assertEquals(350.0, parsePositive("350"))
    }

    @Test
    fun `niente infiniti ne non numeri`() {
        for (t in listOf("NaN", "Infinity", "-Infinity", "1e999", "", " ", "abc", "3,5,0")) {
            assertNull(parseNumber(t), "«$t»")
            assertNull(parsePositive(t), "«$t»")
        }
        assertNull(parsePositive("0"))
        assertNull(parsePositive("-5"))
    }
}
