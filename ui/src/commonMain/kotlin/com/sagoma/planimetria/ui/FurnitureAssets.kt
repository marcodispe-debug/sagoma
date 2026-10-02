package com.sagoma.planimetria.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog

/**
 * File degli arredi in `furniture/` e dei materiali in `materials/` (solo nella versione pro): catalogo,
 * miniature e viste dall'alto. Le immagini si leggono al primo uso e restano in memoria.
 */
object FurnitureAssets {
    private var platform: Platform? = null
    private val thumbs = HashMap<String, ImageBitmap?>()
    private val tops = HashMap<String, ImageBitmap?>()
    private val materialThumbs = HashMap<String, ImageBitmap?>()

    /** Da chiamare all'avvio: senza catalogo (versione free) non succede nulla. */
    fun init(p: Platform) {
        if (platform != null) return
        platform = p
        p.readAsset("furniture/catalog.json")?.let { runCatching { FurnitureCatalog.load(it.decodeToString()) } }
        p.readAsset("materials/materials.json")?.let { runCatching { MaterialCatalog.load(it.decodeToString()) } }
        FurnitureCatalog.addRugs(MaterialCatalog.items)
    }

    /** Campione di un materiale fotografico, per sceglierlo. */
    fun materialThumb(id: String): ImageBitmap? = materialThumbs.getOrPut(id) { read("materials/${id}_thumb.jpg") }

    private fun read(path: String): ImageBitmap? {
        val p = platform ?: return null
        return p.readAsset(path)?.let { runCatching { p.decodeImage(it) }.getOrNull() }
    }

    /** Miniatura in prospettiva, per il catalogo (per i tappeti di materiale, il campione del materiale). */
    fun thumbnail(model: String): ImageBitmap? = FurnitureCatalog.rugOf(model)?.let { materialThumb(it.first) }
        ?: thumbs.getOrPut(model) { read("furniture/$model.png") }

    /** Vista dall'alto, per la pianta (il davanti del mobile in basso). */
    fun top(model: String): ImageBitmap? = FurnitureCatalog.rugOf(model)?.let { materialThumb(it.first) }
        ?: tops.getOrPut(model) { read("furniture/${model}_top.png") }
}
