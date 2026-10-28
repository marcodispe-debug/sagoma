package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.HousingRules
import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.Furnishings
import com.sagoma.planimetria.geometry.ElectricRules
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.PhaseView
import com.sagoma.planimetria.geometry.Phases
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.Swings
import com.sagoma.planimetria.geometry.Triangulation
import com.sagoma.planimetria.geometry.arcOf
import com.sagoma.planimetria.geometry.expandCurves
import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.usableArea
import com.sagoma.planimetria.geometry.wallShift
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * «Controlla la pianta»: errori di disegno che di solito sfuggono e rovinano metri quadri, computo e 3D (stanze sovrapposte o
 * staccate di pochi centimetri, muri che si incrociano, la stessa porta messa due volte), cose da controllare (stanze senza
 * porta, casa senza ingresso, scale che non arrivano, ripide, strette o con poca altezza sopra la testa, ante che aprendosi
 * urtano altre ante, mobili o muri) e minimi delle
 * norme italiane per le case ([HousingRules]: finestre, altezze, metri quadri; [ElectricRules]: punti presa e luce, se nella casa
 * si stanno mettendo le prese). Ogni problema dice quale piano e quale zona
 * inquadrare e cosa selezionare.
 */
object PlanCheck {
    /**
     * Tipi di problema. `error`: di sicuro sbagliato; altrimenti da controllare (può essere voluto). `rule`: sotto un minimo delle
     * norme italiane per le case (DM 5 luglio 1975).
     */
    enum class Kind(val error: Boolean, val rule: Boolean = false) {
        CrossedWalls(true),
        Overlap(true),
        NearWalls(true),
        DoubleOpening(true),
        ZeroWall(true),
        OpeningTooWide(true),
        OpeningOverWall(true),
        OpeningOnRemovedWall(true),
        NoDoor(false),
        NoDoors(false),
        NoEntrance(false),
        InnerWindow(false),
        TinyRoom(false),
        StairNoFloor(false),
        StairOutside(false),
        StairSteep(false),
        StairNarrow(false),
        StairLowHead(false),
        SwingClash(false),
        SwingHitsThing(false),
        SwingHitsWall(false),
        NoHeating(false),
        NoWater(false),
        FixtureOverWall(false),
        FurnitureInWall(false),
        FewWindows(false, rule = true),
        NoWindows(false, rule = true),
        NoFloorWindows(false, rule = true),
        LowCeiling(false, rule = true),
        LowAttic(false, rule = true),
        SmallBedroom(false, rule = true),
        SmallLiving(false, rule = true),
        BathNoWindow(false, rule = true),
        BathOnLiving(false, rule = true),
        BathIncomplete(false, rule = true),
        SmallHome(false, rule = true),
        SmallStudio(false, rule = true),
        FewPoints(false, rule = true),
    }

    /**
     * Un problema al piano `floor` (indice in [Building.floors]). `rooms`: le stanze coinvolte, nell'ordine del testo; `area`: la
     * zona da inquadrare (cm); `selection`: cosa selezionare (null: la prima stanza a fuoco). `a`, `b`: misure per il testo (m² o
     * cm); `opening`: tipo dell'apertura di cui si parla (`opening2` dell'altra); `furniture`: il mobile urtato (senza: il
     * calorifero).
     */
    data class Issue(
        val kind: Kind,
        val floor: Int,
        val rooms: List<Long>,
        val area: Bounds,
        val selection: Selection? = null,
        val a: Double = 0.0,
        val b: Double = 0.0,
        val opening: OpeningKind? = null,
        val opening2: OpeningKind? = null,
        val furniture: Long? = null,
        val count: Int = 0,
    )

    /** Oltre questi non si cerca altro: una pianta così ha bisogno di essere ridisegnata, non di un elenco infinito. */
    const val MAX_ISSUES = 200

    /** Stanze sovrapposte: da questa superficie in comune (cm², 0,1 m²) in su. Meno è il tratto di muro che si accavalla. */
    const val OVERLAP_MIN_CM2 = 1_000.0

    /** Muri quasi accostati: fessura tra i due muri più sottile di così (cm), o muri che si accavallano. */
    const val NEAR_GAP_CM = 8.0

    /** Muri quasi accostati: tratto affacciato almeno così lungo (cm). */
    const val NEAR_MIN_RUN_CM = 30.0

    /** Muro lungo meno di così (cm): due angoli uno sull'altro. */
    const val ZERO_WALL_CM = 0.5

    /** Stanza più piccola di così (cm², 0,5 m²): forse un pezzo rimasto. */
    const val TINY_ROOM_CM2 = 5_000.0

    /**
     * Stanze con troppi angoli (file rovinati): si saltano i controlli che costano col quadrato (muri incrociati) o col cubo
     * (triangoli per le sovrapposizioni) degli angoli. Una stanza tonda srotolata ne ha meno di 200.
     */
    private const val MAX_POINTS_CROSS = 1_500
    private const val MAX_POINTS_SHAPE = 500

    /** Un mobile che entra in un muro meno di così (cm) va bene: appoggiato alla parete, a occhio o con l'aggancio. */
    private const val FURNITURE_IN_WALL_CM = 3.0

    /** Muri dritti del piano (mezzeria e mezzo spessore) e vani di porte e finestre, per vedere quanto un mobile ci entra. */
    private class WallBands(val walls: List<Band>, val gaps: List<Band>) {
        class Band(val a: Vec2, val b: Vec2, val half: Double)

        /** Quanto entra nei muri (cm dalla faccia, il più a fondo) e in quale; `across`: un lato passa da una parte all'altra. */
        class Hit(val depth: Double, val wall: Band?, val across: Boolean)

        private fun inGap(q: Vec2) = gaps.any { g -> Polygon.distanceToSegment(q, g.a, g.b) <= g.half }

        /** Il mobile nei muri, o null se non conta (tappeto, misure rovinate) o non ha muri vicini. */
        fun penetration(f: Furniture): Hit? {
            if (f.height <= 3.0 || !(f.width > 0.0) || !(f.depth > 0.0) || !f.center.x.isFinite() || !f.center.y.isFinite()) return null
            val outline = Furnishings.outline(f)
            // Solo i muri vicini (rettangoli che si toccano): una casa grande ha centinaia di muri e di mobili.
            val near = boundsOf(outline)
            val close = walls.filter { w -> boundsOf(listOf(w.a, w.b)).grow(w.half).touches(near) }
            if (close.isEmpty()) return null
            val perimeter = outline.indices.sumOf { outline[it].distanceTo(outline[(it + 1) % outline.size]) }
            val step = max(5.0, perimeter / 400)
            var depth = 0.0
            var deepest: Band? = null
            var across = false
            for (k in outline.indices) {
                val a = outline[k]
                val b = outline[(k + 1) % outline.size]
                val n = max(1, (a.distanceTo(b) / step).toInt())
                for (w in close) {
                    // Un lato del mobile che passa da una parte all'altra del muro (fuori dalle sue punte, dove i muri si
                    // incontrano negli angoli, e dai vani).
                    val x = crossing(a, b, w.a, w.b)
                    if (x != null && x.distanceTo(w.a) > 1.0 && x.distanceTo(w.b) > 1.0 && !inGap(x)) across = true
                    val dir = w.b - w.a
                    val len2 = dir dot dir
                    val home = dir.cross(f.center - w.a)
                    for (s in 0..n) {
                        val q = a + (b - a) * (s.toDouble() / n)
                        val dist = Polygon.distanceToSegment(q, w.a, w.b)
                        if (dist >= w.half || inGap(q)) continue
                        // Oltre la mezzeria (dalla parte opposta al centro del mobile) è ancora più dentro: con il lato che la
                        // passa nel vano di una porta vicina non si vede che il mobile attraversa il muro.
                        val t = ((q - w.a) dot dir) / len2
                        val beyond = t in 0.0..1.0 && home * dir.cross(q - w.a) < 0
                        val d = if (beyond) w.half + dist else w.half - dist
                        if (d > depth) { depth = d; deepest = w }
                    }
                }
            }
            return Hit(depth, deepest, across)
        }

