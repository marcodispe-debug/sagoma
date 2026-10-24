package com.sagoma.planimetria.reading

import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.usableArea
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.model.ExcludedZone
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FramePosition
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Dalla lettura di una piantina ([PlanReading]) alla pianta di Sagoma: la piantina diventa lo sfondo già tarato e ogni stanza
 * letta diventa una stanza vera, da controllare e correggere sopra lo sfondo.
 *
 * 1. Scala: dalle misure scritte confrontate con le stanze in pixel (lati dei rettangoli e metri quadri), la mediana.
 * 2. Angoli quasi retti raddrizzati; i rettangoli con le due misure scritte prendono esattamente quelle misure.
 * 3. Spessore dei muri dallo spazio tra le stanze (muri esterni: dalla lettura o tipico); dal filo interno alla mezzeria, così
 *    due stanze vicine hanno lo stesso muro.
 * 4. Scale dritte dalla linea di salita; porte e finestre sul muro più vicino (meglio di una stanza interna che di un balcone); zone non calpestabili tolte dall'area.
 * 5. Controlli: metri quadri e lati della stanza disegnata contro quelli scritti, e lati × lati contro i metri quadri scritti.
 */
object PlanReadingImport {
    /** Lati entro questo angolo dall'orizzontale o dalla verticale si raddrizzano. */
    private const val SQUARE_TOLERANCE = 0.18 // tan(≈10°)
    /** Rettangoli che si scostano più di così dalle misure scritte restano come sono stati letti (misure probabilmente non loro). */
    private const val FIT_TOLERANCE = 0.12
    /** Muri più sottili o più spessi di così non sono plausibili (tramezzi, muri portanti). */
    private const val MIN_WALL = 8.0
    private const val MAX_WALL = 60.0
    /** Muri esterni quando la lettura non dice quanto sono spessi. */
    private const val EXTERIOR_WALL = 30.0
    /** Mezzerie di stanze diverse che restano così vicine dopo il calcolo degli spessori finiscono sulla stessa linea. */
    private const val ALIGN_TOLERANCE = 6.0
    /** Un'apertura può stare fino a così fuori dallo spessore del muro (cm): infissi disegnati sul filo esterno. */
    private const val OPENING_MARGIN = 15.0
    /** Le aperture vanno sui muri dei balconi solo se nessuna stanza interna ha un muro adatto. */
    private const val OUTDOOR_PENALTY = 1_000.0
    /** Pedata tipica, per contare i gradini quando la lettura non li dice. */
    private const val DEFAULT_TREAD = 28.0

    /**
     * Pianta pronta, scala trovata (null = nessuna misura scritta utilizzabile: lo sfondo va tarato a mano) e un controllo per
     * stanza, nell'ordine delle stanze della pianta.
     */
    class Result(val plan: FloorPlan, val cmPerPx: Double?, val checks: List<RoomCheck>)

    /** Confronto tra la stanza disegnata e quello che è scritto sulla piantina (cm, m²). */
    data class RoomCheck(
        val name: String,
        val areaRead: Double?,
        val areaDrawn: Double,
        val sidesRead: List<Double>,
        val sidesDrawn: List<Double>,
    ) {
        /** Scarto relativo dei metri quadri disegnati da quelli scritti (null se non scritti). */
        val areaError: Double? get() = areaRead?.takeIf { it > 0 }?.let { abs(areaDrawn - it) / it }

        /**
         * Stanza rettangolare: lati × lati scritti contro i metri quadri scritti. Se non tornano, uno dei numeri è letto male o è
         * sbagliato sulla piantina (capita sulle catastali). Null se non si può dire.
         */
        val readingConsistent: Boolean?
            get() = if (sidesDrawn.size == 4 && sidesRead.size == 2 && areaRead != null && areaRead > 0) {
                abs(sidesRead[0] * sidesRead[1] / 10_000 - areaRead) / areaRead <= 0.05
            } else null
    }

