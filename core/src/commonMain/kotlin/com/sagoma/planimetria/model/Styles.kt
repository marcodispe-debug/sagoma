package com.sagoma.planimetria.model

import kotlinx.serialization.Serializable

/*
 * Modelli e finiture degli elementi (porte, finestre, scale, termosifoni, lampadari, pavimenti, pareti).
 * I nomi delle costanti finiscono nel file salvato: si possono aggiungere, non rinominare.
 * Il primo valore di ogni elenco è quello che avevano gli elementi prima che esistessero i modelli.
 */

/** Modello dell'anta di una porta. */
enum class DoorModel {
    Flush,
    Panels,
    Glazed,
    Planks,
    Modern,
    /** Blindata: anta spessa con le bugne, serratura e spioncino. */
    Armored,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.doorModel(this)
}

/** Colore di ante, telai e legni. `argb`: colore opaco. */
enum class Finish(val argb: Long) {
    Oak(0xFF9E7148),
    White(0xFFF4F3F0),
    LightOak(0xFFC9A77C),
    Walnut(0xFF5E3B24),
    Grey(0xFF8D9095),
    Anthracite(0xFF383B3F),
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.finish(this)
}

/** Modello di finestre e balconi. */
enum class WindowModel {
    Classic,
    Minimal,
    English,
    /** Vetrata stile loft: profili neri sottili e vetro diviso a riquadri. */
    Loft,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.windowModel(this)
}

/** Come si apre l'anta di finestre e balconi (quando non sono scorrevoli). */
enum class SashType {
    Casement,
    TiltTurn,
    Hopper,
    Fixed,
    /** A bilico: gira su un perno orizzontale a metà altezza. */
    Pivot,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.sashType(this)
}

/** Dove sta l'infisso di finestre e balconi nello spessore del muro. */
enum class FramePosition {
    Center,
    Outside,
    Inside,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.framePosition(this)
}

/** Oscuranti di finestre e balconi: all'esterno, tranne gli scuri interni. */
enum class Shading {
    None,
    Shutters,
    RollerShutter,
    /** Veneziana esterna (frangisole a lamelle orientabili), abbassata per due terzi. */
    Venetian,
    /** Scuri interni: ante di legno sulla faccia del muro verso la stanza, aperte ai lati della finestra. */
    InteriorShutters,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.shading(this)
}

/** Struttura della scala (la chiocciola ha sempre il palo centrale). */
enum class StairStructure {
    Masonry,
    Floating,
    Stringers,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.stairStructure(this)
}

/** Materiale dei gradini. */
enum class StairMaterial(val argb: Long) {
    Wood(0xFFCCAD8A),
    DarkWood(0xFF6B4A33),
    Marble(0xFFEDEBE6),
    Stone(0xFF9A9892),
    Concrete(0xFFB8B6B0),
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.stairMaterial(this)
}

/** Ringhiera della scala, sui lati liberi (non contro i muri). */
enum class StairRailing {
    None,
    Metal,
    Glass,
    Wood,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.stairRailing(this)
}

/** Modello di termosifone. */
enum class RadiatorModel {
    Panel,
    Fins,
    TowelRail,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.radiatorModel(this)
}

/** Modello di lampadario. */
enum class LampModel {
    Modern,
    Classic,
    Bell,
    Industrial,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.lampModel(this)
}

/** Pavimento della stanza. `argb` null: il colore del tipo di stanza (come prima). `pattern`: disegno delle fughe. */
enum class FloorFinish(val argb: Long?, val pattern: FloorPattern) {
    RoomColor(null, FloorPattern.None),
    OakParquet(0xFFC8A276, FloorPattern.Planks),
    WalnutParquet(0xFF7A5436, FloorPattern.Planks),
    LightTiles(0xFFE7E3DC, FloorPattern.Tiles),
    GreyTiles(0xFFA7A9AC, FloorPattern.Tiles),
    Marble(0xFFF1EFEA, FloorPattern.LargeTiles),
    Terracotta(0xFFC1683F, FloorPattern.Tiles),
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.floorFinish(this)
}

enum class FloorPattern { None, Planks, Tiles, LargeTiles }

/** Colore delle pareti della stanza. */
enum class WallPaint(val argb: Long) {
    White(0xFFF0EEE8),
    Ivory(0xFFF3EAD6),
    PearlGrey(0xFFD9DADB),
    Sage(0xFFC9D3BF),
    Sky(0xFFCADCE6),
    Sand(0xFFE3D2B8),
    Terracotta(0xFFD9A88C),
    // Colori da facciata (vanno bene anche dentro).
    Yellow(0xFFEBD49A),
    Salmon(0xFFE3B7A5),
    Brick(0xFFB5654A),
    Stone(0xFFBDB5A6),
    Anthracite(0xFF5B5F64),
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.wallPaint(this)
}

/**
 * Rivestimento di una parete, disegnato nel 3D con le sue fughe. `defaultHeight`: fin dove
 * arriva da terra se l'utente non lo cambia (null = tutta la parete). I nomi finiscono nel file salvato.
 */
