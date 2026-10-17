package com.sagoma.planimetria.scan.reconstruction.quality

import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.EvidenceQuality
import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.PerimeterResult
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.SurfaceAmbiguity
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimationResult

/**
 * M4 — valutazione della qualità della ricostruzione. Funzione pura e deterministica dei risultati di R1–R4.1 (che non modifica).
 * Nessuna decisione di rescansione, nessuna semantica di oggetti: solo misura, completezza, ambiguità, evidenza e difetti.
 */
object QualityEngine {

    /** Ordine per il "peggiore": LOW < UNKNOWN < MEDIUM < HIGH (un dato mancante non migliora mai il giudizio). */
    private fun rank(l: QualityLevel) = when (l) { QualityLevel.LOW -> 0; QualityLevel.UNKNOWN -> 1; QualityLevel.MEDIUM -> 2; QualityLevel.HIGH -> 3 }
    fun worst(levels: List<QualityLevel>): QualityLevel = levels.minByOrNull { rank(it) } ?: QualityLevel.UNKNOWN

    /** Fatti del perimetro R4 così come R4 li produce. Il "gap interno R3" ripetuto da R4 non è contato due volte. */
    fun perimeterFacts(r4: PerimeterResult?): PerimeterFacts {
        val main = r4?.main
        val unobserved = r4?.perimeters.orEmpty().flatMap { p -> p.unobserved.filter { it.kind != "gap interno R3" }.map { Triple(it.wallId, it.kind, it.lengthM) } }
        return PerimeterFacts(
            r4?.state, main?.wallIds.orEmpty(), r4?.walls.orEmpty().associate { it.wallId to it.role.label }, unobserved,
            main?.closure?.chi2, main?.closure?.dof, main?.closure?.pValue, main?.closure?.compatible, main?.closure?.unobservedM,
        )
    }

    /** Valutazione dai risultati della pipeline. */
    fun evaluate(map: GlobalMap?, surfaces: SurfaceResult, walls: WallEstimationResult, r4: PerimeterResult?, config: QualityConfig = QualityConfig()): QualityResult =
        evaluate(walls.walls, perimeterFacts(r4), surfaces.ambiguity, surfaces.surfaces.associate { it.id to it.areaM2 }, map?.stats?.planExploredM2, config)

    private fun sigmaLevel(v: Double, high: Double, medium: Double) = when { v.isNaN() -> QualityLevel.UNKNOWN; v <= high -> QualityLevel.HIGH; v <= medium -> QualityLevel.MEDIUM; else -> QualityLevel.LOW }

    /** Valutazione da dati già estratti (usata anche dai test con ingressi costruiti). */
    fun evaluate(
        walls: List<EstimatedWall>, perimeter: PerimeterFacts, ambiguity: Map<Int, SurfaceAmbiguity>, surfaceArea: Map<Int, Double>, exploredAreaM2: Double?,
        config: QualityConfig = QualityConfig(),
    ): QualityResult {
        val wq = walls.sortedBy { it.id }.map { wall(it, perimeter, ambiguity, surfaceArea, config) }
        val ids = roomWallIds(wq, perimeter).toSet()
        val marked = wq.map { it.copy(inRoom = it.wallId in ids) }
        return QualityResult(config, marked, room(marked, perimeter, exploredAreaM2, config))
    }

    /** Pareti della stanza: quelle del perimetro principale R4 se esiste, altrimenti tutte le pareti R3. */
    private fun roomWallIds(all: List<WallQuality>, pf: PerimeterFacts): List<Int> =
        if (pf.mainWallIds.isNotEmpty()) pf.mainWallIds.filter { id -> all.any { it.wallId == id } } else all.map { it.wallId }

