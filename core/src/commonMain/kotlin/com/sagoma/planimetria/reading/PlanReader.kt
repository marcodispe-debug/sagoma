package com.sagoma.planimetria.reading

/**
 * Chi legge una piantina al posto dell'utente: oggi il server della lettura (server/lettura-piantine), che la fa leggere a
 * Claude. Riceve l'immagine (già ridotta, vedi [MAX_SIDE]) e restituisce la lettura in pixel di quell'immagine.
 */
interface PlanReader {
    suspend fun read(image: ByteArray, mediaType: String, width: Int, height: Int): PlanReaderResult

    companion object {
        /** Lato lungo massimo dell'immagine da mandare: oltre, il modello la riduce e le coordinate non tornerebbero. */
        const val MAX_SIDE = 2560

        /** Pixel in tutto, per lo stesso motivo (il massimo è 3,75 milioni). */
        const val MAX_PIXELS = 3_700_000.0

        /** Lato lungo da chiedere riducendo un'immagine di [width] × [height] perché rispetti entrambi i limiti. */
        fun sideFor(width: Int, height: Int): Int {
            val long = maxOf(width, height)
            val byArea = long * kotlin.math.sqrt(MAX_PIXELS / (width.toDouble() * height))
            return minOf(MAX_SIDE.toDouble(), long.toDouble(), byArea).toInt()
        }
    }
}

sealed interface PlanReaderResult {
    data class Ok(val reading: PlanReading) : PlanReaderResult

    /** Il server ha risposto con un errore: [code] tra quelli del server (`troppe_letture`, `non_una_piantina`…), [message] da mostrare. */
    data class Refused(val code: String, val message: String?) : PlanReaderResult

    /** Niente rete, server che non risponde o risposta incomprensibile. */
    data object Unreachable : PlanReaderResult
}

/**
 * La stessa lettura in pixel di un'immagine di [width] × [height]: si legge la piantina ridotta e si usa come sfondo quella
 * più grande, quindi ogni coordinata e misura in pixel si moltiplica per il rapporto tra le due (le misure scritte no).
 */
fun PlanReading.scaledTo(width: Int, height: Int): PlanReading {
    val sx = width.toDouble() / image.width
    val sy = height.toDouble() / image.height
    val s = (sx + sy) / 2
    fun p(q: List<Double>) = if (q.size >= 2) listOf(q[0] * sx, q[1] * sy) else q
    fun ps(qs: List<List<Double>>) = qs.map(::p)
    fun o(x: PlanReading.ReadOpening) = x.copy(ends = ps(x.ends), hinge = x.hinge?.let(::p), opensToward = x.opensToward?.let(::p))
    return copy(
        image = PlanReading.ImageSize(width, height),
        rooms = rooms.map { r -> r.copy(corners = ps(r.corners), excluded = r.excluded.map(::ps)) },
        openings = openings.map(::o),
        stairs = stairs.map { st -> st.copy(from = p(st.from), to = p(st.to), widthPx = st.widthPx?.times(s)) },
        walls = walls.map { w -> w.copy(from = p(w.from), to = p(w.to), thicknessPx = w.thicknessPx?.times(s), openings = w.openings.map(::o)) },
        exteriorWallPx = exteriorWallPx?.times(s),
        exteriorOutline = ps(exteriorOutline),
    )
}
