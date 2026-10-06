package com.sagoma.planimetria.scan.recording.analysis

import com.sagoma.planimetria.scan.recording.ArCoreInfo
import com.sagoma.planimetria.scan.recording.DeviceInfo
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.RecordedFrame
import com.sagoma.planimetria.scan.recording.RecordedPlane
import com.sagoma.planimetria.scan.recording.RecordedPointCloud
import com.sagoma.planimetria.scan.recording.RecordingEnd
import com.sagoma.planimetria.scan.recording.RecordingHeader
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import com.sagoma.planimetria.scan.recording.SessionInfo
import com.sagoma.planimetria.scan.recording.rotate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Registrazione sintetica di una stanza 4 × 3 m percorsa a 0,5 m/s: 200 frame (20 s), con piani che nascono, crescono, vengono assorbiti e spariscono. */
internal object SyntheticScan {
    private val s = sqrt(0.5)
    // Posa che porta l'asse Y locale (la normale) lungo: +z, −z, +x, −x.
    private val plusZ = Triple(s, 0.0, 0.0) to s        // +90° attorno a x
    private val minusZ = Triple(-s, 0.0, 0.0) to s
    private val plusX = Triple(0.0, 0.0, -s) to s       // −90° attorno a z
    private val minusX = Triple(0.0, 0.0, s) to s

    private fun pose(q: Pair<Triple<Double, Double, Double>, Double>, x: Double, y: Double, z: Double) =
        RecPose(x, y, z, q.first.first, q.first.second, q.first.third, q.second)

    /** Muro lungo x (normale ±z): poligono locale = (lungo, alto). */
    private fun alongX(key: Int, q: Pair<Triple<Double, Double, Double>, Double>, cx: Double, z: Double, len: Double, h: Double, by: Int? = null, tracking: String = "TRACKING"): RecordedPlane {
        val p = pose(q, cx, 1.0, z)
        val n = p.rotateNormal()
        return RecordedPlane(key, "VERTICAL", tracking, by, p, n, len, h, listOf(-len / 2, -h / 2, len / 2, -h / 2, len / 2, h / 2, -len / 2, h / 2))
    }

    /** Muro lungo z (normale ±x): in locale x è l'altezza e z la lunghezza. */
    private fun alongZ(key: Int, q: Pair<Triple<Double, Double, Double>, Double>, x: Double, cz: Double, len: Double, h: Double): RecordedPlane {
        val p = pose(q, x, 1.0, cz)
        return RecordedPlane(key, "VERTICAL", "TRACKING", null, p, p.rotateNormal(), h, len, listOf(-h / 2, -len / 2, h / 2, -len / 2, h / 2, len / 2, -h / 2, len / 2))
    }

    private fun RecPose.rotateNormal(): List<Double> {
        val r = rotate(0.0, 1.0, 0.0)
        return listOf(r.x, r.y, r.z)
    }

    private fun floorPlane(i: Int) = RecordedPlane(
        10, "HORIZONTAL_UPWARD_FACING", "TRACKING", null, RecPose(2.0, if (i < 100) -1.2 else -1.18, 1.5), listOf(0.0, 1.0, 0.0), 4.0, 3.0,
        listOf(-2.0, -1.5, 2.0, -1.5, 2.0, 1.5, -2.0, 1.5),
    )

    /** Posizione della camera dopo `d` metri sul percorso (0,5, 0,5) → (3,5, 0,5) → (3,5, 2,5) → (0,5, 2,5) → (0,5, 0,5). */
    private fun cameraAt(d: Double): Pair<Double, Double> = when {
        d <= 3.0 -> (0.5 + d) to 0.5
        d <= 5.0 -> 3.5 to (0.5 + (d - 3.0))
        d <= 8.0 -> (3.5 - (d - 5.0)) to 2.5
        else -> 0.5 to (2.5 - (d - 8.0))
    }

