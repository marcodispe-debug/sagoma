package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ

/*
 * Percorso B (solo Pro): la stanza disegnata come schizzo e poi misurata con il telefono, lato per lato. Questo è il NUCLEO
 * GEOMETRICO: dati lo schizzo, le pareti confermate dall'utente e le misure a mano, calcola la pianta e la controlla.
 * Puro (niente Android, niente UI), deterministico, provabile sul computer.
 *
 * Regole che non si negoziano:
 *  - le rette confermate e le lunghezze a mano sono vincoli FISSI: non si spostano, non si mediano (unica eccezione: la messa in
 *    squadra degli angoli, solo se l'utente la sceglie esplicitamente, [Answer.SQUARE_ANGLES]);
 *  - lo schizzo riempie solo quello che manca e resta marcato come STIMA;
 *  - una geometria ambigua, incompatibile o numericamente instabile non si conferma: il risultato lo dice e si ferma.
 *
 * Unità: metri, nel mondo ARCore in pianta (x, z). Lo schizzo arriva in centimetri di Sagoma e si porta in metri con la stessa
 * corrispondenza di [com.sagoma.planimetria.scan.ScanCoordinates] (x → x, y → z): i due sistemi differiscono solo per una
 * rotazione e una traslazione, mai per uno specchio.
 */

/*
 * Nessun testo per l'utente qui: il nucleo dà CODICI, valori e motivi strutturati. Le parole (in qualsiasi lingua), i numeri
 * formattati e i plurali li mette lo strato di presentazione (`ui/.../sketch/SketchScanText`). I commenti sotto spiegano il
 * significato per chi sviluppa.
 */

/**
 * Da dove viene una misura. Una proposta della scansione NON confermata non entra mai nel nucleo: per questo non ha un valore
 * qui (la distinzione "proposta / confermata" vive nella sessione, che passa al nucleo solo le pareti confermate).
 */
enum class MeasureSource {
    /** Dallo schizzo: una stima, non una misura. */
    SKETCH_ESTIMATE,
    /** Dalla retta di una parete scansionata e confermata dall'utente. */
    SCAN_CONFIRMED,
    /** Lunghezza inserita a mano (metro). */
    MANUAL,
}

/** Gravità di un problema. */
enum class Severity {
    /** La pianta non si può confermare: serve un intervento (riscansione, misura, correzione dello schizzo). */
    BLOCKING,
    /** Serve una risposta esplicita dell'utente; finché manca, la pianta non si conferma. */
    QUESTION,
    /** Da mostrare nella revisione; non impedisce la conferma. */
    WARNING,
}

