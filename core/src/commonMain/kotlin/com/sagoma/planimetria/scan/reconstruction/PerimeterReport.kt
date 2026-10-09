package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.geometry.formatDecimal
import kotlin.math.max
import kotlin.math.roundToInt

/** Report di R4: testo, CSV, JSON e vista dall'alto. Deterministici. Non modificano né riscrivono l'uscita di R3. */
object PerimeterReport {
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun cm(v: Double) = f(v * 100, 1) + " cm"
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun linkLine(l: WallLink): String = buildString {
        append("W${l.fromWall}→W${l.toWall} ${l.kind} ${l.support} ${l.decision} punteggio ${f(l.score, 3)}")
        val c = l.components
        append(" [geom ${f(c.geometry, 3)} · oss ${f(c.observedFraction, 3)} · estremità ${f(c.endpointState, 2)} · spazio ${f(c.freeSpace, 2)} · evidenza ${f(c.evidence, 2)} · R2 ${f(c.r2Support, 2)}]")
        append(" svolta ${f(l.turnDeg, 1)}° ± ${f(l.turnSigmaDeg, 1)}°")
        if (l.kind == LinkKind.CORNER && l.cornerX != null) {
            append(" angolo (${f(l.cornerX, 3)}, ${f(l.cornerZ!!, 3)}) σ ${cm(l.cornerSigmaM!!)}")
            append(" · fine W${l.fromWall} ${cm(l.extensionFromM)} ± ${cm(l.extensionFromSigmaM)} (${f(l.zFrom, 1)}σ)")
            append(" · inizio W${l.toWall} ${cm(l.extensionToM)} ± ${cm(l.extensionToSigmaM)} (${f(l.zTo, 1)}σ)")
            for ((name, e) in listOf("fine W${l.fromWall}" to l.fromEnd, "inizio W${l.toWall}" to l.toEnd)) e?.let { s ->
                append(" · [$name ${s.endpointState}/${s.endpointReason}: σ misura ${cm(s.measurementSigmaM)}, terminale ${cm(s.terminalSigmaM)}, intersezione ${cm(s.intersectionSigmaM)}; " +
                    (if (s.inferredCorner) "angolo DEDOTTO" + (if (s.measurementCompatible) " (compatibile entro la misura, ma non osservato)" else "") else "angolo osservato") + "]")
            }
        }
        if (l.kind == LinkKind.SAME_WALL) append(" gap ${cm(l.extensionFromM)} (${f(l.zFrom, 1)}σ) · sfalsamento ${cm(l.lateralOffsetM ?: 0.0)} (${f(l.zLateral ?: 0.0, 1)}σ) · direzione ${f(l.zHeading ?: 0.0, 1)}σ")
        if (l.unobservedM > 0) append(" · NON osservato ${cm(l.unobservedM)}")
        l.gapReason?.let { append(" (${it.label})") }
        if (l.r2Near.isNotEmpty()) append(" · R2 vicine: " + l.r2Near.joinToString { "S$it" })
        if (l.reasons.isNotEmpty()) append(" — " + l.reasons.joinToString("; "))
    }

