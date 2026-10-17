package com.sagoma.planimetria.scan.reconstruction.quality

import com.sagoma.planimetria.scan.reconstruction.DatasetBlobs
import com.sagoma.planimetria.scan.reconstruction.RoomReconstruction
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.RecordingFormatException
import java.io.File
import java.io.PrintStream
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * M4 — QualityEngine dal computer, su una registrazione M0.2 (cartella o ZIP). Esegue la pipeline esistente R1 → R4.1 (invariata,
 * senza le ricostruzioni di stabilità) e valuta la qualità. Non scrive nessuno dei report esistenti di ReconstructCli.
 *
 *   QualityCli <registrazione.zip | cartella> [--out=<cartella>]
 *
 * Nella cartella (di default `quality-<nome>` accanto alla registrazione): `quality-report.json`, `wall-quality.csv`,
 * `room-quality.csv`, `quality-defects.csv`, `quality-summary.txt`, `quality-topdown.svg` e `quality-timing.txt` (tempi e memoria).
 */
object QualityCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args, PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        if (code != 0) exitProcess(code)
    }

    fun run(args: Array<String>, out: PrintStream): Int {
        val input = args.firstOrNull { !it.startsWith("--") } ?: run { out.println("Uso: QualityCli <registrazione.zip|cartella> [--out=<cartella>]"); return 2 }
        val file = File(input)
        if (!file.exists()) { out.println("Non trovato: $input"); return 2 }
        val outDir = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")?.let(::File)
            ?: File(file.absoluteFile.parentFile, "quality-" + file.nameWithoutExtension)
        val dataset = try { CaptureDatasetFiles.open(file) } catch (e: RecordingFormatException) { out.println("Registrazione non valida: ${e.message}"); return 1 }
        val blobs = cachedBlobs(file, dataset)
        val t0 = System.nanoTime()
        val res = RoomReconstruction.run(dataset.recording, blobs, dataset.files, stabilityRuns = false)
        val t1 = System.nanoTime()
        val walls = res.walls ?: run { out.println("R3 non calcolato: nessuna valutazione"); return 1 }
        val rt = Runtime.getRuntime(); val before = rt.totalMemory() - rt.freeMemory()
        val q = QualityEngine.evaluate(res.map, res.surfaces, walls, res.perimeter)
        val t2 = System.nanoTime(); val after = rt.totalMemory() - rt.freeMemory()
        outDir.mkdirs()
        val title = file.name
        val timing = "\nTempi: pipeline R1→R4.1 ${"%.2f".format((t1 - t0) / 1e9)} s · QualityEngine ${"%.1f".format((t2 - t1) / 1e6)} ms · " +
            "memoria allocata dal QualityEngine (indicativa, heap JVM) ${"%.1f".format((after - before) / 1e6)} MB\n"
        val summary = QualityReport.summary(q, title)
        File(outDir, "quality-summary.txt").writeText(summary)
        // I tempi variano tra esecuzioni: file separato, così gli altri output restano deterministici.
        File(outDir, "quality-timing.txt").writeText(timing.trimStart())
        File(outDir, "quality-report.json").writeText(QualityReport.json(q))
        File(outDir, "wall-quality.csv").writeText(QualityReport.wallCsv(q))
        File(outDir, "room-quality.csv").writeText(QualityReport.roomCsv(q))
        File(outDir, "quality-defects.csv").writeText(QualityReport.defectsCsv(q))
        File(outDir, "quality-topdown.svg").writeText(QualityReport.topDown(q, walls, title))
        out.println(summary + timing)
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
