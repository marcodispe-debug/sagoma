package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Roof
import com.sagoma.planimetria.model.RoofKind
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Forma del tetto inclinato ([Roof]) sopra l'ultimo piano: falde, timpani e linee da tratteggiare in pianta.
 * Le altezze sono dal piano d'appoggio (il solaio sopra l'ultimo piano, cioè il suo interpiano).
 *
 * Ogni falda sale con la stessa pendenza dal filo esterno di un muro: l'altezza di un punto è la pendenza per la
 * distanza dal muro più vicino tra quelli che portano una falda (due per le due falde, quattro per il padiglione,
 * uno per la falda unica). Fuori dai muri (la gronda) la distanza è negativa e il tetto scende sotto l'appoggio.
 */
object Roofs {
    /** Falda: poligono convesso in pianta, con l'altezza (sopra l'appoggio) della faccia di sopra in ogni angolo. */
    data class Face(val points: List<Vec2>, val heights: List<Double>)

    /** Muro a triangolo o a trapezio sotto il tetto (timpano), in verticale sopra il filo esterno dei muri. */
    data class Gable(val points: List<Vec2>, val heights: List<Double>)

    data class Shape(
        /** Rettangolo dei muri esterni dell'ultimo piano. */
        val walls: Bounds,
        /** Rettangolo della gronda: i muri più lo sporto. */
        val eaves: Bounds,
        val faces: List<Face>,
        val gables: List<Gable>,
        /** Colmo e displuvi (gli spigoli tra due falde), da tratteggiare in pianta. */
        val ridges: List<Pair<Vec2, Vec2>>,
        /** Altezza del colmo sopra l'appoggio (cm). */
        val ridgeHeight: Double,
        /** Spessore del tetto misurato in verticale: la faccia di sotto sta così più in basso. */
        val thickness: Double,
    )

    /** Rettangolo dei muri esterni delle stanze al chiuso del piano, o null se non ce ne sono. */
    fun footprint(plan: FloorPlan): Bounds? {
        val pts = plan.rooms.filter { !it.outdoor }.flatMap { it.exteriorOutline() }
        return if (pts.isEmpty()) null else Polygon.bounds(pts)
    }

    /** Tetto della casa sopra il suo ultimo piano, se c'è. */
    fun of(building: Building): Shape? {
        val roof = building.roof ?: return null
        return shape(roof, footprint(building.floors.last().plan) ?: return null)
    }

