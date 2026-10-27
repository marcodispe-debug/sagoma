package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Roof
import com.sagoma.planimetria.model.RoofKind
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Forma del tetto inclinato ([Roof]) sopra l'ultimo piano: falde, timpani e linee da tratteggiare in pianta.
 * Le altezze sono dal piano d'appoggio (il solaio sopra l'ultimo piano, cioè il suo interpiano).
 *
 * Ogni falda sale con la stessa pendenza dal filo esterno di un muro: l'altezza di un punto è la pendenza per la
 * distanza dal muro più vicino tra quelli che portano una falda (due per le due falde, quattro per il padiglione,
 * uno per la falda unica). Fuori dai muri (la gronda) la distanza è negativa e il tetto scende sotto l'appoggio.
 *
 * Una casa a L, a T o a U si divide in ali rettangolari ([plan]): l'ala più grande ha il tetto intero, ogni braccio
 * entra nel tetto dell'ala a cui è attaccato fino al suo colmo. Nel 3D si vede la più alta delle due falde, e il compluvio
 * (la linea dove due falde si incontrano formando una conca) viene da sé.
 */
object Roofs {
    /** Falda: poligono convesso in pianta, con l'altezza (sopra l'appoggio) della faccia di sopra in ogni angolo. */
    data class Face(val points: List<Vec2>, val heights: List<Double>)

    /** Muro a triangolo o a trapezio sotto il tetto (timpano), in verticale sopra il filo esterno dei muri. */
    data class Gable(val points: List<Vec2>, val heights: List<Double>)

    /** Tetto di un'ala rettangolare. */
    data class Shape(
        /** Rettangolo dei muri esterni dell'ala (un braccio arriva dentro l'ala a cui è attaccato). */
        val walls: Bounds,
        /** Rettangolo della gronda: i muri più lo sporto (niente sporto dal lato attaccato a un'altra ala). */
        val eaves: Bounds,
        val faces: List<Face>,
        val gables: List<Gable>,
        /** Colmo e displuvi (gli spigoli tra due falde), da tratteggiare in pianta. */
        val ridges: List<Pair<Vec2, Vec2>>,
        /** Altezza del colmo sopra l'appoggio (cm). */
        val ridgeHeight: Double,
        /** Spessore del tetto misurato in verticale: la faccia di sotto sta così più in basso. */
        val thickness: Double,
    )

    /** Tetto di tutta la casa: le ali e le linee della pianta. */
    data class RoofPlan(
        val wings: List<Shape>,
        /** Giro della gronda (segmenti), che segue la forma della casa. */
        val eaves: List<Pair<Vec2, Vec2>>,
        /** Colmi, displuvi e compluvi, da tratteggiare in pianta. */
        val lines: List<Pair<Vec2, Vec2>>,
        /** Compluvi (dentro [lines]): dove due ali si incontrano. */
        val valleys: List<Pair<Vec2, Vec2>>,
        /** Colmo più alto sopra l'appoggio (cm). */
        val ridgeHeight: Double,
    )

    /** Rettangolo dei muri esterni delle stanze al chiuso del piano, o null se non ce ne sono. */
    fun footprint(plan: FloorPlan): Bounds? {
        val pts = plan.rooms.filter { !it.outdoor }.flatMap { it.exteriorOutline() }
        return if (pts.isEmpty()) null else Polygon.bounds(pts)
    }

    /** Tetto della casa sopra il suo ultimo piano, se c'è. */
    fun of(building: Building): RoofPlan? = plan(building.roof ?: return null, building.floors.last().plan)

    /** Lati di un rettangolo. */
    private enum class Side { MinX, MinY, MaxX, MaxY }

