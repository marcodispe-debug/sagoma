package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.geometry.formatDecimal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * R4 — Perimeter Solver. Ingresso: le pareti R3 così come sono. Uscita: quali segmenti appartengono allo stesso perimetro, con
 * stato CLOSED / PARTIAL / OPEN / UNCERTAIN. Regole:
 *  1. Nessuna parete inventata: un perimetro contiene solo pareti R3; ciò che manca è un "lato mancante" con il motivo e, a parte,
 *     un'ipotesi INFERRED / NOT OBSERVED.
 *  2. Nessun angolo, parallelismo, lunghezza o rettangolarità assunti: gli angoli sono le intersezioni delle linee misurate.
 *  3. Collegamento (R4.1): punteggio = prodotto di componenti esplicite (geometria in unità di σ, frazione osservata, stato delle
 *     estremità, spazio libero, qualità dell'evidenza, evidenza R2 vicina). Le contraddizioni (estremità osservata lontana
 *     dall'angolo oltre 3σ, parete che prosegue oltre l'angolo, prolungamento attraverso spazio visto libero, linee antiparallele)
 *     rifiutano il collegamento con il motivo. Si accetta solo il migliore reciproco, e solo se nessuna alternativa ha almeno
 *     1/[PerimeterParams.ambiguityRatio] del suo punteggio (altrimenti: AMBIGUOUS, nessuna scelta).
 *  4. Frammenti della stessa parete (R4.2): solo con direzione e scarto laterale compatibili con le σ e senza un angolo osservato
 *     in mezzo; il gap resta un tratto non osservato con il motivo.
 *  5. Pareti oltre un'apertura (R4.3): una parete che da nessuna posizione della camera davanti a lei si vede senza attraversare il
 *     contorno (tratti osservati, prolungamenti e gap dei collegamenti accettati) è fuori dal perimetro, come candidata ambigua.
 *  6. CLOSED (R4.5) solo se: anello di almeno 3 angoli, tutti i collegamenti accettati e osservati (R4.1: angolo osservato da ENTRAMBE
 *     le estremità — OBSERVED entro la σ di misura, oppure dati fino all'intersezione; né la σ terminale né quella dell'intersezione
 *     rendono osservato un tratto mancante), estremità non instabili, nessun
 *     gap NON osservato, χ² degli scarti estremità↔angoli compatibile con le σ propagate, nessuna alternativa altrettanto
 *     plausibile, poligono semplice percorso con l'interno a destra. Altrimenti UNCERTAIN, con i motivi.
 * Deterministico: pareti in ordine di id, nessuna dipendenza dall'ordine di ingresso.
 */

/** Traccia in pianta di una superficie verticale R2 non diventata parete (evidenza vicina per R4). Normale verso le camere. */
class R2Trace(val surfaceId: Int, val kind: SurfaceKind, val ax: Double, val az: Double, val bx: Double, val bz: Double, val nx: Double, val nz: Double)

class PerimeterInput(
    val walls: List<EstimatedWall>,
    /** Spazio 3D (1 libero, 2 occupato, 0 ignoto, −1 fuori), come in R3. */
    val space: SpaceQuery,
    /** Lato della cella dello spazio libero (m): passo di campionamento e mezzo-cella dietro la linea. */
    val spaceCellM: Double,
    /** Posizioni della camera in pianta (x, z). */
    val cameras: List<DoubleArray>,
    val r2: List<R2Trace> = emptyList(),
)

data class PerimeterParams(
    /** Compatibilità geometrica: scarti entro [maxZ] σ propagate. */
    val maxZ: Double = 3.0,
    /** Un'alternativa con punteggio ≥ migliore / [ambiguityRatio] è "altrettanto plausibile". */
    val ambiguityRatio: Double = 3.0,
    /** Punteggio minimo per accettare un collegamento (stesso livello 3σ del test di chiusura: exp(−χ²/2) con 2 g.d.l.). */
    val minLinkScore: Double = 0.0027,
    /** Probabilità minima del test di chiusura (equivalente a 3σ). */
    val closureMinP: Double = 0.0027,
    /** Fattori di stato delle estremità. */
    val uncertainEndFactor: Double = 0.7,
    val freeBeyondEndFactor: Double = 0.5,
    /** Fattore per un tratto non osservato senza nessuna evidenza R2 che lo spieghi. */
    val unexplainedExtensionFactor: Double = 0.8,
    /** Oltre questa frazione di campioni "visti liberi", il prolungamento è contraddetto. */
    val maxFreeFraction: Double = 0.5,
    /** Oltre questa frazione di campioni fuori dal volume della mappa, il prolungamento non ha evidenza ed è rifiutato. */
    val maxOutsideFraction: Double = 0.5,
    val maxCameras: Int = 300,
    val maxPasses: Int = 4,
)

object PerimeterSolver {
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun cm(v: Double) = f(v * 100, 1) + " cm"
    private const val RAD = PI / 180

    /** Parete R3 con le grandezze usate da R4 (solo lettura). */
    private class W(val w: EstimatedWall) {
        val id = w.id
        val g = w.geometry
        val ux = g.ux; val uz = g.uz; val nx = g.nx; val nz = g.nz
        val s = g.pointAt(g.startU); val e = g.pointAt(g.endU)
        val obs = max(g.observedLengthM, 1e-6)
        val sPos = w.uncertainty.positionSigmaM
        val sTh = w.uncertainty.headingSigmaDeg * RAD
        val yLo = g.bottomY + 0.25 * (g.topY - g.bottomY)
        val yHi = g.bottomY + 0.75 * (g.topY - g.bottomY)
        fun uOf(x: Double, z: Double) = (x - g.cx) * ux + (z - g.cz) * uz
        fun perpSigma(u: Double) = sqrt(sPos * sPos + (sTh * u) * (sTh * u))
        fun dist(x: Double, z: Double) = (x - g.cx) * nx + (z - g.cz) * nz
    }

