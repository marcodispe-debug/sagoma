package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.rotate
import com.sagoma.planimetria.scan.recording.transformPoint
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * R3 — stima delle pareti dalle superfici R2 VERTICAL_STRUCTURAL. Algoritmo (deterministico):
 *
 *  1. Rumore della depth per fascia di distanza: σ robusta (1,4826·MAD) dei residui dei punti delle superfici strutturali dal loro
 *     piano (fit robusto non pesato), minimo 5 mm. Spostamento temporale per punto: posizione con la posa C − posizione con la posa A
 *     (ambiguità A/C della raw, vedi RawPoseStudy/RawEdgeStudy); se la posa C non c'è, ripiego = p90 dei valori noti (o 5 cm).
 *  2. Fit di ogni frammento: retta pesata in pianta (piano verticale), minimi quadrati totali con IRLS di Huber (k = 2) sui residui
 *     normalizzati; peso del punto = peso R1 / (σ_depth(distanza)² + σ_temporale_normale²): una raw con grande ambiguità temporale
 *     pesa meno. Inlier: |residuo| ≤ 3σ del punto. Normale verso le camere.
 *  3. Fusione dei frammenti: angolo ≤ max(3°, 3·σθ combinata) (al massimo 8°), distanza reciproca dalle rette ≤ max(3 cm, 3·σ
 *     combinata) (al massimo 8 cm), quote compatibili, RMS compatibili, tratto mancante tra i due ≤ 2 m. Il tratto mancante NON viene
 *     riempito: resta un gap con il suo motivo.
 *  4. Estremità e gap dalla distribuzione dei punti lungo la linea (celle da 5 cm); motivi dallo spazio libero (R1): oggetto davanti
 *     → nascosto; si vede attraverso → la parete finisce / vano; nessuna osservazione → fuori vista; altra superficie strutturale non
 *     parallela vicina → angolo. Estremità instabile tra le viste (σ > 8 cm) → incerta. Nessuna estensione oltre i dati.
 *  5. Incertezze dai gruppi di vista (osservazioni indipendenti): posizione = √(dispersione²/G + σ_temporale²/G); orientamento dai
 *     gruppi o dalla formula del fit; estremità dalla dispersione delle estremità dei gruppi (+1,5 cm di campionamento).
 *  6. Spessore solo con due facce compatibili (normali opposte, 3–45 cm, sovrapposte ≥ 30 cm, nessuno spazio libero tra le due).
 *  6b. Una superficie strutturale con una fascia di almeno 30 cm attraversata dai raggi SOPRA il suo bordo superiore (fino a 2,4 m)
 *     è un oggetto basso, non una parete: viene scartata con il motivo (una parete prosegue fino al soffitto).
 *  7. VERTICAL_OBJECT, UNKNOWN e le altre classi non diventano mai pareti in R3 (nessuna promozione).
 */

/** Punti in ingresso a R3 (mondo ARCore, m). [dispX]/[dispY]/[dispZ] = posizione con la posa C − con la posa A (NaN se ignota). */
class WallPoints(
    val x: DoubleArray, val y: DoubleArray, val z: DoubleArray,
    val weight: DoubleArray, val range: DoubleArray,
    val group: IntArray, val frame: IntArray,
    val dispX: DoubleArray, val dispY: DoubleArray, val dispZ: DoubleArray,
) {
    val size: Int get() = x.size
}

/** Una superficie R2 in ingresso: classe, confidenza di classificazione, indici dei suoi punti in [WallPoints]. */
class WallInputSurface(val id: Int, val kind: SurfaceKind, val orientation: Orientation, val kindConfidence: Double, val points: IntArray)

/** Spazio libero (R1): 1 = visto libero, 2 = occupato, 0 = ignoto, −1 = fuori. */
fun interface SpaceQuery {
    fun state(x: Double, y: Double, z: Double): Int
}

class WallInput(
    val points: WallPoints,
    val surfaces: List<WallInputSurface>,
    val floorY: Double?,
    val space: SpaceQuery,
    /** Posizione della camera per gruppo di vista. */
    val groupCamera: Map<Int, DoubleArray>,
)

data class WallParams(
    val minInliers: Int = 150,
    val minGroups: Int = 2,
    val minLengthM: Double = 0.25,
    val huberK: Double = 2.0,
    val inlierSigmas: Double = 3.0,
    val iterations: Int = 6,
    val minGroupPoints: Int = 50,
    val cellM: Double = 0.05,
    /** Buchi fino a questa lunghezza dentro un tratto osservato non sono gap (rumore del campionamento). */
    val mergeHoleM: Double = 0.10,
    val mergeAngleMinDeg: Double = 3.0,
    val mergeAngleMaxDeg: Double = 8.0,
    val mergeDistMinM: Double = 0.03,
    val mergeDistMaxM: Double = 0.08,
    val mergeMaxGapM: Double = 2.0,
    val unstableEndM: Double = 0.08,
    val samplingSigmaM: Double = 0.015,
    val endFallbackSigmaM: Double = 0.10,
    /** R3.1: finestra terminale in cui si misura l'evidenza di un'estremità (come le metriche di densità a 5/10/15/20 cm). */
    val endWindowM: Double = 0.20,
    /** R3.1: un angolo è raggiunto se l'intersezione con l'altra superficie è entro questo numero di σ dell'estremità. */
    val cornerReachSigmas: Double = 3.0,
    val temporalFallbackDefaultM: Double = 0.05,
    /** Spazio libero visto sopra il bordo superiore oltre questa frazione → oggetto basso, non parete. */
    val maxFreeAbove: Double = 0.5,
)

object WallEstimator {
    /** Fit di un insieme di punti (frammento o parete fusa). */
    private class Fit(
        val idx: IntArray,
        val nx: Double, val nz: Double, val d: Double,
        val cx: Double, val cz: Double,
        val residual: DoubleArray, val sigma: DoubleArray, val w: DoubleArray, val inlier: BooleanArray,
        val rms: Double,
    ) {
        val ux get() = -nz
        val uz get() = nx
        fun u(p: WallPoints, i: Int) = (p.x[i] - cx) * ux + (p.z[i] - cz) * uz
        fun dist(x: Double, z: Double) = nx * x + nz * z - d
        val headingDeg: Double get() = ((atan2(uz, ux) * 180 / kotlin.math.PI) % 180 + 180) % 180
    }

