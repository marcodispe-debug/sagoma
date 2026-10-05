package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallCut
import kotlin.math.max
import kotlin.math.min

/** Piano "altezza = c0 + gx·x + gy·y" sulla pianta (cm). */
data class HeightPlane(val c0: Double, val gx: Double, val gy: Double) {
    fun at(p: Vec2) = c0 + gx * p.x + gy * p.y
    operator fun minus(o: HeightPlane) = HeightPlane(c0 - o.c0, gx - o.gx, gy - o.gy)
}

/**
 * Pareti sotto il tetto e soffitto in pendenza (sottotetti). Una parete con il taglio diagonale è alta
 * normale fino al punto scelto, poi scende fino all'altezza del muro perpendicolare più basso (il muretto
 * del sottotetto). Il soffitto della stanza fa lo stesso: è piano fino alla linea da cui inizia la
 * pendenza, poi scende verso quel muro.
 */
object Ceilings {

    // ---------- Pareti tagliate ----------

    /** Muro perpendicolare verso cui scende il taglio del muro `i` (quello nell'angolo basso). */
    fun lowWall(room: Room, i: Int, cut: WallCut): Int =
        if (cut.towardEnd) (i + 1) % room.wallCount else (i - 1 + room.wallCount) % room.wallCount

    /** Angolo basso del muro `i`: quello verso cui la parete scende. */
    fun lowCorner(room: Room, i: Int, cut: WallCut): Vec2 = if (cut.towardEnd) room.wallEnd(i) else room.wallStart(i)

    /** Lati del muro `i` verso cui si può tagliare: quelli dove il muro perpendicolare è più basso. */
    fun cutOptions(room: Room, i: Int): List<Boolean> = if (room.outdoor) emptyList() else listOf(false, true).filter { toward ->
        room.wallHeight(lowWall(room, i, WallCut(toward, 0.0))) < room.wallHeight(i) - 0.5
    }

    /** Taglio del muro `i`, se ha effetto (il muro perpendicolare è davvero più basso). */
    fun effectiveCut(room: Room, i: Int): WallCut? {
        if (i >= room.wallCount || room.outdoor) return null
        val cut = room.wallCuts[i] ?: return null
        if (cut.start < 1.0 || room.wallHeight(lowWall(room, i, cut)) >= room.wallHeight(i) - 0.5) return null
        return cut
    }

    /** Punto del muro (in mezzeria) da cui la parete inizia a scendere. */
    fun startPoint(room: Room, i: Int): Vec2? {
        val cut = effectiveCut(room, i) ?: return null
        val corner = lowCorner(room, i, cut)
        val other = if (cut.towardEnd) room.wallStart(i) else room.wallEnd(i)
        return corner + (other - corner).normalized() * min(cut.start, room.wallLength(i))
    }

    /** Altezza del bordo superiore del muro `i` a distanza `t` (cm) dal suo inizio. */
    fun wallTopAt(room: Room, i: Int, t: Double): Double {
        // Balcone o terrazza: al posto del muro c'è il parapetto.
        if (room.outdoor) return room.parapetHeight
        val full = room.wallHeight(i)
        val cut = effectiveCut(room, i) ?: return full
        val len = room.wallLength(i)
        val fromLow = (if (cut.towardEnd) len - t else t).coerceIn(0.0, len)
        val s = min(cut.start, len)
        val low = room.wallHeight(lowWall(room, i, cut))
        return if (fromLow >= s) full else low + (full - low) * fromLow / s
    }

    /**
     * Altezza reale della parete nel punto `p` del muro `i` della stanza, tenendo conto delle stanze che
     * condividono il muro: conta solo il muro che in quel punto c'è davvero e, se da una parte la parete è
     * tagliata in diagonale, vince il taglio. È l'altezza usata nel 3D e per gli avvisi di collisione.
     */
    fun sharedWallTop(plan: FloorPlan, room: Room, i: Int, p: Vec2): Double {
        val s = room.wallStart(i)
        val e = room.wallEnd(i)
        val half = Room.WALL_THICKNESS / 2
        val owners = listOf(room to i) + plan.rooms.filter { it.id != room.id }.mapNotNull { o ->
            (0 until o.wallCount).firstOrNull { j -> Dimensions.inContact(s, e, o.wallStart(j), o.wallEnd(j)) }?.let { o to it }
        }
        val here = owners.mapNotNull { (o, j) ->
            val tj = (p - o.wallStart(j)) dot (o.wallEnd(j) - o.wallStart(j)).normalized()
            if (o.id != room.id && (tj < -half - 1 || tj > o.wallLength(j) + half + 1)) null else Triple(o, j, tj)
        }
        val cutHere = here.filter { (o, j, _) -> effectiveCut(o, j) != null }
        return (cutHere.ifEmpty { here }).maxOf { (o, j, tj) -> wallTopAt(o, j, tj) }
    }

    // ---------- Soffitto ----------

