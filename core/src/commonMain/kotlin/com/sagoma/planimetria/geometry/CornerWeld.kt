package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Vec2

/**
 * Angoli di stanze diverse quasi sovrapposti (entro [TOLERANCE] cm: un angolo trascinato senza aggancio) saldati nello
 * stesso punto, solo per il 3D: così i muri esterni delle due stanze si raccordano invece di fare uno scalino. La pianta
 * salvata non cambia.
 */
internal object CornerWeld {
    const val TOLERANCE = 2.5

    private class V(val room: Int, val k: Int, val p: Vec2)

    fun weld(plan: FloorPlan): FloorPlan {
        val vs = plan.rooms.flatMapIndexed { r, room -> room.points.mapIndexed { k, p -> V(r, k, p) } }
        val parent = IntArray(vs.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        var near = false
        for (a in vs.indices) for (b in a + 1 until vs.size) {
            if (vs[a].room == vs[b].room) continue
            val d = vs[a].p.distanceTo(vs[b].p)
            if (d > TOLERANCE) continue
            parent[find(a)] = find(b)
            if (d > 1e-6) near = true
        }
        if (!near) return plan
        val moved = plan.rooms.map { it.points.toMutableList() }
        var changed = false
        for (group in vs.indices.groupBy(::find).values) {
            if (group.size < 2) continue
            val members = group.map { vs[it] }
            // Due angoli della stessa stanza nel gruppo (pezzi di curva, catene di angoli vicini): non si tocca niente.
            if (members.map { it.room }.toSet().size < members.size) continue
            // Il punto dove stanno già più angoli; a parità, quello della stanza con l'id più basso.
            val target = members.maxWith(
                compareBy<V> { v -> members.count { it.p.distanceTo(v.p) < 0.01 } }.thenByDescending { plan.rooms[it.room].id },
            ).p
            if (members.any { it.p.distanceTo(target) > TOLERANCE }) continue
            for (v in members) if (v.p != target) { moved[v.room][v.k] = target; changed = true }
        }
        if (!changed) return plan
        return plan.copy(rooms = plan.rooms.mapIndexed { r, room -> if (moved[r] == room.points) room else room.copy(points = moved[r]) })
    }
}
