package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.DiskAssetCache
import com.sagoma.planimetria.assets.NoAssetRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Il ponte tra le richieste dell'interfaccia e il prefetcher: richiesta `Visible`, niente download inutili, arrivo, richieste ripetute. */
class LateBoundAssetRequestsTest {
    private val dirs = mutableListOf<File>()
    private val closeables = mutableListOf<() -> Unit>()

    @AfterTest
    fun cleanup() {
        closeables.forEach { it() }
        dirs.forEach { it.deleteRecursively() }
    }

    private class Rig(val store: RemoteAssetStore, val pf: AssetPrefetcher, val scope: CoroutineScope)

    private fun rig(fetcher: BlobFetcher, resolver: BlobResolver): Rig {
        val dir = kotlin.io.path.createTempDirectory("sagoma-lbr-").toFile().also { dirs += it }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = RemoteAssetStore(resolver, DiskAssetCache(dir, 1L shl 26), fetcher, FetchPolicy(baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = 0))
        val pf = AssetPrefetcher(store, maxParallel = 2)
        closeables += { pf.close(); store.close(); scope.cancel() }
        return Rig(store, pf, scope)
    }

    @Test
    fun `una richiesta Visible arriva al prefetcher, scarica, verifica e mette in cache`() = runBlocking<Unit> {
        val blob = smallBlob("furniture/ob_a.png", 1)
        val fetcher = CountingFetcher().add(blob)
        val r = rig(fetcher, resolverOf(blob))
        val requests = LateBoundAssetRequests().also { it.bind(r.pf, r.scope) }
        assertEquals(AssetAvailability.Remote, r.store.availability(blob.path))
        assertNull(r.store.peek(blob.path))
        val before = requests.version.value

        requests.request(blob.path, AssetPriority.Visible)

        waitUntil("asset in cache") { r.store.peek(blob.path) != null }
        assertContentEquals(blob.bytes(), r.store.peek(blob.path)) // l'impronta combacia: lo store serve solo file verificati
        assertEquals(1, fetcher.calls.get())
        waitUntil("versione cresciuta all'arrivo") { requests.version.value > before }
    }

    @Test
    fun `un asset gia disponibile non viene scaricato di nuovo e non cambia la versione`() = runBlocking<Unit> {
        val blob = smallBlob("furniture/ob_a.png", 2)
        val fetcher = CountingFetcher().add(blob)
        val r = rig(fetcher, resolverOf(blob))
        val requests = LateBoundAssetRequests().also { it.bind(r.pf, r.scope) }
        assertEquals(AssetAvailability.Available, r.store.ensure(blob.path)) // già in cache
        val v = requests.version.value
        val started = r.pf.startedCount

        repeat(5) { requests.request(blob.path, AssetPriority.Visible) }
        delay(200)

        assertEquals(1, fetcher.calls.get())
        assertEquals(started, r.pf.startedCount)
        assertEquals(v, requests.version.value)
    }

    @Test
    fun `richieste ripetute dello stesso file fanno un solo download e un solo arrivo`() = runBlocking<Unit> {
        val blob = smallBlob("furniture/ob_a.png", 3)
        val fetcher = GateFetcher().add(blob)
        val r = rig(fetcher, resolverOf(blob))
        val requests = LateBoundAssetRequests().also { it.bind(r.pf, r.scope) }
        val v0 = requests.version.value

        repeat(10) { requests.request(blob.path, AssetPriority.Visible) }
        waitUntil("download partito") { fetcher.requests.size == 1 }
        repeat(10) { requests.request(blob.path, AssetPriority.Visible) } // anche mentre scarica
        delay(150)
        assertEquals(1, fetcher.requests.size)
        assertEquals(v0, requests.version.value) // niente arrivato finché il download è fermo

        fetcher.gate(blob.key).complete(Unit)
        waitUntil("arrivato") { r.store.peek(blob.path) != null }
        waitUntil("versione") { requests.version.value == v0 + 1 }
        delay(150)
        assertEquals(1, fetcher.requests.size)
        assertEquals(v0 + 1, requests.version.value) // un solo arrivo
    }

