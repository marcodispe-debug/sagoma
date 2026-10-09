package com.sagoma.planimetria.scan.reconstruction.rescan.session

import kotlinx.serialization.Serializable

/*
 * M5.1 — RESCAN SESSION. Memoria tra scansioni dei BERSAGLI FISICI delle richieste M5 (estremità, tratti, perimetro, viste, misura,
 * ambiguità di una parete), non degli id temporanei delle richieste. M5 resta invariato e senza memoria: la sessione lo affianca.
 *
 * Regole approvate:
 *  - stesso sistema di coordinate SOLO se la registrazione corrente CONTIENE la precedente come prefisso (verificabile); altrimenti
 *    COORDINATE_FRAME_MISMATCH: nessun confronto geometrico, i bersagli correnti sono NEW;
 *  - pareti ritrovate con le tolleranze approvate (angolo ≤ 1,5°, posizione ≤ 3 cm + 3σ combinata, estensioni sovrapposte o a
 *    ≤ 20 cm); nessuna soglia nuova;
 *  - ATTEMPTED = "è stata elaborata una nuova scansione con il bersaglio aperto": NON prova che l'utente abbia guardato lì;
 *  - mai RESOLVED senza prova; bersaglio non ritrovato → PERSISTENT (NOT_REFOUND); valore non misurabile → PERSISTENT;
 *  - nessun EXHAUSTED, nessuna richiesta nascosta o eliminata, nessun limite al numero di tentativi.
 * I tempi sono solo metadati: non entrano in nessuna decisione.
 */

@Serializable
enum class TargetState { NEW, PROPOSED, ATTEMPTED, IMPROVED, RESOLVED, PERSISTENT }

@Serializable
enum class TargetKind { WALL_ENDPOINT, WALL_SEGMENT, PERIMETER, WALL_VIEWS, WALL_MEASUREMENT, WALL_AMBIGUITY }

/** Motivo codificato di ogni transizione (mai testo libero). */
@Serializable
enum class TransitionReason {
    FIRST_SEEN, EMITTED, NEW_SCAN_PROCESSED, REAPPEARED,
    ENDPOINT_OBSERVED, UNOBSERVED_BELOW_MIN, UNOBSERVED_LENGTH_REDUCED, SEGMENT_OBSERVED,
    PERIMETER_CLOSED, PERIMETER_STATE_ADVANCED, MISSING_SIDE_REDUCED,
    VIEWS_SUFFICIENT, MEASUREMENT_WITHIN_TOLERANCE, AMBIGUITY_BELOW_TAU, AMBIGUITY_STABILIZED,
    NO_MEASURABLE_IMPROVEMENT, UNOBSERVED_NOT_MEASURABLE, NOT_REFOUND,
    // M5.1.1
    /** Estremità INTERNA a una fusione: il vecchio punto è ora dentro un tratto osservato della parete fusa (prova di risoluzione). */
    INTERNAL_COVERAGE,
    /** Estremità interna a una fusione: il vecchio punto è ora in un gap della parete fusa (il gap è seguito come tratto). */
    BECAME_INTERNAL_GAP,
    /** Parete ritrovata, ma il bersaglio non è ricondotto a niente di verificabile (nessun frammento lo raggiunge, o evidenza insufficiente). */
    TARGET_NOT_VERIFIABLE,
    /** Dopo una fusione lo stesso difetto è seguito da un altro bersaglio (relatedTargetKey); questo non è risolto. */
    COVERED_BY_OTHER_TARGET,
    /** Il tratto non osservato c'è ancora, ma ora confina con un'estremità aperta ed è seguito da quel bersaglio (relatedTargetKey). */
    SEGMENT_ABSORBED_BY_ENDPOINT,
    // M5.1.2
    /** Alternative plausibili con conclusioni incompatibili (o un'alternativa non osservata): il bersaglio resta aperto. */
    AMBIGUOUS_CANDIDATES,
    /** La direzione verso l'esterno dell'estremità non è determinabile o è contraddetta: nessuna decisione, rivalutata. */
    DIRECTION_UNDETERMINED,
}

/** Esito del confronto di una parete tra due scansioni. */
@Serializable
enum class MatchStatus { MATCH_FOUND, NO_GEOMETRIC_MATCH, AMBIGUOUS_MATCH, SPLIT, MERGE, COORDINATE_FRAME_MISMATCH, FIRST_SCAN, REFOUND }

