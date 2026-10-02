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
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.SnapEngine
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import java.io.File
import kotlin.test.Test

/** Immagini di controllo della pianta (in build/): le guide di aggancio durante il trascinamento di un angolo. */
class PlanImageTest {
    @Test
    fun guideDiAggancio() {
        val a = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
        val b = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(700.0, 0.0) })
        // Angolo in basso a destra del soggiorno trascinato verso destra, quasi alla y dell'angolo della cucina.
        val targets = SnapEngine.targets(FloorPlan(listOf(a, b)), excludeRoom = 1)
        val r = SnapEngine.snap(Vec2(560.0, 297.0), targets, 12.0, listOf(Vec2(500.0, 0.0), Vec2(0.0, 400.0)), a.points)
        val moved = a.copy(points = a.points.toMutableList().also { it[2] = r.point })
        val plan = FloorPlan(listOf(moved, b))
        val w = 1200; val h = 700
        val cam = Camera(1.0f, 100f, 150f)
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color.White)
            val state = EditorUiState(plan = plan, camera = cam, snapGuides = r.guides)
            drawPlan(state, measurer, background = Color.White)
            drawSnapGuides(r.guides, cam, measurer)
        }
        File("build").mkdirs()
        File("build/prova-guide.png").writeBytes(SkiaImages.encodePng(img)!!)
        println("Aggancio: ${r.point} ${r.guides.map { it.kind }}")
    }

    /** Due balconi che si toccano: un'unica superficie, senza ringhiera in mezzo (build/prova-balconi.png). */
    @Test
    fun balconiAccostati() {
        val house = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
        val b1 = Room(2, "Balcone", RoomType.Balcone, RoomFactory.rectangle(200.0, 150.0).map { it + Vec2(400.0, 0.0) })
        val b2 = Room(3, "Terrazza", RoomType.Terrazza, RoomFactory.rectangle(200.0, 150.0).map { it + Vec2(400.0, 150.0) })
        val w = 900; val h = 500
        val cam = Camera(1.0f, 80f, 80f)
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color.White)
            drawPlan(EditorUiState(plan = FloorPlan(listOf(house, b1, b2)), camera = cam), measurer, background = Color.White)
        }
        File("build").mkdirs()
        File("build/prova-balconi.png").writeBytes(SkiaImages.encodePng(img)!!)
    }

    /** Muri eliminati: quello in comune tra soggiorno e cucina e uno esterno della cucina (build/prova-muri-eliminati.png). */
    @Test
    fun muriEliminati() {
        val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)).copy(removedWalls = setOf(1))
        val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })
        val k = kitchen.copy(removedWalls = setOf(3, 1))
        val w = 1000; val h = 500
        val cam = Camera(1.0f, 100f, 100f)
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color.White)
            drawPlan(EditorUiState(plan = FloorPlan(listOf(living, k)), camera = cam), measurer, background = Color.White)
        }
        File("build").mkdirs()
        File("build/prova-muri-eliminati.png").writeBytes(SkiaImages.encodePng(img)!!)
    }

    /** Primo piano con il pavimento sopra la prima rampa e il pianerottolo (build/prova-vano-coperto.png). */
    @Test
    fun vanoScalaCoperto() {
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0))
        val stair = com.sagoma.planimetria.model.Stair(1, com.sagoma.planimetria.model.StairKind.LTurn, Vec2(300.0, 250.0), wellRailing = com.sagoma.planimetria.model.StairRailing.Metal)
        val l = com.sagoma.planimetria.geometry.Stairs.layout(stair, 300.0)
        val covered = stair.copy(coveredSteps = l.steps.indexOfFirst { it.landing } + 1)
        val ground = com.sagoma.planimetria.model.Floor(1, "Piano terra", FloorPlan(listOf(room), stairs = listOf(covered)), 300.0)
        val upper = com.sagoma.planimetria.model.Floor(2, "Primo piano", FloorPlan(listOf(room.copy(name = "Camera", type = RoomType.Camera))))
        val b = com.sagoma.planimetria.model.Building(listOf(ground, upper), current = 1)
        val w = 900; val h = 650
        val cam = Camera(1.2f, 90f, 50f)
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color.White)
            val st = EditorUiState(plan = upper.plan, building = b, camera = cam)
            drawPlan(st, measurer, background = Color.White)
            drawStairs(st, measurer)
        }
        File("build").mkdirs()
        File("build/prova-vano-coperto.png").writeBytes(SkiaImages.encodePng(img)!!)
    }

    /** Quote manuali e testi (build/prova-quote-testi.png). */
    @Test
    fun quoteETesti() {
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
        val dims = listOf(
            com.sagoma.planimetria.model.Dimension(1, Vec2(0.0, 0.0), Vec2(500.0, 0.0), offset = 50.0),
            com.sagoma.planimetria.model.Dimension(2, Vec2(500.0, 0.0), Vec2(500.0, 400.0), offset = 50.0),
            com.sagoma.planimetria.model.Dimension(3, Vec2(0.0, 400.0), Vec2(250.0, 150.0), offset = 20.0, text = "diag. 354"),
        )
        val notes = listOf(
            com.sagoma.planimetria.model.TextNote(1, Vec2(180.0, 300.0), "Parquet rovere", 25.0, arrowTo = Vec2(320.0, 360.0)),
            com.sagoma.planimetria.model.TextNote(2, Vec2(40.0, 200.0), "Parete da demolire", 15.0, rotation = -90.0),
        )
        val plan = FloorPlan(listOf(room), dimensions = dims, annotations = notes)
        val w = 1000; val h = 700
        val cam = Camera(1.2f, 160f, 140f)
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color.White)
            val st = EditorUiState(plan = plan, camera = cam, selection = com.sagoma.planimetria.editor.Selection.Dimension(2))
            drawPlan(st, measurer, background = Color.White)
            drawNotes(plan, cam, measurer, Color.White, showDimensions = true, showTexts = true, selectedDimension = 2)
        }
        File("build").mkdirs()
        File("build/prova-quote-testi.png").writeBytes(SkiaImages.encodePng(img)!!)
    }

    @Test
    fun disegnoMuri() {
        val drawn = listOf(Vec2(0.0, 0.0), Vec2(450.0, 0.0), Vec2(450.0, 250.0))
        val r = SnapEngine.snapDrawing(Vec2(210.0, 262.0), drawn.last(), drawn.first(), SnapEngine.Targets(emptyList(), emptyList(), emptyList()), drawn, 12.0)
        val wd = com.sagoma.planimetria.editor.WallDraw(drawn, r.point)
        val w = 1000; val h = 600
        val cam = Camera(1.2f, 150f, 120f)
        val density = Density(1.5f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(0xFFF8F9FA))
            drawWallDraft(wd, cam, measurer)
            drawSnapGuides(r.guides, cam, measurer)
        }
        File("build/prova-disegno-muri.png").writeBytes(SkiaImages.encodePng(img)!!)
    }
}
