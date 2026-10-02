package com.sagoma.planimetria.model

import kotlinx.serialization.Serializable
import kotlin.math.hypot

/** Punto/vettore in coordinate mondo, espresse in centimetri. L'asse y cresce verso il basso (come lo schermo). */
@Serializable
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    operator fun div(k: Double) = Vec2(x / k, y / k)
    infix fun dot(o: Vec2) = x * o.x + y * o.y
    /** Prodotto vettoriale (componente z): 0 se i due vettori sono paralleli. */
    fun cross(o: Vec2) = x * o.y - y * o.x
    val length: Double get() = hypot(x, y)
    fun normalized(): Vec2 = if (length == 0.0) this else this / length
    /** Perpendicolare (rotazione di 90°). */
    fun perp(): Vec2 = Vec2(-y, x)
    fun distanceTo(o: Vec2) = (this - o).length

    companion object {
        val Zero = Vec2(0.0, 0.0)
    }
}

/**
 * Tipi di stanza (§3). Il colore è un ARGB opaco; l'intensità del riempimento la decide il renderer.
 * `outdoor`: spazio esterno (balcone, terrazza), con il parapetto al posto dei muri e senza soffitto.
 */
enum class RoomType(val label: String, val defaultName: String, val argb: Long, val outdoor: Boolean = false) {
    Altro("Altro", "Stanza", 0xFF9E9E9E),
    Soggiorno("Soggiorno", "Soggiorno", 0xFFE8A33D),
    Camera("Camera da letto", "Camera da letto", 0xFF5C7CFA),
    Cucina("Cucina", "Cucina", 0xFFE8590C),
    Bagno("Bagno", "Bagno", 0xFF22B8CF),
    Cameretta("Cameretta", "Cameretta", 0xFFCC5DE8),
    Studio("Studio", "Studio", 0xFF37B24D),
    Corridoio("Corridoio", "Corridoio", 0xFF868E96),
    Balcone("Balcone", "Balcone", 0xFF12B886, outdoor = true),
    Terrazza("Terrazza", "Terrazza", 0xFF74B816, outdoor = true),
}

/** Parapetto di balconi e terrazze. I nomi delle costanti finiscono nel file salvato. */
enum class Parapet(val label: String) {
    Railing("Ringhiera"),
    Wall("Muretto"),
    Glass("Vetro"),
}

/** Forme disponibili alla creazione (§1). */
enum class RoomShape { Square, Rectangle, L }

/** Dove sporge la protuberanza di una stanza a L (§1). */
enum class LOrientation(val label: String) {
    TopRight("In alto a destra"),
    TopLeft("In alto a sinistra"),
    BottomRight("In basso a destra"),
    BottomLeft("In basso a sinistra"),
}

/**
 * Tipi di apertura (§5): porte, infissi (finestre, balconi, porta-finestra) e aperture senza porta.
 * I nomi delle costanti finiscono nel file salvato: si possono aggiungere tipi, non rinominarli.
 */
enum class OpeningKind(val label: String, val leaves: Int, val glazed: Boolean) {
    Door("Porta ad 1 anta", leaves = 1, glazed = false),
    Door2("Porta a 2 ante", leaves = 2, glazed = false),
    Window1("Finestra 1 anta", leaves = 1, glazed = true),
    Window2("Finestra 2 ante", leaves = 2, glazed = true),
    Balcony1("Balcone 1 anta", leaves = 1, glazed = true),
    Balcony2("Balcone 2 ante", leaves = 2, glazed = true),

    /** Non più offerta: resta solo per leggere i file vecchi, dove viene convertita in [Balcony1]. */
    FrenchDoor("Porta-finestra", leaves = 1, glazed = true),
    Passage("Apertura senza porta", leaves = 0, glazed = false);

    /** Infissi vetrati: hanno l'altezza da terra (davanzale; 0 per i balconi). */
    val hasSill get() = glazed
    /** Porte e aperture senza porta collegano due stanze: creano un varco anche nella stanza confinante. */
    val createsSharedGap get() = this == Door || this == Door2 || this == Passage
    /** Porte, finestre e balconi hanno ante: possono essere a battente o scorrevoli. */
    val hasLeaves get() = leaves > 0
}

enum class PassageStyle(val label: String) { Square("Squadrata"), Arched("Ad arco") }

/**
 * Apertura su un muro. `position` è la distanza (cm) del centro dell'apertura dall'inizio del muro;
 * viene sempre limitata alla lunghezza reale del muro quando lo si usa (vedi `Openings.span`).
 */
