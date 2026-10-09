package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.reconstruction.R2CornerDiagnostic.Corner
import com.sagoma.planimetria.scan.reconstruction.R2CornerDiagnostic.CornerClass
import com.sagoma.planimetria.scan.reconstruction.R2CornerDiagnostic.Wall
import com.sagoma.planimetria.scan.reconstruction.R2CornerDiagnostic.f
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.ScanRecording
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SOLO DIAGNOSI R2.x sugli angoli (nessuna modifica di produzione). Sempre: fedeltà della replica tracciata di R2 e determinismo
 * sulla stanza sintetica. Con SAGOMA_R2DIAG_OUT=<cartella> scrive `r2-corner-diagnostic.json`, `.csv` e gli SVG per angolo; con
 * SAGOMA_R2DIAG_ZIPS=<zip1;zip2> analizza anche le registrazioni reali.
 */
class R2CornerDiagnosticTest {
    private class Dataset(
        val name: String, val rec: ScanRecording, val blobs: DatasetBlobs, val map: GlobalMap, val s: SurfaceResult,
        val corners: List<Corner>, val truthPlanes: List<Wall>?,
    )

    // ------------------------------------------------------------------------------------------------------------- dataset

    private fun synthetic(name: String, noise: Double): Dataset {
        val (rec, blobsMap) = SyntheticRoom(noiseM = noise).recording()
        val blobs = DatasetBlobs { blobsMap[it] }
        val map = GlobalMap.build(rec, blobs)
        val s = SurfaceExtractor.extract(map, rec)
        fun ids(nx: Double, nz: Double, d: Double) = s.surfaces.filter { sf ->
            sf.orientation == Orientation.VERTICAL && abs(sf.plane.nx * nx + sf.plane.nz * nz) > 0.98 &&
                abs(sf.plane.centroid[0] * nx + sf.plane.centroid[2] * nz - d) < 0.05
        }.map { it.id }.toSet()
        fun wall(n: String, nx: Double, nz: Double, d: Double, ax: Double, az: Double) = Wall(n, nx, nz, d, ax, az, ids(nx, nz, d))
        val tol = 0.03 // 3σ del rumore sintetico (1 cm); vale anche senza rumore
        fun c(n: String, x: Double, z: Double, a: Wall, b: Wall) = Corner(name, n, CornerClass.SYNTHETIC_GROUND_TRUTH, x, z, a, b, 0.2, 2.4, tol, "angolo vero della stanza sintetica (0..4 × 0..3)")
        val corners = listOf(
            c("C(0,0)", 0.0, 0.0, wall("z=0", 0.0, 1.0, 0.0, 1.0, 0.0), wall("x=0", 1.0, 0.0, 0.0, 0.0, 1.0)),
            c("C(0,3)", 0.0, 3.0, wall("z=3", 0.0, 1.0, 3.0, 1.0, 0.0), wall("x=0", 1.0, 0.0, 0.0, 0.0, -1.0)),
            c("C(4,3)", 4.0, 3.0, wall("z=3", 0.0, 1.0, 3.0, -1.0, 0.0), wall("x=4", 1.0, 0.0, 4.0, 0.0, -1.0)),
            c("C(4,0)", 4.0, 0.0, wall("z=0", 0.0, 1.0, 0.0, -1.0, 0.0), wall("x=4", 1.0, 0.0, 4.0, 0.0, 1.0)),
        )
        val truth = listOf(wall("x=0", 1.0, 0.0, 0.0, 0.0, 0.0), wall("x=4", 1.0, 0.0, 4.0, 0.0, 0.0), wall("z=0", 0.0, 1.0, 0.0, 0.0, 0.0), wall("z=3", 0.0, 1.0, 3.0, 0.0, 0.0))
        return Dataset(name, rec, blobs, map, s, corners, truth)
    }

