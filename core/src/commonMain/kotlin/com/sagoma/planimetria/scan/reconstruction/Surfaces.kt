package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalyzer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * R2 — superfici candidate. Dalla mappa (R1): normali locali sul vicinato dei voxel (27 voxel, circa 12 cm, con i momenti dei punti
 * originali), region growing di voxel complanari, fit pesato e robusto del piano sui PUNTI ORIGINALI, metriche, classificazione con
 * la gravità (y di ARCore) e lo spazio libero. I piani di ARCore sono solo un'evidenza in più (peso piccolo), mai la fonte.
 */

enum class SurfaceKind { FLOOR, CEILING, VERTICAL_STRUCTURAL, VERTICAL_OBJECT, OBJECT, UNKNOWN }

enum class Orientation { HORIZONTAL_UP, HORIZONTAL_DOWN, VERTICAL, OBLIQUE }

/** Parametri di R2 (iniziali, da tarare: ognuno ha il suo motivo). */
data class SurfaceParams(
    /** Punti minimi nel vicinato di un voxel per stimarne la normale. */
    val minNeighborhoodPoints: Int = 10,
    /** Raggio (in voxel) del vicinato per la normale: 1 = 3×3×3 voxel (12 cm), 2 = 5×5×5 (20 cm), per depth più rumorose. */
    val normalRadius: Int = 2,
    /**
     * Soglie ricavate dal rumore della registrazione (consigliato): σ̂ = mediana di √λ0 dei vicinati; distanza di crescita e di fusione
     * = 2,5·σ̂ (tra i minimi qui sotto e 12 cm), angolo 25° se σ̂ > 1,5 cm; semi = 30% dei voxel più piani, crescita fino al 70%.
     * Con false si usano i valori fissi.
     */
    val adaptive: Boolean = true,
    /** Variazione di superficie massima di un seme (0 = piano perfetto). */
    val seedMaxVariation: Double = 0.03,
    /** Variazione massima di un voxel per entrare in una regione. */
    val growMaxVariation: Double = 0.08,
    /** Angolo massimo tra la normale del voxel e quella della regione. */
    val growAngleDeg: Double = 15.0,
    /** Distanza massima del centroide del voxel dal piano della regione: max(questo, 2,5 × RMS della regione). */
    val growMinDistM: Double = 0.03,
    val minRegionVoxels: Int = 12,
    val minRegionAreaM2: Double = 0.05,
    /** Fusione di regioni adiacenti complanari. */
    val mergeAngleDeg: Double = 6.0,
    val mergeDistM: Double = 0.04,
    /** Orizzontale / verticale: entro questo angolo dalla gravità. */
    val orientationTolDeg: Double = 15.0,
    /** Celle (cm) per area e copertura sul piano. */
    val coverageCellM: Double = 0.05,
    /** Evidenza minima per classificare (sotto: UNKNOWN). */
    val minSamples: Int = 200,
    val minEffectiveFrames: Int = 2,
    val minAreaM2: Double = 0.10,
)

/** Una superficie candidata con le sue metriche. Tutto in metri, mondo ARCore. La normale punta verso le camere che l'hanno vista. */
data class Surface(
    val id: Int,
    val kind: SurfaceKind,
    val kindConfidence: Double,
    val reasons: List<String>,
    val orientation: Orientation,
    val plane: PlaneFit,
    /** Inclinazione (gradi) rispetto all'orientamento ideale (0 = perfettamente orizzontale o verticale). */
    val tiltDeg: Double,
    val voxels: Int,
    val samples: Int,
    val rmsM: Double,
    val outlierFraction: Double,
    val areaM2: Double,
    val coverage: Double,
    /** Base sul piano: u (orizzontale per le verticali) e v (in alto per le verticali); estensioni robuste (1°–99° percentile). */
    val u: DoubleArray,
    val v: DoubleArray,
    val uMin: Double, val uMax: Double, val vMin: Double, val vMax: Double,
    /** Quote assolute (2°–98° percentile) e rispetto al pavimento (se noto). */
    val yMin: Double, val yMax: Double,
    val bottomAboveFloor: Double?, val topAboveFloor: Double?,
    val frames: Int,
    val effectiveFrames: Int,
    val viewAngleSpanDeg: Double,
    val rangeMedianM: Double,
    val incidenceMedianDeg: Double,
    /** Frazione dei campioni dietro la superficie visti liberi (null se non calcolata). */
    val freeBehind: Double?,
    /** Nessuna superficie verticale parallela dietro (null se non verticale). */
    val outermost: Boolean?,
    /** Piano verticale ARCore compatibile (chiave), solo evidenza. */
    val arcorePlane: Int?,
    val structuralScore: Double?,
    /** Indici dei voxel della regione (per le viste). */
    val memberVoxels: IntArray,
) {
    val lengthM: Double get() = uMax - uMin
    val heightM: Double get() = vMax - vMin
    override fun equals(other: Any?) = other is Surface && id == other.id && plane == other.plane && kind == other.kind
    override fun hashCode() = id
}

