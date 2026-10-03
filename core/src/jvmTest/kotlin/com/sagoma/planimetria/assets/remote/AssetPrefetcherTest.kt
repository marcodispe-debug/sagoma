package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetPrefetcherTest {
    private val dirs = mutableListOf<File>()
    private val closeables = mutableListOf<() -> Unit>()

    @AfterTest
    fun cleanup() {
        closeables.forEach { it() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun cache(root: File = kotlin.io.path.createTempDirectory("sagoma-pf-").toFile().also { dirs += it }) = DiskAssetCache(root, 1L shl 26)
    private val fastPolicy = FetchPolicy(baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = 0)

    private class Rig(val store: RemoteAssetStore, val pf: AssetPrefetcher, val fetcher: GateFetcher, val cache: DiskAssetCache)

    private fun rig(
        vararg blobs: TestBlob,
        parallel: Int = 1,
        storeParallel: Int = 4,
        ttl: Long = 1_000_000,
        lowCap: Int = 200,
        clock: () -> Long = run { val t = AtomicLong(); { t.incrementAndGet() } },
        resolver: BlobResolver = resolverOf(*blobs),
        policy: FetchPolicy = FetchPolicy(maxConcurrentDownloads = storeParallel, baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = 0),
    ): Rig {
        val fetcher = GateFetcher()
        blobs.forEach { fetcher.add(it) }
        val cache = cache()
        val store = RemoteAssetStore(resolver, cache, fetcher, policy)
        val pf = AssetPrefetcher(store, maxParallel = parallel, visibleTtlMillis = ttl, maxQueuedLowPriority = lowCap, clock = clock)
        closeables += { pf.close(); store.close() }
        return Rig(store, pf, fetcher, cache)
    }

    /** Lascia partire, in ordine, i download: ogni volta aspetta che ne parta uno, ne controlla la chiave e lo libera. */
    private suspend fun Rig.drain(expected: List<TestBlob>, startAt: Int = 0) {
        for ((i, b) in expected.withIndex()) {
            val n = startAt + i
            waitUntil("download ${n + 1}") { fetcher.requests.size > n }
            assertEquals(b.key, fetcher.requests[n], "il download numero ${n + 1} doveva essere ${b.path}")
            fetcher.gate(b.key).complete(Unit)
        }
    }

    // ---- ordine, promozione, deduplicazione ----

    @Test
    fun `l'ordine e Scene Visible Soon Background, tra i Visible il piu recente per primo`() = runBlocking<Unit> {
        val b = (0..7).map { smallBlob("furniture/f$it.glb", it) }
        val r = rig(*b.toTypedArray())
        r.pf.request(b[0].path, AssetPriority.Background) // parte subito e resta fermo
        waitUntil("primo") { r.fetcher.requests.size == 1 }
        r.pf.request(b[1].path, AssetPriority.Background)
        r.pf.request(b[2].path, AssetPriority.Soon)
        r.pf.request(b[3].path, AssetPriority.Visible)
        r.pf.request(b[4].path, AssetPriority.Visible)
        r.pf.request(b[5].path, AssetPriority.Scene)
        r.pf.request(b[6].path, AssetPriority.Scene)
        r.pf.request(b[7].path, AssetPriority.Soon)
        r.fetcher.gate(b[0].key).complete(Unit)
        // Scene (in ordine di arrivo), Visible (l'ultimo richiesto per primo), Soon, Background
        r.drain(listOf(b[5], b[6], b[4], b[3], b[2], b[7], b[1]), startAt = 1)
    }

    @Test
    fun `una richiesta con priorita piu alta promuove quella in coda, una piu bassa no`() = runBlocking<Unit> {
        val b = (0..4).map { smallBlob("furniture/f$it.glb", it) }
        val r = rig(*b.toTypedArray())
        r.pf.request(b[0].path, AssetPriority.Background)
        waitUntil("primo") { r.fetcher.requests.size == 1 }
        r.pf.request(b[1].path, AssetPriority.Soon)
        r.pf.request(b[2].path, AssetPriority.Background)
        r.pf.request(b[3].path, AssetPriority.Visible)
        r.pf.request(b[2].path, AssetPriority.Scene)   // promosso: passa davanti a tutti
        r.pf.request(b[3].path, AssetPriority.Background) // piu bassa: non lo retrocede
        r.fetcher.gate(b[0].key).complete(Unit)
        r.drain(listOf(b[2], b[3], b[1]), startAt = 1)
    }

    @Test
    fun `la stessa richiesta ripetuta fa un solo download e un solo arrivo`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val r = rig(b, parallel = 3)
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { r.pf.arrived.collect { got += it } }
        repeat(10) { r.pf.request(b.path, AssetPriority.Visible) }
        waitUntil("richiesta") { r.fetcher.requests.size == 1 }
        repeat(10) { r.pf.request(b.path, if (it % 2 == 0) AssetPriority.Scene else AssetPriority.Visible) } // mentre e in corso
        r.fetcher.gate(b.key).complete(Unit)
        waitUntil("arrivo") { got.isNotEmpty() }
        delay(150)
        assertEquals(listOf(b.path), got.toList())
        assertEquals(1, r.fetcher.requests.size)
        assertEquals(1, r.pf.startedCount, "il prefetcher non duplica la ensure: il single-flight e del negozio, ma qui non se ne chiede una seconda")
        // gia disponibile: nessun nuovo lavoro, nessun nuovo arrivo
        repeat(3) { r.pf.request(b.path, AssetPriority.Scene) }
        delay(150)
        assertEquals(listOf(b.path), got.toList())
        assertEquals(1, r.fetcher.requests.size)
        collector.cancel()
    }

    @Test
    fun `un lavoro gia in corso non viene interrotto da una richiesta piu importante`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1); val s = smallBlob("furniture/s.glb", 2)
        val r = rig(a, s, parallel = 1)
        r.pf.request(a.path, AssetPriority.Background)
        waitUntil("a partito") { r.fetcher.requests.size == 1 }
        r.pf.request(s.path, AssetPriority.Scene)
        delay(150)
        assertEquals(listOf(a.key), r.fetcher.requests.toList()) // s aspetta, a continua
        assertTrue(r.pf.state(a.path) is AssetState.Downloading)
        r.fetcher.gate(a.key).complete(Unit)
        waitUntil("a finito") { r.pf.state(a.path) == AssetState.Available }
        r.drain(listOf(s), startAt = 1)
    }

    // ---- concorrenza ----

    private class TrackingFetcher(val contents: Map<String, ByteArray>) : BlobFetcher {
        val running = AtomicInteger(); val maxRunning = AtomicInteger()
        override suspend fun fetch(request: BlobRequest, sink: BlobSink): FetchResult {
            val n = running.incrementAndGet()
            maxRunning.accumulateAndGet(n) { a, b -> maxOf(a, b) }
            try {
                delay(60)
                val b = contents[request.blobKey] ?: return FetchResult.Http(404)
                sink.accept(b, 0, b.size)
                return FetchResult.Success(b.size.toLong(), b.size.toLong())
            } finally {
                running.decrementAndGet()
            }
        }
    }

    @Test
    fun `i lavori contemporanei sono al piu maxParallel`() = runBlocking<Unit> {
        val b = (0..9).map { smallBlob("furniture/f$it.glb", it) }
        val f = TrackingFetcher(b.associate { it.key to it.bytes() })
        val store = RemoteAssetStore(resolverOf(*b.toTypedArray()), cache(), f, FetchPolicy(maxConcurrentDownloads = 8, baseBackoffMillis = 0))
        val pf = AssetPrefetcher(store, maxParallel = 2)
        closeables += { pf.close(); store.close() }
        b.forEach { pf.request(it.path, AssetPriority.Visible) }
        waitUntil("tutti") { b.all { store.state(it.path) == AssetState.Available } }
        assertEquals(2, f.maxRunning.get())
    }

    @Test
    fun `maxParallel non supera i download contemporanei del negozio`() = runBlocking<Unit> {
        val b = (0..2).map { smallBlob("furniture/f$it.glb", it) }
        // il negozio ne ammette 1; il prefetcher ne chiede 5, ma deve comunque servire per priorita
        val r = rig(*b.toTypedArray(), parallel = 5, storeParallel = 1)
        r.pf.request(b[0].path, AssetPriority.Background)
        waitUntil("primo") { r.fetcher.requests.size == 1 }
        r.pf.request(b[1].path, AssetPriority.Background)
        r.pf.request(b[2].path, AssetPriority.Scene)
        delay(150)
        assertEquals(1, r.fetcher.requests.size) // b[1] non e stato avviato in anticipo dietro al semaforo del negozio
        // ... e infatti per il negozio non ha nemmeno un volo in corso: resta in coda nel prefetcher
        assertEquals(AssetState.Remote, r.store.state(b[1].path))
        assertEquals(AssetState.Remote, r.store.state(b[2].path))
        assertTrue(r.store.state(b[0].path) is AssetState.Downloading)
        r.fetcher.gate(b[0].key).complete(Unit)
        r.drain(listOf(b[2], b[1]), startAt = 1)
    }

    // ---- scadenza e limiti della coda ----

    @Test
    fun `un Visible non piu richiesto decade, uno richiesto di nuovo no`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1); val old = smallBlob("furniture/old.glb", 2)
        val renewed = smallBlob("furniture/renewed.glb", 3); val fresh = smallBlob("furniture/fresh.glb", 4)
        val now = AtomicLong(0)
        val r = rig(a, old, renewed, fresh, ttl = 3_000, clock = { now.get() })
        r.pf.request(a.path, AssetPriority.Scene)
        waitUntil("a partito") { r.fetcher.requests.size == 1 }
        r.pf.request(old.path, AssetPriority.Visible)       // t = 0
        r.pf.request(renewed.path, AssetPriority.Visible)   // t = 0
        now.set(2_500)
        r.pf.request(renewed.path, AssetPriority.Visible)   // rinnovato a t = 2500
        now.set(4_000)
        r.pf.request(fresh.path, AssetPriority.Visible)     // t = 4000
        r.fetcher.gate(a.key).complete(Unit)
        // old: eta 4000 > 3000 -> scartato; renewed: eta 1500 -> vivo; fresh il piu recente va per primo
        r.drain(listOf(fresh, renewed), startAt = 1)
        delay(200)
        assertFalse(old.key in r.fetcher.requests, "old doveva decadere")
        assertEquals(3, r.fetcher.requests.size)
    }

    @Test
    fun `soon e background in coda sono limitati, si butta il piu vecchio e prima i background`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 0)
        val soon = (1..2).map { smallBlob("furniture/s$it.glb", 10 + it) }
        val bg = (1..4).map { smallBlob("furniture/b$it.glb", 20 + it) }
        val r = rig(a, *soon.toTypedArray(), *bg.toTypedArray(), lowCap = 3)
        r.pf.request(a.path, AssetPriority.Scene)
        waitUntil("a partito") { r.fetcher.requests.size == 1 }
        bg.forEach { r.pf.request(it.path, AssetPriority.Background) } // b1..b4
        soon.forEach { r.pf.request(it.path, AssetPriority.Soon) }       // s1, s2
        r.fetcher.gate(a.key).complete(Unit)
        // in coda al massimo 3: i Soon restano, dei Background resta solo il piu recente (b4)
        r.drain(listOf(soon[0], soon[1], bg[3]), startAt = 1)
        delay(200)
        assertEquals(4, r.fetcher.requests.size)
    }

    // ---- esiti, stato ----

    @Test
    fun `file non nel manifest, incompatibile o gia disponibile non fanno lavoro ne arrivi`() = runBlocking<Unit> {
        val have = smallBlob("furniture/have.glb", 1)
        val ktx = TestBlob("furniture/ktx.glb", BytesContent(ByteArray(100) { it.toByte() }), requires = listOf("ktx2"))
        val r = rig(have, ktx)
        r.cache.put(have.path, have.bytes())
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { r.pf.arrived.collect { got += it } }
        r.pf.request(have.path, AssetPriority.Scene)
        r.pf.request(ktx.path, AssetPriority.Scene)
        r.pf.request("furniture/none.glb", AssetPriority.Scene)
        delay(200)
        assertEquals(0, r.fetcher.requests.size)
        assertTrue(got.isEmpty())
        assertEquals(AssetState.Available, r.pf.state(have.path))
        assertEquals(AssetState.Incompatible, r.pf.state(ktx.path))
        assertEquals(null, r.pf.state("furniture/none.glb"))
        collector.cancel()
    }

    @Test
    fun `un errore resta quello del negozio - nessun arrivo e stato Failed`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val missing = rig(b) // il fetcher non ha il contenuto: 404
        missing.fetcher.contents.clear()
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { missing.pf.arrived.collect { got += it } }
        missing.pf.request(b.path, AssetPriority.Visible)
        waitUntil("richiesta") { missing.fetcher.requests.size == 1 }
        missing.fetcher.gate(b.key).complete(Unit)
        waitUntil("stato") { missing.pf.state(b.path) == AssetState.Failed(RemoteError.NotFound) }
        delay(100)
        assertTrue(got.isEmpty())
        collector.cancel()
    }

    @Test
    fun `un blob oltre il limite remoto non fa lavoro e ha stato Failed TooLarge`() = runBlocking<Unit> {
        val big = smallBlob("furniture/big.glb", 1, size = 5_000)
        val r = rig(big, policy = FetchPolicy(maxBlobBytes = 1_000, baseBackoffMillis = 0))
        r.pf.request(big.path, AssetPriority.Scene)
        waitUntil("stato") { r.pf.state(big.path) == AssetState.Failed(RemoteError.TooLarge(5_000, 1_000)) }
        assertEquals(0, r.fetcher.requests.size)
    }

    // ---- cambio di manifest, close, thread ----

    @Test
    fun `le richieste in coda che il nuovo manifest non prevede piu si scartano`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1); val gone = smallBlob("furniture/gone.glb", 2); val kept = smallBlob("furniture/kept.glb", 3)
        val r = rig(a, gone, kept)
        r.pf.request(a.path, AssetPriority.Scene)
        waitUntil("a partito") { r.fetcher.requests.size == 1 }
        r.pf.request(gone.path, AssetPriority.Visible)
        r.pf.request(kept.path, AssetPriority.Soon)
        r.store.replaceSnapshot(resolverOf(a, kept, catalogVersion = 2)) // gone sparisce dal manifest
        r.pf.onManifestChanged()
        r.fetcher.gate(a.key).complete(Unit)
        r.drain(listOf(kept), startAt = 1)
        delay(200)
        assertFalse(gone.key in r.fetcher.requests)
    }

    @Test
    fun `un download annullato dal cambio di manifest si riaccoda e riparte con il manifest nuovo`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = smallBlob(path, 1); val new = smallBlob(path, 2)
        val r = rig(old, new, resolver = resolverOf(old))
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { r.pf.arrived.collect { got += it } }
        r.pf.request(path, AssetPriority.Scene)
        waitUntil("V1 partito") { r.fetcher.requests.size == 1 }
        r.store.replaceSnapshot(resolverOf(new, catalogVersion = 2))
        r.pf.onManifestChanged()
        r.fetcher.gate(old.key).complete(Unit) // il vecchio finisce: scartato dal negozio come Cancelled
        r.drain(listOf(new), startAt = 1)      // ... e la richiesta riparte con il blob nuovo
        waitUntil("arrivo") { got.isNotEmpty() }
        delay(150)
        assertEquals(listOf(path), got.toList(), "un solo arrivo, senza esiti intermedi")
        assertEquals(listOf(old.key, new.key), r.fetcher.requests.toList(), "nessun terzo download")
        assertEquals(new.sha, r.cache.info(path)!!.sha256)
        collector.cancel()
    }

    @Test
    fun `un Cancelled dovuto alla chiusura del negozio non si riaccoda`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val r = rig(b)
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { r.pf.arrived.collect { got += it } }
        r.pf.request(b.path, AssetPriority.Scene)
        waitUntil("partito") { r.fetcher.requests.size == 1 }
        r.store.close() // il manifest non e cambiato
        delay(300)
        assertEquals(1, r.fetcher.requests.size)
        assertTrue(got.isEmpty())
        assertEquals(1, r.pf.startedCount, "nessuna ripartenza")
        assertEquals(1, r.pf.doneCount, "un solo esito: nessun ciclo di riaccodamento")
        collector.cancel()
    }

    @Test
    fun `close ferma l'attore, ignora le richieste dopo e si puo ripetere`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1); val q = smallBlob("furniture/q.glb", 2)
        val r = rig(a, q)
        r.pf.request(a.path, AssetPriority.Scene)
        waitUntil("partito") { r.fetcher.requests.size == 1 }
        r.pf.request(q.path, AssetPriority.Visible) // in coda
        assertTrue(r.pf.isActive)
        r.pf.close(); r.pf.close()
        waitUntil("attore fermo") { !r.pf.isActive }
        r.pf.request("furniture/x.glb", AssetPriority.Scene) // non fa niente e non lancia
        r.pf.onManifestChanged()
        r.fetcher.gate(a.key).complete(Unit)
        delay(200)
        assertEquals(1, r.fetcher.requests.size, "nessun lavoro nuovo dopo close")
        assertFalse(r.pf.isActive)
    }

    @Test
    fun `richieste da molti thread - ogni percorso scaricato una volta sola e nessuna eccezione`() = runBlocking<Unit> {
        val b = (0 until 40).map { smallBlob("furniture/f$it.glb", it, size = 500) }
        val fetcher = CountingFetcher()
        b.forEach { fetcher.add(it) }
        val store = RemoteAssetStore(resolverOf(*b.toTypedArray()), cache(), fetcher, FetchPolicy(maxConcurrentDownloads = 4, baseBackoffMillis = 0))
        val pf = AssetPrefetcher(store, maxParallel = 4)
        closeables += { pf.close(); store.close() }
        val errors = CopyOnWriteArrayList<Throwable>()
        val ts = (1..8).map { t ->
            thread {
                try {
                    repeat(200) { i -> pf.request(b[(i * 7 + t) % b.size].path, AssetPriority.entries[(i + t) % 4]) }
                } catch (e: Throwable) { errors += e }
            }
        }
        ts.forEach { it.join() }
        waitUntil("tutti disponibili") { b.all { store.state(it.path) == AssetState.Available } }
        delay(100)
        assertTrue(errors.isEmpty())
        assertEquals(b.size, fetcher.calls.get(), "un download per percorso")
    }

    @Test
    fun `parametri non validi`() {
        val store = RemoteAssetStore(BlobResolver.EMPTY, cache(), CountingFetcher())
        closeables += { store.close() }
        kotlin.test.assertFailsWith<IllegalArgumentException> { AssetPrefetcher(store, maxParallel = 0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { AssetPrefetcher(store, visibleTtlMillis = -1) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { AssetPrefetcher(store, maxQueuedLowPriority = -1) }
    }
}
