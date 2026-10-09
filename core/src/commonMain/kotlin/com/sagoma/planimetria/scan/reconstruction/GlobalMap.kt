package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.ScanRecording
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * R1 — mappa 3D globale. I punti restano quelli originali (non quantizzati): il voxel da 4 cm è solo un indice per trovarli e
 * per sommare i loro momenti. Accanto, una griglia di pianta (5 cm) e una griglia 3D dello spazio libero (10 cm), costruite
 * percorrendo i raggi camera → punto: dicono dove si è VISTO attraverso (spazio libero) e dove no (ignoto). In R1/R2 lo spazio
 * libero è solo evidenza: non è mai interpretato come apertura.
 */

/** Una depth integrata: posa, posizione della camera, gruppo di vista. */
class MapFrame(
    val index: Int,
    val depthSeq: Int,
    val use: DepthUse,
    val timestampNs: Long,
    val pose: RecPose,
    val viewGroup: Int,
    val poseFrameSeq: Int?,
)

/** Punti del mondo, in colonne (niente oggetti per punto). */
class MapPoints(
    val x: FloatArray, val y: FloatArray, val z: FloatArray,
    val weight: FloatArray, val range: FloatArray,
    /** Indice in [GlobalMap.frames]. */
    val frame: IntArray,
) {
    val size: Int get() = x.size
}

/**
 * Indice a voxel: [keys] ordinati, per ogni voxel l'intervallo [start] .. [start] + [count] in [order] (indici dei punti) e i
 * momenti (riferiti a [GlobalMap.origin]). [groups] = gruppi di vista distinti, [directions] = maschera delle direzioni di vista
 * (8 settori orizzontali × sopra/sotto).
 */
class VoxelIndex(
    val sizeM: Double,
    val keys: LongArray,
    val start: IntArray,
    val count: IntArray,
    val order: IntArray,
    val moments: Array<Moments>,
    val groups: IntArray,
    val directions: IntArray,
) {
    val size: Int get() = keys.size
    private val lookup = HashMap<Long, Int>(keys.size * 2).also { map -> for (i in keys.indices) map[keys[i]] = i }

    fun find(key: Long): Int = lookup[key] ?: -1

    companion object {
        private const val BIAS = 1 shl 20
        fun key(ix: Int, iy: Int, iz: Int): Long =
            ((ix + BIAS).toLong() shl 42) or ((iy + BIAS).toLong() shl 21) or (iz + BIAS).toLong()
        fun ix(key: Long) = ((key ushr 42) and 0x1FFFFF).toInt() - BIAS
        fun iy(key: Long) = ((key ushr 21) and 0x1FFFFF).toInt() - BIAS
        fun iz(key: Long) = (key and 0x1FFFFF).toInt() - BIAS
    }
}

/** Griglia densa allineata agli assi: celle di [cellM] da ([minX], [minY], [minZ]); [ny] = 1 per la pianta. */
class DenseGrid(val cellM: Double, val minX: Double, val minY: Double, val minZ: Double, val nx: Int, val ny: Int, val nz: Int) {
    val cells: Int get() = nx * ny * nz
    fun cx(x: Double) = floor((x - minX) / cellM).toInt()
    fun cy(y: Double) = if (ny == 1) 0 else floor((y - minY) / cellM).toInt()
    fun cz(z: Double) = floor((z - minZ) / cellM).toInt()
    fun inside(i: Int, j: Int, k: Int) = i in 0 until nx && j in 0 until ny && k in 0 until nz
    fun index(i: Int, j: Int, k: Int) = (k * ny + j) * nx + i
}

/**
 * Pianta (x, z) a celle da 5 cm: punti caduti nella cella (con peso, quota minima e massima) e raggi che l'hanno attraversata
 * senza fermarsi (spazio libero visto). Una cella con raggi e senza punti è "libera"; senza nessuno dei due è "ignota".
 */
class PlanGrid(val grid: DenseGrid) {
    val hits = IntArray(grid.cells)
    val hitWeight = FloatArray(grid.cells)
    val minY = FloatArray(grid.cells) { Float.POSITIVE_INFINITY }
    val maxY = FloatArray(grid.cells) { Float.NEGATIVE_INFINITY }
    val free = IntArray(grid.cells)
}

