package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.SashType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Luce e aria naturali delle stanze (rapporto aeroilluminante) e minimi delle norme italiane per le case (DM 5 luglio 1975):
 * finestre apribili che danno fuori almeno 1/8 del pavimento nelle stanze da abitare, soffitti alti almeno 2,70 m (2,40 in
 * bagni e corridoi), camere da letto di almeno 9 m² (14 per due persone: un letto matrimoniale o a castello, due letti) e
 * soggiorno di almeno 14 m²; il bagno senza finestra verso fuori ha bisogno dell'aspirazione meccanica; almeno un bagno con vaso,
 * bidè, vasca o doccia e lavabo; 14 m² a persona per le prime quattro e 10 per le altre, il monolocale almeno 28 m² (38 per due).
 * Si guarda la casa come sarà (stato di progetto).
 */
object HousingRules {
    /** Finestre apribili almeno 1/8 del pavimento nelle stanze da abitare. */
    const val MIN_RATIO = 1.0 / 8

    /** Altezza minima del soffitto (cm) nelle stanze da abitare e nei servizi (bagni, corridoi, ripostigli). */
    const val MIN_HEIGHT_LIVING_CM = 270.0
    const val MIN_HEIGHT_SERVICE_CM = 240.0

    /** Superficie minima (cm²) di una camera da letto per una persona, per due e del soggiorno. */
    const val MIN_BEDROOM_CM2 = 90_000.0
    const val MIN_DOUBLE_BEDROOM_CM2 = 140_000.0
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

    /** Il rapporto come frazione «1/8,5» (una cifra dopo la virgola, intera da 1/100 in giù); «—» senza finestre. */
    fun fraction(ratio: Double): String {
        if (ratio <= 0) return "—"
        val x = 1 / ratio
        return "1/" + if (x >= 100) formatDecimal(x, 0) else formatDecimal(x, 1)
    }

    /** Letto per due persone: largo almeno così (cm). */
    const val DOUBLE_BED_CM = 140.0

    /**
     * Persone che dormono nel letto `f`: 2 se è largo almeno [DOUBLE_BED_CM] o a castello, 1 per gli altri letti (divani letto aperti,
     * futon e materassi compresi), 0 se non è un letto (culle e lettini dei bambini non contano). I letti del catalogo si riconoscono dal
     * nome italiano, quelli generati dal tipo.
     */
    fun sleepers(f: Furniture): Int {
        val narrow = min(f.width, f.depth)
        ParametricFurniture.parse(f.model)?.let { s ->
            if (s.kind != ParametricFurniture.Kind.BED && s.kind != ParametricFurniture.Kind.SINGLE_BED) return 0
            return if (narrow >= DOUBLE_BED_CM) 2 else 1
        }
        val label = FurnitureCatalog.item(f.model)?.label ?: return 0
        val bed = label.startsWith("Letto") || label.startsWith("Divano letto aperto") || label.startsWith("Futon") || label.startsWith("Materasso")
        return when {
            !bed -> 0
            label.startsWith("Letto a castello") || narrow >= DOUBLE_BED_CM -> 2
            else -> 1
        }
    }

    /** Persone che dormono nella stanza: i letti col centro dentro. */
    fun sleepers(plan: FloorPlan, room: Room): Int {
        val inside = room.interior()
        if (inside.size < 3) return 0
        return plan.furniture.filter { Polygon.contains(inside, it.center) }.sumOf { sleepers(it) }
    }

    /** Sanitari che almeno un bagno della casa deve avere. */
    enum class Sanitary { Toilet, Bidet, BathOrShower, Basin }

    /**
     * Che sanitario è l'arredo `f` (null se non lo è), dal nome italiano degli arredi da bagno del catalogo (il lavello della cucina
     * «a una vasca» non è una vasca). Largo di manica: la porta della doccia o il miscelatore della vasca dicono che la doccia o la
     * vasca ci sono.
     */
    fun sanitary(f: Furniture): Sanitary? {
        val item = FurnitureCatalog.item(f.model)?.takeIf { it.category == "Bagno" } ?: return null
        val label = item.label
        return when {
            label.startsWith("Water") || label.startsWith("WC") || label.startsWith("Toilette") || label.startsWith("Turca") -> Sanitary.Toilet
            label.startsWith("Bidè") || label.startsWith("Bidet") -> Sanitary.Bidet
            label.contains("vasca", ignoreCase = true) || label.contains("doccia", ignoreCase = true) -> Sanitary.BathOrShower
            label.contains("lavabo", ignoreCase = true) || label.startsWith("Lavandino") || label.startsWith("Lavamani") -> Sanitary.Basin
            else -> null
        }
    }

    /** Nella stanza c'è un termoarredo (scaldasalviette) del catalogo: scalda come un calorifero. */
    fun towelWarmer(plan: FloorPlan, room: Room): Boolean {
        val inside = room.interior()
        if (inside.size < 3) return false
        return plan.furniture.any { f -> FurnitureCatalog.item(f.model)?.label?.startsWith("Termoarredo") == true && Polygon.contains(inside, f.center) }
    }

    /** Sanitari nella stanza: gli arredi col centro dentro. */
    fun sanitaries(plan: FloorPlan, room: Room): Set<Sanitary> {
        val inside = room.interior()
        if (inside.size < 3) return emptySet()
        return plan.furniture.filter { Polygon.contains(inside, it.center) }.mapNotNull { sanitary(it) }.toSet()
    }

    /** Superficie minima (cm²) della casa per `persons` persone: 14 m² a testa per le prime quattro, 10 per le altre. */
    fun minHome(persons: Int): Double = (14.0 * min(persons, 4) + 10.0 * (persons - 4).coerceAtLeast(0)) * 10_000

    /** Superficie minima (cm²) del monolocale, bagno compreso: 28 m² per una persona, 38 per due. */
    fun minStudio(persons: Int): Double = if (persons >= 2) 380_000.0 else 280_000.0

    /** Superficie minima (cm²) della camera da letto, per una persona o per due secondo i letti che ci sono. */
    fun minBedroom(plan: FloorPlan, room: Room): Double = if (sleepers(plan, room) >= 2) MIN_DOUBLE_BEDROOM_CM2 else MIN_BEDROOM_CM2

    /** Il soffitto è piano (stessa altezza su tutti i muri, nessuna parete tagliata dal tetto): ha un'altezza sola. */
    fun flatCeiling(room: Room): Boolean = room.wallCuts.isEmpty() && room.wallHeights.values.all { abs(it - room.ceilingHeight) < 0.5 }
}
