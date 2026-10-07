package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.assisted.AssistedScene.camera
import com.sagoma.planimetria.scan.assisted.AssistedScene.sway
import com.sagoma.planimetria.scan.assisted.AssistedScene.plane
import com.sagoma.planimetria.scan.assisted.AssistedScene.scene
import com.sagoma.planimetria.scan.assisted.AssistedScene.standardCam
import com.sagoma.planimetria.scan.assisted.AssistedScene.wallA
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssistedSimulationTest {
    /** Stanza di prova: muro A guardato, sguardo a vuoto, muro B (non ortogonale) guardato, qualche scatto. */
    private fun frames() = scene(14_000) {
        val ang = Math.toRadians(70.0)
        val b = plane(2, 4.0, 0.0, 4.0 + 3.0 * kotlin.math.cos(ang), 3.0 * kotlin.math.sin(ang))
        planes = { listOf(wallA(), b) }
        cam = { t ->
            when {
                t < 4_000 -> camera(2.0 + 0.02 * sway(t, 1), 1.4, 1.5, 2.0, 1.25, 0.0)
                t < 5_500 -> camera(2.0, 1.4, 1.5, -6.0, 1.25, 1.5)            // sguardo a vuoto
                t < 10_000 -> camera(2.0, 1.4, 1.5, 4.0 + 1.5 * kotlin.math.cos(ang), 1.25, 1.5 * kotlin.math.sin(ang))
                else -> standardCam
            }
        }
        points = { _, i -> if (i == 0) AssistedScene.wallPoints(0.0, 0.02) else null }
    }

    @Test
    fun `la simulazione conta frame proposte e pareti e controlla la direzione di mira`() {
        val r = AssistedSimulation.run(AimFrames.from(frames()))
        assertEquals(281, r.frames)
        assertTrue(r.aimValidFrames > 150)
        // Guardando da una parete all'altra, il raggio rovesciato colpisce a volte il muro opposto: conta la prevalenza.
        assertTrue(r.aimForwardFrames > 5 * r.aimBackwardFrames, "avanti ${r.aimForwardFrames} indietro ${r.aimBackwardFrames}")
        assertEquals(2, r.distinctProposedWalls)
        assertEquals(2, r.autoConfirm.confirmed.size)
        assertTrue(r.noConfirm.confirmed.isEmpty(), "il passo 1 non conferma mai")
        assertTrue(r.noConfirm.episodes.size >= 2)
        assertTrue(r.noConfirm.episodes.all { it.timeToProposeMs in 1_300L..2_200L }, "${r.noConfirm.episodes.map { it.timeToProposeMs }}")
        assertEquals(setOf(1, 2), r.planesAimed.map { it.first }.toSet())
    }

    @Test
    fun `il report il CSV e lo SVG sono completi e ben formati`() {
        val fr = frames()
        val r = AssistedSimulation.run(AimFrames.from(fr))
        val text = AssistedSimulationReport.text(r, "prova.jsonl")
        for (h in listOf("Frame analizzati: 281", "Frame con mira valida", "Controllo della convenzione di mira", "Durata dei periodi continui di mira", "Velocità della camera",
            "Stabilità di direzione", "Stabilità di posizione", "Lunghezza osservata", "Altezza osservata", "Punti di supporto", "RMS dei punti", "Perché non si stabilizza",
            "Stato nel tempo", "Proposte ottenute", "conferma automatica", "Parametri usati")) assertTrue(text.contains(h), "manca: $h")
        assertTrue(!text.contains("ATTENZIONE"))
        val csv = AssistedSimulationReport.csv(r).trim().lines()
        assertEquals(282, csv.size)
        assertTrue(csv.all { it.count { c -> c == ';' } == 18 })
        val svg = AssistedSimulationReport.svg(r, AimFrames.from(fr), "prova <&> .jsonl")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
        assertEquals("svg", doc.documentElement.tagName)
        assertTrue(svg.contains("W2"))
    }

    @Test
    fun `se la mira fosse invertita il report lo segnala`() {
        // Camera con la mira rovesciata: guarda il muro dalla parte opposta (alle sue spalle c'e il muro).
        val back = scene(3_000) { planes = { listOf(wallA()) }; cam = { camera(2.0, 1.4, 2.0, 2.0, 1.25, 6.0) } }
        val r = AssistedSimulation.run(AimFrames.from(back))
        assertEquals(0, r.aimForwardFrames)
        assertTrue(r.aimBackwardFrames > 0)
        assertTrue(AssistedSimulationReport.text(r, "x").contains("ATTENZIONE"))
    }

    @Test
    fun `la riga di comando scrive i tre file e non modifica la registrazione`() {
        val dir = Files.createTempDirectory("sagoma-assistita").toFile()
        try {
            val file = File(dir, "scan-prova.jsonl").apply { writeText(ScanRecordingJson.encode(AssistedScene.recording(frames()))) }
            val before = file.readBytes()
            val out = File(dir, "out")
            val bytes = ByteArrayOutputStream()
            assertEquals(0, AssistedSimulationCli.run(arrayOf(file.path, "--out=${out.path}"), PrintStream(bytes, true, "UTF-8")))
            for (n in listOf("assisted-report.txt", "assisted-timeline.csv", "assisted-topdown.svg")) assertTrue(File(out, n).isFile && File(out, n).length() > 0, "manca $n")
            assertTrue(bytes.toString("UTF-8").contains("Simulazione M2.0"))
            assertTrue(before.contentEquals(file.readBytes()))
            assertEquals(2, AssistedSimulationCli.run(arrayOf(File(dir, "non-c-e.jsonl").path), PrintStream(ByteArrayOutputStream())))
            val bad = File(dir, "rotto.jsonl").apply { writeText("{\"type\":\"header\",\"format\":\"altro\",\"version\":1}\n") }
            assertEquals(1, AssistedSimulationCli.run(arrayOf(bad.path, "--out=${File(dir, "o2").path}"), PrintStream(ByteArrayOutputStream())))
            assertEquals(2, AssistedSimulationCli.run(emptyArray(), PrintStream(ByteArrayOutputStream())))
        } finally {
            dir.deleteRecursively()
        }
    }
}
