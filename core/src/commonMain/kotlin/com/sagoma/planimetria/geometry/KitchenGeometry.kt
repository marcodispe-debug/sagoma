package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.KitchenModules
import com.sagoma.planimetria.model.KitchenModules.Kind
import com.sagoma.planimetria.model.KitchenModules.Placement
import kotlin.math.min

/**
 * Geometria dei moduli della cucina ([KitchenModules]) in coordinate LOCALI del modulo:
 * - x lungo la larghezza, da −w/2 a w/2 (guardandolo dal davanti, −x è la sinistra);
 * - y in alto, da 0 (la base del modulo, cioè la sua quota da terra) a h;
 * - z dal retro (−d/2) al davanti (+d/2).
 * Zoccolo, piano, ante, fughe e maniglie hanno misure fisse in cm: allargando un modulo cambiano il numero e la larghezza delle
 * ante, niente si stira. La stessa disposizione dei frontali ([fronts]) serve al 3D, alla miniatura e alla pianta.
 */
object KitchenGeometry {
    /** Zoccolo, spessore del piano, spessore delle ante, fuga tra le ante, sporgenza del piano sulle ante (cm). */
    const val PLINTH = 10.0
    const val TOP = 4.0
    const val FRONT = 1.8
    const val GAP = 0.3
    const val OVERHANG = 1.0

    /** Oltre questa larghezza un vano ha due ante invece di una. */
    const val MAX_SINGLE_DOOR = 50.0

    enum class FrontKind { DOOR, DRAWER, PANEL, OVEN, MICROWAVE, HOOD }

    /** Dove sta la maniglia sul frontale: in alto (basi, colonne in basso), in basso (pensili), nessuna. */
    enum class HandleAt { TOP, BOTTOM, NONE }

    /** Un frontale nella vista dal davanti: da x0 a x1 e da y0 a y1 (cm, coordinate locali), già al netto delle fughe. */
    data class FrontPanel(val kind: FrontKind, val x0: Double, val x1: Double, val y0: Double, val y1: Double, val handle: HandleAt) {
        val width: Double get() = x1 - x0
        val height: Double get() = y1 - y0
    }

    /**
     * Parallelepipedo in coordinate locali, colore ARGB. [topMaterial]: materiale fotografico sulla faccia superiore (piani in
     * marmo o pietra), che si ripete ogni tanti cm quanto dice il catalogo dei materiali.
     */
    data class Part(
        val x0: Double, val x1: Double,
        val y0: Double, val y1: Double,
        val z0: Double, val z1: Double,
        val argb: Long,
        val topMaterial: String? = null,
    )

    private const val STEEL = 0xFFB7BABEL
    private const val STEEL_DARK = 0xFF8E9296L
    private const val GLASS_DARK = 0xFF1C1F22L
    private const val HOB_GLASS = 0xFF111214L
    private const val HANDLE = 0xFF9EA2A6L

    /** Fascia dei frontali (y0, y1): sopra lo zoccolo e sotto il piano per le basi, tutta l'altezza per i pensili. */
    fun frontZone(kind: Kind, h: Double): Pair<Double, Double> = when (kind.placement) {
        Placement.BASE -> PLINTH to h - TOP
        Placement.WALL -> 0.0 to h
        Placement.TALL -> PLINTH to h
    }

