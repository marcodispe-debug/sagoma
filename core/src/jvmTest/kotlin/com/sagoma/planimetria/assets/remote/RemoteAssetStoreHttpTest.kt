package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.AssetPutResult
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `RemoteAssetStore` con `HttpBlobFetcher` contro un server locale: nessuna rete reale. */
class RemoteAssetStoreHttpTest {
    private val server = FakeBlobServer()
    private val dirs = mutableListOf<File>()
    private val stores = mutableListOf<RemoteAssetStore>()

    @AfterTest
    fun cleanup() {
        stores.forEach { it.close() }
        server.close()
        dirs.forEach { it.deleteRecursively() }
    }

    private fun root(): File = kotlin.io.path.createTempDirectory("sagoma-remote-").toFile().also { dirs += it }
    private fun disk(root: File = root(), max: Long = 1L shl 30) = DiskAssetCache(root, max)
    private fun bytes(n: Int, seed: Int = 0) = ByteArray(n) { (it * 31 + seed).toByte() }
    private val fast = FetchPolicy(maxConcurrentDownloads = 4, maxAttempts = 3, baseBackoffMillis = 1, maxBackoffMillis = 5)

    private fun store(
        cache: com.sagoma.planimetria.assets.AssetCache,
        resolver: BlobResolver,
        policy: FetchPolicy = fast,
        caps: Set<String> = emptySet(),
        readTimeout: Int = 5_000,
        bufferSize: Int = 64 * 1024,
    ) = RemoteAssetStore(resolver, cache, HttpBlobFetcher(server.baseUrl, readTimeoutMillis = readTimeout, bufferSize = bufferSize), policy, caps).also { stores += it }

    private fun blob(path: String = "furniture/a.glb", n: Int = 50_000, seed: Int = 1) = TestBlob(path, BytesContent(bytes(n, seed)))
    private fun serve(b: TestBlob) = server.content(b.key, b.content)

    // ---- successo ----

    @Test
    fun `download 200 verificato in cache e leggibile`() = runBlocking<Unit> {
        val b = blob(); serve(b)
        val root = root(); val cache = disk(root)
        val s = store(cache, resolverOf(b))
        assertEquals(AssetAvailability.Remote, s.availability(b.path))
        assertNull(s.peek(b.path))
        assertEquals(AssetState.Remote, s.state(b.path))
        assertEquals(AssetAvailability.Available, s.ensure(b.path))
        assertEquals(1, server.hits(b.key))
        assertEquals(AssetAvailability.Available, s.availability(b.path))
        assertEquals(AssetState.Available, s.state(b.path))
        assertContentEquals((b.content as BytesContent).bytes, s.peek(b.path))
        assertContentEquals((b.content as BytesContent).bytes, s.read(b.path))
        val info = cache.info(b.path)!!
        assertEquals(b.sha, info.sha256)
        assertEquals(b.size, info.size)
        assertEquals(0, tmpCount(root))
    }

    @Test
    fun `read dopo download passa da ensure e legge dalla cache verificata`() = runBlocking<Unit> {
        val b = blob(); serve(b)
        val s = store(disk(), resolverOf(b))
        assertContentEquals((b.content as BytesContent).bytes, s.read(b.path))
        assertEquals(1, server.hits(b.key))
        s.read(b.path)
        assertEquals(1, server.hits(b.key)) // la seconda lettura non fa rete
    }

    // ---- cache ----

    @Test
    fun `cache con l'impronta giusta nessuna richiesta HTTP`() = runBlocking<Unit> {
        val b = blob(); serve(b)
        val cache = disk()
        assertEquals(AssetPutResult.Stored, cache.put(b.path, (b.content as BytesContent).bytes))
        val s = store(cache, resolverOf(b))
        assertEquals(AssetAvailability.Available, s.availability(b.path))
        assertEquals(AssetAvailability.Available, s.ensure(b.path))
        assertNotNull(s.peek(b.path))
        assertEquals(0, server.totalHits())
    }

    @Test
    fun `cache con file di impronta sbagliata e un miss e si scarica quello giusto`() = runBlocking<Unit> {
        val b = blob(); serve(b)
        val cache = disk()
        val wrong = bytes(50_000, 99) // stessa dimensione, contenuto diverso
        cache.put(b.path, wrong)
        val s = store(cache, resolverOf(b))
        assertEquals(AssetAvailability.Remote, s.availability(b.path))
        assertNull(s.peek(b.path)) // non si serve contenuto con l'impronta sbagliata
        assertEquals(AssetAvailability.Available, s.ensure(b.path))
        assertEquals(1, server.hits(b.key))
        assertEquals(b.sha, cache.info(b.path)!!.sha256)
        assertContentEquals((b.content as BytesContent).bytes, s.peek(b.path))
    }

