package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.Surface
import com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AUDIT DIAGNOSTICO (solo test, nessuna modifica di produzione): perché il fronte di un mobile diventa VERTICAL_STRUCTURAL.
 * Per ogni superficie verticale: composizione vera, metriche di regione (planarità, normali, crescita, fusione), termini del punteggio
 * R2 (replica esatta), controfattuali un termine alla volta, soglia, e il meccanismo della contaminazione:
 *   A = punti del mobile entrati in una regione di PARETE durante la crescita;
 *   B = il mobile è una regione/superficie sua, classificata STRUCTURAL;
 *   C = una regione di MOBILE fusa (dopo la crescita) in una superficie di parete.
 * Con SAGOMA_FURNITURE_OUT=<cartella> esegue scenari, casi minimi e sweep di distanza e scrive CSV, JSON, SVG.
 */
class FurnitureAuditTest {
    private fun f(v: Double?, d: Int = 4) = if (v == null || v.isNaN()) "" else "%.${d}f".format(Locale.ROOT, v)

    // ------------------------------------------------------------------------------------------------- analisi di un'esecuzione

    class SurfRow(val cols: List<Any?>, val furnitureInStructural: Int, val mech: Map<String, Int>)

    private val st = SurfaceKind.VERTICAL_STRUCTURAL
    private val weights = mapOf("altezza" to 0.30, "base" to 0.15, "FREE_SPACE" to 0.25, "esterna (distanza parallela)" to 0.20, "lunghezza" to 0.05, "ARCore" to 0.05)

    private fun isFurniture(l: Int) = Label.isBox(l) || Label.isPanel(l)

    /** Istogramma delle etichette dei punti di un insieme di voxel. */
    private fun labels(run: BenchRun, voxels: Collection<Int>): Map<Int, Int> {
        val h = HashMap<Int, Int>(); val vx = run.map.voxels
        for (v in voxels) for (t in vx.start[v] until vx.start[v] + vx.count[v]) h.merge(run.pointLabels[vx.order[t]], 1, Int::plus)
        return h
    }
    private fun majority(h: Map<Int, Int>) = h.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key ?: Label.NONE

    /** La superficie parallela "più esterna dietro" che R2 usa per il termine "esterna" (replica di Surfaces.classifyVertical). */
    private fun behind(s: Surface, verticals: List<Surface>): Surface? = verticals.firstOrNull { o ->
        o !== s && o.id != s.id && o.plane.angleTo(s.plane) <= 10 &&
            (o.plane.centroid[0] - s.plane.centroid[0]) * s.plane.nx + (o.plane.centroid[2] - s.plane.centroid[2]) * s.plane.nz in -1.5..-0.05 &&
            overlap(s, o) >= 0.3 * s.lengthM && o.yMax >= s.yMax - 0.1
    }
    private fun overlap(a: Surface, b: Surface): Double {
        fun proj(s: Surface, t: Double): Double { val px = s.plane.centroid[0] + s.u[0] * t; val pz = s.plane.centroid[2] + s.u[2] * t; return (px - a.plane.centroid[0]) * a.u[0] + (pz - a.plane.centroid[2]) * a.u[2] }
        val b0 = proj(b, b.uMin); val b1 = proj(b, b.uMax)
        return max(0.0, min(a.uMax, max(b0, b1)) - max(a.uMin, min(b0, b1)))
    }

