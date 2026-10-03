package com.sagoma.planimetria.assets.remote

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpBlobFetcherTest {
    private val server = FakeBlobServer()

    @AfterTest
    fun stop() = server.close()

    private fun bytes(n: Int) = ByteArray(n) { (it * 7).toByte() }

    private class Collect : BlobSink {
        val out = ByteArrayOutputStream()
        var chunks = 0
        var maxChunk = 0
        override fun accept(chunk: ByteArray, offset: Int, length: Int): Boolean {
            out.write(chunk, offset, length)
            chunks++
            maxChunk = maxOf(maxChunk, length)
            return true
        }
    }

    private fun fetch(f: HttpBlobFetcher, key: String, offset: Long = 0, size: Long? = null, sink: BlobSink): FetchResult =
        runBlocking { f.fetch(BlobRequest(key, offset, size), sink) }

    @Test
    fun `200 consegna i byte a blocchi`() {
        val d = bytes(100_000)
        server.content("k", BytesContent(d))
        val sink = Collect()
        val r = fetch(HttpBlobFetcher(server.baseUrl, bufferSize = 4096), "k", size = 100_000, sink = sink)
        assertEquals(FetchResult.Success(100_000, 100_000), r)
        assertContentEquals(d, sink.out.toByteArray())
        assertTrue(sink.chunks > 10, "chunks=${sink.chunks}")
        assertTrue(sink.maxChunk <= 4096)
    }

    @Test
    fun `stati HTTP`() {
        for (code in listOf(403, 404, 429, 500, 503, 418)) server.route("s$code", Behavior.Status(code))
        for (code in listOf(403, 404, 429, 500, 503, 418)) {
            assertEquals(FetchResult.Http(code), fetch(HttpBlobFetcher(server.baseUrl), "s$code", sink = Collect()), "code $code")
        }
        server.route("ra", Behavior.Status(429, retryAfter = "7"))
        assertEquals(FetchResult.Http(429, 7), fetch(HttpBlobFetcher(server.baseUrl), "ra", sink = Collect()))
        assertEquals(FetchResult.Http(404), fetch(HttpBlobFetcher(server.baseUrl), "missing", sink = Collect()))
    }

    @Test
    fun `mappa degli stati HTTP sul modello d'errore`() {
        assertEquals(RemoteError.AccessDenied, httpStatusToRemoteError(403))
        assertEquals(RemoteError.AccessDenied, httpStatusToRemoteError(401))
        assertEquals(RemoteError.NotFound, httpStatusToRemoteError(404))
        assertEquals(RemoteError.TooManyRequests(9), httpStatusToRemoteError(429, 9))
        assertEquals(RemoteError.ServerError(500), httpStatusToRemoteError(500))
        assertEquals(RemoteError.ServerError(503), httpStatusToRemoteError(503))
        assertEquals(RemoteError.HttpStatus(418), httpStatusToRemoteError(418))
        assertEquals(RemoteError.HttpStatus(302), httpStatusToRemoteError(302))
    }

    @Test
    fun `lunghezza annunciata diversa da quella attesa prima di leggere il corpo`() {
        server.content("k", BytesContent(bytes(500)))
        val sink = Collect()
        val r = fetch(HttpBlobFetcher(server.baseUrl), "k", size = 400, sink = sink)
        assertEquals(FetchResult.ContentLengthMismatch(400, 500), r)
        assertEquals(0, sink.out.size())
    }

    @Test
    fun `risposta troncata`() {
        server.route("k", Behavior.Cut(BytesContent(bytes(100_000)), 30_000))
        val sink = Collect()
        val r = fetch(HttpBlobFetcher(server.baseUrl), "k", size = 100_000, sink = sink)
        assertIs<FetchResult.Truncated>(r)
        assertTrue(r.bytesDelivered in 1..30_000, r.toString())
        assertEquals(100_000L, r.announced)
        assertEquals(r.bytesDelivered, sink.out.size().toLong())
    }

    @Test
    fun `timeout`() {
        server.route("k", Behavior.Hang(2_000))
        assertEquals(FetchResult.Timeout, fetch(HttpBlobFetcher(server.baseUrl, readTimeoutMillis = 200), "k", sink = Collect()))
    }

    @Test
    fun `timeout durante il corpo`() {
        server.route("k", Behavior.Ok(BytesContent(bytes(100_000)), chunkSize = 1_000, delayPerChunkMs = 800))
        val r = fetch(HttpBlobFetcher(server.baseUrl, readTimeoutMillis = 200), "k", sink = Collect())
        assertEquals(FetchResult.Timeout, r)
    }

    @Test
    fun `connessione rifiutata`() {
        val dead = FakeBlobServer()
        val url = dead.baseUrl
        dead.close()
        val r = fetch(HttpBlobFetcher(url, headers = mapOf("Authorization" to "Bearer SEGRETO")), "k", sink = Collect())
        assertIs<FetchResult.ConnectionFailed>(r)
        assertFalse("127.0.0.1" in r.toString() || "SEGRETO" in r.toString(), r.toString())
    }

    @Test
    fun `il sink puo fermare il trasferimento`() {
        server.content("k", BytesContent(bytes(200_000)))
        var n = 0
        val r = fetch(HttpBlobFetcher(server.baseUrl, bufferSize = 1000), "k", sink = BlobSink { _, _, _ -> ++n < 3 })
        assertIs<FetchResult.SinkRejected>(r)
        assertEquals(2000L, r.bytesDelivered)
    }

    @Test
    fun `offset diverso da zero usa Range e pretende 206`() {
        val d = bytes(10_000)
        server.content("k", BytesContent(d))
        val sink = Collect()
        val r = fetch(HttpBlobFetcher(server.baseUrl), "k", offset = 4_000, size = 10_000, sink = sink)
        assertEquals(FetchResult.Success(6_000, 6_000), r)
        assertContentEquals(d.copyOfRange(4_000, 10_000), sink.out.toByteArray())
        assertTrue(server.requestHeaders.last().any { (k, v) -> k.equals("Range", true) && v == listOf("bytes=4000-") })
    }

    @Test
    fun `intestazioni proprie e niente compressione`() {
        server.content("k", BytesContent(bytes(10)))
        fetch(HttpBlobFetcher(server.baseUrl, headers = mapOf("X-Test" to "uno")), "k", sink = Collect())
        val h = server.requestHeaders.last()
        assertTrue(h.any { (k, v) -> k.equals("X-Test", true) && v == listOf("uno") })
        assertTrue(h.any { (k, v) -> k.equals("Accept-Encoding", true) && v == listOf("identity") })
    }

    @Test
    fun `corpo vuoto`() {
        server.content("k", BytesContent(ByteArray(0)))
        assertEquals(FetchResult.Success(0, 0), fetch(HttpBlobFetcher(server.baseUrl), "k", size = 0, sink = Collect()))
    }
}