    @Test
    fun `cache con dimensione diversa e un miss`() = runBlocking<Unit> {
        val b = blob(); serve(b)
        val cache = disk()
        cache.put(b.path, bytes(10))
        val s = store(cache, resolverOf(b))
        assertEquals(AssetAvailability.Remote, s.availability(b.path))
        assertEquals(AssetAvailability.Available, s.ensure(b.path))
    }

    // ---- errori HTTP ----

    private fun failedWith(status: Int, expected: RemoteError, hits: Int, retryAfter: String? = null) = runBlocking<Unit> {
        val b = blob(); server.route(b.key, Behavior.Status(status, retryAfter))
        val root = root(); val cache = disk(root)
        val s = store(cache, resolverOf(b))
        val r = s.ensureDetailed(b.path)
        assertEquals(RemoteEnsureResult.Failed(expected), r, "status $status")
        assertEquals(hits, server.hits(b.key), "tentativi per $status")
        assertEquals(AssetState.Failed(expected), s.state(b.path))
        assertEquals(AssetAvailability.Remote, s.ensure(b.path)) // dopo un errore ensure dà Remote: si può riprovare
        assertNull(cache.info(b.path))
        assertNull(s.peek(b.path))
        assertEquals(0, tmpCount(root))
        assertFalse("://" in expected.describe() || "127.0.0.1" in expected.describe())
    }

    @Test
    fun `403 accesso negato senza ritentare`() = failedWith(403, RemoteError.AccessDenied, hits = 1)

    @Test
    fun `404 non trovato senza ritentare`() = failedWith(404, RemoteError.NotFound, hits = 1)

    @Test
    fun `429 troppe richieste dopo tutti i tentativi`() = failedWith(429, RemoteError.TooManyRequests(0), hits = 3, retryAfter = "0")

    @Test
    fun `500 errore del server dopo tutti i tentativi`() = failedWith(500, RemoteError.ServerError(500), hits = 3)

    @Test
    fun `stato HTTP senza voce propria non si ritenta`() = failedWith(418, RemoteError.HttpStatus(418), hits = 1)

    @Test
    fun `ensure dopo un errore restituisce Remote e poi riesce quando il server torna`() = runBlocking<Unit> {
        val b = blob()
        server.route(b.key, Behavior.Status(404))
        val s = store(disk(), resolverOf(b), policy = FetchPolicy(maxAttempts = 3, baseBackoffMillis = 1, maxBackoffMillis = 5, failureCooldownMillis = 0))
        assertEquals(AssetAvailability.Remote, s.ensure(b.path))
        assertIs<AssetState.Failed>(s.state(b.path))
        serve(b)
        assertEquals(AssetAvailability.Available, s.ensure(b.path))
        assertEquals(AssetState.Available, s.state(b.path))
    }

    // ---- ritentativi ----

    @Test
    fun `timeout poi successo`() = runBlocking<Unit> {
        val b = blob()
        server.sequence(b.key, Behavior.Hang(2_000), Behavior.Ok(b.content))
        val s = store(disk(), resolverOf(b), readTimeout = 300)
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
        assertEquals(2, server.hits(b.key))
    }

    @Test
    fun `500 poi successo`() = runBlocking<Unit> {
        val b = blob()
        server.sequence(b.key, Behavior.Status(500), Behavior.Ok(b.content))
        val s = store(disk(), resolverOf(b))
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
        assertEquals(2, server.hits(b.key))
    }

    @Test
    fun `429 poi successo`() = runBlocking<Unit> {
        val b = blob()
        server.sequence(b.key, Behavior.Status(429, "0"), Behavior.Status(429), Behavior.Ok(b.content))
        val s = store(disk(), resolverOf(b))
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
        assertEquals(3, server.hits(b.key))
    }

