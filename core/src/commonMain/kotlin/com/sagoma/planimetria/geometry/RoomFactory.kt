package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T

/** Misure inserite nella finestra di creazione (§1). Tutte in cm. */
data class ShapeDimensions(
    val width: Double,
    val depth: Double,
    val protrusionWidth: Double = 0.0,
    val protrusionDepth: Double = 0.0,
)

object RoomFactory {

    fun rectangle(width: Double, depth: Double): List<Vec2> = listOf(
        Vec2(0.0, 0.0), Vec2(width, 0.0), Vec2(width, depth), Vec2(0.0, depth),
    )

    /**
     * Stanza a L: rettangolo principale `width × depth` con una protuberanza
     * `protrusionWidth × protrusionDepth` che sporge dal lato indicato dall'orientamento.
     */
    fun lShape(d: ShapeDimensions, orientation: LOrientation): List<Vec2> {
        require(d.protrusionWidth > 0 && d.protrusionWidth < d.width) { T.creazione.protuberanzaDeveEsserePiu2 }
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

    /**
     * Trasla la forma accanto alle stanze esistenti, attaccata (muro in comune) e senza mai sovrapporsi (§1). Prova i lati di
     * `anchorId` (o dell'ultima stanza creata): a destra allineata in alto, sotto allineata a sinistra, a sinistra, sopra; il
     * primo libero vince. Se nessuno è libero, a destra di tutta la casa.
     */
    fun placeBeside(plan: FloorPlan, shape: List<Vec2>, anchorId: Long? = null): List<Vec2> {
        val sb = Polygon.bounds(shape)
        fun at(x: Double, y: Double) = shape.map { it - Vec2(sb.minX, sb.minY) + Vec2(x, y) }
        if (plan.rooms.isEmpty()) return at(0.0, 0.0)
        val existing = plan.rooms.map { Polygon.bounds(it.points) }
        fun free(moved: List<Vec2>): Boolean {
            val mb = Polygon.bounds(moved)
            return existing.none { e -> mb.minX < e.maxX - 1.0 && e.minX < mb.maxX - 1.0 && mb.minY < e.maxY - 1.0 && e.minY < mb.maxY - 1.0 }
        }
        val anchor = (plan.room(anchorId) ?: plan.rooms.maxBy { it.id }).let { Polygon.bounds(it.points) }
        val candidates = listOf(
            at(anchor.maxX, anchor.minY),
            at(anchor.minX, anchor.maxY),
            at(anchor.minX - sb.width, anchor.minY),
            at(anchor.minX, anchor.minY - sb.height),
        )
        candidates.firstOrNull(::free)?.let { return it }
        val all = existing.reduce { a, b -> a.union(b) }
        return at(all.maxX, all.minY)
    }

    /**
     * Piano di sopra: la forma si posa con l'angolo in alto a sinistra su un angolo del piano di sotto, così muri e angoli si
     * sovrappongono e il resto si regola con l'aggancio. Prima sceglie la prima stanza al chiuso di sotto ancora scoperta (nessuna
     * stanza di sopra la copre); se sono tutte coperte (per esempio il piano nato con i muri copiati da quello di sotto) sceglie
     * l'angolo di sotto più vicino a dove andrebbe di solito (accanto alle stanze di sopra). Mai sovrapposta alle stanze di sopra.
     * Le dimensioni della forma non cambiano e il piano di sotto è solo letto. Null se non si può: allora vale [placeBeside].
     */
    fun placeAbove(plan: FloorPlan, below: FloorPlan?, shape: List<Vec2>): List<Vec2>? {
        if (below == null) return null
        val sb = Polygon.bounds(shape)
        val existing = plan.rooms.map { Polygon.bounds(it.points) }
        fun at(corner: Vec2) = shape.map { it - Vec2(sb.minX, sb.minY) + corner }
        fun free(moved: List<Vec2>): Boolean {
            val mb = Polygon.bounds(moved)
            return existing.none { e -> mb.minX < e.maxX - 1.0 && e.minX < mb.maxX - 1.0 && mb.minY < e.maxY - 1.0 && e.minY < mb.maxY - 1.0 }
        }
        val indoor = below.rooms.filter { !it.outdoor }
        for (lower in indoor) {
            if (plan.rooms.any { Polygon.contains(it.points, Polygon.labelPoint(lower.points)) }) continue
            val lb = Polygon.bounds(lower.points)
            at(Vec2(lb.minX, lb.minY)).takeIf(::free)?.let { return it }
        }
        // Tutte coperte: l'angolo di sotto più vicino al posto di sempre.
        val usual = Polygon.bounds(placeBeside(plan, shape)).let { Vec2(it.minX, it.minY) }
        for (corner in indoor.flatMap { it.points }.distinct().sortedBy { it.distanceTo(usual) }) {
            at(corner).takeIf(::free)?.let { return it }
        }
        return null
    }

    /**
     * Balcone o terrazza: appoggiato al muro verticale più lungo sul lato destro della casa, con il lato lungo
     * lungo il muro (così nasce già con il muro in comune). Se non c'è un muro adatto, accanto alla casa.
     */
    fun placeAgainstHouse(plan: FloorPlan, shape: List<Vec2>): List<Vec2> {
        if (plan.rooms.isEmpty()) return placeBeside(plan, shape)
        val maxX = plan.rooms.maxOf { r -> r.points.maxOf { it.x } }
        val wall = plan.rooms.flatMap { r -> (0 until r.wallCount).map { r.wallStart(it) to r.wallEnd(it) } }
            .filter { (a, b) -> kotlin.math.abs(a.x - b.x) < 0.5 && kotlin.math.abs(a.x - maxX) < 1.0 }
            .maxByOrNull { (a, b) -> kotlin.math.abs(a.y - b.y) }
            ?: return placeBeside(plan, shape)
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
