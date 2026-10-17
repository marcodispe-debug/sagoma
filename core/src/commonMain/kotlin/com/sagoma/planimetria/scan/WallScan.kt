package com.sagoma.planimetria.scan

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Punto o vettore in pianta nel mondo AR, in **metri**: `x` e `z` di ARCore (la quota `y` non c'è). Non è la pianta di Sagoma
 * (centimetri): la conversione è [ScanCoordinates.toPlan]. Tipo a parte per non confondere metri e centimetri.
 */
data class ArXZ(val x: Double, val z: Double) {
    operator fun plus(o: ArXZ) = ArXZ(x + o.x, z + o.z)
    operator fun minus(o: ArXZ) = ArXZ(x - o.x, z - o.z)
    operator fun times(k: Double) = ArXZ(x * k, z * k)
    infix fun dot(o: ArXZ) = x * o.x + z * o.z
    fun cross(o: ArXZ) = x * o.z - z * o.x
    val length: Double get() = sqrt(x * x + z * z)
    fun distanceTo(o: ArXZ) = (this - o).length
    fun normalized(): ArXZ = length.let { if (it < 1e-12) this else ArXZ(x / it, z / it) }
}

/**
 * Un piano verticale visto da ARCore, ridotto a numeri: l'impronta orizzontale del pezzo rilevato è il segmento `a`→`b` (metri,
 * nel mondo AR) e la parte rilevata va da `bottomY` a `topY` (quote AR). `key` identifica il piano di ARCore tra un
 * aggiornamento e l'altro: lo stesso piano che cresce ha sempre la stessa chiave. Nessun tipo di ARCore.
 */
data class WallObservation(val key: Int, val a: ArXZ, val b: ArXZ, val bottomY: Double, val topY: Double) {
    val length: Double get() = a.distanceTo(b)
}

/**
 * Parete ricostruita: l'insieme dei piani verticali che stanno sulla stessa retta. `a`→`b` è l'estensione rilevata (metri, mondo
 * AR, in pianta; non è detto che arrivi agli angoli: dietro un mobile la parete si ferma prima), da `bottomY` a `topY` la parte
 * in altezza rilevata (quote AR). `firstSeen` è l'ordine di comparsa (la più recente ha il valore più alto).
 */
data class DetectedWall(
    val a: ArXZ,
    val b: ArXZ,
    val bottomY: Double,
    val topY: Double,
    val keys: List<Int>,
    val firstSeen: Int,
) {
    val length: Double get() = a.distanceTo(b)
    val mid: ArXZ get() = (a + b) * 0.5
    val direction: ArXZ get() = (b - a).normalized()

    /** Direzione della parete nella pianta di Sagoma, in gradi da 0 (incluso) a 180 (escluso): non ha verso. */
    val headingDeg: Double
        get() {
            val d = direction
            val deg = toDegrees(kotlin.math.atan2(d.z, d.x))
            val h = ((deg % 180.0) + 180.0) % 180.0
            return if (h >= 180.0) 0.0 else h
        }

    /** Distanza (m) dal pavimento al punto più basso rilevato della parete. */
    fun bottomAboveFloor(floorY: Double): Double = bottomY - floorY

    /** Distanza (m) dal pavimento al punto più alto rilevato. */
    fun topAboveFloor(floorY: Double): Double = topY - floorY

    /** La parete nel sistema di Sagoma (centimetri). Le quote dal pavimento solo se si conosce la quota del pavimento. */
    fun toScanned(floorY: Double?): ScannedWall {
        val pa = ScanCoordinates.toPlan(ArPoint(a.x, 0.0, a.z))
        val pb = ScanCoordinates.toPlan(ArPoint(b.x, 0.0, b.z))
        return ScannedWall(
            a = pa,
            b = pb,
            lengthCm = pa.distanceTo(pb),
            headingDeg = headingDeg,
            bottomCm = floorY?.let { bottomAboveFloor(it) * ScanCoordinates.CM_PER_M },
            topCm = floorY?.let { topAboveFloor(it) * ScanCoordinates.CM_PER_M },
        )
    }
}

/**
 * Perimetro ricostruito dalle pareti. `closed`: gli angoli (metri, mondo AR) se le pareti formano una stanza chiusa, in ordine.
 * `chains`: le serie di pareti collegate che non chiudono (dal pezzo con più angoli): ogni serie è la spezzata dagli estremi
 * della prima parete, passando per gli angoli, fino all'estremo dell'ultima; una parete isolata è una serie di due punti.
 */
data class WallLayout(val closed: List<ArXZ>?, val chains: List<List<ArXZ>>) {
    /** La serie più lunga con almeno tre punti: da chiudere "a mano" con un lato dritto quando manca una parete. */
    val closableChain: List<ArXZ>? get() = chains.firstOrNull { it.size >= 3 }
}

/**
 * Le pareti viste finora. Immutabile: [update] dà lo stato nuovo, così si prova senza telefono.
 *
 * ARCore aggiorna più volte lo stesso piano e a volte ne crea di nuovi sulla stessa parete: ogni piano è ricordato per `key`
 * (l'ultimo aggiornamento vince) e i piani che stanno sulla stessa retta (direzione quasi uguale, distanza dalla retta piccola,
 * estensioni sovrapposte o vicine) formano una sola [DetectedWall]. Un piano ricordato resta anche se ARCore smette di
 * aggiornarlo (parete uscita dall'inquadratura, piano assorbito da un altro).
 */
class WallScan private constructor(
    private val tracked: Map<Int, Tracked>,
    private val blocked: Set<Int>,
    private val nextSeq: Int,
) {
    /** Nessuna parete vista. */
    constructor() : this(emptyMap(), emptySet(), 0)

    private class Tracked(val observation: WallObservation, val seq: Int)

    /** Aggiunge gli aggiornamenti: i piani già visti si sostituiscono, quelli nuovi si aggiungono (salvo quelli scartati con [removeNewest]). */
    fun update(observations: List<WallObservation>): WallScan {
        var seq = nextSeq
        val map = tracked.toMutableMap()
        for (o in observations.sortedBy { it.key }) {
            if (o.key in blocked || o.length < 1e-6) continue
            val old = map[o.key]
            map[o.key] = Tracked(o, old?.seq ?: seq++)
        }
        return WallScan(map, blocked, seq)
    }

    /** Le pareti, dalla più vecchia alla più recente; i pezzi troppo corti per essere una parete non contano. */
    fun walls(): List<DetectedWall> =
        WallClustering.cluster(tracked.values.sortedBy { it.seq }.map { it.observation to it.seq }).filter { it.length >= MIN_WALL_LENGTH_M }

    /** Toglie l'ultima parete comparsa; i suoi piani non vengono più riaccolti anche se ARCore li aggiorna. */
    fun removeNewest(): WallScan {
        val newest = walls().maxByOrNull { it.firstSeen } ?: return this
        return WallScan(tracked - newest.keys.toSet(), blocked + newest.keys, nextSeq)
    }

    fun clear(): WallScan = WallScan(emptyMap(), blocked + tracked.keys, nextSeq)

    val isEmpty: Boolean get() = tracked.isEmpty()

    companion object {
        /** Sotto questa lunghezza (m) un pezzo di piano verticale non è una parete (un'anta, un quadro, un mobile). */
        const val MIN_WALL_LENGTH_M = 0.4
    }
}

/** Raggruppa le osservazioni sulla stessa retta in pareti. Deterministico: l'ordine di arrivo decide, a parità di condizioni. */
internal object WallClustering {
    /** Differenza massima di direzione (seno di 8°) per dire che due piani sono la stessa parete. */
    private const val SIN_ANGLE_TOL = 0.139
    /** Distanza massima (m) dei punti di un piano dalla retta della parete. */
    private const val OFFSET_TOL_M = 0.15
    /** Distanza massima (m) tra le estensioni di due piani sulla retta: oltre, sono pareti diverse (o separate da una porta). */
    private const val GAP_TOL_M = 0.6

    private class Cluster(val members: MutableList<WallObservation>, val firstSeen: Int) {
        var p = ArXZ(0.0, 0.0)
        var d = ArXZ(1.0, 0.0)
        var s0 = 0.0
        var s1 = 0.0
        var bottomY = 0.0
        var topY = 0.0

        init { refit() }

        fun refit() {
            var total = 0.0
            var dx = 0.0; var dz = 0.0; var mx = 0.0; var mz = 0.0
            for (o in members) {
                val u = canonical(o.a, o.b)
                val w = o.length
                total += w
                dx += u.x * w; dz += u.z * w
                mx += (o.a.x + o.b.x) / 2 * w; mz += (o.a.z + o.b.z) / 2 * w
            }
            d = ArXZ(dx, dz).normalized()
            p = ArXZ(mx / total, mz / total)
            var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
            var by = Double.MAX_VALUE; var ty = -Double.MAX_VALUE
            for (o in members) {
                for (e in listOf(o.a, o.b)) { val s = (e - p) dot d; lo = min(lo, s); hi = max(hi, s) }
                by = min(by, o.bottomY); ty = max(ty, o.topY)
            }
            s0 = lo; s1 = hi; bottomY = by; topY = ty
        }

        fun start() = p + d * s0
        fun end() = p + d * s1
    }

    /** Direzione unitaria da `a` a `b`, girata in modo che sia sempre la stessa per la stessa retta (la retta non ha verso). */
    fun canonical(a: ArXZ, b: ArXZ): ArXZ {
        val u = (b - a).normalized()
        return if (u.x > 1e-9 || (abs(u.x) <= 1e-9 && u.z > 0)) u else u * -1.0
    }

    private fun compatible(c: Cluster, a: ArXZ, b: ArXZ): Boolean {
        val u = canonical(a, b)
        if (abs(c.d.cross(u)) > SIN_ANGLE_TOL) return false
        if (max(abs((a - c.p).cross(c.d)), abs((b - c.p).cross(c.d))) > OFFSET_TOL_M) return false
        val t0 = (a - c.p) dot c.d
        val t1 = (b - c.p) dot c.d
        val gap = max(0.0, max(c.s0, min(t0, t1)) - min(c.s1, max(t0, t1)))
        return gap <= GAP_TOL_M
    }

    fun cluster(observations: List<Pair<WallObservation, Int>>): List<DetectedWall> {
        val clusters = mutableListOf<Cluster>()
        for ((o, seq) in observations) {
            val target = clusters.filter { compatible(it, o.a, o.b) }.minByOrNull { abs((o.a - it.p).cross(it.d)) + abs((o.b - it.p).cross(it.d)) }
            if (target != null) { target.members.add(o); target.refit() } else clusters.add(Cluster(mutableListOf(o), seq))
        }
        // Un gruppo cresce e può ora toccarne un altro: si uniscono finché possibile.
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in clusters.indices) for (j in i + 1 until clusters.size) {
                if (compatible(clusters[i], clusters[j].start(), clusters[j].end()) && compatible(clusters[j], clusters[i].start(), clusters[i].end())) {
                    val a = clusters[i]
                    val b = clusters[j]
                    val joined = Cluster((a.members + b.members).toMutableList(), min(a.firstSeen, b.firstSeen))
                    clusters.removeAt(j); clusters[i] = joined
                    merged = true
                    break@loop
                }
            }
        }
        return clusters.map {
            DetectedWall(it.start(), it.end(), it.bottomY, it.topY, it.members.map { m -> m.key }.sorted(), it.firstSeen)
        }.sortedBy { it.firstSeen }
    }
}