        companion object {
            fun of(p: FloorPlan): WallBands {
                val walls = p.rooms.flatMap { r ->
                    (0 until r.wallCount).filter { !r.isRemoved(it) && r.arcOf(it) == null }.map { i ->
                        val shift = r.outwardOf(i) * r.wallShift(i)
                        Band(r.wallStart(i) + shift, r.wallEnd(i) + shift, r.thicknessOf(i) / 2)
                    }
                }.filter { it.a.distanceTo(it.b) >= 1.0 && it.half.isFinite() }
                // Vani di porte e finestre (di tutte e due le stanze del muro in comune): lì il mobile non entra nel muro.
                val gaps = p.rooms.flatMap { r ->
                    r.openings.filter { it.wallIndex in 0 until r.wallCount && !r.isRemoved(it.wallIndex) }.map { o ->
                        val (a, b) = Openings.span(r, o)
                        Band(a, b, r.thicknessOf(o.wallIndex) / 2 + 1.0)
                    }
                }
                return WallBands(walls, gaps)
            }
        }
    }

    /** «Correggi» non fa porte e finestre più strette o più basse di così (cm). */
    private const val MIN_FIXED_OPENING_CM = 20.0

    /** Coppie di muri da provare per due stanze vicine, al massimo. */
    private const val MAX_WALL_PAIRS = 200_000

    /** Aperture da cui si passa: porte, passaggi e porte-finestre. */
    private fun passable(k: OpeningKind) = k == OpeningKind.Door || k == OpeningKind.Door2 || k == OpeningKind.Passage ||
        k == OpeningKind.Balcony1 || k == OpeningKind.Balcony2 || k == OpeningKind.FrenchDoor

    /**
     * Problemi che [fixed] sa correggere da solo: solo per questi c'è «Correggi». La stessa apertura messa due volte solo se le due
     * sono dello stesso tipo (una porta e una finestra nello stesso punto: quale togliere lo sceglie chi disegna). L'apertura su un
     * muro lungo quasi zero no: si corregge il muro (l'apertura passa su quello dopo).
     */
    fun canFix(i: Issue): Boolean = when (i.kind) {
        Kind.FurnitureInWall -> i.b == 0.0
        Kind.DoubleOpening -> i.opening != null && i.opening == i.opening2
        Kind.OpeningTooWide -> i.b >= MIN_FIXED_OPENING_CM
        Kind.FixtureOverWall, Kind.OpeningOverWall, Kind.ZeroWall -> true
        else -> false
    }

    /**
     * La casa con il problema `i` corretto, o null se non si riesce da soli (o la correzione non lo toglie, o non c'è più): il
     * mobile dentro un muro torna fino alla parete; l'impianto più alto del muro si abbassa; la porta o la finestra più alta del
     * muro si accorcia, quella più larga del muro si stringe a quanto se ne vede; quella messa due volte resta una sola; il muro
     * lungo zero si unisce al muro dopo (come «Unisci al muro dopo»).
     */
    fun fixed(b: Building, i: Issue): Building? {
        if (!canFix(i)) return null
        val plan = b.floors.getOrNull(i.floor)?.plan ?: return null
        fun Room.with(o: Opening) = copy(openings = openings.map { if (it.id == o.id) o else it })
        val out: FloorPlan = when (i.kind) {
            Kind.FurnitureInWall -> i.furniture?.let { pushedOut(plan, it) }
            Kind.ZeroWall -> (i.selection as? Selection.Wall)?.let { s ->
                val r = plan.room(s.roomId)?.takeIf { s.index in 0 until it.wallCount } ?: return null
                r.removeCorner((s.index + 1) % r.wallCount)?.let(plan::replace)
            }
            Kind.DoubleOpening -> (i.selection as? Selection.Opening)?.let { s ->
                val r = plan.room(s.roomId) ?: return null
                if (r.openings.none { it.id == s.openingId }) null else plan.replace(r.copy(openings = r.openings.filter { it.id != s.openingId }))
            }
            Kind.FixtureOverWall -> (i.selection as? Selection.Fixture)?.let { s ->
                val r = plan.room(s.roomId) ?: return null
                val f = r.fixtures.firstOrNull { it.id == s.fixtureId } ?: return null
                // La cima 2 cm sotto quella del muro.
                val elevation = kotlin.math.floor(f.elevation - (i.a - i.b) - 2.0)
                if (elevation < 0) null else plan.replace(r.copy(fixtures = r.fixtures.map { if (it.id == f.id) f.copy(elevation = elevation) else it }))
            }
            else -> (i.selection as? Selection.Opening)?.let { s ->
                val r = plan.room(s.roomId) ?: return null
                val o = r.openings.firstOrNull { it.id == s.openingId } ?: return null
                val fixedOpening = if (i.kind == Kind.OpeningOverWall) {
                    val h = kotlin.math.floor(i.b - o.bottom)
                    if (h < MIN_FIXED_OPENING_CM) null else o.copy(height = h)
                } else {
                    // Larga quanto la parte che si vede (tutto il muro), con il centro lì.
                    val len = r.wallLength(o.wallIndex)
                    val w = kotlin.math.floor(min(o.width, len))
                    if (w < MIN_FIXED_OPENING_CM) null else o.copy(width = w, position = Openings.clampPosition(len, w, o.position))
                }
                fixedOpening?.let { plan.replace(r.with(it)) }
            }
        } ?: return null
        // Già corretto (l'elenco era vecchio): niente da fare.
        if (out == plan) return null
        val result = b.copy(floors = b.floors.mapIndexed { k, f -> if (k == i.floor) f.copy(plan = out).synced() else f })
        return result.takeIf { of(it).none { x -> x.kind == i.kind && x.floor == i.floor && x.selection == i.selection } }
    }

    /** Il mobile `id` spostato fuori dai muri in cui entra (in un angolo sono due) fino alla parete, o null se non si riesce. */
    private fun pushedOut(plan: FloorPlan, id: Long): FloorPlan? {
        var f = plan.furniture.firstOrNull { it.id == id } ?: return null
        val walls = WallBands.of(Phases.view(plan, PhaseView.Project))
        // Senza muri vicini (o con misure rovinate, e allora non si sposta e non si corregge) va bene così.
        repeat(3) {
            val hit = walls.penetration(f) ?: return plan.replace(f)
            if (hit.across) return null
            val w = hit.wall ?: return plan.replace(f)
            if (hit.depth < 0.05) return plan.replace(f)
            // Dalla parte del muro in cui sta il centro del mobile.
            val n = (w.b - w.a).normalized().perp().let { if (((f.center - w.a) dot it) < 0) it * -1.0 else it }
            f = f.copy(center = f.center + n * (hit.depth + 0.1))
        }
        val last = walls.penetration(f) ?: return plan.replace(f)
        return if (!last.across && last.depth < FURNITURE_IN_WALL_CM) plan.replace(f) else null
    }

