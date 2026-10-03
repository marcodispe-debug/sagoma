package com.sagoma.planimetria.assets

/**
 * Se un asset si può usare subito. Un solo valore, [Available], vuol dire "c'è, si legge adesso": tutti gli altri
 * vogliono dire "adesso no", e chi usa [AssetStore] deve trattarli come un caso normale, non come un errore
 * (confrontare con `== Available`, mai con un elenco di stati). Il dettaglio (in scaricamento, errore…) sta in
 * [com.sagoma.planimetria.assets.remote.AssetState], non qui.
 */
enum class AssetAvailability {
    /** C'è e si legge adesso. */
    Available,

    /** Non c'è, e questo negozio non sa dove prenderlo. */
    Unavailable,

    /** Non è ancora sul dispositivo, ma il catalogo remoto lo ha: [AssetStore.ensure] può ottenerlo. */
    Remote,

    /** Esiste, ma questa versione dell'app non sa usarlo: inutile provare a scaricarlo. */
    Incompatible,
}

/** Da quanto è "buono" uno stato non disponibile: serve a un negozio composto per riassumere quelli dei suoi negozi. */
private fun AssetAvailability.rank(): Int = when (this) {
    AssetAvailability.Available -> 3
    AssetAvailability.Remote -> 2
    AssetAvailability.Incompatible -> 1
    AssetAvailability.Unavailable -> 0
}

/**
 * Da dove arrivano i file di arredi, materiali e luci (catalogo, miniature, modelli 3D, texture, luci
 * ambiente). Chi li usa non sa se stanno nell'app, in una cartella del computer o, più avanti, in una
 * cache riempita da un server: conosce solo il percorso logico, relativo alla radice degli asset
 * (per esempio `furniture/catalog.json`, vedi [AssetPaths]).
 *
 * Due modi di chiedere un asset:
 *  - [peek]: lettura LOCALE e sincrona (disco, risorse dell'app): non fa mai rete e non aspetta altri thread
 *    o coroutine, ma impiega il tempo di leggere il file. È per i punti in cui non si può aspettare un
 *    download (composizione dell'interfaccia, disegno, thread di rendering). Dà `null` se il file non è
 *    già qui o non si legge. Un negozio remoto dovrà farla rispondere solo con ciò che ha già in cache.
 *  - [ensure] e [read]: richiesta che può aspettare. Un negozio locale risponde subito; uno remoto potrà
 *    scaricare il file e rispondere dopo. Ogni I/O lento o di rete passa da qui, mai da [peek].
 */
interface AssetStore {
    /**
     * Disponibilità immediata, senza leggere il contenuto. "Disponibile" vuol dire che il file c'è: leggerlo
     * può comunque fallire (allora [peek] e [read] danno `null`).
     */
    fun availability(path: String): AssetAvailability

    /** Contenuto se il file è già disponibile in locale, altrimenti `null`. Non aspetta e non usa la rete. */
    fun peek(path: String): ByteArray?

    /** Fa in modo che il file sia disponibile (un negozio remoto lo scarica) e dice com'è andata. */
    suspend fun ensure(path: String): AssetAvailability = availability(path)

    /** Come [ensure], poi il contenuto; `null` se il file non c'è. */
    suspend fun read(path: String): ByteArray? = if (ensure(path) == AssetAvailability.Available) peek(path) else null
}

/**
 * Un percorso logico valido: relativo, senza `..`. I nomi dei modelli arrivano anche da file di progetto
 * scritti da altri, quindi i negozi che leggono da cartelle non devono uscire dalla loro radice.
 */
internal fun isSafeAssetPath(path: String): Boolean =
    path.isNotEmpty() && !path.startsWith("/") && !path.startsWith("\\") && path.split('/', '\\').none { it == ".." }

private val windowsReservedNames = setOf("CON", "PRN", "AUX", "NUL") + (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

/**
 * Percorso adatto a una cache su disco, anche su Windows: relativo, senza `..`, senza segmenti vuoti né
 * caratteri che Windows non ammette, né nomi riservati (`CON`, `NUL`…). Lo usano la cache su disco e la
 * validazione del manifest: un percorso che non lo rispetta non può stare né nell'uno né nell'altra.
 */
internal fun isPortableAssetPath(path: String): Boolean {
    if (!isSafeAssetPath(path)) return false
    for (segment in path.split('/')) {
        if (segment.isEmpty() || segment.endsWith('.') || segment.endsWith(' ')) return false
        if (segment.any { it < ' ' || it in "<>:\"|?*\\" }) return false
        if (segment.substringBefore('.').uppercase() in windowsReservedNames) return false
    }
    return true
}

/** Comodo per `if`: il file è già disponibile adesso. */
fun AssetStore.isAvailable(path: String): Boolean = availability(path) == AssetAvailability.Available

/** Negozio senza file: dove gli asset non ci sono (versione free, browser). */
object EmptyAssetStore : AssetStore {
    override fun availability(path: String): AssetAvailability = AssetAvailability.Unavailable
    override fun peek(path: String): ByteArray? = null
}

/**
 * Prova i negozi nell'ordine e usa il primo che ha il file. Serve, per esempio, sul computer: prima la
 * cartella indicata a mano e poi le risorse del programma.
 */
class CompositeAssetStore(private val stores: List<AssetStore>) : AssetStore {
    constructor(vararg stores: AssetStore) : this(stores.toList())

    /** `Available` se uno dei negozi lo ha; altrimenti il miglior stato tra i negozi (`Remote` batte `Incompatible` batte `Unavailable`). */
    override fun availability(path: String): AssetAvailability {
        var best = AssetAvailability.Unavailable
        for (s in stores) {
            val a = s.availability(path)
            if (a == AssetAvailability.Available) return a
            if (a.rank() > best.rank()) best = a
        }
        return best
    }

    override fun peek(path: String): ByteArray? {
        for (s in stores) s.peek(path)?.let { return it }
        return null
    }

    override suspend fun ensure(path: String): AssetAvailability {
        var best = AssetAvailability.Unavailable
        for (s in stores) {
            val a = s.ensure(path)
            if (a == AssetAvailability.Available) return a
            if (a.rank() > best.rank()) best = a
        }
        return best
    }

    /**
     * Ogni store prova a dare il file con la sua `read`: uno che lo dichiara ma non riesce a leggerlo (o che
     * deve prima scaricarlo) non impedisce agli altri di rispondere.
     */
    override suspend fun read(path: String): ByteArray? {
        for (s in stores) s.read(path)?.let { return it }
        return null
    }
}
