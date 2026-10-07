package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.RecPose
import com.sagoma.planimetria.scan.recording.rotate
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Geometria della mira e delle linee: tutto puro, in pianta (x, z) e metri. */
object Aim {
    class Hit(val plane: AimPlane, val t: Double, val point: ArPoint)

    /**
     * Linea di una parete ricavata da un piano verticale di ARCore: normale del piano → direzione orizzontale; posizione dalla media
     * dei vertici lungo la normale; estremi dalla proiezione dei vertici. Null se il piano non è verticale (normale verticale).
     * `reference` orienta la direzione come quella di una linea già vista (così posizione e normale non cambiano segno).
     */
    fun line(plane: AimPlane, reference: ArXZ? = null): WallLine? {
        if (plane.polygonWorld.size < 3) return null
        val n = plane.pose.rotate(0.0, 1.0, 0.0)
        val hl = sqrt(n.x * n.x + n.z * n.z)
        if (hl < 1e-6) return null
        val nh = ArXZ(n.x / hl, n.z / hl)
        var d = ArXZ(-nh.z, nh.x)
        d = orient(d, reference)
        val nn = ArXZ(-d.z, d.x)
        var smin = Double.MAX_VALUE; var smax = -Double.MAX_VALUE
        var off = 0.0; var ymin = Double.MAX_VALUE; var ymax = -Double.MAX_VALUE
        for (v in plane.polygonWorld) {
            val s = v.x * d.x + v.z * d.z
            smin = min(smin, s); smax = max(smax, s)
            off += v.x * nn.x + v.z * nn.z
            ymin = min(ymin, v.y); ymax = max(ymax, v.y)
        }
        off /= plane.polygonWorld.size
        val a = ArXZ(d.x * smin + nn.x * off, d.z * smin + nn.z * off)
        val b = ArXZ(d.x * smax + nn.x * off, d.z * smax + nn.z * off)
        return WallLine(a, b, d, nn, off, ymin, ymax, toDegrees(asin(min(1.0, abs(n.y)))))
    }

    private fun orient(d: ArXZ, ref: ArXZ?): ArXZ =
        if (ref != null) (if (d.x * ref.x + d.z * ref.z < 0) d * -1.0 else d)
        else if (d.x > 1e-9 || (abs(d.x) <= 1e-9 && d.z > 0)) d else d * -1.0

    /** La stessa linea con la direzione orientata come `reference`. */
    fun oriented(l: WallLine, reference: ArXZ): WallLine =
        if (l.direction.x * reference.x + l.direction.z * reference.z >= 0) l
        else WallLine(l.b, l.a, l.direction * -1.0, l.normal * -1.0, -l.offset, l.minY, l.maxY, l.tiltDeg)

    /** Angolo (gradi, da 0 a 90) tra le direzioni di due linee. */
    fun angleBetween(a: WallLine, b: WallLine): Double = toDegrees(asin(min(1.0, abs(a.direction.cross(b.direction)))))

    /** Distanza perpendicolare (m) massima degli estremi di `b` dalla retta di `a`. */
    fun offsetBetween(a: WallLine, b: WallLine): Double {
        fun dist(p: ArXZ) = abs((p - a.a).cross(a.direction))
        return max(dist(b.a), dist(b.b))
    }

    fun compatible(a: WallLine, b: WallLine, p: AssistedParams): Boolean =
        angleBetween(a, b) <= p.sameAngleTolDeg && offsetBetween(a, b) <= p.sameOffsetTolM