    /**
     * Forma del tetto sopra il rettangolo `walls`. Si calcola in coordinate "locali" (u lungo il colmo, v di traverso)
     * e si riporta in pianta scambiando x e y quando il colmo corre lungo y.
     */
    fun shape(roof: Roof, walls: Bounds): Shape? {
        if (walls.width < 1.0 || walls.height < 1.0) return null
        val slope = tan(roof.pitchDeg.coerceIn(Roof.MIN_PITCH, Roof.MAX_PITCH) * PI / 180)
        val o = roof.overhang.coerceIn(0.0, Roof.MAX_OVERHANG)
        val longX = walls.width >= walls.height
        val turn = ((roof.turn % 4) + 4) % 4
        // Il padiglione ha il colmo sempre sul lato lungo: con il colmo di traverso le falde non si chiuderebbero.
        val alongX = if (roof.kind == RoofKind.Hip) longX else longX xor (turn % 2 == 1)
        fun world(u: Double, v: Double) = if (alongX) Vec2(u, v) else Vec2(v, u)
        val u0 = if (alongX) walls.minX else walls.minY
        val u1 = if (alongX) walls.maxX else walls.maxY
        val v0 = if (alongX) walls.minY else walls.minX
        val v1 = if (alongX) walls.maxY else walls.maxX
        val uu0 = u0 - o
        val uu1 = u1 + o
        val vv0 = v0 - o
        val vv1 = v1 + o
        val vc = (v0 + v1) / 2
        val hw = (v1 - v0) / 2
        // Falda unica: il lato basso è v0, o v1 dopo mezzo giro.
        val lowAtV1 = turn >= 2

        /** Distanza dal muro più vicino che porta una falda (negativa sulla gronda). */
        fun reach(u: Double, v: Double): Double = when (roof.kind) {
            RoofKind.Shed -> if (lowAtV1) v1 - v else v - v0
            RoofKind.Gable -> min(v - v0, v1 - v)
            RoofKind.Hip -> min(min(v - v0, v1 - v), min(u - u0, u1 - u))
        }
        fun face(vararg p: Pair<Double, Double>): Face? {
            val pts = p.map { world(it.first, it.second) }
            val distinct = pts.distinctBy { (it.x * 1000).toLong() to (it.y * 1000).toLong() }
            if (distinct.size < 3) return null
            return Face(pts, p.map { slope * reach(it.first, it.second) })
        }
        val thickness = Roof.THICKNESS
        val faces = mutableListOf<Face>()
        val gables = mutableListOf<Gable>()
        val ridges = mutableListOf<Pair<Vec2, Vec2>>()
        val ridgeHeight: Double
        when (roof.kind) {
            RoofKind.Shed -> {
                faces += face(uu0 to vv0, uu1 to vv0, uu1 to vv1, uu0 to vv1)!!
                val rise = slope * (v1 - v0)
                ridgeHeight = rise
                val (low, high) = if (lowAtV1) v1 to v0 else v0 to v1
                // Il muro alto (rettangolo) e i due di fianco (triangoli), fino sotto la falda.
                gables += Gable(listOf(world(u0, high), world(u1, high), world(u1, high), world(u0, high)), listOf(0.0, 0.0, rise, rise))
                gables += Gable(listOf(world(u0, low), world(u0, high), world(u0, high)), listOf(0.0, 0.0, rise))
                gables += Gable(listOf(world(u1, low), world(u1, high), world(u1, high)), listOf(0.0, 0.0, rise))
            }
            RoofKind.Gable -> {
                faces += face(uu0 to vv0, uu1 to vv0, uu1 to vc, uu0 to vc)!!
                faces += face(uu0 to vc, uu1 to vc, uu1 to vv1, uu0 to vv1)!!
                ridgeHeight = slope * hw
                for (u in listOf(u0, u1)) gables += Gable(listOf(world(u, v0), world(u, v1), world(u, vc)), listOf(0.0, 0.0, ridgeHeight))
                ridges += world(uu0, vc) to world(uu1, vc)
            }
            RoofKind.Hip -> {
                val r0 = u0 + hw
                val r1 = max(r0, u1 - hw)
                faces += listOfNotNull(
                    face(uu0 to vv0, uu1 to vv0, r1 to vc, r0 to vc),
                    face(uu1 to vv1, uu0 to vv1, r0 to vc, r1 to vc),
                    face(uu0 to vv1, uu0 to vv0, r0 to vc),
                    face(uu1 to vv0, uu1 to vv1, r1 to vc),
                )
                ridgeHeight = slope * hw
                if (r1 - r0 > 0.5) ridges += world(r0, vc) to world(r1, vc)
                for ((cu, cv, ru) in listOf(Triple(uu0, vv0, r0), Triple(uu0, vv1, r0), Triple(uu1, vv0, r1), Triple(uu1, vv1, r1))) {
                    ridges += world(cu, cv) to world(ru, vc)
                }
            }
        }
        // La faccia di sotto della falda passa sull'appoggio al filo del muro: quella di sopra è più alta dello spessore.
        val lifted = faces.map { f -> f.copy(heights = f.heights.map { it + thickness }) }
        val eaves = if (alongX) Bounds(uu0, vv0, uu1, vv1) else Bounds(vv0, uu0, vv1, uu1)
        return Shape(walls, eaves, lifted, gables, ridges, ridgeHeight + thickness, thickness)
    }
}
