package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Archivio dei progetti su file (telefono e computer): elenco, copie, eliminazione, file .sagoma. */
class FileProjectRepositoryTest {
    private val root: File = Files.createTempDirectory("sagoma-progetti").toFile()

    @AfterTest
    fun pulisci() {
        root.deleteRecursively()
    }

    private fun house(name: String, width: Double = 400.0) =
        Building.single(FloorPlan(listOf(Room(1, name, RoomType.Soggiorno, RoomFactory.rectangle(width, 300.0)))))

    @Test
    fun `al primo avvio c'e un progetto, e la casa della versione vecchia diventa il primo progetto`() {
        val empty = FileProjectRepository(root)
        assertEquals(listOf("La mia casa"), empty.list().map { it.name })
        assertEquals(1L, empty.lastOpened)
        assertNull(empty.planStore(1).load())

        val other = Files.createTempDirectory("sagoma-vecchia").toFile()
        try {
            FilePlanStore(other).save(house("Cucina"))
            val repo = FileProjectRepository(other)
            assertEquals(house("Cucina"), repo.planStore(1).load())
            // Riaprendo non si rifà: l'elenco c'è già.
            repo.create("Seconda")
            assertEquals(2, FileProjectRepository(other).list().size)
        } finally {
            other.deleteRecursively()
        }
    }

    @Test
    fun `ogni progetto ha la sua casa e il salvataggio lo porta in cima all'elenco`() {
        val repo = FileProjectRepository(root)
        val p = repo.create("Villa", client = "Rossi", address = "Via Roma 1")
        assertEquals(2L, p.id)
        assertEquals(p, repo.info(2))
        repo.planStore(1).save(house("Soggiorno"))
        repo.planStore(2).save(house("Camera", 500.0))
        assertEquals(house("Soggiorno"), repo.planStore(1).load())
        assertEquals(house("Camera", 500.0), FileProjectRepository(root).planStore(2).load())
        // Quello salvato per ultimo è il primo dell'elenco.
        Thread.sleep(5)
        repo.planStore(1).save(house("Soggiorno", 450.0))
        assertEquals(listOf(1L, 2L), repo.list().map { it.id })
        assertTrue(repo.info(1)!!.modified >= repo.info(2)!!.modified)
        // Nome vuoto: «Progetto N».
        assertEquals("Progetto 3", repo.create("  ").name)
    }

    @Test
    fun `l'ultimo progetto aperto resta tra un avvio e l'altro, ma non se e stato eliminato`() {
        val repo = FileProjectRepository(root)
        val p = repo.create("Villa")
        repo.markOpened(p.id)
        assertEquals(p.id, FileProjectRepository(root).lastOpened)
        repo.delete(p.id)
        assertNull(repo.lastOpened)
        assertFalse(repo.folder(p.id).exists())
    }

    @Test
    fun `l'ultimo progetto rimasto non si elimina`() {
        val repo = FileProjectRepository(root)
        repo.planStore(1).save(house("Soggiorno"))
        repo.delete(1)
        assertEquals(listOf(1L), repo.list().map { it.id })
        assertEquals(house("Soggiorno"), repo.planStore(1).load())
    }

    @Test
    fun `la copia di un progetto e indipendente dall'originale, con i suoi file`() {
        val repo = FileProjectRepository(root)
        repo.planStore(1).save(house("Soggiorno"))
        repo.writeFile(1, "sfondo.png", byteArrayOf(1, 2, 3))
        val copy = assertNotNull(repo.duplicate(1, "Proposta B"))
        assertEquals("Proposta B", copy.name)
        assertNotEquals(1L, copy.id)
        assertEquals(house("Soggiorno"), repo.planStore(copy.id).load())
        assertContentEquals(byteArrayOf(1, 2, 3), repo.readFile(copy.id, "sfondo.png"))
        // Cambiare la copia non tocca l'originale.
        repo.planStore(copy.id).save(house("Soggiorno", 600.0))
        repo.writeFile(copy.id, "sfondo.png", byteArrayOf(9))
        assertEquals(house("Soggiorno"), repo.planStore(1).load())
        assertContentEquals(byteArrayOf(1, 2, 3), repo.readFile(1, "sfondo.png"))
        assertNull(repo.duplicate(99, "Niente"))
    }

    @Test
    fun `i file del progetto accettano solo nomi semplici`() {
        val repo = FileProjectRepository(root)
        File(root, "segreto.txt").writeText("non toccare")
        for (bad in listOf("../segreto.txt", "..", "a/b.png", "a\\b.png", "", " ")) {
            assertNull(repo.readFile(1, bad), bad)
            assertFailsWith<IllegalArgumentException>(bad) { repo.writeFile(1, bad, byteArrayOf(0)) }
            repo.deleteFile(1, bad)
        }
        assertEquals("non toccare", File(root, "segreto.txt").readText())
        // L'elenco dei file non mostra la casa né i salvataggi a metà.
        repo.planStore(1).save(house("Soggiorno"))
        repo.writeFile(1, "foto-1.jpg", byteArrayOf(1))
        File(repo.folder(1), "planimetria.json.tmp").writeText("{")
        assertEquals(listOf("foto-1.jpg"), repo.files(1))
        repo.deleteFile(1, "foto-1.jpg")
        assertEquals(emptyList(), repo.files(1))
    }

