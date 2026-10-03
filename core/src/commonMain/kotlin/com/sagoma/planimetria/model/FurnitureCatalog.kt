package com.sagoma.planimetria.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Come si disegna un arredo nella pianta 2D quando manca la sua vista dall'alto. */
enum class FurnitureSymbol { Bed, Sofa, CornerSofa, Armchair, Table, RoundTable, Chair, Cabinet, Appliance, Hob, Sink, Toilet, Tub, Shower, Rug, RoundRug, Plant, Lamp, Desk, Tv }

/**
 * Catalogo degli arredi della versione pro, letto da `assets/furniture/catalog.json` (preparato con
 * `tools/furniture/PackModels.java`). Per ogni modello ci sono `<model>.glb` (3D), `<model>.png`
 * (miniatura) e `<model>_top.png` (vista dall'alto per la pianta).
 * Fonti: Poly Haven (CC0) e le librerie di Sweet Home 3D (pubblico dominio e CC-BY): autore e licenza
 * di ogni modello sono nel catalogo e si vedono nei crediti.
 * I nomi dei modelli finiscono nel file salvato: si possono aggiungere, non rinominare.
 */
object FurnitureCatalog {
    @Serializable
    data class Item(
        val model: String,
        val label: String,
        val category: String,
        val symbol: String = "Cabinet",
        /** Misure reali del modello (cm): il mobile nasce così, poi si può cambiare. */
        val width: Double,
        val depth: Double,
        val height: Double,
        /** Altezza da terra tipica (quadri, specchi, applique, oggetti su un mobile). */
        val elevation: Double = 0.0,
        /** Appeso al soffitto: si mette subito sotto il soffitto della stanza. */
        val ceiling: Boolean = false,
        val source: String = "",
        val author: String = "",
        val license: String = "",
    ) {
        val symbolKind: FurnitureSymbol get() = FurnitureSymbol.entries.firstOrNull { it.name == symbol } ?: FurnitureSymbol.Cabinet
    }

    /**
     * Modelli del primo catalogo (Kenney Furniture Kit, poi tolto) e il loro equivalente attuale: le piante
     * salvate allora si aggiornano da sole all'apertura (vedi PlanStore).
     */
    val RENAMED: Map<String, String> = mapOf(
        "loungeSofa" to "ob_9b1e09abe5e34d63", "loungeSofaLong" to "ob_9cef8fd19dd84684",
        "loungeSofaCorner" to "sh_blend_swap_cc_0_l_shaped_sofa", "loungeDesignSofa" to "ob_42da0122f2134a18",
        "loungeDesignSofaCorner" to "ob_9f5156a82f2f4d0a", "loungeChair" to "ph_armchair_01",
        "loungeChairRelax" to "ob_d3aaffde4b3f4af2", "loungeDesignChair" to "ph_modern_arm_chair_01",
        "loungeSofaOttoman" to "ph_ottoman_01", "tableCoffee" to "ph_modern_coffee_table_01",
        "tableCoffeeGlass" to "ph_modern_coffee_table_02", "tableCoffeeSquare" to "ph_industrial_coffee_table",
        "cabinetTelevision" to "sh_blend_swap_cc_0_televisioncabinet", "cabinetTelevisionDoors" to "sh_blend_swap_cc_0_televisioncabinet",
        "televisionModern" to "sh_blend_swap_cc_0_led_tv", "bookcaseOpen" to "ph_shelf_01",
        "bookcaseClosedWide" to "ob_3c782b0787cc41f1", "bookcaseOpenLow" to "ph_painted_wooden_shelves",
        "table" to "ob_132b635ab00748ab", "tableCloth" to "ph_dining_table", "tableGlass" to "sh_scopia_metal_glass_table",
        "tableCross" to "ph_woodentable_01", "chair" to "ph_dining_chair_02", "chairCushion" to "sh_blend_swap_cc_0_chairwithcushion",
        "chairModernCushion" to "ob_99fc178ecc0943ae", "chairRounded" to "ob_d601c2907f114376", "stoolBar" to "ph_bar_chair_round_01",
        "kitchenCabinet" to "sh_blend_swap_cc_0_lowercabinet", "kitchenCabinetDrawer" to "sh_blend_swap_cc_0_drawerscabinet",
        "kitchenCabinetCornerInner" to "sh_blend_swap_cc_0_lowercornercabinet", "kitchenSink" to "sh_blend_swap_cc_0_sinkcabinet2",
        "kitchenStove" to "ob_7c5c9dec5c2e4ff9", "kitchenStoveElectric" to "sh_scopia_induction_cooker_4_zones",
        "kitchenCabinetUpper" to "sh_blend_swap_cc_0_uppercabinet", "kitchenCabinetUpperDouble" to "sh_blend_swap_cc_0_uppercabinet",
        "hoodModern" to "sh_blend_swap_cc_0_rangehood", "kitchenFridge" to "sh_blend_swap_cc_0_refrigerator",
        "kitchenFridgeLarge" to "sh_blend_swap_cc_0_largefridge", "kitchenFridgeBuiltIn" to "sh_blend_swap_cc_0_highcabinet",
        "kitchenBar" to "sh_blend_swap_cc_0_islandextension", "kitchenMicrowave" to "ob_c4a85fc7317f4594",
        "kitchenCoffeeMachine" to "sh_scopia_coffee_machine", "washer" to "sh_scopia_clothes_washing_machine",
        "dryer" to "sh_scopia_dryer_machine", "washerDryerStacked" to "ob_056c4e7895834a4e",
        "bedDouble" to "ob_a2b2645701c94fa4", "bedSingle" to "sh_scopia_captains_bed", "bedBunk" to "ob_c505ffffc1524865",
        "cabinetBedDrawer" to "ph_classicnightstand_01", "cabinetBedDrawerTable" to "ph_classicnightstand_01",
        "bookcaseClosedDoors" to "sh_blend_swap_cc_0_cupboard", "sideTableDrawers" to "ob_a8c06fd4d1a34c60",
        "coatRackStanding" to "sh_scopia_clothes_rack", "toiletSquare" to "ob_389d5427a1f344c4",
        "bathroomSink" to "sh_blend_swap_cc_0_washbasin", "bathroomSinkSquare" to "sh_blend_swap_cc_0_modernvanity",
        "bathtub" to "ob_800d3c5569b94abf", "shower" to "sh_blend_swap_cc_0_showerstall",
        "showerRound" to "sh_scopia_shower_cabin_with_seat", "bathroomMirror" to "ob_a3ad19a9186b445b",
        "bathroomCabinet" to "sh_blend_swap_cc_0_uppercabinet", "bathroomCabinetDrawer" to "sh_blend_swap_cc_0_modernvanity",
        "desk" to "sh_blend_swap_cc_0_desk", "deskCorner" to "sh_blend_swap_cc_0_lbdesk", "chairDesk" to "ob_3177fdf2630c4922",
        "bookcaseClosed" to "sh_scopia_office_furniture1", "rugRound" to "sh_scopia_round_carpet",
        "plantSmall1" to "ph_potted_plant_02", "plantSmall2" to "ph_potted_plant_01", "plantSmall3" to "ph_potted_plant_04",
        "lampRoundFloor" to "ob_33eb258d98734356", "lampSquareFloor" to "ob_20e01053fa0a4933",
        "lampRoundTable" to "ob_2fd7954d31dc47d7", "lampSquareCeiling" to "sh_scopia_ceiling_lamp", "ceilingFan" to "ph_ceiling_fan",
    )

