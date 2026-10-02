package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Phase

/**
 * Come si guarda il progetto rispetto allo stato di fatto (tavole delle pratiche edilizie):
 * - [Compare] tavola comparativa "gialli e rossi": tutto, demolizioni in giallo e costruzioni in rosso;
 * - [Existing] stato di fatto: com'è ora (senza le parti nuove);
 * - [Project] stato di progetto: come sarà (senza le parti demolite).
 */
enum class PhaseView(val label: String, val title: String) {
    Compare("Gialli e rossi", "Tavola comparativa"),
    Existing("Stato di fatto", "Stato di fatto"),
    Project("Stato di progetto", "Stato di progetto"),
}

object Phases {
    /** Il piano ha qualcosa da demolire o di nuovo (altrimenti le tre viste coincidono). */
    fun hasChanges(plan: FloorPlan): Boolean =
        plan.rooms.any { r -> r.wallPhases.values.any { it != Phase.Existing } || r.openings.any { it.phase != Phase.Existing } } ||
            plan.freeWalls.any { it.phase != Phase.Existing }

    /**
     * Pianta come appare nella vista: nello stato di fatto spariscono le parti nuove, nel progetto quelle da
     * demolire (i muri diventano lati aperti, le aperture spariscono: il muro resta pieno). Fuori dalla
     * comparativa non resta nessuno stato, quindi niente si colora.
     */
    fun view(plan: FloorPlan, v: PhaseView): FloorPlan {
        if (v == PhaseView.Compare || !hasChanges(plan)) return plan
        val gone = if (v == PhaseView.Existing) Phase.New else Phase.Demolish
        return plan.copy(
            rooms = plan.rooms.map { r ->
                val hidden = r.wallPhases.filterValues { it == gone }.keys
                r.copy(
                    removedWalls = r.removedWalls + hidden,
                    openings = r.openings.filter { it.phase != gone && it.wallIndex !in hidden }.map { it.copy(phase = Phase.Existing) },
                    fixtures = r.fixtures.filter { it.kind.mount != com.sagoma.planimetria.model.Mount.Wall || it.wallIndex !in hidden },
                    wallPhases = emptyMap(),
                )
            },
            freeWalls = plan.freeWalls.filter { it.phase != gone }.map { it.copy(phase = Phase.Existing) },
        )
    }

    /** Quantità delle opere (m² di muro da demolire e nuovi, aperture da fare e da chiudere). */
    data class Works(val demolishM2: Double, val newM2: Double, val openingsNew: Int, val openingsClosed: Int)

    fun works(plan: FloorPlan): Works {
        var dem = 0.0
        var new = 0.0
        for (r in plan.rooms) for ((i, ph) in r.wallPhases) if (i < r.wallCount && !r.isRemoved(i)) {
            // Un muro in comune tra due stanze si conta una volta sola (dalla stanza con l'id più basso).
            val shared = plan.rooms.any { o -> o.id < r.id && (0 until o.wallCount).any { j -> Dimensions.sharesWall(r.wallStart(i), r.wallEnd(i), o.wallStart(j), o.wallEnd(j)) } }
            if (shared) continue
            val m2 = r.wallLength(i) * r.wallHeight(i) / 10_000.0
            if (ph == Phase.Demolish) dem += m2 else if (ph == Phase.New) new += m2
        }
        for (w in plan.freeWalls) {
            val m2 = w.length * (w.height ?: 270.0) / 10_000.0
            if (w.phase == Phase.Demolish) dem += m2 else if (w.phase == Phase.New) new += m2
        }
        val ops = plan.rooms.flatMap { it.openings }
        return Works(dem, new, ops.count { it.phase == Phase.New }, ops.count { it.phase == Phase.Demolish })
    }
}
