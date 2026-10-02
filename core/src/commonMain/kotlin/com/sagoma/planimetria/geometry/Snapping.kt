package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs

object Snapping {

    /** Raggio di "richiamo magnetico" dell'angolo trascinato (§2). */
    const val CORNER_SNAP_CM = 22.0

    /** Tolleranza per raddrizzare le piccole imprecisioni residue sugli altri muri. */
    const val STRAIGHTEN_CM = 6.0

    /** Distanza entro cui un angolo di una stanza spostata si appoggia a un angolo di un'altra (§2). */
    const val ROOM_SNAP_CM = 25.0

    private const val PROPAGATION_PASSES = 4

    /**
     * Posizioni in cui l'angolo `i` forma un angolo retto esatto con entrambi i muri adiacenti:
     * i due vertici del rettangolo "pulito" costruito sui vicini. Non dipende dalla forma di creazione.
     */
    fun cornerCandidates(points: List<Vec2>, i: Int): List<Vec2> {
        val n = points.size
        val prev = points[(i - 1 + n) % n]
        val next = points[(i + 1) % n]
        return listOf(Vec2(prev.x, next.y), Vec2(next.x, prev.y))
    }

    /**
     * Sposta l'angolo `i` in `target`. Se è entro [CORNER_SNAP_CM] da una posizione "pulita", scatta
     * esattamente lì e raddrizza a cascata gli altri muri della stanza.
     * Ritorna i nuovi punti e se lo snap è avvenuto.
     */
    fun moveCorner(points: List<Vec2>, i: Int, target: Vec2): Pair<List<Vec2>, Boolean> {
        val moved = points.toMutableList().also { it[i] = target }
        val snap = cornerCandidates(moved, i)
            .map { it to it.distanceTo(target) }
            .filter { it.second <= CORNER_SNAP_CM }
            .minByOrNull { it.second }
            ?.first
            ?: return moved to false
        moved[i] = snap
        return straighten(moved, i) to true
    }

    /**
     * Raddrizza i muri quasi orizzontali/verticali partendo dall'angolo `anchor` (che non si muove)
     * e procedendo verso l'esterno in entrambe le direzioni; più passaggi per convergere anche sulle L.
     */
    fun straighten(points: List<Vec2>, anchor: Int, tolerance: Double = STRAIGHTEN_CM): List<Vec2> {
        val pts = points.toMutableList()
        val n = pts.size
        repeat(PROPAGATION_PASSES) {
            var changed = false
            // Metà anello in avanti e metà all'indietro: ogni muro viene sistemato muovendo l'estremo più lontano dall'ancora.
            for (step in 0 until n) {
                val forward = step % 2 == 0
                val k = step / 2 + 1
                val from = if (forward) (anchor + k - 1) % n else (anchor - k + 1 + n) % n
                val to = if (forward) (anchor + k) % n else (anchor - k + n) % n
                if (to == anchor) continue
                val a = pts[from]
                val b = pts[to]
                val dx = abs(b.x - a.x)
                val dy = abs(b.y - a.y)
                if (dx in 1e-9..tolerance && dy > tolerance) {
                    pts[to] = Vec2(a.x, b.y); changed = true
                } else if (dy in 1e-9..tolerance && dx > tolerance) {
                    pts[to] = Vec2(b.x, a.y); changed = true
                }
            }
            if (!changed) return pts
        }
        return pts
    }

    /** Sposta il muro `i` lungo la sua normale (allunga/restringe la stanza su quell'asse). */
    fun moveWall(points: List<Vec2>, i: Int, delta: Vec2): List<Vec2> {
        val n = points.size
        val a = points[i]
        val b = points[(i + 1) % n]
        val normal = (b - a).normalized().perp()
        val shift = normal * (delta dot normal)
        return points.toMutableList().also {
            it[i] = a + shift
            it[(i + 1) % n] = b + shift
        }
    }

    /**
     * Imposta la lunghezza esatta del muro `i`: l'angolo iniziale resta fermo e il muro successivo
     * trasla lungo la direzione del muro, così un rettangolo resta pulito.
     */
    fun setWallLength(points: List<Vec2>, i: Int, length: Double): List<Vec2> {
        val n = points.size
        val a = points[i]
        val b = points[(i + 1) % n]
        val dir = (b - a).normalized()
        val shift = dir * (length - a.distanceTo(b))
        return points.toMutableList().also {
            it[(i + 1) % n] = b + shift
            it[(i + 2) % n] = points[(i + 2) % n] + shift
        }
    }

