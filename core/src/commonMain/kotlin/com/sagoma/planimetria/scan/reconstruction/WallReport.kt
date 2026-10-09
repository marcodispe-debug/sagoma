package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.geometry.formatDecimal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Confronto di una parete tra due ricostruzioni indipendenti (keyframe pari / dispari): differenze contro le σ dichiarate. */
data class WallConsistency(val wallId: Int, val otherId: Int?, val positionDiffM: Double?, val positionSigmaM: Double?, val headingDiffDeg: Double?, val headingSigmaDeg: Double?) {
    val positionZ: Double? get() = if (positionDiffM != null && positionSigmaM != null && positionSigmaM > 0) positionDiffM / positionSigmaM else null
    val headingZ: Double? get() = if (headingDiffDeg != null && headingSigmaDeg != null && headingSigmaDeg > 0) headingDiffDeg / headingSigmaDeg else null
}

object WallReport {
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun cm(v: Double?) = v?.let { f(it * 100, 1) } ?: "—"

    /** Pareti di [a] ritrovate in [b] (angolo ≤ 10°, distanza ≤ 15 cm, sovrapposte): differenze di posizione e orientamento con le σ combinate. */
    fun consistency(a: WallEstimationResult, b: WallEstimationResult): List<WallConsistency> = a.walls.map { w ->
        val g = w.geometry
        val match = b.walls.filter { o ->
            val og = o.geometry
            Geo.angleDeg(abs(g.nx * og.nx + g.nz * og.nz)) <= 10 && g.nx * og.nx + g.nz * og.nz > 0 &&
                abs(g.nx * og.cx + g.nz * og.cz - g.d) <= 0.15 && overlap(g, og) > 0.2
        }.minByOrNull { abs(g.nx * it.geometry.cx + g.nz * it.geometry.cz - g.d) }
        if (match == null) WallConsistency(w.id, null, null, null, null, null) else {
            val og = match.geometry
            // Differenza di posizione: lungo la normale di a, nel punto medio della sovrapposizione.
            val mid = (max(g.startU, uOn(g, og.pointAt(og.startU))).coerceAtMost(g.endU) + min(g.endU, uOn(g, og.pointAt(og.endU))).coerceAtLeast(g.startU)) / 2
            val p = g.pointAt(mid)
            val diff = abs(og.nx * p[0] + og.nz * p[1] - og.d)
            WallConsistency(
                w.id, match.id, diff, sqrt(positionSigmaAt(w, mid).let { it * it } + positionSigmaAt(match, uOn(og, p)).let { it * it }),
                Geo.angleDeg(abs(g.nx * og.nx + g.nz * og.nz)), sqrt(w.uncertainty.headingSigmaDeg.let { it * it } + match.uncertainty.headingSigmaDeg.let { it * it }),
            )
        }
    }

    private fun uOn(g: WallGeometry, q: DoubleArray) = (q[0] - g.cx) * g.ux + (q[1] - g.cz) * g.uz

    /**
     * σ di posizione della linea nel punto u (m dal centro del fit): la σ di posizione vale al centro; lontano dal centro si somma
     * l'effetto dell'errore di direzione, σ(u)² = σ_pos² + (σ_θ · u)².
     */
    fun positionSigmaAt(w: EstimatedWall, u: Double): Double {
        val th = w.uncertainty.headingSigmaDeg * kotlin.math.PI / 180
        return sqrt(w.uncertainty.positionSigmaM * w.uncertainty.positionSigmaM + th * th * u * u)
    }
    private fun overlap(a: WallGeometry, b: WallGeometry): Double {
        val b0 = uOn(a, b.pointAt(b.startU)); val b1 = uOn(a, b.pointAt(b.endU))
        return min(a.endU, max(b0, b1)) - max(a.startU, min(b0, b1))
    }

