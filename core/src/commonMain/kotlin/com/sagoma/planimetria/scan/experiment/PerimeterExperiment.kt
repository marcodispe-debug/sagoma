package com.sagoma.planimetria.scan.experiment

import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.scan.ArXZ
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/*
 * ESPERIMENTO M3.1 (offline): dalle linee candidate di parete al perimetro di stanza. Non è collegato all'app né al rilevamento
 * attuale. Usa SOLO dati geometrici e di evidenza (posizione, estremi, orientamento, lunghezza, durata, frame, supporto dei punti,
 * RMS, altezza, relazione con le altre candidate e con la traiettoria della camera): nessuna etichetta descrittiva.
 *
 * Tutto nel mondo ARCore, in pianta (x, z) e in metri. Deterministico e indipendente dall'ordine delle candidate (e dai loro id).
 */

/** Una linea candidata di parete (segmento in pianta) con l'evidenza che la sostiene. Nessun testo descrittivo: solo numeri. */
@Serializable
data class WallCandidate(
    val id: Int,
    val ax: Double, val az: Double, val bx: Double, val bz: Double,
    /** Primo e ultimo istante (ms dall'inizio) in cui la candidata è stata osservata. */
    val firstMs: Long = 0,
    val lastMs: Long = 0,
    val observedFrames: Int = 0,
    /** Quota dei frame osservati con tracking attivo (0..1). */
    val trackingFraction: Double = 1.0,
    val minY: Double = 0.0,
    val maxY: Double = 0.0,
    /** Punti della nuvola entro la tolleranza dal segmento (0 se non disponibili). */
    val pointSupport: Int = 0,
    /** Scarto quadratico medio (m) dei punti di supporto dalla retta; null se non calcolabile. */
    val rmsM: Double? = null,
    /** Il piano di ARCore da cui deriva è stato assorbito da un altro. */
    val subsumed: Boolean = false,
    val planeKeys: List<Int> = emptyList(),
) {
    val a: ArXZ get() = ArXZ(ax, az)
    val b: ArXZ get() = ArXZ(bx, bz)
    val length: Double get() = a.distanceTo(b)
    val heightM: Double get() = maxY - minY
    val persistenceMs: Long get() = lastMs - firstMs
}

/** Parametri dell'esperimento. Ogni valore ha il suo motivo; la sensibilità si misura con [PerimeterExperiment.sweep]. */
data class PerimeterParams(
    /**
     * Differenza di direzione massima per fondere due candidate. 6°: la normale di un piano ARCore è tipicamente corretta entro
     * pochi gradi; su 4 m, 6° spostano un estremo di ~42 cm, ma la distanza dalla retta (sotto) impedisce di fondere cose distinte.
     */
    val angleTolDeg: Double = 6.0,
    /**
     * Distanza massima dalla retta se le due candidate sono state viste NELLO STESSO MOMENTO: due piani distinti contemporanei a più
     * di 8 cm sono superfici diverse (la deriva non c'entra, perché è la stessa sessione e lo stesso istante).
     */
    val offsetTolSimultaneousM: Double = 0.08,
    /**
     * Distanza massima se i periodi di osservazione non si sovrappongono: lì può esserci deriva del tracciamento tra le due visite
     * (ordine di qualche cm su una sessione di una o due decine di secondi; si lascia il doppio).
     */
    val offsetTolDriftM: Double = 0.15,
    /** Vuoto massimo lungo la retta tra due candidate della stessa parete: copre una porta stretta o un mobile; oltre, sono pareti diverse. */
    val gapTolM: Double = 0.80,
    /** Sotto questi valori un pezzo non è una parete (cornici, ante, quadri). */
    val minCandidateLengthM: Double = 0.30,
    val minCandidateHeightM: Double = 0.20,
    /** Una parete fusa più corta di così, o con evidenza sotto la soglia, non entra nella ricerca del perimetro. */
    val minWallLengthM: Double = 0.80,
    val minWallScore: Double = 0.20,
    /** Angoli accettati tra due pareti consecutive (interni da 25° a 155° tra le rette): sotto, sono quasi parallele. */
    val minCornerAngleDeg: Double = 25.0,
    /** Quanto una parete può essere prolungata (m) per raggiungere l'angolo: dietro un mobile d'angolo, fino a circa un metro. */
    val reachM: Double = 1.0,
    /** Quanto una parete può oltrepassare l'angolo (m): rumore di misura. */
    val overshootM: Double = 0.30,
    val minAreaM2: Double = 1.0,
    val maxCycleWalls: Int = 14,
    val searchBudget: Int = 400_000,
    /** Confidenza minima dei punti della nuvola usati come evidenza. */
    val minPointConfidence: Double = 0.5,
    /** Soglie per dire che l'evidenza SOSTIENE il perimetro (non che sia corretto). */
    val minSupportedFraction: Double = 0.60,
    val minEdgeCoverage: Double = 0.30,
    val minCameraInside: Double = 0.95,
    val maxPointsOutside: Double = 0.15,
    val maxExtensionShare: Double = 0.40,
    /** Ambiguità: seconda soluzione con punteggio ≥ questa quota della migliore e insieme di pareti diverso. */
    val ambiguityRatio: Double = 0.90,
)

