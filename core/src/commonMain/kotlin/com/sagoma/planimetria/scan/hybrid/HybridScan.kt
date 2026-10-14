package com.sagoma.planimetria.scan.hybrid

import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.CameraIntrinsics
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.reconstruction.DepthFrame
import com.sagoma.planimetria.scan.reconstruction.DepthToWorld
import com.sagoma.planimetria.scan.reconstruction.DepthUse
import com.sagoma.planimetria.scan.reconstruction.ReconParams
import com.sagoma.planimetria.scan.sketch.RoomSketch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * I punti di UNA profondità, nel mondo ARCore. [central]: il punto cade nella zona centrale dell'immagine (metà × metà), quella
 * usata per stimare i riferimenti. Solo profondità grezza con confidenza: niente piani di ARCore.
 */
class DepthPoints(
    val frameId: Int,
    val timeMs: Long,
    val camX: Double, val camY: Double, val camZ: Double,
    val x: DoubleArray, val y: DoubleArray, val z: DoubleArray,
    val central: BooleanArray,
    /** Pixel dell'immagine (per dire quanta parte era valida). */
    val pixels: Int,
    /**
     * Per scegliere i punti misurati meglio (selezione esplorativa, [HybridThresholds.pointMaxRangeM] e seguenti): confidenza
     * 0..255, distanza dalla camera (m), distanza dal centro dell'immagine (0 = centro, 1 = metà larghezza/altezza). Null se non note.
     */
    val confidence: IntArray? = null,
    val rangeM: DoubleArray? = null,
    val imageRadius: DoubleArray? = null,
) {
    val size: Int get() = x.size

    companion object {
        /**
         * Dalla profondità grezza (mm), con la sua confidenza (0..255), alle coordinate del mondo: gli stessi filtri della
         * ricostruzione offline (confidenza ≥ 32, 0,3–4 m, bordi) — [ReconParams] predefiniti.
         */
        fun fromDepth(
            frameId: Int, timeMs: Long, mm: IntArray, confidence: ByteArray?, k: CameraIntrinsics, pose: RecPose,
            params: ReconParams = ReconParams(),
        ): DepthPoints {
            val xs = ArrayList<Double>(); val ys = ArrayList<Double>(); val zs = ArrayList<Double>(); val cs = ArrayList<Boolean>()
            val conf = ArrayList<Int>(); val range = ArrayList<Double>(); val radius = ArrayList<Double>()
            val w = k.width; val h = k.height
            DepthToWorld.convert(DepthFrame(frameId, DepthUse.RAW, timeMs * 1_000_000L, pose, k, mm, confidence, null, null), params) { p ->
                xs += p.x; ys += p.y; zs += p.z
                cs += p.u >= w / 4 && p.u < w - w / 4 && p.v >= h / 4 && p.v < h - h / 4
                conf += confidence?.let { it[p.v * w + p.u].toInt() and 0xFF } ?: 255
                range += p.rangeM
                val du = (p.u - w / 2.0) / (w / 2.0); val dv = (p.v - h / 2.0) / (h / 2.0)
                radius += sqrt(du * du + dv * dv)
            }
            return DepthPoints(
                frameId, timeMs, pose.x, pose.y, pose.z, xs.toDoubleArray(), ys.toDoubleArray(), zs.toDoubleArray(), cs.toBooleanArray(), w * h,
                conf.toIntArray(), range.toDoubleArray(), radius.toDoubleArray(),
            )
        }
    }
}

/**
 * Trasformazione rigida con la sola rotazione attorno alla verticale (ARCore tiene la gravità): p' = R(yaw)·p + t. Il prototipo tiene
 * le superfici nel sistema in cui sono state confermate ("originale") e usa questa trasformazione per passare al mondo ATTUALE di
 * ARCore, che dopo una perdita di tracciamento può ruotare e spostarsi (terza prova del 2026-10-13: fino a 144° e 1,5 m).
 */
data class Rigid(val yaw: Double = 0.0, val tx: Double = 0.0, val ty: Double = 0.0, val tz: Double = 0.0) {
    fun x(x: Double, z: Double) = cos(yaw) * x - sin(yaw) * z + tx
    fun z(x: Double, z: Double) = sin(yaw) * x + cos(yaw) * z + tz
    fun y(y: Double) = y + ty
    fun inverse(): Rigid {
        val c = cos(-yaw); val s = sin(-yaw)
        return Rigid(-yaw, -(c * tx - s * tz), -ty, -(s * tx + c * tz))
    }
    /** Prima [o], poi questa. */
    fun after(o: Rigid) = Rigid(yaw + o.yaw, cos(yaw) * o.tx - sin(yaw) * o.tz + tx, o.ty + ty, sin(yaw) * o.tx + cos(yaw) * o.tz + tz)
    fun rotate(v: ArXZ) = ArXZ(cos(yaw) * v.x - sin(yaw) * v.z, sin(yaw) * v.x + cos(yaw) * v.z)
    val isIdentity: Boolean get() = yaw == 0.0 && tx == 0.0 && ty == 0.0 && tz == 0.0
}

/** Calcoli sui piani: fit robusto e autovettori 3×3. Puri. */
object HybridMath {
    /**
     * Fit robusto del piano (IRLS di Huber, k = 2, σ = MAD × 1,4826 dei residui, 10 iterazioni). [frame] per ogni punto: ogni
     * fotogramma pesa una volta.
     */
    fun fitPlane(x: DoubleArray, y: DoubleArray, z: DoubleArray, frame: IntArray): PlaneEstimate? {
        val n = x.size
        if (n < 3) return null
        val perFrame = HashMap<Int, Int>()
        for (f in frame) perFrame[f] = (perFrame[f] ?: 0) + 1
        val base = DoubleArray(n) { 1.0 / perFrame.getValue(frame[it]) }
        var w = base.copyOf()
        var out: PlaneEstimate? = null
        repeat(10) {
            var sw = 0.0; var mx = 0.0; var my = 0.0; var mz = 0.0
            for (i in 0 until n) { sw += w[i]; mx += w[i] * x[i]; my += w[i] * y[i]; mz += w[i] * z[i] }
            if (sw <= 0) return out
            mx /= sw; my /= sw; mz /= sw
            val m = Array(3) { DoubleArray(3) }
            for (i in 0 until n) {
                val dx = x[i] - mx; val dy = y[i] - my; val dz = z[i] - mz
                m[0][0] += w[i] * dx * dx; m[0][1] += w[i] * dx * dy; m[0][2] += w[i] * dx * dz
                m[1][1] += w[i] * dy * dy; m[1][2] += w[i] * dy * dz; m[2][2] += w[i] * dz * dz
            }
            m[1][0] = m[0][1]; m[2][0] = m[0][2]; m[2][1] = m[1][2]
            val e = smallestEigenvector(m)
            val res = DoubleArray(n) { (x[it] - mx) * e[0] + (y[it] - my) * e[1] + (z[it] - mz) * e[2] }
            val s = max(robustSigma(res), 1e-4)
            out = PlaneEstimate(e[0], e[1], e[2], mx, my, mz, s, n, perFrame.size)
            w = DoubleArray(n) { val r = abs(res[it]); base[it] * (if (r <= 2 * s) 1.0 else 2 * s / r) }
        }
        return out
    }

    fun median(v: DoubleArray): Double {
        if (v.isEmpty()) return Double.NaN
        val s = v.sortedArray(); return s[s.size / 2]
    }

    fun robustSigma(v: DoubleArray): Double {
        val m = median(v)
        return 1.4826 * median(DoubleArray(v.size) { abs(v[it] - m) })
    }

    /** Autovettore dell'autovalore più piccolo di una matrice simmetrica 3×3 (Jacobi). */
    fun smallestEigenvector(m0: Array<DoubleArray>): DoubleArray {
        val a = Array(3) { m0[it].copyOf() }
        val v = Array(3) { i -> DoubleArray(3) { if (it == i) 1.0 else 0.0 } }
        for (iter in 0 until 50) {
            var p = 0; var q = 1; var big = abs(a[0][1])
            if (abs(a[0][2]) > big) { p = 0; q = 2; big = abs(a[0][2]) }
            if (abs(a[1][2]) > big) { p = 1; q = 2; big = abs(a[1][2]) }
            if (big < 1e-18) break
            val th = 0.5 * atan2(2 * a[p][q], a[q][q] - a[p][p])
            val c = cos(th); val s = sin(th)
            for (k in 0 until 3) { val akp = a[k][p]; val akq = a[k][q]; a[k][p] = c * akp - s * akq; a[k][q] = s * akp + c * akq }
            for (k in 0 until 3) { val apk = a[p][k]; val aqk = a[q][k]; a[p][k] = c * apk - s * aqk; a[q][k] = s * apk + c * aqk }
            for (k in 0 until 3) { val vkp = v[k][p]; val vkq = v[k][q]; v[k][p] = c * vkp - s * vkq; v[k][q] = s * vkp + c * vkq }
        }
        val i = (0 until 3).minBy { a[it][it] }
        val e = doubleArrayOf(v[0][i], v[1][i], v[2][i])
        val l = sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2])
        return doubleArrayOf(e[0] / l, e[1] / l, e[2] / l)
    }

    fun deg(rad: Double) = rad * 180.0 / PI
    fun rad(deg: Double) = deg * PI / 180.0

    /** Angolo in (−90°, 90°]: le rette non hanno verso. */
    fun wrap90(x: Double): Double { var r = ((x % 180.0) + 180.0) % 180.0; if (r > 90.0) r -= 180.0; return r }

    fun headingDeg(d: ArXZ) = deg(atan2(d.z, d.x))
}

