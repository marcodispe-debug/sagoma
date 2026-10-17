package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.scan.ArXZ
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/** Differenze di angolo senza salti a 0° / 180° / 360°. */
object SketchAngles {
    /** Tra due DIREZIONI di retta (senza verso): risultato in [0°, 90°]. */
    fun lineDeltaDeg(a: Double, b: Double): Double {
        val d = ((abs(a - b) % 180.0) + 180.0) % 180.0
        return min(d, 180.0 - d)
    }

    /** Tra due angoli orientati (per esempio angoli interni): risultato in [0°, 180°]. */
    fun angleDeltaDeg(a: Double, b: Double): Double = abs(wrap180(a - b))

    /** Angolo riportato in (−180°, 180°]. */
    fun wrap180(x: Double): Double {
        val r = ((x % 360.0) + 540.0) % 360.0 - 180.0
        return if (r == -180.0) 180.0 else r
    }

    fun headingDeg(d: ArXZ): Double = toDegrees(atan2(d.z, d.x))
}

/**
 * Il calcolo della pianta del percorso B. Gerarchia delle fonti (mai mediate):
 *  1. rette confermate: fisse; l'angolo tra due lati confermati consecutivi è l'incrocio delle loro rette;
 *  2. lunghezze a mano: fisse;
 *  3. schizzo: dà la direzione dei lati non scansionati (ruotata come le pareti confermate) e, SOLO se l'utente lo accetta,
 *     una lunghezza dove manca una misura. Tutto ciò che ne dipende è marcato come stima.
 * I lati non scansionati tra due pareti confermate si risolvono esattamente (sistema lineare): servono tante lunghezze quanti
 * sono i lati mancanti; le misure in più non si usano per il calcolo ma per controllarlo.
 */
object SketchSolver {

    private class Line(val p: ArXZ, val d: ArXZ, val s0: Double, val s1: Double, val src: ConfirmedLine) {
        fun at(t: Double) = p + d * t
        fun along(q: ArXZ) = (q - p) dot d
    }

    /**
     * Quanto la parte vista supera gli angoli [c0] e [c1] (posizioni lungo la retta). Il tratto `a`–`b` viene dal poligono di
     * ARCore, che si gonfia quando ARCore fonde piani diversi (registrazione reale scan4: 40 cm oltre l'angolo con 0 punti lì,
     * contro 163 attesi alla densità della stessa parete). Criterio RELATIVO, indipendente da quanti punti ha la parete (tempo di
     * scansione, trama, lunghezza): il tratto oltre un angolo è un artefatto solo se ha meno di
     * [SketchThresholds.overshootDensityRatio] dei punti attesi alla densità che la parete ha tra gli angoli, e solo se di punti
     * se ne attendono almeno [SketchThresholds.overshootMinPoints]. Altrimenti (o senza punti) conta il tratto, come prima: mai
     * più blocchi di prima, e nel dubbio si blocca. Restituisce il superamento verso l'angolo [c0] (inizio del lato) e verso [c1].
     */
    private fun overshootEnds(l: Line, c0: Double, c1: Double, th: SketchThresholds): Pair<Double, Double> {
        val polyLow = c0 - l.s0
        val polyHigh = l.s1 - c1
        val support = l.src.supportAlongM ?: return polyLow to polyHigh
        // Dalla retta `a`→`b` al verso della retta del nucleo (stessa origine `a`, verso eventualmente opposto).
        val verse = if ((l.d dot (l.src.b - l.src.a)) >= 0) 1.0 else -1.0
        val along = support.map { it * verse }
        // Densità della parete nel tratto visto tra i due angoli.
        val inLo = max(l.s0, c0); val inHi = min(l.s1, c1)
        val inside = along.count { it in inLo..inHi }
        if (inHi - inLo < MIN_DENSITY_SPAN_M || inside < th.overshootMinPoints) return polyLow to polyHigh
        val density = inside / (inHi - inLo)
        fun end(poly: Double, beyond: Int): Double {
            if (poly <= 0.0) return poly
            val expected = density * poly
            if (expected < th.overshootMinPoints) return poly // troppo poco da attendersi: non si può dire
            return if (beyond < th.overshootDensityRatio * expected) 0.0 else poly
        }
        return end(polyLow, along.count { it < c0 }) to end(polyHigh, along.count { it > c1 })
    }

