package com.sagoma.planimetria.realdata

import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.RecPose
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * TEMPORANEO (esplorativo): i salti di ARCore durante il tracciamento sono correzioni del mondo? Se il telefono non si muove in
 * 33 ms, la differenza tra la posa prima e dopo il salto è la correzione: applicandola, la stessa parete confermata due volte deve
 * tornare con la stessa direzione. Si usa solo la rotazione attorno alla verticale (ARCore tiene la gravità) e lo spostamento.
 */
class TmpRealignTest {
    private fun f(v: Double, d: Int = 1) = "%.${d}f".format(v)

    /** Imbardata (gradi) dell'asse ottico della camera nel piano orizzontale. */
    private fun yaw(p: RecPose): Double {
        // asse −Z della camera ruotato dal quaternione
        val (x, y, z, w) = listOf(p.qx, p.qy, p.qz, p.qw)
        val fx = -(2 * (x * z + w * y)); val fz = -(1 - 2 * (x * x + y * y))
        return Math.toDegrees(atan2(fz, fx))
    }

    /** Rotazione attorno alla verticale (gradi) della rotazione del mondo tra due pose: ΔR = q · p⁻¹ applicata all'asse x. */
    private fun deltaYaw(p: RecPose, q: RecPose): Double {
        // coniugato di p
        val px = -p.qx; val py = -p.qy; val pz = -p.qz; val pw = p.qw
        // d = q * pinv
        val dw = q.qw * pw - q.qx * px - q.qy * py - q.qz * pz
        val dx = q.qw * px + q.qx * pw + q.qy * pz - q.qz * py
        val dy = q.qw * py - q.qx * pz + q.qy * pw + q.qz * px
        val dz = q.qw * pz + q.qx * py - q.qy * px + q.qz * pw
        // ruota (1, 0, 0)
        val vx = 1 - 2 * (dy * dy + dz * dz); val vz = 2 * (dx * dz - dw * dy)
        return Math.toDegrees(atan2(-vz, vx))
    }

    private fun wrap(a: Double): Double { var r = a % 360; if (r > 180) r -= 360; if (r < -180) r += 360; return r }

    @Test
    fun realign() {
        val path = System.getenv("SAGOMA_HYBRID_DIR") ?: return
        val files = CaptureDatasetFiles.open(File(path))
        val ev = files.read("hybrid-events.jsonl")!!.decodeToString().lines().filter { it.contains("\"kind\":\"WALL_REFERENCE\"") && !it.contains("\"plane\":null") }
        fun ts(l: String) = Regex("\"arTimestampNs\":([0-9]+)").find(l)!!.groupValues[1].toLong()
        fun normalDeg(l: String): Double { val p = Regex("\"plane\":\\[([^]]*)]").find(l)!!.groupValues[1].split(",").map { it.toDouble() }; return Math.toDegrees(atan2(p[2], p[0])) }
        val poses = files.recording.poses.filter { it.camera != null }.sortedBy { it.timestampNs }
        val t0 = poses.first().timestampNs
        println("conferme di parete con piano: " + ev.joinToString(" · ") { "${f((ts(it) - t0) / 1e9)} s normale ${f(normalDeg(it))}°" })
        if (ev.size < 2) return
        val a = ev[0]; val b = ev[1]
        // Correzioni fra le due conferme: salti durante il tracciamento (TRACKING → TRACKING) e riprese dopo una pausa.
        var autoYaw = 0.0; var pauseYaw = 0.0; var nAuto = 0; var nPause = 0
        for (i in 1 until poses.size) {
            val p = poses[i - 1]; val q = poses[i]
            if (q.timestampNs <= ts(a) || q.timestampNs > ts(b)) continue
            val pc = p.camera!!; val qc = q.camera!!
            val dy = deltaYaw(pc, qc)
            val dd = sqrt((pc.x - qc.x) * (pc.x - qc.x) + (pc.y - qc.y) * (pc.y - qc.y) + (pc.z - qc.z) * (pc.z - qc.z))
            val dt = (q.timestampNs - p.timestampNs) / 1e9
            val jump = abs(dy) > 10 || dd > 0.10
            if (!jump || dt > 0.2) continue
            if (p.tracking == "TRACKING" && q.tracking == "TRACKING") { autoYaw += dy; nAuto++ }
            else { pauseYaw += dy; nPause++ }
        }
        val diff = wrap(normalDeg(b) - normalDeg(a))
        println("differenza di direzione tra le due conferme della stessa parete: ${f(diff)}°")
        println("rotazioni nei salti durante il tracciamento: $nAuto, somma ${f(autoYaw)}° → differenza residua dopo la correzione automatica: ${f(wrap(diff - autoYaw))}°")
        println("rotazioni nelle riprese dopo una pausa: $nPause, somma ${f(pauseYaw)}° → residua correggendo anche queste: ${f(wrap(diff - autoYaw - pauseYaw))}°")
        // Lo stesso con il segno opposto (convenzione dell'imbardata): per capire quale verso è giusto.
        println("(verso opposto: ${f(wrap(diff + autoYaw))}° e ${f(wrap(diff + autoYaw + pauseYaw))}°)")
        check(acos(0.0) > 0)
        check(cos(0.0) == 1.0 && sin(0.0) == 0.0)
    }
}
