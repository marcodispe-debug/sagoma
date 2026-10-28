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
}