    /**
     * Tetto sopra le stanze al chiuso di `plan`. Con i muri tutti dritti sul disegno (orizzontali e verticali) segue la forma
     * della casa, ala per ala; con muri storti, o con la falda unica, copre il rettangolo che contiene la casa.
     */
    fun plan(roof: Roof, plan: FloorPlan): RoofPlan? {
        val outlines = plan.rooms.filter { !it.outdoor }.map { it.exteriorOutline() }.filter { it.size >= 3 }
        if (outlines.isEmpty()) return null
        val box = Polygon.bounds(outlines.flatten())
        val rects = if (roof.kind == RoofKind.Shed) null else wings(outlines)
        if (rects == null || rects.size <= 1) {
            val s = shape(roof, rects?.singleOrNull() ?: box) ?: return null
            return RoofPlan(listOf(s), rectEdges(s.eaves), s.ridges, emptyList(), s.ridgeHeight)
        }
        val o = overhangOf(roof)
        val main = rects.first()
        val mainShape = shape(roof, main) ?: return null
        val wings = mutableListOf(mainShape)
        // Ali messe: il rettangolo vero (per trovare chi tocca chi), il verso del colmo.
        val placed = mutableListOf(main to isAlongX(roof, main))
        val lines = mutableListOf<Pair<Vec2, Vec2>>()
        val valleys = mutableListOf<Pair<Vec2, Vec2>>()
        lines += mainShape.ridges
        for (r in rects.drop(1)) {
            // Il braccio si attacca all'ala che tocca, con il colmo che parte da lei.
            val (host, hostAlongX) = placed.firstOrNull { touching(it.first, r) != null } ?: continue
            val side = touching(host, r) ?: continue
            val alongX = side == Side.MinX || side == Side.MaxX
            // Fin dove entra nell'ala di attacco: al suo colmo se le corre di fianco, altrimenti la propria mezza larghezza.
            val hostHalf = if (alongX) host.width / 2 else host.height / 2
            val hw = if (alongX) r.height / 2 else r.width / 2
            val depth = if (hostAlongX != alongX) hostHalf else min(hw, hostHalf)
            val walls = when (side) {
                Side.MaxX -> Bounds(r.minX - depth, r.minY, r.maxX, r.maxY)
                Side.MinX -> Bounds(r.minX, r.minY, r.maxX + depth, r.maxY)
                Side.MaxY -> Bounds(r.minX, r.minY - depth, r.maxX, r.maxY)
                Side.MinY -> Bounds(r.minX, r.minY, r.maxX, r.maxY + depth)
            }
            // Capo del colmo dal lato dell'ala di attacco: niente sporto e niente timpano.
            val attachedAtStart = side == Side.MaxX || side == Side.MaxY
            val w = wing(roof, walls, alongX, attachedAtStart = attachedAtStart, attachedAtEnd = !attachedAtStart) ?: continue
            wings += w
            placed += r to alongX
            // Sotto il tetto del braccio le linee delle altre ali non si vedono più: sopra il braccio e la sua gronda, e dentro
            // l'ala di attacco fin dove arriva il suo colmo.
            val reach = min(hw, depth)
            val grown = Bounds(r.minX - o, r.minY - o, r.maxX + o, r.maxY + o)
            val covered = when (side) {
                Side.MaxX -> grown.copy(minX = r.minX - reach)
                Side.MinX -> grown.copy(maxX = r.maxX + reach)
                Side.MaxY -> grown.copy(minY = r.minY - reach)
                Side.MinY -> grown.copy(maxY = r.maxY + reach)
            }
            for (k in lines.indices.reversed()) {
                val pieces = outside(lines[k].first, lines[k].second, covered).filter { (a, b) -> a.distanceTo(b) > 1.0 }
                lines.removeAt(k)
                lines.addAll(k, pieces)
            }
            // In pianta il colmo del braccio finisce dove incontra la falda dell'ala di attacco (dentro di lei di mezza larghezza
            // del braccio); da lì scendono i due compluvi a 45° fino agli angoli rientranti della gronda.
            val dir = when (side) { Side.MaxX -> Vec2(-1.0, 0.0); Side.MinX -> Vec2(1.0, 0.0); Side.MaxY -> Vec2(0.0, -1.0); Side.MinY -> Vec2(0.0, 1.0) }
            val edge = when (side) { Side.MaxX -> r.minX; Side.MinX -> r.maxX; Side.MaxY -> r.minY; Side.MinY -> r.maxY }
            val mid = if (alongX) (r.minY + r.maxY) / 2 else (r.minX + r.maxX) / 2
            val lateral = if (alongX) Vec2(0.0, 1.0) else Vec2(1.0, 0.0)
            val base = if (alongX) Vec2(edge, mid) else Vec2(mid, edge)
            val meet = base + dir * reach
            fun stop(p: Vec2): Vec2 { val t = (p - base) dot dir; return if (t > reach) p - dir * (t - reach) else p }
            lines += w.ridges.map { (a, b) -> stop(a) to stop(b) }.filter { (a, b) -> a.distanceTo(b) > 0.5 }
            val hostSpan = if (alongX) host.minY..host.maxY else host.minX..host.maxX
            for (sgn in listOf(-1.0, 1.0)) {
                // Braccio a filo della testata di un padiglione: da quel lato la falda continua dritta, niente compluvio.
                val wall = mid + sgn * hw
                val flush = abs(wall - (if (sgn < 0) hostSpan.start else hostSpan.endInclusive)) < 1.0
                if (flush && roof.kind == RoofKind.Hip) continue
                val corner = base + lateral * (sgn * (hw + o)) - dir * o
                valleys += corner to meet
            }
        }
        lines += valleys
        return RoofPlan(wings, outline(wings.map { it.eaves }), lines, valleys, wings.maxOf { it.ridgeHeight })
    }

