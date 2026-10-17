package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.ArCameraProjection
import com.sagoma.planimetria.scan.recording.ScanRecording
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * SOLO DIAGNOSI (jvmTest, mai nella pipeline): tracciamento quantitativo dei punti vicino agli angoli, da R1 a R2.
 *
 *   angolo noto → pixel di depth (raw) → punti accettati da R1 → voxel da 4 cm → normali e region growing di R2 → superficie
 *   della parete A / della parete B / altro / scartato → primo punto della pipeline in cui l'evidenza si perde.
 *
 * R2 viene RIFATTO in memoria con le stesse formule e le soglie effettive esposte dalla produzione ([SurfaceResult.thresholds]),
 * registrando ogni tentativo di crescita sui voxel vicini agli angoli. La fedeltà è verificata voxel per voxel contro
 * [SurfaceResult.voxelSurface]: se la replica non coincide, la diagnosi non vale (lo dice [Trace.mismatches]).
 * Nessuna soglia, nessun algoritmo e nessuna classificazione di produzione vengono cambiati.
 */
object R2CornerDiagnostic {
    enum class Loss { VOXELIZATION, REGION_GROWING, NORMAL_THRESHOLD, DISTANCE_THRESHOLD, SURFACE_CONFLICT, ASSIGNMENT_ORDER, ROBUST_FIT, OBJECT_CLASSIFICATION, UNKNOWN, INSUFFICIENT_EVIDENCE, OTHER }
    enum class CornerClass { SYNTHETIC_GROUND_TRUTH, R3_INFERRED, PROBABLE_PHYSICAL, UNCERTAIN }

    /** Parete in pianta: n·p = d (n orizzontale unitaria), direzione ([ax], [az]) dall'angolo lungo la parete, superfici R2 che la rappresentano. */
    class Wall(val name: String, val nx: Double, val nz: Double, val d: Double, val ax: Double, val az: Double, val surfaces: Set<Int>) {
        fun dist(x: Double, z: Double) = nx * x + nz * z - d
    }

    class Corner(
        val dataset: String, val name: String, val cls: CornerClass, val x: Double, val z: Double, val a: Wall, val b: Wall,
        val yLo: Double, val yHi: Double, val tol: Double, val note: String,
    ) {
        fun along(w: Wall, px: Double, pz: Double) = (px - x) * w.ax + (pz - z) * w.az
        fun radius(px: Double, pz: Double) = sqrt((px - x) * (px - x) + (pz - z) * (pz - z))
        /** 0 = nessuna delle due, 1 = A, 2 = B, 3 = entrambe (zona d'angolo non attribuibile). */
        fun side(px: Double, pz: Double): Int {
            val onA = abs(a.dist(px, pz)) <= tol && along(a, px, pz) >= -tol
            val onB = abs(b.dist(px, pz)) <= tol && along(b, px, pz) >= -tol
            return (if (onA) 1 else 0) + (if (onB) 2 else 0)
        }
    }

    val radii = doubleArrayOf(0.02, 0.04, 0.06, 0.08, 0.10, 0.15)
    private const val ZONE = 0.15

    // ------------------------------------------------------------------------------------------------ replica tracciata di R2

    class Attempt(val region: Int, val reason: String, val angleDeg: Double, val distM: Double, val distLimitM: Double, val variation: Double)

    class Trace(
        val normals: Array<PlaneFit?>,
        val region: IntArray,
        /** Regione grezza → id della superficie di produzione (−1 = regione scartata: piccola o area insufficiente). */
        val regionSurface: IntArray,
        val attempts: Map<Int, List<Attempt>>,
        val mismatches: Int,
        val th: Thresholds,
    )

    private fun neighborsOf(vx: VoxelIndex, v: Int, radius: Int = 1): IntArray {
        val k = vx.keys[v]
        val x = VoxelIndex.ix(k); val y = VoxelIndex.iy(k); val z = VoxelIndex.iz(k)
        val out = IntList(26)
        for (dx in -radius..radius) for (dy in -radius..radius) for (dz in -radius..radius) {
            if (dx == 0 && dy == 0 && dz == 0) continue
            val u = vx.find(VoxelIndex.key(x + dx, y + dy, z + dz)); if (u >= 0) out.add(u)
        }
        return out.toArray()
    }

