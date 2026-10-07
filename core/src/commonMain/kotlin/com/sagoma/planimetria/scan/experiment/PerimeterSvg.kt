package com.sagoma.planimetria.scan.experiment

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.recording.analysis.RecordingAnalysis
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Tavola dall'alto dell'esperimento: nuvola di punti (opzionale), traiettoria, candidate originali (c#), pareti fuse (W#),
 * candidate escluse (x#, grigio tratteggiato), perimetro finale evidenziato e angoli (K#). Stessi assi e scala delle tavole di
 * `TopDownSvg`: x → destra, z ↓ basso, metri nel mondo ARCore.
 */
object PerimeterSvg {
    private fun n(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private val PALETTE = listOf("#8e24aa", "#00897b", "#f4511e", "#3949ab", "#7cb342", "#c0ca33", "#6d4c41", "#00acc1", "#d81b60", "#546e7a")

    fun render(a: RecordingAnalysis, r: ExperimentResult, title: String, withPoints: Boolean, widthPx: Int = 1300): String {
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        fun grow(x: Double, z: Double) { minX = min(minX, x); maxX = max(maxX, x); minZ = min(minZ, z); maxZ = max(maxZ, z) }
        for (t in a.trajectory) grow(t.x, t.z)
        for (c in r.candidates) { grow(c.ax, c.az); grow(c.bx, c.bz) }
        for (w in r.walls) { grow(w.a.x, w.a.z); grow(w.b.x, w.b.z) }
        r.best?.corners?.forEach { grow(it.x, it.z) }
        a.pointCloud.heat?.let { if (withPoints) { grow(it.minX, it.minZ); grow(it.minX + it.cols * it.cellM, it.minZ + it.rows * it.cellM) } }
        if (minX > maxX) { minX = -1.0; maxX = 1.0; minZ = -1.0; maxZ = 1.0 }
        minX -= 0.7; maxX += 0.7; minZ -= 0.7; maxZ += 0.7
        val scale = (widthPx - 2 * PAD) / max(maxX - minX, 1.0)
        val height = (max(maxZ - minZ, 1.0) * scale + 2 * PAD + HEADER).toInt()
        fun px(x: Double) = PAD + (x - minX) * scale
        fun py(z: Double) = HEADER + PAD + (z - minZ) * scale
        val sol = r.best
        val cycle = sol?.wallIndices?.toSet() ?: emptySet()
        val status = r.status.associate { it.first to it.second }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$widthPx\" height=\"$height\" viewBox=\"0 0 $widthPx $height\" font-family=\"sans-serif\">\n")
            append("<rect width=\"$widthPx\" height=\"$height\" fill=\"#ffffff\"/>\n")
            append("<text x=\"$PAD\" y=\"22\" font-size=\"16\" font-weight=\"bold\">${esc(title)}${if (withPoints) "" else " — senza nuvola di punti"}</text>\n")
            val verdict = esc(r.verdict.label)
            append("<text x=\"$PAD\" y=\"42\" font-size=\"12\" fill=\"#444\">Candidate ${r.candidates.size} · pareti fuse ${r.walls.size} · nel perimetro ${sol?.wallIndices?.size ?: 0} · area ${sol?.let { n(it.areaM2) } ?: "—"} m² · $verdict</text>\n")
            // Legenda.
            var lx = PAD.toDouble()
            fun legend(color: String, label: String, w: Int = 2, dash: String = "") {
                append("<line x1=\"${n(lx)}\" y1=\"60\" x2=\"${n(lx + 26)}\" y2=\"60\" stroke=\"$color\" stroke-width=\"$w\" $dash/><text x=\"${n(lx + 31)}\" y=\"64\" font-size=\"11\">${esc(label)}</text>\n")
                lx += 31 + label.length * 6.2 + 18
            }
            legend("#90a4ae", "candidata originale (c#)", 2)
            legend("#8e24aa", "parete fusa (W#)", 5)
            legend("#9e9e9e", "esclusa (x#)", 2, "stroke-dasharray=\"4 3\"")
            legend("#d50000", "perimetro finale", 6)
            legend("#1565c0", "camera", 2)
            append("<circle cx=\"${n(lx + 6)}\" cy=\"60\" r=\"6\" fill=\"#ffd600\" stroke=\"#000\"/><text x=\"${n(lx + 16)}\" y=\"64\" font-size=\"11\">angolo (K#)</text>\n")

            // Griglia.
            var gx = ceil(minX)
            while (gx <= maxX) { append("<line x1=\"${n(px(gx))}\" y1=\"${n(py(minZ))}\" x2=\"${n(px(gx))}\" y2=\"${n(py(maxZ))}\" stroke=\"#ececec\"/><text x=\"${n(px(gx) + 2)}\" y=\"${n(py(minZ) + 11)}\" font-size=\"9\" fill=\"#aaa\">${n(gx, 0)}</text>\n"); gx += 1.0 }
            var gz = ceil(minZ)
            while (gz <= maxZ) { append("<line x1=\"${n(px(minX))}\" y1=\"${n(py(gz))}\" x2=\"${n(px(maxX))}\" y2=\"${n(py(gz))}\" stroke=\"#ececec\"/><text x=\"${n(px(minX) + 2)}\" y=\"${n(py(gz) - 2)}\" font-size=\"9\" fill=\"#aaa\">${n(gz, 0)}</text>\n"); gz += 1.0 }

            // Nuvola di punti.
            val heat = a.pointCloud.heat
            if (withPoints && heat != null && heat.maxCount > 0) {
                val lmax = ln(1.0 + heat.maxCount)
                for (row in 0 until heat.rows) for (col in 0 until heat.cols) {
                    val c = heat.count(col, row); if (c == 0) continue
                    append("<rect x=\"${n(px(heat.minX + col * heat.cellM))}\" y=\"${n(py(heat.minZ + row * heat.cellM))}\" width=\"${n(heat.cellM * scale)}\" height=\"${n(heat.cellM * scale)}\" fill=\"#d32f2f\" fill-opacity=\"${n(0.10 + 0.55 * ln(1.0 + c) / lmax)}\"/>\n")
                }
            }

            // Perimetro: riempimento leggero sotto a tutto il resto.
            if (sol != null) append("<polygon points=\"${sol.corners.joinToString(" ") { "${n(px(it.x))},${n(py(it.z))}" }}\" fill=\"#d50000\" fill-opacity=\"0.06\"/>\n")

            // Traiettoria.
            if (a.trajectory.isNotEmpty()) {
                append("<polyline points=\"${a.trajectory.joinToString(" ") { "${n(px(it.x))},${n(py(it.z))}" }}\" fill=\"none\" stroke=\"#1565c0\" stroke-width=\"1.5\" stroke-opacity=\"0.7\"/>\n")
                val s = a.trajectory.first(); val e = a.trajectory.last()
                append("<circle cx=\"${n(px(s.x))}\" cy=\"${n(py(s.z))}\" r=\"5\" fill=\"#43a047\" stroke=\"#fff\"/><circle cx=\"${n(px(e.x))}\" cy=\"${n(py(e.z))}\" r=\"5\" fill=\"#e53935\" stroke=\"#fff\"/>\n")
            }

            // Candidate originali (sottili) ed escluse (grigie tratteggiate).
            for (c in r.candidates) {
                val st = status[c.id]
                val excluded = st == CandidateStatus.EXCLUDED_SANITY || st == CandidateStatus.EXCLUDED_WEAK
                val color = if (excluded) "#9e9e9e" else "#90a4ae"
                val dash = if (excluded) " stroke-dasharray=\"4 3\"" else ""
                append("<line x1=\"${n(px(c.ax))}\" y1=\"${n(py(c.az))}\" x2=\"${n(px(c.bx))}\" y2=\"${n(py(c.bz))}\" stroke=\"$color\" stroke-width=\"2\"$dash/>\n")
                val mx = px((c.ax + c.bx) / 2); val my = py((c.az + c.bz) / 2)
                append("<text x=\"${n(mx)}\" y=\"${n(my + 14)}\" font-size=\"9\" text-anchor=\"middle\" fill=\"${if (excluded) "#757575" else "#546e7a"}\">${if (excluded) "x" else "c"}${c.id}</text>\n")
            }

            // Pareti fuse.
            for (w in r.walls) {
                val color = PALETTE[w.index % PALETTE.size]
                val dash = if (!w.eligible) " stroke-dasharray=\"6 4\" opacity=\"0.5\"" else " opacity=\"0.85\""
                append("<line x1=\"${n(px(w.a.x))}\" y1=\"${n(py(w.a.z))}\" x2=\"${n(px(w.b.x))}\" y2=\"${n(py(w.b.z))}\" stroke=\"$color\" stroke-width=\"5\" stroke-linecap=\"round\"$dash/>\n")
                val mx = px((w.a.x + w.b.x) / 2); val my = py((w.a.z + w.b.z) / 2)
                append("<text x=\"${n(mx)}\" y=\"${n(my - 8)}\" font-size=\"12\" font-weight=\"bold\" text-anchor=\"middle\" fill=\"$color\">W${w.index + 1} · ${n(w.length, 1)} m · ${n(w.score)}</text>\n")
            }

            // Perimetro finale e angoli.
            if (sol != null) {
                append("<polygon points=\"${sol.corners.joinToString(" ") { "${n(px(it.x))},${n(py(it.z))}" }}\" fill=\"none\" stroke=\"#d50000\" stroke-width=\"3\" stroke-linejoin=\"round\" stroke-opacity=\"0.9\"/>\n")
                for ((k, c) in sol.corners.withIndex()) {
                    append("<circle cx=\"${n(px(c.x))}\" cy=\"${n(py(c.z))}\" r=\"9\" fill=\"#ffd600\" stroke=\"#000\"/><text x=\"${n(px(c.x))}\" y=\"${n(py(c.z) + 4)}\" font-size=\"10\" font-weight=\"bold\" text-anchor=\"middle\">K${k + 1}</text>\n")
                    append("<text x=\"${n(px(c.x) + 12)}\" y=\"${n(py(c.z) - 8)}\" font-size=\"10\" fill=\"#b71c1c\">${n(sol.interiorAnglesDeg[k], 0)}°</text>\n")
                }
            } else {
                append("<text x=\"$PAD\" y=\"${HEADER + 14}\" font-size=\"13\" fill=\"#b71c1c\">Nessun perimetro chiuso trovato.</text>\n")
            }
            append("</svg>\n")
        }
    }

    private const val PAD = 20
    private const val HEADER = 76
}
