package com.sagoma.planimetria.ui

import android.content.Context
import android.content.res.AssetManager
import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.AssetStore
import com.sagoma.planimetria.assets.buildCachedAssetStore
import java.io.File

/**
 * Asset impacchettati nell'app (la cartella `catalogo/src/main/assets`, asset pack "catalogo" installato con l'app), letti con l'`AssetManager` di Android.
 */
class AndroidAssetStore(private val assets: AssetManager) : AssetStore {
    override fun availability(path: String): AssetAvailability =
        if (runCatching { assets.open(path).close() }.isSuccess) AssetAvailability.Available else AssetAvailability.Unavailable

    override fun peek(path: String): ByteArray? = runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
}

/** Spazio massimo della cache su disco degli asset: è una copia di ciò che è già nell'app, quindi resta prudente. */
private const val ANDROID_ASSET_CACHE_BYTES = 256L * 1024 * 1024

/**
 * Il negozio d'asset dell'app: gli asset impacchettati ([AndroidAssetStore]) e, davanti a
 * essi una cache su disco ([com.sagoma.planimetria.assets.DiskAssetCache]) in `noBackupFilesDir` (cartella
 * privata dell'app, fuori dai backup, nessun permesso). Se la cache non si può usare l'app legge dagli asset
 * come prima. La cache vale per l'installazione corrente dell'app: un aggiornamento la rende nuova.
 */
fun createAndroidAssetStore(context: Context): AssetStore {
    val bundled = AndroidAssetStore(context.assets)
    @Suppress("DEPRECATION")
    val stamp = runCatching { "apk:" + context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrNull()
    return buildCachedAssetStore(bundled, File(context.noBackupFilesDir, "asset-cache"), stamp, ANDROID_ASSET_CACHE_BYTES)
}
