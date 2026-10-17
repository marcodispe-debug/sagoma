package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.LinkDecision
import com.sagoma.planimetria.scan.reconstruction.LinkKind
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.VoxelIndex
import kotlin.math.floor
import kotlin.math.max

/** BENCHMARK (solo test) — report deterministici: JSON completo, CSV di sintesi, vista dall'alto SVG per scenario. */
object BenchReport {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else "%.${d}f".format(java.util.Locale.ROOT, v)
    private fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    private fun jm(m: Map<String, Int>) = m.entries.sortedBy { it.key }.joinToString(",", "{", "}") { "${js(it.key)}:${it.value}" }

    fun json(results: List<Pair<BenchRun, BenchMetrics>>): String = wrap(results.map { (run, m) -> runJson(run, m) })

    /** Documento completo da frammenti già serializzati (permette di non tenere in memoria le esecuzioni). */
    fun wrap(runs: List<String>): String = buildString {
        append("{\n\"note\":").append(js("BENCHMARK SPERIMENTALE con ground truth: pipeline di produzione R1→R2→R3/R3.1→R4.1 su osservazioni simulate. Le tolleranze sono del benchmark, non degli algoritmi.")).append(",\n\"runs\":[\n")
        append(runs.joinToString(",\n"))
        append("\n]}\n")
    }

