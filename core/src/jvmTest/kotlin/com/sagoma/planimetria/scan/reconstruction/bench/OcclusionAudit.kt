package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.PerimeterResult
import com.sagoma.planimetria.scan.reconstruction.PerimeterSolver
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.Surface
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimationResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimator
import com.sagoma.planimetria.scan.recording.ScanRecording
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * AUDIT (solo test) — evidenza geometrica DIETRO una superficie verticale (BACKFACE_EVIDENCE) e contesto del perimetro R3/R4.
 * Non è una classificazione: misura separatamente cosa è stato osservato dietro il fronte, accanto, sopra, e i fianchi.
 * R1 non conserva quali viste hanno visto una cella: la griglia delle viste è RICALCOLATA qui dai raggi camera → punto della mappa.
 * Il contesto R3/R4 è calcolato in due modi: ingenuo (R2 → R3 → R4 con la candidata) e leave-one-out (la candidata è tolta dai dati
 * PRIMA di R3: R2 → rimuovi candidata → R3 → R4). Nessuna modifica di produzione: R3/R4 sono chiamati con i loro ingressi pubblici.
 */
object OcclusionAudit {
    /** Una superficie a meno di 7,5 cm (1,5 celle) dal piano è trattata come frammento complanare dello stesso piano, non come superficie dietro. */
    const val FRAG = 0.075

    /** Griglia delle osservazioni: per cella, maschera delle viste che l'hanno attraversata (libera) o vi hanno misurato un punto. */
    class ViewGrid(val cell: Double, val minX: Double, val minY: Double, val minZ: Double, val nx: Int, val ny: Int, val nz: Int) {
        val free = IntArray(nx * ny * nz)
        val hit = IntArray(nx * ny * nz)
        val hitCount = IntArray(nx * ny * nz)
        /** Superficie R2 (indice in SurfaceResult.surfaces) dei punti caduti nella cella: −1 nessun punto, −2 più superfici, −3 punti non assegnati. */
        val owner = IntArray(nx * ny * nz) { -1 }
        fun index(x: Double, y: Double, z: Double): Int {
            val i = floor((x - minX) / cell).toInt(); val j = floor((y - minY) / cell).toInt(); val k = floor((z - minZ) / cell).toInt()
            if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return -1
            return (k * ny + j) * nx + i
        }
    }

    /** Vista = posizione della camera in pianta: frame entro [radiusM] da una posizione già vista appartengono alla stessa vista. */
    class Views(val ofFrame: IntArray, val positions: List<DoubleArray>) {
        val count: Int get() = positions.size
        /** Id oltre 31 condividono il bit (id mod 32): il conteggio delle viste è allora un limite inferiore. */
        val aliased: Boolean get() = positions.size > 32
    }

    fun views(map: GlobalMap, radiusM: Double): Views {
        val pos = mutableListOf<DoubleArray>()
        val of = IntArray(map.frames.size) { f ->
            val p = map.frames[f].pose
            val k = pos.indexOfFirst { hypot(it[0] - p.x, it[1] - p.z) <= radiusM }
            if (k >= 0) k else { pos.add(doubleArrayOf(p.x, p.z)); pos.size - 1 }
        }
        return Views(of, pos)
    }

    /** Indice del voxel di ogni punto della mappa. */
    fun pointVoxel(map: GlobalMap): IntArray {
        val vx = map.voxels; val out = IntArray(map.points.size) { -1 }
        for (v in 0 until vx.size) for (t in vx.start[v] until vx.start[v] + vx.count[v]) out[vx.order[t]] = v
        return out
    }

