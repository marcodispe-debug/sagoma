package com.sagoma.planimetria.scan.reconstruction.bench

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/*
 * BENCHMARK (solo test) — GROUND TRUTH. Modello esplicito della stanza, indipendente da R2/R3/R4: pareti (poligono chiuso percorso
 * con l'interno a destra, come la convenzione di R3/R4), angoli, aperture, mobili, pannelli, zone senza ritorno di profondità.
 * È la verità con cui si confronta la pipeline: non viene mai generato da R2/R3.
 */

enum class OpeningKind { DOOR, WINDOW }

/** Apertura in una parete: tratto [fromM, toM] lungo la parete (dall'inizio) e quote [bottomM, topM] dal pavimento. */
data class GtOpening(val id: String, val kind: OpeningKind, val fromM: Double, val toM: Double, val bottomM: Double, val topM: Double)

/** Zona della parete che non restituisce profondità (vetro scuro, TV, specchio): esiste, ma il sensore non la misura. NON è un'apertura. */
data class GtNoReturn(val fromM: Double, val toM: Double, val bottomM: Double, val topM: Double)

/** Parete strutturale da (ax, az) a (bx, bz); normale verso l'interno (a destra della direzione): n = (uz, −ux). */
data class GtWall(val id: Int, val ax: Double, val az: Double, val bx: Double, val bz: Double, val openings: List<GtOpening> = emptyList(), val noReturn: List<GtNoReturn> = emptyList()) {
    val length: Double get() = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
    val ux: Double get() = (bx - ax) / length
    val uz: Double get() = (bz - az) / length
    val nx: Double get() = uz
    val nz: Double get() = -ux
    val headingDeg: Double get() = Math.toDegrees(atan2(uz, ux))
    /** Coordinata lungo la parete e distanza firmata (positiva verso l'interno). */
    fun along(x: Double, z: Double) = (x - ax) * ux + (z - az) * uz
    fun dist(x: Double, z: Double) = (x - ax) * nx + (z - az) * nz
    fun pointAt(u: Double) = doubleArrayOf(ax + ux * u, az + uz * u)
}

/** Mobile/oggetto: scatola allineata agli assi (metri). Mai strutturale. */
data class GtBox(val id: Int, val name: String, val x0: Double, val y0: Double, val z0: Double, val x1: Double, val y1: Double, val z1: Double)

/** Pannello verticale sottile non strutturale (es. testiera, schienale di una libreria) da (ax, az) a (bx, bz), quote [y0, y1]. */
data class GtPanel(val id: Int, val name: String, val ax: Double, val az: Double, val bx: Double, val bz: Double, val y0: Double, val y1: Double)

data class GtCorner(val id: Int, val x: Double, val z: Double, val wallIn: Int, val wallOut: Int, val interiorAngleDeg: Double)

/** Postazione della camera e giro di viste: imbardata da [yawFromDeg] a [yawToDeg] a passi di [yawStepDeg], beccheggio fisso. */
data class Station(val x: Double, val y: Double, val z: Double, val yawFromDeg: Double = 0.0, val yawToDeg: Double = 345.0, val yawStepDeg: Double = 15.0, val pitchDeg: Double = -20.0)

class GtRoom(
    val name: String,
    /** Pareti nell'ordine del poligono (l'inizio di ogni parete è la fine della precedente). */
    val walls: List<GtWall>,
    val heightM: Double = 2.6,
    val boxes: List<GtBox> = emptyList(),
    val panels: List<GtPanel> = emptyList(),
    /** Distanza delle superfici esterne (viste attraverso le aperture) dal perimetro della stanza. */
    val outsideM: Double = 1.5,
    /** Audit FREE_SPACE: false = oltre le aperture non si vede nessuna superficie (nessun ritorno). Default invariato: true. */
    val outsideReturns: Boolean = true,
) {
    init {
        for (i in walls.indices) {
            val a = walls[i]; val b = walls[(i + 1) % walls.size]
            require(abs(a.bx - b.ax) < 1e-9 && abs(a.bz - b.az) < 1e-9) { "$name: il poligono delle pareti non è chiuso tra W${a.id} e W${b.id}" }
        }
    }

    val corners: List<GtCorner> = walls.indices.map { i ->
        val a = walls[i]; val b = walls[(i + 1) % walls.size]
        val turn = Math.toDegrees(atan2(a.ux * b.uz - a.uz * b.ux, a.ux * b.ux + a.uz * b.uz))
        GtCorner(i, a.bx, a.bz, a.id, b.id, 180 + turn)
    }

    val perimeterM: Double get() = walls.sumOf { it.length }

    /** Punto dentro il poligono in pianta (pari/dispari). */
    fun inside(x: Double, z: Double): Boolean {
        var c = false
        for (i in walls.indices) {
            val w = walls[i]
            if ((w.az > z) != (w.bz > z) && x < (w.bx - w.ax) * (z - w.az) / (w.bz - w.az) + w.ax) c = !c
        }
        return c
    }

    val minX get() = walls.minOf { minOf(it.ax, it.bx) }
    val maxX get() = walls.maxOf { maxOf(it.ax, it.bx) }
    val minZ get() = walls.minOf { minOf(it.az, it.bz) }
    val maxZ get() = walls.maxOf { maxOf(it.az, it.bz) }
}

