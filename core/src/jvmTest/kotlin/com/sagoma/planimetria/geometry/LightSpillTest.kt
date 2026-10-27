package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La luce di una stanza passa nelle stanze vicine solo dai varchi: porte, passaggi, muri tolti. */
class LightSpillTest {
    // Soggiorno 400×300 e, a destra, camera 400×300: il muro di destra del soggiorno (1) tocca quello di sinistra della camera (3).
    private val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
    private val bedroom = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(400.0, 0.0) })
    private val door = Opening.default(1, OpeningKind.Door, 1, 150.0)
    private val livingBox = Polygon.bounds(living.points)
    private val bedroomBox = Polygon.bounds(bedroom.points)

    private fun lamp(at: Vec2, lumens: Double = 1500.0, room: Bounds = livingBox) =
        Scene3D.SceneLight(at.at(260.0), null, lumens, warm = true, room = room, profile = LightProfile.Downlight)

    @Test
    fun `dalla porta aperta la luce passa nella stanza vicina, rivolta verso di lei`() {
        val plan = FloorPlan(listOf(living.copy(openings = listOf(door)), bedroom))
        val spill = LightSpill.through(plan, listOf(lamp(Vec2(300.0, 150.0))))
        val l = spill.single()
        assertTrue(l.spill)
        assertEquals(bedroomBox, l.room)
        assertTrue(l.position.x > 400.0, "sulla soglia, dalla parte della camera: ${l.position}")
        assertTrue(l.axis.x > 0.3, "va verso la camera: ${l.axis}")
        assertTrue(l.lumens in LightSpill.MIN_LUMENS..300.0, "passa una parte della luce: ${l.lumens}")
        assertEquals(LightProfile.Wall, l.profile)
        assertTrue(l.warm)
    }

    @Test
    fun `una lampada vicina alla porta ne manda di piu di una lontana`() {
        val plan = FloorPlan(listOf(living.copy(openings = listOf(door)), bedroom))
        val near = LightSpill.through(plan, listOf(lamp(Vec2(340.0, 150.0)))).single().lumens
        val far = LightSpill.through(plan, listOf(lamp(Vec2(40.0, 150.0)))).single().lumens
        assertTrue(near > far * 1.5, "vicina $near, lontana $far")
    }

    @Test
    fun `senza varco, con una finestra o con la luce di un'altra stanza non passa niente`() {
        val lit = listOf(lamp(Vec2(300.0, 150.0)))
        assertEquals(emptyList(), LightSpill.through(FloorPlan(listOf(living, bedroom)), lit))
        val window = Opening.default(1, OpeningKind.Window1, 1, 150.0)
        assertEquals(emptyList(), LightSpill.through(FloorPlan(listOf(living.copy(openings = listOf(window)), bedroom)), lit))
        // Lampada della camera: esce dalla porta verso il soggiorno, non verso la camera stessa.
        val other = LightSpill.through(FloorPlan(listOf(living.copy(openings = listOf(door)), bedroom)), listOf(lamp(Vec2(500.0, 150.0), room = bedroomBox)))
        assertEquals(livingBox, other.single().room)
        assertTrue(other.single().axis.x < -0.3)
    }

    @Test
    fun `tra due ambienti col muro tolto passa piu luce che da una porta`() {
        val lit = listOf(lamp(Vec2(300.0, 150.0)))
        val open = FloorPlan(listOf(living.copy(removedWalls = setOf(1)), bedroom.copy(removedWalls = setOf(3))))
        val throughDoor = LightSpill.through(FloorPlan(listOf(living.copy(openings = listOf(door)), bedroom)), lit).single().lumens
        assertTrue(LightSpill.through(open, lit).single().lumens > throughDoor * 2)
        // Muro tolto solo da una parte: l'altra stanza ha ancora il suo muro, la luce non passa.
        assertEquals(emptyList(), LightSpill.through(FloorPlan(listOf(living.copy(removedWalls = setOf(1)), bedroom)), lit))
    }

    @Test
    fun `nella scena 3D la plafoniera del soggiorno manda luce in camera dalla porta`() {
        val ceiling = Fixture(1, FixtureKind.CeilingLight, point = Vec2(200.0, 150.0))
        val plan = FloorPlan(listOf(living.copy(openings = listOf(door), fixtures = listOf(ceiling)), bedroom))
        val scene = Scene3D.build(plan, focusedRoomId = null, selected = null, ceilings = false)
        assertEquals(1, scene.lights.count { !it.spill })
        assertEquals(bedroomBox, scene.lights.single { it.spill }.room)
    }
}