    fun text(r: PerimeterResult): String = buildString {
        fun line(s: String = "") = append(s).append('\n')
        line("== R4 — Perimetro (NON è una planimetria: solo quali pareti R3 appartengono allo stesso perimetro)")
        line("Stato complessivo: ${r.state}" + (r.main?.let { " (perimetro principale P${it.id})" } ?: " (nessun collegamento supportato)"))
        line("Regole: nessuna parete inventata, nessun angolo/parallelismo/rettangolarità assunti, nessuna chiusura forzata. Tratti NON osservati e ipotesi separati dalla misura.")
        for (per in r.perimeters.filter { it.links.isNotEmpty() }) {
            line()
            line("-- P${per.id} ${per.state}" + if (per === r.main) " (principale)" else "")
            line("Pareti nell'ordine (interno a destra): " + per.wallIds.joinToString(" → ") { "W$it" } + if (per.closure != null) " → W${per.wallIds.first()}" else "")
            line("Lunghezza osservata: ${f(per.observedLengthM)} m" + (per.perimeterLengthM?.let { " · perimetro tra gli angoli ${f(it)} m ± ${cm(per.uncertainty.perimeterSigmaM ?: 0.0)}" } ?: "") +
                (per.areaM2?.let { " · area ${f(it)} m² ± ${f(per.uncertainty.areaSigmaM2 ?: 0.0, 3)} m²" } ?: ""))
            for (c in per.corners) line("  angolo W${c.fromWall}→W${c.toWall}: (${f(c.x, 3)}, ${f(c.z, 3)}) σ ${cm(c.positionSigmaM)} · angolo interno MISURATO ${f(c.interiorAngleDeg, 1)}° ± ${f(c.angleSigmaDeg, 1)}° · ${c.support}")
            per.closure?.let { k ->
                line("  Chiusura: errore ${cm(k.errorM)} (scarti estremità OSSERVATE↔angoli) · σ propagata ${cm(k.propagatedSigmaM)} · χ² ${f(k.chi2, 2)} su ${k.dof} g.d.l. · p = ${f(k.pValue, 4)} → " + (if (k.compatible) "compatibile" else "NON compatibile") + " · tratti NON osservati (fuori dal χ²) ${cm(k.unobservedM)}")
                line("  Svolte: somma ${f(k.turnSumDeg, 1)}° ± ${f(k.turnSigmaDeg, 1)}° · poligono " + if (k.simplePolygon) "semplice" else "NON semplice")
            }
            for (u in per.unobserved) line("  NON osservato: ${u.kind} W${u.wallId} ${cm(u.lengthM)} — ${u.reason}")
            per.missing?.let { line("  Lato mancante (NON creato): ${it.reason}") }
            for (a in per.alternatives) line("  Alternativa: $a")
            val u = per.uncertainty
            line("  Incertezze separate:")
            line("    misura: σ posizione max ${cm(u.maxPositionSigmaM)}, σ direzione max ${f(u.maxHeadingSigmaDeg, 2)}°")
            line("    estremità: osservate ${u.endsObserved}, parziali ${u.endsPartial}, instabili ${u.endsUncertain}")
            line("    collegamento: punteggio minimo ${f(u.minLinkScore, 3)}, medio ${f(u.meanLinkScore, 3)}")
            line("    chiusura: " + (u.closureP?.let { "p = ${f(it, 4)}, confidenza ${f(u.closureConfidence ?: 0.0, 3)}" } ?: "non applicabile (catena aperta)"))
            line("    riconoscimento (R3): minimo ${f(u.minRecognition, 3)}, medio ${f(u.meanRecognition, 3)} · evidenza peggiore ${u.worstEvidence}")
            line("    σ NON calibrate (come in R3): nessun confronto con misure vere")
            for (s in per.reasons) line("  · $s")
        }
        line()
        line("-- Ruolo delle pareti R3")
        for (w in r.walls) line("  W${w.wallId}: ${w.role} — ${w.role.label}" + (if (w.reasons.isNotEmpty()) " · " + w.reasons.joinToString("; ") else ""))
        line()
        val acc = r.candidates.filter { it.decision != LinkDecision.REJECTED }
        line("-- Collegamenti accettati e ambigui (${acc.size}) su ${r.candidates.size} valutati")
        for (l in acc) line("  " + linkLine(l))
        val rej = r.candidates.filter { it.decision == LinkDecision.REJECTED }
        line("-- Collegamenti rifiutati (${rej.size}): motivi")
        for (l in rej) line("  W${l.fromWall}→W${l.toWall} ${l.kind}: " + l.reasons.joinToString("; "))
        line()
        line("-- Ipotesi (INFERRED / NOT OBSERVED: mai misura, mai pareti)")
        if (r.hypotheses.isEmpty()) line("  nessuna")
        for (h in r.hypotheses) line("  P${h.perimeterId} ${h.kind} W${h.afterWall}→W${h.beforeWall}: ${f(h.lengthM)} m — ${h.note} [${h.status}]")
        for (d in r.diagnostics) line("  · $d")
    }

