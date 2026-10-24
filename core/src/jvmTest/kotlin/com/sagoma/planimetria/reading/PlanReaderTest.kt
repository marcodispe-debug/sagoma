package com.sagoma.planimetria.reading

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** "Leggi la piantina da sola": la lettura della copia piccola torna ai pixel dello sfondo, e il dialogo col server. */
class PlanReaderTest {
    private val small = PlanReading(
        image = PlanReading.ImageSize(1000, 500),
        rooms = listOf(PlanReading.ReadRoom("Cucina", listOf(listOf(100.0, 50.0), listOf(400.0, 50.0), listOf(400.0, 250.0), listOf(100.0, 250.0)), listOf(3.0, 2.0))),
        openings = listOf(PlanReading.ReadOpening("door", listOf(listOf(200.0, 250.0), listOf(280.0, 250.0)), hinge = listOf(200.0, 250.0), opensToward = listOf(220.0, 280.0))),
        walls = listOf(PlanReading.ReadWall(listOf(10.0, 10.0), listOf(10.0, 90.0), thicknessPx = 4.0)),
        exteriorWallPx = 10.0,
        exteriorOutline = listOf(listOf(90.0, 40.0), listOf(410.0, 40.0)),
    )

    @Test
    fun letturaRiportataAllaPiantinaGrande() {
        val big = small.scaledTo(3000, 1500)
        assertEquals(PlanReading.ImageSize(3000, 1500), big.image)
        assertEquals(listOf(300.0, 150.0), big.rooms[0].corners[0])
        assertEquals(listOf(3.0, 2.0), big.rooms[0].sidesM)
        assertEquals(listOf(600.0, 750.0), big.openings[0].hinge)
        assertEquals(listOf(660.0, 840.0), big.openings[0].opensToward)
        assertEquals(12.0, big.walls[0].thicknessPx!!, 1e-9)
        assertEquals(30.0, big.exteriorWallPx!!, 1e-9)
        assertEquals(listOf(1230.0, 120.0), big.exteriorOutline[1])
        // Stessa scala in cm: la piantina grande ha solo più pixel.
        assertEquals(PlanReadingImport.toPlan(small, "a.jpg").cmPerPx!! / 3, PlanReadingImport.toPlan(big, "a.jpg").cmPerPx!!, 1e-9)
    }

    @Test
    fun latoPerIlModello() {
        assertEquals(2560, PlanReader.sideFor(3000, 1000))
        assertEquals(800, PlanReader.sideFor(800, 600))
        // Quadrata: la limita il numero di pixel (3,7 milioni), non il lato.
        val side = PlanReader.sideFor(3000, 3000)
        assertTrue(side < 2560 && side.toDouble() * side <= PlanReader.MAX_PIXELS)
    }

    private fun server(status: Int, body: String, seen: MutableList<Pair<String?, String>>): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/leggi") { ex ->
                seen += ex.requestHeaders.getFirst("X-Sagoma-App") to ex.requestBody.readBytes().decodeToString()
                val bytes = body.toByteArray()
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }

    @Test
    fun letturaDalServer() = runBlocking {
        val seen = mutableListOf<Pair<String?, String>>()
        val s = server(200, PlanReading.encode(small), seen)
        try {
            val r = HttpPlanReader("http://127.0.0.1:${s.address.port}/", device = "prova-1234", appKey = "chiave").read(byteArrayOf(1, 2, 3), "image/jpeg", 1000, 500)
            assertEquals(small, assertIs<PlanReaderResult.Ok>(r).reading)
            val (key, body) = seen.single()
            assertEquals("chiave", key)
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("AQID", json["image"]!!.jsonPrimitive.content)
            assertEquals(1000, json["width"]!!.jsonPrimitive.int)
            assertEquals("prova-1234", json["device"]!!.jsonPrimitive.content)
        } finally {
            s.stop(0)
        }
    }

    @Test
    fun erroriDelServer() = runBlocking {
        val s = server(429, """{"error":"troppe_letture","message":"Riprova domani."}""", mutableListOf())
        try {
            val r = HttpPlanReader("http://127.0.0.1:${s.address.port}", device = "prova-1234").read(byteArrayOf(1), "image/jpeg", 10, 10)
            assertEquals(PlanReaderResult.Refused("troppe_letture", "Riprova domani."), r)
        } finally {
            s.stop(0)
        }
        // Nessuno in ascolto.
        val port = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).let { val p = it.address.port; it.stop(0); p }
        assertEquals(PlanReaderResult.Unreachable, HttpPlanReader("http://127.0.0.1:$port", device = "prova-1234").read(byteArrayOf(1), "image/jpeg", 10, 10))
    }

    @Test
    fun codiceDelDispositivoStabile() {
        val dir = Files.createTempDirectory("dispositivo").toFile()
        val id = HttpPlanReader.deviceId(dir)
        assertTrue(id.matches(Regex("[A-Za-z0-9-]{8,64}")))
        assertEquals(id, HttpPlanReader.deviceId(dir))
    }
}