    /** I frontali del modulo largo `w` e alto `h`. */
    fun fronts(spec: KitchenModules.Spec, w: Double, h: Double): List<FrontPanel> {
        val (y0, y1) = frontZone(spec.kind, h)
        val x0 = -w / 2
        val x1 = w / 2
        val out = ArrayList<FrontPanel>()
        fun panel(kind: FrontKind, a: Double, b: Double, c: Double, d: Double, handle: HandleAt) {
            if (b - a > GAP * 2 && d - c > GAP * 2) out += FrontPanel(kind, a + GAP / 2, b - GAP / 2, c + GAP / 2, d - GAP / 2, handle)
        }
        /** Un'anta, o due uguali se il vano è largo. */
        fun doors(a: Double, b: Double, c: Double, d: Double, handle: HandleAt) {
            if (b - a <= MAX_SINGLE_DOOR) panel(FrontKind.DOOR, a, b, c, d, handle)
            else {
                val m = (a + b) / 2
                panel(FrontKind.DOOR, a, m, c, d, handle)
                panel(FrontKind.DOOR, m, b, c, d, handle)
            }
        }
        val zone = y1 - y0
        when (spec.kind) {
            Kind.BASE_DOORS, Kind.SINK -> doors(x0, x1, y0, y1, HandleAt.TOP)
            Kind.BASE_DRAWERS -> {
                // Cassetto alto basso (16 cm) e due grandi; in uno zoccolo molto basso solo due.
                if (zone >= 45) {
                    val top = 16.0
                    val big = (zone - top) / 2
                    panel(FrontKind.DRAWER, x0, x1, y1 - top, y1, HandleAt.TOP)
                    panel(FrontKind.DRAWER, x0, x1, y0 + big, y1 - top, HandleAt.TOP)
                    panel(FrontKind.DRAWER, x0, x1, y0, y0 + big, HandleAt.TOP)
                } else {
                    panel(FrontKind.DRAWER, x0, x1, y0 + zone / 2, y1, HandleAt.TOP)
                    panel(FrontKind.DRAWER, x0, x1, y0, y0 + zone / 2, HandleAt.TOP)
                }
            }
            Kind.OVEN_HOB -> {
                val drawer = min(14.0, zone * 0.2)
                panel(FrontKind.DRAWER, x0, x1, y0, y0 + drawer, HandleAt.TOP)
                panel(FrontKind.OVEN, x0, x1, y0 + drawer, y1, HandleAt.TOP)
            }
            Kind.DISHWASHER -> panel(FrontKind.PANEL, x0, x1, y0, y1, HandleAt.TOP)
            Kind.CORNER -> {
                // Base d'angolo: anta a destra, il resto è un pannello cieco (finisce sotto l'altra base).
                val dw = min(45.0, w / 2)
                panel(FrontKind.PANEL, x0, x1 - dw, y0, y1, HandleAt.NONE)
                panel(FrontKind.DOOR, x1 - dw, x1, y0, y1, HandleAt.TOP)
            }
            Kind.WALL_DOORS -> doors(x0, x1, y0, y1, HandleAt.BOTTOM)
            Kind.WALL_HOOD -> {
                val hood = min(12.0, zone * 0.3)
                panel(FrontKind.HOOD, x0, x1, y0, y0 + hood, HandleAt.NONE)
                doors(x0, x1, y0 + hood, y1, HandleAt.BOTTOM)
            }
            // Colonne: proporzioni delle colonne standard (zona dei frontali di 207 cm), valide anche se più basse o più alte.
            Kind.TALL_PANTRY -> {
                val split = y0 + zone * 130.0 / 207.0
                doors(x0, x1, y0, split, HandleAt.TOP)
                doors(x0, x1, split, y1, HandleAt.BOTTOM)
            }
            Kind.TALL_OVEN -> {
                val a = y0 + zone * 48.0 / 207.0
                val b = a + zone * 60.0 / 207.0
                val c = b + zone * 38.0 / 207.0
                panel(FrontKind.DRAWER, x0, x1, y0, a, HandleAt.TOP)
                panel(FrontKind.OVEN, x0, x1, a, b, HandleAt.TOP)
                panel(FrontKind.MICROWAVE, x0, x1, b, c, HandleAt.NONE)
                doors(x0, x1, c, y1, HandleAt.BOTTOM)
            }
            Kind.TALL_FRIDGE -> {
                val a = y0 + zone * 78.0 / 207.0
                val b = a + zone * 95.0 / 207.0
                doors(x0, x1, y0, a, HandleAt.TOP)
                doors(x0, x1, a, b, HandleAt.BOTTOM)
                doors(x0, x1, b, y1, HandleAt.BOTTOM)
            }
        }
        return out
    }

