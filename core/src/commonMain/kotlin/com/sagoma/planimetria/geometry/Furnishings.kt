package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Arredi (versione pro): ingombro, aggancio a muri e altri mobili, rotazione. */
object Furnishings {
    /** Distanza entro cui un mobile si accosta a un muro o a un altro mobile. */
    const val SNAP = 15.0
    /** Distanza della maniglia di rotazione dal davanti del mobile (cm). */
    const val HANDLE = 30.0

    /** Asse della larghezza (u) e della profondità verso il davanti (v). */
    fun axes(f: Furniture): Pair<Vec2, Vec2> {
        val rad = toRadians(f.rotation)
        val u = Vec2(cos(rad), sin(rad))
        return u to u.perp()
    }

    /** Rettangolo in pianta: dietro-sinistra, dietro-destra, davanti-destra, davanti-sinistra. */
    fun outline(f: Furniture): List<Vec2> {
        val (u, v) = axes(f)
        val hw = f.width / 2
        val hd = f.depth / 2
        return listOf(f.center - u * hw - v * hd, f.center + u * hw - v * hd, f.center + u * hw + v * hd, f.center - u * hw + v * hd)
    }

    fun contains(f: Furniture, p: Vec2): Boolean = Polygon.contains(outline(f), p)

    /** Maniglia di rotazione, davanti al mobile. */
    fun handle(f: Furniture): Vec2 = f.center + axes(f).second * (f.depth / 2 + HANDLE)

    /** Nuovo mobile del catalogo al centro `center`; quelli appesi stanno subito sotto il soffitto. */
    fun create(plan: FloorPlan, item: FurnitureCatalog.Item, center: Vec2, levelHeight: Double): Furniture {
        val elevation = if (item.ceiling) Structure.ceilingAt(plan, center, levelHeight) - item.height else item.elevation
        return Furniture(plan.nextFurnitureId, item.model, center, 0.0, item.width, item.depth, item.height, elevation.coerceAtLeast(0.0))
    }

    /** Rotazione trascinando la maniglia verso `p`: si raddrizza sui multipli di 90° entro 6°. */
    fun rotationToward(f: Furniture, p: Vec2): Double {
        val d = p - f.center
        if (d.length < 1e-6) return f.rotation
        // Il davanti (v) punta verso il dito: v = perp(u), quindi u è ruotato di −90°.
        var deg = toDegrees(kotlin.math.atan2(d.y, d.x)) - 90.0
        val snapped = roundHalfUp(deg / 90.0) * 90.0
        if (abs(deg - snapped) <= Structure.ANGLE_SNAP) deg = snapped.toDouble()
        return ((deg % 360) + 360) % 360
    }

    /**
     * Mobile spostato: si accosta alla faccia di un muro vicino e ai fianchi degli altri mobili (per
     * mettere in fila le basi della cucina), sui due assi.
     */
    fun snapped(plan: FloorPlan, f: Furniture): Furniture {
        val b = Polygon.bounds(outline(f))
        val wall = Structure.wallSnap(plan, b, SNAP)
        var dx = wall.x.takeIf { it != 0.0 }
        var dy = wall.y.takeIf { it != 0.0 }
        fun better(cur: Double?, v: Double) = if (abs(v) < SNAP && (cur == null || abs(v) < abs(cur))) v else cur
        for (o in plan.furniture) {
            if (o.id == f.id) continue
            val ob = Polygon.bounds(outline(o))
            // Solo mobili vicini sull'altro asse: fianco a fianco o allineati.
            if (ob.maxY > b.minY - SNAP && ob.minY < b.maxY + SNAP) {
                for (v in listOf(ob.maxX - b.minX, ob.minX - b.maxX, ob.minX - b.minX, ob.maxX - b.maxX)) dx = better(dx, v)
            }
            if (ob.maxX > b.minX - SNAP && ob.minX < b.maxX + SNAP) {
                for (v in listOf(ob.maxY - b.minY, ob.minY - b.maxY, ob.minY - b.minY, ob.maxY - b.maxY)) dy = better(dy, v)
            }
        }
        val moved = f.copy(center = f.center + Vec2(dx ?: 0.0, dy ?: 0.0))
        return if (wall != Vec2.Zero) backToWall(plan, moved) else moved
    }

    /**
     * Mobile accostato a un muro con il davanti: si gira di 180° così che sia lo schienale (il retro) a
     * toccare il muro, come si mette un divano o una base della cucina.
     */
    private fun backToWall(plan: FloorPlan, f: Furniture): Furniture {
        val v = axes(f).second
        fun inWall(p: Vec2) = Structure.wallLines(plan).any { Polygon.distanceToSegment(p, it.a, it.b) <= it.half + 0.5 }
        val front = f.center + v * (f.depth / 2 + 1.0)
        val back = f.center - v * (f.depth / 2 + 1.0)
        return if (inWall(front) && !inWall(back)) f.copy(rotation = (f.rotation + 180.0) % 360) else f
    }
}