    /** Normali dei voxel come in R2 (vicinato di raggio [radius] voxel, punti originali). */
    fun normals(map: GlobalMap, radius: Int, minPoints: Int): Array<PlaneFit?> {
        val vx = map.voxels
        return Array(vx.size) { v ->
            val acc = Moments(map.origin[0], map.origin[1], map.origin[2])
            acc.addAll(vx.moments[v])
            for (u in neighborsOf(vx, v, radius)) acc.addAll(vx.moments[u])
            if (acc.n >= minPoints) acc.plane() else null
        }
    }

    fun trace(map: GlobalMap, s: SurfaceResult, watch: BooleanArray, p: SurfaceParams = SurfaceParams()): Trace {
        val vx = map.voxels
        val m = vx.size
        val nbr = Array(m) { neighborsOf(vx, it) }
        val normals = normals(map, p.normalRadius, p.minNeighborhoodPoints)
        val own = Array(m) { vx.moments[it].centroid() }
        val th = s.thresholds
        val region = IntArray(m) { -1 }
        val attempts = HashMap<Int, MutableList<Attempt>>()
        fun log(u: Int, a: Attempt) { if (watch[u]) attempts.getOrPut(u) { mutableListOf() }.let { l -> if (l.none { it.region == a.region }) l.add(a) } }
        val seeds = (0 until m).filter { normals[it] != null && normals[it]!!.surfaceVariation <= th.seedMaxVariation }
            .sortedWith(compareBy({ normals[it]!!.surfaceVariation }, { vx.keys[it] }))
        val regions = mutableListOf<MutableList<Int>>()
        for (sd in seeds) {
            if (region[sd] >= 0) continue
            val id = regions.size
            val members = mutableListOf(sd)
            region[sd] = id
            val acc = Moments(map.origin[0], map.origin[1], map.origin[2]).apply { addAll(vx.moments[sd]) }
            var plane = normals[sd]!!
            var refitAt = 8
            val queue = ArrayDeque<Int>().apply { add(sd) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                for (u in nbr[cur]) {
                    if (region[u] >= 0) { if (region[u] != id) log(u, Attempt(id, "taken:${region[u]}", 0.0, 0.0, 0.0, 0.0)); continue }
                    val nu = normals[u]
                    if (nu == null) { log(u, Attempt(id, "no-normal", 0.0, 0.0, 0.0, 0.0)); continue }
                    val ang = plane.angleTo(nu)
                    val c = own[u]
                    val dist = abs(plane.distance(c[0], c[1], c[2]))
                    val lim = max(th.growDistM, 2.5 * plane.rms)
                    if (nu.surfaceVariation > th.growMaxVariation) { log(u, Attempt(id, "variation", ang, dist, lim, nu.surfaceVariation)); continue }
                    if (ang > th.growAngleDeg) { log(u, Attempt(id, "angle", ang, dist, lim, nu.surfaceVariation)); continue }
                    if (dist > lim) { log(u, Attempt(id, "distance", ang, dist, lim, nu.surfaceVariation)); continue }
                    region[u] = id
                    members.add(u)
                    acc.addAll(vx.moments[u])
                    queue.add(u)
                    if (members.size >= refitAt) { acc.plane()?.let { plane = it }; refitAt *= 2 }
                }
            }
            regions.add(members)
        }
        // Fusione come in R2.
        val parent = IntArray(regions.size) { it }
        fun root(a: Int): Int { var r = a; while (parent[r] != r) r = parent[r]; var x = a; while (parent[x] != r) { val nx = parent[x]; parent[x] = r; x = nx }; return r }
        val acc = Array(regions.size) { i -> Moments(map.origin[0], map.origin[1], map.origin[2]).also { mm -> for (v in regions[i]) mm.addAll(vx.moments[v]) } }
        val pairs = HashSet<Long>()
        for (v in 0 until m) { val a = region[v]; if (a < 0) continue; for (u in nbr[v]) { val b = region[u]; if (b > a) pairs.add((a.toLong() shl 32) or b.toLong()) } }
        var changed = true
        val pairList = pairs.sorted()
        while (changed) {
            changed = false
            for (pair in pairList) {
                val a = root((pair ushr 32).toInt()); val b = root((pair and 0xFFFFFFFFL).toInt())
                if (a == b) continue
                val pa = acc[a].plane() ?: continue
                val pb = acc[b].plane() ?: continue
                if (pa.angleTo(pb) > th.mergeAngleDeg) continue
                if (abs(pa.distance(pb.centroid[0], pb.centroid[1], pb.centroid[2])) > th.mergeDistM) continue
                if (abs(pb.distance(pa.centroid[0], pa.centroid[1], pa.centroid[2])) > th.mergeDistM) continue
                val keep = min(a, b); val drop = max(a, b)
                parent[drop] = keep
                acc[keep].addAll(acc[drop])
                changed = true
            }
        }
        val merged = HashMap<Int, MutableList<Int>>()
        for ((i, g) in regions.withIndex()) merged.getOrPut(root(i)) { mutableListOf() }.addAll(g)
        // Gruppo → superficie di produzione (stessi voxel).
        val bySet = HashMap<Int, MutableList<Surface>>()
        for (sf in s.surfaces) bySet.getOrPut(sf.memberVoxels.contentHashCode()) { mutableListOf() }.add(sf)
        val rootSurface = HashMap<Int, Int>()
        for (r in merged.keys) {
            val g = merged.getValue(r).sorted().toIntArray()
            rootSurface[r] = bySet[g.contentHashCode()]?.firstOrNull { it.memberVoxels.contentEquals(g) }?.id ?: -1
        }
        val regionSurface = IntArray(regions.size) { rootSurface.getValue(root(it)) }
        var mismatches = 0
        for (v in 0 until m) { val mine = if (region[v] >= 0) regionSurface[region[v]] else -1; if (mine != s.voxelSurface[v]) mismatches++ }
        return Trace(normals, region, regionSurface, attempts, mismatches, th)
    }

