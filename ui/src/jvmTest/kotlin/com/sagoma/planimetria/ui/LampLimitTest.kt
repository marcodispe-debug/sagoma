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
import kotlin.test.assertTrue

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
        // Striscia LED di 30 pezzi e 25 faretti in soggiorno, 6 luci dalle porte in camera: 61 luci, più del massimo.
        val living = com.sagoma.planimetria.geometry.Bounds(0.0, 0.0, 1000.0, 1000.0)
        val bedroom = com.sagoma.planimetria.geometry.Bounds(1000.0, 0.0, 2000.0, 1000.0)
        fun at(x: Double, z: Double, lumens: Double, room: com.sagoma.planimetria.geometry.Bounds, spill: Boolean = false, profile: LightProfile = LightProfile.Downlight) =
            Scene3D.SceneLight(Vec3(x, 260.0, z), null, lumens, warm = !spill, room = room, profile = profile, spill = spill)
        val strip = (0 until 30).map { at(30.0 + it * 31.0, 100.0, 50.0, living, profile = LightProfile.Strip) }
        val spots = (0 until 25).map { at(200.0 + (it % 5) * 150.0, 300.0 + (it / 5) * 150.0, 400.0, living, profile = LightProfile.Spot) }
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

    @Test
    fun `i faretti restano dove sono finche bastano i posti delle lampade vere`() {
        // Come nell'appartamento di Marco: in disimpegno due faretti e tre luci dalle porte, in tutta la casa più luci del massimo,
        // ma le lampade vere ci stanno. Prima si univano i due faretti in uno solo in mezzo al corridoio (uno restava spento).
        val hall = com.sagoma.planimetria.geometry.Bounds(750.0, 280.0, 1150.0, 420.0)
        val spotA = Scene3D.SceneLight(Vec3(998.0, 220.0, 407.0), Vec3(0.26, -0.96, -0.05), 400.0, warm = true, room = hall, profile = LightProfile.Spot)
        val spotB = Scene3D.SceneLight(Vec3(912.0, 220.0, 293.0), Vec3(-0.04, -0.95, 0.31), 400.0, warm = true, room = hall, profile = LightProfile.Spot)
        val doors = listOf(1087.0 to 295.0, 1135.0 to 350.0, 810.0 to 405.0).map { (x, z) ->
            Scene3D.SceneLight(Vec3(x, 105.0, z), null, 25.0, warm = true, room = hall, profile = LightProfile.Wall, spill = true)
        }
        // Le altre stanze: lampade e luce di passaggio, ognuna nella sua stanza.
        val others = (0 until LitShaders.MAX_LAMPS - 2).flatMap { k ->
            val box = com.sagoma.planimetria.geometry.Bounds(2000.0 + k * 400.0, 0.0, 2400.0 + k * 400.0, 400.0)
            val c = box.center
            listOf(
                Scene3D.SceneLight(Vec3(c.x, 250.0, c.y), null, 1500.0, warm = true, room = box, profile = LightProfile.Downlight, spill = k % 2 == 1),
            )
        }
        val all = listOf(spotA, spotB) + doors + others
        val fitted = LitShaders.fit(all)
        assertEquals(LitShaders.MAX_LAMPS, fitted.size)
        val real = all.filter { !it.spill }
        for (l in real) assertEquals(1, fitted.count { it.position == l.position && it.direction == l.direction }, "lampada al suo posto: $l")
        // La luce delle porte del disimpegno resta nel disimpegno, dentro i faretti.
        assertEquals(800.0 + 75.0, fitted.filter { it.room == hall }.sumOf { it.lumens }, 1e-6)
    }

    @Test
    fun `con piu stanze che posti restano fuori prima le luci di passaggio`() {
        // Una lampada debole (abbassata al 5%) e tante luci che escono dalle finestre, ognuna nel suo rettangolo fuori.
        val dim = Scene3D.SceneLight(Vec3(200.0, 250.0, 200.0), null, 20.0, warm = true, room = box, profile = LightProfile.Downlight)
        val outside = (0 until LitShaders.MAX_LAMPS).map { k ->
            val zone = com.sagoma.planimetria.geometry.Bounds(k * 300.0, -500.0, k * 300.0 + 200.0, -4.0)
            Scene3D.SceneLight(Vec3(k * 300.0 + 100.0, 150.0, -25.0), null, 60.0, warm = true, room = zone, zones = listOf(zone), profile = LightProfile.Window, spill = true)
        }
        val fitted = LitShaders.fit(outside + dim)
        assertEquals(LitShaders.MAX_LAMPS, fitted.size)
        assertTrue(dim in fitted, "la lampada vera resta")
    }
}
