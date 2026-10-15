package com.sagoma.planimetria.realdata

import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.hybrid.CandidateStatus
import com.sagoma.planimetria.scan.hybrid.DepthPoints
import com.sagoma.planimetria.scan.hybrid.FragmentStatus
import com.sagoma.planimetria.scan.hybrid.HybridPhase
import com.sagoma.planimetria.scan.hybrid.HybridScan
import com.sagoma.planimetria.scan.hybrid.PlaneEstimate
import com.sagoma.planimetria.scan.hybrid.WallState
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.StreamStatus
import com.sagoma.planimetria.scan.sketch.RoomSketch
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.test.Test

/** TEMPORANEO (non va nel repository): rifà offline la prova del prototipo ibrido con i riferimenti registrati dal telefono. */
class TmpHybridReplayTest {
    private fun f(v: Double?, d: Int = 1) = v?.let { if (it.isNaN()) "-" else "%.${d}f".format(it) } ?: "-"

    private class Ev(val kind: String, val side: Int?, val ts: Long, val plane: PlaneEstimate?, val override: String?, val phase: HybridPhase? = null)

    private fun num(line: String, key: String) = Regex("\"$key\":(-?[0-9.]+|null)").find(line)?.groupValues?.get(1)?.takeIf { it != "null" }

    @Test
    fun replay() {
        val path = System.getenv("SAGOMA_HYBRID_DIR") ?: return
        val files = CaptureDatasetFiles.open(File(path))
        val lines = files.read("hybrid-events.jsonl")!!.decodeToString().lines().filter { it.isNotBlank() }
        val header = lines.first()
        val sk = Regex("\"sketch\":\\[([^]]*)]").find(header)!!.groupValues[1].split(",").map { it.toDouble() }
        val sketch = RoomSketch(sk.chunked(2).map { ArXZ(it[0], it[1]) })
        val sketchH = num(header, "sketchHeightM")?.toDouble()
        val events = lines.drop(1).filter { it.contains("\"type\":\"event\"") }.map { l ->
            val p = Regex("\"plane\":\\[([^]]*)]").find(l)?.groupValues?.get(1)?.split(",")?.map { it.toDouble() }
            Ev(
                Regex("\"kind\":\"([A-Z_]+)\"").find(l)!!.groupValues[1], num(l, "side")?.toInt(), num(l, "arTimestampNs")!!.toLong(),
                p?.let { PlaneEstimate(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7].toInt(), it[8].toInt()) },
                Regex("\"override\":\"([A-Z_]+)\"").find(l)?.groupValues?.get(1),
                Regex("\"phase\":\"([A-Z_]+)\"").find(l)?.groupValues?.get(1)?.let { HybridPhase.valueOf(it) },
            )
        }
        val rec = files.recording
        val depth = rec.depth.filter { it.rawStatus == StreamStatus.SAVED && it.rawPath != null && it.camera != null && it.intrinsics != null }.sortedBy { it.frameTimestampNs }
        val t0 = rec.poses.first().timestampNs
        fun s(ts: Long) = f((ts - t0) / 1e9, 1) + " s"
        println("profondità grezze fresche: ${depth.size} in ${s(rec.poses.last().timestampNs)}; eventi: ${events.size}")
        val good = System.getenv("SAGOMA_GOODPOINTS") == "1"
        val medium = System.getenv("SAGOMA_GOODPOINTS") == "2"
        // SAGOMA_RADIUS: limite nell'immagine per i punti buoni (assente = immagine intera, come dal vivo).
        val radius = System.getenv("SAGOMA_RADIUS")?.toDoubleOrNull()
        val scan = HybridScan(sketch, sketchH, if (medium) com.sagoma.planimetria.scan.hybrid.HybridThresholds(pointMaxRangeM = 2.5, pointMinConfidence = 128, pointMaxImageRadius = 1.0, pointMaxIncidenceDeg = 45.0) else com.sagoma.planimetria.scan.hybrid.HybridThresholds(pointMaxImageRadius = radius))
        if (good || medium) scan.goodPointKinds = com.sagoma.planimetria.scan.hybrid.SurfaceKind.entries.toSet()
        println("SIMULAZIONE: punti ${if (good) "BUONI (≤ 2 m, angolo ≤ 30°, confidenza ≥ 224, centro ${radius?.let { "≤ $it" } ?: "immagine intera"})" else if (medium) "MEDI (≤ 2,5 m, angolo ≤ 45°, confidenza ≥ 128, centro ≤ 1)" else "tutti"}, salti ${if (System.getenv("SAGOMA_JUMPS") == "1") "gestiti" else "ignorati"}")
        val poseList = rec.poses.sortedBy { it.timestampNs }
        var pj = 1
        var jumpCount = 0
        var pausedFrames = 0
        val offDev = ArrayList<Triple<Double, Double, Int>>()
        val statusCount = HashMap<String, Int>()
        var di = 0
        val single = ArrayList<Double>(); val singleVertical = ArrayList<Double>(); val multi = ArrayList<Double>(); val speeds = ArrayList<Double>()
        var prevCam: com.sagoma.planimetria.scan.recording.RecPose? = null
        fun q(v: List<Double>) = if (v.isEmpty()) "-" else v.sorted().let { s -> listOf(0.25, 0.5, 0.75, 0.9).joinToString(" / ") { p -> f(s[(p * (s.size - 1)).toInt()] * 100) } }
        // SAGOMA_GOODREF=1: il riferimento di pavimento e soffitto si ristima dai soli punti BUONI della zona centrale degli ultimi
        // 1,5 s (distanza ≤ 2 m, confidenza ≥ 224, angolo ≤ 30° dalla verticale), al posto di quello registrato.
        val goodRef = System.getenv("SAGOMA_GOODREF") == "1"
        val firstWalls = LinkedHashMap<Int, PlaneEstimate>()
        for (e in events) if (e.kind.startsWith("WALL_REFERENCE") && e.plane != null && e.side != null && e.side !in firstWalls) firstWalls[e.side] = e.plane
        val lastFed = ArrayDeque<DepthPoints>()
        fun goodReference(rec0: PlaneEstimate, ts: Long): PlaneEstimate? {
            val th = scan.th
            val cosMax = kotlin.math.cos(Math.toRadians(th.pointMaxIncidenceDeg))
            val ys = ArrayList<Double>(); var all = 0
            for (d in lastFed) {
                if (ts / 1_000_000 - d.timeMs > th.refWindowMs) continue
                for (i in 0 until d.size) {
                    if (!d.central[i]) continue
                    if (abs(d.y[i] - rec0.cy) > 0.15) continue // la superficie inquadrata, non altro
                    all++
                    if (d.rangeM!![i] > th.pointMaxRangeM || d.confidence!![i] < th.pointMinConfidence) continue
                    val rx = d.x[i] - d.camX; val ry = d.y[i] - d.camY; val rz = d.z[i] - d.camZ
                    val rl = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz)
                    if (rl <= 0 || abs(ry) / rl < cosMax) continue
                    ys += d.y[i]
                }
            }
            val med = ys.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
            println("   RIFERIMENTO dai punti buoni: ${ys.size} su $all punti centrali entro 15 cm; quota registrata ${f(rec0.cy * 100, 1)} cm, dai buoni ${f(med?.times(100), 1)} cm, differenza ${f(med?.let { (it - rec0.cy) * 100 }, 1)} cm")
            return if (med == null || ys.size < th.refMinPoints) null else rec0.copy(cy = med)
        }
        fun feedUntil(ts: Long) {
            while (di < depth.size && depth[di].frameTimestampNs <= ts) {
                val d = depth[di++]
                if (System.getenv("SAGOMA_JUMPS") == "1") {
                    // Salti di posizione tra la profondità precedente e questa, come li segnala ArScanView (pose consecutive).
                    while (pj < poseList.size && poseList[pj].timestampNs <= d.frameTimestampNs) {
                        val a = poseList[pj - 1]; val b = poseList[pj]
                        val ta = a.tracking == "TRACKING"; val tb = b.tracking == "TRACKING"
                        if (tb && a.camera != null && b.camera != null) {
                            val dd = kotlin.math.sqrt((a.camera!!.x - b.camera!!.x).let { it * it } + (a.camera!!.y - b.camera!!.y).let { it * it } + (a.camera!!.z - b.camera!!.z).let { it * it })
                            if (!ta || dd > 0.10) { scan.poseJump(); jumpCount++ }
                        }
                        pj++
                    }
                }
                val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
                val raw = files.read(d.rawPath!!) ?: continue
                if (raw.size != w * h * 2) continue
                val k0 = d.intrinsics!!
                val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
                val conf = d.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h }
                val pts = DepthPoints.fromDepth(d.seq, d.frameTimestampNs / 1_000_000, DepthRaw.decode(raw, w, h), conf, k, d.camera!!)
                scan.addDepth(pts)
                lastFed.addLast(pts); while (lastFed.size > 8) lastFed.removeFirst()
                // SAGOMA_WALLMATCH=1: le pareti confermate (prima conferma di ogni lato, nel mondo di ARCore registrato) sono ancora al
                // loro posto quando si vedono? Punti entro 15 cm dal piano (almeno 500): mediana della distanza e direzione del piano.
                if (System.getenv("SAGOMA_WALLMATCH") == "1" && scan.snapshot().phase == HybridPhase.WALLS) {
                    val out = ArrayList<String>()
                    for ((side, p) in firstWalls) {
                        val idx = (0 until pts.size).filter { i -> abs((pts.x[i] - p.cx) * p.nx + (pts.z[i] - p.cz) * p.nz) <= 0.15 }
                        if (idx.size < 500) continue
                        val dd = idx.map { i -> (pts.x[i] - p.cx) * p.nx + (pts.z[i] - p.cz) * p.nz }.sorted()
                        val q = com.sagoma.planimetria.scan.hybrid.HybridMath.fitPlane(DoubleArray(idx.size) { pts.x[idx[it]] }, DoubleArray(idx.size) { pts.y[idx[it]] }, DoubleArray(idx.size) { pts.z[idx[it]] }, IntArray(idx.size))
                        val ang = q?.let { Math.toDegrees(kotlin.math.acos(abs(it.nx * p.nx + it.nz * p.nz) / kotlin.math.sqrt(it.nx * it.nx + it.nz * it.nz).coerceAtLeast(1e-9))) }
                        out += "lato $side: ${idx.size} punti, scarto ${f(dd[dd.size / 2] * 100, 1)} cm, direzione ${f(ang, 1)}°"
                    }
                    if (out.isNotEmpty()) println("PARETI ${s(d.frameTimestampNs)} · " + out.joinToString(" · "))
                }
                // SAGOMA_FLOORDRIFT=1: per ogni immagine della fase pavimento, mediana della quota dei punti buoni rispetto al riferimento.
                if (System.getenv("SAGOMA_FLOORDRIFT") == "1") scan.snapshot().let { sn ->
                    val ref = sn.floor.reference
                    if (sn.phase == HybridPhase.FLOOR && ref != null) {
                        val cosMax = kotlin.math.cos(Math.toRadians(scan.th.pointMaxIncidenceDeg))
                        val v = ArrayList<Double>()
                        for (i in 0 until pts.size) {
                            val dy = pts.y[i] - ref.cy
                            if (abs(dy) > 0.08 || pts.rangeM!![i] > 2.0 || pts.confidence!![i] < 224) continue
                            val rx = pts.x[i] - pts.camX; val ry = pts.y[i] - pts.camY; val rz = pts.z[i] - pts.camZ
                            val rl = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz)
                            if (rl <= 0 || abs(ry) / rl < cosMax) continue
                            v += dy
                        }
                        if (v.size >= 200) println("DERIVA ${s(d.frameTimestampNs)} camera x ${f(pts.camX, 2)} z ${f(pts.camZ, 2)} y ${f(pts.camY, 2)} · punti ${v.size} · quota mediana ${f(v.sorted()[v.size / 2] * 100, 1)} cm")
                    }
                }
                val snap = scan.snapshot()
                if (snap.unverified) pausedFrames++
                if (snap.phase == HybridPhase.WALLS && snap.candidate.status == CandidateStatus.OFF_SKETCH) offDev += Triple((d.frameTimestampNs - t0) / 1e9, snap.candidate.deviationDeg ?: Double.NaN, snap.candidate.side ?: -1)
                val key = "${snap.phase}/${snap.candidate.status}"
                statusCount[key] = (statusCount[key] ?: 0) + 1
                // Diagnosi: dispersione del piano al centro con UN solo fotogramma, contro quella del candidato (più fotogrammi).
                if (snap.phase == HybridPhase.WALLS) {
                    val idx = (0 until pts.size).filter { pts.central[it] }
                    if (idx.size >= 300) {
                        val one = com.sagoma.planimetria.scan.hybrid.HybridMath.fitPlane(
                            DoubleArray(idx.size) { pts.x[idx[it]] }, DoubleArray(idx.size) { pts.y[idx[it]] }, DoubleArray(idx.size) { pts.z[idx[it]] }, IntArray(idx.size),
                        )
                        if (one != null) {
                            single += one.sigmaM
                            val vert = Math.toDegrees(kotlin.math.asin(abs(one.ny).coerceAtMost(1.0)))
                            if (vert <= 10.0) singleVertical += one.sigmaM
                        }
                    }
                    snap.candidate.plane?.let { multi += it.sigmaM }
                    val cam = d.camera!!
                    prevCam?.let { p -> speeds += kotlin.math.sqrt((cam.x - p.x) * (cam.x - p.x) + (cam.z - p.z) * (cam.z - p.z)) / 0.5 }
                    prevCam = cam
                }
            }
        }
        for (e in events) {
            feedUntil(e.ts)
            when (e.kind) {
                "START_SIDE" -> scan.chooseStartSide(e.side!!)
                // Le registrazioni vecchie hanno l'ordine pavimento → soffitto → pareti: il motore va nella fase registrata.
                "FLOOR_REFERENCE" -> { scan.replayPhase(HybridPhase.FLOOR); scan.confirmRecordedReference((if (goodRef) goodReference(e.plane!!, e.ts) else null) ?: e.plane!!, null, null) }
                "CEILING_REFERENCE" -> { scan.replayPhase(HybridPhase.CEILING); scan.confirmRecordedReference((if (goodRef) goodReference(e.plane!!, e.ts) else null) ?: e.plane!!, null, null) }
                "FLOOR_DONE", "CEILING_DONE", "WALLS_DONE", "CEILING_SKIPPED" -> e.phase?.let { scan.replayPhase(it) }
                "FLOOR_REDO", "CEILING_REDO" -> scan.redoHorizontal()
                "WALL_REFERENCE", "WALL_REFERENCE_OVERRIDE" -> if (e.plane != null) { scan.replayPhase(HybridPhase.WALLS); scan.setTarget(e.side!!); scan.confirmRecordedReference(e.plane, e.side, e.override?.let { CandidateStatus.valueOf(it) }) } else println("   (conferma non avvenuta: nessun piano registrato)")
                "REDO" -> scan.redoWall(e.side!!)
            }
            val sn = scan.snapshot()
            println("@${s(e.ts)} ${e.kind}${e.side?.let { " lato $it" } ?: ""}${e.override?.let { " (avviso superato: $it)" } ?: ""} · pavimento accettato ${f(sn.floor.areaM2)} m², visto ${f(sn.floor.seenAreaM2)} m²")
        }
        feedUntil(Long.MAX_VALUE)
        println("FUORI SCHIZZO (fase pareti): ${offDev.size} fotogrammi; scarto dal lato atteso per decine di secondi: " + offDev.groupBy { (it.first / 20).toInt() * 20 }.entries.joinToString(" · ") { (t, v) -> "${t}s: ${v.map { it.second }.sorted().let { s -> f(s[s.size / 2], 0) }}° (lato ${v.first().third}, ${v.size})" })
        // Rotazioni brusche dell'orientamento (angolo tra pose consecutive) e loro momento
        run {
            val pp = rec.poses.filter { it.camera != null }.sortedBy { it.timestampNs }
            var n = 0
            for (i in 1 until pp.size) {
                val a = pp[i - 1].camera!!; val b = pp[i].camera!!
                val dq = kotlin.math.abs(a.qx * b.qx + a.qy * b.qy + a.qz * b.qz + a.qw * b.qw).coerceAtMost(1.0)
                val ang = Math.toDegrees(2 * kotlin.math.acos(dq))
                val dt = (pp[i].timestampNs - pp[i - 1].timestampNs) / 1e9
                if (ang > 10 && dt < 0.2) { n++; if (n <= 20) println("ROTAZIONE brusca a ${s(pp[i].timestampNs)}: ${f(ang)}° in ${f(dt * 1000, 0)} ms (${pp[i - 1].tracking} → ${pp[i].tracking})") }
            }
            println("ROTAZIONI brusche oltre 10°: $n")
        }
        println("SALTI applicati: $jumpCount; fotogrammi da verificare: $pausedFrames su ${depth.size}; scartati perché spostati: ${scan.snapshot().discardedFrames}; usati senza verifica: ${scan.snapshot().uncheckedFrames}")
        println("DIAGNOSI fase pareti, dispersione del piano al centro (cm, quartili 25/50/75/90):")
        println("   un solo fotogramma, tutti: ${q(single)} (${single.size} fotogrammi)")
        println("   un solo fotogramma, solo piani verticali: ${q(singleVertical)} (${singleVertical.size})")
        println("   candidato (fino a 6 fotogrammi recenti): ${q(multi)} (${multi.size})")
        println("   spostamento della camera tra fotogrammi (m/s, stessi quartili ×100): ${q(speeds)}")
        val snap = scan.snapshot()
        println("stati del riferimento candidato (per fotogramma, fase/stato): " + statusCount.entries.sortedBy { it.key }.joinToString(" · ") { "${it.key} ${it.value}" })
        println("pavimento: quota ${f(snap.floor.heightY, 3)} m, ${f(snap.floor.areaM2)} m², frammenti " + snap.floor.fragments.groupingBy { it.status }.eachCount())
        for (fr in snap.floor.fragments.take(5)) println("   pavimento ${fr.status}: ${fr.points} punti, ${fr.frames} fotogr., scarto ${f(fr.medianOffsetM * 100)} cm, ${f(fr.extentAM, 2)}×${f(fr.extentBM, 2)} m")
        // Conteggio dei punti: per fotogramma (validi e zona centrale), nella finestra di conferma del pavimento e nella sua fase.
        val fRef = events.first { it.kind == "FLOOR_REFERENCE" }.ts
        val fEnd = events.first { it.kind == "FLOOR_DONE" }.ts
        val validPer = ArrayList<Double>(); val centralPer = ArrayList<Double>(); val nearFloorPer = ArrayList<Double>()
        var confirmFrames = 0; var confirmCentral = 0
        val floorY = events.first { it.kind == "FLOOR_REFERENCE" }.plane!!.cy
        for (d in depth) {
            if (d.frameTimestampNs < fRef - 3_000_000_000L || d.frameTimestampNs > fEnd) continue
            val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
            val raw = files.read(d.rawPath!!) ?: continue
            val k0 = d.intrinsics!!; val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
            val conf = d.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h }
            val p = DepthPoints.fromDepth(d.seq, 0, DepthRaw.decode(raw, w, h), conf, k, d.camera!!)
            val c = p.central.count { it }
            if (d.frameTimestampNs <= fRef) { confirmFrames++; confirmCentral += c; continue }
            validPer += p.size.toDouble(); centralPer += c.toDouble()
            nearFloorPer += (0 until p.size).count { abs(p.y[it] - floorY) <= 0.08 }.toDouble()
        }
        fun qn(v: List<Double>) = if (v.isEmpty()) "-" else v.sorted().let { s -> listOf(0.25, 0.5, 0.75).joinToString(" / ") { p -> f(s[(p * (s.size - 1)).toInt()], 0) } }
        println("PUNTI: pixel per fotogramma ${depth.firstOrNull()?.let { (it.rawWidth ?: it.width) * (it.rawHeight ?: it.height) }}; " +
            "3 s prima della conferma del pavimento: $confirmFrames fotogrammi, $confirmCentral punti nella zona centrale")
        println("PUNTI fase pavimento (${validPer.size} fotogrammi, quartili 25/50/75): validi ${qn(validPer)}, zona centrale ${qn(centralPer)}, entro ±8 cm dal pavimento ${qn(nearFloorPer)}")
        println("soffitto: quota ${f(snap.ceiling.heightY, 3)} m, ${f(snap.ceiling.areaM2)} m², frammenti " + snap.ceiling.fragments.groupingBy { it.status }.eachCount())
        for (fr in snap.ceiling.fragments.take(8)) println("   soffitto ${fr.status}: ${fr.points} punti, ${fr.frames} fotogr., scarto ${f(fr.medianOffsetM * 100)} cm, incl ${f(fr.angleDeg)}°, ${f(fr.extentAM, 2)}×${f(fr.extentBM, 2)} m")
        // Diagnosi del soffitto: nei fotogrammi della fase soffitto, quanti punti stanno vicino alla quota di riferimento.
        val ceilRef = events.firstOrNull { it.kind == "CEILING_REFERENCE" } ?: events.first { it.kind == "FLOOR_REFERENCE" }
        val ceilEnd = events.firstOrNull { it.kind == "CEILING_DONE" || it.kind == "CEILING_SKIPPED" }?.ts ?: 0L
        var frames = 0; var near = 0L; var above = 0L; var total = 0L
        val res = ArrayList<Double>()
        for (d in depth) {
            if (d.frameTimestampNs < ceilRef.ts || d.frameTimestampNs > ceilEnd) continue
            val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
            val raw = files.read(d.rawPath!!) ?: continue
            val k0 = d.intrinsics!!; val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
            val conf = d.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h }
            val p = DepthPoints.fromDepth(d.seq, 0, DepthRaw.decode(raw, w, h), conf, k, d.camera!!)
            frames++; total += p.size
            for (i in 0 until p.size) {
                val r = p.y[i] - ceilRef.plane!!.cy
                if (r > -0.5) { above++; res += r }
                if (abs(r) <= 0.08) near++
            }
        }
        val sr = res.sorted()
        println("DIAGNOSI soffitto: $frames fotogrammi nella fase, punti validi $total, entro 50 cm sotto il riferimento o sopra $above, entro ±8 cm $near; " +
            "scarti dal riferimento (cm) p10/p50/p90: ${if (sr.isEmpty()) "-" else listOf(0.1, 0.5, 0.9).joinToString(" / ") { f(sr[(it * (sr.size - 1)).toInt()] * 100) }}")
        println("altezza: ${f(snap.heightM, 3)} m (schizzo ${f(snap.sketchHeightM, 2)}, valore predefinito)")
        for (w in snap.walls) {
            println("lato ${w.side}: ${w.state}, avviso superato ${w.overriddenWarning}, scarto dallo schizzo ${f(w.sketchDeviationDeg)}°, dispersione attorno alla retta ${f(w.residualM?.times(100))} cm, " +
                "tratto osservato ${f(if (w.fromU != null) (w.toU!! - w.fromU!!) * 100 else null, 0)} cm")
            for (fr in w.fragments.take(6)) println("   ${fr.status}: ${fr.points} punti, ${fr.frames} fotogr., scarto ${f(fr.medianOffsetM * 100)} cm, incl ${f(fr.angleDeg)}°, ${f(fr.extentAM, 2)}×${f(fr.extentBM, 2)} m")
        }
        // Metro della stanzetta (lati nell'ordine dello schizzo, diagonale misurata tra gli angoli 1 e 3; angoli dal metro).
        val tape = listOf(3.295, 3.115, 3.205, 3.09)
        val tapeAngles = listOf(88.93, 89.40, 90.14, 91.52)
        val c = snap.corners
        println("box ${snap.box}; angoli: " + c.mapIndexed { k, p -> "$k=" + (p?.let { "(${f(it.x, 2)}, ${f(it.z, 2)})" } ?: "-") }.joinToString(" "))
        for (k in 0 until 4) {
            val a = c[k]; val b = c[(k + 1) % 4]
            if (a != null && b != null) println("   lato $k: ${f(a.distanceTo(b) * 100)} cm, metro ${f(tape[k] * 100)} → scarto ${f((a.distanceTo(b) - tape[k]) * 100)} cm")
            val p = c[(k + 3) % 4]
            if (a != null && p != null && b != null) {
                val u = p - a; val v = b - a
                val ang = Math.toDegrees(acos(((u dot v) / (u.length * v.length)).coerceIn(-1.0, 1.0)))
                println("   angolo $k: ${f(ang, 2)}°, metro ${f(tapeAngles[k], 2)}° → scarto ${f(ang - tapeAngles[k], 2)}°")
            }
        }
        // Angoli tra coppie di pareti misurate (anche non adiacenti), per capire le associazioni.
        val ms = snap.walls.filter { it.state == WallState.MEASURED }
        for (i in ms.indices) for (j in i + 1 until ms.size) {
            val a = ms[i]; val b = ms[j]
            val ang = Math.toDegrees(acos((a.inward!! dot b.inward!!).coerceIn(-1.0, 1.0)))
            val dist = abs((b.point!! - a.point!!) dot a.inward!!)
            println("   lati ${a.side}–${b.side}: angolo tra le normali interne ${f(ang)}°, distanza del centro di ${b.side} dalla retta di ${a.side} ${f(dist * 100, 0)} cm")
        }
        for (d in snap.discrepancies) println("discrepanza: ${d.kind} ${d.sides} misurato ${f(d.measured, 3)} atteso ${f(d.expected, 3)}")
        // ---- DIAGNOSI dei punti "fuori stanza": dove stanno e quando compaiono ----
        // Stanza attesa (SOLO per la diagnosi, metro): dall'angolo 1 (porta ∩ finestra), lungo la parete della porta per 3,295 m e
        // lungo la finestra per 3,115 m, nel verso interno delle due pareti misurate. Margine 20 cm.
        val w0 = snap.walls[0]; val w1 = snap.walls[1]; val c1 = snap.corners[1]
        if (w0.state == WallState.MEASURED && w1.state == WallState.MEASURED && c1 != null) {
            val n0 = w0.inward!!; val n1 = w1.inward!!
            val m = 0.20; val l0 = 3.295; val l1 = 3.115
            val y0 = snap.floor.heightY!!
            // regioni: dentro, oltre la porta (dietro la parete 0), oltre la finestra (dietro la parete 1), oltre il lato 2, oltre il lato 3
            val labels = listOf("dentro", "oltre la parete della porta", "oltre la parete della finestra", "oltre il lato 2 (mobile)", "oltre il lato 3 (letto)")
            fun region(x: Double, z: Double): Int {
                val q = ArXZ(x, z) - c1
                val d0 = q dot n0; val d1 = q dot n1
                return when {
                    d0 < -m -> 1
                    d1 < -m -> 2
                    d0 > l1 + m -> 3
                    d1 > l0 + m -> 4
                    else -> 0
                }
            }
            val byMinute = java.util.TreeMap<Int, IntArray>()
            val camOut = java.util.TreeMap<Int, IntArray>()
            for (d in depth) {
                val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
                val raw = files.read(d.rawPath!!) ?: continue
                val k0 = d.intrinsics!!; val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
                val conf = d.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h }
                val p = DepthPoints.fromDepth(d.seq, 0, DepthRaw.decode(raw, w, h), conf, k, d.camera!!)
                val minute = ((d.frameTimestampNs - t0) / 60_000_000_000L).toInt()
                val acc = byMinute.getOrPut(minute) { IntArray(5) }
                for (i in 0 until p.size) if (abs(p.y[i] - y0) <= 0.08) acc[region(p.x[i], p.z[i])]++
                camOut.getOrPut(minute) { IntArray(5) }[region(d.camera!!.x, d.camera!!.z)]++
            }
            println("DIAGNOSI punti di pavimento (entro ±8 cm) per minuto e per zona [${labels.joinToString(" | ")}], e dove stava il telefono:")
            for ((mi, a) in byMinute) println("   minuto $mi: ${a.joinToString(" | ")}   · telefono: ${camOut[mi]!!.joinToString(" | ")}")
            // Deriva: la parete della porta rivista nel tempo — mediana della distanza dei suoi punti dalla retta misurata, per minuto.
            val drift = java.util.TreeMap<Int, ArrayList<Double>>()
            val along = java.util.TreeMap<Int, ArrayList<Double>>()
            for (d in depth) {
                val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
                val raw = files.read(d.rawPath!!) ?: continue
                val k0 = d.intrinsics!!; val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
                val conf = d.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h }
                val p = DepthPoints.fromDepth(d.seq, 0, DepthRaw.decode(raw, w, h), conf, k, d.camera!!)
                val minute = ((d.frameTimestampNs - t0) / 60_000_000_000L).toInt()
                for (i in 0 until p.size) {
                    if (p.y[i] < y0 + 0.3 || p.y[i] > y0 + 2.2) continue
                    val q = ArXZ(p.x[i], p.z[i]) - w0.point!!
                    val dn = q dot n0
                    if (abs(dn) > 0.15) continue
                    drift.getOrPut(minute) { ArrayList() } += dn
                    along.getOrPut(minute) { ArrayList() } += ((ArXZ(p.x[i], p.z[i]) - c1) dot (w0.direction!! * (if ((w0.direction!! dot n1) >= 0) 1.0 else -1.0)))
                }
            }
            println("DIAGNOSI parete della porta (punti entro 15 cm, 0,3–2,2 m): per minuto mediana della distanza dalla retta (cm) e tratto lungo la parete dall'angolo 1 (p5–p95, m):")
            for ((mi, v) in drift) {
                val s = v.sorted(); val a = along.getValue(mi).sorted()
                println("   minuto $mi: ${s.size} punti, distanza ${f(s[s.size / 2] * 100)} cm, lungo la parete ${f(a[(a.size * 0.05).toInt()], 2)} … ${f(a[(a.size * 0.95).toInt()], 2)} m")
            }
        }
        // Salti di posizione del telefono (lettore corretto delle pose): spostamenti > 10 cm tra due pose consecutive in tracciamento.
        val ps = rec.poses.filter { it.camera != null && it.tracking == "TRACKING" }.sortedBy { it.timestampNs }
        var jumps = 0
        for (i in 1 until ps.size) {
            val a = ps[i - 1].camera!!; val b = ps[i].camera!!
            val dt = (ps[i].timestampNs - ps[i - 1].timestampNs) / 1e9
            val dd = kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))
            if (dd > 0.10 && dt < 0.2) { jumps++; if (jumps <= 15) println("SALTO di posizione a ${s(ps[i].timestampNs)}: ${f(dd * 100)} cm in ${f(dt * 1000, 0)} ms") }
        }
        val pauses = rec.poses.zipWithNext().count { (a, b) -> a.tracking == "TRACKING" && b.tracking != "TRACKING" }
        println("SALTI di posizione > 10 cm: $jumps; interruzioni del tracciamento: $pauses; pose ${rec.poses.size}")
        check(snap.phase != HybridPhase.START_SIDE)
        check(FragmentStatus.ACCEPTED.name.isNotEmpty())
    }
}
