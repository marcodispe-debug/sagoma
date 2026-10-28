package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.HouseSite
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.Finish
import com.sagoma.planimetria.model.FramePosition
import com.sagoma.planimetria.model.GarageDoor
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.SashType
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.model.WindowShape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rilettura di un file IFC (il ritorno dal professionista, o un file fatto da zero con Revit, Archicad, Allplan…):
 * piani, stanze (IfcSpace), muri, porte, finestre, passaggi e colonne diventano la planimetria di sagoma.
 *
 * - Le stanze nascono dagli IfcSpace (filo interno), e il loro contorno si allarga di mezzo spessore del muro che le
 *   chiude da ogni lato, come lavora sagoma (poligono sulla mezzeria). Un lato senza muro diventa un muro eliminato.
 * - I muri che non chiudono nessuna stanza diventano muri singoli.
 * - Porte e finestre vanno sul muro della stanza più vicino; tipo, ante e verso vengono dalle proprietà "Sagoma" se il
 *   file era uscito da sagoma, altrimenti si deducono da larghezza e altezza da terra.
 * - Tutto il resto (arredi, impianti, oggetti speciali del professionista) per ora si conta soltanto in [Report.skipped].
 */
object IfcImport {
    class Report(
        val floors: Int,
        val rooms: Int,
        val walls: Int,
        val freeWalls: Int,
        val doors: Int,
        val windows: Int,
        val passages: Int,
        val columns: Int,
        /** Il file era uscito da sagoma (ha le proprietà "Sagoma"). */
        val fromSagoma: Boolean,
        /** Oggetti non letti, per tipo IFC. */
        val skipped: Map<String, Int>,
    )

    class Result(
        val building: Building,
        val report: Report,
        /** GlobalId dell'IfcSpace da cui viene ogni stanza (piano, id stanza). */
        val roomGuids: Map<Pair<Long, Long>, String>,
        /** GlobalId degli oggetti letti per piano: serve a [IfcRoundTrip] per riconoscere scale e altro rimasto uguale. */
        val guids: Set<String>,
    )

    fun read(text: String): Result = IfcReader(StepFile.parse(text)).run()

    /** Solidi di tutti gli oggetti del file, in cm sagoma (x, y in pianta, z in alto), per controllare a occhio un file. */
    fun solids(text: String): List<Pair<String, List<List<DoubleArray>>>> {
        val r = IfcReader(StepFile.parse(text))
        return r.f.entities.values.filter { it.args.size > 6 && r.f[it.ref(6)]?.type == "IFCPRODUCTDEFINITIONSHAPE" }.flatMap { e ->
            r.solids(e).map { s ->
                e.type to listOf(s.base, s.base.map { it + s.extrusion }).map { ring -> ring.map { doubleArrayOf(it.x * r.scale, -it.y * r.scale, it.z * r.scale) } }
            }
        }
    }

    /** Tipi IFC di muro. */
    internal val WALLS = setOf("IFCWALL", "IFCWALLSTANDARDCASE", "IFCWALLELEMENTEDCASE")
}

/** Punto o vettore nello spazio del file (unità del file). */
internal data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length get() = sqrt(this dot this)
    fun normalized() = if (length < 1e-12) this else this * (1 / length)
}

/** Sistema di riferimento: assi e origine nel mondo. */
internal class Frame(val o: V3, val x: V3, val y: V3, val z: V3) {
    fun apply(p: V3) = o + x * p.x + y * p.y + z * p.z
    fun dir(v: V3) = x * v.x + y * v.y + z * v.z
    /** Questo sistema dentro `parent`. */
    fun inside(parent: Frame) = Frame(parent.apply(o), parent.dir(x), parent.dir(y), parent.dir(z))

    companion object {
        val Identity = Frame(V3(0.0, 0.0, 0.0), V3(1.0, 0.0, 0.0), V3(0.0, 1.0, 0.0), V3(0.0, 0.0, 1.0))
    }
}

/** Solido letto: poligono di base (nel mondo) e vettore di estrusione. */
internal class Solid(val base: List<V3>, val extrusion: V3) {
    val points get() = base + base.map { it + extrusion }
}

internal class IfcReader(val f: StepFile) {
    /** cm per unità di lunghezza del file. */
    val scale = lengthScale()
    val psets = readPsets()
    val skipped = HashMap<String, Int>()
    var fromSagoma = false

    fun lengthScale(): Double {
        for (u in f.ofType("IFCSIUNIT")) if (u.enum(1) == "LENGTHUNIT") {
            return when (u.enum(2)) { "MILLI" -> 0.1; "CENTI" -> 1.0; "DECI" -> 10.0; "KILO" -> 100000.0; else -> 100.0 }
        }
        for (u in f.ofType("IFCCONVERSIONBASEDUNIT")) if (u.enum(1) == "LENGTHUNIT") {
            when (u.str(2)?.uppercase()) { "FOOT" -> return 30.48; "INCH" -> return 2.54 }
        }
        return 0.1
    }

    /** Proprietà per oggetto: insieme → nome → valore. */
    fun readPsets(): Map<Int, Map<String, Map<String, StepValue>>> {
        val out = HashMap<Int, HashMap<String, Map<String, StepValue>>>()
        for (rel in f.ofType("IFCRELDEFINESBYPROPERTIES")) {
            val set = f[rel.ref(5)] ?: continue
            if (set.type != "IFCPROPERTYSET") continue
            val values = set.refs(4).mapNotNull { f[it] }.filter { it.type == "IFCPROPERTYSINGLEVALUE" }
                .associate { (it.str(0) ?: "") to (it.args.getOrNull(2) ?: StepValue.Null) }
            for (obj in rel.refs(4)) out.getOrPut(obj) { HashMap() }[set.str(2) ?: ""] = values
        }
        return out
    }

