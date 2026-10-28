package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.geometry.expandCurves
import com.sagoma.planimetria.geometry.interiorOutline
import com.sagoma.planimetria.geometry.pieces
import com.sagoma.planimetria.geometry.shiftedEndDeltas
import com.sagoma.planimetria.geometry.usableArea
import com.sagoma.planimetria.geometry.wallShift
import com.sagoma.planimetria.ifc.StepWriter.Companion.NULL
import com.sagoma.planimetria.ifc.StepWriter.Companion.list
import com.sagoma.planimetria.ifc.StepWriter.Companion.num
import com.sagoma.planimetria.ifc.StepWriter.Companion.str
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.GarageDoor
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.SashType
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.WindowShape
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Esportazione IFC (IFC4) per i programmi dei professionisti: SOLO la planimetria nuda, cioè piani, stanze (IfcSpace),
 * muri, porte, finestre, passaggi, colonne, travi e scale. Gli arredi del catalogo sagoma non escono MAI (decisione del
 * 16/10/2026), e nemmeno impianti, finiture, foto, sfondi e note.
 *
 * Ogni oggetto porta l'insieme di proprietà "Sagoma" (id della stanza, numero del muro, tipo di apertura…) e un GlobalId
 * sempre uguale, così al ritorno ([IfcImport], [IfcRoundTrip]) sagoma riconosce le stanze e vi rimette gli arredi.
 *
 * Coordinate: sagoma lavora in cm con la y verso il basso dello schermo; nel file millimetri con la Y verso l'alto
 * del disegno (il nord di default), senza ribaltare la pianta.
 */
object IfcExport {
    /** Nome dell'insieme di proprietà con gli id di sagoma. */
    const val PSET = "Sagoma"

    /** Modo di aprirsi della porta basculante (USERDEFINED: l'IFC4 non ha un nome per lei). */
    const val UP_AND_OVER = "UPANDOVER"

    /**
     * Testo del file .ifc. `name`: nome del progetto; `timestamp`: data e ora da scrivere nell'intestazione (ISO 8601);
     * `key`: identifica il progetto nei GlobalId (due progetti diversi non devono avere gli stessi id).
     */
    fun export(building: Building, name: String, key: String = name, timestamp: String = "2026-01-01T00:00:00"): String =
        IfcExporter(building, name, key).run(timestamp)

    /** Coordinate del file (mm, Y in su) da quelle di sagoma (cm, y in giù). */
    internal fun toIfc(p: Vec2) = Vec2(p.x * 10, -p.y * 10)
    internal fun fromIfc(x: Double, y: Double) = Vec2(x / 10, -y / 10)
}

/** Un pezzo di muro scritto nel file: mezzeria da `a` a `b` (cm sagoma), spessore, chi lo ha generato. */
internal class WallPiece(
    val a: Vec2,
    val b: Vec2,
    val thickness: Double,
    /** Allungamento agli estremi (cm) per chiudere gli angoli. */
    val extStart: Double,
    val extEnd: Double,
    val top: (Double) -> Double,
    val sloped: Boolean,
    val roomId: Long?,
    val wallIndex: Int,
    val freeWallId: Long?,
    val phase: Phase,
    val external: Boolean,
) {
    val length get() = a.distanceTo(b)
    val u get() = (b - a) / length
    var ref: String = ""
    var placement: String = ""
}

private class IfcExporter(val building: Building, val name: String, val key: String) {
    val w = StepWriter()
    var guidCount = 0
    fun guid(k: String) = str(IfcGuid.of("$key|$k"))
    fun newGuid() = guid("rel/${guidCount++}")

    // Elementi geometrici di uso comune.
    val origin3 = point(0.0, 0.0, 0.0)
    val zUp = dir(0.0, 0.0, 1.0)
    val xDir = dir(1.0, 0.0, 0.0)
    lateinit var body: String
    lateinit var axisCtx: String

    fun point(x: Double, y: Double, z: Double) = w.add("IFCCARTESIANPOINT", list(listOf(num(x), num(y), num(z))))
    fun point2(x: Double, y: Double) = w.add("IFCCARTESIANPOINT", list(listOf(num(x), num(y))))
    fun dir(x: Double, y: Double, z: Double) = w.add("IFCDIRECTION", list(listOf(num(x), num(y), num(z))))
    fun axis3(loc: String, axis: String = zUp, ref: String = xDir) = w.add("IFCAXIS2PLACEMENT3D", loc, axis, ref)
    fun local(rel: String?, placement: String) = w.add("IFCLOCALPLACEMENT", rel ?: NULL, placement)

    /** Poligono chiuso nel piano del profilo (mm). */
    fun closedPolyline(pts: List<Vec2>): String {
        val refs = pts.map { point2(it.x, it.y) }
        return w.add("IFCPOLYLINE", list(refs + refs.first()))
    }

