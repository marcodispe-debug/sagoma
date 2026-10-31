package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals

/** Computo in CSV: i nomi scritti dall'utente con «;», virgolette o a capo non devono spostare le colonne. */
class TakeoffCsvTest {
    private fun room(id: Long, name: String, dx: Double = 0.0) =
        Room(id, name, RoomType.Camera, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(dx, 0.0) })

    /** Righe del CSV divise in celle come le legge un foglio di calcolo (virgolette doppie = una virgoletta). */
    private fun parse(csv: String, sep: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when {
                quoted && c == '"' && csv.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == sep -> { row += cell.toString(); cell.clear() }
                !quoted && c == '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        return rows
    }

    private val names = listOf("Camera; grande", "Studio \"Marco\"", "Bagno\nospiti", "Cucina")
    private val building = Building(listOf(Floor(1, "Piano; terra", FloorPlan(names.mapIndexed { i, n -> room(i + 1L, n, i * 500.0) }))))

    @Test
    fun `nomi con punto e virgola, virgolette e a capo restano in una cella`() {
        for (sep in listOf(';', '\t')) {
            val rows = parse(Takeoff.csv(Takeoff.of(building), sep), sep)
            val header = rows[1]
            val roomRows = rows.drop(2).take(names.size)
            for ((k, r) in roomRows.withIndex()) {
                assertEquals(header.size, r.size, "riga di «${names[k]}» con il separatore ${sep.code}: $r")
                assertEquals("Piano; terra", r[0])
                assertEquals(names[k], r[1])
            }
        }
    }

    @Test
    fun `nomi normali scritti come prima`() {
        val csv = Takeoff.csv(Takeoff.of(Building.single(FloorPlan(listOf(room(1, "Camera"))))))
        assertEquals(true, csv.lines().any { it.startsWith("Piano terra;Camera;") }, csv)
        assertEquals(false, csv.contains('"'), csv)
    }
}
