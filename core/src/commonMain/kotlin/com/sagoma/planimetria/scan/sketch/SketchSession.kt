package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.scan.assisted.Advisory
import com.sagoma.planimetria.scan.assisted.AimFrame
import com.sagoma.planimetria.scan.assisted.AssistedParams
import com.sagoma.planimetria.scan.assisted.AssistedScan
import com.sagoma.planimetria.scan.assisted.AssistedState
import com.sagoma.planimetria.scan.assisted.CandidateState
import com.sagoma.planimetria.scan.assisted.CandidateWall

/*
 * Percorso B: la SESSIONE di scansione guidata dallo schizzo. Pura e immutabile (ogni gesto dà la sessione nuova), così il
 * flusso si prova sul computer. Il motore che propone le pareti è [AssistedScan], invariato; la geometria è [SketchSolver].
 *
 * L'associazione parete ↔ lato dello schizzo è SEMPRE esplicita: l'utente sceglie il lato di partenza, conferma la parete
 * mentre la inquadra, e ogni parete confermata va al lato "da scansionare" in quel momento. Nessuna associazione è dedotta
 * dalle lunghezze. Quando le misure sono compatibili anche con un'associazione diversa (stanze simmetriche, quasi quadrate, o
 * pareti il cui verso non è misurato) la sessione lo dice e non lascia confermare finché l'utente non verifica la partenza o
 * un'apertura dello schizzo. Nessun testo per l'utente: solo codici (le parole le mette la presentazione).
 */

/** Tipo di apertura indicata nello schizzo. */
enum class OpeningMark { DOOR, WINDOW, PASSAGE }

/** Un'apertura indicata nello schizzo, sul lato `side`. */
data class SketchOpening(val side: Int, val kind: OpeningMark)

/** Una parete confermata e associata a un lato. */
data class AssignedWall(val wallId: Int, val line: ConfirmedLine, val confirmedAtMs: Long, val advisories: List<Advisory>)

/** Perché un gesto è stato rifiutato (codice: il testo lo sceglie la presentazione). */
enum class SessionRejection {
    INVALID_SIDE,
    START_NOT_CHOSEN,
    ALL_SIDES_ASSIGNED,
    FIRST_MUST_BE_START,
    SIDE_ALREADY_ASSIGNED,
    NO_PROPOSAL,
    DUPLICATE_WALL,
    CONFIRM_FAILED,
    START_NOT_CONFIRMED,
    WALLS_ALREADY_ASSIGNED,
    SIDE_HAS_NO_WALL,
    NOTHING_TO_SHIFT,
    /** La superficie proposta non ha la direzione che lo schizzo prevede per il lato da scansionare. */
    OFF_SKETCH_DIRECTION,
}

/** Esito dell'ultimo gesto: fatto, oppure rifiutato con il motivo. */
sealed interface SessionEvent {
    data class StartChosen(val side: Int) : SessionEvent
    data class WallAssigned(val side: Int, val wallId: Int, val isStart: Boolean) : SessionEvent
    data class TargetChanged(val side: Int) : SessionEvent
    data class Reassigned(val from: Int, val to: Int) : SessionEvent
    data class Shifted(val by: Int) : SessionEvent
    data class Removed(val side: Int) : SessionEvent
    data class Rejected(val why: SessionRejection, val side: Int? = null) : SessionEvent
}

/** Riferimento che l'utente DICHIARA di aver usato per verificare la parete di partenza (non è una verifica del sistema). */
enum class StartReference { DOOR, WINDOW, OTHER }

/** La dichiarazione dell'utente sulla verifica della partenza; `note`: testo libero dell'utente (non tradotto). */
data class StartDeclaration(val reference: StartReference, val note: String = "")

/** Riga della tabella "lato dello schizzo ↔ parete", per mostrarla all'utente. */
data class AssociationRow(
    val side: Int,
    val wallId: Int?,
    val expectedOpenings: List<OpeningMark>,
    val isStart: Boolean,
    val isTarget: Boolean,
    /** Risposta alla verifica dell'apertura, se data. */
    val openingCheck: Answer?,
    /** Lato interno della parete misurato (false: verso non misurato, preso dallo schizzo). Null senza parete. */
    val inwardKnown: Boolean?,
)

/**
 * Un'altra associazione compatibile con la geometria (solo PROPOSTA, mai applicata da sola). `shift`: ogni parete spostata di
 * tanti lati; `reflectedAround`: se non null, ordine dei lati invertito (lato k → c − k).
 */
