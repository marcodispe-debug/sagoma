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

    @Test
    fun `porte da garage - basculante e sezionale, chiuse nel vano e senza arco`() {
        val d = Opening.default(1, OpeningKind.Door, 0, 200.0)
        val up = OpeningPreset.GarageUpAndOver.apply(d) // 250 × 210: da 75 a 325
        val sec = OpeningPreset.GarageSectional.apply(d)
        assertEquals(GarageDoor.UpAndOver, up.garageDoor)
        assertEquals(GarageDoor.Sectional, sec.garageDoor)
        for (g in listOf(up, sec)) assertTrue(g.isGarage && !g.swings && !g.hasSideChoice && !g.hasDoorGlazing && g.transomCm == 0.0)
        // Bianche finché non si sceglie un colore (le porte normali sono rovere).
        assertEquals(Finish.White, up.finish)
        assertEquals(Finish.Oak, d.finish)
        // Scorrevole, a libro o a bilico non è più da garage.
        assertFalse(up.copy(sliding = true).isGarage || up.copy(folding = true).isGarage || up.copy(pivot = true).isGarage)
        assertEquals(157.5, Openings.garageReach(up), 1e-9)
        assertEquals(255.0, Openings.garageReach(sec), 1e-9)
        // Il pannello chiuso sta nel vano, verso la faccia interna del muro: da fuori si vede rientrato.
        for (g in listOf(up, sec)) {
            val s = scene(g)
            val inside = assertNotNull(fromInside(s, 200.0, 120.0))
            assertEquals(Pick.Opening(1, 1), inside.pick)
            assertTrue(inside.point.z > 5.0 && inside.point.z < 7.6, "da dentro: z = ${inside.point.z}")
            val outside = assertNotNull(fromOutside(s, 200.0, 120.0))
            assertEquals(Pick.Opening(1, 1), outside.pick)
            assertTrue(outside.point.z > 1.5, "da fuori: z = ${outside.point.z}")
        }
        // Sezionale: i binari entrano nella stanza sotto il soffitto (215 + 12 cm), fino a dove arriva il pannello aperto.
        val rail = assertNotNull(scene(sec).hit(Vec3(79.0, 400.0, 7.5 + 200.0), Vec3(0.0, -1.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), rail.pick)
        assertEquals(227.0, rail.point.y, 0.5)
        assertNull(scene(up).hit(Vec3(79.0, 400.0, 7.5 + 200.0), Vec3(0.0, -1.0, 0.0))?.takeIf { it.pick == Pick.Opening(1, 1) })
        // Copia stile: le porte da garage tra loro, le altre porte tra loro.
        assertEquals(d, d.withStyleOf(up.copy(customColor = 0xFF444444)))
        assertEquals(0xFF444444, sec.withStyleOf(up.copy(customColor = 0xFF444444)).customColor)
        // Si salvano e si rileggono.
        val plan = FloorPlan(listOf(room(up, sec.copy(id = 2, wallIndex = 2))))
        assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }

    @Test
    fun `zanzariera nel vano e tenda da sole fuori`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0) // 80 × 120: da 160 a 240, da 90 a 210
        val screen = w.copy(insectScreen = true)
        assertTrue(screen.hasInsectScreen)
        assertFalse(OpeningPreset.BayWindow.apply(screen).hasInsectScreen || OpeningPreset.RoundWindow.apply(screen).hasInsectScreen)
        // La rete sta verso fuori nel vano (il vetro a metà muro); con l'infisso a filo esterno sta verso la stanza.
        val net = assertNotNull(fromOutside(scene(screen), 200.0, 150.0))
        assertEquals(Pick.Opening(1, 1), net.pick)
        assertTrue(net.point.z < -5.0 && net.point.z > -7.6, "rete: z = ${net.point.z}")
        assertTrue(assertNotNull(fromOutside(scene(w), 200.0, 150.0)).point.z > -1.0)
        val inner = assertNotNull(fromInside(scene(screen.copy(frame = FramePosition.Outside)), 200.0, 150.0))
        assertTrue(inner.point.z > 5.0 && inner.point.z < 7.6, "rete dentro: z = ${inner.point.z}")
        // Tenda sulla finestra: a caduta, sporge fino a 72 cm; dall'alto si trova il telo sopra la finestra.
        val awning = w.copy(awning = true)
        assertTrue(awning.hasAwning)
        assertEquals(72.0, Openings.awningReach(awning), 1e-9)
        val cloth = assertNotNull(scene(awning).hit(Vec3(200.0, 400.0, -7.5 - 8 - 36), Vec3(0.0, -1.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), cloth.pick)
        assertTrue(cloth.point.y > 150.0 && cloth.point.y < 229.0, "telo a ${cloth.point.y}")
        // Sul balcone: tenda a bracci quasi in piano che sporge 2 m.
        val balcony = Opening.default(1, OpeningKind.Balcony2, 0, 200.0).copy(awning = true) // alto 240
        assertEquals(200.0, Openings.awningReach(balcony), 1e-9)
        val over = assertNotNull(scene(balcony).hit(Vec3(200.0, 400.0, -7.5 - 8 - 100), Vec3(0.0, -1.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), over.pick)
        assertEquals(233.0, over.point.y, 1.0)
        assertFalse(OpeningPreset.BayWindow.apply(awning).hasAwning)
        // Il telo sporge 12 cm per parte, ma non oltre l'angolo (finestra da 2 a 82: l'angolo esterno è a −7,5)...
        val corner = scene(awning.copy(position = 42.0))
        assertEquals(Pick.Opening(1, 1), assertNotNull(corner.hit(Vec3(0.0, 400.0, -51.5), Vec3(0.0, -1.0, 0.0))).pick)
        assertNull(corner.hit(Vec3(-9.0, 400.0, -51.5), Vec3(0.0, -1.0, 0.0)))
        // ...e con due finestre a 20 cm le tende si fermano a metà, senza sovrapporsi.
        val pair = scene(awning, awning.copy(id = 2, position = 300.0)) // da 160 a 240 e da 260 a 340
        assertEquals(Pick.Opening(1, 1), assertNotNull(pair.hit(Vec3(249.0, 400.0, -51.5), Vec3(0.0, -1.0, 0.0))).pick)
        assertEquals(Pick.Opening(1, 2), assertNotNull(pair.hit(Vec3(251.0, 400.0, -51.5), Vec3(0.0, -1.0, 0.0))).pick)
        // Fanno parte dello stile; si salvano e si rileggono.
        val from = w.copy(insectScreen = true, awning = true, awningColor = 0xFF8A2C2C)
        val styled = Opening.default(2, OpeningKind.Balcony1, 1, 150.0).withStyleOf(from)
        assertTrue(styled.insectScreen && styled.awning)
        assertEquals(0xFF8A2C2C, styled.awningColor)
        val plan = FloorPlan(listOf(room(from, styled)))
        assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }

    @Test
    fun `davanzale e soglia - lastra sotto l'infisso che sporge dentro e fuori`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0) // 80 × 120: da 160 a 240, davanzale a 90
        val marble = w.copy(sillMaterial = StairMaterial.Marble)
        assertEquals(StairMaterial.Marble, marble.sill)
        assertNull(w.sill)
        // Il bovindo ha la sua seduta e l'oblò il fondo tondo: niente lastra.
        assertNull(OpeningPreset.BayWindow.apply(marble).sill)
        assertNull(OpeningPreset.RoundWindow.apply(marble).sill)
        fun down(s: Scene3D, x: Double, z: Double) = s.hit(Vec3(x, 400.0, z), Vec3(0.0, -1.0, 0.0))
        // Dall'alto, appena fuori dalla faccia esterna (z = −7,5) e appena dentro quella interna (z = 7,5), si trova la lastra.
        for (z in listOf(-9.0, 8.5)) {
            val top = assertNotNull(down(scene(marble), 200.0, z), "z = $z")
            assertEquals(Pick.Opening(1, 1), top.pick)
            assertEquals(90.5, top.point.y, 0.01)
            assertTrue(down(scene(w), 200.0, z)?.let { it.pick == Pick.Opening(1, 1) && it.point.y > 80 } != true, "senza davanzale, z = $z")
        }
        // Le orecchie: fuori sporge anche ai lati del vano.
        assertEquals(Pick.Opening(1, 1), assertNotNull(down(scene(marble), 158.5, -9.0)).pick)
        // Con lo sguincio verso la stanza si allarga come il vano.
        val splayed = marble.copy(splay = 15.0)
        assertEquals(Pick.Opening(1, 1), assertNotNull(down(scene(splayed), 148.0, 8.5)).pick)
        // Soglia del balcone: a terra, mezzo cm sopra il pavimento.
        val threshold = assertNotNull(down(scene(Opening.default(1, OpeningKind.Balcony1, 0, 200.0).copy(sillMaterial = StairMaterial.Stone)), 200.0, -9.0))
        assertEquals(Pick.Opening(1, 1), threshold.pick)
        assertEquals(0.5, threshold.point.y, 0.01)
        // Fa parte dello stile, va nell'elenco del computo e si salva.
        val styled = Opening.default(2, OpeningKind.Balcony1, 1, 150.0).withStyleOf(marble)
        assertEquals(StairMaterial.Marble, styled.sillMaterial)
        assertTrue(Openings.description(marble).endsWith("davanzale in marmo"), Openings.description(marble))
        assertTrue(Openings.description(styled).endsWith("soglia in marmo"), Openings.description(styled))
        val plan = FloorPlan(listOf(room(marble, styled)))
        assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
        // Anche le porte (es. la soglia di marmo all'ingresso), non quelle da garage.
        val door = Opening.default(1, OpeningKind.Door, 0, 200.0).copy(sillMaterial = StairMaterial.Marble)
        // Dal mezzo del vano verso il basso (sopra la porta c'è il coprifilo).
        val doorSill = assertNotNull(scene(door).hit(Vec3(200.0, 100.0, 0.0), Vec3(0.0, -1.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), doorSill.pick)
        assertEquals(0.5, doorSill.point.y, 0.01)
        assertEquals("Liscia, rovere · soglia in marmo", Openings.description(door))
        assertNull(OpeningPreset.GarageSectional.apply(door).sill)
        assertEquals(StairMaterial.Marble, Opening.default(2, OpeningKind.Door2, 1, 150.0).withStyleOf(door).sillMaterial)
    }

    @Test
    fun `vetromattone - blocchi di vetro fissi, senza telaio né accessori`() {
        val w = Opening.default(1, OpeningKind.Window1, 0, 200.0).copy(
            sash = SashType.TiltTurn, shading = Shading.Shutters, grille = true, insectScreen = true, awning = true, topLight = true,
        )
        val g = OpeningPreset.GlassBlock.apply(w) // 76 × 114 a 100 da terra: da 162 a 238, da 100 a 214
        assertTrue(g.isGlassBlock)
        assertTrue(g.fixed)
        assertEquals(Shading.None, g.shading)
        assertFalse(g.hasGrille || g.hasInsectScreen || g.hasAwning || g.hasTopLight || g.isBay || g.hasShading)
        assertEquals(4 to 6, Openings.glassBlockGrid(g))
        // Anche con i dati scritti a mano (o un file vecchio): niente accessori né forme sul vetromattone.
        val forced = g.copy(grille = true, insectScreen = true, awning = true, shading = Shading.Shutters, bay = true, windowShape = WindowShape.Arched)
        assertFalse(forced.hasGrille || forced.hasInsectScreen || forced.hasAwning || forced.hasShading || forced.isBay)
        assertEquals(WindowShape.Rectangle, forced.shape)
        // Anche cambiando il modello dalla scheda (e tornando indietro le ante si aprono di nuovo).
        val slidingBalcony = Opening.default(1, OpeningKind.Balcony2, 0, 200.0).copy(sliding = true, liftSlide = true).withWindowModel(WindowModel.GlassBlock)
        assertTrue(slidingBalcony.fixed && !slidingBalcony.sliding && !slidingBalcony.lifts)
        val back = g.withWindowModel(WindowModel.Classic)
        assertFalse(back.fixed)
        assertEquals(SashType.Casement, back.sash)
        assertFalse(Opening.default(2, OpeningKind.Door, 1, 200.0).copy(windowModel = WindowModel.GlassBlock).isGlassBlock)
        // In 3D: le facce dei blocchi a 4 cm dalla mezzeria del muro, le fughe di malta un poco più indietro (3,6).
        // Blocchi di 18,75 × 18,83 cm di passo con la fuga da 1: il primo da 163 a 180,75, la fuga dopo da 180,75 a 181,75.
        val face = assertNotNull(fromInside(scene(g), 172.0, 110.0))
        assertEquals(Pick.Opening(1, 1), face.pick)
        assertEquals(4.0, face.point.z, 0.01)
        assertEquals(3.6, assertNotNull(fromInside(scene(g), 181.25, 110.0)).point.z, 0.01)
        assertEquals(3.6, assertNotNull(fromInside(scene(g), 172.0, 119.3)).point.z, 0.01)
        assertEquals(-4.0, assertNotNull(fromOutside(scene(g), 172.0, 110.0)).point.z, 0.01)
        // Né persiane né maniglia: niente sporge dalla stanza oltre i blocchi.
        for (x in listOf(165.0, 200.0, 235.0)) for (y in listOf(105.0, 157.0, 210.0)) {
            assertTrue(assertNotNull(fromInside(scene(g), x, y)).point.z < 4.01, "x = $x, y = $y")
        }
        // Copia stile: il vetromattone non diventa una finestra e le finestre non diventano vetromattone.
        val classic = Opening.default(2, OpeningKind.Window2, 1, 150.0).copy(shading = Shading.Shutters)
        assertEquals(classic, classic.withStyleOf(g))
        assertEquals(g, g.withStyleOf(classic))
        val other = OpeningPreset.GlassBlock.apply(Opening.default(3, OpeningKind.Window1, 2, 100.0))
        assertEquals(StairMaterial.Marble, other.withStyleOf(g.copy(sillMaterial = StairMaterial.Marble)).sill)
        // Nel computo: «Vetromattone» con quanti blocchi servono; si salva e si rilegge.
        assertEquals("Vetromattone", Openings.label(g))
        assertEquals("24 blocchi di vetro", Openings.description(g))
        assertEquals("24 blocchi di vetro · davanzale in marmo", Openings.description(g.copy(sillMaterial = StairMaterial.Marble)))
        assertEquals("15 blocchi di vetro", Openings.description(g.copy(width = 95.0, height = 57.0)))
        assertEquals(listOf("Vetromattone"), Takeoff.of(Building.single(FloorPlan(listOf(room(g))))).openings.map { it.name })
        val plan = FloorPlan(listOf(room(g, other.copy(sillMaterial = StairMaterial.Stone))))
        assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }

    @Test
    fun `persiane scorrevoli - ante aperte di lato fuori dal muro e binario sopra`() {
        // 120 × 120: da 140 a 260, da 90 a 210. Le ante aperte, larghe 63 cm: da 76 a 139 e da 261 a 324.
        val w = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(width = 120.0, shading = Shading.SlidingShutters)
        val panel = assertNotNull(fromOutside(scene(w), 100.0, 150.0))
        assertEquals(Pick.Opening(1, 1), panel.pick)
        // A 3,5 cm dalla faccia esterna del muro (−7,5), spesse 3.
        assertTrue(panel.point.z < -9.0 && panel.point.z > -14.0, "z = ${panel.point.z}")
        assertEquals(Pick.Opening(1, 1), assertNotNull(fromOutside(scene(w), 300.0, 150.0)).pick)
        assertEquals(-7.5, assertNotNull(fromOutside(scene(w.copy(shading = Shading.None)), 100.0, 150.0)).point.z, 0.01)
        // Il vano resta libero e sopra corre il binario.
        assertTrue(assertNotNull(fromOutside(scene(w), 180.0, 150.0)).point.z > -8.0)
        val rail = assertNotNull(fromOutside(scene(w), 200.0, 215.0))
        assertEquals(Pick.Opening(1, 1), rail.pick)
        assertTrue(rail.point.z < -12.0, "binario: z = ${rail.point.z}")
        // Con un'anta sola si apre da una parte sola.
        val single = Opening.default(1, OpeningKind.Window1, 0, 200.0).copy(shading = Shading.SlidingShutters, hingeLeft = true) // da 160 a 240
        assertEquals(Pick.Opening(1, 1), assertNotNull(fromOutside(scene(single), 130.0, 150.0)).pick)
        assertEquals(-7.5, assertNotNull(fromOutside(scene(single), 270.0, 150.0)).point.z, 0.01)
        // Antracite con l'infisso bianco, altrimenti del colore dell'infisso; nel computo; si salva.
        assertEquals(Finish.Anthracite.argb, w.shadingArgb)
        assertEquals(Finish.Walnut.argb, w.copy(color = Finish.Walnut).shadingArgb)
        assertEquals("Classica, bianco · persiane scorrevoli", Openings.description(w))
        val plan = FloorPlan(listOf(room(w, single.copy(id = 2, wallIndex = 2))))
        assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }

    @Test
    fun `porta tutto vetro - lastra satinata con le cerniere, telaio bianco`() {
        val d = OpeningPreset.AllGlass.apply(Opening.default(1, OpeningKind.Door, 0, 200.0))
        assertEquals(DoorModel.AllGlass, d.doorModel)
        // Scorrevole sul muro: l'anta di vetro si colpisce (e si sceglie) ed è tra i trasparenti.
        val slide = OpeningPreset.WallSlide.apply(d)
        val hit = assertNotNull(fromInside(scene(slide), if (slide.hingeLeft) 170.0 else 230.0, 60.0))
        assertEquals(Pick.Opening(1, 1), hit.pick)
        assertTrue(hit.point.z > 9.0, "z = ${hit.point.z}")
        assertTrue(scene(slide).transparent.size > scene(slide.copy(doorModel = DoorModel.Flush)).transparent.size)
        assertEquals("Tutto vetro, bianco", Openings.description(d))
        // «Copia stile» tra porte: il modello passa; si salva e si rilegge.
        assertEquals(DoorModel.AllGlass, Opening.default(2, OpeningKind.Door2, 1, 150.0).withStyleOf(d).doorModel)
        val plan = FloorPlan(listOf(room(d, slide.copy(id = 2, wallIndex = 2))))
        assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }

    @Test
    fun `oscuranti sulla faccia del muro anche con l'infisso a filo esterno o interno`() {
        // Muro da 40 (facce a ±20): l'infisso a filo si sposta di 14 cm, gli oscuranti restano sulle facce del muro.
        fun scene40(o: Opening) = Scene3D.build(
            FloorPlan(listOf(Room(1, "Prova", RoomType.Altro, listOf(Vec2(0.0, 0.0), Vec2(400.0, 0.0), Vec2(400.0, 300.0), Vec2(0.0, 300.0)), wallThickness = 40.0, openings = listOf(o)))),
            null, null, ceilings = false,
        )
        for (frame in FramePosition.entries) {
            // 120 × 120 da 140 a 260 e da 90 a 210: persiane aperte da 80 a 140 e da 260 a 320.
            val w = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(width = 120.0, frame = frame)
            val shutters = assertNotNull(scene40(w.copy(shading = Shading.Shutters)).hit(Vec3(100.0, 153.0, -200.0), Vec3(0.0, 0.0, 1.0)))
            assertEquals(Pick.Opening(1, 1), shutters.pick, "$frame")
            assertEquals(-23.3, shutters.point.z, 0.01, "persiane, $frame")
            val sliding = assertNotNull(scene40(w.copy(shading = Shading.SlidingShutters)).hit(Vec3(100.0, 156.0, -200.0), Vec3(0.0, 0.0, 1.0)))
            assertEquals(-25.0, sliding.point.z, 0.01, "scorrevoli, $frame")
            val inside = assertNotNull(scene40(w.copy(shading = Shading.InteriorShutters)).hit(Vec3(100.0, 153.0, 200.0), Vec3(0.0, 0.0, -1.0)))
            assertEquals(Pick.Opening(1, 1), inside.pick, "$frame")
            assertEquals(23.1, inside.point.z, 0.01, "scuri interni, $frame")
        }
    }

    @Test
    fun `persiane accanto all'angolo - piegate in due o aperte a 90 gradi, mai fuori dal muro`() {
        // 120 × 120 da 30 a 150: a sinistra, fino all'angolo esterno (x = −7,5), ci sono 37,5 cm per un'anta da 60.
        val near = Opening.default(1, OpeningKind.Window2, 0, 90.0).copy(width = 120.0, shading = Shading.Shutters)
        val s = scene(near)
        // L'anta di sinistra è piegata in due (da 0 a 30): lo strato di fuori sta 3,2 cm più in là.
        val folded = assertNotNull(fromOutside(s, 15.0, 153.0))
        assertEquals(Pick.Opening(1, 1), folded.pick)
        assertEquals(-14.0, folded.point.z, 0.01)
        // Oltre l'angolo non c'è niente; a destra lo spazio basta e l'anta resta distesa.
        assertNull(fromOutside(s, -10.0, 153.0), "l'anta non esce oltre l'angolo")
        assertEquals(-10.8, assertNotNull(fromOutside(s, 180.0, 153.0)).point.z, 0.01)
        // Più vicina all'angolo (80 da 10 a 90, 17,5 cm di spazio): l'anta si apre a 90°, di taglio verso fuori.
        val corner = Opening.default(1, OpeningKind.Window1, 0, 50.0).copy(shading = Shading.Shutters, hingeLeft = true)
        val open = assertNotNull(scene(corner).hit(Vec3(100.0, 153.0, -50.0), Vec3(-1.0, 0.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), open.pick)
        assertEquals(10.0, open.point.x, 0.01)
        assertNull(scene(corner.copy(position = 200.0)).hit(Vec3(300.0, 153.0, -50.0), Vec3(-1.0, 0.0, 0.0)), "lontano dall'angolo resta distesa")
        // Scuri interni: dentro lo spazio finisce sulla faccia del muro di fianco (x = 7,5), 22,5 cm: aperti a 90° nella stanza.
        val inner = assertNotNull(scene(near.copy(shading = Shading.InteriorShutters)).hit(Vec3(100.0, 153.0, 40.0), Vec3(-1.0, 0.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), inner.pick)
        assertTrue(inner.point.x > 29.0 && inner.point.x < 31.0, "x = ${inner.point.x}")
        assertNull(fromOutside(scene(near.copy(shading = Shading.InteriorShutters)), -20.0, 153.0), "lo scuro non passa il muro di fianco")
    }

    @Test
    fun `persiane accanto a un muro che sporge - si aprono a 90 gradi contro il muro`() {
        // Un'altra stanza attaccata fuori, da x = 200 a 400: il suo muro x = 200 sporge dalla facciata (faccia a 192,5).
        val w = Opening.default(1, OpeningKind.Window2, 0, 120.0).copy(width = 120.0, shading = Shading.Shutters) // da 60 a 180
        val outside = Room(2, "Fuori", RoomType.Altro, listOf(Vec2(200.0, -200.0), Vec2(400.0, -200.0), Vec2(400.0, 0.0), Vec2(200.0, 0.0)))
        val s = Scene3D.build(FloorPlan(listOf(room(w), outside)), null, null, ceilings = false)
        // A destra 12,5 cm per un'anta da 60: aperta a 90°, di taglio verso fuori; a sinistra resta distesa.
        val open = assertNotNull(s.hit(Vec3(100.0, 153.0, -50.0), Vec3(1.0, 0.0, 0.0)))
        assertEquals(Pick.Opening(1, 1), open.pick)
        assertEquals(180.0, open.point.x, 0.01)
        assertEquals(-7.5, assertNotNull(fromOutside(s, 190.0, 153.0)).point.z, 0.01, "niente anta dentro il muro sporgente")
        assertEquals(-10.8, assertNotNull(fromOutside(s, 20.0, 153.0)).point.z, 0.01)
    }

    @Test
    fun `persiane scorrevoli accanto all'angolo - l'anta si ferma e copre parte del vano`() {
        // 80 da 10 a 90, anta a sinistra larga 83: fino all'angolo (−7,5) corre solo 17,5 cm, quindi copre il vano fino a 75,5.
        val w = Opening.default(1, OpeningKind.Window1, 0, 50.0).copy(shading = Shading.SlidingShutters, hingeLeft = true)
        val s = scene(w)
        val panel = assertNotNull(fromOutside(s, 60.0, 150.0))
        assertEquals(Pick.Opening(1, 1), panel.pick)
        assertEquals(-12.5, panel.point.z, 0.01)
        assertTrue(assertNotNull(fromOutside(s, 80.0, 150.0)).point.z > -8.0, "oltre l'anta il vano resta libero")
        // Né l'anta né il binario escono oltre l'angolo.
        assertNull(fromOutside(s, -10.0, 150.0))
        assertNull(fromOutside(s, -10.0, 215.0))
        // Due ante senza spazio da nessuna parte (vano largo quanto il muro): restano quasi chiuse, dentro il muro.
        val wide = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(width = 400.0, shading = Shading.SlidingShutters)
        val shut = scene(wide)
        assertEquals(-12.5, assertNotNull(fromOutside(shut, 150.0, 150.0)).point.z, 0.01)
        assertEquals(-12.5, assertNotNull(fromOutside(shut, 250.0, 150.0)).point.z, 0.01)
        assertNull(fromOutside(shut, -10.0, 150.0))
        assertNull(fromOutside(shut, 410.0, 150.0))
    }

    @Test
    fun `persiane di due finestre vicine - non si sovrappongono, lo spazio in mezzo si divide`() {
        // 120 × 120 da 40 a 160 e da 240 a 360: 80 cm in mezzo, 40 per parte, ante da 60 piegate in due.
        val left = Opening.default(1, OpeningKind.Window2, 0, 100.0).copy(width = 120.0, shading = Shading.Shutters)
        val right = Opening.default(2, OpeningKind.Window2, 0, 300.0).copy(width = 120.0, shading = Shading.Shutters)
        val s = scene(left, right)
        val a = assertNotNull(fromOutside(s, 175.0, 153.0))
        assertEquals(Pick.Opening(1, 1), a.pick)
        assertEquals(-14.0, a.point.z, 0.01)
        val b = assertNotNull(fromOutside(s, 225.0, 153.0))
        assertEquals(Pick.Opening(1, 2), b.pick)
        assertEquals(-14.0, b.point.z, 0.01)
        assertEquals(-7.5, assertNotNull(fromOutside(s, 200.0, 153.0)).point.z, 0.01, "in mezzo il muro resta libero")
        // Accanto a una finestra senza persiane l'anta arriva fino a 4 cm da lei (76 cm): resta distesa.
        val plain = scene(left, right.copy(shading = Shading.None))
        assertEquals(-10.8, assertNotNull(fromOutside(plain, 210.0, 153.0)).point.z, 0.01)
        // Gli scuri interni della finestra accanto stanno dall'altra parte: fuori non contano.
        assertEquals(-10.8, assertNotNull(fromOutside(scene(left, right.copy(shading = Shading.InteriorShutters)), 210.0, 153.0)).point.z, 0.01)
        // Dentro, due finestre con gli scuri: piegati in due anche loro.
        val inner = scene(left.copy(shading = Shading.InteriorShutters), right.copy(shading = Shading.InteriorShutters))
        assertEquals(13.8, assertNotNull(fromInside(inner, 175.0, 153.0)).point.z, 0.01)
        assertEquals(7.5, assertNotNull(fromInside(inner, 200.0, 153.0)).point.z, 0.01)
        // Accanto a una porta l'anta si ferma prima del coprifilo (7 cm): da 160 a 202, piegata.
        val door = Opening.default(2, OpeningKind.Door, 0, 250.0) // da 210 a 290
        val nearDoor = scene(left, door)
        assertEquals(-14.0, assertNotNull(fromOutside(nearDoor, 175.0, 153.0)).point.z, 0.01)
        // Una persiana a un'anta sola, girata dall'altra parte, non toglie spazio: l'anta resta distesa.
        val single = Opening.default(2, OpeningKind.Window1, 0, 300.0).copy(shading = Shading.Shutters, hingeLeft = false) // da 260 a 340, anta a destra
        assertEquals(-10.8, assertNotNull(fromOutside(scene(left, single), 210.0, 153.0)).point.z, 0.01)
    }

    /** Trasparenze dei vetri della scena (alfa dei vertici trasparenti), arrotondate al centesimo. */
    private fun alphas(s: Scene3D): Set<Float> {
        val out = mutableSetOf<Float>()
        var k = 0
        while (k < s.transparent.size) {
            out += kotlin.math.round(s.transparent[k + 9] * 100) / 100
            k += Scene3D.FLOATS_PER_VERTEX
        }
        return out
    }

    @Test
    fun `vetro satinato - finestre, balconi e porte con il vetro, nel computo, nello stile e nel file`() {
        val w = Opening.default(1, OpeningKind.Window2, 0, 200.0)
        val f = w.copy(frosted = true)
        assertTrue(f.hasFrostedGlass)
        // Nel 3D i vetri diventano un velo bianco quasi coprente (oltre 0,5): passa la luce, non la vista.
        assertTrue(0.35f in alphas(scene(w)) && 0.85f !in alphas(scene(w)))
        assertTrue(0.85f in alphas(scene(f)) && 0.35f !in alphas(scene(f)), "${alphas(scene(f))}")
        // Anche il sopraluce, la finestra ad arco e il bovindo.
        for (p in listOf(OpeningPreset.TopLight, OpeningPreset.ArchedWindow, OpeningPreset.BayWindow)) {
            assertTrue(0.85f in alphas(scene(p.apply(f))) && 0.35f !in alphas(scene(p.apply(f))), "$p")
        }
        // Le porte solo con il vetro: a vetri, sopraluce o vetro di fianco; non la porta liscia, quella da garage o tutto vetro (già satinata).
        val door = Opening.default(1, OpeningKind.Door, 0, 200.0).copy(frosted = true)
        assertFalse(door.hasFrostedGlass)
        assertTrue(door.copy(doorModel = DoorModel.Glazed).hasFrostedGlass)
        assertTrue(OpeningPreset.Glazed.apply(door).hasFrostedGlass)
        assertFalse(OpeningPreset.GarageSectional.apply(door.copy(doorModel = DoorModel.Glazed)).hasFrostedGlass)
        assertFalse(OpeningPreset.AllGlass.apply(door).hasFrostedGlass)
        assertTrue(0.85f in alphas(scene(door.copy(doorModel = DoorModel.Glazed))))
        assertTrue(0.85f in alphas(scene(OpeningPreset.Glazed.apply(door))))
        // Il vetromattone è già smerigliato.
        assertFalse(OpeningPreset.GlassBlock.apply(f).hasFrostedGlass)
        // Nel computo, nello stile copiato e nel file.
        assertEquals("Classica, bianco · vetro satinato", Openings.description(f))
        assertEquals("Con vetro, rovere · vetro satinato", Openings.description(door.copy(doorModel = DoorModel.Glazed)))
        assertTrue(Opening.default(2, OpeningKind.Balcony1, 1, 150.0).withStyleOf(f).frosted)
        assertTrue(Opening.default(2, OpeningKind.Door, 1, 150.0).withStyleOf(door).frosted)
        val plan = FloorPlan(listOf(room(f, door.copy(id = 2, wallIndex = 2, doorModel = DoorModel.Glazed))))
        assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
    }

    @Test
    fun `porte con la finitura in foto - anta, coprifilo e garage nel 3D, verso e misura, computo, stile e file`() {
        val saved = MaterialCatalog.items
        try {
            MaterialCatalog.set(listOf(MaterialCatalog.Item("noce_prova", "Noce", "Legno", size = 50.0, argb = 0xFF6B4A33, use = "pavimento")))
            val door = Opening.default(1, OpeningKind.Door, 0, 200.0)
            val wood = door.copy(doorMaterial = "noce_prova")
            assertTrue(wood.canHaveDoorMaterial)
            assertEquals("noce_prova", wood.doorFinish)
            fun triangles(s: Scene3D) = (s.textured["noce_prova"]?.size ?: 0) / 24
            // Senza materiale niente foto; con il materiale l'anta (due facce) e il coprifilo (tre pezzi per lato).
            assertEquals(0, triangles(scene(door)))
            assertTrue(triangles(scene(wood)) >= 2 * 2 + 6 * 2, "${triangles(scene(wood))}")
            // Filo muro: niente coprifilo, resta l'anta.
            assertTrue(triangles(scene(wood.copy(frameless = true))) in 4 until triangles(scene(wood)))
            // Tutti i modelli (anche a riquadri, a vetri, blindata), la porta a 2 ante e quella da garage; non la porta tutto vetro.
            for (m in DoorModel.entries - DoorModel.AllGlass) assertTrue(triangles(scene(wood.copy(doorModel = m))) > 0, "$m")
            assertTrue(triangles(scene(wood.copy(kind = OpeningKind.Door2, width = 140.0))) > 0)
            assertTrue(triangles(scene(OpeningPreset.GarageSectional.apply(wood))) > 0)
            assertFalse(wood.copy(doorModel = DoorModel.AllGlass).canHaveDoorMaterial)
            assertEquals(0, triangles(scene(wood.copy(doorModel = DoorModel.AllGlass))))
            // Finestre e balconi no (il materiale è solo delle porte).
            val window = Opening.default(1, OpeningKind.Window2, 0, 200.0).copy(doorMaterial = "noce_prova")
            assertNull(window.doorFinish)
            assertEquals(0, triangles(scene(window)))
            // La misura: con la foto grande il doppio le coordinate nella foto sono la metà.
            fun maxU(s: Scene3D): Float {
                val f = s.textured.getValue("noce_prova")
                return (6 until f.size step 8).maxOf { kotlin.math.abs(f[it]) }
            }
            val big = scene(wood.copy(doorMaterialLayout = MaterialLayout(scale = 2.0)))
            assertEquals(maxU(scene(wood)) / 2, maxU(big), 0.01f)
            // Nel computo il materiale al posto del colore; lo copia «Applica stile»; resta nel file.
            assertTrue(Openings.description(wood).startsWith(door.doorModel.label + ", noce"), Openings.description(wood))
            val other = Opening.default(2, OpeningKind.Door, 1, 150.0)
            assertEquals("noce_prova", other.withStyleOf(wood.copy(doorMaterialLayout = MaterialLayout(rotation = 90.0))).doorMaterial)
            assertEquals(90.0, other.withStyleOf(wood.copy(doorMaterialLayout = MaterialLayout(rotation = 90.0))).doorMaterialLayout.rotation)
            val plan = FloorPlan(listOf(room(wood.copy(doorMaterialLayout = MaterialLayout(rotation = 90.0, scale = 1.5)))))
            assertEquals(plan.rooms.single().openings, PlanJson.decode(PlanJson.encode(plan)).rooms.single().openings)
        } finally {
            MaterialCatalog.set(saved)
        }
    }
}
