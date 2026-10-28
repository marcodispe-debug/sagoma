package com.sagoma.planimetria.dxf

import com.sagoma.planimetria.geometry.Clip
import com.sagoma.planimetria.geometry.ExteriorChains
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Parapets
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.geometry.expandCurves
import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.geometry.innerThicknesses
import com.sagoma.planimetria.geometry.interiorOutline
import com.sagoma.planimetria.geometry.outerThicknesses
import com.sagoma.planimetria.geometry.pieces
import com.sagoma.planimetria.geometry.usableArea
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Layer
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pianta in DXF per i programmi CAD (AutoCAD e gli altri), in centimetri: solo la planimetria nuda, come deciso per le
 * esportazioni verso i professionisti (gli arredi del catalogo non escono mai). Un livello per ogni cosa, così nel CAD si
 * accendono e spengono:
 * - muri: il contorno, senza linee doppie dove due stanze hanno il muro in comune, e il riempimento a parte;
 * - porte e finestre: stipiti, vetri, ante con l'arco di apertura (tratteggiato), ante scorrevoli e a libro;
 * - stanze: il contorno interno (per misurare le aree nel CAD), nome e m² come sulla pianta;
 * - scale, pilastri, parapetti di balconi e terrazze;
 * - le misure come sulla pianta (catene fuori dalla casa, muri dentro le stanze, porte e finestre), le quote e i testi messi a
 *   mano. I livelli nascosti nell'app arrivano spenti (nel CAD si riaccendono).
 */
object PlanDxf {
    /** Il file da salvare, nella codifica scritta nella sua intestazione. */
    fun bytes(plan: FloorPlan, rise: Double = Floor.DEFAULT_LEVEL_HEIGHT, hidden: Set<Layer> = emptySet()): ByteArray =
        DxfWriter.bytes(of(plan, rise, hidden))