    fun csv(r: PerimeterResult): String = buildString {
        append("from;to;kind;support;decision;score;geometry;observedFraction;endpointState;freeSpace;evidence;r2Support;turnDeg;turnSigmaDeg;cornerX;cornerZ;cornerSigmaM;")
        append("extFromM;extFromSigmaM;zFrom;extToM;extToSigmaM;zTo;lateralM;zLateral;zHeading;unobservedM;gapReason;r2Near;reasons;")
        append("fromState;fromObservedEnd;fromMeasurementSigmaM;fromTerminalSigmaM;fromIntersectionSigmaM;fromGapM;fromGapObserved;fromInferredCorner;fromMeasurementCompatible;")
        append("toState;toObservedEnd;toMeasurementSigmaM;toTerminalSigmaM;toIntersectionSigmaM;toGapM;toGapObserved;toInferredCorner;toMeasurementCompatible\n")
        fun ends(s: EndSupport?): List<Any> = if (s == null) List(9) { "" } else listOf(
            "${s.endpointState}/${s.endpointReason}", s.observedEnd, f(s.measurementSigmaM, 4), f(s.terminalSigmaM, 4), f(s.intersectionSigmaM, 4),
            f(s.gapToIntersectionM, 4), s.gapObserved, s.inferredCorner, s.measurementCompatible,
        )
        for (l in r.candidates) {
            val c = l.components
            append(
                listOf(
                    l.fromWall, l.toWall, l.kind, l.support, l.decision, f(l.score, 4), f(c.geometry, 4), f(c.observedFraction, 4), f(c.endpointState, 3),
                    f(c.freeSpace, 3), f(c.evidence, 3), f(c.r2Support, 3), f(l.turnDeg, 2), f(l.turnSigmaDeg, 2),
                    l.cornerX?.let { f(it, 4) } ?: "", l.cornerZ?.let { f(it, 4) } ?: "", l.cornerSigmaM?.let { f(it, 4) } ?: "",
                    f(l.extensionFromM, 4), f(l.extensionFromSigmaM, 4), f(l.zFrom, 2), f(l.extensionToM, 4), f(l.extensionToSigmaM, 4), f(l.zTo, 2),
                    l.lateralOffsetM?.let { f(it, 4) } ?: "", l.zLateral?.let { f(it, 2) } ?: "", l.zHeading?.let { f(it, 2) } ?: "",
                    f(l.unobservedM, 4), l.gapReason ?: "", l.r2Near.joinToString(","), l.reasons.joinToString(" | ").replace(";", ","),
                ).plus(ends(l.fromEnd)).plus(ends(l.toEnd)).joinToString(";"),
            ).append('\n')
        }
    }

    // ------------------------------------------------------------------------------------------------------------------ JSON

