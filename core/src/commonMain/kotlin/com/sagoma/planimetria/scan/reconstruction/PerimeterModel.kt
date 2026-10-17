package com.sagoma.planimetria.scan.reconstruction

/*
 * R4 — modello del perimetro. Consuma le pareti R3 così come sono (nessuna modifica a [EstimatedWall]) e dice quali segmenti
 * appartengono allo stesso perimetro. Strutturalmente separati:
 *  - la MISURA (pareti R3, estremità osservate, angoli calcolati come intersezioni di linee misurate);
 *  - i tratti NON OSSERVATI (prolungamenti fino a un angolo, gap tra frammenti), sempre elencati con il motivo;
 *  - le IPOTESI (continuazioni possibili, lato mancante): marcate INFERRED / NOT OBSERVED, mai parte della misura;
 *  - le incertezze: misura, estremità, collegamento, chiusura, riconoscimento, qualità dell'evidenza.
 * Convenzione (da R3): la normale di ogni parete punta verso le camere; percorrendo ogni parete lungo +u (da start a end) l'interno
 * resta a destra. Un collegamento va quindi sempre dall'estremità END di una parete all'estremità START della successiva.
 */

/** Stato di un perimetro: [CLOSED] solo se tutte le condizioni di chiusura sono verificate; [UNCERTAIN] = un anello esiste ma almeno
 *  una condizione non è verificata (o la soluzione è ambigua); [PARTIAL] = catena aperta di più pareti; [OPEN] = nessun collegamento. */
enum class PerimeterState { CLOSED, PARTIAL, OPEN, UNCERTAIN }

/** Tipo di collegamento: [CORNER] = due linee che si incontrano in un angolo; [SAME_WALL] = due frammenti della stessa parete. */
enum class LinkKind { CORNER, SAME_WALL }

/** [OBSERVED] = le estremità arrivano all'angolo entro le loro incertezze; [INFERRED] = serve prolungare almeno un'estremità non
 *  osservata (il prolungamento NON è misura). Per [LinkKind.SAME_WALL] il gap è sempre elencato a parte. */
enum class LinkSupport { OBSERVED, INFERRED }

/** Esito della valutazione di un collegamento candidato. */
enum class LinkDecision { ACCEPTED, AMBIGUOUS, REJECTED }

/** Ruolo di una parete R3 rispetto al perimetro. */
enum class WallRole(val label: String) {
    PERIMETER("nel perimetro principale"),
    OTHER_CHAIN("collegata, ma in un'altra catena"),
    UNLINKED("nessun collegamento supportato"),
    BEYOND_OPENING("vista solo oltre un'apertura o oltre un'altra parete: candidata ambigua, fuori dal perimetro"),
}

/** Componenti del punteggio di un collegamento: ognuna in (0, 1], il punteggio è il loro prodotto. Tutte riportate. */
data class LinkComponents(
    /** Compatibilità geometrica delle estremità con l'angolo (o dei frammenti tra loro): exp(−z²/2) sulle parti osservate. */
    val geometry: Double,
    /** Frazione osservata dei lati che il collegamento produce: L_oss / (L_oss + prolungamento non osservato). */
    val observedFraction: Double,
    /** Stato delle estremità (OBSERVED/PARTIAL 1, UNCERTAIN e FREE_BEYOND ridotti). */
    val endpointState: Double,
    /** 1 − frazione dei campioni del tratto non osservato che lo spazio libero dice "visto attraverso". */
    val freeSpace: Double,
    /** Qualità dell'evidenza R3 delle due pareti (la peggiore). */
    val evidence: Double,
    /** Tratto non osservato spiegato da evidenza R2 vicina (oggetto davanti o superficie verticale sulla linea): 1, altrimenti ridotto. */
    val r2Support: Double,
)

/**
 * R4.1 — supporto di UNA estremità rispetto all'angolo, con le incertezze tenute separate:
 *  - [measurementSigmaM]: σ di misura dell'estremità (ripetibilità tra le viste, da R3.1);
 *  - [terminalSigmaM]: σ dovuta all'evidenza terminale mancante (R3.1): spiega dove potrebbe finire il muro, NON è evidenza;
 *  - [intersectionSigmaM]: σ della posizione dell'angolo lungo la linea (dalle σ delle due linee): NON è evidenza;
 *  - [gapToIntersectionM]: distanza dall'estremità osservata all'angolo (positiva = l'angolo è oltre i dati);
 *  - [observedEnd]: lo stato R3 è OBSERVED; [gapObserved]: il tratto fino all'angolo è coperto dai dati (gap ≤ 0) o, per
 *    un'estremità OBSERVED, è spiegato dalla sola σ dell'estremità; [inferredCorner]: l'angolo è DEDOTTO per questa estremità;
 *  - [measurementCompatible]: il gap è entro 3 σ di misura (solo diagnostica: NON rende osservata l'estremità).
 */
