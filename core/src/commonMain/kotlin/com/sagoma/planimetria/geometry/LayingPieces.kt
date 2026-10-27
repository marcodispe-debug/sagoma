package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Laying
import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.model.LayingStart
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.MaterialPieces
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un pezzo posato (piastrella, doga) senza la fuga: rettangolo `length` × `width` cm con un angolo in `corner`, il lato
 * lungo lungo `u` e quello corto lungo `v` (versori). `row` e `col` lo distinguono dagli altri (per scegliere la sua foto).
 */
class LaidPiece(val corner: Vec2, val u: Vec2, val v: Vec2, val length: Double, val width: Double, val row: Int, val col: Int) {
    val outline: List<Vec2> get() = listOf(corner, corner + u * length, corner + u * length + v * width, corner + v * width)

    /** Dove sta `p` nel pezzo: da 0 a 1 lungo il lato lungo e lungo quello corto. */
    fun local(p: Vec2): Vec2 {
        val d = p - corner
        return Vec2((d dot u) / length, (d dot v) / width)
    }
}

/** Ritaglio della foto che fa da faccia a un pezzo: origine e spostamenti (unità della foto) lungo il lato lungo e il corto. */
class PhotoWindow(val origin: Vec2, val alongLength: Vec2, val alongWidth: Vec2) {
    /** Coordinate nella foto del punto del pezzo a `t` (0..1 lungo il lato lungo) e `s` (0..1 lungo il corto). */
    fun uv(local: Vec2): Vec2 = origin + alongLength * local.x + alongWidth * local.y
}

/**
 * Posa a pezzi ([Laying]): dove vanno i pezzi, che pezzo della foto mostra ognuno e quanti ne servono.
 *
 * I pezzi si dispongono in un riquadro «della posa», girato del verso scelto e con l'origine nel punto di partenza: lì le
 * file corrono lungo x. Ogni pezzo occupa la sua cella meno la fuga sui lati verso +x e +y, così tra due pezzi vicini resta
 * sempre una fuga della larghezza scelta.
 */
object LayingPieces {
    /** Oltre questo numero di pezzi (stanze enormi con pezzi minuscoli) si usa la foto ripetuta. */
    const val MAX_PIECES = 15000

    /** Lati e fuga dentro i limiti, il lato corto in [Laying.width]. */
    fun normalized(l: Laying): Laying {
        val a = l.width.coerceIn(Laying.MIN_SIDE, Laying.MAX_SIDE)
        val b = l.length.coerceIn(Laying.MIN_SIDE, Laying.MAX_SIDE)
        return l.copy(width = min(a, b), length = max(a, b), joint = l.joint.coerceIn(0.0, Laying.MAX_JOINT))
    }

    private fun axes(rotation: Double): Pair<Vec2, Vec2> {
        val r = rotation * kotlin.math.PI / 180
        val ex = Vec2(cos(r), sin(r))
        return ex to ex.perp()
    }

    /**
     * Punto di partenza nel contorno `inner` (la stanza dentro i muri): il centro (lì si incrociano le fughe, tagli uguali
     * sui lati opposti) o l'angolo in alto a sinistra nel verso della posa (pezzi interi da quei due lati).
     */
    fun anchor(laying: Laying, rotation: Double, inner: List<Vec2>): Vec2 {
        if (inner.isEmpty()) return Vec2(0.0, 0.0)
        val (ex, ey) = axes(rotation)
        val xs = inner.map { it dot ex }
        val ys = inner.map { it dot ey }
        val (x, y) = when (laying.start) {
            LayingStart.Centro -> (xs.min() + xs.max()) / 2 to (ys.min() + ys.max()) / 2
            LayingStart.Angolo -> xs.min() to ys.min()
        }
        return ex * x + ey * y
    }

