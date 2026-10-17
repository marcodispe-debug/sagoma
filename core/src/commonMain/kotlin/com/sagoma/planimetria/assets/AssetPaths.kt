package com.sagoma.planimetria.assets

/**
 * I percorsi logici degli asset, relativi alla radice (oggi `app/src/pro/assets`). Stanno qui, e non sparsi
 * nei renderer e nell'interfaccia, perché sono il contratto tra il codice e chi fornisce i file: chi li
 * fornisce (cartella, APK, cache scaricata) può cambiare senza toccare chi li usa.
 */
object AssetPaths {
    const val FURNITURE_CATALOG = "furniture/catalog.json"
    const val MATERIAL_CATALOG = "materials/materials.json"

    /** Modello 3D (glTF binario) di un arredo. */
    fun furnitureModel(model: String) = "furniture/$model.glb"

    /** Miniatura in prospettiva, per il catalogo. */
    fun furnitureThumbnail(model: String) = "furniture/$model.png"

    /** Vista dall'alto, per la pianta. */
    fun furnitureTop(model: String) = "furniture/${model}_top.png"

    /** Mappa di un materiale fotografico: `kind` è `color`, `normal` o `orm`. */
    fun materialMap(id: String, kind: String) = "materials/${id}_$kind.jpg"

    /** Campione del materiale, per sceglierlo. */
    fun materialThumbnail(id: String) = "materials/${id}_thumb.jpg"

    /** Luce ambiente fotografata (`giorno`, `tramonto`, `interno`). */
    fun environment(name: String) = "env/$name.ibl"
}
