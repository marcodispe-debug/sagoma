package com.sagoma.planimetria.scan.reconstruction.rescan.session

import com.sagoma.planimetria.scan.reconstruction.DatasetBlobs
import com.sagoma.planimetria.scan.reconstruction.RoomReconstruction
import com.sagoma.planimetria.scan.reconstruction.quality.QualityEngine
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanDirector
import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.RecordingFormatException
import java.io.File
import java.io.PrintStream
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * M5.1 — aggiorna una sessione di riscansione con la registrazione corrente. Esegue in memoria la pipeline esistente R1 → R4.1,
 * M4 e M5 (invariati, senza ricostruzioni di stabilità), poi confronta con la sessione precedente. Non scrive altri report.
 *
 *   RescanSessionCli <registrazione corrente.zip | cartella> [--session=<rescan-session.json precedente>] [--out=<cartella>]
 *
 * Scrive `rescan-session.json`, `rescan-session-summary.txt`, `rescan-session-events.csv`.
 */
object RescanSessionCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args, PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        if (code != 0) exitProcess(code)
    }

    fun run(args: Array<String>, out: PrintStream): Int {
        val input = args.firstOrNull { !it.startsWith("--") } ?: run { out.println("Uso: RescanSessionCli <registrazione.zip|cartella> [--session=<json>] [--out=<cartella>]"); return 2 }
        val file = File(input)
        if (!file.exists()) { out.println("Non trovato: $input"); return 2 }
        val previous = args.firstOrNull { it.startsWith("--session=") }?.removePrefix("--session=")?.let { p ->
            val f = File(p); if (!f.exists()) { out.println("Sessione non trovata: $p"); return 2 }
            RescanSessionJson.decode(f.readText())
        }
        val outDir = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")?.let(::File)
            ?: File(file.absoluteFile.parentFile, "rescan-session-" + file.nameWithoutExtension)
        val dataset = try { CaptureDatasetFiles.open(file) } catch (e: RecordingFormatException) { out.println("Registrazione non valida: ${e.message}"); return 1 }
        val blobs = cachedBlobs(file, dataset)
        val r = dataset.recording
        val res = RoomReconstruction.run(r, blobs, dataset.files, stabilityRuns = false)
        val walls = res.walls ?: run { out.println("R3 non calcolato"); return 1 }
        val quality = QualityEngine.evaluate(res.map, res.surfaces, walls, res.perimeter)
        val plan = RescanDirector.plan(res.surfaces, walls, res.perimeter, quality)
        val scan = ScanInput(file.nameWithoutExtension, r.header.createdAtMillis, r.poses, r.depth, walls.walls, quality, plan, RescanDirector.perimeterGeometry(res.perimeter))
        val session = RescanSessionEngine.update(previous, scan)
        val problems = RescanSessionEngine.validate(previous, session)
        if (problems.isNotEmpty()) { out.println("Sessione non valida (transizioni non ammesse):\n" + problems.joinToString("\n")); return 1 }
        outDir.mkdirs()
        File(outDir, "rescan-session.json").writeText(RescanSessionJson.encode(session))
        File(outDir, "rescan-session-events.csv").writeText(RescanSessionReport.eventsCsv(session))
        val summary = RescanSessionReport.summary(session)
        File(outDir, "rescan-session-summary.txt").writeText(summary)
        out.println(summary)
        out.println("File scritti in: ${outDir.absolutePath}")
        return 0
    }

    private fun cachedBlobs(file: File, dataset: CaptureDatasetFiles): DatasetBlobs {
        if (!file.name.endsWith(".zip", ignoreCase = true)) return DatasetBlobs { dataset.read(it) }
        val zip = ZipFile(file)
        val prefix = zip.entries().asSequence().first { it.name.endsWith("recording.jsonl") }.name.removeSuffix("recording.jsonl")
        Runtime.getRuntime().addShutdownHook(Thread { zip.close() })
        return DatasetBlobs { path -> zip.getEntry(prefix + path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
    }
}