/** M5.1.2 — stato dell'identità persistente di una parete: ABSORBED = fusa (MERGE dimostrato) in un'altra, non più confrontata. */
@Serializable
enum class WallState { ACTIVE, ABSORBED }

/** Ruolo di un frammento R3 rispetto a una parete persistente in una scansione. */
@Serializable
enum class FragmentRole { PRIMARY, SPLIT_PART, AMBIGUOUS_ALTERNATIVE, NEAREST_REJECTED }

/** Un frammento R3: l'id R3 non compare mai senza la sua scansione (gli id R3 valgono solo dentro una scansione). */
@Serializable
data class FragmentRef(val scanIndex: Int, val r3WallId: Int)

/** Geometria di un frammento usata come riferimento della parete persistente nelle scansioni successive. */
@Serializable
data class FragmentGeometry(
    val ref: FragmentRef,
    val nx: Double, val nz: Double, val cx: Double, val cz: Double, val ux: Double, val uz: Double,
    val startU: Double, val endU: Double,
    val positionSigmaM: Double,
    val observedLengthM: Double,
    val role: FragmentRole,
)

/** Frammento candidato di una parete persistente, con le misure della regola approvata. */
@Serializable
data class CandidateLink(
    val fragment: FragmentRef,
    val role: FragmentRole,
    val deltaPositionM: Double,
    val toleranceM: Double,
    val deltaAngleDeg: Double,
    val overlapM: Double,
    val failedRules: List<String> = emptyList(),
)

@Serializable
enum class WallRelationKind { AMBIGUOUS_WITH }

/** Base dell'evidenza di una relazione: distinzione dimostrata (co-osservate sovrapposte), merge non dimostrato, o dedotta (sessioni vecchie). */
@Serializable
enum class RelationBasis { CO_OBSERVED_OVERLAP, INSUFFICIENT_MERGE_EVIDENCE, LEGACY_INFERRED }

/** Relazione tra due pareti persistenti in una scansione (a < b per numero). Non è un'identità: le pareti restano distinte. */
@Serializable
data class WallRelation(val scanIndex: Int, val kind: WallRelationKind, val a: String, val b: String, val basis: RelationBasis)

/** Verdetto di un frammento ammissibile (o di un'alternativa) per un bersaglio, in una scansione. */
@Serializable
enum class Verdict { OPEN, OBSERVED, BELOW_MIN, NOT_REACHABLE, NOT_OBSERVED }

@Serializable
enum class AlternativeStatus { ACTIVE, DISAMBIGUATED }

/**
 * Alternativa sospesa di un bersaglio: un altro frammento plausibile per lo stesso difetto. Persistente, rivalutata a ogni scansione;
 * DISAMBIGUATED solo con evidenza positiva (la sua parete è osservata e non raggiunge più il punto), mai per assenza o per scelta di un'altra.
 */
@Serializable
data class TargetAlternative(
    val targetKey: String,
    val altKey: String,
    val wallKey: String,
    val end: String? = null,
    val observation: TargetObservation,
    val sinceScan: Int,
    val lastEvaluatedScan: Int,
    val lastVerdict: Verdict,
    val status: AlternativeStatus,
    val disambiguatedScan: Int? = null,
)

@Serializable
enum class TargetLinkKind { POSSIBLE_CONTINUATION }

/** Evidenze di continuità richieste per riaprire un bersaglio storico (H1–H4). */
@Serializable
enum class ContinuityCondition { H1_GEOMETRIC_WINDOW, H2_SAME_WALL, H3_DIRECTION, H4_UNIQUE_HISTORY }

/** Collegamento di INCERTEZZA tra un bersaglio nuovo e uno storico: mai identità, copertura, priorità o prova. */
@Serializable
data class TargetLink(
    val scanIndex: Int,
    val kind: TargetLinkKind,
    val newTargetKey: String,
    val historicalTargetKey: String,
    val failedConditions: List<ContinuityCondition>,
)

@Serializable
enum class OutwardSource { BIRTH_FRAGMENT_END, FOLLOWED_FRAGMENT_END, LEGACY_DERIVED }

