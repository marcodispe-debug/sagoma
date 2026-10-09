package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SOLO AUDIT (jvmTest, nessuna modifica di R4): regola CANDIDATA per le estremità PARTIAL, valutata a posteriori sull'uscita di R4.
 *
 * Candidata: un'estremità PARTIAL (mai UNCERTAIN) con 0 < gap ≤ 3 σ_misura ed evidenza terminale sufficiente (criterio R3.1: ≥ 3 viste
 * e σ_terminale ≤ σ_misura) può contare come angolo sufficientemente osservato per la chiusura; il gap resta registrato e entra nel χ²
 * come termine gap / σ_misura (senza σ terminale né σ dell'intersezione). Tutte le altre condizioni di CLOSED restano quelle di R4.
 *
 * Con SAGOMA_R4PARTIAL_OUT=<cartella> scrive `r4-partial-rule-audit.json` e `.csv`; con SAGOMA_R4PARTIAL_ZIPS=<zip1;zip2> i dati reali.
 */
class R4PartialRuleAuditTest {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else "%.${d}f".format(Locale.ROOT, v)

    // ------------------------------------------------------------------------------------------------- regola candidata

    /** Evidenza terminale sufficiente (R3.1). */
    private fun terminalOk(e: WallEnd): Boolean = e.evidence?.strong ?: false

    private fun qualifies(s: EndSupport, e: WallEnd): Boolean =
        s.endpointState == EndState.PARTIAL && s.gapToIntersectionM > 0 && s.gapToIntersectionM <= 3 * s.measurementSigmaM && terminalOk(e)

    /** Esito della candidata su un perimetro: (chiusura ammessa, motivo). */
    private fun candidateClosed(per: Perimeter, walls: Map<Int, EstimatedWall>): Pair<Boolean, String> {
        val k = per.closure ?: return false to "catena aperta (nessun anello): la candidata non crea lati"
        var extraChi2 = 0.0; var extraTerms = 0
        val blocking = mutableListOf<String>()
        for (l in per.links.filter { it.kind == LinkKind.CORNER && it.support == LinkSupport.INFERRED }) {
            for ((s, e, name) in listOf(Triple(l.fromEnd, walls.getValue(l.fromWall).end, "fine W${l.fromWall}"), Triple(l.toEnd, walls.getValue(l.toWall).start, "inizio W${l.toWall}"))) {
                if (s == null || !s.inferredCorner) continue
                if (qualifies(s, e)) { extraChi2 += (s.gapToIntersectionM / s.measurementSigmaM).let { it * it }; extraTerms++ }
                else blocking.add("$name ${s.endpointState}/${s.endpointReason} gap ${f(s.gapToIntersectionM * 100, 1)} cm = ${f(s.gapToIntersectionM / s.measurementSigmaM, 2)} σ_misura" +
                    (if (!terminalOk(e)) ", evidenza terminale insufficiente" else ""))
            }
        }
        if (blocking.isNotEmpty()) return false to "angoli non osservati: " + blocking.joinToString("; ")
        // Altre condizioni di CLOSED di R4 (oltre agli angoli dedotti) devono già essere verificate.
        val other = per.reasons.filter { !it.startsWith("angolo W") && !it.startsWith("errore di chiusura") && !it.startsWith("tutte le condizioni") }
        if (other.isNotEmpty()) return false to "altre condizioni R4 non verificate: " + other.joinToString("; ")
        var dof = k.dof + extraTerms; if (dof % 2 == 1) dof++
        val p = PerimeterSolver.chi2SurvivalEven(k.chi2 + extraChi2, dof)
        return if (p >= PerimeterParams().closureMinP) true to "chiusura ammessa: $extraTerms gap entro 3σ di misura nel χ² (p = ${f(p, 4)}), gap registrati ${f(k.unobservedM * 100, 1)} cm"
        else false to "χ² con i gap NON compatibile (p = ${f(p, 4)})"
    }

    // ---------------------------------------------------------------------------------------------------------- scenari

    private fun ev(measurement: Double, terminal: Double, groups: Int = 10) = EndEvidence(1.0, 1.0, 1.0, 1.0, terminal * sqrt(12.0), 1.0, null, groups, measurement, terminal, null, null, null)

    private fun wall(id: Int, ax: Double, az: Double, bx: Double, bz: Double, startEnd: WallEnd? = null, endEnd: WallEnd? = null, sPos: Double = 0.005, sThDeg: Double = 0.3): EstimatedWall {
        val l = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az)); val ux = (bx - ax) / l; val uz = (bz - az) / l
        val cx = (ax + bx) / 2; val cz = (az + bz) / 2
        val g = WallGeometry(uz, -ux, uz * cx - ux * cz, cx, cz, ux, uz, -l / 2, l / 2, 0.0, 2.4, 0.0, 2.4, Math.toDegrees(atan2(uz, ux)), listOf(WallSpan(-l / 2, l / 2)))
        val ok = ev(0.016, 0.0)
        val s = startEnd?.copy(u = -l / 2) ?: WallEnd(-l / 2, EndState.OBSERVED, EndReason.CORNER, 0.016, ok)
        val e = endEnd?.copy(u = l / 2) ?: WallEnd(l / 2, EndState.OBSERVED, EndReason.CORNER, 0.016, ok)
        return EstimatedWall(
            id, listOf(id), g, WallUncertainty(sPos, sThDeg, s.sigmaM, e.sigmaM, sqrt(s.sigmaM * s.sigmaM + e.sigmaM * e.sigmaM), 0.0, 0.0, sPos, 0.02),
            WallEvidence(1000, 900, 10, 10, 60.0, 2.0, 0.01, 0.005, 1.0, 0.0, EvidenceQuality.HIGH, emptyList()),
            s, e, emptyList(), WallThickness(ThicknessState.UNKNOWN, note = ""), 0.8, emptyList(), emptyList(),
        )
    }

    private val cams = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 1.5), doubleArrayOf(3.0, 2.0), doubleArrayOf(1.5, 2.2), doubleArrayOf(2.8, 0.8))
    private fun solve(ws: List<EstimatedWall>) = PerimeterSolver.solve(PerimeterInput(ws, { _, _, _ -> 0 }, 0.10, cams))

    private class Sc(val id: String, val desc: String, val gap: Double, val meas: Double, val term: Double, val state: EndState, val reason: EndReason, val groups: Int, val truthObserved: Boolean)

    private fun end(sc: Sc): WallEnd = WallEnd(0.0, sc.state, sc.reason, sqrt(sc.meas * sc.meas + sc.term * sc.term), ev(sc.meas, sc.term, sc.groups))

    /** [truthObserved]: per costruzione il muro arriva davvero all'angolo e manca solo la perdita fisiologica di pochi centimetri (caso A). */
    private val scenarios = listOf(
        Sc("1", "OBSERVED, gap 1 cm", 0.01, 0.016, 0.0, EndState.OBSERVED, EndReason.CORNER, 10, true),
        Sc("2", "PARTIAL, gap 1 cm, σ misura 1,6 cm", 0.01, 0.016, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, true),
        Sc("3", "PARTIAL, gap 2 cm, σ misura 1,6 cm", 0.02, 0.016, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, true),
        Sc("4", "PARTIAL, gap 4 cm, σ misura 1,6 cm", 0.04, 0.016, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, true),
        Sc("5", "PARTIAL, gap 8 cm, σ misura 1,6 cm", 0.08, 0.016, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, false),
        Sc("6", "PARTIAL, gap 8 cm, σ misura 7,9 cm", 0.08, 0.079, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, false),
        Sc("7", "PARTIAL, gap 20 cm, σ misura 7,9 cm", 0.20, 0.079, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, false),
        Sc("8", "PARTIAL, gap 20 cm, σ terminale 7,7 cm, σ misura 1,6 cm", 0.20, 0.016, 0.077, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, false),
    )

    /** Quadrato 4 × 3 ideale: varia l'inizio di W2 (angolo (0, 3), partner W1). */
    private fun idealRow(sc: Sc): List<Any> {
        val sq = listOf(wall(1, 0.0, 0.0, 0.0, 3.0), wall(2, sc.gap, 3.0, 4.0, 3.0, startEnd = end(sc)), wall(3, 4.0, 3.0, 4.0, 0.0), wall(4, 4.0, 0.0, 0.0, 0.0))
        return row("quadrato ideale", sc, sq, solve(sq), 1, 2, true)
    }

    private fun row(base: String, sc: Sc, ws: List<EstimatedWall>, r: PerimeterResult, from: Int, to: Int, targetIsTo: Boolean): List<Any> {
        val l = r.candidates.first { it.fromWall == from && it.toWall == to }
        val s = (if (targetIsTo) l.toEnd else l.fromEnd)!!
        val e = ws.first { it.id == (if (targetIsTo) to else from) }.let { if (targetIsTo) it.start else it.end }
        val sComb = if (targetIsTo) l.extensionToSigmaM else l.extensionFromSigmaM
        val main = r.main
        val (cand, why) = if (main == null) (false to "nessun perimetro") else candidateClosed(main, ws.associateBy { it.id })
        val q = qualifies(s, e)
        val verdict = when {
            cand && !sc.truthObserved -> "FALSA CHIUSURA con la candidata"
            cand -> "chiusura recuperata (corretta)"
            sc.truthObserved && main?.closure != null -> "non recuperata"
            else -> "prudente"
        }
        return listOf(
            base, sc.id, sc.desc, f(s.gapToIntersectionM, 3), f(s.measurementSigmaM, 4), f(s.terminalSigmaM, 4), f(s.intersectionSigmaM, 4),
            f(s.gapToIntersectionM / s.measurementSigmaM, 2), f(s.gapToIntersectionM / sComb, 2),
            "viste ${e.evidence?.groupsNearEnd} · " + (if (terminalOk(e)) "sufficiente" else "insufficiente"),
            "${r.state}/${l.support}", if (q) "estremità ammessa" else "estremità NON ammessa", if (cand) "SÌ" else "no", why,
            if (sc.truthObserved) "A: perdita fisiologica" else "B: angolo non osservato", verdict,
        )
    }

    private fun idealExtra(): List<List<Any>> {
        val out = mutableListOf<List<Any>>()
        // 9: gap spiegabile solo dalla σ dell'intersezione (linee a 12°, estremità PARTIAL a 30 cm, σ misura 3 cm).
        run {
            val sc = Sc("9", "PARTIAL, gap 30 cm spiegabile solo dalla σ dell'intersezione (linee a 12°)", 0.30, 0.03, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, false)
            val s12 = Math.sin(Math.toRadians(12.0)); val c12 = Math.cos(Math.toRadians(12.0))
            val a = wall(1, 0.0, 0.0, 0.0, 3.0, endEnd = WallEnd(0.0, EndState.OBSERVED, EndReason.CORNER, 0.03, ev(0.03, 0.0)), sPos = 0.02, sThDeg = 1.5)
            val bx = 0.30 * s12; val bz = 3.30 + 0.30 * c12
            val b = wall(2, bx, bz, bx + 2 * s12, bz + 2 * c12, startEnd = end(sc), sPos = 0.02, sThDeg = 1.5)
            val ws = listOf(a, b)
            out.add(row("due pareti a 12°", sc, ws, PerimeterSolver.solve(PerimeterInput(ws, { _, _, _ -> 0 }, 0.10, listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.0, 4.0)))), 1, 2, true))
        }
        // 10: due linee che si intersecano, entrambe le estremità PARTIAL a 2 cm, ma SENZA evidenza terminale (2 viste, terminale > misura).
        run {
            val sc = Sc("10", "due PARTIAL a 2 cm senza evidenza terminale (2 viste, σ terminale > σ misura)", 0.02, 0.016, 0.03, EndState.PARTIAL, EndReason.SURFACE_NEAR, 2, false)
            val ws = listOf(
                wall(1, 0.0, 0.0, 0.0, 2.98, endEnd = end(sc)), wall(2, 0.02, 3.0, 4.0, 3.0, startEnd = end(sc)),
                wall(3, 4.0, 3.0, 4.0, 0.0), wall(4, 4.0, 0.0, 0.0, 0.0),
            )
            out.add(row("quadrato ideale", sc, ws, solve(ws), 1, 2, true))
            // 10b: stesse estremità CON evidenza terminale sufficiente (10 viste, terminale 0): l'unica differenza è l'evidenza.
            val sb = Sc("10b", "due PARTIAL a 2 cm CON evidenza terminale (controllo)", 0.02, 0.016, 0.0, EndState.PARTIAL, EndReason.SURFACE_NEAR, 10, true)
            val wb = listOf(
                wall(1, 0.0, 0.0, 0.0, 2.98, endEnd = end(sb)), wall(2, 0.02, 3.0, 4.0, 3.0, startEnd = end(sb)),
                wall(3, 4.0, 3.0, 4.0, 0.0), wall(4, 4.0, 0.0, 0.0, 0.0),
            )
            out.add(row("quadrato ideale", sb, wb, solve(wb), 1, 2, true))
        }
        return out
    }

    /** Stanza sintetica R1→R4 (R3.1 attuale): l'inizio della parete z = 3 viene spostato come nello scenario; le altre estremità restano reali. */
    private fun syntheticRows(): List<List<Any>> {
        val (rec, blobs) = SyntheticRoom(noiseM = 0.01).recording()
        val map = GlobalMap.build(rec, { blobs[it] })
        val s = SurfaceExtractor.extract(map, rec)
        val w = WallEstimator.estimate(WallEstimator.inputFrom(map, s, rec))
        val target = w.walls.first { abs(it.geometry.nz) > 0.9 && abs(it.geometry.cz - 3) < 0.05 }
        val partner = w.walls.first { abs(it.geometry.nx) > 0.9 && abs(it.geometry.cx) < 0.05 }.id
        val start = abs(target.geometry.pointAt(target.start.u)[0]) < abs(target.geometry.pointAt(target.end.u)[0])
        fun solveWith(ws: List<EstimatedWall>) = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, WallEstimationResult(w.inputVertical, w.inputStructural, ws, w.excluded, w.merges, w.noise, w.temporalFallbackM, w.diagnostics)))
        val rows = mutableListOf<List<Any>>()
        rows.add(row("sintetico R1→R4 (attuale)", Sc("0", "uscita R3.1 attuale, senza modifiche", 0.0, 0.0, 0.0, target.start.state, target.start.reason, 0, false), w.walls, solveWith(w.walls), partner, target.id, true))
        for (sc in scenarios) {
            val g = target.geometry
            val u = (sc.gap - g.cx) * g.ux + (3.0 - g.cz) * g.uz // punto (gap, 3) sulla linea (x cresce lungo la parete dall'angolo)
            val spans = g.observedSpans.toMutableList().also { it[0] = WallSpan(u, it[0].toU) }
            val e = end(sc).copy(u = u)
            val moved = target.copy(geometry = g.copy(startU = u, observedSpans = spans), start = e, uncertainty = target.uncertainty.copy(startSigmaM = e.sigmaM))
            check(start)
            val ws = w.walls.map { if (it.id == target.id) moved else it }
            rows.add(row("sintetico R1→R4", sc, ws, solveWith(ws), partner, target.id, true))
        }
        return rows
    }

    private val header = "base;scenario;descrizione;gapM;sigmaMeasurementM;sigmaTerminalM;sigmaIntersectionM;gapOverSigmaMeasurement;gapOverSigmaCombined;terminalEvidence;currentR4;candidateRule;closedAllowed;motivo;verita;verdetto"

    // ------------------------------------------------------------------------------------------------------------ dati reali

    private fun realRows(zip: File): List<List<Any>> {
        val ds = CaptureDatasetFiles.open(zip)
        val map = GlobalMap.build(ds.recording, { ds.read(it) })
        val s = SurfaceExtractor.extract(map, ds.recording)
        val w = WallEstimator.estimate(WallEstimator.inputFrom(map, s, ds.recording))
        val r = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, w))
        val byId = w.walls.associateBy { it.id }
        val out = mutableListOf<List<Any>>()
        for (l in r.candidates.filter { it.kind == LinkKind.CORNER && it.cornerX != null }.sortedWith(compareBy({ it.fromWall }, { it.toWall }))) {
            for ((sup, e, name, sComb) in listOf(Quad(l.fromEnd, byId.getValue(l.fromWall).end, "fine W${l.fromWall}", l.extensionFromSigmaM), Quad(l.toEnd, byId.getValue(l.toWall).start, "inizio W${l.toWall}", l.extensionToSigmaM))) {
                if (sup == null || sup.endpointState != EndState.PARTIAL) continue
                val gap = sup.gapToIntersectionM
                val sEnd = e.sigmaM
                val cls = when {
                    gap <= 0 -> "dati oltre l'angolo (già osservato)"
                    gap <= 3 * sup.measurementSigmaM -> if (terminalOk(e)) "PARTIAL + gap ≤ 3σ misura + evidenza terminale" else "gap ≤ 3σ misura ma evidenza terminale insufficiente"
                    gap <= 3 * sEnd -> "compatibile solo grazie a σ terminale"
                    gap <= 3 * sComb -> "compatibile solo grazie a σ intersezione"
                    else -> "non compatibile (> 3σ propagata)"
                }
                out.add(listOf(
                    zip.nameWithoutExtension, "W${l.fromWall}→W${l.toWall}", l.decision, l.support, name, "${sup.endpointState}/${sup.endpointReason}",
                    f(gap, 4), f(sup.measurementSigmaM, 4), f(sup.terminalSigmaM, 4), f(sup.intersectionSigmaM, 4), f(gap / sup.measurementSigmaM, 2), f(gap / sComb, 2),
                    "viste ${e.evidence?.groupsNearEnd} · densità 10 cm ${f(e.evidence?.density10, 2)} · " + (if (terminalOk(e)) "sufficiente" else "insufficiente"),
                    if (sup.inferredCorner) "angolo dedotto" else "angolo osservato", if (qualifies(sup, e)) "ammessa" else "NON ammessa", cls,
                ))
            }
        }
        // Effetto sui perimetri reali.
        for (per in r.perimeters.filter { it.links.isNotEmpty() }) {
            val (c, why) = candidateClosed(per, byId)
            out.add(listOf(zip.nameWithoutExtension, "P${per.id}", per.state, "", "", per.wallIds.joinToString("→") { "W$it" }, "", "", "", "", "", "", "", "attuale ${per.state}", if (c) "CLOSED con la candidata" else "invariato", why))
        }
        return out
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private val realHeader = "dataset;collegamento;decisione;supporto;estremita;stato;gapM;sigmaMeasurementM;sigmaTerminalM;sigmaIntersectionM;gapOverSigmaMeasurement;gapOverSigmaCombined;evidenzaTerminale;attuale;candidata;classe"

    // ---------------------------------------------------------------------------------------------------------------- test

    private fun all(): List<List<Any>> = scenarios.map { idealRow(it) } + idealExtra() + syntheticRows()

    @Test
    fun `audit della regola candidata PARTIAL deterministico`() {
        assertEquals(all().toString(), all().toString())
    }

    @Test
    fun `report audit regola PARTIAL (solo con SAGOMA_R4PARTIAL_OUT)`() {
        val out = System.getenv("SAGOMA_R4PARTIAL_OUT") ?: return
        val sc = all()
        val real = System.getenv("SAGOMA_R4PARTIAL_ZIPS")?.split(';')?.filter { it.isNotBlank() }?.flatMap { realRows(File(it)) } ?: emptyList()
        fun line(v: List<Any>) = v.joinToString(";") { it.toString().replace(";", ",") }
        val dir = File(out).also { it.mkdirs() }
        File(dir, "r4-partial-rule-audit.csv").writeText("# scenari\n$header\n" + sc.joinToString("\n") { line(it) } + "\n\n# dati reali\n$realHeader\n" + real.joinToString("\n") { line(it) } + "\n")
        fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        fun obj(h: String, v: List<Any>) = "{" + h.split(';').mapIndexed { i, k -> "${js(k)}:${js(v.getOrElse(i) { "" }.toString())}" }.joinToString(",") + "}"
        File(dir, "r4-partial-rule-audit.json").writeText(
            "{\n\"note\":" + js("SOLO AUDIT: regola candidata PARTIAL valutata a posteriori sull'R4 attuale, nessuna modifica di produzione.") +
                ",\n\"scenarios\":[\n" + sc.joinToString(",\n") { obj(header, it) } + "\n],\n\"real\":[\n" + real.joinToString(",\n") { obj(realHeader, it) } + "\n]}\n",
        )
    }
}
