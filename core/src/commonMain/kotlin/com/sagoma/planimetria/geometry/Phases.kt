package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room

/**
 * Come si guarda il progetto rispetto allo stato di fatto (tavole delle pratiche edilizie):
 * - [Compare] tavola comparativa "gialli e rossi": tutto, demolizioni in giallo e costruzioni in rosso;
 * - [Existing] stato di fatto: com'è ora (senza le parti nuove);
 * - [Project] stato di progetto: come sarà (senza le parti demolite).
 */
enum class PhaseView {
    Compare,
    Existing,
    Project,
    ;

    /** Nome breve (pulsante), nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.phaseView(this)

    /** Titolo della tavola. */
    val title: String get() = com.sagoma.planimetria.testi.T.nomi.phaseViewTitle(this)
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

    /**
     * Quantità delle opere di un piano (m² e m³): muri da demolire e nuovi (una faccia, vuoto per pieno meno porte e finestre,
     * alti come la parete; il volume con lo spessore), vani da aprire nei muri che restano e aperture da chiudere.
     */
    data class Works(
        val demolishM2: Double = 0.0,
        val demolishM3: Double = 0.0,
        val newM2: Double = 0.0,
        val newM3: Double = 0.0,
        val openingsNew: Int = 0,
        val openingsNewM2: Double = 0.0,
        val openingsNewM3: Double = 0.0,
        val openingsClosed: Int = 0,
        val openingsClosedM2: Double = 0.0,
        val openingsClosedM3: Double = 0.0,
    ) {
        val isEmpty: Boolean get() = demolishM2 <= 0.0 && newM2 <= 0.0 && openingsNew == 0 && openingsClosed == 0

        operator fun plus(o: Works) = Works(
            demolishM2 + o.demolishM2, demolishM3 + o.demolishM3, newM2 + o.newM2, newM3 + o.newM3,
            openingsNew + o.openingsNew, openingsNewM2 + o.openingsNewM2, openingsNewM3 + o.openingsNewM3,
            openingsClosed + o.openingsClosed, openingsClosedM2 + o.openingsClosedM2, openingsClosedM3 + o.openingsClosedM3,
        )
    }

    /** Opere del piano (`levelHeight`: l'interpiano, per i muri singoli alti fino al soffitto). */
    fun works(plan: FloorPlan, levelHeight: Double = Floor.DEFAULT_LEVEL_HEIGHT): Works {
        // Muri curvi a pezzi dritti lungo l'arco: lunghi come l'arco.
        val p = plan.expandCurves().plan
        // cm² e cm³.
        var demArea = 0.0
        var demVolume = 0.0
        var newArea = 0.0
        var newVolume = 0.0
        fun add(ph: Phase, area: Double, thickness: Double) {
            if (ph == Phase.Demolish) { demArea += area; demVolume += area * thickness }
            if (ph == Phase.New) { newArea += area; newVolume += area * thickness }
        }
        for (r in p.rooms) for ((i, ph) in r.wallPhases) {
            if (ph == Phase.Existing || i !in 0 until r.wallCount || r.isRemoved(i)) continue
            // Un muro in comune tra due stanze si conta una volta sola (dalla stanza con l'id più basso che lo ha nello stesso stato).
            val shared = p.rooms.any { o ->
                o.id < r.id && (0 until o.wallCount).any { j -> o.phaseOf(j) == ph && !o.isRemoved(j) && Dimensions.sharesWall(r.wallStart(i), r.wallEnd(i), o.wallStart(j), o.wallEnd(j)) }
            }
            if (!shared) add(ph, face(r, i), r.thicknessOf(i))
        }
        for (w in p.freeWalls) if (w.phase != Phase.Existing && w.length >= 0.5) {
            add(w.phase, (w.host.arcOf(0)?.arcLength() ?: w.length) * Structure.freeWallHeight(p, w, levelHeight), w.thickness)
        }
        var opened = 0
        var openedArea = 0.0
        var openedVolume = 0.0
        var closed = 0
        var closedArea = 0.0
        var closedVolume = 0.0
        for (r in p.rooms + p.freeWalls.map { it.host }) for (o in r.openings) {
            val i = o.wallIndex
            // La nicchia non attraversa il muro.
            if (i !in 0 until r.wallCount || r.isRemoved(i) || o.kind == OpeningKind.Niche) continue
            val hole = Openings.holeArea(r, o)
            val t = r.thicknessOf(i)
            when {
                // Nei muri da demolire o nuovi il vano non si demolisce né si costruisce.
                r.phaseOf(i) != Phase.Existing -> add(r.phaseOf(i), -hole, t)
                o.phase == Phase.New -> { opened++; openedArea += hole; openedVolume += hole * t }
                o.phase == Phase.Demolish -> { closed++; closedArea += hole; closedVolume += hole * t }
            }
        }
        return Works(
            demArea.coerceAtLeast(0.0) / 1e4, demVolume.coerceAtLeast(0.0) / 1e6, newArea.coerceAtLeast(0.0) / 1e4, newVolume.coerceAtLeast(0.0) / 1e6,
            opened, openedArea / 1e4, openedVolume / 1e6, closed, closedArea / 1e4, closedVolume / 1e6,
        )
    }

    /** Una faccia del muro `i` (cm²): lunga come la mezzeria, alta come la parete (in mansarda segue la pendenza). */
    private fun face(r: Room, i: Int): Double {
        val len = r.wallLength(i)
        if (Ceilings.effectiveCut(r, i) == null || len < 1e-6) return len * r.wallHeight(i)
        val n = 64
        var sum = 0.0
        for (k in 0 until n) sum += Ceilings.wallTopAt(r, i, len * (k + 0.5) / n).coerceAtLeast(0.0)
        return len * sum / n
    }
}
