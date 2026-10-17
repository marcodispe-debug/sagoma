package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.scan.ArXZ
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin

/**
 * Messa in squadra degli angoli, SOLO per scelta esplicita dell'utente ([Answer.SQUARE_ANGLES]). Dagli angoli non si distingue
 * l'errore della scansione da una stanza davvero fuori squadra (lo sa solo l'utente), e lo stesso angolo pesa in proporzione alla
 * stanza: per questo NON c'è un limite in gradi. La domanda compare per ogni schizzo ad angoli retti e dice di quanto cambia la
 * pianta (spostamento massimo degli angoli tra le due versioni); si omette solo quando le due piante coincidono al centimetro.
 *
 * Come si mette in squadra: un orientamento comune per tutte le pareti confermate (media sulla lunghezza vista delle direzioni,
 * modulo 90°); ogni parete ruota attorno al centro della sua parte vista fino alla direzione più vicina tra quelle a 90°.
 * Le posizioni delle pareti restano quelle misurate; gli angoli poi li calcola il nucleo come sempre.
 */
object SketchSquaring {
    /**
     * La proposta: [maxAngleDeviationDeg] = angolo misurato più lontano dal suo angolo retto; [maxShiftM] = spostamento massimo di
     * un angolo della pianta tra le due versioni; [squared] = la pianta in squadra (calcolata dal nucleo).
     */
    data class Offer(val maxAngleDeviationDeg: Double, val maxShiftM: Double, val squared: SketchGeometry)

    /**
     * La proposta, oppure null: schizzo non ad angoli retti, meno di due pareti confermate, una delle due piante non calcolabile,
     * oppure piante uguali entro [SketchThresholds.squareMinShiftM].
     */
    fun offer(input: SketchGeometryInput, scan: SketchGeometry): Offer? {
        val th = input.thresholds
        val sketch = input.sketch
        val scanCorners = scan.corners ?: return null
        if (input.confirmed.size < 2) return null
        val n = sketch.sideCount
        if (scan.cornerResults.size != n) return null
        val orient = sign(sketch.signedArea)
        var worst = 0.0
        for (k in 0 until n) {
            val sk = sketchAngle(sketch.corners, k, orient)
            val right = if (abs(sk - 90.0) <= th.squareSketchTolDeg) 90.0 else if (abs(sk - 270.0) <= th.squareSketchTolDeg) 270.0 else return null
            worst = maxOf(worst, abs(scan.cornerResults[k].interiorAngleDeg - right))
        }
        val squared = SketchSolver.solve(input.copy(confirmed = squaredLines(input.confirmed)))
        val squaredCorners = squared.corners?.takeIf { it.size == scanCorners.size } ?: return null
        val shift = scanCorners.indices.maxOf { scanCorners[it].distanceTo(squaredCorners[it]) }
        if (shift < th.squareMinShiftM) return null
        return Offer(worst, shift, squared)
    }

    /** Le pareti confermate messe in squadra (stessa parte vista, stesso lato interno, ruotate attorno al centro). */
    fun squaredLines(lines: Map<Int, ConfirmedLine>): Map<Int, ConfirmedLine> {
        if (lines.isEmpty()) return lines
        // Media delle direzioni modulo 90°: si moltiplicano gli angoli per 4 (direzioni a 90° l'una dall'altra coincidono).
        var sx = 0.0; var sy = 0.0
        for (l in lines.values) {
            val d = (l.b - l.a).normalized()
            val a4 = 4 * atan2(d.z, d.x)
            sx += l.observedLengthM * cos(a4); sy += l.observedLengthM * sin(a4)
        }
        val theta = atan2(sy, sx) / 4
        return lines.mapValues { (_, l) ->
            val d = (l.b - l.a).normalized()
            val k = round((atan2(d.z, d.x) - theta) / (PI / 2))
            val phi = theta + k * PI / 2
            val nd = ArXZ(cos(phi), sin(phi))
            val mid = (l.a + l.b) * 0.5
            val half = l.observedLengthM / 2
            // I punti di sostegno sono distanze lungo la retta da `a`: la rotazione attorno al centro le lascia invariate.
            l.copy(a = mid - nd * half, b = mid + nd * half)
        }
    }

    /** Angolo interno dello schizzo all'angolo k (tra il lato k − 1 e il lato k), come lo calcola il nucleo. */
    private fun sketchAngle(c: List<ArXZ>, k: Int, orient: Double): Double {
        val n = c.size
        val e1 = c[k] - c[(k - 1 + n) % n]
        val e2 = c[(k + 1) % n] - c[k]
        return 180.0 - orient * toDegrees(atan2(e1.cross(e2), e1 dot e2))
    }
}