    fun runJson(run: BenchRun, m: BenchMetrics): String = run { val sp = run.scenario.sensor
            "{" + listOf(
                "\"scenario\":${js(m.scenario)}", "\"title\":${js(run.scenario.title)}", "\"purpose\":${js(run.scenario.purpose)}", "\"variant\":${js(m.variant)}",
                "\"neighborhoodM\":${f(run.params.variant.neighborhoodM)}",
                "\"sensor\":{\"seed\":${sp.seed},\"depthSigmaAt1mM\":${f(sp.depthSigmaM, 4)},\"dropout\":${f(sp.dropout)},\"frames\":${run.sim.frames.size},\"pixels\":\"${sp.width}x${sp.height}\",\"poseNoiseM\":${f(sp.poseNoiseM, 4)},\"driftMPerFrame\":${f(sp.driftMPerFrame, 4)}},",
                "\"r1\":{\"gtHits\":${jm(m.r1.gtHits)},\"generated\":${jm(m.r1.generated)},\"accepted\":${jm(m.r1.accepted)},\"rejected\":{" + m.r1.rejected.entries.sortedBy { it.key }.joinToString(",") { "${js(it.key)}:${jm(it.value)}" } + "}," +
                    "\"wallRecall\":${f(m.r1.wallRecall, 4)},\"wallErrorM\":{\"mean\":${f(m.r1.wallErrorMeanM, 4)},\"rms\":${f(m.r1.wallErrorRmsM, 4)},\"p95\":${f(m.r1.wallErrorP95M, 4)},\"max\":${f(m.r1.wallErrorMaxM, 4)}}," +
                    "\"wallCoverage\":[" + m.r1.wallCoverage.joinToString(",") { "{\"wall\":${it.first},\"sensor\":${f(it.second)},\"r1\":${f(it.third)}}" } + "]}",
                "\"r2\":{\"wallPointsAccepted\":${m.r2.wallPointsAccepted},\"wallOutcomes\":${jm(m.r2.wallOutcomes)},\"contamination\":${jm(m.r2.contamination)},\"structuralPoints\":${m.r2.structuralPoints}," +
                    "\"structuralRecall\":${f(m.r2.recall, 4)},\"endToEndRecall\":${f(m.r2.endToEndRecall, 4)},\"structuralPrecision\":${f(m.r2.precision, 4)},\"contaminationRate\":${f(m.r2.contaminationRate, 4)},\"falseStructuralSurfaces\":${m.r2.falseStructuralSurfaces},\"replicaMismatches\":${m.r2.replicaMismatches},\"perWall\":{" + m.r2.perWall.entries.joinToString(",") { "\"${it.key}\":${jm(it.value)}" } + "},\"wallSurfaces\":{" + m.r2.wallSurfaces.entries.joinToString(",") { e -> "\"${e.key}\":[" + e.value.joinToString(",") { js(it) } + "]" } + "}}",
                "\"corners\":[" + m.corners.joinToString(",") { c -> "{\"corner\":${c.cornerId},\"x\":${f(c.x)},\"z\":${f(c.z)},\"wall\":${c.wall},\"side\":${js(c.side)},\"sensorLastM\":${f(c.sensorLastM, 4).ifEmpty { "null" }},\"r1LastM\":${f(c.r1LastM, 4).ifEmpty { "null" }},\"r2LastM\":${f(c.r2LastM, 4).ifEmpty { "null" }},\"r3EndM\":${f(c.r3EndM, 4).ifEmpty { "null" }},\"r3EndState\":${js(c.r3EndState)},\"r3EndSigmaM\":${f(c.r3EndSigmaM, 4).ifEmpty { "null" }},\"zonePointsR1\":${c.zonePointsR1},\"lostByR1\":${c.sensorLostR1},\"lostNormal\":${c.lostNormal},\"lostGrowing\":${c.lostGrowing},\"wrongSurface\":${c.wrongSurface},\"objectOrUnknown\":${c.objectOrUnknown},\"assigned\":${c.assigned}}" } + "]",
                "\"openings\":[" + m.openings.joinToString(",") { o -> "{\"id\":${js(o.id)},\"wall\":${o.wall},\"kind\":${js(o.kind.name)},\"zonePoints\":${o.zonePoints},\"lost\":${o.lost},\"lostByCause\":${jm(o.lostByCause)},\"outsideInStructural\":${o.outsideInStructural},\"r3Signal\":${js(o.r3Signal)}}" } + "]",
                "\"r3\":{\"r3Walls\":${m.r3.r3Walls},\"impureWalls\":${m.r3.impureWalls},\"falseWalls\":[" + m.r3.falseWalls.joinToString(",") { js(it) } + "],\"walls\":[" + m.r3.walls.joinToString(",") { w ->
                    "{\"wall\":${w.wall},\"trueLengthM\":${f(w.trueLengthM)},\"detected\":${w.detected},\"fragments\":${w.fragments},\"observedLengthM\":${f(w.observedLengthM)},\"extentLengthM\":${f(w.extentLengthM)},\"lengthErrorM\":${f(w.lengthErrorM, 4)},\"lengthErrorRel\":${f(w.lengthErrorRel, 4)},\"orientationErrorDeg\":${f(w.orientationErrorDeg, 3).ifEmpty { "null" }},\"positionErrorM\":${f(w.positionErrorM, 4).ifEmpty { "null" }},\"startErrorM\":${f(w.startErrorM, 4).ifEmpty { "null" }},\"startState\":${js(w.startState)},\"startSigmaM\":${f(w.startSigmaM, 4).ifEmpty { "null" }},\"endErrorM\":${f(w.endErrorM, 4).ifEmpty { "null" }},\"endState\":${js(w.endState)},\"endSigmaM\":${f(w.endSigmaM, 4).ifEmpty { "null" }}}"
                } + "]}",
                "\"r4\":{\"gtCorners\":${m.r4.gtCorners},\"correct\":${m.r4.correct},\"correctAmbiguous\":${m.r4.correctAmbiguous},\"wrong\":${m.r4.wrong},\"missed\":${m.r4.missed},\"correctObservedSupport\":${m.r4.correctObservedSupport},\"correctInferredSupport\":${m.r4.correctInferredSupport},\"sameWallCorrect\":${m.r4.sameWallCorrect},\"sameWallWrong\":${m.r4.sameWallWrong},\"sensorClosable\":${m.r4.sensorClosable},\"mainState\":${js(m.r4.mainState)},\"closureVerdict\":${js(m.r4.closureVerdict)},\"links\":[" + m.r4.links.joinToString(",") { js(it) } + "]}",
                "\"global\":{\"geometryRecall\":${f(m.global.geometryRecall, 4)},\"geometryPrecision\":${f(m.global.geometryPrecision, 4)},\"wallRecall\":${f(m.global.wallRecall, 4)},\"wallPrecision\":${f(m.global.wallPrecision, 4)},\"cornerRecall\":${f(m.global.cornerRecall, 4)},\"cornerPrecision\":${f(m.global.cornerPrecision, 4)},\"openingRecall\":${f(m.global.openingRecall, 4).ifEmpty { "null" }},\"openingFalsePositives\":${m.global.openingFalsePositives},\"occlusion\":${jm(m.global.occlusion)},\"falseClosure\":${m.global.falseClosure},\"meanGeomErrorM\":${f(m.global.meanGeomErrorM, 4)},\"maxGeomErrorM\":${f(m.global.maxGeomErrorM, 4)}}",
            ).joinToString(",") + "}"
    }

