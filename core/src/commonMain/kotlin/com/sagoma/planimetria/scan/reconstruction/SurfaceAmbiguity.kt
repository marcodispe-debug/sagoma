package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.geometry.formatDecimal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * R2.1 — EVIDENZA DI SUPERFICIE ALTERNATIVA (solo informazione diagnostica aggiuntiva; specifica: r2-1-spec-audit/specification.md).
 *
 * Per ogni superficie verticale di R2 misura se esiste, OSSERVATA, una superficie parallela arretrata: dietro il fronte (E1), sopra
 * (E4), ai lati (E5). ambiguityScore = max(copertura · continuità) su E1, E4, E5 sinistra, E5 destra. Nient'altro entra nello score:
 *  - E2 (parallela adiacente vista da R2) ed E3 (fianchi perpendicolari) sono esposti, con contributo ZERO;
 *  - l'assenza di informazione dietro (NO_BACKFACE_INFORMATION) è solo un flag: contributo ZERO. L'assenza di evidenza non è
 *    evidenza di oggetto.
 * Non cambia NULLA di R2: classi, punteggio strutturale, FREE_SPACE, vicinato. R3/R4 non leggono questi dati.
 * Le viste sono ricalcolate qui dai raggi camera → punto della mappa (R1 non conserva quali viste hanno osservato una cella).
 */

/** Parametri fissati dalla specifica congelata (nessuno è tarato sul benchmark). */
object AmbiguityParams {
    /** Cella della griglia delle osservazioni (m). */
    const val CELL_M = 0.05
    /** Vista = posizione della camera in pianta entro questo raggio da una già vista (m). */
    const val VIEW_RADIUS_M = 0.5
    /** Sotto questa distanza una superficie è un frammento complanare dello stesso piano, non una superficie arretrata (m). */
    const val RECESSED_MIN_M = 0.075
    /** E1: distanza mediana minima della superficie osservata dietro il fronte (m). */
    const val BEHIND_MIN_M = 0.15
    /** Profondità massima di ricerca dietro il piano (m), come il controllo "esterna" di R2. */
    const val MAX_DEPTH_M = 1.5
    /** Bande laterali e superiore: da [BAND_GAP_M] a [BAND_M] oltre il bordo (m). */
    const val BAND_GAP_M = 0.05
    const val BAND_M = 0.5
    /** Soglia DIAGNOSTICA per i consumatori futuri. Non cambia nessuna classificazione. */
    const val TAU = 0.15
    /** Sotto questo score non si calcola la stabilità (non c'è evidenza da togliere). */
    const val STABILITY_MIN_SCORE = 0.05
}

/** Un canale di evidenza (E1, E4, E5): frazione di colonne con superficie arretrata osservata e loro continuità. */
data class AmbiguityChannel(
    val columns: Int,
    val coverage: Double,
    val continuity: Double,
    /** Distanza mediana della superficie arretrata dal piano (m), null se nessuna. */
    val distanceM: Double?,
    /** Viste che hanno misurato la superficie arretrata. */
    val views: Int,
) {
    val score: Double get() = coverage * continuity
}

/** E2: superficie R2 parallela, arretrata, accanto o sopra. Contributo allo score: zero. */
data class AdjacentParallelEvidence(val surfaceId: Int, val distanceM: Double, val where: String)

/** E3: superfici perpendicolari che partono da un estremo del fronte. Contributo allo score: zero. */
data class PerpendicularEvidence(val sidesBehind: Int, val depthM: Double?, val sidesForward: Int)

/** Qualità della MISURA della superficie (asse separato dall'ambiguità: non entra mai nello score). */
enum class MeasurementQuality { HIGH, LOW }

/**
 * Evidenza di superficie alternativa per una superficie verticale. [score] ∈ [0, 1]. [stability]: lo score minimo togliendo una
 * vista alla volta ("l'evidenza resta se tolgo una delle viste che la supportano?"); con una sola vista vale 0.
 */