/**
 * Accumulo di UNA superficie attorno al suo riferimento fisso: celle di [HybridThresholds.cellM] sul piano, per ognuna punti,
 * fotogrammi, istogramma delle distanze (per la mediana) e momenti (per il fit dei frammenti e della retta). Il riferimento non si
 * sposta mai: la tolleranza non può "scivolare" verso un mobile.
 */
internal class SurfaceAccumulator(
    val kind: SurfaceKind,
    val reference: PlaneEstimate,
    private val th: HybridThresholds,
    /** Parete: lato interno (verso la camera alla conferma). */
    val inward: ArXZ?,
) {
    /** Base del piano: per le pareti u = lungo la parete (orizzontale), v = verticale; per pavimento/soffitto u = x, v = z. */
    val ux: Double; val uz: Double
    /** Distanza dal riferimento, con segno: pareti in pianta (positiva verso il muro), pavimento/soffitto in quota. */
    val gate: Double

    init {
        if (kind == SurfaceKind.WALL) {
            val i = inward!!
            ux = -i.z; uz = i.x
            gate = max(th.wallGateMinM, th.wallGateSigmas * reference.sigmaM)
        } else {
            ux = 1.0; uz = 0.0
            gate = max(th.horizontalGateMinM, th.horizontalGateSigmas * reference.sigmaM)
        }
    }

    val bins = 48
    val binWidth get() = 2 * gate / bins

    class Cell {
        var count = 0
        var weight = 0.0
        val frames = HashSet<Int>()
        lateinit var hist: DoubleArray
        // Momenti pesati in coordinate locali (a = u, b = v, d = distanza) e nel mondo (x, z) relativi al riferimento.
        var sa = 0.0; var sb = 0.0; var sd = 0.0
        var saa = 0.0; var sbb = 0.0; var sab = 0.0; var sad = 0.0; var sbd = 0.0; var sdd = 0.0
        var sx = 0.0; var sz = 0.0; var sxx = 0.0; var szz = 0.0; var sxz = 0.0
        var minA = Double.MAX_VALUE; var maxA = -Double.MAX_VALUE
    }

    val cells = HashMap<Long, Cell>()

    fun signedDistance(x: Double, y: Double, z: Double): Double = when (kind) {
        SurfaceKind.WALL -> -((x - reference.cx) * inward!!.x + (z - reference.cz) * inward.z)
        else -> y - reference.cy
    }

    private fun coords(x: Double, y: Double, z: Double): Pair<Double, Double> = when (kind) {
        SurfaceKind.WALL -> ((x - reference.cx) * ux + (z - reference.cz) * uz) to (y - reference.cy)
        else -> (x - reference.cx) to (z - reference.cz)
    }

    private fun key(i: Int, j: Int): Long = (i.toLong() shl 32) or (j.toLong() and 0xffffffffL)

    /** Aggiunge i punti già filtrati di un fotogramma; ogni fotogramma pesa una volta. */
    fun add(frameId: Int, px: DoubleArray, py: DoubleArray, pz: DoubleArray, d: DoubleArray, n: Int) {
        if (n == 0) return
        val w = 1.0 / n
        for (k in 0 until n) {
            val (a, b) = coords(px[k], py[k], pz[k])
            val i = floor(a / th.cellM).toInt(); val j = floor(b / th.cellM).toInt()
            val c = cells.getOrPut(key(i, j)) { Cell().also { it.hist = DoubleArray(bins) } }
            c.count++; c.weight += w; c.frames += frameId
            val bin = ((d[k] + gate) / binWidth).toInt().coerceIn(0, bins - 1)
            c.hist[bin] += w
            c.sa += w * a; c.sb += w * b; c.sd += w * d[k]
            c.saa += w * a * a; c.sbb += w * b * b; c.sab += w * a * b; c.sad += w * a * d[k]; c.sbd += w * b * d[k]; c.sdd += w * d[k] * d[k]
            val rx = px[k] - reference.cx; val rz = pz[k] - reference.cz
            c.sx += w * rx; c.sz += w * rz; c.sxx += w * rx * rx; c.szz += w * rz * rz; c.sxz += w * rx * rz
            if (a < c.minA) c.minA = a
            if (a > c.maxA) c.maxA = a
        }
    }

    class Fragment(val cells: List<Pair<Long, Cell>>, val info: FragmentInfo)

    /**
     * Giudizio di UNA cella (P0.18, decisione dell'utente dopo le prove del 2026-10-13 sera: la quota del pavimento vista dal
     * telefono cambia di 3–5 cm da un punto all'altro e, giudicato tutto insieme, il pavimento diventava tutto dubbio): da completare
     * con pochi punti o immagini; accettata se la sua mediana sta entro 3 cm dal riferimento; dubbia se oltre.
     */
    private fun cellStatus(c: Cell): FragmentStatus {
        if (c.count < th.cellJudgeMinPoints || c.frames.size < th.fragmentMinFrames) return FragmentStatus.INSUFFICIENT
        var acc = 0.0; var med = 0.0
        for (b in 0 until bins) { acc += c.hist[b]; if (acc >= c.weight / 2) { med = -gate + (b + 0.5) * binWidth; break } }
        return if (abs(med) <= th.fragmentMaxOffsetM) FragmentStatus.ACCEPTED else FragmentStatus.DOUBTFUL
    }

    /** Frammenti: celle collegate (8-connessione) con lo STESSO giudizio di cella. */
    fun fragments(): List<Fragment> {
        val valid = cells.filterValues { it.count >= th.cellMinPoints }
        val judged = valid.mapValues { cellStatus(it.value) }
        val seen = HashSet<Long>()
        val out = ArrayList<Fragment>()
        for (k0 in valid.keys) {
            if (k0 in seen) continue
            val cls = judged.getValue(k0)
            val comp = ArrayList<Pair<Long, Cell>>()
            val stack = ArrayDeque<Long>(); stack.addLast(k0); seen += k0
            while (stack.isNotEmpty()) {
                val k = stack.removeLast()
                comp += k to valid.getValue(k)
                val i = (k shr 32).toInt(); val j = k.toInt()
                for (di in -1..1) for (dj in -1..1) {
                    if (di == 0 && dj == 0) continue
                    val nk = key(i + di, j + dj)
                    if (nk in valid && nk !in seen && judged[nk] == cls) { seen += nk; stack.addLast(nk) }
                }
            }
            out += Fragment(comp, describe(comp, cls))
        }
        return out.sortedByDescending { it.info.points }
    }

    /**
     * Un frammento di celle accettate resta accettato se ha abbastanza punti e immagini e, se è abbastanza esteso, inclinazione
     * entro 3° sul riferimento (altrimenti da completare o dubbio); gli altri hanno il giudizio delle loro celle.
     */
    private fun describe(comp: List<Pair<Long, Cell>>, cellStatus: FragmentStatus): FragmentInfo {
        var points = 0; var sw = 0.0
        val frames = HashSet<Int>()
        val hist = DoubleArray(bins)
        var sa = 0.0; var sb = 0.0; var sd = 0.0; var saa = 0.0; var sbb = 0.0; var sab = 0.0; var sad = 0.0; var sbd = 0.0
        var minI = Int.MAX_VALUE; var maxI = Int.MIN_VALUE; var minJ = Int.MAX_VALUE; var maxJ = Int.MIN_VALUE
        for ((k, c) in comp) {
            points += c.count; sw += c.weight; frames += c.frames
            for (b in 0 until bins) hist[b] += c.hist[b]
            sa += c.sa; sb += c.sb; sd += c.sd; saa += c.saa; sbb += c.sbb; sab += c.sab; sad += c.sad; sbd += c.sbd
            val i = (k shr 32).toInt(); val j = k.toInt()
            minI = min(minI, i); maxI = max(maxI, i); minJ = min(minJ, j); maxJ = max(maxJ, j)
        }
        // Mediana pesata dall'istogramma (risoluzione: un bin).
        var acc = 0.0; var med = 0.0
        for (b in 0 until bins) { acc += hist[b]; if (acc >= sw / 2) { med = -gate + (b + 0.5) * binWidth; break } }
        val extA = (maxI - minI + 1) * th.cellM; val extB = (maxJ - minJ + 1) * th.cellM
        // Inclinazione sul riferimento: fit d = p·a + q·b + r (minimi quadrati pesati), solo su frammenti abbastanza estesi.
        val angle = if (extA >= th.fragmentAngleMinExtentM && extB >= th.fragmentAngleMinExtentM && sw > 0) {
            val ma = sa / sw; val mb = sb / sw; val md = sd / sw
            val caa = saa / sw - ma * ma; val cbb = sbb / sw - mb * mb; val cab = sab / sw - ma * mb
            val cad = sad / sw - ma * md; val cbd = sbd / sw - mb * md
            val det = caa * cbb - cab * cab
            if (abs(det) < 1e-12) null else {
                val p = (cad * cbb - cbd * cab) / det; val q = (cbd * caa - cad * cab) / det
                HybridMath.deg(atan(sqrt(p * p + q * q)))
            }
        } else null
        val status = when {
            cellStatus != FragmentStatus.ACCEPTED -> cellStatus
            points < th.fragmentMinPoints || frames.size < th.fragmentMinFrames -> FragmentStatus.INSUFFICIENT
            angle != null && angle > th.fragmentMaxAngleDeg -> FragmentStatus.DOUBTFUL
            else -> FragmentStatus.ACCEPTED
        }
        val ma = if (sw > 0) sa / sw else 0.0; val mb = if (sw > 0) sb / sw else 0.0
        val (wx, wy, wz) = when (kind) {
            SurfaceKind.WALL -> Triple(reference.cx + ux * ma - inward!!.x * med, reference.cy + mb, reference.cz + uz * ma - inward.z * med)
            else -> Triple(reference.cx + ma, reference.cy + med, reference.cz + mb)
        }
        return FragmentInfo(status, points, frames.size, med, angle, extA, extB, wx, wy, wz)
    }

    /**
     * Le celle viste (almeno [HybridThresholds.cellMinPoints] punti) con lo stato del loro frammento, unite per righe: celle
     * consecutive della stessa riga e con lo stesso stato diventano un solo quadrilatero sul piano di riferimento.
     */
    fun coverage(side: Int?, frags: List<Fragment> = fragments()): List<CoverageQuad> {
        val status = HashMap<Long, FragmentStatus>()
        for (f in frags) for ((k, _) in f.cells) status[k] = f.info.status
        val c = th.cellM
        fun pt(a: Double, b: Double) = when (kind) {
            SurfaceKind.WALL -> com.sagoma.planimetria.scan.ArPoint(reference.cx + ux * a, reference.cy + b, reference.cz + uz * a)
            else -> com.sagoma.planimetria.scan.ArPoint(reference.cx + a, reference.cy, reference.cz + b)
        }
        val out = ArrayList<CoverageQuad>()
        for ((j, row) in status.keys.groupBy { it.toInt() }) {
            val cols = row.map { (it shr 32).toInt() }.sorted()
            var start = cols[0]; var prev = cols[0]; var st = status.getValue(key(start, j))
            fun close() { out += CoverageQuad(kind, side, st, listOf(pt(start * c, j * c), pt((prev + 1) * c, j * c), pt((prev + 1) * c, (j + 1) * c), pt(start * c, (j + 1) * c))) }
            for (i in cols.drop(1)) {
                val s = status.getValue(key(i, j))
                if (i == prev + 1 && s == st) { prev = i; continue }
                close(); start = i; prev = i; st = s
            }
            close()
        }
        return out
    }
}