    fun toPlan(reading: PlanReading, imageFile: String): Result {
        val read = reading.rooms.mapNotNull { r ->
            val pts = dedupe(r.corners.filter { it.size >= 2 }.map { Vec2(it[0], it[1]) })
            if (pts.size < 3) null else r to RoomFactory.normalizeWinding(squared(pts))
        }
        val cmPerPx = scale(read)
        // Senza misure: l'immagine larga 12 m, come quando si carica uno sfondo prima di tararlo.
        val k = cmPerPx ?: (1200.0 / max(reading.image.width, reading.image.height))
        val exterior = reading.exteriorWallPx?.let { (it * k).coerceIn(MIN_WALL, MAX_WALL) } ?: EXTERIOR_WALL
        val types = read.map { (r, _) -> typeOf(r.name) }
        val interiors = read.map { (r, px) -> fitted(px.map { it * k }, r.sidesM.map { it * 100 }) }
        val outline = reading.exteriorOutline.filter { it.size >= 2 }.map { Vec2(it[0], it[1]) * k }
        val thicknesses = thicknesses(interiors) { i, a, b ->
            if (types[i].outdoor) Room.WALL_THICKNESS else outlineDistance(outline, a, b) ?: exterior
        }
            .map { room -> room.map { it.roundToInt().toDouble() } }
        val centerlines = aligned(interiors.mapIndexed { i, pts -> Polygon.inset(pts, thicknesses[i].map { -it / 2 }) }, tolerance = ALIGN_TOLERANCE)
        val placed = read.mapIndexed { i, (r, _) ->
            val main = thicknesses[i].groupingBy { it }.eachCount().maxBy { it.value }.key
            Room(
                id = i + 1L,
                name = displayName(r.name).ifBlank { types[i].defaultName },
                type = types[i],
                points = centerlines[i],
                wallThickness = main,
                wallThicknesses = thicknesses[i].withIndex().filter { it.value != main }.associate { it.index to it.value },
                floorFinish = types[i].defaultFloor,
                excludedZones = zonesPx(r).mapIndexed { z, pts -> ExcludedZone(z + 1L, pts.map { it * k }) },
            )
        }
        val rooms = withOpenings(placed, reading.openings, k)
        val checks = read.mapIndexed { i, (r, _) ->
            RoomCheck(
                name = rooms[i].name,
                areaRead = r.areaM2,
                areaDrawn = rooms[i].usableArea() / 10_000,
                sidesRead = r.sidesM.map { it * 100 },
                sidesDrawn = Polygon.interiorEdgeLengths(rooms[i].points, rooms[i].thicknesses),
            )
        }
        val underlay = Underlay(imageFile, reading.image.width, reading.image.height, origin = Vec2.Zero, cmPerPx = k, opacity = 0.5)
        val walls = freeWalls(reading.walls, k, firstOpeningId = (rooms.flatMap { it.openings }.maxOfOrNull { it.id } ?: 0L) + 1)
        return Result(FloorPlan(rooms = rooms, stairs = stairs(reading.stairs, k), freeWalls = walls, underlay = underlay), cmPerPx, checks)
    }

    /**
     * Ogni apertura sul muro parallelo più vicino al suo centro (entro lo spessore del muro e un margine), meglio di una stanza
     * interna che di un balcone: le porte tra due stanze creano da sole il varco anche nell'altra stanza.
     */
    internal fun withOpenings(rooms: List<Room>, openings: List<PlanReading.ReadOpening>, k: Double): List<Room> {
        val added = rooms.map { mutableListOf<Opening>() }
        var id = 0L
        for (o in openings) {
            val kind = openingKind(o.kind) ?: continue
            if (o.ends.size < 2 || o.ends.any { it.size < 2 }) continue
            val a = Vec2(o.ends[0][0] * k, o.ends[0][1] * k)
            val b = Vec2(o.ends[1][0] * k, o.ends[1][1] * k)
            if (a.distanceTo(b) < 1e-6) continue
            val mid = (a + b) / 2.0
            val u = (b - a).normalized()
            var best: Triple<Int, Int, Double>? = null
            var bestScore = Double.MAX_VALUE
            for ((ri, room) in rooms.withIndex()) for (j in 0 until room.wallCount) {
                val s = room.wallStart(j)
                val e = room.wallEnd(j)
                val len = s.distanceTo(e)
                if (len < 1e-6) continue
                val v = (e - s) / len
                if (abs(u.cross(v)) > 0.2) continue
                val t = (mid - s) dot v
                if (t < 0 || t > len) continue
                val dist = abs((mid - s) dot v.perp())
                if (dist > room.thicknessOf(j) / 2 + OPENING_MARGIN) continue
                val score = dist + if (room.type.outdoor) OUTDOOR_PENALTY else 0.0
                if (score < bestScore) { bestScore = score; best = Triple(ri, j, t) }
            }
            val (ri, j, t) = best ?: continue
            val d = Opening.default(++id, kind, j, t)
            added[ri] += d.copy(
                width = o.widthCm ?: a.distanceTo(b),
                height = o.heightCm ?: d.height,
                sillHeight = if (kind == OpeningKind.Window1 || kind == OpeningKind.Window2) o.sillCm ?: d.sillHeight else 0.0,
                splay = if (kind.glazed) o.splayCm ?: 0.0 else 0.0,
                splayRounded = o.splayRounded,
                frame = when (o.frame?.lowercase()) {
                    "outside" -> FramePosition.Outside
                    "inside" -> FramePosition.Inside
                    else -> FramePosition.Center
                },
                entrance = o.entrance && !kind.glazed,
                sliding = o.sliding && kind.hasLeaves,
            ).let { it.copy(position = Openings.clampPosition(rooms[ri].wallLength(j), it.width, t)) }
                .let { leavesAsDrawn(it, rooms[ri], o, k) }
        }
        return rooms.mapIndexed { i, r -> if (added[i].isEmpty()) r else r.copy(openings = r.openings + added[i]) }
    }

