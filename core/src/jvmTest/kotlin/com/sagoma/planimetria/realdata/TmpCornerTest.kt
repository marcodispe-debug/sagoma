package com.sagoma.planimetria.realdata

import com.sagoma.planimetria.scan.hybrid.DepthPoints
import com.sagoma.planimetria.scan.hybrid.HybridMath
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.StreamStatus
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test

/**
 * TEMPORANEO (non va nel repository): verifica P0.20. L'angolo tra due pareti adiacenti preso da UNA sola immagine di profondità
 * (entrambe inquadrate) è giusto? Regole fissate nel protocollo prima dell'esecuzione.
 */
class TmpCornerTest {
    private fun f(v: Double, d: Int = 1) = "%.${d}f".format(v)

    private class Wall(val nx: Double, val nz: Double, val c: Double, val points: Int)
    private class View(val t: Double, val angle: Double, val px: Double, val pz: Double, val a: Int, val b: Int)

    /** Piano verticale con più punti entro 2 cm (RANSAC in pianta su un sottoinsieme), raffinato; null se non verticale o pochi punti. */
    private fun findWall(x: DoubleArray, y: DoubleArray, z: DoubleArray, idx: IntArray, rnd: Random): Pair<Wall, IntArray>? {
        if (idx.size < 1500) return null
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
        if (inl.size < 1500) return null
        val p = HybridMath.fitPlane(DoubleArray(inl.size) { x[inl[it]] }, DoubleArray(inl.size) { y[inl[it]] }, DoubleArray(inl.size) { z[inl[it]] }, IntArray(inl.size)) ?: return null
        val h = sqrt(p.nx * p.nx + p.nz * p.nz)
        if (abs(p.ny) > kotlin.math.sin(Math.toRadians(10.0))) return null
        val nx = p.nx / h; val nz = p.nz / h
        return Wall(nx, nz, nx * p.cx + nz * p.cz, inl.size) to inl
    }