/** Tutto ciò che serve all'esperimento. `trajectory` e `points` sono opzionali (senza, quei controlli sono saltati e dichiarati). */
data class ExperimentInput(
    val candidates: List<WallCandidate>,
    val trajectory: List<ArXZ> = emptyList(),
    val points: List<ArXZ> = emptyList(),
)

enum class CandidateStatus(val label: String) {
    EXCLUDED_SANITY("esclusa: troppo corta o bassa"),
    EXCLUDED_WEAK("esclusa: parete fusa con evidenza debole o corta"),
    NOT_IN_PERIMETER("valida ma non nel perimetro scelto"),
    IN_PERIMETER_SINGLE("nel perimetro (da sola)"),
    IN_PERIMETER_FUSED("nel perimetro (fusa con altre)"),
    NO_PERIMETER("valida; nessun perimetro trovato"),
}

/** Parete ottenuta fondendo una o più candidate. */
data class FusedWall(
    val index: Int,
    val memberIds: List<Int>,
    val a: ArXZ,
    val b: ArXZ,
    val bottomY: Double,
    val topY: Double,
    val firstMs: Long,
    val lastMs: Long,
    /** Quota (0..1) della lunghezza della parete coperta da almeno una candidata (le altre parti sono vuoti). */
    val coverage: Double,
    val persistenceMs: Long,
    val pointDensityPerM: Double,
    val rmsM: Double?,
    val trackingFraction: Double,
    val score: Double,
    val scoreParts: List<Pair<String, Double>>,
    val eligible: Boolean,
    val ineligibleReason: String?,
) {
    val length: Double get() = a.distanceTo(b)
    val direction: ArXZ get() = (b - a).normalized()
    val headingDeg: Double
        get() {
            val d = direction
            return (((toDegrees(atan2(d.z, d.x)) % 180.0) + 180.0) % 180.0).let { if (it >= 180.0) 0.0 else it }
        }
}

data class Corner(val i: Int, val j: Int, val point: ArXZ, val angleDeg: Double, val extI: Double, val extJ: Double, val endI: Int, val endJ: Int)

data class EdgeInfo(val wallIndex: Int, val from: ArXZ, val to: ArXZ, val coverage: Double, val wallScore: Double) {
    val length: Double get() = from.distanceTo(to)
}

data class PerimeterSolution(
    val wallIndices: List<Int>,
    val corners: List<ArXZ>,
    val edges: List<EdgeInfo>,
    /** Angoli interni (gradi) a ogni angolo, nell'ordine dei vertici. */
    val interiorAnglesDeg: List<Double>,
    val extensions: List<Double>,
    val areaM2: Double,
    val perimeterM: Double,
    /** Quanta lunghezza si è dovuta AGGIUNGERE per far incontrare le pareti agli angoli (somma dei prolungamenti positivi). */
    val closureErrorM: Double,
    val maxExtensionM: Double,
    val supportedFraction: Double,
    val minEdgeCoverage: Double,
    val cameraInsideFraction: Double?,
    val pointsOutsideShare: Double?,
    val score: Double,
)

enum class Verdict(val label: String) {
    NO_PERIMETER("nessun perimetro chiuso trovato: evidenza insufficiente"),
    GEOMETRIC_ONLY("soluzione geometricamente coerente, ma NON sufficientemente sostenuta dall'evidenza"),
    SUPPORTED("soluzione geometricamente coerente E sostenuta dall'evidenza disponibile (non è una prova di correttezza)"),
}

data class ExperimentResult(
    val params: PerimeterParams,
    val candidates: List<WallCandidate>,
    val status: List<Pair<Int, CandidateStatus>>,
    val sanityExcluded: List<Int>,
    val walls: List<FusedWall>,
    val corners: List<Corner>,
    val best: PerimeterSolution?,
    val runnersUp: List<PerimeterSolution>,
    val cyclesFound: Int,
    val searchTruncated: Boolean,
    val verdict: Verdict,
    val reasons: List<String>,
    val ambiguities: List<String>,
    val notes: List<String>,
) {
    /** Firma arrotondata al centimetro: due risultati con la stessa firma sono la stessa soluzione. */
    fun signature(): String {
        val sol = best ?: return "nessun-perimetro|${walls.size}"
        fun r(v: Double) = round(v * 100.0) / 100.0
        val ws = walls.filter { it.index in sol.wallIndices }.map { "${r(it.a.x)},${r(it.a.z)}-${r(it.b.x)},${r(it.b.z)}" }.sorted()
        return "walls=${walls.size}|cycle=${sol.wallIndices.size}|" + ws.joinToString(";")
    }
}