    private fun js(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u" + ch.code.toString(16).padStart(4, '0')) else append(ch)
        }
        append('"')
    }
    private fun jn(v: Double?, d: Int = 4): String = if (v == null || v.isNaN() || v.isInfinite()) "null" else f(v, d)
    private fun jl(items: List<String>) = items.joinToString(",", "[", "]")

    private fun linkJson(l: WallLink): String {
        val c = l.components
        return "{" + listOf(
            "\"from\":${l.fromWall}", "\"to\":${l.toWall}", "\"kind\":${js(l.kind.name)}", "\"support\":${js(l.support.name)}", "\"decision\":${js(l.decision.name)}",
            "\"score\":${jn(l.score)}",
            "\"components\":{\"geometry\":${jn(c.geometry)},\"observedFraction\":${jn(c.observedFraction)},\"endpointState\":${jn(c.endpointState)},\"freeSpace\":${jn(c.freeSpace)},\"evidence\":${jn(c.evidence)},\"r2Support\":${jn(c.r2Support)}}",
            "\"turnDeg\":${jn(l.turnDeg, 2)}", "\"turnSigmaDeg\":${jn(l.turnSigmaDeg, 2)}",
            "\"corner\":" + (if (l.cornerX != null) "{\"x\":${jn(l.cornerX)},\"z\":${jn(l.cornerZ)},\"sigmaM\":${jn(l.cornerSigmaM)}}" else "null"),
            "\"extensionFromM\":${jn(l.extensionFromM)}", "\"extensionFromSigmaM\":${jn(l.extensionFromSigmaM)}", "\"zFrom\":${jn(l.zFrom, 2)}",
            "\"extensionToM\":${jn(l.extensionToM)}", "\"extensionToSigmaM\":${jn(l.extensionToSigmaM)}", "\"zTo\":${jn(l.zTo, 2)}",
            "\"lateralOffsetM\":${jn(l.lateralOffsetM)}", "\"zLateral\":${jn(l.zLateral, 2)}", "\"zHeading\":${jn(l.zHeading, 2)}",
            "\"unobservedM\":${jn(l.unobservedM)}", "\"gapReason\":" + (l.gapReason?.let { js(it.name) } ?: "null"),
            "\"r2Near\":${jl(l.r2Near.map { it.toString() })}", "\"reasons\":${jl(l.reasons.map(::js))}",
            "\"fromEnd\":${endJson(l.fromEnd)}", "\"toEnd\":${endJson(l.toEnd)}",
        ).joinToString(",") + "}"
    }

    private fun endJson(s: EndSupport?): String = if (s == null) "null" else "{" + listOf(
        "\"endpointState\":${js(s.endpointState.name)}", "\"endpointReason\":${js(s.endpointReason.name)}", "\"observedEnd\":${s.observedEnd}",
        "\"measurementSigmaM\":${jn(s.measurementSigmaM)}", "\"terminalSigmaM\":${jn(s.terminalSigmaM)}", "\"intersectionSigmaM\":${jn(s.intersectionSigmaM)}",
        "\"gapToIntersectionM\":${jn(s.gapToIntersectionM)}", "\"gapObserved\":${s.gapObserved}", "\"inferredCorner\":${s.inferredCorner}", "\"measurementCompatible\":${s.measurementCompatible}",
    ).joinToString(",") + "}"

    fun json(r: PerimeterResult): String = buildString {
        append("{\n\"state\":${js(r.state.name)},\n\"main\":${r.main?.id ?: "null"},\n\"perimeters\":[")
        append(r.perimeters.joinToString(",\n") { p ->
            val u = p.uncertainty
            "{" + listOf(
                "\"id\":${p.id}", "\"state\":${js(p.state.name)}", "\"walls\":${jl(p.wallIds.map { it.toString() })}",
                "\"links\":${jl(p.links.map { "[${it.fromWall},${it.toWall}]" })}",
                "\"corners\":" + jl(p.corners.map { c -> "{\"from\":${c.fromWall},\"to\":${c.toWall},\"x\":${jn(c.x)},\"z\":${jn(c.z)},\"interiorAngleDeg\":${jn(c.interiorAngleDeg, 2)},\"angleSigmaDeg\":${jn(c.angleSigmaDeg, 2)},\"positionSigmaM\":${jn(c.positionSigmaM)},\"support\":${js(c.support.name)}}" }),
                "\"unobserved\":" + jl(p.unobserved.map { s -> "{\"wall\":${s.wallId},\"kind\":${js(s.kind)},\"reason\":${js(s.reason)},\"lengthM\":${jn(s.lengthM)},\"from\":[${jn(s.ax)},${jn(s.az)}],\"to\":[${jn(s.bx)},${jn(s.bz)}],\"status\":\"NOT OBSERVED\"}" }),
                "\"missing\":" + (p.missing?.let { m -> "{\"afterWall\":${m.afterWall},\"beforeWall\":${m.beforeWall},\"from\":[${jn(m.fromX)},${jn(m.fromZ)}],\"fromState\":${js(m.fromState.name)},\"fromReason\":${js(m.fromReason.name)},\"to\":[${jn(m.toX)},${jn(m.toZ)}],\"toState\":${js(m.toState.name)},\"toReason\":${js(m.toReason.name)},\"reason\":${js(m.reason)}}" } ?: "null"),
                "\"closure\":" + (p.closure?.let { k -> "{\"errorM\":${jn(k.errorM)},\"propagatedSigmaM\":${jn(k.propagatedSigmaM)},\"chi2\":${jn(k.chi2, 3)},\"dof\":${k.dof},\"pValue\":${jn(k.pValue, 6)},\"compatible\":${k.compatible},\"turnSumDeg\":${jn(k.turnSumDeg, 2)},\"turnSigmaDeg\":${jn(k.turnSigmaDeg, 2)},\"simplePolygon\":${k.simplePolygon},\"unobservedM\":${jn(k.unobservedM)}}" } ?: "null"),
                "\"uncertainty\":{\"measurement\":{\"maxPositionSigmaM\":${jn(u.maxPositionSigmaM)},\"maxHeadingSigmaDeg\":${jn(u.maxHeadingSigmaDeg, 3)}},\"endpoints\":{\"observed\":${u.endsObserved},\"partial\":${u.endsPartial},\"uncertain\":${u.endsUncertain}}," +
                    "\"link\":{\"min\":${jn(u.minLinkScore)},\"mean\":${jn(u.meanLinkScore)}},\"closure\":{\"p\":${jn(u.closureP, 6)},\"confidence\":${jn(u.closureConfidence)}}," +
                    "\"recognition\":{\"min\":${jn(u.minRecognition, 3)},\"mean\":${jn(u.meanRecognition, 3)}},\"evidenceWorst\":${js(u.worstEvidence.name)},\"perimeterSigmaM\":${jn(u.perimeterSigmaM)},\"areaSigmaM2\":${jn(u.areaSigmaM2)},\"calibrated\":false}",
                "\"observedLengthM\":${jn(p.observedLengthM)}", "\"perimeterLengthM\":${jn(p.perimeterLengthM)}", "\"areaM2\":${jn(p.areaM2)}",
                "\"alternatives\":${jl(p.alternatives.map(::js))}", "\"reasons\":${jl(p.reasons.map(::js))}",
            ).joinToString(",") + "}"
        })
        append("],\n\"walls\":[")
        append(r.walls.joinToString(",\n") { w -> "{\"id\":${w.wallId},\"role\":${js(w.role.name)},\"perimeter\":${w.perimeterId ?: "null"},\"reasons\":${jl(w.reasons.map(::js))}}" })
        append("],\n\"hypotheses\":[")
        append(r.hypotheses.joinToString(",\n") { h -> "{\"kind\":${js(h.kind.name)},\"status\":${js(h.status)},\"perimeter\":${h.perimeterId},\"afterWall\":${h.afterWall},\"beforeWall\":${h.beforeWall},\"points\":${jl(h.points.map { jn(it) })},\"lengthM\":${jn(h.lengthM)},\"note\":${js(h.note)}}" })
        append("],\n\"candidates\":[")
        append(r.candidates.joinToString(",\n") { linkJson(it) })
        append("],\n\"diagnostics\":${jl(r.diagnostics.map(::js))}\n}\n")
    }

    // ---------------------------------------------------------------------------------------------------------- vista dall'alto

    /**
     * Vista dall'alto: segmenti R3 (sottili), pareti del perimetro principale (spesse), frammenti fusi, collegamenti osservati e
     * dedotti, gap e prolungamenti NON osservati, ipotesi (rosso puntinato), pareti oltre le aperture e collegamenti ambigui.
     */
    fun topDown(map: GlobalMap, s: SurfaceResult, walls: WallEstimationResult, r: PerimeterResult, cameras: List<DoubleArray>, title: String, widthPx: Int = 1300): String {
        val g = map.plan.grid
        val scale = (widthPx - 40) / (g.nx * g.cellM)
        val heightPx = (g.nz * g.cellM * scale + 190).roundToInt()
        fun px(x: Double) = f((x - g.minX) * scale + 20, 1)
        fun pz(z: Double) = f((z - g.minZ) * scale + 60, 1)
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$widthPx" height="$heightPx" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FAFAFA"/>""")
        sb.append("""<text x="20" y="22" font-size="15" font-weight="bold">${esc(title)} — perimetro R4: ${r.state} (NON è una planimetria)</text>""")
        sb.append("""<text x="20" y="42" fill="#555">Spesso = pareti R3 nel perimetro · tratteggio grigio = NON osservato · rosso puntinato = ipotesi INFERRED / NOT OBSERVED · viola = oltre un'apertura · arancio = ambiguo</text>""")
        val floorY = s.floor?.y
        val cells = HashSet<Long>()
        for (i in 0 until map.points.size) {
            if (floorY != null && map.points.y[i] <= floorY + 0.15) continue
            val ci = g.cx(map.points.x[i].toDouble()); val ck = g.cz(map.points.z[i].toDouble())
            if (g.inside(ci, 0, ck)) cells.add((ci.toLong() shl 32) or ck.toLong())
        }
        val cell = f(g.cellM * scale + 0.3, 1)
        sb.append("""<g fill="#E8E8E8">""")
        for (k in cells.sorted()) { val ci = (k ushr 32).toInt(); val ck = (k and 0xFFFFFFFFL).toInt(); sb.append("""<rect x="${px(g.minX + ci * g.cellM)}" y="${pz(g.minZ + ck * g.cellM)}" width="$cell" height="$cell"/>""") }
        sb.append("</g>")
        if (cameras.isNotEmpty()) sb.append("""<polyline fill="none" stroke="#B0BEC5" stroke-width="1" points="${cameras.joinToString(" ") { "${px(it[0])},${pz(it[1])}" }}"/>""")
        val role = r.walls.associate { it.wallId to it.role }
        val byId = walls.walls.associateBy { it.id }
        // Tutti i segmenti R3 (sottili), colorati per ruolo.
        for (w in walls.walls) {
            val gm = w.geometry
            val color = when (role[w.id]) { WallRole.PERIMETER -> "#1565C0"; WallRole.OTHER_CHAIN -> "#00897B"; WallRole.BEYOND_OPENING -> "#7B1FA2"; else -> "#757575" }
            for (sp in gm.observedSpans) { val a = gm.pointAt(sp.fromU); val b = gm.pointAt(sp.toU); sb.append("""<line x1="${px(a[0])}" y1="${pz(a[1])}" x2="${px(b[0])}" y2="${pz(b[1])}" stroke="$color" stroke-width="2"/>""") }
            for (gp in w.gaps) { val a = gm.pointAt(gp.fromU); val b = gm.pointAt(gp.toU); sb.append("""<line x1="${px(a[0])}" y1="${pz(a[1])}" x2="${px(b[0])}" y2="${pz(b[1])}" stroke="#9E9E9E" stroke-width="2" stroke-dasharray="4,4"/>""") }
            val m = gm.pointAt((gm.startU + gm.endU) / 2)
            val tag = when (role[w.id]) { WallRole.BEYOND_OPENING -> " oltre apertura?"; WallRole.UNLINKED -> " (non collegata)"; else -> "" }
            sb.append("""<text x="${f((m[0] - g.minX) * scale + 26, 1)}" y="${f((m[1] - g.minZ) * scale + 54, 1)}" fill="$color" font-weight="bold">${esc("W${w.id}$tag")}</text>""")
        }
        // Perimetri: pareti spesse, collegamenti, tratti non osservati, angoli con σ.
        for (per in r.perimeters.filter { it.links.isNotEmpty() }) {
            val main = per === r.main
            val color = if (main) "#1565C0" else "#00897B"
            for (id in per.wallIds) { val w = byId[id] ?: continue; for (sp in w.geometry.observedSpans) { val a = w.geometry.pointAt(sp.fromU); val b = w.geometry.pointAt(sp.toU); sb.append("""<line x1="${px(a[0])}" y1="${pz(a[1])}" x2="${px(b[0])}" y2="${pz(b[1])}" stroke="$color" stroke-width="${if (main) 6 else 4}" stroke-opacity="0.85"/>""") } }
            for (u in per.unobserved) sb.append("""<line x1="${px(u.ax)}" y1="${pz(u.az)}" x2="${px(u.bx)}" y2="${pz(u.bz)}" stroke="#9E9E9E" stroke-width="3" stroke-dasharray="5,4"/>""")
            for (l in per.links.filter { it.kind == LinkKind.SAME_WALL }) {
                val a = byId[l.fromWall]?.let { it.geometry.pointAt(it.geometry.endU) } ?: continue; val b = byId[l.toWall]?.let { it.geometry.pointAt(it.geometry.startU) } ?: continue
                sb.append("""<text x="${f(((a[0] + b[0]) / 2 - g.minX) * scale + 22, 1)}" y="${f(((a[1] + b[1]) / 2 - g.minZ) * scale + 74, 1)}" fill="#6A1B9A" font-size="11">fusi W${l.fromWall}+W${l.toWall}</text>""")
            }
            for (c in per.corners) {
                val rad = max(3.0, c.positionSigmaM * scale)
                val stroke = if (c.support == LinkSupport.OBSERVED) "#2E7D32" else "#EF6C00"
                sb.append("""<circle cx="${px(c.x)}" cy="${pz(c.z)}" r="${f(rad, 1)}" fill="none" stroke="$stroke" stroke-width="1.5"/><circle cx="${px(c.x)}" cy="${pz(c.z)}" r="2.5" fill="$stroke"/>""")
                sb.append("""<text x="${f((c.x - g.minX) * scale + 26, 1)}" y="${f((c.z - g.minZ) * scale + 74, 1)}" fill="$stroke" font-size="11">${f(c.interiorAngleDeg, 0)}°±${f(c.angleSigmaDeg, 1)}</text>""")
            }
        }
        // Collegamenti ambigui.
        for (l in r.candidates.filter { it.decision == LinkDecision.AMBIGUOUS }) {
            val a = byId[l.fromWall]?.let { it.geometry.pointAt(it.geometry.endU) } ?: continue; val b = byId[l.toWall]?.let { it.geometry.pointAt(it.geometry.startU) } ?: continue
            val pts = if (l.cornerX != null) "${px(a[0])},${pz(a[1])} ${px(l.cornerX)},${pz(l.cornerZ!!)} ${px(b[0])},${pz(b[1])}" else "${px(a[0])},${pz(a[1])} ${px(b[0])},${pz(b[1])}"
            sb.append("""<polyline fill="none" stroke="#FB8C00" stroke-width="1.5" stroke-dasharray="2,3" points="$pts"/>""")
        }
        // Ipotesi.
        for (h in r.hypotheses) {
            val pts = h.points.chunked(2).joinToString(" ") { "${px(it[0])},${pz(it[1])}" }
            sb.append("""<polyline fill="none" stroke="#D32F2F" stroke-width="2" stroke-dasharray="1,4" stroke-linecap="round" points="$pts"/>""")
            val mid = h.points.chunked(2)[h.points.size / 4]
            sb.append("""<text x="${f((mid[0] - g.minX) * scale + 24, 1)}" y="${f((mid[1] - g.minZ) * scale + 56, 1)}" fill="#D32F2F" font-size="11">IPOTESI ${h.kind} — NON osservata</text>""")
        }
        val y0 = heightPx - 85
        sb.append("""<line x1="20" y1="$y0" x2="${f(20 + scale, 1)}" y2="$y0" stroke="#000" stroke-width="3"/><text x="20" y="${y0 + 16}">1 m</text>""")
        val legend = listOf("#1565C0" to "perimetro principale", "#00897B" to "altra catena", "#757575" to "non collegata", "#7B1FA2" to "oltre un'apertura", "#2E7D32" to "angolo osservato (cerchio = σ)", "#EF6C00" to "angolo dedotto", "#FB8C00" to "ambiguo", "#D32F2F" to "ipotesi")
        var lx = 20; var ly = y0 + 36
        for ((c, l) in legend) {
            val w = 30 + 7 * l.length
            if (lx + w > widthPx - 20) { lx = 20; ly += 18 }
            sb.append("""<rect x="$lx" y="${ly - 7}" width="14" height="6" fill="$c"/><text x="${lx + 18}" y="$ly">${esc(l)}</text>"""); lx += w
        }
        val main = r.main
        val info = if (main == null) "Nessun perimetro: nessun collegamento supportato." else
            "P${main.id} ${main.state}: " + main.wallIds.joinToString("→") { "W$it" } + " · osservato ${f(main.observedLengthM)} m" + (main.closure?.let { " · chiusura p = ${f(it.pValue, 3)}" } ?: "")
        sb.append("""<text x="20" y="${heightPx - 10}" fill="#333">${esc(info)}</text>""")
        sb.append("</svg>")
        return sb.toString()
    }
}
