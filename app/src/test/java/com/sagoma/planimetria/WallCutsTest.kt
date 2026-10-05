package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallCut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WallCutsTest {

    // Stanza 600 × 400, soffitto 270. Muri: 0 in alto (verso destra), 1 a destra, 2 in basso, 3 a sinistra.
    // Il muro di destra è il muretto del sottotetto (200 cm); i muri in alto e in basso scendono verso di lui
    // negli ultimi 150 cm.
    private val attic = Room(
        1, "Mansarda", RoomType.Altro, RoomFactory.rectangle(600.0, 400.0),
        wallHeights = mapOf(1 to 200.0),
        wallCuts = mapOf(0 to WallCut(towardEnd = true, start = 150.0), 2 to WallCut(towardEnd = false, start = 150.0)),
    )

    @Test
    fun `la parete è alta fino al punto scelto, poi scende fino al muro basso`() {
        assertEquals(270.0, Ceilings.wallTopAt(attic, 0, 0.0), 1e-9)
        assertEquals(270.0, Ceilings.wallTopAt(attic, 0, 450.0), 1e-9) // inizio discesa
        assertEquals(235.0, Ceilings.wallTopAt(attic, 0, 525.0), 1e-9)
        assertEquals(200.0, Ceilings.wallTopAt(attic, 0, 600.0), 1e-9) // all'altezza del muro basso
        assertEquals(Vec2(450.0, 0.0), Ceilings.startPoint(attic, 0))
        // Il muro in basso va nel verso opposto: scende verso il suo inizio (x = 600).
        assertEquals(235.0, Ceilings.wallTopAt(attic, 2, 75.0), 1e-9)
    }

    @Test
    fun `serve un muro perpendicolare più basso`() {
        val flat = attic.copy(wallHeights = emptyMap())
        assertTrue(Ceilings.cutOptions(flat, 0).isEmpty())
        assertNull(Ceilings.effectiveCut(flat, 0)) // il taglio salvato non ha effetto
        assertEquals(270.0, Ceilings.wallTopAt(flat, 0, 590.0), 1e-9)
        assertEquals(listOf(true), Ceilings.cutOptions(attic, 0))
    }

    @Test
    fun `il soffitto scende allo stesso modo verso il muro basso`() {
        assertEquals(270.0, Ceilings.heightAt(attic, Vec2(300.0, 200.0)), 1e-9)
        assertEquals(235.0, Ceilings.heightAt(attic, Vec2(525.0, 200.0)), 1e-9)
        assertEquals(200.0, Ceilings.heightAt(attic, Vec2(600.0, 100.0)), 1e-9)
        val tris = Ceilings.triangles(attic)
        val area = tris.sumOf { (a, b, c) -> Polygon.area(listOf(a.flat, b.flat, c.flat)) }
        assertEquals(600.0 * 400.0, area, 1.0)
        for ((a, b, c) in tris) for (v in listOf(a, b, c)) assertEquals(Ceilings.heightAt(attic, v.flat), v.y, 1e-6)
        // Il volume è minore di quello a soffitto piano.
        assertTrue(Ceilings.volumeM3(attic) < Ceilings.volumeM3(attic.copy(wallCuts = emptyMap())))
    }

    @Test
    fun `muro in comune tagliato - la diagonale vince anche se la stanza accanto non è tagliata`() {
        // Sotto la mansarda due stanze: a sinistra (0..300) senza taglio, a destra (300..600) con il taglio.
        // Il muro in basso della mansarda (y = 400) è in comune con entrambe.
        val left = Room(2, "Sinistra", RoomType.Altro, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(0.0, 400.0) })
        val right = Room(
            3, "Destra", RoomType.Altro, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(300.0, 400.0) },
            wallHeights = mapOf(1 to 200.0),
            wallCuts = mapOf(0 to WallCut(towardEnd = true, start = 150.0)),
        )
        val scene = Scene3D.build(FloorPlan(listOf(attic, left, right)), null, null, ceilings = true)
        // A 75 cm dal muro basso la parete in comune è a metà discesa, non a 270.
        val sloped = scene.hit(Vec3(525.0, 1000.0, 400.0), Vec3(0.0, -1.0, 0.0))!!
        assertEquals(235.0, sloped.point.y, 0.5)
        // Lontano dal muro basso resta alta.
        val high = scene.hit(Vec3(150.0, 1000.0, 400.0), Vec3(0.0, -1.0, 0.0))!!
        assertEquals(270.0, high.point.y, 0.5)
    }

    @Test
    fun `muro in comune - è tagliato solo il lato della stanza con il taglio`() {
        // Sotto la mansarda una stanza senza taglio lungo tutto il muro in basso (y = 400).
        val below = Room(2, "Soggiorno", RoomType.Altro, RoomFactory.rectangle(600.0, 300.0).map { it + Vec2(0.0, 400.0) })
        val plan = FloorPlan(listOf(attic, below))
        val scene = Scene3D.build(plan, null, null, ceilings = true)
        // Lato mansarda (sopra la linea del muro): a metà discesa.
        assertEquals(235.0, scene.hit(Vec3(525.0, 1000.0, 396.0), Vec3(0.0, -1.0, 0.0))!!.point.y, 0.5)
        // Lato soggiorno: parete intera.
        assertEquals(270.0, scene.hit(Vec3(525.0, 1000.0, 404.0), Vec3(0.0, -1.0, 0.0))!!.point.y, 0.5)
        // Un interruttore alto sul lato del soggiorno ci sta; una porta nel muro in comune deve stare sotto
        // entrambe le facce.
        val sw = com.sagoma.planimetria.model.Fixture.default(1, com.sagoma.planimetria.model.FixtureKind.Switch)
            .copy(wallIndex = 0, position = 580.0, elevation = 240.0)
        val door = com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Door, 0, 540.0)
        val hits = com.sagoma.planimetria.geometry.Collisions.of(
            FloorPlan(listOf(attic, below.copy(fixtures = listOf(sw), openings = listOf(door))))
        )
        assertTrue(hits.none { it.fixtureId == 1L })
        assertEquals(setOf(1L), hits.mapNotNull { it.openingId }.toSet())
    }

    @Test
    fun `avviso se un oggetto supera il taglio diagonale`() {
        // Sul muro in alto (taglio negli ultimi 150 cm verso x = 600): una porta alta 210 vicino al muro basso
        // non ci sta (lì la parete scende sotto i 210), una finestra al centro sì.
        val door = com.sagoma.planimetria.model.Opening.default(1, com.sagoma.planimetria.model.OpeningKind.Door, 0, 540.0)
        val window = com.sagoma.planimetria.model.Opening.default(2, com.sagoma.planimetria.model.OpeningKind.Window1, 0, 200.0)
        val radiator = com.sagoma.planimetria.model.Fixture.default(1, com.sagoma.planimetria.model.FixtureKind.Radiator)
            .copy(wallIndex = 0, position = 560.0, elevation = 150.0) // arriva a 210
        val outlet = com.sagoma.planimetria.model.Fixture.default(2, com.sagoma.planimetria.model.FixtureKind.Outlet)
            .copy(wallIndex = 0, position = 580.0) // a 30 cm da terra: ci sta
        val plan = FloorPlan(listOf(attic.copy(openings = listOf(door, window), fixtures = listOf(radiator, outlet))))
        val hits = com.sagoma.planimetria.geometry.Collisions.of(plan)
        assertEquals(setOf(1L), hits.mapNotNull { it.openingId }.toSet())
        assertEquals(setOf(1L), hits.mapNotNull { it.fixtureId }.toSet())
        val d = hits.first { it.openingId == 1L }
        assertEquals(210.0, d.objectTop, 1e-6)
        // Il bordo destro della porta è a x = 580: 20 cm dal muro basso → 200 + 70 × 20 / 150.
        assertEquals(200.0 + 70.0 * 20 / 150, d.wallTop, 0.01)
        // Senza taglio nessun avviso.
        assertTrue(com.sagoma.planimetria.geometry.Collisions.of(FloorPlan(listOf(plan.rooms.single().copy(wallCuts = emptyMap())))).isEmpty())
    }

    @Test
    fun `nel 3D il bordo della parete è tagliato in diagonale`() {
        val scene = Scene3D.build(FloorPlan(listOf(attic)), null, null, ceilings = true)
        val high = scene.hit(Vec3(300.0, 1000.0, 0.0), Vec3(0.0, -1.0, 0.0))!!
        val sloped = scene.hit(Vec3(525.0, 1000.0, 0.0), Vec3(0.0, -1.0, 0.0))!!
        assertEquals(Pick.Wall(1, 0), sloped.pick)
        assertEquals(270.0, high.point.y, 0.5)
        assertEquals(235.0, sloped.point.y, 0.5)
    }

    // ---------- Mansarda: i due pallini sono lo stesso inizio di falda ----------

    private fun cutRoom(points: List<Vec2>, s0: Double, s2: Double) = Room(
        2, "Obliqua", RoomType.Altro, points,
        wallHeights = mapOf(1 to 200.0),
        wallCuts = mapOf(0 to WallCut(towardEnd = true, start = s0), 2 to WallCut(towardEnd = false, start = s2)),
    )

    // Muri 0 e 2 obliqui rispetto al muro basso (1, verticale a x = 600): i loro start, a pari distanza, differiscono.
    private val oblique = cutRoom(listOf(Vec2(0.0, 0.0), Vec2(600.0, 100.0), Vec2(600.0, 350.0), Vec2(0.0, 400.0)), 150.0, 150.0)

    private fun distFromLowWall(p: Vec2) = 600.0 - p.x

    @Test
    fun `i due punti di una mansarda dritta sono alla stessa distanza dal muro basso`() {
        val a = Ceilings.startPoint(attic, 0)!!
        val b = Ceilings.startPoint(attic, 2)!!
        assertEquals(distFromLowWall(a), distFromLowWall(b), 1e-9)
        assertEquals(2, Ceilings.partnerWall(attic, 0)?.let { 2 } ?: 0)
        assertEquals(0, Ceilings.partnerWall(attic, 2))
    }

    @Test
    fun `trascinando un punto l'altro si aggiorna alla stessa posizione`() {
        val moved = Ceilings.withCutStart(attic, 0, 100.0)
        assertEquals(100.0, moved.wallCuts[0]!!.start, 1e-9)
        assertEquals(100.0, moved.wallCuts[2]!!.start, 1e-9)
        val back = Ceilings.withCutStart(moved, 2, 220.0)
        assertEquals(220.0, back.wallCuts[0]!!.start, 1e-9)
        assertEquals(220.0, back.wallCuts[2]!!.start, 1e-9)
        assertEquals(distFromLowWall(Ceilings.startPoint(back, 0)!!), distFromLowWall(Ceilings.startPoint(back, 2)!!), 1e-9)
    }

    @Test
    fun `la linea di inizio falda passa esattamente dai due punti`() {
        for (room in listOf(attic, Ceilings.withCutStart(oblique, 0, 150.0), Ceilings.withCutStart(oblique, 2, 90.0))) {
            val lines = Ceilings.slopeStartLines(room)
            assertEquals(1, lines.size)
            val (p, q) = lines.single()
            val pts = listOf(Ceilings.startPoint(room, 0)!!, Ceilings.startPoint(room, 2)!!)
            assertEquals(setOf(pts[0], pts[1]).size, setOf(p, q).size)
            for (pt in pts) assertTrue(Polygon.distanceToSegment(pt, p, q) < 1e-6)
            assertTrue(p.distanceTo(pts[0]) < 1e-6 || p.distanceTo(pts[1]) < 1e-6)
        }
    }

    @Test
    fun `con muri obliqui i punti restano sulla stessa distanza anche se gli start differiscono`() {
        val r = Ceilings.withCutStart(oblique, 0, 150.0)
        val s0 = r.wallCuts[0]!!.start
        val s2 = r.wallCuts[2]!!.start
        assertTrue(Math.abs(s0 - s2) > 0.5) // start diversi...
        val a = Ceilings.startPoint(r, 0)!!
        val b = Ceilings.startPoint(r, 2)!!
        assertEquals(distFromLowWall(a), distFromLowWall(b), 1e-6) // ...stessa posizione logica
        // La linea è parallela al muro basso: nessuna diagonale.
        val (p, q) = Ceilings.slopeStartLines(r).single()
        assertEquals(p.x, q.x, 1e-6)
        assertEquals(distFromLowWall(a), distFromLowWall(p), 1e-6)
    }

    @Test
    fun `la falda e un solo piano senza triangoli o diagonali aggiuntivi`() {
        for (room in listOf(attic, Ceilings.withCutStart(oblique, 0, 150.0), Ceilings.withCutStart(oblique, 2, 60.0))) {
            val a = Ceilings.startPoint(room, 0)!!
            val d = distFromLowWall(a)
            // L'altezza dipende solo dalla distanza dal muro basso: stessa x, y diverse, stessa altezza.
            for (x in listOf(300.0, 400.0, 500.0, 590.0)) {
                val expected = if (600.0 - x >= d) 270.0 else 200.0 + 70.0 * (600.0 - x) / d
                val h = listOf(150.0, 200.0, 250.0).map { Ceilings.heightAt(room, Vec2(x, it)) }
                for (v in h) assertEquals(expected, v, 1e-6)
            }
            // Il bordo delle pareti coincide col soffitto: nessun muro buca la falda.
            // (Solo con muri laterali perpendicolari: la fascia davanti al muro basso e quella di prima, con muri obliqui
            // gli angoli fuori dalla fascia restano a soffitto piano.)
            if (room == attic) for (w in listOf(0, 2)) for (t in listOf(0.0, 100.0, 300.0, 450.0, 590.0)) {
                val a0 = room.wallStart(w)
                val b0 = room.wallEnd(w)
                val pt = a0 + (b0 - a0).normalized() * t
                assertEquals(Ceilings.wallTopAt(room, w, t), Ceilings.heightAt(room, pt), 1e-6)
            }
            for (w in listOf(0, 2)) assertEquals(270.0, Ceilings.heightAt(room, Ceilings.startPoint(room, w)!!), 1e-6)
            val tris = Ceilings.triangles(room)
            if (room == attic) for ((p, q, r) in tris) for (v in listOf(p, q, r)) assertEquals(Ceilings.heightAt(room, v.flat), v.y, 1e-6)
            val area = tris.sumOf { (p, q, r) -> Polygon.area(listOf(p.flat, q.flat, r.flat)) }
            assertEquals(Polygon.area(room.points), area, 1.0)
        }
    }

    @Test
    fun `la mansarda a un solo taglio resta come prima`() {
        val single = attic.copy(wallCuts = mapOf(0 to WallCut(towardEnd = true, start = 150.0)))
        assertNull(Ceilings.partnerWall(single, 0))
        val moved = Ceilings.withCutStart(single, 0, 100.0)
        assertEquals(setOf(0), moved.wallCuts.keys)
        assertEquals(100.0, moved.wallCuts[0]!!.start, 1e-9)
        assertSame(single, Ceilings.alignedToPartner(single, 0))
        assertEquals(1, Ceilings.slopeStartLines(single).size)
        val (p, q) = Ceilings.slopeStartLines(single).single()
        assertEquals(450.0, p.x, 1e-9)
        assertEquals(450.0, q.x, 1e-9)
        assertEquals(270.0, Ceilings.heightAt(single, Vec2(450.0, 200.0)), 1e-9)
        assertEquals(235.0, Ceilings.heightAt(single, Vec2(525.0, 200.0)), 1e-9)
        assertEquals(1, Ceilings.slopeStartLines(single).size)
    }

    @Test
    fun `se l'altro muro e piu corto il limite vale per entrambi`() {
        val short = cutRoom(listOf(Vec2(0.0, 0.0), Vec2(600.0, 0.0), Vec2(600.0, 400.0), Vec2(100.0, 400.0)), 150.0, 150.0)
        val r = Ceilings.withCutStart(short, 0, 9999.0)
        assertEquals(500.0, r.wallCuts[0]!!.start, 1e-6)
        assertEquals(500.0, r.wallCuts[2]!!.start, 1e-6)
    }

    @Test
    fun `un taglio nuovo prende lo stesso inizio dell'altro lato`() {
        val mismatched = attic.copy(wallCuts = mapOf(
            0 to WallCut(towardEnd = true, start = 100.0), 2 to WallCut(towardEnd = false, start = 200.0),
        ))
        val aligned = Ceilings.alignedToPartner(mismatched, 0)
        assertEquals(200.0, aligned.wallCuts[0]!!.start, 1e-9)
        assertEquals(200.0, aligned.wallCuts[2]!!.start, 1e-9)
    }

    @Test
    fun `file con start diversi sono un solo piano alla distanza media`() {
        val legacy = attic.copy(wallCuts = mapOf(
            0 to WallCut(towardEnd = true, start = 100.0), 2 to WallCut(towardEnd = false, start = 200.0),
        ))
        assertEquals(270.0, Ceilings.heightAt(legacy, Vec2(450.0, 100.0)), 1e-9)
        assertEquals(270.0, Ceilings.heightAt(legacy, Vec2(450.0, 300.0)), 1e-9)
        assertEquals(235.0, Ceilings.heightAt(legacy, Vec2(525.0, 100.0)), 1e-9)
        assertEquals(235.0, Ceilings.heightAt(legacy, Vec2(525.0, 300.0)), 1e-9)
        val (p, q) = Ceilings.slopeStartLines(legacy).single()
        assertEquals(450.0, p.x, 1e-9)
        assertEquals(450.0, q.x, 1e-9)
    }
}
