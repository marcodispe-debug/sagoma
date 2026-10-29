package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.interiorOutline
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ritorno dal professionista: la planimetria del file IFC prende il posto di quella del progetto, e tutto il resto
 * (arredi, finiture, impianti, foto, note, quote) resta del progetto sagoma e si riaggancia alle stanze nuove.
 *
 * Stanze riconosciute (stesso id sagoma, stesso GlobalId o stessa posizione):
 * - rimaste uguali → la stanza resta quella di sagoma, intera (anche muri curvi e soffitti inclinati, che nel file IFC
 *   sono approssimati);
 * - spostate o girate senza cambiare forma → stanza nuova, e arredi, impianti e zone si spostano con lei;
 * - cambiate di forma → stanza nuova con le finiture di prima; gli arredi restano dove sono e quelli finiti fuori da ogni
 *   stanza (o dentro un muro) si elencano in [Report.toCheck].
 */
object IfcRoundTrip {
    class Report(
        val unchanged: Int,
        val moved: Int,
        val reshaped: Int,
        val added: Int,
        val removed: Int,
        val furnitureKept: Int,
        val furnitureMoved: Int,
        /** Arredi da guardare (piano, id): sono rimasti fuori dalle stanze o la loro stanza non c'è più. */
        val toCheck: List<Pair<Long, Long>>,
    )

    class Result(val building: Building, val report: Report)

    /** `key`: lo stesso passato a [IfcExport.export] (per riconoscere i GlobalId). */
    fun merge(original: Building, imported: IfcImport.Result, key: String): Result {
        var unchanged = 0; var moved = 0; var reshaped = 0; var added = 0; var removed = 0
        var kept = 0; var furnitureMoved = 0
        val toCheck = mutableListOf<Pair<Long, Long>>()
        val floors = imported.building.floors.mapIndexed { index, newFloor ->
            val old = original.floors.firstOrNull { it.id == newFloor.id } ?: original.floors.getOrNull(index)
                ?: return@mapIndexed newFloor
            val oldPlan = old.plan
            val used = HashSet<Long>()
            /** Trasformazione (stanza vecchia → nuova) per gli arredi di ogni stanza vecchia; null = la stanza non c'è più. */
            val moves = HashMap<Long, Rigid?>()
            val rooms = newFloor.plan.rooms.map { r ->
                val guid = imported.roomGuids[newFloor.id to r.id]
                val match = oldPlan.rooms.firstOrNull { it.id == r.id && it.id !in used && (imported.report.fromSagoma || guid == null) }
                    ?: oldPlan.rooms.firstOrNull { it.id !in used && guid != null && IfcGuid.of("$key|room/${old.id}/${it.id}") == guid }
                    ?: oldPlan.rooms.firstOrNull { it.id !in used && Polygon.contains(it.points, Polygon.labelPoint(r.points)) && Polygon.contains(r.points, Polygon.labelPoint(it.points)) }
                if (match == null) { added++; return@map r }
                used += match.id
                when {
                    sameRoom(match, r) -> { unchanged++; moves[match.id] = Rigid.Identity; match }
                    else -> {
                        val rigid = rigid(match.points, r.points)
                        moves[match.id] = rigid ?: Rigid.Identity
                        if (rigid != null && !rigid.isIdentity) moved++ else reshaped++
                        carryOver(match, r, rigid)
                    }
                }
            }
            removed += oldPlan.rooms.count { it.id !in used }
            for (r in oldPlan.rooms) if (r.id !in used) moves[r.id] = null
            val furniture = oldPlan.furniture.map { f ->
                val home = oldPlan.rooms.lastOrNull { Polygon.contains(it.points, f.center) }
                val move = home?.let { moves[it.id] }
                val placed = if (move != null && !move.isIdentity) { furnitureMoved++; move.apply(f) } else { kept++; f }
                val inside = rooms.any { Polygon.contains(it.interiorOutline(), placed.center) }
                if (!inside || (home != null && moves[home.id] == null)) toCheck += newFloor.id to f.id
                placed
            }
            Floor(
                id = old.id, name = newFloor.name, levelHeight = newFloor.levelHeight, autoLevel = old.autoLevel,
                plan = oldPlan.copy(
                    rooms = rooms,
                    freeWalls = newFloor.plan.freeWalls.map { fw -> oldPlan.freeWalls.firstOrNull { sameFreeWall(it, fw) } ?: fw },
                    columns = newFloor.plan.columns.map { c -> oldPlan.columns.firstOrNull { it.center.distanceTo(c.center) < 2 } ?: c },
                    // Scale e travi: il file non le rilegge ancora; restano quelle di sagoma se il professionista non le ha tolte.
                    stairs = oldPlan.stairs.filter { s -> !imported.report.fromSagoma || IfcGuid.of("$key|stair/${old.id}/${s.id}") in imported.guids },
                    beams = oldPlan.beams.filter { b -> !imported.report.fromSagoma || IfcGuid.of("$key|beam/${old.id}/${b.id}") in imported.guids },
                    furniture = furniture,
                ),
            ).synced()
        }
        val building = original.copy(floors = floors.ifEmpty { original.floors }, current = original.current.coerceIn(0, (floors.size - 1).coerceAtLeast(0)))
        return Result(building, Report(unchanged, moved, reshaped, added, removed, kept, furnitureMoved, toCheck))
    }

