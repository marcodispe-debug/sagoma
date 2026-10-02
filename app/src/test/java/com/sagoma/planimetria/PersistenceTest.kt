package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Ruler
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.FilePlanStore
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PersistenceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val plan = FloorPlan(
        rooms = listOf(
            Room(
                1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(400.0, 300.0),
                ceilingHeight = 260.0,
                wallHeights = mapOf(2 to 220.0),
                openings = listOf(
                    Opening.default(1, OpeningKind.Door, 0, 100.0).copy(hingeLeft = false, opensInward = false),
                    Opening.default(2, OpeningKind.Passage, 1, 150.0).copy(style = PassageStyle.Arched),
                ),
            ),
            Room(2, "Stanza", RoomType.Altro, RoomFactory.lShape(ShapeDimensions(600.0, 400.0, 300.0, 250.0), LOrientation.BottomLeft)),
        ),
        rulers = listOf(Ruler(1, Vec2(0.0, -50.0), Vec2(123.5, 10.0))),
    )

    @Test
    fun `andata e ritorno in JSON conservano tutta la pianta`() {
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
    }

    @Test
    fun `campi sconosciuti di versioni future vengono ignorati`() {
        val text = """{"version":2,"nuovoCampo":true,"plan":{"rooms":[],"rulers":[],"mobili":[]}}"""
        assertEquals(FloorPlan(), PlanJson.decode(text))
    }

    @Test
    fun `salva e ricarica da file`() {
        val store = FilePlanStore(tmp.root)
        assertNull(store.load())
        store.save(Building.single(plan))
        store.save(Building.single(plan.copy(rulers = emptyList())))
        assertEquals(Building.single(plan.copy(rulers = emptyList())), FilePlanStore(tmp.root).load())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `i file della versione 1 diventano una casa a un piano`() {
        val v1 = """{"version":1,"plan":{"rooms":[{"id":1,"name":"Cucina","type":"Cucina","points":[{"x":0.0,"y":0.0},{"x":100.0,"y":0.0},{"x":100.0,"y":100.0}]}]}}"""
        val b = PlanJson.decodeBuilding(v1)
        assertEquals(1, b.floors.size)
        assertEquals("Piano terra", b.floor.name)
        assertEquals("Cucina", b.floor.plan.rooms.single().name)
    }

    @Test
    fun `più piani e scale si salvano e si rileggono`() {
        val stair = Stair(1, StairKind.UTurn, Vec2(100.0, 100.0), rotation = 90.0, turnLeft = true)
        val b = Building(
            listOf(
                Floor(1, "Piano terra", plan.copy(stairs = listOf(stair))),
                Floor(2, "Primo piano", FloorPlan(rooms = plan.rooms.take(1)), levelHeight = 320.0),
            ),
            current = 1,
        )
        assertEquals(b, PlanJson.decodeBuilding(PlanJson.encode(b)))
    }

    @Test
    fun `file corrotto messo da parte, non sovrascritto`() {
        File(tmp.root, "planimetria.json").writeText("{ non è json")
        assertNull(FilePlanStore(tmp.root).load())
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("planimetria.corrupt-") })
    }
}
