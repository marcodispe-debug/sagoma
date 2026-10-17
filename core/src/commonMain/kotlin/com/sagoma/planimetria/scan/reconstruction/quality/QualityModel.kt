package com.sagoma.planimetria.scan.reconstruction.quality

import com.sagoma.planimetria.scan.reconstruction.AmbiguityParams
import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EvidenceQuality
import com.sagoma.planimetria.scan.reconstruction.PerimeterState

/*
 * M4 — QUALITY ENGINE (offline). Risponde a "quanto è affidabile e completa la ricostruzione attuale?". NON decide dove mandare
 * l'utente (M5). Legge soltanto i risultati di R1, R2, R2.1, R3/R3.1 e R4/R4.1: non li modifica e non li ricalcola.
 *
 * Tre assi separati, mai fusi in un numero unico:
 *  - MISURA        (measurement): quanto è affidabile geometricamente ciò che è stato misurato (σ di posizione e direzione di R3);
 *  - COMPLETEZZA   (completeness): quanto della geometria è stato davvero osservato (i tratti dedotti da R4 restano NON osservati);
 *  - AMBIGUITÀ     (semantic ambiguity): evidenza POSITIVA osservata di una superficie alternativa (R2.1). L'assenza di informazione
 *                  non è ambiguità.
 * più l'EVIDENZA (qualità dell'evidenza di R3: viste, frame, punti), separata dalla misura.
 * Gli stati HIGH/MEDIUM/LOW sono solo una presentazione dei valori continui, che restano tutti nel risultato. Il giudizio
 * complessivo è il componente PEGGIORE, mai una media.
 */

/** Stato qualitativo di presentazione. [UNKNOWN] = il dato non esiste (mai inventato). */
enum class QualityLevel { HIGH, MEDIUM, LOW, UNKNOWN }

/**
 * Soglie di CONFIGURAZIONE DI PRODOTTO (tolleranze iniziali della planimetria, approvate per M4): NON sono verità metrologiche né
 * soglie fisiche del sensore. Le soglie di ambiguità e di viste vengono dalla specifica R2.1.
 */
data class QualityConfig(
    /** σ di posizione della parete (1σ, m): HIGH ≤ 1 cm, MEDIUM ≤ 3 cm, LOW oltre. */
    val positionHighM: Double = 0.01,
    val positionMediumM: Double = 0.03,
    /** σ di direzione (1σ, gradi): HIGH ≤ 0,5°, MEDIUM ≤ 1,5°, LOW oltre. */
    val headingHighDeg: Double = 0.5,
    val headingMediumDeg: Double = 1.5,
    /** Copertura osservata: HIGH ≥ 90% ed entrambe le estremità OBSERVED; MEDIUM ≥ 60%; LOW sotto. */
    val coverageHigh: Double = 0.90,
    val coverageMedium: Double = 0.60,
    /** Soglia diagnostica di ambiguità (τ della specifica R2.1). */
    val ambiguityTau: Double = AmbiguityParams.TAU,
    /** Viste indipendenti minime (gruppi di vista di R1, come li conta R3). */
    val minViews: Int = 2,
    /** Quante pareti peggiori elencare per asse. */
    val worstN: Int = 3,
)

/** Codici dei difetti: sono DIAGNOSTICA, non ordini di rescansione. */
enum class DefectCode(val label: String) {
    HIGH_POSITION_UNCERTAINTY("σ di posizione oltre la tolleranza MEDIUM"),
    HIGH_DIRECTION_UNCERTAINTY("σ di direzione oltre la tolleranza MEDIUM"),
    PARTIAL_START("estremità iniziale PARTIAL"),
    PARTIAL_END("estremità finale PARTIAL"),
    UNCERTAIN_START("estremità iniziale UNCERTAIN"),
    UNCERTAIN_END("estremità finale UNCERTAIN"),
    UNOBSERVED_SEGMENT("tratti della parete non osservati (gap R3 o prolungamenti/gap dedotti da R4)"),
    LOW_VIEW_COUNT("meno viste indipendenti del minimo"),
    LOW_EVIDENCE("qualità dell'evidenza R3 LOW"),
    HIGH_AMBIGUITY("evidenza di superficie alternativa ≥ τ"),
    UNSTABLE_AMBIGUITY("evidenza di superficie alternativa ≥ τ ma non stabile togliendo una vista"),
    OPEN_PERIMETER("perimetro OPEN o assente"),
    PARTIAL_PERIMETER("perimetro PARTIAL (catena aperta)"),
    UNCERTAIN_PERIMETER("perimetro UNCERTAIN (anello con condizioni non verificate)"),
}

data class Defect(val code: DefectCode, val wallId: Int?, val value: Double?, val detail: String)

/** Asse MISURA. [dispersionM]: dispersione tra viste (R3); [calibrated]: false finché le σ non sono confrontate con misure vere. */
data class MeasurementAxis(
    val positionSigmaM: Double,
    val headingSigmaDeg: Double,
    val rmsM: Double,
    val dispersionM: Double,
    val calibrated: Boolean,
    val positionLevel: QualityLevel,
    val headingLevel: QualityLevel,
    /** Peggiore tra posizione e direzione (l'RMS non ha stato). */
    val level: QualityLevel,
)