    /** Ricalcola la griglia: per ogni punto (uno ogni [stride]) il raggio dalla camera marca libere le celle attraversate. */
    fun grid(map: GlobalMap, s: SurfaceResult, views: Views, cell: Double, stride: Int = 1): ViewGrid {
        val pts = map.points
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var z0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE; var z1 = -Double.MAX_VALUE
        for (i in 0 until pts.size) { x0 = min(x0, pts.x[i].toDouble()); y0 = min(y0, pts.y[i].toDouble()); z0 = min(z0, pts.z[i].toDouble()); x1 = max(x1, pts.x[i].toDouble()); y1 = max(y1, pts.y[i].toDouble()); z1 = max(z1, pts.z[i].toDouble()) }
        for (f in map.frames) { x0 = min(x0, f.pose.x); y0 = min(y0, f.pose.y); z0 = min(z0, f.pose.z); x1 = max(x1, f.pose.x); y1 = max(y1, f.pose.y); z1 = max(z1, f.pose.z) }
        val m = 0.3
        val g = ViewGrid(cell, x0 - m, y0 - m, z0 - m, ((x1 - x0 + 2 * m) / cell).toInt() + 1, ((y1 - y0 + 2 * m) / cell).toInt() + 1, ((z1 - z0 + 2 * m) / cell).toInt() + 1)
        val pv = pointVoxel(map)
        var i = 0
        while (i < pts.size) {
            val f = pts.frame[i]; val c = map.frames[f].pose
            val bit = 1 shl (views.ofFrame[f] % 32)
            val px = pts.x[i].toDouble(); val py = pts.y[i].toDouble(); val pz = pts.z[i].toDouble()
            val dx = px - c.x; val dy = py - c.y; val dz = pz - c.z
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            val step = cell / 2
            var t = 0.0
            while (t < len - cell) {
                val k = g.index(c.x + dx * t / len, c.y + dy * t / len, c.z + dz * t / len)
                if (k >= 0) g.free[k] = g.free[k] or bit
                t += step
            }
            val k = g.index(px, py, pz)
            if (k >= 0) {
                g.hit[k] = g.hit[k] or bit; g.hitCount[k]++
                val own = pv[i].let { v -> if (v < 0) -3 else s.voxelSurface[v].let { if (it < 0) -3 else it } }
                g.owner[k] = when (g.owner[k]) { -1 -> own; own -> own; else -> -2 }
            }
            i += stride
        }
        return g
    }

    // ------------------------------------------------------------------------------------------------------- BACKFACE_EVIDENCE

    /** Una colonna percorsa lungo −n: prima cella con punti di ALTRE superfici (≥ 2 punti) e stato delle celle attraversate prima. */
    class Column(val hitD: Double?, val hitMask: Int, val freeSamples: Int, val unobserved: Int, val samples: Int, val seenMask: Int)

    private fun walk(g: ViewGrid, x: Double, y: Double, z: Double, nx: Double, nz: Double, dFrom: Double, dTo: Double, self: Int, excl: Int = 0): Column {
        val keep = excl.inv()
        val step = g.cell / 2
        var d = dFrom; var free = 0; var unobs = 0; var n = 0; var seen = 0
        while (d <= dTo + 1e-9) {
            val k = g.index(x - nx * d, y, z - nz * d)
            if (k >= 0) {
                val hm = g.hit[k] and keep; val fm = g.free[k] and keep
                if (g.hitCount[k] >= 2 && hm != 0 && g.owner[k] != self) return Column(d, hm, free, unobs, n, seen or hm)
                if (fm != 0) free++ else if (hm == 0) unobs++
                seen = seen or fm or hm
            } else unobs++
            n++
            d += step
        }
        return Column(null, 0, free, unobs, n, seen)
    }

    class Band(val columns: Int, val coplanarFrac: Double, val recessedFrac: Double, val recessedMedianD: Double?, val continuity: Double?, val forwardFrac: Double, val noneFrac: Double, val views: Int)