    /**
     * Pezzi che coprono `region` (almeno il suo riquadro), posati come dice `laying`, girati di `rotation` gradi e con il
     * punto di partenza in `anchor`. Vuoto se ne servirebbero più di [MAX_PIECES].
     */
    fun cover(laying: Laying, rotation: Double, anchor: Vec2, region: List<Vec2>): List<LaidPiece> {
        if (region.size < 3) return emptyList()
        val l = normalized(laying)
        val (ex, ey) = axes(rotation)
        val xs = region.map { (it - anchor) dot ex }
        val ys = region.map { (it - anchor) dot ey }
        val x0 = xs.min(); val x1 = xs.max(); val y0 = ys.min(); val y1 = ys.max()
        val a = l.length + l.joint
        val b = l.width + l.joint
        if ((x1 - x0 + 2 * a) * (y1 - y0 + 2 * a) / (a * b) > MAX_PIECES) return emptyList()
        val out = mutableListOf<LaidPiece>()
        /** Pezzo con l'angolo in (`cx`, `cy`) e gli assi `pu`, `pv` nel riquadro della posa. */
        fun add(cx: Double, cy: Double, pu: Vec2, pv: Vec2, length: Double, width: Double, row: Int, col: Int) {
            out += LaidPiece(anchor + ex * cx + ey * cy, ex * pu.x + ey * pu.y, ex * pv.x + ey * pv.y, length, width, row, col)
        }
        val alongX = Vec2(1.0, 0.0)
        val alongY = Vec2(0.0, 1.0)
        when (l.pattern) {
            LayingPattern.Dritta, LayingPattern.Sfalsata, LayingPattern.Terzi, LayingPattern.ACorrere -> {
                val j0 = floor(y0 / b).toInt() - 1
                val j1 = floor(y1 / b).toInt() + 1
                val offsets = rowOffsets(l.pattern, j0, j1)
                for (j in j0..j1) {
                    val off = offsets[j - j0] * a
                    for (i in floor((x0 - off) / a).toInt() - 1..floor((x1 - off) / a).toInt() + 1) {
                        add(off + i * a, j * b, alongX, alongY, l.length, l.width, j, i)
                    }
                }
            }
            LayingPattern.SpinaDiPesce -> {
                // Nel riquadro della spina (girato di 45°) i pezzi sono dritti: per ogni (i, j) uno disteso (a × b) e uno
                // in piedi (b × a) che chiude la sua testa; i passi (b, b) e (a, −a) ripetono il disegno.
                val k = 1 / sqrt(2.0)
                fun toPose(hx: Double, hy: Double) = Vec2((hx + hy) * k, (hy - hx) * k)
                // Le origini stanno ogni √2·b lungo x e √2·a lungo y, e un pezzo ne sporge fino a (a + b)/√2.
                val mi = ceil((a + b) / (2 * b)).toInt() + 1
                val mj = ceil((a + b) / (2 * a)).toInt() + 1
                val i0 = floor(sqrt(2.0) * x0 / (2 * b)).toInt() - mi
                val i1 = ceil(sqrt(2.0) * x1 / (2 * b)).toInt() + mi
                val jA = floor(-sqrt(2.0) * y1 / (2 * a)).toInt() - mj
                val jB = ceil(-sqrt(2.0) * y0 / (2 * a)).toInt() + mj
                val hu = toPose(1.0, 0.0)
                val hv = toPose(0.0, 1.0)
                for (j in jA..jB) for (i in i0..i1) {
                    val hx = i * b + j * a
                    val hy = i * b - j * a
                    val flat = toPose(hx, hy)
                    add(flat.x, flat.y, hu, hv, l.length, l.width, 2 * j, i)
                    val up = toPose(hx + a, hy + b - a)
                    add(up.x, up.y, hv, hu, l.length, l.width, 2 * j + 1, i)
                }
            }
            LayingPattern.Cesto -> {
                // Quadrati di lato «lunghezza + fuga» con dentro i pezzi affiancati; quanti ne stanno lo dice la larghezza.
                val n = max(1, (a / b).roundToInt())
                val step = a / n
                val width = max(0.5, step - l.joint)
                for (bj in floor(y0 / a).toInt() - 1..floor(y1 / a).toInt() + 1) {
                    for (bi in floor(x0 / a).toInt() - 1..floor(x1 / a).toInt() + 1) {
                        val flat = (bi + bj) % 2 == 0
                        for (s in 0 until n) {
                            if (flat) add(bi * a, bj * a + s * step, alongX, alongY, l.length, width, bj * n + s, bi)
                            else add(bi * a + s * step, bj * a, alongY, alongX, l.length, width, bj * n + s, bi)
                        }
                    }
                }
            }
        }
        return out
    }

    /** Spostamento di ogni fila da `j0` a `j1`, in frazioni della lunghezza del pezzo (da 0 a 1). */
    private fun rowOffsets(pattern: LayingPattern, j0: Int, j1: Int): List<Double> = when (pattern) {
        LayingPattern.Sfalsata -> (j0..j1).map { frac(it * 0.5) }
        LayingPattern.Terzi -> (j0..j1).map { frac(it / 3.0) }
        LayingPattern.ACorrere -> {
            // Ogni fila si sposta da un quarto a tre quarti di pezzo rispetto alla precedente: le giunte non si avvicinano mai.
            fun step(j: Int) = 0.25 + 0.5 * hash(j, 7, 11)
            val off = HashMap<Int, Double>()
            off[0] = 0.0
            for (j in 1..max(0, j1)) off[j] = frac(off.getValue(j - 1) + step(j))
            for (j in -1 downTo min(0, j0)) off[j] = frac(off.getValue(j + 1) - step(j + 1))
            (j0..j1).map { off.getValue(it) }
        }
        else -> (j0..j1).map { 0.0 }
    }