data class EndSupport(
    val endpointState: EndState,
    val endpointReason: EndReason,
    val observedEnd: Boolean,
    val measurementSigmaM: Double,
    val terminalSigmaM: Double,
    val intersectionSigmaM: Double,
    val gapToIntersectionM: Double,
    val gapObserved: Boolean,
    val inferredCorner: Boolean,
    val measurementCompatible: Boolean,
)

/**
 * Un collegamento candidato dall'estremità END di [fromWall] all'estremità START di [toWall]. Per gli angoli: punto d'angolo
 * ([cornerX], [cornerZ]) come intersezione delle due linee misurate; [extensionFromM]/[extensionToM] = quanto ciascuna estremità
 * osservata dista dall'angolo lungo la propria linea (positivo = serve prolungare, negativo = la parete osservata supera l'angolo),
 * con le σ propagate ([extensionFromSigmaM], [extensionToSigmaM]) e i rapporti z. Per i frammenti: gap, scarto laterale e angolare.
 */
data class WallLink(
    val fromWall: Int,
    val toWall: Int,
    val kind: LinkKind,
    val support: LinkSupport,
    val decision: LinkDecision,
    val score: Double,
    val components: LinkComponents,
    val turnDeg: Double,
    val turnSigmaDeg: Double,
    val cornerX: Double?, val cornerZ: Double?,
    /** σ della posizione dell'angolo (m, propagata dalle σ di posizione e direzione delle due linee). */
    val cornerSigmaM: Double?,
    val extensionFromM: Double, val extensionFromSigmaM: Double, val zFrom: Double,
    val extensionToM: Double, val extensionToSigmaM: Double, val zTo: Double,
    /** σ della posizione dell'angolo lungo ciascuna delle due linee (senza la σ dell'estremità). */
    val cornerAlongFromSigmaM: Double? = null, val cornerAlongToSigmaM: Double? = null,
    /** Solo [LinkKind.SAME_WALL]: scarto laterale tra le linee (m), sua σ e z, z dell'angolo tra le direzioni. */
    val lateralOffsetM: Double? = null, val lateralSigmaM: Double? = null, val zLateral: Double? = null, val zHeading: Double? = null,
    /** Lunghezza non osservata introdotta dal collegamento: prolungamenti positivi di estremità non osservate (anche entro 3σ) o gap tra
     *  frammenti. Nel perimetro sono elencati come tratti non osservati solo quelli oltre 3σ. */
    val unobservedM: Double,
    val gapReason: GapReason?,
    /** Superfici R2 vicine al tratto non osservato (id). */
    val r2Near: List<Int>,
    val reasons: List<String>,
    /** R4.1: supporto delle due estremità (solo angoli). */
    val fromEnd: EndSupport? = null,
    val toEnd: EndSupport? = null,
)

/** Tratto non osservato dentro un perimetro: prolungamento verso un angolo, gap tra frammenti o gap interno di una parete R3. */
data class UnobservedStretch(val wallId: Int, val ax: Double, val az: Double, val bx: Double, val bz: Double, val kind: String, val reason: String) {
    val lengthM: Double get() = kotlin.math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
}

/** Angolo del perimetro: punto, angolo interno misurato (NON imposto), σ propagate, supporto. */
data class PerimeterCorner(
    val fromWall: Int, val toWall: Int,
    val x: Double, val z: Double,
    val interiorAngleDeg: Double,
    val angleSigmaDeg: Double,
    val positionSigmaM: Double,
    val support: LinkSupport,
)

/** Lato mancante di un perimetro aperto: tra l'estremità END dell'ultima parete e la START della prima, con il motivo. NON creato. */
data class MissingSide(
    val afterWall: Int, val beforeWall: Int,
    val fromX: Double, val fromZ: Double, val fromState: EndState, val fromReason: EndReason,
    val toX: Double, val toZ: Double, val toState: EndState, val toReason: EndReason,
    val reason: String,
)

