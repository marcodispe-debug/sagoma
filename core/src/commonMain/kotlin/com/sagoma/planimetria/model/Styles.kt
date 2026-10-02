package com.sagoma.planimetria.model

import kotlinx.serialization.Serializable

/*
 * Modelli e finiture degli elementi (porte, finestre, scale, termosifoni, lampadari, pavimenti, pareti).
 * I nomi delle costanti finiscono nel file salvato: si possono aggiungere, non rinominare.
 * Il primo valore di ogni elenco è quello che avevano gli elementi prima che esistessero i modelli.
 */

/** Modello dell'anta di una porta. */
enum class DoorModel(val label: String) {
    Flush("Liscia"),
    Panels("A pannelli"),
    Glazed("Con vetro"),
    Planks("A doghe"),
    Modern("Moderna"),
}

/** Colore di ante, telai e legni. `argb`: colore opaco. */
enum class Finish(val label: String, val argb: Long) {
    Oak("Rovere", 0xFF9E7148),
    White("Bianco", 0xFFF4F3F0),
    LightOak("Rovere chiaro", 0xFFC9A77C),
    Walnut("Noce", 0xFF5E3B24),
    Grey("Grigio", 0xFF8D9095),
    Anthracite("Antracite", 0xFF383B3F),
}

/** Modello di finestre e balconi. */
enum class WindowModel(val label: String) {
    Classic("Classica"),
    Minimal("Minimal"),
    English("All'inglese"),
}

/** Oscuranti di finestre e balconi, all'esterno. */
enum class Shading(val label: String) {
    None("Nessuno"),
    Shutters("Persiane"),
    RollerShutter("Tapparella"),
}

/** Struttura della scala (la chiocciola ha sempre il palo centrale). */
enum class StairStructure(val label: String) {
    Masonry("Muratura"),
    Floating("A sbalzo"),
    Stringers("A giorno"),
}

/** Materiale dei gradini. */
enum class StairMaterial(val label: String, val argb: Long) {
    Wood("Legno", 0xFFCCAD8A),
    DarkWood("Legno scuro", 0xFF6B4A33),
    Marble("Marmo", 0xFFEDEBE6),
    Stone("Pietra", 0xFF9A9892),
    Concrete("Cemento", 0xFFB8B6B0),
}

/** Ringhiera della scala, sui lati liberi (non contro i muri). */
enum class StairRailing(val label: String) {
    None("Nessuna"),
    Metal("Metallo"),
    Glass("Vetro"),
    Wood("Legno"),
}

/** Modello di termosifone. */
enum class RadiatorModel(val label: String) {
    Panel("A piastra"),
    Fins("A elementi"),
    TowelRail("Scaldasalviette"),
}

/** Modello di lampadario. */
enum class LampModel(val label: String) {
    Modern("Moderno"),
    Classic("Classico"),
    Bell("A campana"),
    Industrial("Industriale"),
}

/** Pavimento della stanza. `argb` null: il colore del tipo di stanza (come prima). `pattern`: disegno delle fughe. */
enum class FloorFinish(val label: String, val argb: Long?, val pattern: FloorPattern) {
    RoomColor("Colore stanza", null, FloorPattern.None),
    OakParquet("Parquet rovere", 0xFFC8A276, FloorPattern.Planks),
    WalnutParquet("Parquet noce", 0xFF7A5436, FloorPattern.Planks),
    LightTiles("Gres chiaro", 0xFFE7E3DC, FloorPattern.Tiles),
    GreyTiles("Gres grigio", 0xFFA7A9AC, FloorPattern.Tiles),
    Marble("Marmo", 0xFFF1EFEA, FloorPattern.LargeTiles),
    Terracotta("Cotto", 0xFFC1683F, FloorPattern.Tiles),
}

enum class FloorPattern { None, Planks, Tiles, LargeTiles }

/** Colore delle pareti della stanza. */
enum class WallPaint(val label: String, val argb: Long) {
    White("Bianco", 0xFFF0EEE8),
    Ivory("Avorio", 0xFFF3EAD6),
    PearlGrey("Grigio perla", 0xFFD9DADB),
    Sage("Salvia", 0xFFC9D3BF),
    Sky("Azzurro", 0xFFCADCE6),
    Sand("Sabbia", 0xFFE3D2B8),
    Terracotta("Terracotta", 0xFFD9A88C),
    // Colori da facciata (vanno bene anche dentro).
    Yellow("Giallo", 0xFFEBD49A),
    Salmon("Rosa antico", 0xFFE3B7A5),
    Brick("Mattone", 0xFFB5654A),
    Stone("Pietra", 0xFFBDB5A6),
    Anthracite("Antracite", 0xFF5B5F64),
}

