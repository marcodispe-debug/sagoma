package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    }

    @Test
    fun `porte e finestre doppie, troppo larghe, oltre il muro, su un muro tolto`() {
        // La stessa porta messa anche dalla cucina.
        val twice = kitchen.copy(openings = kitchen.openings + Opening.default(6, OpeningKind.Door, 3, 150.0))
        val d = check(living, twice).single { it.kind == PlanCheck.Kind.DoubleOpening }
        assertEquals(Selection.Opening(2, 6), d.selection)
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
        assertEquals(Selection.Opening(1, 3), check(open, kitchen).single { it.kind == PlanCheck.Kind.OpeningOnRemovedWall }.selection)
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
        assertEquals(0.2025, check(living, kitchen, tiny).single { it.kind == PlanCheck.Kind.TinyRoom }.a, 1e-6)
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
        // Mansarda (pareti tagliate dal tetto): l'altezza non si controlla, dipende dalle regole della regione.
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
}
