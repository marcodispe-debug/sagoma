package com.sagoma.planimetria.ui

import com.sagoma.planimetria.geometry.LightProfile
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sul computer lo shader tiene al massimo [LitShaders.MAX_LAMPS] luci. Le luci che passano dalle porte (deboli) non devono
 * togliere il posto alle lampade vere, e camminando nessuna luce deve spegnersi.
 */
class LampLimitTest {
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(2000.0, 1000.0))
    private val box = Polygon.bounds(room.points)
    private val scene = Scene3D.build(FloorPlan(listOf(room)), focusedRoomId = null, selected = null, ceilings = false)
    private val night = Daylight(hour = 22f, lamps = "on", walking = true)

    private fun light(x: Double, lumens: Double, spill: Boolean) =
        Scene3D.SceneLight(Vec3(x, 260.0, 500.0), null, lumens, warm = true, room = box, profile = LightProfile.Downlight, spill = spill)

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

    @Test
    fun `con piu luci del massimo, camminando restano accese tutte e la luce non cambia`() {
        // Striscia LED di 20 pezzi e 15 faretti in soggiorno, 6 luci dalle porte in camera: 41 luci, più del massimo.
        val living = com.sagoma.planimetria.geometry.Bounds(0.0, 0.0, 1000.0, 1000.0)
        val bedroom = com.sagoma.planimetria.geometry.Bounds(1000.0, 0.0, 2000.0, 1000.0)
        fun at(x: Double, z: Double, lumens: Double, room: com.sagoma.planimetria.geometry.Bounds, spill: Boolean = false, profile: LightProfile = LightProfile.Downlight) =
            Scene3D.SceneLight(Vec3(x, 260.0, z), null, lumens, warm = !spill, room = room, profile = profile, spill = spill)
        val strip = (0 until 20).map { at(50.0 + it * 45.0, 100.0, 50.0, living, profile = LightProfile.Strip) }
        val spots = (0 until 15).map { at(200.0 + (it % 5) * 150.0, 400.0 + (it / 5) * 150.0, 400.0, living, profile = LightProfile.Spot) }
        val doors = (0 until 6).map { at(1015.0, 100.0 + it * 150.0, 40.0, bedroom, spill = true) }
        val all = strip + spots + doors
        val lit = scene.withLights(all)
        fun lampsSeenFrom(viewer: Vec3): List<List<Float>> {
            val l = LitShaders.Lights(lit, night, viewer)
            assertEquals(LitShaders.MAX_LAMPS, l.lampCount)
            return (0 until l.lampCount).map { i -> (0 until 4).map { l.lamps[i * 4 + it] } }.sortedWith(compareBy({ it[0] }, { it[1] }, { it[2] }))
        }
        val here = lampsSeenFrom(Vec3(100.0, 160.0, 100.0))
        assertEquals(here, lampsSeenFrom(Vec3(1900.0, 160.0, 900.0)), "da ogni punto le stesse luci")
        // Nessuna luce persa: la somma è quella di tutte le lampade (s = lumen / 1000 × 0,9).
        assertEquals(all.sumOf { it.lumens } / 1000.0 * 0.9, here.sumOf { it[3].toDouble() }, 1e-3)
        // Le luci della camera restano in camera, quelle del soggiorno in soggiorno.
        val fitted = LitShaders.fit(all)
        assertEquals(doors.sumOf { it.lumens }, fitted.filter { it.room == bedroom }.sumOf { it.lumens }, 1e-6)
        assertEquals(LitShaders.MAX_LAMPS, fitted.size)
    }
}
