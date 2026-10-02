package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs

/** Esito dell'unione di due stanze: la pianta nuova e quanti elementi stavano sul muro eliminato. */
data class MergeResult(val plan: FloorPlan, val removedOpenings: Int, val removedFixtures: Int)

/**
 * Eliminazione di un muro in comune: le due stanze diventano un unico ambiente. Il nuovo contorno è
 * quello della prima stanza senza il muro, seguito da quello della seconda senza il muro corrispondente;
 * del muro resta solo la parte non in comune. Aperture, impianti a muro e altezze personalizzate vengono
 * riportati sui muri nuovi in base alla loro posizione; quelli sul tratto eliminato spariscono.
 */
object RoomMerge {

    /** Distanza entro cui un elemento si considera ancora sullo stesso muro dopo l'unione. */
    private const val ON_WALL_CM = 3.0

    /** Tratto minimo in comune perché due muri contino come muro condiviso (non un semplice angolo che si tocca). */
    private const val MIN_SHARED_CM = 20.0

    /**
     * Stanze confinanti sul muro `index` di `roomId`, ciascuna con l'indice del suo muro in comune, dalla
     * più estesa. Un muro lungo può confinare con più stanze (es. due camere affiancate sotto un bagno).
     */
    fun partners(plan: FloorPlan, roomId: Long, index: Int): List<Pair<Room, Int>> {
        val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return emptyList()
        val a = room.wallStart(index)
        val b = room.wallEnd(index)
        return plan.rooms.filter { it.id != roomId }.mapNotNull { r ->
            (0 until r.wallCount)
                .map { j -> Triple(r, j, Dimensions.overlap(a, b, r.wallStart(j), r.wallEnd(j))) }
                .filter { it.third >= MIN_SHARED_CM }
                .maxByOrNull { it.third }
        }.sortedByDescending { it.third }.map { it.first to it.second }
    }

    /** Stanze con cui il muro si può davvero eliminare (quelle per cui l'unione dà un contorno valido). */
    fun mergeablePartners(plan: FloorPlan, roomId: Long, index: Int): List<Room> =
        partners(plan, roomId, index).map { it.first }.filter { merge(plan, roomId, index, it.id) != null }