data class SurfaceAmbiguity(
    val surfaceId: Int,
    val score: Double,
    /** E1: superficie parallela osservata dietro il fronte, nella sua estensione. */
    val behind: AmbiguityChannel,
    /** E4: superficie arretrata osservata sopra il fronte. */
    val above: AmbiguityChannel,
    /** E5: superficie arretrata osservata a sinistra e a destra del fronte. */
    val left: AmbiguityChannel,
    val right: AmbiguityChannel,
    /** E2 ed E3: esposti, contributo zero. */
    val adjacent: AdjacentParallelEvidence?,
    val perpendicular: PerpendicularEvidence,
    /** Viste che sostengono l'evidenza (unione delle viste di E1, E4, E5). */
    val supportingViews: Int,
    val stability: Double,
    val viewsRemoved: Int,
    val frontViews: Int,
    val totalViews: Int,
    /** Frazione delle colonne dietro il fronte mai osservate. */
    val behindUnobservedFraction: Double,
    /** Nessuna evidenza e dietro quasi tutto non osservato: limite informativo, NON evidenza di oggetto (contributo zero). */
    val noBackfaceInformation: Boolean,
    val quality: MeasurementQuality,
)

object SurfaceAmbiguityEstimator {

    /** Griglia sparsa delle osservazioni: per cella, viste che l'hanno attraversata (libera) o vi hanno misurato un punto. */
    private class ObsGrid(val cell: Double, val minX: Double, val minY: Double, val minZ: Double, val nx: Int, val ny: Int, val nz: Int) {
        private var keys = LongArray(1 shl 16) { -1L }
        var free = IntArray(1 shl 16); private set
        var hit = IntArray(1 shl 16); private set
        var hitCount = IntArray(1 shl 16); private set
        /** Superficie (indice) dei punti caduti nella cella: −1 nessun punto, −2 più superfici, −3 punti non assegnati. */
        var owner = IntArray(1 shl 16) { -1 }; private set
        private var used = 0

        fun key(x: Double, y: Double, z: Double): Long {
            val i = floor((x - minX) / cell).toInt(); val j = floor((y - minY) / cell).toInt(); val k = floor((z - minZ) / cell).toInt()
            if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return -1L
            return (k.toLong() * ny + j) * nx + i
        }

        private fun slot(key: Long): Int {
            var h = ((key * -7046029254386353131L) ushr 40).toInt() and (keys.size - 1)
            while (keys[h] != -1L && keys[h] != key) h = (h + 1) and (keys.size - 1)
            return h
        }

        /** Indice della cella (creata se manca). */
        fun put(key: Long): Int {
            if (used * 2 >= keys.size) grow()
            val h = slot(key)
            if (keys[h] == -1L) { keys[h] = key; used++ }
            return h
        }

        /** Indice della cella o −1 se mai toccata. */
        fun find(key: Long): Int { val h = slot(key); return if (keys[h] == key) h else -1 }

        private fun grow() {
            val ok = keys; val of = free; val oh = hit; val oc = hitCount; val oo = owner
            keys = LongArray(ok.size * 2) { -1L }; free = IntArray(ok.size * 2); hit = IntArray(ok.size * 2); hitCount = IntArray(ok.size * 2); owner = IntArray(ok.size * 2) { -1 }
            for (s in ok.indices) if (ok[s] != -1L) {
                val h = slot(ok[s]); keys[h] = ok[s]; free[h] = of[s]; hit[h] = oh[s]; hitCount[h] = oc[s]; owner[h] = oo[s]
            }
        }
    }

    private class Views(val ofFrame: IntArray, val count: Int)

    private fun views(map: GlobalMap): Views {
        val pos = mutableListOf<DoubleArray>()
        val of = IntArray(map.frames.size) { f ->
            val p = map.frames[f].pose
            val k = pos.indexOfFirst { hypot(it[0] - p.x, it[1] - p.z) <= AmbiguityParams.VIEW_RADIUS_M }
            if (k >= 0) k else { pos.add(doubleArrayOf(p.x, p.z)); pos.size - 1 }
        }
        return Views(of, pos.size)
    }