    /** Righe per tutte le superfici verticali di un'esecuzione, con controfattuali e meccanismi della contaminazione. */
    private fun analyze(tag: String, run: BenchRun, cf: MutableList<String>): List<SurfRow> {
        val s = run.surfaces; val t = run.trace; val vx = run.map.voxels
        // Etichetta di maggioranza di ogni regione grezza (prima della fusione).
        val regVoxels = HashMap<Int, MutableList<Int>>()
        for (v in 0 until vx.size) if (t.region[v] >= 0) regVoxels.getOrPut(t.region[v]) { mutableListOf() }.add(v)
        val regMaj = regVoxels.mapValues { majority(labels(run, it.value)) }
        val verticals = s.surfaces.filter { it.orientation == Orientation.VERTICAL }
        return verticals.map { sf ->
            val h = labels(run, sf.memberVoxels.toList())
            val tot = h.values.sum().coerceAtLeast(1)
            val maj = majority(h)
            val wallPts = h.filterKeys { Label.isWall(it) }.values.sum(); val furnPts = h.filterKeys { isFurniture(it) }.values.sum()
            // Metriche di regione: planarità (variazione dei vicinati), deviazione delle normali dal piano, RMS, regioni fuse.
            val ns = sf.memberVoxels.toList().mapNotNull { v -> t.normals[v] }
            val variation = if (ns.isEmpty()) Double.NaN else ns.map { it.surfaceVariation }.average()
            val normalDev = if (ns.isEmpty()) Double.NaN else ns.map { it.angleTo(sf.plane) }.average()
            val rawRegions = sf.memberVoxels.map { t.region[it] }.filter { it >= 0 }.distinct()
            val furnRegions = rawRegions.count { isFurniture(regMaj[it] ?: 0) }
            // Termini del punteggio (replica esatta) e controfattuali.
            val d = FreeSpaceAudit.detail(run.map, sf)
            val sc = FreeSpaceAudit.score(s, sf, d.freeBehind)
            val terms = mapOf(
                "altezza" to 0.30 * sc.height, "base" to 0.15 * sc.bottom, "FREE_SPACE" to 0.25 * sc.freeTerm,
                "esterna (distanza parallela)" to 0.20 * sc.outermost, "lunghezza" to 0.05 * sc.length, "ARCore" to 0.05 * sc.arcore,
            )
            fun kindOf(score: Double) = when { sc.weak -> SurfaceKind.UNKNOWN; score >= 0.6 -> st; score < 0.5 -> SurfaceKind.VERTICAL_OBJECT; else -> SurfaceKind.UNKNOWN }
            val bh = behind(sf, verticals)
            for ((name, c) in terms) {
                val zero = sc.score - c; val renorm = zero / (1 - weights.getValue(name))
                cf.add(listOf(tag, "S${sf.id}", Label.cls(maj) + if (Label.isWall(maj)) " GT${Label.wallId(maj)}" else "", sc.kind, f(sc.score), name, f(c), f(zero), kindOf(zero), f(renorm), kindOf(renorm)).joinToString(";"))
            }
            cf.add(listOf(tag, "S${sf.id}", Label.cls(maj), sc.kind, f(sc.score), "soglia (G)", "", "", "", "", "UNKNOWN se soglia STRUCTURAL > ${f(sc.score)}; OBJECT se soglia OBJECT > ${f(sc.score)}").joinToString(";"))
            // Meccanismo dei punti del mobile finiti in questa superficie (se strutturale).
            val mech = HashMap<String, Int>()
            if (sf.kind == st) for (v in sf.memberVoxels) for (k in vx.start[v] until vx.start[v] + vx.count[v]) {
                val l = run.pointLabels[vx.order[k]]; if (!isFurniture(l)) continue
                val r = t.region[v]; val rm = regMaj[r] ?: 0
                val m = when {
                    Label.isWall(rm) -> if (labels(run, listOf(v)).keys.any { Label.isWall(it) }) "A (crescita, voxel misto parete/mobile)" else "A (crescita in una regione di parete)"
                    isFurniture(rm) && isFurniture(maj) -> "B (superficie del mobile classificata STRUCTURAL)"
                    isFurniture(rm) -> "C (regione del mobile fusa in una superficie di parete)"
                    else -> "altro"
                }
                mech.merge(m, 1, Int::plus)
            }
            SurfRow(listOf(
                tag, "S${sf.id}", sf.kind, Label.cls(maj) + if (Label.isWall(maj)) " GT${Label.wallId(maj)}" else "", tot, wallPts, furnPts, f(1.0 * h.getOrDefault(maj, 0) / tot, 3),
                f(sf.areaM2, 2), f(sf.topAboveFloor, 2), f(sf.bottomAboveFloor, 2), f(sf.lengthM, 2), sf.voxels, rawRegions.size, furnRegions,
                f(variation, 4), f(normalDev, 2), f(sf.rmsM, 4),
                f(sc.height, 3), f(sc.bottom, 3), f(d.freeBehind, 3), f(sc.outermost, 0), bh?.let { "S${it.id} a ${f(-((it.plane.centroid[0] - sf.plane.centroid[0]) * sf.plane.nx + (it.plane.centroid[2] - sf.plane.centroid[2]) * sf.plane.nz), 2)} m" } ?: "-",
                f(sc.length, 3), f(sc.arcore, 0), f(sc.score), sc.kind, sf.kind == sc.kind,
                mech.entries.sortedByDescending { it.value }.joinToString(" | ") { "${it.key}=${it.value}" },
            ), if (sf.kind == st) furnPts else 0, mech)
        }
    }

