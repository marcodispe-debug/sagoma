package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometria delle scale. Si costruisce in coordinate locali (si sale verso l'alto dello schermo, y negativa;
 * la destra è x positiva), poi si centra l'ingombro sul `center` della scala e la si ruota di `rotation` gradi.
 * Numero e altezza dei gradini vengono dall'altezza da superare (l'interpiano del piano della scala):
 * alzate di al massimo [MAX_RISER] cm, tutte uguali.
 */
object Stairs {
    /** Alzata massima (cm): da qui il numero di gradini. */
    const val MAX_RISER = 18.0
    /** Distanza tra le due rampe di una scala a U. */
    const val U_GAP = 10.0
    /** Raggio del palo centrale della chiocciola. */
    const val COLUMN_RADIUS = 8.0
    /** Distanza entro cui una scala trascinata si accosta alla faccia di un muro. */
    const val SNAP = 15.0

    /** Numero di alzate per salire di `rise` cm. */
    fun risers(rise: Double): Int = ceil(rise / MAX_RISER - 1e-9).toInt().coerceAtLeast(2)

    /** Gradino (o pianerottolo): la pedata in pianta e la quota a cui sta (cm dal pavimento). */
    class Step(val polygon: List<Vec2>, val top: Double, val landing: Boolean = false)

    /**
     * Scala costruita. `pieces`: ingombro in pianta come poligoni convessi (tocchi, vuoto nel solaio di sopra).
     * `path`: linea di salita, dal primo gradino all'arrivo.
     */
    class Layout(val steps: List<Step>, val pieces: List<List<Vec2>>, val path: List<Vec2>, val risers: Int, val riser: Double) {
        val bounds: Bounds get() = Polygon.bounds(pieces.flatten())
        fun contains(p: Vec2) = pieces.any { Polygon.contains(it, p) }
        /** Lunghezza della linea di salita (cm). */
        val run: Double get() = path.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
    }