enum class HypothesisKind { POSSIBLE_CONTINUATION, MISSING_SIDE }

/** Ipotesi su ciò che non è osservato: SEMPRE INFERRED / NOT OBSERVED, separata dalla misura, mai una parete. */
data class PerimeterHypothesis(
    val kind: HypothesisKind,
    val perimeterId: Int,
    val afterWall: Int, val beforeWall: Int,
    /** Polilinea in pianta (x, z alternati). */
    val points: List<Double>,
    val lengthM: Double,
    val note: String,
) {
    val status: String get() = "INFERRED / NOT OBSERVED"
}

/** Verifica di chiusura: scarti tra estremità osservate e angoli, σ propagate, χ² normalizzato e probabilità. */
data class ClosureCheck(
    /** √(Σ scarti²) tra estremità osservate e angoli (più gli scarti laterali dei frammenti), m. */
    val errorM: Double,
    /** √(Σ σ²) degli stessi termini, m. */
    val propagatedSigmaM: Double,
    val chi2: Double,
    val dof: Int,
    /** P(χ²_dof ≥ chi2): compatibilità dell'errore con le incertezze propagate. */
    val pValue: Double,
    val compatible: Boolean,
    /** Somma delle svolte (deve essere −360° per un anello percorso con l'interno a destra) e sua σ propagata. */
    val turnSumDeg: Double,
    val turnSigmaDeg: Double,
    val simplePolygon: Boolean,
    /** R4.1: lunghezza totale dei tratti NON osservati del contorno (prolungamenti verso angoli dedotti, gap tra frammenti, gap R3). Termine separato:
     *  non entra nel χ² e non è mai trattata come errore di misura. */
    val unobservedM: Double = 0.0,
)

/** Incertezze del perimetro, separate per natura (R4.7). */
data class PerimeterUncertainty(
    /** Misura: σ massima di posizione delle pareti (m) e σ massima di direzione (°). */
    val maxPositionSigmaM: Double,
    val maxHeadingSigmaDeg: Double,
    /** Estremità: quante sono OBSERVED/PARTIAL/UNCERTAIN tra quelle coinvolte negli angoli o aperte. */
    val endsObserved: Int, val endsPartial: Int, val endsUncertain: Int,
    /** Collegamento: punteggio minimo e medio geometrico dei collegamenti. */
    val minLinkScore: Double, val meanLinkScore: Double,
    /** Chiusura: probabilità del test (null se non è un anello) e confidenza complessiva della chiusura. */
    val closureP: Double?, val closureConfidence: Double?,
    /** Riconoscimento (da R3): minimo e medio. */
    val minRecognition: Double, val meanRecognition: Double,
    /** Qualità dell'evidenza R3: la peggiore. */
    val worstEvidence: EvidenceQuality,
    /** σ del perimetro (m) e dell'area (m²), propagate dagli angoli (solo per anelli). */
    val perimeterSigmaM: Double?, val areaSigmaM2: Double?,
)

class Perimeter(
    val id: Int,
    val state: PerimeterState,
    /** Pareti R3 nell'ordine di percorrenza (interno a destra). */
    val wallIds: List<Int>,
    val links: List<WallLink>,
    val corners: List<PerimeterCorner>,
    val unobserved: List<UnobservedStretch>,
    val missing: MissingSide?,
    val closure: ClosureCheck?,
    val uncertainty: PerimeterUncertainty,
    /** Lunghezza osservata (somma dei tratti osservati R3) e lunghezza dei lati tra gli angoli (solo anelli). */
    val observedLengthM: Double,
    val perimeterLengthM: Double?,
    val areaM2: Double?,
    /** Alternative ugualmente plausibili che impediscono una decisione (descrizioni). */
    val alternatives: List<String>,
    val reasons: List<String>,
)

data class WallAssessment(val wallId: Int, val role: WallRole, val perimeterId: Int?, val reasons: List<String>)

class PerimeterResult(
    val state: PerimeterState,
    val main: Perimeter?,
    val perimeters: List<Perimeter>,
    /** Tutti i collegamenti valutati (anche rifiutati, con il motivo). */
    val candidates: List<WallLink>,
    val walls: List<WallAssessment>,
    val hypotheses: List<PerimeterHypothesis>,
    val diagnostics: List<String>,
)
