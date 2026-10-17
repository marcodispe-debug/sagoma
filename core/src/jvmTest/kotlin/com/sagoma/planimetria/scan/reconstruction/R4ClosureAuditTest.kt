package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SOLO AUDIT DI R4 (jvmTest, nessuna modifica di produzione). Usa le uscite R3.1/R4 attuali così come sono e:
 *  1. traccia ogni collegamento candidato separando distanza geometrica, incertezza propagata, qualità dell'evidenza, stato delle
 *     estremità ed estensione non osservata;
 *  2. costruisce in memoria scenari sull'angolo problematico della stanza sintetica (R1→R4) per vedere se "gap maggiore + σ maggiore"
 *     diventa equivalente a "angolo osservato + errore maggiore".
 * Sempre: determinismo degli scenari sintetici. Con SAGOMA_R4AUDIT_OUT=<cartella> scrive `r4-closure-audit.json` e `.csv`; con
 * SAGOMA_R4AUDIT_ZIPS=<zip1;zip2> aggiunge i dataset reali.
 */
class R4ClosureAuditTest {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else "%.${d}f".format(Locale.ROOT, v)

    private class Case(val name: String, val map: GlobalMap, val s: SurfaceResult, val walls: WallEstimationResult, val r4: PerimeterResult)

    private fun synthetic(): Case {
        val (rec, blobs) = SyntheticRoom(noiseM = 0.01).recording()
        val map = GlobalMap.build(rec, { blobs[it] })
        val s = SurfaceExtractor.extract(map, rec)
        val walls = WallEstimator.estimate(WallEstimator.inputFrom(map, s, rec))
        return Case("synthetic-full-R1-R4", map, s, walls, PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, walls)))
    }

    private fun real(zip: File): Case {
        val ds = CaptureDatasetFiles.open(zip)
        val map = GlobalMap.build(ds.recording, { ds.read(it) })
        val s = SurfaceExtractor.extract(map, ds.recording)
        val walls = WallEstimator.estimate(WallEstimator.inputFrom(map, s, ds.recording))
        return Case(zip.nameWithoutExtension, map, s, walls, PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, walls)))
    }

    // ------------------------------------------------------------------------------------------------ 1. audit dei collegamenti

    /** Evidenza lungo il tratto [u0, u1] della linea di [w] (alle quote della parete): punti R1 vicini alla linea e stato dello spazio. */
    private class GapEvidence(val r1CoveredPct: Double, val assignedPct: Double, val occupiedPct: Double, val freePct: Double, val unknownPct: Double)

    private fun gapEvidence(c: Case, w: EstimatedWall, u0: Double, u1: Double): GapEvidence? {
        val lo = minOf(u0, u1); val hi = maxOf(u0, u1)
        if (hi - lo < 0.01) return null
        val g = w.geometry
        val tol = c.s.thresholds.growDistM
        val bins = max(1, ((hi - lo) / 0.02).toInt())
        val r1 = BooleanArray(bins); val own = BooleanArray(bins)
        val sz = c.map.voxels.sizeM
        val ownIds = w.sourceSurfaceIds.toSet()
        for (i in 0 until c.map.points.size) {
            val x = c.map.points.x[i].toDouble(); val y = c.map.points.y[i].toDouble(); val z = c.map.points.z[i].toDouble()
            if (y < g.bottomY || y > g.topY) continue
            val u = (x - g.cx) * g.ux + (z - g.cz) * g.uz
            if (u < lo || u >= hi) continue
            if (abs(g.nx * x + g.nz * z - g.d) > tol) continue
            val b = ((u - lo) / (hi - lo) * bins).toInt().coerceIn(0, bins - 1)
            r1[b] = true
            val v = c.map.voxels.find(VoxelIndex.key(floor(x / sz).toInt(), floor(y / sz).toInt(), floor(z / sz).toInt()))
            if (v >= 0 && c.s.voxelSurface[v] in ownIds) own[b] = true
        }
        var occ = 0; var free = 0; var unk = 0; var n = 0
        var u = lo + 0.01
        while (u < hi) {
            val q = g.pointAt(u)
            for (y in doubleArrayOf(g.bottomY + 0.25 * (g.topY - g.bottomY), (g.bottomY + g.topY) / 2, g.bottomY + 0.75 * (g.topY - g.bottomY))) {
                when (c.map.freeSpace.state(q[0], y, q[1])) { 2 -> occ++; 1 -> free++; else -> unk++ }
                n++
            }
            u += 0.02
        }
        return GapEvidence(r1.count { it } * 100.0 / bins, own.count { it } * 100.0 / bins, occ * 100.0 / n, free * 100.0 / n, unk * 100.0 / n)
    }

    /** Interpretazione semantica di un'estremità rispetto all'angolo: (etichetta, compatibile usando SOLO la σ di misura/ripetibilità). */
    private fun endSemantic(e: WallEnd, ext: Double, z: Double): Pair<String, Boolean?> {
        val rep = e.evidence?.repeatSigmaM
        val zRep = if (rep != null && rep > 0) ext / rep else null
        val label = when {
            ext <= 0 -> "dati fino all'angolo o oltre"
            e.state == EndState.OBSERVED && e.reason == EndReason.CORNER -> "B: angolo osservato con errore"
            z > 3 -> "A: fine non osservata, prolungamento dedotto (dichiarato)"
            else -> "A: fine non osservata trattata come raggiunta entro σ"
        }
        return label to zRep?.let { it <= 3 }
    }

    private fun auditRows(c: Case): List<String> {
        val byId = c.walls.walls.associateBy { it.id }
        return c.r4.candidates.filter { it.kind == LinkKind.CORNER && it.cornerX != null }.sortedWith(compareBy({ it.fromWall }, { it.toWall })).map { l ->
            val a = byId.getValue(l.fromWall); val b = byId.getValue(l.toWall)
            val pa = a.geometry.pointAt(a.end.u); val pb = b.geometry.pointAt(b.start.u)
            val endDist = sqrt((pa[0] - pb[0]) * (pa[0] - pb[0]) + (pa[1] - pb[1]) * (pa[1] - pb[1]))
            val sComb = sqrt(a.end.sigmaM * a.end.sigmaM + b.start.sigmaM * b.start.sigmaM)
            val geomGap = max(0.0, l.extensionFromM) + max(0.0, l.extensionToM)
            val ea = if (l.extensionFromM > 0) gapEvidence(c, a, a.end.u, a.end.u + l.extensionFromM) else null
            val eb = if (l.extensionToM > 0) gapEvidence(c, b, b.start.u - l.extensionToM, b.start.u) else null
            val (semA, repOkA) = endSemantic(a.end, l.extensionFromM, l.zFrom)
            val (semB, repOkB) = endSemantic(b.start, l.extensionToM, l.zTo)
            // Il caso critico: supporto OSSERVATO con almeno un'estremità NON osservata che si ferma prima dell'angolo oltre 3σ di misura.
            val critical = l.support == LinkSupport.OBSERVED && listOf(Triple(a.end, l.extensionFromM, repOkA), Triple(b.start, l.extensionToM, repOkB))
                .any { (e, ext, ok) -> e.state != EndState.OBSERVED && ext > 0 && ok == false }
            val viaCorner = l.support == LinkSupport.OBSERVED && listOf(Triple(a.end, l.extensionFromM, l.extensionFromSigmaM), Triple(b.start, l.extensionToM, l.extensionToSigmaM))
                .any { (e, ext, _) -> ext > 3 * e.sigmaM }
            // Componente dominante della σ propagata sull'estremità critica: intersezione (linee quasi parallele) o σ dell'estremità.
            val dominant = listOf(Triple(a.end, l.extensionFromM, l.cornerAlongFromSigmaM), Triple(b.start, l.extensionToM, l.cornerAlongToSigmaM))
                .filter { (e, ext, _) -> e.state != EndState.OBSERVED && ext > 0 }
                .joinToString(", ") { (e, _, along) ->
                    val term = e.evidence?.terminalSigmaM ?: 0.0; val rep = e.evidence?.repeatSigmaM ?: e.sigmaM
                    when {
                        (along ?: 0.0) >= maxOf(term, rep) -> "intersezione σ ${f((along ?: 0.0) * 100, 1)} cm (linee quasi parallele)"
                        term >= rep -> "σ terminale ${f(term * 100, 1)} cm (evidenza mancante)"
                        else -> "σ di ripetibilità ${f(rep * 100, 1)} cm"
                    }
                }
            val flag = when {
                l.decision == LinkDecision.REJECTED -> "rifiutato"
                critical -> "CRITICO: tratto non osservato oltre 3σ di misura trattato come raggiunto; σ dominante: $dominant"
                viaCorner -> "ATTENZIONE: compatibile solo per la σ dell'intersezione (linee quasi parallele), non per la σ dell'estremità"
                l.support == LinkSupport.INFERRED -> "corretto: prolungamento dichiarato non osservato"
                else -> "ok"
            }
            listOf(
                c.name, "W${l.fromWall}", "W${l.toWall}", l.decision, l.support, f(l.score, 4), f(l.turnDeg, 1), f(endDist, 4), f(a.end.sigmaM, 4), f(b.start.sigmaM, 4), f(sComb, 4), f(endDist / sComb, 2),
                f(l.extensionFromM, 4), f(l.extensionToM, 4), f(l.extensionFromSigmaM, 4), f(l.extensionToSigmaM, 4), f(l.zFrom, 2), f(l.zTo, 2), f(l.cornerSigmaM, 4),
                f(geomGap, 4), f(l.unobservedM, 4),
                f(ea?.r1CoveredPct, 0), f(ea?.assignedPct, 0), f(ea?.occupiedPct, 0), f(ea?.freePct, 0), f(eb?.r1CoveredPct, 0), f(eb?.assignedPct, 0), f(eb?.occupiedPct, 0), f(eb?.freePct, 0),
                "${a.end.state}/${a.end.reason}", "${b.start.state}/${b.start.reason}", a.end.evidence?.groupsNearEnd ?: "", b.start.evidence?.groupsNearEnd ?: "", a.evidence.viewGroups, b.evidence.viewGroups,
                a.evidence.quality, b.evidence.quality, f(a.end.evidence?.repeatSigmaM, 4), f(a.end.evidence?.terminalSigmaM, 4), f(b.start.evidence?.repeatSigmaM, 4), f(b.start.evidence?.terminalSigmaM, 4),
                f(l.components.freeSpace, 2), semA, semB, flag, l.reasons.joinToString(" | "),
            ).joinToString(";") { it.toString().replace(";", ",") }
        }
    }

    private val auditHeader = "dataset;from;to;decision;support;score;turnDeg;endDistM;sigmaEndA;sigmaEndB;sigmaComb;endDistOverSigma;" +
        "extA;extB;sigmaExtA;sigmaExtB;zA;zB;cornerSigmaM;geomUnobservedM;r4UnobservedM;" +
        "gapA_R1pct;gapA_ownSurfacePct;gapA_occupiedPct;gapA_freePct;gapB_R1pct;gapB_ownSurfacePct;gapB_occupiedPct;gapB_freePct;" +
        "stateA;stateB;viewsNearEndA;viewsNearEndB;viewsWallA;viewsWallB;qualityA;qualityB;repeatSigmaA;terminalSigmaA;repeatSigmaB;terminalSigmaB;" +
        "freeSpaceFactor;semanticA;semanticB;flag;r4Reasons"

    // ---------------------------------------------------------------------------------------------------------- 2. scenari

    /** Sposta l'estremità ([start] = inizio, altrimenti fine) di [w] a [gap] metri dall'angolo (x, z), con σ e stato dati. Solo in memoria. */
    private fun moveEnd(w: EstimatedWall, start: Boolean, cx: Double, cz: Double, gap: Double, sigma: Double, state: EndState, reason: EndReason, repeat: Double, terminal: Double): EstimatedWall {
        val g = w.geometry
        val away = if (start) 1.0 else -1.0
        val px = cx + g.ux * away * gap; val pz = cz + g.uz * away * gap
        val u = (px - g.cx) * g.ux + (pz - g.cz) * g.uz
        val spans = g.observedSpans.toMutableList()
        if (start) spans[0] = WallSpan(u, spans[0].toU) else spans[spans.lastIndex] = WallSpan(spans.last().fromU, u)
        val geo = if (start) g.copy(startU = u, observedSpans = spans) else g.copy(endU = u, observedSpans = spans)
        val old = if (start) w.start else w.end
        val ev = old.evidence?.copy(repeatSigmaM = repeat, terminalSigmaM = terminal, surfaceGapM = gap, surfaceGapSigmas = gap / sigma)
        val end = WallEnd(u, state, reason, sigma, ev)
        val unc = if (start) w.uncertainty.copy(startSigmaM = sigma) else w.uncertainty.copy(endSigmaM = sigma)
        return if (start) w.copy(geometry = geo, start = end, uncertainty = unc) else w.copy(geometry = geo, end = end, uncertainty = unc)
    }

    private class Scenario(val group: String, val name: String, val gap: Double, val sigma: Double, val repeat: Double, val state: EndState, val reason: EndReason, val semantic: String)

    private fun scenarios(repeat: Double): List<Scenario> {
        val out = mutableListOf<Scenario>()
        out.add(Scenario("1", "angolo osservato con rumore", 0.01, repeat, repeat, EndState.OBSERVED, EndReason.CORNER, "B: angolo osservato con errore"))
        out.add(Scenario("2", "angolo osservato con rumore maggiore (σ 5 cm, scarto 6 cm)", 0.06, 0.05, 0.05, EndState.OBSERVED, EndReason.CORNER, "B: angolo osservato con errore"))
        for (g in listOf(0.08, 0.20, 0.50)) out.add(Scenario("${if (g == 0.08) 3 else if (g == 0.20) 4 else 5}", "fine ${f(g * 100, 0)} cm prima dell'angolo (σ di misura)", g, repeat, repeat, EndState.PARTIAL, EndReason.SURFACE_NEAR, "A: non so dove finisce il muro"))
        // 6: stessi gap con σ dell'estremità crescente per evidenza terminale mancante (σ = ripetibilità ⊕ terminale).
        for (g in listOf(0.08, 0.20, 0.50)) for (sg in listOf(0.022, 0.03, 0.046, 0.06, 0.079, 0.10, 0.15, 0.20)) {
            // Stato coerente con R3: oltre 8 cm di σ un'estremità non d'angolo è UNCERTAIN.
            val st = if (sg > 0.08) EndState.UNCERTAIN else EndState.PARTIAL
            out.add(Scenario("6", "gap ${f(g * 100, 0)} cm, σ crescente (stato come R3)", g, sg, repeat, st, if (st == EndState.UNCERTAIN) EndReason.UNSTABLE else EndReason.SURFACE_NEAR, "A: non so dove finisce il muro"))
            out.add(Scenario("6b", "gap ${f(g * 100, 0)} cm, σ crescente, forzato PARTIAL", g, sg, repeat, EndState.PARTIAL, EndReason.SURFACE_NEAR, "A: non so dove finisce il muro"))
            out.add(Scenario("6c", "confronto: angolo OSSERVATO a ${f(g * 100, 0)} cm con σ", g, sg, sg, EndState.OBSERVED, EndReason.CORNER, "B: angolo osservato con errore"))
        }
        return out
    }

    private fun runScenarios(label: String, base: List<EstimatedWall>, target: Int, start: Boolean, cx: Double, cz: Double, partner: Int, solve: (List<EstimatedWall>) -> PerimeterResult): List<String> {
        val w = base.first { it.id == target }
        val repeat = (if (start) w.start else w.end).evidence?.repeatSigmaM ?: (if (start) w.start else w.end).sigmaM
        return scenarios(repeat).map { sc ->
            val term = sqrt(max(0.0, sc.sigma * sc.sigma - sc.repeat * sc.repeat))
            val moved = moveEnd(w, start, cx, cz, sc.gap, sc.sigma, sc.state, sc.reason, sc.repeat, term)
            val r = solve(base.map { if (it.id == target) moved else it })
            val l = r.candidates.firstOrNull { if (start) it.fromWall == partner && it.toWall == target else it.fromWall == target && it.toWall == partner }
            val main = r.main
            val inMainCycle = main?.closure != null && target in main.wallIds
            val zLink = if (start) l?.zTo else l?.zFrom
            val verdict = when {
                r.state == PerimeterState.CLOSED && sc.semantic.startsWith("A") -> "FALSA CHIUSURA: tratto non osservato usato come misura"
                r.state == PerimeterState.CLOSED -> "chiusura legittima (angolo osservato)"
                l?.support == LinkSupport.OBSERVED && sc.semantic.startsWith("A") -> "collegamento OSSERVATO su fine non osservata (non chiude per altri motivi: ${main?.reasons?.firstOrNull { "instabile" in it || "dedotto" in it || "compatibile" in it } ?: "-"})"
                else -> "prudente: non chiude"
            }
            listOf(
                label, sc.group, sc.name, f(sc.gap, 3), f(sc.sigma, 4), f(sc.gap / sc.sigma, 2), f(sc.repeat, 4), f(sc.gap / sc.repeat, 2), "${sc.state}/${sc.reason}",
                l?.decision ?: "-", l?.support ?: "-", f(zLink, 2), r.state, main?.state ?: "-", if (inMainCycle) "anello" else "catena", f(main?.closure?.pValue, 4),
                sc.semantic, verdict,
            ).joinToString(";") { it.toString().replace(";", ",") }
        }
    }

    private val scenarioHeader = "base;scenario;description;gapM;sigmaM;gapOverSigma;measurementSigmaM;gapOverMeasurementSigma;endpointState;linkDecision;linkSupport;zLink;r4State;mainState;mainShape;closureP;semantic;verdict"

    /** Scenari sulla stanza sintetica R1→R4: l'inizio della parete z = 3 (caso degli 8 cm), angolo vero (0, 3). */
    private fun syntheticScenarios(c: Case): List<String> {
        val target = c.walls.walls.first { abs(it.geometry.nz) > 0.9 && abs(it.geometry.cz - 3) < 0.05 }
        val start = abs(target.geometry.pointAt(target.start.u)[0]) < abs(target.geometry.pointAt(target.end.u)[0])
        val partner = c.walls.walls.first { abs(it.geometry.nx) > 0.9 && abs(it.geometry.cx) < 0.05 }.id
        val w = c.walls
        return runScenarios("synthetic-full-R1-R4 (W${target.id} ${if (start) "inizio" else "fine"}, angolo (0,3))", w.walls, target.id, start, 0.0, 3.0, partner) { ws ->
            PerimeterSolver.solve(PerimeterSolver.inputFrom(c.map, c.s, WallEstimationResult(w.inputVertical, w.inputStructural, ws, w.excluded, w.merges, w.noise, w.temporalFallbackM, w.diagnostics)))
        }
    }

    /** Stessi scenari su un quadrato ideale (tutte le altre estremità osservate esattamente): isola l'effetto della sola estremità variata. */
    private fun idealScenarios(): List<String> {
        fun wall(id: Int, ax: Double, az: Double, bx: Double, bz: Double): EstimatedWall {
            val l = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az)); val ux = (bx - ax) / l; val uz = (bz - az) / l
            val cx = (ax + bx) / 2; val cz = (az + bz) / 2
            val g = WallGeometry(uz, -ux, uz * cx - ux * cz, cx, cz, ux, uz, -l / 2, l / 2, 0.0, 2.4, 0.0, 2.4, Math.toDegrees(atan2(uz, ux)), listOf(WallSpan(-l / 2, l / 2)))
            val ev = EndEvidence(1.0, 1.0, 1.0, 1.0, 0.0, l, null, 10, 0.016, 0.0, null, 0.0, 0.0)
            return EstimatedWall(
                id, listOf(id), g, WallUncertainty(0.005, 0.3, 0.016, 0.016, 0.023, 0.0, 0.0, 0.005, 0.02),
                WallEvidence(1000, 900, 10, 10, 60.0, 2.0, 0.01, 0.005, 1.0, 0.0, EvidenceQuality.HIGH, emptyList()),
                WallEnd(-l / 2, EndState.OBSERVED, EndReason.CORNER, 0.016, ev), WallEnd(l / 2, EndState.OBSERVED, EndReason.CORNER, 0.016, ev), emptyList(),
                WallThickness(ThicknessState.UNKNOWN, note = ""), 0.8, emptyList(), emptyList(),
            )
        }
        val square = listOf(wall(1, 0.0, 0.0, 0.0, 3.0), wall(2, 0.0, 3.0, 4.0, 3.0), wall(3, 4.0, 3.0, 4.0, 0.0), wall(4, 4.0, 0.0, 0.0, 0.0))
        val cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 1.5), doubleArrayOf(3.0, 2.0), doubleArrayOf(1.5, 2.2), doubleArrayOf(2.8, 0.8))
        // Varia l'inizio della parete 2 (z = 3), angolo (0, 3), partner W1.
        return runScenarios("quadrato ideale 4×3 (W2 inizio, angolo (0,3))", square, 2, true, 0.0, 3.0, 1) { ws ->
            PerimeterSolver.solve(PerimeterInput(ws, { _, _, _ -> 0 }, 0.10, cams))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- uscite

    private fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun json(audit: List<String>, scen: List<String>, cases: List<Case>): String = buildString {
        fun rows(header: String, lines: List<String>) = lines.joinToString(",\n") { line ->
            val keys = header.split(';'); val vals = line.split(';')
            "{" + keys.indices.joinToString(",") { "${js(keys[it])}:${js(vals.getOrElse(it) { "" })}" } + "}"
        }
        append("{\n\"note\":").append(js("SOLO AUDIT DI R4: uscite R3.1/R4 attuali, scenari costruiti in memoria. Nessuna modifica di produzione.")).append(",\n")
        append("\"r4States\":{").append(cases.joinToString(",") { "${js(it.name)}:${js(it.r4.state.name)}" }).append("},\n")
        append("\"edges\":[\n").append(rows(auditHeader, audit)).append("\n],\n\"scenarios\":[\n").append(rows(scenarioHeader, scen)).append("\n]}\n")
    }

    private fun all(cases: List<Case>): Pair<List<String>, List<String>> {
        val audit = cases.flatMap { auditRows(it) }
        val scen = syntheticScenarios(cases.first()) + idealScenarios()
        return audit to scen
    }

    @Test
    fun `audit R4 deterministico sugli scenari sintetici`() {
        val a = all(listOf(synthetic())); val b = all(listOf(synthetic()))
        assertEquals(a, b)
    }

    @Test
    fun `report audit R4 (solo con SAGOMA_R4AUDIT_OUT)`() {
        val out = System.getenv("SAGOMA_R4AUDIT_OUT") ?: return
        val cases = mutableListOf(synthetic())
        System.getenv("SAGOMA_R4AUDIT_ZIPS")?.split(';')?.filter { it.isNotBlank() }?.forEach { cases.add(real(File(it))) }
        val (audit, scen) = all(cases)
        val dir = File(out).also { it.mkdirs() }
        File(dir, "r4-closure-audit.csv").writeText("# collegamenti candidati\n$auditHeader\n" + audit.joinToString("\n") + "\n\n# scenari\n$scenarioHeader\n" + scen.joinToString("\n") + "\n")
        File(dir, "r4-closure-audit.json").writeText(json(audit, scen, cases))
        // Le uscite R4 usate sono quelle di produzione: perimeter.json identico a quello della pipeline.
        for (c in cases.drop(1)) System.getenv("SAGOMA_R4AUDIT_BASELINE")?.let { base ->
            val f = File(base, "${c.name.takeLast(6)}/perimeter.json")
            kotlin.test.assertTrue(f.exists(), "baseline mancante: $f")
            assertEquals(f.readText(), PerimeterReport.json(c.r4), "R4 in memoria diverso dalla baseline per ${c.name}")
            assertEquals(File(base, "${c.name.takeLast(6)}/walls.csv").readText(), WallReport.csv(c.walls), "R3.1 in memoria diverso dalla baseline per ${c.name}")
        }
    }
}
