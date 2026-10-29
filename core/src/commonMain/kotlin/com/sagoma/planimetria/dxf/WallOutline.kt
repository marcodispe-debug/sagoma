package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Contorno dei muri per il disegno tecnico: i pezzi di muro (poligoni convessi, uno per muro di ogni stanza, già senza i vani di
 * porte e finestre) si sovrappongono dove due stanze hanno il muro in comune o si toccano agli angoli. Qui resta solo il bordo
 * della loro unione, senza linee doppie né linee dentro il muro.
 *
 * Ogni lato si spezza dove incontra gli altri; un pezzo di lato è bordo se da una parte c'è muro e dall'altra no. Lati e pezzi
 * stanno in griglie, così ogni lato si confronta solo con quelli vicini (anche le case da mille stanze fanno presto).
 */
object WallOutline {
    /** Distanza (cm) a cui si guarda ai due lati di un pezzo di bordo. */
    private const val SIDE = 0.02

    /** Margine (cm) attorno ai rettangoli che contengono i lati. */
    private const val MARGIN = 0.01

    /** Caselle al massimo che un oggetto tocca in una griglia (oltre va in quella più grande). */
    private const val MAX_CELLS = 4096.0

    /**
     * Un pezzo convesso. I punti doppi (i pezzi tagliati dal vano di una finestra con lo sguincio o da un muro tolto ne hanno)
     * si tolgono: con un lato lungo zero nessun punto starebbe dentro e attorno resterebbero linee dentro il muro.
     */
    private class Piece(points: List<Vec2>) {
        private val pts = points.filterIndexed { i, p -> p.distanceTo(points[(i + 1) % points.size]) > 1e-9 }
        val xs = DoubleArray(pts.size) { pts[it].x }
        val ys = DoubleArray(pts.size) { pts[it].y }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        private val sign = if (signedArea(pts) >= 0) 1.0 else -1.0

        /** Il punto sta dentro il pezzo (non sul bordo). */
        fun holds(x: Double, y: Double): Boolean {
            if (x <= minX || x >= maxX || y <= minY || y >= maxY) return false
            var a = xs.size - 1
            for (b in xs.indices) {
                if (((xs[b] - xs[a]) * (y - ys[a]) - (ys[b] - ys[a]) * (x - xs[a])) * sign <= 0) return false
                a = b
            }
            return true
        }
    }

    /** Lista di numeri interi che cresce, senza scatole. */
    private class Ids {
        var items = IntArray(4)
        var size = 0
        fun add(id: Int) {
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = id
        }
    }

    /** Lo stesso, per i punti di taglio lungo un lato. */
    private class Doubles {
        var items = DoubleArray(8)
        var size = 0
        fun add(v: Double) {
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = v
        }
    }

    /**
     * Griglia di caselle quadrate di lato `cell`: in ogni casella gli oggetti che la toccano (i lati seguendo la loro linea, non
     * tutto il rettangolo che li contiene: un lato in diagonale ne tocca poche).
     */
    private class Grid(private val cell: Double) {
        val cells = HashMap<Long, Ids>()
        var buf = LongArray(16)

        fun index(v: Double) = floor(v / cell)
        fun keyAt(x: Double, y: Double) = key(index(x), index(y))
        // Moltiplicato per un numero dispari: resta un numero diverso per ogni casella, ma con i bit ben mescolati per la tabella.
        fun key(i: Double, j: Double) = ((i.toLong() shl 32) xor (j.toLong() and 0xffffffffL)) * -7046029254386353131L