    /** Un frammento o una parete con le sue misure di sintesi (per la fusione). */
    private class Piece(val ids: List<Int>, val fit: Fit, val m: Summary)

    private class Summary(
        val startU: Double, val endU: Double, val bottomY: Double, val topY: Double, val groups: Int,
        val positionSigma: Double, val headingSigma: Double, val dispersion: Double,
    )

    fun estimate(input: WallInput, p: WallParams = WallParams()): WallEstimationResult {
        val pts = input.points
        val diag = mutableListOf<String>()
        val vertical = input.surfaces.filter { it.orientation == Orientation.VERTICAL }.sortedBy { it.id }
        val excluded = mutableListOf<WallExclusion>()
        for (s in vertical) if (s.kind != SurfaceKind.VERTICAL_STRUCTURAL) excluded.add(
            WallExclusion(
                s.id, s.kind,
                when (s.kind) {
                    SurfaceKind.VERTICAL_OBJECT -> "fronte di un oggetto: in R3 non viene mai promosso a parete"
                    SurfaceKind.UNKNOWN -> "classificazione incerta in R2: non usata come parete"
                    else -> "classe ${s.kind}: non è una parete"
                },
            ),
        )
        val structural = vertical.filter { it.kind == SurfaceKind.VERTICAL_STRUCTURAL }

        // 1. Ripiego temporale e modello di rumore.
        val known = (0 until pts.size).filter { !pts.dispX[it].isNaN() }.map { sqrt(pts.dispX[it] * pts.dispX[it] + pts.dispZ[it] * pts.dispZ[it]) }
        val temporalFallback = if (known.isEmpty()) p.temporalFallbackDefaultM else Geo.percentile(known.toDoubleArray().sortedArray(), 0.9)
        if (known.size < pts.size) diag.add("Spostamento temporale A/C ignoto per ${pts.size - known.size} punti: ripiego ${fmt(temporalFallback * 100)} cm (p90 dei valori noti)")
        val noise = noiseModel(pts, structural)

        // 2. Fit dei frammenti.
        val pieces = mutableListOf<Piece>()
        for (s in structural) {
            val fit = fit(pts, s.points, noise, temporalFallback, input.groupCamera, p)
            val reason = when {
                fit == null -> "fit impossibile (punti degeneri)"
                fit.inlier.count { it } < p.minInliers -> "evidenza insufficiente: ${fit.inlier.count { it }} punti affidabili (< ${p.minInliers})"
                else -> null
            }
            if (reason != null) { excluded.add(WallExclusion(s.id, s.kind, reason)); continue }
            val m = summary(pts, fit!!, noise, temporalFallback, p)
            val freeAbove = freeAbove(fit, m, input.space, input.floorY)
            when {
                freeAbove != null && freeAbove >= p.maxFreeAbove -> excluded.add(
                    WallExclusion(s.id, s.kind, "sopra il bordo osservato (${fmt(m.topY - (input.floorY ?: 0.0))} m dal pavimento) c'è una fascia di almeno 30 cm attraversata dai raggi (${fmt(freeAbove * 100)}% dei punti): non arriva al soffitto, oggetto basso, non parete"),
                )
                m.groups < p.minGroups -> excluded.add(WallExclusion(s.id, s.kind, "vista da ${m.groups} gruppo di vista (< ${p.minGroups})"))
                m.endU - m.startU < p.minLengthM -> excluded.add(WallExclusion(s.id, s.kind, "troppo corta (${fmt((m.endU - m.startU) * 100)} cm)"))
                else -> pieces.add(Piece(listOf(s.id), fit, m))
            }
        }

        // 3. Fusione dei frammenti compatibili (union-find, coppie in ordine deterministico).
        val parent = IntArray(pieces.size) { it }
        fun root(a: Int): Int { var r = a; while (parent[r] != r) r = parent[r]; return r }
        for (i in pieces.indices) for (j in i + 1 until pieces.size) {
            if (root(i) == root(j)) continue
            if (compatible(pieces[i], pieces[j], pts, p, input.floorY)) parent[max(root(i), root(j))] = min(root(i), root(j))
        }
        val groupsOf = pieces.indices.groupBy { root(it) }.toList().sortedBy { it.first }.map { it.second }
        val merges = groupsOf.filter { it.size > 1 }.map { g -> g.flatMap { pieces[it].ids }.sorted() }
        val merged = groupsOf.mapNotNull { g ->
            val ids = g.flatMap { pieces[it].ids }.sorted()
            if (g.size == 1) pieces[g[0]] else {
                val all = g.flatMap { pieces[it].fit.idx.toList() }.distinct().sorted().toIntArray()
                val fit = fit(pts, all, noise, temporalFallback, input.groupCamera, p) ?: return@mapNotNull null
                Piece(ids, fit, summary(pts, fit, noise, temporalFallback, p))
            }
        }

        // 4–5. Pareti con estremità, gap, incertezze, evidenza.
        val conf = structural.associate { it.id to it.kindConfidence }
        val walls = merged.map { pc -> wall(pc, pts, merged, noise, temporalFallback, input, p, conf) }
            .sortedWith(compareBy<EstimatedWall>({ -it.geometry.observedLengthM }, { it.sourceSurfaceIds.first() }))
            .mapIndexed { i, w -> w.copy(id = i) }

        // 6. Spessore: solo da due facce compatibili.
        val withThickness = thickness(walls, input.space)
        return WallEstimationResult(vertical.size, structural.size, withThickness, excluded.sortedBy { it.surfaceId }, merges, noise, temporalFallback, diag)
    }

    // ---------- Rumore ----------

    private val RANGE_BINS = listOf(0.0..1.0, 1.0..1.5, 1.5..2.0, 2.0..3.0, 3.0..100.0)

