package com.sagoma.planimetria.scan.recording.analysis

import com.sagoma.planimetria.geometry.formatDecimal
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Opzioni del disegno dall'alto. */
data class SvgOptions(
    val widthPx: Int = 1200,
    val showPoints: Boolean = true,
    val showTrajectory: Boolean = true,
    val showLabels: Boolean = true,
    val title: String? = null,
)

/**
 * Vista dall'alto della scansione in SVG: traiettoria della camera, segmenti dei piani verticali (con chiave e persistenza) e
 * densità della nuvola di punti. Assi come la pianta di Sagoma: x → destra, z ↓ basso (metri nel mondo ARCore, origine e
 * orientamento arbitrari). Colori dei piani per persistenza: grigio < 1 s, arancione 1–5 s, azzurro 5–10 s, verde ≥ 10 s;
 * tratteggiati i piani assorbiti da un altro. Deterministico.
 */
object TopDownSvg {
    private fun n(v: Double, d: Int = 2) = formatDecimal(v, d, '.')

    private fun color(persistenceMs: Long) = when {
        persistenceMs >= 10_000 -> "#2e7d32"
        persistenceMs >= 5_000 -> "#1e88e5"
        persistenceMs >= 1_000 -> "#fb8c00"
        else -> "#9e9e9e"
    }

    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun render(a: RecordingAnalysis, minPersistenceMs: Long = 0, options: SvgOptions = SvgOptions()): String {
        val planes = a.vertical(minPersistenceMs).filter { it.last != null }

        // Ingombro del disegno (metri nel mondo).
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        fun grow(x: Double, z: Double) { minX = min(minX, x); maxX = max(maxX, x); minZ = min(minZ, z); maxZ = max(maxZ, z) }
        for (t in a.trajectory) grow(t.x, t.z)
        for (p in planes) { val g = p.last!!; grow(g.a.x, g.a.z); grow(g.b.x, g.b.z) }
        a.pointCloud.heat?.let { if (options.showPoints) { grow(it.minX, it.minZ); grow(it.minX + it.cols * it.cellM, it.minZ + it.rows * it.cellM) } }
        if (minX > maxX) { minX = -1.0; maxX = 1.0; minZ = -1.0; maxZ = 1.0 }
        val margin = 0.6
        minX -= margin; maxX += margin; minZ -= margin; maxZ += margin
        val spanX = max(maxX - minX, 1.0)
        val spanZ = max(maxZ - minZ, 1.0)
        val width = options.widthPx
        val scale = (width - 2 * PAD) / spanX
        val height = (spanZ * scale + 2 * PAD + HEADER).toInt()
        fun px(x: Double) = PAD + (x - minX) * scale
        fun py(z: Double) = HEADER + PAD + (z - minZ) * scale

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\" font-family=\"sans-serif\">\n")
            append("<rect width=\"$width\" height=\"$height\" fill=\"#ffffff\"/>\n")
            // Titolo e legenda.
            val title = options.title ?: "Scansione vista dall'alto"
            append("<text x=\"$PAD\" y=\"22\" font-size=\"16\" font-weight=\"bold\">${esc(title)}</text>\n")
            append("<text x=\"$PAD\" y=\"42\" font-size=\"12\" fill=\"#444\">${esc("Piani verticali con persistenza ≥ ${n(minPersistenceMs / 1000.0, 1)} s: ${planes.size} di ${a.verticalPlanes.size} · camera ${n(a.distanceM, 1)} m in ${n(a.durationMs / 1000.0, 1)} s · assi: x → destra, z ↓ basso (metri, mondo ARCore)")}</text>\n")
            var lx = PAD.toDouble()
            for ((label, c) in listOf("&lt; 1 s" to "#9e9e9e", "1–5 s" to "#fb8c00", "5–10 s" to "#1e88e5", "≥ 10 s" to "#2e7d32")) {
                append("<line x1=\"${n(lx)}\" y1=\"60\" x2=\"${n(lx + 24)}\" y2=\"60\" stroke=\"$c\" stroke-width=\"4\"/>")
                append("<text x=\"${n(lx + 30)}\" y=\"64\" font-size=\"11\">$label</text>\n")
                lx += 90
            }
            append("<line x1=\"${n(lx)}\" y1=\"60\" x2=\"${n(lx + 24)}\" y2=\"60\" stroke=\"#555\" stroke-width=\"3\" stroke-dasharray=\"5 3\"/><text x=\"${n(lx + 30)}\" y=\"64\" font-size=\"11\">assorbito</text>\n")
            lx += 90
            append("<line x1=\"${n(lx)}\" y1=\"60\" x2=\"${n(lx + 24)}\" y2=\"60\" stroke=\"#1565c0\" stroke-width=\"2\"/><text x=\"${n(lx + 30)}\" y=\"64\" font-size=\"11\">camera</text>\n")

