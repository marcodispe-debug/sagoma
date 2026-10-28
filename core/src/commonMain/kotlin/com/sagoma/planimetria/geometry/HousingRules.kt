package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.SashType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Luce e aria naturali delle stanze (rapporto aeroilluminante) e minimi delle norme italiane per le case (DM 5 luglio 1975):
 * finestre apribili che danno fuori almeno 1/8 del pavimento nelle stanze da abitare, soffitti alti almeno 2,70 m (2,40 in
 * bagni e corridoi), camere da letto di almeno 9 m² e soggiorno di almeno 14 m². Si guarda la casa come sarà (stato di progetto).
 */
object HousingRules {
    /** Finestre apribili almeno 1/8 del pavimento nelle stanze da abitare. */
    const val MIN_RATIO = 1.0 / 8

    /** Altezza minima del soffitto (cm) nelle stanze da abitare e nei servizi (bagni, corridoi, ripostigli). */
    const val MIN_HEIGHT_LIVING_CM = 270.0
    const val MIN_HEIGHT_SERVICE_CM = 240.0

    /** Superficie minima (cm²) di una camera da letto (singola: per due persone 14 m², ma dal disegno non si sa) e del soggiorno. */
    const val MIN_BEDROOM_CM2 = 90_000.0
    const val MIN_LIVING_CM2 = 140_000.0

    /** Tratto minimo (cm) di muro tolto in comune perché due stanze siano un ambiente unico. */
    private const val OPEN_MIN_RUN_CM = 30.0

    /** Stanze da abitare: hanno bisogno di luce e aria dirette e del soffitto alto almeno 2,70 m. */
    fun habitable(t: RoomType): Boolean =
        t == RoomType.Soggiorno || t == RoomType.Camera || t == RoomType.Cameretta || t == RoomType.Cucina || t == RoomType.Studio

    /** Servizi con il soffitto alto almeno 2,40 m. */
    fun service(t: RoomType): Boolean = t == RoomType.Bagno || t == RoomType.Corridoio

    /** Stanza con tutti i muri da demolire: nella casa come sarà non c'è più. */
    fun demolished(r: Room): Boolean = r.wallCount > 0 && (0 until r.wallCount).all { r.phaseOf(it) == Phase.Demolish }

    /**
     * L'ambiente di cui fa parte la stanza: lei e le stanze al chiuso unite dove tutte e due hanno tolto il muro in comune, o lo
     * demoliscono (soggiorno con cucina a vista). La stanza stessa è sempre la prima.
     */
    fun space(plan: FloorPlan, room: Room): List<Room> {
        if (room.outdoor) return listOf(room)
        val indoor = plan.rooms.filter { !it.outdoor && !demolished(it) }
        val out = mutableListOf(room)
        var k = 0
        while (k < out.size) {
            val r = out[k++]
            for (other in indoor) if (out.none { it.id == other.id } && joined(r, other)) out += other
        }
        return out
    }

    /**
     * Gli ambienti da abitare del piano, ognuno una volta sola (il soggiorno con la cucina a vista insieme, il soggiorno per
     * primo), senza le stanze tutte da demolire.
     */
    fun livingSpaces(plan: FloorPlan): List<List<Room>> {
        val seen = mutableSetOf<Long>()
        val out = mutableListOf<List<Room>>()
        for (r in plan.rooms.sortedByDescending { it.type == RoomType.Soggiorno }) {
            if (r.outdoor || !habitable(r.type) || demolished(r) || r.id in seen) continue
            val s = space(plan, r)
            seen += s.map { it.id }
            out += s
        }
        return out
    }

    /** Muro che nella casa come sarà non c'è: tolto o da demolire. */
    private fun open(r: Room, i: Int) = r.isRemoved(i) || r.phaseOf(i) == Phase.Demolish

    private fun joined(a: Room, b: Room): Boolean {
        for (i in 0 until a.wallCount) {
            if (!open(a, i)) continue
            for (j in 0 until b.wallCount) {
                if (!open(b, j)) continue
                if (Dimensions.overlap(a.wallStart(i), a.wallEnd(i), b.wallStart(j), b.wallEnd(j)) >= OPEN_MIN_RUN_CM) return true
            }
        }
        return false
    }

    /**
     * Finestre e porte-finestre della stanza che portano luce e aria da fuori: apribili (non fisse), non da chiudere, su un muro
     * che c'è e non verso un'altra stanza al chiuso (verso un balcone sì).
     */
    fun windows(plan: FloorPlan, room: Room): List<Opening> = room.openings.filter { o ->
        val i = o.wallIndex
        o.kind.glazed && i in 0 until room.wallCount && !room.isRemoved(i) && o.phase != Phase.Demolish &&
            room.phaseOf(i) != Phase.Demolish && (o.sliding || o.sash != SashType.Fixed) &&
            o.width > 0 && o.height > 0 && !facesIndoor(plan, room, o)
    }

    private fun facesIndoor(plan: FloorPlan, room: Room, o: Opening): Boolean {
        val (a, b) = Openings.span(room, o)
        val len = a.distanceTo(b)
        if (len < 1e-6) return false
        return plan.rooms.any { r ->
            r.id != room.id && !r.outdoor && (0 until r.wallCount).any { j -> Dimensions.overlap(a, b, r.wallStart(j), r.wallEnd(j)) >= 0.5 * len }
        }
    }

    /** Superficie (cm²) delle finestre apribili verso fuori dell'ambiente, misurate sul vano del muro con la loro forma. */
    fun windowArea(plan: FloorPlan, space: List<Room>): Double =
        space.sumOf { r -> windows(plan, r).sumOf { Openings.holeArea(r, it) } }

    /** Pavimento (cm²) dell'ambiente, senza le zone non calpestabili: quello dei metri quadri della pianta. */
    fun floorArea(space: List<Room>): Double = space.sumOf { it.usableArea() }

    /** Finestre / pavimento dell'ambiente; null senza pavimento. */
    fun ratio(plan: FloorPlan, space: List<Room>): Double? {
        val floor = floorArea(space)
        return if (floor < 1.0) null else windowArea(plan, space) / floor
    }

    /**
     * Finestre sotto 1/8 del pavimento dell'ambiente, confrontando le misure arrotondate al centesimo di m² come si leggono (così
     * non esce «serve 1,81 m²» con 1,81 m² di finestre).
     */
    fun belowMinimum(plan: FloorPlan, space: List<Room>): Boolean =
        (windowArea(plan, space) / 100).roundToInt() < (floorArea(space) * MIN_RATIO / 100).roundToInt()

    /** Il rapporto come frazione «1/8,5» (una cifra dopo la virgola, intera da 1/100 in giù). */
    fun fraction(ratio: Double): String {
        if (ratio <= 0) return "0"
        val x = 1 / ratio
        return "1/" + if (x >= 100) formatDecimal(x, 0) else formatDecimal(x, 1)
    }

    /** Il soffitto è piano (stessa altezza su tutti i muri, nessuna parete tagliata dal tetto): ha un'altezza sola. */
    fun flatCeiling(room: Room): Boolean = room.wallCuts.isEmpty() && room.wallHeights.values.all { abs(it - room.ceilingHeight) < 0.5 }
}
