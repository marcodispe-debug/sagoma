package com.sagoma.planimetria.scan.reconstruction.rescan

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.reconstruction.WallEstimationResult

/** Report di M5: file NUOVI. Richieste localizzate e decisione di stop; nessuna classificazione di oggetti. */
object RescanReport {
    private fun f(v: Double?, d: Int = 3) = if (v == null || v.isNaN()) "" else formatDecimal(v, d, '.')
    private fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun jn(v: Double?, d: Int = 4) = if (v == null || v.isNaN()) "null" else formatDecimal(v, d, '.')

    private const val HEADER = "priorita;id;tipo;gravita;parete;inStanza;bersaglio;ax;az;bx;bz;origineCoordinate;posizioneNota;direzioneSguardoX;direzioneSguardoZ;" +
        "osservazione;motivoR3R4;motivoPrincipale;motiviContributivi;nonOsservatoM;visibilitaVerificata;spiegazionePriorita"

    fun csv(p: RescanPlan): String = buildString {
        append(HEADER).append('\n')
        for (r in p.requests) {
            val t = r.target
            append(listOf(r.priorityRank, r.id, r.type, r.severity, r.wallId?.let { "W$it" } ?: "stanza", r.inRoom, t.kind, f(t.ax), f(t.az), f(t.bx), f(t.bz), t.source, t.positionKnown,
                f(r.lookDirX), f(r.lookDirZ), r.observation, r.sourceReason ?: "", r.primaryReason, r.contributingReasons.joinToString(" | "), f(r.unobservedLengthM),
                r.visibilityVerified, r.priorityExplanation).joinToString(";") { it.toString().replace(";", ",") }).append('\n')
        }
    }

    fun json(p: RescanPlan): String = buildString {
        val c = p.config
        append("{\n\"note\":\"M5 RescanDirector: richieste localizzate dalle coordinate esistenti di R2/R3/R4. Posizione della camera e visibilità NON verificate. Nessuna classificazione di oggetti, nessuna memoria tra scansioni.\",\n")
        append("\"config\":{\"minUnobservedM\":${jn(c.minUnobservedM)},\"minViews\":${c.minViews},\"ambiguityTau\":${jn(c.ambiguityTau)},\"endAdjacencyM\":${jn(c.endAdjacencyM)},\"kind\":\"product configuration\"},\n")
        append("\"decision\":${js(p.decision.name)},\"decisionReason\":${js(p.decisionReason)},\n\"requests\":[\n")
        append(p.requests.joinToString(",\n") { r ->
            val t = r.target
            "{\"priorityRank\":${r.priorityRank},\"id\":${js(r.id)},\"type\":${js(r.type.name)},\"severity\":${js(r.severity.name)},\"wallId\":${r.wallId ?: "null"},\"inRoom\":${r.inRoom}," +
                "\"target\":{\"kind\":${js(t.kind.name)},\"ax\":${jn(t.ax)},\"az\":${jn(t.az)},\"bx\":${jn(t.bx)},\"bz\":${jn(t.bz)},\"source\":${js(t.source)},\"positionKnown\":${t.positionKnown}}," +
                "\"lookDirection\":${if (r.lookDirX == null) "null" else "{\"x\":${jn(r.lookDirX)},\"z\":${jn(r.lookDirZ)}}"},\"observation\":${js(r.observation.name)},\"observationText\":${js(r.observation.label)}," +
                "\"sourceReason\":${r.sourceReason?.let { js(it) } ?: "null"},\"primaryReason\":${js(r.primaryReason)},\"contributingReasons\":[${r.contributingReasons.joinToString(",") { js(it) }}]," +
                "\"unobservedLengthM\":${jn(r.unobservedLengthM)},\"visibilityVerified\":${r.visibilityVerified},\"priorityExplanation\":${js(r.priorityExplanation)}}"
        })
        append("\n],\n\"limitations\":[" + p.limitations.joinToString(",") { "{\"wallId\":${it.wallId ?: "null"},\"code\":${js(it.code)},\"detail\":${js(it.detail)}}" } + "]\n}\n")
    }

    /** Riepilogo leggibile: cosa manca, dove, perché, che tipo di osservazione, e quando fermarsi. */
    fun summary(p: RescanPlan, title: String): String = buildString {
        appendLine("M5 — RICHIESTE DI NUOVA ACQUISIZIONE: $title")
        appendLine("Decisione: ${p.decision} — ${p.decisionReason}")
        appendLine("Soglie di prodotto: tratto non osservato ≥ ${f(p.config.minUnobservedM, 2)} m · viste minime ${p.config.minViews} · τ ambiguità ${f(p.config.ambiguityTau, 2)}")
        appendLine("Bersaglio = dove guardare (coordinate esistenti di R2/R3/R4). Posizione della camera e visibilità NON verificate: la direzione non è una traiettoria.")
        appendLine()
        for (r in p.requests) {
            val t = r.target
            val where = when (t.kind) {
                TargetKind.POINT -> "punto (${f(t.ax, 2)}, ${f(t.az, 2)})"
                TargetKind.SEGMENT -> "segmento (${f(t.ax, 2)}, ${f(t.az, 2)}) → (${f(t.bx, 2)}, ${f(t.bz, 2)})"
                TargetKind.NONE -> "posizione non determinabile"
            }
            appendLine("${r.priorityRank}. [${r.severity}] ${r.type} ${r.wallId?.let { "W$it" } ?: "stanza"}${if (r.inRoom) "" else " (fuori stanza)"}")
            appendLine("   cosa manca: ${r.primaryReason}")
            if (r.contributingReasons.isNotEmpty()) appendLine("   stesse cause nello stesso punto: ${r.contributingReasons.joinToString(" · ")}")
            appendLine("   dove: $where — origine ${t.source}${r.unobservedLengthM?.let { " · non osservato ${f(it, 2)} m" } ?: ""}")
            appendLine("   osservazione consigliata: ${r.observation.label}${if (r.lookDirX != null) " (sguardo verso (${f(r.lookDirX, 2)}, ${f(r.lookDirZ, 2)}))" else ""} · visibilità non verificata")
            appendLine("   priorità: ${r.priorityExplanation}")
        }
        if (p.requests.isEmpty()) appendLine("Nessuna richiesta.")
        if (p.limitations.isNotEmpty()) { appendLine(); appendLine("LIMITI RIPORTATI (nessuna richiesta):"); p.limitations.forEach { appendLine("  ${it.code}: ${it.detail}") } }
    }

