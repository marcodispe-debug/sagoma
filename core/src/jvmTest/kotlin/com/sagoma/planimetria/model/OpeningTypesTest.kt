package com.sagoma.planimetria.model

import com.sagoma.planimetria.editor.OpeningPreset
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tipi nuovi di porte, finestre e varchi: archi ribassato e a sesto acuto, nicchia, porte scorrevole esterna, a libro,
 * filo muro e a due ante disuguali, finestre anta-ribalta, vasistas, fisse e vetrata loft.
 * Stanza 400 × 300 con i muri da 15 cm: il muro 0 va da (0, 0) a (400, 0) e la stanza sta dalla parte delle y positive.
 */
class OpeningTypesTest {
    private fun room(vararg openings: Opening) = Room(
        1, "Prova", RoomType.Altro,
        listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)),
        openings = openings.toList(),
    )

    private fun scene(vararg openings: Opening) = Scene3D.build(FloorPlan(listOf(room(*openings))), null, null, ceilings = false)

    /** Raggio dalla stanza verso il muro 0, nel punto x e all'altezza y. */
    private fun fromInside(s: Scene3D, x: Double, y: Double) = s.hit(Vec3(x, y, 150.0), Vec3(0.0, 0.0, -1.0))

    private fun passage(style: PassageStyle) =
        Opening.default(1, OpeningKind.Passage, 0, 200.0).copy(width = 100.0, height = 210.0, style = style)

    @Test
    fun `archi - il sesto acuto scende prima ai lati, il ribassato resta alto`() {
        // A 25 cm dal centro: tondo 203 cm, sesto acuto 189 cm, ribassato 206 cm.
        assertNull(fromInside(scene(passage(PassageStyle.Arched)), 225.0, 200.0), "nell'arco tondo a 200 cm si passa")
        assertNotNull(fromInside(scene(passage(PassageStyle.Pointed)), 225.0, 200.0), "nel sesto acuto a 200 cm c'è già muro")
        assertNull(fromInside(scene(passage(PassageStyle.Segmental)), 225.0, 200.0), "nel ribassato a 200 cm si passa")
        // A 45 cm dal centro, a 195 cm: il tondo è già sceso (182 cm), il ribassato no (196 cm).
        assertNotNull(fromInside(scene(passage(PassageStyle.Arched)), 245.0, 195.0))
        assertNull(fromInside(scene(passage(PassageStyle.Segmental)), 245.0, 195.0))
        // Al centro tutti arrivano all'altezza del varco.
        for (st in PassageStyle.entries) assertNull(fromInside(scene(passage(st)), 200.0, 205.0), "$st al centro")
    }

    @Test
    fun `nicchia - scava mezzo muro verso la stanza e non passa dall'altra parte`() {
        val niche = Opening.default(1, OpeningKind.Niche, 0, 200.0) // 60 × 80, da 100 cm
        val s = scene(niche)
        val inside = assertNotNull(fromInside(s, 200.0, 140.0))
        assertTrue(abs(inside.point.z) < 0.5, "il fondo della nicchia sta alla mezzeria del muro: z = ${inside.point.z}")
        assertEquals(Pick.Opening(1, 1), inside.pick)
        // Fuori dalla nicchia il raggio si ferma sulla faccia del muro.
        assertTrue(assertNotNull(fromInside(s, 200.0, 60.0)).point.z > 7.0)
        // Da fuori il muro è intero.
        val outside = assertNotNull(s.hit(Vec3(200.0, 140.0, -100.0), Vec3(0.0, 0.0, 1.0)))
        assertTrue(abs(outside.point.z + 7.5) < 0.5, "da fuori si vede la facciata: z = ${outside.point.z}")
    }

    @Test
    fun `nicchia - non è una porta nel computo e non buca la parete`() {
        val plain = Takeoff.of(Building.single(FloorPlan(listOf(room())))).rooms.single()
        val withNiche = Takeoff.of(Building.single(FloorPlan(listOf(room(Opening.default(1, OpeningKind.Niche, 0, 200.0)))))).rooms.single()
        assertEquals(0, withNiche.doors)
        assertEquals(0, withNiche.windows)
        assertEquals(plain.wallArea, withNiche.wallArea, 1e-9)
    }

    @Test
    fun `ante disuguali - l'anta grande sta dal lato scelto`() {
        val door = OpeningPreset.UnequalLeaves.apply(Opening.default(1, OpeningKind.Door2, 0, 200.0))
        assertTrue(door.unequal)
        assertEquals(2.0 / 3, door.leafSplit, 1e-9)
        assertEquals(1.0 / 3, door.copy(hingeLeft = false).leafSplit, 1e-9)
        assertTrue(door.hasSideChoice)
        // Scorrevole o a libro le ante tornano uguali.
        assertEquals(0.5, door.copy(sliding = true).leafSplit, 1e-9)
        assertEquals(0.5, Opening.default(1, OpeningKind.Door2, 0, 200.0).leafSplit, 1e-9)
    }

    @Test
    fun `finestre - fissa e vasistas non hanno arco, anta-ribalta sì`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0)
        val fixed = OpeningPreset.Fixed.apply(w)
        val hopper = OpeningPreset.Hopper.apply(w)
        val tilt = OpeningPreset.TiltTurn.apply(w)
        assertTrue(fixed.fixed && !fixed.swings && !fixed.hasSideChoice)
        assertTrue(hopper.hopper && !hopper.swings && !hopper.hasSideChoice)
        assertTrue(tilt.swings && tilt.hasSideChoice)
        assertEquals(150.0, hopper.sillHeight)
        // Da scorrevole il tipo di anta non conta.
        assertFalse(fixed.copy(sliding = true).fixed)
        assertTrue(fixed.copy(sliding = true).hasSideChoice)
    }

    @Test
    fun `vetrata interna - fissa, a riquadri, e sul muro in comune buca anche la stanza accanto`() {
        val glazing = OpeningPreset.InteriorGlazing.apply(Opening.default(1, OpeningKind.Window2, 0, 200.0))
        assertTrue(glazing.fixed)
        assertEquals(WindowModel.Loft, glazing.windowModel)
        val other = Room(2, "Accanto", RoomType.Altro, listOf(Vec2(0.0, -300.0), Vec2(400.0, -300.0), Vec2(400.0, 0.0), Vec2(0.0, 0.0)))
        val s = Scene3D.build(FloorPlan(listOf(room(glazing), other)), null, null, ceilings = false)
        // All'altezza della vetrata il raggio dalla stanza accanto arriva al vetro o ai profili, non al muro.
        val hit = assertNotNull(s.hit(Vec3(200.0, 150.0, -150.0), Vec3(0.0, 0.0, 1.0)))
        assertEquals(Pick.Opening(1, 1), hit.pick)
    }

    @Test
    fun `porte - scorrevole esterna, a libro e filo muro`() {
        val d = Opening.default(1, OpeningKind.Door, 0, 200.0)
        val slide = OpeningPreset.WallSlide.apply(d)
        assertTrue(slide.sliding && slide.slideOnWall && !slide.swings && slide.hasSideChoice)
        val fold = OpeningPreset.Folding.apply(d)
        assertTrue(fold.folds && fold.swings)
        val flush = OpeningPreset.Frameless.apply(d)
        assertTrue(flush.frameless && flush.swings)
        // Nel 3D l'anta esterna sta davanti alla faccia del muro, verso la stanza.
        val s = scene(slide)
        val hit = assertNotNull(fromInside(s, if (slide.hingeLeft) 170.0 else 230.0, 100.0))
        assertEquals(Pick.Opening(1, 1), hit.pick)
        assertTrue(hit.point.z > 9.0, "anta sulla faccia del muro: z = ${hit.point.z}")
        // Filo muro: niente coprifilo che sporge dalla faccia accanto al varco.
        val trim = fromInside(scene(d.copy(opensInward = false)), 156.0, 100.0)
        val none = fromInside(scene(flush.copy(opensInward = false)), 156.0, 100.0)
        assertTrue(assertNotNull(trim).point.z > assertNotNull(none).point.z + 0.3, "coprifilo ${trim.point.z}, filo muro ${none.point.z}")
    }

    @Test
    fun `porta a bilico - aperta sporge anche dall'altra parte del muro`() {
        val door = OpeningPreset.Pivot.apply(Opening.default(1, OpeningKind.Door, 0, 200.0)) // 100 cm: da 150 a 250, perno a 170
        assertTrue(door.pivots && door.swings && door.hasSideChoice)
        val hit = assertNotNull(scene(door).hit(Vec3(170.0, 100.0, -100.0), Vec3(0.0, 0.0, 1.0)))
        assertEquals(Pick.Opening(1, 1), hit.pick)
        assertTrue(hit.point.z < -15.0, "l'anta esce di 20 cm fuori: z = ${hit.point.z}")
    }

    @Test
    fun `porta con sopraluce e vetro di fianco - vetri fissi, l'anta resta 80 × 210`() {
        val door = OpeningPreset.Glazed.apply(Opening.default(1, OpeningKind.Door, 0, 200.0)) // 110 × 250: da 145 a 255
        assertEquals(30.0, door.sidelightCm)
        assertEquals(40.0, door.transomCm)
        val s = scene(door)
        // Vetro di fianco, dal lato opposto al cardine (cardine a sinistra: vetro da 225 a 255).
        assertEquals(Pick.Opening(1, 1), assertNotNull(fromInside(s, 240.0, 100.0)).pick)
        // Sopraluce sopra l'anta.
        assertEquals(Pick.Opening(1, 1), assertNotNull(fromInside(s, 185.0, 230.0)).pick)
        // Senza vetri, nello stesso punto si passa.
        assertNull(fromInside(scene(door.copy(transom = 0.0, sidelight = 0.0)), 240.0, 100.0))
        // Scorrevole o a libro i vetri non valgono.
        assertEquals(0.0, door.copy(sliding = true).sidelightCm)
    }

    @Test
    fun `finestre e balconi a 3 e 4 ante, alzante e bilico`() {
        val four = OpeningPreset.FourLeaves.apply(Opening.default(1, OpeningKind.Balcony2, 0, 200.0)) // 240: da 80 a 320
        assertEquals(4, four.leaves)
        assertFalse(four.copy(unequalLeaves = true).unequal, "con 4 ante niente ante disuguali")
        // La finestra a un'anta resta a un'anta anche con leafCount.
        assertEquals(1, Opening.default(1, OpeningKind.Window1, 0, 200.0).copy(leafCount = 4).leaves)
        // Scorrevole a 4 ante: ante sfalsate una sì e una no nello spessore.
        val s = scene(four.copy(sliding = true))
        val first = assertNotNull(fromInside(s, 110.0, 120.0)).point.z
        val second = assertNotNull(fromInside(s, 170.0, 120.0)).point.z
        assertTrue(abs(first - second) > 4.0, "ante sfalsate: $first e $second")
        val lift = OpeningPreset.LiftSlide.apply(Opening.default(1, OpeningKind.Balcony2, 0, 200.0))
        assertTrue(lift.lifts && lift.sliding)
        val pivot = OpeningPreset.PivotSash.apply(Opening.default(1, OpeningKind.Window1, 0, 200.0))
        assertTrue(pivot.pivotSash && !pivot.swings && !pivot.hasSideChoice)
    }

    @Test
    fun `i dati nuovi si salvano e si rileggono`() {
        val openings = listOf(
            OpeningPreset.WallSlide.apply(Opening.default(1, OpeningKind.Door, 0, 100.0)),
            OpeningPreset.Folding.apply(Opening.default(2, OpeningKind.Door, 1, 150.0)),
            OpeningPreset.UnequalLeaves.apply(Opening.default(3, OpeningKind.Door2, 2, 200.0)).copy(hingeLeft = false),
            OpeningPreset.Frameless.apply(Opening.default(4, OpeningKind.Door, 3, 100.0)),
            OpeningPreset.Hopper.apply(Opening.default(5, OpeningKind.Window1, 0, 300.0)),
            OpeningPreset.InteriorGlazing.apply(Opening.default(6, OpeningKind.Window2, 2, 80.0)),
            Opening.default(7, OpeningKind.Niche, 1, 60.0),
            passage(PassageStyle.Pointed).copy(id = 8, wallIndex = 3, position = 220.0),
            passage(PassageStyle.Segmental).copy(id = 9, wallIndex = 1, position = 240.0),
            OpeningPreset.Pivot.apply(Opening.default(10, OpeningKind.Door, 0, 120.0)),
            OpeningPreset.Armored.apply(Opening.default(11, OpeningKind.Door, 2, 300.0)),
            OpeningPreset.Glazed.apply(Opening.default(12, OpeningKind.Door, 3, 150.0)),
            OpeningPreset.FourLeaves.apply(Opening.default(13, OpeningKind.Balcony2, 0, 200.0)),
            OpeningPreset.LiftSlide.apply(Opening.default(14, OpeningKind.Balcony2, 2, 200.0)),
            OpeningPreset.PivotSash.apply(Opening.default(15, OpeningKind.Window1, 1, 200.0)),
        )
        val plan = FloorPlan(listOf(room(*openings.toTypedArray())))
        assertEquals(openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }
}