    fun frames(): List<RecordedFrame> = (0 until 200).map { i ->
        val (cx, cz) = cameraAt(0.05 * i)
        val paused = i in 100..109
        val planes = buildList {
            add(floorPlane(i))
            if (i >= 5) add(alongX(1, plusZ, 2.0, 0.0, 4.0, 2.5))                                   // muro in alto: da 0,5 s fino alla fine
            if (i >= 40) add(alongZ(2, minusX, 4.0, 1.5, 3.0, 2.5))                                 // muro a destra
            if (i >= 90) add(alongX(3, minusZ, if (i < 120) 1.0 else 2.0, 3.0, if (i < 120) 2.0 else 4.0, 2.5)) // in basso, cresce
            if (i >= 140) add(alongZ(4, plusX, 0.0, 1.5, 3.0, 2.5))                                 // muro a sinistra
            if (i in 20..24) add(alongX(5, plusZ, 1.0, 0.0, 0.5, 0.3))                              // piccolo, effimero
            if (i in 30..99) add(alongX(6, plusZ, 1.0, 0.05, 2.0, 1.0, by = if (i >= 60) 1 else null)) // assorbito dal piano 1 da 6 s
        }
        val points = if (i % 5 == 0) {
            val base = if ((i / 5) % 2 == 0) 100 else 110
            val ids = (base until base + 20).toList()
            RecordedPointCloud(1_000L + i, ids.flatMap { listOf((it - 100) * 0.1, 0.5, 0.0) }, ids.map { 0.5 + (it % 5) * 0.1 }, ids)
        } else null
        RecordedFrame(
            index = i, timestampNs = 1_000_000_000L + i * 100_000_000L, elapsedMs = i * 100L,
            tracking = if (paused) "PAUSED" else "TRACKING", failureReason = if (paused) "EXCESSIVE_MOTION" else null,
            camera = RecPose(cx, 1.4, cz), cameraDisplay = RecPose(cx, 1.4, cz),
            floorY = if (i < 3) null else if (i < 100) -1.2 else -1.18,
            planes = planes, points = points,
        )
    }

    fun recording() = ScanRecording(
        RecordingHeader(
            createdAtMillis = 1L, recordIntervalMs = 100, device = DeviceInfo("Test", "Fantasma 1", 34),
            arcore = ArCoreInfo("1.44.0", "x"), session = SessionInfo(depthMode = "DISABLED", depthSupported = false),
        ),
        frames(), RecordingEnd(frames = 200, durationMs = 20_000),
    )
}

class RecordingAnalysisTest {
    private val rec = SyntheticScan.recording()
    private val a = RecordingAnalyzer.analyze(rec)
    private fun plane(key: Int) = a.planes.first { it.key == key }

    @Test
    fun `parsing - il file scritto e riletto dà la stessa analisi`() {
        val text = ScanRecordingJson.encode(rec)
        val again = RecordingAnalyzer.analyze(ScanRecordingJson.decode(text))
        assertEquals(a, again)
        assertEquals(RecordingReport.text(a), RecordingReport.text(again))
    }

    @Test
    fun `sessione - frame, durata, intervalli e dispositivo`() {
        assertEquals(200, a.frames)
        assertEquals(20_000L, a.durationMs)
        assertEquals(100.0, a.frameIntervalMs.median, 0.0)
        assertEquals("Test Fantasma 1", a.deviceLabel)
        assertEquals(false, a.depthSupported)
        assertEquals(0, a.depthInUseFrames)
    }

    @Test
    fun `persistenza e frame osservati di ogni piano`() {
        assertEquals(19_400L, plane(1).persistenceMs); assertEquals(195, plane(1).observedFrames)
        assertEquals(15_900L, plane(2).persistenceMs); assertEquals(160, plane(2).observedFrames)
        assertEquals(10_900L, plane(3).persistenceMs); assertEquals(110, plane(3).observedFrames)
        assertEquals(5_900L, plane(4).persistenceMs); assertEquals(60, plane(4).observedFrames)
        assertEquals(400L, plane(5).persistenceMs); assertEquals(5, plane(5).observedFrames)
        assertEquals(6_900L, plane(6).persistenceMs); assertEquals(70, plane(6).observedFrames)
        // Il pavimento è un piano orizzontale presente per tutta la registrazione.
        assertEquals(200, plane(10).observedFrames); assertEquals(false, plane(10).isVertical)
        assertEquals(6, a.verticalPlanes.size)
    }