    /** Intersezione del raggio di mira con un piano, se cade nel poligono (con margine) e alla distanza utile. */
    fun hit(f: AimFrame, plane: AimPlane, p: AssistedParams): Hit? {
        val o = f.camera ?: return null
        val fw = f.forward ?: return null
        val n = plane.pose.rotate(0.0, 1.0, 0.0)
        val denom = fw.x * n.x + fw.y * n.y + fw.z * n.z
        if (abs(denom) < 1e-3) return null
        val po = plane.pose
        val t = ((po.x - o.x) * n.x + (po.y - o.y) * n.y + (po.z - o.z) * n.z) / denom
        if (t < p.minAimDistanceM || t > p.maxAimDistanceM) return null
        val hit = ArPoint(o.x + fw.x * t, o.y + fw.y * t, o.z + fw.z * t)
        // Al sistema locale del piano: rotazione inversa (coniugato del quaternione).
        val inv = RecPose(0.0, 0.0, 0.0, -po.qx, -po.qy, -po.qz, po.qw)
        val local = inv.rotate(hit.x - po.x, hit.y - po.y, hit.z - po.z)
        val dist = distanceToPolygon(plane.polygonLocal, local.x, local.z)
        return if (dist <= p.aimMarginM) Hit(plane, t, hit) else null
    }

    /** Il piano mirato: il più vicino lungo il raggio tra i piani verticali in tracking non assorbiti e con linea valida. */
    fun select(f: AimFrame, p: AssistedParams): Hit? =
        f.planes.filter { it.tracking && it.subsumedBy == null && line(it) != null }
            .mapNotNull { hit(f, it, p) }
            .minWithOrNull(compareBy<Hit>({ it.t }, { it.plane.key }))

    /**
     * Unisce all'estensione della linea mirata i frammenti compatibili (piani divisi da ARCore): la RETTA resta quella del piano
     * mirato, si allungano solo gli estremi (con vuoti fino a `sameGapTolM`) e l'intervallo di quote.
     */
    fun union(aimed: WallLine, others: List<WallLine>, p: AssistedParams): Pair<WallLine, List<WallLine>> {
        val used = ArrayList<WallLine>()
        val cand = others.filter { compatible(aimed, it, p) }.map { o ->
            val t0 = (o.a - aimed.a) dot aimed.direction; val t1 = (o.b - aimed.a) dot aimed.direction
            Triple(min(t0, t1), max(t0, t1), o)
        }.sortedWith(compareBy({ it.first }, { it.second }))
        var lo = 0.0; var hi = aimed.length
        var minY = aimed.minY; var maxY = aimed.maxY
        var changed = true
        val taken = BooleanArray(cand.size)
        while (changed) {
            changed = false
            for ((i, c) in cand.withIndex()) {
                if (taken[i]) continue
                if (c.first <= hi + p.sameGapTolM && c.second >= lo - p.sameGapTolM) {
                    taken[i] = true; changed = true
                    lo = min(lo, c.first); hi = max(hi, c.second)
                    minY = min(minY, c.third.minY); maxY = max(maxY, c.third.maxY)
                    used += c.third
                }
            }
        }
        val a = aimed.a + aimed.direction * lo
        val b = aimed.a + aimed.direction * hi
        return WallLine(a, b, aimed.direction, aimed.normal, aimed.offset, minY, maxY, aimed.tiltDeg) to used
    }

    private fun distanceToPolygon(poly: List<Pair<Double, Double>>, x: Double, z: Double): Double {
        if (poly.size < 3) return Double.MAX_VALUE
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (ax, az) = poly[i]; val (bx, bz) = poly[j]
            if ((az > z) != (bz > z) && x < (bx - ax) * (z - az) / (bz - az) + ax) inside = !inside
            j = i
        }
        if (inside) return 0.0
        var best = Double.MAX_VALUE
        for (i in poly.indices) {
            val (ax, az) = poly[i]; val (bx, bz) = poly[(i + 1) % poly.size]
            val dx = bx - ax; val dz = bz - az; val l2 = dx * dx + dz * dz
            val t = if (l2 < 1e-12) 0.0 else (((x - ax) * dx + (z - az) * dz) / l2).coerceIn(0.0, 1.0)
            val px = ax + dx * t; val pz = az + dz * t
            best = min(best, sqrt((x - px) * (x - px) + (z - pz) * (z - pz)))
        }
        return best
    }
}