    fun prop(obj: Int, set: String, name: String): StepValue? = psets[obj]?.get(set)?.get(name)?.let { (it as? StepValue.Typed)?.value ?: it }
    fun propNum(obj: Int, set: String, name: String) = (prop(obj, set, name) as? StepValue.Num)?.value
    fun propStr(obj: Int, set: String, name: String) = when (val v = prop(obj, set, name)) {
        is StepValue.Str -> v.text
        is StepValue.Enum -> v.name
        else -> null
    }

    // ---------- Posizioni ----------

    private val frames = HashMap<Int, Frame>()

    fun vec(id: Int?, default: V3): V3 {
        val e = f[id] ?: return default
        val c = e.nums(0)
        return V3(c.getOrElse(0) { 0.0 }, c.getOrElse(1) { 0.0 }, c.getOrElse(2) { 0.0 })
    }

    /** IfcAxis2Placement3D o 2D come sistema di riferimento. */
    fun axisFrame(id: Int?): Frame {
        val e = f[id] ?: return Frame.Identity
        val o = vec(e.ref(0), V3(0.0, 0.0, 0.0))
        if (e.type == "IFCAXIS2PLACEMENT2D") {
            val x = vec(e.ref(1), V3(1.0, 0.0, 0.0)).normalized()
            return Frame(o, x, V3(-x.y, x.x, 0.0), V3(0.0, 0.0, 1.0))
        }
        val z = vec(e.ref(1), V3(0.0, 0.0, 1.0)).normalized()
        val ref = vec(e.ref(2), if (abs(z.x) < 0.9) V3(1.0, 0.0, 0.0) else V3(0.0, 1.0, 0.0))
        val x = (ref - z * (ref dot z)).normalized()
        return Frame(o, x, z cross x, z)
    }

    /** Posizione di un oggetto nel mondo (catena di IfcLocalPlacement). */
    fun placement(id: Int?): Frame {
        if (id == null) return Frame.Identity
        frames[id]?.let { return it }
        val e = f[id] ?: return Frame.Identity
        val local = axisFrame(e.ref(1))
        val result = if (e.ref(0) != null && e.ref(0) != id) local.inside(placement(e.ref(0))) else local
        frames[id] = result
        return result
    }

    // ---------- Geometria ----------

    /** Solidi del corpo ("Body") dell'oggetto, nel mondo. */
    fun solids(product: StepEntity): List<Solid> {
        val frame = placement(product.ref(5))
        val shape = f[product.ref(6)] ?: return emptyList()
        val reps = shape.refs(2).mapNotNull { f[it] }
        val bodies = reps.filter { it.str(1).equals("Body", ignoreCase = true) }.ifEmpty { reps.filter { !it.str(1).equals("Axis", true) } }
        return bodies.flatMap { r -> r.refs(3).flatMap { item(f[it], frame) } }
    }

    /** Asse ("Axis") dell'oggetto nel mondo, se c'è: primo e ultimo punto. */
    fun axis(product: StepEntity): Pair<V3, V3>? {
        val frame = placement(product.ref(5))
        val shape = f[product.ref(6)] ?: return null
        val rep = shape.refs(2).mapNotNull { f[it] }.firstOrNull { it.str(1).equals("Axis", true) } ?: return null
        val curve = f[rep.refs(3).firstOrNull()] ?: return null
        val pts = curvePoints(curve)
        if (pts.size < 2) return null
        return frame.apply(V3(pts.first().x, pts.first().y, 0.0)) to frame.apply(V3(pts.last().x, pts.last().y, 0.0))
    }

    fun item(e: StepEntity?, frame: Frame): List<Solid> {
        e ?: return emptyList()
        return when (e.type) {
            "IFCEXTRUDEDAREASOLID" -> {
                val pos = axisFrame(e.ref(1)).inside(frame)
                val profile = profilePoints(f[e.ref(0)])
                if (profile.size < 3) return emptyList()
                val d = vec(e.ref(2), V3(0.0, 0.0, 1.0)).normalized() * (e.num(3) ?: 0.0)
                listOf(Solid(profile.map { pos.apply(V3(it.x, it.y, 0.0)) }, pos.dir(d)))
            }
            "IFCBOOLEANCLIPPINGRESULT", "IFCBOOLEANRESULT" -> item(f[e.ref(1)], frame)
            "IFCMAPPEDITEM" -> {
                val map = f[e.ref(0)] ?: return emptyList()
                val origin = axisFrame(map.ref(0)).inside(frame)
                val rep = f[map.ref(1)] ?: return emptyList()
                rep.refs(3).flatMap { item(f[it], origin) }
            }
            "IFCFACETEDBREP", "IFCTRIANGULATEDFACESET", "IFCPOLYGONALFACESET" -> {
                val pts = meshPoints(e).map { frame.apply(it) }
                if (pts.isEmpty()) return emptyList()
                val minZ = pts.minOf { it.z }
                val maxZ = pts.maxOf { it.z }
                // Senza estrusione: la base è il contorno convesso dei punti in pianta (basta per muri e ingombri).
                val hull = convexHull(pts.map { Vec2(it.x, it.y) }).map { V3(it.x, it.y, minZ) }
                listOf(Solid(hull, V3(0.0, 0.0, maxZ - minZ)))
            }
            else -> { skipped.countOne("geometria ${e.type}"); emptyList() }
        }
    }

