package com.sagoma.planimetria.ui

import com.sagoma.planimetria.geometry.LightProfile
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sul computer lo shader tiene al massimo [LitShaders.MAX_LAMPS] luci. Le luci che passano dalle porte (deboli) non devono
 * togliere il posto alle lampade vere.
 */
class LampLimitTest {
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(2000.0, 1000.0))
    private val box = Polygon.bounds(room.points)
    private val scene = Scene3D.build(FloorPlan(listOf(room)), focusedRoomId = null, selected = null, ceilings = false)
    private val night = Daylight(hour = 22f, lamps = "on", walking = true)

    private fun light(x: Double, lumens: Double, spill: Boolean) =
        Scene3D.SceneLight(Vec3(x, 260.0, 500.0), null, lumens, warm = true, room = box, profile = LightProfile.Downlight, spill = spill)

    @Ignore // DIFETTO: le luci sono scelte solo per distanza, quelle deboli dalle porte tolgono il posto alle lampade vere.
    @Test
    fun `con troppe luci restano le lampade vere, non quelle che passano dalle porte`() {
        // 22 lampade vere lontane da chi guarda e 6 luci deboli dalle porte vicine.
        val real = (0 until LitShaders.MAX_LAMPS - 2).map { light(1000.0 + it * 40.0, 1500.0, spill = false) }
        val spill = (0 until 6).map { light(100.0 + it * 10.0, 30.0, spill = true) }
        val lights = LitShaders.Lights(scene.withLights(spill + real), night, viewer = Vec3(0.0, 160.0, 500.0))
        assertEquals(LitShaders.MAX_LAMPS, lights.lampCount)
        val strengths = (0 until lights.lampCount).map { lights.lamps[it * 4 + 3] }
        assertEquals(real.size, strengths.count { it > 1f }, "tutte le lampade vere ci sono")
    }
}