/** Dalle pareti al perimetro: le pareti si prolungano fino a incontrarsi, senza supporre angoli retti. */
object WallOutline {
    /** Due pareti con meno di 20° tra loro sono parallele: non formano un angolo. */
    private const val SIN_PARALLEL = 0.342
    /** Un angolo può stare a questa distanza (m) dall'estremo rilevato di una parete: dietro un mobile la parete si ferma prima. */
    const val REACH_M = 1.0

    private class Candidate(val cost: Double, val i: Int, val j: Int, val endI: Int, val endJ: Int, val point: ArXZ)
    private class Link(val other: Int, val point: ArXZ)

    fun layout(walls: List<DetectedWall>): WallLayout {
        val n = walls.size
        if (n == 0) return WallLayout(null, emptyList())
        fun endpoint(w: Int, e: Int) = if (e == 0) walls[w].a else walls[w].b

        // Angoli possibili tra coppie di pareti: l'incontro delle due rette, vicino a un estremo di entrambe.
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until n) for (j in i + 1 until n) {
            val di = walls[i].direction
            val dj = walls[j].direction
            val denom = di.cross(dj)
            if (abs(denom) < SIN_PARALLEL) continue
            val t = (walls[j].a - walls[i].a).cross(dj) / denom
            val p = walls[i].a + di * t
            val s = (p - walls[j].a) dot dj
            val li = walls[i].length
            val lj = walls[j].length
            val di0 = abs(t)
            val di1 = abs(t - li)
            val dj0 = abs(s)
            val dj1 = abs(s - lj)
            val ci = min(di0, di1)
            val cj = min(dj0, dj1)
            if (ci > REACH_M || cj > REACH_M) continue
            candidates.add(Candidate(ci + cj, i, j, if (di0 <= di1) 0 else 1, if (dj0 <= dj1) 0 else 1, p))
        }
        candidates.sortWith(compareBy({ it.cost }, { it.i }, { it.j }))
        // Ogni estremo di parete forma al massimo un angolo: prima i più sicuri.
        val links = arrayOfNulls<Link>(2 * n)
        for (c in candidates) {
            val ei = 2 * c.i + c.endI
            val ej = 2 * c.j + c.endJ
            if (links[ei] != null || links[ej] != null) continue
            links[ei] = Link(ej, c.point)
            links[ej] = Link(ei, c.point)
        }