    fun meshPoints(e: StepEntity): List<V3> = when (e.type) {
        "IFCFACETEDBREP" -> f[e.ref(0)]?.refs(0)?.flatMap { face -> f[face]?.refs(0).orEmpty() }?.flatMap { bound ->
            f[f[bound]?.ref(0)]?.refs(0)?.map { vec(it, V3(0.0, 0.0, 0.0)) }.orEmpty()
        }.orEmpty()
        else -> f[e.ref(0)]?.list(0)?.mapNotNull { (it as? StepValue.Lst)?.items?.mapNotNull { n -> (n as? StepValue.Num)?.value } }
            ?.map { V3(it.getOrElse(0) { 0.0 }, it.getOrElse(1) { 0.0 }, it.getOrElse(2) { 0.0 }) }.orEmpty()
    }

    /** Contorno del profilo nel suo piano. */
    fun profilePoints(p: StepEntity?): List<Vec2> {
        p ?: return emptyList()
        return when (p.type) {
            "IFCARBITRARYCLOSEDPROFILEDEF", "IFCARBITRARYPROFILEDEFWITHVOIDS" -> curvePoints(f[p.ref(2)]).let { pts ->
                if (pts.size > 1 && pts.first().distanceTo(pts.last()) < 1e-6) pts.dropLast(1) else pts
            }
            "IFCRECTANGLEPROFILEDEF" -> {
                val pos = axisFrame(p.ref(2))
                val hx = (p.num(3) ?: 0.0) / 2
                val hy = (p.num(4) ?: 0.0) / 2
                listOf(V3(-hx, -hy, 0.0), V3(hx, -hy, 0.0), V3(hx, hy, 0.0), V3(-hx, hy, 0.0)).map { pos.apply(it).let { q -> Vec2(q.x, q.y) } }
            }
            "IFCCIRCLEPROFILEDEF" -> {
                val pos = axisFrame(p.ref(2))
                val r = p.num(3) ?: 0.0
                (0 until 24).map { k -> val a = k * 2 * kotlin.math.PI / 24; pos.apply(V3(cos(a) * r, sin(a) * r, 0.0)).let { q -> Vec2(q.x, q.y) } }
            }
            else -> { skipped.countOne("profilo ${p.type}"); emptyList() }
        }
    }

    fun curvePoints(c: StepEntity?): List<Vec2> {
        c ?: return emptyList()
        return when (c.type) {
            "IFCPOLYLINE" -> c.refs(0).map { vec(it, V3(0.0, 0.0, 0.0)).let { v -> Vec2(v.x, v.y) } }
            "IFCINDEXEDPOLYCURVE" -> f[c.ref(0)]?.list(0)?.mapNotNull { (it as? StepValue.Lst)?.items?.mapNotNull { n -> (n as? StepValue.Num)?.value } }
                ?.map { Vec2(it.getOrElse(0) { 0.0 }, it.getOrElse(1) { 0.0 }) }.orEmpty()
            "IFCCOMPOSITECURVE" -> c.refs(0).flatMap { seg -> curvePoints(f[f[seg]?.ref(2)]) }
            "IFCTRIMMEDCURVE" -> curvePoints(f[c.ref(0)])
            else -> emptyList()
        }
    }

