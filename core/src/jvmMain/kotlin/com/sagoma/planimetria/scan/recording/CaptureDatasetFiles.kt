package com.sagoma.planimetria.scan.recording

import java.io.File
import java.util.zip.ZipFile

/**
 * Una registrazione aperta dal computer: un file JSONL (M0), una cartella M0.2 o lo ZIP esportato dal telefono. [files] sono i
 * percorsi relativi presenti (null per il JSONL da solo); [read] legge un file per percorso relativo.
 */
class CaptureDatasetFiles private constructor(
    val recording: ScanRecording,
    val files: Set<String>?,
    private val reader: (String) -> ByteArray?,
) {
    fun read(path: String): ByteArray? = reader(path)

    companion object {
        fun open(input: File): CaptureDatasetFiles = when {
            input.isDirectory -> openDirectory(input)
            input.name.endsWith(".zip", ignoreCase = true) -> openZip(input)
            else -> CaptureDatasetFiles(ScanRecordingJson.decode(input.readText(Charsets.UTF_8)), null) { null }
        }

        private fun openDirectory(dir: File): CaptureDatasetFiles {
            val main = File(dir, CaptureDataset.RECORDING)
            if (!main.isFile) throw RecordingFormatException("Nella cartella manca ${CaptureDataset.RECORDING}")
            val files = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()
            return CaptureDatasetFiles(ScanRecordingJson.decode(main.readText(Charsets.UTF_8)), files) { p ->
                File(dir, p).takeIf { it.isFile }?.readBytes()
            }
        }

        /** Lo ZIP può avere i file alla radice o dentro una cartella (`scan-…/recording.jsonl`). Si legge tutto in memoria per percorso. */
        private fun openZip(zip: File): CaptureDatasetFiles = ZipFile(zip).use { z ->
            val entries = z.entries().asSequence().filter { !it.isDirectory }.toList()
            val main = entries.firstOrNull { it.name == CaptureDataset.RECORDING || it.name.endsWith("/" + CaptureDataset.RECORDING) }
                ?: throw RecordingFormatException("Nello ZIP manca ${CaptureDataset.RECORDING}")
            val prefix = main.name.removeSuffix(CaptureDataset.RECORDING)
            val text = z.getInputStream(main).use { it.readBytes().toString(Charsets.UTF_8) }
            val files = entries.filter { it.name.startsWith(prefix) }.map { it.name.removePrefix(prefix) }.toSet()
            val zipPath = zip.absoluteFile
            CaptureDatasetFiles(ScanRecordingJson.decode(text), files) { p ->
                ZipFile(zipPath).use { zz -> zz.getEntry(prefix + p)?.let { e -> zz.getInputStream(e).use { it.readBytes() } } }
            }
        }
    }
}