@Serializable
data class Opening(
    val id: Long,
    val kind: OpeningKind,
    val wallIndex: Int,
    val position: Double,
    val width: Double,
    val height: Double,
    /** Altezza da terra / davanzale: solo finestre. */
    val sillHeight: Double = 0.0,
    /**
     * Anta singola: a battente è il lato del cardine, da scorrevole il lato verso cui scorre.
     * Sinistra guardando il muro dall'interno della stanza.
     */
    val hingeLeft: Boolean = true,
    val opensInward: Boolean = true,
    val style: PassageStyle = PassageStyle.Square,
    /** Ante scorrevoli invece che a battente (porte, finestre, balconi). */
    val sliding: Boolean = false,
    /** Modello dell'anta (porte). */
    val doorModel: DoorModel = DoorModel.Flush,
    /** Colore di anta e telaio; null = quello solito (rovere per le porte, bianco per gli infissi). */
    val color: Finish? = null,
    /** Modello di finestre e balconi, e oscurante all'esterno. */
    val windowModel: WindowModel = WindowModel.Classic,
    val shading: Shading = Shading.None,
    /** Stato di fatto / progetto: [Phase.New] = apertura da realizzare, [Phase.Demolish] = da chiudere (tamponare). */
    val phase: Phase = Phase.Existing,
) {
    /** Colore effettivo di anta e telaio. */
    val finish: Finish get() = color ?: if (kind.glazed) Finish.White else Finish.Oak

    /** Ante a battente: hanno l'arco di apertura e un verso (interno/esterno). */
    val swings: Boolean get() = kind.hasLeaves && !sliding
    /** Cardine (battente) o verso di scorrimento (scorrevole): solo con un'anta. */
    val hasSideChoice: Boolean get() = kind.leaves == 1

    companion object {
        fun default(id: Long, kind: OpeningKind, wallIndex: Int, position: Double) = when (kind) {
            OpeningKind.Door -> Opening(id, kind, wallIndex, position, width = 80.0, height = 210.0)
            OpeningKind.Door2 -> Opening(id, kind, wallIndex, position, width = 120.0, height = 210.0)
            OpeningKind.Window1 -> Opening(id, kind, wallIndex, position, width = 80.0, height = 120.0, sillHeight = 90.0)
            OpeningKind.Window2 -> Opening(id, kind, wallIndex, position, width = 120.0, height = 120.0, sillHeight = 90.0)
            OpeningKind.Balcony1 -> Opening(id, kind, wallIndex, position, width = 80.0, height = 240.0)
            OpeningKind.Balcony2 -> Opening(id, kind, wallIndex, position, width = 120.0, height = 240.0)
            OpeningKind.FrenchDoor -> Opening(id, kind, wallIndex, position, width = 90.0, height = 220.0)
            OpeningKind.Passage -> Opening(id, kind, wallIndex, position, width = 80.0, height = 210.0)
        }
    }
}

/** Dove si monta un impianto: su un muro (posizione lungo il muro) o al soffitto (punto libero nella stanza). */
enum class Mount { Wall, Ceiling }

/**
 * Tipi di impianto. I nomi delle costanti finiscono nel file salvato: si possono aggiungere, non rinominare.
 * `linear`: ha una lunghezza e una rotazione (neon, striscia LED).
 */
enum class FixtureKind(val label: String, val mount: Mount, val linear: Boolean = false) {
    Radiator("Calorifero", Mount.Wall),
    Outlet("Presa di corrente", Mount.Wall),
    Switch("Interruttore", Mount.Wall),
    Spotlight("Faretto", Mount.Ceiling),
    Neon("Neon lungo", Mount.Ceiling, linear = true),
    Chandelier("Lampadario", Mount.Ceiling),
    CeilingLight("Plafoniera", Mount.Ceiling),
    LedStrip("Striscia LED", Mount.Ceiling, linear = true),
    WaterPoint("Punto acqua", Mount.Wall);

    val isLight get() = mount == Mount.Ceiling
}

/**
 * Impianto di una stanza (calorifero, presa, interruttore, luce).
 * - A muro: `wallIndex` + `position` (distanza del centro dall'inizio del muro, come le aperture).
 * - Al soffitto: `point`, in coordinate della pianta (si sposta con la stanza).
 * - `length`: larghezza del calorifero o lunghezza di neon/striscia LED; `height`: altezza del calorifero.
 * - `elevation`: altezza da terra (impianti a muro); `rotation`: gradi (luci lineari).
 */