    fun profile(pts: List<Vec2>) = w.add("IFCARBITRARYCLOSEDPROFILEDEF", ".AREA.", NULL, closedPolyline(pts))

    fun extrusion(pts: List<Vec2>, position: String, depth: Double) =
        w.add("IFCEXTRUDEDAREASOLID", profile(pts), position, dir(0.0, 0.0, 1.0), num(depth))

    fun shape(vararg reps: String) = w.add("IFCPRODUCTDEFINITIONSHAPE", NULL, NULL, list(reps.toList()))
    fun bodyRep(items: List<String>) = w.add("IFCSHAPEREPRESENTATION", body, "'Body'", "'SweptSolid'", list(items))

    fun props(name: String, objects: List<String>, values: List<Pair<String, String>>) {
        if (objects.isEmpty() || values.isEmpty()) return
        val p = values.map { (n, v) -> w.add("IFCPROPERTYSINGLEVALUE", str(n), NULL, v, NULL) }
        val set = w.add("IFCPROPERTYSET", newGuid(), NULL, str(name), NULL, list(p))
        w.add("IFCRELDEFINESBYPROPERTIES", newGuid(), NULL, NULL, NULL, list(objects), set)
    }

    fun label(s: String) = "IFCLABEL(${str(s)})"
    fun text(s: String) = "IFCTEXT(${str(s)})"
    fun int(v: Long) = "IFCINTEGER($v)"
    fun length(cm: Double) = "IFCLENGTHMEASURE(${num(cm * 10)})"
    fun bool(v: Boolean) = "IFCBOOLEAN(${StepWriter.bool(v)})"

    fun status(p: Phase) = label(when (p) { Phase.Existing -> "EXISTING"; Phase.Demolish -> "DEMOLISH"; Phase.New -> "NEW" })

    fun run(timestamp: String): String {
        // Unità: millimetri, metri quadri, metri cubi, radianti.
        val units = w.add(
            "IFCUNITASSIGNMENT",
            list(
                listOf(
                    w.add("IFCSIUNIT", "*", ".LENGTHUNIT.", ".MILLI.", ".METRE."),
                    w.add("IFCSIUNIT", "*", ".AREAUNIT.", NULL, ".SQUARE_METRE."),
                    w.add("IFCSIUNIT", "*", ".VOLUMEUNIT.", NULL, ".CUBIC_METRE."),
                    w.add("IFCSIUNIT", "*", ".PLANEANGLEUNIT.", NULL, ".RADIAN."),
                ),
            ),
        )
        // Nord: northDeg è in senso orario dall'alto del disegno, che nel file è +Y.
        val north = rad(building.site.northDeg)
        val trueNorth = w.add("IFCDIRECTION", list(listOf(num(sin(north)), num(cos(north)))))
        val world = axis3(origin3)
        val ctx = w.add("IFCGEOMETRICREPRESENTATIONCONTEXT", NULL, "'Model'", "3", num(1.0e-5), world, trueNorth)
        body = w.add("IFCGEOMETRICREPRESENTATIONSUBCONTEXT", "'Body'", "'Model'", "*", "*", "*", "*", ctx, NULL, ".MODEL_VIEW.", NULL)
        axisCtx = w.add("IFCGEOMETRICREPRESENTATIONSUBCONTEXT", "'Axis'", "'Model'", "*", "*", "*", "*", ctx, NULL, ".GRAPH_VIEW.", NULL)
        val project = w.add("IFCPROJECT", guid("project"), NULL, str(name), NULL, NULL, NULL, NULL, list(listOf(ctx)), units)

        val sitePlacement = local(null, axis3(origin3))
        val place = building.site.place
        val site = w.add(
            "IFCSITE", guid("site"), NULL, str(place?.name ?: "Sito"), NULL, NULL, sitePlacement, NULL, NULL, ".ELEMENT.",
            place?.let { compound(it.latitude) } ?: NULL, place?.let { compound(it.longitude) } ?: NULL, NULL, NULL, NULL,
        )
        val buildingPlacement = local(sitePlacement, axis3(origin3))
        val house = w.add("IFCBUILDING", guid("building"), NULL, str(name), NULL, NULL, buildingPlacement, NULL, NULL, ".ELEMENT.", NULL, NULL, NULL)
        w.add("IFCRELAGGREGATES", newGuid(), NULL, NULL, NULL, project, list(listOf(site)))
        w.add("IFCRELAGGREGATES", newGuid(), NULL, NULL, NULL, site, list(listOf(house)))

        val storeys = building.floors.mapIndexed { index, floor ->
            val elevation = building.elevation(index)
            val placement = local(buildingPlacement, axis3(point(0.0, 0.0, elevation * 10)))
            val storey = w.add(
                "IFCBUILDINGSTOREY", guid("floor/${floor.id}"), NULL, str(floor.name), NULL, NULL, placement, NULL, NULL, ".ELEMENT.",
                num(elevation * 10),
            )
            props(IfcExport.PSET, listOf(storey), listOf("FloorId" to int(floor.id), "LevelHeight" to length(floor.levelHeight)))
            floorContent(floor, storey, placement)
            storey
        }
        w.add("IFCRELAGGREGATES", newGuid(), NULL, NULL, NULL, house, list(storeys))
        writeWindowTypes(project)

        val header = listOf(
            "FILE_DESCRIPTION(('ViewDefinition [ReferenceView_V1.2]'),'2;1');",
            "FILE_NAME(${str("$name.ifc")},${str(timestamp)},(''),(''),'sagoma','sagoma','');",
            "FILE_SCHEMA(('IFC4'));",
        ).joinToString("\n")
        return w.text(header)
    }