    val summaryHeader = "scenario;variant;neighborhoodM;r1WallRecall;r1WallErrRmsM;r2StructRecall;r2EndToEndRecall;r2Precision;r2Contamination;r2FalseStructural;" +
        "r2LostNormal;r2LostGrowing;r2LostDistance;r2LostAssignOrder;r2Voxelization;r2WrongWall;r2Unknown;r2Object;r2VerticalObject;" +
        "geometryRecall;geometryPrecision;wallRecall;wallPrecision;fragmentsMax;falseWalls;cornerRecall;cornerPrecision;cornersObservedSupport;cornersInferredSupport;" +
        "openingRecall;openingFP;occlusion;r4State;closureVerdict;meanGeomErrM;maxGeomErrM;meanCornerGapR1M;meanCornerGapR2M;meanCornerGapR3M"

    fun summaryRow(m: BenchMetrics, neigh: Double): String {
        val o = m.r2.wallOutcomes
        fun g(k: String) = o[k] ?: 0
        fun mean(xs: List<Double?>) = xs.filterNotNull().let { if (it.isEmpty()) null else it.average() }
        return listOf(
            m.scenario, m.variant, f(neigh), f(m.r1.wallRecall), f(m.r1.wallErrorRmsM, 4), f(m.r2.recall), f(m.r2.endToEndRecall), f(m.r2.precision), f(m.r2.contaminationRate, 4), m.r2.falseStructuralSurfaces,
            g("NORMAL_THRESHOLD") + g("INSUFFICIENT_EVIDENCE"), g("REGION_GROWING") + g("NO_SURFACE"), g("DISTANCE_THRESHOLD"), g("ASSIGNMENT_ORDER"), g("VOXELIZATION"), g("WRONG_WALL") + g("IN_FALSE_WALL") + g("SURFACE_CONFLICT"), g("UNKNOWN"), g("OBJECT"), g("VERTICAL_OBJECT"),
            f(m.global.geometryRecall), f(m.global.geometryPrecision), f(m.global.wallRecall), f(m.global.wallPrecision), m.r3.walls.maxOf { it.fragments }, m.r3.falseWalls.size,
            f(m.global.cornerRecall), f(m.global.cornerPrecision), m.r4.correctObservedSupport, m.r4.correctInferredSupport,
            f(m.global.openingRecall), m.global.openingFalsePositives, m.global.occlusion.entries.joinToString(" | ") { "${it.key}=${it.value}" }, m.r4.mainState, m.r4.closureVerdict,
            f(m.global.meanGeomErrorM, 4), f(m.global.maxGeomErrorM, 4),
            f(mean(m.corners.map { it.r1LastM?.let { v -> max(0.0, v) } }), 4), f(mean(m.corners.map { it.r2LastM?.let { v -> max(0.0, v) } }), 4), f(mean(m.corners.map { it.r3EndM?.let { v -> max(0.0, v) } }), 4),
        ).joinToString(";") { it.toString().replace(";", ",") }
    }