/** I problemi che il nucleo sa riconoscere; i valori numerici di ciascuno sono in [GeometryIssue.values]. */
enum class IssueCode(val severity: Severity, val allowedAnswers: Set<Answer> = emptySet()) {
    /** Dati non validi (valori non numerici, lati inesistenti, lunghezze non positive). */
    INVALID_INPUT(Severity.BLOCKING),
    /** Lo schizzo non è un poligono valido (lati nulli, lati che si incrociano, area nulla). */
    SKETCH_INVALID(Severity.BLOCKING),
    /** Nessuna parete confermata: senza misure non c'è una pianta da aggiornare. */
    NO_CONFIRMED_WALL(Severity.BLOCKING),
    /** La stessa parete confermata per due lati diversi. */
    DUPLICATE_WALL(Severity.BLOCKING),
    /** Due lati consecutivi paralleli: l'angolo tra loro non esiste. */
    PARALLEL_NO_INTERSECTION(Severity.BLOCKING),
    /** Due lati consecutivi quasi paralleli: l'angolo non è determinabile. */
    NEARLY_PARALLEL(Severity.BLOCKING),
    /** Mancano misure per i lati non scansionati (valore "misureMancanti"). */
    UNDERDETERMINED(Severity.BLOCKING),
    /** Calcolo dei lati non scansionati numericamente instabile. */
    UNSTABLE_SOLUTION(Severity.BLOCKING),
    /** Misure in contraddizione: la stanza non si chiude. */
    INCOMPATIBLE_MEASURES(Severity.BLOCKING),
    /** Misura a mano diversa da quella delle pareti confermate. */
    MANUAL_SCAN_CONFLICT(Severity.BLOCKING),
    /** Risultato numerico non valido. */
    NUMERIC(Severity.BLOCKING),
    /** Un angolo fuori scala, lontanissimo dalla stanza. */
    VERTEX_OUT_OF_SCALE(Severity.BLOCKING),
    /** Un angolo troppo incerto: riscansionare una delle due pareti vicino all'angolo. */
    VERTEX_UNSTABLE(Severity.BLOCKING),
    /** Un lato lungo quasi zero. */
    DEGENERATE_SIDE(Severity.BLOCKING),
    /** Un lato rovesciato rispetto allo schizzo. */
    SIDE_REVERSED(Severity.BLOCKING),
    /** Un angolo interno a punta, impossibile per una stanza. */
    SPIKE_ANGLE(Severity.BLOCKING),
    /** Il perimetro si incrocia. */
    SELF_INTERSECTION(Severity.BLOCKING),
    /** Il perimetro gira al contrario rispetto allo schizzo (schizzo speculare o pareti sbagliate). */
    WRONG_ORIENTATION(Severity.BLOCKING),
    /** Area della stanza troppo piccola. */
    AREA_TOO_SMALL(Severity.BLOCKING),
    /** Una parete confermata guarda verso l'esterno della stanza. */
    INWARD_MISMATCH(Severity.BLOCKING),
    /** La parte vista di una parete supera l'angolo: le pareti non possono incontrarsi lì. */
    OBSERVED_BEYOND_CORNER(Severity.BLOCKING),
    /** Nessuna parete con il lato interno noto: riconfermare una parete con il telefono tracciato. */
    INWARD_UNKNOWN_ALL(Severity.BLOCKING),
    /** Una parete in direzione opposta allo schizzo (oltre 90°): correggere lo schizzo o l'associazione. */
    SKETCH_DIRECTION_CONFLICT(Severity.BLOCKING),
    /** L'angolo cade molto oltre la parte vista: la parete continua dietro un mobile? */
    WALL_CONTINUES(Severity.QUESTION, setOf(Answer.WALL_CONTINUES_BEHIND)),
    /** La parete non ha la direzione prevista dallo schizzo. */
    SKETCH_DIRECTION_MISMATCH(Severity.QUESTION, setOf(Answer.KEEP_MEASURED)),
    /** Lunghezza misurata molto diversa dallo schizzo. */
    SKETCH_LENGTH_MISMATCH(Severity.QUESTION, setOf(Answer.KEEP_MEASURED)),
    /** Angolo misurato molto diverso dallo schizzo. */
    SKETCH_ANGLE_MISMATCH(Severity.QUESTION, setOf(Answer.KEEP_MEASURED)),
    /** Un angolo molto lontano da dove lo prevede lo schizzo. */
    SKETCH_VERTEX_FAR(Severity.QUESTION, setOf(Answer.KEEP_MEASURED)),
    /** Angolo incerto. */
    VERTEX_UNCERTAIN(Severity.WARNING),
    /** Alcune misure sono stime dello schizzo. */
    SKETCH_ESTIMATE_USED(Severity.WARNING),
    /** Lato interno della parete non noto: verso preso dallo schizzo, controllo del lato interno non eseguito. */
    INWARD_UNKNOWN(Severity.WARNING),
    // ---- Associazione pareti ↔ lati (sessione) ----
    /** Parete di partenza non identificata. */
    START_NOT_CONFIRMED(Severity.BLOCKING),
    /** Un'altra associazione delle pareti ai lati è compatibile: verificare la partenza sul posto (valore "alternative"). */
    ASSOCIATION_AMBIGUOUS(Severity.QUESTION, setOf(Answer.START_WALL_VERIFIED)),
    /** Il lato ha un'apertura nello schizzo: c'è davvero sulla parete confermata? */
    OPENING_CHECK(Severity.QUESTION, setOf(Answer.OPENING_ON_THIS_WALL, Answer.OPENING_NOT_ON_THIS_WALL)),
    /** L'apertura dello schizzo non è sulla parete associata: riassegnare o correggere lo schizzo. */
    ASSOCIATION_CONTRADICTED(Severity.BLOCKING),
    /** Una sola parete confermata: posizione della stanza lungo di essa per convenzione (centrata sulla parte vista). */
    POSITION_BY_CONVENTION(Severity.WARNING),
    /**
     * Schizzo ad angoli retti: l'utente sceglie se tenere gli angoli della scansione o approssimarli a 90°, vedendo di quanto
     * cambia la pianta (valori "spostamentoMaxM" e "scartoMaxDeg"; vedi [SketchSquaring]). Senza scelta la pianta non si conferma.
     */
    ANGLES_NEAR_SQUARE(Severity.QUESTION, setOf(Answer.KEEP_SCAN_ANGLES, Answer.SQUARE_ANGLES)),
}