    /** Stessa stanza di prima: superficie e baricentro uguali entro pochi cm, stesse aperture. */
    fun sameRoom(old: Room, new: Room): Boolean {
        val a0 = old.interiorArea()
        val a1 = new.interiorArea()
        if (abs(a0 - a1) > maxOf(0.005 * a0, 500.0)) return false
        if (centroid(old.interiorOutline()).distanceTo(centroid(new.interiorOutline())) > 2.0) return false
        if (old.openings.size != new.openings.size) return false
        val p0 = old.openings.map { com.sagoma.planimetria.geometry.Openings.span(old, it).let { (a, b) -> (a + b) / 2.0 } }
        val p1 = new.openings.map { com.sagoma.planimetria.geometry.Openings.span(new, it).let { (a, b) -> (a + b) / 2.0 } }
        return p0.all { c -> p1.any { it.distanceTo(c) < 3.0 } }
    }

    /** Baricentro dell'area (non la media degli angoli: una curva ha tanti angoli da una parte sola). */
    fun centroid(pts: List<Vec2>): Vec2 {
        var a = 0.0; var cx = 0.0; var cy = 0.0
        for (i in pts.indices) {
            val p = pts[i]
            val q = pts[(i + 1) % pts.size]
            val c = p.x * q.y - q.x * p.y
            a += c; cx += (p.x + q.x) * c; cy += (p.y + q.y) * c
        }
        if (abs(a) < 1e-9) return pts.fold(Vec2.Zero) { acc, p -> acc + p } / pts.size.toDouble().coerceAtLeast(1.0)
        return Vec2(cx / (3 * a), cy / (3 * a))
    }

    fun sameFreeWall(a: com.sagoma.planimetria.model.FreeWall, b: com.sagoma.planimetria.model.FreeWall) =
        a.bulge == 0.0 && ((a.start.distanceTo(b.start) < 2 && a.end.distanceTo(b.end) < 2) || (a.start.distanceTo(b.end) < 2 && a.end.distanceTo(b.start) < 2)) &&
            abs(a.thickness - b.thickness) < 1 && a.openings.size == b.openings.size

    /** Stanza nuova con le finiture della vecchia; impianti e zone seguono la stanza se si è solo spostata. */
    fun carryOver(old: Room, new: Room, rigid: Rigid?): Room {
        val sameWalls = rigid != null && old.wallCount == new.wallCount
        return new.copy(
            name = new.name,
            floorFinish = old.floorFinish, floorMaterial = old.floorMaterial,
            wallPaint = old.wallPaint, wallFinish = old.wallFinish,
            wallPaints = if (sameWalls) rigid!!.remap(old.wallPaints, old.wallCount) else emptyMap(),
            wallFinishes = if (sameWalls) rigid!!.remap(old.wallFinishes, old.wallCount) else emptyMap(),
            parapet = old.parapet, parapetHeight = old.parapetHeight,
            photos = old.photos,
            fixtures = when {
                sameWalls -> old.fixtures.map { f -> if (f.kind.mount == Mount.Wall) f.copy(wallIndex = (f.wallIndex + rigid!!.shift) % old.wallCount) else f.copy(point = rigid!!.apply(f.point)) }
                else -> old.fixtures.filter { it.kind.mount == Mount.Ceiling && Polygon.contains(new.points, it.point) }
            },
            excludedZones = if (rigid != null) old.excludedZones.map { z -> z.copy(points = z.points.map(rigid::apply)) } else old.excludedZones,
        )
    }

    /** Spostamento rigido (rotazione attorno all'origine, poi traslazione) e di quanti muri slitta la numerazione. */
    class Rigid(val angle: Double, val move: Vec2, val shift: Int) {
        val isIdentity get() = abs(angle) < 1e-4 && move.length < 0.5
        fun apply(p: Vec2) = Vec2(p.x * cos(angle) - p.y * sin(angle), p.x * sin(angle) + p.y * cos(angle)) + move
        fun apply(f: Furniture) = f.copy(center = apply(f.center), rotation = f.rotation + angle * 180 / kotlin.math.PI)
        fun <T> remap(m: Map<Int, T>, n: Int) = m.mapKeys { (k, _) -> (k + shift) % n }

        companion object {
            val Identity = Rigid(0.0, Vec2.Zero, 0)
        }
    }

    /** La stanza ha la stessa forma (stessi lati, entro 2 cm) e si è solo spostata o girata: come. Null se è cambiata. */
    fun rigid(old: List<Vec2>, new: List<Vec2>): Rigid? {
        val n = old.size
        if (n != new.size || n < 3) return null
        fun len(p: List<Vec2>, i: Int) = p[i].distanceTo(p[(i + 1) % n])
        for (k in 0 until n) {
            if ((0 until n).all { i -> abs(len(old, i) - len(new, (i + k) % n)) < 2.0 }) {
                val d0 = old[1] - old[0]
                val d1 = new[(k + 1) % n] - new[k]
                val angle = atan2(d1.y, d1.x) - atan2(d0.y, d0.x)
                val r = Rigid(angle, Vec2.Zero, k)
                val move = new[k] - r.apply(old[0])
                val rigid = Rigid(angle, move, k)
                if ((0 until n).all { i -> rigid.apply(old[i]).distanceTo(new[(i + k) % n]) < 3.0 }) return rigid
            }
        }
        return null
    }
}
