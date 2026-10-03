package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetCache
import com.sagoma.planimetria.assets.AssetPutResult
import com.sagoma.planimetria.assets.AssetWriteResult
import com.sagoma.planimetria.assets.AssetWriter
import com.sagoma.planimetria.assets.DiskAssetCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

fun sha256Of(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

/** Un blob di prova: percorso logico, contenuto e impronta (chiave del blob = impronta con la disposizione `Flat`). */
class TestBlob(val path: String, val content: Content, val requires: List<String> = emptyList()) {
    val sha: String = content.sha256()
    val size: Long get() = content.size
    val key: String get() = sha
}

fun resolverOf(vararg blobs: TestBlob, catalogVersion: Long = 1): BlobResolver {
    val assets = blobs.mapIndexed { i, b ->
        ManifestAsset(
            "asset$i", "furniture", AssetStatus.Active, null, "standard", b.requires, AssetAccess.Public,
            listOf(ManifestFile("model", b.path, b.sha, b.size)),
        )
    }
    return BlobResolver(Manifest(1, "test", catalogVersion, "r$catalogVersion", null, 0, BlobLayout.Flat, assets, emptyMap(), emptyList(), null))
}

/** Cache su disco che conta e osserva ciò che passa dalla scrittura a blocchi. */
class SpyCache(val disk: DiskAssetCache) : AssetCache by disk {
    val puts = AtomicInteger()
    val opened = AtomicInteger()
    val writeCalls = AtomicInteger()
    val maxChunk = AtomicInteger()

    override fun put(path: String, bytes: ByteArray, sha256: String?): AssetPutResult {
        puts.incrementAndGet()
        return disk.put(path, bytes, sha256)
    }

    override fun openWrite(path: String, expectedSize: Long, expectedSha256: String): AssetWriter {
        opened.incrementAndGet()
        val w = disk.openWrite(path, expectedSize, expectedSha256)
        return object : AssetWriter by w {
            override fun write(chunk: ByteArray, offset: Int, length: Int): AssetWriteResult {
                writeCalls.incrementAndGet()
                maxChunk.accumulateAndGet(length) { a, b -> maxOf(a, b) }
                return w.write(chunk, offset, length)
            }
        }
    }
}

fun tmpCount(root: File): Int = File(root, "tmp").listFiles()?.size ?: 0

fun TestBlob.bytes(): ByteArray = (content as BytesContent).bytes

/** Fetcher in memoria: ogni blob aspetta il suo "via libera". Con `cancellable = false` ignora la cancellazione mentre aspetta. */
class GateFetcher(private val cancellable: Boolean = true) : BlobFetcher {
    val requests = CopyOnWriteArrayList<String>()
    val contents = ConcurrentHashMap<String, ByteArray>()
    private val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    fun gate(key: String): CompletableDeferred<Unit> = gates.getOrPut(key) { CompletableDeferred() }
    fun add(b: TestBlob): GateFetcher = also { contents[b.key] = b.bytes() }

    override suspend fun fetch(request: BlobRequest, sink: BlobSink): FetchResult {
        requests += request.blobKey
        val g = gate(request.blobKey)
        if (cancellable) g.await() else withContext(NonCancellable) { g.await() }
        val b = contents[request.blobKey] ?: return FetchResult.Http(404)
        sink.accept(b, 0, b.size)
        return FetchResult.Success(b.size.toLong(), b.size.toLong())
    }
}

/** Fetcher in memoria che conta le chiamate; risponde con `status` (se non `null`) oppure con il contenuto. */
class CountingFetcher : BlobFetcher {
    val calls = AtomicInteger()
    val contents = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    var status: Int? = null

    fun add(b: TestBlob): CountingFetcher = also { contents[b.key] = b.bytes() }

    override suspend fun fetch(request: BlobRequest, sink: BlobSink): FetchResult {
        calls.incrementAndGet()
        status?.let { return FetchResult.Http(it) }
        val b = contents[request.blobKey] ?: return FetchResult.Http(404)
        sink.accept(b, 0, b.size)
        return FetchResult.Success(b.size.toLong(), b.size.toLong())
    }
}