/**
 * Motore del prototipo: riceve profondità ([addDepth]) e gesti dell'utente (conferme, salti, "rifai"), tiene i riferimenti e gli
 * accumuli, e dà una fotografia ([snapshot]). NON thread-safe: chi lo usa lo chiama da un solo thread (o sincronizza).
 */
class HybridScan(
    val sketch: RoomSketch,
    /** Altezza dichiarata nello schizzo (metri): solo per confronto, mai imposta. */
    val sketchHeightM: Double?,
    val th: HybridThresholds = HybridThresholds(),
) {
    val n: Int get() = sketch.sideCount
    var phase: HybridPhase = HybridPhase.START_SIDE; private set
    var startSide: Int? = null; private set
    var targetSide: Int? = null; private set
    var ceilingSkipped = false; private set

    private var floorAcc: SurfaceAccumulator? = null
    private var ceilingAcc: SurfaceAccumulator? = null
    private val wallAcc = HashMap<Int, SurfaceAccumulator>()
    private val overrides = HashMap<Int, CandidateStatus>()

    /** Ultimi fotogrammi (per stimare il riferimento e per non perdere quelli guardati prima della conferma). */
    private val window = ArrayDeque<DepthPoints>()
    private var framesReceived = 0
    private var lastValidMs: Long? = null
    private var lastPoints = 0
    private var lastPixels = 0
    private var nowMs = 0L
    private var candidate: ReferenceCandidate = ReferenceCandidate(CandidateStatus.NO_DEPTH, null)

    // ---------------- Gesti ----------------

    fun chooseStartSide(side: Int) {
        require(side in 0 until n)
        startSide = side; targetSide = side
        if (phase == HybridPhase.START_SIDE) phase = HybridPhase.FLOOR
        restartView()
    }

    /**
     * Conferma il riferimento della fase in corso: solo una superficie PRONTA. Nessuna forzatura: con un avviso ("superficie
     * dietro", fuori schizzo, già acquisita...) non si conferma, si cerca un'altra superficie (prima prova del 2026-10-13: due
     * conferme forzate, la scatola non si è chiusa). Se il
     * candidato non è più pronto da un istante (è arrivata un'immagine instabile tra la vista e il tocco), vale l'ultimo candidato
     * PRONTO di al massimo [HybridThresholds.confirmGraceMs] fa, per la stessa fase e lo stesso lato (terza prova: due conferme su
     * quattro ignorate in silenzio).
     */
    fun confirmReference(): Boolean {
        if (realign != null) return false
        val recent = lastReady?.takeIf { (cand, t) -> candidate.status in TRANSIENT && nowMs - t <= th.confirmGraceMs && cand.side == candidate.side }?.first
        val c = if (candidate.status == CandidateStatus.READY) candidate else recent ?: return false
        val p = c.plane ?: return false
        if (c.status != CandidateStatus.READY) return false
        when (phase) {
            HybridPhase.FLOOR -> floorAcc = SurfaceAccumulator(SurfaceKind.FLOOR, horizontal(p), th, null).also { replayWindow(it) }
            HybridPhase.CEILING -> ceilingAcc = SurfaceAccumulator(SurfaceKind.CEILING, horizontal(p), th, null).also { replayWindow(it) }
            HybridPhase.WALLS -> {
                val side = c.side ?: return false
                val inward = inwardOf(p)
                wallAcc[side] = SurfaceAccumulator(SurfaceKind.WALL, vertical(p, inward), th, inward).also { replayWindow(it) }
                confirmOrder.remove(side); confirmOrder += side
                overrides.remove(side) // gli avvisi superati restano solo nelle registrazioni vecchie (rianalisi)
                targetSide = nextFree(side) // null: tutte acquisite; si resta nelle pareti per percorrerle, poi "Avanti: soffitto"
            }
            else -> return false
        }
        restartView()
        return true
    }

    /**
     * Rianalisi offline di una registrazione: conferma come riferimento il piano REGISTRATO (hybrid-events.jsonl), già nella forma
     * usata dal vivo (orizzontale per pavimento e soffitto; verticale con la normale verso l'interno per le pareti), senza rifare la
     * valutazione dal vivo. Per le pareti [side] è il lato confermato; [override] l'avviso superato, se c'era.
     */
    fun confirmRecordedReference(plane: PlaneEstimate, side: Int?, override: CandidateStatus?) {
        when (phase) {
            HybridPhase.FLOOR -> floorAcc = SurfaceAccumulator(SurfaceKind.FLOOR, plane, th, null).also { replayWindow(it) }
            HybridPhase.CEILING -> ceilingAcc = SurfaceAccumulator(SurfaceKind.CEILING, plane, th, null).also { replayWindow(it) }
            HybridPhase.WALLS, HybridPhase.REVIEW -> {
                val s = side ?: return
                val inward = ArXZ(plane.nx, plane.nz).normalized()
                wallAcc[s] = SurfaceAccumulator(SurfaceKind.WALL, plane, th, inward).also { replayWindow(it) }
                confirmOrder.remove(s); confirmOrder += s
                if (override != null) overrides[s] = override else overrides.remove(s)
                targetSide = nextFree(s)
                phase = HybridPhase.WALLS
            }
            else -> return
        }
        restartView()
    }

    /** Rianalisi offline: porta il motore nella fase registrata (le registrazioni vecchie hanno un altro ordine delle fasi). */
    fun replayPhase(p: HybridPhase) { if (p != phase) { phase = p; restartView() } }

    /**
     * "Avanti": pavimento → pareti → soffitto → riepilogo (decisione dell'utente, P0.19: il soffitto, inquadrato con il telefono
     * quasi verticale, faceva perdere la posizione ad ARCore il 60% del tempo e rovinava le pareti fatte dopo). Pavimento e soffitto
     * richiedono il riferimento; le pareti no (si può andare avanti anche con pareti mancanti e tornare dal riepilogo).
     */
    fun nextPhase(): Boolean {
        when (phase) {
            HybridPhase.FLOOR -> { if (floorAcc == null) return false; phase = HybridPhase.WALLS }
            HybridPhase.WALLS -> { leaveWalls(); phase = HybridPhase.CEILING }
            HybridPhase.CEILING -> { if (ceilingAcc == null) return false; phase = HybridPhase.REVIEW }
            else -> return false
        }
        restartView()
        return true
    }

    /**
     * Fuori dalla fase pareti le pareti non raccolgono più punti e non si chiede il riallineamento: le perdite di posizione durante
     * il soffitto non le toccano. Un riallineamento in sospeso si lascia: da qui le immagini si controllano come dopo un salto.
     */
    private fun leaveWalls() {
        if (realign != null) {
            realign = null
            unverified = true; lastDisplacement = null
            checkSurfaces = listOfNotNull(floorAcc, ceilingAcc) + wallAcc.values
        }
    }

    /** Soffitto saltato: l'altezza non viene stimata (mai sostituita da quella dello schizzo). */
    fun skipCeiling() {
        if (phase != HybridPhase.CEILING) return
        ceilingSkipped = true; ceilingAcc = null; phase = HybridPhase.REVIEW
        restartView()
    }

    /**
     * Rifai il riferimento di pavimento o soffitto (nella sua fase): si cancella quanto raccolto e si riconferma. Su un pavimento
     * lucido il riferimento può cadere su un riflesso (prova del 2026-10-13, 23:59: riflesso della lampada, circa 4 cm più in basso).
     */
    fun redoHorizontal() {
        val old = when (phase) {
            HybridPhase.FLOOR -> floorAcc.also { floorAcc = null }
            HybridPhase.CEILING -> ceilingAcc.also { ceilingAcc = null }
            else -> return
        } ?: return
        checkSurfaces = checkSurfaces - old
        restartView()
    }

    /** Scegli il lato da acquisire (dopo la partenza). */
    fun setTarget(side: Int) {
        require(side in 0 until n)
        if (side in wallAcc) return
        targetSide = side
        if (phase == HybridPhase.REVIEW || phase == HybridPhase.CEILING) phase = HybridPhase.WALLS
        restartView()
    }

    /** Rifai: cancella la parete del lato e la si riacquisisce. */
    fun redoWall(side: Int) {
        wallAcc.remove(side); overrides.remove(side); confirmOrder.remove(side)
        // Riallineamento in corso: la parete rifatta non serve più come riferimento.
        realign?.let { r ->
            r.done.remove(side)
            if (wallAcc.isEmpty()) {
                realign = null
                unverified = true; lastDisplacement = null
                checkSurfaces = listOfNotNull(floorAcc, ceilingAcc)
            } else if (r.target == side) r.target = lastConfirmedWall()
        }
        targetSide = side
        if (phase == HybridPhase.REVIEW || phase == HybridPhase.CEILING) phase = HybridPhase.WALLS
        restartView()
    }

    fun goToReview() { if (phase == HybridPhase.WALLS || phase == HybridPhase.CEILING) { if (phase == HybridPhase.WALLS) leaveWalls(); phase = HybridPhase.REVIEW; restartView() } }

    /** Dal riepilogo si torna alle pareti se ne mancano, altrimenti al soffitto. */
    fun backToScan() {
        if (phase != HybridPhase.REVIEW) return
        phase = if (targetSide != null) HybridPhase.WALLS else HybridPhase.CEILING
        restartView()
    }

    private fun nextFree(from: Int): Int? = (1..n).map { (from + it) % n }.firstOrNull { it !in wallAcc }

    // ---------------- Profondità ----------------

    /**
     * Superfici su cui vale "solo punti buoni" ([HybridThresholds.pointMaxRangeM] e seguenti); vale per i punti nuovi. Il
     * riferimento si stima sempre dalla zona centrale con tutti i punti; il prototipo dal vivo attiva i punti buoni su tutte e tre.
     */
    var goodPointKinds: Set<SurfaceKind> = emptySet()

    /** Le superfici viste finora, per colorarle dal vivo (sistema originale; per il disegno si applica [world]). */
    fun coverage(): List<CoverageQuad> =
        listOfNotNull(floorAcc?.coverage(null), ceilingAcc?.coverage(null)).flatten() + wallAcc.flatMap { (side, a) -> a.coverage(side) }

    // ---------------- Salti di posizione ----------------

    /**
     * Dopo un salto di posizione la raccolta NON si ferma (la pausa bloccava soffitto e pareti nella seconda prova del 2026-10-13):
     * la posizione è "da verificare" e ogni fotogramma in cui si vede una superficie confermata PRIMA del salto viene controllato.
     * Se quella superficie risulta spostata oltre 3 cm, i punti del fotogramma non si usano; se combacia, la posizione è verificata.
     * I fotogrammi senza superfici confermate in vista si usano (non verificabili: si contano).
     */
    private var unverified = false
    private var jumps = 0
    private var lastDisplacement: Double? = null
    private var discardedFrames = 0
    private var uncheckedFrames = 0
    private var checkSurfaces: List<SurfaceAccumulator> = emptyList()

    /**
     * Salto di posizione o di orientamento ([distanceM], [rotationDeg]), o ripresa dopo una pausa del tracciamento ([afterPause]).
     * Dopo una pausa o una rotazione brusca, con pareti già acquisite, il mondo di ARCore non è più affidabile: si chiede il
     * RIALLINEAMENTO (riconfermare pareti note). Altrimenti si continua controllando i fotogrammi (posizione "da verificare").
     */
    fun poseJump(afterPause: Boolean = false, rotationDeg: Double = 0.0) {
        jumps++
        if (floorAcc == null && ceilingAcc == null && wallAcc.isEmpty()) return
        if ((afterPause || rotationDeg > th.poseJumpRotationDeg) && wallAcc.isNotEmpty() && phase == HybridPhase.WALLS) {
            if (realign == null) realign = Realign(target = lastConfirmedWall())
            restartView()
            return
        }
        unverified = true; lastDisplacement = null
        checkSurfaces = listOfNotNull(floorAcc, ceilingAcc) + wallAcc.values
        restartView() // il riferimento candidato non mescola viste di prima e dopo il salto
    }

    // ---------------- Riallineamento ----------------

    /** Dal sistema delle superfici (originale) al mondo ATTUALE di ARCore. */
    var world: Rigid = Rigid(); private set
    private val confirmOrder = ArrayList<Int>()
    private fun lastConfirmedWall(): Int = confirmOrder.lastOrNull { it in wallAcc } ?: wallAcc.keys.first()

    /** Un riallineamento in corso: parete da riconfermare e riconferme già fatte (lato → piano visto ora). */
    private class Realign(var target: Int, val done: LinkedHashMap<Int, PlaneEstimate> = LinkedHashMap())

    private var realign: Realign? = null

    /** Pareti da riconfermare: 2 se tra quelle acquisite ce ne sono due non parallele (oltre 30°), altrimenti 1. */
    private fun realignNeeded(): Int {
        val ws = wallAcc.values.toList()
        for (i in ws.indices) for (j in i + 1 until ws.size) if (abs(ws[i].inward!!.cross(ws[j].inward!!)) > sin(HybridMath.rad(30.0))) return 2
        return 1
    }

    /** Sceglie la parete da riconfermare (tra quelle acquisite). */
    fun setRealignTarget(side: Int) { realign?.let { if (side in wallAcc) { it.target = side; restartView() } } }

    /** Salta il riallineamento: si assume che il mondo non sia cambiato (i fotogrammi si controllano come dopo un salto). */
    fun skipRealign() {
        if (realign == null) return
        realign = null
        unverified = true; lastDisplacement = null
        checkSurfaces = listOfNotNull(floorAcc, ceilingAcc) + wallAcc.values
        restartView()
    }

    /**
     * Riconferma della parete [Realign.target]: il piano visto ora (nel sistema creduto finora) contro il riferimento salvato. Con
     * abbastanza riconferme si calcola la correzione C (rotazione attorno alla verticale + spostamento) che porta i riferimenti salvati
     * sui piani visti ora, e il mondo diventa world ∘ C. Una sola parete fissa rotazione e distanza lungo la sua normale (lungo la
     * parete lo spostamento non cambia le rette); due pareti non parallele fissano tutto in pianta.
     */
    fun confirmRealign(): Boolean {
        val r = realign ?: return false
        realignMismatch = false
        val recent = lastReady?.takeIf { (cand, t) -> candidate.status in TRANSIENT && nowMs - t <= th.confirmGraceMs && cand.side == r.target }?.first
        val c = if (candidate.status == CandidateStatus.READY) candidate else recent ?: return false
        val p = c.plane ?: return false
        if (wallAcc[r.target] == null) return false
        if (HybridMath.deg(asin(abs(p.ny).coerceAtMost(1.0))) > th.refMaxTiltDeg) return false // serve una parete, non il pavimento
        val inward = inwardOf(p)
        r.done[r.target] = p.copy(nx = inward.x, ny = 0.0, nz = inward.z)
        if (r.done.size < realignNeeded()) {
            // Prossima: una parete acquisita non parallela a quelle già riconfermate.
            val next = wallAcc.entries.firstOrNull { (k, a) -> k !in r.done && r.done.keys.all { abs(wallAcc[it]!!.inward!!.cross(a.inward!!)) > sin(HybridMath.rad(30.0)) } }?.key
            if (next != null) { r.target = next; restartView(); return true }
        }
        // Rotazione: media circolare degli scarti di direzione delle pareti riconfermate.
        val deltas = r.done.map { (k, seen) -> val o = wallAcc[k]!!.inward!!; atan2(seen.nz, seen.nx) - atan2(o.z, o.x) }
        val delta = atan2(deltas.sumOf { sin(it) }, deltas.sumOf { cos(it) })
        val rot = Rigid(delta)
        // Spostamento in pianta: vincoli (R·c_o + t − c_visto)·n_visto = 0 per ogni parete (minimi quadrati, 2×2).
        var a11 = 0.0; var a12 = 0.0; var a22 = 0.0; var b1 = 0.0; var b2 = 0.0
        for ((k, seen) in r.done) {
            val ref = wallAcc[k]!!.reference
            val nx = seen.nx; val nz = seen.nz
            val rhs = (seen.cx - rot.x(ref.cx, ref.cz)) * nx + (seen.cz - rot.z(ref.cx, ref.cz)) * nz
            a11 += nx * nx; a12 += nx * nz; a22 += nz * nz; b1 += nx * rhs; b2 += nz * rhs
        }
        val det = a11 * a22 - a12 * a12
        val (tx, tz) = if (abs(det) > 1e-6) ((b1 * a22 - b2 * a12) / det) to ((a11 * b2 - a12 * b1) / det)
        else r.done.values.first().let { s -> val ref = wallAcc[r.done.keys.first()]!!.reference
            val d = (s.cx - rot.x(ref.cx, ref.cz)) * s.nx + (s.cz - rot.z(ref.cx, ref.cz)) * s.nz; (d * s.nx) to (d * s.nz) }
        // Coerenza (prova del 2026-10-14, 00:33: correzioni di 76° e 117°, spostamenti fino a 7,8 m in una stanza di 3,3 m): le
        // pareti riconfermate devono dare la stessa rotazione e lo spostamento deve stare nella stanza. Altrimenti una delle due
        // non era quella richiesta: si ricomincia dalla prima.
        val disagree = deltas.maxOf { a -> deltas.maxOf { b -> abs(atan2(sin(a - b), cos(a - b))) } }
        // Spostamento: ARCore ruota attorno alla camera (dentro la stanza), quindi al massimo due diagonali della stanza.
        if (HybridMath.deg(disagree) > th.realignMaxDisagreeDeg || sqrt(tx * tx + tz * tz) > 2 * sketchDiagonal()) {
            r.done.clear(); r.target = lastConfirmedWall(); realignMismatch = true
            restartView()
            return false
        }
        // C porta il sistema originale su quello creduto finora: il mondo diventa world ∘ C.
        world = world.after(Rigid(delta, tx, 0.0, tz))
        realign = null
        unverified = true; lastDisplacement = null // da qui si controllano i fotogrammi (anche la quota col pavimento)
        checkSurfaces = listOfNotNull(floorAcc, ceilingAcc) + wallAcc.values
        restartView()
        return true
    }

    /** L'ultima riconferma è stata rifiutata perché incoerente con l'altra (o con la stanza): si ricomincia dalla prima parete. */
    private var realignMismatch = false

    /** Diagonale del rettangolo che contiene lo schizzo (metri). */
    private fun sketchDiagonal(): Double {
        val pts = (0 until n).map { sketch.start(it) }
        return sqrt((pts.maxOf { it.x } - pts.minOf { it.x }).let { it * it } + (pts.maxOf { it.z } - pts.minOf { it.z }).let { it * it })
    }

    /** Riallineamento della quota con il pavimento visto ora (facoltativo, quando lo si inquadra). */
    fun confirmRealignFloor(): Boolean {
        val f = floorAcc ?: return false
        val c = if (candidate.status == CandidateStatus.READY) candidate else return false
        val p = c.plane ?: return false
        if (HybridMath.deg(acos(abs(p.ny).coerceAtMost(1.0))) > th.refMaxTiltDeg) return false
        world = world.after(Rigid(0.0, 0.0, p.cy - f.reference.cy, 0.0))
        restartView()
        return true
    }

    /** Dal mondo attuale al sistema originale: i punti nuovi si portano dove stanno le superfici confermate. */
    private fun toOriginal(d: DepthPoints): DepthPoints {
        if (world.isIdentity) return d
        val inv = world.inverse()
        val n = d.size
        val x = DoubleArray(n); val y = DoubleArray(n); val z = DoubleArray(n)
        for (i in 0 until n) { x[i] = inv.x(d.x[i], d.z[i]); y[i] = inv.y(d.y[i]); z[i] = inv.z(d.x[i], d.z[i]) }
        return DepthPoints(d.frameId, d.timeMs, inv.x(d.camX, d.camZ), inv.y(d.camY), inv.z(d.camX, d.camZ), x, y, z, d.central, d.pixels, d.confidence, d.rangeM, d.imageRadius)
    }

    private var lastReady: Pair<ReferenceCandidate, Long>? = null

    /** Stati momentanei: solo questi lasciano valere l'ultimo candidato pronto (mai un avviso vero). */
    private val TRANSIENT = setOf(CandidateStatus.UNSTABLE, CandidateStatus.NOT_PLANAR, CandidateStatus.TOO_FEW_POINTS, CandidateStatus.NO_DEPTH)

    /**
     * Scarto (m) della superficie confermata più visibile nel fotogramma: mediana delle distanze dei suoi punti entro 15 cm dal
     * riferimento (servono almeno 500 punti). null se nessuna superficie confermata prima del salto è abbastanza in vista.
     */
    private fun displacement(d: DepthPoints): Double? {
        var best: Pair<Int, Double>? = null
        val (lo, hi) = wallBand()
        for (a in checkSurfaces) {
            val v = ArrayList<Double>()
            for (i in 0 until d.size) {
                if (a.kind == SurfaceKind.WALL && (d.y[i] < lo || d.y[i] > hi)) continue
                val s = a.signedDistance(d.x[i], d.y[i], d.z[i])
                if (abs(s) <= 0.15) v += s
            }
            if (v.size >= 500 && (best == null || v.size > best.first)) best = v.size to HybridMath.median(v.toDoubleArray())
        }
        return best?.second
    }

    fun addDepth(current: DepthPoints) {
        framesReceived++
        nowMs = current.timeMs
        lastPoints = current.size; lastPixels = current.pixels
        if (current.size > 0) lastValidMs = current.timeMs
        val d = toOriginal(current)
        if (realign != null) {
            // Mondo incerto: niente raccolta finché non si riallinea; il candidato (per riconfermare una parete) sì.
            window.addLast(d)
            while (window.size > th.refWindowFrames) window.removeFirst()
            updateCandidate()
            return
        }
        if (unverified) {
            val off = displacement(d)
            when {
                off == null -> uncheckedFrames++
                abs(off) <= th.fragmentMaxOffsetM -> { unverified = false; lastDisplacement = null }
                else -> {
                    // Superficie confermata spostata: i punti di questo fotogramma non si usano; il riferimento candidato sì.
                    discardedFrames++; lastDisplacement = off
                    window.addLast(d)
                    while (window.size > th.refWindowFrames) window.removeFirst()
                    updateCandidate()
                    return
                }
            }
        }
        window.addLast(d)
        while (window.size > th.refWindowFrames) window.removeFirst()
        floorAcc?.let { addTo(it, d) }
        ceilingAcc?.let { addTo(it, d) }
        if (wallAcc.isNotEmpty() && phase == HybridPhase.WALLS) { // fuori dalla loro fase le pareti non raccolgono (P0.19)
            val (lo, hi) = wallBand()
            for (a in wallAcc.values) addTo(a, d, lo, hi)
        }
        updateCandidate()
    }

    /** Tempo che passa senza profondità nuova (per dire "nessun dato"). */
    fun tick(timeMs: Long) {
        if (timeMs > nowMs) nowMs = timeMs
        if (lastValidMs == null || nowMs - lastValidMs!! > th.noDepthTimeoutMs) updateCandidate()
    }

    private fun replayWindow(a: SurfaceAccumulator) {
        val (lo, hi) = if (a.kind == SurfaceKind.WALL) wallBand() else Double.NEGATIVE_INFINITY to Double.POSITIVE_INFINITY
        for (d in window) addTo(a, d, lo, hi)
    }

    /** Quote del pavimento e del soffitto: quelle dei riferimenti (fissi), non ricalcolate a ogni punto. */
    private fun floorY(): Double? = floorAcc?.reference?.cy
    private fun ceilingY(): Double? = ceilingAcc?.reference?.cy

    /** Fascia d'altezza dei punti di parete: pavimento + 10 cm … soffitto − 10 cm (senza soffitto: pavimento + 2,5 m). */
    private fun wallBand(): Pair<Double, Double> {
        val f = floorY(); val c = ceilingY()
        val lo = (f ?: Double.NEGATIVE_INFINITY) + th.wallBandMarginM
        val hi = c?.let { it - th.wallBandMarginM } ?: f?.let { it + th.wallTopWithoutCeilingM } ?: Double.POSITIVE_INFINITY
        return lo to hi
    }

    private fun addTo(a: SurfaceAccumulator, d: DepthPoints, lo: Double = Double.NEGATIVE_INFINITY, hi: Double = Double.POSITIVE_INFINITY) {
        val m = d.size
        val px = DoubleArray(m); val py = DoubleArray(m); val pz = DoubleArray(m); val dist = DoubleArray(m)
        var k = 0
        // "Solo punti buoni" (se attivo per questa superficie): distanza, confidenza, angolo di vista (e centro dell'immagine, se
        // limitato: di norma l'immagine intera).
        val good = a.kind in goodPointKinds
        val maxIncCos = if (good) cos(HybridMath.rad(th.pointMaxIncidenceDeg)) else null
        val maxRadius = th.pointMaxImageRadius
        val r = a.reference
        for (i in 0 until m) {
            val y = d.y[i]
            if (y < lo || y > hi) continue
            val s = a.signedDistance(d.x[i], y, d.z[i])
            if (abs(s) > a.gate) continue
            if (good && d.rangeM != null && d.rangeM[i] > th.pointMaxRangeM) continue
            if (good && d.confidence != null && d.confidence[i] < th.pointMinConfidence) continue
            if (good && maxRadius != null && d.imageRadius != null && d.imageRadius[i] > maxRadius) continue
            if (maxIncCos != null) {
                val rx = d.x[i] - d.camX; val ry = y - d.camY; val rz = d.z[i] - d.camZ
                val rl = sqrt(rx * rx + ry * ry + rz * rz)
                if (rl > 0 && abs(rx * r.nx + ry * r.ny + rz * r.nz) / rl < maxIncCos) continue
            }
            px[k] = d.x[i]; py[k] = y; pz[k] = d.z[i]; dist[k] = s; k++
        }
        a.add(d.frameId, px, py, pz, dist, k)
    }

    // ---------------- Riferimento candidato ----------------

    private fun horizontal(p: PlaneEstimate) = p.copy(nx = 0.0, ny = 1.0, nz = 0.0) // a bolla (P0.14): conta solo la quota

    private fun vertical(p: PlaneEstimate, inward: ArXZ) = p.copy(nx = inward.x, ny = 0.0, nz = inward.z) // a piombo (P0.14)

    /** Lato interno della parete: la normale orizzontale rivolta verso le camere che l'hanno vista. */
    private fun inwardOf(p: PlaneEstimate): ArXZ {
        val h = ArXZ(p.nx, p.nz).normalized()
        val cams = window.toList().takeLast(th.refWindowFrames)
        val toCam = cams.sumOf { (it.camX - p.cx) * h.x + (it.camZ - p.cz) * h.z }
        return if (toCam >= 0) h else h * -1.0
    }

    /** Cambio di fase o di parete: il riferimento candidato riparte dai fotogrammi nuovi (niente viste della fase prima). */
    /** Vista da capo: anche l'ultimo candidato pronto non vale più (dopo una conferma o un salto è di un'altra vista). */
    private fun restartView() { window.clear(); lastReady = null; updateCandidate() }

    private fun updateCandidate() {
        candidate = evaluateCandidate()
        if (candidate.status == CandidateStatus.READY) lastReady = candidate to nowMs
    }

    /** Punti della zona centrale di un fotogramma (al massimo circa 1000, passo fisso: il fit dal vivo resta leggero). */
    private class Central(val frameId: Int, val camY: Double, val x: DoubleArray, val y: DoubleArray, val z: DoubleArray, val plane: PlaneEstimate?)

    private fun centralOf(d: DepthPoints): Central {
        val count = d.central.count { it }
        val step = max(1, count / 1000)
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>(); val zs = ArrayList<Double>()
        var seen = 0
        for (i in 0 until d.size) if (d.central[i] && seen++ % step == 0) { xs += d.x[i]; ys += d.y[i]; zs += d.z[i] }
        val x = xs.toDoubleArray(); val y = ys.toDoubleArray(); val z = zs.toDoubleArray()
        val plane = if (x.size >= 30) HybridMath.fitPlane(x, y, z, IntArray(x.size) { d.frameId }) else null
        return Central(d.frameId, d.camY, x, y, z, plane)
    }

    /** Lo stesso piano (tolleranze dei frammenti: 3° e 3 cm), confrontando [b] con il piano [a]. */
    private fun samePlane(a: PlaneEstimate, b: PlaneEstimate): Boolean {
        val cosA = abs(a.nx * b.nx + a.ny * b.ny + a.nz * b.nz).coerceAtMost(1.0)
        return HybridMath.deg(acos(cosA)) <= th.fragmentMaxAngleDeg && abs(a.distance(b.cx, b.cy, b.cz)) <= th.fragmentMaxOffsetM
    }

    private fun evaluateCandidate(): ReferenceCandidate {
        if (phase == HybridPhase.START_SIDE || (phase == HybridPhase.REVIEW && realign == null)) return ReferenceCandidate(CandidateStatus.NO_DEPTH, null)
        val recent = window.filter { nowMs - it.timeMs <= th.noDepthTimeoutMs && it.size > 0 }
        if (recent.isEmpty()) return ReferenceCandidate(CandidateStatus.NO_DEPTH, null)
        // Il fotogramma più recente decide che superficie si sta guardando; gli altri entrano solo se vedono lo stesso piano.
        val frames = recent.filter { nowMs - it.timeMs <= th.refWindowMs }.map { centralOf(it) }.filter { it.x.isNotEmpty() }
        val last = frames.lastOrNull() ?: return ReferenceCandidate(CandidateStatus.TOO_FEW_POINTS, null)
        val lastPlane = last.plane ?: return ReferenceCandidate(CandidateStatus.TOO_FEW_POINTS, null)
        val maxSigma = if (phase == HybridPhase.WALLS || phase == HybridPhase.REVIEW) th.refMaxSigmaWallM else th.refMaxSigmaHorizontalM
        if (lastPlane.sigmaM > maxSigma) return judge(ReferenceCandidate(CandidateStatus.NOT_PLANAR, lastPlane), last.x, last.y, last.z, listOf(last))
        val same = ArrayList<Central>()
        for (c in frames.asReversed()) { if (c.plane == null || !samePlane(lastPlane, c.plane)) break; same += c }
        val xs = DoubleArray(same.sumOf { it.x.size }); val ys = DoubleArray(xs.size); val zs = DoubleArray(xs.size); val fs = IntArray(xs.size)
        var k = 0
        for (c in same) for (i in c.x.indices) { xs[k] = c.x[i]; ys[k] = c.y[i]; zs[k] = c.z[i]; fs[k] = c.frameId; k++ }
        if (same.size < th.refMinFrames || xs.size < th.refMinPoints) {
            // Si mostra comunque la superficie dell'ultimo fotogramma: basta tenere fermo un istante.
            return judge(ReferenceCandidate(CandidateStatus.UNSTABLE, lastPlane), last.x, last.y, last.z, listOf(last))
        }
        val p = HybridMath.fitPlane(xs, ys, zs, fs) ?: return ReferenceCandidate(CandidateStatus.TOO_FEW_POINTS, null)
        return judge(ReferenceCandidate(CandidateStatus.READY, p), xs, ys, zs, same)
    }

    /**
     * Giudica il piano per la fase: orientamento, lato, schizzo. [base] porta lo stato di partenza (READY, o UNSTABLE/NOT_PLANAR che
     * restano tali ma con il disegno e i numeri, se la superficie va considerata). Le superfici oltre 10° dallo schizzo NON si
     * considerano: nessun disegno.
     */
    private fun judge(base: ReferenceCandidate, xs: DoubleArray, ys: DoubleArray, zs: DoubleArray, frames: List<Central>): ReferenceCandidate {
        val p = base.plane!!
        val camY = frames.map { it.camY }.average()
        val vert = abs(p.ny).coerceAtMost(1.0)
        val patch = patchOf(p, xs, ys, zs)
        fun with(status: CandidateStatus) = if (base.status == CandidateStatus.READY) status else base.status
        return when (phase) {
            HybridPhase.FLOOR, HybridPhase.CEILING -> {
                val tilt = HybridMath.deg(acos(vert))
                val status = when {
                    tilt > th.refMaxTiltDeg -> CandidateStatus.WRONG_ORIENTATION
                    phase == HybridPhase.FLOOR && p.cy >= camY -> CandidateStatus.WRONG_SIDE
                    phase == HybridPhase.CEILING && p.cy <= camY -> CandidateStatus.WRONG_SIDE
                    else -> with(CandidateStatus.READY)
                }
                ReferenceCandidate(status, p, tilt, patch = patch)
            }
            HybridPhase.WALLS, HybridPhase.REVIEW -> {
                val tilt = HybridMath.deg(asin(vert))
                val r = realign
                if (r != null) {
                    // Riallineamento: basta una parete verticale, piana e ferma (niente schizzo: il mondo è incerto). Anche il
                    // pavimento è accettato, per la quota.
                    val floorTilt = HybridMath.deg(acos(vert))
                    when {
                        floorTilt <= th.refMaxTiltDeg && p.cy < camY -> ReferenceCandidate(with(CandidateStatus.READY), p, floorTilt, patch = patch)
                        tilt > th.refMaxTiltDeg -> ReferenceCandidate(CandidateStatus.WRONG_ORIENTATION, p, tilt, r.target, patch = patch)
                        else -> ReferenceCandidate(with(CandidateStatus.READY), p, tilt, r.target, patch = patch)
                    }
                } else if (phase == HybridPhase.WALLS) evaluateWall(base, p, tilt, patch)
                else ReferenceCandidate(CandidateStatus.NO_DEPTH, null)
            }
            else -> ReferenceCandidate(CandidateStatus.NO_DEPTH, null)
        }
    }

    private fun evaluateWall(base: ReferenceCandidate, p: PlaneEstimate, tilt: Double, patch: List<com.sagoma.planimetria.scan.ArPoint>?): ReferenceCandidate {
        val side = targetSide
        if (tilt > th.refMaxTiltDeg) return ReferenceCandidate(CandidateStatus.WRONG_ORIENTATION, p, tilt, side, patch = patch)
        val inward = inwardOf(p)
        // Schizzo (dalla seconda parete): direzione E verso previsti per il lato. Oltre 10°: la superficie non si considera.
        val expected = side?.let { expectedInward(it) }
        val dev = expected?.let { HybridMath.deg(acos((inward dot it).coerceIn(-1.0, 1.0))) }
        if (dev != null && dev > th.sketchDirectionDeg) return ReferenceCandidate(CandidateStatus.OFF_SKETCH, null, tilt, side, deviationDeg = dev)
        val status = if (base.status != CandidateStatus.READY) base.status else {
            // Rispetto alle pareti già acquisite: la stessa, oppure parallela ma spostata (mobile, nicchia).
            var s: CandidateStatus = CandidateStatus.READY
            var other: Int? = null; var off: Double? = null
            for ((k, a) in wallAcc) {
                if ((inward dot a.inward!!) < cos(HybridMath.rad(th.sketchDirectionDeg))) continue
                val o = a.signedDistance(p.cx, p.cy, p.cz)
                s = if (abs(o) <= a.gate) CandidateStatus.ALREADY_ACQUIRED else CandidateStatus.PARALLEL_OFFSET
                other = k; off = o; break
            }
            if (s == CandidateStatus.READY && side == null) s = CandidateStatus.ALREADY_ACQUIRED
            if (s != CandidateStatus.READY) return ReferenceCandidate(s, p, tilt, side, other, dev, off, patch = patch)
            // Dietro la candidata c'è un'altra superficie parallela? (indizio di mobile davanti al muro)
            val behind = behindDistance(p, inward, window.filter { nowMs - it.timeMs <= th.refWindowMs })
            if (behind != null) return ReferenceCandidate(CandidateStatus.SURFACE_BEHIND, p, tilt, side, deviationDeg = dev, offsetM = behind, patch = patch,
                adjacentSide = adjacent(side)?.first, adjacentAngleDeg = adjacent(side)?.let { measuredCorner(side!!, it.first, inward, it.second) }, sketchAdjacentAngleDeg = adjacent(side)?.let { sketchCorner(side!!, it.first) })
            CandidateStatus.READY
        }
        val (opp, oppSketch) = if (side != null) oppositeDistance(side, p, inward) else null to null
        val adj = adjacent(side)
        return ReferenceCandidate(
            status, p, tilt, side, deviationDeg = dev, oppositeDistanceM = opp, sketchOppositeDistanceM = oppSketch, patch = patch,
            adjacentSide = adj?.first, adjacentAngleDeg = adj?.let { measuredCorner(side!!, it.first, inward, it.second) },
            sketchAdjacentAngleDeg = adj?.let { sketchCorner(side!!, it.first) },
        )
    }

    /** I 4 vertici del tratto visto sul piano (percentili 5–95 delle proiezioni dei punti), per disegnarlo. */
    private fun patchOf(p: PlaneEstimate, xs: DoubleArray, ys: DoubleArray, zs: DoubleArray): List<com.sagoma.planimetria.scan.ArPoint>? {
        if (xs.size < 10) return null
        // Base del piano: u orizzontale (o lungo x per i piani orizzontali), v = n × u.
        var ux = -p.nz; var uy = 0.0; var uz = p.nx
        var l = sqrt(ux * ux + uz * uz)
        if (l < 0.3) { ux = 1.0; uy = 0.0; uz = 0.0; l = 1.0 } else { ux /= l; uz /= l }
        val vx = p.ny * uz - p.nz * uy; val vy = p.nz * ux - p.nx * uz; val vz = p.nx * uy - p.ny * ux
        val a = DoubleArray(xs.size) { (xs[it] - p.cx) * ux + (ys[it] - p.cy) * uy + (zs[it] - p.cz) * uz }.sortedArray()
        val b = DoubleArray(xs.size) { (xs[it] - p.cx) * vx + (ys[it] - p.cy) * vy + (zs[it] - p.cz) * vz }.sortedArray()
        fun pct(s: DoubleArray, q: Double) = s[((s.size - 1) * q).toInt()]
        val a0 = pct(a, 0.05); val a1 = pct(a, 0.95); val b0 = pct(b, 0.05); val b1 = pct(b, 0.95)
        fun pt(s: Double, t: Double) = com.sagoma.planimetria.scan.ArPoint(p.cx + ux * s + vx * t, p.cy + uy * s + vy * t, p.cz + uz * s + vz * t)
        return listOf(pt(a0, b0), pt(a1, b0), pt(a1, b1), pt(a0, b1))
    }

    /** Orientamento dello schizzo: con area positiva (x, z) l'interno è a sinistra dei lati. */
    private val sketchCcw: Boolean get() = sketch.signedArea > 0

    /** Normale interna del lato [k] dello schizzo (verso l'interno della stanza). */
    private fun sketchInward(k: Int): ArXZ { val d = sketch.sideDirection(k); return if (sketchCcw) ArXZ(-d.z, d.x) else ArXZ(d.z, -d.x) }

    /** Rotazione COMPLETA schizzo → mondo (radianti) dalle normali interne delle pareti acquisite: medoide circolare. */
    private fun fullRotationRad(): Double? {
        if (wallAcc.isEmpty()) return null
        fun wrap(a: Double) = atan2(sin(a), cos(a))
        val rots = wallAcc.map { (s, a) -> wrap(atan2(a.inward!!.z, a.inward.x) - atan2(sketchInward(s).z, sketchInward(s).x)) }
        return rots.minBy { r -> rots.sumOf { o -> abs(wrap(o - r)) } }
    }

    /** Normale interna prevista per il lato [side]: quella dello schizzo ruotata come le pareti acquisite. */
    private fun expectedInward(side: Int): ArXZ? {
        val r = fullRotationRad() ?: return null
        val s = sketchInward(side)
        return ArXZ(s.x * cos(r) - s.z * sin(r), s.x * sin(r) + s.z * cos(r))
    }

    /** Parete adiacente al lato [side] già acquisita (prima la precedente) e la sua normale interna. */
    private fun adjacent(side: Int?): Pair<Int, ArXZ>? {
        if (side == null) return null
        for (j in listOf((side + n - 1) % n, (side + 1) % n)) wallAcc[j]?.let { return j to it.inward!! }
        return null
    }

    /** Direzione del lato ricavata dalla normale interna (con il verso dello schizzo). */
    private fun dirFromInward(i: ArXZ) = if (sketchCcw) ArXZ(i.z, -i.x) else ArXZ(-i.z, i.x)

    /** Angolo interno (0–360°) all'angolo tra il lato [a] e il lato successivo, dalle direzioni dei due lati. */
    private fun interiorFromDirections(da: ArXZ, db: ArXZ): Double {
        val turn = HybridMath.deg(atan2(da.cross(db), da dot db))
        return if (sketchCcw) 180.0 - turn else 180.0 + turn
    }

    /** Angolo interno misurato tra la candidata del lato [side] e la parete [other] adiacente. */
    private fun measuredCorner(side: Int, other: Int, inward: ArXZ, otherInward: ArXZ): Double {
        val ds = dirFromInward(inward); val dOther = dirFromInward(otherInward)
        return if (other == (side + n - 1) % n) interiorFromDirections(dOther, ds) else interiorFromDirections(ds, dOther)
    }

    /** Lo stesso angolo nello schizzo. */
    private fun sketchCorner(side: Int, other: Int): Double =
        if (other == (side + n - 1) % n) interiorFromDirections(sketch.sideDirection(other), sketch.sideDirection(side))
        else interiorFromDirections(sketch.sideDirection(side), sketch.sideDirection(other))

    /** Distanza dalla parete opposta già acquisita (se c'è e se lo schizzo ne ha una parallela) e la stessa nello schizzo. */
    private fun oppositeDistance(side: Int, p: PlaneEstimate, inward: ArXZ): Pair<Double?, Double?> {
        val sd = sketch.sideDirection(side)
        for ((s, a) in wallAcc) {
            val od = sketch.sideDirection(s)
            if (abs(sd.cross(od)) > sin(HybridMath.rad(th.sketchDirectionDeg))) continue
            if ((inward dot a.inward!!) > 0) continue
            val dist = abs(a.signedDistance(p.cx, p.cy, p.cz))
            val ns = ArXZ(-sd.z, sd.x)
            val sketchDist = abs((sketch.start(s) - sketch.start(side)) dot ns)
            return dist to sketchDist
        }
        return null to null
    }

    /** Se dietro il piano (oltre 10 cm, entro 1 m) c'è una superficie parallela con abbastanza punti: la sua distanza. */
    private fun behindDistance(p: PlaneEstimate, inward: ArXZ, recent: List<DepthPoints>): Double? {
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>(); val zs = ArrayList<Double>(); val fs = ArrayList<Int>()
        for (d in recent) for (i in 0 until d.size step 3) {
            val behind = -((d.x[i] - p.cx) * inward.x + (d.z[i] - p.cz) * inward.z)
            if (behind >= th.behindMinM && behind <= th.behindMaxM) { xs += d.x[i]; ys += d.y[i]; zs += d.z[i]; fs += d.frameId }
        }
        if (xs.size < th.behindMinPoints) return null
        val q = HybridMath.fitPlane(xs.toDoubleArray(), ys.toDoubleArray(), zs.toDoubleArray(), fs.toIntArray()) ?: return null
        if (q.sigmaM > th.refMaxSigmaWallM) return null
        val qh = ArXZ(q.nx, q.nz)
        if (qh.length < cos(HybridMath.rad(90.0 - th.refMaxTiltDeg))) return null // non verticale
        val ang = HybridMath.deg(acos(abs(qh.normalized() dot inward).coerceAtMost(1.0)))
        if (ang > th.sketchDirectionDeg) return null
        return -((q.cx - p.cx) * inward.x + (q.cz - p.cz) * inward.z)
    }

    /** Rotazione schizzo → mondo dalle pareti acquisite: medoide circolare (robusto a una parete sbagliata). */
    fun sketchRotationDeg(): Double? {
        if (wallAcc.isEmpty()) return null
        val rots = wallAcc.map { (s, a) -> HybridMath.wrap90(HybridMath.headingDeg(ArXZ(-a.inward!!.z, a.inward.x)) - HybridMath.headingDeg(sketch.sideDirection(s))) }
        return rots.minBy { r -> rots.sumOf { o -> abs(HybridMath.wrap90(o - r)) } }
    }

    // ---------------- Fotografia ----------------

    private fun measuredHeight(a: SurfaceAccumulator): Double? {
        val acc = a.fragments().filter { it.info.status == FragmentStatus.ACCEPTED }
        if (acc.isEmpty()) return null
        // Mediana pesata sui frammenti accettati (peso = punti).
        val sorted = acc.sortedBy { it.info.medianOffsetM }
        val total = sorted.sumOf { it.info.points }
        var c = 0
        for (f in sorted) { c += f.info.points; if (c * 2 >= total) return a.reference.cy + f.info.medianOffsetM }
        return null
    }

    private fun horizontalSnapshot(kind: SurfaceKind, a: SurfaceAccumulator?): HorizontalSnapshot {
        if (a == null) return HorizontalSnapshot(kind, null, null, 0.0, fragments = emptyList(), cells = emptyList())
        val frags = a.fragments()
        val accepted = frags.filter { it.info.status == FragmentStatus.ACCEPTED }
        val cells = accepted.flatMap { f -> f.cells.map { (k, _) -> ArXZ(a.reference.cx + ((k shr 32).toInt() + 0.5) * th.cellM, a.reference.cz + (k.toInt() + 0.5) * th.cellM) } }
        val seen = a.cells.values.count { it.count >= th.cellMinPoints } * th.cellM * th.cellM
        return HorizontalSnapshot(kind, a.reference, measuredHeight(a), cells.size * th.cellM * th.cellM, seenAreaM2 = seen, fragments = frags.map { it.info }, cells = cells, points = a.cells.values.sumOf { it.count })
    }

    private fun wallSnapshot(side: Int, rot: Double?): WallSnapshot =
        wallSnapshotCore(side, rot).let { w -> wallAcc[side]?.let { a -> w.copy(points = a.cells.values.sumOf { it.count }) } ?: w }

    private fun wallSnapshotCore(side: Int, rot: Double?): WallSnapshot {
        val a = wallAcc[side] ?: return WallSnapshot(side, WallState.MISSING, null, null, emptyList(), null, null, null, null, null, null, null)
        val frags = a.fragments()
        val accepted = frags.filter { it.info.status == FragmentStatus.ACCEPTED }
        if (accepted.isEmpty()) return WallSnapshot(side, WallState.COLLECTING, a.reference, overrides[side], frags.map { it.info }, null, null, null, null, a.inward, null, null)
        // Retta in pianta: minimi quadrati totali sui momenti dei punti accettati (nel mondo, relativi al riferimento).
        var sw = 0.0; var sx = 0.0; var sz = 0.0; var sxx = 0.0; var szz = 0.0; var sxz = 0.0
        var minA = Double.MAX_VALUE; var maxA = -Double.MAX_VALUE
        for (f in accepted) for ((_, c) in f.cells) {
            sw += c.weight; sx += c.sx; sz += c.sz; sxx += c.sxx; szz += c.szz; sxz += c.sxz
            minA = min(minA, c.minA); maxA = max(maxA, c.maxA)
        }
        val mx = sx / sw; val mz = sz / sw
        val cxx = sxx / sw - mx * mx; val czz = szz / sw - mz * mz; val cxz = sxz / sw - mx * mz
        val ang = 0.5 * atan2(2 * cxz, cxx - czz)
        var dir = ArXZ(cos(ang), sin(ang))
        val ref = ArXZ(-a.inward!!.z, a.inward.x)
        if ((dir dot ref) < 0) dir = dir * -1.0
        val minorVar = max(0.0, (cxx + czz) / 2 - sqrt(((cxx - czz) / 2) * ((cxx - czz) / 2) + cxz * cxz))
        val point = ArXZ(a.reference.cx + mx, a.reference.cz + mz)
        // Estremi osservati lungo la retta (dalle coordinate lungo il riferimento: le direzioni coincidono entro 3°).
        val refU0 = (ArXZ(a.reference.cx, a.reference.cz) - point) dot dir
        val inward = ArXZ(-dir.z, dir.x).let { if ((it dot a.inward) >= 0) it else it * -1.0 }
        val dev = rot?.let { abs(HybridMath.wrap90(HybridMath.headingDeg(dir) - (HybridMath.headingDeg(sketch.sideDirection(side)) + it))) }
        return WallSnapshot(
            side, WallState.MEASURED, a.reference, overrides[side], frags.map { it.info }, point, dir,
            refU0 + minA, refU0 + maxA, inward, sqrt(minorVar), dev,
        )
    }

    private fun intersect(p1: ArXZ, d1: ArXZ, p2: ArXZ, d2: ArXZ): ArXZ? {
        val den = d1.cross(d2)
        if (abs(den) < sin(HybridMath.rad(th.minCornerAngleDeg))) return null
        val t = (p2 - p1).cross(d2) / den
        return p1 + d1 * t
    }

    fun snapshot(): HybridSnapshot {
        val rot = sketchRotationDeg()
        val walls = (0 until n).map { wallSnapshot(it, rot) }
        var undefined = false
        val corners = (0 until n).map { k ->
            val a = walls[(k + n - 1) % n]; val b = walls[k]
            if (a.point == null || b.point == null) null
            else intersect(a.point, a.direction!!, b.point, b.direction!!).also { if (it == null) undefined = true }
        }
        val allMeasured = walls.all { it.state == WallState.MEASURED }
        val box = when {
            !allMeasured -> BoxState.INCOMPLETE
            undefined -> BoxState.UNDEFINED_CORNER
            walls.any { it.overriddenWarning != null } -> BoxState.UNCERTAIN
            else -> BoxState.COMPLETE
        }
        val disc = ArrayList<Discrepancy>()
        for (w in walls) {
            if (w.state == WallState.MISSING) disc += Discrepancy(DiscrepancyKind.MISSING_WALL, listOf(w.side), null, null)
            if (w.overriddenWarning != null) disc += Discrepancy(DiscrepancyKind.UNCERTAIN_ASSOCIATION, listOf(w.side), null, null)
            if (w.sketchDeviationDeg != null && w.sketchDeviationDeg > th.sketchDirectionDeg) disc += Discrepancy(DiscrepancyKind.DIRECTION, listOf(w.side), w.sketchDeviationDeg, 0.0)
        }
        for (k in 0 until n) {
            val c0 = corners[k]; val c1 = corners[(k + 1) % n]
            if (c0 != null && c1 != null) {
                val len = c0.distanceTo(c1); val exp = (sketch.end(k) - sketch.start(k)).length
                if (abs(len - exp) > th.sketchLengthRel * exp) disc += Discrepancy(DiscrepancyKind.LENGTH, listOf(k), len, exp)
            }
            val cPrev = corners[(k + n - 1) % n]; val cNext = corners[(k + 1) % n]
            if (c0 != null && cPrev != null && cNext != null) {
                val measured = interiorAngle(cPrev, c0, cNext)
                val expected = interiorAngle(sketch.start((k + n - 1) % n), sketch.start(k), sketch.end(k))
                if (abs(measured - expected) > th.sketchAngleDeg) disc += Discrepancy(DiscrepancyKind.ANGLE, listOf((k + n - 1) % n, k), measured, expected)
            }
        }
        val floor = horizontalSnapshot(SurfaceKind.FLOOR, floorAcc)
        val ceiling = horizontalSnapshot(SurfaceKind.CEILING, ceilingAcc)
        val height = if (floor.heightY != null && ceiling.heightY != null) ceiling.heightY - floor.heightY else null
        // Altezza: nessuna soglia (non ce n'è una motivata); il confronto si mostra sempre, con i due valori.
        if (height != null && sketchHeightM != null) disc += Discrepancy(DiscrepancyKind.HEIGHT, emptyList(), height, sketchHeightM)
        return HybridSnapshot(
            phase, n, startSide, targetSide, candidate, floor, ceiling, ceilingSkipped, walls, corners, box, disc, height, sketchHeightM,
            DepthHealth(framesReceived, lastPoints, lastPixels, lastValidMs?.let { nowMs - it }), rot, unverified, jumps, lastDisplacement, discardedFrames, uncheckedFrames,
            realign?.target, realign?.done?.keys?.toList() ?: emptyList(), if (realign != null) realignNeeded() else 0, world, realignMismatch,
        )
    }

    /** Angolo interno in b (gradi, 0–180) tra i lati verso a e verso c. */
    private fun interiorAngle(a: ArXZ, b: ArXZ, c: ArXZ): Double {
        val u = a - b; val v = c - b
        return HybridMath.deg(acos(((u dot v) / (u.length * v.length)).coerceIn(-1.0, 1.0)))
    }
}
