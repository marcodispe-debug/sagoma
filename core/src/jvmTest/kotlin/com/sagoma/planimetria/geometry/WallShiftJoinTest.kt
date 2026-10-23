package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Muro accanto a un tratto spostato dallo scalino: finisce sulla mezzeria vera, senza sporgere dall'altra parte. */
class WallShiftJoinTest {
    // Camera 400×300; muro destro diviso a metà: sopra 28 cm sulla linea, sotto 10 cm spostato di 9 cm verso dentro
    // (filo interno dritto a x = 386), come la camera piccola dell'appartamento al tondo.
    private val room = Room(
        1, "Camera", RoomType.Camera,
        listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 150.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)),
    ).copy(wallThicknesses = mapOf(1 to 28.0, 2 to 10.0), wallOffsets = mapOf(2 to -9.0))

    @Test
    fun `il muro di fondo finisce sulla mezzeria vera del tratto spostato`() {
        assertEquals(9.0, room.shiftedEndDeltas(3).first, 1e-9)
        assertEquals(0.0, room.shiftedEndDeltas(3).second, 1e-9)
        // Il tratto spostato e gli altri muri non cambiano lunghezza.
        assertEquals(0.0 to 0.0, room.shiftedEndDeltas(2))
        assertEquals(0.0 to 0.0, room.shiftedEndDeltas(0))
    }

    @Test
    fun `nel 3D nulla sporge oltre il filo esterno del tratto sottile`() {
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val v = scene.opaque
        val outerFace = 400.0 - 9.0 + 5.0
        // Solo i muri (triangoli che salgono da terra); x e z in pianta, y in alto; 10 numeri per vertice.
        for (t in 0 until v.size / 30) {
            val p = (0 until 3).map { j -> Triple(v[t * 30 + j * 10].toDouble(), v[t * 30 + j * 10 + 1].toDouble(), v[t * 30 + j * 10 + 2].toDouble()) }
            if (p.all { it.second < 1.0 }) continue
            for ((x, _, z) in p) if (z > 160.0) assertTrue(x <= outerFace + 1e-3, "vertice a x = $x, z = $z oltre il filo esterno $outerFace")
        }
    }
}