/**
 * Le sole risposte che risolvono una domanda: ognuna è una DECISIONE dell'utente su quel problema, registrata e verificabile.
 * Le altre scelte possibili ("tengo la stima dello schizzo", "misuro a mano", "correggo lo schizzo", "cambio l'associazione")
 * non sono risposte: cambiano i dati in ingresso, e il problema sparisce solo se i dati nuovi lo risolvono davvero.
 */
enum class Answer {
    /** A [IssueCode.WALL_CONTINUES]: la parete prosegue nascosta fino all'angolo (il prolungamento è voluto). */
    WALL_CONTINUES_BEHIND,
    /** Alle discrepanze con lo schizzo: la misura è giusta, lo schizzo era impreciso. La geometria NON cambia. */
    KEEP_MEASURED,
    /**
     * Ad [IssueCode.ASSOCIATION_AMBIGUOUS]: DICHIARAZIONE dell'utente di aver verificato sul posto la parete di partenza. Vale
     * solo con il riferimento indicato (porta, finestra, altro): vedi `SketchSession.startDeclaration`.
     */
    START_WALL_VERIFIED,
    /** A [IssueCode.OPENING_CHECK]: l'apertura dello schizzo è su questa parete (dichiarazione dell'utente). */
    OPENING_ON_THIS_WALL,
    /** A [IssueCode.OPENING_CHECK]: l'apertura NON è qui. Risponde alla domanda ma crea un blocco: l'associazione va corretta. */
    OPENING_NOT_ON_THIS_WALL,
    /** Ad [IssueCode.ANGLES_NEAR_SQUARE]: si tengono gli angoli misurati. La geometria NON cambia. */
    KEEP_SCAN_ANGLES,
    /**
     * Ad [IssueCode.ANGLES_NEAR_SQUARE]: gli angoli si approssimano a 90°. È l'UNICO caso in cui le rette confermate si
     * muovono (ruotate attorno al centro della parte vista, vedi [SketchSquaring]), e solo per questa scelta esplicita.
     */
    SQUARE_ANGLES,
}

/** Perché una risposta non ha risolto niente. */
enum class RejectionReason {
    /** Nessun problema aperto con quella chiave (magari risolto o cambiato). */
    NO_SUCH_ISSUE,
    /** Il problema ammette risposte, ma non questa. */
    NOT_ALLOWED,
    /** Il problema non si risolve con una risposta: vanno corretti i dati. */
    NEEDS_DATA_CHANGE,
    /** Manca la dichiarazione del riferimento usato per verificare la partenza. */
    MISSING_DECLARATION,
}

/** Una risposta data a un problema che non la ammette (o a un problema che non c'è più): non risolve niente, si riporta. */
data class RejectedAnswer(val key: String, val answer: Answer, val reason: RejectionReason)