object PerimeterExperiment {
    // ---------------------------------------------------------------- chiavi canoniche (indipendenti dall'ordine di arrivo)
    private fun r6(v: Double) = round(v * 1e6)

    private class Key(val v: List<Double>) : Comparable<Key> {
        override fun compareTo(other: Key): Int {
            for (i in 0 until min(v.size, other.v.size)) { val c = v[i].compareTo(other.v[i]); if (c != 0) return c }
            return v.size.compareTo(other.v.size)
        }
    }

    /** Estremi in ordine canonico (a < b) così la direzione non dipende da come è stata scritta la candidata. */
    private fun canon(c: WallCandidate): WallCandidate {
        val swap = c.ax > c.bx || (c.ax == c.bx && c.az > c.bz)
        return if (swap) c.copy(ax = c.bx, az = c.bz, bx = c.ax, bz = c.az) else c
    }

    private fun candKey(c: WallCandidate) = Key(listOf(r6(c.ax), r6(c.az), r6(c.bx), r6(c.bz), c.firstMs.toDouble(), c.lastMs.toDouble(), c.observedFrames.toDouble(), c.pointSupport.toDouble()))

    private fun weight(c: WallCandidate): Double =
        c.length * (0.3 + 0.7 * min(1.0, c.persistenceMs / 10_000.0)) * (if (c.subsumed) 0.5 else 1.0)

    // ---------------------------------------------------------------- fusione
    private class Cluster(members: List<WallCandidate>) {
        val members: List<WallCandidate> = members.sortedBy { candKey(it) }
        val p: ArXZ
        val d: ArXZ
        val s0: Double
        val s1: Double
        val firstMs = this.members.minOf { it.firstMs }
        val lastMs = this.members.maxOf { it.lastMs }
        val weight = this.members.sumOf { weight(it) }
        val key: Key

        init {
            var total = 0.0; var mx = 0.0; var mz = 0.0
            for (m in this.members) { val w = weight(m); total += w; mx += w * (m.ax + m.bx) / 2; mz += w * (m.az + m.bz) / 2 }
            p = if (total > 0) ArXZ(mx / total, mz / total) else ArXZ(this.members.first().ax, this.members.first().az)
            var sxx = 0.0; var szz = 0.0; var sxz = 0.0
            for (m in this.members) for (e in listOf(m.a, m.b)) {
                val w = weight(m) / 2; val dx = e.x - p.x; val dz = e.z - p.z
                sxx += w * dx * dx; szz += w * dz * dz; sxz += w * dx * dz
            }
            val ang = 0.5 * atan2(2 * sxz, sxx - szz)
            var dir = ArXZ(cos(ang), sin(ang))
            if (dir.x < -1e-12 || (abs(dir.x) <= 1e-12 && dir.z < 0)) dir = dir * -1.0
            d = dir
            var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
            for (m in this.members) for (e in listOf(m.a, m.b)) { val s = (e - p) dot d; lo = min(lo, s); hi = max(hi, s) }
            s0 = lo; s1 = hi
            key = Key(listOf(r6(d.x), r6(d.z), r6((p - ArXZ(0.0, 0.0)).cross(d)), r6(s0 + (p dot d)), r6(s1 + (p dot d))))
        }

        fun start() = p + d * s0
        fun end() = p + d * s1
        val len get() = s1 - s0
    }

    private class Merge(val i: Int, val j: Int, val cost: Double)

    private fun angleBetween(d1: ArXZ, d2: ArXZ): Double = toDegrees(asin(min(1.0, abs(d1.cross(d2)))))

    /** Costo di fusione tra due gruppi, o null se non sono compatibili. */
    private fun mergeCost(x: Cluster, y: Cluster, p: PerimeterParams): Double? {
        // Si misura il più corto rispetto alla retta del più lungo: i difetti d'angolo non si amplificano sull'estrapolazione.
        val (big, small) = if (x.len > y.len || (x.len == y.len && x.key <= y.key)) x to y else y to x
        val angle = angleBetween(big.d, small.d)
        if (angle > p.angleTolDeg) return null
        val sa = small.start(); val sb = small.end()
        val offset = max(abs((sa - big.p).cross(big.d)), abs((sb - big.p).cross(big.d)))
        val t0 = (sa - big.p) dot big.d
        val t1 = (sb - big.p) dot big.d
        val gap = max(0.0, max(big.s0, min(t0, t1)) - min(big.s1, max(t0, t1)))
        if (gap > p.gapTolM) return null
        val simultaneous = x.firstMs <= y.lastMs && y.firstMs <= x.lastMs
        val offsetTol = if (simultaneous) p.offsetTolSimultaneousM else p.offsetTolDriftM
        if (offset > offsetTol) return null
        var cost = angle / p.angleTolDeg + offset / offsetTol + gap / p.gapTolM
        // Viste insieme a distanza non nulla: più probabile che siano due strutture, si preferisce fondere prima le altre coppie.
        if (simultaneous && offset > 0.03) cost += 1.0
        return cost
    }

