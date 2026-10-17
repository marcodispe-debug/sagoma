package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.recording.RecordingFormatException
import com.sagoma.planimetria.scan.recording.ScanRecordingJson
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Simulazione M2.0 da riga di comando, su una registrazione (anche reale), senza telefono.
 *
 *   AssistedSimulationCli <registrazione.jsonl> [--out=<cartella>]
 *
 * Scrive `assisted-report.txt`, `assisted-timeline.csv` (una riga per frame) e `assisted-topdown.svg`. La registrazione non viene
 * modificata né copiata.
 */
object AssistedSimulationCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args, PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        if (code != 0) exitProcess(code)
    }

    fun run(args: Array<String>, out: PrintStream): Int {
        val input = args.firstOrNull { !it.startsWith("--") }
        if (input == null) { out.println("Uso: AssistedSimulationCli <registrazione.jsonl> [--out=<cartella>]"); return 2 }
        val file = File(input)
        if (!file.isFile) { out.println("File non trovato: $input"); return 2 }
        val outDir = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")?.let(::File)
            ?: File(file.absoluteFile.parentFile, "assistita-" + file.nameWithoutExtension)
        val recording = try { ScanRecordingJson.decode(file.readText(Charsets.UTF_8)) } catch (e: RecordingFormatException) { out.println("Registrazione non valida: ${e.message}"); return 1 }
        val frames = AimFrames.from(recording)
        val result = AssistedSimulation.run(frames)
        outDir.mkdirs()
        val report = AssistedSimulationReport.text(result, file.name)
        File(outDir, "assisted-report.txt").writeText(report)
        File(outDir, "assisted-timeline.csv").writeText(AssistedSimulationReport.csv(result))
        File(outDir, "assisted-topdown.svg").writeText(AssistedSimulationReport.svg(result, frames, file.name))
        out.println(report)
        out.println()
        out.println("File scritti in: ${outDir.absolutePath}")
        return 0
    }
}