    /** Vista dall'alto: pareti R3 e bersagli numerati per priorità. Nessuna geometria nuova. */
    fun topDown(p: RescanPlan, w: WallEstimationResult, title: String): String {
        val pts = w.walls.flatMap { listOf(it.geometry.pointAt(it.geometry.startU), it.geometry.pointAt(it.geometry.endU)) } +
            p.requests.flatMap { r -> listOfNotNull(r.target.ax?.let { doubleArrayOf(it, r.target.az!!) }, r.target.bx?.let { doubleArrayOf(it, r.target.bz!!) }) }
        if (pts.isEmpty()) return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"60\"><text x=\"10\" y=\"30\">nessuna geometria</text></svg>"
        val x0 = pts.minOf { it[0] } - 0.5; val x1 = pts.maxOf { it[0] } + 0.5; val z0 = pts.minOf { it[1] } - 0.5; val z1 = pts.maxOf { it[1] } + 0.5
        val s = 640.0 / maxOf(x1 - x0, z1 - z0)
        fun px(x: Double) = f(20 + (x - x0) * s, 1)
        fun pz(z: Double) = f(60 + (z - z0) * s, 1)
        val col = mapOf(Severity.BLOCKING to "#C62828", Severity.IMPORTANT to "#EF6C00", Severity.SECONDARY to "#1565C0")
        val sb = StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${f(40 + (x1 - x0) * s + 300, 0)}\" height=\"${f(100 + (z1 - z0) * s, 0)}\" font-family=\"sans-serif\" font-size=\"12\"><rect width=\"100%\" height=\"100%\" fill=\"#FFF\"/>")
        sb.append("<text x=\"20\" y=\"24\" font-size=\"14\" font-weight=\"bold\">M5 — dove guardare: ${title.replace("&", "e").replace("<", "")} (${p.decision})</text>")
        sb.append("<text x=\"20\" y=\"42\" fill=\"#555\">grigio = pareti R3 osservate; numeri = priorità; visibilità dalla camera NON verificata</text>")
        for (wall in w.walls) for (sp in wall.geometry.observedSpans) {
            val a = wall.geometry.pointAt(sp.fromU); val b = wall.geometry.pointAt(sp.toU)
            sb.append("<line x1=\"${px(a[0])}\" y1=\"${pz(a[1])}\" x2=\"${px(b[0])}\" y2=\"${pz(b[1])}\" stroke=\"#9E9E9E\" stroke-width=\"3\"/>")
        }
        for (r in p.requests) {
            val t = r.target; val c = col.getValue(r.severity)
            when (t.kind) {
                TargetKind.POINT -> sb.append("<circle cx=\"${px(t.ax!!)}\" cy=\"${pz(t.az!!)}\" r=\"7\" fill=\"none\" stroke=\"$c\" stroke-width=\"2\"/><text x=\"${px(t.ax)}\" y=\"${pz(t.az)}\" dx=\"9\" dy=\"-6\" fill=\"$c\">${r.priorityRank}</text>")
                TargetKind.SEGMENT -> sb.append("<line x1=\"${px(t.ax!!)}\" y1=\"${pz(t.az!!)}\" x2=\"${px(t.bx!!)}\" y2=\"${pz(t.bz!!)}\" stroke=\"$c\" stroke-width=\"5\" stroke-opacity=\"0.55\" stroke-dasharray=\"6 4\"/><text x=\"${px((t.ax + t.bx) / 2)}\" y=\"${pz((t.az + t.bz) / 2)}\" dx=\"6\" dy=\"-6\" fill=\"$c\">${r.priorityRank}</text>")
                TargetKind.NONE -> {}
            }
        }
        val lx = 40 + (x1 - x0) * s
        for ((i, e) in col.entries.withIndex()) sb.append("<rect x=\"${f(lx, 0)}\" y=\"${70 + 20 * i}\" width=\"14\" height=\"6\" fill=\"${e.value}\"/><text x=\"${f(lx + 20, 0)}\" y=\"${77 + 20 * i}\">${e.key}</text>")
        val none = p.requests.filter { it.target.kind == TargetKind.NONE }
        if (none.isNotEmpty()) sb.append("<text x=\"${f(lx, 0)}\" y=\"150\">senza posizione: ${none.joinToString(" ") { "#${it.priorityRank}" }}</text>")
        sb.append("</svg>")
        return sb.toString()
    }
}
