package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.Finish
import com.sagoma.planimetria.model.GarageDoor
import com.sagoma.planimetria.model.FloorPattern
import com.sagoma.planimetria.model.LampModel
import com.sagoma.planimetria.model.RadiatorModel
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.StairStructure
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.model.WindowShape
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.MaterialLayout
import com.sagoma.planimetria.model.MaterialPieces
import com.sagoma.planimetria.model.Laying
import com.sagoma.planimetria.model.LayingStart
import com.sagoma.planimetria.model.FurnitureCatalog

import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Punto/vettore 3D in cm. Convenzione: x e z sono le coordinate della pianta (z = y della pianta), y è l'altezza. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(this dot this)
    fun normalized(): Vec3 = if (length == 0.0) this else this * (1 / length)
    /** Proiezione sulla pianta. */
    val flat: Vec2 get() = Vec2(x, z)

    companion object {
        val Zero = Vec3(0.0, 0.0, 0.0)
        val Up = Vec3(0.0, 1.0, 0.0)
    }
}

/** Punto della pianta all'altezza `h`. */
fun Vec2.at(h: Double) = Vec3(x, h, y)

/** A cosa appartiene un triangolo: serve a capire cosa si è toccato nella vista 3D. */
sealed interface Pick {
    val roomId: Long
    data class RoomFloor(override val roomId: Long) : Pick
    data class Wall(override val roomId: Long, val index: Int) : Pick
    data class Opening(override val roomId: Long, val openingId: Long) : Pick
    data class Fixture(override val roomId: Long, val fixtureId: Long) : Pick
    /** Una scala non sta in una stanza: `roomId` non corrisponde a nessuna. */
    data class Stair(val stairId: Long) : Pick {
        override val roomId: Long get() = -1L
    }
    data class Column(val columnId: Long) : Pick {
        override val roomId: Long get() = -1L
    }
    data class Beam(val beamId: Long) : Pick {
        override val roomId: Long get() = -1L
    }
    data class FreeWall(val wallId: Long) : Pick {
        override val roomId: Long get() = -1L
    }
    data class Furniture(val furnitureId: Long) : Pick {
        override val roomId: Long get() = -1L
    }
    /** Ringhiera del vano di una scala che sale dal piano di sotto (`stairId` è di quel piano). */
    data class StairWell(val stairId: Long) : Pick {
        override val roomId: Long get() = -1L
    }
}

/** Punto toccato: cosa, dove (cm) e normale della superficie. */
data class Hit(val pick: Pick, val point: Vec3, val normal: Vec3)

/** Tratto di muro che blocca il passaggio quando si cammina (sulla pianta). */
data class Obstacle(val a: Vec2, val b: Vec2)

/** Colore RGBA (0..1). */
data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    fun mix(o: Rgba, t: Float) = Rgba(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t, a + (o.a - a) * t)

    companion object {
        fun argb(v: Long) = Rgba(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
        val White = Rgba(1f, 1f, 1f)
    }
}

/** Coseno dell'angolo (30°) sotto cui due muri di fila si considerano quasi dritti (vedi `nearlyStraightJoin`). */
private val NEARLY_STRAIGHT_COS = cos(PI / 6)

/** Di quanto il muro sotto il vano di una porta resta sotto il pavimento (cm), con un piano sotto. */
private const val THRESHOLD_SINK = 0.5

/**
 * Scena 3D costruita dalla pianta: triangoli opachi e trasparenti (vetri) pronti per la GPU, triangoli
 * "toccabili" con ciò a cui appartengono, e i tratti di muro che bloccano chi cammina.
 * Formato di un vertice: posizione (3), normale (3), colore (4). Normale nulla = superficie che
 * emette luce (le lampade), disegnata senza ombreggiatura.
 */
class Scene3D(
    val opaque: FloatArray,
    val transparent: FloatArray,
    val picks: List<PickTri>,
    val obstacles: List<Obstacle>,
    val bounds: Bounds?,
    /** Arredi da disegnare con i loro modelli 3D (li disegna Filament). */
    val furniture: List<PlacedFurniture> = emptyList(),
    /**
     * Superfici con un materiale fotografico, per materiale: 8 float per vertice
     * (posizione, normale, uv in ripetizioni della foto). Stanno appena sopra le superfici colorate
     * normali, che il renderer semplice continua a disegnare.
     */
    val textured: Map<String, FloatArray> = emptyMap(),
    /** Luci della casa (impianti a soffitto e lampade del catalogo), per i renderer che le sanno usare. */
    val lights: List<SceneLight> = emptyList(),
    /** Finestre e balconi del piano corrente: da lì entra la luce del giorno. */
    val windows: List<SceneWindow> = emptyList(),
    /**
     * Soffitti delle stanze del piano corrente (stesso formato di [opaque]), anche quando non si disegnano
     * (vista dall'alto): per la luce la casa ha comunque il tetto, il sole e il cielo entrano solo dalle aperture.
     */
    val roofs: FloatArray = FloatArray(0),
    /** Quota del terreno (il pavimento del piano terra), se sotto ci sono interrati da coprire; null = sotto il piano più basso. */
    val groundLevel: Double? = null,
    /**
     * Scavo attorno a un piano interrato (in pianta): il terreno è più in alto del piano che si guarda, e il prato lascia qui
     * un buco dove si vedono la terra tagliata e, in fondo, la casa. null = niente scavo.
     */
    val excavation: Bounds? = null,
    /** Stanze del piano corrente (non quelle all'aperto): per la luce del giorno e l'esposizione di chi ci cammina dentro. */
    val rooms: List<SceneRoom> = emptyList(),
) {
    /** Prato attorno alla casa, `margin` cm oltre la pianta: un rettangolo, o quattro attorno allo [excavation]. */
    fun lawn(margin: Double): List<Bounds> {
        val b = bounds ?: return emptyList()
        val h = excavation
        val outer = Bounds(minOf(b.minX, h?.minX ?: b.minX) - margin, minOf(b.minY, h?.minY ?: b.minY) - margin,
            maxOf(b.maxX, h?.maxX ?: b.maxX) + margin, maxOf(b.maxY, h?.maxY ?: b.maxY) + margin)
        if (h == null) return listOf(outer)
        return listOf(
            Bounds(outer.minX, outer.minY, outer.maxX, h.minY),
            Bounds(outer.minX, h.maxY, outer.maxX, outer.maxY),
            Bounds(outer.minX, h.minY, h.minX, h.maxY),
            Bounds(h.maxX, h.minY, outer.maxX, h.maxY),
        )
    }

    /**
     * Luce artificiale: `position` in cm; `direction` non nulla = faretto che punta lì; `lumens` flusso
     * luminoso tipico della lampada; `warm` luce calda (lampadine) o neutra (neon, LED).
     */
    data class SceneLight(
        val position: Vec3, val direction: Vec3?, val lumens: Double, val warm: Boolean,
        /** Ingombro in pianta della stanza della lampada: la luce resta lì e non passa i muri (null = ovunque). */
        val room: Bounds? = null,
        /** Impianto da cui viene la luce (per aggiornarne solo la direzione mentre si orienta), se c'è. */
        val fixtureId: Long? = null,
        /** Forma della luce attorno all'asse ([beamAxis]), come nei profili IES dei produttori. */
        val profile: LightProfile = LightProfile.Uniform,
        /** Asse della lampada senza [direction]: in basso, o verso la stanza per quelle a parete. */
        val axis: Vec3 = Vec3(0.0, -1.0, 0.0),
        /**
         * Luce arrivata da una stanza vicina attraverso una porta o un passaggio ([LightSpill]): serve ai renderer che tengono la
         * luce di ogni lampada nella sua stanza; chi la lascia passare dai muri (Filament sul telefono) non la aggiunge.
         */
        val spill: Boolean = false,
        /** Rettangoli della stanza ([LightZones]): la luce resta lì dentro anche in una stanza a L. null = tutto [room]. */
        val zones: List<Bounds>? = null,
    ) {
        /** Asse attorno a cui si misura [profile]: dove punta il faretto, altrimenti [axis]. */
        val beamAxis: Vec3 get() = direction ?: axis
    }

    /** Stessa scena con altre luci: serve ad aggiornare solo le luci, senza ricostruire la geometria. */
    fun withLights(newLights: List<SceneLight>) = Scene3D(opaque, transparent, picks, obstacles, bounds, furniture, textured, newLights, windows, roofs, groundLevel, excavation, rooms)

    /**
     * Apertura vetrata: centro in cm, normale verso l'interno della stanza, misure in cm. `zones`: rettangoli della stanza in
     * pianta ([LightZones], vuoto se fuori da ogni stanza), dove resta la luce del sole che entra e rimbalza; `surface`: cm² di
     * pavimento, soffitto e pareti della stanza, su cui quella luce si sparpaglia; `floor` e `ceiling`: quote (cm) del suo pavimento
     * e del suo soffitto.
     */
    data class SceneWindow(
        val center: Vec3, val inward: Vec3, val width: Double, val height: Double,
        val zones: List<Bounds> = emptyList(), val surface: Double = 0.0, val floor: Double = 0.0, val ceiling: Double = 0.0,
    )
    /** Stanza: rettangoli in pianta ([LightZones]) e quote (cm) del pavimento e del soffitto. */
    data class SceneRoom(val zones: List<Bounds>, val floor: Double, val ceiling: Double)
    class PickTri(val a: Vec3, val b: Vec3, val c: Vec3, val pick: Pick)

    /**
     * Arredo nella scena: il modello `model` adattato a `width` × `height` × `depth` (cm), con la base al
     * centro in `base`, ruotato di `rotation` gradi come in pianta.
     */
    data class PlacedFurniture(
        val id: Long,
        val model: String,
        val base: Vec3,
        val rotation: Double,
        val width: Double,
        val depth: Double,
        val height: Double,
        val selected: Boolean,
        /** Ribaltato sinistra ↔ destra (es. divano angolare con la penisola dall'altra parte). */
        val mirrored: Boolean = false,
    )

    /** Primo oggetto colpito dal raggio (origine, direzione), oppure null. */
    fun hit(origin: Vec3, dir: Vec3): Hit? {
        var best: Hit? = null
        var bestT = Double.MAX_VALUE
        for (t in picks) {
            val d = rayTriangle(origin, dir, t.a, t.b, t.c) ?: continue
            if (d < bestT) {
                bestT = d
                var n = ((t.b - t.a) cross (t.c - t.a)).normalized()
                if ((n dot dir) > 0) n = n * -1.0
                best = Hit(t.pick, origin + dir * d, n)
            }
        }
        return best
    }

    /** Una persona (cerchio di raggio `radius`) in `p` urterebbe un muro? */
    fun blocked(p: Vec2, radius: Double): Boolean =
        obstacles.any { Polygon.distanceToSegment(p, it.a, it.b) < radius + Room.WALL_THICKNESS / 2 }

    companion object {
        const val FLOATS_PER_VERTEX = 10
        /** Altezza degli occhi quando si cammina (cm). */
        const val EYE_HEIGHT = 160.0
        /** Plafoniera a parete: larghezza, altezza e sporgenza dal muro (cm). */
        const val WALL_LIGHT_WIDTH = 30.0
        const val WALL_LIGHT_HEIGHT = 12.0
        const val WALL_LIGHT_DEPTH = 8.0
        /** Faretto a parete: lato della scatola e sporgenza dal muro (cm). */
        const val WALL_SPOT_SIZE = 8.0
        const val WALL_SPOT_DEPTH = 6.0
        /** Striscia LED a parete: altezza e sporgenza dal muro (cm), flusso totale (lumen) e distanza tra i punti luce (cm). */
        const val WALL_LED_HEIGHT = 2.0
        const val WALL_LED_DEPTH = 1.5
        const val WALL_LED_LUMENS = 1000.0
        const val LED_LIGHT_SPACING = 80.0
        /**
         * Punti luce al massimo per una striscia LED o un neon (lunga più di 50 m si dividono il flusso in meno punti): con una
         * lunghezza sbagliata (troppi zeri, file rovinato) erano centinaia di migliaia di luci e il 3D si fermava.
         */
        const val MAX_STRIP_LIGHTS = 64

        private fun rayTriangle(o: Vec3, d: Vec3, a: Vec3, b: Vec3, c: Vec3): Double? {
            val e1 = b - a
            val e2 = c - a
            val p = d cross e2
            val det = e1 dot p
            if (abs(det) < 1e-9) return null
            val inv = 1 / det
            val s = o - a
            val u = (s dot p) * inv
            if (u < 0 || u > 1) return null
            val q = s cross e1
            val v = (d dot q) * inv
            if (v < 0 || u + v > 1) return null
            val t = (e2 dot q) * inv
            return if (t > 1e-3) t else null
        }

        /**
         * Scena del piano `plan` (a quota 0) con sopra le sue scale. `below`: i piani di sotto, dal più basso,
         * disegnati sotto di lui (non si toccano e non bloccano chi cammina). `levelHeight`: interpiano del
         * piano, cioè l'altezza delle sue scale.
         */
        fun build(
            plan: FloorPlan,
            focusedRoomId: Long?,
            selected: Pick?,
            ceilings: Boolean,
            levelHeight: Double = Floor.DEFAULT_LEVEL_HEIGHT,
            below: List<Floor> = emptyList(),
            /** Piano di sopra (camminando): si vede dal vano scala, chiuso in alto dal suo solaio. */
            above: Floor? = null,
            /**
             * Quota del piano terra rispetto a questo piano, se l'edificio ha interrati ([Scene3D.groundLevel]): più in alto di 0 =
             * questo piano è un interrato, e la casa si vede in fondo a uno scavo ([Scene3D.excavation]).
             */
            groundLevel: Double? = null,
            /** Muro selezionato dal lato di fuori: si evidenzia la facciata invece del lato verso la stanza. */
            selectedExterior: Boolean = false,
        ): Scene3D {
            // Quote dei pavimenti: il piano corrente a 0, quelli di sotto più in basso.
            val levels = mutableListOf<Level>()
            var base = 0.0
            for (f in below.asReversed()) {
                base -= f.levelHeight
                levels.add(0, Level(f.plan, base, f.levelHeight, current = false))
            }
            levels += Level(plan, 0.0, levelHeight, current = true)
            if (above != null) levels += Level(above.plan, levelHeight, above.levelHeight, current = false)
            return SceneBuilder(levels, focusedRoomId, selected, ceilings, groundLevel, selectedExterior).build()
        }
    }
}

/** Un piano nella scena: la sua pianta, la quota del pavimento e l'interpiano (altezza delle scale). */
internal class Level(source: FloorPlan, val base: Double, val levelHeight: Double, val current: Boolean) {
    /** Muri curvi srotolati in pezzi dritti: il 3D si costruisce su questi. */
    val expanded = source.expandCurves()
    /** Con gli angoli quasi sovrapposti di stanze diverse saldati ([CornerWeld]). */
    val plan: FloorPlan = CornerWeld.weld(expanded.plan)
}

/** Array di float che cresce da solo. */
private class FloatList {
    var data = FloatArray(4096)
    var size = 0
    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    fun toArray() = data.copyOf(size)
}

/** Montanti al massimo in una ringhiera di balcone o attorno a un vano scala (con 12 cm di passo: 240 m). */
private const val MAX_RAILING_POSTS = 2000

/**
 * Punti al massimo su un lato del vuoto di una scala ([SceneBuilder.holeEdgeRuns]): uno per centimetro fino a 20 m, poi più radi.
 * Con una scala larga chilometri (misura sbagliata) erano milioni di punti, ognuno provato su tutti i pezzi, e il 3D si fermava.
 */
private const val MAX_EDGE_SAMPLES = 2000

/** Pavimenti più grandi di così (cm², 100 × 100 m) senza fughe disegnate; lo stesso per i rivestimenti delle pareti. */
private const val MAX_PATTERN_AREA = 1e8

/**
 * Facce al massimo per i dettagli di una scena (fughe del pavimento, pezzi dei rivestimenti, montanti delle ringhiere), circa
 * 70 MB: una casa vera ne usa al più qualche decina di migliaia (una facciata in mattoni di 15 × 7 m ne ha 6.500). Con tante
 * misure sbagliate insieme (file rovinato) erano milioni e il 3D finiva la memoria; oltre questo numero non se ne disegnano altri.
 */
private const val MAX_DETAILS = 300_000

