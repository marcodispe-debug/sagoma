package com.sagoma.planimetria.assets.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManifestParserTest {
    private val h1 = "a".repeat(64)
    private val h2 = "b".repeat(64)
    private val h3 = "c".repeat(64)

    private fun file(role: String = "model", path: String = "furniture/a.glb", sha: String = h1, size: String = "100", extra: String = "") =
        """{"role":"$role","path":"$path","sha256":"$sha","size":$size$extra}"""

    private fun asset(id: String = "a", kind: String = "furniture", files: String = file(), extra: String = "") =
        """{"id":"$id","kind":"$kind","files":[$files]$extra}"""

    private fun manifest(
        schema: String = "1",
        catalogVersion: String = "5",
        releaseId: String = "\"2026-10-03.1\"",
        minClient: String = "1",
        layout: String = "\"fanout2\"",
        assets: String = asset(),
        extra: String = "",
    ) = """{"schema":$schema,"catalog":"sagoma","catalogVersion":$catalogVersion,"releaseId":$releaseId,"publishedAt":"2026-10-03T10:00:00Z",""" +
        """"minClient":$minClient,"blobs":{"layout":$layout},"assets":[$assets]$extra}"""

    private fun valid(text: String, client: Int = 1, seen: Long = 0): Manifest {
        val r = ManifestParser.parse(text, client, seen)
        assertIs<ManifestParseResult.Valid>(r, r.toString())
        return r.manifest
    }

    private fun invalid(text: String): List<String> {
        val r = ManifestParser.parse(text)
        assertIs<ManifestParseResult.Invalid>(r, r.toString())
        return r.issues
    }

    private fun assertIssue(issues: List<String>, fragment: String) =
        assertTrue(issues.any { fragment in it }, "atteso '$fragment' in $issues")

    @Test
    fun `manifest valido completo`() {
        val sofaFiles = """{"role":"model","path":"furniture/sofa.glb","sha256":"$h1","size":10,"mime":"model/gltf-binary"},""" +
            """{"role":"thumbnail","path":"furniture/sofa.png","sha256":"$h2","size":5}"""
        val sofa = """{"id":"sofa","kind":"furniture","tier":"premium","requires":["ktx2"],"access":"protected","files":[$sofaFiles]}"""
        val old = """{"id":"old","kind":"furniture","status":"deprecated","replacedBy":"sofa","files":[${file("model", "furniture/old.glb", h3, "7")}]}"""
        val m = valid(
            manifest(
                assets = "$sofa,$old",
                extra = ""","aliases":{"legacy-sofa":"sofa"},"shards":[{"id":"s1","path":"shards/s1.json","sha256":"$h3","size":12}],""" +
                    """"signature":{"alg":"ed25519","keyId":"k1","value":"AAAA"}""",
            ),
        )
        assertEquals(1, m.schema)
        assertEquals("sagoma", m.catalog)
        assertEquals(5L, m.catalogVersion)
        assertEquals("2026-10-03.1", m.releaseId)
        assertEquals("2026-10-03T10:00:00Z", m.publishedAt)
        assertEquals(BlobLayout.Fanout2, m.blobLayout)
        val sofa0 = m.assets[0]
        assertEquals("premium", sofa0.tier)
        assertEquals(listOf("ktx2"), sofa0.requires)
        assertEquals(AssetAccess.Protected, sofa0.access)
        assertEquals(AssetStatus.Active, sofa0.status)
        assertEquals("model/gltf-binary", sofa0.files[0].mime)
        assertNull(sofa0.files[1].mime)
        assertEquals(AssetStatus.Deprecated, m.assets[1].status)
        assertEquals("sofa", m.assets[1].replacedBy)
        assertEquals(mapOf("legacy-sofa" to "sofa"), m.aliases)
        assertEquals("s1", m.shards.single().id)
        assertEquals(ManifestSignature("ed25519", "k1", "AAAA"), m.signature)
    }

    @Test
    fun `valori predefiniti`() {
        val a = valid(manifest()).assets.single()
        assertEquals(AssetStatus.Active, a.status)
        assertEquals("standard", a.tier)
        assertEquals(AssetAccess.Public, a.access)
        assertEquals(emptyList(), a.requires)
        assertNull(a.replacedBy)
        assertNull(valid(manifest()).signature)
    }

    @Test
    fun `schema non supportato e incompatibile`() {
        assertIs<ManifestParseResult.Incompatible>(ManifestParser.parse(manifest(schema = "2")))
        assertIs<ManifestParseResult.Incompatible>(ManifestParser.parse(manifest(schema = "0")))
        assertIs<ManifestParseResult.Incompatible>(ManifestParser.parse(manifest(layout = "\"quantum\"")))
    }

    @Test
    fun `minClient troppo alto e catalogo piu vecchio sono incompatibili`() {
        assertIs<ManifestParseResult.Incompatible>(ManifestParser.parse(manifest(minClient = "3"), clientVersion = 2))
        valid(manifest(minClient = "2"), client = 2)
        assertIs<ManifestParseResult.Incompatible>(ManifestParser.parse(manifest(catalogVersion = "4"), 1, minCatalogVersion = 5))
        valid(manifest(catalogVersion = "5"), seen = 5)
        valid(manifest(catalogVersion = "6"), seen = 5)
    }

    @Test
    fun `catalogVersion non valido`() {
        assertIssue(invalid(manifest(catalogVersion = "0")), "catalogVersion")
        assertIssue(invalid(manifest(catalogVersion = "-3")), "catalogVersion")
        assertIssue(invalid(manifest(catalogVersion = "\"5\"")), "catalogVersion")
        assertIssue(invalid(manifest(catalogVersion = "1.5")), "catalogVersion")
    }

    @Test
    fun `releaseId vuoto o non valido`() {
        assertIssue(invalid(manifest(releaseId = "\"\"")), "releaseId")
        assertIssue(invalid(manifest(releaseId = "\"a/b\"")), "releaseId")
        assertIssue(invalid(manifest(releaseId = "\"..\"")), "releaseId")
        assertIssue(invalid(manifest(releaseId = "\"a b\"")), "releaseId")
        assertIssue(invalid(manifest(releaseId = "5")), "releaseId")
        assertIssue(invalid(manifest(releaseId = "\"" + "x".repeat(129) + "\"")), "releaseId")
    }

    @Test
    fun `id duplicati`() {
        assertIssue(invalid(manifest(assets = asset("a") + "," + asset("a", files = file(path = "furniture/b.glb")))), "duplicate asset id 'a'")
    }

    @Test
    fun `un id di 64 caratteri esadecimali e valido e resta distinto dallo sha256 dei file`() {
        val idLooksLikeHash = "d".repeat(64)
        val m = valid(manifest(assets = asset(idLooksLikeHash, files = file(sha = h1))))
        val a = m.assets.single()
        assertEquals(idLooksLikeHash, a.id)
        assertEquals(h1, a.files.single().sha256)
        // anche in maiuscolo e con lo stesso valore dello sha256 del proprio file: sono campi distinti
        valid(manifest(assets = asset(h1, files = file(sha = h1))))
        valid(manifest(assets = asset("A".repeat(64))))
        // le altre regole valgono comunque: id duplicato
        assertIssue(invalid(manifest(assets = asset(idLooksLikeHash) + "," + asset(idLooksLikeHash, files = file(path = "furniture/b.glb")))), "duplicate asset id")
    }

    @Test
    fun `percorsi duplicati`() {
        val coherent = manifest(assets = asset("a") + "," + asset("b"))
        assertEquals(2, valid(coherent).assets.size) // stesso percorso con stesso contenuto: ammesso
        assertIssue(invalid(manifest(assets = asset("a") + "," + asset("b", files = file(sha = h2)))), "different content")
        assertIssue(invalid(manifest(assets = asset("a") + "," + asset("b", files = file(size = "101")))), "different content")
        assertIssue(invalid(manifest(assets = asset("a") + "," + asset("b", files = file(path = "furniture/A.glb")))), "only by case")
    }

    @Test
    fun `sha256 malformato`() {
        for (bad in listOf("abc", "A".repeat(64), "g".repeat(64), "a".repeat(63), "a".repeat(65), "")) {
            assertTrue(invalid(manifest(assets = asset(files = file(sha = bad)))).isNotEmpty(), bad)
        }
    }

    @Test
    fun `dimensione negativa o mancante`() {
        assertIssue(invalid(manifest(assets = asset(files = file(size = "-1")))), "negative")
        assertIssue(invalid(manifest(assets = asset(files = """{"role":"model","path":"furniture/a.glb","sha256":"$h1"}"""))), "size")
        assertIssue(invalid(manifest(assets = asset(files = file(size = "\"10\"")))), "size")
        valid(manifest(assets = asset(files = file(size = "0"))))
    }

    @Test
    fun `percorsi non validi`() {
        for (bad in listOf("/abs/a.glb", "\\abs.glb", "../a.glb", "a/../b.glb", "", "a//b.glb", "a/b?.glb", "NUL.glb", "a/.", "a ")) {
            assertTrue(invalid(manifest(assets = asset(files = file(path = bad)))).isNotEmpty(), "'$bad'")
        }
    }

    @Test
    fun `file senza sha o senza dimensione o senza ruolo o senza percorso`() {
        assertIssue(invalid(manifest(assets = asset(files = """{"role":"m","path":"a.glb","size":1}"""))), "sha256")
        assertIssue(invalid(manifest(assets = asset(files = """{"path":"a.glb","sha256":"$h1","size":1}"""))), "role")
        assertIssue(invalid(manifest(assets = asset(files = """{"role":"m","sha256":"$h1","size":1}"""))), "path")
    }

    @Test
    fun `asset senza id kind o file`() {
        assertIssue(invalid(manifest(assets = """{"kind":"f","files":[${file()}]}""")), "id")
        assertIssue(invalid(manifest(assets = """{"id":"a","files":[${file()}]}""")), "kind")
        assertIssue(invalid(manifest(assets = """{"id":"a","kind":"f"}""")), "no files")
        assertIssue(invalid(manifest(assets = """{"id":"a","kind":"f","files":[]}""")), "no files")
        assertIssue(invalid(manifest(assets = """{"id":"","kind":"f","files":[${file()}]}""")), "id")
    }

    @Test
    fun `due file con lo stesso ruolo`() {
        assertIssue(invalid(manifest(assets = asset(files = file() + "," + file(path = "furniture/b.glb", sha = h2)))), "role 'model'")
    }

    @Test
    fun `replacedBy non valido`() {
        assertIssue(invalid(manifest(assets = asset("a", extra = ""","status":"deprecated","replacedBy":"zzz""""))), "unknown asset 'zzz'")
        assertIssue(invalid(manifest(assets = asset("a", extra = ""","status":"deprecated","replacedBy":"a""""))), "itself")
        assertIssue(invalid(manifest(assets = asset("a", extra = ""","replacedBy":"b"""") + "," + asset("b", files = file(path = "furniture/b.glb", sha = h2)))), "active")
        val cycle = asset("a", extra = ""","status":"withdrawn","replacedBy":"b"""") + "," +
            asset("b", files = file(path = "furniture/b.glb", sha = h2), extra = ""","status":"withdrawn","replacedBy":"a"""")
        assertIssue(invalid(manifest(assets = cycle)), "cycle")
    }

    @Test
    fun `alias incoerenti`() {
        assertIssue(invalid(manifest(extra = ""","aliases":{"x":"nope"}""")), "unknown asset")
        assertIssue(invalid(manifest(extra = ""","aliases":{"a":"a"}""")), "collides")
        assertIssue(invalid(manifest(extra = ""","aliases":{"x":"a","y":"x"}""")), "another alias")
        assertIssue(invalid(manifest(extra = ""","aliases":{"x":""}""")), "no target")
    }

    @Test
    fun `status access e requires sconosciuti`() {
        assertIssue(invalid(manifest(assets = asset(extra = ""","status":"bogus""""))), "status")
        assertIssue(invalid(manifest(assets = asset(extra = ""","access":"vip""""))), "access")
        assertIssue(invalid(manifest(assets = asset(extra = ""","requires":[1]"""))), "requires")
        assertIssue(invalid(manifest(assets = asset(extra = ""","requires":"x""""))), "requires")
    }

    @Test
    fun `json rotto o di tipo sbagliato`() {
        assertEquals(listOf("not valid JSON"), invalid("{"))
        assertEquals(listOf("not valid JSON"), invalid(""))
        assertIssue(invalid("[]"), "not a JSON object")
        assertTrue(invalid("{}").size >= 4)
        assertIssue(invalid("""{"schema":1,"catalog":"c","catalogVersion":1,"releaseId":"r","blobs":{"layout":"flat"},"assets":{}}"""), "assets")
    }

    @Test
    fun `shard e firma malformati`() {
        assertIssue(invalid(manifest(extra = ""","shards":[{"id":"s","path":"../x","sha256":"$h1","size":1}]""")), "shards[0].path")
        assertIssue(invalid(manifest(extra = ""","signature":{"alg":"ed25519"}""")), "signature")
    }

    @Test
    fun `i problemi sono tutti segnalati e non contengono dati riservati`() {
        val issues = invalid(manifest(catalogVersion = "0", releaseId = "\"\"", assets = asset(files = file(sha = "zz", size = "-4"))))
        assertTrue(issues.size >= 4, issues.toString())
        val err = RemoteError.ManifestInvalid(issues)
        assertTrue(err.describe().startsWith("manifest invalid"))
    }

    @Test
    fun `lo scheletro di un manifest incompatibile non deve essere completo`() {
        // un manifest con schema futuro e campi sconosciuti non e "invalido": l'app lo dice incompatibile senza leggerlo
        val r = ManifestParser.parse("""{"schema":7,"future":true}""")
        assertIs<ManifestParseResult.Incompatible>(r)
        assertIs<RemoteError.ManifestIncompatible>(r.toRemoteError())
        assertNull(ManifestParseResult.Valid(valid(manifest())).toRemoteError())
    }

    @Test
    fun `campi sconosciuti vengono ignorati`() {
        valid(manifest(extra = ""","futureField":{"x":1}"""))
    }

    @Test
    fun `layout chiavi dei blob`() {
        assertEquals(h1, BlobLayout.Flat.keyOf(h1))
        assertEquals("aa/$h1", BlobLayout.Fanout2.keyOf(h1))
    }
}
