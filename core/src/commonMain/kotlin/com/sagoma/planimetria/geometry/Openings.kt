package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.FramePosition
import com.sagoma.planimetria.model.GarageDoor
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Varco disegnato su un muro: quello di un'apertura propria o quello indotto da una stanza confinante. Tratto `a`–`b` del
 * muro `wallIndex` della stanza `roomId`; `height`: altezza del vano (varchi indotti da una porta).
 */
data class WallGap(val roomId: Long, val wallIndex: Int, val a: Vec2, val b: Vec2, val height: Double = 0.0)

object Openings {
    /** Quanto sporge il bovindo dalla faccia esterna del muro (cm); i fianchi sono a 45°. */
    const val BAY_DEPTH = 60.0

    /**
     * Bovindo in pianta: i quattro angoli sulla faccia esterna del muro e davanti, in ordine (inizio del varco sul muro,
     * davanti, davanti, fine del varco sul muro). La sporgenza si riduce sulle finestre strette, così il davanti resta
     * largo almeno 30 cm.
     */
    fun bayOutline(room: Room, o: Opening): List<Vec2> {
        val (a, b) = span(room, o)
        val w = a.distanceTo(b)
        val u = (b - a) / max(w, 1e-9)
        val out = inwardNormal(room, o.wallIndex) * -1.0
        val half = room.thicknessOf(o.wallIndex) / 2
        val d = bayDepth(w)
        val a0 = a + out * half
        val b0 = b + out * half
        return listOf(a0, a0 + out * d + u * d, b0 + out * d - u * d, b0)
    }

    /** Sporgenza del bovindo largo `w`: [BAY_DEPTH], meno se la finestra è stretta. */
    fun bayDepth(w: Double): Double = min(BAY_DEPTH, max(0.0, (w - 30.0) / 2))

    /**
     * Quanto entra nella stanza la porta da garage aperta (cm, dalla faccia interna del muro): la basculante circa tre quarti
     * della sua altezza, la sezionale tutta l'altezza più la curva dei binari.
     */
    fun garageReach(o: Opening): Double = when (o.garageDoor) {
        GarageDoor.UpAndOver -> o.height * 0.75
        GarageDoor.Sectional -> o.height + 40.0
        GarageDoor.None -> 0.0
    }

    /**
     * Quanto sporge dal muro la tenda da sole (cm): sui balconi è una tenda a bracci che copre il terrazzo, sulle finestre
     * una tenda a caduta poco sporgente.
     */
    fun awningReach(o: Opening): Double = if (o.bottom <= 0.0) 200.0 else min(80.0, o.height * 0.6)

    /** Perno della porta a bilico: a un quinto della larghezza dal lato del cardine. */
    const val PIVOT_AT = 0.2


    /** Distanza massima tra due muri paralleli per considerarli a contatto (§5). */
    const val SHARED_WALL_TOLERANCE_CM = 12.0

    /**
     * Bordo superiore di un varco largo da `t0` a `t1` (cm lungo il muro), con il fondo a `bottom` e la cima a `top`,
     * nel punto `t`: dritto, ad arco tondo, ribassato o a sesto acuto. Lo usano il 3D e l'IFC, così la forma è la stessa.
     */
    fun archTop(style: PassageStyle, t0: Double, t1: Double, bottom: Double, top: Double, t: Double): Double {
        if (!style.arched) return top
        val c = (t1 - t0) / 2
        if (c < 1e-6) return top
        val x = (t - (t0 + t1) / 2).coerceIn(-c, c)
        val h = top - bottom
        return when (style) {
            PassageStyle.Segmental -> {
                // Pezzo di cerchio che passa per le due imposte e sale di `rise` al centro.
                val rise = min(c * 0.36, h * 0.25)
                val r = (c * c + rise * rise) / (2 * rise)
                top - rise + (sqrt(max(0.0, r * r - x * x)) - (r - rise))
            }
            PassageStyle.Pointed -> {
                // Sesto acuto equilatero (ogni arco ha il centro sull'imposta opposta), schiacciato se il varco è basso.
                val full = c * sqrt(3.0)
                val rise = min(full, h * 0.45)
                val ax = abs(x)
                val y = sqrt(max(0.0, 4 * c * c - (ax + c) * (ax + c)))
                top - rise + rise * (y / full)
            }
            else -> {
                val rise = min(c, h * 0.3)
                val k = x / c
                top - rise + rise * sqrt(1 - k * k)
            }
        }
    }

    /** Centro dell'apertura limitato al muro, così l'apertura non ne esce mai (anche dopo averlo accorciato). */
    fun clampPosition(wallLength: Double, width: Double, position: Double): Double =
        if (width >= wallLength) wallLength / 2 else position.coerceIn(width / 2, wallLength - width / 2)

    /** Estremi reali dell'apertura sul muro, da `a` (lato inizio muro) a `b`. */
    fun span(room: Room, o: Opening): Pair<Vec2, Vec2> {
        // Muro curvo: gli estremi stanno sull'arco, distanti in linea retta quanto è larga l'apertura.
        room.arcOf(o.wallIndex)?.let { arc ->
            val c = clampPosition(arc.length, min(o.width, arc.length), o.position)
            val (s1, s2) = arc.span(c, o.width)
            return arc.at(s1) to arc.at(s2)
        }
        val start = room.wallStart(o.wallIndex)
        val len = room.wallLength(o.wallIndex)
        val u = (room.wallEnd(o.wallIndex) - start).normalized()
        val w = min(o.width, len)
        val c = clampPosition(len, w, o.position)
        // Tratto più spesso di un muro a scalini: la sua mezzeria sta più fuori della linea della stanza.
        val shift = room.outwardOf(o.wallIndex) * room.wallShift(o.wallIndex)
        return (start + u * (c - w / 2) + shift) to (start + u * (c + w / 2) + shift)
    }