    fun of(b: Building): List<Issue> {
        val out = mutableListOf<Issue>()
        var outside = false
        var anyIndoor = false
        var doorless = false
        val allRooms = b.floors.flatMap { it.plan.rooms }
        val wired = ElectricRules.wired(allRooms)
        val heated = allRooms.any { r -> r.fixtures.any { it.kind == FixtureKind.Radiator } }
        val plumbed = allRooms.any { r -> r.fixtures.any { it.kind == FixtureKind.WaterPoint } }
        for ((f, floor) in b.floors.withIndex()) {
            val plan = sane(floor.plan)
            val below = b.floors.getOrNull(f - 1)
            val above = b.floors.getOrNull(f + 1)
            FloorCheck(plan, f, out, wired, heated, plumbed).run {
                walls()
                pairs()
                openings()
                fixtures()
                furnitureInWalls()
                swings()
                doors(below?.let { sane(it.plan) to it.levelHeight }, floor.levelHeight)
                stairs(floor.levelHeight, above?.let { sane(it.plan) }, f == b.floors.lastIndex)
                rules()
                bathDoors()
                systems()
                if (leadsOutside) outside = true
                if (noDoors) doorless = true
            }
            if (plan.rooms.any { !it.outdoor }) anyIndoor = true
            if (out.size >= MAX_ISSUES) return out.take(MAX_ISSUES)
        }
        // Senza porta d'ingresso: non se il piano non ha ancora nessuna porta (c'è già quell'avviso), né con le scale (la casa
        // disegnata può essere solo una parte: la mansarda in cui si sale dal piano di sotto).
        if (anyIndoor && !outside && !doorless && b.floors.all { it.plan.stairs.isEmpty() }) {
            val f = b.ground.coerceIn(b.floors.indices)
            val plan = sane(b.floors[f].plan)
            plan.rooms.firstOrNull { !it.outdoor }?.let { r -> out += Issue(Kind.NoEntrance, f, listOf(r.id), boundsOf(plan.rooms.flatMap { it.points })) }
        }
        if (out.size < MAX_ISSUES) home(b, out)
        // Prima gli errori, poi le cose da controllare e le norme; ognuno nell'ordine dei piani.
        return out.sortedWith(compareBy({ !it.kind.error }, { it.kind.rule }, { it.floor })).take(MAX_ISSUES)
    }

    /**
     * Norme per tutta la casa (se ha un bagno e un soggiorno o una cucina: è un'abitazione intera, non una stanza da arredare o un
     * pezzo di casa): almeno un bagno con vaso, bidè, vasca o doccia e lavabo (se nei bagni ci sono già dei sanitari), metri quadri
     * per le persone che ci dormono, monolocale.
     */
    private fun home(b: Building, out: MutableList<Issue>) {
        val floors = b.floors.mapIndexed { f, fl -> f to Phases.view(sane(fl.plan), PhaseView.Project) }
        val baths = floors.flatMap { (f, p) -> p.rooms.filter { it.type == RoomType.Bagno && !it.outdoor && !HousingRules.demolished(it) }.map { Triple(f, p, it) } }
        if (baths.isEmpty()) return
        val living = floors.any { (_, p) -> p.rooms.any { (it.type == RoomType.Soggiorno || it.type == RoomType.Cucina) && !it.outdoor && !HousingRules.demolished(it) } }
        if (!living) return
        val found = baths.map { (f, p, r) -> Triple(f, r, HousingRules.sanitaries(p, r)) }
        if (found.any { it.third.isNotEmpty() } && found.none { it.third.size == HousingRules.Sanitary.entries.size }) {
            // Il bagno più completo, con quello che manca.
            val (f, r, has) = found.maxBy { it.third.size }
            val mask = has.fold(0) { m, s -> m or (1 shl s.ordinal) }
            out += Issue(Kind.BathIncomplete, f, listOf(r.id), Polygon.bounds(r.points).grow(60.0), count = mask)
        }
        val indoor = floors.flatMap { (f, p) -> p.rooms.filter { !it.outdoor && !HousingRules.demolished(it) && it.points.size >= 3 }.map { f to it } }
        val area = indoor.sumOf { it.second.usableArea() }
        val persons = floors.sumOf { (_, p) -> p.furniture.sumOf { HousingRules.sleepers(it) } }
        val spaces = floors.sumOf { (_, p) -> HousingRules.livingSpaces(p).size }
        val f = b.ground.coerceIn(b.floors.indices)
        val box = boundsOf(indoor.filter { it.first == f }.flatMap { it.second.points }.ifEmpty { indoor.flatMap { it.second.points } }).grow(60.0)
        fun hundredths(cm2: Double) = (cm2 / 100).roundToInt()
        if (spaces == 1) {
            val min = HousingRules.minStudio(persons)
            if (hundredths(area) < hundredths(min)) out += Issue(Kind.SmallStudio, f, emptyList(), box, a = area / 10_000, b = min / 10_000, count = persons)
        } else if (persons >= 2) {
            val min = HousingRules.minHome(persons)
            if (hundredths(area) < hundredths(min)) out += Issue(Kind.SmallHome, f, emptyList(), box, a = area / 10_000, b = min / 10_000, count = persons)
        }
    }

    /** I problemi nei tre gruppi dell'elenco, con il titolo: da correggere, da controllare, norme. Solo i gruppi non vuoti. */
    fun groups(issues: List<Issue>): List<Pair<String, List<Issue>>> = listOf(
        T.controllo.daCorreggere to issues.filter { it.kind.error },
        T.controllo.daControllare to issues.filter { !it.kind.error && !it.kind.rule },
        T.controllo.daNorme to issues.filter { it.kind.rule },
    ).filter { it.second.isNotEmpty() }

    /** Segno davanti al problema nell'elenco. */
    fun mark(kind: Kind): String = when {
        kind.error -> "⚠"
        kind.rule -> "📏"
        else -> "?"
    }

    /**
     * L'elenco come testo da mandare (al tecnico, all'impresa) o da tenere: i gruppi con i problemi, il piano davanti se la casa ne
     * ha più di uno, la spiegazione delle norme.
     */
    fun report(b: Building, issues: List<Issue> = of(b)): String = buildString {
        val t = T.controllo
        if (issues.isEmpty()) {
            append("✓ ").append(t.nessunProblema).append('\n')
            return@buildString
        }
        val floors = b.floors.size > 1
        for ((k, group) in groups(issues).withIndex()) {
            if (k > 0) append('\n')
            append(group.first).append('\n')
            if (group.second.first().kind.rule) append(t.spiegaNorme).append('\n')
            for (i in group.second) {
                val where = if (floors) b.floors.getOrNull(i.floor)?.name?.let { "$it · " } ?: "" else ""
                append(mark(i.kind)).append(' ').append(where).append(describe(i, b)).append('\n')
            }
        }
        if (issues.size >= MAX_ISSUES) append('\n').append(t.troppi(MAX_ISSUES)).append('\n')
    }