    private val scoreHeader = "esecuzione;superficie;classe;verita(maggioranza);punti;puntiParete;puntiMobile;purezza;areaM2;topDalPav;baseDalPav;lunghezzaM;voxel;regioniGrezze;regioniDiMobile;" +
        "planarita(variazione media);deviazioneNormaliDeg;rmsM;termAltezza;termBase;freeBehind;termEsterna;parallelaDietro;termLunghezza;termARCore;punteggio;classeReplica;replicaUguale;meccanismoContaminazione"
    private val cfHeader = "esecuzione;superficie;verita;classe;punteggio;termineRimosso;contributo;punteggioSenza;classeSenza;punteggioRinormalizzato;classeRinormalizzata"

    // -------------------------------------------------------------------------------------------- casi minimi e sweep

    /** Parete z = 3 (GT1) della stanza 4×3 con un pannello parallelo (fronte di mobile) davanti a [gap] metri. */
    internal fun panelRoom(id: String, gap: Double, width: Double = 1.6, height: Double = 1.4) =
        GtRoom(id, Scenarios.rect(), panels = listOf(GtPanel(0, "fronte di mobile a ${f(gap * 100, 1)} cm", 2.0 - width / 2, 3.0 - gap, 2.0 + width / 2, 3.0 - gap, 0.0, height)))

    internal fun minimal(sensor: SensorParams): List<Scenario> = listOf(
        Scenario("M0", "parete singola pulita", GtRoom("M0", Scenarios.rect()), Scenarios.ring, sensor, ""),
        Scenario("M1", "pannello parallelo a 10 cm", panelRoom("M1", 0.10), Scenarios.ring, sensor, ""),
        Scenario("M2", "pannello parallelo a 20 cm", panelRoom("M2", 0.20), Scenarios.ring, sensor, ""),
        Scenario("M3", "pannello parallelo a 40 cm", panelRoom("M3", 0.40), Scenarios.ring, sensor, ""),
        Scenario("M4", "pannello aderente (0,5 cm)", panelRoom("M4", 0.005), Scenarios.ring, sensor, ""),
        Scenario("M5", "mobile che copre metà parete", GtRoom("M5", Scenarios.rect(), boxes = listOf(GtBox(0, "mobile metà parete", 0.0, 0.0, 2.45, 2.0, 1.9, 2.99))), Scenarios.ring, sensor, ""),
        Scenario("M6", "mobile nell'angolo", GtRoom("M6", Scenarios.rect(), boxes = listOf(GtBox(0, "mobile nell'angolo", 0.01, 0.0, 2.45, 0.9, 1.9, 2.99))), Scenarios.ring, sensor, ""),
        Scenario("M7", "pannello isolato senza parete dietro", GtRoom("M7", Scenarios.rect(), panels = listOf(GtPanel(0, "pannello isolato", 1.2, 1.6, 2.8, 1.6, 0.0, 1.4))), Scenarios.ring, sensor, ""),
    )

    /** Riepilogo di un'esecuzione: recall delle pareti, contaminazione, falsi strutturali, falsi oggetto, meccanismi. */
    private fun summary(tag: String, run: BenchRun, rows: List<SurfRow>): String {
        val m = BenchEval.evaluate(run)
        val furnTotal = (0 until run.map.points.size).count { isFurniture(run.pointLabels[it]) }
        val furnStruct = rows.sumOf { it.furnitureInStructural }
        val mech = HashMap<String, Int>(); rows.forEach { r -> r.mech.forEach { (k, v) -> mech.merge(k, v, Int::plus) } }
        val falseStruct = rows.count { it.cols[2] == st && !it.cols[3].toString().startsWith("WALL") }
        val falseObject = rows.count { it.cols[2] != st && it.cols[3].toString().startsWith("WALL") && (it.cols[4] as Int) > 2000 }
        return listOf(
            tag, run.params.variant.id, f(m.r2.recall, 3), f(m.r2.contaminationRate, 4), furnTotal, furnStruct, f(if (furnTotal == 0) 0.0 else furnStruct.toDouble() / furnTotal, 3),
            falseStruct, falseObject, m.r2.falseStructuralSurfaces, f(m.global.wallRecall, 3), m.r3.falseWalls.size,
            mech.entries.sortedByDescending { it.value }.joinToString(" | ") { "${it.key}=${it.value}" },
        ).joinToString(";") { it.toString().replace(";", ",") }
    }
    private val sumHeader = "esecuzione;variante;recallStrutturaleR2;contaminazione;puntiMobile;puntiMobileInStrutturali;frazioneMobileStrutturale;superficiFalseStrutturali;pareteFalsaOggetto;falseStrutturaliBenchmark;recallPareti;muriFalsiR3;meccanismi"

