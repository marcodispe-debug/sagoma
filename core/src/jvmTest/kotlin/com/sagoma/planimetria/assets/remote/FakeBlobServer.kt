package com.sagoma.planimetria.assets.remote

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Contenuto di un blob di prova: si può generare senza tenerlo in memoria (file grandi). */
interface Content {
    val size: Long

    /** Scrive i byte da `from` in poi a blocchi di `chunk`; `afterChunk` dopo ogni blocco (per i ritardi). */
    fun writeTo(out: OutputStream, from: Long, chunk: Int, limit: Long = size - from, afterChunk: () -> Unit = {})

    fun sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        writeTo(object : OutputStream() {
            override fun write(b: Int) = md.update(b.toByte())
            override fun write(b: ByteArray, off: Int, len: Int) = md.update(b, off, len)
        }, 0, 64 * 1024)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

class BytesContent(val bytes: ByteArray) : Content {
    override val size: Long get() = bytes.size.toLong()
    override fun writeTo(out: OutputStream, from: Long, chunk: Int, limit: Long, afterChunk: () -> Unit) {
        var o = from.toInt()
        val end = (from + limit).toInt()
        while (o < end) {
            val n = minOf(chunk, end - o)
            out.write(bytes, o, n)
            o += n
            afterChunk()
        }
    }
}

/** Byte `i` = `(i * 31 + seed) mod 251`: deterministico, di qualunque dimensione, mai in memoria per intero. */
class SyntheticContent(override val size: Long, private val seed: Int = 0) : Content {
    override fun writeTo(out: OutputStream, from: Long, chunk: Int, limit: Long, afterChunk: () -> Unit) {
        val buf = ByteArray(chunk)
        var pos = from
        val end = from + limit
        while (pos < end) {
            val n = minOf(chunk.toLong(), end - pos).toInt()
            for (k in 0 until n) buf[k] = (((pos + k) * 31 + seed) % 251).toByte()
            out.write(buf, 0, n)
            pos += n
            afterChunk()
        }
    }
}

sealed interface Behavior {
    /** 200 con tutto il contenuto, a blocchi, con ritardi opzionali. */
    class Ok(val content: Content, val chunkSize: Int = 16 * 1024, val delayPerChunkMs: Long = 0, val startDelayMs: Long = 0) : Behavior

    /** Risponde con questo stato e nessun corpo. */
    class Status(val code: Int, val retryAfter: String? = null) : Behavior

    /** Annuncia l'intero contenuto ma ne manda solo `sendBytes` e chiude. */
    class Cut(val content: Content, val sendBytes: Long) : Behavior

    /** Tiene la richiesta ferma `ms` senza rispondere. */
    class Hang(val ms: Long) : Behavior
}

/** Server HTTP locale (solo loopback, porta libera, `HttpServer` del JDK) per i test del fetcher e del negozio remoto. */
class FakeBlobServer : AutoCloseable {
    private val http = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val executor = Executors.newCachedThreadPool()
    private val routes = ConcurrentHashMap<String, (Int) -> Behavior>()
    private val hitCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val inFlight = AtomicInteger()

    val maxInFlight = AtomicInteger()
    val requestHeaders = CopyOnWriteArrayList<Map<String, List<String>>>()
    val baseUrl: String get() = "http://127.0.0.1:${http.address.port}"
    val port: Int get() = http.address.port

    init {
        http.createContext("/") { ex -> handle(ex) }
        http.executor = executor
        http.start()
    }

    fun route(key: String, behavior: (Int) -> Behavior) {
        routes[key] = behavior
    }

    fun route(key: String, behavior: Behavior) = route(key) { behavior }

    fun content(key: String, content: Content) = route(key, Behavior.Ok(content))

    /** Il n-esimo tentativo usa il n-esimo comportamento; gli altri l'ultimo. */
    fun sequence(key: String, vararg behaviors: Behavior) = route(key) { i -> behaviors[minOf(i, behaviors.size - 1)] }

    fun hits(key: String): Int = hitCounts[key]?.get() ?: 0
    fun totalHits(): Int = hitCounts.values.sumOf { it.get() }

    private fun handle(ex: HttpExchange) {
        val key = ex.requestURI.path.removePrefix("/")
        val index = hitCounts.getOrPut(key) { AtomicInteger() }.getAndIncrement()
        requestHeaders += ex.requestHeaders.toMap()
        val now = inFlight.incrementAndGet()
        maxInFlight.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        try {
            when (val b = routes[key]?.invoke(index) ?: Behavior.Status(404)) {
                is Behavior.Status -> {
                    b.retryAfter?.let { ex.responseHeaders.add("Retry-After", it) }
                    ex.sendResponseHeaders(b.code, -1)
                }
                is Behavior.Hang -> Thread.sleep(b.ms)
                is Behavior.Cut -> {
                    ex.sendResponseHeaders(200, b.content.size)
                    b.content.writeTo(ex.responseBody, 0, 8 * 1024, limit = b.sendBytes)
                    ex.responseBody.flush()
                }
                is Behavior.Ok -> {
                    if (b.startDelayMs > 0) Thread.sleep(b.startDelayMs)
                    val from = ex.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toLongOrNull() ?: 0L
                    val length = b.content.size - from
                    ex.sendResponseHeaders(if (from > 0) 206 else 200, if (length == 0L) -1 else length)
                    if (length > 0) b.content.writeTo(ex.responseBody, from, b.chunkSize) { if (b.delayPerChunkMs > 0) { ex.responseBody.flush(); Thread.sleep(b.delayPerChunkMs) } }
                }
            }
        } catch (e: IOException) {
            // il client ha chiuso o la risposta è stata troncata di proposito
        } catch (e: InterruptedException) {
            // chiusura del server
        } finally {
            inFlight.decrementAndGet()
            try {
                ex.close()
            } catch (e: IOException) {
                // troncata di proposito: "insufficient bytes written"
            }
        }
    }

    override fun close() {
        http.stop(0)
        executor.shutdownNow()
    }
}