    @Test
    fun `stato di tracking dei piani, sparizioni e assorbimento`() {
        assertEquals(195, plane(1).trackingFrames)
        // Il piano 6: assorbito dal 1 dal frame 60 (6 s), poi sparisce al frame 100.
        val p6 = plane(6)
        assertEquals(1, p6.subsumedBy); assertEquals(6_000L, p6.firstSubsumedMs); assertEquals(40, p6.subsumedFrames)
        assertTrue(p6.disappeared); assertEquals(0, p6.reappearances)
        assertEquals(1, a.subsumedVertical)
        assertNull(plane(1).subsumedBy)
        // Chi è presente all'ultimo frame non è sparito; chi non c'è più, sì.
        assertEquals(false, plane(1).disappeared)
        assertTrue(plane(5).disappeared)
        assertEquals(listOf(5, 6), a.verticalPlanes.filter { it.disappeared }.map { it.key })
    }

    @Test
    fun `un piano che sparisce e ricompare conta le ricomparse`() {
        val frames = SyntheticScan.frames().map { f ->
            // Il piano 1 manca nei frame 50..59.
            if (f.index in 50..59) f.copy(planes = f.planes.filter { it.key != 1 }) else f
        }
        val an = RecordingAnalyzer.analyze(ScanRecording(rec.header, frames, rec.end))
        assertEquals(1, an.planes.first { it.key == 1 }.reappearances)
        assertEquals(185, an.planes.first { it.key == 1 }.observedFrames)
    }

    @Test
    fun `geometria - lunghezza, altezza, direzione, centro e coerenza della normale`() {
        val g1 = plane(1).last!!
        assertEquals(4.0, g1.lengthM, 1e-6); assertEquals(2.5, g1.heightM, 1e-6)
        assertEquals(0.0, g1.headingDeg, 1e-6); assertEquals(0.0, g1.tiltDeg, 1e-6)
        assertEquals(2.0, g1.centerX, 1e-6); assertEquals(0.0, g1.centerZ, 1e-6)
        assertEquals(-0.25, g1.minY, 1e-6); assertEquals(2.25, g1.maxY, 1e-6)
        val g2 = plane(2).last!!
        assertEquals(3.0, g2.lengthM, 1e-6); assertEquals(90.0, g2.headingDeg, 1e-6)
        assertEquals(180.0, g2.normalHeadingDeg, 1e-6) // normale −x
        assertTrue(a.normalMismatchMaxDeg!! < 0.01)
        // Il piano orizzontale non ha geometria di muro.
        assertNull(plane(10).last)
        // Un piano che cresce: 2 m al primo frame, 4 m dopo.
        assertEquals(2.0, plane(3).lengthFirstM!!, 1e-6); assertEquals(4.0, plane(3).lengthLastM!!, 1e-6); assertEquals(4.0, plane(3).lengthMaxM!!, 1e-6)
    }

    @Test
    fun `traiettoria e distanza percorsa, senza i salti del tracking perso`() {
        assertEquals(200, a.trajectory.size)
        assertEquals(0.5, a.trajectory.first().x, 1e-9); assertEquals(0.5, a.trajectory.first().z, 1e-9)
        // 99 passi prima della pausa e 89 dopo, da 5 cm: 9,4 m (il tratto durante la pausa non conta).
        assertEquals(9.4, a.distanceM, 1e-6)
        assertEquals(0.5, a.speed.median, 1e-6)
        assertEquals(1, a.tracking.losses)
        assertEquals(1_000L, a.tracking.longestNotTrackingMs)
        assertEquals(listOf("PAUSED" to 10, "TRACKING" to 190), a.tracking.framesByState)
        assertEquals(listOf("EXCESSIVE_MOTION" to 10), a.tracking.failureReasons)
    }

