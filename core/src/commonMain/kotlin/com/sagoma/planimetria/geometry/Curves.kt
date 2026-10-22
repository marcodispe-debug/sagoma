package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * Muri curvi. Un muro curvo è un arco di cerchio tra i suoi due angoli, descritto dalla freccia (`bulge`): di quanto
 * si scosta a metà dalla linea dritta, positiva verso l'esterno della stanza. Le posizioni lungo il muro (porte,
 * finestre, impianti) restano misurate sulla linea dritta tra i due angoli, come per i muri dritti: così trascinarle,
 * copiarle e salvarle funziona allo stesso modo.
 *
 * Per disegnare, misurare e costruire il 3D la stanza si «srotola» ([expandCurves]): ogni muro curvo diventa tanti
 * muri dritti cortissimi lungo l'arco (la curva si vede continua), con un pezzo dritto esattamente dove sta una porta
 * o finestra (largo come lei); ai suoi lati il muro parte nella direzione dell'apertura e curva subito.
 */

/** Arco di un muro curvo da `a` a `b`, che a metà si scosta di `bulge` cm verso `outward`. */
class WallArc(val a: Vec2, val b: Vec2, val bulge: Double, val outward: Vec2) {
    val length: Double = a.distanceTo(b)
    val u: Vec2 = (b - a) / length
    val mid: Vec2 = (a + b) / 2.0

    /** Metà dell'angolo al centro, con il segno della freccia: tan(α/2) = 2·freccia / corda. */
    val halfAngle: Double = 2 * atan(2 * bulge / length)

    /** Raggio con il segno della freccia. */
    val radius: Double = length / (2 * sin(halfAngle))

    /** Punto dell'arco: `s` da −1 (in `a`) a 1 (in `b`), 0 a metà. */
    fun at(s: Double): Vec2 {
        val beta = s * halfAngle
        return mid + u * (radius * sin(beta)) + outward * (radius * cos(beta) - radius + bulge)
    }

    /** Parametro del punto dell'arco che sta sopra la posizione `t` misurata sulla linea dritta da `a`. */
    fun paramAt(t: Double): Double = asin(((t - length / 2) / radius).coerceIn(-1.0, 1.0)) / halfAngle

    /** Posizione sulla linea dritta da `a` del punto dell'arco `s`. */
    fun chordPosition(s: Double): Double = (at(s) - a) dot u

    /** Intervallo di parametri di un oggetto largo `width` (in linea retta) centrato in `t`. */
    fun span(t: Double, width: Double): Pair<Double, Double> {
        val sc = paramAt(t)
        val ds = 2 * asin((width / (2 * abs(radius))).coerceIn(0.0, 1.0)) / abs(halfAngle)
        return (sc - ds / 2).coerceIn(-1.0, 1.0) to (sc + ds / 2).coerceIn(-1.0, 1.0)
    }

    /** Lunghezza dell'arco spostato di `offset` cm verso l'interno della stanza (0 = mezzeria, metà spessore = filo interno). */
    fun arcLength(offset: Double = 0.0): Double = abs(2 * halfAngle) * abs(radius - offset)

    /** Angolo al centro dell'arco, in gradi. */
    val degrees: Double get() = abs(2 * halfAngle) * 180 / PI

    fun points(pieces: Int): List<Vec2> = (0..pieces).map { at(-1.0 + 2.0 * it / pieces) }

    /** Direzione dell'arco nel punto `s`, da `a` verso `b`. */
    fun tangentAt(s: Double): Vec2 = (u * cos(s * halfAngle) - outward * sin(s * halfAngle)).normalized()

    fun distanceTo(p: Vec2): Double {
        val pts = points(DISTANCE_PIECES)
        return (0 until pts.size - 1).minOf { Polygon.distanceToSegment(p, pts[it], pts[it + 1]) }
    }

    companion object {
        private const val DISTANCE_PIECES = 24

        /** Freccia di un arco di corda `chord` e raggio `radius` (l'arco più corto, al massimo mezzo cerchio). */
        fun bulgeForRadius(chord: Double, radius: Double): Double {
            val r = abs(radius).coerceAtLeast(chord / 2)
            return kotlin.math.sign(radius) * (r - kotlin.math.sqrt(r * r - chord * chord / 4))
        }

        /** Freccia di un arco di corda `chord` con angolo al centro `degrees` (fino a 180°). */
        fun bulgeForDegrees(chord: Double, degrees: Double): Double {
            val half = (abs(degrees).coerceIn(0.0, 180.0) * PI / 180) / 2
            return kotlin.math.sign(degrees) * chord / 2 * tan(half / 2)
        }
    }
}

/** Muri curvi: la freccia più grande è mezzo cerchio (metà della corda). */
fun Room.bulgeOf(i: Int): Double = (wallBulges[i] ?: 0.0).let { b -> if (abs(b) < MIN_BULGE) 0.0 else b.coerceIn(-wallLength(i) / 2, wallLength(i) / 2) }