    private fun real(zip: File): Dataset {
        val ds = CaptureDatasetFiles.open(zip)
        val rec = ds.recording
        val blobs = DatasetBlobs { ds.read(it) }
        val map = GlobalMap.build(rec, blobs)
        val s = SurfaceExtractor.extract(map, rec)
        val walls = WallEstimator.estimate(WallEstimator.inputFrom(map, s, rec))
        val r4 = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, walls))
        val byId = walls.walls.associateBy { it.id }
        val floor = s.floor?.y ?: map.frames.minOf { it.pose.y } - 1.4
        val tol = s.thresholds.growDistM
        val name = zip.nameWithoutExtension
        val corners = r4.candidates.filter { l ->
            l.kind == LinkKind.CORNER && l.decision != LinkDecision.REJECTED && l.cornerX != null && abs(l.extensionFromM) <= 0.5 && abs(l.extensionToM) <= 0.5
        }.sortedWith(compareBy({ it.fromWall }, { it.toWall })).map { l ->
            val a = byId.getValue(l.fromWall); val b = byId.getValue(l.toWall)
            val ga = a.geometry; val gb = b.geometry
            val wa = Wall("W${a.id}", ga.nx, ga.nz, ga.d, -ga.ux, -ga.uz, a.sourceSurfaceIds.toSet())
            val wb = Wall("W${b.id}", gb.nx, gb.nz, gb.d, gb.ux, gb.uz, b.sourceSurfaceIds.toSet())
            val cls = when {
                l.support == LinkSupport.INFERRED -> CornerClass.R3_INFERRED
                l.decision == LinkDecision.ACCEPTED && a.end.reason == EndReason.CORNER && b.start.reason == EndReason.CORNER -> CornerClass.PROBABLE_PHYSICAL
                else -> CornerClass.UNCERTAIN
            }
            val lo = maxOf(ga.bottomY, gb.bottomY, floor + 0.2); val hi = minOf(ga.topY, gb.topY, floor + 2.2)
            Corner(
                name, "W${a.id}→W${b.id}", cls, l.cornerX!!, l.cornerZ!!, wa, wb, if (hi > lo) lo else floor + 0.2, if (hi > lo) hi else floor + 2.2, tol,
                "R4 ${l.decision} ${l.support}; fine W${a.id} ${a.end.state}/${a.end.reason} a ${f(l.extensionFromM * 100, 1)} cm; inizio W${b.id} ${b.start.state}/${b.start.reason} a ${f(l.extensionToM * 100, 1)} cm",
            )
        }
        return Dataset(name, rec, blobs, map, s, corners, null)
    }

    // ------------------------------------------------------------------------------------------------------------ analisi

    private class Analysis(val ds: Dataset, val trace: R2CornerDiagnostic.Trace, val results: List<R2CornerDiagnostic.Result>, val experiments: List<String>, val absorption: List<String>)

    private fun run(ds: Dataset, withExperiments: Boolean): Analysis {
        val map = ds.map
        val watch = BooleanArray(map.voxels.size) { v -> val c = map.voxels.moments[v].centroid(); ds.corners.any { k -> k.radius(c[0], c[2]) <= 0.30 } }
        val trace = R2CornerDiagnostic.trace(map, ds.s, watch)
        val raw = R2CornerDiagnostic.rawPixels(ds.rec, ds.blobs, ds.corners)
        val results = ds.corners.map { R2CornerDiagnostic.analyze(map, ds.s, trace, raw, it) }
        val exp = mutableListOf<String>()
        val absorption = mutableListOf<String>()
        if (withExperiments) {
            fun gaps(label: String, m: GlobalMap, s: SurfaceResult, prod: Boolean) {
                for (c in ds.corners) for ((w, code) in listOf(c.a to 1, c.b to 2)) {
                    val g = if (prod) R2CornerDiagnostic.gap(m, s, c, w, code, w.surfaces) else R2CornerDiagnostic.gap(m, s, c, w, code)
                    exp.add("$label;${c.name};${w.name};${g?.let { f(it * 100, 1) } ?: "nessuna superficie"};strutturali ${s.surfaces.count { it.kind == SurfaceKind.VERTICAL_STRUCTURAL }};voxel ${m.voxels.size}")
                }
            }
            gaps("produzione (voxel 4 cm, vicinato r=2 → 20 cm)", map, ds.s, true)
            for ((label, rp, sp) in listOf(
                Triple("voxel 2 cm, vicinato r=2 (10 cm)", ReconParams(voxelM = 0.02), SurfaceParams()),
                Triple("voxel 2 cm, vicinato r=4 (18 cm, come la produzione)", ReconParams(voxelM = 0.02), SurfaceParams(normalRadius = 4)),
                Triple("voxel 4 cm, vicinato r=1 (12 cm)", ReconParams(), SurfaceParams(normalRadius = 1)),
            )) {
                val m2 = if (rp.voxelM == map.voxels.sizeM) map else GlobalMap.build(ds.rec, ds.blobs, rp)
                gaps(label, m2, SurfaceExtractor.extract(m2, ds.rec, sp), false)
            }
            // Crescita "bypassata" (oracolo): ogni punto entro la distanza di crescita del piano finale della parete.
            for (c in ds.corners) for ((w, code) in listOf(c.a to 1, c.b to 2)) {
                val sf = ds.s.surfaces.filter { it.id in w.surfaces }.maxByOrNull { it.samples } ?: continue
                val lim = ds.s.thresholds.growDistM
                var best: Double? = null; var foreign = 0
                for (i in 0 until map.points.size) {
                    val x = map.points.x[i].toDouble(); val y = map.points.y[i].toDouble(); val z = map.points.z[i].toDouble()
                    if (y !in c.yLo..c.yHi || c.radius(x, z) > 0.5) continue
                    if (abs(sf.plane.distance(x, y, z)) > lim) continue
                    val sd = c.side(x, z)
                    if (sd == code) { val al = max(0.0, c.along(w, x, z)); if (best == null || al < best) best = al } else if (c.radius(x, z) <= 0.15) foreign++
                }
                exp.add("crescita bypassata (solo distanza dal piano finale ≤ ${f(lim * 100, 1)} cm);${c.name};${w.name};${best?.let { f(it * 100, 1) } ?: "-"};punti non della parete entro 15 cm che verrebbero presi: $foreign")
            }
            // Fit robusto: pesi di Huber dei punti della parete vicino all'angolo (R2 non toglie punti, li pesa).
            for (c in ds.corners) for ((w, code) in listOf(c.a to 1, c.b to 2)) {
                val sf = ds.s.surfaces.filter { it.id in w.surfaces }.maxByOrNull { it.samples } ?: continue
                val k = max(0.01, 2 * sf.plane.rms)
                val idx = SurfaceExtractor.pointsOf(map, sf.memberVoxels)
                fun weights(near: Boolean) = idx.filter { i -> (c.radius(map.points.x[i].toDouble(), map.points.z[i].toDouble()) <= 0.15) == near }
                    .map { i -> val r = abs(sf.plane.distance(map.points.x[i].toDouble(), map.points.y[i].toDouble(), map.points.z[i].toDouble())); if (r <= k) 1.0 else k / r }
                val n = weights(true); val all = weights(false)
                exp.add("fit robusto (pesi di Huber);${c.name};${w.name};entro 15 cm: ${n.size} punti, peso medio ${if (n.isEmpty()) "-" else f(n.average(), 3)};altrove: peso medio ${if (all.isEmpty()) "-" else f(all.average(), 3)};punti rimossi dal fit: 0 (Huber pesa, non scarta)")
            }
            // Candidata R2.x: assorbimento a livello di punto ai bordi, con la valutazione dei falsi positivi.
            for (rings in 1..3) {
                val abs = R2CornerDiagnostic.absorb(map, ds.s, rings)
                val toWall = HashMap<Int, MutableList<IntArray>>(); for (a in abs) toWall.getOrPut(a[1]) { mutableListOf() }.add(a)
                val fromKind = abs.groupingBy { if (it[2] < 0) "non assegnati" else ds.s.surfaces[it[2]].kind.name }.eachCount().toSortedMap()
                var fp = -1
                ds.truthPlanes?.let { truth ->
                    fp = 0
                    for (a in abs) {
                        val sf = ds.s.surfaces[a[1]]
                        val tw = truth.minBy { t -> abs(sf.plane.centroid[0] * t.nx + sf.plane.centroid[2] * t.nz - t.d) + (1 - abs(sf.plane.nx * t.nx + sf.plane.nz * t.nz)) * 10 }
                        if (abs(tw.dist(map.points.x[a[0]].toDouble(), map.points.z[a[0]].toDouble())) > 0.03) fp++
                    }
                }
                val nearCorners = abs.count { a -> ds.corners.any { c -> c.radius(map.points.x[a[0]].toDouble(), map.points.z[a[0]].toDouble()) <= 0.15 } }
                absorption.add("anelli $rings (${rings * 4} cm);punti assorbiti ${abs.size};entro 15 cm dagli angoli analizzati $nearCorners;lontano dagli angoli ${abs.size - nearCorners};provenienza $fromKind;" +
                    (if (fp >= 0) "falsi positivi (fuori dal piano vero > 3 cm) $fp (${f(if (abs.isEmpty()) 0.0 else fp * 100.0 / abs.size, 1)}%)" else "falsi positivi: nessuna verità a terra (reale)"))
                for (c in ds.corners) for ((w, code) in listOf(c.a to 1, c.b to 2)) {
                    // Gap con i punti assorbiti.
                    val absorbed = toWall.filterKeys { it in w.surfaces }.values.flatten().map { it[0] }.toHashSet()
                    var best: Double? = null
                    val sz = map.voxels.sizeM
                    for (i in 0 until map.points.size) {
                        val x = map.points.x[i].toDouble(); val z = map.points.z[i].toDouble(); val y = map.points.y[i].toDouble()
                        if (y !in c.yLo..c.yHi || c.radius(x, z) > 0.5 || c.side(x, z) != code) continue
                        val v = map.voxels.find(VoxelIndex.key(kotlin.math.floor(x / sz).toInt(), kotlin.math.floor(y / sz).toInt(), kotlin.math.floor(z / sz).toInt()))
                        if (ds.s.voxelSurface[v] !in w.surfaces && i !in absorbed) continue
                        val al = max(0.0, c.along(w, x, z)); if (best == null || al < best) best = al
                    }
                    absorption.add("anelli $rings;${c.name};${w.name};gap ${best?.let { f(it * 100, 1) } ?: "-"} cm;assorbiti in questa parete entro 15 cm ${absorbed.count { i -> c.radius(map.points.x[i].toDouble(), map.points.z[i].toDouble()) <= 0.15 }}")
                }
            }
        }
        return Analysis(ds, trace, results, exp, absorption)
    }

    // ------------------------------------------------------------------------------------------------------------- uscite

    private fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun json(all: List<Analysis>): String = buildString {
        append("{\n\"note\":").append(js("SOLO DIAGNOSI R2.x: nessuna modifica di produzione. Replica di R2 verificata voxel per voxel (mismatches).")).append(",\n\"datasets\":[\n")
        append(all.joinToString(",\n") { a ->
            val th = a.trace.th
            "{\"name\":${js(a.ds.name)},\"replicaMismatches\":${a.trace.mismatches},\"thresholds\":{\"seedMaxVariation\":${f(th.seedMaxVariation, 4)},\"growMaxVariation\":${f(th.growMaxVariation, 4)},\"growAngleDeg\":${f(th.growAngleDeg, 2)},\"growDistM\":${f(th.growDistM, 4)},\"noiseM\":${f(th.noiseM, 4)}}," +
                "\"corners\":[" + a.results.joinToString(",") { r ->
                    val c = r.corner
                    "{\"name\":${js(c.name)},\"class\":${js(c.cls.name)},\"x\":${f(c.x, 4)},\"z\":${f(c.z, 4)},\"wallA\":${js(c.a.name)},\"wallB\":${js(c.b.name)},\"surfacesA\":${c.a.surfaces.sorted()},\"surfacesB\":${c.b.surfaces.sorted()},\"yBand\":[${f(c.yLo, 3)},${f(c.yHi, 3)}],\"tolM\":${f(c.tol, 4)},\"note\":${js(c.note)}," +
                        "\"radii\":[" + r.rows.joinToString(",") { w -> "{\"radiusM\":${f(w.radius, 2)},\"raw\":${w.raw},\"accepted\":${w.accepted},\"voxels\":${w.voxels},\"wallA\":${w.wallA},\"wallB\":${w.wallB},\"object\":${w.obj},\"unknown\":${w.unknown},\"otherSurface\":${w.otherSurface},\"discarded\":${w.discarded},\"ambiguousAB\":${w.ambiguous}}" } + "]," +
                        "\"profiles\":[" + r.profiles.joinToString(",") { p -> "{\"wall\":${js(p.wall)},\"r1Points\":${p.r1Points},\"r2Assigned\":${p.r2Assigned},\"firstR1AlongM\":${p.firstR1AlongM?.let { f(it, 4) } ?: "null"},\"firstAssignedAlongM\":${p.firstAssignedAlongM?.let { f(it, 4) } ?: "null"}}" } + "]," +
                        "\"losses\":[" + r.losses.joinToString(",") { l -> "{\"wall\":${js(l.wall)},\"lostAt\":${js(l.loss.name)},\"count\":${l.count},\"pctOfLost\":${f(l.pctOfLost, 1)},\"pctOfWall\":${f(l.pctOfWall, 1)},\"reasons\":[" + l.reasons.joinToString(",") { "{\"reason\":${js(it.first)},\"count\":${it.second}}" } + "]}" } + "]," +
                        "\"normalProfile\":[" + r.normalProfile.joinToString(",") { js(it) } + "]," +
                        "\"voxels\":{\"columns\":${js("ix;iy;iz;cx;cy;cz;punti;raggio;distA;distB;normale;variazione;angoloDaA;angoloDaB;superficie;regione;tentativi")},\"rows\":[" + r.voxelSamples.joinToString(",") { js(it) } + "]}}"
                } + "]," +
                "\"experiments\":[" + a.experiments.joinToString(",") { js(it) } + "],\"absorptionCandidate\":[" + a.absorption.joinToString(",") { js(it) } + "]}"
        })
        append("\n]}\n")
    }

    private fun csv(all: List<Analysis>): String = buildString {
        append("section;dataset;corner;class;radiusOrWall;raw;accepted;voxels;wallA;wallB;object;unknown;otherSurface;discarded;ambiguousAB;lostAt;count;pctOfLost;pctOfWall;topReason\n")
        for (a in all) for (r in a.results) {
            val c = r.corner
            for (w in r.rows) append("radius;${a.ds.name};${c.name};${c.cls};${f(w.radius * 100, 0)} cm;${w.raw};${w.accepted};${w.voxels};${w.wallA};${w.wallB};${w.obj};${w.unknown};${w.otherSurface};${w.discarded};${w.ambiguous};;;;;\n")
            for (l in r.losses) append("loss;${a.ds.name};${c.name};${c.cls};${l.wall};;;;;;;;;;;${l.loss};${l.count};${f(l.pctOfLost, 1)};${f(l.pctOfWall, 1)};${l.reasons.firstOrNull()?.first?.replace(";", ",") ?: ""}\n")
        }
    }

    private val colors = mapOf(
        "A" to "#1565C0", "B" to "#2E7D32", "OTHER_SURFACE" to "#FB8C00", "AMBIGUOUS" to "#8E24AA", "NONE" to "#BDBDBD",
        "VOXELIZATION" to "#D50000", "REGION_GROWING" to "#C51162", "NORMAL_THRESHOLD" to "#6D4C41", "DISTANCE_THRESHOLD" to "#827717",
        "SURFACE_CONFLICT" to "#B71C1C", "ASSIGNMENT_ORDER" to "#F06292", "OBJECT_CLASSIFICATION" to "#E65100", "UNKNOWN" to "#424242",
        "INSUFFICIENT_EVIDENCE" to "#000000", "R1_EDGE" to "#000000", "R1_CONFIDENCE" to "#000000", "R1_RANGE" to "#000000", "OTHER" to "#00838F",
    )

    private fun svg(r: R2CornerDiagnostic.Result, voxel: Double): String {
        val c = r.corner
        val half = 0.20; val px = 1500.0 // 1,5 px/mm
        fun X(x: Double) = f((x - c.x + half) * px + 20, 1)
        fun Z(z: Double) = f((z - c.z + half) * px + 70, 1)
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="${(2 * half * px + 260).toInt()}" height="${(2 * half * px + 100).toInt()}" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="20" font-size="14" font-weight="bold">${c.dataset} ${c.name} (${c.cls}) — punti in pianta entro 20 cm, quota ${f(c.yLo, 2)}..${f(c.yHi, 2)} m</text>""")
        sb.append("""<text x="20" y="40" fill="#555">Griglia = voxel R1 da ${f(voxel * 100, 0)} cm · cerchi = 2/4/6/8/10/15 cm · linee = piani delle due pareti (${c.a.name} blu, ${c.b.name} verde). SOLO DIAGNOSI.</text>""")
        var g = kotlin.math.floor((c.x - half) / voxel) * voxel
        while (g <= c.x + half) { sb.append("""<line x1="${X(g)}" y1="${Z(c.z - half)}" x2="${X(g)}" y2="${Z(c.z + half)}" stroke="#EEE"/>"""); g += voxel }
        g = kotlin.math.floor((c.z - half) / voxel) * voxel
        while (g <= c.z + half) { sb.append("""<line x1="${X(c.x - half)}" y1="${Z(g)}" x2="${X(c.x + half)}" y2="${Z(g)}" stroke="#EEE"/>"""); g += voxel }
        for (rr in R2CornerDiagnostic.radii) sb.append("""<circle cx="${X(c.x)}" cy="${Z(c.z)}" r="${f(rr * px, 1)}" fill="none" stroke="#DDD" stroke-dasharray="3,3"/>""")
        val step = max(1, r.points.size / 8000)
        for (k in r.points.indices step step) {
            val q = r.points[k]
            if (abs(q[0] - c.x) > half || abs(q[1] - c.z) > half) continue
            sb.append("""<circle cx="${X(q[0])}" cy="${Z(q[1])}" r="1.6" fill="${colors[r.pointClass[k]] ?: "#000"}" fill-opacity="0.7"/>""")
        }
        for ((w, col) in listOf(c.a to "#1565C0", c.b to "#2E7D32")) {
            val p0x = c.x - w.ax * 0.05; val p0z = c.z - w.az * 0.05; val p1x = c.x + w.ax * half; val p1z = c.z + w.az * half
            sb.append("""<line x1="${X(p0x)}" y1="${Z(p0z)}" x2="${X(p1x)}" y2="${Z(p1z)}" stroke="$col" stroke-width="1" stroke-opacity="0.6"/>""")
        }
        sb.append("""<circle cx="${X(c.x)}" cy="${Z(c.z)}" r="4" fill="none" stroke="#000" stroke-width="2"/>""")
        var ly = 70
        val lx = (2 * half * px + 40).toInt()
        val present = r.pointClass.toSet()
        for ((k, col) in colors) if (k in present) { sb.append("""<rect x="$lx" y="${ly - 9}" width="10" height="10" fill="$col"/><text x="${lx + 15}" y="$ly">$k (${r.pointClass.count { it == k }})</text>"""); ly += 18 }
        sb.append("</svg>")
        return sb.toString()
    }

    private fun write(all: List<Analysis>, out: File) {
        out.mkdirs()
        File(out, "r2-corner-diagnostic.json").writeText(json(all))
        File(out, "r2-corner-diagnostic.csv").writeText(csv(all))
        for (a in all) for (r in a.results) File(out, "r2-corner-${a.ds.name}-${r.corner.name.replace(Regex("[^A-Za-z0-9]+"), "_")}.svg").writeText(svg(r, a.ds.map.voxels.sizeM))
    }

    // --------------------------------------------------------------------------------------------------------------- test

    @Test
    fun `replica tracciata di R2 fedele e diagnosi deterministica sulla stanza sintetica`() {
        val a = run(synthetic("synthetic-noise1cm", 0.01), withExperiments = false)
        assertEquals(0, a.trace.mismatches, "la replica di R2 deve coincidere con la produzione voxel per voxel")
        val b = run(synthetic("synthetic-noise1cm", 0.01), withExperiments = false)
        assertEquals(json(listOf(a)), json(listOf(b)))
    }

    @Test
    fun `report diagnostico R2 sugli angoli (solo con SAGOMA_R2DIAG_OUT)`() {
        val out = System.getenv("SAGOMA_R2DIAG_OUT") ?: return
        val sets = mutableListOf(synthetic("synthetic-noise1cm", 0.01), synthetic("synthetic-noise0", 0.0))
        System.getenv("SAGOMA_R2DIAG_ZIPS")?.split(';')?.filter { it.isNotBlank() }?.forEach { sets.add(real(File(it))) }
        val all = sets.map { run(it, withExperiments = true) }
        for (a in all) assertEquals(0, a.trace.mismatches, "replica di R2 non fedele su ${a.ds.name}")
        write(all, File(out))
    }
}