    /** Gradi decimali come IfcCompoundPlaneAngleMeasure (gradi, primi, secondi, milionesimi). */
    fun compound(deg: Double): String {
        val sign = if (deg < 0) -1 else 1
        var rest = abs(deg)
        val d = rest.toInt(); rest = (rest - d) * 60
        val m = rest.toInt(); rest = (rest - m) * 60
        val s = rest.toInt(); rest = (rest - s) * 1e6
        return list(listOf(d * sign, m * sign, s * sign, rest.toInt() * sign).map { it.toString() })
    }

    fun floorContent(floor: Floor, storey: String, storeyPlacement: String) {
        val plan = floor.plan
        val expanded = plan.expandCurves().plan
        val contained = mutableListOf<String>()

        // Stanze → IfcSpace sul filo interno dei muri, alte quanto il soffitto.
        val spaces = mutableListOf<String>()
        for (room in plan.rooms) {
            val outline = room.interiorOutline().map(IfcExport::toIfc)
            if (outline.size < 3 || Polygon.area(outline) < 1.0) continue
            val placement = local(storeyPlacement, axis3(origin3))
            val height = if (room.outdoor) room.parapetHeight else room.ceilingHeight
            val solid = extrusion(outline, axis3(origin3), height * 10)
            val space = w.add(
                "IFCSPACE", guid("room/${floor.id}/${room.id}"), NULL, str(room.name), NULL, NULL, placement,
                shape(bodyRep(listOf(solid))), str(room.type.label), ".ELEMENT.", if (room.outdoor) ".EXTERNAL." else ".INTERNAL.", NULL,
            )
            spaces += space
            props(
                IfcExport.PSET, listOf(space),
                listOf(
                    "RoomId" to int(room.id), "RoomType" to label(room.type.name),
                    "CeilingHeight" to length(room.ceilingHeight),
                ),
            )
            props("Pset_SpaceCommon", listOf(space), listOf("IsExternal" to bool(room.outdoor)))
            val area = w.add("IFCQUANTITYAREA", "'NetFloorArea'", NULL, NULL, num(room.usableArea() / 10000.0), NULL)
            val qto = w.add("IFCELEMENTQUANTITY", newGuid(), NULL, "'Qto_SpaceBaseQuantities'", NULL, NULL, list(listOf(area)))
            w.add("IFCRELDEFINESBYPROPERTIES", newGuid(), NULL, NULL, NULL, list(listOf(space)), qto)
        }
        if (spaces.isNotEmpty()) w.add("IFCRELAGGREGATES", newGuid(), NULL, NULL, NULL, storey, list(spaces))

        // Muri: ogni muro una volta sola (quello in comune tra due stanze lo scrive la prima).
        val pieces = wallPieces(expanded, floor)
        for (p in pieces) contained += writeWall(p, floor, storeyPlacement)

        // Porte, finestre e passaggi: sul pezzo di muro che le porta.
        val hosts = expanded.rooms + expanded.freeWalls.flatMap { fw -> fw.pieces().map { it.host.copy(id = -fw.id) } }
        for (room in hosts) for (o in room.openings) {
            if (o.wallIndex >= room.wallCount) continue
            val (a, b) = Openings.span(room, o)
            val piece = hostOf(pieces, a, b) ?: continue
            writeOpening(piece, room, o, a, b, floor)?.let { contained += it }
        }

        // Colonne, travi e scale: scatole e gradini, utili al professionista come ingombri.
        for (c in plan.columns) {
            val h = Structure.columnHeight(plan, c, floor.levelHeight)
            val placement = local(storeyPlacement, axis3(origin3))
            val solid = extrusion(Structure.outline(c).map(IfcExport::toIfc), axis3(origin3), h * 10)
            contained += w.add("IFCCOLUMN", guid("column/${floor.id}/${c.id}"), NULL, "'Colonna'", NULL, NULL, placement, shape(bodyRep(listOf(solid))), NULL, ".COLUMN.")
        }
        for (bm in plan.beams) {
            val top = Structure.ceilingAt(plan, bm.mid, floor.levelHeight)
            val placement = local(storeyPlacement, axis3(origin3))
            val solid = extrusion(Structure.outline(bm).map(IfcExport::toIfc), axis3(point(0.0, 0.0, (top - bm.depth) * 10)), bm.depth * 10)
            contained += w.add("IFCBEAM", guid("beam/${floor.id}/${bm.id}"), NULL, "'Trave'", NULL, NULL, placement, shape(bodyRep(listOf(solid))), NULL, ".BEAM.")
        }
        for (s in plan.stairs) {
            val layout = Stairs.layout(s, floor.levelHeight)
            val placement = local(storeyPlacement, axis3(origin3))
            val solids = layout.steps.map { st ->
                val bottom = (st.top - layout.riser).coerceAtLeast(0.0)
                extrusion(st.polygon.map(IfcExport::toIfc), axis3(point(0.0, 0.0, bottom * 10)), ((st.top - bottom).coerceAtLeast(1.0)) * 10)
            }
            if (solids.isEmpty()) continue
            contained += w.add("IFCSTAIR", guid("stair/${floor.id}/${s.id}"), NULL, "'Scala'", NULL, NULL, placement, shape(bodyRep(solids)), NULL, ".NOTDEFINED.")
        }
        if (contained.isNotEmpty()) w.add("IFCRELCONTAINEDINSPATIALSTRUCTURE", newGuid(), NULL, NULL, NULL, list(contained), storey)
    }