    private fun grid(map: GlobalMap, voxelSurface: IntArray, views: Views): ObsGrid {
        val pts = map.points; val cell = AmbiguityParams.CELL_M
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var z0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE; var z1 = -Double.MAX_VALUE
        for (i in 0 until pts.size) { x0 = min(x0, pts.x[i].toDouble()); y0 = min(y0, pts.y[i].toDouble()); z0 = min(z0, pts.z[i].toDouble()); x1 = max(x1, pts.x[i].toDouble()); y1 = max(y1, pts.y[i].toDouble()); z1 = max(z1, pts.z[i].toDouble()) }
        for (f in map.frames) { x0 = min(x0, f.pose.x); y0 = min(y0, f.pose.y); z0 = min(z0, f.pose.z); x1 = max(x1, f.pose.x); y1 = max(y1, f.pose.y); z1 = max(z1, f.pose.z) }
        val m = 0.3
        val g = ObsGrid(cell, x0 - m, y0 - m, z0 - m, ((x1 - x0 + 2 * m) / cell).toInt() + 1, ((y1 - y0 + 2 * m) / cell).toInt() + 1, ((z1 - z0 + 2 * m) / cell).toInt() + 1)
        val pv = IntArray(pts.size) { -1 }
        val vx = map.voxels
        for (v in 0 until vx.size) for (t in vx.start[v] until vx.start[v] + vx.count[v]) pv[vx.order[t]] = v
        for (i in 0 until pts.size) {
            val f = pts.frame[i]; val c = map.frames[f].pose
            val bit = 1 shl (views.ofFrame[f] % 32)
            val px = pts.x[i].toDouble(); val py = pts.y[i].toDouble(); val pz = pts.z[i].toDouble()
            val dx = px - c.x; val dy = py - c.y; val dz = pz - c.z
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            val step = cell / 2
            var t = 0.0
            while (t < len - cell) {
                val k = g.key(c.x + dx * t / len, c.y + dy * t / len, c.z + dz * t / len)
                if (k >= 0) { val s = g.put(k); g.free[s] = g.free[s] or bit }
                t += step
            }
            val k = g.key(px, py, pz)
            if (k >= 0) {
                val s = g.put(k)
                g.hit[s] = g.hit[s] or bit; g.hitCount[s]++
                val own = pv[i].let { v -> if (v < 0) -3 else voxelSurface[v].let { if (it < 0) -3 else it } }
                g.owner[s] = when (g.owner[s]) { -1 -> own; own -> own; else -> -2 }
            }
        }
        return g
    }

    private class Column(val hitD: Double?, val hitMask: Int, val freeSamples: Int, val unobserved: Int, val samples: Int)

    /** Colonna lungo −n: prima cella con ≥ 2 punti di ALTRE superfici e stato delle celle attraversate prima. [excl]: viste ignorate. */
    private fun walk(g: ObsGrid, x: Double, y: Double, z: Double, nx: Double, nz: Double, dFrom: Double, self: Int, excl: Int): Column {
        val keep = excl.inv()
        val step = g.cell / 2
        var d = dFrom; var free = 0; var unobs = 0; var n = 0
        while (d <= AmbiguityParams.MAX_DEPTH_M + 1e-9) {
            val k = g.key(x - nx * d, y, z - nz * d)
            if (k >= 0) {
                val s = g.find(k)
                val hm = if (s < 0) 0 else g.hit[s] and keep; val fm = if (s < 0) 0 else g.free[s] and keep
                if (s >= 0 && g.hitCount[s] >= 2 && hm != 0 && g.owner[s] != self) return Column(d, hm, free, unobs, n)
                if (fm != 0) free++ else if (hm == 0) unobs++
            } else unobs++
            n++
            d += step
        }
        return Column(null, 0, free, unobs, n)
    }

    private fun median(v: List<Double>) = if (v.isEmpty()) null else v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
    private fun continuity(v: List<Double>): Double? { val m = median(v) ?: return null; return v.count { abs(it - m) <= 0.03 }.toDouble() / v.size }