    @Test
    fun `un progetto esportato in sagoma si reimporta uguale, come progetto nuovo`() {
        val repo = FileProjectRepository(root)
        val p = repo.create("Villa", client = "Rossi", address = "Via Roma 1")
        repo.update(p.copy(author = "Arch. Bianchi"))
        repo.planStore(p.id).save(house("Camera"))
        repo.writeFile(p.id, "sfondo.png", byteArrayOf(4, 5, 6))
        val bytes = assertNotNull(repo.export(p.id))

        val other = Files.createTempDirectory("sagoma-altro-pc").toFile()
        try {
            val dest = FileProjectRepository(other)
            val imported = assertNotNull(dest.import(bytes))
            assertEquals(2L, imported.id, "prende un numero libero di questo archivio")
            assertEquals(listOf("Villa", "Rossi", "Via Roma 1", "Arch. Bianchi"), listOf(imported.name, imported.client, imported.address, imported.author))
            assertEquals(house("Camera"), dest.planStore(imported.id).load())
            assertContentEquals(byteArrayOf(4, 5, 6), dest.readFile(imported.id, "sfondo.png"))
            // Reimportato nello stesso archivio: una copia accanto all'originale.
            val again = assertNotNull(repo.import(bytes))
            assertEquals(3, repo.list().size)
            assertEquals(house("Camera"), repo.planStore(again.id).load())
        } finally {
            other.deleteRecursively()
        }
        assertNull(repo.export(99))
    }

    @Test
    fun `si importa anche un semplice planimetria json`() {
        val repo = FileProjectRepository(root)
        val p = assertNotNull(repo.import(PlanJson.encode(house("Bagno")).toByteArray()))
        assertEquals("Progetto importato", p.name)
        assertEquals(house("Bagno"), repo.planStore(p.id).load())
    }

    @Test
    fun `un file che non e un progetto non lascia niente`() {
        val repo = FileProjectRepository(root)
        val before = root.walkTopDown().map { it.relativeTo(root).path }.toSet()
        assertNull(repo.import("ciao".toByteArray()))
        assertNull(repo.import(zip("progetto.json" to """{"id":5,"name":"Senza casa"}""".toByteArray())))
        assertNull(repo.import(zip("file/planimetria.json" to "{rotto".toByteArray())))
        assertEquals(listOf(1L), repo.list().map { it.id })
        assertEquals(before, root.walkTopDown().map { it.relativeTo(root).path }.toSet())
    }

    @Test
    fun `un file sagoma con nomi furbi non scrive fuori dalla cartella del progetto`() {
        val repo = FileProjectRepository(root)
        val p = assertNotNull(
            repo.import(
                zip(
                    "file/planimetria.json" to PlanJson.encode(house("Camera")).toByteArray(),
                    "file/../../fuori.txt" to "x".toByteArray(),
                    "file/../index.json" to "{}".toByteArray(),
                    "../fuori2.txt" to "x".toByteArray(),
                ),
            ),
        )
        assertEquals(house("Camera"), repo.planStore(p.id).load())
        assertFalse(File(root, "fuori.txt").exists())
        assertFalse(File(root, "fuori2.txt").exists())
        assertFalse(File(root.parentFile, "fuori.txt").exists())
        assertEquals(2, repo.list().size, "l'elenco dei progetti non è stato sovrascritto")
    }

    @Test
    fun `un elenco dei progetti rovinato non fa sparire i progetti`() {
        val repo = FileProjectRepository(root)
        val p = repo.create("Villa")
        repo.planStore(1).save(house("Soggiorno"))
        repo.planStore(p.id).save(house("Camera"))
        File(root, "projects/index.json").writeText("{\"projects\":[{\"id\":1,") // scrittura interrotta, disco pieno, sincronizzazione…
        val reopened = FileProjectRepository(root)
        assertEquals(listOf(1L, 2L), reopened.list().map { it.id }.sorted())
        assertEquals(house("Camera"), reopened.planStore(2).load())
        // Un progetto nuovo non prende il posto di quelli che c'erano; l'elenco rovinato resta da parte.
        assertEquals(3L, reopened.create("Nuovo").id)
        assertEquals(3, FileProjectRepository(root).list().size)
        assertTrue(File(root, "projects").list()!!.any { it.startsWith("index.corrupt-") })
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
