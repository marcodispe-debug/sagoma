package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.Dimension
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Layer
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.TextNote
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pianta in DXF: file ben fatto, muri senza linee doppie né dentro il muro, porte e finestre, testi, y capovolta. */
class PlanDxfTest {
    /** Coppie codice/valore del file. */
    private fun pairs(dxf: String): List<Pair<Int, String>> {
        val lines = dxf.split('\n').dropLastWhile { it.isEmpty() }
        assertEquals(0, lines.size % 2, "righe dispari")
        return lines.chunked(2).map { (c, v) -> c.trim().toInt() to v }
    }

    /** Entità del file: tipo e coppie (solo nella sezione ENTITIES). */
    private fun entities(dxf: String): List<Pair<String, List<Pair<Int, String>>>> {
        val ps = pairs(dxf)
        val start = ps.indexOfFirst { it == (2 to "ENTITIES") }
        val out = mutableListOf<Pair<String, MutableList<Pair<Int, String>>>>()
        for (p in ps.drop(start + 1)) {
            if (p.first == 0) {
                if (p.second == "ENDSEC") break
                out += p.second to mutableListOf()
            } else out.last().second += p
        }
        return out
    }

    private fun layer(e: Pair<String, List<Pair<Int, String>>>) = e.second.first { it.first == 8 }.second
    private fun value(e: Pair<String, List<Pair<Int, String>>>, code: Int) = e.second.first { it.first == code }.second.toDouble()

    private val walls get() = T.pdf.livelliDxf[0]