/** Stato del motore: tutto immutabile. `candidate` è ciò che vedrebbe l'utente ora. */
data class AssistedState internal constructor(
    val walls: List<ConfirmedWall>,
    val candidate: CandidateWall?,
    internal val track: Track?,
    internal val points: Map<Int, PointRec>,
    internal val planeSince: Map<Int, Long>,
    val lastTimeMs: Long,
) {
    companion object { fun initial() = AssistedState(emptyList(), null, null, emptyMap(), emptyMap(), Long.MIN_VALUE) }
}

internal data class Sample(val t: Long, val angleDeg: Double, val offset: Double, val speed: Double)
internal data class Pending(val line: WallLine, val key: Int, val sinceMs: Long)
internal data class Track(
    val createdMs: Long,
    val firstAimedMs: Long,
    val lastAimedMs: Long,
    val refDir: ArXZ,
    val line: WallLine,
    val keys: Set<Int>,
    val aimedKey: Int?,
    val aimDist: Double?,
    val window: List<Sample>,
    val stableSinceMs: Long?,
    val failingSinceMs: Long?,
    val state: CandidateState,
    val pending: Pending?,
    val snapshot: CandidateWall?,
    val cameraAt: ArXZ?,
)

data class StepResult(val state: AssistedState, val events: List<AssistedEvent>)

/**
 * Riduttore della scansione assistita: `step` per ogni frame (in ordine di tempo), `confirm` / `undo` / `reset` per i gesti
 * dell'utente. Nessuna parete nasce senza `confirm`.
 */
object AssistedScan {
    fun initial() = AssistedState.initial()

    private fun std(v: List<Double>): Double {
        if (v.size < 2) return 0.0
        val m = v.sum() / v.size
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }

