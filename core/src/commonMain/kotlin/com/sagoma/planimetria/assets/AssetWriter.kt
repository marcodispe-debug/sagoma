package com.sagoma.planimetria.assets

/** Esito di [AssetWriter.write]. */
enum class AssetWriteResult {
    /** Il blocco è stato accettato. */
    Ok,

    /** Con questo blocco si supererebbe la dimensione dichiarata: lo scrittore è fallito e non salverà niente. */
    TooMuchData,

    /** Errore di scrittura: lo scrittore è fallito e non salverà niente. */
    IoError,

    /** Lo scrittore non è più aperto (completato, abortito o già fallito): il blocco è ignorato. */
    Closed,
}

/** In che punto è uno [AssetWriter]. */
enum class AssetWriterState {
    /** Accetta blocchi. */
    Open,

    /** `commit` è riuscito: il file è in cache. */
    Committed,

    /** Chiuso senza salvare (`abort`, o `commit` non riuscito). */
    Closed,
}

/**
 * Scrittura a blocchi di un file in cache, per file che non devono stare per intero in memoria. Si ottiene da
 * [AssetCache.openWrite], che ne fissa percorso, dimensione e SHA-256 attesi.
 *
 * Garanzie:
 *  - il file non è visibile (né [AssetStore.peek] né [AssetCache.info]) finché `commit` non riesce;
 *  - i byte oltre la dimensione dichiarata vengono rifiutati subito ([AssetWriteResult.TooMuchData]);
 *  - `commit` controlla dimensione esatta e SHA-256 prima di rendere il file visibile, con un solo passaggio sulla
 *    copia (l'impronta si calcola mentre i blocchi arrivano);
 *  - se qualcosa va storto non resta nessuna voce valida in cache e il file temporaneo sparisce;
 *  - appartiene a un solo chiamante alla volta (ma i metodi sono comunque sicuri se chiamati da thread diversi,
 *    per esempio `abort` da un altro thread).
 *
 * Va sempre chiuso con `commit` o `abort`.
 */
interface AssetWriter {
    val path: String
    val expectedSize: Long
    val expectedSha256: String

    /** Byte accettati finora. */
    val bytesWritten: Long

    val state: AssetWriterState

    /** Accoda `length` byte di `chunk` a partire da `offset`. Il blocco si può riusare subito dopo il ritorno. */
    fun write(chunk: ByteArray, offset: Int = 0, length: Int = chunk.size - offset): AssetWriteResult

    /**
     * Completa la scrittura: verifica dimensione e impronta, poi rende il file visibile. Ritorna il motivo se non
     * ci riesce (nel qual caso non resta niente). Chiamato di nuovo dà lo stesso esito; dopo `abort` dà [AssetPutResult.Aborted].
     */
    fun commit(): AssetPutResult

    /** Rinuncia e scarta tutto. Si può chiamare più volte; dopo un `commit` riuscito non fa niente. */
    fun abort()
}

/** Scrittore già fallito in partenza (percorso non valido, file troppo grande…): `commit` ridà il motivo. */
internal class FailedAssetWriter(
    override val path: String,
    override val expectedSize: Long,
    override val expectedSha256: String,
    private val reason: AssetPutResult,
) : AssetWriter {
    override val bytesWritten: Long get() = 0
    override val state: AssetWriterState get() = AssetWriterState.Closed
    override fun write(chunk: ByteArray, offset: Int, length: Int): AssetWriteResult = AssetWriteResult.Closed
    override fun commit(): AssetPutResult = reason
    override fun abort() {}
}

/**
 * Adattatore per le cache che non hanno una scrittura a blocchi (non sicuro tra thread: un solo chiamante): accumula i byte (mai oltre la dimensione
 * dichiarata) e a `commit` li passa a [AssetCache.put]. Non risparmia memoria, ma dà lo stesso contratto.
 */
internal class BufferingAssetWriter(
    private val cache: AssetCache,
    override val path: String,
    override val expectedSize: Long,
    override val expectedSha256: String,
) : AssetWriter {
    private var buffer = ByteArray(0)
    private var size = 0
    private var result: AssetPutResult? = null
    private var open = true

    // Se già in partenza non può riuscire, lo si dice al commit senza accumulare niente.
    private val initialFailure: AssetPutResult? = when {
        expectedSize < 0 || expectedSize > Int.MAX_VALUE -> if (expectedSize > cache.maxBytes) AssetPutResult.TooLarge else AssetPutResult.SizeMismatch
        expectedSize > cache.maxBytes -> AssetPutResult.TooLarge
        else -> null
    }

    init {
        if (initialFailure != null) finish(initialFailure)
    }

    override val bytesWritten: Long get() = size.toLong()
    override val state: AssetWriterState
        get() = when {
            open -> AssetWriterState.Open
            result == AssetPutResult.Stored -> AssetWriterState.Committed
            else -> AssetWriterState.Closed
        }

    override fun write(chunk: ByteArray, offset: Int, length: Int): AssetWriteResult {
        if (offset < 0 || length < 0 || offset > chunk.size - length) throw IndexOutOfBoundsException()
        if (!open) return AssetWriteResult.Closed
        if (size.toLong() + length > expectedSize) {
            finish(AssetPutResult.SizeMismatch)
            return AssetWriteResult.TooMuchData
        }
        if (size + length > buffer.size) {
            val newCap = maxOf(size + length, minOf(buffer.size * 2L, expectedSize).toInt(), 8192.coerceAtMost(expectedSize.toInt()))
            buffer = buffer.copyOf(newCap)
        }
        chunk.copyInto(buffer, size, offset, offset + length)
        size += length
        return AssetWriteResult.Ok
    }

    override fun commit(): AssetPutResult {
        result?.let { return it }
        val data = if (buffer.size == size) buffer else buffer.copyOf(size)
        val r = if (size.toLong() != expectedSize) AssetPutResult.SizeMismatch else cache.put(path, data, expectedSha256)
        finish(r)
        return r
    }

    override fun abort() {
        if (open) finish(AssetPutResult.Aborted)
    }

    private fun finish(r: AssetPutResult) {
        result = r
        open = false
        buffer = ByteArray(0)
    }
}
