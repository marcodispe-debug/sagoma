package com.sagoma.planimetria.assets.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * [BlobFetcher] su HTTP(S) con `HttpURLConnection` (Android e computer, nessuna libreria in più). Solo trasporto:
 * `GET <baseUrl>/<blobKey>`, i byte vanno a blocchi al [BlobSink] senza accumularli.
 *
 * [baseUrl] e [headers] possono contenere dati riservati (token, firme): non finiscono mai nei risultati. Chiede
 * `Accept-Encoding: identity`, così la lunghezza annunciata è quella dei byte che arrivano. Se `request.offset > 0`
 * chiede `Range` e pretende `206`. I timeout sono di connessione e di inattività in lettura; una lettura bloccata
 * si interrompe al più tardi allo scadere di quest'ultimo anche se la coroutine viene cancellata.
 */
class HttpBlobFetcher(
    private val baseUrl: String,
    private val headers: Map<String, String> = emptyMap(),
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 30_000,
    private val bufferSize: Int = 64 * 1024,
) : BlobFetcher {
    init {
        require(bufferSize > 0) { "bufferSize deve essere positivo" }
    }

    override suspend fun fetch(request: BlobRequest, sink: BlobSink): FetchResult = withContext(Dispatchers.IO) {
        val conn = try {
            URL(baseUrl.trimEnd('/') + "/" + request.blobKey).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext FetchResult.ConnectionFailed(e::class.simpleName ?: "")
        } catch (e: RuntimeException) {
            return@withContext FetchResult.ConnectionFailed("InvalidUrl")
        }
        try {
            conn.connectTimeout = connectTimeoutMillis
            conn.readTimeout = readTimeoutMillis
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (request.offset > 0) conn.setRequestProperty("Range", "bytes=${request.offset}-")
            for ((k, v) in headers) conn.setRequestProperty(k, v)

            val status = try {
                conn.responseCode
            } catch (e: SocketTimeoutException) {
                return@withContext FetchResult.Timeout
            } catch (e: IOException) {
                return@withContext FetchResult.ConnectionFailed(e::class.simpleName ?: "")
            }
            val expectedStatus = if (request.offset > 0) 206 else 200
            if (status != expectedStatus) {
                val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                runCatching { conn.errorStream?.close() }
                return@withContext FetchResult.Http(status, retryAfter)
            }

            val announced = conn.contentLengthLong.takeIf { it >= 0 }
            val expectedBody = request.expectedSize?.let { it - request.offset }
            if (announced != null && expectedBody != null && announced != expectedBody) {
                return@withContext FetchResult.ContentLengthMismatch(expectedBody, announced)
            }

            var delivered = 0L
            try {
                conn.inputStream.use { input ->
                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        if (!sink.accept(buffer, 0, n)) return@withContext FetchResult.SinkRejected(delivered)
                        delivered += n
                    }
                }
            } catch (e: SocketTimeoutException) {
                return@withContext FetchResult.Timeout
            } catch (e: IOException) {
                return@withContext FetchResult.Truncated(delivered, announced)
            }
            if (announced != null && delivered != announced) return@withContext FetchResult.Truncated(delivered, announced)
            FetchResult.Success(delivered, announced)
        } finally {
            conn.disconnect()
        }
    }
}