@Serializable
data class Fixture(
    val id: Long,
    val kind: FixtureKind,
    val wallIndex: Int = 0,
    val position: Double = 0.0,
    val point: Vec2 = Vec2.Zero,
    val length: Double = 0.0,
    val height: Double = 0.0,
    val elevation: Double = 0.0,
    val rotation: Double = 0.0,
    /** Modello del termosifone e del lampadario. */
    val radiatorModel: RadiatorModel = RadiatorModel.Panel,
    val lampModel: LampModel = LampModel.Modern,
) {
    companion object {
        fun default(id: Long, kind: FixtureKind) = when (kind) {
            FixtureKind.Radiator -> Fixture(id, kind, length = 80.0, height = 60.0, elevation = 12.0)
            FixtureKind.Outlet -> Fixture(id, kind, elevation = 30.0)
            FixtureKind.Switch -> Fixture(id, kind, elevation = 110.0)
            FixtureKind.WaterPoint -> Fixture(id, kind, elevation = 50.0)
            FixtureKind.Neon -> Fixture(id, kind, length = 120.0)
            FixtureKind.LedStrip -> Fixture(id, kind, length = 200.0)
            else -> Fixture(id, kind)
        }
    }
}

/**
 * Parete sotto il tetto (sottotetto): la parte alta è tagliata in diagonale. La parete è alta normale
 * fino al punto a distanza `start` (cm) dall'angolo basso, poi scende in linea retta fino all'altezza
 * del muro perpendicolare in quell'angolo. `towardEnd`: l'angolo basso è la fine del muro (altrimenti l'inizio).
 * Anche il soffitto della stanza scende allo stesso modo verso quel muro.
 */
@Serializable
data class WallCut(val towardEnd: Boolean, val start: Double)

/**
 * Una stanza: poligono chiuso di angoli. Il muro `i` va da `points[i]` a `points[i + 1]` (ciclico).
 * Il numero di angoli non cambia dopo la creazione, quindi l'indice del muro è stabile.
 */
