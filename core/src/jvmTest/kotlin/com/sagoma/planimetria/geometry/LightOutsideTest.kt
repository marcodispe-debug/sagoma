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

/** Di sera la luce delle lampade esce dalle finestre: fuori, davanti a ognuna, senza entrare nelle altre stanze. */
class LightOutsideTest {
    private val lamp = Fixture(1, FixtureKind.CeilingLight, point = Vec2(200.0, 150.0))

    private fun room(id: Long, at: Vec2 = Vec2.Zero, windows: List<Opening> = emptyList(), lamps: List<Fixture> = listOf(lamp), w: Double = 400.0, h: Double = 300.0) =
        Room(id, "Stanza $id", RoomType.Soggiorno, RoomFactory.rectangle(w, h).map { it + at }, openings = windows, fixtures = lamps.map { it.copy(point = it.point + at) })

    private fun outside(plan: FloorPlan) =
        Scene3D.build(plan, focusedRoomId = null, selected = null, ceilings = true).lights.filter { it.profile == LightProfile.Window }

    private fun overlaps(a: Bounds, b: Bounds) = a.minX < b.maxX && b.minX < a.maxX && a.minY < b.maxY && b.minY < a.maxY

    @Test
    fun `dalla finestra esce la luce della stanza`() {
        val plan = FloorPlan(listOf(room(1, windows = listOf(Opening.default(1, OpeningKind.Window2, 0, 200.0)))))
        val out = outside(plan).single()
        val box = Polygon.bounds(plan.rooms[0].points)
        val zone = out.zones!!.single()
        assertTrue(!overlaps(zone, box), "la luce di fuori resta fuori dalla stanza: $zone")
        assertTrue(zone.width >= 300.0 && zone.height >= 300.0, "davanti alla finestra, larga e profonda: $zone")
        assertTrue(out.lumens in LightSpill.MIN_LUMENS..1500.0, "passa una parte della luce: ${out.lumens}")
        // Sta appena dietro il vetro (così non illumina la facciata attorno) e va verso fuori, e un po' in basso.
        assertTrue(out.position.z in 0.0..40.0, "dietro il vetro: ${out.position}")
        assertTrue(out.beamAxis.z < -0.5 && out.beamAxis.y < 0.0, "verso fuori: ${out.beamAxis}")
    }

    @Test
    fun `senza lampade o con la lampada spenta niente luce fuori`() {
        val window = listOf(Opening.default(1, OpeningKind.Window2, 0, 200.0))
        assertTrue(outside(FloorPlan(listOf(room(1, windows = window, lamps = emptyList())))).isEmpty())
        assertTrue(outside(FloorPlan(listOf(room(1, windows = window, lamps = listOf(lamp.copy(brightness = 0.0)))))).isEmpty())
    }

    @Test
    fun `piu luce in stanza piu luce fuori`() {
        val window = listOf(Opening.default(1, OpeningKind.Window2, 0, 200.0))
        val normal = outside(FloorPlan(listOf(room(1, windows = window)))).single().lumens
        val strong = outside(FloorPlan(listOf(room(1, windows = window, lamps = listOf(lamp.copy(brightness = 2.0)))))).single().lumens
        assertEquals(normal * 2, strong, normal * 1e-6)
    }

    @Test
    fun `davanti a un'altra stanza della casa la luce si ferma prima`() {
        // Casa a U vista dal cortile: la finestra della stanza 1 guarda la stanza 2, 300 cm più in là.
        val r1 = room(1, windows = listOf(Opening.default(1, OpeningKind.Window2, 2, 200.0)))
        val r2 = room(2, at = Vec2(0.0, 600.0), lamps = emptyList())
        val plan = FloorPlan(listOf(r1, r2))
        val zone = outside(plan).single().zones!!.single()
        assertTrue(!overlaps(zone, Polygon.bounds(r2.points)), "non entra nella stanza di fronte: $zone")
        assertTrue(!overlaps(zone, Polygon.bounds(r1.points)), "né nella sua: $zone")
    }

    @Test
    fun `finestra tra due stanze non manda luce fuori`() {
        // La finestra della stanza 1 sul muro in comune con la stanza 2.
        val r1 = room(1, windows = listOf(Opening.default(1, OpeningKind.Window1, 1, 150.0)))
        val r2 = room(2, at = Vec2(400.0, 0.0), lamps = emptyList())
        assertTrue(outside(FloorPlan(listOf(r1, r2))).isEmpty())
    }

    @Test
    fun `anche il balcone prende la luce della porta-finestra`() {
        val r1 = room(1, windows = listOf(Opening.default(1, OpeningKind.Balcony2, 2, 200.0)))
        val terrace = Room(2, "Terrazza", RoomType.Terrazza, RoomFactory.rectangle(400.0, 150.0).map { it + Vec2(0.0, 300.0) })
        val zone = outside(FloorPlan(listOf(r1, terrace))).single().zones!!.single()
        assertTrue(overlaps(zone, Polygon.bounds(terrace.points)), "la luce arriva sul terrazzo: $zone")
    }
}
