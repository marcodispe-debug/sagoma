package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertTrue

/** Specchiando una stanza, il fascio dei faretti a parete si specchia con lei (prima restava girato dalla parte di prima). */
class MirrorSpotTest {
    private val spot = Fixture(1, FixtureKind.WallSpot, wallIndex = 0, position = 120.0, elevation = 220.0, aimYaw = 35.0, aimTilt = 30.0)
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0), fixtures = listOf(spot))
    private val plan = FloorPlan(listOf(room))
    private val c = Vec2(250.0, 200.0)

    private fun beam(p: FloorPlan): Vec3 = p.rooms.single().let { r -> SpotAim.directionOf(r, r.fixtures.single()) }

    private fun near(expected: Vec3, actual: Vec3, what: String) =
        assertTrue(SpotAim.angleBetween(expected, actual) < 0.01, "$what: atteso $expected, ottenuto $actual")

    @Test
    fun `specchio orizzontale - il fascio va dalla parte opposta`() {
        val d = beam(plan)
        near(Vec3(-d.x, d.y, d.z), beam(GroupOps.mirror(plan, setOf(GroupItem.RoomItem(1)), c, horizontal = true)), "orizzontale")
    }

    @Test
    fun `specchio verticale - il fascio va dalla parte opposta`() {
        val d = beam(plan)
        near(Vec3(d.x, d.y, -d.z), beam(GroupOps.mirror(plan, setOf(GroupItem.RoomItem(1)), c, horizontal = false)), "verticale")
    }

    @Test
    fun `due specchi tornano al faretto di partenza`() {
        val twice = GroupOps.mirror(GroupOps.mirror(plan, setOf(GroupItem.RoomItem(1)), c, true), setOf(GroupItem.RoomItem(1)), c, true)
        near(beam(plan), beam(twice), "due volte")
    }
}
