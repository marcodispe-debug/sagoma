package com.sagoma.planimetria.assets

import java.io.IOException

/**
 * Asset inclusi nel programma (risorse del classpath): `furniture/catalog.json` si cerca come risorsa con
 * quel nome. Sul computer è la seconda scelta, dopo la cartella indicata a mano.
 */
class ClasspathAssetStore(private val loader: ClassLoader) : AssetStore {
    override fun availability(path: String): AssetAvailability =
        if (isSafeAssetPath(path) && loader.getResource(path) != null) AssetAvailability.Available else AssetAvailability.Unavailable

    override fun peek(path: String): ByteArray? {
        if (!isSafeAssetPath(path)) return null
        return try {
            loader.getResourceAsStream(path)?.use { it.readBytes() }
        } catch (e: IOException) {
            null
        }
    }
}