    /** Muri singoli con le loro aperture, che stanno sul muro 0 della [FreeWall.host] (da `start` verso `end`). */
    internal fun freeWalls(read: List<PlanReading.ReadWall>, k: Double, firstOpeningId: Long): List<FreeWall> {
        var openingId = firstOpeningId - 1
        return read.mapIndexedNotNull { i, r ->
            if (r.from.size < 2 || r.to.size < 2) return@mapIndexedNotNull null
            val start = Vec2(r.from[0] * k, r.from[1] * k)
            val end = Vec2(r.to[0] * k, r.to[1] * k)
            val len = start.distanceTo(end)
            if (len < 1.0) return@mapIndexedNotNull null
            val wall = FreeWall(i + 1L, start, end, thickness = r.thicknessPx?.let { (it * k).coerceIn(3.0, MAX_WALL) } ?: 10.0)
            val u = (end - start) / len
            val openings = r.openings.mapNotNull { o ->
                val kind = openingKind(o.kind) ?: return@mapNotNull null
                if (o.ends.size < 2 || o.ends.any { it.size < 2 }) return@mapNotNull null
                val a = Vec2(o.ends[0][0] * k, o.ends[0][1] * k)
                val b = Vec2(o.ends[1][0] * k, o.ends[1][1] * k)
                val width = min(o.widthCm ?: a.distanceTo(b), len)
                val d = Opening.default(++openingId, kind, 0, 0.0)
                val opening = d.copy(
                    position = Openings.clampPosition(len, width, ((a + b) / 2.0 - start) dot u),
                    width = width,
                    height = o.heightCm ?: d.height,
                    sillHeight = if (kind == OpeningKind.Window1 || kind == OpeningKind.Window2) o.sillCm ?: d.sillHeight else 0.0,
                    sliding = o.sliding && kind.hasLeaves,
                )
                leavesAsDrawn(opening, wall.host, o, k)
            }
            wall.copy(openings = openings)
        }
    }

    /**
     * Verso delle ante come sulla piantina: i cardini (o il lato verso cui scorre) all'inizio o alla fine del muro, e l'apertura
     * verso la stanza o verso l'altra parte. Quello che non è letto resta com'è.
     */
    private fun leavesAsDrawn(o: Opening, room: Room, read: PlanReading.ReadOpening, k: Double): Opening {
        if (!o.kind.hasLeaves) return o
        val start = room.wallStart(o.wallIndex)
        val u = (room.wallEnd(o.wallIndex) - start).normalized()
        val center = start + u * o.position
        var r = o
        read.hinge?.takeIf { it.size >= 2 }?.let { h -> r = r.copy(hingeLeft = ((Vec2(h[0] * k, h[1] * k) - start) dot u) < o.position) }
        read.opensToward?.takeIf { it.size >= 2 }?.let { p ->
            r = r.copy(opensInward = ((Vec2(p[0] * k, p[1] * k) - center) dot Openings.inwardNormal(room, o.wallIndex)) > 0)
        }
        return r
    }

