package com.sagoma.planimetria.scan.reconstruction.quality

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.reconstruction.WallEstimationResult

/** Report di M4: file NUOVI (nessun report esistente viene modificato). Solo diagnostica: nessuna richiesta di rescansione. */
object QualityReport {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else formatDecimal(v, d, '.')
    private fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun jn(v: Double?, d: Int = 4) = if (v == null || v.isNaN()) "null" else formatDecimal(v, d, '.')

    private val wallHeader = "wallId;inRoom;role;sourceSurfaceIds;overall;" +
        "measurementQuality;sigmaPositionM;positionLevel;sigmaDirectionDeg;directionLevel;rmsM;crossViewDispersionM;sigmaCalibrated;" +
        "completenessQuality;observedLengthM;unobservedLengthM;unobservedR3M;unobservedDeducedR4M;coverageRatio;extentUnknown(unobserved=lower bound,coverage=upper bound);startState;startReason;endState;endReason;" +
        "ambiguityQuality;ambiguityScoreMax;ambiguitySourceSurface;ambiguityStability;ambiguityAreaWeightedMean;ambiguityUnstable;" +
        "evidenceQuality;r3EvidenceQuality;viewCount;rawFrames;pointCount;r3EvidenceReasons;defects"

    fun wallCsv(q: QualityResult): String = buildString {
        append(wallHeader).append('\n')
        for (w in q.walls) {
            val m = w.measurement; val c = w.completeness; val a = w.ambiguity; val e = w.evidence
            append(listOf(
                "W${w.wallId}", w.inRoom, w.role ?: "", w.sourceSurfaceIds.joinToString(" ") { "S$it" }, w.overall,
                m.level, f(m.positionSigmaM, 4), m.positionLevel, f(m.headingSigmaDeg, 3), m.headingLevel, f(m.rmsM, 4), f(m.dispersionM, 4), m.calibrated,
                c.level, f(c.observedLengthM), f(c.unobservedLengthM), f(c.unobservedR3M), f(c.unobservedR4M), f(c.coverageRatio), c.extentUnknown, c.startState, c.startReason.name, c.endState, c.endReason.name,
                a.level, f(a.maxScore), a.sourceSurfaceId?.let { "S$it" } ?: "", f(a.sourceSurfaceStability), f(a.areaWeightedMean), a.unstable,
                e.level, e.r3Quality, e.viewCount, e.rawFrames, e.pointCount, e.r3Reasons.joinToString(" | "), w.defects.joinToString(" ") { it.code.name },
            ).joinToString(";") { it.toString().replace(";", ",") }).append('\n')
        }
    }

    private fun axis(name: String, s: AxisSummary) = listOf(name, s.values, f(s.min, 4), f(s.max, 4), f(s.mean, 4), s.worst,
        QualityLevel.values().joinToString(" ") { "$it=${s.distribution[it] ?: 0}" }, s.worstWalls.joinToString(" ") { "W$it" }).joinToString(";")

    fun roomCsv(q: QualityResult): String = buildString {
        val r = q.room
        append("campo;valore\n")
        for ((k, v) in listOf(
            "overallQuality (componente peggiore)" to r.overallQuality, "geometryQuality" to r.geometryQuality, "completeness" to r.completeness,
            "closureQuality" to r.closureQuality, "semanticQuality (evidenza di superficie alternativa)" to r.semanticQuality, "evidenceQuality" to r.evidenceQuality,
            "wallCount (stanza)" to r.wallCount, "pareti R3 totali" to r.allWallCount, "pareti della stanza" to r.roomWallIds.joinToString(" ") { "W$it" },
            "observedWallLengthM" to f(r.observedWallLengthM), "unobservedWallLengthM" to f(r.unobservedWallLengthM), "wallCoverageRatio" to f(r.wallCoverageRatio),
            "wallCoverageRatio e un limite superiore" to r.coverageIsUpperBound,
            "perimeterState (R4)" to (r.perimeterState ?: "nessuno"), "closedPerimeter" to r.closedPerimeter, "chi2" to f(r.chi2), "dof" to (r.dof ?: ""),
            "pValue" to f(r.pValue, 4), "closureCompatible" to (r.closureCompatible ?: ""), "closureUnobservedM" to f(r.closureUnobservedM),
            "exploredAreaM2 (R1)" to f(r.exploredAreaM2, 2), "pareti con evidenza alternativa >= tau" to r.ambiguousWalls, "di cui instabili" to r.unstableAmbiguousWalls,
        )) append("$k;$v\n")
        append("\nasse;valori;min;max;media;peggiore;distribuzione;paretiPeggiori\n")
        append(axis("sigmaPositionM", r.positionSigma)).append('\n')
        append(axis("sigmaDirectionDeg", r.headingSigma)).append('\n')
        append(axis("coverageRatio", r.coverage)).append('\n')
        append(axis("ambiguityScoreMax", r.ambiguity)).append('\n')
        append(axis("viewCount", r.evidence)).append('\n')
    }