    fun text(r: WallEstimationResult, consistency: List<Pair<String, List<WallConsistency>>>, arcore: List<ArCoreMatch>, surfaces: SurfaceResult): String = buildString {
        fun line(s: String = "") = append(s).append('\n')
        line("-- R3 · Pareti candidate (NON è una planimetria: nessun perimetro, nessun collegamento, nessun angolo imposto)")
        line("Superfici verticali R2 in ingresso: ${r.inputVertical} · strutturali: ${r.inputStructural} · scartate: ${r.excluded.size} · pareti candidate: ${r.walls.size} · fusioni: ${r.merges.size}")
        line("Modello di rumore della depth (σ robusta per distanza): " + r.noise.bins.joinToString(" · ") { (rng, s) -> "${f(rng.start, 1)}–${if (rng.endInclusive > 50) "∞" else f(rng.endInclusive, 1)} m ${cm(s)} cm" })
        line("Spostamento temporale A/C: ripiego per i punti senza posa C = ${cm(r.temporalFallbackM)} cm" + if (r.diagnostics.isEmpty()) "" else "")
        for (d in r.diagnostics) line("  $d")
        val totalObserved = r.walls.sumOf { it.geometry.observedLengthM }
        line("Metri di parete realmente osservati: ${f(totalObserved)} m (somma dei tratti osservati; i gap non sono contati)")
        line()
        line("Superfici scartate:")
        for (e in r.excluded) line("  S${e.surfaceId} ${e.kind}: ${e.reason}")
        line()
        if (r.merges.isNotEmpty()) { line("Fusioni (superfici R2 → una parete):"); for (m in r.merges) line("  " + m.joinToString(" + ") { "S$it" }); line() }
        line("id   sorgenti          lungh. m  osserv. m  cop.%  quote dal pav. m   RMS cm  σpos cm  σdir °  σlungh cm  σtemp raw cm  →contrib cm  σdepth cm  viste  punti   qualità  conf.  inizio              fine")
        for (w in r.walls) {
            val g = w.geometry; val u = w.uncertainty; val e = w.evidence
            line(
                "W${w.id}".padEnd(5) + w.sourceSurfaceIds.joinToString("+") { "S$it" }.padEnd(18) + f(g.lengthM).padStart(8) + f(g.observedLengthM).padStart(11) +
                    f(e.observedCoverage * 100, 0).padStart(7) + "  " + "${g.bottomAboveFloor?.let { f(it) } ?: "?"}..${g.topAboveFloor?.let { f(it) } ?: "?"}".padEnd(16) +
                    cm(e.fitRmsM).padStart(7) + cm(u.positionSigmaM).padStart(9) + f(u.headingSigmaDeg, 1).padStart(8) + cm(u.lengthSigmaM).padStart(11) +
                    cm(u.temporalSigmaM).padStart(14) + cm(u.temporalContributionM).padStart(13) + cm(u.depthSigmaM).padStart(11) +
                    e.viewGroups.toString().padStart(7) + e.inlierCount.toString().padStart(8) + e.quality.name.padStart(10) + f(w.recognitionConfidence).padStart(7) +
                    "  ${w.start.state}/${w.start.reason.name}".padEnd(20) + " ${w.end.state}/${w.end.reason.name}",
            )
        }
        line("Le σ sono stime interne (dispersione tra viste + ambiguità temporale): NON tarate su misure vere (manca il metro laser).")
        line()
        line("Dettagli per parete:")
        for (w in r.walls) {
            line("  W${w.id} (${w.sourceSurfaceIds.joinToString("+") { "S$it" }}): direzione ${f(w.geometry.headingDeg, 1)}° · ${w.evidence.rawFrames} depth · ${w.evidence.viewGroups} viste · angoli di vista ${f(w.evidence.viewAngleSpanDeg, 0)}° · distanza ${f(w.evidence.rangeMedianM)} m · dispersione tra viste ${cm(w.evidence.dispersionM)} cm · outlier ${f(w.evidence.outlierFraction * 100, 1)}%")
            line("     qualità ${w.evidence.quality}: ${w.evidence.qualityReasons.joinToString("; ")}")
            line("     inizio ${w.start.state} (${w.start.reason.label}, σ ${cm(w.start.sigmaM)} cm) · fine ${w.end.state} (${w.end.reason.label}, σ ${cm(w.end.sigmaM)} cm)")
            for ((name, e) in listOf("inizio" to w.start, "fine" to w.end)) e.evidence?.let { v ->
                line(
                    "     evidenza $name: densità 5/10/15/20 cm ${f(v.density5)}/${f(v.density10)}/${f(v.density15)}/${f(v.density20)} · zona assottigliata ${cm(v.thinZoneM)} cm · " +
                        "${v.groupsNearEnd} viste · σ ripetibilità ${cm(v.repeatSigmaM)} cm ⊕ σ terminale ${cm(v.terminalSigmaM)} cm · " + (if (v.strong) "forte" else "debole") +
                        (v.surfaceId?.let { " · superficie S$it a ${cm(v.surfaceGapM ?: 0.0)} cm dall'intersezione (${f(v.surfaceGapSigmas ?: 0.0, 1)}σ)" } ?: ""),
                )
            }
            for (gp in w.gaps) line("     gap ${f(gp.fromU)}..${f(gp.toU)} m (${cm(gp.lengthM)} cm): ${gp.reason.label} — NON riempito")
            line("     spessore: " + if (w.thickness.state == ThicknessState.MEASURED) "${cm(w.thickness.valueM)} ± ${cm(w.thickness.sigmaM)} cm (${w.thickness.note})" else "SCONOSCIUTO (${w.thickness.note})")
            for (c in w.possibleContinuation) line("     indicazione (non geometria): $c")
            for (d in w.diagnostics) line("     nota: $d")
        }
        line()
        line("Spessori misurati: ${r.walls.count { it.thickness.state == ThicknessState.MEASURED }} · sconosciuti: ${r.walls.count { it.thickness.state == ThicknessState.UNKNOWN }}")
        line("Estremità: osservate ${r.walls.sumOf { listOf(it.start, it.end).count { e -> e.state == EndState.OBSERVED } }} · parziali ${r.walls.sumOf { listOf(it.start, it.end).count { e -> e.state == EndState.PARTIAL } }} · incerte ${r.walls.sumOf { listOf(it.start, it.end).count { e -> e.state == EndState.UNCERTAIN } }}")
        val ambiguous = r.walls.filter { it.evidence.quality == EvidenceQuality.LOW || it.start.state == EndState.UNCERTAIN || it.end.state == EndState.UNCERTAIN }
        line("Pareti ambigue (qualità bassa o estremità incerte): " + if (ambiguous.isEmpty()) "nessuna" else ambiguous.joinToString { "W${it.id}" })
        line()
        line("-- R3 · Coerenza delle σ tra due ricostruzioni indipendenti (keyframe pari / dispari)")
        for ((label, rows) in consistency) {
            val found = rows.filter { it.otherId != null }
            val zs = found.mapNotNull { it.positionZ }; val hz = found.mapNotNull { it.headingZ }
            line("  $label: pareti ritrovate ${found.size}/${rows.size} · |Δposizione| ≤ 2σ in ${zs.count { it <= 2 }}/${zs.size} · |Δdirezione| ≤ 2σ in ${hz.count { it <= 2 }}/${hz.size}" +
                if (zs.isNotEmpty()) " · z mediano posizione ${f(Geo.median(zs.toDoubleArray()))}, direzione ${f(Geo.median(hz.toDoubleArray()))}" else "")
            for (c in found) line("    W${c.wallId}↔W${c.otherId}: Δpos ${cm(c.positionDiffM)} cm (σ ${cm(c.positionSigmaM)}, z ${c.positionZ?.let { f(it) }}) · Δdir ${c.headingDiffDeg?.let { f(it, 1) }}° (σ ${c.headingSigmaDeg?.let { f(it, 1) }}, z ${c.headingZ?.let { f(it) }})")
        }
        line()
        line("-- R3 · Confronto con i piani ARCore (solo diagnostica, NON verità)")
        for (a in arcore.filter { it.surface != null }) {
            val w = r.walls.firstOrNull { a.surface in it.sourceSurfaceIds }
            line("  A${a.key} (${f(a.lengthM)} m) → S${a.surface} ${a.surfaceKind}" + (w?.let { " → W${it.id}" } ?: " → nessuna parete (superficie scartata)"))
        }
        val promotedObjects = r.walls.filter { w -> w.sourceSurfaceIds.any { id -> surfaces.surfaces.firstOrNull { it.id == id }?.kind != SurfaceKind.VERTICAL_STRUCTURAL } }
        line("Superfici non strutturali diventate parete: ${promotedObjects.size} (deve essere 0)")
    }

