package com.sagoma.planimetria.reading

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs

/**
 * Stanze lette che si sovrappongono (succede con le AI meno precise): tra due stanze c'è sempre un muro, quindi una delle due
 * cede la parte in comune più un muro divisorio, e le due finiscono ai lati di quel muro.
 *
 * Cede la stanza che sembra sbagliata: quella disegnata più grande di quanto è scritto sulla piantina; poi i balconi rispetto
 * alle stanze interne, quella senza metri quadri scritti rispetto a quella che li rispetta, e infine la più grande. Solo stanze con i lati dritti (orizzontali e verticali): le altre restano come sono.
 */
internal object RoomOverlaps {
    /** Muro divisorio tra le due stanze dopo la correzione (cm). */
    const val WALL = 10.0
    /** Sotto questa area in comune (cm²) la sovrapposizione è un'imprecisione dei muri, non una stanza dentro l'altra. */
    private const val MIN_OVERLAP = 2_000.0

    /**
     * [rooms]: contorni sul filo interno (cm), con il senso di [RoomFactory.normalizeWinding]. [written]: i metri quadri scritti
     * (null se mancano), [excluded]: le zone non calpestabili (cm²) da togliere al disegnato, [outdoor]: balconi e terrazzi.
     */
    fun separated(rooms: List<List<Vec2>>, written: List<Double?>, excluded: List<Double>, outdoor: List<Boolean>): List<List<Vec2>> {
        val result = rooms.toMutableList()
        val excess = rooms.indices.map { i ->
            val w = written[i]?.takeIf { it > 0 } ?: return@map 0.0
            (Polygon.area(rooms[i]) - excluded[i]) / 10_000 / w - 1
        }
        // Prima chi tiene la sua forma, ultima chi cede.
        val order = rooms.indices.sortedWith(
            compareBy<Int> { excess[it] > 0.1 }.thenBy { outdoor[it] }.thenBy { written[it] == null }.thenBy { Polygon.area(rooms[it]) },
        )
        for ((rank, keep) in order.withIndex()) {
            if (!rectilinear(result[keep])) continue
            for (lose in order.drop(rank + 1)) {
                if (!rectilinear(result[lose]) || shared(result[keep], result[lose]) < MIN_OVERLAP) continue
                val cutter = Polygon.inset(result[keep], -WALL)
                result[lose] = minus(result[lose], cutter) ?: continue
            }
        }
        return result
    }

    fun rectilinear(pts: List<Vec2>): Boolean = pts.indices.all { i ->
        val d = pts[(i + 1) % pts.size] - pts[i]
        abs(d.x) < 1e-6 || abs(d.y) < 1e-6
    }

    /** Area in comune tra due contorni con i lati dritti (cm²). */
    fun shared(a: List<Vec2>, b: List<Vec2>): Double {
        val g = Grid(a + b)
        var area = 0.0
        g.forEachCell { i, j, c -> if (Polygon.contains(a, c) && Polygon.contains(b, c)) area += g.cellArea(i, j) }
        return area
    }

    /**
     * [subject] senza la parte dentro [cutter]: il pezzo più grande che resta, con i lati dritti. Null se non resta niente o se il
     * contorno non si ricostruisce bene.
     */
    fun minus(subject: List<Vec2>, cutter: List<Vec2>): List<Vec2>? {
        val g = Grid(subject + cutter)
        val cells = HashSet<Long>()
        g.forEachCell { i, j, c -> if (Polygon.contains(subject, c) && !Polygon.contains(cutter, c)) cells += key(i, j) }
        val piece = largestPiece(cells).ifEmpty { return null }
        val outline = outline(piece, g) ?: return null
        val expected = piece.sumOf { g.cellArea(col(it), row(it)) }
        if (abs(Polygon.area(outline) - expected) > expected * 0.01) return null
        return RoomFactory.normalizeWinding(outline)
    }

    /** Griglia sulle x e le y di tutti gli angoli: ogni cella sta tutta dentro o tutta fuori di ogni contorno con i lati dritti. */
    private class Grid(points: List<Vec2>) {
        val xs = points.map { it.x }.distinct().sorted()
        val ys = points.map { it.y }.distinct().sorted()

        fun cellArea(i: Int, j: Int) = (xs[i + 1] - xs[i]) * (ys[j + 1] - ys[j])

        inline fun forEachCell(action: (i: Int, j: Int, center: Vec2) -> Unit) {
            for (i in 0 until xs.size - 1) for (j in 0 until ys.size - 1) {
                action(i, j, Vec2((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2))
            }
        }
    }

    private fun key(i: Int, j: Int) = (i.toLong() shl 32) or (j.toLong() and 0xffffffffL)
    private fun col(k: Long) = (k shr 32).toInt()
    private fun row(k: Long) = k.toInt()

    /** Il gruppo più grande di celle unite per un lato. */
    private fun largestPiece(cells: Set<Long>): Set<Long> {
        val seen = HashSet<Long>()
        var best = emptySet<Long>()
        for (start in cells) {
            if (start in seen) continue
            val piece = HashSet<Long>()
            val stack = ArrayDeque(listOf(start))
            seen += start
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                piece += c
                for ((di, dj) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val n = key(col(c) + di, row(c) + dj)
                    if (n in cells && seen.add(n)) stack += n
                }
            }
            if (piece.size > best.size) best = piece
        }
        return best
    }

    /**
     * Il contorno esterno di un gruppo di celle: i lati delle celle senza vicina, concatenati sempre nello stesso verso; dei giri
     * che si formano (il contorno e gli eventuali buchi) si tiene il più grande. Angoli sulla stessa linea tolti.
     */
    private fun outline(piece: Set<Long>, g: Grid): List<Vec2>? {
        val next = HashMap<Long, MutableList<Long>>()
        fun edge(a: Long, b: Long) = next.getOrPut(a) { mutableListOf() }.add(b)
        for (c in piece) {
            val i = col(c)
            val j = row(c)
            if (key(i, j - 1) !in piece) edge(key(i, j), key(i + 1, j))
            if (key(i + 1, j) !in piece) edge(key(i + 1, j), key(i + 1, j + 1))
            if (key(i, j + 1) !in piece) edge(key(i + 1, j + 1), key(i, j + 1))
            if (key(i - 1, j) !in piece) edge(key(i, j + 1), key(i, j))
        }
        val loops = mutableListOf<List<Vec2>>()
        while (next.isNotEmpty()) {
            val start = next.keys.first()
            val loop = mutableListOf<Long>()
            var v = start
            do {
                loop += v
                val outs = next[v] ?: return null
                val to = outs.removeAt(outs.size - 1)
                if (outs.isEmpty()) next.remove(v)
                v = to
            } while (v != start && loop.size <= 4 * piece.size + 4)
            if (v != start) return null
            loops += loop.map { Vec2(g.xs[col(it)], g.ys[row(it)]) }
        }
        val best = loops.maxByOrNull { Polygon.area(it) } ?: return null
        return best.filterIndexed { i, p ->
            val a = best[(i + best.size - 1) % best.size]
            val b = best[(i + 1) % best.size]
            abs((p - a).cross(b - p)) > 1e-9
        }.takeIf { it.size >= 4 }
    }
}
