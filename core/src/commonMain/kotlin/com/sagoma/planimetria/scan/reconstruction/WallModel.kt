package com.sagoma.planimetria.scan.reconstruction

/*
 * R3 — modello delle pareti stimate. Misura, incertezza, evidenza e confidenza di riconoscimento sono STRUTTURALMENTE separate:
 *  - [WallGeometry]: la misura (piano, linea in pianta, estremi OSSERVATI, quote) — mai estesa oltre i dati;
 *  - [WallUncertainty]: le σ (1 sigma, metri o gradi) della misura, con la componente temporale della raw depth separata;
 *  - [WallEvidence]: quanta e quale evidenza sostiene la misura (punti, viste, copertura, RMS…) e un giudizio di qualità;
 *  - [EstimatedWall.recognitionConfidence]: quanto è probabile che sia davvero una parete (non una σ).
 * Nessun perimetro, nessun collegamento tra pareti, nessun angolo imposto: questo è R4.
 */

/** Stato di un'estremità: [OBSERVED] = la fine della parete si vede nei dati; [PARTIAL] = i dati finiscono prima (fuori vista o
 *  dietro un oggetto); [UNCERTAIN] = la posizione dell'estremità è instabile tra le viste. */
enum class EndState { OBSERVED, PARTIAL, UNCERTAIN }

/** Perché un'estremità ha quello stato. */
enum class EndReason(val label: String) {
    CORNER("incontra un'altra superficie strutturale"),
    FREE_BEYOND("oltre l'estremità si vede attraverso (la parete finisce)"),
    OCCLUDED("oltre l'estremità c'è un oggetto davanti (parete nascosta)"),
    NOT_SEEN("oltre l'estremità non c'è osservazione (fuori vista)"),
    UNSTABLE("posizione instabile tra le viste"),
    /** R3.1: c'è un'altra superficie oltre l'estremità, ma i dati non dimostrano l'angolo (intersezione non raggiunta entro le σ,
     *  o evidenza terminale debole). Il tratto fino all'intersezione NON è osservato. */
    SURFACE_NEAR("un'altra superficie è vicina, ma l'angolo non è dimostrato dai dati"),
}

/** Tratto NON osservato dentro una parete, con il motivo: non viene mai riempito. */
enum class GapReason(val label: String) {
    OCCLUDED("nascosto da un oggetto davanti"),
    SEEN_THROUGH("si vede attraverso (vano? sarà R5)"),
    NOT_SEEN("non osservato"),
}

enum class ThicknessState { MEASURED, UNKNOWN }

enum class EvidenceQuality { HIGH, MEDIUM, LOW }

/**
 * R3.1 — evidenza misurata nella zona terminale di un'estremità.
 *  - [density5]…[density20]: punti nel tratto terminale di 5/10/15/20 cm divisi per quelli attesi con la densità mediana della parete;
 *  - [thinZoneM]: tratto terminale (continuo, dall'estremità verso l'interno) con densità sotto la soglia: lì la fine vera è incerta;
 *  - [lastSpanM], [terminalGapM]: lunghezza dell'ultimo tratto osservato continuo e del gap che lo precede (null se nessuno);
 *  - [groupsNearEnd]: gruppi di vista con punti entro 20 cm dall'estremità;
 *  - [repeatSigmaM]: σ di ripetibilità tra le viste (la σ R3 precedente), [terminalSigmaM]: σ dovuta alla zona assottigliata;
 *  - [surfaceId], [surfaceGapM], [surfaceGapSigmas]: superficie strutturale non parallela più vicina oltre l'estremità e distanza
 *    lungo la linea fino all'intersezione (positiva = l'intersezione è oltre i dati), anche in unità di σ.
 */
data class EndEvidence(
    val density5: Double, val density10: Double, val density15: Double, val density20: Double,
    val thinZoneM: Double,
    val lastSpanM: Double,
    val terminalGapM: Double?,
    val groupsNearEnd: Int,
    val repeatSigmaM: Double,
    val terminalSigmaM: Double,
    val surfaceId: Int?,
    val surfaceGapM: Double?,
    val surfaceGapSigmas: Double?,
) {
    /** Evidenza terminale forte: almeno 3 viste arrivano all'estremità e l'assottigliamento finale non domina l'incertezza
     *  (σ terminale ≤ σ di ripetibilità tra le viste). */
    val strong: Boolean get() = groupsNearEnd >= 3 && terminalSigmaM <= repeatSigmaM
}

/** Estremità: posizione lungo la linea (m, coordinata u), stato, motivo, incertezza propria e (R3.1) evidenza terminale. */
data class WallEnd(val u: Double, val state: EndState, val reason: EndReason, val sigmaM: Double, val evidence: EndEvidence? = null)

