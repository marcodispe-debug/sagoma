package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.recording.ScanRecording
import com.sagoma.planimetria.scan.recording.analysis.Dist
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Una riga della simulazione: lo stato della candidata in un frame. */
data class SimRow(
    val frameIndex: Int,
    val timeMs: Long,
    val tracking: Boolean,
    val aimedKey: Int?,
    val aimDistM: Double?,
    val hit: ArXZ?,
    val state: CandidateState?,
    val stability: Double?,
    val aimedMs: Long?,
    val planeMs: Long?,
    val speedMps: Double?,
    val headingStdDeg: Double?,
    val offsetStdM: Double?,
    val lengthM: Double?,
    val heightM: Double?,
    val pointSupport: Int?,
    val rmsM: Double?,
    val biasM: Double?,
    val reasons: List<Reason>,
    val advisories: List<Advisory>,
)

/** Un periodo continuo in cui la candidata è stata `Proposta`. */
data class Episode(
    val startMs: Long,
    val endMs: Long,
    /** Tempo dalla comparsa della candidata alla proposta. */
    val timeToProposeMs: Long,
    val planeKeys: List<Int>,
    val lineAtStart: WallLine,
    val lineAtEnd: WallLine,
    val advisories: List<Advisory>,
    val duplicateOfConfirmed: Int?,
)

data class SimPass(
    val rows: List<SimRow>,
    val events: List<AssistedEvent>,
    val episodes: List<Episode>,
    val confirmed: List<ConfirmedWall>,
    val rejected: Int,
)

data class SimResult(
    val frames: Int,
    val durationMs: Long,
    val trackingFrames: Int,
    val aimValidFrames: Int,
    /** Diagnostica della convenzione di mira: frame in cui il raggio colpisce un piano guardando avanti (−Z) e guardando indietro (+Z). */
    val aimForwardFrames: Int,
    val aimBackwardFrames: Int,
    val planesAimed: List<Pair<Int, Int>>,
    val aimRunsMs: Dist,
    val speed: Dist,
    val noConfirm: SimPass,
    val autoConfirm: SimPass,
    val distinctProposedWalls: Int,
    val params: AssistedParams,
)

/**
 * Simulazione OFFLINE su una registrazione: rifà girare il motore frame per frame come farebbe il telefono, con la mira presa
 * dalla direzione della camera registrata. Passo 1: nessuna conferma (quante proposte, quando, perché no). Passo 2: politica di
 * SOLA SIMULAZIONE "conferma subito ogni proposta non duplicata", per vedere quante pareti distinte si acquisirebbero. Non è
 * il comportamento del prodotto: lì la conferma è dell'utente.
 */
object AssistedSimulation {
    fun run(recording: ScanRecording, params: AssistedParams = AssistedParams()): SimResult = run(AimFrames.from(recording), params)

    fun run(frames: List<AimFrame>, params: AssistedParams = AssistedParams()): SimResult {
        val sorted = frames.sortedWith(compareBy({ it.timeMs }, { it.index }))
        val one = pass(sorted, params, autoConfirm = false)
        val two = pass(sorted, params, autoConfirm = true)
        var fwd = 0; var back = 0
        for (f in sorted) {
            if (!f.tracking || f.camera == null || f.forward == null) continue
            if (Aim.select(f, params) != null) fwd++
            if (Aim.select(f.copy(forward = f.forward.let { com.sagoma.planimetria.scan.ArPoint(-it.x, -it.y, -it.z) }), params) != null) back++
        }
        val aimed = one.rows.filter { it.aimedKey != null }
        val perPlane = aimed.groupingBy { it.aimedKey!! }.eachCount().entries.sortedBy { it.key }.map { it.key to it.value }
        // Periodi continui di mira.
        val runs = ArrayList<Double>()
        var start: Long? = null; var last = 0L
        for (r in one.rows) {
            if (r.aimedKey != null) { if (start == null) start = r.timeMs; last = r.timeMs } else if (start != null) { runs += (last - start).toDouble(); start = null }
        }
        if (start != null) runs += (last - start).toDouble()
        // Pareti distinte tra le proposte (stessa parete se linee compatibili).
        val groups = ArrayList<WallLine>()
        for (e in one.episodes) if (groups.none { Aim.compatible(it, e.lineAtStart, params) }) groups += e.lineAtStart
        return SimResult(
            sorted.size, (sorted.lastOrNull()?.timeMs ?: 0L) - (sorted.firstOrNull()?.timeMs ?: 0L), sorted.count { it.tracking }, aimed.size, fwd, back,
            perPlane, Dist.of(runs), Dist.of(sorted.mapNotNull { it.speedMps }), one, two, groups.size, params,
        )
    }