    @Test
    fun `statistiche di lunghezze, altezze e direzioni`() {
        val all = a.lengths()
        assertEquals(6, all.count); assertEquals(0.5, all.min, 1e-9); assertEquals(4.0, all.max, 1e-9)
        assertEquals(3.0, all.median, 1e-9); assertEquals(2.75, all.mean, 1e-9)
        val stable = a.lengths(10_000)
        assertEquals(3, stable.count); assertEquals(listOf(3.0, 4.0, 4.0).sum() / 3, stable.mean, 1e-9); assertEquals(4.0, stable.median, 1e-9)
        assertEquals(2.5, a.heights(10_000).median, 1e-9)
        val hist = a.headingHistogram(15).filter { it.second > 0 }
        assertEquals(listOf(0, 90), hist.map { it.first })
        assertEquals(4, hist[0].second); assertEquals(10.5, hist[0].third, 1e-9)
        assertEquals(2, hist[1].second); assertEquals(6.0, hist[1].third, 1e-9)
        // Distribuzioni: serie vuota, un valore, rango più vicino.
        assertEquals(0, Dist.of(emptyList()).count)
        assertEquals(7.0, Dist.of(listOf(7.0)).p90, 0.0)
        assertEquals(9.0, Dist.of((1..10).map { it.toDouble() }).p90, 0.0) // rango più vicino: ceil(0,9 · 10) = 9º valore
        assertEquals(1.0, Dist.of((1..10).map { it.toDouble() }).p10, 0.0)
    }

    @Test
    fun `filtri di persistenza - tutti, 1, 5 e 10 secondi`() {
        assertEquals(listOf(6, 5, 5, 3), PersistenceFilters.ALL.map { (_, ms) -> a.vertical(ms).size })
        assertEquals(listOf(1, 2, 3, 4, 6), a.vertical(1_000).map { it.key })
        assertEquals(listOf(1, 2, 3), a.vertical(10_000).map { it.key })
    }

    @Test
    fun `pavimento nel tempo`() {
        assertEquals(197, a.floor.stats.count)
        assertEquals(3, a.floor.framesWithoutFloor)
        assertEquals(-1.2, a.floor.stats.min, 1e-9); assertEquals(-1.18, a.floor.stats.max, 1e-9)
        assertEquals(0.02, a.floor.driftM, 1e-9)
        assertEquals(300L, a.floor.series.first().first)
    }

    @Test
    fun `nuvola di punti - aggiornamenti, campioni, id distinti e densità`() {
        val pc = a.pointCloud
        assertEquals(40, pc.updates); assertEquals(800, pc.totalSamples)
        assertEquals(30, pc.distinctIds) // id 100..129
        assertEquals(20.0, pc.pointsPerUpdate.median, 0.0)
        assertEquals(0.5, pc.confidence.min, 1e-9); assertEquals(0.9, pc.confidence.max, 1e-9)
        val heat = assertNotNull(pc.heat)
        assertEquals(30, heat.counts.sum())
        assertTrue(heat.cols >= 29)
    }

    @Test
    fun `report testuale con le sezioni richieste`() {
        val t = RecordingReport.text(a, 5_000, "prova.jsonl")
        for (needle in listOf("Frame registrati: 200", "Durata: 20.0 s", "Distanza percorsa dalla camera: 9.40 m", "Piani verticali: 6", "assorbiti (subsumed): 1",
            "≥tutti: 6 · ≥1s: 5 · ≥5s: 5 · ≥10s: 3", "Aggiornamenti registrati: 40", "Deriva (ultimo − primo): 0.020 m", "prova.jsonl")) {
            assertTrue(t.contains(needle), "manca \"$needle\" nel report:\n$t")
        }
        // La tabella con soglia 5 s non elenca il piano effimero #5 ma elenca #4.
        val table = t.substringAfter("chiave")
        assertTrue(table.contains("#4 ") && !table.contains("#5 "), table)
        assertTrue(RecordingReport.csv(a).lines().first().startsWith("key;kind"))
        assertEquals(1 + a.planes.size + 1, RecordingReport.csv(a).split('\n').size) // intestazione + piani + riga vuota finale
    }

