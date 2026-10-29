package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Controlla la pianta»: trova gli errori di disegno veri e non disturba con le piante giuste. */
class PlanCheckTest {
    @AfterTest
    fun italiano() {
        T.lingua = com.sagoma.planimetria.testi.Italiano
    }

    private fun rect(id: Long, name: String, type: RoomType, w: Double, h: Double, at: Vec2 = Vec2.Zero, thickness: Double = 15.0, openings: List<Opening> = emptyList()) =
        Room(id, name, type, RoomFactory.rectangle(w, h).map { it + at }, wallThickness = thickness, openings = openings)

    /** Soggiorno 400×300 con l'ingresso in alto e la porta verso la cucina 300×300 a destra. */
    private val entrance = Opening.default(1, OpeningKind.Door, 0, 200.0).copy(entrance = true)
    private val inner = Opening.default(2, OpeningKind.Door, 1, 150.0)
    private val living = rect(1, "Soggiorno", RoomType.Soggiorno, 400.0, 300.0, openings = listOf(entrance, inner, Opening.default(3, OpeningKind.Window2, 2, 200.0)))
    private val kitchen = rect(2, "Cucina", RoomType.Cucina, 300.0, 300.0, Vec2(400.0, 0.0), openings = listOf(Opening.default(4, OpeningKind.Window1, 0, 150.0)))

    /** Errori e cose da controllare; i minimi delle norme per la casa hanno le loro prove (in fondo). */
    private fun check(vararg rooms: Room) = PlanCheck.of(Building.single(FloorPlan(rooms.toList()))).filter { !it.kind.rule }
    private fun rules(vararg rooms: Room) = PlanCheck.of(Building.single(FloorPlan(rooms.toList()))).filter { it.kind.rule }
    private fun List<PlanCheck.Issue>.kinds() = map { it.kind }.toSet()

    @Test
    fun `una casa giusta non ha problemi`() {
        assertEquals(emptyList(), check(living, kitchen))
        // Muri uno contro l'altro (facce che si toccano): come un muro solo più spesso, va bene.
        assertTrue(check(living, kitchen.moved(15.0)).none { it.kind == PlanCheck.Kind.NearWalls })
    }

    private fun Room.moved(dx: Double, dy: Double = 0.0) = copy(points = points.map { it + Vec2(dx, dy) })

    @Test
    fun `stanze sovrapposte`() {
        val issues = check(living, kitchen.moved(-100.0))
        val o = issues.single { it.kind == PlanCheck.Kind.Overlap }
        // Contorni interni: x da 307,5 a 392,5 (85 cm) per y da 7,5 a 292,5 (285 cm).
        assertEquals(85.0 * 285.0 / 10_000, o.a, 0.01)
        assertEquals(listOf(2L, 1L), o.rooms, "a fuoco la più piccola")
        assertEquals("«Cucina» e «Soggiorno» si sovrappongono per 2,42 m²: quella parte conta due volte. Sposta un muro o un angolo.", PlanCheck.describe(o, Building.single(FloorPlan(listOf(living, kitchen)))))
        assertTrue(o.area.minX <= 307.5 && o.area.maxX >= 392.5)
        // Una stanza disegnata dentro un'altra.
        val closet = rect(3, "Ripostiglio", RoomType.Altro, 100.0, 100.0, Vec2(100.0, 100.0), openings = listOf(Opening.default(5, OpeningKind.Door, 0, 50.0)))
        assertEquals(listOf(3L, 1L), check(living, kitchen, closet).single { it.kind == PlanCheck.Kind.Overlap }.rooms)
    }

