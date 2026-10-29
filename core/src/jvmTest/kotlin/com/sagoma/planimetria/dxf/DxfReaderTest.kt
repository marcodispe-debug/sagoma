package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Pianta DXF di un CAD come sfondo: unità, blocchi, archi, livelli spenti, scritte, file rovinati. */
class DxfReaderTest {
    /** Coppie codice/valore in un file DXF (righe alternate). */
    private fun p(vararg kv: Any): String = kv.toList().chunked(2).joinToString("") { (c, v) -> "$c\n$v\n" }

    private fun file(entities: String, header: String = "", tables: String = "", blocks: String = ""): ByteArray = (
        p(0, "SECTION", 2, "HEADER") + header + p(0, "ENDSEC") +
            p(0, "SECTION", 2, "TABLES") + tables + p(0, "ENDSEC") +
            p(0, "SECTION", 2, "BLOCKS") + blocks + p(0, "ENDSEC") +
            p(0, "SECTION", 2, "ENTITIES") + entities + p(0, "ENDSEC", 0, "EOF")
        ).encodeToByteArray()

    private fun units(u: Int) = p(9, "\$INSUNITS", 70, u)

    private fun line(x1: Double, y1: Double, x2: Double, y2: Double, layer: String = "0", vararg extra: Any) =
        p(0, "LINE", 8, layer, *extra, 10, x1, 20, y1, 30, 0.0, 11, x2, 21, y2, 31, 0.0)

    private fun ok(bytes: ByteArray): DxfDrawing = assertIs<DxfReader.Result.Ok>(DxfReader.read(bytes)).drawing

    private fun points(d: DxfDrawing) = d.paths.flatMap { p -> (0 until p.size / 2).map { Vec2(p[2 * it], p[2 * it + 1]) } }