    /**
     * Pendenze del soffitto: una per ogni muro basso verso cui scende almeno una parete tagliata. Il piano
     * sale dal muro basso (alla sua altezza) fino al soffitto alla distanza ortogonale `d` dei punti di inizio
     * discesa (`startPoint`) dal muro basso, e vale solo nella fascia davanti a quel muro. I due muri laterali di
     * una mansarda tengono lo stesso `d` (vedi [withCutStart]); se non fosse così (file salvati prima) vale la media.
     */
    private fun slopes(room: Room): List<Pair<HeightPlane, List<HeightPlane>>> {
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        return cutWallsByLowWall(room).mapNotNull { (l, walls) ->
            val low = room.wallHeight(l)
            val full = room.ceilingHeight
            if (low >= full) return@mapNotNull null
            val a = room.wallStart(l)
            val b = room.wallEnd(l)
            val len = a.distanceTo(b)
            if (len < 1.0) return@mapNotNull null
            val u = (b - a) / len
            val n = u.perp() * sign
            val d = walls.map { (startPoint(room, it)!! - a) dot n }.average()
            if (d < 1e-6) return@mapNotNull null
            val k = (full - low) / d
            val plane = HeightPlane(low - k * (a dot n), k * n.x, k * n.y)
            // Fascia davanti al muro basso: proiezione sul muro tra 0 e la sua lunghezza.
            val rules = listOf(HeightPlane(a dot u, -u.x, -u.y), HeightPlane(-(a dot u) - len, u.x, u.y))
            plane to rules
        }
    }

    /** Muri con taglio efficace, raggruppati per muro basso verso cui scendono (al più due: i muri accanto a quello basso). */
    private fun cutWallsByLowWall(room: Room): Map<Int, List<Int>> =
        (0 until room.wallCount).mapNotNull { i -> effectiveCut(room, i)?.let { lowWall(room, i, it) to i } }
            .groupBy({ it.first }, { it.second })

    fun hasSlope(room: Room): Boolean = slopes(room).isNotEmpty()

    private fun planesAt(room: Room, p: Vec2, slopes: List<Pair<HeightPlane, List<HeightPlane>>>): List<HeightPlane> =
        slopes.filter { (_, rules) -> rules.all { it.at(p) <= 1e-6 } }.map { it.first } + HeightPlane(room.ceilingHeight, 0.0, 0.0)

    /** Altezza del soffitto (cm) nel punto p della stanza. */
    fun heightAt(room: Room, p: Vec2): Double = planesAt(room, p, slopes(room)).minOf { it.at(p) }

    /**
     * Soffitto in triangoli con le quote esatte: il pavimento si taglia ai bordi delle fasce in pendenza,
     * poi ogni pezzo si divide dove cambia il piano più basso (lo spigolo in cui la pendenza incontra il piano).
     */
    fun triangles(room: Room): List<Triple<Vec3, Vec3, Vec3>> {
        if (room.outdoor) return emptyList() // all'aperto: niente soffitto
        val slopes = slopes(room)
        val lines = slopes.flatMap { it.second }.distinct()
        val out = mutableListOf<Triple<Vec3, Vec3, Vec3>>()
        for ((ia, ib, ic) in Triangulation.triangulate(room.points)) {
            var cells = listOf(listOf(room.points[ia], room.points[ib], room.points[ic]))
            for (line in lines) cells = cells.flatMap { c ->
                listOf(clip(c, line, strict = false), clip(c, HeightPlane(-line.c0, -line.gx, -line.gy), strict = true)).filter { it.size >= 3 }
            }
            for (cell in cells) {
                val centroid = cell.reduce { a, b -> a + b } / cell.size.toDouble()
                val planes = planesAt(room, centroid, slopes).distinct()
                for ((k, pk) in planes.withIndex()) {
                    var poly = cell
                    for ((j, pj) in planes.withIndex()) {
                        if (j == k || poly.size < 3) continue
                        poly = clip(poly, pk - pj, strict = j < k) // a parità comanda l'indice più basso
                    }
                    if (poly.size < 3) continue
                    for (m in 1 until poly.size - 1) {
                        out += Triple(poly[0].at(pk.at(poly[0])), poly[m].at(pk.at(poly[m])), poly[m + 1].at(pk.at(poly[m + 1])))
                    }
                }
            }
        }
        return out
    }

    /** Volume in m³ sul filo interno dei muri: area calpestabile × altezza media del soffitto. */
    fun volumeM3(room: Room): Double {
        if (room.outdoor) return 0.0
        val tris = triangles(room)
        val area = tris.sumOf { (a, b, c) -> Polygon.area(listOf(a.flat, b.flat, c.flat)) }
        val avg = if (area > 0) tris.sumOf { (a, b, c) -> Polygon.area(listOf(a.flat, b.flat, c.flat)) * (a.y + b.y + c.y) / 3 } / area
        else room.ceilingHeight
        return room.interiorArea() * avg / 1_000_000.0
    }