    private fun noiseModel(pts: WallPoints, surfaces: List<WallInputSurface>): DepthNoiseModel {
        // Residui dal piano di ogni superficie, con un fit che esclude via via i punti oltre 15 cm (outlier, non rumore: altrimenti
        // gli outlier, che si concentrano in certe fasce di distanza, gonfierebbero la σ proprio lì).
        val perBin = RANGE_BINS.map { mutableListOf<Double>() }
        for (s in surfaces) {
            var w = DoubleArray(s.points.size) { pts.weight[s.points[it]] }
            var f = lineFit(pts, s.points, w) ?: continue
            repeat(3) {
                w = DoubleArray(s.points.size) { k -> val i = s.points[k]; if (abs(f.second.first * pts.x[i] + f.second.second * pts.z[i] - f.third) <= 0.15) pts.weight[i] else 0.0 }
                f = lineFit(pts, s.points, w) ?: return@repeat
            }
            for (i in s.points) {
                val r = abs(f.second.first * pts.x[i] + f.second.second * pts.z[i] - f.third)
                if (r > 0.15) continue
                val b = RANGE_BINS.indexOfFirst { pts.range[i] in it }
                if (b >= 0) perBin[b].add(r)
            }
        }
        val all = perBin.flatten()
        val overall = if (all.size >= 50) 1.4826 * Geo.median(all.toDoubleArray()) else 0.03
        val bins = RANGE_BINS.mapIndexed { b, rng -> rng to (if (perBin[b].size >= 200) 1.4826 * Geo.median(perBin[b].toDoubleArray()) else overall).coerceAtLeast(0.005) }
        return DepthNoiseModel(bins, 0.005)
    }

    /** Retta pesata in pianta: (centroide, normale (nx, nz), d). */
    private fun lineFit(pts: WallPoints, idx: IntArray, w: DoubleArray): Triple<DoubleArray, Pair<Double, Double>, Double>? {
        var sw = 0.0; var sx = 0.0; var sz = 0.0
        for ((k, i) in idx.withIndex()) { sw += w[k]; sx += w[k] * pts.x[i]; sz += w[k] * pts.z[i] }
        if (sw <= 0 || idx.size < 3) return null
        val mx = sx / sw; val mz = sz / sw
        var cxx = 0.0; var cxz = 0.0; var czz = 0.0
        for ((k, i) in idx.withIndex()) { val dx = pts.x[i] - mx; val dz = pts.z[i] - mz; cxx += w[k] * dx * dx; cxz += w[k] * dx * dz; czz += w[k] * dz * dz }
        // Autovettore minore della 2×2 = normale.
        val tr = cxx + czz; val det = cxx * czz - cxz * cxz
        val l = tr / 2 - sqrt(max(0.0, tr * tr / 4 - det))
        var nx: Double; var nz: Double
        if (abs(cxz) > 1e-15) { nx = l - czz; nz = cxz } else if (cxx < czz) { nx = 1.0; nz = 0.0 } else { nx = 0.0; nz = 1.0 }
        val n = sqrt(nx * nx + nz * nz)
        if (n < 1e-15) return null
        nx /= n; nz /= n
        if (abs(nx) > abs(nz)) { if (nx < 0) { nx = -nx; nz = -nz } } else if (nz < 0) { nx = -nx; nz = -nz }
        return Triple(doubleArrayOf(mx, mz), nx to nz, nx * mx + nz * mz)
    }

    private fun temporalNormal(pts: WallPoints, i: Int, nx: Double, nz: Double, fallback: Double) =
        if (pts.dispX[i].isNaN()) fallback else abs(pts.dispX[i] * nx + pts.dispZ[i] * nz)

    private fun fit(pts: WallPoints, idx: IntArray, noise: DepthNoiseModel, fallback: Double, cams: Map<Int, DoubleArray>, p: WallParams): Fit? {
        if (idx.size < 10) return null
        var line = lineFit(pts, idx, DoubleArray(idx.size) { pts.weight[idx[it]] }) ?: return null
        val n = idx.size
        val res = DoubleArray(n); val sig = DoubleArray(n); val w = DoubleArray(n); val inl = BooleanArray(n)
        // IRLS: prima Huber (k = 2), poi due passate con esclusione netta oltre 3σ (gli outlier lontani non tirano più la retta).
        repeat(p.iterations + 2) { iter ->
            val hard = iter >= p.iterations
            val (nx, nz) = line.second
            for ((k, i) in idx.withIndex()) {
                val sd = noise.sigma(pts.range[i]); val st = temporalNormal(pts, i, nx, nz, fallback)
                sig[k] = sqrt(sd * sd + st * st)
                res[k] = nx * pts.x[i] + nz * pts.z[i] - line.third
                val zn = abs(res[k]) / sig[k]
                val robust = if (hard) (if (zn <= p.inlierSigmas) 1.0 else 0.0) else (if (zn <= p.huberK) 1.0 else p.huberK / zn)
                w[k] = pts.weight[i] / (sig[k] * sig[k]) * robust
            }
            line = lineFit(pts, idx, w) ?: return null
        }
        var (nx, nz) = line.second
        var d = line.third
        // Normale verso le camere che hanno visto i punti.
        var side = 0.0
        val seen = idx.map { pts.group[it] }.distinct().sorted()
        for (g in seen) cams[g]?.let { c -> side += nx * c[0] + nz * c[2] - d }
        if (side < 0) { nx = -nx; nz = -nz; d = -d }
        var sum = 0.0; var cnt = 0
        for ((k, i) in idx.withIndex()) {
            val sd = noise.sigma(pts.range[i]); val st = temporalNormal(pts, i, nx, nz, fallback)
            sig[k] = sqrt(sd * sd + st * st)
            res[k] = nx * pts.x[i] + nz * pts.z[i] - d
            inl[k] = abs(res[k]) <= p.inlierSigmas * sig[k]
            w[k] = if (inl[k]) pts.weight[i] / (sig[k] * sig[k]) else 0.0
            if (inl[k]) { sum += res[k] * res[k]; cnt++ }
        }
        return Fit(idx, nx, nz, d, line.first[0], line.first[1], res, sig, w, inl, if (cnt == 0) 0.0 else sqrt(sum / cnt))
    }

    // ---------- Sintesi, fusione ----------

    /** Medie pesate per gruppo di vista dei residui: (gruppo → (media, peso)). */
    private fun groupOffsets(pts: WallPoints, f: Fit, p: WallParams): Map<Int, Pair<Double, Double>> {
        val acc = HashMap<Int, DoubleArray>()
        val cnt = HashMap<Int, Int>()
        for ((k, i) in f.idx.withIndex()) {
            if (!f.inlier[k]) continue
            val a = acc.getOrPut(pts.group[i]) { DoubleArray(2) }
            a[0] += f.w[k] * f.residual[k]; a[1] += f.w[k]
            cnt[pts.group[i]] = (cnt[pts.group[i]] ?: 0) + 1
        }
        return acc.keys.sorted().filter { (cnt[it] ?: 0) >= p.minGroupPoints && acc.getValue(it)[1] > 0 }.associateWith { acc.getValue(it)[0] / acc.getValue(it)[1] to acc.getValue(it)[1] }
    }