    // ------------------------------------------------------------------------------------------------------------ test

    @Test
    fun `replica del punteggio e meccanismi coerenti su S5`() {
        val run = BenchPipeline.run(Scenarios.byId("S5"))
        val rows = analyze("S5", run, mutableListOf())
        assertTrue(rows.all { it.cols[27] == true }, "classe replicata = classe di produzione")
        // Ogni punto di mobile in una superficie strutturale ha un solo meccanismo.
        for (r in rows) assertEquals(r.furnitureInStructural, r.mech.values.sum())
        assertEquals(0, run.trace.mismatches)
    }

    @Test
    fun `audit completo della contaminazione da mobili (solo con SAGOMA_FURNITURE_OUT)`() {
        val out = File(System.getenv("SAGOMA_FURNITURE_OUT") ?: return).also { it.mkdirs() }
        val scoreRows = mutableListOf<String>(); val cfRows = mutableListOf<String>(); val cmp = mutableListOf<String>(); val sweep = mutableListOf<String>()
        val variants = listOf(R2Variants.BASELINE, R2Variants.N12)
        val noisy = Scenarios.byId("S1").sensor
        // Scenari del benchmark (invariati) e casi minimi.
        for (sc in listOf("S5", "S8", "S9", "S10", "S17").map { Scenarios.byId(it) } + minimal(noisy)) for (v in variants) {
            val run = BenchPipeline.run(sc, BenchParams(variant = v))
            assertEquals(0, run.trace.mismatches)
            val tag = "${sc.id} ${v.id}"
            val rows = analyze(tag, run, cfRows)
            scoreRows.addAll(rows.map { r -> r.cols.joinToString(";") { it.toString().replace(";", ",") } })
            cmp.add(summary(tag, run, rows))
        }
        // Sweep della distanza parete–fronte del mobile (pannello 1,6 × 1,4 m davanti a GT1), 3 semi, vicinato 20/12 cm.
        for (gap in listOf(0.005, 0.05, 0.10, 0.20, 0.30, 0.40, 0.60)) for (v in variants) for (seed in 1L..3L) {
            val sc = Scenario("D${f(gap * 100, 1)}", "fronte a ${f(gap * 100, 1)} cm", panelRoom("D", gap), Scenarios.ring, noisy.copy(seed = seed), "")
            val run = BenchPipeline.run(sc, BenchParams(variant = v))
            val rows = analyze("sweep", run, mutableListOf())
            val panelPts = (0 until run.map.points.size).count { Label.isPanel(run.pointLabels[it]) }
            val panelStruct = rows.sumOf { it.furnitureInStructural }
            val panelSurf = rows.filter { it.cols[3] == "PANEL" }.maxByOrNull { it.cols[4] as Int }
            val mech = HashMap<String, Int>(); rows.forEach { r -> r.mech.forEach { (k, x) -> mech.merge(k, x, Int::plus) } }
            val m = BenchEval.evaluate(run)
            sweep.add(listOf(f(gap * 100, 1), v.id, seed, panelPts, panelStruct, f(if (panelPts == 0) 0.0 else panelStruct.toDouble() / panelPts, 3),
                panelSurf?.let { "${it.cols[1]} ${it.cols[2]} punteggio ${it.cols[25]} esterna ${it.cols[21]} free ${it.cols[20]} altezza ${it.cols[18]}" } ?: "nessuna superficie con maggioranza pannello",
                f(m.r2.perWall[1]?.let { (it["TP"] ?: 0).toDouble() / it.values.sum() }, 3), mech.entries.sortedByDescending { it.value }.joinToString(" | ") { "${it.key}=${it.value}" },
            ).joinToString(";") { it.toString().replace(";", ",") })
        }
        File(out, "furniture-scores.csv").writeText("$scoreHeader\n" + scoreRows.joinToString("\n") + "\n")
        File(out, "counterfactuals.csv").writeText("$cfHeader\n" + cfRows.joinToString("\n") + "\n")
        File(out, "scenario-comparison.csv").writeText("$sumHeader\n" + cmp.joinToString("\n") + "\n")
        File(out, "distance-sweep.csv").writeText("distanzaCm;variante;seme;puntiPannello;puntiPannelloInStrutturali;frazione;superficieDelPannello;recallParete GT1;meccanismi\n" + sweep.joinToString("\n") + "\n")
        fun arr(h: String, rows: List<String>) = rows.joinToString(",\n", "[", "]") { r -> val k = h.split(';'); val v = r.split(';'); "{" + k.indices.joinToString(",") { "\"${k[it]}\":\"${v.getOrElse(it) { "" }.replace("\\", "\\\\").replace("\"", "'")}\"" } + "}" }
        File(out, "furniture-audit.json").writeText(
            "{\n\"note\":\"AUDIT DIAGNOSTICO contaminazione da mobili: nessuna modifica di produzione.\",\n\"scenarioComparison\":" + arr(sumHeader, cmp) + ",\n\"distanceSweep\":" +
                arr("distanzaCm;variante;seme;puntiPannello;puntiPannelloInStrutturali;frazione;superficieDelPannello;recallParete GT1;meccanismi", sweep) + ",\n\"surfaces\":" + arr(scoreHeader, scoreRows) +
                ",\n\"counterfactuals\":" + arr(cfHeader, cfRows) + "\n}\n",
        )
        File(out, "distance-sweep.svg").writeText(sweepSvg(sweep))
    }

    private fun sweepSvg(rows: List<String>): String {
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="760" height="440" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="14" font-weight="bold">Fronte di mobile davanti alla parete: frazione dei suoi punti finiti in superfici STRUCTURAL</text>""")
        val x0 = 70.0; val y0 = 380.0; val sx = 600 / 60.0; val sy = 320.0
        sb.append("""<line x1="$x0" y1="$y0" x2="${x0 + 600}" y2="$y0" stroke="#000"/><line x1="$x0" y1="$y0" x2="$x0" y2="${y0 - 320}" stroke="#000"/>""")
        for (c in listOf(0, 5, 10, 20, 30, 40, 60)) sb.append("""<text x="${f(x0 + c * sx - 6, 1)}" y="${y0 + 16}">$c cm</text>""")
        for (p in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) sb.append("""<text x="25" y="${f(y0 - p * sy + 4, 1)}">${(p * 100).toInt()}%</text><line x1="$x0" y1="${f(y0 - p * sy, 1)}" x2="${x0 + 600}" y2="${f(y0 - p * sy, 1)}" stroke="#EEE"/>""")
        for ((v, col) in listOf("baseline-20cm" to "#1565C0", "exp-12cm" to "#C62828")) {
            val pts = rows.map { it.split(';') }.filter { it[1] == v }.groupBy { it[0].toDouble() }.toSortedMap().map { (d, g) -> d to g.map { it[5].toDouble() }.average() }
            sb.append("""<polyline fill="none" stroke="$col" stroke-width="2" points="${pts.joinToString(" ") { "${f(x0 + it.first * sx, 1)},${f(y0 - it.second * sy, 1)}" }}"/>""")
            for ((d, y) in pts) sb.append("""<circle cx="${f(x0 + d * sx, 1)}" cy="${f(y0 - y * sy, 1)}" r="4" fill="$col"/>""")
        }
        sb.append("""<rect x="480" y="50" width="12" height="4" fill="#1565C0"/><text x="497" y="56">vicinato 20 cm (produzione)</text><rect x="480" y="68" width="12" height="4" fill="#C62828"/><text x="497" y="74">vicinato 12 cm</text><text x="480" y="92" fill="#555">media di 3 semi, rumore S1</text></svg>""")
        return sb.toString()
    }
}
