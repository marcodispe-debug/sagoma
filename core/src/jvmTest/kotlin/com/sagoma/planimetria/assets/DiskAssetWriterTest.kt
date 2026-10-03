package com.sagoma.planimetria.assets

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scrittura a blocchi di [DiskAssetCache.openWrite]: solo cartelle temporanee. */
class DiskAssetWriterTest {
    private val dirs = mutableListOf<File>()
    private var clock = 1_000L

    @AfterTest
    fun cleanup() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    private fun newDir(): File = kotlin.io.path.createTempDirectory("sagoma-writer-").toFile().also { dirs += it }
    private fun cache(root: File = newDir(), max: Long = 10_000L) = DiskAssetCache(root, max) { clock++ }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun bytes(n: Int, seed: Int = 0) = ByteArray(n) { (it * 31 + seed).toByte() }
    private fun File.tmpFiles() = File(this, "tmp").listFiles()?.toList() ?: emptyList()

    private fun AssetWriter.writeAll(data: ByteArray, chunk: Int) {
        var o = 0
        while (o < data.size) {
            val n = minOf(chunk, data.size - o)
            assertEquals(AssetWriteResult.Ok, write(data, o, n))
            o += n
        }
    }

    // ---- scrittore normale ----

    @Test
    fun `file piccolo e medio con impronta e dimensione giuste`() {
        for (size in listOf(1, 100, 4_321)) {
            val root = newDir()
            val c = cache(root)
            val data = bytes(size, size)
            val w = c.openWrite("furniture/a.glb", size.toLong(), sha(data))
            w.writeAll(data, 77)
            assertEquals(size.toLong(), w.bytesWritten)
            assertEquals(AssetPutResult.Stored, w.commit())
            assertEquals(AssetWriterState.Committed, w.state)
            assertContentEquals(data, c.peek("furniture/a.glb"))
            assertEquals(sha(data), c.info("furniture/a.glb")!!.sha256)
            assertEquals(size.toLong(), c.usedBytes)
            assertTrue(root.tmpFiles().isEmpty())
        }
    }

    @Test
    fun `impronta sbagliata non salva niente`() {
        val root = newDir()
        val c = cache(root)
        val data = bytes(500)
        val w = c.openWrite("a.bin", 500, sha(bytes(500, 9)))
        w.writeAll(data, 100)
        assertEquals(AssetPutResult.HashMismatch, w.commit())
        assertNull(c.info("a.bin"))
        assertNull(c.peek("a.bin"))
        assertEquals(0L, c.usedBytes)
        assertTrue(root.tmpFiles().isEmpty())
        assertEquals(AssetWriterState.Closed, w.state)
    }

    @Test
    fun `impronta attesa in maiuscolo e accettata`() {
        val c = cache()
        val data = bytes(50)
        val w = c.openWrite("a.bin", 50, sha(data).uppercase())
        w.writeAll(data, 50)
        assertEquals(AssetPutResult.Stored, w.commit())
        assertEquals(sha(data), c.info("a.bin")!!.sha256) // in cache sempre minuscola
    }

    @Test
    fun `impronta attesa malformata dall inizio`() {
        val w = cache().openWrite("a.bin", 5, "xyz")
        assertEquals(AssetWriteResult.Closed, w.write(ByteArray(5)))
        assertEquals(AssetPutResult.HashMismatch, w.commit())
    }

    @Test
    fun `dimensione dichiarata sbagliata`() {
        val c = cache()
        val data = bytes(100)
        // dichiarata piu grande: il commit presto o tardi la scopre
        val w1 = c.openWrite("a.bin", 200, sha(data))
        w1.writeAll(data, 30)
        assertEquals(AssetPutResult.SizeMismatch, w1.commit())
        // dichiarata piu piccola: i byte in piu sono rifiutati subito
        val w2 = c.openWrite("b.bin", 60, sha(data))
        assertEquals(AssetWriteResult.Ok, w2.write(data, 0, 60))
        assertEquals(AssetWriteResult.TooMuchData, w2.write(data, 60, 1))
        assertNull(c.info("a.bin"))
        assertNull(c.info("b.bin"))
    }

    // ---- limiti ----

