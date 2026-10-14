package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.assisted.AimFrames
import com.sagoma.planimetria.scan.assisted.AssistedScene
import com.sagoma.planimetria.scan.assisted.CandidateState
import com.sagoma.planimetria.scan.recording.RecordedPlane
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flusso della sessione su scene SINTETICHE con il motore vero ([com.sagoma.planimetria.scan.assisted.AssistedScan]): verifica
 * il comportamento del codice (associazione esplicita, correzioni, blocchi), non la capacità di un utente vero né l'accuratezza.
 */
class SketchSessionTest {

    /** Stanza antioraria nel mondo AR (x, z), ruotata di `rotDeg` attorno all'origine. */
    private fun room(rotDeg: Double, vararg pts: Pair<Double, Double>): List<Pair<Double, Double>> = pts.map { (x, z) ->
        val r = Math.toRadians(rotDeg)
        (x * cos(r) - z * sin(r)) to (x * sin(r) + z * cos(r))
    }

    /** Piani delle pareti vere: dal 15 all'85% di ogni lato, normale verso l'interno (stanza antioraria → a sinistra). */
    private fun planes(c: List<Pair<Double, Double>>): List<RecordedPlane> = c.indices.map { k ->
        val (ax, az) = c[k]; val (bx, bz) = c[(k + 1) % c.size]
        AssistedScene.plane(k + 1, ax + (bx - ax) * 0.15, az + (bz - az) * 0.15, ax + (bx - ax) * 0.85, az + (bz - az) * 0.85, side = 1)
    }

    /**
     * L'utente al centro inquadra le pareti nell'ordine `aimOrder` (indici delle pareti vere), 4 s ciascuna. `gesture` decide cosa
     * fare quando la parete inquadrata è proposta: di solito "conferma".
     */
    private fun scan(
        start: SketchSession,
        c: List<Pair<Double, Double>>,
        aimOrder: List<Int>,
        eye: Pair<Double, Double>? = null,
        gesture: (SketchSession, Int, Long) -> SketchSession = { s, _, t -> s.confirmWall(t) },
    ): SketchSession {
        val (cx, cz) = eye ?: ((c.sumOf { it.first } / c.size) to (c.sumOf { it.second } / c.size))
        val ps = planes(c)
        val frames = AimFrames.from(AssistedScene.scene(aimOrder.size * 4_000L - 50) {
            planes = { ps }
            cam = { t ->
                val k = aimOrder[(t / 4_000).toInt().coerceAtMost(aimOrder.lastIndex)]
                val (ax, az) = c[k]; val (bx, bz) = c[(k + 1) % c.size]
                AssistedScene.camera(cx + 0.01 * AssistedScene.sway(t, 1), 1.4, cz, (ax + bx) / 2, 1.25, (az + bz) / 2)
            }
        })
        var s = start
        val done = HashSet<Int>()
        for (f in frames) {
            s = s.step(f)
            val k = aimOrder[(f.timeMs / 4_000).toInt().coerceAtMost(aimOrder.lastIndex)]
            val cand = s.candidate
            if (k !in done && cand != null && cand.state == CandidateState.PROPOSED && cand.duplicateOf == null) {
                s = gesture(s, k, f.timeMs)
                if (s.lastEvent is SessionEvent.WallAssigned) done += k
            }
        }
        return s
    }

    private val rect = room(23.0, 0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0)
    private val nearSquare = room(-41.0, 0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.9, 0.0 to 3.9)
    private val lRoom = room(12.0, 0.0 to 0.0, 5.0 to 0.0, 5.0 to 2.0, 3.0 to 2.0, 3.0 to 4.0, 0.0 to 4.0)
    /** Punto di vista dentro il corpo principale della L (dal baricentro dei vertici la parete corta si vedrebbe di taglio). */
    private val lEye = room(12.0, 2.0 to 1.0).first()
    private fun sketch(vararg pts: Pair<Double, Double>) = RoomSketch.fromPlanInterior(pts.map { (x, y) -> Vec2(x, y) })
    private val rectSketch = sketch(0.0 to 0.0, 410.0 to 0.0, 410.0 to 290.0, 0.0 to 290.0)
    private val squareSketch = sketch(0.0 to 0.0, 400.0 to 0.0, 400.0 to 400.0, 0.0 to 400.0)
    private val lSketch = sketch(0.0 to 0.0, 490.0 to 0.0, 490.0 to 200.0, 300.0 to 200.0, 300.0 to 390.0, 0.0 to 390.0)