    /** Testo del problema nella lingua dell'app, con i nomi delle stanze. */
    fun describe(i: Issue, b: Building): String {
        val t = T.controllo
        val plan = b.floors.getOrNull(i.floor)?.plan
        fun name(k: Int): String = i.rooms.getOrNull(k)?.let { plan?.room(it) }?.let { t.nome(it.name.ifBlank { it.type.label }) } ?: "?"
        fun cm(v: Double): String = v.roundToInt().toString()
        fun m2(v: Double): String = formatDecimal(v, 2)
        fun cm1(v: Double): String = formatDecimal(v, 1).removeSuffix("${T.lingua.virgola}0")
        // Ambiente fatto di più stanze (soggiorno con cucina a vista): tutti i nomi.
        val all = i.rooms.indices.joinToString(" + ") { name(it) }
        val cosa = i.opening?.let { T.lingua.minuscola(it.label) } ?: ""
        return when (i.kind) {
            Kind.CrossedWalls -> t.muriIncrociati(name(0))
            Kind.Overlap -> t.sovrapposte(name(0), name(1), formatDecimal(i.a, 2))
            Kind.NearWalls -> if (i.a > 0) t.fessura(name(0), name(1), cm(max(i.a, 1.0))) else t.accavallati(name(0), name(1), cm(max(-i.a, 1.0)))
            Kind.DoubleOpening -> t.aperturaDoppia(cosa, i.rooms.indices.joinToString(" / ") { name(it) })
            Kind.ZeroWall -> t.muroZero(name(0), T.pannelli.unisciMuroDopo)
            Kind.OpeningTooWide -> t.troppoLarga(name(0), cosa, cm(i.a), cm(i.b))
            Kind.OpeningOverWall -> t.sopraIlMuro(name(0), cosa, cm(i.a), cm(i.b))
            Kind.OpeningOnRemovedWall -> t.suMuroTolto(name(0), cosa)
            Kind.FurnitureInWall -> {
                val thing = i.furniture?.let { id -> plan?.furniture?.firstOrNull { it.id == id } }
                    ?.let { f -> FurnitureCatalog.item(f.model)?.nome?.substringBefore(" · ") ?: T.computo.arredo } ?: T.computo.arredo
                if (i.b > 0) t.mobileAttraversaMuro(t.nome(thing)) else t.mobileNelMuro(t.nome(thing), cm(i.a))
            }
            Kind.FixtureOverWall -> {
                val s = i.selection as? Selection.Fixture
                val f = s?.let { plan?.room(it.roomId)?.fixtures?.firstOrNull { x -> x.id == it.fixtureId } }
                t.sopraIlMuro(name(0), f?.let { T.lingua.minuscola(it.kind.label) } ?: "?", cm(i.a), cm(i.b))
            }
            Kind.NoDoor -> t.senzaPorta(name(0))
            Kind.NoDoors -> t.nessunaPorta
            Kind.NoEntrance -> t.senzaIngresso
            Kind.InnerWindow -> t.finestraInterna(name(0), name(1))
            Kind.TinyRoom -> t.piccolissima(name(0), formatDecimal(i.a, 2))
            Kind.StairNoFloor -> t.scalaSenzaPiano
            Kind.StairOutside -> t.scalaFuori(b.floors.getOrNull(i.floor + 1)?.name ?: "")
            Kind.NoHeating -> t.senzaCalorifero(name(0))
            Kind.NoWater -> t.senzaAcqua(name(0))
            Kind.StairSteep -> t.scalaRipida(cm1(i.a), if (i.b > 0) cm1(i.b) else null)
            Kind.StairNarrow -> t.scalaStretta(cm(i.a))
            Kind.StairLowHead -> t.scalaTestaBassa(cm(i.a))
            Kind.SwingClash -> t.anteSiUrtano(i.rooms.indices.joinToString(" / ") { name(it) }, cosa, i.opening2?.let { T.lingua.minuscola(it.label) } ?: "")
            Kind.SwingHitsThing -> {
                val thing = i.furniture?.let { id -> plan?.furniture?.firstOrNull { it.id == id } }
                    ?.let { f -> FurnitureCatalog.item(f.model)?.nome?.substringBefore(" · ") ?: T.computo.arredo } ?: FixtureKind.Radiator.label
                t.antaUrta(name(0), cosa, t.nome(thing))
            }
            Kind.SwingHitsWall -> t.antaUrtaMuro(name(0), cosa)
            Kind.FewWindows -> t.pocaLuce(all, m2(i.a), m2(i.b), HousingRules.fraction(i.a / i.b), m2(i.b * HousingRules.MIN_RATIO))
            Kind.NoWindows -> t.senzaFinestre(all, m2(i.b * HousingRules.MIN_RATIO))
            Kind.NoFloorWindows -> t.nessunaFinestra
            Kind.LowCeiling -> if (i.b < HousingRules.MIN_HEIGHT_LIVING_CM) t.soffittoBassoServizi(name(0), formatDecimal(i.a / 100, 2))
            else t.soffittoBasso(name(0), formatDecimal(i.a / 100, 2))
            Kind.LowAttic -> t.mansardaBassa(name(0), formatDecimal(i.a / 100, 2))
            Kind.SmallBedroom -> if (i.b * 10_000 >= HousingRules.MIN_DOUBLE_BEDROOM_CM2) t.cameraPerDuePiccola(name(0), m2(i.a)) else t.cameraPiccola(name(0), m2(i.a))
            Kind.BathNoWindow -> t.bagnoSenzaFinestra(name(0))
            Kind.BathOnLiving -> t.bagnoSuCucina(name(0), name(1))
            Kind.BathIncomplete -> t.bagnoIncompleto(
                name(0),
                HousingRules.Sanitary.entries.filter { (i.count shr it.ordinal) and 1 == 0 }.joinToString(", ") { s ->
                    when (s) {
                        HousingRules.Sanitary.Toilet -> t.vaso
                        HousingRules.Sanitary.Bidet -> t.bide
                        HousingRules.Sanitary.BathOrShower -> t.vascaODoccia
                        HousingRules.Sanitary.Basin -> t.lavabo
                    }
                },
            )
            Kind.SmallHome -> t.casaPiccola(m2(i.a), i.count, formatDecimal(i.b, 0))
            Kind.SmallStudio -> t.monolocalePiccolo(m2(i.a), formatDecimal(i.b, 0), i.count >= 2)
            Kind.FewPoints -> t.impiantoScarso(name(0), t.puntiPresa(i.a.roundToInt()), t.puntiLuce(i.b.roundToInt()), t.puntiPresa(i.count / 10), t.puntiLuce(i.count % 10))
            Kind.SmallLiving -> t.soggiornoPiccolo(all, m2(i.a))
        }
    }

    /** Solo le stanze con cui si può ragionare: almeno tre angoli, numeri veri. */
    private fun sane(plan: FloorPlan): FloorPlan = plan.copy(
        rooms = plan.rooms.filter { r ->
            r.points.size >= 3 && r.points.all { it.x.isFinite() && it.y.isFinite() } && r.thicknesses.all { it.isFinite() && it >= 0 }
        },
    )

    private fun boundsOf(pts: List<Vec2>): Bounds =
        if (pts.isEmpty()) Bounds(0.0, 0.0, 0.0, 0.0) else Polygon.bounds(pts)

    private fun Bounds.grow(d: Double) = Bounds(minX - d, minY - d, maxX + d, maxY + d)
    private fun Bounds.touches(o: Bounds) = minX <= o.maxX && o.minX <= maxX && minY <= o.maxY && o.minY <= maxY