    /** Ordine delle categorie nel catalogo (quelle non elencate vanno in fondo). */
    private val categoryOrder = listOf("Soggiorno", "Pranzo", "Cucina", "Camera", "Bagno", "Studio", "Luci", "Tappeti", "Elettrodomestici e TV", "Decori", "Piante", "Esterni")

    var items: List<Item> = emptyList()
        private set(value) {
            field = value
            byModel = null
        }

    /** Indice per modello, costruito alla prima ricerca dopo ogni cambio del catalogo (a ogni disegno si cerca un arredo). */
    private var byModel: Map<String, Item>? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** Carica il catalogo (JSON: elenco di [Item]). */
    fun load(text: String) {
        items = json.decodeFromString<List<Item>>(text)
    }

    /**
     * Tappeti fatti con i materiali fotografici (tessuti, moquette, vimini): rettangolari e tondi. Non hanno un
     * file 3D: il modello è "tappeto:<materiale>:r" (rettangolo) o ":o" (tondo) e lo disegna la scena con la foto.
     */
    fun addRugs(materials: List<MaterialCatalog.Item>) {
        val rugs = materials.filter { it.forRug }.flatMap { m ->
            val name = m.label.replaceFirstChar { it.lowercase() }
            listOf(
                Item("$RUG${m.id}:r", "Tappeto in $name", "Tappeti", "Rug", 200.0, 140.0, 1.5, source = m.source, author = m.source, license = m.license),
                Item("$RUG${m.id}:o", "Tappeto tondo in $name", "Tappeti", "RoundRug", 160.0, 160.0, 1.5, source = m.source, author = m.source, license = m.license),
            )
        }
        items = items.filterNot { it.model.startsWith(RUG) } + rugs
    }

    const val RUG = "tappeto:"

    /** Tappeto di materiale: (materiale, tondo) oppure null se il modello è un file 3D. */
    fun rugOf(model: String): Pair<String, Boolean>? {
        if (!model.startsWith(RUG)) return null
        val rest = model.removePrefix(RUG)
        return rest.substringBeforeLast(':') to rest.endsWith(":o")
    }

    /** Per le prove: catalogo scelto a mano. */
    fun set(list: List<Item>) {
        items = list
    }

    val categories: List<String>
        get() = items.map { it.category }.distinct().sortedBy { c -> categoryOrder.indexOf(c).let { if (it < 0) Int.MAX_VALUE else it } }

    fun item(model: String): Item? {
        val index = byModel ?: HashMap<String, Item>(items.size * 2).also { m ->
            // Se un modello comparisse due volte vale il primo, come con la ricerca in elenco.
            for (i in items) if (i.model !in m) m[i.model] = i
            byModel = m
        }
        return index[model]
    }

    /** Ricerca per nome o categoria (senza distinguere maiuscole). */
    fun search(query: String): List<Item> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { q in it.label.lowercase() || q in it.category.lowercase() }
    }
}
