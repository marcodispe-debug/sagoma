package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.DatasetBlobs
import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.ReconParams
import com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AUDIT DIAGNOSTICO (solo test, nessuna modifica di produzione): A) rumore della depth reale contro il modello del simulatore,
 * B) semantica del segnale FREE_SPACE di R2. Sempre: validazione dello stimatore e fedeltà delle repliche. Con
 * SAGOMA_AUDIT_OUT=<cartella> e SAGOMA_AUDIT_ZIPS=<zip1;zip2> scrive CSV, JSON, SVG e il riepilogo.
 */
class NoiseFreeSpaceAuditTest {
    private fun f(v: Double?, d: Int = 4) = if (v == null || v.isNaN()) "" else "%.${d}f".format(Locale.ROOT, v)

    // ------------------------------------------------------------------------------------------ scenari F (FREE_SPACE)

    private val clean = SensorParams()
    private val noisy = Scenarios.byId("S1").sensor
    private fun door() = GtOpening("porta 0,8 m", OpeningKind.DOOR, 1.6, 2.4, 0.0, 2.1)
    private fun window() = GtOpening("finestra 1,2 m", OpeningKind.WINDOW, 1.4, 2.6, 0.9, 2.0)

    /** Scenari diagnostici minimi; la parete esaminata è sempre GT3 (z = 0). Non entrano nella suite del benchmark. */
    fun fScenarios(sensor: SensorParams): List<Scenario> = listOf(
        Scenario("F0", "parete piena", GtRoom("F0", Scenarios.rect()), Scenarios.ring, sensor, "riferimento"),
        Scenario("F1", "parete + porta", GtRoom("F1", Scenarios.rect(openings = mapOf(3 to listOf(door())))), Scenarios.ring, sensor, "raggi attraverso la porta"),
        Scenario("F2", "parete + finestra", GtRoom("F2", Scenarios.rect(openings = mapOf(3 to listOf(window())))), Scenarios.ring, sensor, "finestra"),
        Scenario("F3", "parete + porta, oltre nessuna superficie", GtRoom("F3", Scenarios.rect(openings = mapOf(3 to listOf(door()))), outsideReturns = false), Scenarios.ring, sensor, "apertura senza ritorni oltre: nessun raggio attraversa"),
        Scenario("F4", "parete + mobile davanti", GtRoom("F4", Scenarios.rect(), boxes = listOf(GtBox(0, "mobile 1 m davanti alla parete", 1.4, 0.0, 0.3, 2.6, 1.0, 0.8))), Scenarios.ring, sensor, "occlusione parziale bassa"),
        Scenario("F5", "parete + spazio libero reale davanti (camere vicine)", GtRoom("F5", Scenarios.rect()), listOf(Station(2.0, 1.4, 0.9), Station(2.6, 1.5, 1.2, yawFromDeg = 7.5, yawToDeg = 352.5)), sensor, "molto spazio libero osservato davanti alla parete"),
        Scenario("F5b", "pannello isolato con spazio libero reale DIETRO", GtRoom("F5b", Scenarios.rect(), panels = listOf(GtPanel(0, "pannello isolato alto 1,8 m", 1.0, 1.5, 3.0, 1.5, 0.0, 1.8))), Scenarios.ring, sensor, "controllo: il caso per cui FREE_SPACE ha senso (superficie con spazio libero dietro)"),
        Scenario("F6", "parete occlusa", GtRoom("F6", Scenarios.rect(), boxes = listOf(GtBox(0, "armadio alto davanti", 1.2, 0.0, 0.2, 2.8, 2.3, 0.6))), Scenarios.ring, sensor, "tratto di parete nascosto"),
        Scenario("F7", "parete + porta + mobile davanti alla porta", GtRoom("F7", Scenarios.rect(openings = mapOf(3 to listOf(door()))), boxes = listOf(GtBox(0, "mobile davanti alla porta", 1.5, 0.0, 0.3, 2.5, 1.0, 0.8))), Scenarios.ring, sensor, "apertura in parte nascosta"),
    )

