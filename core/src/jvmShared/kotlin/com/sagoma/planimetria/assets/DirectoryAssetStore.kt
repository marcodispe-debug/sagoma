package com.sagoma.planimetria.assets

import java.io.File
import java.io.IOException

/**
 * Asset letti da una cartella del disco: `root/furniture/catalog.json`, `root/materials/…`. Sul computer è la
 * cartella `-Dsagoma.assets` (oggi `app/src/pro/assets`); su Android e in futuro potrà essere una cache.
 * Un file che non c'è o non si legge è semplicemente non disponibile.
 */
class DirectoryAssetStore(private val root: File) : AssetStore {
    private fun file(path: String): File? =
        if (!isSafeAssetPath(path)) null else File(root, path).takeIf { it.isFile }

    override fun availability(path: String): AssetAvailability =
        if (file(path) != null) AssetAvailability.Available else AssetAvailability.Unavailable

    override fun peek(path: String): ByteArray? {
        val f = file(path) ?: return null
        return try {
            f.readBytes()
        } catch (e: IOException) {
            null
        }
    }
}