    /** Colore più scuro di `t` (0–1), per lo zoccolo. */
    fun darker(argb: Long, t: Double): Long {
        fun ch(shift: Int) = (((argb shr shift) and 0xFF) * (1 - t)).toLong().coerceIn(0, 255)
        return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Tutti i pezzi del modulo largo `w`, profondo `d`, alto `h`. */
    fun parts(spec: KitchenModules.Spec, w: Double, d: Double, h: Double): List<Part> {
        val kind = spec.kind
        val front = spec.front.argb
        val out = ArrayList<Part>()
        val x0 = -w / 2
        val x1 = w / 2
        val back = -d / 2
        // Faccia delle ante: le basi stanno sotto la sporgenza del piano; pensili e colonne lasciano lo spazio per le maniglie.
        val face = when (kind.placement) {
            Placement.BASE -> d / 2 - OVERHANG
            Placement.WALL -> d / 2 - 1.5
            Placement.TALL -> d / 2 - 1.0
        }
        val (zoneLo, zoneHi) = frontZone(kind, h)
        if (kind.placement != Placement.WALL) {
            out += Part(x0, x1, 0.0, min(PLINTH, h), min(back + 2, face - FRONT), face - FRONT - 5, darker(front, 0.3))
        }
        // Struttura (fianchi e fondo): stesso colore delle ante, come i fianchi a vista di una cucina.
        out += Part(x0, x1, zoneLo, zoneHi, back, face - FRONT, front)
        if (kind.hasTop) out += Part(x0, x1, h - TOP, h, back, d / 2, spec.top.argb, spec.top.material)
        for (p in fronts(spec, w, h)) {
            val z0 = face - FRONT
            when (p.kind) {
                FrontKind.DOOR, FrontKind.DRAWER, FrontKind.PANEL -> out += Part(p.x0, p.x1, p.y0, p.y1, z0, face, front)
                FrontKind.HOOD -> out += Part(p.x0, p.x1, p.y0, p.y1, z0, face + 0.8, STEEL)
                FrontKind.OVEN -> {
                    out += Part(p.x0, p.x1, p.y0, p.y1, z0, face, STEEL)
                    // Vetro scuro del forno sotto la fascia dei comandi.
                    val top = p.y1 - min(9.0, p.height * 0.18)
                    out += Part(p.x0 + 4, p.x1 - 4, p.y0 + 4, top - 3, face, face + 0.3, GLASS_DARK)
                    out += Part(p.x0 + 3, p.x1 - 3, top, p.y1 - 1.5, face, face + 0.2, STEEL_DARK)
                }
                FrontKind.MICROWAVE -> {
                    out += Part(p.x0, p.x1, p.y0, p.y1, z0, face, STEEL)
                    val split = p.x0 + p.width * 0.68
                    out += Part(p.x0 + 3, split - 1, p.y0 + 3, p.y1 - 3, face, face + 0.3, GLASS_DARK)
                    out += Part(split + 1, p.x1 - 3, p.y0 + 3, p.y1 - 3, face, face + 0.2, STEEL_DARK)
                }
            }
            handle(p)?.let { (hx0, hx1, hy) -> out += Part(hx0, hx1, hy - 0.6, hy + 0.6, face, face + 1.0, HANDLE) }
        }
        when (kind) {
            Kind.SINK -> {
                // Vasca in acciaio incassata nel piano e rubinetto dietro.
                val bw = min(w - 16, 50.0).coerceAtLeast(20.0)
                val bz0 = back + 9
                val bz1 = min(d / 2 - 7, bz0 + 42)
                out += Part(-bw / 2, bw / 2, h, h + 0.3, bz0, bz1, STEEL)
                out += Part(-bw / 2 + 2.5, bw / 2 - 2.5, h + 0.3, h + 0.35, bz0 + 2.5, bz1 - 2.5, STEEL_DARK)
                out += Part(-1.5, 1.5, h, h + 30, back + 3, back + 6, STEEL)
                out += Part(-1.0, 1.0, h + 27, h + 30, back + 3, back + 20, STEEL)
            }
            Kind.OVEN_HOB -> {
                // Piano cottura in vetro nero (al massimo 58 × 51 cm).
                val hw = min(w - 6, 58.0) / 2
                val hz0 = back + 5
                val hz1 = min(d / 2 - 5, hz0 + 51)
                out += Part(-hw, hw, h, h + 0.5, hz0, hz1, HOB_GLASS)
            }
            else -> {}
        }
        return out
    }

    /** Maniglia orizzontale di un frontale: (da x, a x, altezza), o null se non ne ha. */
    fun handle(p: FrontPanel): Triple<Double, Double, Double>? {
        if (p.handle == HandleAt.NONE || p.width < 8) return null
        val len = min(16.0, p.width * 0.4)
        val cx = (p.x0 + p.x1) / 2
        val y = when (p.handle) {
            HandleAt.TOP -> p.y1 - min(5.0, p.height * 0.25)
            HandleAt.BOTTOM -> p.y0 + min(5.0, p.height * 0.25)
            HandleAt.NONE -> return null
        }
        return Triple(cx - len / 2, cx + len / 2, y)
    }
}
