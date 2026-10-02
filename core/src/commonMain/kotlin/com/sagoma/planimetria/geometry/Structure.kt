package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Beam
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Colonne e travi: contorni in pianta, aggancio ai muri, quota del soffitto sopra di loro. */
object Structure {
    /** Distanza entro cui una colonna o l'estremità di una trave si accosta alla faccia di un muro. */
    const val SNAP = 15.0
    const val MIN_SIZE = 10.0

    /** Contorno della colonna in pianta: cerchio (24 lati) o rettangolo ruotato. */
    fun outline(c: Column): List<Vec2> = when (c.shape) {
        ColumnShape.Round -> (0 until 24).map { k ->
            val a = k * kotlin.math.PI * 2 / 24
            c.center + Vec2(cos(a), sin(a)) * (c.width / 2)
        }
        ColumnShape.Square -> {
            val rad = toRadians(c.rotation)
            val u = Vec2(cos(rad), sin(rad))
            val v = u.perp()
            val hw = c.width / 2
            val hd = c.depth / 2
            listOf(c.center - u * hw - v * hd, c.center + u * hw - v * hd, c.center + u * hw + v * hd, c.center - u * hw + v * hd)
        }
    }

    /** Contorno della trave in pianta. */
    fun outline(b: Beam): List<Vec2> {
        val len = b.length
        if (len < 1e-6) return listOf(b.start, b.start, b.start)
        val n = (b.end - b.start).perp() / len * (b.width / 2)
        return listOf(b.start - n, b.end - n, b.end + n, b.start + n)
    }

    fun contains(c: Column, p: Vec2): Boolean =
        if (c.shape == ColumnShape.Round) p.distanceTo(c.center) <= c.width / 2 else Polygon.contains(outline(c), p)

    fun contains(b: Beam, p: Vec2): Boolean = Polygon.distanceToSegment(p, b.start, b.end) <= b.width / 2

    /**
     * Spostamento che accosta il rettangolo `b` alla faccia (interna o esterna) di un muro dritto vicino,
     * orizzontale o verticale, sui due assi. Zero se non c'è niente di abbastanza vicino.
     */
    /** Un muro qualsiasi (di una stanza o singolo): mezzeria da `a` a `b`, mezzo spessore `half`. */
    class WallLine(val a: Vec2, val b: Vec2, val half: Double)

    /** Tutti i muri del piano, esclusi i muri singoli con id in `except`. */
    fun wallLines(plan: FloorPlan, except: Long? = null): List<WallLine> =
        plan.rooms.flatMap { r -> (0 until r.wallCount).map { WallLine(r.wallStart(it), r.wallEnd(it), r.thicknessOf(it) / 2) } } +
            plan.freeWalls.filter { it.id != except }.map { WallLine(it.start, it.end, it.thickness / 2) }

    fun wallSnap(plan: FloorPlan, b: Bounds, distance: Double = SNAP, except: Long? = null): Vec2 {
        var bestDx: Double? = null
        var bestDy: Double? = null
        fun better(cur: Double?, v: Double) = if (abs(v) < distance && (cur == null || abs(v) < abs(cur))) v else cur
        for (line in wallLines(plan, except)) {
            val a = line.a
            val e = line.b
            val half = line.half
            if (abs(a.y - e.y) < 0.5 && minOf(a.x, e.x) < b.maxX && maxOf(a.x, e.x) > b.minX) {
                for (f in listOf(a.y - half, a.y + half)) {
                    bestDy = better(bestDy, f - b.minY)
                    bestDy = better(bestDy, f - b.maxY)
                }
            }
            if (abs(a.x - e.x) < 0.5 && minOf(a.y, e.y) < b.maxY && maxOf(a.y, e.y) > b.minY) {
                for (f in listOf(a.x - half, a.x + half)) {
                    bestDx = better(bestDx, f - b.minX)
                    bestDx = better(bestDx, f - b.maxX)
                }
            }
        }
        return Vec2(bestDx ?: 0.0, bestDy ?: 0.0)
    }