@Serializable
data class Room(
    val id: Long,
    val name: String,
    val type: RoomType,
    val points: List<Vec2>,
    val ceilingHeight: Double = DEFAULT_CEILING_HEIGHT,
    /** Altezze personalizzate per singolo muro (mansarde), indicizzate per muro. */
    val wallHeights: Map<Int, Double> = emptyMap(),
    /** Porte, finestre e aperture: si spostano insieme alla stanza perché legate a muro + posizione. */
    val openings: List<Opening> = emptyList(),
    /** Impianti: caloriferi, prese, interruttori, luci. */
    val fixtures: List<Fixture> = emptyList(),
    /** Pareti sotto il tetto tagliate in diagonale nella parte alta, indicizzate per muro. */
    val wallCuts: Map<Int, WallCut> = emptyMap(),
    /** Balconi e terrazze: tipo e altezza del parapetto che sostituisce i muri (tranne quelli verso la casa). */
    val parapet: Parapet = Parapet.Railing,
    val parapetHeight: Double = DEFAULT_PARAPET_HEIGHT,
    /** Pavimento e colore delle pareti (nel 3D). */
    val floorFinish: FloorFinish = FloorFinish.RoomColor,
    val wallPaint: WallPaint = WallPaint.White,
    /** Colore di singole pareti (lato interno), indicizzato per muro: vince su [wallPaint]. */
    val wallPaints: Map<Int, WallPaint> = emptyMap(),
    /** Versione pro: colore libero e rivestimento delle pareti; vince su [wallPaint]. */
    val wallFinish: WallFinish? = null,
    /** Versione pro: finitura di singole pareti, indicizzata per muro. */
    val wallFinishes: Map<Int, WallFinish> = emptyMap(),
    /** Versione pro: pavimento con un materiale fotografico ([MaterialCatalog]); vince su [floorFinish] nel 3D. */
    val floorMaterial: String? = null,
    /** Spessore dei muri della stanza (cm) e di singoli muri che fanno eccezione (portanti, tramezzi). */
    val wallThickness: Double = WALL_THICKNESS,
    val wallThicknesses: Map<Int, Double> = emptyMap(),
    /**
     * Muri eliminati (indici): la stanza resta (pavimento, misure), ma da quel lato è aperta, senza muro,
     * come un ambiente unico con quello accanto o un lato aperto verso l'esterno.
     */
    val removedWalls: Set<Int> = emptySet(),
    /** Stato di fatto / progetto dei muri (indici): da demolire o nuovi; assente = esistente. */
    val wallPhases: Map<Int, Phase> = emptyMap(),
) {
    /** Stato del muro `i` (esistente, da demolire, nuovo). */
    fun phaseOf(i: Int): Phase = wallPhases[i] ?: Phase.Existing

    /** Il muro `i` è stato eliminato (lato aperto). */
    fun isRemoved(i: Int): Boolean = i in removedWalls

    /** Spessore del muro `i`. */
    fun thicknessOf(i: Int): Double = wallThicknesses[i] ?: wallThickness

    /** Spessori di tutti i muri, nell'ordine dei muri. */
    val thicknesses: List<Double> get() = List(wallCount) { thicknessOf(it) }

    /**
     * Stessa stanza con un angolo in più a metà del muro `i` (che diventa due muri): aperture, impianti e
     * caratteristiche per muro seguono il loro muro, quelle dei muri successivi scalano di uno.
     */
    fun splitWall(i: Int): Room {
        val a = wallStart(i)
        val b = wallEnd(i)
        val mid = (a + b) / 2.0
        val half = a.distanceTo(b) / 2
        fun <T> shift(m: Map<Int, T>, dup: Boolean) = buildMap {
            for ((k, v) in m) when {
                k < i -> put(k, v)
                k == i -> { put(i, v); if (dup) put(i + 1, v) }
                else -> put(k + 1, v)
            }
        }
        return copy(
            points = points.take(i + 1) + mid + points.drop(i + 1),
            wallHeights = shift(wallHeights, dup = true),
            wallCuts = shift(wallCuts, dup = false),
            wallPaints = shift(wallPaints, dup = true),
            wallFinishes = shift(wallFinishes, dup = true),
            wallThicknesses = shift(wallThicknesses, dup = true),
            removedWalls = shift(removedWalls.associateWith { }, dup = true).keys,
            wallPhases = shift(wallPhases, dup = true),
            openings = openings.map { o ->
                when {
                    o.wallIndex < i -> o
                    o.wallIndex > i -> o.copy(wallIndex = o.wallIndex + 1)
                    o.position <= half -> o
                    else -> o.copy(wallIndex = i + 1, position = o.position - half)
                }
            },
            fixtures = fixtures.map { f ->
                when {
                    f.kind.mount != Mount.Wall || f.wallIndex < i -> f
                    f.wallIndex > i -> f.copy(wallIndex = f.wallIndex + 1)
                    f.position <= half -> f
                    else -> f.copy(wallIndex = i + 1, position = f.position - half)
                }
            },
        )
    }

    /**
     * Stessa stanza senza l'angolo `c` (tra il muro `c - 1` e il muro `c`, che diventano uno solo). Serve
     * almeno un triangolo: con 3 angoli non si toglie nulla. Le aperture del muro `c` passano sul muro unito.
     */
    fun removeCorner(c: Int): Room? {
        if (wallCount <= 3) return null
        val n = wallCount
        val prev = (c - 1 + n) % n
        val newPoints = points.filterIndexed { k, _ -> k != c }
        // Indice nuovo di ogni muro vecchio (il muro c confluisce in prev).
        fun map(k: Int): Int = when {
            k == c -> if (prev > c) prev - 1 else prev
            k > c -> k - 1
            else -> k
        }
        val offset = wallLength(prev)
        fun <T> remap(m: Map<Int, T>) = buildMap { for ((k, v) in m) if (k != c) put(map(k), v) }
        return copy(
            points = newPoints,
            wallHeights = remap(wallHeights),
            wallCuts = remap(wallCuts),
            wallPaints = remap(wallPaints),
            wallFinishes = remap(wallFinishes),
            wallThicknesses = remap(wallThicknesses),
            // Il muro unito resta eliminato solo se lo erano entrambi i pezzi.
            removedWalls = remap(removedWalls.associateWith { }).keys.filter { k -> k != map(c) || (c in removedWalls && prev in removedWalls) }.toSet(),
            // Il muro unito tiene lo stato solo se i due pezzi lo avevano uguale.
            wallPhases = remap(wallPhases).filterKeys { k -> k != map(c) || wallPhases[c] == wallPhases[prev] },
            openings = openings.map { o -> if (o.wallIndex == c) o.copy(wallIndex = map(c), position = o.position + offset) else o.copy(wallIndex = map(o.wallIndex)) },
            fixtures = fixtures.map { f ->
                if (f.kind.mount != Mount.Wall) f
                else if (f.wallIndex == c) f.copy(wallIndex = map(c), position = f.position + offset)
                else f.copy(wallIndex = map(f.wallIndex))
            },
        )
    }

    /** Colore del lato interno del muro `i`: il suo, oppure quello delle pareti della stanza. */
    fun paintOf(i: Int): WallPaint = wallPaints[i] ?: wallPaint

    /** Finitura delle pareti della stanza (pro o colore di tavolozza). */
    val roomFinish: WallFinish get() = wallFinish ?: WallFinish.of(wallPaint)

    /** Finitura del lato interno del muro `i`: la sua (pro o di tavolozza), oppure quella della stanza. */
    fun finishOf(i: Int): WallFinish = wallFinishes[i] ?: wallPaints[i]?.let(WallFinish::of) ?: roomFinish

    /** Il muro `i` ha una finitura sua, diversa da quella della stanza. */
    fun hasOwnFinish(i: Int): Boolean = i in wallFinishes || i in wallPaints

    /** Spazio esterno: niente soffitto, parapetto al posto dei muri. */
    val outdoor: Boolean get() = type.outdoor

    fun opening(id: Long): Opening? = openings.firstOrNull { it.id == id }
    fun fixture(id: Long): Fixture? = fixtures.firstOrNull { it.id == id }

    val wallCount: Int get() = points.size
    fun wallStart(i: Int): Vec2 = points[i]
    fun wallEnd(i: Int): Vec2 = points[(i + 1) % points.size]
    fun wallLength(i: Int): Double = wallStart(i).distanceTo(wallEnd(i))
    fun wallHeight(i: Int): Double = wallHeights[i] ?: ceilingHeight

    companion object {
        const val DEFAULT_CEILING_HEIGHT = 270.0
        const val WALL_THICKNESS = 15.0
        const val DEFAULT_PARAPET_HEIGHT = 100.0
    }
}

