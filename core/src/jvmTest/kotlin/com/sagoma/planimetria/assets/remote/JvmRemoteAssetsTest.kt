package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.openVersionedAssetCache
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La factory JVM: tutto il percorso con un server locale, poi il riavvio senza rete. */
class JvmRemoteAssetsTest {
    private val server = FakeBlobServer()
    private val dirs = mutableListOf<File>()
    private val systems = mutableListOf<RemoteAssets>()

    @AfterTest
    fun cleanup() {
        systems.forEach { it.close() }
        server.close()
        dirs.forEach { it.deleteRecursively() }
    }

    private fun dir(): File = kotlin.io.path.createTempDirectory("sagoma-jvmra-").toFile().also { dirs += it }
    private val policy = FetchPolicy(baseBackoffMillis = 0, maxBackoffMillis = 0, failureCooldownMillis = 0)

    private fun build(base: File, manifestUrl: String = "${server.baseUrl}/canale/stabile.json", blobUrl: String = "${server.baseUrl}/blob", bundled: String? = null) =
        createJvmRemoteAssets(base, manifestUrl, blobUrl, 1L shl 24, bundledManifest = bundled, policy = policy)?.also { systems += it }

    @Test
    fun `dal manifest al file in cache, e al riavvio senza rete tutto resta disponibile`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        server.content("canale/stabile.json", BytesContent(manifestJson(1, a).encodeToByteArray()))
        server.content("blob/${a.key}", a.content)
        val base = dir()

        val ra = assertNotNull(build(base))
        assertSame(BlobResolver.EMPTY, ra.store.snapshot) // niente manifest ne seme: partenza vuota
        val got = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { ra.prefetcher.arrived.collect { got += it } }
        assertIs<ManifestRefreshResult.Updated>(ra.refreshManifest())
        assertEquals(AssetAvailability.Remote, ra.store.availability(a.path))
        ra.prefetcher.request(a.path, AssetPriority.Scene)
        waitUntil("arrivo") { got.isNotEmpty() }
        assertContentEquals(a.bytes(), ra.store.peek(a.path))
        assertEquals(listOf(a.path), got.toList())
        collector.cancel()
        // layout su disco
        assertTrue(File(base, "remote").isDirectory)
        assertTrue(File(base, "remote-manifest/manifest.json").isFile)
        ra.close()

        // riavvio con server irraggiungibile: il manifest salvato e la cache bastano
        val dead = FakeBlobServer(); val deadUrl = dead.baseUrl; dead.close()
        val again = assertNotNull(build(base, manifestUrl = "$deadUrl/m.json", blobUrl = "$deadUrl/blob"))
        assertEquals(1L, again.manifests.current!!.catalogVersion)
        assertEquals(ManifestOrigin.Persisted, again.manifests.current!!.origin)
        assertEquals(AssetAvailability.Available, again.store.availability(a.path))
        assertContentEquals(a.bytes(), again.store.peek(a.path))
        assertEquals(ManifestRefreshResult.Failed(RemoteError.Offline), again.refreshManifest()) // offline: l'ultimo manifest resta
        assertEquals(1L, again.manifests.current!!.catalogVersion)
    }

    private fun assertSame(expected: Any?, actual: Any?) = kotlin.test.assertSame(expected, actual)

    @Test
    fun `il seme dell'app e il manifest iniziale`() {
        val a = smallBlob("furniture/a.glb", 1)
        val ra = assertNotNull(build(dir(), bundled = manifestJson(4, a)))
        assertEquals(ManifestOrigin.Bundled, ra.manifests.current!!.origin)
        assertSame(ra.manifests.current!!.resolver, ra.store.snapshot)
    }

    @Test
    fun `la cache versionata puo essere ripulita senza toccare il remoto`() = runBlocking<Unit> {
        val a = smallBlob("furniture/a.glb", 1)
        server.content("canale/stabile.json", BytesContent(manifestJson(1, a).encodeToByteArray()))
        server.content("blob/${a.key}", a.content)
        val base = dir()
        openVersionedAssetCache(base, "apk:1", 1L shl 20)
        val ra = assertNotNull(build(base))
        ra.refreshManifest()
        assertEquals(RemoteEnsureResult.Available, ra.store.ensureDetailed(a.path))
        ra.close()
        openVersionedAssetCache(base, "apk:2", 1L shl 20) // aggiornamento dell'app: toglie le cartelle v-... vecchie
        val again = assertNotNull(build(base, manifestUrl = "http://127.0.0.1:1/m.json"))
        assertEquals(AssetAvailability.Available, again.store.availability(a.path))
    }

    @Test
    fun `senza remoto se le cartelle sarebbero dentro quelle protette`() {
        val assets = dir()
        assertNull(createJvmRemoteAssets(assets, "http://x/m", "http://x/b", 1L shl 20, protectedDirs = listOf(assets)))
        assertNull(createJvmRemoteAssets(File(assets, "sub"), "http://x/m", "http://x/b", 1L shl 20, protectedDirs = listOf(assets)))
        assertTrue(assets.listFiles().isNullOrEmpty(), "gli asset sorgente non si toccano")
        assertFalse(remoteDirNames.any { Regex("v-[0-9a-f]{16}").matches(it) })
    }
}