    class Backface(
        val surfaceId: Int,
        /** (1) Dietro il fronte, dentro la sua estensione (u, y): colonne, frazione con superficie osservata, distanza mediana, continuità. */
        val behindColumns: Int, val behindHitFrac: Double, val behindHitMedianD: Double?, val behindContinuity: Double?,
        val behindFreeFrac: Double, val behindUnobservedFrac: Double, val behindViews: Int, val behindHitViews: Int,
        /** Volume non osservato tra il fronte e la prima superficie osservata (o 1,5 m), m³. */
        val unobservedVolumeM3: Double,
        /** (2) Bande adiacenti: sinistra, destra (fuori dall'estensione in u), sopra (oltre il bordo superiore). */
        val bands: Map<String, Band>,
        /** Superficie R2 parallela dietro (replica del controllo "esterna" di produzione) e parallela adiacente arretrata. */
        val r2Behind: Int?, val r2BehindD: Double?, val r2Adjacent: Int?, val r2AdjacentD: Double?, val r2AdjacentWhere: String?,
        /** (3) Fianchi: superfici perpendicolari con un estremo su un estremo del fronte e l'altro dietro (o davanti) al fronte. */
        val sidesBack: Int, val sideBackDepthM: Double?, val sidesForward: Int,
        val frontViews: Int,
        val freeBehind: Double, val freeOfAll: Double, val observedFraction: Double,
    ) {
        val bestBand: Band? get() = bands.values.filter { it.columns > 0 }.maxByOrNull { it.recessedFrac }
    }

    private fun median(v: List<Double>) = if (v.isEmpty()) null else v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
    private fun continuity(v: List<Double>): Double? { val m = median(v) ?: return null; return v.count { abs(it - m) <= 0.03 }.toDouble() / v.size }

    /** Normale orizzontale unitaria (verso le camere) e asse u orizzontale della superficie. */
    private fun frame(sf: Surface): DoubleArray {
        val h = hypot(sf.plane.nx, sf.plane.nz).takeIf { it > 1e-9 } ?: 1.0
        return doubleArrayOf(sf.plane.nx / h, sf.plane.nz / h, sf.u[0], sf.u[2])
    }

