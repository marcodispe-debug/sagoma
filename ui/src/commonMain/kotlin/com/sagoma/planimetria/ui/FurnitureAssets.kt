package com.sagoma.planimetria.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.sagoma.planimetria.assets.AssetPaths
import com.sagoma.planimetria.assets.LruByteCache
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog

/**
 * Livello sottile sopra [com.sagoma.planimetria.assets.AssetStore] per l'interfaccia: carica i cataloghi
 * all'avvio (solo nella versione pro: senza file non succede nulla) e dà le immagini già decodificate di
 * miniature, viste dall'alto e campioni dei materiali. I file li fornisce [Platform.assetStore] (dove stiano
 * non importa qui); le immagini si leggono al primo uso e restano in memoria fino a un limite, poi si
 * butta via la meno usata di recente. Si usa dal solo thread dell'interfaccia.
 */
object FurnitureAssets {
    // Una miniatura decodificata pesa circa 200 KB e una vista dall'alto circa 180 KB (RGBA): senza limite,
    // sfogliando migliaia di arredi la memoria crescerebbe senza fine.
    private const val THUMBS_BYTES = 32L * 1024 * 1024
    private const val TOPS_BYTES = 48L * 1024 * 1024
    private const val MATERIAL_THUMBS_BYTES = 16L * 1024 * 1024

    private var platform: Platform? = null
    private val thumbs = imageCache(THUMBS_BYTES)
    private val tops = imageCache(TOPS_BYTES)
    private val materialThumbs = imageCache(MATERIAL_THUMBS_BYTES)

    private fun imageCache(maxBytes: Long) = LruByteCache<ImageBitmap>(maxBytes) { it.width.toLong() * it.height * 4 }

    /** Da chiamare all'avvio: senza catalogo (versione free) non succede nulla. */
    fun init(p: Platform) {
        if (platform != null) return
        platform = p
        val store = p.assetStore
        store.peek(AssetPaths.FURNITURE_CATALOG)?.let { runCatching { FurnitureCatalog.load(it.decodeToString()) } }
        store.peek(AssetPaths.MATERIAL_CATALOG)?.let { runCatching { MaterialCatalog.load(it.decodeToString()) } }
        FurnitureCatalog.addRugs(MaterialCatalog.items)
    }

    /** Campione di un materiale fotografico, per sceglierlo. */
    fun materialThumb(id: String): ImageBitmap? = materialThumbs.getOrLoad(id) { read(AssetPaths.materialThumbnail(id)) }

    private fun read(path: String): ImageBitmap? {
        val p = platform ?: return null
        return p.assetStore.peek(path)?.let { runCatching { p.decodeImage(it) }.getOrNull() }
    }

    /** Miniatura in prospettiva, per il catalogo (per i tappeti di materiale, il campione del materiale). */
    fun thumbnail(model: String): ImageBitmap? = FurnitureCatalog.rugOf(model)?.let { materialThumb(it.first) }
        ?: thumbs.getOrLoad(model) { read(AssetPaths.furnitureThumbnail(model)) }

    /** Vista dall'alto, per la pianta (il davanti del mobile in basso). */
    fun top(model: String): ImageBitmap? = FurnitureCatalog.rugOf(model)?.let { materialThumb(it.first) }
        ?: tops.getOrLoad(model) { read(AssetPaths.furnitureTop(model)) }
}
