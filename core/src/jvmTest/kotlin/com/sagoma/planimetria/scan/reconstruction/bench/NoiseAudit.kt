package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.DepthToWorld
import com.sagoma.planimetria.scan.reconstruction.DepthUse
import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.Moments
import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.ScanRecording
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * AUDIT (solo test) — RUMORE DELLA DEPTH in funzione della distanza, su osservazioni reali o simulate, SENZA ground truth.
 *
 * Riferimento: superfici grandi e planari. R2 serve solo a SELEZIONARE le candidate; ogni piano viene rifittato in modo robusto su
 * tutti i punti della mappa nella sua fascia (non sui membri della regione R2) e accettato solo se supera controlli di planarità
 * e stabilità. Per ogni punto: scarto sulla normale → spostamento lungo il raggio (÷ cos incidenza, incidenze ≤ 60°) → errore
 * sulla profondità Z lungo l'asse ottico (× z / distanza), la stessa grandezza che il simulatore perturba (σ(z)).
 * Componenti separate: TOTALE (piano globale: rumore + posa/deriva tra frame + non planarità) e INTRA-FRAME (piano rifittato sui
 * soli punti di ogni frame: tolto l'offset medio di ogni frame, cioè lo spostamento di posa/deriva lungo la normale) e INTER-FRAME
 * (deviazione standard di quegli offset tra frame).
 */
object NoiseAudit {
    val binEdges = doubleArrayOf(0.25, 0.75, 1.25, 1.75, 2.25, 2.75, 3.25, 3.75, 4.25)
    const val MIN_POINTS = 500
    const val MIN_FRAMES = 5
    const val MAX_INCIDENCE_DEG = 60.0

    class RefSurface(
        val id: Int, val kind: SurfaceKind, val areaM2: Double, val frames: Int, val arcore: Int?,
        val nx: Double, val ny: Double, val nz: Double, val d: Double, val points: Int,
        val robustSigmaM: Double, val medianResidualM: Double, val quadrantSpreadM: Double, val accepted: Boolean, val reasons: List<String>,
        /** Estensione (ridotta di 10 cm) nella base della superficie: centroide, assi u e v, limiti. */
        val c: DoubleArray, val u: DoubleArray, val v: DoubleArray, val uMin: Double, val uMax: Double, val vMin: Double, val vMax: Double,
        /** Indici dei punti della mappa usati (fascia ±15 cm, estensione ridotta di 10 cm dai bordi). */
        val idx: IntArray,
    )

    /** Un punto su una superficie di riferimento. [ez]: errore sulla profondità Z (m); [ezIntra]: lo stesso rispetto al piano del frame. */
    class Sample(val surf: Int, val frame: Int, val range: Double, val z: Double, val incDeg: Double, val conf: Int, val ez: Double, var ezIntra: Double = Double.NaN)

    private fun robust(v: DoubleArray): Double { if (v.isEmpty()) return Double.NaN; val s = v.sortedArray(); val m = s[s.size / 2]; val dev = s.map { abs(it - m) }.sorted(); return 1.4826 * dev[dev.size / 2] }
    private fun median(v: DoubleArray): Double = if (v.isEmpty()) Double.NaN else v.sortedArray()[v.size / 2]
    private fun std(v: DoubleArray): Double { if (v.size < 2) return Double.NaN; val m = v.average(); return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1)) }

    /** Superfici di riferimento: candidate da R2 (verticali o pavimento, ≥ 0,5 m², ≥ 5 viste), rifittate e controllate. */
    fun references(map: GlobalMap, s: SurfaceResult): List<RefSurface> {
        val out = mutableListOf<RefSurface>()
        val pts = map.points
        for (sf in s.surfaces.filter { (it.orientation == Orientation.VERTICAL || it.kind == SurfaceKind.FLOOR) && it.areaM2 >= 0.5 && it.effectiveFrames >= MIN_FRAMES }) {
            val c = sf.plane.centroid
            val band = mutableListOf<Int>()
            for (i in 0 until pts.size) {
                val x = pts.x[i].toDouble(); val y = pts.y[i].toDouble(); val z = pts.z[i].toDouble()
                if (abs(sf.plane.distance(x, y, z)) > 0.15) continue
                val du = (x - c[0]) * sf.u[0] + (y - c[1]) * sf.u[1] + (z - c[2]) * sf.u[2]
                val dv = (x - c[0]) * sf.v[0] + (y - c[1]) * sf.v[1] + (z - c[2]) * sf.v[2]
                if (du < sf.uMin + 0.10 || du > sf.uMax - 0.10 || dv < sf.vMin + 0.10 || dv > sf.vMax - 0.10) continue
                band.add(i)
            }
            if (band.size < 100) continue
            // Rifit robusto (Huber) su tutti i punti della fascia.
            var nx = sf.plane.nx; var ny = sf.plane.ny; var nz = sf.plane.nz; var d = sf.plane.d
            var sig = 0.02
            repeat(6) {
                val res = DoubleArray(band.size) { k -> val i = band[k]; nx * pts.x[i] + ny * pts.y[i] + nz * pts.z[i] - d }
                sig = max(0.002, robust(res))
                val m = Moments(map.origin[0], map.origin[1], map.origin[2])
                for ((k, i) in band.withIndex()) { val r = abs(res[k]); val w = if (r <= 1.5 * sig) 1.0 else 1.5 * sig / r; m.add(pts.x[i].toDouble(), pts.y[i].toDouble(), pts.z[i].toDouble(), w) }
                val p = m.plane() ?: return@repeat
                val sgn = if (p.nx * nx + p.ny * ny + p.nz * nz < 0) -1.0 else 1.0
                nx = p.nx * sgn; ny = p.ny * sgn; nz = p.nz * sgn; d = p.d * sgn
            }
            val res = DoubleArray(band.size) { k -> val i = band[k]; nx * pts.x[i] + ny * pts.y[i] + nz * pts.z[i] - d }
            val rob = robust(res); val med = median(res)
            // Planarità: mediana degli scarti nei quattro quadranti dell'estensione.
            val q = Array(4) { mutableListOf<Double>() }
            for ((k, i) in band.withIndex()) {
                val x = pts.x[i].toDouble(); val y = pts.y[i].toDouble(); val z = pts.z[i].toDouble()
                val du = (x - c[0]) * sf.u[0] + (y - c[1]) * sf.u[1] + (z - c[2]) * sf.u[2]
                val dv = (x - c[0]) * sf.v[0] + (y - c[1]) * sf.v[1] + (z - c[2]) * sf.v[2]
                q[(if (du > (sf.uMin + sf.uMax) / 2) 1 else 0) + (if (dv > (sf.vMin + sf.vMax) / 2) 2 else 0)].add(res[k])
            }
            val qm = q.filter { it.size >= 30 }.map { median(it.toDoubleArray()) }
            val spread = if (qm.size < 2) Double.NaN else qm.max() - qm.min()
            val frames = band.map { pts.frame[it] }.distinct().size
            val reasons = mutableListOf<String>()
            if (frames < MIN_FRAMES) reasons.add("$frames frame (< $MIN_FRAMES)")
            if (abs(med) > 0.25 * rob) reasons.add("scarti asimmetrici (mediana ${f(med * 100, 2)} cm)")
            if (!spread.isNaN() && spread > rob) reasons.add("non planare: quadranti differiscono di ${f(spread * 100, 1)} cm > σ robusta ${f(rob * 100, 1)} cm")
            if (spread.isNaN()) reasons.add("estensione insufficiente per il controllo di planarità")
            out.add(RefSurface(sf.id, sf.kind, sf.areaM2, frames, sf.arcorePlane, nx, ny, nz, d, band.size, rob, med, spread, reasons.isEmpty(), reasons, c, sf.u, sf.v, sf.uMin + 0.10, sf.uMax - 0.10, sf.vMin + 0.10, sf.vMax - 0.10, band.toIntArray()))
        }
        return out
    }

    /** Campioni sui riferimenti accettati, da una mappa (raw o filtrata) costruita sulla stessa registrazione. */
    fun samples(map: GlobalMap, refs: List<RefSurface>, useBandOf: GlobalMap? = null): List<Sample> {
        val out = mutableListOf<Sample>()
        val pts = map.points
        for (r in refs.filter { it.accepted }) {
            val idx = if (useBandOf == null) r.idx else (0 until pts.size).filter { i -> abs(r.nx * pts.x[i] + r.ny * pts.y[i] + r.nz * pts.z[i] - r.d) <= 0.15 && near(map, r, i) }.toIntArray()
            for (i in idx) {
                val f = map.frames[pts.frame[i]]
                val dx = pts.x[i] - f.pose.x; val dy = pts.y[i] - f.pose.y; val dz = pts.z[i] - f.pose.z
                val range = sqrt(dx * dx + dy * dy + dz * dz)
                val fw = DepthToWorld.viewDirection(f.pose)
                val zDepth = dx * fw[0] + dy * fw[1] + dz * fw[2]
                val cosInc = abs((dx * r.nx + dy * r.ny + dz * r.nz) / range)
                val inc = Math.toDegrees(acos(min(1.0, cosInc)))
                if (inc > MAX_INCIDENCE_DEG || zDepth <= 0) continue
                val resid = r.nx * pts.x[i] + r.ny * pts.y[i] + r.nz * pts.z[i] - r.d
                val eRay = resid / cosInc
                val conf = if (f.use == DepthUse.RAW) (pts.weight[i] * 255).toInt() else -1
                out.add(Sample(r.id, pts.frame[i], range, zDepth, inc, conf, eRay * zDepth / range))
            }
        }
        intraFrame(map, out)
        return out
    }

    /** Il punto della mappa [i] sta nell'estensione del riferimento? (per la mappa filtrata: vicino a un punto della fascia raw). */
    private fun near(map: GlobalMap, r: RefSurface, i: Int): Boolean {
        val x = map.points.x[i] - r.c[0]; val y = map.points.y[i] - r.c[1]; val z = map.points.z[i] - r.c[2]
        val du = x * r.u[0] + y * r.u[1] + z * r.u[2]; val dv = x * r.v[0] + y * r.v[1] + z * r.v[2]
        return du in r.uMin..r.uMax && dv in r.vMin..r.vMax
    }

    /** Errore intra-frame: tolto l'offset medio dei punti dello stesso frame sulla stessa superficie (≥ 30 punti). */
    private fun intraFrame(map: GlobalMap, s: List<Sample>) {
        val groups = s.indices.groupBy { s[it].surf * 100000L + s[it].frame }
        for ((_, g) in groups) {
            if (g.size < 30) continue
            // Rimuove lo spostamento del frame lungo la normale (posa/deriva); un'eventuale rotazione residua resta.
            val mean = g.map { s[it].ez }.average()
            for (k in g) s[k].ezIntra = s[k].ez - mean
        }
    }

    class BinRow(
        val lo: Double, val hi: Double, val n: Int, val frames: Int, val sigmaStd: Double, val sigmaRobust: Double, val sigmaIntraRobust: Double,
        val sigmaInterFrame: Double, val medianConf: Double, val medianIncDeg: Double, val medianZ: Double, val status: String,
    )

    /** Tabella per fascia di distanza (camera → punto). */
    fun bins(s: List<Sample>): List<BinRow> = (0 until binEdges.size - 1).map { b ->
        val lo = binEdges[b]; val hi = binEdges[b + 1]
        val sel = s.filter { it.range >= lo && it.range < hi }
        val frames = sel.map { it.frame }.distinct().size
        val ez = DoubleArray(sel.size) { sel[it].ez }
        val intra = sel.filter { !it.ezIntra.isNaN() }.map { it.ezIntra }.toDoubleArray()
        val perFrame = sel.groupBy { it.surf * 100000L + it.frame }.values.filter { it.size >= 30 }.map { g -> g.map { it.ez }.average() }.toDoubleArray()
        val conf = sel.filter { it.conf >= 0 }.map { it.conf.toDouble() }.toDoubleArray()
        val ok = sel.size >= MIN_POINTS && frames >= MIN_FRAMES
        BinRow(lo, hi, sel.size, frames, std(ez), robust(ez), robust(intra), std(perFrame), median(conf), median(sel.map { it.incDeg }.toDoubleArray()), median(sel.map { it.z }.toDoubleArray()), if (ok) "OK" else "INSUFFICIENT_EVIDENCE")
    }

    class ModelFit(val name: String, val k: Int, val a: Double, val logLik: Double, val bic: Double, val sigmaAt: (Double) -> Double)

    /**
     * Confronto dei modelli σ(z) per l'errore Z ([intra] = rispetto al piano del frame), con massima verosimiglianza gaussiana sugli
     * scarti ripuliti (entro 5 σ robuste della fascia) e BIC (penalizza i parametri: niente overfitting). Solo fasce con evidenza.
     */
    fun fitModels(s: List<Sample>, intra: Boolean): List<ModelFit> {
        val rows = bins(s)
        val use = mutableListOf<Pair<Double, Double>>() // (z, e)
        for ((b, row) in rows.withIndex()) {
            if (row.status != "OK") continue
            val sr = if (intra) row.sigmaIntraRobust else row.sigmaRobust
            for (p in s) if (p.range >= binEdges[b] && p.range < binEdges[b + 1]) {
                val e = if (intra) p.ezIntra else p.ez
                if (!e.isNaN() && abs(e) <= 5 * sr) use.add(p.z to e)
            }
        }
        if (use.isEmpty()) return emptyList()
        val n = use.size.toDouble()
        fun fit(name: String, g: (Double) -> Double): ModelFit {
            val a2 = use.sumOf { (z, e) -> e * e / (g(z) * g(z)) } / n
            val a = sqrt(a2)
            val ll = use.sumOf { (z, e) -> val sg = a * g(z); -ln(sg) - e * e / (2 * sg * sg) } - n / 2 * ln(2 * Math.PI)
            return ModelFit(name, 1, a, ll, -2 * ll + ln(n), { z -> a * g(z) })
        }
        val models = mutableListOf(fit("costante", { 1.0 }), fit("∝ z", { it }), fit("∝ z²", { it * it }))
        // A tratti: una σ per fascia (MLE per fascia sulla z del punto).
        val okBins = rows.indices.filter { rows[it].status == "OK" }
        val sig = HashMap<Int, Double>()
        var ll = 0.0
        for (b in okBins) {
            val inBin = s.filter { it.range >= binEdges[b] && it.range < binEdges[b + 1] }.map { if (intra) it.ezIntra else it.ez }.filter { !it.isNaN() }
            val sr = if (intra) rows[b].sigmaIntraRobust else rows[b].sigmaRobust
            val e = inBin.filter { abs(it) <= 5 * sr }
            if (e.isEmpty()) continue
            val sg = sqrt(e.sumOf { it * it } / e.size)
            sig[b] = sg
            ll += e.sumOf { -ln(sg) - it * it / (2 * sg * sg) } - e.size / 2.0 * ln(2 * Math.PI)
        }
        models.add(ModelFit("a tratti (${sig.size} fasce)", sig.size, Double.NaN, ll, -2 * ll + sig.size * ln(n), { z ->
            val b = okBins.minByOrNull { abs(rows[it].medianZ - z) } ?: 0; sig[b] ?: Double.NaN
        }))
        return models.sortedBy { it.bic }
    }

    // ------------------------------------------------------------------------------------- raw contro filtrata, densità

    class PixelRow(
        val lo: Double, val hi: Double, val filteredValid: Int, val rawValid: Int, val rawZero: Int, val rawLowConf: Int,
        val bothValid: Int, val diffMedianM: Double, val diffRobustM: Double, val keptFraction: Double, val pointsPerM2PerFrame: Double,
    )

    /**
     * Confronto pixel per pixel nello stesso depth keyframe (stessa risoluzione): distanza di riferimento = depth filtrata (più densa).
     * [minConf]: soglia di confidenza di R1. Densità teorica per frame su una superficie frontale: fx·fy/z² × frazione tenuta.
     * NB: raw e filtrata possono venire da istanti diversi (regola M0.2): la differenza include anche quel possibile scarto.
     */
    fun rawVsFiltered(r: ScanRecording, read: (String) -> ByteArray?, minConf: Int = 32): Pair<List<PixelRow>, String> {
        val n = binEdges.size - 1
        val fv = IntArray(n); val rv = IntArray(n); val rz = IntArray(n); val rl = IntArray(n); val bv = IntArray(n)
        val diffs = Array(n) { mutableListOf<Double>() }
        var fxfy = Double.NaN; var frames = 0; var dtMax = 0L; var mismatch = 0
        for (d in r.depth.sortedBy { it.seq }) {
            val rp = d.rawPath ?: continue
            val raw = read(rp) ?: continue; val fil = read(d.path) ?: continue
            val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
            if (w != d.width || h != d.height || raw.size != w * h * 2 || fil.size != w * h * 2) { mismatch++; continue }
            val conf = d.confidencePath?.let { read(it) }?.takeIf { it.size == w * h }
            val mr = DepthRaw.decode(raw, w, h); val mf = DepthRaw.decode(fil, w, h)
            d.intrinsics?.let { if (fxfy.isNaN()) fxfy = it.fx * it.fy }
            frames++
            dtMax = max(dtMax, abs((d.rawTimestampNs ?: d.timestampNs) - d.timestampNs))
            for (i in 0 until w * h) {
                if (mf[i] <= 0) continue
                val z = mf[i] / 1000.0
                val b = (0 until n).firstOrNull { z >= binEdges[it] && z < binEdges[it + 1] } ?: continue
                fv[b]++
                val c = conf?.get(i)?.toInt()?.and(0xFF) ?: 255
                when {
                    mr[i] <= 0 -> rz[b]++
                    c < minConf -> rl[b]++
                    else -> { rv[b]++; bv[b]++; if (diffs[b].size < 200000) diffs[b].add((mr[i] - mf[i]) / 1000.0) }
                }
            }
        }
        val rows = (0 until n).map { b ->
            val zc = (binEdges[b] + binEdges[b + 1]) / 2
            val kept = if (fv[b] == 0) Double.NaN else rv[b].toDouble() / fv[b]
            PixelRow(binEdges[b], binEdges[b + 1], fv[b], rv[b], rz[b], rl[b], bv[b], median(diffs[b].toDoubleArray()), robust(diffs[b].toDoubleArray()), kept, fxfy / (zc * zc) * kept)
        }
        return rows to "frame confrontati $frames · risoluzioni diverse $mismatch · fx·fy ${f(fxfy, 0)} · scarto temporale raw↔filtrata max ${f(dtMax / 1e6, 0)} ms"
    }

    internal fun f(v: Double, d: Int = 2) = "%.${d}f".format(java.util.Locale.ROOT, v)
}
