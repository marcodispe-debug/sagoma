package com.sagoma.planimetria.assets

/**
 * I percorsi logici degli asset, relativi alla radice (oggi `catalogo/src/main/assets`). Stanno qui, e non sparsi
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

    /**
     * Mappa di un materiale fotografico: `kind` è `color`, `normal` o `orm`. Le mappe 1K stanno con l'app, quelle più
     * definite solo sul server, in una cartella per risoluzione (`materials/2k/`, `materials/4k/`).
     */
    fun materialMap(id: String, kind: String, resolution: MaterialResolution = MaterialResolution.K1) =
        if (resolution.folder == null) "materials/${id}_$kind.jpg" else "materials/${resolution.folder}/${id}_$kind.jpg"

    /** Campione del materiale, per sceglierlo. */
    fun materialThumbnail(id: String) = "materials/${id}_thumb.jpg"

    /** Luce ambiente fotografata (`giorno`, `tramonto`, `interno`). */
    fun environment(name: String) = "env/$name.ibl"
}

/**
 * Definizione delle mappe dei materiali: 1K sul telefono (arrivano con l'app), 2K nel 3D del computer, 4K per i render.
 * Dove manca la mappa più definita si usa l'1K, che c'è sempre.
 */
enum class MaterialResolution(val side: Int, val folder: String?) {
    K1(1024, null),
    K2(2048, "2k"),
    K4(4096, "4k"),
}