    /**
     * Unisce la stanza `roomId` con `otherId`, confinante sul muro `index` (se null, quella con più
     * muro in comune); null se non si può.
     */
    fun merge(plan: FloorPlan, roomId: Long, index: Int, otherId: Long? = null): MergeResult? {
        val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return null
        val (other, otherIndex) = partners(plan, roomId, index)
            .firstOrNull { otherId == null || it.first.id == otherId } ?: return null

        // Si lavora su contorni orari: una stanza salvata al contrario viene prima girata.
        val pa = clockwise(room.points)
        val pb = clockwise(other.points)
        val ia = edgeIndex(pa, room.wallStart(index), room.wallEnd(index)) ?: return null
        val ib = edgeIndex(pb, other.wallStart(otherIndex), other.wallEnd(otherIndex)) ?: return null
        val a0 = pa[ia]
        val a1 = pa[(ia + 1) % pa.size]
        val b0 = pb[ib]
        val b1 = pb[(ib + 1) % pb.size]
        val u = (a1 - a0).normalized()
        // Stanze da parti opposte del muro: i due muri, entrambi orari, vanno in versi opposti.
        if ((u dot (b1 - b0)) >= 0) return null
        // Gli estremi dell'altra stanza vengono portati sulla linea del muro (i muri in comune possono
        // distare qualche cm), così il contorno resta squadrato.
        fun onLine(p: Vec2) = a0 + u * ((p - a0) dot u)

        val loop = mutableListOf<Vec2>()
        for (k in pa.indices) loop += pa[(ia + 1 + k) % pa.size] // da a1 fino ad a0
        val tail = MutableList(pb.size) { k -> pb[(ib + 1 + k) % pb.size] } // da b1 fino a b0
        tail[0] = onLine(tail[0])
        tail[tail.lastIndex] = onLine(tail.last())
        // Muri laterali quasi allineati (pochi cm, come tra muri in comune): il muro dell'altra stanza
        // viene raddrizzato su quello della prima, così resta un unico muro dritto invece di un gradino.
        if (pb.size >= 3) {
            align(tail, 0, 1, a0, pa[(ia - 1 + pa.size) % pa.size])
            align(tail, tail.lastIndex, tail.lastIndex - 1, a1, pa[(ia + 2) % pa.size])
        }
        loop += tail
        val points = simplify(loop)
        if (points.size < 3 || !isSimple(points)) return null
        // Nessun pezzo perso né sovrapposto: l'area è la somma delle due (a meno dello scostamento tra i muri).
        val expected = Polygon.area(pa) + Polygon.area(pb)
        val slack = Openings.SHARED_WALL_TOLERANCE_CM * (a0.distanceTo(a1) + b0.distanceTo(b1)) + 1.0
        if (abs(Polygon.area(points) - expected) > slack) return null

        val shell = Room(room.id, room.name, room.type, points, room.ceilingHeight)
        val openings = (room.openings.mapNotNull { remap(room, it, shell) } + other.openings.mapNotNull { remap(other, it, shell) })
        val fixtures = listOf(room, other).flatMap { r ->
            r.fixtures.mapNotNull { f -> if (f.kind.mount == Mount.Ceiling) f else remap(r, f, shell) }
        }
        val heights = (0 until shell.wallCount).mapNotNull { k ->
            val mid = (shell.wallStart(k) + shell.wallEnd(k)) / 2.0
            sourceHeight(room, mid, shell.wallEnd(k) - shell.wallStart(k))
                ?.let { k to it }
                ?: sourceHeight(other, mid, shell.wallEnd(k) - shell.wallStart(k))?.let { k to it }
        }.filter { it.second != room.ceilingHeight }.toMap()

        val merged = shell.copy(openings = openings, fixtures = fixtures, wallHeights = heights)
        val newPlan = plan.copy(rooms = plan.rooms.filter { it.id != other.id }.map { if (it.id == room.id) merged else it })
        return MergeResult(
            newPlan,
            removedOpenings = room.openings.size + other.openings.size - openings.size,
            removedFixtures = room.fixtures.size + other.fixtures.size - fixtures.size,
        )
    }

    /**
     * `tail[end]` è l'estremo dell'altra stanza sulla linea del muro, `tail[next]` il vertice che segue
     * lungo il suo muro laterale; `corner` è l'estremo della prima stanza e `far` l'altro capo del suo
     * muro laterale. Se i due muri laterali sono paralleli e distano meno della tolleranza dei muri in
     * comune, il muro laterale dell'altra stanza viene spostato su quello della prima.
     */
    private fun align(tail: MutableList<Vec2>, end: Int, next: Int, corner: Vec2, far: Vec2) {
        val shift = corner - tail[end]
        if (shift.length < 1e-6 || shift.length > Openings.SHARED_WALL_TOLERANCE_CM) return
        val side = (tail[next] - tail[end]).normalized()
        val mine = (far - corner).normalized()
        if (abs(side.x * mine.y - side.y * mine.x) > 0.01) return
        tail[end] = corner
        tail[next] = tail[next] + shift
    }

    private fun clockwise(pts: List<Vec2>) = if (Polygon.signedArea(pts) >= 0) pts else pts.reversed()

    /** Indice del lato del contorno che unisce i due punti (in un verso o nell'altro). */
    private fun edgeIndex(pts: List<Vec2>, p: Vec2, q: Vec2): Int? = pts.indices.firstOrNull { i ->
        val s = pts[i]
        val e = pts[(i + 1) % pts.size]
        (s.distanceTo(p) < 1e-6 && e.distanceTo(q) < 1e-6) || (s.distanceTo(q) < 1e-6 && e.distanceTo(p) < 1e-6)
    }

    /** Toglie punti doppi e punti intermedi su lati allineati. */
    private fun simplify(input: List<Vec2>): List<Vec2> {
        var pts = input
        var changed = true
        while (changed && pts.size >= 3) {
            changed = false
            val n = pts.size
            for (i in 0 until n) {
                val prev = pts[(i - 1 + n) % n]
                val cur = pts[i]
                val next = pts[(i + 1) % n]
                val d1 = cur - prev
                val d2 = next - cur
                val degenerate = d1.length < 0.5 ||
                    abs(d1.normalized().x * d2.normalized().y - d1.normalized().y * d2.normalized().x) < 1e-3
                if (degenerate) {
                    pts = pts.filterIndexed { j, _ -> j != i }
                    changed = true
                    break
                }
            }
        }
        return pts
    }

