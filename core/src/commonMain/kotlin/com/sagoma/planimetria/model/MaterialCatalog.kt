package com.sagoma.planimetria.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Materiali fotografici della versione pro (parquet, piastrelle, marmo, mattoni, intonaco, carta da
 * parati…), letti da `assets/materials/materials.json` (preparato con `tools/furniture/PackMaterials.java`).
 * Per ogni materiale ci sono `<id>_color.jpg`, `<id>_normal.jpg`, `<id>_orm.jpg` (ruvidità) e `<id>_thumb.jpg`.
 * Fonti: ambientCG e Poly Haven, licenza CC0. Gli id finiscono nel file salvato: non si rinominano.
 */
object MaterialCatalog {
    @Serializable
    data class Item(
        val id: String,
        val label: String,
        val category: String,
        /** Lato reale della foto (cm): la texture si ripete ogni `size` cm. */
        val size: Double = 100.0,
        /** Colore medio, per la pianta e per il disegno senza texture. */
        val argb: Long = 0xFFC8C2B8,
        /** Dove si usa: "pavimento", "parete" o "entrambi". */
        val use: String = "entrambi",
        val source: String = "",
        val license: String = "CC0",
    ) {
        val forFloor: Boolean get() = use == "pavimento" || use == "entrambi"
        val forWall: Boolean get() = use == "parete" || use == "entrambi"
        /** Buono per un tappeto (tessuti, moquette, vimini). */
        val forRug: Boolean get() = use == "tappeto" || category == "Moquette"
    }

    /**
     * Il materiale dell'erba che i renderer pro stendono attorno alla casa (e che quindi serve a ogni scena con una
     * pianta): una sola definizione, per chi lo disegna e per chi calcola gli asset da preparare ([com.sagoma.planimetria.assets.AssetPlanner]).
     */
    const val GRASS = "grass005"

    var items: List<Item> = emptyList()
        private set

    private val json = Json { ignoreUnknownKeys = true }

    fun load(text: String) {
        items = json.decodeFromString<List<Item>>(text)
    }

    fun set(list: List<Item>) {
        items = list
    }

    fun item(id: String?): Item? = id?.let { k -> items.firstOrNull { it.id == k } }

    val categories: List<String> get() = items.map { it.category }.distinct()
}
