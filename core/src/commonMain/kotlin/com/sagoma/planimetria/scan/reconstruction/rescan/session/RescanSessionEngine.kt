package com.sagoma.planimetria.scan.reconstruction.rescan.session

import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.quality.QualityLevel
import com.sagoma.planimetria.scan.reconstruction.quality.QualityResult
import com.sagoma.planimetria.scan.reconstruction.quality.WallQuality
import com.sagoma.planimetria.scan.reconstruction.rescan.PerimeterGeometry
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanConfig
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanPlan
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanRequestType
import com.sagoma.planimetria.scan.recording.DepthKeyframe
import com.sagoma.planimetria.scan.recording.PoseSample
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Una scansione elaborata fino a M5 (tutto in memoria, dati strutturati: nessuna lettura di testo). */
class ScanInput(
    val dataset: String,
    val createdAtMillis: Long,
    val poses: List<PoseSample>,
    val depth: List<DepthKeyframe>,
    val walls: List<EstimatedWall>,
    val quality: QualityResult,
    val plan: RescanPlan,
    val perimeter: PerimeterGeometry,
)

/**
 * M5.1 — aggiornamento deterministico della sessione: (sessione precedente, nuova scansione) → nuova sessione. Funzione pura.
 * Non modifica M5: legge il suo piano e i dati strutturati di M4/R3/R4 della stessa scansione.
 */
object RescanSessionEngine {
    /** Tolleranze approvate per ritrovare una parete (dalle soglie di prodotto già esistenti di M4 e M5). */
    const val MATCH_ANGLE_DEG = 1.5
    const val MATCH_POSITION_BASE_M = 0.03
    const val MATCH_EXTENT_GAP_M = 0.20

    // ------------------------------------------------------------------------------------------- impronta e prefisso

    private fun fnv(h0: ULong, v: Long): ULong {
        var h = h0
        for (i in 0 until 8) { h = h xor ((v ushr (8 * i)) and 0xFF).toULong(); h *= 1099511628211UL }
        return h
    }
    private fun poseHash(p: List<PoseSample>): String = p.fold(14695981039346656037UL) { h, s ->
        val c = s.camera
        listOf(s.seq.toLong(), s.timestampNs, (c?.x ?: 0.0).toRawBits(), (c?.y ?: 0.0).toRawBits(), (c?.z ?: 0.0).toRawBits(), (c?.qx ?: 0.0).toRawBits(),
            (c?.qy ?: 0.0).toRawBits(), (c?.qz ?: 0.0).toRawBits(), (c?.qw ?: 0.0).toRawBits(), if (c == null) 0L else 1L).fold(h) { a, v -> fnv(a, v) }
    }.toString(16)
    private fun depthHash(d: List<DepthKeyframe>): String = d.fold(14695981039346656037UL) { h, k -> fnv(fnv(h, k.seq.toLong()), k.timestampNs) }.toString(16)

    fun fingerprint(s: ScanInput) = RecordingFingerprint(s.createdAtMillis, s.poses.size, s.depth.size, poseHash(s.poses), depthHash(s.depth))

    /** La registrazione corrente contiene la precedente come prefisso (stessa sessione, stesso sistema di coordinate). */
    fun isContinuation(prev: RecordingFingerprint, cur: ScanInput): Boolean =
        prev.createdAtMillis == cur.createdAtMillis && cur.poses.size >= prev.poseCount && cur.depth.size >= prev.depthCount &&
            poseHash(cur.poses.take(prev.poseCount)) == prev.poseHash && depthHash(cur.depth.take(prev.depthCount)) == prev.depthHash

    // ------------------------------------------------------------------------------------------------- bersagli correnti

    private class Cand(val kind: TargetKind, val r3: Int?, val end: String?, val obs: TargetObservation)

    private fun endPoint(w: EstimatedWall, end: String) = w.geometry.pointAt(if (end == "START") w.geometry.startU else w.geometry.endU)

    /**
     * Bersagli aperti della scansione, con le stesse condizioni con cui M5 genera le richieste (M5 non è modificato): estremità,
     * tratti, perimetro, viste, misura, ambiguità. Le cause che M5 fonde in un'estremità (gap adiacenti) restano nell'estremità.
     */
    private fun candidates(s: ScanInput, cfg: RescanConfig): List<Cand> {
        val out = mutableListOf<Cand>()
        val q = s.quality.walls.associateBy { it.wallId }
        val reqs = s.plan.requests
        val missing = s.perimeter.missing
        fun ext(w: Int, end: String) = s.perimeter.stretches.firstOrNull { it.wallId == w && it.kind == (if (end == "START") "prolungamento dall'angolo" else "prolungamento fino all'angolo") }
        fun covering(pred: (com.sagoma.planimetria.scan.reconstruction.rescan.RescanRequest) -> Boolean) = reqs.filter(pred).map { it.id }.sorted()

        // Perimetro.
        val pState = s.quality.room.perimeterState
        if (pState != PerimeterState.CLOSED) out.add(Cand(TargetKind.PERIMETER, null, null, TargetObservation(
            true, "BLOCKING", covering { it.type == RescanRequestType.RESCAN_OPEN_PERIMETER || it.type == RescanRequestType.RESCAN_UNCERTAIN_PERIMETER },
            perimeterState = pState?.name ?: "NONE", missingSideM = missing?.let { hypot(it.toX - it.fromX, it.toZ - it.fromZ) })))

        for (w in s.walls.sortedBy { it.id }) {
            val wq = q[w.id] ?: continue
            val wallReqs = { r: com.sagoma.planimetria.scan.reconstruction.rescan.RescanRequest -> !r.inRoom && r.wallId == w.id }
            val openEnds = mutableSetOf<String>()
            for (end in listOf("START", "END")) {
                val st = if (end == "START") wq.completeness.startState else wq.completeness.endState
                val rs = if (end == "START") wq.completeness.startReason else wq.completeness.endReason
                val e = ext(w.id, end)
                if (st == EndState.OBSERVED && (e == null || e.lengthM < cfg.minUnobservedM)) continue
                openEnds.add(end)
                val p = endPoint(w, end)
                val ids = covering { r ->
                    (r.wallId == w.id && r.target.ax != null && ((end == "START" && r.type == RescanRequestType.RESCAN_WALL_START) || (end == "END" && r.type == RescanRequestType.RESCAN_WALL_END))) ||
                        (r.id.startsWith("CORNER-") && cornerCovers(r, s, w.id, end)) ||
                        ((r.type == RescanRequestType.RESCAN_OPEN_PERIMETER || r.type == RescanRequestType.RESCAN_UNCERTAIN_PERIMETER) && missing != null &&
                            ((end == "END" && missing.afterWall == w.id) || (end == "START" && missing.beforeWall == w.id))) || wallReqs(r)
                }.filter { id -> !id.startsWith("CORNER-") || reqs.first { it.id == id }.let { cornerCovers(it, s, w.id, end) } }
                out.add(Cand(TargetKind.WALL_ENDPOINT, w.id, end, TargetObservation(
                    true, if (wq.inRoom) "BLOCKING" else "SECONDARY", ids, w.id, st.name, rs.name, p[0], p[1], unobservedM = e?.lengthM)))
            }
            // Tratti interni (R3) e gap tra frammenti (R4) ≥ soglia, salvo quelli che M5 fonde in un'estremità aperta.
            val g = w.geometry
            for (gap in w.gaps) {
                if (gap.lengthM < cfg.minUnobservedM) continue
                val nearStart = gap.fromU - g.startU <= cfg.endAdjacencyM && "START" in openEnds
                val nearEnd = g.endU - gap.toU <= cfg.endAdjacencyM && "END" in openEnds
                if (nearStart || nearEnd) continue
                val a = g.pointAt(gap.fromU); val b = g.pointAt(gap.toU)
                out.add(segment(w.id, wq.inRoom, a[0], a[1], b[0], b[1], gap.lengthM, covering { r -> (r.wallId == w.id && r.type == RescanRequestType.RESCAN_WALL_BODY && r.target.ax == a[0] && r.target.az == a[1]) || wallReqs(r) }))
            }
            for (st in s.perimeter.stretches.filter { it.wallId == w.id && it.kind.startsWith("gap tra frammenti") && it.lengthM >= cfg.minUnobservedM }) {
                if ("END" in openEnds) continue
                out.add(segment(w.id, wq.inRoom, st.ax, st.az, st.bx, st.bz, st.lengthM, covering { r -> (r.wallId == w.id && r.type == RescanRequestType.RESCAN_UNOBSERVED_REGION && r.target.ax == st.ax && r.target.az == st.az) || wallReqs(r) }))
            }
            // Viste, misura, ambiguità instabile.
            val sev = if (wq.inRoom) "IMPORTANT" else "SECONDARY"
            if (wq.evidence.viewCount < cfg.minViews) out.add(Cand(TargetKind.WALL_VIEWS, w.id, null, TargetObservation(true, sev,
                covering { r -> (r.wallId == w.id && r.type == RescanRequestType.RESCAN_LOW_VIEW_COUNT) || wallReqs(r) }, w.id, viewCount = wq.evidence.viewCount)))
            if (wq.measurement.level == QualityLevel.LOW) out.add(Cand(TargetKind.WALL_MEASUREMENT, w.id, null, TargetObservation(true, sev,
                covering { r -> (r.wallId == w.id && (r.type == RescanRequestType.RESCAN_WALL_MEASUREMENT || r.type == RescanRequestType.RESCAN_LOW_VIEW_COUNT)) || wallReqs(r) }, w.id,
                sigmaPositionM = wq.measurement.positionSigmaM, sigmaDirectionDeg = wq.measurement.headingSigmaDeg, measurementLevel = wq.measurement.level.name)))
            val a = wq.ambiguity
            if (a.maxScore != null && a.maxScore >= cfg.ambiguityTau && (a.sourceSurfaceStability ?: 0.0) < cfg.ambiguityTau) out.add(Cand(TargetKind.WALL_AMBIGUITY, w.id, null, TargetObservation(true, "SECONDARY",
                covering { r -> (r.wallId == w.id && r.type == RescanRequestType.RESCAN_HIGH_AMBIGUITY) || wallReqs(r) }, w.id, ambiguityScore = a.maxScore, ambiguityStability = a.sourceSurfaceStability)))
        }
        return out
    }

    private fun segment(w: Int, inRoom: Boolean, ax: Double, az: Double, bx: Double, bz: Double, len: Double, ids: List<String>) =
        Cand(TargetKind.WALL_SEGMENT, w, null, TargetObservation(true, if (inRoom) "BLOCKING" else "SECONDARY", ids, w, segAx = ax, segAz = az, segBx = bx, segBz = bz, unobservedM = len))

