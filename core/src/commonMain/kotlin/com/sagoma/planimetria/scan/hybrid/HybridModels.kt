package com.sagoma.planimetria.scan.hybrid

import com.sagoma.planimetria.scan.ArXZ

/*
 * PROTOTIPO del metodo ibrido (docs/protocollo-verifica-pareti.md, sezione P0): la stanza come pavimento, soffitto e pareti
 * ricostruiti DALLA PROFONDITÀ, a partire da riferimenti confermati dall'utente, con lo schizzo come guida. Separato dalla
 * scansione guidata (scan/sketch), che non cambia. Nessun testo per l'utente: solo codici e numeri.
 */

/**
 * Soglie del prototipo. Sono le tolleranze INIZIALI di P0 (ipotesi da verificare, NON precisioni garantite); il motivo di ognuna è
 * nel protocollo. Le uniche già approvate per l'app sono [sketchDirectionDeg] (2026-10-13) e le soglie di confronto con lo schizzo
 * ([sketchLengthRel], [sketchAngleDeg], [minCornerAngleDeg]), riprese dal nucleo della scansione guidata.
 */
data class HybridThresholds(
    /** Riferimento: fotogrammi di profondità recenti usati per stimarlo (finestra mobile) e minimo per accettarlo. */
    val refWindowFrames: Int = 6,
    val refMinFrames: Int = 3,
    /**
     * Riferimento: solo i fotogrammi dell'ultimo secondo e mezzo, e solo quelli che vedono lo STESSO piano dell'ultimo (entro le
     * tolleranze "stesso piano" dei frammenti: 3° e 3 cm). Prima prova (2026-10-13): unendo fotogrammi mentre il telefono si muove la
     * dispersione saliva da 2,4 a 6,5 cm (mediane). Non è una tolleranza geometrica nuova: è il modo di raccogliere i fotogrammi.
     */
    val refWindowMs: Long = 1500,
    /** Riferimento: punti minimi nella zona centrale dell'immagine. */
    val refMinPoints: Int = 300,
    /** Riferimento: dispersione massima (MAD × 1,4826). Pareti 4 cm (rumore osservato 2–3 cm sul bagno); pavimento/soffitto 6 cm (4 cm osservati). */
    val refMaxSigmaWallM: Double = 0.04,
    val refMaxSigmaHorizontalM: Double = 0.06,
    /** Riferimento: normale entro tanto dall'orizzontale (pareti) o dalla verticale (pavimento, soffitto). */
    val refMaxTiltDeg: Double = 10.0,
    /** Un punto entra in una parete se dista dal riferimento al massimo max(6 cm, 3σ del riferimento). */
    val wallGateMinM: Double = 0.06,
    val wallGateSigmas: Double = 3.0,
    /** Un punto entra in pavimento/soffitto se dista in quota al massimo max(8 cm, 2σ del riferimento). */
    val horizontalGateMinM: Double = 0.08,
    val horizontalGateSigmas: Double = 2.0,
    /** Frammento accettato: mediana delle distanze entro 3 cm e inclinazione entro 3° (minimi già usati da R3 per unire). */
    val fragmentMaxOffsetM: Double = 0.03,
    val fragmentMaxAngleDeg: Double = 3.0,
    /** Oltre tanto il frammento non è lo stesso piano (non è nemmeno dubbio). */
    val fragmentDoubtAngleDeg: Double = 10.0,
    /** L'inclinazione di un frammento si valuta solo se misura almeno tanto nelle due direzioni. */
    val fragmentAngleMinExtentM: Double = 0.30,
    val fragmentMinPoints: Int = 200,
    val fragmentMinFrames: Int = 3,
    /** Celle dei frammenti: lato e punti minimi per contarle. */
    val cellM: Double = 0.10,
    val cellMinPoints: Int = 5,
    /**
     * Giudizio PER CELLA (decisione dell'utente, P0.18): una cella con almeno tanti punti da [fragmentMinFrames] immagini è
     * accettata se la sua mediana sta entro [fragmentMaxOffsetM] dal riferimento, dubbia se oltre; con meno punti resta da
     * completare. PROVVISORIA (non tratta da dati).
     */
    val cellJudgeMinPoints: Int = 20,
    /** Punti di parete: tra pavimento + 10 cm e soffitto − 10 cm (senza soffitto: fino a pavimento + 2,5 m). */
    val wallBandMarginM: Double = 0.10,
    val wallTopWithoutCeilingM: Double = 2.5,
    /** Superficie parallela "dietro" la candidata: tra 10 cm e 1 m più lontana, con almeno tanti punti (indizio di mobile). */
    val behindMinM: Double = 0.10,
    val behindMaxM: Double = 1.0,
    val behindMinPoints: Int = 300,
    /** Schizzo: direzione prevista (approvata il 2026-10-13), lunghezze e angoli per segnalare le discrepanze (soglie esistenti). */
    val sketchDirectionDeg: Double = 10.0,
    val sketchLengthRel: Double = 0.15,
    val sketchAngleDeg: Double = 10.0,
    val minCornerAngleDeg: Double = 20.0,
    /** Senza profondità valida da tanto, si dice chiaramente che mancano i dati. */
    val noDepthTimeoutMs: Long = 3000,
    /**
     * Salto di posizione: spostamento della camera oltre tanto tra due fotogrammi consecutivi (circa 33 ms). PROVVISORIA: una mano
     * che muove il telefono raramente supera 1,5 m/s (5 cm per fotogramma); 10 cm è il doppio. Nella prima prova (2026-10-13)
     * ARCore ha fatto 17 salti oltre 10 cm, fino a 2 m.
     */
    val poseJumpM: Double = 0.10,
    /** Rotazione brusca: orientamento cambiato di oltre tanto tra due fotogrammi (oltre 300°/s). PROVVISORIA. */
    val poseJumpRotationDeg: Double = 10.0,
    /** Una conferma vale per l'ultimo candidato pronto fino a tanto fa (tra la vista e il tocco può arrivare un'immagine instabile). */
    val confirmGraceMs: Long = 1500,
    /** Riallineamento: le pareti riconfermate devono dare la stessa rotazione entro tanto (NUOVA, provvisoria). */
    val realignMaxDisagreeDeg: Double = 5.0,
    /**
     * "Solo punti buoni" (ESPLORATIVA): distanza massima, confidenza minima, distanza massima dal centro dell'immagine, angolo di
     * vista massimo rispetto alla perpendicolare della superficie. Vale solo per le superfici in [HybridScan.goodPointKinds].
     * Dall'analisi della prima prova (2026-10-13): pavimento da 3,2 a 2,2 cm di dispersione con 2 m, 224, 0,75 e 30°; sulle pareti
     * inquadrate di sbieco restano pochissimi punti. Da verificare su altre registrazioni.
     * [pointMaxImageRadius] null = immagine intera: il riferimento si prende al centro, poi l'area si allarga tenendo solo i punti
     * buoni (decisione dell'utente, 2026-10-13 sera; sul pavimento, seconda prova: area da 7,7 a 8,5 m², dispersione da 1,6 a 1,9 cm).
     */
    val pointMaxRangeM: Double = 2.0,
    val pointMinConfidence: Int = 224,
    val pointMaxImageRadius: Double? = null,
    val pointMaxIncidenceDeg: Double = 30.0,
)