    /**
     * Colonna spostata: il centro va sull'estremità di una trave vicina; altrimenti si accosta alla faccia
     * di un muro (pilastro che sporge dal muro); sugli assi rimasti liberi si allinea alle altre colonne
     * (maglia strutturale).
     */
    fun snapped(plan: FloorPlan, c: Column): Column {
        plan.beams.flatMap { listOf(it.start, it.end) }.filter { it.distanceTo(c.center) < SNAP }
            .minByOrNull { it.distanceTo(c.center) }?.let { return c.copy(center = it) }
        val w = wallSnap(plan, Polygon.bounds(outline(c)))
        val others = plan.columns.filter { it.id != c.id }
        fun align(cur: Double, values: List<Double>) = values.filter { abs(it - cur) < SNAP }.minByOrNull { abs(it - cur) }?.minus(cur) ?: 0.0
        val dx = if (w.x != 0.0) w.x else align(c.center.x, others.map { it.center.x })
        val dy = if (w.y != 0.0) w.y else align(c.center.y, others.map { it.center.y })
        return c.copy(center = c.center + Vec2(dx, dy))
    }

    /**
     * Estremità di una trave: se è vicina alla faccia di un muro, ci si appoggia (lungo la direzione della
     * trave, così la trave resta dritta). `other` è l'altra estremità.
     */
    fun snapEnd(plan: FloorPlan, p: Vec2, other: Vec2, except: Long? = null): Vec2 {
        val dir = (p - other).normalized()
        var best: Vec2? = null
        var bestD = SNAP * 1.5
        for (line in wallLines(plan, except)) {
            val a = line.a
            val e = line.b
            val half = line.half
            val len = a.distanceTo(e)
            if (len < 1.0) continue
            val u = (e - a) / len
            val n = u.perp()
            for (side in listOf(-1.0, 1.0)) {
                val fa = a + n * (side * half)
                // Intersezione della retta della trave con la faccia del muro.
                val den = dir dot n
                if (abs(den) < 0.2) continue
                val t = ((fa - p) dot n) / den
                if (abs(t) > bestD) continue
                val q = p + dir * t
                val along = (q - fa) dot u
                if (along < -half || along > len + half) continue
                best = q
                bestD = abs(t)
            }
        }
        return best ?: p
    }

    // ---------- Muri singoli ----------

    fun outline(w: FreeWall): List<Vec2> {
        val len = w.length
        if (len < 1e-6) return listOf(w.start, w.start, w.start)
        val n = (w.end - w.start).perp() / len * (w.thickness / 2)
        return listOf(w.start - n, w.end - n, w.end + n, w.start + n)
    }

    fun contains(w: FreeWall, p: Vec2): Boolean = Polygon.distanceToSegment(p, w.start, w.end) <= w.thickness / 2

    /**
     * Estremità di un muro singolo: si unisce all'estremità di un altro muro singolo vicino (angolo), oppure
     * si appoggia alla faccia di un muro vicino (a T). `other` è l'altra estremità, `id` il muro stesso.
     */
    fun snapWallEnd(plan: FloorPlan, p: Vec2, other: Vec2, id: Long): Vec2 {
        val corner = plan.freeWalls.filter { it.id != id }.flatMap { listOf(it.start, it.end) }
            .filter { it.distanceTo(p) < SNAP }.minByOrNull { it.distanceTo(p) }
        return corner ?: snapEnd(plan, p, other, except = id)
    }

    /**
     * Muro singolo spostato intero: si accosta a un muro parallelo vicino e le estremità si appoggiano alle
     * facce dei muri lungo la sua direzione. (L'unione con le estremità di altri muri vale solo trascinando
     * un'estremità: spostando tutto il muro lo storcerebbe.)
     */
    fun snapped(plan: FloorPlan, w: FreeWall): FreeWall {
        val d = wallSnap(plan, Polygon.bounds(outline(w)), except = w.id)
        val moved = w.copy(start = w.start + d, end = w.end + d)
        return moved.copy(start = snapEnd(plan, moved.start, moved.end, except = w.id), end = snapEnd(plan, moved.end, moved.start, except = w.id))
    }