    /** [supportMask]: viste che hanno misurato superfici arretrate valide in E1, E4, E5. */
    private class Channels(val behind: AmbiguityChannel, val above: AmbiguityChannel, val left: AmbiguityChannel, val right: AmbiguityChannel, val behindUnobserved: Double, val supportMask: Int) {
        val score: Double get() = max(max(behind.score, above.score), max(left.score, right.score))
    }

    private fun channels(g: ObsGrid, sf: Surface, floorY: Double?, ceilingY: Double?, excl: Int): Channels {
        val self = sf.id
        val h = hypot(sf.plane.nx, sf.plane.nz).takeIf { it > 1e-9 } ?: 1.0
        val nx = sf.plane.nx / h; val nz = sf.plane.nz / h; val ux = sf.u[0]; val uz = sf.u[2]
        val c = sf.plane.centroid
        val fy = floorY ?: sf.yMin
        val cy = ceilingY ?: (sf.yMax + 10)
        val st = g.cell
        fun range(a: Double, b: Double): List<Double> { val out = mutableListOf<Double>(); var t = a; while (t <= b + 1e-9) { out.add(t); t += st }; return out }
        val yLo = max(sf.yMin, fy + 0.15) + 0.05; val yHi = min(sf.yMax, cy - 0.15) - 0.05
        fun col(t: Double, y: Double, d0: Double) = walk(g, c[0] + ux * t, y, c[2] + uz * t, nx, nz, d0, self, excl)

        // E1: dietro il fronte, nella sua estensione.
        val cols = mutableListOf<Column>()
        for (t in range(sf.uMin + 0.05, sf.uMax - 0.05)) for (y in range(yLo, yHi)) cols.add(col(t, y, AmbiguityParams.RECESSED_MIN_M))
        val hits = cols.filter { it.hitD != null }
        val hd = hits.map { it.hitD!! }
        val md = median(hd)
        val behindFrac = if (cols.isEmpty()) 0.0 else hits.size.toDouble() / cols.size
        val behind = AmbiguityChannel(
            cols.size, if ((md ?: 0.0) >= AmbiguityParams.BEHIND_MIN_M) behindFrac else 0.0, continuity(hd) ?: 0.0, md,
            hits.fold(0) { a, k -> a or k.hitMask }.countOneBits(),
        )
        val unobs = cols.count { it.hitD == null && it.samples > 0 && it.unobserved >= 0.8 * it.samples }

        // E4/E5: bande sopra e ai lati, superficie arretrata oltre il frammento complanare.
        var mask = if (behind.coverage > 0) hits.fold(0) { a, k -> a or k.hitMask } else 0
        fun band(cs: List<Column>): AmbiguityChannel {
            if (cs.isEmpty()) return AmbiguityChannel(0, 0.0, 0.0, null, 0)
            val rec = cs.filter { it.hitD != null && it.hitD > AmbiguityParams.RECESSED_MIN_M }
            val rd = rec.map { it.hitD!! }
            val m = rec.fold(0) { a, k -> a or k.hitMask }
            mask = mask or m
            return AmbiguityChannel(cs.size, rec.size.toDouble() / cs.size, continuity(rd) ?: 0.0, median(rd), m.countOneBits())
        }
        val left = mutableListOf<Column>(); val right = mutableListOf<Column>(); val above = mutableListOf<Column>()
        val gap = AmbiguityParams.BAND_GAP_M; val bw = AmbiguityParams.BAND_M
        for (y in range(yLo, yHi)) {
            for (t in range(sf.uMin - bw, sf.uMin - gap)) left.add(col(t, y, -0.10))
            for (t in range(sf.uMax + gap, sf.uMax + bw)) right.add(col(t, y, -0.10))
        }
        for (t in range(sf.uMin + 0.05, sf.uMax - 0.05)) for (y in range(sf.yMax + gap, min(sf.yMax + bw, cy - 0.15))) above.add(col(t, y, -0.10))
        val ab = band(above); val lb = band(left); val rb = band(right)
        return Channels(behind, ab, lb, rb, if (cols.isEmpty()) 0.0 else unobs.toDouble() / cols.size, mask)
    }