    @Test
    fun `Correggi - stanza sovrapposta spostata fuori`() {
        // La cucina trascinata di un metro dentro il soggiorno, con la sua plafoniera: torna al muro in comune, con la luce.
        val lamp = Fixture.default(9, FixtureKind.CeilingLight).copy(point = Vec2(450.0, 150.0))
        val over = kitchen.copy(fixtures = listOf(lamp)).moved(-100.0)
        val b = Building.single(FloorPlan(listOf(living, over)))
        val o = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.Overlap }
        assertTrue(PlanCheck.triesFirst(o))
        assertTrue(PlanCheck.canFix(b, o))
        val back = assertNotNull(PlanCheck.fixed(b, o)).floors[0].plan.rooms
        assertEquals(living, back[0])
        assertEquals(kitchen.points, back[1].points)
        assertEquals(Vec2(550.0, 150.0), back[1].fixtures.single().point)
        assertEquals(emptyList(), check(*back.toTypedArray()))
        // Già corretto (l'elenco era vecchio): niente.
        assertNull(PlanCheck.fixed(Building.single(FloorPlan(back)), o))
        // Con il bagno attaccato alla cucina dall'altra parte (cucina e bagno stanno insieme): si sposta il soggiorno.
        val bath = rect(3, "Bagno", RoomType.Bagno, 200.0, 300.0, Vec2(600.0, 0.0))
        val three = Building.single(FloorPlan(listOf(living, over, bath)))
        val o3 = PlanCheck.of(three).single { it.kind == PlanCheck.Kind.Overlap }
        val left = assertNotNull(PlanCheck.fixed(three, o3)).floors[0].plan.rooms
        assertEquals(-100.0, left[0].points.minOf { it.x }, 1e-9)
        assertEquals(300.0, left[0].points.maxOf { it.x }, 1e-9)
        assertEquals(listOf(over, bath), left.drop(1))
        assertTrue(check(*left.toTypedArray()).none { it.kind.error })
        // E con un ripostiglio attaccato al soggiorno a sinistra nessuna delle due si può spostare: la cucina si accorcia fino al muro.
        val store = rect(4, "Ripostiglio", RoomType.Altro, 100.0, 300.0, Vec2(-100.0, 0.0))
        val four = Building.single(FloorPlan(listOf(living, over, bath, store)))
        val o4 = PlanCheck.of(four).single { it.kind == PlanCheck.Kind.Overlap }
        val shrunk = assertNotNull(PlanCheck.fixed(four, o4)).floors[0].plan.rooms
        assertEquals(living, shrunk[0])
        assertEquals(400.0, shrunk[1].points.minOf { it.x }, 1e-9)
        assertEquals(600.0, shrunk[1].points.maxOf { it.x }, 1e-9)
        assertTrue(check(*shrunk.toTypedArray()).none { it.kind.error })
        // Sovrapposta in un angolo: si sposta dalla parte dove va meno (qui in giù di 40 cm, non a destra di 100).
        val corner = Building.single(FloorPlan(listOf(living, kitchen.moved(-100.0, 260.0))))
        val oc = PlanCheck.of(corner).single { it.kind == PlanCheck.Kind.Overlap }
        val down = assertNotNull(PlanCheck.fixed(corner, oc)).floors[0].plan.rooms[1]
        assertEquals(300.0, down.points.minOf { it.y }, 1e-9)
        assertEquals(300.0, down.points.minOf { it.x }, 1e-9)
        // Il ripostiglio disegnato dentro il soggiorno: non si sa dove va, niente «Correggi».
        val closet = rect(3, "Ripostiglio", RoomType.Altro, 100.0, 100.0, Vec2(100.0, 100.0), openings = listOf(Opening.default(5, OpeningKind.Door, 0, 50.0)))
        val inside = Building.single(FloorPlan(listOf(living, kitchen, closet)))
        val oi = PlanCheck.of(inside).single { it.kind == PlanCheck.Kind.Overlap }
        assertNull(PlanCheck.fixed(inside, oi))
        assertEquals(false, PlanCheck.canFix(inside, oi))
    }

    @Test
    fun `stanze quasi attaccate - fessura o muri accavallati`() {
        // Linee a 20 cm: muri da 15, tra le facce 5 cm di fessura.
        val slit = check(living, kitchen.moved(20.0)).single { it.kind == PlanCheck.Kind.NearWalls }
        assertEquals(5.0, slit.a, 1e-9)
        assertEquals(Selection.Wall(2, 3), slit.selection, "il muro della stanza più piccola")
        assertTrue(PlanCheck.describe(slit, Building.single(FloorPlan(listOf(living, kitchen)))).contains("fessura di 5 cm"))
        // Muri da 30 a 20 cm: si accavallano per 10 cm.
        val thick = check(living.copy(wallThickness = 30.0), kitchen.copy(wallThickness = 30.0).moved(20.0)).single { it.kind == PlanCheck.Kind.NearWalls }
        assertEquals(-10.0, thick.a, 1e-9)
        assertTrue(PlanCheck.describe(thick, Building.single(FloorPlan(listOf(living, kitchen)))).contains("si accavallano per 10 cm"))
        // «Correggi»: il muro della cucina va sulla linea di quello del soggiorno e diventa un muro in comune (la cucina si
        // allarga di 20 cm verso il soggiorno; il soggiorno non cambia).
        for ((issue, rooms) in listOf(slit to listOf(living, kitchen.moved(20.0)), thick to listOf(living.copy(wallThickness = 30.0), kitchen.copy(wallThickness = 30.0).moved(20.0)))) {
            assertTrue(PlanCheck.canFix(issue))
            val joined = assertNotNull(PlanCheck.fixed(Building.single(FloorPlan(rooms)), issue)).floors[0].plan.rooms
            assertEquals(rooms[0], joined[0])
            assertEquals(400.0, joined[1].points.minOf { it.x }, 1e-9)
            assertEquals(720.0, joined[1].points.maxOf { it.x }, 1e-9)
            assertTrue(check(*joined.toTypedArray()).none { it.kind == PlanCheck.Kind.NearWalls || it.kind == PlanCheck.Kind.Overlap })
        }
        // Entro la tolleranza dei muri in comune (12 cm) il muro è uno solo: niente da dire.
        assertTrue(check(living, kitchen.moved(10.0)).none { it.kind == PlanCheck.Kind.NearWalls })
        // Lontane: niente.
        assertTrue(check(living, kitchen.moved(80.0)).none { it.kind == PlanCheck.Kind.NearWalls })
    }

    @Test
    fun `muri incrociati e muro lungo zero`() {
        val bow = Room(3, "Camera", RoomType.Camera, listOf(Vec2(0.0, 0.0), Vec2(300.0, 300.0), Vec2(300.0, 0.0), Vec2(0.0, 300.0)), openings = listOf(Opening.default(9, OpeningKind.Door, 2, 100.0)))
        val crossed = check(bow.moved(2000.0)).first { it.kind == PlanCheck.Kind.CrossedWalls }
        assertEquals(Vec2(2150.0, 150.0), crossed.area.center)
        val dup = living.copy(points = living.points.take(2) + living.points[1] + living.points.drop(2))
        val zero = check(dup, kitchen).single { it.kind == PlanCheck.Kind.ZeroWall }
        assertEquals(Selection.Wall(1, 1), zero.selection)
        assertTrue(PlanCheck.describe(zero, Building.single(FloorPlan(listOf(dup)))).contains("«Unisci al muro dopo»"))
        // La porta sul muro lungo zero è anche «troppo larga»: lì si corregge il muro, non la porta.
        assertTrue(!PlanCheck.canFix(check(dup, kitchen).single { it.kind == PlanCheck.Kind.OpeningTooWide }))
        // «Correggi»: l'angolo doppio sparisce e la stanza torna com'era.
        assertTrue(PlanCheck.canFix(zero))
        val clean = dup.copy(openings = living.openings.map { if (it.wallIndex >= 1) it.copy(wallIndex = it.wallIndex + 1) else it })
        val z = check(clean, kitchen).single { it.kind == PlanCheck.Kind.ZeroWall }
        assertEquals(living, assertNotNull(PlanCheck.fixed(Building.single(FloorPlan(listOf(clean, kitchen))), z)).floors[0].plan.rooms[0])
        // Qui invece la porta del muro lungo zero passerebbe sul muro dopo, sopra la finestra: due aperture nello stesso punto,
        // un errore nuovo. Allora niente (si sistema a mano).
        assertNull(PlanCheck.fixed(Building.single(FloorPlan(listOf(dup, kitchen))), zero))
    }

    @Test
    fun `Correggi - porta alla stanza senza porta`() {
        // Corridoio in alto (con l'ingresso); sotto il bagno senza porta e la camera con la porta sul corridoio.
        val hall = rect(1, "Corridoio", RoomType.Corridoio, 400.0, 120.0, openings = listOf(Opening.default(1, OpeningKind.Door, 1, 60.0).copy(entrance = true)))
        val bath = rect(2, "Bagno", RoomType.Bagno, 200.0, 200.0, Vec2(0.0, 120.0))
        val bedroom = rect(3, "Camera", RoomType.Camera, 200.0, 200.0, Vec2(200.0, 120.0), openings = listOf(Opening.default(2, OpeningKind.Door, 0, 100.0)))
        val b = Building.single(FloorPlan(listOf(hall, bath, bedroom)))
        val i = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.NoDoor }
        assertEquals(listOf(2L), i.rooms)
        assertTrue(PlanCheck.triesFirst(i))
        assertTrue(PlanCheck.canFix(b, i))
        // La porta va sul muro in comune con il corridoio, nel mezzo; nient'altro cambia.
        val fixed = assertNotNull(PlanCheck.fixed(b, i)).floors[0].plan
        val door = fixed.rooms[1].openings.single()
        assertEquals(OpeningKind.Door, door.kind)
        assertEquals(0, door.wallIndex)
        assertEquals(100.0, door.position, 1e-9)
        assertEquals(3L, door.id)
        assertEquals(listOf(hall, bedroom), listOf(fixed.rooms[0], fixed.rooms[2]))
        assertTrue(PlanCheck.of(Building.single(fixed)).none { it.kind == PlanCheck.Kind.NoDoor || it.kind.error })
        // Già corretto: niente.
        assertNull(PlanCheck.fixed(Building.single(fixed), i))
        // Una nicchia nel mezzo del muro verso il corridoio: lì la porta non ci sta, va verso la camera.
        val niche = bath.copy(openings = listOf(Opening.default(9, OpeningKind.Niche, 0, 100.0)))
        val b2 = Building.single(FloorPlan(listOf(hall, niche, bedroom)))
        val toBedroom = assertNotNull(PlanCheck.fixed(b2, PlanCheck.of(b2).single { it.kind == PlanCheck.Kind.NoDoor })).floors[0].plan.rooms[1].openings.last()
        assertEquals(1, toBedroom.wallIndex)
        // Una stanza da sola, senza muri in comune: non si sa dove mettere la porta, niente «Correggi».
        val alone = rect(4, "Studio", RoomType.Studio, 300.0, 300.0, Vec2(1000.0, 0.0))
        val b3 = Building.single(FloorPlan(listOf(hall, bath.copy(openings = listOf(Opening.default(5, OpeningKind.Door, 0, 100.0))), bedroom, alone)))
        val i3 = PlanCheck.of(b3).single { it.kind == PlanCheck.Kind.NoDoor }
        assertEquals(listOf(4L), i3.rooms)
        assertNull(PlanCheck.fixed(b3, i3))
        assertEquals(false, PlanCheck.canFix(b3, i3))
    }

    @Test
    fun `porte e finestre doppie, troppo larghe, oltre il muro, su un muro tolto`() {
        // La stessa porta messa anche dalla cucina.
        val twice = kitchen.copy(openings = kitchen.openings + Opening.default(6, OpeningKind.Door, 3, 150.0))
        val d = check(living, twice).single { it.kind == PlanCheck.Kind.DoubleOpening }
        assertEquals(Selection.Opening(2, 6), d.selection)
        // «Correggi» toglie la seconda (quella della cucina); la prima resta.
        assertTrue(PlanCheck.canFix(d))
        val once = assertNotNull(PlanCheck.fixed(Building.single(FloorPlan(listOf(living, twice))), d)).floors[0].plan
        assertEquals(kitchen.openings, once.rooms[1].openings)
        assertEquals(living.openings, once.rooms[0].openings)
        // Una porta e una finestra nello stesso punto: quale togliere lo sceglie chi disegna.
        val mixed = kitchen.copy(openings = kitchen.openings + Opening.default(6, OpeningKind.Window1, 3, 150.0).copy(sillHeight = 0.0, height = 200.0))
        val dm = check(living, mixed).single { it.kind == PlanCheck.Kind.DoubleOpening }
        assertTrue(!PlanCheck.canFix(dm))
        // Finestra sopra la porta (sopraluce): non è doppia.
        val above = kitchen.copy(openings = kitchen.openings + Opening(6, OpeningKind.Window1, 3, 150.0, 80.0, 40.0, sillHeight = 220.0))
        assertTrue(check(living, above).none { it.kind == PlanCheck.Kind.DoubleOpening })

        val small = rect(5, "Bagno", RoomType.Bagno, 100.0, 200.0, Vec2(0.0, 300.0), openings = listOf(Opening(7, OpeningKind.Door, 0, 50.0, 140.0, 210.0)))
        val wide = check(living, kitchen, small).single { it.kind == PlanCheck.Kind.OpeningTooWide }
        assertEquals(140.0, wide.a)
        assertEquals(100.0, wide.b)

        val tall = living.copy(openings = living.openings + Opening(8, OpeningKind.Window1, 3, 150.0, 80.0, 200.0, sillHeight = 100.0))
        val over = check(tall, kitchen).single { it.kind == PlanCheck.Kind.OpeningOverWall }
        assertEquals(300.0, over.a)
        assertEquals(270.0, over.b)
        assertTrue(PlanCheck.describe(over, Building.single(FloorPlan(listOf(tall)))).contains("arriva a 300 cm da terra, ma lì il muro è alto 270 cm"))

        val open = living.copy(removedWalls = setOf(2))
        val removed = check(open, kitchen).single { it.kind == PlanCheck.Kind.OpeningOnRemovedWall }
        assertEquals(Selection.Opening(1, 3), removed.selection)
        // «Correggi»: la finestra sul lato aperto (che non c'è) si toglie; le altre aperture restano.
        assertTrue(PlanCheck.canFix(removed))
        val fixed = assertNotNull(PlanCheck.fixed(Building.single(FloorPlan(listOf(open, kitchen))), removed)).floors[0].plan.rooms[0]
        assertEquals(listOf(1L, 2L), fixed.openings.map { it.id })
        assertTrue(PlanCheck.of(Building.single(FloorPlan(listOf(fixed, kitchen)))).none { it.kind == PlanCheck.Kind.OpeningOnRemovedWall })
    }

    @Test
    fun `stanze senza porta e casa senza ingresso`() {
        val closed = kitchen.copy(openings = kitchen.openings)
        val noInner = living.copy(openings = listOf(entrance))
        assertEquals(listOf(2L), check(noInner, closed).filter { it.kind == PlanCheck.Kind.NoDoor }.map { it.rooms.single() })
        // La porta della cucina verso il soggiorno basta a tutte e due.
        val fromKitchen = closed.copy(openings = closed.openings + Opening.default(6, OpeningKind.Door, 3, 150.0))
        assertTrue(check(noInner, fromKitchen).none { it.kind == PlanCheck.Kind.NoDoor })
        // Un lato aperto (muro tolto da tutte e due, come fa l'editor) collega; tolto da una parte sola, il muro dell'altra resta.
        assertTrue(check(noInner.copy(removedWalls = setOf(1)), closed.copy(removedWalls = setOf(3))).none { it.kind == PlanCheck.Kind.NoDoor })
        assertEquals(listOf(2L), check(noInner.copy(removedWalls = setOf(1)), closed).filter { it.kind == PlanCheck.Kind.NoDoor }.map { it.rooms.single() })
        // Nessuna porta in tutto il piano (pianta appena ricalcata): un avviso solo, e non quello dell'ingresso.
        val bare = check(living.copy(openings = emptyList()), kitchen.copy(openings = emptyList()))
        assertEquals(listOf(PlanCheck.Kind.NoDoors), bare.map { it.kind })
        // Nessuna porta dà fuori: manca l'ingresso. Il balcone conta come fuori.
        val noEntrance = check(living.copy(openings = listOf(inner)), kitchen)
        assertTrue(PlanCheck.Kind.NoEntrance in noEntrance.kinds())
        val balcony = rect(6, "Balcone", RoomType.Balcone, 400.0, 150.0, Vec2(0.0, -150.0))
        assertTrue(PlanCheck.Kind.NoEntrance !in check(living.copy(openings = listOf(inner, Opening.default(1, OpeningKind.Door, 0, 200.0))), kitchen, balcony).kinds())
        // Finestra verso un'altra stanza.
        val window = kitchen.copy(openings = listOf(Opening.default(6, OpeningKind.Window1, 3, 60.0)))
        val w = check(living, window).single { it.kind == PlanCheck.Kind.InnerWindow }
        assertEquals(listOf(2L, 1L), w.rooms)
        // Stanza minuscola (60 × 60 con muri da 15: 0,20 m² dentro).
        val tiny = rect(7, "Cavedio", RoomType.Altro, 60.0, 60.0, Vec2(1000.0, 0.0), openings = listOf(Opening.default(8, OpeningKind.Passage, 0, 30.0).copy(width = 30.0)))
        val t = check(living, kitchen, tiny).single { it.kind == PlanCheck.Kind.TinyRoom }
        assertEquals(0.2025, t.a, 1e-6)
        // «Correggi» la toglie (un pezzo rimasto); da sola nel piano no.
        assertTrue(PlanCheck.canFix(t))
        assertEquals(listOf(living, kitchen), assertNotNull(PlanCheck.fixed(Building.single(FloorPlan(listOf(living, kitchen, tiny))), t)).floors[0].plan.rooms)
        val onlyTiny = Building.single(FloorPlan(listOf(tiny)))
        assertNull(PlanCheck.fixed(onlyTiny, PlanCheck.of(onlyTiny).single { it.kind == PlanCheck.Kind.TinyRoom }))
    }

    @Test
    fun `scale che non arrivano`() {
        // Salone 800 × 600 con la scala al centro (sta tutta dentro, qualunque sia il verso).
        val hall = rect(1, "Salone", RoomType.Soggiorno, 800.0, 600.0, openings = listOf(entrance))
        val stair = Stair(1, StairKind.Straight, Vec2(400.0, 300.0))
        val one = PlanCheck.of(Building.single(FloorPlan(listOf(hall), stairs = listOf(stair))))
        assertEquals(Selection.Stair(1), one.single { it.kind == PlanCheck.Kind.StairNoFloor }.selection)
        // Con le scale la casa può essere disegnata solo in parte: niente avviso per l'ingresso.
        assertTrue(PlanCheck.Kind.NoEntrance !in PlanCheck.of(Building.single(FloorPlan(listOf(hall.copy(openings = emptyList())), stairs = listOf(stair)))).kinds())
        // Al piano di sopra c'è solo una camera lontana: la scala arriva nel vuoto.
        val far = rect(9, "Camera", RoomType.Camera, 300.0, 300.0, Vec2(1000.0, 0.0))
        val two = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(hall), stairs = listOf(stair))), Floor(2, "Primo piano", FloorPlan(listOf(far)))))
        val out = PlanCheck.of(two).single { it.kind == PlanCheck.Kind.StairOutside }
        assertEquals("La scala arriva fuori dalle stanze del piano di sopra (Primo piano).", PlanCheck.describe(out, two))
        // Con il salone anche di sopra va bene, e il salone di sopra (senza porte) ha la scala per entrare; la camera no.
        val study = rect(10, "Studio", RoomType.Studio, 300.0, 300.0, Vec2(1000.0, 400.0), openings = listOf(Opening.default(11, OpeningKind.Door, 0, 150.0)))
        val ok = two.copy(floors = listOf(two.floors[0], two.floors[1].copy(plan = FloorPlan(listOf(hall.copy(openings = emptyList()), far, study)))))
        val issues = PlanCheck.of(ok)
        assertTrue(issues.none { it.kind == PlanCheck.Kind.StairOutside || it.kind == PlanCheck.Kind.StairNoFloor }, "$issues")
        assertEquals(listOf(9L), issues.filter { it.kind == PlanCheck.Kind.NoDoor }.map { it.rooms.single() })
    }

    @Test
    fun `prima gli errori e mai troppi, anche con una pianta assurda`() {
        val many = (1..600L).map { rect(it, "S$it", RoomType.Camera, 300.0, 300.0, Vec2((it % 7) * 10.0, (it % 5) * 10.0)) }
        val t0 = System.nanoTime()
        val issues = PlanCheck.of(Building.single(FloorPlan(many)))
        val ms = (System.nanoTime() - t0) / 1e6
        assertEquals(PlanCheck.MAX_ISSUES, issues.size)
        assertTrue(ms < 5_000, "$ms ms")
        val kinds = issues.map { it.kind.error }
        assertEquals(kinds.sortedDescending(), kinds, "gli errori vengono prima")
        // Numeri impossibili: nessun errore, si salta la stanza.
        val nan = living.copy(points = living.points.map { if (it.x > 0) Vec2(Double.NaN, it.y) else it })
        check(nan, kitchen)
        check(living.copy(wallThickness = Double.POSITIVE_INFINITY), kitchen.copy(points = kitchen.points.map { it * 1e12 }))
    }
    @Test
    fun `norme per la casa - finestre, altezze e metri quadri`() {
        val window = Opening.default(10, OpeningKind.Window2, 2, 200.0)
        // Camera 400×400 (14,82 m² dentro i muri) con una finestra 120×120 (1,44 m²): 1/10,3, ne servono 1,85.
        val bedroom = rect(1, "Camera", RoomType.Camera, 400.0, 400.0, openings = listOf(Opening.default(1, OpeningKind.Door, 0, 200.0).copy(entrance = true), window))
        val few = rules(bedroom).single()
        assertEquals(PlanCheck.Kind.FewWindows, few.kind)
        val b = Building.single(FloorPlan(listOf(bedroom)))
        assertEquals(
            "«Camera»: finestre apribili di 1,44 m² per 14,82 m² di pavimento (1/10,3). Per una stanza da abitare serve almeno 1/8, cioè 1,85 m².",
            PlanCheck.describe(few, b),
        )
        // Finestra più alta (120×160 = 1,92 m²): va bene.
        assertEquals(emptyList(), rules(bedroom.copy(openings = listOf(window.copy(height = 160.0)))))
        // Senza finestre, soffitto basso, troppo piccola.
        val study = rect(2, "Studio", RoomType.Studio, 300.0, 300.0, Vec2(400.0, 0.0), openings = listOf(Opening.default(2, OpeningKind.Door, 3, 150.0)))
        val dark = rules(bedroom.copy(openings = listOf(window.copy(height = 160.0))), study).single()
        assertEquals(PlanCheck.Kind.NoWindows, dark.kind)
        assertTrue(PlanCheck.describe(dark, Building.single(FloorPlan(listOf(study)))).startsWith("«Studio» non ha finestre apribili che danno fuori"))
        val low = rules(bedroom.copy(ceilingHeight = 255.0, openings = listOf(window.copy(height = 160.0)))).single()
        assertEquals(PlanCheck.Kind.LowCeiling, low.kind)
        assertEquals("«Camera»: soffitto a 2,55 m; nelle stanze da abitare deve essere almeno a 2,70 m.", PlanCheck.describe(low, b))
        val small = rect(3, "Cameretta", RoomType.Cameretta, 300.0, 300.0, openings = listOf(Opening.default(3, OpeningKind.Window2, 2, 150.0)))
        val tiny = rules(small).single()
        assertEquals(PlanCheck.Kind.SmallBedroom, tiny.kind)
        assertEquals("«Cameretta»: 8,12 m²; una camera da letto deve averne almeno 9 (14 se è per due persone).", PlanCheck.describe(tiny, Building.single(FloorPlan(listOf(small)))))
        // Bagni e corridoi: 2,40 m bastano, e niente finestre o metri quadri minimi.
        val bath = rect(4, "Bagno", RoomType.Bagno, 200.0, 250.0)
        assertEquals(emptyList(), rules(bath.copy(ceilingHeight = 240.0)))
        val lowBath = rules(bath.copy(ceilingHeight = 230.0)).single()
        assertEquals("«Bagno»: soffitto a 2,30 m; in bagni e corridoi deve essere almeno a 2,40 m.", PlanCheck.describe(lowBath, Building.single(FloorPlan(listOf(bath)))))
        // Mansarda (pareti tagliate dal tetto): non conta il soffitto più alto ma la media (prova sotto).
        assertTrue(rules(bedroom.copy(ceilingHeight = 200.0, wallCuts = mapOf(0 to com.sagoma.planimetria.model.WallCut(true, 100.0)), openings = listOf(window.copy(height = 160.0)))).none { it.kind == PlanCheck.Kind.LowCeiling })
        // Le norme vengono dopo gli errori e dopo le cose da controllare.
        val all = PlanCheck.of(Building.single(FloorPlan(listOf(bedroom, study, bedroom.copy(id = 9).moved(-100.0)))))
        val order = all.map { if (it.kind.error) 0 else if (!it.kind.rule) 1 else 2 }
        assertEquals(order.sorted(), order)
        assertTrue(2 in order && 0 in order)
    }

    @Test
    fun `norme - soggiorno con la cucina a vista e stanze da demolire`() {
        // Soggiorno 400×300 e cucina 300×300 senza muro in mezzo: un ambiente solo di 19,10 m², e le finestre si sommano.
        val livingOpen = living.copy(removedWalls = setOf(1), openings = listOf(entrance, Opening.default(3, OpeningKind.Window2, 2, 200.0)))
        val kitchenOpen = kitchen.copy(removedWalls = setOf(3), openings = listOf(Opening.default(4, OpeningKind.Window2, 0, 150.0)))
        val issues = rules(livingOpen, kitchenOpen)
        // 2,88 m² di finestre per 19,10 m²: basta (1/6,6); il soggiorno è piccolo anche con la cucina (meno di 14 m²)? No: 19,10.
        assertEquals(emptyList(), issues, "$issues")
        // Muro tolto solo dal soggiorno: la cucina ha ancora il suo, due stanze separate. Il soggiorno da solo è sotto i 14 m².
        val apart = rules(livingOpen, kitchenOpen.copy(removedWalls = emptySet()))
        assertEquals(listOf(PlanCheck.Kind.SmallLiving), apart.map { it.kind })
        // Senza la finestra della cucina: l'ambiente ne ha 1,44 m² su 19,10 (servono 2,39), un avviso solo con tutte e due le stanze.
        val dim = rules(livingOpen, kitchenOpen.copy(openings = emptyList())).single()
        assertEquals(PlanCheck.Kind.FewWindows, dim.kind)
        assertEquals(listOf(1L, 2L), dim.rooms)
        assertTrue(PlanCheck.describe(dim, Building.single(FloorPlan(listOf(livingOpen, kitchenOpen)))).startsWith("«Soggiorno» + «Cucina»: finestre apribili di 1,44 m² per 19,10 m²"))
        // Nessuna finestra in tutto il piano (pianta appena letta): un avviso solo, non uno per stanza.
        val bare = rules(livingOpen.copy(openings = listOf(entrance)), kitchenOpen.copy(openings = emptyList(), removedWalls = emptySet()))
        assertEquals(listOf(PlanCheck.Kind.NoFloorWindows, PlanCheck.Kind.SmallLiving), bare.map { it.kind })
        assertEquals(emptyList(), bare.first().rooms)
        // Stanza tutta da demolire: non c'è più, niente da dire.
        val gone = rect(5, "Camera", RoomType.Camera, 250.0, 250.0, Vec2(0.0, 400.0)).let { r -> r.copy(wallPhases = (0 until r.wallCount).associateWith { com.sagoma.planimetria.model.Phase.Demolish }) }
        assertTrue(rules(gone).isEmpty())
        // In francese i nomi tra « » con gli spazi.
        T.lingua = com.sagoma.planimetria.testi.fr.Francese
        assertTrue(PlanCheck.describe(dim, Building.single(FloorPlan(listOf(livingOpen, kitchenOpen)))).startsWith("« Soggiorno » + « Cucina » : fenêtres ouvrantes de 1,44 m²"))
    }

    @Test
    fun `norme - camera per due e bagno senza finestra`() {
        val window = Opening.default(1, OpeningKind.Window2, 2, 150.0).copy(height = 160.0)
        // Camera 380×380 (13,32 m² dentro i muri da 15): basta per una persona, non per due.
        val bedroom = rect(1, "Camera", RoomType.Camera, 380.0, 380.0, openings = listOf(window))
        val double = com.sagoma.planimetria.model.Furniture(1, "mobile:letto:rovere", Vec2(190.0, 150.0), width = 160.0, depth = 205.0, height = 100.0)
        val single = double.copy(model = "mobile:letto-singolo:rovere", width = 90.0)
        fun plan(vararg f: com.sagoma.planimetria.model.Furniture) = FloorPlan(listOf(bedroom), furniture = f.toList())
        fun rulesOf(p: FloorPlan) = PlanCheck.of(Building.single(p)).filter { it.kind.rule }
        assertEquals(emptyList(), rulesOf(plan(single)))
        val i = rulesOf(plan(double)).single()
        assertEquals(PlanCheck.Kind.SmallBedroom, i.kind)
        assertEquals("«Camera»: 13,32 m² con i letti per due persone; una camera per due deve averne almeno 14.", PlanCheck.describe(i, Building.single(plan(double))))
        // Due letti singoli: due persone. Un letto fuori dalla stanza non conta; un comodino non è un letto.
        assertEquals(PlanCheck.Kind.SmallBedroom, rulesOf(plan(single, single.copy(id = 2, center = Vec2(300.0, 150.0)))).single().kind)
        assertEquals(emptyList(), rulesOf(plan(single, single.copy(id = 2, center = Vec2(600.0, 150.0)))))
        assertEquals(emptyList(), rulesOf(plan(single, single.copy(id = 2, model = "mobile:comodino:rovere"))))
        // Bagno senza finestra verso fuori (la casa ha già le finestre): serve l'aspirazione.
        val bath = rect(2, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(380.0, 0.0))
        val b = Building.single(FloorPlan(listOf(bedroom, bath)))
        val v = PlanCheck.of(b).single { it.kind.rule }
        assertEquals(PlanCheck.Kind.BathNoWindow, v.kind)
        assertEquals("«Bagno»: nessuna finestra apribile verso fuori; il bagno ha bisogno dell'aspirazione meccanica (e niente apparecchi a fiamma libera).", PlanCheck.describe(v, b))
        // Con una finestra apribile niente; con una finestra fissa sì.
        val withWindow = bath.copy(openings = listOf(Opening.default(2, OpeningKind.Window1, 0, 100.0)))
        assertTrue(PlanCheck.of(Building.single(FloorPlan(listOf(bedroom, withWindow)))).none { it.kind == PlanCheck.Kind.BathNoWindow })
        val fixed = bath.copy(openings = listOf(Opening.default(2, OpeningKind.Window1, 0, 100.0).copy(sash = com.sagoma.planimetria.model.SashType.Fixed)))
        assertTrue(PlanCheck.of(Building.single(FloorPlan(listOf(bedroom, fixed)))).any { it.kind == PlanCheck.Kind.BathNoWindow })
    }

    @Test
    fun `mobili dentro i muri`() {
        val saved = com.sagoma.planimetria.model.FurnitureCatalog.items
        try {
            com.sagoma.planimetria.model.FurnitureCatalog.set(listOf(com.sagoma.planimetria.model.FurnitureCatalog.Item(model = "armadio", label = "Armadio", category = "Camera", width = 100.0, depth = 60.0, height = 220.0)))
            // Armadio 100×60 contro il muro 0 del soggiorno (mezzeria y = 0, faccia a 7,5), lontano dalla porta d'ingresso (x 160-240).
            fun at(y: Double, height: Double = 220.0, x: Double = 100.0, w: Double = 100.0) =
                com.sagoma.planimetria.model.Furniture(7, "armadio", Vec2(x, y), width = w, depth = 60.0, height = height)
            fun issues(f: com.sagoma.planimetria.model.Furniture): Pair<Building, List<PlanCheck.Issue>> {
                val b = Building.single(FloorPlan(listOf(living, kitchen), furniture = listOf(f)))
                return b to PlanCheck.of(b).filter { it.kind == PlanCheck.Kind.FurnitureInWall }
            }
            // Appoggiato alla parete, o dentro di 2 cm: va bene.
            assertEquals(emptyList(), issues(at(37.5)).second)
            assertEquals(emptyList(), issues(at(35.5)).second)
            // Dentro di 5 cm.
            val (b, list) = issues(at(32.5))
            val i = list.single()
            assertEquals(com.sagoma.planimetria.editor.Selection.Furniture(7), i.selection)
            assertEquals(listOf(1L), i.rooms)
            assertTrue(!i.kind.error && !i.kind.rule)
            assertEquals("«Armadio» entra di 5 cm in un muro: spostalo o rimpiccioliscilo.", PlanCheck.describe(i, b))
            // A cavallo del muro.
            val (b2, across) = issues(at(0.0))
            assertEquals("«Armadio» attraversa un muro: spostalo dentro la stanza o rimpiccioliscilo.", PlanCheck.describe(across.single(), b2))
            // Nel vano della porta non è dentro il muro; un tappeto non conta.
            assertEquals(emptyList(), issues(at(0.0, x = 200.0, w = 40.0)).second)
            assertEquals(emptyList(), issues(at(0.0, height = 1.0)).second)
        } finally {
            com.sagoma.planimetria.model.FurnitureCatalog.set(saved)
        }
    }

    @Test
    fun `Correggi - mobile fuori dal muro, impianto piu basso, porte e finestre nel muro`() {
        fun one(b: Building, k: PlanCheck.Kind) = PlanCheck.of(b).single { it.kind == k }
        fun still(b: Building, k: PlanCheck.Kind) = PlanCheck.of(b).filter { it.kind == k }
        val saved = com.sagoma.planimetria.model.FurnitureCatalog.items
        try {
            com.sagoma.planimetria.model.FurnitureCatalog.set(listOf(com.sagoma.planimetria.model.FurnitureCatalog.Item(model = "armadio", label = "Armadio", category = "Camera", width = 100.0, depth = 60.0, height = 220.0)))
            fun at(x: Double, y: Double) = com.sagoma.planimetria.model.Furniture(7, "armadio", Vec2(x, y), width = 100.0, depth = 60.0, height = 220.0)
            // Dentro di 5 cm nel muro 0 del soggiorno (faccia a 7,5): torna appoggiato alla parete.
            val b = Building.single(FloorPlan(listOf(living, kitchen), furniture = listOf(at(100.0, 32.5))))
            val i = one(b, PlanCheck.Kind.FurnitureInWall)
            assertTrue(PlanCheck.canFix(i))
            val fixed = assertNotNull(PlanCheck.fixed(b, i), "mobile")
            val moved = fixed.floors[0].plan.furniture.single()
            assertEquals(37.6, moved.center.y, 0.01)
            assertEquals(100.0, moved.center.x, 0.01)
            assertEquals(emptyList(), still(fixed, PlanCheck.Kind.FurnitureInWall))
            // Nell'angolo (dentro i muri 0 e 3): esce da tutti e due.
            val corner = Building.single(FloorPlan(listOf(living, kitchen), furniture = listOf(at(54.0, 32.5))))
            val c = assertNotNull(PlanCheck.fixed(corner, one(corner, PlanCheck.Kind.FurnitureInWall)), "angolo").floors[0].plan.furniture.single()
            assertTrue(c.center.x >= 57.5 && c.center.y >= 37.5, "${c.center}")
            // Oltre la mezzeria del muro di 2 cm, con i fianchi nei vani della porta d'ingresso e di una finestra: è dentro di
            // 9,5 cm (mezzo muro più 2), non di 5,5, e torna alla parete in un colpo.
            val windowed = living.copy(openings = living.openings + Opening(9, OpeningKind.Window1, 0, 57.5, 75.0, 120.0, sillHeight = 90.0))
            val past = Building.single(FloorPlan(listOf(windowed, kitchen), furniture = listOf(at(150.0, 28.0))))
            val deep = one(past, PlanCheck.Kind.FurnitureInWall)
            assertEquals(9.5, deep.a, 0.01)
            assertEquals(0.0, deep.b)
            assertEquals(37.6, assertNotNull(PlanCheck.fixed(past, deep), "oltre").floors[0].plan.furniture.single().center.y, 0.01)
            // A cavallo del muro non si sa da che parte: niente «Correggi».
            val across = Building.single(FloorPlan(listOf(living, kitchen), furniture = listOf(at(100.0, 0.0))))
            val a = one(across, PlanCheck.Kind.FurnitureInWall)
            assertTrue(!PlanCheck.canFix(a))
            assertNull(PlanCheck.fixed(across, a))
        } finally {
            com.sagoma.planimetria.model.FurnitureCatalog.set(saved)
        }

        // Plafoniera a parete alta 226 cm sul muro alto 140: scende con la cima 2 cm sotto quella del muro.
        val light = com.sagoma.planimetria.model.Fixture(1, com.sagoma.planimetria.model.FixtureKind.WallLight, wallIndex = 3, position = 200.0, elevation = 220.0)
        val attic = Building.single(FloorPlan(listOf(Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0), wallThickness = 30.0, fixtures = listOf(light), wallHeights = mapOf(3 to 140.0)))))
        val f = one(attic, PlanCheck.Kind.FixtureOverWall)
        val lowered = assertNotNull(PlanCheck.fixed(attic, f), "impianto")
        assertEquals(132.0, lowered.floors[0].plan.rooms[0].fixtures.single().elevation)
        assertEquals(emptyList(), still(lowered, PlanCheck.Kind.FixtureOverWall))

        // Finestra fino a 300 cm sul muro alto 270: si accorcia fino alla cima del muro.
        val tall = Building.single(FloorPlan(listOf(living.copy(openings = living.openings + Opening(8, OpeningKind.Window1, 3, 150.0, 80.0, 200.0, sillHeight = 100.0)), kitchen)))
        val shorter = assertNotNull(PlanCheck.fixed(tall, one(tall, PlanCheck.Kind.OpeningOverWall)), "alta")
        assertEquals(170.0, shorter.floors[0].plan.rooms[0].openings.single { it.id == 8L }.height)
        assertEquals(emptyList(), still(shorter, PlanCheck.Kind.OpeningOverWall))

        // Porta larga 140 su un muro lungo 100: larga quanto il muro, al centro.
        val small = rect(5, "Bagno", RoomType.Bagno, 100.0, 200.0, Vec2(0.0, 300.0), openings = listOf(Opening(7, OpeningKind.Door, 0, 30.0, 140.0, 210.0)))
        val wide = Building.single(FloorPlan(listOf(living, kitchen, small)))
        val narrow = assertNotNull(PlanCheck.fixed(wide, one(wide, PlanCheck.Kind.OpeningTooWide)), "larga").floors[0].plan.rooms.single { it.id == 5L }.openings.single()
        assertEquals(100.0, narrow.width)
        assertEquals(50.0, narrow.position)

        // Gli altri problemi no (la camera da sola: non si sa dove va la sua porta). La finestra verso un'altra stanza si prova
        // prima (va su un muro che dà fuori, se c'è).
        val inner = Building.single(FloorPlan(listOf(living, kitchen.copy(openings = listOf(Opening.default(6, OpeningKind.Window1, 3, 60.0))))))
        assertTrue(PlanCheck.triesFirst(PlanCheck.of(inner).first { it.kind == PlanCheck.Kind.InnerWindow }))
        val lone = Building.single(FloorPlan(listOf(rect(9, "Camera", RoomType.Camera, 400.0, 300.0))))
        assertTrue(!PlanCheck.canFix(lone, PlanCheck.of(lone).first { it.kind == PlanCheck.Kind.NoDoor }))
    }

    @Test
    fun `Correggi - porta d'ingresso che manca`() {
        // Soggiorno e cucina con la porta in mezzo, nessuna che dà fuori: la porta blindata va nel soggiorno, nel mezzo del muro
        // libero più lungo (in alto, 400 cm; in basso c'è la finestra, a destra la cucina).
        val b = Building.single(FloorPlan(listOf(living.copy(openings = living.openings.filter { !it.entrance }), kitchen)))
        val i = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.NoEntrance }
        assertTrue(PlanCheck.triesFirst(i))
        assertTrue(PlanCheck.canFix(b, i))
        val fixed = assertNotNull(PlanCheck.fixed(b, i), "ingresso")
        val door = fixed.floors[0].plan.rooms.single { it.id == 1L }.openings.single { it.id !in living.openings.map { o -> o.id } }
        assertTrue(door.entrance)
        assertEquals(OpeningKind.Door, door.kind)
        assertEquals(90.0, door.width)
        assertEquals(0, door.wallIndex)
        assertEquals(200.0, door.position, 1e-9)
        assertTrue(PlanCheck.of(fixed).none { it.kind == PlanCheck.Kind.NoEntrance })
        // Già corretto (l'elenco è vecchio): niente.
        assertNull(PlanCheck.fixed(fixed, i))
        // Con l'ingresso (anche solo dal nome) va lì: muro in alto, il primo dei due liberi più lunghi.
        val hall = rect(3, "Ingresso", RoomType.Altro, 250.0, 200.0, Vec2(-250.0, 0.0), openings = listOf(Opening.default(9, OpeningKind.Door, 1, 150.0)))
        val withHall = Building.single(FloorPlan(listOf(living.copy(openings = living.openings.filter { !it.entrance }), kitchen, hall)))
        val h = assertNotNull(PlanCheck.fixed(withHall, PlanCheck.of(withHall).single { it.kind == PlanCheck.Kind.NoEntrance }), "dall'ingresso")
        val front = h.floors[0].plan.rooms.single { it.id == 3L }.openings.single { it.entrance }
        assertEquals(0, front.wallIndex)
        assertEquals(125.0, front.position, 1e-9)
        assertEquals(living.openings.filter { !it.entrance }, h.floors[0].plan.rooms.single { it.id == 1L }.openings)
        // Mai in bagno: la casa che è solo un bagno non si corregge.
        val bath = Building.single(FloorPlan(listOf(rect(9, "Bagno", RoomType.Bagno, 200.0, 200.0))))
        val nb = PlanCheck.of(bath).single { it.kind == PlanCheck.Kind.NoEntrance }
        assertTrue(!PlanCheck.canFix(bath, nb))
        assertNull(PlanCheck.fixed(bath, nb))
    }

    @Test
    fun `Correggi - calorifero e punto acqua che mancano`() {
        // Il soggiorno ha il calorifero e il bagno (a parte) il punto acqua; la cucina ha il lavello sotto la finestra.
        val radiator = Fixture(1, FixtureKind.Radiator, wallIndex = 3, position = 150.0, length = 80.0, height = 60.0, elevation = 12.0)
        val water = Fixture(2, FixtureKind.WaterPoint, wallIndex = 0, position = 50.0, elevation = 50.0)
        val bath = rect(5, "Bagno", RoomType.Bagno, 200.0, 200.0, Vec2(1000.0, 0.0)).copy(fixtures = listOf(water))
        val sink = com.sagoma.planimetria.model.Furniture(7, "cucina:lavello:bianco:legno", Vec2(550.0, 37.5), width = 80.0, depth = 60.0, height = 90.0)
        fun house(kitchenFixtures: List<Fixture> = emptyList(), furniture: List<com.sagoma.planimetria.model.Furniture> = listOf(sink)) =
            Building.single(FloorPlan(listOf(living.copy(fixtures = listOf(radiator)), kitchen.copy(fixtures = kitchenFixtures), bath), furniture = furniture))
        val b = house()
        fun one(b: Building, k: PlanCheck.Kind, room: Long) = PlanCheck.of(b).single { it.kind == k && it.rooms == listOf(room) }
        fun added(b: Building, room: Long) = b.floors[0].plan.rooms.single { it.id == room }.fixtures.last()

        // Cucina: sotto la finestra c'è il lavello, il calorifero va nel mezzo del muro libero più lungo (a destra).
        val cold = one(b, PlanCheck.Kind.NoHeating, 2)
        assertTrue(PlanCheck.triesFirst(cold) && PlanCheck.canFix(b, cold))
        val warm = assertNotNull(PlanCheck.fixed(b, cold), "calorifero")
        val r = added(warm, 2)
        assertEquals(FixtureKind.Radiator, r.kind)
        assertEquals(1, r.wallIndex)
        assertEquals(150.0, r.position, 1e-9)
        assertTrue(PlanCheck.of(warm).none { it.kind == PlanCheck.Kind.NoHeating && it.rooms == listOf(2L) })
        assertNull(PlanCheck.fixed(warm, cold), "già corretto")
        // Senza il lavello va sotto la finestra.
        val free = house(furniture = emptyList())
        val under = added(assertNotNull(PlanCheck.fixed(free, one(free, PlanCheck.Kind.NoHeating, 2))), 2)
        assertEquals(0, under.wallIndex)
        assertEquals(150.0, under.position, 1e-9)
        // Bagno: uno scaldasalviette sul muro libero.
        val towel = added(assertNotNull(PlanCheck.fixed(b, one(b, PlanCheck.Kind.NoHeating, 5))), 5)
        assertEquals(com.sagoma.planimetria.model.RadiatorModel.TowelRail, towel.radiatorModel)
        assertEquals(120.0, towel.height)
        assertEquals(1, towel.wallIndex)

        // Punto acqua della cucina: sul muro dietro al lavello.
        val dry = one(b, PlanCheck.Kind.NoWater, 2)
        assertTrue(PlanCheck.canFix(b, dry))
        val wet = added(assertNotNull(PlanCheck.fixed(b, dry), "punto acqua"), 2)
        assertEquals(FixtureKind.WaterPoint, wet.kind)
        assertEquals(0, wet.wallIndex)
        assertEquals(150.0, wet.position, 1e-9)
        assertEquals(50.0, wet.elevation)
        // Senza lavello non si sa dove va.
        val noSink = house(furniture = emptyList())
        assertTrue(!PlanCheck.canFix(noSink, one(noSink, PlanCheck.Kind.NoWater, 2)))
    }

    @Test
    fun `Correggi - porte per il piano che non ne ha`() {
        // Pianta appena ricalcata: soggiorno e cucina sopra il corridoio, camera e bagno sotto; nessuna porta.
        val bare = listOf(
            rect(1, "Soggiorno", RoomType.Soggiorno, 400.0, 300.0),
            rect(2, "Cucina", RoomType.Cucina, 300.0, 300.0, Vec2(400.0, 0.0)),
            rect(3, "Corridoio", RoomType.Corridoio, 700.0, 120.0, Vec2(0.0, 300.0)),
            rect(4, "Camera", RoomType.Camera, 400.0, 300.0, Vec2(0.0, 420.0)),
            rect(5, "Bagno", RoomType.Bagno, 300.0, 300.0, Vec2(400.0, 420.0)),
        )
        val b = Building.single(FloorPlan(bare))
        val i = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.NoDoors }
        assertTrue(PlanCheck.triesFirst(i) && PlanCheck.canFix(b, i))
        val fixed = assertNotNull(PlanCheck.fixed(b, i), "porte")
        val rooms = fixed.floors[0].plan.rooms.associateBy { it.id }
        // Ogni stanza ha la sua porta verso il corridoio; il corridoio ha la porta d'ingresso, su un lato corto.
        for (id in listOf(1L, 2L, 4L, 5L)) {
            val d = rooms.getValue(id).openings.single()
            assertEquals(OpeningKind.Door, d.kind)
            assertTrue(!d.entrance)
        }
        assertEquals(2, rooms.getValue(1L).openings.single().wallIndex, "soggiorno: in basso, verso il corridoio")
        assertEquals(0, rooms.getValue(4L).openings.single().wallIndex, "camera: in alto, verso il corridoio")
        val front = rooms.getValue(3L).openings.single()
        assertTrue(front.entrance)
        assertTrue(front.wallIndex == 1 || front.wallIndex == 3, "ingresso sul lato corto: ${front.wallIndex}")
        val after = PlanCheck.of(fixed)
        assertTrue(after.none { it.kind == PlanCheck.Kind.NoDoors || it.kind == PlanCheck.Kind.NoDoor || it.kind == PlanCheck.Kind.NoEntrance }, "${after.map { it.kind }}")
        assertEquals(emptyList(), after.filter { it.kind.error })
        assertNull(PlanCheck.fixed(fixed, i), "già corretto")
        // Due stanze lontane: non si collegano.
        val apart = Building.single(FloorPlan(listOf(bare[0], bare[1].copy(points = bare[1].points.map { it + Vec2(500.0, 0.0) }))))
        val far = PlanCheck.of(apart).single { it.kind == PlanCheck.Kind.NoDoors }
        assertTrue(!PlanCheck.canFix(apart, far))
    }

    @Test
    fun `Correggi - finestre sotto 1 ottavo del pavimento`() {
        val door = Opening.default(1, OpeningKind.Door, 0, 200.0).copy(entrance = true)
        val window = Opening.default(10, OpeningKind.Window2, 2, 200.0)
        // Camera 400×400 (14,82 m²) con la finestra 120×120 (1,44 m²; ne servono 1,85): si allarga a 160 (1,92 m²), al suo posto.
        val bedroom = rect(1, "Camera", RoomType.Camera, 400.0, 400.0, openings = listOf(door, window))
        val b = Building.single(FloorPlan(listOf(bedroom)))
        val few = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.FewWindows }
        assertTrue(PlanCheck.triesFirst(few) && PlanCheck.canFix(b, few))
        val wider = assertNotNull(PlanCheck.fixed(b, few)).floors[0].plan.rooms.single().openings.single { it.id == 10L }
        assertEquals(160.0, wider.width)
        assertEquals(200.0, wider.position)
        // Lo studio accanto, senza finestre: una nuova da 90 (1,08 m² su 8,12), su un muro che dà fuori, non su quello in comune.
        val study = rect(2, "Studio", RoomType.Studio, 300.0, 300.0, Vec2(400.0, 0.0), openings = listOf(Opening.default(2, OpeningKind.Door, 3, 150.0)))
        val two = Building.single(FloorPlan(listOf(bedroom.copy(openings = listOf(door, window.copy(height = 160.0))), study)))
        val dark = PlanCheck.of(two).single { it.kind == PlanCheck.Kind.NoWindows }
        val lit = assertNotNull(PlanCheck.fixed(two, dark))
        val added = lit.floors[0].plan.rooms.single { it.id == 2L }.openings.single { it.kind.glazed }
        assertEquals(OpeningKind.Window1, added.kind)
        assertEquals(90.0, added.width)
        assertTrue(added.wallIndex != 3, "non sul muro in comune con la camera")
        assertTrue(PlanCheck.of(lit).none { it.kind.rule && it.kind != PlanCheck.Kind.SmallBedroom && it.rooms.contains(2L) }, "${PlanCheck.of(lit).map { it.kind }}")
        // Il piano senza nessuna finestra (pianta appena letta): le finestre in tutte e due le stanze.
        val bare = Building.single(FloorPlan(listOf(bedroom.copy(openings = listOf(door)), study)))
        val none = PlanCheck.of(bare).single { it.kind == PlanCheck.Kind.NoFloorWindows }
        assertTrue(PlanCheck.triesFirst(none) && PlanCheck.canFix(bare, none))
        val all = assertNotNull(PlanCheck.fixed(bare, none))
        val after = PlanCheck.of(all)
        assertTrue(after.none { it.kind == PlanCheck.Kind.NoFloorWindows || it.kind == PlanCheck.Kind.NoWindows || it.kind == PlanCheck.Kind.FewWindows }, "${after.map { it.kind }}")
        assertEquals(emptyList(), after.filter { it.kind.error })
        // Stanza chiusa tra le altre, senza muri che danno fuori: niente «Correggi».
        val inner = rect(3, "Studio", RoomType.Studio, 300.0, 300.0, Vec2(300.0, 300.0), openings = listOf(Opening.default(3, OpeningKind.Door, 0, 150.0)))
        val around = listOf(
            rect(4, "Camera", RoomType.Camera, 900.0, 300.0, openings = listOf(door, window.copy(height = 160.0), window.copy(id = 11, wallIndex = 0, position = 700.0, height = 160.0))),
            rect(5, "Camera", RoomType.Camera, 300.0, 300.0, Vec2(0.0, 300.0), openings = listOf(window.copy(id = 12, wallIndex = 3, height = 160.0))),
            rect(6, "Camera", RoomType.Camera, 300.0, 300.0, Vec2(600.0, 300.0), openings = listOf(window.copy(id = 13, wallIndex = 1, height = 160.0))),
            rect(7, "Camera", RoomType.Camera, 900.0, 300.0, Vec2(0.0, 600.0), openings = listOf(window.copy(id = 14, height = 160.0))),
        )
        val closed = Building.single(FloorPlan(around + inner))
        val shut = PlanCheck.of(closed).single { it.kind == PlanCheck.Kind.NoWindows && it.rooms == listOf(3L) }
        assertTrue(!PlanCheck.canFix(closed, shut))
    }

    @Test
    fun `Correggi - prese e luci che mancano`() {
        fun points(b: Building, room: Long) = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.FewPoints && it.rooms == listOf(room) }
        fun fix(b: Building, room: Long): Pair<Building, List<Fixture>> {
            val i = points(b, room)
            assertTrue(PlanCheck.triesFirst(i) && PlanCheck.canFix(b, i))
            val out = assertNotNull(PlanCheck.fixed(b, i))
            assertTrue(PlanCheck.of(out).none { it.kind == PlanCheck.Kind.FewPoints && it.rooms == listOf(room) })
            assertEquals(emptyList(), LiveCheck.newErrors(PlanCheck.of(b), PlanCheck.of(out)))
            val old = b.floors[0].plan.rooms.single { it.id == room }.fixtures.map { it.id }.toSet()
            return out to out.floors[0].plan.rooms.single { it.id == room }.fixtures.filter { it.id !in old }
        }
        fun at(r: Room, f: Fixture) = com.sagoma.planimetria.geometry.Fixtures.center(r, f)

        // Camera 400×400 (14,82 m²) con una presa: ne mancano 3 e una luce. Le prese lontane tra loro e dalla finestra, a 30 cm; la
        // plafoniera nel mezzo.
        val window = Opening.default(10, OpeningKind.Window2, 2, 200.0).copy(height = 160.0)
        val outlet = Fixture.default(1, FixtureKind.Outlet).copy(wallIndex = 0, position = 100.0)
        val bedroom = rect(1, "Camera", RoomType.Camera, 400.0, 400.0, openings = listOf(window)).copy(fixtures = listOf(outlet))
        val (lit, added) = fix(Building.single(FloorPlan(listOf(bedroom))), 1)
        val outlets = added.filter { it.kind == FixtureKind.Outlet }
        assertEquals(3, outlets.size)
        assertTrue(outlets.all { it.elevation == 30.0 })
        assertTrue(outlets.none { it.wallIndex == 2 && it.position in 125.0..275.0 }, "non sotto la finestra: $outlets")
        val room = lit.floors[0].plan.rooms.single()
        val spots = (outlets + outlet).map { at(room, it) }
        assertTrue(spots.indices.all { a -> spots.indices.all { b -> a == b || spots[a].distanceTo(spots[b]) > 150.0 } }, "$spots")
        val light = added.single { it.kind.isLight }
        assertEquals(FixtureKind.CeilingLight, light.kind)
        assertTrue(light.point.distanceTo(Vec2(200.0, 200.0)) < 15.0, "${light.point}")
        assertNull(PlanCheck.fixed(lit, points(Building.single(FloorPlan(listOf(bedroom))), 1)), "già corretto")

        // Cucina con le basi sul muro della finestra: le prese a 110 cm, prima sopra il piano di lavoro (non sopra il lavello e i
        // fuochi), le altre sugli altri muri. Il soggiorno ha già tutto.
        val full = living.copy(fixtures = (1..4).map { Fixture.default(it.toLong(), FixtureKind.Outlet).copy(wallIndex = 0, position = 60.0 * it) } +
            Fixture.default(5, FixtureKind.CeilingLight).copy(point = Vec2(200.0, 150.0)))
        fun module(id: Long, kind: String, x: Double, width: Double) =
            com.sagoma.planimetria.model.Furniture(id, "cucina:$kind:bianco:legno", Vec2(x, 37.5), width = width, depth = 60.0, height = 90.0)
        val modules = listOf(module(1, "base", 440.0, 60.0), module(2, "lavello", 510.0, 80.0), module(3, "forno", 580.0, 60.0), module(4, "base", 640.0, 60.0))
        val (_, cooking) = fix(Building.single(FloorPlan(listOf(full, kitchen), furniture = modules)), 2)
        val sockets = cooking.filter { it.kind == FixtureKind.Outlet }
        assertEquals(5, sockets.size)
        assertTrue(sockets.all { it.elevation == 110.0 })
        val top = sockets.filter { it.wallIndex == 0 }.map { it.position }
        assertEquals(2, top.size, "$sockets")
        assertTrue(top.any { it in 10.0..70.0 } && top.any { it in 210.0..270.0 }, "una sopra ogni base: $top")
        assertEquals(1, cooking.count { it.kind == FixtureKind.CeilingLight })

        // Bagno con la doccia nell'angolo: la presa a 110 lontano 60 cm dalla doccia, due luci. Balcone da 11 m² davanti al
        // soggiorno: presa e plafoniera a parete sul muro verso la casa.
        val saved = com.sagoma.planimetria.model.FurnitureCatalog.items
        try {
            com.sagoma.planimetria.model.FurnitureCatalog.set(listOf(
                com.sagoma.planimetria.model.FurnitureCatalog.Item(model = "doccia", label = "Piatto doccia", category = "Bagno", symbol = "Shower", width = 80.0, depth = 80.0, height = 10.0),
            ))
            val shower = com.sagoma.planimetria.model.Furniture(9, "doccia", Vec2(447.5, 47.5), width = 80.0, depth = 80.0, height = 10.0)
            val bath = rect(3, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(400.0, 0.0))
            val balcony = rect(4, "Balcone", RoomType.Balcone, 400.0, 300.0, Vec2(0.0, 300.0))
            val b = Building.single(FloorPlan(listOf(full, bath, balcony), furniture = listOf(shower)))
            val (washed, wet) = fix(b, 3)
            val plug = wet.single { it.kind == FixtureKind.Outlet }
            assertEquals(110.0, plug.elevation)
            val p = at(washed.floors[0].plan.rooms.single { it.id == 3L }, plug)
            val tray = com.sagoma.planimetria.geometry.Furnishings.outline(shower)
            assertTrue(tray.indices.all { j -> com.sagoma.planimetria.geometry.Polygon.distanceToSegment(p, tray[j], tray[(j + 1) % tray.size]) >= 60.0 }, "$p")
            val lamps = wet.filter { it.kind == FixtureKind.CeilingLight }
            assertEquals(2, lamps.size)
            assertTrue(lamps[0].point.distanceTo(lamps[1].point) > 80.0, "$lamps")
            val (_, outside) = fix(b, 4)
            assertEquals(setOf(FixtureKind.Outlet, FixtureKind.WallLight), outside.map { it.kind }.toSet())
            assertTrue(outside.all { it.wallIndex == 0 }, "sul muro verso il soggiorno: $outside")
        } finally {
            com.sagoma.planimetria.model.FurnitureCatalog.set(saved)
        }
    }

    @Test
    fun `Correggi - finestra del bagno`() {
        // Bagno 200×250 accanto al soggiorno, senza finestre: una da 80 nel mezzo del muro libero più lungo che dà fuori.
        val bath = rect(3, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(400.0, 0.0))
        val b = Building.single(FloorPlan(listOf(living, bath)))
        val dark = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.BathNoWindow }
        assertTrue(PlanCheck.triesFirst(dark) && PlanCheck.canFix(b, dark))
        val fixed = assertNotNull(PlanCheck.fixed(b, dark))
        val window = fixed.floors[0].plan.rooms.single { it.id == 3L }.openings.single()
        assertEquals(OpeningKind.Window1, window.kind)
        assertEquals(80.0, window.width)
        assertEquals(1, window.wallIndex)
        assertEquals(125.0, window.position)
        assertTrue(PlanCheck.of(fixed).none { it.kind == PlanCheck.Kind.BathNoWindow })
        // Bagno chiuso tra le altre stanze (cieco): niente «Correggi», serve l'aspiratore.
        val blind = rect(3, "Bagno", RoomType.Bagno, 200.0, 200.0, Vec2(400.0, 300.0))
        val around = Building.single(FloorPlan(listOf(
            rect(4, "Camera", RoomType.Camera, 1000.0, 300.0, openings = listOf(Opening.default(10, OpeningKind.Window2, 0, 500.0))),
            rect(5, "Camera", RoomType.Camera, 400.0, 200.0, Vec2(0.0, 300.0)),
            rect(6, "Camera", RoomType.Camera, 400.0, 200.0, Vec2(600.0, 300.0)),
            rect(7, "Camera", RoomType.Camera, 1000.0, 300.0, Vec2(0.0, 500.0)),
            blind,
        )))
        val closed = PlanCheck.of(around).single { it.kind == PlanCheck.Kind.BathNoWindow }
        assertTrue(!PlanCheck.canFix(around, closed))
        // Il piano senza nessuna finestra: anche il bagno ne riceve una.
        val bare = Building.single(FloorPlan(listOf(living.copy(openings = living.openings.filter { !it.kind.glazed }), bath)))
        val none = PlanCheck.of(bare).single { it.kind == PlanCheck.Kind.NoFloorWindows }
        val all = assertNotNull(PlanCheck.fixed(bare, none))
        assertTrue(PlanCheck.of(all).none { it.kind == PlanCheck.Kind.BathNoWindow || it.kind == PlanCheck.Kind.NoWindows }, "${PlanCheck.of(all).map { it.kind }}")
        assertEquals(1, all.floors[0].plan.rooms.single { it.id == 3L }.openings.count { it.kind.glazed })
    }

    @Test
    fun `Correggi - nel piano con stato di fatto e di progetto porte e finestre messe sono nuove`() {
        // Ristrutturazione: la porta tra soggiorno e cucina è da chiudere; la cucina resta senza porta e «Correggi» ne apre una
        // nuova sullo stesso muro, accanto.
        val b = Building.single(FloorPlan(listOf(living.copy(openings = listOf(entrance, inner.copy(phase = Phase.Demolish), living.openings[2])), kitchen)))
        val shut = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.NoDoor && it.rooms == listOf(2L) }
        val fixed = assertNotNull(PlanCheck.fixed(b, shut))
        val plan = fixed.floors[0].plan
        val door = (plan.rooms.flatMap { it.openings } - b.floors[0].plan.rooms.flatMap { it.openings }.toSet()).single()
        assertEquals(Phase.New, door.phase)
        val works = com.sagoma.planimetria.geometry.Phases.works(plan)
        assertEquals(1, works.openingsNew)
        assertEquals(1, works.openingsClosed)
        // Camera con una finestra da chiudere e una da 120 che resta: quella che resta non si allarga, se ne apre una nuova.
        val window = Opening.default(10, OpeningKind.Window2, 2, 200.0)
        val bedroom = rect(1, "Camera", RoomType.Camera, 400.0, 400.0, openings = listOf(
            Opening.default(1, OpeningKind.Door, 0, 200.0).copy(entrance = true), window,
            Opening.default(11, OpeningKind.Window1, 1, 200.0).copy(phase = Phase.Demolish),
        ))
        val dark = Building.single(FloorPlan(listOf(bedroom)))
        val few = PlanCheck.of(dark).single { it.kind == PlanCheck.Kind.FewWindows }
        val lit = assertNotNull(PlanCheck.fixed(dark, few)).floors[0].plan.rooms.single()
        assertEquals(window, lit.openings.single { it.id == 10L })
        val opened = lit.openings.single { it.id !in setOf(1L, 10L, 11L) }
        assertEquals(Phase.New, opened.phase)
        assertTrue(opened.kind.glazed)
        // Senza stato di fatto e di progetto restano come quelle del menu (e la finestra si allarga).
        val plain = Building.single(FloorPlan(listOf(bedroom.copy(openings = bedroom.openings.dropLast(1)))))
        val wide = assertNotNull(PlanCheck.fixed(plain, PlanCheck.of(plain).single { it.kind == PlanCheck.Kind.FewWindows })).floors[0].plan.rooms.single()
        assertEquals(Phase.Existing, wide.openings.single { it.id == 10L }.phase)
        assertTrue(wide.openings.single { it.id == 10L }.width > 120.0)
    }

    @Test
    fun `Correggi - porta del bagno che si apre sul soggiorno`() {
        // Soggiorno sopra, bagno e corridoio sotto; il bagno si apre sul soggiorno, il corridoio anche (e va bene).
        val room = rect(1, "Soggiorno", RoomType.Soggiorno, 400.0, 300.0, openings = listOf(entrance, Opening.default(3, OpeningKind.Window2, 3, 150.0)))
        val bath = rect(3, "Bagno", RoomType.Bagno, 200.0, 200.0, Vec2(0.0, 300.0), openings = listOf(Opening.default(5, OpeningKind.Door, 0, 100.0)))
        val hall = rect(4, "Corridoio", RoomType.Corridoio, 200.0, 200.0, Vec2(200.0, 300.0), openings = listOf(Opening.default(6, OpeningKind.Door, 0, 100.0)))
        fun house(vararg r: Room) = Building.single(FloorPlan(r.toList()))
        val b = house(room, bath, hall)
        val onLiving = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.BathOnLiving }
        assertTrue(PlanCheck.triesFirst(onLiving) && PlanCheck.canFix(b, onLiving))
        val fixed = assertNotNull(PlanCheck.fixed(b, onLiving))
        val door = fixed.floors[0].plan.rooms.single { it.id == 3L }.openings.single()
        assertEquals(1, door.wallIndex, "verso il corridoio")
        val after = PlanCheck.of(fixed)
        assertTrue(after.none { it.kind == PlanCheck.Kind.BathOnLiving || it.kind == PlanCheck.Kind.NoDoor }, "${after.map { it.kind }}")
        // Con stato di fatto e di progetto: la porta di prima si chiude, la nuova si apre.
        val phased = house(room.copy(openings = room.openings + Opening.default(7, OpeningKind.Window1, 0, 350.0).copy(phase = Phase.New)), bath, hall)
        val works = assertNotNull(PlanCheck.fixed(phased, PlanCheck.of(phased).single { it.kind == PlanCheck.Kind.BathOnLiving })).floors[0].plan
        val doors = works.rooms.single { it.id == 3L }.openings
        assertEquals(Phase.Demolish, doors.single { it.id == 5L }.phase)
        assertEquals(Phase.New, doors.single { it.id != 5L }.phase)
        // Il bagno che dà solo sul soggiorno: niente «Correggi» (resterebbe senza porta).
        val alone = house(room, bath)
        assertTrue(!PlanCheck.canFix(alone, PlanCheck.of(alone).single { it.kind == PlanCheck.Kind.BathOnLiving }))
    }

    @Test
    fun `Correggi tutti - gli errori di un gruppo insieme`() {
        // Finestra più alta del muro, porta più larga del muro del bagno, cucina sovrapposta al soggiorno: tre errori (⚠) che si
        // correggono da soli.
        val tall = living.copy(openings = living.openings + Opening(8, OpeningKind.Window1, 3, 150.0, 80.0, 200.0, sillHeight = 100.0))
        val bath = rect(5, "Bagno", RoomType.Bagno, 100.0, 200.0, Vec2(0.0, 300.0), openings = listOf(Opening(7, OpeningKind.Door, 0, 30.0, 140.0, 210.0)))
        val b = Building.single(FloorPlan(listOf(tall, kitchen.moved(-100.0), bath)))
        val errors = PlanCheck.of(b).filter { it.kind.error }
        assertEquals(setOf(PlanCheck.Kind.OpeningOverWall, PlanCheck.Kind.OpeningTooWide, PlanCheck.Kind.Overlap), errors.kinds())
        val (all, three) = PlanCheck.fixedAll(b, errors)
        assertEquals(3, three)
        assertEquals(emptyList(), PlanCheck.of(all).filter { it.kind.error })
        assertEquals(170.0, all.floors[0].plan.rooms.single { it.id == 1L }.openings.single { it.id == 8L }.height)
        assertEquals(100.0, all.floors[0].plan.rooms.single { it.id == 5L }.openings.single().width)
        // Già corretti (l'elenco è vecchio): niente.
        assertEquals(all to 0, PlanCheck.fixedAll(all, errors))
        // La porta larga tolta dalla pianta (vista, voluta così) resta com'è; gli altri due sì, e nessun errore nuovo.
        val wide = errors.single { it.kind == PlanCheck.Kind.OpeningTooWide }
        val seen = b.copy(hiddenChecks = setOf(PlanCheck.hideKey(b, wide)))
        val (fixed, two) = PlanCheck.fixedAll(seen, errors)
        assertEquals(2, two)
        assertEquals(setOf(PlanCheck.Kind.OpeningTooWide), PlanCheck.of(fixed).filter { it.kind.error }.kinds())
        assertEquals(emptyList(), LiveCheck.newErrors(PlanCheck.of(seen), PlanCheck.of(fixed)))
        // Quelli che non si correggono da soli si saltano.
        val onlyBath = Building.single(FloorPlan(listOf(rect(9, "Bagno", RoomType.Bagno, 200.0, 200.0))))
        assertEquals(onlyBath to 0, PlanCheck.fixedAll(onlyBath, PlanCheck.of(onlyBath)))
    }

    @Test
    fun `impianti piu alti del muro in mansarda`() {
        // Muro 3 basso (140): la plafoniera a parete a 220 cm non ci sta; il calorifero sotto sì, e sul muro alto (1) anche la luce.
        val light = com.sagoma.planimetria.model.Fixture(1, com.sagoma.planimetria.model.FixtureKind.WallLight, wallIndex = 3, position = 200.0, elevation = 220.0)
        val radiator = com.sagoma.planimetria.model.Fixture(2, com.sagoma.planimetria.model.FixtureKind.Radiator, wallIndex = 3, position = 100.0, length = 80.0, height = 60.0, elevation = 15.0)
        val high = light.copy(id = 3, wallIndex = 1)
        val attic = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0), wallThickness = 30.0, fixtures = listOf(light, radiator, high),
            wallHeights = mapOf(3 to 140.0), wallCuts = mapOf(0 to com.sagoma.planimetria.model.WallCut(false, 250.0), 2 to com.sagoma.planimetria.model.WallCut(true, 250.0)))
        val b = Building.single(FloorPlan(listOf(attic)))
        val i = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.FixtureOverWall }
        assertEquals(com.sagoma.planimetria.editor.Selection.Fixture(1, 1), i.selection)
        assertTrue(!i.kind.error && !i.kind.rule)
        assertEquals("«Camera»: plafoniera a parete arriva a 226 cm da terra, ma lì il muro è alto 140 cm.", PlanCheck.describe(i, b))
        // Abbassata sotto la cima del muro: niente.
        val lower = attic.copy(fixtures = listOf(light.copy(elevation = 120.0), radiator, high))
        assertTrue(PlanCheck.of(Building.single(FloorPlan(listOf(lower)))).none { it.kind == PlanCheck.Kind.FixtureOverWall })
    }

    @Test
    fun `norme - mansarda bassa in media`() {
        // Camera 500×400 con il muro 3 basso (140): il soffitto sale fino a 270 nei primi 250 cm. Dentro i muri da 30 la media è
        // 2,39 m: bassa anche per le leggi sui sottotetti (2,40). Con il colmo a 300 la media è 2,62: va bene.
        val window = Opening.default(1, OpeningKind.Window2, 1, 200.0).copy(width = 200.0, height = 160.0)
        val attic = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 400.0), wallThickness = 30.0, openings = listOf(window),
            wallHeights = mapOf(3 to 140.0), wallCuts = mapOf(0 to com.sagoma.planimetria.model.WallCut(false, 250.0), 2 to com.sagoma.planimetria.model.WallCut(true, 250.0)))
        val b = Building.single(FloorPlan(listOf(attic)))
        val low = rules(attic).single { it.kind == PlanCheck.Kind.LowAttic }
        assertEquals(
            "«Camera»: soffitto in pendenza alto in media 2,39 m. Per abitarci le leggi regionali sui sottotetti di solito chiedono una media di almeno 2,40 m (fuori dai sottotetti 2,70 m).",
            PlanCheck.describe(low, b),
        )
        assertTrue(rules(attic.copy(ceilingHeight = 300.0)).none { it.kind == PlanCheck.Kind.LowAttic || it.kind == PlanCheck.Kind.LowCeiling })
        // Un bagno o un ripostiglio in mansarda non si guardano; una stanza col soffitto piano ha già il suo controllo.
        assertTrue(rules(attic.copy(type = RoomType.Bagno, name = "Bagno")).none { it.kind == PlanCheck.Kind.LowAttic })
        assertTrue(rules(attic.copy(type = RoomType.Altro, name = "Ripostiglio")).none { it.kind == PlanCheck.Kind.LowAttic })
        assertTrue(rules(attic.copy(wallCuts = emptyMap(), wallHeights = emptyMap(), ceilingHeight = 230.0)).none { it.kind == PlanCheck.Kind.LowAttic })
    }

    @Test
    fun `norme - bagno che si apre sulla cucina o sul soggiorno`() {
        // Bagno 200×250 sotto il soggiorno, con la finestra; la porta sul muro in comune.
        val window = Opening.default(10, OpeningKind.Window1, 2, 100.0)
        fun bath(vararg doors: Opening) = rect(3, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(0.0, 300.0), openings = listOf(window) + doors)
        val door = Opening.default(11, OpeningKind.Door, 0, 100.0)
        fun on(vararg rooms: Room) = PlanCheck.of(Building.single(FloorPlan(rooms.toList()))).filter { it.kind == PlanCheck.Kind.BathOnLiving }
        val b = Building.single(FloorPlan(listOf(living, kitchen, bath(door))))
        val i = on(living, kitchen, bath(door)).single()
        assertEquals(listOf(3L, 1L), i.rooms)
        assertEquals(com.sagoma.planimetria.editor.Selection.Opening(3, 11), i.selection)
        assertEquals(
            "«Bagno» si apre direttamente su «Soggiorno»: molti regolamenti d'igiene dei comuni vogliono un antibagno o un disimpegno tra il bagno e la cucina o il soggiorno.",
            PlanCheck.describe(i, b),
        )
        // La porta disegnata dal soggiorno sul muro in comune conta lo stesso; un passaggio senza porta anche.
        val fromLiving = living.copy(openings = living.openings + Opening.default(12, OpeningKind.Door, 2, 300.0))
        assertEquals(listOf(3L, 1L), on(fromLiving, kitchen, bath()).single().rooms)
        assertEquals(1, on(living, kitchen, bath(door.copy(kind = OpeningKind.Passage))).size)
        // Sulla cucina sì; su una camera, un disimpegno o un antibagno no; una porta da chiudere (stato di progetto) no.
        val underKitchen = rect(3, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(450.0, 300.0), openings = listOf(window, door))
        assertEquals(listOf(3L, 2L), on(living, kitchen, underKitchen).single().rooms)
        for (type in listOf(RoomType.Camera, RoomType.Corridoio, RoomType.Altro)) {
            assertEquals(emptyList(), on(living.copy(type = type, name = "Stanza"), kitchen, bath(door)), "$type")
        }
        assertEquals(emptyList(), on(living, kitchen, bath(door.copy(phase = com.sagoma.planimetria.model.Phase.Demolish))))
        // Un bagno senza porte non è questo problema (c'è «senza porta»).
        assertEquals(emptyList(), on(living, kitchen, bath()))
    }

    @Test
    fun `norme - sanitari del bagno, monolocale e metri quadri per persona`() {
        val saved = com.sagoma.planimetria.model.FurnitureCatalog.items
        try {
            fun item(model: String, label: String) = com.sagoma.planimetria.model.FurnitureCatalog.Item(model = model, label = label, category = "Bagno", width = 40.0, depth = 50.0, height = 40.0)
            com.sagoma.planimetria.model.FurnitureCatalog.set(listOf(item("wc", "WC moderno"), item("bide", "Bidè"), item("doccia", "Box doccia"), item("lavabo", "Lavabo a colonna")))
            val big = Opening.default(1, OpeningKind.Window2, 2, 300.0).copy(width = 200.0, height = 200.0)
            // Soggiorno 600×500 (28,37 m²) e bagno 200×250 (4,35 m²) con la finestra: un monolocale di 32,72 m².
            val living = rect(1, "Soggiorno", RoomType.Soggiorno, 600.0, 500.0, openings = listOf(big))
            val bath = rect(2, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(600.0, 0.0), openings = listOf(Opening.default(2, OpeningKind.Window1, 0, 100.0)))
            fun thing(id: Long, model: String, x: Double) = com.sagoma.planimetria.model.Furniture(id, model, Vec2(x, 100.0), width = 40.0, depth = 50.0, height = 40.0)
            val wc = thing(1, "wc", 640.0)
            val bide = thing(2, "bide", 690.0)
            val shower = thing(3, "doccia", 740.0)
            val basin = thing(4, "lavabo", 780.0)
            val bed = com.sagoma.planimetria.model.Furniture(5, "mobile:letto:rovere", Vec2(300.0, 250.0), width = 160.0, depth = 205.0, height = 100.0)
            fun house(vararg f: com.sagoma.planimetria.model.Furniture) = Building.single(FloorPlan(listOf(living, bath), furniture = f.toList()))
            fun rulesOf(b: Building) = PlanCheck.of(b).filter { it.kind.rule }
            // Bagno ancora da arredare: niente. Arredato a metà: cosa manca.
            assertEquals(emptyList(), rulesOf(house()))
            val i = rulesOf(house(wc, shower)).single()
            assertEquals(PlanCheck.Kind.BathIncomplete, i.kind)
            assertEquals("«Bagno»: senza bidè, lavabo. Almeno un bagno della casa deve avere vaso, bidè, vasca o doccia e lavabo.", PlanCheck.describe(i, house(wc, shower)))
            assertEquals(emptyList(), rulesOf(house(wc, bide, shower, basin)))
            // Con un letto matrimoniale il monolocale deve avere almeno 38 m².
            val studio = rulesOf(house(bed)).single()
            assertEquals(PlanCheck.Kind.SmallStudio, studio.kind)
            assertEquals("Monolocale di 32,72 m²: ne servono almeno 38 per due persone, bagno compreso.", PlanCheck.describe(studio, house(bed)))
            // Casa con soggiorno e camera (14,82 m² l'una) per quattro persone: 33,99 m² invece di 56.
            val window = Opening.default(1, OpeningKind.Window2, 2, 200.0).copy(height = 160.0)
            val room = rect(1, "Soggiorno", RoomType.Soggiorno, 400.0, 400.0, openings = listOf(window))
            val bedroom = rect(3, "Camera", RoomType.Camera, 400.0, 400.0, Vec2(400.0, 0.0), openings = listOf(window.copy(id = 3)))
            val bath2 = bath.copy(points = bath.points.map { it + Vec2(200.0, 0.0) })
            val beds = listOf(bed.copy(center = Vec2(500.0, 200.0)), bed.copy(id = 6, center = Vec2(700.0, 200.0)))
            val home = Building.single(FloorPlan(listOf(room, bedroom, bath2), furniture = beds))
            val small = rulesOf(home).single()
            assertEquals(PlanCheck.Kind.SmallHome, small.kind)
            assertEquals("La casa ha 33,99 m² per 4 persone (contate dai letti); ne servono almeno 56 (14 m² a testa per le prime 4, 10 per le altre).", PlanCheck.describe(small, home))
            // Senza bagno, o senza soggiorno né cucina, è solo una parte della casa (o una stanza da arredare): queste norme non si guardano.
            assertEquals(emptyList(), rulesOf(Building.single(FloorPlan(listOf(room, bedroom), furniture = beds))))
            assertEquals(emptyList(), rulesOf(Building.single(FloorPlan(listOf(bedroom, bath2), furniture = beds))))
        } finally {
            com.sagoma.planimetria.model.FurnitureCatalog.set(saved)
        }
    }

    @Test
    fun `scale ripide, strette o con poca altezza sopra la testa`() {
        val hall = rect(1, "Salone", RoomType.Soggiorno, 800.0, 600.0, openings = listOf(entrance))
        val stair = Stair(1, StairKind.Straight, Vec2(400.0, 300.0))
        fun two(s: Stair) = Building(
            listOf(
                Floor(1, "Piano terra", FloorPlan(listOf(hall), stairs = listOf(s)), levelHeight = 300.0),
                Floor(2, "Primo piano", FloorPlan(listOf(hall.copy(openings = emptyList())))),
            ),
        )
        val kinds = setOf(PlanCheck.Kind.StairSteep, PlanCheck.Kind.StairNarrow, PlanCheck.Kind.StairLowHead)
        fun issues(s: Stair) = PlanCheck.of(two(s)).filter { it.kind in kinds }
        // La scala di serie: 17 alzate da 17,6 cm, pedate da 28, larga 90.
        assertEquals(emptyList(), issues(stair))
        // 14 alzate da 21,4 cm: ripida.
        val steep = issues(stair.copy(risers = 14)).single()
        assertEquals(PlanCheck.Kind.StairSteep, steep.kind)
        assertEquals(Selection.Stair(1), steep.selection)
        assertEquals(
            "Scala ripida: alzate di 21,4 cm e pedate di 28 cm. Per salire comodi l'alzata di solito è al massimo 18-20 cm e due alzate più una pedata fanno 62-64 cm: aggiungi alzate o scegli una pendenza più dolce (Info della scala → Gradini).",
            PlanCheck.describe(steep, two(stair.copy(risers = 14))),
        )
        // Le pendenze proposte nella scheda vanno bene fino a «Ripida»; «Molto ripida» no.
        assertEquals(emptyList(), issues(com.sagoma.planimetria.geometry.Stairs.withSlope(stair, 300.0, 19.5)))
        assertEquals(listOf(PlanCheck.Kind.StairSteep), issues(com.sagoma.planimetria.geometry.Stairs.withSlope(stair, 300.0, 21.5)).map { it.kind })
        // Pedate troppo corte per le alzate (2 × 17,6 + 20 = 55 cm).
        assertEquals(listOf(PlanCheck.Kind.StairSteep), issues(stair.copy(tread = 20.0)).map { it.kind })
        // Stretta.
        val narrow = issues(stair.copy(width = 70.0)).single()
        assertEquals("Scala larga 70 cm: dentro casa di solito ne servono almeno 80 (lo dice il regolamento edilizio del comune).", PlanCheck.describe(narrow, two(stair)))
        // Chiocciola: conta solo l'alzata (pedata e larghezza si misurano in un altro modo).
        val spiral = Stair(1, StairKind.Spiral, Vec2(400.0, 300.0), width = 60.0, diameter = 140.0)
        assertEquals(emptyList(), issues(spiral))
        val steepSpiral = issues(spiral.copy(risers = 13)).single()
        assertEquals("Scala ripida: alzate di 23,1 cm. Per salire comodi l'alzata di solito è al massimo 18-20 cm e due alzate più una pedata fanno 62-64 cm: aggiungi alzate o scegli una pendenza più dolce (Info della scala → Gradini).", PlanCheck.describe(steepSpiral, two(spiral)))
        // Pavimento di sopra che copre 10 gradini: sopra il decimo restano 300 - 30 - 176 = 94 cm.
        val low = issues(stair.copy(coveredSteps = 10)).single()
        assertEquals(PlanCheck.Kind.StairLowHead, low.kind)
        assertEquals(94.0, low.a, 0.5)
        assertTrue(PlanCheck.describe(low, two(stair)).startsWith("Sopra la scala restano 94 cm fino al solaio del piano di sopra"))
        assertEquals(emptyList(), issues(stair.copy(coveredSteps = 3)))
    }

    @Test
    fun `scala che esce dalla stanza di sopra con poca altezza sotto il tetto`() {
        // Prova di Marco del 29/10: il piano di sopra copre solo la metà della scala verso l'arrivo; sopra l'altra metà c'è il
        // tetto piano, chiuso, e ci si batte la testa. Prima l'avviso guardava solo i gradini coperti dal pavimento di sopra.
        val hall = rect(1, "Salone", RoomType.Soggiorno, 800.0, 600.0, openings = listOf(entrance))
        val stair = Stair(1, StairKind.Straight, Vec2(400.0, 300.0))
        val l = com.sagoma.planimetria.geometry.Stairs.layout(stair, 300.0)
        val start = l.path.first()
        val end = l.path.last()
        assertTrue(end.y < start.y)
        fun two(upper: Room) = Building(
            listOf(
                Floor(1, "Piano terra", FloorPlan(listOf(hall), stairs = listOf(stair)), levelHeight = 300.0),
                Floor(2, "Primo piano", FloorPlan(listOf(upper))),
            ),
        )
        fun low(upper: Room) = PlanCheck.of(two(upper)).filter { it.kind == PlanCheck.Kind.StairLowHead }
        val half = rect(2, "Camera", RoomType.Camera, 800.0, (start.y + end.y) / 2)
        val issue = low(half).single()
        // Sotto il tetto (soffitto a 270) il gradino più alto fuori dalla stanza di sopra è a metà scala.
        assertTrue(issue.a > 70.0 && issue.a < 200.0, "altezza ${issue.a}")
        assertTrue(issue.b > 0.5)
        val text = PlanCheck.describe(issue, two(half))
        assertTrue(text.startsWith("Sopra la scala restano") && "c'è il tetto" in text, text)
        // La stanza di sopra sopra tutta la scala, o solo i primi gradini fuori (bassi): niente avviso.
        assertEquals(emptyList(), low(rect(2, "Camera", RoomType.Camera, 800.0, 600.0)))
        assertEquals(emptyList(), low(rect(2, "Camera", RoomType.Camera, 800.0, start.y - 60.0)))
    }

    @Test
    fun `porte nella casa come sara`() {
        // La porta tra soggiorno e cucina è da chiudere: nella casa come sarà in cucina non si entra.
        val closing = Building.single(FloorPlan(listOf(living.copy(openings = listOf(entrance, inner.copy(phase = Phase.Demolish), living.openings[2])), kitchen)))
        assertEquals(listOf(listOf(2L)), PlanCheck.of(closing).filter { it.kind == PlanCheck.Kind.NoDoor }.map { it.rooms })
        // Una porta nuova la apre; demolire il muro in comune (di tutte e due le stanze) anche.
        val opening = Building.single(FloorPlan(listOf(living.copy(openings = listOf(entrance, inner.copy(phase = Phase.New), living.openings[2])), kitchen)))
        assertTrue(PlanCheck.of(opening).none { it.kind == PlanCheck.Kind.NoDoor })
        val wall = Building.single(FloorPlan(listOf(
            living.copy(openings = listOf(entrance, living.openings[2]), wallPhases = mapOf(1 to Phase.Demolish)),
            kitchen.copy(wallPhases = mapOf(3 to Phase.Demolish)),
        )))
        assertTrue(PlanCheck.of(wall).none { it.kind == PlanCheck.Kind.NoDoor }, "${PlanCheck.of(wall).map { it.kind }}")
        // L'ingresso da chiudere: la casa resta senza ingresso.
        val noEntry = Building.single(FloorPlan(listOf(living.copy(openings = listOf(entrance.copy(phase = Phase.Demolish), inner, living.openings[2])), kitchen)))
        assertTrue(PlanCheck.of(noEntry).any { it.kind == PlanCheck.Kind.NoEntrance })
    }

    @Test
    fun `impianto elettrico solo se si mettono le prese`() {
        val window = Opening.default(1, OpeningKind.Window2, 2, 200.0).copy(height = 160.0)
        // Camera 400×400 (14,82 m² dentro i muri da 15): 4 punti presa e 1 punto luce.
        val bedroom = rect(1, "Camera", RoomType.Camera, 400.0, 400.0, openings = listOf(window))
        fun outlets(n: Int) = (1..n).map { com.sagoma.planimetria.model.Fixture.default(it.toLong(), com.sagoma.planimetria.model.FixtureKind.Outlet) }
        val light = com.sagoma.planimetria.model.Fixture.default(9, com.sagoma.planimetria.model.FixtureKind.CeilingLight)
        fun rulesOf(r: Room) = PlanCheck.of(Building.single(FloorPlan(listOf(r)))).filter { it.kind.rule }
        // Senza prese non si guarda (la casa non ha ancora l'impianto); con una luce sola nemmeno.
        assertEquals(emptyList(), rulesOf(bedroom))
        assertEquals(emptyList(), rulesOf(bedroom.copy(fixtures = listOf(light))))
        val few = rulesOf(bedroom.copy(fixtures = outlets(2))).single()
        assertEquals(PlanCheck.Kind.FewPoints, few.kind)
        assertEquals(
            "«Camera»: 2 punti presa e nessun punto luce. Per l'impianto elettrico di base (CEI 64-8, livello 1) servono almeno 4 punti presa e 1 punto luce.",
            PlanCheck.describe(few, Building.single(FloorPlan(listOf(bedroom)))),
        )
        assertEquals(emptyList(), rulesOf(bedroom.copy(fixtures = outlets(4) + light)))
        // Le prese messe in un'altra stanza bastano per guardare anche questa.
        val other = rect(2, "Camera 2", RoomType.Camera, 400.0, 400.0, Vec2(400.0, 0.0), openings = listOf(window.copy(id = 2)))
        val b = Building.single(FloorPlan(listOf(bedroom.copy(fixtures = outlets(4) + light), other)))
        assertEquals(listOf(2L), PlanCheck.of(b).filter { it.kind == PlanCheck.Kind.FewPoints }.map { it.rooms.single() })
    }

    @Test
    fun `elenco da condividere`() {
        // Due piani: al piano terra una camera piccola con la porta più larga del muro, al primo piano una scala che non arriva.
        val window = Opening.default(1, OpeningKind.Window2, 2, 150.0).copy(height = 160.0)
        val wide = Opening.default(2, OpeningKind.Door, 0, 150.0).copy(width = 400.0)
        val bedroom = rect(1, "Camera", RoomType.Camera, 280.0, 300.0, openings = listOf(window, wide))
        val b = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(bedroom))), Floor(2, "Primo piano", FloorPlan(listOf(bedroom.copy(openings = listOf(window, Opening.default(2, OpeningKind.Door, 0, 150.0))))))))
        val issues = PlanCheck.of(b)
        val text = PlanCheck.report(b, issues)
        val lines = text.lines()
        // Prima gli errori, poi le norme, ognuno con il piano davanti; la spiegazione delle norme sotto il titolo.
        assertEquals("Da correggere", lines[0])
        assertTrue(lines[1].startsWith("⚠ Piano terra · «Camera»"), text)
        assertTrue(text.contains("\nNorme per la casa (Italia)\n" + T.controllo.spiegaNorme + "\n📏 Piano terra · «Camera»: "), text)
        assertTrue(text.contains("\n📏 Primo piano · «Camera»: "), text)
        assertEquals(issues.size, lines.count { it.startsWith("⚠ ") || it.startsWith("? ") || it.startsWith("📏 ") })
        // Un piano solo: niente nome del piano. Senza problemi: una riga.
        val one = Building.single(FloorPlan(listOf(bedroom)))
        assertTrue(PlanCheck.report(one).lines()[1].startsWith("⚠ «Camera»"))
        assertEquals("✓ Nessun problema trovato.\n", PlanCheck.report(Building.single(FloorPlan()), emptyList()))
        val titles = PlanCheck.groups(issues).map { it.first }
        assertEquals("Da correggere", titles.first())
        assertEquals("Norme per la casa (Italia)", titles.last())
        assertEquals(titles, lines.filter { it in titles })
    }

    @Test
    fun `caloriferi e punti acqua dove mancano`() {
        val saved = com.sagoma.planimetria.model.FurnitureCatalog.items
        try {
            com.sagoma.planimetria.model.FurnitureCatalog.set(
                listOf(com.sagoma.planimetria.model.FurnitureCatalog.Item(model = "termo", label = "Termoarredo", category = "Bagno", width = 50.0, depth = 10.0, height = 120.0)),
            )
            fun fx(id: Long, kind: com.sagoma.planimetria.model.FixtureKind) = com.sagoma.planimetria.model.Fixture.default(id, kind)
            val radiator = fx(1, com.sagoma.planimetria.model.FixtureKind.Radiator)
            val water = fx(2, com.sagoma.planimetria.model.FixtureKind.WaterPoint)
            val living = rect(1, "Soggiorno", RoomType.Soggiorno, 500.0, 400.0, openings = listOf(entrance))
            val kitchen = rect(2, "Cucina", RoomType.Cucina, 300.0, 400.0, Vec2(500.0, 0.0))
            val bath = rect(3, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(800.0, 0.0))
            val corridor = rect(4, "Corridoio", RoomType.Corridoio, 300.0, 120.0, Vec2(0.0, 400.0))
            fun kinds(vararg r: Room, furniture: List<com.sagoma.planimetria.model.Furniture> = emptyList()) =
                PlanCheck.of(Building.single(FloorPlan(r.toList(), furniture = furniture)))
                    .filter { it.kind == PlanCheck.Kind.NoHeating || it.kind == PlanCheck.Kind.NoWater }.map { it.kind to it.rooms.single() }
            // Senza caloriferi né punti acqua in tutta la casa non si guarda.
            assertEquals(emptyList(), kinds(living, kitchen, bath, corridor))
            // Un calorifero nel soggiorno: mancano in cucina e bagno (il corridoio no). Un punto acqua in cucina: manca nel bagno.
            val out = kinds(living.copy(fixtures = listOf(radiator)), kitchen.copy(fixtures = listOf(water)), bath, corridor)
            assertEquals(listOf(PlanCheck.Kind.NoHeating to 2L, PlanCheck.Kind.NoHeating to 3L, PlanCheck.Kind.NoWater to 3L), out)
            assertEquals(
                "«Cucina»: nessun calorifero, mentre altre stanze ce l'hanno. Se si scalda in un altro modo va bene così.",
                PlanCheck.describe(PlanCheck.Issue(PlanCheck.Kind.NoHeating, 0, listOf(2L), com.sagoma.planimetria.geometry.Bounds(0.0, 0.0, 1.0, 1.0)), Building.single(FloorPlan(listOf(kitchen)))),
            )
            // Cucina a vista (muro in comune tolto): il calorifero del soggiorno scalda anche lei. Il termoarredo scalda il bagno.
            val towel = com.sagoma.planimetria.model.Furniture(1, "termo", Vec2(900.0, 100.0), width = 50.0, depth = 10.0, height = 120.0)
            val open = kinds(
                living.copy(fixtures = listOf(radiator), removedWalls = setOf(1)), kitchen.copy(removedWalls = setOf(3)), bath, corridor,
                furniture = listOf(towel),
            )
            assertEquals(emptyList(), open)
        } finally {
            com.sagoma.planimetria.model.FurnitureCatalog.set(saved)
        }
    }

    @Test
    fun `Correggi - l'avviso dice cosa ha cambiato`() {
        // Bagno senza finestra: «Correggi» ne mette una, e l'avviso lo dice.
        val bath = rect(3, "Bagno", RoomType.Bagno, 200.0, 250.0, Vec2(700.0, 0.0))
        val b = Building.single(FloorPlan(listOf(living, kitchen, bath)))
        val dark = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.BathNoWindow }
        val lit = assertNotNull(PlanCheck.fixed(b, dark))
        assertEquals(FixSummary.Summary(added = mapOf(FixSummary.Thing.Window to 1)), FixSummary.of(b, lit))
        assertEquals("Corretto un problema (aggiunto: 1 finestra): si annulla con ↶.", FixSummary.message(1, b, lit))
        // Più cose insieme: una finestra nuova, tre prese e una luce; una porta tolta, una finestra spostata, una porta più larga.
        val after = Building.single(FloorPlan(listOf(
            living.copy(openings = listOf(entrance.copy(width = 100.0), living.openings[2])),
            kitchen.copy(
                openings = listOf(kitchen.openings[0].copy(position = 100.0)),
                fixtures = (1L..3L).map { Fixture.default(it, FixtureKind.Outlet) } + Fixture.default(4, FixtureKind.CeilingLight),
            ),
            bath.copy(openings = listOf(Opening.default(5, OpeningKind.Window1, 1, 125.0))),
        )))
        val all = FixSummary.of(b, after)
        assertEquals(mapOf(FixSummary.Thing.Window to 1, FixSummary.Thing.Outlet to 3, FixSummary.Thing.Light to 1), all.added)
        assertEquals(mapOf(FixSummary.Thing.Door to 1), all.removed)
        assertEquals(mapOf(FixSummary.Thing.Window to 1), all.moved)
        assertEquals(mapOf(FixSummary.Thing.Door to 1), all.changed)
        assertEquals(
            "Corretti 4 problemi (aggiunto: 1 finestra, 3 prese e 1 luce; tolto: 1 porta; spostato: 1 finestra; cambiato: 1 porta): " +
                "un solo ↶ li annulla tutti.",
            FixSummary.message(4, b, after),
        )
        // La porta tolta da un muro e messa su un altro (la porta del bagno): spostata.
        val door = Building.single(FloorPlan(listOf(living.copy(openings = living.openings - inner), kitchen.copy(openings = kitchen.openings + inner.copy(id = 9, wallIndex = 2)))))
        assertEquals(FixSummary.Summary(moved = mapOf(FixSummary.Thing.Door to 1)), FixSummary.of(Building.single(FloorPlan(listOf(living, kitchen))), door))
        // Con stato di fatto e di progetto la porta chiusa è tolta.
        val phased = Building.single(FloorPlan(listOf(living.copy(openings = living.openings.map { if (it.id == 2L) it.copy(phase = Phase.Demolish) else it }), kitchen)))
        assertEquals(FixSummary.Summary(removed = mapOf(FixSummary.Thing.Door to 1)), FixSummary.of(Building.single(FloorPlan(listOf(living, kitchen))), phased))
        // Niente di cambiato: l'avviso di sempre.
        assertEquals(T.controllo.corretti(2), FixSummary.message(2, b, b))
        // Nelle altre lingue.
        T.lingua = com.sagoma.planimetria.testi.en.Inglese
        assertEquals(
            "Fixed 4 problems (added: 1 window, 3 sockets and 1 light; removed: 1 door; moved: 1 window; changed: 1 door): one ↶ undoes them all.",
            FixSummary.message(4, b, after),
        )
        T.lingua = com.sagoma.planimetria.testi.fr.Francese
        assertEquals("Un problème corrigé (ajouté : 1 fenêtre) : annulez avec ↶.", FixSummary.message(1, b, lit))
        T.lingua = com.sagoma.planimetria.testi.es.Spagnolo
        assertEquals("Un problema corregido (añadido: 1 ventana): se deshace con ↶.", FixSummary.message(1, b, lit))
    }

    @Test
    fun `Correggi - finestra che da su un'altra stanza`() {
        // La finestra della cucina disegnata sul muro in comune con il soggiorno: va, uguale, su un muro che dà fuori.
        val inside = Opening.default(4, OpeningKind.Window1, 3, 150.0)
        val b = Building.single(FloorPlan(listOf(living, kitchen.copy(openings = listOf(inside)))))
        val inner = PlanCheck.of(b).single { it.kind == PlanCheck.Kind.InnerWindow }
        assertTrue(PlanCheck.triesFirst(inner) && PlanCheck.canFix(b, inner))
        val fixed = assertNotNull(PlanCheck.fixed(b, inner))
        val w = fixed.floors[0].plan.rooms.single { it.id == 2L }.openings.single()
        assertEquals(inside.copy(wallIndex = 0), w)
        assertTrue(PlanCheck.of(fixed).none { it.kind == PlanCheck.Kind.InnerWindow })
        assertEquals(FixSummary.Summary(moved = mapOf(FixSummary.Thing.Window to 1)), FixSummary.of(b, fixed))
        // Con stato di fatto e di progetto quella che c'è si chiude e se ne apre una nuova.
        val phased = Building.single(FloorPlan(listOf(
            living.copy(openings = living.openings.map { if (it.id == 3L) it.copy(phase = Phase.New) else it }),
            kitchen.copy(openings = listOf(inside)),
        )))
        val again = PlanCheck.of(phased).single { it.kind == PlanCheck.Kind.InnerWindow }
        val opened = assertNotNull(PlanCheck.fixed(phased, again)).floors[0].plan.rooms.single { it.id == 2L }.openings
        assertEquals(listOf(inside.copy(phase = Phase.Demolish), inside.copy(id = 5, wallIndex = 0, phase = Phase.New)), opened)
        // La stanza chiusa tra le altre: non ha muri che danno fuori, niente «Correggi».
        val closed = rect(3, "Studio", RoomType.Camera, 200.0, 200.0, Vec2(400.0, 300.0), openings = listOf(Opening.default(20, OpeningKind.Window1, 0, 100.0)))
        val around = Building.single(FloorPlan(listOf(
            rect(4, "Camera", RoomType.Camera, 1000.0, 300.0, openings = listOf(Opening.default(10, OpeningKind.Window2, 0, 500.0))),
            rect(5, "Camera", RoomType.Camera, 400.0, 200.0, Vec2(0.0, 300.0)),
            rect(6, "Camera", RoomType.Camera, 400.0, 200.0, Vec2(600.0, 300.0)),
            rect(7, "Camera", RoomType.Camera, 1000.0, 300.0, Vec2(0.0, 500.0)),
            closed,
        )))
        val blind = PlanCheck.of(around).single { it.kind == PlanCheck.Kind.InnerWindow }
        assertTrue(!PlanCheck.canFix(around, blind))
    }
}