    /** Altezza del muro singolo: quella scelta (mai oltre il soffitto), altrimenti fino al soffitto. */
    fun freeWallHeight(plan: FloorPlan, w: FreeWall, levelHeight: Double): Double {
        val ceiling = ceilingAt(plan, w.mid, levelHeight)
        return w.height?.coerceIn(1.0, ceiling) ?: ceiling
    }

    /** Muro singolo proposto: attraversa la stanza nel verso più corto, da un muro all'altro (la divide in due). */
    fun defaultFreeWall(id: Long, room: Room?, center: Vec2, plan: FloorPlan): FreeWall {
        val b = defaultBeam(id, room, center, plan)
        return FreeWall(id, b.start, b.end)
    }

    /** Tolleranza (gradi) entro cui una trave si raddrizza su un multiplo di 90°. */
    const val ANGLE_SNAP = 6.0

    /**
     * Estremità `p` di una trave che parte da `other`: se la trave è entro [ANGLE_SNAP] gradi da un multiplo
     * di 90° (orizzontale o verticale), la si raddrizza tenendo la stessa lunghezza.
     */
    fun snapAngle(p: Vec2, other: Vec2): Vec2 {
        val d = p - other
        val len = d.length
        if (len < 1e-6) return p
        val deg = toDegrees(kotlin.math.atan2(d.y, d.x))
        val snapped = roundHalfUp(deg / 90.0) * 90.0
        if (abs(deg - snapped) > ANGLE_SNAP) return p
        val rad = toRadians(snapped)
        return other + Vec2(roundHalfUp(cos(rad)).toDouble(), roundHalfUp(sin(rad)).toDouble()) * len
    }

    /** Stanza (non all'aperto) che contiene il punto. */
    fun roomAt(plan: FloorPlan, p: Vec2): Room? = plan.rooms.lastOrNull { !it.outdoor && Polygon.contains(it.points, p) }

    /** Distanza entro cui un punto fuori dalle stanze (un pilastro sul muro) prende il soffitto della stanza più vicina. */
    private const val NEAR_ROOM = 100.0

    /**
     * Quota del soffitto nel punto (cm): quella della stanza che lo contiene, anche in mansarda. Un punto
     * appena fuori (il centro di un pilastro accostato al muro, che cade nello spessore del muro) prende il
     * soffitto della stanza più vicina. Lontano da tutte le stanze (una colonna all'aperto, sotto una
     * terrazza) arriva al solaio del piano di sopra (interpiano meno solaio), ma mai più in basso del
     * soffitto più alto delle stanze del piano: così anche con un interpiano scritto uguale al soffitto
     * la colonna arriva al soffitto.
     */
    fun ceilingAt(plan: FloorPlan, p: Vec2, levelHeight: Double): Double {
        val room = roomAt(plan, p) ?: plan.rooms.filter { !it.outdoor }
            .map { r -> r to (0 until r.wallCount).minOf { Polygon.distanceToSegment(p, r.wallStart(it), r.wallEnd(it)) } }
            .filter { it.second <= NEAR_ROOM }
            .minByOrNull { it.second }?.first
        if (room != null) return Ceilings.heightAt(room, p)
        val tallest = plan.rooms.filter { !it.outdoor }.maxOfOrNull { it.ceilingHeight } ?: 0.0
        return maxOf(levelHeight - Floor.SLAB, tallest)
    }

    /** Altezza della colonna: quella scelta, ma mai oltre il soffitto; senza scelta, fino al soffitto. */
    fun columnHeight(plan: FloorPlan, c: Column, levelHeight: Double): Double {
        val ceiling = ceilingAt(plan, c.center, levelHeight)
        return c.height?.coerceIn(1.0, ceiling) ?: ceiling
    }