    fun solve(input: PerimeterInput, p: PerimeterParams = PerimeterParams()): PerimeterResult {
        val all = input.walls.sortedBy { it.id }.map { W(it) }
        val diag = mutableListOf<String>()
        val beyondReasons = HashMap<Int, List<String>>()
        var active = all
        var links: List<WallLink> = emptyList()
        var pass = 0
        while (true) {
            pass++
            links = select(active.flatMap { a -> active.filter { it.id != a.id }.map { b -> evaluate(a, b, active, input, p) } }, p)
            val outline = outline(active, links)
            val beyond = HashMap<Int, List<String>>()
            for (w in all) beyondCheck(w, outline, input, p)?.let { beyond[w.id] = it }
            val next = all.filter { it.id !in beyond }
            beyondReasons.clear(); for (k in beyond.keys.sorted()) beyondReasons[k] = beyond.getValue(k)
            if (next.map { it.id } == active.map { it.id }) break
            if (pass >= p.maxPasses) { diag.add("Verifica delle pareti oltre le aperture non stabile dopo $pass passate: usata l'ultima."); break }
            active = next
        }
        diag.add("Passate (collegamenti ↔ pareti oltre le aperture): $pass")
        return assemble(all, active, links, beyondReasons, input, p, diag)
    }

    // ---------------------------------------------------------------------------------------------------- valutazione collegamenti

    private class EndFit(val geom: Double, val unobserved: Double, val inferred: Boolean, val reject: String?, val support: EndSupport)
    private data class Quad(val ext: Double, val sigma: Double, val z: Double, val inferred: Boolean)

    /**
     * Un'estremità rispetto all'angolo: [ext] = distanza lungo la propria linea (positiva = l'angolo è oltre i dati), [sigma] = σ
     * propagata (estremità ⊕ intersezione), [alongSigma] = σ dell'intersezione lungo la linea.
     *
     * La σ propagata decide solo la COMPATIBILITÀ geometrica (rifiuti, punteggio). Se l'angolo è osservato lo decide l'EVIDENZA (R4.1):
     *  - estremità OBSERVED: angolo osservato se il gap è spiegato dalla sola σ di misura dell'estremità (≤ 3σ); se per spiegarlo serve
     *    la σ dell'intersezione, l'angolo resta DEDOTTO;
     *  - estremità non osservata (PARTIAL / UNCERTAIN): angolo osservato solo se i dati arrivano all'intersezione o la superano (gap ≤ 0);
     *    altrimenti il tratto fino all'angolo NON è osservato, qualunque sia la σ (terminale o dell'intersezione).
     */
    private fun endFit(name: String, end: WallEnd, ext: Double, sigma: Double, alongSigma: Double, p: PerimeterParams): EndFit {
        val z = ext / sigma
        val meas = end.evidence?.repeatSigmaM ?: end.sigmaM
        val term = end.evidence?.terminalSigmaM ?: 0.0
        val observedEnd = end.state == EndState.OBSERVED
        val gapObserved = ext <= 0.0 || (observedEnd && ext <= p.maxZ * meas)
        val support = EndSupport(end.state, end.reason, observedEnd, meas, term, alongSigma, ext, gapObserved, !gapObserved, ext <= p.maxZ * meas)
        if (z < -p.maxZ) return EndFit(0.0, 0.0, false, "$name: la parete osservata prosegue ${cm(-ext)} oltre l'angolo (${f(-z, 1)}σ)", support)
        if (observedEnd) {
            if (z > p.maxZ) return EndFit(0.0, 0.0, false, "$name: estremità OSSERVATA (${end.reason.label}) a ${cm(ext)} dall'angolo (${f(z, 1)}σ)", support)
            return EndFit(exp(-z * z / 2), if (gapObserved) 0.0 else ext, !gapObserved, null, support)
        }
        // Estremità non osservata (PARTIAL / UNCERTAIN): un prolungamento è possibile ma NON è misura.
        val g = if (z < 0) exp(-z * z / 2) else 1.0
        return EndFit(g, max(0.0, ext), !gapObserved, null, support)
    }

    private fun endFactor(end: WallEnd, p: PerimeterParams) = when {
        end.state == EndState.UNCERTAIN -> p.uncertainEndFactor
        end.state == EndState.OBSERVED && end.reason == EndReason.FREE_BEYOND -> p.freeBeyondEndFactor
        else -> 1.0
    }

    private fun quality(q: EvidenceQuality) = when (q) { EvidenceQuality.HIGH -> 1.0; EvidenceQuality.MEDIUM -> 0.85; EvidenceQuality.LOW -> 0.6 }

    /** Campioni dello spazio lungo la linea di [w] tra [u0] e [u1], mezza cella dietro la parete: (liberi, validi). */
    private fun freeAlong(w: W, u0: Double, u1: Double, input: PerimeterInput): Pair<Int, Int> {
        val c = input.spaceCellM
        val a = min(u0, u1) + c; val b = max(u0, u1) - c
        if (b <= a) return 0 to 0
        var free = 0; var valid = 0
        val n = ((b - a) / c).toInt() + 1
        for (k in 0..n) {
            val u = a + (b - a) * k / max(1, n)
            val q = w.g.pointAt(u)
            val x = q[0] - w.nx * c / 2; val z = q[1] - w.nz * c / 2
            for (y in doubleArrayOf(w.yLo, (w.yLo + w.yHi) / 2, w.yHi)) {
                val st = input.space.state(x, y, z)
                if (st < 0) continue
                valid++
                if (st == 1) free++
            }
        }
        return free to valid
    }

    /** Campioni del tratto [u0, u1] sulla linea di [w] che cadono fuori dal volume della mappa (−1): (fuori, totali). */
    private fun outsideAlong(w: W, u0: Double, u1: Double, input: PerimeterInput): Pair<Int, Int> {
        val c = input.spaceCellM
        val a = min(u0, u1); val b = max(u0, u1)
        if (b <= a) return 0 to 0
        val n = ((b - a) / c).toInt() + 1
        var out = 0
        for (k in 0..n) {
            val q = w.g.pointAt(a + (b - a) * k / max(1, n))
            if (input.space.state(q[0], (w.yLo + w.yHi) / 2, q[1]) < 0) out++
        }
        return out to n + 1
    }

    /** Qualcosa di occupato davanti alla linea di [w] tra [u0] e [u1] (entro la distanza mediana di osservazione). */
    private fun occupiedInFront(w: W, u0: Double, u1: Double, input: PerimeterInput): Boolean {
        val c = input.spaceCellM
        val a = min(u0, u1) + c; val b = max(u0, u1) - c
        if (b <= a) return false
        val reach = max(2 * c, w.w.evidence.rangeMedianM)
        val n = ((b - a) / c).toInt() + 1
        for (k in 0..n) {
            val q = w.g.pointAt(a + (b - a) * k / max(1, n))
            var t = 1.5 * c
            while (t <= reach) {
                if (input.space.state(q[0] + w.nx * t, (w.yLo + w.yHi) / 2, q[1] + w.nz * t) == 2) return true
                t += c
            }
        }
        return false
    }