    /** Contorno senza lati che si incrociano o si toccano (a parte quelli consecutivi nel vertice comune). */
    private fun isSimple(pts: List<Vec2>): Boolean {
        val n = pts.size
        for (i in 0 until n) for (j in i + 1 until n) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue
            if (segmentsTouch(pts[i], pts[(i + 1) % n], pts[j], pts[(j + 1) % n])) return false
        }
        return true
    }

    private fun segmentsTouch(p: Vec2, q: Vec2, r: Vec2, s: Vec2): Boolean {
        val eps = 0.5
        return Polygon.distanceToSegment(p, r, s) < eps || Polygon.distanceToSegment(q, r, s) < eps ||
            Polygon.distanceToSegment(r, p, q) < eps || Polygon.distanceToSegment(s, p, q) < eps ||
            properCross(p, q, r, s)
    }

    private fun properCross(p: Vec2, q: Vec2, r: Vec2, s: Vec2): Boolean {
        fun side(a: Vec2, b: Vec2, c: Vec2) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        val d1 = side(r, s, p)
        val d2 = side(r, s, q)
        val d3 = side(p, q, r)
        val d4 = side(p, q, s)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /** Muro di `shell` su cui cade il punto `c` di un muro con direzione `dir`, con la distanza dal suo inizio. */
    private fun locate(shell: Room, c: Vec2, dir: Vec2): Pair<Int, Double>? {
        val u = dir.normalized()
        return (0 until shell.wallCount)
            .filter { k ->
                val v = (shell.wallEnd(k) - shell.wallStart(k)).normalized()
                abs(u.x * v.y - u.y * v.x) < 0.1
            }
            .map { k -> k to Polygon.distanceToSegment(c, shell.wallStart(k), shell.wallEnd(k)) }
            .filter { it.second < ON_WALL_CM }
            .minByOrNull { it.second }
            ?.let { (k, _) -> k to Openings.projectOnWall(shell, k, c) }
    }

    private fun remap(from: Room, o: Opening, shell: Room): Opening? {
        if (o.wallIndex >= from.wallCount) return null
        val (a, b) = Openings.span(from, o)
        val dir = from.wallEnd(o.wallIndex) - from.wallStart(o.wallIndex)
        val (k, pos) = locate(shell, (a + b) / 2.0, dir) ?: return null
        // Muro nuovo percorso al contrario (stanza salvata in senso antiorario): lato del cardine e
        // verso di apertura si invertono per restare fisicamente dove erano.
        val flipped = (dir dot (shell.wallEnd(k) - shell.wallStart(k))) < 0
        return o.copy(
            wallIndex = k,
            position = pos,
            hingeLeft = if (flipped) !o.hingeLeft else o.hingeLeft,
            opensInward = if (flipped) !o.opensInward else o.opensInward,
        )
    }

    private fun remap(from: Room, f: Fixture, shell: Room): Fixture? {
        if (f.wallIndex >= from.wallCount) return null
        val start = from.wallStart(f.wallIndex)
        val dir = from.wallEnd(f.wallIndex) - start
        val c = start + dir.normalized() * Openings.clampPosition(from.wallLength(f.wallIndex), f.length, f.position)
        val (k, pos) = locate(shell, c, dir) ?: return null
        return f.copy(wallIndex = k, position = pos)
    }

    /** Altezza personalizzata del muro di `from` su cui cade il punto `mid` (del muro nuovo), se c'è. */
    private fun sourceHeight(from: Room, mid: Vec2, dir: Vec2): Double? {
        val u = dir.normalized()
        return (0 until from.wallCount).firstOrNull { i ->
            val v = (from.wallEnd(i) - from.wallStart(i)).normalized()
            abs(u.x * v.y - u.y * v.x) < 0.1 &&
                Polygon.distanceToSegment(mid, from.wallStart(i), from.wallEnd(i)) < Openings.SHARED_WALL_TOLERANCE_CM
        }?.let { from.wallHeights[it] }
    }
}
