package com.sagoma.planimetria.ui

import android.content.res.AssetManager
import com.sagoma.planimetria.assets.AssetAvailability
import com.sagoma.planimetria.assets.AssetStore

/**
 * Asset impacchettati nell'app (la cartella `assets/` del flavor pro, oggi `app/src/pro/assets`), letti con
 * l'`AssetManager` di Android. Nel flavor free non c'è nessun asset: ogni file risulta non disponibile.
 */
class AndroidAssetStore(private val assets: AssetManager) : AssetStore {
    override fun availability(path: String): AssetAvailability =
        if (runCatching { assets.open(path).close() }.isSuccess) AssetAvailability.Available else AssetAvailability.Unavailable

    override fun peek(path: String): ByteArray? = runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
}