/** Uno scenario: stanza, postazioni della camera, parametri del sensore predefiniti e cosa deve mostrare. */
data class Scenario(val id: String, val title: String, val room: GtRoom, val stations: List<Station>, val sensor: SensorParams, val purpose: String)

/** Libreria degli scenari S0–S17. Nessuna soglia è tarata sui dataset reali. */
object Scenarios {
    /** Rettangolo 4 × 3 m percorso con l'interno a destra: (0,0) → (0,3) → (4,3) → (4,0). */
    fun rect(w: Double = 4.0, d: Double = 3.0, openings: Map<Int, List<GtOpening>> = emptyMap(), noReturn: Map<Int, List<GtNoReturn>> = emptyMap()): List<GtWall> {
        val p = listOf(0.0 to 0.0, 0.0 to d, w to d, w to 0.0)
        return p.indices.map { i -> val a = p[i]; val b = p[(i + 1) % p.size]; GtWall(i, a.first, a.second, b.first, b.second, openings[i].orEmpty(), noReturn[i].orEmpty()) }
    }

    val ring = listOf(Station(2.0, 1.4, 1.5), Station(2.6, 1.5, 1.8, yawFromDeg = 7.5, yawToDeg = 352.5))
    private val clean = SensorParams()
    private val noisy = SensorParams(depthSigmaM = 0.006, dropout = 0.03)
    private val cornerNoise = SensorParams(depthSigmaM = 0.012, dropout = 0.05)

    private fun door(id: String, from: Double, w: Double) = GtOpening(id, OpeningKind.DOOR, from, from + w, 0.0, 2.1)
    private fun window(id: String, from: Double, w: Double, bottom: Double, top: Double) = GtOpening(id, OpeningKind.WINDOW, from, from + w, bottom, top)