/** Spazio 3D a celle da 10 cm: raggi che ci sono passati (libero) e punti che ci sono caduti (occupato). */
class FreeSpace(val grid: DenseGrid) {
    val free = IntArray(grid.cells)
    val hits = IntArray(grid.cells)

    /** 1 = visto libero, 2 = occupato (anche se attraversato), 0 = ignoto, -1 = fuori dalla griglia. */
    fun state(x: Double, y: Double, z: Double): Int {
        val i = grid.cx(x); val j = grid.cy(y); val k = grid.cz(z)
        if (!grid.inside(i, j, k)) return -1
        val c = grid.index(i, j, k)
        return when {
            hits[c] > 0 -> 2
            free[c] > 0 -> 1
            else -> 0
        }
    }
}

/** Conteggi di R1 (per il report): nessun dato sparisce senza un numero che lo dica. */
data class MapStats(
    val depthKeyframes: Int,
    val framesRaw: Int,
    val framesFallback: Int,
    val framesSkipped: Int,
    val skipReasons: List<Pair<String, Int>>,
    val pixels: Long,
    val accepted: Long,
    val rejectedZero: Long,
    val rejectedRange: Long,
    val rejectedConfidence: Long,
    val rejectedEdge: Long,
    val voxels: Int,
    val pointsPerVoxelMedian: Double,
    val viewGroups: Int,
    val raysTraced: Long,
    val freeCells3d: Int,
    val occupiedCells3d: Int,
    val planHitCells: Int,
    val planFreeOnlyCells: Int,
    val planExploredM2: Double,
    val boundsMin: DoubleArray,
    val boundsMax: DoubleArray,
)

