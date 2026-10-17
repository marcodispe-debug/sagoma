package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Istantanea del manifest e single-flight, con un fetcher in memoria che si può fermare e far ripartire. */
class RemoteAssetStoreSnapshotTest {
    private val dirs = mutableListOf<File>()
    private val stores = mutableListOf<RemoteAssetStore>()

    @AfterTest
    fun cleanup() {
        stores.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun root(): File = kotlin.io.path.createTempDirectory("sagoma-snap-").toFile().also { dirs += it }
    private fun cache(root: File = root()): DiskAssetCache = DiskAssetCache(root, 1L shl 26)
    private fun bytes(n: Int, seed: Int) = ByteArray(n) { (it * 31 + seed).toByte() }
    private fun blob(path: String, seed: Int, n: Int = 4_000) = TestBlob(path, BytesContent(bytes(n, seed)))
    private fun store(r: BlobResolver, c: DiskAssetCache, f: BlobFetcher) = RemoteAssetStore(r, c, f, FetchPolicy(baseBackoffMillis = 1)).also { stores += it }

    private suspend fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timeout in attesa di: $what" }
            delay(5)
        }
    }

    @Test
    fun `un download vecchio non puo installare un risultato che il manifest attuale non aspetta`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = blob(path, seed = 1)
        val new = blob(path, seed = 2) // stessa destinazione, contenuto atteso diverso
        val fetcher = GateFetcher().add(old).add(new)
        val root = root(); val cache = cache(root)
        val r1 = resolverOf(old, catalogVersion = 1)
        val r2 = resolverOf(new, catalogVersion = 2)
        val s = store(r1, cache, fetcher)
        assertSame(r1, s.snapshot)

        val first = async(Dispatchers.Default) { s.ensureDetailed(path) }
        waitFor("prima richiesta") { fetcher.requests.size == 1 }
        // Il manifest cambia mentre il download e a meta: le nuove richieste usano il nuovo.
        s.replaceSnapshot(r2)
        assertSame(r2, s.snapshot)
        assertEquals(AssetAvailability.Remote, s.availability(path))
        fetcher.gate(old.key).complete(Unit)
        // Il vecchio download ha verificato i suoi byte con il SUO hash, ma non e piu atteso: non installa niente.
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Cancelled), first.await())
        assertNull(cache.info(path))
        assertEquals(0, tmpCount(root))
        assertEquals(listOf(old.key), fetcher.requests.toList())

        // Il nuovo manifest scarica il nuovo contenuto.
        fetcher.gate(new.key).complete(Unit)
        assertEquals(AssetAvailability.Available, s.ensure(path))
        assertEquals(listOf(old.key, new.key), fetcher.requests.toList())
        assertEquals(new.sha, cache.info(path)!!.sha256)
        assertContentEquals(new.bytes(), s.peek(path))
    }

    @Test
    fun staleDownloadCannotOverwriteCurrentSnapshot() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = blob(path, seed = 1)
        val new = blob(path, seed = 2)
        val fetcher = GateFetcher().add(old).add(new)
        val root = root(); val cache = cache(root)
        val s = store(resolverOf(old), cache, fetcher)

        val a = async(Dispatchers.Default) { s.ensureDetailed(path) } // V1 (H1) lento
        waitFor("download V1") { fetcher.requests.size == 1 }
        s.replaceSnapshot(resolverOf(new, catalogVersion = 2))
        val b = async(Dispatchers.Default) { s.ensureDetailed(path) } // V2 (H2)
        waitFor("download V2") { fetcher.requests.size == 2 }
        fetcher.gate(new.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Available, b.await()) // V2 completa per prima
        fetcher.gate(old.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Cancelled), a.await()) // V1 termina dopo: scartato

        assertEquals(new.sha, cache.info(path)!!.sha256) // la cache resta H2
        assertEquals(AssetAvailability.Available, s.availability(path))
        assertContentEquals(new.bytes(), s.peek(path))
        assertEquals(AssetAvailability.Available, s.ensure(path))
        assertEquals(2, fetcher.requests.size, "nessun terzo download")
        assertEquals(0, tmpCount(root))
    }

    @Test
    fun `un download di un percorso tolto dal manifest non si installa`() = runBlocking<Unit> {
        val a = blob("furniture/a.glb", 1)
        val other = blob("furniture/other.glb", 2)
        val fetcher = GateFetcher().add(a)
        val root = root(); val cache = cache(root)
        val s = store(resolverOf(a), cache, fetcher)
        val job = async(Dispatchers.Default) { s.ensureDetailed(a.path) }
        waitFor("richiesta") { fetcher.requests.size == 1 }
        s.replaceSnapshot(resolverOf(other, catalogVersion = 2)) // a.glb non c'e piu
        fetcher.gate(a.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Cancelled), job.await())
        assertNull(cache.info(a.path))
        assertEquals(0, tmpCount(root))
        assertEquals(AssetAvailability.Unavailable, s.availability(a.path))
    }

    @Test
    fun `un download il cui file non e cambiato nel nuovo manifest si installa normalmente`() = runBlocking<Unit> {
        val a = blob("furniture/a.glb", 1)
        val fetcher = GateFetcher().add(a)
        val cache = cache()
        val s = store(resolverOf(a, catalogVersion = 1), cache, fetcher)
        val job = async(Dispatchers.Default) { s.ensureDetailed(a.path) }
        waitFor("richiesta") { fetcher.requests.size == 1 }
        s.replaceSnapshot(resolverOf(a, blob("furniture/new.glb", 9), catalogVersion = 2)) // a.glb identico
        fetcher.gate(a.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Available, job.await())
        assertEquals(a.sha, cache.info(a.path)!!.sha256)
        assertEquals(1, fetcher.requests.size)
    }

    @Test
    fun `anche con un fetcher che ignora la cancellazione il vecchio download non si installa`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = blob(path, 1); val new = blob(path, 2)
        val fetcher = GateFetcher(cancellable = false).add(old).add(new)
        val cache = cache()
        val s = store(resolverOf(old), cache, fetcher)
        val a = async(Dispatchers.Default) { s.ensureDetailed(path) }
        waitFor("download V1") { fetcher.requests.size == 1 }
        s.replaceSnapshot(resolverOf(new, catalogVersion = 2))
        fetcher.gate(new.key).complete(Unit)
        assertEquals(AssetAvailability.Available, s.ensure(path))
        fetcher.gate(old.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Cancelled), a.await())
        assertEquals(new.sha, cache.info(path)!!.sha256)
    }

    @Test
    fun `la chiave del single-flight comprende l'impronta attesa e non solo il percorso`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = blob(path, seed = 1)
        val new = blob(path, seed = 2)
        val fetcher = GateFetcher().add(old).add(new)
        val s = store(resolverOf(old), cache(), fetcher)

        val a = async(Dispatchers.Default) { s.ensureDetailed(path) }
        waitFor("download vecchio") { fetcher.requests.size == 1 }
        s.replaceSnapshot(resolverOf(new, catalogVersion = 2))
        val b = async(Dispatchers.Default) { s.ensureDetailed(path) }
        // Stesso percorso ma impronta attesa diversa: e un altro download, non si unisce al primo.
        waitFor("download nuovo") { fetcher.requests.size == 2 }
        assertEquals(setOf(old.key, new.key), fetcher.requests.toSet())
        fetcher.gate(old.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Cancelled), a.await()) // il vecchio non e piu atteso
        fetcher.gate(new.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Available, b.await())
    }

    @Test
    fun `richieste uguali durante lo stesso download condividono una sola richiesta e lo stesso esito`() = runBlocking<Unit> {
        val b = blob("furniture/x.glb", 1)
        val fetcher = GateFetcher().add(b)
        val s = store(resolverOf(b), cache(), fetcher)
        val calls = (1..3).map { async(Dispatchers.Default) { s.ensureDetailed(b.path) } }
        waitFor("richiesta") { fetcher.requests.size >= 1 }
        delay(100) // il tempo di far arrivare anche le altre due
        assertEquals(1, fetcher.requests.size)
        assertTrue(s.state(b.path) is AssetState.Downloading)
        fetcher.gate(b.key).complete(Unit)
        calls.forEach { assertEquals(RemoteEnsureResult.Available, it.await()) }
        assertEquals(1, fetcher.requests.size)
    }

    @Test
    fun `l'istantanea resta immutabile e un nuovo manifest e un oggetto separato`() = runBlocking<Unit> {
        val a = blob("furniture/a.glb", 1)
        val b = blob("furniture/b.glb", 2)
        val r1 = resolverOf(a, catalogVersion = 1)
        val r2 = resolverOf(a, b, catalogVersion = 2)
        assertNotSame(r1, r2)
        assertEquals(1, r1.assetCount)
        assertEquals(2, r2.assetCount)
        val s = store(r1, cache(), GateFetcher())
        assertEquals(AssetAvailability.Unavailable, s.availability(b.path))
        s.replaceSnapshot(r2)
        assertEquals(AssetAvailability.Remote, s.availability(b.path))
        assertEquals(1, r1.assetCount) // la prima istantanea non e cambiata
        assertNull(r1.entryForPath(b.path))
    }
}