    /**
     * Scale dritte: centro a metà della linea di salita, ruotate verso l'arrivo (a 0° si sale verso l'alto dello schermo), con le
     * pedate contate: tante alzate quante le pedate più una e la pedata che fa tornare la lunghezza.
     */
    internal fun stairs(read: List<PlanReading.ReadStair>, k: Double): List<Stair> = read.mapIndexedNotNull { i, r ->
        if (r.from.size < 2 || r.to.size < 2) return@mapIndexedNotNull null
        val a = Vec2(r.from[0] * k, r.from[1] * k)
        val b = Vec2(r.to[0] * k, r.to[1] * k)
        val run = a.distanceTo(b)
        if (run < 50.0) return@mapIndexedNotNull null
        val d = (b - a) / run
        val treads = (r.treads ?: kotlin.math.round(run / DEFAULT_TREAD).toInt()).coerceIn(Stairs.MIN_RISERS - 1, Stairs.MAX_RISERS - 1)
        Stair(
            id = i + 1L, kind = StairKind.Straight, center = (a + b) / 2.0,
            rotation = toDegrees(kotlin.math.atan2(d.x, -d.y)),
            width = r.widthCm ?: r.widthPx?.let { it * k } ?: Stair(0, StairKind.Straight, Vec2.Zero).width,
            tread = run / treads, risers = treads + 1,
        )
    }

    internal fun openingKind(kind: String): OpeningKind? = when (kind.lowercase().trim()) {
        "door" -> OpeningKind.Door
        "double_door" -> OpeningKind.Door2
        "window" -> OpeningKind.Window1
        "double_window" -> OpeningKind.Window2
        "balcony" -> OpeningKind.Balcony1
        "double_balcony" -> OpeningKind.Balcony2
        "passage" -> OpeningKind.Passage
        else -> null
    }

    /**
     * Spessore di ogni muro (cm), dal filo interno delle stanze: un lato che ha di fronte, a meno di [MAX_WALL], il lato di
     * un'altra stanza è un muro in comune, spesso quanto lo spazio tra le due (così le due stanze finiscono sulla stessa mezzeria);
     * gli altri sono muri esterni, spessi [exterior] (per stanza).
     */
    internal fun thicknesses(interiors: List<List<Vec2>>, exterior: (room: Int, a: Vec2, b: Vec2) -> Double): List<List<Double>> =
        interiors.mapIndexed { ri, pts ->
            List(pts.size) { i ->
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val len = a.distanceTo(b)
                val d = (b - a).normalized()
                val inward = d.perp()
                var best: Double? = null
                for ((rj, other) in interiors.withIndex()) {
                    if (rj == ri || len < 1e-6) continue
                    for (j in other.indices) {
                        val c = other[j]
                        val e = other[(j + 1) % other.size]
                        val d2 = (e - c).normalized()
                        // Lato parallelo, percorso al contrario (le due stanze stanno ai due lati del muro).
                        if (abs(d.cross(d2)) > 0.03 || (d dot d2) > 0) continue
                        val gap = (c - a) dot (inward * -1.0)
                        if (gap < -2.0 || gap > MAX_WALL) continue
                        val t1 = (c - a) dot d
                        val t2 = (e - a) dot d
                        val overlap = minOf(len, maxOf(t1, t2)) - maxOf(0.0, minOf(t1, t2))
                        if (overlap < minOf(20.0, 0.3 * minOf(len, c.distanceTo(e)))) continue
                        if (best == null || gap < best) best = gap
                    }
                }
                best?.coerceIn(MIN_WALL, MAX_WALL) ?: exterior(ri, a, b)
            }
        }

    /**
     * Spessore di un muro esterno dal filo interno `a`-`b` (stanza a destra del verso a→b, come nei contorni letti) al filo
     * esterno [outline]: distanza verso l'esterno dal centro del lato. Null se manca il contorno o se è troppo lontano.
     */
    internal fun outlineDistance(outline: List<Vec2>, a: Vec2, b: Vec2): Double? {
        if (outline.size < 3 || a.distanceTo(b) < 1e-6) return null
        val m = (a + b) / 2.0
        val out = (b - a).normalized().perp() * -1.0
        var best: Double? = null
        for (j in outline.indices) {
            val c = outline[j]
            val e = outline[(j + 1) % outline.size]
            val s = e - c
            val det = out.cross(s)
            if (abs(det) < 1e-9) continue
            val t = (c - m).cross(s) / det
            val u = (c - m).cross(out) / det
            if (t > 0.5 && u >= -1e-9 && u <= 1 + 1e-9 && (best == null || t < best)) best = t
        }
        return best?.takeIf { it <= MAX_WALL }?.coerceAtLeast(MIN_WALL)
    }

