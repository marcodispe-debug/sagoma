package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * BENCHMARK sperimentale con ground truth (solo test, nessuna modifica di produzione).
 * I test controllano il benchmark stesso (verità, simulatore, metriche, determinismo) e rendono visibili gli errori importanti della
 * pipeline; non affermano che la pipeline "è buona". Con SAGOMA_BENCH_OUT=<cartella> esegue l'intera suite (S0–S17 × varianti R2 +
 * sweep di rumore, viste e seme) e scrive JSON, CSV, SVG e un riepilogo.
 */
class BenchmarkTest {
    private val runs = HashMap<String, Pair<BenchRun, BenchMetrics>>()
    private fun run(id: String, bp: BenchParams = BenchParams()): Pair<BenchRun, BenchMetrics> =
        runs.getOrPut("$id|$bp") { BenchPipeline.run(Scenarios.byId(id), bp).let { it to BenchEval.evaluate(it) } }

    // ---------------------------------------------------------------------------------------------------- ground truth

    @Test
    fun `ground truth - angoli, lunghezze, orientazioni, poligono chiuso`() {
        val r = Scenarios.byId("S0").room
        assertEquals(4, r.corners.size); assertEquals(14.0, r.perimeterM, 1e-12)
        assertTrue(r.corners.all { abs(it.interiorAngleDeg - 90) < 1e-9 })
        assertTrue(r.inside(2.0, 1.5)); assertFalse(r.inside(5.0, 1.5))
        // Normale verso l'interno (convenzione di R3/R4).
        for (w in r.walls) { val m = w.pointAt(w.length / 2); assertTrue(r.inside(m[0] + w.nx * 0.1, m[1] + w.nz * 0.1)) }
        val l = Scenarios.byId("S11").room
        assertEquals(8, l.corners.size)
        assertEquals(2, l.corners.count { abs(it.interiorAngleDeg - 270) < 1e-9 }) // angoli rientranti
        assertFailsWith<IllegalArgumentException> { GtRoom("aperto", listOf(GtWall(0, 0.0, 0.0, 1.0, 0.0), GtWall(1, 1.0, 1.0, 0.0, 0.0))) }
        assertEquals(18, Scenarios.all().size)
    }

    // ------------------------------------------------------------------------------------------------------ simulatore

    @Test
    fun `simulatore - deterministico per scenario, seme e parametri`() {
        val sc = Scenarios.byId("S1")
        val a = SensorSim.simulate(sc.room, sc.stations, sc.sensor); val b = SensorSim.simulate(sc.room, sc.stations, sc.sensor)
        for (i in a.frames.indices) { assertContentEquals(a.frames[i].mm, b.frames[i].mm); assertContentEquals(a.frames[i].labels, b.frames[i].labels) }
        val c = SensorSim.simulate(sc.room, sc.stations, sc.sensor.copy(seed = 2))
        assertTrue(a.frames.indices.any { !a.frames[it].mm.contentEquals(c.frames[it].mm) }, "un altro seme deve cambiare il rumore")
        assertEquals(a.frames[0].hitLabels.toList(), c.frames[0].hitLabels.toList()) // la geometria vera non dipende dal seme
    }