    /** Pezzo di muro che porta l'apertura da `a` a `b`: parallelo, con la mezzeria vicina e l'apertura dentro. */
    fun hostOf(pieces: List<WallPiece>, a: Vec2, b: Vec2): WallPiece? {
        val c = (a + b) / 2.0
        val d = b - a
        if (d.length < 1e-6) return null
        val v = d.normalized()
        return pieces.filter { p ->
            val u = p.u
            abs(u.x * v.y - u.y * v.x) < 0.02 && run {
                val t = (c - p.a) dot u
                t >= -1 && t <= p.length + 1 && Polygon.distanceToSegment(c, p.a, p.b) <= p.thickness / 2 + Openings.SHARED_WALL_TOLERANCE_CM
            }
        }.minByOrNull { Polygon.distanceToSegment(c, it.a, it.b) }
    }

    fun wallPieces(expanded: com.sagoma.planimetria.model.FloorPlan, floor: Floor): List<WallPiece> {
        val out = mutableListOf<WallPiece>()
        val indoor = expanded.rooms.filter { !it.outdoor }
        for (room in indoor) for (i in 0 until room.wallCount) {
            if (room.isRemoved(i) || room.wallLength(i) < 1.0) continue
            val n = room.wallCount
            val s0 = room.wallStart(i)
            val len = room.wallLength(i)
            val u = (room.wallEnd(i) - s0) / len
            val shift = room.outwardOf(i) * room.wallShift(i)
            val (dS, dE) = room.shiftedEndDeltas(i)
            val s = s0 + shift
            val core = dS..(len + dE)
            // Tratti già scritti da un'altra stanza (stesso muro visto dall'altra parte).
            val covered = out.filter { p ->
                val pu = p.u
                abs(pu.x * u.y - pu.y * u.x) < 0.01 && abs(((p.a - s).let { it.x * u.y - it.y * u.x })) < Openings.SHARED_WALL_TOLERANCE_CM
            }.map { p ->
                val t1 = (p.a - s) dot u
                val t2 = (p.b - s) dot u
                minOf(t1, t2)..maxOf(t1, t2)
            }
            var free = listOf(core)
            for (c in covered) free = free.flatMap { cut(it, c) }
            free = free.filter { it.endInclusive - it.start > 1.0 }
            val thick = room.thicknessOf(i)
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            fun extension(other: Int, atStart: Boolean): Double {
                if (room.isRemoved(other)) return 0.0
                val od = (room.wallEnd(other) - room.wallStart(other))
                if (od.length < 1e-6) return 0.0
                val cosA = (od.normalized() dot u).coerceIn(-1.0, 1.0)
                if (cosA > 0.9999) return 0.0 // muro dritto che continua (scalino): finisce pari
                val angle = acos(cosA)
                // Angolo vero: si allunga di mezzo spessore dell'altro; quasi dritto (curve): quanto basta a chiudere lo spicchio.
                return if (angle > rad(30.0)) room.thicknessOf(other) / 2 else thick / 2 * tan(angle / 2)
            }
            val sloped = Ceilings.effectiveCut(room, i) != null
            for (r in free) {
                val atStart = abs(r.start - core.start) < 0.01
                val atEnd = abs(r.endInclusive - core.endInclusive) < 0.01
                val t0 = r.start
                out += WallPiece(
                    a = s + u * r.start, b = s + u * r.endInclusive, thickness = thick,
                    extStart = if (atStart && dS == 0.0) extension(prev, true) else 0.0,
                    extEnd = if (atEnd && dE == 0.0) extension(next, false) else 0.0,
                    top = { t -> Ceilings.wallTopAt(room, i, (t0 + t).coerceIn(0.0, len)) },
                    sloped = sloped,
                    roomId = room.id, wallIndex = i, freeWallId = null, phase = room.phaseOf(i),
                    external = covered.isEmpty() && indoor.none { o -> o.id != room.id && touches(o, s + u * ((r.start + r.endInclusive) / 2)) },
                )
            }
        }
        for (fw in expanded.freeWalls) {
            if (fw.length < 1.0) continue
            val h = Structure.freeWallHeight(expanded, fw, floor.levelHeight)
            for (piece in fw.pieces()) {
                if (piece.length < 1.0) continue
                out += WallPiece(
                    piece.start, piece.end, piece.thickness, 0.0, 0.0, { h }, false,
                    roomId = null, wallIndex = 0, freeWallId = fw.id, phase = fw.phase, external = false,
                )
            }
        }
        return out
    }