    /**
     * Centimetri per pixel: per ogni stanza rettangolare con due lati scritti, lato scritto / lato in pixel (il più lungo con il
     * più lungo); per ogni stanza con i metri quadri scritti, √(area scritta / area in pixel). La mediana scarta le letture storte.
     */
    internal fun scale(rooms: List<Pair<PlanReading.ReadRoom, List<Vec2>>>): Double? {
        val ratios = buildList {
            for ((r, px) in rooms) {
                if (r.sidesM.size == 2 && isRectangle(px)) {
                    val b = Polygon.bounds(px)
                    val pxSides = listOf(b.width, b.height).sorted()
                    val cm = r.sidesM.map { it * 100 }.sorted()
                    for (j in 0..1) if (pxSides[j] > 0) add(cm[j] / pxSides[j])
                }
                val a = r.areaM2
                val pxArea = Polygon.area(px) - zonesPx(r).sumOf { Polygon.area(it) }
                if (a != null && a > 0 && pxArea > 0) add(sqrt(a * 10_000 / pxArea))
            }
        }.sorted()
        if (ratios.isEmpty()) return null
        val m = ratios.size / 2
        return if (ratios.size % 2 == 1) ratios[m] else (ratios[m - 1] + ratios[m]) / 2
    }

    /** Lati quasi orizzontali o quasi verticali resi esatti: ogni angolo prende la x dal suo lato verticale e la y da quello orizzontale. */
    internal fun squared(pts: List<Vec2>): List<Vec2> {
        val n = pts.size
        fun edge(i: Int) = pts[(i + 1) % n] - pts[i]
        val horizontal = BooleanArray(n) { val d = edge(it); abs(d.y) <= abs(d.x) * SQUARE_TOLERANCE }
        val vertical = BooleanArray(n) { val d = edge(it); abs(d.x) <= abs(d.y) * SQUARE_TOLERANCE }
        return List(n) { i ->
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            val xs = listOfNotNull(
                if (vertical[prev]) (pts[prev].x + pts[i].x) / 2 else null,
                if (vertical[i]) (pts[i].x + pts[next].x) / 2 else null,
            )
            val ys = listOfNotNull(
                if (horizontal[prev]) (pts[prev].y + pts[i].y) / 2 else null,
                if (horizontal[i]) (pts[i].y + pts[next].y) / 2 else null,
            )
            Vec2(if (xs.isEmpty()) pts[i].x else xs.average(), if (ys.isEmpty()) pts[i].y else ys.average())
        }
    }

    /** Zone non calpestabili della stanza, in pixel. */
    private fun zonesPx(r: PlanReading.ReadRoom): List<List<Vec2>> =
        r.excluded.map { z -> z.filter { it.size >= 2 }.map { Vec2(it[0], it[1]) } }.filter { it.size >= 3 }

    private fun isRectangle(pts: List<Vec2>): Boolean {
        if (pts.size != 4) return false
        val b = Polygon.bounds(pts)
        return pts.all { p -> (abs(p.x - b.minX) < 1e-6 || abs(p.x - b.maxX) < 1e-6) && (abs(p.y - b.minY) < 1e-6 || abs(p.y - b.maxY) < 1e-6) }
    }

    /**
     * Rettangolo con due lati scritti: prende esattamente quelle misure, allargato o stretto attorno al suo centro, se non se ne
     * scosta più di [FIT_TOLERANCE]. Le altre forme restano come lette.
     */
    internal fun fitted(interior: List<Vec2>, sidesCm: List<Double>): List<Vec2> {
        if (sidesCm.size != 2 || !isRectangle(interior)) return interior
        val b = Polygon.bounds(interior)
        fun err(w: Double, h: Double) = max(abs(w - b.width) / b.width, abs(h - b.height) / b.height)
        val (w, h) = listOf(sidesCm[0] to sidesCm[1], sidesCm[1] to sidesCm[0]).minBy { (w, h) -> err(w, h) }
        if (err(w, h) > FIT_TOLERANCE) return interior
        val c = b.center
        return interior.map { p -> Vec2(c.x + (if (p.x < c.x) -w else w) / 2, c.y + (if (p.y < c.y) -h else h) / 2) }
    }