        /**
         * Le caselle toccate dal rettangolo (`segment` falso) o dal lato da (ax, ay) a (bx, by), allargati di MARGIN: in `buf`,
         * ognuna una volta. Restituisce quante sono, o −1 se sono troppe.
         */
        fun collect(ax: Double, ay: Double, bx: Double, by: Double, segment: Boolean): Int {
            var count = 0
            fun box(x0: Double, y0: Double, x1: Double, y1: Double) {
                var i = index(min(x0, x1) - MARGIN)
                while (i <= index(max(x0, x1) + MARGIN)) {
                    var j = index(min(y0, y1) - MARGIN)
                    while (j <= index(max(y0, y1) + MARGIN)) {
                        if (count == buf.size) buf = buf.copyOf(count * 2)
                        buf[count++] = key(i, j)
                        j++
                    }
                    i++
                }
            }
            if (!segment) {
                val n = (index(bx + MARGIN) - index(ax - MARGIN) + 1) * (index(by + MARGIN) - index(ay - MARGIN) + 1)
                if (!(n <= MAX_CELLS)) return -1
                box(ax, ay, bx, by)
            } else {
                // A pezzetti lunghi al più una casella: ognuno tocca 4 caselle o poco più.
                val steps = ceil(max(abs(bx - ax), abs(by - ay)) / cell).coerceAtLeast(1.0)
                if (!(steps * 4 <= MAX_CELLS)) return -1
                val k = steps.toInt()
                for (s in 0 until k) {
                    box(ax + (bx - ax) * s / k, ay + (by - ay) * s / k, ax + (bx - ax) * (s + 1) / k, ay + (by - ay) * (s + 1) / k)
                }
            }
            buf.sort(0, count)
            var unique = 0
            for (c in 0 until count) if (c == 0 || buf[c] != buf[c - 1]) buf[unique++] = buf[c]
            return unique
        }

        /** Mette l'oggetto nelle sue caselle; falso (e non lo mette) se ne toccherebbe troppe. */
        fun add(id: Int, ax: Double, ay: Double, bx: Double, by: Double, segment: Boolean): Boolean {
            val count = collect(ax, ay, bx, by, segment)
            if (count < 0) return false
            for (c in 0 until count) cells.getOrPut(buf[c]) { Ids() }.add(id)
            return true
        }
    }

    /**
     * Griglie sempre più grandi (ogni casella 32 volte quella prima): ogni oggetto sta nella più fine dove tocca poche caselle.
     * Così anche i lati lunghi chilometri di un progetto rovinato si trovano subito, senza guardarli tutti a ogni lato.
     */
    private class Levels(cell: Double, private val count: Int) {
        val grids = List(LEVELS) { k -> Grid(cell * POWERS[k]) }

        /** Troppo grandi anche per l'ultima griglia: si guardano sempre. */
        val huge = Ids()
        val levelOf = IntArray(count)

        fun add(id: Int, ax: Double, ay: Double, bx: Double, by: Double, segment: Boolean) {
            for ((k, g) in grids.withIndex()) if (g.add(id, ax, ay, bx, by, segment)) { levelOf[id] = k; return }
            levelOf[id] = LEVELS
            huge.add(id)
        }

        /**
         * Gli oggetti della griglia `from` o di quelle più grandi che possono toccare il lato, ognuno una volta sola (`seen` segna
         * quelli già dati).
         */
        inline fun near(from: Int, ax: Double, ay: Double, bx: Double, by: Double, seen: IntArray, stamp: Int, f: (Int) -> Unit) {
            for (k in from until LEVELS) {
                val g = grids[k]
                val n = g.collect(ax, ay, bx, by, segment = true)
                if (n < 0) {
                    // Non succede (un lato che sta in una griglia sta anche in quelle più grandi), ma per sicurezza: tutti.
                    for (id in 0 until count) if (levelOf[id] == k && seen[id] != stamp) { seen[id] = stamp; f(id) }
                    continue
                }
                for (c in 0 until n) {
                    val ids = g.cells[g.buf[c]] ?: continue
                    for (i in 0 until ids.size) {
                        val id = ids.items[i]
                        if (seen[id] != stamp) { seen[id] = stamp; f(id) }
                    }
                }
            }
            for (i in 0 until huge.size) {
                val id = huge.items[i]
                if (seen[id] != stamp) { seen[id] = stamp; f(id) }
            }
        }

