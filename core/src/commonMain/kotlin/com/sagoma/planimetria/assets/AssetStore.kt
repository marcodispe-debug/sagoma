package com.sagoma.planimetria.assets

/**
 * Se un asset si può usare subito. Oggi i file sono tutti locali, quindi gli stati sono due; quando gli
 * asset potranno arrivare dalla rete se ne aggiungeranno altri (per esempio "da scaricare" e "in scaricamento"):
 * chi usa [AssetStore] deve trattare "non disponibile" come un caso normale, non come un errore.
 */
enum class AssetAvailability { Available, Unavailable }

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

    override fun availability(path: String): AssetAvailability =
        if (stores.any { it.availability(path) == AssetAvailability.Available }) AssetAvailability.Available else AssetAvailability.Unavailable

    override fun peek(path: String): ByteArray? {
        for (s in stores) s.peek(path)?.let { return it }
        return null
    }

    override suspend fun ensure(path: String): AssetAvailability {
        for (s in stores) if (s.ensure(path) == AssetAvailability.Available) return AssetAvailability.Available
        return AssetAvailability.Unavailable
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