/** Evidenza che giustifica la direzione verso l'esterno di un'estremità. */
@Serializable
data class OutwardEvidence(val source: OutwardSource, val fragment: FragmentRef? = null, val end: String? = null, val scanIndex: Int)

/** Candidato considerato per un bersaglio in una scansione (registrato nell'evento: nessuna alternativa sparisce in silenzio). */
@Serializable
data class TargetCandidate(
    val fragment: FragmentRef,
    val wallKey: String,
    val end: String? = null,
    val verdict: Verdict,
    val followedBy: String? = null,
    val alternativeKey: String? = null,
)

@Serializable
enum class FrameStatus { FIRST_SCAN, SAME_FRAME_PREFIX, COORDINATE_FRAME_MISMATCH }

/** Impronta della registrazione: verifica di continuazione per prefisso. [createdAtMillis] è solo un identificatore, non un tempo usato. */
@Serializable
data class RecordingFingerprint(val createdAtMillis: Long, val poseCount: Int, val depthCount: Int, val poseHash: String, val depthHash: String)

/** Valori osservati di un bersaglio in una scansione (null = non applicabile o non misurabile, mai inventato). */
@Serializable
data class TargetObservation(
    /** Il difetto è presente (una richiesta M5 copre il bersaglio). */
    val open: Boolean,
    val severity: String? = null,
    val m5RequestIds: List<String> = emptyList(),
    val r3WallId: Int? = null,
    val endpointState: String? = null,
    val endpointReason: String? = null,
    val pointX: Double? = null, val pointZ: Double? = null,
    /** Non osservato misurabile (m): per un'estremità solo se R4 ha dedotto un prolungamento; altrimenti null. */
    val unobservedM: Double? = null,
    val segAx: Double? = null, val segAz: Double? = null, val segBx: Double? = null, val segBz: Double? = null,
    val perimeterState: String? = null,
    val missingSideM: Double? = null,
    val viewCount: Int? = null,
    val sigmaPositionM: Double? = null, val sigmaDirectionDeg: Double? = null, val measurementLevel: String? = null,
    val ambiguityScore: Double? = null, val ambiguityStability: Double? = null,
    /** M5.1.2 — direzione verso l'esterno dell'estremità e l'evidenza che la giustifica (null nelle sessioni precedenti). */
    val outX: Double? = null, val outZ: Double? = null,
    val outEvidence: OutwardEvidence? = null,
)

/** Una transizione registrata, con i valori prima e dopo che la spiegano. */
@Serializable
data class TransitionEvent(
    val scanIndex: Int,
    val from: TargetState?,
    val to: TargetState,
    val reason: TransitionReason,
    val changeM: Double? = null,
    val matchStatus: MatchStatus? = null,
    /** Valore precedente realmente noto; null = nessun valore precedente (prima osservazione o emissione). */
    val previous: TargetObservation? = null,
    val current: TargetObservation? = null,
    /** Bersaglio collegato (es. quello che ora segue lo stesso difetto, o in cui il tratto è confluito). */
    val relatedTargetKey: String? = null,
    /** M5.1.2 — candidati considerati (frammento, verdetto, chi li segue, alternativa). */
    val candidates: List<TargetCandidate> = emptyList(),
)

@Serializable
data class SessionTarget(
    /** Identità stabile (es. "SW3-END", "SW3-SEG-1", "PERIMETER", "SW3-VIEWS"). */
    val key: String,
    val kind: TargetKind,
    val wallKey: String?,
    /** Per le estremità: START/END come erano alla prima osservazione; poi ritrovate per posizione (l'orientamento può invertirsi). */
    val end: String? = null,
    val state: TargetState,
    /** Scansioni elaborate mentre il bersaglio era aperto (non prova che l'utente abbia guardato lì). */
    val attempts: Int,
    val reopenCount: Int,
    val firstScan: Int,
    val lastScan: Int,
    val last: TargetObservation,
    val history: List<TransitionEvent>,
    /** Epoca del sistema di coordinate (cresce a ogni COORDINATE_FRAME_MISMATCH): si confrontano solo bersagli della stessa epoca. */
    val frameEpoch: Int = 0,
    /** Esito dell'ultima valutazione: estremità interna a una fusione (M5.1.2: solo storico, ricalcolato a ogni scansione). */
    val internal: Boolean = false,
)

