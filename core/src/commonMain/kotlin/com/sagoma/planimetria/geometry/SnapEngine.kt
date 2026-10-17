package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs

/** Tipo di aggancio, con il nome mostrato accanto alla guida. */
enum class SnapKind(val label: String) {
    Endpoint("Estremità"),
    Midpoint("Punto medio"),
    Center("Centro"),
    AlignVertical("Allineato"),
    AlignHorizontal("Allineato"),
    RightAngle("Angolo retto"),
    /** Direzione bloccata rispetto al punto precedente (disegno dei muri). */
    Horizontal("Orizzontale"),
    Vertical("Verticale"),
    Diagonal("45°"),
    /** Chiusura sul primo punto (disegno dei muri). */
    Close("Chiudi"),
}

/**
 * Guida di un aggancio da disegnare durante il trascinamento: il punto agganciato e, per gli allineamenti,
 * il punto di riferimento da cui parte la linea tratteggiata.
 */
data class SnapGuide(val kind: SnapKind, val point: Vec2, val from: Vec2? = null)

/** Punto dopo l'aggancio e le guide che lo spiegano. */
data class SnapResult(val point: Vec2, val guides: List<SnapGuide>) {
    val snapped: Boolean get() = guides.isNotEmpty()
}

/**
 * Aggancio unico dei punti trascinati, come nei programmi CAD: estremità (angoli, estremi di muri e travi),
 * punti medi dei muri, centri (colonne), allineamenti orizzontali e verticali con gli altri punti del piano.
 * La tolleranza è in cm (il chiamante la ricava dai pixel dello schermo, così vale a ogni zoom).
 */
object SnapEngine {

    /** Punti notevoli del piano: estremità, punti medi e centri; `exclude` toglie i punti di chi si sta spostando. */
    class Targets(val endpoints: List<Vec2>, val midpoints: List<Vec2>, val centers: List<Vec2>) {
        /** Tutti i punti da cui partono gli allineamenti. */
        val alignable: List<Vec2> get() = endpoints + centers
    }

    /**
     * `faces`: anche gli spigoli delle facce interne ed esterne dei muri (per misurare con il metro: le misure
     * vere sono tra le facce, non tra le mezzerie).
     */
    fun targets(plan: FloorPlan, excludeRoom: Long? = null, excludeCornerOf: Pair<Long, Int>? = null, faces: Boolean = false): Targets {
        val ends = mutableListOf<Vec2>()
        val mids = mutableListOf<Vec2>()
        for (r in plan.rooms) {
            if (r.id == excludeRoom) continue
            if (faces) {
                ends += r.interior()
                if (!r.outdoor) ends += r.exterior()
            }
            r.points.forEachIndexed { i, p -> if (excludeCornerOf == null || excludeCornerOf.first != r.id || excludeCornerOf.second != i) ends += p }
            for (i in 0 until r.wallCount) {
                // Il punto medio dei muri che toccano l'angolo trascinato si sposta con lui: non vale.
                if (excludeCornerOf != null && excludeCornerOf.first == r.id &&
                    (i == excludeCornerOf.second || (i + 1) % r.wallCount == excludeCornerOf.second)
                ) continue
                mids += (r.wallStart(i) + r.wallEnd(i)) / 2.0
            }
        }
        for (w in plan.freeWalls) { ends += w.start; ends += w.end; mids += w.mid }
        for (b in plan.beams) { ends += b.start; ends += b.end }
        val centers = plan.columns.map { it.center }
        return Targets(ends, mids, centers)
    }

    /**
     * Come [targets], più i punti notevoli del piano di sotto (angoli, estremi e punti medi dei muri, colonne), se c'è: sono
     * riferimenti per posare le stanze di sopra proprio sopra quelle di sotto. Il piano di sotto non si modifica mai.
     */
    fun targetsWithBelow(
        plan: FloorPlan, below: FloorPlan?, excludeRoom: Long? = null, excludeCornerOf: Pair<Long, Int>? = null, faces: Boolean = false,
    ): Targets {
        val own = targets(plan, excludeRoom, excludeCornerOf, faces)
        if (below == null) return own
        val lower = targets(below, faces = faces)
        return Targets(own.endpoints + lower.endpoints, own.midpoints + lower.midpoints, own.centers + lower.centers)
    }