    /** Il punto sta sul bordo della stanza `o` (lì il muro divide due stanze). */
    fun touches(o: Room, p: Vec2) = (0 until o.wallCount).any { j -> Polygon.distanceToSegment(p, o.wallStart(j), o.wallEnd(j)) < Openings.SHARED_WALL_TOLERANCE_CM }

    fun cut(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>): List<ClosedFloatingPointRange<Double>> {
        if (b.endInclusive <= a.start || b.start >= a.endInclusive) return listOf(a)
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        if (b.start > a.start) out += a.start..b.start
        if (b.endInclusive < a.endInclusive) out += b.endInclusive..a.endInclusive
        return out
    }

    /**
     * Muro: sistema locale con l'origine sull'inizio della mezzeria e l'asse X lungo il muro; il corpo è il profilo verticale
     * (lunghezza × altezza, anche con il soffitto inclinato delle mansarde) estruso attraverso lo spessore.
     */
    fun writeWall(p: WallPiece, floor: Floor, storeyPlacement: String): String {
        val a = IfcExport.toIfc(p.a)
        val ifcU = IfcExport.toIfc(p.b) - a
        val len = p.length
        val placement = local(storeyPlacement, axis3(point(a.x, a.y, 0.0), zUp, dir(ifcU.x / ifcU.length, ifcU.y / ifcU.length, 0.0)))
        p.placement = placement
        val samples = if (p.sloped) (0..16).map { len * it / 16 } else listOf(0.0, len)
        val topPts = samples.map { t -> Vec2(t, p.top(t)) }
        val profilePts = buildList {
            add(Vec2(-p.extStart, 0.0))
            add(Vec2(len + p.extEnd, 0.0))
            add(Vec2(len + p.extEnd, topPts.last().y))
            for (q in topPts.reversed()) add(q)
            add(Vec2(-p.extStart, topPts.first().y))
        }.map { Vec2(it.x * 10, it.y * 10) }.let(::simplify)
        val half = p.thickness / 2 * 10
        val position = axis3(point(0.0, half, 0.0), dir(0.0, -1.0, 0.0), xDir)
        val solid = extrusion(profilePts, position, p.thickness * 10)
        val axisCurve = w.add("IFCPOLYLINE", list(listOf(point2(0.0, 0.0), point2(len * 10, 0.0))))
        val axisRep = w.add("IFCSHAPEREPRESENTATION", axisCtx, "'Axis'", "'Curve2D'", list(listOf(axisCurve)))
        val key = if (p.freeWallId != null) "freewall/${floor.id}/${p.freeWallId}/${p.a}" else "wall/${floor.id}/${p.roomId}/${p.wallIndex}/${p.a}"
        val wall = w.add(
            "IFCWALL", guid(key), NULL, str(if (p.freeWallId != null) "Muro singolo" else "Muro"), NULL, NULL, placement,
            shape(axisRep, bodyRep(listOf(solid))), NULL, ".STANDARD.",
        )
        p.ref = wall
        props(
            IfcExport.PSET, listOf(wall),
            buildList {
                p.roomId?.let { add("RoomId" to int(it)); add("WallIndex" to int(p.wallIndex.toLong())) }
                p.freeWallId?.let { add("FreeWallId" to int(it)) }
                add("Thickness" to length(p.thickness))
            },
        )
        props("Pset_WallCommon", listOf(wall), listOf("IsExternal" to bool(p.external), "Status" to status(p.phase)))
        return wall
    }

