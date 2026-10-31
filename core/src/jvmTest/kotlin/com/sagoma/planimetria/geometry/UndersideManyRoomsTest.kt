package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Solaio visto da sotto: si calcola togliendo dal pavimento le stanze del piano di sotto. Con stanze che si sovrappongono
 * e muri curvi (che nel 3D diventano tanti pezzi dritti) i pezzi si moltiplicavano e il 3D restava bloccato per decine di
 * secondi (minuti con più stanze). Trovato con la prova di resistenza.
 */
class UndersideManyRoomsTest {

    private var id = 0L
    private fun room(pts: List<Pair<Double, Double>>, bulges: Map<Int, Double> = emptyMap()) =
        Room(++id, "Stanza $id", RoomType.Camera, pts.map { (x, y) -> Vec2(x, y) }, wallBulges = bulges)

    @Test
    fun stanzeSovrapposteConMuriCurviSuDuePiani() {
        val plan = FloorPlan(
            listOf(
                room(listOf(250.0 to 520.0, 750.0 to 520.0, 750.0 to 920.0, 250.0 to 920.0), mapOf(1 to 184.0)),
                room(listOf(250.0 to 575.0, 750.0 to 575.0, 750.0 to 975.0, 138.0 to 908.0), mapOf(1 to 184.0, 2 to -111.0)),
                room(listOf(56.0 to 629.0, 556.0 to 629.0, 556.0 to 1029.0, -56.0 to 963.0), mapOf(1 to 184.0, 2 to -111.0)),
                room(listOf(-43.0 to 683.0, 457.0 to 683.0, 457.0 to 1083.0, -43.0 to 1083.0), mapOf(1 to 184.0)),
                room(listOf(207.0 to 738.0, 707.0 to 738.0, 707.0 to 1138.0, 207.0 to 1138.0), mapOf(1 to 184.0)),
                room(listOf(207.0 to 792.0, 707.0 to 792.0, 707.0 to 1192.0, 95.0 to 1126.0), mapOf(1 to 184.0, 2 to -111.0)),
                room(listOf(13.0 to 847.0, 513.0 to 847.0, 513.0 to 1247.0, -99.0 to 1180.0), mapOf(1 to 184.0, 2 to -111.0)),
                room(listOf(-86.0 to 901.0, 414.0 to 901.0, 414.0 to 1301.0, -86.0 to 1301.0), mapOf(1 to 184.0)),
            ),
        )
        val start = System.nanoTime()
        Scene3D.build(plan, null, null, ceilings = true, levelHeight = 300.0, below = listOf(Floor(1, "Terra", plan, 300.0)))
        val seconds = (System.nanoTime() - start) / 1e9
        // Prima della correzione circa 10 s su un computer veloce; ora qualche decimo.
        assertTrue(seconds < 4, "3D del piano di sopra in $seconds s")
    }

    @Test
    fun ritaglioConBuchiLontaniEUnoCheCopreMeta() {
        val tri = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(0.0, 100.0))
        val far = (0 until 500).map { k -> listOf(Vec2(1000.0 + k, 0.0), Vec2(1010.0 + k, 0.0), Vec2(1010.0 + k, 10.0)) }
        assertEquals(listOf(tri), Clip.subtractAll(tri, far))
        // Un buco che copre la parte con x < 50: resta il triangolino con x > 50.
        val half = listOf(Vec2(-10.0, -10.0), Vec2(50.0, -10.0), Vec2(50.0, 200.0), Vec2(-10.0, 200.0))
        assertEquals(50.0 * 50.0 / 2, Clip.subtractAll(tri, far + listOf(half)).sumOf { Polygon.area(it) }, 1e-6)
        // Coperto da due buchi che si sovrappongono: non resta niente.
        val left = listOf(Vec2(-10.0, -10.0), Vec2(60.0, -10.0), Vec2(60.0, 200.0), Vec2(-10.0, 200.0))
        val right = listOf(Vec2(40.0, -10.0), Vec2(200.0, -10.0), Vec2(200.0, 200.0), Vec2(40.0, 200.0))
        assertEquals(0.0, Clip.subtractAll(tri, listOf(left, right)).sumOf { Polygon.area(it) }, 1e-6)
    }

    @Test
    fun casaDellaProvaDiResistenzaConTanteStanzeSovrapposte() {
        // Tre piani da 124 stanze messe a caso, che si sovrappongono, metà con un muro curvo (solo le stanze del progetto
        // salvato dalla prova di resistenza). Il 3D ci metteva quasi due minuti: l'app restava bloccata.
        val text = javaClass.getResource("/casa-stanze-sovrapposte.json")!!.readText()
        val b = PlanJson.decodeBuilding(text)
        val start = System.nanoTime()
        Scene3D.build(b.floors[2].plan, null, null, ceilings = false, levelHeight = 300.0, below = b.floors.take(2))
        val seconds = (System.nanoTime() - start) / 1e9
        // Prima della correzione circa 50 s; ora qualche secondo.
        assertTrue(seconds < 25, "3D del piano di sopra in $seconds s")
    }

    @Test
    fun centinaiaDiStanzeSovrappostePezziGiusti() {
        // Il ritaglio più veloce deve lasciare esattamente la parte scoperta: un pavimento di 20 × 20 m sopra 400 stanze
        // messe a caso, che si coprono a vicenda.
        val rnd = Random(7)
        val floor = listOf(Vec2(0.0, 0.0), Vec2(2000.0, 0.0), Vec2(2000.0, 2000.0), Vec2(0.0, 2000.0))
        val rooms = (0 until 400).map {
            val x = rnd.nextInt(-30, 200) * 10.0
            val y = rnd.nextInt(-30, 200) * 10.0
            val w = rnd.nextInt(5, 40) * 10.0
            val h = rnd.nextInt(5, 40) * 10.0
            listOf(Vec2(x, y), Vec2(x + w, y), Vec2(x + w, y + h), Vec2(x, y + h))
        }
        // Come nel 3D, stanze e pavimento ritagliati a triangoli (le diagonali danno rettangoli che si toccano senza sovrapporsi).
        fun triangles(r: List<Vec2>) = listOf(listOf(r[0], r[1], r[2]), listOf(r[0], r[2], r[3]))
        val holes = rooms.flatMap(::triangles)
        val start = System.nanoTime()
        val pieces = triangles(floor).flatMap { Clip.subtractAll(it, holes) }
        val seconds = (System.nanoTime() - start) / 1e9
        // Il pezzo scoperto, calcolato a strisce verticali: in ogni striscia la lunghezza non coperta da nessuna stanza.
        val xs = (rooms.flatMap { r -> listOf(r[0].x, r[1].x) } + listOf(0.0, 2000.0)).map { it.coerceIn(0.0, 2000.0) }.distinct().sorted()
        var free = 0.0
        for (i in 0 until xs.size - 1) {
            val mid = (xs[i] + xs[i + 1]) / 2
            val spans = rooms.filter { it[0].x < mid && mid < it[1].x }.map { it[0].y.coerceIn(0.0, 2000.0) to it[2].y.coerceIn(0.0, 2000.0) }.sortedBy { it.first }
            var covered = 0.0
            var end = 0.0
            for ((a, b) in spans) {
                if (b > end) { covered += b - maxOf(a, end); end = b }
            }
            free += (xs[i + 1] - xs[i]) * (2000.0 - covered)
        }
        assertTrue(free > 0, "la prova deve lasciare qualcosa di scoperto")
        assertEquals(free, pieces.sumOf { Polygon.area(it) }, 1.0)
        assertTrue(seconds < 5, "ritaglio in $seconds s")
    }
}