/** Le fasi del prototipo, nell'ordine. */
enum class HybridPhase { START_SIDE, FLOOR, CEILING, WALLS, REVIEW }

enum class SurfaceKind { FLOOR, CEILING, WALL }

/** Stato della superficie inquadrata al centro, candidata a riferimento. */
enum class CandidateStatus {
    /** Nessun dato di profondità valido di recente: il prototipo non sostituisce la profondità con i piani di ARCore. */
    NO_DEPTH,
    /** Pochi fotogrammi o pochi punti al centro dell'immagine. */
    TOO_FEW_POINTS,
    /** Punti troppo dispersi: non è una superficie piana (o la profondità è rumorosa). */
    NOT_PLANAR,
    /** Gli ultimi fotogrammi non vedono lo stesso piano: tenere fermo il telefono un istante. */
    UNSTABLE,
    /** Orientamento sbagliato per questa fase (per esempio una parete mentre si chiede il pavimento). */
    WRONG_ORIENTATION,
    /** Pavimento sopra la camera o soffitto sotto. */
    WRONG_SIDE,
    /** È una parete già acquisita. */
    ALREADY_ACQUIRED,
    /** Parallela a una parete già acquisita ma spostata oltre la tolleranza: probabilmente un mobile o una nicchia. */
    PARALLEL_OFFSET,
    /**
     * Direzione diversa oltre 10° da quella che lo schizzo prevede per il lato da acquisire: la superficie NON si considera (niente
     * disegno, niente avviso, niente conferma; decisione dell'utente del 2026-10-13).
     */
    OFF_SKETCH,
    /** Dietro c'è un'altra superficie parallela: probabilmente un mobile davanti al muro. */
    SURFACE_BEHIND,
    /** Va bene: si può confermare come riferimento. */
    READY,
}