    /** Vista dall'alto di uno scenario: verità, punti (esito R2), muri R3, angoli R4, errori evidenziati. */
    fun svg(run: BenchRun, m: BenchMetrics, widthPx: Int = 1000): String {
        val room = run.scenario.room
        val pad = 0.6
        val x0 = room.minX - pad; val x1 = room.maxX + pad; val z0 = room.minZ - pad; val z1 = room.maxZ + pad
        val sc = (widthPx - 40) / (x1 - x0)
        val h = ((z1 - z0) * sc + 230).toInt()
        fun X(x: Double) = f((x - x0) * sc + 20, 1)
        fun Z(z: Double) = f((z - z0) * sc + 70, 1)
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="$widthPx" height="$h" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="15" font-weight="bold">${m.scenario} — ${run.scenario.title} · ${m.variant} (vicinato ${f(run.params.variant.neighborhoodM * 100, 0)} cm)</text>""")
        sb.append("""<text x="20" y="42" fill="#555">BENCHMARK con ground truth. Nero = verità · punti: blu = parete assegnata giusta, rosso = parete persa in R2, arancio = non-parete dentro un muro, grigio = altro</text>""")
        sb.append("""<text x="20" y="58" fill="#555">Verde spesso = muri R3 (pallini: estremità osservata, cerchietti: parziale, ?: incerta) · cerchio verde = angolo R4 corretto, croce rossa = sbagliato, cerchio nero vuoto = angolo vero mancato</text>""")
        // Punti (sottocampionati in modo deterministico), colorati per esito R2.
        val pts = run.map.points
        val sz = run.map.voxels.sizeM
        val step = max(1, pts.size / 15000)
        for (i in 0 until pts.size step step) {
            val y = pts.y[i]; if (y < 0.15 || y > room.heightM - 0.15) continue
            val l = run.pointLabels[i]
            val v = run.map.voxels.find(VoxelIndex.key(floor(pts.x[i] / sz).toInt(), floor(y / sz).toInt(), floor(pts.z[i] / sz).toInt()))
            val sid = run.surfaces.voxelSurface[v]
            val structural = sid >= 0 && run.surfaces.surfaces[sid].kind == SurfaceKind.VERTICAL_STRUCTURAL
            val col = when {
                Label.isWall(l) && structural -> "#1E88E5"
                Label.isWall(l) -> "#E53935"
                structural -> "#FB8C00"
                else -> "#C8C8C8"
            }
            sb.append("""<circle cx="${X(pts.x[i].toDouble())}" cy="${Z(pts.z[i].toDouble())}" r="1.1" fill="$col" fill-opacity="0.55"/>""")
        }
        // Verità: pareti, aperture, zone senza ritorno, mobili, pannelli.
        for (w in room.walls) {
            sb.append("""<line x1="${X(w.ax)}" y1="${Z(w.az)}" x2="${X(w.bx)}" y2="${Z(w.bz)}" stroke="#000" stroke-width="1.5"/>""")
            for (o in w.openings) { val a = w.pointAt(o.fromM); val b = w.pointAt(o.toM); sb.append("""<line x1="${X(a[0])}" y1="${Z(a[1])}" x2="${X(b[0])}" y2="${Z(b[1])}" stroke="#00ACC1" stroke-width="5"/><text x="${X((a[0] + b[0]) / 2)}" y="${Z((a[1] + b[1]) / 2)}" fill="#00838F" font-size="10">${o.kind} ${o.id}</text>""") }
            for (o in w.noReturn) { val a = w.pointAt(o.fromM); val b = w.pointAt(o.toM); sb.append("""<line x1="${X(a[0])}" y1="${Z(a[1])}" x2="${X(b[0])}" y2="${Z(b[1])}" stroke="#6A1B9A" stroke-width="5" stroke-dasharray="3,2"/>""") }
            val mm = w.pointAt(w.length / 2); sb.append("""<text x="${X(mm[0] + w.nx * 0.25)}" y="${Z(mm[1] + w.nz * 0.25)}" fill="#000" font-size="11">GT${w.id}</text>""")
        }
        for (b in room.boxes) sb.append("""<rect x="${X(b.x0)}" y="${Z(b.z0)}" width="${f((b.x1 - b.x0) * sc, 1)}" height="${f((b.z1 - b.z0) * sc, 1)}" fill="#9E9E9E" fill-opacity="0.35" stroke="#616161"/><text x="${X(b.x0)}" y="${Z(b.z0 - 0.03)}" font-size="10" fill="#424242">${b.name}</text>""")
        for (p in room.panels) sb.append("""<line x1="${X(p.ax)}" y1="${Z(p.az)}" x2="${X(p.bx)}" y2="${Z(p.bz)}" stroke="#795548" stroke-width="3"/>""")
        // Muri R3.
        for (w in run.walls.walls) {
            val g = w.geometry
            for (s in g.observedSpans) { val a = g.pointAt(s.fromU); val b = g.pointAt(s.toU); sb.append("""<line x1="${X(a[0])}" y1="${Z(a[1])}" x2="${X(b[0])}" y2="${Z(b[1])}" stroke="#2E7D32" stroke-width="3.5" stroke-opacity="0.8"/>""") }
            for (e in listOf(w.start, w.end)) {
                val q = g.pointAt(e.u)
                when (e.state) {
                    EndState.OBSERVED -> sb.append("""<circle cx="${X(q[0])}" cy="${Z(q[1])}" r="4" fill="#2E7D32"/>""")
                    EndState.PARTIAL -> sb.append("""<circle cx="${X(q[0])}" cy="${Z(q[1])}" r="4" fill="#FFF" stroke="#2E7D32" stroke-width="2"/>""")
                    EndState.UNCERTAIN -> sb.append("""<text x="${X(q[0])}" y="${Z(q[1])}" fill="#E65100" font-weight="bold">?</text>""")
                }
            }
            val mid = g.pointAt((g.startU + g.endU) / 2); sb.append("""<text x="${X(mid[0] - g.nx * 0.18)}" y="${Z(mid[1] - g.nz * 0.18)}" fill="#2E7D32" font-size="10">W${w.id}</text>""")
        }
        // Angoli R4 contro angoli veri.
        val ok = HashSet<Int>()
        for (l in run.r4.candidates.filter { it.kind == LinkKind.CORNER && it.decision != LinkDecision.REJECTED && it.cornerX != null }) {
            val c = room.corners.minByOrNull { (it.x - l.cornerX!!) * (it.x - l.cornerX!!) + (it.z - l.cornerZ!!) * (it.z - l.cornerZ!!) }
            val good = m.r4.links.any { it.startsWith("W${l.fromWall}→W${l.toWall} ") && "CORRETTO" in it }
            if (good && c != null) ok.add(c.id)
            if (good) sb.append("""<circle cx="${X(l.cornerX!!)}" cy="${Z(l.cornerZ!!)}" r="7" fill="none" stroke="#43A047" stroke-width="2"${if (l.support.name == "INFERRED") " stroke-dasharray=\"3,2\"" else ""}/>""")
            else sb.append("""<text x="${f((l.cornerX!! - x0) * sc + 14, 1)}" y="${f((l.cornerZ!! - z0) * sc + 75, 1)}" fill="#D50000" font-size="16" font-weight="bold">×</text>""")
        }
        for (c in room.corners) if (c.id !in ok) sb.append("""<circle cx="${X(c.x)}" cy="${Z(c.z)}" r="9" fill="none" stroke="#000" stroke-width="1.5"/><text x="${X(c.x + 0.05)}" y="${Z(c.z + 0.12)}" font-size="10">mancato</text>""")
        // Riepilogo.
        var y = h - 120
        for (t in listOf(
            "R1: recall pareti ${f(m.r1.wallRecall)} · errore RMS ${f(m.r1.wallErrorRmsM * 100, 1)} cm",
            "R2: recall strutturale ${f(m.r2.recall)} · precisione ${f(m.r2.precision)} · contaminazione ${f(m.r2.contaminationRate * 100, 2)}% · esiti ${m.r2.wallOutcomes}",
            "R3: pareti trovate ${m.r3.walls.count { it.detected }}/${m.r3.walls.size} · frammenti max ${m.r3.walls.maxOf { it.fragments }} · muri falsi ${m.r3.falseWalls.size} · errore geometrico medio ${f(m.global.meanGeomErrorM * 100, 1)} cm, max ${f(m.global.maxGeomErrorM * 100, 1)} cm",
            "R4: angoli corretti ${m.r4.correct}/${m.r4.gtCorners} (osservati ${m.r4.correctObservedSupport}, dedotti ${m.r4.correctInferredSupport}) · sbagliati ${m.r4.wrong} · ${m.r4.closureVerdict}",
            "Aperture: recall ${f(m.global.openingRecall)} · falsi positivi ${m.global.openingFalsePositives} · occlusione ${m.global.occlusion}",
        )) { sb.append("""<text x="20" y="$y" fill="#222">${t.replace("&", "&amp;").replace("<", "&lt;")}</text>"""); y += 18 }
        sb.append("</svg>")
        return sb.toString()
    }
}
