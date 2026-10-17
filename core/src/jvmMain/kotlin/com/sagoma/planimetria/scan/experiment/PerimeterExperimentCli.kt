package com.sagoma.planimetria.scan.experiment

import com.sagoma.planimetria.scan.recording.RecordingFormatException
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalyzer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Esperimento M3.1 da riga di comando.
 *
 *   PerimeterExperimentCli <registrazione.jsonl> [--out=<cartella>] [--permutations=20] [--candidates=<file.json>]
 *
 * Scrive nella cartella: `perimeter-report.txt`, `perimeter-with-points.svg`, `perimeter-clean.svg` (senza nuvola di punti),
 * `perimeter-walls.csv`, `candidates.json` (le candidate usate, riutilizzabili con `--candidates`). Con `--candidates` le candidate
 * vengono lette dal file (lista JSON di [WallCandidate]) invece che ricavate dai piani verticali della registrazione.
 */
object PerimeterExperimentCli {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args, PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        if (code != 0) exitProcess(code)
    }

    fun run(args: Array<String>, out: PrintStream): Int {
        val input = args.firstOrNull { !it.startsWith("--") }
        if (input == null) { out.println("Uso: PerimeterExperimentCli <registrazione.jsonl> [--out=<cartella>] [--permutations=20] [--candidates=<file.json>]"); return 2 }
        val file = File(input)
        if (!file.isFile) { out.println("File non trovato: $input"); return 2 }
        val outDir = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")?.let(::File)
            ?: File(file.absoluteFile.parentFile, "perimetro-" + file.nameWithoutExtension)
        val runs = args.firstOrNull { it.startsWith("--permutations=") }?.removePrefix("--permutations=")?.toIntOrNull() ?: 20
        val candFile = args.firstOrNull { it.startsWith("--candidates=") }?.removePrefix("--candidates=")?.let(::File)

        val recording = try { ScanRecordingJson.decode(file.readText(Charsets.UTF_8)) } catch (e: RecordingFormatException) { out.println("Registrazione non valida: ${e.message}"); return 1 }
        val analysis = RecordingAnalyzer.analyze(recording)
        val extracted = CandidateExtractor.extract(recording, analysis)
        val experimentInput: ExperimentInput
        val source: String
        if (candFile != null) {
            if (!candFile.isFile) { out.println("File delle candidate non trovato: ${candFile.path}"); return 2 }
            val list = try { json.decodeFromString(ListSerializer(WallCandidate.serializer()), candFile.readText(Charsets.UTF_8)) } catch (e: Exception) { out.println("Candidate non valide: ${e.message}"); return 1 }
            experimentInput = extracted.copy(candidates = list)
            source = "file ${candFile.name} (${list.size} candidate); traiettoria e punti dalla registrazione"
        } else {
            experimentInput = extracted
            source = "un piano verticale di ARCore = una candidata (${extracted.candidates.size}); supporto dei punti e RMS calcolati dalla nuvola registrata"
        }

        val result = PerimeterExperiment.run(experimentInput)
        val perm = PerimeterExperiment.permutations(experimentInput, runs = runs)
        val sweep = PerimeterExperiment.sweep(experimentInput)
        outDir.mkdirs()
        val title = file.name
        File(outDir, "perimeter-report.txt").writeText(PerimeterReport.text(title, experimentInput, result, perm, sweep, source))
        File(outDir, "perimeter-with-points.svg").writeText(PerimeterSvg.render(analysis, result, title, withPoints = true))
        File(outDir, "perimeter-clean.svg").writeText(PerimeterSvg.render(analysis, result, title, withPoints = false))
        File(outDir, "perimeter-walls.csv").writeText(PerimeterReport.wallsCsv(result))
        File(outDir, "candidates.json").writeText(json.encodeToString(ListSerializer(WallCandidate.serializer()), experimentInput.candidates))
        out.println(PerimeterReport.text(title, experimentInput, result, perm, sweep, source))
        out.println()
        out.println("File scritti in: ${outDir.absolutePath}")
        return 0
    }
}
