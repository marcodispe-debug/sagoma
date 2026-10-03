package com.sagoma.planimetria.assets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LruByteCacheTest {
    private fun cache(max: Long) = LruByteCache<ByteArray>(max) { it.size.toLong() }

    @Test
    fun `tiene fino al limite e butta via il meno recente`() {
        val c = cache(10)
        c["a"] = ByteArray(4)
        c["b"] = ByteArray(4)
        c["c"] = ByteArray(4) // 12 > 10: via "a"
        assertNull(c["a"])
        assertEquals(4, c["b"]!!.size)
        assertEquals(4, c["c"]!!.size)
        assertEquals(8L, c.totalBytes)
        assertEquals(2, c.size)
    }

    @Test
    fun `leggere un valore lo rende recente`() {
        val c = cache(10)
        c["a"] = ByteArray(4)
        c["b"] = ByteArray(4)
        c["a"] // "a" torna recente, quindi "b" è il meno recente
        c["c"] = ByteArray(4)
        assertEquals(4, c["a"]!!.size)
        assertNull(c["b"])
        assertEquals(4, c["c"]!!.size)
    }

    @Test
    fun `sostituire un valore aggiorna il totale`() {
        val c = cache(100)
        c["a"] = ByteArray(40)
        c["a"] = ByteArray(10)
        assertEquals(10L, c.totalBytes)
        assertEquals(1, c.size)
    }

    @Test
    fun `un valore piu grande del limite non si tiene e non butta via gli altri`() {
        val c = cache(10)
        c["a"] = ByteArray(4)
        c["grande"] = ByteArray(11)
        assertNull(c["grande"])
        assertEquals(4, c["a"]!!.size)
        assertEquals(4L, c.totalBytes)
    }

    @Test
    fun `getOrLoad carica una volta sola e non tiene i null`() {
        val c = cache(100)
        var loads = 0
        repeat(3) { c.getOrLoad("a") { loads++; ByteArray(5) } }
        assertEquals(1, loads)
        var misses = 0
        repeat(3) { assertNull(c.getOrLoad("manca") { misses++; null }) }
        assertEquals(3, misses) // come prima: un file che non c'è si riprova a ogni richiesta
    }

    @Test
    fun `il totale non supera mai il limite con molti valori`() {
        val c = cache(1000)
        for (i in 0 until 500) {
            c["k$i"] = ByteArray(1 + i % 37)
            assertTrue(c.totalBytes <= 1000)
        }
        c.clear()
        assertEquals(0L, c.totalBytes)
        assertEquals(0, c.size)
    }
}
