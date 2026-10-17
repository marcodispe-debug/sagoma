package com.sagoma.planimetria.assets

/** Com'è andato il tentativo di mettere un file in una [AssetCache]. */
enum class AssetPutResult {
    /** Il file è in cache, completo e verificato. */
    Stored,

    /** L'impronta SHA-256 del contenuto non è quella attesa: il file NON è stato salvato. */
    HashMismatch,

    /** Da solo il file non entra nel limite di spazio della cache: NON è stato salvato. */
    TooLarge,

    /** Percorso non valido per la cache (esce dalla radice, caratteri non portabili, maiuscole/minuscole in conflitto). */
    InvalidPath,

    /** Errore del disco (spazio finito, percorso occupato da un file…): il file NON è stato salvato e il resto della cache è intatto. */
    IoError,

    /** I byte ricevuti non sono quanti dichiarati (troppi o troppo pochi): il file NON è stato salvato. */
    SizeMismatch,

    /** Chi scriveva ha rinunciato ([AssetWriter.abort]) o ha chiuso lo scrittore senza completarlo: niente è stato salvato. */
    Aborted,
}

/**
 * Un file presente in cache. [sha256] è sempre quello del contenuto salvato (esadecimale minuscolo): chi
 * scarica può confrontarlo con quello atteso per sapere se la copia in cache è quella giusta o è da sostituire.
 */
class CachedAssetInfo(
    val path: String,
    val size: Long,
    val sha256: String,
    val lastAccessMillis: Long,
    val pinned: Boolean,
)

/**
 * Cache persistente di asset già verificati: i file restano tra un avvio e l'altro e si leggono come da
 * qualsiasi [AssetStore] ([peek], [availability]), anche senza rete. Non scarica niente: i file ce li mette
 * chi li ottiene (in futuro un negozio remoto) con [put].
 *
 * Garanzie:
 *  - un file in cache è sempre completo: [put] lo scrive prima in un file temporaneo e lo sposta al suo
 *    posto solo a verifica fatta, quindi una scrittura interrotta o un file corrotto non entrano mai;
 *  - lo spazio è limitato a [maxBytes]: quando serve si buttano via per primi i file usati meno di recente,
 *    mai quelli fissati con [setPinned] (per esempio gli asset usati dai progetti salvati);
 *  - un file non fissato può sparire in qualsiasi momento: chi lo usa tratta "non disponibile" come normale.
 */
interface AssetCache : AssetStore {
    /** Spazio massimo per i file in cache (byte). I file fissati possono farlo superare. */
    val maxBytes: Long

    /** Spazio usato dai file in cache (byte). */
    val usedBytes: Long

    /** Cosa c'è in cache, dal meno al più recente. */
    fun list(): List<CachedAssetInfo>

    /** Il file in cache, o `null` se non c'è. */
    fun info(path: String): CachedAssetInfo?

    /**
     * Mette il file in cache, al posto di quello che c'era con lo stesso percorso. Se si passa [sha256] il
     * contenuto deve avere quell'impronta, altrimenti non si salva ([AssetPutResult.HashMismatch]).
     */
    fun put(path: String, bytes: ByteArray, sha256: String? = null): AssetPutResult

    /**
     * Apre una scrittura a blocchi di un file di cui si conoscono dimensione e impronta (per esempio da un
     * manifest): è il modo di mettere in cache file grandi senza tenerli per intero in memoria. Il file diventa
     * visibile, completo, solo con [AssetWriter.commit]; vedi [AssetWriter] per le garanzie. Lo scrittore si
     * ottiene sempre: se il percorso non è valido o il file non entra, il suo `commit` darà il motivo.
     *
     * L'implementazione di default accumula i byte in memoria e alla fine usa [put]: corretta ma non a blocchi.
     * Le cache su disco la sostituiscono con una vera scrittura in streaming.
     */
    fun openWrite(path: String, expectedSize: Long, expectedSha256: String): AssetWriter =
        BufferingAssetWriter(this, path, expectedSize, expectedSha256)

    /** Toglie il file dalla cache; `true` se c'era. */
    fun remove(path: String): Boolean

    /**
     * Fissa in cache i file di `owner` (per esempio l'id di un progetto): non vengono mai buttati via per
     * far posto ad altri. Sostituisce l'elenco precedente di quell'`owner`; un elenco vuoto lo libera.
     * Si può fissare un file che non c'è ancora: vale da quando arriva.
     */
    fun setPinned(owner: String, paths: Collection<String>)

    /** Ricalcola l'impronta del file e la confronta con quella salvata; se non coincide lo toglie e dà `false`. */
    fun verify(path: String): Boolean

    /** Come [verify] per tutta la cache: i percorsi trovati corrotti (e tolti). */
    fun verifyAll(): List<String>

    /**
     * Salva su disco l'ordine d'uso aggiornato dalle letture (le scritture lo salvano già da sole). Chi usa la
     * cache lo chiama quando l'app va in pausa o si chiude: senza, dopo un arresto brusco l'ordine tornerebbe
     * a quello dell'ultima scrittura. Non serve alla correttezza, solo a buttare via i file giusti.
     */
    fun flush()

    /** Svuota la cache, file fissati compresi. */
    fun clear()
}

/**
 * La voce in cache è proprio quella attesa: stessa impronta SHA-256 (maiuscole e minuscole non contano) e, se
 * [size] è data, stessa dimensione. L'unico confronto "questo file è quello giusto?" per chi verifica la cache
 * contro un manifest ([HashCheckedCacheView], [com.sagoma.planimetria.assets.remote.RemoteAssetStore]).
 */
internal fun CachedAssetInfo?.matches(sha256: String, size: Long? = null): Boolean =
    this != null && this.sha256.equals(sha256, ignoreCase = true) && (size == null || this.size == size)