    /** Tratto minimo tra gli angoli su cui stimare la densità dei punti (sotto, la densità non è affidabile). */
    private const val MIN_DENSITY_SPAN_M = 0.3

    private class Constraint(val priority: Int, val source: MeasureSource, val side: Int, val row: DoubleArray, val rhs: Double)

    private class Issues(private val answers: Map<String, Answer>) {
        val list = ArrayList<GeometryIssue>()
        fun add(code: IssueCode, sides: List<Int>, values: Map<String, Double> = emptyMap()) {
            val s = sides.distinct().sorted()
            if (list.any { it.code == code && it.sides == s }) return
            // Una risposta risolve SOLO se il codice la ammette (le domande): blocchi e avvisi non ne ammettono nessuna.
            val answer = answers[GeometryIssue.keyOf(code, s)]?.takeIf { it in code.allowedAnswers }
            list += GeometryIssue(code, s, values, answer)
        }
        val blocking get() = list.any { it.severity == Severity.BLOCKING }

        /** Le risposte che non hanno risolto niente, con il motivo: si riportano, non si ignorano in silenzio. */
        fun rejected(): List<RejectedAnswer> = answers.mapNotNull { (key, a) ->
            val issue = list.firstOrNull { it.key == key }
            when {
                issue == null -> RejectedAnswer(key, a, RejectionReason.NO_SUCH_ISSUE)
                a !in issue.code.allowedAnswers -> RejectedAnswer(key, a, if (issue.code.allowedAnswers.isEmpty()) RejectionReason.NEEDS_DATA_CHANGE else RejectionReason.NOT_ALLOWED)
                else -> null
            }
        }
    }

