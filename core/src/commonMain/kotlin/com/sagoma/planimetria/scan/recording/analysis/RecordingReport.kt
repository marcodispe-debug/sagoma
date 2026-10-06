package com.sagoma.planimetria.scan.recording.analysis

import com.sagoma.planimetria.geometry.formatDecimal

/** Le soglie di persistenza dei piani con cui si guardano i dati: tutti, almeno 1 s, 5 s, 10 s. */
object PersistenceFilters {
    val ALL: List<Pair<String, Long>> = listOf("tutti" to 0L, "1s" to 1_000L, "5s" to 5_000L, "10s" to 10_000L)
}

/** Report testuale di un'analisi e tabella CSV dei piani. Deterministici: stesso input, stesso testo. */
object RecordingReport {
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun s(ms: Long) = f(ms / 1000.0, 1) + " s"

    private fun dist(d: Dist, unit: String, decimals: Int = 2): String =
        if (d.count == 0) "nessun dato"
        else "n=${d.count} min ${f(d.min, decimals)} · p10 ${f(d.p10, decimals)} · mediana ${f(d.median, decimals)} · media ${f(d.mean, decimals)} · p90 ${f(d.p90, decimals)} · max ${f(d.max, decimals)} $unit"

    fun text(a: RecordingAnalysis, minPersistenceMs: Long = 0, title: String? = null): String = buildString {
        fun line(t: String = "") = append(t).append('\n')
        line("=== Analisi registrazione di scansione${title?.let { " — $it" } ?: ""} ===")
        line("Dispositivo: ${a.deviceLabel}")
        line()
        line("-- Sessione")
        line("Frame registrati: ${a.frames}")
        line("Durata: ${s(a.durationMs)}")
        line("Intervallo tra frame (ms): ${dist(a.frameIntervalMs, "ms", 0)}")
        line("Distanza percorsa dalla camera: ${f(a.distanceM)} m in pianta (${f(a.distance3dM)} m in 3D, solo con tracking)")
        line("Velocità della camera: ${dist(a.speed, "m/s")}")
        line("Tracking per stato: " + a.tracking.framesByState.joinToString(", ") { "${it.first} ${it.second}" }.ifBlank { "—" })
        line("Perdite di tracking: ${a.tracking.losses} · periodo più lungo senza tracking: ${s(a.tracking.longestNotTrackingMs)}")
        if (a.tracking.failureReasons.isNotEmpty()) line("Motivi: " + a.tracking.failureReasons.joinToString(", ") { "${it.first} ${it.second}" })
        line("Depth: supportata ${a.depthSupported ?: "?"} · frame con depth in uso: ${a.depthInUseFrames}")
        line()
        line("-- Pavimento (floorY, quota ARCore in metri)")
        line("Stima: ${dist(a.floor.stats, "m", 3)}")
        line("Deriva (ultimo − primo): ${f(a.floor.driftM, 3)} m · frame senza stima: ${a.floor.framesWithoutFloor}")
        line()
        line("-- Piani")
        val kinds = a.planes.groupBy { it.kind }.entries.sortedBy { it.key }
        line("Piani osservati: ${a.planes.size} (" + kinds.joinToString(", ") { "${it.key} ${it.value.size}" } + ")")
        val v = a.verticalPlanes
        line("Piani verticali: ${v.size} · assorbiti (subsumed): ${a.subsumedVertical} · spariti prima della fine: ${v.count { it.disappeared }} · ricomparsi: ${v.count { it.reappearances > 0 }}")
        line("Persistenza dei piani verticali: " + PersistenceFilters.ALL.joinToString(" · ") { (name, ms) -> "≥$name: ${a.vertical(ms).size}" })
        a.normalMismatchMaxDeg?.let { line("Coerenza normale registrata / normale dalla posa: max ${f(it)}°") }
        line()
        val shown = a.vertical(minPersistenceMs)
        line("-- Piani verticali con persistenza ≥ ${s(minPersistenceMs)} (${shown.size})")
        line("Lunghezze (m): ${dist(a.lengths(minPersistenceMs), "m")}")
        line("Altezze (m): ${dist(a.heights(minPersistenceMs), "m")}")
        line("Direzioni dei muri in pianta (gradi da 0 a 180, bin da 15°; piani · lunghezza totale):")
        for ((start, n, len) in a.headingHistogram(15, minPersistenceMs)) if (n > 0) line("  ${start.toString().padStart(3)}–${(start + 15).toString().padStart(3)}°: $n piani · ${f(len)} m")
        line()
        line("chiave  persist.  frame  trk%  assorbito   sparito  lung.ult/max (m)  alt.max  dir°   centro (x, z)         quota y min..max")
        for (p in shown) {
            val g = p.last
            val trk = if (p.observedFrames == 0) 0 else p.trackingFrames * 100 / p.observedFrames
            line(
                "#${p.key.toString().padEnd(5)} ${s(p.persistenceMs).padStart(9)} ${p.observedFrames.toString().padStart(6)} ${trk.toString().padStart(4)}%  " +
                    (p.subsumedBy?.let { "da #$it @${s(p.firstSubsumedMs ?: 0)}" } ?: "—").padEnd(11) + " " +
                    (if (p.disappeared) "sì @${s(p.lastMs)}" else "no").padEnd(9) + "  " +
                    (if (g == null) "—" else "${f(p.lengthLastM ?: 0.0)}/${f(p.lengthMaxM ?: 0.0)}").padEnd(16) + "  " +
                    (p.heightMaxM?.let { f(it) } ?: "—").padEnd(7) + "  " +
                    (g?.let { f(it.headingDeg, 1) } ?: "—").padEnd(5) + "  " +
                    (g?.let { "(${f(it.centerX)}, ${f(it.centerZ)})" } ?: "—").padEnd(20) + "  " +
                    (g?.let { "${f(it.minY)}..${f(it.maxY)}" } ?: "—"),
            )
        }
        line()
        line("-- Nuvola di punti")
        val pc = a.pointCloud
        line("Aggiornamenti registrati: ${pc.updates} · campioni totali: ${pc.totalSamples} · id distinti: ${pc.distinctIds}")
        line("Punti per aggiornamento: ${dist(pc.pointsPerUpdate, "punti", 0)}")
        line("Confidenza: ${dist(pc.confidence, "")}")
        pc.heat?.let { line("Griglia di densità: ${it.cols} × ${it.rows} celle da ${f(it.cellM * 100, 0)} cm (cella più densa: ${it.maxCount} punti)") }
    }

