package com.sagoma.planimetria.assets

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** L'adattatore di default di [AssetCache.openWrite] per cache senza scrittura a blocchi (qui una cache in memoria). */
class BufferingAssetWriterTest {
    private class MemCache(override val maxBytes: Long = 1_000) : AssetCache {
        val files = LinkedHashMap<String, ByteArray>()
        var puts = 0
        override val usedBytes get() = files.values.sumOf { it.size.toLong() }
        override fun availability(path: String) = if (path in files) AssetAvailability.Available else AssetAvailability.Unavailable
        override fun peek(path: String) = files[path]
        override fun list() = emptyList<CachedAssetInfo>()
        override fun info(path: String) = files[path]?.let { CachedAssetInfo(path, it.size.toLong(), sha(it), 0, false) }
        override fun put(path: String, bytes: ByteArray, sha256: String?): AssetPutResult {
            puts++
            if (sha256 != null && !sha256.equals(sha(bytes), true)) return AssetPutResult.HashMismatch
            files[path] = bytes
            return AssetPutResult.Stored
        }
        override fun remove(path: String) = files.remove(path) != null
        override fun setPinned(owner: String, paths: Collection<String>) {}
        override fun verify(path: String) = path in files
        override fun verifyAll() = emptyList<String>()
        override fun flush() {}
        override fun clear() = files.clear()
    }

    companion object {
        fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }

    private fun bytes(n: Int) = ByteArray(n) { (it * 7).toByte() }

    @Test
    fun `accumula e al commit usa put`() {
        val c = MemCache()
        val d = bytes(300)
        val w = c.openWrite("a.bin", 300, sha(d))
        assertEquals(AssetWriterState.Open, w.state)
        for (o in 0 until 300 step 40) assertEquals(AssetWriteResult.Ok, w.write(d, o, minOf(40, 300 - o)))
        assertEquals(300L, w.bytesWritten)
        assertNull(c.peek("a.bin"))
        assertEquals(0, c.puts)
        assertEquals(AssetPutResult.Stored, w.commit())
        assertContentEquals(d, c.peek("a.bin"))
        assertEquals(AssetWriterState.Committed, w.state)
        assertEquals(AssetPutResult.Stored, w.commit())
        assertEquals(1, c.puts)
    }

    @Test
    fun `impronta sbagliata dimensione sbagliata e abort`() {
        val c = MemCache()
        val d = bytes(100)
        val w1 = c.openWrite("a.bin", 100, sha(bytes(99)))
        w1.write(d)
        assertEquals(AssetPutResult.HashMismatch, w1.commit())

        val w2 = c.openWrite("b.bin", 100, sha(d))
        w2.write(d, 0, 50)
        assertEquals(AssetPutResult.SizeMismatch, w2.commit())

        val w3 = c.openWrite("c.bin", 50, sha(d))
        assertEquals(AssetWriteResult.TooMuchData, w3.write(d))
        assertEquals(AssetWriteResult.Closed, w3.write(d, 0, 1))
        assertEquals(AssetPutResult.SizeMismatch, w3.commit())

        val w4 = c.openWrite("d.bin", 100, sha(d))
        w4.write(d)
        w4.abort()
        assertEquals(AssetPutResult.Aborted, w4.commit())
        assertEquals(0, c.puts - 1) // solo w1 ha chiamato put
        assertEquals(0, c.files.size)
    }

    @Test
    fun `dimensione zero e troppo grande`() {
        val c = MemCache(maxBytes = 100)
        assertEquals(AssetPutResult.Stored, c.openWrite("e.bin", 0, sha(ByteArray(0))).commit())
        val w = c.openWrite("big.bin", 101, sha(bytes(101)))
        assertEquals(AssetWriteResult.Closed, w.write(bytes(1)))
        assertEquals(AssetPutResult.TooLarge, w.commit())
        assertEquals(AssetPutResult.SizeMismatch, c.openWrite("n.bin", -5, sha(ByteArray(0))).commit())
    }
}
