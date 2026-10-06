package com.sagoma.planimetria.scan.recording.analysis

import com.sagoma.planimetria.scan.recording.RecordingFormatException
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Analizza una registrazione di scansione dal computer, senza telefono.
 *
 *   AnalyzeRecordingCli <registrazione.jsonl> [--out=<cartella>] [--min-persistence=<secondi>] [--no-points]
 *
 * Nella cartella (di default `analysis-<nome file>` accanto alla registrazione) scrive: `report.txt` (tutti i piani verticali),
 * `report-1s.txt`, `report-5s.txt`, `report-10s.txt`, `topdown-all.svg`, `topdown-1s.svg`, `topdown-5s.svg`, `topdown-10s.svg` e
 * `planes.csv`. Stampa a schermo il report con la soglia `--min-persistence` (default 0). Non modifica né copia la registrazione.
 */
object AnalyzeRecordingCli {
    @JvmStatic
    fun main(args: Array<String>) {
        // Il report ha lettere accentate e simboli: si stampa in UTF-8 (i file scritti lo sono sempre; la console potrebbe no).
        val code = run(args, PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        if (code != 0) exitProcess(code)
    }

    fun run(args: Array<String>, out: PrintStream): Int {
        val input = args.firstOrNull { !it.startsWith("--") }
        if (input == null) {
            out.println("Uso: AnalyzeRecordingCli <registrazione.jsonl> [--out=<cartella>] [--min-persistence=<secondi>] [--no-points]")
            return 2
        }
        val file = File(input)
        if (!file.isFile) { out.println("File non trovato: $input"); return 2 }
        val outDir = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")?.let(::File)
            ?: File(file.absoluteFile.parentFile, "analysis-" + file.nameWithoutExtension)
        val minSeconds = args.firstOrNull { it.startsWith("--min-persistence=") }?.removePrefix("--min-persistence=")?.toDoubleOrNull() ?: 0.0
        val points = "--no-points" !in args

        val recording = try {
            ScanRecordingJson.decode(file.readText(Charsets.UTF_8))
        } catch (e: RecordingFormatException) {
            out.println("Registrazione non valida: ${e.message}")
            return 1
        }
        val analysis = RecordingAnalyzer.analyze(recording)
        outDir.mkdirs()
        val title = file.name
        File(outDir, "report.txt").writeText(RecordingReport.text(analysis, 0, title))
        File(outDir, "planes.csv").writeText(RecordingReport.csv(analysis))
        for ((name, ms) in PersistenceFilters.ALL) {
            val suffix = if (ms == 0L) "all" else name
            if (ms > 0L) File(outDir, "report-$name.txt").writeText(RecordingReport.text(analysis, ms, title))
            val svgTitle = "$title — piani verticali ≥ ${if (ms == 0L) "0 s (tutti)" else name}"
            File(outDir, "topdown-$suffix.svg").writeText(TopDownSvg.render(analysis, ms, SvgOptions(showPoints = points, title = svgTitle)))
        }
        out.println(RecordingReport.text(analysis, (minSeconds * 1000).toLong(), title))
        out.println()
        out.println("File scritti in: ${outDir.absolutePath}")
        return 0
    }
}