    @Test
    fun `SVG dall'alto - ben formato, con piani, traiettoria e filtro`() {
        val all = TopDownSvg.render(a, 0)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(all.toByteArray(Charsets.UTF_8)))
        assertEquals("svg", doc.documentElement.nodeName)
        assertTrue(all.contains("<polyline") && all.contains("inizio") && all.contains("fine"))
        for (k in 1..6) assertTrue(all.contains("#$k ·"), "manca l'etichetta del piano $k")
        // Con la soglia di 10 s restano solo i piani 1, 2 e 3.
        val stable = TopDownSvg.render(a, 10_000)
        assertTrue(stable.contains("#1 ·") && stable.contains("#3 ·") && !stable.contains("#4 ·") && !stable.contains("#5 ·"))
        // Il piano assorbito è tratteggiato; le densità della nuvola compaiono solo se richieste.
        assertTrue(all.contains("stroke-dasharray=\"6 4\""))
        assertTrue(all.contains("fill=\"#d32f2f\"") && !TopDownSvg.render(a, 0, SvgOptions(showPoints = false)).contains("fill=\"#d32f2f\""))
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(stable.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `determinismo - stessa registrazione, stessi byte`() {
        val a2 = RecordingAnalyzer.analyze(SyntheticScan.recording())
        assertEquals(a, a2)
        assertEquals(TopDownSvg.render(a, 0), TopDownSvg.render(a2, 0))
        assertEquals(TopDownSvg.render(a, 5_000), TopDownSvg.render(a2, 5_000))
        assertEquals(RecordingReport.csv(a), RecordingReport.csv(a2))
        // Anche invertendo l'ordine dei piani dentro ogni frame (l'analisi è per chiave).
        val shuffled = rec.copy(frames = rec.frames.map { it.copy(planes = it.planes.reversed()) })
        val a3 = RecordingAnalyzer.analyze(shuffled)
        assertEquals(RecordingReport.text(a), RecordingReport.text(a3))
    }

    @Test
    fun `registrazione vuota o senza piani non fa errori`() {
        val empty = RecordingAnalyzer.analyze(ScanRecording(rec.header, emptyList()))
        assertEquals(0, empty.frames); assertEquals(0.0, empty.distanceM, 0.0); assertTrue(empty.planes.isEmpty()); assertNull(empty.pointCloud.heat)
        assertTrue(RecordingReport.text(empty).contains("Frame registrati: 0"))
        val svg = TopDownSvg.render(empty, 0)
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)))
        // Solo camera, nessun piano.
        val noPlanes = RecordingAnalyzer.analyze(rec.copy(frames = rec.frames.map { it.copy(planes = emptyList(), points = null) }))
        assertEquals(0, noPlanes.verticalPlanes.size)
        assertTrue(RecordingReport.text(noPlanes).contains("Piani osservati: 0"))
    }

    @Test
    fun `riga di comando - scrive report e SVG senza toccare la registrazione`() {
        val dir = Files.createTempDirectory("sagoma-analisi").toFile()
        try {
            val file = File(dir, "scan-prova.jsonl").apply { writeText(ScanRecordingJson.encode(rec)) }
            val before = file.readBytes()
            val bytes = ByteArrayOutputStream()
            val code = AnalyzeRecordingCli.run(arrayOf(file.path, "--out=${File(dir, "out").path}", "--min-persistence=5"), PrintStream(bytes, true, "UTF-8"))
            assertEquals(0, code)
            val out = File(dir, "out")
            for (name in listOf("report.txt", "report-1s.txt", "report-5s.txt", "report-10s.txt", "topdown-all.svg", "topdown-1s.svg", "topdown-5s.svg", "topdown-10s.svg", "planes.csv")) {
                assertTrue(File(out, name).isFile && File(out, name).length() > 0, "manca $name")
            }
            assertTrue(bytes.toString("UTF-8").contains("persistenza ≥ 5.0 s"))
            assertTrue(before.contentEquals(file.readBytes()), "la registrazione non deve cambiare")
            // File inesistente e file non valido.
            assertEquals(2, AnalyzeRecordingCli.run(arrayOf(File(dir, "non-c-e.jsonl").path), PrintStream(ByteArrayOutputStream())))
            val bad = File(dir, "rotto.jsonl").apply { writeText("{\"type\":\"header\",\"format\":\"altro\",\"version\":1}\n") }
            assertEquals(1, AnalyzeRecordingCli.run(arrayOf(bad.path, "--out=${File(dir, "o2").path}"), PrintStream(ByteArrayOutputStream())))
            assertEquals(2, AnalyzeRecordingCli.run(emptyArray(), PrintStream(ByteArrayOutputStream())))
        } finally {
            dir.deleteRecursively()
        }
    }
}
