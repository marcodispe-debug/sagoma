package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.RecordedFrame
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.rotate
import com.sagoma.planimetria.scan.recording.transformPoint
import kotlin.math.sqrt

/*
 * M2.0 — scansione assistita delle pareti: motore puro (nessuna UI, nessun Android). ARCore PROPONE una candidata mirata
 * dall'utente; solo la conferma esplicita (evento `Confirm`) la trasforma in parete. Tutto nel mondo ARCore, in pianta (x, z),
 * metri. Il piano verticale di ARCore è la fonte primaria della linea; la nuvola di punti è solo evidenza e controllo qualità.
 */

/**
 * Parametri del motore. Valori iniziali da validare sulle registrazioni reali (la simulazione offline serve a questo): ognuno
 * ha il suo motivo.
 */
data class AssistedParams(
    /** Mira: distanza utile dal telefono al piano (m). Più vicino è quasi dentro il muro, più lontano la profondità e i piani sono rumorosi. */
    val minAimDistanceM: Double = 0.3,
    val maxAimDistanceM: Double = 8.0,
    /** Il poligono di un piano ARCore è un'approssimazione convessa: la mira può cadere appena fuori. */
    val aimMarginM: Double = 0.10,
    /** Continuità della candidata: stessa parete se direzione entro 6° e distanza dalla retta entro 8 cm (come in M3.1 per piani contemporanei). */
    val sameAngleTolDeg: Double = 6.0,
    val sameOffsetTolM: Double = 0.08,
    /** Vuoto massimo lungo la retta tra frammenti dello stesso muro (piani divisi da ARCore). */
    val sameGapTolM: Double = 0.60,
    /** Mira persa: la candidata resta viva per questo tempo (ms), poi si riparte da "Cerca". */
    val graceMs: Long = 1_000,
    /** Un'altra superficie incompatibile deve essere mirata per tanto (ms) prima di sostituire la candidata (evita lo sfarfallio agli angoli). */
    val switchHoldMs: Long = 300,
    /** Finestra (ms) su cui si misura la stabilità di direzione e posizione. */
    val windowMs: Long = 1_000,
    val minWindowSamples: Int = 5,
    /** Soglie di stabilità. */
    val minAimedMs: Long = 800,
    val minPlaneTrackingMs: Long = 1_000,
    val maxSpeedMps: Double = 0.5,
    val maxHeadingStdDeg: Double = 1.5,
    val maxOffsetStdM: Double = 0.02,
    val maxTiltDeg: Double = 8.0,
    val minLengthM: Double = 1.0,
    val minHeightM: Double = 1.0,
    /** La proposta compare dopo che tutte le condizioni valgono da tanto (ms); sparisce solo se non valgono più da tanto. */
    val proposeHoldMs: Long = 500,
    val unproposeHoldMs: Long = 300,
    /** Evidenza dai punti (solo controllo qualità, non sposta la linea). */
    val pointSupportTolM: Double = 0.06,
    val pointMinConfidence: Double = 0.5,
    val minPointSupport: Int = 10,
    val maxPointRmsM: Double = 0.04,
    val maxPointBiasM: Double = 0.05,
    /** Avvisi (non bloccanti). */
    val floorGapAdvisoryM: Double = 0.30,
    val behindMaxM: Double = 0.70,
    val farAimM: Double = 6.0,
)

/** Un piano verticale di ARCore in un frame, già nel mondo (indipendente da come è stato registrato). */
data class AimPlane(
    val key: Int,
    val tracking: Boolean,
    val subsumedBy: Int?,
    val pose: RecPose,
    /** Poligono nel sistema locale del piano (x, z). */
    val polygonLocal: List<Pair<Double, Double>>,
    val polygonWorld: List<ArPoint>,
)

data class PointRec(val x: Double, val y: Double, val z: Double, val confidence: Double)

/** Ingresso del motore: un frame con ciò che serve per mirare e valutare una candidata. Si ricava da `RecordedFrame`. */
data class AimFrame(
    val index: Int,
    val timeMs: Long,
    val tracking: Boolean,
    /** Posizione del telefono e direzione di mira (versore, asse −Z della posa orientata come lo schermo), nel mondo. */
    val camera: ArPoint?,
    val forward: ArPoint?,
    val speedMps: Double?,
    val floorY: Double?,
    val planes: List<AimPlane>,
    /** Nuvola di punti, solo nei frame in cui è cambiata. */
    val points: Map<Int, PointRec>?,
)

object AimFrames {
    /** Frame in ordine di tempo (a parità, di indice): l'ordine di arrivo nel file non conta. */
    fun from(recording: ScanRecording): List<AimFrame> = from(recording.frames)

    fun from(frames: List<RecordedFrame>): List<AimFrame> {
        val sorted = frames.sortedWith(compareBy({ it.elapsedMs }, { it.index }))
        val out = ArrayList<AimFrame>(sorted.size)
        var prevPos: RecPose? = null
        var prevNs = 0L
        for (f in sorted) {
            val pose = f.cameraDisplay ?: f.camera
            val tracking = f.tracking == "TRACKING"
            var speed: Double? = null
            val cam = f.camera
            if (tracking && cam != null) {
                val p = prevPos
                if (p != null) {
                    val dt = (f.timestampNs - prevNs) / 1e9
                    if (dt > 0) speed = sqrt((cam.x - p.x) * (cam.x - p.x) + (cam.y - p.y) * (cam.y - p.y) + (cam.z - p.z) * (cam.z - p.z)) / dt
                }
                prevPos = cam; prevNs = f.timestampNs
            } else prevPos = null
            val vertical = f.planes.filter { it.kind == "VERTICAL" && it.polygon.size >= 6 }.map { p ->
                val local = List(p.polygon.size / 2) { p.polygon[2 * it] to p.polygon[2 * it + 1] }
                AimPlane(p.key, p.tracking == "TRACKING", p.subsumedBy, p.pose, local, local.map { (x, z) -> p.pose.transformPoint(x, 0.0, z) })
            }
            val points = f.points?.takeIf { it.isConsistent }?.let { pc ->
                LinkedHashMap<Int, PointRec>().also { m -> for (i in 0 until pc.count) m[pc.ids[i]] = PointRec(pc.xyz[3 * i], pc.xyz[3 * i + 1], pc.xyz[3 * i + 2], pc.confidence[i]) }
            }
            out += AimFrame(
                f.index, f.elapsedMs, tracking, pose?.let { ArPoint(it.x, it.y, it.z) }, pose?.rotate(0.0, 0.0, -1.0), speed, f.floorY, vertical, points,
            )
        }
        return out
    }
}