    private fun near(a: Vec2, b: Vec2, tol: Double = 0.01) = a.distanceTo(b) < tol

    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 30.0)
    private val kitchen = Room(2, "Cucina più grande", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }, wallThickness = 30.0)

    @Test
    fun `la pianta esportata da sagoma torna al suo posto, in centimetri`() {
        val plan = FloorPlan(listOf(living.copy(openings = listOf(Opening.default(1, OpeningKind.Door, 1, 150.0))), kitchen))
        val d = ok(PlanDxf.bytes(plan))
        assertEquals(1.0, d.cmPerUnit)
        assertFalse(d.unitGuessed)
        // Il filo esterno di sopra: da (−15, −15) a (715, −15), con la y della pianta.
        val top = setOf(Vec2(-15.0, -15.0), Vec2(715.0, -15.0))
        assertTrue(d.paths.any { it.size == 4 && listOf(Vec2(it[0], it[1]), Vec2(it[2], it[3])).all { q -> top.any { near(it, q) } } && it[0] != it[2] })
        // Le scritte con le lettere accentate (ANSI 1252) e le misure.
        val words = d.texts.flatMap { it.lines }
        assertTrue("Cucina più grande" in words)
        assertTrue(words.any { it.endsWith(" m²") })
        assertTrue("730" in words, "totale della catena di sopra")
        // Il riquadro comprende la casa e le quote attorno.
        assertTrue(d.minX < -15 && d.maxX > 715 && d.minY < -15 && d.maxY > 315)
    }

    @Test
    fun `millimetri, blocchi girati e in scala, griglie di blocchi`() {
        val blocks = p(0, "BLOCK", 8, "0", 2, "PORTA", 70, 0, 10, 100.0, 20, 0.0, 30, 0.0, 3, "PORTA") +
            line(100.0, 0.0, 1100.0, 0.0) + p(0, "ENDBLK", 8, "0")
        val insert = p(0, "INSERT", 8, "0", 2, "PORTA", 10, 5000.0, 20, 0.0, 30, 0.0, 41, 2.0, 42, 2.0, 50, 90.0)
        val d = ok(file(insert, header = units(4), blocks = blocks))
        assertEquals(0.1, d.cmPerUnit)
        // Linea lunga 1000 mm dal punto base, doppia e girata di 90°: da (5000, 0) a (5000, 2000) mm → in pianta (500, 0)…(500, −200).
        val pts = points(d)
        assertTrue(pts.any { near(it, Vec2(500.0, 0.0)) } && pts.any { near(it, Vec2(500.0, -200.0)) }, pts.toString())
        // MINSERT: 3 colonne a 2000 mm.
        val grid = p(0, "INSERT", 8, "0", 2, "porta", 10, 0.0, 20, 0.0, 30, 0.0, 70, 3, 44, 2000.0)
        assertEquals(3, ok(file(grid, header = units(4), blocks = blocks)).paths.size)
    }

    @Test
    fun `polilinea con un tratto ad arco, cerchio, ellisse e spline`() {
        // Mezzo cerchio in senso antiorario da (0, 0) a (100, 0): passa da (50, −50), cioè (50, 50) sulla pianta.
        val lw = p(0, "LWPOLYLINE", 8, "0", 90, 2, 70, 0, 10, 0.0, 20, 0.0, 42, 1.0, 10, 100.0, 20, 0.0)
        val d = ok(file(lw, header = units(5)))
        assertTrue(points(d).any { near(it, Vec2(50.0, 50.0), 0.5) })
        assertTrue(points(d).all { abs(it.distanceTo(Vec2(50.0, 0.0)) - 50) < 1e-6 })
        // Cerchio: tutti i punti a 50 dal centro.
        val c = ok(file(p(0, "CIRCLE", 8, "0", 10, 100.0, 20, 100.0, 40, 50.0), header = units(5)))
        assertTrue(points(c).size > 36 && points(c).all { abs(it.distanceTo(Vec2(100.0, -100.0)) - 50) < 1e-6 })
        // Ellisse 200 × 100.
        val e = ok(file(p(0, "ELLIPSE", 8, "0", 10, 0.0, 20, 0.0, 11, 100.0, 21, 0.0, 40, 0.5, 41, 0.0, 42, 6.283185307179586), header = units(5)))
        assertEquals(200.0, e.width, 1e-6)
        assertEquals(100.0, e.height, 1e-6)
        // Spline di grado 3 con 4 punti (una curva di Bézier): a metà è (P0 + 3 P1 + 3 P2 + P3) / 8.
        val knots = listOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0).flatMap { listOf<Any>(40, it) }
        val ctrl = listOf(0.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0, 100.0 to 0.0).flatMap { (x, y) -> listOf<Any>(10, x, 20, y, 30, 0.0) }
        val sp = ok(file(p(0, "SPLINE", 8, "0", 70, 8, 71, 3, 72, 8, 73, 4, *knots.toTypedArray(), *ctrl.toTypedArray()), header = units(5)))
        assertTrue(points(sp).any { near(it, Vec2(50.0, -75.0), 1e-6) })
        assertTrue(near(points(sp).first(), Vec2(0.0, 0.0)) && near(points(sp).last(), Vec2(100.0, 0.0)))
        // Solo i punti di passaggio (senza nodi): una curva morbida che passa da tutti.
        val fit = listOf(0.0 to 0.0, 50.0 to 100.0, 150.0 to 50.0, 200.0 to 150.0).flatMap { (x, y) -> listOf<Any>(11, x, 21, y, 31, 0.0) }
        val fs = points(ok(file(p(0, "SPLINE", 8, "0", 70, 8, 71, 3, 74, 4, *fit.toTypedArray()), header = units(5))))
        assertTrue(fs.size > 20)
        assertTrue(listOf(Vec2(0.0, 0.0), Vec2(50.0, -100.0), Vec2(150.0, -50.0), Vec2(200.0, -150.0)).all { c -> fs.any { near(it, c, 1e-9) } })
    }

    @Test
    fun `retino pieno con il buco, a polilinea e a lati - i retini a righe no`() {
        val square = p(92, 2, 72, 0, 73, 1, 93, 4, 10, 0.0, 20, 0.0, 10, 100.0, 20, 0.0, 10, 100.0, 20, 100.0, 10, 0.0, 20, 100.0, 97, 0)
        // Buco: tre lati dritti e un mezzo cerchio in senso antiorario da (60, 60) a (40, 60) passando per (50, 70).
        val hole = p(
            92, 16, 93, 4,
            72, 1, 10, 40.0, 20, 40.0, 11, 60.0, 21, 40.0,
            72, 1, 10, 60.0, 20, 40.0, 11, 60.0, 21, 60.0,
            72, 2, 10, 50.0, 20, 60.0, 40, 10.0, 50, 0.0, 51, 180.0, 73, 1,
            72, 1, 10, 40.0, 20, 60.0, 11, 40.0, 21, 40.0, 97, 0,
        )
        fun hatch(solid: Int) = p(0, "HATCH", 8, "0", 10, 0.0, 20, 0.0, 30, 0.0, 2, if (solid == 1) "SOLID" else "ANSI31", 70, solid, 71, 0, 91, 2) +
            square + hole + p(75, 0, 76, 1, 98, 0)
        val d = ok(file(hatch(1), header = units(5)))
        val loops = d.fills.single()
        assertEquals(2, loops.size)
        val outer = loops[0].toList().chunked(2).map { Vec2(it[0], it[1]) }
        assertTrue(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, -100.0), Vec2(0.0, -100.0)).all { c -> outer.any { near(it, c) } })
        val inner = loops[1].toList().chunked(2).map { Vec2(it[0], it[1]) }
        assertTrue(inner.any { near(it, Vec2(50.0, -70.0), 1e-6) }, inner.toString())
        assertTrue(inner.none { it.y < -70.001 })
        assertIs<DxfReader.Result.Empty>(DxfReader.read(file(hatch(0), header = units(5))))
    }

    @Test
    fun `livelli spenti o congelati, entità invisibili e spazio carta si saltano`() {
        val tables = p(0, "TABLE", 2, "LAYER", 70, 2) +
            p(0, "LAYER", 2, "SPENTO", 70, 0, 62, -3, 6, "CONTINUOUS") +
            p(0, "LAYER", 2, "CONGELATO", 70, 1, 62, 5, 6, "CONTINUOUS") + p(0, "ENDTAB")
        val entities = line(0.0, 0.0, 100.0, 0.0) + line(0.0, 10.0, 100.0, 10.0, "SPENTO") + line(0.0, 20.0, 100.0, 20.0, "CONGELATO") +
            line(0.0, 30.0, 100.0, 30.0, "0", 67, 1) + line(0.0, 40.0, 100.0, 40.0, "0", 60, 1)
        assertEquals(1, ok(file(entities, header = units(5), tables = tables)).paths.size)
        // Un blocco messo su un livello spento non si vede; le sue linee sul livello 0 prendono il livello del blocco.
        val blocks = p(0, "BLOCK", 8, "0", 2, "B", 70, 0, 10, 0.0, 20, 0.0, 30, 0.0) + line(0.0, 0.0, 10.0, 0.0) + p(0, "ENDBLK", 8, "0")
        val ins = p(0, "INSERT", 8, "SPENTO", 2, "B", 10, 0.0, 20, 0.0, 30, 0.0)
        assertIs<DxfReader.Result.Empty>(DxfReader.read(file(ins, header = units(5), tables = tables, blocks = blocks)))
    }

    @Test
    fun `unità indovinata dalla grandezza del disegno`() {
        fun guess(size: Double) = ok(file(line(0.0, 0.0, size, size * 0.6))).also { assertTrue(it.unitGuessed) }.cmPerUnit
        assertEquals(100.0, guess(12.0))
        assertEquals(1.0, guess(1200.0))
        assertEquals(0.1, guess(12000.0))
        // Un pezzo sperduto lontanissimo non cambia la grandezza del disegno.
        val stray = (0 until 300).joinToString("") { line(it.toDouble(), 0.0, it + 1.0, 8.0) } + line(1e6, 1e6, 1e6 + 1, 1e6)
        val d = ok(file(stray, header = units(6)))
        assertTrue(d.width < 400 * 100.0, "${d.width}")
    }

    @Test
    fun `DWG, DXF binario, file qualunque e DXF vuoto`() {
        assertIs<DxfReader.Result.Dwg>(DxfReader.read("AC1032\u0000\u0000\u0000".encodeToByteArray()))
        assertIs<DxfReader.Result.Binary>(DxfReader.read("AutoCAD Binary DXF\r\n\u001a\u0000xx".encodeToByteArray()))
        assertIs<DxfReader.Result.NotDxf>(DxfReader.read("ciao, non sono un disegno".encodeToByteArray()))
        assertIs<DxfReader.Result.NotDxf>(DxfReader.read(ByteArray(0)))
        assertIs<DxfReader.Result.Empty>(DxfReader.read(file("")))
        // Con le righe che finiscono in \r\n (Windows) e il commento iniziale va bene lo stesso.
        val windows = ("999\r\ncommento\r\n" + String(file(line(0.0, 0.0, 1.0, 1.0), header = units(6)).map { it.toInt().toChar() }.toCharArray()).replace("\n", "\r\n"))
        assertEquals(1, ok(windows.encodeToByteArray()).paths.size)
    }

    @Test
    fun `scritte - codici speciali, allineamenti, MTEXT, UTF-8 dal 2007`() {
        val text = p(0, "TEXT", 8, "0", 10, 0.0, 20, 0.0, 30, 0.0, 40, 20.0, 1, "Pendenza 45%%d \\U+00E8", 50, 90.0, 72, 1, 11, 100.0, 21, 50.0, 31, 0.0, 73, 2)
        val mtext = p(0, "MTEXT", 8, "0", 10, 0.0, 20, 0.0, 30, 0.0, 40, 10.0, 71, 5, 3, "{\\fArial|b1;Cucina}\\P", 1, "Soggiorno \\S1^2;")
        val d = ok(file(text + mtext, header = units(5)))
        val t = d.texts.first { it.lines.first().startsWith("Pendenza") }
        assertEquals(listOf("Pendenza 45° è"), t.lines)
        // Centrata sul secondo punto, girata di 90° in senso antiorario: sulla pianta −90°.
        assertTrue(near(t.at, Vec2(100.0, -50.0)))
        assertEquals(1, t.align)
        assertEquals(2, t.valign)
        assertEquals(-90.0, t.rotation, 1e-9)
        val m = d.texts.first { it.lines.first() == "Cucina" }
        assertEquals(listOf("Cucina", "Soggiorno 1/2"), m.lines)
        assertEquals(1, m.align)
        assertEquals(2, m.valign)
        // Dal 2007 (AC1021) il file è in UTF-8.
        val utf = file(p(0, "TEXT", 8, "0", 10, 0.0, 20, 0.0, 30, 0.0, 40, 20.0, 1, "Città ŁÓDŹ"), header = p(9, "\$ACADVER", 1, "AC1032") + units(5))
        assertEquals(listOf("Città ŁÓDŹ"), ok(utf).texts.single().lines)
    }

    @Test
    fun `blocco che contiene sé stesso e quote con il loro blocco`() {
        val blocks = p(0, "BLOCK", 8, "0", 2, "A", 70, 0, 10, 0.0, 20, 0.0, 30, 0.0) + line(0.0, 0.0, 10.0, 0.0) +
            p(0, "INSERT", 8, "0", 2, "A", 10, 1.0, 20, 0.0, 30, 0.0) + p(0, "ENDBLK", 8, "0") +
            p(0, "BLOCK", 8, "0", 2, "*D1", 70, 1, 10, 0.0, 20, 0.0, 30, 0.0) + line(0.0, 50.0, 300.0, 50.0) +
            p(0, "TEXT", 8, "0", 10, 150.0, 20, 55.0, 30, 0.0, 40, 10.0, 1, "300") + p(0, "ENDBLK", 8, "0")
        val d = ok(file(p(0, "INSERT", 8, "0", 2, "A", 10, 0.0, 20, 0.0, 30, 0.0) + p(0, "DIMENSION", 8, "QUOTE", 2, "*D1", 70, 0), header = units(5), blocks = blocks))
        assertTrue(d.paths.size in 2..20)
        assertTrue(d.texts.any { it.lines == listOf("300") })
    }

    @Test
    fun `blocchi annidati con scala diversa in x e y e specchiati`() {
        // A: cerchio di raggio 10 e un quarto d'arco da 0° a 90°. B: A in (100, 0), largo il doppio e girato di 90°.
        val blocks = p(0, "BLOCK", 8, "0", 2, "A", 70, 0, 10, 0.0, 20, 0.0, 30, 0.0) + p(0, "CIRCLE", 8, "0", 10, 0.0, 20, 0.0, 40, 10.0) + p(0, "ENDBLK", 8, "0") +
            p(0, "BLOCK", 8, "0", 2, "Q", 70, 0, 10, 0.0, 20, 0.0, 30, 0.0) + p(0, "ARC", 8, "0", 10, 0.0, 20, 0.0, 40, 10.0, 50, 0.0, 51, 90.0) + p(0, "ENDBLK", 8, "0") +
            p(0, "BLOCK", 8, "0", 2, "B", 70, 0, 10, 0.0, 20, 0.0, 30, 0.0) + p(0, "INSERT", 8, "0", 2, "A", 10, 100.0, 20, 0.0, 30, 0.0, 41, 2.0, 50, 90.0) + p(0, "ENDBLK", 8, "0")
        // Il cerchio diventa un'ellisse 20 × 40 attorno a (100, 0), anche con B specchiato in y.
        val d = ok(file(p(0, "INSERT", 8, "0", 2, "B", 10, 0.0, 20, 0.0, 30, 0.0, 42, -1.0), header = units(5), blocks = blocks))
        assertEquals(90.0, d.minX, 0.1); assertEquals(110.0, d.maxX, 0.1)
        assertEquals(-20.0, d.minY, 0.1); assertEquals(20.0, d.maxY, 0.1)
        // Il quarto d'arco specchiato in y va da (10, 0) a (0, −10): sulla pianta (y in giù) tra y = 0 e 10.
        val q = ok(file(p(0, "INSERT", 8, "0", 2, "Q", 10, 0.0, 20, 0.0, 30, 0.0, 42, -1.0), header = units(5), blocks = blocks))
        assertEquals(0.0, q.minX, 1e-6); assertEquals(10.0, q.maxX, 1e-6)
        assertEquals(0.0, q.minY, 1e-6); assertEquals(10.0, q.maxY, 1e-6)
    }

    @Test
    fun `specchiato (estrusione verso il basso) - la x cambia segno`() {
        val circle = p(0, "CIRCLE", 8, "0", 10, 100.0, 20, 0.0, 30, 0.0, 40, 10.0, 210, 0.0, 220, 0.0, 230, -1.0)
        val d = ok(file(circle, header = units(5)))
        assertEquals(-100.0, (d.minX + d.maxX) / 2, 1e-6)
    }

    @Test
    fun `file rovinati - mai un errore`() {
        val plan = FloorPlan(listOf(living.copy(openings = listOf(Opening.default(1, OpeningKind.Door, 1, 150.0))), kitchen))
        val good = PlanDxf.bytes(plan)
        val rnd = Random(7)
        repeat(300) {
            val b = good.copyOf(rnd.nextInt(good.size))
            if (b.isNotEmpty()) repeat(rnd.nextInt(20)) { b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte() }
            val r = runCatching { DxfReader.read(b) }
            assertTrue(r.isSuccess, r.exceptionOrNull()?.stackTraceToString())
            (r.getOrNull() as? DxfReader.Result.Ok)?.drawing?.let { d ->
                assertTrue(d.paths.all { p -> p.all { it.isFinite() } })
                assertTrue(listOf(d.minX, d.minY, d.maxX, d.maxY).all { it.isFinite() })
            }
        }
        // Numeri strani nel file: non diventano punti infiniti.
        val weird = ok(file(line(0.0, 0.0, 10.0, 10.0) + p(0, "LINE", 8, "0", 10, "NaN", 20, "1e400", 11, "x", 21, 3.0) + p(0, "CIRCLE", 8, "0", 10, 0.0, 20, 0.0, 40, "Infinity"), header = units(5)))
        assertTrue(weird.paths.all { p -> p.all { it.isFinite() } })
        assertTrue(hypot(weird.width, weird.height) < 100)
    }
}