    fun defectsCsv(q: QualityResult): String = buildString {
        append("ambito;codice;descrizione;valore;dettaglio\n")
        for (d in q.room.defects) append(listOf("stanza", d.code.name, d.code.label, f(d.value, 4), d.detail).joinToString(";") { it.replace(";", ",") }).append('\n')
        for (w in q.walls) for (d in w.defects) append(listOf("W${w.wallId}${if (w.inRoom) "" else " (fuori stanza)"}", d.code.name, d.code.label, f(d.value, 4), d.detail).joinToString(";") { it.replace(";", ",") }).append('\n')
    }

    fun json(q: QualityResult): String = buildString {
        val c = q.config; val r = q.room
        append("{\n\"note\":\"M4 QualityEngine: diagnostica di qualità. Nessuna decisione di rescansione, nessuna semantica di oggetti.\",\n")
        append("\"config\":{\"positionHighM\":${jn(c.positionHighM)},\"positionMediumM\":${jn(c.positionMediumM)},\"headingHighDeg\":${jn(c.headingHighDeg)},\"headingMediumDeg\":${jn(c.headingMediumDeg)},")
        append("\"coverageHigh\":${jn(c.coverageHigh)},\"coverageMedium\":${jn(c.coverageMedium)},\"ambiguityTau\":${jn(c.ambiguityTau)},\"minViews\":${c.minViews},\"kind\":\"product configuration\"},\n")
        append("\"room\":{\"overallQuality\":${js(r.overallQuality.name)},\"geometryQuality\":${js(r.geometryQuality.name)},\"completeness\":${js(r.completeness.name)},")
        append("\"closureQuality\":${js(r.closureQuality.name)},\"semanticQuality\":${js(r.semanticQuality.name)},\"evidenceQuality\":${js(r.evidenceQuality.name)},")
        append("\"wallCount\":${r.wallCount},\"allWallCount\":${r.allWallCount},\"roomWallIds\":${r.roomWallIds},\"observedWallLengthM\":${jn(r.observedWallLengthM)},")
        append("\"unobservedWallLengthM\":${jn(r.unobservedWallLengthM)},\"wallCoverageRatio\":${jn(r.wallCoverageRatio)},\"coverageIsUpperBound\":${r.coverageIsUpperBound},\"perimeterState\":${r.perimeterState?.let { js(it.name) } ?: "null"},")
        append("\"closedPerimeter\":${r.closedPerimeter},\"chi2\":${jn(r.chi2)},\"dof\":${r.dof ?: "null"},\"pValue\":${jn(r.pValue)},\"closureCompatible\":${r.closureCompatible ?: "null"},")
        append("\"closureUnobservedM\":${jn(r.closureUnobservedM)},\"exploredAreaM2\":${jn(r.exploredAreaM2)},\"ambiguousWalls\":${r.ambiguousWalls},\"unstableAmbiguousWalls\":${r.unstableAmbiguousWalls},")
        fun ax(s: AxisSummary) = "{\"values\":${s.values},\"min\":${jn(s.min)},\"max\":${jn(s.max)},\"mean\":${jn(s.mean)},\"worst\":${js(s.worst.name)},\"distribution\":{" +
            QualityLevel.values().joinToString(",") { "${js(it.name)}:${s.distribution[it] ?: 0}" } + "},\"worstWalls\":${s.worstWalls}}"
        append("\"axes\":{\"sigmaPositionM\":${ax(r.positionSigma)},\"sigmaDirectionDeg\":${ax(r.headingSigma)},\"coverageRatio\":${ax(r.coverage)},\"ambiguityScoreMax\":${ax(r.ambiguity)},\"viewCount\":${ax(r.evidence)}},")
        append("\"defects\":[" + r.defects.joinToString(",") { "{\"code\":${js(it.code.name)},\"value\":${jn(it.value)},\"detail\":${js(it.detail)}}" } + "]},\n")
        append("\"walls\":[\n")
        append(q.walls.joinToString(",\n") { w ->
            val m = w.measurement; val cp = w.completeness; val a = w.ambiguity; val e = w.evidence
            "{\"wallId\":${w.wallId},\"inRoom\":${w.inRoom},\"role\":${w.role?.let { js(it) } ?: "null"},\"sourceSurfaceIds\":${w.sourceSurfaceIds},\"overall\":${js(w.overall.name)}," +
                "\"measurement\":{\"level\":${js(m.level.name)},\"sigmaPositionM\":${jn(m.positionSigmaM)},\"positionLevel\":${js(m.positionLevel.name)},\"sigmaDirectionDeg\":${jn(m.headingSigmaDeg)},\"directionLevel\":${js(m.headingLevel.name)},\"rmsM\":${jn(m.rmsM)},\"crossViewDispersionM\":${jn(m.dispersionM)},\"calibrated\":${m.calibrated}}," +
                "\"completeness\":{\"level\":${js(cp.level.name)},\"observedLengthM\":${jn(cp.observedLengthM)},\"unobservedLengthM\":${jn(cp.unobservedLengthM)},\"unobservedR3M\":${jn(cp.unobservedR3M)},\"unobservedDeducedR4M\":${jn(cp.unobservedR4M)},\"coverageRatio\":${jn(cp.coverageRatio)},\"extentUnknown\":${cp.extentUnknown},\"startState\":${js(cp.startState.name)},\"startReason\":${js(cp.startReason.name)},\"endState\":${js(cp.endState.name)},\"endReason\":${js(cp.endReason.name)}}," +
                "\"alternativeSurfaceEvidence\":{\"level\":${js(a.level.name)},\"ambiguityScoreMax\":${jn(a.maxScore)},\"sourceSurfaceId\":${a.sourceSurfaceId ?: "null"},\"sourceSurfaceStability\":${jn(a.sourceSurfaceStability)},\"areaWeightedMean\":${jn(a.areaWeightedMean)},\"unstable\":${a.unstable}}," +
                "\"evidence\":{\"level\":${js(e.level.name)},\"r3Quality\":${js(e.r3Quality.name)},\"viewCount\":${e.viewCount},\"rawFrames\":${e.rawFrames},\"pointCount\":${e.pointCount},\"r3Reasons\":[${e.r3Reasons.joinToString(",") { js(it) }}]}," +
                "\"defects\":[" + w.defects.joinToString(",") { "{\"code\":${js(it.code.name)},\"value\":${jn(it.value)},\"detail\":${js(it.detail)}}" } + "]}"
        })
        append("\n]\n}\n")
    }

