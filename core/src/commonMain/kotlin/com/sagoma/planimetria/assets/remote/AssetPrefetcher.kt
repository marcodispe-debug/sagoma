package com.sagoma.planimetria.assets.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Quanto serve un asset a chi lo chiede; in ordine decrescente di importanza. */
enum class AssetPriority {
    /** Serve subito alla scena (modelli, materiali, luci della vista aperta; i cataloghi all'avvio). Prima richiesto, prima servito. */
    Scene,

    /** È sullo schermo adesso (miniature del catalogo, viste dall'alto). Il più recente per primo; decade se non lo si richiede più. */
    Visible,

    /** Servirà a breve (righe vicine, modelli del progetto aperto). */
    Soon,

    /** Non urgente. */
    Background,
}

/**
 * Decide in che ordine e quanti asset remoti scaricare. Non scarica niente da sé: per ogni richiesta chiama
 * [RemoteAssetStore.ensureDetailed], quindi il single-flight, i tentativi, la pausa dopo un errore e la verifica dei
 * file restano tutti del negozio. Qui ci sono solo: coda con priorità, deduplicazione, limite di lavori contemporanei,
 * scadenza delle richieste non ancora partite e la notifica [arrived].
 *
 * - [request] non sospende e si può chiamare da qualunque thread (anche a ogni disegno): è un messaggio a un attore, l'unico
 *   che tocca la coda, senza blocchi.
 * - Per percorso c'è al più una richiesta: se è già in coda una nuova con priorità più alta la promuove (a pari priorità
 *   ne rinnova l'ora); se il lavoro è già in corso la richiesta si ignora; se il file è già disponibile (o il manifest non
 *   lo prevede, o non è compatibile) non si fa niente.
 * - Si sceglie `Scene` > `Visible` > `Soon` > `Background`; tra i `Visible` il più recente per primo, negli altri l'ordine
 *   di arrivo. Un lavoro in corso non si interrompe mai; si possono scartare solo quelli non ancora partiti: un `Visible`
 *   non richiesto da più di [visibleTtlMillis] decade, e `Soon`+`Background` in coda sono al più [maxQueuedLowPriority]
 *   (si butta il più vecchio, prima i `Background`).
 * - I lavori contemporanei sono al più [maxParallel], e mai più di [RemoteAssetStore.maxConcurrentDownloads].
 * - [arrived] emette il percorso quando la sua richiesta finisce con il file disponibile. Errori e stato restano quelli
 *   del negozio ([state]); un `Cancelled` dovuto al cambio di manifest durante il lavoro non è un errore del prefetcher: la
 *   richiesta torna in coda con la sua priorità e riparte con il manifest nuovo.
 * - [close] ferma l'attore e i lavori in corso (non i voli del negozio, che finiscono da soli) e ignora le richieste successive.
 */
