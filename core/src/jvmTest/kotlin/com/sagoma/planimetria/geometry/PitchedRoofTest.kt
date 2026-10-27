package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Roof
import com.sagoma.planimetria.model.RoofKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tetto inclinato sopra l'ultimo piano: forma delle falde, pianta, 3D e file. */
class PitchedRoofTest {
    /** Muri esterni da 0,0 a 1000×600. */
    private val walls = Bounds(0.0, 0.0, 1000.0, 600.0)
    private val t30 = tan(30 * PI / 180)

    private fun close(a: Double, b: Double, eps: Double = 0.01) = assertTrue(abs(a - b) < eps, "$a invece di $b")

    @Test
    fun `due falde - colmo lungo il lato lungo, a meta, timpani sui lati corti`() {
        val s = assertNotNull(Roofs.shape(Roof(RoofKind.Gable, pitchDeg = 30.0, overhang = 50.0), walls))
        assertEquals(Bounds(-50.0, -50.0, 1050.0, 650.0), s.eaves)
        assertEquals(2, s.faces.size)
        assertEquals(2, s.gables.size)
        close(s.ridgeHeight, t30 * 300 + Roof.THICKNESS)
        val (a, b) = s.ridges.single()
        close(a.y, 300.0); close(b.y, 300.0)
        // Ogni falda è un piano: l'angolo della gronda sta sotto l'appoggio di sporto × pendenza (più lo spessore).
        for (f in s.faces) close(f.heights.min(), -50 * t30 + Roof.THICKNESS)
        // I timpani stanno sul filo dei muri corti e arrivano sotto il colmo.
        for (g in s.gables) {
            assertTrue(g.points.all { abs(it.x) < 0.01 || abs(it.x - 1000) < 0.01 })
            close(g.heights.max(), t30 * 300)
        }
    }

    @Test
    fun `un giro mette il colmo di traverso`() {
        val s = assertNotNull(Roofs.shape(Roof(RoofKind.Gable, turn = 1), walls))
        val (a, b) = s.ridges.single()
        close(a.x, 500.0); close(b.x, 500.0)
        close(s.ridgeHeight, tan(Roof.DEFAULT_PITCH * PI / 180) * 500 + Roof.THICKNESS)
    }

    @Test
    fun `padiglione - quattro falde, colmo e quattro displuvi, niente timpani`() {
        val s = assertNotNull(Roofs.shape(Roof(RoofKind.Hip, pitchDeg = 30.0, overhang = 0.0), walls))
        assertEquals(4, s.faces.size)
        assertEquals(0, s.gables.size)
        assertEquals(5, s.ridges.size)
        // Colmo da 300 a 700 (a metà del lato corto da ogni testata).
        val ridge = s.ridges.first()
        close(minOf(ridge.first.x, ridge.second.x), 300.0); close(maxOf(ridge.first.x, ridge.second.x), 700.0)
        // Su una casa quadrata diventa una piramide: solo i quattro displuvi.
        val square = assertNotNull(Roofs.shape(Roof(RoofKind.Hip), Bounds(0.0, 0.0, 600.0, 600.0)))
        assertEquals(4, square.ridges.size)
    }

    @Test
    fun `una falda - bassa su un lato lungo, alta sull'altro, chiusa dal muro alto e da due triangoli`() {
        val s = assertNotNull(Roofs.shape(Roof(RoofKind.Shed, pitchDeg = 20.0, overhang = 0.0), walls))
        assertEquals(1, s.faces.size)
        assertEquals(3, s.gables.size)
        val f = s.faces.single()
        val rise = tan(20 * PI / 180) * 600
        for ((k, p) in f.points.withIndex()) close(f.heights[k], (if (abs(p.y) < 0.01) 0.0 else rise) + Roof.THICKNESS)
        // Mezzo giro: il lato basso passa dall'altra parte.
        val flipped = assertNotNull(Roofs.shape(Roof(RoofKind.Shed, pitchDeg = 20.0, overhang = 0.0, turn = 2), walls)).faces.single()
        for ((k, p) in flipped.points.withIndex()) close(flipped.heights[k], (if (abs(p.y) < 0.01) rise else 0.0) + Roof.THICKNESS)
    }

    private val house = FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(800.0, 500.0))))

    private fun maxHeight(scene: Scene3D): Float {
        var m = Float.NEGATIVE_INFINITY
        var k = 0
        while (k < scene.opaque.size) { m = maxOf(m, scene.opaque[k + 1]); k += Scene3D.FLOATS_PER_VERTEX }
        return m
    }

    @Test
    fun `nel 3D il tetto sta sopra il solaio dell'ultimo piano, e senza tetto non c'e niente lassu`() {
        val level = 300.0
        val open = Scene3D.build(house, null, null, ceilings = false, levelHeight = level)
        assertTrue(maxHeight(open) <= level + 0.5, "senza tetto la casa finisce sotto l'interpiano")
        val covered = Scene3D.build(house, null, null, ceilings = false, levelHeight = level, roof = Roof(RoofKind.Gable, pitchDeg = 30.0))
        val shape = assertNotNull(Roofs.footprint(house)?.let { Roofs.shape(Roof(RoofKind.Gable, pitchDeg = 30.0), it) })
        close(maxHeight(covered).toDouble(), level + shape.ridgeHeight, 0.5)
        assertTrue(covered.opaque.size > open.opaque.size)
        // Il tetto non si tocca e non ferma chi cammina.
        assertEquals(open.picks.size, covered.picks.size)
        assertEquals(open.obstacles.size, covered.obstacles.size)
    }

    @Test
    fun `il tetto copre il rettangolo dei muri esterni, balconi esclusi`() {
        val b = assertNotNull(Roofs.footprint(house))
        close(b.minX, -Room.WALL_THICKNESS / 2); close(b.maxX, 800 + Room.WALL_THICKNESS / 2)
        assertNull(Roofs.footprint(FloorPlan()))
    }

    @Test
    fun `il tetto si salva nel progetto, e i file vecchi restano senza`() {
        val building = Building(listOf(Floor(1, "Piano terra", house)), roof = Roof(RoofKind.Hip, pitchDeg = 35.0, overhang = 70.0, turn = 1))
        assertEquals(building.roof, PlanJson.decodeBuilding(PlanJson.encode(building)).roof)
        assertNull(PlanJson.decodeBuilding(PlanJson.encode(building.copy(roof = null))).roof)
        // Della casa conta l'ultimo piano.
        val two = building.copy(floors = building.floors + Floor(2, "Primo piano", FloorPlan()))
        assertNull(Roofs.of(two), "ultimo piano vuoto: niente tetto")
        assertNotNull(Roofs.of(building))
    }
}