    /** Distanza lungo il muro della proiezione di `p` (non limitata). */
    fun projectOnWall(room: Room, wallIndex: Int, p: Vec2): Double {
        val start = room.wallStart(wallIndex)
        val u = (room.wallEnd(wallIndex) - start).normalized()
        return (p - start) dot u
    }

    /** Profondità dell'infisso (cm): a filo esterno o interno resta così distante dalla faccia del muro. */
    const val FRAME_DEPTH = 6.0

    /**
     * Spostamento dell'infisso dalla mezzeria del muro, verso l'interno della stanza (negativo = verso l'esterno).
     * Solo finestre e balconi; le porte stanno sempre a metà muro.
     */
    fun frameOffset(room: Room, o: Opening): Double {
        if (!o.kind.glazed) return 0.0
        val reach = (room.thicknessOf(o.wallIndex) / 2 - FRAME_DEPTH).coerceAtLeast(0.0)
        return when (o.frame) {
            FramePosition.Center -> 0.0
            FramePosition.Outside -> -reach
            FramePosition.Inside -> reach
        }
    }

    /** Sguincio effettivo (cm per lato): solo finestre e balconi, mai più dello spessore del muro. */
    fun splayOf(room: Room, o: Opening): Double =
        if (o.kind.glazed && !o.isBay && o.wallIndex < room.wallCount) o.splay.coerceIn(0.0, room.thicknessOf(o.wallIndex)) else 0.0

    /**
     * Lato del vano con lo sguincio, dal punto `p` sulla mezzeria (bordo del varco) alla faccia del muro verso la stanza:
     * `toward` = −1 dal lato dell'inizio del varco, +1 da quello della fine. Dritto (due punti) o arrotondato (una curva
     * che parte lungo il muro e arriva sulla faccia).
     */
    fun splayProfile(p: Vec2, u: Vec2, inward: Vec2, half: Double, splay: Double, toward: Double, rounded: Boolean): List<Vec2> {
        val end = p + u * (toward * splay) + inward * half
        if (!rounded) return listOf(p, end)
        val control = p + inward * half
        return (0..SPLAY_STEPS).map { k ->
            val t = k.toDouble() / SPLAY_STEPS
            p * ((1 - t) * (1 - t)) + control * (2 * t * (1 - t)) + end * (t * t)
        }
    }

    private const val SPLAY_STEPS = 6

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
        // Muro singolo ([com.sagoma.planimetria.model.FreeWall.host]): le aperture restano sul suo unico muro.
        val j = (0 until if (room.id < 0) 1 else room.wallCount)
            // Mai su un muro eliminato (lato aperto): lì non c'è muro in cui fare il vano.
            .filter { room.wallLength(it) >= width && !room.isRemoved(it) }
            .minByOrNull { Polygon.distanceToSegment(p, room.wallStart(it), room.wallEnd(it)) } ?: return null
        return j to clampPosition(room.wallLength(j), width, projectOnWall(room, j, p))
    }

    fun planBounds(plan: FloorPlan, rise: Double = Floor.DEFAULT_LEVEL_HEIGHT): Bounds? {
        val pts = mutableListOf<Vec2>()
        for (room in plan.rooms) {
            pts += room.points
            for (o in room.openings) {
                if (o.wallIndex >= room.wallCount) continue
                // Il bovindo sporge fuori dal muro.
                if (o.isBay) pts += bayOutline(room, o)
                if (!o.swings || o.opensInward) continue
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
     * Tratti dei muri eliminati da togliere dal disegno: tutto il muro, tranne dove un'altra stanza ha lì un suo muro non eliminato
     * (quello resta: una stanza aperta verso un'altra che invece ha il muro).
     */
    fun removedStretches(plan: FloorPlan): List<WallGap> {
        val out = mutableListOf<WallGap>()
        for (room in plan.rooms) for (i in room.removedWalls) {
            if (i >= room.wallCount) continue
            val s = room.wallStart(i)
            val e = room.wallEnd(i)
            val len = s.distanceTo(e)
            if (len < 1e-6) continue
            val u = (e - s) / len
            var ranges = listOf(0.0..len)
            for (other in plan.rooms) {
                if (other.id == room.id) continue
                for (j in 0 until other.wallCount) {
                    if (other.isRemoved(j) || !Dimensions.inContact(s, e, other.wallStart(j), other.wallEnd(j))) continue
                    val t0 = ((other.wallStart(j) - s) dot u).coerceIn(0.0, len)
                    val t1 = ((other.wallEnd(j) - s) dot u).coerceIn(0.0, len)
                    val lo = min(t0, t1)
                    val hi = max(t0, t1)
                    ranges = ranges.flatMap { r ->
                        if (hi <= r.start || lo >= r.endInclusive) listOf(r)
                        else listOfNotNull((r.start..lo).takeIf { lo > r.start }, (hi..r.endInclusive).takeIf { hi < r.endInclusive })
                    }
                }
            }
            for (r in ranges) if (r.endInclusive - r.start > 0.5) out += WallGap(room.id, i, s + u * r.start, s + u * r.endInclusive)
        }
        return out
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
                    gaps += WallGap(other.id, j, s + v * lo, s + v * hi, o.height)
                }
            }
        }
        return gaps
    }
}