    @Test
    fun `byte oltre la dimensione dichiarata rifiutati subito e lo scrittore e fallito`() {
        val root = newDir()
        val c = cache(root)
        val data = bytes(100)
        val w = c.openWrite("a.bin", 100, sha(data))
        assertEquals(AssetWriteResult.Ok, w.write(data, 0, 90))
        assertEquals(AssetWriteResult.TooMuchData, w.write(data, 0, 11))
        assertEquals(90L, w.bytesWritten) // il blocco rifiutato non conta
        assertEquals(AssetWriteResult.Closed, w.write(data, 0, 1)) // e ora e chiuso
        assertTrue(root.tmpFiles().isEmpty()) // il temporaneo e gia sparito
        assertEquals(AssetPutResult.SizeMismatch, w.commit())
        assertNull(c.info("a.bin"))
    }

    @Test
    fun `commit anticipato`() {
        val root = newDir()
        val c = cache(root)
        val data = bytes(100)
        val w = c.openWrite("a.bin", 100, sha(data))
        w.write(data, 0, 99)
        assertEquals(AssetPutResult.SizeMismatch, w.commit())
        assertNull(c.peek("a.bin"))
        assertTrue(root.tmpFiles().isEmpty())
    }

    @Test
    fun `dimensione zero`() {
        val c = cache()
        val w = c.openWrite("empty.bin", 0, sha(ByteArray(0)))
        assertEquals(AssetPutResult.Stored, w.commit())
        assertEquals(0L, c.info("empty.bin")!!.size)
        assertContentEquals(ByteArray(0), c.peek("empty.bin"))
        // con dimensione zero un byte e gia troppo
        val w2 = c.openWrite("e2.bin", 0, sha(ByteArray(0)))
        assertEquals(AssetWriteResult.TooMuchData, w2.write(ByteArray(1)))
    }

    @Test
    fun `file piu grande del limite della cache`() {
        val c = cache(max = 1_000)
        val w = c.openWrite("big.bin", 1_001, sha(bytes(1_001)))
        assertEquals(AssetWriteResult.Closed, w.write(bytes(10)))
        assertEquals(AssetPutResult.TooLarge, w.commit())
        assertEquals(0L, c.usedBytes)
    }

    @Test
    fun `dimensione negativa`() {
        assertEquals(AssetPutResult.SizeMismatch, cache().openWrite("a.bin", -1, sha(ByteArray(0))).commit())
    }

    @Test
    fun `blocchi con indici non validi sono un errore di chi chiama`() {
        val w = cache().openWrite("a.bin", 10, sha(bytes(10)))
        assertFailsWith<IndexOutOfBoundsException> { w.write(ByteArray(5), 3, 5) }
        assertFailsWith<IndexOutOfBoundsException> { w.write(ByteArray(5), -1, 2) }
        w.abort()
    }

    @Test
    fun `file piu grande di un blocco e dell eviction`() {
        // 3 file da 400 in una cache da 1000: il piu vecchio esce, come con put
        val c = cache(max = 1_000)
        for (n in 1..3) {
            val d = bytes(400, n)
            val w = c.openWrite("f$n.bin", 400, sha(d))
            w.writeAll(d, 64)
            assertEquals(AssetPutResult.Stored, w.commit())
        }
        assertNull(c.info("f1.bin"))
        assertNotNull(c.info("f2.bin"))
        assertNotNull(c.info("f3.bin"))
        assertEquals(800L, c.usedBytes)
    }

    // ---- atomicita ----

    @Test
    fun `il file non si vede prima del commit e abort lo scarta`() {
        val root = newDir()
        val c = cache(root)
        val data = bytes(300)
        val w = c.openWrite("a.bin", 300, sha(data))
        w.writeAll(data, 100)
        assertNull(c.peek("a.bin"))
        assertNull(c.info("a.bin"))
        assertEquals(AssetAvailability.Unavailable, c.availability("a.bin"))
        assertTrue(c.list().isEmpty())
        assertEquals(0L, c.usedBytes)
        assertFalse(File(root, "data/a.bin").exists())
        assertEquals(1, root.tmpFiles().size)
        w.abort()
        assertTrue(root.tmpFiles().isEmpty())
        assertNull(c.peek("a.bin"))
        assertEquals(AssetPutResult.Aborted, w.commit())
        assertEquals(AssetWriteResult.Closed, w.write(data, 0, 1))
        w.abort() // ripetuto: niente
    }