    fun solve(input: SketchGeometryInput): SketchGeometry {
        val th = input.thresholds
        val sk = input.sketch
        val n = sk.sideCount
        val issues = Issues(input.answers)
        fun blocked(rot: Double? = null) = SketchGeometry(null, emptyList(), emptyList(), issues.list.toList(), rot, issues.rejected())

        // ---------- Ingressi ----------
        val badInput = n < 3 || sk.corners.any { !it.isFinite() } ||
            input.confirmed.keys.any { it !in 0 until n } || input.manualLengthsM.keys.any { it !in 0 until n } ||
            input.acceptedSketchLengths.any { it !in 0 until n } ||
            input.manualLengthsM.values.any { !it.isFinite() || it <= 0.0 } ||
            input.confirmed.values.any { c ->
                !c.a.isFinite() || !c.b.isFinite() || !c.inward.isFinite() || !c.sigmaOffsetM.isFinite() || !c.sigmaDirectionDeg.isFinite() ||
                    c.sigmaOffsetM < 0 || c.sigmaDirectionDeg < 0 || c.observedLengthM < 1e-6 || c.inward.length < 1e-9
            }
        if (badInput) { issues.add(IssueCode.INVALID_INPUT, emptyList()); return blocked() }
        if (!sketchValid(sk, th)) { issues.add(IssueCode.SKETCH_INVALID, emptyList()); return blocked() }
        if (input.confirmed.isEmpty()) { issues.add(IssueCode.NO_CONFIRMED_WALL, emptyList()); return blocked() }

        // Verso del giro dello schizzo: l'interno sta a sinistra (antiorario) o a destra (orario) di ogni lato.
        val orient = sign(sk.signedArea)

        // ---------- Rette confermate, con il verso del lato dello schizzo ----------
        // Il verso NON viene dall'ordine degli estremi a → b (una convenzione): viene dal lato interno, cioè da dove stava il
        // telefono alla conferma. È un dato fisico, quindi uno scarto oltre 90° dallo schizzo è un vero conflitto (schizzo
        // speculare o parete sul lato sbagliato). Se il lato interno NON è noto ([ConfirmedLine.inwardKnown] falso: il motore
        // ha usato una normale di convenzione) il verso non è un dato: lo si prende dallo schizzo, e quella parete non può
        // generare il conflitto né il controllo del lato interno.
        val lines = HashMap<Int, Line>()
        val unknownInward = ArrayList<Int>()
        for ((k, c) in input.confirmed) {
            val u = (c.b - c.a).normalized()
            if (!c.inwardKnown) { unknownInward += k; lines[k] = Line(c.a, u, 0.0, (c.b - c.a) dot u, c); continue }
            val inward = c.inward.normalized()
            val wanted = if (orient > 0) ArXZ(inward.z, -inward.x) else ArXZ(-inward.z, inward.x)
            val dot = u dot wanted
            // Il lato interno deve essere perpendicolare alla parete (entro 60°): se no il dato è incoerente.
            if (abs(dot) < 0.5) { issues.add(IssueCode.INVALID_INPUT, listOf(k)); continue }
            val d = if (dot >= 0) u else u * -1.0
            val e = (c.b - c.a) dot d
            lines[k] = Line(c.a, d, min(0.0, e), max(0.0, e), c)
        }
        if (issues.blocking) return blocked()
        if (unknownInward.size == lines.size) {
            // Nessuna parete con il lato interno noto: il verso della stanza non si può stabilire senza inventarlo.
            issues.add(IssueCode.INWARD_UNKNOWN_ALL, unknownInward)
            return blocked()
        }

        // ---------- Rotazione schizzo → mondo (solo per prevedere direzioni e controllare), dalle pareti con verso noto ----------
        val known = lines.keys.filter { it !in unknownInward }.sorted()
        val rhos = known.map { k -> k to SketchAngles.wrap180(SketchAngles.headingDeg(lines.getValue(k).d) - SketchAngles.headingDeg(sk.sideDirection(k))) }
        // Medoide circolare: il valore che minimizza la somma degli scarti (robusto a una parete sbagliata, nessuna media).
        val rot = rhos.minBy { (_, r) -> rhos.sumOf { (_, o) -> abs(SketchAngles.wrap180(o - r)) } }.second
        // Pareti con lato interno non noto: verso dallo schizzo ruotato; si controlla solo la direzione della retta (≤ 90°).
        for (k in unknownInward) {
            val l = lines.getValue(k)
            val expected = sk.sideDirection(k).rotatedDeg(rot)
            val d = if ((l.d dot expected) >= 0) l.d else l.d * -1.0
            val e = (l.src.b - l.src.a) dot d
            lines[k] = Line(l.src.a, d, min(0.0, e), max(0.0, e), l.src)
            val dev = SketchAngles.lineDeltaDeg(SketchAngles.headingDeg(d), SketchAngles.headingDeg(expected))
            if (dev > th.sketchAngleDeg) issues.add(IssueCode.SKETCH_DIRECTION_MISMATCH, listOf(k), mapOf("scartoDeg" to dev))
            issues.add(IssueCode.INWARD_UNKNOWN, listOf(k))
        }

        // Doppioni: la stessa parete confermata per due lati.
        val keys = lines.keys.sorted()
        for (i in keys.indices) for (j in i + 1 until keys.size) {
            val a = lines.getValue(keys[i]); val b = lines.getValue(keys[j])
            val ang = SketchAngles.lineDeltaDeg(SketchAngles.headingDeg(a.d), SketchAngles.headingDeg(b.d))
            val mid = (b.at(b.s0) + b.at(b.s1)) * 0.5
            val off = abs((mid - a.p).cross(a.d))
            if (ang <= th.duplicateAngleDeg && off <= th.duplicateOffsetM) issues.add(IssueCode.DUPLICATE_WALL, listOf(keys[i], keys[j]), mapOf("angoloDeg" to ang, "distanzaM" to off))
        }

        if (known.size >= 2) for ((k, r) in rhos) {
            val dev = abs(SketchAngles.wrap180(r - rot))
            // Oltre 90°: la parete va al contrario dello schizzo (schizzo speculare o parete associata al lato sbagliato). Si
            // blocca: lo risolve solo correggere lo schizzo o l'associazione, mai una risposta né una correzione automatica.
            if (dev > 90.0) issues.add(IssueCode.SKETCH_DIRECTION_CONFLICT, listOf(k), mapOf("scartoDeg" to dev))
            else if (dev > th.sketchAngleDeg) issues.add(IssueCode.SKETCH_DIRECTION_MISMATCH, listOf(k), mapOf("scartoDeg" to dev))
        }
        val dir = List(n) { k -> lines[k]?.d ?: sk.sideDirection(k).rotatedDeg(rot) }

        // ---------- Lati consecutivi paralleli o quasi ----------
        val minSin = kotlin.math.sin(th.minCornerAngleDeg * kotlin.math.PI / 180.0)
        for (k in 0 until n) {
            val s = abs(dir[k].cross(dir[mod(k + 1, n)]))
            val ang = toDegrees(kotlin.math.asin(min(1.0, s)))
            when {
                s < th.parallelEpsilon -> issues.add(IssueCode.PARALLEL_NO_INTERSECTION, listOf(k, mod(k + 1, n)))
                s < minSin -> issues.add(IssueCode.NEARLY_PARALLEL, listOf(k, mod(k + 1, n)), mapOf("angoloDeg" to ang))
            }
        }
        if (issues.blocking) return blocked(rot)

        // ---------- Angoli tra due lati confermati: incrocio delle rette ----------
        val vertex = arrayOfNulls<ArXZ>(n) // vertex[k] = inizio del lato k
        val vertexSigma = arrayOfNulls<Double>(n)
        // Angoli che vengono SOLO da due rette confermate: gli unici usabili come estremo noto per le lunghezze di A e B.
        val lineVertex = BooleanArray(n)
        for (k in 0 until n) {
            val a = lines[mod(k - 1, n)] ?: continue
            val b = lines[k] ?: continue
            val v = intersect(a, b) ?: run { issues.add(IssueCode.PARALLEL_NO_INTERSECTION, listOf(mod(k - 1, n), k)); null } ?: continue
            vertex[k] = v
            lineVertex[k] = true
            val sinPhi = abs(a.d.cross(b.d))
            fun lever(l: Line) = max(0.0, max(l.s0 - l.along(v), l.along(v) - l.s1))
            fun rad(deg: Double) = deg * kotlin.math.PI / 180.0
            val va = a.src.sigmaOffsetM * a.src.sigmaOffsetM + sq(lever(a) * rad(a.src.sigmaDirectionDeg))
            val vb = b.src.sigmaOffsetM * b.src.sigmaOffsetM + sq(lever(b) * rad(b.src.sigmaDirectionDeg))
            vertexSigma[k] = sqrt(va + vb) / sinPhi
        }
        if (issues.blocking) return blocked(rot)

        // ---------- Lati non scansionati: sistema lineare tra due pareti confermate ----------
        // Con una sola parete confermata: il suo indice (la posizione lungo di lei è per convenzione, non misurata).
        var conventionSide: Int? = null
        val lengthSources = Array(n) { mutableSetOf<MeasureSource>() }
        for (k in keys) lengthSources[k] += MeasureSource.SCAN_CONFIRMED
        val usedConstraint = HashSet<Pair<Int, MeasureSource>>() // misure usate per il calcolo (le altre servono da controllo)
        for ((ia, aIdx) in keys.withIndex()) {
            val bIdx = keys[(ia + 1) % keys.size]
            val r = if (bIdx == aIdx) n - 1 else mod(bIdx - aIdx - 1, n)
            if (r == 0) continue
            val run = List(r) { mod(aIdx + 1 + it, n) }
            val la = lines.getValue(aIdx); val lb = lines.getValue(bIdx)
            val m = r + 2 // incognite: t (fine di A), r lunghezze, s (inizio di B)
            val rows = ArrayList<DoubleArray>(); val rhs = ArrayList<Double>()
            for (axis in 0..1) {
                val row = DoubleArray(m)
                fun c(v: ArXZ) = if (axis == 0) v.x else v.z
                row[0] = c(la.d)
                for (i in 0 until r) row[1 + i] = c(dir[run[i]])
                row[m - 1] = -c(lb.d)
                rows += row; rhs += c(lb.p) - c(la.p)
            }
            // Vincoli di lunghezza disponibili, in ordine di priorità: a mano sui lati mancanti, a mano su A/B, schizzo accettato.
            val cand = ArrayList<Constraint>()
            fun own(i: Int, len: Double, src: MeasureSource, prio: Int) {
                val row = DoubleArray(m); row[1 + i] = 1.0; cand += Constraint(prio, src, run[i], row, len)
            }
            for (i in 0 until r) {
                input.manualLengthsM[run[i]]?.let { own(i, it, MeasureSource.MANUAL, 0) }
                if (run[i] in input.acceptedSketchLengths) own(i, sk.sideLength(run[i]), MeasureSource.SKETCH_ESTIMATE, 2)
            }
            fun sideA(len: Double, src: MeasureSource, prio: Int) {
                val row = DoubleArray(m)
                if (bIdx == aIdx) { row[0] = 1.0; row[m - 1] = -1.0; cand += Constraint(prio, src, aIdx, row, len); return }
                val start = vertex[aIdx]?.takeIf { lineVertex[aIdx] } ?: return // inizio di A noto solo se il lato prima di A è confermato
                row[0] = 1.0; cand += Constraint(prio, src, aIdx, row, len + la.along(start))
            }
            fun sideB(len: Double, src: MeasureSource, prio: Int) {
                if (bIdx == aIdx) return // stesso lato di A: già considerato
                val end = vertex[mod(bIdx + 1, n)]?.takeIf { lineVertex[mod(bIdx + 1, n)] } ?: return
                val row = DoubleArray(m); row[m - 1] = -1.0; cand += Constraint(prio, src, bIdx, row, len - lb.along(end))
            }
            input.manualLengthsM[aIdx]?.let { sideA(it, MeasureSource.MANUAL, 1) }
            input.manualLengthsM[bIdx]?.let { sideB(it, MeasureSource.MANUAL, 1) }
            if (aIdx in input.acceptedSketchLengths) sideA(sk.sideLength(aIdx), MeasureSource.SKETCH_ESTIMATE, 3)
            if (bIdx in input.acceptedSketchLengths) sideB(sk.sideLength(bIdx), MeasureSource.SKETCH_ESTIMATE, 3)
            // Con UNA sola parete confermata (A = B) la posizione lungo la sua retta non è misurabile: conta solo la forma.
            // Convenzione dichiarata: il lato A si centra sulla parte vista (riga fissa, non una misura). Servono r − 1 lunghezze.
            if (bIdx == aIdx) {
                val row = DoubleArray(m); row[0] = 1.0; row[m - 1] = 1.0
                rows += row; rhs += la.s0 + la.s1
                conventionSide = aIdx
            }
            val needed = if (bIdx == aIdx) r - 1 else r
            val sorted = cand.sortedWith(compareBy({ it.priority }, { it.side }))
            // Si cercano, in ordine di priorità, le misure che FISSANO davvero la stanza: una lunghezza può non bastare (per
            // esempio la lunghezza del lato mancante tra due lati paralleli non dice dove sta). Nessuna combinazione → mancano misure.
            var chosen: List<Constraint>? = null
            var x: DoubleArray? = null
            var unstable = false
            for (combo in combinations(sorted, needed)) {
                val rr = (rows + combo.map { it.row }).toTypedArray()
                val bb = (rhs + combo.map { it.rhs }).toDoubleArray()
                val sol = solveLinear(rr, bb, th.pivotEpsilon) ?: continue
                if (solveLinear(rr, bb, th.unstablePivot) == null) { unstable = true; continue } // risolvibile solo per poco
                chosen = combo; x = sol; break
            }
            if (chosen == null || x == null || x.any { !it.isFinite() }) {
                if (unstable) issues.add(IssueCode.UNSTABLE_SOLUTION, run + listOf(aIdx, bIdx))
                else issues.add(IssueCode.UNDERDETERMINED, run + listOf(aIdx, bIdx), mapOf("misureMancanti" to max(1, needed - sorted.size).toDouble()))
                continue
            }
            for (cst in chosen) usedConstraint += cst.side to cst.source
            vertex[mod(aIdx + 1, n)] = la.at(x[0])
            var v = la.at(x[0])
            for (i in 0 until r - 1) { v = v + dir[run[i]] * x[1 + i]; vertex[run[i + 1]] = v }
            vertex[bIdx] = lb.at(x[m - 1])
            val src = chosen.map { it.source }.toSet() + MeasureSource.SCAN_CONFIRMED
            for (k in run + listOf(aIdx, bIdx)) lengthSources[k] += src
        }
        if (issues.blocking) return blocked(rot)
        val corners = vertex.map { it!! }
        if (corners.any { !it.isFinite() }) { issues.add(IssueCode.NUMERIC, emptyList()); return blocked(rot) }

        // ---------- Controlli sul poligono ----------
        val center = keys.map { k -> lines.getValue(k).let { (it.at(it.s0) + it.at(it.s1)) * 0.5 } }.let { ms -> ms.reduce { a, b -> a + b } * (1.0 / ms.size) }
        val far = corners.indices.filter { corners[it].distanceTo(center) > th.maxVertexDistanceM }
        val diag = diagonal(corners); val skDiag = diagonal(sk.corners)
        if (far.isNotEmpty() || diag > th.maxScaleVsSketch * skDiag) {
            issues.add(IssueCode.VERTEX_OUT_OF_SCALE, far.flatMap { listOf(mod(it - 1, n), it) }, mapOf("diagonaleM" to diag, "diagonaleSchizzoM" to skDiag))
        }
        val lengths = List(n) { k -> corners[k].distanceTo(corners[mod(k + 1, n)]) }
        for (k in 0 until n) {
            val along = (corners[mod(k + 1, n)] - corners[k]) dot dir[k]
            if (along < 0) issues.add(IssueCode.SIDE_REVERSED, listOf(k), mapOf("lunghezzaM" to along))
            else if (lengths[k] < th.minSideM) issues.add(IssueCode.DEGENERATE_SIDE, listOf(k), mapOf("lunghezzaM" to lengths[k]))
        }
        val area = corners.indices.sumOf { corners[it].cross(corners[(it + 1) % n]) } / 2
        if (sign(area) != orient) issues.add(IssueCode.WRONG_ORIENTATION, emptyList(), mapOf("areaM2" to area))
        if (abs(area) < th.minAreaM2) issues.add(IssueCode.AREA_TOO_SMALL, emptyList(), mapOf("areaM2" to abs(area)))
        val angles = List(n) { k -> interiorAngle(corners, k, orient) }
        for (k in 0 until n) if (angles[k] < th.minInteriorAngleDeg || angles[k] > 360.0 - th.minInteriorAngleDeg) {
            issues.add(IssueCode.SPIKE_ANGLE, listOf(mod(k - 1, n), k), mapOf("angoloDeg" to angles[k]))
        }
        for (i in 0 until n) for (j in i + 2 until n) {
            if (i == 0 && j == n - 1) continue // lati adiacenti (si toccano nell'angolo 0)
            if (segmentDistance(corners[i], corners[mod(i + 1, n)], corners[j], corners[mod(j + 1, n)]) < 1e-3) issues.add(IssueCode.SELF_INTERSECTION, listOf(i, j))
        }
        // Pareti confermate: lato interno, parte vista oltre l'angolo, prolungamento.
        val prolongation = arrayOfNulls<Double>(n)
        for (k in keys) {
            val l = lines.getValue(k)
            val c0 = l.along(corners[k]); val c1 = l.along(corners[mod(k + 1, n)])
            val mid = (corners[k] + corners[mod(k + 1, n)]) * 0.5
            if (l.src.inwardKnown && lengths[k] >= th.minSideM && !insidePolygon(mid + l.src.inward.normalized() * 0.05, corners)) issues.add(IssueCode.INWARD_MISMATCH, listOf(k))
            val (overLow, overHigh) = overshootEnds(l, c0, c1, th)
            val overshoot = max(overLow, overHigh)
            if (overshoot > th.overshootTolM + 2 * l.src.sigmaOffsetM) {
                // Quale angolo: quello all'inizio del lato (k) o alla fine (k + 1). Con l'angolo misurato e quello dello schizzo,
                // la presentazione può dire quale parete rifare.
                val atStart = overLow >= overHigh
                val corner = if (atStart) k else mod(k + 1, n)
                val other = if (atStart) mod(k - 1, n) else mod(k + 1, n) // l'altro lato di quell'angolo
                issues.add(
                    IssueCode.OBSERVED_BEYOND_CORNER, listOf(k),
                    mapOf(
                        "oltreM" to overshoot, "angolo" to corner.toDouble(), "altroLato" to other.toDouble(),
                        "angoloDeg" to angles[corner], "schizzoDeg" to interiorAngle(sk.corners, corner, orient),
                    ),
                )
            }
            // Parete con posizione per convenzione: il prolungamento sarebbe solo un effetto del centraggio, non un dato.
            if (k == conventionSide) continue
            val pro = max(0.0, max(l.s0 - c0, c1 - l.s1))
            prolongation[k] = pro
            if (pro > th.prolongationAbsM || pro > th.prolongationRel * lengths[k]) issues.add(IssueCode.WALL_CONTINUES, listOf(k), mapOf("prolungamentoM" to pro))
        }
        // Incertezza degli angoli tra due pareti confermate.
        for (k in 0 until n) {
            val s = vertexSigma[k] ?: continue
            if (s > th.vertexSigmaBlockM) issues.add(IssueCode.VERTEX_UNSTABLE, listOf(mod(k - 1, n), k), mapOf("sigmaM" to s))
            else if (s > th.vertexSigmaWarnM) issues.add(IssueCode.VERTEX_UNCERTAIN, listOf(mod(k - 1, n), k), mapOf("sigmaM" to s))
        }
        // Misure a mano non usate nel calcolo: servono da controllo, MAI mediate.
        for ((k, len) in input.manualLengthsM) {
            if ((k to MeasureSource.MANUAL) in usedConstraint) continue
            val diff = abs(lengths[k] - len)
            if (diff > th.redundancyTolM) {
                val allScan = lengthSources[k] == setOf(MeasureSource.SCAN_CONFIRMED)
                issues.add(if (allScan) IssueCode.MANUAL_SCAN_CONFLICT else IssueCode.INCOMPATIBLE_MEASURES, listOf(k), mapOf("calcolataM" to lengths[k], "manoM" to len))
            } else lengthSources[k] += MeasureSource.MANUAL
        }
        // Confronto con lo schizzo (stime): domande, mai correzioni.
        for (k in 0 until n) {
            if (MeasureSource.SKETCH_ESTIMATE in lengthSources[k]) continue
            val skLen = sk.sideLength(k)
            val rel = abs(lengths[k] - skLen) / skLen
            if (rel > th.sketchLengthRel) issues.add(IssueCode.SKETCH_LENGTH_MISMATCH, listOf(k), mapOf("schizzoM" to skLen, "misurataM" to lengths[k], "scartoRel" to rel))
        }
        val skAngles = List(n) { k -> interiorAngle(sk.corners, k, orient) }
        for (k in 0 until n) {
            if (mod(k - 1, n) !in lines || k !in lines) continue
            val d = SketchAngles.angleDeltaDeg(angles[k], skAngles[k])
            if (d > th.sketchAngleDeg) issues.add(IssueCode.SKETCH_ANGLE_MISMATCH, listOf(mod(k - 1, n), k), mapOf("schizzoDeg" to skAngles[k], "misuratoDeg" to angles[k]))
        }
        // Schizzo ruotato e centrato sulla stanza calcolata: angoli molto lontani dalla posizione prevista.
        val skRot = sk.corners.map { it.rotatedDeg(rot) }
        val shift = centroid(corners) - centroid(skRot)
        val farLimit = max(th.sketchVertexAbsM, th.sketchVertexRel * skDiag)
        for (k in 0 until n) {
            val expected = skRot[k] + shift
            val d = corners[k].distanceTo(expected)
            // Entrambe le posizioni nei valori: quella prevista dallo schizzo e quella calcolata, nello stesso sistema.
            if (d > farLimit) issues.add(
                IssueCode.SKETCH_VERTEX_FAR, listOf(mod(k - 1, n), k),
                mapOf("distanzaM" to d, "limiteM" to farLimit, "schizzoX" to expected.x, "schizzoZ" to expected.z, "calcolatoX" to corners[k].x, "calcolatoZ" to corners[k].z),
            )
        }
        val sides = List(n) { k ->
            SideResult(k, lengths[k], sk.sideLength(k), lengthSources[k].toSet(), if (k in lines) MeasureSource.SCAN_CONFIRMED else MeasureSource.SKETCH_ESTIMATE, prolongation[k])
        }
        val estimated = sides.filter { it.isEstimate }.map { it.index }
        if (estimated.isNotEmpty()) issues.add(IssueCode.SKETCH_ESTIMATE_USED, estimated)
        conventionSide?.let { issues.add(IssueCode.POSITION_BY_CONVENTION, listOf(it)) }
        val conventional = conventionSide?.let { setOf(it, mod(it + 1, n)) } ?: emptySet()
        val cornerResults = List(n) { k -> CornerResult(k, corners[k], angles[k], vertexSigma[k], k in conventional) }
        return SketchGeometry(corners, sides, cornerResults, issues.list.toList(), rot, issues.rejected())
    }