    /** R3.1: evidenza terminale di ogni estremità (una riga per estremità). */
    fun endsCsv(r: WallEstimationResult): String = buildString {
        append("wall;end;u;x;z;state;reason;sigmaM;repeatSigmaM;terminalSigmaM;thinZoneM;density5;density10;density15;density20;lastSpanM;terminalGapM;groupsNearEnd;strong;surfaceId;surfaceGapM;surfaceGapSigmas\n")
        for (w in r.walls) for ((name, e) in listOf("start" to w.start, "end" to w.end)) {
            val q = w.geometry.pointAt(e.u); val v = e.evidence
            append(
                listOf(
                    w.id, name, f(e.u, 4), f(q[0], 4), f(q[1], 4), e.state, e.reason, f(e.sigmaM, 4),
                    v?.let { f(it.repeatSigmaM, 4) } ?: "", v?.let { f(it.terminalSigmaM, 4) } ?: "", v?.let { f(it.thinZoneM, 3) } ?: "",
                    v?.let { f(it.density5, 3) } ?: "", v?.let { f(it.density10, 3) } ?: "", v?.let { f(it.density15, 3) } ?: "", v?.let { f(it.density20, 3) } ?: "",
                    v?.let { f(it.lastSpanM, 3) } ?: "", v?.terminalGapM?.let { f(it, 3) } ?: "", v?.groupsNearEnd ?: "", v?.strong ?: "",
                    v?.surfaceId ?: "", v?.surfaceGapM?.let { f(it, 4) } ?: "", v?.surfaceGapSigmas?.let { f(it, 2) } ?: "",
                ).joinToString(";"),
            ).append('\n')
        }
    }

