package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.AssetCache
import com.sagoma.planimetria.assets.AssetPutResult
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.AssetWriteResult
import com.sagoma.planimetria.assets.AssetWriterState
import com.sagoma.planimetria.assets.matches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource

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
 *   dimensione e chiave del blob. [replaceSnapshot] vale per le operazioni successive; un download già partito
 *   con una vecchia istantanea può finire di scaricare e verificare, ma NON installa il risultato se il manifest
 *   corrente non lo aspetta più (altro SHA o percorso tolto): il suo `ensure` dà `Failed(Cancelled)`.
 * - Un blob dichiarato più grande di [FetchPolicy.maxBlobBytes] non si scarica e non si serve: `ensure` dà
 *   `Failed(TooLarge)` (e [state] lo stesso) senza nessuna richiesta, `peek` dà `null`, `availability` non è mai `Available`.
 * - Assenza dal manifest = non disponibile da qui ([AssetAvailability.Unavailable]), anche se un vecchio file è
 *   rimasto in cache. Conservare asset ritirati sarebbe una policy esplicita diversa.
 * - Dopo un fallimento transiente (rete, tempo scaduto, 429, 5xx: [RemoteError.isTransient]) a tentativi finiti il blob resta
 *   in pausa per [FetchPolicy.failureCooldownMillis]. Gli errori permanenti o di integrità non creano pausa; l'ultimo errore
 *   resta comunque in [state].
 *
 * Un download continua anche se chi lo ha chiesto smette di aspettare (lo possono attendere altri). Lo store ha un job proprio,
 * figlio di quello dello `scope` che riceve: [close] ferma solo i suoi lavori e lascia attivo lo scope di chi lo ha costruito,
 * mentre la cancellazione di quello scope ferma anche lo store. [close] ferma
 * tutto: i download in corso e chi li attende finiscono come `Failed(Cancelled)`, e ogni `ensure` successivo
 * dà subito `Failed(Cancelled)` (mai sospeso). La cache va usata solo tramite questo negozio, mai come negozio
 * nudo davanti ad esso: la verifica dell'impronta contro il manifest sta qui.
 */