    private fun overlap(a: Surface, b: Surface): Double {
        fun proj(t: Double): Double { val px = b.plane.centroid[0] + b.u[0] * t; val pz = b.plane.centroid[2] + b.u[2] * t; return (px - a.plane.centroid[0]) * a.u[0] + (pz - a.plane.centroid[2]) * a.u[2] }
        val b0 = proj(b.uMin); val b1 = proj(b.uMax)
        return max(0.0, min(a.uMax, max(b0, b1)) - max(a.uMin, min(b0, b1)))
    }

    private fun lateralGap(a: Surface, b: Surface): Double {
        fun proj(t: Double): Double { val px = b.plane.centroid[0] + b.u[0] * t; val pz = b.plane.centroid[2] + b.u[2] * t; return (px - a.plane.centroid[0]) * a.u[0] + (pz - a.plane.centroid[2]) * a.u[2] }
        val lo = min(proj(b.uMin), proj(b.uMax)); val hi = max(proj(b.uMin), proj(b.uMax))
        return when { hi < a.uMin -> a.uMin - hi; lo > a.uMax -> lo - a.uMax; else -> 0.0 }
    }

    /** E2 (parallela adiacente arretrata vista da R2) ed E3 (fianchi): solo dalle superfici R2. Contributo allo score: zero. */
    private fun r2Evidence(sf: Surface, verticals: List<Surface>): Pair<AdjacentParallelEvidence?, PerpendicularEvidence> {
        val h = hypot(sf.plane.nx, sf.plane.nz).takeIf { it > 1e-9 } ?: 1.0
        val nx = sf.plane.nx / h; val nz = sf.plane.nz / h
        val c = sf.plane.centroid
        val others = verticals.filter { it.id != sf.id }
        fun offset(o: Surface) = (o.plane.centroid[0] - c[0]) * nx + (o.plane.centroid[2] - c[2]) * nz
        var adj: AdjacentParallelEvidence? = null
        for (o in others.filter { it.plane.angleTo(sf.plane) <= 10 && -offset(it) in AmbiguityParams.RECESSED_MIN_M..AmbiguityParams.MAX_DEPTH_M }.sortedBy { -offset(it) }) {
            val ov = overlap(sf, o)
            val where = when {
                ov < 0.3 * sf.lengthM && lateralGap(sf, o) <= AmbiguityParams.BAND_M -> "lato"
                ov > 0 && o.yMin >= sf.yMax - 0.1 -> "sopra"
                else -> null
            }
            if (where != null) { adj = AdjacentParallelEvidence(o.id, -offset(o), where); break }
        }
        val ends = listOf(sf.uMin, sf.uMax).map { doubleArrayOf(c[0] + sf.u[0] * it, c[2] + sf.u[2] * it) }
        var back = 0; var fwd = 0; var depth: Double? = null
        for (o in others.filter { it.plane.angleTo(sf.plane) >= 75 }) {
            val oc = o.plane.centroid
            val oe = listOf(o.uMin, o.uMax).map { doubleArrayOf(oc[0] + o.u[0] * it, oc[2] + o.u[2] * it) }
            for (a in 0..1) {
                if (ends.none { hypot(it[0] - oe[a][0], it[1] - oe[a][1]) <= 0.15 }) continue
                val far = oe[1 - a]
                val d = -((far[0] - c[0]) * nx + (far[1] - c[2]) * nz)
                if (d in AmbiguityParams.RECESSED_MIN_M..AmbiguityParams.MAX_DEPTH_M) { back++; depth = max(depth ?: 0.0, d) } else if (d < -AmbiguityParams.RECESSED_MIN_M) fwd++
                break
            }
        }
        return adj to PerpendicularEvidence(back, depth, fwd)
    }

