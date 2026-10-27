package com.sagoma.planimetria.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Nomi dei file salvati (tavola PDF, computo, progetto esportato): le lettere accentate restano. */
class FileNameTest {
    @Test
    fun accentiRestano() {
        assertEquals("Casa di Nicolò", fileNameOf("Casa di Nicolò", "progetto"))
        assertEquals("Città alta - piano 2", fileNameOf("Città alta - piano 2", "progetto"))
    }

    @Test
    fun caratteriVietatiNeiNomiDeiFileSpariscono() {
        assertEquals("Villa Rossi 1-2", fileNameOf("Villa \"Rossi\" 1/2*?:<>|".replace("1/2", "1-2"), "progetto"))
        assertEquals("progetto", fileNameOf("///", "progetto"))
        assertEquals("progetto", fileNameOf("   ", "progetto"))
    }
}