    /** Superfici R2 vicine al tratto [u0, u1] della linea di [w]: sulla linea (entro 3σ + cella) o davanti (oggetto che nasconde). */
    private fun r2Near(w: W, u0: Double, u1: Double, input: PerimeterInput, p: PerimeterParams): List<Int> {
        val lo = min(u0, u1); val hi = max(u0, u1)
        val out = mutableListOf<Int>()
        for (t in input.r2.sortedBy { it.surfaceId }) {
            val ua = w.uOf(t.ax, t.az); val ub = w.uOf(t.bx, t.bz)
            if (max(ua, ub) < lo || min(ua, ub) > hi) continue
            val da = w.dist(t.ax, t.az); val db = w.dist(t.bx, t.bz)
            val tol = p.maxZ * w.perpSigma((lo + hi) / 2) + input.spaceCellM
            val onLine = abs(da) <= tol && abs(db) <= tol
            val inFront = min(da, db) > 0 && max(da, db) <= max(w.w.evidence.rangeMedianM, 2 * input.spaceCellM)
            if (onLine || inFront) out.add(t.surfaceId)
        }
        return out
    }

    /**
     * Un'altra parete osservata che giace sulla linea di [w] (stessa direzione entro 3σ, estremi entro 3σ + cella) dentro il tratto
     * [u0, u1]: il tratto "non osservato" la scavalcherebbe, quindi il collegamento salterebbe una parete osservata.
     */
    private fun skipped(w: W, u0: Double, u1: Double, exclude: Set<Int>, others: List<W>, input: PerimeterInput, p: PerimeterParams): Int? {
        val lo = min(u0, u1); val hi = max(u0, u1)
        for (k in others) {
            if (k.id in exclude || k.id == w.id) continue
            val turn = atan2(w.ux * k.uz - w.uz * k.ux, w.ux * k.ux + w.uz * k.uz)
            if (abs(turn) > p.maxZ * sqrt(w.sTh * w.sTh + k.sTh * k.sTh)) continue
            val ua = w.uOf(k.s[0], k.s[1]); val ub = w.uOf(k.e[0], k.e[1])
            if (min(ua, ub) >= hi || max(ua, ub) <= lo) continue
            val tol = p.maxZ * sqrt(w.perpSigma((lo + hi) / 2).let { it * it } + k.sPos * k.sPos) + input.spaceCellM
            if (abs(w.dist(k.s[0], k.s[1])) <= tol && abs(w.dist(k.e[0], k.e[1])) <= tol) return k.id
        }
        return null
    }

    /** Valuta il collegamento END di [a] → START di [b]. */
    private fun evaluate(a: W, b: W, others: List<W>, input: PerimeterInput, p: PerimeterParams): WallLink {
        val cross = a.ux * b.uz - a.uz * b.ux
        val dot = a.ux * b.ux + a.uz * b.uz
        val turn = atan2(cross, dot)
        val sTurn = sqrt(a.sTh * a.sTh + b.sTh * b.sTh)
        val ev = min(quality(a.w.evidence.quality), quality(b.w.evidence.quality))
        if (abs(turn) <= p.maxZ * sTurn) return sameWall(a, b, turn, sTurn, ev, others, input, p)
        val reasons = mutableListOf<String>()
        if (PI - abs(turn) <= p.maxZ * sTurn) {
            reasons.add("linee antiparallele (svolta ${f(turn / RAD, 1)}° ± ${f(sTurn / RAD, 1)}°): nessun angolo possibile")
            return WallLink(
                a.id, b.id, LinkKind.CORNER, LinkSupport.INFERRED, LinkDecision.REJECTED, 0.0, LinkComponents(0.0, 0.0, 0.0, 0.0, ev, 0.0),
                turn / RAD, sTurn / RAD, null, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, unobservedM = 0.0, gapReason = null, r2Near = emptyList(), reasons = reasons,
            )
        }
        // Angolo = intersezione delle due linee misurate: e_a + ext_a·u_a = s_b − ext_b·u_b.
        val dx = b.s[0] - a.e[0]; val dz = b.s[1] - a.e[1]
        val extA = (dx * b.uz - dz * b.ux) / cross
        val extB = (a.ux * dz - a.uz * dx) / cross
        val x = a.e[0] + a.ux * extA; val z = a.e[1] + a.uz * extA
        val si = a.perpSigma(a.uOf(x, z)); val sj = b.perpSigma(b.uOf(x, z))
        val sn = abs(sin(turn)); val cs = cos(turn)
        // Spostamento dell'angolo lungo ciascuna linea per le σ trasversali delle due linee (indipendenti), più la σ dell'estremità.
        val alongA = sqrt(sj * sj + si * si * cs * cs) / sn
        val alongB = sqrt(si * si + sj * sj * cs * cs) / sn
        val sigA = sqrt(alongA * alongA + a.w.end.sigmaM * a.w.end.sigmaM)
        val sigB = sqrt(alongB * alongB + b.w.start.sigmaM * b.w.start.sigmaM)
        val cornerSigma = sqrt(si * si + sj * sj) / sn
        val fa = endFit("W${a.id} fine", a.w.end, extA, sigA, alongA, p)
        val fb = endFit("W${b.id} inizio", b.w.start, extB, sigB, alongB, p)
        fa.reject?.let { reasons.add(it) }; fb.reject?.let { reasons.add(it) }
        val inferred = fa.inferred || fb.inferred
        var free = 1.0
        var near = emptyList<Int>()
        var r2 = 1.0
        if (reasons.isEmpty() && inferred) {
            val skipA = if (fa.inferred) skipped(a, a.g.endU, a.g.endU + extA, setOf(a.id, b.id), others, input, p) else null
            val skipB = if (fb.inferred) skipped(b, b.g.startU - extB, b.g.startU, setOf(a.id, b.id), others, input, p) else null
            for (k in listOfNotNull(skipA, skipB).distinct()) reasons.add("il prolungamento non osservato passa sopra la parete osservata W$k")
        }
        if (reasons.isEmpty() && inferred) {
            var fr = 0; var va = 0
            if (fa.inferred) freeAlong(a, a.g.endU, a.g.endU + extA, input).let { fr += it.first; va += it.second }
            if (fb.inferred) freeAlong(b, b.g.startU - extB, b.g.startU, input).let { fr += it.first; va += it.second }
            val frac = if (va == 0) 0.0 else fr.toDouble() / va
            free = 1 - frac
            if (frac > p.maxFreeFraction) reasons.add("il prolungamento attraversa spazio visto libero (${f(frac * 100, 0)}% dei campioni)")
            var ou = 0; var to = 0
            if (fa.inferred) outsideAlong(a, a.g.endU, a.g.endU + extA, input).let { ou += it.first; to += it.second }
            if (fb.inferred) outsideAlong(b, b.g.startU - extB, b.g.startU, input).let { ou += it.first; to += it.second }
            if (to > 0 && ou.toDouble() / to > p.maxOutsideFraction) reasons.add("il prolungamento esce dal volume esplorato dalla scansione (${f(ou * 100.0 / to, 0)}% dei campioni): nessuna evidenza")
            near = (if (fa.inferred) r2Near(a, a.g.endU, a.g.endU + extA, input, p) else emptyList()) +
                (if (fb.inferred) r2Near(b, b.g.startU - extB, b.g.startU, input, p) else emptyList())
            near = near.distinct().sorted()
            r2 = if (near.isNotEmpty()) 1.0 else p.unexplainedExtensionFactor
        }
        val obsFrac = (a.obs / (a.obs + fa.unobserved)) * (b.obs / (b.obs + fb.unobserved))
        val comp = LinkComponents(fa.geom * fb.geom, obsFrac, endFactor(a.w.end, p) * endFactor(b.w.start, p), free, ev, r2)
        val score = if (reasons.isNotEmpty()) 0.0 else comp.geometry * comp.observedFraction * comp.endpointState * comp.freeSpace * comp.evidence * comp.r2Support
        if (inferred && reasons.isEmpty()) reasons.add("angolo DEDOTTO: prolungamento non osservato " + listOfNotNull(
            if (fa.inferred) "W${a.id} ${cm(extA)} (${a.w.end.reason.label})" else null,
            if (fb.inferred) "W${b.id} ${cm(extB)} (${b.w.start.reason.label})" else null,
        ).joinToString(", "))
        return WallLink(
            a.id, b.id, LinkKind.CORNER, if (inferred) LinkSupport.INFERRED else LinkSupport.OBSERVED,
            if (score > 0) LinkDecision.ACCEPTED else LinkDecision.REJECTED, score, comp,
            turn / RAD, sTurn / RAD, x, z, cornerSigma, extA, sigA, extA / sigA, extB, sigB, extB / sigB,
            cornerAlongFromSigmaM = alongA, cornerAlongToSigmaM = alongB, unobservedM = fa.unobserved + fb.unobserved, gapReason = null, r2Near = near, reasons = reasons,
            fromEnd = fa.support, toEnd = fb.support,
        )
    }