        /** Vero se `f` è vero per uno degli oggetti nelle caselle del punto. */
        inline fun any(x: Double, y: Double, f: (Int) -> Boolean): Boolean {
            for (g in grids) {
                val ids = g.cells[g.keyAt(x, y)] ?: continue
                for (i in 0 until ids.size) if (f(ids.items[i])) return true
            }
            for (i in 0 until huge.size) if (f(huge.items[i])) return true
            return false
        }

        companion object {
            const val LEVELS = 8
            val POWERS = DoubleArray(LEVELS) { k -> var p = 1.0; repeat(k) { p *= 32 }; p }
        }
    }

    fun boundary(pieces: List<List<Vec2>>): List<Pair<Vec2, Vec2>> = merge(edges(pieces))

    /**
     * Superficie (cm²) dell'unione dei pezzi convessi, dal suo bordo girato con l'unione a sinistra (formula di Gauss): due pezzi
     * che si sovrappongono contano una volta sola.
     */
    fun unionArea(pieces: List<List<Vec2>>): Double {
        var s = 0.0
        for ((a, b) in mergeDirected(edges(pieces))) s += a.x * b.y - b.x * a.y
        return s / 2
    }

    /** I pezzi del bordo dell'unione, ognuno girato con l'unione alla sua sinistra (doppioni compresi). */
    private fun edges(pieces: List<List<Vec2>>): List<Pair<Vec2, Vec2>> {
        val ps = pieces.filter { pc -> pc.size >= 3 && pc.all { it.x.isFinite() && it.y.isFinite() } && abs(signedArea(pc)) > 1e-4 }
            .map { Piece(it) }
        // I lati, con il pezzo da cui vengono.
        val edges = ps.withIndex().flatMap { (i, p) -> p.xs.indices.map { k -> val n = (k + 1) % p.xs.size; Triple(Vec2(p.xs[k], p.ys[k]), Vec2(p.xs[n], p.ys[n]), i) } }
            .filter { (a, b) -> a.distanceTo(b) > 1e-3 }
        if (edges.isEmpty()) return emptyList()
        val n = edges.size
        val ax = DoubleArray(n) { edges[it].first.x }
        val ay = DoubleArray(n) { edges[it].first.y }
        val bx = DoubleArray(n) { edges[it].second.x }
        val by = DoubleArray(n) { edges[it].second.y }
        val owner = IntArray(n) { edges[it].third }
        // Caselle grandi più o meno come metà di un lato normale (pochi lati per casella, poche caselle per lato), ma non più del
        // doppio del lato a metà per lunghezza: pochi lati lunghissimi di un progetto rovinato non devono ingrandirle tutte.
        val extents = DoubleArray(n) { max(abs(bx[it] - ax[it]), abs(by[it] - ay[it])) }.also { it.sort() }
        val cell = max(1.0, min(extents.average() / 2, extents[n / 2] * 2))
        val edgeGrids = Levels(cell, n)
        for (k in 0 until n) edgeGrids.add(k, ax[k], ay[k], bx[k], by[k], segment = true)
        val pieceGrids = Levels(cell, ps.size)
        for ((k, p) in ps.withIndex()) pieceGrids.add(k, p.minX, p.minY, p.maxX, p.maxY, segment = false)
        fun inside(x: Double, y: Double) = pieceGrids.any(x, y) { ps[it].holds(x, y) }

        // Prima i lati delle griglie fini. Dove uno incontra un lato più grande, il taglio si segna anche su quello, che poi cerca
        // solo i lati grandi come lui o di più: così nessuna coppia si perde e i lati lunghi non passano tutte le caselle fini.
        val order = (0 until n).sortedBy { edgeGrids.levelOf[it] }
        val marked = HashMap<Int, Doubles>()
        fun mark(edge: Int, t: Double) = marked.getOrPut(edge) { Doubles() }.add(t)

        val seen = IntArray(n) { -1 }
        val kept = mutableListOf<Pair<Vec2, Vec2>>()
        val ts = Doubles()
        for (index in order) {
            val level = edgeGrids.levelOf[index]
            val dx = bx[index] - ax[index]
            val dy = by[index] - ay[index]
            val len2 = dx * dx + dy * dy
            ts.size = 0
            ts.add(0.0)
            ts.add(1.0)
            marked.remove(index)?.let { m -> for (i in 0 until m.size) ts.add(m.items[i]) }
            val eMinX = min(ax[index], bx[index]) - MARGIN; val eMaxX = max(ax[index], bx[index]) + MARGIN
            val eMinY = min(ay[index], by[index]) - MARGIN; val eMaxY = max(ay[index], by[index]) + MARGIN
            edgeGrids.near(level, ax[index], ay[index], bx[index], by[index], seen, index) { other ->
                val cx = ax[other]; val cy = ay[other]; val ex = bx[other]; val ey = by[other]
                if (max(cx, ex) < eMinX || min(cx, ex) > eMaxX || max(cy, ey) < eMinY || min(cy, ey) > eMaxY) return@near
                // L'altro lato è in una griglia più grande: non verrà a cercare questo, il suo taglio si segna adesso.
                val bigger = edgeGrids.levelOf[other] > level
                val fx = ex - cx
                val fy = ey - cy
                val f2 = fx * fx + fy * fy
                val den = dx * fy - dy * fx
                val wx = cx - ax[index]
                val wy = cy - ay[index]
                if (den * den > 1e-18 * len2 * f2) {
                    val t = (wx * fy - wy * fx) / den
                    val s = (wx * dy - wy * dx) / den
                    if (t > 0 && t < 1 && s >= -1e-9 && s <= 1 + 1e-9) ts.add(t)
                    if (bigger && s > 0 && s < 1 && t >= -1e-9 && t <= 1 + 1e-9) mark(other, s)
                } else {
                    // Sulla stessa linea: si spezza dove finisce l'altro lato (scostamento² ≤ (1e-3)²).
                    for (end in 0..1) {
                        val px = (if (end == 0) cx else ex) - ax[index]
                        val py = (if (end == 0) cy else ey) - ay[index]
                        val cross = px * dy - py * dx
                        if (cross * cross > 1e-6 * len2) continue
                        val t = (px * dx + py * dy) / len2
                        if (t > 0 && t < 1) ts.add(t)
                    }
                    if (bigger) for (end in 0..1) {
                        val px = (if (end == 0) ax[index] else bx[index]) - cx
                        val py = (if (end == 0) ay[index] else by[index]) - cy
                        val cross = px * fy - py * fx
                        if (cross * cross > 1e-6 * f2) continue
                        val t = (px * fx + py * fy) / f2
                        if (t > 0 && t < 1) mark(other, t)
                    }
                }
            }
            ts.items.sort(0, ts.size)
            val len = sqrt(len2)
            val nx = -dy / len * SIDE
            val ny = dx / len * SIDE
            val own = ps[owner[index]]
            for (k in 0 until ts.size - 1) {
                val t0 = ts.items[k]
                val t1 = ts.items[k + 1]
                if ((t1 - t0) * len < 1e-3) continue
                val t = (t0 + t1) / 2
                val mx = ax[index] + dx * t
                val my = ay[index] + dy * t
                // Da una parte di solito c'è il pezzo del lato stesso: si guarda prima lui.
                val left = own.holds(mx + nx, my + ny) || inside(mx + nx, my + ny)
                val right = own.holds(mx - nx, my - ny) || inside(mx - nx, my - ny)
                if (left == right) continue
                val p0 = Vec2(ax[index] + dx * t0, ay[index] + dy * t0)
                val p1 = Vec2(ax[index] + dx * t1, ay[index] + dy * t1)
                kept += if (left) p0 to p1 else p1 to p0
            }
        }
        return kept
    }

