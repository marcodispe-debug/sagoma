package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * Luce che passa da una stanza all'altra attraverso porte, passaggi e muri tolti. Ogni lampada illumina solo la sua stanza
 * ([Scene3D.SceneLight.room]: la luce non passa i muri); da ogni varco verso una stanza vicina esce la parte della sua luce che
 * lo attraversa, e nella stanza vicina diventa una luce morbida sulla soglia, rivolta verso dove andava la luce (come fanno
 * i programmi di rendering con i "portali"). Un solo passaggio: la luce non rimbalza poi in una terza stanza.
 */
object LightSpill {
    /** Sotto questo flusso (lumen) la luce che passa non si vede: niente luce in più. */
    const val MIN_LUMENS = 15.0

    /** Quota di luce che le pareti di una stanza rimandano (pareti chiare): anche questa esce dai varchi. */
    private const val BOUNCE = 0.35

    /** Varco tra due stanze: da `from` verso `to`, largo da `a` a `b` in pianta, alto fino a `top`; `into` punta dentro `to`. */
    data class Portal(val from: Room, val to: Room, val a: Vec2, val b: Vec2, val top: Double, val into: Vec2)

    /**
     * Varchi del piano (verso la stanza di là con [Room.outwardOf], giusto anche per le stanze disegnate in senso antiorario): porte e passaggi su un muro a contatto con quello di un'altra stanza ([Openings.sharedGaps]), e muri tolti
     * da entrambe le stanze dove si toccano (ambienti aperti). Ognuno nei due versi.
     */
    fun portals(plan: FloorPlan): List<Portal> {
        val out = mutableListOf<Portal>()
        fun add(from: Room, to: Room, a: Vec2, b: Vec2, top: Double, into: Vec2) {
            if (a.distanceTo(b) < 20.0 || top < 50.0) return
            val c = (a + b) * 0.5
            if (out.any { it.from.id == from.id && it.to.id == to.id && ((it.a + it.b) * 0.5).distanceTo(c) < 5.0 }) return
            out += Portal(from, to, a, b, top, into)
            out += Portal(to, from, a, b, top, into * -1.0)
        }
        for (room in plan.rooms) {
            for (o in room.openings) {
                if (!o.kind.createsSharedGap || o.wallIndex >= room.wallCount) continue
                val (a, b) = Openings.span(room, o)
                for (other in plan.rooms) {
                    if (other.id == room.id) continue
                    if ((0 until other.wallCount).none { Dimensions.inContact(a, b, other.wallStart(it), other.wallEnd(it)) }) continue
                    add(room, other, a, b, o.height, room.outwardOf(o.wallIndex))
                }
            }
            for (i in room.removedWalls) {
                if (i >= room.wallCount) continue
                val s = room.wallStart(i)
                val e = room.wallEnd(i)
                val len = s.distanceTo(e)
                if (len < 1e-6) continue
                val u = (e - s) / len
                for (other in plan.rooms) {
                    if (other.id == room.id) continue
                    for (j in other.removedWalls) {
                        if (j >= other.wallCount || !Dimensions.inContact(s, e, other.wallStart(j), other.wallEnd(j))) continue
                        val t0 = ((other.wallStart(j) - s) dot u).coerceIn(0.0, len)
                        val t1 = ((other.wallEnd(j) - s) dot u).coerceIn(0.0, len)
                        add(room, other, s + u * min(t0, t1), s + u * max(t0, t1), min(room.ceilingHeight, other.ceilingHeight), room.outwardOf(i))
                    }
                }
            }
        }
        return out
    }

    /**
     * Le luci che le lampade di `lights` mandano nelle stanze vicine attraverso i varchi di `plan` (piano a quota 0): una per
     * varco illuminato, nella stanza di là, con il flusso che passa (diretto dalle lampade più quello rimandato dalle pareti).
     * `spaces`: ambienti aperti in cui le lampade illuminano tutte le stanze ([LightZones.spaces]): tra le loro stanze non passa
     * niente in più, e dai loro varchi verso le altre stanze esce la luce di tutte le lampade dell'ambiente.
     */
    fun through(plan: FloorPlan, lights: List<Scene3D.SceneLight>, spaces: List<List<Room>> = emptyList()): List<Scene3D.SceneLight> {
        val portals = portals(plan)
        if (portals.isEmpty() || lights.isEmpty()) return emptyList()
        val boxes = plan.rooms.associate { it.id to Polygon.bounds(it.points) }
        val spaceOf = HashMap<Long, List<Room>>()
        for (s in spaces) for (r in s) spaceOf[r.id] = s
        val out = mutableListOf<Scene3D.SceneLight>()
        for (p in portals) {
            val space = spaceOf[p.from.id]
            if (space != null && space === spaceOf[p.to.id]) continue
            val sources = space ?: listOf(p.from)
            val fromBoxes = sources.mapNotNull { boxes[it.id] }.toSet()
            val own = lights.filter { it.room in fromBoxes }
            if (own.isEmpty()) continue
            val area = p.a.distanceTo(p.b) * p.top
            val surface = LightZones.surface(sources)
            val n = Vec3(p.into.x, 0.0, p.into.y)
            var flux = 0.0
            var warmFlux = 0.0
            var dir = Vec3.Zero
            for (l in own) {
                var direct = 0.0
                var lampDir = Vec3.Zero
                // Il varco diviso in pezzi: la luce che arriva su ognuno, secondo la distanza e la forma della lampada.
                for (k in 0 until COLUMNS) for (h in 0 until ROWS) {
                    val s = (p.a + (p.b - p.a) * ((k + 0.5) / COLUMNS)).at(p.top * (h + 0.5) / ROWS)
                    val d = s - l.position
                    val r2 = max(d dot d, 900.0)
                    val u = d * (1 / kotlin.math.sqrt(r2))
                    val facing = u dot n
                    if (facing <= 0.0) continue
                    val intensity = l.lumens / (4 * PI) * l.profile.norm * l.profile.shape(u dot l.beamAxis.normalized())
                    val part = intensity * facing * (area / (COLUMNS * ROWS)) / r2
                    direct += part
                    lampDir += u * part
                }
                val bounce = if (surface > 0) l.lumens * BOUNCE * area / surface else 0.0
                val total = direct + bounce
                flux += total
                if (l.warm) warmFlux += total
                dir += lampDir + n * bounce
            }
            if (flux < MIN_LUMENS) continue
            val center = ((p.a + p.b) * 0.5).at(p.top * 0.5) + n * 15.0
            val toBox = boxes[p.to.id] ?: continue
            out += Scene3D.SceneLight(
                center, null, flux, warm = warmFlux >= flux / 2, room = toBox,
                profile = LightProfile.Wall, axis = dir.normalized().takeIf { it.length > 0.5 } ?: n, spill = true,
            )
        }
        return out
    }

    private const val COLUMNS = 4
    private const val ROWS = 4
}