    /**
     * Evidenza di superficie alternativa per tutte le superfici verticali (chiave: id della superficie). Deterministica.
     * [voxelSurface]: per voxel, l'id della superficie (−1 nessuna), come in [SurfaceResult.voxelSurface].
     */
    fun estimate(map: GlobalMap, surfaces: List<Surface>, voxelSurface: IntArray, floorY: Double?, ceilingY: Double?, p: SurfaceParams = SurfaceParams()): Map<Int, SurfaceAmbiguity> {
        val verticals = surfaces.filter { it.orientation == Orientation.VERTICAL }
        if (verticals.isEmpty() || map.points.size == 0) return emptyMap()
        val views = views(map)
        val g = grid(map, voxelSurface, views)
        val out = LinkedHashMap<Int, SurfaceAmbiguity>()
        for (sf in verticals) {
            val ch = channels(g, sf, floorY, ceilingY, 0)
            val score = ch.score
            var stab = score; var removed = 0
            if (score >= AmbiguityParams.STABILITY_MIN_SCORE) for (v in 0 until min(views.count, 32)) { removed++; stab = min(stab, channels(g, sf, floorY, ceilingY, 1 shl v).score) }
            val (adj, perp) = r2Evidence(sf, verticals)
            val fv = HashSet<Int>()
            for (i in SurfaceExtractor.pointsOf(map, sf.memberVoxels)) fv.add(views.ofFrame[map.points.frame[i]])
            val weak = sf.samples < p.minSamples || sf.effectiveFrames < p.minEffectiveFrames || sf.areaM2 < p.minAreaM2
            out[sf.id] = SurfaceAmbiguity(
                sf.id, score, ch.behind, ch.above, ch.left, ch.right, adj, perp,
                ch.supportMask.countOneBits(),
                stab, removed, fv.size, views.count, ch.behindUnobserved,
                score < AmbiguityParams.STABILITY_MIN_SCORE && ch.behindUnobserved >= 0.8,
                if (weak || fv.size < 2 || sf.areaM2 < 0.5) MeasurementQuality.LOW else MeasurementQuality.HIGH,
            )
        }
        return out
    }
}

/** `surface-ambiguity.csv`: una riga per superficie verticale. File NUOVO: gli output esistenti non cambiano. */
object SurfaceAmbiguityReport {
    private fun f(v: Double?, d: Int = 3) = if (v == null) "" else formatDecimal(v, d, '.')
    private fun ch(c: AmbiguityChannel) = listOf(c.columns, f(c.coverage), f(c.continuity), f(c.distanceM), c.views)

    fun csv(s: SurfaceResult): String = buildString {
        append("id;kind;structuralScore;areaM2;ambiguityScore;aboveTau;stability;viewsRemoved;supportingViews;frontViews;totalViews;measurementQuality;noBackfaceInformation;behindUnobservedFraction;")
        append("E1behind_columns;E1behind_coverage;E1behind_continuity;E1behind_distanceM;E1behind_views;E4above_columns;E4above_coverage;E4above_continuity;E4above_distanceM;E4above_views;")
        append("E5left_columns;E5left_coverage;E5left_continuity;E5left_distanceM;E5left_views;E5right_columns;E5right_coverage;E5right_continuity;E5right_distanceM;E5right_views;")
        append("E2adjacent_surface(score0);E2adjacent_distanceM;E2adjacent_where;E3sidesBehind(score0);E3depthM;E3sidesForward\n")
        for (sf in s.surfaces) {
            val a = s.ambiguity[sf.id] ?: continue
            append(
                (listOf(sf.id, sf.kind, sf.structuralScore?.let { f(it) } ?: "", f(sf.areaM2), f(a.score), a.score >= AmbiguityParams.TAU, f(a.stability), a.viewsRemoved, a.supportingViews,
                    a.frontViews, a.totalViews, a.quality, a.noBackfaceInformation, f(a.behindUnobservedFraction)) +
                    ch(a.behind) + ch(a.above) + ch(a.left) + ch(a.right) +
                    listOf(a.adjacent?.let { "S${it.surfaceId}" } ?: "", f(a.adjacent?.distanceM), a.adjacent?.where ?: "", a.perpendicular.sidesBehind, f(a.perpendicular.depthM), a.perpendicular.sidesForward))
                    .joinToString(";"),
            )
            append('\n')
        }
    }
}
