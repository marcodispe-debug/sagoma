package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Il «!» dei problemi sulla pianta: dove va, e quelli tolti dalla pianta (salvati nel progetto). */
class PlanCheckSegniTest {
    private fun rect(id: Long, name: String, type: RoomType, w: Double, h: Double, at: Vec2 = Vec2.Zero, openings: List<Opening> = emptyList()) =
        Room(id, name, type, RoomFactory.rectangle(w, h).map { it + at }, wallThickness = 15.0, openings = openings)

    private val living = rect(
        1, "Soggiorno", RoomType.Soggiorno, 400.0, 300.0,
        openings = listOf(Opening.default(1, OpeningKind.Door, 0, 200.0).copy(entrance = true), Opening.default(2, OpeningKind.Door, 1, 150.0)),
    )
    private val kitchen = rect(2, "Cucina", RoomType.Cucina, 300.0, 300.0, Vec2(400.0, 0.0))

    @Test
    fun `dove va il punto esclamativo`() {
        // Finestra più alta del muro sul muro 3 (da (0,300) a (0,0)) a 100 cm: il segno sull'apertura.
        val tall = Opening(8, OpeningKind.Window1, 3, 100.0, 80.0, 200.0, sillHeight = 100.0)
        val plan = FloorPlan(listOf(living.copy(openings = living.openings + tall), kitchen))
        val issues = PlanCheck.of(Building.single(plan))
        val over = issues.single { it.kind == PlanCheck.Kind.OpeningOverWall }
        val s = assertNotNull(PlanCheck.spot(over, plan))
        assertFalse(s.room)
        assertEquals(0.0, s.at.x, 0.01)
        assertEquals(200.0, s.at.y, 0.01)
        // Le norme della cucina (finestre): nel punto del nome, sopra il quale va il segno.
        val windows = issues.first { it.kind.rule && it.rooms.first() == 2L }
        val r = assertNotNull(PlanCheck.spot(windows, plan))
        assertTrue(r.room)
        assertEquals(Polygon.labelPoint(kitchen.points), r.at)
        // Stanze sovrapposte: dove si sovrappongono.
        val overlap = FloorPlan(listOf(living, kitchen.copy(points = kitchen.points.map { it + Vec2(-100.0, 0.0) })))
        val o = PlanCheck.of(Building.single(overlap)).single { it.kind == PlanCheck.Kind.Overlap }
        assertEquals(o.area.center, assertNotNull(PlanCheck.spot(o, overlap)).at)
        // Impianto più alto del muro: sull'impianto. Il segno segue l'impianto spostato.
        val light = Fixture(1, FixtureKind.WallLight, wallIndex = 3, position = 200.0, elevation = 220.0)
        val attic = rect(3, "Camera", RoomType.Camera, 500.0, 400.0).copy(fixtures = listOf(light), wallHeights = mapOf(3 to 140.0))
        val atticPlan = FloorPlan(listOf(attic))
        val f = PlanCheck.of(Building.single(atticPlan)).single { it.kind == PlanCheck.Kind.FixtureOverWall }
        assertEquals(Fixtures.center(attic, light), assertNotNull(PlanCheck.spot(f, atticPlan)).at)
        val moved = attic.copy(fixtures = listOf(light.copy(position = 100.0)))
        assertEquals(Fixtures.center(moved, moved.fixtures.single()), assertNotNull(PlanCheck.spot(f, FloorPlan(listOf(moved)))).at)
        // Quello di cui parla non c'è più: niente segno.
        assertNull(PlanCheck.spot(f, FloorPlan(listOf(attic.copy(fixtures = emptyList())))))
        assertNull(PlanCheck.spot(over, FloorPlan(listOf(living, kitchen))))
    }

    @Test
    fun `problemi tolti dalla pianta`() {
        val plan = FloorPlan(listOf(living, kitchen.copy(points = kitchen.points.map { it + Vec2(-100.0, 0.0) })))
        val b = Building(listOf(Floor(7, "Piano terra", plan)))
        val issues = PlanCheck.of(b)
        val overlap = issues.single { it.kind == PlanCheck.Kind.Overlap }
        val key = PlanCheck.hideKey(b, overlap)
        assertEquals("Overlap/7/1,2/", key)
        // Ogni problema ha la sua chiave.
        assertEquals(issues.size, issues.map { PlanCheck.hideKey(b, it) }.toSet().size)
        assertFalse(PlanCheck.hidden(b, overlap))
        val hidden = b.copy(hiddenChecks = setOf(key))
        assertTrue(PlanCheck.hidden(hidden, overlap))
        assertTrue(issues.filter { it != overlap }.none { PlanCheck.hidden(hidden, it) })
        // La chiave resta la stessa quando le misure cambiano (la stanza sovrapposta si sposta ancora un po').
        val more = b.copy(floors = listOf(b.floors[0].copy(plan = plan.copy(rooms = listOf(living, kitchen.copy(points = kitchen.points.map { it + Vec2(-120.0, 0.0) }))))))
        val o2 = PlanCheck.of(more).single { it.kind == PlanCheck.Kind.Overlap }
        assertNotEquals(overlap.a, o2.a)
        assertEquals(key, PlanCheck.hideKey(more, o2))
        // Si salva nel progetto; un progetto di prima non ne ha.
        assertEquals(setOf(key), PlanJson.decodeBuilding(PlanJson.encode(hidden)).hiddenChecks)
        assertFalse(PlanJson.encode(b).contains("hiddenChecks"))
        assertEquals(emptySet(), PlanJson.decodeBuilding(PlanJson.encode(b)).hiddenChecks)
    }
}