    /** [excl]: maschera di viste da ignorare (stabilità "togli una vista"; il conteggio di punti per cella resta quello di tutte le viste). */
    fun backface(map: GlobalMap, s: SurfaceResult, g: ViewGrid, views: Views, sf: Surface, excl: Int = 0): Backface {
        val self = s.surfaces.indexOf(sf)
        val (nx, nz, ux, uz) = frame(sf).let { listOf(it[0], it[1], it[2], it[3]) }
        val c = sf.plane.centroid
        val floorY = s.floor?.y ?: sf.yMin
        val ceilY = s.ceilingY ?: (sf.yMax + 10)
        val st = g.cell
        fun range(a: Double, b: Double): List<Double> { val out = mutableListOf<Double>(); var t = a; while (t <= b + 1e-9) { out.add(t); t += st }; return out }
        val yLo = max(sf.yMin, floorY + 0.15) + 0.05; val yHi = min(sf.yMax, ceilY - 0.15) - 0.05
        fun col(t: Double, y: Double, d0: Double) = walk(g, c[0] + ux * t, y, c[2] + uz * t, nx, nz, d0, 1.5, self, excl)

        // (1) dietro il fronte
        val cols = mutableListOf<Column>()
        for (t in range(sf.uMin + 0.05, sf.uMax - 0.05)) for (y in range(yLo, yHi)) cols.add(col(t, y, FRAG))
        val hits = cols.filter { it.hitD != null }
        val nonHit = cols.filter { it.hitD == null }
        val freeCols = nonHit.count { it.samples > 0 && it.freeSamples >= 0.5 * it.samples }
        val unobsCols = nonHit.count { it.samples > 0 && it.unobserved >= 0.8 * it.samples }
        val vol = cols.sumOf { it.unobserved } * st * st * (st / 2)
        val seen = cols.fold(0) { a, k -> a or k.seenMask }; val hitSeen = hits.fold(0) { a, k -> a or k.hitMask }

        // (2) bande adiacenti
        fun band(cs: List<Column>): Band {
            if (cs.isEmpty()) return Band(0, 0.0, 0.0, null, null, 0.0, 0.0, 0)
            val rec = cs.filter { it.hitD != null && it.hitD > FRAG }.map { it.hitD!! }
            val cop = cs.count { it.hitD != null && abs(it.hitD) <= 0.05 }
            val fwd = cs.count { it.hitD != null && it.hitD < -0.05 }
            val v = cs.filter { it.hitD != null && it.hitD > FRAG }.fold(0) { a, k -> a or k.hitMask }
            return Band(cs.size, cop.toDouble() / cs.size, rec.size.toDouble() / cs.size, median(rec), continuity(rec), fwd.toDouble() / cs.size, cs.count { it.hitD == null }.toDouble() / cs.size, Integer.bitCount(v))
        }
        val left = mutableListOf<Column>(); val right = mutableListOf<Column>(); val above = mutableListOf<Column>()
        for (y in range(yLo, yHi)) {
            for (t in range(sf.uMin - 0.5, sf.uMin - 0.05)) left.add(col(t, y, -0.10))
            for (t in range(sf.uMax + 0.05, sf.uMax + 0.5)) right.add(col(t, y, -0.10))
        }
        for (t in range(sf.uMin + 0.05, sf.uMax - 0.05)) for (y in range(sf.yMax + 0.05, min(sf.yMax + 0.5, ceilY - 0.15))) above.add(col(t, y, -0.10))

        // R2: parallela dietro (produzione), parallela adiacente arretrata, fianchi.
        val verticals = s.surfaces.filter { it.orientation == Orientation.VERTICAL && it.id != sf.id }
        fun offset(o: Surface) = (o.plane.centroid[0] - c[0]) * nx + (o.plane.centroid[2] - c[2]) * nz
        val behind = verticals.firstOrNull { o ->
            o.plane.angleTo(sf.plane) <= 10 && (o.plane.centroid[0] - c[0]) * sf.plane.nx + (o.plane.centroid[2] - c[2]) * sf.plane.nz in -1.5..-0.05 &&
                overlap(sf, o) >= 0.3 * sf.lengthM && o.yMax >= sf.yMax - 0.1
        }
        var adj: Surface? = null; var adjWhere: String? = null
        for (o in verticals.filter { it.plane.angleTo(sf.plane) <= 10 && -offset(it) in 0.04..1.5 }.sortedBy { -offset(it) }) {
            val ov = overlap(sf, o)
            val where = when {
                ov < 0.3 * sf.lengthM && lateralGap(sf, o) <= 0.5 -> "lato"
                ov > 0 && o.yMin >= sf.yMax - 0.1 -> "sopra"
                else -> null
            }
            if (where != null) { adj = o; adjWhere = where; break }
        }
        val ends = listOf(sf.uMin, sf.uMax).map { doubleArrayOf(c[0] + ux * it, c[2] + uz * it) }
        var back = 0; var fwd = 0; var depth: Double? = null
        for (o in verticals.filter { it.plane.angleTo(sf.plane) >= 75 }) {
            val oc = o.plane.centroid
            val oe = listOf(o.uMin, o.uMax).map { doubleArrayOf(oc[0] + o.u[0] * it, oc[2] + o.u[2] * it) }
            for (a in 0..1) {
                if (ends.none { hypot(it[0] - oe[a][0], it[1] - oe[a][1]) <= 0.15 }) continue
                val far = oe[1 - a]
                val d = -((far[0] - c[0]) * nx + (far[1] - c[2]) * nz)
                if (d in 0.05..1.5) { back++; depth = max(depth ?: 0.0, d) } else if (d < -0.05) fwd++
                break
            }
        }
        val fv = HashSet<Int>()
        val idx = com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor.pointsOf(map, sf.memberVoxels)
        for (i in idx) fv.add(views.ofFrame[map.points.frame[i]])
        val det = FreeSpaceAudit.detail(map, sf)
        return Backface(
            sf.id, cols.size, if (cols.isEmpty()) 0.0 else hits.size.toDouble() / cols.size, median(hits.map { it.hitD!! }), continuity(hits.map { it.hitD!! }),
            if (cols.isEmpty()) 0.0 else freeCols.toDouble() / cols.size, if (cols.isEmpty()) 0.0 else unobsCols.toDouble() / cols.size, Integer.bitCount(seen), Integer.bitCount(hitSeen), vol,
            linkedMapOf("sinistra" to band(left), "destra" to band(right), "sopra" to band(above)),
            behind?.id, behind?.let { -offset(it) }, adj?.id, adj?.let { -offset(it) }, adjWhere,
            back, depth, fwd, fv.size, det.freeBehind, det.freeOfAll, det.observedFraction,
        )
    }

