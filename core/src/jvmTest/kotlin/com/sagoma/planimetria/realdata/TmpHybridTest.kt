package com.sagoma.planimetria.realdata

import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.StreamStatus
import com.sagoma.planimetria.scan.recording.transformPoint
import com.sagoma.planimetria.scan.reconstruction.DepthFrame
import com.sagoma.planimetria.scan.reconstruction.DepthToWorld
import com.sagoma.planimetria.scan.reconstruction.DepthUse
import com.sagoma.planimetria.scan.reconstruction.ReconParams
import com.sagoma.planimetria.scan.sketch.RoomSketch
import com.sagoma.planimetria.scan.sketch.SessionLogEntry
import com.sagoma.planimetria.scan.sketch.SketchSessionLog
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * TEMPORANEO (non va nel repository): prova offline P0 del metodo ibrido (docs/protocollo-verifica-pareti.md, sezione P0).
 * Regole fissate nel protocollo PRIMA di aprire le registrazioni; qui solo la loro esecuzione.
 */
class TmpHybridTest {
    private fun f(v: Double?, d: Int = 1) = v?.let { if (it.isNaN()) "-" else "%.${d}f".format(it) } ?: "-"
    private fun median(v: List<Double>) = if (v.isEmpty()) Double.NaN else v.sorted()[v.size / 2]
    private fun mad(v: List<Double>): Double { val m = median(v); return 1.4826 * median(v.map { abs(it - m) }) }

    // ---------------- Dati ----------------

    class Rec(val name: String, val files: CaptureDatasetFiles) {
        val rec = files.recording
        val log: Pair<com.sagoma.planimetria.scan.sketch.SessionLogHeader?, List<SessionLogEntry>>? =
            files.read(SketchSessionLog.FILE_NAME)?.let { SketchSessionLog.parse(it.decodeToString()) }
        val sketch: RoomSketch? = log?.first?.sketch?.takeIf { it.size >= 6 }?.chunked(2)?.map { ArXZ(it[0], it[1]) }?.let { RoomSketch(it) }
        val events: List<SessionLogEntry> = log?.second.orEmpty()
    }

    /** Asse ottico nel mondo (−Z della camera) e sua inclinazione sull'orizzontale (gradi). */
    private fun forward(p: RecPose): DoubleArray {
        val o = p.transformPoint(0.0, 0.0, 0.0); val t = p.transformPoint(0.0, 0.0, -1.0)
        return doubleArrayOf(t.x - o.x, t.y - o.y, t.z - o.z)
    }
    private fun pitchDeg(p: RecPose) = Math.toDegrees(asin(forward(p)[1].coerceIn(-1.0, 1.0)))
    private fun rotAngleDeg(a: RecPose, b: RecPose): Double {
        val d = abs(a.qx * b.qx + a.qy * b.qy + a.qz * b.qz + a.qw * b.qw).coerceAtMost(1.0)
        return Math.toDegrees(2 * acos(d))
    }

    /** Periodo fermo (P0.4): inizio, fine (ns) e inclinazione mediana. */
    data class Still(val t0: Long, val t1: Long, val pitch: Double) { val durS get() = (t1 - t0) / 1e9 }

