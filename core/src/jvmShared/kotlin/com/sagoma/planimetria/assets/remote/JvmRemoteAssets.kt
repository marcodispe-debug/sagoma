package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.REMOTE_CACHE_DIR_NAME
import com.sagoma.planimetria.assets.openRemoteAssetCache
import com.sagoma.planimetria.assets.overlaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

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
    val cache = openRemoteAssetCache(baseDir, maxCacheBytes, protectedDirs) ?: return null
    val manifests = ManifestRepository(
        persistence = DiskManifestPersistence(manifestDir),
        bundledManifest = bundledManifest,
        fetcher = HttpManifestFetcher(manifestUrl, manifestHeaders),
        clientVersion = clientVersion,
    )
    return RemoteAssets.create(manifests, cache, HttpBlobFetcher(blobBaseUrl, blobHeaders), policy, capabilities, maxParallel, scope)
}

/** I nomi delle due cartelle remote dentro la base, per chi deve escluderle da pulizie o backup. */
val remoteDirNames: List<String> = listOf(REMOTE_CACHE_DIR_NAME, REMOTE_MANIFEST_DIR_NAME)
