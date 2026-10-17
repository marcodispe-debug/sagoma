package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalyzer
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * Diagnosi su una registrazione reale (solo con SAGOMA_RECON_ZIP=<file.zip>): quanto sono "spessi" i punti della mappa attorno ai
 * piani verticali di ARCore, con la raw (con e senza filtro di confidenza) e con la depth filtrata. Non promuove né boccia: stampa.
 */
class ReconRealDataDiagnosticTest {
    @Test
    fun `superfici con parametri diversi`() {
        val path = System.getenv("SAGOMA_RECON_ZIP") ?: return
        val ds = CaptureDatasetFiles.open(File(path))
        val r = ds.recording
        val map = GlobalMap.build(r, { ds.read(it) }, ReconParams())
        for ((label, sp) in listOf(
            "predefiniti (3 cm, 15°, vicinato 12 cm)" to SurfaceParams(),
            "adatti al rumore (8 cm, 25°, vicinato 20 cm)" to SurfaceParams(normalRadius = 2, seedMaxVariation = 0.06, growMaxVariation = 0.15, growAngleDeg = 25.0, growMinDistM = 0.08, mergeAngleDeg = 10.0, mergeDistM = 0.08),
            "adatti al rumore (12 cm, 30°, vicinato 20 cm)" to SurfaceParams(normalRadius = 2, seedMaxVariation = 0.08, growMaxVariation = 0.2, growAngleDeg = 30.0, growMinDistM = 0.12, mergeAngleDeg = 12.0, mergeDistM = 0.12),
        )) {
            val s = SurfaceExtractor.extract(map, r, sp)
            println("== $label: ${s.surfaces.size} superfici · punti assegnati ${"%.1f".format(s.pointsAssigned * 100.0 / s.pointsTotal)}% · " +
                SurfaceKind.entries.joinToString(" ") { k -> "$k ${s.surfaces.count { it.kind == k }}" })
            for (sf in s.surfaces.filter { it.areaM2 >= 0.3 }.sortedByDescending { it.areaM2 }.take(14)) println(
                "   S${sf.id} ${sf.kind} ${sf.orientation} area ${"%.2f".format(sf.areaM2)} lungh ${"%.2f".format(sf.lengthM)} dal pav ${sf.bottomAboveFloor?.let { "%.2f".format(it) }}..${sf.topAboveFloor?.let { "%.2f".format(it) }} RMS ${"%.1f".format(sf.rmsM * 100)} cm viste ${sf.effectiveFrames} ARCore ${sf.arcorePlane ?: "-"} score ${sf.structuralScore?.let { "%.2f".format(it) } ?: "-"}",
            )
            println("   ARCore compatibili: ${s.arcore.count { it.surface != null }}/${s.arcore.size}")
        }
    }

    @Test
    fun `spessore dei punti attorno ai piani verticali ARCore`() {
        val path = System.getenv("SAGOMA_RECON_ZIP") ?: return
        val ds = CaptureDatasetFiles.open(File(path))
        val r = ds.recording
        val blobs = DatasetBlobs { ds.read(it) }
        val tracks = RecordingAnalyzer.analyze(r).verticalPlanes.filter { it.last != null && it.subsumedBy == null && it.last!!.lengthM >= 0.7 }
        val floorY = r.frames.mapNotNull { it.floorY }.sorted().let { it[it.size / 2] }
        for ((label, p) in listOf(
            "raw + confidenza (normale)" to ReconParams(),
            "raw senza filtro di confidenza" to ReconParams(diagnosticIgnoreConfidence = true),
            "solo depth filtrata" to ReconParams(diagnosticFilteredOnly = true),
        )) {
            val map = GlobalMap.build(r, blobs, p)
            println("== $label: ${map.points.size} punti")
            for (t in tracks) {
                val g = t.last!!
                val dx = g.b.x - g.a.x; val dz = g.b.z - g.a.z
                val len = sqrt(dx * dx + dz * dz)
                val ux = dx / len; val uz = dz / len
                val nx = -uz; val nz = ux
                val d = mutableListOf<Double>()
                for (i in 0 until map.points.size) {
                    val y = map.points.y[i] - floorY
                    if (y < 0.3 || y > 2.0) continue
                    val px = map.points.x[i] - g.a.x; val pz = map.points.z[i] - g.a.z
                    val along = px * ux + pz * uz
                    if (along < 0 || along > len) continue
                    val off = px * nx + pz * nz
                    if (abs(off) <= 0.5) d.add(off)
                }
                if (d.isEmpty()) { println("  A${t.key} (${"%.2f".format(len)} m, ${t.persistenceMs / 1000} s): nessun punto entro 50 cm"); continue }
                val s = d.sorted()
                val med = s[s.size / 2]
                val mad = s.map { abs(it - med) }.sorted()[s.size / 2]
                val in3 = s.count { abs(it - med) <= 0.03 } * 100.0 / s.size
                val in10 = s.count { abs(it - med) <= 0.10 } * 100.0 / s.size
                val hist = (-5..4).joinToString(" ") { b -> s.count { it >= b * 0.1 && it < (b + 1) * 0.1 }.toString() }
                println("  A${t.key} (${"%.2f".format(len)} m, ${t.persistenceMs / 1000} s): ${s.size} punti · mediana ${"%+.1f".format(med * 100)} cm · MAD ${"%.1f".format(mad * 100)} cm · entro ±3 cm ${"%.0f".format(in3)}% · ±10 cm ${"%.0f".format(in10)}% · istogramma −50..+50 cm (10 cm): $hist")
                // Per frame: dove sta la superficie in ogni frame (mediana) e quanto è spessa dentro il frame (MAD).
                val perFrame = HashMap<Int, MutableList<Double>>()
                for (i in 0 until map.points.size) {
                    val y = map.points.y[i] - floorY
                    if (y < 0.3 || y > 2.0) continue
                    val px = map.points.x[i] - g.a.x; val pz = map.points.z[i] - g.a.z
                    val along = px * ux + pz * uz
                    if (along < 0 || along > len) continue
                    val off = px * nx + pz * nz
                    if (abs(off - med) <= 0.25) perFrame.getOrPut(map.points.frame[i]) { mutableListOf() }.add(off)
                }
                val stats = perFrame.filterValues { it.size >= 200 }.map { (_, v) ->
                    val q = v.sorted(); val m = q[q.size / 2]
                    m to q.map { abs(it - m) }.sorted()[q.size / 2]
                }
                if (stats.size >= 3) {
                    val meds = stats.map { it.first }.sorted(); val mads = stats.map { it.second }.sorted()
                    val mm = meds[meds.size / 2]
                    val spread = meds.map { abs(it - mm) }.sorted()[meds.size / 2]
                    println("     per frame (${stats.size} frame con ≥200 punti): MAD dentro il frame mediana ${"%.1f".format(mads[mads.size / 2] * 100)} cm · posizione della superficie tra frame: MAD ${"%.1f".format(spread * 100)} cm, da ${"%+.1f".format(meds.first() * 100)} a ${"%+.1f".format(meds.last() * 100)} cm")
                }
            }
        }
    }
}