/** Arco del muro `i`, o null se è dritto. */
fun Room.arcOf(i: Int): WallArc? {
    if (i !in 0 until wallCount || wallLength(i) < 1.0) return null
    val b = bulgeOf(i)
    return if (b == 0.0) null else WallArc(wallStart(i), wallEnd(i), b, outwardOf(i))
}

/** Distanza dalla mezzeria del muro `i`, dritto o curvo. */
fun Room.distanceToWall(i: Int, p: Vec2): Double = arcOf(i)?.distanceTo(p) ?: Polygon.distanceToSegment(p, wallStart(i), wallEnd(i))

/** Punto a metà del muro sulla sua mezzeria (in cima alla curva, per un muro curvo): dove sta la maniglia della curva. */
fun Room.wallMiddle(i: Int): Vec2 = arcOf(i)?.at(0.0) ?: ((wallStart(i) + wallEnd(i)) / 2.0)

/** Freccia sotto cui un muro si considera dritto (cm). */
const val MIN_BULGE = 0.5

/**
 * Pezzi dritti di un muro curvo srotolato: così fitti che la curva si vede continua. Al massimo 2° tra un pezzo e
 * l'altro, e mai più di 1 mm di scarto dall'arco vero.
 */
private const val MAX_PIECE_DEGREES = 2.0
private const val PIECE_TOLERANCE = 0.1

private fun WallArc.pieces(): Int {
    val r = abs(radius)
    val step = if (r <= PIECE_TOLERANCE) MAX_PIECE_DEGREES else kotlin.math.min(MAX_PIECE_DEGREES, 2 * kotlin.math.acos(1 - PIECE_TOLERANCE / r) * 180 / PI)
    return ceil(degrees / step).toInt().coerceIn(4, 360)
}

/** Stanza srotolata: i muri curvi diventano pezzi dritti. [origin] dice per ogni muro nuovo da quale muro viene. */
class ExpandedRoom(val room: Room, val origin: List<Int>)

/**
 * Accanto a una porta o finestra il muro curvo parte nella direzione dell'apertura (che è dritta), così ci si collega
 * con un taglio pulito, e con un raccordo morbido torna subito sulla curva.
 */
private const val BLEND = 70.0
private const val BLEND_STEPS = 16

/** Punti di un muro curvo da un angolo all'altro (compresi), e per ogni apertura il pezzo dove sta. */
private class CurvedWall(val points: List<Vec2>, val openingPiece: Map<Long, Int>)

private fun Room.curvedWall(i: Int, arc: WallArc): CurvedWall {
    val pts = mutableListOf(arc.at(-1.0))
    fun add(p: Vec2) { if (p.distanceTo(pts.last()) > 0.01) pts += p }
    val pieceOf = mutableMapOf<Long, Int>()
    val grid = arc.pieces().let { n -> (0..n).map { -1.0 + 2.0 * it / n } }

    /** Tratto da `ls` a `rs`: accanto alle aperture parte nella loro direzione (`ld`, `rd`, uscendo da loro), poi curva. */
    fun gap(ls: Double, ld: Vec2?, rs: Double, rd: Vec2?) {
        val cl = arc.chordPosition(ls)
        val cr = arc.chordPosition(rs)
        val share = (cr - cl) / (if (ld != null && rd != null) 2 else 1)
        val blendLength = kotlin.math.min(BLEND, kotlin.math.max(share, 0.0))
        val sj = if (ld != null) arc.paramAt(cl + blendLength) else ls
        val ej = if (rd != null) arc.paramAt(cr - blendLength) else rs
        if (ld != null) for (p in blend(arc.at(ls), ld, arc.at(sj), arc.tangentAt(sj))) add(p)
        for (g in grid) if (g > sj + 1e-6 && g < ej - 1e-6) add(arc.at(g))
        if (rd != null) {
            add(arc.at(ej))
            for (p in blend(arc.at(ej), arc.tangentAt(ej), arc.at(rs), rd * -1.0)) add(p)
        }
        add(arc.at(rs))
    }

    var ls = -1.0
    var ld: Vec2? = null
    for ((o, span) in openings.filter { it.wallIndex == i }.map { it to arc.span(it.position, it.width) }.sortedBy { it.second.first }) {
        val s1 = kotlin.math.max(span.first, ls)
        val s2 = kotlin.math.max(span.second, s1)
        val dir = (arc.at(s2) - arc.at(s1)).normalized()
        gap(ls, ld, s1, dir * -1.0)
        pieceOf[o.id] = pts.size - 1
        add(arc.at(s2))
        ls = s2
        ld = dir
    }
    gap(ls, ld, 1.0, null)
    return CurvedWall(pts, pieceOf)
}