    fun step(s: AssistedState, f: AimFrame, p: AssistedParams = AssistedParams()): StepResult {
        if (f.timeMs < s.lastTimeMs) return StepResult(s, emptyList()) // fuori ordine: chi chiama deve ordinare i frame
        val events = mutableListOf<AssistedEvent>()
        val points = if (f.points != null) s.points + f.points else s.points

        // Da quanto ogni piano verticale è seguito senza interruzioni.
        val since = HashMap<Int, Long>()
        for (pl in f.planes) if (pl.tracking) since[pl.key] = s.planeSince[pl.key] ?: f.timeMs

        val canAim = f.tracking && f.camera != null && f.forward != null
        val hit = if (canAim) Aim.select(f, p) else null
        var track = s.track
        var matched = false
        var lineNow: WallLine? = null
        var contributing: List<AimPlane> = emptyList()

        if (hit != null) {
            val ref = track?.refDir
            val own = Aim.line(hit.plane, ref)!!
            val others = f.planes.filter { it.key != hit.plane.key && it.tracking && it.subsumedBy == null }
            val otherLines = others.mapNotNull { pl -> Aim.line(pl, own.direction)?.let { pl to it } }
            val (merged, usedLines) = Aim.union(own, otherLines.map { it.second }, p)
            contributing = listOf(hit.plane) + otherLines.filter { it.second in usedLines }.map { it.first }
            when {
                track == null -> {
                    track = Track(f.timeMs, f.timeMs, f.timeMs, own.direction, merged, emptySet(), hit.plane.key, hit.t, emptyList(), null, null, CandidateState.TRACKING, null, null, null)
                    events += AssistedEvent.Appeared(f.timeMs, hit.plane.key)
                    matched = true; lineNow = merged
                }
                Aim.compatible(track.line, own, p) -> { matched = true; lineNow = merged; track = track.copy(pending = null) }
                else -> {
                    val pend = track.pending?.takeIf { Aim.compatible(it.line, own, p) } ?: Pending(own, hit.plane.key, f.timeMs)
                    if (f.timeMs - pend.sinceMs >= p.switchHoldMs) {
                        track = Track(f.timeMs, f.timeMs, f.timeMs, own.direction, merged, emptySet(), hit.plane.key, hit.t, emptyList(), null, null, CandidateState.TRACKING, null, null, null)
                        events += AssistedEvent.Switched(f.timeMs, hit.plane.key)
                        matched = true; lineNow = merged
                    } else track = track.copy(pending = pend)
                }
            }
        }

        if (track == null) return StepResult(AssistedState(s.walls, null, null, points, since, f.timeMs), events)

        if (matched) {
            val ln = Aim.oriented(lineNow!!, track.refDir)
            val angle = toDegrees(atan2(track.refDir.cross(ln.direction), track.refDir dot ln.direction))
            val keys = (contributing.map { it.key } + f.planes.filter { c -> c.subsumedBy != null && c.subsumedBy in contributing.map { it.key } }.map { it.key }).toSet()
            val sample = Sample(f.timeMs, angle, ln.offset, f.speedMps ?: 0.0)
            val window = (track.window + sample).filter { f.timeMs - it.t <= p.windowMs }
            track = track.copy(lastAimedMs = f.timeMs, line = ln, keys = keys, aimedKey = hit!!.plane.key, aimDist = hit.t, window = window, cameraAt = f.camera?.let { ArXZ(it.x, it.z) })
        } else if (f.timeMs - track.lastAimedMs > p.graceMs) {
            events += AssistedEvent.Lost(f.timeMs)
            return StepResult(AssistedState(s.walls, null, null, points, since, f.timeMs), events)
        }

        // ---- Condizioni di stabilità
        val reasons = mutableListOf<Reason>()
        val ratios = mutableListOf<Double>()
        fun ratio(v: Double, th: Double) = if (th <= 0) 1.0 else min(1.0, v / th)
        fun inv(v: Double, th: Double) = if (v <= th) 1.0 else th / v
        if (!f.tracking) { reasons += Reason.NO_TRACKING; ratios += 0.0 }
        else if (!matched) { reasons += Reason.AIM_LOST; ratios += 0.0 }
        val aimedMs = track.lastAimedMs - track.firstAimedMs
        val tracksSince = (track.keys.mapNotNull { since[it] }).minOrNull()
        val planeMs = if (tracksSince != null) f.timeMs - tracksSince else 0L
        val win = track.window
        val maxSpeed = win.maxOfOrNull { it.speed } ?: 0.0
        val hStd = std(win.map { it.angleDeg })
        val oStd = std(win.map { it.offset })
        if (aimedMs < p.minAimedMs) { reasons += Reason.AIM_SHORT; ratios += ratio(aimedMs.toDouble(), p.minAimedMs.toDouble()) } else ratios += 1.0
        if (planeMs < p.minPlaneTrackingMs) { reasons += Reason.PLANE_NEW; ratios += ratio(planeMs.toDouble(), p.minPlaneTrackingMs.toDouble()) } else ratios += 1.0
        if (win.size < p.minWindowSamples) { reasons += Reason.FEW_SAMPLES; ratios += ratio(win.size.toDouble(), p.minWindowSamples.toDouble()) } else ratios += 1.0
        if (maxSpeed > p.maxSpeedMps) { reasons += Reason.TOO_FAST; ratios += inv(maxSpeed, p.maxSpeedMps) } else ratios += 1.0
        if (hStd > p.maxHeadingStdDeg) { reasons += Reason.HEADING_UNSTABLE; ratios += inv(hStd, p.maxHeadingStdDeg) } else ratios += 1.0
        if (oStd > p.maxOffsetStdM) { reasons += Reason.OFFSET_UNSTABLE; ratios += inv(oStd, p.maxOffsetStdM) } else ratios += 1.0
        val aimedPlane = f.planes.firstOrNull { it.key == track.aimedKey }
        val tilt = aimedPlane?.let { Aim.line(it)?.tiltDeg } ?: track.line.tiltDeg
        if (tilt > p.maxTiltDeg) { reasons += Reason.TILTED; ratios += inv(tilt, p.maxTiltDeg) } else ratios += 1.0
        if (track.line.length < p.minLengthM) { reasons += Reason.SHORT; ratios += ratio(track.line.length, p.minLengthM) } else ratios += 1.0
        if (track.line.heightM < p.minHeightM) { reasons += Reason.LOW; ratios += ratio(track.line.heightM, p.minHeightM) } else ratios += 1.0
        val ok = reasons.isEmpty()
        val stability = ratios.minOrNull() ?: 0.0

        // ---- Transizioni
        var state = track.state
        var stableSince = track.stableSinceMs
        var failingSince = track.failingSinceMs
        if (ok) {
            failingSince = null
            if (stableSince == null) stableSince = f.timeMs
            if (state != CandidateState.PROPOSED && f.timeMs - stableSince >= p.proposeHoldMs) {
                state = CandidateState.PROPOSED
                events += AssistedEvent.Proposed(f.timeMs, f.timeMs - track.createdMs)
            }
        } else {
            if (state == CandidateState.PROPOSED) {
                if (failingSince == null) failingSince = f.timeMs
                if (f.timeMs - failingSince > p.unproposeHoldMs) {
                    state = CandidateState.TRACKING; stableSince = null; failingSince = null
                    events += AssistedEvent.Unproposed(f.timeMs, reasons.toList())
                }
            } else stableSince = null
        }

        val evidence = evidence(track.line, points, p)
        val advisories = advisories(track.line, evidence, track.aimDist, f.floorY, track.cameraAt, s.walls, p)
        val dup = s.walls.firstOrNull { w -> Aim.compatible(w.line, track.line, p) }?.id
        val cand = CandidateWall(
            state, track.keys.sorted(), track.line, track.aimedKey, track.aimDist, f.timeMs - track.createdMs, aimedMs, planeMs, maxSpeed, hStd, oStd, win.size,
            evidence, stability, reasons.toList(), advisories, dup,
        )
        val snapshot = if (ok) cand else track.snapshot
        track = track.copy(state = state, stableSinceMs = stableSince, failingSinceMs = failingSince, snapshot = snapshot)
        return StepResult(AssistedState(s.walls, cand, track, points, since, f.timeMs), events)
    }

