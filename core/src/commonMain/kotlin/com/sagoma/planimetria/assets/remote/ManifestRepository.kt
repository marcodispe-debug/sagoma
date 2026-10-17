package com.sagoma.planimetria.assets.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Da dove viene l'istantanea corrente. */
enum class ManifestOrigin {
    /** Il manifest iniziale che l'app porta con sé. */
    Bundled,

    /** L'ultimo manifest valido salvato in una sessione precedente. */
    Persisted,

    /** Un manifest arrivato dopo l'avvio ([ManifestRepository.replace] o [ManifestRepository.refresh]). */
    Updated,
}

/**
 * Un manifest letto, validato e pronto all'uso, con il suo [BlobResolver]. Non cambia mai: chi la tiene in mano
 * (un'operazione in corso) vede sempre gli stessi dati, anche se nel frattempo il repository pubblica un'altra.
 */
class ManifestSnapshot internal constructor(
    val manifest: Manifest,
    val resolver: BlobResolver,
    val origin: ManifestOrigin,
) {
    val catalogVersion: Long get() = manifest.catalogVersion
    val releaseId: String get() = manifest.releaseId
}

/** Dove si conserva l'ultimo manifest valido tra una sessione e l'altra. Il contenuto è il testo del manifest, così com'è arrivato. */
interface ManifestPersistence {
    /** Il testo salvato, oppure `null` se non c'è (o non si legge). Non dice se è valido: lo decide il repository. */
    fun load(): String?

    /**
     * Salva `text` al posto del precedente in modo atomico: chi legge vede il vecchio o il nuovo, mai un file a metà, e se
     * qualcosa va storto (anche a metà scrittura) il precedente resta intatto. `false` se non è riuscito.
     */
    fun save(text: String): Boolean
}

/** Da dove si prende un manifest più recente. Trasporto e provider stanno fuori da qui: è un'interfaccia sostituibile. */
interface ManifestFetcher {
    suspend fun fetch(): ManifestFetchResult
}

sealed class ManifestFetchResult {
    /** Il testo del manifest (non ancora validato). */
    data class Text(val text: String) : ManifestFetchResult()

    data class Error(val error: RemoteError) : ManifestFetchResult()
}

/** Esito di [ManifestRepository.replace] e [ManifestRepository.refresh]. */
sealed class ManifestRefreshResult {
    /** Il nuovo manifest è valido, salvato ed è ora quello corrente. */
    data class Updated(val snapshot: ManifestSnapshot) : ManifestRefreshResult()

    /** Era già quello corrente (stessa versione, stesso contenuto): niente da fare. */
    data class Unchanged(val snapshot: ManifestSnapshot) : ManifestRefreshResult()

    /** Non è cambiato niente: l'istantanea corrente (se c'è) resta quella di prima. [error] dice perché. */
    data class Failed(val error: RemoteError) : ManifestRefreshResult()
}

/**
 * Possiede l'istantanea del manifest usata dal resto del sistema (quella che si dà a [BlobResolver] / `RemoteAssetStore`).
 *
 * All'avvio sceglie tra il manifest iniziale ([bundledManifest]) e l'ultimo salvato da [persistence]: vince il più recente
 * (`catalogVersion` più alta; a parità, quello dell'app). Un manifest salvato che non è valido (illeggibile, troncato, vuoto,
 * di uno schema che l'app non conosce, incoerente) non si usa e si ripiega sull'altro; se non ce n'è nessuno [current] è `null`.
 *
 * Aggiornamento ([replace], [refresh]): il testo nuovo si valida per intero, la sua `catalogVersion` deve essere più alta
 * di quella corrente (una più bassa è un tentativo di tornare indietro e si rifiuta; la stessa è un no-op se il contenuto
 * è identico e un errore se è diverso: una versione identifica un solo manifest), poi si SALVA in modo atomico e solo se il
 * salvataggio riesce diventa quello corrente. Qualunque fallimento (rete, manifest non valido, versione vecchia, salvataggio)
 * lascia l'istantanea corrente com'era: un server irraggiungibile non toglie mai l'ultimo manifest valido.
 *
 * Il controllo di versione è solo locale (non tornare indietro per errore): NON è l'anti-rollback crittografico, che
 * verrà con la firma del manifest. Il minimo che si ricorda è quello del manifest corrente.
 *
 * L'istantanea è immutabile: [current] si sostituisce per intero, mai in-place. Chi ne ha già una continua a usare quella;
 * chi la chiede dopo vede la nuova. Si può leggere da qualunque thread senza attese; gli aggiornamenti sono uno alla volta.
 * Il costruttore legge da [persistence] (un file piccolo): va costruito all'avvio, non in un frame di disegno.
 */