    fun all(): List<Scenario> = listOf(
        Scenario("S0", "stanza rettangolare pulita", GtRoom("S0", rect()), ring, clean, "riferimento: nessun rumore, nessun oggetto, nessuna apertura"),
        Scenario("S1", "rettangolo con rumore depth", GtRoom("S1", rect()), ring, noisy, "effetto del rumore realistico su tutte le fasi"),
        Scenario("S2", "angoli rumorosi", GtRoom("S2", rect()), ring, cornerNoise, "perdita agli angoli (problema del corner diagnostic)"),
        Scenario("S3", "porte", GtRoom("S3", rect(openings = mapOf(3 to listOf(door("D1 0,8 m centrale", 1.6, 0.8)), 2 to listOf(door("D2 1,0 m vicino all'angolo", 0.15, 1.0))))), ring, noisy, "porte di larghezza e posizione diverse, una vicina a un angolo"),
        Scenario("S4", "finestre", GtRoom("S4", rect(openings = mapOf(1 to listOf(window("F1 alta", 1.2, 1.2, 0.9, 2.0)), 0 to listOf(window("F2 bassa", 1.0, 0.8, 0.3, 0.9), window("F3 a nastro", 2.0, 0.6, 1.8, 2.3))))), ring, noisy, "finestre a quote diverse dal pavimento"),
        Scenario("S5", "mobile contro parete", GtRoom("S5", rect(), boxes = listOf(GtBox(0, "armadio a 2 cm dalla parete", 1.0, 0.0, 2.38, 2.2, 2.0, 2.98))), ring, noisy, "contaminazione dei muri con il fronte/fianco del mobile"),
        Scenario("S6", "mobile davanti a una porta", GtRoom("S6", rect(openings = mapOf(3 to listOf(door("D1", 1.5, 0.8)))), boxes = listOf(GtBox(0, "cassettiera davanti alla porta", 1.4, 0.0, 0.3, 2.4, 1.0, 0.8))), ring, noisy, "apertura parzialmente occlusa: apertura o occlusione?"),
        Scenario("S7", "parete parzialmente occlusa", GtRoom("S7", rect(), boxes = listOf(GtBox(0, "colonna/libreria alta", 3.4, 0.0, 0.8, 3.7, 2.3, 2.0))), ring, noisy, "un tratto di parete non osservabile da nessuna postazione"),
        Scenario("S8", "parete dietro un mobile", GtRoom("S8", rect(), boxes = listOf(GtBox(0, "mobile lungo quasi aderente", 0.05, 0.0, 0.3, 0.6, 2.2, 2.7))), ring, noisy, "parete strutturale presente ma solo parzialmente acquisita"),
        Scenario("S9", "angolo + mobile", GtRoom("S9", rect(), boxes = listOf(GtBox(0, "mobile nell'angolo", 0.02, 0.0, 2.4, 0.62, 1.8, 2.98))), ring, cornerNoise, "perdita dell'angolo e contaminazione insieme"),
        Scenario("S10", "superfici parallele vicine", GtRoom("S10", rect(), panels = listOf(GtPanel(0, "pannello parallelo a 15 cm", 1.0, 2.85, 3.0, 2.85, 0.0, 1.6))), ring, noisy, "parete + superficie parallela a distanza ridotta"),
        Scenario("S11", "stanza con rientranza", GtRoom("S11", listOf(
            GtWall(0, 0.0, 0.0, 0.0, 3.0), GtWall(1, 0.0, 3.0, 1.5, 3.0), GtWall(2, 1.5, 3.0, 1.5, 4.2), GtWall(3, 1.5, 4.2, 2.5, 4.2),
            GtWall(4, 2.5, 4.2, 2.5, 3.0), GtWall(5, 2.5, 3.0, 4.0, 3.0), GtWall(6, 4.0, 3.0, 4.0, 0.0), GtWall(7, 4.0, 0.0, 0.0, 0.0),
        )), ring + Station(2.0, 1.4, 2.9, yawFromDeg = 0.0, yawToDeg = 345.0, yawStepDeg = 30.0), noisy, "non solo rettangoli: angoli rientranti (270°)"),
        Scenario("S12", "acquisizione incompleta", GtRoom("S12", rect()), listOf(Station(2.0, 1.4, 1.5, yawFromDeg = 90.0, yawToDeg = 270.0)), noisy, "pareti viste solo in parte: il sistema non deve inventarle"),
        Scenario("S13", "viste multiple", GtRoom("S13", rect(), boxes = listOf(GtBox(0, "tavolo", 1.5, 0.0, 1.0, 2.5, 0.75, 2.0))), listOf(
            Station(1.0, 1.4, 1.0, yawStepDeg = 30.0), Station(3.0, 1.5, 1.0, yawStepDeg = 30.0), Station(1.0, 1.5, 2.2, yawStepDeg = 30.0), Station(3.0, 1.4, 2.2, yawStepDeg = 30.0),
        ), noisy, "stessa stanza da traiettorie diverse (confronta con S1 e con numberOfViews)"),
        Scenario("S14", "singola vista", GtRoom("S14", rect()), listOf(Station(2.0, 1.4, 1.0, yawFromDeg = 0.0, yawToDeg = 0.0)), noisy.copy(frameIntervalMs = 100, framesPerYaw = 5), "una sola osservazione non deve bastare"),
        Scenario("S15", "falso spazio libero", GtRoom("S15", rect(noReturn = mapOf(2 to listOf(GtNoReturn(1.0, 2.0, 0.8, 1.6))))), ring, noisy, "zona senza depth (TV/vetro scuro) che NON è un'apertura"),
        Scenario("S16", "parete frammentata", GtRoom("S16", rect(noReturn = mapOf(1 to listOf(GtNoReturn(1.1, 1.4, 0.0, 2.6), GtNoReturn(2.6, 2.9, 0.0, 2.6))))), ring, noisy, "una parete acquisita in più frammenti separati"),
        Scenario("S17", "apertura parzialmente osservata", GtRoom("S17", rect(openings = mapOf(3 to listOf(door("D1", 2.7, 0.9)))), boxes = listOf(GtBox(0, "armadio alto davanti a metà porta", 0.35, 0.0, 0.3, 0.85, 2.2, 0.7))), ring, noisy, "solo una parte della porta è visibile"),
    )

    fun byId(id: String) = all().first { it.id == id }
}