/** Alias di parete: la chiave [absorbedKey] è stata fusa (MERGE dimostrato) nella parete con chiave [canonicalKey]. */
@Serializable
data class WallAlias(val absorbedKey: String, val canonicalKey: String, val scanIndex: Int, val status: MatchStatus)

/** Parete della sessione: chiave stabile e ultima geometria R3 (linea, estremità, σ) usata per ritrovarla. */
@Serializable
data class SessionWall(
    val key: String,
    val r3WallId: Int,
    val nx: Double, val nz: Double, val cx: Double, val cz: Double, val ux: Double, val uz: Double,
    val startU: Double, val endU: Double,
    val positionSigmaM: Double,
    val inRoom: Boolean,
    /** Ultima scansione in cui la parete possedeva frammenti (i campi geometrici descrivono il frammento primario di allora). */
    val lastScan: Int,
    /** M5.1.2 — ACTIVE oppure ABSORBED (fusa); per le sessioni precedenti si deduce dagli alias. */
    val state: WallState = WallState.ACTIVE,
    /** M5.1.2 — prima scansione della parete; −1 = non registrata (sessioni precedenti). */
    val firstScan: Int = -1,
    /** M5.1.2 — frammenti di riferimento dell'ultima osservazione (primario e parti di split); vuoto = solo i campi geometrici. */
    val fragments: List<FragmentGeometry> = emptyList(),
)

/** Confronto di una parete tra la scansione precedente e la corrente, con la diagnostica anche quando NON passa la regola. */
@Serializable
data class WallMatch(
    val scanIndex: Int,
    val previousKey: String?,
    val currentR3WallId: Int?,
    val status: MatchStatus,
    /** Candidata più vicina (anche se non passa la regola) e misure del confronto. */
    val nearestR3WallId: Int? = null,
    val deltaPositionM: Double? = null,
    val positionToleranceM: Double? = null,
    val deltaAngleDeg: Double? = null,
    /** Sovrapposizione lungo la linea (m); negativa = distanza tra le estensioni. */
    val overlapM: Double? = null,
    val failedRules: List<String> = emptyList(),
    /** Id R3 (della stessa scansione) delle altre candidate. Nelle sessioni M5.1.1 può contenere anche numeri di sessione (legacy). */
    val alternatives: List<Int> = emptyList(),
    /** M5.1.2 — insieme delle relazioni (status resta il riassunto per precedenza). */
    val relations: List<MatchStatus> = emptyList(),
    val primary: FragmentRef? = null,
    val candidates: List<CandidateLink> = emptyList(),
    /** Chiavi di sessione delle pareti fuse insieme in questa scansione. */
    val mergedWith: List<String> = emptyList(),
    /** Chiavi di sessione delle pareti in relazione AMBIGUOUS_WITH in questa scansione. */
    val ambiguousWith: List<String> = emptyList(),
    /** Parete ritrovata dopo una o più scansioni senza osservazione: ultima scansione in cui era stata osservata. */
    val dormantSinceScan: Int? = null,
)

@Serializable
data class ScanRecord(
    val index: Int,
    val dataset: String,
    val fingerprint: RecordingFingerprint,
    val frameStatus: FrameStatus,
    val m5Decision: String,
    val m5RequestIds: List<String>,
    /** Solo metadato (createdAtMillis della registrazione): mai usato nelle decisioni. */
    val recordingCreatedAtMillis: Long,
)

@Serializable
data class RescanSession(
    val schema: String = "sagoma.rescan-session/1",
    val sessionId: String,
    val nextWallNumber: Int,
    val scans: List<ScanRecord>,
    val walls: List<SessionWall>,
    val targets: List<SessionTarget>,
    val wallMatches: List<WallMatch>,
    /** Alias delle pareti fuse (M5.1.1); vuoto nelle sessioni precedenti, che restano leggibili. */
    val wallAliases: List<WallAlias> = emptyList(),
    /** M5.1.2 — relazioni tra pareti, alternative sospese dei bersagli, collegamenti di incertezza (vuoti nelle sessioni precedenti). */
    val wallRelations: List<WallRelation> = emptyList(),
    val targetAlternatives: List<TargetAlternative> = emptyList(),
    val targetLinks: List<TargetLink> = emptyList(),
)