    private fun wall(w: EstimatedWall, pf: PerimeterFacts, amb: Map<Int, SurfaceAmbiguity>, area: Map<Int, Double>, c: QualityConfig): WallQuality {
        // Misura.
        val u = w.uncertainty
        val pl = sigmaLevel(u.positionSigmaM, c.positionHighM, c.positionMediumM)
        val hl = sigmaLevel(u.headingSigmaDeg, c.headingHighDeg, c.headingMediumDeg)
        val m = MeasurementAxis(u.positionSigmaM, u.headingSigmaDeg, w.evidence.fitRmsM, w.evidence.dispersionM, u.calibrated, pl, hl, worst(listOf(pl, hl)))

        // Completezza: osservato (R3), gap interni (R3), tratti dedotti da R4 per questa parete: tutti non osservati.
        val obs = w.geometry.observedLengthM
        val gapR3 = w.gaps.sumOf { it.lengthM }
        val gapR4 = pf.unobserved.filter { it.first == w.id }.sumOf { it.third }
        val cov = if (obs + gapR3 + gapR4 <= 0.0) 0.0 else obs / (obs + gapR3 + gapR4)
        val bothObserved = w.start.state == EndState.OBSERVED && w.end.state == EndState.OBSERVED
        val cl = when { cov >= c.coverageHigh && bothObserved -> QualityLevel.HIGH; cov >= c.coverageMedium -> QualityLevel.MEDIUM; else -> QualityLevel.LOW }
        val comp = CompletenessAxis(obs, gapR3, gapR4, w.start.state, w.start.reason, w.end.state, w.end.reason, cl, extentUnknown = !bothObserved && gapR4 == 0.0)

        // Ambiguità (R2.1): massimo conservativo + media pesata per area sulle superfici sorgente.
        val src = w.sourceSurfaceIds.mapNotNull { id -> amb[id]?.let { id to it } }
        val top = src.maxWithOrNull(compareBy<Pair<Int, SurfaceAmbiguity>> { it.second.score }.thenByDescending { it.first })
        val wsum = src.sumOf { area[it.first] ?: 0.0 }
        val mean = if (src.isEmpty()) null else if (wsum > 0) src.sumOf { (area[it.first] ?: 0.0) * it.second.score } / wsum else src.map { it.second.score }.average()
        val high = top != null && top.second.score >= c.ambiguityTau
        val unstable = high && top!!.second.stability < c.ambiguityTau
        val al = when { top == null -> QualityLevel.UNKNOWN; high -> QualityLevel.LOW; else -> QualityLevel.HIGH }
        val a = AmbiguityAxis(top?.second?.score, top?.first, top?.second?.stability, mean, unstable, al)

        // Evidenza: qualità R3 riusata; viste = gruppi di vista R1 contati da R3.
        val ev = w.evidence
        val el = when (ev.quality) { EvidenceQuality.HIGH -> QualityLevel.HIGH; EvidenceQuality.MEDIUM -> QualityLevel.MEDIUM; EvidenceQuality.LOW -> QualityLevel.LOW }
        val e = EvidenceAxis(ev.quality, ev.qualityReasons, ev.viewGroups, ev.rawFrames, ev.pointCount, if (ev.viewGroups < c.minViews) worst(listOf(el, QualityLevel.LOW)) else el)

        val d = mutableListOf<Defect>()
        if (pl == QualityLevel.LOW) d.add(Defect(DefectCode.HIGH_POSITION_UNCERTAINTY, w.id, u.positionSigmaM, "σ posizione ${fmt(u.positionSigmaM * 100)} cm"))
        if (hl == QualityLevel.LOW) d.add(Defect(DefectCode.HIGH_DIRECTION_UNCERTAINTY, w.id, u.headingSigmaDeg, "σ direzione ${fmt(u.headingSigmaDeg)}°"))
        if (w.start.state == EndState.PARTIAL) d.add(Defect(DefectCode.PARTIAL_START, w.id, null, w.start.reason.label))
        if (w.end.state == EndState.PARTIAL) d.add(Defect(DefectCode.PARTIAL_END, w.id, null, w.end.reason.label))
        if (w.start.state == EndState.UNCERTAIN) d.add(Defect(DefectCode.UNCERTAIN_START, w.id, null, w.start.reason.label))
        if (w.end.state == EndState.UNCERTAIN) d.add(Defect(DefectCode.UNCERTAIN_END, w.id, null, w.end.reason.label))
        if (comp.unobservedLengthM > 0) d.add(Defect(DefectCode.UNOBSERVED_SEGMENT, w.id, comp.unobservedLengthM, "non osservati ${fmt(comp.unobservedLengthM)} m (R3 ${fmt(gapR3)} m, dedotti da R4 ${fmt(gapR4)} m)"))
        if (ev.viewGroups < c.minViews) d.add(Defect(DefectCode.LOW_VIEW_COUNT, w.id, ev.viewGroups.toDouble(), "${ev.viewGroups} viste indipendenti"))
        if (ev.quality == EvidenceQuality.LOW) d.add(Defect(DefectCode.LOW_EVIDENCE, w.id, null, ev.qualityReasons.joinToString(" · ")))
        if (high) d.add(Defect(DefectCode.HIGH_AMBIGUITY, w.id, top!!.second.score, "superficie S${top.first}: score ${fmt(top.second.score)}"))
        if (unstable) d.add(Defect(DefectCode.UNSTABLE_AMBIGUITY, w.id, top!!.second.stability, "superficie S${top.first}: stabilità ${fmt(top.second.stability)}"))

        return WallQuality(w.id, w.sourceSurfaceIds, pf.roles[w.id], false, m, comp, a, e, worst(listOf(m.level, comp.level, a.level, e.level)), d)
    }