/**
 * Rivestimento di una parete (versione pro), disegnato nel 3D con le sue fughe. `defaultHeight`: fin dove
 * arriva da terra se l'utente non lo cambia (null = tutta la parete). I nomi finiscono nel file salvato.
 */
enum class Covering(val label: String, val defaultArgb: Long, val defaultHeight: Double?) {
    None("Solo pittura", 0xFFF0EEE8, null),
    Wallpaper("Carta da parati", 0xFFC9B48E, null),
    Tiles("Piastrelle 20×20", 0xFFF4F4F0, 120.0),
    Metro("Piastrelle diamantate", 0xFFEDEFEF, 120.0),
    Brick("Mattoni a vista", 0xFFB0603F, null),
    Stone("Pietra", 0xFFB9AE9A, null),
    Wainscot("Boiserie", 0xFFF3F0E8, 100.0),
    Boards("Perlinato", 0xFFD1AD7E, 110.0),
    /** Materiale fotografico della libreria (versione pro): vedi [WallFinish.material]. */
    Material("Materiale fotografico", 0xFFC8C2B8, null),
}

/**
 * Finitura libera di una parete (versione pro): colore qualsiasi della pittura e, se c'è, rivestimento
 * con il suo colore e la sua altezza da terra (null = quella tipica del rivestimento).
 */
@Serializable
data class WallFinish(
    val argb: Long,
    val covering: Covering = Covering.None,
    val coveringArgb: Long? = null,
    val coveringHeight: Double? = null,
    /** Con [Covering.Material]: il materiale della libreria ([MaterialCatalog]). */
    val material: String? = null,
) {
    val coverArgb: Long get() = coveringArgb ?: (if (covering == Covering.Material) material?.let { MaterialCatalog.item(it)?.argb } else null) ?: covering.defaultArgb
    /** Fin dove arriva il rivestimento (null = tutta la parete). */
    val coverHeight: Double? get() = if (covering == Covering.None) null else (coveringHeight ?: covering.defaultHeight)?.takeIf { it < FULL_HEIGHT }

    companion object {
        /** Valore di [coveringHeight] che vuol dire "tutta la parete". */
        const val FULL_HEIGHT = 1000.0

        fun of(paint: WallPaint) = WallFinish(paint.argb)
    }
}

/** Tavolozza estesa della versione pro: bianchi, neutri, pastelli, terre, colori decisi. */
object ProPalette {
    val colors: List<Pair<String, Long>> = WallPaint.entries.map { it.label to it.argb } + listOf(
        "Bianco puro" to 0xFFFAFAF8, "Latte" to 0xFFF5F1E8, "Panna" to 0xFFF1E6CF, "Lino" to 0xFFE8DECB,
        "Tortora" to 0xFFBFB2A3, "Greige" to 0xFFCBC3B6, "Cemento" to 0xFFA9A9A4, "Ardesia" to 0xFF6E7378,
        "Grafite" to 0xFF3F4246, "Nero" to 0xFF26272A,
        "Cipria" to 0xFFEBCFC8, "Rosa" to 0xFFE8B4B8, "Lavanda" to 0xFFD2CBE3, "Glicine" to 0xFFB9A6D1,
        "Menta" to 0xFFCDE6D6, "Acquamarina" to 0xFFA9D6D0, "Cielo" to 0xFFB8D4EA, "Polvere" to 0xFF9DB3C4,
        "Ocra" to 0xFFD9A441, "Senape" to 0xFFC89F3A, "Mattone chiaro" to 0xFFCB8467, "Ruggine" to 0xFF9E4B2D,
        "Bordeaux" to 0xFF6E2632, "Rosso" to 0xFFB5332E, "Corallo" to 0xFFE9806B, "Pesca" to 0xFFF2C0A0,
        "Oliva" to 0xFF8B8B55, "Bosco" to 0xFF3E5E44, "Smeraldo" to 0xFF2E7D63, "Petrolio" to 0xFF22565E,
        "Blu notte" to 0xFF243A5E, "Blu" to 0xFF3C6FA8, "Avio" to 0xFF7E9CB9, "Tabacco" to 0xFF7A5A3C,
        "Cioccolato" to 0xFF4E3629, "Caffelatte" to 0xFFB39377,
    )

    fun nameOf(argb: Long): String = colors.firstOrNull { it.second == argb }?.first ?: "#" + com.sagoma.planimetria.geometry.hex6(argb)
}