    /**
     * Aggancia `p`: prima a un punto (estremità, centro, punto medio) entro `tol`; altrimenti allinea x e/o y
     * con il punto più vicino sullo stesso asse. `rightAngleRefs`: i due vicini dell'angolo trascinato (se
     * allineato con entrambi è un angolo retto).
     */
    fun snap(p: Vec2, targets: Targets, tol: Double, rightAngleRefs: List<Vec2> = emptyList(), alignOnly: List<Vec2> = emptyList()): SnapResult {
        fun nearest(list: List<Vec2>) = list.map { it to it.distanceTo(p) }.filter { it.second <= tol }.minByOrNull { it.second }?.first
        nearest(targets.endpoints)?.let { return SnapResult(it, listOf(SnapGuide(SnapKind.Endpoint, it))) }
        nearest(targets.centers)?.let { return SnapResult(it, listOf(SnapGuide(SnapKind.Center, it))) }
        nearest(targets.midpoints)?.let { return SnapResult(it, listOf(SnapGuide(SnapKind.Midpoint, it))) }

        // Allineamenti: il riferimento più vicino (in distanza lungo l'asse libero) tra quelli entro tolleranza.
        val refs = (targets.alignable + rightAngleRefs + alignOnly).distinct()
        val vx = refs.filter { abs(it.x - p.x) <= tol }.minByOrNull { abs(it.x - p.x) * 1000 + abs(it.y - p.y) }
        val hy = refs.filter { abs(it.y - p.y) <= tol }.minByOrNull { abs(it.y - p.y) * 1000 + abs(it.x - p.x) }
        if (vx == null && hy == null) return SnapResult(p, emptyList())
        val q = Vec2(vx?.x ?: p.x, hy?.y ?: p.y)
        val guides = mutableListOf<SnapGuide>()
        val square = rightAngleRefs.size == 2 && vx != null && hy != null && vx in rightAngleRefs && hy in rightAngleRefs && vx != hy
        vx?.let { guides += SnapGuide(if (square) SnapKind.RightAngle else SnapKind.AlignVertical, q, it) }
        hy?.let { guides += SnapGuide(if (square) SnapKind.RightAngle else SnapKind.AlignHorizontal, q, it) }
        return SnapResult(q, guides)
    }

    /** Entro questi gradi da 0°, 45°, 90°… la direzione del nuovo muro si blocca. */
    const val ANGLE_LOCK_DEG = 5.0

    /**
     * Punto del muro che si sta disegnando da `last`: prima gli agganci a punti (e la chiusura sul primo punto
     * `first`); altrimenti la direzione si blocca su orizzontale, verticale o 45° e, lungo quella direzione,
     * la lunghezza si allinea agli altri punti del piano.
     */
    fun snapDrawing(p: Vec2, last: Vec2?, first: Vec2?, targets: Targets, drawn: List<Vec2>, tol: Double): SnapResult {
        if (first != null && drawn.size >= 3 && p.distanceTo(first) <= tol) return SnapResult(first, listOf(SnapGuide(SnapKind.Close, first)))
        fun nearest(list: List<Vec2>) = list.map { it to it.distanceTo(p) }.filter { it.second <= tol }.minByOrNull { it.second }?.first
        nearest(targets.endpoints)?.let { return SnapResult(it, listOf(SnapGuide(SnapKind.Endpoint, it))) }
        nearest(targets.centers)?.let { return SnapResult(it, listOf(SnapGuide(SnapKind.Center, it))) }
        nearest(targets.midpoints)?.let { return SnapResult(it, listOf(SnapGuide(SnapKind.Midpoint, it))) }
        if (last == null) return snap(p, targets, tol, alignOnly = drawn)
        val d = p - last
        val len = d.length
        if (len < 1e-6) return SnapResult(p, emptyList())
        val deg = toDegrees(kotlin.math.atan2(d.y, d.x))
        val locked = roundHalfUp(deg / 45.0) * 45.0
        if (abs(deg - locked) > ANGLE_LOCK_DEG) return snap(p, targets, tol, alignOnly = drawn)
        val rad = toRadians(locked)
        val dir = Vec2(kotlin.math.cos(rad), kotlin.math.sin(rad)).let { Vec2(roundTiny(it.x), roundTiny(it.y)) }
        var q = last + dir * len
        val kind = when {
            abs(dir.y) < 1e-9 -> SnapKind.Horizontal
            abs(dir.x) < 1e-9 -> SnapKind.Vertical
            else -> SnapKind.Diagonal
        }
        val guides = mutableListOf(SnapGuide(kind, q, last))
        // Lungo un muro orizzontale (o verticale) la fine si allinea alla x (o y) di un altro punto.
        val refs = (targets.alignable + drawn).distinct()
        if (kind == SnapKind.Horizontal) refs.filter { abs(it.x - q.x) <= tol }.minByOrNull { abs(it.x - q.x) }?.let {
            q = Vec2(it.x, q.y); guides[0] = guides[0].copy(point = q); guides += SnapGuide(SnapKind.AlignVertical, q, it)
        }
        if (kind == SnapKind.Vertical) refs.filter { abs(it.y - q.y) <= tol }.minByOrNull { abs(it.y - q.y) }?.let {
            q = Vec2(q.x, it.y); guides[0] = guides[0].copy(point = q); guides += SnapGuide(SnapKind.AlignHorizontal, q, it)
        }
        return SnapResult(q, guides)
    }

    private fun roundTiny(v: Double) = if (abs(v) < 1e-9) 0.0 else v
}