    /**
     * Come [merge], ma tiene il verso: pezzi sulla stessa linea e nello stesso verso che si toccano o si sovrappongono diventano
     * uno solo (i doppioni, dove due pezzi hanno un lato in comune, non contano due volte nell'area).
     */
    private fun mergeDirected(segments: List<Pair<Vec2, Vec2>>): List<Pair<Vec2, Vec2>> {
        class Line(val angle: Double, val offset: Double, val intervals: MutableList<Pair<Double, Double>> = mutableListOf())
        val lines = LinkedHashMap<Pair<Long, Long>, Line>()
        for ((a, b) in segments) {
            val d = (b - a).normalized()
            var angle = atan2(d.y, d.x)
            // −180° e 180° sono lo stesso verso.
            if (angle <= -kotlin.math.PI + 1e-7) angle += 2 * kotlin.math.PI
            val offset = d.x * a.y - d.y * a.x
            val key = round(angle * 1e5).toLong() to round(offset * 100).toLong()
            val line = lines.getOrPut(key) { Line(angle, offset) }
            line.intervals += (a dot d) to (b dot d)
        }
        val out = mutableListOf<Pair<Vec2, Vec2>>()
        for (l in lines.values) {
            val d = Vec2(kotlin.math.cos(l.angle), kotlin.math.sin(l.angle))
            val n = Vec2(-d.y, d.x)
            fun at(t: Double) = d * t + n * l.offset
            val sorted = l.intervals.sortedBy { it.first }
            var s = sorted.first().first
            var e = sorted.first().second
            for ((x, y) in sorted.drop(1)) {
                if (x <= e + 0.01) e = max(e, y)
                else { out += at(s) to at(e); s = x; e = y }
            }
            out += at(s) to at(e)
        }
        return out
    }

