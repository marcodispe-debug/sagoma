package com.sagoma.planimetria.realdata

import com.sagoma.planimetria.scan.assisted.AimFrames
import com.sagoma.planimetria.scan.assisted.AssistedScan
import com.sagoma.planimetria.scan.assisted.CandidateState
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.sketch.ConfirmedLine
import com.sagoma.planimetria.scan.sketch.R3Reference
import com.sagoma.planimetria.scan.sketch.SketchGeometry
import com.sagoma.planimetria.scan.sketch.SketchGeometryInput
import com.sagoma.planimetria.scan.sketch.SketchSolver
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CONCORDANZA CON R3 SULLA STESSA REGISTRAZIONE — NON ACCURATEZZA REALE (comando `:core:realDataQuality`, fuori dalla suite
 * normale). Il riferimento sono i valori FISSI di R3 ([R3Reference]: pipeline R1–R3 sulla profondità di scan4), non una misura
 * col metro: R3 e la scansione vengono dalla stessa sessione, con le stesse pose e le stesse derive. Le pareti sono confermate
 * da un UTENTE SIMULATO (la prima proposta del motore compatibile con una parete R3): non è la capacità di un utente vero.
 * Soglie approvate: lato ≤ 5 cm, diagonale ≤ 5 cm, angolo ≤ 2°. Se fallisce è un limite NON risolto: le soglie non si toccano.
 */
class SketchScan4QualityTest {
    private fun scan4(): File? = File(System.getenv("SAGOMA_SCAN4_ZIP") ?: "../test stanza1/scan4.zip").takeIf { it.isFile }

    private fun f(v: Double?, d: Int = 3) = v?.let { "%.${d}f".format(it) } ?: "-"

    private fun report(g: SketchGeometry) {
        println("Scansione: pianta ${if (g.corners == null) "NON calcolata" else "calcolata"} · confermabile ${g.canConfirm}")
        for (s in g.sides) println("  lato ${s.index}: ${f(s.lengthM)} m fonti ${s.lengthSources} prolungamento ${f(s.prolongationM)}")
        for (c in g.cornerResults) println("  angolo ${c.index}: ${f(c.interiorAngleDeg, 2)} gradi sigma ${f(c.sigmaM)}")
        for (i in g.issues) println("  ${i.severity} ${i.code} lati ${i.sides} ${i.values}")
    }

    @Test
    fun `scan4 concordanza con R3 sulla stessa registrazione entro le soglie approvate (non accuratezza reale)`() {
        println("SIGNIFICATO: concordanza tra scansione guidata e R3 sulla STESSA registrazione; NON accuratezza reale (manca il metro).")
        val zip = scan4()
        if (zip == null) println("SALTATO: scan4 non trovato; impostare SAGOMA_SCAN4_ZIP")
        assumeTrue("scan4 non disponibile", zip != null)

        // Scansione: motore M2.0 frame per frame, utente simulato.
        val frames = AimFrames.from(CaptureDatasetFiles.open(zip!!).recording)
        var s = AssistedScan.initial()
        val confirmed = HashMap<Int, ConfirmedLine>()
        for (fr in frames) {
            s = AssistedScan.step(s, fr).state
            val c = s.candidate ?: continue
            if (c.state != CandidateState.PROPOSED || c.duplicateOf != null) continue
            val hit = R3Reference.WALLS.entries.firstOrNull { (w, seg) ->
                if (R3Reference.SIDE_OF_WALL.getValue(w) in confirmed) return@firstOrNull false
                val d = (seg.second - seg.first).normalized()
                val ang = Math.toDegrees(asin(min(1.0, abs(d.cross(c.line.direction)))))
                val mid = (c.line.a + c.line.b) * 0.5
                ang <= 6.0 && abs((mid - seg.first).cross(d)) <= 0.10
            } ?: continue
            val sigOff = c.offsetStdM
            val sigDir = c.headingStdDeg
            s = AssistedScan.confirm(s, fr.timeMs).state
            val w = s.walls.last()
            // Come l'app (SketchSession): con i punti della nuvola alla conferma.
            confirmed[R3Reference.SIDE_OF_WALL.getValue(hit.key)] = ConfirmedLine(w.line.a, w.line.b, w.normalIn, sigOff, sigDir, supportAlongM = w.supportAlongM)
        }
        println("pareti confermate dall'utente simulato (lati dello schizzo): ${confirmed.keys.sorted()}")
        val g = SketchSolver.solve(SketchGeometryInput(R3Reference.sketch2x2, confirmed))
        report(g)
        val corners = assertNotNull(g.corners, "la pianta della scansione non si calcola: ${g.blocking}")

        // Confronto con i valori FISSI di R3 (non ricalcolati dal nucleo).
        var worstSide = 0.0
        var worstDiag = 0.0
        var worstAngle = 0.0
        for (k in 0 until 4) {
            val d = g.sides[k].lengthM - R3Reference.SIDES_M[k]
            worstSide = max(worstSide, abs(d))
            val a = g.cornerResults[k].interiorAngleDeg - R3Reference.ANGLES_DEG[k]
            worstAngle = max(worstAngle, abs(a))
            println("  lato $k: ${f(g.sides[k].lengthM)} vs R3 ${f(R3Reference.SIDES_M[k])} (diff ${f(d)}) · angolo $k: ${f(g.cornerResults[k].interiorAngleDeg, 2)} vs R3 ${f(R3Reference.ANGLES_DEG[k], 2)} (diff ${f(a, 2)})")
        }
        for (k in 0..1) {
            val dm = corners[k].distanceTo(corners[k + 2])
            worstDiag = max(worstDiag, abs(dm - R3Reference.DIAGONALS_M[k]))
            println("  diagonale $k-${k + 2}: ${f(dm)} vs R3 ${f(R3Reference.DIAGONALS_M[k])} (diff ${f(dm - R3Reference.DIAGONALS_M[k])})")
        }
        println("  perimetro ${f(g.perimeterM)} · area ${f(g.areaM2)} · confermabile dal nucleo: ${g.canConfirm}")
        val failures = listOfNotNull(
            "lato ${f(worstSide)} m > 0,05".takeIf { worstSide > 0.05 },
            "diagonale ${f(worstDiag)} m > 0,05".takeIf { worstDiag > 0.05 },
            "angolo ${f(worstAngle, 2)} gradi > 2".takeIf { worstAngle > 2.0 },
        )
        println("  soglie di concordanza con R3: ${if (failures.isEmpty()) "RISPETTATE" else "NON rispettate: $failures"}")
        assertTrue(failures.isEmpty(), "concordanza con R3 (stessa registrazione, non accuratezza reale): soglie non rispettate: ${failures.joinToString("; ")}")
    }
}