    fun overlap(a: Surface, b: Surface): Double {
        fun proj(s: Surface, t: Double): Double { val px = s.plane.centroid[0] + s.u[0] * t; val pz = s.plane.centroid[2] + s.u[2] * t; return (px - a.plane.centroid[0]) * a.u[0] + (pz - a.plane.centroid[2]) * a.u[2] }
        val b0 = proj(b, b.uMin); val b1 = proj(b, b.uMax)
        return max(0.0, min(a.uMax, max(b0, b1)) - max(a.uMin, min(b0, b1)))
    }

    private fun lateralGap(a: Surface, b: Surface): Double {
        fun proj(t: Double): Double { val px = b.plane.centroid[0] + b.u[0] * t; val pz = b.plane.centroid[2] + b.u[2] * t; return (px - a.plane.centroid[0]) * a.u[0] + (pz - a.plane.centroid[2]) * a.u[2] }
        val lo = min(proj(b.uMin), proj(b.uMax)); val hi = max(proj(b.uMin), proj(b.uMax))
        return when { hi < a.uMin -> a.uMin - hi; lo > a.uMax -> lo - a.uMax; else -> 0.0 }
    }

    // ------------------------------------------------------------------------------------------------------ contesto R3/R4

    class Context(
        val mode: String, val walls: Int, val r4State: PerimeterState, val areaM2: Double?, val role: String,
        val parallelWall: Int?, val offsetM: Double?, val side: String, val overlapM: Double?,
        val insidePolygon: Boolean?, val boundaryDistM: Double?, val continuityWall: Int?, val intersects: List<Int>, val conflict: Boolean,
        /** Parete R3 parallela, con la stessa orientazione, DIETRO la candidata (0,08–1,5 m) e accanto (gap laterale ≤ 0,5 m, senza sovrapposizione richiesta). */
        val adjacentWallBehind: Int?, val adjacentWallBehindM: Double?,
    )