    /** Pezzi sulla stessa linea che si toccano o si sovrappongono diventano una linea sola (e i doppioni spariscono). */
    private fun merge(segments: List<Pair<Vec2, Vec2>>): List<Pair<Vec2, Vec2>> {
        class Line(val angle: Double, val offset: Double, val intervals: MutableList<Pair<Double, Double>> = mutableListOf())
        val lines = LinkedHashMap<Pair<Long, Long>, Line>()
        for ((a, b) in segments) {
            var d = (b - a).normalized()
            if (d.x < -1e-9 || (abs(d.x) <= 1e-9 && d.y < 0)) d = d * -1.0
            var angle = atan2(d.y, d.x)
            // La stessa linea verticale può dare −90° o 90°.
            if (angle <= -kotlin.math.PI / 2 + 1e-7) { angle += kotlin.math.PI; d = d * -1.0 }
            val offset = d.x * a.y - d.y * a.x
            val key = round(angle * 1e5).toLong() to round(offset * 100).toLong()
            val line = lines.getOrPut(key) { Line(angle, offset) }
            val t1 = a dot d
            val t2 = b dot d
            line.intervals += min(t1, t2) to max(t1, t2)
        }
        val out = mutableListOf<Pair<Vec2, Vec2>>()
        for (l in lines.values) {
            val d = Vec2(kotlin.math.cos(l.angle), kotlin.math.sin(l.angle))
            val n = Vec2(-d.y, d.x)
            fun at(t: Double) = d * t + n * l.offset
            val sorted = l.intervals.sortedBy { it.first }
            var s = sorted.first().first
            var e = sorted.first().second
            for ((x, y) in sorted.drop(1)) {
                if (x <= e + 0.01) e = max(e, y)
                else { out += at(s) to at(e); s = x; e = y }
            }
            out += at(s) to at(e)
        }
        return out
    }

    private fun signedArea(pts: List<Vec2>): Double {
        var s = 0.0
        for (k in pts.indices) {
            val a = pts[k]
            val b = pts[(k + 1) % pts.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2
    }
}