    private fun codes(r: SessionResult) = r.geometry.issues.map { it.code }.toSet()

    // ---------- Partenza esplicita ----------

    @Test
    fun `senza lato di partenza nessuna parete viene associata`() {
        val reasons = ArrayList<String>()
        val s = scan(SketchSession(rectSketch), rect, listOf(0)) { st, _, t -> st.confirmWall(t).also { r -> (r.lastEvent as? SessionEvent.Rejected)?.let { reasons += it.why.name } } }
        assertTrue(s.assigned.isEmpty())
        assertTrue(reasons.isNotEmpty() && reasons.all { it == SessionRejection.START_NOT_CHOSEN.name }, "$reasons")
        assertTrue(IssueCode.NO_CONFIRMED_WALL in codes(s.result()))
        assertFalse(s.result().canConfirm)
    }

    @Test
    fun `la porta suggerisce la partenza solo se e unica`() {
        assertEquals(2, SketchSession(rectSketch, listOf(SketchOpening(2, OpeningMark.DOOR))).suggestedStartSide)
        assertNull(SketchSession(rectSketch, listOf(SketchOpening(2, OpeningMark.DOOR), SketchOpening(0, OpeningMark.DOOR))).suggestedStartSide)
        assertNull(SketchSession(rectSketch).suggestedStartSide)
    }

    @Test
    fun `flusso completo stanza a L con partenza esplicita e associazione mostrata`() {
        val s = scan(SketchSession(lSketch).chooseStartSide(0), lRoom, (0 until 6).toList(), lEye)
        val r = s.result()
        assertEquals((0 until 6).toSet(), s.assigned.keys)
        assertTrue(s.startConfirmed)
        assertEquals(listOf(true, false, false, false, false, false), r.associations.map { it.isStart })
        assertEquals((1..6).toList(), r.associations.map { it.wallId }) // lato k ↔ parete confermata k+1, mostrato
        assertTrue(r.alternatives.isEmpty(), "${r.alternatives}") // la forma a L non ammette altre associazioni
        assertTrue(r.canConfirm, "${r.geometry.issues}")
        for (k in 0 until 6) assertEquals(0.0, r.geometry.corners!![k].distanceTo(com.sagoma.planimetria.scan.ArXZ(lRoom[k].first, lRoom[k].second)), 0.02)
    }

    @Test
    fun `la prima parete deve essere la partenza e prima della partenza non si cambia lato`() {
        val s = SketchSession(rectSketch).chooseStartSide(1)
        assertEquals(SessionRejection.START_NOT_CONFIRMED, (s.setTarget(2).lastEvent as SessionEvent.Rejected).why)
        val after = scan(s, rect, listOf(1))
        assertEquals(setOf(1), after.assigned.keys)
        assertTrue(after.startConfirmed)
        assertEquals(2, after.target) // si prosegue sul lato successivo dello schizzo
        assertEquals(SessionRejection.WALLS_ALREADY_ASSIGNED, (after.chooseStartSide(0).lastEvent as SessionEvent.Rejected).why)
    }

    // ---------- Ambiguità ----------

    @Test
    fun `rettangolo senza riferimenti e ambiguo e non si conferma senza verifica della partenza`() {
        val s = scan(SketchSession(rectSketch).chooseStartSide(0), rect, (0 until 4).toList())
        val r = s.result()
        assertTrue(r.geometry.blocking.isEmpty(), "${r.geometry.issues}")
        val amb = r.geometry.openQuestions.single { it.code == IssueCode.ASSOCIATION_AMBIGUOUS }
        // Spostate di 1 o 3 lati (lati scambiati: solo le lunghezze lo direbbero) o di 2 (il rettangolo ruotato di 180° è identico).
        assertEquals(setOf(1, 2, 3), r.alternatives.filter { it.reflectedAround == null }.map { it.shift }.toSet())
        assertTrue(r.alternatives.none { it.reflectedAround != null }, "${r.alternatives}") // l'ordine invertito no: il verso delle pareti lo esclude
        assertFalse(r.canConfirm)
        // La risposta giusta (verifica sul posto) risolve; una risposta non pertinente no.
        val wrongAnswer = s.answer(amb.key, Answer.KEEP_MEASURED).result()
        assertFalse(wrongAnswer.canConfirm)
        assertTrue(wrongAnswer.geometry.rejectedAnswers.any { it.key == amb.key })
        // "Ho verificato" senza dire con quale riferimento non vale: è rifiutata e la domanda resta aperta.
        val noDecl = s.answer(amb.key, Answer.START_WALL_VERIFIED).result()
        assertFalse(noDecl.canConfirm)
        assertEquals(RejectionReason.MISSING_DECLARATION, noDecl.geometry.rejectedAnswers.single { it.key == amb.key }.reason)
        // Con la dichiarazione dell'utente (porta) la risposta vale ed è registrata, insieme alla dichiarazione.
        val ok = s.declareStart(StartDeclaration(StartReference.DOOR)).answer(amb.key, Answer.START_WALL_VERIFIED).result()
        assertTrue(ok.canConfirm, "${ok.geometry.issues}")
        assertEquals(Answer.START_WALL_VERIFIED, ok.geometry.issues.single { it.key == amb.key }.answer)
        assertEquals(StartReference.DOOR, ok.startDeclaration?.reference)
    }

