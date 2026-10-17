package com.sagoma.planimetria.assets.remote

/**
 * Come [RemoteAssetStore] scarica: quanti download insieme, quanti tentativi e quanto aspettare tra l'uno e l'altro.
 * I valori di default sono un punto di partenza, non definitivi di produzione.
 *
 * - [maxConcurrentDownloads]: tentativi di download in corso nello stesso momento (di blob diversi).
 * - [maxAttempts]: tentativi totali per un blob, il primo compreso.
 * - [retryTruncated]: una risposta interrotta a metà si ritenta (è un problema di connessione); se `false` fallisce subito.
 * - [failureCooldownMillis]: dopo che un blob ha fallito per un errore transiente (finiti i tentativi) un nuovo `ensure`
 *   non riparte subito con un'altra raffica: per questo tempo ridà l'errore già noto, senza rete. Non vale per gli errori
 *   permanenti o di integrità (404, 403, hash, dimensione, cache…): un nuovo `ensure` esplicito rifà la verifica. Poi scade da solo, e solo una nuova richiesta
 *   riprova (non c'è nessun ritentativo automatico). `0` lo disattiva. Vale per blob (percorso + impronta attesa).
 * - [maxBlobBytes]: dimensione massima, dichiarata dal manifest, di un blob ottenuto dal percorso remoto. Oltre, `RemoteAssetStore`
 *   non lo scarica e non lo serve ([RemoteError.TooLarge]): oggi i file si leggono in un `ByteArray` intero, e un manifest
 *   sbagliato non deve poter esaurire la memoria. Vale solo per il remoto: non è un limite di `AssetStore` né di `AssetCache`.
 * - Attesa prima del tentativo successivo: [baseBackoffMillis] raddoppiata a ogni tentativo, al massimo [maxBackoffMillis];
 *   con `Retry-After` si aspetta almeno quanto dice il server, sempre entro [maxBackoffMillis].
 *
 * Si ritentano solo errori che possono passare da soli (rete, tempo scaduto, 429, 5xx); gli errori di integrità o di
 * contratto (403, 404, hash, dimensione, manifest, capacità) non si ritentano mai.
 */
class FetchPolicy(
    val maxConcurrentDownloads: Int = 4,
    val maxAttempts: Int = 3,
    val retryTruncated: Boolean = true,
    val baseBackoffMillis: Long = 500,
    val maxBackoffMillis: Long = 10_000,
    val failureCooldownMillis: Long = 5_000,
    val maxBlobBytes: Long = 64L * 1024 * 1024,
) {
    init {
        require(maxConcurrentDownloads >= 1) { "maxConcurrentDownloads deve essere almeno 1" }
        require(maxAttempts >= 1) { "maxAttempts deve essere almeno 1" }
        require(maxBlobBytes > 0) { "maxBlobBytes deve essere positivo" }
        require(baseBackoffMillis >= 0 && maxBackoffMillis >= 0 && failureCooldownMillis >= 0) { "attese negative" }
    }

    fun isRetryable(error: RemoteError): Boolean = error.isTransient

    /** Quanto aspettare dopo il tentativo numero [attempt] (1 = il primo) prima del successivo. */
    fun backoffMillis(attempt: Int, error: RemoteError? = null): Long {
        var wait = baseBackoffMillis
        var i = 1
        while (i < attempt && wait < maxBackoffMillis) {
            wait = minOf(wait * 2, maxBackoffMillis)
            i++
        }
        wait = minOf(wait, maxBackoffMillis)
        val retryAfter = (error as? RemoteError.TooManyRequests)?.retryAfterSeconds
        if (retryAfter != null && retryAfter > 0) wait = maxOf(wait, minOf(retryAfter * 1000, maxBackoffMillis))
        return wait
    }
}
