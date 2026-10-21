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
            "Balcone" to RoomType.Balcone, "Terrazzo" to RoomType.Terrazza, "Portico" to RoomType.Terrazza,
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
}