            // Griglia da 1 m.
            var gx = ceil(minX)
            while (gx <= maxX) {
                append("<line x1=\"${n(px(gx))}\" y1=\"${n(py(minZ))}\" x2=\"${n(px(gx))}\" y2=\"${n(py(maxZ))}\" stroke=\"#e6e6e6\" stroke-width=\"1\"/>")
                append("<text x=\"${n(px(gx) + 2)}\" y=\"${n(py(minZ) + 11)}\" font-size=\"9\" fill=\"#999\">${n(gx, 0)}</text>\n")
                gx += 1.0
            }
            var gz = ceil(minZ)
            while (gz <= maxZ) {
                append("<line x1=\"${n(px(minX))}\" y1=\"${n(py(gz))}\" x2=\"${n(px(maxX))}\" y2=\"${n(py(gz))}\" stroke=\"#e6e6e6\" stroke-width=\"1\"/>")
                append("<text x=\"${n(px(minX) + 2)}\" y=\"${n(py(gz) - 2)}\" font-size=\"9\" fill=\"#999\">${n(gz, 0)}</text>\n")
                gz += 1.0
            }

            // Densità della nuvola di punti.
            val heat = a.pointCloud.heat
            if (options.showPoints && heat != null && heat.maxCount > 0) {
                val lmax = ln(1.0 + heat.maxCount)
                for (row in 0 until heat.rows) for (col in 0 until heat.cols) {
                    val c = heat.count(col, row)
                    if (c == 0) continue
                    val alpha = 0.12 + 0.68 * ln(1.0 + c) / lmax
                    append("<rect x=\"${n(px(heat.minX + col * heat.cellM))}\" y=\"${n(py(heat.minZ + row * heat.cellM))}\" width=\"${n(heat.cellM * scale)}\" height=\"${n(heat.cellM * scale)}\" fill=\"#d32f2f\" fill-opacity=\"${n(alpha)}\"/>\n")
                }
            }

            // Piani verticali: dal meno al più persistente, così quelli stabili restano sopra.
            for (p in planes.sortedBy { it.persistenceMs }) {
                val g = p.last!!
                val dash = if (p.subsumedBy != null) " stroke-dasharray=\"6 4\"" else ""
                append("<line x1=\"${n(px(g.a.x))}\" y1=\"${n(py(g.a.z))}\" x2=\"${n(px(g.b.x))}\" y2=\"${n(py(g.b.z))}\" stroke=\"${color(p.persistenceMs)}\" stroke-width=\"4\" stroke-linecap=\"round\"$dash opacity=\"0.9\"/>\n")
                if (options.showLabels) {
                    val mx = px((g.a.x + g.b.x) / 2); val my = py((g.a.z + g.b.z) / 2)
                    append("<text x=\"${n(mx)}\" y=\"${n(my - 6)}\" font-size=\"11\" text-anchor=\"middle\" fill=\"#222\">#${p.key} · ${n(p.persistenceMs / 1000.0, 1)}s</text>\n")
                }
            }

            // Traiettoria della camera.
            if (options.showTrajectory && a.trajectory.isNotEmpty()) {
                val pts = a.trajectory.joinToString(" ") { "${n(px(it.x))},${n(py(it.z))}" }
                append("<polyline points=\"$pts\" fill=\"none\" stroke=\"#1565c0\" stroke-width=\"1.5\" stroke-opacity=\"0.8\"/>\n")
                var next = 0L
                for (t in a.trajectory) if (t.elapsedMs >= next) {
                    append("<circle cx=\"${n(px(t.x))}\" cy=\"${n(py(t.z))}\" r=\"2.5\" fill=\"#1565c0\"/>")
                    if (options.showLabels) append("<text x=\"${n(px(t.x) + 4)}\" y=\"${n(py(t.z) - 4)}\" font-size=\"9\" fill=\"#1565c0\">${t.elapsedMs / 1000}s</text>")
                    append("\n")
                    next += 10_000
                }
                val s = a.trajectory.first(); val e = a.trajectory.last()
                append("<circle cx=\"${n(px(s.x))}\" cy=\"${n(py(s.z))}\" r=\"6\" fill=\"#43a047\" stroke=\"#fff\"/><text x=\"${n(px(s.x) + 8)}\" y=\"${n(py(s.z) + 4)}\" font-size=\"11\" fill=\"#2e7d32\">inizio</text>\n")
                append("<circle cx=\"${n(px(e.x))}\" cy=\"${n(py(e.z))}\" r=\"6\" fill=\"#e53935\" stroke=\"#fff\"/><text x=\"${n(px(e.x) + 8)}\" y=\"${n(py(e.z) + 4)}\" font-size=\"11\" fill=\"#c62828\">fine</text>\n")
            }
            append("</svg>\n")
        }
    }

    private const val PAD = 20
    private const val HEADER = 70
}
