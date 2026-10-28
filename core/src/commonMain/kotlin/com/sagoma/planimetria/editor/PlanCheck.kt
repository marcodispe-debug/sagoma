package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.HousingRules
import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.Furnishings
import com.sagoma.planimetria.geometry.ElectricRules
import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.PhaseView
import com.sagoma.planimetria.geometry.Phases
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Snapping
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
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.FurnitureSymbol
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.RadiatorModel
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
     * muro lungo quasi zero no: si corregge il muro (l'apertura passa su quello dopo). Le ante che urtano, le stanze sovrapposte,
     * quelle senza porta, calorifero o punto acqua, la casa senza ingresso e le stanze con poche finestre qui sì, ma girarle,
     * spostarle o trovare dove mettere la porta, l'impianto o la finestra non sempre si può: [canFix] con la casa lo prova
     * ([triesFirst]).
     */
    fun canFix(i: Issue): Boolean = when (i.kind) {
        Kind.FurnitureInWall -> i.b == 0.0
        Kind.DoubleOpening -> i.opening != null && i.opening == i.opening2
        Kind.OpeningTooWide -> i.b >= MIN_FIXED_OPENING_CM
        Kind.FixtureOverWall, Kind.OpeningOverWall, Kind.ZeroWall, Kind.NearWalls, Kind.OpeningOnRemovedWall -> true
        Kind.Overlap -> i.rooms.size == 2
        Kind.NoDoor, Kind.TinyRoom, Kind.NoHeating, Kind.NoWater, Kind.FewPoints, Kind.BathNoWindow -> i.rooms.size == 1
        Kind.NoEntrance, Kind.NoDoors, Kind.NoFloorWindows -> true
        Kind.NoWindows, Kind.FewWindows -> i.rooms.isNotEmpty()
        Kind.SwingClash, Kind.SwingHitsThing, Kind.SwingHitsWall -> i.selection is Selection.Opening
        else -> false
    }

    private val SWINGS = setOf(Kind.SwingClash, Kind.SwingHitsThing, Kind.SwingHitsWall)

    /** Problemi che si riconoscono dalle stanze di [Issue.rooms] (non da cosa selezionare): corretti quando lì non c'è più. */
    private val BY_ROOMS = setOf(Kind.NearWalls, Kind.Overlap, Kind.NoDoor, Kind.TinyRoom, Kind.NoHeating, Kind.NoWater, Kind.NoWindows, Kind.FewWindows,
        Kind.FewPoints, Kind.BathNoWindow)

    /** Finestre sotto 1/8 del pavimento: «senza» e «poche» sono lo stesso problema (una finestra piccola non lo corregge). */
    private val FEW_WINDOWS = setOf(Kind.NoWindows, Kind.FewWindows)

    /** Problemi che [canFix] con la casa prova a correggere davvero prima di offrire «Correggi» ([triesFirst]). */
    private val TRIES_FIRST = SWINGS + FEW_WINDOWS + Kind.Overlap + Kind.NoDoor + Kind.NoDoors + Kind.NoEntrance + Kind.NoHeating + Kind.NoWater +
        Kind.NoFloorWindows + Kind.FewPoints + Kind.BathNoWindow

    /**
     * Chiave del problema `i` tra quelli tolti dalla pianta ([Building.hiddenChecks]): tipo, piano (il suo id, che non cambia
     * aggiungendo piani), stanze e cosa selezionare. Resta la stessa anche quando le misure del problema cambiano.
     */
    fun hideKey(b: Building, i: Issue): String {
        val floor = b.floors.getOrNull(i.floor)?.id ?: i.floor.toLong()
        val what = when (val s = i.selection) {
            null -> ""
            is Selection.Wall -> "w${s.roomId}.${s.index}"
            is Selection.Opening -> "o${s.roomId}.${s.openingId}"
            is Selection.Fixture -> "f${s.roomId}.${s.fixtureId}"
            is Selection.Furniture -> "m${s.furnitureId}"
            is Selection.Stair -> "s${s.stairId}"
            else -> s.toString()
        }
        return "${i.kind.name}/$floor/${i.rooms.sorted().joinToString(",")}/$what"
    }

    /** Il «!» del problema `i` è stato tolto dalla pianta. */
    fun hidden(b: Building, i: Issue): Boolean = b.hiddenChecks.isNotEmpty() && hideKey(b, i) in b.hiddenChecks

    /**
     * Dove va il «!» di un problema sulla pianta (cm). `room`: è il problema di una stanza (o di tutto il piano) e il segno sta
     * nel punto del nome, che non deve coprire.
     */
    data class Spot(val at: Vec2, val room: Boolean)

    /**
     * Il punto del «!» del problema `i` sulla pianta del suo piano (`plan`, com'è adesso: il segno segue la porta o il mobile
     * spostati): sull'apertura, a metà del muro, sull'impianto, sul mobile; stanze sovrapposte, muri incrociati e muri lunghi
     * zero dove succede; il problema di una stanza nel punto del suo nome. Null se quello di cui parla non c'è più.
     */
    fun spot(i: Issue, plan: FloorPlan): Spot? {
        val s = i.selection
        val at: Vec2? = when {
            i.kind == Kind.ZeroWall || i.kind == Kind.CrossedWalls || i.kind == Kind.Overlap -> i.area.center
            s is Selection.Opening -> plan.room(s.roomId)?.let { r ->
                r.openings.firstOrNull { it.id == s.openingId && it.wallIndex in 0 until r.wallCount }?.let { o ->
                    val (a, b) = Openings.span(r, o)
                    (a + b) / 2.0
                }
            }
            s is Selection.Wall -> plan.room(s.roomId)?.takeIf { s.index in 0 until it.wallCount }?.let { (it.wallStart(s.index) + it.wallEnd(s.index)) / 2.0 }
            s is Selection.Fixture -> plan.room(s.roomId)?.let { r -> r.fixtures.firstOrNull { it.id == s.fixtureId }?.let { Fixtures.center(r, it) } }
            s is Selection.Furniture -> plan.furniture.firstOrNull { it.id == s.furnitureId }?.center
            s != null -> i.area.center
            else -> null
        }
        fun Vec2.ok() = x.isFinite() && y.isFinite()
        if (s != null || at != null) return at?.takeIf { it.ok() }?.let { Spot(it, room = false) }
        val r = i.rooms.firstNotNullOfOrNull { plan.room(it) }?.takeIf { it.points.size >= 3 }
        val c = r?.let { runCatching { Polygon.labelPoint(it.points) }.getOrNull() } ?: i.area.center
        return c.takeIf { it.ok() }?.let { Spot(it, room = true) }
    }

    /**
     * Problemi che prima di offrire «Correggi» si prova a correggere davvero ([canFix] con la casa): le ante che urtano, le stanze
     * sovrapposte, quelle senza porta, calorifero o punto acqua e la casa senza porta d'ingresso.
     */
    fun triesFirst(i: Issue): Boolean = i.kind in TRIES_FIRST && canFix(i)

    /** Come [canFix], ma i problemi di [triesFirst] si provano a correggere: «Correggi» solo se serve a qualcosa. */
    fun canFix(b: Building, i: Issue): Boolean = canFix(i) && (!triesFirst(i) || fixed(b, i) != null)

    /**
     * «Correggi tutti» di un gruppo dell'elenco: corregge uno dopo l'altro ([fixed]) i problemi di `issues` che si correggono da
     * soli, ognuno cercato di nuovo nella casa già corretta (sparito con un altro, o cambiato, si salta). Quelli tolti dalla pianta
     * ([hidden]: già visti, voluti così) restano come sono. Torna la casa corretta e quanti problemi sono stati corretti (0: la
     * casa di prima). Come per [fixed], nessun errore di disegno nuovo.
     */
    fun fixedAll(b: Building, issues: List<Issue>): Pair<Building, Int> {
        var out = b
        var n = 0
        for (i in issues) {
            if (!canFix(i) || hidden(b, i)) continue
            val now = LiveCheck.stillThere(i, of(out)) ?: continue
            out = fixed(out, now) ?: continue
            n++
        }
        return out to n
    }

    /**
     * La casa con il problema `i` corretto, o null se non si riesce da soli (o la correzione non lo toglie, o non c'è più): il
     * mobile dentro un muro torna fino alla parete; l'impianto più alto del muro si abbassa; la porta o la finestra più alta del
     * muro si accorcia, quella più larga del muro si stringe a quanto se ne vede; quella messa due volte resta una sola, quella su
     * un muro tolto (lato aperto) si toglie; il muro lungo zero si unisce al muro dopo (come «Unisci al muro dopo»); il muro
     * della stanza quasi attaccata a un'altra va sulla linea del muro dell'altra e diventa un muro in comune (come trascinarlo
     * lì); delle due stanze sovrapposte una si sposta fuori dall'altra fino al muro in comune, o se no si accorcia
     * ([overlapFixes]); alla stanza senza porta se ne mette una verso la stanza accanto ([doorFixes]), alla casa senza ingresso
     * una porta blindata su un muro che dà fuori ([entranceFixes]); alla stanza senza calorifero se ne mette uno ([heatFixes]),
     * alla cucina o al bagno senza punto acqua uno dietro il lavello ([waterFixes]); al piano senza nessuna porta una porta
     * per stanza e quella d'ingresso ([allDoors]); all'ambiente con finestre sotto 1/8 del pavimento si allarga una finestra o
     * se ne mette una nuova su un muro che dà fuori ([windowFixes]), al piano senza nessuna finestra in ogni ambiente da abitare
     * ([allWindows]), al bagno senza finestra una su un muro che dà fuori ([bathWindows]); alla stanza con meno punti presa o punti luce di quelli della norma si mettono quelli che mancano
     * ([pointFixes]); la stanza piccolissima
     * (un pezzo rimasto) si toglie, se il piano ne ha altre; l'anta che urta si gira ([swingFixes]). Nel piano con stato di fatto e di progetto le porte e le finestre messe sono
     * nuove ([added]). Una correzione che crea un errore di disegno nuovo (o, per le ante, un'anta che urta altro) non si fa.
     */
    fun fixed(b: Building, i: Issue): Building? {
        if (!canFix(i)) return null
        val plan = b.floors.getOrNull(i.floor)?.plan ?: return null
        if (i.kind in SWINGS) return turned(b, plan, i)
        fun Room.with(o: Opening) = copy(openings = openings.map { if (it.id == o.id) o else it })
        val single: FloorPlan? = when (i.kind) {
            Kind.FurnitureInWall -> i.furniture?.let { pushedOut(plan, it) }
            Kind.NearWalls, Kind.Overlap, Kind.NoDoor, Kind.NoEntrance, Kind.NoHeating, Kind.NoWater, Kind.BathNoWindow -> null
            // Il piano senza nessuna porta (appena ricalcato): una porta per stanza e quella d'ingresso; se l'ingresso non trova
            // posto, solo le porte.
            Kind.NoDoors, Kind.NoFloorWindows -> null
            Kind.FewPoints -> pointFixes(plan, i)
            Kind.TinyRoom -> i.rooms.singleOrNull()?.takeIf { id -> plan.room(id) != null && plan.rooms.size > 1 }?.let { id ->
                plan.copy(rooms = plan.rooms.filter { it.id != id })
            }
            Kind.ZeroWall -> (i.selection as? Selection.Wall)?.let { s ->
                val r = plan.room(s.roomId)?.takeIf { s.index in 0 until it.wallCount } ?: return null
                r.removeCorner((s.index + 1) % r.wallCount)?.let(plan::replace)
            }
            // La seconda apertura nello stesso punto, o quella sul muro tolto (che non c'è): si toglie.
            Kind.DoubleOpening, Kind.OpeningOnRemovedWall -> (i.selection as? Selection.Opening)?.let { s ->
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
        }
        // Già corretto (l'elenco era vecchio): niente da fare. Una correzione che crea un errore di disegno nuovo non va.
        val before by lazy { of(b) }
        fun same(x: Issue) = (x.kind == i.kind || (i.kind in FEW_WINDOWS && x.kind in FEW_WINDOWS)) && x.floor == i.floor &&
            (if (i.kind in BY_ROOMS) x.rooms.toSet() == i.rooms.toSet() else x.selection == i.selection)
        fun accepted(out: FloorPlan): Pair<Building, List<Issue>>? {
            if (out == plan || before.none(::same)) return null
            val result = b.copy(floors = b.floors.mapIndexed { k, f -> if (k == i.floor) f.copy(plan = out).synced() else f })
            val after = of(result)
            return if (after.none(::same) && LiveCheck.newErrors(before, after).isEmpty()) result to after else null
        }
        if (i.kind == Kind.NoEntrance) {
            // Prima i posti dove l'anta non urta niente di nuovo (si prova solo quello, è veloce), a pari la stanza migliore e il
            // tratto di muro più lungo; il primo che va.
            fun swings(p: FloorPlan) = mutableListOf<Issue>().also { FloorCheck(sane(p), i.floor, it).swings() }
            val old = swings(plan).mapTo(HashSet()) { LiveCheck.key(it) }
            return entranceFixes(plan).sortedBy { p -> swings(p).count { LiveCheck.key(it) !in old } }.firstNotNullOfOrNull { accepted(it)?.first }
        }
        if (i.kind == Kind.NoDoor || i.kind == Kind.NoHeating || i.kind == Kind.NoWater || i.kind in FEW_WINDOWS || i.kind == Kind.BathNoWindow) {
            // Tra le porte (o i caloriferi, i punti acqua, le finestre) che vanno, quella con meno problemi nuovi (anta che urta,
            // bagno che si apre sul soggiorno); a pari, la prima (verso corridoio o soggiorno; sotto la finestra; dietro il
            // lavello; la finestra che c'è allargata).
            val keys by lazy { before.mapTo(HashSet()) { LiveCheck.key(it) } }
            val fixes = when (i.kind) {
                Kind.NoDoor -> doorFixes(plan, i)
                Kind.NoHeating -> heatFixes(plan, i)
                Kind.NoWater -> waterFixes(plan, i)
                Kind.BathNoWindow -> i.rooms.singleOrNull()?.let(plan::room)?.let { bathWindows(plan, it) }.orEmpty()
                else -> windowFixes(plan, i.rooms.mapNotNull(plan::room))
            }
            return fixes.mapNotNull { out -> accepted(out)?.let { (result, after) -> result to after.count { LiveCheck.key(it) !in keys } } }
                .minByOrNull { it.second }?.first
        }
        if (i.kind == Kind.Overlap) {
            // Tra gli spostamenti che vanno, quello con meno problemi nuovi e poi con la stanza spostata che si attacca di più alle
            // altre (di solito è quella trascinata per sbaglio che torna al suo posto; una spinta via dal suo posto se ne stacca);
            // accorciare una stanza solo se spostarla non va.
            val keys by lazy { before.mapTo(HashSet()) { LiveCheck.key(it) } }
            return overlapFixes(plan, i).mapNotNull { fix -> accepted(fix.plan)?.let { (result, after) -> Triple(fix, result, after.count { LiveCheck.key(it) !in keys }) } }
                .minWithOrNull(
                    compareBy<Triple<OverlapFix, Building, Int>> { it.first.shrink }.thenBy { it.third }
                        .thenByDescending { contact(it.first.plan, it.first.room) - contact(plan, it.first.room) },
                )
                ?.second
        }
        val candidates = when (i.kind) {
            Kind.NearWalls -> nearFixes(plan, i)
            Kind.NoDoors -> allDoors(plan, i.floor)?.let { doors -> entranceFixes(doors).take(2) + doors }.orEmpty()
            Kind.NoFloorWindows -> listOfNotNull(allWindows(plan))
            else -> listOfNotNull(single)
        }
        return candidates.firstNotNullOfOrNull { accepted(it)?.first }
    }

    /**
     * L'anta che urta girata ([swingFixes]), se così non urta più e non ne urta un'altra (altre ante, mobili, muri). Si guardano
     * solo le ante del piano: girarle non cambia muri, stanze e aperture (niente errori di disegno nuovi).
     */
    private fun turned(b: Building, plan: FloorPlan, i: Issue): Building? {
        fun swings(p: FloorPlan) = mutableListOf<Issue>().also { FloorCheck(sane(p), i.floor, it).swings() }
        val before = swings(plan).mapTo(HashSet()) { LiveCheck.key(it) }
        // Già corretto (l'elenco era vecchio): niente da girare.
        if (LiveCheck.key(i) !in before) return null
        val out = swingFixes(plan, i).firstOrNull { p ->
            val after = swings(p)
            after.none { it.kind == i.kind && it.selection == i.selection } && after.all { LiveCheck.key(it) in before }
        } ?: return null
        return b.copy(floors = b.floors.mapIndexed { k, f -> if (k == i.floor) f.copy(plan = out).synced() else f })
    }

    /**
     * Ante che urtano: l'anta girata, prima con il cardine dall'altra parte, poi (solo le porte tra due stanze, non quelle che
     * danno fuori) aperta verso l'altra stanza, poi tutte e due le cose; per due ante che si urtano, dopo quella scelta anche
     * l'altra.
     */
    private fun swingFixes(plan: FloorPlan, i: Issue): List<FloorPlan> {
        val s = i.selection as? Selection.Opening ?: return emptyList()
        val ids = mutableListOf(s.roomId to s.openingId)
        if (i.kind == Kind.SwingClash) {
            val sweeps = Swings.sweeps(Phases.view(plan, PhaseView.Project))
            val mine = sweeps.filter { it.room.id == s.roomId && it.opening.id == s.openingId }
            sweeps.filter { x -> (x.room.id != s.roomId || x.opening.id != s.openingId) && mine.any { Swings.overlap(it, x) >= Swings.MIN_OVERLAP_CM2 } }
                .mapTo(ids) { it.room.id to it.opening.id }
        }
        return ids.distinct().flatMap { (roomId, openingId) ->
            val r = plan.room(roomId) ?: return@flatMap emptyList()
            val o = r.openings.firstOrNull { it.id == openingId } ?: return@flatMap emptyList()
            // Una porta tra due stanze (anche sui muri singoli) si può aprire dall'altra parte; quella di casa resta com'è.
            val alone = plan.copy(rooms = plan.rooms.map { if (it.id == r.id) it.copy(openings = listOf(o)) else it.copy(openings = emptyList()) })
            val between = (o.kind == OpeningKind.Door || o.kind == OpeningKind.Door2) && (r.id < 0 || Openings.sharedGaps(alone).isNotEmpty())
            val hinge = o.kind.leaves == 1
            listOfNotNull(
                o.copy(hingeLeft = !o.hingeLeft).takeIf { hinge },
                o.copy(opensInward = !o.opensInward).takeIf { between },
                o.copy(hingeLeft = !o.hingeLeft, opensInward = !o.opensInward).takeIf { hinge && between },
            ).map { turned -> plan.replace(r.copy(openings = r.openings.map { if (it.id == o.id) turned else it })) }
        }
    }

    /**
     * Stanze quasi attaccate: il muro scelto (della stanza più piccola) sulla linea di quello dell'altra stanza; poi, se così
     * la stanza va sopra un'altra, il contrario (il muro dell'altra sulla linea di questo). `b`: distanza tra le due linee.
     */
    private fun nearFixes(plan: FloorPlan, i: Issue): List<FloorPlan> {
        val s = i.selection as? Selection.Wall ?: return emptyList()
        val r = plan.room(s.roomId)?.takeIf { s.index in 0 until it.wallCount && it.arcOf(s.index) == null } ?: return emptyList()
        val n = r.outwardOf(s.index)
        val mine = plan.replace(r.copy(points = Snapping.moveWall(r.points, s.index, n * i.b)))
        val a = r.wallStart(s.index)
        val u = (r.wallEnd(s.index) - a).normalized()
        val len = r.wallLength(s.index)
        val theirs = plan.room(i.rooms.firstOrNull { it != r.id })?.let { o ->
            // Il muro di fronte: parallelo, rivolto verso questo, con la linea a quella distanza e un tratto affacciato.
            (0 until o.wallCount).firstOrNull { j ->
                val t0 = (o.wallStart(j) - a) dot u
                val t1 = (o.wallEnd(j) - a) dot u
                o.arcOf(j) == null && !o.isRemoved(j) && (o.outwardOf(j) dot n) < -0.99 &&
                    abs(((o.wallStart(j) - a) dot n) - i.b) < 0.5 && min(max(t0, t1), len) - max(min(t0, t1), 0.0) >= NEAR_MIN_RUN_CM
            }?.let { j -> plan.replace(o.copy(points = Snapping.moveWall(o.points, j, o.outwardOf(j) * i.b))) }
        }
        return listOfNotNull(mine, theirs)
    }

    /** Stanze sovrapposte per tanto (della più piccola): una sta dentro l'altra, «Correggi» non c'è. */
    private const val INSIDE_SHARE = 0.9

    /** Una correzione delle stanze sovrapposte: la pianta, la stanza spostata e se invece è stata accorciata. */
    private class OverlapFix(val plan: FloorPlan, val room: Long, val shrink: Boolean)

    /**
     * Stanze sovrapposte: una delle due spostata fuori dall'altra fino ad avere il muro in comune, come trascinarla (con le luci al
     * soffitto; i mobili restano), dalle due parti dove si sposta meno; oppure solo il suo muro sulla linea di quello dell'altra
     * (si accorcia). Non dalla parte dove entra nell'altra per tutta la sua larghezza, né se una sta quasi tutta dentro l'altra
     * (lì non si sa dove va).
     */
    private fun overlapFixes(plan: FloorPlan, i: Issue): List<OverlapFix> {
        val a = plan.room(i.rooms.getOrNull(0) ?: return emptyList()) ?: return emptyList()
        val b = plan.room(i.rooms.getOrNull(1) ?: return emptyList())?.takeIf { it.id != a.id } ?: return emptyList()
        if (i.a * 10_000 >= INSIDE_SHARE * min(a.interiorArea(), b.interiorArea())) return emptyList()
        return listOf(a to b, b to a).flatMap { (r, o) ->
            backOut(r, o).flatMap { (k, s) ->
                val d = r.outwardOf(k) * s
                listOf(
                    OverlapFix(plan.replace(Fixtures.movedRoom(r, d)), r.id, shrink = false),
                    OverlapFix(plan.replace(r.copy(points = Snapping.moveWall(r.points, k, d))), r.id, shrink = true),
                )
            }
        }
    }

    /**
     * I muri di `r` (al massimo due, prima quelli che vanno meno lontano) e di quanto vanno indietro, lungo la loro normale verso
     * fuori, per arrivare sulla linea del muro di fronte di `o` che c'è dentro `r`.
     */
    private fun backOut(r: Room, o: Room): List<Pair<Int, Double>> {
        val moves = mutableListOf<Pair<Int, Double>>()
        for (k in 0 until r.wallCount) {
            if (r.isRemoved(k) || r.arcOf(k) != null) continue
            val a = r.wallStart(k)
            val len = r.wallLength(k)
            if (len < NEAR_MIN_RUN_CM) continue
            val u = (r.wallEnd(k) - a) / len
            val n = r.outwardOf(k)
            // Quanto è profonda la stanza dietro quel muro.
            val depth = r.points.maxOf { (a - it) dot n }
            for (j in 0 until o.wallCount) {
                if (o.isRemoved(j) || o.arcOf(j) != null || (n dot o.outwardOf(j)) > -0.99) continue
                val s = (o.wallStart(j) - a) dot n
                if (s > -0.5 || -s >= depth) continue
                if (run(o.wallStart(j) - a, o.wallEnd(j) - a, u, len) < NEAR_MIN_RUN_CM) continue
                moves += k to s
            }
        }
        return moves.sortedBy { -it.second }.distinctBy { it.first }.take(2)
    }

    /** Quanto il tratto da `p0` a `p1` (rispetto all'inizio di un muro lungo `len` nella direzione `u`) gli sta di fronte. */
    private fun run(p0: Vec2, p1: Vec2, u: Vec2, len: Double): Double {
        val t0 = p0 dot u
        val t1 = p1 dot u
        return min(max(t0, t1), len) - max(min(t0, t1), 0.0)
    }

    /** Quanto dei muri della stanza `id` (cm) è in comune con le altre stanze di `plan`: sulla stessa linea, uno di fronte all'altro. */
    private fun contact(plan: FloorPlan, id: Long): Double {
        val r = plan.room(id) ?: return 0.0
        var total = 0.0
        for (k in 0 until r.wallCount) {
            if (r.arcOf(k) != null) continue
            val a = r.wallStart(k)
            val len = r.wallLength(k)
            if (len < 1.0) continue
            val u = (r.wallEnd(k) - a) / len
            val n = r.outwardOf(k)
            for (o in plan.rooms) {
                if (o.id == r.id) continue
                for (j in 0 until o.wallCount) {
                    if (o.arcOf(j) != null || (n dot o.outwardOf(j)) > -0.99 || abs((o.wallStart(j) - a) dot n) > Openings.SHARED_WALL_TOLERANCE_CM) continue
                    total += max(0.0, run(o.wallStart(j) - a, o.wallEnd(j) - a, u, len))
                }
            }
        }
        return total
    }

    /** Tratto libero di muro in comune che serve per mettere una porta da 80 (cm): la porta e 10 cm per parte. */
    private const val DOOR_RUN_CM = 100.0

    /**
     * Stanza senza porta: una porta (80 cm) sul muro in comune con un'altra stanza al chiuso, dove il muro è libero da altre porte
     * e finestre (nel mezzo del tratto, se no a un capo), prima verso corridoio e soggiorno, poi le altre stanze (al massimo
     * tre); per ognuna l'anta come viene e girata (cardine dall'altra parte, verso l'altra stanza).
     */
    private fun doorFixes(plan: FloorPlan, i: Issue): List<FloorPlan> {
        val r = plan.room(i.rooms.singleOrNull() ?: return emptyList())?.takeIf { !it.outdoor } ?: return emptyList()
        val id = plan.nextOpeningId
        return doorSpots(plan, r).sortedBy { (_, _, o) -> doorTowards(o) }.distinctBy { it.third.id }.take(3).flatMap { (k, c, _) ->
                val d = added(plan, Opening.default(id, OpeningKind.Door, k, c))
                listOf(d, d.copy(hingeLeft = false), d.copy(opensInward = false), d.copy(hingeLeft = false, opensInward = false))
                    .map { plan.replace(r.copy(openings = r.openings + it)) }
            }
    }

    /**
     * Piano senza nessuna porta (appena ricalcato o letto): una porta (80 cm) per ogni stanza al chiuso, come per la stanza senza
     * porta ([doorSpots]), così che da una stanza si arrivi a tutte. Si parte dall'ingresso (o dal corridoio, dal soggiorno…: come
     * per la porta d'ingresso) e si collega ogni volta la stanza che va meglio: prima quelle che danno su corridoi e soggiorno.
     * Di ogni porta l'anta come urta meno (con il cardine dall'altra parte, aperta verso l'altra stanza). Null se nessuna stanza
     * si collega.
     */
    private fun allDoors(plan: FloorPlan, floor: Int): FloorPlan? {
        fun clashes(p: FloorPlan) = mutableListOf<Issue>().also { FloorCheck(sane(p), floor, it).swings() }.size
        val indoor = plan.rooms.filter { !it.outdoor }
        val root = indoor.minByOrNull { entranceFrom(it) ?: 6 } ?: return null
        val linked = mutableSetOf(root.id)
        var out = plan
        while (true) {
            // Per ogni stanza ancora staccata il posto migliore verso una già collegata; tra tutte, la migliore.
            val next = out.rooms.filter { !it.outdoor && it.id !in linked }.mapNotNull { r ->
                val spots = doorSpots(out, r).filter { it.third.id in linked }
                spots.minOfOrNull { doorTowards(it.third) }?.let { best -> Triple(r, best, spots.filter { doorTowards(it.third) == best }) }
            }.minByOrNull { (r, best, _) -> best * 10 + (entranceFrom(r) ?: 6) } ?: break
            val (r, _, spots) = next
            // Tra i posti verso le stanze migliori (nel mezzo del muro in comune o ai capi) e i versi dell'anta, quello che urta meno.
            val now = out
            out = spots.flatMap { (k, c, _) ->
                val d = added(plan, Opening.default(now.nextOpeningId, OpeningKind.Door, k, c))
                listOf(d, d.copy(hingeLeft = false), d.copy(opensInward = false), d.copy(hingeLeft = false, opensInward = false))
            }.map { now.replace(r.copy(openings = r.openings + it)) }.minBy(::clashes)
            linked += r.id
        }
        return out.takeIf { linked.size > 1 }
    }

    /**
     * Dove può andare una porta da 80 della stanza `r` verso un'altra stanza al chiuso: muro di `r`, centro della porta lungo il
     * muro e stanza dall'altra parte. Sul muro in comune, dove è libero da altre porte e finestre: nel mezzo del tratto e ai due
     * capi (prima il mezzo).
     */
    private fun doorSpots(plan: FloorPlan, r: Room): List<Triple<Int, Double, Room>> {
        // Dove mettere la porta: muro di `r`, centro della porta lungo il muro, stanza dall'altra parte.
        val spots = mutableListOf<Triple<Int, Double, Room>>()
        for (k in 0 until r.wallCount) {
            if (r.isRemoved(k) || r.arcOf(k) != null) continue
            val a = r.wallStart(k)
            val len = r.wallLength(k)
            if (len < DOOR_RUN_CM) continue
            val u = (r.wallEnd(k) - a) / len
            val n = r.outwardOf(k)
            fun onLine(p: Vec2) = abs((p - a) dot n) <= Openings.SHARED_WALL_TOLERANCE_CM
            // Porte e finestre già su questa linea (di questa stanza, delle altre, dei muri singoli): lì la porta non va.
            val taken = plan.openingHosts.flatMap { h ->
                h.openings.filter { it.wallIndex in 0 until h.wallCount && h.arcOf(it.wallIndex) == null }.mapNotNull { o ->
                    val (p, q) = Openings.span(h, o)
                    if (onLine(p) && onLine(q)) min((p - a) dot u, (q - a) dot u) to max((p - a) dot u, (q - a) dot u) else null
                }
            }
            for (o in plan.rooms) {
                if (o.id == r.id || o.outdoor) continue
                for (j in 0 until o.wallCount) {
                    if (o.isRemoved(j) || o.arcOf(j) != null || (n dot o.outwardOf(j)) > -0.99 || !onLine(o.wallStart(j))) continue
                    val t0 = (o.wallStart(j) - a) dot u
                    val t1 = (o.wallEnd(j) - a) dot u
                    val lo = max(min(t0, t1), 0.0)
                    val hi = min(max(t0, t1), len)
                    if (hi - lo < DOOR_RUN_CM) continue
                    val half = (DOOR_RUN_CM / 2)
                    listOf((lo + hi) / 2, lo + half, hi - half).distinct().filter { c ->
                        taken.none { (x0, x1) -> x1 > c - half && x0 < c + half }
                    }.forEach { spots += Triple(k, it, o) }
                }
            }
        }
        return spots
    }

    /** Tratto libero di muro verso fuori che serve per la porta d'ingresso da 90 (cm): la porta e 15 cm per parte. */
    private const val ENTRANCE_RUN_CM = 120.0

    /**
     * Per sapere se un muro dà fuori: si guarda tanto (cm) oltre la sua faccia esterna, ogni [PROBE_STEP_CM] lungo il muro. Lì
     * c'è un'altra stanza anche quando in mezzo c'è il suo muro (fino a 80 cm), o il muro spesso disegnato tra le due.
     */
    private const val NEIGHBOUR_PROBE_CM = 40.0
    private const val PROBE_STEP_CM = 10.0

    /**
     * Casa senza porta d'ingresso: dove mettere una porta blindata (come «Porta blindata» del menu: 90 cm), nell'ordine in cui
     * provarle. Su un muro che dà fuori (non toccato da altre stanze, nemmeno balconi e terrazzi), in un tratto libero da porte e
     * finestre: nel mezzo e, se il tratto è lungo, anche ai due capi (lì di solito non ci sono mobili). Prima nell'ingresso, poi
     * nei corridoi, nel soggiorno, in cucina, nelle altre stanze, per ultime le camere (al massimo quattro stanze, due tratti per
     * stanza, prima il più lungo); mai in bagno o nei ripostigli. Per ogni posto l'anta come viene e girata.
     */
    private fun entranceFixes(plan: FloorPlan): List<FloorPlan> {
        // Tratto libero: muro, inizio e fine lungo il muro.
        data class Run(val wall: Int, val lo: Double, val hi: Double)
        val id = plan.nextOpeningId
        val half = ENTRANCE_RUN_CM / 2
        return plan.rooms.filter { !it.outdoor && entranceFrom(it) != null }.sortedBy { entranceFrom(it) }.take(4).flatMap { r ->
            val runs = mutableListOf<Run>()
            for (k in 0 until r.wallCount) {
                if (r.isRemoved(k) || r.arcOf(k) != null) continue
                val a = r.wallStart(k)
                val len = r.wallLength(k)
                if (len < ENTRANCE_RUN_CM) continue
                val u = (r.wallEnd(k) - a) / len
                val n = r.outwardOf(k)
                fun onLine(p: Vec2) = abs((p - a) dot n) <= Openings.SHARED_WALL_TOLERANCE_CM
                // Già occupato: porte e finestre su questa linea; dove poco fuori dal muro c'è un'altra stanza.
                val taken = plan.openingHosts.flatMap { h ->
                    h.openings.filter { it.wallIndex in 0 until h.wallCount && h.arcOf(it.wallIndex) == null }.mapNotNull { o ->
                        val (p, q) = Openings.span(h, o)
                        if (onLine(p) && onLine(q)) min((p - a) dot u, (q - a) dot u) to max((p - a) dot u, (q - a) dot u) else null
                    }
                } + neighbours(plan, r, k, outdoor = true)
                // I tratti liberi tra quelli occupati.
                var from = 0.0
                for ((x0, x1) in taken.sortedBy { it.first } + (len to len)) {
                    if (min(x0, len) - from >= ENTRANCE_RUN_CM) runs += Run(k, from, min(x0, len))
                    from = max(from, x1)
                }
            }
            runs.sortedByDescending { it.hi - it.lo }.take(2).flatMap { run ->
                val at = listOf((run.lo + run.hi) / 2) + if (run.hi - run.lo >= 2 * ENTRANCE_RUN_CM) listOf(run.lo + half, run.hi - half) else emptyList()
                at.flatMap { c ->
                    val d = added(plan, OpeningPreset.Armored.apply(Opening.default(id, OpeningKind.Door, run.wall, c)))
                    listOf(d, d.copy(hingeLeft = false)).map { plan.replace(r.copy(openings = r.openings + it)) }
                }
            }
        }
    }

    /**
     * Tratti del muro `k` della stanza `r` (inizio e fine lungo il muro) dove poco fuori c'è un'altra stanza al chiuso (anche con un
     * muro spesso in mezzo, o il suo muro contro questo); con `outdoor` anche balconi e terrazze.
     */
    private fun neighbours(plan: FloorPlan, r: Room, k: Int, outdoor: Boolean): List<Pair<Double, Double>> {
        val a = r.wallStart(k)
        val len = r.wallLength(k)
        val u = (r.wallEnd(k) - a) / len
        val out = r.outwardOf(k) * (r.thicknessOf(k) / 2 + NEIGHBOUR_PROBE_CM)
        val taken = mutableListOf<Pair<Double, Double>>()
        var t = 0.0
        while (t <= len) {
            val p = a + u * t + out
            if (plan.rooms.any { it.id != r.id && (outdoor || !it.outdoor) && Polygon.contains(it.points, p) }) taken += (t - PROBE_STEP_CM) to (t + PROBE_STEP_CM)
            t += PROBE_STEP_CM
        }
        return taken
    }

    /** Fascia davanti a un muro (cm, oltre la faccia interna) dove un mobile o un impianto è addossato al muro. */
    private const val WALL_BAND_CM = 25.0

    /** Spazio (cm) che tiene un impianto piccolo (presa, punto acqua) sul muro, e il calorifero dai capi del tratto libero. */
    private const val FIXTURE_GAP_CM = 20.0

    /** Posti da provare per il calorifero o il punto acqua che manca. */
    private const val MAX_SYSTEM_SPOTS = 4

    /**
     * Tratti del muro `k` della stanza `r` già occupati (inizio e fine lungo il muro): porte e finestre su quella linea (solo con
     * `openings`), impianti a muro e mobili addossati (con `above`, solo quelli che arrivano più su: sotto una finestra il
     * calorifero o il divano vanno bene; con `below`, solo quelli che partono più giù: sotto la TV appesa la presa va bene).
     */
    private fun wallBlocks(
        plan: FloorPlan,
        r: Room,
        k: Int,
        openings: Boolean,
        above: Double = -1.0,
        below: Double = Double.MAX_VALUE,
    ): List<Pair<Double, Double>> {
        val a = r.wallStart(k)
        val len = r.wallLength(k)
        val u = (r.wallEnd(k) - a) / len
        val n = r.outwardOf(k)
        fun along(p: Vec2) = (p - a) dot u
        val out = mutableListOf<Pair<Double, Double>>()
        if (openings) {
            fun onLine(p: Vec2) = abs((p - a) dot n) <= Openings.SHARED_WALL_TOLERANCE_CM
            for (h in plan.openingHosts) for (o in h.openings) {
                if (o.wallIndex !in 0 until h.wallCount || h.arcOf(o.wallIndex) != null) continue
                val (p, q) = Openings.span(h, o)
                if (onLine(p) && onLine(q)) out += min(along(p), along(q)) to max(along(p), along(q))
            }
        }
        for (f in r.fixtures) if (f.kind.mount == Mount.Wall && f.wallIndex == k && f.elevation + f.height > above && f.elevation < below) {
            val half = max(f.length, FIXTURE_GAP_CM) / 2
            out += (f.position - half) to (f.position + half)
        }
        for (f in plan.furniture) {
            if (f.elevation + f.height <= above || f.elevation >= below) continue
            out += against(r, k, f) ?: continue
        }
        return out
    }

    /**
     * Il tratto del muro `k` della stanza `r` (inizio e fine lungo il muro) davanti a cui è addossato il mobile `f`, o null. Con
     * `facing`, solo se ha il retro verso quel muro (non il fianco, come una base della cucina accanto all'angolo).
     */
    private fun against(r: Room, k: Int, f: Furniture, facing: Boolean = false): Pair<Double, Double>? {
        val a = r.wallStart(k)
        val len = r.wallLength(k)
        val u = (r.wallEnd(k) - a) / len
        val n = r.outwardOf(k)
        if (facing && (Furnishings.axes(f).second dot n) > -0.7) return null
        val band = r.thicknessOf(k) / 2 + WALL_BAND_CM
        val pts = Furnishings.outline(f)
        val inward = pts.map { -((it - a) dot n) }
        if (inward.max() < -1.0 || inward.min() > band) return null
        val t = pts.map { (it - a) dot u }
        if (t.max() < 0.0 || t.min() > len) return null
        return t.min() to t.max()
    }

    /** Spazio (cm) che una finestra messa o allargata da «Correggi» lascia dagli spigoli, dalle altre aperture e dai mobili alti. */
    private const val WINDOW_GAP_CM = 30.0

    /** Larghezza (cm) della finestra più stretta e di quella più larga che «Correggi» mette o allarga. */
    private const val MIN_WINDOW_CM = 80.0
    private const val MAX_WINDOW_CM = 180.0

    /** Finestre nuove al massimo per un ambiente. */
    private const val MAX_NEW_WINDOWS = 4

    /**
     * Porta o finestra che mette «Correggi» nel piano `plan`: con stato di fatto e di progetto ([Phases.hasChanges]) è nuova, da
     * aprire (conta nelle opere del computo e nello stato di fatto non c'è); se no come quelle del menu.
     */
    private fun added(plan: FloorPlan, o: Opening): Opening = if (Phases.hasChanges(plan)) o.copy(phase = Phase.New) else o

    /** Finestra nuova: come quella a due ante del menu (alta 120, davanzale a 90), larga `width` (fino a 90 a un'anta). */
    private fun newWindow(id: Long, wall: Int, at: Double, width: Double): Opening =
        Opening.default(id, if (width <= 90.0) OpeningKind.Window1 else OpeningKind.Window2, wall, at).copy(width = width)

    private fun ceil10(cm: Double) = kotlin.math.ceil(cm / 10 - 1e-9) * 10
    private fun floor10(cm: Double) = kotlin.math.floor(cm / 10 + 1e-9) * 10

    /** Quanto (cm²) mancano le finestre dell'ambiente a 1/8 del pavimento, con un centesimo di m² in più per l'arrotondamento. */
    private fun missingLight(plan: FloorPlan, space: List<Room>): Double =
        HousingRules.floorArea(space) * HousingRules.MIN_RATIO - HousingRules.windowArea(plan, space) + 100.0

    /**
     * Finestre nuove nell'ambiente `space` per `need` cm² (vedi [windowFixes]), partendo dal tratto libero `first` (indice tra
     * quelli più lunghi); null se non bastano i muri che danno fuori.
     */
    private fun addedWindows(plan: FloorPlan, space: List<Room>, need: Double, first: Int = 0): FloorPlan? {
        val probe = newWindow(0, 0, 0.0, MAX_WINDOW_CM)
        // Tratti liberi di muri dritti che danno fuori (anche su un balcone), alti abbastanza: stanza, muro, inizio e fine.
        val runs = space.flatMap { r ->
            (0 until r.wallCount).filter { k ->
                !r.isRemoved(k) && r.arcOf(k) == null && r.phaseOf(k) != Phase.Demolish && r.wallHeight(k) >= probe.bottom + probe.height + 10.0 &&
                    r.wallLength(k) >= MIN_WINDOW_CM + 2 * WINDOW_GAP_CM
            }.flatMap { k ->
                val blocks = wallBlocks(plan, r, k, openings = true, above = probe.bottom) + neighbours(plan, r, k, outdoor = false)
                freeRuns(blocks, r.wallLength(k), MIN_WINDOW_CM + 2 * WINDOW_GAP_CM).map { (lo, hi) -> Triple(r.id, k, lo to hi) }
            }
        }.sortedByDescending { it.third.second - it.third.first }
        if (first >= runs.size) return null
        var out = plan
        var left = need
        var id = plan.nextOpeningId
        for ((roomId, k, run) in listOf(runs[first]) + runs.filterIndexed { n, _ -> n != first }) {
            if (left <= 0 || id - plan.nextOpeningId >= MAX_NEW_WINDOWS) break
            val (lo, hi) = run
            // Se ne servono più d'una, larghe uguali (due da 100 invece di una da 180 e una da 20).
            val count = kotlin.math.ceil(left / (MAX_WINDOW_CM * probe.height))
            val width = ceil10(left / count / probe.height).coerceIn(MIN_WINDOW_CM, min(MAX_WINDOW_CM, floor10(hi - lo - 2 * WINDOW_GAP_CM)))
            val r = out.room(roomId) ?: continue
            out = out.replace(r.copy(openings = r.openings + added(plan, newWindow(id++, k, (lo + hi) / 2, width))))
            left -= width * probe.height
        }
        return out.takeIf { left <= 0 }
    }

    /**
     * Ambiente da abitare con finestre apribili sotto 1/8 del pavimento (o senza): le correzioni, nell'ordine in cui provare.
     * Prima una finestra che c'è allargata quanto serve (fino a 180 cm, o un quarto in più se è già più larga), nel suo tratto di
     * muro libero da porte, finestre e mobili alti; poi finestre nuove, alte 120 col davanzale a 90 come quella del menu, larghe
     * quanto serve (fino a 180 cm, se ne servono di più larghe uguali), nel mezzo dei tratti più lunghi dei muri che danno fuori
     * (al massimo quattro).
     */
    private fun windowFixes(plan: FloorPlan, space: List<Room>): List<FloorPlan> {
        if (space.isEmpty() || space.any { it.outdoor }) return emptyList()
        val need = missingLight(plan, space)
        if (need <= 0) return emptyList()
        // Nel piano con stato di fatto e di progetto la finestra che c'è già non si allarga (sarebbe da chiudere e da riaprire):
        // se ne mette una nuova.
        val phased = Phases.hasChanges(plan)
        val widened = space.flatMap { r ->
            HousingRules.windows(plan, r).filter {
                it.kind.isWindow && r.arcOf(it.wallIndex) == null && it.height > 0 && (!phased || it.phase == Phase.New)
            }.sortedByDescending { it.width }.mapNotNull { o ->
                // Fino a 180 cm, o un quarto in più per una finestra già larga.
                val width = ceil10(o.width + need / o.height)
                if (width > max(MAX_WINDOW_CM, ceil10(o.width * 1.25))) return@mapNotNull null
                // Il tratto libero dove sta, senza di lei.
                val without = r.copy(openings = r.openings.filter { it.id != o.id })
                val k = o.wallIndex
                val len = r.wallLength(k)
                val blocks = wallBlocks(plan.replace(without), without, k, openings = true, above = o.bottom) + neighbours(plan, r, k, outdoor = false)
                val (lo, hi) = freeRuns(blocks, len, width + 2 * WINDOW_GAP_CM).firstOrNull { (lo, hi) -> o.position in lo..hi } ?: return@mapNotNull null
                val at = o.position.coerceIn(lo + WINDOW_GAP_CM + width / 2, hi - WINDOW_GAP_CM - width / 2)
                val kind = if (o.kind == OpeningKind.Window1 && width > 90.0) OpeningKind.Window2 else o.kind
                plan.replace(r.copy(openings = r.openings.map { if (it.id == o.id) o.copy(kind = kind, width = width, position = at) else it }))
            }
        }
        return widened.take(2) + (0 until 3).mapNotNull { addedWindows(plan, space, need, it) }
    }

    /**
     * Piano senza nessuna finestra (pianta appena ricalcata o letta): in ogni ambiente da abitare le finestre per arrivare a 1/8
     * del pavimento ([windowFixes]) e in ogni bagno una ([bathWindows]), dove ci sono muri che danno fuori; null se non se ne
     * mette nessuna.
     */
    private fun allWindows(plan: FloorPlan): FloorPlan? {
        var out = plan
        for (space in HousingRules.livingSpaces(plan)) {
            val now = space.mapNotNull { out.room(it.id) }
            if (now.isEmpty() || HousingRules.floorArea(now) < TINY_ROOM_CM2) continue
            val need = missingLight(out, now)
            out = (0 until 3).firstNotNullOfOrNull { addedWindows(out, now, need, it) } ?: continue
        }
        for (r in plan.rooms) {
            if (r.type != RoomType.Bagno || r.outdoor || HousingRules.demolished(r)) continue
            out = out.room(r.id)?.let { bathWindows(out, it).firstOrNull() } ?: continue
        }
        return out.takeIf { it != plan }
    }

    /** Larghezza (cm) della finestra che «Correggi» mette nel bagno, e quella più stretta se il muro libero è corto. */
    private const val BATH_WINDOW_CM = 80.0
    private const val MIN_BATH_WINDOW_CM = 60.0

    /**
     * Bagno senza finestra apribile: una finestra a un'anta come quella del menu (alta 120, davanzale a 90), larga 80 (60 se il
     * tratto è corto), nel mezzo di uno dei tratti più lunghi (al massimo tre, nell'ordine) dei muri dritti che danno fuori (anche
     * su un balcone), liberi da porte, finestre e mobili alti. Nessuna se il bagno non ha muri che danno fuori.
     */
    private fun bathWindows(plan: FloorPlan, r: Room): List<FloorPlan> {
        if (r.outdoor) return emptyList()
        val probe = newWindow(0, 0, 0.0, BATH_WINDOW_CM)
        val need = MIN_BATH_WINDOW_CM + 2 * WINDOW_GAP_CM
        return (0 until r.wallCount).filter { k ->
            !r.isRemoved(k) && r.arcOf(k) == null && r.phaseOf(k) != Phase.Demolish && r.wallHeight(k) >= probe.bottom + probe.height + 10.0 &&
                r.wallLength(k) >= need
        }.flatMap { k ->
            val blocks = wallBlocks(plan, r, k, openings = true, above = probe.bottom) + neighbours(plan, r, k, outdoor = false)
            freeRuns(blocks, r.wallLength(k), need).map { (lo, hi) -> Triple(k, lo, hi) }
        }.sortedByDescending { it.third - it.second }.take(3).map { (k, lo, hi) ->
            val width = min(BATH_WINDOW_CM, floor10(hi - lo - 2 * WINDOW_GAP_CM))
            plan.replace(r.copy(openings = r.openings + added(plan, newWindow(plan.nextOpeningId, k, (lo + hi) / 2, width))))
        }
    }

    /** I tratti liberi (inizio e fine) di un muro lungo `len`, tra i tratti occupati `blocks`, lunghi almeno `need`. */
    private fun freeRuns(blocks: List<Pair<Double, Double>>, len: Double, need: Double): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        var from = 0.0
        for ((x0, x1) in blocks.sortedBy { it.first } + (len to len)) {
            if (min(x0, len) - from >= need) out += from to min(x0, len)
            from = max(from, x1)
        }
        return out
    }

    /**
     * Stanza senza calorifero: dove metterlo, nell'ordine in cui provare. Un calorifero come quello appena messo dal menu (80 ×
     * 60), sotto la finestra (prima la più larga) se il davanzale è più alto e lì non ci sono mobili; se no nel mezzo del tratto
     * di muro più lungo libero da porte, finestre, impianti e mobili. Nel bagno uno scaldasalviette (50 × 120), sul muro libero.
     */
    private fun heatFixes(plan: FloorPlan, i: Issue): List<FloorPlan> {
        val r = plan.room(i.rooms.singleOrNull() ?: return emptyList())?.takeIf { !it.outdoor } ?: return emptyList()
        val bath = r.type == RoomType.Bagno
        val base = Fixture.default(plan.nextFixtureId, FixtureKind.Radiator).let {
            if (bath) it.copy(radiatorModel = RadiatorModel.TowelRail, length = 50.0, height = 120.0, elevation = 20.0) else it
        }
        val need = base.length + 2 * FIXTURE_GAP_CM
        val walls = (0 until r.wallCount).filter { k -> !r.isRemoved(k) && r.arcOf(k) == null && r.wallLength(k) >= need }
        val spots = mutableListOf<Pair<Int, Double>>()
        if (!bath) {
            for (o in r.openings.filter { it.kind.isWindow && it.wallIndex in walls && it.bottom >= base.elevation + base.height + 5.0 }.sortedByDescending { it.width }) {
                val half = base.length / 2
                if (wallBlocks(plan, r, o.wallIndex, openings = false).none { (x0, x1) -> x1 > o.position - half && x0 < o.position + half }) {
                    spots += o.wallIndex to o.position
                }
            }
        }
        walls.flatMap { k -> freeRuns(wallBlocks(plan, r, k, openings = true), r.wallLength(k), need).map { (lo, hi) -> Triple(k, lo, hi) } }
            .sortedByDescending { it.third - it.second }
            .forEach { (k, lo, hi) -> spots += k to (lo + hi) / 2 }
        return spots.distinct().take(MAX_SYSTEM_SPOTS).map { (k, at) -> plan.replace(r.copy(fixtures = r.fixtures + base.copy(wallIndex = k, position = at))) }
    }

    /**
     * Cucina o bagno senza punto acqua: sul muro dietro al lavello (in bagno al lavabo, poi al vaso, alla doccia o alla vasca),
     * all'altezza di quello messo dal menu. Senza nessuno di questi addossato a un muro non si sa dove va.
     */
    private fun waterFixes(plan: FloorPlan, i: Issue): List<FloorPlan> {
        val r = plan.room(i.rooms.singleOrNull() ?: return emptyList())?.takeIf { !it.outdoor } ?: return emptyList()
        val inside = r.interior()
        if (inside.size < 3) return emptyList()
        fun rank(f: Furniture): Int? = when (FurnitureCatalog.item(f.model)?.symbolKind) {
            FurnitureSymbol.Sink -> 0
            FurnitureSymbol.Toilet -> 1
            FurnitureSymbol.Shower, FurnitureSymbol.Tub -> 2
            else -> null
        }
        val base = Fixture.default(plan.nextFixtureId, FixtureKind.WaterPoint)
        return plan.furniture.filter { Polygon.contains(inside, it.center) }.mapNotNull { f -> rank(f)?.let { f to it } }.sortedBy { it.second }
            .mapNotNull { (f, _) -> behind(r, f) }.distinct().take(MAX_SYSTEM_SPOTS)
            .map { (k, at) -> plan.replace(r.copy(fixtures = r.fixtures + base.copy(wallIndex = k, position = at))) }
    }

    /** Il muro dietro il mobile `f` della stanza `r` (con il retro addossato) e il punto del muro dietro al suo centro, o null. */
    private fun behind(r: Room, f: Furniture): Pair<Int, Double>? {
        val v = Furnishings.axes(f).second
        val back = f.center - v * (f.depth / 2)
        return (0 until r.wallCount).filter { k -> !r.isRemoved(k) && r.arcOf(k) == null }.mapNotNull { k ->
            val a = r.wallStart(k)
            val len = r.wallLength(k)
            val n = r.outwardOf(k)
            // Il mobile guarda dentro la stanza: il retro verso il muro.
            if ((v dot n) > -0.7 || len < 2 * FIXTURE_GAP_CM) return@mapNotNull null
            val d = -((back - a) dot n)
            if (d < -1.0 || d > r.thicknessOf(k) / 2 + WALL_BAND_CM) return@mapNotNull null
            val t = (f.center - a) dot ((r.wallEnd(k) - a) / len)
            if (t < 0.0 || t > len) return@mapNotNull null
            Triple(k, t.coerceIn(FIXTURE_GAP_CM / 2, len - FIXTURE_GAP_CM / 2), d)
        }.minByOrNull { it.third }?.let { it.first to it.second }
    }

    /** Altezza (cm) da terra delle prese che «Correggi» mette in cucina e in bagno: sopra il piano di lavoro e il lavabo. */
    private const val HIGH_OUTLET_CM = 110.0

    /** Spazio (cm) che una presa messa da «Correggi» lascia dagli spigoli, da porte e finestre, dagli impianti e dai mobili. */
    private const val OUTLET_GAP_CM = 15.0

    /** Distanza (cm) delle prese da doccia e vasca: fuori dalla zona 2 della norma CEI 64-8. */
    private const val WET_ZONE_CM = 60.0

    /** Distanza (cm) tra le prese sopra il piano di lavoro della cucina. */
    private const val COUNTER_OUTLETS_CM = 60.0

    /** Distanza (cm) minima dai muri delle luci al soffitto messe da «Correggi». */
    private const val LIGHT_WALL_CM = 40.0

    /** Volte che le luci al soffitto si spostano nel mezzo della loro parte di stanza ([ceilingSpots]), al massimo. */
    private const val LIGHT_ROUNDS = 20

    /** Altezza (cm) da terra della cima di un mobile basso che fa da piano di lavoro (basi della cucina). */
    private val COUNTER_TOP_CM = 75.0..100.0

    /**
     * Posto per un impianto piccolo a muro: muro e distanza dal suo inizio ([Fixture.position]), punto sul filo interno, spazio
     * libero (cm) ai due lati lungo il muro, se davanti c'è il piano di lavoro.
     */
    private class WallSpot(val wall: Int, val at: Double, val point: Vec2, val clear: Double, val counter: Boolean)

    /**
     * Posti (ogni 10 cm) per un impianto piccolo a muro a `elevation` da terra nella stanza `r`: sui muri dritti e abbastanza
     * alti, lontano dagli spigoli, da porte e finestre, dagli impianti e dai mobili addossati a quell'altezza, non sopra il lavello,
     * il lavabo o i fuochi; nel bagno fuori dalla zona di doccia e vasca; su balconi e terrazze solo sul muro verso la casa.
     */
    private fun wallSpots(plan: FloorPlan, r: Room, elevation: Double): List<WallSpot> {
        val inside = r.interior()
        fun symbol(f: Furniture) = FurnitureCatalog.item(f.model)?.symbolKind
        val wet = if (r.type != RoomType.Bagno) emptyList() else plan.furniture.filter { f ->
            (symbol(f) == FurnitureSymbol.Shower || symbol(f) == FurnitureSymbol.Tub) && Polygon.contains(inside, f.center)
        }.map(Furnishings::outline)
        val wetOrHot = plan.furniture.filter { symbol(it) == FurnitureSymbol.Sink || symbol(it) == FurnitureSymbol.Hob }
        val counters = plan.furniture.filter { f -> f.elevation < FIXTURE_GAP_CM && f.elevation + f.height in COUNTER_TOP_CM }
        val n = r.wallCount
        val out = mutableListOf<WallSpot>()
        for (k in 0 until n) {
            if (r.isRemoved(k) || r.arcOf(k) != null || r.wallHeight(k) < elevation + FIXTURE_GAP_CM) continue
            val len = r.wallLength(k)
            val from = kotlin.math.ceil(r.thicknessOf((k + n - 1) % n) / 2 + OUTLET_GAP_CM)
            val to = len - r.thicknessOf((k + 1) % n) / 2 - OUTLET_GAP_CM
            val blocks = (wallBlocks(plan, r, k, openings = true, above = elevation - FIXTURE_GAP_CM / 2, below = elevation + FIXTURE_GAP_CM / 2) +
                wetOrHot.mapNotNull { against(r, k, it) }).map { (x0, x1) -> x0 - OUTLET_GAP_CM to x1 + OUTLET_GAP_CM }
            val house = if (r.outdoor) neighbours(plan, r, k, outdoor = false) else null
            val counter = counters.mapNotNull { against(r, k, it, facing = true) }
            var at = from
            while (at <= to) {
                val t = at
                at += PROBE_STEP_CM
                if (blocks.any { (x0, x1) -> t in x0..x1 } || (house != null && house.none { (x0, x1) -> t in x0..x1 })) continue
                val p = Fixtures.wallAnchor(r, Fixture(0, FixtureKind.Outlet, wallIndex = k, position = t)).first
                if (wet.any { w -> Polygon.contains(w, p) || w.indices.any { j -> Polygon.distanceToSegment(p, w[j], w[(j + 1) % w.size]) < WET_ZONE_CM } }) continue
                val clear = blocks.fold(min(t - from, to - t)) { c, (x0, x1) -> min(c, if (t < x0) x0 - t else t - x1) }
                out += WallSpot(k, t, p, clear, counter.any { (x0, x1) -> t in x0..x1 })
            }
        }
        return out
    }

    /**
     * Tra i posti `spots` quello più lontano da tutti i punti `taken` (a pari, con più spazio libero intorno), almeno `apart` cm;
     * senza punti, quello con più spazio libero intorno (nel mezzo del tratto libero più lungo). Null se non ce n'è.
     */
    private fun farthest(spots: List<WallSpot>, taken: List<Vec2>, apart: Double): WallSpot? {
        if (taken.isEmpty()) return spots.maxByOrNull { it.clear }
        return spots.map { s -> s to taken.minOf { it.distanceTo(s.point) } }.filter { it.second >= apart }
            .maxWithOrNull(compareBy<Pair<WallSpot, Double>> { it.second }.thenBy { it.first.clear })?.first
    }

    /**
     * Stanza con meno punti presa o punti luce di quelli che chiede la norma ([ElectricRules.minimum]): si mettono quelli che
     * mancano. Le prese a muro ([wallSpots]), ognuna il più lontano possibile da quelle che ci sono già (senza nessuna, nel mezzo
     * del tratto di muro libero più lungo): a 30 cm da terra come quella del menu, in cucina e in bagno a 110 (in cucina prima
     * sopra il piano di lavoro). Le luci: plafoniere al soffitto, sparse nella stanza insieme a quelle che ci sono già
     * ([ceilingSpots]); su balconi e terrazze plafoniere a parete, sul muro verso la casa. Null se non trovano posto.
     */
    private fun pointFixes(plan: FloorPlan, i: Issue): FloorPlan? {
        val r = plan.room(i.rooms.singleOrNull() ?: return null) ?: return null
        val min = ElectricRules.minimum(plan, r) ?: return null
        val has = ElectricRules.points(r)
        if (has.outlets >= min.outlets && has.lights >= min.lights) return null
        var id = plan.nextFixtureId
        val added = mutableListOf<Fixture>()
        fun onWalls(base: Fixture, count: Int, taken: MutableList<Vec2>, first: (List<WallSpot>, MutableList<Vec2>) -> WallSpot? = { _, _ -> null }): Boolean {
            val spots = wallSpots(plan, r, base.elevation)
            repeat(count) {
                val s = first(spots, taken) ?: farthest(spots, taken, 2 * OUTLET_GAP_CM) ?: return false
                added += base.copy(id = id++, wallIndex = s.wall, position = s.at)
                taken += s.point
            }
            return true
        }
        if (has.outlets < min.outlets) {
            val kitchen = r.type == RoomType.Cucina
            val base = Fixture.default(0, FixtureKind.Outlet).let { if (kitchen || r.type == RoomType.Bagno) it.copy(elevation = HIGH_OUTLET_CM) else it }
            val taken = r.fixtures.filter { it.kind == FixtureKind.Outlet }.mapTo(mutableListOf()) { Fixtures.center(r, it) }
            val counter = { spots: List<WallSpot>, t: MutableList<Vec2> -> if (kitchen) farthest(spots.filter { it.counter }, t, COUNTER_OUTLETS_CM) else null }
            if (!onWalls(base, min.outlets - has.outlets, taken, counter)) return null
        }
        if (has.lights < min.lights) {
            val lights = r.fixtures.filter { it.kind.isLight }.mapTo(mutableListOf()) { Fixtures.center(r, it) }
            if (r.outdoor) {
                if (!onWalls(Fixture.default(0, FixtureKind.WallLight), min.lights - has.lights, lights)) return null
            } else {
                val points = ceilingSpots(r.interior(), lights, min.lights - has.lights) ?: return null
                for (p in points) added += Fixture.default(id++, FixtureKind.CeilingLight).copy(point = p)
            }
        }
        return plan.replace(r.copy(fixtures = r.fixtures + added))
    }

    /**
     * Dove mettere `need` luci al soffitto nella stanza con il pavimento `inside` e le luci `fixed`: la stanza si divide in parti,
     * ognuna con i punti più vicini a una luce, e ogni luce nuova va nel punto più vicino al mezzo della sua parte, finché non si
     * sposta più. Per cominciare la prima nel punto più lontano dai muri, le altre ognuna il più lontano dalle luci. Sempre ad
     * almeno [LIGHT_WALL_CM] dai muri, al centimetro; null se la stanza è troppo stretta.
     */
    private fun ceilingSpots(inside: List<Vec2>, fixed: List<Vec2>, need: Int): List<Vec2>? {
        if (inside.size < 3) return null
        val b = Polygon.bounds(inside)
        val step = max(PROBE_STEP_CM, max(b.width, b.height) / 40)
        val samples = mutableListOf<Vec2>()
        var y = b.minY + step / 2
        while (y < b.maxY) {
            var x = b.minX + step / 2
            while (x < b.maxX) {
                val p = Vec2(kotlin.math.round(x), kotlin.math.round(y))
                if (Polygon.contains(inside, p)) samples += p
                x += step
            }
            y += step
        }
        fun wallDistance(p: Vec2) = inside.indices.minOf { j -> Polygon.distanceToSegment(p, inside[j], inside[(j + 1) % inside.size]) }
        val ok = samples.filter { wallDistance(it) >= LIGHT_WALL_CM }
        if (ok.isEmpty()) return null
        val centers = mutableListOf<Vec2>()
        repeat(need) {
            val all = fixed + centers
            centers += if (all.isEmpty()) ok.maxBy(::wallDistance) else ok.maxBy { p -> all.minOf { it.distanceTo(p) } }
        }
        repeat(LIGHT_ROUNDS) {
            val all = fixed + centers
            val parts = samples.groupBy { s -> all.indices.minBy { all[it].distanceTo(s) } }
            var moved = false
            for (j in centers.indices) {
                val part = parts[fixed.size + j] ?: continue
                val mean = Vec2(part.sumOf { it.x } / part.size, part.sumOf { it.y } / part.size)
                val next = ok.minBy { it.distanceTo(mean) }
                if (next != centers[j]) {
                    centers[j] = next
                    moved = true
                }
            }
            if (!moved) return centers
        }
        return centers
    }

    /**
     * Da quale stanza è meglio entrare in casa (prima il numero più basso), o null se non da questa: l'ingresso (dal nome), i
     * corridoi, il soggiorno, la cucina, le altre stanze, le camere; non bagni e ripostigli.
     */
    private fun entranceFrom(r: Room): Int? {
        val name = r.name.lowercase()
        return when {
            ENTRANCE_NAMES.any { it in name } -> 0
            r.type == RoomType.Bagno || CLOSET_NAMES.any { it in name } -> null
            r.type == RoomType.Corridoio || PASSAGE_NAMES.any { it in name } -> 1
            r.type == RoomType.Soggiorno -> 2
            r.type == RoomType.Cucina -> 3
            r.type == RoomType.Camera || r.type == RoomType.Cameretta -> 5
            else -> 4
        }
    }

    /** Nomi dell'ingresso di casa (in minuscolo, nelle lingue dell'app). */
    private val ENTRANCE_NAMES = listOf("ingresso", "atrio", "entrance", "entry", "lobby", "entrée", "recibidor", "vestíbulo", "entrada")

    /** Nomi di stanze di passaggio e di ripostigli (in minuscolo, nelle lingue dell'app): per scegliere dove va una porta. */
    private val PASSAGE_NAMES = listOf(
        "corridoio", "disimpegno", "antibagno", "ingresso", "atrio", "corridor", "hall", "entrance", "lobby", "couloir", "entrée",
        "dégagement", "pasillo", "recibidor", "distribuidor", "vestíbulo",
    )
    private val CLOSET_NAMES = listOf(
        "armadio", "ripostiglio", "guardaroba", "lavanderia", "dispensa", "closet", "storage", "pantry", "laundry", "dressing",
        "placard", "cellier", "buanderie", "trastero", "vestidor", "despensa", "lavadero",
    )

    /**
     * Verso quale stanza va meglio la porta di una stanza che non ne ha (prima il numero più basso): corridoi, disimpegni e
     * ingressi (anche dal nome), poi il soggiorno, poi le altre; per ultimi bagni, ripostigli e cabine armadio.
     */
    private fun doorTowards(o: Room): Int {
        val name = o.name.lowercase()
        return when {
            o.type == RoomType.Corridoio || PASSAGE_NAMES.any { it in name } -> 0
            o.type == RoomType.Soggiorno -> 1
            o.type == RoomType.Bagno || CLOSET_NAMES.any { it in name } -> 3
            else -> 2
        }
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

        /**
         * Stanze in cui non si entra, nella casa come sarà (una porta da chiudere non fa più entrare, un muro da demolire apre il
         * passaggio); intanto guarda se almeno una porta del piano dà fuori.
         */
        fun doors(below: Pair<FloorPlan, Double>?, rise: Double) {
            val project = Phases.view(plan, PhaseView.Project)
            val rooms = project.rooms
            val shared = Openings.sharedGaps(project).map { it.roomId }.toSet()
            // Lati aperti: solo dove dall'altra parte non c'è il muro di un'altra stanza.
            val opened = Openings.removedStretches(project).map { it.roomId }.toSet()
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