    @Test
    fun `esaurimento dei tentativi del timeout`() = runBlocking<Unit> {
        val b = blob()
        server.route(b.key, Behavior.Hang(2_000))
        val s = store(disk(), resolverOf(b), policy = FetchPolicy(maxAttempts = 2, baseBackoffMillis = 1, maxBackoffMillis = 2), readTimeout = 200)
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Timeout), s.ensureDetailed(b.path))
        assertEquals(2, server.hits(b.key))
    }

    @Test
    fun `un solo tentativo se maxAttempts e 1`() = runBlocking<Unit> {
        val b = blob()
        server.route(b.key, Behavior.Status(500))
        val s = store(disk(), resolverOf(b), policy = FetchPolicy(maxAttempts = 1))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.ServerError(500)), s.ensureDetailed(b.path))
        assertEquals(1, server.hits(b.key))
    }

    @Test
    fun `connessione rifiutata e Offline e si ritenta`() = runBlocking<Unit> {
        val b = blob()
        val dead = FakeBlobServer(); val url = dead.baseUrl; dead.close()
        val cache = disk()
        val s = RemoteAssetStore(resolverOf(b), cache, HttpBlobFetcher(url), fast).also { stores += it }
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Offline), s.ensureDetailed(b.path))
        assertNull(cache.info(b.path))
    }

    // ---- integrita ----

    @Test
    fun `SHA diverso a parita di dimensione non entra in cache e non si ritenta`() = runBlocking<Unit> {
        val b = blob()
        server.content(b.key, BytesContent(bytes(50_000, 77))) // altro contenuto, stessa dimensione
        val root = root(); val cache = disk(root)
        val s = store(cache, resolverOf(b))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.HashMismatch(b.sha)), s.ensureDetailed(b.path))
        assertEquals(1, server.hits(b.key))
        assertNull(cache.info(b.path))
        assertNull(s.peek(b.path))
        assertEquals(0, tmpCount(root))
    }

    @Test
    fun `Content-Length diverso da quello atteso non si ritenta`() = runBlocking<Unit> {
        val b = blob()
        server.content(b.key, BytesContent(bytes(40_000)))
        val root = root(); val cache = disk(root)
        val s = store(cache, resolverOf(b))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.ContentLengthMismatch(50_000, 40_000)), s.ensureDetailed(b.path))
        assertEquals(1, server.hits(b.key))
        assertNull(cache.info(b.path))
        assertEquals(0, tmpCount(root))
    }

    @Test
    fun `risposta troncata si ritenta e poi riesce`() = runBlocking<Unit> {
        val b = blob()
        server.sequence(b.key, Behavior.Cut(b.content, 20_000), Behavior.Ok(b.content))
        val s = store(disk(), resolverOf(b))
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
        assertEquals(2, server.hits(b.key))
    }

    @Test
    fun `risposta troncata sempre fallisce con SizeMismatch e niente in cache`() = runBlocking<Unit> {
        val b = blob()
        server.route(b.key, Behavior.Cut(b.content, 20_000))
        val root = root(); val cache = disk(root)
        val s = store(cache, resolverOf(b))
        val r = s.ensureDetailed(b.path)
        assertIs<RemoteEnsureResult.Failed>(r)
        val e = assertIs<RemoteError.SizeMismatch>(r.error)
        assertEquals(50_000L, e.expected)
        assertTrue(e.actual in 1..20_000, e.describe())
        assertEquals(3, server.hits(b.key))
        assertNull(cache.info(b.path))
        assertEquals(0, tmpCount(root))
    }

    @Test
    fun `risposta troncata con retryTruncated falso non si ritenta`() = runBlocking<Unit> {
        val b = blob()
        server.route(b.key, Behavior.Cut(b.content, 20_000))
        val s = store(disk(), resolverOf(b), policy = FetchPolicy(maxAttempts = 3, retryTruncated = false, baseBackoffMillis = 1))
        assertIs<RemoteEnsureResult.Failed>(s.ensureDetailed(b.path))
        assertEquals(1, server.hits(b.key))
    }

    @Test
    fun `il file temporaneo non e esposto durante il download`() = runBlocking<Unit> {
        val b = TestBlob("furniture/slow.glb", BytesContent(bytes(200_000, 3)))
        server.route(b.key, Behavior.Ok(b.content, chunkSize = 8_192, delayPerChunkMs = 30))
        val root = root(); val cache = disk(root)
        val s = store(cache, resolverOf(b), bufferSize = 8_192)
        val job = async(Dispatchers.Default) { s.ensure(b.path) }
        var seen = false
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !job.isCompleted) {
            val st = s.state(b.path)
            if (st is AssetState.Downloading && st.progress > 0f) {
                seen = true
                assertNull(s.peek(b.path))
                assertNull(cache.info(b.path))
                assertNull(cache.peek(b.path))
                assertEquals(AssetAvailability.Remote, s.availability(b.path))
                assertTrue(cache.list().isEmpty())
                assertEquals(1, tmpCount(root))
                break
            }
            delay(5)
        }
        assertTrue(seen, "il download non e stato osservato in corso")
        assertEquals(AssetAvailability.Available, job.await())
        assertEquals(0, tmpCount(root))
        assertNotNull(s.peek(b.path))
    }

    // ---- concorrenza ----

    @Test
    fun `tre ensure contemporanei dello stesso blob fanno una sola richiesta`() = runBlocking<Unit> {
        val b = blob()
        server.route(b.key, Behavior.Ok(b.content, startDelayMs = 300))
        val s = store(disk(), resolverOf(b))
        val results = (1..3).map { async(Dispatchers.Default) { s.ensureDetailed(b.path) } }.awaitAll()
        assertEquals(List(3) { RemoteEnsureResult.Available }, results)
        assertEquals(1, server.hits(b.key))
    }

    @Test
    fun `molti ensure contemporanei con download lento e fallimento condividono lo stesso esito`() = runBlocking<Unit> {
        val b = blob()
        server.route(b.key, Behavior.Status(404))
        val s = store(disk(), resolverOf(b))
        val results = (1..5).map { async(Dispatchers.Default) { s.ensureDetailed(b.path) } }.awaitAll()
        assertTrue(results.all { it == RemoteEnsureResult.Failed(RemoteError.NotFound) })
        assertTrue(server.hits(b.key) in 1..5)
    }

    @Test
    fun `piu blob diversi contemporanei`() = runBlocking<Unit> {
        val blobs = (1..4).map { TestBlob("furniture/f$it.glb", BytesContent(bytes(30_000, it))) }
        blobs.forEach { server.route(it.key, Behavior.Ok(it.content, startDelayMs = 400)) }
        val s = store(disk(), resolverOf(*blobs.toTypedArray()), policy = FetchPolicy(maxConcurrentDownloads = 4, baseBackoffMillis = 1))
        val r = blobs.map { b -> async(Dispatchers.Default) { s.ensure(b.path) } }.awaitAll()
        assertEquals(List(4) { AssetAvailability.Available }, r)
        assertTrue(server.maxInFlight.get() >= 3, "in parallelo: ${server.maxInFlight.get()}")
        blobs.forEach { assertEquals(1, server.hits(it.key)); assertEquals(it.sha, s.snapshot.entryForPath(it.path)!!.sha256) }
    }

    @Test
    fun `il limite di download contemporanei e rispettato`() = runBlocking<Unit> {
        val blobs = (1..6).map { TestBlob("furniture/f$it.glb", BytesContent(bytes(20_000, it))) }
        blobs.forEach { server.route(it.key, Behavior.Ok(it.content, startDelayMs = 200)) }
        val s = store(disk(), resolverOf(*blobs.toTypedArray()), policy = FetchPolicy(maxConcurrentDownloads = 2, baseBackoffMillis = 1))
        blobs.map { b -> async(Dispatchers.Default) { s.ensure(b.path) } }.awaitAll()
        assertEquals(2, server.maxInFlight.get())
    }

    @Test
    fun `un solo download alla volta con limite 1`() = runBlocking<Unit> {
        val blobs = (1..3).map { TestBlob("furniture/f$it.glb", BytesContent(bytes(20_000, it))) }
        blobs.forEach { server.route(it.key, Behavior.Ok(it.content, startDelayMs = 100)) }
        val s = store(disk(), resolverOf(*blobs.toTypedArray()), policy = FetchPolicy(maxConcurrentDownloads = 1))
        assertEquals(List(3) { AssetAvailability.Available }, blobs.map { b -> async(Dispatchers.Default) { s.ensure(b.path) } }.awaitAll())
        assertEquals(1, server.maxInFlight.get())
    }

    @Test
    fun `l'errore di un blob non blocca gli altri`() = runBlocking<Unit> {
        val ok = TestBlob("furniture/ok.glb", BytesContent(bytes(30_000, 1)))
        val bad = TestBlob("furniture/bad.glb", BytesContent(bytes(30_000, 2)))
        val other = TestBlob("furniture/other.glb", BytesContent(bytes(30_000, 3)))
        server.content(ok.key, ok.content); server.content(other.key, other.content)
        server.route(bad.key, Behavior.Status(500))
        val cache = disk()
        val s = store(cache, resolverOf(ok, bad, other))
        val r = listOf(ok, bad, other).map { b -> async(Dispatchers.Default) { s.ensureDetailed(b.path) } }.awaitAll()
        assertEquals(RemoteEnsureResult.Available, r[0])
        assertEquals(RemoteEnsureResult.Failed(RemoteError.ServerError(500)), r[1])
        assertEquals(RemoteEnsureResult.Available, r[2])
        assertNotNull(cache.info(ok.path)); assertNull(cache.info(bad.path)); assertNotNull(cache.info(other.path))
    }

    // ---- manifest ----

    @Test
    fun `peek e availability non fanno mai rete`() = runBlocking<Unit> {
        val b = blob(); serve(b)
        val s = store(disk(), resolverOf(b))
        repeat(5) { s.peek(b.path); s.availability(b.path); s.state(b.path) }
        assertEquals(0, server.totalHits())
    }

    @Test
    fun `blob non presente nel manifest`() = runBlocking<Unit> {
        val b = blob(); serve(b)
        val s = store(disk(), resolverOf(b))
        assertEquals(AssetAvailability.Unavailable, s.availability("furniture/nope.glb"))
        assertEquals(AssetAvailability.Unavailable, s.ensure("furniture/nope.glb"))
        assertEquals(RemoteEnsureResult.NotInCatalog, s.ensureDetailed("furniture/nope.glb"))
        assertNull(s.peek("furniture/nope.glb"))
        assertNull(s.read("furniture/nope.glb"))
        assertNull(s.state("furniture/nope.glb"))
        assertEquals(0, server.totalHits())
    }

    @Test
    fun `asset incompatibile non si scarica`() = runBlocking<Unit> {
        val b = TestBlob("furniture/ktx.glb", BytesContent(bytes(10_000)), requires = listOf("ktx2"))
        serve(b)
        val r = resolverOf(b)
        val s = store(disk(), r)
        assertEquals(AssetAvailability.Incompatible, s.availability(b.path))
        assertEquals(AssetAvailability.Incompatible, s.ensure(b.path))
        assertEquals(AssetState.Incompatible, s.state(b.path))
        assertNull(s.read(b.path))
        assertEquals(0, server.totalHits())
        // con la capacita richiesta e un normale download
        val s2 = store(disk(), r, caps = setOf("ktx2", "altro"))
        assertEquals(AssetAvailability.Remote, s2.availability(b.path))
        assertEquals(AssetAvailability.Available, s2.ensure(b.path))
    }

    // ---- streaming ----

    @Test
    fun `il blob remoto arriva a blocchi dallo scrittore e mai con put`() = runBlocking<Unit> {
        val b = TestBlob("furniture/chunky.glb", SyntheticContent(1_000_000, 5))
        serve(b)
        val spy = SpyCache(disk())
        val s = store(spy, resolverOf(b), bufferSize = 4_096)
        assertEquals(AssetAvailability.Available, s.ensure(b.path))
        assertEquals(0, spy.puts.get(), "put(ByteArray) usato per il blob remoto")
        assertEquals(1, spy.opened.get())
        assertTrue(spy.writeCalls.get() >= 1_000_000 / 4_096, "blocchi: ${spy.writeCalls.get()}")
        assertTrue(spy.maxChunk.get() <= 4_096)
        assertEquals(b.sha, spy.info(b.path)!!.sha256)
    }

    @Test
    fun `file grande sintetico in streaming`() = runBlocking<Unit> {
        val b = TestBlob("env/huge.ibl", SyntheticContent(256L * 1024 * 1024, 9))
        serve(b)
        val spy = SpyCache(disk(max = 400L * 1024 * 1024))
        // 256 MB: oltre il limite predefinito (64 MB) dei blob remoti, quindi lo si alza apposta per provare lo streaming
        val s = store(spy, resolverOf(b), policy = FetchPolicy(baseBackoffMillis = 1, maxBackoffMillis = 5, maxBlobBytes = 512L * 1024 * 1024))
        assertEquals(AssetAvailability.Available, s.ensure(b.path))
        assertEquals(0, spy.puts.get())
        assertTrue(spy.writeCalls.get() > 1_000)
        assertTrue(spy.maxChunk.get() <= 64 * 1024)
        assertEquals(b.sha, spy.info(b.path)!!.sha256)
        assertEquals(b.size, spy.info(b.path)!!.size)
    }

    @Test
    fun `se la cache non puo accogliere il file non si scarica nemmeno`() = runBlocking<Unit> {
        val b = blob(n = 10_000); serve(b)
        val s = store(disk(max = 5_000), resolverOf(b))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.TooLarge(10_000, 5_000)), s.ensureDetailed(b.path))
        assertEquals(0, server.totalHits())
    }
}