    private fun pass(frames: List<AimFrame>, p: AssistedParams, autoConfirm: Boolean): SimPass {
        var s = AssistedScan.initial()
        val rows = ArrayList<SimRow>(frames.size)
        val events = ArrayList<AssistedEvent>()
        val episodes = ArrayList<Episode>()
        var rejected = 0
        var open: Triple<Long, Long, Pair<List<Int>, WallLine>>? = null // start, tempo alla proposta, (chiavi, linea)
        var openAdvisories: List<Advisory> = emptyList(); var openDup: Int? = null
        var lastLine: WallLine? = null; var lastEnd = 0L
        fun closeEpisode() {
            val o = open ?: return
            episodes += Episode(o.first, lastEnd, o.second, o.third.first, o.third.second, lastLine ?: o.third.second, openAdvisories, openDup)
            open = null
        }
        for (f in frames) {
            val r = AssistedScan.step(s, f, p)
            s = r.state
            events += r.events
            var cand = s.candidate
            if (autoConfirm && cand != null && cand.state == CandidateState.PROPOSED && cand.duplicateOf == null) {
                val c = AssistedScan.confirm(s, f.timeMs, p)
                events += c.events
                if (c.events.any { it is AssistedEvent.Rejected }) rejected++
                s = c.state
                cand = s.candidate
            }
            val hitPoint = if (f.tracking && f.camera != null && f.forward != null) Aim.select(f, p)?.point?.let { ArXZ(it.x, it.z) } else null
            val aimedKey = if (hitPoint != null) Aim.select(f, p)?.plane?.key else null
            rows += SimRow(
                f.index, f.timeMs, f.tracking, aimedKey, cand?.aimDistanceM, hitPoint, cand?.state, cand?.stability, cand?.aimedMs, cand?.planeTrackingMs, cand?.maxSpeedMps,
                cand?.headingStdDeg, cand?.offsetStdM, cand?.line?.length, cand?.line?.heightM, cand?.evidence?.support, cand?.evidence?.rmsM, cand?.evidence?.biasM,
                cand?.reasons.orEmpty(), cand?.advisories.orEmpty(),
            )
            val proposed = cand?.state == CandidateState.PROPOSED
            if (proposed) {
                if (open == null) {
                    val age = r.events.filterIsInstance<AssistedEvent.Proposed>().firstOrNull()?.ageMs ?: cand!!.candidateAgeMs
                    open = Triple(f.timeMs, age, cand!!.planeKeys to cand.line)
                }
                lastLine = cand!!.line; lastEnd = f.timeMs; openAdvisories = cand.advisories; openDup = cand.duplicateOf
            } else closeEpisode()
        }
        closeEpisode()
        return SimPass(rows, events, episodes, s.walls, rejected)
    }
}

/** Report testuale, CSV e SVG di una simulazione. Deterministici. */
object AssistedSimulationReport {
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun s(ms: Long) = f(ms / 1000.0, 1) + " s"
    private fun dist(d: Dist, unit: String, decimals: Int = 2) =
        if (d.count == 0) "nessun dato" else "n=${d.count} · min ${f(d.min, decimals)} · mediana ${f(d.median, decimals)} · media ${f(d.mean, decimals)} · p90 ${f(d.p90, decimals)} · max ${f(d.max, decimals)} $unit"