private class SceneBuilder(
    val levels: List<Level>,
    val focusedRoomId: Long?,
    val selected: Pick?,
    val ceilings: Boolean,
    val groundLevel: Double? = null,
    val selectedExterior: Boolean = false,
) {
    private val opaque = FloatList()
    private val transparent = FloatList()
    private val picks = mutableListOf<Scene3D.PickTri>()
    private val placed = mutableListOf<Scene3D.PlacedFurniture>()
    private val textured = HashMap<String, FloatList>()
    private val lights = mutableListOf<Scene3D.SceneLight>()
    private val windows = mutableListOf<Scene3D.SceneWindow>()
    private val rooms = mutableListOf<Scene3D.SceneRoom>()
    private val roofs = FloatList()
    private var details = 0

    /** Si può disegnare un altro dettaglio di `faces` facce (vedi [MAX_DETAILS]). */
    private fun detail(faces: Int = 1): Boolean {
        if (details + faces > MAX_DETAILS) return false
        details += faces
        return true
    }

    /** Triangolo con un materiale fotografico: `uv` sono le coordinate della foto nei tre vertici. */
    private fun texTri(material: String, a: Vec3, b: Vec3, c: Vec3, ua: Vec2, ub: Vec2, uc: Vec2, normal: Vec3) {
        val face = (b - a) cross (c - a)
        val flip = (face dot normal) < 0
        val out = textured.getOrPut(material) { FloatList() }
        val n = normal.normalized()
        for ((v, uv) in if (flip) listOf(a to ua, c to uc, b to ub) else listOf(a to ua, b to ub, c to uc)) {
            out.add(v.x.toFloat()); out.add(v.y.toFloat()); out.add(v.z.toFloat())
            out.add(n.x.toFloat()); out.add(n.y.toFloat()); out.add(n.z.toFloat())
            out.add(uv.x.toFloat()); out.add(uv.y.toFloat())
        }
    }

    /**
     * Foto del materiale `m` sul rettangolo verticale da `a` a `b` (già sulla faccia), da `y0` a `y1`, rivolto verso `side`: si
     * ripete ogni [MaterialCatalog.Item.size] cm, girata e ingrandita come in `layout` (come sul pavimento). `t0` è dove comincia
     * lungo il pezzo: i pezzi della stessa anta continuano la stessa foto.
     */
    private fun skin(m: MaterialCatalog.Item, layout: MaterialLayout, a: Vec2, b: Vec2, side: Vec2, y0: Double, y1: Double, t0: Double = 0.0) {
        if (a.distanceTo(b) < 0.05 || y1 - y0 < 0.05) return
        val t1 = t0 + a.distanceTo(b)
        fun uv(t: Double, y: Double) = layout.uv(t, -y, m.size)
        val n = side.dir()
        texTri(m.id, a.at(y0), b.at(y0), b.at(y1), uv(t0, y0), uv(t1, y0), uv(t1, y1), n)
        texTri(m.id, a.at(y0), b.at(y1), a.at(y1), uv(t0, y0), uv(t1, y1), uv(t0, y1), n)
    }

    /**
     * Tappeto di materiale: una lastra sottile del colore del materiale e, sopra, la foto del materiale che
     * gira con il tappeto e si ripete ogni `size` cm (ingrandendo il tappeto il disegno non si stira).
     */
    private fun rug(f: com.sagoma.planimetria.model.Furniture, material: String, round: Boolean, pick: Pick) {
        val m = MaterialCatalog.item(material)
        val size = m?.size ?: 60.0
        val (u, v) = Furnishings.axes(f)
        // Contorno in coordinate locali (larghezza lungo u, profondità lungo v).
        val local = if (round) (0 until 40).map { k ->
            val a = k * kotlin.math.PI * 2 / 40
            Vec2(kotlin.math.cos(a) * f.width / 2, kotlin.math.sin(a) * f.depth / 2)
        } else listOf(Vec2(-f.width / 2, -f.depth / 2), Vec2(f.width / 2, -f.depth / 2), Vec2(f.width / 2, f.depth / 2), Vec2(-f.width / 2, f.depth / 2))
        val outline = local.map { f.center + u * it.x + v * it.y }
        val color = tint(Rgba.argb(m?.argb ?: 0xFFB8A58C), pick)
        val top = f.elevation + f.height
        solid(outline, f.elevation, top, color.mix(Rgba(0f, 0f, 0f), 0.15f), color, pick)
        if (m != null) {
            fun uv(p: Vec2) = Vec2(p.x / size, p.y / size)
            for (k in 1 until outline.size - 1) {
                texTri(m.id, outline[0].at(top + 0.05), outline[k].at(top + 0.05), outline[k + 1].at(top + 0.05), uv(local[0]), uv(local[k]), uv(local[k + 1]), Vec3.Up)
            }
        }
    }

    /**
     * Mobile generato alla misura (moduli della cucina, [ParametricGeometry]): ogni pezzo è un parallelepipedo che gira con il
     * mobile (specchiato: sinistra ↔ destra). Il piano in marmo o pietra ha sopra la foto del materiale, che si sposta con il
     * mobile. Per toccarlo basta la scatola dell'arredo: i pezzi non entrano tra i triangoli da colpire.
     */
    private fun generatedFurniture(f: com.sagoma.planimetria.model.Furniture, parts: List<KitchenGeometry.Part>, pick: Pick) {
        val (u, v) = Furnishings.axes(f)
        val sx = if (f.mirrored) -1.0 else 1.0
        fun at(x: Double, z: Double) = f.center + u * (x * sx) + v * z
        for (p in parts) {
            if (p.x1 - p.x0 < 1e-3 || p.y1 - p.y0 < 1e-3 || p.z1 - p.z0 < 1e-3) continue
            val outline = listOf(at(p.x0, p.z0), at(p.x1, p.z0), at(p.x1, p.z1), at(p.x0, p.z1))
            val color = tint(Rgba.argb(p.argb), pick)
            solid(outline, f.elevation + p.y0, f.elevation + p.y1, color, color, null)
            val m = p.topMaterial?.let { MaterialCatalog.item(it) } ?: continue
            val y = f.elevation + p.y1 + 0.05
            fun uv(x: Double, z: Double) = Vec2(x / m.size, z / m.size)
            texTri(m.id, outline[0].at(y), outline[1].at(y), outline[2].at(y), uv(p.x0, p.z0), uv(p.x1, p.z0), uv(p.x1, p.z1), Vec3.Up)
            texTri(m.id, outline[0].at(y), outline[2].at(y), outline[3].at(y), uv(p.x0, p.z0), uv(p.x1, p.z1), uv(p.x0, p.z1), Vec3.Up)
        }
    }

    /**
     * Oggetto fornito da un professionista (la cucina del negozio): i suoi triangoli, stirati sulle misure dell'arredo, girati e
     * specchiati con lui. Se l'oggetto non c'è più resta la scatola grigia dell'ingombro. Come per i mobili generati, il tocco
     * passa dalla scatola dell'arredo.
     */
    private fun suppliedFurniture(f: com.sagoma.planimetria.model.Furniture, obj: com.sagoma.planimetria.model.SuppliedObject?, pick: Pick) {
        if (obj == null) {
            val grey = tint(Rgba.argb(0xFFC9C6C0), pick)
            solid(Furnishings.outline(f), f.elevation, f.elevation + f.height, grey, grey, null)
            return
        }
        val (u, v) = Furnishings.axes(f)
        val sx = (if (f.mirrored) -1.0 else 1.0) * f.width / obj.width.coerceAtLeast(0.1) / 10
        val sy = f.height / obj.height.coerceAtLeast(0.1) / 10
        val sz = f.depth / obj.depth.coerceAtLeast(0.1) / 10
        for (part in obj.parts) {
            val color = tint(Rgba.argb(part.argb), pick)
            val vs = part.vertices
            fun at(i: Int): Vec3 = (f.center + u * (vs[3 * i] * sx) + v * (vs[3 * i + 2] * sz)).at(f.elevation + vs[3 * i + 1] * sy)
            val ts = part.triangles
            var k = 0
            while (k + 2 < ts.size) {
                val a = at(ts[k]); val b = at(ts[k + 1]); val c = at(ts[k + 2])
                k += 3
                val n = (b - a) cross (c - a)
                if (n.length < 1e-9) continue
                tri(a, b, c, n, color, null)
            }
        }
    }

    /**
     * Rettangolo con un materiale sulla faccia di un muro (stesse coordinate di [wallFace]). Con la posa a pezzi i pezzi
     * partono da terra con una fila intera, dal centro del muro (lungo `wallLength`) o dal suo inizio; `seed` cambia la
     * scelta dei pezzi della foto da un muro all'altro.
     */
    private fun texturedWallFace(
        material: String, layout: MaterialLayout,
        s: Vec2, u: Vec2, side: Vec2, dist: Double, t0: Double, t1: Double, y0: Double, y1: Double,
        wallLength: Double, seed: Int,
        /** Bordo basso e alto a `t1`, se il riquadro è inclinato (sopra un arco, sotto un oblò, sotto un soffitto in pendenza). */
        y0End: Double = y0, y1End: Double = y1,
    ) {
        if (t1 - t0 < 0.05 || (y1 - y0 < 0.05 && y1End - y0End < 0.05)) return
        val size = MaterialCatalog.item(material)?.size ?: 100.0
        val off = side * (dist + 0.1)
        val n = side.dir()
        /** Punto della faccia a `t` lungo il muro e `-q.y` d'altezza (il riquadro della posa ha y verso il basso, come le foto). */
        fun at(q: Vec2) = (s + u * q.x + off).at(-q.y)
        // Riquadro tra i due bordi (se a un capo il bordo alto scende sotto quello basso, lì non c'è niente da coprire).
        var region = listOf(Vec2(t0, -max(y1, y1End)), Vec2(t1, -max(y1, y1End)), Vec2(t1, -min(y0, y0End)), Vec2(t0, -min(y0, y0End)))
        region = clipAbove(region) { q -> -q.y - (y0 + (y0End - y0) * (q.x - t0) / (t1 - t0)) }
        region = clipAbove(region) { q -> (y1 + (y1End - y1) * (q.x - t0) / (t1 - t0)) + q.y }
        if (region.size < 3 || abs(Polygon.signedArea(region)) < 0.01) return
        val laying = layout.laying?.takeIf { MaterialPieces.canLay(material) }?.let { LayingPieces.normalized(it) }
        if (laying != null) {
            val rect = region
            // La fuga di ogni pezzo sta dal lato verso terra: spostando la partenza di una fuga la prima fila poggia a terra.
            val anchor = Vec2(if (laying.start == LayingStart.Centro) wallLength / 2 else 0.0, laying.joint)
            val pieces = LayingPieces.cover(laying, layout.rotation, anchor, rect)
            if (pieces.isNotEmpty()) {
                for (piece in pieces) {
                    val q = Polygon.clipConvex(rect, piece.outline)
                    if (q.size < 3) continue
                    val window = LayingPieces.window(material, size, laying, piece, seed)
                    fun uv(v: Vec2) = window.uv(piece.local(v))
                    for (k in 1 until q.size - 1) texTri(material, at(q[0]), at(q[k]), at(q[k + 1]), uv(q[0]), uv(q[k]), uv(q[k + 1]), n)
                }
                return
            }
        }
        // La foto si ripete ogni `size` cm, dritta (v cresce verso il basso come nelle immagini), girata e ingrandita come scelto.
        fun uv(q: Vec2) = layout.uv(q.x, q.y, size)
        for (k in 1 until region.size - 1) texTri(material, at(region[0]), at(region[k]), at(region[k + 1]), uv(region[0]), uv(region[k]), uv(region[k + 1]), n)
    }

    /** Solido invisibile (solo per il tocco) sul contorno `poly`, da `y0` a `y1`. */
    private fun pickBox(poly: List<Vec2>, y0: Double, y1: Double, pick: Pick) {
        val top = poly.map { it.at(y1) }
        for (k in 1 until poly.size - 1) picks += Scene3D.PickTri(top[0], top[k], top[k + 1], pick)
        for (k in poly.indices) {
            val a = poly[k]
            val b = poly[(k + 1) % poly.size]
            picks += Scene3D.PickTri(a.at(y0), b.at(y0), b.at(y1), pick)
            picks += Scene3D.PickTri(a.at(y0), b.at(y1), a.at(y1), pick)
        }
    }
    private val obstacles = mutableListOf<Obstacle>()
    /** Mezzo spessore del muro che si sta costruendo (ogni muro ha il suo spessore). */
    private var half = Room.WALL_THICKNESS / 2

    // Piano in costruzione: pianta, quota del pavimento, se è quello che si modifica (solo lui si tocca).
    private var plan = FloorPlan()
    private var expanded = ExpandedPlan(FloorPlan(), emptyMap())
    /** Ingombro di ogni stanza di [plan] (stesso ordine): scarta subito le stanze lontane dal muro che si disegna. */
    private var roomBounds = emptyList<Bounds>()
    /** Aperture di ogni stanza di [plan] (stesso ordine) con i loro estremi sul muro. */
    private var openingSpans = emptyList<List<Pair<Opening, Pair<Vec2, Vec2>>>>()
    private var base = 0.0
    private var current = true
    /** Ingombro delle scale del piano di sotto: il vuoto nel pavimento di questo piano. */
    private var holes: List<List<Vec2>> = emptyList()
    /** Vuoto delle scale di questo piano nel soffitto (e nel solaio sopra). */
    private var holesAbove: List<List<Vec2>> = emptyList()
    /** Interpiano del piano in costruzione: il pavimento di sopra sta a questa quota. */
    private var levelHeight = Floor.DEFAULT_LEVEL_HEIGHT
    /** Base dei muri: sotto zero ai piani con un piano di sotto (lo spessore del solaio). */
    private var wallBottom = 0.0

    /** Punto della pianta all'altezza `h` sopra il pavimento del piano in costruzione. */
    private fun Vec2.at(h: Double) = Vec3(x, h + base, y)
    /** Direzione orizzontale della pianta (per le normali). */
    private fun Vec2.dir() = Vec3(x, 0.0, y)

    private val railColor = Rgba(0.30f, 0.32f, 0.34f)
    private val stairTop = Rgba(0.80f, 0.68f, 0.54f)
    private val stairSide = Rgba(0.93f, 0.91f, 0.88f)

    // Colori.
    private val wallSide = Rgba(0.94f, 0.93f, 0.91f)
    private val wallTop = Rgba(0.24f, 0.26f, 0.28f)
    private val selectedTint = Rgba(0.10f, 0.44f, 0.76f)
    private val frame = Rgba(0.97f, 0.97f, 0.97f)
    /** Vetro: trasparenza 0,35. Nel 3D del computer i trasparenti più coprenti sono un velo (zanzariera, vetromattone; vedi PbrShaders). */
    private val glass = Rgba(0.55f, 0.78f, 0.92f, 0.35f)

    /** Vetro satinato delle porte: un velo bianco (tra 0,36 e 0,5 sul computer non lascia passare la luce del cielo). */
    private val satinGlass = Rgba(0.88f, 0.92f, 0.93f, 0.48f)
    /** Vetro satinato scelto per finestre e porte ([Opening.frosted]): oltre 0,5, sul computer smerigliato che si illumina col cielo. */
    private val frostedPane = Rgba(0.90f, 0.93f, 0.94f, 0.85f)

    /** Vetro di porte e finestre: trasparente, o satinato se scelto. */
    private fun glassOf(o: Opening, pick: Pick): Rgba = (if (o.hasFrostedGlass) frostedPane else glass).let { tint(it, pick).copy(a = it.a) }
    private val wood = Rgba(0.62f, 0.44f, 0.29f)
    private val ceilingColor = Rgba(0.97f, 0.97f, 0.96f)
    /** Tetto piano sopra i piani di sotto (dove il piano di sopra non li copre): guaina grigio chiaro. */
    private val slabTop = Rgba(0.72f, 0.71f, 0.69f)

    // Terreno tagliato attorno a un interrato ([excavate]): terra scura in superficie, argilla sotto, ghiaia sul fondo.
    private val Topsoil = Rgba(0.33f, 0.25f, 0.17f)
    private val Clay = Rgba(0.60f, 0.47f, 0.33f)
    private val Gravel = Rgba(0.55f, 0.52f, 0.47f)
    /** Distanza dello scavo dal filo esterno della casa e spessore della terra scura in superficie (cm). */
    private val EXCAVATION_MARGIN = 80.0
    private val TOPSOIL_DEPTH = 40.0
    /** Spessore del solaio visto di lato (fascia sotto il pavimento di sopra, bordi dei vani scala): cemento, più scuro dell'intonaco. */
    private val slabEdge = Rgba(0.60f, 0.59f, 0.57f)
    private val lightColor = Rgba(1f, 0.93f, 0.62f)
    private val skirting = Rgba(0.80f, 0.78f, 0.74f)
    private val SKIRTING = 8.0
    /** Fettine di muro sopra un varco ad arco (ognuna con il bordo inferiore inclinato: curva continua). */
    private val ARCH_SLICES = 32
    private val MORTAR = Rgba(0.80f, 0.78f, 0.74f)
    /** Rivestimenti con il listello in cima e quelli che vanno fino a terra senza battiscopa. */
    private val TRIMMED = setOf(Covering.Tiles, Covering.Metro, Covering.Wainscot, Covering.Boards, Covering.Material)
    private val NO_SKIRTING = setOf(Covering.Tiles, Covering.Metro, Covering.Brick, Covering.Stone)

    private fun tint(c: Rgba, pick: Pick) = if (current && pick == selected) c.mix(selectedTint, 0.55f) else c

    fun build(): Scene3D {
        var previous: Level? = null
        for ((index, level) in levels.withIndex()) {
            plan = level.plan
            roomBounds = plan.rooms.map { if (it.points.isEmpty()) Bounds(0.0, 0.0, -1.0, -1.0) else Polygon.bounds(it.points) }
            openingSpans = plan.rooms.map { r -> r.openings.filter { it.wallIndex < r.wallCount }.map { it to Openings.span(r, it) } }
            expanded = level.expanded
            base = level.base
            current = level.current
            holes = previous?.let { p -> stairWells(p, plan) }.orEmpty()
            // Vuoto nel soffitto sopra le scale di questo piano che salgono (lo stesso del pavimento di sopra).
            holesAbove = stairWells(level, levels.getOrNull(index + 1)?.plan)
            levelHeight = level.levelHeight
            // Piano di sopra (camminando): si guarda solo dal basso, dal vano scala. Il suo pavimento non si
            // disegna (da sotto lo copre il soffitto, e con l'interpiano uguale all'altezza del soffitto starebbe
            // alla stessa quota e sfarfallerebbe) e i suoi muri scendono nel solaio solo fino a 1 cm sopra il soffitto di sotto
            // ([bottomOver]): da fuori casa continuano i muri di sotto, senza fessura.
            // Tutto il piano di sopra 1 cm più in alto (invisibile): muri, colonne, scale e arredi non toccano il
            // piano del soffitto, altrimenti le loro facce di sotto sfarfallerebbero con lui.
            val aboveCurrent = level.base > 0
            if (aboveCurrent) base = level.base + 1.0
            wallBottom = when {
                aboveCurrent -> 0.0
                previous != null -> -Floor.SLAB
                else -> 0.0
            }
            // Stanze (al chiuso) del piano di sotto: lì sotto c'è il loro soffitto. Dove invece non c'è niente sotto
            // (balconi, terrazze, parti che sporgono) il pavimento ha il solaio visibile da sotto e di lato.
            val coveredBelow = previous?.plan?.rooms?.filter { !it.outdoor }?.flatMap { r ->
                Triangulation.triangulate(r.points).map { (a, b, c) -> listOf(r.points[a], r.points[b], r.points[c]) }
            }.orEmpty()
            for (room in plan.rooms) {
                if (!aboveCurrent) floor(room)
                else {
                    // Piano di sopra: il pavimento solo dove sotto non c'è un soffitto (balconi, sporgenze).
                    val saved = holes
                    holes = holes + coveredBelow
                    floor(room)
                    holes = saved
                }
                if (previous != null) underside(room, coveredBelow)
                if (ceilings && current) ceiling(room)
                // Soffitti per la luce: del piano corrente e di quello di sopra (camminando), che è dentro casa.
                if (current || base > 0) roof(room)
                // Piani di sotto: chiusi in alto dal solaio (dove il piano di sopra non li copre si vede il tetto piano), anche
                // le stanze con una scala che sale, tranne il suo vano: da lì (e dal vano nel pavimento di sopra) si vede la scala.
                if (!current) { ceiling(room); coverSlab(room) }
                for (i in 0 until room.wallCount) {
                    if (aboveCurrent && previous != null) wallBottom = bottomOver(previous, room.wallStart(i), room.wallEnd(i))
                    wall(room, i)
                }
                if (aboveCurrent) wallBottom = 0.0
                for (o in room.openings) if (o.wallIndex < room.wallCount) openingModel(room, o)
                for (f in room.fixtures) fixture(room, f)
            }
            // Vuoto della scala nel pavimento: si vede lo spessore del solaio (non nel piano di sopra visto da sotto, senza pavimento).
            if (!aboveCurrent && previous != null && holes.isNotEmpty()) floorHoleSides(previous)
            // Camminando si vede il vano scala nel soffitto: i suoi bordi nello spessore del solaio.
            if (ceilings && current) wellSides(levels.getOrNull(index + 1))
            for (s in plan.stairs) stair(s, level.levelHeight)
            // Colonne dal pavimento al soffitto e travi appese al soffitto.
            val plaster = Rgba(0.91f, 0.90f, 0.87f)
            for (c in plan.columns) {
                val pick = Pick.Column(c.id)
                val outline = Structure.outline(c)
                solid(outline, 0.0, Structure.columnHeight(plan, c, level.levelHeight), tint(plaster, pick), tint(plaster, pick), pick)
                if (current) for (k in outline.indices) obstacles += Obstacle(outline[k], outline[(k + 1) % outline.size])
            }
            // Muri singoli: dal pavimento (ai piani di sopra dal solaio) fino alla loro altezza, bordo scuro come i muri.
            for (w in plan.freeWalls) {
                if (w.length < 1.0) continue
                val pick = Pick.FreeWall(w.id)
                val h = Structure.freeWallHeight(plan, w, level.levelHeight)
                // Muro curvo: pezzi dritti lungo l'arco, ognuno con le sue aperture.
                for (piece in w.pieces()) {
                    if (piece.length < 0.01) continue
                    freeWall(piece, h, pick)
                    // Si passa solo dalle porte e dai passaggi: i tratti pieni e le finestre fermano chi cammina.
                    if (current && h > 40) {
                        val u = (piece.end - piece.start) / piece.length
                        for (r in Structure.solidRanges(piece)) obstacles += Obstacle(piece.start + u * r.start, piece.start + u * r.endInclusive)
                        for (o in piece.openings) if (o.kind.glazed && o.sillHeight > 0) Openings.span(piece.host, o).let { (a, b) -> obstacles += Obstacle(a, b) }
                    }
                }
            }
            for (b in plan.beams) {
                val pick = Pick.Beam(b.id)
                val top = Structure.ceilingAt(plan, b.mid, level.levelHeight)
                solid(Structure.outline(b), top - b.depth, top, tint(plaster.mix(Rgba(0f, 0f, 0f), 0.05f), pick), tint(plaster, pick), pick)
            }
            // Arredi: il modello lo disegna il renderer; qui la scatola invisibile per toccarli e, se alti,
            // l'ingombro che blocca chi cammina.
            for (f in plan.furniture) {
                val pick = Pick.Furniture(f.id)
                val rug = FurnitureCatalog.rugOf(f.model)
                val generated = GeneratedGeometry.parts(f.model, f.width, f.depth, f.height)
                if (rug != null) rug(f, rug.first, rug.second, pick)
                else if (generated != null) generatedFurniture(f, generated, pick)
                else if (com.sagoma.planimetria.model.SuppliedObjects.isModel(f.model)) suppliedFurniture(f, plan.suppliedFor(f.model), pick)
                else placed += Scene3D.PlacedFurniture(f.id, f.model, f.center.at(f.elevation), f.rotation, f.width, f.depth, f.height, current && pick == selected, f.mirrored)
                // Le lampade del catalogo fanno luce: dal paralume (in alto) o, se appese, da sotto.
                val item = FurnitureCatalog.item(f.model)
                if (current && item?.symbolKind == com.sagoma.planimetria.model.FurnitureSymbol.Lamp) {
                    val y = if (item.ceiling) f.elevation + f.height * 0.3 else f.elevation + f.height * 0.85
                    val lampRoom = plan.rooms.lastOrNull { Polygon.contains(it.points, f.center) }?.let { Polygon.bounds(it.points) }
                    lights += Scene3D.SceneLight(f.center.at(y), null, if (item.ceiling) 2000.0 else 600.0, warm = true, room = lampRoom,
                        profile = if (item.ceiling) LightProfile.Pendant else LightProfile.Shade)
                }
                if (!current) continue
                val outline = Furnishings.outline(f)
                pickBox(outline, f.elevation, f.elevation + f.height, pick)
                if (f.elevation < 60 && f.elevation + f.height > 40) for (k in outline.indices) obstacles += Obstacle(outline[k], outline[(k + 1) % outline.size])
            }
            // Ringhiere attorno ai vani delle scale che salgono dal piano di sotto: solo dentro le stanze di questo piano (fuori c'è il tetto).
            previous?.let { p ->
                val walls = Structure.wallLines(plan)
                for (s in p.plan.stairs) if (s.wellRailing != StairRailing.None) {
                    for ((a, b) in insideRooms(Stairs.wellRailingRunsAlong(s, p.levelHeight, walls))) {
                        wellRail(a, b, s.wellRailing, Pick.StairWell(s.id))
                        if (current) obstacles += Obstacle(a, b)
                    }
                }
            }
            // Luce delle lampade che passa dalle porte nelle stanze vicine (solo il piano corrente ha le luci).
            if (current) {
                // Solo stanze vere: una stanza senza punti (file rovinato) non ha zone e farebbe fermare il 3D.
                // Le stanze unite senza muro (soggiorno, cucina e ingresso aperti) sono un ambiente solo: la luce delle finestre si
                // sparge in tutto l'ambiente e l'esposizione è la stessa, così pareti e pavimenti non cambiano luce dove c'era il
                // muro. Se l'ambiente ha troppi rettangoli per il renderer, ogni stanza resta da sola come prima.
                val light = LinkedHashMap<Long, Scene3D.SceneRoom>()
                val surfaces = HashMap<Long, Double>()
                val indoor = plan.rooms.filter { !it.outdoor && it.points.size >= 3 }
                for (space in LightZones.spaces(plan)) {
                    val zones = if (space.size > 1) LightZones.merged(space, indoor - space.toSet()) else null
                    if (zones == null) {
                        for (room in space) {
                            val perimeter = room.points.indices.sumOf { room.points[it].distanceTo(room.points[(it + 1) % room.points.size]) }
                            light[room.id] = Scene3D.SceneRoom(LightZones.of(room.points), base, base + room.ceilingHeight)
                            surfaces[room.id] = 2 * Polygon.area(room.points) + perimeter * room.ceilingHeight
                        }
                        continue
                    }
                    // Pavimento, soffitto e pareti di tutto l'ambiente: senza i muri tolti.
                    val surface = space.sumOf { r ->
                        2 * Polygon.area(r.points) + (0 until r.wallCount).filter { !r.isRemoved(it) }.sumOf { r.wallLength(it) } * r.ceilingHeight
                    }
                    val shared = Scene3D.SceneRoom(zones, base, base + space.maxOf { it.ceilingHeight })
                    for (room in space) { light[room.id] = shared; surfaces[room.id] = surface }
                }
                rooms += light.values.distinct()
                // Finestre: la stanza (o l'ambiente aperto) in cui entra la loro luce (appena dentro, verso l'interno).
                for (k in windows.indices) {
                    val w = windows[k]
                    val inside = Vec2(w.center.x + w.inward.x * 30, w.center.z + w.inward.z * 30)
                    val room = indoor.firstOrNull { Polygon.contains(it.points, inside) } ?: continue
                    val r = light[room.id] ?: continue
                    windows[k] = w.copy(zones = r.zones, surface = surfaces.getValue(room.id), floor = r.floor, ceiling = r.ceiling)
                }
                lights += LightSpill.through(plan, lights.toList())
                // Stanze a L o a U: la luce resta nei rettangoli della stanza, non in tutto il suo riquadro.
                // Una stanza senza punti (file rovinato) non ha riquadro: si salta, altrimenti il 3D non si apre.
                val zones = plan.rooms.filter { it.points.size >= 3 }.associate { Polygon.bounds(it.points) to LightZones.of(it.points) }
                for (k in lights.indices) {
                    val l = lights[k]
                    val z = l.room?.let { zones[it] } ?: continue
                    if (l.zones == null && z.size > 1) lights[k] = l.copy(zones = z)
                }
            }
            previous = level
        }
        // Piano interrato: il terreno sta più in alto, la casa si vede in fondo a uno scavo.
        val excavation = groundLevel?.takeIf { it > 1.0 }?.let { excavate(it) }
        // Inquadratura sul piano che si sta guardando (non su quello di sopra, che si vede solo dal vano scala).
        val top = levels.first { it.current }
        return Scene3D(
            opaque.toArray(), transparent.toArray(), picks, obstacles, Openings.planBounds(top.plan, top.levelHeight), placed,
            textured.mapValues { it.value.toArray() },
            lights, windows, roofs.toArray(), groundLevel, excavation, rooms,
        )
    }

    /**
     * Scavo attorno a un piano interrato: il terreno tagliato come in una sezione (terra scura in alto, argilla sotto) dalla
     * quota del terreno `ground` fino sotto il piano più basso, poco oltre i muri, e la ghiaia sul fondo. Il prato (lo disegna
     * il renderer) resta alla quota del terreno attorno al buco. Restituisce il buco in pianta.
     */
    private fun excavate(ground: Double): Bounds? {
        val pts = levels.filter { it.base <= 0.0 }.flatMap { l -> l.plan.rooms.filterNot { it.outdoor }.flatMap { it.points } }
        if (pts.isEmpty()) return null
        val b = Polygon.bounds(pts)
        val m = EXCAVATION_MARGIN
        val hole = Bounds(b.minX - m, b.minY - m, b.maxX + m, b.maxY + m)
        val bottom = levels.minOf { it.base } - 2.0
        val topsoil = maxOf(bottom, ground - TOPSOIL_DEPTH)
        current = false
        val c = listOf(Vec2(hole.minX, hole.minY), Vec2(hole.maxX, hole.minY), Vec2(hole.maxX, hole.maxY), Vec2(hole.minX, hole.maxY))
        val mid = Vec2((hole.minX + hole.maxX) / 2, (hole.minY + hole.maxY) / 2)
        for (k in c.indices) {
            val a = c[k]
            val e = c[(k + 1) % c.size]
            // Faccia rivolta verso la casa.
            val inward = ((mid - (a + e) * 0.5).let { Vec3(it.x, 0.0, it.y) })
            quad(a.at(bottom), e.at(bottom), e.at(topsoil), a.at(topsoil), inward, Clay, null)
            quad(a.at(topsoil), e.at(topsoil), e.at(ground), a.at(ground), inward, Topsoil, null)
        }
        quad(c[0].at(bottom), c[1].at(bottom), c[2].at(bottom), c[3].at(bottom), Vec3.Up, Gravel, null)
        return hole
    }

    /**
     * Faccia superiore del solaio sopra una stanza di un piano di sotto: il tetto piano visto dall'alto, senza il vano delle
     * scale che salgono da questo piano ([holesAbove]), da cui si vede la scala.
     */
    private fun coverSlab(room: Room) {
        val lift = Vec3(0.0, base + 2.0, 0.0)
        for ((qa, qb, qc) in Ceilings.triangles(room)) {
            val pa = qa + lift; val pb = qb + lift; val pc = qc + lift
            if (holesAbove.isEmpty()) { tri(pa, pb, pc, Vec3.Up, slabTop, null); continue }
            val n = ((pb - pa) cross (pc - pa)).normalized()
            if (n.length < 0.5) continue // triangolo degenere
            // Come per il soffitto: ogni pezzo che resta torna sul piano del triangolo (in mansarda, in pendenza).
            val flat = listOf(Vec2(pa.x, pa.z), Vec2(pb.x, pb.z), Vec2(pc.x, pc.z))
            fun yAt(p: Vec2) = if (abs(n.y) < 1e-9) pa.y else pa.y - (n.x * (p.x - pa.x) + n.z * (p.y - pa.z)) / n.y
            for (poly in Clip.subtractAll(flat, holesAbove)) for (k in 1 until poly.size - 1) {
                val a = poly[0]; val b = poly[k]; val c = poly[k + 1]
                tri(Vec3(a.x, yAt(a), a.y), Vec3(b.x, yAt(b), b.y), Vec3(c.x, yAt(c), c.y), Vec3.Up, slabTop, null)
            }
        }
    }

    /** Soffitto della stanza per la sola luce ([Scene3D.roofs]); il colore non conta. */
    private fun roof(room: Room) {
        for ((pa, pb, pc) in Ceilings.triangles(room)) {
            for (v in listOf(pa, pb, pc)) {
                roofs.add(v.x.toFloat()); roofs.add((v.y + base).toFloat()); roofs.add(v.z.toFloat())
                roofs.add(0f); roofs.add(-1f); roofs.add(0f)
                roofs.add(1f); roofs.add(1f); roofs.add(1f); roofs.add(1f)
            }
        }
    }

    // ---------- Scale ----------

    /**
     * Scala: ogni gradino è un blocco pieno fino a terra (scala in muratura); nella chiocciola i gradini
     * sono lastre attorno al palo centrale.
     */
    private fun stair(s: Stair, rise: Double) {
        val layout = Stairs.layout(s, rise)
        val pick = Pick.Stair(s.id)
        // Gradini con il materiale fotografico dei pavimenti: il suo colore sui fronti e la foto sulle pedate.
        val photo = MaterialCatalog.item(s.treadMaterial)
        val material = Rgba.argb(photo?.argb ?: s.material.argb)
        val top = tint(material, pick)
        val face = tint(material.mix(Rgba(0f, 0f, 0f), 0.1f), pick)
        val plaster = tint(stairSide, pick)
        val metal = tint(railColor, pick)
        val spiral = s.kind == StairKind.Spiral
        val structure = if (spiral) StairStructure.Floating else s.structure
        for (st in layout.steps) {
            when (structure) {
                // In muratura: blocco pieno fino a terra, rivestito sopra e sul fronte.
                StairStructure.Masonry -> {
                    solid(st.polygon, 0.0, st.top - 3, plaster, plaster, pick)
                    solid(st.polygon, st.top - 3, st.top, face, top, pick)
                }
                // A sbalzo o a giorno: lastre sospese (i pianerottoli più spessi).
                StairStructure.Floating, StairStructure.Stringers -> {
                    val thick = if (st.landing) 12.0 else if (structure == StairStructure.Floating) 7.0 else 5.0
                    solid(st.polygon, st.top - thick, st.top, face, top, pick)
                }
            }
            if (photo != null) for (k in 1 until st.polygon.size - 1) {
                val p = st.polygon
                fun uv(q: Vec2) = Vec2(q.x / photo.size, q.y / photo.size)
                texTri(photo.id, p[0].at(st.top + 0.03), p[k].at(st.top + 0.03), p[k + 1].at(st.top + 0.03), uv(p[0]), uv(p[k]), uv(p[k + 1]), Vec3.Up)
            }
        }
        if (structure == StairStructure.Stringers) stringers(layout, metal, pick)
        if (spiral) {
            val c = s.center
            val r = Stairs.COLUMN_RADIUS
            box(c - Vec2(r, 0.0), c + Vec2(r, 0.0), r, 0.0, rise + 90, metal, pick)
        }
        if (s.railing != StairRailing.None) railing(s, layout, rise, pick)
    }

    /** Tratti dei bordi dei gradini, con il gradino a cui appartengono. */
    private class StepEdge(val a: Vec2, val b: Vec2, val step: Stairs.Step)

    /**
     * Bordi laterali dei gradini (quelli nel verso della salita): i fianchi della scala. Per ogni gradino si
     * prende la direzione della linea di salita più vicina.
     */
    private fun sideEdges(layout: Stairs.Layout): List<StepEdge> {
        val out = mutableListOf<StepEdge>()
        val segs = layout.path.zipWithNext()
        for (st in layout.steps) {
            if (st.landing || st.polygon.size != 4) continue
            val c = st.polygon.reduce { x, y -> x + y } / st.polygon.size.toDouble()
            val (pa, pb) = segs.minByOrNull { (p, q) -> Polygon.distanceToSegment(c, p, q) } ?: continue
            val dir = (pb - pa).normalized()
            for (k in st.polygon.indices) {
                val a = st.polygon[k]
                val b = st.polygon[(k + 1) % st.polygon.size]
                if (kotlin.math.abs((b - a).normalized() dot dir) > 0.7) out += StepEdge(a, b, st)
            }
        }
        return out
    }

    /** Scala a giorno: le due travi laterali (cosciali) sotto i fianchi dei gradini. */
    private fun stringers(layout: Stairs.Layout, color: Rgba, pick: Pick) {
        for (e in sideEdges(layout)) {
            val top = e.step.top - 5
            box(e.a, e.b, 2.5, top - 26, top + 1, color, pick)
        }
    }

    /**
     * Ringhiera sui lati liberi della scala: bordi dei gradini e dei pianerottoli che non toccano un altro
     * gradino né un muro, esclusi la partenza e l'arrivo. Il corrimano sale insieme alla linea di salita,
     * 90 cm sopra i gradini.
     */
    private fun railing(s: Stair, layout: Stairs.Layout, rise: Double, pick: Pick) {
        val steps = layout.steps
        val run = layout.run.coerceAtLeast(1.0)
        val segs = layout.path.zipWithNext()
        /** Quota del corrimano nel punto p: avanzamento lungo la linea di salita. */
        fun railAt(p: Vec2): Double {
            var best = Double.MAX_VALUE
            var along = 0.0
            var walked = 0.0
            for ((a, b) in segs) {
                val len = a.distanceTo(b)
                val t = if (len < 1e-9) 0.0 else (((p - a) dot (b - a)) / (len * len)).coerceIn(0.0, 1.0)
                val q = a + (b - a) * t
                val d = q.distanceTo(p)
                if (d < best) { best = d; along = walked + len * t }
                walked += len
            }
            return 90.0 + rise * (along / run).coerceIn(0.0, 1.0) * (steps.last().top / rise)
        }
        // Solo i muri vicini alla scala (quelli lontani non possono toccarne i bordi): con tante stanze era il grosso del tempo.
        val stairArea = steps.flatMap { it.polygon }.takeIf { it.isNotEmpty() }?.let(Polygon::bounds)
        val margin = half + 4
        val walls = plan.rooms.flatMap { r -> (0 until r.wallCount).map { r.wallStart(it) to r.wallEnd(it) } }.filter { (c, d) ->
            stairArea == null || min(c.x, d.x) <= stairArea.maxX + margin && max(c.x, d.x) >= stairArea.minX - margin && min(c.y, d.y) <= stairArea.maxY + margin && max(c.y, d.y) >= stairArea.minY - margin
        }
        val first = steps.first().polygon
        val last = steps.last().polygon
        fun edgesOf(poly: List<Vec2>) = poly.indices.map { poly[it] to poly[(it + 1) % poly.size] }
        // Partenza: il bordo del primo gradino più indietro nel verso della salita; arrivo: il bordo
        // dell'ultimo più avanti. (La linea di salita parte a metà del primo gradino, equidistante dai due bordi.)
        val startDir = (segs.first().second - segs.first().first).normalized()
        val endDir = (segs.last().second - segs.last().first).normalized()
        val startEdge = edgesOf(first).minByOrNull { (a, b) -> ((a + b) / 2.0) dot startDir }
        val endEdge = edgesOf(last).maxByOrNull { (a, b) -> ((a + b) / 2.0) dot endDir }
        val spiral = s.kind == StairKind.Spiral
        val runs = mutableListOf<Triple<Vec2, Vec2, Stairs.Step>>()
        for (st in steps) for ((a, b) in edgesOf(st.polygon)) {
            if (st.polygon === first && (a to b) == startEdge) continue
            if (st.polygon === last && (a to b) == endEdge) continue
            // Chiocciola: solo il bordo esterno (lontano dal palo).
            if (spiral && (a.distanceTo(s.center) < s.diameter / 2 - 1 || b.distanceTo(s.center) < s.diameter / 2 - 1)) continue
            // Il bordo si divide in pezzi da ~10 cm: restano quelli liberi.
            val len = a.distanceTo(b)
            val pieces = (len / 10.0).toInt().coerceAtLeast(1)
            var runStart: Vec2? = null
            for (k in 0 until pieces) {
                val p0 = a + (b - a) * (k.toDouble() / pieces)
                val p1 = a + (b - a) * ((k + 1).toDouble() / pieces)
                val mid = (p0 + p1) / 2.0
                val shared = steps.any { o -> o !== st && edgesOf(o.polygon).any { (c, d) -> Polygon.distanceToSegment(mid, c, d) < 0.5 } }
                val nearWall = walls.any { (c, d) -> Polygon.distanceToSegment(mid, c, d) < half + 4 }
                val free = !shared && !nearWall
                if (free && runStart == null) runStart = p0
                if (!free && runStart != null) { runs += Triple(runStart, p0, st); runStart = null }
                if (free && k == pieces - 1) { runs += Triple(runStart!!, p1, st); runStart = null }
            }
        }
        val railCol = when (s.railing) {
            StairRailing.Wood -> tint(Rgba(0.54f, 0.37f, 0.23f), pick)
            else -> tint(railColor, pick)
        }
        // Tagliata dal solaio (a scelta): niente sopra la faccia inferiore del solaio, cioè sotto lo spessore di [Floor.SLAB]
        // che sta tra il soffitto e il pavimento del piano di sopra (non a filo del pavimento, che è la faccia superiore).
        val fullCap = if (s.railingAboveFloor) Double.MAX_VALUE else rise - Floor.SLAB
        for ((a, b, st) in runs) {
            // Fuori dal vano (la scala esce dalle stanze del piano di sopra, lì c'è il soffitto): niente sopra il soffitto.
            val mid = (a + b) / 2.0
            val inside = st.polygon.reduce { p, q -> p + q } / st.polygon.size.toDouble()
            val probe = mid + (inside - mid).normalized() * 2.0
            val cap = if (holesAbove.any { Polygon.contains(it, probe) }) fullCap else min(fullCap, Structure.ceilingAt(plan, probe, rise) - 3.0)
            if (st.top >= cap) continue
            val ha = railAt(a)
            val hb = railAt(b)
            val len = a.distanceTo(b)
            when (s.railing) {
                StairRailing.None -> Unit
                StairRailing.Metal, StairRailing.Wood -> {
                    val r = if (s.railing == StairRailing.Wood) 2.0 else 0.8
                    // Montanti ogni 12 cm circa, dal gradino al corrimano.
                    val n = (len / 12.0).toInt().coerceAtLeast(1)
                    for (k in 0..n) {
                        val p = a + (b - a) * (k.toDouble() / n)
                        box(p - Vec2(r, 0.0), p + Vec2(r, 0.0), r, st.top, min(railAt(p), cap), railCol, pick)
                    }
                }
                StairRailing.Glass -> {
                    val glassColor = tint(glass, pick).copy(a = glass.a)
                    pane(a, b, st.top + 2, min(minOf(ha, hb) - 4, cap), glassColor, pick)
                }
            }
            // Corrimano: la parte sopra il taglio non c'è; se lo attraversa, finisce proprio lì.
            val w = if (s.railing == StairRailing.Wood) 3.0 else 2.0
            when {
                ha <= cap && hb <= cap -> beam(a.at(ha), b.at(hb), w, railCol, pick)
                ha > cap && hb > cap -> Unit
                else -> {
                    val t = (cap - ha) / (hb - ha)
                    val c = a + (b - a) * t
                    if (ha < hb) beam(a.at(ha), c.at(cap), w, railCol, pick) else beam(c.at(cap), b.at(hb), w, railCol, pick)
                }
            }
        }
    }

    /** Ringhiera orizzontale alta 100 cm sul bordo del vano scala, dal punto a al punto b. */
    private fun wellRail(a: Vec2, b: Vec2, type: StairRailing, pick: Pick) {
        val h = 100.0
        val len = a.distanceTo(b)
        if (len < 1e-6) return
        val u = (b - a) / len
        // Come sui balconi: su un tratto lunghissimo (scala larga chilometri, misura sbagliata) i montanti sono più radi e contano
        // come dettagli, altrimenti erano milioni e finivano la memoria.
        fun step(spacing: Double) = max(spacing, len / MAX_RAILING_POSTS)
        when (type) {
            StairRailing.None -> return
            StairRailing.Metal -> {
                var t = 0.0
                while (t <= len + 1e-6 && detail(6)) { val c = a + u * t; box(c - u * 0.8, c + u * 0.8, 0.8, 0.0, h, railColor, pick); t += step(12.0) }
                box(a, b, 1.2, 4.0, 7.0, railColor, pick)
                box(a, b, 2.0, h - 4, h, railColor, pick)
            }
            StairRailing.Glass -> {
                box(a, b, 2.5, 0.0, 4.0, railColor, pick)
                pane(a, b, 4.0, h - 5, glass, pick)
                box(a, b, 2.0, h - 5, h, railColor, pick)
            }
            StairRailing.Wood -> {
                val wood = Rgba(0.54f, 0.37f, 0.23f)
                var t = 0.0
                while (t <= len + 1e-6 && detail(6)) { val c = a + u * t; box(c - u * 2.0, c + u * 2.0, 2.0, 0.0, h - 5, wood, pick); t += step(15.0) }
                box(a, b, 3.0, h - 6, h, wood, pick)
            }
        }
    }

    /** Trave a sezione quadrata (lato 2 × `r`) tra due punti qualsiasi dello spazio: corrimano inclinati. */
    private fun beam(a: Vec3, b: Vec3, r: Double, color: Rgba, pick: Pick) {
        val d = b - a
        val len = d.length
        if (len < 1e-6) return
        val f = d * (1 / len)
        val ref = if (kotlin.math.abs(f.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3.Up
        val s1 = (f cross ref).normalized() * r
        val s2 = (s1 cross f).normalized() * r
        val offs = listOf(s1 + s2, s1 - s2, s1 * -1.0 - s2, s1 * -1.0 + s2)
        for (k in 0 until 4) {
            val o0 = offs[k]
            val o1 = offs[(k + 1) % 4]
            quad(a + o0, b + o0, b + o1, a + o1, (o0 + o1).normalized(), color, pick)
        }
        quad(a + offs[0], a + offs[1], a + offs[2], a + offs[3], f * -1.0, color, pick)
        quad(b + offs[0], b + offs[1], b + offs[2], b + offs[3], f, color, pick)
    }

    /** Prisma verticale su un poligono convesso della pianta, da `y0` a `y1`. */
    private fun solid(poly: List<Vec2>, y0: Double, y1: Double, side: Rgba, top: Rgba, pick: Pick?) {
        if (poly.size < 3 || y1 - y0 < 1e-6) return
        for (k in 1 until poly.size - 1) {
            tri(poly[0].at(y1), poly[k].at(y1), poly[k + 1].at(y1), Vec3.Up, top, pick)
            tri(poly[0].at(y0), poly[k].at(y0), poly[k + 1].at(y0), Vec3.Up * -1.0, side, pick)
        }
        val center = poly.reduce { a, b -> a + b } / poly.size.toDouble()
        for (k in poly.indices) {
            val a = poly[k]
            val b = poly[(k + 1) % poly.size]
            if (a.distanceTo(b) < 1e-6) continue
            val out = ((a + b) / 2.0 - center).dir().normalized()
            quad(a.at(y0), b.at(y0), b.at(y1), a.at(y1), out, side, pick)
        }
    }

    // ---------- Primitive ----------

    /** Triangolo con la faccia rivolta verso `normal` (l'ordine dei vertici si sistema da solo). */
    private fun tri(a: Vec3, b: Vec3, c: Vec3, normal: Vec3, color: Rgba, pick: Pick?, emissive: Boolean = false, glassy: Boolean = false) {
        val face = (b - a) cross (c - a)
        val (p, q) = if ((face dot normal) >= 0) b to c else c to b
        val out = if (glassy) transparent else opaque
        val n = if (emissive) Vec3.Zero else normal.normalized()
        for (v in listOf(a, p, q)) {
            out.add(v.x.toFloat()); out.add(v.y.toFloat()); out.add(v.z.toFloat())
            out.add(n.x.toFloat()); out.add(n.y.toFloat()); out.add(n.z.toFloat())
            out.add(color.r); out.add(color.g); out.add(color.b); out.add(color.a)
        }
        if (pick != null && current) picks += Scene3D.PickTri(a, p, q, pick)
    }

    private fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, normal: Vec3, color: Rgba, pick: Pick?, emissive: Boolean = false, glassy: Boolean = false) {
        tri(a, b, c, normal, color, pick, emissive, glassy)
        tri(a, c, d, normal, color, pick, emissive, glassy)
    }

    /**
     * Parallelepipedo con la base sul segmento p0→p1 della pianta, largo 2 × `halfWidth`, da `y0` a `y1`.
     * `top`: colore della faccia superiore.
     */
    private fun box(p0: Vec2, p1: Vec2, halfWidth: Double, y0: Double, y1: Double, color: Rgba, pick: Pick?, top: Rgba = color, emissive: Boolean = false) {
        val len = p0.distanceTo(p1)
        if (len < 1e-6 || y1 - y0 < 1e-6) return
        val u = (p1 - p0) / len
        val n = u.perp()
        val c = listOf(p0 - n * halfWidth, p1 - n * halfWidth, p1 + n * halfWidth, p0 + n * halfWidth)
        val lo = c.map { it.at(y0) }
        val hi = c.map { it.at(y1) }
        quad(hi[0], hi[1], hi[2], hi[3], Vec3.Up, top, pick, emissive)
        quad(lo[0], lo[1], lo[2], lo[3], Vec3.Up * -1.0, color, pick, emissive)
        for (k in 0 until 4) {
            val k2 = (k + 1) % 4
            val mid = (c[k] + c[k2]) / 2.0
            val center = (p0 + p1) / 2.0
            val out = (mid - center).dir().normalized()
            quad(lo[k], lo[k2], hi[k2], hi[k], out, color, pick, emissive)
        }
    }

    /**
     * Come [box], ma con la faccia superiore inclinata: alta `top0` sul lato di p0 e `top1` sul lato
     * di p1 (tratto di muro sotto un soffitto in pendenza).
     */
    private fun prism(p0: Vec2, p1: Vec2, halfWidth: Double, y0: Double, top0: Double, top1: Double, color: Rgba, pick: Pick?, top: Rgba) {
        val len = p0.distanceTo(p1)
        if (len < 1e-6 || max(top0, top1) - y0 < 1e-6) return
        val u = (p1 - p0) / len
        val n = u.perp()
        val c = listOf(p0 - n * halfWidth, p1 - n * halfWidth, p1 + n * halfWidth, p0 + n * halfWidth)
        val tops = listOf(top0, top1, top1, top0)
        val lo = c.map { it.at(y0) }
        val hi = c.mapIndexed { k, p -> p.at(max(tops[k], y0)) }
        var topN = ((hi[1] - hi[0]) cross (hi[3] - hi[0])).normalized()
        if (topN.y < 0) topN = topN * -1.0
        quad(hi[0], hi[1], hi[2], hi[3], topN, top, pick)
        quad(lo[0], lo[1], lo[2], lo[3], Vec3.Up * -1.0, color, pick)
        val center = (p0 + p1) / 2.0
        for (k in 0 until 4) {
            val k2 = (k + 1) % 4
            val out = ((c[k] + c[k2]) / 2.0 - center).dir().normalized()
            quad(lo[k], lo[k2], hi[k2], hi[k], out, color, pick)
        }
    }

    /**
     * Come [prism], ma inclinata anche sotto: la faccia inferiore va da `bottom0` (lato p0) a `bottom1` (lato p1).
     * Serve sopra i varchi ad arco: tante fettine così fanno una curva continua, senza gradini.
     */
    private fun slab(
        p0: Vec2, p1: Vec2, halfWidth: Double, bottom0: Double, bottom1: Double, top0: Double, top1: Double,
        color: Rgba, pick: Pick?, top: Rgba,
    ) {
        val len = p0.distanceTo(p1)
        if (len < 1e-6 || max(top0 - bottom0, top1 - bottom1) < 1e-6) return
        val u = (p1 - p0) / len
        val n = u.perp()
        val c = listOf(p0 - n * halfWidth, p1 - n * halfWidth, p1 + n * halfWidth, p0 + n * halfWidth)
        val bottoms = listOf(bottom0, bottom1, bottom1, bottom0)
        val tops = listOf(top0, top1, top1, top0)
        val lo = c.mapIndexed { k, p -> p.at(bottoms[k]) }
        val hi = c.mapIndexed { k, p -> p.at(max(tops[k], bottoms[k])) }
        var topN = ((hi[1] - hi[0]) cross (hi[3] - hi[0])).normalized()
        if (topN.y < 0) topN = topN * -1.0
        quad(hi[0], hi[1], hi[2], hi[3], topN, top, pick)
        // Intradosso (la faccia sotto, curva dell'arco): rivolto in basso.
        var lowN = ((lo[1] - lo[0]) cross (lo[3] - lo[0])).normalized()
        if (lowN.y > 0) lowN = lowN * -1.0
        quad(lo[0], lo[1], lo[2], lo[3], lowN, color, pick)
        val center = (p0 + p1) / 2.0
        for (k in 0 until 4) {
            val k2 = (k + 1) % 4
            val out = ((c[k] + c[k2]) / 2.0 - center).dir().normalized()
            quad(lo[k], lo[k2], hi[k2], hi[k], out, color, pick)
        }
    }

    // ---------- Pavimenti e soffitti ----------

    /** Pavimento; dove arriva una scala dal piano di sotto resta il vuoto. */
    private fun floor(room: Room) {
        val typeColor = Rgba.argb(room.type.argb)
        val focused = current && room.id == focusedRoomId
        // Un po' schiarito: il pavimento riceve la luce di sbieco e altrimenti sembra spento.
        val material = MaterialCatalog.item(room.floorMaterial)
        val finish = (material?.argb ?: room.floorFinish.argb)?.let { Rgba.argb(it).mix(Rgba.White, 0.15f) }
        val color = when {
            finish == null -> Rgba.White.mix(typeColor, if (focused) 0.55f else 0.3f)
            focused -> finish.mix(selectedTint, 0.18f)
            else -> finish
        }
        val pick = Pick.RoomFloor(room.id)
        // Posa a pezzi: sotto i pezzi si vede il colore della fuga.
        val laid = material?.let { m -> layingPieces(m, room.floorLayout, room.interiorOutline(), room.points) }
        val base = laid?.let { (l, _) -> Rgba.argb(l.jointArgb).let { if (focused) it.mix(selectedTint, 0.18f) else it } } ?: color
        val parts = mutableListOf<List<Vec2>>()
        for ((a, b, c) in Triangulation.triangulate(room.points)) {
            val t = listOf(room.points[a], room.points[b], room.points[c])
            parts += if (holes.isEmpty()) listOf(t) else Clip.subtractAll(t, holes)
        }
        for (p in parts) for (k in 1 until p.size - 1) {
            tri(p[0].at(0.0), p[k].at(0.0), p[k + 1].at(0.0), Vec3.Up, base, pick)
            // Materiale fotografico appena sopra, ripetuto ogni `size` cm (coordinate della pianta), girato e ingrandito come scelto.
            if (material != null && laid == null) {
                fun uv(q: Vec2) = room.floorLayout.uv(q.x, q.y, material.size)
                texTri(material.id, p[0].at(0.03), p[k].at(0.03), p[k + 1].at(0.03), uv(p[0]), uv(p[k]), uv(p[k + 1]), Vec3.Up)
            }
        }
        if (material != null && laid != null) {
            val (laying, pieces) = laid
            for (piece in pieces) {
                val outline = piece.outline
                val box = Polygon.bounds(outline)
                val window = LayingPieces.window(material.id, material.size, laying, piece, room.id.toInt())
                for (p in parts) {
                    if (p.all { it.x < box.minX } || p.all { it.x > box.maxX } || p.all { it.y < box.minY } || p.all { it.y > box.maxY }) continue
                    val q = Polygon.clipConvex(p, outline)
                    if (q.size < 3) continue
                    fun uv(v: Vec2) = window.uv(piece.local(v))
                    for (k in 1 until q.size - 1) {
                        texTri(material.id, q[0].at(0.03), q[k].at(0.03), q[k + 1].at(0.03), uv(q[0]), uv(q[k]), uv(q[k + 1]), Vec3.Up)
                    }
                }
            }
        }
        // Con il materiale fotografico le fughe sono già nella foto.
        if (material == null) floorPattern(room, color)
    }

    /**
     * Pezzi della posa a pezzi di `material` su `region` (partendo dal contorno `inner`), o null se il materiale non si posa
     * a pezzi (posa non scelta, foto che è già una posa, troppi pezzi).
     */
    private fun layingPieces(material: MaterialCatalog.Item, layout: MaterialLayout, inner: List<Vec2>, region: List<Vec2>): Pair<Laying, List<LaidPiece>>? {
        val laying = layout.laying?.let { LayingPieces.normalized(it) } ?: return null
        if (!MaterialPieces.canLay(material.id)) return null
        val pieces = LayingPieces.cover(laying, layout.rotation, LayingPieces.anchor(laying, layout.rotation, inner), region)
        return if (pieces.isEmpty()) null else laying to pieces
    }

    /**
     * Solaio sotto le parti di una stanza che non stanno sopra una stanza del piano di sotto (balconi, terrazze,
     * sporgenze): la faccia di sotto, spessa [Floor.SLAB], e i bordi verso l'esterno.
     */
    private fun underside(room: Room, coveredBelow: List<List<Vec2>>) {
        val color = Rgba(0.87f, 0.86f, 0.84f)
        val y = -Floor.SLAB
        val cut = coveredBelow + holes
        var any = false
        for ((a, b, c) in Triangulation.triangulate(room.points)) {
            val t = listOf(room.points[a], room.points[b], room.points[c])
            val pieces = if (cut.isEmpty()) listOf(t) else Clip.subtractAll(t, cut)
            for (p in pieces) for (k in 1 until p.size - 1) {
                tri(p[0].at(y), p[k].at(y), p[k + 1].at(y), Vec3.Up * -1.0, color, null)
                any = true
            }
        }
        if (!any) return
        // Bordi: sui lati che danno sul vuoto (fuori non c'è né un'altra stanza né il piano di sotto).
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        for (i in 0 until room.wallCount) {
            val s = room.wallStart(i)
            val e = room.wallEnd(i)
            if (s.distanceTo(e) < 1.0) continue
            val outward = ((e - s).normalized().perp() * -sign)
            val probe = (s + e) / 2.0 + outward * 3.0
            if (coveredBelow.any { Polygon.contains(it, probe) } || plan.rooms.any { it.id != room.id && Polygon.contains(it.points, probe) }) continue
            quad(s.at(y), e.at(y), e.at(0.0), s.at(0.0), Vec3(outward.x, 0.0, outward.y), color, null)
        }
    }

    /**
     * Fughe del pavimento, appena sopra il piano: doghe del parquet sfalsate, piastrelle 60 × 60 o lastre
     * 90 × 90. Le linee si tagliano sul contorno interno della stanza (e sul vuoto delle scale).
     */
    private fun floorPattern(room: Room, base: Rgba) {
        val pattern = room.floorFinish.pattern
        if (pattern == FloorPattern.None) return
        val inner = room.interiorOutline()
        if (inner.size < 3) return
        val line = base.mix(Rgba(0f, 0f, 0f), 0.16f)
        val b = Polygon.bounds(inner)
        // Stanza più grande di 100 × 100 m (di solito una misura scritta per sbaglio, es. in metri invece che in cm): le fughe
        // non si vedrebbero e sarebbero milioni di pezzi, abbastanza da finire la memoria del telefono.
        if (b.width * b.height > MAX_PATTERN_AREA) return
        val y = 0.15
        /** Tratti della retta (orizzontale se `horizontal`, a quota `c`) dentro la stanza e fuori dai vuoti. */
        fun inside(horizontal: Boolean, c: Double): List<Pair<Double, Double>> {
            val xs = mutableListOf<Double>()
            for (k in inner.indices) {
                val p = inner[k]
                val q = inner[(k + 1) % inner.size]
                val (pc, qc) = if (horizontal) p.y to q.y else p.x to q.x
                if ((pc <= c) == (qc <= c)) continue
                val t = (c - pc) / (qc - pc)
                xs += if (horizontal) p.x + (q.x - p.x) * t else p.y + (q.y - p.y) * t
            }
            xs.sort()
            return (0 until xs.size / 2).map { xs[2 * it] to xs[2 * it + 1] }
        }
        fun at(horizontal: Boolean, c: Double, s: Double) = if (horizontal) Vec2(s, c) else Vec2(c, s)
        fun groove(horizontal: Boolean, c: Double, s0: Double, s1: Double) {
            // Il tratto si spezza dove passa sopra il vuoto di una scala.
            val steps = ((s1 - s0) / 10.0).toInt().coerceAtLeast(1)
            var runStart: Double? = null
            for (k in 0..steps) {
                val s = s0 + (s1 - s0) * k / steps
                val free = holes.none { Polygon.contains(it, at(horizontal, c, s)) }
                if (free && runStart == null) runStart = s
                if ((!free || k == steps) && runStart != null) {
                    val end = if (free) s else s - (s1 - s0) / steps
                    if (end - runStart > 1 && detail(6)) box(at(horizontal, c, runStart), at(horizontal, c, end), 0.25, y - 0.1, y, line, null)
                    runStart = null
                }
            }
        }
        when (pattern) {
            FloorPattern.None -> Unit
            FloorPattern.Planks -> {
                // Doghe larghe 15 cm lungo il lato più lungo, giunte ogni 120 cm sfalsate di riga in riga.
                val horizontal = b.width >= b.height
                val (c0, c1) = if (horizontal) b.minY to b.maxY else b.minX to b.maxX
                var c = c0 + 15.0
                var row = 0
                while (c < c1) {
                    for ((s0, s1) in inside(horizontal, c)) groove(horizontal, c, s0, s1)
                    // Giunte di testa nella riga appena sopra la fuga.
                    var s = (if (horizontal) b.minX else b.minY) + (if (row % 2 == 0) 40.0 else 100.0)
                    val limit = if (horizontal) b.maxX else b.maxY
                    while (s < limit) {
                        val mid = at(horizontal, c - 7.5, s)
                        if (Polygon.contains(inner, mid) && holes.none { Polygon.contains(it, mid) } && detail(6)) {
                            box(at(horizontal, c - 15.0, s), at(horizontal, c, s), 0.25, y - 0.1, y, line, null)
                        }
                        s += 120.0
                    }
                    c += 15.0
                    row++
                }
            }
            FloorPattern.Tiles, FloorPattern.LargeTiles -> {
                val size = if (pattern == FloorPattern.Tiles) 60.0 else 90.0
                for (horizontal in listOf(true, false)) {
                    val (c0, c1) = if (horizontal) b.minY to b.maxY else b.minX to b.maxX
                    var c = c0 + size
                    while (c < c1) {
                        for ((s0, s1) in inside(horizontal, c)) groove(horizontal, c, s0, s1)
                        c += size
                    }
                }
            }
        }
    }

    /**
     * Soffitto: faccia rivolta in basso, quindi visibile solo da dentro (dall'alto la GPU la scarta).
     * In mansarda segue la pendenza: i triangoli si suddividono finché sono piccoli, così anche un
     * tetto a capanna (due pendenze) ha il colmo al posto giusto.
     */
    private fun ceiling(room: Room) {
        val up = Vec3(0.0, base, 0.0) // quota del piano (0 per quello corrente)
        // Il vano scala è nel solaio: si taglia solo nei soffitti che arrivano al solaio, non in quelli più bassi
        // (es. un ripostiglio basso sotto la scala).
        val holesAbove = if (room.ceilingHeight >= levelHeight - Floor.SLAB - 1.0) holesAbove else emptyList()
        for ((qa, qb, qc) in Ceilings.triangles(room)) {
            val pa = qa + up; val pb = qb + up; val pc = qc + up
            var n = ((pb - pa) cross (pc - pa)).normalized()
            if (n.length < 0.5) continue // triangolo degenere
            if (n.y > 0) n = n * -1.0
            if (holesAbove.isEmpty()) { tri(pa, pb, pc, n, ceilingColor, null); continue }
            // Vano scala: si toglie dal soffitto il vuoto delle scale che salgono (in pianta), e ogni pezzo
            // che resta torna sul piano del triangolo (anche in mansarda, in pendenza).
            val flat = listOf(Vec2(pa.x, pa.z), Vec2(pb.x, pb.z), Vec2(pc.x, pc.z))
            fun yAt(p: Vec2) = if (abs(n.y) < 1e-9) pa.y else pa.y - (n.x * (p.x - pa.x) + n.z * (p.y - pa.z)) / n.y
            for (poly in Clip.subtractAll(flat, holesAbove)) for (k in 1 until poly.size - 1) {
                val a = poly[0]; val b = poly[k]; val c = poly[k + 1]
                tri(Vec3(a.x, yAt(a), a.y), Vec3(b.x, yAt(b), b.y), Vec3(c.x, yAt(c), c.y), n, ceilingColor, null)
            }
        }
    }

    /**
     * Tratti del contorno del vuoto formato dai `pieces` (i pezzi di una o più scale, anche adiacenti o sovrapposti): per ogni
     * tratto, i due estremi e la direzione verso l'interno del vuoto. I tratti in comune tra due pezzi non sono contorno.
     * Si valuta un punto per centimetro di lato, perché un lato può essere solo in parte in comune con un altro pezzo.
     */
    private fun holeEdgeRuns(pieces: List<List<Vec2>>): List<Triple<Vec2, Vec2, Vec2>> {
        val out = mutableListOf<Triple<Vec2, Vec2, Vec2>>()
        for ((pi, piece) in pieces.withIndex()) {
            for (k in piece.indices) {
                val a = piece[k]
                val b = piece[(k + 1) % piece.size]
                val len = a.distanceTo(b)
                if (len < 1.0) continue
                val d = (b - a).normalized().perp()
                val n = min(kotlin.math.ceil(len), MAX_EDGE_SAMPLES.toDouble()).toInt()
                var runStart = -1
                var runIn = d
                fun close(end: Int) {
                    if (runStart >= 0) out += Triple(a + (b - a) * (runStart.toDouble() / n), a + (b - a) * (end.toDouble() / n), runIn)
                    runStart = -1
                }
                for (i in 0 until n) {
                    val p = a + (b - a) * ((i + 0.5) / n)
                    // Il lato verso l'interno del pezzo e quello verso l'esterno; il tratto è contorno se fuori non c'è un altro pezzo.
                    val inside = if (Polygon.contains(piece, p + d * 0.5)) d else d * -1.0
                    val outsidePoint = p - inside * 0.5
                    val shared = pieces.withIndex().any { (qi, q) -> qi != pi && Polygon.contains(q, outsidePoint) }
                    if (shared) close(i) else {
                        if (runStart < 0) { runStart = i; runIn = inside }
                    }
                }
                close(n)
            }
        }
        return out
    }

    /**
     * Bordi del vano scala nel soffitto: fasce verticali dal soffitto della stanza fino al pavimento di sopra
     * (lo spessore del solaio), sul contorno del vuoto (non dove due pezzi del vuoto confinano). Dove sopra non c'è una stanza
     * del piano `next` la scala esce sul tetto: il bordo si vede anche da fuori casa.
     */
    private fun wellSides(next: Level?) {
        val plaster = slabEdge
        for ((a, b, inward) in holeEdgeRuns(holesAbove)) {
            val mid = (a + b) / 2.0
            // Il soffitto sotto il solaio è quello della stanza più alta lì (una stanza bassa ricavata dentro
            // un'altra, come un ripostiglio sotto la scala, ha il suo soffitto più in basso: non conta).
            val y0 = plan.rooms.filter { !it.outdoor && Polygon.contains(it.points, mid) }.maxOfOrNull { Ceilings.heightAt(it, mid) } ?: continue
            if (levelHeight - y0 < 1.0) continue
            val n = Vec3(inward.x, 0.0, inward.y)
            quad(a.at(y0), b.at(y0), b.at(levelHeight), a.at(levelHeight), n, plaster, null)
            if (next == null || next.plan.rooms.none { !it.outdoor && Polygon.contains(it.points, mid) }) {
                val out = inward * -0.4
                quad((a + out).at(y0), (b + out).at(y0), (b + out).at(levelHeight), (a + out).at(levelHeight), n * -1.0, plaster, null)
            }
        }
    }

    /**
     * Bordi del vuoto della scala nel pavimento di questo piano: lo spessore del solaio ([Floor.SLAB], da sotto il pavimento
     * a filo del pavimento) visto dall'alto. Solo sul contorno del vuoto (mai dove due pezzi confinano); fuori dalle stanze del
     * piano, dal tetto piano del piano di sotto al pavimento.
     */
    private fun floorHoleSides(previous: Level) {
        for ((a, b, inward) in holeEdgeRuns(holes)) {
            val mid = (a + b) / 2.0
            val n = Vec3(inward.x, 0.0, inward.y)
            // Appena dentro il vuoto, così non si sovrappone alla faccia di un muro contro cui sta la scala.
            val lift = inward * 0.2
            if (plan.rooms.any { !it.outdoor && Polygon.contains(it.points, mid) }) {
                quad((a + lift).at(-Floor.SLAB), (b + lift).at(-Floor.SLAB), (b + lift).at(0.0), (a + lift).at(0.0), n, slabEdge, null)
                continue
            }
            // Fuori dalle stanze di questo piano la scala esce sul tetto piano del piano di sotto, più in basso del pavimento: il
            // bordo del vano sale dal tetto al pavimento, da dentro e da fuori, e la ringhiera attorno al vano ci sta sopra.
            val below = Structure.roomAt(previous.plan, mid - inward * 1.0) ?: continue
            val roof = previous.base + Ceilings.heightAt(below, mid) + 2.0 - base
            if (roof > -0.5) continue
            quad((a + lift).at(roof), (b + lift).at(roof), (b + lift).at(0.0), (a + lift).at(0.0), n, slabEdge, null)
            quad((a - lift).at(roof), (b - lift).at(roof), (b - lift).at(0.0), (a - lift).at(0.0), n * -1.0, slabEdge, null)
        }
    }

    /**
     * Vuoto nel solaio sopra le scale del piano `lower` ([Stairs.openWell]): dove sopra c'è una stanza del piano `upper`; fuori il
     * tetto piano, aperto solo sopra i gradini che lo attraverserebbero (il corrimano lì sotto si ferma sotto il soffitto, vedi
     * [railing]). Senza il piano di sopra nella scena il vuoto resta tutto.
     */
    private fun stairWells(lower: Level, upper: FloorPlan?): List<List<Vec2>> {
        val cover = upper?.let { Stairs.cover(it) }
        return lower.plan.stairs.flatMap { Stairs.openWell(it, lower.levelHeight, lower.plan, cover) }
    }

    /** I tratti `runs` (ringhiere) solo dove stanno dentro una stanza del piano (pavimento sotto), a pezzi di 10 cm. */
    private fun insideRooms(runs: List<Pair<Vec2, Vec2>>): List<Pair<Vec2, Vec2>> {
        val out = mutableListOf<Pair<Vec2, Vec2>>()
        for ((a, b) in runs) {
            val n = (a.distanceTo(b) / 10.0).toInt().coerceIn(1, 2000)
            var start: Vec2? = null
            for (k in 0 until n) {
                val p0 = a + (b - a) * (k.toDouble() / n)
                val p1 = a + (b - a) * ((k + 1).toDouble() / n)
                val inside = plan.rooms.any { it.points.size >= 3 && Polygon.contains(it.points, (p0 + p1) / 2.0) }
                if (inside && start == null) start = p0
                if (!inside && start != null) { out += start to p0; start = null }
            }
            if (start != null) out += start to b
        }
        return out
    }

    /**
     * Piano di sopra (camminando): quota (relativa al piano) da cui parte il muro p0→p1. Scende nel solaio fino a 1 cm sopra il
     * soffitto più alto del piano di sotto lungo il muro: da fuori continua il muro di sotto senza una fessura (da cui si
     * vedrebbe dentro il piano di sopra, che lì non ha pavimento), e dentro casa resta nascosto sopra il soffitto.
     */
    private fun bottomOver(previous: Level, p0: Vec2, p1: Vec2): Double {
        val steps = max(1, (p0.distanceTo(p1) / 50.0).toInt() + 1)
        val top = (0..steps).maxOf { k -> Structure.ceilingAt(previous.plan, p0 + (p1 - p0) * (k.toDouble() / steps), previous.levelHeight) }
        return min(0.0, previous.base + top + 1.0 - base)
    }

    // ---------- Muri con i varchi ----------

    /** Varco nel muro, in cm lungo il muro (t) e in altezza. */
    private class Gap(
        val t0: Double, val t1: Double, val bottom: Double, val top: Double, val arch: PassageStyle, val pick: Pick,
        /** Sguincio (cm per lato) verso l'interno della stanza `splayRoom`: sulla sua faccia il varco è più largo. */
        val splay: Double = 0.0, val splayRoom: Long? = null, val splayRounded: Boolean = false,
        /** Nicchia: buca solo la fascia interna della stanza `splayRoom`, non il muro intero. */
        val niche: Boolean = false,
        /** Varco tondo (oblò): cerchio con il diametro della larghezza, a partire da `bottom`. */
        val round: Boolean = false,
    ) {
        val arched: Boolean get() = arch.arched
        /** Bordi curvi: il muro attorno si fa a fettine. */
        val curved: Boolean get() = arched || round

        /**
         * Il varco sulla faccia verso la stanza, con lo sguincio: più largo di [splay] per lato (in cima resta alla stessa quota,
         * come l'arco); l'oblò si allarga tutto attorno, con lo stesso centro.
         */
        fun widened() = Gap(t0 - splay, t1 + splay, if (round) bottom - splay else bottom, top, arch, pick, splay, splayRoom, splayRounded, niche, round)

        /** Bordo inferiore del varco nel punto t: dritto, o la metà bassa del cerchio. */
        fun bottomAt(t: Double): Double {
            if (!round) return bottom
            val c = (t1 - t0) / 2
            val x = (t - (t0 + t1) / 2).coerceIn(-c, c)
            return bottom + c - sqrt(max(0.0, c * c - x * x))
        }

        /** Bordo superiore del varco nel punto t: dritto, ad arco tondo, ribassato o a sesto acuto. */
        fun topAt(t: Double): Double {
            if (round) {
                val c = (t1 - t0) / 2
                val x = (t - (t0 + t1) / 2).coerceIn(-c, c)
                return bottom + c + sqrt(max(0.0, c * c - x * x))
            }
            return Openings.archTop(arch, t0, t1, bottom, top, t)
        }
    }

    /**
     * Vano dello sguincio del varco `g` (sulla faccia verso la stanza della fascia `side`): una superficie che va dal bordo del
     * varco sulla mezzeria del muro a quello del varco allargato ([Gap.widened]) sulla faccia, dritta o arrotondata. Il bordo si
     * segue punto per punto: i lati, e anche l'arco o il cerchio dell'oblò, negli stessi punti in cui il muro attorno è fatto a
     * fettine (così vano e muro si toccano senza fessure). Una metà non si disegna se la sua faccia cade fuori dal tratto `range`.
     */
    private fun splayReveal(g: Gap, s: Vec2, u: Vec2, side: Vec2, range: ClosedFloatingPointRange<Double>, color: Rgba) {
        val w = g.widened()
        val mid = (g.t0 + g.t1) / 2
        // Asse del varco (punti verso cui guarda il vano): la verticale a metà, tra il fondo e l'imposta dell'arco; il centro per l'oblò.
        val axisLow = if (g.round) g.bottom + (g.t1 - g.t0) / 2 else g.bottom
        val axisHigh = when {
            g.round -> axisLow
            g.curved -> min(g.topAt(g.t0), w.topAt(w.t0))
            else -> g.top
        }
        /** Punto della superficie: `t` lungo il muro, `y` in altezza, `depth` dalla mezzeria verso la faccia della stanza. */
        fun at(t: Double, y: Double, depth: Double) = (s + u * t + side * depth).at(y)
        val steps = if (g.splayRounded) Openings.SPLAY_STEPS else 1
        for (toward in listOf(-1.0, 1.0)) {
            val edge = if (toward < 0) w.t0 else w.t1
            if (edge < range.start - 0.01 || edge > range.endInclusive + 0.01) continue
            // Coppie di punti corrispondenti (sulla mezzeria, sulla faccia), come (t, altezza), dal basso verso la cima.
            val rungs = ArrayList<Pair<Vec2, Vec2>>()
            fun rung(k: Double, top: Boolean) {
                val t = mid + k * (g.t1 - g.t0) / 2
                val tw = mid + k * (w.t1 - w.t0) / 2
                rungs += Vec2(t, if (top) g.topAt(t) else g.bottomAt(t)) to Vec2(tw, if (top) w.topAt(tw) else w.bottomAt(tw))
            }
            val n = ARCH_SLICES / 2
            when {
                // Oblò: dal punto più basso al lato, poi su fino in cima.
                g.round -> {
                    for (j in 0..n) rung(toward * j / n, top = false)
                    for (j in n downTo 0) rung(toward * j / n, top = true)
                }
                // Arco: il lato fino all'imposta, poi lungo l'arco fino alla chiave.
                g.curved -> {
                    rung(toward, top = false)
                    for (j in n downTo 0) rung(toward * j / n, top = true)
                }
                else -> { rung(toward, top = false); rung(toward, top = true) }
            }
            fun point(r: Pair<Vec2, Vec2>, k: Int): Vec3 {
                val tau = k.toDouble() / steps
                // Arrotondato: curva che parte perpendicolare alla mezzeria e arriva sulla faccia (come in pianta, [Openings.splayProfile]):
                // il punto di controllo sta sulla faccia, davanti al bordo del varco. Dritto: il controllo a metà, cioè una linea.
                val (a, b) = r
                val control = if (g.splayRounded) a else (a + b) / 2.0
                val p = a * ((1 - tau) * (1 - tau)) + control * (2 * tau * (1 - tau)) + b * (tau * tau)
                val depth = (if (g.splayRounded) half else half / 2) * (2 * tau * (1 - tau)) + half * (tau * tau)
                return at(p.x, p.y, depth)
            }
            for (i in 0 until rungs.size - 1) for (k in 0 until steps) {
                val a = point(rungs[i], k)
                val b = point(rungs[i + 1], k)
                val c = point(rungs[i + 1], k + 1)
                val d = point(rungs[i], k + 1)
                val y = (a.y + b.y + c.y + d.y) / 4 - base
                val axis = at(mid, y.coerceIn(axisLow, max(axisLow, axisHigh)), (k + 0.5) / steps * half)
                for ((p, q, r) in listOf(Triple(a, b, c), Triple(a, c, d))) {
                    var n = (q - p) cross (r - p)
                    if (n.length < 1e-9) continue
                    if ((n dot (axis - (p + q + r) * (1.0 / 3))) < 0) n = n * -1.0
                    tri(p, q, r, n, color, g.pick)
                }
            }
        }
    }

    private fun gapOf(owner: Room, o: Opening, s: Vec2, u: Vec2, wallTop: (Double) -> Double): Gap {
        val (a, b) = Openings.span(owner, o)
        val ta = (a - s) dot u
        val tb = (b - s) dot u
        val bottom = o.bottom
        // Il varco non supera il muro, che in mansarda può essere più basso dell'apertura.
        val top = min(wallTop((ta + tb) / 2) - 1, bottom + o.height)
        return Gap(
            min(ta, tb), max(ta, tb), bottom, top,
            when {
                o.kind == OpeningKind.Passage -> o.style
                o.shape == WindowShape.Arched -> PassageStyle.Arched
                else -> PassageStyle.Square
            },
            Pick.Opening(owner.id, o.id),
            // L'oblò si allarga tutto attorno: non sotto il pavimento, né sopra la cima del muro.
            splay = Openings.splayOf(owner, o).let {
                if (o.shape == WindowShape.Round) min(it, max(0.0, min(bottom, wallTop((ta + tb) / 2) - 1 - bottom - abs(tb - ta)))) else it
            },
            splayRoom = owner.id, splayRounded = o.splayRounded, niche = o.kind == OpeningKind.Niche,
            round = o.shape == WindowShape.Round,
        )
    }

    /**
     * Muro `i` della stanza, costruito come due mezze fasce ai lati della mezzeria:
     * - la fascia interna, verso la stanza, con il colore delle sue pareti, che segue la sua parete (anche
     *   tagliata in diagonale). Ogni stanza disegna la propria, anche sui muri in comune: così ogni lato di
     *   un muro in comune ha il colore della propria stanza;
     * - la fascia esterna, con il colore della facciata, solo dove il muro dà all'esterno (non confina con
     *   un'altra stanza; un balcone conta come esterno).
     * Agli angoli le fasce si raccordano senza sporgere: quella interna si allunga di mezzo spessore solo
     * negli angoli rientranti, quella esterna solo negli angoli sporgenti della casa. Così nessun colore
     * sconfina sulla faccia di un'altra stanza.
     * Balconi e terrazze: parapetto verso l'esterno; verso la casa il muro lo disegna la stanza.
     */
    private fun wall(room: Room, i: Int) {
        // Muro eliminato: da quel lato la stanza è aperta (niente muro né parapetto).
        if (room.isRemoved(i)) return
        half = room.thicknessOf(i) / 2
        try { wallBody(room, i) } finally { half = Room.WALL_THICKNESS / 2 }
    }

    private fun wallBody(room: Room, i: Int) {
        val s = room.wallStart(i)
        val e = room.wallEnd(i)
        val len = s.distanceTo(e)
        if (len < 1.0) return
        val u = (e - s) / len
        // Un pezzo di muro curvo si seleziona come il muro intero.
        val wallPick = Pick.Wall(room.id, expanded.originOf(room.id, i))
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        val inward = u.perp() * sign
        val sloped = Ceilings.effectiveCut(room, i) != null
        /** Parete di questa stanza nel punto t. */
        fun ownTop(t: Double): Double = Ceilings.wallTopAt(room, i, t.coerceIn(0.0, len))

        // Angoli del muro: sporgenti (convessi) o rientranti, per come raccordare le fasce.
        fun convexAt(corner: Int): Boolean {
            val n = room.wallCount
            val prev = (room.points[corner] - room.points[(corner - 1 + n) % n]).normalized()
            val next = (room.points[(corner + 1) % n] - room.points[corner]).normalized()
            return (prev.x * next.y - prev.y * next.x) * sign >= -1e-6
        }
        // Muro che continua dritto con un altro spessore (scalino): lì nessuna fascia si allunga, i due tratti
        // finiscono pari e la testata del più spesso fa lo scalino.
        val startStraight = Polygon.straightAt(room.points, i)
        val endStraight = Polygon.straightAt(room.points, (i + 1) % room.wallCount)
        val startConvex = convexAt(i)
        // Agli angoli la fascia si allunga di mezzo spessore del muro accanto (che può essere diverso).
        val prevHalf = room.thicknessOf((i - 1 + room.wallCount) % room.wallCount) / 2
        val nextHalf = room.thicknessOf((i + 1) % room.wallCount) / 2
        val endConvex = convexAt((i + 1) % room.wallCount)
        /**
         * Giunta quasi dritta (i pezzi di un muro curvo, o un muro appena storto): dove la faccia di questo muro, dal lato
         * `side` (1 = verso la stanza, −1 = fuori), incontra quella del muro accanto, in cm lungo il muro dall'angolo
         * (all'inizio, o alla fine se `atEnd`). Così i pezzi si raccordano senza allungarsi di mezzo spessore uno sull'altro.
         * Null per gli angoli veri, che si raccordano come sempre; 0 dove lo spessore cambia (scalino).
         */
        fun nearlyStraightJoin(atEnd: Boolean, side: Double): Double? {
            val n = room.wallCount
            val other = if (atEnd) (i + 1) % n else (i - 1 + n) % n
            if (room.isRemoved(other)) return null
            val od = room.wallEnd(other) - room.wallStart(other)
            if (od.length < 1e-6) return null
            val d = od.normalized()
            if ((u dot d) < NEARLY_STRAIGHT_COS) return null
            val c = u.x * d.y - u.y * d.x
            val otherHalf = room.thicknessOf(other) / 2
            if (abs(c) < 1e-6) return 0.0
            val w = d.perp() * (sign * side * otherHalf) - inward * (side * half)
            val t = (w.x * d.y - w.y * d.x) / c
            // Solo allungata: accorciata lascerebbe uno spiraglio sul lato della mezzeria; dove le facce si incrociano, il
            // pezzetto in più sta dentro il muro accanto e non si vede.
            return when {
                abs(t) > 2 * max(half, otherHalf) + 1 -> 0.0
                atEnd -> max(t, 0.0)
                else -> min(t, 0.0)
            }
        }
        val others = plan.rooms.filter { it.id != room.id && !it.outdoor }
        // Per i muri in comune solo le stanze vicine: con centinaia di stanze (e muri curvi fatti a pezzi) confrontare ogni
        // muro con tutti gli altri rendeva il 3D lentissimo. Più in là della tolleranza dei muri in comune non si toccano.
        val reach = Openings.SHARED_WALL_TOLERANCE_CM + 1
        val near = plan.rooms.filterIndexed { k, it ->
            it.id != room.id && !it.outdoor && roomBounds[k].let { b ->
                b.minX - reach <= max(s.x, e.x) && b.maxX + reach >= min(s.x, e.x) && b.minY - reach <= max(s.y, e.y) && b.maxY + reach >= min(s.y, e.y)
            }
        }
        fun touched(p: Vec2) = others.any { o -> (0 until o.wallCount).any { j -> Polygon.distanceToSegment(p, o.wallStart(j), o.wallEnd(j)) < 1.0 } }
        /**
         * Angolo `p` della casa dove il muro esterno continua in un'altra stanza, girando (es. il muro del bagno, storto, e
         * quello dell'ingresso, dritto, che partono dallo stesso angolo): di quanto allungare la fascia esterna oltre `p`,
         * verso `along`, perché la sua faccia arrivi dove comincia quella dell'altro muro. Senza, tra le due fasce resta uno
         * scalino o una fessura. Solo allungata (il pezzo in più sta dentro il muro dell'altra stanza); 0 se non c'è.
         */
        fun junction(p: Vec2, along: Vec2): Double {
            val out = inward * -1.0
            var best = 0.5
            var ext = 0.0
            for (o in others) for (j in 0 until o.wallCount) {
                if (o.isRemoved(j)) continue
                val a = o.wallStart(j)
                val b = o.wallEnd(j)
                val (q, far) = when {
                    a.distanceTo(p) < 1.0 -> a to b
                    b.distanceTo(p) < 1.0 -> b to a
                    else -> continue
                }
                if (q.distanceTo(far) < 1e-6) continue
                val d = (far - q).normalized()
                val align = along dot d
                if (align <= best) continue
                val c = along.x * d.y - along.y * d.x
                val oh = o.thicknessOf(j) / 2
                val w = (q + o.outwardOf(j) * oh) - (p + out * half)
                val t = if (abs(c) < 1e-6) 0.0 else (w.x * d.y - w.y * d.x) / c
                best = align
                ext = if (t > 2 * max(half, oh) + 1) 0.0 else max(t, 0.0)
            }
            return ext
        }

        // Tratti del muro in comune con altre stanze (non all'aperto). Conta da che parte sta l'altra stanza:
        // - dall'altra parte del muro (stanze accostate): il muro è interno alla casa, niente facciata lì;
        // - dalla stessa parte (una stanza dentro l'altra, es. un ripostiglio ricavato in un soggiorno contro
        //   il muro esterno): la facciata resta alla stanza più grande, la faccia interna alla più piccola.
        val contacts = mutableListOf<ClosedFloatingPointRange<Double>>()
        val hiddenInterior = mutableListOf<ClosedFloatingPointRange<Double>>()
        val area = Polygon.area(room.points)
        for (o in near) for (j in 0 until o.wallCount) {
            // Un muro eliminato dell'altra stanza non copre la faccia: da quella parte il muro si vede intero.
            if (o.isRemoved(j) || !Dimensions.inContact(s, e, o.wallStart(j), o.wallEnd(j))) continue
            val t1 = ((o.wallStart(j) - s) dot u).coerceIn(0.0, len)
            val t2 = ((o.wallEnd(j) - s) dot u).coerceIn(0.0, len)
            if (kotlin.math.abs(t2 - t1) <= 0.5) continue
            val stretch = min(t1, t2)..max(t1, t2)
            val probe = s + u * ((t1 + t2) / 2) + inward * 5.0
            val sameSide = Polygon.contains(o.points, probe)
            when {
                !sameSide -> contacts += stretch
                Polygon.area(o.points) < area -> hiddenInterior += stretch // dentro questa stanza: la sua faccia vince
                else -> contacts += stretch // questa stanza è dentro l'altra: la facciata la disegna l'altra
            }
        }
        var exterior = listOf(0.0..len)
        for (c in contacts) exterior = exterior.flatMap { cut(it, c) }
        exterior = exterior.filter { it.endInclusive - it.start > 0.5 }

        // Varchi: aperture di questa stanza sul muro e aperture delle stanze confinanti sullo stesso muro.
        val gaps = mutableListOf<Gap>()
        for (o in room.openings) if (o.wallIndex == i) gaps += gapOf(room, o, s, u, ::ownTop)
        for ((k, other) in plan.rooms.withIndex()) {
            if (other.id == room.id) continue
            for ((o, span) in openingSpans[k]) {
                val (a, b) = span
                if (min(a.x, b.x) > max(s.x, e.x) + reach || max(a.x, b.x) < min(s.x, e.x) - reach ||
                    min(a.y, b.y) > max(s.y, e.y) + reach || max(a.y, b.y) < min(s.y, e.y) - reach) continue
                if (Dimensions.inContact(s, e, a, b)) gaps += gapOf(other, o, s, u, ::ownTop)
            }
        }

        if (room.outdoor) {
            // Parapetto sui tratti verso il vuoto: non contro la casa né contro un altro balcone o terrazza
            // (insieme formano un'unica superficie). Dove c'è un cancelletto, niente.
            for (r in Parapets.openStretches(plan, room, i)) {
                val a = if (r.start < 0.5) -half else r.start
                val b = if (r.endInclusive > len - 0.5) len + half else r.endInclusive
                for (piece in gaps.fold(listOf(a..b)) { acc, g -> acc.flatMap { cut(it, g.t0..g.t1) } }) {
                    val p0 = s + u * piece.start
                    val p1 = s + u * piece.endInclusive
                    parapet(room, p0, p1, wallPick)
                    if (current) obstacles += Obstacle(p0, p1)
                }
            }
            return
        }

        val paint = room.finishOf(i)
        val facade = plan.facadeOrPaint
        // Tratto più spesso di un muro a scalini: sporge solo verso fuori, il filo verso la stanza resta dritto.
        val bandStart = s + room.outwardOf(i) * room.wallShift(i)
        // Fascia interna: da angolo ad angolo, allungata solo negli angoli rientranti; tolti i tratti coperti da una
        // stanza ricavata dentro questa (lì si vede la parete di quella stanza).
        // Angoli con un muro spostato (scalino): le fasce partono e finiscono dove si incontrano le mezzerie vere.
        val (shiftStart, shiftEnd) = room.shiftedEndDeltas(i)
        val interiorStart = nearlyStraightJoin(atEnd = false, side = 1.0) ?: (shiftStart + if (startConvex || startStraight) 0.0 else -prevHalf)
        val interiorEnd = nearlyStraightJoin(atEnd = true, side = 1.0)?.let { len + it } ?: (len + shiftEnd + if (endConvex || endStraight) 0.0 else nextHalf)
        var interiorRanges = listOf(interiorStart..interiorEnd)
        for (h in hiddenInterior) interiorRanges = interiorRanges.flatMap { cut(it, h) }
        for (r in interiorRanges) if (r.endInclusive - r.start > 0.5) {
            band(bandStart, u, len, gaps, sloped, room, i, wallPick, r, inward, paint, interior = true)
        }
        // Fascia esterna, sui tratti che danno all'esterno: allungata solo negli spigoli sporgenti della casa.
        for (r in exterior) {
            val a = when {
                r.start >= 0.5 -> r.start
                else -> nearlyStraightJoin(atEnd = false, side = -1.0) ?: when {
                    startConvex && !startStraight && !touched(s + u * shiftStart) -> shiftStart - prevHalf
                    startConvex && !startStraight -> shiftStart + r.start - junction(s, u * -1.0)
                    else -> shiftStart + r.start
                }
            }
            val b = when {
                r.endInclusive <= len - 0.5 -> r.endInclusive
                else -> nearlyStraightJoin(atEnd = true, side = -1.0)?.let { len + it } ?: when {
                    endConvex && !endStraight && !touched(e + u * shiftEnd) -> shiftEnd + len + nextHalf
                    endConvex && !endStraight -> shiftEnd + r.endInclusive + junction(e, u)
                    else -> shiftEnd + r.endInclusive
                }
            }
            band(bandStart, u, len, gaps, sloped, room, i, wallPick, a..b, inward * -1.0, facade, interior = false)
        }
    }

    /**
     * Mezza fascia di muro lungo il tratto `range` (in cm lungo il muro), dal lato `side` della mezzeria,
     * alta come la parete della stanza, con i varchi delle aperture. `interior`: faccia verso la stanza
     * (con il battiscopa).
     */
    private fun band(
        s: Vec2, u: Vec2, len: Double, gaps: List<Gap>, sloped: Boolean, room: Room, i: Int, wallPick: Pick,
        range: ClosedFloatingPointRange<Double>, side: Vec2, finish: WallFinish, interior: Boolean,
    ) {
        // Del muro selezionato si evidenzia solo il lato scelto (verso la stanza o la facciata).
        fun tint(c: Rgba, pick: Pick) = if (pick == wallPick && interior == selectedExterior) c else this.tint(c, pick)
        val paint = Rgba.argb(finish.argb)
        // Le nicchie bucano solo la fascia verso la loro stanza; in fondo resta la parete, alla mezzeria del muro.
        val faceGaps = gaps.filter { !it.niche || (interior && it.splayRoom == room.id) }
        for (g in faceGaps) if (g.niche) {
            val a = (g.t0).coerceIn(range.start, range.endInclusive)
            val b = (g.t1).coerceIn(range.start, range.endInclusive)
            if (b - a < 0.5) continue
            val back = side * 0.05
            val color = tint(paint, g.pick)
            val n = Vec3(side.x, 0.0, side.y)
            quad((s + u * a + back).at(g.bottom), (s + u * b + back).at(g.bottom), (s + u * b + back).at(g.top), (s + u * a + back).at(g.top), n, color, g.pick)
            quad((s + u * b + back).at(g.bottom), (s + u * a + back).at(g.bottom), (s + u * a + back).at(g.top), (s + u * b + back).at(g.top), n, color, g.pick)
        }
        // Sguinci: sulla faccia verso la stanza il varco si allarga, e il vano va dalla mezzeria alla faccia.
        val splayed = if (interior) faceGaps.filter { it.splay > 0 && it.splayRoom == room.id } else emptyList()
        @Suppress("NAME_SHADOWING")
        val gaps = if (splayed.isEmpty()) faceGaps else faceGaps.map { g -> if (g in splayed) g.widened() else g }
        for (g in splayed) splayReveal(g, s, u, side, range, tint(paint, g.pick))
        val coverTop = finish.coverHeight ?: Double.MAX_VALUE
        // Mansarda: il soffitto sale dalla mezzeria del muro basso, e sulla faccia verso la stanza sta già più in alto della cima
        // del muro. Con il soffitto disegnato la parete arriva fin lì, altrimenti in cima resta una fessura scura.
        val underSlope = interior && ceilings && current && Ceilings.hasSlope(room)
        fun topFn(t: Double): Double {
            val top = Ceilings.wallTopAt(room, i, t.coerceIn(0.0, len))
            return if (underSlope) max(top, Ceilings.heightAt(room, s + u * t.coerceIn(0.0, len) + side * half)) else top
        }
        val cuts = mutableSetOf(range.start, range.endInclusive)
        fun add(t: Double) { if (t > range.start && t < range.endInclusive) cuts += t }
        // Parete tagliata: il bordo superiore fa uno spigolo alle estremità e dove inizia a scendere.
        if (sloped) {
            add(0.0); add(len)
            Ceilings.startPoint(room, i)?.let { add((it - s) dot u) }
        }
        for (g in gaps) {
            add(g.t0); add(g.t1)
            // Varco ad arco: tante fettine, ognuna con il bordo inferiore inclinato lungo l'arco (curva continua).
            if (g.curved) for (k in 1 until ARCH_SLICES) add(g.t0 + (g.t1 - g.t0) * k / ARCH_SLICES)
        }
        val hw = half / 2
        val off = side * hw
        val list = cuts.sorted()
        for (k in 0 until list.size - 1) {
            val c0 = list[k]
            val c1 = list[k + 1]
            if (c1 - c0 < 0.01) continue
            val mid = (c0 + c1) / 2
            val covering = gaps.filter { mid > it.t0 && mid < it.t1 }
            val pick = covering.firstOrNull()?.pick ?: wallPick
            val p0 = s + u * c0
            val p1 = s + u * c1
            val h = topFn(mid)
            // Fasce verticali piene: tutta l'altezza meno i varchi che coprono questo tratto.
            // Ai piani superiori il muro scende anche nello spessore del solaio, fino al muro di sotto.
            var ranges = listOf(wallBottom..h)
            // Sotto una porta il pezzo nello spessore del solaio resta mezzo cm sotto il pavimento: alla stessa quota le due facce
            // sfarfallerebbero sulla soglia.
            for (g in covering) ranges = ranges.flatMap { cut(it, (if (g.bottomAt(mid) < 0.01) g.bottomAt(mid) - THRESHOLD_SINK else g.bottomAt(mid))..g.topAt(mid)) }
            // Con un rivestimento fino a una certa altezza, il muro si divide in parte rivestita e parte dipinta.
            if (finish.covering != Covering.None) ranges = ranges.flatMap { r ->
                if (coverTop > r.start + 0.05 && coverTop < r.endInclusive - 0.05) listOf(r.start..coverTop, coverTop..r.endInclusive) else listOf(r)
            }
            // Lo spessore del solaio (sotto il pavimento del piano di sopra) si distingue solo sulla faccia interna del muro, che si vede
            // dai vuoti del pavimento (scale): la facciata esterna resta del colore della parete, senza fasce.
            if (interior && wallBottom < -0.5) ranges = ranges.flatMap { r ->
                if (r.start < -0.01 && r.endInclusive > 0.01) listOf(r.start..0.0, 0.0..r.endInclusive) else listOf(r)
            }
            var blocks = false
            for (r in ranges) {
                if (r.endInclusive - r.start < 0.05) continue
                val slabBand = interior && r.endInclusive <= 0.01 && r.start < -0.01
                val covered = !slabBand && finish.covering != Covering.None && r.start < coverTop - 0.01
                val color = tint(if (slabBand) slabEdge else if (covered) coveringBase(finish) else paint, pick)
                val atTop = r.endInclusive >= h - 0.01
                val topColor = if (atTop) wallTop else color
                // Sopra un varco ad arco il bordo inferiore segue l'arco da un capo all'altro della fettina.
                val arch = covering.firstOrNull { it.curved && abs(it.topAt(mid) - r.start) < 0.01 }
                // Sotto un oblò il bordo superiore segue la metà bassa del cerchio.
                val under = covering.firstOrNull { it.round && abs(it.bottomAt(mid) - r.endInclusive) < 0.01 }
                if (under != null) {
                    slab(p0 + off, p1 + off, hw, r.start, r.start, under.bottomAt(c0), under.bottomAt(c1), color, pick, color)
                } else if (arch != null) {
                    val t0 = if (atTop && sloped) topFn(c0) else r.endInclusive
                    val t1 = if (atTop && sloped) topFn(c1) else r.endInclusive
                    slab(p0 + off, p1 + off, hw, arch.topAt(c0), arch.topAt(c1), t0, t1, color, pick, topColor)
                } else if (atTop && sloped) prism(p0 + off, p1 + off, hw, r.start, topFn(c0), topFn(c1), color, pick, topColor)
                else box(p0 + off, p1 + off, hw, r.start, r.endInclusive, color, pick, top = topColor)
                if (covered) {
                    // Il rivestimento segue i bordi inclinati della fettina: l'arco sotto, l'oblò o il soffitto in pendenza sopra.
                    val b0 = max(arch?.topAt(c0) ?: r.start, 0.0)
                    val b1 = max(arch?.topAt(c1) ?: r.start, 0.0)
                    val top0 = under?.bottomAt(c0) ?: if (atTop && sloped) topFn(c0) else r.endInclusive
                    val top1 = under?.bottomAt(c1) ?: if (atTop && sloped) topFn(c1) else r.endInclusive
                    coveringPattern(finish, s, u, side, half, c0, c1, b0, top0, pick, len, (room.id * 31 + i).toInt(), b1, top1)
                    // Listello in cima al rivestimento quando non arriva al soffitto.
                    if (abs(r.endInclusive - coverTop) < 0.01 && coverTop < h - 0.01) trim(finish, s, u, side, half, c0, c1, coverTop, pick)
                }
                // Battiscopa sulla faccia verso la stanza, dove il muro arriva a terra: anche sotto le finestre, non sotto le porte.
                if (interior && r.start < 0.01 && r.endInclusive > SKIRTING && !(covered && finish.covering in NO_SKIRTING)) {
                    val n = side * (half + 0.6)
                    box(p0 + n, p1 + n, 0.6, 0.0, SKIRTING, skirting, wallPick)
                }
                // Chi cammina urta i tratti di muro all'altezza del corpo (non sopra una porta, non un davanzale basso).
                if (r.start < 150 && r.endInclusive > 40) blocks = true
            }
            if (blocks && current) obstacles += Obstacle(p0, p1)
        }
    }

    /**
     * Muro singolo: colore (o rivestimento fino alla sua altezza) uguale sui due lati. Con porte e finestre: i tratti pieni tra
     * un'apertura e l'altra, sotto il davanzale e sopra l'architrave, e il modello dell'apertura (come nei muri delle stanze).
     */
    private fun freeWall(w: FreeWall, h: Double, pick: Pick) {
        if (w.openings.isEmpty()) return freeWallSolid(w, h, pick)
        val len = w.length
        val u = (w.end - w.start) / len
        for (r in Structure.solidRanges(w)) freeWallSolid(w.copy(start = w.start + u * r.start, end = w.start + u * r.endInclusive, openings = emptyList()), h, pick)
        val host = w.host
        val paint = tint(Rgba.argb(w.finishOrPaint.argb), pick)
        for (o in w.openings) {
            val (a, b) = Openings.span(host, o)
            val bottom = o.bottom
            val top = min(h - 1, bottom + o.height)
            if (bottom > 0.5) box(a, b, w.thickness / 2, wallBottom, bottom, paint, pick, top = paint)
            if (top < h - 0.5) box(a, b, w.thickness / 2, top, h, paint, pick, top = wallTop)
            openingModel(host, o)
        }
    }

    private fun freeWallSolid(w: FreeWall, h: Double, pick: Pick) {
        val f = w.finishOrPaint
        val paint = tint(Rgba.argb(f.argb), pick)
        val coverTop = min(f.coverHeight ?: h, h)
        if (f.covering == Covering.None || coverTop <= 0.5) {
            box(w.start, w.end, w.thickness / 2, wallBottom, h, paint, pick, top = wallTop)
            return
        }
        val full = coverTop >= h - 0.01
        box(w.start, w.end, w.thickness / 2, wallBottom, coverTop, tint(coveringBase(f), pick), pick, top = if (full) wallTop else paint)
        if (!full) box(w.start, w.end, w.thickness / 2, coverTop, h, paint, pick, top = wallTop)
        val u = (w.end - w.start) / w.length
        for (side in listOf(u.perp(), u.perp() * -1.0)) {
            coveringPattern(f, w.start, u, side, w.thickness / 2, 0.0, w.length, 0.0, coverTop, pick, w.length, w.id.toInt())
            if (!full) trim(f, w.start, u, side, w.thickness / 2, 0.0, w.length, coverTop, pick)
        }
    }

    // ---------- Rivestimenti delle pareti ----------

    /** Colore di fondo della parte rivestita: sotto mattoni e pietre si vede la malta. */
    private fun coveringBase(f: WallFinish): Rgba {
        val c = Rgba.argb(f.coverArgb)
        return when (f.covering) {
            Covering.Wallpaper -> c.mix(Rgba.White, 0.45f)
            Covering.Brick, Covering.Stone -> MORTAR
            // Posa a pezzi: tra un pezzo e l'altro si vede la fuga.
            Covering.Material -> f.layout.laying?.takeIf { MaterialPieces.canLay(f.material) }?.let { Rgba.argb(it.jointArgb) } ?: c
            else -> c
        }
    }

    /**
     * Rettangolo sulla faccia di un muro: lungo il muro da `t0` a `t1` (cm dall'inizio `s`, direzione `u`),
     * da `y0` a `y1` d'altezza, appena staccato dalla faccia che sta a `dist` dalla mezzeria dal lato `side`.
     */
    private fun wallFace(s: Vec2, u: Vec2, side: Vec2, dist: Double, t0: Double, t1: Double, y0: Double, y1: Double, color: Rgba, lift: Double = 0.15) {
        if (t1 - t0 < 0.05 || y1 - y0 < 0.05) return
        val off = side * (dist + lift)
        val a = s + u * t0 + off
        val b = s + u * t1 + off
        quad(a.at(y0), b.at(y0), b.at(y1), a.at(y1), side.dir(), color, null)
    }

    /** Listello in cima a piastrelle, boiserie e perlinato. */
    private fun trim(f: WallFinish, s: Vec2, u: Vec2, side: Vec2, dist: Double, t0: Double, t1: Double, y: Double, pick: Pick) {
        if (f.covering !in TRIMMED || t1 - t0 < 0.05) return
        val depth = if (f.covering == Covering.Wainscot) 1.4 else 0.6
        val c = tint(Rgba.argb(f.coverArgb).mix(Rgba(0f, 0f, 0f), 0.10f), pick)
        val n = side * (dist + depth / 2)
        box(s + u * t0 + n, s + u * t1 + n, depth / 2, y - 3.0, y, c, null)
    }

    /**
     * Disegno del rivestimento nel riquadro [t0, t1] × [y0, y1] della faccia: le misure partono dall'inizio
     * del muro e da terra, così i pezzi tra una finestra e l'altra restano allineati.
     */
    private fun coveringPattern(
        f: WallFinish, s: Vec2, u: Vec2, side: Vec2, dist: Double, t0: Double, t1: Double, y0: Double, y1: Double, pick: Pick,
        wallLength: Double, seed: Int,
        /**
         * Bordo basso e alto a `t1` (riquadro inclinato: fettina sopra un arco, sotto un oblò o sotto il soffitto in pendenza):
         * i pezzi si tagliano sui bordi inclinati, senza scalini né pezzi che sporgono.
         */
        y0End: Double = y0, y1End: Double = y1,
    ) {
        if (f.covering == Covering.Material) {
            f.material?.let { texturedWallFace(it, f.layout, s, u, side, dist, t0, t1, y0, y1, wallLength, seed, y0End, y1End) }
            return
        }
        val low = min(y0, y0End)
        val high = max(y1, y1End)
        if (t1 - t0 < 0.05 || high - low < 0.05) return
        // Parete enorme (misura sbagliata): i pezzi non si vedrebbero e sarebbero milioni, come le fughe dei pavimenti.
        if ((t1 - t0) * (high - low) > MAX_PATTERN_AREA) return
        val cover = Rgba.argb(f.coverArgb)
        val sloped = abs(y0End - y0) > 1e-6 || abs(y1End - y1) > 1e-6
        /** Bordi inclinati nel punto `t`: sopra quello basso, sotto quello alto (dove si incrociano non resta niente). */
        fun bottomAt(t: Double) = y0 + (y0End - y0) * (t - t0) / (t1 - t0)
        fun topAt(t: Double) = y1 + (y1End - y1) * (t - t0) / (t1 - t0)
        /** Riquadro tagliato sul riquadro da disegnare (sul trapezio, se i bordi sono inclinati). */
        fun cell(a0: Double, a1: Double, b0: Double, b1: Double, color: Rgba) {
            val lo = max(a0, t0)
            val hi = min(a1, t1)
            if (!sloped) {
                if (hi - lo < 0.05 || min(b1, y1) - max(b0, y0) < 0.05 || !detail()) return
                return wallFace(s, u, side, dist, lo, hi, max(b0, y0), min(b1, y1), tint(color, pick))
            }
            if (hi - lo < 0.05 || min(b1, high) - max(b0, low) < 0.05) return
            var q = listOf(Vec2(lo, b0), Vec2(hi, b0), Vec2(hi, b1), Vec2(lo, b1))
            q = clipAbove(q) { p -> p.y - bottomAt(p.x) }
            q = clipAbove(q) { p -> topAt(p.x) - p.y }
            if (q.size < 3 || abs(Polygon.signedArea(q)) < 0.01 || !detail(q.size - 2)) return
            val off = side * (dist + 0.15)
            val c = tint(color, pick)
            fun at(p: Vec2) = (s + u * p.x + off).at(p.y)
            for (k in 1 until q.size - 1) tri(at(q[0]), at(q[k]), at(q[k + 1]), side.dir(), c, null)
        }
        fun columnsIn(step: Double, shift: Double = 0.0) = (kotlin.math.floor((t0 - shift) / step).toInt() - 1)..(((t1 - shift) / step).toInt() + 1)
        fun rowsIn(step: Double) = max(0, (low / step).toInt() - 1)..((high / step).toInt() + 1)
        fun shade(c: Rgba, k: Double) = if (k >= 0) c.mix(Rgba.White, k.toFloat()) else c.mix(Rgba(0f, 0f, 0f), (-k).toFloat())
        when (f.covering) {
            Covering.None -> Unit
            Covering.Material -> Unit
            Covering.Wallpaper -> {
                // Righe verticali: una larga e una sottile ogni 16 cm.
                for (k in columnsIn(16.0)) {
                    cell(k * 16.0, k * 16.0 + 5.0, low, high, cover)
                    cell(k * 16.0 + 9.0, k * 16.0 + 10.0, low, high, cover)
                }
            }
            Covering.Tiles, Covering.Metro -> {
                val grout = cover.mix(Rgba(0.55f, 0.55f, 0.53f), 0.5f)
                val (w, h) = if (f.covering == Covering.Tiles) 20.0 to 20.0 else 15.0 to 7.5
                for (r in rowsIn(h)) {
                    cell(t0, t1, r * h - 0.2, r * h + 0.2, grout)
                    // Diamantate: file sfalsate di mezza piastrella.
                    val shift = if (f.covering == Covering.Metro && r % 2 == 1) w / 2 else 0.0
                    for (k in columnsIn(w, shift)) cell(k * w + shift - 0.2, k * w + shift + 0.2, r * h, (r + 1) * h, grout)
                    if (f.covering == Covering.Metro) {
                        // Bordo smussato: una striscia chiara in alto su ogni fila.
                        cell(t0, t1, (r + 1) * h - 1.2, (r + 1) * h - 0.2, shade(cover, 0.35))
                    }
                }
            }
            Covering.Boards -> {
                val joint = cover.mix(Rgba(0f, 0f, 0f), 0.3f)
                for (k in columnsIn(10.0)) {
                    cell(k * 10.0 - 0.3, k * 10.0 + 0.3, low, high, joint)
                    cell(k * 10.0 + 0.3, k * 10.0 + 1.5, low, high, shade(cover, 0.12))
                }
            }
            Covering.Wainscot -> {
                // Pannelli in rilievo larghi 60 cm, dal battiscopa a poco sotto il listello.
                val top = (f.coverHeight ?: y1) - 12.0
                val frame = cover.mix(Rgba(0f, 0f, 0f), 0.14f)
                val panel = cover.mix(Rgba(0f, 0f, 0f), 0.04f)
                if (top > 30) for (k in columnsIn(60.0)) {
                    val a = k * 60.0 + 8.0
                    val b = k * 60.0 + 52.0
                    cell(a, b, 18.0, top, panel)
                    cell(a, b, 18.0, 19.2, frame)
                    cell(a, b, top - 1.2, top, frame)
                    cell(a, a + 1.2, 18.0, top, frame)
                    cell(b - 1.2, b, 18.0, top, frame)
                }
            }
            Covering.Brick -> {
                // Mattoni 24 × 6,2 cm con giunti di 1 cm, sfalsati di mezzo mattone da una fila all'altra.
                for (r in rowsIn(7.2)) {
                    val shift = if (r % 2 == 1) 12.5 else 0.0
                    for (k in columnsIn(25.0, shift)) {
                        val a = k * 25.0 + shift + 0.5
                        cell(a, a + 24.0, r * 7.2 + 0.5, r * 7.2 + 6.7, shade(cover, (hash(r, k) - 0.5) * 0.22))
                    }
                }
            }
            Covering.Stone -> {
                // Conci di pietra di altezze e lunghezze diverse (sempre le stesse per lo stesso muro).
                var yb = 0.0
                var r = 0
                while (yb < high) {
                    val rh = 14.0 + hash(r, 999) * 12.0
                    if (yb + rh > low) {
                        var ta = -60.0 - hash(r, -1) * 30.0
                        var k = 0
                        while (ta < t1) {
                            val len = 24.0 + hash(r, k) * 30.0
                            if (ta + len > t0) cell(ta + 0.8, ta + len - 0.8, yb + 0.8, yb + rh - 0.8, shade(cover, (hash(k, r) - 0.5) * 0.3))
                            ta += len
                            k++
                        }
                    }
                    yb += rh
                    r++
                }
            }
        }
    }

    /** Parte del poligono convesso `poly` dove `f` (lineare) è ≥ 0. */
    private fun clipAbove(poly: List<Vec2>, f: (Vec2) -> Double): List<Vec2> = buildList {
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val fp = f(p)
            val fq = f(q)
            if (fp >= 0) add(p)
            if ((fp >= 0) != (fq >= 0)) add(p + (q - p) * (fp / (fp - fq)))
        }
    }

    /** Numero pseudo-casuale in 0..1, sempre uguale per la stessa coppia. */
    private fun hash(a: Int, b: Int): Double {
        var h = a * 73856093 xor b * 19349663
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535.0
    }

    /** Parapetto di balconi e terrazze sul tratto p0→p1: ringhiera, muretto o vetro. */
    private fun parapet(room: Room, p0: Vec2, p1: Vec2, pick: Pick) {
        val h = room.parapetHeight
        val metal = tint(railColor, pick)
        when (room.parapet) {
            Parapet.Wall -> box(p0, p1, half, wallBottom, h, tint(wallSide, pick), pick, top = wallTop)
            Parapet.Railing -> {
                box(p0, p1, 2.5, h - 5, h, metal, pick) // corrimano
                box(p0, p1, 1.5, 3.0, 7.0, metal, pick) // traverso in basso
                val len = p0.distanceTo(p1)
                if (len < 1e-6) return
                val u = (p1 - p0) / len
                // Montanti ogni 12 cm; su una ringhiera lunghissima (una misura scritta per sbaglio, es. in metri invece che in
                // cm) più radi, altrimenti milioni di montanti finivano la memoria.
                val step = max(12.0, len / MAX_RAILING_POSTS)
                var t = step / 2
                while (t < len) {
                    val c = p0 + u * t
                    if (detail(6)) box(c - u * 0.8, c + u * 0.8, 0.8, 7.0, h - 5, metal, pick)
                    t += step
                }
            }
            Parapet.Glass -> {
                box(p0, p1, 3.0, wallBottom, 6.0, tint(wallSide, pick), pick)
                pane(p0, p1, 6.0, h - 5, tint(glass, pick).copy(a = glass.a), pick)
                box(p0, p1, 2.5, h - 5, h, metal, pick)
            }
        }
    }

    /** Intervallo `a` meno l'intervallo `b` (0, 1 o 2 pezzi). */
    private fun cut(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>): List<ClosedFloatingPointRange<Double>> {
        if (b.endInclusive <= a.start || b.start >= a.endInclusive) return listOf(a)
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        if (b.start > a.start) out += a.start..b.start
        if (b.endInclusive < a.endInclusive) out += b.endInclusive..a.endInclusive
        return out
    }

    // ---------- Porte, finestre, balconi ----------

    private fun openingModel(room: Room, o: Opening) {
        half = room.thicknessOf(o.wallIndex) / 2
        try { openingBody(room, o) } finally { half = Room.WALL_THICKNESS / 2 }
    }

    private fun openingBody(room: Room, o: Opening) {
        val (a, b) = Openings.span(room, o)
        val w = a.distanceTo(b)
        if (w < 1.0) return
        val u = (b - a) / w
        val n = Openings.inwardNormal(room, o.wallIndex)
        // Sul lato di un balcone che confina con la casa conta il muro della casa, non il parapetto.
        val h = (if (room.outdoor) Ceilings.sharedWallTop(plan, room, o.wallIndex, (a + b) / 2.0)
        else Ceilings.wallTopAt(room, o.wallIndex, Openings.projectOnWall(room, o.wallIndex, (a + b) / 2.0))) - 1
        val pick = Pick.Opening(room.id, o.id)
        val bottom = o.bottom
        val top = min(h, bottom + o.height)
        // Soglia della porta (quella di finestre e balconi si fa con l'infisso, che può stare spostato nello spessore del muro).
        if (!o.kind.glazed) o.sill?.let { windowSill(room, o, a, b, u, n, bottom, it, pick) }

        when {
            o.kind == OpeningKind.Passage || o.kind == OpeningKind.Niche -> Unit // varco libero, nicchia
            o.kind.glazed && o.isBay -> bayWindow(room, o, a, b, n, bottom, top, pick)
            o.kind.glazed -> {
                // Infisso a filo esterno o interno: spostato nello spessore del muro.
                val f = n * Openings.frameOffset(room, o)
                window(a + f, b + f, u, n, bottom, top, o, pick)
                // Oscuranti sulle facce del muro, anche con l'infisso spostato a filo esterno o interno.
                if (o.shape == WindowShape.Rectangle && !o.isGlassBlock) shading(a, b, u, n, bottom, top, o, pick)
                o.sill?.let { windowSill(room, o, a, b, u, n, bottom, it, pick) }
                if (o.hasGrille) grille(a, b, u, n * -(room.thicknessOf(o.wallIndex) / 2 + 4.0), bottom, top, pick)
                if (o.hasInsectScreen) insectScreen(a, b, u, n, bottom, top, o, pick)
                if (o.hasAwning && !room.outdoor) awning(a, b, u, n, top, h, o, pick)
            }
            o.isGarage -> garageDoor(a, b, u, n, bottom, top, h, o, pick)
            o.sliding && o.slideOnWall -> {
                // Porta scorrevole esterna: anta sulla faccia del muro verso la stanza, aperta a metà sopra il muro,
                // appesa a un binario a vista che corre per tutta la sua corsa.
                casing(a, b, u, n, top, o, pick)
                val face = n * (half + 3.2)
                val start = if (o.hingeLeft) a - u * (w / 2 + 3) else b - u * (w / 2 + 3)
                doorLeaf(start + face, u, w + 6, 2.0, top + 2, o, pick, handleAtEnd = !o.hingeLeft)
                val metal = tint(Rgba(0.2f, 0.21f, 0.22f), pick)
                val (r0, r1) = if (o.hingeLeft) (a - u * (w + 6)) to (b + u * 6.0) else (a - u * 6.0) to (b + u * (w + 6))
                box(r0 + face, r1 + face, 1.6, top + 3, top + 7, metal, pick)
            }
            o.sliding -> {
                // Porta scorrevole: anta nel muro, aperta a metà verso il lato scelto.
                casing(a, b, u, n, top, o, pick)
                val off = n * (Room.WALL_THICKNESS / 4)
                val start = if (o.hingeLeft) a - u * (w / 2) else b - u * (w / 2)
                doorLeaf(start + off, u, w, 1.5, top - 1, o, pick, handleAtEnd = !o.hingeLeft)
            }
            o.folds -> {
                // Porta a libro: ogni anta è divisa in due pannelli piegati a V, aperti a metà verso il lato di apertura.
                casing(a, b, u, n, top, o, pick)
                val swing = if (o.opensInward) n else n * -1.0
                val ang = kotlin.math.PI / 3
                fun fold(hinge: Vec2, along: Vec2, length: Double) {
                    val p = length / 2
                    val d1 = along * kotlin.math.cos(ang) + swing * kotlin.math.sin(ang)
                    val start = hinge + swing * (half - 1.5)
                    val mid = start + d1 * p
                    val d2 = along * kotlin.math.cos(ang) - swing * kotlin.math.sin(ang)
                    doorLeaf(start, d1, p, 1.5, top - 1, o.copy(doorModel = foldModel(o.doorModel)), pick, handleAtEnd = false)
                    doorLeaf(mid, d2, p, 1.5, top - 1, o.copy(doorModel = foldModel(o.doorModel)), pick, handleAtEnd = true)
                }
                if (o.kind.leaves == 2) {
                    fold(a, u, w / 2)
                    fold(b, u * -1.0, w / 2)
                } else if (o.hingeLeft) fold(a, u, w) else fold(b, u * -1.0, w)
            }
            o.pivots -> {
                // Porta a bilico: aperta a 90°, attraversa il varco sul perno a un quinto della larghezza.
                if (!o.frameless) casing(a, b, u, n, top, o, pick)
                val (la, lb, lt) = doorGlazing(a, u, w, n, top, o, pick)
                val lw = la.distanceTo(lb)
                val swing = if (o.opensInward) n else n * -1.0
                val axis = if (o.hingeLeft) la + u * (lw * Openings.PIVOT_AT) else lb - u * (lw * Openings.PIVOT_AT)
                doorLeaf(axis - swing * (lw * Openings.PIVOT_AT), swing, lw, 2.0, lt - 1, o, pick, handleAtEnd = true)
            }
            else -> {
                // Porta a battente: aperta a 90° verso il lato di apertura, come nel simbolo della pianta.
                // Filo muro: niente coprifilo, l'anta sta a filo della faccia.
                if (!o.frameless) casing(a, b, u, n, top, o, pick)
                val (la, lb, lt) = doorGlazing(a, u, w, n, top, o, pick)
                val lw = la.distanceTo(lb)
                val swing = if (o.opensInward) n else n * -1.0
                fun leafFrom(hinge: Vec2, length: Double) = doorLeaf(hinge + swing * half, swing, length, 2.0, lt - 1, o, pick, handleAtEnd = true)
                if (o.kind.leaves == 2) {
                    val split = w * o.leafSplit
                    leafFrom(a, split)
                    leafFrom(b, w - split)
                } else {
                    leafFrom(if (o.hingeLeft) la else lb, lw)
                }
            }
        }
    }

    /**
     * Porta da garage, chiusa, a filo della faccia del muro verso la stanza (come si montano). Sezionale: pannelli
     * orizzontali con le guide ai lati e i binari che entrano nella stanza sotto il soffitto. Basculante: pannello unico a
     * doghe verticali con la maniglia in mezzo e i montanti ai lati. Bianca finché non si sceglie un colore.
     */
    private fun garageDoor(a: Vec2, b: Vec2, u: Vec2, n: Vec2, bottom: Double, top: Double, ceiling: Double, o: Opening, pick: Pick) {
        val w = a.distanceTo(b)
        // Con la finitura in foto ([Opening.doorFinish]) il pannello ha il colore medio del materiale e la foto sulle due facce.
        val material = o.doorFinish?.let { MaterialCatalog.item(it) }
        val c = tint(Rgba.argb(material?.argb ?: o.frameArgb), pick)
        val groove = c.mix(Rgba(0f, 0f, 0f), 0.25f)
        val metal = tint(Rgba(0.45f, 0.46f, 0.48f), pick)
        val face = n * (half - 2.0)
        box(a + face, b + face, 1.5, bottom, top, c, pick)
        if (material != null) for (side in listOf(1.0, -1.0)) {
            val off = face + n * (side * 1.55)
            skin(material, o.doorMaterialLayout, a + off, b + off, n * side, bottom, top)
        }
        val outer = n * (half - 3.6)
        if (o.garageDoor == GarageDoor.Sectional) {
            // Giunti tra i pannelli, visibili dai due lati.
            val panels = maxOf(3, kotlin.math.round((top - bottom) / 52.0).toInt())
            for (k in 1 until panels) {
                val y = bottom + (top - bottom) * k / panels
                box(a + face, b + face, 1.8, y - 0.6, y + 0.6, groove, pick)
            }
            // Guide ai lati e binari sotto il soffitto, fin dove arriva il pannello aperto.
            val reach = Openings.garageReach(o)
            val railTop = min(ceiling - 1, top + 12)
            val inner = n * half
            for (side in listOf(a + u * 4.0, b - u * 4.0)) {
                box(side + inner, side + inner + n * 5.0, 2.0, bottom, railTop, metal, pick)
                box(side + inner, side + inner + n * reach, 2.0, railTop - 5, railTop, metal, pick)
            }
            box(a + u * 4.0 + inner + n * reach, b - u * 4.0 + inner + n * reach, 1.5, railTop - 5, railTop, metal, pick)
        } else {
            // Doghe verticali verso fuori e maniglia a metà.
            val ribs = maxOf(4, kotlin.math.round(w / 14.0).toInt())
            for (k in 1 until ribs) {
                val p = a + u * (w * k / ribs) + outer
                box(p - u * 0.6, p + u * 0.6, 0.6, bottom + 3, top - 3, groove, pick)
            }
            val m = (a + b) / 2.0
            val y = bottom + min(100.0, (top - bottom) / 2)
            box(m - u * 8.0 + outer - n * 0.8, m + u * 8.0 + outer - n * 0.8, 1.0, y - 1.5, y + 1.5, metal, pick)
            // Montanti ai lati verso la stanza, con i contrappesi.
            val inner = n * (half + 3.0)
            for (side in listOf(a + u * 3.0, b - u * 3.0)) box(side + inner - u * 2.5, side + inner + u * 2.5, 3.0, bottom, top, metal, pick)
        }
    }

    /**
     * Sopraluce e vetro di fianco della porta: vetri fissi con il telaio del colore della porta, a metà muro.
     * Restituisce il tratto e la cima che restano all'anta.
     */
    private fun doorGlazing(a: Vec2, u: Vec2, w: Double, n: Vec2, top: Double, o: Opening, pick: Pick): Triple<Vec2, Vec2, Double> {
        val r = o.leafRange
        val la = a + u * (w * r.start)
        val lb = a + u * (w * r.endInclusive)
        val transom = o.transomCm
        val side = o.sidelightCm
        if (transom <= 0 && side <= 0) return Triple(la, lb, top)
        val frame = tint(Rgba.argb(o.doorFinish?.let { MaterialCatalog.item(it)?.argb } ?: o.frameArgb), pick)
        val glassColor = glassOf(o, pick)
        val lt = top - transom
        val f = 3.0
        if (transom > 0) {
            box(a, a + u * w, 3.0, lt - f, lt, frame, pick) // traverso tra anta e sopraluce
            pane(a, a + u * w, lt, top, glassColor, pick)
        }
        if (side > 0) {
            val (g0, g1) = if (o.hingeLeft) lb to (a + u * w) else a to la
            val mull = if (o.hingeLeft) lb else la
            box(mull - u * (f / 2), mull + u * (f / 2), 3.0, 0.0, lt, frame, pick) // montante tra anta e vetro
            box(g0, g1, 3.0, 0.0, 10.0, frame, pick) // zoccolo sotto il vetro
            pane(g0, g1, 10.0, lt - f, glassColor, pick)
        }
        return Triple(la, lb, lt)
    }

    /** I pannelli della porta a libro sono stretti: i riquadri delle pannellate diventano lisci. */
    private fun foldModel(m: DoorModel) = if (m == DoorModel.Panels) DoorModel.Flush else m

    /**
     * Anta di una porta, dal punto `start` lungo `dir` per `length` cm, spessa 2 × `thick`, alta `height`,
     * con il disegno del suo modello su entrambe le facce e la maniglia verso l'estremità libera.
     */
    private fun doorLeaf(start: Vec2, dir: Vec2, length: Double, thick: Double, height: Double, o: Opening, pick: Pick, handleAtEnd: Boolean) {
        // Con la finitura in foto ([Opening.doorFinish]) l'anta ha il colore medio del materiale e, sopra, la sua foto.
        val material = o.doorFinish?.let { MaterialCatalog.item(it) }
        val color = tint(Rgba.argb(material?.argb ?: o.frameArgb), pick)
        val relief = color.mix(Rgba(0f, 0f, 0f), 0.12f)
        val metal = tint(Rgba(0.62f, 0.64f, 0.66f), pick)
        val across = dir.perp()
        /** Un pezzo dell'anta; `skinned`: con la foto del materiale sulle due facce, appena fuori. */
        fun part(from: Double, to: Double, y0: Double, y1: Double, hw: Double, c: Rgba, skinned: Boolean = false) {
            box(start + dir * (length * from), start + dir * (length * to), hw, y0, y1, c, pick)
            if (skinned && material != null) for (side in listOf(1.0, -1.0)) {
                val off = across * (side * (hw + 0.05))
                skin(material, o.doorMaterialLayout, start + dir * (length * from) + off, start + dir * (length * to) + off, across * side, y0, y1, length * from)
            }
        }
        when (o.doorModel) {
            DoorModel.Flush -> part(0.0, 1.0, 0.0, height, thick, color, skinned = true)
            DoorModel.Panels -> {
                part(0.0, 1.0, 0.0, height, thick, color, skinned = true)
                // Riquadri in rilievo: uno alto e uno basso, su tutte e due le facce.
                part(0.16, 0.84, height * 0.55, height * 0.9, thick + 0.6, relief, skinned = true)
                part(0.16, 0.84, height * 0.1, height * 0.46, thick + 0.6, relief, skinned = true)
            }
            DoorModel.Glazed -> {
                // Telaio dell'anta e vetro al centro.
                part(0.0, 0.16, 0.0, height, thick, color, skinned = true)
                part(0.84, 1.0, 0.0, height, thick, color, skinned = true)
                part(0.16, 0.84, 0.0, height * 0.36, thick, color, skinned = true)
                part(0.16, 0.84, height * 0.88, height, thick, color, skinned = true)
                pane(start + dir * (length * 0.16), start + dir * (length * 0.84), height * 0.36, height * 0.88, glassOf(o, pick), pick)
            }
            DoorModel.Planks -> {
                part(0.0, 1.0, 0.0, height, thick, color, skinned = true)
                for (k in 1 until 5) part(k / 5.0 - 0.006, k / 5.0 + 0.006, height * 0.02, height * 0.98, thick + 0.25, relief)
            }
            DoorModel.Modern -> {
                part(0.0, 1.0, 0.0, height, thick, color, skinned = true)
                for (y in listOf(0.3, 0.55, 0.8)) part(0.04, 0.96, height * y - 0.4, height * y + 0.4, thick + 0.25, relief)
            }
            DoorModel.AllGlass -> {
                // Lastra di vetro satinato (un velo sul computer, vedi PbrShaders) con le due cerniere dal lato opposto alla maniglia e la
                // serratura di metallo.
                pane(start, start + dir * length, 0.5, height, tint(satinGlass, pick).copy(a = satinGlass.a), pick)
                val hinge = if (handleAtEnd) 0.0 else 0.94
                for (y in listOf(20.0, height - 32.0)) part(hinge, hinge + 0.06, y, y + 12.0, thick * 0.4 + 0.8, metal)
                val lockAt = if (handleAtEnd) 0.84 else 0.04
                part(lockAt, lockAt + 0.12, 92.0, 110.0, thick * 0.4 + 0.6, metal)
            }
            DoorModel.Armored -> {
                // Anta più spessa, bugne quadrate in rilievo, serratura sopra la maniglia e spioncino.
                part(0.0, 1.0, 0.0, height, thick + 1.5, color, skinned = true)
                for (row in 0 until 3) {
                    val y0 = height * (0.08 + row * 0.3)
                    part(0.12, 0.88, y0, y0 + height * 0.24, thick + 2.1, relief, skinned = true)
                }
                val lockAt = if (handleAtEnd) 0.86 else 0.14
                part(lockAt - 0.02, lockAt + 0.02, 120.0, 128.0, thick + 2.4, metal)
                part(0.48, 0.52, height * 0.76, height * 0.77 + 2.0, thick + 2.2, metal)
            }
        }
        // Maniglia (la porta moderna ha il maniglione verticale).
        val at = if (handleAtEnd) 0.88 else 0.12
        val grip = if (o.doorModel == DoorModel.Armored) thick + 4.0 else thick + 2.5
        if (o.doorModel == DoorModel.Modern) part(at - 0.015, at + 0.015, height * 0.38, height * 0.78, grip, metal)
        else part(if (handleAtEnd) 0.8 else 0.07, if (handleAtEnd) 0.93 else 0.2, 102.0, 104.5, grip, metal)
    }

    /** Coprifilo attorno alla porta, sulle due facce del muro, e rivestimento dello spessore del varco. */
    private fun casing(a: Vec2, b: Vec2, u: Vec2, n: Vec2, top: Double, o: Opening, pick: Pick) {
        // Con la finitura in foto della porta, il coprifilo è dello stesso materiale (la foto sulla faccia davanti).
        val material = o.doorFinish?.let { MaterialCatalog.item(it) }
        val color = if (material != null) tint(Rgba.argb(material.argb), pick) else tint(Rgba.argb(o.frameArgb), pick).mix(Rgba.White, 0.08f)
        val trim = 7.0
        for (side in listOf(1.0, -1.0)) {
            val off = n * (side * (half + 0.6))
            box(a - u * trim + off, a + off, 0.6, 0.0, top + trim, color, pick)
            box(b + off, b + u * trim + off, 0.6, 0.0, top + trim, color, pick)
            box(a - u * trim + off, b + u * trim + off, 0.6, top, top + trim, color, pick)
            if (material != null) {
                // I montanti fino all'architrave e l'architrave per tutta la larghezza: le foto non si sovrappongono negli angoli.
                val face = n * (side * (half + 1.25))
                val w = a.distanceTo(b)
                skin(material, o.doorMaterialLayout, a - u * trim + face, a + face, n * side, 0.0, top, -trim)
                skin(material, o.doorMaterialLayout, b + face, b + u * trim + face, n * side, 0.0, top, w)
                skin(material, o.doorMaterialLayout, a - u * trim + face, b + u * trim + face, n * side, top, top + trim, -trim)
            }
        }
        box(a, a + u * 1.2, half, 0.0, top, color, pick)
        box(b - u * 1.2, b, half, 0.0, top, color, pick)
        box(a, b, half, top - 1.2, top, color, pick)
    }

    /** Finestra o balcone: telaio bianco e vetro; due ante con il montante centrale, scorrevoli sfalsate. */
    private fun window(a: Vec2, b: Vec2, u: Vec2, n: Vec2, bottom: Double, top: Double, o: Opening, pick: Pick) {
        if (current) windows += Scene3D.SceneWindow(((a + b) / 2.0).at((bottom + top) / 2), n.dir().normalized(), a.distanceTo(b), top - bottom)
        if (o.isGlassBlock) return glassBlocks(a, b, u, n, bottom, top, pick)
        if (o.shape != WindowShape.Rectangle) return shapedWindow(a, b, u, bottom, top, o, pick)
        val minimal = o.windowModel == WindowModel.Minimal
        val loft = o.windowModel == WindowModel.Loft
        val wood = o.windowModel == WindowModel.Wood
        val f = profileWidth(o)
        val depth = if (minimal || loft) 3.0 else if (wood) 5.0 else 4.0
        val color = windowFrameColor(o, pick)
        val w = a.distanceTo(b)
        val split = w * o.leafSplit
        val glassBottom = bottom + if (bottom > 0) f else 2.0
        // Sopraluce: un traverso divide le ante (sotto) da un vetro fisso alto circa un quarto della finestra (sopra).
        val leafTop = if (o.hasTopLight) top - f - ((top - f - glassBottom) * 0.28).coerceIn(25.0, 50.0) else top - f
        // Ante (tratti lungo il varco, in cm da `a`): una, due (anche disuguali), tre o quattro uguali, nessuna divisione se fissa.
        val leaves = when {
            o.sliding -> emptyList()
            o.fixed -> listOf(f to w - f)
            o.leaves == 2 -> listOf(f to split, split to w - f)
            o.leaves > 2 -> (0 until o.leaves).map { k -> maxOf(f, w * k / o.leaves) to minOf(w - f, w * (k + 1) / o.leaves) }
            else -> listOf(f to w - f)
        }
        if (o.windowModel == WindowModel.English && !o.sliding) {
            // Inglesina: griglia di listelli sul vetro di ogni anta.
            for ((x0, x1) in leaves) {
                for (k in 1..2) {
                    val x = x0 + (x1 - x0) * k / 3
                    box(a + u * (x - 0.8), a + u * (x + 0.8), 1.2, glassBottom, leafTop, color, pick)
                }
                for (k in 1..2) {
                    val y = glassBottom + (leafTop - glassBottom) * k / 3
                    box(a + u * x0, a + u * x1, 1.2, y - 0.8, y + 0.8, color, pick)
                }
            }
        }
        if (loft && !o.sliding) {
            // Loft: vetro diviso in riquadri di circa 45 × 50 cm da profili neri.
            for ((x0, x1) in leaves) {
                val cols = maxOf(1, kotlin.math.round((x1 - x0) / 45.0).toInt())
                val rows = maxOf(1, kotlin.math.round((leafTop - glassBottom) / 50.0).toInt())
                for (k in 1 until cols) {
                    val x = x0 + (x1 - x0) * k / cols
                    box(a + u * (x - 0.8), a + u * (x + 0.8), 1.5, glassBottom, leafTop, color, pick)
                }
                for (k in 1 until rows) {
                    val y = glassBottom + (leafTop - glassBottom) * k / rows
                    box(a + u * x0, a + u * x1, 1.5, y - 0.8, y + 0.8, color, pick)
                }
            }
        }
        val glassColor = glassOf(o, pick)
        box(a, a + u * f, depth, bottom, top, color, pick)
        box(b - u * f, b, depth, bottom, top, color, pick)
        box(a, b, depth, top - f, top, color, pick)
        box(a, b, depth, bottom, bottom + if (bottom > 0) f else 2.0, color, pick)
        // Alzante: la soglia e il traverso in basso più alti e robusti.
        if (o.lifts) box(a, b, depth + 1.0, bottom, glassBottom + 4.0, color, pick)
        if (o.sliding) {
            // Ante scorrevoli sfalsate nello spessore (una sì e una no), sovrapposte dove si incontrano.
            val count = maxOf(2, o.leaves)
            val overlap = w * 0.08 / (count - 1)
            val low = if (o.lifts) glassBottom + 4.0 else glassBottom
            for (k in 0 until count) {
                val x0 = maxOf(f, w * k / count - if (k > 0) overlap else 0.0)
                val x1 = minOf(w - f, w * (k + 1) / count + if (k < count - 1) overlap else 0.0)
                val off = n * (if (k % 2 == 0) 2.5 else -2.5)
                pane(a + u * x0 + off, a + u * x1 + off, low, top - f, glassColor, pick)
                if (k > 0) box(a + u * x0 + off, a + u * (x0 + 2.0) + off, 1.5, low, top - f, color, pick)
                if (k < count - 1) box(a + u * (x1 - 2.0) + off, a + u * x1 + off, 1.5, low, top - f, color, pick)
            }
        } else {
            pane(a + u * f, b - u * f, glassBottom, top - f, glassColor, pick)
            // Montanti tra un'anta e l'altra (sotto il traverso del sopraluce, se c'è).
            for (k in 1 until leaves.size) {
                val mid = a + u * leaves[k].first
                box(mid - u * 2.5, mid + u * 2.5, depth, glassBottom, leafTop, color, pick)
            }
            if (o.hasTopLight) box(a, b, depth, leafTop - 2.5, leafTop + 2.5, color, pick)
            // Legno: il fermavetro, listelli sottili verso la stanza attorno al vetro di ogni anta (e del sopraluce).
            if (wood) {
                val bead = color.mix(Rgba(0f, 0f, 0f), 0.10f)
                val inner = n * (depth * 0.55)
                val panes = leaves.map { (x0, x1) -> Triple(x0 + if (x0 > f) 2.5 else 0.0, x1 - if (x1 < w - f) 2.5 else 0.0, glassBottom to leafTop - if (o.hasTopLight) 2.5 else 0.0) } +
                    if (o.hasTopLight) listOf(Triple(f, w - f, leafTop + 2.5 to top - f)) else emptyList()
                for ((x0, x1, ys) in panes) {
                    val (y0, y1) = ys
                    box(a + u * x0 + inner, a + u * (x0 + 1.6) + inner, 1.0, y0, y1, bead, pick)
                    box(a + u * (x1 - 1.6) + inner, a + u * x1 + inner, 1.0, y0, y1, bead, pick)
                    box(a + u * x0 + inner, a + u * x1 + inner, 1.0, y0, y0 + 1.6, bead, pick)
                    box(a + u * x0 + inner, a + u * x1 + inner, 1.0, y1 - 1.6, y1, bead, pick)
                }
            }
            // A bilico: il perno orizzontale a metà altezza, ai due lati.
            if (o.pivotSash) {
                val y = (glassBottom + leafTop) / 2
                val metal = tint(Rgba(0.62f, 0.64f, 0.66f), pick)
                for (x in listOf(f + 1.5, w - f - 1.5)) box(a + u * (x - 1.5), a + u * (x + 1.5), depth + 0.6, y - 1.5, y + 1.5, metal, pick)
            }
            // Maniglia sul lato libero dell'anta (in cima per il vasistas); il vetro fisso non ne ha.
            if (!o.fixed && (top - bottom) > 30) {
                val metal = tint(Rgba(0.62f, 0.64f, 0.66f), pick)
                val inner = n * (depth + 1.2)
                if (o.hopper || o.pivotSash) {
                    val c = (a + b) / 2.0 + inner
                    box(c - u * 6.0, c + u * 6.0, 1.0, leafTop - 4, leafTop - 2, metal, pick)
                } else {
                    val y = min(bottom + 105.0, (bottom + top) / 2).coerceAtLeast(glassBottom + 5)
                    val xs = when {
                        leaves.size > 2 -> leaves.mapIndexed { k, (x0, x1) -> if (k * 2 < leaves.size) x1 - 4 else x0 + 4 }
                        leaves.size == 2 -> listOf(split - 5.0)
                        o.hingeLeft -> listOf(w - f - 4)
                        else -> listOf(f + 4)
                    }
                    for (x in xs) box(a + u * (x - 1) + inner, a + u * (x + 1) + inner, 1.0, y - 7, y + 7, metal, pick)
                }
            }
        }
    }

    /**
     * Vetromattone: parete di blocchi di vetro di circa 19 × 19 cm e 8 di spessore murati nel vano, senza telaio. Tra un blocco
     * e l'altro e attorno le fughe di malta chiara, un poco più indietro delle facce dei blocchi; le facce sono vetro
     * smerigliato, che lascia passare la luce ma non la vista.
     */
    private fun glassBlocks(a: Vec2, b: Vec2, u: Vec2, n: Vec2, bottom: Double, top: Double, pick: Pick) {
        val w = a.distanceTo(b)
        val h = top - bottom
        if (w < 4 || h < 4) return
        val joint = 1.0
        val cols = maxOf(1, kotlin.math.round(w / Openings.GLASS_BLOCK).toInt())
        val rows = maxOf(1, kotlin.math.round(h / Openings.GLASS_BLOCK).toInt())
        // Passo dei blocchi: le fughe stanno a 0, bw, 2·bw… e l'ultima finisce sul bordo del vano.
        val bw = (w - joint) / cols
        val bh = (h - joint) / rows
        val mortar = tint(Rgba(0.83f, 0.82f, 0.79f), pick)
        for (k in 0..cols) box(a + u * (bw * k), a + u * (bw * k + joint), 3.6, bottom, top, mortar, pick)
        for (k in 0..rows) box(a, b, 3.6, bottom + bh * k, bottom + bh * k + joint, mortar, pick)
        // Ogni faccia: il centro smerigliato e, attorno, il bordo spesso del blocco più bianco e meno trasparente.
        val face = tint(frostedGlass, pick).copy(a = frostedGlass.a)
        val rim = tint(glassBlockRim, pick).copy(a = glassBlockRim.a)
        val e = min(2.0, min(bw, bh) / 5)
        for (i in 0 until cols) for (j in 0 until rows) {
            val x0 = bw * i + joint
            val x1 = bw * (i + 1)
            val y0 = bottom + bh * j + joint
            val y1 = bottom + bh * (j + 1)
            for (side in listOf(4.0, -4.0)) {
                val d = n * side
                pane(a + u * (x0 + e) + d, a + u * (x1 - e) + d, y0 + e, y1 - e, face, pick)
                pane(a + u * x0 + d, a + u * (x0 + e) + d, y0, y1, rim, pick)
                pane(a + u * (x1 - e) + d, a + u * x1 + d, y0, y1, rim, pick)
                pane(a + u * (x0 + e) + d, a + u * (x1 - e) + d, y0, y0 + e, rim, pick)
                pane(a + u * (x0 + e) + d, a + u * (x1 - e) + d, y1 - e, y1, rim, pick)
            }
        }
    }

    private val frostedGlass = Rgba(0.80f, 0.88f, 0.91f, 0.62f)
    private val glassBlockRim = Rgba(0.90f, 0.94f, 0.95f, 0.85f)

    /** Larghezza dei profili dell'infisso (cm): sottili minimal e loft, larghi quelli in legno. */
    private fun profileWidth(o: Opening): Double = when (o.windowModel) {
        WindowModel.Minimal, WindowModel.Loft -> 3.0
        WindowModel.Wood -> 7.0
        else -> 5.0
    }

    /**
     * Colore dell'infisso: quello scelto; senza scelta minimal antracite, loft nero e legno rovere, come di solito sono
     * (gli altri bianchi).
     */
    private fun windowFrameColor(o: Opening, pick: Pick): Rgba = when {
        o.windowModel == WindowModel.Loft && o.defaultColor -> tint(Rgba(0.1f, 0.1f, 0.11f), pick)
        o.windowModel == WindowModel.Minimal && o.defaultColor -> tint(Rgba.argb(Finish.Anthracite.argb), pick)
        o.windowModel == WindowModel.Wood && o.defaultColor -> tint(Rgba.argb(Finish.Oak.argb), pick)
        else -> tint(Rgba.argb(o.frameArgb), pick)
    }

    private val wroughtIron = Rgba(0.13f, 0.13f, 0.14f)

    /**
     * Davanzale (finestre) o soglia (balconi e porte): lastra di 3 cm sotto l'infisso per tutto lo spessore del muro, che sporge
     * [Openings.SILL_IN] verso la stanza e [Openings.SILL_OUT] fuori, con le «orecchie» di [Openings.SILL_EAR] per lato infilate
     * nei lati del vano ([Openings.sillSize]). Con lo sguincio, verso la stanza si allarga come il vano. Sta mezzo cm sopra il
     * fondo del varco: copre il muro sotto senza sfarfallare.
     */
    private fun windowSill(room: Room, o: Opening, a: Vec2, b: Vec2, u: Vec2, n: Vec2, bottom: Double, m: StairMaterial, pick: Pick) {
        val splay = Openings.splayOf(room, o)
        val ear = Openings.SILL_EAR
        val inner = n * (half + Openings.SILL_IN)
        val outer = n * -(half + Openings.SILL_OUT)
        val poly = if (splay < 0.5) listOf(a - u * ear + outer, b + u * ear + outer, b + u * ear + inner, a - u * ear + inner)
        else listOf(
            a - u * ear + outer, b + u * ear + outer, b + u * ear, b + u * (splay + ear) + n * half,
            b + u * (splay + ear) + inner, a - u * (splay + ear) + inner, a - u * (splay + ear) + n * half, a - u * ear,
        )
        // Con il materiale fotografico dei pavimenti: il suo colore sui lati e la foto sopra, ripetuta come sul pavimento.
        val photo = o.sillPhoto
        val c = tint(Rgba.argb(photo?.argb ?: m.argb), pick)
        solid(poly, bottom - Openings.SILL_THICKNESS + 0.5, bottom + 0.5, c, c, pick)
        if (photo != null) for (k in 1 until poly.size - 1) {
            fun uv(p: Vec2) = Vec2(p.x / photo.size, p.y / photo.size)
            val y = bottom + 0.53
            texTri(photo.id, poly[0].at(y), poly[k].at(y), poly[k + 1].at(y), uv(poly[0]), uv(poly[k]), uv(poly[k + 1]), Vec3.Up)
        }
    }

    /**
     * Zanzariera avvolgibile nel vano: la rete grigia (trasparente) dal davanzale al cassonetto in alto, con le guide ai
     * lati. Sta verso fuori; con l'infisso a filo esterno sta verso la stanza.
     */
    private fun insectScreen(a: Vec2, b: Vec2, u: Vec2, n: Vec2, bottom: Double, top: Double, o: Opening, pick: Pick) {
        val side = if (o.frame == com.sagoma.planimetria.model.FramePosition.Outside) 1.0 else -1.0
        val at = n * (side * (half - 1.5))
        val frame = tint(Rgba.argb(if (o.defaultColor) Finish.White.argb else o.frameArgb), pick)
        val box = 7.0
        pane(a + at, b + at, bottom + 1, top - box, tint(Rgba(0.22f, 0.23f, 0.24f), pick).copy(a = 0.38f), pick)
        box(a + at, b + at, 3.0, top - box, top, frame, pick)
        box(a + at, a + u * 2.5 + at, 1.6, bottom, top - box, frame, pick)
        box(b - u * 2.5 + at, b + at, 1.6, bottom, top - box, frame, pick)
        // Barra in fondo alla rete, appena sopra il davanzale.
        box(a + u * 2.5 + at, b - u * 2.5 + at, 1.2, bottom + 1, bottom + 4, frame, pick)
    }

    /**
     * Tenda da sole sopra l'apertura, fuori: telo a righe (o del colore scelto) dal cassonetto sul muro fino al terminale,
     * con la mantovana e i bracci. Sui balconi sporge [Openings.awningReach] quasi in piano; sulle finestre scende a caduta.
     */
    private fun awning(a: Vec2, b: Vec2, u: Vec2, n: Vec2, top: Double, wallTop: Double, o: Opening, pick: Pick) {
        val out = n * -1.0
        val reach = Openings.awningReach(o)
        val balcony = o.bottom <= 0.0
        val y0 = min(top + 18, wallTop - 4)
        val drop = if (balcony) reach * 0.25 else reach * 1.1
        val valance = if (balcony) 22.0 else 14.0
        // Il telo sporge 12 cm per parte, meno accanto a un angolo, a un muro che sporge o alla tenda della finestra vicina.
        val p0 = a - u * faceRoom(a, u * -1.0, n, -1.0, 12.0, awning = true) + out * (half + 8)
        val p1 = b + u * faceRoom(b, u, n, -1.0, 12.0, awning = true) + out * (half + 8)
        val w = p0.distanceTo(p1)
        val front = out * reach
        val colors = o.awningColor?.let { listOf(Rgba.argb(it)) } ?: listOf(Rgba.argb(Opening.AWNING_GREEN), Rgba.argb(Opening.AWNING_CREAM))
        val stripes = if (colors.size == 1) 1 else maxOf(2, kotlin.math.round(w / 16.0).toInt())
        val up = ((p1 - p0).dir() cross (front.dir() - Vec3(0.0, drop, 0.0))).normalized().let { if (it.y < 0) it * -1.0 else it }
        for (k in 0 until stripes) {
            val c = tint(colors[k % colors.size], pick)
            val s0 = p0 + (p1 - p0) * (k.toDouble() / stripes)
            val s1 = p0 + (p1 - p0) * ((k + 1).toDouble() / stripes)
            // Telo, con il sotto appena staccato (le due facce non nello stesso piano).
            val t0 = s0.at(y0); val t1 = s1.at(y0)
            val f0 = (s0 + front).at(y0 - drop); val f1 = (s1 + front).at(y0 - drop)
            quad(t0, t1, f1, f0, up, c, pick)
            val dn = up * -0.6
            quad(t0 + dn, t1 + dn, f1 + dn, f0 + dn, up * -1.0, c.mix(Rgba(0f, 0f, 0f), 0.12f), pick)
            // Mantovana davanti, che pende.
            val outward = out.dir()
            quad(f0, f1, f1 - Vec3(0.0, valance, 0.0), f0 - Vec3(0.0, valance, 0.0), outward, c, pick)
            quad(f0 - outward * 0.5, f1 - outward * 0.5, f1 - outward * 0.5 - Vec3(0.0, valance, 0.0), f0 - outward * 0.5 - Vec3(0.0, valance, 0.0), outward * -1.0, c, pick)
        }
        val metal = tint(Rgba(0.86f, 0.86f, 0.85f), pick)
        // Cassonetto sul muro e terminale davanti.
        box(p0 - out * 6.0, p1 - out * 6.0, 6.0, y0 - 4, y0 + 8, metal, pick)
        box(p0 + front, p1 + front, 2.0, y0 - drop - 3, y0 - drop + 1, metal, pick)
        // Bracci: dai lati sul muro, più in basso, fino al terminale.
        for (s in listOf(p0 + u * 6.0, p1 - u * 6.0)) {
            val from = (s - out * 6.0).at(if (balcony) y0 - 35 else y0 - drop - 25)
            beam(from, (s + front).at(y0 - drop - 1), 1.5, metal, pick)
        }
    }

    /** Inferriata: sbarre tonde ogni 12 cm e tre piatti orizzontali, appena fuori dalla faccia esterna (`out`). */
    private fun grille(a: Vec2, b: Vec2, u: Vec2, out: Vec2, bottom: Double, top: Double, pick: Pick) {
        val w = a.distanceTo(b)
        val c = tint(wroughtIron, pick)
        val bars = maxOf(2, kotlin.math.round(w / 12.0).toInt())
        for (k in 1 until bars) {
            val p = a + u * (w * k / bars) + out
            box(p - u * 0.8, p + u * 0.8, 0.8, bottom + 2, top - 2, c, pick)
        }
        val rails = if (top - bottom > 120) listOf(bottom + 10, (bottom + top) / 2, top - 10) else listOf(bottom + 10, top - 10)
        for (y in rails) box(a + out, b + out, 1.2, y - 2.0, y + 2.0, c, pick)
        // Le due sbarre di bordo, murate ai lati del vano.
        box(a + out, a + u * 2.0 + out, 1.2, bottom, top, c, pick)
        box(b - u * 2.0 + out, b + out, 1.2, bottom, top, c, pick)
    }

    /**
     * Bovindo ([Openings.bayOutline]): la finestra sporge dal muro su tre lati vetrati, con la base in pietra sotto e il
     * tettuccio sopra. Il vano nel muro resta aperto verso la stanza.
     */
    private fun bayWindow(room: Room, o: Opening, a: Vec2, b: Vec2, n: Vec2, bottom: Double, top: Double, pick: Pick) {
        if (current) windows += Scene3D.SceneWindow(((a + b) / 2.0).at((bottom + top) / 2), n.dir().normalized(), a.distanceTo(b), top - bottom)
        val poly = Openings.bayOutline(room, o)
        val color = windowFrameColor(o, pick)
        val glassColor = glassOf(o, pick)
        val stone = tint(Rgba(0.88f, 0.87f, 0.84f), pick)
        val roof = tint(Rgba(0.30f, 0.31f, 0.33f), pick)
        // Base e tettuccio: lo stesso trapezio, appoggiato alla faccia esterna del muro.
        solid(poly, bottom - 12, bottom, stone, stone, pick)
        solid(poly, top, top + 14, roof, roof, pick)
        for (k in 0 until poly.size - 1) {
            val p0 = poly[k]
            val p1 = poly[k + 1]
            val len = p0.distanceTo(p1)
            if (len < 5) continue
            val d = (p1 - p0) / len
            val f = profileWidth(o)
            box(p0, p0 + d * f, 3.0, bottom, top, color, pick)
            box(p1 - d * f, p1, 3.0, bottom, top, color, pick)
            box(p0, p1, 3.0, top - f, top, color, pick)
            box(p0, p1, 3.0, bottom, bottom + f, color, pick)
            pane(p0 + d * f, p1 - d * f, bottom + f, top - f, glassColor, pick)
            // Il davanti largo ha il montante in mezzo, come una finestra a due ante.
            if (k == 1 && len > 100) {
                val m = (p0 + p1) / 2.0
                box(m - d * 2.5, m + d * 2.5, 3.0, bottom + f, top - f, color, pick)
            }
        }
    }

    /**
     * Finestra ad arco o tonda: telaio che segue il bordo del varco (a fettine) e vetro dentro. Ad arco con due ante ha il
     * montante centrale fino all'imposta dell'arco; l'oblò ha una croce sottile se è grande.
     */
    private fun shapedWindow(a: Vec2, b: Vec2, u: Vec2, bottom: Double, top: Double, o: Opening, pick: Pick) {
        val w = a.distanceTo(b)
        val round = o.shape == WindowShape.Round
        val gap = Gap(0.0, w, bottom, top, if (round) PassageStyle.Square else PassageStyle.Arched, pick, round = round)
        val color = windowFrameColor(o, pick)
        val glassColor = glassOf(o, pick)
        val f = profileWidth(o)
        val depth = 3.5
        val slices = 24
        val n = (b - a).perp().normalized()
        for (k in 0 until slices) {
            val t0 = w * k / slices
            val t1 = w * (k + 1) / slices
            val p0 = a + u * t0
            val p1 = a + u * t1
            val hi0 = gap.topAt(t0)
            val hi1 = gap.topAt(t1)
            val lo0 = gap.bottomAt(t0)
            val lo1 = gap.bottomAt(t1)
            // Telaio sopra (lungo l'arco o la metà alta del cerchio) e sotto (davanzale dritto o metà bassa del cerchio).
            slab(p0, p1, depth, hi0 - f, hi1 - f, hi0, hi1, color, pick, color)
            slab(p0, p1, depth, lo0, lo1, lo0 + f, lo1 + f, color, pick, color)
            // Vetro: striscia tra telaio sotto e telaio sopra, visibile dai due lati.
            val y00 = lo0 + f
            val y01 = lo1 + f
            val y10 = hi0 - f
            val y11 = hi1 - f
            if (y10 > y00 || y11 > y01) {
                val nn = n.dir()
                quad(p0.at(y00), p1.at(y01), p1.at(max(y11, y01)), p0.at(max(y10, y00)), nn, glassColor, pick, glassy = true)
                quad(p0.at(y00), p1.at(y01), p1.at(max(y11, y01)), p0.at(max(y10, y00)), nn * -1.0, glassColor, pick, glassy = true)
            }
        }
        if (!round) {
            // Lati dritti fino all'imposta dell'arco.
            val spring = gap.topAt(0.0)
            box(a, a + u * f, depth, bottom, spring, color, pick)
            box(b - u * f, b, depth, bottom, spring, color, pick)
            if (o.leaves == 2) box(a + u * (w / 2 - 2.5), a + u * (w / 2 + 2.5), depth, bottom + f, gap.topAt(w / 2) - f, color, pick)
        } else if (w >= 70) {
            // Oblò grande: croce sottile.
            val c = bottom + w / 2
            box(a + u * (w / 2 - 1.5), a + u * (w / 2 + 1.5), depth - 0.5, bottom + f, bottom + w - f, color, pick)
            box(a + u * f, b - u * f, depth - 0.5, c - 1.5, c + 1.5, color, pick)
        }
    }

    /**
     * Termosifone contro il muro nel punto `p` (normale `n` verso la stanza): a piastra con le scanalature,
     * a elementi verticali, oppure scaldasalviette con due montanti e i tubi orizzontali. Scanalature, elementi e tubi contano
     * come dettagli ([detail]): un termosifone alto o largo chilometri (troppi zeri nel campo) ne aveva milioni e finiva la memoria.
     */
    private fun radiator(f: Fixture, p: Vec2, n: Vec2, along: Vec2, pick: Pick) {
        val depth = Fixtures.RADIATOR_DEPTH
        val y0 = f.elevation
        val y1 = f.elevation + f.height
        val l = f.length
        val start = p - along * (l / 2)
        fun seg(from: Double, to: Double, off: Double, hw: Double, a: Double, b: Double, c: Rgba) =
            box(start + along * from + n * off, start + along * to + n * off, hw, a, b, c, pick)
        when (f.radiatorModel) {
            RadiatorModel.Panel -> {
                val c = tint(Rgba(0.97f, 0.97f, 0.96f), pick)
                val groove = c.mix(Rgba(0f, 0f, 0f), 0.1f)
                seg(0.0, l, depth / 2 + 0.5, depth / 2, y0, y1, c)
                var y = y0 + 6
                while (y < y1 - 4 && detail(6)) { seg(1.0, l - 1.0, depth / 2 + 0.5, depth / 2 + 0.3, y, y + 0.8, groove); y += 5.0 }
            }
            RadiatorModel.Fins -> {
                val c = tint(Rgba(0.96f, 0.96f, 0.95f), pick)
                var x = 0.0
                while (x + 6.0 <= l + 0.01 && detail(6)) { seg(x, x + 6.0, depth / 2 + 0.5, depth / 2, y0, y1, c); x += 8.0 }
                // Collettori sopra e sotto che uniscono gli elementi.
                seg(0.0, l, depth / 2 + 0.5, depth / 4, y0 + 4, y0 + 8, c)
                seg(0.0, l, depth / 2 + 0.5, depth / 4, y1 - 8, y1 - 4, c)
            }
            RadiatorModel.TowelRail -> {
                val chrome = tint(Rgba(0.72f, 0.74f, 0.77f), pick)
                seg(0.0, 3.0, 4.0, 1.5, y0, y1, chrome)
                seg(l - 3.0, l, 4.0, 1.5, y0, y1, chrome)
                var y = y0 + 4
                var k = 0
                while (y < y1 - 2 && detail(6)) {
                    seg(1.5, l - 1.5, 4.0, 1.0, y, y + 2.0, chrome)
                    // Ogni cinque tubi uno spazio più largo, per appendere l'asciugamano.
                    y += if (++k % 5 == 0) 14.0 else 7.0
                }
            }
        }
    }

    /** Lampadario appeso al soffitto (quota `h`) nel punto `c`, secondo il modello. */
    private fun chandelier(f: Fixture, c: Vec2, h: Double, glow: Rgba, pick: Pick) {
        val dark = tint(Rgba(0.22f, 0.23f, 0.25f), pick)
        val brass = tint(Rgba(0.72f, 0.6f, 0.35f), pick)
        fun rod(p: Vec2, y0: Double, y1: Double, r: Double, color: Rgba) = box(p - Vec2(r, 0.0), p + Vec2(r, 0.0), r, y0, y1, color, pick)
        fun light(p: Vec2, size: Double, y0: Double, y1: Double) =
            box(p - Vec2(size / 2, 0.0), p + Vec2(size / 2, 0.0), size / 2, y0, y1, glow, pick, emissive = true)
        when (f.lampModel) {
            LampModel.Modern -> {
                rod(c, h - 50, h, 0.5, dark)
                light(c, 40.0, h - 65, h - 50)
            }
            LampModel.Classic -> {
                rod(c, h - 55, h, 0.8, brass)
                rod(c, h - 62, h - 55, 3.0, brass)
                for (k in 0 until 6) {
                    val ang = k * kotlin.math.PI / 3
                    val tip = c + Vec2(kotlin.math.cos(ang), kotlin.math.sin(ang)) * 32.0
                    box(c, tip, 0.8, h - 60, h - 58.4, brass, pick)
                    rod(tip, h - 60, h - 52, 1.6, brass)
                    light(tip, 3.0, h - 52, h - 44)
                }
            }
            LampModel.Bell -> {
                rod(c, h - 60, h, 0.4, dark)
                // Campana: tre anelli sempre più larghi verso il basso.
                for ((k, r) in listOf(8.0, 14.0, 20.0).withIndex()) rod(c, h - 60 - (k + 1) * 8.0, h - 60 - k * 8.0, r, dark)
                light(c, 10.0, h - 88, h - 84)
            }
            LampModel.Industrial -> {
                for ((dx, drop) in listOf(-30.0 to 60.0, 0.0 to 80.0, 30.0 to 50.0)) {
                    val p = c + Vec2(dx, 0.0)
                    rod(p, h - drop, h, 0.3, dark)
                    rod(p, h - drop - 6, h - drop, 5.0, dark)
                    rod(p, h - drop - 14, h - drop - 6, 10.0, dark)
                    light(p, 7.0, h - drop - 17, h - drop - 14)
                }
            }
        }
    }

    /**
     * Oscuranti sulla faccia esterna del muro: persiane aperte ai due lati della finestra (con le stecche),
     * oppure tapparella abbassata per un terzo con le guide ai lati.
     */
    private fun shading(a: Vec2, b: Vec2, u: Vec2, n: Vec2, bottom: Double, top: Double, o: Opening, pick: Pick) {
        if (o.shading == Shading.None) return
        val out = n * -(half + 1.8) // appena fuori dalla faccia esterna
        val w = a.distanceTo(b)
        when (o.shading) {
            Shading.None -> Unit
            Shading.Shutters -> {
                val c = tint(Rgba.argb(o.shadingArgb), pick)
                val slat = c.mix(Rgba(0f, 0f, 0f), 0.18f)
                val leaves = if (o.kind.leaves == 2 || w > 90) 2 else 1
                val lw = w / leaves
                val sides = if (leaves == 2) listOf(a to -1.0, b to 1.0) else listOf((if (o.hingeLeft) a else b) to (if (o.hingeLeft) -1.0 else 1.0))
                for ((edge, dirSign) in sides) for (leaf in openLeaf(edge, u * dirSign, n, -1.0, half + 1.8, lw)) {
                    box(leaf.p0, leaf.p1, 1.5, bottom, top, c, pick)
                    var y = bottom + 6
                    while (y < top - 4) { box(leaf.p0, leaf.p1, 2.1, y, y + 1.2, slat, pick); y += 6.0 }
                }
            }
            Shading.RollerShutter -> {
                val c = tint(Rgba.argb(o.shadingArgb), pick)
                val line = c.mix(Rgba(0f, 0f, 0f), 0.2f)
                val low = top - (top - bottom) / 3
                box(a + out, b + out, 1.2, low, top, c, pick)
                var y = low + 5
                while (y < top) { box(a + out, b + out, 1.5, y, y + 0.5, line, pick); y += 5.0 }
                box(a + out, a + u * 3.0 + out, 2.0, bottom, top, line, pick)
                box(b - u * 3.0 + out, b + out, 2.0, bottom, top, line, pick)
                box(a - u * 4.0 + out, b + u * 4.0 + out, 3.0, top, top + 18, c, pick) // cassonetto
            }
            Shading.Venetian -> {
                // Lamelle inclinate ogni 7 cm, abbassate per due terzi, con le guide ai lati e il pacco in alto.
                val c = tint(Rgba.argb(o.shadingArgb), pick)
                val guide = c.mix(Rgba(0f, 0f, 0f), 0.25f)
                val low = top - (top - bottom) * 2 / 3
                var y = low
                while (y < top - 6) {
                    box(a + u * 3.0 + out, b - u * 3.0 + out, 2.4, y, y + 0.6, c, pick)
                    box(a + u * 3.0 + out * 1.03, b - u * 3.0 + out * 1.03, 1.2, y + 0.6, y + 1.6, c, pick)
                    y += 7.0
                }
                box(a + out, a + u * 3.0 + out, 2.6, bottom, top, guide, pick)
                box(b - u * 3.0 + out, b + out, 2.6, bottom, top, guide, pick)
                box(a + out, b + out, 3.0, top - 6, top, c, pick)
            }
            Shading.RomanShutter -> {
                // Persiana alla romana: un pannello a stecche incernierato in alto, aperto in fuori di 30° (il fondo si stacca dal muro).
                val c = tint(Rgba.argb(o.shadingArgb), pick)
                val slat = c.mix(Rgba(0f, 0f, 0f), 0.2f)
                val len = (top - bottom) * 0.92
                val ang = kotlin.math.PI / 6
                val away = n * -1.0
                val reach = away * (len * kotlin.math.sin(ang))
                val drop = len * kotlin.math.cos(ang)
                val p0 = a + out
                val p1 = b + out
                val tl = p0.at(top)
                val tr = p1.at(top)
                val bl = (p0 + reach).at(top - drop)
                val br = (p1 + reach).at(top - drop)
                // Normale del pannello verso fuori e in alto.
                val pn = ((tr - tl) cross (bl - tl)).normalized().let { if ((it dot away.dir()) < 0) it * -1.0 else it }
                // Pannello spesso 1 cm: le due facce non stanno nello stesso piano, così da dentro si vede il retro (in ombra) e non il davanti al buio.
                val back = pn * -1.0
                quad(tl, tr, br, bl, pn, c, pick)
                quad(tl + back, tr + back, br + back, bl + back, back, c, pick)
                quad(bl, br, br + back, bl + back, (bl - tl).normalized(), c, pick)
                // Stecche ogni 6 cm lungo il pannello, appena staccate sul davanti.
                val steps = (len / 6.0).toInt()
                for (k in 1 until steps) {
                    val t = k.toDouble() / steps
                    val t2 = (k + 0.25) / steps
                    val l0 = tl + (bl - tl) * t + pn * 0.4
                    val r0 = tr + (br - tr) * t + pn * 0.4
                    val l1 = tl + (bl - tl) * t2 + pn * 0.4
                    val r1 = tr + (br - tr) * t2 + pn * 0.4
                    quad(l0, r0, r1, l1, pn, slat, pick)
                }
                // Cerniera in alto e due bracci che tengono il pannello aperto.
                box(p0, p1, 2.0, top - 3, top, slat, pick)
                for (p in listOf(p0 + u * 4.0, p1 - u * 4.0)) {
                    val foot = p + reach * 0.5
                    box(p, foot, 0.6, top - drop * 0.5 - 1, top - drop * 0.5 + 1, slat, pick)
                }
            }
            Shading.SlidingShutters -> {
                // Persiane scorrevoli: ante a stecche un poco staccate dal muro, aperte di lato accanto al vano (ognuna larga metà
                // vano più la battuta), appese a un binario che corre sopra la finestra e sopra le ante aperte.
                val c = tint(Rgba.argb(o.shadingArgb), pick)
                val slat = c.mix(Rgba(0f, 0f, 0f), 0.18f)
                val rail = c.mix(Rgba(0f, 0f, 0f), 0.3f)
                val off = n * -(half + 3.5)
                val leaves = if (o.kind.leaves == 2 || w > 90) 2 else 1
                val lw = w / leaves + 3.0
                val sides = if (leaves == 2) listOf(a to -1.0, b to 1.0) else listOf((if (o.hingeLeft) a else b) to (if (o.hingeLeft) -1.0 else 1.0))
                val low = maxOf(bottom - 3, 0.0)
                // Spazio libero sulla faccia esterna ai due lati: accanto a un angolo o a un muro che sporge l'anta si ferma
                // prima e copre in parte il vano (con due ante mai oltre la metà); il binario non va oltre.
                val roomA = faceRoom(a, u * -1.0, n, -1.0, lw + 3)
                val roomB = faceRoom(b, u, n, -1.0, lw + 3)
                var reachA = minOf(3.0, roomA)
                var reachB = minOf(3.0, roomB)
                for ((edge, dirSign) in sides) {
                    val room = if (dirSign < 0) roomA else roomB
                    val end = maxOf(minOf(lw + 1, room), if (leaves == 2) lw - w / 2 else 0.0)
                    val p0 = edge + u * (dirSign * (end - lw)) + off
                    val p1 = edge + u * (dirSign * end) + off
                    box(p0, p1, 1.5, low, top + 3, c, pick)
                    var y = low + 6
                    while (y < top) { box(p0, p1, 2.1, y, y + 1.2, slat, pick); y += 6.0 }
                    val reach = maxOf(end, minOf(end + 1, room))
                    if (dirSign < 0) reachA = reach else reachB = reach
                }
                // Il binario: sopra le ante aperte e il vano (con un'anta sola fino a poco oltre l'altro lato).
                box(a - u * reachA + off, b + u * reachB + off, 2.5, top + 3, top + 8, rail, pick)
            }
            Shading.InteriorShutters -> {
                // Scuri interni: ante piene di legno (colore dell'infisso), aperte sulla faccia del muro verso la stanza.
                val c = tint(Rgba.argb(o.shadingArgb), pick).mix(Rgba(0f, 0f, 0f), 0.05f)
                val relief = c.mix(Rgba(0f, 0f, 0f), 0.12f)
                val leaves = if (o.leaves >= 2 || w > 90) 2 else 1
                val lw = w / leaves
                val sides = if (leaves == 2) listOf(a to -1.0, b to 1.0) else listOf((if (o.hingeLeft) a else b) to (if (o.hingeLeft) -1.0 else 1.0))
                for ((edge, dirSign) in sides) {
                    val open = openLeaf(edge, u * dirSign, n, 1.0, half + 1.6, lw)
                    for (leaf in open) box(leaf.p0, leaf.p1, 1.5, bottom, top, c, pick)
                    // Due riquadri in rilievo sulla faccia che si vede (verso la stanza, o verso il vano se è aperto a 90°).
                    val leaf = open.last()
                    val q0 = leaf.p0 + (leaf.p1 - leaf.p0) * 0.15 + leaf.front * 0.6
                    val q1 = leaf.p0 + (leaf.p1 - leaf.p0) * 0.85 + leaf.front * 0.6
                    box(q0, q1, 1.5, bottom + (top - bottom) * 0.1, bottom + (top - bottom) * 0.45, relief, pick)
                    box(q0, q1, 1.5, bottom + (top - bottom) * 0.55, bottom + (top - bottom) * 0.9, relief, pick)
                }
            }
        }
    }

    /** Anta di un oscurante aperta: mezzeria da [p0] a [p1] (spessa 3 cm), con la faccia che si vede verso [front]. */
    private class OpenLeaf(val p0: Vec2, val p1: Vec2, val front: Vec2)

    /**
     * Anta larga `lw` incernierata in `edge`, aperta verso `dir` sulla faccia del muro dal lato `side` (−1 fuori, +1 dentro) a
     * `face` dalla mezzeria: distesa sul muro se c'è spazio; piegata in due (due strati larghi metà) se ce n'è almeno per metà;
     * altrimenti aperta a 90°, di taglio. Così accanto a un angolo o a un muro che sporge non esce dal muro e non lo attraversa.
     */
    private fun openLeaf(edge: Vec2, dir: Vec2, n: Vec2, side: Double, face: Double, lw: Double): List<OpenLeaf> {
        val room = faceRoom(edge, dir, n, side, lw + 2)
        val off = n * (side * face)
        val front = n * side
        return when {
            room >= lw - 0.5 -> listOf(OpenLeaf(edge + off, edge + dir * lw + off, front))
            room >= lw / 2 - 0.5 -> {
                val outer = n * (side * (face + 3.2))
                listOf(OpenLeaf(edge + off, edge + dir * (lw / 2) + off, front), OpenLeaf(edge + outer, edge + dir * (lw / 2) + outer, front))
            }
            else -> {
                val p0 = edge + dir * 1.5 + n * (side * (half + 0.3))
                listOf(OpenLeaf(p0, p0 + n * (side * lw), dir * -1.0))
            }
        }
    }

    /** Muro pieno del piano: mezzeria da `a` lunga `len` verso `u`, mezzo spessore `half`, e quanto continua oltre gli estremi. */
    private class SolidWall(val a: Vec2, val u: Vec2, val len: Double, val half: Double, val cap: Double) {
        fun contains(p: Vec2): Boolean {
            val d = p - a
            val along = d dot u
            return along >= -cap && along <= len + cap && abs(d dot u.perp()) <= half + 0.01
        }
    }

    /** Porta o finestra del piano vista dagli oscuranti accanto: estremi sulla mezzeria del suo muro e normale verso la sua stanza. */
    private class FaceOpening(val o: Opening, val a: Vec2, val b: Vec2, val n: Vec2)

    private var solidWallsOf: FloorPlan? = null
    private var solidWalls = emptyList<SolidWall>()
    private var faceOpenings = emptyList<FaceOpening>()

    /**
     * Muri pieni di [plan] (calcolati una volta per piano, con le sue porte e finestre): quelli delle stanze sporgono di mezzo
     * spessore oltre gli estremi (negli angoli si incontrano), i muri singoli finiscono dritti (i curvi a pezzi).
     */
    private fun solidWalls(): List<SolidWall> {
        if (solidWallsOf !== plan) {
            solidWallsOf = plan
            faceOpenings = plan.rooms.flatMap { r ->
                r.openings.filter { it.wallIndex < r.wallCount }.map { o ->
                    val (a, b) = Openings.span(r, o)
                    FaceOpening(o, a, b, Openings.inwardNormal(r, o.wallIndex))
                }
            } + plan.freeWalls.flatMap { it.pieces() }.flatMap { w ->
                w.openings.map { o -> Openings.span(w.host, o).let { (a, b) -> FaceOpening(o, a, b, Openings.inwardNormal(w.host, 0)) } }
            }
            fun wall(a: Vec2, b: Vec2, half: Double, cap: Double): SolidWall? {
                val len = a.distanceTo(b)
                return if (len < 0.01) null else SolidWall(a, (b - a) / len, len, half, cap)
            }
            solidWalls = plan.rooms.flatMap { r ->
                (0 until r.wallCount).mapNotNull { i ->
                    val shift = r.outwardOf(i) * r.wallShift(i)
                    wall(r.wallStart(i) + shift, r.wallEnd(i) + shift, r.thicknessOf(i) / 2, r.thicknessOf(i) / 2)
                }
            } + plan.freeWalls.flatMap { it.pieces() }.mapNotNull { wall(it.start, it.end, it.thickness / 2, 0.0) }
        }
        return solidWalls
    }

    /**
     * Spazio libero (cm) sulla faccia del muro dal lato `side` (−1 fuori, +1 dentro), partendo da `edge` (sulla mezzeria) verso
     * `dir`: finisce dove finisce il muro (angolo esterno) o dove c'è un altro muro o una colonna davanti alla faccia (angolo
     * interno, muro che sporge) o poco prima di un'altra porta o finestra. Al massimo `reach`, anche se il muro stesso non si
     * trova (forma strana).
     */
    private fun faceRoom(edge: Vec2, dir: Vec2, n: Vec2, side: Double, reach: Double, awning: Boolean = false): Double {
        val near = solidWalls().filter { Polygon.distanceToSegment(edge, it.a, it.a + it.u * it.len) <= reach + it.half + it.cap + 5 }
        // Porte e finestre accanto, sullo stesso muro: l'anta non le copre; se hanno le ante da questa parte, lo spazio si divide a metà.
        var limit = reach
        val across = dir.perp()
        for (f in faceOpenings) {
            val ta = (f.a - edge) dot dir
            val tb = (f.b - edge) dot dir
            val t = minOf(ta, tb)
            if (t <= 0.5 || t >= 2 * limit) continue // con le ante anche dall'altra parte conta la metà
            if (abs((f.a - edge) dot across) > half + 1 || abs((f.b - edge) dot across) > half + 1) continue
            neighbourLimit(f, n * side, nearIsA = ta < tb, t, awning)?.let { limit = minOf(limit, it) }
        }
        val columns = plan.columns.filter { it.center.distanceTo(edge) <= limit + it.width + it.depth }
        fun solid(p: Vec2) = near.any { it.contains(p) } || columns.any { Structure.contains(it, p) }
        val wallDepth = n * (side * (half - 1))
        val beyond = n * (side * (half + 2))
        if (!solid(edge + wallDepth)) return limit
        var t = 0.0
        while (t < limit) {
            val p = edge + dir * (t + 1)
            if (!solid(p + wallDepth) || solid(p + beyond)) return t
            t += 1.0
        }
        return limit
    }

    /**
     * Fin dove arriva un'anta aperta verso la porta o finestra `f` che comincia a `t` cm, sulla faccia del muro verso `face`: a metà
     * strada se anche lei ha un'anta da questa parte (persiane e scorrevoli fuori, scuri dentro), altrimenti poco prima del suo
     * bordo (il coprifilo delle porte, il cassonetto della tapparella). Null se da questa parte non c'è (la nicchia dell'altra faccia).
     * Per la tenda da sole (`awning`, più in alto) conta solo la tenda della finestra vicina: a metà strada.
     */
    private fun neighbourLimit(f: FaceOpening, face: Vec2, nearIsA: Boolean, t: Double, awning: Boolean): Double? {
        val o = f.o
        val outside = (f.n dot face) < 0
        if (awning) return if (outside && o.hasAwning) t / 2 else null
        val drawn = o.kind.glazed && o.hasShading && o.shape == WindowShape.Rectangle
        val leaf = drawn && when (o.shading) {
            Shading.Shutters, Shading.SlidingShutters -> outside
            Shading.InteriorShutters -> !outside
            else -> false
        }
        if (leaf) {
            val w = f.a.distanceTo(f.b)
            val leaves = if (o.shading == Shading.InteriorShutters) o.leaves else o.kind.leaves
            if (leaves >= 2 || w > 90 || o.hingeLeft == nearIsA) return t / 2
        }
        return when {
            o.kind == OpeningKind.Niche -> if (outside) null else t - 1
            o.kind == OpeningKind.Passage -> t - 1
            !o.kind.glazed && !o.frameless -> t - 8
            else -> t - 4
        }
    }

    /** Lastra di vetro verticale da p0 a p1, visibile dai due lati. */
    private fun pane(p0: Vec2, p1: Vec2, y0: Double, y1: Double, color: Rgba, pick: Pick?) {
        if (y1 <= y0) return
        val n = (p1 - p0).perp().normalized().dir()
        val q = listOf(p0.at(y0), p1.at(y0), p1.at(y1), p0.at(y1))
        quad(q[0], q[1], q[2], q[3], n, color, pick, glassy = true)
        quad(q[0], q[1], q[2], q[3], n * -1.0, color, pick, glassy = true)
    }

    // ---------- Impianti ----------

    /**
     * Luce fissata a una parete: scatola luminosa appena fuori dal muro (sul filo interno, verso la stanza), centrata
     * all'altezza dell'impianto; la luce vera sta davanti, un poco staccata, e punta lungo la normale della parete.
     */
    /** Scatola luminosa sulla parete: larga `width` lungo il muro, alta `height` attorno alla quota, sporgente `depth` dal filo interno. */
    private fun wallGlow(f: Fixture, p: Vec2, n: Vec2, along: Vec2, width: Double, height: Double, depth: Double, pick: Pick) {
        val c = p + n * (depth / 2 + 0.5)
        box(c - along * (width / 2), c + along * (width / 2), depth / 2, f.elevation - height / 2, f.elevation + height / 2, tint(lightColor, pick), pick, emissive = true)
    }

    /**
     * Striscia LED a parete: striscia sottile lungo il muro e, come quella a soffitto, più luci puntiformi neutre (senza direzione:
     * il renderer le fa diffuse) distribuite sulla sua lunghezza, un poco davanti alla parete; il flusso totale si divide tra i punti.
     */
    private fun wallLedStrip(room: Room, f: Fixture, p: Vec2, n: Vec2, along: Vec2, pick: Pick) {
        wallGlow(f, p, n, along, f.length, Scene3D.WALL_LED_HEIGHT, Scene3D.WALL_LED_DEPTH, pick)
        if (!current) return
        val count = (f.length / Scene3D.LED_LIGHT_SPACING).toInt().coerceIn(1, Scene3D.MAX_STRIP_LIGHTS)
        val front = p + n * (Scene3D.WALL_LED_DEPTH + 4.0)
        for (k in 0 until count) {
            val t = f.length * ((k + 0.5) / count) - f.length / 2
            lights += Scene3D.SceneLight((front + along * t).at(f.elevation), null, Scene3D.WALL_LED_LUMENS / count, warm = false, room = Polygon.bounds(room.points),
                profile = LightProfile.Strip, axis = Vec3(n.x, 0.0, n.y))
        }
    }

    private fun wallLamp(room: Room, f: Fixture, p: Vec2, n: Vec2, along: Vec2, width: Double, height: Double, depth: Double, lumens: Double, pick: Pick) {
        wallGlow(f, p, n, along, width, height, depth, pick)
        // La plafoniera è una luce diffusa davanti alla parete (senza direzione: il renderer la fa puntiforme, come la plafoniera a
        // soffitto). Il faretto è un fascio orientato secondo `aimYaw` e `aimTilt` rispetto alla normale della parete (SpotAim).
        val direction = if (f.kind == FixtureKind.WallSpot) SpotAim.direction(n, f.aimYaw, f.aimTilt) else null
        if (current) lights += Scene3D.SceneLight((p + n * (depth + 1.0)).at(f.elevation), direction, lumens, warm = true, room = Polygon.bounds(room.points), fixtureId = f.id,
            profile = if (direction != null) LightProfile.Spot else LightProfile.Wall, axis = Vec3(n.x, 0.0, n.y))
    }

    private fun fixture(room: Room, f: Fixture) {
        val pick = Pick.Fixture(room.id, f.id)
        if (f.kind.mount == Mount.Wall) {
            if (f.wallIndex >= room.wallCount) return
            val (p, n) = Fixtures.wallAnchor(room, f)
            val along = n.perp() * -1.0
            fun onWall(width: Double, height: Double, depth: Double, elevation: Double, color: Rgba) {
                val c = p + n * (depth / 2 + 0.5)
                box(c - along * (width / 2), c + along * (width / 2), depth / 2, elevation, elevation + height, tint(color, pick), pick)
            }
            when (f.kind) {
                FixtureKind.Radiator -> radiator(f, p, n, along, pick)
                FixtureKind.Outlet -> onWall(8.0, 8.0, 1.5, f.elevation - 4, Rgba(0.78f, 0.87f, 1f))
                FixtureKind.Switch -> onWall(8.0, 12.0, 1.5, f.elevation - 6, Rgba(0.88f, 0.92f, 1f))
                FixtureKind.WaterPoint -> onWall(6.0, 6.0, 5.0, f.elevation - 3, Rgba(0.18f, 0.6f, 0.7f))
                FixtureKind.WallLight -> wallLamp(room, f, p, n, along, Scene3D.WALL_LIGHT_WIDTH, Scene3D.WALL_LIGHT_HEIGHT, Scene3D.WALL_LIGHT_DEPTH, 800.0, pick)
                FixtureKind.WallSpot -> wallLamp(room, f, p, n, along, Scene3D.WALL_SPOT_SIZE, Scene3D.WALL_SPOT_SIZE, Scene3D.WALL_SPOT_DEPTH, 400.0, pick)
                FixtureKind.WallLedStrip -> wallLedStrip(room, f, p, n, along, pick)
                else -> Unit
            }
            return
        }
        // Luci attaccate al soffitto, anche se è in pendenza.
        val c = f.point
        val h = Ceilings.heightAt(room, if (f.kind.linear) Fixtures.linearEnds(f).let { (a, b) -> (a + b) / 2.0 } else c)
        val glow = tint(lightColor, pick)
        fun lamp(size: Double, y0: Double, y1: Double) = box(c - Vec2(size / 2, 0.0), c + Vec2(size / 2, 0.0), size / 2, y0, y1, glow, pick, emissive = true)
        // La luce vera, per chi la sa disegnare (un po' sotto la lampada, così non resta chiusa nel soffitto).
        val roomBox = Polygon.bounds(room.points)
        if (current) when (f.kind) {
            FixtureKind.Spotlight -> lights += Scene3D.SceneLight(c.at(h - 4), Vec3(0.0, -1.0, 0.0), 400.0, warm = true, room = roomBox, profile = LightProfile.Spot)
            FixtureKind.CeilingLight -> lights += Scene3D.SceneLight(c.at(h - 12), null, 1500.0, warm = true, room = roomBox, profile = LightProfile.Downlight)
            FixtureKind.Chandelier -> lights += Scene3D.SceneLight(c.at(h - 60), null, 2000.0, warm = true, room = roomBox, profile = LightProfile.Pendant)
            FixtureKind.Neon, FixtureKind.LedStrip -> {
                // Luce lunga: qualche punto luce lungo la sua lunghezza.
                val (a, b) = Fixtures.linearEnds(f)
                val n = (a.distanceTo(b) / 80).toInt().coerceIn(1, Scene3D.MAX_STRIP_LIGHTS)
                val total = if (f.kind == FixtureKind.Neon) 2500.0 else 1000.0
                for (k in 0 until n) lights += Scene3D.SceneLight((a + (b - a) * ((k + 0.5) / n)).at(h - 10), null, total / n, warm = false, room = roomBox, profile = LightProfile.Strip)
            }
            else -> Unit
        }
        when (f.kind) {
            FixtureKind.Spotlight -> lamp(12.0, h - 2, h)
            FixtureKind.CeilingLight -> lamp(40.0, h - 6, h)
            FixtureKind.Chandelier -> chandelier(f, c, h, glow, pick)
            FixtureKind.Neon, FixtureKind.LedStrip -> {
                val (a, b) = Fixtures.linearEnds(f)
                val strip = f.kind == FixtureKind.LedStrip
                box(a, b, if (strip) 1.0 else 3.0, h - if (strip) 1.5 else 6.0, h, glow, pick, emissive = true)
            }
            else -> Unit
        }
    }
}

