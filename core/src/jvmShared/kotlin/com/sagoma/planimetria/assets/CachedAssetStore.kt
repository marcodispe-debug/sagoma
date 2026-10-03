package com.sagoma.planimetria.assets

/**
 * Negozio d'asset dell'app con una cache persistente davanti a una sorgente locale:
 *
 * ```
 * CompositeAssetStore
 *   ├─ cache            ← per prima (se ha il file, la sorgente non si legge)
 *   └─ sorgente locale  ← se la cache non ce l'ha; ciò che dà si mette anche in cache
 * ```
 *
 * L'ordine e il passaggio al negozio successivo quando una lettura fallisce sono quelli di [CompositeAssetStore]
 * (non c'è un secondo sistema di ripiego). Qui c'è solo il modo di riempire la cache e, volendo, di non fidarsi
 * di una voce che non ha l'impronta attesa.
 *
 * - `expectedSha256`: se dà l'impronta attesa di un percorso (in futuro il catalogo) e la voce in cache ne ha
 *   un'altra, la cache non risponde per quel file: si legge dalla sorgente e la cache si aggiorna. Se dà `null` la
 *   cache vale com'è.
 * - `execute`: dove girano le scritture in cache. Chi chiama passa un esecutore di sfondo, così il thread che
 *   legge (interfaccia, rendering) non paga impronta, scrittura su disco e indice; di default si scrive subito.
 */
fun cachedAssetStore(
    source: AssetStore,
    cache: AssetCache,
    expectedSha256: ((String) -> String?)? = null,
    execute: (() -> Unit) -> Unit = { it() },
    maxPendingBytes: Long = DEFAULT_MAX_PENDING_BYTES,
): AssetStore {
    val view: AssetStore = if (expectedSha256 == null) cache else HashCheckedCacheView(cache, expectedSha256)
    return CompositeAssetStore(view, CachePopulatingAssetStore(source, cache, execute, maxPendingBytes))
}

/** Quanti byte, al massimo, possono aspettare di essere scritti in cache: oltre, quel file non si mette in cache. */
const val DEFAULT_MAX_PENDING_BYTES: Long = 32L * 1024 * 1024

/**
 * Legge dalla sorgente e, quando riesce, ne mette una copia nella cache. La cache è un'ottimizzazione, mai una
 * condizione: se non si riesce a scrivere (disco pieno, cartella non scrivibile, errore qualsiasi) il chiamante
 * ottiene comunque i suoi byte, e dopo alcuni errori di fila non si prova più per il resto della sessione.
 *
 * I byte restituiti sono gli stessi che finiscono in cache, senza copie: chi li riceve non deve modificarli (come
 * già oggi). Va usato dietro un [CompositeAssetStore] che prova prima la cache.
 */
class CachePopulatingAssetStore(
    private val source: AssetStore,
    private val cache: AssetCache,
    private val execute: (() -> Unit) -> Unit = { it() },
    private val maxPendingBytes: Long = DEFAULT_MAX_PENDING_BYTES,
    private val maxConsecutiveFailures: Int = 5,
) : AssetStore {
    private val lock = Any()
    private val inFlight = HashSet<String>()
    private var pendingBytes = 0L
    private var failures = 0

    override fun availability(path: String): AssetAvailability = source.availability(path)

    override fun peek(path: String): ByteArray? {
        val bytes = source.peek(path) ?: return null
        populate(path, bytes)
        return bytes
    }

    override suspend fun ensure(path: String): AssetAvailability = source.ensure(path)

    private fun populate(path: String, bytes: ByteArray) {
        synchronized(lock) {
            if (failures >= maxConsecutiveFailures || path in inFlight || pendingBytes + bytes.size > maxPendingBytes) return
            inFlight += path
            pendingBytes += bytes.size
        }
        try {
            execute { store(path, bytes) }
        } catch (e: RuntimeException) {
            // L'esecutore non accetta il lavoro (per esempio è stato chiuso): non si mette in cache e basta.
            finish(path, bytes.size, ok = true)
        }
    }

    private fun store(path: String, bytes: ByteArray) {
        val ok = try {
            cache.put(path, bytes) != AssetPutResult.IoError
        } catch (e: Exception) {
            false
        }
        finish(path, bytes.size, ok)
    }

    private fun finish(path: String, size: Int, ok: Boolean) {
        synchronized(lock) {
            inFlight -= path
            pendingBytes -= size
            failures = if (ok) 0 else failures + 1
        }
    }
}
