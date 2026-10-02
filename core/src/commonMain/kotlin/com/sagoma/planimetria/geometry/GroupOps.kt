package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs

/** Oggetto della pianta in una selezione multipla. */
sealed interface GroupItem {
    val id: Long
    data class RoomItem(override val id: Long) : GroupItem
    data class FurnitureItem(override val id: Long) : GroupItem
    data class ColumnItem(override val id: Long) : GroupItem
    data class BeamItem(override val id: Long) : GroupItem
    data class FreeWallItem(override val id: Long) : GroupItem
    data class StairItem(override val id: Long) : GroupItem
    data class RulerItem(override val id: Long) : GroupItem
    /** Quota manuale e testo del disegno. */
    data class DimensionItem(override val id: Long) : GroupItem
    data class AnnotationItem(override val id: Long) : GroupItem
}

/** Come si allineano gli oggetti selezionati (sui bordi o sui centri del loro ingombro). */
enum class Alignment(val label: String) {
    Left("A sinistra"), CenterX("Al centro (orizz.)"), Right("A destra"),
    Top("In alto"), CenterY("Al centro (vert.)"), Bottom("In basso"),
}

/**
 * Operazioni su più oggetti insieme, come nei CAD: sposta, ruota di 90°, specchia, copia, serie, allinea,
 * distribuisci, elimina. Le stanze portano con sé porte, finestre e impianti; gli oggetti ruotati o specchiati
 * restano coerenti (verso delle porte, scale a destra o a sinistra, stanze sempre in senso orario).
 */
object GroupOps {

    /** Oggetti della pianta dentro il rettangolo (`crossing` = anche quelli che lo toccano, come in AutoCAD). */
    fun inRect(plan: FloorPlan, a: Vec2, b: Vec2, crossing: Boolean): Set<GroupItem> {
        val minX = minOf(a.x, b.x); val maxX = maxOf(a.x, b.x)
        val minY = minOf(a.y, b.y); val maxY = maxOf(a.y, b.y)
        fun inside(p: Vec2) = p.x in minX..maxX && p.y in minY..maxY
        fun take(pts: List<Vec2>) = if (crossing) pts.any(::inside) || rectCrosses(pts, minX, minY, maxX, maxY) else pts.all(::inside)
        val out = mutableSetOf<GroupItem>()
        plan.rooms.forEach { if (take(it.points)) out += GroupItem.RoomItem(it.id) }
        plan.furniture.forEach { if (take(Furnishings.outline(it))) out += GroupItem.FurnitureItem(it.id) }
        plan.columns.forEach { if (take(Structure.outline(it))) out += GroupItem.ColumnItem(it.id) }
        plan.beams.forEach { if (take(listOf(it.start, it.end))) out += GroupItem.BeamItem(it.id) }
        plan.freeWalls.forEach { if (take(listOf(it.start, it.end))) out += GroupItem.FreeWallItem(it.id) }
        plan.stairs.forEach { if (take(listOf(it.center))) out += GroupItem.StairItem(it.id) }
        plan.rulers.forEach { if (take(listOf(it.start, it.end))) out += GroupItem.RulerItem(it.id) }
        plan.dimensions.forEach { if (take(listOf(it.a, it.b))) out += GroupItem.DimensionItem(it.id) }
        plan.annotations.forEach { if (take(listOfNotNull(it.at, it.arrowTo))) out += GroupItem.AnnotationItem(it.id) }
        return out
    }

    /** Il rettangolo interseca uno dei lati del poligono (oggetto più grande del rettangolo). */
    private fun rectCrosses(pts: List<Vec2>, minX: Double, minY: Double, maxX: Double, maxY: Double): Boolean {
        val corners = listOf(Vec2(minX, minY), Vec2(maxX, minY), Vec2(maxX, maxY), Vec2(minX, maxY))
        if (pts.size >= 3 && corners.any { Polygon.contains(pts, it) }) return true
        for (i in pts.indices) {
            val p = pts[i]; val q = pts[(i + 1) % pts.size]
            for (k in 0..3) if (segmentsIntersect(p, q, corners[k], corners[(k + 1) % 4])) return true
        }
        return false
    }