    private fun summary(pts: WallPoints, f: Fit, noise: DepthNoiseModel, fallback: Double, p: WallParams): Summary {
        val us = f.idx.indices.filter { f.inlier[it] }.map { f.u(pts, f.idx[it]) }.sorted().toDoubleArray()
        val ys = f.idx.indices.filter { f.inlier[it] }.map { pts.y[f.idx[it]] }.sorted().toDoubleArray()
        val g = groupOffsets(pts, f, p)
        val disp = weightedStd(g.values.toList())
        val sigmaPos = positionSigma(pts, f, g, disp, fallback)
        val sigmaHead = headingSigma(pts, f, g.size, disp, us)
        return Summary(Geo.percentile(us, 0.01), Geo.percentile(us, 0.99), Geo.percentile(ys, 0.02), Geo.percentile(ys, 0.98), max(1, g.size), sigmaPos[0], sigmaHead, disp)
    }

    private fun weightedStd(v: List<Pair<Double, Double>>): Double {
        if (v.size < 2) return 0.0
        val sw = v.sumOf { it.second }
        val m = v.sumOf { it.first * it.second } / sw
        return sqrt(v.sumOf { it.second * (it.first - m) * (it.first - m) } / sw)
    }

    /** (σ posizione, contributo della dispersione, contributo temporale, σ temporale per punto). */
    private fun positionSigma(pts: WallPoints, f: Fit, g: Map<Int, Pair<Double, Double>>, dispersion: Double, fallback: Double): DoubleArray {
        val groups = max(1, g.size)
        // Con un solo gruppo la dispersione tra viste non si misura: si usa l'RMS del fit (più prudente).
        val dispC = (if (g.size >= 2) dispersion else f.rms) / sqrt(groups.toDouble())
        var s2 = 0.0; var sw = 0.0
        for ((k, i) in f.idx.withIndex()) if (f.inlier[k]) { val t = temporalNormal(pts, i, f.nx, f.nz, fallback); s2 += f.w[k] * t * t; sw += f.w[k] }
        val temporal = if (sw > 0) sqrt(s2 / sw) else fallback
        val tempC = temporal / sqrt(groups.toDouble())
        return doubleArrayOf(sqrt(dispC * dispC + tempC * tempC), dispC, tempC, temporal)
    }

    /** σ dell'orientamento (gradi): dalla dispersione (o RMS) sulla lunghezza, per gruppo di vista. */
    private fun headingSigma(pts: WallPoints, f: Fit, groups: Int, dispersion: Double, us: DoubleArray): Double {
        if (us.size < 2) return 90.0
        val length = max(0.05, us.last() - us.first())
        val spread = max(dispersion, f.rms / sqrt(max(1, groups).toDouble()))
        return atan(spread / (length / sqrt(12.0)) / sqrt(max(1, groups).toDouble())) * 180 / kotlin.math.PI
    }

    private fun compatible(a: Piece, b: Piece, pts: WallPoints, p: WallParams, floorY: Double?): Boolean {
        val angle = Geo.angleDeg(abs(a.fit.nx * b.fit.nx + a.fit.nz * b.fit.nz))
        val angMax = min(p.mergeAngleMaxDeg, max(p.mergeAngleMinDeg, 3 * sqrt(a.m.headingSigma * a.m.headingSigma + b.m.headingSigma * b.m.headingSigma)))
        if (angle > angMax) return false
        val distMax = min(p.mergeDistMaxM, max(p.mergeDistMinM, 3 * sqrt(a.m.positionSigma * a.m.positionSigma + b.m.positionSigma * b.m.positionSigma)))
        // Distanza reciproca: il centro di ciascuno dalla retta dell'altro.
        val ca = a.fit.pointAtCenter(); val cb = b.fit.pointAtCenter()
        if (abs(a.fit.dist(cb[0], cb[1])) > distMax || abs(b.fit.dist(ca[0], ca[1])) > distMax) return false
        // Stesso verso della normale (stessa faccia della parete).
        if (a.fit.nx * b.fit.nx + a.fit.nz * b.fit.nz < 0) return false
        // Quote compatibili: le fasce di altezza si sovrappongono di almeno 20 cm.
        if (min(a.m.topY, b.m.topY) - max(a.m.bottomY, b.m.bottomY) < 0.2) return false
        // RMS compatibili.
        if (max(a.fit.rms, b.fit.rms) > 3 * max(0.005, min(a.fit.rms, b.fit.rms))) return false
        // Continuità: il tratto mancante tra i due, lungo la retta di a, non oltre il massimo.
        val b0 = a.fit.uOf(b.fit.pointAtU(b.m.startU)); val b1 = a.fit.uOf(b.fit.pointAtU(b.m.endU))
        val gap = max(min(b0, b1) - a.m.endU, a.m.startU - max(b0, b1))
        return gap <= p.mergeMaxGapM
    }

    private fun Fit.pointAtCenter() = doubleArrayOf(cx, cz)
    private fun Fit.pointAtU(u: Double) = doubleArrayOf(cx + ux * u, cz + uz * u)
    private fun Fit.uOf(q: DoubleArray) = (q[0] - cx) * ux + (q[1] - cz) * uz

    // ---------- Parete ----------