data class AssociationAlternative(val shift: Int, val reflectedAround: Int?) {
    fun map(side: Int, n: Int): Int = if (reflectedAround != null) mod(reflectedAround - side, n) else mod(side + shift, n)
}

/** Risultato completo per la revisione: geometria (con i problemi di associazione dentro) e tabella dell'associazione. */
data class SessionResult(
    val geometry: SketchGeometry,
    val associations: List<AssociationRow>,
    val alternatives: List<AssociationAlternative>,
    val startDeclaration: StartDeclaration?,
) {
    val canConfirm: Boolean get() = geometry.canConfirm
}

data class SketchSession(
    val sketch: RoomSketch,
    val openings: List<SketchOpening> = emptyList(),
    val engine: AssistedState = AssistedScan.initial(),
    val params: AssistedParams = AssistedParams(),
    val thresholds: SketchThresholds = SketchThresholds(),
    /** Lato di partenza scelto dall'utente (tocco sullo schizzo). */
    val startSide: Int? = null,
    /** La parete di partenza è stata confermata mentre l'utente la inquadrava. */
    val startConfirmed: Boolean = false,
    /** Il lato a cui va la prossima parete confermata. */
    val target: Int? = null,
    val assigned: Map<Int, AssignedWall> = emptyMap(),
    val manualLengthsM: Map<Int, Double> = emptyMap(),
    val acceptedSketchLengths: Set<Int> = emptySet(),
    val answers: Map<String, Answer> = emptyMap(),
    /** Riferimento dichiarato dall'utente per la verifica della partenza; senza, "ho verificato" non vale. */
    val startDeclaration: StartDeclaration? = null,
    val floorY: Double? = null,
    val lastEvent: SessionEvent? = null,
) {
    val n: Int get() = sketch.sideCount
    val candidate: CandidateWall? get() = engine.candidate

    /** Lato suggerito per partire: quello con la porta, solo se nello schizzo c'è UNA sola porta. È un suggerimento. */
    val suggestedStartSide: Int? get() = openings.filter { it.kind == OpeningMark.DOOR }.map { it.side }.distinct().singleOrNull()

    private fun reject(why: SessionRejection, side: Int? = null) = copy(lastEvent = SessionEvent.Rejected(why, side))

    fun chooseStartSide(side: Int): SketchSession {
        if (side !in 0 until n) return reject(SessionRejection.INVALID_SIDE, side)
        if (assigned.isNotEmpty()) return reject(SessionRejection.WALLS_ALREADY_ASSIGNED)
        return copy(startSide = side, target = side, startConfirmed = false, lastEvent = SessionEvent.StartChosen(side))
    }

    fun step(f: AimFrame): SketchSession {
        val r = AssistedScan.step(engine, f, params)
        return copy(engine = r.state, floorY = f.floorY ?: floorY, lastEvent = null)
    }

    /** "Conferma parete": la proposta del motore va al lato da scansionare. La prima deve essere la parete di partenza. */
    fun confirmWall(timeMs: Long): SketchSession {
        val start = startSide ?: return reject(SessionRejection.START_NOT_CHOSEN)
        val side = target ?: return reject(SessionRejection.ALL_SIDES_ASSIGNED)
        if (!startConfirmed && side != start) return reject(SessionRejection.FIRST_MUST_BE_START, start)
        if (side in assigned) return reject(SessionRejection.SIDE_ALREADY_ASSIGNED, side)
        val c = engine.candidate
        if (c == null || c.state != CandidateState.PROPOSED) return reject(SessionRejection.NO_PROPOSAL)
        if (c.duplicateOf != null) return reject(SessionRejection.DUPLICATE_WALL)
        if (!fitsSketch(c)) return reject(SessionRejection.OFF_SKETCH_DIRECTION, side)
        val sigOff = c.offsetStdM
        val sigDir = c.headingStdDeg
        val r = AssistedScan.confirm(engine, timeMs, params)
        if (r.state.walls.size == engine.walls.size) return reject(SessionRejection.CONFIRM_FAILED)
        val w = r.state.walls.last()
        val line = ConfirmedLine(w.line.a, w.line.b, w.normalIn, sigOff, sigDir, inwardKnown = w.cameraAt != null, supportAlongM = w.supportAlongM)
        val newAssigned = assigned + (side to AssignedWall(w.id, line, timeMs, w.advisories))
        val isStart = side == start && !startConfirmed
        return copy(
            engine = r.state, assigned = newAssigned, startConfirmed = startConfirmed || isStart, answers = dropGeometryAnswers(),
            target = nextFree(side, newAssigned), lastEvent = SessionEvent.WallAssigned(side, w.id, isStart),
        )
    }

    /**
     * Scarto in gradi (0–90) tra la direzione della superficie [c] e quella che lo schizzo prevede per il lato da scansionare,
     * con lo schizzo ruotato come le pareti già confermate (medoide circolare delle rotazioni, come nel nucleo: robusto a una
     * parete sbagliata). Solo la direzione della retta, senza verso. null se non si può dire: nessuna parete ancora (la prima fa
     * da riferimento), nessun lato da scansionare o nessuna superficie.
     */
    fun sketchDirectionGapDeg(c: CandidateWall? = candidate): Double? {
        val side = target ?: return null
        if (c == null || assigned.isEmpty()) return null
        fun lineHeading(a: com.sagoma.planimetria.scan.ArXZ, b: com.sagoma.planimetria.scan.ArXZ) = SketchAngles.headingDeg(b - a)
        // Rotazione schizzo → mondo di ogni parete confermata, riportata in [−90°, 90°) (le rette non hanno verso).
        fun wrap90(x: Double) = (((x % 180.0) + 270.0) % 180.0) - 90.0
        val rots = assigned.map { (k, w) -> wrap90(lineHeading(w.line.a, w.line.b) - SketchAngles.headingDeg(sketch.sideDirection(k))) }
        val rot = rots.minBy { r -> rots.sumOf { o -> kotlin.math.abs(wrap90(o - r)) } }
        val expected = SketchAngles.headingDeg(sketch.sideDirection(side)) + rot
        return SketchAngles.lineDeltaDeg(lineHeading(c.line.a, c.line.b), expected)
    }

    /** La superficie [c] può essere la parete del lato da scansionare (vedi [SketchThresholds.candidateDirectionDeg]). */
    fun fitsSketch(c: CandidateWall? = candidate): Boolean =
        sketchDirectionGapDeg(c)?.let { it <= thresholds.candidateDirectionDeg } ?: true

    /** Scegli il lato da scansionare dopo (si può andare in qualsiasi ordine, ma solo dopo la parete di partenza). */
    fun setTarget(side: Int): SketchSession {
        if (side !in 0 until n) return reject(SessionRejection.INVALID_SIDE, side)
        if (!startConfirmed) return reject(SessionRejection.START_NOT_CONFIRMED)
        if (side in assigned) return reject(SessionRejection.SIDE_ALREADY_ASSIGNED, side)
        return copy(target = side, lastEvent = SessionEvent.TargetChanged(side))
    }

    /** Sposta la parete del lato `from` sul lato `to` (se `to` è occupato le due si scambiano). La geometria non cambia da sola. */
    fun reassign(from: Int, to: Int): SketchSession {
        if (from !in 0 until n || to !in 0 until n || from == to) return reject(SessionRejection.INVALID_SIDE)
        val a = assigned[from] ?: return reject(SessionRejection.SIDE_HAS_NO_WALL, from)
        val m = assigned.toMutableMap()
        val b = m[to]
        m[to] = a
        if (b != null) m[from] = b else m.remove(from)
        val newStart = when (startSide) { from -> to; to -> if (b != null) from else startSide; else -> startSide }
        return copy(assigned = m, startSide = newStart, answers = dropAssociationAnswers(), target = nextFree(to, m), lastEvent = SessionEvent.Reassigned(from, to))
    }

    /** Sposta TUTTE le pareti di `by` lati (per esempio quando la partenza era stata identificata sul lato sbagliato). */
    fun shiftAll(by: Int): SketchSession {
        if (assigned.isEmpty() || mod(by, n) == 0) return reject(SessionRejection.NOTHING_TO_SHIFT)
        val m = assigned.entries.associate { (k, v) -> mod(k + by, n) to v }
        return copy(
            assigned = m, startSide = startSide?.let { mod(it + by, n) }, target = target?.let { mod(it + by, n) },
            answers = dropAssociationAnswers(), lastEvent = SessionEvent.Shifted(by),
        )
    }

    /** Toglie la parete di un lato (si può riconfermare). Togliere la parete di partenza riporta al primo passo. */
    fun removeWall(side: Int): SketchSession {
        val a = assigned[side] ?: return reject(SessionRejection.SIDE_HAS_NO_WALL, side)
        val m = assigned - side
        val eng = engine.copy(walls = engine.walls.filter { it.id != a.wallId }, candidate = null, track = null)
        val wasStart = side == startSide
        return copy(
            engine = eng, assigned = m, startConfirmed = startConfirmed && !wasStart, target = side,
            answers = dropAssociationAnswers(), lastEvent = SessionEvent.Removed(side),
        )
    }

    fun setManualLength(side: Int, meters: Double?): SketchSession {
        val m = if (meters == null) manualLengthsM - side else manualLengthsM + (side to meters)
        return copy(manualLengthsM = m, answers = if (m != manualLengthsM) dropGeometryAnswers() else answers, lastEvent = null)
    }

    fun acceptSketchLength(side: Int, accept: Boolean): SketchSession {
        val s = if (accept) acceptedSketchLengths + side else acceptedSketchLengths - side
        return copy(acceptedSketchLengths = s, answers = if (s != acceptedSketchLengths) dropGeometryAnswers() else answers, lastEvent = null)
    }

    fun answer(key: String, a: Answer?): SketchSession = copy(answers = if (a == null) answers - key else answers + (key to a), lastEvent = null)

    /** Dichiarazione dell'utente sul riferimento usato per verificare la partenza (null la toglie). */
    fun declareStart(d: StartDeclaration?): SketchSession = copy(startDeclaration = d, lastEvent = null)

    /**
     * C'è qualcosa che l'utente perderebbe uscendo: lato di partenza scelto, pareti confermate, risposte, misure a mano, stime
     * accettate o la dichiarazione sulla partenza. La schermata chiede conferma prima di uscire se è vero. Non modifica la sessione.
     */
    val hasUnsavedWork: Boolean
        get() = startSide != null || assigned.isNotEmpty() || answers.isNotEmpty() || manualLengthsM.isNotEmpty() ||
            acceptedSketchLengths.isNotEmpty() || startDeclaration != null

    // ---------- Risultato ----------

    fun result(): SessionResult {
        val lines = assigned.mapValues { it.value.line }
        val scanInput = SketchGeometryInput(sketch, lines, manualLengthsM, acceptedSketchLengths, answers, thresholds)
        val scanBase = SketchSolver.solve(scanInput)
        // Messa in squadra: si propone mostrando di quanto cambia la pianta; si applica solo se l'utente l'ha scelta.
        val squareOffer = SketchSquaring.offer(scanInput, scanBase)
        val squareChosen = squareOffer != null && answers[GeometryIssue.keyOf(IssueCode.ANGLES_NEAR_SQUARE, emptyList())] == Answer.SQUARE_ANGLES
        val base = if (squareChosen) squareOffer!!.squared else scanBase
        val extra = ArrayList<GeometryIssue>()
        val sessionRejected = ArrayList<RejectedAnswer>()
        fun add(code: IssueCode, sides: List<Int>, values: Map<String, Double> = emptyMap()) {
            val s = sides.distinct().sorted()
            val key = GeometryIssue.keyOf(code, s)
            var a = answers[key]?.takeIf { it in code.allowedAnswers }
            // "Ho verificato la partenza" vale solo con il riferimento dichiarato (porta, finestra, altro).
            if (a == Answer.START_WALL_VERIFIED && startDeclaration == null) {
                sessionRejected += RejectedAnswer(key, a, RejectionReason.MISSING_DECLARATION)
                a = null
            }
            extra += GeometryIssue(code, s, values, a)
        }
        if (!startConfirmed) add(IssueCode.START_NOT_CONFIRMED, listOfNotNull(startSide))
        squareOffer?.let { add(IssueCode.ANGLES_NEAR_SQUARE, emptyList(), mapOf("spostamentoMaxM" to it.maxShiftM, "scartoMaxDeg" to it.maxAngleDeviationDeg)) }
        // Verifica delle aperture dello schizzo sulle pareti associate: un riferimento indipendente dalle misure.
        val anchors = HashMap<Int, OpeningMark>()
        for ((side, _) in assigned.entries.sortedBy { it.key }) {
            val marks = openings.filter { it.side == side }.map { it.kind }.distinct()
            if (marks.isEmpty()) continue
            add(IssueCode.OPENING_CHECK, listOf(side))
            when (extra.last().answer) {
                Answer.OPENING_ON_THIS_WALL -> marks.forEach { anchors[side] = it }
                Answer.OPENING_NOT_ON_THIS_WALL -> add(IssueCode.ASSOCIATION_CONTRADICTED, listOf(side))
                else -> {}
            }
        }
        // Ambiguità: altre associazioni compatibili con la geometria e con le aperture verificate.
        val alternatives = if (startConfirmed) compatibleAlternatives(lines, anchors) else emptyList()
        if (alternatives.isNotEmpty()) add(IssueCode.ASSOCIATION_AMBIGUOUS, listOfNotNull(startSide), mapOf("alternative" to alternatives.size.toDouble()))
        // Risposte: quelle per i problemi della sessione non sono "rifiutate" dal nucleo; quelle non valide sì.
        val sessionKeys = extra.map { it.key }.toSet()
        val rejected = base.rejectedAnswers.filter { it.key !in sessionKeys } + sessionRejected + extra.mapNotNull { i ->
            val a = answers[i.key] ?: return@mapNotNull null
            if (a in i.code.allowedAnswers) null
            else RejectedAnswer(i.key, a, if (i.code.allowedAnswers.isEmpty()) RejectionReason.NEEDS_DATA_CHANGE else RejectionReason.NOT_ALLOWED)
        }
        val geometry = base.copy(issues = base.issues + extra, rejectedAnswers = rejected)
        val rows = (0 until n).map { k ->
            AssociationRow(
                k, assigned[k]?.wallId, openings.filter { it.side == k }.map { it.kind }, k == startSide, k == target,
                answers[GeometryIssue.keyOf(IssueCode.OPENING_CHECK, listOf(k))]?.takeIf { it in IssueCode.OPENING_CHECK.allowedAnswers },
                assigned[k]?.line?.inwardKnown,
            )
        }
        return SessionResult(geometry, rows, alternatives, startDeclaration)
    }

    /**
     * Le associazioni diverse da quella attuale (pareti spostate di s lati, oppure in ordine invertito) che la geometria NON
     * esclude. Un'alternativa è esclusa solo da un blocco "di associazione" ([ASSOCIATION_SENSITIVE]) che NON dipende da un
     * verso inferito ([excludes]). Le differenze dallo schizzo non contano: le lunghezze non bastano a decidere l'associazione.
     * Le aperture verificate devono finire su lati con la stessa apertura nello schizzo.
     */
    private fun compatibleAlternatives(lines: Map<Int, ConfirmedLine>, anchors: Map<Int, OpeningMark>): List<AssociationAlternative> {
        if (lines.isEmpty()) return emptyList()
        val candidates = (1 until n).map { AssociationAlternative(it, null) } + (0 until n).map { AssociationAlternative(0, it) }
        return candidates.filter { alt ->
            val moved = lines.entries.associate { (k, v) -> alt.map(k, n) to v }
            if (moved.size != lines.size) return@filter false
            val anchorsOk = anchors.all { (side, kind) -> openings.any { it.side == alt.map(side, n) && it.kind == kind } }
            if (!anchorsOk) return@filter false
            !excludes(SketchSolver.solve(SketchGeometryInput(sketch, moved, thresholds = thresholds)).blocking, inferredSides(moved, n))
        }
    }

    private fun nextFree(from: Int, m: Map<Int, AssignedWall>): Int? =
        (1..n).map { mod(from + it, n) }.firstOrNull { it !in m }

    /** Cambiando l'associazione, le risposte che riguardavano l'associazione precedente non valgono più (né la scelta degli angoli). */
    private fun dropAssociationAnswers(): Map<String, Answer> = dropGeometryAnswers().filterKeys { key ->
        !key.startsWith(IssueCode.OPENING_CHECK.name) && !key.startsWith(IssueCode.ASSOCIATION_AMBIGUOUS.name)
    }

    /**
     * Le risposte date su una pianta valgono per QUELLA pianta: cambiando una parete o una misura la pianta cambia, e le domande
     * che dipendono dalla geometria tornano aperte (scan6: scelta degli angoli fatta con 3 pareti, poi la quarta ha cambiato lo
     * spostamento da 15 a 7 cm). Restano le risposte che non dipendono dalla geometria (aperture, partenza verificata).
     */
    private fun dropGeometryAnswers(): Map<String, Answer> = answers.filterKeys { key -> GEOMETRY_DEPENDENT.none { key.startsWith(it.name + "[") } }

    companion object {
        /** Chiave della scelta sugli angoli ([IssueCode.ANGLES_NEAR_SQUARE]). */
        val ANGLE_CHOICE_KEY: String = GeometryIssue.keyOf(IssueCode.ANGLES_NEAR_SQUARE, emptyList())

        /** Domande la cui risposta dipende dalla pianta calcolata: si riaprono quando cambiano pareti o misure. */
        val GEOMETRY_DEPENDENT = setOf(
            IssueCode.ANGLES_NEAR_SQUARE, IssueCode.WALL_CONTINUES, IssueCode.SKETCH_DIRECTION_MISMATCH, IssueCode.SKETCH_LENGTH_MISMATCH,
            IssueCode.SKETCH_ANGLE_MISMATCH, IssueCode.SKETCH_VERTEX_FAR,
        )

        /** Blocchi che dipendono da QUALE parete sta su quale lato: se un'associazione li produce, può essere incompatibile. */
        val ASSOCIATION_SENSITIVE = setOf(
            IssueCode.SKETCH_DIRECTION_CONFLICT, IssueCode.INWARD_MISMATCH, IssueCode.SIDE_REVERSED, IssueCode.SELF_INTERSECTION,
            IssueCode.WRONG_ORIENTATION, IssueCode.OBSERVED_BEYOND_CORNER, IssueCode.NEARLY_PARALLEL, IssueCode.PARALLEL_NO_INTERSECTION,
            IssueCode.DUPLICATE_WALL, IssueCode.SPIKE_ANGLE, IssueCode.AREA_TOO_SMALL, IssueCode.VERTEX_OUT_OF_SCALE, IssueCode.DEGENERATE_SIDE,
        )

        /**
         * Blocchi calcolati SENZA il verso delle pareti (rette senza verso, angoli come incroci, segmenti tra angoli, valori
         * assoluti): escludono un'alternativa in ogni caso.
         */
        val VERSE_INDEPENDENT = setOf(
            IssueCode.SELF_INTERSECTION, IssueCode.AREA_TOO_SMALL, IssueCode.VERTEX_OUT_OF_SCALE,
            IssueCode.DUPLICATE_WALL, IssueCode.PARALLEL_NO_INTERSECTION, IssueCode.NEARLY_PARALLEL,
        )

        /**
         * Blocchi DIREZIONALI: usano il verso dei lati che indicano (SIDE_REVERSED, OBSERVED_BEYOND_CORNER, DEGENERATE_SIDE: il
         * lato; SPIKE_ANGLE: i due lati dell'angolo; WRONG_ORIENTATION: tutti). SKETCH_DIRECTION_CONFLICT e INWARD_MISMATCH
         * nascono solo da pareti con lato interno MISURATO (il nucleo non li calcola per le altre).
         */
        val DIRECTIONAL = ASSOCIATION_SENSITIVE - VERSE_INDEPENDENT

        /** Lati il cui verso è INFERITO: parete con lato interno non noto, o lato senza parete (direzione dallo schizzo). */
        fun inferredSides(lines: Map<Int, ConfirmedLine>, n: Int): Set<Int> =
            (0 until n).filter { k -> lines[k]?.inwardKnown != true }.toSet()

        /**
         * Regola prudente: un'alternativa è esclusa solo se c'è un blocco indipendente dal verso, oppure un blocco direzionale
         * che non tocca nessun lato dal verso inferito. Un blocco direzionale su un verso inferito NON esclude: l'alternativa resta
         * possibile e la sessione chiede la verifica sul posto. Nessuna soglia sul numero di pareti note.
         */
        fun excludes(blocking: List<GeometryIssue>, inferred: Set<Int>): Boolean = blocking.any { i ->
            when {
                i.code in VERSE_INDEPENDENT -> true
                i.code !in DIRECTIONAL -> false
                i.code == IssueCode.SKETCH_DIRECTION_CONFLICT || i.code == IssueCode.INWARD_MISMATCH -> i.sides.none { it in inferred }
                i.sides.isEmpty() -> inferred.isEmpty() // WRONG_ORIENTATION: dipende dal verso di tutti i lati
                else -> i.sides.none { it in inferred }
            }
        }
    }
}