    fun csv(r: WallEstimationResult): String = buildString {
        append("id;sources;startX;startZ;endX;endZ;lengthM;observedM;coverage;headingDeg;bottomAboveFloor;topAboveFloor;rmsM;positionSigmaM;headingSigmaDeg;lengthSigmaM;startSigmaM;endSigmaM;")
        append("temporalSigmaM;temporalContributionM;depthSigmaM;viewGroups;rawFrames;inliers;quality;recognitionConfidence;startState;startReason;endState;endReason;gaps;thicknessState;thicknessM\n")
        for (w in r.walls) {
            val g = w.geometry; val u = w.uncertainty; val e = w.evidence
            val s = g.pointAt(g.startU); val t = g.pointAt(g.endU)
            append(
                listOf(
                    w.id, w.sourceSurfaceIds.joinToString("+"), f(s[0], 4), f(s[1], 4), f(t[0], 4), f(t[1], 4), f(g.lengthM, 4), f(g.observedLengthM, 4), f(e.observedCoverage, 3), f(g.headingDeg, 2),
                    g.bottomAboveFloor?.let { f(it, 3) } ?: "", g.topAboveFloor?.let { f(it, 3) } ?: "", f(e.fitRmsM, 5), f(u.positionSigmaM, 5), f(u.headingSigmaDeg, 3), f(u.lengthSigmaM, 4),
                    f(u.startSigmaM, 4), f(u.endSigmaM, 4), f(u.temporalSigmaM, 5), f(u.temporalContributionM, 5), f(u.depthSigmaM, 5), e.viewGroups, e.rawFrames, e.inlierCount, e.quality,
                    f(w.recognitionConfidence, 3), w.start.state, w.start.reason, w.end.state, w.end.reason, w.gaps.joinToString("|") { "${f(it.fromU, 3)}..${f(it.toU, 3)}:${it.reason}" },
                    w.thickness.state, w.thickness.valueM?.let { f(it, 4) } ?: "",
                ).joinToString(";"),
            ).append('\n')
        }
    }