    @Test
    fun `abort dopo commit riuscito non toglie il file`() {
        val c = cache()
        val d = bytes(20)
        val w = c.openWrite("a.bin", 20, sha(d))
        w.writeAll(d, 20)
        assertEquals(AssetPutResult.Stored, w.commit())
        w.abort()
        assertEquals(AssetPutResult.Stored, w.commit()) // stesso esito
        assertContentEquals(d, c.peek("a.bin"))
    }

    @Test
    fun `un file sostituito resta quello vecchio finche il commit non riesce`() {
        val c = cache()
        val old = bytes(50, 1)
        c.put("a.bin", old)
        val new = bytes(50, 2)
        val w = c.openWrite("a.bin", 50, sha(new))
        w.writeAll(new, 10)
        assertContentEquals(old, c.peek("a.bin"))
        w.abort()
        assertContentEquals(old, c.peek("a.bin")) // un abort non rovina la voce esistente
        val w2 = c.openWrite("a.bin", 50, sha(new))
        w2.writeAll(new, 10)
        assertEquals(AssetPutResult.Stored, w2.commit())
        assertContentEquals(new, c.peek("a.bin"))
        assertEquals(50L, c.usedBytes)
    }

    private class FailingOut(val inner: OutputStream, val failAfter: Int = Int.MAX_VALUE, val failFlush: Boolean = false) : OutputStream() {
        var n = 0
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (n + len > failAfter) throw IOException("disco pieno (simulato)")
            n += len
            inner.write(b, off, len)
        }
        override fun flush() {
            if (failFlush) throw IOException("flush (simulato)")
            inner.flush()
        }
        override fun close() = inner.close()
    }

    @Test
    fun `errore durante la scrittura non lascia voci ne temporanei`() {
        val root = newDir()
        val c = cache(root)
        c.outputWrapper = { FailingOut(it, failAfter = 150) }
        val data = bytes(300)
        val w = c.openWrite("a.bin", 300, sha(data))
        assertEquals(AssetWriteResult.Ok, w.write(data, 0, 100))
        assertEquals(AssetWriteResult.IoError, w.write(data, 100, 100))
        assertEquals(AssetWriteResult.Closed, w.write(data, 200, 100))
        assertTrue(root.tmpFiles().isEmpty())
        assertEquals(AssetPutResult.IoError, w.commit())
        assertNull(c.info("a.bin"))
        assertEquals(0L, c.usedBytes)
    }

    @Test
    fun `errore durante il commit non lascia voci ne temporanei e il vecchio file resta`() {
        val root = newDir()
        val c = cache(root)
        val old = bytes(40, 5)
        c.put("a.bin", old)
        val data = bytes(100)

        c.outputWrapper = { FailingOut(it, failFlush = true) }
        val w1 = c.openWrite("a.bin", 100, sha(data))
        w1.writeAll(data, 100)
        assertEquals(AssetPutResult.IoError, w1.commit())

        c.outputWrapper = { it }
        c.beforeInstall = { throw IOException("spostamento (simulato)") }
        val w2 = c.openWrite("a.bin", 100, sha(data))
        w2.writeAll(data, 100)
        assertEquals(AssetPutResult.IoError, w2.commit())

        assertTrue(root.tmpFiles().isEmpty())
        assertContentEquals(old, c.peek("a.bin"))
        assertEquals(40L, c.usedBytes)
        // la cache continua a funzionare
        c.beforeInstall = {}
        val w3 = c.openWrite("a.bin", 100, sha(data))
        w3.writeAll(data, 100)
        assertEquals(AssetPutResult.Stored, w3.commit())
    }

    @Test
    fun `cartella temporanea non scrivibile dà uno scrittore fallito senza eccezioni`() {
        val root = newDir()
        val c = cache(root)
        File(root, "tmp").deleteRecursively()
        File(root, "tmp").writeText("sono un file, non una cartella")
        val w = c.openWrite("a.bin", 3, sha(bytes(3)))
        assertEquals(AssetWriteResult.Closed, w.write(bytes(3)))
        assertEquals(AssetPutResult.IoError, w.commit())
        assertNull(c.peek("a.bin"))
    }

    @Test
    fun `dopo un riavvio i temporanei di scritture interrotte spariscono e niente e in cache`() {
        val root = newDir()
        val c = cache(root)
        val d = bytes(200)
        val w = c.openWrite("a.bin", 200, sha(d))
        w.writeAll(d, 50)
        // il processo "muore" qui: nessun commit ne abort
        val c2 = cache(root)
        assertTrue(root.tmpFiles().isEmpty())
        assertNull(c2.info("a.bin"))
        assertEquals(0L, c2.usedBytes)
    }

    // ---- sicurezza dei percorsi ----

    @Test
    fun `percorsi non validi`() {
        val root = newDir()
        val c = cache(root)
        for (p in listOf("../x.bin", "a/../../x.bin", "/abs.bin", "\\abs.bin", "", "a//b.bin", "a/b?.bin", "CON.bin", "a./b.bin")) {
            val w = c.openWrite(p, 1, sha(ByteArray(1)))
            assertEquals(AssetWriteResult.Closed, w.write(ByteArray(1)), p)
            assertEquals(AssetPutResult.InvalidPath, w.commit(), p)
        }
        assertTrue(root.tmpFiles().isEmpty())
        assertTrue(c.list().isEmpty())
        assertFalse(File(root.parentFile, "x.bin").exists())
    }

    @Test
    fun `percorsi che differiscono solo per maiuscole sono rifiutati`() {
        val c = cache()
        c.put("A/b.bin", bytes(3))
        val w = c.openWrite("a/B.bin", 3, sha(bytes(3)))
        assertEquals(AssetPutResult.InvalidPath, w.commit())
    }

    // ---- concorrenza ----

    @Test
    fun `due scrittori sullo stesso percorso non rovinano il file finale`() {
        val c = cache(max = 1_000_000)
        val a = bytes(50_000, 1)
        val b = bytes(50_000, 2)
        repeat(20) {
            val barrier = CyclicBarrier(2)
            val results = arrayOfNulls<AssetPutResult>(2)
            val ts = listOf(a, b).mapIndexed { i, d ->
                thread {
                    val w = c.openWrite("same.bin", d.size.toLong(), sha(d))
                    barrier.await()
                    w.writeAll(d, 1_000)
                    results[i] = w.commit()
                }
            }
            ts.forEach { it.join() }
            assertEquals(listOf(AssetPutResult.Stored, AssetPutResult.Stored), results.toList())
            val got = c.peek("same.bin")!!
            assertTrue(got.contentEquals(a) || got.contentEquals(b), "contenuto misto")
            assertEquals(sha(got), c.info("same.bin")!!.sha256)
            assertEquals(50_000L, c.usedBytes)
            assertEquals(1, c.list().size)
            assertTrue(c.verifyAll().isEmpty())
        }
    }

    @Test
    fun `scrittori indipendenti su percorsi diversi`() {
        val root = newDir()
        val c = cache(root, max = 1_000_000)
        val datas = (0 until 8).map { bytes(20_000 + it, it) }
        val ts = datas.mapIndexed { i, d ->
            thread {
                val w = c.openWrite("dir$i/f.bin", d.size.toLong(), sha(d))
                w.writeAll(d, 333)
                assertEquals(AssetPutResult.Stored, w.commit())
            }
        }
        ts.forEach { it.join() }
        datas.forEachIndexed { i, d -> assertContentEquals(d, c.peek("dir$i/f.bin")) }
        assertEquals(datas.sumOf { it.size.toLong() }, c.usedBytes)
        assertTrue(root.tmpFiles().isEmpty())
    }

    @Test
    fun `lettura concorrente mentre si scrive vede solo il file completo`() {
        val c = cache(max = 1_000_000)
        val d = bytes(200_000, 3)
        val seenPartial = java.util.concurrent.atomic.AtomicBoolean(false)
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val reader = thread {
            while (!done.get()) {
                val got = c.peek("a.bin")
                if (got != null && !got.contentEquals(d)) seenPartial.set(true)
            }
        }
        val w = c.openWrite("a.bin", d.size.toLong(), sha(d))
        w.writeAll(d, 100)
        assertEquals(AssetPutResult.Stored, w.commit())
        done.set(true)
        reader.join()
        assertFalse(seenPartial.get())
    }

    // ---- compatibilita con put ----

    @Test
    fun `put e openWrite danno lo stesso risultato`() {
        val d = bytes(777, 4)
        val c1 = cache()
        val c2 = cache()
        assertEquals(AssetPutResult.Stored, c1.put("x/y.bin", d))
        val w = c2.openWrite("x/y.bin", d.size.toLong(), sha(d))
        w.writeAll(d, 50)
        assertEquals(AssetPutResult.Stored, w.commit())
        val i1 = c1.info("x/y.bin")!!
        val i2 = c2.info("x/y.bin")!!
        assertEquals(i1.sha256, i2.sha256)
        assertEquals(i1.size, i2.size)
        assertContentEquals(c1.peek("x/y.bin"), c2.peek("x/y.bin"))
        assertEquals(c1.usedBytes, c2.usedBytes)
        assertEquals(c1.verify("x/y.bin"), c2.verify("x/y.bin"))
    }

    @Test
    fun `i file scritti con openWrite sono persistiti e verificabili dopo riapertura`() {
        val root = newDir()
        val d = bytes(1_234, 6)
        val c = cache(root)
        val w = c.openWrite("a/b.bin", d.size.toLong(), sha(d))
        w.writeAll(d, 100)
        w.commit()
        val c2 = cache(root)
        assertContentEquals(d, c2.peek("a/b.bin"))
        assertTrue(c2.verify("a/b.bin"))
    }

    @Test
    fun `fissaggio ed eviction valgono anche per i file scritti a blocchi`() {
        val c = cache(max = 1_000)
        fun add(p: String, n: Int) {
            val d = bytes(n, n)
            val w = c.openWrite(p, n.toLong(), sha(d))
            w.writeAll(d, 64)
            assertEquals(AssetPutResult.Stored, w.commit())
        }
        add("pinned.bin", 400)
        c.setPinned("proj", listOf("pinned.bin"))
        add("b.bin", 400)
        add("c.bin", 400) // serve spazio: esce b (il piu vecchio non fissato), non pinned
        assertNotNull(c.info("pinned.bin"))
        assertNull(c.info("b.bin"))
        assertNotNull(c.info("c.bin"))
        assertTrue(c.info("pinned.bin")!!.pinned)
    }

    @Test
    fun `put continua a funzionare con scrittori aperti`() {
        val c = cache()
        val d = bytes(100)
        val w = c.openWrite("w.bin", 100, sha(d))
        w.write(d, 0, 50)
        assertEquals(AssetPutResult.Stored, c.put("p.bin", bytes(10)))
        w.write(d, 50, 50)
        assertEquals(AssetPutResult.Stored, w.commit())
        assertEquals(110L, c.usedBytes)
    }

    // ---- memoria ----

    @Test
    fun `con blocchi piccoli la memoria non cresce con la dimensione del file`() {
        // 40 MB sintetici con blocchi da 8 KB: lo scrittore non ha mai in mano piu di un blocco. La prova
        // strutturale e che il blocco e riusato (stesso array) e che il risultato e corretto.
        val total = 40L * 1024 * 1024
        val chunk = ByteArray(8 * 1024)
        val md = MessageDigest.getInstance("SHA-256")
        fun fill(i: Long) { for (k in chunk.indices) chunk[k] = ((i + k) % 251).toByte() }
        var i = 0L
        while (i < total) { fill(i); md.update(chunk); i += chunk.size }
        val expected = md.digest().joinToString("") { "%02x".format(it) }

        val root = newDir()
        val c = DiskAssetCache(root, total * 2) { clock++ }
        val w = c.openWrite("big.bin", total, expected)
        i = 0
        while (i < total) {
            fill(i)
            assertEquals(AssetWriteResult.Ok, w.write(chunk))
            i += chunk.size
        }
        assertEquals(AssetPutResult.Stored, w.commit())
        assertEquals(total, c.info("big.bin")!!.size)
        assertEquals(expected, c.info("big.bin")!!.sha256)
        assertEquals(total, File(root, "data/big.bin").length())
        assertTrue(c.verify("big.bin"))
    }
}