/**
 * Un problema trovato. `sides`: i lati coinvolti (indici dello schizzo). Una domanda ([Severity.QUESTION]) è risolta solo da una
 * delle risposte che il suo codice ammette ([IssueCode.allowedAnswers]), data per la sua [key]; blocchi e avvisi non ammettono
 * risposte: si risolvono solo cambiando i dati che li causano.
 */
data class GeometryIssue(
    val code: IssueCode,
    val sides: List<Int>,
    val values: Map<String, Double> = emptyMap(),
    /** La risposta valida che ha risolto la domanda; null se aperta (o se non è una domanda). */
    val answer: Answer? = null,
) {
    val severity: Severity get() = code.severity
    val key: String get() = keyOf(code, sides)
    val isOpenQuestion: Boolean get() = severity == Severity.QUESTION && answer == null

    companion object {
        fun keyOf(code: IssueCode, sides: List<Int>) = code.name + sides.sorted().joinToString(",", "[", "]")
    }
}

/**
 * Lo schizzo: angoli sul filo INTERNO, in metri, nell'ordine dei muri della stanza (il lato `k` va dall'angolo `k` all'angolo
 * `k + 1`, come i muri di una `Room`). Le misure sono stime.
 */
data class RoomSketch(val corners: List<ArXZ>) {
    val sideCount: Int get() = corners.size
    fun start(k: Int): ArXZ = corners[mod(k, corners.size)]
    fun end(k: Int): ArXZ = corners[mod(k + 1, corners.size)]
    fun sideLength(k: Int): Double = start(k).distanceTo(end(k))
    /** Direzione del lato, da inizio a fine (versore). */
    fun sideDirection(k: Int): ArXZ = (end(k) - start(k)).normalized()
    /** Area con segno (> 0: giro antiorario negli assi x, z). */
    val signedArea: Double get() = corners.indices.sumOf { corners[it].cross(corners[(it + 1) % corners.size]) } / 2

    companion object {
        /** Dallo schizzo in pianta di Sagoma (cm, filo interno) ai metri del nucleo, con la stessa corrispondenza della scansione. */
        fun fromPlanInterior(interiorCm: List<Vec2>): RoomSketch = RoomSketch(interiorCm.map { ArXZ(it.x / 100.0, it.y / 100.0) })
    }
}

/**
 * Una parete confermata dall'utente: il tratto VISTO `a`–`b` (sulla retta della parete), il lato verso l'interno della stanza
 * (visto da dove stava il telefono) e l'incertezza dichiarata dal motore al momento della conferma.
 */
data class ConfirmedLine(
    val a: ArXZ,
    val b: ArXZ,
    val inward: ArXZ,
    val sigmaOffsetM: Double,
    val sigmaDirectionDeg: Double,
    /**
     * Il lato interno viene dalla posizione del telefono alla conferma (dato fisico). Falso se il motore non la conosceva e ha
     * usato una normale di convenzione: allora [inward] non dice niente sul verso.
     */
    val inwardKnown: Boolean = true,
    /**
     * Punti della nuvola che sostengono la parete (metri lungo la retta da [a] verso [b], in ordine), se noti. Il tratto `a`–`b`
     * viene dal poligono di ARCore, che si gonfia quando ARCore fonde piani diversi: "visto oltre l'angolo" vale solo se lo
     * confermano anche i punti (vedi [SketchThresholds.overshootMinPoints]). Null = nessun dato: conta solo il tratto `a`–`b`.
     */
    val supportAlongM: List<Double>? = null,
) {
    val observedLengthM: Double get() = a.distanceTo(b)
}