    /** Lunghezza totale delle linee dei muri. */
    private fun wallLength(dxf: String) = entities(dxf).filter { it.first == "LINE" && layer(it) == walls }
        .sumOf { hypot(value(it, 11) - value(it, 10), value(it, 21) - value(it, 20)) }

    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 30.0)
    private val kitchen = Room(2, "Cucina più grande", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }, wallThickness = 30.0)

    @Test
    fun `file ben fatto`() {
        val stair = Stair(1, StairKind.Straight, Vec2(200.0, 150.0))
        val plan = FloorPlan(
            listOf(living.copy(openings = listOf(Opening.default(1, OpeningKind.Door, 1, 150.0), Opening.default(2, OpeningKind.Window2, 0, 200.0))), kitchen),
            freeWalls = listOf(FreeWall(1, Vec2(100.0, 15.0), Vec2(100.0, 200.0), bulge = 30.0)),
            stairs = listOf(stair),
            columns = listOf(Column(1, Vec2(600.0, 150.0))),
        )
        val dxf = PlanDxf.of(plan)
        val ps = pairs(dxf)
        assertEquals(0 to "EOF", ps.last())
        assertEquals(listOf("HEADER", "TABLES", "BLOCKS", "ENTITIES"), ps.zipWithNext().filter { it.first == (0 to "SECTION") }.map { it.second.second })
        assertTrue(ps.contains(1 to "AC1009"))
        // Codifica ANSI 1252, come dice l'intestazione: «ù» e «²» sono un byte solo.
        assertTrue(ps.zipWithNext().any { it.first == (9 to "\$DWGCODEPAGE") && it.second == (3 to "ANSI_1252") })
        val bytes = PlanDxf.bytes(plan)
        assertEquals(dxf.length, bytes.size)
        assertTrue(String(bytes, charset("windows-1252")) == dxf)
        assertTrue(dxf.contains("Cucina più grande"), "nome con l'accento")
        assertTrue(dxf.contains("m²"), "m²")
        // Ogni livello usato è nella tabella dei livelli.
        val es = entities(dxf)
        val declared = ps.zipWithNext().filter { it.first == (0 to "LAYER") }.map { it.second.second }.toSet()
        assertTrue(es.all { layer(it) in declared })
        val names = T.pdf.livelliDxf
        for (k in listOf(0, 1, 2, 3, 4, 5, 6, 7)) assertTrue(es.any { layer(it) == names[k] }, "niente sul livello ${names[k]}")
        // Porte: l'arco dell'anta; nessun arredo.
        assertTrue(es.any { it.first == "ARC" })
        // La y è capovolta: la pianta va da y = −15 a y = 315 (in giù sullo schermo), nel file da 15 a −315.
        val ys = es.filter { it.first == "LINE" && layer(it) == walls }.flatMap { listOf(value(it, 20), value(it, 21)) }
        assertEquals(15.0, ys.max(), 1e-6)
        assertEquals(-315.0, ys.min(), 1e-6)
    }

    @Test
    fun `una stanza - otto linee`() {
        // Fuori 430 × 330, dentro 370 × 270.
        val dxf = PlanDxf.of(FloorPlan(listOf(living)))
        assertEquals(8, entities(dxf).count { it.first == "LINE" && layer(it) == walls })
        assertEquals(2 * (430 + 330) + 2 * (370 + 270).toDouble(), wallLength(dxf), 1e-6)
    }

    @Test
    fun `muro in comune senza linee doppie, con il vano della porta`() {
        // Contorno esterno 730 × 330, dentro 370 × 270 e 270 × 270: nessuna linea dentro il muro in comune.
        val two = PlanDxf.of(FloorPlan(listOf(living, kitchen)))
        assertEquals(2 * (730 + 330) + 2 * (370 + 270) + 2 * (270 + 270).toDouble(), wallLength(two), 1e-6)
        // La porta da 80 nel muro in comune toglie 80 da ognuna delle due facce e aggiunge i due stipiti da 30.
        val door = Opening.default(1, OpeningKind.Door, 1, 150.0)
        val withDoor = PlanDxf.of(FloorPlan(listOf(living.copy(openings = listOf(door)), kitchen)))
        assertEquals(wallLength(two) - 160 + 60, wallLength(withDoor), 1e-6)
    }

    @Test
    fun `finestra con lo sguincio senza linee dentro il muro`() {
        // Lo sguincio da 10 allarga il vano verso la stanza: la faccia interna perde 80 + 20, gli stipiti sono mezzo dritti
        // (15) e mezzo inclinati. Prima i pezzi rimasti dopo il taglio avevano un punto doppio e restava una linea sulla mezzeria.
        val window = Opening.default(1, OpeningKind.Window1, 0, 200.0).copy(splay = 10.0)
        val dxf = PlanDxf.of(FloorPlan(listOf(living.copy(openings = listOf(window)))))
        val lines = entities(dxf).filter { it.first == "LINE" && layer(it) == walls }
        assertTrue(lines.none { kotlin.math.abs(value(it, 20)) < 0.01 && kotlin.math.abs(value(it, 21)) < 0.01 }, "linea sulla mezzeria")
        val base = 2 * (430 + 330) + 2 * (370 + 270).toDouble()
        assertEquals(base - 80 - 100 + 2 * 15 + 2 * hypot(10.0, 15.0), wallLength(dxf), 1e-6)
    }

    @Test
    fun `ambiente unico senza tacca e lato aperto dritto`() {
        // Soggiorno e cucina con il muro in comune tolto da tutte e due: fuori 730 × 330, dentro un solo vano 670 × 270.
        val open = PlanDxf.of(FloorPlan(listOf(living.copy(removedWalls = setOf(1)), kitchen.copy(removedWalls = setOf(3)))))
        assertEquals(2 * (730 + 330) + 2 * (670 + 270).toDouble(), wallLength(open), 1e-6)
        assertEquals(8, entities(open).count { it.first == "LINE" && layer(it) == walls })
        // Il soggiorno da solo, aperto a destra: una C (i muri sopra e sotto finiscono dritti a x = 415).
        val alone = PlanDxf.of(FloorPlan(listOf(living.copy(removedWalls = setOf(1)))))
        assertEquals(430 + 30 + 400 + 270 + 400 + 30 + 430 + 330.0, wallLength(alone), 1e-6)
        assertEquals(8, entities(alone).count { it.first == "LINE" && layer(it) == walls })
    }

    /** Scritte di un livello: testo e rotazione (gradi nel DXF, antiorari). */
    private fun texts(dxf: String, name: String) = entities(dxf).filter { it.first == "TEXT" && layer(it) == name }
        .map { e -> e.second.first { it.first == 1 }.second to (e.second.firstOrNull { it.first == 50 }?.second?.toDouble() ?: 0.0) }

    @Test
    fun `misure come sulla pianta - catene fuori, porte e finestre`() {
        val names = T.pdf.livelliDxf
        // Fuori 430 × 330, dentro 370 × 270: su ogni lato la catena 30 · 370 · 30 (o 30 · 270 · 30) e il totale.
        val door = Opening.default(1, OpeningKind.Door, 1, 150.0)
        val dxf = PlanDxf.of(FloorPlan(listOf(living.copy(openings = listOf(door)))))
        val t = texts(dxf, names[9])
        assertEquals(2, t.count { it.first == "430" })
        assertEquals(2, t.count { it.first == "330" })
        assertEquals(2, t.count { it.first == "370" })
        assertEquals(2, t.count { it.first == "270" })
        assertEquals(8, t.count { it.first == "30" })
        // In orizzontale dritte, in verticale si leggono dal basso.
        assertTrue(t.filter { it.first == "430" }.all { it.second == 0.0 })
        assertTrue(t.filter { it.first == "330" }.all { it.second == 90.0 })
        // Il rettangolo è già quotato fuori: dentro nessuna misura in più. La porta: larghezza sopra l'altezza.
        assertTrue(t.any { it.first == door.width.toInt().toString() })
        assertTrue(t.any { it.first == door.height.toInt().toString() })
        assertEquals(8 + 4 + 4 + 2, t.size)
    }

    @Test
    fun `misure dentro la stanza dove fuori non si leggono`() {
        val names = T.pdf.livelliDxf
        // Stanza a L: i muri dentro la rientranza non sono nelle catene, la loro misura sta dentro la stanza.
        val l = Room(1, "L", RoomType.Soggiorno, listOf(Vec2(0.0, 0.0), Vec2(600.0, 0.0), Vec2(600.0, 300.0), Vec2(300.0, 300.0), Vec2(300.0, 500.0), Vec2(0.0, 500.0)), wallThickness = 20.0)
        val t = texts(PlanDxf.of(FloorPlan(listOf(l))), names[9])
        assertTrue(t.isNotEmpty())
        val inside = entities(PlanDxf.of(FloorPlan(listOf(l)))).filter { it.first == "TEXT" && layer(it) == names[9] }
            .map { Vec2(value(it, 10), -value(it, 20)) }
            .filter { it.x > 0 && it.x < 600 && it.y > 0 && it.y < 500 }
        assertTrue(inside.isNotEmpty(), "misure dentro la stanza")
    }

    @Test
    fun `quote e testi messi a mano`() {
        val names = T.pdf.livelliDxf
        val plan = FloorPlan(
            listOf(living),
            dimensions = listOf(Dimension(1, Vec2(0.0, 0.0), Vec2(200.0, 0.0), offset = 40.0), Dimension(2, Vec2(0.0, 0.0), Vec2(0.0, 100.0), text = "Altezza libera")),
            annotations = listOf(TextNote(1, Vec2(100.0, 100.0), "Nota sul muro", size = 20.0, rotation = 30.0, arrowTo = Vec2(400.0, 100.0))),
        )
        val dxf = PlanDxf.of(plan)
        val dims = texts(dxf, names[10])
        assertEquals(setOf("200", "Altezza libera"), dims.map { it.first }.toSet())
        // La quota verticale si legge dal basso.
        assertEquals(90.0, dims.first { it.first == "Altezza libera" }.second)
        val es = entities(dxf)
        // Linea di quota, due richiami e due trattini per ogni quota.
        assertEquals(10, es.count { it.first == "LINE" && layer(it) == names[10] })
        val notes = texts(dxf, names[11])
        assertEquals(listOf("Nota sul muro" to 330.0), notes)
        // La freccia: la linea parte fuori dalla scritta e finisce nella punta piena.
        assertEquals(1, es.count { it.first == "SOLID" && layer(it) == names[11] })
        val arrow = es.single { it.first == "LINE" && layer(it) == names[11] }
        // La scritta è girata di 30°: la freccia esce dal suo lato lungo, a 26 cm dal centro.
        assertEquals(126.0, value(arrow, 10), 0.5)
        assertEquals(385.0, value(arrow, 11), 1e-6)
    }

    @Test
    fun `livelli nascosti nell'app arrivano spenti`() {
        val names = T.pdf.livelliDxf
        val dxf = PlanDxf.of(FloorPlan(listOf(living)), hidden = setOf(Layer.Dimensions, Layer.Structure))
        val table = pairs(dxf).zipWithNext().filter { it.first == (0 to "LAYER") }.map { it.second.second }
        val colors = pairs(dxf).windowed(5).filter { it[0] == (0 to "LAYER") }.associate { w -> w[1].second to w.first { it.first == 62 }.second.toInt() }
        assertTrue(table.containsAll(names))
        assertTrue(colors.getValue(names[10]) < 0, "quote spente")
        assertTrue(colors.getValue(names[6]) < 0 && colors.getValue(names[7]) < 0, "scale e pilastri spenti")
        assertTrue(colors.getValue(names[9]) > 0 && colors.getValue(names[0]) > 0 && colors.getValue(names[11]) > 0)
    }

    @Test
    fun `numeri e testi`() {
        assertEquals("12.5", DxfWriter.num(12.5))
        assertEquals("-0.0001", DxfWriter.num(-0.0001))
        assertEquals("0.0", DxfWriter.num(-0.00001))
        assertEquals("1234567.0", DxfWriter.num(1234567.0))
        assertEquals("Città a 2 m² – 5 €", DxfWriter.encode("Città a 2 m² – 5 €"))
        // Fuori dalla codifica del file (polacco, cinese): \U+.
        assertEquals("\\U+0141ód\\U+017A", DxfWriter.encode("Łódź"))
        // Emoji: un punto di domanda.
        assertEquals("Bagno ?", DxfWriter.encode("Bagno \uD83D\uDEC1"))
        assertEquals("\\U+53A8\\U+623F", DxfWriter.encode("厨房"))
        assertEquals("a b", DxfWriter.encode("a\nb"))
    }
}
