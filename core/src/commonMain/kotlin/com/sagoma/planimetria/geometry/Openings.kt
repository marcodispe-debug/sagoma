package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.FramePosition
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WindowShape
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

    /**
     * Superficie (cm²) del vano dell'apertura `o` tra le quote `lo` e `hi` da terra, con la sua forma (ad arco, tonda) e senza
     * passare la cima del muro (in mansarda): quella che si toglie dalla parete nel computo.
     */
    fun holeArea(room: Room, o: Opening, lo: Double = 0.0, hi: Double = Double.MAX_VALUE): Double {
        if (o.wallIndex >= room.wallCount) return 0.0
        val len = room.wallLength(o.wallIndex)
        val w = min(o.width, len)
        if (w <= 0) return 0.0
        val bottom = o.bottom
        val center = clampPosition(len, w, o.position)
        val wallTop = Ceilings.wallTopAt(room, o.wallIndex, center)
        val top = min(bottom + o.height, wallTop)
        val round = o.shape == WindowShape.Round
        val style = when {
            o.kind == OpeningKind.Passage -> o.style
            o.shape == WindowShape.Arched -> PassageStyle.Arched
            else -> PassageStyle.Square
        }
        val sloped = Ceilings.effectiveCut(room, o.wallIndex) != null
        if (!round && !style.arched && !sloped) return w * (min(top, hi) - max(bottom, lo)).coerceAtLeast(0.0)
        // Bordo curvo o muro che scende (mansarda): a strisce verticali sottili. L'oblò è un cerchio largo quanto l'apertura
        // (come nel 3D); sopra la cima del muro non c'è parete da togliere.
        val n = 128
        val c = w / 2
        var sum = 0.0
        for (k in 0 until n) {
            val x = -c + w * (k + 0.5) / n
            val dy = sqrt(max(0.0, c * c - x * x))
            val b = if (round) bottom + c - dy else bottom
            val t = when {
                round -> bottom + c + dy
                style.arched -> archTop(style, -c, c, bottom, top, x)
                else -> top
            }
            val roof = if (sloped) Ceilings.wallTopAt(room, o.wallIndex, center + x) else wallTop
            sum += (minOf(t, roof, hi) - max(b, lo)).coerceAtLeast(0.0)
        }
        return sum * w / n
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
        if (o.kind.glazed && o.wallIndex < room.wallCount) o.splay.coerceIn(0.0, room.thicknessOf(o.wallIndex)) else 0.0

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

    /** Pezzi dritti della curva dello sguincio arrotondato. */
    const val SPLAY_STEPS = 6

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
     * Dove togliere dal disegno (pianta e DXF) il tratto [g] di un muro eliminato: la striscia del muro, dal filo esterno a quello
     * interno, più `extra` per parte. All'angolo con un muro della stanza che resta si ferma sulla faccia interna di quel muro:
     * l'angolo resta muro pieno, così il muro accanto finisce dritto e in un ambiente unico (muro tolto da tutte e due le stanze)
     * i muri di fuori continuano senza tacca. Dove anche il muro accanto è eliminato va oltre l'angolo, che resta aperto. Vuota
     * se non resta niente da togliere.
     */
    fun openBand(room: Room, g: WallGap, extra: Double): List<Vec2> {
        val i = g.wallIndex
        val n = room.wallCount
        if (i !in 0 until n) return emptyList()
        val s = room.wallStart(i)
        val len = s.distanceTo(room.wallEnd(i))
        if (len < 1e-6) return emptyList()
        val u = (room.wallEnd(i) - s) / len
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        val inward = u.perp() * sign
        val inner = room.innerThicknesses[i] / 2 + extra
        val outer = room.outerThicknesses[i] / 2 + extra
        val ta = (g.a - s) dot u
        val tb = (g.b - s) dot u
        /** Dove finiscono, lungo il muro, il lato della striscia verso la stanza e quello verso fuori. */
        fun end(atEnd: Boolean, t: Double): Pair<Double, Double> {
            if (if (atEnd) t < len - 0.5 else t > 0.5) return t to t
            val corner = if (atEnd) len else 0.0
            val j = if (atEnd) (i + 1) % n else (i - 1 + n) % n
            val js = room.wallStart(j)
            val jl = js.distanceTo(room.wallEnd(j))
            if (jl < 1e-6) return corner to corner
            val ju = (room.wallEnd(j) - js) / jl
            if (room.isRemoved(j)) {
                // Eliminato anche il muro accanto: oltre l'angolo quanto basta a togliere le punte dei due pezzi di muro (poco
                // dove il muro continua quasi dritto, come i pezzi di un muro curvo; mai oltre la fine del muro accanto).
                val tanHalf = abs(u.x * ju.y - u.y * ju.x) / max(1e-6, 1 + (u dot ju))
                val thick = max(room.thicknessOf(i), room.thicknessOf(j))
                val past = corner + (if (atEnd) 1 else -1) * (min(thick * tanHalf, min(3 * thick, jl)) + extra)
                return past to past
            }
            val jn = ju.perp() * sign
            val c = u dot jn
            // Muro accanto quasi dritto (scalino, pezzi di un muro curvo): taglio di traverso all'angolo.
            if (abs(c) < 0.2) return corner to corner
            val face = js + jn * (room.innerThicknesses[j] / 2)
            fun at(side: Vec2) = ((face - s - side) dot jn) / c
            return at(inward * inner) to at(inward * -outer)
        }
        val (in0, out0) = end(false, min(ta, tb))
        val (in1, out1) = end(true, max(ta, tb))
        if (in1 - in0 < 0.01 || out1 - out0 < 0.01) return emptyList()
        return listOf(s + u * in0 + inward * inner, s + u * in1 + inward * inner, s + u * out1 - inward * outer, s + u * out0 - inward * outer)
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
