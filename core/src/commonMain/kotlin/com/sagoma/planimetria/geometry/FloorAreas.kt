package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.dxf.PlanDxf
import com.sagoma.planimetria.dxf.WallOutline
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.max
import kotlin.math.min

/**
 * Superfici di un piano: utile (il pavimento delle stanze, come sulla pianta), lorda (pavimento e muri delle stanze al chiuso,
 * ogni muro una volta sola) e commerciale indicativa secondo il DPR 138/1998: muri esterni fino a 50 cm di spessore, senza le
 * zone non calpestabili (le parti basse delle mansarde), interrati a metà, balconi e terrazze al 30% fino a 25 m² e al 10% oltre.
 */
object FloorAreas {
    /** Spessore massimo (cm) dei muri esterni che conta nella superficie commerciale. */
    const val MAX_OUTER_WALL_CM = 50.0

    /** Balconi e terrazze: i primi 25 m² al 30%, il resto al 10%. */
    const val OUTDOOR_FIRST_M2 = 25.0
    const val OUTDOOR_FIRST_SHARE = 0.30
    const val OUTDOOR_REST_SHARE = 0.10

    /** Piani interrati (cantine, taverne): a metà. */
    const val BASEMENT_SHARE = 0.5

    /** Superficie lorda (cm²) delle stanze al chiuso del piano: pavimento e muri, dove due stanze si toccano contati una volta. */
    fun gross(plan: FloorPlan): Double {
        val p = plan.expandCurves().plan
        val indoor = p.rooms.filter { r -> !r.outdoor && r.wallCount >= 3 && r.points.all { it.x.isFinite() && it.y.isFinite() } }
        val pieces = indoor.flatMap { r -> triangles(r.interior()) + PlanDxf.wallPieces(r) + openBands(indoor, r) }
        return WallOutline.unionArea(pieces).coerceAtLeast(0.0)
    }

    private fun triangles(p: List<Vec2>): List<List<Vec2>> =
        if (p.size < 3 || !p.all { it.x.isFinite() && it.y.isFinite() }) emptyList()
        else Triangulation.triangulate(p).map { (i, j, k) -> listOf(p[i], p[j], p[k]) }

    /**
     * Muri tolti verso un'altra stanza che ha tolto il suo (ambiente unico, soggiorno con cucina a vista): lì c'è pavimento, da
     * contare anche se nessuno dei due contorni interni ci arriva.
     */
    private fun openBands(indoor: List<Room>, r: Room): List<List<Vec2>> = r.removedWalls.filter { i ->
        i in 0 until r.wallCount && indoor.any { o ->
            o.id != r.id && o.removedWalls.any { j -> j in 0 until o.wallCount && Dimensions.overlap(r.wallStart(i), r.wallEnd(i), o.wallStart(j), o.wallEnd(j)) >= 1.0 }
        }
    }.mapNotNull { i -> band(r.wallStart(i), r.wallEnd(i), r.thicknessOf(i) / 2) }

    private fun band(a: Vec2, b: Vec2, half: Double): List<Vec2>? {
        val len = a.distanceTo(b)
        if (len < 1e-6 || half <= 0) return null
        val n = (b - a).perp() / len * half
        return listOf(a - n, b - n, b + n, a + n)
    }

    /**
     * Muri esterni più spessi di [MAX_OUTER_WALL_CM] (cm²): la parte in più, lungo tutto il muro, che nella superficie commerciale
     * non conta. Esterno: non in comune con un'altra stanza al chiuso del piano.
     */
    fun extraWall(plan: FloorPlan): Double {
        val p = plan.expandCurves().plan
        val indoor = p.rooms.filter { !it.outdoor && it.wallCount >= 3 }
        var s = 0.0
        for (r in indoor) for (i in 0 until r.wallCount) {
            val t = r.thicknessOf(i)
            if (r.isRemoved(i) || !(t > MAX_OUTER_WALL_CM)) continue
            val a = r.wallStart(i)
            val b = r.wallEnd(i)
            val shared = indoor.any { o -> o.id != r.id && (0 until o.wallCount).any { j -> Dimensions.sharesWall(a, b, o.wallStart(j), o.wallEnd(j)) } }
            if (!shared) s += (t - MAX_OUTER_WALL_CM) * a.distanceTo(b)
        }
        return s
    }

    /** Superficie (cm²) di balconi e terrazze del piano. */
    fun outdoor(plan: FloorPlan): Double = plan.rooms.filter { it.outdoor }.sumOf { it.usableArea() }

    /** Zone non calpestabili (cm²) delle stanze al chiuso del piano. */
    fun excluded(plan: FloorPlan): Double = plan.rooms.filter { !it.outdoor }.sumOf { (it.interiorArea() - it.usableArea()).coerceAtLeast(0.0) }

    /**
     * Superficie commerciale indicativa (m²) dalle superfici di tutta la casa (m²): quella al chiuso sopra terra (già senza muri
     * esterni oltre 50 cm e zone non calpestabili), quella interrata a metà, balconi e terrazze al 30% fino a 25 m² e al 10% oltre.
     */
    fun commercial(indoor: Double, basement: Double, outdoor: Double): Double =
        indoor + basement * BASEMENT_SHARE + min(outdoor, OUTDOOR_FIRST_M2) * OUTDOOR_FIRST_SHARE + max(0.0, outdoor - OUTDOOR_FIRST_M2) * OUTDOOR_REST_SHARE
}