    /** Frammenti della stessa parete: END di [a] → START di [b] con direzioni compatibili. */
    private fun sameWall(a: W, b: W, turn: Double, sTurn: Double, ev: Double, others: List<W>, input: PerimeterInput, p: PerimeterParams): WallLink {
        val reasons = mutableListOf<String>()
        val dx = b.s[0] - a.e[0]; val dz = b.s[1] - a.e[1]
        val gap = dx * a.ux + dz * a.uz
        val sGap = sqrt(a.w.end.sigmaM * a.w.end.sigmaM + b.w.start.sigmaM * b.w.start.sigmaM)
        val mx = (a.e[0] + b.s[0]) / 2; val mz = (a.e[1] + b.s[1]) / 2
        val qx = mx - a.dist(mx, mz) * a.nx; val qz = mz - a.dist(mx, mz) * a.nz
        val lateral = b.dist(qx, qz)
        val sLat = sqrt(a.perpSigma(a.uOf(mx, mz)).let { it * it } + b.perpSigma(b.uOf(mx, mz)).let { it * it })
        val zLat = lateral / sLat; val zHead = turn / sTurn; val zGap = gap / sGap
        if (zGap < -p.maxZ) reasons.add("i frammenti si sovrappongono di ${cm(-gap)} (${f(-zGap, 1)}σ): due strati, non una sequenza")
        if (abs(zLat) > p.maxZ) reasons.add("linee sfalsate di ${cm(abs(lateral))} (${f(abs(zLat), 1)}σ)")
        for ((name, end) in listOf("W${a.id} fine" to a.w.end, "W${b.id} inizio" to b.w.start))
            if (end.state == EndState.OBSERVED && end.reason == EndReason.CORNER) reasons.add("$name: estremità osservata ad un angolo con un'altra superficie, la parete non prosegue")
        // R4.1: il gap tra i frammenti è osservato solo se i dati si toccano (gap ≤ 0) o se entrambe le estremità affacciate sono
        // OSSERVATE e il gap è entro 3 σ di misura; la σ terminale non lo fa sparire.
        val measA = a.w.end.evidence?.repeatSigmaM ?: a.w.end.sigmaM
        val measB = b.w.start.evidence?.repeatSigmaM ?: b.w.start.sigmaM
        val gapObserved = gap <= 0.0 || (a.w.end.state == EndState.OBSERVED && b.w.start.state == EndState.OBSERVED && gap <= p.maxZ * sqrt(measA * measA + measB * measB))
        val unobserved = if (gapObserved) 0.0 else gap
        if (reasons.isEmpty() && unobserved > 0) skipped(a, a.g.endU, a.g.endU + gap, setOf(a.id, b.id), others, input, p)?.let { reasons.add("nel gap c'è la parete osservata W$it: i frammenti non sono consecutivi") }
        var gapReason: GapReason? = null
        var near = emptyList<Int>()
        var r2 = 1.0
        if (reasons.isEmpty() && unobserved > 0) {
            val (fr, va) = freeAlong(a, a.g.endU, a.g.endU + gap, input)
            gapReason = when {
                va > 0 && fr.toDouble() / va > p.maxFreeFraction -> GapReason.SEEN_THROUGH
                occupiedInFront(a, a.g.endU, a.g.endU + gap, input) -> GapReason.OCCLUDED
                else -> GapReason.NOT_SEEN
            }
            near = r2Near(a, a.g.endU, a.g.endU + gap, input, p)
            if (gapReason == GapReason.NOT_SEEN && near.isEmpty()) r2 = p.unexplainedExtensionFactor
        }
        val comp = LinkComponents(
            exp(-zLat * zLat / 2) * exp(-zHead * zHead / 2), (a.obs + b.obs) / (a.obs + b.obs + unobserved),
            (if (a.w.end.state == EndState.UNCERTAIN) p.uncertainEndFactor else 1.0) * (if (b.w.start.state == EndState.UNCERTAIN) p.uncertainEndFactor else 1.0),
            1.0, ev, r2,
        )
        val score = if (reasons.isNotEmpty()) 0.0 else comp.geometry * comp.observedFraction * comp.endpointState * comp.freeSpace * comp.evidence * comp.r2Support
        if (reasons.isEmpty()) reasons.add("frammenti della stessa linea" + if (gapReason != null) ": gap NON osservato di ${cm(unobserved)} (${gapReason.label})" else "")
        return WallLink(
            a.id, b.id, LinkKind.SAME_WALL, if (unobserved > 0) LinkSupport.INFERRED else LinkSupport.OBSERVED,
            if (score > 0) LinkDecision.ACCEPTED else LinkDecision.REJECTED, score, comp, turn / RAD, sTurn / RAD, null, null, null,
            gap, sGap, zGap, 0.0, sGap, 0.0, lateralOffsetM = lateral, lateralSigmaM = sLat, zLateral = zLat, zHeading = zHead,
            unobservedM = unobserved, gapReason = gapReason, r2Near = near, reasons = reasons,
        )
    }

