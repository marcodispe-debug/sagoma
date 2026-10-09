package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.DatasetBlobs
import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.PerimeterSolver
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.Surface
import com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimator
import com.sagoma.planimetria.scan.reconstruction.WallInput
import com.sagoma.planimetria.scan.reconstruction.WallInputSurface
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AUDIT OFFLINE DELLA SPECIFICA R2.1 (solo test, nessuna modifica di produzione). Applica in memoria i criteri candidati di
 * `VERTICAL_AMBIGUOUS` (specification.md: soglie fissate PRIMA dell'audit, nessuna ottimizzata) alle superfici di R2 di produzione,
 * misura la matrice per categoria, la sensibilità alle soglie, la stabilità togliendo una vista, le opzioni del contratto R3/R4
 * (A esclusa, D separata, B peso ridotto APPROSSIMATO; C non simulabile) e i conteggi del contratto RescanDirector.
 * Con SAGOMA_R21_OUT=<cartella> (e SAGOMA_R21_ZIPS=<zip1;zip2> per le sole distribuzioni sui dati reali) scrive CSV, JSON, SVG.
 */
class R21SpecAuditTest {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else "%.${d}f".format(Locale.ROOT, v)
    private val occ = OcclusionAuditTest()

    // ---------------------------------------------------------------------------------------- soglie (fissate a priori)
    private val dMinE1 = 0.15
    private val covT = 0.30
    private val contT = 0.50
    private val tau = 0.15
    private val minViews = 2
    private val tauSweep = listOf(0.05, 0.10, 0.15, 0.20, 0.30, 0.45)
    private val covSweep = listOf(0.15, 0.30, 0.45)

    // -------------------------------------------------------------------------------------------------------- casi
    class SpecCase(val case: OcclusionAuditTest.Case, val set: String)

    private val sAxis = mapOf("S5" to "z", "S6" to "z", "S7" to "x", "S8" to "x", "S9" to "xz", "S10" to "z", "S13" to "xz", "S17" to "z")
    private val gapCat = mapOf(
        "O5" to "aderente", "O7" to "aderente", "O8" to "aderente", "O9" to "aderente", "O10" to "aderente", "O11" to "aderente", "O12" to "aderente",
        "S5" to "aderente", "S9" to "aderente", "M5" to "aderente", "M6" to "aderente",
        "O1" to "distante", "O2" to "distante", "O3" to "distante", "O4" to "distante", "S6" to "distante", "S7" to "distante", "S8" to "distante", "S17" to "distante",
        "O6" to "isolato", "S13" to "isolato",
    )
    private val panelCat = mapOf("S10" to "pannello parallelo", "M1" to "pannello parallelo", "M2" to "pannello parallelo", "M3" to "pannello parallelo", "M4" to "pannello aderente", "M7" to "pannello isolato")

    private fun cases(): List<SpecCase> {
        val out = mutableListOf<SpecCase>()
        for (s in Scenarios.all()) out.add(SpecCase(OcclusionAuditTest.Case(s, sAxis[s.id] ?: "", "S", if (s.id == "S11") mapOf(1 to "r-accanto", 3 to "r-fondo", 5 to "r-accanto", 2 to "r-lato", 4 to "r-lato") else emptyMap()), if (s.id == "S11") "verifica" else "definizione"))
        val c = occ.partC()
        for (k in c) {
            val notes = when (k.sc.id) { "C1" -> mapOf(1 to "r-accanto", 3 to "r-fondo", 5 to "r-accanto", 2 to "r-lato", 4 to "r-lato"); "C2" -> mapOf(3 to "p-fronte", 2 to "p-fianco", 4 to "p-fianco"); else -> emptyMap() }
            out.add(SpecCase(OcclusionAuditTest.Case(k.sc, k.frontAxis, k.group, notes), if (k.sc.id.startsWith("C")) "verifica" else "definizione"))
        }
        for (m in FurnitureAuditTest().minimal(occ.noisy)) out.add(SpecCase(OcclusionAuditTest.Case(m, if (m.id == "M6") "xz" else "z", "M"), "definizione"))
        val g = occ.partG()
        fun ren(k: OcclusionAuditTest.Case, id: String, title: String) = OcclusionAuditTest.Case(k.sc.copy(id = id, title = title), k.frontAxis, "G")
        out.add(SpecCase(ren(g[0], "G0", "parete di riferimento"), "verifica"))
        out.add(SpecCase(ren(g[1], "G1", "armadio aderente a tutta larghezza e altezza"), "verifica"))
        out.add(SpecCase(ren(g[2], "G2", "pannello strutturale indistinguibile"), "verifica"))
        out.add(SpecCase(ren(g[3], "G3", "divisorio senza evidenza sui due lati"), "verifica"))
        out.add(SpecCase(ren(c.first { it.sc.id == "O9" }, "G4", "parete dietro un mobile completamente nascosta"), "verifica"))
        return out
    }

    /** Categoria del punto 5 della richiesta (dalla verità del simulatore, mai usata dai criteri). */
    private fun category(c: OcclusionAuditTest.Case, truth: String, role: String): String {
        val id = c.sc.id
        if (id.startsWith("G") && id != "G0" && !truth.startsWith("WALL")) return "G: indistinguibile ($id)"
        if (id == "G4" && truth.startsWith("WALL")) return "muro normale"
        if (truth.startsWith("WALL")) return when (role) {
            "r-accanto" -> "rientranza (pareti accanto)"; "r-fondo" -> "rientranza (fondo)"; "r-lato" -> "rientranza (lati)"
            "p-fronte" -> "pilastro (fronte)"; "p-fianco" -> "pilastro (fianchi)"
            else -> if (id == "G0" && truth == "WALL GT1") "G0 parete di riferimento" else "muro normale"
        }
        if (role == "fianco") return "fianco di mobile"
        if (role != "fronte") return "altro"
        if (id == "C3") return "mobile con l'impronta del pilastro"
        panelCat[id]?.let { return it }
        return gapCat[id]?.let { "mobile $it" } ?: "mobile (altro)"
    }
    private fun isWallCat(k: String) = k == "muro normale" || k.startsWith("pilastro") || k.startsWith("rientranza") || k.startsWith("G0")
    private fun isFurnCat(k: String) = k.startsWith("mobile") || k.startsWith("pannello") || k == "fianco di mobile"

    // ---------------------------------------------------------------------------------------------- per superficie

    class Ch(val coverage: Double, val continuity: Double, val distM: Double?, val views: Int)

    class SRow(
        val caseId: String, val title: String, val set: String, val seed: Long, val views: Int, val sf: Surface, val truth: String, val role: String, val category: String,
        val purity: Double, val fullHeight: Boolean, val sc: FreeSpaceAudit.Score, val quality: String, val frontViews: Int,
        val e1: Ch, val e4: Ch, val e5l: Ch, val e5r: Ch, val e2: Int?, val e2d: Double?, val sidesBack: Int, val sidesForward: Int,
        val behindUnobserved: Double, val bandUnobserved: Double, val score: Double, val lovoMin: Double, val lovoN: Int, val inR3: Boolean, val wallBehindVisible: Int?,
    ) {
        val noBackface get() = score < 0.05 && behindUnobserved >= 0.8
    }

    private fun ch(b: OcclusionAudit.Band) = Ch(b.recessedFrac, b.continuity ?: 0.0, b.recessedMedianD, b.views)
    private fun e1Of(b: OcclusionAudit.Backface) = Ch(if ((b.behindHitMedianD ?: 0.0) >= dMinE1) b.behindHitFrac else 0.0, b.behindContinuity ?: 0.0, b.behindHitMedianD, b.behindHitViews)
    private fun scoreOf(b: OcclusionAudit.Backface) = listOf(e1Of(b), ch(b.bands.getValue("sopra")), ch(b.bands.getValue("sinistra")), ch(b.bands.getValue("destra"))).maxOf { it.coverage * it.continuity }

    private fun rowsOf(sc: SpecCase, map: GlobalMap, s: SurfaceResult, run: BenchRun?, seed: Long, radius: Double, cell: Double, only: ((Surface) -> Boolean)? = null, minArea: Double = 0.25): List<SRow> {
        val views = OcclusionAudit.views(map, radius)
        val g = OcclusionAudit.grid(map, s, views, cell)
        val floorY = s.floor?.y; val ceilH = if (s.ceilingY != null && floorY != null) s.ceilingY!! - floorY else null
        val inR3 = run?.walls?.walls?.flatMap { it.sourceSurfaceIds }?.toSet() ?: emptySet()
        return s.surfaces.filter { it.orientation == Orientation.VERTICAL && it.areaM2 >= minArea && (only == null || only(it)) }.map { sf ->
            val (truth, role, pur) = if (run != null) occ.truthOf(sc.case, run, sf) else Triple("?", "?", 0.0)
            val b = OcclusionAudit.backface(map, s, g, views, sf)
            val score = scoreOf(b)
            var lovoMin = score; var lovoN = 0
            if (score >= 0.05) for (v in 0 until minOf(views.count, 32)) { lovoN++; lovoMin = minOf(lovoMin, scoreOf(OcclusionAudit.backface(map, s, g, views, sf, 1 shl v))) }
            val fsc = FreeSpaceAudit.score(s, sf, sf.freeBehind ?: 0.0)
            val quality = if (fsc.weak || b.frontViews < minViews || sf.areaM2 < 0.5) "LOW" else "HIGH"
            val bands = b.bands.values.filter { it.columns > 0 }
            SRow(sc.case.sc.id, sc.case.sc.title, sc.set, seed, views.count, sf, truth, role, if (run != null) category(sc.case, truth, role) else "?",
                pur, ceilH != null && (sf.topAboveFloor ?: 0.0) >= ceilH - 0.25, fsc, quality, b.frontViews,
                e1Of(b), ch(b.bands.getValue("sopra")), ch(b.bands.getValue("sinistra")), ch(b.bands.getValue("destra")), b.r2Adjacent, b.r2AdjacentD, b.sidesBack, b.sidesForward,
                b.behindUnobservedFrac, if (bands.isEmpty()) 0.0 else bands.maxOf { it.noneFrac }, score, lovoMin, lovoN, sf.id in inR3,
                if (run != null && (role == "fronte" || role == "fianco")) occ.wallBehindVisible(run, sf) else null)
        }
    }

    // ------------------------------------------------------------------------------------------------------ criteri

    private fun on(c: Ch, cov: Double = covT) = c.coverage >= cov && c.continuity >= contT
    private val AMB = "VERTICAL_AMBIGUOUS"

    /** Classe R2.1 ipotetica: solo una STRUCTURAL può diventare AMBIGUOUS; le altre classi restano quelle di R2. */
    private fun classify(r: SRow, cand: String, p: Double? = null): String {
        if (r.sf.kind != SurfaceKind.VERTICAL_STRUCTURAL) return r.sf.kind.name
        val amb = when (cand) {
            "P0" -> false
            "A" -> { val cv = p ?: covT; on(r.e1, cv) || r.e2 != null || on(r.e4, cv) || on(r.e5l, cv) || on(r.e5r, cv) }
            "B" -> on(r.e1) || on(r.e4)
            "C" -> listOf(r.e1, r.e4, r.e5l, r.e5r).any { on(it) && it.views >= minViews }
            "D" -> r.score >= (p ?: tau)
            "E" -> r.score >= (p ?: tau) && r.lovoMin >= (p ?: tau) && r.views >= minViews
            else -> error(cand)
        }
        return if (amb) AMB else r.sf.kind.name
    }
    private val candidates: List<Triple<String, Double?, String>> get() =
        listOf(Triple("P0", null, "produzione R2 (riferimento)")) +
            covSweep.map { Triple("A", it, "E1/E2/E4/E5 accesi (copertura ≥ ${f(it, 2)})") } +
            listOf(Triple("B", null, "solo E1/E4"), Triple("C", null, "E1/E4/E5 accesi da ≥ $minViews viste")) +
            tauSweep.map { Triple("D", it, "ambiguityScore ≥ ${f(it, 2)}") } + tauSweep.map { Triple("E", it, "score ≥ ${f(it, 2)} e stabile togliendo una vista") }

    // ------------------------------------------------------------------------------------------------------ test

    @Test
    fun `R2_1 in memoria non produce mai STRUCTURAL verso OBJECT e i casi G ricevono la stessa classe`() {
        val g = cases().filter { it.case.sc.id.startsWith("G") && it.case.sc.id != "G4" }
        val res = g.map { sc ->
            val run = BenchPipeline.run(sc.case.sc)
            val rows = rowsOf(sc, run.map, run.surfaces, run, 1L, 0.10, 0.05)
            val main = rows.filter { abs(it.sf.plane.nz) > 0.9 && it.sf.plane.centroid[2] > 2.0 }.maxByOrNull { it.sf.areaM2 }!!
            for (r in rows) for ((c, p, _) in candidates) assertTrue(!(r.sf.kind == SurfaceKind.VERTICAL_STRUCTURAL && classify(r, c, p) == SurfaceKind.VERTICAL_OBJECT.name))
            candidates.map { (c, p, _) -> classify(main, c, p) }
        }
        for (r in res) assertEquals(res[0], r, "G0–G3: osservazioni identiche → stessa classe per ogni candidato")
    }

    @Test
    fun `audit offline della specifica R2_1 (solo con SAGOMA_R21_OUT)`() {
        val out = File(System.getenv("SAGOMA_R21_OUT") ?: return).also { it.mkdirs() }
        val zips = System.getenv("SAGOMA_R21_ZIPS")?.split(';')?.filter { it.isNotBlank() }.orEmpty()
        val rows = mutableListOf<SRow>(); val r3rows = mutableListOf<String>()
        for (sc in cases()) for (seed in 1L..3L) {
            val run = BenchPipeline.run(sc.case.sc, BenchParams(seed = seed))
            assertEquals(0, run.trace.mismatches)
            val rr = rowsOf(sc, run.map, run.surfaces, run, seed, 0.10, 0.05)
            rows.addAll(rr)
            r3rows.addAll(r3Options(sc, run, rr, seed))
        }
        // Viste: stabilità e crescita dell'evidenza con 1/2/4/8 posizioni (casi dell'audit occlusione).
        val sweep = mutableListOf<SRow>()
        val sw = listOf(Triple("O5", 2.395, Label.box(0)), Triple("O9", 2.395, Label.box(0)), Triple("O10", 2.395, Label.box(0)), Triple("O0", 3.0, Label.wall(1)), Triple("C2", 2.4, Label.wall(3)))
        for ((id, fz, target) in sw) for (n in listOf(1, 2, 4, 8)) for (seed in 1L..3L) {
            val base = cases().first { it.case.sc.id == id }
            val sc = SpecCase(OcclusionAuditTest.Case(base.case.sc.copy(stations = occ.arc(2.0, fz, n)), base.case.frontAxis, "E", base.case.wallNotes), "viste")
            val run = BenchPipeline.run(sc.case.sc, BenchParams(seed = seed))
            val main = run.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL && abs(it.plane.nz) > 0.9 }
                .maxByOrNull { occ.labels(run, it)[target] ?: 0 }?.takeIf { (occ.labels(run, it)[target] ?: 0) > 0 } ?: continue
            sweep.addAll(rowsOf(sc, run.map, run.surfaces, run, seed, 0.10, 0.05, only = { it.id == main.id }, minArea = 0.0))
        }
        // Dati reali: solo distribuzioni. NO SEMANTIC GROUND TRUTH.
        val real = mutableListOf<SRow>()
        for (zip in zips) {
            val name = File(zip).nameWithoutExtension
            val ds = CaptureDatasetFiles.open(File(zip)); val blobs = DatasetBlobs { ds.read(it) }
            val map = GlobalMap.build(ds.recording, blobs); val s = SurfaceExtractor.extract(map, ds.recording)
            val sc = SpecCase(OcclusionAuditTest.Case(Scenario(name, "dati reali", GtRoom(name, Scenarios.rect()), emptyList(), occ.noisy, ""), "", "reale"), "reale")
            real.addAll(rowsOf(sc, map, s, null, 0L, 0.5, 0.10, minArea = 0.3))
        }
        write(out, rows, sweep, real, r3rows)
    }

    // ------------------------------------------------------------------------------------------- opzioni R3/R4

    private fun evalLine(sc: SpecCase, seed: Long, option: String, run: BenchRun, amb: Set<Int>, w: com.sagoma.planimetria.scan.reconstruction.WallEstimationResult, r4: com.sagoma.planimetria.scan.reconstruction.PerimeterResult): String {
        val m = BenchEval.evaluate(BenchRun(run.scenario, run.params, run.sim, run.map, run.surfaces, w, r4, run.trace, run.pointLabels, run.r1))
        val det = m.r3.walls.count { it.detected }
        return listOf(sc.case.sc.id, sc.set, seed, option, amb.sorted().joinToString(" ") { "S$it" }, w.walls.size, det, m.r3.walls.size, m.r3.falseWalls.size,
            f(m.global.wallRecall), f(m.global.wallPrecision), r4.state, m.r4.correct, m.r4.wrong, m.r4.missed, f(m.global.meanGeomErrorM)).joinToString(";")
    }

    /** Opzioni del contratto R3/R4 per le superfici AMBIGUOUS del candidato D (τ = 0,15). C non è simulabile. */
    private fun r3Options(sc: SpecCase, run: BenchRun, rr: List<SRow>, seed: Long): List<String> {
        val amb = rr.filter { classify(it, "D") == AMB }.map { it.sf.id }.toSet()
        val out = mutableListOf(evalLine(sc, seed, "base (R2 di produzione)", run, amb, run.walls, run.r4))
        if (amb.isEmpty()) return out
        val s = run.surfaces
        val s2 = SurfaceResult(s.surfaces.filter { it.id !in amb }, s.floor, s.ceilingY, s.cameraMedianY, s.voxelsWithNormal, s.voxelsAssigned, s.pointsAssigned, s.pointsTotal, s.arcore, s.voxelSurface, s.noiseEstimateM, s.thresholds)
        val wA = WallEstimator.estimate(WallEstimator.inputFrom(run.map, s2, run.sim.recording)); val r4A = PerimeterSolver.solve(PerimeterSolver.inputFrom(run.map, s2, wA))
        out.add(evalLine(sc, seed, "A esclusa", run, amb, wA, r4A))
        out.add(evalLine(sc, seed, "D separata (R3/R4 = A)", run, amb, wA, r4A))
        val wi = WallEstimator.inputFrom(run.map, s, run.sim.recording)
        for (ws in wi.surfaces) if (ws.id in amb) for (p in ws.points) wi.points.weight[p] *= 0.25
        val wiB = WallInput(wi.points, wi.surfaces.map { if (it.id in amb) WallInputSurface(it.id, it.kind, it.orientation, it.kindConfidence * 0.5, it.points) else it }, wi.floorY, wi.space, wi.groupCamera)
        val wB = WallEstimator.estimate(wiB); val r4B = PerimeterSolver.solve(PerimeterSolver.inputFrom(run.map, s, wB))
        out.add(evalLine(sc, seed, "B peso ridotto (APPROSSIMATO)", run, amb, wB, r4B))
        return out
    }

    // ------------------------------------------------------------------------------------------------------ output

    private fun ch(c: Ch) = listOf(f(c.coverage), f(c.continuity), f(c.distM), c.views)

    private val surfHeader = "insieme;caso;titolo;seme;viste;superficie;verita;ruolo;categoria;purezza;areaM2;lunghezzaM;topDalPav;tuttaAltezza;classeR2;structuralScore;evidenzaDebole;evidenceQuality;visteFronte;" +
        "E1cop;E1cont;E1distM;E1viste;E4cop;E4cont;E4distM;E4viste;E5Lcop;E5Lcont;E5LdistM;E5Lviste;E5Rcop;E5Rcont;E5RdistM;E5Rviste;E2superficie;E2distM;E3fianchiDietro;fianchiAvanti;" +
        "dietroNonOsservato;bandaNonOsservataMax;NO_BACKFACE_INFORMATION;ambiguityScore;stabilitaMinTogliUnaVista;visteTolte;fonteDiPareteR3;veritaPuntiPareteDietro;" +
        "P0;A;B;C;D;E"

    private fun surfLine(r: SRow) = (listOf(r.set, r.caseId, r.title, r.seed, r.views, "S${r.sf.id}", r.truth, r.role, r.category, f(r.purity), f(r.sf.areaM2, 2), f(r.sf.lengthM, 2), f(r.sf.topAboveFloor, 2), r.fullHeight,
        r.sf.kind, f(r.sc.score), r.sc.weak, r.quality, r.frontViews) + ch(r.e1) + ch(r.e4) + ch(r.e5l) + ch(r.e5r) +
        listOf(r.e2?.let { "S$it" } ?: "", f(r.e2d), r.sidesBack, r.sidesForward, f(r.behindUnobserved), f(r.bandUnobserved), r.noBackface, f(r.score), f(r.lovoMin), r.lovoN, r.inR3, r.wallBehindVisible ?: "") +
        listOf("P0", "A", "B", "C", "D", "E").map { classify(r, it) }).joinToString(";") { it.toString().replace(";", ",") }

    private fun write(out: File, rows: List<SRow>, sweep: List<SRow>, real: List<SRow>, r3rows: List<String>) {
        val clean = rows.filter { it.purity >= 0.8 && it.category != "altro" }
        val cats = clean.map { it.category }.distinct().sorted()
        val kinds = listOf("VERTICAL_STRUCTURAL", AMB, "VERTICAL_OBJECT", "UNKNOWN")

        // Matrice per categoria.
        val matrix = mutableListOf<String>()
        for ((c, p, d) in candidates) for (set in listOf("tutti", "definizione", "verifica")) for (k in cats) {
            val g = clean.filter { it.category == k && (set == "tutti" || it.set == set) }; if (g.isEmpty()) continue
            val cl = g.map { classify(it, c, p) }
            matrix.add((listOf(c, f(p, 2), d, set, k, g.size) + kinds.map { kk -> cl.count { it == kk } }).joinToString(";"))
        }

        // Confronto dei candidati: primario, secondario, terziario, sicurezza, accoppiamento con la qualità.
        val comp = mutableListOf<String>()
        for ((c, p, d) in candidates) for (set in listOf("tutti", "definizione", "verifica")) {
            val g = clean.filter { set == "tutti" || it.set == set }
            val furn = g.filter { isFurnCat(it.category) }; val walls = g.filter { isWallCat(it.category) }
            fun cnt(l: List<SRow>, k: String) = l.count { classify(it, c, p) == k }
            val wallsNorm = walls.filter { it.category == "muro normale" || it.category.startsWith("G0") }
            val wallsPil = walls.filter { it.category.startsWith("pilastro") }; val wallsRec = walls.filter { it.category.startsWith("rientranza") }
            val low = walls.filter { it.quality == "LOW" }; val high = walls.filter { it.quality == "HIGH" }
            val gInd = g.filter { it.category.startsWith("G: ") }
            comp.add(listOf(c, f(p, 2), d, set, furn.size, cnt(furn, "VERTICAL_STRUCTURAL"), cnt(furn, AMB), cnt(furn, "VERTICAL_OBJECT"), cnt(furn, "UNKNOWN"),
                walls.size, cnt(walls, "VERTICAL_OBJECT"), cnt(walls, "VERTICAL_OBJECT") - walls.count { it.sf.kind == SurfaceKind.VERTICAL_OBJECT },
                cnt(wallsNorm, AMB), wallsNorm.size, cnt(wallsPil, AMB), wallsPil.size, cnt(wallsRec, AMB), wallsRec.size,
                f(cnt(walls, "VERTICAL_STRUCTURAL").toDouble() / maxOf(1, walls.size)), f((cnt(walls, "VERTICAL_STRUCTURAL") + cnt(walls, AMB)).toDouble() / maxOf(1, walls.size)),
                "${cnt(low, AMB)}/${low.size}", "${cnt(high, AMB)}/${high.size}", "${cnt(gInd, "VERTICAL_STRUCTURAL")}/${gInd.size}").joinToString(";"))
        }
        val compHeader = "candidato;parametro;descrizione;insieme;superficiMobile;PRIMARIO mobileSTRUCTURAL;mobileAMBIGUOUS;mobileOBJECT;mobileUNKNOWN;pareti;pareteOBJECT;SECONDARIO pareteOBJECT in piu rispetto a R2;" +
            "TERZIARIO muroNormaleAMBIGUOUS;muriNormali;pilastroAMBIGUOUS;pilastro;rientranzaAMBIGUOUS;rientranza;SICUREZZA recallSTRUCTURAL;recallSTRUCTURAL+AMBIGUOUS;pareteAMBIGUOUS con qualita LOW;pareteAMBIGUOUS con qualita HIGH;G1-G4 STRUCTURAL"

        // Feature per categoria e confronto mobile aderente / pilastro / rientranza.
        val feats: List<Pair<String, (SRow) -> Double?>> = listOf(
            "ambiguityScore" to { r -> r.score }, "stabilita (min togli una vista)" to { r -> r.lovoMin },
            "E1 copertura (≥ 15 cm)" to { r -> r.e1.coverage }, "E4 sopra copertura" to { r -> r.e4.coverage }, "E4 distanza m" to { r -> r.e4.distM },
            "E5 sinistra copertura" to { r -> r.e5l.coverage }, "E5 destra copertura" to { r -> r.e5r.coverage },
            "E5 entrambi i lati accesi (0/1)" to { r -> if (on(r.e5l) && on(r.e5r)) 1.0 else 0.0 },
            "E5 distanza m (media dei lati)" to { r -> listOfNotNull(r.e5l.distM, r.e5r.distM).takeIf { it.isNotEmpty() }?.average() },
            "E5 asimmetria |dL - dR| m" to { r -> if (r.e5l.distM != null && r.e5r.distM != null) abs(r.e5l.distM - r.e5r.distM) else null },
            "E2 parallela adiacente R2 (0/1)" to { r -> if (r.e2 != null) 1.0 else 0.0 },
            "E3 fianchi verso dietro" to { r -> r.sidesBack.toDouble() }, "fianchi in avanti (concavita)" to { r -> r.sidesForward.toDouble() },
            "tutta altezza (0/1)" to { r -> if (r.fullHeight) 1.0 else 0.0 }, "altezza sopra pavimento m" to { r -> r.sf.topAboveFloor },
            "larghezza m" to { r -> r.sf.lengthM }, "dietro non osservato" to { r -> r.behindUnobserved }, "viste del fronte" to { r -> r.frontViews.toDouble() },
        )
        val fa = mutableListOf<String>()
        fun stats(v: List<Double>) = v.sorted().let { if (it.isEmpty()) listOf("", "", "") else listOf(f(it.first()), f(it[it.size / 2]), f(it.last())) }
        for ((name, fn) in feats) for (k in cats) {
            val v = clean.filter { it.category == k }.mapNotNull(fn).filter { !it.isNaN() }
            fa.add((listOf("distribuzione", name, k, v.size) + stats(v) + listOf("")).joinToString(";"))
        }
        val pairs = listOf("mobile aderente" to "pilastro (fronte)", "mobile aderente" to "rientranza (pareti accanto)", "mobile con l'impronta del pilastro" to "pilastro (fronte)",
            "mobile distante" to "pilastro (fronte)", "mobile aderente" to "muro normale")
        for ((a, b) in pairs) for ((name, fn) in feats) {
            val va = clean.filter { it.category == a }.mapNotNull(fn).filter { !it.isNaN() }; val vb = clean.filter { it.category == b }.mapNotNull(fn).filter { !it.isNaN() }
            if (va.isEmpty() || vb.isEmpty()) continue
            val sep = va.max() < vb.min() || vb.max() < va.min()
            fa.add(listOf("confronto", name, "$a | $b", "${va.size}|${vb.size}", "${f(va.min())}..${f(va.max())}", "", "${f(vb.min())}..${f(vb.max())}",
                if (sep) "SEPARATI nel benchmark (non generalizzabile: un solo tipo di pilastro/rientranza)" else "SI SOVRAPPONGONO").joinToString(";"))
        }

        // Contratto R3/R4: aggregato per opzione.
        val r3Header = "caso;insieme;seme;opzione;superficiAMBIGUOUS;pareteR3;pareteGTrilevate;pareteGT;pareteR3False;recallPareti;precisionePareti;statoR4;angoliCorretti;angoliSbagliati;angoliMancati;erroreGeomMedioM"
        val r3agg = mutableListOf<String>()
        val parsed = r3rows.map { it.split(';') }
        val withAmb = parsed.filter { it[4].isNotBlank() }.map { it[0] to it[2] }.toSet()
        for (opt in listOf("base (R2 di produzione)", "A esclusa", "D separata (R3/R4 = A)", "B peso ridotto (APPROSSIMATO)")) for (set in listOf("tutti", "definizione", "verifica")) {
            val g = parsed.filter { it[3] == opt && (it[0] to it[2]) in withAmb && (set == "tutti" || it[1] == set) }; if (g.isEmpty()) continue
            r3agg.add(listOf(opt, set, g.size, g.sumOf { it[6].toInt() }, g.sumOf { it[7].toInt() }, g.sumOf { it[8].toInt() }, f(g.map { it[9].toDouble() }.average()), f(g.map { it[10].toDouble() }.average()),
                g.count { it[11] == PerimeterState.CLOSED.name }, g.sumOf { it[12].toInt() }, g.sumOf { it[13].toInt() }, g.sumOf { it[14].toInt() }).joinToString(";"))
        }
        r3agg.add("C incertezza elevata;tutti;;;;;;;;;;NON SIMULABILE senza modificare R3: solo contratto")
        val r3Contract = listOf(
            "campo;tipo;significato",
            "surfaceClass;VERTICAL_STRUCTURAL|VERTICAL_AMBIGUOUS|VERTICAL_OBJECT|UNKNOWN;classe R2.1",
            "structuralScore;Double;punteggio R2 attuale invariato",
            "ambiguityScore;Double [0,1];solo evidenza alternativa osservata (E1/E4/E5)",
            "ambiguityStable;Boolean;lo score resta >= tau togliendo una vista qualsiasi",
            "evidenceQuality;HIGH|LOW;qualita della misura: asse separato, non entra in ambiguityScore",
            "flags;NO_BACKFACE_INFORMATION;nessuna informazione dietro: limite del sistema, non cambia la classe",
            "",
            "opzione;esito aggregato sui run con almeno una superficie AMBIGUOUS (candidato D tau 0.15): run;pareteGTrilevate;pareteGT;pareteR3False;recallMedia;precisioneMedia;R4 CLOSED;angoliCorretti;angoliSbagliati;angoliMancati",
        ) + r3agg

        // Contratto RescanDirector + conteggi simulati (candidato D).
        fun rescan(r: SRow): String? {
            val k = classify(r, "D")
            return when {
                k == AMB && r.sf.areaM2 >= 0.5 && r.inR3 && r.bandUnobserved >= 0.3 -> "RESCAN_REQUIRED"
                k == AMB && r.bandUnobserved < 0.3 -> "INFORMATION_LIMITATION"
                k == AMB -> "KEEP_UNRESOLVED"
                k == "VERTICAL_STRUCTURAL" && r.noBackface -> "SYSTEM_LIMITATION_LOGGED"
                r.quality == "LOW" -> "QUALITY_ENGINE"
                else -> null
            }
        }
        val outcomes = listOf("RESCAN_REQUIRED", "INFORMATION_LIMITATION", "KEEP_UNRESOLVED", "SYSTEM_LIMITATION_LOGGED", "QUALITY_ENGINE")
        val rescanRows = mutableListOf(
            "esito;condizione;ingressi;uscita",
            "RESCAN_REQUIRED;AMBIGUOUS + area >= 0.5 m2 + fonte di una parete R3 + banda laterale o superiore con >= 30% colonne non osservate;surfaceId|ambiguityChannels|bande non osservate|viste;banda da osservare + direzione suggerita (normale e lato)",
            "INFORMATION_LIMITATION;AMBIGUOUS + nessuna banda con colonne non osservate (tutto il visibile e gia visto);surfaceId|ambiguityChannels;nessuna richiesta: ambiguita residua dichiarata",
            "KEEP_UNRESOLVED;AMBIGUOUS + area < 0.5 m2 o non fonte di parete R3;surfaceId;lasciata irrisolta",
            "SYSTEM_LIMITATION_LOGGED;STRUCTURAL + NO_BACKFACE_INFORMATION;surfaceId;registrato: un'altra vista dall'interno non aggiunge nulla",
            "QUALITY_ENGINE;evidenceQuality LOW senza evidenza alternativa;surfaceId|viste|area|frame;fuori da questo contratto (incertezza di misura, non ambiguita)",
            "MANUAL_REVIEW;richiesta dell'utente o conflitto col perimetro non risolvibile;-;-",
            "",
            "categoria;" + outcomes.joinToString(";") + ";nessuno",
        )
        for (k in cats) {
            val g = clean.filter { it.category == k }; val res = g.map { rescan(it) }
            rescanRows.add((listOf(k) + outcomes.map { o -> res.count { it == o } } + listOf(res.count { it == null })).joinToString(";"))
        }

        // Viste.
        val viewRows = sweep.map { r -> listOf(r.caseId, r.views, r.seed, "S${r.sf.id}", r.category, r.sf.kind, f(r.score), f(r.lovoMin), r.lovoN, classify(r, "C"), classify(r, "D"), classify(r, "E"), f(r.e4.coverage), f(r.e5l.coverage), f(r.e5r.coverage)).joinToString(";") }
        val viewHeader = "caso;viste;seme;superficie;categoria;classeR2;ambiguityScore;stabilitaMin;visteTolte;C;D;E;E4cop;E5Lcop;E5Rcop"

        // Dati reali.
        val realRows = real.map { r -> listOf(r.caseId, "S${r.sf.id}", r.sf.kind, f(r.sf.areaM2, 2), r.quality, r.frontViews, r.views, f(r.score), f(r.lovoMin), r.noBackface, classify(r, "A"), classify(r, "C"), classify(r, "D"), classify(r, "E")).joinToString(";") }
        val realHeader = "dataset (NO SEMANTIC GROUND TRUTH);superficie;classeR2;areaM2;evidenceQuality;visteFronte;visteTotali;ambiguityScore;stabilitaMin;NO_BACKFACE_INFORMATION;A;C;D;E"

        File(out, "surfaces.csv").writeText("$surfHeader\n" + (rows + sweep + real).joinToString("\n") { surfLine(it) } + "\n")
        File(out, "classification-matrix.csv").writeText("candidato;parametro;descrizione;insieme;categoria;n;STRUCTURAL;AMBIGUOUS;OBJECT;UNKNOWN\n" + matrix.joinToString("\n") + "\n")
        File(out, "candidate-comparison.csv").writeText("$compHeader\n" + comp.joinToString("\n") + "\n")
        File(out, "feature-analysis.csv").writeText("tipo;feature;categoria;n;min;mediana;max;verdetto\n" + fa.joinToString("\n") + "\n")
        File(out, "r3-r4-contract.csv").writeText(r3Contract.joinToString("\n") + "\n\n$r3Header\n" + r3rows.joinToString("\n") + "\n")
        File(out, "rescan-contract.csv").writeText(rescanRows.joinToString("\n") + "\n")
        File(out, "view-stability.csv").writeText("$viewHeader\n" + viewRows.joinToString("\n") + "\n")
        File(out, "real-distribution.csv").writeText("$realHeader\n" + realRows.joinToString("\n") + "\n")
        fun arr(h: String, rs: List<String>) = rs.joinToString(",\n", "[", "]") { r -> val k = h.split(';'); val v = r.split(';'); "{" + k.indices.joinToString(",") { "\"${k[it]}\":\"${v.getOrElse(it) { "" }.replace("\\", "\\\\").replace("\"", "'")}\"" } + "}" }
        File(out, "r2-1-spec-audit.json").writeText(
            "{\n\"note\":\"AUDIT OFFLINE della specifica R2.1: nessuna modifica di produzione. Soglie fissate a priori. Dati reali: NO SEMANTIC GROUND TRUTH.\",\n" +
                "\"soglie\":{\"dMinArretrataM\":0.075,\"dMinE1M\":$dMinE1,\"coperturaMin\":$covT,\"continuitaMin\":$contT,\"tau\":$tau,\"visteMin\":$minViews},\n" +
                "\"candidateComparison\":" + arr(compHeader, comp) + ",\n\"classificationMatrix\":" + arr("candidato;parametro;descrizione;insieme;categoria;n;STRUCTURAL;AMBIGUOUS;OBJECT;UNKNOWN", matrix) +
                ",\n\"featureAnalysis\":" + arr("tipo;feature;categoria;n;min;mediana;max;verdetto", fa) + ",\n\"r3r4\":" + arr(r3Header, r3rows) +
                ",\n\"viewStability\":" + arr(viewHeader, viewRows) + ",\n\"real\":" + arr(realHeader, realRows) + ",\n\"surfaces\":" + arr(surfHeader, (rows + sweep + real).map { surfLine(it) }) + "\n}\n",
        )
        File(out, "ambiguity-by-category.svg").writeText(stripSvg(clean, cats))
        File(out, "view-stability.svg").writeText(viewSvg(sweep))
    }

    private fun stripSvg(rows: List<SRow>, cats: List<String>): String {
        val h = 60 + 26 * cats.size
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="900" height="${h + 40}" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="14" font-weight="bold">ambiguityScore per categoria (solo superfici STRUCTURAL per R2 sono candidabili; linea = τ 0,15)</text>""")
        val x0 = 300.0; val w = 560.0
        sb.append("""<line x1="${f(x0 + tau * w, 1)}" y1="40" x2="${f(x0 + tau * w, 1)}" y2="$h" stroke="#C62828" stroke-dasharray="4 3"/>""")
        for (p in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) sb.append("""<text x="${f(x0 + p * w - 8, 1)}" y="${h + 16}">${f(p, 2)}</text>""")
        for ((i, k) in cats.withIndex()) {
            val y = 60 + 26 * i
            sb.append("""<text x="10" y="${y + 4}">${k.replace("&", "e")}</text><line x1="$x0" y1="$y" x2="${x0 + w}" y2="$y" stroke="#EEE"/>""")
            for (r in rows.filter { it.category == k }) {
                val col = when (r.sf.kind) { SurfaceKind.VERTICAL_STRUCTURAL -> "#1565C0"; SurfaceKind.VERTICAL_OBJECT -> "#2E7D32"; else -> "#999" }
                sb.append("""<circle cx="${f(x0 + r.score * w, 1)}" cy="${f(y + (r.seed - 2) * 5.0, 1)}" r="3.5" fill="$col" fill-opacity="0.6"><title>${r.caseId} s${r.seed} S${r.sf.id} ${r.sf.kind} ${f(r.score)}</title></circle>""")
            }
        }
        sb.append("""<text x="$x0" y="${h + 34}" fill="#555">blu = STRUCTURAL in R2, verde = OBJECT, grigio = UNKNOWN</text></svg>""")
        return sb.toString()
    }

    private fun viewSvg(rows: List<SRow>): String {
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="820" height="440" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="14" font-weight="bold">ambiguityScore (pieno) e stabilità togliendo una vista (vuoto) contro numero di viste</text>""")
        val x0 = 70.0; val y0 = 390.0; val w = 500.0; val h = 330.0; val xs = listOf(1, 2, 4, 8)
        fun px(n: Int) = x0 + w * xs.indexOf(n) / 3.0
        sb.append("""<line x1="$x0" y1="$y0" x2="${x0 + w}" y2="$y0" stroke="#000"/><line x1="$x0" y1="$y0" x2="$x0" y2="${y0 - h}" stroke="#000"/><line x1="$x0" y1="${f(y0 - tau * h, 1)}" x2="${x0 + w}" y2="${f(y0 - tau * h, 1)}" stroke="#C62828" stroke-dasharray="4 3"/>""")
        for (n in xs) sb.append("""<text x="${f(px(n) - 12, 1)}" y="${y0 + 18}">$n</text>""")
        for (p in listOf(0.0, 0.5, 1.0)) sb.append("""<text x="35" y="${f(y0 - p * h + 4, 1)}">${f(p, 1)}</text>""")
        val cols = listOf("O5" to "#C62828", "O9" to "#6A1B9A", "O10" to "#EF6C00", "O0" to "#1565C0", "C2" to "#2E7D32")
        for ((i, pr) in cols.withIndex()) {
            val (id, col) = pr
            val g = rows.filter { it.caseId == id }.groupBy { it.views }.toSortedMap()
            val pts = g.map { (n, l) -> Triple(n, l.map { it.score }.average(), l.map { it.lovoMin }.average()) }
            sb.append("""<polyline fill="none" stroke="$col" stroke-width="2" points="${pts.joinToString(" ") { "${f(px(it.first) + i * 3.0, 1)},${f(y0 - it.second * h, 1)}" }}"/>""")
            for ((n, a, m) in pts) sb.append("""<circle cx="${f(px(n) + i * 3.0, 1)}" cy="${f(y0 - a * h, 1)}" r="4" fill="$col"/><circle cx="${f(px(n) + i * 3.0, 1)}" cy="${f(y0 - m * h, 1)}" r="4" fill="none" stroke="$col"/>""")
            val lbl = mapOf("O5" to "O5 mobile aderente", "O9" to "O9 armadio parete-parete", "O10" to "O10 armadio 2 m / parete 3 m", "O0" to "O0 parete", "C2" to "C2 pilastro")
            sb.append("""<rect x="600" y="${60 + 20 * i}" width="12" height="4" fill="$col"/><text x="617" y="${66 + 20 * i}">${lbl[id]}</text>""")
        }
        sb.append("""<text x="600" y="180" fill="#555">media di 3 semi; linea rossa = τ</text></svg>""")
        return sb.toString()
    }
}