    /** Riepilogo leggibile. Descrive lo stato: non dice cosa fare (decisione di M5). */
    fun summary(q: QualityResult, title: String): String = buildString {
        val r = q.room; val c = q.config
        appendLine("M4 — QUALITÀ DELLA RICOSTRUZIONE: $title")
        appendLine("Diagnostica: nessuna decisione di rescansione, nessuna semantica di oggetti. Soglie = configurazione di prodotto:")
        appendLine("  σ posizione HIGH ≤ ${f(c.positionHighM * 100, 1)} cm, MEDIUM ≤ ${f(c.positionMediumM * 100, 1)} cm · σ direzione HIGH ≤ ${f(c.headingHighDeg, 2)}°, MEDIUM ≤ ${f(c.headingMediumDeg, 2)}°")
        appendLine("  copertura HIGH ≥ ${f(c.coverageHigh * 100, 0)}% con entrambe le estremità OBSERVED, MEDIUM ≥ ${f(c.coverageMedium * 100, 0)}% · τ ambiguità ${f(c.ambiguityTau, 2)} · viste minime ${c.minViews}")
        appendLine()
        appendLine("STANZA (pareti: ${r.roomWallIds.joinToString(" ") { "W$it" }}; ${r.wallCount} su ${r.allWallCount} pareti R3)")
        appendLine("  complessiva (componente peggiore): ${r.overallQuality}")
        appendLine("  geometria ${r.geometryQuality} · completezza ${r.completeness} · chiusura ${r.closureQuality} · evidenza alternativa ${r.semanticQuality} · evidenza ${r.evidenceQuality}")
        appendLine("  perimetro R4: ${r.perimeterState ?: "nessuno"}${if (r.pValue != null) " · χ² ${f(r.chi2, 2)} / ${r.dof} gdl, p = ${f(r.pValue, 3)}" else ""}")
        appendLine("  lunghezza osservata ${f(r.observedWallLengthM, 2)} m · non osservata ${f(r.unobservedWallLengthM, 2)} m · copertura ${if (r.coverageIsUpperBound) "≤ " else ""}${f(r.wallCoverageRatio * 100, 1)}%${if (r.coverageIsUpperBound) " (limite superiore: perimetro non chiuso o estensione ignota oltre estremità non osservate)" else ""}")
        appendLine("  area esplorata (R1) ${f(r.exploredAreaM2, 2)} m² · pareti con evidenza alternativa ≥ τ: ${r.ambiguousWalls} (instabili ${r.unstableAmbiguousWalls})")
        fun line(n: String, s: AxisSummary, d: Int) = "  $n: min ${f(s.min, d)} · media ${f(s.mean, d)} · max ${f(s.max, d)} · stati ${QualityLevel.values().joinToString(" ") { "$it ${s.distribution[it] ?: 0}" }} · peggiori ${s.worstWalls.joinToString(" ") { "W$it" }}"
        appendLine(line("σ posizione (m)", r.positionSigma, 4)); appendLine(line("σ direzione (°)", r.headingSigma, 3))
        appendLine(line("copertura", r.coverage, 3)); appendLine(line("evidenza alternativa (max)", r.ambiguity, 3)); appendLine(line("viste", r.evidence, 0))
        appendLine()
        appendLine("PARETI (misura | completezza | evidenza alternativa | evidenza → complessiva)")
        for (w in q.walls) {
            val m = w.measurement; val cp = w.completeness; val a = w.ambiguity; val e = w.evidence
            appendLine("  W${w.wallId}${if (w.inRoom) "" else " (fuori stanza)"}: ${m.level} (σ ${f(m.positionSigmaM * 100, 2)} cm, ${f(m.headingSigmaDeg, 2)}°) | ${cp.level} (oss. ${f(cp.observedLengthM, 2)} m, non oss. ${if (cp.extentUnknown) "≥ " else ""}${f(cp.unobservedLengthM, 2)} m, ${cp.startState}/${cp.endState}) | " +
                "${a.level} (max ${f(a.maxScore, 3)}${a.sourceSurfaceId?.let { " S$it" } ?: ""}${if (a.unstable) ", instabile" else ""}) | ${e.level} (${e.viewCount} viste, R3 ${e.r3Quality}) → ${w.overall}")
            if (w.defects.isNotEmpty()) appendLine("      difetti: ${w.defects.joinToString(" ") { it.code.name }}")
        }
        if (r.defects.isNotEmpty()) { appendLine(); appendLine("DIFETTI DELLA STANZA: ${r.defects.joinToString(" ") { it.code.name }}") }
    }