/** Linea di una parete ricavata da UN piano (o da frammenti compatibili): estremi osservati e quote. */
data class WallLine(
    val a: ArXZ,
    val b: ArXZ,
    /** Direzione canonica (da a verso b). */
    val direction: ArXZ,
    /** Normale canonica (perpendicolare a `direction`); la posizione si misura lungo di essa. */
    val normal: ArXZ,
    /** Posizione della retta lungo `normal`. */
    val offset: Double,
    val minY: Double,
    val maxY: Double,
    /** Inclinazione del piano rispetto alla verticale (gradi). */
    val tiltDeg: Double,
) {
    val length: Double get() = a.distanceTo(b)
    val heightM: Double get() = maxY - minY
    val headingDeg: Double
        get() = (((com.sagoma.planimetria.geometry.toDegrees(kotlin.math.atan2(direction.z, direction.x)) % 180.0) + 180.0) % 180.0).let { if (it >= 180.0) 0.0 else it }
}

enum class CandidateState(val label: String) {
    SEARCHING("Cerca una parete"),
    TRACKING("Stabilizzo"),
    PROPOSED("Parete rilevata"),
}

/** Perché una candidata non è (ancora) stabile. */
enum class Reason(val label: String) {
    AIM_SHORT("mira troppo breve"),
    PLANE_NEW("piano ancora poco seguito"),
    TOO_FAST("telefono troppo veloce"),
    HEADING_UNSTABLE("direzione instabile"),
    OFFSET_UNSTABLE("posizione instabile"),
    TILTED("superficie inclinata"),
    SHORT("parete troppo corta"),
    LOW("parte osservata troppo bassa"),
    FEW_SAMPLES("troppo pochi campioni"),
    NO_TRACKING("tracciamento perso"),
    AIM_LOST("mira persa"),
}

enum class Advisory(val label: String) {
    NOT_REACHING_FLOOR("non arriva a terra: potrebbe essere un mobile"),
    PARALLEL_BEHIND("c'è già una parete confermata dietro: potrebbe essere un mobile davanti"),
    POINTS_DISAGREE("i punti non concordano con il piano"),
    FEW_POINTS("poca evidenza dai punti"),
    FAR("molto lontana"),
}

/** Evidenza dalla nuvola di punti (solo controllo qualità). */
data class PointEvidence(val support: Int, val rmsM: Double?, val biasM: Double?, val available: Boolean)

/** La candidata come la vede l'utente in questo istante. */
data class CandidateWall(
    val state: CandidateState,
    val planeKeys: List<Int>,
    val line: WallLine,
    val aimedPlaneKey: Int?,
    val aimDistanceM: Double?,
    val candidateAgeMs: Long,
    val aimedMs: Long,
    val planeTrackingMs: Long,
    val maxSpeedMps: Double,
    val headingStdDeg: Double,
    val offsetStdM: Double,
    val samples: Int,
    val evidence: PointEvidence,
    /** 0..1: la componente più lontana dalla soglia (1 = tutte le condizioni valgono). */
    val stability: Double,
    val reasons: List<Reason>,
    val advisories: List<Advisory>,
    /** Parete già confermata uguale a questa (confermarla di nuovo sarebbe un doppione). */
    val duplicateOf: Int?,
)

enum class Quality(val label: String) { HIGH("Alta"), MEDIUM("Media"), LOW("Bassa") }

/** Parete confermata dall'utente: istantanea CONGELATA della candidata stabile. */
data class ConfirmedWall(
    val id: Int,
    val line: WallLine,
    /** Normale verso il lato da cui è stata vista (l'interno della stanza). */
    val normalIn: ArXZ,
    val observedLengthM: Double,
    val floorGapM: Double?,
    val qualityScore: Double,
    val quality: Quality,
    val rmsM: Double?,
    val pointSupport: Int,
    val planeKeys: List<Int>,
    val confirmedAtMs: Long,
    val cameraAt: ArXZ?,
    val advisories: List<Advisory>,
)

sealed interface AssistedEvent {
    val timeMs: Long
    data class Appeared(override val timeMs: Long, val planeKey: Int) : AssistedEvent
    data class Switched(override val timeMs: Long, val planeKey: Int) : AssistedEvent
    data class Lost(override val timeMs: Long) : AssistedEvent
    data class Proposed(override val timeMs: Long, val ageMs: Long) : AssistedEvent
    data class Unproposed(override val timeMs: Long, val reasons: List<Reason>) : AssistedEvent
    data class Confirmed(override val timeMs: Long, val wallId: Int) : AssistedEvent
    data class Rejected(override val timeMs: Long, val why: String) : AssistedEvent
    data class Undone(override val timeMs: Long, val wallId: Int) : AssistedEvent
}

internal fun sq(v: Double) = v * v
