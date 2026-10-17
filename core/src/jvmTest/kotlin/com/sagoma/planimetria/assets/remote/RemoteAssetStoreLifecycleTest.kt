package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `close()` coerente: mai un `ensure` sospeso, mai uno stato `Downloading` per un download che non esiste più. */
class RemoteAssetStoreLifecycleTest {
    private val dirs = mutableListOf<File>()
    private val stores = mutableListOf<RemoteAssetStore>()

    @AfterTest
    fun cleanup() {
        stores.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun root(): File = kotlin.io.path.createTempDirectory("sagoma-life-").toFile().also { dirs += it }
    private fun bytes(n: Int, seed: Int) = ByteArray(n) { (it * 31 + seed).toByte() }
    private fun blob(seed: Int = 1) = TestBlob("furniture/x$seed.glb", BytesContent(bytes(4_000, seed)))
    private fun store(r: BlobResolver, c: DiskAssetCache, f: BlobFetcher) = RemoteAssetStore(r, c, f, FetchPolicy(baseBackoffMillis = 1)).also { stores += it }
    private val cancelled = RemoteEnsureResult.Failed(RemoteError.Cancelled)

    private suspend fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timeout in attesa di: $what" }
            delay(5)
        }
    }

    @Test
    fun ensureAfterCloseDoesNotHang() = runBlocking<Unit> {
        val b = blob()
        val fetcher = CountingFetcher().add(b)
        val s = store(resolverOf(b), DiskAssetCache(root(), 1L shl 26), fetcher)
        s.close()
        repeat(3) {
            assertEquals(cancelled, withTimeout(2_000) { s.ensureDetailed(b.path) })
            assertEquals(AssetAvailability.Remote, withTimeout(2_000) { s.ensure(b.path) })
        }
        assertEquals(AssetState.Failed(RemoteError.Cancelled), s.state(b.path)) // mai Downloading
        assertNull(s.peek(b.path))
        assertNull(withTimeout(2_000) { s.read(b.path) })
        assertEquals(0, fetcher.calls.get(), "dopo close nessuna richiesta")
    }

    @Test
    fun `dopo close cio che era gia in cache resta disponibile e il resto del manifest e coerente`() = runBlocking<Unit> {
        val have = blob(1); val miss = blob(2)
        val cache = DiskAssetCache(root(), 1L shl 26)
        cache.put(have.path, have.bytes())
        val s = store(resolverOf(have, miss), cache, CountingFetcher().add(miss))
        s.close()
        assertEquals(RemoteEnsureResult.Available, withTimeout(2_000) { s.ensureDetailed(have.path) })
        assertEquals(AssetState.Available, s.state(have.path))
        assertNotNull(s.peek(have.path))
        assertEquals(RemoteEnsureResult.NotInCatalog, s.ensureDetailed("furniture/none.glb"))
        assertEquals(cancelled, s.ensureDetailed(miss.path))
    }

    @Test
    fun `doppio close e close senza download`() = runBlocking<Unit> {
        val b = blob()
        val s = store(resolverOf(b), DiskAssetCache(root(), 1L shl 26), CountingFetcher().add(b))
        assertEquals(AssetState.Remote, s.state(b.path))
        s.close()
        s.close()
        s.close()
        assertEquals(cancelled, withTimeout(2_000) { s.ensureDetailed(b.path) })
    }

    @Test
    fun `prima di close ensure funziona normalmente`() = runBlocking<Unit> {
        val b = blob()
        val s = store(resolverOf(b), DiskAssetCache(root(), 1L shl 26), CountingFetcher().add(b))
        assertEquals(RemoteEnsureResult.Available, withTimeout(5_000) { s.ensureDetailed(b.path) })
        s.close()
    }

    @Test
    fun `close durante un download lo fa terminare come Cancelled per tutti quelli che aspettano`() = runBlocking<Unit> {
        val b = blob()
        val fetcher = GateFetcher().add(b)
        val root = root(); val cache = DiskAssetCache(root, 1L shl 26)
        val s = store(resolverOf(b), cache, fetcher)
        val waiters = (1..3).map { async(Dispatchers.Default) { s.ensureDetailed(b.path) } }
        waitFor("download partito") { fetcher.requests.size == 1 }
        assertTrue(s.state(b.path) is AssetState.Downloading)
        s.close()
        assertEquals(List(3) { cancelled }, withTimeout(3_000) { waiters.awaitAll() })
        assertFalse(s.state(b.path) is AssetState.Downloading)
        assertEquals(AssetState.Failed(RemoteError.Cancelled), s.state(b.path))
        waitFor("temporaneo ripulito") { tmpCount(root) == 0 }
        assertNull(cache.info(b.path))
        assertNull(s.peek(b.path))
    }

    @Test
    fun `close con un fetcher che ignora la cancellazione libera chi aspetta e non installa niente`() = runBlocking<Unit> {
        val b = blob()
        val fetcher = GateFetcher(cancellable = false).add(b)
        val root = root(); val cache = DiskAssetCache(root, 1L shl 26)
        val s = store(resolverOf(b), cache, fetcher)
        val waiter = async(Dispatchers.Default) { s.ensureDetailed(b.path) }
        waitFor("download partito") { fetcher.requests.size == 1 }
        s.close()
        assertEquals(cancelled, withTimeout(3_000) { waiter.await() }) // subito, anche se la rete e ancora bloccata
        fetcher.gate(b.key).complete(Unit) // la lettura "finisce" dopo la chiusura
        waitFor("temporaneo ripulito") { tmpCount(root) == 0 }
        delay(100)
        assertNull(cache.info(b.path), "dopo close non si installa niente")
    }

    @Test
    fun `ensure e close concorrenti non restano mai sospesi`() = runBlocking<Unit> {
        val base = root()
        repeat(150) { i ->
            val b = blob(i + 1)
            val fetcher = CountingFetcher().add(b)
            val s = store(resolverOf(b), DiskAssetCache(File(base, "c$i"), 1L shl 26), fetcher)
            val e = (1..3).map { async(Dispatchers.Default) { withTimeout(10_000) { s.ensureDetailed(b.path) } } }
            val c = async(Dispatchers.Default) { s.close() }
            c.await()
            for (r in e.awaitAll()) assertTrue(r == RemoteEnsureResult.Available || r == cancelled, "iterazione $i: $r")
            // dopo la chiusura lo stato non e mai bloccato in Downloading
            val st = s.state(b.path)
            assertFalse(st is AssetState.Downloading, "iterazione $i: $st")
            assertTrue(withTimeout(5_000) { s.ensureDetailed(b.path) }.let { it == RemoteEnsureResult.Available || it == cancelled })
        }
    }
}