    fun convexHull(pts: List<Vec2>): List<Vec2> {
        val s = pts.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (s.size < 3) return s
        fun cross(o: Vec2, a: Vec2, b: Vec2) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<Vec2>()
        for (p in s) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1); lower += p }
        val upper = mutableListOf<Vec2>()
        for (p in s.reversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1); upper += p }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Punto del file → punto di sagoma (cm, y in giù). */
    fun plan(p: V3) = Vec2(p.x * scale, -p.y * scale)

    // ---------- Lettura ----------

    class WallLine(val id: Int, val a: Vec2, val b: Vec2, val thickness: Double, val height: Double, val phase: Phase) {
        val u get() = (b - a).normalized()
        val length get() = a.distanceTo(b)
    }

    class Hole(
        val id: Int, val kind: OpeningKind?, val isDoor: Boolean, val filled: Boolean, val wall: WallLine?, val center: Vec2, val width: Double,
        val sill: Double, val height: Double, val phase: Phase,
        /** Incavo che non passa il muro (IfcOpeningElement .RECESS.): una nicchia. */
        val recess: Boolean = false,
        /** Da che parte della mezzeria sta il vano (per la nicchia: verso la sua stanza). */
        val offset: Vec2 = Vec2.Zero,
        /** Porte: modo di aprirsi dell'IFC (IfcDoorTypeOperationEnum), dalla porta o dal suo tipo. */
        val doorOperation: String? = null,
        /** Finestre: modo di aprirsi di ogni anta (IfcWindowPanelOperationEnum), dal tipo della finestra. */
        val panelOperations: List<String> = emptyList(),
    )

    /** Tipo (IfcDoorType, IfcWindowType o gli stili dell'IFC2x3) di ogni porta o finestra. */
    val typeOf: Map<Int, StepEntity> = buildMap {
        for (rel in f.ofType("IFCRELDEFINESBYTYPE")) { val t = f[rel.ref(5)] ?: continue; for (o in rel.refs(4)) put(o, t) }
    }

    fun doorOperation(door: StepEntity): String? {
        // USERDEFINED: vale il nome scritto (es. la basculante, UPANDOVER).
        if (door.enum(11) == "USERDEFINED") door.str(12)?.let { return it.uppercase() }
        door.enum(11)?.takeIf { it != "NOTDEFINED" && it != "USERDEFINED" }?.let { return it }
        val t = typeOf[door.id] ?: return null
        return when (t.type) { "IFCDOORTYPE" -> t.enum(10); "IFCDOORSTYLE" -> t.enum(8); else -> null }
    }

    fun panelOperations(window: StepEntity): List<String> {
        val t = typeOf[window.id] ?: return emptyList()
        return t.refs(5).mapNotNull { f[it] }.filter { it.type == "IFCWINDOWPANELPROPERTIES" }.mapNotNull { it.enum(4) }
    }

    /** Aspetto dell'apertura scritto da sagoma (proprietà "Sagoma"), o quello che si deduce dai nomi standard dell'IFC. */
    fun looks(h: Hole, base: Opening): Opening {
        fun b(name: String) = (prop(h.id, IfcExport.PSET, name) as? StepValue.Enum)?.name == "T"
        fun <E : Enum<E>> e(name: String, values: List<E>): E? = propStr(h.id, IfcExport.PSET, name)?.let { v -> values.firstOrNull { it.name == v } }
        var o = base.copy(
            style = e("Style", PassageStyle.entries) ?: base.style,
            slideOnWall = b("SlideOnWall"), folding = b("Folding"), frameless = b("Frameless"), unequalLeaves = b("UnequalLeaves"),
            sash = e("Sash", SashType.entries) ?: base.sash,
            windowModel = e("WindowModel", WindowModel.entries) ?: base.windowModel,
            doorModel = e("DoorModel", DoorModel.entries) ?: base.doorModel,
            shading = e("Shading", Shading.entries) ?: base.shading,
            frame = e("Frame", FramePosition.entries) ?: base.frame,
            color = e("Color", Finish.entries),
            customColor = propNum(h.id, IfcExport.PSET, "CustomColor")?.toLong(),
            shadingColor = propNum(h.id, IfcExport.PSET, "ShadingColor")?.toLong(),
            splay = propNum(h.id, IfcExport.PSET, "Splay")?.let { it * scale } ?: 0.0,
            splayRounded = b("SplayRounded"), entrance = b("Entrance"),
            pivot = b("Pivot"),
            transom = propNum(h.id, IfcExport.PSET, "Transom")?.let { it * scale } ?: 0.0,
            sidelight = propNum(h.id, IfcExport.PSET, "Sidelight")?.let { it * scale } ?: 0.0,
            leafCount = propNum(h.id, IfcExport.PSET, "LeafCount")?.toInt() ?: 0,
            liftSlide = b("LiftSlide"),
            windowShape = e("WindowShape", WindowShape.entries) ?: base.windowShape,
            grille = b("Grille"), topLight = b("TopLight"), bay = b("Bay"),
            garage = e("Garage", GarageDoor.entries) ?: base.garage,
            insectScreen = b("InsectScreen"), awning = b("Awning"),
            awningColor = propNum(h.id, IfcExport.PSET, "AwningColor")?.toLong(),
            sillMaterial = e("SillMaterial", StairMaterial.entries),
        )
        if (h.kind != null) return o
        // File non uscito da sagoma: il modo di aprirsi dai nomi dell'IFC.
        h.doorOperation?.let { op ->
            when {
                op.startsWith("SLIDING_TO_") -> o = o.copy(sliding = true, hingeLeft = op.endsWith("LEFT"))
                op == "DOUBLE_DOOR_SLIDING" -> o = o.copy(sliding = true)
                op.startsWith("FOLDING_TO_") -> o = o.copy(folding = true, hingeLeft = op.endsWith("LEFT"))
                op == "DOUBLE_DOOR_FOLDING" -> o = o.copy(folding = true)
                op == "SINGLE_SWING_LEFT" -> o = o.copy(hingeLeft = true)
                op == "SINGLE_SWING_RIGHT" -> o = o.copy(hingeLeft = false)
                // Porte da garage: la sezionale si arrotola in alto; la basculante col nome scritto da sagoma.
                op == "ROLLINGUP" && !o.kind.glazed -> o = o.copy(garage = GarageDoor.Sectional)
                op == IfcExport.UP_AND_OVER && !o.kind.glazed -> o = o.copy(garage = GarageDoor.UpAndOver)
            }
        }
        val ops = h.panelOperations
        if (ops.isNotEmpty()) {
            when {
                "SLIDINGHORIZONTAL" in ops && ops.all { it == "SLIDINGHORIZONTAL" || it == "FIXEDCASEMENT" } ->
                    o = o.copy(sliding = true, liftSlide = "FIXEDCASEMENT" in ops)
                ops.all { it == "FIXEDCASEMENT" } -> o = o.copy(sash = SashType.Fixed)
                ops.any { it == "BOTTOMHUNG" } -> o = o.copy(sash = SashType.Hopper)
                ops.any { it.startsWith("TILTANDTURN") } -> o = o.copy(sash = SashType.TiltTurn)
                ops.any { it == "PIVOTHORIZONTAL" } -> o = o.copy(sash = SashType.Pivot)
            }
            if (ops.size in 3..4 && o.kind.leaves == 2) o = o.copy(leafCount = ops.size)
            if (ops.size == 1) ops[0].takeIf { it.endsWith("HAND") }?.let { o = o.copy(hingeLeft = it.endsWith("LEFTHAND")) }
        }
        return o
    }

    fun phaseOf(id: Int, common: String): Phase = when (propStr(id, common, "Status")?.uppercase()) {
        "NEW" -> Phase.New
        "DEMOLISH" -> Phase.Demolish
        else -> Phase.Existing
    }

    /**
     * Muro come mezzeria dritta: rettangolo più stretto attorno al corpo in pianta (lungo l'asse, se c'è).
     * Altezza dal pavimento del piano.
     */
    fun wallLine(e: StepEntity, floorZ: Double): WallLine? {
        val solids = solids(e)
        val pts = solids.flatMap { it.points }
        if (pts.size < 3) return null
        val plan = pts.map(::plan)
        val axis = axis(e)?.let { (a, b) -> (plan(b) - plan(a)).normalized() }
        val dirs = if (axis != null && axis.length > 0.5) listOf(axis) else convexHull(plan).let { h ->
            h.indices.map { (h[(it + 1) % h.size] - h[it]).normalized() }.filter { it.length > 0.5 }
        }
        var best: Triple<Vec2, ClosedFloatingPointRange<Double>, ClosedFloatingPointRange<Double>>? = null
        var bestArea = Double.MAX_VALUE
        for (u in dirs) {
            val v = u.perp()
            val along = plan.map { it dot u }
            val across = plan.map { it dot v }
            val area = (along.max() - along.min()) * (across.max() - across.min())
            if (area < bestArea) { bestArea = area; best = Triple(u, along.min()..along.max(), across.min()..across.max()) }
        }
        val (u, along, across) = best ?: return null
        val v = u.perp()
        var lengthRange = along
        var widthRange = across
        var dir = u
        // L'asse è il lato lungo.
        if (widthRange.endInclusive - widthRange.start > lengthRange.endInclusive - lengthRange.start && axis == null) {
            dir = v
            lengthRange = across
            widthRange = along.let { (-it.endInclusive)..(-it.start) }
        }
        val n = dir.perp()
        val mid = (widthRange.start + widthRange.endInclusive) / 2
        val thickness = propNum(e.id, IfcExport.PSET, "Thickness")?.let { it * scale } ?: (widthRange.endInclusive - widthRange.start)
        val a = dir * lengthRange.start + n * mid
        val b = dir * lengthRange.endInclusive + n * mid
        val height = pts.maxOf { it.z } * scale - floorZ
        return WallLine(e.id, a, b, thickness, height, phaseOf(e.id, "Pset_WallCommon"))
    }

    fun run(): IfcImport.Result {
        // Piani: dal più basso; ogni oggetto al piano che lo contiene.
        val storeys = f.ofType("IFCBUILDINGSTOREY").map { s ->
            val z = placement(s.ref(5)).o.z.takeIf { abs(it) > 1e-9 } ?: (s.num(9) ?: 0.0)
            s to z * scale
        }.sortedBy { it.second }.ifEmpty { listOf(StepEntity(0, "IFCBUILDINGSTOREY", emptyList()) to 0.0) }
        val storeyOf = HashMap<Int, Int>()
        fun assign(obj: Int, storeyId: Int) { if (obj !in storeyOf) storeyOf[obj] = storeyId }
        for (rel in f.ofType("IFCRELCONTAINEDINSPATIALSTRUCTURE")) rel.ref(5)?.let { s -> rel.refs(4).forEach { assign(it, s) } }
        // Stanze aggregate al piano (anche passando da IfcSpace o IfcZone intermedi).
        val aggregates = f.ofType("IFCRELAGGREGATES").associate { (it.ref(4) ?: -1) to it.refs(5) }
        fun spread(parent: Int, storeyId: Int) { for (c in aggregates[parent].orEmpty()) { assign(c, storeyId); spread(c, storeyId) } }
        for ((s, _) in storeys) spread(s.id, s.id)
        val defaultStorey = storeys.first().first.id
        fun storeyIndexOf(obj: Int) = storeys.indexOfFirst { it.first.id == (storeyOf[obj] ?: defaultStorey) }.coerceAtLeast(0)

        // Vuoti e riempimenti: porta/finestra → varco → muro.
        val voidOf = f.ofType("IFCRELVOIDSELEMENT").associate { (it.ref(5) ?: -1) to (it.ref(4) ?: -1) }
        val fillOf = f.ofType("IFCRELFILLSELEMENT").associate { (it.ref(5) ?: -1) to (it.ref(4) ?: -1) }
        val filledOpenings = fillOf.values.toSet()

        val known = IfcImport.WALLS + setOf("IFCSPACE", "IFCDOOR", "IFCWINDOW", "IFCOPENINGELEMENT", "IFCCOLUMN", "IFCBUILDINGSTOREY", "IFCBUILDING", "IFCSITE")
        for (e in f.entities.values) {
            if (e.type in known || e.ref(5) == null || f[e.ref(5)]?.type != "IFCLOCALPLACEMENT") continue
            if (e.args.size >= 7 && f[e.ref(6)]?.type == "IFCPRODUCTDEFINITIONSHAPE") skipped.countOne(e.type)
        }

        val floors = mutableListOf<Floor>()
        val roomGuids = HashMap<Pair<Long, Long>, String>()
        val guids = f.entities.values.mapNotNull { e -> e.str(0)?.takeIf { e.type.startsWith("IFC") && IfcGuid.isValid(it) } }.toSet()
        var nWalls = 0; var nFree = 0; var nDoors = 0; var nWindows = 0; var nPassages = 0; var nColumns = 0; var nRooms = 0

        storeys.forEachIndexed { index, (storey, z) ->
            val floorId = propNum(storey.id, IfcExport.PSET, "FloorId")?.toLong() ?: (index + 1L)
            if (psets[storey.id]?.containsKey(IfcExport.PSET) == true) fromSagoma = true
            val mine = { e: StepEntity -> storeyIndexOf(e.id) == index }
            val walls = f.ofType(*IfcImport.WALLS.toTypedArray()).filter(mine).mapNotNull { wallLine(it, z) }
            nWalls += walls.size
            val wallById = walls.associateBy { it.id }

            // Stanze dagli IfcSpace.
            data class SpaceIn(val e: StepEntity, val interior: List<Vec2>, val height: Double)
            val spaces = f.ofType("IFCSPACE").filter(mine).mapNotNull { s ->
                val solid = solids(s).maxByOrNull { Polygon.area(it.base.map(::plan)) } ?: return@mapNotNull null
                if (abs(solid.extrusion.z) < abs(solid.extrusion.x) + abs(solid.extrusion.y)) return@mapNotNull null
                val pts = simplify(solid.base.map(::plan))
                if (pts.size < 3 || Polygon.area(pts) < 100.0) return@mapNotNull null
                SpaceIn(s, pts, abs(solid.extrusion.z) * scale)
            }
            var nextId = 1L
            val usedIds = HashSet<Long>()
            val rooms = spaces.map { sp ->
                val id = propNum(sp.e.id, IfcExport.PSET, "RoomId")?.toLong()?.takeIf { it > 0 && usedIds.add(it) }
                    ?: run { while (nextId in usedIds) nextId++; nextId.also { usedIds += it } }
                sp.e.str(0)?.let { roomGuids[floorId to id] = it }
                val type = propStr(sp.e.id, IfcExport.PSET, "RoomType")?.let { t -> RoomType.entries.firstOrNull { it.name == t } }
                    ?: guessType(sp.e.str(2), sp.e.str(7), sp.e.enum(9) == "EXTERNAL")
                // Per ogni lato, il muro che lo chiude: parallelo e con la faccia sul lato.
                val n = sp.interior.size
                val thick = MutableList(n) { Room.WALL_THICKNESS }
                val removed = mutableSetOf<Int>()
                for (i in 0 until n) {
                    val a = sp.interior[i]
                    val b = sp.interior[(i + 1) % n]
                    val wantedId = propNum(sp.e.id, IfcExport.PSET, "RoomId")?.toLong()
                    val (wall, off) = faceWall(walls, a, b, wantedId) ?: run { if (walls.isNotEmpty() && !type.outdoor) removed += i; null } ?: continue
                    // Lato dentro il muro (muro in comune più sottile da questa parte): la linea resta sulla mezzeria del muro.
                    thick[i] = if (off < wall.thickness / 2 - 3) (2 * off).coerceAtLeast(5.0) else wall.thickness
                }
                val points = Polygon.inset(sp.interior, thick.map { -it / 2 })
                Room(
                    id = id, name = sp.e.str(2)?.takeIf { it.isNotBlank() } ?: type.defaultName, type = type, points = points,
                    ceilingHeight = propNum(sp.e.id, IfcExport.PSET, "CeilingHeight")?.let { it * scale } ?: sp.height.takeIf { it > 100 } ?: Room.DEFAULT_CEILING_HEIGHT,
                    wallThickness = thick.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: Room.WALL_THICKNESS,
                    removedWalls = if (type.outdoor) emptySet() else removed,
                ).let { r -> r.copy(wallThicknesses = thick.withIndex().filter { it.value != r.wallThickness }.associate { it.index to it.value }) }
            }
            nRooms += rooms.size

            // Muri che non chiudono stanze: muri singoli. Un muro chiude una stanza se una sua faccia corre sul bordo di uno
            // spazio (provato in tre punti); nei file usciti da sagoma lo dicono anche le sue proprietà.
            val boundaries = spaces.map { it.interior }
            val roomIds = rooms.map { it.id }.toSet()
            fun isFree(wl: WallLine): Boolean {
                if (propNum(wl.id, IfcExport.PSET, "FreeWallId") != null) return true
                if (propNum(wl.id, IfcExport.PSET, "RoomId")?.toLong() in roomIds) return false
                val n = wl.u.perp()
                return listOf(0.25, 0.5, 0.75).none { k ->
                    val c = wl.a + (wl.b - wl.a) * k
                    listOf(1.0, -1.0).any { side ->
                        val p = c + n * (side * wl.thickness / 2)
                        boundaries.any { poly -> poly.indices.any { j -> Polygon.distanceToSegment(p, poly[j], poly[(j + 1) % poly.size]) < 3.0 } }
                    }
                }
            }
            val freeLines = walls.filter(::isFree)
            val free = freeLines.mapIndexed { k, wl ->
                FreeWall(
                    id = propNum(wl.id, IfcExport.PSET, "FreeWallId")?.toLong() ?: (k + 1L), start = wl.a, end = wl.b, thickness = wl.thickness,
                    height = wl.height.takeIf { it > 1 }, phase = wl.phase,
                )
            }
            nFree += free.size
            val freeOf = freeLines.zip(free).associate { it.first.id to it.second.id }

            // Porte, finestre e passaggi.
            val holes = mutableListOf<Hole>()
            // Le finestre da tetto (anche quelle del tetto del professionista) stanno nel tetto, non nei muri.
            for (fill in f.ofType("IFCDOOR", "IFCWINDOW").filter(mine).filter { !(it.type == "IFCWINDOW" && it.enum(10) == "SKYLIGHT") }) {
                val opening = fillOf[fill.id]
                val wall = opening?.let { voidOf[it] }?.let { wallById[it] }
                holes += hole(fill, opening?.let { f[it] }, wall, z, filled = true) ?: continue
            }
            for (op in f.ofType("IFCOPENINGELEMENT")) {
                if (op.id in filledOpenings) continue
                val wall = voidOf[op.id]?.let { wallById[it] } ?: continue
                holes += hole(op, op, wall, z, filled = false) ?: continue
            }
            var openingId = 1L
            val roomOpenings = HashMap<Long, MutableList<Opening>>()
            val freeOpenings = HashMap<Long, MutableList<Opening>>()
            for (h in holes) {
                val kind = h.kind ?: when {
                    !h.filled -> if (h.recess) OpeningKind.Niche else if (h.sill < 5) OpeningKind.Passage else continue
                    h.isDoor -> if (h.width > 110) OpeningKind.Door2 else OpeningKind.Door
                    h.sill < 10 -> if (h.width > 120) OpeningKind.Balcony2 else OpeningKind.Balcony1
                    else -> if (h.width > 100) OpeningKind.Window2 else OpeningKind.Window1
                }
                val wantedRoom = propNum(h.id, IfcExport.PSET, "RoomId")?.toLong()
                val dir = h.wall?.u
                // Muro di stanza più vicino (prima le stanze al chiuso, poi balconi), oppure il muro singolo.
                val target = rooms.sortedBy { if (it.id == wantedRoom) 0 else if (it.type.outdoor) 2 else 1 }.flatMap { r ->
                    (0 until r.wallCount).map { i -> Triple(r, i, Polygon.distanceToSegment(h.center, r.wallStart(i), r.wallEnd(i))) }
                }.filter { (r, i, d) ->
                    val u = (r.wallEnd(i) - r.wallStart(i)).normalized()
                    d <= r.thicknessOf(i) / 2 + 3 && (dir == null || abs(u.cross(dir)) < 0.05)
                }.minByOrNull { (r, i, d) ->
                    // La nicchia va alla stanza dalla cui parte è scavata.
                    val wrongSide = kind == OpeningKind.Niche && (r.outwardOf(i) dot h.offset) > 0.5
                    d + (if (r.id == wantedRoom) -1000.0 else if (r.type.outdoor) 50.0 else 0.0) + (if (wrongSide) 500.0 else 0.0)
                }
                val opening = { wallIndex: Int, position: Double ->
                    looks(h, Opening(
                        id = propNum(h.id, IfcExport.PSET, "OpeningId")?.toLong() ?: openingId++, kind = kind, wallIndex = wallIndex,
                        position = position, width = h.width, height = h.height, sillHeight = if (kind.hasSill) h.sill else 0.0,
                        hingeLeft = (prop(h.id, IfcExport.PSET, "HingeLeft") as? StepValue.Enum)?.name != "F",
                        opensInward = (prop(h.id, IfcExport.PSET, "OpensInward") as? StepValue.Enum)?.name != "F",
                        sliding = (prop(h.id, IfcExport.PSET, "Sliding") as? StepValue.Enum)?.name == "T",
                        phase = h.phase,
                    ))
                }
                if (target != null) {
                    val (r, i, _) = target
                    val pos = (h.center - r.wallStart(i)) dot (r.wallEnd(i) - r.wallStart(i)).normalized()
                    roomOpenings.getOrPut(r.id) { mutableListOf() } += opening(i, pos)
                } else {
                    val fwId = h.wall?.let { freeOf[it.id] } ?: continue
                    val fw = free.first { it.id == fwId }
                    freeOpenings.getOrPut(fwId) { mutableListOf() } += opening(0, (h.center - fw.start) dot (fw.end - fw.start).normalized())
                }
                when {
                    kind == OpeningKind.Passage || kind == OpeningKind.Niche -> nPassages++
                    h.isDoor || !kind.glazed -> nDoors++
                    else -> nWindows++
                }
            }

            val columns = f.ofType("IFCCOLUMN").filter(mine).mapNotNull { c ->
                val wl = wallLine(c, z) ?: return@mapNotNull null
                val u = wl.u
                Column(
                    id = nColumns + 1L, center = (wl.a + wl.b) / 2.0, width = wl.length.coerceAtLeast(1.0), depth = wl.thickness.coerceAtLeast(1.0),
                    rotation = deg(atan2(u.y, u.x)),
                ).also { nColumns++ }
            }

            val plan = FloorPlan(
                rooms = rooms.map { r -> r.copy(openings = roomOpenings[r.id].orEmpty()) },
                freeWalls = free.map { fw -> fw.copy(openings = freeOpenings[fw.id].orEmpty()) },
                columns = columns,
            )
            floors += Floor(id = floorId, name = storey.str(2)?.takeIf { it.isNotBlank() } ?: Building.floorName(index), plan = plan)
        }
        // Interpiano: distanza dal piano di sopra; l'ultimo segue le stanze.
        val built = floors.mapIndexed { i, fl ->
            val next = storeys.getOrNull(i + 1)?.second
            val level = propNum(storeys[i].first.id, IfcExport.PSET, "LevelHeight")?.let { it * scale }
                ?: next?.let { it - storeys[i].second }?.takeIf { it > 100 }
            if (level != null) fl.copy(levelHeight = level) else fl.copy(levelHeight = Floor.autoLevelHeight(fl.plan))
        }
        val recentered = if (fromSagoma) built else recenter(built)
        val building = Building(recentered.ifEmpty { listOf(Floor(1, Building.floorName(0))) }, site = HouseSite(northDeg = northDeg()))
        return IfcImport.Result(
            building,
            IfcImport.Report(built.size, nRooms, nWalls, nFree, nDoors, nWindows, nPassages, nColumns, fromSagoma, skipped.toMap()),
            roomGuids, guids,
        )
    }

    fun hole(product: StepEntity, opening: StepEntity?, wall: WallLine?, floorZ: Double, filled: Boolean): Hole? {
        val isDoor = product.type == "IFCDOOR"
        val pts = (opening?.let { solids(it) }.orEmpty().ifEmpty { solids(product) }).flatMap { it.points }
        if (pts.isEmpty()) return null
        val plan = pts.map(::plan)
        val u = wall?.u ?: run {
            val h = convexHull(plan)
            h.indices.map { h[(it + 1) % h.size] - h[it] }.maxByOrNull { it.length }?.normalized() ?: return null
        }
        val along = plan.map { it dot u }
        val center2 = plan.reduce { acc, p -> acc + p } / plan.size.toDouble()
        val width = product.num(9)?.takeIf { filled && it > 0 }?.let { it * scale } ?: (along.max() - along.min())
        val bottom = pts.minOf { it.z } * scale - floorZ
        val top = pts.maxOf { it.z } * scale - floorZ
        val height = product.num(8)?.takeIf { filled && it > 0 }?.let { it * scale } ?: (top - bottom)
        // Centro sulla mezzeria del muro (il varco può sporgere di qua e di là).
        val center = wall?.let { w -> w.a + w.u * ((center2 - w.a) dot w.u) } ?: center2
        val sill = propNum(product.id, IfcExport.PSET, "SillHeight")?.let { it * scale } ?: bottom.coerceAtLeast(0.0)
        val kind = propStr(product.id, IfcExport.PSET, "Kind")?.let { k -> OpeningKind.entries.firstOrNull { it.name == k } }
        if (kind != null) fromSagoma = true
        val common = if (isDoor) "Pset_DoorCommon" else "Pset_WindowCommon"
        return Hole(
            product.id, kind, isDoor, filled, wall, center, width, sill, height, phaseOf(product.id, common),
            recess = !filled && product.enum(8) == "RECESS", offset = center2 - center,
            doorOperation = if (isDoor) doorOperation(product) else null,
            panelOperations = if (product.type == "IFCWINDOW") panelOperations(product) else emptyList(),
        )
    }

    /**
     * Muro con la faccia sul lato a→b di una stanza (filo interno): parallelo, sovrapposto, con il lato sulla faccia o dentro
     * il muro. Restituisce anche la distanza del lato dalla mezzeria.
     */
    fun faceWall(walls: List<WallLine>, a: Vec2, b: Vec2, roomId: Long? = null): Pair<WallLine, Double>? {
        val len = a.distanceTo(b)
        if (len < 1.0) return null
        val u = (b - a) / len
        val mid = (a + b) / 2.0
        class Candidate(val w: WallLine, val off: Double, val overlap: Double)
        return walls.mapNotNull { w ->
            if (abs(w.u.cross(u)) > 0.02) return@mapNotNull null
            val off = abs((mid - w.a).cross(w.u))
            if (off > w.thickness / 2 + 3.0 + 0.1 * w.thickness) return@mapNotNull null
            // Quanto del lato corre lungo il muro: almeno metà (o metà del muro, se il muro è più corto).
            val ta = (a - w.a) dot w.u
            val tb = (b - w.a) dot w.u
            val overlap = minOf(maxOf(ta, tb), w.length) - maxOf(minOf(ta, tb), 0.0)
            if (overlap < 0.5 * minOf(len, w.length)) return@mapNotNull null
            Candidate(w, off, overlap)
        }.minByOrNull { c ->
            // Prima la faccia giusta (lato a mezzo spessore), poi il muro di questa stanza, poi il più sovrapposto.
            abs(c.off - c.w.thickness / 2) + (if (roomId != null && propNum(c.w.id, IfcExport.PSET, "RoomId")?.toLong() == roomId) -0.5 else 0.0) - c.overlap / len * 0.1
        }?.let { it.w to it.off }
    }


    /** Toglie i punti doppi e quelli allineati. */
    fun simplify(pts: List<Vec2>): List<Vec2> {
        val out = pts.filterIndexed { i, p -> p.distanceTo(pts[(i + 1) % pts.size]) > 0.5 }.toMutableList()
        var changed = true
        while (changed && out.size > 3) {
            changed = false
            for (k in out.indices) {
                val p0 = out[(k - 1 + out.size) % out.size]
                val p2 = out[(k + 1) % out.size]
                if (Polygon.distanceToSegment(out[k], p0, p2) < 0.3) { out.removeAt(k); changed = true; break }
            }
        }
        return out
    }

    /** Tipo di stanza dal nome (italiano o inglese), altrimenti "Altro". */
    fun guessType(name: String?, longName: String?, external: Boolean): RoomType {
        val s = "${name.orEmpty()} ${longName.orEmpty()}".lowercase()
        fun has(vararg w: String) = w.any { it in s }
        return when {
            has("balcon", "loggia", "poggiolo") -> RoomType.Balcone
            has("terrazz", "terrace", "patio") -> RoomType.Terrazza
            has("cucin", "kitchen", "cuisine", "cocina") -> RoomType.Cucina
            has("bagno", "bath", "wc", "toilet", "servizi", "salle de bain", "baño") -> RoomType.Bagno
            has("camerett", "kid", "child") -> RoomType.Cameretta
            has("camera", "letto", "bed", "chambre", "dormitorio") -> RoomType.Camera
            has("soggiorno", "salotto", "living", "salon", "sala") -> RoomType.Soggiorno
            has("studio", "office", "bureau") -> RoomType.Studio
            has("corridoio", "disimpegno", "ingresso", "hall", "corridor", "couloir", "pasillo", "entrance") -> RoomType.Corridoio
            external -> RoomType.Terrazza
            else -> RoomType.Altro
        }
    }

    /** Sposta la casa vicino all'origine (i file dei professionisti hanno spesso coordinate del lotto molto grandi). */
    fun recenter(floors: List<Floor>): List<Floor> {
        val pts = floors.flatMap { fl -> fl.plan.rooms.flatMap { it.points } + fl.plan.freeWalls.flatMap { listOf(it.start, it.end) } }
        if (pts.isEmpty()) return floors
        val d = Vec2(pts.minOf { it.x }, pts.minOf { it.y }) - Vec2(100.0, 100.0)
        if (d.length < 1000) return floors
        return floors.map { fl ->
            fl.copy(
                plan = fl.plan.copy(
                    rooms = fl.plan.rooms.map { r -> r.copy(points = r.points.map { it - d }) },
                    freeWalls = fl.plan.freeWalls.map { w -> w.copy(start = w.start - d, end = w.end - d) },
                    columns = fl.plan.columns.map { c -> c.copy(center = c.center - d) },
                ),
            )
        }
    }

    /** Nord del file (TrueNorth del contesto) in gradi orari dall'alto del disegno. */
    fun northDeg(): Double {
        val ctx = f.ofType("IFCGEOMETRICREPRESENTATIONCONTEXT").firstOrNull() ?: return 0.0
        val n = vec(ctx.ref(5), V3(0.0, 1.0, 0.0))
        val d = deg(atan2(n.x, n.y))
        return ((d % 360) + 360) % 360
    }
}

private fun deg(rad: Double) = rad * 180.0 / kotlin.math.PI
