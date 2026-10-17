package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Clip
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StairsTest {

    private val rise = 300.0

    @Test
    fun `gradini tutti uguali e non più alti di 18 cm`() {
        assertEquals(17, Stairs.risers(300.0))
        assertEquals(17, Stairs.risers(306.0))
        assertEquals(18, Stairs.risers(306.5))
        val l = Stairs.layout(Stair(1, StairKind.Straight, Vec2.Zero), rise)
        assertEquals(300.0 / 17, l.riser, 1e-9)
        // 16 pedate: l'ultima alzata porta al pavimento di sopra.
        assertEquals(16, l.steps.size)
        assertEquals(16 * l.riser, l.steps.last().top, 1e-9)
        assertEquals(90.0, l.bounds.width, 1e-9)
        assertEquals(16 * 28.0, l.bounds.height, 1e-9)
        // L'ingombro è centrato sul centro della scala.
        assertEquals(0.0, l.bounds.center.x, 1e-9)
        assertEquals(0.0, l.bounds.center.y, 1e-9)
    }

    @Test
    fun `la rampa dritta sale verso l'alto dello schermo, ruotata di 90 gradi verso destra`() {
        val up = Stairs.layout(Stair(1, StairKind.Straight, Vec2.Zero), rise)
        assertTrue(up.path.last().y < up.path.first().y)
        val right = Stairs.layout(Stair(1, StairKind.Straight, Vec2.Zero, rotation = 90.0), rise)
        assertTrue(right.path.last().x > right.path.first().x)
        assertEquals(16 * 28.0, right.bounds.width, 1e-6)
    }

    @Test
    fun `scala a L - due rampe e un pianerottolo, gira a destra o a sinistra`() {
        val l = Stairs.layout(Stair(1, StairKind.LTurn, Vec2.Zero), rise)
        val landings = l.steps.filter { it.landing }
        assertEquals(1, landings.size)
        // 17 alzate: 8 pedate, pianerottolo, 7 pedate.
        assertEquals(16, l.steps.size)
        assertEquals(9 * l.riser, landings.single().top, 1e-9)
        // Le quote salgono sempre.
        assertTrue(l.steps.zipWithNext().all { (a, b) -> b.top > a.top })
        assertTrue(l.path.last().x > l.path.first().x)
        val left = Stairs.layout(Stair(1, StairKind.LTurn, Vec2.Zero, turnLeft = true), rise)
        assertTrue(left.path.last().x < left.path.first().x)
    }

    @Test
    fun `scala a L con meno gradini prima del pianerottolo e più dopo`() {
        val l = Stairs.layout(Stair(1, StairKind.LTurn, Vec2.Zero, firstFlight = 4), rise)
        val landing = l.steps.indexOfFirst { it.landing }
        assertEquals(4, landing) // 4 pedate, poi il pianerottolo
        assertEquals(5 * l.riser, l.steps[landing].top, 1e-9)
        assertEquals(11, l.steps.size - landing - 1) // 17 alzate: 4 + pianerottolo + 11
        // La prima rampa è più corta: l'ingombro in altezza è 4 pedate + la larghezza del pianerottolo.
        assertEquals(4 * 28.0 + 90.0, l.bounds.height, 1e-9)
        // Valori fuori misura si limitano: almeno un gradino per rampa.
        val clamped = Stairs.layout(Stair(1, StairKind.LTurn, Vec2.Zero, firstFlight = 99), rise)
        assertEquals(1, clamped.steps.size - clamped.steps.indexOfFirst { it.landing } - 1)
    }

    @Test
    fun `più alzate rendono la scala meno ripida e più lunga`() {
        val auto = Stairs.layout(Stair(1, StairKind.Straight, Vec2.Zero), rise)
        val gentle = Stairs.layout(Stair(1, StairKind.Straight, Vec2.Zero, risers = 20), rise)
        assertEquals(15.0, gentle.riser, 1e-9)
        assertTrue(gentle.bounds.height > auto.bounds.height)
        // Scelta a parole: "ripida" = alzate di circa 19,5 cm, pedata per un passo comodo.
        val steep = Stairs.withSlope(Stair(1, StairKind.Straight, Vec2.Zero), rise, 19.5)
        assertEquals(15, steep.risers)
        assertEquals(23.0, steep.tread, 1e-9) // 63 − 2 × 20
        assertTrue(Stairs.slopeDegrees(20.0, 23.0) > Stairs.slopeDegrees(15.0, 33.0))
    }

    @Test
    fun `scala a U con la seconda rampa più lunga - l'ingombro la contiene`() {
        val u = Stairs.layout(Stair(1, StairKind.UTurn, Vec2.Zero, firstFlight = 3), rise)
        for (st in u.steps) for (p in st.polygon) assertTrue(u.contains(p) || u.pieces.any { pc -> Polygon.bounds(pc).let { p.x in it.minX - 1e-6..it.maxX + 1e-6 && p.y in it.minY - 1e-6..it.maxY + 1e-6 } })
        assertEquals(12 * 28.0 - 3 * 28.0 + (3 * 28.0 + 90.0), u.bounds.height, 1e-9)
    }

    @Test
    fun `scala a U - si arriva tornando indietro accanto alla prima rampa`() {
        val u = Stairs.layout(Stair(1, StairKind.UTurn, Vec2.Zero), rise)
        assertEquals(2 * 90.0 + Stairs.U_GAP, u.bounds.width, 1e-9)
        assertEquals(8 * 28.0 + 90.0, u.bounds.height, 1e-9)
        assertTrue(u.path.last().y > u.path[2].y) // la seconda rampa scende sullo schermo, cioè torna indietro
    }

    @Test
    fun `chiocciola - gradini a spicchio attorno al palo, dentro il cerchio`() {
        val s = Stair(1, StairKind.Spiral, Vec2(100.0, 50.0), diameter = 160.0)
        val l = Stairs.layout(s, rise)
        assertEquals(16, l.steps.size)
        for (st in l.steps) for (p in st.polygon) assertTrue(p.distanceTo(s.center) <= 80.0 + 1e-6)
        assertTrue(l.contains(Vec2(100.0, 50.0)))
        assertFalse(l.contains(Vec2(190.0, 50.0)))
    }

    @Test
    fun `trascinata vicino a un muro la scala si accosta alla sua faccia`() {
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))
        val plan = FloorPlan(listOf(room))
        // Lato sinistro dell'ingombro a 7.5 + 10 cm dalla mezzeria del muro di sinistra (x = 0).
        val s = Stair(1, StairKind.Straight, Vec2(45.0 + 17.5, 300.0))
        val snapped = Stairs.snapped(plan, s, rise)
        assertEquals(7.5, Stairs.layout(snapped, rise).bounds.minX, 1e-9)
    }

    @Test
    fun `in 3D il piano di sopra ha il vuoto della scala e la scala si può toccare`() {
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))
        val stair = Stair(1, StairKind.Straight, Vec2(250.0, 300.0))
        val ground = com.sagoma.planimetria.model.Floor(1, "Piano terra", FloorPlan(listOf(room), stairs = listOf(stair)))
        val down = com.sagoma.planimetria.geometry.Vec3(0.0, -1.0, 0.0)
        // Piano terra: la scala si tocca, e il primo gradino è basso.
        val g = com.sagoma.planimetria.geometry.Scene3D.build(ground.plan, null, null, ceilings = false, levelHeight = rise)
        val hitStair = g.hit(com.sagoma.planimetria.geometry.Vec3(250.0, 1000.0, 300.0), down)!!
        assertEquals(com.sagoma.planimetria.geometry.Pick.Stair(1), hitStair.pick)
        // Primo piano: sopra la scala il pavimento non c'è (e il piano di sotto non si tocca).
        val upper = com.sagoma.planimetria.geometry.Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false, below = listOf(ground))
        assertEquals(null, upper.hit(com.sagoma.planimetria.geometry.Vec3(250.0, 1000.0, 300.0), down))
        val floorHit = upper.hit(com.sagoma.planimetria.geometry.Vec3(50.0, 1000.0, 50.0), down)!!
        assertEquals(com.sagoma.planimetria.geometry.Pick.RoomFloor(1), floorHit.pick)
        assertEquals(0.0, floorHit.point.y, 1e-6)
    }

    @Test
    fun `ringhiera del vano scala - tutto il contorno tranne l'arrivo e i lati contro i muri`() {
        val s = Stair(1, StairKind.Straight, Vec2(250.0, 300.0), wellRailing = com.sagoma.planimetria.model.StairRailing.Metal)
        val free = Stairs.wellRailingRuns(s, rise, emptyList()).sumOf { (a, b) -> a.distanceTo(b) }
        assertEquals(2 * (90.0 + 16 * 28.0) - 90.0, free, 1e-6)
        // Scala contro il muro di sinistra: anche quel lato resta senza ringhiera.
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))
        val walls = (0 until room.wallCount).map { room.wallStart(it) to room.wallEnd(it) }
        val against = s.copy(center = Vec2(7.5 + 45.0, 300.0))
        val runs = Stairs.wellRailingRuns(against, rise, walls)
        assertEquals(90.0 + 16 * 28.0, runs.sumOf { (a, b) -> a.distanceTo(b) }, 1e-6)
        // Tipi a due rampe: il vuoto interno tra i pezzi non ha ringhiera, l'arrivo nemmeno.
        for (kind in listOf(StairKind.LTurn, StairKind.LWinder, StairKind.UTurn, StairKind.StraightLanding, StairKind.Spiral)) {
            val k = Stair(1, kind, Vec2(250.0, 300.0))
            val l = Stairs.layout(k, rise)
            val r = Stairs.wellRailingRuns(k, rise, emptyList())
            assertTrue("$kind", r.isNotEmpty())
            for ((a, b) in r) assertTrue("$kind", l.steps.last().polygon.let { p -> p.indices.none { i -> Polygon.distanceToSegment((a + b) / 2.0, p[i], p[(i + 1) % p.size]) < 0.1 && kind != StairKind.Spiral } } || kind == StairKind.Spiral)
        }
    }

    @Test
    fun `in 3D la ringhiera del vano è al piano di sopra, alta 100 cm`() {
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))
        val s = Stair(1, StairKind.Straight, Vec2(250.0, 300.0), wellRailing = com.sagoma.planimetria.model.StairRailing.Metal)
        val ground = com.sagoma.planimetria.model.Floor(1, "Piano terra", FloorPlan(listOf(room), stairs = listOf(s)))
        val scene = com.sagoma.planimetria.geometry.Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false, below = listOf(ground))
        val down = com.sagoma.planimetria.geometry.Vec3(0.0, -1.0, 0.0)
        // Fianco del vano (x = 205): il corrimano a 100 cm sopra il pavimento del piano di sopra.
        val side = scene.hit(com.sagoma.planimetria.geometry.Vec3(205.0, 1000.0, 300.0), down)
        assertEquals(100.0, side!!.point.y, 0.5)
        // Nel vano, appena dentro l'arrivo (in alto): nessuna ringhiera, si scende nel vuoto.
        assertEquals(null, scene.hit(com.sagoma.planimetria.geometry.Vec3(250.0, 1000.0, 300.0 - 224.0 + 1.0), down))
        // Senza ringhiera sul fianco c'è solo il bordo del pavimento.
        val bare = com.sagoma.planimetria.geometry.Scene3D.build(
            FloorPlan(listOf(room)), null, null, ceilings = false,
            below = listOf(ground.copy(plan = ground.plan.copy(stairs = listOf(s.copy(wellRailing = com.sagoma.planimetria.model.StairRailing.None))))),
        )
        assertEquals(0.0, bare.hit(com.sagoma.planimetria.geometry.Vec3(205.0, 1000.0, 300.0), down)!!.point.y, 0.5)
        assertEquals(com.sagoma.planimetria.geometry.Pick.StairWell(1), side.pick)
    }

    @Test
    fun `un triangolo meno un buco quadrato - resta l'area giusta`() {
        val tri = listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(0.0, 400.0))
        val hole = RoomFactory.rectangle(100.0, 100.0).map { it + Vec2(50.0, 50.0) }
        val pieces = Clip.subtract(tri, hole)
        assertEquals(Polygon.area(tri) - 100.0 * 100.0, pieces.sumOf { Polygon.area(it) }, 1e-6)
        // Un buco che non tocca il triangolo non cambia niente.
        val far = hole.map { it + Vec2(1000.0, 0.0) }
        assertEquals(Polygon.area(tri), Clip.subtract(tri, far).sumOf { Polygon.area(it) }, 1e-6)
    }

    /** Il corrimano è un profilo spesso circa 3 cm: la sua linea di mezzo è tagliata alla quota, i suoi spigoli sporgono un poco. */
    private val RAIL_THICKNESS = 3.0

    /** Quota massima dei triangoli del colore della ringhiera (grigio scuro 0,30 · 0,32 · 0,34) in una scena. */
    private fun railTop(scene: com.sagoma.planimetria.geometry.Scene3D): Double? {
        val a = scene.opaque
        var top: Double? = null
        var i = 0
        while (i + 9 < a.size) {
            if (Math.abs(a[i + 6] - 0.30f) < 0.005f && Math.abs(a[i + 7] - 0.32f) < 0.005f && Math.abs(a[i + 8] - 0.34f) < 0.005f) {
                top = maxOf(top ?: -1e9, a[i + 1].toDouble())
            }
            i += 10
        }
        return top
    }

    @Test
    fun `la ringhiera tagliata dal solaio si ferma alla faccia inferiore del solaio, non a quella superiore`() {
        val room = Room(1, "Ingresso", RoomType.Altro, RoomFactory.rectangle(500.0, 600.0))
        fun scene(railing: com.sagoma.planimetria.model.StairRailing, aboveFloor: Boolean): com.sagoma.planimetria.geometry.Scene3D {
            val s = Stair(1, StairKind.Straight, Vec2(250.0, 300.0), railing = railing, railingAboveFloor = aboveFloor)
            return com.sagoma.planimetria.geometry.Scene3D.build(FloorPlan(listOf(room), stairs = listOf(s)), null, null, ceilings = false, levelHeight = rise)
        }
        val slabBottom = rise - com.sagoma.planimetria.model.Floor.SLAB // 270: pavimento di sopra (300) meno lo spessore del solaio
        val cut = railTop(scene(com.sagoma.planimetria.model.StairRailing.Metal, aboveFloor = false))
        assertTrue("serve una ringhiera", cut != null)
        assertTrue("tagliata a $cut: deve fermarsi a $slabBottom (parte bassa del solaio), non a $rise (parte alta)", cut!! <= slabBottom + RAIL_THICKNESS)
        // La ringhiera arriva davvero fino alla quota di taglio (non è stata accorciata di più).
        assertTrue(railTop(scene(com.sagoma.planimetria.model.StairRailing.Metal, aboveFloor = false))!! >= slabBottom - RAIL_THICKNESS)
        // Se la ringhiera continua sopra il pavimento il comportamento è quello di prima: supera la quota del pavimento.
        assertTrue(railTop(scene(com.sagoma.planimetria.model.StairRailing.Metal, aboveFloor = true))!! > rise)
    }
}
