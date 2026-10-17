package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ownership per processo: una sola istanza aperta per cartella, finche chi la possiede non la chiude. */
class JvmRemoteAssetsOwnershipTest {
    private val server = FakeBlobServer()
    private val dirs = mutableListOf<File>()
    private val systems = CopyOnWriteArrayList<RemoteAssets>()

    @AfterTest
    fun cleanup() {
        systems.forEach { it.close() }
        server.close()
        dirs.forEach { it.deleteRecursively() }
    }

    private fun dir(): File = kotlin.io.path.createTempDirectory("sagoma-own-").toFile().also { dirs += it }
    private fun create(base: File, protectedDirs: List<File> = emptyList()) =
        createJvmRemoteAssets(base, "${server.baseUrl}/m.json", "${server.baseUrl}/blob", 1L shl 24, protectedDirs = protectedDirs)?.also { systems += it }

    @Test
    fun `una seconda istanza sulla stessa cartella non si crea mentre la prima e aperta, e la prima continua a funzionare`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        server.content("m.json", BytesContent(manifestJson(1, a).encodeToByteArray()))
        server.content("blob/${a.key}", a.content)
        val base = dir()
        val first = assertNotNull(create(base))
        // la "seconda Activity" prova a costruirne un'altra: non ottiene niente e non disturba la prima
        repeat(3) { assertNull(create(base)) }
        assertIs<ManifestRefreshResult.Updated>(first.refreshManifest())
        assertEquals(RemoteEnsureResult.Available, first.store.ensureDetailed(a.path))
        assertContentEquals(a.bytes(), first.store.peek(a.path))
        assertTrue(first.prefetcher.isActive)
    }

    @Test
    fun `dopo la chiusura la cartella si puo occupare di nuovo e i dati scaricati ci sono ancora`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        server.content("m.json", BytesContent(manifestJson(1, a).encodeToByteArray()))
        server.content("blob/${a.key}", a.content)
        val base = dir()
        val first = assertNotNull(create(base))
        first.refreshManifest(); first.store.ensureDetailed(a.path)
        first.close()
        val second = assertNotNull(create(base))
        assertEquals(AssetAvailability.Available, second.store.availability(a.path)) // cache e manifest salvato della prima
        assertNull(create(base)) // e ora e la seconda a possederla
    }

    @Test
    fun `chiudere due volte la prima non libera la cartella occupata dalla seconda`() {
        val base = dir()
        val first = assertNotNull(create(base))
        first.close()
        val second = assertNotNull(create(base))
        first.close(); first.close() // chiusure ripetute della vecchia istanza
        assertNull(create(base), "la seconda possiede ancora la cartella")
        second.close()
        assertNotNull(create(base))
    }

    @Test
    fun `cartelle diverse sono indipendenti e chiuderne una non tocca l'altra`() = runBlocking<Unit> {
        val b1 = dir(); val b2 = dir()
        val one = assertNotNull(create(b1)); val two = assertNotNull(create(b2))
        one.close()
        assertTrue(two.prefetcher.isActive)
        assertNull(create(b2))
        assertNotNull(create(b1))
    }

    @Test
    fun `lo stesso percorso scritto in modo diverso e la stessa cartella`() {
        val base = dir()
        File(base, "sub").mkdirs()
        val first = assertNotNull(create(base))
        assertNull(create(File(base, "sub/..")))
        assertNull(create(File(base.path + File.separator + ".")))
        first.close()
        assertNotNull(create(File(base, "sub/..")))
    }

    @Test
    fun `creazioni concorrenti - ne riesce una sola`() {
        val base = dir()
        val n = 8
        val start = CountDownLatch(1)
        val results = CopyOnWriteArrayList<RemoteAssets?>()
        val ts = (1..n).map { thread { start.await(); results += create(base) } }
        start.countDown()
        ts.forEach { it.join() }
        assertEquals(1, results.count { it != null })
        assertEquals(n - 1, results.count { it == null })
    }

    @Test
    fun `un tentativo che non puo riuscire non occupa la cartella`() {
        val assets = dir()
        assertNull(create(assets, protectedDirs = listOf(assets))) // dentro una cartella protetta
        assertNotNull(create(assets.let { File(it, "altro") })) // un'altra cartella non e toccata
        val base = dir()
        assertNull(create(base, protectedDirs = listOf(base)))
        assertNotNull(create(base)) // il rifiuto di prima non ha lasciato la cartella occupata
    }
}