    // ------------------------------------------------------------------------------------------------------------ pixel raw

    /** Pixel di depth con profondità > 0 vicino agli angoli, con l'esito dei filtri di R1 (replicati: distanza, confidenza, bordi). */
    class RawPixel(val x: Double, val y: Double, val z: Double, val status: String)

    fun rawPixels(r: ScanRecording, blobs: DatasetBlobs, corners: List<Corner>, p: ReconParams = ReconParams()): List<RawPixel> {
        val (frames, _) = DepthFrames.select(r, blobs, p)
        val out = mutableListOf<RawPixel>()
        for (f in frames) {
            val w = f.k.width; val h = f.k.height
            for (v in 0 until h) for (u in 0 until w) {
                val mm = f.mm[v * w + u]
                if (mm <= 0) continue
                val zz = mm / 1000.0
                val q = ArCameraProjection.unproject(f.pose, f.k, u.toDouble(), v.toDouble(), zz)
                if (corners.none { c -> c.radius(q[0], q[2]) <= ZONE && q[1] in c.yLo..c.yHi }) continue
                val status = when {
                    zz < p.minRangeM || zz > p.maxRangeM -> "R1_RANGE"
                    f.confidence != null && (f.confidence[v * w + u].toInt() and 0xFF) < p.minRawConfidence -> "R1_CONFIDENCE"
                    isEdge(f.mm, w, h, u, v, mm, p.edgeJumpRatio) -> "R1_EDGE"
                    else -> "ACCEPTED"
                }
                out.add(RawPixel(q[0], q[1], q[2], status))
            }
        }
        return out
    }

    private fun isEdge(mm: IntArray, w: Int, h: Int, u: Int, v: Int, d: Int, ratio: Double): Boolean {
        val limit = d * ratio
        fun jump(uu: Int, vv: Int): Boolean { if (uu < 0 || vv < 0 || uu >= w || vv >= h) return false; val n = mm[vv * w + uu]; return n > 0 && abs(n - d) > limit }
        return jump(u - 1, v) || jump(u + 1, v) || jump(u, v - 1) || jump(u, v + 1)
    }

