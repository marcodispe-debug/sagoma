package com.sagoma.planimetria.assets.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Una sola regola per la chiave di un blob (`BlobLayout`), valida a ogni livello anche se il parser viene aggirato. */
class BlobKeyValidationTest {
    private val h = "0123456789abcdef".repeat(4)

    private val hostile = listOf(
        "", ".", "..", "../../secret", "../" + h, h + "/..", "/" + h, h + "/", "\\" + h, h.replace("0", "\\"), "a/b",
        "%2e%2e", "%2e%2e%2f%2e%2e%2fsecret", "%2f" + h.substring(3), h.substring(0, 60) + "%2e%2e",
        "http://evil.example/x", "https://evil/" + h, "file:///etc/passwd", "javascript:alert(1)", "//evil/x", "user@evil",
        h + "?x=1", h + "#frag", h.substring(0, 63) + "?", "?" + h.substring(1), "#" + h.substring(1),
        h.uppercase(), h.substring(0, 63), h + "a", " " + h.substring(1), h.substring(1) + "\n", h.substring(1) + "\u0000",
        h.take(2) + "/" + h.substring(0, 63), h.take(2) + "/" + h + "/x", "zz/" + h, "00/" + h, // prefisso diverso dall'impronta
    )

    @Test
    fun `chiavi valide per le due disposizioni`() {
        assertTrue(BlobLayout.Flat.isValidKey(h))
        assertTrue(BlobLayout.Fanout2.isValidKey(h.take(2) + "/" + h))
        assertTrue(BlobLayout.isValidBlobKey(h))
        assertTrue(BlobLayout.isValidBlobKey(h.take(2) + "/" + h))
        assertEquals(h, BlobLayout.Flat.keyOf(h))
        assertEquals(h.take(2) + "/" + h, BlobLayout.Fanout2.keyOf(h))
        assertFalse(BlobLayout.Flat.isValidKey(h.take(2) + "/" + h)) // ogni disposizione accetta solo la sua forma
        assertFalse(BlobLayout.Fanout2.isValidKey(h))
    }

    @Test
    fun invalidBlobKeyRejectedDefensively() {
        for (bad in hostile) {
            assertFalse(BlobLayout.isValidBlobKey(bad), "chiave accettata: '${bad.replace("\n", "\\n")}'")
            assertFalse(BlobLayout.Flat.isValidKey(bad))
            assertFalse(BlobLayout.Fanout2.isValidKey(bad))
            assertFailsWith<IllegalArgumentException>("BlobRequest: $bad") { BlobRequest(bad) }
            assertFailsWith<IllegalArgumentException>("keyOf Flat: $bad") { BlobLayout.Flat.keyOf(bad) }
            assertFailsWith<IllegalArgumentException>("keyOf Fanout2: $bad") { BlobLayout.Fanout2.keyOf(bad) }
            val file = ManifestFile("m", "furniture/a.glb", bad, 10)
            assertFailsWith<IllegalArgumentException>("BlobEntry: $bad") { BlobEntry("a", file, bad) }
        }
    }

    private fun manualManifest(sha: String, layout: BlobLayout) = Manifest(
        1, "c", 1, "r", null, 0, layout,
        listOf(ManifestAsset("a", "k", AssetStatus.Active, null, "standard", emptyList(), AssetAccess.Public, listOf(ManifestFile("m", "furniture/a.glb", sha, 10)))),
        emptyMap(), emptyList(), null,
    )

    @Test
    fun `un manifest costruito a mano senza passare dal parser non puo produrre una chiave ostile`() {
        for (bad in hostile.filter { it.isNotEmpty() }) {
            for (layout in BlobLayout.entries) {
                val e = assertFailsWith<IllegalArgumentException>("resolver: $bad / $layout") { BlobResolver(manualManifest(bad, layout)) }
                assertTrue("asset 'a'" in (e.message ?: ""), e.message)
                assertFalse(bad.length > 3 && bad in (e.message ?: ""), "il messaggio non deve riportare il valore ostile")
            }
        }
        // un manifest valido costruito a mano funziona e produce chiavi canoniche
        assertEquals(h, BlobResolver(manualManifest(h, BlobLayout.Flat)).entryForPath("furniture/a.glb")!!.blobKey)
        assertEquals(h.take(2) + "/" + h, BlobResolver(manualManifest(h, BlobLayout.Fanout2)).entryForPath("furniture/a.glb")!!.blobKey)
    }

    @Test
    fun `BlobEntry richiede che la chiave sia proprio quella del suo file`() {
        val file = ManifestFile("m", "furniture/a.glb", h, 10)
        assertFailsWith<IllegalArgumentException> { BlobEntry("a", file, "f".repeat(64)) } // chiave valida ma di un altro contenuto
        BlobEntry("a", file, h)
        BlobEntry("a", file, h.take(2) + "/" + h)
    }

    @Test
    fun `il parser rifiuta ancora i valori ostili`() {
        for (bad in listOf("../../secret", "x?y", "a".repeat(63), "A".repeat(64))) {
            val r = ManifestParser.parse("""{"schema":1,"catalog":"c","catalogVersion":1,"releaseId":"r","blobs":{"layout":"flat"},"assets":[{"id":"a","kind":"k","files":[{"role":"m","path":"a.glb","sha256":"$bad","size":1}]}]}""")
            assertTrue(r is ManifestParseResult.Invalid, bad)
        }
    }
}