    @Test
    fun `stanza quasi quadrata le lunghezze non bastano e la sessione lo segnala`() {
        val s = scan(SketchSession(squareSketch).chooseStartSide(0), nearSquare, (0 until 4).toList())
        val r = s.result()
        // Nessuna domanda di lunghezza (4,0 vs 3,9 m sono entro il 15% dello schizzo 4 × 4): solo l'ambiguità lo dice.
        assertTrue(r.geometry.issues.none { it.code == IssueCode.SKETCH_LENGTH_MISMATCH }, "${r.geometry.issues}")
        assertTrue(r.geometry.openQuestions.any { it.code == IssueCode.ASSOCIATION_AMBIGUOUS })
        assertFalse(r.canConfirm)
    }

    @Test
    fun `una porta verificata sulla parete giusta toglie l'ambiguita`() {
        val sess = SketchSession(rectSketch, listOf(SketchOpening(0, OpeningMark.DOOR))).chooseStartSide(0)
        val s = scan(sess, rect, (0 until 4).toList())
        val check = s.result().geometry.openQuestions.single { it.code == IssueCode.OPENING_CHECK }
        assertEquals(listOf(0), check.sides)
        val r = s.answer(check.key, Answer.OPENING_ON_THIS_WALL).result()
        assertTrue(r.alternatives.isEmpty(), "${r.alternatives}")
        assertTrue(r.geometry.issues.none { it.code == IssueCode.ASSOCIATION_AMBIGUOUS })
        assertTrue(r.canConfirm, "${r.geometry.issues}")
        assertEquals(Answer.OPENING_ON_THIS_WALL, r.associations[0].openingCheck)
    }

    // ---------- Errore di associazione e correzione ----------

    @Test
    fun `partenza sbagliata di un lato viene scoperta dalla porta e si corregge spostando tutto`() {
        // Porta sul lato 0 dello schizzo; l'utente parte dal lato 0 ma inquadra la parete vera 1: tutto finisce spostato di uno.
        val sess = SketchSession(rectSketch, listOf(SketchOpening(0, OpeningMark.DOOR))).chooseStartSide(0)
        val s = scan(sess, rect, listOf(1, 2, 3, 0))
        val check = s.result().geometry.openQuestions.single { it.code == IssueCode.OPENING_CHECK }
        // Sul posto: la parete associata al lato 0 non ha la porta.
        val wrong = s.answer(check.key, Answer.OPENING_NOT_ON_THIS_WALL)
        val r = wrong.result()
        assertTrue(r.geometry.blocking.any { it.code == IssueCode.ASSOCIATION_CONTRADICTED }, "${r.geometry.issues}")
        assertFalse(r.canConfirm)
        // Correzione dei dati: ogni parete un lato più avanti (la parete vera 1 va al lato 1). Le risposte vecchie decadono.
        val fixed = wrong.shiftAll(1)
        assertTrue(fixed.result().geometry.issues.none { it.code == IssueCode.ASSOCIATION_CONTRADICTED })
        val check2 = fixed.result().geometry.openQuestions.single { it.code == IssueCode.OPENING_CHECK }
        val ok = fixed.answer(check2.key, Answer.OPENING_ON_THIS_WALL).result()
        assertTrue(ok.canConfirm, "${ok.geometry.issues}")
        for (k in 0 until 4) assertEquals(0.0, ok.geometry.corners!![k].distanceTo(com.sagoma.planimetria.scan.ArXZ(rect[k].first, rect[k].second)), 0.02)
    }