    private fun wall(pc: Piece, pts: WallPoints, all: List<Piece>, noise: DepthNoiseModel, fallback: Double, input: WallInput, p: WallParams, conf: Map<Int, Double>): EstimatedWall {
        val f = pc.fit
        val diag = mutableListOf<String>()
        val inl = f.idx.indices.filter { f.inlier[it] }
        val us = inl.map { f.u(pts, f.idx[it]) }
        // Celle lungo la linea: tratti osservati e buchi.
        val uMin = us.min(); val uMax = us.max()
        val nCells = max(1, ((uMax - uMin) / p.cellM).toInt() + 1)
        val counts = IntArray(nCells)
        for (u in us) counts[min(nCells - 1, ((u - uMin) / p.cellM).toInt())]++
        val nonZero = counts.filter { it > 0 }.sorted()
        val minCount = max(3, (0.05 * (if (nonZero.isEmpty()) 0 else nonZero[nonZero.size / 2])).toInt())
        val occupied = BooleanArray(nCells) { counts[it] >= minCount }
        val holeCells = (p.mergeHoleM / p.cellM).toInt()
        val spans = mutableListOf<WallSpan>()
        var i = 0
        while (i < nCells) {
            if (!occupied[i]) { i++; continue }
            var j = i
            while (true) {
                var k = j + 1
                while (k < nCells && !occupied[k]) k++
                if (k < nCells && k - j - 1 <= holeCells) j = k else break
            }
            spans.add(WallSpan(uMin + i * p.cellM, uMin + (j + 1) * p.cellM))
            i = j + 1
        }
        // Estremi robusti dentro il primo e l'ultimo tratto osservato.
        val firstSpan = spans.first(); val lastSpan = spans.last()
        // Estremi: il punto più esterno dentro la prima e l'ultima cella OCCUPATA (una cella occupata ha abbastanza punti da non essere
        // rumore isolato): sono punti veri, quindi l'estremo non accorcia e non estende la parete.
        val startU = us.filter { it >= firstSpan.fromU && it < firstSpan.fromU + p.cellM }.minOrNull() ?: firstSpan.fromU
        val endU = us.filter { it < lastSpan.toU && it >= lastSpan.toU - p.cellM }.maxOrNull() ?: lastSpan.toU
        val observed = spans.mapIndexed { k, s -> WallSpan(if (k == 0) startU else s.fromU, if (k == spans.lastIndex) endU else s.toU) }
        val ys = inl.map { pts.y[f.idx[it]] }.sorted().toDoubleArray()
        val bottomY = Geo.percentile(ys, 0.02); val topY = Geo.percentile(ys, 0.98)

        // Gap tra i tratti osservati: motivo dallo spazio libero.
        val gaps = observed.zipWithNext().map { (a, b) -> WallGap(a.toU, b.fromU, gapReason(f, (a.toU + b.fromU) / 2, b.fromU - a.toU, bottomY, topY, input.space)) }

        // Incertezze.
        val g = groupOffsets(pts, f, p)
        val dispersion = weightedStd(g.values.toList())
        val pos = positionSigma(pts, f, g, dispersion, fallback)
        val usSorted = us.sorted().toDoubleArray()
        val head = headingSigma(pts, f, g.size, dispersion, usSorted)
        // R3.1: σ di un'estremità = ripetibilità tra le viste (R3) ⊕ incertezza della zona terminale assottigliata.
        val startEv = endEvidence(pts, f, us, startU, -1, observed, p, fallback)
        val endEv = endEvidence(pts, f, us, endU, +1, observed, p, fallback)
        val startSigma = sqrt(startEv.repeatSigmaM * startEv.repeatSigmaM + startEv.terminalSigmaM * startEv.terminalSigmaM)
        val endSigmaV = sqrt(endEv.repeatSigmaM * endEv.repeatSigmaM + endEv.terminalSigmaM * endEv.terminalSigmaM)

        // Estremità.
        val others = all.filter { it !== pc }
        val start = endState(f, startU, -1, startSigma, startEv, others, input.space, bottomY, topY, p)
        val end = endState(f, endU, +1, endSigmaV, endEv, others, input.space, bottomY, topY, p)
        val continuation = mutableListOf<String>()
        if (start.reason == EndReason.OCCLUDED) continuation.add("possibile continuazione prima di u = ${fmt(startU)} m: nascosta da un oggetto (non misurata)")
        if (end.reason == EndReason.OCCLUDED) continuation.add("possibile continuazione dopo u = ${fmt(endU)} m: nascosta da un oggetto (non misurata)")
        for (gp in gaps.filter { it.reason == GapReason.OCCLUDED }) continuation.add("tratto ${fmt(gp.fromU)}–${fmt(gp.toU)} m nascosto: continuità probabile, non misurata")

        // Evidenza.
        val frames = inl.map { pts.frame[f.idx[it]] }.distinct().size
        val groupIds = inl.map { pts.group[f.idx[it]] }.distinct().sorted()
        val ranges = inl.map { pts.range[f.idx[it]] }.sorted().toDoubleArray()
        val span = viewSpan(groupIds, input.groupCamera, f)
        val length = endU - startU
        val coverage = if (length <= 0) 0.0 else observed.sumOf { it.toU - it.fromU } / length
        val rangeMed = Geo.percentile(ranges, 0.5)
        val q = mutableListOf<String>()
        val quality = when {
            g.size >= 8 && span >= 20 && coverage >= 0.7 && f.rms <= 0.03 && rangeMed <= 2.5 -> EvidenceQuality.HIGH.also { q.add("≥ 8 viste, angoli ≥ 20°, copertura ≥ 70%, RMS ≤ 3 cm, distanza ≤ 2,5 m") }
            g.size >= 3 && coverage >= 0.4 -> EvidenceQuality.MEDIUM.also {
                if (g.size < 8) q.add("${g.size} viste (< 8)"); if (span < 20) q.add("angoli di vista ${fmt(span)}° (< 20°)")
                if (coverage < 0.7) q.add("copertura ${fmt(coverage * 100)}% (< 70%)"); if (f.rms > 0.03) q.add("RMS ${fmt(f.rms * 100)} cm (> 3 cm)")
                if (rangeMed > 2.5) q.add("distanza ${fmt(rangeMed)} m (> 2,5 m)")
            }
            else -> EvidenceQuality.LOW.also { q.add("${g.size} viste, copertura ${fmt(coverage * 100)}%") }
        }
        if (g.size < 2) diag.add("un solo gruppo di vista con abbastanza punti: σ di posizione dall'RMS del fit")
        if (pc.ids.size > 1) diag.add("fusa da ${pc.ids.size} superfici R2: ${pc.ids.joinToString { "S$it" }}")
        val outliers = f.inlier.count { !it }
        val recognition = pc.ids.sumOf { (conf[it] ?: 0.0) * 1.0 } / pc.ids.size

        val floorY = input.floorY
        return EstimatedWall(
            id = -1, sourceSurfaceIds = pc.ids,
            geometry = WallGeometry(
                f.nx, f.nz, f.d, f.cx, f.cz, f.ux, f.uz, startU, endU, bottomY, topY,
                floorY?.let { bottomY - it }, floorY?.let { topY - it }, f.headingDeg, observed,
            ),
            uncertainty = WallUncertainty(
                positionSigmaM = pos[0], headingSigmaDeg = head, startSigmaM = startSigma, endSigmaM = endSigmaV,
                lengthSigmaM = sqrt(startSigma * startSigma + endSigmaV * endSigmaV), temporalSigmaM = pos[3], temporalContributionM = pos[2],
                dispersionContributionM = pos[1], depthSigmaM = noise.sigma(rangeMed),
            ),
            evidence = WallEvidence(
                pointCount = f.idx.size, inlierCount = inl.size, rawFrames = frames, viewGroups = g.size, viewAngleSpanDeg = span,
                rangeMedianM = rangeMed, fitRmsM = f.rms, dispersionM = dispersion, observedCoverage = coverage,
                outlierFraction = outliers.toDouble() / f.idx.size, quality = quality, qualityReasons = q,
            ),
            start = start, end = end, gaps = gaps,
            thickness = WallThickness(ThicknessState.UNKNOWN, note = "una sola faccia osservata"),
            recognitionConfidence = recognition, possibleContinuation = continuation, diagnostics = diag,
        )
    }

