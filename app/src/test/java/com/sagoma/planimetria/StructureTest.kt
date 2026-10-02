package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Beam
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureTest {

    // Stanza 600 × 400: muro 0 in alto, 1 a destra, 2 in basso, 3 a sinistra (mezzerie).
    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 400.0))
    private val plan = FloorPlan(listOf(room))
    private val down = Vec3(0.0, -1.0, 0.0)

    @Test
    fun `porte e finestre scorrono lungo tutto il perimetro, girando gli angoli`() {
        // Sul muro in alto, verso destra: resta lì.
        assertEquals(0 to 300.0, Openings.alongPerimeter(room, Vec2(300.0, 20.0), 80.0))
        // Oltre l'angolo in alto a destra, scendendo lungo il muro di destra: passa sul muro 1.
        val (j, pos) = Openings.alongPerimeter(room, Vec2(590.0, 150.0), 80.0)!!
        assertEquals(1, j)
        assertEquals(150.0, pos, 1e-9)
        // Vicino all'angolo resta tutta sul muro (non esce dall'estremità).
        val (j2, pos2) = Openings.alongPerimeter(room, Vec2(595.0, 5.0), 80.0)!!
        assertTrue(j2 == 0 || j2 == 1)
        assertTrue(pos2 >= 40.0 && pos2 <= room.wallLength(j2) - 40.0)
        // Un muro troppo corto per l'apertura si salta.
        assertEquals(null, Openings.alongPerimeter(room, Vec2(300.0, 20.0), 700.0))
    }

    @Test
    fun `una colonna vicino a un muro si accosta alla sua faccia e sporge dal muro`() {
        val c = Structure.snapped(plan, Column(1, Vec2(30.0, 200.0)))
        // Faccia interna del muro di sinistra a x = 7,5: la colonna 30 × 30 va da 7,5 a 37,5.
        assertEquals(22.5, c.center.x, 1e-9)
        // In mezzo alla stanza resta dov'è.
        assertEquals(Vec2(300.0, 200.0), Structure.snapped(plan, Column(2, Vec2(300.0, 200.0))).center)
        // Rotonda: il contorno è un cerchio del diametro scelto.
        val r = Column(3, Vec2(300.0, 200.0), ColumnShape.Round, width = 40.0)
        assertEquals(Math.PI * 20 * 20, Polygon.area(Structure.outline(r)), 60.0)
        assertTrue(Structure.contains(r, Vec2(315.0, 200.0)))
    }

    @Test
    fun `la trave proposta attraversa la stanza nel verso corto, da faccia a faccia`() {
        val b = Structure.defaultBeam(1, room, Vec2(300.0, 200.0), plan)
        assertEquals(400.0 - 15.0, b.length, 1e-6)
        assertEquals(7.5, minOf(b.start.y, b.end.y), 1e-6)
        // Un'estremità trascinata vicino a un muro si appoggia alla sua faccia, lungo la trave.
        assertEquals(Vec2(300.0, 392.5), Structure.snapEnd(plan, Vec2(300.0, 380.0), Vec2(300.0, 7.5)))
        assertEquals(Vec2(300.0, 300.0), Structure.snapEnd(plan, Vec2(300.0, 300.0), Vec2(300.0, 7.5)))
    }

    @Test
    fun `in 3D la colonna arriva al soffitto, la trave sporge sotto di 40 cm, si toccano e bloccano chi cammina`() {
        val p = plan.copy(
            columns = listOf(Column(1, Vec2(150.0, 200.0)), Column(2, Vec2(300.0, 200.0), ColumnShape.Round)),
            beams = listOf(Beam(1, Vec2(450.0, 7.5), Vec2(450.0, 392.5))),
        )
        val scene = Scene3D.build(p, null, null, ceilings = false)
        val col = scene.hit(Vec3(150.0, 1000.0, 200.0), down)!!
        assertEquals(Pick.Column(1), col.pick)
        assertEquals(270.0, col.point.y, 0.5)
        assertEquals(Pick.Column(2), scene.hit(Vec3(300.0, 1000.0, 200.0), down)!!.pick)
        val beam = scene.hit(Vec3(450.0, 1000.0, 200.0), down)!!
        assertEquals(Pick.Beam(1), beam.pick)
        assertEquals(270.0, beam.point.y, 0.5)
        // Da sotto si vede la trave a 230 cm.
        assertEquals(230.0, scene.hit(Vec3(450.0, 10.0, 200.0), Vec3(0.0, 1.0, 0.0))!!.point.y, 0.5)
        assertTrue(scene.blocked(Vec2(150.0, 200.0), 10.0))
        assertTrue(!scene.blocked(Vec2(450.0, 200.0), 10.0)) // sotto la trave si passa
    }

    @Test
    fun `un pilastro con il centro nel muro arriva al soffitto della stanza, anche con un interpiano basso`() {
        // Interpiano 270: fuori dalle stanze varrebbe 270 − 30 = 240, ma il pilastro è sul muro del soggiorno (270).
        val onWall = Column(1, Vec2(3.0, 200.0), width = 40.0)
        assertEquals(270.0, Structure.ceilingAt(plan, onWall.center, 270.0), 1e-9)
        assertEquals(270.0, Structure.columnHeight(plan, onWall, 270.0), 1e-9)
        // Appena fuori dal muro, dalla parte esterna: ancora il soffitto della stanza.
        assertEquals(270.0, Structure.ceilingAt(plan, Vec2(-20.0, 200.0), 270.0), 1e-9)
        // Lontano da tutto (all'aperto, sotto una terrazza): fino al solaio di sopra, interpiano meno solaio…
        assertEquals(300.0, Structure.ceilingAt(plan, Vec2(-500.0, 200.0), 330.0), 1e-9)
        // …ma mai sotto il soffitto delle stanze, anche se l'interpiano è scritto uguale al soffitto.
        assertEquals(270.0, Structure.ceilingAt(plan, Vec2(-500.0, 200.0), 270.0), 1e-9)
    }

    @Test
    fun `colonna più bassa del soffitto, ma mai oltre`() {
        val low = Column(1, Vec2(300.0, 200.0), height = 120.0)
        assertEquals(120.0, Structure.columnHeight(plan, low, 300.0), 1e-9)
        assertEquals(270.0, Structure.columnHeight(plan, low.copy(height = 500.0), 300.0), 1e-9)
        assertEquals(270.0, Structure.columnHeight(plan, low.copy(height = null), 300.0), 1e-9)
        val scene = Scene3D.build(plan.copy(columns = listOf(low)), null, null, ceilings = false)
        assertEquals(120.0, scene.hit(Vec3(300.0, 1000.0, 200.0), down)!!.point.y, 0.5)
    }

    @Test
    fun `le travi si agganciano a colonne, ad altre travi e ai loro assi`() {
        val main = Beam(1, Vec2(300.0, 7.5), Vec2(300.0, 392.5))
        val p = plan.copy(beams = listOf(main), columns = listOf(Column(1, Vec2(150.0, 200.0))))
        // Estremità trascinata vicino al centro della colonna: ci va sopra.
        assertEquals(Vec2(150.0, 200.0), Structure.snapBeamEnd(p, Vec2(160.0, 195.0), Vec2(500.0, 200.0), 2))
        // Vicino all'estremità di un'altra trave: si uniscono.
        assertEquals(Vec2(300.0, 7.5), Structure.snapBeamEnd(p, Vec2(290.0, 15.0), Vec2(100.0, 15.0), 2))
        // Vicino all'asse di un'altra trave (a metà): innesto a T, restando dritta.
        assertEquals(Vec2(300.0, 250.0), Structure.snapBeamEnd(p, Vec2(290.0, 250.0), Vec2(500.0, 250.0), 2))
        // Trave parallela spostata a 10 cm dall'asse di quella esistente: si allinea.
        val moved = Structure.snapped(p, Beam(2, Vec2(310.0, 100.0), Vec2(310.0, 200.0)))
        assertEquals(300.0, moved.start.x, 1e-9)
        // Trave che passa a 8 cm dal centro della colonna: in asse con il pilastro.
        val onColumn = Structure.snapped(p, Beam(3, Vec2(50.0, 208.0), Vec2(120.0, 208.0)))
        assertEquals(200.0, onColumn.start.y, 1e-9)
    }

    @Test
    fun `le colonne si allineano tra loro e alle estremità delle travi`() {
        val p = plan.copy(columns = listOf(Column(1, Vec2(150.0, 200.0))), beams = listOf(Beam(1, Vec2(450.0, 100.0), Vec2(450.0, 300.0))))
        assertEquals(Vec2(300.0, 200.0), Structure.snapped(p, Column(2, Vec2(300.0, 208.0))).center)
        assertEquals(Vec2(450.0, 300.0), Structure.snapped(p, Column(3, Vec2(455.0, 295.0))).center)
    }

    @Test
    fun `trave spostata vicino a un muro parallelo ci si accosta, e le estremità restano sui muri`() {
        // Trave verticale (da muro in alto a muro in basso) portata a 10 cm dal muro di sinistra.
        val b = Beam(1, Vec2(35.0, 7.5), Vec2(35.0, 392.5))
        val s = Structure.snapped(plan, b)
        assertEquals(7.5 + 15.0, s.start.x, 1e-9) // fianco contro la faccia interna (x = 7,5)
        assertEquals(7.5, s.start.y, 1e-9)
        assertEquals(392.5, s.end.y, 1e-9)
    }

    @Test
    fun `pareti interne con il colore della stanza, esterne con il colore della facciata`() {
        val painted = room.copy(wallPaint = com.sagoma.planimetria.model.WallPaint.Sage)
        val p = FloorPlan(listOf(painted), facade = com.sagoma.planimetria.model.WallPaint.Brick)
        val scene = Scene3D.build(p, null, null, ceilings = false)
        // Colori dei vertici: 10 float per vertice, il colore dal 7° al 9°.
        fun colorAt(x: Double, z: Double): Triple<Float, Float, Float>? {
            val d = scene.opaque
            var k = 0
            while (k < d.size) {
                val xs = listOf(d[k], d[k + 10], d[k + 20]).map { it.toDouble() }
                val zs = listOf(d[k + 2], d[k + 12], d[k + 22]).map { it.toDouble() }
                val nx = d[k + 3]; val nz = d[k + 5]
                // Faccia verticale piatta proprio su quella x (normale lungo x), che copre la z cercata.
                if (xs.all { kotlin.math.abs(it - x) < 0.01 } && kotlin.math.abs(nx) > 0.9 && kotlin.math.abs(nz) < 0.1 &&
                    z in zs.min()..zs.max()) return Triple(d[k + 6], d[k + 7], d[k + 8])
                k += 30
            }
            return null
        }
        val sage = com.sagoma.planimetria.geometry.Rgba.argb(com.sagoma.planimetria.model.WallPaint.Sage.argb)
        val brick = com.sagoma.planimetria.geometry.Rgba.argb(com.sagoma.planimetria.model.WallPaint.Brick.argb)
        // Muro di sinistra (x = 0): faccia interna a x = 7,5, esterna a x = -7,5.
        assertEquals(Triple(sage.r, sage.g, sage.b), colorAt(7.5, 200.0))
        assertEquals(Triple(brick.r, brick.g, brick.b), colorAt(-7.5, 200.0))
        // Una sola parete di un altro colore: il muro di destra azzurro, quello di sinistra resta salvia.
        val sky = com.sagoma.planimetria.geometry.Rgba.argb(com.sagoma.planimetria.model.WallPaint.Sky.argb)
        val one = FloorPlan(listOf(painted.copy(wallPaints = mapOf(1 to com.sagoma.planimetria.model.WallPaint.Sky))), facade = com.sagoma.planimetria.model.WallPaint.Brick)
        val scene2 = Scene3D.build(one, null, null, ceilings = false)
        fun colorAt2(x: Double, z: Double): Triple<Float, Float, Float>? {
            val d = scene2.opaque
            var k = 0
            while (k < d.size) {
                val xs = listOf(d[k], d[k + 10], d[k + 20]).map { it.toDouble() }
                val zs = listOf(d[k + 2], d[k + 12], d[k + 22]).map { it.toDouble() }
                if (xs.all { kotlin.math.abs(it - x) < 0.01 } && kotlin.math.abs(d[k + 3]) > 0.9 && z in zs.min()..zs.max()) return Triple(d[k + 6], d[k + 7], d[k + 8])
                k += 30
            }
            return null
        }
        assertEquals(Triple(sky.r, sky.g, sky.b), colorAt2(592.5, 200.0))
        assertEquals(Triple(sage.r, sage.g, sage.b), colorAt2(7.5, 200.0))
        assertEquals(Triple(brick.r, brick.g, brick.b), colorAt2(607.5, 200.0))
        assertEquals(one, com.sagoma.planimetria.persistence.PlanJson.decode(com.sagoma.planimetria.persistence.PlanJson.encode(one)))
    }

    @Test
    fun `colonne e travi si salvano e si rileggono`() {
        val p = plan.copy(
            columns = listOf(Column(1, Vec2(10.0, 20.0), ColumnShape.Round, width = 45.0, rotation = 30.0)),
            beams = listOf(Beam(1, Vec2(0.0, 0.0), Vec2(100.0, 0.0), width = 25.0, depth = 50.0)),
        )
        assertEquals(p, PlanJson.decode(PlanJson.encode(p)))
    }
}