    private fun summary(rows: List<WallQuality>, value: (WallQuality) -> Double?, level: (WallQuality) -> QualityLevel, higherIsWorse: Boolean, n: Int): AxisSummary {
        val v = rows.mapNotNull { r -> value(r)?.takeIf { !it.isNaN() }?.let { r.wallId to it } }
        val dist = QualityLevel.values().associateWith { l -> rows.count { level(it) == l } }
        val order = if (higherIsWorse) v.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first }) else v.sortedWith(compareBy<Pair<Int, Double>> { it.second }.thenBy { it.first })
        return AxisSummary(v.size, v.minOfOrNull { it.second }, v.maxOfOrNull { it.second }, if (v.isEmpty()) null else v.sumOf { it.second } / v.size, dist, order.take(n).map { it.first }, worst(rows.map(level)))
    }

    private fun room(all: List<WallQuality>, pf: PerimeterFacts, explored: Double?, c: QualityConfig): RoomQuality {
        val ids = roomWallIds(all, pf)
        val rows = all.filter { it.wallId in ids }
        val obs = rows.sumOf { it.completeness.observedLengthM }; val un = rows.sumOf { it.completeness.unobservedLengthM }
        val cov = if (obs + un <= 0) 0.0 else obs / (obs + un)
        val closed = pf.state == PerimeterState.CLOSED
        val closure = when (pf.state) { null -> QualityLevel.UNKNOWN; PerimeterState.CLOSED -> QualityLevel.HIGH; else -> QualityLevel.LOW }
        val completeness = when { rows.isEmpty() -> QualityLevel.UNKNOWN; cov >= c.coverageHigh && closed -> QualityLevel.HIGH; cov >= c.coverageMedium -> QualityLevel.MEDIUM; else -> QualityLevel.LOW }
        val pos = summary(rows, { it.measurement.positionSigmaM }, { it.measurement.positionLevel }, true, c.worstN)
        val head = summary(rows, { it.measurement.headingSigmaDeg }, { it.measurement.headingLevel }, true, c.worstN)
        val covS = summary(rows, { it.completeness.coverageRatio }, { it.completeness.level }, false, c.worstN)
        val ambS = summary(rows, { it.ambiguity.maxScore }, { it.ambiguity.level }, true, c.worstN)
        val evS = summary(rows, { it.evidence.viewCount.toDouble() }, { it.evidence.level }, false, c.worstN)
        val geometry = worst(rows.map { it.measurement.level })
        val semantic = worst(rows.map { it.ambiguity.level })
        val evidence = worst(rows.map { it.evidence.level })
        val d = mutableListOf<Defect>()
        when (pf.state) {
            null, PerimeterState.OPEN -> d.add(Defect(DefectCode.OPEN_PERIMETER, null, null, "stato R4: ${pf.state ?: "nessun perimetro"}"))
            PerimeterState.PARTIAL -> d.add(Defect(DefectCode.PARTIAL_PERIMETER, null, cov, "catena aperta: la geometria può essere utile, la completezza non è 100%"))
            PerimeterState.UNCERTAIN -> d.add(Defect(DefectCode.UNCERTAIN_PERIMETER, null, pf.pValue, "anello con condizioni non verificate (R4)"))
            PerimeterState.CLOSED -> {}
        }
        return RoomQuality(
            ids, rows.size, all.size, obs, un, cov, !closed || rows.any { it.completeness.extentUnknown }, pf.state, closed, pf.chi2, pf.dof, pf.pValue, pf.compatible, pf.closureUnobservedM, closure, explored,
            pos, head, covS, ambS, evS, rows.count { it.ambiguity.level == QualityLevel.LOW }, rows.count { it.ambiguity.unstable },
            if (rows.isEmpty()) QualityLevel.UNKNOWN else geometry, completeness, if (rows.isEmpty()) QualityLevel.UNKNOWN else semantic, if (rows.isEmpty()) QualityLevel.UNKNOWN else evidence,
            worst(listOf(if (rows.isEmpty()) QualityLevel.UNKNOWN else geometry, completeness, closure, if (rows.isEmpty()) QualityLevel.UNKNOWN else semantic, if (rows.isEmpty()) QualityLevel.UNKNOWN else evidence)),
            d,
        )
    }

    private fun fmt(v: Double) = com.sagoma.planimetria.geometry.formatDecimal(v, 2, ',')
}