    @Test
    fun `prima del collegamento le richieste non fanno niente e non si accodano, al collegamento la versione cresce`() = runBlocking<Unit> {
        val blob = smallBlob("furniture/ob_a.png", 4)
        val fetcher = CountingFetcher().add(blob)
        val r = rig(fetcher, resolverOf(blob))
        val requests = LateBoundAssetRequests()
        assertEquals(0L, requests.version.value)

        requests.request(blob.path, AssetPriority.Visible) // niente prefetcher: non fa niente e non lancia
        requests.bind(r.pf, r.scope)
        assertTrue(requests.version.value >= 1L) // chi osserva ricontrolla e richiede di nuovo
        delay(200)
        assertEquals(0, fetcher.calls.get()) // la richiesta di prima non è stata accodata

        requests.request(blob.path, AssetPriority.Visible)
        waitUntil("asset in cache") { r.store.peek(blob.path) != null }
        assertEquals(1, fetcher.calls.get())
    }

    @Test
    fun `un manifest nuovo seguito da invalidate fa ripartire la richiesta di un file prima sconosciuto`() = runBlocking<Unit> {
        val blob = smallBlob("furniture/ob_a.png", 5)
        val fetcher = CountingFetcher().add(blob)
        val r = rig(fetcher, BlobResolver.EMPTY)
        val requests = LateBoundAssetRequests().also { it.bind(r.pf, r.scope) }

        requests.request(blob.path, AssetPriority.Visible) // il manifest non lo prevede ancora: niente da scaricare
        delay(150)
        assertEquals(0, fetcher.calls.get())
        assertEquals(AssetAvailability.Unavailable, r.store.availability(blob.path))

        r.store.replaceSnapshot(resolverOf(blob)) // arriva il manifest
        r.pf.onManifestChanged()
        val v = requests.version.value
        requests.invalidate()
        assertEquals(v + 1, requests.version.value)
        requests.request(blob.path, AssetPriority.Visible) // l'osservatore, vedendo la versione, richiede di nuovo

        waitUntil("asset in cache") { r.store.peek(blob.path) != null }
        assertEquals(1, fetcher.calls.get())
    }

    @Test
    fun `il secondo collegamento e rifiutato e non cambia le richieste`() = runBlocking<Unit> {
        val blob = smallBlob("furniture/ob_a.png", 6)
        val first = rig(CountingFetcher().add(blob), resolverOf(blob))
        val secondFetcher = CountingFetcher().add(blob)
        val second = rig(secondFetcher, resolverOf(blob))
        val requests = LateBoundAssetRequests().also { it.bind(first.pf, first.scope) }

        assertFailsWith<IllegalStateException> { requests.bind(second.pf, second.scope) }
        requests.request(blob.path, AssetPriority.Visible)
        waitUntil("asset nel primo") { first.store.peek(blob.path) != null }
        delay(100)
        assertEquals(0, secondFetcher.calls.get())
        assertNull(second.store.peek(blob.path))
    }

    @Test
    fun `collegamenti concorrenti - ne riesce uno solo`() = runBlocking<Unit> {
        val blob = smallBlob("furniture/ob_a.png", 7)
        val rigs = List(8) { rig(CountingFetcher().add(blob), resolverOf(blob)) }
        val requests = LateBoundAssetRequests()
        val barrier = CyclicBarrier(rigs.size)
        val done = CountDownLatch(rigs.size)
        val ok = AtomicInteger()
        for (g in rigs) thread {
            barrier.await()
            try { requests.bind(g.pf, g.scope); ok.incrementAndGet() } catch (_: IllegalStateException) {}
            done.countDown()
        }
        done.await()
        assertEquals(1, ok.get())
    }

    @Test
    fun `senza sistema remoto non c'e nessun comportamento nuovo`() {
        NoAssetRequests.request("furniture/x.png", AssetPriority.Visible)
        assertEquals(0L, NoAssetRequests.version.value)
        NoAssetRequests.request("furniture/x.png", AssetPriority.Scene)
        assertEquals(0L, NoAssetRequests.version.value)
    }
}