/** Soglie iniziali e configurabili: NON sono garanzie di accuratezza. */
data class SketchThresholds(
    /** Lati consecutivi con meno di questo angolo tra loro: angolo non determinabile. */
    val minCornerAngleDeg: Double = 20.0,
    /** Sotto questo seno l'angolo è considerato nullo (parallelo esatto, divisione per zero). */
    val parallelEpsilon: Double = 1e-6,
    val vertexSigmaWarnM: Double = 0.05,
    val vertexSigmaBlockM: Double = 0.15,
    /** La parte vista può superare l'angolo al massimo di tanto (più due volte l'incertezza della retta). */
    val overshootTolM: Double = 0.05,
    /**
     * Con i punti della nuvola noti ([ConfirmedLine.supportAlongM]): per giudicare un tratto oltre l'angolo servono almeno tanti
     * punti tra gli angoli (per la densità) e almeno tanti punti ATTESI oltre l'angolo; se no conta il solo tratto `a`–`b`, come
     * prima (come il supporto minimo del motore).
     */
    val overshootMinPoints: Int = 10,
    /**
     * Il tratto oltre l'angolo è un artefatto del poligono di ARCore se ha meno di questa frazione dei punti attesi alla densità
     * della parete tra gli angoli. Un quarto: una parete vera che prosegue può essere vista peggio vicino all'angolo, ma non
     * quasi vuota (scan4: 0 su 163 e 3 su 21,5 negli artefatti; 8 su 7,4 in un tratto vero di 5 cm). Da verificare su altre
     * registrazioni.
     */
    val overshootDensityRatio: Double = 0.25,
    /**
     * Messa in squadra: la domanda si omette se tra le due piante nessun angolo si sposta di almeno tanto. NON è una tolleranza di
     * accuratezza: sotto il centimetro le due versioni sono identiche come l'app mostra le misure (in centimetri), quindi non c'è
     * niente da scegliere. Approvata dall'utente il 2026-10-12.
     */
    val squareMinShiftM: Double = 0.01,
    /** Uno schizzo è "ad angoli retti" se ogni suo angolo è entro tanto da 90° o 270°. */
    val squareSketchTolDeg: Double = 1.0,
    /** Prolungamento oltre la parte vista che fa chiedere "la parete continua?": oltre 1 m o oltre metà lato. */
    val prolongationAbsM: Double = 1.0,
    val prolongationRel: Double = 0.5,
    /** Due misure della stessa grandezza (a mano e dalle rette) devono coincidere entro tanto. */
    val redundancyTolM: Double = 0.03,
    val sketchLengthRel: Double = 0.15,
    val sketchAngleDeg: Double = 10.0,
    /**
     * Durante la scansione, dalla seconda parete in poi: una superficie la cui direzione si scosta più di tanto da quella che lo
     * schizzo prevede per il lato da scansionare non viene mostrata né si può confermare (per esempio una diagonale che taglia la
     * stanza). Approvata dall'utente il 2026-10-13, scelta sui dati: le pareti VERE confermate in scan5/scan6 si scostavano fino a
     * 5,4° (errore di direzione di ARCore, la stanza è in squadra entro 0,3°); 10° è circa il doppio.
     */
    val candidateDirectionDeg: Double = 10.0,
    val sketchVertexAbsM: Double = 0.5,
    val sketchVertexRel: Double = 0.3,
    val minSideM: Double = 0.10,
    val minInteriorAngleDeg: Double = 15.0,
    val minAreaM2: Double = 0.5,
    val maxVertexDistanceM: Double = 20.0,
    val maxScaleVsSketch: Double = 3.0,
    /** Due pareti confermate sono la stessa se direzione entro tanto e distanza entro tanto (come il motore). */
    val duplicateAngleDeg: Double = 6.0,
    val duplicateOffsetM: Double = 0.08,
    /** Pivot relativo minimo nella soluzione dei lati non scansionati: sotto, il sistema è singolare (mancano misure). */
    val pivotEpsilon: Double = 1e-9,
    /** Sopra [pivotEpsilon] ma sotto questo: risolvibile solo per poco, la soluzione non è affidabile (instabile). */
    val unstablePivot: Double = 1e-6,
)

