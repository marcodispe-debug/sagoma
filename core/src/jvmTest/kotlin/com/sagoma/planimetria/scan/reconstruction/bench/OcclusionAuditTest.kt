package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.DatasetBlobs
import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.PerimeterSolver
import com.sagoma.planimetria.scan.reconstruction.Surface
import com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.WallEstimator
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AUDIT DIAGNOSTICO (solo test, nessuna modifica di produzione): si può distinguere una parete vera dal fronte di un oggetto davanti a
 * una parete NASCOSTA? Misura BACKFACE_EVIDENCE (indicatore geometrico, NON una classe), il contesto R3/R4 ingenuo e leave-one-out,
 * l'effetto del numero di viste e il caso impossibile (osservazioni identiche). I default del simulatore non cambiano: postazioni
 * inclinate e archi di viste sono configurazioni di QUESTO test.
 * Con SAGOMA_OCCLUSION_OUT=<cartella> (e SAGOMA_OCCLUSION_ZIPS=<zip1;zip2> per la distribuzione sui dati reali) scrive CSV, JSON, SVG.
 */
class OcclusionAuditTest {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else "%.${d}f".format(Locale.ROOT, v)

    /** Un caso: scenario, asse della normale del FRONTE degli oggetti ("z", "x", "xz"), gruppo e note sulle pareti. */
    class Case(val sc: Scenario, val frontAxis: String, val group: String, val wallNotes: Map<Int, String> = emptyMap())

    internal val noisy = SensorParams(depthSigmaM = 0.006, dropout = 0.03)
    /** Postazioni del test: ogni postazione anche con la camera inclinata verso l'alto (+15°), per vedere fino al soffitto. */
    internal fun up(st: List<Station>) = st + st.map { it.copy(pitchDeg = 15.0) }
    /** Anello del test: le due postazioni standard spostate verso z = 0 (1,0 e 1,3 m), così i mobili davanti a GT1 non arrivano alla camera. */
    internal val ring2 = up(listOf(Station(2.0, 1.4, 1.0), Station(2.6, 1.5, 1.3, yawFromDeg = 7.5, yawToDeg = 352.5)))

    internal fun box(name: String, x0: Double, x1: Double, gap: Double, h: Double, depth: Double = 0.6) = GtBox(0, name, x0, 0.0, 3.0 - gap - depth, x1, h, 3.0 - gap)
    internal fun room(id: String, vararg b: GtBox, h: Double = 2.6) = GtRoom(id, Scenarios.rect(), heightM = h, boxes = b.toList())
    internal fun case(id: String, title: String, r: GtRoom, group: String = "C", axis: String = "z", st: List<Station> = ring2, notes: Map<Int, String> = emptyMap()) =
        Case(Scenario(id, title, r, st, noisy, ""), axis, group, notes)

    private val pilasterWalls = listOf(
        GtWall(0, 0.0, 0.0, 0.0, 3.0), GtWall(1, 0.0, 3.0, 1.6, 3.0), GtWall(2, 1.6, 3.0, 1.6, 2.4), GtWall(3, 1.6, 2.4, 2.4, 2.4),
        GtWall(4, 2.4, 2.4, 2.4, 3.0), GtWall(5, 2.4, 3.0, 4.0, 3.0), GtWall(6, 4.0, 3.0, 4.0, 0.0), GtWall(7, 4.0, 0.0, 0.0, 0.0),
    )

    internal fun partC(): List<Case> = listOf(
        case("O0", "parete libera", room("O0")),
        case("O1", "mobile 5 cm davanti alla parete", room("O1", box("mobile a 5 cm", 1.2, 2.8, 0.05, 1.9))),
        case("O2", "mobile 10 cm davanti", room("O2", box("mobile a 10 cm", 1.2, 2.8, 0.10, 1.9))),
        case("O3", "mobile 20 cm davanti", room("O3", box("mobile a 20 cm", 1.2, 2.8, 0.20, 1.9))),
        case("O4", "mobile 40 cm davanti", room("O4", box("mobile a 40 cm", 1.2, 2.8, 0.40, 1.9))),
        case("O5", "mobile aderente", room("O5", box("mobile aderente", 1.2, 2.8, 0.005, 1.9))),
        case("O6", "mobile isolato", GtRoom("O6", Scenarios.rect(4.0, 5.0), boxes = listOf(GtBox(0, "mobile isolato", 1.2, 0.0, 3.0, 2.8, 1.1, 3.5)))),
        case("O7", "mobile davanti a metà parete", room("O7", box("mobile metà parete", 0.005, 2.0, 0.005, 1.9))),
        case("O8", "mobile nell'angolo", room("O8", box("mobile nell'angolo", 0.005, 0.9, 0.005, 1.9))),
        case("O9", "armadio a tutta altezza da parete a parete", room("O9", box("armadio parete-parete a tutta altezza", 0.005, 3.995, 0.005, 2.6))),
        case("O10", "armadio alto 2 m davanti a parete alta 3 m", room("O10", box("armadio 2 m", 1.2, 2.8, 0.005, 2.0), h = 3.0)),
        case("O11", "parete visibile solo sopra il mobile", room("O11", box("mobile parete-parete 1,9 m", 0.005, 3.995, 0.005, 1.9))),
        case("O12", "parete visibile solo ai lati", room("O12", box("armadio a tutta altezza 1,6 m", 1.2, 2.8, 0.005, 2.6))),
        // Controlli: pareti VERE con la stessa firma geometrica di un oggetto (parallela arretrata accanto, fianchi verso dietro).
        Case(Scenario("C1", "rientranza (pareti vere)", Scenarios.byId("S11").room, up(Scenarios.byId("S11").stations), noisy, ""), "", "controllo",
            mapOf(1 to "parete con rientranza accanto", 3 to "fondo della rientranza", 5 to "parete con rientranza accanto")),
        case("C2", "pilastro 0,8 × 0,6 m (parete vera)", GtRoom("C2", pilasterWalls), "controllo", notes = mapOf(3 to "fronte del pilastro", 2 to "fianco del pilastro", 4 to "fianco del pilastro")),
        case("C3", "armadio con l'impronta del pilastro", room("C3", GtBox(0, "armadio 0,8 × 0,6 a tutta altezza", 1.6, 0.0, 2.4, 2.4, 2.6, 3.0)), "controllo"),
    )

