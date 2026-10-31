package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.geometry.KitchenGeometry.Part
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.model.ParametricFurniture.Kind
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pezzi di qualsiasi mobile generato (moduli della cucina o [ParametricFurniture]), oppure null se il modello è un file 3D. */
object GeneratedGeometry {
    /**
     * Misura (cm) oltre la quale un mobile generato non aggiunge più ante, ripiani e campate ma si allunga e basta: con una
     * misura sbagliata (uno zero di troppo scritto nel pannello, un file rovinato) le ante diventavano centinaia di migliaia e
     * il 3D finiva la memoria.
     */
    const val MAX_SIZE = 2000.0

    fun parts(model: String, w: Double, d: Double, h: Double): List<Part>? {
        val cw = if (w > MAX_SIZE) MAX_SIZE else w
        val cd = if (d > MAX_SIZE) MAX_SIZE else d
        val ch = if (h > MAX_SIZE) MAX_SIZE else h
        val parts = com.sagoma.planimetria.model.KitchenModules.parse(model)?.let { KitchenGeometry.parts(it, cw, cd, ch) }
            ?: ParametricFurniture.parse(model)?.let { ParametricGeometry.parts(it, cw, cd, ch) }
            ?: return null
        if (cw == w && cd == d && ch == h) return parts
        // Pezzi attorno al centro (x e z) e dalla base (y): basta stirarli fino alla misura vera.
        val sx = w / cw
        val sy = h / ch
        val sz = d / cd
        return parts.map { it.copy(x0 = it.x0 * sx, x1 = it.x1 * sx, y0 = it.y0 * sy, y1 = it.y1 * sy, z0 = it.z0 * sz, z1 = it.z1 * sz) }
    }
}

/**
 * Geometria dei mobili generati ([ParametricFurniture]), nelle stesse coordinate locali dei moduli della cucina
 * ([KitchenGeometry]): x lungo la larghezza, y in alto dalla base del mobile, z dal retro (−d/2) al davanti (+d/2).
 * Spessori, fughe, maniglie, gambe e passo dei ripiani sono fissi in cm: allargando un mobile cambiano il numero di ante,
 * cassetti e ripiani, niente si stira.
 */
object ParametricGeometry {
    /** Spessore dei pannelli, delle ante, fuga tra le ante (cm). */
    const val PANEL = 2.0
    const val FRONT = 1.8
    const val GAP = 0.3
    /** Larghezza massima di un'anta a battente (oltre si aggiunge un'anta). */
    const val MAX_DOOR = 55.0
    /** Passo dei ripiani della libreria e larghezza massima di una campata (oltre si aggiunge un montante). */
    const val SHELF_STEP = 37.0
    const val MAX_BAY = 80.0

    private const val HANDLE = 0xFF9EA2A6L
    private const val METAL = 0xFF3B3E42L
    private const val ALUMINIUM = 0xFFB9BCC0L
    private const val SHEET = 0xFFF4F3EFL
    private const val SHEET_SHADE = 0xFFE3E1DBL
    private const val PILLOW = 0xFFFAF9F6L