    /** `rise`: altezza del piano, per disegnare le scale come sulla pianta. `hidden`: livelli nascosti nell'app. */
    fun of(plan: FloorPlan, rise: Double = Floor.DEFAULT_LEVEL_HEIGHT, hidden: Set<Layer> = emptySet()): String {
        val names = T.pdf.livelliDxf
        val walls = DxfWriter.Layer(names[0], 7)
        val fill = DxfWriter.Layer(names[1], 8)
        val openings = DxfWriter.Layer(names[2], 4)
        val swings = DxfWriter.Layer(names[3], 4, dashed = true)
        val rooms = DxfWriter.Layer(names[4], 3)
        val texts = DxfWriter.Layer(names[5], 7)
        val stairs = DxfWriter.Layer(names[6], 6, off = Layer.Structure in hidden)
        val columns = DxfWriter.Layer(names[7], 7, off = Layer.Structure in hidden)
        val parapets = DxfWriter.Layer(names[8], 5)
        val lengths = DxfWriter.Layer(names[9], 2, off = Layer.WallLengths in hidden)
        val dimensions = DxfWriter.Layer(names[10], 150, off = Layer.Dimensions in hidden)
        val notes = DxfWriter.Layer(names[11], 150, off = Layer.Texts in hidden)
        val w = DxfWriter()
        for (l in listOf(walls, fill, openings, swings, rooms, texts, stairs, columns, parapets, lengths, dimensions, notes)) w.layer(l)

        // Muri curvi: tanti pezzi dritti lungo l'arco, come sulla pianta.
        val p = plan.expandCurves().plan
        val freeWalls = p.freeWalls.filter { it.length >= 0.5 }
        val freePieces = freeWalls.flatMap { fw -> fw.pieces().filter { it.length >= 0.5 } }
        val holes = p.rooms.flatMap { r -> r.openings.filter { it.wallIndex < r.wallCount && !r.isRemoved(it.wallIndex) }.flatMap { holes(r, it) } } +
            freePieces.flatMap { fw -> fw.host.let { h -> h.openings.flatMap { holes(h, it) } } } +
            // Porte sul muro in comune: il vano attraversa anche il muro della stanza accanto, con il suo spessore.
            Openings.sharedGaps(p).map { g -> band(g.a, g.b, p.room(g.roomId)!!.thicknessOf(g.wallIndex) / 2 + CUT) }
        val solid = (p.rooms.filter { !it.outdoor && it.wallCount >= 3 }.flatMap { wallPieces(it) } + freeWalls.flatMap { freeWallPieces(it) })
            .flatMap { convex(it) }
            .flatMap { Clip.subtractAll(it, holes) }
        for (pc in solid) {
            if (pc.size == 3 || pc.size == 4) w.solid(fill, pc)
            else for (k in 1 until pc.size - 1) w.solid(fill, listOf(pc[0], pc[k], pc[k + 1]))
        }
        for ((a, b) in WallOutline.boundary(solid)) w.line(walls, a, b)

        for (r in p.rooms) for (o in r.openings) if (o.wallIndex < r.wallCount && !r.isRemoved(o.wallIndex)) symbol(w, openings, swings, r, o)
        for (fw in freePieces) fw.host.let { h -> for (o in h.openings) symbol(w, openings, swings, h, o) }

        for (r in p.rooms) {
            if (r.outdoor) for ((a, b) in Parapets.segments(p, r)) {
                if (r.parapet == Parapet.Wall) w.polyline(parapets, band(a, b, PARAPET_WALL / 2), closed = true) else w.line(parapets, a, b)
            }
            w.polyline(rooms, r.interiorOutline(), closed = true)
        }
        // Nome e m² al centro della stanza (come sulla pianta, con i muri curvi veri).
        val labels = mutableListOf<DxfDimensions.Box>()
        for (r in plan.rooms) {
            val at = Polygon.labelPoint(r.points)
            val area = formatDecimal(r.usableArea() / 10_000.0, 2) + " m²"
            w.text(texts, at - Vec2(0.0, 17.0), 25.0, r.name)
            w.text(texts, at + Vec2(0.0, 17.0), 18.0, area)
            val half = max(DxfWriter.width(25.0, r.name.trim()), DxfWriter.width(18.0, area)) / 2 + 5
            labels += DxfDimensions.Box(at.x - half, at.y - 35.0, at.x + half, at.y + 31.0)
        }
        for (s in p.stairs) {
            val l = Stairs.layout(s, rise)
            for (st in l.steps) w.polyline(stairs, st.polygon, closed = true)
            for (pc in l.pieces) w.polyline(stairs, pc, closed = true)
            w.polyline(stairs, l.path, closed = false)
            l.path.firstOrNull()?.let { w.circle(stairs, it, 4.0) }
        }
        for (c in p.columns) {
            if (c.shape == ColumnShape.Round) w.circle(columns, c.center, c.width / 2) else w.polyline(columns, Structure.outline(c), closed = true)
        }
        val outside = ExteriorChains.of(plan)
        DxfDimensions.chains(w, lengths, outside)
        DxfDimensions.walls(w, lengths, plan, outside, labels)
        DxfDimensions.openings(w, lengths, p, outside)
        DxfDimensions.manual(w, dimensions, plan.dimensions)
        DxfDimensions.notes(w, notes, plan.annotations)
        return w.build()
    }

    /** Di quanto il vano supera le facce del muro: taglia il muro tutto, anche con le facce non proprio allineate. */
    private const val CUT = 0.5

    /** Parapetto in muratura: spessore disegnato. */
    private const val PARAPET_WALL = 12.0

    /**
     * Pezzi di muro della stanza, uno per muro (i muri eliminati no), tra il filo esterno e quello interno: negli angoli si
     * incontrano in diagonale; dove un muro cambia spessore restando allineato, con uno scalino.
     */
    private fun wallPieces(r: Room): List<List<Vec2>> {
        val n = r.wallCount
        val inner = corners(r.points, r.innerThicknesses.map { it / 2 })
        val outer = corners(r.points, r.outerThicknesses.map { -it / 2 })
        val quads = (0 until n).filter { !r.isRemoved(it) }.map { i ->
            listOf(outer[i].second, outer[(i + 1) % n].first, inner[(i + 1) % n].first, inner[i].second)
        }
        // Lato aperto: i muri accanto finiscono dove sarebbe la sua faccia, non a punta dentro il varco.
        val open = r.removedWalls.filter { it in 0 until n }.map { i -> band(r.wallStart(i), r.wallEnd(i), r.thicknessOf(i) / 2) }
        return if (open.isEmpty()) quads else quads.flatMap { q -> convex(q).flatMap { Clip.subtractAll(it, open) } }
    }

