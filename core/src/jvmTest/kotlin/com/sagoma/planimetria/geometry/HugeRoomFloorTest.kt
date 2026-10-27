package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Stanza enorme per una misura scritta per sbaglio (es. 450 con le misure in metri, cioè 450 m invece di 4,50): con il
 * parquet il 3D disegnava milioni di fughe e finiva la memoria (sul computer con 1 km di lato; sul telefono molto prima).
 */
class HugeRoomFloorTest {
    @Test
    fun parquetInUnaStanzaDiUnChilometro() {
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(100_000.0, 100_000.0), floorFinish = FloorFinish.OakParquet)
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
        val floats = scene.opaque.size
        assertTrue(floats < 1_000_000, "$floats valori nel 3D")
    }

    @Test
    fun stanzaNormaleHaAncoraLeFughe() {
        fun floats(finish: FloorFinish) =
            Scene3D.build(FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0), floorFinish = finish))), null, null, ceilings = true).opaque.size
        assertTrue(floats(FloorFinish.OakParquet) > floats(FloorFinish.RoomColor))
    }
}