    /** Conferma: solo se c'è una proposta e non è un doppione di una parete già acquisita. La parete è l'istantanea CONGELATA. */
    fun confirm(s: AssistedState, timeMs: Long, p: AssistedParams = AssistedParams()): StepResult {
        val t = s.track; val c = s.candidate
        if (t == null || c == null || c.state != CandidateState.PROPOSED) return StepResult(s, listOf(AssistedEvent.Rejected(timeMs, "nessuna parete proposta")))
        c.duplicateOf?.let { return StepResult(s, listOf(AssistedEvent.Rejected(timeMs, "già acquisita (parete $it)"))) }
        val snap = t.snapshot ?: c
        val q = quality(snap, p)
        val line = snap.line
        val wall = ConfirmedWall(
            id = s.walls.size + 1, line = line, normalIn = inward(line, t.cameraAt), observedLengthM = line.length,
            floorGapM = null, qualityScore = q.first, quality = q.second, rmsM = snap.evidence.rmsM, pointSupport = snap.evidence.support,
            planeKeys = snap.planeKeys, confirmedAtMs = timeMs, cameraAt = t.cameraAt, advisories = snap.advisories,
        )
        return StepResult(s.copy(walls = s.walls + wall, candidate = null, track = null), listOf(AssistedEvent.Confirmed(timeMs, wall.id)))
    }

    fun undo(s: AssistedState, timeMs: Long): StepResult {
        val last = s.walls.lastOrNull() ?: return StepResult(s, listOf(AssistedEvent.Rejected(timeMs, "nessuna parete da annullare")))
        return StepResult(s.copy(walls = s.walls.dropLast(1), candidate = null, track = null), listOf(AssistedEvent.Undone(timeMs, last.id)))
    }

    fun reset(s: AssistedState): AssistedState = AssistedState.initial().copy(points = s.points)

