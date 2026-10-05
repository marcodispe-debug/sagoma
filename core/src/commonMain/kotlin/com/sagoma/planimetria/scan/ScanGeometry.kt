package com.sagoma.planimetria.scan

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.roundHalfUp
import com.sagoma.planimetria.model.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Punto nel mondo AR, in **metri**, come lo dà la sessione di realtà aumentata (x a destra, y in su, z verso chi guarda). */
data class ArPoint(val x: Double, val y: Double, val z: Double)

/**
 * L'unica trasformazione dal mondo AR (metri) alla pianta di Sagoma (centimetri).
 *
 * Il mondo AR ha y verso l'ALTO (la verticale) e un sistema destrorso: guardando la stanza dall'alto, x va a destra e z va verso
 * il BASSO dello schermo. La pianta di Sagoma ha y verso il basso: quindi `pianta.x = x · 100` e `pianta.y = z · 100`,
 * mentre la quota `y` del mondo AR non entra in pianta. Così la stanza non viene specchiata (l'ordine orario resta orario).
 * L'orientamento rispetto al nord è arbitrario (dipende da come è partita la sessione): lo sistema [ScanGeometry.alignedInterior].
 */
object ScanCoordinates {
    const val CM_PER_M = 100.0

    fun toPlan(p: ArPoint): Vec2 = Vec2(p.x * CM_PER_M, p.z * CM_PER_M)

    /** Distanza in cm, in pianta, tra due punti AR (la differenza di quota non conta). */
    fun distanceCm(a: ArPoint, b: ArPoint): Double = toPlan(a).distanceTo(toPlan(b))
}

/**
 * Perimetro in costruzione: i punti toccati sul pavimento, in ordine, e se il poligono è chiuso. Immutabile: ogni operazione
 * restituisce la bozza nuova, così annullare e cancellare sono semplici e si provano senza telefono.
 */
data class ScanDraft(val points: List<ArPoint> = emptyList(), val closed: Boolean = false) {

    /** Angoli in pianta (cm), nell'ordine toccato. */
    val corners: List<Vec2> get() = points.map(ScanCoordinates::toPlan)

    val canClose: Boolean get() = !closed && points.size >= MIN_CORNERS

    /**
     * Aggiunge un angolo. Non fa niente se il poligono è chiuso o se il punto è praticamente quello di prima (doppio tocco);
     * toccando vicino al primo angolo con almeno tre punti il poligono si chiude (senza aggiungere il punto).
     */
    fun add(p: ArPoint): ScanDraft {
        if (closed) return this
        val last = points.lastOrNull()
        if (last != null && ScanCoordinates.distanceCm(last, p) < MIN_GAP_CM) return this
        if (points.size >= MIN_CORNERS && ScanCoordinates.distanceCm(points.first(), p) <= CLOSE_RADIUS_CM) return copy(closed = true)
        return copy(points = points + p)
    }

    /** Annulla l'ultimo gesto: da chiuso riapre il poligono, altrimenti toglie l'ultimo punto. */
    fun undoLast(): ScanDraft = if (closed) copy(closed = false) else copy(points = points.dropLast(1))

    fun clear(): ScanDraft = ScanDraft()

    /** Chiude il poligono (l'ultimo punto si collega al primo); serve almeno [MIN_CORNERS] punti. */
    fun close(): ScanDraft = if (canClose) copy(closed = true) else this

    /**
     * Lunghezze (cm) dei lati: il lato `i` va dal punto `i` al successivo; se il poligono è chiuso l'ultimo lato torna al primo
     * punto. Con un solo punto non ce ne sono.
     */
    val edgeLengthsCm: List<Double>
        get() {
            val c = corners
            val n = c.size
            if (n < 2) return emptyList()
            val count = if (closed) n else n - 1
            return List(count) { c[it].distanceTo(c[(it + 1) % n]) }
        }

    val perimeterCm: Double get() = edgeLengthsCm.sum()

    /** Area (m²) del poligono chiuso; 0 se non è chiuso. */
    val areaM2: Double get() = if (closed) Polygon.area(corners) / 10_000.0 else 0.0

    /**
     * La stanza scansionata, solo se il poligono è chiuso, non si incrocia e ha un'area ragionevole; altrimenti null (e la
     * schermata dice di correggere i punti).
     */
    fun toScannedRoom(): ScannedRoom? {
        if (!closed || points.size < MIN_CORNERS) return null
        val c = corners
        if (!ScanGeometry.isSimple(c) || Polygon.area(c) < MIN_AREA_CM2) return null
        return ScannedRoom(c)
    }

    companion object {
        const val MIN_CORNERS = 3
        /** Due tocchi più vicini di così (cm) sono lo stesso punto. */
        const val MIN_GAP_CM = 5.0
        /** Toccando entro questa distanza (cm) dal primo angolo il poligono si chiude. */
        const val CLOSE_RADIUS_CM = 25.0
        /** Sotto 0,25 m² non è una stanza. */
        const val MIN_AREA_CM2 = 2_500.0
    }
}

object ScanGeometry {

    /** Poligono semplice: nessun lato ne attraversa un altro non adiacente. */
    fun isSimple(c: List<Vec2>): Boolean {
        val n = c.size
        if (n < 3) return false
        for (i in 0 until n) for (j in i + 1 until n) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue // lati adiacenti
            if (segmentsCross(c[i], c[(i + 1) % n], c[j], c[(j + 1) % n])) return false
        }
        return true
    }

    private fun segmentsCross(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Boolean {
        val d1 = (b - a).cross(c - a)
        val d2 = (b - a).cross(d - a)
        val d3 = (d - c).cross(a - c)
        val d4 = (d - c).cross(b - c)
        return d1 * d2 < 0 && d3 * d4 < 0
    }

    /**
     * Angoli interni per Sagoma: in ordine orario sullo schermo, ruotati in modo che il lato più lungo sia orizzontale
     * (l'orientamento AR è casuale: una stanza storta in pianta sarebbe scomoda), con l'angolo in alto a sinistra in (0, 0)
     * e i valori arrotondati al millimetro. Rotazione e traslazione non cambiano lunghezze né angoli.
     */
    fun alignedInterior(corners: List<Vec2>): List<Vec2> {
        val n = corners.size
        if (n < 3) return corners
        val ordered = if (Polygon.signedArea(corners) >= 0) corners else corners.reversed()
        var longest = 0
        var best = -1.0
        for (i in 0 until n) {
            val len = ordered[i].distanceTo(ordered[(i + 1) % n])
            if (len > best + 1e-9) { best = len; longest = i }
        }
        val dir = ordered[(longest + 1) % n] - ordered[longest]
        val angle = -atan2(dir.y, dir.x)
        val ca = cos(angle)
        val sa = sin(angle)
        val rotated = ordered.map { Vec2(it.x * ca - it.y * sa, it.x * sa + it.y * ca) }
        val b = Polygon.bounds(rotated)
        return rotated.map { Vec2(round1(it.x - b.minX), round1(it.y - b.minY)) }
    }

    private fun round1(v: Double): Double = roundHalfUp(v * 10.0) / 10.0
}