    /** Superficie in comune (cm²) tra il poligono semplice `a` e quello fatto dai triangoli `b`, e dove sta. */
    internal fun overlapArea(a: List<Vec2>, b: List<List<Vec2>>): Pair<Double, Bounds?> {
        var sum = 0.0
        var box: Bounds? = null
        val ab = Polygon.bounds(a)
        for (t in b) {
            val tb = Polygon.bounds(t)
            if (tb.minX >= ab.maxX || ab.minX >= tb.maxX || tb.minY >= ab.maxY || ab.minY >= tb.maxY) continue
            val piece = Polygon.clipConvex(a, t)
            if (piece.size < 3) continue
            val s = Polygon.area(piece)
            if (s < 1.0) continue
            sum += s
            val pb = Polygon.bounds(piece)
            box = box?.union(pb) ?: pb
        }
        return sum to box
    }

    /** Triangoli del poligono semplice `p`. */
    internal fun triangles(p: List<Vec2>): List<List<Vec2>> = Triangulation.triangulate(p).map { (i, j, k) -> listOf(p[i], p[j], p[k]) }

    /** Due segmenti si tagliano in un punto interno a entrambi (non agli estremi). */
    internal fun crossing(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Vec2? {
        val r = b - a
        val s = d - c
        val den = r.x * s.y - r.y * s.x
        if (abs(den) < 1e-9) return null
        val t = ((c.x - a.x) * s.y - (c.y - a.y) * s.x) / den
        val u = ((c.x - a.x) * r.y - (c.y - a.y) * r.x) / den
        val eps = 1e-6
        return if (t > eps && t < 1 - eps && u > eps && u < 1 - eps) a + r * t else null
    }

    /**
     * Controlli di un piano. `wired`: nella casa ci sono delle prese, si guarda anche l'impianto elettrico; `heated`, `plumbed`: ci
     * sono caloriferi o punti acqua, si guarda dove mancano.
     */
    private class FloorCheck(
        val plan: FloorPlan,
        val floor: Int,
        val out: MutableList<Issue>,
        val wired: Boolean = false,
        val heated: Boolean = false,
        val plumbed: Boolean = false,
    ) {
        val expanded = plan.expandCurves().plan
        val rooms = plan.rooms
        val boxes: Map<Long, Bounds> = rooms.associate { it.id to Polygon.bounds(it.points) }
        val crossed = mutableSetOf<Long>()
        var leadsOutside = false
        var noDoors = false

        fun full() = out.size >= MAX_ISSUES

        fun add(i: Issue) {
            if (!full()) out += i
        }

        /** Muri che si incrociano, muri lunghi zero, stanze piccolissime. */
        fun walls() {
            for (r in expanded.rooms) {
                val p = r.points
                if (p.size > MAX_POINTS_CROSS) continue
                val n = p.size
                var hit: Vec2? = null
                loop@ for (i in 0 until n) for (j in i + 2 until n) {
                    if (i == 0 && j == n - 1) continue
                    hit = crossing(p[i], p[(i + 1) % n], p[j], p[(j + 1) % n])
                    if (hit != null) break@loop
                }
                if (hit != null) {
                    crossed += r.id
                    add(Issue(Kind.CrossedWalls, floor, listOf(r.id), Bounds(hit.x, hit.y, hit.x, hit.y).grow(100.0)))
                }
            }
            for (r in rooms) {
                for (i in 0 until r.wallCount) if (r.wallLength(i) < ZERO_WALL_CM) {
                    val c = r.wallStart(i)
                    add(Issue(Kind.ZeroWall, floor, listOf(r.id), Bounds(c.x, c.y, c.x, c.y).grow(100.0), Selection.Wall(r.id, i)))
                    break
                }
                if (r.id in crossed) continue
                val area = r.interiorArea()
                if (area < TINY_ROOM_CM2) add(Issue(Kind.TinyRoom, floor, listOf(r.id), boxes.getValue(r.id).grow(50.0), a = area / 10_000))
            }
        }

        /** Contorno interno di una stanza in triangoli, con la sua superficie: per le sovrapposizioni. */
        private class Shape(val poly: List<Vec2>, val tris: List<List<Vec2>>, val area: Double)

        /** Coppie di stanze vicine: sovrapposte o con muri paralleli quasi attaccati. */
        fun pairs() {
            val shapes = mutableMapOf<Long, Shape?>()
            val byId = expanded.rooms.associateBy { it.id }
            fun shape(id: Long): Shape? = shapes.getOrPut(id) {
                val r = byId[id]?.takeIf { it.points.size <= MAX_POINTS_SHAPE && it.id !in crossed } ?: return@getOrPut null
                val p = r.interior()
                val area = Polygon.area(p)
                // Contorno interno rovesciato o nullo (stanza più stretta dei suoi muri): non è un poligono di cui fidarsi.
                if (p.size < 3 || area < 1.0 || !p.all { it.x.isFinite() && it.y.isFinite() }) null else Shape(p, triangles(p), area)
            }
            val sorted = rooms.sortedBy { boxes.getValue(it.id).minX }
            for ((k, a) in sorted.withIndex()) {
                val ba = boxes.getValue(a.id).grow(60.0)
                for (m in k + 1 until sorted.size) {
                    val b = sorted[m]
                    val bb = boxes.getValue(b.id)
                    if (bb.minX > ba.maxX) break
                    if (!ba.touches(bb)) continue
                    if (full()) return
                    overlap(a, b, shape(a.id), shape(b.id))
                    if (a.wallCount.toLong() * b.wallCount <= MAX_WALL_PAIRS) near(a, b)
                }
            }
        }

        private fun overlap(a: Room, b: Room, sa: Shape?, sb: Shape?) {
            if (sa == null || sb == null) return
            val (s, box) = overlapArea(sa.poly, sb.tris)
            if (s < OVERLAP_MIN_CM2 || box == null) return
            // A fuoco la più piccola: di solito è quella disegnata sopra l'altra per sbaglio.
            val (small, big) = if (sa.area <= sb.area) a to b else b to a
            add(Issue(Kind.Overlap, floor, listOf(small.id, big.id), box.grow(60.0), a = s / 10_000))
        }

        private fun near(a: Room, b: Room) {
            var best: Issue? = null
            for (i in 0 until a.wallCount) {
                if (a.isRemoved(i) || a.arcOf(i) != null) continue
                val sa = a.wallStart(i)
                val ea = a.wallEnd(i)
                val la = sa.distanceTo(ea)
                if (la < NEAR_MIN_RUN_CM) continue
                val u = (ea - sa) / la
                val na = a.outwardOf(i)
                for (j in 0 until b.wallCount) {
                    if (b.isRemoved(j) || b.arcOf(j) != null) continue
                    val sb = b.wallStart(j)
                    val eb = b.wallEnd(j)
                    val lb = sb.distanceTo(eb)
                    if (lb < NEAR_MIN_RUN_CM) continue
                    val v = (eb - sb) / lb
                    if (abs(u.x * v.y - u.y * v.x) > 0.01) continue
                    // Affacciati: l'altra stanza sta dalla parte di fuori del muro.
                    if ((na dot b.outwardOf(j)) > -0.99) continue
                    val s = (sb - sa) dot na
                    if (s <= Openings.SHARED_WALL_TOLERANCE_CM) continue
                    // Facce dei due muri una verso l'altra, sulla normale verso fuori di `a`.
                    val faceA = a.wallShift(i) + a.thicknessOf(i) / 2
                    val faceB = s - b.wallShift(j) - b.thicknessOf(j) / 2
                    val gap = faceB - faceA
                    // Facce che si toccano: due muri uno contro l'altro, come un muro solo più spesso. Va bene.
                    if (gap >= NEAR_GAP_CM || abs(gap) <= 0.5) continue
                    val t0 = (sb - sa) dot u
                    val t1 = (eb - sa) dot u
                    val lo = max(min(t0, t1), 0.0)
                    val hi = min(max(t0, t1), la)
                    if (hi - lo < NEAR_MIN_RUN_CM) continue
                    if (best != null && best.a <= gap) continue
                    val p0 = sa + u * lo
                    val p1 = sa + u * hi
                    // Si seleziona il muro della stanza più piccola: di solito è quello da spostare.
                    val moveB = Polygon.area(b.points) <= Polygon.area(a.points)
                    val sel = if (moveB) Selection.Wall(b.id, j) else Selection.Wall(a.id, i)
                    best = Issue(
                        Kind.NearWalls, floor, if (moveB) listOf(b.id, a.id) else listOf(a.id, b.id),
                        boundsOf(listOf(p0, p1, p0 + na * s, p1 + na * s)).grow(60.0), sel, a = gap, b = s,
                    )
                }
            }
            best?.let(::add)
        }

        /** Porte e finestre: doppie, più larghe del muro, oltre la cima del muro, su un muro tolto, finestre tra due stanze. */
        fun openings() {
            class Span(val room: Room, val o: Opening, val a: Vec2, val b: Vec2)
            val spans = mutableListOf<Span>()
            for (r in rooms) for (o in r.openings) {
                if (o.wallIndex !in 0 until r.wallCount || !o.width.isFinite() || !o.position.isFinite()) continue
                val (a, b) = Openings.span(r, o)
                if (!a.x.isFinite() || !a.y.isFinite() || !b.x.isFinite() || !b.y.isFinite()) continue
                val box = boundsOf(listOf(a, b)).grow(80.0)
                val sel = Selection.Opening(r.id, o.id)
                val drawn = a.distanceTo(b)
                when {
                    r.isRemoved(o.wallIndex) -> add(Issue(Kind.OpeningOnRemovedWall, floor, listOf(r.id), box, sel, opening = o.kind))
                    o.width - drawn > 1.0 && r.arcOf(o.wallIndex) == null ->
                        add(Issue(Kind.OpeningTooWide, floor, listOf(r.id), box, sel, a = o.width, b = r.wallLength(o.wallIndex), opening = o.kind))
                    !r.outdoor && o.kind != OpeningKind.Niche -> {
                        val top = o.bottom + o.height
                        val wall = Ceilings.sharedWallTop(plan, r, o.wallIndex, (a + b) / 2.0)
                        if (top > wall + 2.0) add(Issue(Kind.OpeningOverWall, floor, listOf(r.id), box, sel, a = top, b = wall, opening = o.kind))
                    }
                }
                if (o.kind != OpeningKind.Niche) spans += Span(r, o, a, b)
            }
            // Due aperture nello stesso punto dello stesso muro (anche dalle due stanze che lo hanno in comune).
            val reported = mutableSetOf<Long>()
            for ((k, x) in spans.withIndex()) for (m in k + 1 until spans.size) {
                val y = spans[m]
                if (x.o.id in reported || y.o.id in reported) continue
                if (x.room.id == y.room.id && x.o.wallIndex != y.o.wallIndex) continue
                val run = Dimensions.overlap(x.a, x.b, y.a, y.b)
                if (run < 3.0) continue
                val vx = x.o.bottom..(x.o.bottom + x.o.height)
                val vy = y.o.bottom..(y.o.bottom + y.o.height)
                if (min(vx.endInclusive, vy.endInclusive) - max(vx.start, vy.start) < 3.0) continue
                reported += y.o.id
                add(
                    Issue(
                        Kind.DoubleOpening, floor, listOfNotNull(x.room.id, y.room.id.takeIf { it != x.room.id }),
                        boundsOf(listOf(x.a, x.b, y.a, y.b)).grow(80.0), Selection.Opening(y.room.id, y.o.id), opening = y.o.kind,
                        opening2 = x.o.kind,
                    ),
                )
            }
            // Finestre che danno su un'altra stanza al chiuso, invece che fuori.
            for (s in spans) {
                if (!s.o.kind.isWindow) continue
                val other = rooms.firstOrNull { r ->
                    r.id != s.room.id && !r.outdoor && (0 until r.wallCount).any { j ->
                        Dimensions.overlap(s.a, s.b, r.wallStart(j), r.wallEnd(j)) >= 0.5 * s.a.distanceTo(s.b)
                    }
                } ?: continue
                add(Issue(Kind.InnerWindow, floor, listOf(s.room.id, other.id), boundsOf(listOf(s.a, s.b)).grow(80.0), Selection.Opening(s.room.id, s.o.id), opening = s.o.kind))
            }
        }

        /**
         * Mobili che entrano in un muro per più di [FURNITURE_IN_WALL_CM] (o lo attraversano), fuori dai vani di porte e finestre,
         * nella casa come sarà: di solito spostati a mano o ridimensionati dopo averli messi contro la parete. I tappeti no.
         */
        fun furnitureInWalls() {
            val p = Phases.view(plan, PhaseView.Project)
            val walls = WallBands.of(p)
            for (f in p.furniture) {
                if (full()) return
                val hit = walls.penetration(f) ?: continue
                if (!hit.across && hit.depth < FURNITURE_IN_WALL_CM) continue
                val box = boundsOf(Furnishings.outline(f)).grow(60.0)
                val room = p.rooms.firstOrNull { !it.outdoor && Polygon.contains(it.points, f.center) }
                add(Issue(Kind.FurnitureInWall, floor, listOfNotNull(room?.id), box, Selection.Furniture(f.id), a = hit.depth, b = if (hit.across) 1.0 else 0.0, furniture = f.id))
            }
        }

        /**
         * Impianti sulle pareti (termosifoni, luci, strisce LED, prese…) che arrivano più in alto del muro su cui stanno: di solito
         * in mansarda, dove la parete è bassa o tagliata dal tetto. La pianta li segna già in rosso; qui finiscono nell'elenco.
         */
        fun fixtures() {
            for (c in Collisions.of(plan)) {
                if (full()) return
                val id = c.fixtureId ?: continue
                if (c.objectTop <= c.wallTop + 2.0 || !c.point.x.isFinite() || !c.point.y.isFinite()) continue
                add(Issue(Kind.FixtureOverWall, floor, listOf(c.roomId), Bounds(c.point.x, c.point.y, c.point.x, c.point.y).grow(120.0), Selection.Fixture(c.roomId, id), a = c.objectTop, b = c.wallTop))
            }
        }

        /**
         * Ante a battente che aprendosi si urtano tra loro, urtano un mobile o un calorifero, o non si aprono del tutto per un muro o
         * un pilastro; nella casa come sarà (senza muri e aperture da demolire).
         */
        fun swings() {
            val p = Phases.view(plan, PhaseView.Project)
            val sweeps = Swings.sweeps(p)
            if (sweeps.isEmpty()) return
            fun box(s: List<Swings.Sweep>) = s.map { it.box }.reduce { x, y -> x.union(y) }.grow(60.0)
            // Due ante che si urtano: ogni coppia di aperture una volta sola (le ante della stessa apertura non si urtano).
            val pairs = mutableSetOf<List<Long>>()
            for ((k, x) in sweeps.withIndex()) for (m in k + 1 until sweeps.size) {
                if (full()) return
                val y = sweeps[m]
                if (x.room.id == y.room.id && x.opening.id == y.opening.id) continue
                val key = listOf(x.room.id, x.opening.id, y.room.id, y.opening.id)
                if (key in pairs || Swings.overlap(x, y) < Swings.MIN_OVERLAP_CM2) continue
                pairs += key
                add(
                    Issue(
                        Kind.SwingClash, floor, listOf(x.room.id, y.room.id).distinct(), box(listOf(x, y)), Selection.Opening(y.room.id, y.opening.id),
                        opening = x.opening.kind, opening2 = y.opening.kind,
                    ),
                )
            }
            val things = Swings.things(p)
            val walls = Swings.walls(p)
            for (group in sweeps.groupBy { it.room.id to it.opening.id }.values) {
                if (full()) return
                val s = group.first()
                val sel = Selection.Opening(s.room.id, s.opening.id)
                // Il mobile (o calorifero) urtato di più.
                val hit = things.map { t -> t to group.sumOf { Swings.overlap(it, t) } }.maxByOrNull { it.second }
                if (hit != null && hit.second >= Swings.MIN_OVERLAP_CM2) {
                    val f = hit.first.id.takeIf { hit.first.kind == Swings.ObstacleKind.Furniture }
                    add(Issue(Kind.SwingHitsThing, floor, listOf(s.room.id), box(group), sel, opening = s.opening.kind, furniture = f))
                }
                val wall = walls.any { w -> group.sumOf { Swings.overlap(it, w) } >= Swings.MIN_OVERLAP_CM2 && group.none { Swings.ownWall(it, w) } }
                if (wall) add(Issue(Kind.SwingHitsWall, floor, listOf(s.room.id), box(group), sel, opening = s.opening.kind))
            }
        }

        /** Stanze in cui non si entra; intanto guarda se almeno una porta del piano dà fuori. */
        fun doors(below: Pair<FloorPlan, Double>?, rise: Double) {
            val shared = Openings.sharedGaps(plan).map { it.roomId }.toSet()
            // Lati aperti: solo dove dall'altra parte non c'è il muro di un'altra stanza.
            val opened = Openings.removedStretches(plan).map { it.roomId }.toSet()
            val stairPoints = plan.stairs.flatMap { Stairs.layout(it, rise).path.take(1) } +
                (below?.let { (p, h) -> p.stairs.flatMap { s -> Stairs.layout(s, h).path.takeLast(1) } } ?: emptyList())
            for (r in rooms) {
                for (o in r.openings) {
                    if (o.wallIndex !in 0 until r.wallCount || r.isRemoved(o.wallIndex)) continue
                    if (!o.kind.createsSharedGap && !o.entrance) continue
                    val (a, b) = Openings.span(r, o)
                    val len = a.distanceTo(b)
                    // Dà fuori se dall'altra parte non c'è una stanza al chiuso con il muro lì.
                    val inside = rooms.any { other ->
                        other.id != r.id && !other.outdoor && (0 until other.wallCount).any { j ->
                            Dimensions.overlap(a, b, other.wallStart(j), other.wallEnd(j)) >= 0.5 * len
                        }
                    }
                    if (o.entrance || !inside) leadsOutside = true
                }
            }
            val indoor = rooms.filter { !it.outdoor }
            // Nessuna porta né passaggio in tutto il piano (pianta appena ricalcata o letta): un avviso solo, non uno per stanza.
            if (indoor.size >= 2 && opened.isEmpty() && rooms.none { r -> r.openings.any { it.wallIndex in 0 until r.wallCount && passable(it.kind) } }) {
                noDoors = true
                add(Issue(Kind.NoDoors, floor, emptyList(), boundsOf(indoor.flatMap { it.points }).grow(60.0)))
                return
            }
            for (r in indoor) {
                val open = r.id in opened ||
                    r.openings.any { it.wallIndex in 0 until r.wallCount && !r.isRemoved(it.wallIndex) && passable(it.kind) } ||
                    r.id in shared ||
                    stairPoints.any { Polygon.contains(r.points, it) } ||
                    rooms.any { other -> other.id != r.id && other.openings.any { o -> o.wallIndex in 0 until other.wallCount && passable(o.kind) && onWallOf(r, other, o) } }
                if (!open) add(Issue(Kind.NoDoor, floor, listOf(r.id), boxes.getValue(r.id).grow(60.0)))
            }
        }

        /** L'apertura `o` della stanza `other` sta su un muro di `r` (porte-finestre tra due stanze: non fanno varco). */
        private fun onWallOf(r: Room, other: Room, o: Opening): Boolean {
            val (a, b) = Openings.span(other, o)
            val len = a.distanceTo(b)
            return (0 until r.wallCount).any { j -> Dimensions.overlap(a, b, r.wallStart(j), r.wallEnd(j)) >= 0.5 * len }
        }

        /**
         * Bagni che si aprono direttamente (porta o passaggio) sulla cucina o sul soggiorno, anche quando la cucina è a vista nel
         * soggiorno, nella casa come sarà: molti regolamenti d'igiene dei comuni vogliono un antibagno o un disimpegno in mezzo. In
         * camera da letto no: di solito si può aprire il secondo bagno, e il bagno principale non si riconosce dal disegno.
         */
        fun bathDoors() {
            val p = Phases.view(plan, PhaseView.Project)
            fun door(k: OpeningKind) = k == OpeningKind.Door || k == OpeningKind.Door2 || k == OpeningKind.Passage
            val doors = p.rooms.flatMap { r ->
                r.openings.filter { it.wallIndex in 0 until r.wallCount && !r.isRemoved(it.wallIndex) && door(it.kind) }.map { r to it }
            }
            for (bath in p.rooms) {
                if (full()) return
                if (bath.type != RoomType.Bagno || bath.id in crossed || bath.points.size < 3) continue
                for ((r, o) in doors) {
                    val (a, b) = Openings.span(r, o)
                    val len = a.distanceTo(b)
                    // La porta sta su un muro della stanza `x` (per almeno metà della sua larghezza).
                    fun on(x: Room) = (0 until x.wallCount).any { j -> Dimensions.overlap(a, b, x.wallStart(j), x.wallEnd(j)) >= 0.5 * len }
                    val other = when {
                        r.id == bath.id -> p.rooms.firstOrNull { it.id != bath.id && !it.outdoor && on(it) }
                        !r.outdoor && on(bath) -> r
                        else -> null
                    } ?: continue
                    if (HousingRules.space(p, other).none { it.type == RoomType.Cucina || it.type == RoomType.Soggiorno }) continue
                    add(Issue(Kind.BathOnLiving, floor, listOf(bath.id, other.id), boundsOf(listOf(a, b)).grow(120.0), Selection.Opening(r.id, o.id), opening = o.kind))
                    break
                }
            }
        }

        /**
         * Impianti a metà, solo se nella casa si stanno mettendo: stanze da abitare e bagni senza calorifero (né termoarredo; il
         * soggiorno con la cucina a vista ne vuole uno in tutto l'ambiente), cucine e bagni senza punto acqua.
         */
        fun systems() {
            if (!heated && !plumbed) return
            fun has(r: Room, k: FixtureKind) = r.fixtures.any { it.kind == k }
            for (r in rooms) {
                if (full()) return
                if (r.outdoor || r.id in crossed || HousingRules.demolished(r) || r.points.size < 3) continue
                val box = boxes.getValue(r.id).grow(60.0)
                if (heated && (HousingRules.habitable(r.type) || r.type == RoomType.Bagno)) {
                    val space = HousingRules.space(plan, r)
                    val warm = space.any { s -> has(s, FixtureKind.Radiator) || HousingRules.towelWarmer(plan, s) }
                    if (!warm) add(Issue(Kind.NoHeating, floor, listOf(r.id), box))
                }
                if (plumbed && (r.type == RoomType.Cucina || r.type == RoomType.Bagno) && !has(r, FixtureKind.WaterPoint)) {
                    add(Issue(Kind.NoWater, floor, listOf(r.id), box))
                }
            }
        }

        /**
         * Scale che salgono dove non c'è un piano o arrivano fuori dalle sue stanze; ripide (alzate oltre [Stairs.STEEP_RISER] o passo
         * più corto di [Stairs.MIN_STRIDE]), strette (meno di [Stairs.MIN_WIDTH]) o con meno di [Stairs.MIN_HEADROOM] sopra la testa
         * dove il pavimento di sopra copre i primi gradini. Le scale a chiocciola si guardano solo per l'alzata.
         */
        fun stairs(rise: Double, above: FloorPlan?, top: Boolean) {
            for (s in plan.stairs) {
                if (full()) return
                val l = Stairs.layout(s, rise)
                val box = l.bounds.grow(80.0)
                val spiral = s.kind == StairKind.Spiral
                if (l.riser > Stairs.STEEP_RISER + 0.05 || (!spiral && 2 * l.riser + s.tread < Stairs.MIN_STRIDE - 0.05)) {
                    add(Issue(Kind.StairSteep, floor, emptyList(), box, Selection.Stair(s.id), a = l.riser, b = if (spiral) 0.0 else s.tread))
                }
                if (!spiral && s.width < Stairs.MIN_WIDTH - 0.5) add(Issue(Kind.StairNarrow, floor, emptyList(), box, Selection.Stair(s.id), a = s.width))
                if (above != null && !top) {
                    Stairs.headroom(s, rise, Floor.SLAB)?.let { h ->
                        if (h < Stairs.MIN_HEADROOM - 0.5) add(Issue(Kind.StairLowHead, floor, emptyList(), box, Selection.Stair(s.id), a = h))
                    }
                }
                if (top) {
                    add(Issue(Kind.StairNoFloor, floor, emptyList(), box, Selection.Stair(s.id)))
                    continue
                }
                if (above == null || l.path.size < 2) continue
                val end = l.path.last()
                val dir = (end - l.path[l.path.size - 2]).normalized()
                val step = end + dir * 20.0
                val arrives = above.rooms.any { r -> Polygon.contains(r.points, step) || Polygon.contains(r.points, end) }
                if (!arrives) add(Issue(Kind.StairOutside, floor, emptyList(), box, Selection.Stair(s.id)))
            }
        }

        /**
         * Minimi delle norme italiane per le case, sulla casa come sarà (le stanze tutte da demolire non contano): finestre apribili
         * di almeno 1/8 del pavimento nelle stanze da abitare (per ambiente: il soggiorno con la cucina a vista insieme), soffitti
         * alti almeno 2,70 m (2,40 in bagni e corridoi), camere da letto da 9 m² e soggiorno da 14 m². Le misure si confrontano
         * arrotondate come si leggono (m² al centesimo, cm interi), così non esce «9,00 m², meno di 9 m²».
         */
        fun rules() {
            fun hundredths(cm2: Double) = (cm2 / 100).roundToInt()
            // Ambienti da abitare (il soggiorno con la cucina a vista insieme), senza le stanze col contorno che si taglia.
            val spaces = HousingRules.livingSpaces(plan).map { s -> s.filter { it.id !in crossed } }
                .filter { s -> s.isNotEmpty() && HousingRules.floorArea(s) >= TINY_ROOM_CM2 }
            fun where(s: List<Room>) = boundsOf(s.flatMap { it.points }).grow(60.0)
            // Nessuna finestra in tutto il piano (pianta appena ricalcata o letta): un avviso solo, non uno per ambiente.
            val windowless = rooms.none { r -> r.openings.any { it.kind.glazed } }
            if (windowless && spaces.isNotEmpty()) add(Issue(Kind.NoFloorWindows, floor, emptyList(), boundsOf(spaces.flatten().flatMap { it.points }).grow(60.0)))
            if (!windowless) for (s in spaces) {
                if (!HousingRules.belowMinimum(plan, s)) continue
                val windows = HousingRules.windowArea(plan, s)
                add(Issue(if (windows < 1.0) Kind.NoWindows else Kind.FewWindows, floor, s.map { it.id }, where(s), a = windows / 10_000, b = HousingRules.floorArea(s) / 10_000))
            }
            for (r in rooms) {
                if (full()) return
                if (r.outdoor || r.id in crossed || HousingRules.demolished(r)) continue
                val area = r.usableArea()
                if (area < TINY_ROOM_CM2) continue
                val box = boxes.getValue(r.id).grow(60.0)
                if (HousingRules.flatCeiling(r)) {
                    val min = when {
                        HousingRules.habitable(r.type) -> HousingRules.MIN_HEIGHT_LIVING_CM
                        HousingRules.service(r.type) -> HousingRules.MIN_HEIGHT_SERVICE_CM
                        else -> null
                    }
                    if (min != null && r.ceilingHeight.roundToInt() < min) add(Issue(Kind.LowCeiling, floor, listOf(r.id), box, a = r.ceilingHeight, b = min))
                } else if (HousingRules.habitable(r.type)) {
                    // Mansarda: conta l'altezza media (le regole cambiano da regione a regione: si avvisa solo sotto quella più comune).
                    val average = Ceilings.averageHeight(r)
                    if (average.roundToInt() < HousingRules.MIN_ATTIC_AVERAGE_CM) add(Issue(Kind.LowAttic, floor, listOf(r.id), box, a = average))
                }
                if (r.type == RoomType.Camera || r.type == RoomType.Cameretta) {
                    // Per due persone (letto matrimoniale, a castello o due letti) ne servono 14.
                    val min = HousingRules.minBedroom(plan, r)
                    if (hundredths(area) < hundredths(min)) add(Issue(Kind.SmallBedroom, floor, listOf(r.id), box, a = area / 10_000, b = min / 10_000))
                }
                // Bagno senza finestra apribile verso fuori: serve l'aspirazione meccanica (non se il piano non ha ancora finestre).
                if (r.type == RoomType.Bagno && !windowless && HousingRules.windows(plan, r).isEmpty()) add(Issue(Kind.BathNoWindow, floor, listOf(r.id), box))
            }
            // Soggiorno: con la cucina a vista conta tutto l'ambiente.
            for (s in spaces) {
                if (s.first().type != RoomType.Soggiorno) continue
                val total = HousingRules.floorArea(s)
                if (hundredths(total) < hundredths(HousingRules.MIN_LIVING_CM2)) add(Issue(Kind.SmallLiving, floor, s.map { it.id }, where(s), a = total / 10_000))
            }
            // Impianto elettrico di base, solo se nella casa si stanno mettendo le prese: punti presa e punti luce di ogni stanza.
            if (wired) for (r in rooms) {
                if (full()) return
                if (r.id in crossed || HousingRules.demolished(r)) continue
                val min = ElectricRules.minimum(plan, r) ?: continue
                val has = ElectricRules.points(r)
                if (has.outlets >= min.outlets && has.lights >= min.lights) continue
                val box = boxes.getValue(r.id).grow(60.0)
                add(Issue(Kind.FewPoints, floor, listOf(r.id), box, a = has.outlets.toDouble(), b = has.lights.toDouble(), count = min.outlets * 10 + min.lights))
            }
        }
    }
}