    /** R2 → rimuovi la candidata dai dati → R3 → R4 (la candidata non può influenzare nessuna geometria di R3). */
    fun leaveOneOut(map: GlobalMap, s: SurfaceResult, rec: ScanRecording?, cand: Surface): Pair<WallEstimationResult, PerimeterResult> {
        val s2 = SurfaceResult(
            s.surfaces.filter { it.id != cand.id }, s.floor, s.ceilingY, s.cameraMedianY, s.voxelsWithNormal, s.voxelsAssigned, s.pointsAssigned, s.pointsTotal,
            s.arcore, s.voxelSurface, s.noiseEstimateM, s.thresholds,
        )
        val w = WallEstimator.estimate(WallEstimator.inputFrom(map, s2, rec))
        return w to PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s2, w))
    }

    fun context(mode: String, cand: Surface, w: WallEstimationResult, r4: PerimeterResult): Context {
        val (nx, nz, ux, uz) = frame(cand).let { listOf(it[0], it[1], it[2], it[3]) }
        val c = cand.plane.centroid
        val a = doubleArrayOf(c[0] + ux * cand.uMin, c[2] + uz * cand.uMin); val b = doubleArrayOf(c[0] + ux * cand.uMax, c[2] + uz * cand.uMax)
        val own = w.walls.firstOrNull { cand.id in it.sourceSurfaceIds }
        val role = own?.let { o -> "parete R3 W${o.id} (" + (r4.walls.firstOrNull { it.wallId == o.id }?.role?.label ?: "?") + ")" } ?: if (mode == "ingenua") "non usata da R3" else "rimossa prima di R3"
        var best: Triple<Int, Double, Double>? = null; var cont: Int? = null; var adjBehind: Pair<Int, Double>? = null
        val inter = mutableListOf<Int>()
        for (wl in w.walls) {
            val gm = wl.geometry
            val sdot = nx * gm.nx + nz * gm.nz; val dot = abs(sdot)
            val ua = (a[0] - gm.cx) * gm.ux + (a[1] - gm.cz) * gm.uz; val ub = (b[0] - gm.cx) * gm.ux + (b[1] - gm.cz) * gm.uz
            val lo = min(ua, ub); val hi = max(ua, ub)
            if (sdot >= 0.966) {
                val ov = min(hi, gm.endU) - max(lo, gm.startU)
                val off = (c[0] - gm.cx) * gm.nx + (c[2] - gm.cz) * gm.nz
                if (ov >= 0.3 * cand.lengthM && abs(off) <= 2.5 && (best == null || abs(off) < abs(best.second))) best = Triple(wl.id, off, ov)
                val gap = max(0.0, max(gm.startU - hi, lo - gm.endU))
                if (abs(off) <= 0.08 && gap <= 0.3 && wl !== own && cont == null) cont = wl.id
                if (off in 0.08..1.5 && gap <= 0.5 && wl !== own && (adjBehind == null || off < adjBehind.second)) adjBehind = wl.id to off
            } else if (dot <= 0.866) {
                val p = gm.pointAt(gm.startU); val q = gm.pointAt(gm.endU)
                if (properIntersect(a, b, p, q)) inter.add(wl.id)
            }
        }
        val side = when { best == null -> "nessuna parete parallela"; best.second > 0.08 -> "interno"; best.second < -0.08 -> "esterno"; else -> "complanare" }
        val poly = r4.main?.takeIf { it.state == PerimeterState.CLOSED && it.corners.size >= 3 }?.corners?.map { doubleArrayOf(it.x, it.z) }
        val inside = poly?.let { inPoly(it, c[0], c[2]) }
        val dist = poly?.let { pl -> pl.indices.minOf { segDist(c[0], c[2], pl[it], pl[(it + 1) % pl.size]) } }
        return Context(mode, w.walls.size, r4.state, r4.main?.areaM2, role, best?.first, best?.second, side, best?.third, inside, dist, cont, inter, inter.isNotEmpty() || inside == false, adjBehind?.first, adjBehind?.second)
    }

    private fun properIntersect(a: DoubleArray, b: DoubleArray, p: DoubleArray, q: DoubleArray): Boolean {
        val rx = b[0] - a[0]; val rz = b[1] - a[1]; val sx = q[0] - p[0]; val sz = q[1] - p[1]
        val den = rx * sz - rz * sx
        if (abs(den) < 1e-12) return false
        val t = ((p[0] - a[0]) * sz - (p[1] - a[1]) * sx) / den; val u = ((p[0] - a[0]) * rz - (p[1] - a[1]) * rx) / den
        val la = hypot(rx, rz); val lb = hypot(sx, sz)
        return t * la > 0.05 && (1 - t) * la > 0.05 && u * lb > 0.05 && (1 - u) * lb > 0.05
    }

    private fun inPoly(p: List<DoubleArray>, x: Double, z: Double): Boolean {
        var c = false
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % p.size]; if ((a[1] > z) != (b[1] > z) && x < (b[0] - a[0]) * (z - a[1]) / (b[1] - a[1]) + a[0]) c = !c }
        return c
    }

    private fun segDist(x: Double, z: Double, a: DoubleArray, b: DoubleArray): Double {
        val dx = b[0] - a[0]; val dz = b[1] - a[1]; val l2 = dx * dx + dz * dz
        val t = if (l2 < 1e-12) 0.0 else (((x - a[0]) * dx + (z - a[1]) * dz) / l2).coerceIn(0.0, 1.0)
        return hypot(x - a[0] - dx * t, z - a[1] - dz * t)
    }
}