    /** Una richiesta d'angolo M5 copre la fine di Wa e l'inizio di Wb: verificato sulle coordinate R4 (copiate, quindi identiche). */
    private fun cornerCovers(r: com.sagoma.planimetria.scan.reconstruction.rescan.RescanRequest, s: ScanInput, w: Int, end: String): Boolean {
        val ends = s.perimeter.stretches.filter { it.kind == "prolungamento fino all'angolo" }
        val starts = s.perimeter.stretches.filter { it.kind == "prolungamento dall'angolo" }
        for (sa in ends) {
            val sb = starts.filter { it.ax == sa.bx && it.az == sa.bz }.minByOrNull { it.wallId } ?: continue
            if (r.target.ax == sa.ax && r.target.az == sa.az && r.target.bx == sb.bx && r.target.bz == sb.bz)
                return (end == "END" && sa.wallId == w) || (end == "START" && sb.wallId == w)
        }
        return false
    }

    // --------------------------------------------------------------------------------------------- confronto pareti

    private class Line(val nx: Double, val nz: Double, val cx: Double, val cz: Double, val ux: Double, val uz: Double, val s: Double, val e: Double, val sigma: Double) {
        fun pt(u: Double) = doubleArrayOf(cx + ux * u, cz + uz * u)
    }
    private fun line(w: SessionWall) = Line(w.nx, w.nz, w.cx, w.cz, w.ux, w.uz, w.startU, w.endU, w.positionSigmaM)
    private fun line(w: EstimatedWall) = w.geometry.let { Line(it.nx, it.nz, it.cx, it.cz, it.ux, it.uz, it.startU, it.endU, w.uncertainty.positionSigmaM) }

    private class Cmp(val cur: Int, val pos: Double, val tol: Double, val angle: Double, val overlap: Double, val failed: List<String>) { val ok get() = failed.isEmpty() }

    private fun compare(p: Line, c: Line, curId: Int): Cmp {
        val dot = p.nx * c.nx + p.nz * c.nz
        val angle = acos(min(1.0, abs(dot))) * 180.0 / PI
        val mid = p.pt((p.s + p.e) / 2)
        val pos = abs((mid[0] - c.cx) * c.nx + (mid[1] - c.cz) * c.nz)
        val tol = MATCH_POSITION_BASE_M + 3 * sqrt(p.sigma * p.sigma + c.sigma * c.sigma)
        val a = p.pt(p.s); val b = p.pt(p.e)
        val ua = (a[0] - c.cx) * c.ux + (a[1] - c.cz) * c.uz; val ub = (b[0] - c.cx) * c.ux + (b[1] - c.cz) * c.uz
        val overlap = min(max(ua, ub), c.e) - max(min(ua, ub), c.s)
        val failed = mutableListOf<String>()
        if (dot <= 0) failed.add("FACING")
        if (angle > MATCH_ANGLE_DEG) failed.add("ANGLE")
        if (pos > tol) failed.add("POSITION")
        if (overlap < -MATCH_EXTENT_GAP_M) failed.add("EXTENT")
        return Cmp(curId, pos, tol, angle, overlap, failed)
    }

    private fun wallNumber(k: String) = k.removePrefix("SW").toIntOrNull() ?: Int.MAX_VALUE


    // ------------------------------------------------------------------------------------ ordini deterministici (D5, D6)

    /** Chiave geometrica: SOLO ordine (conio delle chiavi nuove, spareggi, output). Mai identità fisica. Non usa l'id R3. */
    private class GeoKey(val theta: Double, val d: Double, val mx: Double, val mz: Double, val len: Double) : Comparable<GeoKey> {
        override fun compareTo(other: GeoKey) = compareValuesBy(this, other, { it.theta }, { it.d }, { it.mx }, { it.mz }, { it.len })
    }
    private fun nz0(v: Double) = v + 0.0   // −0.0 → 0.0
    private fun geoKey(w: EstimatedWall): GeoKey {
        val g = w.geometry
        var th = atan2(g.nz, g.nx); if (th < 0) th += 2 * PI
        val m = g.pointAt((g.startU + g.endU) / 2)
        return GeoKey(nz0(th), nz0(g.nx * g.cx + g.nz * g.cz), nz0(m[0]), nz0(m[1]), nz0(g.endU - g.startU))
    }

    /** Preferenza tra candidati: SOLO per scegliere quale seguire; mai prova di identità o di risoluzione. Nessuna soglia. */
    private class Rank(val ratio: Double, val angle: Double, val negOverlap: Double, val negObserved: Double, val geo: GeoKey) : Comparable<Rank> {
        override fun compareTo(other: Rank) = compareValuesBy(this, other, { it.ratio }, { it.angle }, { it.negOverlap }, { it.negObserved }, { it.geo })
    }

    private val kindOrder = mapOf(TargetKind.PERIMETER to 0, TargetKind.WALL_ENDPOINT to 1, TargetKind.WALL_SEGMENT to 2,
        TargetKind.WALL_VIEWS to 3, TargetKind.WALL_MEASUREMENT to 4, TargetKind.WALL_AMBIGUITY to 5)
    private val suffixRe = Regex("-(\\d+)$")
    private fun suffix(k: String) = suffixRe.find(k)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // ------------------------------------------------------------------------------ pareti persistenti e frammenti

    private fun refsOf(w: SessionWall): List<FragmentGeometry> = w.fragments.ifEmpty {
        listOf(FragmentGeometry(FragmentRef(w.lastScan, w.r3WallId), w.nx, w.nz, w.cx, w.cz, w.ux, w.uz, w.startU, w.endU, w.positionSigmaM, w.endU - w.startU, FragmentRole.PRIMARY))
    }
    private fun line(f: FragmentGeometry) = Line(f.nx, f.nz, f.cx, f.cz, f.ux, f.uz, f.startU, f.endU, f.positionSigmaM)
    private fun fragGeom(w: EstimatedWall, scan: Int, role: FragmentRole) = w.geometry.let { g ->
        FragmentGeometry(FragmentRef(scan, w.id), g.nx, g.nz, g.cx, g.cz, g.ux, g.uz, g.startU, g.endU, w.uncertainty.positionSigmaM, g.observedLengthM, role)
    }
    private fun interval(w: EstimatedWall, on: EstimatedWall): Pair<Double, Double> {
        val a = w.geometry.pointAt(w.geometry.startU); val b = w.geometry.pointAt(w.geometry.endU)
        val ua = proj(on, a[0], a[1]); val ub = proj(on, b[0], b[1]); return min(ua, ub) to max(ua, ub)
    }
    /** Disgiunti (o contigui) lungo la linea: stessa regola "≤" della classificazione SPLIT di M5.1. */
    private fun disjoint(x: EstimatedWall, y: EstimatedWall, on: EstimatedWall): Boolean {
        val i = interval(x, on); val j = interval(y, on); return min(i.second, j.second) - max(i.first, j.first) <= 0
    }
    /** Due frammenti della STESSA scansione che si accettano a vicenda con la regola approvata e si sovrappongono: co-osservati distinti. */
    private fun coObserved(x: EstimatedWall, y: EstimatedWall): Boolean {
        val c1 = compare(line(x), line(y), y.id); val c2 = compare(line(y), line(x), x.id)
        return c1.ok && c2.ok && c1.overlap > 0 && c2.overlap > 0
    }
    private fun pairOf(a: String, b: String) = if (wallNumber(a) <= wallNumber(b)) a to b else b to a

    /** Corrispondenza pareti persistenti → frammenti della scansione corrente (§3.2, §3.3). */
    private class Assignment(
        val frags: List<EstimatedWall>,
        val geo: Map<Int, GeoKey>,
        val cmp: Map<Pair<String, Int>, Cmp>,
        val rank: Map<Pair<String, Int>, Rank>,
        val accepted: Map<String, List<Int>>,
        val nearest: Map<String, Cmp>,
        val owner: Map<Int, String>,
        val owned: Map<String, List<Int>>,
        val mergedInto: Map<String, String>,
        val newAliases: List<WallAlias>,
        val newWalls: Set<String>,
        val relations: List<WallRelation>,
        val nextNumber: Int,
    )