    /**
     * Linea da cui il soffitto inizia a scendere, per ogni muro basso (per disegnarla sulla pianta). Con due tagli alla stessa
     * distanza dal muro basso (il caso normale: i due pallini sono allineati) è il segmento tra i due pallini, quindi passa
     * esattamente da entrambi; altrimenti è la parallela al muro basso alla distanza di inizio discesa (media, se i due differiscono).
     */
    fun slopeStartLines(room: Room): List<Pair<Vec2, Vec2>> {
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        return cutWallsByLowWall(room).map { (l, walls) ->
            val a = room.wallStart(l)
            val b = room.wallEnd(l)
            val n = (b - a).normalized().perp() * sign
            val points = walls.map { startPoint(room, it)!! }
            val dists = points.map { (it - a) dot n }
            if (points.size == 2 && kotlin.math.abs(dists[0] - dists[1]) <= SAME_DISTANCE_CM) points[0] to points[1]
            else {
                val d = dists.average()
                (a + n * d) to (b + n * d)
            }
        }
    }

    /** Taglio di un poligono convesso con il semipiano diff(p) ≤ 0 (o < 0 se `strict`, per le parità). */
    private fun clip(poly: List<Vec2>, diff: HeightPlane, strict: Boolean): List<Vec2> {
        val eps = if (strict) -1e-7 else 1e-7
        val out = mutableListOf<Vec2>()
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val dp = diff.at(p)
            val dq = diff.at(q)
            val pIn = dp <= eps
            val qIn = dq <= eps
            if (pIn) out += p
            if (pIn != qIn) out += p + (q - p) * (dp / (dp - dq))
        }
        return out
    }

    /** Due distanze dal muro basso che differiscono meno di così (cm) sono lo stesso inizio di falda. */
    private const val SAME_DISTANCE_CM = 1e-3

    /** L'altro lato della mansarda: l'altro muro con taglio efficace che scende verso lo stesso muro basso di `i`. */
    fun partnerWall(room: Room, i: Int): Int? {
        val cut = effectiveCut(room, i) ?: return null
        val l = lowWall(room, i, cut)
        return (0 until room.wallCount).firstOrNull { j -> j != i && effectiveCut(room, j)?.let { lowWall(room, j, it) } == l }
    }

    /** Quanto un cm lungo il muro `i`, dall'angolo basso, si allontana dal muro basso (ortogonalmente): 1 se è perpendicolare. */
    private fun distancePerCm(room: Room, i: Int, cut: WallCut): Double {
        val l = lowWall(room, i, cut)
        val a = room.wallStart(l)
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        val n = (room.wallEnd(l) - a).normalized().perp() * sign
        val other = if (cut.towardEnd) room.wallStart(i) else room.wallEnd(i)
        return (other - lowCorner(room, i, cut)).normalized() dot n
    }

    /**
     * Imposta l'inizio di discesa `start` (cm, lungo il muro `i` dal suo angolo basso) e porta l'altro lato della mansarda alla stessa
     * distanza dal muro basso: i due pallini descrivono un solo inizio di falda e la linea passa da entrambi. Con muri laterali
     * perpendicolari i due `start` coincidono; con muri obliqui cambiano per restare sulla stessa parallela. Rispetta i limiti di
     * [clampStart] di tutti e due i muri. Senza l'altro lato (un solo taglio) cambia solo il muro `i`.
     */
    fun withCutStart(room: Room, i: Int, start: Double): Room {
        val cut = room.wallCuts[i] ?: return room
        val j = partnerWall(room, i)
        val cutJ = j?.let { room.wallCuts[it] }
        var si = clampStart(room, i, start)
        if (j == null || cutJ == null) return room.copy(wallCuts = room.wallCuts + (i to cut.copy(start = si)))
        val ki = distancePerCm(room, i, cut)
        val kj = distancePerCm(room, j, cutJ)
        if (ki <= 1e-6 || kj <= 1e-6) return room.copy(wallCuts = room.wallCuts + (i to cut.copy(start = si))) // muro che non entra nella stanza
        var sj = clampStart(room, j, si * ki / kj)
        si = clampStart(room, i, sj * kj / ki) // se l'altro muro è più corto, il limite vale anche per questo
        sj = clampStart(room, j, si * ki / kj)
        return room.copy(wallCuts = room.wallCuts + (i to cut.copy(start = si)) + (j to cutJ.copy(start = sj)))
    }

    /** Al taglio appena attivato sul muro `i` dà lo stesso inizio di falda dell'altro lato, se c'è; altrimenti non cambia niente. */
    fun alignedToPartner(room: Room, i: Int): Room {
        val j = partnerWall(room, i) ?: return room
        val cutJ = room.wallCuts[j] ?: return room
        return withCutStart(room, j, cutJ.start)
    }

    fun clampStart(room: Room, i: Int, start: Double) = start.coerceIn(10.0, max(10.0, room.wallLength(i)))
}