    private fun frac(v: Double) = v - floor(v)

    /** Numero pseudo-casuale in [0, 1), sempre uguale per gli stessi tre numeri. */
    internal fun hash(a: Int, b: Int, c: Int): Double {
        var h = (a * 73856093) xor (b * 19349663) xor (c * 83492791)
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65536.0
    }

    /**
     * Pezzo di foto per un pezzo posato del materiale `materialId` (foto di `size` cm): un pezzo vero della foto (senza la
     * sua fuga) quando la foto ne ha, scelto a caso, oppure in ordine nella posa dritta di una griglia (così scacchiere e
     * cementine restano com'erano); alla misura vera se ci sta, altrimenti ingrandito senza deformarlo. `seed` cambia la
     * scelta da una stanza all'altra.
     */
    fun window(materialId: String, size: Double, laying: Laying, piece: LaidPiece, seed: Int): PhotoWindow {
        val photo = MaterialPieces.of(materialId)
        val lu = piece.length / size
        val wu = piece.width / size
        val r1 = hash(piece.row, piece.col, seed)
        val r2 = hash(piece.col, seed, piece.row)
        val r3 = hash(seed, piece.row, piece.col)
        if (photo !is MaterialPieces.Pieces) {
            // Foto senza pezzi: un ritaglio a caso (la foto si ripete senza bordi), rimpicciolito se il pezzo è più grande della foto.
            val k = min(1.0, 1 / lu)
            return PhotoWindow(Vec2(r1, r2), Vec2(lu * k, 0.0), Vec2(0.0, wu * k))
        }
        val n = photo.rects.size
        val ordered = photo.grid && laying.pattern == LayingPattern.Dritta && n == photo.columns * photo.rows
        val rect = if (ordered) {
            photo.rects[mod(piece.row, photo.rows) * photo.columns + mod(piece.col, photo.columns)]
        } else photo.rects[(r1 * n).toInt().coerceIn(0, n - 1)]
        val horizontal = rect.w >= rect.h
        // Nelle griglie (piastrelle) il pezzo mostra tutta la piastrella della foto; nelle file (doghe) la venatura resta alla misura vera.
        val fit = min(rect.long / lu, rect.short / wu)
        val k = if (photo.grid) fit else min(1.0, fit)
        val winL = lu * k
        val winW = wu * k
        val offL = r2 * (rect.long - winL)
        val offW = r3 * (rect.short - winW)
        var origin = if (horizontal) Vec2(rect.x + offL, rect.y + offW) else Vec2(rect.x + offW, rect.y + offL)
        var alongL = if (horizontal) Vec2(winL, 0.0) else Vec2(0.0, winL)
        val alongW = if (horizontal) Vec2(0.0, winW) else Vec2(winW, 0.0)
        // Le doghe si posano nei due versi.
        if (!photo.grid && hash(piece.row + 3, piece.col, seed) < 0.5) {
            origin += alongL
            alongL = alongL * -1.0
        }
        return PhotoWindow(origin, alongL, alongW)
    }

    private fun mod(a: Int, n: Int) = ((a % n) + n) % n

    /** Pezzi che servono a coprire un pavimento: interi, da tagliare, e la superficie (m²). */
    data class Count(val whole: Int, val cut: Int, val area: Double) {
        val total: Int get() = whole + cut
    }

    /** Pezzi da posare nella stanza (dentro i muri), o null se il pavimento non è posato a pezzi. */
    fun count(room: Room): Count? {
        val laying = room.floorLayout.laying ?: return null
        val material = MaterialCatalog.item(room.floorMaterial) ?: return null
        if (!MaterialPieces.canLay(material.id)) return null
        val inner = room.interiorOutline()
        if (inner.size < 3) return null
        return count(laying, room.floorLayout.rotation, inner)
    }

    /** Pezzi da posare nel contorno `inner`, partendo come dice `laying`. */
    fun count(laying: Laying, rotation: Double, inner: List<Vec2>): Count? {
        val pieces = cover(laying, rotation, anchor(laying, rotation, inner), inner)
        if (pieces.isEmpty()) return null
        val box = Polygon.bounds(inner)
        var whole = 0
        var cut = 0
        for (p in pieces) {
            val o = p.outline
            if (o.all { it.x < box.minX } || o.all { it.x > box.maxX } || o.all { it.y < box.minY } || o.all { it.y > box.maxY }) continue
            val full = p.length * p.width
            val inside = Polygon.area(Polygon.clipConvex(inner, o))
            when {
                inside >= full * 0.995 -> whole++
                inside > 1.0 -> cut++
            }
        }
        return Count(whole, cut, Polygon.area(inner) / 10_000)
    }
}
