package com.sagoma.planimetria.scan.reconstruction.rescan

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.GapReason
import com.sagoma.planimetria.scan.reconstruction.Orientation
import com.sagoma.planimetria.scan.reconstruction.PerimeterResult
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.SurfaceAmbiguity
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import com.sagoma.planimetria.scan.reconstruction.WallEstimationResult
import com.sagoma.planimetria.scan.reconstruction.quality.QualityLevel
import com.sagoma.planimetria.scan.reconstruction.quality.QualityResult
import kotlin.math.hypot

/**
 * M5 — decisione di nuova acquisizione. Funzione pura e deterministica di M4 (difetti e stati) e delle coordinate di R2/R3/R4.
 * Regole (approvate): gravità BLOCKING (perimetro non chiuso, estremità PARTIAL/UNCERTAIN e tratti non osservati ≥ 20 cm delle
 * pareti della stanza), IMPORTANT (meno di 2 viste, misura LOW nelle pareti della stanza), SECONDARY (ambiguità instabile, pareti
 * fuori dalla stanza). Priorità: gravità → tipo → lunghezza non osservata decrescente → parete. Stop: NO_RESCAN_NEEDED se non ci
 * sono richieste BLOCKING o IMPORTANT. Più cause nello stesso punto = una sola richiesta con le altre in contributingReasons.
 */
