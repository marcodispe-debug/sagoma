package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Varco disegnato su un muro: quello di un'apertura propria o quello indotto da una stanza confinante. */
data class WallGap(val roomId: Long, val wallIndex: Int, val a: Vec2, val b: Vec2)

object Openings {

    /** Distanza massima tra due muri paralleli per considerarli a contatto (§5). */
    const val SHARED_WALL_TOLERANCE_CM = 12.0

    /** Centro dell'apertura limitato al muro, così l'apertura non ne esce mai (anche dopo averlo accorciato). */
    fun clampPosition(wallLength: Double, width: Double, position: Double): Double =
        if (width >= wallLength) wallLength / 2 else position.coerceIn(width / 2, wallLength - width / 2)

    /** Estremi reali dell'apertura sul muro, da `a` (lato inizio muro) a `b`. */
    fun span(room: Room, o: Opening): Pair<Vec2, Vec2> {
        val start = room.wallStart(o.wallIndex)
        val len = room.wallLength(o.wallIndex)
        val u = (room.wallEnd(o.wallIndex) - start).normalized()
        val w = min(o.width, len)
        val c = clampPosition(len, w, o.position)
        return (start + u * (c - w / 2)) to (start + u * (c + w / 2))
    }

    /** Distanza lungo il muro della proiezione di `p` (non limitata). */
    fun projectOnWall(room: Room, wallIndex: Int, p: Vec2): Double {
        val start = room.wallStart(wallIndex)
        val u = (room.wallEnd(wallIndex) - start).normalized()
        return (p - start) dot u
    }

    /** Normale verso l'interno della stanza (poligoni orari su schermo). */
    fun inwardNormal(room: Room, wallIndex: Int): Vec2 =
        (room.wallEnd(wallIndex) - room.wallStart(wallIndex)).normalized().perp()

    /**
     * Ingombro completo della pianta: stanze, metri e ante che si aprono verso l'esterno (che sporgono
     * oltre i muri di tutta la loro larghezza). Serve ad adattare e centrare la vista senza tagliarle.
     */
    /**
     * Muro della stanza più vicino al punto `p` su cui ci sta un oggetto largo `width`, e la posizione
     * (distanza del centro dall'inizio del muro) più vicina a `p`: serve a far scorrere porte, finestre e
     * impianti lungo tutto il perimetro. Null se nessun muro è abbastanza lungo.
     */
    fun alongPerimeter(room: Room, p: Vec2, width: Double): Pair<Int, Double>? {
        val j = (0 until room.wallCount)
            .filter { room.wallLength(it) >= width }
            .minByOrNull { Polygon.distanceToSegment(p, room.wallStart(it), room.wallEnd(it)) } ?: return null
        return j to clampPosition(room.wallLength(j), width, projectOnWall(room, j, p))
    }

    fun planBounds(plan: FloorPlan, rise: Double = Floor.DEFAULT_LEVEL_HEIGHT): Bounds? {
        val pts = mutableListOf<Vec2>()
        for (room in plan.rooms) {
            pts += room.points
            for (o in room.openings) {
                if (o.wallIndex >= room.wallCount || !o.swings || o.opensInward) continue
                val (a, b) = span(room, o)
                val out = inwardNormal(room, o.wallIndex) * -(o.width + room.thicknessOf(o.wallIndex) / 2)
                pts += a + out
                pts += b + out
            }
        }
        for (r in plan.rulers) { pts += r.start; pts += r.end }
        pts += Stairs.footprint(plan, rise)
        return if (pts.isEmpty()) null else Polygon.bounds(pts)
    }

    /**
     * Varchi indotti: per ogni porta o apertura senza porta su un muro che combacia (parallelo, entro
     * [SHARED_WALL_TOLERANCE_CM]) con il muro di un'altra stanza, il tratto corrispondente su quel muro.
     */
    fun sharedGaps(plan: FloorPlan): List<WallGap> {
        val gaps = mutableListOf<WallGap>()
        for (room in plan.rooms) for (o in room.openings) {
            if (!o.kind.createsSharedGap || o.wallIndex >= room.wallCount) continue
            val (a, b) = span(room, o)
            val u = (b - a).normalized()
            for (other in plan.rooms) {
                if (other.id == room.id) continue
                for (j in 0 until other.wallCount) {
                    val s = other.wallStart(j)
                    val e = other.wallEnd(j)
                    val len = s.distanceTo(e)
                    if (len < 1e-6) continue
                    val v = (e - s) / len
                    val cross = u.x * v.y - u.y * v.x
                    if (abs(cross) > 0.01) continue // non paralleli
                    val dist = abs((a - s) dot v.perp())
                    if (dist > SHARED_WALL_TOLERANCE_CM) continue
                    val ta = (a - s) dot v
                    val tb = (b - s) dot v
                    val lo = max(min(ta, tb), 0.0)
                    val hi = min(max(ta, tb), len)
                    if (hi - lo < 1.0) continue // nessuna sovrapposizione lungo il muro
                    gaps += WallGap(other.id, j, s + v * lo, s + v * hi)
                }
            }
        }
        return gaps
    }
}