    /** Vista dall'alto: pareti colorate per qualità complessiva; i gap non osservati di R3 tratteggiati. Nessuna geometria nuova. */
    fun topDown(q: QualityResult, w: WallEstimationResult, title: String): String {
        val pts = w.walls.flatMap { listOf(it.geometry.pointAt(it.geometry.startU), it.geometry.pointAt(it.geometry.endU)) }
        if (pts.isEmpty()) return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"60\"><text x=\"10\" y=\"30\">nessuna parete</text></svg>"
        val x0 = pts.minOf { it[0] } - 0.5; val x1 = pts.maxOf { it[0] } + 0.5; val z0 = pts.minOf { it[1] } - 0.5; val z1 = pts.maxOf { it[1] } + 0.5
        val s = 640.0 / maxOf(x1 - x0, z1 - z0)
        fun px(x: Double) = f(20 + (x - x0) * s, 1)
        fun pz(z: Double) = f(60 + (z - z0) * s, 1)
        val col = mapOf(QualityLevel.HIGH to "#2E7D32", QualityLevel.MEDIUM to "#F9A825", QualityLevel.LOW to "#C62828", QualityLevel.UNKNOWN to "#757575")
        val sb = StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${f(40 + (x1 - x0) * s + 260, 0)}\" height=\"${f(100 + (z1 - z0) * s, 0)}\" font-family=\"sans-serif\" font-size=\"12\"><rect width=\"100%\" height=\"100%\" fill=\"#FFF\"/>")
        sb.append("<text x=\"20\" y=\"24\" font-size=\"14\" font-weight=\"bold\">M4 — qualità per parete: ${title.replace("&", "e").replace("<", "")}</text>")
        sb.append("<text x=\"20\" y=\"42\" fill=\"#555\">colore = qualità complessiva (componente peggiore); tratteggio grigio = gap NON osservati di R3</text>")
        for (wall in w.walls) {
            val g = wall.geometry; val q1 = q.walls.firstOrNull { it.wallId == wall.id } ?: continue
            for (sp in g.observedSpans) { val a = g.pointAt(sp.fromU); val b = g.pointAt(sp.toU); sb.append("<line x1=\"${px(a[0])}\" y1=\"${pz(a[1])}\" x2=\"${px(b[0])}\" y2=\"${pz(b[1])}\" stroke=\"${col[q1.overall]}\" stroke-width=\"${if (q1.inRoom) 5 else 2}\"/>") }
            for (gp in wall.gaps) { val a = g.pointAt(gp.fromU); val b = g.pointAt(gp.toU); sb.append("<line x1=\"${px(a[0])}\" y1=\"${pz(a[1])}\" x2=\"${px(b[0])}\" y2=\"${pz(b[1])}\" stroke=\"#9E9E9E\" stroke-width=\"3\" stroke-dasharray=\"5 4\"/>") }
            val m = g.pointAt((g.startU + g.endU) / 2)
            sb.append("<text x=\"${px(m[0])}\" y=\"${pz(m[1])}\" dy=\"-6\">W${wall.id}</text>")
        }
        val lx = 40 + (x1 - x0) * s
        for ((i, l) in QualityLevel.values().withIndex()) sb.append("<rect x=\"${f(lx, 0)}\" y=\"${70 + 20 * i}\" width=\"14\" height=\"6\" fill=\"${col[l]}\"/><text x=\"${f(lx + 20, 0)}\" y=\"${77 + 20 * i}\">$l</text>")
        sb.append("<text x=\"${f(lx, 0)}\" y=\"170\">stanza: ${q.room.overallQuality}</text><text x=\"${f(lx, 0)}\" y=\"188\">perimetro R4: ${q.room.perimeterState ?: "nessuno"}</text></svg>")
        return sb.toString()
    }
}