enum class Covering(val defaultArgb: Long, val defaultHeight: Double?) {
    None(0xFFF0EEE8, null),
    Wallpaper(0xFFC9B48E, null),
    Tiles(0xFFF4F4F0, 120.0),
    Metro(0xFFEDEFEF, 120.0),
    Brick(0xFFB0603F, null),
    Stone(0xFFB9AE9A, null),
    Wainscot(0xFFF3F0E8, 100.0),
    Boards(0xFFD1AD7E, 110.0),
    /** Materiale fotografico della libreria: vedi [WallFinish.material]. */
    Material(0xFFC8C2B8, null),
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.covering(this)
}

/**
 * Finitura libera di una parete: colore qualsiasi della pittura e, se c'è, rivestimento
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
    /** Con [Covering.Material]: verso e misura dei pezzi. */
    val layout: MaterialLayout = MaterialLayout(),
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

/**
 * Posa di un materiale fotografico ([MaterialCatalog]) su un pavimento o su una parete: `rotation` gira i pezzi (gradi: 90 =
 * doghe nell'altro verso, 45 = in diagonale), `scale` li ingrandisce rispetto alla foto del catalogo (1,5 = doghe o
 * piastrelle più grandi del 50%). Con `laying` i pezzi si posano uno per uno, con la fuga ([Laying]); `scale` allora non conta.
 */
@Serializable
data class MaterialLayout(val rotation: Double = 0.0, val scale: Double = 1.0, val laying: Laying? = null) {
    /** Coordinate nella foto del punto (`x`, `y`) cm del pavimento o della parete, con la foto che si ripete ogni `size` cm. */
    fun uv(x: Double, y: Double, size: Double): Vec2 {
        val r = rotation * kotlin.math.PI / 180
        val c = kotlin.math.cos(r)
        val s = kotlin.math.sin(r)
        val k = size * scale.coerceIn(MIN_SCALE, MAX_SCALE)
        return Vec2((x * c + y * s) / k, (y * c - x * s) / k)
    }

    companion object {
        const val MIN_SCALE = 0.25
        const val MAX_SCALE = 3.0
    }
}

/** Schema di posa dei pezzi: le file corrono lungo il verso scelto ([MaterialLayout.rotation]). */
@Serializable
enum class LayingPattern {
    /** In fila, fughe allineate nei due versi. */
    Dritta,
    /** File sfalsate di mezzo pezzo (a mattoncino). */
    Sfalsata,
    /** File sfalsate di un terzo di pezzo. */
    Terzi,
    /** File sfalsate a caso, mai con le giunte vicine a quelle della fila accanto (il parquet «a correre»). */
    ACorrere,
    /** Spina di pesce: pezzi ad angolo retto, a 45° rispetto al verso. */
    SpinaDiPesce,
    /** A cesto: quadrati di pezzi affiancati, girati di 90° uno sì e uno no. */
    Cesto;

    val label: String get() = com.sagoma.planimetria.testi.T.finiture.schemaPosa(this)
}

/** Da dove parte la posa: dal centro della stanza (tagli uguali ai lati) o da un angolo (pezzi interi da quel lato). */
@Serializable
enum class LayingStart { Centro, Angolo }

/**
 * Posa a pezzi di un materiale: pezzi `width` × `length` cm (lato corto e lato lungo) posati con lo schema `pattern`,
 * fughe larghe `joint` cm del colore `jointArgb`. Ogni pezzo mostra un pezzo vero della foto ([MaterialPieces]).
 */
@Serializable
data class Laying(
    val pattern: LayingPattern = LayingPattern.Dritta,
    val width: Double = 60.0,
    val length: Double = 60.0,
    val joint: Double = 0.3,
    val jointArgb: Long = 0xFFB9B4AA,
    val start: LayingStart = LayingStart.Centro,
) {
    companion object {
        const val MIN_SIDE = 2.0
        const val MAX_SIDE = 300.0
        const val MAX_JOINT = 2.0
    }
}

/** Tavolozza estesa: bianchi, neutri, pastelli, terre, colori decisi. */
object ProPalette {
    /** Colori oltre a quelli di [WallPaint]; i nomi sono nei testi ([com.sagoma.planimetria.testi.Nomi.tavolozza]). */
    private val extra: List<Long> = listOf(
        0xFFFAFAF8, 0xFFF5F1E8, 0xFFF1E6CF, 0xFFE8DECB, 0xFFBFB2A3, 0xFFCBC3B6,
        0xFFA9A9A4, 0xFF6E7378, 0xFF3F4246, 0xFF26272A, 0xFFEBCFC8, 0xFFE8B4B8,
        0xFFD2CBE3, 0xFFB9A6D1, 0xFFCDE6D6, 0xFFA9D6D0, 0xFFB8D4EA, 0xFF9DB3C4,
        0xFFD9A441, 0xFFC89F3A, 0xFFCB8467, 0xFF9E4B2D, 0xFF6E2632, 0xFFB5332E,
        0xFFE9806B, 0xFFF2C0A0, 0xFF8B8B55, 0xFF3E5E44, 0xFF2E7D63, 0xFF22565E,
        0xFF243A5E, 0xFF3C6FA8, 0xFF7E9CB9, 0xFF7A5A3C, 0xFF4E3629, 0xFFB39377,
    )

    val colors: List<Pair<String, Long>>
        get() = WallPaint.entries.map { it.label to it.argb } + extra.map { (com.sagoma.planimetria.testi.T.nomi.tavolozza[it] ?: "") to it }

    fun nameOf(argb: Long): String = colors.firstOrNull { it.second == argb }?.first ?: "#" + com.sagoma.planimetria.geometry.hex6(argb)
}