/** Metro a schermo (§9): segmento libero sulla pianta, indipendente dalle stanze. */
@Serializable
data class Ruler(val id: Long, val start: Vec2, val end: Vec2) {
    val length: Double get() = start.distanceTo(end)
    val mid: Vec2 get() = (start + end) / 2.0
    val direction: Vec2 get() = (end - start).normalized()

    companion object {
        const val DEFAULT_LENGTH = 100.0
        const val MIN_LENGTH = 10.0
    }
}

/**
 * Quota manuale del disegno (come nei CAD): misura la distanza tra `a` e `b`. La linea di quota è parallela ad
 * a→b, spostata di `offset` cm di lato (positivo a sinistra guardando da a verso b); dagli estremi partono le
 * linee di richiamo. `text`: scritta al posto della misura (null = la misura in cm).
 */
@Serializable
data class Dimension(val id: Long, val a: Vec2, val b: Vec2, val offset: Double = 40.0, val text: String? = null) {
    val length: Double get() = a.distanceTo(b)
    /** Direzione a→b (unitaria) e normale a sinistra. */
    val direction: Vec2 get() = (b - a).normalized()
    val normal: Vec2 get() = direction.perp()
    /** Estremi della linea di quota. */
    val lineA: Vec2 get() = a + normal * offset
    val lineB: Vec2 get() = b + normal * offset
    val label: String get() = text?.takeIf { it.isNotBlank() } ?: formatLength(length)

    companion object {
        /** Misura in cm, con un decimale solo se serve (come le altre quote dell'app). */
        fun formatLength(cm: Double): String {
            val r = kotlin.math.round(cm * 10) / 10
            return if (r == kotlin.math.round(r)) kotlin.math.round(r).toLong().toString() else r.toString().replace('.', ',')
        }
    }
}

/**
 * Testo sulla pianta (nota, etichetta, indicazione): alto `size` cm in scala, ruotato di `rotation` gradi.
 * Con `arrowTo` c'è una freccia di richiamo dal testo a quel punto.
 */
@Serializable
data class TextNote(
    val id: Long,
    val at: Vec2,
    val text: String,
    val size: Double = 20.0,
    val rotation: Double = 0.0,
    val arrowTo: Vec2? = null,
)

/**
 * Livelli del disegno: si nascondono (niente sulla pianta né nel PDF) o si bloccano (si vedono, ma non si
 * toccano). I nomi delle costanti finiscono nel file salvato: si possono aggiungere, non rinominare.
 */
enum class Layer(val label: String) {
    WallLengths("Misure dei muri"),
    Dimensions("Quote"),
    Texts("Testi"),
    Furniture("Arredi"),
    Fixtures("Impianti (prese, luci, caloriferi…)"),
    Structure("Scale, colonne, travi, muri singoli"),
    Rulers("Metri"),
    Underlay("Pianta di sfondo"),
}

/**
 * Stato di fatto / progetto (pratiche edilizie): esistente, da demolire (giallo), nuovo (rosso). Per le
 * aperture: [New] = da aprire, [Demolish] = da chiudere. I nomi finiscono nel file salvato.
 */