    /** Pezzi del segmento a→b fuori dal rettangolo `r` (ristretto di un poco: le linee sul suo bordo restano). */
    private fun outside(a: Vec2, b: Vec2, r: Bounds): List<Pair<Vec2, Vec2>> {
        val e = 0.5
        val d = b - a
        var t0 = 0.0
        var t1 = 1.0
        for ((p, q) in listOf(-d.x to a.x - (r.minX + e), d.x to (r.maxX - e) - a.x, -d.y to a.y - (r.minY + e), d.y to (r.maxY - e) - a.y)) {
            if (abs(p) < 1e-12) { if (q < 0) return listOf(a to b) else continue }
            val t = q / p
            if (p < 0) t0 = max(t0, t) else t1 = min(t1, t)
        }
        if (t0 >= t1) return listOf(a to b)
        return listOfNotNull(
            (a to (a + d * t0)).takeIf { t0 > 1e-6 },
            ((a + d * t1) to b).takeIf { t1 < 1 - 1e-6 },
        )
    }

    /** Lato di `host` su cui `r` gli sta attaccato da fuori (condividendo un tratto), o null. */
    private fun touching(host: Bounds, r: Bounds): Side? {
        val eps = 1.0
        val overlapY = min(host.maxY, r.maxY) - max(host.minY, r.minY)
        val overlapX = min(host.maxX, r.maxX) - max(host.minX, r.minX)
        return when {
            abs(r.minX - host.maxX) < eps && overlapY > eps -> Side.MaxX
            abs(r.maxX - host.minX) < eps && overlapY > eps -> Side.MinX
            abs(r.minY - host.maxY) < eps && overlapX > eps -> Side.MaxY
            abs(r.maxY - host.minY) < eps && overlapX > eps -> Side.MinY
            else -> null
        }
    }