    /** Righe FREE_SPACE di tutte le superfici verticali di un'esecuzione del benchmark (con la verità). */
    private fun fsRows(tag: String, run: BenchRun, details: MutableMap<String, FreeSpaceAudit.Detail>? = null): List<String> {
        val s = run.surfaces
        return s.surfaces.filter { it.orientation == Orientation.VERTICAL }.map { sf ->
            val idx = SurfaceExtractor.pointsOf(run.map, sf.memberVoxels)
            val h = HashMap<Int, Int>(); for (i in idx) h.merge(run.pointLabels[i], 1, Int::plus)
            val maj = h.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).first().key
            val wall = if (Label.isWall(maj)) run.scenario.room.walls.first { it.id == Label.wallId(maj) } else null
            val d = FreeSpaceAudit.detail(run.map, sf, run.scenario.room, wall)
            details?.put("$tag S${sf.id}", d)
            row(tag, s, sf, d, Label.cls(maj) + (wall?.let { " GT${it.id}" + if (it.openings.isNotEmpty()) " (con apertura)" else "" } ?: ""), idx.size)
        }
    }

    private fun row(tag: String, s: SurfaceResult, sf: com.sagoma.planimetria.scan.reconstruction.Surface, d: FreeSpaceAudit.Detail, truth: String, points: Int): String {
        val sc = FreeSpaceAudit.score(s, sf, d.freeBehind)
        val cf = FreeSpaceAudit.counterfactual(s, sf, d)
        return listOf(
            tag, "S${sf.id}", sf.kind, truth, points, f(sf.areaM2, 2), f(sf.topAboveFloor, 2), d.samples, d.free, d.occupied, d.unknown, d.outside,
            f(d.freeBehind), f(d.freeBehindProduction), f(d.freeOfAll), f(d.observedFraction),
            f(sc.height, 3), f(sc.bottom, 3), f(sc.freeTerm, 3), f(sc.outermost, 1), f(sc.length, 3), f(sc.arcore, 1), f(sc.score), f(sf.structuralScore), sc.kind,
            cf.noOpeningSamples ?: "", cf.freeOfAll, cf.fullHeight, cf.noOpeningFullHeight ?: "", cf.freeZero,
            d.byTruth.entries.joinToString(" | ") { (k, v) -> "$k: " + v.entries.joinToString(",") { "${it.key}=${it.value}" } },
        ).joinToString(";") { it.toString().replace(";", ",") }
    }

    private val fsHeader = "sorgente;superficie;classe;verita;punti;areaM2;altezzaDalPavimento;campioni;liberi;occupati;ignoti;fuoriGriglia;" +
        "freeBehind;freeBehindProduzione;liberiSuTutti;frazioneOsservata;termAltezza;termBase;term1MenoFree;termEsterna;termLunghezza;termARCore;punteggio;punteggioProduzione;classeReplica;" +
        "cf_senzaCampioniApertura;cf_liberiSuTutti;cf_altezzaPiena;cf_senzaAperturaAltezzaPiena;cf_freeZero;campioniPerVerita"

    // -------------------------------------------------------------------------------------------------------- test sempre attivi

    @Test
    fun `stimatore del rumore - ritrova le leggi note sul sintetico`() {
        val sc = Scenarios.byId("S1")
        for ((sp, expected, a) in listOf(Triple(sc.sensor, "∝ z²", 0.006), Triple(sc.sensor.copy(noiseTable = listOf(0.0 to 0.02, 10.0 to 0.02)), "costante", 0.02))) {
            val sim = SensorSim.simulate(sc.room, sc.stations, sp)
            val map = GlobalMap.build(sim.recording, DatasetBlobs { sim.blobs[it] })
            val smp = NoiseAudit.samples(map, NoiseAudit.references(map, SurfaceExtractor.extract(map, sim.recording)))
            val best = NoiseAudit.fitModels(smp, false).first { !it.name.startsWith("a tratti") }
            assertEquals(expected, best.name, "modello migliore tra i parametrici")
            assertEquals(a, best.a, a * 0.05)
        }
    }

    @Test
    fun `FREE_SPACE - replica esatta di freeBehind e del punteggio di produzione`() {
        for (sc in fScenarios(noisy).filter { it.id in setOf("F0", "F1", "F5b") } + Scenarios.byId("S3")) {
            val run = BenchPipeline.run(sc)
            for (sf in run.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL }) {
                val d = FreeSpaceAudit.detail(run.map, sf)
                assertEquals(sf.freeBehind!!, d.freeBehind, 0.0, "${sc.id} S${sf.id}: freeBehind replicato")
                val s = FreeSpaceAudit.score(run.surfaces, sf, d.freeBehind)
                assertEquals(sf.structuralScore!!, s.score, 1e-12, "${sc.id} S${sf.id}: punteggio replicato")
                assertEquals(sf.kind, s.kind)
            }
        }
    }

    @Test
    fun `F3 - oltre l'apertura nessun ritorno, nessuna cella libera dietro`() {
        val sc = fScenarios(noisy).first { it.id == "F3" }
        val sim = SensorSim.simulate(sc.room, sc.stations, sc.sensor)
        assertEquals(0, sim.frames.sumOf { f -> f.labels.count { it == Label.OUTSIDE } })
        assertTrue(sim.frames.sumOf { f -> f.hitLabels.count { it == Label.OUTSIDE } } > 0)
    }

    // ------------------------------------------------------------------------------------------------- audit completo

    @Test
    fun `audit completo rumore e FREE_SPACE (solo con SAGOMA_AUDIT_OUT)`() {
        val out = File(System.getenv("SAGOMA_AUDIT_OUT") ?: return).also { it.mkdirs() }
        val zips = System.getenv("SAGOMA_AUDIT_ZIPS")?.split(';')?.filter { it.isNotBlank() }.orEmpty()
        val json = StringBuilder("{\n\"note\":\"AUDIT DIAGNOSTICO: nessuna modifica di produzione. Rumore misurato come scarto da piani di riferimento rifittati: è un limite SUPERIORE del rumore del sensore.\",\n")
        val depthRows = mutableListOf<String>(); val modelRows = mutableListOf<String>(); val pixRows = mutableListOf<String>(); val refRows = mutableListOf<String>(); val fsAll = mutableListOf<String>()
        val empirical = LinkedHashMap<String, Pair<List<Pair<Double, Double>>, Double>>() // dataset → (tabella σ intra (z, σ), σ inter-frame mediana)
        val svgData = mutableListOf<Pair<String, List<NoiseAudit.BinRow>>>()
        var densityInfo = ""

        // ----------------------------------------------------------------------------------- A: dati reali
        for (zip in zips) {
            val name = File(zip).nameWithoutExtension
            val ds = CaptureDatasetFiles.open(File(zip))
            val blobs = DatasetBlobs { ds.read(it) }
            val map = GlobalMap.build(ds.recording, blobs)
            val s = SurfaceExtractor.extract(map, ds.recording)
            val refs = NoiseAudit.references(map, s)
            for (r in refs) refRows.add(listOf(name, "S${r.id}", r.kind, f(r.areaM2, 2), r.frames, r.points, f(r.robustSigmaM), f(r.medianResidualM), f(r.quadrantSpreadM), r.accepted, r.reasons.joinToString(" | "), r.arcore ?: "").joinToString(";"))
            val raw = NoiseAudit.samples(map, refs)
            val fmap = GlobalMap.build(ds.recording, blobs, ReconParams(diagnosticFilteredOnly = true))
            val fil = NoiseAudit.samples(fmap, refs, useBandOf = map)
            for ((label, smp) in listOf("raw" to raw, "filtrata" to fil)) {
                val bins = NoiseAudit.bins(smp)
                if (label == "raw") svgData.add(name to bins)
                for (b in bins) depthRows.add(listOf(name, label, f(b.lo, 2), f(b.hi, 2), b.n, b.frames, f(b.sigmaStd), f(b.sigmaRobust), f(b.sigmaIntraRobust), f(b.sigmaInterFrame), f(b.medianConf, 0), f(b.medianIncDeg, 1), f(b.medianZ, 3), b.status, f(if (b.medianZ.isNaN()) null else noisy.depthSigmaM * b.medianZ * b.medianZ)).joinToString(";"))
                for (intra in listOf(false, true)) {
                    val fits = NoiseAudit.fitModels(smp, intra)
                    val best = fits.firstOrNull()?.bic ?: 0.0
                    for (m in fits) modelRows.add(listOf(name, label, if (intra) "intra-frame" else "totale", m.name, m.k, f(m.a, 5), f(m.logLik, 1), f(m.bic, 1), f(m.bic - best, 1)).joinToString(";"))
                }
                if (label == "raw") empirical[name] = bins.filter { it.status == "OK" }.map { it.medianZ to it.sigmaIntraRobust } to bins.filter { it.status == "OK" && !it.sigmaInterFrame.isNaN() }.map { it.sigmaInterFrame }.sorted().let { it[it.size / 2] }
            }
            val (px, info) = NoiseAudit.rawVsFiltered(ds.recording, { ds.read(it) })
            for (p in px) pixRows.add(listOf(name, f(p.lo, 2), f(p.hi, 2), p.filteredValid, p.rawValid, p.rawZero, p.rawLowConf, p.bothValid, f(p.diffMedianM), f(p.diffRobustM), f(p.keptFraction, 3), f(p.pointsPerM2PerFrame, 0)).joinToString(";"))
            densityInfo += "$name: $info\n"
            // B sui dati reali: FREE_SPACE di tutte le verticali (senza verità).
            for (sf in s.surfaces.filter { it.orientation == Orientation.VERTICAL }) fsAll.add(row(name, s, sf, FreeSpaceAudit.detail(map, sf), "(reale: nessuna verità)", SurfaceExtractor.pointsOf(map, sf.memberVoxels).size))
        }
        // Densità del simulatore (stessa formula fx·fy/z² × frazione tenuta), per il confronto.
        run {
            val fx = noisy.width / 2.0 / Math.tan(Math.toRadians(noisy.hfovDeg / 2))
            for (b in 0 until NoiseAudit.binEdges.size - 1) { val zc = (NoiseAudit.binEdges[b] + NoiseAudit.binEdges[b + 1]) / 2; pixRows.add(listOf("SIMULATORE (S1)", f(NoiseAudit.binEdges[b], 2), f(NoiseAudit.binEdges[b + 1], 2), "", "", "", "", "", "", "", f(1 - noisy.dropout, 3), f(fx * fx / (zc * zc) * (1 - noisy.dropout), 0)).joinToString(";")) }
            densityInfo += "SIMULATORE: ${noisy.width}×${noisy.height} pixel, fx·fy ${f(fx * fx, 0)}, dropout ${noisy.dropout}\n"
        }

        // ----------------------------------------------------------------------- A5: parete lontana con modelli alternativi
        val expRows = mutableListOf<String>()
        val s1 = Scenarios.byId("S1")
        val variants = mutableListOf<Pair<String, SensorParams>>("ATTUALE σ=0,6 cm·z², 64×36" to s1.sensor)
        for ((ds, e) in empirical) {
            val table = e.first.sortedBy { it.first }
            variants.add("EMPIRICO intra-frame $ds" to s1.sensor.copy(noiseTable = table))
            variants.add("EMPIRICO intra $ds + posa per frame σ=${f(e.second * 100, 1)} cm" to s1.sensor.copy(noiseTable = table, poseNoiseM = e.second))
            val fxReal = sqrt(12250.0)
            variants.add("EMPIRICO intra $ds + densità reale 160×90 (fx≈${f(fxReal, 0)}), dropout 0,4" to s1.sensor.copy(noiseTable = table, width = 160, height = 90, hfovDeg = Math.toDegrees(2 * atan(80 / fxReal)), dropout = 0.4))
        }
        variants.add("ATTUALE σ=0,6 cm·z² + densità reale 160×90, dropout 0,4" to s1.sensor.copy(width = 160, height = 90, hfovDeg = Math.toDegrees(2 * atan(80 / sqrt(12250.0))), dropout = 0.4))
        for ((label, sp) in variants) {
            val run = BenchPipeline.run(s1.copy(sensor = sp))
            val m = BenchEval.evaluate(run)
            val w0 = m.r3.walls.first { it.wall == 0 }
            val o = m.r2.perWall[0].orEmpty()
            val tot = o.values.sum().coerceAtLeast(1)
            expRows.add(listOf(label, f(run.surfaces.thresholds.noiseM), f(run.surfaces.thresholds.seedMaxVariation), f(run.surfaces.thresholds.growMaxVariation), f(run.surfaces.thresholds.growDistM),
                w0.detected, f((o["TP"] ?: 0).toDouble() / tot, 3), o.entries.sortedByDescending { it.value }.take(3).joinToString(" | ") { "${it.key}=${it.value}" },
                f(m.global.wallRecall, 3), m.r4.mainState, m.r2.wallSurfaces[0].orEmpty().take(2).joinToString(" | ")).joinToString(";") { it.toString().replace(";", ",") })
        }

        // ------------------------------------------------------------------------------- B: scenari F, S3, S4
        val details = LinkedHashMap<String, FreeSpaceAudit.Detail>()
        val fsCount = mutableListOf<String>()
        for (sensor in listOf("rumore S1" to noisy, "senza rumore" to clean)) for (sc in fScenarios(sensor.second)) {
            val run = BenchPipeline.run(sc)
            fsAll.addAll(fsRows("${sc.id} (${sensor.first})", run, if (sc.id == "F1" && sensor.first == "rumore S1") details else null))
            val m = BenchEval.evaluate(run)
            fsCount.add(listOf("${sc.id} ${sc.title} (${sensor.first})", m.r2.perWall[3].orEmpty().entries.sortedBy { it.key }.joinToString(" | ") { "${it.key}=${it.value}" }, m.r3.walls.first { it.wall == 3 }.detected).joinToString(";"))
        }
        // B4: S3/S4 — scomposizione dei punti della parete con apertura.
        val b4 = mutableListOf<String>()
        for (id in listOf("S3", "S4")) {
            val run = BenchPipeline.run(Scenarios.byId(id)); val m = BenchEval.evaluate(run)
            fsAll.addAll(fsRows(id, run))
            for (g in run.scenario.room.walls.filter { it.openings.isNotEmpty() }) {
                val outc = m.r2.perWall[g.id].orEmpty(); val tot = outc.values.sum().coerceAtLeast(1)
                // Punti UNKNOWN per superficie e causa (controfattuali).
                val cause = HashMap<String, Int>()
                for (sf in run.surfaces.surfaces.filter { it.kind == SurfaceKind.UNKNOWN && it.orientation == Orientation.VERTICAL }) {
                    val idx = SurfaceExtractor.pointsOf(run.map, sf.memberVoxels)
                    val mine = idx.count { run.pointLabels[it] == Label.wall(g.id) }
                    if (mine == 0) continue
                    val d = FreeSpaceAudit.detail(run.map, sf, run.scenario.room, g)
                    val cf = FreeSpaceAudit.counterfactual(run.surfaces, sf, d)
                    val st = SurfaceKind.VERTICAL_STRUCTURAL
                    // FREE_SPACE "corretto" = rapporto sulle celle campionate (le ignote non contano come libere): la prova dipende
                    // da quanta parte dietro la superficie è davvero osservata. Altezza = parete vista fino al soffitto.
                    val byFs = cf.freeOfAll == st; val byH = cf.fullHeight == st
                    val sc0 = FreeSpaceAudit.score(run.surfaces, sf, d.freeBehind)
                    val c = when {
                        sc0.weak -> "evidenza debole (campioni/viste/area)"
                        byFs && byH -> "basta FREE_SPACE normalizzato oppure l'altezza piena"
                        byFs -> "solo FREE_SPACE normalizzato"
                        byH -> "solo altezza piena"
                        FreeSpaceAudit.score(run.surfaces, sf, d.freeOfAll, 1.0).kind == st -> "servono entrambi"
                        else -> "nessuno dei due"
                    } + (if (sf.outermost == false) " · e 'superficie parallela più esterna dietro' (vista attraverso l'apertura)" else "") +
                        " · freeBehind ${f(d.freeBehind, 2)} su ${f(d.observedFraction * 100, 0)}% di celle osservate"
                    cause.merge(c, mine, Int::plus)
                }
                val unk = outc["UNKNOWN"] ?: 0
                b4.add(listOf(id, "GT${g.id}", g.openings.joinToString { "${it.kind} ${it.id}" }, tot,
                    "TP=${outc["TP"] ?: 0} (${f(100.0 * (outc["TP"] ?: 0) / tot, 1)}%)", "UNKNOWN=$unk (${f(100.0 * unk / tot, 1)}%)",
                    "perdite di normale/planarità=${(outc["NORMAL_THRESHOLD"] ?: 0) + (outc["INSUFFICIENT_EVIDENCE"] ?: 0)} (${f(100.0 * ((outc["NORMAL_THRESHOLD"] ?: 0) + (outc["INSUFFICIENT_EVIDENCE"] ?: 0)) / tot, 1)}%)",
                    "perdite di crescita/distanza/ordine=${(outc["REGION_GROWING"] ?: 0) + (outc["DISTANCE_THRESHOLD"] ?: 0) + (outc["ASSIGNMENT_ORDER"] ?: 0)} (${f(100.0 * ((outc["REGION_GROWING"] ?: 0) + (outc["DISTANCE_THRESHOLD"] ?: 0) + (outc["ASSIGNMENT_ORDER"] ?: 0)) / tot, 1)}%)",
                    "cause degli UNKNOWN: " + cause.entries.sortedByDescending { it.value }.joinToString(" | ") { "${it.key}=${it.value} (${f(100.0 * it.value / unk.coerceAtLeast(1), 1)}%)" },
                ).joinToString(";") { it.toString().replace(";", ",") })
            }
        }

        // ----------------------------------------------------------------------------------------------------- scrittura
        File(out, "references.csv").writeText("dataset;superficie;classe;areaM2;frame;punti;sigmaRobustaM;mediaScartoM;differenzaQuadrantiM;accettata;motivi;pianoARCore\n" + refRows.joinToString("\n") + "\n")
        File(out, "depth-vs-distance.csv").writeText("dataset;mappa;daM;aM;N;frame;sigmaStdM;sigmaRobustaM;sigmaIntraFrameRobustaM;sigmaInterFrameM;confidenzaMediana;incidenzaMedianaDeg;zMedianaM;stato;sigmaSimulatoreAttualeM\n" + depthRows.joinToString("\n") + "\n")
        File(out, "noise-models.csv").writeText("dataset;mappa;componente;modello;parametri;a;logVerosimiglianza;BIC;deltaBIC\n" + modelRows.joinToString("\n") + "\n")
        File(out, "raw-vs-filtered.csv").writeText("dataset;daM;aM;pixelFiltrataValidi;rawValidi;rawZero;rawConfidenzaBassa;entrambiValidi;differenzaMedianaM;differenzaRobustaM;frazioneTenuta;puntiPerM2PerFrame\n" + pixRows.joinToString("\n") + "\n")
        File(out, "far-wall-experiment.csv").writeText("modello;sigmaStimataR2;sogliaSeme;sogliaCrescitaVariazione;distanzaCrescita;pareteLontanaTrovata;frazioneTP;esitiPrincipali;recallPareti;statoR4;superficiR2DellaParete\n" + expRows.joinToString("\n") + "\n")
        File(out, "freespace.csv").writeText("$fsHeader\n" + fsAll.joinToString("\n") + "\n")
        File(out, "freespace-wall-outcomes.csv").writeText("scenario;esitiR2DellaParete GT3;pareteTrovataDaR3\n" + fsCount.joinToString("\n") + "\n")
        File(out, "s3-s4-decomposition.csv").writeText("scenario;parete;aperture;puntiR1;assegnati;unknown;perditeNormale;perditeCrescita;causeUnknown\n" + b4.joinToString("\n") + "\n")
        json.append("\"density\":").append("\"" + densityInfo.replace("\n", " · ").replace("\"", "'") + "\",\n")
        json.append("\"files\":[\"references.csv\",\"depth-vs-distance.csv\",\"noise-models.csv\",\"raw-vs-filtered.csv\",\"far-wall-experiment.csv\",\"freespace.csv\",\"freespace-wall-outcomes.csv\",\"s3-s4-decomposition.csv\"],\n")
        fun arr(h: String, rows: List<String>) = rows.joinToString(",\n", "[", "]") { r -> val k = h.split(';'); val v = r.split(';'); "{" + k.indices.joinToString(",") { "\"${k[it]}\":\"${v.getOrElse(it) { "" }.replace("\\", "\\\\").replace("\"", "'")}\"" } + "}" }
        json.append("\"depthVsDistance\":").append(arr("dataset;mappa;daM;aM;N;frame;sigmaStdM;sigmaRobustaM;sigmaIntraFrameRobustaM;sigmaInterFrameM;confidenzaMediana;incidenzaMedianaDeg;zMedianaM;stato;sigmaSimulatoreAttualeM", depthRows)).append(",\n")
        json.append("\"noiseModels\":").append(arr("dataset;mappa;componente;modello;parametri;a;logVerosimiglianza;BIC;deltaBIC", modelRows)).append(",\n")
        json.append("\"farWallExperiment\":").append(arr("modello;sigmaStimataR2;sogliaSeme;sogliaCrescitaVariazione;distanzaCrescita;pareteLontanaTrovata;frazioneTP;esitiPrincipali;recallPareti;statoR4;superficiR2DellaParete", expRows)).append(",\n")
        json.append("\"s3s4Decomposition\":").append(arr("scenario;parete;aperture;puntiR1;assegnati;unknown;perditeNormale;perditeCrescita;causeUnknown", b4)).append("\n}\n")
        File(out, "audit.json").writeText(json.toString())
        for ((name, bins) in svgData) File(out, "noise-$name.svg").writeText(noiseSvg(name, bins))
        for ((k, d) in details) if (d.byTruth.containsKey("dietro apertura")) File(out, "freespace-F1-${k.substringAfterLast(' ')}.svg").writeText(fsSvg("F1 parete con porta — campioni di FREE_SPACE di ${k.substringAfterLast(' ')}", d))
    }

    // ------------------------------------------------------------------------------------------------------------- SVG

    private fun noiseSvg(name: String, bins: List<NoiseAudit.BinRow>): String {
        val w = 760; val h = 460; val x0 = 70.0; val y0 = 400.0; val sx = 600 / 3.5; val sy = 340 / 0.08
        fun X(z: Double) = f(x0 + z * sx, 1)
        fun Y(s: Double) = f(y0 - s * sy, 1)
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="14" font-weight="bold">$name — errore della depth (Z) contro la profondità</text>""")
        sb.append("""<line x1="$x0" y1="$y0" x2="${x0 + 600}" y2="$y0" stroke="#000"/><line x1="$x0" y1="$y0" x2="$x0" y2="${y0 - 340}" stroke="#000"/>""")
        for (z in listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5)) sb.append("""<text x="${X(z)}" y="${y0 + 16}">${z} m</text>""")
        for (s in listOf(0.02, 0.04, 0.06, 0.08)) sb.append("""<text x="20" y="${Y(s)}">${(s * 100).toInt()} cm</text><line x1="$x0" y1="${Y(s)}" x2="${x0 + 600}" y2="${Y(s)}" stroke="#EEE"/>""")
        // Modello attuale del simulatore S1 e S2.
        for ((s1, col, lab) in listOf(Triple(0.006, "#D32F2F", "simulatore S1: 0,6 cm·z²"), Triple(0.012, "#F57C00", "simulatore S2: 1,2 cm·z²"))) {
            val pts = (5..35).map { it / 10.0 }.joinToString(" ") { z -> "${X(z)},${Y(minOf(0.08, s1 * z * z))}" }
            sb.append("""<polyline fill="none" stroke="$col" stroke-width="2" stroke-dasharray="5,3" points="$pts"/>""")
        }
        for (b in bins.filter { !it.medianZ.isNaN() }) {
            val ok = b.status == "OK"
            sb.append("""<circle cx="${X(b.medianZ)}" cy="${Y(b.sigmaRobust)}" r="5" fill="${if (ok) "#1565C0" else "none"}" stroke="#1565C0"/>""")
            sb.append("""<rect x="${f(x0 + b.medianZ * sx - 4, 1)}" y="${f(y0 - b.sigmaIntraRobust * sy - 4, 1)}" width="8" height="8" fill="${if (ok) "#2E7D32" else "none"}" stroke="#2E7D32"/>""")
            sb.append("""<text x="${f(x0 + b.medianZ * sx + 7, 1)}" y="${f(y0 - b.sigmaRobust * sy, 1)}" font-size="10" fill="#555">N ${b.n}${if (ok) "" else " (insuff.)"}</text>""")
        }
        var ly = 50
        for ((c, t) in listOf("#1565C0" to "reale: σ robusta totale (cerchio)", "#2E7D32" to "reale: σ robusta intra-frame (quadrato)", "#D32F2F" to "simulatore S1 (tratteggio)", "#F57C00" to "simulatore S2 (tratteggio)")) { sb.append("""<rect x="${x0 + 360}" y="${ly - 9}" width="10" height="10" fill="$c"/><text x="${x0 + 375}" y="$ly">$t</text>"""); ly += 16 }
        sb.append("</svg>")
        return sb.toString()
    }

    private fun fsSvg(title: String, d: FreeSpaceAudit.Detail): String {
        val sc = 180.0; val ox = 60.0; val oz = 80.0
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="900" height="720" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FFF"/>""")
        sb.append("""<text x="20" y="22" font-size="14" font-weight="bold">$title</text><text x="20" y="40" fill="#555">Campioni a 15/25/40 cm dietro la superficie: verde = libera, rosso = occupata, grigio = ignota. Bordo blu = dietro l'apertura vera.</text>""")
        sb.append("""<rect x="${f(ox, 1)}" y="${f(oz + 0.0 * sc, 1)}" width="${f(4 * sc, 1)}" height="${f(3 * sc, 1)}" fill="none" stroke="#000"/>""")
        for ((p, st, tag) in d.points) {
            val col = when (st) { 1 -> "#2E7D32"; 2 -> "#C62828"; 0 -> "#9E9E9E"; else -> "#000" }
            sb.append("""<circle cx="${f(ox + p[0] * sc, 1)}" cy="${f(oz + (p[1] + 0.6) * sc, 1)}" r="3" fill="$col"${if (tag == "dietro apertura") " stroke=\"#1565C0\" stroke-width=\"1.5\"" else ""}/>""")
        }
        sb.append("""<text x="20" y="${700}">freeBehind ${f(d.freeBehind, 3)} (= libere ${d.free} / osservate ${d.free + d.occupied}) · ignote ${d.unknown} di ${d.samples} campioni · frazione osservata ${f(d.observedFraction, 3)} · per verità: ${d.byTruth}</text>""")
        sb.append("</svg>")
        return sb.toString()
    }
}