object RescanDirector {

    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, ',')

    /** I testi di R4 citano le etichette descrittive di R3 (es. per OCCLUDED): M5 usa il nome neutro dello stato, senza semantica. */
    private fun neutral(s: String) = EndReason.values().fold(s) { acc, r -> acc.replace(r.label, r.name) }

    /** Geometria di R4 così com'è: stato (come lo legge M4), lato mancante del perimetro principale, tratti non osservati. */
    fun perimeterGeometry(r4: PerimeterResult?): PerimeterGeometry =
        PerimeterGeometry(r4?.state, r4?.main?.missing, r4?.perimeters.orEmpty().flatMap { it.unobserved }, r4?.main?.reasons.orEmpty())

    /** Dai risultati della pipeline e di M4. */
    fun plan(surfaces: SurfaceResult, walls: WallEstimationResult, r4: PerimeterResult?, quality: QualityResult, config: RescanConfig = RescanConfig()): RescanPlan =
        plan(
            quality, walls.walls, perimeterGeometry(r4), surfaces.ambiguity,
            surfaces.surfaces.filter { it.orientation == Orientation.VERTICAL }.associate { it.id to SurfacePlan(it.plane.centroid[0], it.plane.centroid[2], it.u[0], it.u[2], it.uMin, it.uMax) },
            config,
        )

    private class Draft(
        val key: String, var type: RescanRequestType, var severity: Severity, val wallId: Int?, val inRoom: Boolean,
        var target: RescanTarget, val lookX: Double?, val lookZ: Double?, var observation: ObservationKind, var sourceReason: String?,
        var primary: String, val contributing: MutableList<String> = mutableListOf(), var unobserved: Double? = null,
    )

    private fun typeOrder(t: RescanRequestType) = when (t) {
        RescanRequestType.RESCAN_OPEN_PERIMETER, RescanRequestType.RESCAN_UNCERTAIN_PERIMETER -> 0
        RescanRequestType.RESCAN_WALL_START, RescanRequestType.RESCAN_WALL_END -> 1
        RescanRequestType.RESCAN_WALL_BODY, RescanRequestType.RESCAN_UNOBSERVED_REGION -> 2
        RescanRequestType.RESCAN_LOW_VIEW_COUNT -> 3
        RescanRequestType.RESCAN_WALL_MEASUREMENT -> 4
        RescanRequestType.RESCAN_HIGH_AMBIGUITY -> 5
    }

    private fun endObservation(r: EndReason) = when (r) {
        EndReason.OCCLUDED -> ObservationKind.OBLIQUE_PAST_OCCLUSION
        EndReason.NOT_SEEN -> ObservationKind.FRAME_BEYOND_END
        EndReason.UNSTABLE -> ObservationKind.MORE_VIEWS_OF_END
        EndReason.SURFACE_NEAR, EndReason.CORNER -> ObservationKind.FRAME_THE_CORNER
        EndReason.FREE_BEYOND -> ObservationKind.FRAME_BEYOND_END
    }

    /** Valutazione da dati già estratti (usata anche dai test con ingressi costruiti). */
    fun plan(
        quality: QualityResult, walls: List<EstimatedWall>, perimeter: PerimeterGeometry, ambiguity: Map<Int, SurfaceAmbiguity>,
        surfacePlans: Map<Int, SurfacePlan>, config: RescanConfig = RescanConfig(),
    ): RescanPlan {
        val drafts = LinkedHashMap<String, Draft>()
        val limitations = mutableListOf<RescanLimitation>()
        val geo = walls.associateBy { it.id }

        // 1. Perimetro (stato come lo legge M4, localizzazione dal lato mancante di R4).
        val pState = quality.room.perimeterState
        if (pState != PerimeterState.CLOSED) {
            val m = perimeter.missing
            val type = if (pState == PerimeterState.UNCERTAIN) RescanRequestType.RESCAN_UNCERTAIN_PERIMETER else RescanRequestType.RESCAN_OPEN_PERIMETER
            val target = if (m != null) RescanTarget(TargetKind.SEGMENT, m.fromX, m.fromZ, m.toX, m.toZ, "R4 lato mancante W${m.afterWall} → W${m.beforeWall}")
                else RescanTarget(TargetKind.NONE, source = "R4 non indica un lato mancante: posizione non determinabile")
            val obs = when { m != null -> ObservationKind.CLOSE_MISSING_SIDE; pState == PerimeterState.UNCERTAIN -> ObservationKind.REVIEW_PERIMETER; else -> ObservationKind.REVIEW_PERIMETER }
            val why = when (pState) {
                null -> "nessun perimetro R4"
                PerimeterState.OPEN -> "perimetro R4 OPEN: nessun collegamento tra pareti"
                PerimeterState.PARTIAL -> "perimetro R4 PARTIAL: catena aperta"
                PerimeterState.UNCERTAIN -> "perimetro R4 UNCERTAIN: anello con condizioni non verificate"
                PerimeterState.CLOSED -> ""
            }
            drafts["ROOM-PERIMETER"] = Draft("ROOM-PERIMETER", type, Severity.BLOCKING, null, true, target, null, null, obs, m?.reason?.let { neutral(it) }, why).also { d ->
                if (m != null) d.unobserved = hypot(m.toX - m.fromX, m.toZ - m.fromZ)
                perimeter.reasons.take(3).forEach { d.contributing.add("R4: ${neutral(it)}") }
            }
        }
        // Le estremità che delimitano il lato mancante sono la STESSA causa: confluiscono nella richiesta del perimetro.
        val absorbed = perimeter.missing?.let { setOf("W${it.afterWall}-END", "W${it.beforeWall}-START") }.orEmpty()

        for (q in quality.walls.sortedBy { it.wallId }) {
            val g = geo[q.wallId] ?: continue
            val gm = g.geometry
            val look = -gm.nx to -gm.nz
            val blocking = if (q.inRoom) Severity.BLOCKING else Severity.SECONDARY
            val important = if (q.inRoom) Severity.IMPORTANT else Severity.SECONDARY
            fun pt(u: Double) = gm.pointAt(u)

            // 2. Estremità: stato non OBSERVED o prolungamento dedotto da R4 ≥ soglia.
            for (isStart in listOf(true, false)) {
                val st = if (isStart) q.completeness.startState else q.completeness.endState
                val rs = if (isStart) q.completeness.startReason else q.completeness.endReason
                val kind = if (isStart) "prolungamento dall'angolo" else "prolungamento fino all'angolo"
                val ext = perimeter.stretches.firstOrNull { it.wallId == q.wallId && it.kind == kind }
                val extLong = ext != null && ext.lengthM >= config.minUnobservedM
                if (st == EndState.OBSERVED && !extLong) continue
                val key = "W${q.wallId}-${if (isStart) "START" else "END"}"
                val u = if (isStart) gm.startU else gm.endU
                val target = if (ext != null) RescanTarget(TargetKind.SEGMENT, ext.ax, ext.az, ext.bx, ext.bz, "R4 $kind di W${q.wallId}")
                    else pt(u).let { RescanTarget(TargetKind.POINT, it[0], it[1], source = "R3 W${q.wallId} ${if (isStart) "start" else "end"}") }
                val reason = if (st != EndState.OBSERVED) "estremità ${if (isStart) "iniziale" else "finale"} $st (${rs.name})" else "prolungamento dedotto da R4 non osservato (${f(ext!!.lengthM)} m)"
                val d = Draft(key, if (isStart) RescanRequestType.RESCAN_WALL_START else RescanRequestType.RESCAN_WALL_END, blocking, q.wallId, q.inRoom, target, look.first, look.second,
                    if (st != EndState.OBSERVED) endObservation(rs) else ObservationKind.FRAME_THE_CORNER, rs.name, reason)
                if (ext != null) { d.unobserved = ext.lengthM; if (st != EndState.OBSERVED) d.contributing.add("prolungamento dedotto da R4 non osservato (${f(ext.lengthM)} m)") }
                drafts[key] = d
            }

            // 3. Tratti interni non osservati (R3) ≥ soglia: vicini a un'estremità con richiesta → stessa causa.
            for ((i, gap) in g.gaps.withIndex()) {
                if (gap.lengthM < config.minUnobservedM) continue
                val reason = "tratto non osservato di ${f(gap.lengthM)} m (${gap.reason.name})"
                val nearStart = gap.fromU - gm.startU <= config.endAdjacencyM; val nearEnd = gm.endU - gap.toU <= config.endAdjacencyM
                val endKey = when { nearStart && drafts.containsKey("W${q.wallId}-START") -> "W${q.wallId}-START"; nearEnd && drafts.containsKey("W${q.wallId}-END") -> "W${q.wallId}-END"; else -> null }
                if (endKey != null) { drafts.getValue(endKey).let { it.contributing.add(reason); it.unobserved = (it.unobserved ?: 0.0) + gap.lengthM }; continue }
                val a = pt(gap.fromU); val b = pt(gap.toU)
                drafts["W${q.wallId}-BODY-$i"] = Draft("W${q.wallId}-BODY-$i", RescanRequestType.RESCAN_WALL_BODY, blocking, q.wallId, q.inRoom,
                    RescanTarget(TargetKind.SEGMENT, a[0], a[1], b[0], b[1], "R3 W${q.wallId} gap ${i + 1}"), look.first, look.second,
                    if (gap.reason == GapReason.OCCLUDED) ObservationKind.OBLIQUE_PAST_OCCLUSION else ObservationKind.FRAME_SEGMENT, gap.reason.name, reason).also { it.unobserved = gap.lengthM }
            }

            // 4. Gap tra frammenti (R4) ≥ soglia: con l'END della parete se esiste, altrimenti regione non osservata.
            for ((i, s) in perimeter.stretches.filter { it.wallId == q.wallId && it.kind.startsWith("gap tra frammenti") }.withIndex()) {
                if (s.lengthM < config.minUnobservedM) continue
                val reason = "${s.kind}: ${f(s.lengthM)} m non osservati"
                drafts["W${q.wallId}-END"]?.let { it.contributing.add(reason); it.unobserved = (it.unobserved ?: 0.0) + s.lengthM } ?: run {
                    drafts["W${q.wallId}-REGION-$i"] = Draft("W${q.wallId}-REGION-$i", RescanRequestType.RESCAN_UNOBSERVED_REGION, blocking, q.wallId, q.inRoom,
                        RescanTarget(TargetKind.SEGMENT, s.ax, s.az, s.bx, s.bz, "R4 ${s.kind}"), look.first, look.second, ObservationKind.FRAME_SEGMENT, s.reason, reason).also { it.unobserved = s.lengthM }
                }
            }

            // 5. Viste e misura: una sola richiesta sulla parete (la misura confluisce nelle viste se entrambe).
            val ext0 = pt(gm.startU); val ext1 = pt(gm.endU)
            val whole = RescanTarget(TargetKind.SEGMENT, ext0[0], ext0[1], ext1[0], ext1[1], "R3 estensione di W${q.wallId}")
            val measReason = "misura ${q.measurement.level}: σ posizione ${f(q.measurement.positionSigmaM * 100)} cm, σ direzione ${f(q.measurement.headingSigmaDeg)}°"
            if (q.evidence.viewCount < config.minViews) {
                drafts["W${q.wallId}-VIEWS"] = Draft("W${q.wallId}-VIEWS", RescanRequestType.RESCAN_LOW_VIEW_COUNT, important, q.wallId, q.inRoom, whole, look.first, look.second,
                    ObservationKind.ADDITIONAL_VIEWPOINT, null, "${q.evidence.viewCount} viste indipendenti (minimo ${config.minViews})").also { if (q.measurement.level == QualityLevel.LOW) it.contributing.add(measReason) }
            } else if (q.measurement.level == QualityLevel.LOW) {
                drafts["W${q.wallId}-MEASUREMENT"] = Draft("W${q.wallId}-MEASUREMENT", RescanRequestType.RESCAN_WALL_MEASUREMENT, important, q.wallId, q.inRoom, whole, look.first, look.second,
                    ObservationKind.ADDITIONAL_VIEWPOINT, null, measReason)
            }

            // 6. Ambiguità: richiesta solo se ≥ τ e INSTABILE; stabile → limite (altre viste non la risolvono).
            val a = q.ambiguity
            if (a.maxScore != null && a.maxScore >= config.ambiguityTau) {
                val sid = a.sourceSurfaceId
                if ((a.sourceSurfaceStability ?: 0.0) < config.ambiguityTau) {
                    drafts["W${q.wallId}-AMBIGUITY"] = Draft("W${q.wallId}-AMBIGUITY", RescanRequestType.RESCAN_HIGH_AMBIGUITY, Severity.SECONDARY, q.wallId, q.inRoom,
                        ambiguityTarget(sid, ambiguity[sid ?: -1], surfacePlans[sid ?: -1]), look.first, look.second, ObservationKind.VIEW_AMBIGUITY_ZONE, null,
                        "evidenza di superficie alternativa ${f(a.maxScore, 3)} (S$sid) non stabile togliendo una vista (stabilità ${f(a.sourceSurfaceStability ?: 0.0, 3)})")
                } else limitations.add(RescanLimitation(q.wallId, "STABLE_ALTERNATIVE_SURFACE_EVIDENCE",
                    "W${q.wallId}: evidenza di superficie alternativa ${f(a.maxScore, 3)} (S$sid) stabile tra le viste: altre viste non la risolvono (audit occlusione); nessuna richiesta"))
            }
        }
        // Due prolungamenti R4 che convergono nello stesso angolo dedotto (fine di Wa, inizio di Wb) sono la STESSA causa: una sola
        // richiesta, bersaglio dall'ultima estremità osservata di Wa alla prima di Wb (coordinate di R4), le due cause in contributing.
        val ends = perimeter.stretches.filter { it.kind == "prolungamento fino all'angolo" }
        val starts = perimeter.stretches.filter { it.kind == "prolungamento dall'angolo" }
        for (sa in ends.sortedBy { it.wallId }) {
            val sb = starts.filter { it.ax == sa.bx && it.az == sa.bz }.minByOrNull { it.wallId } ?: continue
            val da = drafts["W${sa.wallId}-END"] ?: continue
            val db = drafts["W${sb.wallId}-START"] ?: continue
            val dom = if ((db.unobserved ?: 0.0) > (da.unobserved ?: 0.0)) db else da
            val other = if (dom === da) db else da
            val key = "CORNER-W${sa.wallId}-W${sb.wallId}"
            drafts[key] = Draft(
                key, dom.type, if (da.severity.ordinal <= db.severity.ordinal) da.severity else db.severity, dom.wallId, da.inRoom || db.inRoom,
                RescanTarget(TargetKind.SEGMENT, sa.ax, sa.az, sb.bx, sb.bz, "R4 angolo dedotto tra W${sa.wallId} e W${sb.wallId} (${f(sa.bx)}, ${f(sa.bz)}): prolungamenti non osservati"),
                null, null, dom.observation, dom.sourceReason,
                "angolo tra W${sa.wallId} e W${sb.wallId} non osservato: ${f(sa.lengthM + sb.lengthM)} m dedotti da R4",
            ).also { d ->
                d.unobserved = (da.unobserved ?: 0.0) + (db.unobserved ?: 0.0)
                for (x in listOf(dom, other)) { d.contributing.add("W${x.wallId}: ${x.primary}"); x.contributing.forEach { c -> d.contributing.add("W${x.wallId}: $c") } }
                d.contributing.add("osservazione per W${other.wallId}: ${other.observation.label}")
            }
            drafts.remove(da.key); drafts.remove(db.key)
        }
        for (k in absorbed) drafts.remove(k)?.let { d -> drafts["ROOM-PERIMETER"]?.contributing?.add("W${d.wallId}: ${d.primary}"); d.contributing.forEach { c -> drafts["ROOM-PERIMETER"]?.contributing?.add("W${d.wallId}: $c") } }

        // 7. Pareti fuori dalla stanza: una sola richiesta SECONDARY per parete (la causa dominante, le altre in contributing).
        val merged = mutableListOf<Draft>()
        for ((wid, group) in drafts.values.filter { !it.inRoom && it.wallId != null }.groupBy { it.wallId!! }.entries.sortedBy { it.key }) {
            val sorted = group.sortedWith(compareBy<Draft>({ typeOrder(it.type) }, { -(it.unobserved ?: 0.0) }, { it.key }))
            val top = sorted.first()
            sorted.drop(1).forEach { o -> top.contributing.add(o.primary); top.contributing.addAll(o.contributing) }
            top.contributing.add(0, "parete fuori dalla stanza (W$wid): gravità secondaria")
            merged.add(top)
        }
        val all = drafts.values.filter { it.inRoom || it.wallId == null } + merged

        val ordered = all.sortedWith(compareBy<Draft>({ it.severity.ordinal }, { typeOrder(it.type) }, { -(it.unobserved ?: 0.0) }, { it.wallId ?: -1 }, { it.key }))
        val requests = ordered.mapIndexed { i, d ->
            RescanRequest(
                d.key, d.type, d.severity, d.wallId, d.inRoom, d.target, d.lookX, d.lookZ, d.observation, d.sourceReason, d.primary, d.contributing.toList(), d.unobserved,
                visibilityVerified = false, priorityRank = i + 1,
                priorityExplanation = "gravità ${d.severity} · tipo ${d.type} (ordine ${typeOrder(d.type)}) · non osservato ${d.unobserved?.let { f(it) + " m" } ?: "—"} · ${d.wallId?.let { "W$it" } ?: "stanza"}",
            )
        }
        val blocking = requests.count { it.severity == Severity.BLOCKING }; val important = requests.count { it.severity == Severity.IMPORTANT }
        val decision = if (blocking + important > 0) RescanDecision.RESCAN_RECOMMENDED else RescanDecision.NO_RESCAN_NEEDED
        val reason = if (decision == RescanDecision.NO_RESCAN_NEEDED)
            "nessuna richiesta bloccante o importante: l'informazione è sufficiente per le soglie di prodotto (${requests.size} richieste secondarie e ${limitations.size} limiti solo riportati)"
        else "$blocking richieste bloccanti, $important importanti, ${requests.size - blocking - important} secondarie"
        return RescanPlan(config, decision, requests, limitations.sortedWith(compareBy({ it.wallId ?: -1 }, { it.code })), reason)
    }

    /** Zona della banda R2.1 con lo score massimo (sinistra/destra/sopra/dietro), in pianta. Senza dati: posizione non determinabile. */
    private fun ambiguityTarget(sid: Int?, a: SurfaceAmbiguity?, p: SurfacePlan?): RescanTarget {
        if (sid == null || a == null || p == null) return RescanTarget(TargetKind.NONE, source = "superficie di origine non disponibile: posizione non determinabile")
        val ch = listOf("E5 sinistra" to a.left, "E5 destra" to a.right, "E4 sopra" to a.above, "E1 dietro" to a.behind).maxWithOrNull(compareBy<Pair<String, com.sagoma.planimetria.scan.reconstruction.AmbiguityChannel>> { it.second.score }.thenByDescending { it.first })!!
        val (u0, u1) = when (ch.first) {
            "E5 sinistra" -> (p.uMin - 0.5) to (p.uMin - 0.05)
            "E5 destra" -> (p.uMax + 0.05) to (p.uMax + 0.5)
            else -> p.uMin to p.uMax
        }
        return RescanTarget(TargetKind.SEGMENT, p.cx + p.ux * u0, p.cz + p.uz * u0, p.cx + p.ux * u1, p.cz + p.uz * u1, "R2.1 banda ${ch.first} di S$sid")
    }
}
