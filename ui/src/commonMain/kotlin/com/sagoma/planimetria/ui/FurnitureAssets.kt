package com.sagoma.planimetria.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.sagoma.planimetria.assets.AssetPaths
import com.sagoma.planimetria.assets.LruByteCache
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.GeneratedFurniture
import com.sagoma.planimetria.model.MaterialCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Livello sottile sopra [com.sagoma.planimetria.assets.AssetStore] per l'interfaccia: carica i cataloghi
 * all'avvio (senza file, come nel browser, non succede nulla) e dà le immagini già decodificate di
 * miniature, viste dall'alto e campioni dei materiali. I file li fornisce [Platform.assetStore] (dove stiano
 * non importa qui); le immagini si leggono al primo uso e restano in memoria fino a un limite, poi si
 * butta via la meno usata di recente. Si usa dal solo thread dell'interfaccia, tranne la lettura dei cataloghi all'avvio
 * (vedi [init]).
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

    /**
     * I cataloghi di arredi e materiali sono pronti. Si leggono in un thread a parte ([init]): l'app si apre subito e il
     * progetto (che li usa per disegnare) aspetta questo segnale, di solito già arrivato. Vero finché nessuno chiama [init]
     * (nelle prove non c'è niente da aspettare).
     */
    var ready by mutableStateOf(true)
        private set

    /**
     * Da chiamare all'avvio: legge i cataloghi in un thread a parte (prima si leggevano qui, sul thread principale, prima che
     * comparisse l'app). Senza catalogo (browser) non succede nulla, ma [ready] diventa vero lo stesso.
     */
    fun init(p: Platform) {
        if (platform != null) return
        platform = p
        ready = false
        CoroutineScope(Dispatchers.Default).launch {
            try {
                loadCatalogs(p)
            } finally {
                ready = true
            }
        }
    }

    private fun loadCatalogs(p: Platform) {
        val store = p.assetStore
        store.peek(AssetPaths.FURNITURE_CATALOG)?.let { runCatching { FurnitureCatalog.load(it.decodeToString()) } }
        store.peek(AssetPaths.MATERIAL_CATALOG)?.let { runCatching { MaterialCatalog.load(it.decodeToString()) } }
        FurnitureCatalog.addRugs(MaterialCatalog.items)
        // Moduli della cucina: non hanno file, ma vanno con gli arredi (solo dove c'è il catalogo).
        if (FurnitureCatalog.items.isNotEmpty()) {
            FurnitureCatalog.addKitchen()
            FurnitureCatalog.addParametric()
        }
    }

    /** Campione di un materiale fotografico, per sceglierlo. */
    fun materialThumb(id: String): ImageBitmap? = materialThumbs.getOrLoad(id) { read(AssetPaths.materialThumbnail(id)) }

    private fun read(path: String): ImageBitmap? {
        val p = platform ?: return null
        return p.assetStore.peek(path)?.let { runCatching { p.decodeImage(it) }.getOrNull() }
    }

    /**
     * Miniatura in prospettiva, per il catalogo (per i tappeti di materiale, il campione del materiale). I mobili generati alla
     * misura non ne hanno: si disegna la loro vista dal davanti.
     */
    fun thumbnail(model: String): ImageBitmap? = if (GeneratedFurniture.isGenerated(model)) null
        else FurnitureCatalog.rugOf(model)?.let { materialThumb(it.first) } ?: thumbs.getOrLoad(model) { read(AssetPaths.furnitureThumbnail(model)) }

    /** Vista dall'alto, per la pianta (il davanti del mobile in basso). I mobili generati alla misura hanno il loro simbolo. */
    fun top(model: String): ImageBitmap? = if (GeneratedFurniture.isGenerated(model)) null
        else FurnitureCatalog.rugOf(model)?.let { materialThumb(it.first) } ?: tops.getOrLoad(model) { read(AssetPaths.furnitureTop(model)) }
}
