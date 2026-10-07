package com.sagoma.planimetria.scan.experiment

import com.sagoma.planimetria.geometry.formatDecimal

/** Report testuale dell'esperimento M3.1. Deterministico. */
object PerimeterReport {
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun pct(v: Double) = formatDecimal(v * 100, 0, '.') + "%"
    private fun pt(x: Double, z: Double) = "(${f(x)}, ${f(z)})"

    fun text(
        title: String, input: ExperimentInput, r: ExperimentResult, perm: PerimeterExperiment.PermutationReport,
        sweep: List<PerimeterExperiment.SweepRow>, source: String,
    ): String = buildString {
        fun line(t: String = "") = append(t).append('\n')
        line("=== Esperimento M3.1: dalle candidate di parete al perimetro — $title ===")
        line("Origine delle candidate: $source")
        line("Coordinate: mondo ARCore, pianta (x → destra, z ↓ basso), metri. Nessuna etichetta descrittiva usata: solo geometria ed evidenza.")
        line()

        // ---- Conteggi
        val st = r.status.associate { it.first to it.second }
        val fusedCandidates = r.walls.filter { it.memberIds.size > 1 }.sumOf { it.memberIds.size }
        line("-- 1. Quante candidate")
        line("Candidate iniziali: ${r.candidates.size}")
        line("Escluse subito (troppo corte < ${f(r.params.minCandidateLengthM)} m o basse < ${f(r.params.minCandidateHeightM)} m): ${r.sanityExcluded.size}")
        line("Usate nella fusione: ${r.candidates.size - r.sanityExcluded.size}")
        line("Fuse con almeno un'altra: $fusedCandidates (in ${r.walls.count { it.memberIds.size > 1 }} pareti fuse)")
        line("Pareti dopo la fusione: ${r.walls.size} (idonee al perimetro: ${r.walls.count { it.eligible }}, deboli/corte: ${r.walls.count { !it.eligible }})")
        line("Candidate escluse perché in pareti deboli: ${st.values.count { it == CandidateStatus.EXCLUDED_WEAK }}")
        line("Candidate valide ma non nel perimetro scelto: ${st.values.count { it == CandidateStatus.NOT_IN_PERIMETER }}")
        line("Candidate nel perimetro: ${st.values.count { it == CandidateStatus.IN_PERIMETER_SINGLE || it == CandidateStatus.IN_PERIMETER_FUSED }}")
        line("Pareti finali nel perimetro: ${r.best?.wallIndices?.size ?: 0}")
        r.notes.forEach { line("Nota: $it") }
        line()

        // ---- Candidate
        line("-- 2. Candidate (id = chiave del piano ARCore)")
        line("id     lung.(m) persist.(s) frame trk%  alt.(m) punti  rms(cm) assorbita  parete  stato")
        val wallOf = HashMap<Int, FusedWall>()
        r.walls.forEach { w -> w.memberIds.forEach { wallOf[it] = w } }
        for (c in r.candidates) {
            line(
                "c${c.id.toString().padEnd(5)} ${f(c.length).padStart(7)} ${f(c.persistenceMs / 1000.0, 1).padStart(10)} ${c.observedFrames.toString().padStart(5)} " +
                    "${formatDecimal(c.trackingFraction * 100, 0, '.').padStart(4)}% ${f(c.heightM).padStart(6)} ${c.pointSupport.toString().padStart(6)} " +
                    "${(c.rmsM?.let { f(it * 100, 1) } ?: "—").padStart(7)}  ${(if (c.subsumed) "sì" else "no").padEnd(9)}  " +
                    "${(wallOf[c.id]?.let { "W${it.index + 1}" } ?: "—").padEnd(6)}  ${st[c.id]?.label}",
            )
        }
        line()

        // ---- Pareti fuse
        line("-- 3. Pareti dopo la fusione")
        for (w in r.walls) {
            line(
                "W${w.index + 1}: candidate ${w.memberIds.joinToString { "c$it" }} · estremi ${pt(w.a.x, w.a.z)} → ${pt(w.b.x, w.b.z)} · lunghezza ${f(w.length)} m · " +
                    "direzione ${f(w.headingDeg, 1)}° · copertura ${pct(w.coverage)} · persistenza ${f(w.persistenceMs / 1000.0, 1)} s · punti/m ${f(w.pointDensityPerM, 1)} · " +
                    "rms ${w.rmsM?.let { f(it * 100, 1) + " cm" } ?: "—"} · quota y ${f(w.bottomY)}..${f(w.topY)}",
            )
            line("     evidenza (punteggio ${f(w.score)}): " + w.scoreParts.joinToString(" · ") { "${it.first} ${f(it.second)}" } + " → " + (if (w.eligible) "idonea al perimetro" else "ESCLUSA: ${w.ineligibleReason}"))
        }
        line()

        // ---- Angoli
        line("-- 4. Angoli validi tra pareti idonee (${r.corners.size})")
        for (c in r.corners) line("W${c.i + 1}–W${c.j + 1}: ${pt(c.point.x, c.point.z)} · angolo tra le rette ${f(c.angleDeg, 1)}° · prolungamento W${c.i + 1} ${f(c.extI)} m, W${c.j + 1} ${f(c.extJ)} m")
        line()

        // ---- Perimetro
        line("-- 5. Perimetro")
        line("Cicli chiusi trovati: ${r.cyclesFound}${if (r.searchTruncated) " (ricerca interrotta dal limite)" else ""}")
        line("ESITO: ${r.verdict.label}")
        r.reasons.forEach { line("  - $it") }
        val b = r.best
        if (b != null) {
            line("Pareti nel ciclo: " + b.wallIndices.joinToString(" → ") { "W${it + 1}" })
            line("Area: ${f(b.areaM2)} m² · perimetro: ${f(b.perimeterM)} m · punteggio complessivo: ${f(b.score, 3)}")
            line("Errore di chiusura (lunghezza AGGIUNTA prolungando le pareti fino agli angoli): ${f(b.closureErrorM)} m · prolungamento massimo ${f(b.maxExtensionM)} m")
            line("Quota del perimetro realmente osservata: ${pct(b.supportedFraction)} · lato meno osservato: ${pct(b.minEdgeCoverage)}")
            line("Camera dentro il perimetro: ${b.cameraInsideFraction?.let { pct(it) } ?: "non verificato"} · punti fuori dal perimetro (> 30 cm): ${b.pointsOutsideShare?.let { pct(it) } ?: "non verificato"}")
            line("Angoli (vertice · coordinate · angolo interno · prolungamenti delle due pareti):")
            for (k in b.corners.indices) {
                val prev = b.wallIndices[k]; val next = b.wallIndices[(k + 1) % b.wallIndices.size]
                line("  K${k + 1} ${pt(b.corners[k].x, b.corners[k].z)} · ${f(b.interiorAnglesDeg[k], 1)}° · W${prev + 1}/W${next + 1}: ${f(b.extensions[2 * k])} m / ${f(b.extensions[2 * k + 1])} m")
            }
            line("Lati (parete · da → a · lunghezza · copertura osservata · evidenza della parete):")
            for ((i, e) in b.edges.withIndex()) {
                line("  W${e.wallIndex + 1} ${pt(e.from.x, e.from.z)} → ${pt(e.to.x, e.to.z)} · ${f(e.length)} m · ${pct(e.coverage)} · ${f(e.wallScore)}")
            }
        }
        line()

        // ---- Ambiguità
        line("-- 6. Ambiguità")
        if (r.ambiguities.isEmpty()) line("Nessuna soluzione concorrente vicina alla migliore.") else r.ambiguities.forEach { line("  - $it") }
        for ((i, s) in r.runnersUp.withIndex()) line("  alternativa ${i + 1}: pareti {" + s.wallIndices.sorted().joinToString { "W${it + 1}" } + "} · area ${f(s.areaM2)} m² · punteggio ${f(s.score, 3)}")
        line()

        // ---- Permutazioni
        line("-- 7. Stabilità sull'ordine delle candidate (${perm.runs} permutazioni casuali, anche con id ridistribuiti)")
        line("Soluzioni identiche alla di riferimento: ${perm.identical} su ${perm.runs} · firme distinte: ${perm.distinctSignatures.size}")
        line("Scarto massimo degli angoli: ${if (perm.maxDeviationM >= 1e8) "soluzioni diverse" else f(perm.maxDeviationM * 100, 4) + " cm"} · scarto massimo dell'area: ${f(perm.maxAreaDeviationM2, 6)} m² · scarto massimo del punteggio: ${f(perm.maxScoreDeviation, 6)}")
        if (perm.distinctSignatures.size > 1 || perm.identical != perm.runs) line("ATTENZIONE: il risultato dipende dall'ordine di arrivo delle candidate. Firme: " + perm.distinctSignatures.joinToString(" || "))
        else line("Il risultato non dipende dall'ordine (né dagli id) delle candidate.")
        for (d in perm.details) line("  $d")
        line()

        // ---- Sensibilità
        line("-- 8. Sensibilità alle soglie di fusione")
        line("variante          candidate usate  pareti  idonee  nel ciclo  area (m²)  esito")
        for (s in sweep) line("${s.label.padEnd(17)} ${s.usable.toString().padStart(15)} ${s.walls.toString().padStart(7)} ${s.eligible.toString().padStart(7)} ${(s.cycleWalls?.toString() ?: "—").padStart(10)} ${(s.area?.let { f(it) } ?: "—").padStart(10)}  ${s.verdict.name}")
        line()

        // ---- Conclusione
        line("-- 9. Come leggere il risultato")
        line("Soluzione geometricamente coerente: ${if (b != null) "SÌ (poligono semplice chiuso con ${b.wallIndices.size} pareti e angoli validi)" else "NO"}.")
        line("Evidenza che le pareti appartengano alla stanza: " + when (r.verdict) {
            Verdict.SUPPORTED -> "le soglie di evidenza sono rispettate (perimetro osservato, camera dentro, punti coerenti)."
            Verdict.GEOMETRIC_ONLY -> "NON sufficiente: vedi i motivi sopra."
            Verdict.NO_PERIMETER -> "non applicabile: nessun perimetro."
        })
        line("Un poligono chiuso NON prova che le sue pareti siano pareti della stanza: l'evidenza disponibile (piani ARCore, nuvola di punti, traiettoria) non distingue")
        line("una parete da un mobile alto, da un divisorio o da una superficie vicina. Il giudizio finale richiede il confronto con la stanza reale.")
    }

    /** Tabella CSV delle pareti fuse. */
    fun wallsCsv(r: ExperimentResult): String = buildString {
        append("wall;members;ax;az;bx;bz;lengthM;headingDeg;coverage;persistenceMs;pointsPerM;rmsM;score;eligible;inPerimeter\n")
        val inCycle = r.best?.wallIndices?.toSet() ?: emptySet()
        for (w in r.walls) append("W${w.index + 1};${w.memberIds.joinToString(",")};${f(w.a.x, 3)};${f(w.a.z, 3)};${f(w.b.x, 3)};${f(w.b.z, 3)};${f(w.length, 3)};${f(w.headingDeg, 1)};${f(w.coverage, 3)};${w.persistenceMs};${f(w.pointDensityPerM, 2)};${w.rmsM?.let { f(it, 4) } ?: ""};${f(w.score, 3)};${w.eligible};${w.index in inCycle}\n")
    }
}