    /** Per ogni angolo, l'estremità del contorno spostato sul muro prima e su quello dopo (diverse solo dove c'è lo scalino). */
    private fun corners(pts: List<Vec2>, d: List<Double>): List<Pair<Vec2, Vec2>> {
        val n = pts.size
        val sign = if (Polygon.signedArea(pts) >= 0) 1.0 else -1.0
        val plain = Polygon.inset(pts, d)
        return List(n) { k ->
            if (!Polygon.steppedAt(pts, k, d)) plain[k] to plain[k]
            else {
                val cur = pts[k]
                val d1 = (cur - pts[(k - 1 + n) % n]).normalized()
                val d2 = (pts[(k + 1) % n] - cur).normalized()
                (cur + d1.perp() * (d[(k - 1 + n) % n] * sign)) to (cur + d2.perp() * (d[k] * sign))
            }
        }
    }

    /**
     * Muro singolo: un quadrilatero per ogni pezzo dritto (uno solo se il muro è dritto). Sul muro curvo i pezzi si toccano
     * lungo la bisettrice dell'angolo tra due pezzi, senza fessure né sovrapposizioni sul lato esterno della curva.
     */
    private fun freeWallPieces(fw: FreeWall): List<List<Vec2>> {
        val pieces = fw.pieces()
        val c = listOf(pieces.first().start) + pieces.map { it.end }
        val half = fw.thickness / 2
        val normals = c.indices.map { k ->
            val d = when (k) {
                0 -> c[1] - c[0]
                c.lastIndex -> c[k] - c[k - 1]
                else -> (c[k] - c[k - 1]).normalized() + (c[k + 1] - c[k]).normalized()
            }
            d.normalized().perp() * half
        }
        return (0 until c.size - 1).filter { c[it].distanceTo(c[it + 1]) > 1e-6 }
            .map { k -> listOf(c[k] - normals[k], c[k + 1] - normals[k + 1], c[k + 1] + normals[k + 1], c[k] + normals[k]) }
    }

    /** Striscia larga `2 · half` attorno al segmento da `a` a `b`. */
    private fun band(a: Vec2, b: Vec2, half: Double): List<Vec2> {
        val len = a.distanceTo(b)
        if (len < 1e-6) return emptyList()
        val n = (b - a).perp() / len * half
        return listOf(a - n, b - n, b + n, a + n)
    }

    /** Vano dell'apertura nel muro (con lo sguincio verso la stanza); la nicchia scava solo la metà verso la stanza. */
    private fun holes(r: Room, o: Opening): List<List<Vec2>> {
        val (a, b) = Openings.span(r, o)
        val len = a.distanceTo(b)
        if (len < 0.5) return emptyList()
        val u = (b - a) / len
        val n = Openings.inwardNormal(r, o.wallIndex)
        val half = r.thicknessOf(o.wallIndex) / 2 + CUT
        if (o.kind == OpeningKind.Niche) return listOf(listOf(a, b, b + n * half, a + n * half))
        val out = mutableListOf(listOf(a - n * half, b - n * half, b + n * half, a + n * half))
        // Lo sguincio arriva oltre la faccia (fino a `half`): allungato in proporzione, sulla faccia è largo proprio `splay`.
        val splay = Openings.splayOf(r, o) * half / (half - CUT)
        if (splay > 0) out += listOf(a, b, b + u * splay + n * half, a - u * splay + n * half)
        return out
    }

    /** Il poligono in pezzi convessi (a ventaglio se non lo è già: i pezzi di muro sono quadrilateri). */
    private fun convex(pts: List<Vec2>): List<List<Vec2>> {
        if (pts.size < 3) return emptyList()
        if (Polygon.isConvex(pts)) return listOf(pts)
        return (1 until pts.size - 1).map { listOf(pts[0], pts[it], pts[it + 1]) }.filter { Polygon.area(it) > 1e-6 }
    }

