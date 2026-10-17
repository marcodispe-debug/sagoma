package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifica del CODICE (suite normale): date le rette R3 di scan4, il nucleo deve riprodurre i valori di riferimento FISSI entro
 * 1 mm (lati, diagonali) e 0,01° (angoli). I valori fissi vengono da un calcolo indipendente dal nucleo (incroci di
 * `WallOutline` sulle stesse pareti, analisi del 10/10/2026), quindi un errore del nucleo qui si vede invece di annullarsi.
 * Non dice nulla sull'accuratezza della stanza reale (manca una misura col metro).
 */
class R3ReferenceTest {
    @Test
    fun `il nucleo riproduce i valori fissi di R3 entro 1 mm`() {
        val ref = R3Reference
        val g = SketchSolver.solve(SketchGeometryInput(ref.sketch2x2, ref.lines()))
        val c = assertNotNull(g.corners, "${g.issues}")
        assertTrue(g.canConfirm, "${g.issues}")
        for (k in 0 until 4) {
            assertEquals(ref.SIDES_M[k], g.sides[k].lengthM, 1e-3, "lato $k")
            assertEquals(ref.ANGLES_DEG[k], g.cornerResults[k].interiorAngleDeg, 0.01, "angolo $k")
        }
        assertEquals(ref.DIAGONALS_M[0], c[0].distanceTo(c[2]), 1e-3, "diagonale 0-2")
        assertEquals(ref.DIAGONALS_M[1], c[1].distanceTo(c[3]), 1e-3, "diagonale 1-3")
    }
}

/**
 * Riferimento EMPIRICO R3 di scan4 (pipeline R1–R3 sulla profondità della stessa registrazione). Non è una misura certificata:
 * confrontarsi con R3 misura la CONCORDANZA con R3, non l'accuratezza reale.
 */
object R3Reference {
    /** Pareti R3 (W0, W1, W2, W4 di `risultati4/walls.csv`): estremi nel mondo ARCore. */
    val WALLS: Map<Int, Pair<ArXZ, ArXZ>> = mapOf(
        0 to (ArXZ(-0.7781, -0.9869) to ArXZ(-0.8678, 0.9108)),
        1 to (ArXZ(-0.8666, 0.9241) to ArXZ(1.0288, 1.0442)),
        2 to (ArXZ(1.0571, 0.9836) to ArXZ(1.1601, -0.8633)),
        4 to (ArXZ(0.9016, -1.1175) to ArXZ(-0.4446, -1.1893)),
    )

    /** Lato dello schizzo per ogni parete R3: giro antiorario (x, z) a partire da W0. Fissato, non calcolato. */
    val SIDE_OF_WALL: Map<Int, Int> = mapOf(0 to 0, 4 to 1, 2 to 2, 1 to 3)

    /** Valori fissi, per lato dello schizzo (lato k = parete con SIDE_OF_WALL = k). */
    val SIDES_M = listOf(2.133, 1.944, 2.152, 1.926)

    /** Angolo k tra il lato k−1 e il lato k: W1-W0, W0-W4, W4-W2, W2-W1. */
    val ANGLES_DEG = listOf(90.92, 89.65, 89.86, 89.57)

    /** Diagonali angolo 0 – angolo 2 e angolo 1 – angolo 3. */
    val DIAGONALS_M = listOf(2.877, 2.897)

    val sketch2x2 = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(200.0, 0.0), Vec2(200.0, 200.0), Vec2(0.0, 200.0)))

    private val center = WALLS.values.map { (a, b) -> (a + b) * 0.5 }.reduce { a, b -> a + b } * 0.25

    fun inwardOf(a: ArXZ, b: ArXZ): ArXZ {
        val d = (b - a).normalized()
        val n = ArXZ(-d.z, d.x)
        return if (((center - a) dot n) >= 0) n else n * -1.0
    }

    /** Le rette R3 come pareti confermate (incertezze nominali piccole: servono solo a non far scattare avvisi). */
    fun lines(): Map<Int, ConfirmedLine> =
        WALLS.entries.associate { (w, s) -> SIDE_OF_WALL.getValue(w) to ConfirmedLine(s.first, s.second, inwardOf(s.first, s.second), 0.003, 0.3) }
}
