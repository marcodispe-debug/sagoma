package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.AssetCache
import com.sagoma.planimetria.assets.AssetPutResult
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.AssetWriteResult
import com.sagoma.planimetria.assets.AssetWriterState
import com.sagoma.planimetria.assets.CachedAssetInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Esito dettagliato di [RemoteAssetStore.ensureDetailed]. */
sealed class RemoteEnsureResult {
    /** Il file è in cache, verificato. */
    data object Available : RemoteEnsureResult()

    /** Non si è riusciti a ottenerlo; [error] dice perché. */
    data class Failed(val error: RemoteError) : RemoteEnsureResult()

    /** Esiste ma questa app non ha le capacità richieste (`requires`). */
    data object Incompatible : RemoteEnsureResult()

    /** Il manifest non lo prevede. */
    data object NotInCatalog : RemoteEnsureResult()
}

/**
 * Negozio di asset il cui contenuto sta in un deposito remoto, descritto da un manifest:
 *
 * ```
 * RemoteAssetStore → BlobResolver (istantanea del manifest) → BlobFetcher → AssetWriter → AssetCache
 * ```
 *
 * Non sa niente di HTTP né del fornitore: il trasporto è un [BlobFetcher]. I byte vanno dal fetcher allo scrittore
 * della cache a blocchi (`AssetCache.openWrite`), con impronta SHA-256 incrementale e commit atomico: un blob diventa
 * disponibile solo a dimensione e impronta verificate, e il file intero non sta mai in memoria.
 *
 * - [peek] e [availability]: solo cache, mai rete. Un file in cache conta solo se ha l'impronta e la dimensione
 *   che il manifest attende; altrimenti è come se non ci fosse.
 * - [ensure]: scarica se serve, con un solo download per lo stesso blob anche se chiesto da più parti insieme
 *   (la chiave è percorso + SHA-256 atteso), ritentando secondo [FetchPolicy].
 * - Istantanea: ogni operazione parte con l'istantanea corrente e ne usa fino in fondo percorso, impronta,
 *   dimensione e chiave del blob. [replaceSnapshot] vale per le operazioni successive, mai per quelle in corso.
 *
 * Un download continua anche se chi lo ha chiesto smette di aspettare (lo possono attendere altri); si ferma con [close].
 */