    /** Scelta: migliore reciproco, senza alternative entro [PerimeterParams.ambiguityRatio]. */
    private fun select(evaluated: List<WallLink>, p: PerimeterParams): List<WallLink> {
        val ok = evaluated.filter { it.decision != LinkDecision.REJECTED }
        val order = compareByDescending<WallLink> { it.score }.thenBy { it.fromWall }.thenBy { it.toWall }
        val byFrom = ok.groupBy { it.fromWall }.mapValues { it.value.sortedWith(order) }
        val byTo = ok.groupBy { it.toWall }.mapValues { it.value.sortedWith(order) }
        // Sostenibile: sopra il minimo e non nettamente superato (oltre il rapporto di ambiguità) su nessuna delle due estremità.
        fun viable(l: WallLink) = l.score >= p.minLinkScore &&
            byFrom.getValue(l.fromWall).first().score <= l.score * p.ambiguityRatio && byTo.getValue(l.toWall).first().score <= l.score * p.ambiguityRatio
        // Rivali: alternative sostenibili sulla stessa estremità (un'alternativa conta solo se regge anche all'altra estremità).
        fun rivals(list: List<WallLink>, l: WallLink) = list.filter { it !== l && viable(it) }
        return evaluated.map { l ->
            if (l.decision == LinkDecision.REJECTED) return@map l
            val from = byFrom.getValue(l.fromWall); val to = byTo.getValue(l.toWall)
            val extra = mutableListOf<String>()
            val decision = when {
                l.score < p.minLinkScore -> { extra.add("punteggio ${f(l.score, 4)} sotto il minimo ${f(p.minLinkScore, 4)}"); LinkDecision.REJECTED }
                !viable(l) -> {
                    val better = if (from.first().score > l.score * p.ambiguityRatio) from.first() else to.first()
                    extra.add("superato da W${better.fromWall}→W${better.toWall} (${f(better.score, 3)})"); LinkDecision.REJECTED
                }
                rivals(from, l).isEmpty() && rivals(to, l).isEmpty() -> LinkDecision.ACCEPTED
                else -> {
                    val alt = (rivals(from, l) + rivals(to, l)).distinct().joinToString { "W${it.fromWall}→W${it.toWall} (${f(it.score, 3)})" }
                    extra.add("alternativa altrettanto plausibile: $alt"); LinkDecision.AMBIGUOUS
                }
            }
            l.copy(decision = decision, reasons = l.reasons + extra)
        }
    }

    // ------------------------------------------------------------------------------------------------- pareti oltre le aperture

    private class Seg(val owner: Int, val ax: Double, val az: Double, val bx: Double, val bz: Double, val observed: Boolean)

    /** Contorno: tratti R3 (con i gap interni) più prolungamenti e gap dei collegamenti accettati. */
    private fun outline(active: List<W>, links: List<WallLink>): List<Seg> {
        val out = mutableListOf<Seg>()
        for (w in active) out.add(Seg(w.id, w.s[0], w.s[1], w.e[0], w.e[1], true))
        val byId = active.associateBy { it.id }
        for (l in links.filter { it.decision == LinkDecision.ACCEPTED }) {
            val a = byId[l.fromWall] ?: continue; val b = byId[l.toWall] ?: continue
            if (l.kind == LinkKind.SAME_WALL) { out.add(Seg(a.id, a.e[0], a.e[1], b.s[0], b.s[1], false)); continue }
            val x = l.cornerX ?: continue; val z = l.cornerZ ?: continue
            if (l.extensionFromM > 0) out.add(Seg(a.id, a.e[0], a.e[1], x, z, false))
            if (l.extensionToM > 0) out.add(Seg(b.id, x, z, b.s[0], b.s[1], false))
        }
        return out
    }

    /** Parametro t ∈ [0, 1] sul segmento p→q dove attraversa il segmento s (null se non lo attraversa). */
    private fun crossT(px: Double, pz: Double, qx: Double, qz: Double, s: Seg): Double? {
        val rx = qx - px; val rz = qz - pz; val sx = s.bx - s.ax; val sz = s.bz - s.az
        val den = rx * sz - rz * sx
        if (abs(den) < 1e-12) return null
        val t = ((s.ax - px) * sz - (s.az - pz) * sx) / den
        val v = ((s.ax - px) * rz - (s.az - pz) * rx) / den
        return if (t in 0.0..1.0 && v in 0.0..1.0) t else null
    }

