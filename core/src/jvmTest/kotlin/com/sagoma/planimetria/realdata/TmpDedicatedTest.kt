package com.sagoma.planimetria.realdata

import com.sagoma.planimetria.scan.hybrid.DepthPoints
import com.sagoma.planimetria.scan.hybrid.HybridMath
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.StreamStatus
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test

/**
 * TEMPORANEO (non va nel repository): registrazione dedicata P0.21. Passo 1 (SAGOMA_STEP=1): linea del tempo (immagini con due
 * pareti, tratti fermi, foto) per identificare spigoli e pareti. Passo 2 (SAGOMA_STEP=2, con gli intervalli identificati):
 * Q1, Q1 corretta, Q2 con le regole fissate prima.
 */
class TmpDedicatedTest {
    /** ESPLORATIVA: soglia di confidenza dei punti per spigoli (P0.20–P0.22 fissano 224). */
    private val minConf = System.getenv("SAGOMA_MINCONF")?.toIntOrNull() ?: 224
    private fun f(v: Double, d: Int = 1) = if (v.isNaN()) "-" else "%.${d}f".format(v)

    private class Wall(val nx: Double, val nz: Double, val c: Double, val inl: IntArray)
    private class Frame(val t: Double, val ns: Long, val pts: DepthPoints, val speed: Double)

    private fun ransacWall(p: DepthPoints, idx: IntArray, rnd: Random, minPts: Int): Wall? {
        if (idx.size < minPts) return null
        val x = p.x; val y = p.y; val z = p.z
        val sub = if (idx.size <= 1000) idx else IntArray(1000) { idx[(it.toLong() * idx.size / 1000).toInt()] }
        var best = -1; var bnx = 0.0; var bnz = 0.0; var bc = 0.0
        repeat(400) {
            val a = sub[rnd.nextInt(sub.size)]; val b = sub[rnd.nextInt(sub.size)]
            val dx = x[b] - x[a]; val dz = z[b] - z[a]; val len = sqrt(dx * dx + dz * dz)
            if (len < 0.2) return@repeat
            val nx = -dz / len; val nz = dx / len; val c = nx * x[a] + nz * z[a]
            var n = 0
            for (i in sub) if (abs(nx * x[i] + nz * z[i] - c) <= 0.02) n++
            if (n > best) { best = n; bnx = nx; bnz = nz; bc = c }
        }
        if (best < 0) return null
        val inl = idx.filter { abs(bnx * x[it] + bnz * z[it] - bc) <= 0.02 }.toIntArray()
        if (inl.size < minPts) return null
        val q = HybridMath.fitPlane(DoubleArray(inl.size) { x[inl[it]] }, DoubleArray(inl.size) { y[inl[it]] }, DoubleArray(inl.size) { z[inl[it]] }, IntArray(inl.size)) ?: return null
        if (abs(q.ny) > kotlin.math.sin(Math.toRadians(10.0))) return null
        val h = sqrt(q.nx * q.nx + q.nz * q.nz)
        var nx = q.nx / h; var nz = q.nz / h
        if (nx * (p.camX - q.cx) + nz * (p.camZ - q.cz) < 0) { nx = -nx; nz = -nz } // verso la camera
        return Wall(nx, nz, nx * q.cx + nz * q.cz, inl)
    }

    /** Angolo di vista (gradi) del punto i rispetto alla normale (nx, ny, nz). */
    private fun incidence(p: DepthPoints, i: Int, nx: Double, ny: Double, nz: Double): Double {
        val rx = p.x[i] - p.camX; val ry = p.y[i] - p.camY; val rz = p.z[i] - p.camZ
        val rl = sqrt(rx * rx + ry * ry + rz * rz)
        return Math.toDegrees(acos((abs(rx * nx + ry * ny + rz * nz) / rl).coerceAtMost(1.0)))
    }