    private fun inward(line: WallLine, cam: ArXZ?): ArXZ {
        if (cam == null) return line.normal
        return if ((cam - line.a) dot line.normal >= 0) line.normal else line.normal * -1.0
    }

    /** Evidenza dai punti vicini alla linea: SOLO controllo qualità, la linea non si sposta. */
    fun evidence(line: WallLine, points: Map<Int, PointRec>, p: AssistedParams): PointEvidence {
        if (points.isEmpty()) return PointEvidence(0, null, null, false)
        var n = 0; var sq = 0.0; var sum = 0.0
        val len = line.length
        for (pt in points.values) {
            if (pt.confidence < p.pointMinConfidence) continue
            val rx = pt.x - line.a.x; val rz = pt.z - line.a.z
            val along = rx * line.direction.x + rz * line.direction.z
            val perp = rx * line.normal.x + rz * line.normal.z
            if (abs(perp) <= p.pointSupportTolM && along >= -0.2 && along <= len + 0.2 && pt.y >= line.minY - 0.1 && pt.y <= line.maxY + 0.1) { n++; sq += perp * perp; sum += perp }
        }
        return if (n == 0) PointEvidence(0, null, null, true) else PointEvidence(n, sqrt(sq / n), sum / n, true)
    }

    private fun advisories(line: WallLine, ev: PointEvidence, aimDist: Double?, floorY: Double?, cam: ArXZ?, walls: List<ConfirmedWall>, p: AssistedParams): List<Advisory> {
        val out = mutableListOf<Advisory>()
        if (floorY != null && line.minY - floorY > p.floorGapAdvisoryM) out += Advisory.NOT_REACHING_FLOOR
        if (cam != null) {
            // Una parete confermata quasi parallela, dietro questa (più lontana dal telefono) e sovrapposta.
            val behind = walls.any { w ->
                if (Aim.angleBetween(w.line, line) > p.sameAngleTolDeg) return@any false
                val dCand = abs((cam - line.a).cross(line.direction)); val dWall = abs((cam - w.line.a).cross(w.line.direction))
                val gap = dWall - dCand
                val t0 = (w.line.a - line.a) dot line.direction; val t1 = (w.line.b - line.a) dot line.direction
                val overlap = min(max(t0, t1), line.length) - max(min(t0, t1), 0.0)
                gap > p.sameOffsetTolM && gap <= p.behindMaxM && overlap > 0.3
            }
            if (behind) out += Advisory.PARALLEL_BEHIND
        }
        if (ev.available && (ev.support < p.minPointSupport)) out += Advisory.FEW_POINTS
        if (ev.support >= p.minPointSupport && ((ev.rmsM ?: 0.0) > p.maxPointRmsM || abs(ev.biasM ?: 0.0) > p.maxPointBiasM)) out += Advisory.POINTS_DISAGREE
        if (aimDist != null && aimDist > p.farAimM) out += Advisory.FAR
        return out
    }

    /** Qualità (0..1 e classe) dall'evidenza disponibile: lunghezza, altezza, stabilità, punti, persistenza del piano. */
    private fun quality(c: CandidateWall, p: AssistedParams): Pair<Double, Quality> {
        val parts = mutableListOf<Double>()
        parts += min(1.0, c.line.length / 3.0)
        parts += min(1.0, c.line.heightM / 1.5)
        parts += 1.0 - 0.5 * min(1.0, c.headingStdDeg / p.maxHeadingStdDeg) - 0.5 * min(1.0, c.offsetStdM / p.maxOffsetStdM)
        if (c.evidence.available) parts += min(1.0, c.evidence.support / 50.0) * (if ((c.evidence.rmsM ?: 0.0) > p.maxPointRmsM) 0.5 else 1.0)
        parts += min(1.0, c.planeTrackingMs / 5000.0)
        val score = parts.sum() / parts.size
        return score to (if (score >= 0.75) Quality.HIGH else if (score >= 0.5) Quality.MEDIUM else Quality.LOW)
    }
}