    /** Motivi se [w] non si vede direttamente da nessuna camera davanti a lei (null = visibile). */
    private fun beyondCheck(w: W, outline: List<Seg>, input: PerimeterInput, p: PerimeterParams): List<String>? {
        if (input.cameras.isEmpty()) return null
        val step = max(1, (input.cameras.size + p.maxCameras - 1) / p.maxCameras)
        val qs = listOf(0.25, 0.5, 0.75).map { w.g.pointAt(w.g.startU + it * w.g.lengthM) }
        val near = p.maxZ * w.sPos + input.spaceCellM
        var front = 0; var direct = 0
        val blockObserved = HashMap<Int, Int>(); val blockOpen = HashMap<Int, Int>()
        var i = 0
        while (i < input.cameras.size) {
            val c = input.cameras[i]; i += step
            if (w.dist(c[0], c[1]) <= 0) continue
            front++
            var clear = false
            for (q in qs) {
                val len = sqrt((q[0] - c[0]) * (q[0] - c[0]) + (q[1] - c[1]) * (q[1] - c[1]))
                val tMax = 1 - near / max(len, 1e-9)
                var blocked: Seg? = null
                for (s in outline) {
                    if (s.owner == w.id) continue
                    val t = crossT(c[0], c[1], q[0], q[1], s) ?: continue
                    if (t < tMax) { blocked = s; if (s.observed) break }
                }
                if (blocked == null) { clear = true; break }
                val m = if (blocked.observed) blockObserved else blockOpen
                m[blocked.owner] = (m[blocked.owner] ?: 0) + 1
            }
            if (clear) direct++
        }
        if (direct > 0) return null
        if (front == 0) return listOf("nessuna posizione della camera davanti alla parete")
        val r = mutableListOf("da nessuna delle $front posizioni della camera davanti alla parete la si vede senza attraversare il contorno")
        if (blockOpen.isNotEmpty()) r.add("linea di vista attraverso un tratto NON osservato (apertura, gap o prolungamento) di " + blockOpen.keys.sorted().joinToString { "W$it" })
        if (blockObserved.isNotEmpty()) r.add("linea di vista attraverso il tratto osservato di " + blockObserved.keys.sorted().joinToString { "W$it" })
        return r
    }

    // ------------------------------------------------------------------------------------------------------------- perimetri

    /** P(χ²_k ≥ x) per k pari: e^(−x/2) Σ_{m<k/2} (x/2)^m / m!. */
    internal fun chi2SurvivalEven(x: Double, k: Int): Double {
        if (k <= 0) return 1.0
        val h = x / 2
        var term = 1.0; var sum = 1.0
        for (m in 1 until k / 2) { term *= h / m; sum += term }
        return min(1.0, exp(-h) * sum)
    }

