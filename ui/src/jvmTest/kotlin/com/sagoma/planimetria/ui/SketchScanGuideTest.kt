package com.sagoma.planimetria.ui

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.assisted.CandidateState
import com.sagoma.planimetria.scan.assisted.CandidateWall
import com.sagoma.planimetria.scan.assisted.PointEvidence
import com.sagoma.planimetria.scan.assisted.WallLine
import com.sagoma.planimetria.scan.sketch.Answer
import com.sagoma.planimetria.scan.sketch.AssignedWall
import com.sagoma.planimetria.scan.sketch.ConfirmedLine
import com.sagoma.planimetria.scan.sketch.IssueCode
import com.sagoma.planimetria.scan.sketch.OpeningMark
import com.sagoma.planimetria.scan.sketch.RoomSketch
import com.sagoma.planimetria.scan.sketch.SketchOpening
import com.sagoma.planimetria.scan.sketch.SketchSession
import com.sagoma.planimetria.ui.sketch.AimStatus
import com.sagoma.planimetria.ui.sketch.GuidePhase
import com.sagoma.planimetria.ui.sketch.GuideWindow
import com.sagoma.planimetria.ui.sketch.Turn
import com.sagoma.planimetria.ui.sketch.WindowMemory
import com.sagoma.planimetria.ui.sketch.SketchScanGuide
import com.sagoma.planimetria.ui.sketch.SketchScanTextIt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La procedura guidata della scansione dallo schizzo (SketchScanGuide): in ogni momento fase, parete attiva, progresso, prossima
 * azione e domande da fare. Solo presentazione: la geometria è quella della sessione, invariata.
 */