    private fun cluster(cands: List<WallCandidate>, p: PerimeterParams): List<Cluster> {
        val cl = cands.sortedBy { candKey(it) }.map { Cluster(listOf(it)) }.toMutableList()
        while (true) {
            var best: Merge? = null
            var bestKey: Key? = null
            for (i in cl.indices) for (j in i + 1 until cl.size) {
                val c = mergeCost(cl[i], cl[j], p) ?: continue
                val k = if (cl[i].key <= cl[j].key) Key(cl[i].key.v + cl[j].key.v) else Key(cl[j].key.v + cl[i].key.v)
                val b = best
                if (b == null || c < b.cost - 1e-12 || (abs(c - b.cost) <= 1e-12 && k < bestKey!!)) { best = Merge(i, j, c); bestKey = k }
            }
            val m = best ?: break
            val joined = Cluster(cl[m.i].members + cl[m.j].members)
            cl.removeAt(m.j)
            cl[m.i] = joined
        }
        return cl.sortedBy { it.key }
    }

    // ---------------------------------------------------------------- evidenza di una parete fusa
    private fun union(intervals: List<Pair<Double, Double>>): Double {
        if (intervals.isEmpty()) return 0.0
        val s = intervals.sortedBy { it.first }
        var total = 0.0
        var lo = s[0].first; var hi = s[0].second
        for ((a, b) in s.drop(1)) { if (a > hi) { total += hi - lo; lo = a; hi = b } else hi = max(hi, b) }
        return total + (hi - lo)
    }

    private fun memberIntervals(c: Cluster): List<Pair<Double, Double>> = c.members.map { m ->
        val t0 = (m.a - c.p) dot c.d; val t1 = (m.b - c.p) dot c.d
        min(t0, t1) to max(t0, t1)
    }

    private fun wall(index: Int, c: Cluster, p: PerimeterParams, pointsAvailable: Boolean): FusedWall {
        val len = c.len
        val coverage = if (len > 1e-9) min(1.0, union(memberIntervals(c)) / len) else 0.0
        val persistence = c.members.maxOf { it.persistenceMs }
        val support = c.members.sumOf { it.pointSupport }
        val density = if (len > 1e-9) support / len else 0.0
        val rmsList = c.members.filter { it.rmsM != null && it.pointSupport > 0 }
        val rms = if (rmsList.isEmpty()) null else rmsList.sumOf { it.rmsM!! * it.pointSupport } / rmsList.sumOf { it.pointSupport }
        val tracking = c.members.sumOf { it.trackingFraction * it.observedFrames.coerceAtLeast(1) } / c.members.sumOf { it.observedFrames.coerceAtLeast(1) }
        val bottom = c.members.minOf { it.minY }; val top = c.members.maxOf { it.maxY }
        // Punteggio: media pesata delle componenti disponibili (le assenti non contano né a favore né contro).
        val parts = mutableListOf<Triple<String, Double, Double>>() // nome, valore 0..1, peso
        parts += Triple("copertura", coverage, 0.30)
        parts += Triple("persistenza", min(1.0, persistence / 10_000.0), 0.25)
        if (pointsAvailable) parts += Triple("punti", min(1.0, density / 10.0), 0.20)
        parts += Triple("altezza", min(1.0, (top - bottom) / 1.5), 0.10)
        if (rms != null) parts += Triple("rms", 1.0 - min(1.0, rms / 0.10), 0.10)
        parts += Triple("tracking", tracking, 0.05)
        val score = parts.sumOf { it.second * it.third } / parts.sumOf { it.third }
        val reason = when {
            len < p.minWallLengthM -> "lunghezza ${fmt(len)} m < ${fmt(p.minWallLengthM)} m"
            score < p.minWallScore -> "evidenza ${fmt(score)} < ${fmt(p.minWallScore)}"
            else -> null
        }
        return FusedWall(
            index, c.members.map { it.id }.sorted(), c.start(), c.end(), bottom, top, c.firstMs, c.lastMs, coverage, persistence, density, rms, tracking,
            score, parts.map { it.first to it.second }, reason == null, reason,
        )
    }

