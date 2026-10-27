package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.Phases
import com.sagoma.planimetria.geometry.PhaseView
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.model.*
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Progetti con dati fuori misura (file scritti a mano, da altri programmi o da versioni future dell'app): pianta, 3D, computo e
 * editor non si devono bloccare, e le modifiche fatte dopo averli aperti si devono salvare.
 */
class StrangeFilesTest {
    private fun room(id: Long, dx: Double = 0.0) =
        Room(id, "Stanza $id", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(dx, 0.0) })

    private val base = room(1)
    private val cases: Map<String, Building> = mapOf(
        "piano corrente oltre" to Building(listOf(Floor(1, "PT", FloorPlan(listOf(room(1)))), Floor(2, "P1", FloorPlan(listOf(room(2))))), current = 5),
        "piano corrente negativo" to Building(listOf(Floor(1, "PT", FloorPlan(listOf(room(1)))), Floor(2, "P1", FloorPlan(listOf(room(2))))), current = -1),
        "stanza 2 punti" to Building.single(FloorPlan(listOf(base, Room(2, "x", RoomType.Camera, listOf(Vec2(500.0, 0.0), Vec2(800.0, 0.0)))))),
        "stanza 0 punti" to Building.single(FloorPlan(listOf(base, Room(2, "x", RoomType.Camera, emptyList())))),
        "stanza punti uguali" to Building.single(FloorPlan(listOf(base, Room(2, "x", RoomType.Camera, listOf(Vec2(500.0, 0.0), Vec2(500.0, 0.0), Vec2(800.0, 0.0), Vec2(800.0, 300.0)))))),
        "stanza piatta" to Building.single(FloorPlan(listOf(base, Room(2, "x", RoomType.Camera, listOf(Vec2(500.0, 0.0), Vec2(600.0, 0.0), Vec2(800.0, 0.0)))))),
        "apertura muro 9" to Building.single(FloorPlan(listOf(base.copy(openings = listOf(Opening(1, OpeningKind.Door, 9, 50.0, 80.0, 210.0)))))),
        "apertura larghezza negativa" to Building.single(FloorPlan(listOf(base.copy(openings = listOf(Opening(1, OpeningKind.Window2, 0, 50.0, -80.0, 140.0, 90.0)))))),
        "apertura enorme" to Building.single(FloorPlan(listOf(base.copy(openings = listOf(Opening(1, OpeningKind.Door, 0, 5000.0, 9000.0, 900.0)))))),
        "impianto muro 9" to Building.single(FloorPlan(listOf(base.copy(fixtures = listOf(Fixture(1, FixtureKind.Radiator, 9, 50.0, length = 60.0, height = 60.0, elevation = 15.0), Fixture(2, FixtureKind.WallLight, 9, 50.0, elevation = 200.0)))))),
        "mappe muro 9" to Building.single(FloorPlan(listOf(base.copy(
            wallThicknesses = mapOf(9 to 30.0), wallPhases = mapOf(9 to Phase.Demolish), removedWalls = setOf(9), wallBulges = mapOf(9 to 40.0),
            wallOffsets = mapOf(9 to 5.0), wallCuts = mapOf(9 to WallCut(true, 50.0)), wallHeights = mapOf(9 to 100.0), wallFinishes = mapOf(9 to WallFinish.of(WallPaint.White)),
        )))),
        "soffitto zero" to Building.single(FloorPlan(listOf(base.copy(ceilingHeight = 0.0)))),
        "soffitto negativo" to Building.single(FloorPlan(listOf(base.copy(ceilingHeight = -50.0)))),
        "spessore zero" to Building.single(FloorPlan(listOf(base.copy(wallThickness = 0.0)))),
        "spessore negativo" to Building.single(FloorPlan(listOf(base.copy(wallThickness = -10.0)))),
        "id stanza doppio" to Building.single(FloorPlan(listOf(room(1), room(1, 400.0)))),
        "scala misure zero" to Building.single(FloorPlan(listOf(base), stairs = listOf(Stair(1, StairKind.entries.first(), Vec2(100.0, 100.0), width = 0.0, tread = 0.0, diameter = 0.0, risers = 0)))),
        "colonna zero" to Building.single(FloorPlan(listOf(base), columns = listOf(Column(1, Vec2(100.0, 100.0), width = 0.0, depth = 0.0)))),
        "trave zero" to Building.single(FloorPlan(listOf(base), beams = listOf(Beam(1, Vec2(100.0, 100.0), Vec2(100.0, 100.0))))),
        "muro singolo zero" to Building.single(FloorPlan(listOf(base), freeWalls = listOf(FreeWall(1, Vec2(100.0, 100.0), Vec2(100.0, 100.0))))),
        "arredo zero" to Building.single(FloorPlan(listOf(base), furniture = listOf(Furniture(1, "sconosciuto", Vec2(100.0, 100.0), width = 0.0, depth = 0.0, height = 0.0)))),
        "interpiano zero" to Building(listOf(Floor(1, "PT", FloorPlan(listOf(room(1))), levelHeight = 0.0), Floor(2, "P1", FloorPlan(listOf(room(2))), levelHeight = 0.0)), current = 1),
    )

    private fun tryIt(what: String, block: () -> Unit): String? = try { block(); null } catch (e: Throwable) { "$what: ${e::class.simpleName} ${e.message?.take(120)}" }

    @Test
    fun `file strani - niente si blocca e le modifiche si salvano`() {
        val report = mutableListOf<String>()
        for ((name, b0) in cases) {
            val problems = mutableListOf<String>()
            val b = try { PlanJson.decodeBuilding(PlanJson.encode(b0)) } catch (e: Throwable) { report += "$name: LETTURA ${e::class.simpleName} ${e.message}"; continue }
            for ((i, f) in b.floors.withIndex()) {
                val plan = f.plan
                problems += listOfNotNull(
                    tryIt("3D") { Scene3D.build(plan, null, null, true, f.levelHeight, below = b.floors.take(i)) },
                    tryIt("3D progetto") { Scene3D.build(Phases.view(plan, PhaseView.Project), null, null, false) },
                    tryIt("collisioni") { Collisions.of(plan) },
                    tryIt("pianta") {
                        val img = ImageBitmap(400, 300)
                        val density = Density(1f)
                        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
                        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(400f, 300f)) {
                            drawPlan(EditorUiState(plan = plan, camera = Camera(0.5f, 50f, 50f)), measurer, background = Color.White)
                        }
                    },
                )
            }
            problems += listOfNotNull(tryIt("computo") { Takeoff.csv(Takeoff.of(b)) })
            var saved: Building? = null
            problems += listOfNotNull(tryIt("editor") {
                val vm = EditorViewModel(object : PlanStore {
                    override fun load() = b
                    override fun save(building: Building) { saved = building }
                })
                vm.addColumn(ColumnShape.Round)
                vm.saveBlocking()
                val cols = saved!!.floors.sumOf { it.plan.columns.count { c -> c.shape == ColumnShape.Round } }
                if (cols != 1) error("colonna aggiunta persa nel salvataggio ($cols)")
                vm.undo(); vm.redo()
            })
            if (problems.isNotEmpty()) report += "$name: " + problems.joinToString(" | ")
        }
        assertTrue(report.isEmpty(), report.joinToString("\n"))
    }

    @Test
    fun `piano corrente fuori misura - si apre il piano piu vicino e la modifica resta su quel piano`() {
        val b = PlanJson.decodeBuilding(PlanJson.encode(cases.getValue("piano corrente oltre")))
        assertEquals(1, b.current)
        var saved: Building? = null
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = Building(b.floors, current = 7) // anche se arriva da un archivio che non passa dalla lettura
            override fun save(building: Building) { saved = building }
        })
        vm.addColumn(ColumnShape.Round)
        vm.saveBlocking()
        assertEquals(listOf(0, 1), saved!!.floors.map { it.plan.columns.size })
    }
}
