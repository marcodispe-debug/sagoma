package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.AssetPutResult
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.CompositeAssetStore
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Limite di dimensione dei blob remoti: solo per il percorso remoto, prima di qualunque lavoro. */
class RemoteAssetStoreLimitTest {
    private val server = FakeBlobServer()
    private val dirs = mutableListOf<File>()
    private val stores = mutableListOf<RemoteAssetStore>()

    @AfterTest
    fun cleanup() {
        stores.forEach { it.close() }
        server.close()
        dirs.forEach { it.deleteRecursively() }
    }

    private fun root(): File = kotlin.io.path.createTempDirectory("sagoma-limit-").toFile().also { dirs += it }
    private fun policy(limit: Long) = FetchPolicy(baseBackoffMillis = 0, maxBackoffMillis = 0, maxBlobBytes = limit)
    private fun store(r: BlobResolver, c: com.sagoma.planimetria.assets.AssetCache, p: FetchPolicy) =
        RemoteAssetStore(r, c, HttpBlobFetcher(server.baseUrl), p).also { stores += it }

    @Test
    fun `un blob oltre il limite non apre connessioni ne scrittori ne cache`() = runBlocking<Unit> {
        val big = smallBlob("furniture/big.glb", 1, size = 1_001)
        server.content(big.key, big.content) // c'e, ma non deve mai essere richiesto
        val root = root(); val spy = SpyCache(DiskAssetCache(root, 1L shl 20))
        val s = store(resolverOf(big), spy, policy(1_000))

        val expected = RemoteError.TooLarge(1_001, 1_000)
        assertEquals(RemoteEnsureResult.Failed(expected), s.ensureDetailed(big.path))
        assertEquals(AssetAvailability.Remote, s.ensure(big.path))
        assertEquals(AssetState.Failed(expected), s.state(big.path))
        assertEquals(AssetAvailability.Remote, s.availability(big.path)) // e nel manifest ma non e mai Available
        assertNull(s.peek(big.path))
        assertNull(s.read(big.path))

        assertEquals(0, server.totalHits(), "zero richieste HTTP")
        assertEquals(0, spy.opened.get(), "nessuno scrittore")
        assertEquals(0, spy.puts.get())
        assertEquals(0, spy.disk.list().size)
        assertEquals(0, tmpCount(root))
        assertEquals("too large: 1001 bytes, limit 1000", expected.describe())
    }

    @Test
    fun `un blob grande esattamente quanto il limite si scarica, uno di un byte in piu no`() = runBlocking<Unit> {
        val ok = smallBlob("furniture/ok.glb", 1, size = 1_000)
        val ko = smallBlob("furniture/ko.glb", 2, size = 1_001)
        server.content(ok.key, ok.content); server.content(ko.key, ko.content)
        val s = store(resolverOf(ok, ko), DiskAssetCache(root(), 1L shl 20), policy(1_000))
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(ok.path))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.TooLarge(1_001, 1_000)), s.ensureDetailed(ko.path))
        assertEquals(1, server.totalHits())
    }

    @Test
    fun `il limite predefinito e 64 MB e un manifest che dichiara di piu non scarica niente`() = runBlocking<Unit> {
        assertEquals(64L * 1024 * 1024, FetchPolicy().maxBlobBytes)
        val huge = TestBlob("env/huge.ibl", BytesContent(ByteArray(10)))
        val r = BlobResolver(resolverOf(huge).manifest.let { m ->
            m.copy(assets = listOf(m.assets[0].copy(files = listOf(m.assets[0].files[0].copy(size = 64L * 1024 * 1024 + 1)))))
        })
        val s = store(r, DiskAssetCache(root(), 1L shl 20), FetchPolicy())
        assertEquals(RemoteEnsureResult.Failed(RemoteError.TooLarge(64L * 1024 * 1024 + 1, 64L * 1024 * 1024)), s.ensureDetailed("env/huge.ibl"))
        assertEquals(0, server.totalHits())
    }

    @Test
    fun `un blob oltre il limite gia in cache non si serve e non risulta disponibile`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1, size = 2_000)
        val cache = DiskAssetCache(root(), 1L shl 20)
        assertEquals(AssetPutResult.Stored, cache.put(b.path, b.bytes()))
        // con un limite alto e normale
        val normal = store(resolverOf(b), cache, policy(4_000))
        assertEquals(AssetAvailability.Available, normal.availability(b.path))
        assertContentEquals(b.bytes(), normal.peek(b.path))
        // con un limite abbassato dopo: lo stesso file non si serve (un ByteArray cosi grande non si vuole)
        val strict = store(resolverOf(b), cache, policy(1_000))
        assertEquals(AssetAvailability.Remote, strict.availability(b.path))
        assertNull(strict.peek(b.path))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.TooLarge(2_000, 1_000)), strict.ensureDetailed(b.path))
        assertEquals(AssetState.Failed(RemoteError.TooLarge(2_000, 1_000)), strict.state(b.path))
        assertEquals(0, server.totalHits())
    }

    @Test
    fun `il limite non tocca gli asset locali presenti nel composito ne la cache`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1, size = 2_000)
        val local = object : AssetStore {
            override fun availability(path: String) = if (path == b.path) AssetAvailability.Available else AssetAvailability.Unavailable
            override fun peek(path: String): ByteArray? = if (path == b.path) b.bytes() else null
        }
        val remote = store(resolverOf(b), DiskAssetCache(root(), 1L shl 20), policy(1_000)) // il remoto lo rifiuta
        val composite = CompositeAssetStore(remote, local)
        assertEquals(AssetAvailability.Available, composite.availability(b.path))
        assertEquals(AssetAvailability.Available, composite.ensure(b.path))
        assertContentEquals(b.bytes(), composite.peek(b.path)) // il remoto da' null, il locale risponde
        assertContentEquals(b.bytes(), composite.read(b.path))
        // e il limite non e della cache: una cache accetta file piu grandi con put e con openWrite
        val plain = DiskAssetCache(root(), 1L shl 24)
        assertEquals(AssetPutResult.Stored, plain.put("x.bin", ByteArray(200_000)))
        assertEquals(0, server.totalHits())
    }

    @Test
    fun `parametri del limite`() {
        assertFailsWith<IllegalArgumentException> { FetchPolicy(maxBlobBytes = 0) }
        assertFailsWith<IllegalArgumentException> { FetchPolicy(maxBlobBytes = -5) }
        assertEquals(7, FetchPolicy(maxConcurrentDownloads = 7).maxConcurrentDownloads)
        val s = RemoteAssetStore(BlobResolver.EMPTY, DiskAssetCache(root(), 1L shl 20), CountingFetcher(), FetchPolicy(maxConcurrentDownloads = 2))
        assertEquals(2, s.maxConcurrentDownloads)
        s.close()
    }
}