enum class Phase(val label: String) {
    Existing("Esistente"),
    Demolish("Da demolire"),
    New("Nuovo"),
}

@Serializable
data class LayerSettings(val hidden: Set<Layer> = emptySet(), val locked: Set<Layer> = emptySet()) {
    fun visible(l: Layer) = l !in hidden
    /** Si può toccare, spostare, selezionare. */
    fun editable(l: Layer) = l !in hidden && l !in locked
}

/**
 * Tipi di scala. I nomi delle costanti finiscono nel file salvato: si possono aggiungere, non rinominare.
 */
enum class StairKind(val label: String) {
    Straight("Rampa dritta"),
    LTurn("A L (con pianerottolo)"),
    UTurn("A U (due rampe)"),
    Spiral("A chiocciola"),
    StraightLanding("Dritta con pianerottolo"),
    LWinder("A L con ventaglio"),
}

/**
 * Scala che sale dal piano su cui sta al piano di sopra: l'altezza da superare è l'interpiano del suo
 * piano, da cui si ricavano numero e altezza dei gradini (vedi `geometry/Stairs`).
 * - `center`: centro dell'ingombro in pianta; `rotation`: gradi (0 = si sale verso l'alto dello schermo).
 * - `width`: larghezza della rampa; `tread`: pedata (profondità di un gradino).
 * - `turnLeft`: le scale a L e a U girano a sinistra invece che a destra; la chiocciola sale in senso antiorario.
 * - `diameter`: solo chiocciola.
 * - `risers`: numero di alzate scelto dall'utente (più alzate = gradini più bassi, scala meno ripida);
 *   null = il minimo con alzate di al massimo 18 cm.
 * - `firstFlight`: scale a L e a U, pedate della prima rampa (prima del pianerottolo); null = metà.
 */
@Serializable
data class Stair(
    val id: Long,
    val kind: StairKind,
    val center: Vec2,
    val rotation: Double = 0.0,
    val width: Double = 90.0,
    val tread: Double = 28.0,
    val turnLeft: Boolean = false,
    val diameter: Double = 160.0,
    val risers: Int? = null,
    val firstFlight: Int? = null,
    /** Struttura, materiale dei gradini e ringhiera. */
    val structure: StairStructure = StairStructure.Masonry,
    val material: StairMaterial = StairMaterial.Wood,
    val railing: StairRailing = StairRailing.None,
    /** Ringhiera attorno al vano scala al piano di sopra (lasciando libero l'arrivo). */
    val wellRailing: StairRailing = StairRailing.None,
    /**
     * Ringhiera della scala al piano di sopra: true = continua e si vede sopra il pavimento (sale fino all'ultimo
     * gradino); false = tagliata a filo del pavimento del piano di sopra.
     */
    val railingAboveFloor: Boolean = true,
    /**
     * Gradini coperti dal pavimento del piano di sopra, contando dal basso (il pianerottolo conta come uno):
     * il vuoto nel solaio resta solo sopra gli altri. 0 = vuoto sopra tutta la scala.
     */
    val coveredSteps: Int = 0,
) {
    /** Scale con due rampe e una svolta o un pianerottolo in mezzo: si sceglie quanti gradini prima. */
    val hasTwoFlights: Boolean get() = kind == StairKind.LTurn || kind == StairKind.UTurn || kind == StairKind.StraightLanding || kind == StairKind.LWinder
}

/** Sezione di una colonna. I nomi delle costanti finiscono nel file salvato. */
enum class ColumnShape(val label: String) {
    Square("Quadrata"),
    Round("Rotonda"),
}

/**
 * Colonna (o pilastro) dal pavimento al soffitto. Può stare in mezzo a una stanza o accostata a un muro,
 * da cui sporge. `width` è il lato (o il diametro), `depth` l'altro lato della colonna quadrata
 * (rettangolare se diversi); `rotation` in gradi.
 */
@Serializable
data class Column(
    val id: Long,
    val center: Vec2,
    val shape: ColumnShape = ColumnShape.Square,
    val width: Double = 30.0,
    val depth: Double = 30.0,
    val rotation: Double = 0.0,
    /** Altezza da terra (cm); null = fino al soffitto. */
    val height: Double? = null,
)

/**
 * Trave a soffitto da `start` a `end` (in pianta, di solito da muro a muro): larga `width`, sporge dal
 * soffitto verso il basso di `depth` cm.
 */
@Serializable
data class Beam(
    val id: Long,
    val start: Vec2,
    val end: Vec2,
    val width: Double = 30.0,
    val depth: Double = 40.0,
) {
    val length: Double get() = start.distanceTo(end)
    val mid: Vec2 get() = (start + end) / 2.0
}