class AssetPrefetcher(
    private val remote: RemoteAssetStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    maxParallel: Int = 3,
    private val visibleTtlMillis: Long = 3_000,
    private val maxQueuedLowPriority: Int = 200,
    private val clock: () -> Long = monotonicMillis,
) {
    init {
        require(maxParallel >= 1) { "maxParallel deve essere almeno 1" }
        require(visibleTtlMillis >= 0) { "visibleTtlMillis negativo" }
        require(maxQueuedLowPriority >= 0) { "maxQueuedLowPriority negativo" }
    }

    private sealed interface Msg
    private class Request(val path: String, val priority: AssetPriority, val at: Long) : Msg
    private class Done(val path: String, val result: RemoteEnsureResult) : Msg
    private data object ManifestChanged : Msg

    private class Queued(val path: String, var priority: AssetPriority, var requestedAt: Long, var seq: Long)
    private class Running(val priority: AssetPriority, val requestedAt: Long, val snapshot: BlobResolver)

    private val limit = minOf(maxParallel, remote.maxConcurrentDownloads).coerceAtLeast(1)
    private val inbox = Channel<Msg>(Channel.UNLIMITED)
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val workers = CoroutineScope(scope.coroutineContext + job)
    private val arrivedFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Percorsi diventati disponibili, una volta per richiesta conclusa con successo. */
    val arrived: SharedFlow<String> = arrivedFlow.asSharedFlow()

    /** `false` dopo [close] (e a coda svuotata dall'attore terminato). */
    val isActive: Boolean get() = job.isActive

    // Stato dell'attore: lo tocca solo la sua coroutine.
    private val queue = LinkedHashMap<String, Queued>()
    private val running = HashMap<String, Running>()
    private var seq = 0L

    /** Solo per i test: quante `ensureDetailed` il prefetcher ha avviato e quanti esiti ha elaborato (si scrivono solo dall'attore). */
    @kotlin.concurrent.Volatile
    internal var startedCount = 0

    @kotlin.concurrent.Volatile
    internal var doneCount = 0

    init {
        workers.launch {
            for (m in inbox) {
                when (m) {
                    is Request -> onRequest(m)
                    is Done -> onDone(m)
                    ManifestChanged -> dropUnneeded()
                }
                pump()
            }
        }
    }

    /** Chiede che `path` sia disponibile in locale; non aspetta e non fallisce mai. */
    fun request(path: String, priority: AssetPriority) {
        inbox.trySend(Request(path, priority, clock()))
    }

    /** Lo stato dell'asset secondo il negozio (`null` se il manifest non lo prevede). */
    fun state(path: String): AssetState? = remote.state(path)

    /** Il manifest del negozio è cambiato: si tolgono dalla coda le richieste che non hanno più senso. */
    fun onManifestChanged() {
        inbox.trySend(ManifestChanged)
    }

    fun close() {
        inbox.close()
        job.cancel()
    }

    // ---- attore ----

    private fun onRequest(m: Request) {
        if (m.path in running) return
        if (!worthFetching(m.path)) {
            queue.remove(m.path)
            return
        }
        val q = queue[m.path]
        if (q == null) {
            queue[m.path] = Queued(m.path, m.priority, m.at, ++seq)
            trimLowPriority()
        } else if (m.priority.ordinal < q.priority.ordinal) {
            q.priority = m.priority
            q.requestedAt = m.at
            q.seq = ++seq
        } else if (m.priority == q.priority) {
            q.requestedAt = m.at
        }
    }

    private suspend fun onDone(m: Done) {
        doneCount++
        val run = running.remove(m.path) ?: return
        when (val r = m.result) {
            RemoteEnsureResult.Available -> arrivedFlow.emit(m.path)
            is RemoteEnsureResult.Failed ->
                // Annullato perché il manifest è cambiato mentre scaricava: si ripete con il manifest nuovo.
                if (r.error == RemoteError.Cancelled && remote.snapshot !== run.snapshot) {
                    queue[m.path] = Queued(m.path, run.priority, run.requestedAt, ++seq)
                }
            RemoteEnsureResult.Incompatible, RemoteEnsureResult.NotInCatalog -> {}
        }
    }

    private fun dropUnneeded() {
        val it = queue.entries.iterator()
        while (it.hasNext()) if (!worthFetching(it.next().key)) it.remove()
    }

    /** C'è qualcosa da scaricare: il manifest prevede il file, è compatibile e non è già in locale. */
    private fun worthFetching(path: String): Boolean = when (remote.state(path)) {
        null, AssetState.Available, AssetState.Incompatible -> false
        else -> true
    }

    private fun trimLowPriority() {
        while (true) {
            val low = queue.values.filter { it.priority >= AssetPriority.Soon }
            if (low.size <= maxQueuedLowPriority) return
            val victim = low.filter { it.priority == AssetPriority.Background }.minByOrNull { it.seq }
                ?: low.minByOrNull { it.seq } ?: return
            queue.remove(victim.path)
        }
    }

    private fun pump() {
        while (running.size < limit) {
            val next = pick() ?: return
            queue.remove(next.path)
            start(next)
        }
    }

    private fun pick(): Queued? {
        val now = clock()
        val expired = queue.values.filter { it.priority == AssetPriority.Visible && now - it.requestedAt > visibleTtlMillis }
        for (q in expired) queue.remove(q.path)
        var best: Queued? = null
        for (q in queue.values) if (best == null || before(q, best)) best = q
        return best
    }

    /** `a` va servito prima di `b`. */
    private fun before(a: Queued, b: Queued): Boolean = when {
        a.priority != b.priority -> a.priority.ordinal < b.priority.ordinal
        a.priority == AssetPriority.Visible && a.requestedAt != b.requestedAt -> a.requestedAt > b.requestedAt
        else -> a.seq < b.seq
    }

    private fun start(q: Queued) {
        startedCount++
        running[q.path] = Running(q.priority, q.requestedAt, remote.snapshot)
        workers.launch {
            val result = try {
                remote.ensureDetailed(q.path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                RemoteEnsureResult.Failed(RemoteError.CacheUnavailable("unexpected ${e::class.simpleName}"))
            }
            inbox.trySend(Done(q.path, result))
        }
    }
}