    private fun rect(x0: Double, x1: Double, y0: Double, y1: Double) =
        listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))

    /** Alzate della scala: quelle scelte dall'utente, altrimenti il minimo con alzate di al massimo [MAX_RISER] cm. */
    fun risersOf(s: Stair, rise: Double): Int = (s.risers ?: risers(rise)).coerceIn(MIN_RISERS, MAX_RISERS)

    /** Pedate della prima rampa di una scala a L o a U (prima del pianerottolo), tra 1 e il totale meno una. */
    fun firstFlightOf(s: Stair, n: Int): Int {
        val treads = flightTreads(effectiveKind(s, n), n)
        return (s.firstFlight ?: ceil(treads / 2.0).toInt()).coerceIn(1, (treads - 1).coerceAtLeast(1))
    }

    /** Pedate della seconda rampa. */
    fun secondFlightOf(s: Stair, n: Int): Int = flightTreads(effectiveKind(s, n), n) - firstFlightOf(s, n)

    /**
     * Pedate delle due rampe insieme: il pianerottolo prende il posto di un gradino, i tre gradini a
     * ventaglio dell'angolo di tre.
     */
    private fun flightTreads(kind: StairKind, n: Int) = if (kind == StairKind.LWinder) n - 4 else n - 2

    /** Tipo effettivo: con troppo pochi gradini per le due rampe si ripiega su una forma più semplice. */
    fun effectiveKind(s: Stair, n: Int): StairKind = when {
        s.kind == StairKind.LWinder && n < 6 -> if (n < 4) StairKind.Straight else StairKind.LTurn
        s.hasTwoFlights && n < 4 -> StairKind.Straight
        else -> s.kind
    }

    /**
     * Pendenza scelta a parole: alzate alte circa `riser` cm, e la pedata che rende il passo comodo
     * (2 alzate + 1 pedata = 63 cm, ma non meno di 18 cm).
     */
    fun withSlope(s: Stair, rise: Double, riser: Double): Stair {
        val n = kotlin.math.round(rise / riser).toInt().coerceIn(MIN_RISERS, MAX_RISERS)
        val tread = (63 - 2 * rise / n).coerceAtLeast(18.0)
        return s.copy(risers = n, tread = kotlin.math.round(tread * 2) / 2.0)
    }

    const val MIN_RISERS = 3
    const val MAX_RISERS = 40

    /** Inclinazione della rampa in gradi (alzata su pedata). */
    fun slopeDegrees(riser: Double, tread: Double): Double = toDegrees(kotlin.math.atan2(riser, tread))

    fun layout(s: Stair, rise: Double): Layout {
        val n = risersOf(s, rise)
        val r = rise / n
        val w = s.width
        val g = s.tread
        val steps = mutableListOf<Step>()
        val pieces = mutableListOf<List<Vec2>>()
        val path = mutableListOf<Vec2>()
        // Le scale a due rampe hanno bisogno di almeno un gradino per rampa, oltre al pianerottolo.
        val kind = effectiveKind(s, n)
        val t1 = firstFlightOf(s, n)
        val t2 = secondFlightOf(s, n)
        when (kind) {
            StairKind.StraightLanding -> {
                for (i in 0 until t1) steps += Step(rect(-w / 2, w / 2, -(i + 1) * g, -i * g), (i + 1) * r)
                val l0 = t1 * g
                steps += Step(rect(-w / 2, w / 2, -(l0 + w), -l0), (t1 + 1) * r, landing = true)
                for (j in 0 until t2) steps += Step(rect(-w / 2, w / 2, -(l0 + w + (j + 1) * g), -(l0 + w + j * g)), (t1 + 2 + j) * r)
                val end = l0 + w + t2 * g
                pieces += rect(-w / 2, w / 2, -end, 0.0)
                path += listOf(Vec2(0.0, -g / 2), Vec2(0.0, -end))
            }
            StairKind.LWinder -> {
                for (i in 0 until t1) steps += Step(rect(-w / 2, w / 2, -(i + 1) * g, -i * g), (i + 1) * r)
                val ly0 = -(t1 * g + w)
                val ly1 = -t1 * g
                // Tre gradini a ventaglio nell'angolo, dal vertice interno della svolta: ognuno gira di 30°.
                val c = Vec2(w / 2, ly1)
                fun edge(deg: Double): Vec2 = when {
                    deg >= 90.0 -> Vec2(w / 2, ly0)
                    deg <= 45.0 -> Vec2(-w / 2, ly1 - w * kotlin.math.tan(toRadians(deg)))
                    else -> Vec2(w / 2 - w / kotlin.math.tan(toRadians(deg)), ly0)
                }
                for (k in 0 until 3) {
                    val a0 = 30.0 * k
                    val a1 = 30.0 * (k + 1)
                    val poly = mutableListOf(c, edge(a0))
                    if (a0 < 45.0 && a1 > 45.0) poly += Vec2(-w / 2, ly0)
                    poly += edge(a1)
                    steps += Step(poly, (t1 + 1 + k) * r)
                }
                for (j in 0 until t2) steps += Step(rect(w / 2 + j * g, w / 2 + (j + 1) * g, ly0, ly1), (t1 + 4 + j) * r)
                pieces += rect(-w / 2, w / 2, ly0, 0.0)
                if (t2 > 0) pieces += rect(w / 2, w / 2 + t2 * g, ly0, ly1)
                val my = (ly0 + ly1) / 2
                path += listOf(Vec2(0.0, -g / 2), Vec2(0.0, my), Vec2(w / 2 + t2 * g, my))
            }
            StairKind.Straight -> {
                val t = n - 1
                for (i in 0 until t) steps += Step(rect(-w / 2, w / 2, -(i + 1) * g, -i * g), (i + 1) * r)
                pieces += rect(-w / 2, w / 2, -t * g, 0.0)
                path += listOf(Vec2(0.0, -g / 2), Vec2(0.0, -t * g))
            }
            StairKind.LTurn -> {
                for (i in 0 until t1) steps += Step(rect(-w / 2, w / 2, -(i + 1) * g, -i * g), (i + 1) * r)
                val ly0 = -(t1 * g + w)
                val ly1 = -t1 * g
                steps += Step(rect(-w / 2, w / 2, ly0, ly1), (t1 + 1) * r, landing = true)
                for (j in 0 until t2) steps += Step(rect(w / 2 + j * g, w / 2 + (j + 1) * g, ly0, ly1), (t1 + 2 + j) * r)
                pieces += rect(-w / 2, w / 2, ly0, 0.0)
                if (t2 > 0) pieces += rect(w / 2, w / 2 + t2 * g, ly0, ly1)
                val my = (ly0 + ly1) / 2
                path += listOf(Vec2(0.0, -g / 2), Vec2(0.0, my), Vec2(w / 2 + t2 * g, my))
            }
            StairKind.UTurn -> {
                val x2 = w + U_GAP
                for (i in 0 until t1) steps += Step(rect(-w / 2, w / 2, -(i + 1) * g, -i * g), (i + 1) * r)
                val ly0 = -(t1 * g + w)
                val ly1 = -t1 * g
                steps += Step(rect(-w / 2, x2 + w / 2, ly0, ly1), (t1 + 1) * r, landing = true)
                for (j in 0 until t2) steps += Step(rect(x2 - w / 2, x2 + w / 2, ly1 + j * g, ly1 + (j + 1) * g), (t1 + 2 + j) * r)
                // La seconda rampa può essere più lunga della prima: l'ingombro arriva fin dove finisce.
                // Pianerottolo, le due rampe e lo spazio tra loro: il vuoto si ferma dove finisce ciascuna rampa.
                val end2 = ly1 + t2 * g
                pieces += rect(-w / 2, x2 + w / 2, ly0, ly1)
                pieces += rect(-w / 2, w / 2, ly1, 0.0)
                pieces += rect(x2 - w / 2, x2 + w / 2, ly1, end2)
                pieces += rect(w / 2, x2 - w / 2, ly1, maxOf(0.0, end2))
                val my = (ly0 + ly1) / 2
                path += listOf(Vec2(0.0, -g / 2), Vec2(0.0, my), Vec2(x2, my), Vec2(x2, ly1 + t2 * g))
            }
            StairKind.Spiral -> {
                val t = n - 1
                val big = s.diameter / 2
                val a = min(toRadians(30.0), toRadians(330.0) / t)
                val start = kotlin.math.PI / 2 // si parte in basso (verso chi arriva) e si sale in senso orario
                fun at(radius: Double, ang: Double) = Vec2(cos(ang) * radius, sin(ang) * radius)
                for (i in 0 until t) {
                    val a0 = start + i * a
                    val a1 = a0 + a
                    steps += Step(
                        listOf(at(COLUMN_RADIUS, a0), at(big, a0), at(big, (a0 + a1) / 2), at(big, a1), at(COLUMN_RADIUS, a1)),
                        (i + 1) * r,
                    )
                }
                pieces += (0 until 24).map { k -> at(big, k * kotlin.math.PI * 2 / 24) }
                val pr = (big + COLUMN_RADIUS) / 2
                val samples = 24
                for (k in 0..samples) path += at(pr, start + a / 2 + (t - 1) * a * k / samples)
            }
        }
        // Rampa che gira a sinistra: la stessa scala specchiata.
        fun mirror(p: Vec2) = if (s.turnLeft) Vec2(-p.x, p.y) else p
        val local = Layout(
            steps.map { Step(it.polygon.map(::mirror), it.top, it.landing) },
            pieces.map { pc -> pc.map(::mirror) },
            path.map(::mirror),
            n, r,
        )
        // Centro dell'ingombro sul centro della scala, poi rotazione.
        val c = local.bounds.center
        val rad = toRadians(s.rotation)
        val cs = cos(rad)
        val sn = sin(rad)
        fun place(p: Vec2): Vec2 {
            val q = p - c
            return s.center + Vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs)
        }
        return Layout(
            local.steps.map { Step(it.polygon.map(::place), it.top, it.landing) },
            local.pieces.map { pc -> pc.map(::place) },
            local.path.map(::place),
            n, r,
        )
    }

    /**
     * Spostamento di una scala trascinata: se un lato del suo ingombro arriva vicino alla faccia interna o
     * esterna di un muro dritto (orizzontale o verticale), ci si accosta.
     */
    fun snapped(plan: FloorPlan, stair: Stair, rise: Double): Stair {
        val b = layout(stair, rise).bounds
        val half = Room.WALL_THICKNESS / 2
        var bestDx: Double? = null
        var bestDy: Double? = null
        fun better(cur: Double?, v: Double) = if (abs(v) < SNAP && (cur == null || abs(v) < abs(cur))) v else cur
        for (room in plan.rooms) for (i in 0 until room.wallCount) {
            val a = room.wallStart(i)
            val e = room.wallEnd(i)
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
        return stair.copy(center = stair.center + Vec2(bestDx ?: 0.0, bestDy ?: 0.0))
    }

    /**
     * Tratti della ringhiera attorno al vano scala al piano di sopra: il contorno del vuoto, tranne
     * dove tocca un muro (`walls`, i muri del piano di sopra) e tranne l'arrivo, da cui si esce dalla scala.
     */
    fun wellRailingRuns(s: Stair, rise: Double, walls: List<Pair<Vec2, Vec2>>): List<Pair<Vec2, Vec2>> {
        val l = layout(s, rise)
        // Il contorno è quello del vuoto vero (se il pavimento copre i primi gradini, il vuoto è più corto).
        val pieces = well(s, rise, l)
        val last = l.steps.last()
        val segs = l.path.zipWithNext()
        val endDir = (segs.last().second - segs.last().first).normalized()
        fun edgesOf(poly: List<Vec2>) = poly.indices.map { poly[it] to poly[(it + 1) % poly.size] }
        // Arrivo: il bordo dell'ultimo gradino più avanti nel verso della salita; nella chiocciola tutto
        // l'ultimo spicchio (si esce di lato, verso l'esterno).
        val spiral = s.kind == StairKind.Spiral
        val arrival = if (spiral) edgesOf(last.polygon) else listOfNotNull(edgesOf(last.polygon).maxByOrNull { (a, b) -> ((a + b) / 2.0) dot endDir })
        val half = Room.WALL_THICKNESS / 2
        val runs = mutableListOf<Pair<Vec2, Vec2>>()
        for ((pi, piece) in pieces.withIndex()) for ((a, b) in edgesOf(piece)) {
            val len = a.distanceTo(b)
            val n = (len / 10.0).toInt().coerceAtLeast(1)
            var runStart: Vec2? = null
            for (k in 0 until n) {
                val p0 = a + (b - a) * (k.toDouble() / n)
                val p1 = a + (b - a) * ((k + 1).toDouble() / n)
                val mid = (p0 + p1) / 2.0
                // Tratto interno al vuoto (confine tra due pezzi dell'ingombro): niente ringhiera.
                val inner = pieces.withIndex().any { (qi, q) ->
                    qi != pi && (Polygon.contains(q, mid) || edgesOf(q).any { (c, d) -> Polygon.distanceToSegment(mid, c, d) < 0.5 })
                }
                val nearWall = walls.any { (c, d) -> Polygon.distanceToSegment(mid, c, d) < half + 4 }
                val atArrival = arrival.any { (c, d) -> Polygon.distanceToSegment(mid, c, d) < if (spiral) 3.0 else 2.0 }
                val free = !inner && !nearWall && !atArrival
                if (free && runStart == null) runStart = p0
                if (!free && runStart != null) { runs += runStart to p0; runStart = null }
                if (free && k == n - 1) { runs += runStart!! to p1; runStart = null }
            }
        }
        return runs
    }

    /** Altezza libera minima consigliata sotto il solaio sopra una scala (cm). */
    const val MIN_HEADROOM = 200.0

    /** Gradini che si possono coprire con il pavimento di sopra: tutti tranne gli ultimi due (per arrivare). */
    fun maxCovered(l: Layout): Int = (l.steps.size - 2).coerceAtLeast(0)

    /** Gradini coperti davvero (la scelta, entro i limiti della scala). */
    fun coveredOf(s: Stair, l: Layout): Int = s.coveredSteps.coerceIn(0, maxCovered(l))

    /**
     * Vuoto nel solaio del piano di sopra: tutto l'ingombro della scala oppure, se il pavimento copre i primi
     * gradini, solo i gradini scoperti (poligoni convessi, come i pezzi dell'ingombro).
     */
    fun well(s: Stair, rise: Double, l: Layout = layout(s, rise)): List<List<Vec2>> {
        val k = coveredOf(s, l)
        return if (k == 0) l.pieces else l.steps.drop(k).map { it.polygon }
    }

    /**
     * Altezza libera sopra l'ultimo gradino coperto: dal gradino al solaio di sopra (spessore `slab`).
     * Null se non c'è niente di coperto.
     */
    fun headroom(s: Stair, rise: Double, slab: Double): Double? {
        val l = layout(s, rise)
        val k = coveredOf(s, l)
        if (k == 0) return null
        return rise - slab - l.steps[k - 1].top
    }

    /** Ingombro di tutte le scale di una pianta (per inquadrarla). */
    fun footprint(plan: FloorPlan, rise: Double): List<Vec2> = plan.stairs.flatMap { layout(it, rise).pieces.flatten() }
}