    private fun assign(index: Int, scan: ScanInput, pws: List<SessionWall>, distinct: Set<Pair<String, String>>, startNumber: Int): Assignment {
        val geo = scan.walls.associate { it.id to geoKey(it) }
        val frags = scan.walls.sortedWith(compareBy { geo.getValue(it.id) })
        val byId = frags.associateBy { it.id }
        val cmp = HashMap<Pair<String, Int>, Cmp>(); val rank = HashMap<Pair<String, Int>, Rank>(); val maxOv = HashMap<Pair<String, Int>, Double>()
        val nearest = HashMap<String, Cmp>()
        fun rankOf(c: Cmp, f: EstimatedWall) = Rank(c.pos / c.tol, c.angle, -c.overlap, -f.geometry.observedLengthM, geo.getValue(f.id))
        for (w in pws) {
            val refs = refsOf(w)
            var near: Pair<Cmp, EstimatedWall>? = null
            for (f in frags) {
                val cs = refs.map { compare(line(it), line(f), f.id) }
                val ok = cs.filter { it.ok }
                if (ok.isNotEmpty()) {
                    val b = ok.minWithOrNull(compareBy { rankOf(it, f) })!!
                    cmp[w.key to f.id] = b; rank[w.key to f.id] = rankOf(b, f); maxOv[w.key to f.id] = ok.maxOf { it.overlap }
                } else {
                    val n = cs.minWithOrNull(compareBy<Cmp>({ it.failed.size }, { it.pos }))!!
                    val cur = near
                    if (cur == null || n.failed.size < cur.first.failed.size ||
                        (n.failed.size == cur.first.failed.size && (n.pos < cur.first.pos || (n.pos == cur.first.pos && geo.getValue(f.id) < geo.getValue(cur.second.id))))) near = n to f
                }
            }
            if (frags.none { (w.key to it.id) in cmp }) near?.let { nearest[w.key] = it.first }
        }
        val accepted = pws.associate { w -> w.key to frags.filter { (w.key to it.id) in cmp }.sortedWith(compareBy { rank.getValue(w.key to it.id) }).map { it.id } }
        val pref = accepted.mapValues { it.value.firstOrNull() }
        val owner = LinkedHashMap<Int, String>(); val owned = HashMap<String, MutableList<Int>>()
        val mergedInto = LinkedHashMap<String, String>(); val newAliases = mutableListOf<WallAlias>()
        val rel = mutableListOf<Triple<String, String, RelationBasis>>()
        fun own(k: String, f: Int) { owner[f] = k; owned.getOrPut(k) { mutableListOf() }.add(f) }
        fun isDistinct(a: String, b: String) = pairOf(a, b) in distinct

        // Fase 1: frammenti preferiti da almeno una parete (merge dimostrato, vietato o non distinguibile).
        for (f in frags) {
            val g = pws.filter { pref[it.key] == f.id }.map { it.key }.sortedBy { wallNumber(it) }
            if (g.isEmpty()) continue
            if (g.size == 1) { own(g[0], f.id); continue }
            val m2 = g.all { (maxOv[it to f.id] ?: -1.0) > 0 }                                   // f copre una parte di ciascuna
            val m3 = g.indices.all { i -> (i + 1 until g.size).all { j -> !isDistinct(g[i], g[j]) } } // mai co-osservate distinte
            if (m2 && m3) {
                val canonical = g.first()
                own(canonical, f.id)
                for (o in g.drop(1)) { mergedInto[o] = canonical; newAliases.add(WallAlias(o, canonical, index, MatchStatus.MERGE)) }
            } else {
                val ow = g.minWithOrNull(compareBy<String>({ rank.getValue(it to f.id) }, { wallNumber(it) }))!!
                own(ow, f.id)
                for (o in g) if (o != ow) rel.add(Triple(ow, o, if (isDistinct(ow, o)) RelationBasis.CO_OBSERVED_OVERLAP else RelationBasis.INSUFFICIENT_MERGE_EVIDENCE))
            }
        }
        // Fase 2: altri frammenti accettati → parte di split (disgiunti) o alternativa (sovrapposti: parete propria, opzione B).
        fun rootNow(k: String) = mergedInto[k] ?: k
        val rest = frags.filter { it.id !in owner }
        val bestOf = rest.associate { f -> f.id to pws.filter { (it.key to f.id) in cmp }.minWithOrNull(compareBy<SessionWall>({ rank.getValue(it.key to f.id) }, { wallNumber(it.key) })) }
        val order = rest.sortedWith(compareBy<EstimatedWall, Rank?>(nullsLast()) { f -> bestOf[f.id]?.let { rank.getValue(it.key to f.id) } }.thenBy { geo.getValue(it.id) })
        val unassociated = HashSet<Int>(); val altOf = HashMap<Int, List<String>>()
        for (f in order) {
            val best = bestOf[f.id]
            if (best == null) { unassociated.add(f.id); continue }
            val target = rootNow(best.key)
            val mine = owned[target].orEmpty()
            if (mine.isEmpty() || mine.all { disjoint(byId.getValue(it), f, byId.getValue(mine.first())) }) own(target, f.id)
            else { unassociated.add(f.id); altOf[f.id] = pws.filter { (it.key to f.id) in cmp }.map { rootNow(it.key) }.distinct().sortedBy { wallNumber(it) } }
        }
        var next = startNumber
        val newWalls = LinkedHashSet<String>()
        for (f in frags) if (f.id in unassociated) { val k = "SW${next++}"; own(k, f.id); newWalls.add(k) }
        for ((f, accs) in altOf.entries.sortedBy { geo.getValue(it.key) }) for (a in accs) rel.add(Triple(a, owner.getValue(f), RelationBasis.CO_OBSERVED_OVERLAP))
        // Co-osservazioni: pareti con frammenti propri sovrapposti che si accettano a vicenda (evidenza di distinzione).
        val keys = owned.keys.sortedBy { wallNumber(it) }
        for (i in keys.indices) for (j in i + 1 until keys.size) {
            if (owned.getValue(keys[i]).any { x -> owned.getValue(keys[j]).any { y -> coObserved(byId.getValue(x), byId.getValue(y)) } })
                rel.add(Triple(keys[i], keys[j], RelationBasis.CO_OBSERVED_OVERLAP))
        }
        val relations = rel.map { (a, b, basis) -> val p = pairOf(a, b); Triple(p.first, p.second, basis) }
            .groupBy { it.first to it.second }.map { (p, l) -> WallRelation(index, WallRelationKind.AMBIGUOUS_WITH, p.first, p.second,
                if (l.any { it.third == RelationBasis.CO_OBSERVED_OVERLAP }) RelationBasis.CO_OBSERVED_OVERLAP else l.first().third) }
            .sortedWith(compareBy({ wallNumber(it.a) }, { wallNumber(it.b) }))
        // Ordine dei frammenti posseduti: primario (rank migliore per la parete) e poi per geoKey.
        val ownedSorted = owned.mapValues { (k, l) ->
            val members = listOf(k) + mergedInto.filterValues { it == k }.keys
            val r = { f: Int -> members.mapNotNull { rank[it to f] }.minOrNull() }
            l.sortedWith(compareBy<Int, Rank?>(nullsLast()) { r(it) }.thenBy { geo.getValue(it) })
        }
        return Assignment(frags, geo, cmp, rank, accepted, nearest, owner, ownedSorted, mergedInto, newAliases, newWalls, relations, next)
    }

    // ------------------------------------------------------------------------------------------------------ aggiorna

    private class Upd(var t: SessionTarget) {
        /** [previous] = valore precedente realmente noto (default: l'ultima osservazione); null per le emissioni (nessun "prima"). */
        fun go(scan: Int, to: TargetState, reason: TransitionReason, cur: TargetObservation?, change: Double? = null, match: MatchStatus? = null,
               previous: TargetObservation? = t.last, related: String? = null, candidates: List<TargetCandidate> = emptyList()) {
            t = t.copy(state = to, history = t.history + TransitionEvent(scan, t.state, to, reason, change, match, previous, cur, related, candidates))
        }
    }

    /** Direzione verso l'esterno di un'estremità, con l'evidenza che la giustifica. */
    private class Dir(val x: Double, val z: Double, val ev: OutwardEvidence) { val legacy get() = ev.source == OutwardSource.LEGACY_DERIVED }

    /** Un frammento ammissibile considerato per un bersaglio: verdetto, candidato aperto (se c'è), parete proprietaria. */
    private class Entry(val f: EstimatedWall, val end: String?, val verdict: Verdict, val cand: Cand?, val own: Boolean, val obs: TargetObservation, val absorbedEnd: String? = null)

    private sealed class Outcome {
        class Follow(val cand: Cand, val obs: TargetObservation, val mixed: Boolean, val entries: List<Entry>, val alts: List<TargetAlternative>, val keyOf: Map<Entry, String> = emptyMap()) : Outcome()
        class Decided(val to: TargetState, val reason: TransitionReason, val obs: TargetObservation?, val related: String? = null,
                      val pendingEnd: Pair<Int, String>? = null, val pendingSegment: Pair<Int, Double>? = null, val internal: Boolean? = null,
                      val entries: List<Entry> = emptyList(), val alts: List<TargetAlternative> = emptyList(), val notRefound: Boolean = false, val keyOf: Map<Entry, String> = emptyMap()) : Outcome()
    }

    private class Ctx(
        val index: Int, val scan: ScanInput, val cfg: RescanConfig, val cands: List<Cand>, val asg: Assignment,
        val prevWalls: Map<String, SessionWall>, val aliasOf: Map<String, String>, val quality: Map<Int, WallQuality>,
        val prevAlts: Map<String, List<TargetAlternative>>,
    ) {
        val walls = scan.walls.associateBy { it.id }
        val usedBy = HashMap<Cand, String>()
        val reserved = HashMap<Cand, String>()
        val alts = HashMap<String, List<TargetAlternative>>()
        fun root(k: String): String { var x = k; var guard = 0; while (guard++ < 10_000) x = aliasOf[x] ?: return x; return x }
        fun group(r: String): List<String> = listOf(r) + asg.mergedInto.filterValues { it == r }.keys.sortedBy { wallNumber(it) }
        fun rankFor(r: String, f: Int): Rank? = group(r).mapNotNull { asg.rank[it to f] }.minOrNull()
        /** Frammenti accettati dalla regola approvata per la parete r (e per le pareti fuse in r in questa scansione), per preferenza. */
        fun pool(r: String): List<EstimatedWall> = group(r).flatMap { asg.accepted[it].orEmpty() }.distinct()
            .sortedWith(compareBy<Int, Rank?>(nullsLast()) { rankFor(r, it) }.thenBy { asg.geo.getValue(it) }).map { walls.getValue(it) }
        fun ownedBy(f: Int, r: String) = asg.owner[f]?.let { root(it) } == r
    }

    private fun tolPos(a: Double, b: Double) = MATCH_POSITION_BASE_M + 3 * sqrt(a * a + b * b)
    private fun outOf(w: EstimatedWall, end: String) = if (end == "END") doubleArrayOf(w.geometry.ux, w.geometry.uz) else doubleArrayOf(-w.geometry.ux, -w.geometry.uz)
    private fun sideOf(w: EstimatedWall, d: Dir) = if (d.x * w.geometry.ux + d.z * w.geometry.uz > 0) "END" else "START"
    private fun followedDir(w: EstimatedWall, end: String, scan: Int) = outOf(w, end).let { Dir(it[0], it[1], OutwardEvidence(OutwardSource.FOLLOWED_FRAGMENT_END, FragmentRef(scan, w.id), end, scan)) }
    private fun withOut(o: TargetObservation, d: Dir?) = if (d == null) o else o.copy(outX = d.x, outZ = d.z, outEvidence = d.ev)
    /** Finestra di corrispondenza (individua candidati, non prova nulla): ≤ 0,20 m lungo la linea e ≤ 3 cm + 3σ in perpendicolare. */
    private fun inWindow(w: EstimatedWall, end: String, px: Double, pz: Double, sigmaT: Double): Boolean {
        val q = endPoint(w, end)
        val along = abs((q[0] - px) * w.geometry.ux + (q[1] - pz) * w.geometry.uz)
        val across = abs((q[0] - px) * w.geometry.nx + (q[1] - pz) * w.geometry.nz)
        return along <= MATCH_EXTENT_GAP_M && across <= tolPos(sigmaT, w.uncertainty.positionSigmaM)
    }
    private fun sigmaOf(t: SessionTarget, ctx: Ctx) = (t.wallKey?.let { ctx.prevWalls[it] ?: ctx.prevWalls[ctx.root(it)] })?.positionSigmaM ?: 0.0

