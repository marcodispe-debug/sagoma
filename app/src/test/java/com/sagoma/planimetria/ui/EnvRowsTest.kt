package com.sagoma.planimetria.ui

import org.junit.Test
import org.junit.Assert.assertEquals

/** Riordino di un livello della luce ambiente per caricarlo una riga alla volta (envRowsByFace). */
class EnvRowsTest {

    @Test
    fun `ogni riga contiene la stessa riga delle 6 facce in ordine`() {
        val dim = 3
        // Ogni pixel (4 byte) vale faccia*100 + riga*10 + colonna; davanti 5 byte da saltare.
        val skip = 5
        val bytes = ByteArray(skip + dim * dim * 6 * 4)
        for (f in 0 until 6) for (y in 0 until dim) for (x in 0 until dim) {
            bytes[skip + ((f * dim + y) * dim + x) * 4] = (f * 100 + y * 10 + x).toByte()
        }
        val out = envRowsByFace(bytes, skip, dim)
        assertEquals(dim * dim * 6 * 4, out.size)
        for (y in 0 until dim) for (f in 0 until 6) for (x in 0 until dim) {
            assertEquals((f * 100 + y * 10 + x).toByte(), out[((y * 6 + f) * dim + x) * 4])
        }
    }
}
