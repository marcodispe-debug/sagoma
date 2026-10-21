package com.sagoma.planimetria.model

/**
 * Moduli della cucina componibile, GENERATI alla misura (nessun file 3D): basi, pensili e colonne con ante, cassetti ed
 * elettrodomestici a incasso. Come i tappeti di materiale, il modulo è tutto nel nome del modello
 * "cucina:<tipo>:<ante>:<piano>"; le misure sono quelle dell'arredo ([Furniture.width], [Furniture.depth], [Furniture.height]).
 * Il formato del progetto non cambia: allargando un modulo le ante si ridisegnano, niente si stira.
 * I codici finiscono nei file salvati: si possono aggiungere, non rinominare.
 */
object KitchenModules {
    const val PREFIX = "cucina:"

    /** Categoria del catalogo (subito prima di "Cucina"). */
    const val CATEGORY = "Cucina componibile"

    /** Dove sta il modulo: a terra con piano di lavoro, appeso al muro, a colonna da terra fino ai pensili. */
    enum class Placement { BASE, WALL, TALL }

    enum class Kind(
        val code: String,
        val placement: Placement,
        val width: Double,
        val depth: Double,
        val height: Double,
        val elevation: Double = 0.0,
        val symbol: FurnitureSymbol = FurnitureSymbol.Cabinet,
    ) {
        BASE_DOORS("base", Placement.BASE, 60.0, 60.0, 90.0),
        BASE_DRAWERS("cassetti", Placement.BASE, 60.0, 60.0, 90.0),
        SINK("lavello", Placement.BASE, 80.0, 60.0, 90.0, symbol = FurnitureSymbol.Sink),
        OVEN_HOB("forno", Placement.BASE, 60.0, 60.0, 90.0, symbol = FurnitureSymbol.Hob),
        DISHWASHER("lavastoviglie", Placement.BASE, 60.0, 60.0, 90.0),
        CORNER("angolo", Placement.BASE, 90.0, 60.0, 90.0),
        WALL_DOORS("pensile", Placement.WALL, 60.0, 35.0, 72.0, elevation = 145.0),
        WALL_HOOD("cappa", Placement.WALL, 60.0, 35.0, 72.0, elevation = 145.0),
        TALL_PANTRY("dispensa", Placement.TALL, 60.0, 60.0, 217.0),
        TALL_OVEN("colonna-forno", Placement.TALL, 60.0, 60.0, 217.0),
        TALL_FRIDGE("frigo", Placement.TALL, 60.0, 60.0, 217.0),
        ;

        /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
        val label: String get() = com.sagoma.planimetria.testi.T.nomi.kitchenKind(this)

        /** Ha il piano di lavoro (le basi). */
        val hasTop: Boolean get() = placement == Placement.BASE
    }

    /** Finitura di ante, cassetti, fianchi e zoccolo. */
    enum class Front(val code: String, val argb: Long) {
        WHITE("bianco", 0xFFF1F0EC),
        PEARL("perla", 0xFFC9C6C0),
        ANTHRACITE("antracite", 0xFF3E4246),
        SAGE("salvia", 0xFF9AA796),
        NAVY("blu", 0xFF2F3A4E),
        OAK("rovere", 0xFFC8A27A),
        WALNUT("noce", 0xFF6E4B32),
        ;

        /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
        val label: String get() = com.sagoma.planimetria.testi.T.nomi.kitchenFront(this)
    }

    /** Piano di lavoro: tinta unita o materiale fotografico del catalogo dei materiali ([material]). */
    enum class Top(val code: String, val argb: Long, val material: String? = null) {
        LAMINATE_WHITE("lam-bianco", 0xFFEDEBE7),
        LAMINATE_GREY("lam-grigio", 0xFF9C9A96),
        LAMINATE_BLACK("lam-nero", 0xFF2B2B2C),
        WOOD("legno", 0xFFB78B5E),
        CARRARA("carrara", 0xFFE4E2DF, "marble021"),
        STONE("pietra", 0xFFCBBFA9, "ph_marble_01"),
        CONCRETE("cemento", 0xFFA7A39C, "concrete034"),
        MARQUINIA("marquinia", 0xFF26262A, "marble006"),
        ;

        /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
        val label: String get() = com.sagoma.planimetria.testi.T.nomi.kitchenTop(this)
    }

    data class Spec(val kind: Kind, val front: Front = Front.WHITE, val top: Top = Top.LAMINATE_WHITE)

    fun isModule(model: String): Boolean = model.startsWith(PREFIX)

    /** Il modulo descritto da `model`, oppure null se non è un modulo della cucina (o un codice sconosciuto). */
    fun parse(model: String): Spec? {
        if (!isModule(model)) return null
        val parts = model.removePrefix(PREFIX).split(':')
        val kind = Kind.entries.firstOrNull { it.code == parts.getOrNull(0) } ?: return null
        val front = Front.entries.firstOrNull { it.code == parts.getOrNull(1) } ?: Front.WHITE
        val top = Top.entries.firstOrNull { it.code == parts.getOrNull(2) } ?: Top.LAMINATE_WHITE
        return Spec(kind, front, top)
    }

    fun model(spec: Spec): String = "$PREFIX${spec.kind.code}:${spec.front.code}:${spec.top.code}"

    /** Nome leggibile: tipo e finiture (il piano solo per le basi). */
    fun label(spec: Spec): String =
        spec.kind.label + " · " + spec.front.label + if (spec.kind.hasTop) com.sagoma.planimetria.testi.T.nomi.pianoCucina + spec.top.label.replaceFirstChar { it.lowercase() } else ""

    /** Voce del catalogo per un modulo (anche con finiture diverse da quelle proposte nel catalogo). */
    fun item(model: String): FurnitureCatalog.Item? = parse(model)?.let { s ->
        FurnitureCatalog.Item(
            model = model(s),
            label = label(s),
            category = CATEGORY,
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

    /**
     * Le voci del catalogo: un modulo per tipo, col solo nome del tipo, bianco con piano in laminato bianco (le finiture si
     * cambiano dopo, anche per tutta la cucina insieme).
     */
    val catalogItems: List<FurnitureCatalog.Item> get() = Kind.entries.mapNotNull { k -> item(model(Spec(k)))?.copy(label = k.label) }
}
