package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.CaptureDatasetFiles
import com.sagoma.planimetria.scan.recording.RecordingFormatException
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalyzer
import java.io.File
import java.io.PrintStream
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * Room Reconstruction R1/R2 dal computer, su una registrazione M0.2 (cartella o ZIP esportato dal telefono). Il dataset non viene
 * modificato.
 *
 *   ReconstructCli <registrazione.zip | cartella> [--out=<cartella>] [--no-stability]
 *
 * Nella cartella (di default `recon-<nome>` accanto alla registrazione): `report.txt`, `surfaces.csv`, `map-topdown.svg`, `map.ply`,
 * `elevation-S<id>.svg` (superfici verticali principali), `overlay-<n>.svg` (immagini RGB con la mappa proiettata) e, per R3,
 * `walls-topdown.svg` (pareti candidate, NON una planimetria), `walls.csv` e (R3.1) `wall-ends.csv`; per R4 `perimeter-topdown.svg`, `perimeter.csv`
 * (collegamenti valutati) e `perimeter.json`; per R2.1 `surface-ambiguity.csv` (evidenza di superficie alternativa, solo diagnostica).
 */
object ReconstructCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args, PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        if (code != 0) exitProcess(code)
    }

    fun run(args: Array<String>, out: PrintStream): Int {
        val input = args.firstOrNull { !it.startsWith("--") } ?: run { out.println("Uso: ReconstructCli <registrazione.zip|cartella> [--out=<cartella>] [--no-stability]"); return 2 }
        val file = File(input)
        if (!file.exists()) { out.println("Non trovato: $input"); return 2 }
        val outDir = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")?.let(::File)
            ?: File(file.absoluteFile.parentFile, "recon-" + file.nameWithoutExtension)
        val dataset = try { CaptureDatasetFiles.open(file) } catch (e: RecordingFormatException) { out.println("Registrazione non valida: ${e.message}"); return 1 }
        val blobs = cachedBlobs(file, dataset)
        val t0 = System.nanoTime()
        val res = RoomReconstruction.run(dataset.recording, blobs, dataset.files, stabilityRuns = "--no-stability" !in args)
        val seconds = (System.nanoTime() - t0) / 1e9
        outDir.mkdirs()
        val r = dataset.recording
        val title = file.name
        val report = ReconReport.text(res, title) + "\nTempo di calcolo (con le due ricostruzioni di stabilità): ${"%.1f".format(seconds)} s\n"
        File(outDir, "report.txt").writeText(report)
        File(outDir, "surfaces.csv").writeText(ReconReport.csv(res.surfaces))
        File(outDir, "surface-ambiguity.csv").writeText(SurfaceAmbiguityReport.csv(res.surfaces))
        val tracks = RecordingAnalyzer.analyze(r).verticalPlanes.filter { it.last != null }
        val trajectory = r.poses.filterIndexed { i, _ -> i % 5 == 0 }.mapNotNull { it.camera?.let { c -> doubleArrayOf(c.x, c.y, c.z) } }
        File(outDir, "map-topdown.svg").writeText(ReconVisuals.topDown(res.map, res.surfaces, trajectory, tracks, "$title — R1/R2"))
        File(outDir, "map.ply").writeBytes(ReconVisuals.ply(res.map, res.surfaces))
        res.walls?.let { w ->
            File(outDir, "walls-topdown.svg").writeText(WallReport.topDown(res.map, res.surfaces, w, title))
            File(outDir, "walls.csv").writeText(WallReport.csv(w))
            File(outDir, "wall-ends.csv").writeText(WallReport.endsCsv(w))
            res.perimeter?.let { pr ->
                val cams = res.map.frames.map { doubleArrayOf(it.pose.x, it.pose.z) }
                File(outDir, "perimeter-topdown.svg").writeText(PerimeterReport.topDown(res.map, res.surfaces, w, pr, cams, title))
                File(outDir, "perimeter.csv").writeText(PerimeterReport.csv(pr))
                File(outDir, "perimeter.json").writeText(PerimeterReport.json(pr))
            }
        }
        val verticals = res.surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL }.sortedByDescending { it.areaM2 }.take(8)
        for (sf in verticals) File(outDir, "elevation-S${sf.id}.svg").writeText(ReconVisuals.elevation(res.map, sf, res.surfaces.floor?.y, res.surfaces.ceilingY))
        val rgb = r.rgb.sortedBy { it.timestampNs }
        val picks = if (rgb.isEmpty()) emptyList() else listOf(0.15, 0.35, 0.55, 0.75, 0.92).map { rgb[(it * (rgb.size - 1)).toInt()] }.distinct()
        for ((n, x) in picks.withIndex()) {
            val jpeg = blobs.read(x.path) ?: continue
            File(outDir, "overlay-${n + 1}.svg").writeText(ReconVisuals.overlay(res.map, res.surfaces, x, jpeg))
        }
        out.println(report)
        out.println("File scritti in: ${outDir.absolutePath}")
        return 0
    }

    /** Lettura dei file del dataset: da cartella, o dallo ZIP aperto una volta sola (non a ogni file). */
    private fun cachedBlobs(file: File, dataset: CaptureDatasetFiles): DatasetBlobs {
        if (!file.name.endsWith(".zip", ignoreCase = true)) return DatasetBlobs { dataset.read(it) }
        val zip = ZipFile(file)
        val prefix = zip.entries().asSequence().first { it.name.endsWith("recording.jsonl") }.name.removeSuffix("recording.jsonl")
        Runtime.getRuntime().addShutdownHook(Thread { zip.close() })
        return DatasetBlobs { path -> zip.getEntry(prefix + path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
    }
}
