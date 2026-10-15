package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.DetectedWall
import com.sagoma.planimetria.scan.ScanResult
import com.sagoma.planimetria.scan.WallOutline
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min

/*
 * Prova M2.0 sul telefono: la scansione guidata dal vivo. Il motore è [AssistedScan] (invariato); qui si aggiungono solo lo
 * stato della sessione e il perimetro, calcolato ESCLUSIVAMENTE dalle pareti confermate dall'utente con lo stesso
 * [WallOutline] della scansione attuale (nessun angolo retto supposto). Nessun lato viene aggiunto per chiudere la stanza.
 */

/** Un lato del perimetro chiuso: sta sulla retta della parete confermata `wallId`. */
data class AssistedSide(
    val wallId: Int,
    val from: ArXZ,
    val to: ArXZ,
    val lengthM: Double,
    /** Parte del lato coperta dal tratto osservato della parete. */
    val observedM: Double,
) {
    /** Parte del lato oltre il tratto osservato: prolungamento della parete fino all'angolo (al massimo [WallOutline.REACH_M] per estremo). */
    val extendedM: Double get() = max(0.0, lengthM - observedM)
}

/**
 * Perimetro chiuso dalle pareti confermate. `corners[i]` è l'angolo tra il lato `i - 1` e il lato `i`; `sides[i]` va da
 * `corners[i]` a `corners[i + 1]`. `unusedWallIds`: pareti confermate che non fanno parte del perimetro.
 */
data class AssistedPerimeter(val corners: List<ArXZ>, val sides: List<AssistedSide>, val unusedWallIds: List<Int>) {
    val perimeterM: Double get() = sides.sumOf { it.lengthM }
    val areaM2: Double get() = Polygon.area(corners.map { Vec2(it.x, it.z) })

    /** Angolo interno (gradi) in `corners[i]`, tra le pareti `sides[i - 1]` e `sides[i]`. */
    fun interiorAngleDeg(i: Int): Double {
        val n = corners.size
        val a = corners[(i + n - 1) % n] - corners[i]
        val b = corners[(i + 1) % n] - corners[i]
        val deg = toDegrees(acos(((a dot b) / (a.length * b.length)).coerceIn(-1.0, 1.0)))
        // Angolo concavo: il poligono gira dall'altra parte rispetto al verso complessivo.
        val turn = (corners[i] - corners[(i + n - 1) % n]).cross(corners[(i + 1) % n] - corners[i])
        val area = corners.indices.sumOf { corners[it].cross(corners[(it + 1) % n]) }
        return if (turn * area < 0) 360.0 - deg else deg
    }

    /** Diagonali di una stanza a 4 lati: (angolo 0 – angolo 2) e (angolo 1 – angolo 3). Vuoto con un numero diverso di lati. */
    val diagonalsM: List<Double> get() = if (corners.size == 4) listOf(corners[0].distanceTo(corners[2]), corners[1].distanceTo(corners[3])) else emptyList()
}

object AssistedPerimeters {
    /** Distanza massima (m) di un angolo dalla retta della sua parete: gli angoli sono incroci di rette, quindi praticamente 0. */
    private const val ON_LINE_TOL_M = 1e-3

    fun detected(w: ConfirmedWall): DetectedWall = DetectedWall(w.line.a, w.line.b, w.line.minY, w.line.maxY, listOf(w.id), w.id)

    /**
     * Il perimetro chiuso dalle sole pareti confermate, o null se non si chiude. Ogni lato deve stare sulla retta di una parete
     * confermata diversa: se no (non dovrebbe succedere) il perimetro non vale.
     */
    fun of(walls: List<ConfirmedWall>): AssistedPerimeter? {
        if (walls.size < 3) return null
        val corners = WallOutline.layout(walls.map(::detected)).closed ?: return null
        val n = corners.size
        val used = HashSet<Int>()
        val sides = ArrayList<AssistedSide>(n)
        for (i in 0 until n) {
            val p = corners[i]
            val q = corners[(i + 1) % n]
            val wall = walls.filter { it.id !in used }.minByOrNull { max(distanceToLine(p, it.line), distanceToLine(q, it.line)) } ?: return null
            if (max(distanceToLine(p, wall.line), distanceToLine(q, wall.line)) > ON_LINE_TOL_M) return null
            used += wall.id
            sides += AssistedSide(wall.id, p, q, p.distanceTo(q), overlap(p, q, wall.line))
        }
        return AssistedPerimeter(corners, sides, walls.map { it.id }.filter { it !in used })
    }

    private fun distanceToLine(p: ArXZ, l: WallLine): Double = abs((p - l.a).cross(l.direction))