    private fun segmentsCross(a: DoubleArray, b: DoubleArray, c: DoubleArray, d: DoubleArray): Boolean {
        fun orient(p: DoubleArray, q: DoubleArray, r: DoubleArray) = (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
        val o1 = orient(a, b, c); val o2 = orient(a, b, d); val o3 = orient(c, d, a); val o4 = orient(c, d, b)
        return o1 * o2 < 0 && o3 * o4 < 0
    }

    private fun assemble(
        all: List<W>, active: List<W>, links: List<WallLink>, beyond: Map<Int, List<String>>, input: PerimeterInput, p: PerimeterParams, diag: MutableList<String>,
    ): PerimeterResult {
        val byId = all.associateBy { it.id }
        val accepted = links.filter { it.decision == LinkDecision.ACCEPTED }
        val next = HashMap<Int, WallLink>(); val prev = HashMap<Int, WallLink>()
        for (l in accepted) { next[l.fromWall] = l; prev[l.toWall] = l }
        val ambiguous = links.filter { it.decision == LinkDecision.AMBIGUOUS }
        val seen = HashSet<Int>()
        val perimeters = mutableListOf<Perimeter>()
        val hypotheses = mutableListOf<PerimeterHypothesis>()
        for (w in active) {
            if (w.id in seen) continue
            // Risale all'inizio della catena (o riconosce un anello).
            var head = w.id
            val guard = HashSet<Int>()
            while (true) {
                val pl = prev[head] ?: break
                if (!guard.add(head)) break
                head = pl.fromWall
                if (head == w.id) break
            }
            val ids = mutableListOf(head); seen.add(head)
            var cycle = false
            while (true) {
                val nl = next[ids.last()] ?: break
                if (nl.toWall == ids.first()) { cycle = true; break }
                if (!seen.add(nl.toWall)) break
                ids.add(nl.toWall)
            }
            val id = perimeters.size
            val chainLinks = (0 until ids.size - 1).map { k -> next.getValue(ids[k]) } + if (cycle) listOf(next.getValue(ids.last())) else emptyList()
            perimeters.add(perimeter(id, ids.map { byId.getValue(it) }, chainLinks, cycle, ambiguous, p, hypotheses))
        }
        // Principale: il perimetro con almeno un collegamento e la maggiore lunghezza osservata.
        val main = perimeters.filter { it.links.isNotEmpty() }.maxWithOrNull(compareBy<Perimeter> { it.observedLengthM }.thenByDescending { it.id })
        val assessments = all.map { w ->
            val per = perimeters.firstOrNull { w.id in it.wallIds }
            when {
                w.id in beyond -> WallAssessment(w.id, WallRole.BEYOND_OPENING, null, beyond.getValue(w.id))
                per != null && per === main && per.wallIds.size > 1 -> WallAssessment(w.id, WallRole.PERIMETER, per.id, emptyList())
                per != null && per.wallIds.size > 1 -> WallAssessment(w.id, WallRole.OTHER_CHAIN, per.id, listOf("catena P${per.id} (${per.state})"))
                else -> {
                    val own = links.filter { it.fromWall == w.id || it.toWall == w.id }
                    val r = own.filter { it.decision == LinkDecision.AMBIGUOUS }.map { "ambiguo: W${it.fromWall}→W${it.toWall}" }.ifEmpty { listOf("nessun collegamento supportato (${own.count { it.decision == LinkDecision.REJECTED }} candidati rifiutati)") }
                    WallAssessment(w.id, WallRole.UNLINKED, per?.id, r)
                }
            }
        }
        return PerimeterResult(main?.state ?: PerimeterState.OPEN, main, perimeters, links, assessments, hypotheses, diag)
    }

    private fun perimeter(id: Int, ws: List<W>, links: List<WallLink>, cycle: Boolean, ambiguous: List<WallLink>, p: PerimeterParams, hyp: MutableList<PerimeterHypothesis>): Perimeter {
        val ids = ws.map { it.id }
        val byId = ws.associateBy { it.id }
        val reasons = mutableListOf<String>()
        val corners = mutableListOf<PerimeterCorner>()
        val unobserved = mutableListOf<UnobservedStretch>()
        for (w in ws) for (gp in w.w.gaps) {
            val a = w.g.pointAt(gp.fromU); val b = w.g.pointAt(gp.toU)
            unobserved.add(UnobservedStretch(w.id, a[0], a[1], b[0], b[1], "gap interno R3", gp.reason.label))
        }
        for (l in links) {
            val a = byId.getValue(l.fromWall); val b = byId.getValue(l.toWall)
            if (l.kind == LinkKind.SAME_WALL) {
                if (l.unobservedM > 0) unobserved.add(UnobservedStretch(a.id, a.e[0], a.e[1], b.s[0], b.s[1], "gap tra frammenti W${a.id}–W${b.id}", l.gapReason?.label ?: ""))
                continue
            }
            val x = l.cornerX!!; val z = l.cornerZ!!
            corners.add(PerimeterCorner(a.id, b.id, x, z, 180 + l.turnDeg, l.turnSigmaDeg, l.cornerSigmaM!!, l.support))
            // R4.1: ogni tratto fino a un angolo dedotto resta NON osservato, qualunque sia la σ.
            if (l.fromEnd?.inferredCorner ?: (l.zFrom > p.maxZ)) unobserved.add(UnobservedStretch(a.id, a.e[0], a.e[1], x, z, "prolungamento fino all'angolo", a.w.end.reason.label))
            if (l.toEnd?.inferredCorner ?: (l.zTo > p.maxZ)) unobserved.add(UnobservedStretch(b.id, x, z, b.s[0], b.s[1], "prolungamento dall'angolo", b.w.start.reason.label))
        }
        val endsInvolved = buildList {
            for (l in links) { add(byId.getValue(l.fromWall).w.end); add(byId.getValue(l.toWall).w.start) }
            if (!cycle) { add(ws.last().w.end); add(ws.first().w.start) }
        }
        val alternatives = ambiguous.filter { it.fromWall in ids || it.toWall in ids }.map { "W${it.fromWall}→W${it.toWall} (${f(it.score, 3)}): " + it.reasons.last() }.distinct()
        val scores = links.map { it.score }
        val meanScore = if (scores.isEmpty()) 0.0 else exp(scores.sumOf { kotlin.math.ln(max(it, 1e-12)) } / scores.size)
        val observed = ws.sumOf { it.g.observedLengthM }
        var closure: ClosureCheck? = null
        var perimeterLen: Double? = null; var area: Double? = null; var sP: Double? = null; var sA: Double? = null
        var closureConf: Double? = null
        var missing: MissingSide? = null
        val state: PerimeterState
        if (cycle) {
            var chi2 = 0.0; var dof = 0; var err2 = 0.0; var sig2 = 0.0
            for (l in links) {
                if (l.kind == LinkKind.SAME_WALL) {
                    chi2 += l.zLateral!! * l.zLateral + l.zHeading!! * l.zHeading; dof += 2
                    err2 += l.lateralOffsetM!! * l.lateralOffsetM; sig2 += l.lateralSigmaM!! * l.lateralSigmaM
                    continue
                }
                for ((ext, sg, zz, inf) in listOf(
                    Quad(l.extensionFromM, l.extensionFromSigmaM, l.zFrom, l.fromEnd?.inferredCorner ?: (l.zFrom > p.maxZ)),
                    Quad(l.extensionToM, l.extensionToSigmaM, l.zTo, l.toEnd?.inferredCorner ?: (l.zTo > p.maxZ)),
                )) {
                    // R4.1: solo le parti OSSERVATE entrano nel χ²; un tratto non osservato non è uno scarto di misura (resta un gap,
                    // contato a parte in ClosureCheck.unobservedM, e impedisce CLOSED).
                    if (inf) continue
                    chi2 += zz * zz; dof++; err2 += ext * ext; sig2 += sg * sg
                }
            }
            if (dof % 2 == 1) dof++ // un termine escluso: approssimazione conservativa (più gradi di libertà, nessuna informazione inventata)
            val pv = chi2SurvivalEven(chi2, dof)
            val turnSum = links.sumOf { it.turnDeg }
            val turnSigma = sqrt(links.sumOf { it.turnSigmaDeg * it.turnSigmaDeg })
            val pts = corners.map { doubleArrayOf(it.x, it.z) }
            var simple = pts.size >= 3
            for (i in pts.indices) for (j in i + 1 until pts.size) {
                if (j == i || (j + 1) % pts.size == i || (i + 1) % pts.size == j) continue
                if (segmentsCross(pts[i], pts[(i + 1) % pts.size], pts[j], pts[(j + 1) % pts.size])) simple = false
            }
            var signed = 0.0
            for (i in pts.indices) { val a = pts[i]; val b = pts[(i + 1) % pts.size]; signed += a[0] * b[1] - b[0] * a[1] }
            signed /= 2
            closure = ClosureCheck(sqrt(err2), sqrt(sig2), chi2, dof, pv, pv >= p.closureMinP, turnSum, turnSigma, simple, unobserved.sumOf { it.lengthM })
            // Lunghezze, area e σ propagate dagli angoli.
            if (pts.size >= 3) {
                var len = 0.0; var vl = 0.0; var va = 0.0
                val cornerLinks = links.filter { it.kind == LinkKind.CORNER }
                for (k in cornerLinks.indices) {
                    val c0 = cornerLinks[(k - 1 + cornerLinks.size) % cornerLinks.size]; val c1 = cornerLinks[k]
                    val side = sqrt((c1.cornerX!! - c0.cornerX!!).let { it * it } + (c1.cornerZ!! - c0.cornerZ!!).let { it * it })
                    len += side
                    vl += c0.cornerAlongToSigmaM!! * c0.cornerAlongToSigmaM + c1.cornerAlongFromSigmaM!! * c1.cornerAlongFromSigmaM
                    val sideWalls = ws.filter { it.id == c1.fromWall }
                    val sPosSide = sideWalls.maxOfOrNull { it.sPos } ?: 0.0
                    va += (side * sPosSide) * (side * sPosSide)
                }
                perimeterLen = len; sP = sqrt(vl); area = abs(signed); sA = sqrt(va)
            }
            closureConf = meanScore * min(1.0, pv / p.closureMinP)
            // Condizioni di CLOSED (R4.5), ognuna con il suo motivo.
            if (corners.size < 3) reasons.add("meno di 3 angoli: nessun poligono")
            // R4.1: un angolo vale per CLOSED solo se per ENTRAMBE le estremità è osservato (OBSERVED entro la σ di misura, o dati che
            // arrivano all'intersezione). Una PARTIAL geometricamente compatibile resta PARTIAL: l'angolo è dedotto.
            for (l in links.filter { it.kind == LinkKind.CORNER && it.support == LinkSupport.INFERRED }) {
                val ends = listOfNotNull(
                    l.fromEnd?.takeIf { it.inferredCorner }?.let { "fine di W${l.fromWall} ${it.endpointState}/${it.endpointReason} a ${cm(it.gapToIntersectionM)} dall'angolo" },
                    l.toEnd?.takeIf { it.inferredCorner }?.let { "inizio di W${l.toWall} ${it.endpointState}/${it.endpointReason} a ${cm(it.gapToIntersectionM)} dall'angolo" },
                )
                reasons.add("angolo W${l.fromWall}→W${l.toWall} dedotto: ${cm(l.unobservedM)} di prolungamento non osservato" + if (ends.isEmpty()) "" else " (${ends.joinToString("; ")})")
            }
            for (l in links.filter { it.kind == LinkKind.CORNER }) {
                if (byId.getValue(l.fromWall).w.end.state == EndState.UNCERTAIN) reasons.add("estremità instabile: fine di W${l.fromWall}")
                if (byId.getValue(l.toWall).w.start.state == EndState.UNCERTAIN) reasons.add("estremità instabile: inizio di W${l.toWall}")
            }
            for (u in unobserved.filter { it.reason == GapReason.NOT_SEEN.label }) reasons.add("tratto NON osservato senza spiegazione: ${u.kind} (W${u.wallId}, ${cm(u.lengthM)})")
            if (!closure.compatible) reasons.add("errore di chiusura ${cm(closure.errorM)} NON compatibile con le σ propagate (χ² ${f(chi2, 1)} su $dof g.d.l., p = ${f(pv, 4)})")
            if (alternatives.isNotEmpty()) reasons.add("alternative altrettanto plausibili: ${alternatives.size}")
            if (!simple) reasons.add("poligono non semplice (lati che si incrociano)")
            if ((turnSum / 360).roundToInt() != -1 || signed >= 0) reasons.add("percorrenza incoerente con l'interno a destra (somma delle svolte ${f(turnSum, 1)}°)")
            state = if (reasons.isEmpty()) PerimeterState.CLOSED else PerimeterState.UNCERTAIN
            if (state == PerimeterState.CLOSED) reasons.add("tutte le condizioni di chiusura verificate")
        } else {
            val last = ws.last(); val first = ws.first()
            if (ws.size > 1) {
                missing = MissingSide(
                    last.id, first.id, last.e[0], last.e[1], last.w.end.state, last.w.end.reason, first.s[0], first.s[1], first.w.start.state, first.w.start.reason,
                    "nessun collegamento supportato tra la fine di W${last.id} (${last.w.end.state}, ${last.w.end.reason.label}) e l'inizio di W${first.id} (${first.w.start.state}, ${first.w.start.reason.label})",
                )
                // Ipotesi (INFERRED / NOT OBSERVED): continuazione possibile fino all'intersezione davanti alle due estremità, oppure lato mancante.
                val cross = last.ux * first.uz - last.uz * first.ux
                val dx = first.s[0] - last.e[0]; val dz = first.s[1] - last.e[1]
                val gapLen = sqrt(dx * dx + dz * dz)
                val ea = if (abs(cross) > 1e-9) (dx * first.uz - dz * first.ux) / cross else -1.0
                val eb = if (abs(cross) > 1e-9) (last.ux * dz - last.uz * dx) / cross else -1.0
                if (ea >= 0 && eb >= 0 && ea + eb <= 2 * (gapLen + observed)) {
                    val x = last.e[0] + last.ux * ea; val z = last.e[1] + last.uz * ea
                    hyp.add(PerimeterHypothesis(HypothesisKind.POSSIBLE_CONTINUATION, id, last.id, first.id, listOf(last.e[0], last.e[1], x, z, first.s[0], first.s[1]), ea + eb,
                        "prolungamento di W${last.id} (${cm(ea)}) e di W${first.id} (${cm(eb)}) fino all'intersezione delle linee misurate"))
                } else {
                    hyp.add(PerimeterHypothesis(HypothesisKind.MISSING_SIDE, id, last.id, first.id, listOf(last.e[0], last.e[1], first.s[0], first.s[1]), gapLen,
                        "lato mancante tra le estremità aperte (le linee non si incontrano davanti a entrambe)"))
                }
                reasons.add("catena aperta: " + missing.reason)
            }
            state = when {
                ws.size == 1 -> PerimeterState.OPEN
                alternatives.isNotEmpty() -> PerimeterState.UNCERTAIN
                else -> PerimeterState.PARTIAL
            }
            if (state == PerimeterState.UNCERTAIN) reasons.add("soluzione ambigua: ${alternatives.size} alternative altrettanto plausibili")
        }
        val unc = PerimeterUncertainty(
            ws.maxOf { it.sPos }, ws.maxOf { it.w.uncertainty.headingSigmaDeg },
            endsInvolved.count { it.state == EndState.OBSERVED }, endsInvolved.count { it.state == EndState.PARTIAL }, endsInvolved.count { it.state == EndState.UNCERTAIN },
            scores.minOrNull() ?: 0.0, meanScore, closure?.pValue, closureConf,
            ws.minOf { it.w.recognitionConfidence }, ws.sumOf { it.w.recognitionConfidence } / ws.size,
            ws.map { it.w.evidence.quality }.maxBy { it.ordinal }, sP, sA,
        )
        return Perimeter(id, state, ids, links, corners, unobserved, missing, closure, unc, observed, perimeterLen, area, alternatives, reasons)
    }

    /** Ingresso di R4 dalla pipeline: pareti R3 così come sono, spazio libero, traiettoria della camera, superfici R2 non usate. */
    fun inputFrom(map: GlobalMap, s: SurfaceResult, walls: WallEstimationResult): PerimeterInput {
        val used = walls.walls.flatMap { it.sourceSurfaceIds }.toSet()
        val traces = s.surfaces.filter { it.orientation == Orientation.VERTICAL && it.id !in used }.map { sf ->
            val c = sf.plane.centroid
            val h = sqrt(sf.plane.nx * sf.plane.nx + sf.plane.nz * sf.plane.nz).takeIf { it > 1e-9 } ?: 1.0
            R2Trace(sf.id, sf.kind, c[0] + sf.u[0] * sf.uMin, c[2] + sf.u[2] * sf.uMin, c[0] + sf.u[0] * sf.uMax, c[2] + sf.u[2] * sf.uMax, sf.plane.nx / h, sf.plane.nz / h)
        }
        val cams = map.frames.map { doubleArrayOf(it.pose.x, it.pose.z) }
        return PerimeterInput(walls.walls, { x, y, z -> map.freeSpace.state(x, y, z) }, map.freeSpace.grid.cellM, cams, traces)
    }
}
