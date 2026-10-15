package com.sagoma.planimetria.realdata

import com.sagoma.planimetria.scan.recording.ArCameraProjection
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.StreamStatus
import com.sagoma.planimetria.scan.reconstruction.DepthFrame
import com.sagoma.planimetria.scan.reconstruction.DepthToWorld
import com.sagoma.planimetria.scan.reconstruction.DepthUse
import com.sagoma.planimetria.scan.reconstruction.ReconParams
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * TEMPORANEO (esplorativo, non va nel repository): quali punti di profondità sono più giusti e perché. Banco di prova: superfici
 * confermate della prova del prototipo (pavimento, soffitto, parete della porta). Scarto = distanza con segno dal piano del
 * riferimento; per gruppi di distanza, confidenza, angolo di vista e posizione nell'immagine: quanti punti, scarto tipico (mediana)
 * e dispersione (MAD × 1,4826), quota entro ±1 e ±2 cm.
 */
class TmpPointQualityTest {
    private fun f(v: Double, d: Int = 1) = "%.${d}f".format(v)

    private class Acc { val r = ArrayList<Double>() }

    private fun summary(v: List<Double>): String {
        if (v.size < 200) return "pochi punti (${v.size})"
        val s = v.sorted(); val med = s[s.size / 2]
        val mad = s.map { abs(it - med) }.sorted()[s.size / 2] * 1.4826
        val in1 = v.count { abs(it) <= 0.01 } * 100.0 / v.size; val in2 = v.count { abs(it) <= 0.02 } * 100.0 / v.size
        return "${v.size} punti · scarto tipico ${f(med * 100)} cm · dispersione ${f(mad * 100)} cm · entro ±1 cm ${f(in1, 0)}% · entro ±2 cm ${f(in2, 0)}%"
    }

    private fun plane(line: String): DoubleArray = Regex("\"plane\":\\[([^]]*)]").find(line)!!.groupValues[1].split(",").map { it.toDouble() }.toDoubleArray()
    private fun ts(line: String) = Regex("\"arTimestampNs\":([0-9]+)").find(line)!!.groupValues[1].toLong()