/**
 * Asse COMPLETEZZA. [observedLengthM]: tratti osservati (R3). [unobservedR3M]: gap interni di R3. [unobservedR4M]: tratti che R4
 * DEDUCE per questa parete (prolungamenti fino agli angoli, gap tra frammenti): restano non osservati.
 */
data class CompletenessAxis(
    val observedLengthM: Double,
    val unobservedR3M: Double,
    val unobservedR4M: Double,
    val startState: EndState, val startReason: EndReason,
    val endState: EndState, val endReason: EndReason,
    val level: QualityLevel,
    /**
     * Oltre almeno un'estremità non OBSERVED l'estensione vera della parete non è nota e R4 non ha dedotto nessun tratto: allora
     * [unobservedLengthM] è un limite INFERIORE e [coverageRatio] un limite SUPERIORE (il non quantificabile non è "zero").
     */
    val extentUnknown: Boolean = false,
) {
    val unobservedLengthM: Double get() = unobservedR3M + unobservedR4M
    val coverageRatio: Double get() = if (observedLengthM + unobservedLengthM <= 0.0) 0.0 else observedLengthM / (observedLengthM + unobservedLengthM)
}

/**
 * Asse AMBIGUITÀ (alternativeSurfaceEvidence, R2.1). Una parete R3 può fondere più superfici R2: [maxScore] (giudizio conservativo,
 * con la superficie responsabile e la sua stabilità) e [areaWeightedMean] (informativa). NON è una probabilità semantica.
 */
data class AmbiguityAxis(
    val maxScore: Double?,
    val sourceSurfaceId: Int?,
    val sourceSurfaceStability: Double?,
    val areaWeightedMean: Double?,
    val unstable: Boolean,
    /** HIGH = nessuna evidenza alternativa ≥ τ; LOW = evidenza ≥ τ; UNKNOWN = nessun dato R2.1. */
    val level: QualityLevel,
)

/** Asse EVIDENZA: qualità dell'evidenza che R3 ha già calcolato (riusata, nessuna soglia nuova) e viste indipendenti. */
data class EvidenceAxis(
    val r3Quality: EvidenceQuality,
    val r3Reasons: List<String>,
    val viewCount: Int,
    val rawFrames: Int,
    val pointCount: Int,
    val level: QualityLevel,
)

data class WallQuality(
    val wallId: Int,
    val sourceSurfaceIds: List<Int>,
    /** Ruolo rispetto al perimetro R4 (se calcolato). */
    val role: String?,
    val inRoom: Boolean,
    val measurement: MeasurementAxis,
    val completeness: CompletenessAxis,
    val ambiguity: AmbiguityAxis,
    val evidence: EvidenceAxis,
    /** Componente peggiore tra misura, completezza, ambiguità ed evidenza. */
    val overall: QualityLevel,
    val defects: List<Defect>,
)

/** Riepilogo di un asse sulle pareti della stanza: min/max/media e distribuzione degli stati, e le pareti peggiori. */
data class AxisSummary(val values: Int, val min: Double?, val max: Double?, val mean: Double?, val distribution: Map<QualityLevel, Int>, val worstWalls: List<Int>, val worst: QualityLevel)

/** Fatti del perimetro di R4, letti senza reinterpretarli. */
data class PerimeterFacts(
    val state: PerimeterState?,
    val mainWallIds: List<Int>,
    val roles: Map<Int, String>,
    /** Tratti non osservati di R4 (tipo, parete, lunghezza). */
    val unobserved: List<Triple<Int, String, Double>>,
    val chi2: Double?, val dof: Int?, val pValue: Double?, val compatible: Boolean?, val closureUnobservedM: Double?,
)

data class RoomQuality(
    /** Pareti usate per la stanza: quelle del perimetro principale R4 se esiste, altrimenti tutte le pareti R3. */
    val roomWallIds: List<Int>,
    val wallCount: Int,
    val allWallCount: Int,
    val observedWallLengthM: Double,
    val unobservedWallLengthM: Double,
    val wallCoverageRatio: Double,
    /** true se il perimetro non è CLOSED o una parete ha estensione ignota: [wallCoverageRatio] è allora un limite SUPERIORE. */
    val coverageIsUpperBound: Boolean,
    val perimeterState: PerimeterState?,
    val closedPerimeter: Boolean,
    val chi2: Double?, val dof: Int?, val pValue: Double?, val closureCompatible: Boolean?, val closureUnobservedM: Double?,
    /** CLOSED → HIGH; UNCERTAIN, PARTIAL, OPEN → LOW; nessun perimetro → UNKNOWN (presentazione dello stato R4). */
    val closureQuality: QualityLevel,
    val exploredAreaM2: Double?,
    val positionSigma: AxisSummary,
    val headingSigma: AxisSummary,
    val coverage: AxisSummary,
    val ambiguity: AxisSummary,
    val evidence: AxisSummary,
    val ambiguousWalls: Int,
    val unstableAmbiguousWalls: Int,
    val geometryQuality: QualityLevel,
    val completeness: QualityLevel,
    val semanticQuality: QualityLevel,
    val evidenceQuality: QualityLevel,
    /** Componente peggiore tra geometria, completezza (stanza e chiusura), ambiguità ed evidenza. */
    val overallQuality: QualityLevel,
    val defects: List<Defect>,
)

class QualityResult(val config: QualityConfig, val walls: List<WallQuality>, val room: RoomQuality)