    /** Lunghezza del tratto osservato `l.a`–`l.b` che cade dentro il lato `p`–`q` (stessa retta). */
    private fun overlap(p: ArXZ, q: ArXZ, l: WallLine): Double {
        val d = l.direction
        val s0 = min((p - l.a) dot d, (q - l.a) dot d)
        val s1 = max((p - l.a) dot d, (q - l.a) dot d)
        val o0 = min(0.0, (l.b - l.a) dot d)
        val o1 = max(0.0, (l.b - l.a) dot d)
        return max(0.0, min(s1, o1) - max(s0, o0))
    }
}

/**
 * La sessione guidata: il motore, le pareti confermate e il perimetro che ne risulta. Immutabile, come [AssistedScan]:
 * ogni gesto dà la sessione nuova, così si prova senza telefono rigiocando una registrazione.
 */
data class AssistedSession(
    val state: AssistedState = AssistedScan.initial(),
    val params: AssistedParams = AssistedParams(),
    /** Ultima quota del pavimento nota (mondo AR): serve solo a portare il perimetro sul pavimento. */
    val floorY: Double? = null,
    /** Eventi dell'ultimo passo (frame o gesto). */
    val events: List<AssistedEvent> = emptyList(),
) {
    val walls: List<ConfirmedWall> get() = state.walls
    val candidate: CandidateWall? get() = state.candidate
    val perimeter: AssistedPerimeter? by lazy { AssistedPerimeters.of(state.walls) }

    fun step(f: AimFrame): AssistedSession {
        val r = AssistedScan.step(state, f, params)
        return copy(state = r.state, floorY = f.floorY ?: floorY, events = r.events)
    }

    fun confirm(timeMs: Long): AssistedSession = AssistedScan.confirm(state, timeMs, params).let { copy(state = it.state, events = it.events) }

    fun undo(timeMs: Long): AssistedSession = AssistedScan.undo(state, timeMs).let { copy(state = it.state, events = it.events) }

    /** La stanza per Sagoma, solo se il perimetro delle pareti confermate si chiude ed è valido; con le sole pareti del perimetro. */
    fun result(): ScanResult? {
        val p = perimeter ?: return null
        val y = floorY ?: walls.minOfOrNull { it.line.minY } ?: 0.0
        val room = WallOutline.toDraft(p.corners, y).toScannedRoom() ?: return null
        val byId = walls.associateBy { it.id }
        return ScanResult(room, p.sides.map { AssistedPerimeters.detected(byId.getValue(it.wallId)).toScanned(floorY) })
    }
}

/** Righe di testo per i log della prova (una riga per evento, leggibili con `adb logcat -s SagomaAssisted`). */
object AssistedLog {
    private fun f(v: Double, d: Int = 3) = formatDecimal(v, d, '.')
    private fun pt(p: ArXZ) = "(${f(p.x)};${f(p.z)})"

    fun wall(w: ConfirmedWall): String =
        "CONFERMATA W${w.id} ${pt(w.line.a)}->${pt(w.line.b)} osservata=${f(w.observedLengthM)}m dir=${f(w.line.headingDeg, 1)}° " +
            "quote=${f(w.line.minY)}..${f(w.line.maxY)} qualita=${w.quality.name} rms=${w.rmsM?.let { f(it) } ?: "-"} punti=${w.pointSupport} " +
            "piani=${w.planeKeys} avvisi=${w.advisories.map { it.name }}"

    fun undone(id: Int): String = "ANNULLATA W$id"

    fun perimeter(p: AssistedPerimeter): String = buildString {
        append("PERIMETRO lati=").append(p.sides.size)
        append(" perimetro=").append(f(p.perimeterM)).append("m area=").append(f(p.areaM2)).append("m2")
        append(" lati[").append(p.sides.joinToString(" ") { "W${it.wallId}:${f(it.lengthM)}m(oss ${f(it.observedM)} prol ${f(it.extendedM)})" }).append(']')
        append(" angoli[").append(p.corners.indices.joinToString(" ") { "W${p.sides[(it + p.sides.size - 1) % p.sides.size].wallId}-W${p.sides[it].wallId}:${f(p.interiorAngleDeg(it), 2)}°" }).append(']')
        if (p.diagonalsM.isNotEmpty()) append(" diagonali[").append(p.diagonalsM.joinToString(" ") { f(it) + "m" }).append(']')
        append(" escluse=").append(p.unusedWallIds.map { "W$it" })
        append(" angoli_xz[").append(p.corners.joinToString(" ") { pt(it) }).append(']')
    }
}