    // ------------------------------------------------------------------------------------------------------- classificazione

    class PointLoss(val loss: Loss, val detail: String)

    private fun voxelOf(map: GlobalMap, i: Int): Int {
        val sz = map.voxels.sizeM
        return map.voxels.find(VoxelIndex.key(floor(map.points.x[i] / sz).toInt(), floor(map.points.y[i] / sz).toInt(), floor(map.points.z[i] / sz).toInt()))
    }

    /** Il piano principale (superficie più grande) che rappresenta la parete. */
    private fun mainPlane(s: SurfaceResult, w: Wall): Surface? = s.surfaces.filter { it.id in w.surfaces }.maxByOrNull { it.samples }

    /** Le soglie di crescita applicate al voxel [v] rispetto al piano finale della parete [w]: null se le supererebbe tutte. */
    private fun wouldFail(map: GlobalMap, t: Trace, s: SurfaceResult, w: Wall, v: Int): PointLoss? {
        val sf = mainPlane(s, w) ?: return PointLoss(Loss.OTHER, "parete senza superficie R2")
        val n = t.normals[v] ?: return PointLoss(Loss.INSUFFICIENT_EVIDENCE, "vicinato senza abbastanza punti per la normale")
        if (n.surfaceVariation > t.th.growMaxVariation) return PointLoss(Loss.NORMAL_THRESHOLD, "planarità: variazione ${f(n.surfaceVariation, 3)} > ${f(t.th.growMaxVariation, 3)}")
        val ang = sf.plane.angleTo(n)
        if (ang > t.th.growAngleDeg) return PointLoss(Loss.NORMAL_THRESHOLD, "angolo normale ${f(ang, 1)}° > ${f(t.th.growAngleDeg, 1)}°")
        val c = map.voxels.moments[v].centroid()
        val dist = abs(sf.plane.distance(c[0], c[1], c[2])); val lim = max(t.th.growDistM, 2.5 * sf.plane.rms)
        if (dist > lim) return PointLoss(Loss.DISTANCE_THRESHOLD, "centroide del voxel a ${f(dist * 100, 1)} cm > ${f(lim * 100, 1)} cm")
        return null
    }

    /** Primo punto di perdita di un punto della parete [w] nel voxel [v] (null = assegnato alla parete). */
    fun lossOf(map: GlobalMap, t: Trace, s: SurfaceResult, c: Corner, w: Wall, v: Int, mixed: Boolean): PointLoss? {
        val sid = s.voxelSurface[v]
        if (sid in w.surfaces) return null
        val other = if (w === c.a) c.b else c.a
        if (sid >= 0) {
            val sf = s.surfaces[sid]
            if (mixed && sid in other.surfaces) return PointLoss(Loss.VOXELIZATION, "voxel da 4 cm misto ${c.a.name}/${c.b.name}, assegnato per intero a ${other.name} (S$sid)")
            return when (sf.kind) {
                SurfaceKind.OBJECT, SurfaceKind.VERTICAL_OBJECT -> PointLoss(Loss.OBJECT_CLASSIFICATION, "nella superficie S$sid classificata ${sf.kind}")
                SurfaceKind.UNKNOWN -> PointLoss(Loss.UNKNOWN, "nella superficie S$sid classificata UNKNOWN")
                else -> PointLoss(Loss.SURFACE_CONFLICT, "il voxel è stato preso da S$sid (${sf.kind})" + if (sid in other.surfaces) " = ${other.name}" else "")
            }
        }
        if (t.normals[v] == null) return PointLoss(Loss.INSUFFICIENT_EVIDENCE, "vicinato senza abbastanza punti per la normale")
        val r = t.region[v]
        if (r >= 0) {
            // In una regione poi scartata (piccola): la parete l'avrebbe accettato?
            val fail = wouldFail(map, t, s, w, v) ?: return PointLoss(Loss.ASSIGNMENT_ORDER, "preso per primo dalla regione $r, poi scartata (piccola); ${w.name} l'avrebbe accettato")
            return PointLoss(fail.loss, "in una regione scartata; rispetto a ${w.name}: ${fail.detail}")
        }
        val att = t.attempts[v].orEmpty().filter { a -> a.region >= 0 && t.regionSurface[a.region] in w.surfaces }
        val first = att.firstOrNull()
        if (first != null) return when (first.reason) {
            "variation" -> PointLoss(Loss.NORMAL_THRESHOLD, "planarità del vicinato: variazione ${f(first.variation, 3)} > ${f(t.th.growMaxVariation, 3)} (crescita di ${w.name})")
            "angle" -> PointLoss(Loss.NORMAL_THRESHOLD, "angolo della normale ${f(first.angleDeg, 1)}° > ${f(t.th.growAngleDeg, 1)}° (crescita di ${w.name})")
            "distance" -> PointLoss(Loss.DISTANCE_THRESHOLD, "centroide a ${f(first.distM * 100, 1)} cm > ${f(first.distLimitM * 100, 1)} cm (crescita di ${w.name})")
            "no-normal" -> PointLoss(Loss.INSUFFICIENT_EVIDENCE, "vicinato senza abbastanza punti")
            else -> PointLoss(Loss.OTHER, first.reason)
        }
        val would = wouldFail(map, t, s, w, v)
        return PointLoss(Loss.REGION_GROWING, "mai raggiunto dalla crescita di ${w.name}" + (would?.let { "; comunque fuori soglia: ${it.detail}" } ?: "; sarebbe dentro le soglie"))
    }