    // ---------- Geometria di base ----------

    /** Combinazioni di `k` elementi, nell'ordine della lista (le prime usano gli elementi più prioritari). */
    private fun <T> combinations(items: List<T>, k: Int): Sequence<List<T>> = sequence {
        if (k == 0) { yield(emptyList()); return@sequence }
        if (k > items.size) return@sequence
        val idx = IntArray(k) { it }
        while (true) {
            yield(idx.map { items[it] })
            var i = k - 1
            while (i >= 0 && idx[i] == items.size - k + i) i--
            if (i < 0) return@sequence
            idx[i]++
            for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
        }
    }

    private fun sq(v: Double) = v * v

    private fun intersect(a: Line, b: Line): ArXZ? {
        val den = a.d.cross(b.d)
        if (abs(den) < 1e-12) return null
        val t = (b.p - a.p).cross(b.d) / den
        return a.at(t)
    }

    /** Eliminazione di Gauss con pivot parziale; null se un pivot è troppo piccolo rispetto alla scala della matrice. */
    internal fun solveLinear(a: Array<DoubleArray>, b: DoubleArray, eps: Double): DoubleArray? {
        val n = b.size
        if (a.size != n || a.any { it.size != n }) return null
        val m = Array(n) { a[it].copyOf() }
        val v = b.copyOf()
        val scale = m.maxOf { row -> row.maxOf { abs(it) } }.takeIf { it > 0 } ?: return null
        for (col in 0 until n) {
            val p = (col until n).maxBy { abs(m[it][col]) }
            if (abs(m[p][col]) < eps * scale) return null
            if (p != col) { val t = m[p]; m[p] = m[col]; m[col] = t; val tv = v[p]; v[p] = v[col]; v[col] = tv }
            for (r in col + 1 until n) {
                val f = m[r][col] / m[col][col]
                if (f == 0.0) continue
                for (c in col until n) m[r][c] -= f * m[col][c]
                v[r] -= f * v[col]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = v[r]
            for (c in r + 1 until n) s -= m[r][c] * x[c]
            x[r] = s / m[r][r]
        }
        return x
    }

    /** Angolo interno in `c[k]` (tra il lato k−1 e il lato k), per un poligono che gira con verso `orient`. */
    private fun interiorAngle(c: List<ArXZ>, k: Int, orient: Double): Double {
        val n = c.size
        val e1 = c[k] - c[mod(k - 1, n)]
        val e2 = c[mod(k + 1, n)] - c[k]
        val turn = toDegrees(atan2(e1.cross(e2), e1 dot e2))
        return 180.0 - orient * turn
    }

    private fun centroid(c: List<ArXZ>) = c.reduce { a, b -> a + b } * (1.0 / c.size)

    private fun diagonal(c: List<ArXZ>): Double {
        val dx = c.maxOf { it.x } - c.minOf { it.x }; val dz = c.maxOf { it.z } - c.minOf { it.z }
        return sqrt(dx * dx + dz * dz)
    }

    private fun insidePolygon(p: ArXZ, c: List<ArXZ>): Boolean {
        var inside = false
        var j = c.size - 1
        for (i in c.indices) {
            val a = c[i]; val b = c[j]
            if ((a.z > p.z) != (b.z > p.z) && p.x < (b.x - a.x) * (p.z - a.z) / (b.z - a.z) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    private fun pointSegment(p: ArXZ, a: ArXZ, b: ArXZ): Double {
        val ab = b - a
        val l2 = ab dot ab
        if (l2 < 1e-18) return p.distanceTo(a)
        val t = (((p - a) dot ab) / l2).coerceIn(0.0, 1.0)
        return p.distanceTo(a + ab * t)
    }

    private fun segmentDistance(a: ArXZ, b: ArXZ, c: ArXZ, d: ArXZ): Double {
        val d1 = (b - a).cross(c - a); val d2 = (b - a).cross(d - a)
        val d3 = (d - c).cross(a - c); val d4 = (d - c).cross(b - c)
        if (d1 * d2 < 0 && d3 * d4 < 0) return 0.0
        return min(min(pointSegment(a, c, d), pointSegment(b, c, d)), min(pointSegment(c, a, b), pointSegment(d, a, b)))
    }

    private fun sketchValid(sk: RoomSketch, th: SketchThresholds): Boolean {
        val n = sk.sideCount
        if ((0 until n).any { sk.sideLength(it) < 1e-6 }) return false
        if (abs(sk.signedArea) < 1e-6) return false
        for (i in 0 until n) for (j in i + 2 until n) {
            if (i == 0 && j == n - 1) continue
            if (segmentDistance(sk.start(i), sk.end(i), sk.start(j), sk.end(j)) < 1e-6) return false
        }
        return true
    }
}