/** Tratto non osservato tra [fromU] e [toU] (m lungo la linea). */
data class WallGap(val fromU: Double, val toU: Double, val reason: GapReason) {
    val lengthM: Double get() = toU - fromU
}

/** Tratto osservato tra [fromU] e [toU]. */
data class WallSpan(val fromU: Double, val toU: Double)

/**
 * Geometria misurata. La parete è un piano verticale n·p = d con n = ([nx], 0, [nz]) unitaria verso le camere; in pianta la linea
 * passa per ([cx], [cz]) con direzione ([ux], [uz]); gli estremi osservati sono [start] e [end] (coordinate u lungo la linea). Quote
 * assolute (mondo ARCore, m) e rispetto al pavimento se noto.
 */
data class WallGeometry(
    val nx: Double, val nz: Double, val d: Double,
    val cx: Double, val cz: Double, val ux: Double, val uz: Double,
    val startU: Double, val endU: Double,
    val bottomY: Double, val topY: Double,
    val bottomAboveFloor: Double?, val topAboveFloor: Double?,
    val headingDeg: Double,
    val observedSpans: List<WallSpan>,
) {
    val lengthM: Double get() = endU - startU
    val observedLengthM: Double get() = observedSpans.sumOf { it.toU - it.fromU }
    fun pointAt(u: Double) = doubleArrayOf(cx + ux * u, cz + uz * u)
}

/**
 * Incertezze (1σ). [positionSigmaM]: lungo la normale; [headingSigmaDeg]; [lengthSigmaM] = combinazione delle estremità;
 * [temporalSigmaM]: ambiguità A/C della raw (spostamento lungo la normale), media quadratica sui punti, PRIMA della media tra viste;
 * [temporalContributionM]: la sua parte in [positionSigmaM]; [depthSigmaM]: rumore della depth alla distanza mediana (modello del
 * dataset). [calibrated] = false finché non c'è un confronto con misure vere (metro laser).
 */
data class WallUncertainty(
    val positionSigmaM: Double,
    val headingSigmaDeg: Double,
    val startSigmaM: Double,
    val endSigmaM: Double,
    val lengthSigmaM: Double,
    val temporalSigmaM: Double,
    val temporalContributionM: Double,
    val dispersionContributionM: Double,
    val depthSigmaM: Double,
    val calibrated: Boolean = false,
)

/** Evidenza che sostiene la misura, e il giudizio sulla sua qualità (NON una σ, NON la confidenza di riconoscimento). */
data class WallEvidence(
    val pointCount: Int,
    val inlierCount: Int,
    val rawFrames: Int,
    val viewGroups: Int,
    val viewAngleSpanDeg: Double,
    val rangeMedianM: Double,
    val fitRmsM: Double,
    /** Deviazione standard degli scarti medi dei gruppi di vista dal piano (dispersione tra viste). */
    val dispersionM: Double,
    val observedCoverage: Double,
    val outlierFraction: Double,
    val quality: EvidenceQuality,
    val qualityReasons: List<String>,
)

data class WallThickness(val state: ThicknessState, val valueM: Double? = null, val sigmaM: Double? = null, val otherWallId: Int? = null, val note: String)

/** Parete stimata da R3. Gli id delle superfici R2 di origine restano disponibili. */
data class EstimatedWall(
    val id: Int,
    val sourceSurfaceIds: List<Int>,
    val geometry: WallGeometry,
    val uncertainty: WallUncertainty,
    val evidence: WallEvidence,
    val start: WallEnd,
    val end: WallEnd,
    val gaps: List<WallGap>,
    val thickness: WallThickness,
    val recognitionConfidence: Double,
    /** Possibile continuazione oltre un'estremità nascosta: SOLO un'indicazione, non geometria. */
    val possibleContinuation: List<String>,
    val diagnostics: List<String>,
)

/** Superficie R2 non usata come parete, con il motivo. */
data class WallExclusion(val surfaceId: Int, val kind: SurfaceKind, val reason: String)

/** Rumore della depth per fascia di distanza (σ robusta, m), stimato dai residui delle superfici strutturali del dataset. */
data class DepthNoiseModel(val bins: List<Pair<ClosedFloatingPointRange<Double>, Double>>, val floorM: Double) {
    fun sigma(range: Double): Double = bins.firstOrNull { range in it.first }?.second ?: bins.lastOrNull()?.second ?: floorM
}

class WallEstimationResult(
    val inputVertical: Int,
    val inputStructural: Int,
    val walls: List<EstimatedWall>,
    val excluded: List<WallExclusion>,
    /** Gruppi di superfici R2 fuse in una sola parete (solo quelli con più di una superficie). */
    val merges: List<List<Int>>,
    val noise: DepthNoiseModel,
    /** Ripiego per lo spostamento temporale quando la posa C non è disponibile (m). */
    val temporalFallbackM: Double,
    val diagnostics: List<String>,
)
