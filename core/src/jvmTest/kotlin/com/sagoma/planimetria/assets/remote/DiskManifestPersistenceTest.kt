package com.sagoma.planimetria.assets.remote

import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskManifestPersistenceTest {
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() = dirs.forEach { it.deleteRecursively() }

    private fun dir(): File = kotlin.io.path.createTempDirectory("sagoma-mdisk-").toFile().also { dirs += it }

    @Test
    fun `salva e rilegge, e senza file non c'e niente`() {
        val d = dir()
        val p = DiskManifestPersistence(File(d, "sub/dir")) // la cartella si crea
        assertNull(p.load())
        assertTrue(p.save("uno"))
        assertEquals("uno", p.load())
        assertTrue(p.save("due è €"))
        assertEquals("due è €", p.load())
        assertFalse(File(d, "sub/dir/manifest.json.tmp").exists())
    }

    @Test
    fun persistenzaAtomicaUnErroreAMetaLasciaIlPrecedenteIntero() {
        val d = dir()
        val p = DiskManifestPersistence(d)
        assertTrue(p.save("""{"versione":1}"""))
        p.beforeReplace = { throw IOException("arresto a meta (simulato)") } // il .tmp e scritto, lo spostamento non avviene
        assertFalse(p.save("""{"versione":2}"""))
        assertEquals("""{"versione":1}""", p.load())
        assertFalse(File(d, "manifest.json.tmp").exists(), "il temporaneo si ripulisce")
        p.beforeReplace = {}
        assertTrue(p.save("""{"versione":3}"""))
        assertEquals("""{"versione":3}""", p.load())
    }

    @Test
    fun `un arresto brusco durante la scrittura lascia un tmp che non si legge mai`() {
        val d = dir()
        val p = DiskManifestPersistence(d)
        p.save("""{"versione":1}""")
        File(d, "manifest.json.tmp").writeText("""{"versione":2,"asse""") // il processo muore a meta del .tmp
        assertEquals("""{"versione":1}""", DiskManifestPersistence(d).load())
        assertTrue(DiskManifestPersistence(d).save("""{"versione":4}""")) // il prossimo salvataggio lo riscrive
        assertEquals("""{"versione":4}""", DiskManifestPersistence(d).load())
        assertFalse(File(d, "manifest.json.tmp").exists())
    }

    @Test
    fun `salvare dove non si puo scrivere da false senza eccezioni`() {
        val d = dir()
        File(d, "occupato").writeText("sono un file")
        val p = DiskManifestPersistence(File(d, "occupato")) // la "cartella" e un file
        assertFalse(p.save("x"))
        assertNull(p.load())
    }

    @Test
    fun `nome del file personalizzato`() {
        val d = dir()
        DiskManifestPersistence(d, "catalogo.json").save("x")
        assertTrue(File(d, "catalogo.json").isFile)
        assertFalse(File(d, "manifest.json").exists())
    }

    @Test
    fun `scritture concorrenti non lasciano un file misto`() {
        val d = dir()
        val p = DiskManifestPersistence(d)
        val a = "A".repeat(200_000); val b = "B".repeat(200_000)
        val ts = listOf(a, b).map { text -> kotlin.concurrent.thread { repeat(30) { p.save(text) } } }
        ts.forEach { it.join() }
        val got = p.load()!!
        assertTrue(got == a || got == b)
    }
}