class RemoteAssetStore(
    snapshot: BlobResolver,
    private val cache: AssetCache,
    private val fetcher: BlobFetcher,
    private val policy: FetchPolicy = FetchPolicy(),
    private val capabilities: Set<String> = emptySet(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AssetStore {
    /** L'istantanea del manifest usata dalle nuove operazioni. */
    @kotlin.concurrent.Volatile
    var snapshot: BlobResolver = snapshot
        private set

    /** Da ora le nuove richieste usano [newSnapshot]; i download già partiti finiscono con la loro. */
    fun replaceSnapshot(newSnapshot: BlobResolver) {
        snapshot = newSnapshot
    }

    private class Flight(val entry: BlobEntry) {
        val result = CompletableDeferred<RemoteEnsureResult>()

        @kotlin.concurrent.Volatile
        var delivered = 0L
    }

    // I dati condivisi si cambiano solo con `lock` preso e si sostituiscono per intero (copia e scambio): chi legge
    // senza sospendere (state, availability) vede sempre una mappa coerente.
    private val lock = Mutex()
    private val permits = Semaphore(policy.maxConcurrentDownloads)

    @kotlin.concurrent.Volatile
    private var flights: Map<String, Flight> = emptyMap()

    @kotlin.concurrent.Volatile
    private var failures: Map<String, RemoteError> = emptyMap()

    // ---- AssetStore ----

    override fun availability(path: String): AssetAvailability {
        val snap = snapshot
        val entry = snap.entryForPath(path) ?: return AssetAvailability.Unavailable
        if (isCached(entry)) return AssetAvailability.Available
        return if (isCompatible(snap, entry)) AssetAvailability.Remote else AssetAvailability.Incompatible
    }

    override fun peek(path: String): ByteArray? {
        val entry = snapshot.entryForPath(path) ?: return null
        if (!matches(cache.info(entry.path), entry)) return null
        val bytes = cache.peek(entry.path) ?: return null
        if (bytes.size.toLong() != entry.size) return null
        // Se nel frattempo la voce è stata sostituita con un'altra impronta, quei byte non sono più quelli attesi.
        return if (matches(cache.info(entry.path), entry)) bytes else null
    }

    override suspend fun ensure(path: String): AssetAvailability = when (val r = ensureDetailed(path)) {
        RemoteEnsureResult.Available -> AssetAvailability.Available
        RemoteEnsureResult.Incompatible -> AssetAvailability.Incompatible
        RemoteEnsureResult.NotInCatalog -> AssetAvailability.Unavailable
        is RemoteEnsureResult.Failed -> AssetAvailability.Remote // si può riprovare; il dettaglio è in state()/ensureDetailed()
    }

    // `read` è quello di AssetStore: ensure(path) == Available e poi peek(path), cioè sempre il file verificato.

    // ---- dettaglio ----

    /** Come [ensure], ma dice anche l'errore. */
    suspend fun ensureDetailed(path: String): RemoteEnsureResult {
        val snap = snapshot // da qui in poi si usa solo questa istantanea
        val entry = snap.entryForPath(path) ?: return RemoteEnsureResult.NotInCatalog
        if (!isCompatible(snap, entry)) return RemoteEnsureResult.Incompatible
        if (isCached(entry)) return RemoteEnsureResult.Available
        val flight = lock.withLock {
            // Un download appena finito ha già lasciato il file in cache: non se ne fa un altro.
            if (isCached(entry)) return@withLock null
            flights[keyOf(entry)] ?: startFlight(entry)
        } ?: return RemoteEnsureResult.Available
        return flight.result.await()
    }

    /** Stato per l'interfaccia; `null` se il manifest non prevede il file. */
    fun state(path: String): AssetState? {
        val snap = snapshot
        val entry = snap.entryForPath(path) ?: return null
        if (isCached(entry)) return AssetState.Available
        if (!isCompatible(snap, entry)) return AssetState.Incompatible
        val key = keyOf(entry)
        flights[key]?.let { f -> return AssetState.Downloading(if (entry.size <= 0) 0f else (f.delivered.toFloat() / entry.size).coerceIn(0f, 1f)) }
        failures[key]?.let { return AssetState.Failed(it) }
        return AssetState.Remote
    }

    /** Ferma i download in corso (solo se lo scope è quello creato qui o di chi lo possiede). */
    fun close() {
        scope.cancel()
    }

    // ---- interno ----

    /** Il download è identificato dal percorso E dall'impronta attesa: la stessa destinazione con contenuto atteso diverso è un altro download. */
    private fun keyOf(entry: BlobEntry) = entry.path + "\u0000" + entry.sha256

    private fun matches(info: CachedAssetInfo?, entry: BlobEntry) =
        info != null && info.size == entry.size && info.sha256.equals(entry.sha256, ignoreCase = true)

    private fun isCached(entry: BlobEntry) =
        matches(cache.info(entry.path), entry) && cache.availability(entry.path) == AssetAvailability.Available

    private fun isCompatible(snap: BlobResolver, entry: BlobEntry) =
        snap.asset(entry.assetId)?.requires?.all { it in capabilities } ?: true

    /** Con `lock` preso. */
    private fun startFlight(entry: BlobEntry): Flight {
        val key = keyOf(entry)
        val flight = Flight(entry)
        flights = flights + (key to flight)
        failures = failures - key
        scope.launch {
            val result = try {
                download(flight)
            } catch (e: CancellationException) {
                finish(key, RemoteEnsureResult.Failed(RemoteError.Cancelled), flight)
                throw e
            } catch (e: Throwable) {
                RemoteEnsureResult.Failed(RemoteError.CacheUnavailable("unexpected ${e::class.simpleName}"))
            }
            finish(key, result, flight)
        }
        return flight
    }

    private suspend fun finish(key: String, result: RemoteEnsureResult, flight: Flight) {
        // Prima si registra l'esito e si toglie il download dall'elenco, poi si svegliano gli altri.
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            lock.withLock {
                if (result is RemoteEnsureResult.Failed) failures = failures + (key to result.error)
                flights = flights - key
            }
        }
        flight.result.complete(result)
    }

    private class Attempt(val error: RemoteError?, val retryable: Boolean = false)

    private suspend fun download(flight: Flight): RemoteEnsureResult {
        var attempt = 1
        while (true) {
            flight.delivered = 0
            val outcome = permits.withPermit { attemptOnce(flight) }
            val error = outcome.error ?: return RemoteEnsureResult.Available
            if (!outcome.retryable || attempt >= policy.maxAttempts) return RemoteEnsureResult.Failed(error)
            delay(policy.backoffMillis(attempt, error))
            attempt++
        }
    }

    private suspend fun attemptOnce(flight: Flight): Attempt {
        val entry = flight.entry
        val writer = cache.openWrite(entry.path, entry.size, entry.sha256)
        if (writer.state != AssetWriterState.Open) {
            // La cache non può accogliere il file: inutile scaricarlo.
            return Attempt(mapPutFailure(writer.commit(), entry, 0))
        }
        var rejected: AssetWriteResult? = null
        var rejectedLength = 0
        try {
            val sink = BlobSink { chunk, offset, length ->
                val r = writer.write(chunk, offset, length)
                if (r == AssetWriteResult.Ok) {
                    flight.delivered += length
                    true
                } else {
                    rejected = r
                    rejectedLength = length
                    false
                }
            }
            return when (val res = fetcher.fetch(BlobRequest(entry.blobKey, 0, entry.size), sink)) {
                is FetchResult.Success -> {
                    val put = writer.commit()
                    if (put == AssetPutResult.Stored) Attempt(null) else Attempt(mapPutFailure(put, entry, writer.bytesWritten))
                }
                is FetchResult.Http -> classify(httpStatusToRemoteError(res.status, res.retryAfterSeconds))
                is FetchResult.ContentLengthMismatch -> Attempt(RemoteError.ContentLengthMismatch(res.expected, res.announced))
                FetchResult.Timeout -> classify(RemoteError.Timeout)
                is FetchResult.ConnectionFailed -> classify(RemoteError.Offline)
                is FetchResult.Truncated -> Attempt(RemoteError.SizeMismatch(entry.size, res.bytesDelivered), policy.retryTruncated)
                is FetchResult.SinkRejected -> when (rejected) {
                    AssetWriteResult.TooMuchData -> Attempt(RemoteError.SizeMismatch(entry.size, writer.bytesWritten + rejectedLength))
                    else -> Attempt(RemoteError.CacheUnavailable("write failed"))
                }
            }
        } finally {
            writer.abort() // dopo un commit riuscito non fa niente; altrimenti scarta il temporaneo
        }
    }

    private fun classify(error: RemoteError) = Attempt(error, policy.isRetryable(error))

    private fun mapPutFailure(put: AssetPutResult, entry: BlobEntry, written: Long): RemoteError = when (put) {
        AssetPutResult.HashMismatch -> RemoteError.HashMismatch(entry.sha256)
        AssetPutResult.SizeMismatch -> RemoteError.SizeMismatch(entry.size, written)
        AssetPutResult.TooLarge -> RemoteError.TooLarge(entry.size, cache.maxBytes)
        AssetPutResult.Aborted -> RemoteError.Cancelled
        AssetPutResult.InvalidPath, AssetPutResult.IoError, AssetPutResult.Stored -> RemoteError.CacheUnavailable(put.name)
    }
}