    private fun segmentsIntersect(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Boolean {
        fun o(p: Vec2, q: Vec2, r: Vec2) = (q - p).cross(r - p)
        val d1 = o(c, d, a); val d2 = o(c, d, b); val d3 = o(a, b, c); val d4 = o(a, b, d)
        return (d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0)
    }

    /** Punti che definiscono l'ingombro di ogni oggetto selezionato. */
    private fun pointsOf(plan: FloorPlan, item: GroupItem): List<Vec2> = when (item) {
        is GroupItem.RoomItem -> plan.room(item.id)?.points.orEmpty()
        is GroupItem.FurnitureItem -> plan.furniture(item.id)?.let(Furnishings::outline).orEmpty()
        is GroupItem.ColumnItem -> plan.column(item.id)?.let(Structure::outline).orEmpty()
        is GroupItem.BeamItem -> plan.beam(item.id)?.let { listOf(it.start, it.end) }.orEmpty()
        is GroupItem.FreeWallItem -> plan.freeWall(item.id)?.let { listOf(it.start, it.end) }.orEmpty()
        is GroupItem.StairItem -> plan.stair(item.id)?.let { listOf(it.center) }.orEmpty()
        is GroupItem.RulerItem -> plan.ruler(item.id)?.let { listOf(it.start, it.end) }.orEmpty()
        is GroupItem.DimensionItem -> plan.dimension(item.id)?.let { listOf(it.a, it.b, it.lineA, it.lineB) }.orEmpty()
        is GroupItem.AnnotationItem -> plan.annotation(item.id)?.let { listOfNotNull(it.at, it.arrowTo) }.orEmpty()
    }

    fun bounds(plan: FloorPlan, items: Set<GroupItem>): Bounds? {
        val pts = items.flatMap { pointsOf(plan, it) }
        return if (pts.isEmpty()) null else Polygon.bounds(pts)
    }

    fun boundsOf(plan: FloorPlan, item: GroupItem): Bounds? = pointsOf(plan, item).takeIf { it.isNotEmpty() }?.let(Polygon::bounds)

    // ---------------------------------------------------------------------------------------------------
    // Trasformazioni

    /** Trasformazione del piano: punti, verso di rotazione (gradi da sommare), specchiatura (asse x o y). */
    private class Xf(val point: (Vec2) -> Vec2, val rotate: (Double) -> Double, val mirrored: Boolean)

    private fun norm(deg: Double) = ((deg % 360) + 360) % 360

    fun translate(plan: FloorPlan, items: Set<GroupItem>, d: Vec2): FloorPlan =
        apply(plan, items, Xf({ it + d }, { it }, mirrored = false))

    /** Rotazione di 90° in senso orario sullo schermo attorno a `c` (antiorario con `clockwise = false`). */
    fun rotate90(plan: FloorPlan, items: Set<GroupItem>, c: Vec2, clockwise: Boolean = true): FloorPlan =
        apply(
            plan, items,
            Xf(
                { p -> val q = p - c; c + if (clockwise) Vec2(-q.y, q.x) else Vec2(q.y, -q.x) },
                { norm(it + if (clockwise) 90.0 else -90.0) },
                mirrored = false,
            ),
        )

    /** Specchiatura: `horizontal` = destra ↔ sinistra (attorno alla verticale x = c.x), altrimenti su ↔ giù. */
    fun mirror(plan: FloorPlan, items: Set<GroupItem>, c: Vec2, horizontal: Boolean): FloorPlan =
        apply(
            plan, items,
            Xf(
                { p -> if (horizontal) Vec2(2 * c.x - p.x, p.y) else Vec2(p.x, 2 * c.y - p.y) },
                { r -> norm(if (horizontal) -r else 180 - r) },
                mirrored = true,
            ),
        )

    private fun apply(plan: FloorPlan, items: Set<GroupItem>, x: Xf): FloorPlan {
        var p = plan
        for (item in items) p = when (item) {
            is GroupItem.RoomItem -> p.room(item.id)?.let { p.replace(transformRoom(it, x)) } ?: p
            is GroupItem.FurnitureItem -> p.furniture(item.id)?.let { f -> 
                // Specchiato anche il mobile stesso (la penisola del divano angolare passa dall'altra parte).
                p.replace(f.copy(center = x.point(f.center), rotation = x.rotate(f.rotation), mirrored = f.mirrored != x.mirrored))
            } ?: p
            is GroupItem.ColumnItem -> p.column(item.id)?.let { c -> p.replace(c.copy(center = x.point(c.center), rotation = x.rotate(c.rotation))) } ?: p
            is GroupItem.BeamItem -> p.beam(item.id)?.let { b -> p.replace(b.copy(start = x.point(b.start), end = x.point(b.end))) } ?: p
            is GroupItem.FreeWallItem -> p.freeWall(item.id)?.let { w -> p.replace(w.copy(start = x.point(w.start), end = x.point(w.end))) } ?: p
            is GroupItem.StairItem -> p.stair(item.id)?.let { s ->
                p.replace(s.copy(center = x.point(s.center), rotation = x.rotate(s.rotation), turnLeft = if (x.mirrored) !s.turnLeft else s.turnLeft))
            } ?: p
            is GroupItem.RulerItem -> p.ruler(item.id)?.let { r -> p.replace(r.copy(start = x.point(r.start), end = x.point(r.end))) } ?: p
            // Specchiando la quota, la sinistra di a→b diventa la destra: la linea resta dalla stessa parte.
            is GroupItem.DimensionItem -> p.dimension(item.id)?.let { d ->
                p.replace(d.copy(a = x.point(d.a), b = x.point(d.b), offset = if (x.mirrored) -d.offset else d.offset))
            } ?: p
            // I testi si spostano e girano con il gruppo; specchiati restano leggibili (non al contrario).
            is GroupItem.AnnotationItem -> p.annotation(item.id)?.let { t ->
                p.replace(t.copy(at = x.point(t.at), arrowTo = t.arrowTo?.let(x.point), rotation = if (x.mirrored) t.rotation else x.rotate(t.rotation)))
            } ?: p
        }
        return p
    }

    /**
     * Stanza trasformata. Con la specchiatura l'ordine degli angoli si inverte (le stanze restano in senso
     * orario): il muro `j` diventa il muro `n − 2 − j`, percorso al contrario, quindi le posizioni lungo il
     * muro si misurano dall'altra estremità e i cardini delle porte cambiano lato.
     */
    private fun transformRoom(r: Room, x: Xf): Room {
        val pts = r.points.map(x.point)
        if (!x.mirrored) {
            return r.copy(
                points = pts,
                fixtures = r.fixtures.map { f -> if (f.kind.mount == Mount.Ceiling) f.copy(point = x.point(f.point), rotation = x.rotate(f.rotation)) else f },
            )
        }
        val n = pts.size
        fun wall(j: Int) = ((n - 2 - j) % n + n) % n
        fun <T> remap(m: Map<Int, T>) = m.mapKeys { wall(it.key) }
        return r.copy(
            points = pts.reversed(),
            wallHeights = remap(r.wallHeights),
            wallPaints = remap(r.wallPaints),
            wallFinishes = remap(r.wallFinishes),
            wallThicknesses = remap(r.wallThicknesses),
            removedWalls = r.removedWalls.map(::wall).toSet(),
            wallPhases = remap(r.wallPhases),
            wallCuts = r.wallCuts.entries.associate { (k, v) -> wall(k) to v.copy(towardEnd = !v.towardEnd) },
            openings = r.openings.map { o -> o.copy(wallIndex = wall(o.wallIndex), position = r.wallLength(o.wallIndex) - o.position, hingeLeft = !o.hingeLeft) },
            fixtures = r.fixtures.map { f ->
                if (f.kind.mount == Mount.Ceiling) f.copy(point = x.point(f.point), rotation = x.rotate(f.rotation))
                else f.copy(wallIndex = wall(f.wallIndex), position = r.wallLength(f.wallIndex) - f.position)
            },
        )
    }

    // ---------------------------------------------------------------------------------------------------
    // Copie

    /**
     * Copie degli oggetti spostate di `offset`, `count` volte (serie: 1ª copia a offset, 2ª a 2×offset…).
     * Nuovi id per tutto, anche per porte, finestre e impianti. Restituisce la pianta e i nuovi oggetti.
     * Gli oggetti si leggono da `plan` e le copie finiscono in `into` (incolla: anche su un altro piano).
     */
    fun duplicate(plan: FloorPlan, items: Set<GroupItem>, offset: Vec2, count: Int = 1, into: FloorPlan = plan): Pair<FloorPlan, Set<GroupItem>> {
        var p = into
        val created = mutableSetOf<GroupItem>()
        for (k in 1..count) {
            val d = offset * k.toDouble()
            for (item in items) when (item) {
                is GroupItem.RoomItem -> plan.room(item.id)?.let { r ->
                    var openingId = p.nextOpeningId
                    var fixtureId = p.nextFixtureId
                    val copy = r.copy(
                        id = p.nextId,
                        name = RoomFactory.autoName(p, r.type),
                        points = r.points.map { it + d },
                        openings = r.openings.map { it.copy(id = openingId++) },
                        fixtures = r.fixtures.map { f -> f.copy(id = fixtureId++, point = if (f.kind.mount == Mount.Ceiling) f.point + d else f.point) },
                    )
                    p = p.copy(rooms = p.rooms + copy); created += GroupItem.RoomItem(copy.id)
                }
                is GroupItem.FurnitureItem -> plan.furniture(item.id)?.let { f ->
                    val c = f.copy(id = p.nextFurnitureId, center = f.center + d); p = p.copy(furniture = p.furniture + c); created += GroupItem.FurnitureItem(c.id)
                }
                is GroupItem.ColumnItem -> plan.column(item.id)?.let { c0 ->
                    val c = c0.copy(id = p.nextColumnId, center = c0.center + d); p = p.copy(columns = p.columns + c); created += GroupItem.ColumnItem(c.id)
                }
                is GroupItem.BeamItem -> plan.beam(item.id)?.let { b ->
                    val c = b.copy(id = p.nextBeamId, start = b.start + d, end = b.end + d); p = p.copy(beams = p.beams + c); created += GroupItem.BeamItem(c.id)
                }
                is GroupItem.FreeWallItem -> plan.freeWall(item.id)?.let { w ->
                    val c = w.copy(id = p.nextFreeWallId, start = w.start + d, end = w.end + d); p = p.copy(freeWalls = p.freeWalls + c); created += GroupItem.FreeWallItem(c.id)
                }
                is GroupItem.StairItem -> plan.stair(item.id)?.let { s ->
                    val c = s.copy(id = p.nextStairId, center = s.center + d); p = p.copy(stairs = p.stairs + c); created += GroupItem.StairItem(c.id)
                }
                is GroupItem.RulerItem -> plan.ruler(item.id)?.let { r ->
                    val c = r.copy(id = p.nextRulerId, start = r.start + d, end = r.end + d); p = p.copy(rulers = p.rulers + c); created += GroupItem.RulerItem(c.id)
                }
                is GroupItem.DimensionItem -> plan.dimension(item.id)?.let { q ->
                    val c = q.copy(id = p.nextDimensionId, a = q.a + d, b = q.b + d); p = p.copy(dimensions = p.dimensions + c); created += GroupItem.DimensionItem(c.id)
                }
                is GroupItem.AnnotationItem -> plan.annotation(item.id)?.let { t ->
                    val c = t.copy(id = p.nextAnnotationId, at = t.at + d, arrowTo = t.arrowTo?.let { it + d }); p = p.copy(annotations = p.annotations + c); created += GroupItem.AnnotationItem(c.id)
                }
            }
        }
        return p to created
    }

    fun delete(plan: FloorPlan, items: Set<GroupItem>): FloorPlan {
        val ids = items.groupBy({ it::class }, { it.id })
        fun of(k: kotlin.reflect.KClass<out GroupItem>) = ids[k].orEmpty().toSet()
        return plan.copy(
            rooms = plan.rooms.filter { it.id !in of(GroupItem.RoomItem::class) },
            furniture = plan.furniture.filter { it.id !in of(GroupItem.FurnitureItem::class) },
            columns = plan.columns.filter { it.id !in of(GroupItem.ColumnItem::class) },
            beams = plan.beams.filter { it.id !in of(GroupItem.BeamItem::class) },
            freeWalls = plan.freeWalls.filter { it.id !in of(GroupItem.FreeWallItem::class) },
            stairs = plan.stairs.filter { it.id !in of(GroupItem.StairItem::class) },
            rulers = plan.rulers.filter { it.id !in of(GroupItem.RulerItem::class) },
            dimensions = plan.dimensions.filter { it.id !in of(GroupItem.DimensionItem::class) },
            annotations = plan.annotations.filter { it.id !in of(GroupItem.AnnotationItem::class) },
        )
    }

    // ---------------------------------------------------------------------------------------------------
    // Allinea e distribuisci

    /** Allinea gli oggetti al bordo (o al centro) dell'ingombro di tutta la selezione. */
    fun align(plan: FloorPlan, items: Set<GroupItem>, a: Alignment): FloorPlan {
        val all = bounds(plan, items) ?: return plan
        var p = plan
        for (item in items) {
            val b = boundsOf(plan, item) ?: continue
            val d = when (a) {
                Alignment.Left -> Vec2(all.minX - b.minX, 0.0)
                Alignment.Right -> Vec2(all.maxX - b.maxX, 0.0)
                Alignment.CenterX -> Vec2((all.minX + all.maxX) / 2 - (b.minX + b.maxX) / 2, 0.0)
                Alignment.Top -> Vec2(0.0, all.minY - b.minY)
                Alignment.Bottom -> Vec2(0.0, all.maxY - b.maxY)
                Alignment.CenterY -> Vec2(0.0, (all.minY + all.maxY) / 2 - (b.minY + b.maxY) / 2)
            }
            if (abs(d.x) + abs(d.y) > 1e-9) p = translate(p, setOf(item), d)
        }
        return p
    }

    /** Spazi uguali tra gli oggetti (dal primo all'ultimo restano dove sono), in orizzontale o in verticale. */
    fun distribute(plan: FloorPlan, items: Set<GroupItem>, horizontal: Boolean): FloorPlan {
        val boxes = items.mapNotNull { i -> boundsOf(plan, i)?.let { i to it } }
            .sortedBy { (_, b) -> if (horizontal) b.minX else b.minY }
        if (boxes.size < 3) return plan
        fun size(b: Bounds) = if (horizontal) b.width else b.height
        fun start(b: Bounds) = if (horizontal) b.minX else b.minY
        val first = boxes.first().second
        val last = boxes.last().second
        val span = start(last) + size(last) - start(first)
        val gap = (span - boxes.sumOf { size(it.second) }) / (boxes.size - 1)
        var p = plan
        var cursor = start(first)
        for ((item, b) in boxes) {
            val shift = cursor - start(b)
            if (abs(shift) > 1e-9) p = translate(p, setOf(item), if (horizontal) Vec2(shift, 0.0) else Vec2(0.0, shift))
            cursor += size(b) + gap
        }
        return p
    }
}
