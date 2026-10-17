package com.sagoma.planimetria.assets

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * File sintetico più grande della memoria di test (Gradle dà 512 MB di heap ai test): se lo scrittore tenesse il
 * file in memoria, andrebbe in OutOfMemoryError. Solo cartelle temporanee, un file da 640 MB per pochi secondi.
 */
class HugeAssetStreamingTest {
    @Test
    fun `file da 640 MB in streaming senza tenerlo in memoria`() {
        val total = 640L * 1024 * 1024
        val chunk = ByteArray(64 * 1024) { (it % 251).toByte() }
        val md = MessageDigest.getInstance("SHA-256")
        var n = 0L
        while (n < total) { md.update(chunk); n += chunk.size }
        val expected = md.digest().joinToString("") { "%02x".format(it) }

        val root = kotlin.io.path.createTempDirectory("sagoma-huge-").toFile()
        try {
            val cache = DiskAssetCache(root, total + 1024)
            val w = cache.openWrite("env/huge.bin", total, expected)
            n = 0
            while (n < total) {
                assertEquals(AssetWriteResult.Ok, w.write(chunk))
                n += chunk.size
            }
            assertEquals(total, w.bytesWritten)
            assertEquals(AssetPutResult.Stored, w.commit())
            assertEquals(total, cache.info("env/huge.bin")!!.size)
            assertEquals(expected, cache.info("env/huge.bin")!!.sha256)
            assertEquals(total, File(root, "data/env/huge.bin").length())
            assertTrue(File(root, "tmp").listFiles().isNullOrEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