    /** σ di un'estremità: dispersione delle estremità dei gruppi di vista che arrivano vicino (≥ 3 gruppi), più il campionamento e la parte temporale lungo la linea. */
    private fun endSigma(pts: WallPoints, f: Fit, endU: Double, start: Boolean, p: WallParams, fallback: Double): Double {
        val byGroup = HashMap<Int, Double>()
        var t2 = 0.0; var tn = 0
        for ((k, i) in f.idx.withIndex()) {
            if (!f.inlier[k]) continue
            val u = f.u(pts, i)
            if (abs(u - endU) > 0.2) continue
            val g = pts.group[i]
            val cur = byGroup[g]
            byGroup[g] = if (cur == null) u else if (start) min(cur, u) else max(cur, u)
            val t = if (pts.dispX[i].isNaN()) fallback else abs(pts.dispX[i] * f.ux + pts.dispZ[i] * f.uz)
            t2 += t * t; tn++
        }
        val ext = byGroup.keys.sorted().map { byGroup.getValue(it) }
        if (ext.size < 3) return p.endFallbackSigmaM
        val m = ext.average()
        val sd = sqrt(ext.sumOf { (it - m) * (it - m) } / (ext.size - 1))
        val temporal = if (tn == 0) fallback else sqrt(t2 / tn) / sqrt(ext.size.toDouble())
        return sqrt(sd * sd + p.samplingSigmaM * p.samplingSigmaM + temporal * temporal)
    }

    /**
     * R3.1 — evidenza terminale di un'estremità ([dir] = −1 inizio, +1 fine). ρ = densità lineare di riferimento (punti affidabili per
     * metro) nel tratto subito all'interno della finestra terminale (o media della parete se l'ultimo tratto è corto). Negli ultimi L metri ci si aspettano ρ·L punti; la "lunghezza mancante" m(L) = L − n(L)/ρ dice quanta
     * parete equivalente manca. La zona assottigliata è max m(L) per L ≤ min([WallParams.endWindowM], ultimo tratto continuo): lì la
     * posizione della fine vera non è determinata, e la si tratta come uniforme: σ_terminale = zona / √12. La σ di ripetibilità è quella
     * di R3 ([endSigma]), invariata. Nessuna soglia sulla densità.
     */
    private fun endEvidence(pts: WallPoints, f: Fit, us: List<Double>, endU: Double, dir: Int, spans: List<WallSpan>, p: WallParams, fallback: Double): EndEvidence {
        val last = if (dir > 0) spans.last() else spans.first()
        val lastLen = last.toU - last.fromU
        val observedLen = spans.sumOf { it.toU - it.fromU }
        val window = min(p.endWindowM, lastLen)
        val all = us.map { (it - endU) * -dir } // distanze dall'estremità verso l'interno
        // Densità di riferimento LOCALE: il tratto subito all'interno della finestra ([W, 2W] dentro l'ultimo tratto continuo), così si
        // misura il diradamento verso la fine e non la variazione di densità lungo la parete; se manca, la media della parete.
        val refTo = min(2 * window, lastLen)
        val rho = if (refTo - window >= p.cellM) all.count { it >= window && it < refTo } / (refTo - window)
            else if (observedLen > 0) us.size / observedLen else 0.0
        val ts = all.filter { it >= 0 && it <= window }.sorted()
        var thinZone = 0.0
        if (rho > 0) {
            for ((k, t) in ts.withIndex()) thinZone = max(thinZone, t - k / rho) // appena prima del k-esimo punto: k punti visti in [0, t)
            thinZone = max(thinZone, window - ts.size / rho)
        }
        thinZone = min(thinZone, window)
        fun density(len: Double): Double {
            if (rho <= 0) return 0.0
            val n = us.count { u -> val t = (u - endU) * -dir; t >= 0 && t < len }
            return n / (rho * len)
        }
        val gap = if (spans.size < 2) null else if (dir > 0) spans[spans.size - 1].fromU - spans[spans.size - 2].toU else spans[1].fromU - spans[0].toU
        val groups = HashSet<Int>()
        for ((k, i) in f.idx.withIndex()) if (f.inlier[k] && abs(f.u(pts, i) - endU) <= 0.2) groups.add(pts.group[i])
        return EndEvidence(
            density(0.05), density(0.10), density(0.15), density(0.20), thinZone, lastLen, gap, groups.size,
            endSigma(pts, f, endU, dir < 0, p, fallback), thinZone / sqrt(12.0), null, null, null,
        )
    }