    /** Vista dall'alto delle pareti candidate R3, separata dalla vista delle superfici R2. NON è una planimetria. */
    fun topDown(map: GlobalMap, s: SurfaceResult, r: WallEstimationResult, title: String, widthPx: Int = 1300): String {
        val g = map.plan.grid
        val scale = (widthPx - 40) / (g.nx * g.cellM)
        val heightPx = (g.nz * g.cellM * scale + 170).roundToInt()
        fun px(x: Double) = (x - g.minX) * scale + 20
        fun pz(z: Double) = (z - g.minZ) * scale + 60
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$widthPx" height="$heightPx" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FAFAFA"/>""")
        sb.append("""<text x="20" y="22" font-size="15" font-weight="bold">${esc(title)} — pareti candidate R3 (NON è una planimetria)</text>""")
        sb.append("""<text x="20" y="42" fill="#555">Solo tratti osservati. Nessun perimetro, nessun collegamento, nessun angolo imposto. Fascia trasparente = ±σ di posizione; tratteggio = gap NON osservato.</text>""")
        // Contesto: punti in elevazione (grigio chiaro).
        val floorY = s.floor?.y
        val cells = HashSet<Long>()
        for (i in 0 until map.points.size) {
            if (floorY != null && map.points.y[i] <= floorY + 0.15) continue
            val ci = g.cx(map.points.x[i].toDouble()); val ck = g.cz(map.points.z[i].toDouble())
            if (g.inside(ci, 0, ck)) cells.add((ci.toLong() shl 32) or ck.toLong())
        }
        val cell = g.cellM * scale
        sb.append("""<g fill="#E3E3E3">""")
        for (k in cells.sorted()) { val ci = (k ushr 32).toInt(); val ck = (k and 0xFFFFFFFFL).toInt(); sb.append("""<rect x="${f(px(g.minX + ci * g.cellM), 1)}" y="${f(pz(g.minZ + ck * g.cellM), 1)}" width="${f(cell + 0.3, 1)}" height="${f(cell + 0.3, 1)}"/>""") }
        sb.append("</g>")
        // Superfici verticali R2 scartate (sottili, per vedere cosa NON è diventato parete).
        for (sf in s.surfaces.filter { it.orientation == Orientation.VERTICAL && r.excluded.any { e -> e.surfaceId == it.id } }) {
            val c = sf.plane.centroid
            sb.append("""<line x1="${f(px(c[0] + sf.u[0] * sf.uMin), 1)}" y1="${f(pz(c[2] + sf.u[2] * sf.uMin), 1)}" x2="${f(px(c[0] + sf.u[0] * sf.uMax), 1)}" y2="${f(pz(c[2] + sf.u[2] * sf.uMax), 1)}" stroke="${ReconVisuals.color(sf.kind)}" stroke-width="1.2" stroke-opacity="0.6"/>""")
        }
        for (w in r.walls) {
            val gm = w.geometry
            val color = when (w.evidence.quality) { EvidenceQuality.HIGH -> "#1565C0"; EvidenceQuality.MEDIUM -> "#00897B"; EvidenceQuality.LOW -> "#C62828" }
            // Fascia ±σ di posizione.
            val sg = max(w.uncertainty.positionSigmaM, 0.002)
            val a = gm.pointAt(gm.startU); val b = gm.pointAt(gm.endU)
            sb.append("""<polygon points="${f(px(a[0] + gm.nx * sg), 1)},${f(pz(a[1] + gm.nz * sg), 1)} ${f(px(b[0] + gm.nx * sg), 1)},${f(pz(b[1] + gm.nz * sg), 1)} ${f(px(b[0] - gm.nx * sg), 1)},${f(pz(b[1] - gm.nz * sg), 1)} ${f(px(a[0] - gm.nx * sg), 1)},${f(pz(a[1] - gm.nz * sg), 1)}" fill="$color" fill-opacity="0.18"/>""")
            for (sp in gm.observedSpans) {
                val p0 = gm.pointAt(sp.fromU); val p1 = gm.pointAt(sp.toU)
                sb.append("""<line x1="${f(px(p0[0]), 1)}" y1="${f(pz(p0[1]), 1)}" x2="${f(px(p1[0]), 1)}" y2="${f(pz(p1[1]), 1)}" stroke="$color" stroke-width="4" stroke-linecap="butt"/>""")
            }
            for (gp in w.gaps) {
                val p0 = gm.pointAt(gp.fromU); val p1 = gm.pointAt(gp.toU)
                sb.append("""<line x1="${f(px(p0[0]), 1)}" y1="${f(pz(p0[1]), 1)}" x2="${f(px(p1[0]), 1)}" y2="${f(pz(p1[1]), 1)}" stroke="#9E9E9E" stroke-width="2" stroke-dasharray="4,4"/>""")
            }
            for (e in listOf(w.start, w.end)) {
                val q = gm.pointAt(e.u); val x = px(q[0]); val z = pz(q[1])
                when (e.state) {
                    EndState.OBSERVED -> sb.append("""<circle cx="${f(x, 1)}" cy="${f(z, 1)}" r="5" fill="$color"/>""")
                    EndState.PARTIAL -> sb.append("""<circle cx="${f(x, 1)}" cy="${f(z, 1)}" r="5" fill="#FFF" stroke="$color" stroke-width="2"/>""")
                    EndState.UNCERTAIN -> sb.append("""<text x="${f(x - 4, 1)}" y="${f(z + 5, 1)}" font-size="15" font-weight="bold" fill="#E65100">?</text>""")
                }
            }
            val m = gm.pointAt((gm.startU + gm.endU) / 2)
            sb.append("""<line x1="${f(px(m[0]), 1)}" y1="${f(pz(m[1]), 1)}" x2="${f(px(m[0] + gm.nx * 0.2), 1)}" y2="${f(pz(m[1] + gm.nz * 0.2), 1)}" stroke="$color" stroke-width="1.5"/>""")
            val label = "W${w.id}" + (if (w.sourceSurfaceIds.size > 1) " (" + w.sourceSurfaceIds.joinToString("+") { "S$it" } + ")" else "") +
                (if (w.thickness.state == ThicknessState.MEASURED) " sp. ${cm(w.thickness.valueM)} cm" else "")
            sb.append("""<text x="${f(px(m[0]) + 6, 1)}" y="${f(pz(m[1]) - 6, 1)}" fill="$color" font-weight="bold">${esc(label)}</text>""")
        }
        val y0 = heightPx - 75
        sb.append("""<line x1="20" y1="$y0" x2="${f(20 + scale, 1)}" y2="$y0" stroke="#000" stroke-width="3"/><text x="20" y="${y0 + 16}">1 m</text>""")
        val legend = listOf("#1565C0" to "evidenza alta", "#00897B" to "media", "#C62828" to "bassa")
        var lx = 110
        for ((c, l) in legend) { sb.append("""<rect x="$lx" y="${y0 - 10}" width="14" height="6" fill="$c"/><text x="${lx + 18}" y="${y0 - 3}">$l</text>"""); lx += 140 }
        sb.append("""<text x="110" y="${y0 + 18}">● estremità osservata · ○ parziale (fuori vista o nascosta) · ? incerta · trattino = normale verso le camere · linee sottili colorate = superfici R2 scartate</text>""")
        sb.append("""<text x="110" y="${y0 + 36}">W# (S…+S…) = parete fusa da più superfici R2 · grigio chiaro = punti in elevazione (contesto)</text>""")
        sb.append("</svg>")
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
