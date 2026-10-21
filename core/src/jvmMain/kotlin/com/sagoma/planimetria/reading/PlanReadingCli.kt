package com.sagoma.planimetria.reading

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.ProjectInfo
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * Dalla lettura di una piantina (JSON, [PlanReading]) e dalla sua immagine a un progetto .sagoma da importare nell'app
 * (Progetti → Importa): la piantina come sfondo tarato e le stanze lette già disegnate. Stampa il confronto con le misure scritte.
 *
 *   ./gradlew :core:readPlan -Preading=<lettura.json> -Pimage=<piantina.jpg> -Pout=<progetto.sagoma> [-Pname=<nome>]
 */
object PlanReadingCli {
    private const val IMAGE = "sfondo-piantina.jpg"

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            System.err.println("Uso: PlanReadingCli <lettura.json> <piantina.jpg> <progetto.sagoma> [nome]")
            exitProcess(2)
        }
        val reading = PlanReading.decode(File(args[0]).readText())
        val image = File(args[1])
        val out = File(args[2])
        val name = args.getOrNull(3) ?: image.nameWithoutExtension
        val result = PlanReadingImport.toPlan(reading, IMAGE)
        val info = ProjectInfo(1, name)
        ZipOutputStream(out.outputStream()).use { zip ->
            fun put(entry: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("progetto.json", Json.encodeToString(ProjectInfo.serializer(), info).toByteArray())
            put("file/planimetria.json", PlanJson.encode(Building.single(result.plan, autoLevel = true)).toByteArray())
            put("file/$IMAGE", image.readBytes())
        }
        println(report(result))
        println("Scritto ${out.absolutePath}")
    }

    fun report(result: PlanReadingImport.Result): String = buildString {
        fun f(v: Double, d: Int = 2) = String.format(Locale.ITALY, "%.${d}f", v)
        appendLine(result.cmPerPx?.let { "Scala: ${f(it, 3)} cm per pixel" } ?: "Scala: nessuna misura scritta, sfondo da tarare")
        for (c in result.checks) {
            append("- ${c.name}: ${f(c.areaDrawn)} m²")
            c.areaRead?.let { append(" (scritti ${f(it)}, scarto ${f(c.areaError!! * 100, 0)}%)") }
            if (c.sidesRead.isNotEmpty()) append("; lati scritti ${c.sidesRead.joinToString(" × ") { f(it / 100) }}")
            append("; lati disegnati ${c.sidesDrawn.joinToString(" ") { f(it / 100) }}")
            if (c.readingConsistent == false) append("  ⚠ lati × lati non tornano con i m² scritti")
            appendLine()
        }
    }
}