    private fun endState(f: Fit, u: Double, dir: Int, sigma: Double, ev: EndEvidence, others: List<Piece>, space: SpaceQuery, bottomY: Double, topY: Double, p: WallParams): WallEnd {
        val e = f.pointAtU(u)
        // Superficie strutturale non parallela che passa vicino all'estremità (come in R3): candidata per un angolo.
        var best: Piece? = null; var bestGap = 0.0
        for (o in others) {
            val angle = Geo.angleDeg(abs(o.fit.nx * f.nx + o.fit.nz * f.nz))
            if (angle < 30) continue
            val along = o.fit.uOf(e)
            if (!(abs(o.fit.dist(e[0], e[1])) <= 0.15 && along >= o.m.startU - 0.15 && along <= o.m.endU + 0.15)) continue
            // R3.1: distanza lungo questa linea fino all'intersezione con l'altra superficie (positiva = oltre i dati).
            val den = o.fit.nx * f.ux + o.fit.nz * f.uz
            if (abs(den) < 1e-9) continue
            val gap = (-o.fit.dist(f.cx, f.cz) / den - u) * dir
            if (best == null || abs(gap) < abs(bestGap) || (abs(gap) == abs(bestGap) && o.ids.min() < best.ids.min())) { best = o; bestGap = gap }
        }
        val z = if (best == null) null else bestGap / sigma
        val evidence = ev.copy(surfaceId = best?.ids?.min(), surfaceGapM = best?.let { bestGap }, surfaceGapSigmas = z)
        // R3.1: CORNER solo se i dati arrivano all'intersezione entro le σ E l'evidenza terminale è forte; altrimenti l'altra superficie è
        // solo "vicina" (SURFACE_NEAR, estremità non osservata). Se i dati superano l'altra superficie oltre le σ, non è un angolo.
        val reach = z != null && abs(z) <= p.cornerReachSigmas
        val nearOnly = (z != null && !reach && z > 0) || (reach && !ev.strong)
        val reason = if (reach && ev.strong) EndReason.CORNER else if (nearOnly) EndReason.SURFACE_NEAR else {
            var front = 0; var through = 0; var seen = 0; var total = 0
            val y0 = bottomY + 0.2; val y1 = max(y0, min(topY, bottomY + 1.8))
            var t = 0.05
            while (t <= 0.35) {
                val uu = u + dir * t
                val q = f.pointAtU(uu)
                var y = y0
                while (y <= y1 + 1e-9) {
                    total++
                    // Davanti alla parete (verso le camere): un oggetto?
                    if (listOf(0.15, 0.3, 0.5).any { off -> space.state(q[0] + f.nx * off, y, q[1] + f.nz * off) == 2 }) front++
                    // Sul piano e subito dietro: si vede attraverso?
                    val onPlane = space.state(q[0], y, q[1]); val behind = space.state(q[0] - f.nx * 0.15, y, q[1] - f.nz * 0.15)
                    if (onPlane == 1 && behind == 1) through++
                    if (onPlane > 0 || behind > 0) seen++
                    y += 0.25
                }
                t += 0.1
            }
            when {
                total > 0 && front >= 0.3 * total -> EndReason.OCCLUDED
                total > 0 && through >= 0.3 * total -> EndReason.FREE_BEYOND
                else -> EndReason.NOT_SEEN
            }
        }
        val state = when {
            reason != EndReason.CORNER && sigma > p.unstableEndM -> EndState.UNCERTAIN
            reason == EndReason.CORNER || reason == EndReason.FREE_BEYOND -> EndState.OBSERVED
            else -> EndState.PARTIAL
        }
        return WallEnd(u, state, if (state == EndState.UNCERTAIN) EndReason.UNSTABLE else reason, sigma, evidence)
    }

    /**
     * Una parete prosegue fino al soffitto. Sul piano della superficie, sopra il suo bordo superiore (da +15 cm fino a 2,4 m dal
     * pavimento, il minimo di un soffitto), si guarda riga per riga (10 cm) dove i raggi hanno attraversato il piano (visto libero sul
     * piano e subito dietro). Se c'è una fascia di almeno 3 righe consecutive (30 cm) attraversate ciascuna in almeno
     * [WallParams.maxFreeAbove] dei punti osservati, la superficie non arriva al soffitto: oggetto, non parete. Torna la frazione
     * attraversata della fascia migliore (0 se nessuna), null se sopra non c'è abbastanza osservazione.
     */
    private fun freeAbove(f: Fit, m: Summary, space: SpaceQuery, floorY: Double?): Double? {
        val ceiling = (floorY ?: m.bottomY) + 2.4
        val rows = mutableListOf<Pair<Int, Int>>() // (attraversati, osservati) per riga
        var y = m.topY + 0.15
        while (y <= ceiling) {
            var through = 0; var seen = 0
            var u = m.startU
            while (u <= m.endU) {
                val q = f.pointAtU(u)
                val on = space.state(q[0], y, q[1]); val behind = space.state(q[0] - f.nx * 0.15, y, q[1] - f.nz * 0.15)
                if (on > 0 || behind > 0) { seen++; if (on == 1 && behind == 1) through++ }
                u += 0.1
            }
            rows.add(through to seen)
            y += 0.1
        }
        if (rows.sumOf { it.second } < 10) return null
        var best = 0.0
        for (i in 0..rows.size - 3) {
            val band = rows.subList(i, i + 3)
            if (band.any { it.second < 3 }) continue
            val worst = band.minOf { it.first.toDouble() / it.second }
            best = max(best, worst)
        }
        return best
    }

    private fun gapReason(f: Fit, u: Double, length: Double, bottomY: Double, topY: Double, space: SpaceQuery): GapReason {
        var front = 0; var through = 0; var total = 0
        val y0 = bottomY + 0.2; val y1 = max(y0, min(topY, bottomY + 1.8))
        var t = -length / 2 + 0.025
        while (t <= length / 2) {
            val q = f.pointAtU(u + t)
            var y = y0
            while (y <= y1 + 1e-9) {
                total++
                if (listOf(0.15, 0.3, 0.5).any { off -> space.state(q[0] + f.nx * off, y, q[1] + f.nz * off) == 2 }) front++
                if (space.state(q[0], y, q[1]) == 1 && space.state(q[0] - f.nx * 0.15, y, q[1] - f.nz * 0.15) == 1) through++
                y += 0.25
            }
            t += 0.05
        }
        return when {
            total > 0 && front >= 0.3 * total -> GapReason.OCCLUDED
            total > 0 && through >= 0.3 * total -> GapReason.SEEN_THROUGH
            else -> GapReason.NOT_SEEN
        }
    }

    private fun viewSpan(groups: List<Int>, cams: Map<Int, DoubleArray>, f: Fit): Double {
        val dirs = groups.mapNotNull { g -> cams[g]?.let { c -> val dx = f.cx - c[0]; val dz = f.cz - c[2]; val l = sqrt(dx * dx + dz * dz); if (l < 1e-9) null else doubleArrayOf(dx / l, dz / l) } }
        var best = 0.0
        for (a in dirs.indices) for (b in a + 1 until dirs.size) best = max(best, Geo.angleDeg(dirs[a][0] * dirs[b][0] + dirs[a][1] * dirs[b][1]))
        return best
    }

    // ---------- Spessore ----------