    /**
     * Muri di stanze diverse quasi sulla stessa linea (entro [tolerance]) portati sulla stessa linea, così due stanze vicine hanno il
     * muro in comune e non due muri accostati o sovrapposti.
     */
    internal fun aligned(rooms: List<List<Vec2>>, tolerance: Double): List<List<Vec2>> {
        fun groups(values: List<Double>): Map<Double, Double> {
            val out = HashMap<Double, Double>()
            val sorted = values.distinct().sorted()
            var start = 0
            for (i in 1..sorted.size) {
                if (i == sorted.size || sorted[i] - sorted[start] > tolerance) {
                    val group = sorted.subList(start, i)
                    val mean = group.average()
                    for (v in group) out[v] = mean
                    start = i
                }
            }
            return out
        }
        fun onVertical(pts: List<Vec2>, i: Int): Boolean {
            val n = pts.size
            return abs(pts[(i + 1) % n].x - pts[i].x) < 1e-6 || abs(pts[(i - 1 + n) % n].x - pts[i].x) < 1e-6
        }
        fun onHorizontal(pts: List<Vec2>, i: Int): Boolean {
            val n = pts.size
            return abs(pts[(i + 1) % n].y - pts[i].y) < 1e-6 || abs(pts[(i - 1 + n) % n].y - pts[i].y) < 1e-6
        }
        val xs = groups(rooms.flatMap { pts -> pts.indices.filter { onVertical(pts, it) }.map { pts[it].x } })
        val ys = groups(rooms.flatMap { pts -> pts.indices.filter { onHorizontal(pts, it) }.map { pts[it].y } })
        return rooms.map { pts ->
            pts.mapIndexed { i, p ->
                Vec2(if (onVertical(pts, i)) xs[p.x] ?: p.x else p.x, if (onHorizontal(pts, i)) ys[p.y] ?: p.y else p.y)
            }
        }
    }

    private fun dedupe(pts: List<Vec2>): List<Vec2> {
        val out = pts.filterIndexed { i, p -> i == 0 || p.distanceTo(pts[i - 1]) > 1e-6 }
        return if (out.size > 1 && out.first().distanceTo(out.last()) <= 1e-6) out.dropLast(1) else out
    }

    /** "CUCINA" → "Cucina", "salotto" → "Salotto"; sigle brevi (WC, DIs) restano come sono. */
    internal fun displayName(name: String): String {
        val t = name.trim()
        if (t.length <= 3) return t
        val lower = if (t == t.uppercase()) t.lowercase() else t
        return lower.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Tipo di stanza dal nome scritto (anche abbreviato, come sulle catastali: "Disimp.", "Rip.", "K"); quello che non si
     * riconosce resta "Altro" e si cambia dalla scheda della stanza.
     */
    fun typeOf(name: String): RoomType {
        val n = name.lowercase().replace(Regex("[^a-zàèéìòù0-9 ]"), " ").trim().replace(Regex(" +"), " ")
        fun has(vararg keys: String) = keys.any { k -> n == k || n.startsWith("$k ") || n.split(' ').any { w -> w.startsWith(k) } }
        return when {
            n == "k" || has("cucin", "cottura", "kitchen", "cuisine", "cocina") -> RoomType.Cucina
            n == "b" || n.replace(" ", "") == "wc" || has("bagno", "bagni", "servizi", "lav", "bath", "bain", "baño", "aseo", "toilet") -> RoomType.Bagno
            has("cameret", "bambin") -> RoomType.Cameretta
            has("letto", "camera", "matrimonial", "bedroom", "chambre", "dormitorio", "habitación") -> RoomType.Camera
            has("soggiorno", "salott", "salone", "living", "pranzo", "sala", "séjour", "salon", "lounge") -> RoomType.Soggiorno
            has("studio", "ufficio", "office", "bureau", "despacho") -> RoomType.Studio
            has("disimp", "dis", "corridoio", "ingresso", "ing", "atrio", "hall", "entrée", "pasillo", "couloir") -> RoomType.Corridoio
            has("balcon", "loggia", "poggiol") -> RoomType.Balcone
            has("terrazz", "portico", "veranda", "patio", "terrasse", "terraza") -> RoomType.Terrazza
            else -> RoomType.Altro
        }
    }
}