class SketchScanGuideTest {
    /** Schizzo 4,1 × 2,9 m con la porta sul lato 3 e la finestra sul lato 1. */
    private val sketch = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(410.0, 0.0), Vec2(410.0, 290.0), Vec2(0.0, 290.0)))
    private val fresh = SketchSession(sketch, listOf(SketchOpening(3, OpeningMark.DOOR), SketchOpening(1, OpeningMark.WINDOW)))

    /** Stanza vera 4 × 3 m (stessa orientazione dello schizzo), pareti viste dal 15 all'85%, eventualmente ruotate di qualche grado. */
    private val room = listOf(ArXZ(0.0, 0.0), ArXZ(4.0, 0.0), ArXZ(4.0, 3.0), ArXZ(0.0, 3.0))
    private fun line(k: Int, tiltDeg: Double = 0.0): ConfirmedLine {
        val a0 = room[k]; val b0 = room[(k + 1) % 4]
        val a = a0 + (b0 - a0) * 0.15; val b = a0 + (b0 - a0) * 0.85
        val mid = (a + b) * 0.5; val r = Math.toRadians(tiltDeg)
        fun rot(p: ArXZ) = (p - mid).let { v -> mid + ArXZ(v.x * cos(r) - v.z * sin(r), v.x * sin(r) + v.z * cos(r)) }
        val centre = ArXZ(2.0, 1.5)
        val d = (b - a).normalized(); var inward = ArXZ(-d.z, d.x)
        if (((centre - mid) dot inward) < 0) inward = inward * -1.0
        return ConfirmedLine(rot(a), rot(b), inward, 0.003, 0.3)
    }

    /** Sessione con la partenza sul lato 3 (porta) e le pareti [sides] già acquisite, nell'ordine. */
    private fun withWalls(vararg sides: Int, tilts: Map<Int, Double> = emptyMap()): SketchSession {
        var s = fresh.chooseStartSide(3)
        val assigned = LinkedHashMap<Int, AssignedWall>()
        sides.forEachIndexed { i, k -> assigned[k] = AssignedWall(i + 1, line(k, tilts[k] ?: 0.0), 1000L * (i + 1), emptyList()) }
        val free = (0 until 4).filter { it !in assigned }
        s = s.copy(assigned = assigned, startConfirmed = 3 in assigned, target = if (3 !in assigned) 3 else free.firstOrNull())
        return s
    }

    private fun guide(s: SketchSession, tracking: Boolean = true, review: Boolean = false, applicable: Boolean = true, choosingStart: Boolean = false, candidate: CandidateWall? = s.candidate) =
        SketchScanGuide.of(s, s.result(), tracking, review, applicable, choosingStart, candidate)

    private fun candidate(state: CandidateState, duplicateOf: Int? = null) = CandidateWall(
        state = state, planeKeys = listOf(1), line = WallLine(ArXZ(0.0, 0.0), ArXZ(1.0, 0.0), ArXZ(1.0, 0.0), ArXZ(0.0, 1.0), 0.0, 0.0, 2.5, 0.0),
        aimedPlaneKey = 1, aimDistanceM = 1.5, candidateAgeMs = 2000, aimedMs = 2000, planeTrackingMs = 2000, maxSpeedMps = 0.1,
        headingStdDeg = 0.2, offsetStdM = 0.005, samples = 20, evidence = PointEvidence(50, 0.01, 0.0, true), stability = 1.0,
        reasons = emptyList(), advisories = emptyList(), duplicateOf = duplicateOf,
    )

    @Test
    fun `all'inizio si sceglie la partenza, con la porta consigliata`() {
        val g = guide(fresh)
        assertEquals(GuidePhase.START, g.phase)
        assertNull(g.activeSide)
        assertEquals(4, g.total); assertEquals(0, g.done); assertEquals(4, g.remaining)
        assertEquals(3, g.suggestedStart)
        assertFalse(g.canConfirmWall); assertFalse(g.canApply)
    }

    @Test
    fun `scelta la partenza si inquadra quella parete, Parete 1 di 4`() {
        val s = fresh.chooseStartSide(3)
        val g = guide(s)
        assertEquals(GuidePhase.SCAN, g.phase)
        assertEquals(3, g.activeSide)
        assertEquals(1, g.wallNumber)
        assertEquals(AimStatus.SEARCHING, g.aim)
        assertFalse(g.canConfirmWall)
        // Cambio della partenza: si torna alla scelta finché non c'è nessuna parete.
        assertEquals(GuidePhase.START, guide(s, choosingStart = true).phase)
        assertTrue(guide(withWalls(3), choosingStart = true).phase != GuidePhase.START)
    }

    @Test
    fun `stato della mira in parole semplici e conferma solo con la parete pronta`() {
        val s = fresh.chooseStartSide(3)
        assertEquals(AimStatus.NO_TRACKING, guide(s, tracking = false).aim)
        assertEquals(AimStatus.STABILIZING, guide(s, candidate = candidate(CandidateState.TRACKING)).aim)
        val ready = guide(s, candidate = candidate(CandidateState.PROPOSED))
        assertEquals(AimStatus.READY, ready.aim)
        assertTrue(ready.canConfirmWall)
        val dup = guide(s, candidate = candidate(CandidateState.PROPOSED, duplicateOf = 1))
        assertEquals(AimStatus.ALREADY_TAKEN, dup.aim)
        assertFalse(dup.canConfirmWall)
        assertFalse(guide(s, tracking = false, candidate = candidate(CandidateState.PROPOSED)).canConfirmWall)
    }

    @Test
    fun `dopo una parete con un'apertura la domanda arriva subito, poi si prosegue`() {
        val s = withWalls(3) // la porta è sul lato 3
        val g = guide(s)
        assertEquals(GuidePhase.QUESTION, g.phase)
        val q = g.questions.first()
        assertEquals(IssueCode.OPENING_CHECK, q.code)
        assertEquals(listOf(3), q.sides)
        val answered = guide(s.answer(q.key, Answer.OPENING_ON_THIS_WALL))
        // Dopo la risposta: la scansione riprende con la parete successiva (o un'altra domanda di contesto, se c'è).
        assertTrue(answered.phase == GuidePhase.SCAN || answered.questions.none { it.key == q.key }, "${answered.phase} ${answered.questions}")
    }

    @Test
    fun `progresso Parete N di M e parete attiva dalla sessione`() {
        val s = answerContext(withWalls(3, 0))
        val g = guide(s)
        assertEquals(GuidePhase.SCAN, g.phase)
        assertEquals(2, g.done); assertEquals(2, g.remaining); assertEquals(3, g.wallNumber)
        assertEquals(setOf(3, 0), g.doneSides)
        assertEquals(s.target, g.activeSide)
    }

    /** Risponde alle domande di contesto (aperture, partenza) come farebbe l'utente. */
    private fun answerContext(s0: SketchSession): SketchSession {
        var s = s0
        repeat(5) {
            val g = guide(s)
            if (g.phase != GuidePhase.QUESTION) return s
            val q = g.questions.first()
            s = if (q.code == IssueCode.ASSOCIATION_AMBIGUOUS)
                s.declareStart(com.sagoma.planimetria.scan.sketch.StartDeclaration(com.sagoma.planimetria.scan.sketch.StartReference.DOOR)).answer(q.key, Answer.START_WALL_VERIFIED)
            else s.answer(q.key, q.code.allowedAnswers.first())
        }
        return s
    }

    @Test
    fun `revisione chiesta prima della fine - mancano pareti, si puo tornare a scansionare, niente Applica`() {
        val s = answerContext(withWalls(3, 0))
        val g = guide(s, review = true)
        assertEquals(GuidePhase.REVIEW, g.phase)
        assertEquals(2, g.remaining)
        assertTrue(g.canResumeScan)
        assertFalse(g.canApply)
        assertNull(g.activeSide)
    }

    @Test
    fun `tutte le pareti - revisione con la scelta degli angoli, Applica solo quando la stanza e confermabile`() {
        val s = answerContext(withWalls(3, 0, 1, 2, tilts = mapOf(0 to 2.0, 2 to -1.5)))
        val g = guide(s)
        assertEquals(GuidePhase.REVIEW, g.phase)
        assertFalse(g.canResumeScan)
        val angle = assertNotNull(g.angleChoice)
        assertNull(angle.answer)
        assertTrue(angle.values.getValue("spostamentoMaxM") > 0.01)
        assertFalse(g.canApply) // manca la scelta
        // Le altre domande (per esempio schizzo diverso dalle misure) si confermano in revisione.
        var done = s.answer(SketchSession.ANGLE_CHOICE_KEY, Answer.SQUARE_ANGLES)
        for (q in guide(done).questions) done = done.answer(q.key, q.code.allowedAnswers.first())
        val ok = guide(done)
        assertTrue(ok.blocking.isEmpty(), "${ok.blocking}")
        assertTrue(ok.canApply, "${done.result().geometry.issues}")
        assertEquals(done.result().canConfirm, ok.canApply)
        // Aperture che non entrano nei muri nuovi: niente Applica.
        assertFalse(guide(done, applicable = false).canApply)
    }

    @Test
    fun `cambiare una parete riapre la scelta degli angoli e le altre decisioni sulla geometria`() {
        val s = answerContext(withWalls(3, 0, 1, 2, tilts = mapOf(0 to 2.0, 2 to -1.5)))
        var done = s.answer(SketchSession.ANGLE_CHOICE_KEY, Answer.SQUARE_ANGLES)
        for (q in guide(done).questions) done = done.answer(q.key, q.code.allowedAnswers.first())
        assertTrue(guide(done).canApply)
        val removed = done.removeWall(2)
        assertNull(removed.answers[SketchSession.ANGLE_CHOICE_KEY])
        assertTrue(removed.answers.keys.none { k -> SketchSession.GEOMETRY_DEPENDENT.any { k.startsWith(it.name + "[") } })
        // Togliere una parete cambia anche l'associazione: per la regola già esistente della sessione si riaprono anche le verifiche
        // delle aperture (si rifanno quando le pareti tornano).
        assertTrue(removed.answers.keys.none { it.startsWith(IssueCode.OPENING_CHECK.name) })
        assertEquals(GuidePhase.QUESTION, guide(removed).phase) // prima si rifanno le verifiche riaperte
        val g = guide(answerContext(removed))
        assertEquals(GuidePhase.SCAN, g.phase)
        assertEquals(2, g.activeSide)
        assertFalse(g.canApply)
    }

    // ---------- Testi della procedura ----------

    private val t = SketchScanTextIt()

    @Test
    fun `testi della procedura chiari e completi`() {
        assertEquals("Parete 2 di 4", t.wallOfTotal(2, 4))
        assertEquals("Completate 1 di 4", t.progress(1, 4))
        assertEquals("Lato 4 · porta", t.sideName(3, listOf(OpeningMark.DOOR)))
        assertEquals("Lato 1", t.sideName(0, emptyList()))
        assertEquals("Inquadra Lato 2 · finestra: punta il mirino al centro della parete", t.aimInstruction(t.sideName(1, listOf(OpeningMark.WINDOW))))
        for (p in GuidePhase.entries) assertTrue(t.phaseTitle(p).isNotBlank(), "$p")
        for (a in AimStatus.entries) assertTrue(t.aimStatus(a).isNotBlank(), "$a")
        assertTrue(t.aimStatus(AimStatus.READY).contains(t.confirmWall))
        val explain = t.angleExplain(0.047, 3.2)
        assertTrue("5 cm" in explain && "3,2°" in explain && "90°" in explain, explain)
        assertEquals("Manca 1 parete: torna alla scansione oppure inserisci la misura a mano.", t.wallsMissing(1))
        assertTrue(t.wallsMissing(2).startsWith("Mancano 2 pareti"))
        assertEquals("Applica alla stanza", t.applyRoom)
    }

    // ---------- Finestre passo passo ----------

    @Test
    fun `finestre in ordine - partenza, istruzioni, fotocamera libera, Fatto con la prossima a destra`() {
        assertEquals(GuideWindow.START, SketchScanGuide.window(guide(fresh), WindowMemory()))
        val started = fresh.chooseStartSide(3)
        assertEquals(GuideWindow.WALL_INTRO, SketchScanGuide.window(guide(started), WindowMemory()))
        // Letto "Inizia": nessuna finestra, si inquadra.
        assertNull(SketchScanGuide.window(guide(started), WindowMemory(introSeenFor = 3)))
        // Presa la prima parete con la porta: prima la domanda sulla porta, poi "Fatto".
        val first = withWalls(3)
        assertEquals(GuideWindow.QUESTION, SketchScanGuide.window(guide(first), WindowMemory(introSeenFor = 3)))
        val answered = answerContext(first)
        val g = guide(answered)
        assertEquals(GuideWindow.WALL_DONE, SketchScanGuide.window(g, WindowMemory(introSeenFor = 3)))
        assertEquals(3, g.lastDoneSide)
        assertEquals(0, g.activeSide)
        assertEquals(Turn.RIGHT, g.nextTurn) // dal lato 4 al lato 1 dello schizzo disegnato in senso orario
        // "Avanti" vale anche come istruzioni della parete successiva: fotocamera libera.
        assertNull(SketchScanGuide.window(g, WindowMemory().afterDone(g)))
    }

    @Test
    fun `annullata una parete, la prossima conferma mostra di nuovo Fatto`() {
        val s = answerContext(withWalls(3, 0))
        val seen = WindowMemory().afterDone(guide(s))
        assertNull(SketchScanGuide.window(guide(s), seen))
        val undone = answerContext(s.removeWall(0))
        // Tolta la parete: istruzioni per rifarla.
        assertEquals(GuideWindow.WALL_INTRO, SketchScanGuide.window(guide(undone), seen))
        val m = seen.normalized(guide(undone).done)
        assertEquals(1, m.doneSeen)
        // Ripresa: di nuovo "Fatto".
        val again = answerContext(withWalls(3, 0))
        assertEquals(GuideWindow.WALL_DONE, SketchScanGuide.window(guide(again), m.copy(introSeenFor = 0)))
    }

    @Test
    fun `revisione una cosa alla volta - tutte fatte, angoli, poi riepilogo`() {
        val s = answerContext(withWalls(3, 0, 1, 2, tilts = mapOf(0 to 2.0, 2 to -1.5)))
        val g = guide(s)
        assertEquals(GuideWindow.WALL_DONE, SketchScanGuide.window(g, WindowMemory(doneSeen = 3)))
        val seen = WindowMemory().afterDone(g)
        val first = SketchScanGuide.window(g, seen)
        assertTrue(first == GuideWindow.QUESTION || first == GuideWindow.ANGLES, "$first")
        var done = s
        for (q in guide(done).questions) done = done.answer(q.key, q.code.allowedAnswers.first())
        assertEquals(GuideWindow.ANGLES, SketchScanGuide.window(guide(done), seen))
        done = done.answer(SketchSession.ANGLE_CHOICE_KEY, Answer.SQUARE_ANGLES)
        for (q in guide(done).questions) done = done.answer(q.key, q.code.allowedAnswers.first())
        assertEquals(GuideWindow.SUMMARY, SketchScanGuide.window(guide(done), seen))
    }

    @Test
    fun `destra o sinistra solo tra lati vicini e mai su un angolo rientrante`() {
        assertEquals(Turn.RIGHT, SketchScanGuide.turn(sketch, 0, 1))
        assertEquals(Turn.LEFT, SketchScanGuide.turn(sketch, 1, 0))
        assertEquals(Turn.RIGHT, SketchScanGuide.turn(sketch, 3, 0))
        assertNull(SketchScanGuide.turn(sketch, 0, 2))
        // Lo stesso rettangolo disegnato al contrario: il lato successivo è a sinistra.
        val reversed = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(0.0, 290.0), Vec2(410.0, 290.0), Vec2(410.0, 0.0)))
        assertEquals(Turn.LEFT, SketchScanGuide.turn(reversed, 0, 1))
        // Stanza a L: l'angolo tra i lati 3 e 4 rientra, lì niente destra/sinistra.
        val l = RoomSketch.fromPlanInterior(listOf(Vec2(0.0, 0.0), Vec2(200.0, 0.0), Vec2(200.0, 100.0), Vec2(100.0, 100.0), Vec2(100.0, 200.0), Vec2(0.0, 200.0)))
        assertEquals(Turn.RIGHT, SketchScanGuide.turn(l, 1, 2))
        assertNull(SketchScanGuide.turn(l, 2, 3))
        assertNull(SketchScanGuide.turn(l, 3, 2))
        assertEquals(Turn.RIGHT, SketchScanGuide.turn(l, 3, 4))
    }

    @Test
    fun `testi delle finestre`() {
        assertEquals("È subito alla tua destra: girati verso destra.", t.turnHint(Turn.RIGHT))
        assertEquals("Ora la parete 3 di 4", t.nextWall(3, 4))
        assertEquals("✓ Fatto: Lato 1", t.wallDone(t.sideName(0, emptyList())))
        assertEquals(3, t.introSteps.size)
        assertTrue(t.introSteps.last().contains(t.confirmWall))
    }

    @Test
    fun `rifai una parete anche dal riepilogo - si torna subito alle istruzioni di quella parete`() {
        val s = answerContext(withWalls(3, 0, 1, 2))
        val seen = WindowMemory().afterDone(guide(s))
        assertTrue(SketchScanGuide.window(guide(s), seen) != GuideWindow.WALL_INTRO)
        // "Rifai Lato 2": parete tolta, istruzioni riaperte (come fa la schermata).
        val redo = answerContext(s.removeWall(1))
        val g = guide(redo)
        assertEquals(1, g.activeSide)
        assertEquals(GuideWindow.WALL_INTRO, SketchScanGuide.window(g, seen.copy(introSeenFor = null)))
        assertEquals("Rifai Lato 2", t.redoSide(t.sideName(1, emptyList())))
    }

    // ---------- Superfici che non corrispondono allo schizzo ----------

    /** Superficie proposta lunga 1 m nella direzione [deg] (0° = lungo x). */
    private fun proposedAt(deg: Double): CandidateWall {
        val r = Math.toRadians(deg)
        val d = ArXZ(cos(r), sin(r))
        return candidate(CandidateState.PROPOSED).copy(line = WallLine(ArXZ(2.0, 0.0), ArXZ(2.0, 0.0) + d, d, ArXZ(-d.z, d.x), 0.0, 0.0, 2.5, 0.0))
    }

    @Test
    fun `dalla seconda parete le superfici storte rispetto allo schizzo non si mostrano e non si confermano`() {
        val s = answerContext(withWalls(3)) // partenza confermata; ora il lato 1 (lungo x, perpendicolare alla partenza)
        assertEquals(0, s.target)
        assertEquals(0.0, s.sketchDirectionGapDeg(proposedAt(0.0))!!, 1e-6)
        assertTrue(s.fitsSketch(proposedAt(5.4))) // lo scarto più grande visto su una parete vera (scan6)
        assertTrue(s.fitsSketch(proposedAt(-9.9)))
        assertFalse(s.fitsSketch(proposedAt(12.0)))
        assertFalse(s.fitsSketch(proposedAt(45.0))) // la diagonale che taglia la stanza
        assertTrue(s.fitsSketch(proposedAt(180.0))) // stessa retta, verso opposto
        // Nella guida: stato "non corrisponde", niente conferma.
        val off = guide(s, candidate = proposedAt(45.0))
        assertEquals(AimStatus.OFF_SKETCH, off.aim)
        assertFalse(off.canConfirmWall)
        assertTrue(guide(s, candidate = proposedAt(3.0)).canConfirmWall)
        // E la sessione rifiuta comunque la conferma.
        val withCandidate = s.copy(engine = s.engine.copy(candidate = proposedAt(45.0)))
        val rejected = withCandidate.confirmWall(5000)
        assertEquals(com.sagoma.planimetria.scan.sketch.SessionEvent.Rejected(com.sagoma.planimetria.scan.sketch.SessionRejection.OFF_SKETCH_DIRECTION, 0), rejected.lastEvent)
        assertEquals(withCandidate.assigned, rejected.assigned)
    }

    @Test
    fun `la prima parete non si filtra - manca il riferimento - e la stanza ruotata nel mondo non cambia niente`() {
        val start = fresh.chooseStartSide(3)
        assertNull(start.sketchDirectionGapDeg(proposedAt(45.0)))
        assertTrue(start.fitsSketch(proposedAt(45.0)))
        // Stanza vera ruotata di 30° nel mondo AR: la partenza ruotata dà il riferimento, il lato successivo è atteso a 30°.
        val r = Math.toRadians(30.0)
        fun rot(p: ArXZ) = ArXZ(p.x * cos(r) - p.z * sin(r), p.x * sin(r) + p.z * cos(r))
        val l = line(3)
        val rotated = l.copy(a = rot(l.a), b = rot(l.b), inward = rot(l.inward))
        val s = start.copy(assigned = mapOf(3 to AssignedWall(1, rotated, 1000, emptyList())), startConfirmed = true, target = 0)
        assertEquals(0.0, s.sketchDirectionGapDeg(proposedAt(30.0))!!, 1e-6)
        assertFalse(s.fitsSketch(proposedAt(0.0)))
    }

    @Test
    fun `testo per la superficie che non corrisponde`() {
        assertTrue(t.aimStatus(AimStatus.OFF_SKETCH).contains("non corrisponde"))
    }
}