    /** Simbolo dell'apertura come sulla pianta: soglia, vetri, ante con l'arco, ante scorrevoli o a libro. */
    private fun symbol(w: DxfWriter, line: DxfWriter.Layer, dashed: DxfWriter.Layer, r: Room, o: Opening) {
        if (o.kind == OpeningKind.Niche || o.kind == OpeningKind.Passage) return
        val (a, b) = Openings.span(r, o)
        val width = a.distanceTo(b)
        if (width < 0.5) return
        val u = (b - a) / width
        val n = Openings.inwardNormal(r, o.wallIndex)
        val half = r.thicknessOf(o.wallIndex) / 2
        val frame = Openings.frameOffset(r, o)
        val f = n * frame
        val g = n * min(1.5, half)
        // Infissi: il vetro nel muro (linea doppia dove sta il telaio); a terra (balconi) anche la soglia.
        if (o.kind.glazed) {
            w.line(line, a + f - g, b + f - g)
            w.line(line, a + f + g, b + f + g)
            if (o.sillHeight == 0.0) w.line(line, a, b)
        }
        if (o.sliding && o.kind.hasLeaves) {
            val off = if (o.slideOnWall) n * (half + 3) else n * (half / 2)
            if (o.kind.leaves == 1) w.line(line, a + off, b + off)
            else {
                val k = maxOf(2, o.leaves)
                val overlap = width * 0.08 / (k - 1)
                for (i in 0 until k) {
                    val side = if (i % 2 == 0) off else if (o.slideOnWall) off + n * 3.0 else off * -1.0
                    val x0 = maxOf(0.0, width * i / k - if (i > 0) overlap else 0.0)
                    val x1 = minOf(width, width * (i + 1) / k + if (i < k - 1) overlap else 0.0)
                    w.line(line, a + u * x0 + side, a + u * x1 + side)
                }
            }
            return
        }
        if (o.fixed || o.hopper || o.pivotSash) return
        val swing = if (o.opensInward) n else n * -1.0
        val base = if (frame != 0.0) f else swing * half
        fun leaf(hinge: Vec2, tip: Vec2) {
            val radius = hinge.distanceTo(tip)
            if (radius < 0.5) return
            val open = hinge + swing * radius
            w.line(line, hinge, open)
            val mid = hinge + ((tip - hinge).normalized() + swing).normalized() * radius
            w.arc(dashed, hinge, tip, open, mid)
        }
        if (o.folds) {
            fun fold(hinge: Vec2, tip: Vec2) {
                val radius = hinge.distanceTo(tip)
                if (radius < 0.5) return
                val e = (tip - hinge) / radius
                val panel = radius / 2
                val knee = hinge + e * (panel * 0.5) + swing * (panel * sin(PI / 3))
                w.line(line, hinge, knee)
                w.line(line, knee, hinge + e * panel)
            }
            if (o.kind.leaves == 2) {
                val mid = (a + b) / 2.0
                fold(a + base, mid + base); fold(b + base, mid + base)
            } else if (o.hingeLeft) fold(a + base, b + base) else fold(b + base, a + base)
            return
        }
        val lr = o.leafRange
        val la = a + (b - a) * lr.start
        val lb = a + (b - a) * lr.endInclusive
        if (o.pivots) {
            // Porta a bilico: l'anta aperta sul perno, con i due archi.
            val leafW = la.distanceTo(lb)
            val dir = if (o.hingeLeft) u else u * -1.0
            val axis = (if (o.hingeLeft) la else lb) + dir * (leafW * Openings.PIVOT_AT) + base - swing * half
            val big = leafW * (1 - Openings.PIVOT_AT)
            val small = leafW * Openings.PIVOT_AT
            w.line(line, axis - swing * small, axis + swing * big)
            w.arc(dashed, axis, axis + dir * big, axis + swing * big, axis + (dir + swing).normalized() * big)
            w.arc(dashed, axis, axis - dir * small, axis - swing * small, axis - (dir + swing).normalized() * small)
            return
        }
        when {
            o.leaves > 2 -> for (k in 0 until o.leaves) {
                val p0 = a + (b - a) * (k.toDouble() / o.leaves)
                val p1 = a + (b - a) * ((k + 1).toDouble() / o.leaves)
                if (k * 2 < o.leaves) leaf(p0 + base, p1 + base) else leaf(p1 + base, p0 + base)
            }
            o.kind.leaves == 1 -> if (o.hingeLeft) leaf(la + base, lb + base) else leaf(lb + base, la + base)
            o.kind.leaves == 2 -> {
                val mid = a + (b - a) * o.leafSplit
                leaf(a + base, mid + base)
                leaf(b + base, mid + base)
            }
        }
    }
}