    /**
     * Spostamento di una stanza intera: se un suo angolo arriva entro [ROOM_SNAP_CM] da un angolo
     * di un'altra stanza, corregge lo spostamento per farli coincidere. Altrimenti allinea i muri
     * paralleli e affiancati (vedi [wallSnapCorrection]), anche due alla volta su assi diversi.
     */
    fun snapRoomTranslation(room: Room, others: List<Room>, delta: Vec2): Vec2 {
        var best: Vec2? = null
        var bestD = ROOM_SNAP_CM
        for (p in room.points) {
            val moved = p + delta
            for (o in others) for (q in o.points) {
                val d = moved.distanceTo(q)
                if (d <= bestD) { bestD = d; best = q - p }
            }
        }
        best?.let { return it }

        val otherWalls = others.flatMap { wallsOf(it) }
        val first = bestWallSnap(wallsOf(room), otherWalls, delta, exclude = null) ?: return delta
        val afterFirst = delta + first.second
        // Secondo aggancio su un asse perpendicolare al primo (stanza che si incastra in un angolo).
        val second = bestWallSnap(wallsOf(room), otherWalls, afterFirst, exclude = first.first) ?: return afterFirst
        return afterFirst + second.second
    }

    /** Distanza entro cui un muro trascinato si allinea a un muro parallelo di un'altra stanza. */
    const val WALL_SNAP_CM = 25.0

    /** I muri di una stanza come segmenti in mezzeria. */
    fun wallsOf(room: Room): List<Pair<Vec2, Vec2>> = (0 until room.wallCount).map { room.wallStart(it) to room.wallEnd(it) }

    /**
     * Correzione (lungo la normale del muro a→b) che porta il muro sulla linea del muro parallelo più
     * vicino tra `others`, se è entro [WALL_SNAP_CM]. Vale per muri affiancati (così due muri avvicinati
     * si sovrappongono perfettamente e diventano un muro in comune) e per muri uno di seguito all'altro
     * con le estremità entro [WALL_SNAP_CM] (così si allineano a filo). Null se non c'è niente da agganciare.
     */
    fun wallSnapCorrection(a: Vec2, b: Vec2, others: List<Pair<Vec2, Vec2>>): Double? {
        val len = a.distanceTo(b)
        if (len < 1e-6) return null
        val u = (b - a) / len
        val n = u.perp()
        var best: Double? = null
        for ((c, d) in others) {
            val olen = c.distanceTo(d)
            if (olen < 1e-6) continue
            val v = (d - c) / olen
            if (abs(u.x * v.y - u.y * v.x) > PARALLEL_TOLERANCE) continue
            // Affiancati (si sovrappongono lungo la direzione del muro) oppure uno di seguito all'altro con
            // le estremità vicine: in quel caso l'aggancio li mette "a filo" sulla stessa linea.
            val tc = (c - a) dot u
            val td = (d - a) dot u
            if (minOf(maxOf(tc, td), len) - maxOf(minOf(tc, td), 0.0) < -WALL_SNAP_CM) continue
            val dist = (c - a) dot n
            if (abs(dist) <= WALL_SNAP_CM && (best == null || abs(dist) < abs(best))) best = dist
        }
        return best
    }

    /** Il miglior aggancio muro-su-muro per la stanza spostata di `delta`: normale usata e correzione. */
    private fun bestWallSnap(
        walls: List<Pair<Vec2, Vec2>>,
        others: List<Pair<Vec2, Vec2>>,
        delta: Vec2,
        exclude: Vec2?,
    ): Pair<Vec2, Vec2>? {
        var best: Pair<Vec2, Vec2>? = null
        for ((a, b) in walls) {
            val n = (b - a).normalized().perp()
            // Il secondo aggancio non deve disfare il primo: solo muri perpendicolari a quello.
            if (exclude != null && abs(n dot exclude) > 0.1) continue
            val dist = wallSnapCorrection(a + delta, b + delta, others) ?: continue
            if (best == null || abs(dist) < best.second.length) best = n to n * dist
        }
        return best
    }

    /**
     * Trascinamento del muro `i` con aggancio: si sposta lungo la normale come [moveWall] e, se arriva
     * vicino a un muro parallelo di un'altra stanza, scatta esattamente sulla sua linea.
     */
    fun moveWallSnapped(points: List<Vec2>, i: Int, delta: Vec2, otherWalls: List<Pair<Vec2, Vec2>>): List<Vec2> {
        val moved = moveWall(points, i, delta)
        val a = moved[i]
        val b = moved[(i + 1) % moved.size]
        val correction = wallSnapCorrection(a, b, otherWalls) ?: return moved
        val shift = (b - a).normalized().perp() * correction
        return moved.toMutableList().also {
            it[i] = a + shift
            it[(i + 1) % moved.size] = b + shift
        }
    }

    /** Tolleranza (seno dell'angolo) per considerare paralleli due muri. */
    private const val PARALLEL_TOLERANCE = 0.02
}