/**
 * Muro singolo (tramezzo, muretto) che non fa parte del contorno di una stanza: dritto da `start` a `end`
 * (mezzeria), spesso `thickness`, alto `height` (null = fino al soffitto), colorato `paint` sui due lati.
 */
@Serializable
data class FreeWall(
    val id: Long,
    val start: Vec2,
    val end: Vec2,
    val thickness: Double = 10.0,
    val height: Double? = null,
    val paint: WallPaint = WallPaint.White,
    /** Versione pro: colore libero e rivestimento (sui due lati); vince su [paint]. */
    val finish: WallFinish? = null,
    /** Stato di fatto / progetto: esistente, da demolire, nuovo. */
    val phase: Phase = Phase.Existing,
) {
    val finishOrPaint: WallFinish get() = finish ?: WallFinish.of(paint)
    val length: Double get() = start.distanceTo(end)
    val mid: Vec2 get() = (start + end) / 2.0
}

/**
 * Arredo (versione pro): un modello 3D del catalogo ([FurnitureCatalog]) appoggiato sul pavimento, o
 * alzato di `elevation` cm (pensili, lampade a soffitto). `width` × `depth` è l'ingombro in pianta,
 * `height` l'altezza: il modello si adatta a queste misure. `rotation` in gradi; a 0 il davanti del
 * mobile guarda verso il basso dello schermo.
 */
@Serializable
data class Furniture(
    val id: Long,
    val model: String,
    val center: Vec2,
    val rotation: Double = 0.0,
    val width: Double,
    val depth: Double,
    val height: Double,
    val elevation: Double = 0.0,
    /** Ribaltato sinistra ↔ destra guardandolo dal davanti (divani angolari, cucine ad angolo…). */
    val mirrored: Boolean = false,
)

/**
 * Immagine di sfondo di un piano: `file` è il nome del file nella cartella del progetto, `origin` la
 * posizione (cm) del suo angolo in alto a sinistra, `cmPerPx` la scala (si ricava dalla taratura su una
 * misura nota). `opacity` da 0 a 1.
 */
@Serializable
data class Underlay(
    val file: String,
    val widthPx: Int,
    val heightPx: Int,
    val origin: Vec2 = Vec2.Zero,
    val cmPerPx: Double = 1.0,
    val opacity: Double = 0.5,
    val visible: Boolean = true,
) {
    val widthCm: Double get() = widthPx * cmPerPx
    val heightCm: Double get() = heightPx * cmPerPx
}

