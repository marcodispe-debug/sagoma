package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.ProPalette
import com.sagoma.planimetria.model.Room

/**
 * Computo metrico della casa: per ogni stanza pavimento, pareti da finire (al netto di porte e finestre),
 * battiscopa e aperture; totali per materiale; elenco di arredi e impianti. Misure in m e m².
 */
object Takeoff {
    data class RoomLine(
        val floor: String,
        val room: String,
        val floorArea: Double,
        val perimeter: Double,
        val wallArea: Double,
        val openingsArea: Double,
        val skirting: Double,
        val floorFinish: String,
        val wallFinish: String,
        val doors: Int,
        val windows: Int,
    )

    data class Total(val what: String, val quantity: Double, val unit: String)

    data class Item(val name: String, val size: String, val count: Int, val where: String)

    data class Result(
        val rooms: List<RoomLine>,
        val floorMaterials: List<Total>,
        val wallFinishes: List<Total>,
        val furniture: List<Item>,
        val fixtures: List<Item>,
    ) {
        val totalFloor: Double get() = rooms.sumOf { it.floorArea }
        val totalWalls: Double get() = rooms.sumOf { it.wallArea }
        val totalSkirting: Double get() = rooms.sumOf { it.skirting }
    }

    fun floorFinishName(r: Room): String =
        MaterialCatalog.item(r.floorMaterial)?.label ?: if (r.floorFinish == FloorFinish.RoomColor) "Da definire" else r.floorFinish.label

    /** "Pittura salvia", oppure "Pittura colore #D9C8B0" per i colori fuori tavolozza. */
    fun paintName(argb: Long): String {
        val n = ProPalette.nameOf(argb)
        return if (n.startsWith("#")) "Pittura colore $n" else "Pittura ${n.lowercase()}"
    }

    fun wallFinishName(r: Room, i: Int): String {
        val f = r.finishOf(i)
        return when (f.covering) {
            Covering.None -> paintName(f.argb)
            Covering.Material -> MaterialCatalog.item(f.material)?.label ?: "Rivestimento"
            else -> "${f.covering.label} + ${paintName(f.argb).lowercase()}"
        }
    }

    /**
     * Superfici (m²) della parete `i` al netto delle aperture, divise per finitura: il rivestimento fino
     * alla sua altezza, la pittura sopra (o su tutta la parete se non c'è rivestimento).
     */
    fun wallParts(r: Room, i: Int, length: Double): List<Pair<String, Double>> {
        val f = r.finishOf(i)
        val height = r.wallHeight(i)
        val openings = r.openings.filter { it.wallIndex == i }
        fun holesBetween(lo: Double, hi: Double) = openings.sumOf { o ->
            val bottom = if (o.kind.glazed) o.sillHeight else 0.0
            o.width * ((minOf(bottom + o.height, hi) - maxOf(bottom, lo)).coerceAtLeast(0.0))
        }
        fun area(lo: Double, hi: Double) = ((length * (hi - lo) - holesBetween(lo, hi)) / 10_000.0).coerceAtLeast(0.0)
        val paint = paintName(f.argb)
        if (f.covering == Covering.None) return listOf(paint to area(0.0, height))
        val cover = if (f.covering == Covering.Material) MaterialCatalog.item(f.material)?.label ?: "Rivestimento" else f.covering.label
        val h = f.coverHeight?.coerceAtMost(height) ?: height
        if (h >= height) return listOf(cover to area(0.0, height))
        return listOf("$cover fino a ${h.toInt()} cm" to area(0.0, h), paint to area(h, height))
    }

    /** Somma `v` al totale della voce `key`. */
    private fun MutableMap<String, Double>.add(key: String, v: Double) {
        this[key] = (this[key] ?: 0.0) + v
    }

