package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.GarageDoor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.SashType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.WindowShape
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tipi nuovi di porte, finestre e varchi nello scambio IFC: escono con i nomi standard dell'IFC4 (modo di aprirsi delle
 * porte, ante delle finestre nel loro tipo, nicchia come incavo, arco nella forma del vano) e tornano uguali in sagoma.
 *
 * Soggiorno 600 × 400 con muri esterni da 30 e il tramezzo da 20 in comune con la camera (a destra).
 * Muri del soggiorno: 0 in alto (0,0)→(600,0), 1 il tramezzo, 2 in basso, 3 a sinistra.
 */
class IfcOpeningTypesTest {
    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))

    private val openings = listOf(
        Opening(1, OpeningKind.Window1, 0, 80.0, 70.0, 140.0, sillHeight = 90.0, hingeLeft = false, sash = SashType.TiltTurn),
        Opening(2, OpeningKind.Window1, 0, 200.0, 60.0, 60.0, sillHeight = 150.0, sash = SashType.Hopper),
        Opening(3, OpeningKind.Window2, 0, 330.0, 120.0, 140.0, sillHeight = 90.0, sash = SashType.Fixed),
        Opening(4, OpeningKind.Window2, 0, 500.0, 140.0, 200.0, sillHeight = 20.0, windowModel = WindowModel.Loft, sash = SashType.Fixed),
        Opening(5, OpeningKind.Door, 1, 70.0, 80.0, 210.0, sliding = true, slideOnWall = true),
        Opening(6, OpeningKind.Door, 1, 190.0, 80.0, 210.0, hingeLeft = false, folding = true),
        Opening(7, OpeningKind.Passage, 1, 330.0, 90.0, 220.0, style = PassageStyle.Pointed),
        Opening(8, OpeningKind.Door, 2, 100.0, 80.0, 210.0, frameless = true),
        Opening(9, OpeningKind.Door2, 2, 300.0, 120.0, 210.0, hingeLeft = false, unequalLeaves = true),
        Opening(10, OpeningKind.Passage, 2, 500.0, 100.0, 210.0, style = PassageStyle.Segmental),
        Opening(11, OpeningKind.Niche, 3, 200.0, 60.0, 80.0, sillHeight = 100.0),
    )
    private val soggiorno = Room(
        1, "Soggiorno", RoomType.Soggiorno, rect(0.0, 0.0, 600.0, 400.0), wallThickness = 30.0, wallThicknesses = mapOf(1 to 20.0),
        openings = openings,
    )

    /** La camera ha una nicchia sul tramezzo, scavata dalla sua parte (y = 260, tra la porta a libro e l'arco). */
    private val cameraNiche = Opening(1, OpeningKind.Niche, 3, 140.0, 40.0, 60.0, sillHeight = 110.0)
    private val camera = Room(
        2, "Camera", RoomType.Camera, rect(600.0, 0.0, 900.0, 400.0), wallThickness = 30.0, wallThicknesses = mapOf(3 to 20.0),
        openings = listOf(cameraNiche),
    )

    private fun house(rooms: List<Room> = listOf(soggiorno, camera)) =
        Building(listOf(Floor(1, "Piano terra", FloorPlan(rooms = rooms), levelHeight = 300.0)))

    private fun export(b: Building = house()) = IfcExport.export(b, "Porte nuove", key = "porte").also { save("porte-nuove.ifc", it) }

    /** Con SAGOMA_IFC_PORTE=<cartella> i file di prova restano lì, per guardarli con IfcOpenShell o un visualizzatore. */
    private fun save(name: String, text: String) {
        val dir = System.getenv("SAGOMA_IFC_PORTE") ?: return
        File(dir).mkdirs()
        File(dir, name).writeText(text)
    }

    /** Quello che deve tornare uguale: tipo, misure, ante e aspetto. */
    private fun look(o: Opening) = listOf(
        o.kind, o.style, o.sliding, o.slideOnWall, o.folding, o.frameless, o.unequalLeaves, o.sash, o.windowModel, o.hingeLeft,
        o.width.toInt(), o.height.toInt(), if (o.kind.hasSill) o.sillHeight.toInt() else 0,
    )

    @Test
    fun `i tipi nuovi escono con i nomi standard dell'IFC4`() {
        val f = StepFile.parse(export())
        val doors = f.ofType("IFCDOOR").map { it.enum(11) }.sortedBy { it }
        assertEquals(listOf("DOUBLE_DOOR_SINGLE_SWING", "FOLDING_TO_RIGHT", "SINGLE_SWING_LEFT", "SLIDING_TO_LEFT"), doors)
        // Le ante delle finestre stanno nel tipo della finestra.
        val typeOf = f.ofType("IFCRELDEFINESBYTYPE").flatMap { r -> r.refs(4).map { it to f[r.ref(5)]!! } }.toMap()
        val windows = f.ofType("IFCWINDOW")
        assertEquals(4, windows.size)
        val panels = windows.map { w ->
            val t = assertNotNull(typeOf[w.id], "ogni finestra ha il suo tipo")
            assertEquals("IFCWINDOWTYPE", t.type)
            t.refs(5).map { f[it]!!.enum(4) }
        }.toSet()
        assertEquals(
            setOf(listOf("TILTANDTURNRIGHTHAND"), listOf("BOTTOMHUNG"), listOf("FIXEDCASEMENT", "FIXEDCASEMENT")),
            panels,
        )
        // I tipi sono dichiarati nel progetto.
        val declared = f.ofType("IFCRELDECLARES").flatMap { it.refs(5) }.toSet()
        assertTrue(f.ofType("IFCWINDOWTYPE").all { it.id in declared })
        // Due nicchie: incavi che non hanno né porta né finestra.
        val recesses = f.ofType("IFCOPENINGELEMENT").filter { it.enum(8) == "RECESS" }
        assertEquals(2, recesses.size)
        val filled = f.ofType("IFCRELFILLSELEMENT").map { it.ref(4) }.toSet()
        assertTrue(recesses.none { it.id in filled })
    }

    @Test
    fun `l'arco esce nella forma del vano e la nicchia scava solo mezzo muro`() {
        val text = export()
        val f = StepFile.parse(text)
        val solids = IfcImport.solids(text).filter { it.first == "IFCOPENINGELEMENT" }.map { it.second }
        // Profilo del vano: 4 punti se dritto, tanti se ad arco (due varchi ad arco).
        val profiles = f.ofType("IFCOPENINGELEMENT").map { op ->
            val rep = f[f[op.ref(6)]!!.refs(2).first()]!!
            val solid = f[rep.refs(3).first()]!!
            val profile = f[solid.ref(0)]!!
            f[profile.ref(2)]!!.refs(0).size - 1
        }
        assertEquals(2, profiles.count { it > 10 }, "profili: $profiles")
        // Nicchie: profonde metà muro (15 cm il muro esterno, 10 il tramezzo) più il centimetro di margine fuori dalla faccia.
        val depths = solids.map { rings -> rings[0].zip(rings[1]).maxOf { (a, b) -> abs(a[0] - b[0]) + abs(a[1] - b[1]) } }.sorted()
        assertEquals(listOf(11.0, 16.0), depths.take(2).map { Math.round(it).toDouble() })
        assertTrue(depths.drop(2).all { it > 20 }, "gli altri vani passano tutto il muro: $depths")
        // Tutti i vani stanno tra il davanzale e la cima, anche le nicchie scavate dall'altra parte del muro.
        val heights = solids.map { rings -> rings.flatten().let { p -> Math.round(p.minOf { it[2] }) to Math.round(p.maxOf { it[2] }) } }.toSet()
        val wanted = (openings + cameraNiche).map { o -> Math.round(o.bottom) to Math.round(o.bottom + o.height) }.toSet()
        assertEquals(wanted, heights)
    }

    @Test
    fun `andata e ritorno - tutto torna uguale`() {
        val back = IfcImport.read(export()).building.floors.single().plan
        val sog = back.rooms.first { it.id == 1L }
        assertEquals(openings.map { it.id to look(it) }.toMap(), sog.openings.associate { it.id to look(it) })
        val cam = back.rooms.first { it.id == 2L }
        assertEquals(listOf(look(cameraNiche)), cam.openings.map(::look), "la nicchia della camera resta nella camera")
    }

    @Test
    fun `il professionista cambia la stanza - le aperture tengono il loro aspetto`() {
        // Nel progetto di prima il soggiorno era più stretto di 20 cm e le aperture erano tutte «normali».
        val before = soggiorno.copy(
            points = rect(20.0, 0.0, 600.0, 400.0),
            openings = openings.map { Opening(it.id, it.kind, it.wallIndex, it.position, it.width, it.height, it.sillHeight) },
        )
        val merged = IfcRoundTrip.merge(house(listOf(before, camera)), IfcImport.read(export()), key = "porte")
        assertEquals(1, merged.report.reshaped)
        val sog = merged.building.floors.single().plan.rooms.first { it.id == 1L }
        assertEquals(openings.map { it.id to look(it) }.toMap(), sog.openings.associate { it.id to look(it) })
    }

    @Test
    fun `file di un altro programma - il modo di aprirsi si deduce dai nomi standard`() {
        // Senza le proprietà "Sagoma" restano solo i nomi dell'IFC4.
        val text = export().replace("'Sagoma'", "'AltroProgramma'")
        save("porte-nuove-senza-sagoma.ifc", text)
        val r = IfcImport.read(text)
        assertFalse(r.report.fromSagoma)
        val all = r.building.floors.single().plan.rooms.flatMap { room -> room.openings.map { room.id to it } }
        val niches = all.filter { it.second.kind == OpeningKind.Niche }
        assertEquals(2, niches.size)
        // Ogni nicchia va nella stanza dalla cui parte è scavata: una nel soggiorno, una nella camera.
        val sogId = r.building.floors.single().plan.rooms.maxBy { it.points.let { p -> abs(p[0].x - p[1].x) } }.id
        assertEquals(1, niches.count { it.first == sogId })
        val os = all.map { it.second }
        assertTrue(os.any { it.kind == OpeningKind.Door && it.folding && !it.hingeLeft }, "porta a libro")
        assertTrue(os.any { it.kind == OpeningKind.Door && it.sliding }, "porta scorrevole")
        assertTrue(os.any { it.sash == SashType.TiltTurn && !it.hingeLeft }, "anta-ribalta")
        assertTrue(os.any { it.sash == SashType.Hopper }, "vasistas")
        assertEquals(2, os.count { it.sash == SashType.Fixed }, "fisse")
        assertEquals(2, os.count { it.kind == OpeningKind.Passage })
    }

    // ---------- Secondo gruppo: blindata, bilico, sopraluce, 3-4 ante, alzante, finestre ad arco e tonde, oscuranti ----------

    private val group2 = listOf(
        Opening(1, OpeningKind.Window1, 0, 80.0, 60.0, 100.0, sillHeight = 110.0, sash = SashType.Pivot),
        Opening(2, OpeningKind.Window2, 0, 250.0, 180.0, 140.0, sillHeight = 90.0, leafCount = 3),
        Opening(3, OpeningKind.Window1, 0, 450.0, 80.0, 150.0, sillHeight = 80.0, windowShape = WindowShape.Arched),
        Opening(4, OpeningKind.Window1, 0, 600.0, 60.0, 60.0, sillHeight = 130.0, windowShape = WindowShape.Round),
        Opening(5, OpeningKind.Window1, 0, 730.0, 70.0, 140.0, sillHeight = 90.0, shading = Shading.Venetian),
        Opening(6, OpeningKind.Door, 1, 100.0, 90.0, 210.0, pivot = true, doorModel = DoorModel.Armored),
        Opening(7, OpeningKind.Door, 1, 300.0, 120.0, 240.0, transom = 40.0, sidelight = 30.0),
        Opening(8, OpeningKind.Balcony2, 2, 200.0, 240.0, 240.0, leafCount = 4),
        Opening(9, OpeningKind.Balcony2, 2, 550.0, 200.0, 240.0, sliding = true, liftSlide = true),
        Opening(10, OpeningKind.Window1, 2, 720.0, 60.0, 120.0, sillHeight = 90.0, shading = Shading.InteriorShutters, customColor = 0xFF2E5E4E, shadingColor = 0xFF8B2E2E),
    )
    private val big = Room(3, "Salone", RoomType.Soggiorno, rect(0.0, 0.0, 800.0, 500.0), wallThickness = 30.0, openings = group2)

    private fun look2(o: Opening) = look(o) + listOf(o.pivot, o.transom.toInt(), o.sidelight.toInt(), o.leafCount, o.liftSlide, o.windowShape, o.shading, o.doorModel, o.customColor, o.shadingColor)

    @Test
    fun `secondo gruppo - nomi standard e andata e ritorno`() {
        val text = IfcExport.export(house(listOf(big)), "Gruppo 2", key = "g2").also { save("gruppo-2.ifc", it) }
        val f = StepFile.parse(text)
        val typeOf = f.ofType("IFCRELDEFINESBYTYPE").flatMap { r -> r.refs(4).map { it to f[r.ref(5)]!! } }.toMap()
        val windows = f.ofType("IFCWINDOW").associate { w -> w.enum(11) to typeOf[w.id]!!.refs(5).map { f[it]!!.enum(4) } }
        assertEquals(listOf("PIVOTHORIZONTAL"), f.ofType("IFCWINDOW").map { typeOf[it.id]!!.refs(5).map { p -> f[p]!!.enum(4) } }.first { it == listOf("PIVOTHORIZONTAL") })
        assertEquals(3, windows["TRIPLE_PANEL_VERTICAL"]?.size)
        // Balconi: alzante = un'anta che scorre; 4 ante = due ante a battente per l'IFC (il numero vero sta nelle proprietà).
        val doors = f.ofType("IFCDOOR").map { it.enum(11) }.sortedBy { it }
        assertEquals(listOf("DOUBLE_DOOR_SINGLE_SWING", "SINGLE_SWING_LEFT", "SINGLE_SWING_LEFT", "SLIDING_TO_LEFT"), doors)
        // Finestra tonda e ad arco: vani con tanti punti.
        val profiles = f.ofType("IFCOPENINGELEMENT").map { op ->
            val solid = f[f[f[op.ref(6)]!!.refs(2).first()]!!.refs(3).first()]!!
            f[f[solid.ref(0)]!!.ref(2)]!!.refs(0).size - 1
        }
        assertEquals(2, profiles.count { it > 10 }, "profili: $profiles")
        val back = IfcImport.read(text).building.floors.single().plan.rooms.single()
        assertEquals(group2.associate { it.id to look2(it) }, back.openings.associate { it.id to look2(it) })
    }

    @Test
    fun `quattro ante - USERDEFINED con il nome scritto`() {
        val four = big.copy(openings = listOf(Opening(1, OpeningKind.Window2, 0, 300.0, 240.0, 140.0, sillHeight = 90.0, leafCount = 4)))
        val f = StepFile.parse(IfcExport.export(house(listOf(four)), "4 ante", key = "g4"))
        val w = f.ofType("IFCWINDOW").single()
        assertEquals("USERDEFINED", w.enum(11))
        assertEquals("QUADRUPLE_PANEL_VERTICAL", w.str(12))
        val t = f.ofType("IFCWINDOWTYPE").single()
        assertEquals(listOf("LEFT", "MIDDLE", "MIDDLE", "RIGHT"), t.refs(5).map { f[it]!!.enum(5) })
        // Da un altro programma: 4 ante dal tipo.
        val back = IfcImport.read(f.let { IfcExport.export(house(listOf(four)), "4 ante", key = "g4").replace("'Sagoma'", "'Altro'") })
        assertEquals(4, back.building.floors.single().plan.rooms.single().openings.single().leafCount)
    }

    /** Terzo gruppo: finestre e porte nuove della notte del 28/10 (inferriata, sopraluce, bovindo, legno, romana, scorrevoli, vetromattone, garage, tutto vetro, zanzariera, tenda, davanzali, vetro satinato). */
    private val group3 = listOf(
        Opening(1, OpeningKind.Window1, 0, 100.0, 80.0, 120.0, sillHeight = 90.0, grille = true, sillMaterial = StairMaterial.Marble, shading = Shading.SlidingShutters),
        Opening(2, OpeningKind.Window1, 0, 250.0, 80.0, 160.0, sillHeight = 90.0, topLight = true, windowModel = WindowModel.Wood, shading = Shading.RomanShutter, insectScreen = true, frosted = true),
        Opening(3, OpeningKind.Window2, 0, 450.0, 140.0, 140.0, sillHeight = 90.0, bay = true),
        Opening(4, OpeningKind.Window1, 0, 650.0, 76.0, 114.0, sillHeight = 100.0, windowModel = WindowModel.GlassBlock, sash = SashType.Fixed),
        Opening(5, OpeningKind.Door, 2, 150.0, 250.0, 210.0, garage = GarageDoor.UpAndOver),
        Opening(6, OpeningKind.Door, 2, 450.0, 250.0, 215.0, garage = GarageDoor.Sectional),
        Opening(7, OpeningKind.Door, 2, 680.0, 80.0, 210.0, doorModel = DoorModel.AllGlass, sillMaterial = StairMaterial.Stone),
        Opening(8, OpeningKind.Balcony2, 3, 250.0, 120.0, 240.0, awning = true, awningColor = 0xFF9C2F2F, insectScreen = true, sillMaterial = StairMaterial.Wood),
    )

    private fun look3(o: Opening) = look2(o) + listOf(o.grille, o.topLight, o.bay, o.garage, o.insectScreen, o.awning, o.awningColor, o.sillMaterial, o.frosted)

    @Test
    fun `terzo gruppo - garage con i nomi standard, il resto torna uguale`() {
        val house3 = house(listOf(big.copy(openings = group3)))
        val text = IfcExport.export(house3, "Gruppo 3", key = "g3").also { save("gruppo-3.ifc", it) }
        val f = StepFile.parse(text)
        // Sezionale: si arrotola in alto; basculante: USERDEFINED con il suo nome.
        val doors = f.ofType("IFCDOOR").map { it.enum(11) to it.str(12) }
        assertTrue("ROLLINGUP" to null in doors, doors.toString())
        assertTrue("USERDEFINED" to IfcExport.UP_AND_OVER in doors, doors.toString())
        // Il nome dice che cos'è, come nel computo.
        val names = (f.ofType("IFCDOOR") + f.ofType("IFCWINDOW")).mapNotNull { it.str(2) }.toSet()
        assertTrue(names.containsAll(listOf("Porta basculante (garage)", "Porta sezionale (garage)", "Vetromattone", "Bovindo (finestra sporgente)")), names.toString())
        val back = IfcImport.read(text).building.floors.single().plan.rooms.single()
        assertEquals(group3.associate { it.id to look3(it) }, back.openings.associate { it.id to look3(it) })
        // Da un altro programma (senza le proprietà di sagoma) le porte da garage si riconoscono dal modo di aprirsi.
        val other = IfcImport.read(text.replace("'Sagoma'", "'Altro'")).building.floors.single().plan.rooms.single().openings
        assertEquals(setOf(GarageDoor.UpAndOver, GarageDoor.Sectional), other.map { it.garage }.filter { it != GarageDoor.None }.toSet())
    }
}