class RemoteAssetStore(
    snapshot: BlobResolver,
    private val cache: AssetCache,
    private val fetcher: BlobFetcher,
    private val policy: FetchPolicy = FetchPolicy(),
    private val capabilities: Set<String> = emptySet(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = monotonicMillis,
) : AssetStore {
    // I voli girano su un job proprio, FIGLIO di quello dello scope ricevuto: chi lo possiede è lo store, che con `close()`
    // cancella solo questo (lo scope del chiamante resta attivo); se invece il chiamante cancella il suo scope, la
    // cancellazione scende fino ai voli.
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val workScope = CoroutineScope(scope.coroutineContext + job)

    /** L'istantanea del manifest usata dalle nuove operazioni. */
    @kotlin.concurrent.Volatile
    var snapshot: BlobResolver = snapshot
        private set

    /** Quanti download può fare insieme (da [FetchPolicy]): chi lo orchestra non deve chiederne di più. */
    val maxConcurrentDownloads: Int get() = policy.maxConcurrentDownloads

    /** Da ora le nuove richieste usano [newSnapshot]; i download già partiti finiscono con la loro. */
    fun replaceSnapshot(newSnapshot: BlobResolver) {
        snapshot = newSnapshot
    }

    private class Failure(val error: RemoteError, val untilMillis: Long)

    private class Flight(val entry: BlobEntry) {
        val result = CompletableDeferred<RemoteEnsureResult>()

        @kotlin.concurrent.Volatile
        var delivered = 0L
    }

    // I dati condivisi si cambiano solo con `lock` preso e si sostituiscono per intero (copia e scambio): chi legge
    // senza sospendere (state, availability) vede sempre una mappa coerente.
    private val lock = Mutex()
    private val permits = Semaphore(policy.maxConcurrentDownloads)

    // Un solo commit alla volta: il controllo "il manifest lo aspetta ancora?" e il commit sono indivisibili rispetto
    // agli altri commit, quindi un download vecchio non può installarsi dopo (o al posto di) quello attuale.
    private val installLock = Mutex()

    @kotlin.concurrent.Volatile
    private var closed = false

    @kotlin.concurrent.Volatile
    private var flights: Map<String, Flight> = emptyMap()

    @kotlin.concurrent.Volatile
    private var failures: Map<String, Failure> = emptyMap()

    // ---- AssetStore ----

    override fun availability(path: String): AssetAvailability {
        val snap = snapshot
        val entry = snap.entryForPath(path) ?: return AssetAvailability.Unavailable
        if (!tooLarge(entry) && isCached(entry)) return AssetAvailability.Available
        return if (isCompatible(snap, entry)) AssetAvailability.Remote else AssetAvailability.Incompatible
    }

    override fun peek(path: String): ByteArray? {
        val entry = snapshot.entryForPath(path) ?: return null
        if (tooLarge(entry)) return null // oltre il limite remoto non si serve (e non si legge in un ByteArray) nemmeno se è in cache
        if (!cache.info(entry.path).matches(entry.sha256, entry.size)) return null
        val bytes = cache.peek(entry.path) ?: return null
        if (bytes.size.toLong() != entry.size) return null
        // Se nel frattempo la voce è stata sostituita con un'altra impronta, quei byte non sono più quelli attesi.
        return if (cache.info(entry.path).matches(entry.sha256, entry.size)) bytes else null
    }

    override suspend fun ensure(path: String): AssetAvailability = when (val r = ensureDetailed(path)) {
        RemoteEnsureResult.Available -> AssetAvailability.Available
        RemoteEnsureResult.Incompatible -> AssetAvailability.Incompatible
        RemoteEnsureResult.NotInCatalog -> AssetAvailability.Unavailable
        is RemoteEnsureResult.Failed -> AssetAvailability.Remote // si può riprovare; il dettaglio è in state()/ensureDetailed()
    }

    // `read` è quello di AssetStore: ensure(path) == Available e poi peek(path), cioè sempre il file verificato.

    // ---- dettaglio ----

    /** Come [ensure], ma dice anche l'errore. Non resta mai sospeso: dopo [close] dà subito `Failed(Cancelled)`. */
    suspend fun ensureDetailed(path: String): RemoteEnsureResult {
        val snap = snapshot // da qui in poi si usa solo questa istantanea
        val entry = snap.entryForPath(path) ?: return RemoteEnsureResult.NotInCatalog
        if (!isCompatible(snap, entry)) return RemoteEnsureResult.Incompatible
        // Prima di ogni lavoro: un blob oltre il limite non si scarica, senza connessioni, scrittori né cache.
        if (tooLarge(entry)) return RemoteEnsureResult.Failed(RemoteError.TooLarge(entry.size, policy.maxBlobBytes))
        if (isCached(entry)) return RemoteEnsureResult.Available
        var immediate: RemoteEnsureResult? = null
        val flight = lock.withLock {
            // Un download appena finito ha già lasciato il file in cache: non se ne fa un altro.
            if (isCached(entry)) {
                immediate = RemoteEnsureResult.Available
                return@withLock null
            }
            if (closed) {
                immediate = RemoteEnsureResult.Failed(RemoteError.Cancelled)
                return@withLock null
            }
            val key = keyOf(entry)
            flights[key]?.let { return@withLock it }
            // Pausa dopo un fallimento: si ridà l'errore già noto, senza una nuova raffica di richieste.
            failures[key]?.takeIf { clock() < it.untilMillis }?.let {
                immediate = RemoteEnsureResult.Failed(it.error)
                return@withLock null
            }
            startFlight(entry)
        }
        return flight?.result?.await() ?: immediate!!
    }

    /** Stato per l'interfaccia; `null` se il manifest non prevede il file. */
    fun state(path: String): AssetState? {
        val snap = snapshot
        val entry = snap.entryForPath(path) ?: return null
        if (!tooLarge(entry) && isCached(entry)) return AssetState.Available
        if (!isCompatible(snap, entry)) return AssetState.Incompatible
        if (tooLarge(entry)) return AssetState.Failed(RemoteError.TooLarge(entry.size, policy.maxBlobBytes))
        if (closed) return AssetState.Failed(RemoteError.Cancelled) // un download interrotto da close() non esiste più
        val key = keyOf(entry)
        flights[key]?.let { f -> return AssetState.Downloading(if (entry.size <= 0) 0f else (f.delivered.toFloat() / entry.size).coerceIn(0f, 1f)) }
        failures[key]?.let { return AssetState.Failed(it.error) }
        return AssetState.Remote
    }

    /**
     * Ferma tutto e si può chiamare più volte. I download in corso e chi li attende finiscono subito come
     * `Failed(Cancelled)` (anche se la lettura di rete sta ancora bloccata: il suo risultato verrebbe scartato); i
     * `ensure` successivi danno subito `Failed(Cancelled)`; niente viene più installato in cache. Ciò che era già in cache
     * resta leggibile.
     */
    fun close() {
        closed = true
        val running = flights.values
        job.cancel() // solo il job dello store, mai lo scope di chi lo ha costruito
        for (f in running) f.result.complete(RemoteEnsureResult.Failed(RemoteError.Cancelled))
    }

    // ---- interno ----

    /** Il download è identificato dal percorso E dall'impronta attesa: la stessa destinazione con contenuto atteso diverso è un altro download. */
    private fun keyOf(entry: BlobEntry) = entry.path + "\u0000" + entry.sha256

    private fun tooLarge(entry: BlobEntry) = entry.size > policy.maxBlobBytes

    private fun isCached(entry: BlobEntry) =
        cache.info(entry.path).matches(entry.sha256, entry.size) && cache.availability(entry.path) == AssetAvailability.Available

    /** Il manifest attuale aspetta ancora proprio questo file (stesso percorso, impronta e dimensione). */
    private fun isCurrent(entry: BlobEntry): Boolean {
        val cur = snapshot.entryForPath(entry.path) ?: return false
        return cur.sha256 == entry.sha256 && cur.size == entry.size
    }

    private fun isCompatible(snap: BlobResolver, entry: BlobEntry) =
        snap.asset(entry.assetId)?.requires?.all { it in capabilities } ?: true

    /**
     * Con `lock` preso. Parte con `ATOMIC`: anche se lo scope è già stato cancellato (close concorrente) il corpo gira
     * almeno fino al primo controllo di cancellazione e completa sempre il risultato, quindi nessuno resta in attesa.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun startFlight(entry: BlobEntry): Flight {
        val key = keyOf(entry)
        val flight = Flight(entry)
        flights = flights + (key to flight)
        failures = failures - key
        workScope.launch(start = CoroutineStart.ATOMIC) {
            val result = try {
                currentCoroutineContext().ensureActive()
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
                // L'ultimo errore si conserva sempre (lo riporta state()); la pausa vale solo per quelli transienti
                // (rete, tempo scaduto, 429, 5xx). Gli altri (404, 403, integrità, cache, interruzione) hanno scadenza
                // già passata: un nuovo ensure può rifare subito la verifica, senza ritentativi automatici.
                if (result is RemoteEnsureResult.Failed && result.error != RemoteError.Cancelled) {
                    val now = clock()
                    val until = if (result.error.isTransient) saturatingAdd(now, policy.failureCooldownMillis) else now
                    failures = failures + (key to Failure(result.error, until))
                }
                flights = flights - key
            }
        }
        flight.result.complete(result)
    }

    private fun saturatingAdd(a: Long, b: Long): Long = if (b > Long.MAX_VALUE - a) Long.MAX_VALUE else a + b

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
                is FetchResult.Success -> installLock.withLock {
                    // Scaricato e (alla commit) verificato, ma si installa solo se è ancora ciò che il manifest attuale aspetta.
                    if (closed || !isCurrent(entry)) {
                        Attempt(RemoteError.Cancelled)
                    } else {
                        val put = writer.commit()
                        if (put == AssetPutResult.Stored) Attempt(null) else Attempt(mapPutFailure(put, entry, writer.bytesWritten))
                    }
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

/** Orologio monotono in millisecondi (comune a tutte le piattaforme); i test ne passano uno proprio. */
internal val monotonicMillis: () -> Long = run {
    val origin = TimeSource.Monotonic.markNow()
    ({ origin.elapsedNow().inWholeMilliseconds })
}
