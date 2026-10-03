package com.sagoma.planimetria.assets.remote

/**
 * Trasporto di un blob e basta: dato un blob ne consegna i byte, a blocchi, a un [BlobSink]. Non conosce cache,
 * manifest, tentativi, scritture concorrenti né quale blob scegliere: chi lo usa ([RemoteAssetStore]) decide tutto
 * questo. Un solo tentativo per chiamata: i ritentativi sono della policy, non del trasporto.
 *
 * Un'implementazione non deve mai mettere URL firmati, credenziali o intestazioni nei risultati.
 */
interface BlobFetcher {
    suspend fun fetch(request: BlobRequest, sink: BlobSink): FetchResult
}

/**
 * Cosa scaricare. [blobKey] è la chiave del blob nel deposito (vedi [BlobEntry.blobKey]), sempre una chiave
 * valida secondo [BlobLayout] (altrimenti la richiesta non si crea): sta al fetcher tradurla in un indirizzo,
 * così l'endpoint resta astratto e la chiave non può uscire dal suo spazio. [offset] è dove riprendere: per ora si usa sempre 0
 * (un fetcher può non supportare altro). [expectedSize] è la dimensione intera attesa del blob, utile al trasporto
 * per scartare subito una risposta che dichiara un'altra lunghezza; `null` se non si sa.
 */
class BlobRequest(
    val blobKey: String,
    val offset: Long = 0,
    val expectedSize: Long? = null,
) {
    init {
        require(BlobLayout.isValidBlobKey(blobKey)) { "chiave del blob non valida" }
        require(offset >= 0) { "offset negativo" }
    }
}

/**
 * Dove il fetcher consegna i byte. Il blocco ([chunk], da [offset] per [length] byte) vale solo durante la
 * chiamata: il fetcher lo riusa subito dopo. Se risponde `false` il fetcher smette e dà [FetchResult.SinkRejected].
 */
fun interface BlobSink {
    fun accept(chunk: ByteArray, offset: Int, length: Int): Boolean
}

/** Com'è andato un tentativo di trasporto. */
sealed class FetchResult {
    /** Tutti i byte sono stati consegnati; [contentLength] è la lunghezza annunciata dal server, se l'ha data. */
    data class Success(val bytesDelivered: Long, val contentLength: Long?) : FetchResult()

    /** Il server ha risposto con questo stato (non 200/206); [retryAfterSeconds] se lo ha indicato. */
    data class Http(val status: Int, val retryAfterSeconds: Long? = null) : FetchResult()

    /** Il server annuncia una lunghezza diversa da quella attesa: il corpo non è stato letto. */
    data class ContentLengthMismatch(val expected: Long, val announced: Long) : FetchResult()

    /** Nessuna risposta in tempo. */
    data object Timeout : FetchResult()

    /** Non si è riusciti a collegarsi (rete assente, host sconosciuto, connessione rifiutata…). [reason] è solo un nome di eccezione, mai un indirizzo. */
    data class ConnectionFailed(val reason: String = "") : FetchResult()

    /** La risposta si è interrotta prima della fine: [bytesDelivered] byte arrivati su [announced] annunciati. */
    data class Truncated(val bytesDelivered: Long, val announced: Long?) : FetchResult()

    /** Il [BlobSink] ha rifiutato un blocco: il fetcher si è fermato. */
    data class SinkRejected(val bytesDelivered: Long) : FetchResult()
}

/** Lo stato HTTP nel modello d'errore comune (nessuna dipendenza da una libreria HTTP). */
fun httpStatusToRemoteError(status: Int, retryAfterSeconds: Long? = null): RemoteError = when (status) {
    401, 403 -> RemoteError.AccessDenied
    404 -> RemoteError.NotFound
    429 -> RemoteError.TooManyRequests(retryAfterSeconds)
    in 500..599 -> RemoteError.ServerError(status)
    else -> RemoteError.HttpStatus(status)
}