    /** Due pareti nella stessa immagine (regole P0.20): angolo interno, angoli di vista mediani, intersezione. */
    private fun corner(p: DepthPoints, rnd: Random, correct: ((Double) -> Double)?): DoubleArray? {
        val q = if (correct == null) p else corrected(p, correct)
        val idx = (0 until q.size).filter { i -> q.confidence!![i] >= 224 && q.rangeM!![i] <= 3.0 && q.y[i] >= q.camY - 1.0 && q.y[i] <= q.camY + 0.9 }.toIntArray()
        val a = ransacWall(q, idx, rnd, 1500) ?: return null
        val used = a.inl.toHashSet()
        val b = ransacWall(q, idx.filter { it !in used }.toIntArray(), rnd, 1500) ?: return null
        val between = Math.toDegrees(acos((a.nx * b.nx + a.nz * b.nz).coerceIn(-1.0, 1.0)))
        if (between < 60 || between > 120) return null
        fun medInc(w: Wall) = w.inl.map { incidence(q, it, w.nx, 0.0, w.nz) }.sorted().let { it[it.size / 2] }
        val det = a.nx * b.nz - a.nz * b.nx
        val px = (a.c * b.nz - a.nz * b.c) / det; val pz = (a.nx * b.c - a.c * b.nx) / det
        return doubleArrayOf(180.0 - between, medInc(a), medInc(b), px, pz)
    }

    /**
     * Correzione dell'errore di sbieco (P0.21): ogni punto si sposta lungo il raggio di −e(θ), con e(θ) l'errore in distanza
     * stimato sul pavimento per il suo angolo di vista. Per le pareti l'angolo di vista si calcola rispetto alla normale
     * orizzontale stimata senza correzione (prima passata), qui approssimata con la normale del piano verticale migliore.
     */
    private fun corrected(p: DepthPoints, e: (Double) -> Double): DepthPoints {
        val idx = (0 until p.size).filter { i -> p.confidence!![i] >= minConf && p.rangeM!![i] <= 3.0 && p.y[i] >= p.camY - 1.0 && p.y[i] <= p.camY + 0.9 }.toIntArray()
        val rnd = Random(7)
        val a = ransacWall(p, idx, rnd, 1500) ?: return p
        val used = a.inl.toHashSet()
        val b = ransacWall(p, idx.filter { it !in used }.toIntArray(), rnd, 1500)
        val x = p.x.copyOf(); val y = p.y.copyOf(); val z = p.z.copyOf()
        val sets = listOfNotNull(a, b)
        for (w in sets) for (i in w.inl) {
            val th = incidence(p, i, w.nx, 0.0, w.nz)
            val rx = p.x[i] - p.camX; val ry = p.y[i] - p.camY; val rz = p.z[i] - p.camZ
            val rl = sqrt(rx * rx + ry * ry + rz * rz)
            val k = (rl - e(th)) / rl
            x[i] = p.camX + rx * k; y[i] = p.camY + ry * k; z[i] = p.camZ + rz * k
        }
        return DepthPoints(p.frameId, p.timeMs, p.camX, p.camY, p.camZ, x, y, z, p.central, p.pixels, p.confidence, p.rangeM, p.imageRadius)
    }

