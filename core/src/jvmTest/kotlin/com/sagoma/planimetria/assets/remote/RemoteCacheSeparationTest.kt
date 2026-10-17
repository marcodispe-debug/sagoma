package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.AssetPutResult
import com.sagoma.planimetria.assets.CompositeAssetStore
import com.sagoma.planimetria.assets.DiskAssetCache
import com.sagoma.planimetria.assets.HashCheckedCacheView
import com.sagoma.planimetria.assets.REMOTE_CACHE_DIR_NAME
import com.sagoma.planimetria.assets.openRemoteAssetCache
import com.sagoma.planimetria.assets.openVersionedAssetCache
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cache remota separata da quella versionata, verifica obbligatoria dell'impronta e assenza dal manifest. */
class RemoteCacheSeparationTest {
    private val dirs = mutableListOf<File>()
    private val stores = mutableListOf<RemoteAssetStore>()

    @AfterTest
    fun cleanup() {
        stores.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun dir(): File = kotlin.io.path.createTempDirectory("sagoma-sep-").toFile().also { dirs += it }
    private fun bytes(n: Int, seed: Int) = ByteArray(n) { (it * 31 + seed).toByte() }
    private fun blob(path: String = "furniture/a.glb", seed: Int = 1) = TestBlob(path, BytesContent(bytes(5_000, seed)))
    private val fast = FetchPolicy(baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = 0)
    private fun store(r: BlobResolver, c: com.sagoma.planimetria.assets.AssetCache, f: BlobFetcher) = RemoteAssetStore(r, c, f, fast).also { stores += it }

    // ---- separazione ----

    @Test
    fun remoteCacheIsIndependentFromBundledCacheVersion() = runBlocking<Unit> {
        val base = dir()
        val b = blob()
        val fetcher = CountingFetcher().add(b)

        // App versione 1: cache degli asset impacchettati + cache remota nella STESSA cartella di base.
        val bundled1 = openVersionedAssetCache(base, "apk:100", 1L shl 24)!!
        bundled1.put("furniture/bundled.glb", bytes(1_000, 7))
        val remote1 = openRemoteAssetCache(base, 1L shl 26)!!
        assertEquals(RemoteEnsureResult.Available, store(resolverOf(b), remote1, fetcher).ensureDetailed(b.path))
        assertEquals(1, fetcher.calls.get())
        val versionDirs1 = base.listFiles()!!.filter { it.name.startsWith("v-") }
        assertEquals(1, versionDirs1.size)
        assertTrue(File(base, REMOTE_CACHE_DIR_NAME).isDirectory)

        // Aggiornamento dell'app: un'altra versione. La cache versionata vecchia si butta; quella remota resta.
        val bundled2 = openVersionedAssetCache(base, "apk:200", 1L shl 24)!!
        assertFalse(versionDirs1.single().exists(), "la cache versionata della vecchia installazione va tolta")
        assertNull(bundled2.info("furniture/bundled.glb"))
        assertTrue(File(base, REMOTE_CACHE_DIR_NAME).isDirectory, "la cache remota non si tocca")

        // Riavvio: il blob remoto e ancora li, verificato, senza rete.
        val remote2 = openRemoteAssetCache(base, 1L shl 26)!!
        val offline = CountingFetcher() // se provasse a scaricare darebbe 404
        val s2 = store(resolverOf(b), remote2, offline)
        assertEquals(AssetAvailability.Available, s2.availability(b.path))
        assertEquals(RemoteEnsureResult.Available, s2.ensureDetailed(b.path))
        assertContentEquals(b.bytes(), s2.peek(b.path))
        assertEquals(0, offline.calls.get())
    }

    @Test
    fun `la cartella remota non puo essere scambiata per una cartella di versione`() {
        assertFalse(Regex("v-[0-9a-f]{16}").matches(REMOTE_CACHE_DIR_NAME))
    }

    @Test
    fun `la cache remota non sta dentro le cartelle protette`() {
        val assets = dir()
        assertNull(openRemoteAssetCache(assets, 1L shl 20, protectedDirs = listOf(assets)))
        assertNull(openRemoteAssetCache(File(assets, "sub"), 1L shl 20, protectedDirs = listOf(assets)))
        assertNotNull(openRemoteAssetCache(dir(), 1L shl 20, protectedDirs = listOf(assets)))
        assertTrue(assets.listFiles().isNullOrEmpty(), "gli asset sorgente non si toccano")
    }

    @Test
    fun `cache remota e cache degli asset impacchettati non si mischiano sullo stesso percorso`() {
        val base = dir()
        val bundled = openVersionedAssetCache(base, "apk:1", 1L shl 24)!!
        val remote = openRemoteAssetCache(base, 1L shl 24)!!
        bundled.put("furniture/a.glb", bytes(100, 1))
        remote.put("furniture/a.glb", bytes(200, 2))
        assertEquals(100L, bundled.info("furniture/a.glb")!!.size)
        assertEquals(200L, remote.info("furniture/a.glb")!!.size)
    }

    // ---- impronta obbligatoria ----

    private fun composed(r: BlobResolver, cache: DiskAssetCache, fetcher: BlobFetcher): Pair<com.sagoma.planimetria.assets.AssetStore, RemoteAssetStore> {
        val remote = store(r, cache, fetcher)
        // La cache dei blob remoti, se proprio va davanti, ci va solo dietro una vista rigorosa che chiede l'impronta al manifest.
        val view = HashCheckedCacheView(cache, { p -> remote.snapshot.entryForPath(p)?.sha256 }, requireExpected = true)
        return CompositeAssetStore(view, remote) to remote
    }

    @Test
    fun `cache corretta e disponibile senza rete`() = runBlocking<Unit> {
        val b = blob()
        val cache = DiskAssetCache(dir(), 1L shl 26); cache.put(b.path, b.bytes())
        val f = CountingFetcher()
        val (comp, remote) = composed(resolverOf(b), cache, f)
        for (s in listOf(comp, remote)) {
            assertEquals(AssetAvailability.Available, s.availability(b.path))
            assertContentEquals(b.bytes(), s.peek(b.path))
            assertEquals(AssetAvailability.Available, s.ensure(b.path))
            assertContentEquals(b.bytes(), s.read(b.path))
        }
        assertEquals(0, f.calls.get())
    }

    @Test
    fun corruptRemoteCacheIsNotAvailable() = runBlocking<Unit> {
        val b = blob()
        val cache = DiskAssetCache(dir(), 1L shl 26)
        cache.put(b.path, bytes(5_000, 99)) // stessa dimensione, altro contenuto: file corrotto
        val f = CountingFetcher().add(b)
        val (comp, remote) = composed(resolverOf(b), cache, f)
        for (s in listOf(comp, remote)) {
            assertEquals(AssetAvailability.Remote, s.availability(b.path), "la cache corrotta non e Available")
            assertNull(s.peek(b.path), "la cache corrotta non si serve")
        }
        assertEquals(0, f.calls.get()) // finora nessuna rete
        assertContentEquals(b.bytes(), comp.read(b.path)) // read: scarica e verifica
        assertEquals(1, f.calls.get())
        assertEquals(b.sha, cache.info(b.path)!!.sha256)
        assertEquals(AssetAvailability.Available, comp.availability(b.path))
        comp.read(b.path)
        assertEquals(1, f.calls.get())
    }

    @Test
    fun `cache con lo stesso percorso e un'altra impronta non e disponibile e si scarica la nuova`() = runBlocking<Unit> {
        val v1 = TestBlob("furniture/a.glb", BytesContent(bytes(5_000, 1)))
        val v2 = TestBlob("furniture/a.glb", BytesContent(bytes(5_000, 2)))
        val cache = DiskAssetCache(dir(), 1L shl 26); cache.put(v1.path, v1.bytes()) // cache H1
        val f = CountingFetcher().add(v2)
        val (comp, remote) = composed(resolverOf(v2), cache, f) // manifest H2
        assertEquals(AssetAvailability.Remote, comp.availability(v2.path))
        assertNull(comp.peek(v2.path))
        assertEquals(AssetAvailability.Available, comp.ensure(v2.path))
        assertEquals(1, f.calls.get())
        assertEquals(v2.sha, cache.info(v2.path)!!.sha256)
        assertContentEquals(v2.bytes(), remote.peek(v2.path))
    }

    // ---- assenza dal manifest ----

    @Test
    fun removedManifestPathIsUnavailable() = runBlocking<Unit> {
        val kept = blob("furniture/kept.glb", 1)
        val removed = blob("furniture/removed.glb", 2)
        val cache = DiskAssetCache(dir(), 1L shl 26)
        cache.put(removed.path, removed.bytes()) // era nel manifest V1, e ancora in cache
        val f = CountingFetcher().add(kept).add(removed)
        val (comp, remote) = composed(resolverOf(kept, catalogVersion = 2), cache, f) // V2 non lo ha piu
        assertNotNull(cache.info(removed.path)) // il file e ancora li
        for (s in listOf(comp, remote)) {
            assertEquals(AssetAvailability.Unavailable, s.availability(removed.path))
            assertNull(s.peek(removed.path))
            assertEquals(AssetAvailability.Unavailable, s.ensure(removed.path))
            assertNull(s.read(removed.path))
        }
        assertEquals(RemoteEnsureResult.NotInCatalog, remote.ensureDetailed(removed.path))
        assertNull(remote.state(removed.path))
        assertEquals(0, f.calls.get())
        // gli altri asset non ne risentono
        assertEquals(AssetAvailability.Available, comp.ensure(kept.path))
    }

    @Test
    fun `la vista rigorosa distingue assenza dal manifest da nessuna aspettativa`() {
        val cache = DiskAssetCache(dir(), 1L shl 24)
        val d = bytes(100, 3)
        assertEquals(AssetPutResult.Stored, cache.put("a.bin", d))
        val strict = HashCheckedCacheView(cache, { null }, requireExpected = true)
        val lax = HashCheckedCacheView(cache, { null }) // cache degli asset impacchettati: "nessuna aspettativa" = vale com'e
        assertEquals(AssetAvailability.Unavailable, strict.availability("a.bin"))
        assertNull(strict.peek("a.bin"))
        assertEquals(AssetAvailability.Available, lax.availability("a.bin"))
        assertContentEquals(d, lax.peek("a.bin"))
        // con un'aspettativa le due coincidono
        val sha = TestBlob("a.bin", BytesContent(d)).sha
        for (v in listOf(HashCheckedCacheView(cache, { sha }, true), HashCheckedCacheView(cache, { sha }, false))) {
            assertEquals(AssetAvailability.Available, v.availability("a.bin"))
            assertContentEquals(d, v.peek("a.bin"))
        }
        for (v in listOf(HashCheckedCacheView(cache, { "0".repeat(64) }, true), HashCheckedCacheView(cache, { "0".repeat(64) }, false))) {
            assertEquals(AssetAvailability.Unavailable, v.availability("a.bin"))
            assertNull(v.peek("a.bin"))
        }
        // percorso non in cache: ci pensa la cache
        assertEquals(AssetAvailability.Unavailable, strict.availability("altro.bin"))
    }
}
