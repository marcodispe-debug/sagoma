package com.sagoma.planimetria.assets.remote

/**
 * Perché un asset remoto non si è potuto ottenere. Non dipende da HTTP né da un fornitore: chi scarica (in futuro un
 * `BlobFetcher`) traduce i suoi errori in questi. [describe] è sicuro da scrivere nei log: non contiene mai
 * credenziali, token né URL firmati, solo percorsi logici, numeri e impronte.
 */
sealed class RemoteError {
    /** Testo per i log, senza dati riservati. */
    abstract fun describe(): String

    /** Non c'è rete. */
    data object Offline : RemoteError() {
        override fun describe() = "offline"
    }

    /** Il server non ha risposto in tempo. */
    data object Timeout : RemoteError() {
        override fun describe() = "timeout"
    }

    /** Risposta con uno stato che non ha una voce propria (`status` è il codice, per esempio 418). */
    data class HttpStatus(val status: Int) : RemoteError() {
        override fun describe() = "http status $status"
    }

    /** Il file non esiste sul server. */
    data object NotFound : RemoteError() {
        override fun describe() = "not found"
    }

    /** Accesso negato (credenziali mancanti, scadute o non sufficienti). */
    data object AccessDenied : RemoteError() {
        override fun describe() = "access denied"
    }

    /** Troppe richieste; `retryAfterSeconds` se il server lo ha detto. */
    data class TooManyRequests(val retryAfterSeconds: Long? = null) : RemoteError() {
        override fun describe() = "too many requests" + (retryAfterSeconds?.let { " (retry after ${it}s)" } ?: "")
    }

    /** Errore del server (5xx). */
    data class ServerError(val status: Int) : RemoteError() {
        override fun describe() = "server error $status"
    }

    /** Il file è più grande di quanto dichiarato o consentito. */
    data class TooLarge(val declared: Long, val limit: Long) : RemoteError() {
        override fun describe() = "too large: $declared bytes, limit $limit"
    }

    /** La lunghezza annunciata dal server non coincide con quella attesa. */
    data class ContentLengthMismatch(val expected: Long, val announced: Long) : RemoteError() {
        override fun describe() = "content length mismatch: expected $expected, announced $announced"
    }

    /** Sono arrivati più o meno byte di quelli attesi. */
    data class SizeMismatch(val expected: Long, val actual: Long) : RemoteError() {
        override fun describe() = "size mismatch: expected $expected, got $actual"
    }

    /** L'impronta SHA-256 del contenuto non è quella attesa. */
    data class HashMismatch(val expected: String, val actual: String) : RemoteError() {
        override fun describe() = "hash mismatch: expected $expected, got $actual"
    }

    /** La cache locale non è utilizzabile (disco pieno, non scrivibile, percorso non valido). */
    data class CacheUnavailable(val reason: String = "") : RemoteError() {
        override fun describe() = "cache unavailable" + if (reason.isEmpty()) "" else ": $reason"
    }

    /** Il manifest non è valido. */
    data class ManifestInvalid(val issues: List<String>) : RemoteError() {
        override fun describe() = "manifest invalid: " + issues.take(5).joinToString("; ") + if (issues.size > 5) "; …" else ""
    }

    /** Il manifest è valido ma questa versione dell'app non lo può usare (schema più nuovo, `minClient`, catalogo più vecchio di uno già visto). */
    data class ManifestIncompatible(val reason: String) : RemoteError() {
        override fun describe() = "manifest incompatible: $reason"
    }

    /** La firma del manifest non è valida. */
    data object SignatureInvalid : RemoteError() {
        override fun describe() = "signature invalid"
    }

    /** L'operazione è stata annullata da chi l'aveva chiesta. */
    data object Cancelled : RemoteError() {
        override fun describe() = "cancelled"
    }

    /** Errori che ha senso ritentare più tardi (rete, tempo scaduto, limiti, server). */
    val isTransient: Boolean
        get() = when (this) {
            Offline, Timeout, is TooManyRequests, is ServerError -> true
            else -> false
        }
}