    @Test
    fun dedicated() {
        val dir = System.getenv("SAGOMA_DEDICATED_DIR") ?: return
        val step = System.getenv("SAGOMA_STEP") ?: "1"
        val files = CaptureDatasetFiles.open(File(dir))
        val rec = files.recording
        val poses = rec.poses.sortedBy { it.timestampNs }
        val t0 = poses.first().timestampNs
        fun s(ns: Long) = (ns - t0) / 1e9
        // Interruzioni: posizione persa o salto oltre 10 cm tra pose consecutive.
        val breaks = ArrayList<Long>()
        for (i in 1 until poses.size) {
            val a = poses[i - 1]; val b = poses[i]
            if (b.tracking != "TRACKING" || a.tracking != "TRACKING") { breaks += b.timestampNs; continue }
            val ca = a.camera ?: continue; val cb = b.camera ?: continue
            if (sqrt((ca.x - cb.x) * (ca.x - cb.x) + (ca.y - cb.y) * (ca.y - cb.y) + (ca.z - cb.z) * (ca.z - cb.z)) > 0.10) breaks += b.timestampNs
        }
        fun speedAt(ns: Long): Double {
            val w = poses.filter { abs(it.timestampNs - ns) <= 500_000_000L && it.tracking == "TRACKING" && it.camera != null }
            if (w.size < 2) return Double.NaN
            var d = 0.0
            for (i in 1 until w.size) { val a = w[i - 1].camera!!; val b = w[i].camera!!; d += sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)) }
            return d / ((w.last().timestampNs - w.first().timestampNs) / 1e9)
        }
        val frames = ArrayList<Frame>()
        for (dp in rec.depth.filter { it.rawStatus == StreamStatus.SAVED && it.rawPath != null && it.camera != null && it.intrinsics != null }.sortedBy { it.frameTimestampNs }) {
            val w = dp.rawWidth ?: dp.width; val h = dp.rawHeight ?: dp.height
            val raw = files.read(dp.rawPath!!) ?: continue
            if (raw.size != w * h * 2) continue
            val k0 = dp.intrinsics!!
            val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
            val conf = dp.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h } ?: continue
            frames += Frame(s(dp.frameTimestampNs), dp.frameTimestampNs, DepthPoints.fromDepth(dp.seq, dp.frameTimestampNs / 1_000_000, DepthRaw.decode(raw, w, h), conf, k, dp.camera!!), speedAt(dp.frameTimestampNs))
        }
        val rgb = rec.rgb.sortedBy { it.timestampNs }
        fun photo(t: Double) = rgb.minByOrNull { abs(s(it.timestampNs) - t) }?.path ?: "-"
        val rnd = Random(20261014)
        if (step == "1") {
            println("immagini di profondità ${frames.size}; interruzioni: " + breaks.map { f(s(it), 0) }.distinct().joinToString(" "))
            for (fr in frames) {
                val c = corner(fr.pts, rnd, null)
                val line = StringBuilder("${f(fr.t, 1)} s · ${f(fr.speed * 100, 0)} cm/s")
                if (c != null) line.append(" · DUE PARETI angolo ${f(c[0], 1)}° viste ${f(c[1], 0)}°/${f(c[2], 0)}° spigolo (${f(c[3], 2)}, ${f(c[4], 2)})")
                else {
                    // Parete di fronte: piano verticale dominante dei punti buoni.
                    val p = fr.pts
                    val idx = (0 until p.size).filter { i -> p.confidence!![i] >= 224 && p.rangeM!![i] <= 2.0 && p.y[i] >= p.camY - 1.0 && p.y[i] <= p.camY + 0.9 }.toIntArray()
                    ransacWall(p, idx, rnd, 1500)?.let { wl -> line.append(" · parete normale ${f(Math.toDegrees(kotlin.math.atan2(wl.nz, wl.nx)), 1)}° punti ${wl.inl.size}") }
                }
                line.append(" · foto ${photo(fr.t)}")
                println(line)
            }
            return
        }
        if (step == "4" || step == "5") {
            // P0.22: due pareti per immagine, piani rifatti con i soli punti a più di 40 cm dallo spigolo; angolo e spigolo.
            println("interruzioni: " + breaks.map { f(s(it), 1) }.joinToString(" ").ifEmpty { "nessuna" })
            val res = ArrayList<DoubleArray>() // t, angolo, x, z
            for (fr in frames) {
                val p = fr.pts
                val rnd2 = Random(20261014 + p.frameId)
                val idx = (0 until p.size).filter { i -> p.confidence!![i] >= minConf && p.rangeM!![i] <= 3.0 && p.y[i] >= p.camY - 1.0 && p.y[i] <= p.camY + 0.9 }.toIntArray()
                val a = ransacWall(p, idx, rnd2, 1500) ?: continue
                val used = a.inl.toHashSet()
                val b = ransacWall(p, idx.filter { it !in used }.toIntArray(), rnd2, 1500) ?: continue
                val between = Math.toDegrees(acos((a.nx * b.nx + a.nz * b.nz).coerceIn(-1.0, 1.0)))
                if (between < 60 || between > 120) continue
                val det = a.nx * b.nz - a.nz * b.nx
                val px = (a.c * b.nz - a.nz * b.c) / det; val pz = (a.nx * b.c - a.c * b.nx) / det
                fun refit(w: Wall): DoubleArray? {
                    val keep = w.inl.filter { i -> sqrt((p.x[i] - px) * (p.x[i] - px) + (p.z[i] - pz) * (p.z[i] - pz)) > 0.4 }
                    if (keep.size < 800) return null
                    val q = HybridMath.fitPlane(DoubleArray(keep.size) { p.x[keep[it]] }, DoubleArray(keep.size) { p.y[keep[it]] }, DoubleArray(keep.size) { p.z[keep[it]] }, IntArray(keep.size)) ?: return null
                    val h = sqrt(q.nx * q.nx + q.nz * q.nz)
                    var nx = q.nx / h; var nz = q.nz / h
                    if (nx * (p.camX - q.cx) + nz * (p.camZ - q.cz) < 0) { nx = -nx; nz = -nz }
                    return doubleArrayOf(nx, nz, nx * q.cx + nz * q.cz)
                }
                val na = refit(a) ?: continue; val nb = refit(b) ?: continue
                val ang = 180.0 - Math.toDegrees(acos((na[0] * nb[0] + na[1] * nb[1]).coerceIn(-1.0, 1.0)))
                val d2 = na[0] * nb[1] - na[1] * nb[0]
                if (abs(d2) < 1e-6) continue
                val cx = (na[2] * nb[1] - na[1] * nb[2]) / d2; val cz = (na[0] * nb[2] - na[2] * nb[0]) / d2
                res += doubleArrayOf(fr.t, ang, cx, cz)
                if (step == "4") println("${f(fr.t, 1)} s · angolo ${f(ang, 2)}° · spigolo (${f(cx, 2)}, ${f(cz, 2)}) · foto ${photo(fr.t)}")
            }
            if (step == "5") {
                // Tratti identificati (dall'ordine e dalle foto), da SAGOMA_SEGMENTS = "C12:t0-t1;C34:t0-t1;C41:t0-t1".
                val segs = System.getenv("SAGOMA_SEGMENTS")!!.split(";").map { it.split(":") }.map { (n, r) -> n to r.split("-").map { it.toDouble() } }
                val tape = mapOf("C12" to 89.40, "C34" to 91.52, "C41" to 88.93)
                val corners = LinkedHashMap<String, DoubleArray>()
                for ((n, r) in segs) {
                    val v = res.filter { it[0] >= r[0] && it[0] <= r[1] }
                    if (v.size < 5) { println("$n: ${v.size} immagini, non valido"); continue }
                    fun med(k: Int) = v.map { it[k] }.sorted()[v.size / 2]
                    val a = v.map { it[1] }.sorted()
                    corners[n] = doubleArrayOf(med(1), med(2), med(3))
                    println("$n: ${v.size} immagini · angolo mediano ${f(med(1), 2)}° (metro ${f(tape.getValue(n), 2)}°, scarto ${f(med(1) - tape.getValue(n), 2)}°) · interquartile ${f(a[(a.size * 3) / 4] - a[a.size / 4], 2)}° · spigolo (${f(med(2), 3)}, ${f(med(3), 3)})")
                }
                val angWorst = corners.entries.maxOfOrNull { (n, c) -> abs(c[0] - tape.getValue(n)) }
                println("ESITO ANGOLI P0.22: " + when { angWorst == null -> "NON VALUTABILE"; angWorst > 2.5 -> "INSUFFICIENTE"; angWorst > 1.0 -> "INCERTO"; else -> "PROMETTENTE" } + " (scarto massimo ${angWorst?.let { f(it, 2) } ?: "-"}°)")
                val lengths = listOf(Triple("C41", "C12", 3.295), Triple("C34", "C41", 3.090), Triple("C12", "C34", 4.475))
                var lenWorst: Double? = null
                for ((a, b, t) in lengths) {
                    val ca = corners[a] ?: continue; val cb = corners[b] ?: continue
                    val ta = segs.first { it.first == a }.second; val tb = segs.first { it.first == b }.second
                    val lo = minOf(ta[0], tb[0]); val hi = maxOf(ta[1], tb[1])
                    if (breaks.any { s(it) in lo..hi }) { println("$a–$b: interruzione in mezzo, non valida"); continue }
                    val d = sqrt((ca[1] - cb[1]) * (ca[1] - cb[1]) + (ca[2] - cb[2]) * (ca[2] - cb[2]))
                    println("$a–$b: ${f(d * 100, 1)} cm (metro ${f(t * 100, 1)} cm, scarto ${f((d - t) * 100, 1)} cm)")
                    lenWorst = maxOf(lenWorst ?: 0.0, abs(d - t))
                }
                println("ESITO LUNGHEZZE P0.22: " + when { lenWorst == null -> "NON VALUTABILE"; lenWorst!! > 0.10 -> "INSUFFICIENTE"; lenWorst!! > 0.05 -> "INCERTO"; else -> "PROMETTENTE" } + " (scarto massimo ${lenWorst?.let { f(it * 100, 1) } ?: "-"} cm)")
            }
            return
        }
        if (step == "3") {
            // ESPLORATIVA (dichiarata dopo P0.21): piani rifatti escludendo i punti vicini allo spigolo (20, 40, 60 cm in pianta).
            for (r in listOf(0.2, 0.4, 0.6)) {
                val ang = ArrayList<Pair<Double, Double>>()
                for (fr in frames) {
                    val p = fr.pts
                    val rnd2 = Random(20261014 + p.frameId)
                    val idx = (0 until p.size).filter { i -> p.confidence!![i] >= minConf && p.rangeM!![i] <= 3.0 && p.y[i] >= p.camY - 1.0 && p.y[i] <= p.camY + 0.9 }.toIntArray()
                    val a = ransacWall(p, idx, rnd2, 1500) ?: continue
                    val used = a.inl.toHashSet()
                    val b = ransacWall(p, idx.filter { it !in used }.toIntArray(), rnd2, 1500) ?: continue
                    val between = Math.toDegrees(acos((a.nx * b.nx + a.nz * b.nz).coerceIn(-1.0, 1.0)))
                    if (between < 60 || between > 120) continue
                    val det = a.nx * b.nz - a.nz * b.nx
                    val px = (a.c * b.nz - a.nz * b.c) / det; val pz = (a.nx * b.c - a.c * b.nx) / det
                    fun refit(w: Wall): Pair<Double, Double>? {
                        val keep = w.inl.filter { i -> sqrt((p.x[i] - px) * (p.x[i] - px) + (p.z[i] - pz) * (p.z[i] - pz)) >= r }
                        if (keep.size < 800) return null
                        val q = HybridMath.fitPlane(DoubleArray(keep.size) { p.x[keep[it]] }, DoubleArray(keep.size) { p.y[keep[it]] }, DoubleArray(keep.size) { p.z[keep[it]] }, IntArray(keep.size)) ?: return null
                        val h = sqrt(q.nx * q.nx + q.nz * q.nz)
                        var nx = q.nx / h; var nz = q.nz / h
                        if (nx * (p.camX - q.cx) + nz * (p.camZ - q.cz) < 0) { nx = -nx; nz = -nz }
                        return nx to nz
                    }
                    val na = refit(a) ?: continue; val nb = refit(b) ?: continue
                    ang += fr.t to 180.0 - Math.toDegrees(acos((na.first * nb.first + na.second * nb.second).coerceIn(-1.0, 1.0)))
                }
                fun med(v: List<Double>) = if (v.isEmpty()) Double.NaN else v.sorted()[v.size / 2]
                val g1 = ang.filter { it.first in 60.0..90.0 }.map { it.second }; val g2 = ang.filter { it.first in 105.0..140.0 }.map { it.second }
                println("ESPLORATIVA esclusi ${f(r * 100, 0)} cm dallo spigolo: spigolo mensola ${f(med(g1), 1)}° (${g1.size} immagini) · spigolo letto ${f(med(g2), 1)}° (${g2.size} immagini)")
            }
            return
        }
        // ---- Passo 2: errore di sbieco dal pavimento (zero per immagine nella fascia 0–15°), poi Q1 e Q1 corretta ----
        val bins = HashMap<Int, MutableList<Double>>()
        for (fr in frames) {
            val p = fr.pts
            val fl = (0 until p.size).filter { i -> p.confidence!![i] >= 224 && p.rangeM!![i] <= 3.0 && p.y[i] < p.camY - 0.9 }
            if (fl.size < 500) continue
            val inc = fl.associateWith { incidence(p, it, 0.0, 1.0, 0.0) }
            val zeroPts = fl.filter { inc.getValue(it) <= 15.0 }.map { p.y[it] }.sorted()
            if (zeroPts.size < 200) continue
            val zero = zeroPts[zeroPts.size / 2]
            for (i in fl) { val dh = p.y[i] - zero; if (abs(dh) <= 0.15) bins.getOrPut((inc.getValue(i) / 5).toInt()) { ArrayList() } += dh }
        }
        val dhBin = bins.filterValues { it.size >= 500 }.mapValues { (_, v) -> v.sorted()[v.size / 2] }
        println("PAVIMENTO, quota mediana per angolo di vista (zero 0–15°): " + dhBin.toSortedMap().entries.joinToString(" · ") { (b, v) -> "${b * 5}–${b * 5 + 5}° ${f(v * 100, 1)} cm (${bins.getValue(b).size})" })
        // e(θ) = −Δh / cos θ: errore in distanza (positivo = misurato più lontano). Fuori dalle fasce con dati: la fascia più vicina.
        val e: (Double) -> Double = { th ->
            val b = (th / 5).toInt()
            if (th <= 15.0 || dhBin.isEmpty()) 0.0 else {
                val key = dhBin.keys.minByOrNull { abs(it - b) }!!
                -dhBin.getValue(key) / cos(Math.toRadians(th))
            }
        }
        for ((label, corr) in listOf("Q1" to null, "Q1 corretta" to e)) {
            val views = ArrayList<Pair<Frame, DoubleArray>>()
            for (fr in frames) corner(fr.pts, Random(20261014 + fr.pts.frameId), corr)?.let { views += fr to it }
            // Gruppi come P0.20: stesso tratto senza interruzioni, intersezione entro 50 cm.
            val groups = ArrayList<MutableList<Int>>()
            fun seg(ns: Long) = breaks.count { it <= ns }
            for (i in views.indices) {
                val g = groups.lastOrNull { gr -> seg(views[gr.first()].first.ns) == seg(views[i].first.ns) && run {
                    val mx = gr.map { views[it].second[3] }.sorted()[gr.size / 2]; val mz = gr.map { views[it].second[4] }.sorted()[gr.size / 2]
                    sqrt((views[i].second[3] - mx) * (views[i].second[3] - mx) + (views[i].second[4] - mz) * (views[i].second[4] - mz)) <= 0.5
                } }
                if (g != null) g += i else groups += mutableListOf(i)
            }
            println("== $label: immagini con due pareti ${views.size}")
            val meds = ArrayList<Double>()
            for (g in groups.filter { it.size >= 5 }) {
                val a = g.map { views[it].second[0] }.sorted()
                val med = a[a.size / 2]; meds += med
                println("   ${f(views[g.first()].first.t, 0)}–${f(views[g.last()].first.t, 0)} s · immagini ${g.size} · angolo mediano ${f(med, 2)}° · interquartile ${f(a[(a.size * 3) / 4] - a[a.size / 4], 2)}° · foto ${photo(views[g.first()].first.t)}")
                // Errore in funzione della vista più di sbieco tra le due pareti.
                val byInc = g.groupBy { (maxOf(views[it].second[1], views[it].second[2]) / 10).toInt() * 10 }
                println("      per vista più di sbieco: " + byInc.toSortedMap().entries.joinToString(" · ") { (k, v) -> "$k–${k + 10}° ${f(v.map { views[it].second[0] }.sorted()[v.size / 2], 1)}° (${v.size})" })
            }
            val worst = meds.maxOfOrNull { m -> if (m in 88.93..91.52) 0.0 else minOf(abs(m - 88.93), abs(m - 91.52)) }
            val verdict = when { meds.isEmpty() -> "NON VALUTABILE"; worst!! > 2.5 -> "INSUFFICIENTE"; worst > 1.0 -> "INCERTO"; else -> "PROMETTENTE" }
            println("ESITO $label: $verdict (scarto massimo dall'intervallo del metro ${worst?.let { f(it, 1) } ?: "-"}°)")
        }
    }
}