    /** Direzione memorizzata (evidenza di frammento); altrimenti dall'estremità di un frammento della parete sul punto; altrimenti legacy. */
    private fun dirOf(t: SessionTarget, ctx: Ctx, r: String): Dir? {
        val o = t.last
        val stored = if (o.outX != null && o.outZ != null && o.outEvidence != null) Dir(o.outX, o.outZ, o.outEvidence) else null
        if (stored != null && !stored.legacy) return stored
        val px = o.pointX ?: return stored; val pz = o.pointZ ?: return stored
        val sig = sigmaOf(t, ctx)
        val hits = ctx.pool(r).filter { ctx.ownedBy(it.id, r) }.flatMap { w -> listOf("START", "END").filter { inWindow(w, it, px, pz, sig) }.map { w to it } }
        if (hits.isNotEmpty()) {
            val d0 = outOf(hits[0].first, hits[0].second)
            if (hits.any { (w, e) -> outOf(w, e).let { it[0] * d0[0] + it[1] * d0[1] } <= 0 }) return null
            if (stored != null && stored.x * d0[0] + stored.z * d0[1] <= 0) return null
            return followedDir(hits[0].first, hits[0].second, ctx.index)
        }
        if (stored != null) return stored
        val own = t.wallKey?.let { ctx.prevWalls[it] ?: ctx.prevWalls[r] } ?: return null
        val mid = line(own).pt((own.startU + own.endU) / 2)
        val sgn = if ((px - mid[0]) * own.ux + (pz - mid[1]) * own.uz >= 0) 1.0 else -1.0
        return Dir(own.ux * sgn, own.uz * sgn, OutwardEvidence(OutwardSource.LEGACY_DERIVED, scanIndex = ctx.index))
    }

    private fun endEntry(ctx: Ctx, f: EstimatedWall, end: String, own: Boolean): Entry {
        val p = endPoint(f, end)
        val base = TargetObservation(false, r3WallId = f.id, pointX = p[0], pointZ = p[1])
        val c = ctx.cands.firstOrNull { it.kind == TargetKind.WALL_ENDPOINT && it.r3 == f.id && it.end == end }
        if (c != null) return Entry(f, end, Verdict.OPEN, c, own, c.obs)
        val q = ctx.quality[f.id] ?: return Entry(f, end, Verdict.NOT_OBSERVED, null, own, base)
        val st = if (end == "START") q.completeness.startState else q.completeness.endState
        if (st != EndState.OBSERVED) return Entry(f, end, Verdict.NOT_OBSERVED, null, own, base)
        val ext = ctx.scan.perimeter.stretches.any { it.wallId == f.id && it.kind == (if (end == "START") "prolungamento dall'angolo" else "prolungamento fino all'angolo") }
        return Entry(f, end, if (ext) Verdict.BELOW_MIN else Verdict.OBSERVED, null, own, base.copy(endpointState = st.name))
    }

    private fun segEntry(ctx: Ctx, f: EstimatedWall, o: TargetObservation, own: Boolean): Entry {
        val a = proj(f, o.segAx!!, o.segAz!!); val b = proj(f, o.segBx!!, o.segBz!!)
        val lo = min(a, b); val hi = max(a, b); val g = f.geometry
        val base = TargetObservation(false, r3WallId = f.id, segAx = o.segAx, segAz = o.segAz, segBx = o.segBx, segBz = o.segBz)
        if (lo < g.startU || hi > g.endU) return Entry(f, null, Verdict.NOT_REACHABLE, null, own, base)
        val gaps = f.gaps.filter { min(hi, it.toU) - max(lo, it.fromU) > 0 }
        if (gaps.isEmpty()) return Entry(f, null, Verdict.OBSERVED, null, own, base.copy(unobservedM = 0.0))
        if (gaps.all { it.lengthM < ctx.cfg.minUnobservedM }) return Entry(f, null, Verdict.BELOW_MIN, null, own, base.copy(unobservedM = gaps.sumOf { it.lengthM }))
        val c = ctx.cands.firstOrNull { it.kind == TargetKind.WALL_SEGMENT && it.r3 == f.id && segOverlap(o, it.obs, f) > 0 }
        val gap = gaps.maxByOrNull { it.lengthM }!!
        val end = if (gap.fromU - g.startU <= g.endU - gap.toU) "START" else "END"
        return Entry(f, null, Verdict.OPEN, c, own, base.copy(open = true, unobservedM = gap.lengthM), absorbedEnd = if (c == null) end else null)
    }

    private val closed = setOf(Verdict.OBSERVED, Verdict.BELOW_MIN)

    /**
     * Alternative sospese del bersaglio: abbinate ai frammenti considerati in questa scansione (stessa parete, stessa posizione),
     * altrimenti rivalutate sulla loro parete. DISAMBIGUATED solo con evidenza positiva (parete osservata, punto non più raggiunto).
     */
    private fun updateAlternatives(t: SessionTarget, ctx: Ctx, entries: List<Entry>, follow: Entry?): Pair<List<TargetAlternative>, Map<Entry, String>> {
        val existing = ctx.prevAlts[t.key].orEmpty()
        val keyOf = HashMap<Entry, String>()
        val matched = HashSet<Entry>()
        fun sameAs(a: TargetAlternative, e: Entry): Boolean {
            if (ctx.root(a.wallKey) != ctx.root(ctx.asg.owner.getValue(e.f.id)) || a.end != e.end) return false
            return if (e.end != null) {
                val x = a.observation.pointX; val z = a.observation.pointZ
                x != null && z != null && inWindow(e.f, e.end, x, z, e.f.uncertainty.positionSigmaM)
            } else true
        }
        val out = mutableListOf<TargetAlternative>()
        for (a in existing) {
            val e = entries.firstOrNull { it !in matched && sameAs(a, it) }
            if (e != null) {
                matched.add(e); keyOf[e] = a.altKey
                out.add(a.copy(lastEvaluatedScan = ctx.index, lastVerdict = e.verdict, status = AlternativeStatus.ACTIVE, disambiguatedScan = null))
                continue
            }
            val ra = ctx.root(a.wallKey)
            val pool = ctx.pool(ra)
            if (pool.isEmpty()) { out.add(a.copy(lastEvaluatedScan = ctx.index, lastVerdict = Verdict.NOT_OBSERVED)); continue }   // assenza: nessuna prova
            val o = a.observation
            val verdict: Verdict = if (a.end != null) {
                val px = o.pointX; val pz = o.pointZ
                val d = if (o.outX != null && o.outZ != null) Dir(o.outX, o.outZ, o.outEvidence ?: OutwardEvidence(OutwardSource.LEGACY_DERIVED, scanIndex = ctx.index)) else null
                val adm = if (px == null || pz == null || d == null) emptyList() else pool.filter { beyond(it, proj(it, px, pz)) <= MATCH_EXTENT_GAP_M }
                if (adm.isEmpty()) Verdict.NOT_REACHABLE else endEntry(ctx, adm.first(), sideOf(adm.first(), d!!), true).verdict
            } else if (o.segAx != null) {
                val mx = (o.segAx + o.segBx!!) / 2; val mz = (o.segAz!! + o.segBz!!) / 2
                pool.filter { beyond(it, proj(it, mx, mz)) <= MATCH_EXTENT_GAP_M }.map { segEntry(ctx, it, o, true) }.firstOrNull { it.verdict != Verdict.NOT_REACHABLE }?.verdict ?: Verdict.NOT_REACHABLE
            } else Verdict.NOT_OBSERVED
            out.add(if (verdict == Verdict.NOT_REACHABLE) a.copy(lastEvaluatedScan = ctx.index, lastVerdict = verdict, status = AlternativeStatus.DISAMBIGUATED, disambiguatedScan = ctx.index)
                    else a.copy(lastEvaluatedScan = ctx.index, lastVerdict = verdict, status = AlternativeStatus.ACTIVE, disambiguatedScan = null))
        }
        var n = existing.mapNotNull { it.altKey.removePrefix("ALT-").toIntOrNull() }.maxOrNull() ?: 0
        for (e in entries) {
            if (e === follow || e in matched || e.verdict == Verdict.NOT_REACHABLE) continue
            val k = "ALT-${++n}"
            keyOf[e] = k
            out.add(TargetAlternative(t.key, k, ctx.asg.owner.getValue(e.f.id), e.end, e.obs, ctx.index, ctx.index, e.verdict, AlternativeStatus.ACTIVE))
        }
        ctx.alts[t.key] = out
        return out to keyOf
    }

    private fun considered(ctx: Ctx, t: SessionTarget, entries: List<Entry>, follow: Entry?, keyOf: Map<Entry, String>) = entries.map { e ->
        TargetCandidate(FragmentRef(ctx.index, e.f.id), ctx.asg.owner.getValue(e.f.id), e.end, e.verdict,
            if (e === follow) t.key else e.cand?.let { ctx.usedBy[it] }, keyOf[e])
    }

    private fun carry(t: SessionTarget, d: Dir? = null) = withOut(t.last.copy(open = t.last.open), d ?: t.last.outEvidence?.let { Dir(t.last.outX!!, t.last.outZ!!, it) })
    private val notRefound get() = Outcome.Decided(TargetState.PERSISTENT, TransitionReason.NOT_REFOUND, null, notRefound = true)
    private fun notVerifiable(obs: TargetObservation? = null, internal: Boolean? = null) = Outcome.Decided(TargetState.PERSISTENT, TransitionReason.TARGET_NOT_VERIFIABLE, obs, internal = internal)

