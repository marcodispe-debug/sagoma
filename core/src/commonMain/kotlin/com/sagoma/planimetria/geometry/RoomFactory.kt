package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2

/** Misure inserite nella finestra di creazione (§1). Tutte in cm. */
data class ShapeDimensions(
    val width: Double,
    val depth: Double,
    val protrusionWidth: Double = 0.0,
    val protrusionDepth: Double = 0.0,
)

object RoomFactory {

    /** Distanza tra una nuova stanza e quelle esistenti. */
    const val PLACEMENT_GAP = 60.0

    fun rectangle(width: Double, depth: Double): List<Vec2> = listOf(
        Vec2(0.0, 0.0), Vec2(width, 0.0), Vec2(width, depth), Vec2(0.0, depth),
    )

    /**
     * Stanza a L: rettangolo principale `width × depth` con una protuberanza
     * `protrusionWidth × protrusionDepth` che sporge dal lato indicato dall'orientamento.
     */
    fun lShape(d: ShapeDimensions, orientation: LOrientation): List<Vec2> {
        require(d.protrusionWidth > 0 && d.protrusionWidth < d.width) { "La protuberanza deve essere più stretta della stanza" }
        require(d.protrusionDepth > 0)
        val w = d.width
        val h = d.depth
        val pw = d.protrusionWidth
        val pd = d.protrusionDepth
        // Forma di base: protuberanza in alto a destra.
        val base = listOf(
            Vec2(0.0, pd), Vec2(w - pw, pd), Vec2(w - pw, 0.0), Vec2(w, 0.0), Vec2(w, pd + h), Vec2(0.0, pd + h),
        )
        val totalH = pd + h
        val mirrored = when (orientation) {
            LOrientation.TopRight -> base
            LOrientation.TopLeft -> base.map { Vec2(w - it.x, it.y) }
            LOrientation.BottomRight -> base.map { Vec2(it.x, totalH - it.y) }
            LOrientation.BottomLeft -> base.map { Vec2(w - it.x, totalH - it.y) }
        }
        return normalizeWinding(mirrored)
    }

    /** Porta sempre i poligoni in ordine orario su schermo, così "interno/esterno" è coerente. */
    fun normalizeWinding(pts: List<Vec2>): List<Vec2> =
        if (Polygon.signedArea(pts) < 0) pts.reversed() else pts

    /** Trasla la forma accanto alle stanze esistenti, senza mai sovrapporsi (§1). */
    fun placeBeside(plan: FloorPlan, shape: List<Vec2>, gap: Double = PLACEMENT_GAP): List<Vec2> {
        val sb = Polygon.bounds(shape)
        if (plan.rooms.isEmpty()) return shape.map { it - Vec2(sb.minX, sb.minY) }
        val all = plan.rooms.map { Polygon.bounds(it.points) }.reduce { a, b -> a.union(b) }
        val origin = Vec2(all.maxX + gap, all.minY)
        return shape.map { it - Vec2(sb.minX, sb.minY) + origin }
    }

    /**
     * Balcone o terrazza: appoggiato al muro verticale più lungo sul lato destro della casa, con il lato lungo
     * lungo il muro (così nasce già con il muro in comune). Se non c'è un muro adatto, accanto alla casa.
     */
    fun placeAgainstHouse(plan: FloorPlan, shape: List<Vec2>): List<Vec2> {
        if (plan.rooms.isEmpty()) return placeBeside(plan, shape, gap = 0.0)
        val maxX = plan.rooms.maxOf { r -> r.points.maxOf { it.x } }
        val wall = plan.rooms.flatMap { r -> (0 until r.wallCount).map { r.wallStart(it) to r.wallEnd(it) } }
            .filter { (a, b) -> kotlin.math.abs(a.x - b.x) < 0.5 && kotlin.math.abs(a.x - maxX) < 1.0 }
            .maxByOrNull { (a, b) -> kotlin.math.abs(a.y - b.y) }
            ?: return placeBeside(plan, shape, gap = 0.0)
        // Lato lungo in verticale, lungo il muro.
        val sb0 = Polygon.bounds(shape)
        val turned = if (sb0.width > sb0.height) normalizeWinding(shape.map { Vec2(it.y, it.x) }) else shape
        val sb = Polygon.bounds(turned)
        val top = minOf(wall.first.y, wall.second.y)
        return turned.map { it - Vec2(sb.minX, sb.minY) + Vec2(maxX, top) }
    }

    /** Nome automatico per tipo: "Cucina", poi "Cucina 2", "Cucina 3"... (§3). */
    fun autoName(plan: FloorPlan, type: RoomType, excludingRoomId: Long? = null): String {
        val taken = plan.rooms.filter { it.id != excludingRoomId }.map { it.name }.toSet()
        val base = type.defaultName
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }
}