class GlobalMap(
    val frames: List<MapFrame>,
    val uses: List<FrameUse>,
    val points: MapPoints,
    val origin: DoubleArray,
    val voxels: VoxelIndex,
    val plan: PlanGrid,
    val freeSpace: FreeSpace,
    val stats: MapStats,
) {
    /** Posizione della camera del frame [i]. */
    fun camera(i: Int): DoubleArray = frames[i].pose.let { doubleArrayOf(it.x, it.y, it.z) }

    companion object {
        /** R1: dalla registrazione (e dai suoi file) alla mappa. Deterministico: stesso ingresso, stessa mappa. */
        fun build(r: ScanRecording, blobs: DatasetBlobs, p: ReconParams = ReconParams()): GlobalMap {
            val (depthFrames, skipped) = DepthFrames.select(r, blobs, p)
            val frames = assignViewGroups(depthFrames, p)

            // 1. Punti nel mondo (e i pixel accettati di ogni frame, per i raggi).
            val xs = FloatList(); val ys = FloatList(); val zs = FloatList(); val ws = FloatList(); val rs = FloatList(); val fs = IntList(1024)
            val rayU = IntList(1024); val rayV = IntList(1024)
            val uses = mutableListOf<FrameUse>()
            val rayStart = IntArray(frames.size + 1)
            for ((fi, f) in depthFrames.withIndex()) {
                rayStart[fi] = xs.size
                val use = DepthToWorld.convert(f, p) { pt ->
                    xs.add(pt.x.toFloat()); ys.add(pt.y.toFloat()); zs.add(pt.z.toFloat())
                    ws.add(pt.weight.toFloat()); rs.add(pt.rangeM.toFloat()); fs.add(fi)
                    rayU.add(pt.u); rayV.add(pt.v)
                }
                uses.add(use)
            }
            rayStart[frames.size] = xs.size
            val points = MapPoints(xs.toArray(), ys.toArray(), zs.toArray(), ws.toArray(), rs.toArray(), fs.toArray())
            val allUses = (uses + skipped).sortedBy { it.depthSeq }

            // 2. Limiti (punti + camere) e origine dei momenti.
            val lo = doubleArrayOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
            val hi = doubleArrayOf(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
            fun extend(x: Double, y: Double, z: Double) {
                lo[0] = min(lo[0], x); lo[1] = min(lo[1], y); lo[2] = min(lo[2], z)
                hi[0] = max(hi[0], x); hi[1] = max(hi[1], y); hi[2] = max(hi[2], z)
            }
            for (i in 0 until points.size) extend(points.x[i].toDouble(), points.y[i].toDouble(), points.z[i].toDouble())
            for (f in frames) extend(f.pose.x, f.pose.y, f.pose.z)
            if (points.size == 0 && frames.isEmpty()) { lo.fill(0.0); hi.fill(0.0) }
            val origin = doubleArrayOf(floor((lo[0] + hi[0]) / 2), floor((lo[1] + hi[1]) / 2), floor((lo[2] + hi[2]) / 2))

            // 3. Indice a voxel.
            val voxels = buildVoxels(points, frames, origin, p.voxelM)

            // 4. Griglie: pianta e spazio libero, percorrendo i raggi.
            val margin = 0.2
            val planGrid = PlanGrid(gridOf(lo, hi, margin, p.planCellM, flat = true))
            val free = FreeSpace(gridOf(lo, hi, margin, p.freeCellM, flat = false))
            for (i in 0 until points.size) {
                val x = points.x[i].toDouble(); val y = points.y[i].toDouble(); val z = points.z[i].toDouble()
                val g = planGrid.grid
                val ci = g.cx(x); val ck = g.cz(z)
                if (g.inside(ci, 0, ck)) {
                    val c = g.index(ci, 0, ck)
                    planGrid.hits[c]++
                    planGrid.hitWeight[c] += points.weight[i]
                    planGrid.minY[c] = min(planGrid.minY[c], points.y[i]); planGrid.maxY[c] = max(planGrid.maxY[c], points.y[i])
                }
                val fg = free.grid
                val fi = fg.cx(x); val fj = fg.cy(y); val fk = fg.cz(z)
                if (fg.inside(fi, fj, fk)) free.hits[fg.index(fi, fj, fk)]++
            }
            var rays = 0L
            for ((fi, f) in frames.withIndex()) {
                for (i in rayStart[fi] until rayStart[fi + 1]) {
                    if (rayU[i] % p.freeRayStride != 0 || rayV[i] % p.freeRayStride != 0) continue
                    val px = points.x[i].toDouble(); val py = points.y[i].toDouble(); val pz = points.z[i].toDouble()
                    traverse3d(free, f.pose.x, f.pose.y, f.pose.z, px, py, pz)
                    traversePlan(planGrid, f.pose.x, f.pose.z, px, pz)
                    rays++
                }
            }

            val counts = DoubleArray(voxels.size) { voxels.count[it].toDouble() }
            val stats = MapStats(
                depthKeyframes = allUses.size,
                framesRaw = uses.count { it.use == DepthUse.RAW },
                framesFallback = uses.count { it.use == DepthUse.FILTERED_FALLBACK },
                framesSkipped = skipped.size,
                skipReasons = skipped.groupBy { it.skipReason ?: "?" }.map { it.key to it.value.size }.sortedBy { it.first },
                pixels = uses.sumOf { it.pixels.toLong() },
                accepted = uses.sumOf { it.accepted.toLong() },
                rejectedZero = uses.sumOf { it.rejectedZero.toLong() },
                rejectedRange = uses.sumOf { it.rejectedRange.toLong() },
                rejectedConfidence = uses.sumOf { it.rejectedConfidence.toLong() },
                rejectedEdge = uses.sumOf { it.rejectedEdge.toLong() },
                voxels = voxels.size,
                pointsPerVoxelMedian = if (counts.isEmpty()) 0.0 else Geo.median(counts),
                viewGroups = frames.map { it.viewGroup }.distinct().size,
                raysTraced = rays,
                freeCells3d = (0 until free.grid.cells).count { free.free[it] > 0 && free.hits[it] == 0 },
                occupiedCells3d = free.hits.count { it > 0 },
                planHitCells = planGrid.hits.count { it > 0 },
                planFreeOnlyCells = (0 until planGrid.grid.cells).count { planGrid.free[it] > 0 && planGrid.hits[it] == 0 },
                planExploredM2 = (0 until planGrid.grid.cells).count { planGrid.free[it] > 0 || planGrid.hits[it] > 0 } * p.planCellM * p.planCellM,
                boundsMin = lo, boundsMax = hi,
            )
            return GlobalMap(frames, allUses, points, origin, voxels, planGrid, free, stats)
        }

        /** Gruppi di vista: frame vicini nel tempo e nella posa contano come una sola osservazione indipendente. */
        private fun assignViewGroups(frames: List<DepthFrame>, p: ReconParams): List<MapFrame> {
            val out = ArrayList<MapFrame>(frames.size)
            var group = -1
            var ref: DepthFrame? = null
            for ((i, f) in frames.withIndex()) {
                val r = ref
                val newGroup = r == null || (f.timestampNs - r.timestampNs) >= p.viewGroupMs * 1_000_000 ||
                    distance(r.pose, f.pose) >= p.viewGroupMoveM || turnDeg(r.pose, f.pose) >= p.viewGroupTurnDeg
                if (newGroup) { group++; ref = f }
                out.add(MapFrame(i, f.depthSeq, f.use, f.timestampNs, f.pose, group, f.poseFrameSeq))
            }
            return out
        }

        private fun distance(a: RecPose, b: RecPose): Double {
            val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        private fun turnDeg(a: RecPose, b: RecPose): Double {
            val fa = DepthToWorld.viewDirection(a)
            val fb = DepthToWorld.viewDirection(b)
            return Geo.angleDeg(fa[0] * fb[0] + fa[1] * fb[1] + fa[2] * fb[2])
        }

        private fun gridOf(lo: DoubleArray, hi: DoubleArray, margin: Double, cell: Double, flat: Boolean): DenseGrid {
            // Limite di sicurezza: una scena di più di 40 m per lato è un errore dei dati, non una stanza.
            val span = DoubleArray(3) { min(40.0, hi[it] - lo[it] + 2 * margin) }
            val nx = max(1, kotlin.math.ceil(span[0] / cell).toInt())
            val ny = if (flat) 1 else max(1, kotlin.math.ceil(span[1] / cell).toInt())
            val nz = max(1, kotlin.math.ceil(span[2] / cell).toInt())
            return DenseGrid(cell, lo[0] - margin, lo[1] - margin, lo[2] - margin, nx, ny, nz)
        }

        private fun buildVoxels(points: MapPoints, frames: List<MapFrame>, origin: DoubleArray, size: Double): VoxelIndex {
            val n = points.size
            val keyOf = LongArray(n) { i ->
                VoxelIndex.key(floor(points.x[i] / size).toInt(), floor(points.y[i] / size).toInt(), floor(points.z[i] / size).toInt())
            }
            // Ordine stabile: per voxel, poi per indice del punto (cioè per frame e pixel).
            val order = (0 until n).sortedWith { a, b -> keyOf[a].compareTo(keyOf[b]).let { if (it != 0) it else a.compareTo(b) } }.toIntArray()
            val keys = mutableListOf<Long>()
            val starts = IntList(); val counts = IntList()
            var i = 0
            while (i < n) {
                val k = keyOf[order[i]]
                var j = i
                while (j < n && keyOf[order[j]] == k) j++
                keys.add(k); starts.add(i); counts.add(j - i)
                i = j
            }
            val m = keys.size
            val start = starts.toArray(); val count = counts.toArray()
            val moments = Array(m) { Moments(origin[0], origin[1], origin[2]) }
            val groups = IntArray(m)
            val dirs = IntArray(m)
            for (v in 0 until m) {
                var lastGroup = -1
                val seen = HashSet<Int>()
                for (t in start[v] until start[v] + count[v]) {
                    val pi = order[t]
                    val x = points.x[pi].toDouble(); val y = points.y[pi].toDouble(); val z = points.z[pi].toDouble()
                    moments[v].add(x, y, z, points.weight[pi].toDouble())
                    val f = frames[points.frame[pi]]
                    if (f.viewGroup != lastGroup && seen.add(f.viewGroup)) lastGroup = f.viewGroup
                    dirs[v] = dirs[v] or directionBit(x - f.pose.x, y - f.pose.y, z - f.pose.z)
                }
                groups[v] = seen.size
            }
            return VoxelIndex(size, keys.toLongArray(), start, count, order, moments, groups, dirs)
        }

        /** 8 settori orizzontali di 45° × (dall'alto / dal basso). */
        private fun directionBit(dx: Double, dy: Double, dz: Double): Int {
            val a = (atan2(dz, dx) + kotlin.math.PI) / (2 * kotlin.math.PI) // 0..1
            val sector = min(7, (a * 8).toInt())
            return 1 shl (sector + if (dy < 0) 0 else 8)
        }

        /** Raggio camera → punto nella griglia 3D (Amanatides-Woo): celle attraversate prima dell'ultima, che è occupata. */
        private fun traverse3d(fs: FreeSpace, ox: Double, oy: Double, oz: Double, px: Double, py: Double, pz: Double) {
            val g = fs.grid
            var i = g.cx(ox); var j = g.cy(oy); var k = g.cz(oz)
            val ei = g.cx(px); val ej = g.cy(py); val ek = g.cz(pz)
            val dx = px - ox; val dy = py - oy; val dz = pz - oz
            val si = if (dx > 0) 1 else -1; val sj = if (dy > 0) 1 else -1; val sk = if (dz > 0) 1 else -1
            fun next(c: Int, s: Int, o: Double, min: Double, d: Double): Double =
                if (abs(d) < 1e-12) Double.MAX_VALUE else ((min + (c + (if (s > 0) 1 else 0)) * g.cellM) - o) / d
            var tx = next(i, si, ox, g.minX, dx); var ty = next(j, sj, oy, g.minY, dy); var tz = next(k, sk, oz, g.minZ, dz)
            val ddx = if (abs(dx) < 1e-12) Double.MAX_VALUE else g.cellM / abs(dx)
            val ddy = if (abs(dy) < 1e-12) Double.MAX_VALUE else g.cellM / abs(dy)
            val ddz = if (abs(dz) < 1e-12) Double.MAX_VALUE else g.cellM / abs(dz)
            var steps = 0
            while (!(i == ei && j == ej && k == ek) && steps < 2000) {
                if (g.inside(i, j, k)) fs.free[g.index(i, j, k)]++
                if (tx <= ty && tx <= tz) { i += si; tx += ddx } else if (ty <= tz) { j += sj; ty += ddy } else { k += sk; tz += ddz }
                if (tx > 1 && ty > 1 && tz > 1) break
                steps++
            }
        }

        private fun traversePlan(pg: PlanGrid, ox: Double, oz: Double, px: Double, pz: Double) {
            val g = pg.grid
            var i = g.cx(ox); var k = g.cz(oz)
            val ei = g.cx(px); val ek = g.cz(pz)
            val dx = px - ox; val dz = pz - oz
            val si = if (dx > 0) 1 else -1; val sk = if (dz > 0) 1 else -1
            var tx = if (abs(dx) < 1e-12) Double.MAX_VALUE else ((g.minX + (i + (if (si > 0) 1 else 0)) * g.cellM) - ox) / dx
            var tz = if (abs(dz) < 1e-12) Double.MAX_VALUE else ((g.minZ + (k + (if (sk > 0) 1 else 0)) * g.cellM) - oz) / dz
            val ddx = if (abs(dx) < 1e-12) Double.MAX_VALUE else g.cellM / abs(dx)
            val ddz = if (abs(dz) < 1e-12) Double.MAX_VALUE else g.cellM / abs(dz)
            var steps = 0
            while (!(i == ei && k == ek) && steps < 4000) {
                if (g.inside(i, 0, k)) pg.free[g.index(i, 0, k)]++
                if (tx <= tz) { i += si; tx += ddx } else { k += sk; tz += ddz }
                if (tx > 1 && tz > 1) break
                steps++
            }
        }
    }
}
