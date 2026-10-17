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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pausa dopo un fallimento, con un orologio finto: nessuna attesa reale. */
class RemoteAssetStoreCooldownTest {
    private val dirs = mutableListOf<File>()
    private val stores = mutableListOf<RemoteAssetStore>()
    private var now = 0L

    @AfterTest
    fun cleanup() {
        stores.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun cache() = DiskAssetCache(kotlin.io.path.createTempDirectory("sagoma-cool-").toFile().also { dirs += it }, 1L shl 26)
    private fun blob(seed: Int = 1) = TestBlob("furniture/x$seed.glb", BytesContent(ByteArray(3_000) { (it * 31 + seed).toByte() }))
    private fun policy(cooldown: Long = 10_000, attempts: Int = 3) =
        FetchPolicy(maxAttempts = attempts, baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = cooldown)

    private fun store(r: BlobResolver, c: DiskAssetCache, f: BlobFetcher, p: FetchPolicy) =
        RemoteAssetStore(r, c, f, p, clock = { now }).also { stores += it }

    @Test
    fun failedDownloadCooldown() = runBlocking<Unit> {
        val b = blob()
        val fetcher = CountingFetcher().add(b).also { it.status = 500 }
        val s = store(resolverOf(b), cache(), fetcher, policy())
        val failed = RemoteEnsureResult.Failed(RemoteError.ServerError(500))

        assertEquals(failed, s.ensureDetailed(b.path))
        assertEquals(3, fetcher.calls.get()) // la prima raffica: tutti i tentativi della policy
        repeat(5) { assertEquals(failed, s.ensureDetailed(b.path)) } // 5 ensure di fila: nessuna nuova richiesta
        assertEquals(3, fetcher.calls.get())
        assertEquals(AssetAvailability.Remote, s.ensure(b.path))
        assertEquals(AssetState.Failed(RemoteError.ServerError(500)), s.state(b.path))

        now = 9_999 // ancora in pausa
        assertEquals(failed, s.ensureDetailed(b.path))
        assertEquals(3, fetcher.calls.get())

        now = 10_000 // scaduta: una nuova richiesta riprova (e solo quella)
        assertEquals(failed, s.ensureDetailed(b.path))
        assertEquals(6, fetcher.calls.get())
        assertEquals(failed, s.ensureDetailed(b.path)) // di nuovo in pausa, dal nuovo fallimento
        assertEquals(6, fetcher.calls.get())

        // Il server torna: dopo la scadenza si riesce e la pausa sparisce
        fetcher.status = null
        now = 20_000
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
        assertEquals(7, fetcher.calls.get())
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path)) // Available non e mai in pausa
        assertEquals(7, fetcher.calls.get())
    }

    @Test
    fun `un 404 non crea pausa e due ensure di fila fanno due richieste`() = runBlocking<Unit> {
        val b = blob()
        val fetcher = CountingFetcher() // nessun contenuto: 404
        val s = store(resolverOf(b), cache(), fetcher, policy(cooldown = 10_000))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.NotFound), s.ensureDetailed(b.path))
        assertEquals(1, fetcher.calls.get()) // 404: un solo tentativo interno, nessun ritentativo automatico
        assertEquals(RemoteEnsureResult.Failed(RemoteError.NotFound), s.ensureDetailed(b.path)) // nessuna attesa del cooldown
        assertEquals(2, fetcher.calls.get())
        assertEquals(AssetState.Failed(RemoteError.NotFound), s.state(b.path)) // l'ultimo errore resta visibile
        now = 1_000_000 // il tempo che passa non fa partire niente da solo
        assertEquals(2, fetcher.calls.get())
        fetcher.add(b) // ora il file c'e: lo trova il prossimo ensure esplicito
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
        assertEquals(3, fetcher.calls.get())
        assertEquals(AssetState.Available, s.state(b.path))
    }

    private class ScriptFetcher(val data: ByteArray, val f: (BlobSink, ByteArray) -> FetchResult) : BlobFetcher {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        override suspend fun fetch(request: BlobRequest, sink: BlobSink): FetchResult { calls.incrementAndGet(); return f(sink, data) }
    }

    /** Un errore, due ensure consecutivi senza che il tempo passi: quante richieste in totale. */
    private fun requestsForTwoEnsures(fx: (BlobSink, ByteArray) -> FetchResult, expected: RemoteError): Int = runBlocking {
        val b = blob()
        val bytes = b.bytes()
        val sf = ScriptFetcher(bytes, fx)
        val s = RemoteAssetStore(resolverOf(b), cache(), sf, policy(), clock = { now }).also { stores += it }
        assertEquals(RemoteEnsureResult.Failed(expected), s.ensureDetailed(b.path))
        val first = sf.calls.get()
        assertEquals(RemoteEnsureResult.Failed(expected), s.ensureDetailed(b.path))
        assertEquals(AssetState.Failed(expected), s.state(b.path)) // l'errore resta sempre in state()
        sf.calls.get() - first // richieste del secondo ensure
    }

    @Test
    fun `solo gli errori transienti entrano in pausa`() {
        // Transienti: il secondo ensure non fa richieste
        assertEquals(0, requestsForTwoEnsures({ _, _ -> FetchResult.Http(500) }, RemoteError.ServerError(500)))
        assertEquals(0, requestsForTwoEnsures({ _, _ -> FetchResult.Http(503) }, RemoteError.ServerError(503)))
        assertEquals(0, requestsForTwoEnsures({ _, _ -> FetchResult.Http(429, 1) }, RemoteError.TooManyRequests(1)))
        assertEquals(0, requestsForTwoEnsures({ _, _ -> FetchResult.Timeout }, RemoteError.Timeout))
        assertEquals(0, requestsForTwoEnsures({ _, _ -> FetchResult.ConnectionFailed("X") }, RemoteError.Offline))
        // Non transienti: il secondo ensure rifa la verifica (una richiesta: nessun ritentativo interno)
        assertEquals(1, requestsForTwoEnsures({ _, _ -> FetchResult.Http(404) }, RemoteError.NotFound))
        assertEquals(1, requestsForTwoEnsures({ _, _ -> FetchResult.Http(403) }, RemoteError.AccessDenied))
        assertEquals(1, requestsForTwoEnsures({ _, _ -> FetchResult.Http(418) }, RemoteError.HttpStatus(418)))
        assertEquals(1, requestsForTwoEnsures({ _, _ -> FetchResult.ContentLengthMismatch(3_000, 2_000) }, RemoteError.ContentLengthMismatch(3_000, 2_000)))
        val wrong = ByteArray(3_000) { (it + 5).toByte() } // stessa dimensione, altro contenuto
        val b = blob()
        assertEquals(1, requestsForTwoEnsures({ sink, _ -> sink.accept(wrong, 0, wrong.size); FetchResult.Success(3_000, 3_000) }, RemoteError.HashMismatch(b.sha)))
        assertEquals(1, requestsForTwoEnsures({ sink, d -> sink.accept(d, 0, 100); FetchResult.Success(100, 100) }, RemoteError.SizeMismatch(3_000, 100)))
    }

    @Test
    fun `cache non utilizzabile e file troppo grande non creano pausa`() = runBlocking<Unit> {
        val b = blob()
        // TooLarge: la cache e piu piccola del file; ogni ensure riprova ad aprire la scrittura, senza richieste
        val small = SpyCache(DiskAssetCache(kotlin.io.path.createTempDirectory("sagoma-cool-").toFile().also { dirs += it }, 1_000))
        val f1 = CountingFetcher().add(b)
        val spy = RemoteAssetStore(resolverOf(b), small, f1, policy(), clock = { now }).also { stores += it }
        assertEquals(RemoteEnsureResult.Failed(RemoteError.TooLarge(3_000, 1_000)), spy.ensureDetailed(b.path))
        assertEquals(RemoteEnsureResult.Failed(RemoteError.TooLarge(3_000, 1_000)), spy.ensureDetailed(b.path))
        assertEquals(2, small.opened.get())
        assertEquals(0, f1.calls.get())
        assertEquals(AssetState.Failed(RemoteError.TooLarge(3_000, 1_000)), spy.state(b.path))
    }

    @Test
    fun `la pausa e per blob non per tutto il negozio`() = runBlocking<Unit> {
        val bad = blob(1); val good = blob(2)
        val fetcher = CountingFetcher().add(good)
        val s = store(resolverOf(bad, good), cache(), fetcher, policy())
        fetcher.status = 503
        assertTrue(s.ensureDetailed(bad.path) is RemoteEnsureResult.Failed)
        fetcher.status = null // il server funziona, ma `bad` non ha contenuto -> 404 al prossimo tentativo vero
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(good.path)) // un altro blob non e in pausa
        assertEquals(RemoteEnsureResult.Failed(RemoteError.ServerError(503)), s.ensureDetailed(bad.path)) // ancora in pausa: errore noto
    }

    @Test
    fun `replaceSnapshot con un'altra impronta non eredita la pausa`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = TestBlob(path, BytesContent(ByteArray(2_000) { it.toByte() }))
        val new = TestBlob(path, BytesContent(ByteArray(2_000) { (it + 1).toByte() }))
        val fetcher = CountingFetcher().add(new).also { it.status = 500 }
        val s = store(resolverOf(old), cache(), fetcher, policy())
        assertTrue(s.ensureDetailed(path) is RemoteEnsureResult.Failed)
        assertEquals(3, fetcher.calls.get())
        s.replaceSnapshot(resolverOf(new, catalogVersion = 2)) // altro blob: niente pausa
        fetcher.status = null
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(path))
        assertEquals(4, fetcher.calls.get())
    }

    @Test
    fun `replaceSnapshot con lo stesso blob mantiene la pausa`() = runBlocking<Unit> {
        val b = blob()
        val fetcher = CountingFetcher().add(b).also { it.status = 500 }
        val s = store(resolverOf(b, catalogVersion = 1), cache(), fetcher, policy())
        assertTrue(s.ensureDetailed(b.path) is RemoteEnsureResult.Failed)
        s.replaceSnapshot(resolverOf(b, blob(9), catalogVersion = 2))
        assertTrue(s.ensureDetailed(b.path) is RemoteEnsureResult.Failed)
        assertEquals(3, fetcher.calls.get())
    }

    @Test
    fun `con pausa zero ogni ensure riprova subito`() = runBlocking<Unit> {
        val b = blob()
        val fetcher = CountingFetcher().add(b).also { it.status = 500 }
        val s = store(resolverOf(b), cache(), fetcher, policy(cooldown = 0, attempts = 2))
        repeat(3) { s.ensureDetailed(b.path) }
        assertEquals(6, fetcher.calls.get())
        fetcher.status = null
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
    }

    @Test
    fun `un annullamento non mette il blob in pausa`() = runBlocking<Unit> {
        val b = blob()
        val gate = GateFetcher().add(b)
        val s = RemoteAssetStore(resolverOf(b), cache(), gate, policy(), clock = { now }).also { stores += it }
        val job = async(Dispatchers.Default) { s.ensureDetailed(b.path) }
        while (gate.requests.isEmpty()) delay(5)
        s.replaceSnapshot(resolverOf(blob(5), catalogVersion = 2)) // b non e piu atteso -> il download viene scartato
        gate.gate(b.key).complete(Unit)
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Cancelled), job.await())
        s.replaceSnapshot(resolverOf(b, catalogVersion = 3)) // di nuovo atteso: nessuna pausa da Cancelled
        assertNotNull(s.state(b.path))
        assertEquals(AssetState.Remote, s.state(b.path))
    }

    @Test
    fun `parametri della pausa`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { FetchPolicy(failureCooldownMillis = -1) }
        assertEquals(5_000L, FetchPolicy().failureCooldownMillis)
    }
}