    /** Tabella dei piani (CSV con il punto e virgola): una riga per piano, per aprirla in un foglio di calcolo. */
    fun csv(a: RecordingAnalysis): String = buildString {
        append("key;kind;firstMs;lastMs;persistenceMs;observedFrames;trackingFrames;subsumedBy;firstSubsumedMs;disappeared;reappearances;")
        append("lengthLastM;lengthMaxM;heightM;headingDeg;tiltDeg;centerX;centerZ;minY;maxY\n")
        for (p in a.planes) {
            val g = p.last
            append("${p.key};${p.kind};${p.firstMs};${p.lastMs};${p.persistenceMs};${p.observedFrames};${p.trackingFrames};")
            append("${p.subsumedBy ?: ""};${p.firstSubsumedMs ?: ""};${p.disappeared};${p.reappearances};")
            append("${p.lengthLastM?.let { f(it, 3) } ?: ""};${p.lengthMaxM?.let { f(it, 3) } ?: ""};${g?.let { f(it.heightM, 3) } ?: ""};")
            append("${g?.let { f(it.headingDeg, 1) } ?: ""};${g?.let { f(it.tiltDeg, 1) } ?: ""};")
            append("${g?.let { f(it.centerX, 3) } ?: ""};${g?.let { f(it.centerZ, 3) } ?: ""};${g?.let { f(it.minY, 3) } ?: ""};${g?.let { f(it.maxY, 3) } ?: ""}\n")
        }
    }
}
