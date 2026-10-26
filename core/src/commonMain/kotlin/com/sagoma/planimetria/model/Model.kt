package com.sagoma.planimetria.model

import com.sagoma.planimetria.testi.T
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
enum class RoomType(val argb: Long, val outdoor: Boolean = false) {
    Altro(0xFF9E9E9E),
    Soggiorno(0xFFE8A33D),
    Camera(0xFF5C7CFA),
    Cucina(0xFFE8590C),
    Bagno(0xFF22B8CF),
    Cameretta(0xFFCC5DE8),
    Studio(0xFF37B24D),
    Corridoio(0xFF868E96),
    Balcone(0xFF12B886, outdoor = true),
    Terrazza(0xFF74B816, outdoor = true),
    ;

    /** Nome del tipo, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.roomType(this)

    /** Nome con cui nasce una stanza di questo tipo. */
    val defaultName: String get() = com.sagoma.planimetria.testi.T.nomi.roomDefaultName(this)

    /**
     * Pavimento con cui nasce una stanza di questo tipo, così il 3D sembra una casa e non un plastico colorato: parquet dove si
     * vive e si dorme, gres in cucina e bagno, cotto fuori. Si cambia da Info → Finiture.
     */
    val defaultFloor: FloorFinish
        get() = when (this) {
            Cucina, Bagno -> FloorFinish.LightTiles
            Balcone, Terrazza -> FloorFinish.Terracotta
            else -> FloorFinish.OakParquet
        }
}

/** Parapetto di balconi e terrazze. I nomi delle costanti finiscono nel file salvato. */
enum class Parapet {
    Railing,
    Wall,
    Glass,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.parapet(this)
}

/** Forme disponibili alla creazione (§1). */
enum class RoomShape { Square, Rectangle, L }

/** Dove sporge la protuberanza di una stanza a L (§1). */
enum class LOrientation {
    TopRight,
    TopLeft,
    BottomRight,
    BottomLeft,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.lOrientation(this)
}

/**
 * Tipi di apertura (§5): porte, infissi (finestre, balconi, porta-finestra) e aperture senza porta.
 * I nomi delle costanti finiscono nel file salvato: si possono aggiungere tipi, non rinominarli.
 */
enum class OpeningKind(val leaves: Int, val glazed: Boolean) {
    Door(leaves = 1, glazed = false),
    Door2(leaves = 2, glazed = false),
    Window1(leaves = 1, glazed = true),
    Window2(leaves = 2, glazed = true),
    Balcony1(leaves = 1, glazed = true),
    Balcony2(leaves = 2, glazed = true),

    /** Non più offerta: resta solo per leggere i file vecchi, dove viene convertita in [Balcony1]. */
    FrenchDoor(leaves = 1, glazed = true),
    Passage(leaves = 0, glazed = false);

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.openingKind(this)

    /** Infissi vetrati: hanno l'altezza da terra (davanzale; 0 per i balconi). */
    val hasSill get() = glazed
    /** Porte e aperture senza porta collegano due stanze: creano un varco anche nella stanza confinante. */
    val createsSharedGap get() = this == Door || this == Door2 || this == Passage
    /** Porte, finestre e balconi hanno ante: possono essere a battente o scorrevoli. */
    val hasLeaves get() = leaves > 0
}

enum class PassageStyle {
    Square, Arched;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.passageStyle(this)
}

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
    /** Finestre e balconi: infisso a metà muro, a filo esterno o a filo interno. */
    val frame: FramePosition = FramePosition.Center,
    /**
     * Finestre e balconi: sguincio, cioè di quanti cm per lato il vano si allarga verso l'interno della stanza
     * (lati inclinati, come nei muri spessi). 0 = lati dritti.
     */
    val splay: Double = 0.0,
    /** Sguincio arrotondato (lati del vano curvi) invece che inclinato dritto. */
    val splayRounded: Boolean = false,
    /** Porte: è l'ingresso di casa (in pianta il triangolo con «INGRESSO» fuori dalla porta). */
    val entrance: Boolean = false,
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
enum class FixtureKind(val mount: Mount, val linear: Boolean = false) {
    Radiator(Mount.Wall),
    Outlet(Mount.Wall),
    Switch(Mount.Wall),
    Spotlight(Mount.Ceiling),
    Neon(Mount.Ceiling, linear = true),
    Chandelier(Mount.Ceiling),
    CeilingLight(Mount.Ceiling),
    LedStrip(Mount.Ceiling, linear = true),
    WaterPoint(Mount.Wall),
    /** Plafoniera fissata verticalmente a una parete (a muro come presa e interruttore, ma è una luce). */
    WallLight(Mount.Wall),
    /** Faretto fissato a una parete, rivolto verso la stanza. */
    WallSpot(Mount.Wall),
    /** Striscia LED fissata a una parete: lunga `length`, segue il muro (nessuna rotazione), a `elevation` da terra. */
    WallLedStrip(Mount.Wall);

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.fixtureKind(this)

