package com.sagoma.planimetria.model

import com.sagoma.planimetria.editor.OpeningPreset
import com.sagoma.planimetria.geometry.ExteriorChains
import com.sagoma.planimetria.geometry.Openings
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
    fun `scuri interni dentro, veneziana fuori`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0) // 80: da 160 a 240, da 90 a 210
        val inside = assertNotNull(fromInside(scene(w.copy(shading = Shading.InteriorShutters)), 150.0, 150.0))
        assertEquals(Pick.Opening(1, 1), inside.pick)
        assertTrue(inside.point.z > 8.0, "scuro sulla faccia interna: z = ${inside.point.z}")
        val out = assertNotNull(scene(w.copy(shading = Shading.Venetian)).hit(Vec3(200.0, 180.0, -100.0), Vec3(0.0, 0.0, 1.0)))
        assertEquals(Pick.Opening(1, 1), out.pick)
        assertTrue(out.point.z < -8.0, "veneziana fuori: z = ${out.point.z}")
    }

    @Test
    fun `oblò e finestra ad arco - il muro segue il bordo curvo`() {
        val round = OpeningPreset.RoundWindow.apply(Opening.default(1, OpeningKind.Window1, 0, 200.0)) // Ø 60 da 130: centro a 160
        val s = scene(round)
        assertEquals(Pick.Opening(1, 1), assertNotNull(fromInside(s, 200.0, 160.0)).pick)
        // Nell'angolo del quadrato attorno al cerchio c'è muro (la faccia a 7,5 cm), sopra e sotto; il vetro sta a metà muro.
        fun z(sc: Scene3D, x: Double, y: Double) = assertNotNull(fromInside(sc, x, y)).point.z
        assertTrue(z(s, 174.0, 134.0) > 7.0)
        assertTrue(z(s, 174.0, 186.0) > 7.0)
        assertTrue(z(s, 200.0, 160.0) < 5.0)
        assertTrue(z(scene(round.copy(windowShape = WindowShape.Rectangle)), 174.0, 134.0) < 5.0)
        // Ad arco: 80 × 160 da 90, arco alto 40 cm; vicino allo spigolo in alto c'è muro.
        val arched = OpeningPreset.ArchedWindow.apply(Opening.default(1, OpeningKind.Window1, 0, 200.0))
        assertTrue(z(scene(arched), 163.0, 240.0) > 7.0)
        assertTrue(z(scene(arched), 200.0, 240.0) < 5.0)
        // Un balcone non può essere tondo; da scorrevole torna rettangolare.
        assertEquals(WindowShape.Rectangle, Opening.default(1, OpeningKind.Balcony1, 0, 200.0).copy(windowShape = WindowShape.Round).shape)
        assertEquals(WindowShape.Rectangle, arched.copy(sliding = true).shape)
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
            OpeningPreset.PivotSash.apply(Opening.default(15, OpeningKind.Window1, 1, 200.0)).copy(shading = Shading.Venetian),
            Opening.default(16, OpeningKind.Window2, 3, 200.0).copy(shading = Shading.InteriorShutters),
            OpeningPreset.RoundWindow.apply(Opening.default(17, OpeningKind.Window1, 0, 330.0)),
            OpeningPreset.ArchedWindow.apply(Opening.default(18, OpeningKind.Window2, 2, 100.0)),
        )
        val plan = FloorPlan(listOf(room(*openings.toTypedArray())))
        assertEquals(openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }

    @Test
    fun `colore degli oscuranti - il solito finche non si sceglie, poi uno qualsiasi, e si salva`() {
        val w = Opening.default(1, OpeningKind.Window2, 0, 200.0)
        assertEquals(Opening.SHUTTER_GREEN, w.copy(shading = Shading.Shutters).shadingArgb, "persiane verdi con l'infisso bianco")
        assertEquals(Finish.Walnut.argb, w.copy(shading = Shading.Shutters, color = Finish.Walnut).shadingArgb)
        assertEquals(Opening.VENETIAN_GREY, w.copy(shading = Shading.Venetian).shadingArgb)
        val brown = w.copy(shading = Shading.Venetian, shadingColor = 0xFF6B4A33)
        assertEquals(0xFF6B4A33, brown.shadingArgb)
        assertEquals(0xFF6B4A33, PlanJson.decode(PlanJson.encode(FloorPlan(listOf(room(brown))))).rooms[0].openings[0].shadingColor)
    }

    @Test
    fun `colore infisso qualsiasi - vince sul colore della lista, le persiane lo seguono, e si salva`() {
        val w = Opening.default(1, OpeningKind.Window2, 0, 200.0)
        assertTrue(w.defaultColor)
        assertEquals(Finish.White.argb, w.frameArgb)
        val red = w.copy(customColor = 0xFF8A2C2C, shading = Shading.Shutters)
        assertFalse(red.defaultColor)
        assertEquals(0xFF8A2C2C, red.frameArgb)
        assertEquals(0xFF8A2C2C, red.shadingArgb, "persiane del colore dell'infisso, come con i colori della lista")
        assertEquals(0xFF8A2C2C, PlanJson.decode(PlanJson.encode(FloorPlan(listOf(room(red))))).rooms[0].openings[0].customColor)
        // Minimal: senza colore scelto antracite, con il colore scelto quello.
        val minimal = w.copy(windowModel = WindowModel.Minimal)
        val plain = Scene3D.build(FloorPlan(listOf(room(minimal))), null, null, ceilings = false)
        val tinted = Scene3D.build(FloorPlan(listOf(room(minimal.copy(customColor = 0xFF8A2C2C)))), null, null, ceilings = false)
        assertTrue(plain.opaque != tinted.opaque)
    }

    /** Raggio da fuori (y negative) verso il muro 0, nel punto x e all'altezza y. */
    private fun fromOutside(s: Scene3D, x: Double, y: Double) = s.hit(Vec3(x, y, -200.0), Vec3(0.0, 0.0, 1.0))

    @Test
    fun `inferriata - sbarre appena fuori dal muro, solo su finestre e balconi`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0) // 80 × 120: da 160 a 240, da 90 a 210
        val grille = OpeningPreset.Grille.apply(w)
        assertTrue(grille.hasGrille)
        // Sul piatto in basso (a 100 cm) il raggio da fuori si ferma prima della faccia esterna (−7,5).
        val bar = assertNotNull(fromOutside(scene(grille), 200.0, 100.0))
        assertEquals(Pick.Opening(1, 1), bar.pick)
        assertTrue(bar.point.z < -10.0, "inferriata fuori dal muro: z = ${bar.point.z}")
        assertTrue(assertNotNull(fromOutside(scene(w), 200.0, 100.0)).point.z > -8.0)
        // Sulle porte e sul bovindo non c'è.
        assertFalse(Opening.default(1, OpeningKind.Door, 0, 200.0).copy(grille = true).hasGrille)
        assertFalse(OpeningPreset.BayWindow.apply(grille).hasGrille)
        assertTrue(Opening.default(1, OpeningKind.Balcony1, 0, 200.0).copy(grille = true).hasGrille)
    }

    @Test
    fun `sopraluce - il traverso divide le ante dal vetro fisso in alto`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0).copy(height = 160.0) // da 90 a 250
        val top = OpeningPreset.TopLight.apply(w)
        assertTrue(top.hasTopLight)
        assertEquals(160.0, top.height)
        // Traverso a 203 cm (vetro da 95 a 245, sopraluce alto il 28%): il profilo sporge verso la stanza, il vetro no.
        val transom = assertNotNull(fromInside(scene(top), 200.0, 203.0))
        assertEquals(Pick.Opening(1, 1), transom.pick)
        assertTrue(transom.point.z > 3.0, "traverso: z = ${transom.point.z}")
        assertTrue(assertNotNull(fromInside(scene(w), 200.0, 203.0)).point.z < 1.0)
        // Scorrevole, fissa o tonda: niente sopraluce.
        assertFalse(top.copy(sliding = true).hasTopLight)
        assertFalse(top.copy(sash = SashType.Fixed).hasTopLight)
        assertFalse(top.copy(windowShape = WindowShape.Round).hasTopLight)
    }

    @Test
    fun `legno con fermavetro - profili più larghi, rovere se non si sceglie il colore`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0) // da 160 a 240
        val wood = OpeningPreset.WoodWindow.apply(w)
        assertEquals(WindowModel.Wood, wood.windowModel)
        // A 6 cm dal bordo il legno (7 cm) è ancora telaio, il classico (5 cm) è già vetro.
        assertTrue(assertNotNull(fromInside(scene(wood), 166.0, 150.0)).point.z > 4.5)
        assertTrue(assertNotNull(fromInside(scene(w), 166.0, 150.0)).point.z < 1.0)
        // Subito dentro il telaio c'è il fermavetro, tra il vetro e la faccia del telaio.
        val bead = assertNotNull(fromInside(scene(wood), 167.8, 150.0)).point.z
        assertTrue(bead > 2.0 && bead < 4.5, "fermavetro: z = $bead")
        // Bianco di serie diventa rovere; con il colore scelto resta quello.
        val oak = Scene3D.build(FloorPlan(listOf(room(wood))), null, null, ceilings = false)
        val oakChosen = Scene3D.build(FloorPlan(listOf(room(wood.copy(color = Finish.Oak)))), null, null, ceilings = false)
        val white = Scene3D.build(FloorPlan(listOf(room(wood.copy(color = Finish.White)))), null, null, ceilings = false)
        assertTrue(oak.opaque.contentEquals(oakChosen.opaque))
        assertFalse(oak.opaque.contentEquals(white.opaque))
    }

    @Test
    fun `persiana alla romana - incernierata in alto e aperta in fuori`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0) // da 90 a 210
        val roman = OpeningPreset.RomanShutter.apply(w)
        assertEquals(Shading.RomanShutter, roman.shading)
        assertEquals(Opening.SHUTTER_GREEN, roman.shadingArgb)
        // In alto il pannello sta lontano dal muro; sotto il suo bordo (a circa 114 cm) si vede la finestra.
        val panel = assertNotNull(fromOutside(scene(roman), 200.0, 180.0))
        assertEquals(Pick.Opening(1, 1), panel.pick)
        assertTrue(panel.point.z < -15.0, "pannello aperto: z = ${panel.point.z}")
        assertTrue(assertNotNull(fromOutside(scene(roman), 200.0, 100.0)).point.z > -8.0)
        assertTrue(assertNotNull(fromOutside(scene(w), 200.0, 180.0)).point.z > -8.0)
    }

    @Test
    fun `bovindo - sporge fuori su tre lati con base e tettuccio`() {
        val bay = OpeningPreset.BayWindow.apply(Opening.default(1, OpeningKind.Window2, 0, 200.0)) // 180 × 140 da 50: da 110 a 290
        assertTrue(bay.isBay)
        val outline = Openings.bayOutline(room(bay), bay)
        val expected = listOf(Vec2(110.0, -7.5), Vec2(170.0, -67.5), Vec2(230.0, -67.5), Vec2(290.0, -7.5))
        for ((p, e) in outline.zip(expected)) assertTrue(p.distanceTo(e) < 1e-6, "$p invece di $e")
        // Stretto: sporge meno, il davanti resta largo 30 cm.
        assertEquals(25.0, Openings.bayDepth(80.0), 1e-9)
        val s = scene(bay)
        // Dalla stanza si guarda dentro il bovindo fino al vetro davanti.
        val front = assertNotNull(fromInside(s, 200.0, 120.0))
        assertEquals(Pick.Opening(1, 1), front.pick)
        assertTrue(front.point.z < -60.0, "vetro davanti: z = ${front.point.z}")
        // Dall'alto si trova il tettuccio sopra la finestra (190 cm + 14).
        val roof = assertNotNull(s.hit(Vec3(200.0, 400.0, -40.0), Vec3(0.0, -1.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), roof.pick)
        assertEquals(204.0, roof.point.y, 0.5)
        // Fuori dal muro: niente oscuranti, sopraluce e sguincio; le quote esterne e l'ingombro da esportare passano oltre.
        val extras = bay.copy(shading = Shading.Shutters, topLight = true, splay = 10.0)
        assertFalse(extras.hasShading || extras.hasTopLight)
        assertEquals(0.0, Openings.splayOf(room(extras), extras))
        assertTrue(extras.copy(bay = false).hasShading && extras.copy(bay = false).hasTopLight)
        val plan = FloorPlan(listOf(room(bay)))
        assertEquals(67.5, ExteriorChains.of(plan).chains.single { it.out.y < -0.5 }.edge, 1e-6)
        assertEquals(7.5, ExteriorChains.of(FloorPlan(listOf(room()))).chains.single { it.out.y < -0.5 }.edge, 1e-6)
        assertEquals(-67.5, assertNotNull(Openings.planBounds(plan)).minY, 1e-6)
        // Balconi, scorrevoli e finestre ad arco non fanno il bovindo.
        assertFalse(bay.copy(sliding = true).isBay)
        assertFalse(bay.copy(windowShape = WindowShape.Arched).isBay)
        assertFalse(Opening.default(1, OpeningKind.Balcony2, 0, 200.0).copy(bay = true).isBay)
    }

    @Test
    fun `copia stile - infissi con infissi e porte con porte, misure e tipo restano`() {
        val from = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(
            windowModel = WindowModel.Wood, customColor = 0xFF5A3A22, shading = Shading.RomanShutter,
            shadingColor = 0xFF2F4F3A, grille = true, topLight = true, bay = true,
        )
        val balcony = Opening.default(2, OpeningKind.Balcony1, 1, 150.0).copy(sliding = true)
        val styled = balcony.withStyleOf(from)
        assertEquals(WindowModel.Wood, styled.windowModel)
        assertEquals(0xFF5A3A22, styled.customColor)
        assertEquals(Shading.RomanShutter, styled.shading)
        assertEquals(0xFF2F4F3A, styled.shadingColor)
        assertTrue(styled.grille)
        // Sopraluce e bovindo sono il tipo di finestra, non lo stile.
        assertFalse(styled.topLight || styled.bay)
        assertEquals(balcony.copy(windowModel = WindowModel.Wood, customColor = 0xFF5A3A22, shading = Shading.RomanShutter, shadingColor = 0xFF2F4F3A, grille = true), styled)
        // Porte, varchi e nicchie restano come sono.
        val door = Opening.default(3, OpeningKind.Door, 2, 100.0)
        assertEquals(door, door.withStyleOf(from))
        assertEquals(Opening.default(4, OpeningKind.Passage, 2, 100.0), Opening.default(4, OpeningKind.Passage, 2, 100.0).withStyleOf(from))
        // Le porte prendono modello, colore e cornice dalle porte.
        val walnut = door.copy(doorModel = DoorModel.Panels, color = Finish.Walnut, frameless = true)
        val door2 = Opening.default(5, OpeningKind.Door2, 1, 200.0).withStyleOf(walnut)
        assertEquals(DoorModel.Panels, door2.doorModel)
        assertEquals(Finish.Walnut, door2.color)
        assertTrue(door2.frameless)
        assertEquals(120.0, door2.width)
        assertEquals(from.copy(id = 9), from.copy(id = 9).withStyleOf(walnut))
    }

    @Test
    fun `inferriata, sopraluce, bovindo, legno e romana si salvano e si rileggono`() {
        val openings = listOf(
            OpeningPreset.Grille.apply(Opening.default(1, OpeningKind.Window1, 0, 100.0)),
            OpeningPreset.TopLight.apply(Opening.default(2, OpeningKind.Window2, 1, 150.0)),
            OpeningPreset.BayWindow.apply(Opening.default(3, OpeningKind.Window2, 2, 200.0)),
            OpeningPreset.WoodWindow.apply(Opening.default(4, OpeningKind.Balcony2, 3, 150.0)),
            OpeningPreset.RomanShutter.apply(Opening.default(5, OpeningKind.Window1, 0, 300.0)),
        )
        val plan = FloorPlan(listOf(room(*openings.toTypedArray())))
        assertEquals(openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }
}