    /**
     * Estremità di una trave, in ordine di priorità:
     * 1. su un punto notevole vicino: centro di una colonna o estremità di un'altra trave (angolo, continuità);
     * 2. sull'asse di un'altra trave, lungo la propria direzione (innesto a T, la trave resta dritta);
     * 3. sulla faccia di un muro ([snapEnd]).
     * `other` è l'altra estremità, `id` la trave stessa (esclusa dagli agganci).
     */
    fun snapBeamEnd(plan: FloorPlan, p: Vec2, other: Vec2, id: Long): Vec2 {
        val reach = SNAP * 1.5
        val anchors = plan.columns.map { it.center } + plan.beams.filter { it.id != id }.flatMap { listOf(it.start, it.end) }
        anchors.filter { it.distanceTo(p) < reach }.minByOrNull { it.distanceTo(p) }?.let { return it }
        val len = p.distanceTo(other)
        if (len > 1e-6) {
            val dir = (p - other) / len
            var best: Vec2? = null
            var bestT = reach
            for (o in plan.beams) {
                if (o.id == id || o.length < 1.0) continue
                val u = (o.end - o.start) / o.length
                val n = u.perp()
                val den = dir dot n
                if (abs(den) < 0.2) continue // quasi parallele: nessun innesto
                val t = ((o.start - p) dot n) / den
                if (abs(t) > bestT) continue
                val q = p + dir * t
                val along = (q - o.start) dot u
                if (along < -o.width / 2 || along > o.length + o.width / 2) continue
                best = q
                bestT = abs(t)
            }
            best?.let { return it }
        }
        return snapEnd(plan, p, other)
    }

    /**
     * Spostamento perpendicolare che mette l'asse della trave `b` in linea con un riferimento vicino: l'asse
     * di un'altra trave parallela (travi allineate) o il centro di una colonna (trave in asse al pilastro).
     * Null se non c'è niente entro [SNAP].
     */
    private fun axisSnap(plan: FloorPlan, b: Beam): Vec2? {
        val len = b.length
        if (len < 1e-6) return null
        val u = (b.end - b.start) / len
        val n = u.perp()
        val offsets = plan.beams.filter { it.id != b.id && it.length > 1.0 }
            .filter { o -> abs(((o.end - o.start) / o.length).cross(u)) < 0.02 }
            .map { o -> (o.start - b.start) dot n } +
            plan.columns.map { c -> (c.center - b.start) dot n }
        val best = offsets.filter { abs(it) < SNAP }.minByOrNull { abs(it) } ?: return null
        return n * best
    }

    /**
     * Trave spostata intera: l'asse si allinea a travi parallele o a colonne vicine (se no si accosta di
     * fianco a un muro parallelo) e le estremità si agganciano a colonne, travi e muri ([snapBeamEnd]).
     */
    fun snapped(plan: FloorPlan, b: Beam): Beam {
        val d = axisSnap(plan, b) ?: wallSnap(plan, Polygon.bounds(outline(b)))
        val moved = b.copy(start = b.start + d, end = b.end + d)
        // Ogni estremità si appoggia per conto suo (una trave da muro a muro resta appoggiata ai due muri).
        return moved.copy(start = snapBeamEnd(plan, moved.start, moved.end, b.id), end = snapBeamEnd(plan, moved.end, moved.start, b.id))
    }

    /**
     * Trave proposta per una stanza: attraversa la stanza nel verso più corto, passando per il centro, da
     * una faccia del muro all'altra.
     */
    fun defaultBeam(id: Long, room: Room?, center: Vec2, plan: FloorPlan): Beam {
        val half = Room.WALL_THICKNESS / 2
        if (room == null) return Beam(id, center - Vec2(150.0, 0.0), center + Vec2(150.0, 0.0))
        val b = Polygon.bounds(room.points)
        val c = Polygon.labelPoint(room.points)
        val raw = if (b.width >= b.height) Beam(id, Vec2(c.x, b.minY + half), Vec2(c.x, b.maxY - half))
        else Beam(id, Vec2(b.minX + half, c.y), Vec2(b.maxX - half, c.y))
        return raw.copy(start = snapEnd(plan, raw.start, raw.end), end = snapEnd(plan, raw.end, raw.start))
    }
}
