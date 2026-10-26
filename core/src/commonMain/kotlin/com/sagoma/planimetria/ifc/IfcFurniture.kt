package com.sagoma.planimetria.ifc

import com.sagoma.planimetria.geometry.Triangulation
import com.sagoma.planimetria.model.SuppliedElement
import com.sagoma.planimetria.model.SuppliedObject
import com.sagoma.planimetria.model.SuppliedPart
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La cucina del negozio (o qualsiasi gruppo di mobili) dal file IFC di un professionista. Winner esporta proprio così: un IFC
 * con i soli mobili, senza muri. Da un file completo (Revit, Archicad…) si prendono soltanto i mobili: muri, solai, porte,
 * finestre, stanze e le altre parti dell'edificio restano fuori.
 *
 * Tutti i mobili del file diventano UN [SuppliedObject] (la composizione resta com'è nel progetto del negozio), con la forma
 * vera triangolo per triangolo e i colori del file. Si legge la geometria più comune dei programmi: facce triangolate e
 * poligonali (IFC4), Brep a facce piane, superfici a facce, estrusioni di profili, oggetti ripetuti (mapped item, anche
 * scalati), operazioni booleane (si tiene il primo solido). Il resto si conta in [Result.skipped].
 */
object IfcFurniture {
    class Result(
        /** Null se nel file non c'è nessun mobile con una forma leggibile. */
        val obj: SuppliedObject?,
        /** Dove stava il centro della base dell'oggetto nel file, in cm sagoma (y in giù): se il negozio ha disegnato sulla
         *  planimetria esportata da sagoma, la cucina va già al suo posto. */
        val center: Vec2?,
        val triangles: Int,
        val skipped: Map<String, Int>,
    )

    /** Parti dell'edificio, che non sono mobili. */
    private val BUILDING = IfcImport.WALLS + setOf(
        "IFCSLAB", "IFCSPACE", "IFCDOOR", "IFCWINDOW", "IFCOPENINGELEMENT", "IFCCOLUMN", "IFCBEAM", "IFCROOF", "IFCSTAIR",
        "IFCSTAIRFLIGHT", "IFCRAMP", "IFCRAMPFLIGHT", "IFCRAILING", "IFCCOVERING", "IFCPLATE", "IFCMEMBER", "IFCCURTAINWALL",
        "IFCFOOTING", "IFCPILE", "IFCBUILDINGSTOREY", "IFCBUILDING", "IFCSITE", "IFCPROJECT", "IFCANNOTATION", "IFCGRID",
        "IFCVIRTUALELEMENT", "IFCZONE", "IFCSPATIALZONE",
    )

    /** Colore dei pezzi senza colore nel file. */
    const val DEFAULT_COLOR = 0xFFD9D6D0L

    fun read(text: String, id: Long, name: String, supplier: String = ""): Result = FurnitureReader(IfcReader(StepFile.parse(text))).run(id, name, supplier)

    private class Tri(val a: V3, val b: V3, val c: V3, val argb: Long)

    private class FurnitureReader(val r: IfcReader) {
        val f = r.f
        val skipped = HashMap<String, Int>()
        val itemColor = readStyles()
        val materialColor = readMaterialColors()

        /** Colore per oggetto geometrico, da IfcStyledItem. */
        fun readStyles(): Map<Int, Long> {
            val out = HashMap<Int, Long>()
            for (s in f.ofType("IFCSTYLEDITEM")) {
                val item = s.ref(0) ?: continue
                styleColor(s.refs(1))?.let { out[item] = it }
            }
            return out
        }

        /** Colore dagli stili: IFC4 li mette diretti, IFC2x3 dentro IfcPresentationStyleAssignment. */
        fun styleColor(styles: List<Int>): Long? {
            for (id in styles) {
                val st = f[id] ?: continue
                when (st.type) {
                    "IFCPRESENTATIONSTYLEASSIGNMENT" -> styleColor(st.refs(0))?.let { return it }
                    "IFCSURFACESTYLE" -> for (el in st.refs(2).mapNotNull { f[it] }) {
                        if (el.type != "IFCSURFACESTYLERENDERING" && el.type != "IFCSURFACESTYLESHADING") continue
                        val c = f[el.ref(0)] ?: continue
                        if (c.type != "IFCCOLOURRGB") continue
                        return rgb(c.num(1) ?: 0.8, c.num(2) ?: 0.8, c.num(3) ?: 0.8)
                    }
                }
            }
            return null
        }

        /** Colore per oggetto dal suo materiale (IfcMaterialDefinitionRepresentation), quando la forma non ne ha uno. */
        fun readMaterialColors(): Map<Int, Long> {
            val ofMaterial = HashMap<Int, Long>()
            for (mdr in f.ofType("IFCMATERIALDEFINITIONREPRESENTATION")) {
                val mat = mdr.ref(3) ?: continue
                val color = mdr.refs(2).mapNotNull { f[it] }.flatMap { it.refs(3) }.firstNotNullOfOrNull { item ->
                    f[item]?.takeIf { it.type == "IFCSTYLEDITEM" }?.let { styleColor(it.refs(1)) }
                } ?: continue
                ofMaterial[mat] = color
            }
            if (ofMaterial.isEmpty()) return emptyMap()
            fun materialOf(id: Int?): Long? {
                val m = f[id] ?: return null
                return when (m.type) {
                    "IFCMATERIAL" -> ofMaterial[m.id]
                    "IFCMATERIALLIST" -> m.refs(0).firstNotNullOfOrNull { ofMaterial[it] }
                    "IFCMATERIALLAYERSETUSAGE" -> materialOf(m.ref(0))
                    "IFCMATERIALLAYERSET" -> m.refs(0).firstNotNullOfOrNull { materialOf(f[it]?.ref(0)) }
                    "IFCMATERIALCONSTITUENTSET" -> m.refs(2).firstNotNullOfOrNull { materialOf(f[it]?.ref(2)) }
                    else -> null
                }
            }
            val out = HashMap<Int, Long>()
            for (rel in f.ofType("IFCRELASSOCIATESMATERIAL")) {
                val c = materialOf(rel.ref(5)) ?: continue
                rel.refs(4).forEach { out[it] = c }
            }
            return out
        }

        fun rgb(red: Double, green: Double, blue: Double): Long {
            fun b(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt().toLong()
            return (0xFFL shl 24) or (b(red) shl 16) or (b(green) shl 8) or b(blue)
        }

        fun run(id: Long, name: String, supplier: String): Result {
            val products = f.entities.values.filter { e ->
                e.type !in BUILDING && e.args.size > 6 && f[e.ref(5)]?.type == "IFCLOCALPLACEMENT" && f[e.ref(6)]?.type == "IFCPRODUCTDEFINITIONSHAPE"
            }.sortedBy { it.id }
            val elements = mutableListOf<Pair<String, List<Tri>>>()
            for (p in products) {
                val color = materialColor[p.id] ?: DEFAULT_COLOR
                val tris = body(p, color)
                if (tris.isEmpty()) { skipped.countOne(p.type); continue }
                elements += (p.str(2)?.takeIf { it.isNotBlank() } ?: p.type.removePrefix("IFC").lowercase()) to tris
            }
            val all = elements.flatMap { it.second }
            if (all.isEmpty()) return Result(null, null, 0, skipped)

            // In cm sagoma: x come nel file, y della pianta = −y del file, altezza = z.
            val s = r.scale
            fun planX(p: V3) = p.x * s
            fun planY(p: V3) = -p.y * s
            fun up(p: V3) = p.z * s
            val pts = all.flatMap { listOf(it.a, it.b, it.c) }
            val minX = pts.minOf(::planX); val maxX = pts.maxOf(::planX)
            val minY = pts.minOf(::planY); val maxY = pts.maxOf(::planY)
            val minZ = pts.minOf(::up); val maxZ = pts.maxOf(::up)
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            // Locale in mm: x lungo la larghezza, y in alto dalla base, z verso il davanti (= giù in pianta a rotazione 0).
            fun mm(v: Double) = (v * 10).roundToInt()
            fun local(p: V3) = intArrayOf(mm(planX(p) - cx), mm(up(p) - minZ), mm(planY(p) - cy))

            val parts = all.groupBy { it.argb }.map { (argb, tris) ->
                val index = HashMap<Triple<Int, Int, Int>, Int>()
                val vertices = ArrayList<Int>()
                val triangles = ArrayList<Int>()
                for (t in tris) {
                    val ids = listOf(t.a, t.b, t.c).map { p ->
                        val l = local(p)
                        index.getOrPut(Triple(l[0], l[1], l[2])) { vertices += l[0]; vertices += l[1]; vertices += l[2]; index.size }
                    }
                    if (ids.toSet().size == 3) triangles += ids
                }
                SuppliedPart(argb, vertices, triangles)
            }.filter { it.triangles.isNotEmpty() }
            val outlines = elements.map { (n, tris) ->
                val l = tris.flatMap { listOf(it.a, it.b, it.c) }.map(::local)
                SuppliedElement(n, l.minOf { it[0] }, l.minOf { it[2] }, l.maxOf { it[0] }, l.maxOf { it[2] }, l.minOf { it[1] }, l.maxOf { it[1] })
            }
            val obj = SuppliedObject(
                id = id, name = name, supplier = supplier,
                width = maxX - minX, depth = maxY - minY, height = maxZ - minZ,
                parts = parts, elements = outlines,
            )
            return Result(obj, Vec2(cx, cy), parts.sumOf { it.triangles.size / 3 }, skipped)
        }

        /** Triangoli del corpo ("Body") del mobile, nel mondo (unità del file). */
        fun body(product: StepEntity, color: Long): List<Tri> {
            val frame = r.placement(product.ref(5))
            val shape = f[product.ref(6)] ?: return emptyList()
            val reps = shape.refs(2).mapNotNull { f[it] }
            val bodies = reps.filter { it.str(1).equals("Body", ignoreCase = true) }
                .ifEmpty { reps.filter { !it.str(1).equals("Axis", true) && !it.str(1).equals("FootPrint", true) && !it.str(1).equals("Box", true) } }
            val out = ArrayList<Tri>()
            for (rep in bodies) for (item in rep.refs(3)) item(f[item], Xf(frame::apply), color, out)
            return out
        }

        /** Da coordinate dell'oggetto geometrico al mondo (posizioni annidate, oggetti ripetuti anche scalati). */
        fun interface Xf { fun apply(p: V3): V3 }

        fun item(e: StepEntity?, xf: Xf, inherited: Long, out: MutableList<Tri>) {
            e ?: return
            val color = itemColor[e.id] ?: inherited
            fun tri(a: V3, b: V3, c: V3) { out += Tri(xf.apply(a), xf.apply(b), xf.apply(c), color) }
            when (e.type) {
                "IFCTRIANGULATEDFACESET" -> {
                    val coords = pointList(f[e.ref(0)])
                    val pn = e.list(4).mapNotNull { (it as? StepValue.Num)?.value?.toInt() }
                    fun at(i: Int): V3? = (if (pn.isNotEmpty()) pn.getOrNull(i - 1) else i)?.let { coords.getOrNull(it - 1) }
                    for (t in e.list(3)) {
                        val ix = (t as? StepValue.Lst)?.items?.mapNotNull { (it as? StepValue.Num)?.value?.toInt() } ?: continue
                        if (ix.size < 3) continue
                        tri(at(ix[0]) ?: continue, at(ix[1]) ?: continue, at(ix[2]) ?: continue)
                    }
                }
                "IFCPOLYGONALFACESET" -> {
                    val coords = pointList(f[e.ref(0)])
                    val pn = e.list(3).mapNotNull { (it as? StepValue.Num)?.value?.toInt() }
                    fun at(i: Int): V3? = (if (pn.isNotEmpty()) pn.getOrNull(i - 1) else i)?.let { coords.getOrNull(it - 1) }
                    for (face in e.refs(2).mapNotNull { f[it] }) {
                        val loop = face.list(0).mapNotNull { (it as? StepValue.Num)?.value?.toInt()?.let(::at) }
                        polygon(loop, ::tri)
                    }
                }
                "IFCFACETEDBREP", "IFCFACETEDBREPWITHVOIDS" -> faces(f[e.ref(0)]?.refs(0).orEmpty(), ::tri)
                "IFCSHELLBASEDSURFACEMODEL", "IFCFACEBASEDSURFACEMODEL" -> for (shell in e.refs(0)) faces(f[shell]?.refs(0).orEmpty(), ::tri)
                "IFCCLOSEDSHELL", "IFCOPENSHELL", "IFCCONNECTEDFACESET" -> faces(e.refs(0), ::tri)
                "IFCEXTRUDEDAREASOLID", "IFCEXTRUDEDAREASOLIDTAPERED" -> {
                    val pos = r.axisFrame(e.ref(1))
                    var profile = r.profilePoints(f[e.ref(0)])
                    if (profile.size > 1 && profile.first().distanceTo(profile.last()) < 1e-9) profile = profile.dropLast(1)
                    if (profile.size < 3) return
                    val dir = r.vec(e.ref(2), V3(0.0, 0.0, 1.0)).normalized() * (e.num(3) ?: 0.0)
                    val base = profile.map { pos.apply(V3(it.x, it.y, 0.0)) }
                    val top = profile.map { pos.apply(V3(it.x, it.y, 0.0) + dir) }
                    for ((i, j, k) in Triangulation.triangulate(profile)) {
                        tri(base[i], base[k], base[j])
                        tri(top[i], top[j], top[k])
                    }
                    for (i in profile.indices) {
                        val j = (i + 1) % profile.size
                        tri(base[i], base[j], top[j])
                        tri(base[i], top[j], top[i])
                    }
                }
                "IFCBOOLEANCLIPPINGRESULT", "IFCBOOLEANRESULT" -> item(f[e.ref(1)], xf, color, out)
                "IFCMAPPEDITEM" -> {
                    val map = f[e.ref(0)] ?: return
                    val origin = r.axisFrame(map.ref(0))
                    val target = transform(f[e.ref(1)])
                    val inner = Xf { p -> xf.apply(target.apply(origin.apply(p))) }
                    val rep = f[map.ref(1)] ?: return
                    for (it in rep.refs(3)) item(f[it], inner, color, out)
                }
                else -> skipped.countOne("geometria ${e.type}")
            }
        }

        /** IfcCartesianTransformationOperator3D (anche non uniforme: si usa la prima scala). */
        fun transform(t: StepEntity?): Xf {
            t ?: return Xf { it }
            val x = r.vec(t.ref(0), V3(1.0, 0.0, 0.0)).normalized()
            val yHint = r.vec(t.ref(1), V3(0.0, 1.0, 0.0)).normalized()
            val o = r.vec(t.ref(2), V3(0.0, 0.0, 0.0))
            val k = t.num(3) ?: 1.0
            val z = t.ref(4)?.let { r.vec(it, V3(0.0, 0.0, 1.0)).normalized() }
                ?: (x cross yHint).normalized().let { if (it.length < 0.5) V3(0.0, 0.0, 1.0) else it }
            val y = (z cross x).normalized()
            val frame = Frame(o, x, y, z)
            return Xf { p -> frame.apply(p * k) }
        }

        fun pointList(e: StepEntity?): List<V3> = e?.list(0)?.mapNotNull { (it as? StepValue.Lst)?.items?.mapNotNull { n -> (n as? StepValue.Num)?.value } }
            ?.map { V3(it.getOrElse(0) { 0.0 }, it.getOrElse(1) { 0.0 }, it.getOrElse(2) { 0.0 }) }.orEmpty()

        /** Facce (IfcFace con il contorno esterno IfcPolyLoop). */
        fun faces(ids: List<Int>, tri: (V3, V3, V3) -> Unit) {
            for (face in ids.mapNotNull { f[it] }) {
                val bounds = face.refs(0).mapNotNull { f[it] }
                val outer = bounds.firstOrNull { it.type == "IFCFACEOUTERBOUND" } ?: bounds.firstOrNull() ?: continue
                val loop = f[outer.ref(0)] ?: continue
                if (loop.type != "IFCPOLYLOOP") { skipped.countOne("contorno ${loop.type}"); continue }
                var pts = loop.refs(0).map { r.vec(it, V3(0.0, 0.0, 0.0)) }
                if (outer.enum(1) == "F") pts = pts.reversed()
                polygon(pts, tri)
            }
        }

        /** Poligono piano nello spazio in triangoli (anche concavo): si proietta sul piano dove è più largo. */
        fun polygon(pts0: List<V3>, tri: (V3, V3, V3) -> Unit) {
            val pts = pts0.filterIndexed { i, p -> (p - pts0[(i + 1) % pts0.size]).length > 1e-9 }
            if (pts.size < 3) return
            if (pts.size == 3) { tri(pts[0], pts[1], pts[2]); return }
            // Normale di Newell.
            var n = V3(0.0, 0.0, 0.0)
            for (i in pts.indices) {
                val a = pts[i]; val b = pts[(i + 1) % pts.size]
                n += V3((a.y - b.y) * (a.z + b.z), (a.z - b.z) * (a.x + b.x), (a.x - b.x) * (a.y + b.y))
            }
            val flat = when {
                abs(n.z) >= abs(n.x) && abs(n.z) >= abs(n.y) -> pts.map { Vec2(it.x, it.y) }
                abs(n.y) >= abs(n.x) -> pts.map { Vec2(it.z, it.x) }
                else -> pts.map { Vec2(it.y, it.z) }
            }
            val tris = Triangulation.triangulate(flat)
            if (tris.isEmpty()) { for (k in 1 until pts.size - 1) tri(pts[0], pts[k], pts[k + 1]); return }
            for ((i, j, k) in tris) {
                // Stesso verso del poligono di partenza.
                val face = (pts[j] - pts[i]) cross (pts[k] - pts[i])
                if ((face dot n) >= 0) tri(pts[i], pts[j], pts[k]) else tri(pts[i], pts[k], pts[j])
            }
        }
    }
}
