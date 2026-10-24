package com.sagoma.planimetria.reading

import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanReadingImportTest {
    private fun room(name: String, vararg corners: Pair<Int, Int>, sides: List<Double> = emptyList(), area: Double? = null) =
        PlanReading.ReadRoom(name, corners.map { listOf(it.first.toDouble(), it.second.toDouble()) }, sides, area)

    /** Due stanze affiancate, 2 cm per pixel: 4,00 × 3,00 e 3,00 × 3,00, separate da un tramezzo di 6 px (12 cm). */
    private val twoRooms = PlanReading(
        image = PlanReading.ImageSize(1000, 800),
        rooms = listOf(
            room("CUCINA", 100 to 100, 300 to 100, 300 to 250, 100 to 250, sides = listOf(4.0, 3.0), area = 12.0),
            room("Letto", 306 to 100, 456 to 100, 456 to 250, 306 to 250, sides = listOf(3.0, 3.0)),
        ),
        exteriorWallPx = 15.0,
    )

    @Test
    fun scalaDalleMisureScritte() {
        val r = PlanReadingImport.toPlan(twoRooms, "sfondo.jpg")
        assertEquals(2.0, r.cmPerPx!!, 1e-9)
        val u = assertNotNull(r.plan.underlay)
        assertEquals("sfondo.jpg", u.file)
        assertEquals(2.0, u.cmPerPx, 1e-9)
        assertEquals(Vec2.Zero, u.origin)
    }

    @Test
    fun murInComuneEMuriEsterni() {
        val (a, b) = PlanReadingImport.toPlan(twoRooms, "sfondo.jpg").plan.rooms
        // Il tramezzo è spesso quanto lo spazio tra le stanze e le due stanze hanno la stessa mezzeria.
        assertEquals(12.0, a.thicknessOf(1))
        assertEquals(12.0, b.thicknessOf(3))
        assertEquals(a.points[1].x, b.points[0].x, 1e-9)
        assertEquals(606.0, a.points[1].x, 1e-9)
        // Muri esterni dalla lettura: 15 px = 30 cm, mezzeria a mezzo muro dal filo interno.
        assertEquals(30.0, a.wallThickness)
        assertEquals(mapOf(1 to 12.0), a.wallThicknesses)
        assertEquals(185.0, a.points[0].x, 1e-9)
        assertEquals(185.0, a.points[0].y, 1e-9)
    }

    @Test
    fun controlliSulleMisure() {
        val (a, b) = PlanReadingImport.toPlan(twoRooms, "sfondo.jpg").checks
        assertEquals("Cucina", a.name)
        assertEquals(12.0, a.areaDrawn, 1e-6)
        assertEquals(0.0, a.areaError!!, 1e-6)
        assertEquals(listOf(400.0, 300.0, 400.0, 300.0), a.sidesDrawn.map { Math.round(it * 1000) / 1000.0 })
        assertEquals(true, a.readingConsistent)
        assertNull(b.areaError)
        assertNull(b.readingConsistent)
    }

    @Test
    fun misureScritteCheNonTornano() {
        // Come sulla catastale p01: 3,70 × 2,84 = 10,51 m², ma è scritto 12,13.
        val reading = PlanReading(
            image = PlanReading.ImageSize(500, 500),
            rooms = listOf(room("Letto", 100 to 100, 192 to 100, 192 to 171, 100 to 171, sides = listOf(3.70, 2.84), area = 12.13)),
        )
        assertEquals(false, PlanReadingImport.toPlan(reading, "x.jpg").checks.single().readingConsistent)
    }

    @Test
    fun rettangoloAdattatoAlleMisureScritte() {
        // Letto un po' più stretto (148 px = 2,96 m): prende i 3,00 m scritti, attorno al suo centro.
        val reading = twoRooms.copy(rooms = listOf(twoRooms.rooms[0], room("Letto", 308 to 100, 456 to 100, 456 to 250, 308 to 250, sides = listOf(3.0, 3.0))))
        val check = PlanReadingImport.toPlan(reading, "x.jpg").checks[1]
        assertEquals(300.0, check.sidesDrawn[0], 0.5)
        assertEquals(300.0, check.sidesDrawn[1], 0.5)
    }

    @Test
    fun senzaMisureLoSfondoVaTarato() {
        val reading = PlanReading(
            image = PlanReading.ImageSize(600, 400),
            rooms = listOf(room("Soggiorno", 100 to 100, 300 to 100, 300 to 250, 100 to 250)),
        )
        val r = PlanReadingImport.toPlan(reading, "x.jpg")
        assertNull(r.cmPerPx)
        // Come uno sfondo appena caricato: largo 12 m.
        assertEquals(2.0, r.plan.underlay!!.cmPerPx, 1e-9)
        assertEquals(1, r.plan.rooms.size)
    }

    @Test
    fun angoliQuasiRettiRaddrizzati() {
        val pts = PlanReadingImport.squared(listOf(Vec2(100.0, 102.0), Vec2(300.0, 98.0), Vec2(303.0, 250.0), Vec2(99.0, 252.0)))
        assertEquals(listOf(Vec2(99.5, 100.0), Vec2(301.5, 100.0), Vec2(301.5, 251.0), Vec2(99.5, 251.0)), pts)
    }

    @Test
    fun stanzeLetteInSensoAntiorario() {
        val reading = twoRooms.copy(rooms = listOf(room("Letto", 100 to 100, 100 to 250, 300 to 250, 300 to 100, sides = listOf(4.0, 3.0))))
        val room = PlanReadingImport.toPlan(reading, "x.jpg").plan.rooms.single()
        assertTrue(com.sagoma.planimetria.geometry.Polygon.signedArea(room.points) > 0)
        assertEquals(12.0, PlanReadingImport.toPlan(reading, "x.jpg").checks.single().areaDrawn, 1e-6)
    }

    @Test
    fun stanzeConMenoDiTreAngoliIgnorate() {
        val reading = twoRooms.copy(rooms = twoRooms.rooms + room("Niente", 1 to 1, 5 to 5))
        assertEquals(2, PlanReadingImport.toPlan(reading, "x.jpg").plan.rooms.size)
    }

    @Test
    fun tipoDiStanzaDalNome() {
        val expected = mapOf(
            "CUCINA" to RoomType.Cucina, "K" to RoomType.Cucina, "Angolo cottura" to RoomType.Cucina,
            "w.c." to RoomType.Bagno, "WC/Lav" to RoomType.Bagno, "BAGNO 1" to RoomType.Bagno,
            "Letto" to RoomType.Camera, "CAMERA 2" to RoomType.Camera, "Cameretta" to RoomType.Cameretta,
            "salotto" to RoomType.Soggiorno, "Living" to RoomType.Soggiorno, "Soggiorno-pranzo" to RoomType.Soggiorno,
            "Studio" to RoomType.Studio, "Disimp." to RoomType.Corridoio, "DIs" to RoomType.Corridoio, "INGRESSO" to RoomType.Corridoio,
            "Balcone" to RoomType.Balcone, "POGGIOLO" to RoomType.Balcone, "Terrazzo" to RoomType.Terrazza, "Portico" to RoomType.Terrazza,
            "Rip." to RoomType.Altro, "CANTINA" to RoomType.Altro, "VANO TECNICO" to RoomType.Altro,
        )
        for ((name, type) in expected) assertEquals(type, PlanReadingImport.typeOf(name), name)
    }

    @Test
    fun nomiDaMostrare() {
        assertEquals("Cucina", PlanReadingImport.displayName("CUCINA"))
        assertEquals("Salotto", PlanReadingImport.displayName("salotto"))
        assertEquals("WC", PlanReadingImport.displayName("WC"))
        assertEquals("DIs", PlanReadingImport.displayName("DIs"))
        assertEquals("Camera 2", PlanReadingImport.displayName("CAMERA 2"))
    }

    @Test
    fun letturaInJson() {
        val text = """
            {"version": 1, "image": {"width": 526, "height": 1168},
             "rooms": [{"name": "Cucina", "corners": [[96,293],[259,293],[259,455],[96,455]], "sides_m": [3.5, 3.5], "area_m2": 12.25}],
             "exterior_wall_px": 12, "notes": "prova", "altro": true}
        """.trimIndent()
        val r = PlanReading.decode(text)
        assertEquals(526, r.image.width)
        assertEquals(listOf(3.5, 3.5), r.rooms.single().sidesM)
        assertEquals(12.25, r.rooms.single().areaM2)
        assertEquals(12.0, r.exteriorWallPx)
        assertEquals(r, PlanReading.decode(PlanReading.encode(r)))
    }

    @Test
    fun laPiantaSiSalvaESiRilegge() {
        val plan = PlanReadingImport.toPlan(twoRooms, "sfondo.jpg").plan
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
        assertFalse(plan.rooms.any { it.points.size != 4 })
    }

    @Test
    fun porteEFinestreSulMuroGiusto() {
        val reading = twoRooms.copy(
            rooms = twoRooms.rooms + room("Balcone", 100 to 265, 300 to 265, 300 to 320, 100 to 320),
            openings = listOf(
                // Porta nel tramezzo tra cucina e letto, larga quanto scritto.
                PlanReading.ReadOpening("door", listOf(listOf(303.0, 150.0), listOf(303.0, 190.0)), widthCm = 80.0, heightCm = 210.0),
                // Porta-finestra tra cucina e balcone: va alla cucina, non al balcone.
                PlanReading.ReadOpening("balcony", listOf(listOf(150.0, 257.0), listOf(200.0, 257.0))),
                // Finestra sul muro esterno a destra del letto (x = 456 + metà muro).
                PlanReading.ReadOpening("window", listOf(listOf(463.0, 150.0), listOf(463.0, 200.0)), widthCm = 100.0, heightCm = 140.0),
                PlanReading.ReadOpening("boh", listOf(listOf(0.0, 0.0), listOf(1.0, 1.0))),
            ),
        )
        val (cucina, letto, balcone) = PlanReadingImport.toPlan(reading, "sfondo.jpg").plan.rooms
        assertTrue(balcone.openings.isEmpty())
        val door = cucina.openings.single { it.kind == com.sagoma.planimetria.model.OpeningKind.Door }
        assertEquals(80.0, door.width, 1e-9)
        assertEquals(210.0, door.height, 1e-9)
        val (a, b) = com.sagoma.planimetria.geometry.Openings.span(cucina, door)
        assertEquals(340.0, (a.y + b.y) / 2, 1.0)
        val porta = cucina.openings.single { it.kind == com.sagoma.planimetria.model.OpeningKind.Balcony1 }
        assertEquals(100.0, porta.width, 1e-6) // 50 px × 2 cm
        assertEquals(0.0, porta.sillHeight, 1e-9)
        val window = letto.openings.single()
        assertEquals(com.sagoma.planimetria.model.OpeningKind.Window1, window.kind)
        assertEquals(140.0, window.height, 1e-9)
        assertEquals(90.0, window.sillHeight, 1e-9)
    }

    @Test
    fun zoneNonCalpestabiliTolteDaiMetriQuadri() {
        // 4,00 × 3,00 con una fascia non accessibile di 1,00 × 3,00: utili 9 m², come scritto.
        val reading = PlanReading(
            image = PlanReading.ImageSize(1000, 800),
            rooms = listOf(
                PlanReading.ReadRoom(
                    "Mansarda", listOf(listOf(100.0, 100.0), listOf(300.0, 100.0), listOf(300.0, 250.0), listOf(100.0, 250.0)),
                    areaM2 = 9.0, excluded = listOf(listOf(listOf(100.0, 100.0), listOf(150.0, 100.0), listOf(150.0, 250.0), listOf(100.0, 250.0))),
                ),
            ),
        )
        val r = PlanReadingImport.toPlan(reading, "sfondo.jpg")
        assertEquals(2.0, r.cmPerPx!!, 1e-9)
        assertEquals(1, r.plan.rooms.single().excludedZones.size)
        assertEquals(9.0, r.checks.single().areaDrawn, 0.01)
        assertEquals(0.0, r.checks.single().areaError!!, 0.01)
    }

    @Test
    fun scalaDrittaDallaLineaDiSalita() {
        // Sale da sinistra a destra: 150 px = 300 cm con 10 pedate da 30 cm, larga 50 px = 100 cm.
        val reading = twoRooms.copy(stairs = listOf(PlanReading.ReadStair(listOf(100.0, 400.0), listOf(250.0, 400.0), widthPx = 50.0, treads = 10)))
        val s = PlanReadingImport.toPlan(reading, "sfondo.jpg").plan.stairs.single()
        assertEquals(com.sagoma.planimetria.model.StairKind.Straight, s.kind)
        assertEquals(Vec2(350.0, 800.0), s.center)
        assertEquals(90.0, s.rotation, 1e-9)
        assertEquals(100.0, s.width, 1e-9)
        assertEquals(30.0, s.tread, 1e-9)
        assertEquals(11, s.risers)
        // Il disegno della scala va proprio da un capo all'altro della linea letta.
        val path = com.sagoma.planimetria.geometry.Stairs.layout(s, 300.0).path
        assertEquals(200.0, path.minOf { it.x }, 16.0)
        assertEquals(500.0, path.maxOf { it.x }, 1.0)
    }

    @Test
    fun muriEsterniDalContornoEsterno() {
        // Contorno esterno a 15 px sopra e a sinistra, a 25 px sotto e a destra: muri esterni da 30 e da 50 cm.
        val reading = twoRooms.copy(
            exteriorOutline = listOf(listOf(85.0, 85.0), listOf(481.0, 85.0), listOf(481.0, 275.0), listOf(85.0, 275.0)),
        )
        val (a, b) = PlanReadingImport.toPlan(reading, "sfondo.jpg").plan.rooms
        assertEquals(listOf(30.0, 50.0), listOf(a, b).flatMap { r -> r.thicknesses }.distinct().filter { it > 12 }.sorted())
        assertEquals(12.0, a.thicknesses.min(), 1e-9)
        // Lati esterni: sopra 30, sotto 50 in entrambe le stanze.
        for (r in listOf(a, b)) {
            val top = (0 until r.wallCount).minBy { (r.wallStart(it).y + r.wallEnd(it).y) }
            val bottom = (0 until r.wallCount).maxBy { (r.wallStart(it).y + r.wallEnd(it).y) }
            assertEquals(30.0, r.thicknessOf(top), 1e-9)
            assertEquals(50.0, r.thicknessOf(bottom), 1e-9)
        }
    }
    @Test
    fun versoDelleAnteComeSulDisegno() {
        // Porta tra cucina e letto (tramezzo a x = 303), cardini in basso e aperta verso il letto; poi la stessa con i cardini
        // in alto e aperta verso la cucina; una porta-finestra scorrevole sul muro sotto la cucina.
        fun door(hinge: Pair<Double, Double>, toward: Pair<Double, Double>) = PlanReading.ReadOpening(
            "door", listOf(listOf(303.0, 150.0), listOf(303.0, 190.0)),
            hinge = listOf(hinge.first, hinge.second), opensToward = listOf(toward.first, toward.second),
        )
        val sliding = PlanReading.ReadOpening("double_balcony", listOf(listOf(150.0, 257.0), listOf(250.0, 257.0)), sliding = true)
        fun read(vararg o: PlanReading.ReadOpening) = PlanReadingImport.toPlan(twoRooms.copy(openings = o.toList()), "x.jpg").plan.rooms
        fun drawn(rooms: List<com.sagoma.planimetria.model.Room>): Pair<com.sagoma.planimetria.model.Room, com.sagoma.planimetria.model.Opening> =
            rooms.first { it.openings.isNotEmpty() }.let { it to it.openings.single() }
        // Cardine e verso nel piano: l'estremo con i cardini e la parte dove sta l'anta aperta.
        fun hingeAndSide(rooms: List<com.sagoma.planimetria.model.Room>): Pair<Double, Double> {
            val (room, o) = drawn(rooms)
            val (a, b) = com.sagoma.planimetria.geometry.Openings.span(room, o)
            val hinge = if (o.hingeLeft) a else b
            val n = com.sagoma.planimetria.geometry.Openings.inwardNormal(room, o.wallIndex) * (if (o.opensInward) 1.0 else -1.0)
            return hinge.y to n.x
        }
        val (h1, side1) = hingeAndSide(read(door(303.0 to 190.0, 330.0 to 175.0)))
        assertEquals(380.0, h1, 1e-6)
        assertTrue(side1 > 0) // verso il letto, a destra
        val (h2, side2) = hingeAndSide(read(door(303.0 to 150.0, 280.0 to 160.0)))
        assertEquals(300.0, h2, 1e-6)
        assertTrue(side2 < 0) // verso la cucina, a sinistra
        val (_, s) = drawn(read(sliding))
        assertTrue(s.sliding)
        assertFalse(s.swings)
    }
    @Test
    fun muriSingoliConLeAnte() {
        // Fianco di un armadio a muro nel letto, da (400,100) a (400,180), spesso 6 px, con due ante scorrevoli in mezzo.
        val front = PlanReading.ReadWall(
            from = listOf(400.0, 100.0), to = listOf(400.0, 180.0), thicknessPx = 6.0,
            openings = listOf(PlanReading.ReadOpening("double_door", listOf(listOf(400.0, 110.0), listOf(400.0, 170.0)), sliding = true)),
        )
        val plan = PlanReadingImport.toPlan(twoRooms.copy(walls = listOf(front)), "x.jpg").plan
        val w = plan.freeWalls.single()
        assertEquals(Vec2(800.0, 200.0), w.start)
        assertEquals(Vec2(800.0, 360.0), w.end)
        assertEquals(12.0, w.thickness, 1e-9)
        val o = w.openings.single()
        assertEquals(0, o.wallIndex)
        assertEquals(80.0, o.position, 1e-9)
        assertEquals(120.0, o.width, 1e-9)
        assertTrue(o.sliding)
        // Si salva e si rilegge uguale.
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
    }
}