data class FloorEstimate(val y: Double, val source: String)

/** Soglie di R2 usate davvero in una ricostruzione (vedi [SurfaceParams.adaptive]). */
data class Thresholds(
    val seedMaxVariation: Double,
    val growMaxVariation: Double,
    val growAngleDeg: Double,
    val growDistM: Double,
    val mergeAngleDeg: Double,
    val mergeDistM: Double,
    val noiseM: Double,
)

/** Corrispondenza tra un piano verticale ARCore (all'ultimo stato) e le superfici: solo per confronto, non è verità. */
data class ArCoreMatch(val key: Int, val lengthM: Double, val persistenceMs: Long, val subsumed: Boolean, val surface: Int?, val angleDeg: Double?, val offsetM: Double?, val surfaceKind: SurfaceKind?)

class SurfaceResult(
    val surfaces: List<Surface>,
    val floor: FloorEstimate?,
    val ceilingY: Double?,
    val cameraMedianY: Double,
    /** Voxel con normale stimata, assegnati a una regione, e punti non assegnati (oggetti non planari, rumore, superfici piccole). */
    val voxelsWithNormal: Int,
    val voxelsAssigned: Int,
    val pointsAssigned: Long,
    val pointsTotal: Long,
    val arcore: List<ArCoreMatch>,
    /** Per ogni voxel, l'indice della superficie in [surfaces] (−1 = nessuna). */
    val voxelSurface: IntArray,
    /** Rumore stimato (σ̂, m) e soglie usate davvero (adattive o fisse). */
    val noiseEstimateM: Double = 0.0,
    val thresholds: Thresholds = Thresholds(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    /** R2.1: evidenza di superficie alternativa per superficie verticale (chiave: id). Solo diagnostica: non cambia le classi, R3/R4 non la leggono. */
    val ambiguity: Map<Int, SurfaceAmbiguity> = emptyMap(),
)

object SurfaceExtractor {

    fun extract(map: GlobalMap, recording: ScanRecording?, p: SurfaceParams = SurfaceParams()): SurfaceResult {
        val vx = map.voxels
        val m = vx.size
        val nbr = Array(m) { v -> neighborsOf(vx, v) }

        // 1. Normale e variazione di superficie di ogni voxel, sul vicinato (momenti dei punti originali).
        val normals = arrayOfNulls<PlaneFit>(m)
        for (v in 0 until m) {
            val acc = Moments(map.origin[0], map.origin[1], map.origin[2])
            acc.addAll(vx.moments[v])
            for (u in if (p.normalRadius == 1) nbr[v] else neighborsOf(vx, v, p.normalRadius)) acc.addAll(vx.moments[u])
            if (acc.n >= p.minNeighborhoodPoints) normals[v] = acc.plane()
        }
        val ownCentroid = Array(m) { vx.moments[it].centroid() }
        val (noise, th) = thresholds(normals, p)

        // 2. Region growing da semi molto piani (ordine deterministico: variazione, poi chiave).
        val region = IntArray(m) { -1 }
        val seeds = (0 until m).filter { normals[it] != null && normals[it]!!.surfaceVariation <= th.seedMaxVariation }
            .sortedWith(compareBy({ normals[it]!!.surfaceVariation }, { vx.keys[it] }))
        val regions = mutableListOf<MutableList<Int>>()
        for (s in seeds) {
            if (region[s] >= 0) continue
            val id = regions.size
            val members = mutableListOf(s)
            region[s] = id
            val acc = Moments(map.origin[0], map.origin[1], map.origin[2]).apply { addAll(vx.moments[s]) }
            var plane = normals[s]!!
            var refitAt = 8
            val queue = ArrayDeque<Int>().apply { add(s) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                for (u in nbr[cur]) {
                    if (region[u] >= 0) continue
                    val nu = normals[u] ?: continue
                    if (nu.surfaceVariation > th.growMaxVariation) continue
                    if (plane.angleTo(nu) > th.growAngleDeg) continue
                    val c = ownCentroid[u]
                    if (abs(plane.distance(c[0], c[1], c[2])) > max(th.growDistM, 2.5 * plane.rms)) continue
                    region[u] = id
                    members.add(u)
                    acc.addAll(vx.moments[u])
                    queue.add(u)
                    if (members.size >= refitAt) { acc.plane()?.let { plane = it }; refitAt *= 2 }
                }
            }
            regions.add(members)
        }

        // 3. Fusione delle regioni adiacenti complanari (union-find sui momenti, a passate finché ce ne sono), poi scarto delle piccole.
        val parent = IntArray(regions.size) { it }
        fun root(a: Int): Int { var r = a; while (parent[r] != r) r = parent[r]; var x = a; while (parent[x] != r) { val nx = parent[x]; parent[x] = r; x = nx }; return r }
        val acc = Array(regions.size) { momentsOf(map, regions[it]) }
        val pairs = HashSet<Long>()
        for (v in 0 until m) {
            val a = region[v]
            if (a < 0) continue
            for (u in nbr[v]) { val b = region[u]; if (b > a) pairs.add((a.toLong() shl 32) or b.toLong()) }
        }
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
        val groups = merged.keys.sorted().map { merged.getValue(it) }.map { it.sorted() }.filter { it.size >= p.minRegionVoxels }

        // 4. Piano robusto sui punti originali e metriche.
        val camY = map.frames.map { it.pose.y }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        val raw = groups.mapNotNull { g -> measure(map, g, p) }.filter { it.areaM2 >= p.minRegionAreaM2 }

        // 5. Classificazione: pavimento e soffitto prima (servono come riferimento), poi le verticali.
        val arcoreTracks = recording?.let { RecordingAnalyzer.analyze(it).verticalPlanes.filter { t -> t.last != null } } ?: emptyList()
        val classified = classify(map, raw, camY, recording, arcoreTracks, p)
        val surfaces = classified.first.sortedWith(compareBy<Surface>({ it.kind.ordinal }, { -it.areaM2 }, { it.plane.centroid[0] }, { it.plane.centroid[2] }))
            .mapIndexed { i, s -> s.copy(id = i) }
        val voxelSurface = IntArray(m) { -1 }
        for (s in surfaces) for (v in s.memberVoxels) voxelSurface[v] = s.id
        val arcore = matchArCore(surfaces, arcoreTracks)
        return SurfaceResult(
            surfaces, classified.second, classified.third, camY,
            voxelsWithNormal = normals.count { it != null },
            voxelsAssigned = voxelSurface.count { it >= 0 },
            pointsAssigned = surfaces.sumOf { it.samples.toLong() },
            pointsTotal = map.points.size.toLong(),
            arcore = arcore,
            voxelSurface = voxelSurface,
            noiseEstimateM = noise,
            thresholds = th,
            ambiguity = SurfaceAmbiguityEstimator.estimate(map, surfaces, voxelSurface, classified.second?.y, classified.third, p),
        )
    }

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

    private fun momentsOf(map: GlobalMap, voxels: List<Int>): Moments {
        val acc = Moments(map.origin[0], map.origin[1], map.origin[2])
        for (v in voxels) acc.addAll(map.voxels.moments[v])
        return acc
    }

    /** I punti originali di una regione (indici in [GlobalMap.points]). */
    fun pointsOf(map: GlobalMap, voxels: IntArray): IntArray {
        val out = IntList(1024)
        for (v in voxels) for (t in map.voxels.start[v] until map.voxels.start[v] + map.voxels.count[v]) out.add(map.voxels.order[t])
        return out.toArray().also { it.sort() }
    }

    /** Fit robusto (Huber, 3 iterazioni) sui punti originali e tutte le metriche della regione. */
    private fun measure(map: GlobalMap, voxels: List<Int>, p: SurfaceParams): Surface? {
        val pts = map.points
        val idx = pointsOf(map, voxels.toIntArray())
        var plane = momentsOf(map, voxels).plane() ?: return null
        repeat(3) {
            val k = max(0.01, 2 * plane.rms)
            val acc = Moments(map.origin[0], map.origin[1], map.origin[2])
            for (i in idx) {
                val r = abs(plane.distance(pts.x[i].toDouble(), pts.y[i].toDouble(), pts.z[i].toDouble()))
                val huber = if (r <= k) 1.0 else k / r
                acc.add(pts.x[i].toDouble(), pts.y[i].toDouble(), pts.z[i].toDouble(), pts.weight[i] * huber)
            }
            plane = acc.plane() ?: return null
        }
        // Normale verso le camere che l'hanno vista.
        var side = 0.0
        for (i in idx) {
            val f = map.frames[pts.frame[i]].pose
            side += plane.distance(f.x, f.y, f.z)
        }
        if (side < 0) plane = plane.flipped()

        val up = plane.ny
        val tolCos = cos(Angles.toRadians(p.orientationTolDeg))
        val tolSin = sin(Angles.toRadians(p.orientationTolDeg))
        val orientation = when {
            up >= tolCos -> Orientation.HORIZONTAL_UP
            up <= -tolCos -> Orientation.HORIZONTAL_DOWN
            abs(up) <= tolSin -> Orientation.VERTICAL
            else -> Orientation.OBLIQUE
        }
        val tilt = when (orientation) {
            Orientation.HORIZONTAL_UP, Orientation.HORIZONTAL_DOWN -> Geo.angleDeg(abs(up))
            Orientation.VERTICAL -> 90.0 - Geo.angleDeg(abs(up))
            Orientation.OBLIQUE -> Geo.angleDeg(abs(up))
        }
        // Base sul piano: per le verticali u orizzontale e v verso l'alto; per le altre l'asse principale dei punti.
        val n = doubleArrayOf(plane.nx, plane.ny, plane.nz)
        val u: DoubleArray
        val v: DoubleArray
        if (orientation == Orientation.VERTICAL) {
            u = normalize(doubleArrayOf(n[2], 0.0, -n[0])) // up × n
            v = cross(n, u)
        } else {
            val major = Eigen3.symmetric(momentsOf(map, voxels).covariance()).vector(2)
            u = normalize(sub(major, scale(n, dot(major, n))))
            v = cross(n, u)
        }
        val c = plane.centroid
        val k = 2.5 * max(0.01, plane.rms)
        var outliers = 0
        val su = DoubleArray(idx.size); val sv = DoubleArray(idx.size); val ys = DoubleArray(idx.size)
        val cells = HashSet<Long>()
        val frames = HashSet<Int>(); val groups = HashSet<Int>()
        val ranges = DoubleArray(idx.size)
        val incidence = DoubleArray(idx.size)
        val groupDir = HashMap<Int, DoubleArray>()
        for ((t, i) in idx.withIndex()) {
            val x = pts.x[i].toDouble(); val y = pts.y[i].toDouble(); val z = pts.z[i].toDouble()
            if (abs(plane.distance(x, y, z)) > k) outliers++
            val d = doubleArrayOf(x - c[0], y - c[1], z - c[2])
            su[t] = dot(d, u); sv[t] = dot(d, v); ys[t] = y
            cells.add((kotlin.math.floor(su[t] / p.coverageCellM).toLong() shl 32) xor (kotlin.math.floor(sv[t] / p.coverageCellM).toLong() and 0xFFFFFFFFL))
            val f = map.frames[pts.frame[i]]
            frames.add(f.index); groups.add(f.viewGroup)
            ranges[t] = pts.range[i].toDouble()
            val ray = normalize(doubleArrayOf(x - f.pose.x, y - f.pose.y, z - f.pose.z))
            incidence[t] = Geo.angleDeg(abs(dot(ray, n)))
            val gd = groupDir.getOrPut(f.viewGroup) { DoubleArray(3) }
            gd[0] += ray[0]; gd[1] += ray[1]; gd[2] += ray[2]
        }
        su.sort(); sv.sort(); ys.sort(); ranges.sort(); incidence.sort()
        val uMin = Geo.percentile(su, 0.01); val uMax = Geo.percentile(su, 0.99)
        val vMin = Geo.percentile(sv, 0.01); val vMax = Geo.percentile(sv, 0.99)
        val area = cells.size * p.coverageCellM * p.coverageCellM
        val box = max(p.coverageCellM * p.coverageCellM, (uMax - uMin + p.coverageCellM) * (vMax - vMin + p.coverageCellM))
        // Ampiezza degli angoli di vista: angolo massimo tra le direzioni medie dei gruppi di vista.
        val dirs = groupDir.keys.sorted().map { normalize(groupDir.getValue(it)) }
        var span = 0.0
        for (a in dirs.indices) for (b in a + 1 until dirs.size) span = max(span, Geo.angleDeg(dot(dirs[a], dirs[b])))
        return Surface(
            id = -1, kind = SurfaceKind.UNKNOWN, kindConfidence = 0.0, reasons = emptyList(), orientation = orientation, plane = plane, tiltDeg = tilt,
            voxels = voxels.size, samples = idx.size, rmsM = plane.rms, outlierFraction = if (idx.isEmpty()) 0.0 else outliers.toDouble() / idx.size,
            areaM2 = area, coverage = min(1.0, area / box), u = u, v = v, uMin = uMin, uMax = uMax, vMin = vMin, vMax = vMax,
            yMin = Geo.percentile(ys, 0.02), yMax = Geo.percentile(ys, 0.98), bottomAboveFloor = null, topAboveFloor = null,
            frames = frames.size, effectiveFrames = groups.size, viewAngleSpanDeg = span,
            rangeMedianM = Geo.percentile(ranges, 0.5), incidenceMedianDeg = Geo.percentile(incidence, 0.5),
            freeBehind = null, outermost = null, arcorePlane = null, structuralScore = null, memberVoxels = voxels.sorted().toIntArray(),
        )
    }

    private fun classify(
        map: GlobalMap, raw: List<Surface>, camY: Double, recording: ScanRecording?, tracks: List<com.sagoma.planimetria.scan.recording.analysis.PlaneTrack>, p: SurfaceParams,
    ): Triple<List<Surface>, FloorEstimate?, Double?> {
        fun weak(s: Surface) = s.samples < p.minSamples || s.effectiveFrames < p.minEffectiveFrames || s.areaM2 < p.minAreaM2
        // Pavimento: la superficie orizzontale verso l'alto più grande ben sotto le camere; le altre alla stessa quota sono pavimento.
        val ups = raw.filter { it.orientation == Orientation.HORIZONTAL_UP && !weak(it) && it.plane.centroid[1] < camY - 0.6 }
        val floorMain = ups.maxWithOrNull(compareBy<Surface>({ it.areaM2 }, { -it.plane.centroid[1] }))
        val arFloor = recording?.frames?.mapNotNull { it.floorY }?.sorted()?.let { if (it.isEmpty()) null else it[it.size / 2] }
        val floor = when {
            floorMain != null -> FloorEstimate(floorMain.plane.centroid[1], "superficie ricostruita")
            arFloor != null -> FloorEstimate(arFloor, "floorY di ARCore (nessun pavimento ricostruito)")
            else -> null
        }
        val downs = raw.filter { it.orientation == Orientation.HORIZONTAL_DOWN && !weak(it) && it.plane.centroid[1] > camY + 0.3 }
        val ceilingMain = downs.maxWithOrNull(compareBy<Surface>({ it.areaM2 }, { it.plane.centroid[1] }))
        val ceilingY = ceilingMain?.plane?.centroid?.get(1)
        val floorY = floor?.y

        val out = mutableListOf<Surface>()
        val verticals = raw.filter { it.orientation == Orientation.VERTICAL }
        for (s0 in raw) {
            val s = if (floorY != null) s0.copy(bottomAboveFloor = s0.yMin - floorY, topAboveFloor = s0.yMax - floorY) else s0
            val cy = s.plane.centroid[1]
            out.add(
                when (s.orientation) {
                    Orientation.HORIZONTAL_UP -> when {
                        weak(s) -> s.copy(kind = SurfaceKind.UNKNOWN, kindConfidence = 0.3, reasons = listOf("evidenza debole (${s.samples} campioni, ${s.effectiveFrames} viste, ${fmt(s.areaM2)} m²)"))
                        floorY != null && abs(cy - floorY) <= 0.06 -> s.copy(kind = SurfaceKind.FLOOR, kindConfidence = if (s === floorMain || s0 === floorMain) 0.95 else 0.8,
                            reasons = listOf("orizzontale verso l'alto alla quota del pavimento (${fmt(cy - floorY)} m)", "inclinazione ${fmt(s.tiltDeg)}°"))
                        else -> s.copy(kind = SurfaceKind.OBJECT, kindConfidence = 0.7, reasons = listOf("orizzontale verso l'alto a ${floorY?.let { fmt(cy - it) } ?: "?"} m dal pavimento (piano di un mobile?)"))
                    }
                    Orientation.HORIZONTAL_DOWN -> when {
                        weak(s) -> s.copy(kind = SurfaceKind.UNKNOWN, kindConfidence = 0.3, reasons = listOf("evidenza debole"))
                        ceilingY != null && abs(cy - ceilingY) <= 0.06 -> s.copy(kind = SurfaceKind.CEILING, kindConfidence = if (s0 === ceilingMain) 0.9 else 0.75,
                            reasons = listOf("orizzontale verso il basso sopra le camere (${fmt(cy - camY)} m sopra la quota mediana)"))
                        else -> s.copy(kind = SurfaceKind.OBJECT, kindConfidence = 0.6, reasons = listOf("orizzontale verso il basso non alla quota del soffitto"))
                    }
                    Orientation.OBLIQUE -> if (weak(s)) s.copy(kind = SurfaceKind.UNKNOWN, kindConfidence = 0.3, reasons = listOf("obliqua, evidenza debole"))
                    else s.copy(kind = SurfaceKind.OBJECT, kindConfidence = 0.5, reasons = listOf("obliqua (${fmt(s.tiltDeg)}° dalla verticale)"))
                    Orientation.VERTICAL -> classifyVertical(map, s, verticals, floorY, ceilingY, tracks, p)
                },
            )
        }
        return Triple(out, floor, ceilingY)
    }

    /**
     * Parete strutturale o fronte di un oggetto: punteggio di evidenze indipendenti (nessuna decide da sola):
     * altezza (arriva al soffitto o oltre 2 m) 0,30 · parte da terra 0,15 · nessuno spazio libero visto dietro 0,25 ·
     * nessuna superficie verticale parallela più esterna dietro 0,20 · lunghezza 0,05 · piano ARCore compatibile 0,05.
     */
    private fun classifyVertical(
        map: GlobalMap, s: Surface, verticals: List<Surface>, floorY: Double?, ceilingY: Double?,
        tracks: List<com.sagoma.planimetria.scan.recording.analysis.PlaneTrack>, p: SurfaceParams,
    ): Surface {
        val reasons = mutableListOf<String>()
        val top = s.topAboveFloor
        val bottom = s.bottomAboveFloor
        val ceilingH = if (ceilingY != null && floorY != null) ceilingY - floorY else null
        val heightScore = when {
            top == null -> 0.5
            ceilingH != null && top >= ceilingH - 0.25 -> 1.0
            else -> ((top - 1.2) / 0.8).coerceIn(0.0, 1.0)
        }
        reasons.add("altezza: fino a ${top?.let { fmt(it) } ?: "?"} m dal pavimento" + (ceilingH?.let { " (soffitto a ${fmt(it)} m)" } ?: "") + " → ${fmt(heightScore)}")
        val bottomScore = if (bottom == null) 0.5 else (1 - (bottom - 0.15) / 0.45).coerceIn(0.0, 1.0)
        reasons.add("parte da ${bottom?.let { fmt(it) } ?: "?"} m dal pavimento → ${fmt(bottomScore)}")
        val freeBehind = freeBehind(map, s)
        reasons.add("spazio libero visto dietro: ${fmt(freeBehind * 100)}% → ${fmt(1 - freeBehind)}")
        val behind = verticals.firstOrNull { o ->
            o !== s && o.plane.angleTo(s.plane) <= 10 &&
                (o.plane.centroid[0] - s.plane.centroid[0]) * s.plane.nx + (o.plane.centroid[2] - s.plane.centroid[2]) * s.plane.nz in -1.5..-0.05 &&
                overlap(s, o) >= 0.3 * s.lengthM && o.yMax >= s.yMax - 0.1
        }
        val outermost = behind == null
        reasons.add(if (outermost) "nessuna superficie parallela più esterna dietro" else "c'è una superficie parallela più esterna dietro (a ${fmt(-((behind!!.plane.centroid[0] - s.plane.centroid[0]) * s.plane.nx + (behind.plane.centroid[2] - s.plane.centroid[2]) * s.plane.nz))} m)")
        val lengthScore = ((s.lengthM - 0.3) / 0.7).coerceIn(0.0, 1.0)
        val ar = tracks.firstOrNull { t -> compatible(s, t) }?.key
        if (ar != null) reasons.add("piano verticale ARCore #$ar compatibile (solo evidenza)")
        val score = 0.30 * heightScore + 0.15 * bottomScore + 0.25 * (1 - freeBehind) + 0.20 * (if (outermost) 1.0 else 0.0) +
            0.05 * lengthScore + 0.05 * (if (ar != null) 1.0 else 0.0)
        val weak = s.samples < p.minSamples || s.effectiveFrames < p.minEffectiveFrames || s.areaM2 < p.minAreaM2
        val (kind, conf) = when {
            weak -> SurfaceKind.UNKNOWN to 0.3
            score >= 0.6 -> SurfaceKind.VERTICAL_STRUCTURAL to score
            score < 0.5 -> SurfaceKind.VERTICAL_OBJECT to (1 - score)
            else -> SurfaceKind.UNKNOWN to 0.5
        }
        if (weak) reasons.add(0, "evidenza debole (${s.samples} campioni, ${s.effectiveFrames} viste, ${fmt(s.areaM2)} m²)")
        reasons.add(0, "punteggio strutturale ${fmt(score)}")
        return s.copy(kind = kind, kindConfidence = conf, reasons = reasons, freeBehind = freeBehind, outermost = outermost, arcorePlane = ar, structuralScore = score)
    }

    /** Frazione dei campioni dietro la superficie (15, 25, 40 cm, lato opposto alle camere) visti liberi tra quelli osservati. */
    private fun freeBehind(map: GlobalMap, s: Surface): Double {
        val idx = pointsOf(map, s.memberVoxels)
        val stride = max(1, idx.size / 300)
        var free = 0; var seen = 0
        var t = 0
        while (t < idx.size) {
            val i = idx[t]
            for (off in doubleArrayOf(0.15, 0.25, 0.40)) {
                val x = map.points.x[i] - s.plane.nx * off; val y = map.points.y[i] - s.plane.ny * off; val z = map.points.z[i] - s.plane.nz * off
                when (map.freeSpace.state(x, y, z)) { 1 -> { free++; seen++ }; 2 -> seen++ }
            }
            t += stride
        }
        return if (seen == 0) 0.0 else free.toDouble() / seen
    }

    private fun overlap(a: Surface, b: Surface): Double {
        // Estremi di b proiettati sull'asse u di a.
        fun proj(s: Surface, t: Double): Double {
            val px = s.plane.centroid[0] + s.u[0] * t; val pz = s.plane.centroid[2] + s.u[2] * t
            return (px - a.plane.centroid[0]) * a.u[0] + (pz - a.plane.centroid[2]) * a.u[2]
        }
        val b0 = proj(b, b.uMin); val b1 = proj(b, b.uMax)
        return max(0.0, min(a.uMax, max(b0, b1)) - max(a.uMin, min(b0, b1)))
    }

    private fun compatible(s: Surface, t: com.sagoma.planimetria.scan.recording.analysis.PlaneTrack): Boolean {
        val g = t.last ?: return false
        val dir = normalize(doubleArrayOf(g.b.x - g.a.x, 0.0, g.b.z - g.a.z))
        val angle = Geo.angleDeg(abs(dot(dir, s.u)))
        val offset = abs(s.plane.distance(g.centerX, (g.minY + g.maxY) / 2, g.centerZ))
        val along = (g.centerX - s.plane.centroid[0]) * s.u[0] + (g.centerZ - s.plane.centroid[2]) * s.u[2]
        return angle <= 10 && offset <= 0.10 && along in (s.uMin - 0.3)..(s.uMax + 0.3)
    }

    private fun matchArCore(surfaces: List<Surface>, tracks: List<com.sagoma.planimetria.scan.recording.analysis.PlaneTrack>): List<ArCoreMatch> =
        tracks.sortedBy { it.key }.map { t ->
            val g = t.last!!
            val best = surfaces.filter { it.orientation == Orientation.VERTICAL && compatible(it, t) }
                .minByOrNull { abs(it.plane.distance(g.centerX, (g.minY + g.maxY) / 2, g.centerZ)) }
            val dir = normalize(doubleArrayOf(g.b.x - g.a.x, 0.0, g.b.z - g.a.z))
            ArCoreMatch(
                t.key, g.lengthM, t.persistenceMs, t.subsumedBy != null, best?.id,
                best?.let { Geo.angleDeg(abs(dot(dir, it.u))) }, best?.let { abs(it.plane.distance(g.centerX, (g.minY + g.maxY) / 2, g.centerZ)) }, best?.kind,
            )
        }

    /**
     * Rumore della registrazione e soglie di R2. σ̂ = mediana di √λ0 (spessore del vicinato) sui voxel con normale. Con
     * [SurfaceParams.adaptive] le soglie seguono σ̂ (una depth rumorosa non spezza le superfici, una precisa non le fonde), mai
     * sotto i valori fissi: distanza 2,5·σ̂ (al massimo 12 cm), angolo 25° se σ̂ > 1,5 cm, semi tra i 30% voxel più piani.
     */
    private fun thresholds(normals: Array<PlaneFit?>, p: SurfaceParams): Pair<Double, Thresholds> {
        val valid = normals.filterNotNull()
        if (valid.isEmpty()) return 0.0 to Thresholds(p.seedMaxVariation, p.growMaxVariation, p.growAngleDeg, p.growMinDistM, p.mergeAngleDeg, p.mergeDistM, 0.0)
        val noise = Geo.median(DoubleArray(valid.size) { valid[it].rms })
        if (!p.adaptive) return noise to Thresholds(p.seedMaxVariation, p.growMaxVariation, p.growAngleDeg, p.growMinDistM, p.mergeAngleDeg, p.mergeDistM, noise)
        val sv = DoubleArray(valid.size) { valid[it].surfaceVariation }.also { it.sort() }
        val dist = (2.5 * noise).coerceIn(p.growMinDistM, 0.12)
        val angle = if (noise > 0.015) max(p.growAngleDeg, 25.0) else p.growAngleDeg
        return noise to Thresholds(
            seedMaxVariation = max(p.seedMaxVariation, Geo.percentile(sv, 0.30)),
            growMaxVariation = max(p.growMaxVariation, Geo.percentile(sv, 0.70)),
            growAngleDeg = angle,
            growDistM = dist,
            mergeAngleDeg = max(p.mergeAngleDeg, angle * 0.4),
            mergeDistM = max(p.mergeDistM, dist),
            noiseM = noise,
        )
    }

    internal fun fmt(v: Double) = (kotlin.math.round(v * 100) / 100).toString()
    internal fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    internal fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    internal fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    internal fun scale(a: DoubleArray, k: Double) = doubleArrayOf(a[0] * k, a[1] * k, a[2] * k)
    internal fun normalize(a: DoubleArray): DoubleArray {
        val l = sqrt(dot(a, a))
        return if (l < 1e-12) a else doubleArrayOf(a[0] / l, a[1] / l, a[2] / l)
    }
}

/** Gradi → radianti (commonMain non ha Math.toRadians). */
internal object Angles {
    fun toRadians(deg: Double) = deg * kotlin.math.PI / 180.0
}
