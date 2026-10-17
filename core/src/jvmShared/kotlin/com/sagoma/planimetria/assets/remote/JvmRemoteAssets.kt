package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.REMOTE_CACHE_DIR_NAME
import com.sagoma.planimetria.assets.openRemoteAssetCache
import com.sagoma.planimetria.assets.overlaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Nome della cartella (dentro la base) del manifest salvato; non è mai un nome `v-<impronta>` né la cache dei blob. */
const val REMOTE_MANIFEST_DIR_NAME = "remote-manifest"

/**
 * Costruisce in modo coerente il sistema remoto su disco (Android e computer), senza collegarlo a niente:
 *
 * ```
 * baseDir/remote/            cache dei blob remoti        (openRemoteAssetCache)
 * baseDir/remote-manifest/   ultimo manifest valido       (DiskManifestPersistence)
 * manifestUrl  → HttpManifestFetcher → ManifestRepository (con `bundledManifest` come seme)
 * blobBaseUrl  → HttpBlobFetcher     → RemoteAssetStore → AssetPrefetcher → RemoteAssets
 * ```
 *
 * Per ogni cartella di base può esistere UN SOLO sistema aperto nel processo: due istanze sulla stessa cartella condividerebbero la cache
 * e il manifest salvato (la seconda cancellerebbe i temporanei della prima). Se ce n'è già uno aperto la funzione dà `null`: chi la
 * chiama deve usare l'istanza che già c'è (di norma tenuta da chi vive quanto il processo, non da una schermata) e chiuderla solo
 * a fine processo; dopo `RemoteAssets.close()` la cartella si può occupare di nuovo.
 *
 * `baseDir` può essere la stessa cartella di base della cache degli asset impacchettati: le cartelle remote non vengono mai
 * toccate dalla pulizia delle versioni. Gli indirizzi e le intestazioni sono solo di chi chiama: non c'è nessun fornitore noto qui.
 * Dà `null` se non si può usare (cartelle dentro o sopra una delle `protectedDirs`, errore di apertura della cache): chi chiama
 * prosegue senza remoto. Chiamarla all'avvio, non in un frame di disegno (legge il manifest salvato).
 */
fun createJvmRemoteAssets(
    baseDir: File,
    manifestUrl: String,
    blobBaseUrl: String,
    maxCacheBytes: Long,
    bundledManifest: String? = null,
    protectedDirs: List<File> = emptyList(),
    manifestHeaders: Map<String, String> = emptyMap(),
    blobHeaders: Map<String, String> = emptyMap(),
    policy: FetchPolicy = FetchPolicy(),
    capabilities: Set<String> = emptySet(),
    maxParallel: Int = 3,
    clientVersion: Int = Int.MAX_VALUE,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): RemoteAssets? {
    val manifestDir = File(baseDir, REMOTE_MANIFEST_DIR_NAME)
    if (protectedDirs.any { overlaps(manifestDir, it) }) return null
    val key = try {
        baseDir.canonicalPath
    } catch (e: java.io.IOException) {
        return null
    }
    val token = Any()
    if (openBaseDirs.putIfAbsent(key, token) != null) return null // già aperto in questo processo
    var created: RemoteAssets? = null
    try {
        val cache = openRemoteAssetCache(baseDir, maxCacheBytes, protectedDirs) ?: return null
        val manifests = ManifestRepository(
            persistence = DiskManifestPersistence(manifestDir),
            bundledManifest = bundledManifest,
            fetcher = HttpManifestFetcher(manifestUrl, manifestHeaders),
            clientVersion = clientVersion,
        )
        created = RemoteAssets.create(manifests, cache, HttpBlobFetcher(blobBaseUrl, blobHeaders), policy, capabilities, maxParallel, scope)
        // Si libera la cartella solo se è ancora la nostra: una chiusura ripetuta non tocca chi l'ha occupata dopo.
        created.onClosed = { openBaseDirs.remove(key, token) }
        return created
    } finally {
        if (created == null) openBaseDirs.remove(key, token)
    }
}

/** Cartelle di base occupate da un sistema remoto aperto in questo processo (percorso canonico → chi le occupa). */
private val openBaseDirs = ConcurrentHashMap<String, Any>()

/** I nomi delle due cartelle remote dentro la base, per chi deve escluderle da pulizie o backup. */
val remoteDirNames: List<String> = listOf(REMOTE_CACHE_DIR_NAME, REMOTE_MANIFEST_DIR_NAME)