    /**
     * Spessore solo da due facce della stessa parete: quasi parallele (≤ 5°), normali opposte, la seconda dietro la prima tra 3 e
     * 45 cm (e viceversa), sovrapposte per almeno 30 cm lungo la linea, e nessuno spazio libero visto tra le due (il corpo del muro).
     */
    private fun thickness(walls: List<EstimatedWall>, space: SpaceQuery): List<EstimatedWall> {
        val result = walls.toMutableList()
        for (a in walls.indices) for (b in a + 1 until walls.size) {
            val wa = walls[a].geometry; val wb = walls[b].geometry
            val dot = wa.nx * wb.nx + wa.nz * wb.nz
            if (dot > -0.996) continue // ≥ 5° o non opposte
            val sab = wa.nx * wb.cx + wa.nz * wb.cz - wa.d // b dietro a: negativo
            val sba = wb.nx * wa.cx + wb.nz * wa.cz - wb.d
            if (sab !in -0.45..-0.03 || sba !in -0.45..-0.03) continue
            // Sovrapposizione lungo la linea di a.
            fun uOn(g: WallGeometry, x: Double, z: Double) = (x - g.cx) * g.ux + (z - g.cz) * g.uz
            val pb0 = wb.pointAt(wb.startU); val pb1 = wb.pointAt(wb.endU)
            val b0 = uOn(wa, pb0[0], pb0[1]); val b1 = uOn(wa, pb1[0], pb1[1])
            val lo = max(wa.startU, min(b0, b1)); val hi = min(wa.endU, max(b0, b1))
            if (hi - lo < 0.3) continue
            // Tra le due facce non deve esserci spazio visto libero.
            var free = 0; var tot = 0
            var u = lo
            val y0 = max(wa.bottomY, wb.bottomY) + 0.2; val y1 = min(wa.topY, wb.topY) - 0.1
            while (u <= hi) {
                val q = wa.pointAt(u)
                var y = y0
                while (y <= y1) { tot++; if (space.state(q[0] + wa.nx * sab / 2, y, q[1] + wa.nz * sab / 2) == 1) free++; y += 0.25 }
                u += 0.1
            }
            val thicknessM = (abs(sab) + abs(sba)) / 2
            val sigma = sqrt(walls[a].uncertainty.positionSigmaM.let { it * it } + walls[b].uncertainty.positionSigmaM.let { it * it })
            if (tot > 0 && free > 0.1 * tot) {
                val note = "faccia opposta W${walls[b].id}/W${walls[a].id} a ${fmt(thicknessM * 100)} cm ma spazio libero visto tra le due: non è lo stesso muro"
                result[a] = result[a].copy(thickness = WallThickness(ThicknessState.UNKNOWN, note = note))
                result[b] = result[b].copy(thickness = WallThickness(ThicknessState.UNKNOWN, note = note))
                continue
            }
            result[a] = result[a].copy(thickness = WallThickness(ThicknessState.MEASURED, thicknessM, sigma, walls[b].id, "due facce: W${walls[a].id} e W${walls[b].id}"))
            result[b] = result[b].copy(thickness = WallThickness(ThicknessState.MEASURED, thicknessM, sigma, walls[a].id, "due facce: W${walls[a].id} e W${walls[b].id}"))
        }
        return result
    }

    private fun fmt(v: Double) = (kotlin.math.round(v * 100) / 100).toString()

    // ---------- Adattatore da R1/R2 ----------

    /**
     * Dall'esito di R1/R2 all'ingresso di R3: i punti originali delle superfici verticali, il gruppo di vista e la camera, e per ogni
     * punto lo spostamento tra la posa C (frame della depth filtrata) e la posa A usata dalla mappa: p_C = T_C · T_A⁻¹ · p_A. Per le
     * depth filtrate di ripiego A = C (spostamento 0); se la posa C manca lo spostamento è ignoto (NaN → ripiego documentato).
     */
    fun inputFrom(map: GlobalMap, s: SurfaceResult, r: ScanRecording?): WallInput {
        val vertical = s.surfaces.filter { it.orientation == Orientation.VERTICAL }
        val lists = vertical.map { SurfaceExtractor.pointsOf(map, it.memberVoxels) }
        val all = lists.flatMap { it.toList() }.distinct().sorted().toIntArray()
        val local = HashMap<Int, Int>(all.size * 2).also { m -> for ((k, i) in all.withIndex()) m[i] = k }
        val depthBySeq = r?.depth?.associateBy { it.seq } ?: emptyMap()
        val poseBySeq = r?.poses?.associateBy { it.seq } ?: emptyMap()
        val cPose = map.frames.map { f ->
            when {
                f.use == DepthUse.FILTERED_FALLBACK -> f.pose
                else -> depthBySeq[f.depthSeq]?.poseFrameSeq?.let { poseBySeq[it]?.camera }
            }
        }
        val n = all.size
        val pts = WallPoints(
            DoubleArray(n) { map.points.x[all[it]].toDouble() }, DoubleArray(n) { map.points.y[all[it]].toDouble() }, DoubleArray(n) { map.points.z[all[it]].toDouble() },
            DoubleArray(n) { map.points.weight[all[it]].toDouble() }, DoubleArray(n) { map.points.range[all[it]].toDouble() },
            IntArray(n) { map.frames[map.points.frame[all[it]]].viewGroup }, IntArray(n) { map.points.frame[all[it]] },
            DoubleArray(n), DoubleArray(n), DoubleArray(n),
        )
        for (k in 0 until n) {
            val fi = map.points.frame[all[k]]
            val a = map.frames[fi].pose
            val c = cPose[fi]
            if (c == null) { pts.dispX[k] = Double.NaN; pts.dispY[k] = Double.NaN; pts.dispZ[k] = Double.NaN; continue }
            val loc = RecPose(0.0, 0.0, 0.0, -a.qx, -a.qy, -a.qz, a.qw).rotate(pts.x[k] - a.x, pts.y[k] - a.y, pts.z[k] - a.z)
            val q = c.transformPoint(loc.x, loc.y, loc.z)
            pts.dispX[k] = q.x - pts.x[k]; pts.dispY[k] = q.y - pts.y[k]; pts.dispZ[k] = q.z - pts.z[k]
        }
        val surfaces = vertical.mapIndexed { vi, sf -> WallInputSurface(sf.id, sf.kind, sf.orientation, sf.kindConfidence, IntArray(lists[vi].size) { local.getValue(lists[vi][it]) }) }
        val cams = HashMap<Int, DoubleArray>()
        for (f in map.frames) if (f.viewGroup !in cams) cams[f.viewGroup] = doubleArrayOf(f.pose.x, f.pose.y, f.pose.z)
        return WallInput(pts, surfaces, s.floor?.y, { x, y, z -> map.freeSpace.state(x, y, z) }, cams)
    }
}