    internal fun partG(): List<Case> = listOf(
        case("G1", "parete", GtRoom("G1", Scenarios.rect(4.0, 2.4)), "G"),
        case("G2", "armadio aderente parete-parete a tutta altezza", room("G2", GtBox(0, "armadio", 0.0, 0.0, 2.4, 4.0, 2.6, 3.0)), "G"),
        case("G3", "pannello strutturale", GtRoom("G3", Scenarios.rect(), panels = listOf(GtPanel(0, "pannello strutturale", 0.0, 2.4, 4.0, 2.4, 0.0, 2.6))), "G"),
        case("G4", "divisorio", GtRoom("G4", Scenarios.rect(), panels = listOf(GtPanel(0, "divisorio", 0.0, 2.4, 4.0, 2.4, 0.0, 2.6))), "G"),
    )

    private fun bench(): List<Case> = listOf("S5" to "z", "S8" to "x", "S9" to "xz", "S17" to "z").map { (id, ax) -> Case(Scenarios.byId(id), ax, "benchmark") }

    /** Arco di [n] viste attorno al punto (cx, frontZ): raggio 1,6 m, ±70°; ogni vista = posizione con giro di 360° a −20° e a +15°. */
    internal fun arc(cx: Double, frontZ: Double, n: Int): List<Station> {
        val ang = when (n) { 1 -> listOf(0.0); 2 -> listOf(-40.0, 40.0); 4 -> listOf(-60.0, -20.0, 20.0, 60.0); else -> (0 until n).map { -70.0 + 140.0 * it / (n - 1) } }
        return ang.flatMap { a ->
            val x = cx + 1.6 * sin(Math.toRadians(a)); val z = frontZ - 1.6 * cos(Math.toRadians(a))
            listOf(Station(x, 1.4, z, 0.0, 330.0, 30.0, -20.0), Station(x, 1.4, z, 0.0, 330.0, 30.0, 15.0))
        }
    }

    // ------------------------------------------------------------------------------------------------------------ analisi

    class Row(
        val case: Case, val tag: String, val seed: Long, val views: Int, val sf: Surface, val truth: String, val role: String, val purity: Double,
        val sc: FreeSpaceAudit.Score, val bf: OcclusionAudit.Backface, val wallBehindVisiblePts: Int?, val naive: OcclusionAudit.Context?, val loo: OcclusionAudit.Context?,
    ) {
        val isWall get() = truth.startsWith("WALL")
        val isFront get() = role.startsWith("fronte")
        /** E1: superficie osservata DIETRO il fronte (dentro la sua estensione), oltre 15 cm: più vicino sono frammenti rumorosi dello stesso piano. */
        val e1 get() = bf.behindHitFrac >= 0.2 && (bf.behindHitMedianD ?: 0.0) >= 0.15
        /** E2: parallela adiacente arretrata (lato o sopra), continua. */
        val e2 get() = bf.bands.values.any { it.columns > 0 && it.recessedFrac >= 0.3 && (it.continuity ?: 0.0) >= 0.5 }
        /** E3: fianchi perpendicolari che vanno verso dietro. */
        val e3 get() = bf.sidesBack >= 1
        /** E4: nel contesto R3/R4 leave-one-out la superficie sta DAVANTI (lato interno) a una parete R3 parallela. */
        val e4 get() = loo?.side == "interno"
        val e4naive get() = naive?.side == "interno"
        /** E4b: nel contesto leave-one-out c'è una parete R3 parallela DIETRO e ACCANTO (la parete prosegue ai lati, arretrata). */
        val e4b get() = loo?.adjacentWallBehind != null
        val e4x get() = e4 || e4b
    }

    internal fun labels(run: BenchRun, sf: Surface): Map<Int, Int> {
        val h = HashMap<Int, Int>(); val vx = run.map.voxels
        for (v in sf.memberVoxels) for (t in vx.start[v] until vx.start[v] + vx.count[v]) h.merge(run.pointLabels[vx.order[t]], 1, Int::plus)
        return h
    }

