package com.sagoma.planimetria.reading

import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.model.Building
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Letture strane, come può restituirle un'AI che sbaglia: stanze con meno di 3 angoli o angoli con una coordinata sola,
 * misure zero o negative, aperture e scale con un punto solo o due punti uguali, contorno esterno con un punto, immagine
 * a misura zero. La lettura non deve mai fermare l'app: le parti senza senso si scartano, il resto diventa la pianta.
 */
class StrangeReadingsTest {
    private val good = PlanReading.ReadRoom("Cucina", listOf(listOf(100.0, 100.0), listOf(300.0, 100.0), listOf(300.0, 250.0), listOf(100.0, 250.0)), listOf(4.0, 3.0), 12.0)
    private val image = PlanReading.ImageSize(1000, 800)
    private fun opening(kind: String, vararg ends: List<Double>, width: Double? = null) = PlanReading.ReadOpening(kind, ends.toList(), widthCm = width)

    private val cases: Map<String, PlanReading> = mapOf(
        "stanza con 2 angoli" to PlanReading(image = image, rooms = listOf(good, PlanReading.ReadRoom("B", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0))))),
        "angolo con una coordinata" to PlanReading(image = image, rooms = listOf(good, PlanReading.ReadRoom("B", listOf(listOf(400.0), listOf(500.0, 100.0), listOf(500.0, 200.0), listOf(400.0, 200.0))))),
        "angolo con tre coordinate" to PlanReading(image = image, rooms = listOf(PlanReading.ReadRoom("B", listOf(listOf(400.0, 100.0, 7.0), listOf(500.0, 100.0), listOf(500.0, 200.0), listOf(400.0, 200.0)), listOf(2.0, 2.0)))),
        "stanza schiacciata" to PlanReading(image = image, rooms = listOf(good, PlanReading.ReadRoom("B", listOf(listOf(0.0, 0.0), listOf(100.0, 0.0), listOf(200.0, 0.0), listOf(50.0, 0.0)), listOf(3.0, 3.0), 9.0))),
        "angoli tutti uguali" to PlanReading(image = image, rooms = listOf(PlanReading.ReadRoom("B", List(4) { listOf(5.0, 5.0) }, listOf(3.0, 3.0), 9.0))),
        "misure zero e negative" to PlanReading(image = image, rooms = listOf(good.copy(sidesM = listOf(0.0, -3.0), areaM2 = -1.0), good.copy(name = "Due", corners = good.corners.map { listOf(it[0] + 220, it[1]) }, sidesM = listOf(0.0, 0.0), areaM2 = 0.0))),
        "misure enormi" to PlanReading(image = image, rooms = listOf(good.copy(sidesM = listOf(1e9, 1e-9), areaM2 = 1e12))),
        "nessuna stanza" to PlanReading(image = image, rooms = emptyList(), openings = listOf(opening("door", listOf(1.0, 1.0), listOf(20.0, 1.0)))),
        "immagine a misura zero" to PlanReading(image = PlanReading.ImageSize(0, 0), rooms = listOf(good)),
        "aperture con un punto o punti uguali" to PlanReading(
            image = image, rooms = listOf(good),
            openings = listOf(
                opening("door", listOf(150.0, 100.0)),
                opening("window", listOf(150.0, 100.0), listOf(150.0, 100.0)),
                opening("balcony"),
                opening("door", listOf(150.0), listOf(190.0, 100.0)),
                opening("door", listOf(150.0, 100.0), listOf(190.0, 100.0), width = -80.0),
                opening("door", listOf(150.0, 100.0), listOf(190.0, 100.0), width = 0.0),
                opening("finestra strana", listOf(150.0, 100.0), listOf(190.0, 100.0)),
                opening("door", listOf(150.0, 100.0), listOf(190.0, 100.0)).copy(hinge = listOf(1.0), opensToward = listOf()),
                opening("window", listOf(150.0, 100.0), listOf(190.0, 100.0)).copy(splayCm = -20.0, heightCm = -5.0, sillCm = -90.0),
            ),
        ),
        "scale strane" to PlanReading(
            image = image, rooms = listOf(good),
            stairs = listOf(
                PlanReading.ReadStair(listOf(150.0, 150.0), listOf(150.0, 150.0)),
                PlanReading.ReadStair(listOf(150.0), listOf(200.0, 150.0)),
                PlanReading.ReadStair(listOf(150.0, 150.0), listOf(250.0, 150.0), widthCm = -90.0, treads = -3),
                PlanReading.ReadStair(listOf(150.0, 150.0), listOf(250.0, 150.0), widthPx = 0.0, treads = 0),
                PlanReading.ReadStair(listOf(150.0, 150.0), listOf(250.0, 150.0), treads = 100_000),
            ),
        ),
        "muri singoli strani" to PlanReading(
            image = image, rooms = listOf(good),
            walls = listOf(
                PlanReading.ReadWall(listOf(150.0, 150.0), listOf(150.0, 150.0)),
                PlanReading.ReadWall(listOf(150.0), listOf(200.0, 150.0)),
                PlanReading.ReadWall(listOf(150.0, 150.0), listOf(250.0, 150.0), thicknessPx = -4.0, openings = listOf(opening("door", listOf(160.0, 150.0)))),
            ),
        ),
        "contorno e muri esterni strani" to PlanReading(image = image, rooms = listOf(good), exteriorWallPx = -10.0, exteriorOutline = listOf(listOf(1.0), listOf(2.0, 2.0))),
        "zone non calpestabili strane" to PlanReading(image = image, rooms = listOf(good.copy(excluded = listOf(listOf(listOf(1.0, 1.0)), listOf(listOf(120.0), listOf(150.0, 120.0), listOf(150.0, 150.0)), List(3) { listOf(120.0, 120.0) })))),
    )

    @Test
    fun nessunaLetturaFermaLApp() {
        val problems = mutableListOf<String>()
        for ((name, reading) in cases) {
            try {
                val plan = PlanReadingImport.toPlan(reading, "sfondo.jpg").plan
                for (r in plan.rooms) {
                    assertTrue(r.points.size >= 3 && r.points.all { it.x.isFinite() && it.y.isFinite() }, "stanza ${r.name} non valida: ${r.points}")
                    assertTrue(r.openings.all { it.wallIndex in 0 until r.wallCount && it.width > 0 && it.width.isFinite() }, "apertura non valida in ${r.name}: ${r.openings}")
                    assertTrue(r.wallThicknesses.values.all { it > 0 && it.isFinite() } && r.wallThickness > 0, "spessori non validi in ${r.name}")
                }
                assertTrue(plan.stairs.all { it.width > 0 && it.width.isFinite() && it.center.x.isFinite() }, "scala non valida: ${plan.stairs}")
                assertTrue(plan.freeWalls.all { it.thickness > 0 && it.start.distanceTo(it.end) > 0 }, "muro singolo non valido: ${plan.freeWalls}")
                // E da lì il resto dell'app.
                Scene3D.build(plan, null, null, ceilings = true)
                Takeoff.of(Building.single(plan))
            } catch (e: Throwable) {
                problems += "$name: ${e::class.simpleName}: ${e.message?.take(200)} @ ${e.stackTrace.firstOrNull { it.className.startsWith("com.sagoma") }}"
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