/** Triangolazione "a orecchie" di un poligono semplice (anche concavo, come le stanze a L). */
object Triangulation {
    fun triangulate(pts: List<Vec2>): List<Triple<Int, Int, Int>> {
        val n = pts.size
        if (n < 3) return emptyList()
        val sign = if (Polygon.signedArea(pts) >= 0) 1.0 else -1.0
        val idx = (0 until n).toMutableList()
        val out = mutableListOf<Triple<Int, Int, Int>>()
        fun cross(o: Vec2, a: Vec2, b: Vec2) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        /**
         * Vertice dentro il triangolo o sul suo bordo: anche un vertice che cade proprio sulla diagonale
         * (angolo interno di una L) impedisce di tagliare quell'orecchio, altrimenti il taglio uscirebbe dalla stanza.
         */
        fun inside(p: Vec2, a: Vec2, b: Vec2, c: Vec2): Boolean {
            if (p.distanceTo(a) < 1e-9 || p.distanceTo(b) < 1e-9 || p.distanceTo(c) < 1e-9) return false
            val d1 = cross(a, b, p) * sign
            val d2 = cross(b, c, p) * sign
            val d3 = cross(c, a, p) * sign
            return d1 >= -1e-9 && d2 >= -1e-9 && d3 >= -1e-9
        }
        var guard = 0
        while (idx.size > 3 && guard++ < n * n) {
            var clipped = false
            for (k in idx.indices) {
                val ia = idx[(k - 1 + idx.size) % idx.size]
                val ib = idx[k]
                val ic = idx[(k + 1) % idx.size]
                val a = pts[ia]
                val b = pts[ib]
                val c = pts[ic]
                if (cross(a, b, c) * sign <= 1e-9) continue // vertice concavo o allineato
                if (idx.any { it != ia && it != ib && it != ic && inside(pts[it], a, b, c) }) continue
                out += Triple(ia, ib, ic)
                idx.removeAt(k)
                clipped = true
                break
            }
            if (!clipped) break
        }
        // Resto (3 vertici, o poligono degenere): ventaglio.
        for (k in 1 until idx.size - 1) out += Triple(idx[0], idx[k], idx[k + 1])
        return out
    }
}