    // ---------------------------------------------------------------- angoli
    private fun corner(i: Int, j: Int, wi: FusedWall, wj: FusedWall, p: PerimeterParams): Corner? {
        val di = wi.direction; val dj = wj.direction
        val denom = di.cross(dj)
        val angle = toDegrees(asin(min(1.0, abs(denom))))
        if (angle < p.minCornerAngleDeg) return null
        val t = (wj.a - wi.a).cross(dj) / denom
        val pt = wi.a + di * t
        val s = (pt - wj.a) dot dj
        val li = wi.length; val lj = wj.length
        val endI = if (abs(t) <= abs(t - li)) 0 else 1
        val endJ = if (abs(s) <= abs(s - lj)) 0 else 1
        // Prolungamento (positivo = si prolunga la parete oltre il suo estremo; negativo = la parete oltrepassa l'angolo).
        val extI = if (endI == 0) -t else t - li
        val extJ = if (endJ == 0) -s else s - lj
        if (extI > p.reachM || extJ > p.reachM || extI < -p.overshootM || extJ < -p.overshootM) return null
        return Corner(i, j, pt, angle, extI, extJ, endI, endJ)
    }

    private fun shoelace(poly: List<ArXZ>): Double {
        var s = 0.0
        for (k in poly.indices) { val a = poly[k]; val b = poly[(k + 1) % poly.size]; s += a.x * b.z - b.x * a.z }
        return s / 2
    }

    private fun segCross(a: ArXZ, b: ArXZ, c: ArXZ, d: ArXZ): Boolean {
        val d1 = (b - a).cross(c - a); val d2 = (b - a).cross(d - a); val d3 = (d - c).cross(a - c); val d4 = (d - c).cross(b - c)
        return d1 * d2 < 0 && d3 * d4 < 0
    }