    private fun stills(r: Rec): List<Still> {
        val ps = r.rec.poses.filter { it.camera != null && it.tracking == "TRACKING" }.sortedBy { it.timestampNs }
        val n = ps.size
        val still = BooleanArray(n)
        var j = 0
        for (i in 0 until n) {
            if (j < i) j = i
            while (j < n - 1 && ps[j].timestampNs < ps[i].timestampNs + 500_000_000L) j++
            val dt = (ps[j].timestampNs - ps[i].timestampNs) / 1e9
            if (dt < 0.4) continue
            val a = ps[i].camera!!; val b = ps[j].camera!!
            val rot = rotAngleDeg(a, b) / dt
            val lin = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)) / dt
            still[i] = rot < 10.0 && lin < 0.10
        }
        val out = ArrayList<Still>()
        var i = 0
        while (i < n) {
            if (!still[i]) { i++; continue }
            var k = i
            while (k + 1 < n && still[k + 1]) k++
            val t0 = ps[i].timestampNs; val t1 = ps[k].timestampNs + 500_000_000L
            if ((t1 - t0) / 1e9 >= 2.5) out += Still(t0, t1, median((i..k).map { pitchDeg(ps[it].camera!!) }))
            i = k + 1
        }
        return out
    }

    // ---------------- Piani ----------------

    class Pt(val x: Double, val y: Double, val z: Double, val frame: Int)

    /** Piano: normale unitaria n e punto c. */
    data class Plane(val n: DoubleArray, val c: DoubleArray, val sigma: Double, val count: Int, val frames: Int) {
        fun dist(x: Double, y: Double, z: Double) = (x - c[0]) * n[0] + (y - c[1]) * n[1] + (z - c[2]) * n[2]
    }

    /** Autovettore del più piccolo autovalore di una matrice 3×3 simmetrica (Jacobi). */
    private fun smallestEigvec(m0: Array<DoubleArray>): DoubleArray {
        val a = Array(3) { m0[it].clone() }
        val v = Array(3) { i -> DoubleArray(3) { if (it == i) 1.0 else 0.0 } }
        repeat(50) {
            var p = 0; var q = 1; var big = abs(a[0][1])
            if (abs(a[0][2]) > big) { p = 0; q = 2; big = abs(a[0][2]) }
            if (abs(a[1][2]) > big) { p = 1; q = 2; big = abs(a[1][2]) }
            if (big < 1e-15) return@repeat
            val th = 0.5 * kotlin.math.atan2(2 * a[p][q], a[q][q] - a[p][p])
            val c = kotlin.math.cos(th); val s = kotlin.math.sin(th)
            for (k in 0 until 3) { val akp = a[k][p]; val akq = a[k][q]; a[k][p] = c * akp - s * akq; a[k][q] = s * akp + c * akq }
            for (k in 0 until 3) { val apk = a[p][k]; val aqk = a[q][k]; a[p][k] = c * apk - s * aqk; a[q][k] = s * apk + c * aqk }
            for (k in 0 until 3) { val vkp = v[k][p]; val vkq = v[k][q]; v[k][p] = c * vkp - s * vkq; v[k][q] = s * vkp + c * vkq }
        }
        val i = (0 until 3).minBy { a[it][it] }
        val e = doubleArrayOf(v[0][i], v[1][i], v[2][i])
        val l = sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2])
        return doubleArrayOf(e[0] / l, e[1] / l, e[2] / l)
    }

    /** Fit robusto del piano: IRLS di Huber, k = 2, σ = MAD × 1,4826 dei residui; ogni fotogramma pesa una volta. */
    private fun fitPlane(pts: List<Pt>): Plane? {
        if (pts.size < 3) return null
        val perFrame = pts.groupingBy { it.frame }.eachCount()
        val base = DoubleArray(pts.size) { 1.0 / perFrame.getValue(pts[it].frame) }
        var w = base.clone()
        var plane: Plane? = null
        repeat(10) {
            var sw = 0.0; val c = DoubleArray(3)
            for (i in pts.indices) { sw += w[i]; c[0] += w[i] * pts[i].x; c[1] += w[i] * pts[i].y; c[2] += w[i] * pts[i].z }
            for (k in 0 until 3) c[k] /= sw
            val m = Array(3) { DoubleArray(3) }
            for (i in pts.indices) {
                val d = doubleArrayOf(pts[i].x - c[0], pts[i].y - c[1], pts[i].z - c[2])
                for (a in 0 until 3) for (b in 0 until 3) m[a][b] += w[i] * d[a] * d[b]
            }
            val n = smallestEigvec(m)
            val res = pts.map { (it.x - c[0]) * n[0] + (it.y - c[1]) * n[1] + (it.z - c[2]) * n[2] }
            val s = mad(res).coerceAtLeast(1e-4)
            plane = Plane(n, c, s, pts.size, perFrame.size)
            w = DoubleArray(pts.size) { val r = abs(res[it]); base[it] * (if (r <= 2 * s) 1.0 else 2 * s / r) }
        }
        return plane
    }

    // ---------------- Profondità ----------------

    private val params = ReconParams()

    /** Tutti i punti di una profondità grezza fresca (P0.3); [central] = solo la zona centrale (metà × metà). */
    private fun depthPoints(r: Rec, d: com.sagoma.planimetria.scan.recording.DepthKeyframe, current: Boolean, central: Boolean, sink: (DepthToWorld.Point) -> Unit): Boolean {
        val rawPath = d.rawPath ?: return false
        if (d.rawStatus != StreamStatus.SAVED) return false
        val raw = r.files.read(rawPath) ?: return false
        val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
        if (raw.size != w * h * 2) return false
        val k0 = d.intrinsics ?: return false
        val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
        val pose: RecPose = (if (current) d.camera else d.rawCamera) ?: return false
        val conf = d.confidencePath?.let { r.files.read(it) }?.takeIf { it.size == w * h }
        DepthToWorld.convert(DepthFrame(d.seq, DepthUse.RAW, d.timestampNs, pose, k, DepthRaw.decode(raw, w, h), conf, d.poseFrameSeq, null), params) { p ->
            if (!central || (p.u >= w / 4 && p.u < w - w / 4 && p.v >= h / 4 && p.v < h - h / 4)) sink(p)
        }
        return true
    }

    // ---------------- Riferimenti (P0.4) ----------------

    enum class Kind { FLOOR, CEILING, WALL }
    data class Ref(val kind: Kind, val side: Int?, val still: Still?, val plane: Plane?, val camY: Double?, val why: String?)

    private fun refPlane(r: Rec, kind: Kind, side: Int?, s: Still?, current: Boolean): Ref {
        if (s == null) return Ref(kind, side, null, null, null, "nessun periodo fermo")
        val pts = ArrayList<Pt>()
        var frames = 0; var camY = 0.0
        for (d in r.rec.depth) {
            val ts = d.frameTimestampNs
            if (ts < s.t0 || ts > s.t1) continue
            val before = pts.size
            if (depthPoints(r, d, current, central = true) { p -> pts += Pt(p.x, p.y, p.z, d.seq) }) {
                if (pts.size > before) { frames++; camY += (if (current) d.camera else d.rawCamera)!!.y }
            }
        }
        if (frames < 3) return Ref(kind, side, s, null, null, "solo $frames fotogrammi di profondità")
        camY /= frames
        val pl = fitPlane(pts) ?: return Ref(kind, side, s, null, camY, "fit fallito")
        val vert = abs(pl.n[1])
        val why = when {
            pl.count < 300 -> "solo ${pl.count} punti"
            kind == Kind.WALL && pl.sigma > 0.04 -> "dispersione ${f(pl.sigma * 100)} cm > 4"
            kind != Kind.WALL && pl.sigma > 0.06 -> "dispersione ${f(pl.sigma * 100)} cm > 6"
            kind == Kind.WALL && Math.toDegrees(asin(vert.coerceAtMost(1.0))) > 10.0 -> "normale a ${f(Math.toDegrees(asin(vert.coerceAtMost(1.0))))}° dall'orizzontale"
            kind != Kind.WALL && Math.toDegrees(acos(vert.coerceAtMost(1.0))) > 10.0 -> "normale a ${f(Math.toDegrees(acos(vert.coerceAtMost(1.0))))}° dalla verticale"
            kind == Kind.FLOOR && pl.c[1] >= camY -> "piano sopra la camera"
            kind == Kind.CEILING && pl.c[1] <= camY -> "piano sotto la camera"
            else -> null
        }
        return Ref(kind, side, s, pl, camY, why)
    }

    /** Lati finali dal registro: lato → istante (ns) del WALL_ASSIGNED che lo occupa alla fine. */
    private fun finalAssignments(r: Rec): Map<Int, Long> {
        val m = LinkedHashMap<Int, Long>()
        for (e in r.events) when (e.kind) {
            "WALL_ASSIGNED" -> m[e.side!!] = e.arTimestampNs!!
            "WALL_REMOVED" -> m.remove(e.side!!)
        }
        return m
    }

    fun references(r: Rec, current: Boolean): List<Ref> {
        val st = stills(r)
        val floorS = st.firstOrNull { it.pitch <= -40.0 }
        val floor = refPlane(r, Kind.FLOOR, null, floorS, current)
        val ceilS = floorS?.let { fs -> st.firstOrNull { it.t0 >= fs.t1 && it.pitch >= 40.0 } }
        val ceil = refPlane(r, Kind.CEILING, null, ceilS, current)
        val n = r.sketch?.sideCount ?: 4
        val assigned = finalAssignments(r)
        val wallEvents = r.events.filter { it.kind == "WALL_ASSIGNED" || it.kind == "WALL_REMOVED" }.map { it.arTimestampNs!! }.sorted()
        val start = r.events.firstOrNull { it.kind == "START_SIDE" }?.side ?: 0
        val afterCeil = (ceilS ?: floorS)?.t1 ?: Long.MIN_VALUE
        val walls = ArrayList<Ref>()
        val used = HashSet<Still>()
        // Ordine della guida: dal lato di partenza, k+1.
        var lastEnd = afterCeil
        for (step in 0 until n) {
            val side = (start + step) % n
            val tAssign = assigned[side]
            val s = if (tAssign != null) {
                val prevEvent = wallEvents.lastOrNull { it < tAssign } ?: afterCeil
                val from = maxOf(prevEvent, afterCeil)
                st.firstOrNull { it.t0 >= from && it.t1 <= tAssign && abs(it.pitch) <= 30.0 && it !in used }
            } else st.firstOrNull { it.t0 >= lastEnd && abs(it.pitch) <= 30.0 && it !in used }
            s?.let { used += it; lastEnd = it.t1 }
            walls += refPlane(r, Kind.WALL, side, s, current)
        }
        return listOf(floor, ceil) + walls
    }

    // ---------------- Fase 1: inventario e riferimenti ----------------

    private fun zips() = System.getenv("SAGOMA_ZIPS").split(";").filter { it.isNotBlank() }

    @Test
    fun stage1() {
        if (System.getenv("SAGOMA_STAGE") != "1") return
        for (path in zips()) {
            val r = Rec(File(path).name, CaptureDatasetFiles.open(File(path)))
            val ps = r.rec.poses
            val dur = if (ps.size > 1) (ps.last().timestampNs - ps.first().timestampNs) / 1e9 else 0.0
            val fresh = r.rec.depth.count { it.rawStatus == StreamStatus.SAVED }
            println("=== ${r.name}")
            println("durata ${f(dur, 0)} s · pose ${ps.size} · frame ${r.rec.frames.size} · profondità ${r.rec.depth.size} (grezze fresche $fresh) · foto ${r.rec.rgb.size}")
            val t0 = ps.firstOrNull()?.timestampNs ?: 0L
            fun ts(t: Long) = f((t - t0) / 1e9, 1) + " s"
            println("schizzo (interno, m): " + (r.sketch?.let { s -> (0 until s.sideCount).joinToString(" / ") { f((s.end(it) - s.start(it)).length * 100, 1) } } ?: "ASSENTE") +
                " · aperture ${r.log?.first?.openings}")
            println("eventi: " + r.events.joinToString(" | ") { e -> "${e.kind}${e.side?.let { " lato $it" } ?: ""}${e.answer?.let { " $it" } ?: ""} @${e.arTimestampNs?.let { ts(it) }}" })
            val st = stills(r)
            println("periodi fermi (${st.size}): " + st.joinToString(" | ") { "${ts(it.t0)}–${ts(it.t1)} incl ${f(it.pitch, 0)}°" })
            for (current in listOf(true, false)) {
                println("-- riferimenti, posa ${if (current) "frame corrente (principale)" else "timestamp della grezza (sensibilità)"}")
                for (ref in references(r, current)) {
                    val p = ref.plane
                    println("  ${ref.kind}${ref.side?.let { " lato $it" } ?: ""}: " +
                        (ref.still?.let { "fermo ${ts(it.t0)}–${ts(it.t1)} (incl ${f(it.pitch, 0)}°)" } ?: "—") + " · " +
                        (p?.let { "punti ${it.count} da ${it.frames} fotogrammi, dispersione ${f(it.sigma * 100)} cm, n=(${f(it.n[0], 2)}, ${f(it.n[1], 2)}, ${f(it.n[2], 2)}), quota centro ${f(it.c[1], 2)} m" } ?: "-") +
                        " → " + (ref.why?.let { "NON VALIDO: $it" } ?: "valido"))
                }
            }
        }
    }

    // ---------------- Fase 2 (descrittiva): R3/R4 e scansione guidata con e senza correzione ----------------

    /** Metro, lati nell'ordine dello schizzo (lato 0 = porta) e diagonale misurata tra gli angoli 1 e 3 dello schizzo. */
    private fun tapeOf(name: String): Pair<List<Double>, Double>? = when {
        name.contains("letto") -> listOf(5.19, 4.28, 5.18, 4.27) to 6.74
        name.contains("stanzetta") || name.contains("sranzetta") -> listOf(3.295, 3.115, 3.205, 3.09) to 4.475
        else -> null
    }

    private fun polyReport(label: String, c: List<ArXZ>, tape: Pair<List<Double>, Double>) {
        val sides = (0 until 4).map { (c[(it + 1) % 4].distanceTo(c[it]) - tape.first[it]) * 100 }
        val diag = (c[1].distanceTo(c[3]) - tape.second) * 100
        val worst = (sides + diag).maxOf { abs(it) }
        fun ang(k: Int): Double {
            val a = c[(k + 3) % 4] - c[k]; val b = c[(k + 1) % 4] - c[k]
            return Math.toDegrees(acos(((a dot b) / (a.length * b.length)).coerceIn(-1.0, 1.0)))
        }
        println("   $label: lati ${sides.joinToString(" / ") { f(it) }} · diagonale misurata ${f(diag)} cm · angoli " +
            "${(0 until 4).joinToString(" / ") { f(ang(it)) }} · peggiore ${f(worst)} cm ${if (worst <= 5.0) "(entro 5)" else "(OLTRE 5)"}")
    }

    private fun guided(r: Rec): Map<Int, com.sagoma.planimetria.scan.sketch.ConfirmedLine> {
        val m = LinkedHashMap<Int, com.sagoma.planimetria.scan.sketch.ConfirmedLine>()
        for (e in r.events) when (e.kind) {
            "WALL_ASSIGNED" -> m[e.side!!] = SketchSessionLog.confirmedLine(e)!!
            "WALL_REMOVED" -> m.remove(e.side!!)
        }
        return m
    }

    @Test
    fun stage2() {
        if (System.getenv("SAGOMA_STAGE") != "2") return
        for (path in zips()) {
            val r = Rec(File(path).name, CaptureDatasetFiles.open(File(path)))
            val tape = tapeOf(r.name)
            println("=== ${r.name}")
            // ---- R1–R4 così come sono ----
            val blobs = com.sagoma.planimetria.scan.reconstruction.DatasetBlobs { r.files.read(it) }
            val res = com.sagoma.planimetria.scan.reconstruction.RoomReconstruction.run(r.rec, blobs, stabilityRuns = false)
            println("pavimento (controllo R1/R2): ${res.floorCheck}")
            val walls = res.walls?.walls.orEmpty()
            println("R3: superfici verticali ${res.walls?.inputVertical}, strutturali ${res.walls?.inputStructural}, pareti ${walls.size}")
            for (w in walls.sortedByDescending { it.geometry.lengthM }) {
                val g = w.geometry; val e = w.evidence
                println("  R3 parete ${w.id}: lunga ${f(g.lengthM * 100, 0)} cm (osservata ${f(g.observedLengthM * 100, 0)}), direzione ${f(g.headingDeg)}°, " +
                    "quote ${f(g.bottomAboveFloor?.let { it * 100 }, 0)}–${f(g.topAboveFloor?.let { it * 100 }, 0)} cm, punti ${e.pointCount}, " +
                    "fotogrammi ${e.rawFrames}, rms ${f(e.fitRmsM * 100)} cm, dispersione viste ${f(e.dispersionM * 100)} cm, qualità ${e.quality}, " +
                    "σ posizione ${f(w.uncertainty.positionSigmaM * 100)} cm, σ direzione ${f(w.uncertainty.headingSigmaDeg)}°")
            }
            val per = res.perimeter
            val main = per?.main
            println("R4: stato ${per?.state}; perimetro principale: ${main?.let { "stato ${it.state}, pareti ${it.wallIds}, angoli ${it.corners.size}" } ?: "nessuno"}")
            main?.reasons?.take(4)?.forEach { println("   motivo: $it") }
            val gl = guided(r)
            if (tape != null && main != null && main.corners.size == 4 && main.wallIds.size == 4) {
                // Aggancio DESCRITTIVO (non previsto da P0): lato 0 = parete R3 più vicina, entro 10°, alla parete della porta confermata.
                val idx = gl[0]?.let { dl ->
                    val du = (dl.b - dl.a).normalized(); val mid = (dl.a + dl.b) * 0.5
                    main.wallIds.indices.filter { i ->
                        val g = walls.first { it.id == main.wallIds[i] }.geometry
                        Math.toDegrees(asin(abs(du.cross(ArXZ(g.ux, g.uz))).coerceAtMost(1.0))) <= 10.0
                    }.minByOrNull { i -> val g = walls.first { it.id == main.wallIds[i] }.geometry; abs((mid - ArXZ(g.cx, g.cz)).cross(ArXZ(g.ux, g.uz))) }
                }
                if (idx == null) println("   R4 chiuso a 4 pareti, ma senza la parete della porta confermata non so agganciare i lati")
                else {
                    // Angolo k dello schizzo = inizio del lato k = tra la parete del lato k−1 e quella del lato k (ordine R4 = ordine dello schizzo).
                    val cs = (0 until 4).map { k ->
                        val a = main.wallIds[(idx + k + 3) % 4]; val b = main.wallIds[(idx + k) % 4]
                        main.corners.firstOrNull { it.fromWall == a && it.toWall == b }?.let { ArXZ(it.x, it.z) }
                    }
                    if (cs.any { it == null }) println("   R4: angoli non nell'ordine atteso, aggancio non possibile")
                    else polyReport("R4 (aggancio descrittivo, angoli di R4)", cs.map { it!! }, tape)
                }
            } else if (tape != null) {
                val big = walls.filter { it.geometry.lengthM >= 1.0 }.sortedByDescending { it.geometry.lengthM }.take(6)
                println("   R4 non chiuso a 4 pareti; pareti R3 da almeno 1 m: ${big.joinToString(" / ") { f(it.geometry.lengthM * 100, 0) }} cm")
            }
            // ---- (B) guidata e (B') guidata + correzione congelata ----
            val sketch = r.sketch
            if (sketch == null || tape == null) { println("(B) nessuna scansione guidata (registro assente)"); continue }
            println("(B) pareti confermate: lati ${gl.keys.sorted()}; stime dello schizzo o misure a mano: " +
                "${r.events.any { it.kind == "SKETCH_LENGTH_ACCEPTED" || it.kind == "MANUAL_LENGTH" }}")
            if (gl.size < 4) { println("   meno di 4 pareti: pianta guidata non calcolabile senza misure dello schizzo"); continue }
            fun solve(label: String, ls: Map<Int, com.sagoma.planimetria.scan.sketch.ConfirmedLine>) {
                for ((choice, l2) in listOf("angoli scansione" to ls, "a 90°" to com.sagoma.planimetria.scan.sketch.SketchSquaring.squaredLines(ls))) {
                    val g = com.sagoma.planimetria.scan.sketch.SketchSolver.solve(com.sagoma.planimetria.scan.sketch.SketchGeometryInput(sketch, l2))
                    val c = g.corners
                    if (c == null) println("   $label, $choice: non calcolabile ${g.blocking.map { it.code }}") else polyReport("$label, $choice", c, tape)
                }
            }
            solve("(B) guidata", gl)
            val corners = com.sagoma.planimetria.scan.sketch.SketchSolver.solve(com.sagoma.planimetria.scan.sketch.SketchGeometryInput(sketch, gl)).corners ?: continue
            val fl = r.rec.frames.filter { it.floorY != null }.map { it.timestampNs to it.floorY!! }
            val flT = LongArray(fl.size) { fl[it].first }
            fun floorAt(ts: Long): Double { var i = java.util.Arrays.binarySearch(flT, ts); if (i < 0) i = (-i - 1).coerceIn(0, fl.size - 1); return fl[i].second }
            for (current in listOf(true, false)) {
                val bands = listOf(Triple("fascia 0,8–2,3 m (congelata)", 0.8, 2.3)) +
                    (if (r.name.contains("letto")) listOf(Triple("fascia 1,8–2,3 m (sensibilità mobili, solo lato 1 conta)", 1.8, 2.3)) else emptyList())
                for ((bandLabel, lo, hi) in bands) {
                    val frameMed = gl.keys.associateWith { ArrayList<Double>() }
                    for (d in r.rec.depth) {
                        val floor = floorAt(d.frameTimestampNs)
                        val perWall = gl.keys.associateWith { ArrayList<Double>() }
                        depthPoints(r, d, current, central = false) { pt ->
                            val hgt = pt.y - floor
                            if (hgt >= lo && hgt <= hi) {
                                val q = ArXZ(pt.x, pt.z)
                                for ((side, l) in gl) {
                                    val u = (l.b - l.a).normalized()
                                    val ca = (corners[side] - l.a) dot u; val cb = (corners[(side + 1) % 4] - l.a) dot u
                                    val along = (q - l.a) dot u
                                    if (along < minOf(ca, cb) + 0.2 || along > maxOf(ca, cb) - 0.2) continue
                                    val dd = (q - l.a) dot (l.inward.normalized() * -1.0)
                                    if (abs(dd) <= 0.15) perWall.getValue(side) += dd
                                }
                            }
                        }
                        for ((side, v) in perWall) if (v.size >= 200) frameMed.getValue(side) += median(v)
                    }
                    val shifts = frameMed.mapValues { (_, v) -> if (v.size >= 5) median(v) else null }
                    println("-- (B') posa ${if (current) "frame corrente" else "timestamp grezza (sensibilità)"}, $bandLabel: correzioni " +
                        gl.keys.sorted().joinToString(" · ") { s -> "lato $s ${shifts[s]?.let { f(it * 100) + " cm" } ?: "nessuna"} (${frameMed.getValue(s).size} fotogr.)" })
                    val corrected = gl.mapValues { (side, l) ->
                        val s = shifts[side] ?: return@mapValues l
                        val out = l.inward.normalized() * -1.0
                        l.copy(a = l.a + out * s, b = l.b + out * s)
                    }
                    solve("(B') corretta", corrected)
                }
            }
        }
    }
}