    @Test
    fun corners() {
        val dirs = System.getenv("SAGOMA_CORNER_DIRS")?.split(";")?.filter { it.isNotBlank() } ?: return
        val all = ArrayList<Pair<String, Double>>()
        for (dir in dirs) {
            val files = CaptureDatasetFiles.open(File(dir))
            val rec = files.recording
            val depth = rec.depth.filter { it.rawStatus == StreamStatus.SAVED && it.rawPath != null && it.camera != null && it.intrinsics != null }.sortedBy { it.frameTimestampNs }
            val poses = rec.poses.sortedBy { it.timestampNs }
            val t0 = poses.first().timestampNs
            // Interruzioni: posizione persa o salto oltre 10 cm tra pose consecutive (istanti in ns).
            val breaks = ArrayList<Long>()
            for (i in 1 until poses.size) {
                val a = poses[i - 1]; val b = poses[i]
                if (b.tracking != "TRACKING" || a.tracking != "TRACKING") { breaks += b.timestampNs; continue }
                val ca = a.camera ?: continue; val cb = b.camera ?: continue
                val d = sqrt((ca.x - cb.x) * (ca.x - cb.x) + (ca.y - cb.y) * (ca.y - cb.y) + (ca.z - cb.z) * (ca.z - cb.z))
                if (d > 0.10) breaks += b.timestampNs
            }
            val rnd = Random(20261014)
            val views = ArrayList<View>()
            var frameNo = 0
            val segOf = HashMap<Int, Int>()
            for (dp in depth) {
                val w = dp.rawWidth ?: dp.width; val h = dp.rawHeight ?: dp.height
                val raw = files.read(dp.rawPath!!) ?: continue
                if (raw.size != w * h * 2) continue
                val k0 = dp.intrinsics!!
                val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
                val conf = dp.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h } ?: continue
                val pts = DepthPoints.fromDepth(dp.seq, dp.frameTimestampNs / 1_000_000, DepthRaw.decode(raw, w, h), conf, k, dp.camera!!)
                val idx = (0 until pts.size).filter { i ->
                    pts.confidence!![i] >= 224 && pts.rangeM!![i] <= 3.0 && pts.y[i] >= pts.camY - 1.0 && pts.y[i] <= pts.camY + 0.9
                }.toIntArray()
                val first = findWall(pts.x, pts.y, pts.z, idx, rnd) ?: continue
                val used = first.second.toHashSet()
                val rest = idx.filter { it !in used }.toIntArray()
                val second = findWall(pts.x, pts.y, pts.z, rest, rnd) ?: continue
                // Normali verso la camera.
                fun toward(wl: Wall): Pair<Double, Double> = if (wl.nx * pts.camX + wl.nz * pts.camZ - wl.c >= 0) wl.nx to wl.nz else -wl.nx to -wl.nz
                val (n1x, n1z) = toward(first.first); val (n2x, n2z) = toward(second.first)
                val between = Math.toDegrees(acos((n1x * n2x + n1z * n2z).coerceIn(-1.0, 1.0)))
                if (between < 60 || between > 120) continue
                val interior = 180.0 - between
                // Intersezione in pianta.
                val a1 = first.first; val a2 = second.first
                val det = a1.nx * a2.nz - a1.nz * a2.nx
                if (abs(det) < 1e-6) continue
                val px = (a1.c * a2.nz - a1.nz * a2.c) / det; val pz = (a1.nx * a2.c - a1.c * a2.nx) / det
                val seg = breaks.count { it <= dp.frameTimestampNs }
                segOf[views.size] = seg
                views += View((dp.frameTimestampNs - t0) / 1e9, interior, px, pz, first.first.points, second.first.points)
                frameNo++
            }
            // Gruppi: stesso tratto senza interruzioni, intersezione entro 50 cm dalla mediana del gruppo.
            val groups = ArrayList<MutableList<Int>>()
            for (i in views.indices) {
                val g = groups.lastOrNull { gr -> segOf[gr.first()] == segOf[i] && run {
                    val mx = gr.map { views[it].px }.sorted()[gr.size / 2]; val mz = gr.map { views[it].pz }.sorted()[gr.size / 2]
                    sqrt((views[i].px - mx) * (views[i].px - mx) + (views[i].pz - mz) * (views[i].pz - mz)) <= 0.5
                } }
                if (g != null) g += i else groups += mutableListOf(i)
            }
            val kept = groups.filter { it.size >= 5 }
            val name = File(dir).name
            println("=== $name: immagini di profondità ${depth.size}, con due pareti ${views.size}, gruppi con almeno 5 immagini ${kept.size}")
            for (g in kept) {
                val a = g.map { views[it].angle }.sorted()
                val med = a[a.size / 2]; val iqr = a[(a.size * 3) / 4] - a[a.size / 4]
                val v0 = views[g.first()]; val v1 = views[g.last()]
                println("   ${f(v0.t, 0)}–${f(v1.t, 0)} s · immagini ${g.size} · angolo mediano ${f(med, 2)}° · scarto interquartile ${f(iqr, 2)}° · min ${f(a.first(), 1)} max ${f(a.last(), 1)}")
                all += name to med
            }
        }
        val lo = all.count { it.second < 87.9 || it.second > 92.5 }
        val bad = all.count { it.second < 86.4 || it.second > 94.0 }
        val verdict = when { all.isEmpty() -> "NESSUN GRUPPO"; bad > 0 -> "INSUFFICIENTE"; lo > 0 -> "INCERTO"; else -> "PROMETTENTE" }
        println("ESITO P0.20: $verdict · gruppi ${all.size} · fuori 87,9–92,5°: $lo · fuori 86,4–94,0°: $bad")
        println("mediane: " + all.joinToString(" · ") { "${it.first.takeLast(6)} ${f(it.second, 1)}°" })
    }
}