    fun text(r: SimResult, title: String): String = buildString {
        fun line(t: String = "") = append(t).append('\n')
        val rows = r.noConfirm.rows
        line("=== Simulazione M2.0 della scansione assistita — $title ===")
        line("La mira è la direzione della camera registrata (asse −Z della posa orientata come lo schermo). Nessuna conferma dell'utente è simulata nel passo 1.")
        line()
        line("-- 1. Frame e mira")
        line("Frame analizzati: ${r.frames} (con tracking: ${r.trackingFrames}) · durata ${s(r.durationMs)}")
        line("Frame con mira valida (il raggio colpisce un piano verticale): ${r.aimValidFrames} (${pctOf(r.aimValidFrames, r.frames)})")
        line("Controllo della convenzione di mira: colpisce un piano guardando avanti in ${r.aimForwardFrames} frame, guardando indietro in ${r.aimBackwardFrames}" +
            if (r.aimBackwardFrames > r.aimForwardFrames) "  ← ATTENZIONE: più colpi 'indietro' che 'avanti': la direzione di mira potrebbe essere invertita." else "")
        line("Piani mirati (chiave: frame): " + (if (r.planesAimed.isEmpty()) "nessuno" else r.planesAimed.joinToString(", ") { "#${it.first}: ${it.second}" }))
        line("Durata dei periodi continui di mira: ${dist(r.aimRunsMs.let { d -> Dist(d.count, d.min / 1000, d.max / 1000, d.mean / 1000, d.median / 1000, d.p10 / 1000, d.p90 / 1000) }, "s", 1)}")
        line("Velocità della camera: ${dist(r.speed, "m/s")}")
        line()
        line("-- 2. Stabilità della candidata (solo frame con candidata)")
        val c = rows.filter { it.state != null }
        line("Frame con candidata: ${c.size} · cerca: ${rows.count { it.state == null }} · stabilizzo: ${c.count { it.state == CandidateState.TRACKING }} · proposta: ${c.count { it.state == CandidateState.PROPOSED }}")
        line("Stabilità di direzione (deviazione standard, gradi): ${dist(Dist.of(c.mapNotNull { it.headingStdDeg }), "°")}")
        line("Stabilità di posizione (deviazione standard, cm): ${dist(Dist.of(c.mapNotNull { it.offsetStdM?.times(100) }), "cm")}")
        line("Lunghezza osservata (m): ${dist(Dist.of(c.mapNotNull { it.lengthM }), "m")}")
        line("Altezza osservata (m): ${dist(Dist.of(c.mapNotNull { it.heightM }), "m")}")
        line("Punti di supporto (entro ${f(r.params.pointSupportTolM * 100, 0)} cm): ${dist(Dist.of(c.mapNotNull { it.pointSupport?.toDouble() }), "punti", 0)}")
        line("RMS dei punti dal piano (cm): ${dist(Dist.of(c.mapNotNull { it.rmsM?.times(100) }), "cm")}")
        line("Scostamento medio dei punti dal piano (cm): ${dist(Dist.of(c.mapNotNull { it.biasM?.times(100) }), "cm")}")
        line()
        line("-- 3. Perché non si stabilizza (frame con candidata non proposta)")
        val notProposed = c.filter { it.state != CandidateState.PROPOSED }
        val hist = Reason.entries.map { re -> re to notProposed.count { re in it.reasons } }.filter { it.second > 0 }.sortedByDescending { it.second }
        if (hist.isEmpty()) line("Nessun motivo registrato.") else hist.forEach { (re, n) -> line("  ${re.label}: $n frame (${pctOf(n, notProposed.size)})") }
        line()
        line("-- 4. Stato nel tempo (segmenti)")
        var segStart = rows.firstOrNull()?.timeMs ?: 0L
        var segState: CandidateState? = rows.firstOrNull()?.state
        var segKey = rows.firstOrNull()?.aimedKey
        fun seg(end: Long) { line("  ${s(segStart).padStart(8)} → ${s(end).padStart(8)}  ${(segState?.name ?: "SEARCHING").padEnd(9)}  piano mirato ${segKey?.let { "#$it" } ?: "—"}") }
        for (row in rows.drop(1)) {
            if (row.state != segState || (row.aimedKey != segKey && row.state != null)) { seg(row.timeMs); segStart = row.timeMs; segState = row.state; segKey = row.aimedKey }
        }
        if (rows.isNotEmpty()) seg(rows.last().timeMs)
        line()
        line("-- 5. Proposte (senza conferma)")
        val ep = r.noConfirm.episodes
        line("Proposte ottenute (periodi in cui la candidata è 'Parete rilevata'): ${ep.size} · pareti distinte: ${r.distinctProposedWalls}")
        for ((i, e) in ep.withIndex()) {
            line("  P${i + 1}: da ${s(e.startMs)} a ${s(e.endMs)} (${s(e.endMs - e.startMs)}) · diventa proposta dopo ${s(e.timeToProposeMs)} dalla comparsa · piani ${e.planeKeys.joinToString { "#$it" }}")
            line("      linea ${pt(e.lineAtStart.a)} → ${pt(e.lineAtStart.b)} · lunghezza ${f(e.lineAtStart.length)} m (a fine proposta ${f(e.lineAtEnd.length)} m) · direzione ${f(e.lineAtStart.headingDeg, 1)}° · quote ${f(e.lineAtStart.minY)}..${f(e.lineAtStart.maxY)}")
            if (e.advisories.isNotEmpty()) line("      avvisi: " + e.advisories.joinToString("; ") { it.label })
        }
        line()
        line("-- 6. Simulazione con conferma automatica (SOLO per studio: conferma ogni proposta non duplicata)")
        val w = r.autoConfirm.confirmed
        line("Pareti che si acquisirebbero: ${w.size}")
        for (x in w) line("  Parete ${x.id} a ${s(x.confirmedAtMs)} · ${pt(x.line.a)} → ${pt(x.line.b)} · ${f(x.observedLengthM)} m · direzione ${f(x.line.headingDeg, 1)}° · qualità ${x.quality.label} (${f(x.qualityScore)}) · punti ${x.pointSupport}${x.rmsM?.let { " · rms ${f(it * 100, 1)} cm" } ?: ""}" + if (x.advisories.isNotEmpty()) " · avvisi: " + x.advisories.joinToString("; ") { it.label } else "")
        val dup = r.autoConfirm.rows.count { it.state == CandidateState.PROPOSED }
        line("Frame in cui una proposta duplicava una parete già acquisita: ${dup}")
        line()
        line("-- 7. Parametri usati")
        line("angolo ${f(r.params.sameAngleTolDeg, 1)}° · distanza ${f(r.params.sameOffsetTolM * 100, 0)} cm · vuoto ${f(r.params.sameGapTolM)} m · tolleranza di mira persa ${r.params.graceMs} ms · mira ≥ ${r.params.minAimedMs} ms · piano seguito ≥ ${r.params.minPlaneTrackingMs} ms · velocità ≤ ${f(r.params.maxSpeedMps)} m/s · direzione σ ≤ ${f(r.params.maxHeadingStdDeg, 1)}° · posizione σ ≤ ${f(r.params.maxOffsetStdM * 100, 1)} cm · inclinazione ≤ ${f(r.params.maxTiltDeg, 0)}° · lunghezza ≥ ${f(r.params.minLengthM)} m · altezza ≥ ${f(r.params.minHeightM)} m")
        line("La linea è QUELLA DEL PIANO ARCore (le estensioni di frammenti compatibili allungano solo gli estremi); i punti servono solo come controllo qualità e non la spostano.")
    }

    private fun pct(n: Int, d: Int) = formatDecimal(if (d == 0) 0.0 else n * 100.0 / d, 0, '.') + "%"
    private fun pctOf(n: Int, d: Int) = pct(n, d)
    private fun pt(p: ArXZ) = "(${f(p.x)}, ${f(p.z)})"

    fun csv(r: SimResult): String = buildString {
        append("frame;timeMs;tracking;aimedPlane;aimDistM;state;stability;aimedMs;planeMs;speedMps;headingStdDeg;offsetStdCm;lengthM;heightM;pointSupport;rmsCm;biasCm;reasons;advisories\n")
        for (x in r.noConfirm.rows) {
            append("${x.frameIndex};${x.timeMs};${x.tracking};${x.aimedKey ?: ""};${x.aimDistM?.let { f(it, 2) } ?: ""};${x.state ?: ""};${x.stability?.let { f(it, 2) } ?: ""};${x.aimedMs ?: ""};${x.planeMs ?: ""};")
            append("${x.speedMps?.let { f(it, 2) } ?: ""};${x.headingStdDeg?.let { f(it, 2) } ?: ""};${x.offsetStdM?.let { f(it * 100, 2) } ?: ""};${x.lengthM?.let { f(it, 2) } ?: ""};${x.heightM?.let { f(it, 2) } ?: ""};")
            append("${x.pointSupport ?: ""};${x.rmsM?.let { f(it * 100, 2) } ?: ""};${x.biasM?.let { f(it * 100, 2) } ?: ""};${x.reasons.joinToString("|") { it.name }};${x.advisories.joinToString("|") { it.name }}\n")
        }
    }

    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Vista dall'alto: traiettoria, dove si è mirato (colore = stato), proposte e pareti della simulazione con conferma automatica. */
    fun svg(r: SimResult, frames: List<AimFrame>, title: String, widthPx: Int = 1200): String {
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        fun grow(x: Double, z: Double) { minX = min(minX, x); maxX = max(maxX, x); minZ = min(minZ, z); maxZ = max(maxZ, z) }
        val cam = frames.filter { it.tracking && it.camera != null }
        for (c in cam) grow(c.camera!!.x, c.camera.z)
        for (x in r.noConfirm.rows) x.hit?.let { grow(it.x, it.z) }
        for (e in r.noConfirm.episodes) { grow(e.lineAtStart.a.x, e.lineAtStart.a.z); grow(e.lineAtStart.b.x, e.lineAtStart.b.z) }
        if (minX > maxX) { minX = -1.0; maxX = 1.0; minZ = -1.0; maxZ = 1.0 }
        minX -= 0.7; maxX += 0.7; minZ -= 0.7; maxZ += 0.7
        val pad = 20; val header = 70
        val scale = (widthPx - 2 * pad) / max(maxX - minX, 1.0)
        val height = (max(maxZ - minZ, 1.0) * scale + 2 * pad + header).toInt()
        fun px(x: Double) = pad + (x - minX) * scale
        fun py(z: Double) = header + pad + (z - minZ) * scale
        fun n(v: Double) = f(v, 1)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$widthPx\" height=\"$height\" viewBox=\"0 0 $widthPx $height\" font-family=\"sans-serif\">\n")
            append("<rect width=\"$widthPx\" height=\"$height\" fill=\"#fff\"/>\n")
            append("<text x=\"$pad\" y=\"22\" font-size=\"16\" font-weight=\"bold\">${esc(title)} — simulazione M2.0</text>\n")
            append("<text x=\"$pad\" y=\"42\" font-size=\"12\" fill=\"#444\">${esc("Mira valida ${r.aimValidFrames}/${r.frames} frame · proposte ${r.noConfirm.episodes.size} (pareti distinte ${r.distinctProposedWalls}) · pareti con conferma automatica ${r.autoConfirm.confirmed.size}")}</text>\n")
            append("<circle cx=\"${pad + 6}\" cy=\"58\" r=\"4\" fill=\"#bdbdbd\"/><text x=\"${pad + 14}\" y=\"62\" font-size=\"11\">mira (cerca/instabile)</text>")
            append("<circle cx=\"${pad + 170}\" cy=\"58\" r=\"4\" fill=\"#fb8c00\"/><text x=\"${pad + 178}\" y=\"62\" font-size=\"11\">stabilizzo</text>")
            append("<circle cx=\"${pad + 260}\" cy=\"58\" r=\"4\" fill=\"#2e7d32\"/><text x=\"${pad + 268}\" y=\"62\" font-size=\"11\">proposta</text>")
            append("<line x1=\"${pad + 340}\" y1=\"58\" x2=\"${pad + 366}\" y2=\"58\" stroke=\"#1565c0\" stroke-width=\"2\"/><text x=\"${pad + 372}\" y=\"62\" font-size=\"11\">camera</text>\n")
            var gx = ceil(minX)
            while (gx <= maxX) { append("<line x1=\"${n(px(gx))}\" y1=\"${n(py(minZ))}\" x2=\"${n(px(gx))}\" y2=\"${n(py(maxZ))}\" stroke=\"#eee\"/>"); gx += 1.0 }
            var gz = ceil(minZ)
            while (gz <= maxZ) { append("<line x1=\"${n(px(minX))}\" y1=\"${n(py(gz))}\" x2=\"${n(px(maxX))}\" y2=\"${n(py(gz))}\" stroke=\"#eee\"/>"); gz += 1.0 }
            append("\n")
            if (cam.isNotEmpty()) append("<polyline points=\"${cam.joinToString(" ") { "${n(px(it.camera!!.x))},${n(py(it.camera.z))}" }}\" fill=\"none\" stroke=\"#1565c0\" stroke-width=\"1.5\" stroke-opacity=\"0.7\"/>\n")
            for (x in r.noConfirm.rows) {
                val h = x.hit ?: continue
                val color = when (x.state) { CandidateState.PROPOSED -> "#2e7d32"; CandidateState.TRACKING -> "#fb8c00"; else -> "#bdbdbd" }
                append("<circle cx=\"${n(px(h.x))}\" cy=\"${n(py(h.z))}\" r=\"2.5\" fill=\"$color\" fill-opacity=\"0.8\"/>")
            }
            append("\n")
            for ((i, e) in r.noConfirm.episodes.withIndex()) {
                val l = e.lineAtEnd
                append("<line x1=\"${n(px(l.a.x))}\" y1=\"${n(py(l.a.z))}\" x2=\"${n(px(l.b.x))}\" y2=\"${n(py(l.b.z))}\" stroke=\"#2e7d32\" stroke-width=\"4\" stroke-linecap=\"round\" stroke-opacity=\"0.55\"/>")
                append("<text x=\"${n(px((l.a.x + l.b.x) / 2))}\" y=\"${n(py((l.a.z + l.b.z) / 2) - 8)}\" font-size=\"11\" text-anchor=\"middle\" fill=\"#1b5e20\">P${i + 1} · ${n(l.length)} m</text>\n")
            }
            for (w in r.autoConfirm.confirmed) {
                val l = w.line
                append("<line x1=\"${n(px(l.a.x))}\" y1=\"${n(py(l.a.z))}\" x2=\"${n(px(l.b.x))}\" y2=\"${n(py(l.b.z))}\" stroke=\"#d50000\" stroke-width=\"2.5\" stroke-dasharray=\"6 3\"/>")
                append("<text x=\"${n(px((l.a.x + l.b.x) / 2))}\" y=\"${n(py((l.a.z + l.b.z) / 2) + 14)}\" font-size=\"11\" text-anchor=\"middle\" fill=\"#b71c1c\">W${w.id}</text>\n")
            }
            append("</svg>\n")
        }
    }
}
