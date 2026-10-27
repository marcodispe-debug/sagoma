package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
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
}
