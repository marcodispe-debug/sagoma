package com.sagoma.planimetria.assets.remote

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpManifestFetcherTest {
    private val server = FakeBlobServer()

    @AfterTest
    fun stop() = server.close()

    private fun bytes(s: String) = BytesContent(s.encodeToByteArray())
    private fun fetcher(key: String, headers: Map<String, String> = emptyMap(), read: Int = 5_000, max: Int = 1 shl 20) =
        HttpManifestFetcher("${server.baseUrl}/$key", headers, readTimeoutMillis = read, maxBytes = max)
    private fun fetch(f: HttpManifestFetcher) = runBlocking { f.fetch() }

    @Test
    fun `200 da il testo, anche non ASCII`() {
        val text = "{\"nome\":\"caff\u00e8 \u20ac\"}"
        server.content("channel/stable.json", bytes(text))
        assertEquals(ManifestFetchResult.Text(text), fetch(fetcher("channel/stable.json")))
        server.content("vuoto", BytesContent(ByteArray(0)))
        assertEquals(ManifestFetchResult.Text(""), fetch(fetcher("vuoto")))
    }

    @Test
    fun `stati HTTP nel modello d'errore comune`() {
        for ((code, expected) in listOf(
            403 to RemoteError.AccessDenied, 404 to RemoteError.NotFound, 500 to RemoteError.ServerError(500),
            503 to RemoteError.ServerError(503), 418 to RemoteError.HttpStatus(418),
        )) {
            server.route("s$code", Behavior.Status(code))
            assertEquals(ManifestFetchResult.Error(expected), fetch(fetcher("s$code")), "stato $code")
        }
        server.route("limite", Behavior.Status(429, retryAfter = "9"))
        assertEquals(ManifestFetchResult.Error(RemoteError.TooManyRequests(9)), fetch(fetcher("limite")))
        assertEquals(ManifestFetchResult.Error(RemoteError.NotFound), fetch(fetcher("non-esiste")))
    }

    @Test
    fun `timeout e connessione rifiutata`() {
        server.route("lento", Behavior.Hang(2_000))
        assertEquals(ManifestFetchResult.Error(RemoteError.Timeout), fetch(fetcher("lento", read = 200)))
        val dead = FakeBlobServer(); val url = dead.baseUrl; dead.close()
        assertEquals(ManifestFetchResult.Error(RemoteError.Offline), fetch(HttpManifestFetcher("$url/x", connectTimeoutMillis = 1_000)))
    }

    @Test
    fun `un manifest troppo grande non si legge`() {
        server.content("grande", BytesContent(ByteArray(5_000) { 'a'.code.toByte() }))
        assertEquals(ManifestFetchResult.Error(RemoteError.TooLarge(5_000, 1_000)), fetch(fetcher("grande", max = 1_000)))
        // grande esattamente quanto il tetto: si legge
        server.content("giusto", BytesContent(ByteArray(1_000) { 'a'.code.toByte() }))
        assertEquals(1_000, assertIs<ManifestFetchResult.Text>(fetch(fetcher("giusto", max = 1_000))).text.length)
    }

    @Test
    fun `la lunghezza annunciata oltre il tetto si rifiuta prima di leggere il corpo`() {
        // 5000 byte annunciati, a blocchi di 1000: un controllo solo in lettura direbbe 1000; quello anticipato dice 5000
        server.route("grande", Behavior.Ok(BytesContent(ByteArray(5_000) { 'a'.code.toByte() }), chunkSize = 1_000, delayPerChunkMs = 50))
        assertEquals(ManifestFetchResult.Error(RemoteError.TooLarge(5_000, 500)), fetch(fetcher("grande", max = 500)))
    }

    @Test
    fun `risposta troncata`() {
        server.route("tronco", Behavior.Cut(bytes("x".repeat(50_000)), 10_000))
        val r = fetch(fetcher("tronco"))
        assertTrue(r is ManifestFetchResult.Error && (r.error == RemoteError.Offline || r.error is RemoteError.SizeMismatch), r.toString())
    }

    @Test
    fun `intestazioni proprie, niente compressione, e niente indirizzo o segreti negli errori`() {
        server.route("protetto", Behavior.Status(403))
        val r = fetch(fetcher("protetto", headers = mapOf("Authorization" to "Bearer SEGRETO", "X-Test" to "uno")))
        val h = server.requestHeaders.last()
        assertTrue(h.any { (k, v) -> k.equals("X-Test", true) && v == listOf("uno") })
        assertTrue(h.any { (k, v) -> k.equals("Accept-Encoding", true) && v == listOf("identity") })
        assertFalse("SEGRETO" in r.toString() || "127.0.0.1" in r.toString())
        // indirizzo non valido
        val bad = fetch(HttpManifestFetcher("non un indirizzo"))
        assertIs<RemoteError.CacheUnavailable>(assertIs<ManifestFetchResult.Error>(bad).error)
        assertFalse("indirizzo" in bad.toString())
    }

    @Test
    fun `con il ManifestRepository - un manifest nuovo si installa, un server in errore lascia il precedente`() = runBlocking<Unit> {
        val b = smallBlob("furniture/a.glb", 1)
        val repo = ManifestRepository(null, manifestJson(1, b), fetcher("canale/stabile.json"))
        server.content("canale/stabile.json", bytes(manifestJson(2, b, smallBlob("furniture/b.glb", 2))))
        assertIs<ManifestRefreshResult.Updated>(repo.refresh())
        assertEquals(2L, repo.current!!.catalogVersion)
        server.route("canale/stabile.json", Behavior.Status(500))
        assertEquals(ManifestRefreshResult.Failed(RemoteError.ServerError(500)), repo.refresh())
        assertEquals(2L, repo.current!!.catalogVersion)
        server.content("canale/stabile.json", bytes("{non json"))
        assertIs<RemoteError.ManifestInvalid>(assertIs<ManifestRefreshResult.Failed>(repo.refresh()).error)
        assertEquals(2L, repo.current!!.catalogVersion)
        server.content("canale/stabile.json", bytes(manifestJson(1, b))) // un tentativo di tornare indietro
        assertIs<RemoteError.ManifestIncompatible>(assertIs<ManifestRefreshResult.Failed>(repo.refresh()).error)
        assertEquals(2L, repo.current!!.catalogVersion)
    }
}
