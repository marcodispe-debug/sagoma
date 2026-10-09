package com.sagoma.planimetria.scan.reconstruction.rescan

import com.sagoma.planimetria.scan.reconstruction.DatasetBlobs
import com.sagoma.planimetria.scan.reconstruction.RoomReconstruction
import com.sagoma.planimetria.scan.reconstruction.quality.QualityEngine
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.RecordingFormatException
import java.io.File
import java.io.PrintStream
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * M5 — RescanDirector dal computer, su una registrazione M0.2 (cartella o ZIP). Esegue la pipeline esistente R1 → R4.1 (invariata,
 * senza le ricostruzioni di stabilità), M4 e M5. Non scrive nessuno dei report esistenti.
 *
 *   RescanCli <registrazione.zip | cartella> [--out=<cartella>]
 *
 * Nella cartella (di default `rescan-<nome>` accanto alla registrazione): `rescan-report.json`, `rescan-requests.csv`,
 * `rescan-summary.txt`, `rescan-topdown.svg` e `rescan-timing.txt` (tempi, separati perché variano tra esecuzioni).
 */
object RescanCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args, PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        if (code != 0) exitProcess(code)
    }

    fun run(args: Array<String>, out: PrintStream): Int {
        val input = args.firstOrNull { !it.startsWith("--") } ?: run { out.println("Uso: RescanCli <registrazione.zip|cartella> [--out=<cartella>]"); return 2 }
        val file = File(input)
        if (!file.exists()) { out.println("Non trovato: $input"); return 2 }
        val outDir = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")?.let(::File)
            ?: File(file.absoluteFile.parentFile, "rescan-" + file.nameWithoutExtension)
        val dataset = try { CaptureDatasetFiles.open(file) } catch (e: RecordingFormatException) { out.println("Registrazione non valida: ${e.message}"); return 1 }
        val blobs = cachedBlobs(file, dataset)
        val t0 = System.nanoTime()
        val res = RoomReconstruction.run(dataset.recording, blobs, dataset.files, stabilityRuns = false)
        val walls = res.walls ?: run { out.println("R3 non calcolato: nessuna decisione"); return 1 }
        val t1 = System.nanoTime()
        val quality = QualityEngine.evaluate(res.map, res.surfaces, walls, res.perimeter)
        val t2 = System.nanoTime()
        val plan = RescanDirector.plan(res.surfaces, walls, res.perimeter, quality)
        val t3 = System.nanoTime()
        outDir.mkdirs()
        val summary = RescanReport.summary(plan, file.name)
        File(outDir, "rescan-summary.txt").writeText(summary)
        File(outDir, "rescan-report.json").writeText(RescanReport.json(plan))
        File(outDir, "rescan-requests.csv").writeText(RescanReport.csv(plan))
        File(outDir, "rescan-topdown.svg").writeText(RescanReport.topDown(plan, walls, file.name))
        val timing = "Tempi: pipeline R1→R4.1 ${"%.2f".format((t1 - t0) / 1e9)} s · M4 ${"%.1f".format((t2 - t1) / 1e6)} ms · M5 ${"%.1f".format((t3 - t2) / 1e6)} ms\n"
        File(outDir, "rescan-timing.txt").writeText(timing)
        out.println(summary + "\n" + timing)
        out.println("File scritti in: ${outDir.absolutePath}")
        return 0
    }

    /** Lettura dei file del dataset: da cartella, o dallo ZIP aperto una volta sola. */
    private fun cachedBlobs(file: File, dataset: CaptureDatasetFiles): DatasetBlobs {
        if (!file.name.endsWith(".zip", ignoreCase = true)) return DatasetBlobs { dataset.read(it) }
        val zip = ZipFile(file)
        val prefix = zip.entries().asSequence().first { it.name.endsWith("recording.jsonl") }.name.removeSuffix("recording.jsonl")
        Runtime.getRuntime().addShutdownHook(Thread { zip.close() })
        return DatasetBlobs { path -> zip.getEntry(prefix + path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
    }
}
