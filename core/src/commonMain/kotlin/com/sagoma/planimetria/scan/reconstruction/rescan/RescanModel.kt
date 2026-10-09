package com.sagoma.planimetria.scan.reconstruction.rescan

import com.sagoma.planimetria.scan.reconstruction.AmbiguityParams
import com.sagoma.planimetria.scan.reconstruction.MissingSide
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.UnobservedStretch

/*
 * M5 — RESCAN DIRECTOR (offline, deterministico). Livello DECISIONALE sopra la qualità di M4: trasforma i difetti in richieste di
 * nuova acquisizione ordinate per priorità, oppure decide NO_RESCAN_NEEDED. Legge M4 e i dati di R1–R4.1/R2.1 SOLO per localizzare
 * (punti, segmenti, lato mancante): non li modifica, non inventa geometria, non riconosce oggetti, non ha memoria tra scansioni.
 * Il bersaglio è dove guardare; la posizione della camera e la visibilità NON sono verificate (visibilityVerified = false sempre).
 */

/** Soglie di CONFIGURAZIONE DI PRODOTTO (come quelle di M4): non sono proprietà fisiche del sensore. */
data class RescanConfig(
    /** Un tratto non osservato genera una richiesta se ≥ questa lunghezza (m), incluso il valore esatto. */
    val minUnobservedM: Double = 0.20,
    /** Viste indipendenti minime (gruppi di vista R1, come in R3/M4). */
    val minViews: Int = 2,
    /** Soglia di ambiguità (τ della specifica R2.1). */
    val ambiguityTau: Double = AmbiguityParams.TAU,
    /** Un gap interno R3 entro questa distanza da un'estremità è la STESSA causa dell'estremità (una sola richiesta), m. */
    val endAdjacencyM: Double = 0.20,
)

enum class RescanRequestType {
    RESCAN_OPEN_PERIMETER,
    RESCAN_UNCERTAIN_PERIMETER,
    RESCAN_WALL_START,
    RESCAN_WALL_END,
    RESCAN_WALL_BODY,
    RESCAN_UNOBSERVED_REGION,
    RESCAN_LOW_VIEW_COUNT,
    RESCAN_WALL_MEASUREMENT,
    RESCAN_HIGH_AMBIGUITY,
}

/** Gravità (ordine di priorità). Il giudizio non usa mai una media. */
enum class Severity { BLOCKING, IMPORTANT, SECONDARY }

/** Tipo di osservazione consigliata (dal motivo che R3/R4 già registrano). NON è una traiettoria. */
enum class ObservationKind(val label: String) {
    CLOSE_MISSING_SIDE("inquadrare il lato mancante tra le due estremità indicate"),
    REVIEW_PERIMETER("riosservare il perimetro: R4 ha un anello con condizioni non verificate"),
    OBLIQUE_PAST_OCCLUSION("vista obliqua oltre ciò che occlude l'estremità o il tratto"),
    FRAME_BEYOND_END("inquadrare la zona oltre l'estremità, non ancora osservata"),
    MORE_VIEWS_OF_END("più viste dell'estremità: la sua posizione è instabile tra le viste"),
    FRAME_THE_CORNER("inquadrare l'angolo: c'è una superficie vicina ma l'incontro non è dimostrato"),
    FRAME_SEGMENT("inquadrare il tratto non osservato"),
    ADDITIONAL_VIEWPOINT("osservare la parete da un altro punto di vista"),
    VIEW_AMBIGUITY_ZONE("osservare la zona da cui viene l'evidenza di superficie alternativa, da un'altra posizione"),
}

enum class TargetKind { POINT, SEGMENT, NONE }

/** Bersaglio in pianta (x, z), derivato SOLO da coordinate già presenti in R2/R3/R4. [positionKnown] = false: non determinabile. */
data class RescanTarget(
    val kind: TargetKind,
    val ax: Double? = null, val az: Double? = null, val bx: Double? = null, val bz: Double? = null,
    /** Da dove viene la coordinata (es. "R3 W2 end", "R4 lato mancante", "R2.1 banda E5 sinistra di S7"). */
    val source: String,
    val positionKnown: Boolean = kind != TargetKind.NONE,
)

data class RescanRequest(
    /** Identificativo deterministico (es. "W3-END", "ROOM-PERIMETER"). */
    val id: String,
    val type: RescanRequestType,
    val severity: Severity,
    val wallId: Int?,
    val inRoom: Boolean,
    val target: RescanTarget,
    /** Direzione di sguardo consigliata in pianta (verso la parete, dal lato interno), null se non determinabile. */
    val lookDirX: Double?, val lookDirZ: Double?,
    val observation: ObservationKind,
    /** Motivo registrato da R3/R4 (es. OCCLUDED, NOT_SEEN, UNSTABLE, SURFACE_NEAR), se esiste. */
    val sourceReason: String?,
    val primaryReason: String,
    /** Altre cause nello stesso punto, fuse in questa richiesta (nessuna richiesta duplicata). */
    val contributingReasons: List<String>,
    val unobservedLengthM: Double?,
    /** La posizione della camera e la visibilità del bersaglio NON sono verificate: la direzione non è una traiettoria garantita. */
    val visibilityVerified: Boolean = false,
    val priorityRank: Int = 0,
    val priorityExplanation: String = "",
)

/** Limiti riportati senza richiesta (es. ambiguità stabile: altre viste non la risolvono). */
data class RescanLimitation(val wallId: Int?, val code: String, val detail: String)

enum class RescanDecision { RESCAN_RECOMMENDED, NO_RESCAN_NEEDED }

/** Geometria di R4 usata per localizzare (non reinterpretata). */
data class PerimeterGeometry(
    val state: PerimeterState?,
    val missing: MissingSide?,
    val stretches: List<UnobservedStretch>,
    val reasons: List<String>,
)

/** Superficie R2 in pianta, per localizzare le bande di R2.1: centroide, asse u, estensione. */
data class SurfacePlan(val cx: Double, val cz: Double, val ux: Double, val uz: Double, val uMin: Double, val uMax: Double)

class RescanPlan(
    val config: RescanConfig,
    val decision: RescanDecision,
    /** Tutte le richieste, ordinate per priorità (anche le SECONDARY, che non impediscono lo stop). */
    val requests: List<RescanRequest>,
    val limitations: List<RescanLimitation>,
    val decisionReason: String,
)
