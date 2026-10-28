package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.ProPalette
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.testi.T

/**
 * Computo metrico della casa: per ogni stanza pavimento, pareti da finire (al netto di porte e finestre),
 * battiscopa e aperture; totali per materiale; elenco di arredi e impianti. Misure in m e m², nello stato di progetto (senza
 * i muri da demolire e le aperture da chiudere).
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
        /** m² dei soffitti da dipingere (in mansarda la falda vera, più grande della pianta). */
        val ceilings: Double = 0.0,
    ) {
        val totalFloor: Double get() = rooms.sumOf { it.floorArea }
        val totalWalls: Double get() = rooms.sumOf { it.wallArea }
        val totalSkirting: Double get() = rooms.sumOf { it.skirting }
    }

    fun floorFinishName(r: Room): String =
        MaterialCatalog.item(r.floorMaterial)?.nome ?: if (r.floorFinish == FloorFinish.RoomColor) T.computo.definire else r.floorFinish.label

    /** "Pittura salvia", oppure "Pittura colore #D9C8B0" per i colori fuori tavolozza. */
    fun paintName(argb: Long): String {
        val n = ProPalette.nameOf(argb)
        return if (n.startsWith("#")) T.computo.pitturaColore(n) else T.computo.pittura(n.lowercase())
    }

    fun wallFinishName(r: Room, i: Int): String {
        val f = r.finishOf(i)
        return when (f.covering) {
            Covering.None -> paintName(f.argb)
            Covering.Material -> MaterialCatalog.item(f.material)?.nome ?: T.computo.rivestimento
            else -> "${f.covering.label} + ${paintName(f.argb).lowercase()}"
        }
    }

    /**
     * Superfici (m²) della parete `i` al netto delle aperture, divise per finitura: il rivestimento fino
     * alla sua altezza, la pittura sopra (o su tutta la parete se non c'è rivestimento). `induced`: i varchi delle porte
     * della stanza accanto sul muro in comune ([Openings.sharedGaps]), che bucano anche questa parete.
     */
    fun wallParts(r: Room, i: Int, length: Double, induced: List<WallGap> = emptyList()): List<Pair<String, Double>> {
        val f = r.finishOf(i)
        val height = r.wallHeight(i)
        // La nicchia non buca la parete: si dipinge anche lei.
        val openings = r.openings.filter { it.wallIndex == i && it.kind != OpeningKind.Niche }
        val gaps = induced.filter { it.roomId == r.id && it.wallIndex == i }
        fun holesBetween(lo: Double, hi: Double) = openings.sumOf { o -> Openings.holeArea(r, o, lo, hi) } +
            gaps.sumOf { g -> g.a.distanceTo(g.b) * ((minOf(g.height, hi) - maxOf(0.0, lo)).coerceAtLeast(0.0)) }
        fun area(lo: Double, hi: Double) = ((wallBetween(r, i, length, lo, hi) - holesBetween(lo, hi)) / 10_000.0).coerceAtLeast(0.0)
        val paint = paintName(f.argb)
        if (f.covering == Covering.None) return listOf(paint to area(0.0, height))
        val cover = if (f.covering == Covering.Material) MaterialCatalog.item(f.material)?.nome ?: T.computo.rivestimento else f.covering.label
        val h = f.coverHeight?.coerceAtMost(height) ?: height
        if (h >= height) return listOf(cover to area(0.0, height))
        return listOf(T.computo.finoCm(cover, h.toInt()) to area(0.0, h), paint to area(h, height))
    }

    /**
     * Parete `i` tra le quote `lo` e `hi` (cm²), lunga `length` sul filo interno: alta come il muro o, in mansarda, tagliata in
     * diagonale come nel 3D ([Ceilings.wallTopAt]).
     */
    private fun wallBetween(r: Room, i: Int, length: Double, lo: Double, hi: Double): Double {
        val len = r.wallLength(i)
        if (Ceilings.effectiveCut(r, i) == null || len < 1e-6) return length * (minOf(hi, r.wallHeight(i)) - lo).coerceAtLeast(0.0)
        // La cima è dritta e poi scende in linea retta: a strisce sottili lo scarto è di frazioni di cm².
        val n = 256
        var sum = 0.0
        for (k in 0 until n) sum += (minOf(hi, Ceilings.wallTopAt(r, i, len * (k + 0.5) / n)) - lo).coerceAtLeast(0.0)
        return length * sum / n
    }

    /** Somma `v` al totale della voce `key`. */
    private fun MutableMap<String, Double>.add(key: String, v: Double) {
        this[key] = (this[key] ?: 0.0) + v
    }

    /** True se in casa c'è almeno una zona non calpestabile: solo allora ha senso chiedere se toglierla dal computo. */
    fun hasExcludedZones(building: Building) = building.floors.any { f -> f.plan.rooms.any { it.excludedZones.isNotEmpty() } }

    /** @param withoutZones true = i m² del pavimento senza le zone non calpestabili (come sulla pianta); lo sceglie l'utente. */
    fun of(building: Building, withoutZones: Boolean = true): Result {
        val lines = mutableListOf<RoomLine>()
        val floorTot = LinkedHashMap<String, Double>()
        val wallTot = LinkedHashMap<String, Double>()
        val furniture = LinkedHashMap<Pair<String, String>, Pair<Int, MutableSet<String>>>()
        val fixtures = LinkedHashMap<String, Pair<Int, MutableSet<String>>>()
        var ceilings = 0.0
        for (fl in building.floors) {
            // Lo stato di progetto: i muri da demolire sono lati aperti, le aperture da chiudere sono muro pieno.
            val plan = Phases.view(fl.plan, PhaseView.Project)
            // Porte sul muro in comune: il vano buca anche la parete della stanza accanto (che non ha la porta tra le sue).
            val induced = Openings.sharedGaps(plan)
            for (r in plan.rooms) {
                val line = roomLine(fl.name, plan, r, induced, withoutZones)
                lines += line
                floorTot.add(line.floorFinish, line.floorArea)
                if (!r.outdoor) {
                    ceilings += Ceilings.areaM2(r)
                    val lengths = r.interiorLengths()
                    for (i in 0 until r.wallCount) if (!r.isRemoved(i)) for ((name, area) in wallParts(r, i, lengths[i], induced)) wallTot.add(name, area)
                }
                for (f in r.fixtures) {
                    val e = fixtures.getOrPut(f.kind.label) { 0 to mutableSetOf() }
                    fixtures[f.kind.label] = (e.first + 1) to e.second.also { it += r.name }
                }
            }
            // Muri singoli (tramezzi, muretti): una riga per piano, con le pareti dei due lati.
            freeWallsLine(fl, plan, wallTot)?.let { lines += it }
            for (f in plan.furniture) {
                val name = FurnitureCatalog.item(f.model)?.nome ?: T.computo.arredo
                val size = T.computo.cm(cm(f.width), cm(f.depth), cm(f.height))
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
            ceilings,
        )
    }

    private fun roomLine(floor: String, plan: FloorPlan, r: Room, induced: List<WallGap>, withoutZones: Boolean): RoomLine {
        // Con withoutZones gli stessi m² scritti sulla pianta: senza le zone non calpestabili, dove il pavimento non si posa.
        val area = (if (withoutZones) r.usableArea() else r.interiorArea()) / 10_000.0
        val lengths = r.interiorLengths()
        val perimeter = lengths.sum() / 100.0
        var wall = 0.0
        var holes = 0.0
        // I muri eliminati (lati aperti) non si dipingono e non hanno battiscopa.
        if (!r.outdoor) for (i in 0 until r.wallCount) if (!r.isRemoved(i)) wall += wallBetween(r, i, lengths[i], 0.0, Double.MAX_VALUE) / 10_000.0
        val openSides = (0 until r.wallCount).filter { r.isRemoved(it) }.sumOf { lengths[it] } / 100.0
        // La nicchia non buca la parete e non è né una porta né una finestra.
        val openings = r.openings.filter { it.kind != OpeningKind.Niche }
        // Il vano, con la sua forma (ad arco, tonda), non è mai più largo del muro né più alto della parete (una misura scritta
        // per sbaglio, es. 2100 invece di 210; in mansarda la parete scende).
        for (o in openings) if (o.wallIndex < r.wallCount && !r.isRemoved(o.wallIndex)) holes += Openings.holeArea(r, o) / 10_000.0
        // Varchi delle porte della stanza accanto sul muro in comune (sui muri eliminati non c'è parete da togliere).
        val gaps = induced.filter { it.roomId == r.id && it.wallIndex < r.wallCount && !r.isRemoved(it.wallIndex) }
        if (!r.outdoor) for (g in gaps) holes += g.a.distanceTo(g.b) * minOf(g.height, r.wallHeight(g.wallIndex)) / 10_000.0
        // Il battiscopa si interrompe davanti a porte, porte-finestre e varchi (tutto ciò che arriva a terra), anche quelli
        // delle porte della stanza accanto.
        // Una porta rimasta su un muro eliminato non conta: quel lato è già tolto tutto (altrimenti si toglieva due volte).
        val atFloor = (openings.filter { (!it.kind.glazed || it.sillHeight == 0.0) && it.wallIndex < r.wallCount && !r.isRemoved(it.wallIndex) }.sumOf { minOf(it.width, r.wallLength(it.wallIndex)) } + gaps.sumOf { it.a.distanceTo(it.b) }) / 100.0
        val doors = openings.count { !it.kind.glazed }
        val windows = openings.count { it.kind.glazed }
        val wallName = if (r.outdoor) "—" else (0 until r.wallCount).map { wallFinishName(r, it) }.distinct().joinToString(" / ")
        return RoomLine(
            floor, r.name, area, perimeter,
            (wall - holes).coerceAtLeast(0.0), holes,
            if (r.outdoor) 0.0 else (perimeter - atFloor - openSides).coerceAtLeast(0.0),
            floorFinishName(r), wallName, doors, windows,
        )
    }

    /**
     * Muri singoli di `plan` (il piano `fl` nello stato di progetto, senza quelli da demolire): si finiscono sui due lati, alti fino al soffitto o quanto scritto,
     * lunghi come la loro mezzeria (sull'arco, se curvi: la media dei due lati), al netto dei vani; il battiscopa corre sui due
     * lati, tranne davanti a porte e varchi. Le finiture vanno nei totali `wallTot`.
     */
    private fun freeWallsLine(fl: Floor, plan: FloorPlan, wallTot: MutableMap<String, Double>): RoomLine? {
        val walls = plan.freeWalls.filter { it.length >= 1.0 }
        if (walls.isEmpty()) return null
        var wall = 0.0
        var holes = 0.0
        var skirting = 0.0
        val names = LinkedHashSet<String>()
        var doors = 0
        var windows = 0
        for (w in walls) {
            val h = Structure.freeWallHeight(plan, w, fl.levelHeight)
            val host = w.host.copy(ceilingHeight = h, wallFinish = w.finishOrPaint)
            val length = host.arcOf(0)?.arcLength() ?: w.length
            for ((name, area) in wallParts(host, 0, length)) {
                wallTot.add(name, 2 * area)
                wall += 2 * area
            }
            val openings = host.openings.filter { it.kind != OpeningKind.Niche && it.wallIndex == 0 }
            holes += 2 * openings.sumOf { Openings.holeArea(host, it) } / 10_000.0
            val atFloor = openings.filter { !it.kind.glazed || it.sillHeight == 0.0 }.sumOf { minOf(it.width, length) }
            skirting += 2 * (length - atFloor).coerceAtLeast(0.0) / 100.0
            names += wallFinishName(host, 0)
            doors += openings.count { !it.kind.glazed }
            windows += openings.count { it.kind.glazed }
        }
        return RoomLine(fl.name, T.computo.muriSingoli, 0.0, 0.0, wall, holes, skirting, "—", names.joinToString(" / "), doors, windows)
    }

    private fun cm(v: Double) = if (v == kotlin.math.round(v)) v.toLong().toString() else formatDecimal(v, 1)

    /**
     * Tabella in formato CSV (separatore ";", come la aprono Excel e LibreOffice in italiano; con `sep` = tabulazione è il
     * testo da incollare in un foglio di calcolo). I nomi scritti dall'utente (stanze, piani) possono contenere il separatore,
     * le virgolette o un a capo: vanno tra virgolette, altrimenti spostano le colonne.
     */
    fun csv(res: Result, sep: Char = ';'): String = buildString {
        fun n(v: Double) = formatDecimal(v, 2)
        fun t(s: String) = if (s.any { it == sep || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        fun row(vararg cells: String) = appendLine(cells.joinToString(sep.toString()))
        fun head(s: String) = appendLine(s.replace(';', sep))
        head(T.computo.stanze2)
        head(T.computo.pianoStanzaPavimentoM2)
        for (l in res.rooms) row(t(l.floor), t(l.room), n(l.floorArea), n(l.perimeter), n(l.wallArea), n(l.openingsArea), n(l.skirting), t(l.floorFinish), t(l.wallFinish), "${l.doors}", "${l.windows}")
        head(T.computo.totale2(n(res.totalFloor), n(res.totalWalls), n(res.totalSkirting)))
        appendLine()
        head(T.computo.pavimentiM2M210)
        for (x in res.floorMaterials) row(t(x.what), n(x.quantity), n(x.quantity * 1.1))
        appendLine()
        head(T.computo.paretiM22)
        for (x in res.wallFinishes) row(t(x.what), n(x.quantity))
        if (res.ceilings > 0) row(t(T.computo.soffitti), n(res.ceilings))
        appendLine()
        head(T.computo.arrediMisureQuantitaDove)
        for (i in res.furniture) row(t(i.name), t(i.size), "${i.count}", t(i.where))
        appendLine()
        head(T.computo.impiantiQuantitaDove)
        for (i in res.fixtures) row(t(i.name), "${i.count}", t(i.where))
    }
}