    // ------------------------------------------------------------------------------------------------------------- analisi

    class RadiusRow(val radius: Double, val raw: Int, val accepted: Int, val voxels: Int, val wallA: Int, val wallB: Int, val obj: Int, val unknown: Int, val otherSurface: Int, val discarded: Int, val ambiguous: Int)
    class LossRow(val wall: String, val loss: Loss, val count: Int, val pctOfLost: Double, val pctOfWall: Double, val reasons: List<Pair<String, Int>>)
    class WallProfile(val wall: String, val r1Points: Int, val r2Assigned: Int, val firstAssignedAlongM: Double?, val firstR1AlongM: Double?)
    class Result(
        val corner: Corner, val rows: List<RadiusRow>, val losses: List<LossRow>, val profiles: List<WallProfile>,
        val voxelSamples: List<String>, val normalProfile: List<String>, val points: List<DoubleArray>, val pointClass: List<String>,
    )

    fun analyze(map: GlobalMap, s: SurfaceResult, t: Trace, raw: List<RawPixel>, c: Corner): Result {
        val pts = map.points
        val inZone = (0 until pts.size).filter { i ->
            val x = pts.x[i].toDouble(); val z = pts.z[i].toDouble(); val y = pts.y[i].toDouble()
            y in c.yLo..c.yHi && c.radius(x, z) <= ZONE
        }
        val vox = inZone.associateWith { voxelOf(map, it) }
        // Voxel misti: contengono punti di A e di B (non solo della zona ambigua).
        val voxSides = HashMap<Int, Int>()
        for (i in inZone) { val sd = c.side(pts.x[i].toDouble(), pts.z[i].toDouble()); if (sd == 1 || sd == 2) voxSides[vox.getValue(i)] = (voxSides[vox.getValue(i)] ?: 0) or sd }
        val rows = radii.map { r ->
            val sel = inZone.filter { c.radius(pts.x[it].toDouble(), pts.z[it].toDouble()) <= r }
            fun cnt(pred: (Int) -> Boolean) = sel.count { i -> pred(s.voxelSurface[vox.getValue(i)]) }
            RadiusRow(
                r, raw.count { it.y in c.yLo..c.yHi && c.radius(it.x, it.z) <= r }, sel.size, sel.map { vox.getValue(it) }.distinct().size,
                cnt { it in c.a.surfaces }, cnt { it in c.b.surfaces },
                cnt { it >= 0 && it !in c.a.surfaces && it !in c.b.surfaces && s.surfaces[it].kind in setOf(SurfaceKind.OBJECT, SurfaceKind.VERTICAL_OBJECT) },
                cnt { it >= 0 && it !in c.a.surfaces && it !in c.b.surfaces && s.surfaces[it].kind == SurfaceKind.UNKNOWN },
                cnt { it >= 0 && it !in c.a.surfaces && it !in c.b.surfaces && s.surfaces[it].kind in setOf(SurfaceKind.VERTICAL_STRUCTURAL, SurfaceKind.FLOOR, SurfaceKind.CEILING) },
                cnt { it < 0 },
                sel.count { c.side(pts.x[it].toDouble(), pts.z[it].toDouble()) == 3 },
            )
        }
        // Perdite per parete: punti della parete (lato 1 o 2) non nella sua superficie, più i pixel scartati da R1.
        val losses = mutableListOf<LossRow>()
        val pointClass = ArrayList<String>()
        val pointsOut = ArrayList<DoubleArray>()
        val profiles = mutableListOf<WallProfile>()
        for ((w, sideCode) in listOf(c.a to 1, c.b to 2)) {
            val mine = inZone.filter { c.side(pts.x[it].toDouble(), pts.z[it].toDouble()) == sideCode }
            val byLoss = HashMap<Loss, MutableList<String>>()
            var assigned = 0
            for (i in mine) {
                val v = vox.getValue(i)
                val l = lossOf(map, t, s, c, w, v, voxSides[v] == 3)
                if (l == null) assigned++ else byLoss.getOrPut(l.loss) { mutableListOf() }.add(l.detail)
            }
            val r1Lost = raw.filter { it.status != "ACCEPTED" && it.y in c.yLo..c.yHi && c.radius(it.x, it.z) <= ZONE && c.side(it.x, it.z) == sideCode }
            for (rp in r1Lost) byLoss.getOrPut(Loss.OTHER) { mutableListOf() }.add("R1: pixel scartato (${rp.status})")
            val total = mine.size + r1Lost.size
            val lost = total - assigned
            for (k in Loss.entries) {
                val l = byLoss[k] ?: continue
                val reasons = l.groupingBy { it.replace(Regex("[0-9]+([.,][0-9]+)?"), "#") }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }
                losses.add(LossRow(w.name, k, l.size, if (lost == 0) 0.0 else l.size * 100.0 / lost, if (total == 0) 0.0 else l.size * 100.0 / total, reasons))
            }
            val alongAssigned = mine.filter { s.voxelSurface[vox.getValue(it)] in w.surfaces }.map { c.along(w, pts.x[it].toDouble(), pts.z[it].toDouble()) }
            val alongR1 = mine.map { c.along(w, pts.x[it].toDouble(), pts.z[it].toDouble()) }
            profiles.add(WallProfile(w.name, mine.size, assigned, alongAssigned.minOrNull(), alongR1.minOrNull()))
        }
        // Punti per la vista: stato di ogni punto della zona.
        for (i in inZone) {
            val x = pts.x[i].toDouble(); val z = pts.z[i].toDouble(); val v = vox.getValue(i)
            val sd = c.side(x, z)
            val sid = s.voxelSurface[v]
            val cls = when {
                sid in c.a.surfaces -> "A"
                sid in c.b.surfaces -> "B"
                sd == 1 -> lossOf(map, t, s, c, c.a, v, voxSides[v] == 3)!!.loss.name
                sd == 2 -> lossOf(map, t, s, c, c.b, v, voxSides[v] == 3)!!.loss.name
                sid >= 0 -> "OTHER_SURFACE"
                sd == 3 -> "AMBIGUOUS"
                else -> "NONE"
            }
            pointsOut.add(doubleArrayOf(x, z)); pointClass.add(cls)
        }
        for (rp in raw) if (rp.status != "ACCEPTED" && rp.y in c.yLo..c.yHi && c.radius(rp.x, rp.z) <= ZONE) { pointsOut.add(doubleArrayOf(rp.x, rp.z)); pointClass.add(rp.status) }
        // Campione dei voxel entro 10 cm: stato, superficie, normale, distanze, tentativi di crescita.
        val voxSamples = inZone.map { vox.getValue(it) }.distinct().sortedBy { v -> map.voxels.keys[v] }.filter { v ->
            val ce = map.voxels.moments[v].centroid(); c.radius(ce[0], ce[2]) <= 0.10
        }.map { v ->
            val ce = map.voxels.moments[v].centroid()
            val n = t.normals[v]
            val sid = s.voxelSurface[v]
            val att = t.attempts[v].orEmpty().joinToString("|") { a -> "R${a.region}${if (a.region >= 0 && t.regionSurface[a.region] >= 0) "(S${t.regionSurface[a.region]})" else ""}:${a.reason}" + if (a.reason == "angle" || a.reason == "distance" || a.reason == "variation") "[${f(a.angleDeg, 1)}°,${f(a.distM * 100, 1)}cm,v${f(a.variation, 3)}]" else "" }
            listOf(
                VoxelIndex.ix(map.voxels.keys[v]), VoxelIndex.iy(map.voxels.keys[v]), VoxelIndex.iz(map.voxels.keys[v]), f(ce[0], 3), f(ce[1], 3), f(ce[2], 3), map.voxels.count[v],
                f(c.radius(ce[0], ce[2]), 3), f(c.a.dist(ce[0], ce[2]), 3), f(c.b.dist(ce[0], ce[2]), 3),
                n?.let { "${f(it.nx, 3)}/${f(it.ny, 3)}/${f(it.nz, 3)}" } ?: "-", n?.let { f(it.surfaceVariation, 3) } ?: "-",
                n?.let { f(Geo.angleDeg(abs(it.nx * c.a.nx + it.nz * c.a.nz)), 1) } ?: "-", n?.let { f(Geo.angleDeg(abs(it.nx * c.b.nx + it.nz * c.b.nz)), 1) } ?: "-",
                if (sid >= 0) "S$sid:${s.surfaces[sid].kind}" else "-", t.region[v], att.ifEmpty { "-" },
            ).joinToString(";")
        }
        // Normale stimata vs ideale, per distanza dall'angolo lungo la parete (voxel con soli punti della parete).
        val normalProfile = mutableListOf<String>()
        val bins = doubleArrayOf(0.0, 0.02, 0.04, 0.06, 0.08, 0.10, 0.15)
        for ((w, code) in listOf(c.a to 1, c.b to 2)) for (k in 0 until bins.size - 1) {
            val vs = voxSides.filter { it.value == code }.keys.filter { v ->
                val ce = map.voxels.moments[v].centroid(); val al = c.along(w, ce[0], ce[2]); al >= bins[k] && al < bins[k + 1]
            }.sorted()
            val withN = vs.mapNotNull { t.normals[it] }
            if (vs.isEmpty()) continue
            val angles = withN.map { Geo.angleDeg(abs(it.nx * w.nx + it.nz * w.nz)) }
            normalProfile.add(
                "${w.name};${f(bins[k] * 100, 0)}-${f(bins[k + 1] * 100, 0)} cm;voxel ${vs.size};normale ${withN.size};angolo medio ${if (angles.isEmpty()) "-" else f(angles.average(), 1)}°;" +
                    "oltre soglia angolo ${angles.count { it > t.th.growAngleDeg }};variazione media ${if (withN.isEmpty()) "-" else f(withN.map { it.surfaceVariation }.average(), 3)};" +
                    "oltre soglia planarità ${withN.count { it.surfaceVariation > t.th.growMaxVariation }};assegnati ${vs.count { s.voxelSurface[it] in w.surfaces }}",
            )
        }
        return Result(c, rows, losses, profiles, voxSamples, normalProfile, pointsOut, pointClass)
    }

    // ------------------------------------------------------------------------------------------ esperimenti (solo in memoria)

    /** Superfici di [s] che rappresentano la parete [w] vicino all'angolo: verticali, normale entro 10°, piano entro la tolleranza nell'angolo. */
    fun surfacesFor(s: SurfaceResult, c: Corner, w: Wall): Set<Int> = s.surfaces.filter { sf ->
        sf.orientation == Orientation.VERTICAL && Geo.angleDeg(abs(sf.plane.nx * w.nx + sf.plane.nz * w.nz)) <= 10 &&
            abs(sf.plane.distance(c.x, (c.yLo + c.yHi) / 2, c.z)) <= max(c.tol, 0.05)
    }.map { it.id }.toSet()

    /** Distanza lungo la parete del primo punto della parete assegnato a una sua superficie (null = nessuno entro 50 cm). */
    fun gap(map: GlobalMap, s: SurfaceResult, c: Corner, w: Wall, code: Int, ids: Set<Int> = surfacesFor(s, c, w)): Double? {
        var best: Double? = null
        val sz = map.voxels.sizeM
        for (i in 0 until map.points.size) {
            val x = map.points.x[i].toDouble(); val z = map.points.z[i].toDouble(); val y = map.points.y[i].toDouble()
            if (y !in c.yLo..c.yHi || c.radius(x, z) > 0.5 || c.side(x, z) != code) continue
            val v = map.voxels.find(VoxelIndex.key(floor(x / sz).toInt(), floor(y / sz).toInt(), floor(z / sz).toInt()))
            if (v < 0 || s.voxelSurface[v] !in ids) continue
            val al = max(0.0, c.along(w, x, z))
            if (best == null || al < best) best = al
        }
        return best
    }

    /**
     * Candidata R2.x valutata offline: assorbimento a livello di PUNTO ai bordi delle superfici strutturali. I punti nei voxel entro
     * [rings] voxel da una superficie verticale strutturale, non assegnati o in voxel di un'altra superficie, vanno alla superficie se
     * distano dal suo piano ≤ distanza di crescita di R2; se sono vicini a più piani, al più vicino. Torna, per ogni punto
     * assorbito, (indice, superficie di destinazione, superficie di provenienza o −1).
     */
    fun absorb(map: GlobalMap, s: SurfaceResult, rings: Int): List<IntArray> {
        val vx = map.voxels
        val structural = s.surfaces.filter { it.kind == SurfaceKind.VERTICAL_STRUCTURAL }
        val lim = s.thresholds.growDistM
        val candidates = HashMap<Int, MutableList<Int>>() // voxel → superfici vicine
        for (sf in structural) {
            val set = HashSet<Int>(); for (v in sf.memberVoxels) set.add(v)
            for (v in sf.memberVoxels) {
                val k = vx.keys[v]; val x = VoxelIndex.ix(k); val y = VoxelIndex.iy(k); val z = VoxelIndex.iz(k)
                for (dx in -rings..rings) for (dy in -rings..rings) for (dz in -rings..rings) {
                    val u = vx.find(VoxelIndex.key(x + dx, y + dy, z + dz))
                    if (u < 0 || u in set) continue
                    val l = candidates.getOrPut(u) { mutableListOf() }; if (sf.id !in l) l.add(sf.id)
                }
            }
        }
        val out = mutableListOf<IntArray>()
        for (u in candidates.keys.sorted()) {
            val from = s.voxelSurface[u]
            if (from >= 0 && s.surfaces[from].kind == SurfaceKind.VERTICAL_STRUCTURAL && from in candidates.getValue(u)) continue
            for (t in vx.start[u] until vx.start[u] + vx.count[u]) {
                val i = vx.order[t]
                val x = map.points.x[i].toDouble(); val y = map.points.y[i].toDouble(); val z = map.points.z[i].toDouble()
                var best = -1; var bd = Double.MAX_VALUE
                for (id in candidates.getValue(u).sorted()) { val dd = abs(s.surfaces[id].plane.distance(x, y, z)); if (dd <= lim && dd < bd) { bd = dd; best = id } }
                // Se il voxel è già di un'altra superficie, il punto passa solo se è più vicino al nuovo piano che al proprio.
                if (best >= 0 && from >= 0 && abs(s.surfaces[from].plane.distance(x, y, z)) <= bd) best = -1
                if (best >= 0) out.add(intArrayOf(i, best, from))
            }
        }
        return out
    }

    internal fun f(v: Double, d: Int = 2): String = "%.${d}f".format(java.util.Locale.ROOT, v)
}
