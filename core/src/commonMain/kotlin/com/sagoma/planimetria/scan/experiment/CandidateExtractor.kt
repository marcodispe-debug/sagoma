package com.sagoma.planimetria.scan.experiment

import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalysis
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Dalla registrazione (e dalla sua analisi) ai dati dell'esperimento: una candidata per ogni piano verticale visto (tutti, anche
 * i più effimeri: la selezione la fa l'esperimento con regole geometriche, non questa classe), la traiettoria della camera e i
 * punti della nuvola (ultima posizione di ogni id, con la confidenza minima richiesta).
 */
object CandidateExtractor {
    /** Distanza massima (m) di un punto dalla retta per contarlo come supporto della candidata. */
    const val SUPPORT_TOLERANCE_M = 0.10
    /** Quanto oltre gli estremi (m) si cercano punti di supporto. */
    const val SUPPORT_MARGIN_M = 0.20

    fun extract(recording: ScanRecording, analysis: RecordingAnalysis, minPointConfidence: Double = PerimeterParams().minPointConfidence): ExperimentInput {
        // Punti: l'ultima posizione conosciuta di ogni identificatore persistente.
        val latest = LinkedHashMap<Int, DoubleArray>()
        for (f in recording.frames) {
            val pc = f.points ?: continue
            if (!pc.isConsistent) continue
            for (i in 0 until pc.count) if (pc.confidence[i] >= minPointConfidence) latest[pc.ids[i]] = doubleArrayOf(pc.xyz[3 * i], pc.xyz[3 * i + 1], pc.xyz[3 * i + 2])
        }
        val pts = latest.entries.sortedBy { it.key }.map { it.value }

        val candidates = analysis.verticalPlanes.filter { it.last != null }.map { t ->
            val g = t.last!!
            val ax = g.a.x; val az = g.a.z; val bx = g.b.x; val bz = g.b.z
            val len = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
            var support = 0
            var sq = 0.0
            if (len > 1e-6) {
                val dx = (bx - ax) / len; val dz = (bz - az) / len
                for (p in pts) {
                    val rx = p[0] - ax; val rz = p[2] - az
                    val along = rx * dx + rz * dz
                    val perp = abs(rx * dz - rz * dx)
                    if (perp <= SUPPORT_TOLERANCE_M && along >= -SUPPORT_MARGIN_M && along <= len + SUPPORT_MARGIN_M &&
                        p[1] >= g.minY - SUPPORT_TOLERANCE_M && p[1] <= g.maxY + SUPPORT_TOLERANCE_M
                    ) { support++; sq += perp * perp }
                }
            }
            WallCandidate(
                id = t.key, ax = ax, az = az, bx = bx, bz = bz, firstMs = t.firstMs, lastMs = t.lastMs, observedFrames = t.observedFrames,
                trackingFraction = if (t.observedFrames > 0) t.trackingFrames.toDouble() / t.observedFrames else 0.0,
                minY = g.minY, maxY = g.maxY, pointSupport = support, rmsM = if (support > 0) sqrt(sq / support) else null,
                subsumed = t.subsumedBy != null, planeKeys = listOf(t.key),
            )
        }
        val trajectory = analysis.trajectory.filter { it.tracking == "TRACKING" }.map { ArXZ(it.x, it.z) }
        return ExperimentInput(candidates, trajectory, pts.map { ArXZ(it[0], it[2]) })
    }
}