    /** Toglie i punti allineati (soffitto piano su più campioni). */
    fun simplify(pts: List<Vec2>): List<Vec2> {
        val out = pts.filterIndexed { i, q -> i == 0 || q.distanceTo(pts[i - 1]) > 0.01 }.toMutableList()
        var changed = true
        while (changed && out.size > 3) {
            changed = false
            for (k in out.indices) {
                val p0 = out[(k - 1 + out.size) % out.size]
                val p1 = out[k]
                val p2 = out[(k + 1) % out.size]
                val cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)
                if (abs(cross) < 1e-6 * maxOf(1.0, p0.distanceTo(p2))) { out.removeAt(k); changed = true; break }
            }
        }
        return out
    }

    /** Scrive il varco nel muro e, se c'è, la porta o finestra che lo riempie (restituita: va nel piano). */
    fun writeOpening(p: WallPiece, room: Room, o: Opening, a: Vec2, b: Vec2, floor: Floor): String? {
        val t0 = minOf((a - p.a) dot p.u, (b - p.a) dot p.u)
        val t1 = maxOf((a - p.a) dot p.u, (b - p.a) dot p.u)
        val sill = if (o.kind.hasSill) o.sillHeight else 0.0
        val top = sill + o.height
        val rect = listOf(Vec2(t0, sill), Vec2(t1, sill), Vec2(t1, top), Vec2(t0, top)).map { Vec2(it.x * 10, it.y * 10) }
        // Varchi e finestre ad arco: la cima del vano segue l'arco (la stessa curva del 3D), a fettine dritte.
        // Finestre tonde (oblò): un cerchio largo quanto la finestra, dal davanzale in su.
        val arch = when {
            o.kind == OpeningKind.Passage && o.style.arched -> o.style
            o.shape == WindowShape.Arched -> PassageStyle.Arched
            else -> null
        }
        val outline = when {
            o.shape == WindowShape.Round -> {
                val r = (t1 - t0) / 2
                (0 until ARCH_SLICES * 2).map { k ->
                    val a = 2 * kotlin.math.PI * k / (ARCH_SLICES * 2)
                    Vec2((t0 + r + r * cos(a)) * 10, (sill + r + r * sin(a)) * 10)
                }
            }
            arch == null -> rect
            else -> buildList {
                add(Vec2(t0, sill)); add(Vec2(t1, sill))
                for (k in ARCH_SLICES downTo 0) {
                    val t = t0 + (t1 - t0) * k / ARCH_SLICES
                    add(Vec2(t, Openings.archTop(arch, t0, t1, sill, top, t)))
                }
            }.map { Vec2(it.x * 10, it.y * 10) }.let(::simplify)
        }
        val half = p.thickness / 2 * 10
        val openingPlacement = local(p.placement, axis3(origin3))
        val niche = o.kind == OpeningKind.Niche
        val cutter = if (niche) {
            // Nicchia: scava solo dalla faccia verso la stanza fino alla mezzeria del muro (come nel 3D), non passa di là.
            // Dall'altra parte del muro il sistema del profilo si gira anche lungo il muro, così la Y resta in alto.
            val side = nicheSide(p, room, o)
            extrusion(outline.map { Vec2(it.x * side, it.y) }, axis3(point(0.0, side * (half + 10), 0.0), dir(0.0, -side, 0.0), dir(side, 0.0, 0.0)), half + 10)
        } else {
            extrusion(outline, axis3(point(0.0, half + 10, 0.0), dir(0.0, -1.0, 0.0), xDir), p.thickness * 10 + 20)
        }
        val baseKey = "opening/${floor.id}/${room.id}/${o.id}"
        val opening = w.add(
            "IFCOPENINGELEMENT", guid(baseKey), NULL, if (niche) "'Nicchia'" else "'Apertura'", NULL, NULL, openingPlacement,
            shape(bodyRep(listOf(cutter))), NULL, if (niche) ".RECESS." else ".OPENING.",
        )
        w.add("IFCRELVOIDSELEMENT", newGuid(), NULL, NULL, NULL, p.ref, opening)
        // Ricorda chi è: tipo, ante, verso, misure scelte in sagoma (così al ritorno nulla si perde se non è stato toccato).
        val sagoma = listOf(
            "RoomId" to int(room.id), "OpeningId" to int(o.id), "Kind" to label(o.kind.name),
            "Sliding" to bool(o.sliding), "HingeLeft" to bool(o.hingeLeft), "OpensInward" to bool(o.opensInward),
        ) + looks(o)
        if (!o.kind.hasLeaves) {
            props(IfcExport.PSET, listOf(opening), sagoma + if (niche) listOf("SillHeight" to length(sill)) else emptyList())
            return null
        }
        val panel = extrusion(outline, axis3(point(0.0, 25.0, 0.0), dir(0.0, -1.0, 0.0), xDir), 50.0)
        val fillPlacement = local(openingPlacement, axis3(origin3))
        val widthMm = num((t1 - t0) * 10)
        val heightMm = num(o.height * 10)
        val isWindow = o.kind.glazed && o.kind != OpeningKind.Balcony1 && o.kind != OpeningKind.Balcony2
        val fill = if (isWindow) {
            val partitioning = partitioning(o.leaves)
            val window = w.add(
                "IFCWINDOW", guid("$baseKey/fill"), NULL, str(Openings.label(o)), NULL, NULL, fillPlacement, shape(bodyRep(listOf(panel))), NULL,
                heightMm, widthMm, ".WINDOW.", ".$partitioning.", userPartitioning(o.leaves),
            )
            // Come si aprono le ante: nell'IFC4 sta nel tipo della finestra (IfcWindowType con IfcWindowPanelProperties).
            windowTypes.getOrPut(partitioning to panelOperations(o)) { mutableListOf() } += window
            window
        } else {
            w.add(
                "IFCDOOR", guid("$baseKey/fill"), NULL, str(Openings.label(o)), NULL, NULL, fillPlacement, shape(bodyRep(listOf(panel))), NULL,
                heightMm, widthMm, ".DOOR.", ".${doorOperation(o)}.", if (o.garageDoor == GarageDoor.UpAndOver) "'${IfcExport.UP_AND_OVER}'" else NULL,
            )
        }
        w.add("IFCRELFILLSELEMENT", newGuid(), NULL, NULL, NULL, opening, fill)
        props(IfcExport.PSET, listOf(fill), sagoma + listOf("SillHeight" to length(sill)))
        val common = if (isWindow) "Pset_WindowCommon" else "Pset_DoorCommon"
        props(common, listOf(fill), listOf("IsExternal" to bool(p.external), "Status" to status(o.phase)))
        return fill
    }

    /** Da che parte del pezzo di muro (asse Y locale, +1 o -1) sta la stanza della nicchia. */
    fun nicheSide(p: WallPiece, room: Room, o: Opening): Double {
        val inward = room.outwardOf(o.wallIndex) * -1.0
        val ifcInward = Vec2(inward.x, -inward.y)
        val ifcU = IfcExport.toIfc(p.b) - IfcExport.toIfc(p.a)
        return if (ifcInward.x * -ifcU.y + ifcInward.y * ifcU.x >= 0) 1.0 else -1.0
    }

    /**
     * Aspetto scelto in sagoma (forma dell'arco, modo di aprirsi, modello, colore…): solo quello diverso dal solito, così
     * al ritorno si ritrova uguale anche se la stanza è stata spostata o cambiata.
     */
    fun looks(o: Opening): List<Pair<String, String>> {
        val d = Opening(0, o.kind, 0, 0.0, width = 0.0, height = 0.0)
        return buildList {
            if (o.style != d.style) add("Style" to label(o.style.name))
            if (o.slideOnWall) add("SlideOnWall" to bool(true))
            if (o.folding) add("Folding" to bool(true))
            if (o.frameless) add("Frameless" to bool(true))
            if (o.unequalLeaves) add("UnequalLeaves" to bool(true))
            if (o.sash != d.sash) add("Sash" to label(o.sash.name))
            if (o.windowModel != d.windowModel) add("WindowModel" to label(o.windowModel.name))
            if (o.doorModel != d.doorModel) add("DoorModel" to label(o.doorModel.name))
            if (o.shading != d.shading) add("Shading" to label(o.shading.name))
            if (o.frame != d.frame) add("Frame" to label(o.frame.name))
            o.color?.let { add("Color" to label(it.name)) }
            o.customColor?.let { add("CustomColor" to int(it)) }
            o.shadingColor?.let { add("ShadingColor" to int(it)) }
            if (o.splay != 0.0) add("Splay" to length(o.splay))
            if (o.splayRounded) add("SplayRounded" to bool(true))
            if (o.entrance) add("Entrance" to bool(true))
            if (o.pivot) add("Pivot" to bool(true))
            if (o.transom != 0.0) add("Transom" to length(o.transom))
            if (o.sidelight != 0.0) add("Sidelight" to length(o.sidelight))
            if (o.leafCount != 0) add("LeafCount" to int(o.leafCount.toLong()))
            if (o.liftSlide) add("LiftSlide" to bool(true))
            if (o.windowShape != d.windowShape) add("WindowShape" to label(o.windowShape.name))
            if (o.grille) add("Grille" to bool(true))
            if (o.topLight) add("TopLight" to bool(true))
            if (o.bay) add("Bay" to bool(true))
            if (o.garage != d.garage) add("Garage" to label(o.garage.name))
            if (o.insectScreen) add("InsectScreen" to bool(true))
            if (o.awning) add("Awning" to bool(true))
            o.awningColor?.let { add("AwningColor" to int(it)) }
            o.sillMaterial?.let { add("SillMaterial" to label(it.name)) }
            if (o.frosted) add("FrostedGlass" to bool(true))
        }
    }

    /** Modo di aprirsi della porta (anche dei balconi, che nel file sono porte) con i nomi dell'IFC4. */
    fun doorOperation(o: Opening): String {
        val side = if (o.hingeLeft) "LEFT" else "RIGHT"
        val two = o.leaves >= 2
        return when {
            // Porte da garage: la sezionale si arrotola in alto (ROLLINGUP), la basculante ha il suo nome (USERDEFINED).
            o.garageDoor == GarageDoor.Sectional -> "ROLLINGUP"
            o.isGarage -> "USERDEFINED"
            // Alzante: un'anta fissa e una che scorre.
            o.sliding -> if (two && !o.lifts) "DOUBLE_DOOR_SLIDING" else "SLIDING_TO_$side"
            o.folds -> if (two) "DOUBLE_DOOR_FOLDING" else "FOLDING_TO_$side"
            o.fixed -> "NOTDEFINED"
            two -> "DOUBLE_DOOR_SINGLE_SWING"
            else -> "SINGLE_SWING_$side"
        }
    }

    /** Modo di aprirsi di ogni anta della finestra, da sinistra, con i nomi dell'IFC4 (IfcWindowPanelOperationEnum). */
    fun panelOperations(o: Opening): List<String> {
        fun hand(left: Boolean) = if (left) "LEFTHAND" else "RIGHTHAND"
        val n = o.leaves
        // Le ante della metà sinistra hanno il cardine a sinistra, quelle della metà destra a destra.
        val leaves = if (n == 1) listOf(o.hingeLeft) else List(n) { it * 2 < n }
        return leaves.mapIndexed { i, left ->
            when {
                // Alzante: la prima anta resta ferma, le altre scorrono.
                o.lifts && n > 1 && i == 0 -> "FIXEDCASEMENT"
                o.sliding -> "SLIDINGHORIZONTAL"
                o.sash == SashType.Fixed -> "FIXEDCASEMENT"
                o.sash == SashType.Hopper -> "BOTTOMHUNG"
                o.sash == SashType.Pivot -> "PIVOTHORIZONTAL"
                o.sash == SashType.TiltTurn -> "TILTANDTURN${hand(left)}"
                else -> "SIDEHUNG${hand(left)}"
            }
        }
    }

    /** Suddivisione in ante dell'IFC4 (per 4 ante non c'è un nome: USERDEFINED, con il nome scritto a parte). */
    fun partitioning(leaves: Int) = when (leaves) { 1 -> "SINGLE_PANEL"; 2 -> "DOUBLE_PANEL_VERTICAL"; 3 -> "TRIPLE_PANEL_VERTICAL"; else -> "USERDEFINED" }
    fun userPartitioning(leaves: Int) = if (partitioning(leaves) == "USERDEFINED") "'QUADRUPLE_PANEL_VERTICAL'" else NULL

    /** Finestre raggruppate per tipo: (suddivisione, modo di aprirsi delle ante) → finestre. */
    val windowTypes = LinkedHashMap<Pair<String, List<String>>, MutableList<String>>()

    /** Un IfcWindowType per ogni modo di aprirsi, con le sue ante, dichiarato nel progetto. */
    fun writeWindowTypes(project: String) {
        if (windowTypes.isEmpty()) return
        val types = windowTypes.entries.map { (k, windows) ->
            val (partitioning, ops) = k
            val positions = when (ops.size) { 1 -> listOf("MIDDLE"); 2 -> listOf("LEFT", "RIGHT"); else -> listOf("LEFT") + List(ops.size - 2) { "MIDDLE" } + "RIGHT" }
            val panels = ops.mapIndexed { i, op ->
                w.add("IFCWINDOWPANELPROPERTIES", guid("windowtype/$partitioning/${ops.joinToString("+")}/panel/$i"), NULL, NULL, NULL, ".$op.", ".${positions[i]}.", NULL, NULL, NULL)
            }
            val name = str("Finestra " + ops.joinToString(" + ") { it.lowercase() })
            val type = w.add(
                "IFCWINDOWTYPE", guid("windowtype/$partitioning/${ops.joinToString("+")}"), NULL, name, NULL, NULL, list(panels), NULL, NULL, NULL,
                ".WINDOW.", ".$partitioning.", StepWriter.bool(false), userPartitioning(ops.size),
            )
            w.add("IFCRELDEFINESBYTYPE", newGuid(), NULL, NULL, NULL, list(windows), type)
            type
        }
        w.add("IFCRELDECLARES", newGuid(), NULL, NULL, NULL, project, list(types))
    }

    companion object {
        /** Fettine dritte per la cima dei varchi ad arco (come nel 3D). */
        const val ARCH_SLICES = 32
    }
}

private fun rad(deg: Double) = deg * kotlin.math.PI / 180.0
