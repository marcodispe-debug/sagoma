package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.persistence.PlanJson
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

/**
 * Prove dello scambio IFC senza app: da un progetto .sagoma al file .ifc, e ritorno.
 *
 *   ./gradlew :core:ifc -Pin=<progetto.sagoma> -Pout=<casa.ifc>                   esporta la planimetria nuda
 *   ./gradlew :core:ifc -Pin=<casa.ifc> [-Porig=<progetto.sagoma>] [-Pout=<nuovo.sagoma.json>]
 *                                                                                 rilegge (e rimette gli arredi del progetto)
 */
object IfcCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val input = args.getOrNull(0)?.let(::File) ?: run { System.err.println("Uso: IfcCli <in> [out] [orig]"); exitProcess(2) }
        val out = args.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(::File)
        if (input.extension.equals("ifc", ignoreCase = true)) {
            val r = IfcImport.read(input.readText())
            val rep = r.report
            println("Piani ${rep.floors}, stanze ${rep.rooms}, muri ${rep.walls} (singoli ${rep.freeWalls}), porte ${rep.doors}, finestre ${rep.windows}, passaggi ${rep.passages}, colonne ${rep.columns}")
            println(if (rep.fromSagoma) "Il file è uscito da sagoma." else "File di un altro programma.")
            if (rep.skipped.isNotEmpty()) println("Non letti: " + rep.skipped.entries.joinToString { "${it.key} ${it.value}" })
            var result = r.building
            args.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { orig ->
                val m = IfcRoundTrip.merge(readSagoma(File(orig)), r, key = File(orig).nameWithoutExtension)
                val x = m.report
                println("Ritorno: stanze uguali ${x.unchanged}, spostate ${x.moved}, cambiate ${x.reshaped}, nuove ${x.added}, tolte ${x.removed}; arredi tenuti ${x.furnitureKept}, spostati ${x.furnitureMoved}, da controllare ${x.toCheck.size}")
                result = m.building
            }
            out?.let {
                if (it.extension == "svg") it.writeText(axonometric(input.readText())) else it.writeText(PlanJson.encode(result))
                println("Scritto ${it.absolutePath}")
            }
        } else {
            val building = readSagoma(input)
            val name = input.nameWithoutExtension
            val text = IfcExport.export(building, name, key = name, timestamp = java.time.LocalDateTime.now().withNano(0).toString())
            val target = out ?: File(input.parentFile, "$name.ifc")
            target.writeText(text)
            println("Scritto ${target.absolutePath} (${text.length / 1024} KB)")
        }
    }

    /** Vista assonometrica dei solidi del file (muri grigi, porte marroni, finestre azzurre, stanze tenui), per controllarlo a occhio. */
    fun axonometric(text: String): String {
        val solids = IfcImport.solids(text)
        val cos30 = kotlin.math.cos(Math.PI / 6)
        fun proj(p: DoubleArray) = doubleArrayOf((p[0] - p[1]) * cos30, (p[0] + p[1]) * 0.5 - p[2])
        val all = solids.flatMap { (_, rings) -> rings.flatten().map(::proj) }
        if (all.isEmpty()) return "<svg xmlns='http://www.w3.org/2000/svg'/>"
        val minX = all.minOf { it[0] } - 20; val minY = all.minOf { it[1] } - 20
        val w = all.maxOf { it[0] } - minX + 20; val h = all.maxOf { it[1] } - minY + 20
        val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='$minX $minY $w $h' width='${(w * 1.2).toInt()}' height='${(h * 1.2).toInt()}'>")
        sb.append("<rect x='$minX' y='$minY' width='$w' height='$h' fill='white'/>")
        // Dal fondo in avanti: prima le stanze, poi gli oggetti in ordine di profondità.
        val order = solids.sortedWith(compareBy({ it.first != "IFCSPACE" }, { s -> s.second[0].maxOf { it[0] + it[1] } }))
        for ((type, rings) in order) {
            val (fill, stroke, op) = when (type) {
                "IFCSPACE" -> Triple("#9ec5fe", "#6ea8fe", 0.15)
                "IFCDOOR" -> Triple("#a0522d", "#5c2e0e", 0.9)
                "IFCWINDOW" -> Triple("#4dabf7", "#1971c2", 0.8)
                "IFCOPENINGELEMENT" -> Triple("none", "#e03131", 0.0)
                "IFCSTAIR" -> Triple("#f59f00", "#a86400", 0.9)
                else -> Triple("#ced4da", "#495057", 0.95)
            }
            val (bottom, top) = rings
            fun poly(r: List<DoubleArray>) = r.joinToString(" ") { proj(it).let { q -> "${q[0]},${q[1]}" } }
            val n = bottom.size
            for (k in 0 until n) {
                val quad = listOf(bottom[k], bottom[(k + 1) % n], top[(k + 1) % n], top[k])
                sb.append("<polygon points='${poly(quad)}' fill='$fill' fill-opacity='$op' stroke='$stroke' stroke-width='0.6'/>")
            }
            sb.append("<polygon points='${poly(top)}' fill='$fill' fill-opacity='$op' stroke='$stroke' stroke-width='0.6'/>")
        }
        return sb.append("</svg>").toString()
    }

    /** Casa da un progetto .sagoma (ZIP con file/planimetria.json) o da un JSON della planimetria. */
    fun readSagoma(f: File): Building {
        if (f.extension == "json") return PlanJson.decodeBuilding(f.readText())
        ZipInputStream(f.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "file/planimetria.json") return PlanJson.decodeBuilding(zip.readBytes().decodeToString())
            }
        }
        error("Nel progetto non c'è file/planimetria.json")
    }
}
