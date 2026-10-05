package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2

/**
 * Oggetto che non ci sta sotto la parete: arriva più in alto del bordo superiore del muro, di solito
 * perché la parete è tagliata in diagonale (sottotetto) o il muro è più basso del soffitto.
 * `openingId` o `fixtureId` dice di quale oggetto si tratta; `point` è dove disegnare l'avviso.
 */
data class WallCollision(
    val roomId: Long,
    val wallIndex: Int,
    val openingId: Long? = null,
    val fixtureId: Long? = null,
    val point: Vec2,
    /** Quota del bordo superiore dell'oggetto (cm). */
    val objectTop: Double,
    /** Altezza della parete nel punto più basso sopra l'oggetto (cm). */
    val wallTop: Double,
)

object Collisions {

    /** Tolleranza: un oggetto che sfiora la parete (entro mezzo centimetro) non è una collisione. */
    private const val TOLERANCE = 0.5

    /** Tutti gli oggetti della pianta che superano la parete su cui stanno. */
    fun of(plan: FloorPlan): List<WallCollision> = plan.rooms.flatMap { room -> ofRoom(plan, room) }

    private fun ofRoom(plan: FloorPlan, room: Room): List<WallCollision> {
        val out = mutableListOf<WallCollision>()
        for (o in room.openings) {
            if (o.wallIndex >= room.wallCount) continue
            val (a, b) = Openings.span(room, o)
            val top = (if (o.kind.glazed) o.sillHeight else 0.0) + o.height
            val wall = lowestTop(plan, room, o.wallIndex, a, b, bothSides = true)
            if (top > wall + TOLERANCE) out += WallCollision(room.id, o.wallIndex, openingId = o.id, point = (a + b) / 2.0, objectTop = top, wallTop = wall)
        }
        for (f in room.fixtures) {
            if (f.kind.mount != Mount.Wall || f.wallIndex >= room.wallCount) continue
            val (p, _) = Fixtures.wallAnchor(room, f)
            val along = (room.wallEnd(f.wallIndex) - room.wallStart(f.wallIndex)).normalized()
            val half = if (f.kind == FixtureKind.Radiator) f.length / 2 else 0.0
            val top = f.elevation + when (f.kind) {
                FixtureKind.Radiator -> f.height
                FixtureKind.Outlet -> 4.0
                FixtureKind.Switch -> 6.0
                FixtureKind.WaterPoint -> 3.0
                FixtureKind.WallLight -> Scene3D.WALL_LIGHT_HEIGHT / 2
                FixtureKind.WallSpot -> Scene3D.WALL_SPOT_SIZE / 2
                else -> 0.0
            }
            // Un impianto sta su una sola faccia: conta solo la parete della sua stanza.
            val wall = lowestTop(plan, room, f.wallIndex, p - along * half, p + along * half, bothSides = false)
            if (top > wall + TOLERANCE) out += WallCollision(room.id, f.wallIndex, fixtureId = f.id, point = p, objectTop = top, wallTop = wall)
        }
        return out
    }

    /**
     * Altezza minima della parete tra i punti a e b del muro: il bordo superiore è fatto di tratti dritti,
     * quindi basta guardare le estremità e i punti in cui il taglio cambia pendenza.
     */
    private fun lowestTop(plan: FloorPlan, room: Room, i: Int, a: Vec2, b: Vec2, bothSides: Boolean): Double {
        val s = room.wallStart(i)
        val u = (room.wallEnd(i) - s).normalized()
        val ta = (a - s) dot u
        val tb = (b - s) dot u
        val lo = minOf(ta, tb)
        val hi = maxOf(ta, tb)
        // Punti in cui cambia la pendenza: l'inizio della discesa sui muri tagliati che stanno su questa linea.
        val kinks = plan.rooms.flatMap { r -> (0 until r.wallCount).mapNotNull { j -> Ceilings.startPoint(r, j) } }
            .map { (it - s) dot u }.filter { it > lo && it < hi }
        return (listOf(lo, hi) + kinks).minOf { t ->
            when {
                // Balcone: contro la casa conta il muro della casa, verso l'esterno il parapetto.
                room.outdoor -> faces(plan, room, i, s + u * t).max()
                bothSides -> faces(plan, room, i, s + u * t).min()
                else -> Ceilings.wallTopAt(room, i, t)
            }
        }
    }

    /**
     * Altezza delle facce del muro nel punto p: quella di questa stanza e, se il muro è in comune, quelle
     * delle stanze dall'altra parte. Un'apertura attraversa tutto il muro, quindi deve starci sotto entrambe.
     */
    private fun faces(plan: FloorPlan, room: Room, i: Int, p: Vec2): List<Double> {
        val s = room.wallStart(i)
        val e = room.wallEnd(i)
        val own = Ceilings.wallTopAt(room, i, (p - s) dot (e - s).normalized())
        // Un balcone confinante non ha una sua faccia del muro: lì c'è solo il muro della casa.
        val others = plan.rooms.filter { it.id != room.id && !it.outdoor }.flatMap { o ->
            (0 until o.wallCount).filter { j -> Dimensions.inContact(s, e, o.wallStart(j), o.wallEnd(j)) }.mapNotNull { j ->
                val tj = (p - o.wallStart(j)) dot (o.wallEnd(j) - o.wallStart(j)).normalized()
                if (tj < -1 || tj > o.wallLength(j) + 1) null else Ceilings.wallTopAt(o, j, tj)
            }
        }
        return listOf(own) + others
    }
}