        val visited = BooleanArray(n)
        val chains = mutableListOf<List<ArXZ>>()
        // Serie aperte: si parte da una parete con un estremo senza angolo.
        for (w in 0 until n) {
            if (visited[w]) continue
            val freeEnd = (0..1).firstOrNull { links[2 * w + it] == null } ?: continue
            visited[w] = true
            val points = mutableListOf(endpoint(w, freeEnd))
            var cur = w
            var exit = freeEnd xor 1
            var guard = 0
            while (guard++ <= n) {
                val link = links[2 * cur + exit]
                if (link == null) { points.add(endpoint(cur, exit)); break }
                points.add(link.point)
                cur = link.other / 2
                exit = (link.other % 2) xor 1
                visited[cur] = true
            }
            chains.add(points)
        }
        // Cicli: le pareti rimaste hanno tutti e due gli estremi collegati.
        val cycles = mutableListOf<List<ArXZ>>()
        for (w in 0 until n) {
            if (visited[w]) continue
            val corners = mutableListOf<ArXZ>()
            var cur = w
            var exit = 1
            var guard = 0
            var ok = true
            while (guard++ <= n) {
                visited[cur] = true
                val link = links[2 * cur + exit]
                if (link == null) { ok = false; break }
                corners.add(link.point)
                cur = link.other / 2
                exit = (link.other % 2) xor 1
                if (cur == w) break
            }
            if (ok && cur == w && corners.size >= 3) cycles.add(corners)
        }
        val closed = cycles.maxByOrNull { Polygon.area(it.map { p -> Vec2(p.x, p.z) }) }
        val ordered = chains.sortedWith(compareByDescending<List<ArXZ>> { it.size }.thenByDescending { c -> c.zipWithNext().sumOf { (p, q) -> p.distanceTo(q) } })
        return WallLayout(closed, ordered)
    }

    /** La bozza di perimetro per la scansione: punti sul pavimento (quota `floorY`) di un poligono chiuso. */
    fun toDraft(corners: List<ArXZ>, floorY: Double): ScanDraft = ScanDraft(corners.map { ArPoint(it.x, floorY, it.z) }, closed = true)
}