/** Piano: normale unitaria [nx], [ny], [nz] e punto [cx], [cy], [cz] (mondo ARCore, metri); [sigmaM] dispersione robusta. */
data class PlaneEstimate(
    val nx: Double, val ny: Double, val nz: Double,
    val cx: Double, val cy: Double, val cz: Double,
    val sigmaM: Double, val points: Int, val frames: Int,
) {
    fun distance(x: Double, y: Double, z: Double) = (x - cx) * nx + (y - cy) * ny + (z - cz) * nz
}

/** La superficie al centro dell'inquadratura, valutata per la fase in corso. */
data class ReferenceCandidate(
    val status: CandidateStatus,
    val plane: PlaneEstimate?,
    /** Inclinazione dall'orizzontale (pareti) o dalla verticale (pavimento/soffitto), gradi. */
    val tiltDeg: Double? = null,
    /** Lato a cui andrebbe la parete (fase pareti). */
    val side: Int? = null,
    /** Lato coinvolto (già acquisito, parallelo) e scostamento: gradi rispetto allo schizzo o metri di spostamento. */
    val otherSide: Int? = null,
    val deviationDeg: Double? = null,
    val offsetM: Double? = null,
    /** Distanza dalla parete opposta già acquisita e quella dello schizzo (solo per mostrarle). */
    val oppositeDistanceM: Double? = null,
    val sketchOppositeDistanceM: Double? = null,
    /**
     * La superficie da disegnare: i 4 vertici (mondo ARCore) del tratto inquadrato sul piano STIMATO, con la sua inclinazione vera
     * (non raddrizzata), per giudicarla prima di confermare. null se la superficie non si considera.
     */
    val patch: List<com.sagoma.planimetria.scan.ArPoint>? = null,
    /** Parete adiacente già acquisita e angolo interno tra le due (gradi), con quello dello schizzo. */
    val adjacentSide: Int? = null,
    val adjacentAngleDeg: Double? = null,
    val sketchAdjacentAngleDeg: Double? = null,
)

enum class FragmentStatus { ACCEPTED, DOUBTFUL, INSUFFICIENT }

/** Un frammento di superficie: celle collegate sul piano di riferimento. */
data class FragmentInfo(
    val status: FragmentStatus,
    val points: Int,
    val frames: Int,
    val medianOffsetM: Double,
    val angleDeg: Double?,
    /** Estensione: lungo la parete (o x) e in altezza (o z), metri; centro nel mondo. */
    val extentAM: Double,
    val extentBM: Double,
    val centerX: Double, val centerY: Double, val centerZ: Double,
)

/**
 * Un tratto visto di una superficie, per colorarla dal vivo mentre si scansiona: celle consecutive della stessa riga con lo stesso
 * stato del frammento a cui appartengono (accettato = registrato come buono, dubbio, ancora insufficiente). Quattro vertici sul
 * piano di riferimento, nel sistema ORIGINALE delle superfici (per disegnarli si applica [HybridSnapshot.world]).
 */
data class CoverageQuad(val kind: SurfaceKind, val side: Int?, val status: FragmentStatus, val corners: List<com.sagoma.planimetria.scan.ArPoint>)

enum class WallState {
    /** Nessun riferimento. */
    MISSING,
    /** Riferimento confermato, ma ancora nessun frammento accettato. */
    COLLECTING,
    /** Almeno un frammento accettato: c'è una retta misurata. */
    MEASURED,
}

/** Una parete del prototipo, legata a un lato dello schizzo. */
data class WallSnapshot(
    val side: Int,
    val state: WallState,
    val reference: PlaneEstimate?,
    /** Il riferimento è stato confermato nonostante un avviso (schizzo, superficie dietro): associazione incerta. */
    val overriddenWarning: CandidateStatus?,
    val fragments: List<FragmentInfo>,
    /** Retta misurata in pianta: punto e direzione unitaria; estremi osservati lungo la retta. */
    val point: ArXZ?,
    val direction: ArXZ?,
    val fromU: Double?,
    val toU: Double?,
    /** Lato interno: verso le camere che l'hanno vista. */
    val inward: ArXZ?,
    /** Dispersione dei punti accettati attorno alla retta (non è un'incertezza della retta). */
    val residualM: Double?,
    /** Scarto della direzione misurata da quella prevista dallo schizzo (gradi), se calcolabile. */
    val sketchDeviationDeg: Double?,
    /** Punti raccolti attorno al riferimento (con "solo punti buoni" attivo: solo quelli buoni). */
    val points: Int = 0,
)

