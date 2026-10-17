package com.sagoma.planimetria.scan.experiment

import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalyzer
import com.sagoma.planimetria.scan.recording.analysis.SyntheticScan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerimeterExperimentTest {
    /** Candidata con evidenza "buona": 12 s, 120 frame, punti 12/m, rms 2 cm, alta 2,2 m. */
    private fun cand(
        id: Int, ax: Double, az: Double, bx: Double, bz: Double, first: Long = 0, last: Long = 12_000,
        frames: Int = 120, points: Int? = null, rms: Double? = 0.02, subsumed: Boolean = false,
    ): WallCandidate {
        val len = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
        return WallCandidate(id, ax, az, bx, bz, first, last, frames, 1.0, -0.2, 2.0, points ?: (len * 12).toInt(), rms, subsumed, listOf(id))
    }

    private fun poly(vararg v: Pair<Double, Double>, hide: Double = 0.0, firstId: Int = 1): List<WallCandidate> =
        v.indices.map { i ->
            val (x0, z0) = v[i]; val (x1, z1) = v[(i + 1) % v.size]
            val len = sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0))
            val ux = (x1 - x0) / len; val uz = (z1 - z0) / len
            cand(firstId + i, x0 + ux * hide, z0 + uz * hide, x1 - ux * hide, z1 - uz * hide)
        }

    private val rect = arrayOf(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0)
    private val lShape = arrayOf(0.0 to 0.0, 5.0 to 0.0, 5.0 to 2.0, 2.5 to 2.0, 2.5 to 4.0, 0.0 to 4.0)

    /** Camera che gira dentro il poligono e punti lungo i muri (evidenza coerente con la stanza). */
    private fun input(cands: List<WallCandidate>, cam: List<ArXZ>, withPoints: Boolean = true): ExperimentInput {
        val pts = if (!withPoints) emptyList() else cands.flatMap { c -> (0..10).map { k -> ArXZ(c.ax + (c.bx - c.ax) * k / 10, c.az + (c.bz - c.az) * k / 10) } }
        return ExperimentInput(cands, cam, pts)
    }

    private val camRect = listOf(ArXZ(1.0, 1.0), ArXZ(3.0, 1.0), ArXZ(3.0, 2.0), ArXZ(1.0, 2.0))
    private val camL = listOf(ArXZ(1.0, 1.0), ArXZ(4.0, 1.0), ArXZ(1.5, 3.0), ArXZ(1.0, 2.5))

    private fun polygonArea(p: List<ArXZ>): Double { var s = 0.0; for (k in p.indices) { val a = p[k]; val b = p[(k + 1) % p.size]; s += a.x * b.z - b.x * a.z }; return abs(s) / 2 }

    // ------------------------------------------------------------------ forme

    @Test
    fun `rettangolo - quattro pareti, area esatta, chiusura senza prolungamenti`() {
        val r = PerimeterExperiment.run(input(poly(*rect), camRect))
        assertEquals(4, r.walls.size)
        val s = assertNotNull(r.best)
        assertEquals(4, s.wallIndices.size)
        assertEquals(12.0, s.areaM2, 1e-6); assertEquals(14.0, s.perimeterM, 1e-6)
        assertEquals(0.0, s.closureErrorM, 1e-6)
        assertTrue(s.interiorAnglesDeg.all { abs(it - 90.0) < 1e-6 }, s.interiorAnglesDeg.toString())
        assertEquals(1.0, s.supportedFraction, 1e-6)
        assertEquals(Verdict.SUPPORTED, r.verdict)
        assertEquals(List(4) { CandidateStatus.IN_PERIMETER_SINGLE }, r.status.map { it.second })
    }

    @Test
    fun `stanza a L - sei pareti, un angolo rientrante di 270 gradi, area 15`() {
        val r = PerimeterExperiment.run(input(poly(*lShape), camL))
        val s = assertNotNull(r.best)
        assertEquals(6, s.wallIndices.size)
        assertEquals(15.0, s.areaM2, 1e-6)
        assertEquals(1, s.interiorAnglesDeg.count { abs(it - 270.0) < 1e-6 }, s.interiorAnglesDeg.toString())
        assertEquals(5, s.interiorAnglesDeg.count { abs(it - 90.0) < 1e-6 })
        assertEquals(Verdict.SUPPORTED, r.verdict)
    }

    @Test
    fun `pareti non ortogonali - parallelogramma a 70 gradi`() {
        val c = cos(70 * PI / 180) * 4; val h = sin(70 * PI / 180) * 4
        val v = arrayOf(0.0 to 0.0, 5.0 to 0.0, 5.0 + c to h, c to h)
        val r = PerimeterExperiment.run(input(poly(*v), listOf(ArXZ(2.0, 1.5), ArXZ(4.0, 1.5), ArXZ(3.0, 2.5))))
        val s = assertNotNull(r.best)
        assertEquals(4, s.wallIndices.size)
        assertEquals(5.0 * h, s.areaM2, 1e-6)
        assertEquals(2, s.interiorAnglesDeg.count { abs(it - 70.0) < 1e-6 }, s.interiorAnglesDeg.toString())
        assertEquals(2, s.interiorAnglesDeg.count { abs(it - 110.0) < 1e-6 })
    }

    @Test
    fun `non assume quattro pareti ne angoli retti - pentagono irregolare`() {
        val v = arrayOf(0.0 to 0.0, 4.0 to -0.5, 5.2 to 2.0, 2.5 to 4.2, -0.8 to 2.5)
        val r = PerimeterExperiment.run(input(poly(*v), listOf(ArXZ(2.0, 1.5), ArXZ(3.0, 2.0), ArXZ(1.5, 2.5))))
        val s = assertNotNull(r.best)
        assertEquals(5, s.wallIndices.size)
        assertEquals(polygonArea(v.map { ArXZ(it.first, it.second) }), s.areaM2, 1e-6)
    }

    // ------------------------------------------------------------------ fusione

    @Test
    fun `segmenti duplicati della stessa parete si fondono, anche con piccoli errori`() {
        val dup = poly(*rect).flatMapIndexed { i, c ->
            listOf(
                c.copy(id = 10 * (i + 1), firstMs = 0, lastMs = 5_000),
                // Stessa parete vista più tardi: 3 cm di scostamento (deriva) e circa 1° di rotazione.
                c.copy(id = 10 * (i + 1) + 1, firstMs = 7_000, lastMs = 12_000, ax = c.ax + 0.02, az = c.az + 0.03, bx = c.bx, bz = c.bz - 0.02),
                c.copy(id = 10 * (i + 1) + 2, firstMs = 3_000, lastMs = 9_000, ax = c.ax + (c.bx - c.ax) * 0.2, az = c.az + (c.bz - c.az) * 0.2),
            )
        }
        val r = PerimeterExperiment.run(input(dup, camRect))
        assertEquals(4, r.walls.size)
        assertTrue(r.walls.all { it.memberIds.size == 3 })
        val s = assertNotNull(r.best)
        assertEquals(12.0, s.areaM2, 0.15)
        assertEquals(12, r.status.count { it.second == CandidateStatus.IN_PERIMETER_FUSED })
    }

    @Test
    fun `pareti parallele distinte non si fondono - opposte, o un mobile davanti al muro`() {
        // Le due pareti opposte (3 m) sono sempre distinte; il fronte di un armadio a 50 cm dal muro, visto insieme al muro, pure.
        val wardrobe = cand(9, 1.0, 0.5, 2.5, 0.5, first = 2_000, last = 11_000)
        val r = PerimeterExperiment.run(input(poly(*rect) + wardrobe, camRect))
        assertEquals(5, r.walls.size)
        assertEquals(12.0, assertNotNull(r.best).areaM2, 1e-6)
        assertEquals(CandidateStatus.NOT_IN_PERIMETER, r.status.first { it.first == 9 }.second)
    }

    @Test
    fun `la tolleranza dipende dal tempo - 12 cm di scarto si fondono solo se non erano visti insieme`() {
        val top = cand(1, 0.0, 0.0, 4.0, 0.0, first = 0, last = 5_000)
        val simultaneous = cand(2, 0.0, 0.12, 4.0, 0.12, first = 1_000, last = 6_000)
        val later = cand(3, 0.0, 0.12, 4.0, 0.12, first = 8_000, last = 12_000)
        assertEquals(2, PerimeterExperiment.run(ExperimentInput(listOf(top, simultaneous))).walls.size)
        assertEquals(1, PerimeterExperiment.run(ExperimentInput(listOf(top, later))).walls.size)
        // 5 cm contemporanei: stessa parete (sotto la tolleranza di 8 cm).
        assertEquals(1, PerimeterExperiment.run(ExperimentInput(listOf(top, cand(4, 0.0, 0.05, 4.0, 0.05, first = 1_000, last = 6_000)))).walls.size)
        // Angolo di 12°: mai la stessa parete.
        val tilted = cand(5, 0.0, 0.0, 4.0, 4.0 * sin(12 * PI / 180) / cos(12 * PI / 180), first = 8_000, last = 12_000)
        assertEquals(2, PerimeterExperiment.run(ExperimentInput(listOf(top, tilted))).walls.size)
    }

    @Test
    fun `gap tra segmenti - piccolo si fonde, grande no, e senza perimetro non si inventa niente`() {
        val whole = poly(*rect)
        // Parete in alto spezzata con un vuoto di 50 cm (una porta stretta): una sola parete, copertura ridotta.
        val small = whole.drop(1) + cand(11, 0.0, 0.0, 1.75, 0.0) + cand(12, 2.25, 0.0, 4.0, 0.0)
        val rs = PerimeterExperiment.run(input(small, camRect))
        assertEquals(4, rs.walls.size)
        val top = rs.walls.first { it.memberIds == listOf(11, 12) }
        assertEquals(3.5 / 4.0, top.coverage, 1e-6)
        assertNotNull(rs.best)
        // Vuoto di 2 m: due pezzi collineari distinti, e il perimetro non si chiude (non si inventa il pezzo mancante).
        val big = whole.drop(1) + cand(11, 0.0, 0.0, 1.0, 0.0) + cand(12, 3.0, 0.0, 4.0, 0.0)
        val rb = PerimeterExperiment.run(input(big, camRect))
        assertEquals(5, rb.walls.size)
        assertNull(rb.best)
        assertEquals(Verdict.NO_PERIMETER, rb.verdict)
        assertTrue(rb.reasons.isNotEmpty())
    }

    // ------------------------------------------------------------------ spurie, incomplete, nascoste

    @Test
    fun `candidate spurie - corte, deboli e linee che attraversano la stanza non cambiano il perimetro`() {
        val spurious = listOf(
            cand(20, 1.0, 1.0, 1.4, 1.0),                                              // corta (40 cm)
            cand(21, 0.0, 1.5, 4.0, 1.5, first = 0, last = 400, frames = 4, points = 0, rms = null), // attraversa la stanza, poca evidenza
            cand(22, 2.0, 0.0, 2.0, 0.25),                                              // troppo corta
        )
        val r = PerimeterExperiment.run(input(poly(*rect) + spurious, camRect))
        val s = assertNotNull(r.best)
        assertEquals(12.0, s.areaM2, 1e-6)
        assertEquals(4, s.wallIndices.size)
        val st = r.status.associate { it.first to it.second }
        assertEquals(CandidateStatus.EXCLUDED_SANITY, st[22])
        assertEquals(CandidateStatus.EXCLUDED_WEAK, st[20])
        assertEquals(CandidateStatus.NOT_IN_PERIMETER, st[21])
        assertEquals(listOf(22), r.sanityExcluded)
    }

    @Test
    fun `solo tre pareti su quattro - nessun perimetro e nessuna parete inventata`() {
        val r = PerimeterExperiment.run(input(poly(*rect).take(3), camRect))
        assertNull(r.best)
        assertEquals(Verdict.NO_PERIMETER, r.verdict)
        assertEquals(3, r.walls.size)
        assertTrue(r.status.all { it.second == CandidateStatus.NO_PERIMETER })
    }

    @Test
    fun `angoli nascosti da mobili - le pareti si prolungano e l errore di chiusura lo dice`() {
        val r = PerimeterExperiment.run(input(poly(*rect, hide = 0.6), camRect))
        val s = assertNotNull(r.best)
        assertEquals(12.0, s.areaM2, 1e-6)
        assertEquals(4.8, s.closureErrorM, 1e-6) // 4 angoli × 2 pareti × 60 cm aggiunti
        assertEquals(0.6, s.maxExtensionM, 1e-6)
        assertTrue(s.supportedFraction < 0.8 && s.supportedFraction > 0.6, s.supportedFraction.toString())
    }

    @Test
    fun `la forma coerente non basta - camera fuori dal perimetro, evidenza debole`() {
        val outside = listOf(ArXZ(10.0, 10.0), ArXZ(11.0, 10.0), ArXZ(10.0, 11.0))
        val r = PerimeterExperiment.run(input(poly(*rect), outside))
        assertNotNull(r.best) // geometricamente coerente...
        assertEquals(Verdict.GEOMETRIC_ONLY, r.verdict) // ...ma non sostenuta
        assertTrue(r.reasons.any { it.contains("camera") }, r.reasons.toString())
        // Pareti ad alta evidenza ma punti quasi tutti FUORI dal perimetro: non è la stanza.
        val faraway = ExperimentInput(poly(*rect), camRect, (0..50).map { ArXZ(20.0 + it * 0.1, 20.0) })
        val rf = PerimeterExperiment.run(faraway)
        assertEquals(Verdict.GEOMETRIC_ONLY, rf.verdict)
        assertTrue(rf.reasons.any { it.contains("fuori dal perimetro") })
    }

    @Test
    fun `con rumore realistico di posizione e angolo il perimetro resta quello`() {
        val rnd = kotlin.random.Random(42)
        fun jitter(c: WallCandidate): WallCandidate {
            val dx = c.bx - c.ax; val dz = c.bz - c.az
            val a = (rnd.nextDouble() - 0.5) * 2 * 1.5 * PI / 180
            val ox = (rnd.nextDouble() - 0.5) * 0.06; val oz = (rnd.nextDouble() - 0.5) * 0.06
            val rx = dx * cos(a) - dz * sin(a); val rz = dx * sin(a) + dz * cos(a)
            return c.copy(ax = c.ax + ox, az = c.az + oz, bx = c.ax + ox + rx, bz = c.az + oz + rz)
        }
        val r = PerimeterExperiment.run(input(poly(*rect).map(::jitter), camRect))
        val s = assertNotNull(r.best)
        assertEquals(4, s.wallIndices.size)
        assertEquals(12.0, s.areaM2, 12.0 * 0.04)
    }

    // ------------------------------------------------------------------ ordine

    @Test
    fun `indipendenza dall ordine - 20 permutazioni casuali con id ridistribuiti, stessa soluzione`() {
        val cands = poly(*lShape) + listOf(
            cand(30, 0.0, 0.0, 2.0, 0.02, first = 6_000, last = 11_000),  // duplicato parziale della prima parete
            cand(31, 1.0, 1.0, 1.4, 1.0),                                  // spuria corta
            cand(32, 0.0, 3.0, 5.0, 3.0, first = 0, last = 300, frames = 3, points = 0, rms = null), // spuria debole
        )
        val perm = PerimeterExperiment.permutations(input(cands, camL), runs = 20)
        assertEquals(20, perm.runs)
        assertEquals(20, perm.identical, perm.details.joinToString("\n"))
        assertEquals(1, perm.distinctSignatures.size, perm.distinctSignatures.toString())
        assertTrue(perm.maxDeviationM < 1e-6, perm.maxDeviationM.toString())
        assertTrue(perm.maxAreaDeviationM2 < 1e-6 && perm.maxScoreDeviation < 1e-6)
        // Anche con gli estremi scambiati (a ↔ b).
        val flipped = cands.map { it.copy(ax = it.bx, az = it.bz, bx = it.ax, bz = it.az) }
        assertEquals(PerimeterExperiment.run(input(cands, camL)).signature(), PerimeterExperiment.run(input(flipped, camL)).signature())
    }

    @Test
    fun `determinismo e sensibilita alle soglie`() {
        val inp = input(poly(*rect), camRect)
        assertEquals(PerimeterReport.text("t", inp, PerimeterExperiment.run(inp), PerimeterExperiment.permutations(inp, runs = 3), emptyList(), "s"),
            PerimeterReport.text("t", inp, PerimeterExperiment.run(inp), PerimeterExperiment.permutations(inp, runs = 3), emptyList(), "s"))
        val rows = PerimeterExperiment.sweep(inp)
        assertTrue(rows.size >= 10)
        assertEquals("base", rows.first().label)
        assertEquals(12.0, rows.first().area!!, 1e-6)
        // Nel rettangolo pulito il risultato è identico con tutte le varianti di soglia.
        assertTrue(rows.all { it.walls == 4 && it.cycleWalls == 4 }, rows.toString())
    }

    // ------------------------------------------------------------------ registrazione sintetica, report, SVG, riga di comando

    @Test
    fun `dalla registrazione sintetica - candidate dai piani, fusione del piano assorbito e dell effimero collineare`() {
        val rec = SyntheticScan.recording()
        val analysis = RecordingAnalyzer.analyze(rec)
        val inp = CandidateExtractor.extract(rec, analysis)
        assertEquals(6, inp.candidates.size)
        assertTrue(inp.candidates.first { it.id == 1 }.pointSupport > 0)
        val r = PerimeterExperiment.run(inp)
        assertEquals(4, r.walls.size)
        val s = assertNotNull(r.best)
        assertEquals(12.0, s.areaM2, 0.05)
        val st = r.status.associate { it.first to it.second }
        // Il pezzetto effimero (#5) sta sulla stessa retta del muro in alto e viene fuso con esso (non è scartato da una etichetta: lo decide la geometria).
        assertEquals(CandidateStatus.IN_PERIMETER_FUSED, st[5])
        assertEquals(CandidateStatus.IN_PERIMETER_FUSED, st[6])
        assertEquals(CandidateStatus.IN_PERIMETER_FUSED, st[1])
        val perm = PerimeterExperiment.permutations(inp, runs = 20)
        assertEquals(20, perm.identical)
        val text = PerimeterReport.text("sintetica", inp, r, perm, PerimeterExperiment.sweep(inp), "test")
        for (needle in listOf("Candidate iniziali: 6", "Pareti dopo la fusione: 4", "Errore di chiusura", "Area:", "20 permutazioni", "Soluzione geometricamente coerente: SÌ", "NON prova")) {
            assertTrue(text.contains(needle), "manca \"$needle\":\n$text")
        }
        // Le candidate escluse compaiono nella tavola come x# (qui due spurie sintetiche, sullo sfondo della stessa registrazione).
        val withSpurious = PerimeterExperiment.run(input(poly(*rect) + cand(20, 1.0, 1.0, 1.4, 1.0) + cand(22, 2.0, 0.0, 2.0, 0.25), camRect))
        val svgX = PerimeterSvg.render(analysis, withSpurious, "prova", false)
        assertTrue(svgX.contains(">x20<") && svgX.contains(">x22<"))
        for (withPoints in listOf(true, false)) {
            val svg = PerimeterSvg.render(analysis, r, "prova", withPoints)
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)))
            assertTrue(svg.contains("K1") && svg.contains("W1") && svg.contains("c1"))
            assertEquals(withPoints, svg.contains("fill=\"#d32f2f\""))
        }
    }

    @Test
    fun `riga di comando - scrive report, SVG con e senza punti, tabella e candidate riutilizzabili`() {
        val dir = Files.createTempDirectory("sagoma-perimetro").toFile()
        try {
            val file = File(dir, "scan.jsonl").apply { writeText(ScanRecordingJson.encode(SyntheticScan.recording())) }
            val out = File(dir, "o")
            val bytes = ByteArrayOutputStream()
            assertEquals(0, PerimeterExperimentCli.run(arrayOf(file.path, "--out=${out.path}", "--permutations=5"), PrintStream(bytes, true, "UTF-8")))
            for (n in listOf("perimeter-report.txt", "perimeter-with-points.svg", "perimeter-clean.svg", "perimeter-walls.csv", "candidates.json")) assertTrue(File(out, n).length() > 0, "manca $n")
            // Le candidate scritte si rileggono e danno lo stesso risultato.
            val out2 = File(dir, "o2")
            assertEquals(0, PerimeterExperimentCli.run(arrayOf(file.path, "--out=${out2.path}", "--permutations=5", "--candidates=${File(out, "candidates.json").path}"), PrintStream(ByteArrayOutputStream(), true, "UTF-8")))
            assertEquals(File(out, "perimeter-walls.csv").readText(), File(out2, "perimeter-walls.csv").readText())
            assertEquals(2, PerimeterExperimentCli.run(emptyArray(), PrintStream(ByteArrayOutputStream())))
            assertEquals(2, PerimeterExperimentCli.run(arrayOf(File(dir, "no.jsonl").path), PrintStream(ByteArrayOutputStream())))
        } finally {
            dir.deleteRecursively()
        }
    }
}