    /**
     * Ali rettangolari della casa, la più grande per prima e poi ognuna attaccata a una già messa; null se un muro è storto
     * (allora basta il rettangolo che la contiene). Le misure più vicine di [SNAP] cm sono la stessa (muri di spessori un po'
     * diversi), e i pezzi più stretti di [MIN_WING] cm restano sotto il tetto delle altre ali.
     */
    private fun wings(outlines: List<List<Vec2>>): List<Bounds>? {
        for (o in outlines) for (k in o.indices) {
            val a = o[k]
            val b = o[(k + 1) % o.size]
            if (abs(a.x - b.x) > 1.0 && abs(a.y - b.y) > 1.0) return null
        }
        val xs = snap(outlines.flatten().map { it.x })
        val ys = snap(outlines.flatten().map { it.y })
        if (xs.size < 2 || ys.size < 2) return null
        val nx = xs.size - 1
        val ny = ys.size - 1
        val full = Array(nx) { i ->
            BooleanArray(ny) { j ->
                val c = Vec2((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2)
                outlines.any { Polygon.contains(it, c) }
            }
        }
        val found = mutableListOf<Bounds>()
        while (true) {
            // Rettangolo pieno più grande tra le celle rimaste (le piante hanno poche righe: si provano tutti).
            var best: IntArray? = null
            var bestArea = 0.0
            for (i0 in 0 until nx) for (j0 in 0 until ny) {
                if (!full[i0][j0]) continue
                var top = ny - 1
                for (i1 in i0 until nx) {
                    if (!full[i1][j0]) break
                    var j1 = j0
                    while (j1 + 1 <= top && full[i1][j1 + 1]) j1++
                    top = j1
                    val area = (xs[i1 + 1] - xs[i0]) * (ys[top + 1] - ys[j0])
                    if (area > bestArea) { bestArea = area; best = intArrayOf(i0, j0, i1, top) }
                }
            }
            val b = best ?: break
            for (i in b[0]..b[2]) for (j in b[1]..b[3]) full[i][j] = false
            val rect = Bounds(xs[b[0]], ys[b[1]], xs[b[2] + 1], ys[b[3] + 1])
            if (rect.width >= MIN_WING && rect.height >= MIN_WING) found += rect
        }
        if (found.isEmpty()) return null
        val ordered = mutableListOf(found.removeAt(0))
        while (true) {
            val next = found.firstOrNull { r -> ordered.any { touching(it, r) != null } } ?: break
            found.remove(next)
            ordered += next
        }
        return ordered
    }

    /** Misure ordinate, con quelle più vicine di [SNAP] cm unite (tenendo quella più esterna ai due capi, la media in mezzo). */
    private fun snap(values: List<Double>): List<Double> {
        val sorted = values.sorted()
        val groups = mutableListOf<MutableList<Double>>()
        for (v in sorted) if (groups.isNotEmpty() && v - groups.last().last() < SNAP) groups.last() += v else groups += mutableListOf(v)
        return groups.mapIndexed { k, g -> if (k == 0) g.first() else if (k == groups.lastIndex) g.last() else g.average() }
    }

    /** Distanza sotto la quale due fili dei muri sono lo stesso, e larghezza minima di un'ala con il suo tetto (cm). */
    private const val SNAP = 25.0
    private const val MIN_WING = 120.0

    /** Giro esterno dell'unione dei rettangoli: i lati delle celle tra dentro e fuori, uniti in segmenti lunghi. */
    private fun outline(rects: List<Bounds>): List<Pair<Vec2, Vec2>> {
        val xs = rects.flatMap { listOf(it.minX, it.maxX) }.distinct().sorted()
        val ys = rects.flatMap { listOf(it.minY, it.maxY) }.distinct().sorted()
        fun inside(x: Double, y: Double) = rects.any { x > it.minX && x < it.maxX && y > it.minY && y < it.maxY }
        val segs = mutableListOf<Pair<Vec2, Vec2>>()
        for (j in ys.indices) for (i in 0 until xs.size - 1) {
            val cx = (xs[i] + xs[i + 1]) / 2
            val below = j > 0 && inside(cx, (ys[j - 1] + ys[j]) / 2)
            val above = j < ys.lastIndex && inside(cx, (ys[j] + ys[j + 1]) / 2)
            if (below != above) segs += Vec2(xs[i], ys[j]) to Vec2(xs[i + 1], ys[j])
        }
        for (i in xs.indices) for (j in 0 until ys.size - 1) {
            val cy = (ys[j] + ys[j + 1]) / 2
            val left = i > 0 && inside((xs[i - 1] + xs[i]) / 2, cy)
            val right = i < xs.lastIndex && inside((xs[i] + xs[i + 1]) / 2, cy)
            if (left != right) segs += Vec2(xs[i], ys[j]) to Vec2(xs[i], ys[j + 1])
        }
        // Pezzi di fila sulla stessa retta: uno solo (arrivano già in ordine).
        val merged = mutableListOf<Pair<Vec2, Vec2>>()
        for (s in segs) {
            val last = merged.lastOrNull()
            val joins = last != null && last.second == s.first &&
                ((abs(last.first.y - last.second.y) < 1e-9 && abs(s.first.y - s.second.y) < 1e-9) ||
                    (abs(last.first.x - last.second.x) < 1e-9 && abs(s.first.x - s.second.x) < 1e-9))
            if (joins) merged[merged.lastIndex] = last!!.first to s.second else merged += s
        }
        return merged
    }

    private fun rectEdges(b: Bounds): List<Pair<Vec2, Vec2>> {
        val c = listOf(Vec2(b.minX, b.minY), Vec2(b.maxX, b.minY), Vec2(b.maxX, b.maxY), Vec2(b.minX, b.maxY))
        return c.indices.map { c[it] to c[(it + 1) % 4] }
    }

    private fun slopeOf(roof: Roof) = tan(roof.pitchDeg.coerceIn(Roof.MIN_PITCH, Roof.MAX_PITCH) * PI / 180)
    private fun overhangOf(roof: Roof) = roof.overhang.coerceIn(0.0, Roof.MAX_OVERHANG)

    /** Il colmo dell'ala corre lungo x? Con 0 giri lungo il lato più lungo; il padiglione sempre sul lato lungo. */
    private fun isAlongX(roof: Roof, walls: Bounds): Boolean {
        val longX = walls.width >= walls.height
        val turn = ((roof.turn % 4) + 4) % 4
        return if (roof.kind == RoofKind.Hip) longX else longX xor (turn % 2 == 1)
    }

    /** Tetto sopra il rettangolo `walls`, da solo (con lo sporto su tutti i lati). */
    fun shape(roof: Roof, walls: Bounds): Shape? = wing(roof, walls, isAlongX(roof, walls), attachedAtStart = false, attachedAtEnd = false)

    /**
     * Tetto di un'ala: si calcola in coordinate "locali" (u lungo il colmo, v di traverso) e si riporta in pianta scambiando
     * x e y quando il colmo corre lungo y. `attachedAtStart` / `attachedAtEnd`: il capo del colmo verso u piccolo / grande sta
     * dentro un'altra ala (niente sporto e niente timpano lì).
     */
    private fun wing(roof: Roof, walls: Bounds, alongX: Boolean, attachedAtStart: Boolean, attachedAtEnd: Boolean): Shape? {
        if (walls.width < 1.0 || walls.height < 1.0) return null
        val slope = slopeOf(roof)
        val o = overhangOf(roof)
        val turn = ((roof.turn % 4) + 4) % 4
        fun world(u: Double, v: Double) = if (alongX) Vec2(u, v) else Vec2(v, u)
        val u0 = if (alongX) walls.minX else walls.minY
        val u1 = if (alongX) walls.maxX else walls.maxY
        val v0 = if (alongX) walls.minY else walls.minX
        val v1 = if (alongX) walls.maxY else walls.maxX
        val uu0 = if (attachedAtStart) u0 else u0 - o
        val uu1 = if (attachedAtEnd) u1 else u1 + o
        val vv0 = v0 - o
        val vv1 = v1 + o
        val vc = (v0 + v1) / 2
        val hw = (v1 - v0) / 2
        // Falda unica: il lato basso è v0, o v1 dopo mezzo giro.
        val lowAtV1 = turn >= 2

        /** Distanza dal muro più vicino che porta una falda (negativa sulla gronda). */
        fun reach(u: Double, v: Double): Double = when (roof.kind) {
            RoofKind.Shed -> if (lowAtV1) v1 - v else v - v0
            RoofKind.Gable -> min(v - v0, v1 - v)
            RoofKind.Hip -> min(min(v - v0, v1 - v), min(u - u0, u1 - u))
        }
        fun face(vararg p: Pair<Double, Double>): Face? {
            val pts = p.map { world(it.first, it.second) }
            val distinct = pts.distinctBy { (it.x * 1000).toLong() to (it.y * 1000).toLong() }
            if (distinct.size < 3) return null
            return Face(pts, p.map { slope * reach(it.first, it.second) })
        }
        val thickness = Roof.THICKNESS
        val faces = mutableListOf<Face>()
        val gables = mutableListOf<Gable>()
        val ridges = mutableListOf<Pair<Vec2, Vec2>>()
        val ridgeHeight: Double
        when (roof.kind) {
            RoofKind.Shed -> {
                faces += face(uu0 to vv0, uu1 to vv0, uu1 to vv1, uu0 to vv1)!!
                val rise = slope * (v1 - v0)
                ridgeHeight = rise
                val (low, high) = if (lowAtV1) v1 to v0 else v0 to v1
                // Il muro alto (rettangolo) e i due di fianco (triangoli), fino sotto la falda.
                gables += Gable(listOf(world(u0, high), world(u1, high), world(u1, high), world(u0, high)), listOf(0.0, 0.0, rise, rise))
                gables += Gable(listOf(world(u0, low), world(u0, high), world(u0, high)), listOf(0.0, 0.0, rise))
                gables += Gable(listOf(world(u1, low), world(u1, high), world(u1, high)), listOf(0.0, 0.0, rise))
            }
            RoofKind.Gable -> {
                faces += face(uu0 to vv0, uu1 to vv0, uu1 to vc, uu0 to vc)!!
                faces += face(uu0 to vc, uu1 to vc, uu1 to vv1, uu0 to vv1)!!
                ridgeHeight = slope * hw
                for ((u, attached) in listOf(u0 to attachedAtStart, u1 to attachedAtEnd)) {
                    if (!attached) gables += Gable(listOf(world(u, v0), world(u, v1), world(u, vc)), listOf(0.0, 0.0, ridgeHeight))
                }
                ridges += world(uu0, vc) to world(uu1, vc)
            }
            RoofKind.Hip -> {
                val r0 = u0 + hw
                val r1 = max(r0, u1 - hw)
                faces += listOfNotNull(
                    face(uu0 to vv0, uu1 to vv0, r1 to vc, r0 to vc),
                    face(uu1 to vv1, uu0 to vv1, r0 to vc, r1 to vc),
                    face(uu0 to vv1, uu0 to vv0, r0 to vc),
                    face(uu1 to vv0, uu1 to vv1, r1 to vc),
                )
                ridgeHeight = slope * hw
                // Dal lato attaccato il colmo continua dentro l'altra ala (la testata a padiglione resta nascosta lì).
                val start = if (attachedAtStart) uu0 else r0
                val end = if (attachedAtEnd) uu1 else r1
                if (end - start > 0.5) ridges += world(start, vc) to world(end, vc)
                if (!attachedAtStart) for (cv in listOf(vv0, vv1)) ridges += world(uu0, cv) to world(r0, vc)
                if (!attachedAtEnd) for (cv in listOf(vv0, vv1)) ridges += world(uu1, cv) to world(r1, vc)
            }
        }
        // La faccia di sotto della falda passa sull'appoggio al filo del muro: quella di sopra è più alta dello spessore.
        val lifted = faces.map { f -> f.copy(heights = f.heights.map { it + thickness }) }
        val eaves = if (alongX) Bounds(uu0, vv0, uu1, vv1) else Bounds(vv0, uu0, vv1, uu1)
        return Shape(walls, eaves, lifted, gables, ridges, ridgeHeight + thickness, thickness)
    }
}