    /** Verità e ruolo: parete GTn (con nota), fronte di oggetto (normale lungo l'asse del fronte), fianco/altro. */
    internal fun truthOf(c: Case, run: BenchRun, sf: Surface): Triple<String, String, Double> {
        val h = labels(run, sf); val tot = h.values.sum().coerceAtLeast(1)
        val maj = h.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).first().key
        val pur = h.getValue(maj).toDouble() / tot
        if (Label.isWall(maj)) { val id = Label.wallId(maj); return Triple("WALL GT$id", c.wallNotes[id] ?: "parete", pur) }
        if (!Label.isBox(maj) && !Label.isPanel(maj)) return Triple(Label.cls(maj), "altro", pur)
        val ax = if (abs(sf.plane.nz) > abs(sf.plane.nx)) "z" else "x"
        return Triple(Label.cls(maj), if (ax in c.frontAxis) "fronte" else "fianco", pur)
    }

    /** Punti misurati sulla parete GT dietro il fronte, entro ±0,6 m dall'estensione del fronte (verità: la parete è visibile?). */
    internal fun wallBehindVisible(run: BenchRun, sf: Surface): Int? {
        val c = sf.plane.centroid
        val h = kotlin.math.hypot(sf.plane.nx, sf.plane.nz); val nx = sf.plane.nx / h; val nz = sf.plane.nz / h
        val w = run.scenario.room.walls.filter { abs(it.nx * nx + it.nz * nz) > 0.95 }.mapNotNull { w ->
            val den = -(nx * w.nx + nz * w.nz); if (abs(den) < 1e-9) return@mapNotNull null
            val d = -w.dist(c[0], c[2]) / den
            if (d <= 0 || d > 2.5) null else w to d
        }.minByOrNull { it.second }?.first ?: return null
        val a = w.along(c[0] + sf.u[0] * sf.uMin, c[2] + sf.u[2] * sf.uMin); val b = w.along(c[0] + sf.u[0] * sf.uMax, c[2] + sf.u[2] * sf.uMax)
        val lo = minOf(a, b) - 0.6; val hi = maxOf(a, b) + 0.6
        var n = 0
        for (fr in run.sim.frames) for (i in fr.labels.indices) if (fr.labels[i] == Label.wall(w.id)) {
            val u = w.along(fr.hitXYZ[3 * i].toDouble(), fr.hitXYZ[3 * i + 2].toDouble()); if (u in lo..hi) n++
        }
        return n
    }

    private fun analyze(c: Case, run: BenchRun, tag: String, seed: Long, cell: Double = 0.05, only: ((Surface) -> Boolean)? = null, context: Boolean = true): List<Row> {
        val views = OcclusionAudit.views(run.map, 0.10)
        val g = OcclusionAudit.grid(run.map, run.surfaces, views, cell)
        val s = run.surfaces
        return s.surfaces.filter { it.orientation == Orientation.VERTICAL && it.areaM2 >= 0.25 && (only == null || only(it)) }.map { sf ->
            val (truth, role, pur) = truthOf(c, run, sf)
            val sc = FreeSpaceAudit.score(s, sf, sf.freeBehind ?: 0.0)
            val cand = context && sf.kind != SurfaceKind.VERTICAL_OBJECT
            val naive = if (cand) OcclusionAudit.context("ingenua", sf, run.walls, run.r4) else null
            val loo = if (cand) OcclusionAudit.leaveOneOut(run.map, s, run.sim.recording, sf).let { (w, r4) -> OcclusionAudit.context("leave-one-out", sf, w, r4) } else null
            Row(c, tag, seed, views.count, sf, truth, role, pur, sc, OcclusionAudit.backface(run.map, s, g, views, sf), if (role == "fronte" || role == "fianco") wallBehindVisible(run, sf) else null, naive, loo)
        }
    }

    private val metricsHeader = "gruppo;caso;titolo;esecuzione;seme;viste;superficie;verita;ruolo;purezza;areaM2;lunghezzaM;topDalPav;classeR2;punteggio;termEsterna;freeBehind;freeOfAll;frazioneOsservataDietro;" +
        "dietroColonne;dietroSuperficieOsservata;dietroDistanzaMedianaM;dietroContinuita;dietroLibera;dietroNonOsservata;dietroViste;dietroVisteSuperficie;volumeNonOsservatoM3;" +
        "sinistraArretrata;sinistraDistM;sinistraComplanare;destraArretrata;destraDistM;destraComplanare;sopraArretrata;sopraDistM;sopraComplanare;sopraColonne;bandaContinuita;bandaViste;" +
        "r2ParallelaDietro;r2DietroDistM;r2Adiacente;r2AdiacenteDistM;r2AdiacenteDove;fianchiVersoDietro;profonditaFianchiM;fianchiInAvanti;visteDelFronte;veritaPuntiPareteDietroVisibile;" +
        "E1_dietro;E2_adiacente;E3_fianchi;E4_perimetroLOO;E4_perimetroIngenuo;E4b_pareteR3AdiacenteDietroLOO"

    private fun metricsLine(r: Row): String {
        val b = r.bf; val bb = b.bestBand
        fun band(k: String) = b.bands.getValue(k).let { listOf(f(it.recessedFrac), f(it.recessedMedianD), f(it.coplanarFrac)) }
        return (listOf(r.case.group, r.case.sc.id, r.case.sc.title, r.tag, r.seed, r.views, "S${r.sf.id}", r.truth, r.role, f(r.purity), f(r.sf.areaM2, 2), f(r.sf.lengthM, 2), f(r.sf.topAboveFloor, 2),
            r.sf.kind, f(r.sc.score), f(r.sc.outermost, 0), f(b.freeBehind), f(b.freeOfAll), f(b.observedFraction),
            b.behindColumns, f(b.behindHitFrac), f(b.behindHitMedianD), f(b.behindContinuity), f(b.behindFreeFrac), f(b.behindUnobservedFrac), b.behindViews, b.behindHitViews, f(b.unobservedVolumeM3, 4)) +
            band("sinistra") + band("destra") + band("sopra") + listOf(b.bands.getValue("sopra").columns, f(bb?.continuity), bb?.views ?: 0,
            b.r2Behind?.let { "S$it" } ?: "", f(b.r2BehindD), b.r2Adjacent?.let { "S$it" } ?: "", f(b.r2AdjacentD), b.r2AdjacentWhere ?: "", b.sidesBack, f(b.sideBackDepthM), b.sidesForward, b.frontViews,
            r.wallBehindVisiblePts ?: "", r.e1, r.e2, r.e3, if (r.loo == null) "" else r.e4, if (r.naive == null) "" else r.e4naive, if (r.loo == null) "" else r.e4b))
            .joinToString(";") { it.toString().replace(";", ",") }
    }

    private val ctxHeader = "gruppo;caso;esecuzione;seme;superficie;verita;ruolo;classeR2;modo;pareteR3;statoR4;areaR4M2;ruoloCandidata;pareteParallela;offsetM(+ interno);lato;sovrapposizioneM;dentroPoligono;distanzaBordoM;continuitaConParete;intersezioni;conflitto;pareteR3AdiacenteDietro;distanzaM"
    private fun ctxLine(r: Row, c: OcclusionAudit.Context) = listOf(r.case.group, r.case.sc.id, r.tag, r.seed, "S${r.sf.id}", r.truth, r.role, r.sf.kind, c.mode, c.walls, c.r4State, f(c.areaM2, 2), c.role,
        c.parallelWall?.let { "W$it" } ?: "", f(c.offsetM), c.side, f(c.overlapM, 2), c.insidePolygon ?: "", f(c.boundaryDistM), c.continuityWall?.let { "W$it" } ?: "", c.intersects.joinToString(" ") { "W$it" }, c.conflict, c.adjacentWallBehind?.let { "W$it" } ?: "", f(c.adjacentWallBehindM))
        .joinToString(";") { it.toString().replace(";", ",") }

    // ------------------------------------------------------------------------------------------------ controfattuali

    private fun kindOf(r: Row, outermost: Double): SurfaceKind {
        val sc = r.sc.score - 0.20 * r.sc.outermost + 0.20 * outermost
        return when { r.sc.weak -> SurfaceKind.UNKNOWN; sc >= 0.6 -> SurfaceKind.VERTICAL_STRUCTURAL; sc < 0.5 -> SurfaceKind.VERTICAL_OBJECT; else -> SurfaceKind.UNKNOWN }
    }
    private val AMB = "AMBIGUOUS_VERTICAL_SURFACE"

    /** (strategia, descrizione, classe ipotetica). Nessuna di queste è applicata a R2: solo calcolo. */
    private fun counterfactuals(r: Row): List<Triple<String, String, String>> {
        val prod = r.sf.kind
        val st = prod == SurfaceKind.VERTICAL_STRUCTURAL
        return listOf(
            Triple("A0", "produzione (solo punteggio)", prod.name),
            Triple("B1", "esterna = 0 se E1 (superficie osservata dietro)", kindOf(r, if (r.e1 || r.sc.outermost == 0.0) 0.0 else 1.0).name),
            Triple("B2", "esterna = 0 se E1 o E2 (parallela adiacente arretrata)", kindOf(r, if (r.e1 || r.e2 || r.sc.outermost == 0.0) 0.0 else 1.0).name),
            Triple("B3", "esterna = 0 se E1, E2 o E3 (fianchi verso dietro)", kindOf(r, if (r.e1 || r.e2 || r.e3 || r.sc.outermost == 0.0) 0.0 else 1.0).name),
            Triple("C1", "STRUCTURAL con E1/E2/E3 → $AMB", if (st && (r.e1 || r.e2 || r.e3)) AMB else prod.name),
            Triple("D1", "STRUCTURAL con E4 o E4b leave-one-out → VERTICAL_OBJECT", if (st && r.e4x) SurfaceKind.VERTICAL_OBJECT.name else prod.name),
            Triple("D0", "STRUCTURAL con E4 INGENUO → VERTICAL_OBJECT (solo confronto)", if (st && r.e4naive) SurfaceKind.VERTICAL_OBJECT.name else prod.name),
            Triple("E1", "STRUCTURAL con (E2 o E3) e (E4 o E4b) leave-one-out → $AMB", if (st && (r.e2 || r.e3) && r.e4x) AMB else prod.name),
            Triple("E2", "STRUCTURAL con E1/E2/E3 o E4/E4b leave-one-out → $AMB", if (st && (r.e1 || r.e2 || r.e3 || r.e4x)) AMB else prod.name),
        )
    }

    // ------------------------------------------------------------------------------------------------------------ test

    @Test
    fun `leave-one-out toglie la candidata prima di R3 e il caso impossibile ha osservazioni identiche`() {
        val g = partG()
        val sims = g.map { SensorSim.simulate(it.sc.room, it.sc.stations, it.sc.sensor) }
        for (s in sims.drop(1)) for ((a, b) in sims[0].frames.zip(s.frames)) assertTrue(a.mm.contentEquals(b.mm) && a.confidence.contentEquals(b.confidence), "G: depth identica")
        val c = partC().first { it.sc.id == "O5" }
        val run = BenchPipeline.run(c.sc)
        val cand = run.surfaces.surfaces.filter { it.kind == SurfaceKind.VERTICAL_STRUCTURAL }.maxByOrNull { it.areaM2 }!!
        val (w, _) = OcclusionAudit.leaveOneOut(run.map, run.surfaces, run.sim.recording, cand)
        assertTrue(w.walls.none { cand.id in it.sourceSurfaceIds })
        assertEquals(0, run.trace.mismatches)
    }

    @Test
    fun `audit occlusione completo (solo con SAGOMA_OCCLUSION_OUT)`() {
        val out = File(System.getenv("SAGOMA_OCCLUSION_OUT") ?: return).also { it.mkdirs() }
        val zips = System.getenv("SAGOMA_OCCLUSION_ZIPS")?.split(';')?.filter { it.isNotBlank() }.orEmpty()
        val metrics = mutableListOf<String>(); val ctx = mutableListOf<String>(); val cf = mutableListOf<String>(); val sweep = mutableListOf<String>()
        val rowsC = mutableListOf<Row>()
        fun keep(rows: List<Row>, cfToo: Boolean) {
            for (r in rows) {
                metrics.add(metricsLine(r))
                listOfNotNull(r.naive, r.loo).forEach { ctx.add(ctxLine(r, it)) }
                if (cfToo) for ((id, d, k) in counterfactuals(r)) cf.add(listOf(r.case.group, r.case.sc.id, r.tag, r.seed, "S${r.sf.id}", r.truth, r.role, r.sf.kind, id, d, k).joinToString(";"))
            }
        }
        // C: scenari minimi, controlli e scenari del benchmark (invariati), 3 semi.
        for (c in partC() + bench()) for (seed in 1L..3L) {
            val run = BenchPipeline.run(c.sc, BenchParams(seed = seed))
            assertEquals(0, run.trace.mismatches)
            val rows = analyze(c, run, "${c.sc.id} s$seed", seed)
            rowsC.addAll(rows); keep(rows, true)
        }
        // G: il caso impossibile. Osservazioni identiche → nessun algoritmo può separarli.
        val gRows = mutableListOf<Row>(); val gNotes = mutableListOf<String>()
        val gCases = partG()
        val gSims = gCases.map { SensorSim.simulate(it.sc.room, it.sc.stations, it.sc.sensor) }
        for ((i, c) in gCases.withIndex()) {
            val diff = gSims[0].frames.zip(gSims[i].frames).sumOf { (a, b) -> a.mm.indices.count { a.mm[it] != b.mm[it] || a.confidence[it] != b.confidence[it] } }
            gNotes.add("${c.sc.id} (${c.sc.title}) contro G1: pixel di depth/confidenza diversi = $diff su ${gSims[0].frames.sumOf { it.mm.size }}")
            val run = BenchPipeline.run(c.sc)
            val rows = analyze(c, run, c.sc.id, 1L)
            gRows.addAll(rows); keep(rows, true)
        }
        run {
            val c2 = partC().first { it.sc.id == "C2" }; val c3 = partC().first { it.sc.id == "C3" }
            val a = SensorSim.simulate(c2.sc.room, c2.sc.stations, c2.sc.sensor); val b = SensorSim.simulate(c3.sc.room, c3.sc.stations, c3.sc.sensor)
            val diff = a.frames.zip(b.frames).sumOf { (x, y) -> x.mm.indices.count { x.mm[it] != y.mm[it] || x.confidence[it] != y.confidence[it] } }
            gNotes.add("C3 (armadio con l'impronta del pilastro) contro C2 (pilastro): pixel di depth/confidenza diversi = $diff su ${a.frames.sumOf { it.mm.size }}")
        }
        // E: viste multiple (1, 2, 4, 8 posizioni ad arco), 3 semi. Oggetti O5/O9/O10, pareti di controllo O0 (parete) e C2 (pilastro).
        val sweepCases = listOf(
            Triple(partC().first { it.sc.id == "O5" }, 2.395, Label.box(0)), Triple(partC().first { it.sc.id == "O9" }, 2.395, Label.box(0)),
            Triple(partC().first { it.sc.id == "O10" }, 2.395, Label.box(0)), Triple(partC().first { it.sc.id == "O0" }, 3.0, Label.wall(1)),
            Triple(partC().first { it.sc.id == "C2" }, 2.4, Label.wall(3)),
        )
        val sweepRows = mutableListOf<Row>()
        for ((c, fz, target) in sweepCases) for (n in listOf(1, 2, 4, 8)) for (seed in 1L..3L) {
            val sc = c.sc.copy(stations = arc(2.0, fz, n))
            val run = BenchPipeline.run(sc, BenchParams(seed = seed))
            val main = run.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL && abs(it.plane.nz) > 0.9 }
                .maxByOrNull { sf -> labels(run, sf)[target] ?: 0 }?.takeIf { (labels(run, it)[target] ?: 0) > 0 }
            if (main == null) { sweep.add(listOf(c.sc.id, n, seed, "nessuna superficie R2 del fronte").joinToString(";")); continue }
            val r = analyze(Case(sc, c.frontAxis, "E", c.wallNotes), run, "${c.sc.id} ${n}v s$seed", seed, only = { it.id == main.id }).single()
            sweepRows.add(r); metrics.add(metricsLine(r)); listOfNotNull(r.naive, r.loo).forEach { ctx.add(ctxLine(r, it)) }
            val b = r.bf
            sweep.add(listOf(c.sc.id, n, seed, "S${main.id}", r.truth, r.role, r.sf.kind, f(r.sc.score), f(r.sc.outermost, 0), f(b.behindHitFrac), b.behindViews,
                f(b.bands.getValue("sinistra").recessedFrac), f(b.bands.getValue("destra").recessedFrac), f(b.bands.getValue("sopra").recessedFrac), f(b.bestBand?.recessedMedianD), b.bestBand?.views ?: 0,
                b.sidesBack, f(b.sideBackDepthM), r.wallBehindVisiblePts ?: "", r.e1, r.e2, r.e3, r.loo?.side ?: "", r.e4, r.e4b).joinToString(";") { it.toString().replace(";", ",") })
        }
        // Dati reali: solo la distribuzione degli indicatori (nessuna verità). Griglia a 10 cm, viste = posizioni entro 0,5 m.
        val realRows = mutableListOf<String>()
        for (zip in zips) {
            val name = File(zip).nameWithoutExtension
            val ds = CaptureDatasetFiles.open(File(zip)); val blobs = DatasetBlobs { ds.read(it) }
            val map = GlobalMap.build(ds.recording, blobs); val s = SurfaceExtractor.extract(map, ds.recording)
            val w = WallEstimator.estimate(WallEstimator.inputFrom(map, s, ds.recording)); val r4 = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, s, w))
            val views = OcclusionAudit.views(map, 0.5); val g = OcclusionAudit.grid(map, s, views, 0.10)
            for (sf in s.surfaces.filter { it.orientation == Orientation.VERTICAL && it.areaM2 >= 0.3 && it.kind != SurfaceKind.VERTICAL_OBJECT }) {
                val c = Case(Scenario(name, "dati reali", GtRoom(name, Scenarios.rect()), emptyList(), noisy, ""), "", "reale")
                val r = Row(c, name, 0L, views.count, sf, "? (nessuna verità)", "?", 0.0, FreeSpaceAudit.score(s, sf, sf.freeBehind ?: 0.0), OcclusionAudit.backface(map, s, g, views, sf), null,
                    OcclusionAudit.context("ingenua", sf, w, r4), OcclusionAudit.leaveOneOut(map, s, ds.recording, sf).let { (w2, r42) -> OcclusionAudit.context("leave-one-out", sf, w2, r42) })
                metrics.add(metricsLine(r)); listOfNotNull(r.naive, r.loo).forEach { ctx.add(ctxLine(r, it)) }
                realRows.add(listOf(name, "S${sf.id}", sf.kind, f(sf.areaM2, 2), r.e1, r.e2, r.e3, r.loo?.side ?: "", r.naive?.side ?: "").joinToString(";"))
            }
            realRows.add("$name;viste=${views.count}${if (views.aliased) " (oltre 32: conteggio per difetto)" else ""};;;;;;;")
        }

        val sweepHeader = "caso;viste;seme;superficie;verita;ruolo;classeR2;punteggio;termEsterna;dietroSuperficieOsservata;dietroViste;sinistraArretrata;destraArretrata;sopraArretrata;bandaDistM;bandaViste;fianchiVersoDietro;profonditaFianchiM;veritaPuntiPareteDietroVisibile;E1;E2;E3;latoLOO;E4;E4b"
        val cfHeader = "gruppo;caso;esecuzione;seme;superficie;verita;ruolo;classeR2;strategia;descrizione;classeIpotetica"
        File(out, "occlusion-metrics.csv").writeText("$metricsHeader\n" + metrics.joinToString("\n") + "\n")
        File(out, "perimeter-context.csv").writeText("$ctxHeader\n" + ctx.joinToString("\n") + "\n")
        File(out, "counterfactuals.csv").writeText("$cfHeader\n" + cf.joinToString("\n") + "\n")
        File(out, "view-sweep.csv").writeText("$sweepHeader\n" + sweep.joinToString("\n") + "\n")
        val sep = separability(rowsC.filter { it.case.group != "benchmark" }, "C (O0–O12 + controlli)") + separability(rowsC, "C + benchmark S5/S8/S9/S17") + sweepSeparability(sweepRows)
        val cfSum = cfSummary(rowsC + gRows)
        File(out, "separability.csv").writeText("insieme;indicatore;gruppo;n;min;mediana;max;segnaleAcceso\n" + sep.joinToString("\n") + "\n")
        File(out, "counterfactual-summary.csv").writeText("strategia;descrizione;gruppo;n;STRUCTURAL;$AMB;UNKNOWN;VERTICAL_OBJECT\n" + cfSum.joinToString("\n") + "\n")
        fun arr(h: String, rows: List<String>) = rows.joinToString(",\n", "[", "]") { r -> val k = h.split(';'); val v = r.split(';'); "{" + k.indices.joinToString(",") { "\"${k[it]}\":\"${v.getOrElse(it) { "" }.replace("\\", "\\\\").replace("\"", "'")}\"" } + "}" }
        File(out, "occlusion-audit.json").writeText(
            "{\n\"note\":\"AUDIT DIAGNOSTICO occlusione: BACKFACE_EVIDENCE è un indicatore, non una classe. Nessuna modifica di produzione.\",\n\"casoImpossibile\":" +
                gNotes.joinToString(",", "[", "]") { "\"$it\"" } + ",\n\"separability\":" + arr("insieme;indicatore;gruppo;n;min;mediana;max;segnaleAcceso", sep) +
                ",\n\"counterfactualSummary\":" + arr("strategia;descrizione;gruppo;n;STRUCTURAL;$AMB;UNKNOWN;VERTICAL_OBJECT", cfSum) +
                ",\n\"viewSweep\":" + arr(sweepHeader, sweep) + ",\n\"real\":" + arr("dataset;superficie;classe;areaM2;E1;E2;E3;latoLOO;latoIngenuo", realRows) +
                ",\n\"metrics\":" + arr(metricsHeader, metrics) + ",\n\"perimeterContext\":" + arr(ctxHeader, ctx) + ",\n\"counterfactuals\":" + arr(cfHeader, cf) + "\n}\n",
        )
        File(out, "case-G.txt").writeText(gNotes.joinToString("\n") + "\n")
        File(out, "view-sweep.svg").writeText(sweepSvg(sweepRows))
        File(out, "separation.svg").writeText(scatterSvg(rowsC.filter { it.case.group != "benchmark" && it.purity >= 0.8 && (it.isWall || it.isFront) }))
    }

    // --------------------------------------------------------------------------------------------- riepiloghi

    /** Gruppi per la separabilità: pareti vere, fronti con parete dietro visibile (in parte), fronti con parete dietro NASCOSTA. */
    private fun groupOf(r: Row) = when {
        r.purity < 0.8 -> null
        r.isWall -> if (r.role == "parete") "parete" else "parete (controllo: ${r.role})"
        r.isFront && (r.wallBehindVisiblePts ?: 0) > 0 -> "fronte, parete dietro in parte visibile"
        r.isFront && r.wallBehindVisiblePts == 0 -> "fronte, parete dietro NASCOSTA"
        r.isFront -> "fronte senza parete dietro"
        else -> null
    }

    private fun separability(rows: List<Row>, set: String): List<String> {
        val out = mutableListOf<String>()
        val ind: List<Pair<String, (Row) -> Pair<Double, Boolean>>> = listOf(
            "punteggio R2 (≥0,6 = STRUCTURAL)" to { r -> r.sc.score to (r.sf.kind == SurfaceKind.VERTICAL_STRUCTURAL) },
            "dietro: frazione con superficie osservata (E1 ≥ 0,2 a ≥ 15 cm)" to { r -> r.bf.behindHitFrac to r.e1 },
            "dietro: frazione non osservata" to { r -> r.bf.behindUnobservedFrac to (r.bf.behindUnobservedFrac >= 0.8) },
            "volume non osservato m³ (≥ 0,1)" to { r -> r.bf.unobservedVolumeM3 to (r.bf.unobservedVolumeM3 >= 0.1) },
            "free-space dietro (freeBehind ≥ 0,3)" to { r -> r.bf.freeBehind to (r.bf.freeBehind >= 0.3) },
            "adiacente arretrata: miglior banda (E2)" to { r -> (r.bf.bestBand?.recessedFrac ?: 0.0) to r.e2 },
            "fianchi verso dietro (E3)" to { r -> r.bf.sidesBack.toDouble() to r.e3 },
            "perimetro LOO: offset dalla parete R3 parallela (E4 = interno)" to { r -> (r.loo?.offsetM ?: Double.NaN) to r.e4 },
            "perimetro INGENUO: offset (solo confronto)" to { r -> (r.naive?.offsetM ?: Double.NaN) to r.e4naive },
            "E1 o E2 o E3" to { r -> 0.0 to (r.e1 || r.e2 || r.e3) },
            "perimetro LOO: parete R3 parallela dietro e accanto (E4b)" to { r -> (r.loo?.adjacentWallBehindM ?: Double.NaN) to r.e4b },
            "E4 o E4b" to { r -> 0.0 to r.e4x },
            "(E2 o E3) e E4" to { r -> 0.0 to ((r.e2 || r.e3) && r.e4) },
            "(E2 o E3) e (E4 o E4b)" to { r -> 0.0 to ((r.e2 || r.e3) && r.e4x) },
        )
        val groups = rows.mapNotNull { r -> groupOf(r)?.let { it to r } }.groupBy({ it.first }, { it.second }).toSortedMap()
        for ((name, fn) in ind) for ((gname, g) in groups) {
            val v = g.map(fn); val xs = v.map { it.first }.filter { !it.isNaN() }.sorted()
            out.add(listOf(set, name, gname, g.size, f(xs.firstOrNull()), f(xs.getOrNull(xs.size / 2)), f(xs.lastOrNull()), "${v.count { it.second }}/${g.size}").joinToString(";"))
        }
        return out
    }

    private fun sweepSeparability(rows: List<Row>): List<String> = rows.groupBy { "${it.case.sc.id} ${it.views} viste" }.toSortedMap().flatMap { (k, g) ->
        listOf(
            "E (viste) $k;E1 o E2 o E3;${g.first().truth};${g.size};;;;${g.count { it.e1 || it.e2 || it.e3 }}/${g.size}",
            "E (viste) $k;E4 o E4b perimetro LOO;${g.first().truth};${g.size};;;;${g.count { it.e4x }}/${g.size}",
        )
    }

    private fun cfSummary(rows: List<Row>): List<String> {
        val out = mutableListOf<String>()
        val byGroup = rows.mapNotNull { r -> (if (r.case.group == "G") "G: ${r.case.sc.id} ${r.case.sc.title} (${r.truth})" else groupOf(r))?.let { it to r } }
            .filter { it.second.purity >= 0.8 }.groupBy({ it.first }, { it.second }).toSortedMap()
        val ids = counterfactuals(rows.first()).map { it.first to it.second }
        for ((id, d) in ids) for ((gname, g) in byGroup) {
            val ks = g.map { r -> counterfactuals(r).first { it.first == id }.third }
            out.add(listOf(id, d, gname, g.size, ks.count { it == "VERTICAL_STRUCTURAL" }, ks.count { it == AMB }, ks.count { it == "UNKNOWN" }, ks.count { it == "VERTICAL_OBJECT" }).joinToString(";"))
        }
        return out
    }

    private fun sweepSvg(rows: List<Row>): String {
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="820" height="460" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="14" font-weight="bold">Viste multiple: frazione della miglior banda adiacente con parete ARRETRATA osservata (media di 3 semi)</text>""")
        val x0 = 70.0; val y0 = 400.0; val w = 520.0; val h = 330.0
        val xs = listOf(1, 2, 4, 8)
        fun px(n: Int) = x0 + w * xs.indexOf(n) / 3.0
        sb.append("""<line x1="$x0" y1="$y0" x2="${x0 + w}" y2="$y0" stroke="#000"/><line x1="$x0" y1="$y0" x2="$x0" y2="${y0 - h}" stroke="#000"/>""")
        for (n in xs) sb.append("""<text x="${f(px(n) - 14, 1)}" y="${y0 + 18}">$n vist${if (n == 1) "a" else "e"}</text>""")
        for (p in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) sb.append("""<text x="30" y="${f(y0 - p * h + 4, 1)}">${(p * 100).toInt()}%</text><line x1="$x0" y1="${f(y0 - p * h, 1)}" x2="${x0 + w}" y2="${f(y0 - p * h, 1)}" stroke="#EEE"/>""")
        val cols = listOf("O5" to "#C62828", "O9" to "#6A1B9A", "O10" to "#EF6C00", "O0" to "#1565C0", "C2" to "#2E7D32")
        for ((i, pair) in cols.withIndex()) {
            val (id, col) = pair
            val pts = rows.filter { it.case.sc.id == id }.groupBy { it.views }.toSortedMap().map { (n, g) -> n to g.map { it.bf.bestBand?.recessedFrac ?: 0.0 }.average() }
            sb.append("""<polyline fill="none" stroke="$col" stroke-width="2" points="${pts.joinToString(" ") { "${f(px(it.first), 1)},${f(y0 - it.second * h, 1)}" }}"/>""")
            for ((n, y) in pts) sb.append("""<circle cx="${f(px(n), 1)}" cy="${f(y0 - y * h, 1)}" r="4" fill="$col"/>""")
            val lbl = mapOf("O5" to "O5 mobile aderente", "O9" to "O9 armadio parete-parete tutta altezza", "O10" to "O10 armadio 2 m, parete 3 m", "O0" to "O0 parete vera", "C2" to "C2 pilastro (parete vera)")
            sb.append("""<rect x="610" y="${60 + 20 * i}" width="12" height="4" fill="$col"/><text x="627" y="${66 + 20 * i}">${lbl[id]}</text>""")
        }
        sb.append("""<text x="610" y="180" fill="#555">la parete vera O0 resta a 0;</text><text x="610" y="196" fill="#555">il pilastro C2 si comporta</text><text x="610" y="212" fill="#555">come un mobile aderente</text></svg>""")
        return sb.toString()
    }

    private fun scatterSvg(rows: List<Row>): String {
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="820" height="480" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="14" font-weight="bold">Punteggio R2 contro evidenza adiacente arretrata (miglior banda) — pareti e fronti di oggetti</text>""")
        val x0 = 70.0; val y0 = 420.0; val w = 520.0; val h = 360.0
        sb.append("""<line x1="$x0" y1="$y0" x2="${x0 + w}" y2="$y0" stroke="#000"/><line x1="$x0" y1="$y0" x2="$x0" y2="${y0 - h}" stroke="#000"/>""")
        for (p in listOf(0.0, 0.5, 1.0)) sb.append("""<text x="${f(x0 + p * w - 8, 1)}" y="${y0 + 16}">${f(p, 1)}</text>""")
        for (p in listOf(0.3, 0.5, 0.6, 0.8, 1.0)) sb.append("""<text x="30" y="${f(y0 - (p - 0.3) / 0.7 * h + 4, 1)}">${f(p, 1)}</text><line x1="$x0" y1="${f(y0 - (p - 0.3) / 0.7 * h, 1)}" x2="${x0 + w}" y2="${f(y0 - (p - 0.3) / 0.7 * h, 1)}" stroke="${if (p == 0.6) "#999" else "#EEE"}"/>""")
        for (r in rows) {
            val g = groupOf(r) ?: continue
            val col = when { g == "parete" -> "#1565C0"; g.startsWith("parete") -> "#2E7D32"; g.contains("NASCOSTA") -> "#6A1B9A"; else -> "#C62828" }
            val x = x0 + (r.bf.bestBand?.recessedFrac ?: 0.0) * w; val y = y0 - ((r.sc.score - 0.3) / 0.7).coerceIn(0.0, 1.0) * h
            sb.append("""<circle cx="${f(x, 1)}" cy="${f(y, 1)}" r="4" fill="$col" fill-opacity="0.6"><title>${r.tag} S${r.sf.id} ${r.truth} ${r.role}</title></circle>""")
        }
        val leg = listOf("#1565C0" to "parete vera", "#2E7D32" to "parete vera di controllo (pilastro, rientranza)", "#C62828" to "fronte, parete dietro in parte visibile", "#6A1B9A" to "fronte, parete dietro NASCOSTA")
        for ((i, l) in leg.withIndex()) sb.append("""<circle cx="616" cy="${62 + 20 * i}" r="5" fill="${l.first}"/><text x="627" y="${66 + 20 * i}">${l.second}</text>""")
        sb.append("""<text x="${x0 + w / 2 - 80}" y="${y0 + 36}">frazione di colonne adiacenti con parete arretrata</text><text x="10" y="${y0 - h - 10}">punteggio R2</text></svg>""")
        return sb.toString()
    }
}