/** Raccordo morbido da `a` (che parte nella direzione `da`) a `b` (dove arriva nella direzione `db`), senza `a`. */
private fun blend(a: Vec2, da: Vec2, b: Vec2, db: Vec2): List<Vec2> {
    val d = b - a
    val det = da.x * db.y - da.y * db.x
    val t = if (abs(det) < 1e-9) -1.0 else (d.x * db.y - d.y * db.x) / det
    val w = if (abs(det) < 1e-9) -1.0 else (da.x * d.y - da.y * d.x) / det
    val control = if (t > 0 && w > 0) a + da * t else (a + b) / 2.0
    return (1..BLEND_STEPS).map { k ->
        val f = k.toDouble() / BLEND_STEPS
        a * ((1 - f) * (1 - f)) + control * (2 * f * (1 - f)) + b * (f * f)
    }
}

/** Stanza con i muri curvi fatti di pezzi dritti (per disegnare, misurare, costruire il 3D). Uguale se non ne ha. */
fun Room.expandCurves(): ExpandedRoom {
    val n = wallCount
    val arcs = (0 until n).map { arcOf(it) }
    if (arcs.all { it == null }) return ExpandedRoom(this, List(n) { it })
    val points = mutableListOf<Vec2>()
    val origin = mutableListOf<Int>()
    val first = IntArray(n)
    val walls = arrayOfNulls<CurvedWall>(n)
    for (i in 0 until n) {
        first[i] = origin.size
        val arc = arcs[i]
        if (arc == null) {
            points += this.points[i]
            origin += i
            continue
        }
        val w = curvedWall(i, arc)
        walls[i] = w
        points += this.points[i]
        for (k in 1 until w.points.size - 1) points += w.points[k]
        repeat(w.points.size - 1) { origin += i }
    }
    /** Pezzo del muro curvo `i` dove cade la posizione `t` (sulla linea dritta), e la posizione lungo quel pezzo. */
    fun place(i: Int, t: Double): Pair<Int, Double> {
        val arc = arcs[i] ?: return first[i] to t
        val pts = walls[i]!!.points
        val c = pts.map { (it - arc.a) dot arc.u }
        val k = (0 until pts.size - 1).firstOrNull { t <= c[it + 1] + 1e-9 } ?: (pts.size - 2)
        val f = if (abs(c[k + 1] - c[k]) < 1e-9) 0.5 else ((t - c[k]) / (c[k + 1] - c[k])).coerceIn(0.0, 1.0)
        return first[i] + k to f * pts[k].distanceTo(pts[k + 1])
    }
    fun <T> dup(m: Map<Int, T>): Map<Int, T> = buildMap { origin.forEachIndexed { j, i -> m[i]?.let { put(j, it) } } }
    return ExpandedRoom(
        copy(
            points = points,
            wallHeights = dup(wallHeights),
            wallCuts = buildMap { for ((i, v) in wallCuts) if (i < n) put(first[i], v) },
            wallPaints = dup(wallPaints),
            wallFinishes = dup(wallFinishes),
            wallThicknesses = dup(wallThicknesses),
            removedWalls = origin.indices.filter { origin[it] in removedWalls }.toSet(),
            wallPhases = dup(wallPhases),
            wallBulges = emptyMap(),
            openings = openings.map { o ->
                if (o.wallIndex >= n) return@map o
                val w = walls[o.wallIndex] ?: return@map o.copy(wallIndex = first[o.wallIndex])
                // L'apertura ha il suo pezzo dritto, largo come lei: sta a metà di quel pezzo.
                val k = w.openingPiece[o.id] ?: 0
                val len = w.points[k].distanceTo(w.points[k + 1])
                o.copy(wallIndex = first[o.wallIndex] + k, position = len / 2, width = kotlin.math.min(o.width, len))
            },
            fixtures = fixtures.map { f ->
                if (f.kind.mount != Mount.Wall || f.wallIndex >= n) f
                else place(f.wallIndex, f.position).let { (j, t) -> f.copy(wallIndex = j, position = t) }
            },
        ),
        origin,
    )
}

/** Pianta con i muri curvi srotolati in pezzi dritti. */
class ExpandedPlan(val plan: FloorPlan, private val origins: Map<Long, List<Int>>) {
    /** Muro della stanza originale da cui viene il muro `index` della stanza srotolata. */
    fun originOf(roomId: Long, index: Int): Int = origins[roomId]?.getOrNull(index) ?: index
}

fun FloorPlan.expandCurves(): ExpandedPlan {
    if (rooms.none { r -> r.wallBulges.isNotEmpty() }) return ExpandedPlan(this, emptyMap())
    val expanded = rooms.map { it.expandCurves() }
    return ExpandedPlan(copy(rooms = expanded.map { it.room }), rooms.zip(expanded).associate { (r, e) -> r.id to e.origin })
}