    fun parts(spec: ParametricFurniture.Spec, w: Double, d: Double, h: Double): List<Part> {
        val c = spec.finish.argb
        val out = ArrayList<Part>()
        val x0 = -w / 2
        val x1 = w / 2
        val back = -d / 2
        val front = d / 2
        fun part(a: Double, b: Double, y0: Double, y1: Double, z0: Double, z1: Double, argb: Long) {
            if (b - a > 1e-6 && y1 - y0 > 1e-6 && z1 - z0 > 1e-6) out += Part(a, b, y0, y1, z0, z1, argb)
        }
        /** Ante affiancate tra y0 e y1 sulla faccia `face`: `n` ante uguali, maniglia orizzontale in alto o verticale al centro. */
        fun doors(n: Int, ya: Double, yb: Double, face: Double, vertical: Boolean, handleY: Double = (ya + yb) / 2) {
            val dw = w / n
            for (i in 0 until n) {
                val a = x0 + i * dw + GAP / 2
                val b = x0 + (i + 1) * dw - GAP / 2
                part(a, b, ya + GAP / 2, yb - GAP / 2, face - FRONT, face, c)
                if (vertical) {
                    // Maniglie verticali vicino al bordo dove le ante si aprono a coppie (la seconda di ogni coppia a sinistra).
                    val len = min(40.0, (yb - ya) * 0.25)
                    val hx = if (i % 2 == 0 && i < n - 1) b - 4 else a + 4
                    part(hx - 0.6, hx + 0.6, handleY - len / 2, handleY + len / 2, face, face + 1.2, HANDLE)
                } else {
                    val len = min(16.0, (b - a) * 0.4)
                    val cx = (a + b) / 2
                    val hy = yb - min(5.0, (yb - ya) * 0.25)
                    part(cx - len / 2, cx + len / 2, hy - 0.6, hy + 0.6, face, face + 1.0, HANDLE)
                }
            }
        }
        /** `n` cassetti uguali (tutta la larghezza) tra y0 e y1, con maniglia orizzontale. */
        fun drawers(n: Int, ya: Double, yb: Double, face: Double) {
            val dh = (yb - ya) / n
            for (i in 0 until n) {
                val a = ya + i * dh + GAP / 2
                val b = ya + (i + 1) * dh - GAP / 2
                part(x0 + GAP / 2, x1 - GAP / 2, a, b, face - FRONT, face, c)
                val len = min(20.0, w * 0.3)
                val hy = b - min(5.0, dh * 0.3)
                part(-len / 2, len / 2, hy - 0.6, hy + 0.6, face, face + 1.0, HANDLE)
            }
        }
        /** Quattro gambe quadrate di lato `s`, rientrate di `inset` dai bordi, da 0 a `top`. */
        fun legs(s: Double, inset: Double, top: Double, argb: Long) {
            for (lx in listOf(x0 + inset, x1 - inset - s)) for (lz in listOf(back + inset, front - inset - s)) part(lx, lx + s, 0.0, top, lz, lz + s, argb)
        }

        when (spec.kind) {
            Kind.WARDROBE -> {
                val plinth = min(8.0, h * 0.05)
                val face = front - 1.2
                part(x0, x1, 0.0, plinth, back + 2, face - FRONT - 4, KitchenGeometry.darker(c, 0.3))
                part(x0, x1, plinth, h, back, face - FRONT, c)
                doors(max(1, ceil(w / MAX_DOOR).toInt()), plinth, h, face, vertical = true, handleY = min(105.0, h * 0.5))
            }
            Kind.SLIDING_WARDROBE -> {
                val plinth = min(8.0, h * 0.05)
                part(x0, x1, 0.0, plinth, back + 2, front - 8, KitchenGeometry.darker(c, 0.3))
                part(x0, x1, plinth, h, back, front - 5, c)
                // Ante scorrevoli su due binari, sovrapposte di 4 cm, con profili verticali in alluminio.
                val n = if (w > 240) 3 else 2
                val pw = (w + (n - 1) * 4.0) / n
                for (i in 0 until n) {
                    val a = x0 + i * (pw - 4.0)
                    val b = a + pw
                    val z1 = if (i % 2 == 0) front else front - 2.5
                    part(a, b, plinth, h - 1, z1 - FRONT, z1 - 0.3, c)
                    part(a, a + 2, plinth, h - 1, z1 - 0.3, z1, ALUMINIUM)
                    part(b - 2, b, plinth, h - 1, z1 - 0.3, z1, ALUMINIUM)
                }
            }
            Kind.BED, Kind.SINGLE_BED -> {
                // Testiera (dietro, alta quanto il letto), giroletto su piedini, materasso, piumone e cuscini. Un letto più basso
                // dei cuscini (68 cm) è un letto basso: si abbassa tutto insieme, per restare dentro l'altezza scritta.
                val s = min(1.0, h / 68.0)
                val headboard = 8.0
                val feet = 10.0 * s
                val frameTop = 32.0 * s
                val mattress = 22.0 * s
                part(x0, x1, 0.0, h, back, back + headboard, c)
                part(x0, x1, feet, frameTop, back + headboard, front, c)
                legs(5.0, 3.0, feet, KitchenGeometry.darker(c, 0.35))
                val mz0 = back + headboard + 1
                part(x0 + 2, x1 - 2, frameTop, frameTop + mattress, mz0, front - 2, SHEET)
                // Piumone: copre i due terzi verso i piedi e scende un po' sui lati.
                val dz0 = mz0 + (front - 2 - mz0) * 0.32
                part(x0 + 1, x1 - 1, frameTop + 6 * s, frameTop + mattress + 3 * s, dz0, front - 1, SHEET_SHADE)
                val pillows = if (w >= 120) 2 else 1
                val pw = (w - 8 - (pillows - 1) * 4) / pillows
                for (i in 0 until pillows) {
                    val a = x0 + 4 + i * (pw + 4)
                    part(a, a + pw, frameTop + mattress, frameTop + mattress + 14 * s, mz0 + 3, mz0 + 3 + min(40.0, d * 0.2), PILLOW)
                }
            }
            Kind.NIGHTSTAND, Kind.CHEST -> {
                // Cassetti rientrati di 1 cm sotto il piano: le maniglie restano dentro l'ingombro.
                val feet = min(8.0, h * 0.12)
                val face = front - 1.0
                legs(3.0, 2.0, feet, KitchenGeometry.darker(c, 0.35))
                part(x0, x1, feet, h, back, face - FRONT, c)
                val n = if (spec.kind == Kind.NIGHTSTAND) (if (h >= 40) 2 else 1) else max(2, min(5, ((h - feet) / 20).roundToInt()))
                drawers(n, feet, h - PANEL, face)
                part(x0, x1, h - PANEL, h, back, front, c)
            }
            Kind.BOOKCASE -> {
                // Fianchi, cielo, fondo, schiena; ripiani ogni 37 cm circa; un montante ogni 80 cm al massimo.
                part(x0, x0 + PANEL, 0.0, h, back, front, c)
                part(x1 - PANEL, x1, 0.0, h, back, front, c)
                part(x0, x1, 0.0, min(6.0, h), back, front, KitchenGeometry.darker(c, 0.15))
                part(x0, x1, h - PANEL, h, back, front, c)
                part(x0, x1, 0.0, h, back, back + 1.0, KitchenGeometry.darker(c, 0.1))
                val bays = max(1, ceil((w - 2 * PANEL) / MAX_BAY).toInt())
                val bay = (w - 2 * PANEL) / bays
                for (i in 1 until bays) {
                    val a = x0 + PANEL + i * bay - PANEL / 2
                    part(a, a + PANEL, 6.0, h - PANEL, back + 1, front, c)
                }
                val shelves = max(0, ((h - 6 - PANEL) / SHELF_STEP).roundToInt() - 1)
                val step = (h - 6 - PANEL) / (shelves + 1)
                for (k in 1..shelves) {
                    val y = 6 + k * step
                    part(x0 + PANEL, x1 - PANEL, y - PANEL / 2, y + PANEL / 2, back + 1, front, c)
                }
            }
            Kind.SIDEBOARD, Kind.TV_UNIT -> {
                // Su gambe metalliche; ante (madia) o ribalte (mobile TV) per tutta la larghezza.
                val feet = if (spec.kind == Kind.TV_UNIT) min(10.0, h * 0.2) else min(14.0, h * 0.2)
                val face = front - 1.0
                legs(3.0, 3.0, feet, METAL)
                part(x0, x1, feet, h, back, face - FRONT, c)
                val n = max(1, ceil(w / (if (spec.kind == Kind.TV_UNIT) 60.0 else MAX_DOOR)).toInt())
                doors(n, feet, h - PANEL, face, vertical = false)
                part(x0, x1, h - PANEL, h, back, front, c)
            }
            Kind.SHELF -> part(x0, x1, 0.0, h, back, front, c)
            Kind.TABLE -> {
                val top = min(4.0, h * 0.1)
                part(x0, x1, h - top, h, back, front, c)
                legs(6.0, 5.0, h - top, KitchenGeometry.darker(c, 0.1))
                // Fascia sotto il piano.
                part(x0 + 5, x1 - 5, h - top - 8, h - top, back + 5, back + 7, KitchenGeometry.darker(c, 0.1))
                part(x0 + 5, x1 - 5, h - top - 8, h - top, front - 7, front - 5, KitchenGeometry.darker(c, 0.1))
            }
            Kind.DESK -> {
                val top = min(3.0, h * 0.1)
                part(x0, x1, h - top, h, back, front, c)
                // Due fianchi pieni, pannello dietro per le gambe, un cassetto sotto il piano a destra.
                part(x0, x0 + PANEL + 1, 0.0, h - top, back, front, c)
                part(x1 - PANEL - 1, x1, 0.0, h - top, back, front, c)
                part(x0 + PANEL + 1, x1 - PANEL - 1, h - top - 35, h - top, back + 2, back + 3.8, c)
                val dw = min(45.0, w * 0.35)
                val face = front - 1.0
                part(x1 - PANEL - 1 - dw, x1 - PANEL - 1, h - top - 14, h - top, back + 4, face - FRONT, c)
                part(x1 - PANEL - 1 - dw + GAP, x1 - PANEL - 1 - GAP, h - top - 13.7, h - top - 0.3, face - FRONT, face, c)
                val hx = x1 - PANEL - 1 - dw / 2
                part(hx - 7, hx + 7, h - top - 6.6, h - top - 5.4, face, face + 1.0, HANDLE)
            }
        }
        return out
    }
}