@Serializable
data class FloorPlan(
    val rooms: List<Room> = emptyList(),
    val rulers: List<Ruler> = emptyList(),
    val stairs: List<Stair> = emptyList(),
    val columns: List<Column> = emptyList(),
    val beams: List<Beam> = emptyList(),
    /** Colore delle pareti esterne (facciata) del piano. */
    val facade: WallPaint = WallPaint.White,
    /** Muri singoli (tramezzi) che non chiudono una stanza. */
    val freeWalls: List<FreeWall> = emptyList(),
    /** Versione pro: colore libero e rivestimento della facciata; vince su [facade]. */
    val facadeFinish: WallFinish? = null,
    /** Versione pro: arredi. */
    val furniture: List<Furniture> = emptyList(),
    /** Pianta esistente (foto, scansione, PDF) sotto al disegno, da ricalcare. */
    val underlay: Underlay? = null,
    /** Quote manuali e testi del disegno. */
    val dimensions: List<Dimension> = emptyList(),
    val annotations: List<TextNote> = emptyList(),
) {
    val facadeOrPaint: WallFinish get() = facadeFinish ?: WallFinish.of(facade)

    fun dimension(id: Long?): Dimension? = dimensions.firstOrNull { it.id == id }
    fun replace(d: Dimension): FloorPlan = copy(dimensions = dimensions.map { if (it.id == d.id) d else it })
    val nextDimensionId: Long get() = (dimensions.maxOfOrNull { it.id } ?: 0L) + 1

    fun annotation(id: Long?): TextNote? = annotations.firstOrNull { it.id == id }
    fun replace(t: TextNote): FloorPlan = copy(annotations = annotations.map { if (it.id == t.id) t else it })
    val nextAnnotationId: Long get() = (annotations.maxOfOrNull { it.id } ?: 0L) + 1

    fun furniture(id: Long?): Furniture? = furniture.firstOrNull { it.id == id }
    fun replace(f: Furniture): FloorPlan = copy(furniture = furniture.map { if (it.id == f.id) f else it })
    val nextFurnitureId: Long get() = (furniture.maxOfOrNull { it.id } ?: 0L) + 1

    fun freeWall(id: Long?): FreeWall? = freeWalls.firstOrNull { it.id == id }
    fun replace(wall: FreeWall): FloorPlan = copy(freeWalls = freeWalls.map { if (it.id == wall.id) wall else it })
    val nextFreeWallId: Long get() = (freeWalls.maxOfOrNull { it.id } ?: 0L) + 1

    fun column(id: Long?): Column? = columns.firstOrNull { it.id == id }
    fun replace(column: Column): FloorPlan = copy(columns = columns.map { if (it.id == column.id) column else it })
    val nextColumnId: Long get() = (columns.maxOfOrNull { it.id } ?: 0L) + 1

    fun beam(id: Long?): Beam? = beams.firstOrNull { it.id == id }
    fun replace(beam: Beam): FloorPlan = copy(beams = beams.map { if (it.id == beam.id) beam else it })
    val nextBeamId: Long get() = (beams.maxOfOrNull { it.id } ?: 0L) + 1

    fun ruler(id: Long): Ruler? = rulers.firstOrNull { it.id == id }
    fun replace(ruler: Ruler): FloorPlan = copy(rulers = rulers.map { if (it.id == ruler.id) ruler else it })
    val nextRulerId: Long get() = (rulers.maxOfOrNull { it.id } ?: 0L) + 1

    fun stair(id: Long?): Stair? = stairs.firstOrNull { it.id == id }
    fun replace(stair: Stair): FloorPlan = copy(stairs = stairs.map { if (it.id == stair.id) stair else it })
    val nextStairId: Long get() = (stairs.maxOfOrNull { it.id } ?: 0L) + 1

    fun room(id: Long?): Room? = rooms.firstOrNull { it.id == id }
    fun replace(room: Room): FloorPlan = copy(rooms = rooms.map { if (it.id == room.id) room else it })
    val nextId: Long get() = (rooms.maxOfOrNull { it.id } ?: 0L) + 1
    val nextOpeningId: Long get() = (rooms.flatMap { it.openings }.maxOfOrNull { it.id } ?: 0L) + 1
    val nextFixtureId: Long get() = (rooms.flatMap { it.fixtures }.maxOfOrNull { it.id } ?: 0L) + 1
}

/**
 * Un piano della casa. `levelHeight` è l'interpiano: dal pavimento di questo piano a quello del piano
 * di sopra (soffitto + solaio); è anche l'altezza che devono superare le sue scale.
 */
@Serializable
data class Floor(
    val id: Long,
    val name: String,
    val plan: FloorPlan = FloorPlan(),
    val levelHeight: Double = DEFAULT_LEVEL_HEIGHT,
) {
    companion object {
        const val DEFAULT_LEVEL_HEIGHT = 300.0
        /** Spessore del solaio tra un piano e l'altro, nel 3D. */
        const val SLAB = 30.0
    }
}

/** La casa: i piani dal basso verso l'alto e quello che si sta modificando (`current`). */
@Serializable
data class Building(
    val floors: List<Floor>,
    val current: Int = 0,
    /** Livelli nascosti o bloccati (valgono per tutti i piani). */
    val layers: LayerSettings = LayerSettings(),
) {
    init {
        require(floors.isNotEmpty()) { "Serve almeno un piano" }
    }

    val floor: Floor get() = floors[current.coerceIn(floors.indices)]
    /** Piano di sotto (null al piano più basso). */
    val below: Floor? get() = floors.getOrNull(current - 1)

    /** Quota del pavimento del piano `index` rispetto al piano terra (cm). */
    fun elevation(index: Int): Double = floors.take(index).sumOf { it.levelHeight }

    /** Stessa casa con la pianta del piano corrente sostituita. */
    fun withPlan(plan: FloorPlan): Building =
        if (floor.plan == plan) this else copy(floors = floors.mapIndexed { i, f -> if (i == current) f.copy(plan = plan) else f })

    val nextFloorId: Long get() = (floors.maxOfOrNull { it.id } ?: 0L) + 1

    companion object {
        fun single(plan: FloorPlan) = Building(listOf(Floor(1, floorName(0), plan)))

        /** Nome proposto per il piano `index` (0 = piano terra). */
        fun floorName(index: Int): String = when (index) {
            0 -> "Piano terra"
            1 -> "Primo piano"
            2 -> "Secondo piano"
            3 -> "Terzo piano"
            4 -> "Quarto piano"
            else -> "Piano ${index}"
        }
    }
}