/** Ingresso del nucleo. Le chiavi delle mappe sono gli indici dei lati dello schizzo. */
data class SketchGeometryInput(
    val sketch: RoomSketch,
    val confirmed: Map<Int, ConfirmedLine> = emptyMap(),
    val manualLengthsM: Map<Int, Double> = emptyMap(),
    /** Lati per cui l'utente ha accettato di usare la lunghezza dello schizzo (stima) dove manca una misura. */
    val acceptedSketchLengths: Set<Int> = emptySet(),
    /** Risposte dell'utente, per [GeometryIssue.key]. Valgono solo se il problema c'è e ammette quella risposta. */
    val answers: Map<String, Answer> = emptyMap(),
    val thresholds: SketchThresholds = SketchThresholds(),
)

/** Un lato calcolato, con l'origine della lunghezza e della direzione. */
data class SideResult(
    val index: Int,
    val lengthM: Double,
    val sketchLengthM: Double,
    /** Tutte le fonti da cui dipende la lunghezza: se c'è [MeasureSource.SKETCH_ESTIMATE] è una stima. */
    val lengthSources: Set<MeasureSource>,
    val directionSource: MeasureSource,
    /**
     * Solo pareti confermate: quanto il lato va oltre la parte vista (m). Null per i lati non scansionati e per la parete la cui
     * posizione è per convenzione ([IssueCode.POSITION_BY_CONVENTION]): lì il prolungamento non è un dato.
     */
    val prolongationM: Double?,
) {
    val isEstimate: Boolean get() = MeasureSource.SKETCH_ESTIMATE in lengthSources || directionSource == MeasureSource.SKETCH_ESTIMATE
}

/**
 * Un angolo calcolato: `index` è l'angolo tra il lato `index - 1` e il lato `index`. `positionByConvention`: la sua posizione
 * lungo l'unica parete confermata non è misurata ma fissata per convenzione (l'angolo interno resta misurato).
 */
data class CornerResult(
    val index: Int,
    val point: ArXZ,
    val interiorAngleDeg: Double,
    val sigmaM: Double?,
    val positionByConvention: Boolean = false,
)

/** Uscita del nucleo. `corners` è null quando la geometria non si può nemmeno calcolare. */
data class SketchGeometry(
    val corners: List<ArXZ>?,
    val sides: List<SideResult>,
    val cornerResults: List<CornerResult>,
    val issues: List<GeometryIssue>,
    /** Rotazione stimata schizzo → mondo AR (gradi); null senza pareti confermate. */
    val rotationDeg: Double?,
    /** Risposte date ma non valide (problema che non le ammette, o che non c'è): non hanno risolto niente. */
    val rejectedAnswers: List<RejectedAnswer> = emptyList(),
) {
    val blocking: List<GeometryIssue> get() = issues.filter { it.severity == Severity.BLOCKING }
    val openQuestions: List<GeometryIssue> get() = issues.filter { it.isOpenQuestion }
    val warnings: List<GeometryIssue> get() = issues.filter { it.severity == Severity.WARNING }
    val canConfirm: Boolean get() = corners != null && blocking.isEmpty() && openQuestions.isEmpty()
    val perimeterM: Double? get() = corners?.let { c -> c.indices.sumOf { c[it].distanceTo(c[(it + 1) % c.size]) } }
    val areaM2: Double? get() = corners?.let { c -> kotlin.math.abs(c.indices.sumOf { c[it].cross(c[(it + 1) % c.size]) } / 2) }
}

internal fun ArXZ.rotatedDeg(deg: Double): ArXZ {
    val r = deg * kotlin.math.PI / 180.0
    val c = kotlin.math.cos(r); val s = kotlin.math.sin(r)
    return ArXZ(x * c - z * s, x * s + z * c)
}

internal fun ArXZ.isFinite(): Boolean = x.isFinite() && z.isFinite()


internal fun mod(k: Int, n: Int): Int = ((k % n) + n) % n
