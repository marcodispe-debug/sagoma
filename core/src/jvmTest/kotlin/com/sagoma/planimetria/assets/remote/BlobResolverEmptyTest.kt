package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlobResolverEmptyTest {
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() = dirs.forEach { it.deleteRecursively() }

    private fun cache() = DiskAssetCache(kotlin.io.path.createTempDirectory("sagoma-empty-").toFile().also { dirs += it }, 1L shl 20)

    @Test
    fun `il resolver vuoto non risolve niente e non da errori`() {
        val r = BlobResolver.EMPTY
        assertEquals(0, r.assetCount)
        assertTrue(r.assets.isEmpty())
        assertNull(r.entryForPath("furniture/a.glb"))
        assertNull(r.entryForPath(""))
        assertNull(r.asset("a"))
        assertNull(r.entry("a", "model"))
        assertTrue(r.entries("a").isEmpty())
        assertNull(r.currentAsset("a"))
        assertSame(BlobResolver.EMPTY, BlobResolver.EMPTY) // un solo oggetto, esplicito
        assertTrue(r.manifest.assets.isEmpty() && r.manifest.aliases.isEmpty() && r.manifest.shards.isEmpty())
    }

    @Test
    fun `un RemoteAssetStore con il resolver vuoto - tutto non disponibile, niente rete, niente errori`() = runBlocking<Unit> {
        val f = CountingFetcher()
        val s = RemoteAssetStore(BlobResolver.EMPTY, cache(), f)
        assertEquals(AssetAvailability.Unavailable, s.availability("furniture/a.glb"))
        assertNull(s.peek("furniture/a.glb"))
        assertNull(s.read("furniture/a.glb"))
        assertNull(s.state("furniture/a.glb"))
        assertEquals(RemoteEnsureResult.NotInCatalog, s.ensureDetailed("furniture/a.glb"))
        assertEquals(AssetAvailability.Unavailable, s.ensure("furniture/a.glb"))
        assertEquals(0, f.calls.get())
        s.close()
    }

    @Test
    fun `dal resolver vuoto al primo manifest valido`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val f = CountingFetcher().add(b)
        val s = RemoteAssetStore(BlobResolver.EMPTY, cache(), f)
        assertEquals(AssetAvailability.Unavailable, s.availability(b.path))
        s.replaceSnapshot(resolverOf(b))
        assertEquals(AssetAvailability.Remote, s.availability(b.path))
        assertEquals(RemoteEnsureResult.Available, s.ensureDetailed(b.path))
        s.close()
    }
}
