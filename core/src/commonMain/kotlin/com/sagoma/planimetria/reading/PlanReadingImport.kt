package com.sagoma.planimetria.reading

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
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
 * 4. Controlli: metri quadri e lati della stanza disegnata contro quelli scritti, e lati × lati contro i metri quadri scritti.
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
        val thicknesses = thicknesses(interiors) { i -> if (types[i].outdoor) Room.WALL_THICKNESS else exterior }
            .map { room -> room.map { it.roundToInt().toDouble() } }
        val centerlines = aligned(interiors.mapIndexed { i, pts -> Polygon.inset(pts, thicknesses[i].map { -it / 2 }) }, tolerance = ALIGN_TOLERANCE)
        val rooms = read.mapIndexed { i, (r, _) ->
            val main = thicknesses[i].groupingBy { it }.eachCount().maxBy { it.value }.key
            Room(
                id = i + 1L,
                name = displayName(r.name).ifBlank { types[i].defaultName },
                type = types[i],
                points = centerlines[i],
                wallThickness = main,
                wallThicknesses = thicknesses[i].withIndex().filter { it.value != main }.associate { it.index to it.value },
                floorFinish = types[i].defaultFloor,
            )
        }
        val checks = read.mapIndexed { i, (r, _) ->
            RoomCheck(
                name = rooms[i].name,
                areaRead = r.areaM2,
                areaDrawn = Polygon.interiorArea(rooms[i].points, rooms[i].thicknesses) / 10_000,
                sidesRead = r.sidesM.map { it * 100 },
                sidesDrawn = Polygon.interiorEdgeLengths(rooms[i].points, rooms[i].thicknesses),
            )
        }
        val underlay = Underlay(imageFile, reading.image.width, reading.image.height, origin = Vec2.Zero, cmPerPx = k, opacity = 0.5)
        return Result(FloorPlan(rooms = rooms, underlay = underlay), cmPerPx, checks)
    }

    /**
     * Spessore di ogni muro (cm), dal filo interno delle stanze: un lato che ha di fronte, a meno di [MAX_WALL], il lato di
     * un'altra stanza è un muro in comune, spesso quanto lo spazio tra le due (così le due stanze finiscono sulla stessa mezzeria);
     * gli altri sono muri esterni, spessi [exterior] (per stanza).
     */
    internal fun thicknesses(interiors: List<List<Vec2>>, exterior: (Int) -> Double): List<List<Double>> =
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
                best?.coerceIn(MIN_WALL, MAX_WALL) ?: exterior(ri)
            }
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
                val pxArea = Polygon.area(px)
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