    /** Estremità aperta (§3.4): interna in questa scansione → copertura del punto; esterna → frammenti ammissibili, concordanza. */
    private fun endpointOpen(t: SessionTarget, ctx: Ctx): Outcome {
        val r = ctx.root(t.wallKey ?: return notRefound)
        val pool = ctx.pool(r)
        if (pool.isEmpty()) return notRefound
        val px = t.last.pointX ?: return notVerifiable(); val pz = t.last.pointZ ?: return notVerifiable()
        val dir = dirOf(t, ctx, r) ?: return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.DIRECTION_UNDETERMINED, carry(t))
        val sig = sigmaOf(t, ctx)
        // Interna IN QUESTA scansione: una parete fusa in r ora, con il punto medio dal lato esterno del punto.
        val chain = generateSequence(t.wallKey) { ctx.aliasOf[it] }.take(10_000).toList()
        val group = ctx.group(r)
        val ownMember = chain.firstOrNull { it in group } ?: r
        val inner = group.filter { it != ownMember }.any { k ->
            ctx.prevWalls[k]?.let { pw -> refsOf(pw).any { f -> val m = line(f).pt((f.startU + f.endU) / 2); (m[0] - px) * dir.x + (m[1] - pz) * dir.z > 0 } } ?: false
        }
        if (inner) {
            val w = ctx.walls.getValue(ctx.asg.owned.getValue(r).first())
            val u = proj(w, px, pz)
            val obs = withOut(TargetObservation(false, r3WallId = w.id, pointX = px, pointZ = pz), dir)
            if (perp(w, px, pz) > tolPos(sig, w.uncertainty.positionSigmaM)) return notVerifiable(obs, internal = true)
            if (w.geometry.observedSpans.any { u >= it.fromU && u <= it.toU }) return Outcome.Decided(TargetState.RESOLVED, TransitionReason.INTERNAL_COVERAGE, obs, internal = true)
            if (w.gaps.any { u >= it.fromU && u <= it.toU }) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.BECAME_INTERNAL_GAP, obs.copy(open = true), pendingSegment = w.id to u, internal = true)
            return notVerifiable(obs, internal = true)
        }
        val adm = pool.filter { beyond(it, proj(it, px, pz)) <= MATCH_EXTENT_GAP_M }
        if (adm.isEmpty()) return notVerifiable(carry(t, dir), internal = false)
        val entries = adm.map { f -> endEntry(ctx, f, sideOf(f, dir), ctx.ownedBy(f.id, r)) }
        val followable = entries.filter { it.own }.ifEmpty { entries }
        val follow = followable.firstOrNull { it.verdict == Verdict.OPEN && it.cand!! !in ctx.usedBy }
        val (alts, keyOf) = updateAlternatives(t, ctx, entries, follow ?: followable.first())
        val altVerdicts = alts.filter { it.status == AlternativeStatus.ACTIVE && entries.none { e -> keyOf[e] == it.altKey } }.map { it.lastVerdict }
        val others = entries.filter { it !== follow }.map { it.verdict } + altVerdicts
        for (e in entries) if (e !== follow && e.verdict == Verdict.OPEN && e.cand!! !in ctx.usedBy) ctx.reserved.getOrPut(e.cand) { t.key }
        if (follow != null) {
            val nd = followedDir(follow.f, follow.end!!, ctx.index)
            if (nd.x * dir.x + nd.z * dir.z <= 0) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.DIRECTION_UNDETERMINED, carry(t, dir), entries = entries, alts = alts, keyOf = keyOf)
            return Outcome.Follow(follow.cand!!, withOut(follow.cand.obs, nd), others.any { it != Verdict.OPEN }, entries, alts, keyOf)
        }
        val usedOpen = followable.firstOrNull { it.verdict == Verdict.OPEN }
        if (usedOpen != null) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.COVERED_BY_OTHER_TARGET, withOut(usedOpen.cand!!.obs, dir),
            related = ctx.usedBy[usedOpen.cand], entries = entries, alts = alts, keyOf = keyOf)
        val allClosed = (entries.map { it.verdict } + altVerdicts).all { it in closed }
        if (allClosed && dir.legacy) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.DIRECTION_UNDETERMINED, carry(t, dir), entries = entries, alts = alts, keyOf = keyOf)
        if (allClosed) {
            val b = followable.first()
            val bd = followedDir(b.f, b.end!!, ctx.index)
            return Outcome.Decided(TargetState.RESOLVED, if (b.verdict == Verdict.BELOW_MIN) TransitionReason.UNOBSERVED_BELOW_MIN else TransitionReason.ENDPOINT_OBSERVED,
                withOut(b.obs.copy(endpointState = b.obs.endpointState ?: "OBSERVED"), bd), internal = false, entries = entries, alts = alts, keyOf = keyOf)
        }
        return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.AMBIGUOUS_CANDIDATES, carry(t, dir), internal = false, entries = entries, alts = alts, keyOf = keyOf)
    }

    /** Tratto aperto (§3.5): corrispondente per sovrapposizione; risolto solo con verdetti concordi dei frammenti che lo contengono. */
    private fun segmentOpen(t: SessionTarget, ctx: Ctx): Outcome {
        val r = ctx.root(t.wallKey ?: return notRefound)
        val pool = ctx.pool(r)
        if (pool.isEmpty()) return notRefound
        val o = t.last
        if (o.segAx == null) return notVerifiable()
        val mx = (o.segAx + o.segBx!!) / 2; val mz = (o.segAz!! + o.segBz!!) / 2
        val entries = pool.filter { beyond(it, proj(it, mx, mz)) <= MATCH_EXTENT_GAP_M }.map { segEntry(ctx, it, o, ctx.ownedBy(it.id, r)) }
        val poolIds = pool.map { it.id }
        val segC = ctx.cands.filter { it.kind == TargetKind.WALL_SEGMENT && it.r3 in poolIds && segOverlap(o, it.obs, ctx.walls.getValue(it.r3!!)) > 0 }
            .sortedWith(compareBy<Cand>({ if (ctx.ownedBy(it.r3!!, r)) 0 else 1 }, { -segOverlap(o, it.obs, ctx.walls.getValue(it.r3!!)) }, { poolIds.indexOf(it.r3) }))
        val valid = entries.filter { it.verdict != Verdict.NOT_REACHABLE }
        val ownValid = valid.any { it.own }
        val followableC = if (ownValid) segC.filter { ctx.ownedBy(it.r3!!, r) }.ifEmpty { segC } else segC
        val fc = followableC.firstOrNull { it !in ctx.usedBy }
        val follow = fc?.let { c -> valid.firstOrNull { it.cand === c } }
        val (alts, keyOf) = updateAlternatives(t, ctx, valid, follow ?: valid.filter { it.own }.ifEmpty { valid }.firstOrNull())
        val altVerdicts = alts.filter { it.status == AlternativeStatus.ACTIVE && valid.none { e -> keyOf[e] == it.altKey } }.map { it.lastVerdict }
        for (c in segC) if (c !== fc && c !in ctx.usedBy) ctx.reserved.getOrPut(c) { t.key }
        if (fc != null) {
            val others = valid.filter { it !== follow }.map { it.verdict } + altVerdicts
            return Outcome.Follow(fc, fc.obs, others.any { it != Verdict.OPEN }, valid, alts, keyOf)
        }
        val used = followableC.firstOrNull()
        if (used != null) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.COVERED_BY_OTHER_TARGET, used.obs, related = ctx.usedBy[used], entries = valid, alts = alts, keyOf = keyOf)
        if (valid.isEmpty()) return notVerifiable()
        val verdicts = valid.map { it.verdict } + altVerdicts
        val openE = valid.firstOrNull { it.verdict == Verdict.OPEN }
        if (openE != null && verdicts.any { it != Verdict.OPEN }) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.AMBIGUOUS_CANDIDATES, openE.obs, entries = valid, alts = alts, keyOf = keyOf)
        if (openE != null) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.SEGMENT_ABSORBED_BY_ENDPOINT, openE.obs,
            pendingEnd = openE.absorbedEnd?.let { openE.f.id to it }, entries = valid, alts = alts, keyOf = keyOf)
        if (verdicts.all { it in closed }) {
            val b = valid.filter { it.own }.ifEmpty { valid }.first()
            return Outcome.Decided(TargetState.RESOLVED, if (valid.all { it.verdict == Verdict.OBSERVED }) TransitionReason.SEGMENT_OBSERVED else TransitionReason.UNOBSERVED_BELOW_MIN,
                b.obs, entries = valid, alts = alts, keyOf = keyOf)
        }
        return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.AMBIGUOUS_CANDIDATES, null, entries = valid, alts = alts, keyOf = keyOf)
    }

    /** Viste, misura, ambiguità: solo sui frammenti POSSEDUTI dalla parete (le pareti AMBIGUOUS_WITH sono altre identità). */
    private fun perWallOpen(t: SessionTarget, ctx: Ctx): Outcome {
        val r = ctx.root(t.wallKey ?: return notRefound)
        val pool = ctx.pool(r)
        if (pool.isEmpty()) return notRefound
        val owned = ctx.asg.owned[r].orEmpty()
        if (owned.isEmpty()) {
            val c = ctx.cands.firstOrNull { it.kind == t.kind && it.r3 in pool.map { w -> w.id } && it in ctx.usedBy }
            return if (c != null) Outcome.Decided(TargetState.PERSISTENT, TransitionReason.COVERED_BY_OTHER_TARGET, c.obs, related = ctx.usedBy[c]) else notVerifiable()
        }
        val cs = owned.mapNotNull { f -> ctx.cands.firstOrNull { it.kind == t.kind && it.r3 == f } }
        val free = cs.firstOrNull { it !in ctx.usedBy }
        if (free != null) {
            for (c in cs) if (c !== free && c !in ctx.usedBy) ctx.usedBy[c] = t.key   // stesso difetto della stessa parete su un'altra parte
            return Outcome.Follow(free, free.obs, false, emptyList(), emptyList())
        }
        if (cs.isNotEmpty()) return Outcome.Decided(TargetState.PERSISTENT, TransitionReason.COVERED_BY_OTHER_TARGET, cs.first().obs, related = ctx.usedBy[cs.first()])
        val qs = owned.map { ctx.quality[it] ?: return notVerifiable() }
        val p = owned.first(); val q = qs.first()
        return when (t.kind) {
            TargetKind.WALL_VIEWS -> if (qs.all { it.evidence.viewCount >= ctx.cfg.minViews })
                Outcome.Decided(TargetState.RESOLVED, TransitionReason.VIEWS_SUFFICIENT, TargetObservation(false, r3WallId = p, viewCount = q.evidence.viewCount)) else notVerifiable()
            TargetKind.WALL_MEASUREMENT -> if (qs.all { it.measurement.level != QualityLevel.LOW }) Outcome.Decided(TargetState.RESOLVED, TransitionReason.MEASUREMENT_WITHIN_TOLERANCE,
                TargetObservation(false, r3WallId = p, sigmaPositionM = q.measurement.positionSigmaM, sigmaDirectionDeg = q.measurement.headingSigmaDeg, measurementLevel = q.measurement.level.name)) else notVerifiable()
            TargetKind.WALL_AMBIGUITY -> {
                val obs = TargetObservation(false, r3WallId = p, ambiguityScore = q.ambiguity.maxScore, ambiguityStability = q.ambiguity.sourceSurfaceStability)
                when {
                    qs.any { it.ambiguity.maxScore == null } -> notVerifiable()
                    qs.all { it.ambiguity.maxScore!! < ctx.cfg.ambiguityTau } -> Outcome.Decided(TargetState.RESOLVED, TransitionReason.AMBIGUITY_BELOW_TAU, obs)
                    else -> Outcome.Decided(TargetState.RESOLVED, TransitionReason.AMBIGUITY_STABILIZED, obs)
                }
            }
            else -> notVerifiable()
        }
    }

    private fun perimeterOpen(ctx: Ctx): Outcome {
        val c = ctx.cands.firstOrNull { it.kind == TargetKind.PERIMETER && it !in ctx.usedBy }
        if (c != null) return Outcome.Follow(c, c.obs, false, emptyList(), emptyList())
        val st = ctx.scan.quality.room.perimeterState
        return if (st == PerimeterState.CLOSED) Outcome.Decided(TargetState.RESOLVED, TransitionReason.PERIMETER_CLOSED, TargetObservation(false, perimeterState = st.name)) else notRefound
    }

    private fun matchStatusOf(r: Map<String, MatchStatus>, t: SessionTarget, ctx: Ctx) = t.wallKey?.let { r[it] ?: r[ctx.root(it)] }

    /** Aggiorna la sessione (null = prima scansione) con una nuova scansione. Deterministico: nessun tempo, nessun caso, nessun id R3 nelle scelte. */
    fun update(previous: RescanSession?, scan: ScanInput, cfg: RescanConfig = RescanConfig()): RescanSession {
        val fp = fingerprint(scan)
        val index = (previous?.scans?.maxOfOrNull { it.index } ?: -1) + 1
        val frame = when {
            previous == null -> FrameStatus.FIRST_SCAN
            isContinuation(previous.scans.last().fingerprint, scan) -> FrameStatus.SAME_FRAME_PREFIX
            else -> FrameStatus.COORDINATE_FRAME_MISMATCH
        }
        val scans0 = previous?.scans.orEmpty()
        val epoch = scans0.count { it.frameStatus == FrameStatus.COORDINATE_FRAME_MISMATCH } + (if (frame == FrameStatus.COORDINATE_FRAME_MISMATCH) 1 else 0)
        fun epochOf(i: Int) = scans0.count { it.index <= i && it.frameStatus == FrameStatus.COORDINATE_FRAME_MISMATCH }
        val aliases0 = previous?.wallAliases.orEmpty()
        val absorbed0 = aliases0.map { it.absorbedKey }.toSet()
        val walls0 = previous?.walls.orEmpty().map { if (it.key in absorbed0 && it.state != WallState.ABSORBED) it.copy(state = WallState.ABSORBED) else it }
        val relations0 = previous?.wallRelations.orEmpty()
        val lastIndex = scans0.lastOrNull()?.index ?: -1

        // Relazioni dedotte per le sessioni precedenti (pareti senza frammenti registrati): prudenza, co-osservate se sovrapposte.
        val legacyRel = mutableListOf<WallRelation>()
        val legacy = walls0.filter { it.fragments.isEmpty() && it.state == WallState.ACTIVE }.sortedBy { wallNumber(it.key) }
        for (i in legacy.indices) for (j in i + 1 until legacy.size) {
            val a = legacy[i]; val b = legacy[j]
            if (a.lastScan != b.lastScan) continue
            val c1 = compare(line(a), line(b), 0); val c2 = compare(line(b), line(a), 0)
            if (c1.ok && c2.ok && c1.overlap > 0 && c2.overlap > 0) {
                val p = pairOf(a.key, b.key)
                if (relations0.none { it.a == p.first && it.b == p.second }) legacyRel.add(WallRelation(a.lastScan, WallRelationKind.AMBIGUOUS_WITH, p.first, p.second, RelationBasis.LEGACY_INFERRED))
            }
        }
        val distinct = (relations0 + legacyRel).filter { it.basis != RelationBasis.INSUFFICIENT_MERGE_EVIDENCE }.map { it.a to it.b }.toSet()

        val pws = if (frame == FrameStatus.SAME_FRAME_PREFIX) walls0.filter { it.state == WallState.ACTIVE && epochOf(it.lastScan) == epoch }.sortedBy { wallNumber(it.key) } else emptyList()
        val asg = assign(index, scan, pws, distinct, previous?.nextWallNumber ?: 1)
        val aliases = aliases0 + asg.newAliases
        val aliasOf = aliases.associate { it.absorbedKey to it.canonicalKey }
        val partnersOf = asg.relations.flatMap { listOf(it.a to it.b, it.b to it.a) }.groupBy({ it.first }, { it.second })

        // Corrispondenze tipizzate (D7): nessuno stato sovrascrive un altro.
        val matches = mutableListOf<WallMatch>()
        val statusOf = HashMap<String, MatchStatus>()
        when (frame) {
            FrameStatus.FIRST_SCAN -> for (f in asg.frags) matches.add(WallMatch(index, null, f.id, MatchStatus.FIRST_SCAN, relations = listOf(MatchStatus.FIRST_SCAN), primary = FragmentRef(index, f.id)))
            FrameStatus.COORDINATE_FRAME_MISMATCH -> for (w in walls0.filter { it.state == WallState.ACTIVE && epochOf(it.lastScan) == epoch - 1 }.sortedBy { wallNumber(it.key) })
                matches.add(WallMatch(index, w.key, null, MatchStatus.COORDINATE_FRAME_MISMATCH, relations = listOf(MatchStatus.COORDINATE_FRAME_MISMATCH)))
            FrameStatus.SAME_FRAME_PREFIX -> for (w in pws) {
                val acc = asg.accepted.getValue(w.key)
                val dormant = if (w.lastScan < lastIndex) w.lastScan else null
                if (acc.isEmpty()) {
                    val n = asg.nearest[w.key]
                    matches.add(WallMatch(index, w.key, null, MatchStatus.NO_GEOMETRIC_MATCH, n?.cur, n?.pos, n?.tol, n?.angle, n?.overlap, n?.failed.orEmpty(),
                        relations = listOf(MatchStatus.NO_GEOMETRIC_MATCH),
                        candidates = listOfNotNull(n?.let { CandidateLink(FragmentRef(index, it.cur), FragmentRole.NEAREST_REJECTED, it.pos, it.tol, it.angle, it.overlap, it.failed) }),
                        dormantSinceScan = dormant))
                    statusOf[w.key] = MatchStatus.NO_GEOMETRIC_MATCH
                    continue
                }
                val r = asg.mergedInto[w.key] ?: w.key
                val ownedW = asg.owned[w.key].orEmpty()
                val primaryF = if (ownedW.isNotEmpty()) ownedW.first() else acc.first()
                val merged = w.key in asg.mergedInto || asg.mergedInto.containsValue(w.key)
                val partners = partnersOf[w.key].orEmpty().distinct().sortedBy { wallNumber(it) }
                val ambiguous = partners.isNotEmpty() || acc.any { asg.owner[it]?.let { o -> (asg.mergedInto[o] ?: o) != r } == true }
                val rels = buildList {
                    if (ownedW.isNotEmpty()) add(MatchStatus.MATCH_FOUND)
                    if (ownedW.size >= 2) add(MatchStatus.SPLIT)
                    if (ambiguous) add(MatchStatus.AMBIGUOUS_MATCH)
                    if (merged) add(MatchStatus.MERGE)
                    if (dormant != null) add(MatchStatus.REFOUND)
                }.sortedBy { it.ordinal }
                val status = listOf(MatchStatus.MERGE, MatchStatus.AMBIGUOUS_MATCH, MatchStatus.SPLIT, MatchStatus.REFOUND, MatchStatus.MATCH_FOUND).first { it in rels }
                statusOf[w.key] = status
                val c = asg.cmp.getValue(w.key to primaryF)
                val links = acc.map { f ->
                    val cf = asg.cmp.getValue(w.key to f)
                    val role = when {
                        asg.owner[f] == w.key -> if (f == ownedW.first()) FragmentRole.PRIMARY else FragmentRole.SPLIT_PART
                        asg.owner[f] == r -> FragmentRole.PRIMARY
                        else -> FragmentRole.AMBIGUOUS_ALTERNATIVE
                    }
                    CandidateLink(FragmentRef(index, f), role, cf.pos, cf.tol, cf.angle, cf.overlap)
                }
                val group = (listOf(r) + asg.mergedInto.filterValues { it == r }.keys).filter { merged && it != w.key }.distinct().sortedBy { wallNumber(it) }
                matches.add(WallMatch(index, w.key, primaryF, status, primaryF, c.pos, c.tol, c.angle, c.overlap, emptyList(), acc.filter { it != primaryF },
                    rels, FragmentRef(index, primaryF), links, group, partners, dormant))
            }
        }

        // Bersagli correnti, in ordine deterministico (parete, tipo, posizione lungo la linea, geoKey): mai per id R3.
        val quality = scan.quality.walls.associateBy { it.wallId }
        val candsRaw = candidates(scan, cfg)
        fun posKey(c: Cand): Double {
            val w = c.r3?.let { id -> scan.walls.first { it.id == id } } ?: return 0.0
            val x = c.obs.pointX ?: c.obs.segAx?.let { (it + c.obs.segBx!!) / 2 } ?: return 0.0
            val z = c.obs.pointZ ?: c.obs.segAz?.let { (it + c.obs.segBz!!) / 2 } ?: return 0.0
            return nz0(-w.geometry.nz * x + w.geometry.nx * z)
        }
        val cands = candsRaw.sortedWith(compareBy<Cand>({ it.r3?.let { r3 -> wallNumber(asg.owner.getValue(r3)) } ?: -1 }, { kindOrder.getValue(it.kind) },
            { posKey(it) }).thenBy(nullsFirst()) { it.r3?.let { r3 -> asg.geo.getValue(r3) } }.thenBy { it.end ?: "" })

        val targets = previous?.targets?.associateBy { it.key }?.mapValues { Upd(it.value) }?.toMutableMap() ?: mutableMapOf()
        val prevAlts = previous?.targetAlternatives.orEmpty().groupBy { it.targetKey }
        val ctx = Ctx(index, scan, cfg, cands, asg, walls0.associateBy { it.key }, aliasOf, quality, prevAlts)
        val pending = mutableListOf<Triple<String, Outcome.Decided, Int>>()
        val fillFollowed = mutableListOf<Triple<String, Int, List<Cand?>>>()   // (bersaglio, evento, candidato di ciascuna voce)
        val links = mutableListOf<TargetLink>()
        val linkCand = mutableListOf<Cand>()
        val compared = frame == FrameStatus.SAME_FRAME_PREFIX
        val priority = compareBy<Upd>({ if (it.t.state == TargetState.RESOLVED) 1 else 0 }, { it.t.wallKey?.let { k -> wallNumber(ctx.root(k)) } ?: -1 },
            { if (it.t.wallKey == null || it.t.wallKey == ctx.root(it.t.wallKey!!)) 0 else 1 }, { kindOrder.getValue(it.t.kind) }, { it.t.firstScan }, { suffix(it.t.key) }, { it.t.key })

        if (compared) {
            // Passaggio A: bersagli APERTI in ordine di priorità.
            for (u in targets.values.filter { it.t.frameEpoch == epoch }.sortedWith(priority)) {
                val t = u.t
                if (t.state != TargetState.PROPOSED && t.state != TargetState.IMPROVED && t.state != TargetState.PERSISTENT) continue
                val outcome = when (t.kind) {
                    TargetKind.PERIMETER -> perimeterOpen(ctx)
                    TargetKind.WALL_ENDPOINT -> endpointOpen(t, ctx)
                    TargetKind.WALL_SEGMENT -> segmentOpen(t, ctx)
                    else -> perWallOpen(t, ctx)
                }
                val m = matchStatusOf(statusOf, t, ctx)
                val att = when (outcome) { is Outcome.Follow -> outcome.obs; is Outcome.Decided -> outcome.obs }
                u.go(index, TargetState.ATTEMPTED, TransitionReason.NEW_SCAN_PROCESSED, att, match = m)
                when (outcome) {
                    is Outcome.Follow -> {
                        ctx.usedBy[outcome.cand] = t.key
                        val (to0, reason0, change) = compareOpen(t, outcome.obs, cfg)
                        val (to, reason) = if (outcome.mixed) TargetState.PERSISTENT to TransitionReason.AMBIGUOUS_CANDIDATES else to0 to reason0
                        val cs = considered(ctx, t, outcome.entries, outcome.entries.firstOrNull { it.cand === outcome.cand }, outcome.keyOf)
                        u.go(index, to, reason, outcome.obs, if (outcome.mixed) null else change, m, candidates = cs)
                        fillFollowed.add(Triple(t.key, u.t.history.size - 1, outcome.entries.map { it.cand }))
                        if (t.kind == TargetKind.WALL_ENDPOINT) u.t = u.t.copy(internal = false)
                    }
                    is Outcome.Decided -> {
                        val cs = considered(ctx, t, outcome.entries, null, outcome.keyOf)
                        u.go(index, outcome.to, outcome.reason, outcome.obs, match = m ?: if (outcome.notRefound) MatchStatus.NO_GEOMETRIC_MATCH else null, related = outcome.related, candidates = cs)
                        if (outcome.pendingEnd != null || outcome.pendingSegment != null) pending.add(Triple(t.key, outcome, u.t.history.size - 1))
                        fillFollowed.add(Triple(t.key, u.t.history.size - 1, outcome.entries.map { it.cand }))
                        outcome.internal?.let { u.t = u.t.copy(internal = it) }
                    }
                }
                u.t = u.t.copy(attempts = u.t.attempts + 1, lastScan = index, last = u.t.history.last().current ?: u.t.last)
            }
            // Passaggio B: bersagli RESOLVED → solo riapertura, con evidenze sufficienti (estremità: H1–H4).
            val claims = LinkedHashMap<Cand, MutableList<Upd>>()
            val maybe = LinkedHashMap<Cand, MutableList<Pair<Upd, List<ContinuityCondition>>>>()
            for (u in targets.values.filter { it.t.frameEpoch == epoch && it.t.state == TargetState.RESOLVED }.sortedWith(priority)) {
                val t = u.t
                val r = t.wallKey?.let { ctx.root(it) }
                when (t.kind) {
                    TargetKind.PERIMETER -> ctx.cands.firstOrNull { it.kind == TargetKind.PERIMETER && it !in ctx.usedBy }?.let { claims.getOrPut(it) { mutableListOf() }.add(u) }
                    TargetKind.WALL_ENDPOINT -> {
                        if (r == null) continue
                        val pool = ctx.pool(r); if (pool.isEmpty()) continue
                        val px = t.last.pointX ?: continue; val pz = t.last.pointZ ?: continue
                        val dir = dirOf(t, ctx, r)
                        val sig = sigmaOf(t, ctx)
                        for (f in pool) {
                            val sides = if (dir != null) listOf(sideOf(f, dir)) else listOf("START", "END")
                            for (end in sides) {
                                val c = ctx.cands.firstOrNull { it.kind == TargetKind.WALL_ENDPOINT && it.r3 == f.id && it.end == end } ?: continue
                                if (c in ctx.usedBy) continue
                                val h1 = inWindow(f, end, px, pz, sig)
                                val h2 = ctx.ownedBy(f.id, r)
                                val h3 = dir != null && !dir.legacy
                                if (h1 && h2 && h3) claims.getOrPut(c) { mutableListOf() }.add(u)
                                else if (h1 || beyond(f, proj(f, px, pz)) <= MATCH_EXTENT_GAP_M)
                                    maybe.getOrPut(c) { mutableListOf() }.add(u to listOfNotNull(if (!h1) ContinuityCondition.H1_GEOMETRIC_WINDOW else null,
                                        if (!h2) ContinuityCondition.H2_SAME_WALL else null, if (!h3) ContinuityCondition.H3_DIRECTION else null))
                            }
                        }
                    }
                    TargetKind.WALL_SEGMENT -> {
                        if (r == null) continue
                        val seg = t.last.takeIf { it.segAx != null } ?: t.history.lastOrNull { it.to == TargetState.RESOLVED }?.previous ?: continue
                        if (seg.segAx == null) continue
                        ctx.cands.firstOrNull { it.kind == TargetKind.WALL_SEGMENT && it !in ctx.usedBy && ctx.ownedBy(it.r3!!, r) && segOverlap(seg, it.obs, ctx.walls.getValue(it.r3)) > 0 }
                            ?.let { claims.getOrPut(it) { mutableListOf() }.add(u) }
                    }
                    else -> if (r != null) ctx.asg.owned[r].orEmpty().firstNotNullOfOrNull { f -> ctx.cands.firstOrNull { it.kind == t.kind && it.r3 == f && it !in ctx.usedBy } }
                        ?.let { claims.getOrPut(it) { mutableListOf() }.add(u) }
                }
            }
            for ((c, us) in claims) {
                if (c in ctx.usedBy) continue
                if (us.size != 1) { for (u in us) maybe.getOrPut(c) { mutableListOf() }.add(u to listOf(ContinuityCondition.H4_UNIQUE_HISTORY)); continue }
                val u = us.single()
                ctx.usedBy[c] = u.t.key
                val obs = if (c.kind == TargetKind.WALL_ENDPOINT) withOut(c.obs, followedDir(ctx.walls.getValue(c.r3!!), c.end!!, index)) else c.obs
                u.go(index, TargetState.NEW, TransitionReason.REAPPEARED, obs, match = matchStatusOf(statusOf, u.t, ctx))
                u.go(index, TargetState.PROPOSED, TransitionReason.EMITTED, obs, previous = null)
                u.t = u.t.copy(reopenCount = u.t.reopenCount + 1, lastScan = index, last = obs, internal = if (u.t.kind == TargetKind.WALL_ENDPOINT) false else u.t.internal)
            }
            // Passaggio C: le alternative non toccate restano come sono (nessuna cancellazione).
            // Collegamenti di incertezza (solo per candidati che diventano bersagli nuovi, sotto).
            ctx.reserved.keys.removeAll { it in ctx.usedBy }
            maybe.keys.removeAll { it in ctx.usedBy || it in ctx.reserved }
            for ((c, l) in maybe) for ((u, failed) in l) {
                links.add(TargetLink(index, TargetLinkKind.POSSIBLE_CONTINUATION, "", u.t.key, failed.distinct().sortedBy { it.ordinal }))
                linkCand.add(c)
            }
        }

        // Passaggio D: candidati liberi e non riservati → NEW → PROPOSED.
        val newKeyOf = HashMap<Cand, String>()
        for (c in cands.filter { it !in ctx.usedBy && it !in ctx.reserved }) {
            val wk = c.r3?.let { asg.owner.getValue(it) }
            val key = when (c.kind) {
                TargetKind.PERIMETER -> "PERIMETER"
                TargetKind.WALL_ENDPOINT -> "$wk-${c.end}"
                TargetKind.WALL_SEGMENT -> "$wk-SEG-${(targets.keys.filter { it.startsWith("$wk-SEG-") }.mapNotNull { it.removePrefix("$wk-SEG-").toIntOrNull() }.maxOrNull() ?: 0) + 1}"
                TargetKind.WALL_VIEWS -> "$wk-VIEWS"
                TargetKind.WALL_MEASUREMENT -> "$wk-MEASUREMENT"
                TargetKind.WALL_AMBIGUITY -> "$wk-AMBIGUITY"
            }
            val match = when {
                frame == FrameStatus.FIRST_SCAN -> MatchStatus.FIRST_SCAN
                frame == FrameStatus.COORDINATE_FRAME_MISMATCH -> MatchStatus.COORDINATE_FRAME_MISMATCH
                wk != null && wk in asg.newWalls -> MatchStatus.NO_GEOMETRIC_MATCH
                else -> MatchStatus.MATCH_FOUND
            }
            var finalKey = key; var n = 2
            while (finalKey in targets) finalKey = "$key-${n++}"
            val obs = if (c.kind == TargetKind.WALL_ENDPOINT) withOut(c.obs, outOf(ctx.walls.getValue(c.r3!!), c.end!!).let {
                Dir(it[0], it[1], OutwardEvidence(OutwardSource.BIRTH_FRAGMENT_END, FragmentRef(index, c.r3), c.end, index)) }) else c.obs
            val t0 = SessionTarget(finalKey, c.kind, wk, c.end, TargetState.NEW, 0, 0, index, index, obs,
                listOf(TransitionEvent(index, null, TargetState.NEW, TransitionReason.FIRST_SEEN, null, match, null, obs)), epoch)
            targets[finalKey] = Upd(t0).also { it.go(index, TargetState.PROPOSED, TransitionReason.EMITTED, obs, previous = null) }
            ctx.usedBy[c] = finalKey
            newKeyOf[c] = finalKey
        }
        val finalLinks = links.zip(linkCand).mapNotNull { (l, c) -> newKeyOf[c]?.let { l.copy(newTargetKey = it) } }
        // Collegamenti da completare: chi segue ora il difetto (estremità in cui un tratto confluisce, tratto di un gap interno).
        for ((key, o, ev) in pending) {
            val related = o.pendingEnd?.let { (r3, end) -> ctx.cands.firstOrNull { it.kind == TargetKind.WALL_ENDPOINT && it.r3 == r3 && it.end == end }?.let { ctx.usedBy[it] } }
                ?: o.pendingSegment?.let { (r3, u) -> ctx.walls[r3]?.let { w -> ctx.cands.firstOrNull { c -> c.kind == TargetKind.WALL_SEGMENT && c.r3 == r3 && segContains(c.obs, w, u) }?.let { ctx.usedBy[it] } } }
            if (related != null) targets.getValue(key).let { up -> up.t = up.t.copy(history = up.t.history.mapIndexed { i, e -> if (i == ev) e.copy(relatedTargetKey = related) else e }) }
        }
        // Candidati considerati: chi li segue (noto solo a fine aggiornamento).
        for ((key, ev, cs) in fillFollowed) targets.getValue(key).let { up ->
            up.t = up.t.copy(history = up.t.history.mapIndexed { i, e ->
                if (i != ev) e else e.copy(candidates = e.candidates.mapIndexed { j, tc -> if (tc.followedBy == null) tc.copy(followedBy = cs.getOrNull(j)?.let { ctx.usedBy[it] }) else tc })
            })
        }

        // Pareti persistenti: frammenti di riferimento aggiornati; assorbite marcate; non osservate invariate.
        val byId = scan.walls.associateBy { it.id }
        val updated = asg.owned.entries.sortedBy { wallNumber(it.key) }.map { (k, ids) ->
            val p = byId.getValue(ids.first()); val g = p.geometry
            val prev = walls0.firstOrNull { it.key == k }
            SessionWall(k, p.id, g.nx, g.nz, g.cx, g.cz, g.ux, g.uz, g.startU, g.endU, p.uncertainty.positionSigmaM, quality[p.id]?.inRoom ?: false, index,
                WallState.ACTIVE, prev?.firstScan ?: index, ids.mapIndexed { i, id -> fragGeom(byId.getValue(id), index, if (i == 0) FragmentRole.PRIMARY else FragmentRole.SPLIT_PART) })
        }
        val updatedKeys = updated.map { it.key }.toSet()
        val kept = walls0.filter { it.key !in updatedKeys }.map { if (it.key in asg.mergedInto) it.copy(state = WallState.ABSORBED) else it }
        val sessionId = previous?.sessionId ?: "RS-${fp.poseHash.take(12)}-${fp.createdAtMillis}"
        val geoOrder = { id: Int? -> id?.let { asg.geo[it] } }
        val evaluatedAlts = ctx.alts
        val alternatives = (previous?.targetAlternatives.orEmpty().filter { it.targetKey !in evaluatedAlts } + evaluatedAlts.values.flatten())
            .sortedWith(compareBy({ it.targetKey }, { it.altKey.removePrefix("ALT-").toIntOrNull() ?: 0 }))
        return RescanSession(
            sessionId = sessionId, nextWallNumber = asg.nextNumber,
            scans = scans0 + ScanRecord(index, scan.dataset, fp, frame, scan.plan.decision.name, scan.plan.requests.map { it.id }.sorted(), scan.createdAtMillis),
            walls = (kept + updated).sortedBy { wallNumber(it.key) },
            targets = targets.values.map { it.t }.sortedBy { it.key },
            wallMatches = previous?.wallMatches.orEmpty() + matches.sortedWith(compareBy<WallMatch>({ it.previousKey?.let { k -> wallNumber(k) } ?: Int.MAX_VALUE })
                .thenBy(nullsFirst()) { geoOrder(it.currentR3WallId) }),
            wallAliases = aliases.sortedWith(compareBy({ it.scanIndex }, { wallNumber(it.absorbedKey) })),
            wallRelations = (relations0 + legacyRel + asg.relations).sortedWith(compareBy({ it.scanIndex }, { wallNumber(it.a) }, { wallNumber(it.b) }, { it.basis.ordinal })),
            targetAlternatives = alternatives,
            targetLinks = (previous?.targetLinks.orEmpty() + finalLinks).sortedWith(compareBy({ it.scanIndex }, { it.newTargetKey }, { it.historicalTargetKey })),
        )
    }

    private fun proj(w: EstimatedWall, x: Double, z: Double) = (x - w.geometry.cx) * w.geometry.ux + (z - w.geometry.cz) * w.geometry.uz
    private fun perp(w: EstimatedWall, x: Double, z: Double) = abs((x - w.geometry.cx) * w.geometry.nx + (z - w.geometry.cz) * w.geometry.nz)
    /** Distanza lungo la linea dal punto all'estensione [startU, endU] (0 se dentro). */
    private fun beyond(w: EstimatedWall, u: Double) = when { u < w.geometry.startU -> w.geometry.startU - u; u > w.geometry.endU -> u - w.geometry.endU; else -> 0.0 }
    private fun segContains(o: TargetObservation, w: EstimatedWall, u: Double): Boolean {
        val a = proj(w, o.segAx ?: return false, o.segAz!!); val b = proj(w, o.segBx!!, o.segBz!!)
        return u >= min(a, b) && u <= max(a, b)
    }
    private fun segOverlap(a: TargetObservation, b: TargetObservation, w: EstimatedWall): Double {
        val a0 = proj(w, a.segAx!!, a.segAz!!); val a1 = proj(w, a.segBx!!, a.segBz!!)
        val b0 = proj(w, b.segAx!!, b.segAz!!); val b1 = proj(w, b.segBx!!, b.segBz!!)
        return min(max(a0, a1), max(b0, b1)) - max(min(a0, a1), min(b0, b1))
    }

    private val perimeterRank = mapOf("NONE" to 0, "OPEN" to 0, "PARTIAL" to 1, "UNCERTAIN" to 2, "CLOSED" to 3)

    /** Bersaglio ancora aperto: miglioramento solo se misurabile e ≥ della soglia già approvata (0,20 m), mai dalla gravità. */
    private fun compareOpen(t: SessionTarget, cur: TargetObservation, cfg: RescanConfig): Triple<TargetState, TransitionReason, Double?> {
        val p = t.last
        return when (t.kind) {
            TargetKind.WALL_ENDPOINT, TargetKind.WALL_SEGMENT -> {
                val a = p.unobservedM; val b = cur.unobservedM
                when {
                    a == null || b == null -> Triple(TargetState.PERSISTENT, TransitionReason.UNOBSERVED_NOT_MEASURABLE, null)
                    a - b >= cfg.minUnobservedM -> Triple(TargetState.IMPROVED, TransitionReason.UNOBSERVED_LENGTH_REDUCED, a - b)
                    else -> Triple(TargetState.PERSISTENT, TransitionReason.NO_MEASURABLE_IMPROVEMENT, a - b)
                }
            }
            TargetKind.PERIMETER -> {
                val ra = perimeterRank[p.perimeterState] ?: 0; val rb = perimeterRank[cur.perimeterState] ?: 0
                val a = p.missingSideM; val b = cur.missingSideM
                when {
                    rb > ra -> Triple(TargetState.IMPROVED, TransitionReason.PERIMETER_STATE_ADVANCED, null)
                    rb == ra && a != null && b != null && a - b >= cfg.minUnobservedM -> Triple(TargetState.IMPROVED, TransitionReason.MISSING_SIDE_REDUCED, a - b)
                    rb == ra && (a == null || b == null) -> Triple(TargetState.PERSISTENT, TransitionReason.UNOBSERVED_NOT_MEASURABLE, null)
                    else -> Triple(TargetState.PERSISTENT, TransitionReason.NO_MEASURABLE_IMPROVEMENT, if (a != null && b != null) a - b else null)
                }
            }
            else -> Triple(TargetState.PERSISTENT, TransitionReason.NO_MEASURABLE_IMPROVEMENT, null)
        }
    }

    /** Transizioni ammesse (tutte le altre sono rifiutate). */
    val ALLOWED: Set<Pair<TargetState?, TargetState>> = setOf(
        null to TargetState.NEW, TargetState.NEW to TargetState.PROPOSED,
        TargetState.PROPOSED to TargetState.ATTEMPTED, TargetState.IMPROVED to TargetState.ATTEMPTED, TargetState.PERSISTENT to TargetState.ATTEMPTED,
        TargetState.ATTEMPTED to TargetState.IMPROVED, TargetState.ATTEMPTED to TargetState.RESOLVED, TargetState.ATTEMPTED to TargetState.PERSISTENT,
        TargetState.RESOLVED to TargetState.NEW,
    )

    /**
     * Verifica che ogni transizione registrata sia ammessa (usata dai test e dalla CLI prima di scrivere). M5.1.2: anche alias aciclici
     * e unici, nessun RESOLVED con un candidato ancora aperto, AMBIGUOUS_CANDIDATES solo PERSISTENT, collegamenti mai verso alias.
     */
    fun validate(s: RescanSession): List<String> {
        val out = s.targets.flatMap { t ->
            val bad = t.history.filter { (it.from to it.to) !in ALLOWED }.map { "${t.key}: ${it.from} → ${it.to} non ammessa" }
            val chain = t.history.zipWithNext().filter { (a, b) -> a.to != b.from }.map { (a, b) -> "${t.key}: storia spezzata ${a.to} / ${b.from}" }
            val proof = t.history.filter { it.to == TargetState.RESOLVED && it.candidates.any { c -> c.verdict == Verdict.OPEN } }.map { "${t.key}: RESOLVED con un candidato aperto (scansione ${it.scanIndex})" }
            val amb = t.history.filter { it.reason == TransitionReason.AMBIGUOUS_CANDIDATES && it.to != TargetState.PERSISTENT }.map { "${t.key}: AMBIGUOUS_CANDIDATES non PERSISTENT" }
            bad + chain + proof + amb
        }.toMutableList()
        for (a in s.wallAliases) if (wallNumber(a.absorbedKey) <= wallNumber(a.canonicalKey)) out.add("alias ${a.absorbedKey} → ${a.canonicalKey}: non verso un numero minore")
        s.wallAliases.groupBy { it.absorbedKey }.filter { it.value.size > 1 }.keys.forEach { out.add("alias multiplo per $it") }
        val absorbed = s.wallAliases.map { it.absorbedKey }.toSet()
        for (l in s.targetLinks) if (l.newTargetKey in absorbed || l.historicalTargetKey in absorbed || s.targets.none { it.key == l.newTargetKey }) out.add("collegamento ${l.newTargetKey} → ${l.historicalTargetKey} non valido")
        return out
    }

    /** M5.1.2 — invarianti tra due sessioni consecutive: storia prefisso esatto, nessun bersaglio, parete, alias, relazione, alternativa o collegamento perso. */
    fun validate(previous: RescanSession?, next: RescanSession): List<String> {
        val out = validate(next).toMutableList()
        if (previous == null) return out
        val nt = next.targets.associateBy { it.key }
        for (t in previous.targets) {
            val n = nt[t.key]
            if (n == null) out.add("${t.key}: bersaglio scomparso")
            else if (n.history.take(t.history.size) != t.history) out.add("${t.key}: storia precedente alterata")
        }
        val nw = next.walls.map { it.key }.toSet()
        previous.walls.filter { it.key !in nw }.forEach { out.add("${it.key}: parete scomparsa") }
        if (!next.wallAliases.containsAll(previous.wallAliases)) out.add("alias precedenti persi")
        if (!next.wallRelations.containsAll(previous.wallRelations)) out.add("relazioni precedenti perse")
        if (!next.targetLinks.containsAll(previous.targetLinks)) out.add("collegamenti precedenti persi")
        val na = next.targetAlternatives.map { it.targetKey to it.altKey }.toSet()
        previous.targetAlternatives.filter { (it.targetKey to it.altKey) !in na }.forEach { out.add("alternativa ${it.targetKey}/${it.altKey} persa") }
        if (next.scans.take(previous.scans.size) != previous.scans) out.add("scansioni precedenti alterate")
        return out
    }
}