    /** Luce vera: a soffitto o a parete. */
    val isLight get() = when (this) {
        Spotlight, Neon, Chandelier, CeilingLight, LedStrip, WallLight, WallSpot, WallLedStrip -> true
        Radiator, Outlet, Switch, WaterPoint -> false
    }
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
    /**
     * Faretto a parete: rotazione orizzontale del fascio (gradi, [0, 360), 0° = lungo la normale della parete) e inclinazione
     * (gradi, da −90 a 90: 0° orizzontale, positivo verso il basso). I valori di default sono quelli di sempre (45° verso il basso).
     */
    val aimYaw: Double = 0.0,
    val aimTilt: Double = DEFAULT_AIM_TILT,
) {
    companion object {
        /** Inclinazione (gradi verso il basso) del fascio di un faretto a parete appena messo. */
        const val DEFAULT_AIM_TILT = 45.0

        /** Altezza da terra (cm) di plafoniere e faretti a parete appena messi. */
        const val WALL_LIGHT_ELEVATION = 220.0
        /** Striscia LED a parete appena messa: lunghezza e altezza da terra (cm). */
        const val WALL_LED_LENGTH = 200.0
        const val WALL_LED_ELEVATION = 250.0

        fun default(id: Long, kind: FixtureKind) = when (kind) {
            FixtureKind.Radiator -> Fixture(id, kind, length = 80.0, height = 60.0, elevation = 12.0)
            FixtureKind.Outlet -> Fixture(id, kind, elevation = 30.0)
            FixtureKind.Switch -> Fixture(id, kind, elevation = 110.0)
            FixtureKind.WaterPoint -> Fixture(id, kind, elevation = 50.0)
            FixtureKind.WallLight, FixtureKind.WallSpot -> Fixture(id, kind, elevation = WALL_LIGHT_ELEVATION)
            FixtureKind.WallLedStrip -> Fixture(id, kind, length = WALL_LED_LENGTH, elevation = WALL_LED_ELEVATION)
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
    /** Colore libero e rivestimento delle pareti; vince su [wallPaint]. */
    val wallFinish: WallFinish? = null,
    /** Finitura di singole pareti, indicizzata per muro. */
    val wallFinishes: Map<Int, WallFinish> = emptyMap(),
    /** Pavimento con un materiale fotografico ([MaterialCatalog]); vince su [floorFinish] nel 3D. */
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
    /**
     * Muri curvi (indici): ad arco di cerchio tra i due angoli, che a metà si scosta di tanti cm dalla linea dritta
     * (freccia; positiva verso l'esterno della stanza, negativa verso l'interno). Assente = muro dritto.
     */
    val wallBulges: Map<Int, Double> = emptyMap(),
    /**
     * Muri spostati di lato (indici): di quanti cm la mezzeria del muro sta fuori dalla linea della stanza (negativo = verso
     * dentro). Nei muri a scalini il tratto ingrossato si sposta di metà della differenza, così il filo verso la stanza resta
     * dritto. Assente = muro centrato sulla linea.
     */
    val wallOffsets: Map<Int, Double> = emptyMap(),
    /**
     * Stanza creata come SCHIZZO (forma e misure approssimative) e non ancora misurata con la scansione guidata: offre
     * "Avvia scansione". Torna false quando la scansione aggiorna la stanza. Assente nei file vecchi = false; non viene scritta
     * quando è false (i file delle stanze normali restano identici a prima).
     */
    val sketchPending: Boolean = false,
    /**
     * Parti del pavimento che non contano nei metri quadri (fascia "non accessibile" di una mansarda, cavedio, pilastro):
     * l'area utile della stanza è quella interna meno queste zone. Coordinate come [points].
     */
    val excludedZones: List<ExcludedZone> = emptyList(),
    /** Foto della stanza fatte al sopralluogo, solo come promemoria (non diventano il 3D). Assenti nei file vecchi. */
    val photos: List<RoomPhoto> = emptyList(),
) {
    /** Stato del muro `i` (esistente, da demolire, nuovo). */
    fun phaseOf(i: Int): Phase = wallPhases[i] ?: Phase.Existing

    /** Il muro `i` è stato eliminato (lato aperto). */
    fun isRemoved(i: Int): Boolean = i in removedWalls

    /** Spessore del muro `i`. */
    fun thicknessOf(i: Int): Double = wallThicknesses[i] ?: wallThickness

    /** Versore perpendicolare al muro `i`, verso l'esterno della stanza (qualunque sia il verso degli angoli). */
    fun outwardOf(i: Int): Vec2 {
        val a = wallStart(i)
        val b = wallEnd(i)
        val len = a.distanceTo(b)
        if (len < 1e-9) return Vec2.Zero
        val area2 = points.indices.sumOf { k -> val p = points[k]; val q = points[(k + 1) % points.size]; p.x * q.y - q.x * p.y }
        return ((b - a) / len).perp() * (if (area2 >= 0) -1.0 else 1.0)
    }

    /** Spessori di tutti i muri, nell'ordine dei muri. */
    val thicknesses: List<Double> get() = List(wallCount) { thicknessOf(it) }

    /**
     * Stessa stanza con un angolo in più a metà del muro `i` (che diventa due muri): aperture, impianti e
     * caratteristiche per muro seguono il loro muro, quelle dei muri successivi scalano di uno.
     */
    fun splitWall(i: Int): Room {
        val a = wallStart(i)
        val b = wallEnd(i)
        val bulge = wallBulges[i] ?: 0.0
        // Muro curvo: l'angolo nuovo va sulla curva, e le due metà restano parti dello stesso arco.
        val mid = (a + b) / 2.0 + outwardOf(i) * bulge
        val half = a.distanceTo(b) / 2
        val halfBulge = if (bulge == 0.0) 0.0 else {
            val r = (half * half + bulge * bulge) / (2 * bulge)
            val c = a.distanceTo(mid) / 2
            r - kotlin.math.sign(r) * kotlin.math.sqrt((r * r - c * c).coerceAtLeast(0.0))
        }
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
            wallBulges = shift(wallBulges, dup = false).let { if (halfBulge == 0.0) it - i else it + (i to halfBulge) + (i + 1 to halfBulge) },
            wallOffsets = shift(wallOffsets, dup = true),
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
            // Il muro unito torna dritto.
            wallBulges = remap(wallBulges).filterKeys { k -> k != map(c) },
            // Il muro unito torna centrato sulla linea.
            wallOffsets = remap(wallOffsets).filterKeys { k -> k != map(c) },
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

    /** Finitura delle pareti della stanza (finitura libera o colore di tavolozza). */
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
/** Zona non calpestabile di una stanza: si disegna come un poligono e si toglie dall'area utile. */
@Serializable
data class ExcludedZone(val id: Long, val points: List<Vec2>, val name: String = DEFAULT_NAME) {
    companion object {
        const val DEFAULT_NAME = "Non accessibile"
    }
}

/**
 * Foto di riferimento di una stanza: `file` è l'immagine ridotta e `thumb` la miniatura per l'elenco, entrambe tra i file del
 * progetto; `note` è un promemoria scritto dall'utente ("presa a 30 cm dal pavimento").
 */
@Serializable
data class RoomPhoto(val file: String, val thumb: String, val note: String = "", val taken: Long = 0) {
    companion object {
        /** Inizio del nome dei file delle foto: i file che nessuna stanza usa più si possono cancellare. */
        const val PREFIX = "foto-"
    }
}

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
/** Unità delle misure scritte sulla pianta. I nomi finiscono nel file salvato. */
enum class LengthUnit { Centimeters, Meters }

/**
 * Misure scritte sulla pianta nell'unità scelta per il progetto: in centimetri (503) o in metri con due
 * decimali (5,03), come nelle piantine degli architetti. I campi da compilare restano in centimetri.
 */
object PlanUnits {
    /** Unità del progetto aperto (la imposta il disegno della pianta). */
    var unit: LengthUnit = LengthUnit.Centimeters

    fun format(cm: Double): String = when (unit) {
        LengthUnit.Centimeters -> Dimension.formatLength(cm)
        LengthUnit.Meters -> com.sagoma.planimetria.geometry.formatDecimal(cm / 100, 2)
    }

    /** Con l'unità dopo: "503 cm" o "5,03 m". */
    fun withUnit(cm: Double): String = format(cm) + if (unit == LengthUnit.Meters) " m" else " cm"
}

@Serializable
data class Dimension(val id: Long, val a: Vec2, val b: Vec2, val offset: Double = 40.0, val text: String? = null) {
    val length: Double get() = a.distanceTo(b)
    /** Direzione a→b (unitaria) e normale a sinistra. */
    val direction: Vec2 get() = (b - a).normalized()
    val normal: Vec2 get() = direction.perp()
    /** Estremi della linea di quota. */
    val lineA: Vec2 get() = a + normal * offset
    val lineB: Vec2 get() = b + normal * offset
    val label: String get() = text?.takeIf { it.isNotBlank() } ?: PlanUnits.format(length)

    companion object {
        /** Misura in cm, con un decimale solo se serve (come le altre quote dell'app). */
        fun formatLength(cm: Double): String {
            val r = kotlin.math.round(cm * 10) / 10
            return if (r == kotlin.math.round(r)) kotlin.math.round(r).toLong().toString() else r.toString().replace('.', com.sagoma.planimetria.testi.T.lingua.virgola)
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
enum class Layer {
    WallLengths,
    Dimensions,
    Texts,
    Furniture,
    Fixtures,
    Structure,
    Rulers,
    Underlay,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.layer(this)
}

/**
 * Stato di fatto / progetto (pratiche edilizie): esistente, da demolire (giallo), nuovo (rosso). Per le
 * aperture: [New] = da aprire, [Demolish] = da chiudere. I nomi finiscono nel file salvato.
 */
enum class Phase {
    Existing,
    Demolish,
    New,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.phase(this)
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
enum class StairKind {
    Straight,
    LTurn,
    UTurn,
    Spiral,
    StraightLanding,
    LWinder,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.stairKind(this)
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
enum class ColumnShape {
    Square,
    Round,
    ;

    /** Nome da mostrare, nella lingua dei testi ([com.sagoma.planimetria.testi.Nomi]). */
    val label: String get() = com.sagoma.planimetria.testi.T.nomi.columnShape(this)
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
    /** Colore libero e rivestimento (sui due lati); vince su [paint]. */
    val finish: WallFinish? = null,
    /** Stato di fatto / progetto: esistente, da demolire, nuovo. */
    val phase: Phase = Phase.Existing,
    /** Porte, finestre e passaggi nel muro: tutti sul muro 0, da `start` verso `end` (vedi [host]). */
    val openings: List<Opening> = emptyList(),
    /**
     * Muro curvo: di quanto si scosta a metà dalla linea dritta (cm), positiva verso `−(end − start).perp()` (il lato «fuori»
     * del muro 0 della [host]). 0 = dritto.
     */
    val bulge: Double = 0.0,
) {
    val finishOrPaint: WallFinish get() = finish ?: WallFinish.of(paint)
    val length: Double get() = start.distanceTo(end)
    val mid: Vec2 get() = (start + end) / 2.0

    /**
     * Il muro visto come "stanza" di due muri (andata e ritorno sulla mezzeria) che porta le aperture: così porte e finestre
     * sui muri singoli si posizionano, si trascinano e si modificano come quelle delle stanze. Ha l'id negativo ([hostId]); le
     * aperture stanno sul muro 0.
     */
    val host: Room get() = Room(
        id = hostId(id), name = com.sagoma.planimetria.testi.T.vari.muroSingolo, type = RoomType.Altro,
        points = listOf(start, end), wallThickness = thickness, openings = openings,
        wallPhases = if (phase == Phase.Existing) emptyMap() else mapOf(0 to phase, 1 to phase),
        // Andata e ritorno sullo stesso arco: visto dal muro 1 la curva va dall'altra parte.
        wallBulges = if (bulge == 0.0) emptyMap() else mapOf(0 to bulge, 1 to -bulge),
    )

    companion object {
        /** Id della [host] del muro singolo `wallId`: negativo, così non si confonde con le stanze. */
        fun hostId(wallId: Long): Long = -wallId
    }
}

/**
 * Arredo: un modello 3D del catalogo ([FurnitureCatalog]) appoggiato sul pavimento, o
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
    /** Colore libero e rivestimento della facciata; vince su [facade]. */
    val facadeFinish: WallFinish? = null,
    /** Arredi. */
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

    /** Stanza con questo id; con un id negativo, il muro singolo come porta-aperture ([FreeWall.host]). */
    fun room(id: Long?): Room? = rooms.firstOrNull { it.id == id } ?: id?.takeIf { it < 0 }?.let { freeWall(-it)?.host }
    /** Con l'id negativo di un muro singolo cambia solo le sue aperture. */
    fun replace(room: Room): FloorPlan =
        if (room.id < 0) freeWall(-room.id)?.let { replace(it.copy(openings = room.openings)) } ?: this
        else copy(rooms = rooms.map { if (it.id == room.id) room else it })
    /** Stanze e muri singoli con aperture, tutti come porta-aperture. */
    val openingHosts: List<Room> get() = rooms + freeWalls.filter { it.openings.isNotEmpty() }.map { it.host }
    val nextId: Long get() = (rooms.maxOfOrNull { it.id } ?: 0L) + 1
    val nextOpeningId: Long get() = (openingHosts.flatMap { it.openings }.maxOfOrNull { it.id } ?: 0L) + 1
    val nextFixtureId: Long get() = (rooms.flatMap { it.fixtures }.maxOfOrNull { it.id } ?: 0L) + 1
}

/**
 * Un piano della casa. `levelHeight` è l'interpiano: dal pavimento di questo piano a quello del piano
 * di sopra (soffitto + solaio); è anche l'altezza che devono superare le sue scale.
 * `autoLevel`: l'interpiano è calcolato (stanza al chiuso più alta + [SLAB]) e si aggiorna da solo con le
 * stanze; `false` = valore scelto a mano, che non cambia. I file senza questo campo (anteriori) sono manuali.
 */
@Serializable
data class Floor(
    val id: Long,
    val name: String,
    val plan: FloorPlan = FloorPlan(),
    val levelHeight: Double = DEFAULT_LEVEL_HEIGHT,
    val autoLevel: Boolean = false,
) {
    /** Con l'interpiano automatico, lo riporta al valore calcolato dalle stanze; altrimenti non cambia niente. */
    fun synced(): Floor = if (autoLevel) autoLevelHeight(plan).let { if (it == levelHeight) this else copy(levelHeight = it) } else this

    companion object {
        const val DEFAULT_LEVEL_HEIGHT = 300.0
        /** Spessore del solaio tra un piano e l'altro, nel 3D. */
        const val SLAB = 30.0

        /** Interpiano automatico: altezza della stanza al chiuso più alta (i balconi non contano) più il solaio. */
        fun autoLevelHeight(plan: FloorPlan): Double =
            (plan.rooms.filter { !it.outdoor }.maxOfOrNull { it.ceilingHeight } ?: Room.DEFAULT_CEILING_HEIGHT) + SLAB
    }
}

/**
 * Orientamento e luogo della casa. La pianta non si gira mai: [northDeg] dice dove punta il nord sul disegno,
 * in gradi in senso orario dall'alto dello schermo (0 = nord in alto, 90 = nord a destra). [place] è solo la città
 * (null = Roma), per l'altezza del sole e l'ora dell'alba e del tramonto.
 */
@Serializable
data class HouseSite(val northDeg: Double = 0.0, val place: Place? = null)

/** Regola dell'ora legale di un luogo. */
@Serializable
enum class DaylightSaving {
    None,
    /** Unione Europea e dintorni: dall'ultima domenica di marzo all'ultima di ottobre. */
    Europe,
    /** Stati Uniti e Canada: dalla seconda domenica di marzo alla prima di novembre. */
    NorthAmerica,
    /** Australia del sud-est e Nuova Zelanda: dalla prima domenica di ottobre alla prima di aprile. */
    South,
}

/** Città (posizione approssimata, mai l'indirizzo): latitudine e longitudine in gradi, fuso in ore rispetto a UTC. */
@Serializable
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val utcOffset: Double,
    val dst: DaylightSaving = DaylightSaving.Europe,
)

/** La casa: i piani dal basso verso l'alto e quello che si sta modificando (`current`). */
@Serializable
data class Building(
    val floors: List<Floor>,
    val current: Int = 0,
    /** Livelli nascosti o bloccati (valgono per tutti i piani). */
    val layers: LayerSettings = LayerSettings(),
    /** Unità delle misure scritte sulla pianta (quote, righelli, tavola PDF). */
    val units: LengthUnit = LengthUnit.Centimeters,
    /** Dove punta il nord rispetto alla pianta e in che città è la casa: decidono il giro del sole nel 3D (facoltativi). */
    val site: HouseSite = HouseSite(),
    /**
     * Indice in [floors] del piano terra, a livello del terreno: quelli prima sono interrati (aggiunti con «piano sotto»).
     * Nel 3D il prato sta alla sua quota e copre gli interrati.
     */
    val ground: Int = 0,
    /**
     * Luminosità delle lampade della casa nel 3D e nei render, scelta da chi usa l'app: 1 = come le fa sagoma, da
     * [MIN_LAMP_BRIGHTNESS] a [MAX_LAMP_BRIGHTNESS].
     */
    val lampBrightness: Double = 1.0,
) {
    init {
        require(floors.isNotEmpty()) { "Serve almeno un piano" }
    }

    val floor: Floor get() = floors[current.coerceIn(floors.indices)]
    /** Piano di sotto (null al piano più basso). */
    val below: Floor? get() = floors.getOrNull(current - 1)

    /** Quota del pavimento del piano `index` rispetto al piano più basso (cm). */
    fun elevation(index: Int): Double = floors.take(index).sumOf { it.levelHeight }

    /** Indice del piano terra, sempre valido. */
    val groundIndex: Int get() = ground.coerceIn(floors.indices)

    /** Il piano `index` rispetto al piano terra: 0 il piano terra, 1 il primo piano, -1 il primo interrato. */
    fun level(index: Int): Int = index - groundIndex

    /** Stessa casa con la pianta del piano corrente sostituita. */
    fun withPlan(plan: FloorPlan): Building {
        val updated = floor.copy(plan = plan).synced() // con l'interpiano automatico segue le stanze
        return if (updated == floor) this else copy(floors = floors.mapIndexed { i, f -> if (i == current) updated else f })
    }

    val nextFloorId: Long get() = (floors.maxOfOrNull { it.id } ?: 0L) + 1

    companion object {
        const val MIN_LAMP_BRIGHTNESS = 0.25
        const val MAX_LAMP_BRIGHTNESS = 2.0

        /** Casa a un piano. `autoLevel`: l'interpiano segue le stanze (i progetti nuovi); i file vecchi sono manuali. */
        fun single(plan: FloorPlan, autoLevel: Boolean = false) =
            Building(listOf(Floor(1, floorName(0), plan, autoLevel = autoLevel).synced()))

        /** Nome proposto per il piano `index` (0 = piano terra, -1 il primo interrato), nella lingua dell'app o in quella data. */
        fun floorName(index: Int, lingua: com.sagoma.planimetria.testi.Lingua = T.lingua): String = when {
            index < 0 -> lingua.piani.interrato(-index)
            else -> floorNameAbove(index, lingua)
        }

        private fun floorNameAbove(index: Int, lingua: com.sagoma.planimetria.testi.Lingua): String = when (index) {
            0 -> lingua.piani.pianoTerra
            1 -> lingua.piani.primoPiano
            2 -> lingua.piani.secondoPiano
            3 -> lingua.piani.terzoPiano
            4 -> lingua.piani.quartoPiano
            else -> lingua.piani.piano(index)
        }
    }
}
