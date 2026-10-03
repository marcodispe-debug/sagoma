package com.sagoma.planimetria.assets.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlobResolverTest {
    private val h = (1..6).map { it.toString().repeat(64) }

    private fun f(role: String, path: String, i: Int) = ManifestFile(role, path, h[i], 10L + i)

    private fun manifest(): Manifest {
        val assets = listOf(
            ManifestAsset("sofa", "furniture", AssetStatus.Active, null, "standard", emptyList(), AssetAccess.Public,
                listOf(f("model", "furniture/sofa.glb", 0), f("thumbnail", "furniture/sofa.png", 1))),
            ManifestAsset("old", "furniture", AssetStatus.Deprecated, "sofa", "standard", emptyList(), AssetAccess.Public,
                listOf(f("model", "furniture/old.glb", 2))),
            ManifestAsset("twin", "furniture", AssetStatus.Active, null, "standard", emptyList(), AssetAccess.Public,
                listOf(f("model", "furniture/sofa.glb", 0))), // stesso percorso e contenuto di sofa
        )
        return Manifest(1, "sagoma", 3, "r1", null, 1, BlobLayout.Fanout2, assets, mapOf("legacy" to "sofa"), emptyList(), null)
    }

    private val r = BlobResolver(manifest())

    @Test
    fun `percorso a blob`() {
        val e = r.entryForPath("furniture/sofa.png")!!
        assertEquals("sofa", e.assetId)
        assertEquals("thumbnail", e.role)
        assertEquals(h[1], e.sha256)
        assertEquals(11L, e.size)
        assertEquals("22/${h[1]}", e.blobKey)
        assertNull(r.entryForPath("furniture/nope.png"))
        assertNull(r.entryForPath(""))
    }

    @Test
    fun `percorso ripetuto da due asset ha un solo contenuto`() {
        val e = r.entryForPath("furniture/sofa.glb")!!
        assertEquals(h[0], e.sha256)
        assertEquals("sofa", e.assetId) // il primo del manifest
        assertEquals(h[0], r.entry("twin", "model")!!.sha256)
    }

    @Test
    fun `id a asset e id e ruolo a blob`() {
        assertEquals("sofa", r.asset("sofa")!!.id)
        assertNull(r.asset("nope"))
        assertEquals(h[1], r.entry("sofa", "thumbnail")!!.sha256)
        assertNull(r.entry("sofa", "top"))
        assertNull(r.entry("nope", "model"))
        assertEquals(setOf("model", "thumbnail"), r.entries("sofa").map { it.role }.toSet())
        assertTrue(r.entries("nope").isEmpty())
    }

    @Test
    fun `alias e sostituzioni`() {
        assertEquals("sofa", r.asset("legacy")!!.id)
        assertEquals(h[0], r.entry("legacy", "model")!!.sha256)
        assertEquals("sofa", r.currentAsset("old")!!.id)
        assertEquals("sofa", r.currentAsset("sofa")!!.id)
        assertEquals("sofa", r.currentAsset("legacy")!!.id)
        assertNull(r.currentAsset("nope"))
    }

    @Test
    fun `istantanea immutabile e stesse istanze a ogni richiesta`() {
        assertSame(r.entryForPath("furniture/sofa.png"), r.entryForPath("furniture/sofa.png"))
        assertSame(r.entry("sofa", "thumbnail"), r.entryForPath("furniture/sofa.png"))
        assertEquals(3, r.assetCount)
        // un manifest nuovo e un'altra istantanea: la prima non cambia
        val m2 = manifest().copy(assets = manifest().assets.take(1))
        val r2 = BlobResolver(m2)
        assertEquals(1, r2.assetCount)
        assertEquals(3, r.assetCount)
        assertNull(r2.entryForPath("furniture/old.glb"))
        assertTrue(r.entryForPath("furniture/old.glb") != null)
    }

    @Test
    fun `molti asset con ricerche costanti`() {
        val n = 20_000
        val assets = (0 until n).map { i ->
            val sha = i.toString(16).padStart(64, '0')
            ManifestAsset("a$i", "k", AssetStatus.Active, null, "standard", emptyList(), AssetAccess.Public,
                listOf(ManifestFile("model", "d/f$i.glb", sha, 1)))
        }
        val big = BlobResolver(Manifest(1, "c", 1, "r", null, 0, BlobLayout.Flat, assets, emptyMap(), emptyList(), null))
        val t = System.nanoTime()
        repeat(n) { i ->
            assertEquals("a$i", big.entryForPath("d/f$i.glb")!!.assetId)
            assertEquals("a$i", big.entry("a$i", "model")!!.assetId)
        }
        // con una ricerca lineare 20000 x 20000 sarebbe nell'ordine dei minuti: qui largo margine
        assertTrue((System.nanoTime() - t) / 1_000_000 < 5_000)
    }
}