    private fun simple(poly: List<ArXZ>): Boolean {
        val n = poly.size
        for (i in 0 until n) for (j in i + 1 until n) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue
            if (segCross(poly[i], poly[(i + 1) % n], poly[j], poly[(j + 1) % n])) return false
        }
        return true
    }

    private fun inside(poly: List<ArXZ>, p: ArXZ): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.z > p.z) != (b.z > p.z) && p.x < (b.x - a.x) * (p.z - a.z) / (b.z - a.z) + a.x) c = !c
            j = i
        }
        return c
    }

    private fun distToPoly(poly: List<ArXZ>, p: ArXZ): Double {
        var best = Double.MAX_VALUE
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val ab = b - a; val l2 = ab dot ab
            val t = if (l2 < 1e-12) 0.0 else (((p - a) dot ab) / l2).coerceIn(0.0, 1.0)
            best = min(best, p.distanceTo(a + ab * t))
        }
        return best
    }

    // ---------------------------------------------------------------- ricerca del perimetro
    private class Link(val j: Int, val endJ: Int, val corner: Corner)

    private fun evaluate(
        path: List<Int>, walls: List<FusedWall>, clusters: List<Cluster>, cornerOf: (Int, Int) -> Corner,
        input: ExperimentInput, p: PerimeterParams,
    ): PerimeterSolution? {
        val k = path.size
        // Vertice t = angolo tra la parete t e la t+1.
        val verts = List(k) { t -> cornerOf(path[t], path[(t + 1) % k]).point }
        if (!simple(verts)) return null
        val signed = shoelace(verts)
        val area = abs(signed)
        if (area < p.minAreaM2) return null
        val perim = List(k) { verts[it].distanceTo(verts[(it + 1) % k]) }.sum()
        // Angoli interni.
        val interior = List(k) { t ->
            val prev = verts[(t - 1 + k) % k]; val cur = verts[t]; val next = verts[(t + 1) % k]
            val u = cur - prev; val w = next - cur
            val turn = toDegrees(atan2(u.cross(w), u dot w))
            if (signed > 0) 180.0 - turn else 180.0 + turn
        }
        val exts = ArrayList<Double>()
        for (t in 0 until k) {
            val c = cornerOf(path[t], path[(t + 1) % k])
            exts.add(if (c.i == path[t]) c.extI else c.extJ)
            exts.add(if (c.i == path[(t + 1) % k]) c.extI else c.extJ)
        }
        // Lati: parete t va dal vertice t−1 al vertice t.
        val edges = List(k) { t ->
            val w = walls[path[t]]; val cl = clusters[path[t]]
            val from = verts[(t - 1 + k) % k]; val to = verts[t]
            val e0 = (from - cl.p) dot cl.d; val e1 = (to - cl.p) dot cl.d
            val lo = min(e0, e1); val hi = max(e0, e1)
            val covered = union(memberIntervals(cl).mapNotNull { (a, b) -> val x = max(a, lo); val y = min(b, hi); if (y > x) x to y else null })
            EdgeInfo(path[t], from, to, if (hi - lo > 1e-9) min(1.0, covered / (hi - lo)) else 0.0, w.score)
        }
        val totalLen = edges.sumOf { it.length }
        val supported = if (totalLen > 0) edges.sumOf { it.coverage * it.length } / totalLen else 0.0
        val meanScore = if (totalLen > 0) edges.sumOf { it.wallScore * it.length } / totalLen else 0.0
        val cam = if (input.trajectory.isEmpty()) null else input.trajectory.count { inside(verts, it) }.toDouble() / input.trajectory.size
        val outside = if (input.points.isEmpty()) null else input.points.count { !inside(verts, it) && distToPoly(verts, it) > 0.30 }.toDouble() / input.points.size
        val extAbs = exts.map { abs(it) }
        val cornerQuality = 1.0 - min(1.0, (extAbs.sum() / extAbs.size) / (2 * p.reachM))
        val score = meanScore * supported * cornerQuality * (cam ?: 1.0) * (1.0 - (outside ?: 0.0))
        return PerimeterSolution(
            path, verts, edges, interior, exts, area, perim, exts.filter { it > 0 }.sum(), extAbs.maxOrNull() ?: 0.0, supported,
            edges.minOf { it.coverage }, cam, outside, score,
        )
    }

    fun run(input: ExperimentInput, p: PerimeterParams = PerimeterParams()): ExperimentResult {
        val notes = mutableListOf<String>()
        val canonical = input.candidates.map { canon(it) }.sortedBy { candKey(it) }
        val usable = canonical.filter { it.length >= p.minCandidateLengthM && it.heightM >= p.minCandidateHeightM }
        val sanityExcluded = canonical.filter { it !in usable }.map { it.id }.sorted()
        val pointsAvailable = canonical.any { it.pointSupport > 0 }
        if (!pointsAvailable) notes += "Nessun supporto di punti nelle candidate: la componente 'punti' non entra nel punteggio."
        if (input.trajectory.isEmpty()) notes += "Traiettoria assente: non si verifica che la camera sia dentro il perimetro."
        if (input.points.isEmpty()) notes += "Nuvola di punti assente: non si verifica la quota di punti fuori dal perimetro."

        val clusters = cluster(usable, p)
        val walls = clusters.mapIndexed { i, c -> wall(i, c, p, pointsAvailable) }
        val eligible = walls.filter { it.eligible }.map { it.index }

        // Angoli validi tra le pareti idonee.
        val cornerMap = HashMap<Long, Corner>()
        fun ck(i: Int, j: Int) = min(i, j).toLong() * 100_000L + max(i, j)
        val links = HashMap<Int, MutableList<Link>>() // chiave: parete*2+estremo
        val corners = mutableListOf<Corner>()
        for (a in eligible.indices) for (b in a + 1 until eligible.size) {
            val i = eligible[a]; val j = eligible[b]
            val c = corner(i, j, walls[i], walls[j], p) ?: continue
            corners += c
            cornerMap[ck(i, j)] = c
            links.getOrPut(i * 2 + c.endI) { mutableListOf() }.add(Link(j, c.endJ, c))
            links.getOrPut(j * 2 + c.endJ) { mutableListOf() }.add(Link(i, c.endI, c))
        }
        for (l in links.values) l.sortBy { it.j }
        fun cornerOf(i: Int, j: Int) = cornerMap.getValue(ck(i, j))

        // Cicli semplici: ogni parete entra da un estremo e esce dall'altro; si parte dalla parete idonea di indice minore, uscendo dall'estremo 1.
        val solutions = mutableListOf<PerimeterSolution>()
        var budget = p.searchBudget
        var truncated = false
        var cyclesFound = 0
        fun dfs(start: Int, cur: Int, exitEnd: Int, path: MutableList<Int>, visited: HashSet<Int>) {
            if (budget-- <= 0) { truncated = true; return }
            for (l in links[cur * 2 + exitEnd].orEmpty()) {
                if (l.j == start) {
                    if (l.endJ == 0 && path.size >= 3) {
                        cyclesFound++
                        evaluate(path.toList(), walls, clusters, ::cornerOf, input, p)?.let { solutions += it }
                    }
                } else if (l.j > start && l.j !in visited && path.size < p.maxCycleWalls) {
                    path += l.j; visited += l.j
                    dfs(start, l.j, 1 - l.endJ, path, visited)
                    path.removeAt(path.size - 1); visited -= l.j
                }
                if (truncated) return
            }
        }
        for (s in eligible) {
            dfs(s, s, 1, mutableListOf(s), hashSetOf(s))
            if (truncated) break
        }
        val ranked = solutions.sortedWith(compareByDescending<PerimeterSolution> { it.score }.thenBy { it.wallIndices.sorted().joinToString(",") })
        val best = ranked.firstOrNull()
        // Soluzioni distinte per insieme di pareti.
        val distinct = ranked.distinctBy { it.wallIndices.sorted() }

        // Verdetto e ambiguità.
        val reasons = mutableListOf<String>()
        val ambiguities = mutableListOf<String>()
        var verdict: Verdict
        if (best == null) {
            verdict = Verdict.NO_PERIMETER
            reasons += if (eligible.size < 3) "Meno di tre pareti con evidenza sufficiente (${eligible.size})."
            else "Nessun ciclo chiuso di pareti con angoli validi (angolo ≥ ${fmt(p.minCornerAngleDeg)}°, prolungamento ≤ ${fmt(p.reachM)} m)."
        } else {
            val bad = mutableListOf<String>()
            if (best.supportedFraction < p.minSupportedFraction) bad += "solo ${pct(best.supportedFraction)} del perimetro è osservato (minimo ${pct(p.minSupportedFraction)})"
            if (best.minEdgeCoverage < p.minEdgeCoverage) bad += "un lato è osservato solo al ${pct(best.minEdgeCoverage)} (minimo ${pct(p.minEdgeCoverage)})"
            best.cameraInsideFraction?.let { if (it < p.minCameraInside) bad += "la camera sta dentro il perimetro solo per il ${pct(it)} del percorso (minimo ${pct(p.minCameraInside)})" }
            best.pointsOutsideShare?.let { if (it > p.maxPointsOutside) bad += "${pct(it)} dei punti sta fuori dal perimetro (massimo ${pct(p.maxPointsOutside)})" }
            val extShare = if (best.perimeterM > 0) best.closureErrorM / best.perimeterM else 0.0
            if (extShare > p.maxExtensionShare) bad += "il ${pct(extShare)} del perimetro è prolungamento delle pareti, non misura (massimo ${pct(p.maxExtensionShare)})"
            val weakWalls = best.wallIndices.filter { walls[it].score < 0.35 }
            if (weakWalls.isNotEmpty()) bad += "pareti con evidenza bassa (< 0,35): " + weakWalls.joinToString { "W${it + 1}" }
            verdict = if (bad.isEmpty()) Verdict.SUPPORTED else Verdict.GEOMETRIC_ONLY
            reasons += bad
            val second = distinct.getOrNull(1)
            if (second != null && best.score > 0 && second.score >= p.ambiguityRatio * best.score) {
                ambiguities += "Soluzione alternativa con punteggio ${fmt(second.score)} (migliore ${fmt(best.score)}) e pareti diverse: " +
                    "{" + second.wallIndices.sorted().joinToString { "W${it + 1}" } + "} contro {" + best.wallIndices.sorted().joinToString { "W${it + 1}" } + "}."
            }
            if (distinct.size > 1 && ambiguities.isEmpty()) ambiguities += "Altre ${distinct.size - 1} soluzioni chiuse, tutte con punteggio sotto il ${pct(p.ambiguityRatio)} della migliore."
        }
        if (truncated) ambiguities += "Ricerca interrotta dal limite di ${p.searchBudget} passi: potrebbero esistere altre soluzioni."

        // Stato di ogni candidata.
        val wallOf = HashMap<Int, FusedWall>()
        for (w in walls) for (id in w.memberIds) wallOf[id] = w
        val inCycle = best?.wallIndices?.toSet() ?: emptySet()
        val status = input.candidates.map { it.id }.sorted().map { id ->
            val w = wallOf[id]
            id to when {
                id in sanityExcluded -> CandidateStatus.EXCLUDED_SANITY
                w == null || !w.eligible -> CandidateStatus.EXCLUDED_WEAK
                best == null -> CandidateStatus.NO_PERIMETER
                w.index !in inCycle -> CandidateStatus.NOT_IN_PERIMETER
                w.memberIds.size > 1 -> CandidateStatus.IN_PERIMETER_FUSED
                else -> CandidateStatus.IN_PERIMETER_SINGLE
            }
        }
        return ExperimentResult(p, input.candidates.sortedBy { it.id }, status, sanityExcluded, walls, corners, best, distinct.drop(1).take(3), cyclesFound, truncated, verdict, reasons, ambiguities, notes)
    }

    // ---------------------------------------------------------------- sensibilità e ordine
    data class SweepRow(val label: String, val usable: Int, val walls: Int, val eligible: Int, val cycleWalls: Int?, val area: Double?, val verdict: Verdict)

    /** Come cambia il risultato variando le soglie di fusione (una per volta, e insieme). */
    fun sweep(input: ExperimentInput, base: PerimeterParams = PerimeterParams()): List<SweepRow> {
        val variants = mutableListOf<Pair<String, PerimeterParams>>()
        variants += "base" to base
        for (a in listOf(3.0, 10.0, 15.0)) variants += "angolo ${fmt(a)}°" to base.copy(angleTolDeg = a)
        for (f in listOf(0.5, 1.5, 2.0)) variants += "offset ×${fmt(f)}" to base.copy(offsetTolSimultaneousM = base.offsetTolSimultaneousM * f, offsetTolDriftM = base.offsetTolDriftM * f)
        for (g in listOf(0.4, 1.5, 3.0)) variants += "gap ${fmt(g)} m" to base.copy(gapTolM = g)
        for (r in listOf(0.5, 1.5)) variants += "reach ${fmt(r)} m" to base.copy(reachM = r)
        variants += "tutto stretto" to base.copy(angleTolDeg = 3.0, offsetTolSimultaneousM = 0.04, offsetTolDriftM = 0.08, gapTolM = 0.4)
        variants += "tutto largo" to base.copy(angleTolDeg = 10.0, offsetTolSimultaneousM = 0.16, offsetTolDriftM = 0.30, gapTolM = 1.5)
        return variants.map { (label, p) ->
            val r = run(input, p)
            SweepRow(label, input.candidates.size - r.sanityExcluded.size, r.walls.size, r.walls.count { it.eligible }, r.best?.wallIndices?.size, r.best?.areaM2, r.verdict)
        }
    }

    data class PermutationReport(val runs: Int, val identical: Int, val distinctSignatures: List<String>, val maxDeviationM: Double, val maxAreaDeviationM2: Double, val maxScoreDeviation: Double, val details: List<String>)

    /** Rifà girare lo stesso esperimento con `runs` ordini casuali (e id ridistribuiti a caso) e confronta con l'ordine originale. */
    fun permutations(input: ExperimentInput, p: PerimeterParams = PerimeterParams(), runs: Int = 20): PermutationReport {
        val ref = run(input, p)
        fun dev(a: ExperimentResult, b: ExperimentResult): Double {
            val ea = a.best; val eb = b.best
            if (ea == null || eb == null) return if (ea == null && eb == null) 0.0 else Double.MAX_VALUE
            if (ea.corners.size != eb.corners.size) return Double.MAX_VALUE
            // Confronto degli angoli indipendente dal punto di partenza e dal verso del giro.
            val pa = ea.corners
            var best = Double.MAX_VALUE
            for (rev in listOf(false, true)) {
                val pb = if (rev) eb.corners.reversed() else eb.corners
                for (shift in pb.indices) {
                    var m = 0.0
                    for (k in pa.indices) m = max(m, pa[k].distanceTo(pb[(k + shift) % pb.size]))
                    best = min(best, m)
                }
            }
            return best
        }
        val sigs = linkedSetOf(ref.signature())
        var identical = 0
        var maxDev = 0.0; var maxArea = 0.0; var maxScore = 0.0
        val details = mutableListOf<String>()
        for (seed in 1..runs) {
            val rnd = kotlin.random.Random(seed)
            val newIds = input.candidates.indices.toList().shuffled(rnd)
            val relabeled = input.candidates.mapIndexed { i, c -> c.copy(id = 5_000 + newIds[i]) }.shuffled(rnd)
            val res = run(input.copy(candidates = relabeled), p)
            val d = dev(ref, res)
            val da = abs((ref.best?.areaM2 ?: 0.0) - (res.best?.areaM2 ?: 0.0))
            val ds = abs((ref.best?.score ?: 0.0) - (res.best?.score ?: 0.0))
            sigs += res.signature()
            if (res.signature() == ref.signature() && d < 1e-6) identical++
            maxDev = max(maxDev, if (d == Double.MAX_VALUE) 1e9 else d); maxArea = max(maxArea, da); maxScore = max(maxScore, ds)
            details += "permutazione $seed: pareti ${res.walls.size}, ciclo ${res.best?.wallIndices?.size ?: 0}, area ${fmt(res.best?.areaM2 ?: 0.0)} m², punteggio ${fmt(res.best?.score ?: 0.0, 4)}, scarto massimo angoli ${if (d == Double.MAX_VALUE) "n/d" else fmt(d * 100, 4) + " cm"}"
        }
        return PermutationReport(runs, identical, sigs.toList(), maxDev, maxArea, maxScore, details)
    }

    private fun fmt(v: Double, d: Int = 2) = com.sagoma.planimetria.geometry.formatDecimal(v, d, '.')
    private fun pct(v: Double) = com.sagoma.planimetria.geometry.formatDecimal(v * 100, 0, '.') + "%"
}
