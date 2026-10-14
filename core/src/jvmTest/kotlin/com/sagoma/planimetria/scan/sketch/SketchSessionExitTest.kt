package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Uscita dalla scansione: la schermata chiede conferma ("Esci" e tasto indietro passano dalla stessa funzione) quando la sessione
 * ha qualcosa che l'utente perderebbe. Annullare l'uscita non deve cambiare niente: qui si verifica che la domanda non tocca la
 * sessione.
 */
class SketchSessionExitTest {
    private val sketch = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)))
    private val line = ConfirmedLine(ArXZ(0.5, 0.0), ArXZ(3.5, 0.0), ArXZ(0.0, 1.0), 0.005, 0.5)

    @Test
    fun `sessione vuota - si esce senza conferma`() {
        assertFalse(SketchSession(sketch).hasUnsavedWork)
    }

    @Test
    fun `qualsiasi dato inserito chiede conferma, non solo le pareti`() {
        val empty = SketchSession(sketch)
        val cases = mapOf(
            "lato di partenza" to empty.chooseStartSide(0),
            "parete confermata" to empty.copy(assigned = mapOf(0 to AssignedWall(1, line, 0, emptyList()))),
            "risposta" to empty.answer("WALL_CONTINUES[0]", Answer.WALL_CONTINUES_BEHIND),
            "misura a mano" to empty.setManualLength(2, 4.0),
            "stima accettata" to empty.acceptSketchLength(2, true),
            "dichiarazione" to empty.declareStart(StartDeclaration(StartReference.DOOR)),
        )
        for ((what, s) in cases) assertTrue(s.hasUnsavedWork, what)
    }

    @Test
    fun `chiedere se c'e qualcosa da perdere non modifica la sessione`() {
        val s = SketchSession(sketch).chooseStartSide(1).setManualLength(2, 3.1).answer("X[0]", Answer.KEEP_MEASURED)
        val copy = s.copy()
        repeat(3) { assertTrue(s.hasUnsavedWork) }
        assertEquals(copy, s)
        assertEquals(copy.result(), s.result())
    }
}