    @Test
    fun quality() {
        val path = System.getenv("SAGOMA_HYBRID_DIR") ?: return
        val files = CaptureDatasetFiles.open(File(path))
        val ev = files.read("hybrid-events.jsonl")!!.decodeToString().lines().filter { it.contains("\"type\":\"event\"") }
        fun first(kind: String, side: Int? = null, nth: Int = 0) = ev.filter { it.contains("\"kind\":\"$kind\"") && (side == null || it.contains("\"side\":$side,")) }[nth]
        val floorRef = first("FLOOR_REFERENCE"); val floorEnd = first("FLOOR_DONE")
        // Superficie, piano (n, c), intervallo di tempo, distanza massima dal piano per considerare il punto (code comprese).
        val cases = listOf(
            Triple("PAVIMENTO", plane(floorRef), ts(floorRef) to ts(floorEnd)),
            *(if (System.getenv("SAGOMA_ONLY_FLOOR") == "1") emptyArray() else arrayOf(Triple("SOFFITTO", plane(first("CEILING_REFERENCE")), ts(first("CEILING_REFERENCE")) to ts(first("CEILING_DONE"))))),
            *(if (System.getenv("SAGOMA_ONLY_FLOOR") == "1") emptyArray() else arrayOf(Triple("PARETE DELLA PORTA", plane(first("WALL_REFERENCE", 0, 1)), ts(first("WALL_REFERENCE", 0, 1)) to ts(first("WALL_REFERENCE", 1))))),
        )
        val rec = files.recording
        val params = ReconParams()
        for ((name, p, window) in cases) {
            val byRange = sortedMapOf<Int, Acc>(); val byConf = sortedMapOf<Int, Acc>(); val byAngle = sortedMapOf<Int, Acc>(); val byRadius = sortedMapOf<Int, Acc>()
            val best = Acc(); val all = Acc(); val strict = Acc(); val wide = Acc(); val wide45 = Acc()
            val byTime = sortedMapOf<Int, Acc>()
            val cellsStrict = HashSet<Long>(); val cellsWide = HashSet<Long>(); val cellsWide45 = HashSet<Long>(); val cellsAll = HashSet<Long>()
            fun cell(x: Double, z: Double) = (Math.floor(x / 0.1).toLong() shl 32) or (Math.floor(z / 0.1).toLong() and 0xffffffffL)
            for (d in rec.depth) {
                if (d.frameTimestampNs < window.first || d.frameTimestampNs > window.second) continue
                if (d.rawStatus != StreamStatus.SAVED) continue
                val raw = files.read(d.rawPath ?: continue) ?: continue
                val w = d.rawWidth ?: d.width; val h = d.rawHeight ?: d.height
                if (raw.size != w * h * 2) continue
                val k0 = d.intrinsics ?: continue; val k = if (k0.width == w && k0.height == h) k0 else k0.scaledTo(w, h)
                val cam = d.camera ?: continue
                val conf = d.confidencePath?.let { files.read(it) }?.takeIf { it.size == w * h } ?: continue
                DepthToWorld.convert(DepthFrame(d.seq, DepthUse.RAW, d.timestampNs, cam, k, DepthRaw.decode(raw, w, h), conf, d.poseFrameSeq, null), params) { pt ->
                    val r = (pt.x - p[3]) * p[0] + (pt.y - p[4]) * p[1] + (pt.z - p[5]) * p[2]
                    if (abs(r) > 0.25) return@convert
                    val rx = pt.x - cam.x; val ry = pt.y - cam.y; val rz = pt.z - cam.z
                    val rl = sqrt(rx * rx + ry * ry + rz * rz)
                    val incidence = Math.toDegrees(acos((abs(rx * p[0] + ry * p[1] + rz * p[2]) / rl).coerceAtMost(1.0))) // 0° = di fronte
                    val c = conf[pt.v * w + pt.u].toInt() and 0xFF
                    val radius = sqrt(((pt.u - w / 2.0) / (w / 2.0)).let { it * it } + ((pt.v - h / 2.0) / (h / 2.0)).let { it * it })
                    all.r += r
                    byRange.getOrPut(minOf((pt.rangeM / 0.5).toInt(), 7)) { Acc() }.r += r
                    byConf.getOrPut(minOf(c / 32, 7)) { Acc() }.r += r
                    byAngle.getOrPut(minOf((incidence / 15).toInt(), 5)) { Acc() }.r += r
                    byRadius.getOrPut(minOf((radius / 0.25).toInt(), 5)) { Acc() }.r += r
                    if (pt.rangeM <= 2.0 && incidence <= 45 && c >= 128) best.r += r
                    if (pt.rangeM <= 2.0 && incidence <= 30 && c >= 224 && radius <= 0.75) { strict.r += r; if (abs(r) <= 0.08) cellsStrict += cell(pt.x, pt.z) }
                    if (pt.rangeM <= 2.0 && incidence <= 30 && c >= 224) { wide.r += r; if (abs(r) <= 0.08) cellsWide += cell(pt.x, pt.z) }
                    if (pt.rangeM <= 2.5 && incidence <= 45 && c >= 224) { wide45.r += r; if (abs(r) <= 0.08) cellsWide45 += cell(pt.x, pt.z) }
                    if (abs(r) <= 0.08) cellsAll += cell(pt.x, pt.z)
                    if (pt.rangeM <= 2.0 && incidence <= 30 && c >= 224) byTime.getOrPut(((d.frameTimestampNs - window.first) / 10_000_000_000L).toInt()) { Acc() }.r += r
                }
            }
            println("=== $name (punti entro 25 cm dal piano confermato)")
            println("   tutti: ${summary(all.r)}")
            for ((b, a) in byRange) println("   distanza ${f(b * 0.5)}–${f(b * 0.5 + 0.5)} m${if (b == 7) "+" else ""}: ${summary(a.r)}")
            for ((b, a) in byConf) println("   confidenza ${b * 32}–${b * 32 + 31}: ${summary(a.r)}")
            for ((b, a) in byAngle) println("   angolo di vista ${b * 15}–${b * 15 + 15}° (0 = di fronte): ${summary(a.r)}")
            for ((b, a) in byRadius) println("   posizione nell'immagine ${f(b * 0.25, 2)}–${f(b * 0.25 + 0.25, 2)} dal centro: ${summary(a.r)}")
            println("   SELEZIONE distanza ≤ 2 m, angolo ≤ 45°, confidenza ≥ 128: ${summary(best.r)}")
            println("   SELEZIONE STRETTA distanza ≤ 2 m, angolo ≤ 30°, confidenza ≥ 224, centro (≤ 0,75): ${summary(strict.r)}")
            println("   SELEZIONE TUTTA L'IMMAGINE distanza ≤ 2 m, angolo ≤ 30°, confidenza ≥ 224: ${summary(wide.r)}")
            println("   SELEZIONE LARGA distanza ≤ 2,5 m, angolo ≤ 45°, confidenza ≥ 224: ${summary(wide45.r)}")
            for ((b, a) in byTime) println("   tempo dalla conferma ${b * 10}–${b * 10 + 10} s (punti buoni): ${summary(a.r)}")
            println("   AREA COPERTA (celle 10 cm entro ±8 cm): stretta ${f(cellsStrict.size * 0.01)} m² · tutta l'immagine ${f(cellsWide.size * 0.01)} m² · larga ${f(cellsWide45.size * 0.01)} m² · tutti i punti ${f(cellsAll.size * 0.01)} m²")
        }
    }
}
