package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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
    private class GateFetcher : BlobFetcher {
        val requests = CopyOnWriteArrayList<String>()
        val contents = ConcurrentHashMap<String, ByteArray>()
        val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        fun gate(key: String) = gates.getOrPut(key) { CompletableDeferred() }

        override suspend fun fetch(request: BlobRequest, sink: BlobSink): FetchResult {
            requests += request.blobKey
            gate(request.blobKey).await()
            val b = contents[request.blobKey] ?: return FetchResult.Http(404)
            sink.accept(b, 0, b.size)
            return FetchResult.Success(b.size.toLong(), b.size.toLong())
        }
    }

    private val dirs = mutableListOf<File>()
    private val stores = mutableListOf<RemoteAssetStore>()

    @AfterTest
    fun cleanup() {
        stores.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun cache(): DiskAssetCache = DiskAssetCache(kotlin.io.path.createTempDirectory("sagoma-snap-").toFile().also { dirs += it }, 1L shl 26)
    private fun bytes(n: Int, seed: Int) = ByteArray(n) { (it * 31 + seed).toByte() }
    private fun blob(path: String, seed: Int, n: Int = 4_000) = TestBlob(path, BytesContent(bytes(n, seed)))

    private suspend fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timeout in attesa di: $what" }
            delay(5)
        }
    }

    @Test
    fun `un download iniziato continua con il proprio hash e destinazione anche se il manifest cambia`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = blob(path, seed = 1)
        val new = blob(path, seed = 2) // stessa destinazione, contenuto atteso diverso
        val fetcher = GateFetcher()
        fetcher.contents[old.key] = (old.content as BytesContent).bytes
        fetcher.contents[new.key] = (new.content as BytesContent).bytes
        val cache = cache()
        val r1 = resolverOf(old, catalogVersion = 1)
        val r2 = resolverOf(new, catalogVersion = 2)
        val s = RemoteAssetStore(r1, cache, fetcher, FetchPolicy(baseBackoffMillis = 1)).also { stores += it }
        assertSame(r1, s.snapshot)

        val first = async(Dispatchers.Default) { s.ensureDetailed(path) }
        waitFor("prima richiesta") { fetcher.requests.size == 1 }
        // Il manifest cambia mentre il download e a meta.
        s.replaceSnapshot(r2)
        assertSame(r2, s.snapshot)
        assertEquals(AssetAvailability.Remote, s.availability(path))
        fetcher.gate(old.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Available, first.await())

        // Il vecchio download e stato verificato e salvato con l'impronta del VECCHIO manifest, in quella destinazione.
        assertEquals(listOf(old.key), fetcher.requests.toList())
        assertEquals(old.sha, cache.info(path)!!.sha256)
        // ...ma per il nuovo manifest quel file non vale: e un miss e non si serve.
        assertEquals(AssetAvailability.Remote, s.availability(path))
        assertNull(s.peek(path))

        // Il nuovo manifest scarica il nuovo contenuto, senza nemmeno toccare il vecchio blob.
        fetcher.gate(new.key).complete(Unit)
        assertEquals(AssetAvailability.Available, s.ensure(path))
        assertEquals(listOf(old.key, new.key), fetcher.requests.toList())
        assertEquals(new.sha, cache.info(path)!!.sha256)
        assertContentEquals((new.content as BytesContent).bytes, s.peek(path))
    }

    @Test
    fun `la chiave del single-flight comprende l'impronta attesa e non solo il percorso`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = blob(path, seed = 1)
        val new = blob(path, seed = 2)
        val fetcher = GateFetcher()
        fetcher.contents[old.key] = (old.content as BytesContent).bytes
        fetcher.contents[new.key] = (new.content as BytesContent).bytes
        val s = RemoteAssetStore(resolverOf(old), cache(), fetcher, FetchPolicy(baseBackoffMillis = 1)).also { stores += it }

        val a = async(Dispatchers.Default) { s.ensureDetailed(path) }
        waitFor("download vecchio") { fetcher.requests.size == 1 }
        s.replaceSnapshot(resolverOf(new, catalogVersion = 2))
        val b = async(Dispatchers.Default) { s.ensureDetailed(path) }
        // Stesso percorso ma impronta attesa diversa: e un altro download, non si unisce al primo.
        waitFor("download nuovo") { fetcher.requests.size == 2 }
        assertEquals(setOf(old.key, new.key), fetcher.requests.toSet())
        fetcher.gate(old.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Available, a.await())
        fetcher.gate(new.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Available, b.await())
    }

    @Test
    fun `richieste uguali durante lo stesso download condividono una sola richiesta e lo stesso esito`() = runBlocking<Unit> {
        val b = blob("furniture/x.glb", 1)
        val fetcher = GateFetcher()
        fetcher.contents[b.key] = (b.content as BytesContent).bytes
        val s = RemoteAssetStore(resolverOf(b), cache(), fetcher, FetchPolicy(baseBackoffMillis = 1)).also { stores += it }
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
        val s = RemoteAssetStore(r1, cache(), GateFetcher()).also { stores += it }
        assertEquals(AssetAvailability.Unavailable, s.availability(b.path))
        s.replaceSnapshot(r2)
        assertEquals(AssetAvailability.Remote, s.availability(b.path))
        assertEquals(1, r1.assetCount) // la prima istantanea non e cambiata
        assertNull(r1.entryForPath(b.path))
    }
}
