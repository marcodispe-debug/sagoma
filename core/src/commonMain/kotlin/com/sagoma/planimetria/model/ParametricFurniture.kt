package com.sagoma.planimetria.model

/**
 * Mobili GENERATI alla misura (nessun file 3D), oltre ai moduli della cucina ([KitchenModules]): armadi, librerie, tavoli, letti,
 * comodini, cassettiere, scrivanie, madie, mobili TV e mensole. Il mobile è tutto nel nome del modello
 * "mobile:<tipo>:<finitura>"; le misure sono quelle dell'arredo. Allargandolo cambiano il numero di ante, cassetti e ripiani,
 * niente si stira. Il formato del progetto non cambia. I codici finiscono nei file salvati: si possono aggiungere, non rinominare.
 */
object ParametricFurniture {
    const val PREFIX = "mobile:"

    enum class Kind(
        val code: String,
        val category: String,
        val symbol: FurnitureSymbol,
        val width: Double,
        val depth: Double,
        val height: Double,
        val elevation: Double = 0.0,
    ) {
        WARDROBE("armadio", "Camera", FurnitureSymbol.Cabinet, 150.0, 60.0, 240.0),
        SLIDING_WARDROBE("armadio-scorrevole", "Camera", FurnitureSymbol.Cabinet, 200.0, 65.0, 240.0),
        BED("letto", "Camera", FurnitureSymbol.Bed, 160.0, 205.0, 100.0),
        SINGLE_BED("letto-singolo", "Camera", FurnitureSymbol.Bed, 90.0, 205.0, 90.0),
        NIGHTSTAND("comodino", "Camera", FurnitureSymbol.Cabinet, 50.0, 40.0, 50.0),
        CHEST("cassettiera", "Camera", FurnitureSymbol.Cabinet, 100.0, 50.0, 85.0),
        BOOKCASE("libreria", "Soggiorno", FurnitureSymbol.Cabinet, 100.0, 35.0, 200.0),
        SIDEBOARD("madia", "Soggiorno", FurnitureSymbol.Cabinet, 160.0, 45.0, 80.0),
        TV_UNIT("mobile-tv", "Soggiorno", FurnitureSymbol.Tv, 180.0, 42.0, 45.0),
        SHELF("mensola", "Soggiorno", FurnitureSymbol.Cabinet, 80.0, 22.0, 3.0, elevation = 140.0),
        TABLE("tavolo", "Pranzo", FurnitureSymbol.Table, 160.0, 90.0, 75.0),
        DESK("scrivania", "Studio", FurnitureSymbol.Desk, 120.0, 60.0, 75.0),
        ;

        /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
        val label: String get() = com.sagoma.planimetria.testi.T.nomi.furnitureKind(this)
    }

    /** Finitura: le stesse tinte delle ante della cucina (legni e laccati). */
    val finishes: List<KitchenModules.Front> get() = KitchenModules.Front.entries

    data class Spec(val kind: Kind, val finish: KitchenModules.Front = KitchenModules.Front.OAK)

    fun isModel(model: String): Boolean = model.startsWith(PREFIX)

    fun parse(model: String): Spec? {
        if (!isModel(model)) return null
        val parts = model.removePrefix(PREFIX).split(':')
        val kind = Kind.entries.firstOrNull { it.code == parts.getOrNull(0) } ?: return null
        val finish = KitchenModules.Front.entries.firstOrNull { it.code == parts.getOrNull(1) } ?: KitchenModules.Front.OAK
        return Spec(kind, finish)
    }

    fun model(spec: Spec): String = "$PREFIX${spec.kind.code}:${spec.finish.code}"

    fun label(spec: Spec): String = spec.kind.label + " · " + spec.finish.label

    fun item(model: String): FurnitureCatalog.Item? = parse(model)?.let { s ->
        FurnitureCatalog.Item(
            model = model(s),
            label = label(s),
            category = s.kind.category,
            symbol = s.kind.symbol.name,
            width = s.kind.width,
            depth = s.kind.depth,
            height = s.kind.height,
            elevation = s.kind.elevation,
            source = "Sagoma (generato)",
            author = "Sagoma",
            license = "CC0",
        )
    }

    /** Voci del catalogo: un mobile per tipo, in rovere chiaro, col solo nome del tipo. */
    val catalogItems: List<FurnitureCatalog.Item> get() = Kind.entries.mapNotNull { k -> item(model(Spec(k)))?.copy(label = k.label) }
}

/** Mobili generati alla misura, di qualsiasi famiglia, e oggetti forniti dai professionisti: non hanno file da scaricare. */
object GeneratedFurniture {
    fun isGenerated(model: String): Boolean = KitchenModules.isModule(model) || ParametricFurniture.isModel(model) || SuppliedObjects.isModel(model)
}
