package com.sagoma.planimetria.assets.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * [ManifestFetcher] su HTTP(S) con `HttpURLConnection`: `GET url` e il corpo come testo (UTF-8). Generico: non sa che cosa
 * ci sia dietro l'indirizzo (un file, un oggetto in un deposito, un server proprio) né dove sia il "canale" del manifest:
 * l'indirizzo lo decide chi lo costruisce. Ne [url] ne [headers] (possono contenere firme o credenziali) finiscono mai negli errori.
 *
 * Un manifest più grande di [maxBytes] non si legge ([RemoteError.TooLarge]): si guarda prima la lunghezza annunciata e,
 * se manca, si smette di leggere al superamento. Stati HTTP e problemi di rete diventano gli stessi [RemoteError] del
 * trasporto dei blob. Chiede `Accept-Encoding: identity` così la lunghezza annunciata è quella dei byte che arrivano.
 */
class HttpManifestFetcher(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 30_000,
    private val maxBytes: Int = 8 * 1024 * 1024,
) : ManifestFetcher {
    init {
        require(maxBytes > 0) { "maxBytes deve essere positivo" }
    }

    override suspend fun fetch(): ManifestFetchResult = withContext(Dispatchers.IO) {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext ManifestFetchResult.Error(RemoteError.CacheUnavailable("invalid manifest url"))
        } catch (e: RuntimeException) {
            return@withContext ManifestFetchResult.Error(RemoteError.CacheUnavailable("invalid manifest url"))
        }
        try {
            conn.connectTimeout = connectTimeoutMillis
            conn.readTimeout = readTimeoutMillis
            conn.setRequestProperty("Accept-Encoding", "identity")
            for ((k, v) in headers) conn.setRequestProperty(k, v)

            val status = try {
                conn.responseCode
            } catch (e: SocketTimeoutException) {
                return@withContext ManifestFetchResult.Error(RemoteError.Timeout)
            } catch (e: IOException) {
                return@withContext ManifestFetchResult.Error(RemoteError.Offline)
            }
            if (status != 200) {
                val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                runCatching { conn.errorStream?.close() }
                return@withContext ManifestFetchResult.Error(httpStatusToRemoteError(status, retryAfter))
            }
            val announced = conn.contentLengthLong.takeIf { it >= 0 }
            if (announced != null && announced > maxBytes) {
                return@withContext ManifestFetchResult.Error(RemoteError.TooLarge(announced, maxBytes.toLong()))
            }

            val out = ByteArrayOutputStream(announced?.toInt() ?: 8 * 1024)
            try {
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > maxBytes) {
                            return@withContext ManifestFetchResult.Error(RemoteError.TooLarge(out.size().toLong() + n, maxBytes.toLong()))
                        }
                        out.write(buffer, 0, n)
                    }
                }
            } catch (e: SocketTimeoutException) {
                return@withContext ManifestFetchResult.Error(RemoteError.Timeout)
            } catch (e: IOException) {
                return@withContext ManifestFetchResult.Error(RemoteError.Offline) // connessione caduta a metà
            }
            if (announced != null && out.size().toLong() != announced) {
                return@withContext ManifestFetchResult.Error(RemoteError.SizeMismatch(announced, out.size().toLong()))
            }
            ManifestFetchResult.Text(out.toString(Charsets.UTF_8.name()))
        } finally {
            conn.disconnect()
        }
    }
}