    fun of(building: Building): Result {
        val lines = mutableListOf<RoomLine>()
        val floorTot = LinkedHashMap<String, Double>()
        val wallTot = LinkedHashMap<String, Double>()
        val furniture = LinkedHashMap<Pair<String, String>, Pair<Int, MutableSet<String>>>()
        val fixtures = LinkedHashMap<String, Pair<Int, MutableSet<String>>>()
        for (fl in building.floors) {
            val plan = fl.plan
            for (r in plan.rooms) {
                val line = roomLine(fl.name, plan, r)
                lines += line
                floorTot.add(line.floorFinish, line.floorArea)
                if (!r.outdoor) {
                    val lengths = r.interiorLengths()
                    for (i in 0 until r.wallCount) if (!r.isRemoved(i)) for ((name, area) in wallParts(r, i, lengths[i])) wallTot.add(name, area)
                }
                for (f in r.fixtures) {
                    val e = fixtures.getOrPut(f.kind.label) { 0 to mutableSetOf() }
                    fixtures[f.kind.label] = (e.first + 1) to e.second.also { it += r.name }
                }
            }
            for (f in plan.furniture) {
                val name = FurnitureCatalog.item(f.model)?.label ?: "Arredo"
                val size = "${cm(f.width)}×${cm(f.depth)}×${cm(f.height)} cm"
                val where = plan.rooms.lastOrNull { Polygon.contains(it.points, f.center) }?.name ?: fl.name
                val e = furniture.getOrPut(name to size) { 0 to mutableSetOf() }
                furniture[name to size] = (e.first + 1) to e.second.also { it += where }
            }
        }
        return Result(
            lines,
            floorTot.map { (k, v) -> Total(k, v, "m²") },
            wallTot.map { (k, v) -> Total(k, v, "m²") },
            furniture.map { (k, v) -> Item(k.first, k.second, v.first, v.second.joinToString(", ")) }.sortedBy { it.name },
            fixtures.map { (k, v) -> Item(k, "", v.first, v.second.joinToString(", ")) },
        )
    }

    private fun roomLine(floor: String, plan: FloorPlan, r: Room): RoomLine {
        val area = r.interiorArea() / 10_000.0
        val lengths = r.interiorLengths()
        val perimeter = lengths.sum() / 100.0
        var wall = 0.0
        var holes = 0.0
        // I muri eliminati (lati aperti) non si dipingono e non hanno battiscopa.
        if (!r.outdoor) for (i in 0 until r.wallCount) if (!r.isRemoved(i)) wall += lengths[i] * r.wallHeight(i) / 10_000.0
        val openSides = (0 until r.wallCount).filter { r.isRemoved(it) }.sumOf { lengths[it] } / 100.0
        for (o in r.openings) holes += o.width * o.height / 10_000.0
        // Il battiscopa si interrompe davanti a porte, porte-finestre e varchi (tutto ciò che arriva a terra).
        val atFloor = r.openings.filter { !it.kind.glazed || it.sillHeight == 0.0 }.sumOf { it.width } / 100.0
        val doors = r.openings.count { !it.kind.glazed }
        val windows = r.openings.count { it.kind.glazed }
        val wallName = if (r.outdoor) "—" else (0 until r.wallCount).map { wallFinishName(r, it) }.distinct().joinToString(" / ")
        return RoomLine(
            floor, r.name, area, perimeter,
            (wall - holes).coerceAtLeast(0.0), holes,
            if (r.outdoor) 0.0 else (perimeter - atFloor - openSides).coerceAtLeast(0.0),
            floorFinishName(r), wallName, doors, windows,
        )
    }

    private fun cm(v: Double) = if (v == kotlin.math.round(v)) v.toLong().toString() else formatDecimal(v, 1)

    /** Tabella in formato CSV (separatore ";", come la aprono Excel e LibreOffice in italiano). */
    fun csv(res: Result): String = buildString {
        fun n(v: Double) = formatDecimal(v, 2)
        appendLine("STANZE")
        appendLine("Piano;Stanza;Pavimento m²;Perimetro m;Pareti nette m²;Aperture m²;Battiscopa m;Pavimento;Pareti;Porte;Finestre")
        for (l in res.rooms) appendLine("${l.floor};${l.room};${n(l.floorArea)};${n(l.perimeter)};${n(l.wallArea)};${n(l.openingsArea)};${n(l.skirting)};${l.floorFinish};${l.wallFinish};${l.doors};${l.windows}")
        appendLine("Totale;;${n(res.totalFloor)};;${n(res.totalWalls)};;${n(res.totalSkirting)};;;;")
        appendLine()
        appendLine("PAVIMENTI;m²;m² con 10% di sfrido")
        for (t in res.floorMaterials) appendLine("${t.what};${n(t.quantity)};${n(t.quantity * 1.1)}")
        appendLine()
        appendLine("PARETI;m²")
        for (t in res.wallFinishes) appendLine("${t.what};${n(t.quantity)}")
        appendLine()
        appendLine("ARREDI;Misure;Quantità;Dove")
        for (i in res.furniture) appendLine("${i.name};${i.size};${i.count};${i.where}")
        appendLine()
        appendLine("IMPIANTI;Quantità;Dove")
        for (i in res.fixtures) appendLine("${i.name};${i.count};${i.where}")
    }
}