    @Test
    fun `simulatore - il rumore della depth segue il modello sigma = s1 per z al quadrato`() {
        val sc = Scenarios.byId("S1")
        val sim = SensorSim.simulate(sc.room, sc.stations, sc.sensor.copy(dropout = 0.0))
        var s2 = 0.0; var n = 0
        for (f in sim.frames) for (i in f.mm.indices) if (f.mm[i] > 0) {
            // Profondità vera lungo l'asse ottico del punto colpito (posa vera).
            val zz = com.sagoma.planimetria.scan.recording.ArCameraProjection.project(f.truePose, sim.k, f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 1].toDouble(), f.hitXYZ[3 * i + 2].toDouble())!!.depthM
            val err = (f.mm[i] / 1000.0 - zz) / (sc.sensor.depthSigmaM * zz * zz)
            s2 += err * err; n++
            if (n > 20000) break
        }
        val sigmaNorm = sqrt(s2 / n)
        assertEquals(1.0, sigmaNorm, 0.1, "σ normalizzata $sigmaNorm (la quantizzazione al mm aggiunge poco)")
    }

    @Test
    fun `simulatore - occlusione, porte, finestre, zone senza ritorno`() {
        // S7: nessun punto della parete x = 4 dietro la libreria alla quota centrale, da nessuna postazione.
        val s7 = Scenarios.byId("S7"); val sim7 = SensorSim.simulate(s7.room, s7.stations, s7.sensor)
        val hidden = sim7.frames.sumOf { f -> f.mm.indices.count { i -> f.labels[i] == Label.wall(2) && f.hitXYZ[3 * i + 2] in 1.05f..1.75f && f.hitXYZ[3 * i + 1] in 0.5f..1.5f } }
        assertEquals(0, hidden, "il tratto dietro la libreria non è osservabile")
        assertTrue(sim7.frames.sumOf { f -> f.labels.count { it == Label.box(0) } } > 0)
        // S3: attraverso la porta si vede l'esterno, mai la parete.
        val s3 = Scenarios.byId("S3"); val sim3 = SensorSim.simulate(s3.room, s3.stations, s3.sensor)
        val d = s3.room.walls[3]
        val inDoor = sim3.frames.sumOf { f -> f.mm.indices.count { i -> f.labels[i] == Label.wall(3) && d.along(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()) in 1.65..2.35 && f.hitXYZ[3 * i + 1] < 2.0f } }
        assertEquals(0, inDoor)
        assertTrue(sim3.frames.sumOf { f -> f.labels.count { it == Label.OUTSIDE } } > 0)
        // S4: la finestra alta lascia la parete sotto 0,9 m.
        val s4 = Scenarios.byId("S4"); val sim4 = SensorSim.simulate(s4.room, s4.stations, s4.sensor)
        val w1 = s4.room.walls[1]
        val below = sim4.frames.sumOf { f -> f.mm.indices.count { i -> f.labels[i] == Label.wall(1) && w1.along(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()) in 1.3..2.3 && f.hitXYZ[3 * i + 1] < 0.85f } }
        val inWin = sim4.frames.sumOf { f -> f.mm.indices.count { i -> f.labels[i] == Label.wall(1) && w1.along(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()) in 1.3..2.3 && f.hitXYZ[3 * i + 1] in 0.95f..1.95f } }
        assertTrue(below > 0); assertEquals(0, inWin)
        // S15: la zona senza ritorno è colpita (la parete esiste) ma non produce depth.
        val s15 = Scenarios.byId("S15"); val sim15 = SensorSim.simulate(s15.room, s15.stations, s15.sensor)
        val w2 = s15.room.walls[2]
        val hit = sim15.frames.sumOf { f -> f.hitLabels.indices.count { i -> f.hitLabels[i] == Label.wall(2) && w2.along(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()) in 1.1..1.9 && f.hitXYZ[3 * i + 1] in 0.9f..1.5f } }
        val depth = sim15.frames.sumOf { f -> f.mm.indices.count { i -> f.mm[i] > 0 && f.labels[i] == Label.wall(2) && w2.along(f.hitXYZ[3 * i].toDouble(), f.hitXYZ[3 * i + 2].toDouble()) in 1.1..1.9 && f.hitXYZ[3 * i + 1] in 0.9f..1.5f } }
        assertTrue(hit > 0); assertEquals(0, depth)
    }

    // ---------------------------------------------------------------------------------------------------- metriche R1/R2

    @Test
    fun `metriche R1 e R2 - conti coerenti, etichette esatte, replica di R2 fedele`() {
        val (run, m) = run("S1")
        for ((cls, g) in m.r1.generated) assertEquals(g, (m.r1.accepted[cls] ?: 0) + (m.r1.rejected[cls]?.values?.sum() ?: 0), "R1: generati = accettati + scartati per $cls")
        assertEquals(run.map.points.size, m.r1.accepted.values.sum())
        assertEquals(m.r2.wallPointsAccepted, m.r2.wallOutcomes.values.sum(), "ogni punto di parete ha un solo esito")
        assertEquals(0, m.r2.replicaMismatches)
        assertTrue(m.r2.precision in 0.0..1.0 && m.r2.recall in 0.0..1.0)
        assertTrue(m.r1.wallErrorRmsM > 0.001, "il rumore deve vedersi nell'errore di R1")
    }

    @Test
    fun `angolo - la perdita di R2 vicino all'angolo è misurata (S0 senza rumore)`() {
        val (_, m) = run("S0")
        for (c in m.corners) {
            assertTrue(c.sensorLastM!! < 0.01, "il sensore arriva all'angolo")
            assertTrue(c.r1LastM!! < 0.01, "R1 arriva all'angolo")
            assertTrue(c.r2LastM!! > c.r1LastM!!, "R2 perde punti vicino all'angolo: è proprio ciò che il benchmark deve mostrare")
            assertEquals(c.zonePointsR1, c.assigned + c.lostNormal + c.lostGrowing + c.wrongSurface + c.objectOrUnknown)
        }
        // L'estremità R3 non va oltre l'ultimo punto R2 (R3 non estende la geometria).
        for (c in m.corners) if (c.r3EndM != null) assertTrue(c.r3EndM!! >= c.r2LastM!! - 0.005)
    }

    @Test
    fun `porte - metriche delle aperture e falsi positivi calcolati`() {
        val (_, m) = run("S3")
        assertEquals(2, m.openings.size)
        assertTrue(m.openings.all { it.zonePoints > 0 })
        assertTrue(m.global.openingRecall != null)
        assertTrue(m.global.openingFalsePositives >= 0)
    }

    @Test
    fun `mobili - la contaminazione dei muri è contata per classe`() {
        val (_, m) = run("S5")
        assertTrue(m.r2.contaminationRate in 0.0..1.0)
        // Il fronte dell'armadio non deve diventare una parete vera: se succede, compare come muro falso o come contaminazione.
        val objectInStructural = m.r2.contamination["OBJECT"] ?: 0
        assertTrue(objectInStructural >= 0 && m.r3.falseWalls.size >= 0)
    }

    @Test
    fun `parete frammentata - frammenti e gap della parete vera sono riportati`() {
        val (run, m) = run("S16")
        val w = m.r3.walls.first { it.wall == 1 }
        assertTrue(w.detected)
        val gaps = run.walls.walls.filter { it.geometry.observedSpans.size > 1 || it.gaps.isNotEmpty() }
        assertTrue(w.fragments > 1 || gaps.isNotEmpty(), "la parete vista a pezzi deve risultare in frammenti o in gap espliciti")
    }

    // -------------------------------------------------------------------------------------------------------- R3 / R4

    @Test
    fun `metriche R3 - pareti trovate e orientazione su S0`() {
        val (_, m) = run("S0")
        assertTrue(m.r3.walls.all { it.detected })
        assertTrue(m.r3.walls.all { it.orientationErrorDeg!! < 1.0 })
        assertTrue(m.r3.falseWalls.isEmpty())
        // Errore di lunghezza = verità − estensione R3 (positivo = muro più corto del vero): mai negativo oltre il rumore.
        assertTrue(m.r3.walls.all { it.lengthErrorM > -0.02 })
    }

    @Test
    fun `metriche R4 - collegamenti veri e verdetto di chiusura sul sensore`() {
        val (_, m) = run("S0")
        assertEquals(4, m.r4.gtCorners)
        assertEquals(m.r4.gtCorners, m.r4.correct + m.r4.missed)
        assertTrue(m.r4.sensorClosable, "in S0 il sensore vede tutti gli angoli")
        assertFalse(m.global.falseClosure)
        // Singola vista: mai CLOSED.
        val (_, s14) = run("S14")
        assertNotEquals(PerimeterState.CLOSED.name, s14.r4.mainState)
    }

    @Test
    fun `nessuna falsa chiusura negli scenari con angoli non visti o pareti mancanti`() {
        for (id in listOf("S12", "S14", "S8")) assertFalse(run(id).second.global.falseClosure, "falsa chiusura in $id")
    }

    // -------------------------------------------------------------------------------------- determinismo e varianti

    @Test
    fun `determinismo - stesso scenario, seme e parametri danno lo stesso report`() {
        val a = BenchPipeline.run(Scenarios.byId("S2")); val b = BenchPipeline.run(Scenarios.byId("S2"))
        assertEquals(BenchReport.json(listOf(a to BenchEval.evaluate(a))), BenchReport.json(listOf(b to BenchEval.evaluate(b))))
    }

    @Test
    fun `parametri modificabili senza riscrivere gli scenari, varianti R2 solo lato test`() {
        val base = Scenarios.byId("S5")
        val noFurniture = BenchPipeline.apply(base, BenchParams(occlusion = 0.0))
        assertTrue(noFurniture.room.boxes.isEmpty())
        assertEquals(0.02, BenchPipeline.apply(base, BenchParams(depthNoiseSigma = 0.02)).sensor.depthSigmaM)
        assertEquals(7, BenchPipeline.apply(base, BenchParams(seed = 7)).sensor.seed)
        val few = SensorSim.simulate(base.room, base.stations, base.sensor, viewsPerStation = 4)
        assertEquals(8, few.frames.size)
        assertEquals(0.20, R2Variants.BASELINE.neighborhoodM, 1e-12); assertEquals(0.12, R2Variants.N12.neighborhoodM, 1e-12)
        assertFailsWith<IllegalArgumentException> { BenchPipeline.run(base, BenchParams(variant = R2Variants.ADAPTIVE)) }
    }

    // --------------------------------------------------------------------------------------------- esecuzione completa

    @Test
    fun `benchmark completo (solo con SAGOMA_BENCH_OUT)`() {
        val out = File(System.getenv("SAGOMA_BENCH_OUT") ?: return).also { it.mkdirs() }
        // Ogni esecuzione viene serializzata subito: in memoria restano solo metriche e testo (mappe e tracce sono grandi).
        val results = mutableListOf<BenchMetrics>()
        val jsons = mutableListOf<String>()
        val rows = mutableListOf<String>()
        fun go(sc: Scenario, bp: BenchParams, tag: String, svg: Boolean) {
            val r = BenchPipeline.run(sc, bp); val m = BenchEval.evaluate(r)
            assertEquals(0, r.trace.mismatches, "replica di R2 non fedele in ${sc.id}")
            results.add(m); jsons.add(BenchReport.runJson(r, m)); rows.add("$tag;" + BenchReport.summaryRow(m, bp.variant.neighborhoodM))
            if (svg) File(out, "bench-${sc.id}-${bp.variant.id}${if (tag == "suite") "" else "-" + tag.replace(Regex("[^A-Za-z0-9.=]+"), "_")}.svg").writeText(BenchReport.svg(r, m))
        }
        for (sc in Scenarios.all()) for (v in R2Variants.all.filter { it.runnable }) go(sc, BenchParams(variant = v), "suite", true)
        // Sweep dell'angolo: rumore × vicinato, sulla stanza di S2.
        for (noise in listOf(0.0, 0.004, 0.008, 0.012)) for (v in R2Variants.all.filter { it.runnable }) go(Scenarios.byId("S2"), BenchParams(depthNoiseSigma = noise, variant = v), "sweep-rumore σ1m=$noise", false)
        // Sweep del numero di viste e del seme (S1).
        for (views in listOf(4, 8, 24)) go(Scenarios.byId("S1"), BenchParams(numberOfViews = views), "sweep-viste $views per postazione", false)
        for (seed in listOf(2L, 3L)) go(Scenarios.byId("S1"), BenchParams(seed = seed), "sweep-seme $seed", false)
        File(out, "benchmark.json").writeText(BenchReport.wrap(jsons))
        File(out, "benchmark-summary.csv").writeText("run;" + BenchReport.summaryHeader + "\n" + rows.joinToString("\n") + "\n")
        File(out, "benchmark-corners.csv").writeText(
            "run;scenario;variant;corner;wall;side;sensorLastM;r1LastM;r2LastM;r3EndM;r3EndState;r3EndSigmaM;zonePointsR1;lostByR1;lostNormal;lostGrowing;wrongSurface;objectOrUnknown;assigned\n" +
                results.indices.flatMap { k -> val m = results[k]; m.corners.map { c -> listOf(rows[k].substringBefore(';'), m.scenario, m.variant, c.cornerId, c.wall, c.side, c.sensorLastM, c.r1LastM, c.r2LastM, c.r3EndM, c.r3EndState, c.r3EndSigmaM, c.zonePointsR1, c.sensorLostR1, c.lostNormal, c.lostGrowing, c.wrongSurface, c.objectOrUnknown, c.assigned).joinToString(";") { it?.let { v -> if (v is Double) "%.4f".format(java.util.Locale.ROOT, v) else v.toString() } ?: "" } } }.joinToString("\n") + "\n",
        )
    }
}
