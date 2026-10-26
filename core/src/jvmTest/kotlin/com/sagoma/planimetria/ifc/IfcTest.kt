package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IfcTest {
    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))

    /** Soggiorno 4×3 m e camera 3×3 m con il tramezzo da 10 cm in comune, muri esterni da 30; sopra una mansarda. */
    private val soggiorno = Room(
        1, "Soggiorno", RoomType.Soggiorno, rect(0.0, 0.0, 400.0, 300.0), wallThickness = 30.0, wallThicknesses = mapOf(1 to 10.0),
        floorFinish = FloorFinish.LightTiles,
        openings = listOf(
            Opening(1, OpeningKind.Door, wallIndex = 1, position = 150.0, width = 80.0, height = 210.0),
            Opening(2, OpeningKind.Window2, wallIndex = 0, position = 200.0, width = 120.0, height = 140.0, sillHeight = 90.0),
        ),
    )
    private val camera = Room(2, "Camera", RoomType.Camera, rect(400.0, 0.0, 700.0, 300.0), wallThickness = 30.0, wallThicknesses = mapOf(3 to 10.0))
    private val mansarda = Room(1, "Mansarda", RoomType.Studio, rect(0.0, 0.0, 400.0, 300.0), wallThickness = 30.0, ceilingHeight = 240.0)
    private val divano = Furniture(1, "divano-test", Vec2(200.0, 150.0), width = 200.0, depth = 90.0, height = 80.0)
    private val letto = Furniture(2, "letto-test", Vec2(550.0, 150.0), width = 160.0, depth = 200.0, height = 50.0)
    private val scrivania = Furniture(1, "scrivania-test", Vec2(100.0, 100.0), rotation = 30.0, width = 120.0, depth = 60.0, height = 75.0)

    private fun house(ground: List<Room> = listOf(soggiorno, camera), upper: List<Room> = listOf(mansarda)) = Building(
        listOf(
            Floor(1, "Piano terra", FloorPlan(rooms = ground, furniture = listOf(divano, letto)), levelHeight = 300.0),
            Floor(2, "Primo piano", FloorPlan(rooms = upper, furniture = listOf(scrivania)), levelHeight = 270.0),
        ),
    )

    private fun export(b: Building) = IfcExport.export(b, "Casa di prova", key = "prova")

    @Test
    fun globalIdSempreUgualeEValido() {
        val a = IfcGuid.of("prova|room/1/1")
        assertEquals(a, IfcGuid.of("prova|room/1/1"))
        assertTrue(IfcGuid.isValid(a), a)
        assertFalse(a == IfcGuid.of("prova|room/1/2"))
    }

    @Test
    fun testiConAccentiEApici() {
        val w = StepWriter()
        w.add("IFCLABEL", StepWriter.str("Camera dell'òspite – 1°"))
        val e = StepFile.parse(w.text("")).entities.values.single()
        assertEquals("Camera dell'òspite – 1°", e.str(0))
    }

    @Test
    fun esportaSoloLaPlanimetriaNuda() {
        val text = export(house())
        val f = StepFile.parse(text)
        assertEquals(2, f.ofType("IFCBUILDINGSTOREY").size)
        assertEquals(3, f.ofType("IFCSPACE").size)
        // Piano terra: 4 muri del soggiorno + 3 della camera (il tramezzo una volta sola); sopra 4.
        assertEquals(11, f.ofType("IFCWALL").size)
        assertEquals(1, f.ofType("IFCDOOR").size)
        assertEquals(1, f.ofType("IFCWINDOW").size)
        assertEquals(2, f.ofType("IFCOPENINGELEMENT").size)
        // Gli arredi del catalogo non escono mai.
        assertTrue(f.ofType("IFCFURNISHINGELEMENT").isEmpty())
        assertFalse("divano-test" in text || "letto-test" in text || "scrivania-test" in text)
        assertTrue(text.startsWith("ISO-10303-21;") && "FILE_SCHEMA(('IFC4'));" in text)
    }

    @Test
    fun rilettoUgualeAlProgetto() {
        val r = IfcImport.read(export(house()))
        assertTrue(r.report.fromSagoma)
        assertEquals(2, r.building.floors.size)
        val ground = r.building.floors[0].plan
        assertEquals(listOf(1L, 2L), ground.rooms.map { it.id })
        for ((orig, back) in listOf(soggiorno, camera).zip(ground.rooms)) {
            assertEquals(orig.type, back.type)
            assertEquals(orig.interiorArea(), back.interiorArea(), 1.0)
            for (p in orig.points) assertTrue(back.points.any { it.distanceTo(p) < 0.5 }, "${orig.name}: $p non c'è in ${back.points}")
            assertTrue(back.removedWalls.isEmpty(), "${orig.name}: muri eliminati ${back.removedWalls}")
        }
        val s = ground.rooms[0]
        assertEquals(10.0, s.thicknessOf(s.points.indexOfFirst { it.distanceTo(Vec2(400.0, 0.0)) < 0.5 }), 0.5)
        val door = assertNotNull(ground.rooms.flatMap { it.openings }.singleOrNull { it.kind == OpeningKind.Door })
        assertEquals(80.0, door.width, 0.5)
        assertEquals(210.0, door.height, 0.5)
        val window = assertNotNull(s.openings.singleOrNull { it.kind == OpeningKind.Window2 })
        assertEquals(120.0, window.width, 0.5)
        assertEquals(90.0, window.sillHeight, 0.5)
        assertEquals(240.0, r.building.floors[1].plan.rooms.single().ceilingHeight, 0.5)
        assertEquals(300.0, r.building.floors[0].levelHeight, 0.5)
    }

    @Test
    fun ritornoSenzaModificheTieneTutto() {
        val original = house()
        val m = IfcRoundTrip.merge(original, IfcImport.read(export(original)), key = "prova")
        assertEquals(3, m.report.unchanged)
        assertEquals(original.floors.map { it.plan.rooms }, m.building.floors.map { it.plan.rooms })
        assertEquals(original.floors.map { it.plan.furniture }, m.building.floors.map { it.plan.furniture })
        assertTrue(m.report.toCheck.isEmpty())
    }

    @Test
    fun ritornoConStanzeCambiateTieneGliArredi() {
        val original = house()
        // Il professionista allunga la camera di un metro e sposta la mansarda di 1 m a destra e 50 cm in giù.
        val fromPro = house(
            ground = listOf(soggiorno, camera.copy(points = rect(400.0, 0.0, 800.0, 300.0), name = "Camera matrimoniale")),
            upper = listOf(mansarda.copy(points = mansarda.points.map { it + Vec2(100.0, 50.0) })),
        )
        val m = IfcRoundTrip.merge(original, IfcImport.read(export(fromPro)), key = "prova")
        assertEquals(1, m.report.unchanged)
        assertEquals(1, m.report.reshaped)
        assertEquals(1, m.report.moved)
        val ground = m.building.floors[0].plan
        assertEquals("Camera matrimoniale", ground.rooms[1].name)
        assertEquals(800.0, ground.rooms[1].points.maxOf { it.x }, 0.5)
        // Gli arredi restano: il letto dov'era (la camera è cambiata di forma), la scrivania segue la mansarda.
        assertEquals(listOf(divano, letto), ground.furniture)
        val desk = m.building.floors[1].plan.furniture.single()
        assertEquals(200.0, desk.center.x, 0.5)
        assertEquals(150.0, desk.center.y, 0.5)
        assertEquals(30.0, desk.rotation, 0.1)
        assertEquals(FloorFinish.LightTiles, ground.rooms[0].floorFinish)
        assertTrue(m.report.toCheck.isEmpty())
    }

    @Test
    fun arredoRimastoFuoriDaControllare() {
        val original = house()
        // La camera sparisce: il letto resta dov'era ma va controllato.
        val m = IfcRoundTrip.merge(original, IfcImport.read(export(house(ground = listOf(soggiorno)))), key = "prova")
        assertEquals(1, m.report.removed)
        assertEquals(listOf(1L to 2L), m.report.toCheck)
    }

    /** File come lo scriverebbe un altro programma: metri, IFC2X3, muri con profilo rettangolare, una porta. */
    private val otherProgram = """
        ISO-10303-21;
        HEADER;
        FILE_SCHEMA(('IFC2X3'));
        ENDSEC;
        DATA;
        #1=IFCSIUNIT(*,.LENGTHUNIT.,$,.METRE.);
        #2=IFCUNITASSIGNMENT((#1));
        #3=IFCCARTESIANPOINT((0.,0.,0.));
        #4=IFCAXIS2PLACEMENT3D(#3,$,$);
        #5=IFCLOCALPLACEMENT($,#4);
        #6=IFCBUILDINGSTOREY('2O2Fr${'$'}t4X7Zf8NOew3FLOH',$,'Ground Floor',$,$,#5,$,$,.ELEMENT.,0.);
        #7=IFCDIRECTION((0.,0.,1.));
        #10=IFCCARTESIANPOINT((2.,1.5));
        #11=IFCAXIS2PLACEMENT2D(#10,$);
        #12=IFCRECTANGLEPROFILEDEF(.AREA.,$,#11,4.,3.);
        #13=IFCEXTRUDEDAREASOLID(#12,#4,#7,2.7);
        #15=IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#13));
        #16=IFCPRODUCTDEFINITIONSHAPE($,$,(#15));
        #17=IFCSPACE('1kTvXnbbzCWw8lcMd1dR4o',$,'K01','Kitchen',$,#5,#16,'Kitchen',.ELEMENT.,.INTERNAL.,$);
        #20=IFCCARTESIANPOINT((2.,-0.1));
        #21=IFCAXIS2PLACEMENT2D(#20,$);
        #22=IFCRECTANGLEPROFILEDEF(.AREA.,$,#21,4.4,0.2);
        #23=IFCEXTRUDEDAREASOLID(#22,#4,#7,2.7);
        #24=IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#23));
        #25=IFCPRODUCTDEFINITIONSHAPE($,$,(#24));
        #26=IFCWALLSTANDARDCASE('3vB2YO${'$'}MX4xv5uCqZZG05x',$,'Wall S',$,$,#5,#25,$);
        #30=IFCCARTESIANPOINT((2.,3.1));
        #31=IFCAXIS2PLACEMENT2D(#30,$);
        #32=IFCRECTANGLEPROFILEDEF(.AREA.,$,#31,4.4,0.2);
        #33=IFCEXTRUDEDAREASOLID(#32,#4,#7,2.7);
        #34=IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#33));
        #35=IFCPRODUCTDEFINITIONSHAPE($,$,(#34));
        #36=IFCWALLSTANDARDCASE('0BTBFw6f90Nfh9rP1dlXr2',$,'Wall N',$,$,#5,#35,$);
        #40=IFCCARTESIANPOINT((-0.1,1.5));
        #41=IFCAXIS2PLACEMENT2D(#40,$);
        #42=IFCRECTANGLEPROFILEDEF(.AREA.,$,#41,0.2,3.4);
        #43=IFCEXTRUDEDAREASOLID(#42,#4,#7,2.7);
        #44=IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#43));
        #45=IFCPRODUCTDEFINITIONSHAPE($,$,(#44));
        #46=IFCWALLSTANDARDCASE('1hOSvn6df7F8_7GcBWlRGQ',$,'Wall W',$,$,#5,#45,$);
        #50=IFCCARTESIANPOINT((4.1,1.5));
        #51=IFCAXIS2PLACEMENT2D(#50,$);
        #52=IFCRECTANGLEPROFILEDEF(.AREA.,$,#51,0.2,3.4);
        #53=IFCEXTRUDEDAREASOLID(#52,#4,#7,2.7);
        #54=IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#53));
        #55=IFCPRODUCTDEFINITIONSHAPE($,$,(#54));
        #56=IFCWALLSTANDARDCASE('2fUV1hJ4L1ZvAVbdRGOyJ9',$,'Wall E',$,$,#5,#55,$);
        #60=IFCCARTESIANPOINT((1.,-0.1));
        #61=IFCAXIS2PLACEMENT2D(#60,$);
        #62=IFCRECTANGLEPROFILEDEF(.AREA.,$,#61,0.9,0.4);
        #63=IFCEXTRUDEDAREASOLID(#62,#4,#7,2.1);
        #64=IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#63));
        #65=IFCPRODUCTDEFINITIONSHAPE($,$,(#64));
        #66=IFCOPENINGELEMENT('0aB8ckMw97ce1Ilrt9s7jd',$,'Opening',$,$,#5,#65,$);
        #67=IFCRELVOIDSELEMENT('3xqdIIa0r4Vh3Hc0pBfUGl',$,$,$,#26,#66);
        #68=IFCDOOR('1Z8JdXXSv8fh2bVvyzNj6g',$,'Door',$,$,#5,$,$,2.1,0.9);
        #69=IFCRELFILLSELEMENT('2bN4Ly3qn0HQtY3vDsl0bR',$,$,$,#66,#68);
        #70=IFCFURNISHINGELEMENT('0sn3PHYX54WRpWBZDn5Zjc',$,'Table',$,$,#5,#16,$);
        #80=IFCRELCONTAINEDINSPATIALSTRUCTURE('1ovV5OvgL8jfPu7mz7o0Hh',$,$,$,(#26,#36,#46,#56,#68,#70),#6);
        #81=IFCRELAGGREGATES('1J6gqAB2n1zgmIsbc0AaR8',$,$,$,#6,(#17));
        ENDSEC;
        END-ISO-10303-21;
    """.trimIndent()

    @Test
    fun fileDiUnAltroProgramma() {
        val r = IfcImport.read(otherProgram)
        assertFalse(r.report.fromSagoma)
        val room = r.building.floors.single().plan.rooms.single()
        assertEquals(RoomType.Cucina, room.type)
        assertEquals(120000.0, room.interiorArea(), 5.0)
        assertEquals(20.0, room.wallThickness, 0.5)
        assertEquals(270.0, room.ceilingHeight, 0.5)
        assertTrue(room.removedWalls.isEmpty())
        val door = room.openings.single()
        assertEquals(OpeningKind.Door, door.kind)
        assertEquals(90.0, door.width, 0.5)
        val (a, b) = com.sagoma.planimetria.geometry.Openings.span(room, door)
        assertEquals(Vec2(100.0, 10.0).x, ((a + b) / 2.0).x, 0.5)
        assertEquals(10.0, ((a + b) / 2.0).y, 0.5)
        assertEquals(1, r.report.skipped["IFCFURNISHINGELEMENT"])
    }
}