class ManifestRepository(
    private val persistence: ManifestPersistence? = null,
    bundledManifest: String? = null,
    private val fetcher: ManifestFetcher? = null,
    private val clientVersion: Int = Int.MAX_VALUE,
) {
    private val lock = Mutex()

    /** L'istantanea corrente, o `null` se non c'è né un manifest iniziale né uno salvato valido. */
    @kotlin.concurrent.Volatile
    var current: ManifestSnapshot? = null
        private set

    /** Il resolver dell'istantanea corrente (lo stesso oggetto di `current.resolver`). */
    val resolver: BlobResolver? get() = current?.resolver

    init {
        val bundled = bundledManifest?.let { snapshotOf(it, ManifestOrigin.Bundled) }
        val persisted = persistence?.let { p ->
            val text = try {
                p.load()
            } catch (e: Exception) {
                null
            }
            text?.let { snapshotOf(it, ManifestOrigin.Persisted) }
        }
        current = when {
            bundled == null -> persisted
            persisted == null -> bundled
            persisted.catalogVersion > bundled.catalogVersion -> persisted
            else -> bundled // a parità vince il manifest portato dall'app
        }
    }

    /**
     * Prende un manifest più recente dal [ManifestFetcher] e lo installa come [replace]. Senza fetcher dà `Failed`.
     * Il fetcher gira fuori dal blocco degli aggiornamenti: leggere [current] non aspetta mai la rete.
     */
    suspend fun refresh(): ManifestRefreshResult {
        val f = fetcher ?: return ManifestRefreshResult.Failed(RemoteError.CacheUnavailable("no manifest fetcher configured"))
        val fetched = try {
            f.fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return ManifestRefreshResult.Failed(RemoteError.CacheUnavailable("unexpected ${e::class.simpleName}"))
        }
        return when (fetched) {
            is ManifestFetchResult.Text -> replace(fetched.text)
            is ManifestFetchResult.Error -> ManifestRefreshResult.Failed(fetched.error)
        }
    }

    /** Valida `text`, lo salva e lo rende corrente, oppure non cambia niente (vedi [ManifestRefreshResult]). */
    suspend fun replace(text: String): ManifestRefreshResult = lock.withLock {
        val cur = current
        val manifest = when (val r = ManifestParser.parse(text, clientVersion, cur?.catalogVersion ?: 0)) {
            is ManifestParseResult.Valid -> r.manifest
            is ManifestParseResult.Invalid -> return@withLock ManifestRefreshResult.Failed(RemoteError.ManifestInvalid(r.issues))
            is ManifestParseResult.Incompatible -> return@withLock ManifestRefreshResult.Failed(RemoteError.ManifestIncompatible(r.reason))
        }
        if (cur != null && manifest.catalogVersion == cur.catalogVersion) {
            return@withLock if (manifest == cur.manifest) {
                ManifestRefreshResult.Unchanged(cur)
            } else {
                ManifestRefreshResult.Failed(RemoteError.ManifestIncompatible("catalogVersion ${manifest.catalogVersion} already used by a different manifest"))
            }
        }
        val resolver = try {
            BlobResolver(manifest)
        } catch (e: IllegalArgumentException) {
            return@withLock ManifestRefreshResult.Failed(RemoteError.ManifestInvalid(listOf("manifest cannot be indexed")))
        }
        if (persistence != null) {
            val saved = try {
                persistence.save(text)
            } catch (e: Exception) {
                false
            }
            // Non salvato = non adottato: la memoria e il disco dicono la stessa cosa e il manifest corrente resta usabile.
            if (!saved) return@withLock ManifestRefreshResult.Failed(RemoteError.CacheUnavailable("manifest not persisted"))
        }
        val snapshot = ManifestSnapshot(manifest, resolver, ManifestOrigin.Updated)
        current = snapshot
        ManifestRefreshResult.Updated(snapshot)
    }

    private fun snapshotOf(text: String, origin: ManifestOrigin): ManifestSnapshot? {
        val manifest = (ManifestParser.parse(text, clientVersion) as? ManifestParseResult.Valid)?.manifest ?: return null
        val resolver = try {
            BlobResolver(manifest)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return ManifestSnapshot(manifest, resolver, origin)
    }
}
