package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RemoteAssetsTest {
    private val dirs = mutableListOf<File>()
    private val systems = mutableListOf<RemoteAssets>()

    @AfterTest
    fun cleanup() {
        systems.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun cache() = DiskAssetCache(kotlin.io.path.createTempDirectory("sagoma-ra-").toFile().also { dirs += it }, 1L shl 26)
    private val policy = FetchPolicy(baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = 0)

    private class FakeManifestFetcher(var result: () -> ManifestFetchResult) : ManifestFetcher {
        override suspend fun fetch(): ManifestFetchResult = result()
    }

    private fun system(bundled: String?, fetcher: BlobFetcher, manifestFetcher: ManifestFetcher? = null, persistence: ManifestPersistence? = null): RemoteAssets {
        val repo = ManifestRepository(persistence, bundled, manifestFetcher)
        return RemoteAssets.create(repo, cache(), fetcher, policy, maxParallel = 2).also { systems += it }
    }

    @Test
    fun `senza manifest parte con il resolver vuoto e il primo manifest lo sostituisce`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val ra = system(null, CountingFetcher().add(b))
        assertSame(BlobResolver.EMPTY, ra.store.snapshot)
        assertEquals(AssetAvailability.Unavailable, ra.store.availability(b.path))
        val r = ra.install(manifestJson(1, b))
        assertIs<ManifestRefreshResult.Updated>(r)
        assertSame(ra.manifests.current!!.resolver, ra.store.snapshot)
        assertEquals(AssetAvailability.Remote, ra.store.availability(b.path))
    }

    @Test
    fun `un manifest iniziale e gia lo snapshot dello store`() {
        val b = smallBlob("furniture/a.glb", 1)
        val ra = system(manifestJson(3, b), CountingFetcher())
        assertSame(ra.manifests.current!!.resolver, ra.store.snapshot)
        assertEquals(3L, ra.store.snapshot.manifest.catalogVersion)
    }

    @Test
    fun `da V1 a V2 lo store usa il resolver nuovo e quello vecchio non cambia`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1); val b = smallBlob("furniture/b.glb", 2)
        val mf = FakeManifestFetcher { ManifestFetchResult.Text(manifestJson(2, a, b)) }
        val ra = system(manifestJson(1, a), CountingFetcher(), mf)
        val v1 = ra.store.snapshot
        assertEquals(AssetAvailability.Unavailable, ra.store.availability(b.path))
        assertIs<ManifestRefreshResult.Updated>(ra.refreshManifest())
        val v2 = ra.store.snapshot
        assertNotSame(v1, v2)
        assertSame(ra.manifests.current!!.resolver, v2)
        assertEquals(AssetAvailability.Remote, ra.store.availability(b.path))
        assertNull(v1.entryForPath(b.path)) // il vecchio non e stato toccato
        assertEquals(1L, v1.manifest.catalogVersion)
    }

    @Test
    fun `un refresh fallito lascia il manifest attivo e lo store com'erano`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val results = listOf<ManifestFetchResult>(
            ManifestFetchResult.Error(RemoteError.Offline),
            ManifestFetchResult.Error(RemoteError.ServerError(503)),
            ManifestFetchResult.Text("{non json"),
            ManifestFetchResult.Text(manifestJson(1, a).replace("\"schema\":1", "\"schema\":9")),
            ManifestFetchResult.Text(manifestJson(0, a)),
        )
        var next = 0
        val mf = FakeManifestFetcher { results[next++] }
        val ra = system(manifestJson(5, a), CountingFetcher(), mf)
        val before = ra.store.snapshot
        for (i in results.indices) {
            assertIs<ManifestRefreshResult.Failed>(ra.refreshManifest(), "caso $i")
            assertSame(before, ra.store.snapshot)
            assertSame(before, ra.manifests.current!!.resolver)
        }
        // una versione piu vecchia e rifiutata
        mf.result = { ManifestFetchResult.Text(manifestJson(4, a)) }
        assertIs<ManifestRefreshResult.Failed>(ra.refreshManifest())
        assertSame(before, ra.store.snapshot)
        assertEquals(0, ra.appliedCount, "nessun fallimento applica niente")
    }

    @Test
    fun `un refresh invariato non ricostruisce niente`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val text = manifestJson(2, a)
        val ra = system(text, CountingFetcher(), FakeManifestFetcher { ManifestFetchResult.Text(text) })
        val before = ra.store.snapshot
        val r = ra.refreshManifest()
        assertIs<ManifestRefreshResult.Unchanged>(r)
        assertSame(before, ra.store.snapshot)
        assertSame(before, ra.manifests.current!!.resolver)
        assertEquals(0, ra.appliedCount, "un manifest invariato non applica niente")
    }

    @Test
    fun `un Unchanged riallinea lo store al repository`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val v2 = manifestJson(2, a)
        // il repository e gia su V2, lo store e rimasto su V1
        val repo = ManifestRepository(null, v2, FakeManifestFetcher { ManifestFetchResult.Text(v2) })
        val oldResolver = resolverOf(a, catalogVersion = 1)
        val store = RemoteAssetStore(oldResolver, cache(), CountingFetcher(), policy)
        val ra = RemoteAssets(repo, store, AssetPrefetcher(store)).also { systems += it }
        assertSame(oldResolver, ra.store.snapshot)
        assertEquals(2L, repo.current!!.catalogVersion)

        // install: il repository risponde Unchanged (stesso manifest) e lo store si allinea
        val r = ra.install(v2)
        assertIs<ManifestRefreshResult.Unchanged>(r)
        assertSame(repo.current!!.resolver, ra.store.snapshot)
        assertEquals(2L, ra.store.snapshot.manifest.catalogVersion)
        assertEquals(1, ra.appliedCount)

        // lo stesso vale per refreshManifest
        ra.store.replaceSnapshot(oldResolver) // di nuovo indietro
        assertIs<ManifestRefreshResult.Unchanged>(ra.refreshManifest())
        assertSame(repo.current!!.resolver, ra.store.snapshot)
        assertEquals(2, ra.appliedCount)
        // gia allineato: Unchanged non applica niente
        assertIs<ManifestRefreshResult.Unchanged>(ra.refreshManifest())
        assertEquals(2, ra.appliedCount)
    }

    @Test
    fun `un Failed non riallinea lo store e non altera lo snapshot corrente`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val v2 = manifestJson(2, a)
        val repo = ManifestRepository(null, v2, FakeManifestFetcher { ManifestFetchResult.Error(RemoteError.Offline) })
        val oldResolver = resolverOf(a, catalogVersion = 1)
        val store = RemoteAssetStore(oldResolver, cache(), CountingFetcher(), policy)
        val ra = RemoteAssets(repo, store, AssetPrefetcher(store)).also { systems += it }
        val current = repo.current
        assertIs<ManifestRefreshResult.Failed>(ra.refreshManifest())     // rete assente
        assertIs<ManifestRefreshResult.Failed>(ra.install("{non json"))   // manifest non valido
        assertIs<ManifestRefreshResult.Failed>(ra.install(manifestJson(1, a))) // versione piu vecchia
        assertSame(current, repo.current)
        assertSame(oldResolver, ra.store.snapshot, "un errore non tocca lo store")
        assertEquals(0, ra.appliedCount)
    }

    @Test
    fun `applyCurrent allinea lo store a un cambio avvenuto altrove e converge anche con applicazioni fuori ordine`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val ra = system(manifestJson(1, a), CountingFetcher())
        ra.manifests.replace(manifestJson(2, a, smallBlob("furniture/b.glb", 2))) // cambio diretto sul repository
        assertEquals(1L, ra.store.snapshot.manifest.catalogVersion) // lo store non lo sa
        ra.manifests.replace(manifestJson(3, a, smallBlob("furniture/c.glb", 3)))
        ra.applyCurrent()
        assertSame(ra.manifests.current!!.resolver, ra.store.snapshot)
        assertEquals(3L, ra.store.snapshot.manifest.catalogVersion)
        val same = ra.store.snapshot
        val applied = ra.appliedCount
        ra.applyCurrent() // gia allineato: niente
        ra.applyCurrent()
        assertSame(same, ra.store.snapshot)
        assertEquals(applied, ra.appliedCount)
    }

    @Test
    fun `cambio di manifest durante un download - il vecchio e scartato e la richiesta riparte`() = runBlocking<Unit> {
        val path = "furniture/x.glb"
        val old = smallBlob(path, 1); val new = smallBlob(path, 2)
        val gate = GateFetcher().add(old).add(new)
        val ra = system(manifestJson(1, old), gate)
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { ra.prefetcher.arrived.collect { got += it } }
        ra.prefetcher.request(path, AssetPriority.Scene)
        waitUntil("V1 in corso") { gate.requests.size == 1 }
        assertIs<ManifestRefreshResult.Updated>(ra.install(manifestJson(2, new))) // V2 cambia l'impronta di x
        gate.gate(old.key).complete(Unit)
        waitUntil("V2 in corso") { gate.requests.size == 2 }
        gate.gate(new.key).complete(Unit)
        waitUntil("arrivo") { got.isNotEmpty() }
        delay(150)
        assertEquals(listOf(path), got.toList())
        assertEquals(listOf(old.key, new.key), gate.requests.toList(), "nessun terzo download")
        assertEquals(new.sha, ra.store.state(path).let { (ra.store.snapshot.entryForPath(path)!!).sha256 })
        assertEquals(AssetState.Available, ra.store.state(path))
        collector.cancel()
    }

    @Test
    fun `se il file non cambia tra i due manifest il download in corso si installa e non se ne fa un altro`() = runBlocking<Unit> {
        val x = smallBlob("furniture/x.glb", 1); val extra = smallBlob("furniture/extra.glb", 2)
        val gate = GateFetcher().add(x).add(extra)
        val ra = system(manifestJson(1, x), gate)
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { ra.prefetcher.arrived.collect { got += it } }
        ra.prefetcher.request(x.path, AssetPriority.Scene)
        waitUntil("x in corso") { gate.requests.size == 1 }
        assertIs<ManifestRefreshResult.Updated>(ra.install(manifestJson(2, x, extra))) // x identico, extra nuovo
        gate.gate(x.key).complete(Unit)
        waitUntil("arrivo") { got.contains(x.path) }
        delay(150)
        assertEquals(listOf(x.path), got.toList())
        assertEquals(listOf(x.key), gate.requests.toList(), "x scaricato una volta sola")
        assertEquals(AssetState.Available, ra.store.state(x.path))
        assertEquals(AssetState.Remote, ra.store.state(extra.path)) // il nuovo del V2 e visibile ai prossimi
        collector.cancel()
    }

    @Test
    fun `refresh concorrenti convergono sull'ultimo manifest`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val ra = system(manifestJson(1, a), CountingFetcher())
        (2L..30L).shuffled().map { v -> async(Dispatchers.Default) { ra.install(manifestJson(v, a)) } }.forEach { it.await() }
        assertEquals(30L, ra.manifests.current!!.catalogVersion)
        assertSame(ra.manifests.current!!.resolver, ra.store.snapshot)
    }

    private class CountingPersistence(var text: String? = null) : ManifestPersistence {
        var saves = 0
        override fun load(): String? = text
        override fun save(text: String): Boolean { saves++; this.text = text; return true }
    }

    @Test
    fun `dopo close refreshManifest non fa richieste ne salva ne applica`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        var fetches = 0
        val mf = FakeManifestFetcher { fetches++; ManifestFetchResult.Text(manifestJson(2, a)) }
        val p = CountingPersistence()
        val ra = system(manifestJson(1, a), CountingFetcher(), mf, p)
        // prima della chiusura funziona come sempre
        assertIs<ManifestRefreshResult.Updated>(ra.refreshManifest())
        assertEquals(1, fetches)
        assertEquals(1, p.saves)
        val v2Resolver = ra.store.snapshot
        ra.close()
        mf.result = { fetches++; ManifestFetchResult.Text(manifestJson(3, a)) } // se arrivasse in fondo sarebbe una V3
        repeat(3) {
            assertEquals(ManifestRefreshResult.Failed(RemoteError.Cancelled), kotlinx.coroutines.withTimeout(2_000) { ra.refreshManifest() })
        }
        assertEquals(1, fetches, "nessuna richiesta dopo close")
        assertEquals(1, p.saves, "niente salvato dopo close")
        assertEquals(2L, ra.manifests.current!!.catalogVersion)
        assertSame(v2Resolver, ra.store.snapshot)
    }

    @Test
    fun `dopo close install non modifica ne il repository ne lo store ne la persistenza`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val p = CountingPersistence()
        val ra = system(manifestJson(1, a), CountingFetcher(), persistence = p)
        val v1 = ra.manifests.current
        val v1Resolver = ra.store.snapshot
        ra.close()
        for (text in listOf(manifestJson(2, a, smallBlob("furniture/b.glb", 2)), manifestJson(1, a), "{non json")) {
            assertEquals(ManifestRefreshResult.Failed(RemoteError.Cancelled), kotlinx.coroutines.withTimeout(2_000) { ra.install(text) })
        }
        assertSame(v1, ra.manifests.current)
        assertSame(v1Resolver, ra.store.snapshot)
        assertEquals(0, p.saves)
        assertEquals(null, p.text)
    }

    @Test
    fun `dopo close nemmeno applyCurrent aggiorna lo store`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val ra = system(manifestJson(1, a), CountingFetcher())
        val v1Resolver = ra.store.snapshot
        ra.close()
        ra.manifests.replace(manifestJson(2, a)) // cambio diretto sul repository
        ra.applyCurrent()
        assertSame(v1Resolver, ra.store.snapshot)
        assertEquals(0, ra.appliedCount)
    }

    @Test
    fun `close ferma prefetcher e negozio`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        val ra = system(manifestJson(1, a), CountingFetcher().add(a))
        ra.close(); ra.close()
        assertFalse(ra.prefetcher.isActive)
        assertEquals(RemoteEnsureResult.Failed(RemoteError.Cancelled), ra.store.ensureDetailed(a.path))
    }
}