data class HorizontalSnapshot(
    val kind: SurfaceKind,
    val reference: PlaneEstimate?,
    /** Quota misurata (mediana dei punti dei frammenti accettati), metri ARCore. */
    val heightY: Double?,
    /** Estensione ricostruita: area delle celle dei frammenti accettati. È l'estensione, NON la precisione del bordo. */
    val areaM2: Double,
    /** Area VISTA: tutte le celle con punti vicino al riferimento, accettate o no (per dare un riscontro mentre si percorre). */
    val seenAreaM2: Double = 0.0,
    /** Punti raccolti attorno al riferimento (con "solo punti buoni" attivo: solo quelli buoni). */
    val points: Int = 0,
    val fragments: List<FragmentInfo>,
    /** Celle accettate (centro x, z) per disegnare l'estensione. */
    val cells: List<ArXZ>,
)

enum class DiscrepancyKind { LENGTH, ANGLE, DIRECTION, MISSING_WALL, UNCERTAIN_ASSOCIATION, HEIGHT }

/** Differenza tra dati e schizzo: si mostra, non si corregge. */
data class Discrepancy(
    val kind: DiscrepancyKind,
    val sides: List<Int>,
    val measured: Double?,
    val expected: Double?,
)

enum class BoxState {
    /** Mancano pareti misurate. */
    INCOMPLETE,
    /** Tutte le pareti misurate, ma almeno un'associazione è incerta (avviso superato). */
    UNCERTAIN,
    /** Angoli non definiti (pareti adiacenti quasi parallele). */
    UNDEFINED_CORNER,
    /** Tutte le pareti misurate, angoli definiti, nessuna associazione incerta. */
    COMPLETE,
}

/** Quanto arriva dalla profondità, per dire chiaramente quando mancano dati. */
data class DepthHealth(
    val framesReceived: Int,
    val lastFrameValidPoints: Int,
    val lastFramePixels: Int,
    val msSinceValid: Long?,
)

/** Fotografia immutabile del motore, per l'interfaccia. */
data class HybridSnapshot(
    val phase: HybridPhase,
    val sides: Int,
    val startSide: Int?,
    val targetSide: Int?,
    val candidate: ReferenceCandidate,
    val floor: HorizontalSnapshot,
    val ceiling: HorizontalSnapshot,
    val ceilingSkipped: Boolean,
    val walls: List<WallSnapshot>,
    /** Angolo k = inizio del lato k (intersezione dei lati k−1 e k); null se una delle due pareti non è misurata. */
    val corners: List<ArXZ?>,
    val box: BoxState,
    val discrepancies: List<Discrepancy>,
    /** Altezza misurata (soffitto − pavimento) e quella dichiarata nello schizzo. */
    val heightM: Double?,
    val sketchHeightM: Double?,
    val depth: DepthHealth,
    /** Rotazione schizzo → mondo stimata dalle pareti (gradi), null senza pareti. */
    val sketchRotationDeg: Double?,
    /**
     * Dopo un salto di posizione: posizione da verificare (la raccolta continua); salti finora; scarto (m) dell'ultima superficie
     * confermata vista spostata; fotogrammi scartati perché spostati e fotogrammi usati senza poterli verificare.
     */
    val unverified: Boolean = false,
    val jumps: Int = 0,
    val lastDisplacementM: Double? = null,
    val discardedFrames: Int = 0,
    val uncheckedFrames: Int = 0,
    /** Riallineamento in corso: parete da riconfermare, già riconfermate, quante servono (0 = nessun riallineamento). */
    val realignTarget: Int? = null,
    val realignDone: List<Int> = emptyList(),
    val realignNeeded: Int = 0,
    /** Dal sistema delle superfici al mondo attuale di ARCore (per disegnarle sopra la scena). */
    val world: Rigid = Rigid(),
    /** L'ultima riconferma del riallineamento è stata rifiutata: le pareti non tornavano tra loro (si ricomincia dalla prima). */
    val realignMismatch: Boolean = false,
)