    @Test
    fun `parete saltata e la successiva confermata sul lato sbagliato viene bloccata e si corregge`() {
        // Dal baricentro dei vertici la parete corta della L (lato 2) si vede di taglio e non viene mai proposta: l'utente
        // conferma la parete 3 mentre il lato da scansionare è ancora il 2, e da lì tutto slitta di un lato.
        // Filtro delle superfici fuori schizzo SPENTO: qui si prova che il nucleo riconosce lo slittamento se arriva fino a lui.
        val s = scan(SketchSession(lSketch, thresholds = SketchThresholds(candidateDirectionDeg = 90.0)).chooseStartSide(0), lRoom, (0 until 6).toList())
        assertEquals(mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5), s.assigned.mapValues { it.value.wallId })
        val r = s.result()
        // Rilevato come errore di associazione (lati consecutivi paralleli, direzioni diverse dallo schizzo), non solo "mancano misure".
        assertTrue(r.geometry.blocking.any { it.code in SketchSession.ASSOCIATION_SENSITIVE }, "${r.geometry.issues}")
        assertFalse(r.canConfirm)
        // Correzione: le pareti confermate 3, 4, 5 (fisiche 3, 4, 5) vanno un lato più avanti (dal 4 al 5, dal 3 al 4, dal 2 al 3).
        val fixed = s.reassign(4, 5).reassign(3, 4).reassign(2, 3)
        assertEquals(mapOf(0 to 1, 1 to 2, 3 to 3, 4 to 4, 5 to 5), fixed.assigned.mapValues { it.value.wallId })
        val rf = fixed.result()
        assertTrue(rf.geometry.issues.none { it.code in SketchSession.ASSOCIATION_SENSITIVE }, "${rf.geometry.issues}")
        // Il lato 2 resta da scansionare (o da misurare): finché manca, la stanza non si conferma e l'ambiguità non è esclusa.
        assertTrue(rf.geometry.blocking.any { it.code == IssueCode.UNDERDETERMINED })
        assertFalse(rf.canConfirm)
    }

    @Test
    fun `parete saltata con il filtro dello schizzo - la parete perpendicolare viene rifiutata alla conferma`() {
        val rejected = ArrayList<SessionRejection>()
        val s = scan(SketchSession(lSketch).chooseStartSide(0), lRoom, (0 until 6).toList()) { st, _, t ->
            st.confirmWall(t).also { r -> (r.lastEvent as? SessionEvent.Rejected)?.let { rejected += it.why } }
        }
        // La parete fisica 3 (perpendicolare al lato 2 da scansionare) non entra: è la superficie "fuori schizzo".
        assertTrue(SessionRejection.OFF_SKETCH_DIRECTION in rejected, "$rejected")
        // Le pareti parallele allo schizzo passano il filtro: se finiscono sul lato sbagliato le ferma comunque il nucleo.
        val r = s.result()
        assertFalse(r.canConfirm, "${s.assigned.mapValues { it.value.wallId }} ${r.geometry.issues}")
        assertTrue(r.geometry.blocking.any { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT }, "${r.geometry.issues}")
    }

    @Test
    fun `due pareti scambiate bloccano e la riassegnazione risolve`() {
        // L'utente conferma le pareti 1 e 3 sui lati sbagliati (3 e 1): verso opposto allo schizzo → conflitto bloccante.
        val sess = SketchSession(rectSketch).chooseStartSide(0)
        val s = scan(sess, rect, listOf(0, 3, 2, 1)) // il lato da scansionare avanza 1, 2, 3 ma la parete inquadrata è 3, 2, 1
        val r = s.result()
        assertTrue(r.geometry.blocking.any { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT }, "${r.geometry.issues}")
        assertFalse(r.canConfirm)
        val fixed = s.reassign(1, 3) // scambia le pareti dei lati 1 e 3
        val rf = fixed.result()
        assertTrue(rf.geometry.blocking.isEmpty(), "${rf.geometry.issues}")
    }

    @Test
    fun `togliere la parete di partenza riporta al primo passo`() {
        val s = scan(SketchSession(rectSketch).chooseStartSide(0), rect, listOf(0, 1))
        val removed = s.removeWall(0)
        assertFalse(removed.startConfirmed)
        assertEquals(setOf(1), removed.assigned.keys)
        assertTrue(IssueCode.START_NOT_CONFIRMED in codes(removed.result()))
        assertFalse(removed.result().canConfirm)
    }

    @Test
    fun `una risposta non elimina il blocco di partenza non confermata`() {
        val s = SketchSession(rectSketch).chooseStartSide(0)
        val key = GeometryIssue.keyOf(IssueCode.START_NOT_CONFIRMED, listOf(0))
        val r = s.answer(key, Answer.START_WALL_VERIFIED).result()
        assertTrue(r.geometry.blocking.any { it.code == IssueCode.START_NOT_CONFIRMED })
        assertEquals(RejectionReason.NEEDS_DATA_CHANGE, r.geometry.rejectedAnswers.single { it.key == key }.reason)
    }

    @Test
    fun `LIMITE NOTO - senza aperture ne verifica una stanza simmetrica resta non confermabile`() {
        // Non è un errore del codice: senza un riferimento fisico (porta verificata o partenza verificata sul posto) il sistema
        // non può sapere quale parete è quale in una stanza simmetrica. Sta all'utente verificarlo: il test documenta il limite.
        val s = scan(SketchSession(squareSketch).chooseStartSide(0), nearSquare, (0 until 4).toList())
        assertFalse(s.result().canConfirm)
        assertNotNull(s.result().geometry.openQuestions.singleOrNull { it.code == IssueCode.ASSOCIATION_AMBIGUOUS })
    }

    // ---------- Lato interno noto solo per alcune pareti ----------

    /**
     * Le pareti dei lati `sides` come se il motore non conoscesse la posizione del telefono alla conferma: lato interno NON noto
     * e normale di convenzione rovesciata (il caso peggiore: la normale punta fuori dalla stanza).
     */
    private fun withUnknownInward(s: SketchSession, sides: Set<Int>) = s.copy(
        assigned = s.assigned.mapValues { (k, w) -> if (k in sides) w.copy(line = w.line.copy(inwardKnown = false, inward = w.line.inward * -1.0)) else w },
    )

    /**
     * Ogni alternativa elencata è davvero compatibile: rifatta a mano, nessun blocco la esclude secondo la regola prudente
     * (blocchi indipendenti dal verso, o direzionali su soli versi misurati).
     */
    private fun assertReallyCompatible(s: SketchSession, alts: List<AssociationAlternative>) {
        for (alt in alts) {
            val moved = s.assigned.entries.associate { (k, w) -> alt.map(k, s.n) to w.line }
            val g = SketchSolver.solve(SketchGeometryInput(s.sketch, moved))
            assertFalse(SketchSession.excludes(g.blocking, SketchSession.inferredSides(moved, s.n)), "alternativa $alt non compatibile: ${g.blocking}")
        }
    }

    @Test
    fun `rettangolo con due pareti a lato interno noto resta ambiguo dove la geometria lo consente`() {
        val full = scan(SketchSession(rectSketch).chooseStartSide(0), rect, (0 until 4).toList())
        val allKnown = full.result().alternatives.toSet()
        // Due casi concreti: lati noti adiacenti (0, 1) e lati noti opposti (0, 2).
        for (known in listOf(setOf(0, 1), setOf(0, 2))) {
            val mixed = withUnknownInward(full, (0 until 4).toSet() - known)
            val r = mixed.result()
            val alts = r.alternatives
            println("rettangolo, lati con lato interno noto $known: alternative $alts")
            // 1) Rendere ignoto il lato interno di alcune pareti può solo AGGIUNGERE alternative, mai toglierle.
            assertTrue(alts.toSet().containsAll(allKnown), "noti $known: perse alternative ${allKnown - alts.toSet()}")
            // 2) Resta almeno un'alternativa, e ognuna è davvero compatibile (rifatta a mano). La rotazione di 180° (spostamento
            //    di 2 lati) è una simmetria fisica del rettangolo: nessun verso noto può escluderla.
            assertTrue(alts.isNotEmpty())
            assertTrue(alts.any { it.reflectedAround == null && it.shift == 2 }, "noti $known: $alts")
            assertReallyCompatible(mixed, alts)
            // 3) L'associazione non viene dichiarata certa: ambiguità aperta, stanza non confermabile.
            assertTrue(r.geometry.openQuestions.any { it.code == IssueCode.ASSOCIATION_AMBIGUOUS }, "noti $known: ${r.geometry.issues}")
            assertFalse(r.canConfirm)
            // Le pareti a lato interno ignoto non generano falsi conflitti di verso.
            assertTrue(r.geometry.issues.none { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT || it.code == IssueCode.INWARD_MISMATCH }, "noti $known: ${r.geometry.issues}")
        }
    }


    @Test
    fun `stanza a L con una sola parete a lato interno noto - regola prudente sui versi inferiti`() {
        val full = scan(SketchSession(lSketch).chooseStartSide(0), lRoom, (0 until 6).toList(), lEye)
        assertEquals((0 until 6).toSet(), full.assigned.keys)
        // Con TUTTI i versi misurati la forma a L esclude ogni alternativa (anche con blocchi direzionali: il verso è un dato).
        assertTrue(full.result().alternatives.isEmpty())
        val mixed = withUnknownInward(full, (1 until 6).toSet()) // solo il lato 0 ha il lato interno noto
        val r = mixed.result()
        val alts = r.alternatives.toSet()
        val candidates = (1 until 6).map { AssociationAlternative(it, null) } + (0 until 6).map { AssociationAlternative(0, it) }
        for (alt in candidates) {
            val moved = mixed.assigned.entries.associate { (k, w) -> alt.map(k, 6) to w.line }
            val blocking = SketchSolver.solve(SketchGeometryInput(mixed.sketch, moved)).blocking.filter { it.code in SketchSession.ASSOCIATION_SENSITIVE }
            val inferred = SketchSession.inferredSides(moved, 6)
            val independent = blocking.filter { it.code in SketchSession.VERSE_INDEPENDENT }
            val directionalOnMeasured = blocking.filter { it.code in SketchSession.DIRECTIONAL && it.sides.isNotEmpty() && it.sides.none { s -> s in inferred } }
            val directionalOnInferred = blocking.filter { it.code in SketchSession.DIRECTIONAL && (it.sides.isEmpty() || it.sides.any { s -> s in inferred }) }
            println("  $alt: indipendenti ${independent.map { it.code to it.sides }} · direzionali su versi misurati ${directionalOnMeasured.map { it.code to it.sides }} · su versi inferiti ${directionalOnInferred.map { it.code to it.sides }} · compatibile ${alt in alts}")
            if (alt in alts) {
                // Resta tra le alternative: nessun blocco che la escluda senza appoggiarsi a un verso inferito.
                assertTrue(independent.isEmpty() && directionalOnMeasured.isEmpty(), "$alt elencata ma esclusa da $independent $directionalOnMeasured")
            } else {
                // Esclusa: almeno un blocco indipendente dal verso, o direzionale su soli versi misurati. MAI solo versi inferiti.
                assertTrue(independent.isNotEmpty() || directionalOnMeasured.isNotEmpty(), "$alt esclusa solo da versi inferiti: ${directionalOnInferred.map { it.code to it.sides }}")
            }
        }
        // Le alternative che dipendono solo da versi inferiti restano: la sessione chiede la verifica e non si conferma.
        assertTrue(alts.isNotEmpty(), "con un solo verso misurato nessuna alternativa resta: ${r.alternatives}")
        assertTrue(r.geometry.openQuestions.any { it.code == IssueCode.ASSOCIATION_AMBIGUOUS })
        assertFalse(r.canConfirm)
        assertReallyCompatible(mixed, r.alternatives)
        // La verifica sul posto (con dichiarazione) la risolve.
        val amb = r.geometry.openQuestions.single { it.code == IssueCode.ASSOCIATION_AMBIGUOUS }
        val ok = mixed.declareStart(StartDeclaration(StartReference.OTHER, "parete lunga della L")).answer(amb.key, Answer.START_WALL_VERIFIED).result()
        assertTrue(ok.geometry.issues.none { it.isOpenQuestion && it.code == IssueCode.ASSOCIATION_AMBIGUOUS })
    }

    @Test
    fun `regola di esclusione - i controlli indipendenti dal verso escludono sempre, quelli direzionali solo su versi misurati`() {
        fun i(code: IssueCode, vararg sides: Int) = GeometryIssue(code, sides.toList())
        val inferred = setOf(2, 3)
        // Indipendenti dal verso: escludono anche se toccano lati dal verso inferito.
        for (c in SketchSession.VERSE_INDEPENDENT) assertTrue(SketchSession.excludes(listOf(i(c, 2)), inferred), "$c")
        // Direzionali su un lato dal verso inferito: NON escludono.
        assertFalse(SketchSession.excludes(listOf(i(IssueCode.SIDE_REVERSED, 2)), inferred))
        assertFalse(SketchSession.excludes(listOf(i(IssueCode.OBSERVED_BEYOND_CORNER, 3)), inferred))
        assertFalse(SketchSession.excludes(listOf(i(IssueCode.SPIKE_ANGLE, 1, 2)), inferred))
        assertFalse(SketchSession.excludes(listOf(i(IssueCode.WRONG_ORIENTATION)), inferred))
        // Direzionali su lati dal verso misurato: escludono.
        assertTrue(SketchSession.excludes(listOf(i(IssueCode.SIDE_REVERSED, 1)), inferred))
        assertTrue(SketchSession.excludes(listOf(i(IssueCode.SKETCH_DIRECTION_CONFLICT, 0)), inferred))
        assertTrue(SketchSession.excludes(listOf(i(IssueCode.WRONG_ORIENTATION)), emptySet()))
        // Non di associazione (per esempio mancano misure): non escludono.
        assertFalse(SketchSession.excludes(listOf(i(IssueCode.UNDERDETERMINED, 1, 2)), emptySet()))
        // Un lato senza parete ha il verso inferito (dallo schizzo); una parete a lato interno noto no.
        val line = ConfirmedLine(com.sagoma.planimetria.scan.ArXZ(0.0, 0.0), com.sagoma.planimetria.scan.ArXZ(1.0, 0.0), com.sagoma.planimetria.scan.ArXZ(0.0, 1.0), 0.01, 1.0)
        assertEquals(setOf(1, 2), SketchSession.inferredSides(mapOf(0 to line, 1 to line.copy(inwardKnown = false)), 3))
    }

    // ---------- Angoli: scansione o 90° (scelta dell'utente) ----------

    /** Le pareti assegnate ruotate attorno al centro della parte vista: angoli misurati non esattamente retti. */
    private fun withTilts(s: SketchSession, tilts: Map<Int, Double>) = s.copy(
        assigned = s.assigned.mapValues { (k, w) ->
            val deg = tilts[k] ?: return@mapValues w
            val r = Math.toRadians(deg)
            val l = w.line
            val mid = (l.a + l.b) * 0.5
            fun rot(p: com.sagoma.planimetria.scan.ArXZ) = (p - mid).let { v -> mid + com.sagoma.planimetria.scan.ArXZ(v.x * cos(r) - v.z * sin(r), v.x * sin(r) + v.z * cos(r)) }
            w.copy(line = l.copy(a = rot(l.a), b = rot(l.b)))
        },
    )

    private val squareKey = GeometryIssue.keyOf(IssueCode.ANGLES_NEAR_SQUARE, emptyList())

    @Test
    fun `angoli quasi retti l'utente sceglie e senza scelta non si conferma`() {
        // Il rettangolo è ambiguo per simmetria: la partenza verificata sul posto risolve quella domanda, resta solo la squadra.
        val scanned = withTilts(scan(SketchSession(rectSketch).chooseStartSide(0), rect, (0 until 4).toList()), mapOf(0 to 2.0, 2 to -1.5))
        val amb = scanned.result().geometry.issues.single { it.code == IssueCode.ASSOCIATION_AMBIGUOUS }
        val s = scanned.declareStart(StartDeclaration(StartReference.DOOR)).answer(amb.key, Answer.START_WALL_VERIFIED)
        val open = s.result()
        val q = open.geometry.issues.single { it.code == IssueCode.ANGLES_NEAR_SQUARE }
        assertTrue(q.isOpenQuestion)
        assertTrue(q.values.getValue("scartoMaxDeg") > 1.0)
        assertTrue(q.values.getValue("spostamentoMaxM") > 0.01) // di quanto cambia la pianta, mostrato all'utente
        assertFalse(open.canConfirm)
        // "Tieni gli angoli della scansione": stessa geometria della scansione.
        val keep = s.answer(squareKey, Answer.KEEP_SCAN_ANGLES).result()
        assertTrue(keep.canConfirm, "${keep.geometry.issues}")
        assertEquals(open.geometry.corners, keep.geometry.corners)
        // "Approssima a 90°": tutti gli angoli retti.
        val sq = s.answer(squareKey, Answer.SQUARE_ANGLES).result()
        assertTrue(sq.canConfirm, "${sq.geometry.issues}")
        for (c in sq.geometry.cornerResults) assertEquals(90.0, c.interiorAngleDeg, 1e-9)
        assertEquals(Answer.SQUARE_ANGLES, sq.geometry.issues.single { it.code == IssueCode.ANGLES_NEAR_SQUARE }.answer)
        // Si torna alla scelta: di nuovo la domanda aperta e la geometria della scansione.
        val back = s.answer(squareKey, Answer.SQUARE_ANGLES).answer(squareKey, null).result()
        assertEquals(open.geometry.corners, back.geometry.corners)
        assertFalse(back.canConfirm)
    }

    @Test
    fun `angoli gia retti nessuna domanda, scarto grande domanda con lo spostamento`() {
        val base = scan(SketchSession(rectSketch).chooseStartSide(0), rect, (0 until 4).toList())
        assertTrue(IssueCode.ANGLES_NEAR_SQUARE !in codes(base.result()), "${base.result().geometry.issues}")
        // Nessun limite in gradi: decide l'utente, vedendo quanto cambia la pianta.
        val q = withTilts(base, mapOf(1 to 6.0)).result().geometry.issues.single { it.code == IssueCode.ANGLES_NEAR_SQUARE }
        assertTrue(q.values.getValue("spostamentoMaxM") > 0.1)
    }

    @Test
    fun `la scelta degli angoli si azzera quando cambiano le pareti o le misure`() {
        // scan6: scelta con 3 pareti, poi la quarta ha cambiato la pianta; la scelta deve tornare aperta.
        // L'utente sceglie dopo la terza parete, poi conferma la quarta: la scelta sparisce.
        var afterThree: SketchSession? = null
        val four = scan(SketchSession(rectSketch).chooseStartSide(0), rect, (0 until 4).toList()) { s, _, t ->
            s.confirmWall(t).let { c -> if (c.assigned.size == 3) c.answer(SketchSession.ANGLE_CHOICE_KEY, Answer.SQUARE_ANGLES).also { afterThree = it } else c }
        }
        assertEquals(Answer.SQUARE_ANGLES, afterThree!!.answers[SketchSession.ANGLE_CHOICE_KEY])
        assertEquals(4, four.assigned.size)
        assertNull(four.answers[SketchSession.ANGLE_CHOICE_KEY])
        val three = withTilts(afterThree!!, mapOf(0 to 2.0, 1 to -1.5)).answer(SketchSession.ANGLE_CHOICE_KEY, null)
        val chosen = three.answer(SketchSession.ANGLE_CHOICE_KEY, Answer.SQUARE_ANGLES)
        // Togliere, spostare, cambiare una misura o una stima: idem. Una misura uguale a prima no.
        assertNull(chosen.removeWall(2).answers[SketchSession.ANGLE_CHOICE_KEY])
        assertNull(chosen.reassign(2, 3).answers[SketchSession.ANGLE_CHOICE_KEY])
        assertNull(chosen.shiftAll(1).answers[SketchSession.ANGLE_CHOICE_KEY])
        assertNull(chosen.setManualLength(3, 2.9).answers[SketchSession.ANGLE_CHOICE_KEY])
        assertNull(chosen.acceptSketchLength(3, true).answers[SketchSession.ANGLE_CHOICE_KEY])
        val withLength = three.setManualLength(3, 2.9).answer(SketchSession.ANGLE_CHOICE_KEY, Answer.KEEP_SCAN_ANGLES)
        assertEquals(Answer.KEEP_SCAN_ANGLES, withLength.setManualLength(3, 2.9).answers[SketchSession.ANGLE_CHOICE_KEY])
        // Anche le altre risposte che dipendono dalla pianta si riaprono ("la misura è giusta", "la parete continua")...
        val other = chosen.answer("SKETCH_LENGTH_MISMATCH[1]", Answer.KEEP_MEASURED).answer("WALL_CONTINUES[2]", Answer.WALL_CONTINUES_BEHIND)
            .answer("OPENING_CHECK[1]", Answer.OPENING_ON_THIS_WALL).acceptSketchLength(3, true)
        assertNull(other.answers["SKETCH_LENGTH_MISMATCH[1]"])
        assertNull(other.answers["WALL_CONTINUES[2]"])
        // ...quelle che non dipendono dalla geometria (l'apertura c'è su quella parete) restano.
        assertEquals(Answer.OPENING_ON_THIS_WALL, other.answers["OPENING_CHECK[1]"])
    }
}
